package com.petmounts;

/**
 * Makes the rider move like someone sitting on a living animal rather than a model glued to it.
 *
 * <ul>
 *     <li><b>Settling:</b> you drop onto the saddle after the poof and give a small bounce.</li>
 *     <li><b>Following the back:</b> the seat's steps between animation frames are smoothed with a stiff spring,
 *     so you ride the motion instead of jerking frame to frame.</li>
 *     <li><b>Stride sway:</b> a gentle side-to-side sway in time with the mount's walk or run cycle.</li>
 *     <li><b>Surge:</b> you rock back a little when the mount sets off and forward when it stops.</li>
 * </ul>
 *
 * Positions are in the mount's model space (x sideways, y pointing down, z toward the tail). Updated once per
 * client tick (20 ms).
 */
final class RiderMotion
{
	static final float DT = 0.02f;

	/** How high above the seat you appear before settling. */
	static final float SETTLE_DROP = 14f;
	private static final float SEAT_SPRING = 38f;
	private static final float SETTLE_SPRING = 15f, SETTLE_DAMPING = 0.42f;
	private static final float SURGE_SPRING = 9f, SURGE_DAMPING = 0.5f;
	/** Push per step of speed (standing, walking, running). */
	private static final float SURGE_KICK = 55f;
	private static final float SWAY_WALK = 2.5f, SWAY_RUN = 3.5f;
	private static final float SWAY_EASE = 5f;

	private boolean primed;
	private float sx, sy, sz, vx, vy, vz;
	private float settle, settleV;
	private float surge, surgeV;
	private float sway;
	private int lastGait;

	/** Where the rider sits this frame, before the pose's own offsets. */
	float x, y, z;

	/** Starts over, e.g. when a new mount appears. {@code dropIn} starts the rider above the seat. */
	void reset(boolean dropIn)
	{
		primed = false;
		settle = dropIn ? SETTLE_DROP : 0;
		settleV = 0;
		surge = 0;
		surgeV = 0;
		sway = 0;
	}

	/**
	 * @param seatX seat on the mount's back this frame
	 * @param gait 0 standing, 1 walking, 2 running
	 * @param cycle how far through its walk or run cycle the mount is, 0 to 1
	 * @param natural false keeps the rider fixed to the seat (for players who turn the effect off)
	 */
	void update(float seatX, float seatY, float seatZ, int gait, float cycle, boolean natural)
	{
		if (!primed || !natural)
		{
			sx = seatX;
			sy = seatY;
			sz = seatZ;
			vx = vy = vz = 0;
			lastGait = gait;
			primed = true;
		}
		if (!natural)
		{
			settle = surge = sway = 0;
			x = seatX;
			y = seatY;
			z = seatZ;
			return;
		}

		// Two half steps keep the stiff spring stable.
		for (int i = 0; i < 2; i++)
		{
			float h = DT / 2;
			vx += (SEAT_SPRING * SEAT_SPRING * (seatX - sx) - 2 * SEAT_SPRING * vx) * h;
			vy += (SEAT_SPRING * SEAT_SPRING * (seatY - sy) - 2 * SEAT_SPRING * vy) * h;
			vz += (SEAT_SPRING * SEAT_SPRING * (seatZ - sz) - 2 * SEAT_SPRING * vz) * h;
			sx += vx * h;
			sy += vy * h;
			sz += vz * h;

			settleV += (-SETTLE_SPRING * SETTLE_SPRING * settle - 2 * SETTLE_DAMPING * SETTLE_SPRING * settleV) * h;
			settle += settleV * h;
			if (settle < -3)
			{
				settle = -3; // a small give into the saddle, never through it
				settleV = Math.max(0, settleV);
			}
		}

		// Rock back when setting off, forward when stopping.
		if (gait != lastGait)
		{
			surgeV += (gait - lastGait) * SURGE_KICK;
			lastGait = gait;
		}
		surgeV += (-SURGE_SPRING * SURGE_SPRING * surge - 2 * SURGE_DAMPING * SURGE_SPRING * surgeV) * DT;
		surge += surgeV * DT;

		// Sway with the stride, easing in and out as the mount starts and stops.
		float target = gait == 0 ? 0 : gait == 1 ? SWAY_WALK : SWAY_RUN;
		sway += (target - sway) * Math.min(1, SWAY_EASE * DT);
		float side = sway * (float) Math.sin(2 * Math.PI * cycle);

		x = sx + side;
		y = sy - settle; // y points down: settling from above
		z = sz + surge;
	}

	/** How far the smoothed seat is from the real one (for tests). */
	float lag(float seatX, float seatY, float seatZ)
	{
		return Math.max(Math.abs(sx - seatX), Math.max(Math.abs(sy - seatY), Math.abs(sz - seatZ)));
	}
}
