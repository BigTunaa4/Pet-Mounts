package com.petmounts;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

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
		for (String name : new String[]{"Baby red dragon", "Jaguar", "Vorkath", "Skeletal Wyvern", "Gryphon", "Cat"})
		{
			assertEquals(name, -1, MountFits.flightAnimation(MountFits.choices().get(name)));
		}
	}
}
