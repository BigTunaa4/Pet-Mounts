package com.petmounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class RiderMotionTest
{
	private static final int ONE_SECOND = 50; // client ticks

	@Test
	public void dropsIntoTheSaddleAndSettles()
	{
		RiderMotion m = new RiderMotion();
		m.reset(true);
		assertTrue("starts above the seat", -m.y > RiderMotion.SETTLE_DROP - 2);
		float lowest = Float.MAX_VALUE;
		for (int i = 0; i < ONE_SECOND; i++)
		{
			m.update(0, 0, true);
			lowest = Math.min(lowest, -m.y);
		}
		assertTrue("never sinks far into the saddle", lowest >= -3.01f);
		assertTrue("gives a little bounce", lowest < 0);
		assertEquals(0, m.y, 1);
	}

	@Test
	public void swaysOnlyWhileMoving()
	{
		RiderMotion m = new RiderMotion();
		m.reset(false);
		for (int i = 0; i < ONE_SECOND; i++)
		{
			m.update(0, 0.25f, true);
		}
		assertEquals(0, m.x, 0.01);

		float widest = 0;
		for (int i = 0; i < 2 * ONE_SECOND; i++)
		{
			m.update(1, (i % 40) / 40f, true);
			widest = Math.max(widest, Math.abs(m.x));
		}
		assertTrue("gentle sway", widest > 1.5f && widest < 4f);
	}

	@Test
	public void rocksBackWhenSettingOffButNeverFar()
	{
		RiderMotion m = new RiderMotion();
		m.reset(false);
		m.update(0, 0, true);
		float furthest = 0;
		for (int i = 0; i < 20; i++)
		{
			m.update(2, 0, true); // straight into a run
			furthest = Math.max(furthest, m.z);
		}
		assertTrue("rocks toward the tail", furthest > 1.5f && furthest <= 6f);
	}

	@Test
	public void noJoltWhenFirstShown()
	{
		RiderMotion m = new RiderMotion();
		m.reset(false);
		m.update(2, 0, true); // someone already running when they come into view
		assertEquals(0, m.z, 0.5);
	}

	@Test
	public void stillWhenTurnedOff()
	{
		RiderMotion m = new RiderMotion();
		m.reset(true);
		m.update(2, 0.25f, false);
		assertEquals(0, m.x, 0);
		assertEquals(0, m.y, 0);
		assertEquals(0, m.z, 0);
	}
}
