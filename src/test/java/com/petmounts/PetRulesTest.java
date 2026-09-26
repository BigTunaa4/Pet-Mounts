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

	private static PetRules.Verdict check(String name)
	{
		return PetRules.check(0, name, null, NONE, NONE);
	}

	@Test
	public void goodMountsAreAllowed()
	{
		assertTrue(check("Baby Mole").rideable);
		assertTrue(check("Prince Black Dragon").rideable);
		assertTrue(check("<col=ffff00>Vorki</col>").rideable);
		assertTrue(check("Overgrown cat").rideable);
		assertTrue(check("Border Collie puppy").rideable);
		// The Corporeal Beast pet, in both forms
		assertTrue(check("Dark core").rideable);
		assertTrue(check("Corporeal Critter").rideable);
	}

	@Test
	public void awkwardPetsAreRefused()
	{
		assertEquals(PetRules.Reason.OBJECT, check("Tangleroot").reason);
		assertEquals(PetRules.Reason.HUMANOID, check("General Graardor Jr.").reason);
		assertEquals(PetRules.Reason.HUMANOID, check("Olmlet").reason);
		assertEquals(PetRules.Reason.SLITHERS, check("Snakeling").reason);
		assertEquals(PetRules.Reason.AQUATIC, check("Kraken").reason);
	}

	@Test
	public void formsOfTheSamePetCanDiffer()
	{
		assertTrue(PetRules.check(6638, "Kalphite Princess", null, NONE, NONE).rideable);
		assertFalse(PetRules.check(6637, "Kalphite Princess", null, NONE, NONE).rideable);
	}

	@Test
	public void unlistedPetsAreJudgedByShape()
	{
		assertTrue(PetRules.check(0, "Brand new pet", new PetRules.Shape(80, 0, 120), NONE, NONE).rideable);
		assertTrue(PetRules.check(0, "Brand new pet", new PetRules.Shape(120, 50, 60), NONE, NONE).rideable);
		assertEquals(PetRules.Reason.HUMANOID, PetRules.check(0, "Brand new pet", new PetRules.Shape(150, 0, 60), NONE, NONE).reason);
	}

	@Test
	public void playerListsWin()
	{
		Set<String> allow = PetRules.parseList("Olmlet, Nexling");
		Set<String> deny = PetRules.parseList("baby mole");
		assertTrue(PetRules.check(0, "Olmlet", null, allow, deny).rideable);
		assertFalse(PetRules.check(0, "Baby Mole", null, allow, deny).rideable);
	}
}
