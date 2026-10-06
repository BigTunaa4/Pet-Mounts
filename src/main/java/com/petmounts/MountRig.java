package com.petmounts;

import java.awt.Color;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.JagexColor;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.coords.LocalPoint;

/**
 * One player riding one pet: the enlarged pet, the saddle and blanket, the reins, and the player drawn on its
 * back in a riding pose, moving with it. Used for your own mount and for other players shown riding theirs.
 *
 * Everything here runs on the client thread. The real player and pet are hidden by the plugin while
 * {@link #isVisible()}.
 */
final class MountRig
{
	/** Spare model reshaped into the saddle and reins; any stable model with enough vertices and faces works. */
	private static final int TEMPLATE_MODEL = 25754;
	/** Walk animation speed when running on a pet that has no run animation. */
	private static final float RUN_PACE = 1.8f;

	/** How this rider should look; read from the settings (and the pet's saved adjustments) every tick. */
	static final class Style
	{
		RiderPose pose = RiderPose.AUTO;
		int seatHeight;
		int seatForward;
		boolean saddle = true;
		boolean reins = true;
		boolean naturalMotion = true;
		boolean hideHeld = true;
		boolean hideCape = true;
		/** Blanket colour, or null to take it from the pet. */
		Color blanket;
		/** Saddle design. */
		SaddleStyle saddleStyle = SaddleStyle.CLASSIC;
		/** Whether the mount does its tricks now and then. */
		boolean tricks = true;
		/** How far a rein end may move before the reins are rebuilt. Coarser for other riders, to save work. */
		float reinStep = 0.3f;
	}

	private final Client client;
	private final Player player;
	private final PetModels.Built built;

	private final RuneLiteObject mount;
	private final SeatTracker seat = new SeatTracker();
	private final RiderController rider;
	private final RiderPoser poser;
	private final RiderLook look = new RiderLook();
	private final RiderMotion motion = new RiderMotion();

	private PacedAnimationController animation;
	private int animationId = -2;
	private int idleAnimationId = -1;
	/** Something the mount does now and then while standing still (a dog digging), or -1. */
	/** Tricks the mount does now and then while standing still (a dog digging, a dragon rearing up), or null. */
	private final int[] tricks;
	private int stillTicks;
	private int nextExtraAt;
	/** The trick being played, or -1. */
	private int playingTrick = -1;
	/** A trick asked for right away (climbing on), or -1. */
	private int queuedTrick = -1;
	/** A trick that started this tick, for its sound and effects, or -1. */
	private int trickStarted = -1;
	private int lastOrientation = -1;
	private float slide;
	private int nudgeX, nudgeY;

	private RuneLiteObject saddle;
	private boolean saddleTried;
	private float saddleLift;
	private RiderPose saddlePose;
	private SaddleStyle saddleLook;

	private RuneLiteObject reins;
	private final ReinMesh reinMesh = new ReinMesh();
	private ModelData template;
	private int[] bit;
	private boolean bitTried;
	private int bitVertexCount;
	private final float[] reinEnds = new float[12];

	private boolean visible;
	private NPC pet;
	/** The pose and style from the latest tick, for placing everything between ticks. */
	private RiderPose placePose;
	private Style placeStyle;
	/** The mount's footprint in its own space, for the right-click area. */
	private final float minX, maxX, minZ, maxZ;
	/** How far the rider's head is above the ground, updated as they move. */
	private int topHeight;

	MountRig(Client client, Player player, PetModels.Built built)
	{
		this.client = client;
		this.player = player;
		this.built = built;
		this.poser = new RiderPoser(client);
		this.rider = new RiderController(player, () -> poser.hold(player));
		mount = new MountObject(client, built);
		tricks = MountExtras.tricks(built.npcId);
		nextExtraAt = nextExtraDelay();
		seat.set(built.a, built.b, built.c, built.wa, built.wb, built.wc, built.model, built.mountHeight);

		float[] xs = built.model.getVerticesX(), zs = built.model.getVerticesZ();
		float x0 = 0, x1 = 0, z0 = 0, z1 = 0;
		for (int v = 0; v < built.model.getVerticesCount(); v++)
		{
			x0 = Math.min(x0, xs[v]);
			x1 = Math.max(x1, xs[v]);
			z0 = Math.min(z0, zs[v]);
			z1 = Math.max(z1, zs[v]);
		}
		minX = x0;
		maxX = x1;
		minZ = z0;
		maxZ = z1;
	}

	/** Rider height above the feet, to the top of the head. */
	private static final int RIDER_HEIGHT = 200;

	/**
	 * The area on screen covered by the mount and its rider, or null if it's not shown. Used to put the rider's
	 * right-click options back, since the real (hidden) player can't be clicked.
	 */
	java.awt.Shape screenArea()
	{
		LocalPoint lp = drawnAt();
		if (!visible || lp == null)
		{
			return null;
		}
		int plane = player.getWorldView().getPlane();
		double rad = player.getCurrentOrientation() * Math.PI / 1024.0;
		double sin = Math.sin(rad), cos = Math.cos(rad);
		java.util.List<java.awt.Point> points = new java.util.ArrayList<>();
		for (float x : new float[]{minX, maxX})
		{
			for (float z : new float[]{minZ, maxZ})
			{
				int dx = (int) Math.round(x * cos + z * sin);
				int dy = (int) Math.round(z * cos - x * sin);
				LocalPoint corner = new LocalPoint(lp.getX() + dx, lp.getY() + dy, lp.getWorldView());
				for (int h : new int[]{0, topHeight})
				{
					net.runelite.api.Point p = Perspective.localToCanvas(client, corner, plane, h);
					if (p != null)
					{
						points.add(new java.awt.Point(p.getX(), p.getY()));
					}
				}
			}
		}
		return points.size() < 3 ? null : ScreenHull.of(points);
	}

	Player player()
	{
		return player;
	}

	NPC pet()
	{
		return pet;
	}

	String petName()
	{
		return built.petName;
	}

	/** How tall the mount is, in local units. */
	int mountHeight()
	{
		return built.mountHeight;
	}

	int npcId()
	{
		return built.npcId;
	}

	boolean isVisible()
	{
		return visible;
	}

	Color[] palette()
	{
		return built.palette;
	}

	RiderPose poseFor(Style style)
	{
		return style.pose == null || style.pose == RiderPose.AUTO ? built.autoPose : style.pose;
	}

	/** Height of the seat above the ground right now, including the adjustments. */
	int seatLift(Style style)
	{
		float h = seat.isSet() ? -seat.y : built.mountHeight * 0.6f;
		if (saddle != null && saddle.isActive())
		{
			h += saddleLift;
		}
		return Math.round(h) + style.seatHeight;
	}

	/**
	 * Draws the rider on the mount for this client tick.
	 *
	 * @param pet the pet being ridden, for its walk and run animations
	 * @param gait 0 standing, 1 walking, 2 running
	 * @param dropIn whether a rider who has just appeared drops into the saddle from above
	 */
	void update(NPC pet, int gait, Style style, boolean dropIn)
	{
		update(pet, pet == null ? null : new int[]{pet.getIdlePoseAnimation(), pet.getWalkAnimation(),
			pet.getRunAnimation()}, gait, style, dropIn);
	}

	/**
	 * @param pet the pet being ridden, or null when riding a pet that isn't following you
	 * @param animations the pet's {idle, walk, run} animations
	 */
	void update(NPC pet, int[] animations, int gait, Style style, boolean dropIn)
	{
		this.pet = pet;
		RiderPose pose = poseFor(style);

		// When the pet itself does something (a cat pouncing on a rat, a dog digging), the mount does it too.
		int action = pet != null ? pet.getAnimation() : -1;
		updateAnimation(animations, gait, action, style.tricks);
		poser.apply(player, pose);
		look.apply(player, style.hideHeld, style.hideCape);

		boolean idle = animationId == idleAnimationId;
		if (saddleOn(style) && idle && (!saddleTried || saddlePose != pose || saddleLook != style.saddleStyle))
		{
			buildSaddle(style, pose);
		}
		if (!bitTried && idle)
		{
			findBit();
		}
		if (!visible)
		{
			motion.reset(style.naturalMotion && dropIn);
		}

		// The motion (settle, sway, surge) steps once per client tick; where everything goes is worked out every
		// frame in place(), on the exact animation frame being drawn.
		float cycle = animation != null && gait > 0 ? animation.cycle() : 0;
		int orientation = player.getCurrentOrientation();
		int turn = lastOrientation < 0 ? 0 : ((orientation - lastOrientation + 3072) % 2048) - 1024;
		lastOrientation = orientation;
		motion.update(gait, cycle, style.naturalMotion, turn);
		placePose = pose;
		placeStyle = style;
		place();

		if (!mount.isActive())
		{
			mount.setActive(true);
		}
		if (saddle != null && saddle.isActive() != saddleOn(style))
		{
			saddle.setActive(saddleOn(style));
		}
		if (!client.isRuneLiteObjectRegistered(rider))
		{
			client.registerRuneLiteObject(rider);
		}
		visible = true;
	}

	/** Takes the rider off the mount and shows the real player again. */
	void hide(boolean moving)
	{
		setActive(mount, false);
		setActive(saddle, false);
		setActive(reins, false);
		if (client.isRuneLiteObjectRegistered(rider))
		{
			client.removeRuneLiteObject(rider);
		}
		if (visible)
		{
			poser.restore(player, moving);
			look.restore(player);
		}
		visible = false;
	}

	/** Forgets what was changed on the player without touching them (after logout or a world hop). */
	void forget()
	{
		poser.forget();
		look.forget();
		visible = false;
	}

	private static void setActive(RuneLiteObject o, boolean active)
	{
		if (o != null && o.isActive() != active)
		{
			o.setActive(active);
		}
	}

	// ------------------------------------------------------------------
	// Mount animation
	// ------------------------------------------------------------------

	/** Has the mount do one of its tricks as soon as it's standing still (when you climb on). Returns whether it has one. */
	boolean showOff()
	{
		if (tricks == null)
		{
			return false;
		}
		queuedTrick = tricks[(int) (Math.random() * tricks.length)];
		return true;
	}

	/**
	 * Sliding off the side when dismounting: 0 seated, 1 on the ground beside the mount. The rider goes to the
	 * mount's left.
	 */
	void setSlide(float slide)
	{
		this.slide = Math.max(0, Math.min(1, slide));
	}

	/** Shifts the whole mount a little on screen (in local units), to keep riders side by side from overlapping. */
	void setNudge(int x, int y)
	{
		nudgeX = x;
		nudgeY = y;
	}

	/** Where the mount is drawn, including any nudge, or null. */
	LocalPoint drawnAt()
	{
		LocalPoint lp = player.getLocalLocation();
		return lp == null ? null : new LocalPoint(lp.getX() + nudgeX, lp.getY() + nudgeY, lp.getWorldView());
	}

	/** Half the mount's width, in local units. */
	float halfWidth()
	{
		return Math.max(-minX, maxX);
	}

	/** Half the mount's length, in local units. */
	float halfLength()
	{
		return Math.max(-minZ, maxZ);
	}

	/** The trick that just started (once), or -1. */
	int takeTrickStarted()
	{
		int t = trickStarted;
		trickStarted = -1;
		return t;
	}

	/** Shoulder rides have no saddle: you sit on the pet itself. */
	private boolean saddleOn(Style style)
	{
		return style.saddle && !built.shoulders;
	}

	/** 8 to 18 seconds of standing still, in client ticks. */
	private static int nextExtraDelay()
	{
		return 400 + (int) (Math.random() * 500);
	}

	private void updateAnimation(int[] animations, int gait, int action, boolean allowTricks)
	{
		int walk = animations == null ? -1 : animations[1];
		int run = animations == null ? -1 : animations[2];
		idleAnimationId = animations == null ? -1 : animations[0];

		// Now and then, while standing still, the mount does its own thing (a dog stops to dig).
		boolean still = gait == 0 && action == -1;
		if (!still)
		{
			stillTicks = 0;
			playingTrick = -1;
			queuedTrick = -1;
		}
		else if (queuedTrick != -1 && playingTrick == -1)
		{
			playingTrick = queuedTrick;
			queuedTrick = -1;
		}
		else if (tricks != null && allowTricks && playingTrick == -1 && ++stillTicks >= nextExtraAt)
		{
			playingTrick = tricks[(int) (Math.random() * tricks.length)];
		}
		if (playingTrick != -1 && animationId == playingTrick && (animation == null || animation.playedOnce()))
		{
			playingTrick = -1;
			stillTicks = 0;
			nextExtraAt = nextExtraDelay();
		}

		int anim;
		float pace = 1f;
		if (action != -1 && gait == 0)
		{
			anim = action;
		}
		else if (playingTrick != -1)
		{
			anim = playingTrick;
		}
		else if (gait == 2 && run != -1 && run != walk)
		{
			anim = run;
		}
		else if (gait > 0 && walk != -1)
		{
			anim = walk;
			// No run animation: play the walk at running pace so the legs keep up with the ground.
			pace = gait == 2 ? RUN_PACE : 1f;
		}
		else
		{
			anim = idleAnimationId;
		}

		if (anim != animationId)
		{
			if (anim == playingTrick)
			{
				trickStarted = anim;
			}
			animationId = anim;
			Animation a = anim == -1 ? null : client.loadAnimation(anim);
			animation = a == null ? null : new PacedAnimationController(client, a);
			mount.setAnimationController(animation);
			mount.setShouldLoop(true);
		}
		if (animation != null)
		{
			animation.setPace(pace);
		}
	}

	// ------------------------------------------------------------------
	// Placing everything
	// ------------------------------------------------------------------

	/**
	 * Puts the mount, saddle, rider and reins where they belong for the frame about to be drawn. Called every
	 * client tick and again just before each frame is drawn, so the rider sits on the exact animation frame the
	 * mount is showing (never a frame behind) and moves smoothly at any frame rate.
	 */
	void place()
	{
		if (placeStyle == null || player.getLocalLocation() == null)
		{
			return;
		}
		RiderPose pose = placePose;
		Style style = placeStyle;
		LocalPoint lp = drawnAt();
		int plane = player.getWorldView().getPlane();
		int orientation = player.getCurrentOrientation();

		mount.setOrientation(orientation);
		mount.setLocation(lp, plane);

		// Follow the seat on the mount's back as it animates.
		Model frame = mount.getModel();
		seat.update(frame);

		// The rider sits on the seat, plus settling, stride sway and surge (see RiderMotion).
		// Where the rider's feet go, in the mount's own space: the seat, shifted so the pose's contact point
		// lands on it, plus the forward adjustment. Model x is sideways and z points toward the tail.
		// Sliding off: out to the left side (model +x) and down to the ground.
		float mx = seat.x + motion.x + slide * (halfWidth() + 24);
		float mz = seat.z + motion.z - pose.getContactBack() - style.seatForward;

		// Turn that to face the way the mount faces (orientation 0 faces south).
		double rad = orientation * Math.PI / 1024.0;
		double sin = Math.sin(rad);
		double cos = Math.cos(rad);
		int dx = (int) Math.round(mx * cos + mz * sin);
		int dy = (int) Math.round(mz * cos - mx * sin);
		int ground = Perspective.getTileHeight(client, lp, plane);

		if (saddle != null && saddle.isActive())
		{
			// The saddle rides on the seat point, turned the way the mount faces.
			int sdx = (int) Math.round(seat.x * cos + seat.z * sin);
			int sdy = (int) Math.round(seat.z * cos - seat.x * sin);
			saddle.setLocation(new LocalPoint(lp.getX() + sdx, lp.getY() + sdy, lp.getWorldView()), plane);
			saddle.setOrientation(orientation);
			saddle.setZ(ground + Math.round(seat.y));
		}

		rider.setLocation(new LocalPoint(lp.getX() + dx, lp.getY() + dy, lp.getWorldView()), plane);
		rider.setOrientation(orientation);
		// Settling lifts the rider a little above the seat just after they appear.
		float riderLift = seatLift(style) - pose.getContactHeight() - motion.y; // y points down
		if (slide > 0)
		{
			// Ease down to standing height as the rider slides off.
			riderLift = riderLift * (1 - slide * slide);
		}
		rider.setZ(ground - Math.round(riderLift)); // negative Z is up
		topHeight = Math.max(built.mountHeight, Math.round(riderLift) + RIDER_HEIGHT);

		if (slide > 0)
		{
			setActive(reins, false); // let go of the reins while getting off
		}
		else
		{
			updateReins(frame, pose, style, mx, riderLift, mz, lp, plane, orientation, ground);
		}
	}

	// ------------------------------------------------------------------
	// Saddle
	// ------------------------------------------------------------------

	/** Builds the saddle and blanket moulded to the mount's back in its idle pose. */
	private void buildSaddle(Style style, RiderPose pose)
	{
		saddleTried = true;
		saddleLook = style.saddleStyle;
		saddlePose = pose;
		Model idle = mount.getModel();
		if (idle == null || !seat.isSet())
		{
			return;
		}
		seat.update(idle);
		final float seatX = seat.x, seatZ = seat.z, seatHeight = -seat.y;
		final float[] xs = idle.getVerticesX(), ys = idle.getVerticesY(), zs = idle.getVerticesZ();
		final int[] f1 = idle.getFaceIndices1(), f2 = idle.getFaceIndices2(), f3 = idle.getFaceIndices3();
		final byte[] alphas = idle.getFaceTransparencies();
		final int faces = idle.getFaceCount();
		final float cap = seatHeight * 1.35f + 4; // ignore heads and necks rising above the back
		SaddleMesh.Surface surface = (dx, dz) ->
		{
			SeatFinder.Seat hit = SeatFinder.raycast(xs, ys, zs, f1, f2, f3, alphas, faces, seatX + dx, seatZ + dz, cap);
			return hit == null ? Float.NaN : hit.height - seatHeight;
		};

		ModelData md = freshTemplate();
		if (md == null)
		{
			saddleTried = false; // not loaded yet: try again next tick
			return;
		}

		boolean onTop = pose == RiderPose.CROSS_LEGGED || pose == RiderPose.STANDING;
		SaddleStyle look = style.saddleStyle != null ? style.saddleStyle : SaddleStyle.CLASSIC;
		short trim = look.trim != -1 ? look.trim : style.blanket != null ? SaddleMesh.GOLD : built.trim;
		short blanket = look.blanket != -1 ? look.blanket : blanketColor(style);
		SaddleMesh mesh = SaddleMesh.build(surface, seatHeight, pose == RiderPose.EXTRA_WIDE, !onTop,
			blanket, trim, look, md.getVerticesCount(), md.getFaceCount());

		fill(md, mesh.x, mesh.y, mesh.z, mesh.vertexCount, mesh.f1, mesh.f2, mesh.f3, mesh.faceCount, mesh.color, (short) 0);
		Model model = md.light(64, 850, -30, -50, -30);
		if (model == null)
		{
			return;
		}
		setActive(saddle, false);
		saddle = client.createRuneLiteObject();
		saddle.setModel(model);
		saddleLift = mesh.seatThickness;
		saddle.setActive(saddleOn(style));
	}

	/** Blanket colour: from the pet's colours, deepened so it reads as cloth, or the colour from settings. */
	private short blanketColor(Style style)
	{
		Color[] palette = built.palette;
		Color c = style.blanket != null ? style.blanket : palette[Math.min(1, palette.length - 1)];
		short hsl = JagexColor.rgbToHSL(c.getRGB(), 1.0);
		int hue = JagexColor.unpackHue(hsl);
		int sat = Math.max(4, JagexColor.unpackSaturation(hsl));
		int lum = Math.max(28, Math.min(52, JagexColor.unpackLuminance(hsl)));
		return SaddleMesh.hsl(hue, sat, lum);
	}

	// ------------------------------------------------------------------
	// Reins
	// ------------------------------------------------------------------

	/** Finds the corners of the mount's mouth in its idle pose. */
	private void findBit()
	{
		Model m = mount.getModel();
		if (m == null || !seat.isSet())
		{
			return;
		}
		bitTried = true;
		seat.update(m);
		bit = ReinMesh.findBit(m.getVerticesX(), m.getVerticesY(), m.getVerticesZ(), m.getVerticesCount(),
			m.getFaceIndices1(), m.getFaceIndices2(), m.getFaceIndices3(), m.getFaceTransparencies(),
			m.getFaceCount(), seat.z, -seat.y);
		bitVertexCount = m.getVerticesCount();
	}

	/**
	 * Lays the reins from the rider's hands to the mount's mouth. The rider's feet are at (footX, footZ) in the
	 * mount's space, {@code footLift} above the ground.
	 */
	private void updateReins(Model m, RiderPose pose, Style style, float footX, float footLift, float footZ,
		LocalPoint lp, int plane, int orientation, int ground)
	{
		int[][] hands = ReinMesh.handsFor(pose);
		if (!style.reins || built.shoulders || hands == null || bit == null || m == null
			|| m.getVerticesCount() != bitVertexCount)
		{
			setActive(reins, false);
			return;
		}

		float[] xs = m.getVerticesX(), ys = m.getVerticesY(), zs = m.getVerticesZ();
		float[][] handPoints = new float[2][];
		float[][] bitPoints = new float[2][];
		boolean moved = reins == null;
		for (int i = 0; i < 2; i++)
		{
			handPoints[i] = new float[]{footX + hands[i][0], -(footLift + hands[i][1]), footZ + hands[i][2]};
			bitPoints[i] = new float[]{xs[bit[i]], ys[bit[i]], zs[bit[i]]};
			for (int k = 0; k < 3; k++)
			{
				moved |= Math.abs(reinEnds[i * 6 + k] - handPoints[i][k]) > style.reinStep
					|| Math.abs(reinEnds[i * 6 + 3 + k] - bitPoints[i][k]) > style.reinStep;
			}
		}

		if (moved && !rebuildReins(handPoints, bitPoints))
		{
			return;
		}
		reins.setLocation(lp, plane);
		reins.setOrientation(orientation);
		reins.setZ(ground);
		setActive(reins, true);
	}

	private boolean rebuildReins(float[][] hands, float[][] bits)
	{
		// A fresh copy each time, so the lighting is worked out for the reins' new shape.
		ModelData md = freshTemplate();
		if (md == null || md.getVerticesCount() < reinMesh.vertexCount() || md.getFaceCount() < reinMesh.faceCount())
		{
			return false;
		}
		reinMesh.update(hands, bits);
		fill(md, reinMesh.x, reinMesh.y, reinMesh.z, reinMesh.vertexCount(), reinMesh.f1, reinMesh.f2, reinMesh.f3,
			reinMesh.faceCount(), null, ReinMesh.COLOR);
		Model model = md.light(64, 850, -30, -50, -30);
		if (model == null)
		{
			return false;
		}
		if (reins == null)
		{
			reins = client.createRuneLiteObject();
		}
		reins.setModel(model);
		for (int i = 0; i < 2; i++)
		{
			System.arraycopy(hands[i], 0, reinEnds, i * 6, 3);
			System.arraycopy(bits[i], 0, reinEnds, i * 6 + 3, 3);
		}
		return true;
	}

	// ------------------------------------------------------------------
	// Template model
	// ------------------------------------------------------------------

	/** A private copy of the template, so reshaping it never touches the game's cached model. Null if not loaded. */
	private ModelData freshTemplate()
	{
		if (template == null)
		{
			template = client.loadModelData(TEMPLATE_MODEL);
			if (template == null)
			{
				return null;
			}
		}
		return client.mergeModels(template, template.shallowCopy())
			.cloneVertices()
			.cloneColors()
			.cloneTransparencies(true);
	}

	/** Reshapes the template copy into the given mesh; spare vertices and faces are collapsed and hidden. */
	private static void fill(ModelData md, float[] x, float[] y, float[] z, int vertexCount,
		int[] f1, int[] f2, int[] f3, int faceCount, short[] faceColors, short color)
	{
		float[] vx = md.getVerticesX(), vy = md.getVerticesY(), vz = md.getVerticesZ();
		int[] i1 = md.getFaceIndices1(), i2 = md.getFaceIndices2(), i3 = md.getFaceIndices3();
		short[] colors = md.getFaceColors();
		byte[] trans = md.getFaceTransparencies();
		java.util.Arrays.fill(vx, 0);
		java.util.Arrays.fill(vy, 0);
		java.util.Arrays.fill(vz, 0);
		System.arraycopy(x, 0, vx, 0, vertexCount);
		System.arraycopy(y, 0, vy, 0, vertexCount);
		System.arraycopy(z, 0, vz, 0, vertexCount);
		for (int f = 0; f < md.getFaceCount(); f++)
		{
			boolean used = f < faceCount;
			i1[f] = used ? f1[f] : 0;
			i2[f] = used ? f2[f] : 0;
			i3[f] = used ? f3[f] : 0;
			colors[f] = used ? (faceColors != null ? faceColors[f] : color) : 0;
			trans[f] = used ? 0 : (byte) 255; // spare faces stay invisible
		}
	}
}
