package com.petmounts;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.IndexDataBase;
import net.runelite.api.JagexColor;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.SpotanimID;

/**
 * The climb-on sparkle and the poofs, built from the game's own graphics and drawn in the world
 * like any spell or teleport effect. Each graphic is recoloured to the pet's colours, keeping the
 * original light and shade so it still looks like the game's own art.
 */
@Slf4j
@Singleton
class MountEffects
{
	/** Sparkles around the player while climbing on. */
	private static final int WINDUP_GRAPHIC = SpotanimID.LEVELUP_ANIM;
	/** Poof when the mount appears or is sent away. */
	private static final int POOF_GRAPHIC = SpotanimID.SMOKEPUFF_LARGE;
	/** Small puff when hopping off for an action and climbing back on. */
	private static final int PUFF_GRAPHIC = SpotanimID.SMALL_SMOKEPUFF;

	/** Cache archive holding graphic (spotanim) definitions. */
	private static final int SPOTANIM_ARCHIVE = 13;

	private static final int AMBIENT = 64;
	private static final int CONTRAST = 850;

	private final Client client;
	private final Map<Integer, Graphic> graphics = new HashMap<>();
	private final List<RuneLiteObject> active = new ArrayList<>();
	private RuneLiteObject windup;

	@Inject
	MountEffects(Client client)
	{
		this.client = client;
	}

	void windup(Color color)
	{
		endWindup();
		windup = spawn(WINDUP_GRAPHIC, color, 0, true);
	}

	void endWindup()
	{
		if (windup != null)
		{
			windup.setActive(false);
			active.remove(windup);
			windup = null;
		}
	}

	void poof(Color color, int height)
	{
		endWindup();
		spawn(POOF_GRAPHIC, color, height, false);
	}

	void puff(Color color, int height)
	{
		spawn(PUFF_GRAPHIC, color, height, false);
	}

	/** Keeps the climb-on sparkle on the player and forgets finished effects. Call every client tick. */
	void tick()
	{
		for (Iterator<RuneLiteObject> it = active.iterator(); it.hasNext(); )
		{
			if (!it.next().isActive())
			{
				it.remove();
			}
		}

		Player me = client.getLocalPlayer();
		if (windup != null && me != null)
		{
			place(windup, me, 0);
		}
	}

	void clear()
	{
		for (RuneLiteObject o : active)
		{
			o.setActive(false);
		}
		active.clear();
		windup = null;
	}

	// ------------------------------------------------------------------

	private RuneLiteObject spawn(int graphicId, Color color, int height, boolean loop)
	{
		Player me = client.getLocalPlayer();
		if (me == null)
		{
			return null;
		}
		RuneLiteObject obj = create(graphicId, color, (short) 0, 128, loop);
		if (obj == null)
		{
			return null;
		}
		place(obj, me, height);
		obj.setActive(true);
		active.add(obj);
		return obj;
	}

	/**
	 * Plays a graphic once at a spot in the world, e.g. a flame under a fiery mount.
	 *
	 * @param recolorTo if not 0, every face is recoloured to this colour (keeping its lightness)
	 * @param scale size, 128 = as the game draws it
	 */
	RuneLiteObject spawnAt(int graphicId, short recolorTo, LocalPoint lp, int plane, int height, int orientation,
		int scale)
	{
		if (lp == null || active.size() >= MAX_ACTIVE)
		{
			return null;
		}
		RuneLiteObject obj = create(graphicId, null, recolorTo, scale, false);
		if (obj == null)
		{
			return null;
		}
		obj.setLocation(lp, plane);
		obj.setZ(Perspective.getTileHeight(client, lp, plane) - height);
		obj.setOrientation(orientation);
		obj.setActive(true);
		active.add(obj);
		return obj;
	}

	/** Effects on screen at once, at most: plenty for trails, and keeps a crowd of fiery mounts cheap. */
	private static final int MAX_ACTIVE = 40;

	private RuneLiteObject create(int graphicId, Color color, short recolorTo, int scale, boolean loop)
	{
		Graphic g = graphic(graphicId);
		if (g == null)
		{
			return null;
		}

		ModelData md = client.loadModelData(g.model);
		if (md == null)
		{
			return null;
		}
		md = md.shallowCopy().cloneColors();
		if (g.recolorFind != null)
		{
			for (int i = 0; i < Math.min(g.recolorFind.length, g.recolorReplace.length); i++)
			{
				md.recolor(g.recolorFind[i], g.recolorReplace[i]);
			}
		}
		if (g.retextureFind != null)
		{
			md = md.cloneTextures();
			for (int i = 0; i < Math.min(g.retextureFind.length, g.retextureReplace.length); i++)
			{
				md.retexture(g.retextureFind[i], g.retextureReplace[i]);
			}
		}
		for (int i = 0; i < Math.floorMod(g.rotation, 360) / 90; i++)
		{
			md = md.cloneVertices().rotateY90Ccw();
		}
		int sx = g.resizeX * scale / 128, sy = g.resizeY * scale / 128;
		if (sx != 128 || sy != 128)
		{
			md = md.cloneVertices().scale(Math.max(1, sx), Math.max(1, sy), Math.max(1, sx));
		}
		tint(md, color);
		if (recolorTo != 0)
		{
			recolor(md, recolorTo);
		}

		Model model = md.light(AMBIENT + g.ambient, CONTRAST + g.contrast, -30, -50, -30);
		if (model == null)
		{
			return null;
		}

		Animation anim = g.animation >= 0 ? client.loadAnimation(g.animation) : null;
		if (anim == null && !loop)
		{
			return null; // a one-shot needs its animation to know when to end
		}

		RuneLiteObject obj = client.createRuneLiteObject();
		obj.setModel(model);
		// Looping effects stay until removed; one-shots switch themselves off when their animation ends.
		obj.setShouldLoop(loop);
		obj.setAnimation(anim);
		return obj;
	}

	/** Recolours every face to this colour's hue and saturation, keeping each face's own lightness. */
	private static void recolor(ModelData md, short target)
	{
		short[] colors = md.getFaceColors();
		if (colors == null)
		{
			return;
		}
		int hue = JagexColor.unpackHue(target), sat = JagexColor.unpackSaturation(target);
		int lum = JagexColor.unpackLuminance(target);
		for (int i = 0; i < colors.length; i++)
		{
			int own = JagexColor.unpackLuminance(colors[i]);
			colors[i] = JagexColor.packHSL(hue, sat, Math.min(127, (own + lum) / 2));
		}
	}

	private void place(RuneLiteObject obj, Player me, int height)
	{
		LocalPoint lp = me.getLocalLocation();
		int plane = me.getWorldView().getPlane();
		obj.setLocation(lp, plane);
		obj.setZ(Perspective.getTileHeight(client, lp, plane) - height);
		obj.setOrientation(me.getCurrentOrientation());
	}

	/**
	 * Recolours every face to the given colour's hue and saturation while keeping each face's
	 * own lightness, so the graphic keeps its shading and just changes colour.
	 */
	static void tint(ModelData md, Color color)
	{
		if (color == null)
		{
			return;
		}
		short target = JagexColor.rgbToHSL(color.getRGB(), 1.0);
		int hue = JagexColor.unpackHue(target);
		int sat = JagexColor.unpackSaturation(target); // grey pets give grey smoke
		short[] colors = md.getFaceColors();
		if (colors == null)
		{
			return;
		}
		for (int i = 0; i < colors.length; i++)
		{
			int lum = JagexColor.unpackLuminance(colors[i]);
			colors[i] = JagexColor.packHSL(hue, sat, lum);
		}
	}

	// ------------------------------------------------------------------
	// Reading graphic definitions from the game cache
	// ------------------------------------------------------------------

	private Graphic graphic(int id)
	{
		Graphic cached = graphics.get(id);
		if (cached != null)
		{
			return cached;
		}
		IndexDataBase configs = client.getIndexConfig();
		byte[] data = configs == null ? null : configs.loadData(SPOTANIM_ARCHIVE, id);
		if (data == null)
		{
			return null;
		}
		try
		{
			Graphic g = Graphic.decode(data);
			if (g != null)
			{
				graphics.put(id, g);
			}
			return g;
		}
		catch (RuntimeException e)
		{
			log.debug("Couldn't read graphic {}", id, e);
			return null;
		}
	}

	/**
	 * A graphic (spotanim) definition. The opcode layout follows the game's cache format; the
	 * handling of opcode 10 (a flag with no data) is from Rapid Mounts by RapidUrsa (BSD 2-Clause).
	 */
	static final class Graphic
	{
		int model;
		int animation = -1;
		int resizeX = 128;
		int resizeY = 128;
		int rotation;
		int ambient;
		int contrast;
		short[] recolorFind;
		short[] recolorReplace;
		short[] retextureFind;
		short[] retextureReplace;

		static Graphic decode(byte[] data)
		{
			Reader in = new Reader(data);
			Graphic g = new Graphic();
			for (int op = in.u8(); op != 0; op = in.u8())
			{
				switch (op)
				{
					case 1:
						g.model = in.u16();
						break;
					case 2:
						g.animation = in.u16();
						break;
					case 3:
						g.model = in.i32();
						break;
					case 4:
						g.resizeX = in.u16();
						break;
					case 5:
						g.resizeY = in.u16();
						break;
					case 6:
						g.rotation = in.u16();
						break;
					case 7:
						g.ambient = in.u8();
						break;
					case 8:
						g.contrast = in.u8();
						break;
					case 9:
						in.skipString();
						break;
					case 10:
						break;
					case 40:
					{
						int n = in.u8();
						g.recolorFind = new short[n];
						g.recolorReplace = new short[n];
						for (int i = 0; i < n; i++)
						{
							g.recolorFind[i] = (short) in.u16();
							g.recolorReplace[i] = (short) in.u16();
						}
						break;
					}
					case 41:
					{
						int n = in.u8();
						g.retextureFind = new short[n];
						g.retextureReplace = new short[n];
						for (int i = 0; i < n; i++)
						{
							g.retextureFind[i] = (short) in.u16();
							g.retextureReplace[i] = (short) in.u16();
						}
						break;
					}
					default:
						throw new IllegalStateException("unknown graphic opcode " + op);
				}
			}
			return g.model > 0 ? g : null;
		}
	}

	private static final class Reader
	{
		private final byte[] data;
		private int pos;

		Reader(byte[] data)
		{
			this.data = data;
		}

		int u8()
		{
			return data[pos++] & 0xFF;
		}

		int u16()
		{
			return (u8() << 8) | u8();
		}

		int i32()
		{
			return (u16() << 16) | u16();
		}

		void skipString()
		{
			while (data[pos++] != 0)
			{
				// skip to the terminating zero
			}
		}
	}
}
