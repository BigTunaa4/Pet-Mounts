package com.petmounts;

import java.awt.Color;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.client.util.Text;

/**
 * Loads pets from the game's cache and turns them into mounts: enlarged, lowered if they float, lit the way the
 * game lights them, with the seat worked out. Shared by your own mount and other players' mounts.
 */
@Slf4j
final class PetModels
{
	static final int SCALE_BASE = 128;
	/** Floating pets are lowered so their underside hovers no more than this above the ground. */
	static final int MAX_HOVER = 12;
	/** Pets grow until the seat is about this high: pony height, so legs hang down naturally. */
	static final int TARGET_SEAT_HEIGHT = 90;
	/**
	 * The biggest a mount may be (before the player's size settings): about 2.5 tiles long, 3 tiles wide and
	 * 2 tiles tall. Long, wide and tall pets (wings, tails, legs, necks) are kept to this even if their back
	 * ends up lower, so no mount towers over everything around it.
	 */
	static final int MAX_LENGTH = 320;
	static final int MAX_WIDTH = 384;
	static final int MAX_HEIGHT = 250;
	/** The lowest seat each pose works on: below this the legs would go through the ground. */
	private static final int MIN_WIDE_SEAT = 85;
	private static final int MIN_EXTRA_WIDE_SEAT = 42;
	static final float MAX_GROWTH = 4.5f;
	/** Untuned pets at least this wide at the seat (half-width, after enlarging) get the Extra wide pose. */
	static final int EXTRA_WIDE_HALF_WIDTH = 40;
	/** The game's base lighting for NPC models; each NPC adds its own ambient and contrast on top. */
	private static final int NPC_AMBIENT = 64;
	private static final int NPC_CONTRAST = 850;

	/** A pet made into a mount. */
	static final class Built
	{
		final int npcId;
		final String petName;
		final Model model;
		final int a, b, c;
		final float wa, wb, wc;
		final int mountHeight;
		final RiderPose autoPose;
		final Color[] palette;
		/** Gold or silver saddle fittings, whichever suits the pet's colours. */
		final short trim;
		/**
		 * The pet at its own size, to animate: the enlargement is applied after each animation frame, the way the
		 * game draws scaled NPCs. (Enlarging first breaks skeletal animations, which move bones by fixed amounts.)
		 * Null to animate {@link #model} directly.
		 */
		Model base;
		/** How much {@link #base} is enlarged (sideways, up) and lowered (model units) after animating. */
		float scaleX = 1, scaleY = 1, hover;

		Built(int npcId, String petName, Model model, int a, int b, int c, float wa, float wb, float wc,
			int mountHeight, RiderPose autoPose, Color[] palette, short trim)
		{
			this.npcId = npcId;
			this.petName = petName;
			this.model = model;
			this.a = a;
			this.b = b;
			this.c = c;
			this.wa = wa;
			this.wb = wb;
			this.wc = wc;
			this.mountHeight = mountHeight;
			this.autoPose = autoPose;
			this.palette = palette;
			this.trim = trim;
		}
	}

	private final Client client;

	PetModels(Client client)
	{
		this.client = client;
	}

	static NPCComposition compositionOf(NPC pet)
	{
		NPCComposition comp = pet.getTransformedComposition();
		return comp != null ? comp : pet.getComposition();
	}

	static String nameOf(NPCComposition comp)
	{
		return comp == null || comp.getName() == null ? null : Text.removeTags(comp.getName());
	}

	/**
	 * Real pets, unlike quest companions and other followers, can be picked up. (The follower flag isn't used:
	 * the game's "Move follower options lower down" setting changes it.)
	 */
	static boolean isOwnablePet(NPCComposition comp)
	{
		if (comp == null)
		{
			return false;
		}
		String[] actions = comp.getActions();
		if (actions == null)
		{
			return false;
		}
		for (String action : actions)
		{
			if (action != null && action.equalsIgnoreCase("Pick-up"))
			{
				return true;
			}
		}
		return false;
	}

	/** Loads the pet's models merged and recoloured, at their original size. Null if not loaded yet. */
	ModelData load(NPCComposition comp)
	{
		int[] modelIds = comp.getModels();
		if (modelIds == null || modelIds.length == 0)
		{
			return null;
		}

		ModelData[] parts = new ModelData[modelIds.length];
		for (int i = 0; i < modelIds.length; i++)
		{
			parts[i] = client.loadModelData(modelIds[i]);
			if (parts[i] == null)
			{
				return null;
			}
		}

		ModelData md = client.mergeModels(parts)
			.cloneVertices()
			.cloneColors();

		short[] find = comp.getColorToReplace();
		short[] repl = comp.getColorToReplaceWith();
		if (find != null && repl != null)
		{
			for (int i = 0; i < Math.min(find.length, repl.length); i++)
			{
				md.recolor(find[i], repl[i]);
			}
		}
		return md;
	}

	static PetRules.Shape measure(ModelData md, NPCComposition comp)
	{
		float[] xs = md.getVerticesX();
		float[] ys = md.getVerticesY();
		float[] zs = md.getVerticesZ();
		float minX = 0, maxX = 0, minY = 0, maxY = 0, minZ = 0, maxZ = 0;
		for (int i = 0; i < md.getVerticesCount(); i++)
		{
			minX = Math.min(minX, xs[i]);
			maxX = Math.max(maxX, xs[i]);
			minY = Math.min(minY, ys[i]);
			maxY = Math.max(maxY, ys[i]);
			minZ = Math.min(minZ, zs[i]);
			maxZ = Math.max(maxZ, zs[i]);
		}
		float ws = (comp.getWidthScale() > 0 ? comp.getWidthScale() : SCALE_BASE) / (float) SCALE_BASE;
		float hs = (comp.getHeightScale() > 0 ? comp.getHeightScale() : SCALE_BASE) / (float) SCALE_BASE;

		// Model Y points down: the top is at -minY, and maxY < 0 means the model's lowest point is above the ground.
		float height = -minY * hs;
		float lift = Math.max(0, -maxY) * hs;
		float footprint = Math.max(maxX - minX, maxZ - minZ) * ws;
		return new PetRules.Shape(height, lift, footprint);
	}

	/**
	 * Makes the mount for this pet.
	 *
	 * @param sizeScale the player's size settings (1 = as fitted)
	 * @return the mount, or null if the pet's models aren't loaded yet or no seat could be found
	 */
	Built build(NPCComposition comp, float sizeScale)
	{
		ModelData md = load(comp);
		if (md == null)
		{
			return null;
		}

		int ws = comp.getWidthScale() > 0 ? comp.getWidthScale() : SCALE_BASE;
		int hs = comp.getHeightScale() > 0 ? comp.getHeightScale() : SCALE_BASE;

		// A tuned seat is only valid for the exact model it was measured on.
		MountFits.Fit fit = MountFits.get(comp.getId());
		if (fit != null && fit.vertexCount != md.getVerticesCount())
		{
			log.debug("Pet {} model changed since tuning ({} vs {} vertices); finding a seat automatically",
				comp.getId(), md.getVerticesCount(), fit.vertexCount);
			fit = null;
		}

		// Everything done to the model below, so the same can be done after each animation frame.
		float fx = 1, fy = 1, hover = 0;

		// How much to enlarge the pet: tuned, or grown until its back is about pony height.
		float growth;
		if (fit != null)
		{
			growth = fit.growth;
		}
		else
		{
			md.scale(ws, hs, ws); // the pet at its normal in-game size, to measure it
			fx *= ws / (float) SCALE_BASE;
			fy *= hs / (float) SCALE_BASE;
			SeatFinder.Seat found = findSeat(md);
			float seatHeight = found != null ? found.height - Math.max(0, -maxY(md)) : -minY(md) * 0.6f;
			growth = MountSizing.growthFactor(Math.round(seatHeight), TARGET_SEAT_HEIGHT, MAX_GROWTH, 1f);
			ws = hs = SCALE_BASE; // already applied
		}
		growth *= sizeScale;

		md.scale(Math.round(ws * growth), Math.round(hs * growth), Math.round(ws * growth));
		fx *= Math.round(ws * growth) / (float) SCALE_BASE;
		fy *= Math.round(hs * growth) / (float) SCALE_BASE;

		// Floating pets: bring them down to a gentle hover so the rider isn't up in the air.
		// Model Y points down, so a negative lowest point means the model floats above the ground.
		float lowest = maxY(md);
		boolean floating = lowest < -MAX_HOVER;
		if (floating)
		{
			md.translate(0, Math.round(-lowest - MAX_HOVER), 0);
			hover = Math.round(-lowest - MAX_HOVER);
		}

		// Tuned pets were fitted for a seat at horse height: bring the seat down to pony height. And keep long,
		// wide and tall pets to a sensible size. Never smaller than the pet itself.
		float cap = capFactor(md, sizeScale);
		if (fit != null)
		{
			float seat = seatHeight(md, fit.a, fit.b, fit.c, fit.wa, fit.wb, fit.wc);
			cap = Math.min(cap, TARGET_SEAT_HEIGHT * sizeScale / Math.max(1f, seat));
		}
		float fitted = Math.max(cap, Math.min(1f, sizeScale / growth));
		if (fitted < 1f)
		{
			int s = Math.max(1, Math.round(SCALE_BASE * fitted));
			md.scale(s, s, s);
			growth *= s / (float) SCALE_BASE;
			fx *= s / (float) SCALE_BASE;
			fy *= s / (float) SCALE_BASE;
			hover *= s / (float) SCALE_BASE;
		}
		int mountHeight = Math.max(1, Math.round(-minY(md)));

		// The seat and pose: tuned, or found on the enlarged model.
		RiderPose autoPose;
		int sa, sb, sc;
		float swa, swb, swc;
		if (fit != null)
		{
			autoPose = poseForSeat(fit.pose, seatHeight(md, fit.a, fit.b, fit.c, fit.wa, fit.wb, fit.wc));
			sa = fit.a;
			sb = fit.b;
			sc = fit.c;
			swa = fit.wa;
			swb = fit.wb;
			swc = fit.wc;
		}
		else
		{
			SeatFinder.Seat found = findSeat(md);
			if (found == null)
			{
				return null;
			}
			autoPose = floating ? RiderPose.CROSS_LEGGED
				: poseForSeat(halfWidthAt(md, found) >= EXTRA_WIDE_HALF_WIDTH ? RiderPose.EXTRA_WIDE : RiderPose.WIDE,
				-(found.wa * md.getVerticesY()[found.a] + found.wb * md.getVerticesY()[found.b]
					+ found.wc * md.getVerticesY()[found.c]));
			sa = found.a;
			sb = found.b;
			sc = found.c;
			swa = found.wa;
			swb = found.wb;
			swc = found.wc;
		}

		Color[] palette = PetPalette.fromModel(md.getFaceColors(), md.getFaceTextures());
		short trim = SaddleMesh.trimFor(md.getFaceColors(), md.getFaceTransparencies());

		// Lit exactly as the game lights this NPC, so the mount looks like the pet you know.
		int[] lighting = MountFits.lighting(comp.getId());
		Model model = md.light(NPC_AMBIENT + lighting[0], NPC_CONTRAST + lighting[1] * 5, -30, -50, -30);
		if (model == null)
		{
			return null;
		}

		int[] mapped = null;
		if (model.getVerticesCount() != md.getVerticesCount())
		{
			// The lit model numbers its vertices differently. Keep the fitted seat by finding the same points on it.
			mapped = mapVertices(md, model, sa, sb, sc);
			if (mapped != null)
			{
				sa = mapped[0];
				sb = mapped[1];
				sc = mapped[2];
			}
		}
		if (model.getVerticesCount() != md.getVerticesCount() && mapped == null)
		{
			// Couldn't match them up: find the seat on the lit model directly.
			SeatFinder.Seat onModel = SeatFinder.find(model.getVerticesX(), model.getVerticesY(), model.getVerticesZ(),
				model.getVerticesCount(), model.getFaceIndices1(), model.getFaceIndices2(), model.getFaceIndices3(),
				model.getFaceTransparencies(), model.getFaceCount());
			if (onModel == null)
			{
				return null;
			}
			sa = onModel.a;
			sb = onModel.b;
			sc = onModel.c;
			swa = onModel.wa;
			swb = onModel.wb;
			swc = onModel.wc;
		}

		Built built = new Built(comp.getId(), nameOf(comp), model, sa, sb, sc, swa, swb, swc, mountHeight, autoPose,
			palette, trim);

		// The pet at its own size, lit the same way, for animating before enlarging.
		ModelData raw = load(comp);
		Model base = raw == null ? null
			: raw.light(NPC_AMBIENT + lighting[0], NPC_CONTRAST + lighting[1] * 5, -30, -50, -30);
		if (base != null && base.getVerticesCount() == model.getVerticesCount())
		{
			built.base = base;
			built.scaleX = fx;
			built.scaleY = fy;
			built.hover = hover;
		}

		log.debug("Built mount for npc {} ({}): {}, growth {}x, pose {}",
			comp.getId(), comp.getName(), fit != null ? "tuned" : "automatic", growth, autoPose);
		return built;
	}

	/** The lit model's vertices at the same places as these model-data vertices, or null if any is missing. */
	private static int[] mapVertices(ModelData md, Model model, int... vertices)
	{
		float[] mx = md.getVerticesX(), my = md.getVerticesY(), mz = md.getVerticesZ();
		float[] lx = model.getVerticesX(), ly = model.getVerticesY(), lz = model.getVerticesZ();
		int[] out = new int[vertices.length];
		for (int i = 0; i < vertices.length; i++)
		{
			int v = vertices[i];
			if (v < 0 || v >= md.getVerticesCount())
			{
				return null;
			}
			int best = -1;
			float bestD = 0.5f;
			for (int j = 0; j < model.getVerticesCount(); j++)
			{
				float d = Math.abs(lx[j] - mx[v]) + Math.abs(ly[j] - my[v]) + Math.abs(lz[j] - mz[v]);
				if (d < bestD)
				{
					bestD = d;
					best = j;
				}
			}
			if (best < 0)
			{
				return null;
			}
			out[i] = best;
		}
		return out;
	}

	/** How much to shrink this (enlarged) pet to keep it within the biggest mount size, or 1 if it fits. */
	private static float capFactor(ModelData md, float sizeScale)
	{
		float[] xs = md.getVerticesX(), ys = md.getVerticesY(), zs = md.getVerticesZ();
		int[] f1 = md.getFaceIndices1(), f2 = md.getFaceIndices2(), f3 = md.getFaceIndices3();
		byte[] alphas = md.getFaceTransparencies();
		float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE, top = 0;
		for (int f = 0; f < md.getFaceCount(); f++)
		{
			if (alphas != null && (alphas[f] & 0xFF) >= 254)
			{
				continue; // invisible faces don't count
			}
			for (int v : new int[]{f1[f], f2[f], f3[f]})
			{
				minX = Math.min(minX, xs[v]);
				maxX = Math.max(maxX, xs[v]);
				minZ = Math.min(minZ, zs[v]);
				maxZ = Math.max(maxZ, zs[v]);
				top = Math.min(top, ys[v]);
			}
		}
		if (minX > maxX)
		{
			return 1f;
		}
		float cap = 1f;
		cap = Math.min(cap, MAX_LENGTH * sizeScale / Math.max(1f, maxZ - minZ));
		cap = Math.min(cap, MAX_WIDTH * sizeScale / Math.max(1f, maxX - minX));
		cap = Math.min(cap, MAX_HEIGHT * sizeScale / Math.max(1f, -top));
		return cap;
	}

	/** The pose, or a lower one if the seat is too low for it. */
	static RiderPose poseForSeat(RiderPose pose, float seatHeight)
	{
		if (pose == RiderPose.WIDE && seatHeight < MIN_WIDE_SEAT)
		{
			pose = RiderPose.EXTRA_WIDE;
		}
		if (pose == RiderPose.EXTRA_WIDE && seatHeight < MIN_EXTRA_WIDE_SEAT)
		{
			pose = RiderPose.CROSS_LEGGED;
		}
		return pose;
	}

	private static float seatHeight(ModelData md, int a, int b, int c, float wa, float wb, float wc)
	{
		float[] ys = md.getVerticesY();
		return -(wa * ys[a] + wb * ys[b] + wc * ys[c]);
	}

	private static SeatFinder.Seat findSeat(ModelData md)
	{
		return SeatFinder.find(md.getVerticesX(), md.getVerticesY(), md.getVerticesZ(), md.getVerticesCount(),
			md.getFaceIndices1(), md.getFaceIndices2(), md.getFaceIndices3(), md.getFaceTransparencies(), md.getFaceCount());
	}

	/** How far the body reaches to either side around the seat. */
	private static float halfWidthAt(ModelData md, SeatFinder.Seat s)
	{
		float[] xs = md.getVerticesX();
		float[] zs = md.getVerticesZ();
		float seatZ = s.wa * zs[s.a] + s.wb * zs[s.b] + s.wc * zs[s.c];
		float seatX = s.wa * xs[s.a] + s.wb * xs[s.b] + s.wc * xs[s.c];
		float half = 0;
		for (int v = 0; v < md.getVerticesCount(); v++)
		{
			if (Math.abs(zs[v] - seatZ) < 12)
			{
				half = Math.max(half, Math.abs(xs[v] - seatX));
			}
		}
		return half;
	}

	private static float maxY(ModelData md)
	{
		float[] ys = md.getVerticesY();
		float max = Float.NEGATIVE_INFINITY;
		for (int i = 0; i < md.getVerticesCount(); i++)
		{
			max = Math.max(max, ys[i]);
		}
		return max == Float.NEGATIVE_INFINITY ? 0 : max;
	}

	private static float minY(ModelData md)
	{
		float[] ys = md.getVerticesY();
		float min = 0;
		for (int i = 0; i < md.getVerticesCount(); i++)
		{
			min = Math.min(min, ys[i]);
		}
		return min;
	}
}
