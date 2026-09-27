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
		m.update(0, -100, 0, 0, 0, true);
		assertTrue("starts above the seat", -m.y > 100 + RiderMotion.SETTLE_DROP - 2);
		float lowest = Float.MAX_VALUE;
		for (int i = 0; i < ONE_SECOND; i++)
		{
			m.update(0, -100, 0, 0, 0, true);
			lowest = Math.min(lowest, -m.y);
		}
		assertTrue("never sinks far into the saddle", lowest >= 100 - 3.01f);
		assertTrue("gives a little bounce", lowest < 100);
		assertEquals(100, -m.y, 1);
	}

	@Test
	public void followsTheBackClosely()
	{
		RiderMotion m = new RiderMotion();
		m.reset(false);
		m.update(0, -100, 0, 0, 0, true);
		for (int i = 0; i < 10; i++)
		{
			m.update(0, -106, 0, 0, 0, true); // an animation frame lifts the back
		}
		assertTrue(m.lag(0, -106, 0) < 0.3f);
	}

	@Test
	public void swaysOnlyWhileMoving()
	{
		RiderMotion m = new RiderMotion();
		m.reset(false);
		for (int i = 0; i < ONE_SECOND; i++)
		{
			m.update(0, -100, 0, 0, 0.25f, true);
		}
		assertEquals(0, m.x, 0.01);

		float widest = 0;
		for (int i = 0; i < 2 * ONE_SECOND; i++)
		{
			m.update(0, -100, 0, 1, (i % 40) / 40f, true);
			widest = Math.max(widest, Math.abs(m.x));
		}
		assertTrue("gentle sway", widest > 1.5f && widest < 4f);
	}

	@Test
	public void rocksBackWhenSettingOff()
	{
		RiderMotion m = new RiderMotion();
		m.reset(false);
		m.update(0, -100, 0, 0, 0, true);
		float furthest = 0;
		for (int i = 0; i < 20; i++)
		{
			m.update(0, -100, 0, 1, 0, true);
			furthest = Math.max(furthest, m.z);
		}
		assertTrue("rocks toward the tail", furthest > 1.5f && furthest < 8f);
	}

	@Test
	public void fixedToTheSeatWhenTurnedOff()
	{
		RiderMotion m = new RiderMotion();
		m.reset(true);
		m.update(3, -100, 7, 2, 0.25f, false);
		assertEquals(3, m.x, 0);
		assertEquals(-100, m.y, 0);
		assertEquals(7, m.z, 0);
	}
}
