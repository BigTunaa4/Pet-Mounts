package com.petmounts;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.Paint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws the mount-up effects around the local player: a glowing summoning circle and orbiting sparkles
 * while climbing on, then a coloured poof of smoke and sparkles when the mount appears or vanishes.
 * All colours come from the palette of the pet being ridden.
 */
class MountEffectsOverlay extends Overlay
{
	private static final int POOF_MS = 750;

	private final Client client;
	private final List<Effect> effects = new ArrayList<>();
	private final Random random = new Random();

	@Inject
	MountEffectsOverlay(Client client)
	{
		this.client = client;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(PRIORITY_HIGH);
	}

	/** Swirling sparkles and a ground circle while the player climbs on. */
	void windup(Color[] palette, int durationMs, int seatHeight)
	{
		synchronized (effects)
		{
			effects.removeIf(e -> e.windup);
			effects.add(new Effect(true, durationMs, palette, seatHeight, random.nextLong()));
		}
	}

	/** Burst of coloured smoke and sparkles, centred at the given height above the ground. */
	void poof(Color[] palette, int height)
	{
		synchronized (effects)
		{
			effects.removeIf(e -> e.windup);
			effects.add(new Effect(false, POOF_MS, palette, height, random.nextLong()));
		}
	}

	void cancelWindup()
	{
		synchronized (effects)
		{
			effects.removeIf(e -> e.windup);
		}
	}

	void clear()
	{
		synchronized (effects)
		{
			effects.clear();
		}
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Player me = client.getLocalPlayer();
		if (me == null)
		{
			return null;
		}
		LocalPoint lp = me.getLocalLocation();
		int plane = me.getWorldView().getPlane();

		List<Effect> active;
		synchronized (effects)
		{
			long now = System.nanoTime();
			for (Iterator<Effect> it = effects.iterator(); it.hasNext(); )
			{
				if (it.next().progress(now) >= 1f)
				{
					it.remove();
				}
			}
			if (effects.isEmpty())
			{
				return null;
			}
			active = new ArrayList<>(effects);
		}

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		Composite oldComposite = g.getComposite();
		Paint oldPaint = g.getPaint();

		long now = System.nanoTime();
		Projector proj = new SceneProjector(lp, plane);
		for (Effect e : active)
		{
			float t = e.progress(now);
			if (e.windup)
			{
				drawWindup(g, proj, e, t);
			}
			else
			{
				drawPoof(g, proj, e, t);
			}
		}

		g.setComposite(oldComposite);
		g.setPaint(oldPaint);
		return null;
	}

	// ------------------------------------------------------------------
	// Climbing on
	// ------------------------------------------------------------------

	static void drawWindup(Graphics2D g, Projector proj, Effect e, float t)
	{
		float fadeIn = Math.min(1f, t * 4f);
		double spin = t * Math.PI * 3.0;

		// Glowing circle on the ground that grows in, with a dashed rune ring spinning inside it.
		int radius = Math.round(40 + 30 * easeOut(Math.min(1f, t * 2f)));
		Color ring = e.palette[0];
		g.setStroke(new BasicStroke(3f));
		drawGroundCircle(g, proj, radius, 0, 1f, withAlpha(ring, 0.75f * fadeIn));
		g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{6f, 8f}, 0f));
		drawGroundCircle(g, proj, Math.round(radius * 0.72f), spin, 1f, withAlpha(e.palette[1], 0.8f * fadeIn));

		// Sparkles orbit the player, spiralling up and in toward the seat.
		int count = 14;
		for (int i = 0; i < count; i++)
		{
			double angle = spin * 1.6 + i * (Math.PI * 2 / count);
			float r = 55 - 30 * t;
			float h = 10 + (e.height + 20) * ((i % 3) / 3f * 0.5f + t * 0.8f);
			Point2D p = proj.at((float) Math.cos(angle) * r, (float) Math.sin(angle) * r, h);
			if (p == null)
			{
				continue;
			}
			float twinkle = 0.55f + 0.45f * (float) Math.sin(t * 30 + i * 1.7);
			float size = proj.pixels(7 + 4 * twinkle);
			drawSparkle(g, p, size, withAlpha(e.palette[i % e.palette.length], fadeIn * twinkle));
		}

		// Soft glow at the seat that brightens as the mount is about to appear.
		Point2D seat = proj.at(0, 0, e.height);
		if (seat != null)
		{
			drawGlow(g, seat, proj.pixels(30 + 25 * t), withAlpha(e.palette[2], 0.35f * t));
		}
	}

	// ------------------------------------------------------------------
	// Poof
	// ------------------------------------------------------------------

	static void drawPoof(Graphics2D g, Projector proj, Effect e, float t)
	{
		Random r = new Random(e.seed);
		float fade = 1f - t;
		float spread = easeOut(t);

		// Bright flash at the start.
		Point2D centre = proj.at(0, 0, e.height * 0.6f);
		if (centre != null && t < 0.35f)
		{
			float ft = t / 0.35f;
			drawGlow(g, centre, proj.pixels(20 + 70 * ft), withAlpha(Color.WHITE, 0.8f * (1f - ft)));
		}

		// Smoke puffs drifting outward and up.
		int puffs = 16;
		for (int i = 0; i < puffs; i++)
		{
			double angle = i * (Math.PI * 2 / puffs) + r.nextDouble() * 0.4;
			float dist = (35 + r.nextFloat() * 55) * spread;
			float rise = (r.nextFloat() * e.height * 1.1f) + 25 * spread;
			float size = 20 + 26 * spread + r.nextFloat() * 10;
			Point2D p = proj.at((float) Math.cos(angle) * dist, (float) Math.sin(angle) * dist, rise);
			if (p == null)
			{
				continue;
			}
			Color c = e.palette[i % e.palette.length];
			drawGlow(g, p, proj.pixels(size), withAlpha(lighten(c, 0.35f), 0.85f * fade));
		}

		// Sparkles shooting outward past the smoke.
		int sparkles = 12;
		for (int i = 0; i < sparkles; i++)
		{
			double angle = r.nextDouble() * Math.PI * 2;
			float dist = (60 + r.nextFloat() * 80) * spread;
			float rise = e.height * 0.4f + (r.nextFloat() - 0.3f) * 90 * spread;
			Point2D p = proj.at((float) Math.cos(angle) * dist, (float) Math.sin(angle) * dist, rise);
			if (p == null)
			{
				continue;
			}
			drawSparkle(g, p, proj.pixels(10 * fade + 3), withAlpha(lighten(e.palette[i % e.palette.length], 0.5f), fade));
		}
	}

	// ------------------------------------------------------------------
	// Drawing helpers
	// ------------------------------------------------------------------

	private static void drawGroundCircle(Graphics2D g, Projector proj, int radius, double rotation, float heightScale, Color color)
	{
		Path2D path = new Path2D.Float();
		boolean started = false;
		int steps = 32;
		for (int i = 0; i <= steps; i++)
		{
			double a = rotation + i * (Math.PI * 2 / steps);
			Point2D p = proj.at((float) Math.cos(a) * radius, (float) Math.sin(a) * radius, 2 * heightScale);
			if (p == null)
			{
				started = false;
				continue;
			}
			if (!started)
			{
				path.moveTo(p.getX(), p.getY());
				started = true;
			}
			else
			{
				path.lineTo(p.getX(), p.getY());
			}
		}
		g.setComposite(AlphaComposite.SrcOver);
		g.setColor(color);
		g.draw(path);
	}

	private static void drawGlow(Graphics2D g, Point2D p, float radius, Color color)
	{
		if (radius < 1f || color.getAlpha() == 0)
		{
			return;
		}
		Color clear = new Color(color.getRed(), color.getGreen(), color.getBlue(), 0);
		g.setComposite(AlphaComposite.SrcOver);
		g.setPaint(new RadialGradientPaint(p, radius, new float[]{0f, 0.55f, 1f},
			new Color[]{color, withAlpha(color, color.getAlpha() / 255f * 0.6f), clear},
			MultipleGradientPaint.CycleMethod.NO_CYCLE));
		g.fillOval(Math.round((float) p.getX() - radius), Math.round((float) p.getY() - radius),
			Math.round(radius * 2), Math.round(radius * 2));
	}

	/** Four-pointed star with a small glow. */
	private static void drawSparkle(Graphics2D g, Point2D p, float size, Color color)
	{
		if (size < 1f || color.getAlpha() == 0)
		{
			return;
		}
		drawGlow(g, p, size * 1.2f, withAlpha(color, color.getAlpha() / 255f * 0.45f));

		float x = (float) p.getX();
		float y = (float) p.getY();
		float w = size * 0.28f;
		Path2D star = new Path2D.Float();
		star.moveTo(x, y - size);
		star.lineTo(x + w, y - w);
		star.lineTo(x + size, y);
		star.lineTo(x + w, y + w);
		star.lineTo(x, y + size);
		star.lineTo(x - w, y + w);
		star.lineTo(x - size, y);
		star.lineTo(x - w, y - w);
		star.closePath();
		g.setComposite(AlphaComposite.SrcOver);
		g.setColor(color);
		g.fill(star);
	}

	private static float easeOut(float t)
	{
		float inv = 1f - Math.max(0f, Math.min(1f, t));
		return 1f - inv * inv * inv;
	}

	static Color withAlpha(Color c, float alpha)
	{
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
	}

	static Color lighten(Color c, float amount)
	{
		return new Color(
			Math.round(c.getRed() + (255 - c.getRed()) * amount),
			Math.round(c.getGreen() + (255 - c.getGreen()) * amount),
			Math.round(c.getBlue() + (255 - c.getBlue()) * amount));
	}

	// ------------------------------------------------------------------

	static final class Effect
	{
		final boolean windup;
		final long start = System.nanoTime();
		final long durationNanos;
		final Color[] palette;
		final int height;
		final long seed;

		Effect(boolean windup, int durationMs, Color[] palette, int height, long seed)
		{
			this.windup = windup;
			this.durationNanos = durationMs * 1_000_000L;
			this.palette = palette;
			this.height = height;
			this.seed = seed;
		}

		float progress(long now)
		{
			return (now - start) / (float) durationNanos;
		}
	}

	/** Projects points around the player (x/y in local units, h = height above ground) to the screen. */
	interface Projector
	{
		Point2D at(float dx, float dy, float h);

		float pixels(float units);
	}

	private final class SceneProjector implements Projector
	{
		private final LocalPoint origin;
		private final int plane;
		private final float pixelsPerUnit;

		SceneProjector(LocalPoint origin, int plane)
		{
			this.origin = origin;
			this.plane = plane;
			Point a = Perspective.localToCanvas(client, origin, plane, 0);
			Point b = Perspective.localToCanvas(client, origin, plane, 100);
			this.pixelsPerUnit = a != null && b != null
				? (float) Math.hypot(a.getX() - b.getX(), a.getY() - b.getY()) / 100f
				: 0.5f;
		}

		@Override
		public Point2D at(float dx, float dy, float h)
		{
			LocalPoint lp = new LocalPoint(origin.getX() + Math.round(dx), origin.getY() + Math.round(dy), origin.getWorldView());
			Point p = Perspective.localToCanvas(client, lp, plane, Math.round(h));
			return p == null ? null : new Point2D.Float(p.getX(), p.getY());
		}

		@Override
		public float pixels(float units)
		{
			return units * pixelsPerUnit;
		}
	}
}
