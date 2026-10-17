package com.petmounts;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GlideTest
{
	@Test
	public void wingedMountsGlide()
	{
		for (String name : new String[]{"Gryphon", "Shellbane gryphon", "King Black Dragon", "Ancient Wyvern", "Phoenix",
			"Elvarg", "Vorkath"})
		{
			assertTrue(name, MountRig.isWinged(name, false, false));
		}
	}

	@Test
	public void othersStayOnTheGround()
	{
		assertFalse(MountRig.isWinged("Baby red dragon", false, false));
		assertFalse(MountRig.isWinged("Jaguar", false, false));
		assertFalse(MountRig.isWinged("Prince Black Dragon", true, false)); // floating pets already hover
		assertFalse(MountRig.isWinged("Kree'arra Jr.", false, true)); // shoulder rides
		assertFalse(MountRig.isWinged(null, false, false));
	}
}
