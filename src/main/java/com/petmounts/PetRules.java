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
 * Order of checks:
 * 1. The player's own "always allow" / "never allow" lists from the config
 * 2. Pets we know are awkward or unrealistic to ride (objects, floating things, humanoids, fish...)
 * 3. Pets we know make good mounts
 * 4. For anything else (new or unlisted pets), the model's shape: tall-and-thin (humanoid) models are refused.
 *
 * Floating pets are allowed; the plugin lowers them so the rider doesn't hover unrealistically high.
 */
final class PetRules
{
	enum Reason
	{
		OBJECT("isn't something you can ride."),
		TOO_SMALL("is far too small and fragile to carry you."),
		HUMANOID("walks on two legs and refuses to be ridden."),
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

	/** Pets that are awkward or unrealistic to ride. Names are lower case. */
	private static final Map<String, Reason> NOT_RIDEABLE = ImmutableMap.<String, Reason>builder()
		// Objects and oddities
		.put("pet rock", Reason.OBJECT)
		.put("toy cat", Reason.OBJECT)
		.put("spooky chair", Reason.OBJECT)
		.put("humphrey dumphrey", Reason.OBJECT)
		.put("smolcano", Reason.OBJECT)
		.put("tangleroot", Reason.OBJECT)
		// Too small / soft
		.put("maggot marquess", Reason.TOO_SMALL)
		// Humanoids
		.put("pet general graardor", Reason.HUMANOID)
		.put("pet k'ril tsutsaroth", Reason.HUMANOID)
		.put("pet kree'arra", Reason.HUMANOID)
		.put("pet zilyana", Reason.HUMANOID)
		.put("vet'ion jr.", Reason.HUMANOID)
		.put("nexling", Reason.HUMANOID)
		.put("noon", Reason.HUMANOID)
		.put("midnight", Reason.HUMANOID)
		.put("butch", Reason.HUMANOID)
		.put("smol heredit", Reason.HUMANOID)
		.put("yami", Reason.HUMANOID)
		.put("bran", Reason.HUMANOID)
		.put("tektiny", Reason.HUMANOID)
		.put("vanguard", Reason.HUMANOID)
		.put("lil' maiden", Reason.HUMANOID)
		.put("lil' sot", Reason.HUMANOID)
		.put("akkhito", Reason.HUMANOID)
		.put("tzrek-zuk", Reason.HUMANOID)
		// Snakes and worms
		.put("pet snakeling", Reason.SLITHERS)
		.put("jal-nib-rek", Reason.SLITHERS)
		// Water creatures
		.put("pet kraken", Reason.AQUATIC)
		.put("tiny tempor", Reason.AQUATIC)
		.put("pet fish", Reason.AQUATIC)
		.build();

	/** Pets known to make sensible mounts. Names are lower case. */
	private static final Set<String> RIDEABLE = ImmutableSet.of(
		// Boss pets
		"baby mole", "baby mole-rat", "callisto cub", "callisto cub (brown)", "hellpuppy", "ikkle hydra",
		"kalphite princess", "lil' zik", "lil' nylo", "lil' xarp", "olmlet", "puppadile", "vespina",
		"pet dagannoth prime", "pet dagannoth rex", "pet dagannoth supreme", "phoenix",
		"prince black dragon", "scorpia's offspring", "sraracha", "tzrek-jad", "venenatis spiderling",
		"vorki", "youngllef", "corrupted youngllef", "beef", "gull", "moxi", "nid", "scurry",
		"babi", "kephriti", "zebo",
		// Floating pets (lowered to a gentle hover when ridden)
		"rift guardian", "greatish guardian", "pet dark core", "corporeal critter", "pet chaos elemental", "pet smoke devil", "abyssal protector", "wisp", "skotos", "little nightmare", "little parasite", "vasa minirio", "lil' bloat", "tumeken's guardian", "tumeken's damaged guardian", "elidinis' guardian", "elidinis' damaged guardian",
		// Skilling pets
		"baby chinchompa", "beaver", "giant squirrel", "heron", "rock golem", "rocky",
		// Other
		"bloodhound", "chompy chick", "herbi", "pet penance queen", "quetzin", "mr mcgroot", "broav",
		"hellcat"
	);

	/** Words that mark a cat or dog pet (their names vary a lot). */
	private static final Set<String> CAT_AND_DOG_WORDS = ImmutableSet.of(
		"kitten", "cat", "puppy", "dog", "terrier", "labrador", "greyhound", "dalmatian", "sheepdog", "bulldog"
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
	 * @param name       pet name as shown in game
	 * @param shape      the pet's model measurements, or null if not loaded yet
	 * @param alwaysAllow names the player has chosen to allow
	 * @param neverAllow  names the player has chosen to block
	 */
	static Verdict check(String name, Shape shape, Set<String> alwaysAllow, Set<String> neverAllow)
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

		Reason known = NOT_RIDEABLE.get(n);
		if (known != null)
		{
			return Verdict.no(known);
		}
		if (RIDEABLE.contains(n) || isCatOrDog(n))
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

	private static boolean isCatOrDog(String n)
	{
		for (String word : n.split("[\\s-]+"))
		{
			if (CAT_AND_DOG_WORDS.contains(word))
			{
				return true;
			}
		}
		return false;
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
