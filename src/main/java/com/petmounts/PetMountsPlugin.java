package com.petmounts;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.ModelData;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.TileObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.BeforeRender;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.FocusChanged;
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
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Pet Mounts",
	description = "Ride your pet like a mount. Your pet grows to a rideable size and carries you around.",
	tags = {"pet", "mount", "ride", "follower", "cosmetic", "fun", "wow"}
)
public class PetMountsPlugin extends Plugin
{
	/** Settings that change how the mounts are built (size, blanket colour, which pets); the rest apply live. */
	private static final Set<String> REBUILD_KEYS = Set.of("sizeMultiplier", "matchPetColors", "effectColor",
		"alwaysAllow", "neverAllow", "chosenMount");
	/** Game ticks to stay off the mount after the last action animation. */
	private static final int ACTION_GRACE_TICKS = 3;
	/** How long the climb-on animation plays before the mount poofs in. */
	private static final int WINDUP_MS = 1200;
	/** The player beckons their pet over while climbing on. */
	private static final int WINDUP_ANIMATION = AnimationID.EMOTE_BECKON;

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
	private MountFlair flair;

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

	private PetModels models;
	private OtherRiders others;

	// ----- state -----
	/** Whether the player wants to be riding. */
	private boolean riding;
	/** When the climb-on animation started (System.nanoTime), or 0 when not climbing on. */
	private long mountingSince;
	/** Hidden for an action (skilling, combat, teleport) and waiting to climb back on. */
	private boolean pausedForAction;
	/** Your own mount, built for the pet following you. */
	private MountRig rig;
	/** Settings changed: build the mount again with them. */
	private boolean rebuild;
	/** Drop into the saddle from above next time the mount appears (after the poof, or climbing back on). */
	private boolean dropIn;

	/** Saved adjustments per pet name, read once and kept until they change. */
	private final Map<String, PetTweaks> tweaks = new HashMap<>();
	/** How riders look on each pet NPC id: yours, and other players' (cleared when settings change). */
	private final Map<Integer, MountRig.Style> styles = new HashMap<>();
	private final Map<Integer, MountRig.Style> otherStyles = new HashMap<>();
	/** Rideability verdict per pet NPC id (cleared when settings change). */
	private final Map<Integer, PetRules.Verdict> verdicts = new HashMap<>();

	private final SpeedTracker speed = new SpeedTracker();
	private int lastBusyTick = -100;
	private int tickCount;
	/** Client ticks (20 ms) since start, for timing within a game tick. */
	private int clientTicks;
	/** Sliding off the mount: client ticks so far, or -1 when not. */
	private int slidingOff = -1;
	private boolean slideAnnounce;
	/** How long sliding off takes, in client ticks. */
	private static final int SLIDE_TICKS = 18;
	/** After a loading screen, mounts wait this many client ticks before showing again (safe mode). */
	private static final int SETTLE_AFTER_LOADING = 40;
	private int hiddenUntil;
	/** Picks a random mount on the next login, if that's switched on. */
	private boolean pickOnLogin = true;
	/**
	 * Animation smoothing blends each frame into the next. The seated poses hold one frame of an emote (or loop
	 * part of one), so blending made riders' arms and legs twitch toward the next frame and snap back. Riding
	 * poses are left out of the smoothing; everything else is smoothed exactly as before.
	 */
	private IntPredicate smoothingFilter;
	private final IntPredicate riderPoseFilter = id -> !isRidingPose(id) && smoothingFilter != null
		&& smoothingFilter.test(id);

	/** Held to see everyone normally (to click on them) while "Everyone rides" is on. */
	private volatile boolean shiftHeld;

	private final RenderCallback renderCallback = new RenderCallback()
	{
		@Override
		public boolean addEntity(Renderable renderable, boolean ui)
		{
			// Called while the game draws the scene: if anything goes wrong, just draw the entity normally.
			try
			{
				return shouldDraw(renderable, ui);
			}
			catch (RuntimeException e)
			{
				log.debug("Render check failed", e);
				return true;
			}
		}

		@Override
		public boolean drawObject(Scene scene, TileObject object)
		{
			try
			{
				return shouldDrawObject(object);
			}
			catch (RuntimeException e)
			{
				log.debug("Draw check failed", e);
				return true;
			}
		}
	};

	/**
	 * When the game last asked before drawing a person. While it keeps asking, riders can be hidden without losing
	 * their clickbox. (Some renderers might not ask; then riders are left out of the scene as before.)
	 */
	private volatile long peopleDrawCheckedAt;
	private static final long DRAW_CHECK_TIMEOUT = 2_000_000_000L;

	private boolean drawChecksPeople()
	{
		return System.nanoTime() - peopleDrawCheckedAt < DRAW_CHECK_TIMEOUT;
	}

	private final HotkeyListener hotkeyListener = new HotkeyListener(() -> config.mountHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			clientThread.invoke(PetMountsPlugin.this::toggleRiding);
		}
	};

	private final KeyListener shiftListener = new KeyListener()
	{
		@Override
		public void keyTyped(KeyEvent e)
		{
		}

		@Override
		public void keyPressed(KeyEvent e)
		{
			if (e.getKeyCode() == KeyEvent.VK_SHIFT)
			{
				shiftHeld = true;
			}
		}

		@Override
		public void keyReleased(KeyEvent e)
		{
			if (e.getKeyCode() == KeyEvent.VK_SHIFT)
			{
				shiftHeld = false;
			}
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
		models = new PetModels(client);
		others = new OtherRiders(client, models, this::isRideable, this::sizeFor, this::otherStyleFor);
		tweaks.clear();
		styles.clear();
		otherStyles.clear();
		verdicts.clear();
		rebuild = true;

		renderCallbackManager.register(renderCallback);
		keyManager.registerKeyListener(hotkeyListener);
		keyManager.registerKeyListener(shiftListener);

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

			@Override
			public void chooseMount(int npcId)
			{
				configManager.setConfiguration(PetMountsConfig.GROUP, "chosenMount", Math.max(0, npcId));
			}

			@Override
			public void toggleFavourite(int npcId)
			{
				java.util.Set<Integer> favs = favourites();
				if (!favs.remove(npcId))
				{
					favs.add(npcId);
				}
				configManager.setConfiguration(PetMountsConfig.GROUP, "favouriteMounts", joinIds(favs));
			}

			@Override
			public void randomMount()
			{
				clientThread.invoke(PetMountsPlugin.this::pickRandomMount);
			}

			@Override
			public void setSaddleStyle(SaddleStyle style)
			{
				configManager.setConfiguration(PetMountsConfig.GROUP, "saddleStyle", style);
			}

			@Override
			public void copyMountInfo()
			{
				clientThread.invoke(PetMountsPlugin.this::copyMountInfoToClipboard);
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
		clientThread.invoke(this::keepPosesSteady);
	}

	/** Leaves the riding poses out of animation smoothing (see {@link #riderPoseFilter}). */
	private void keepPosesSteady()
	{
		IntPredicate current = client.getAnimationInterpolationFilter();
		if (current != null && current != riderPoseFilter)
		{
			// Smoothing is on (or its settings changed): smooth what it smoothed, except the riding poses.
			smoothingFilter = current;
			client.setAnimationInterpolationFilter(riderPoseFilter);
		}
	}

	private static boolean isRidingPose(int animationId)
	{
		for (RiderPose pose : RiderPose.values())
		{
			if (pose.getAnimationId() == animationId && animationId != -1)
			{
				return true;
			}
		}
		return false;
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(hotkeyListener);
		keyManager.unregisterKeyListener(shiftListener);
		renderCallbackManager.unregister(renderCallback);
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(mountButton);
		mouseManager.unregisterMouseListener(mountButton.mouse);
		panelState = null;
		clientThread.invoke(() ->
		{
			if (client.getAnimationInterpolationFilter() == riderPoseFilter)
			{
				client.setAnimationInterpolationFilter(smoothingFilter);
			}
			smoothingFilter = null;
			effects.clear();
			cancelMounting(null);
			hideMount();
			rig = null;
			others.clear();
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
		if (me == null)
		{
			return;
		}
		if (chosenMount() <= 0)
		{
			if (pet == null)
			{
				if (announce)
				{
					message("You need one of your pets following you to ride it, or pick a mount in the Mount Stable.");
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
		}

		// Build the mount now so we know its size (for where the effects and poof go).
		NPCComposition comp = mountComposition();
		if (comp != null)
		{
			ensureRig(me, comp);
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
		dropIn = true;
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

		if (slidingOff >= 0)
		{
			return; // already getting off
		}
		boolean wasVisible = rig != null && rig.isVisible();
		if (wasVisible && config.mountEffects() && !speed.isMoving())
		{
			// Slide off the side first; the mount goes away once you're down (see updateOwnMount).
			slidingOff = 0;
			slideAnnounce = announce;
			return;
		}
		finishDismount(announce, wasVisible);
	}

	/** Gets off for real: the mount goes away in a poof. */
	private void finishDismount(boolean announce, boolean wasVisible)
	{
		slidingOff = -1;
		if (rig != null)
		{
			rig.setSlide(0);
		}
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
		String name = PetModels.nameOf(mountComposition());
		return name != null ? name : "your pet";
	}

	/** The pet picked in the Mount Stable (its NPC id), or 0 to ride the pet following you. */
	private int chosenMount()
	{
		int id = config.chosenMount();
		return id > 0 && MountFits.choices().containsValue(id) ? id : 0;
	}

	/** What you ride: the pet picked in the Mount Stable, or the pet following you. Null if neither. */
	private NPCComposition mountComposition()
	{
		int chosen = chosenMount();
		if (chosen > 0)
		{
			return client.getNpcDefinition(chosen);
		}
		NPC pet = client.getFollower();
		return pet == null ? null : PetModels.compositionOf(pet);
	}

	/** Colour for the effects: the pet's main colour, or the colour chosen in settings. */
	private Color effectColor()
	{
		Color[] palette = rig != null ? rig.palette() : PetPalette.DEFAULT;
		return config.matchPetColors() ? palette[0] : config.effectColor();
	}

	/** Height of the seat above the ground right now, including the player's adjustments. */
	private int seatLift()
	{
		NPCComposition comp = mountComposition();
		return rig == null || comp == null ? 0 : rig.seatLift(styleFor(comp));
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
			.onClick(me ->
			{
				// Riding the pet that's following you, rather than one picked in the Mount Stable.
				if (chosenMount() > 0)
				{
					configManager.setConfiguration(PetMountsConfig.GROUP, "chosenMount", 0);
				}
				setRiding(true, true);
			});
	}

	@Subscribe
	public void onMenuOpened(MenuOpened e)
	{
		addRiderOptions();
		if (!riding && mountingSince == 0)
		{
			return;
		}

		addMountOptions();

		// Always offered while riding. Index 1 sits just above "Cancel", so it never becomes the left-click option.
		client.getMenu().createMenuEntry(1)
			.setOption("Dismount")
			.setTarget("<col=ffff00>" + petName() + "</col>")
			.setType(MenuAction.RUNELITE)
			.setDeprioritized(true)
			.onClick(me -> setRiding(false, true));
	}

	/** The game's options for an NPC's five right-click actions, in order. */
	private static final MenuAction[] NPC_OPTIONS = {
		MenuAction.NPC_FIRST_OPTION, MenuAction.NPC_SECOND_OPTION, MenuAction.NPC_THIRD_OPTION,
		MenuAction.NPC_FOURTH_OPTION, MenuAction.NPC_FIFTH_OPTION
	};

	/**
	 * The pet you're riding is hidden from the game, so you can't right-click it. Put its own options (a cat's
	 * Chase, a dog's Dig, Interact...) on the mount instead, so it can still do its tricks while you ride.
	 */
	private void addMountOptions()
	{
		NPC pet = client.getFollower();
		net.runelite.api.Point mouse = client.getMouseCanvasPosition();
		if (!riding || chosenMount() > 0 || pet == null || rig == null || !rig.isVisible() || mouse == null)
		{
			return;
		}
		java.awt.Shape area = rig.screenArea();
		if (area == null || !area.contains(mouse.getX(), mouse.getY()))
		{
			return;
		}
		NPCComposition comp = pet.getTransformedComposition();
		if (comp == null)
		{
			comp = pet.getComposition();
		}
		String[] actions = comp == null ? null : comp.getActions();
		if (actions == null)
		{
			return;
		}
		String target = "<col=ffff00>" + comp.getName() + "</col>";
		// Added last-option first, so the first option ends up on top as in the game's own menu.
		for (int i = Math.min(actions.length, NPC_OPTIONS.length) - 1; i >= 0; i--)
		{
			if (actions[i] == null || actions[i].isEmpty())
			{
				continue;
			}
			client.getMenu().createMenuEntry(-1)
				.setOption(actions[i])
				.setTarget(target)
				.setType(NPC_OPTIONS[i])
				.setIdentifier(pet.getIndex());
		}
	}

	/**
	 * Players shown riding are hidden from the game, so the game can't list them when you right-click. Put their
	 * usual options (Follow, Trade with, Report...) back for any rider under the mouse, exactly as the game would.
	 */
	private void addRiderOptions()
	{
		net.runelite.api.Point mouse = client.getMouseCanvasPosition();
		String[] options = client.getPlayerOptions();
		int[] types = client.getPlayerMenuTypes();
		boolean[] lowered = client.getPlayerOptionsPriorities();
		Player me = client.getLocalPlayer();
		if (mouse == null || options == null || types == null || me == null)
		{
			return;
		}
		for (Player rider : others.ridersAt(new java.awt.Point(mouse.getX(), mouse.getY())))
		{
			if (listedByGame(rider))
			{
				continue; // the game already made this rider's menu itself: leave it exactly as it is
			}
			String target = PlayerMenu.target(rider, me.getCombatLevel());
			// Added last-option first, so the first option ends up on top as in the game's own menu.
			for (int i = Math.min(options.length, types.length) - 1; i >= 0; i--)
			{
				MenuAction action = PlayerMenu.action(types[i]);
				if (options[i] == null || options[i].isEmpty() || action == null)
				{
					continue;
				}
				client.getMenu().createMenuEntry(-1)
					.setOption(options[i])
					.setTarget(target)
					.setType(action)
					.setIdentifier(rider.getId())
					.setDeprioritized(PlayerMenu.deprioritized(types[i])
						|| lowered != null && i < lowered.length && lowered[i]);
			}
		}
	}

	/** Whether the open menu already has the game's own entries for this player. */
	private boolean listedByGame(Player player)
	{
		for (MenuEntry entry : client.getMenu().getMenuEntries())
		{
			if (entry.getPlayer() == player)
			{
				return true;
			}
		}
		return false;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (!PetMountsConfig.GROUP.equals(e.getGroup()))
		{
			return;
		}
		String key = e.getKey();
		boolean rebuildMounts = REBUILD_KEYS.contains(key) || key.startsWith(PetTweaks.KEY_PREFIX);
		clientThread.invoke(() ->
		{
			// Most settings are read live every tick; only the look of the mounts themselves needs a rebuild.
			styles.clear();
			otherStyles.clear();
			if (rebuildMounts)
			{
				rebuild = true;
				tweaks.clear();
				verdicts.clear();
				others.rebuild();
			}
		});
	}

	@Subscribe
	public void onFocusChanged(FocusChanged e)
	{
		if (!e.isFocused())
		{
			shiftHeld = false; // Shift may be let go outside the window
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() == GameState.LOGIN_SCREEN)
		{
			pickOnLogin = true;
		}
		if (e.getGameState() == GameState.LOGIN_SCREEN || e.getGameState() == GameState.HOPPING)
		{
			slidingOff = -1;
			cancelMounting(null);
			effects.clear();
			pausedForAction = false;
			if (rig != null)
			{
				rig.forget();
				rig.hide(false);
			}
			rig = null;
			others.forget();
			riding = config.remountOnLogin() && config.wasMounted();
		}
		else if (e.getGameState() == GameState.LOADING)
		{
			// Scene is being rebuilt (a teleport, a house, a new area): hide everything and wait a moment for the
			// new scene to settle before showing the mounts again.
			hideMount();
			others.hideAll();
			effects.clear();
			hiddenUntil = clientTicks + SETTLE_AFTER_LOADING;
		}
		else if (e.getGameState() == GameState.LOGGED_IN && pickOnLogin)
		{
			pickOnLogin = false;
			if (config.randomMount())
			{
				pickRandomMount();
			}
		}
	}

	// ------------------------------------------------------------------
	// Per-tick logic
	// ------------------------------------------------------------------

	@Subscribe
	public void onGameTick(GameTick e)
	{
		tickCount++;
		others.gameTick(tickCount);
		keepPosesSteady(); // in case smoothing was switched on (or changed) since

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

	/**
	 * Just before each frame is drawn: sit every rider on the exact animation frame their mount is showing, so
	 * nothing trails a frame behind, even at unlocked frame rates.
	 */
	@Subscribe
	public void onBeforeRender(BeforeRender e)
	{
		try
		{
			if (rig != null && rig.isVisible())
			{
				rig.place();
			}
			others.place();
		}
		catch (RuntimeException ex)
		{
			log.debug("Couldn't place the riders this frame", ex);
		}
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

		clientTicks++;
		speed.track(me);
		effects.tick();
		if (++panelTicks % 10 == 0) // about five times a second is plenty for the side panel
		{
			refreshPanel(pet);
		}

		updateOwnMount(me, pet);

		boolean settling = clientTicks < hiddenUntil;
		if (!config.everyoneRides())
		{
			others.clear();
		}
		else if (shiftHeld || settling || !others.allowedHere())
		{
			others.hideAll();
		}
		else
		{
			others.update(tickCount, config.everyoneRidesLimit());
		}
		keepRidersApart();
	}

	/**
	 * Riders on neighbouring tiles: big mounts would overlap, so shift each a little apart (never far, so the
	 * rider stays over their own tile).
	 */
	private void keepRidersApart()
	{
		java.util.List<MountRig> rigs = new java.util.ArrayList<>(others.visibleRigs());
		if (rig != null && rig.isVisible())
		{
			rigs.add(rig);
		}
		int n = rigs.size();
		if (n > 40)
		{
			n = 40; // plenty for a busy spot
		}
		float[] nx = new float[n], ny = new float[n];
		for (int i = 0; i < n; i++)
		{
			LocalPoint a = rigs.get(i).player().getLocalLocation();
			for (int j = i + 1; j < n && a != null; j++)
			{
				LocalPoint b = rigs.get(j).player().getLocalLocation();
				if (b == null)
				{
					continue;
				}
				float dx = b.getX() - a.getX(), dy = b.getY() - a.getY();
				float d = (float) Math.sqrt(dx * dx + dy * dy);
				float overlap = rigs.get(i).halfWidth() + rigs.get(j).halfWidth() - d;
				if (overlap <= 0 || d < 1)
				{
					continue; // apart already, or on the same tile (nothing sensible to do)
				}
				float push = Math.min(MAX_NUDGE, overlap / 2) / d;
				nx[i] -= dx * push;
				ny[i] -= dy * push;
				nx[j] += dx * push;
				ny[j] += dy * push;
			}
		}
		for (int i = 0; i < rigs.size(); i++)
		{
			if (i < n)
			{
				int x = Math.round(Math.max(-MAX_NUDGE, Math.min(MAX_NUDGE, nx[i])));
				int y = Math.round(Math.max(-MAX_NUDGE, Math.min(MAX_NUDGE, ny[i])));
				rigs.get(i).setNudge(x, y);
			}
			else
			{
				rigs.get(i).setNudge(0, 0);
			}
		}
	}

	/** The furthest a mount is shifted to keep it from overlapping a neighbour: a quarter of a tile. */
	private static final int MAX_NUDGE = 32;

	private void updateOwnMount(Player me, NPC pet)
	{
		int chosen = chosenMount();
		if (mountingSince != 0)
		{
			if (pet == null && chosen <= 0)
			{
				cancelMounting("Your pet wandered off before you could climb on.");
			}
			else if (speed.movedThisTick())
			{
				cancelMounting("You need to stand still to climb onto your pet.");
			}
			else if ((System.nanoTime() - mountingSince) / 1_000_000L >= WINDUP_MS)
			{
				finishMounting(true);
			}
		}

		boolean visible = rig != null && rig.isVisible();
		boolean busy = config.hopOffForActions() && tickCount - lastBusyTick < ACTION_GRACE_TICKS;

		// Sliding off the side: when down (or if you walk off first), the mount goes away.
		if (slidingOff >= 0)
		{
			if (++slidingOff >= SLIDE_TICKS || speed.isMoving() || busy || rig == null || !visible)
			{
				finishDismount(slideAnnounce, visible);
				return;
			}
			rig.setSlide(slidingOff / (float) SLIDE_TICKS);
		}

		// Safe mode: just after a loading screen, wait for the new scene to settle.
		if (clientTicks < hiddenUntil)
		{
			hideMount();
			return;
		}
		if (riding && busy && visible)
		{
			// Hopping off to do something: a small puff hides the mount leaving.
			pausedForAction = true;
			dropIn = true;
			if (config.mountEffects())
			{
				effects.puff(effectColor(), 0);
			}
		}
		if (!riding || (pet == null && chosen <= 0) || busy)
		{
			if (!riding)
			{
				pausedForAction = false;
			}
			hideMount();
			return;
		}

		NPCComposition comp = mountComposition();
		if (comp == null)
		{
			hideMount();
			return;
		}

		if (chosen <= 0)
		{
			String refusal = refusalFor(pet);
			if (refusal != null)
			{
				setRiding(false, false);
				message(refusal);
				return;
			}
		}

		if (!ensureRig(me, comp))
		{
			hideMount(); // model data not loaded yet, try again next tick
			return;
		}

		// Just appeared after the poof, or climbing back on: drop into the saddle. (Not after a loading screen.)
		NPC ridden = chosen > 0 ? null : pet;
		int[] animations = ridden != null ? new int[]{ridden.getIdlePoseAnimation(), ridden.getWalkAnimation(),
			ridden.getRunAnimation()} : MountFits.animations(chosen);
		boolean climbedOn = dropIn;
		rig.update(ridden, animations, speed.gait(), styleFor(comp), dropIn && config.mountEffects());
		dropIn = false;
		if (climbedOn)
		{
			// Show off a little as you climb on (a dragon rears up, a dog digs in).
			flair.reset();
			if (config.idleTricks() && config.mountEffects() && !pausedForAction)
			{
				rig.showOff();
			}
		}
		flair.tick(rig, me, speed.gait(), config.mountSounds(), config.mountTrails());

		if (pausedForAction)
		{
			// Climbing back on after an action.
			pausedForAction = false;
			if (config.mountEffects())
			{
				effects.puff(effectColor(), seatLift() / 3);
			}
		}
	}

	/** Builds your mount for this pet if it isn't built yet (or the settings changed). False if not loaded yet. */
	private boolean ensureRig(Player me, NPCComposition comp)
	{
		if (rig != null && !rebuild && rig.npcId() == comp.getId() && rig.player() == me)
		{
			return true;
		}
		PetModels.Built built = models.build(comp, sizeFor(comp));
		if (built == null)
		{
			return false;
		}
		hideMount();
		rig = new MountRig(client, me, built);
		rebuild = false;
		return true;
	}

	private void hideMount()
	{
		if (rig != null)
		{
			rig.hide(speed.isMoving());
		}
	}

	// ------------------------------------------------------------------
	// Settings for a mount
	// ------------------------------------------------------------------

	private PetTweaks tweaksFor(NPCComposition comp)
	{
		String name = PetModels.nameOf(comp);
		return name == null ? PetTweaks.NONE : tweaks.computeIfAbsent(name, n -> PetTweaks.load(configManager, n));
	}

	/** How much bigger or smaller than fitted to make this pet: the Mount size setting and the pet's own size. */
	private float sizeFor(NPCComposition comp)
	{
		return config.sizeMultiplier() / 100f * tweaksFor(comp).size / 100f;
	}

	/** How you look riding this pet: the settings, with the pet's saved adjustments on top. */
	private MountRig.Style styleFor(NPCComposition comp)
	{
		return styles.computeIfAbsent(comp.getId(), id -> makeStyle(comp));
	}

	/**
	 * How other players look riding this pet. Their weapons and capes are left alone (changing other players'
	 * appearance is best avoided), and their reins are rebuilt less often, to keep busy areas smooth.
	 */
	private MountRig.Style otherStyleFor(NPCComposition comp)
	{
		return otherStyles.computeIfAbsent(comp.getId(), id ->
		{
			MountRig.Style s = makeStyle(comp);
			s.hideHeld = false;
			s.hideCape = false;
			s.reinStep = 2f;
			return s;
		});
	}

	private MountRig.Style makeStyle(NPCComposition comp)
	{
		PetTweaks t = tweaksFor(comp);
		MountRig.Style s = new MountRig.Style();
		s.pose = t.pose != RiderPose.AUTO ? t.pose : config.riderPose();
		s.seatHeight = config.seatHeightAdjust() + t.seatHeight;
		s.seatForward = config.seatForwardAdjust() + t.seatForward;
		s.saddle = config.showSaddle();
		s.reins = config.showReins();
		s.naturalMotion = config.naturalMotion();
		s.hideHeld = config.hideHeldItems();
		s.hideCape = config.hideCape();
		s.blanket = config.matchPetColors() ? null : config.effectColor();
		s.saddleStyle = config.saddleStyle();
		s.tricks = config.idleTricks();
		return s;
	}

	// ------------------------------------------------------------------
	// Which pets can be ridden
	// ------------------------------------------------------------------

	/**
	 * @return a chat message explaining why this follower can't be ridden, or null if it can
	 */
	private String refusalFor(NPC pet)
	{
		NPCComposition comp = PetModels.compositionOf(pet);
		String name = PetModels.nameOf(comp);
		if (name == null)
		{
			name = "That";
		}

		// Only the player's own pets: client.getFollower() is always the local player's follower,
		// and real pets (unlike quest companions) can be picked up.
		if (comp == null || pet != client.getFollower() || !PetModels.isOwnablePet(comp))
		{
			return name + " isn't one of your pets, so you can't ride it.";
		}

		PetRules.Verdict verdict = verdictFor(comp);
		return verdict.rideable ? null : name + " " + verdict.reason.text;
	}

	/** Whether this kind of pet can be ridden at all (anyone's). */
	private boolean isRideable(NPCComposition comp)
	{
		return comp != null && verdictFor(comp).rideable;
	}

	private PetRules.Verdict verdictFor(NPCComposition comp)
	{
		PetRules.Verdict verdict = verdicts.get(comp.getId());
		if (verdict == null)
		{
			ModelData md = models.load(comp);
			PetRules.Shape shape = md != null ? PetModels.measure(md, comp) : null;
			verdict = PetRules.check(comp.getId(), comp.getName(), shape,
				PetRules.parseList(config.alwaysAllow()), PetRules.parseList(config.neverAllow()));
			if (md != null)
			{
				verdicts.put(comp.getId(), verdict); // only cache once the shape could be checked
			}
		}
		return verdict;
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
		int chosen = chosenMount();
		if (chosen > 0)
		{
			NPCComposition comp = client.getNpcDefinition(chosen);
			name = PetModels.nameOf(comp);
			canRide = true;
			t = comp == null ? PetTweaks.NONE : tweaksFor(comp);
			status = riding ? "You're riding " + name + "."
				: mountingSince != 0 ? "Climbing on..."
				: "Ready to ride.";
		}
		else if (pet == null)
		{
			status = "Summon one of your pets, or pick a mount below.";
		}
		else
		{
			NPCComposition comp = PetModels.compositionOf(pet);
			name = PetModels.nameOf(comp);
			if (name == null)
			{
				name = "Your pet";
			}
			String refusal = refusalFor(pet);
			canRide = refusal == null;
			t = comp == null ? PetTweaks.NONE : tweaksFor(comp);
			status = riding ? "You're riding " + name + "."
				: mountingSince != 0 ? "Climbing on..."
				: canRide ? "Ready to ride."
				: refusal;
		}
		MountStablePanel.State s = new MountStablePanel.State(name, status, canRide, riding || mountingSince != 0, t,
			config.showSaddle(), config.showReins(), config.naturalMotion(), config.everyoneRides(),
			config.hideHeldItems(), config.hideCape(), chosen, favourites(), config.saddleStyle());
		if (!s.sameAs(panelState))
		{
			panelState = s;
			MountStablePanel p = panel;
			javax.swing.SwingUtilities.invokeLater(() -> p.show(s));
		}
	}

	// ------------------------------------------------------------------
	// Favourites, random mounts and bug reports
	// ------------------------------------------------------------------

	/** Starred mounts (NPC ids) that are still in the Mount list. */
	private java.util.Set<Integer> favourites()
	{
		java.util.Set<Integer> ids = new java.util.LinkedHashSet<>();
		for (String part : config.favouriteMounts().split(","))
		{
			try
			{
				int id = Integer.parseInt(part.trim());
				if (MountFits.choices().containsValue(id))
				{
					ids.add(id);
				}
			}
			catch (NumberFormatException e)
			{
				// skip anything that isn't an id
			}
		}
		return ids;
	}

	private static String joinIds(java.util.Collection<Integer> ids)
	{
		StringBuilder b = new StringBuilder();
		for (int id : ids)
		{
			if (b.length() > 0)
			{
				b.append(',');
			}
			b.append(id);
		}
		return b.toString();
	}

	/** Rides a random mount: one of the favourites if any are starred, otherwise any mount. */
	private void pickRandomMount()
	{
		java.util.List<Integer> pool = new java.util.ArrayList<>(favourites());
		if (pool.size() < 2)
		{
			pool = new java.util.ArrayList<>(MountFits.choices().values());
		}
		pool.remove(Integer.valueOf(config.chosenMount())); // something different from now
		if (pool.isEmpty())
		{
			return;
		}
		int id = pool.get((int) (Math.random() * pool.size()));
		configManager.setConfiguration(PetMountsConfig.GROUP, "chosenMount", id);
		String name = PetModels.nameOf(client.getNpcDefinition(id));
		message("Your mount for now: " + (name != null ? name : "a surprise") + ".");
	}

	/** Copies what's needed to look into a problem with the current mount. */
	private void copyMountInfoToClipboard()
	{
		NPCComposition comp = mountComposition();
		StringBuilder b = new StringBuilder("Pet Mounts report\n");
		if (comp == null)
		{
			b.append("No mount picked and no pet following\n");
		}
		else
		{
			PetTweaks t = tweaksFor(comp);
			MountFits.Fit fit = MountFits.get(comp.getId());
			b.append("Mount: ").append(comp.getName()).append(" (npc ").append(comp.getId()).append(")\n");
			b.append("Tuned: ").append(fit != null ? "yes, " + fit.pose + (fit.shoulders ? ", shoulders" : "") : "no")
				.append('\n');
			b.append("Ridden: ").append(chosenMount() > 0 ? "picked in the Mount Stable" : "your own pet").append('\n');
			b.append("Size: ").append(config.sizeMultiplier()).append("% x ").append(t.size).append("%\n");
			b.append("Pose: ").append(t.pose != RiderPose.AUTO ? t.pose : config.riderPose()).append('\n');
			b.append("Seat height/forward: ").append(config.seatHeightAdjust() + t.seatHeight).append(" / ")
				.append(config.seatForwardAdjust() + t.seatForward).append('\n');
			b.append("Saddle: ").append(config.showSaddle() ? config.saddleStyle() : "off").append(", reins: ")
				.append(config.showReins() ? "on" : "off").append('\n');
		}
		Player me = client.getLocalPlayer();
		if (me != null && me.getWorldLocation() != null)
		{
			b.append("Where: ").append(me.getWorldLocation()).append('\n');
		}
		String text = b.toString();
		java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
			.setContents(new java.awt.datatransfer.StringSelection(text), null);
		message("Mount info copied. Paste it into your report.");
	}

	/** Saves the panel's adjustments for the pet following you and rebuilds the mounts with them. */
	private void savePetTweaks(PetTweaks t)
	{
		String name = PetModels.nameOf(mountComposition());
		if (name == null)
		{
			return;
		}
		PetTweaks.save(configManager, name, t);
		// Saving changes the config, which rebuilds the mounts (see onConfigChanged).
	}

	// ------------------------------------------------------------------
	// Rendering
	// ------------------------------------------------------------------

	private boolean shouldDraw(Renderable renderable, boolean drawingUi)
	{
		// Keep overhead text, hitsplats and health bars (drawn as UI) visible.
		if (drawingUi)
		{
			return true;
		}
		// Hide the real pets (the enlarged mounts replace them) and the real players
		// (the rider copies are drawn on the mounts' backs).
		if (rig != null && rig.isVisible()
			&& ((renderable == client.getFollower() && chosenMount() <= 0) || renderable == client.getLocalPlayer()))
		{
			return false;
		}
		if (others.hides(renderable))
		{
			// Other riders: keep them in the scene, so right-clicking them gives exactly the game's own menu (and
			// whatever other plugins add to it), and just don't draw them. Their pets are left out completely.
			return renderable instanceof Player && drawChecksPeople();
		}
		return true;
	}

	/** Hides other riders' real players from the picture while leaving them clickable. */
	private boolean shouldDrawObject(TileObject object)
	{
		if (!(object instanceof GameObject))
		{
			return true;
		}
		Renderable r = ((GameObject) object).getRenderable();
		if (!(r instanceof Actor))
		{
			return true;
		}
		peopleDrawCheckedAt = System.nanoTime();
		return !(r instanceof Player && others.hides(r));
	}
}
