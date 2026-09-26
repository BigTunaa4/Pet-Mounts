package com.petmounts;

import com.google.inject.Provides;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Animation;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.gameval.AnimationID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Pet Mounts",
	description = "Ride your pet like a mount. Your pet grows to a rideable size and carries you around.",
	tags = {"pet", "mount", "ride", "follower", "cosmetic", "fun", "wow"}
)
public class PetMountsPlugin extends Plugin
{
	private static final int SCALE_BASE = 128;
	/** Game ticks to stay off the mount after the last action animation. */
	private static final int ACTION_GRACE_TICKS = 3;
	/** Local units moved per client tick above which we treat the player as running (walk ~4, run ~8). */
	private static final int RUN_SPEED_THRESHOLD = 6;
	/** How long the climb-on animation plays before the mount poofs in. */
	private static final int WINDUP_MS = 1200;
	/** The player beckons their pet over while climbing on. */
	private static final int WINDUP_ANIMATION = AnimationID.EMOTE_BECKON;
	/** Floating pets are lowered so their underside hovers no more than this above the ground. */
	private static final int MAX_HOVER = 12;
	/** Rider never sits higher than this (local units; a player is ~200 tall). */
	private static final int MAX_SEAT_HEIGHT = 150;
	/** Pets at least this wide (local units, after enlarging) get the legs-apart Wide pose. */
	private static final int WIDE_POSE_WIDTH = 90;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private RenderCallbackManager renderCallbackManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private PetMountsConfig config;

	@Inject
	private MountEffects effects;

	// ----- state -----
	/** Whether the player wants to be riding. */
	private boolean riding;
	/** Whether the mount is actually on screen right now. */
	private boolean mountedVisible;
	/** When the climb-on animation started (System.nanoTime), or 0 when not climbing on. */
	private long mountingSince;
	/** Hidden for an action (skilling, combat, teleport) and waiting to climb back on. */
	private boolean pausedForAction;
	/** Colours taken from the current pet, for the effects. */
	private Color[] petPalette = PetPalette.DEFAULT;
	/** Pose picked from the current pet's shape, used when Riding pose is Automatic. */
	private RiderPose autoPose = RiderPose.SADDLE;
	/** Follows the mount's back so the rider moves with it. */
	private final SeatAnchor seatAnchor = new SeatAnchor();

	private RuneLiteObject mount;
	private RiderController rider;

	private int builtForNpcId = -1;
	private int mountHeight;
	private int mountAnimId = -2;

	private int lastBusyTick = -100;
	private int tickCount;

	private int lastX = Integer.MIN_VALUE;
	private int lastY = Integer.MIN_VALUE;
	private int speed;
	/** Client ticks left before we treat the player as standing still (smooths walk/idle flicker). */
	private int movingFrames;
	/** Whether the player moved during the latest client tick. */
	private boolean movedThisTick;

	/** Rideability verdict per pet NPC id (cleared when settings change). */
	private final Map<Integer, PetRules.Verdict> verdicts = new HashMap<>();

	/** Player's own movement animations, captured so they can be restored on dismount. */
	private int[] savedPose;

	private final RenderCallback renderCallback = new RenderCallback()
	{
		@Override
		public boolean addEntity(Renderable renderable, boolean ui)
		{
			return shouldDraw(renderable, ui);
		}
	};

	private final HotkeyListener hotkeyListener = new HotkeyListener(() -> config.mountHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			clientThread.invoke(PetMountsPlugin.this::toggleRiding);
		}
	};

	@Provides
	PetMountsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PetMountsConfig.class);
	}

	@Override
	protected void startUp()
	{
		renderCallbackManager.register(renderCallback);
		keyManager.registerKeyListener(hotkeyListener);

		riding = config.remountOnLogin() && config.wasMounted();
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(hotkeyListener);
		renderCallbackManager.unregister(renderCallback);
		clientThread.invoke(() ->
		{
			effects.clear();
			cancelMounting(null);
			hideMount();
			mount = null;
			rider = null;
			builtForNpcId = -1;
		});
	}

	// ------------------------------------------------------------------
	// Toggling
	// ------------------------------------------------------------------

	private void toggleRiding()
	{
		setRiding(!(riding || mountingSince != 0), true);
	}

	private void setRiding(boolean wantRide, boolean announce)
	{
		if (wantRide)
		{
			startMounting(announce);
		}
		else
		{
			dismount(announce);
		}
	}

	private void startMounting(boolean announce)
	{
		if (riding || mountingSince != 0)
		{
			return;
		}

		NPC pet = client.getFollower();
		Player me = client.getLocalPlayer();
		if (pet == null || me == null)
		{
			if (announce)
			{
				message("You need one of your pets following you to ride it.");
			}
			return;
		}

		String refusal = refusalFor(pet);
		if (refusal != null)
		{
			if (announce)
			{
				message(refusal);
			}
			return;
		}

		// Build the mount now so we know its size (for where the effects and poof go).
		NPCComposition comp = compositionOf(pet);
		if (comp != null && (mount == null || builtForNpcId != comp.getId()))
		{
			buildMount(comp);
		}

		if (!config.mountEffects())
		{
			finishMounting(announce);
			return;
		}

		mountingSince = System.nanoTime();
		me.setAnimation(WINDUP_ANIMATION);
		me.setAnimationFrame(0);
		effects.windup(effectColor());
	}

	private void finishMounting(boolean announce)
	{
		mountingSince = 0;
		Player me = client.getLocalPlayer();
		if (me != null && me.getAnimation() == WINDUP_ANIMATION)
		{
			me.setAnimation(-1);
		}

		riding = true;
		config.wasMounted(true);

		if (config.mountEffects() && me != null)
		{
			effects.poof(effectColor(), seatLift() / 3);
		}
		if (announce)
		{
			message("You climb onto " + petName() + ".");
		}
	}

	/**
	 * Stops a climb-on that hasn't finished yet.
	 * @param reason chat message to show, or null for none
	 */
	private void cancelMounting(String reason)
	{
		if (mountingSince == 0)
		{
			return;
		}
		mountingSince = 0;
		effects.endWindup();
		Player me = client.getLocalPlayer();
		if (me != null && me.getAnimation() == WINDUP_ANIMATION)
		{
			me.setAnimation(-1);
		}
		if (reason != null)
		{
			message(reason);
		}
	}

	private void dismount(boolean announce)
	{
		if (mountingSince != 0)
		{
			cancelMounting(announce ? "You decide not to climb on." : null);
			return;
		}
		if (!riding)
		{
			return;
		}

		boolean wasVisible = mountedVisible;
		riding = false;
		config.wasMounted(false);
		hideMount();

		Player me = client.getLocalPlayer();
		if (wasVisible && config.mountEffects() && me != null)
		{
			effects.poof(effectColor(), 0);
		}
		if (announce)
		{
			message("You hop off " + petName() + ".");
		}
	}

	private String petName()
	{
		NPC pet = client.getFollower();
		return pet != null && pet.getName() != null ? Text.removeTags(pet.getName()) : "your pet";
	}

	/** Colour for the effects: the pet's main colour, or the colour chosen in settings. */
	private Color effectColor()
	{
		return config.matchPetColors() ? petPalette[0] : config.effectColor();
	}

	private int seatLift()
	{
		return Math.min(MAX_SEAT_HEIGHT, mountHeight * config.seatHeight() / 100);
	}

	private void message(String text)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", text, null);
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted e)
	{
		switch (e.getCommand().toLowerCase())
		{
			case "ride":
			case "mount":
				setRiding(true, true);
				break;
			case "dismount":
				setRiding(false, true);
				break;
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded e)
	{
		if (!config.showMenuOptions() || riding || mountingSince != 0)
		{
			return;
		}

		MenuEntry entry = e.getMenuEntry();
		NPC follower = client.getFollower();
		if (follower == null || entry.getType() != MenuAction.EXAMINE_NPC || entry.getNpc() != follower
			|| refusalFor(follower) != null)
		{
			return;
		}

		client.getMenu().createMenuEntry(-1)
			.setOption("Ride")
			.setTarget(entry.getTarget())
			.setType(MenuAction.RUNELITE)
			.onClick(me -> setRiding(true, true));
	}

	@Subscribe
	public void onMenuOpened(MenuOpened e)
	{
		if (!riding && mountingSince == 0)
		{
			return;
		}

		// Always offered while riding. Index 1 sits just above "Cancel", so it never becomes the left-click option.
		client.getMenu().createMenuEntry(1)
			.setOption("Dismount")
			.setTarget("<col=ffff00>" + petName() + "</col>")
			.setType(MenuAction.RUNELITE)
			.setDeprioritized(true)
			.onClick(me -> setRiding(false, true));
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (!PetMountsConfig.GROUP.equals(e.getGroup()) || "wasMounted".equals(e.getKey()))
		{
			return;
		}
		// Size or pose settings changed: rebuild the mount model and re-apply the pose next tick.
		clientThread.invoke(() ->
		{
			builtForNpcId = -1;
			verdicts.clear();
			restorePose();
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() == GameState.LOGIN_SCREEN || e.getGameState() == GameState.HOPPING)
		{
			cancelMounting(null);
			effects.clear();
			pausedForAction = false;
			hideMount();
			mount = null;
			rider = null;
			builtForNpcId = -1;
			savedPose = null;
			riding = config.remountOnLogin() && config.wasMounted();
		}
		else if (e.getGameState() == GameState.LOADING)
		{
			// Scene is being rebuilt; re-register objects afterwards.
			hideMount();
		}
	}

	// ------------------------------------------------------------------
	// Per-tick logic
	// ------------------------------------------------------------------

	@Subscribe
	public void onGameTick(GameTick e)
	{
		tickCount++;
		Player me = client.getLocalPlayer();
		if (me == null || !config.hopOffForActions() || mountingSince != 0)
		{
			return;
		}

		if (me.getAnimation() != -1 || isFighting(me))
		{
			lastBusyTick = tickCount;
		}
	}

	private static boolean isFighting(Player me)
	{
		Actor target = me.getInteracting();
		if (target == null)
		{
			return false;
		}
		if (target instanceof NPC)
		{
			return ((NPC) target).getCombatLevel() > 0;
		}
		return target instanceof Player;
	}

	@Subscribe
	public void onClientTick(ClientTick e)
	{
		Player me = client.getLocalPlayer();
		NPC pet = client.getFollower();

		if (me == null)
		{
			return;
		}

		trackSpeed(me);
		effects.tick();

		if (mountingSince != 0)
		{
			if (pet == null)
			{
				cancelMounting("Your pet wandered off before you could climb on.");
			}
			else if (movedThisTick)
			{
				cancelMounting("You need to stand still to climb onto your pet.");
			}
			else if ((System.nanoTime() - mountingSince) / 1_000_000L >= WINDUP_MS)
			{
				finishMounting(true);
			}
		}

		boolean busy = config.hopOffForActions() && tickCount - lastBusyTick < ACTION_GRACE_TICKS;
		if (riding && busy && mountedVisible)
		{
			// Hopping off to do something: a small puff hides the mount leaving.
			pausedForAction = true;
			if (config.mountEffects())
			{
				effects.puff(effectColor(), 0);
			}
		}
		if (!riding || pet == null || busy)
		{
			if (!riding)
			{
				pausedForAction = false;
			}
			hideMount();
			return;
		}

		NPCComposition comp = compositionOf(pet);
		if (comp == null)
		{
			hideMount();
			return;
		}

		String refusal = refusalFor(pet);
		if (refusal != null)
		{
			setRiding(false, false);
			message(refusal);
			return;
		}

		if (mount == null || builtForNpcId != comp.getId())
		{
			if (!buildMount(comp))
			{
				hideMount(); // model data not loaded yet, try again next tick
				return;
			}
		}

		if (rider == null || !rider.isFor(me))
		{
			rider = new RiderController(me);
		}

		updateMountAnimation(pet);
		applyRiderPose(me);
		positionObjects(me);

		if (!mount.isActive())
		{
			mount.setActive(true);
		}
		if (!client.isRuneLiteObjectRegistered(rider))
		{
			client.registerRuneLiteObject(rider);
		}
		if (pausedForAction)
		{
			// Climbing back on after an action.
			pausedForAction = false;
			if (config.mountEffects())
			{
				effects.puff(effectColor(), seatLift() / 3);
			}
		}
		mountedVisible = true;
	}

	private void trackSpeed(Player me)
	{
		LocalPoint lp = me.getLocalLocation();
		if (lastX != Integer.MIN_VALUE)
		{
			int moved = Math.max(Math.abs(lp.getX() - lastX), Math.abs(lp.getY() - lastY));
			movedThisTick = moved > 0;
			if (moved > 0)
			{
				speed = moved;
				movingFrames = 5;
			}
			else if (movingFrames > 0)
			{
				movingFrames--;
			}
			else
			{
				speed = 0;
			}
		}
		lastX = lp.getX();
		lastY = lp.getY();
	}

	// ------------------------------------------------------------------
	// Which pets can be ridden
	// ------------------------------------------------------------------

	/**
	 * @return a chat message explaining why this follower can't be ridden, or null if it can
	 */
	private String refusalFor(NPC pet)
	{
		NPCComposition comp = compositionOf(pet);
		String name = comp != null && comp.getName() != null ? Text.removeTags(comp.getName()) : "That";

		// Only the player's own pets: client.getFollower() is always the local player's follower,
		// and real pets (unlike quest companions) can be picked up.
		if (comp == null || pet != client.getFollower() || !isOwnedPet(comp))
		{
			return name + " isn't one of your pets, so you can't ride it.";
		}

		PetRules.Verdict verdict = verdicts.get(comp.getId());
		if (verdict == null)
		{
			ModelData md = loadPetModel(comp);
			PetRules.Shape shape = md != null ? measure(md, comp) : null;
			verdict = PetRules.check(comp.getName(), shape,
				PetRules.parseList(config.alwaysAllow()), PetRules.parseList(config.neverAllow()));
			if (md != null)
			{
				verdicts.put(comp.getId(), verdict); // only cache once the shape could be checked
			}
		}

		return verdict.rideable ? null : name + " " + verdict.reason.text;
	}

	private static boolean isOwnedPet(NPCComposition comp)
	{
		if (!comp.isFollower())
		{
			return false;
		}
		String[] actions = comp.getActions();
		if (actions == null)
		{
			return false;
		}
		for (String action : actions)
		{
			if (action != null && action.equalsIgnoreCase("Pick-up"))
			{
				return true;
			}
		}
		return false;
	}

	private static PetRules.Shape measure(ModelData md, NPCComposition comp)
	{
		float[] xs = md.getVerticesX();
		float[] ys = md.getVerticesY();
		float[] zs = md.getVerticesZ();
		float minX = 0, maxX = 0, minY = 0, maxY = 0, minZ = 0, maxZ = 0;
		for (int i = 0; i < md.getVerticesCount(); i++)
		{
			minX = Math.min(minX, xs[i]);
			maxX = Math.max(maxX, xs[i]);
			minY = Math.min(minY, ys[i]);
			maxY = Math.max(maxY, ys[i]);
			minZ = Math.min(minZ, zs[i]);
			maxZ = Math.max(maxZ, zs[i]);
		}
		float ws = (comp.getWidthScale() > 0 ? comp.getWidthScale() : SCALE_BASE) / (float) SCALE_BASE;
		float hs = (comp.getHeightScale() > 0 ? comp.getHeightScale() : SCALE_BASE) / (float) SCALE_BASE;

		// Model Y points down: the top is at -minY, and maxY < 0 means the model's lowest point is above the ground.
		float height = -minY * hs;
		float lift = Math.max(0, -maxY) * hs;
		float footprint = Math.max(maxX - minX, maxZ - minZ) * ws;
		return new PetRules.Shape(height, lift, footprint);
	}

	private NPCComposition compositionOf(NPC pet)
	{
		NPCComposition comp = pet.getTransformedComposition();
		return comp != null ? comp : pet.getComposition();
	}

	/** Loads the pet's models merged and recoloured, at their original size. Null if not loaded yet. */
	private ModelData loadPetModel(NPCComposition comp)
	{
		int[] modelIds = comp.getModels();
		if (modelIds == null || modelIds.length == 0)
		{
			return null;
		}

		ModelData[] parts = new ModelData[modelIds.length];
		for (int i = 0; i < modelIds.length; i++)
		{
			parts[i] = client.loadModelData(modelIds[i]);
			if (parts[i] == null)
			{
				return null;
			}
		}

		ModelData md = client.mergeModels(parts)
			.cloneVertices()
			.cloneColors();

		short[] find = comp.getColorToReplace();
		short[] repl = comp.getColorToReplaceWith();
		if (find != null && repl != null)
		{
			for (int i = 0; i < Math.min(find.length, repl.length); i++)
			{
				md.recolor(find[i], repl[i]);
			}
		}
		return md;
	}

	// ------------------------------------------------------------------
	// Mount model
	// ------------------------------------------------------------------

	private boolean buildMount(NPCComposition comp)
	{
		ModelData md = loadPetModel(comp);
		if (md == null)
		{
			return false;
		}

		int ws = comp.getWidthScale() > 0 ? comp.getWidthScale() : SCALE_BASE;
		int hs = comp.getHeightScale() > 0 ? comp.getHeightScale() : SCALE_BASE;

		// Height of the pet as the game normally draws it (model Y points down, so up is negative).
		// Size by the body itself, ignoring any gap under a floating pet.
		float gap = Math.max(0, -maxY(md));
		int naturalHeight = Math.max(1, Math.round((-minY(md) - gap) * hs / (float) SCALE_BASE));
		float growth = MountSizing.growthFactor(naturalHeight, config.targetHeight(),
			config.maxGrowth() / 100f, config.sizeMultiplier() / 100f);

		int sx = Math.round(ws * growth);
		int sy = Math.round(hs * growth);
		md.scale(sx, sy, sx);

		// Floating pets: bring them down to a gentle hover so the rider isn't up in the air.
		// Model Y points down, so a negative lowest point means the model floats above the ground.
		float lowest = maxY(md);
		boolean floating = lowest < -MAX_HOVER;
		if (floating)
		{
			md.translate(0, Math.round(-lowest - MAX_HOVER), 0);
		}
		mountHeight = Math.max(1, Math.round(-minY(md)));

		// Choose a natural riding pose for this pet's shape.
		autoPose = floating ? RiderPose.CROSS_LEGGED
			: width(md) >= WIDE_POSE_WIDTH ? RiderPose.WIDE
			: RiderPose.SADDLE;

		petPalette = PetPalette.fromModel(md.getFaceColors(), md.getFaceTextures());

		Model model = md.light();
		if (model == null)
		{
			return false;
		}

		if (mount != null)
		{
			mount.setActive(false);
		}
		mount = client.createRuneLiteObject();
		mount.setModel(model);
		mount.setShouldLoop(true);
		mountAnimId = -2;
		builtForNpcId = comp.getId();
		seatAnchor.select(model, seatLift(), config.seatForward(), mountHeight);

		log.debug("Built mount for npc {} ({}): natural height {}, growth {}x, mount height {}, auto pose {}",
			comp.getId(), comp.getName(), naturalHeight, growth, mountHeight, autoPose);
		return true;
	}

	/** Side-to-side width of the model. */
	private static float width(ModelData md)
	{
		float[] xs = md.getVerticesX();
		float min = 0, max = 0;
		for (int i = 0; i < md.getVerticesCount(); i++)
		{
			min = Math.min(min, xs[i]);
			max = Math.max(max, xs[i]);
		}
		return max - min;
	}

	private static float maxY(ModelData md)
	{
		float[] ys = md.getVerticesY();
		float max = Float.NEGATIVE_INFINITY;
		for (int i = 0; i < md.getVerticesCount(); i++)
		{
			max = Math.max(max, ys[i]);
		}
		return max == Float.NEGATIVE_INFINITY ? 0 : max;
	}

	private static float minY(ModelData md)
	{
		float[] ys = md.getVerticesY();
		float min = 0;
		for (int i = 0; i < md.getVerticesCount(); i++)
		{
			min = Math.min(min, ys[i]);
		}
		return min;
	}

	private void updateMountAnimation(NPC pet)
	{
		int anim;
		if (speed >= RUN_SPEED_THRESHOLD && pet.getRunAnimation() != -1)
		{
			anim = pet.getRunAnimation();
		}
		else if (speed > 0 && pet.getWalkAnimation() != -1)
		{
			anim = pet.getWalkAnimation();
		}
		else
		{
			anim = pet.getIdlePoseAnimation();
		}

		if (anim == mountAnimId)
		{
			return;
		}
		mountAnimId = anim;
		Animation a = anim == -1 ? null : client.loadAnimation(anim);
		mount.setAnimation(a);
		mount.setShouldLoop(true);
	}

	private void positionObjects(Player me)
	{
		LocalPoint lp = me.getLocalLocation();
		int plane = me.getWorldView().getPlane();
		int orientation = me.getCurrentOrientation();

		mount.setOrientation(orientation);
		mount.setLocation(lp, plane);

		// Follow the mount's back as it animates.
		seatAnchor.update(mount.getModel());

		// Seat offset in the mount's own frame, turned to face the way the mount faces.
		// Model x is sideways and model z points toward the tail; orientation 0 faces south.
		int sideways = seatAnchor.sideways;
		int back = -(config.seatForward() + seatAnchor.forward);
		double rad = orientation * Math.PI / 1024.0;
		double sin = Math.sin(rad);
		double cos = Math.cos(rad);
		int dx = (int) Math.round(sideways * cos + back * sin);
		int dy = (int) Math.round(back * cos - sideways * sin);

		LocalPoint seat = new LocalPoint(lp.getX() + dx, lp.getY() + dy, lp.getWorldView());
		rider.setLocation(seat, plane);
		rider.setOrientation(orientation);
		int ground = Perspective.getTileHeight(client, lp, plane);
		// Put the rider's hips (not feet) on the seat: chair-style poses already raise the hips.
		int riderLift = seatLift() + seatAnchor.height - resolvedPose().getHipHeight();
		rider.setZ(ground - riderLift); // negative Z is up
	}

	// ------------------------------------------------------------------
	// Rider pose
	// ------------------------------------------------------------------

	private RiderPose resolvedPose()
	{
		RiderPose pose = config.riderPose();
		return pose == null || pose == RiderPose.AUTO ? autoPose : pose;
	}

	private int poseAnimation(Player me)
	{
		RiderPose pose = resolvedPose();
		if (pose == RiderPose.STANDING || pose.getAnimationId() == -1)
		{
			return savedPose != null ? savedPose[0] : me.getIdlePoseAnimation();
		}
		return pose.getAnimationId();
	}

	private void applyRiderPose(Player me)
	{
		int pose = poseAnimation(me);

		// If the game has reset the player's movement anims (e.g. after an equipment change),
		// capture the fresh set so we restore the right ones when dismounting.
		if (savedPose == null || me.getWalkAnimation() != pose)
		{
			savedPose = new int[]{
				me.getIdlePoseAnimation(),
				me.getWalkAnimation(),
				me.getRunAnimation(),
				me.getIdleRotateLeft(),
				me.getIdleRotateRight(),
				me.getWalkRotateLeft(),
				me.getWalkRotateRight(),
				me.getWalkRotate180(),
			};
			pose = poseAnimation(me);
		}

		me.setIdlePoseAnimation(pose);
		me.setWalkAnimation(pose);
		me.setRunAnimation(pose);
		me.setIdleRotateLeft(pose);
		me.setIdleRotateRight(pose);
		me.setWalkRotateLeft(pose);
		me.setWalkRotateRight(pose);
		me.setWalkRotate180(pose);
		RiderPose rp = resolvedPose();
		if (me.getPoseAnimation() != pose)
		{
			me.setPoseAnimation(pose);
			me.setPoseAnimationFrame(rp.controlsFrames() ? rp.getLoopStart() : 0);
		}

		// Some seated poses come from one-off emotes: hold one frame, or loop just the settled part.
		if (rp.controlsFrames())
		{
			int frame = me.getPoseAnimationFrame();
			boolean hold = rp.getLoopStart() == rp.getLoopEnd();
			if (hold ? frame != rp.getLoopStart() : frame < rp.getLoopStart() || frame >= rp.getLoopEnd())
			{
				me.setPoseAnimationFrame(rp.getLoopStart());
			}
		}
	}

	private void restorePose()
	{
		Player me = client.getLocalPlayer();
		if (me == null || savedPose == null)
		{
			savedPose = null;
			return;
		}

		me.setIdlePoseAnimation(savedPose[0]);
		me.setWalkAnimation(savedPose[1]);
		me.setRunAnimation(savedPose[2]);
		me.setIdleRotateLeft(savedPose[3]);
		me.setIdleRotateRight(savedPose[4]);
		me.setWalkRotateLeft(savedPose[5]);
		me.setWalkRotateRight(savedPose[6]);
		me.setWalkRotate180(savedPose[7]);
		me.setPoseAnimation(speed > 0 ? savedPose[1] : savedPose[0]);
		me.setPoseAnimationFrame(0);
		savedPose = null;
	}

	private void hideMount()
	{
		if (mount != null && mount.isActive())
		{
			mount.setActive(false);
		}
		if (rider != null && client.isRuneLiteObjectRegistered(rider))
		{
			client.removeRuneLiteObject(rider);
		}
		if (mountedVisible)
		{
			restorePose();
		}
		mountedVisible = false;
	}

	// ------------------------------------------------------------------
	// Rendering
	// ------------------------------------------------------------------

	private boolean shouldDraw(Renderable renderable, boolean drawingUi)
	{
		// Keep overhead text, hitsplats and health bars (drawn as UI) visible.
		if (!mountedVisible || drawingUi)
		{
			return true;
		}
		// Hide the real pet (the enlarged mount replaces it) and the real player
		// (the rider copy is drawn on the mount's back).
		return renderable != client.getFollower() && renderable != client.getLocalPlayer();
	}
}
