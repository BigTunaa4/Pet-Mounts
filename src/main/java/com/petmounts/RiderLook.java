package com.petmounts;

import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.kit.KitType;

/**
 * Hides the rider's weapon, shield and cape while mounted, on your screen only, and puts them back afterwards.
 *
 * Big weapons and capes poke through the mount. This works like other appearance plugins on the Plugin Hub:
 * it changes the local copy of your character's equipment appearance and redraws it. Nothing is sent to the
 * server and your real equipment is untouched. If the game updates your appearance while you ride (for
 * example you swap weapons), the new items are remembered and hidden again.
 */
final class RiderLook
{
	private static final KitType[] HELD = {KitType.WEAPON, KitType.SHIELD};
	private static final KitType[] CAPE = {KitType.CAPE};

	/** What each hidden slot showed before we hid it (0 = nothing hidden). */
	private final int[] saved = new int[KitType.values().length];

	/** Hides the chosen slots. Cheap to call every tick: it only redraws when something changes. */
	void apply(Player player, boolean hideHeld, boolean hideCape)
	{
		PlayerComposition comp = player == null ? null : player.getPlayerComposition();
		if (comp == null)
		{
			return;
		}
		int[] ids = comp.getEquipmentIds();
		boolean changed = false;
		changed |= hide(ids, HELD, hideHeld);
		changed |= hide(ids, CAPE, hideCape);
		if (changed)
		{
			comp.setHash();
		}
	}

	/** Puts back everything that was hidden. */
	void restore(Player player)
	{
		PlayerComposition comp = player == null ? null : player.getPlayerComposition();
		boolean changed = false;
		if (comp != null)
		{
			int[] ids = comp.getEquipmentIds();
			for (int slot = 0; slot < saved.length; slot++)
			{
				if (saved[slot] != 0 && ids[slot] == 0)
				{
					ids[slot] = saved[slot];
					changed = true;
				}
			}
			if (changed)
			{
				comp.setHash();
			}
		}
		java.util.Arrays.fill(saved, 0);
	}

	/** Forgets hidden items without touching the player (after logout or a world hop). */
	void forget()
	{
		java.util.Arrays.fill(saved, 0);
	}

	private boolean hide(int[] ids, KitType[] slots, boolean hide)
	{
		boolean changed = false;
		for (KitType kit : slots)
		{
			int slot = kit.getIndex();
			if (hide && ids[slot] != 0)
			{
				saved[slot] = ids[slot]; // new item, or one the game just put back
				ids[slot] = 0;
				changed = true;
			}
			else if (!hide && saved[slot] != 0 && ids[slot] == 0)
			{
				ids[slot] = saved[slot]; // setting switched off while riding
				saved[slot] = 0;
				changed = true;
			}
		}
		return changed;
	}
}
