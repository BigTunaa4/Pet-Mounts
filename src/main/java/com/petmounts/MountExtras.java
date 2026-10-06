package com.petmounts;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The little things each mount does on its own: idle tricks played now and then while standing still, a sound now
 * and then, and a trail for the showpiece mounts.
 *
 * Every trick is one of the game's own animations made for the same skeleton as the mount (checked against the
 * cache), so it plays on the mount exactly as it does on the creature in the game. Sounds are the creature's own
 * sound effects, taken from its animations.
 */
final class MountExtras
{
	/** Dog dig animations: every dog, wolf and hound shares one skeleton, so they can all dig. */
	static final int DIG = 14498, DIG_SMALL = 14499;

	enum Trail
	{
		/** Little flames at its feet. */
		FIRE,
		/** Flames, and glowing lava footprints. */
		LAVA,
		/** Icy mist. */
		FROST,
		/** Ghostly mist. */
		GHOST
	}

	private static final Map<Integer, int[]> TRICKS = buildTricks();
	private static final Map<Integer, Integer> SOUNDS = buildSounds();
	private static final Map<String, Trail> TRAILS = buildTrails();

	private MountExtras()
	{
	}

	/** Animations this mount plays now and then while standing still, or null if it has none. */
	static int[] tricks(int npcId)
	{
		return TRICKS.get(npcId);
	}

	/** A sound this mount makes now and then, or -1. */
	static int sound(int npcId)
	{
		return SOUNDS.getOrDefault(npcId, -1);
	}

	/** The trail this mount leaves, by name, or null. */
	static Trail trail(String name)
	{
		return name == null ? null : TRAILS.get(name.toLowerCase(Locale.ROOT));
	}

	private static void put(Map<Integer, int[]> m, int[] tricks, int... ids)
	{
		for (int id : ids)
		{
			m.put(id, tricks);
		}
	}

	private static void put(Map<Integer, Integer> m, int sound, int... ids)
	{
		for (int id : ids)
		{
			m.put(id, sound);
		}
	}

	private static Map<Integer, int[]> buildTricks()
	{
		Map<Integer, int[]> m = new HashMap<>();
		// cats arch their backs and paw at things
		put(m, new int[]{1823, 2663},
			395, 540, 541, 1619, 1620, 1621, 1622, 1623, 1624, 1625, 1626, 1627, 1628, 1629, 1630, 1631, 1632, 2782,
			3498, 5584, 5585, 5586, 5587, 5588, 5589, 5590, 5591, 5592, 5593, 5594, 5595, 5596, 5597, 5598, 5599, 5600,
			5601, 5602, 5603, 5604, 6661, 6662, 6663, 6664, 6665, 6666, 6667, 6668, 6683, 6684, 6685, 6686, 6687, 6688,
			6689, 6690, 6691, 6692, 6693, 6694, 6695, 6696);
		// puppies and small dogs dig
		put(m, new int[]{14499},
			964, 3099, 16364, 16365, 16366, 16379, 16380, 16381, 16394, 16395, 16396, 16433, 16434, 16435, 16436, 16437,
			16438, 16439, 16440, 16441, 16442, 16443, 16444, 16445, 16446, 16447, 16448, 16449, 16450, 16451, 16452,
			16453, 16454, 16455, 16456, 16457, 16458, 16459, 16460, 16461, 16462, 16463, 16464, 16465, 16466, 16467,
			16468, 16543, 16544, 16545, 16558, 16559, 16560, 16573, 16574, 16575);
		// dragons rear up and breathe fire
		put(m, new int[]{81},
			239, 243, 245, 248, 252, 260, 268, 270, 272, 274, 817, 1871, 2918, 2919, 5873, 6593, 6636, 6652, 7039, 7273,
			7274, 7275, 7940, 8030, 8031, 9033, 9047, 14922);
		// dogs, wolves and hounds dig
		put(m, new int[]{14498},
			104, 106, 107, 109, 114, 3426, 4185, 6296, 7232, 12548, 12550, 14237, 16361, 16362, 16363, 16367, 16368,
			16369, 16370, 16371, 16372, 16373, 16374, 16375, 16376, 16377, 16378, 16382, 16383, 16384, 16385, 16386,
			16387, 16388, 16389, 16390, 16391, 16392, 16393, 16404, 16540, 16541, 16542, 16546, 16547, 16548, 16549,
			16550, 16551, 16552, 16553, 16554, 16555, 16556, 16557, 16561, 16562, 16563, 16564, 16565, 16566, 16567,
			16568, 16569, 16570, 16571, 16572);
		// bears rear up and stomp
		put(m, new int[]{8835},
			2838, 2839, 6503, 11986);
		// cattle graze
		put(m, new int[]{1735},
			2790, 15625, 15631, 15633);
		// demons raise their arms
		put(m, new int[]{69},
			425, 6634, 6647, 7671);
		// frogs hop
		put(m, new int[]{10957},
			477, 478);
		// unicorns and stags rear and kick
		put(m, new int[]{6376},
			1845, 2837, 2849);
		// camels eat
		put(m, new int[]{1896},
			2835, 4652);
		// Cerberus howls
		put(m, new int[]{4494},
			5862);
		// chickens flutter
		put(m, new int[]{5380},
			1173, 1175);
		// goats graze
		put(m, new int[]{1736},
			1792);
		// penguins flap, preen and wave
		put(m, new int[]{5699, 5700, 5695},
			830);
		// skeletal wyverns breathe ice
		put(m, new int[]{2988},
			466);
		// terror dogs look around
		put(m, new int[]{5623},
			6473);
		return m;
	}

	private static Map<Integer, Integer> buildSounds()
	{
		Map<Integer, Integer> m = new HashMap<>();
		// cats purr
		put(m, 340,
			395, 540, 541, 1619, 1620, 1621, 1622, 1623, 1624, 1625, 1626, 1627, 1628, 1629, 1630, 1631, 1632, 2782,
			3498, 5584, 5585, 5586, 5587, 5588, 5589, 5590, 5591, 5592, 5593, 5594, 5595, 5596, 5597, 5598, 5599, 5600,
			5601, 5602, 5603, 5604, 6661, 6662, 6663, 6664, 6665, 6666, 6667, 6668, 6683, 6684, 6685, 6686, 6687, 6688,
			6689, 6690, 6691, 6692, 6693, 6694, 6695, 6696);
		// spiders skitter
		put(m, 6963,
			495, 2143, 2144, 5557, 8713, 10763, 10872, 11157, 11158, 11159, 11160, 11998, 13681, 13682, 13683, 13684);
		// hellhounds snarl
		put(m, 6904,
			104, 964, 3099, 5862);
		// dragons roar
		put(m, 3752,
			239, 243, 245, 248, 252, 260, 268, 270, 272, 274, 466, 817, 1871, 2918, 2919, 5873, 6593, 6636, 6652, 7039,
			7273, 7274, 7275, 7792, 7793, 7794, 7795, 7940, 8025, 8029, 8030, 8031, 8061, 8610, 8612, 9033, 9047, 14922);
		// birds call
		put(m, 10495,
			6715, 6722, 6817, 10636, 12547, 12549, 12768, 12858);
		// dogs bark
		put(m, 12113,
			114, 6296, 7232, 12548, 12550, 14237, 16361, 16362, 16363, 16364, 16365, 16366, 16367, 16368, 16369, 16370,
			16371, 16372, 16373, 16374, 16375, 16376, 16377, 16378, 16379, 16380, 16381, 16382, 16383, 16384, 16385,
			16386, 16387, 16388, 16389, 16390, 16391, 16392, 16393, 16394, 16395, 16396, 16404, 16433, 16434, 16435,
			16436, 16437, 16438, 16439, 16440, 16441, 16442, 16443, 16444, 16445, 16446, 16447, 16448, 16449, 16450,
			16451, 16452, 16453, 16454, 16455, 16456, 16457, 16458, 16459, 16460, 16461, 16462, 16463, 16464, 16465,
			16466, 16467, 16468, 16540, 16541, 16542, 16543, 16544, 16545, 16546, 16547, 16548, 16549, 16550, 16551,
			16552, 16553, 16554, 16555, 16556, 16557, 16558, 16559, 16560, 16561, 16562, 16563, 16564, 16565, 16566,
			16567, 16568, 16569, 16570, 16571, 16572, 16573, 16574, 16575);
		// raccoons chatter
		put(m, 10485,
			7336, 7353, 9850, 9851, 9852, 9853);
		// cattle snort
		put(m, 11267,
			2790, 15625, 15631, 15633);
		// Graardor Jr. bellows
		put(m, 3843,
			6632, 6644);
		// K'ril Jr. and Skotos growl
		put(m, 3865,
			425, 6634, 6647, 7671);
		// Kree'arra Jr. screeches
		put(m, 3882,
			6631, 6643);
		// wolves howl
		put(m, 10419,
			106, 107, 109, 3426, 4185);
		// big cats growl
		put(m, 10487,
			2907, 2909);
		// the mammoth trumpets
		put(m, 623,
			6604);
		// the nail beast growls
		put(m, 3482,
			2946);
		// the pig grunts
		put(m, 10486,
			2796);
		return m;
	}

	private static Map<String, Trail> buildTrails()
	{
		Map<String, Trail> m = new HashMap<>();
		for (String name : new String[]{"phoenix", "hellcat", "lazy hellcat", "wily hellcat", "overgrown hellcat",
			"hell-kitten", "hellpuppy", "cerberus", "hellhound", "tzrek-jad", "jalrek-jad", "tzrek-zuk",
			"lil' destructor", "bran", "elvarg", "red dragon", "brutal red dragon", "baby red dragon"})
		{
			m.put(name, Trail.FIRE);
		}
		m.put("lava dragon", Trail.LAVA);
		for (String name : new String[]{"vorkath", "vorki", "frost dragon", "ric", "skeletal wyvern"})
		{
			m.put(name, Trail.FROST);
		}
		for (String name : new String[]{"revenant dragon", "reanimated dragon", "wisp", "little nightmare", "dark core",
			"abyssal orphan", "vet'ion jr.", "skotos", "corporeal critter"})
		{
			m.put(name, Trail.GHOST);
		}
		return m;
	}
}
