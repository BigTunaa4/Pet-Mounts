package com.petmounts;

import net.runelite.api.Model;
import net.runelite.api.Player;
import net.runelite.api.RuneLiteObjectController;

/**
 * Draws a player's current (animated) model at an arbitrary height.
 * The real player is hidden while riding and this copy is drawn on the mount's back instead.
 */
class RiderController extends RuneLiteObjectController
{
	private final Player player;

	RiderController(Player player)
	{
		this.player = player;
	}

	boolean isFor(Player p)
	{
		return p == player;
	}

	@Override
	public Model getModel()
	{
		return player.getModel();
	}
}
