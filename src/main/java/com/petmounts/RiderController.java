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
	/** Run just before the player's model is taken, to put them in the riding pose for this frame. */
	private final Runnable beforeDraw;

	RiderController(Player player, Runnable beforeDraw)
	{
		this.player = player;
		this.beforeDraw = beforeDraw;
	}

	boolean isFor(Player p)
	{
		return p == player;
	}

	@Override
	public Model getModel()
	{
		beforeDraw.run();
		return player.getModel();
	}
}
