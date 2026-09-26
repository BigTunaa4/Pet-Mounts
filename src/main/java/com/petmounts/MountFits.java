package com.petmounts;

import java.util.HashMap;
import java.util.Map;

/**
 * Hand-checked seat for every rideable pet, measured from the game's own models in each pet's idle pose.
 *
 * For each pet: the riding pose, how much to enlarge it, and the triangle on its back where the rider sits
 * (three vertex indices plus the seat's position inside that triangle). The plugin follows that exact spot as
 * the pet animates, so the rider moves with the pet's back. The vertex count guards against model updates:
 * if the pet's model changes, the plugin falls back to finding a seat automatically.
 *
 * Generated from cache revision 2026-09-23 (rev 240). 191 pet variants.
 */
final class MountFits
{
	static final class Fit
	{
		final RiderPose pose;
		final float growth;
		final int a, b, c;
		final float wa, wb, wc;
		final int vertexCount;

		Fit(RiderPose pose, float growth, int a, int b, int c, float wa, float wb, int vertexCount)
		{
			this.pose = pose;
			this.growth = growth;
			this.a = a;
			this.b = b;
			this.c = c;
			this.wa = wa;
			this.wb = wb;
			this.wc = 1f - wa - wb;
			this.vertexCount = vertexCount;
		}
	}

	private static final Map<Integer, Fit> FITS = build();

	private MountFits()
	{
	}

	/** The tuned seat for this pet NPC id, or null if it hasn't been tuned. */
	static Fit get(int npcId)
	{
		return FITS.get(npcId);
	}

	static int size()
	{
		return FITS.size();
	}

	private static void put(Map<Integer, Fit> m, int id, RiderPose pose, float growth, int a, int b, int c, float wa, float wb, int vc)
	{
		m.put(id, new Fit(pose, growth, a, b, c, wa, wb, vc));
	}

	private static Map<Integer, Fit> build()
	{
		Map<Integer, Fit> m = new HashMap<>();
		put(m, 318, RiderPose.CROSS_LEGGED, 1.355f, 260, 261, 262, 0.4578f, 0.2534f, 433); // Dark core
		put(m, 1619, RiderPose.WIDE, 2.24f, 93, 119, 40, 0.1865f, 0.5f, 209); // Cat
		put(m, 1620, RiderPose.WIDE, 2.24f, 93, 119, 40, 0.1865f, 0.5f, 209); // Cat
		put(m, 1621, RiderPose.WIDE, 2.24f, 93, 119, 40, 0.1865f, 0.5f, 209); // Cat
		put(m, 1622, RiderPose.WIDE, 2.24f, 93, 119, 40, 0.1865f, 0.5f, 209); // Cat
		put(m, 1623, RiderPose.WIDE, 2.24f, 93, 119, 40, 0.1865f, 0.5f, 209); // Cat
		put(m, 1624, RiderPose.WIDE, 2.24f, 93, 119, 40, 0.1865f, 0.5f, 209); // Cat
		put(m, 1625, RiderPose.WIDE, 2.24f, 74, 116, 117, 0.3975f, 0.1025f, 313); // Hellcat
		put(m, 1626, RiderPose.WIDE, 2.177f, 128, 157, 75, 0.2437f, 0.4744f, 322); // Lazy cat
		put(m, 1627, RiderPose.WIDE, 2.177f, 128, 157, 75, 0.2437f, 0.4744f, 322); // Lazy cat
		put(m, 1628, RiderPose.WIDE, 2.177f, 128, 157, 75, 0.2437f, 0.4744f, 322); // Lazy cat
		put(m, 1629, RiderPose.WIDE, 2.177f, 128, 157, 75, 0.2437f, 0.4744f, 322); // Lazy cat
		put(m, 1630, RiderPose.WIDE, 2.177f, 128, 157, 75, 0.2437f, 0.4744f, 322); // Lazy cat
		put(m, 1631, RiderPose.WIDE, 2.177f, 128, 157, 75, 0.2437f, 0.4744f, 322); // Lazy cat
		put(m, 1632, RiderPose.WIDE, 2.081f, 4, 8, 19, 0.2637f, 0.0275f, 347); // Lazy hellcat
		put(m, 2055, RiderPose.CROSS_LEGGED, 3.488f, 61, 65, 66, 0.0667f, 0.0829f, 282); // Chaos Elemental Jr.
		put(m, 2144, RiderPose.EXTRA_WIDE, 2.488f, 209, 266, 267, -0.0f, 0.2291f, 686); // Sraracha
		put(m, 2782, RiderPose.WIDE, 2.291f, 125, 127, 126, 0.2556f, 0.2444f, 333); // Clockwork cat
		put(m, 3081, RiderPose.CROSS_LEGGED, 3.098f, 267, 284, 268, 0.0f, 0.9673f, 523); // Phoenix
		put(m, 3082, RiderPose.CROSS_LEGGED, 3.098f, 267, 284, 268, 0.0f, 0.9673f, 523); // Phoenix
		put(m, 3083, RiderPose.CROSS_LEGGED, 3.098f, 267, 284, 268, 0.0f, 0.9673f, 523); // Phoenix
		put(m, 3084, RiderPose.CROSS_LEGGED, 3.098f, 267, 284, 268, 0.0f, 0.9673f, 523); // Phoenix
		put(m, 3099, RiderPose.WIDE, 1.816f, 291, 328, 329, 0.9563f, 0.0f, 574); // Hellpuppy
		put(m, 4002, RiderPose.EXTRA_WIDE, 2.696f, 127, 142, 143, 0.6143f, 0.0163f, 249); // Chompy chick
		put(m, 5557, RiderPose.EXTRA_WIDE, 3.358f, 318, 499, 319, -0.0f, 0.6273f, 2221); // Venenatis spiderling
		put(m, 5558, RiderPose.WIDE, 1.63f, 93, 101, 102, -0.0f, 0.2785f, 470); // Callisto cub
		put(m, 5561, RiderPose.EXTRA_WIDE, 1.3f, 137, 143, 138, 0.0f, 0.1619f, 267); // Scorpia's offspring
		put(m, 5584, RiderPose.WIDE, 1.878f, 126, 155, 74, 0.2102f, 0.5f, 316); // Wily cat
		put(m, 5585, RiderPose.WIDE, 1.878f, 126, 155, 74, 0.2102f, 0.5f, 316); // Wily cat
		put(m, 5586, RiderPose.WIDE, 1.878f, 126, 155, 74, 0.2102f, 0.5f, 316); // Wily cat
		put(m, 5587, RiderPose.WIDE, 1.878f, 126, 155, 74, 0.2102f, 0.5f, 316); // Wily cat
		put(m, 5588, RiderPose.WIDE, 1.878f, 126, 155, 74, 0.2102f, 0.5f, 316); // Wily cat
		put(m, 5589, RiderPose.WIDE, 1.878f, 126, 155, 74, 0.2102f, 0.5f, 316); // Wily cat
		put(m, 5590, RiderPose.WIDE, 1.881f, 126, 167, 127, 0.1797f, 0.5f, 321); // Wily hellcat
		put(m, 5591, RiderPose.WIDE, 3.828f, 163, 189, 110, 0.0154f, 0.4031f, 209); // Kitten
		put(m, 5592, RiderPose.WIDE, 3.828f, 163, 189, 110, 0.0154f, 0.4031f, 209); // Kitten
		put(m, 5593, RiderPose.WIDE, 3.828f, 163, 189, 110, 0.0154f, 0.4031f, 209); // Kitten
		put(m, 5594, RiderPose.WIDE, 3.828f, 163, 189, 110, 0.0154f, 0.4031f, 209); // Kitten
		put(m, 5595, RiderPose.WIDE, 3.828f, 163, 189, 110, 0.0154f, 0.4031f, 209); // Kitten
		put(m, 5596, RiderPose.WIDE, 3.828f, 163, 189, 110, 0.0154f, 0.4031f, 209); // Kitten
		put(m, 5597, RiderPose.WIDE, 4.5f, 127, 213, 128, 0.5841f, 0.3363f, 316); // Hell-kitten
		put(m, 5598, RiderPose.WIDE, 2.068f, 93, 119, 40, 0.1804f, 0.5f, 209); // Overgrown cat
		put(m, 5599, RiderPose.WIDE, 2.068f, 93, 119, 40, 0.1804f, 0.5f, 209); // Overgrown cat
		put(m, 5600, RiderPose.WIDE, 2.068f, 93, 119, 40, 0.1804f, 0.5f, 209); // Overgrown cat
		put(m, 5601, RiderPose.WIDE, 2.068f, 93, 119, 40, 0.1804f, 0.5f, 209); // Overgrown cat
		put(m, 5602, RiderPose.WIDE, 2.068f, 93, 119, 40, 0.1804f, 0.5f, 209); // Overgrown cat
		put(m, 5603, RiderPose.WIDE, 2.068f, 93, 119, 40, 0.1804f, 0.5f, 209); // Overgrown cat
		put(m, 5604, RiderPose.WIDE, 2.068f, 74, 116, 117, 0.4007f, 0.0993f, 313); // Overgrown hellcat
		put(m, 5893, RiderPose.EXTRA_WIDE, 1.429f, 125, 147, 126, -0.0f, 0.7364f, 483); // TzRek-Jad
		put(m, 6635, RiderPose.EXTRA_WIDE, 2.965f, 5, 18, 19, 0.0f, 0.4215f, 278); // Baby Mole
		put(m, 6636, RiderPose.EXTRA_WIDE, 2.06f, 734, 792, 793, 0.2544f, 0.6898f, 1414); // Prince Black Dragon
		put(m, 6638, RiderPose.EXTRA_WIDE, 1.427f, 210, 277, 211, 0.3678f, 0.5236f, 1130); // Kalphite Princess
		put(m, 6674, RiderPose.EXTRA_WIDE, 1.136f, 129, 159, 160, -0.0f, 0.7449f, 1239); // Penance Pet
		put(m, 6722, RiderPose.CROSS_LEGGED, 4.402f, 13, 54, 55, 0.8528f, 0.0f, 256); // Heron
		put(m, 6756, RiderPose.EXTRA_WIDE, 3.802f, 50, 51, 52, 0.8085f, 0.0957f, 141); // Baby Chinchompa
		put(m, 6757, RiderPose.EXTRA_WIDE, 3.802f, 50, 51, 52, 0.8085f, 0.0957f, 141); // Baby Chinchompa
		put(m, 6758, RiderPose.EXTRA_WIDE, 3.802f, 50, 51, 52, 0.8085f, 0.0957f, 141); // Baby Chinchompa
		put(m, 6759, RiderPose.EXTRA_WIDE, 3.814f, 50, 51, 52, 0.7635f, 0.1183f, 145); // Baby Chinchompa
		put(m, 7232, RiderPose.WIDE, 1.22f, 131, 149, 132, 0.0383f, 0.9617f, 458); // Bloodhound
		put(m, 7351, RiderPose.EXTRA_WIDE, 3.108f, 69, 103, 104, -0.0f, 0.3171f, 427); // Giant Squirrel
		put(m, 7353, RiderPose.WIDE, 1.698f, 158, 156, 159, -0.0f, 0.9673f, 319); // Rocky
		put(m, 7370, RiderPose.CROSS_LEGGED, 3.098f, 267, 284, 268, 0.0f, 0.9673f, 523); // Phoenix
		put(m, 7616, RiderPose.EXTRA_WIDE, 1.829f, 26, 46, 47, 0.1205f, 0.2339f, 1495); // Scurry
		put(m, 7760, RiderPose.EXTRA_WIDE, 1.863f, 164, 176, 177, 0.5682f, 0.0f, 826); // Herbi
		put(m, 8010, RiderPose.EXTRA_WIDE, 1.351f, 750, 754, 753, 0.53f, 0.2933f, 1456); // Corporeal Critter
		put(m, 8029, RiderPose.EXTRA_WIDE, 2.913f, 347, 81, 348, 0.1142f, 0.0961f, 1497); // Vorki
		put(m, 8201, RiderPose.EXTRA_WIDE, 1.844f, 727, 732, 733, 0.2248f, 0.2248f, 753); // Puppadile
		put(m, 8205, RiderPose.WIDE, 2.789f, 512, 522, 504, -0.0f, 0.7924f, 1137); // Vespina
		put(m, 8337, RiderPose.EXTRA_WIDE, 1.35f, 1605, 644, 1606, 0.0f, 0.6471f, 2110); // Lil' Zik
		put(m, 8492, RiderPose.EXTRA_WIDE, 1.5f, 127, 134, 135, 0.127f, 0.8144f, 1556); // Ikkle Hydra
		put(m, 8493, RiderPose.EXTRA_WIDE, 1.5f, 42, 49, 50, 0.1921f, 0.0439f, 1625); // Ikkle Hydra
		put(m, 8494, RiderPose.WIDE, 1.5f, 409, 618, 610, 0.3895f, 0.1969f, 1510); // Ikkle Hydra
		put(m, 8495, RiderPose.WIDE, 1.5f, 747, 1345, 753, 0.4388f, 0.18f, 1455); // Ikkle Hydra
		put(m, 8541, RiderPose.CROSS_LEGGED, 1.303f, 414, 416, 415, 0.7703f, 0.2297f, 800); // Little Parasite
		put(m, 8737, RiderPose.WIDE, 2.067f, 225, 246, 247, 0.0925f, 0.2074f, 1131); // Youngllef
		put(m, 8738, RiderPose.WIDE, 2.067f, 225, 246, 247, 0.0925f, 0.2074f, 1131); // Corrupted Youngllef
		put(m, 9514, RiderPose.CROSS_LEGGED, 1.69f, 513, 514, 515, 0.6433f, 0.0f, 1137); // Flying Vespina
		put(m, 9637, RiderPose.EXTRA_WIDE, 3.108f, 69, 103, 104, -0.0f, 0.3171f, 427); // Dark Squirrel
		put(m, 9852, RiderPose.WIDE, 1.698f, 153, 151, 154, -0.0f, 0.9672f, 319); // Red
		put(m, 9853, RiderPose.WIDE, 1.698f, 198, 154, 151, 0.0f, 0.0326f, 319); // Ziggy
		put(m, 10625, RiderPose.EXTRA_WIDE, 1.628f, 620, 622, 623, 0.6622f, 0.0473f, 1487); // JalRek-Jad
		put(m, 10636, RiderPose.CROSS_LEGGED, 4.398f, 13, 44, 45, 0.876f, 0.0f, 254); // Great blue heron
		put(m, 10651, RiderPose.EXTRA_WIDE, 2.965f, 5, 18, 19, 0.0f, 0.4215f, 278); // Baby Mole-rat
		put(m, 10872, RiderPose.WIDE, 1.197f, 859, 880, 881, 0.0f, 0.4771f, 1766); // Lil' Nylo
		put(m, 10873, RiderPose.EXTRA_WIDE, 1.35f, 751, 783, 782, 0.0081f, 0.0466f, 1480); // Lil' Sot
		put(m, 11159, RiderPose.EXTRA_WIDE, 2.488f, 1, 8, 9, 0.0f, 0.7709f, 686); // Sraracha
		put(m, 11160, RiderPose.EXTRA_WIDE, 2.488f, 1, 8, 9, 0.0f, 0.7709f, 686); // Sraracha
		put(m, 11847, RiderPose.WIDE, 1.326f, 994, 975, 171, 0.6196f, 0.0f, 1603); // Babi
		put(m, 11849, RiderPose.EXTRA_WIDE, 3.529f, 454, 504, 505, 0.0f, 0.4343f, 1712); // Zebo
		put(m, 11985, RiderPose.EXTRA_WIDE, 2.891f, 148, 147, 281, 0.3992f, 0.183f, 507); // Venenatis spiderling
		put(m, 11986, RiderPose.WIDE, 1.577f, 2, 8, 9, 0.2083f, 0.6576f, 464); // Callisto cub
		put(m, 12157, RiderPose.CROSS_LEGGED, 1.953f, 1497, 2009, 1998, 0.0f, 0.1544f, 2289); // Wisp
		put(m, 12181, RiderPose.EXTRA_WIDE, 3.502f, 106, 114, 107, 0.0198f, 0.9011f, 331); // Beaver
		put(m, 12182, RiderPose.EXTRA_WIDE, 3.502f, 106, 114, 107, 0.0198f, 0.9011f, 331); // Beaver
		put(m, 12183, RiderPose.EXTRA_WIDE, 3.502f, 106, 114, 107, 0.0198f, 0.9011f, 331); // Beaver
		put(m, 12184, RiderPose.EXTRA_WIDE, 3.502f, 106, 114, 107, 0.0198f, 0.9011f, 331); // Beaver
		put(m, 12185, RiderPose.EXTRA_WIDE, 3.502f, 106, 114, 107, 0.0198f, 0.9011f, 331); // Beaver
		put(m, 12186, RiderPose.EXTRA_WIDE, 3.502f, 106, 114, 107, 0.0198f, 0.9011f, 331); // Beaver
		put(m, 12187, RiderPose.EXTRA_WIDE, 3.502f, 106, 114, 107, 0.0198f, 0.9011f, 331); // Beaver
		put(m, 12188, RiderPose.EXTRA_WIDE, 3.502f, 106, 114, 107, 0.0198f, 0.9011f, 331); // Beaver
		put(m, 12189, RiderPose.EXTRA_WIDE, 3.502f, 106, 114, 107, 0.0198f, 0.9011f, 331); // Beaver
		put(m, 12190, RiderPose.EXTRA_WIDE, 3.502f, 106, 114, 107, 0.0198f, 0.9011f, 331); // Beaver
		put(m, 12549, RiderPose.EXTRA_WIDE, 2.85f, 7, 23, 24, 0.5074f, 0.3645f, 173); // Pheasant
		put(m, 12550, RiderPose.WIDE, 2.352f, 141, 188, 187, 0.0f, 0.6389f, 263); // Fox
		put(m, 12858, RiderPose.CROSS_LEGGED, 4.463f, 143, 173, 174, 0.1569f, 0.0f, 469); // Quetzin
		put(m, 13518, RiderPose.EXTRA_WIDE, 1.315f, 127, 49, 133, 0.861f, 0.0f, 307); // Broav
		put(m, 13683, RiderPose.EXTRA_WIDE, 2.485f, 1554, 1568, 1569, 0.0927f, 0.0f, 2019); // Nid
		put(m, 13684, RiderPose.EXTRA_WIDE, 2.584f, 124, 218, 219, 0.3733f, 0.2f, 527); // Rax
		put(m, 14044, RiderPose.EXTRA_WIDE, 3.581f, 453, 461, 462, 0.0f, 0.8758f, 715); // Bone Squirrel
		put(m, 14785, RiderPose.EXTRA_WIDE, 2.075f, 197, 530, 198, 0.0f, 0.0993f, 698); // Dom
		put(m, 14926, RiderPose.EXTRA_WIDE, 3.502f, 106, 114, 107, 0.0198f, 0.9011f, 331); // Beaver
		put(m, 14927, RiderPose.EXTRA_WIDE, 3.502f, 106, 114, 107, 0.0198f, 0.9011f, 331); // Beaver
		put(m, 14928, RiderPose.EXTRA_WIDE, 3.502f, 106, 114, 107, 0.0198f, 0.9011f, 331); // Beaver
		put(m, 14929, RiderPose.EXTRA_WIDE, 3.502f, 106, 114, 107, 0.0198f, 0.9011f, 331); // Beaver
		put(m, 14930, RiderPose.EXTRA_WIDE, 3.84f, 86, 88, 87, 0.0f, 0.3307f, 274); // Soup
		put(m, 14931, RiderPose.EXTRA_WIDE, 2.544f, 270, 210, 209, 0.0927f, 0.0f, 359); // Gull
		put(m, 14932, RiderPose.EXTRA_WIDE, 1.65f, 781, 783, 784, 0.0f, 0.1395f, 1465); // Gulliver
		put(m, 15631, RiderPose.WIDE, 1.627f, 88, 108, 109, -0.0f, 0.958f, 437); // Beef
		put(m, 16316, RiderPose.WIDE, 2.086f, 129, 67, 36, 0.2406f, 0.7594f, 323); // Mr McGroot
		put(m, 16361, RiderPose.WIDE, 1.294f, 77, 98, 99, -0.0f, 0.1496f, 318); // Labrador
		put(m, 16362, RiderPose.WIDE, 1.294f, 77, 98, 99, -0.0f, 0.1496f, 318); // Labrador
		put(m, 16363, RiderPose.WIDE, 1.294f, 77, 98, 99, -0.0f, 0.1496f, 318); // Labrador
		put(m, 16364, RiderPose.WIDE, 2.723f, 63, 67, 68, -0.0f, 0.565f, 293); // Chihuahua
		put(m, 16365, RiderPose.WIDE, 2.723f, 99, 100, 101, 0.565f, 0.0f, 293); // Chihuahua
		put(m, 16366, RiderPose.WIDE, 2.723f, 10, 26, 27, 0.0f, 0.4352f, 293); // Chihuahua
		put(m, 16367, RiderPose.WIDE, 1.383f, 48, 74, 75, 0.3524f, 0.0f, 334); // Border Collie
		put(m, 16368, RiderPose.WIDE, 1.383f, 46, 84, 85, 0.3524f, 0.0f, 336); // Border Collie
		put(m, 16369, RiderPose.WIDE, 1.383f, 48, 74, 75, 0.3524f, 0.0f, 334); // Border Collie
		put(m, 16370, RiderPose.WIDE, 3.228f, 41, 50, 51, 0.4681f, 0.5319f, 292); // Corgi
		put(m, 16371, RiderPose.WIDE, 3.228f, 41, 50, 51, 0.4681f, 0.5319f, 292); // Corgi
		put(m, 16372, RiderPose.WIDE, 3.227f, 40, 48, 49, 0.467f, 0.0f, 294); // Corgi
		put(m, 16373, RiderPose.WIDE, 1.872f, 203, 217, 218, 0.0f, 0.1027f, 309); // Greyhound
		put(m, 16374, RiderPose.WIDE, 1.872f, 203, 217, 218, 0.0f, 0.1027f, 309); // Greyhound
		put(m, 16375, RiderPose.WIDE, 1.872f, 76, 98, 77, 0.0f, 0.1032f, 309); // Greyhound
		put(m, 16376, RiderPose.WIDE, 1.493f, 97, 73, 101, -0.0f, 0.3331f, 286); // Husky
		put(m, 16377, RiderPose.WIDE, 1.493f, 97, 73, 101, -0.0f, 0.3331f, 286); // Husky
		put(m, 16378, RiderPose.WIDE, 1.493f, 97, 73, 101, -0.0f, 0.3331f, 286); // Husky
		put(m, 16379, RiderPose.WIDE, 2.765f, 56, 67, 68, -0.0f, 0.9814f, 270); // Pug
		put(m, 16380, RiderPose.WIDE, 2.765f, 56, 67, 68, -0.0f, 0.9814f, 270); // Pug
		put(m, 16381, RiderPose.WIDE, 2.765f, 28, 62, 63, 0.0f, 0.0186f, 270); // Pug
		put(m, 16382, RiderPose.WIDE, 1.312f, 168, 42, 41, 0.6321f, 0.0f, 338); // Samoyed
		put(m, 16383, RiderPose.WIDE, 1.312f, 168, 42, 41, 0.6327f, 0.0f, 338); // Samoyed
		put(m, 16384, RiderPose.WIDE, 1.312f, 168, 42, 41, 0.6327f, 0.0f, 338); // Samoyed
		put(m, 16385, RiderPose.WIDE, 1.322f, 249, 295, 296, 0.0f, 0.4445f, 303); // Bernese Mountain Dog
		put(m, 16386, RiderPose.WIDE, 1.322f, 54, 81, 82, 0.5555f, 0.0f, 303); // Bernese Mountain Dog
		put(m, 16387, RiderPose.WIDE, 1.322f, 11, 28, 29, 0.5555f, 0.4445f, 303); // Bernese Mountain Dog
		put(m, 16388, RiderPose.WIDE, 1.591f, 103, 70, 109, -0.0f, 0.7195f, 284); // Shiba
		put(m, 16389, RiderPose.WIDE, 1.591f, 103, 70, 109, -0.0f, 0.7195f, 284); // Shiba
		put(m, 16390, RiderPose.WIDE, 1.591f, 65, 205, 228, 0.7173f, 0.0f, 285); // Shiba
		put(m, 16391, RiderPose.WIDE, 2.25f, 119, 121, 122, 0.671f, 0.0f, 311); // Spaniel
		put(m, 16392, RiderPose.WIDE, 2.25f, 127, 129, 130, 0.6739f, 0.0f, 315); // Spaniel
		put(m, 16393, RiderPose.WIDE, 2.25f, 105, 108, 109, 0.6709f, 0.0f, 311); // Spaniel
		put(m, 16394, RiderPose.WIDE, 3.0f, 90, 103, 104, 0.5737f, 0.0f, 306); // Yorkie
		put(m, 16395, RiderPose.WIDE, 3.0f, 219, 300, 221, 0.0f, 0.5737f, 306); // Yorkie
		put(m, 16396, RiderPose.WIDE, 3.0f, 190, 273, 274, 0.4263f, 0.0f, 306); // Yorkie
		put(m, 16433, RiderPose.WIDE, 2.625f, 81, 102, 103, -0.0f, 0.504f, 284); // Labrador puppy
		put(m, 16434, RiderPose.WIDE, 2.625f, 81, 102, 103, -0.0f, 0.5088f, 284); // Labrador puppy
		put(m, 16435, RiderPose.WIDE, 2.625f, 81, 102, 103, -0.0f, 0.5088f, 284); // Labrador puppy
		put(m, 16436, RiderPose.WIDE, 2.351f, 90, 72, 94, -0.0f, 0.6552f, 268); // Husky puppy
		put(m, 16437, RiderPose.WIDE, 2.351f, 90, 72, 94, -0.0f, 0.6552f, 268); // Husky puppy
		put(m, 16438, RiderPose.WIDE, 2.351f, 90, 72, 94, -0.0f, 0.6552f, 268); // Husky puppy
		put(m, 16439, RiderPose.WIDE, 4.5f, 25, 30, 31, -0.0f, 0.351f, 250); // Chihuahua puppy
		put(m, 16440, RiderPose.WIDE, 4.5f, 25, 30, 31, -0.0f, 0.351f, 250); // Chihuahua puppy
		put(m, 16441, RiderPose.WIDE, 4.5f, 25, 30, 31, -0.0f, 0.351f, 250); // Chihuahua puppy
		put(m, 16442, RiderPose.WIDE, 2.386f, 4, 18, 5, 0.0f, 0.389f, 303); // Border Collie puppy
		put(m, 16443, RiderPose.WIDE, 2.386f, 5, 18, 6, 0.0f, 0.3859f, 305); // Border Collie puppy
		put(m, 16444, RiderPose.WIDE, 2.386f, 4, 18, 5, 0.0f, 0.3901f, 303); // Border Collie puppy
		put(m, 16445, RiderPose.WIDE, 4.023f, 49, 83, 84, 0.8972f, 0.0f, 268); // Corgi puppy
		put(m, 16446, RiderPose.WIDE, 4.023f, 49, 83, 84, 0.8972f, 0.0f, 268); // Corgi puppy
		put(m, 16447, RiderPose.WIDE, 4.023f, 25, 70, 26, 0.0f, 0.8972f, 268); // Corgi puppy
		put(m, 16448, RiderPose.WIDE, 3.387f, 255, 277, 204, -0.0f, 0.4145f, 281); // Greyhound puppy
		put(m, 16449, RiderPose.WIDE, 3.387f, 255, 277, 204, -0.0f, 0.4145f, 281); // Greyhound puppy
		put(m, 16450, RiderPose.WIDE, 3.387f, 118, 172, 173, 0.0f, 0.5855f, 281); // Greyhound puppy
		put(m, 16451, RiderPose.WIDE, 3.967f, 95, 100, 101, 0.5338f, 0.0f, 248); // Pug puppy
		put(m, 16452, RiderPose.WIDE, 3.967f, 95, 100, 101, 0.5338f, 0.0f, 248); // Pug puppy
		put(m, 16453, RiderPose.WIDE, 3.967f, 95, 100, 101, 0.5338f, 0.0f, 248); // Pug puppy
		put(m, 16454, RiderPose.WIDE, 2.601f, 145, 163, 164, 0.3733f, 0.0f, 318); // Samoyed puppy
		put(m, 16455, RiderPose.WIDE, 2.601f, 14, 106, 107, 0.3734f, 0.0f, 318); // Samoyed puppy
		put(m, 16456, RiderPose.WIDE, 2.601f, 14, 106, 107, 0.3734f, 0.0f, 318); // Samoyed puppy
		put(m, 16457, RiderPose.WIDE, 2.386f, 43, 52, 44, 0.0f, 0.3223f, 279); // Bernese Mountain Dog puppy
		put(m, 16458, RiderPose.WIDE, 2.386f, 11, 28, 29, 0.6777f, 0.0f, 278); // Bernese Mountain Dog puppy
		put(m, 16459, RiderPose.WIDE, 2.386f, 11, 28, 29, 0.6777f, 0.0f, 278); // Bernese Mountain Dog puppy
		put(m, 16460, RiderPose.WIDE, 2.917f, 45, 63, 64, -0.0f, 0.9044f, 268); // Shiba puppy
		put(m, 16461, RiderPose.WIDE, 2.917f, 37, 38, 39, 0.0f, 0.0956f, 268); // Shiba puppy
		put(m, 16462, RiderPose.WIDE, 2.917f, 39, 52, 53, 0.9042f, 0.0958f, 269); // Shiba puppy
		put(m, 16463, RiderPose.WIDE, 3.534f, 90, 111, 112, 0.7125f, 0.0f, 288); // Spaniel puppy
		put(m, 16464, RiderPose.WIDE, 3.534f, 42, 172, 173, 0.0f, 0.2886f, 288); // Spaniel puppy
		put(m, 16465, RiderPose.WIDE, 3.534f, 90, 111, 112, 0.7125f, 0.0f, 288); // Spaniel puppy
		put(m, 16466, RiderPose.WIDE, 4.5f, 75, 88, 89, 0.3617f, 0.0f, 258); // Yorkie puppy
		put(m, 16467, RiderPose.WIDE, 4.5f, 65, 39, 38, 0.6447f, 0.0f, 256); // Yorkie puppy
		put(m, 16468, RiderPose.WIDE, 4.5f, 75, 88, 89, 0.3617f, 0.0f, 258); // Yorkie puppy
		return m;
	}
}
