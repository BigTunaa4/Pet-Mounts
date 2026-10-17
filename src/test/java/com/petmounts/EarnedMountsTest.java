package com.petmounts;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class EarnedMountsTest
{
	@Test
	public void firstKillUnlocksTheBossMount()
	{
		assertEquals("Araxxor", EarnedMounts.unlockedBy("Your Araxxor kill count is: <col=ff0000>1</col>."));
		assertEquals("Shellbane gryphon",
			EarnedMounts.unlockedBy("Your <col=ff0000>Shellbane Gryphon</col> kill count is: <col=ff0000>1</col>."));
		assertEquals("Manticore", EarnedMounts.unlockedBy("Your Sol Heredit kill count is: 1."));
	}

	@Test
	public void laterKillsAndOtherMessagesUnlockNothing()
	{
		assertNull(EarnedMounts.unlockedBy("Your Vorkath kill count is: <col=ff0000>1,204</col>."));
		assertNull(EarnedMounts.unlockedBy("Your Zulrah kill count is: 1."));
		assertNull(EarnedMounts.unlockedBy("You have a funny feeling like you're being followed."));
	}

	@Test
	public void everyLockableMountIsInTheMountList()
	{
		for (String name : EarnedMounts.lockable())
		{
			assertTrue(name, MountFits.choices().containsKey(name));
		}
	}

	@Test
	public void keysAreStable()
	{
		assertEquals("king_black_dragon", EarnedMounts.key("King Black Dragon"));
		assertEquals("sol_heredit", EarnedMounts.key(" Sol Heredit "));
	}
}
