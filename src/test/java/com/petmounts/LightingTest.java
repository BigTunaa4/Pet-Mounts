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
			if (id != 5983) // the pet rock just sits there
			{
				org.junit.Assert.assertTrue("has an idle animation: " + id, anims[0] != -1);
			}
		}
		org.junit.Assert.assertEquals(Integer.valueOf(6635), MountFits.choices().get("Baby Mole"));
	}

	@Test
	public void creaturesCanBeRidden()
	{
		for (String name : new String[]{"Lava dragon", "Elvarg", "Vorkath", "Battle tortoise", "Pet rock", "Unicorn"})
		{
			Integer id = MountFits.choices().get(name);
			org.junit.Assert.assertNotNull(name, id);
			org.junit.Assert.assertNotNull(name, MountFits.get(id));
		}
		// Case doesn't split the list: "baby" and "Baby" sort together.
		String last = "";
		for (String name : MountFits.choices().keySet())
		{
			org.junit.Assert.assertTrue(name, String.CASE_INSENSITIVE_ORDER.compare(last, name) < 0);
			last = name;
		}
	}

	@Test
	public void dogsDig()
	{
		org.junit.Assert.assertArrayEquals(new int[]{MountExtras.DIG}, MountExtras.tricks(16361)); // Labrador
		org.junit.Assert.assertArrayEquals(new int[]{MountExtras.DIG_SMALL}, MountExtras.tricks(16433)); // Labrador puppy
		org.junit.Assert.assertArrayEquals(new int[]{MountExtras.DIG}, MountExtras.tricks(106)); // Wolf
		org.junit.Assert.assertArrayEquals(new int[]{1823, 2663}, MountExtras.tricks(1619)); // Cat: arch back, paw
		org.junit.Assert.assertNull(MountExtras.tricks(6635)); // Baby mole: none
	}

	@Test
	public void mountsHaveTheirOwnSoundsAndTrails()
	{
		org.junit.Assert.assertEquals(340, MountExtras.sound(1619)); // a cat purrs
		org.junit.Assert.assertEquals(-1, MountExtras.sound(6635));
		org.junit.Assert.assertEquals(MountExtras.Trail.LAVA, MountExtras.trail("Lava dragon"));
		org.junit.Assert.assertEquals(MountExtras.Trail.FIRE, MountExtras.trail("Phoenix"));
		org.junit.Assert.assertNull(MountExtras.trail("Cat"));
		// every mount with tricks or sounds is a real, tuned mount
		for (int id = 0; id < 20000; id++)
		{
			if (MountExtras.tricks(id) != null || MountExtras.sound(id) != -1)
			{
				org.junit.Assert.assertNotNull("tuned: " + id, MountFits.get(id));
			}
		}
	}
}
