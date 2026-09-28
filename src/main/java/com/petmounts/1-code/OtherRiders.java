package com.petmounts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.WorldType;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.VarbitID;

/**
 * "Everyone rides": shows other players riding the pets following them, on your screen only.
 *
 * Every nearby player with a rideable pet following them is drawn sitting on it, using the same mounts, saddles,
 * reins and motion as your own. It's purely what your client draws; other players see nothing different.
 *
 * Players shown riding can't be clicked (their real model is hidden), so it switches off in the Wilderness and
 * on PvP worlds, and holding Shift shows everyone normally for trading or following.
 */
final class OtherRiders
{
	/** Game ticks a rider stays off their mount after an action animation (skilling, fighting). */
	private static final int ACTION_GRACE_TICKS = 3;
	/** Game ticks to keep trying to build a pet's mount (while its models load) before giving up on it. */
	private static final int MAX_BUILD_TRIES = 5;
	/** How close a player must be to a pet to be taken as its owner when the pet isn't facing anyone. */
	private static final int OWNER_RANGE = 2 * 128;
	/** How much nearer (in local units, 3 tiles) a rider already shown counts as, when picking who to show. */
	private static final int KEEP_SHOWN_BONUS = 3 * 128;

	private final Client client;
	private final PetModels models;
	/** Whether a pet may be ridden (the same rules as your own pets). */
	private final Predicate<NPCComposition> rideable;
	/** Size for a pet's mount (settings and the pet's saved adjustment), so it looks the same whoever rides it. */
	private final Function<NPCComposition, Float> sizeFor;
	/** How a rider on this pet looks (settings and the pet's saved adjustments). */
	private final Function<NPCComposition, MountRig.Style> styleFor;

	private final Map<Player, MountRig> rigs = new HashMap<>();
	private final Map<Player, SpeedTracker> speeds = new HashMap<>();
	private final Map<Player, Integer> busyUntil = new HashMap<>();
	/** Built mounts by pet NPC id, shared by every rider on that pet. */
	private final Map<Integer, PetModels.Built> built = new HashMap<>();
	/** Failed attempts to build a mount per pet NPC id (one per game tick); pets that keep failing are left alone. */
	private final Map<Integer, Integer> failures = new HashMap<>();
	/** The game tick each pet's mount was last tried. */
	private final Map<Integer, Integer> lastTry = new HashMap<>();
	/** The real players and pets hidden this frame (their riders are drawn instead). */
	private final Set<Renderable> hidden = new HashSet<>();

	OtherRiders(Client client, PetModels models, Predicate<NPCComposition> rideable,
		Function<NPCComposition, Float> sizeFor, Function<NPCComposition, MountRig.Style> styleFor)
	{
		this.client = client;
		this.models = models;
		this.rideable = rideable;
		this.sizeFor = sizeFor;
		this.styleFor = styleFor;
	}

	/** Whether this player or pet is drawn as a rider right now. */
	boolean hides(Renderable renderable)
	{
		return hidden.contains(renderable);
	}

	/** The players shown riding under this point on screen, nearest last (as the game lists them). */
	List<Player> ridersAt(java.awt.Point mouse)
	{
		List<Player> found = new ArrayList<>();
		for (MountRig rig : rigs.values())
		{
			java.awt.Shape area = rig.isVisible() ? rig.screenArea() : null;
			if (area != null && area.contains(mouse))
			{
				found.add(rig.player());
			}
		}
		return found;
	}

	/** Whether showing others riding makes sense here: never where players need to be clicked to fight. */
	boolean allowedHere()
	{
		return !WorldType.isPvpWorld(client.getWorldType())
			&& !client.getWorldType().contains(WorldType.PVP_ARENA)
			&& client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) == 0;
	}

	/** Called every game tick to note who is busy (skilling, fighting) and should step off. */
	void gameTick(int tick)
	{
		for (Player p : rigs.keySet())
		{
			if (p.getAnimation() != -1 || isFighting(p))
			{
				busyUntil.put(p, tick + ACTION_GRACE_TICKS);
			}
		}
	}

	/** Takes everyone off their mounts for now, keeping the mounts ready (Shift held, loading, Wilderness). */
	void hideAll()
	{
		hidden.clear();
		for (Map.Entry<Player, MountRig> e : rigs.entrySet())
		{
			SpeedTracker speed = speeds.get(e.getKey());
			e.getValue().hide(speed != null && speed.isMoving());
		}
	}

	/**
	 * Draws everyone riding for this client tick.
	 *
	 * @param max the most riders to show, nearest first
	 */
	void update(int tick, int max)
	{
		Player me = client.getLocalPlayer();
		if (me == null || me.getLocalLocation() == null)
		{
			clear();
			return;
		}

		// Everyone else's pets, with the player each one follows.
		List<NPC> pets = new ArrayList<>();
		Map<NPC, Player> owners = new HashMap<>();
		List<Player> players = new ArrayList<>();
		for (Player p : client.getTopLevelWorldView().players())
		{
			players.add(p);
		}
		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			if (npc == client.getFollower())
			{
				continue;
			}
			NPCComposition comp = PetModels.compositionOf(npc);
			if (!PetModels.isOwnablePet(comp) || failures.getOrDefault(comp.getId(), 0) >= MAX_BUILD_TRIES
				|| !rideable.test(comp))
			{
				continue;
			}
			Player owner = ownerOf(npc, comp, me, players);
			if (owner != null)
			{
				pets.add(npc);
				owners.put(npc, owner);
			}
		}
		// Nearest first. Riders already shown keep their place a little longer, so riders at the edge of the
		// limit don't flicker on and off as people move around.
		LocalPoint here = me.getLocalLocation();
		pets.sort((a, b) -> Integer.compare(rank(here, a, owners), rank(here, b, owners)));

		Set<Player> shown = new HashSet<>();
		hidden.clear();
		for (NPC pet : pets)
		{
			if (shown.size() >= max)
			{
				break;
			}
			Player owner = owners.get(pet);
			if (shown.contains(owner) || owner.getLocalLocation() == null)
			{
				continue;
			}
			NPCComposition comp = PetModels.compositionOf(pet);
			MountRig rig = rigs.get(owner);
			if (rig == null || rig.npcId() != comp.getId())
			{
				PetModels.Built b = built(comp, tick);
				if (b == null)
				{
					continue; // not loaded yet
				}
				if (rig != null)
				{
					rig.hide(false);
				}
				rig = new MountRig(client, owner, b);
				rigs.put(owner, rig);
			}
			SpeedTracker speed = speeds.computeIfAbsent(owner, p -> new SpeedTracker());
			speed.track(owner);

			if (busyUntil.getOrDefault(owner, -1) > tick)
			{
				rig.hide(speed.isMoving());
				shown.add(owner); // keep their mount ready for when they're done
				continue;
			}

			rig.update(pet, speed.gait(), styleFor.apply(comp), false);
			shown.add(owner);
			hidden.add(owner);
			hidden.add(pet);
		}

		// Riders who left, dismissed their pet, or are now too far away.
		rigs.entrySet().removeIf(e ->
		{
			if (shown.contains(e.getKey()))
			{
				return false;
			}
			e.getValue().hide(false);
			return true;
		});
		speeds.keySet().retainAll(rigs.keySet());
		busyUntil.keySet().retainAll(rigs.keySet());
	}

	/** The mount for this pet, built once and shared by everyone riding one. Null while it can't be built. */
	private PetModels.Built built(NPCComposition comp, int tick)
	{
		PetModels.Built b = built.get(comp.getId());
		if (b != null || lastTry.getOrDefault(comp.getId(), -1) == tick)
		{
			return b; // at most one try per game tick
		}
		lastTry.put(comp.getId(), tick);
		b = models.build(comp, sizeFor.apply(comp));
		if (b == null)
		{
			failures.merge(comp.getId(), 1, Integer::sum); // not loaded yet, or no seat on it
			return null;
		}
		built.put(comp.getId(), b);
		return b;
	}

	/** Sits every shown rider on their mount for the frame about to be drawn. */
	void place()
	{
		for (MountRig rig : rigs.values())
		{
			if (rig.isVisible())
			{
				rig.place();
			}
		}
	}

	/** Shows everyone normally again and lets go of their mounts. */
	void clear()
	{
		for (MountRig rig : rigs.values())
		{
			rig.hide(false);
		}
		rigs.clear();
		speeds.clear();
		busyUntil.clear();
		hidden.clear();
	}

	/** Forgets everyone without touching them (after logout or a world hop, when they're gone anyway). */
	void forget()
	{
		for (MountRig rig : rigs.values())
		{
			rig.forget();
			rig.hide(false);
		}
		rigs.clear();
		speeds.clear();
		busyUntil.clear();
		hidden.clear();
	}

	/** Settings changed: rebuild every mount with them. */
	void rebuild()
	{
		clear();
		built.clear();
		failures.clear();
		lastTry.clear();
	}

	/**
	 * The player a pet belongs to: the one it's following (pets face their owner), or failing that, for a
	 * follower, the only other player right next to it. Null if it's yours or it can't be told.
	 */
	private static Player ownerOf(NPC pet, NPCComposition comp, Player me, List<Player> players)
	{
		Actor facing = pet.getInteracting();
		if (facing instanceof Player)
		{
			return facing == me ? null : (Player) facing;
		}
		LocalPoint lp = pet.getLocalLocation();
		if (lp == null || !comp.isFollower())
		{
			return null;
		}
		Player only = null;
		for (Player p : players)
		{
			LocalPoint pp = p.getLocalLocation();
			if (pp == null || pp.distanceTo(lp) > OWNER_RANGE)
			{
				continue;
			}
			if (p == me || only != null)
			{
				return null; // yours, or too crowded to tell
			}
			only = p;
		}
		return only;
	}

	private int rank(LocalPoint here, NPC pet, Map<NPC, Player> owners)
	{
		int d = distance(here, pet);
		MountRig rig = rigs.get(owners.get(pet));
		return rig != null && rig.isVisible() && d != Integer.MAX_VALUE ? d - KEEP_SHOWN_BONUS : d;
	}

	private static int distance(LocalPoint here, NPC npc)
	{
		LocalPoint lp = npc.getLocalLocation();
		return lp == null ? Integer.MAX_VALUE : here.distanceTo(lp);
	}

	/** Fighting a monster. (Facing another player is usually following or trading, so it doesn't count.) */
	private static boolean isFighting(Player p)
	{
		Actor target = p.getInteracting();
		return target instanceof NPC && ((NPC) target).getCombatLevel() > 0;
	}
}
