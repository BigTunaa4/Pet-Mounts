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
import net.runelite.client.input.MouseManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
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
	/** Untuned pets grow until the seat is about this high: horse height, so legs hang down naturally. */
	private static final int TARGET_SEAT_HEIGHT = 105;
	private static final float MAX_GROWTH = 4.5f;
	/** Untuned pets at least this wide at the seat (half-width, after enlarging) get the Extra wide pose. */
	private static final int EXTRA_WIDE_HALF_WIDTH = 40;

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

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private MouseManager mouseManager;

	private MountStablePanel panel;
	private NavigationButton navButton;
	private MountButtonOverlay mountButton;
	private MountStablePanel.State panelState;
	private int panelTicks;

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
	/** Pose tuned for (or picked from the shape of) the current pet, used when Riding pose is Automatic. */
	private RiderPose autoPose = RiderPose.WIDE;
	/** Spare model reshaped into the saddle; any stable model with enough vertices and faces works. */
	private static final int SADDLE_TEMPLATE_MODEL = 25754;

	/** Saddle and blanket, built once the mount stands in its idle pose. */
	private RuneLiteObject saddle;
	private boolean saddleTried;
	/** How far the saddle raises the rider above the pet's back. */
	private float saddleLift;

	/** Reins from the rider's hands to the corners of the mount's mouth, rebuilt as both move. */
	private RuneLiteObject reins;
	private final ReinMesh reinMesh = new ReinMesh();
	private ModelData reinTemplate;
	/** Mouth corners on the mount (left, right vertex), or null if it has none; found once in the idle pose. */
	private int[] bit;
	private boolean bitTried;
	private int bitVertexCount;
	/** The rein ends the current reins model was built for, so it's only rebuilt when they move. */
	private final float[] reinEnds = new float[12];
	/** Settling, stride sway and surge for the rider. */
	private final RiderMotion motion = new RiderMotion();
	/** The mount's idle animation, so the saddle is moulded to the pose you see most. */
	private int idleAnimId = -1;
	private PacedAnimationController mountAnimation;
	/** Walk animation speed when running on a pet that has no run animation. */
	private static final float RUN_PACE = 1.8f;
	/** The player's saved adjustments for the pet being ridden. */
	private PetTweaks tweaks = PetTweaks.NONE;
	private String currentPetName;

	/** Hides the rider's weapon, shield and cape while mounted. */
	private final RiderLook riderLook = new RiderLook();
	/** Follows the spot on the mount's back where the rider sits. */
	private final SeatTracker seat = new SeatTracker();
	/** Frame counts of the rider pose animations, so held frames stay in range. */
	private final Map<Integer, Integer> poseFrameCounts = new HashMap<>();

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

		panel = new MountStablePanel(new MountStablePanel.Actions()
		{
			@Override
			public void toggleRide()
			{
				clientThread.invoke(PetMountsPlugin.this::toggleRiding);
			}

			@Override
			public void saveTweaks(PetTweaks t)
			{
				clientThread.invoke(() -> savePetTweaks(t));
			}

			@Override
			public void setOption(String key, boolean value)
			{
				configManager.setConfiguration(PetMountsConfig.GROUP, key, value);
			}
		});
		navButton = NavigationButton.builder()
			.tooltip("Pet Mounts")
			.icon(ImageUtil.loadImageResource(getClass(), "panel_icon.png"))
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		mountButton = new MountButtonOverlay(this, ImageUtil.loadImageResource(getClass(), "button_icon.png"),
			() -> config.showMountButton() && client.getGameState() == GameState.LOGGED_IN,
			() -> riding || mountingSince != 0,
			() -> panelState != null && panelState.canRide,
			() -> clientThread.invoke(this::toggleRiding));
		overlayManager.add(mountButton);
		mouseManager.registerMouseListener(mountButton.mouse);

		riding = config.remountOnLogin() && config.wasMounted();
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(hotkeyListener);
		renderCallbackManager.unregister(renderCallback);
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(mountButton);
		mouseManager.unregisterMouseListener(mountButton.mouse);
		panelState = null;
		clientThread.invoke(() ->
		{
			effects.clear();
			removeSaddle();
			removeReins();
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

	/** Height of the seat above the ground right now, including the player's adjustment. */
	private int seatLift()
	{
		float h = seat.isSet() ? -seat.y : mountHeight * 0.6f;
		if (saddle != null && saddle.isActive())
		{
			h += saddleLift;
		}
		return Math.round(h) + config.seatHeightAdjust() + tweaks.seatHeight;
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
			seat.clear();
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
			riderLook.forget();
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
		if (++panelTicks % 10 == 0) // about five times a second is plenty for the side panel
		{
			refreshPanel(pet);
		}

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
		riderLook.apply(me, config.hideHeldItems(), config.hideCape());
		if (config.showSaddle() && !saddleTried && mountAnimId == idleAnimId)
		{
			buildSaddle();
		}
		if (!bitTried && mountAnimId == idleAnimId)
		{
			findBit();
		}
		if (!mountedVisible)
		{
			// Just appeared (after the poof, or climbing back on): drop into the saddle.
			motion.reset(config.naturalMotion() && config.mountEffects());
		}
		positionObjects(me);

		if (!mount.isActive())
		{
			mount.setActive(true);
		}
		if (saddle != null && saddle.isActive() != config.showSaddle())
		{
			saddle.setActive(config.showSaddle());
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
			verdict = PetRules.check(comp.getId(), comp.getName(), shape,
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

		// A tuned seat is only valid for the exact model it was measured on.
		MountFits.Fit fit = MountFits.get(comp.getId());
		if (fit != null && fit.vertexCount != md.getVerticesCount())
		{
			log.debug("Pet {} model changed since tuning ({} vs {} vertices); finding a seat automatically",
				comp.getId(), md.getVerticesCount(), fit.vertexCount);
			fit = null;
		}

		// How much to enlarge the pet: tuned, or grown until its back is about horse height.
		float growth;
		SeatFinder.Seat found = null;
		if (fit != null)
		{
			growth = fit.growth;
		}
		else
		{
			md.scale(ws, hs, ws); // the pet at its normal in-game size, to measure it
			found = findSeat(md);
			float seatHeight = found != null ? found.height - Math.max(0, -maxY(md)) : -minY(md) * 0.6f;
			growth = MountSizing.growthFactor(Math.round(seatHeight), TARGET_SEAT_HEIGHT, MAX_GROWTH, 1f);
			ws = hs = SCALE_BASE; // already applied
		}
		currentPetName = comp.getName() == null ? null : Text.removeTags(comp.getName());
		tweaks = currentPetName == null ? PetTweaks.NONE : PetTweaks.load(configManager, currentPetName);
		growth *= config.sizeMultiplier() / 100f * tweaks.size / 100f;

		md.scale(Math.round(ws * growth), Math.round(hs * growth), Math.round(ws * growth));

		// Floating pets: bring them down to a gentle hover so the rider isn't up in the air.
		// Model Y points down, so a negative lowest point means the model floats above the ground.
		float lowest = maxY(md);
		boolean floating = lowest < -MAX_HOVER;
		if (floating)
		{
			md.translate(0, Math.round(-lowest - MAX_HOVER), 0);
		}
		mountHeight = Math.max(1, Math.round(-minY(md)));

		// The seat and pose: tuned, or found on the enlarged model.
		int sa, sb, sc;
		float swa, swb, swc;
		if (fit != null)
		{
			autoPose = fit.pose;
			sa = fit.a;
			sb = fit.b;
			sc = fit.c;
			swa = fit.wa;
			swb = fit.wb;
			swc = fit.wc;
		}
		else
		{
			found = findSeat(md);
			if (found == null)
			{
				return false;
			}
			autoPose = floating ? RiderPose.CROSS_LEGGED
				: halfWidthAt(md, found) >= EXTRA_WIDE_HALF_WIDTH ? RiderPose.EXTRA_WIDE
				: RiderPose.WIDE;
			sa = found.a;
			sb = found.b;
			sc = found.c;
			swa = found.wa;
			swb = found.wb;
			swc = found.wc;
		}

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
		removeSaddle();
		removeReins();
		mount = client.createRuneLiteObject();
		mount.setModel(model);
		mount.setShouldLoop(true);
		mountAnimId = -2;
		builtForNpcId = comp.getId();
		if (model.getVerticesCount() != md.getVerticesCount())
		{
			// The lit model numbers its vertices differently: find the seat on it directly.
			SeatFinder.Seat onModel = SeatFinder.find(model.getVerticesX(), model.getVerticesY(), model.getVerticesZ(),
				model.getVerticesCount(), model.getFaceIndices1(), model.getFaceIndices2(), model.getFaceIndices3(),
				model.getFaceTransparencies(), model.getFaceCount());
			if (onModel == null)
			{
				return false;
			}
			sa = onModel.a;
			sb = onModel.b;
			sc = onModel.c;
			swa = onModel.wa;
			swb = onModel.wb;
			swc = onModel.wc;
		}
		seat.set(sa, sb, sc, swa, swb, swc, model, mountHeight);

		log.debug("Built mount for npc {} ({}): {}, growth {}x, seat height {}, pose {}",
			comp.getId(), comp.getName(), fit != null ? "tuned" : "automatic", growth, -seat.y, autoPose);
		return true;
	}

	private static SeatFinder.Seat findSeat(ModelData md)
	{
		return SeatFinder.find(md.getVerticesX(), md.getVerticesY(), md.getVerticesZ(), md.getVerticesCount(),
			md.getFaceIndices1(), md.getFaceIndices2(), md.getFaceIndices3(), md.getFaceTransparencies(), md.getFaceCount());
	}

	/** How far the body reaches to either side around the seat. */
	private static float halfWidthAt(ModelData md, SeatFinder.Seat s)
	{
		float[] xs = md.getVerticesX();
		float[] zs = md.getVerticesZ();
		float seatZ = s.wa * zs[s.a] + s.wb * zs[s.b] + s.wc * zs[s.c];
		float seatX = s.wa * xs[s.a] + s.wb * xs[s.b] + s.wc * xs[s.c];
		float half = 0;
		for (int v = 0; v < md.getVerticesCount(); v++)
		{
			if (Math.abs(zs[v] - seatZ) < 12)
			{
				half = Math.max(half, Math.abs(xs[v] - seatX));
			}
		}
		return half;
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
		boolean running = speed >= RUN_SPEED_THRESHOLD;
		int walk = pet.getWalkAnimation();
		int run = pet.getRunAnimation();
		idleAnimId = pet.getIdlePoseAnimation();

		int anim;
		float pace = 1f;
		if (running && run != -1 && run != walk)
		{
			anim = run;
		}
		else if (speed > 0 && walk != -1)
		{
			anim = walk;
			// No run animation: play the walk at running pace so the legs keep up with the ground.
			pace = running ? RUN_PACE : 1f;
		}
		else
		{
			anim = idleAnimId;
		}

		if (anim != mountAnimId)
		{
			mountAnimId = anim;
			Animation a = anim == -1 ? null : client.loadAnimation(anim);
			mountAnimation = a == null ? null : new PacedAnimationController(client, a);
			mount.setAnimationController(mountAnimation);
			mount.setShouldLoop(true);
		}
		if (mountAnimation != null)
		{
			mountAnimation.setPace(pace);
		}
	}

	// ------------------------------------------------------------------
	// Mount Stable panel
	// ------------------------------------------------------------------

	/** Sends the panel what it should show, only when something changed. */
	private void refreshPanel(NPC pet)
	{
		String name = null;
		String status;
		boolean canRide = false;
		PetTweaks t = PetTweaks.NONE;
		if (pet == null)
		{
			status = "Summon one of your pets to ride it.";
		}
		else
		{
			NPCComposition comp = compositionOf(pet);
			name = comp != null && comp.getName() != null ? Text.removeTags(comp.getName()) : "Your pet";
			String refusal = refusalFor(pet);
			canRide = refusal == null;
			t = PetTweaks.load(configManager, name);
			status = riding ? "You're riding " + name + "."
				: mountingSince != 0 ? "Climbing on..."
				: canRide ? "Ready to ride."
				: refusal;
		}
		MountStablePanel.State s = new MountStablePanel.State(name, status, canRide, riding || mountingSince != 0, t,
			config.showSaddle(), config.showReins(), config.naturalMotion(), config.hideHeldItems(), config.hideCape());
		if (!s.sameAs(panelState))
		{
			panelState = s;
			MountStablePanel p = panel;
			javax.swing.SwingUtilities.invokeLater(() -> p.show(s));
		}
	}

	/** Saves the panel's adjustments for the pet following you and rebuilds the mount with them. */
	private void savePetTweaks(PetTweaks t)
	{
		NPC pet = client.getFollower();
		NPCComposition comp = pet == null ? null : compositionOf(pet);
		if (comp == null || comp.getName() == null)
		{
			return;
		}
		PetTweaks.save(configManager, Text.removeTags(comp.getName()), t);
		builtForNpcId = -1;
		seat.clear();
		restorePose();
	}

	// ------------------------------------------------------------------
	// Saddle
	// ------------------------------------------------------------------

	/** Builds the saddle and blanket moulded to the mount's back in its idle pose. Tried once per mount. */
	private void buildSaddle()
	{
		saddleTried = true;
		Model pose = mount.getModel();
		if (pose == null || !seat.isSet())
		{
			return;
		}
		seat.update(pose);
		final float seatX = seat.x, seatZ = seat.z, seatHeight = -seat.y;
		final float[] xs = pose.getVerticesX(), ys = pose.getVerticesY(), zs = pose.getVerticesZ();
		final int[] f1 = pose.getFaceIndices1(), f2 = pose.getFaceIndices2(), f3 = pose.getFaceIndices3();
		final byte[] alphas = pose.getFaceTransparencies();
		final int faces = pose.getFaceCount();
		final float cap = seatHeight * 1.35f + 4; // ignore heads and necks rising above the back
		SaddleMesh.Surface surface = (dx, dz) ->
		{
			SeatFinder.Seat hit = SeatFinder.raycast(xs, ys, zs, f1, f2, f3, alphas, faces, seatX + dx, seatZ + dz, cap);
			return hit == null ? Float.NaN : hit.height - seatHeight;
		};

		ModelData template = client.loadModelData(SADDLE_TEMPLATE_MODEL);
		if (template == null)
		{
			saddleTried = false; // not loaded yet: try again next tick
			return;
		}
		// Merging gives the saddle its own arrays, so reshaping it never touches the game's cached model.
		ModelData md = client.mergeModels(template, template.shallowCopy())
			.cloneVertices()
			.cloneColors()
			.cloneTransparencies(true);

		RiderPose pose0 = resolvedPose();
		boolean onTop = pose0 == RiderPose.CROSS_LEGGED || pose0 == RiderPose.STANDING;
		SaddleMesh mesh = SaddleMesh.build(surface, seatHeight, pose0 == RiderPose.EXTRA_WIDE, !onTop,
			blanketColor(), SaddleMesh.GOLD, md.getVerticesCount(), md.getFaceCount());

		float[] vx = md.getVerticesX(), vy = md.getVerticesY(), vz = md.getVerticesZ();
		int[] i1 = md.getFaceIndices1(), i2 = md.getFaceIndices2(), i3 = md.getFaceIndices3();
		short[] colors = md.getFaceColors();
		byte[] trans = md.getFaceTransparencies();
		java.util.Arrays.fill(vx, 0);
		java.util.Arrays.fill(vy, 0);
		java.util.Arrays.fill(vz, 0);
		System.arraycopy(mesh.x, 0, vx, 0, mesh.vertexCount);
		System.arraycopy(mesh.y, 0, vy, 0, mesh.vertexCount);
		System.arraycopy(mesh.z, 0, vz, 0, mesh.vertexCount);
		for (int f = 0; f < md.getFaceCount(); f++)
		{
			boolean used = f < mesh.faceCount;
			i1[f] = used ? mesh.f1[f] : 0;
			i2[f] = used ? mesh.f2[f] : 0;
			i3[f] = used ? mesh.f3[f] : 0;
			colors[f] = used ? mesh.color[f] : 0;
			trans[f] = used ? 0 : (byte) 255; // spare faces stay invisible
		}

		Model model = md.light(64, 850, -30, -50, -30);
		if (model == null)
		{
			return;
		}
		saddle = client.createRuneLiteObject();
		saddle.setModel(model);
		saddleLift = mesh.seatThickness;
		saddle.setActive(config.showSaddle());
	}

	/** Blanket colour: from the pet's colours, deepened so it reads as cloth, or the colour from settings. */
	private short blanketColor()
	{
		Color c = config.matchPetColors() ? petPalette[Math.min(1, petPalette.length - 1)] : config.effectColor();
		short hsl = net.runelite.api.JagexColor.rgbToHSL(c.getRGB(), 1.0);
		int hue = net.runelite.api.JagexColor.unpackHue(hsl);
		int sat = Math.max(4, net.runelite.api.JagexColor.unpackSaturation(hsl));
		int lum = Math.max(28, Math.min(52, net.runelite.api.JagexColor.unpackLuminance(hsl)));
		return SaddleMesh.hsl(hue, sat, lum);
	}

	private void removeSaddle()
	{
		if (saddle != null)
		{
			saddle.setActive(false);
		}
		saddle = null;
		saddleTried = false;
		saddleLift = 0;
	}

	private void positionObjects(Player me)
	{
		LocalPoint lp = me.getLocalLocation();
		int plane = me.getWorldView().getPlane();
		int orientation = me.getCurrentOrientation();

		mount.setOrientation(orientation);
		mount.setLocation(lp, plane);

		// Follow the seat on the mount's back as it animates.
		seat.update(mount.getModel());
		RiderPose pose = resolvedPose();

		// The rider follows the seat through a spring, with stride sway and surge (see RiderMotion).
		int gait = speed == 0 ? 0 : speed >= RUN_SPEED_THRESHOLD ? 2 : 1;
		float cycle = mountAnimation != null && gait > 0 ? mountAnimation.cycle() : 0;
		motion.update(seat.x, seat.y, seat.z, gait, cycle, config.naturalMotion());

		// Where the rider's feet go, in the mount's own space: the seat, shifted so the pose's contact point
		// lands on it, plus the player's forward adjustment. Model x is sideways and z points toward the tail.
		float mx = motion.x;
		float mz = motion.z - pose.getContactBack() - config.seatForwardAdjust() - tweaks.seatForward;

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

		LocalPoint riderPoint = new LocalPoint(lp.getX() + dx, lp.getY() + dy, lp.getWorldView());
		rider.setLocation(riderPoint, plane);
		rider.setOrientation(orientation);
		// seatLift is measured from the resting seat; add how far the smoothed, settling rider is from it.
		float riderLift = seatLift() - pose.getContactHeight() + (seat.y - motion.y);
		rider.setZ(ground - Math.round(riderLift)); // negative Z is up

		updateReins(pose, mx, riderLift, mz, lp, plane, orientation, ground);
	}

	// ------------------------------------------------------------------
	// Reins
	// ------------------------------------------------------------------

	/** Finds the corners of the mount's mouth in its idle pose. Tried once per mount. */
	private void findBit()
	{
		bitTried = true;
		Model m = mount.getModel();
		if (m == null || !seat.isSet())
		{
			bitTried = false;
			return;
		}
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
	private void updateReins(RiderPose pose, float footX, float footLift, float footZ, LocalPoint lp, int plane,
		int orientation, int ground)
	{
		int[][] hands = ReinMesh.handsFor(pose);
		Model m = mount.getModel();
		if (!config.showReins() || hands == null || bit == null || m == null || m.getVerticesCount() != bitVertexCount)
		{
			if (reins != null && reins.isActive())
			{
				reins.setActive(false);
			}
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
				moved |= Math.abs(reinEnds[i * 6 + k] - handPoints[i][k]) > 0.3f
					|| Math.abs(reinEnds[i * 6 + 3 + k] - bitPoints[i][k]) > 0.3f;
			}
		}

		if (moved && !rebuildReins(handPoints, bitPoints))
		{
			return;
		}
		reins.setLocation(lp, plane);
		reins.setOrientation(orientation);
		reins.setZ(ground);
		if (!reins.isActive())
		{
			reins.setActive(true);
		}
	}

	private boolean rebuildReins(float[][] hands, float[][] bits)
	{
		if (reinTemplate == null)
		{
			reinTemplate = client.loadModelData(SADDLE_TEMPLATE_MODEL);
			if (reinTemplate == null)
			{
				return false; // not loaded yet
			}
		}
		// A fresh copy each time, so the lighting is worked out for the reins' new shape.
		ModelData md = client.mergeModels(reinTemplate, reinTemplate.shallowCopy())
			.cloneVertices()
			.cloneColors()
			.cloneTransparencies(true);
		if (md.getVerticesCount() < reinMesh.vertexCount() || md.getFaceCount() < reinMesh.faceCount())
		{
			return false;
		}
		reinMesh.update(hands, bits);

		float[] vx = md.getVerticesX(), vy = md.getVerticesY(), vz = md.getVerticesZ();
		int[] i1 = md.getFaceIndices1(), i2 = md.getFaceIndices2(), i3 = md.getFaceIndices3();
		short[] colors = md.getFaceColors();
		byte[] trans = md.getFaceTransparencies();
		java.util.Arrays.fill(vx, 0);
		java.util.Arrays.fill(vy, 0);
		java.util.Arrays.fill(vz, 0);
		System.arraycopy(reinMesh.x, 0, vx, 0, reinMesh.vertexCount());
		System.arraycopy(reinMesh.y, 0, vy, 0, reinMesh.vertexCount());
		System.arraycopy(reinMesh.z, 0, vz, 0, reinMesh.vertexCount());
		for (int f = 0; f < md.getFaceCount(); f++)
		{
			boolean used = f < reinMesh.faceCount();
			i1[f] = used ? reinMesh.f1[f] : 0;
			i2[f] = used ? reinMesh.f2[f] : 0;
			i3[f] = used ? reinMesh.f3[f] : 0;
			colors[f] = used ? ReinMesh.COLOR : 0;
			trans[f] = used ? 0 : (byte) 255;
		}
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

	private void removeReins()
	{
		if (reins != null)
		{
			reins.setActive(false);
		}
		reins = null;
		bit = null;
		bitTried = false;
	}

	// ------------------------------------------------------------------
	// Rider pose
	// ------------------------------------------------------------------

	private RiderPose resolvedPose()
	{
		if (tweaks.pose != RiderPose.AUTO)
		{
			return tweaks.pose;
		}
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
		int start = 0, end = 0;
		if (rp.controlsFrames())
		{
			int last = Math.max(0, poseFrameCount(pose) - 1);
			start = Math.min(rp.getLoopStart(), last);
			end = Math.min(rp.getLoopEnd(), last);
		}
		if (me.getPoseAnimation() != pose)
		{
			me.setPoseAnimation(pose);
			me.setPoseAnimationFrame(start);
		}

		// Some seated poses come from one-off emotes: hold one frame, or loop just the settled part.
		if (rp.controlsFrames())
		{
			int frame = me.getPoseAnimationFrame();
			boolean hold = start >= end;
			if (hold ? frame != start : frame < start || frame >= end)
			{
				me.setPoseAnimationFrame(start);
			}
		}
	}

	private int poseFrameCount(int animationId)
	{
		return poseFrameCounts.computeIfAbsent(animationId, id ->
		{
			Animation a = client.loadAnimation(id);
			if (a == null)
			{
				return 1;
			}
			return a.isMayaAnim() ? Math.max(1, a.getDuration()) : Math.max(1, a.getNumFrames());
		});
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
		if (saddle != null && saddle.isActive())
		{
			saddle.setActive(false);
		}
		if (reins != null && reins.isActive())
		{
			reins.setActive(false);
		}
		if (rider != null && client.isRuneLiteObjectRegistered(rider))
		{
			client.removeRuneLiteObject(rider);
		}
		if (mountedVisible)
		{
			restorePose();
			riderLook.restore(client.getLocalPlayer());
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
