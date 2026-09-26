package com.petmounts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Collections;
import java.util.Set;
import org.junit.Test;

public class PetRulesTest
{
	private static final Set<String> NONE = Collections.emptySet();

	private static PetRules.Verdict check(String name, PetRules.Shape shape)
	{
		return PetRules.check(name, shape, NONE, NONE);
	}

	@Test
	public void goodMountsAreAllowed()
	{
		assertTrue(check("Baby mole", null).rideable);
		assertTrue(check("Prince Black Dragon", null).rideable);
		assertTrue(check("<col=ffff00>Olmlet</col>", null).rideable);
		assertTrue(check("Overgrown cat", null).rideable);
		assertTrue(check("Bulldog puppy", null).rideable);
		// Floating pets are allowed, including the Corporeal Beast pet
		assertTrue(check("Pet dark core", null).rideable);
		assertTrue(check("Corporeal critter", null).rideable);
		assertTrue(check("Rift guardian", null).rideable);
	}

	@Test
	public void awkwardPetsAreRefused()
	{
		assertEquals(PetRules.Reason.OBJECT, check("Pet rock", null).reason);
		assertEquals(PetRules.Reason.OBJECT, check("Toy cat", null).reason);
		assertEquals(PetRules.Reason.HUMANOID, check("Pet General Graardor", null).reason);
		assertEquals(PetRules.Reason.SLITHERS, check("Pet snakeling", null).reason);
		assertEquals(PetRules.Reason.AQUATIC, check("Pet kraken", null).reason);
	}

	@Test
	public void unlistedPetsAreJudgedByShape()
	{
		// solid four-legged creature: 80 tall, 120 long, on the ground
		assertTrue(check("Brand new pet", new PetRules.Shape(80, 0, 120)).rideable);
		// hovering orb: allowed (the gap underneath doesn't count as body height)
		assertTrue(check("Brand new pet", new PetRules.Shape(120, 50, 60)).rideable);
		// tall and thin, like a person
		assertEquals(PetRules.Reason.HUMANOID, check("Brand new pet", new PetRules.Shape(150, 0, 60)).reason);
	}

	@Test
	public void playerListsWin()
	{
		Set<String> allow = PetRules.parseList("Pet rock, Nexling");
		Set<String> deny = PetRules.parseList("baby mole");
		assertTrue(PetRules.check("Pet rock", null, allow, deny).rideable);
		assertFalse(PetRules.check("Baby mole", null, allow, deny).rideable);
	}
}
