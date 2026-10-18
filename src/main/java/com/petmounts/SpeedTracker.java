package com.petmounts;

import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;

/** Works out how fast a player is moving from how far they move each client tick. */
final class SpeedTracker
{
	/** Client ticks to keep moving after the last step, so walk and idle don't flicker between tiles. */
	private static final int LINGER = 5;

	/**
	 * Distance moved over the last game tick's worth of client ticks (30 x 20 ms). Within a tick the game moves
	 * you in uneven steps, so one client tick alone can look like running while you walk; averaged over a whole
	 * tick, walking covers one tile (128) and running two (256).
	 */
	private static final int WINDOW = 30;
	/** Over this much in the last game tick (one and a half tiles) is running. */
	private static final int RUN_DISTANCE = 192;
	private final int[] window = new int[WINDOW];
	private int windowAt;
	private int windowSum;

	private int lastX = Integer.MIN_VALUE;
	private int lastY = Integer.MIN_VALUE;
	private int speed;
	private int lingering;
	private boolean movedThisTick;

	void track(Player player)
	{
		LocalPoint lp = player.getLocalLocation();
		if (lp == null)
		{
			return;
		}
		if (lastX != Integer.MIN_VALUE)
		{
			int moved = Math.max(Math.abs(lp.getX() - lastX), Math.abs(lp.getY() - lastY));
			movedThisTick = moved > 0;
			if (moved > 4 * 128)
			{
				// A big jump is a teleport, or the map reloading under you as you run across it: not a step. Keep
				// the current pace so a running mount doesn't stop dead for a moment and set off again.
				movedThisTick = false;
				moved = 0;
			}
			else if (moved > 0)
			{
				speed = moved;
				lingering = LINGER;
			}
			else if (lingering > 0)
			{
				lingering--;
			}
			else
			{
				speed = 0;
			}
			windowSum += moved - window[windowAt];
			window[windowAt] = moved;
			windowAt = (windowAt + 1) % WINDOW;
		}
		lastX = lp.getX();
		lastY = lp.getY();
	}

	int speed()
	{
		return speed;
	}

	boolean isMoving()
	{
		return speed > 0;
	}

	/** Running: moving, and covering more than one and a half tiles over the last game tick. */
	boolean isRunning()
	{
		return speed > 0 && windowSum >= RUN_DISTANCE;
	}

	boolean movedThisTick()
	{
		return movedThisTick;
	}

	/** 0 standing, 1 walking, 2 running. */
	int gait()
	{
		return speed == 0 ? 0 : isRunning() ? 2 : 1;
	}
}
