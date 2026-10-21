package com.petmounts;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MountSearchTest
{
	@Test
	public void matchesEveryWordAnywhereInTheName()
	{
		assertTrue(MountStablePanel.matchesSearch("King Black Dragon", "drag"));
		assertTrue(MountStablePanel.matchesSearch("King Black Dragon", "black king"));
		assertTrue(MountStablePanel.matchesSearch("Bork (Fractured Archive)", "fractured"));
		assertTrue(MountStablePanel.matchesSearch("Dagon'hai Elder", "dagonhai"));
		assertFalse(MountStablePanel.matchesSearch("King Black Dragon", "red dragon"));
	}

	@Test
	public void everyMountCanBeFoundByItsOwnName()
	{
		for (String name : MountFits.choices().keySet())
		{
			assertTrue(name, MountStablePanel.matchesSearch(name, name.toLowerCase(java.util.Locale.ROOT)));
		}
	}
}
