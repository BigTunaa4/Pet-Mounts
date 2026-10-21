package com.petmounts;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class RaidMountsTest
{
	private static final String[] RAID = {"Orion", "Orion (robed)", "Ocellus", "Ocellus (second form)", "Archive Guardian",
		"Bork (Fractured Archive)", "The Smorks", "Dreadnoth the Jellied", "Ezekiel", "Moia", "Dagon'hai Elder", "Xarth"};

	@Test
	public void everyRaidMountIsFittedAndAnimated()
	{
		for (String name : RAID)
		{
			Integer id = MountFits.choices().get(name);
			assertNotNull(name, id);
			MountFits.Fit fit = MountFits.get(id);
			assertNotNull(name, fit);
			assertTrue(name + " rides with no saddle", fit.shoulders);
			assertNotNull(name, MountFits.animations(id));
			assertNotNull(name + " has tricks", MountExtras.tricks(id));
		}
	}

	@Test
	public void orionsFollowerFormsShareTheFit()
	{
		assertEquals(MountFits.get(16764).vertexCount, MountFits.get(16632).vertexCount);
		assertEquals(MountFits.get(16764).a, MountFits.get(16632).a);
	}

	@Test
	public void raidBossesAreLockedButOrionIsNot()
	{
		java.util.Set<String> lockable = new java.util.HashSet<>();
		EarnedMounts.lockable().forEach(lockable::add);
		assertTrue(lockable.contains("Xarth"));
		assertTrue(lockable.contains("The Smorks"));
		assertTrue(!lockable.contains("Orion"));
	}
}
