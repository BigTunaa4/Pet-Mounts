package com.petmounts;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides which pets can be ridden.
 *
 * Every ownable pet in the game was checked by looking at its model in its idle pose.
 *
 * Order of checks:
 * 1. The player's own "always allow" / "never allow" lists from the config
 * 2. Pets that are awkward or unrealistic to ride (objects, pets standing on two legs, snakes, fish...)
 * 3. Pets checked and tuned as mounts
 * 4. For anything else (pets released later), the model's shape: tall-and-thin (upright) models are refused.
 *
 * Floating pets are allowed; the plugin lowers them so the rider doesn't hover unrealistically high.
 */
final class PetRules
{
	enum Reason
	{
		OBJECT("isn't something you can ride."),
		TOO_SMALL("is far too small and fragile to carry you."),
		HUMANOID("stands on two legs and refuses to be ridden."),
		SLITHERS("slithers, so there's nowhere to sit."),
		AQUATIC("can't carry you on dry land."),
		SHAPE("isn't shaped for riding.");

		final String text;

		Reason(String text)
		{
			this.text = text;
		}
	}

	static final class Verdict
	{
		static final Verdict RIDEABLE = new Verdict(true, null);

		final boolean rideable;
		final Reason reason;

		private Verdict(boolean rideable, Reason reason)
		{
			this.rideable = rideable;
			this.reason = reason;
		}

		static Verdict no(Reason reason)
		{
			return new Verdict(false, reason);
		}
	}

	/**
	 * Pets that are awkward or unrealistic to ride, by their in-game name (lower case). Checked against
	 * every ownable pet's model in its idle pose.
	 */
	private static final Map<String, Reason> NOT_RIDEABLE = ImmutableMap.<String, Reason>builder()
		// Objects
		.put("smolcano", Reason.OBJECT)
		.put("tangleroot", Reason.OBJECT)
		.put("vanguard", Reason.OBJECT)
		// Too small
		.put("maggot marquess", Reason.TOO_SMALL)
		// Stand upright on two legs
		.put("abyssal orphan", Reason.HUMANOID)
		.put("abyssal protector", Reason.HUMANOID)
		.put("aggy", Reason.HUMANOID)
		.put("akkhito", Reason.HUMANOID)
		.put("bran", Reason.HUMANOID)
		.put("butch", Reason.HUMANOID)
		.put("dagannoth prime jr.", Reason.HUMANOID)
		.put("dagannoth rex jr.", Reason.HUMANOID)
		.put("dagannoth supreme jr.", Reason.HUMANOID)
		.put("elidinis' damaged guardian", Reason.HUMANOID)
		.put("elidinis' guardian", Reason.HUMANOID)
		.put("enraged tektiny", Reason.HUMANOID)
		.put("general graardor jr.", Reason.HUMANOID)
		.put("greatish guardian", Reason.HUMANOID)
		.put("k'ril tsutsaroth jr.", Reason.HUMANOID)
		.put("kree'arra jr.", Reason.HUMANOID)
		.put("lil' bloat", Reason.HUMANOID)
		.put("lil' creator", Reason.HUMANOID)
		.put("lil' destructor", Reason.HUMANOID)
		.put("lil' maiden", Reason.HUMANOID)
		.put("lil' xarp", Reason.HUMANOID)
		.put("little nightmare", Reason.HUMANOID)
		.put("midnight", Reason.HUMANOID)
		.put("moxi", Reason.HUMANOID)
		.put("nexling", Reason.HUMANOID)
		.put("noon", Reason.HUMANOID)
		.put("olmlet", Reason.HUMANOID)
		.put("ric", Reason.HUMANOID)
		.put("rift guardian", Reason.HUMANOID)
		.put("rock golem", Reason.HUMANOID)
		.put("skotos", Reason.HUMANOID)
		.put("smol heredit", Reason.HUMANOID)
		.put("tektiny", Reason.HUMANOID)
		.put("tumeken's damaged guardian", Reason.HUMANOID)
		.put("tumeken's guardian", Reason.HUMANOID)
		.put("tzrek-zuk", Reason.HUMANOID)
		.put("vet'ion jr.", Reason.HUMANOID)
		.put("yami", Reason.HUMANOID)
		.put("zilyana jr.", Reason.HUMANOID)
		// Snakes and worms
		.put("huberte", Reason.SLITHERS)
		.put("jal-nib-rek", Reason.SLITHERS)
		.put("lil'viathan", Reason.SLITHERS)
		.put("snakeling", Reason.SLITHERS)
		// Water creatures
		.put("kraken", Reason.AQUATIC)
		.put("tiny tempor", Reason.AQUATIC)
		// Not shaped for riding
		.put("baron", Reason.SHAPE)
		.put("kephriti", Reason.SHAPE)
		.put("muphin", Reason.SHAPE)
		.put("smoke devil", Reason.SHAPE)
		.put("vasa minirio", Reason.SHAPE)
		.build();

	/** Pet forms refused even though other forms of the same pet can be ridden (by NPC id). */
	private static final Map<Integer, Reason> NOT_RIDEABLE_FORMS = ImmutableMap.of(
		6637, Reason.HUMANOID // Kalphite Princess, upright winged form (the crawling form can be ridden)
	);

	/** Pets checked and tuned as mounts, by their in-game name (lower case). */
	private static final Set<String> RIDEABLE = ImmutableSet.of(
		"babi", "baby chinchompa", "baby mole", "baby mole-rat", "beaver", "beef", "bernese mountain dog",
		"bernese mountain dog puppy", "bloodhound", "bone squirrel", "border collie", "border collie puppy",
		"broav", "callisto cub", "cat", "chaos elemental jr.", "chihuahua", "chihuahua puppy", "chompy chick",
		"clockwork cat", "corgi", "corgi puppy", "corporeal critter", "corrupted youngllef", "dark core",
		"dark squirrel", "dom", "flying vespina", "fox", "giant squirrel", "great blue heron", "greyhound",
		"greyhound puppy", "gull", "gulliver", "hell-kitten", "hellcat", "hellpuppy", "herbi", "heron", "husky",
		"husky puppy", "ikkle hydra", "jalrek-jad", "kalphite princess", "kitten", "labrador", "labrador puppy",
		"lazy cat", "lazy hellcat", "lil' nylo", "lil' sot", "lil' zik", "little parasite", "mr mcgroot", "nid",
		"overgrown cat", "overgrown hellcat", "penance pet", "pheasant", "phoenix", "prince black dragon", "pug",
		"pug puppy", "puppadile", "quetzin", "rax", "red", "rocky", "samoyed", "samoyed puppy",
		"scorpia's offspring", "scurry", "shiba", "shiba puppy", "soup", "spaniel", "spaniel puppy", "sraracha",
		"tzrek-jad", "venenatis spiderling", "vespina", "vorki", "wily cat", "wily hellcat", "wisp", "yorkie",
		"yorkie puppy", "youngllef", "zebo", "ziggy"
	);

	private PetRules()
	{
	}

	static String normalize(String name)
	{
		if (name == null)
		{
			return "";
		}
		return name
			.replaceAll("<[^>]*>", "")
			.replace(' ', ' ')
			.trim()
			.toLowerCase(Locale.ROOT);
	}

	static Set<String> parseList(String csv)
	{
		if (csv == null || csv.isBlank())
		{
			return new HashSet<>();
		}
		return Arrays.stream(csv.split("[,\\n]"))
			.map(PetRules::normalize)
			.filter(s -> !s.isEmpty())
			.collect(Collectors.toSet());
	}

	/**
	 * @param npcId      the pet's NPC id (some pets have forms that differ)
	 * @param name       pet name as shown in game
	 * @param shape      the pet's model measurements, or null if not loaded yet
	 * @param alwaysAllow names the player has chosen to allow
	 * @param neverAllow  names the player has chosen to block
	 */
	static Verdict check(int npcId, String name, Shape shape, Set<String> alwaysAllow, Set<String> neverAllow)
	{
		String n = normalize(name);

		if (alwaysAllow.contains(n))
		{
			return Verdict.RIDEABLE;
		}
		if (neverAllow.contains(n))
		{
			return Verdict.no(Reason.SHAPE);
		}

		Reason known = NOT_RIDEABLE_FORMS.getOrDefault(npcId, NOT_RIDEABLE.get(n));
		if (known != null)
		{
			return Verdict.no(known);
		}
		if (RIDEABLE.contains(n))
		{
			return Verdict.RIDEABLE;
		}

		// Unlisted pet: judge it by its shape.
		if (shape == null)
		{
			return Verdict.RIDEABLE;
		}
		if (shape.isTallAndThin())
		{
			return Verdict.no(Reason.HUMANOID);
		}
		return Verdict.RIDEABLE;
	}

	/** Measurements of a pet model in local units, before any enlarging. Model Y points down. */
	static final class Shape
	{
		final float height;   // ground to top
		final float lift;     // gap between ground and the lowest point of the model
		final float footprint; // larger of width and length

		Shape(float height, float lift, float footprint)
		{
			this.height = height;
			this.lift = lift;
			this.footprint = footprint;
		}

		/** Much taller than it is wide or long, like a person standing up. */
		boolean isTallAndThin()
		{
			// Measure the body itself, not the gap under a floating pet.
			float body = height - lift;
			return footprint > 0 && body > footprint * 1.8f;
		}
	}
}
