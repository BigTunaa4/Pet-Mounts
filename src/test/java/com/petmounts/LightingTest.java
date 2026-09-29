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

	@Test
	public void everyRideablePetCanBePickedAndAnimated()
	{
		org.junit.Assert.assertTrue(MountFits.choices().size() >= 90);
		for (int id : MountFits.choices().values())
		{
			org.junit.Assert.assertNotNull("tuned: " + id, MountFits.get(id));
			int[] anims = MountFits.animations(id);
			org.junit.Assert.assertNotNull("animations: " + id, anims);
			org.junit.Assert.assertTrue("has an idle animation: " + id, anims[0] != -1);
		}
		org.junit.Assert.assertEquals(Integer.valueOf(6635), MountFits.choices().get("Baby Mole"));
	}
}
