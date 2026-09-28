package com.petmounts;

/**
 * Makes the rider move like someone sitting on a living animal rather than a model glued to it.
 *
 * <ul>
 *     <li><b>Settling:</b> you drop onto the saddle after the poof and give a small bounce.</li>
 *     <li><b>Stride sway:</b> a gentle side-to-side sway in time with the mount's walk or run cycle.</li>
 *     <li><b>Surge:</b> you rock back a little when the mount sets off and forward when it stops.</li>
 * </ul>
 *
 * These are offsets from the seat, in the mount's model space (x sideways, y pointing down, z toward the tail).
 * The seat itself is read from the mount's animation on every frame, so the rider never lags behind the saddle;
 * the offsets step once per client tick (20 ms).
 */
final class RiderMotion
{
	static final float DT = 0.02f;

	/** How high above the seat you appear before settling. */
	static final float SETTLE_DROP = 14f;
	private static final float SETTLE_SPRING = 15f, SETTLE_DAMPING = 0.42f;
	private static final float SURGE_SPRING = 9f, SURGE_DAMPING = 0.5f;
	/** Push per step of speed (standing, walking, running). */
	private static final float SURGE_KICK = 55f;
	/** The furthest the surge may rock you, so a stop-start never throws you off the saddle. */
	private static final float MAX_SURGE = 6f;
	private static final float SWAY_WALK = 2.5f, SWAY_RUN = 3.5f;
	private static final float SWAY_EASE = 5f;

	private float settle, settleV;
	private float surge, surgeV;
	private float sway;
	private int lastGait = -1;

	/** Offset from the seat this tick. */
	float x, y, z;

	/** Starts over, e.g. when a mount appears. {@code dropIn} starts the rider above the seat. */
	void reset(boolean dropIn)
	{
		settle = dropIn ? SETTLE_DROP : 0;
		settleV = 0;
		surge = 0;
		surgeV = 0;
		sway = 0;
		lastGait = -1;
		x = 0;
		y = dropIn ? -settle : 0;
		z = 0;
	}

	/**
	 * Steps the motion by one client tick.
	 *
	 * @param gait 0 standing, 1 walking, 2 running
	 * @param cycle how far through its walk or run cycle the mount is, 0 to 1
	 * @param natural false keeps the rider still on the seat (for players who turn the effect off)
	 */
	void update(int gait, float cycle, boolean natural)
	{
		if (!natural)
		{
			settle = settleV = surge = surgeV = sway = 0;
			lastGait = gait;
			x = y = z = 0;
			return;
		}

		// Two half steps keep the springs steady.
		for (int i = 0; i < 2; i++)
		{
			float h = DT / 2;
			settleV += (-SETTLE_SPRING * SETTLE_SPRING * settle - 2 * SETTLE_DAMPING * SETTLE_SPRING * settleV) * h;
			settle += settleV * h;
			if (settle < -3)
			{
				settle = -3; // a small give into the saddle, never through it
				settleV = Math.max(0, settleV);
			}
		}

		// Rock back when setting off, forward when stopping.
		if (lastGait >= 0 && gait != lastGait)
		{
			surgeV += (gait - lastGait) * SURGE_KICK;
		}
		lastGait = gait;
		surgeV += (-SURGE_SPRING * SURGE_SPRING * surge - 2 * SURGE_DAMPING * SURGE_SPRING * surgeV) * DT;
		surge = Math.max(-MAX_SURGE, Math.min(MAX_SURGE, surge + surgeV * DT));

		// Sway with the stride, easing in and out as the mount starts and stops.
		float target = gait == 0 ? 0 : gait == 1 ? SWAY_WALK : SWAY_RUN;
		sway += (target - sway) * Math.min(1, SWAY_EASE * DT);

		x = sway * (float) Math.sin(2 * Math.PI * cycle);
		y = -settle; // y points down: settling from above
		z = surge;
	}
}
