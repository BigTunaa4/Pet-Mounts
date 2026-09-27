package com.petmounts;

import static org.junit.Assert.assertArrayEquals;
import org.junit.Test;

public class LightingTest
{
	@Test
	public void petsKeepTheirOwnLighting()
	{
		assertArrayEquals(new int[]{40, 0}, MountFits.lighting(1625)); // Hellcat: brighter than normal
		assertArrayEquals(new int[]{30, 30}, MountFits.lighting(318)); // Dark core
		assertArrayEquals(new int[]{0, 0}, MountFits.lighting(1619)); // Cat: normal lighting
		assertArrayEquals(new int[]{0, 0}, MountFits.lighting(-1)); // unknown pet
	}
}
