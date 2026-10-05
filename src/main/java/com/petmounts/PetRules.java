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
 * 2. Pets that are awkward or unrealistic to ride (objects, snakes, fish...). Two-legged pets carry you on their
 *    shoulders instead.
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
		.put("vanguard", Reason.OBJECT)
		// Too small
		.put("maggot marquess", Reason.TOO_SMALL)
		// Stand upright on two legs
		.put("abyssal protector", Reason.HUMANOID)
		.put("akkhito", Reason.HUMANOID)
		.put("elidinis' damaged guardian", Reason.HUMANOID)
		.put("elidinis' guardian", Reason.HUMANOID)
		.put("enraged tektiny", Reason.HUMANOID)
		.put("moxi", Reason.HUMANOID)
		.put("tektiny", Reason.HUMANOID)
		.put("tumeken's damaged guardian", Reason.HUMANOID)
		.put("tumeken's guardian", Reason.HUMANOID)
		.put("yami", Reason.HUMANOID)
		// Snakes and worms
		.put("huberte", Reason.SLITHERS)
		.put("jal-nib-rek", Reason.SLITHERS)
		.put("lil'viathan", Reason.SLITHERS)
		.put("snakeling", Reason.SLITHERS)
		// Water creatures
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
		"abyssal orphan", "aggy", "babi", "baby chinchompa", "baby mole", "baby mole-rat", "beaver",
		"beef", "bernese mountain dog", "bernese mountain dog puppy", "bloodhound", "bone squirrel", "border collie",
		"border collie puppy", "bran", "broav", "butch", "callisto cub", "cat", "chaos elemental jr.", "chihuahua",
		"chihuahua puppy", "chompy chick", "clockwork cat", "corgi", "corgi puppy", "corporeal critter",
		"corrupted youngllef", "dagannoth prime jr.", "dagannoth rex jr.", "dagannoth supreme jr.", "dark core",
		"dark squirrel", "dom", "flying vespina", "fox", "general graardor jr.", "giant squirrel",
		"great blue heron", "greatish guardian", "greyhound", "greyhound puppy", "gull", "gulliver", "hell-kitten",
		"hellcat", "hellpuppy", "herbi", "heron", "husky", "husky puppy", "ikkle hydra", "jalrek-jad",
		"k'ril tsutsaroth jr.", "kalphite princess", "kitten", "kraken", "kree'arra jr.", "labrador", "labrador puppy",
		"lazy cat", "lazy hellcat", "lil' bloat", "lil' creator", "lil' destructor", "lil' maiden", "lil' nylo", "lil' sot",
		"lil' xarp", "lil' zik", "little nightmare", "little parasite", "midnight", "mr mcgroot", "nexling", "nid", "noon",
		"olmlet", "overgrown cat", "overgrown hellcat", "penance pet", "pheasant", "phoenix", "prince black dragon", "pug",
		"pug puppy", "puppadile", "quetzin", "rax", "red", "ric", "rift guardian", "rock golem", "rocky", "samoyed",
		"samoyed puppy", "scorpia's offspring", "scurry", "shiba", "shiba puppy", "skotos", "smol heredit", "soup",
		"spaniel", "spaniel puppy", "sraracha", "tangleroot", "tzrek-jad", "tzrek-zuk", "venenatis spiderling",
		"vespina", "vet'ion jr.", "vorki", "wily cat", "wily hellcat", "wisp", "yorkie", "yorkie puppy", "youngllef",
		"zebo", "ziggy", "zilyana jr."
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
