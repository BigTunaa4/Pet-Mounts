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

	private RuneLiteObject saddle;
	private boolean saddleTried;
	private float saddleLift;
	private RiderPose saddlePose;

	private RuneLiteObject reins;
	private final ReinMesh reinMesh = new ReinMesh();
	private ModelData template;
	private int[] bit;
	private boolean bitTried;
	private int bitVertexCount;
	private final float[] reinEnds = new float[12];

	private boolean visible;
	private NPC pet;
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
		mount = client.createRuneLiteObject();
		mount.setModel(built.model);
		mount.setShouldLoop(true);
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
		LocalPoint lp = player.getLocalLocation();
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
		this.pet = pet;
		RiderPose pose = poseFor(style);

		updateAnimation(pet, gait);
		poser.apply(player, pose);
		look.apply(player, style.hideHeld, style.hideCape);

		boolean idle = animationId == idleAnimationId;
		if (style.saddle && idle && (!saddleTried || saddlePose != pose))
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

		position(pose, gait, style);

		if (!mount.isActive())
		{
			mount.setActive(true);
		}
		if (saddle != null && saddle.isActive() != style.saddle)
		{
			saddle.setActive(style.saddle);
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

	private void updateAnimation(NPC pet, int gait)
	{
		int walk = pet.getWalkAnimation();
		int run = pet.getRunAnimation();
		idleAnimationId = pet.getIdlePoseAnimation();

		int anim;
		float pace = 1f;
		if (gait == 2 && run != -1 && run != walk)
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

	private void position(RiderPose pose, int gait, Style style)
	{
		LocalPoint lp = player.getLocalLocation();
		int plane = player.getWorldView().getPlane();
		int orientation = player.getCurrentOrientation();

		mount.setOrientation(orientation);
		mount.setLocation(lp, plane);

		// Follow the seat on the mount's back as it animates.
		seat.update(mount.getModel());

		// The rider follows the seat through a spring, with stride sway and surge (see RiderMotion).
		float cycle = animation != null && gait > 0 ? animation.cycle() : 0;
		motion.update(seat.x, seat.y, seat.z, gait, cycle, style.naturalMotion);

		// Where the rider's feet go, in the mount's own space: the seat, shifted so the pose's contact point
		// lands on it, plus the forward adjustment. Model x is sideways and z points toward the tail.
		float mx = motion.x;
		float mz = motion.z - pose.getContactBack() - style.seatForward;

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
		// seatLift is measured from the resting seat; add how far the smoothed, settling rider is from it.
		float riderLift = seatLift(style) - pose.getContactHeight() + (seat.y - motion.y);
		rider.setZ(ground - Math.round(riderLift)); // negative Z is up
		topHeight = Math.max(built.mountHeight, Math.round(riderLift) + RIDER_HEIGHT);

		updateReins(pose, style, mx, riderLift, mz, lp, plane, orientation, ground);
	}

	// ------------------------------------------------------------------
	// Saddle
	// ------------------------------------------------------------------

	/** Builds the saddle and blanket moulded to the mount's back in its idle pose. */
	private void buildSaddle(Style style, RiderPose pose)
	{
		saddleTried = true;
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
		SaddleMesh mesh = SaddleMesh.build(surface, seatHeight, pose == RiderPose.EXTRA_WIDE, !onTop,
			blanketColor(style), SaddleMesh.GOLD, md.getVerticesCount(), md.getFaceCount());

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
		saddle.setActive(style.saddle);
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
	private void updateReins(RiderPose pose, Style style, float footX, float footLift, float footZ, LocalPoint lp,
		int plane, int orientation, int ground)
	{
		int[][] hands = ReinMesh.handsFor(pose);
		Model m = mount.getModel();
		if (!style.reins || hands == null || bit == null || m == null || m.getVerticesCount() != bitVertexCount)
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
