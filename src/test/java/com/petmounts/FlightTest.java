package com.petmounts;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FlightTest
{
	@Test
	public void dragonsFly()
	{
		for (String name : new String[]{"Black dragon", "King Black Dragon", "Elvarg", "Rune dragon", "Brutal red dragon"})
		{
			assertEquals(name, 7870, MountFits.flightAnimation(MountFits.choices().get(name)));
		}
	}

	@Test
	public void othersStayOnTheGround()
	{
		for (String name : new String[]{"Baby red dragon", "Jaguar", "Skeletal Wyvern", "Cat"})
		{
			assertEquals(name, -1, MountFits.flightAnimation(MountFits.choices().get(name)));
		}
	}

	@Test
	public void gryphonsAndVorkathHaveMadeFlightPoses()
	{
		for (String name : new String[]{"Gryphon", "Vorkath"})
		{
			int npc = MountFits.choices().get(name);
			assertTrue(name, FlightPoses.has(npc));
			assertNotNull(name, FlightPoses.forNpc(npc));
		}
		assertFalse(FlightPoses.has(MountFits.choices().get("Jaguar")));
	}
}
