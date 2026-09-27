package com.petmounts;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.function.BooleanSupplier;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * A small on-screen button to hop on and off. Click it to ride or dismount; hold Alt and drag to move it,
 * like any RuneLite overlay.
 */
class MountButtonOverlay extends Overlay
{
	private static final int SIZE = 34;
	private static final Color BACKGROUND = new Color(30, 30, 30, 190);
	private static final Color RIDING = new Color(110, 225, 110);
	private static final Color IDLE = new Color(120, 120, 120);

	private final BufferedImage icon;
	private final BooleanSupplier visible;
	private final BooleanSupplier riding;
	private final BooleanSupplier canRide;
	private final Runnable onClick;

	final MouseAdapter mouse = new MouseAdapter()
	{
		@Override
		public MouseEvent mouseClicked(MouseEvent e)
		{
			Rectangle b = getBounds();
			if (visible.getAsBoolean() && b != null && b.contains(e.getPoint()) && !e.isAltDown()
				&& e.getButton() == MouseEvent.BUTTON1)
			{
				onClick.run();
				e.consume();
			}
			return e;
		}

		@Override
		public MouseEvent mousePressed(MouseEvent e)
		{
			// Don't let a click on the button also walk your character.
			Rectangle b = getBounds();
			if (visible.getAsBoolean() && b != null && b.contains(e.getPoint()) && !e.isAltDown()
				&& e.getButton() == MouseEvent.BUTTON1)
			{
				e.consume();
			}
			return e;
		}
	};

	MountButtonOverlay(PetMountsPlugin plugin, BufferedImage icon, BooleanSupplier visible, BooleanSupplier riding,
		BooleanSupplier canRide, Runnable onClick)
	{
		super(plugin);
		this.icon = icon;
		this.visible = visible;
		this.riding = riding;
		this.canRide = canRide;
		this.onClick = onClick;
		setPosition(OverlayPosition.ABOVE_CHATBOX_RIGHT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setMovable(true);
		setResettable(true);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!visible.getAsBoolean())
		{
			return null;
		}
		boolean on = riding.getAsBoolean();
		boolean ready = on || canRide.getAsBoolean();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(BACKGROUND);
		g.fillRoundRect(0, 0, SIZE, SIZE, 8, 8);
		g.setStroke(new BasicStroke(on ? 2f : 1f));
		g.setColor(on ? RIDING : IDLE);
		g.drawRoundRect(0, 0, SIZE - 1, SIZE - 1, 8, 8);
		if (icon != null)
		{
			java.awt.Composite old = g.getComposite();
			if (!ready)
			{
				g.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, 0.4f));
			}
			int h = SIZE - 8;
			int w = Math.round(h * icon.getWidth() / (float) icon.getHeight());
			g.drawImage(icon, (SIZE - w) / 2, 4, w, h, null);
			g.setComposite(old);
		}
		return new Dimension(SIZE, SIZE);
	}
}
