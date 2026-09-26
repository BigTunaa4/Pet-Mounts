package com.petmounts;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class MountSizingTest
{
	private static final float EPS = 0.001f;

	@Test
	public void tinyPetGrowsButIsCapped()
	{
		// 20-unit pet rock would need 7x to reach 140; capped at 4.5x
		assertEquals(4.5f, MountSizing.growthFactor(20, 140, 4.5f, 1f), EPS);
	}

	@Test
	public void smallPetGrowsToTarget()
	{
		assertEquals(2.0f, MountSizing.growthFactor(70, 140, 4.5f, 1f), EPS);
	}

	@Test
	public void bigPetIsNeverShrunk()
	{
		assertEquals(1.0f, MountSizing.growthFactor(260, 140, 4.5f, 1f), EPS);
	}

	@Test
	public void userMultiplierApplies()
	{
		assertEquals(3.0f, MountSizing.growthFactor(70, 140, 4.5f, 1.5f), EPS);
	}
}
