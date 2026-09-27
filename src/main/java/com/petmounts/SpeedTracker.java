package com.petmounts;

import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;

/** Works out how fast a player is moving from how far they move each client tick. */
final class SpeedTracker
{
	/** Local units moved per client tick above which a player is running (walk ~4, run ~8). */
	static final int RUN_SPEED = 6;
	/** Client ticks to keep moving after the last step, so walk and idle don't flicker between tiles. */
	private static final int LINGER = 5;

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
			if (moved > 0)
			{
				// A big jump is a teleport, not a step.
				speed = moved > 4 * 128 ? 0 : moved;
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

	boolean isRunning()
	{
		return speed >= RUN_SPEED;
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
