package com.petmounts;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The Mount Stable: a sidebar panel showing the pet following you, whether it can be ridden, a ride button,
 * the saddle, reins, motion and hide-items switches, and adjustments saved for this pet.
 *
 * Swing code only; every game action goes back to the plugin, which runs it on the client thread.
 */
class MountStablePanel extends PluginPanel
{
	interface Actions
	{
		void toggleRide();

		void saveTweaks(PetTweaks tweaks);

		void setOption(String key, boolean value);
	}

	/** What the panel shows; built by the plugin on the client thread. */
	static final class State
	{
		final String petName;
		final String status;
		final boolean canRide;
		final boolean riding;
		final PetTweaks tweaks;
		final boolean saddle;
		final boolean reins;
		final boolean motion;
		final boolean hideHeld;
		final boolean hideCape;

		State(String petName, String status, boolean canRide, boolean riding, PetTweaks tweaks,
			boolean saddle, boolean reins, boolean motion, boolean hideHeld, boolean hideCape)
		{
			this.petName = petName;
			this.status = status;
			this.canRide = canRide;
			this.riding = riding;
			this.tweaks = tweaks;
			this.saddle = saddle;
			this.reins = reins;
			this.motion = motion;
			this.hideHeld = hideHeld;
			this.hideCape = hideCape;
		}

		boolean sameAs(State o)
		{
			return o != null && java.util.Objects.equals(petName, o.petName) && status.equals(o.status)
				&& canRide == o.canRide && riding == o.riding && saddle == o.saddle && reins == o.reins && motion == o.motion && hideHeld == o.hideHeld
				&& hideCape == o.hideCape && tweaks.size == o.tweaks.size && tweaks.seatHeight == o.tweaks.seatHeight
				&& tweaks.seatForward == o.tweaks.seatForward && tweaks.pose == o.tweaks.pose;
		}
	}

	private static final Color READY = new Color(110, 225, 110);

	private final Actions actions;
	private final JLabel petLabel = new JLabel();
	private final JLabel statusLabel = new JLabel();
	private final JButton rideButton = new JButton();
	private final JCheckBox saddleBox = new JCheckBox("Saddle and blanket");
	private final JCheckBox reinsBox = new JCheckBox("Reins");
	private final JCheckBox motionBox = new JCheckBox("Natural riding motion");
	private final JCheckBox heldBox = new JCheckBox("Hide weapon and shield");
	private final JCheckBox capeBox = new JCheckBox("Hide cape");
	private final JComboBox<RiderPose> poseBox = new JComboBox<>(RiderPose.values());
	private final JSlider sizeSlider = slider(60, 160, 100);
	private final JSlider heightSlider = slider(-40, 40, 0);
	private final JSlider forwardSlider = slider(-60, 60, 0);
	private final JPanel tweakPanel = new JPanel();
	private final JLabel tweakTitle = new JLabel();

	private boolean updating;
	private State shown;

	MountStablePanel(Actions actions)
	{
		this.actions = actions;
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

		JLabel title = new JLabel("Mount Stable");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		content.add(left(title));
		content.add(Box.createRigidArea(new Dimension(0, 8)));

		// The pet following you and whether it can be ridden.
		JPanel card = new JPanel(new GridLayout(2, 1, 0, 2));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new EmptyBorder(8, 8, 8, 8));
		petLabel.setFont(FontManager.getRunescapeBoldFont());
		petLabel.setForeground(Color.WHITE);
		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		card.add(petLabel);
		card.add(statusLabel);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
		content.add(left(card));
		content.add(Box.createRigidArea(new Dimension(0, 8)));

		rideButton.setFont(FontManager.getRunescapeBoldFont());
		rideButton.setFocusPainted(false);
		rideButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		rideButton.addActionListener(e -> actions.toggleRide());
		content.add(left(rideButton));
		content.add(Box.createRigidArea(new Dimension(0, 12)));

		// Switches shared by every pet.
		checkbox(saddleBox, "showSaddle");
		checkbox(reinsBox, "showReins");
		checkbox(motionBox, "naturalMotion");
		checkbox(heldBox, "hideHeldItems");
		checkbox(capeBox, "hideCape");
		content.add(left(saddleBox));
		content.add(left(reinsBox));
		content.add(left(motionBox));
		content.add(left(heldBox));
		content.add(left(capeBox));
		content.add(Box.createRigidArea(new Dimension(0, 12)));

		// Adjustments remembered for this pet.
		tweakPanel.setLayout(new BoxLayout(tweakPanel, BoxLayout.Y_AXIS));
		tweakPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		tweakPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		tweakTitle.setFont(FontManager.getRunescapeSmallFont());
		tweakTitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		tweakPanel.add(left(tweakTitle));
		tweakPanel.add(Box.createRigidArea(new Dimension(0, 6)));
		tweakPanel.add(left(small("Riding pose")));
		poseBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		poseBox.addActionListener(e -> tweaksChanged());
		tweakPanel.add(left(poseBox));
		tweakPanel.add(labelled("Size (%)", sizeSlider));
		tweakPanel.add(labelled("Seat height", heightSlider));
		tweakPanel.add(labelled("Seat forward / back", forwardSlider));
		JButton reset = new JButton("Reset this pet");
		reset.setFocusPainted(false);
		reset.addActionListener(e -> actions.saveTweaks(PetTweaks.NONE));
		tweakPanel.add(Box.createRigidArea(new Dimension(0, 6)));
		tweakPanel.add(left(reset));
		content.add(left(tweakPanel));

		content.add(Box.createRigidArea(new Dimension(0, 10)));
		JLabel tip = new JLabel("<html>Right-click your pet and choose <b>Ride</b>, or use the hotkey (Alt + M by default)."
			+ " Right-click anywhere and choose <b>Dismount</b> to get off.</html>");
		tip.setFont(FontManager.getRunescapeSmallFont());
		tip.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		content.add(left(tip));

		add(content, BorderLayout.NORTH);
		show(new State(null, "Summon one of your pets to ride it.", false, false, PetTweaks.NONE, true, true, true, true, true));
	}

	/** Shows the latest state. Call on the Swing thread. */
	void show(State s)
	{
		if (s.sameAs(shown))
		{
			return;
		}
		shown = s;
		updating = true;
		petLabel.setText(s.petName == null ? "No pet following you" : s.petName);
		statusLabel.setText(s.status);
		statusLabel.setForeground(s.riding || s.canRide ? READY : ColorScheme.LIGHT_GRAY_COLOR);
		rideButton.setText(s.riding ? "Dismount" : "Ride");
		rideButton.setEnabled(s.riding || s.canRide);
		saddleBox.setSelected(s.saddle);
		reinsBox.setSelected(s.reins);
		motionBox.setSelected(s.motion);
		heldBox.setSelected(s.hideHeld);
		capeBox.setSelected(s.hideCape);
		tweakTitle.setText(s.petName == null ? "Adjustments for your pet" : "Adjustments for " + s.petName);
		poseBox.setSelectedItem(s.tweaks.pose);
		sizeSlider.setValue(s.tweaks.size);
		heightSlider.setValue(s.tweaks.seatHeight);
		forwardSlider.setValue(s.tweaks.seatForward);
		setEnabledDeep(tweakPanel, s.petName != null && s.canRide);
		updating = false;
	}

	private void tweaksChanged()
	{
		if (updating)
		{
			return;
		}
		RiderPose pose = (RiderPose) poseBox.getSelectedItem();
		actions.saveTweaks(new PetTweaks(sizeSlider.getValue(), heightSlider.getValue(), forwardSlider.getValue(), pose));
	}

	private void checkbox(JCheckBox box, String key)
	{
		box.setFocusPainted(false);
		box.addActionListener(e ->
		{
			if (!updating)
			{
				actions.setOption(key, box.isSelected());
			}
		});
	}

	private JSlider slider(int min, int max, int value)
	{
		JSlider s = new JSlider(min, max, value);
		s.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		// Save when the player lets go, not on every step of the drag.
		s.addChangeListener(e ->
		{
			if (!s.getValueIsAdjusting())
			{
				tweaksChanged();
			}
		});
		return s;
	}

	private JPanel labelled(String text, JSlider s)
	{
		JPanel p = new JPanel(new BorderLayout());
		p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		JLabel value = new JLabel(String.valueOf(s.getValue()));
		value.setFont(FontManager.getRunescapeSmallFont());
		s.addChangeListener(e -> value.setText(String.valueOf(s.getValue())));
		JPanel head = new JPanel(new BorderLayout());
		head.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		head.add(small(text), BorderLayout.WEST);
		head.add(value, BorderLayout.EAST);
		p.add(head, BorderLayout.NORTH);
		p.add(s, BorderLayout.CENTER);
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
		return p;
	}

	private static JLabel small(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		return l;
	}

	private static <T extends Component> T left(T c)
	{
		if (c instanceof javax.swing.JComponent)
		{
			((javax.swing.JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
		}
		return c;
	}

	private static void setEnabledDeep(Component c, boolean enabled)
	{
		c.setEnabled(enabled);
		if (c instanceof java.awt.Container)
		{
			for (Component child : ((java.awt.Container) c).getComponents())
			{
				setEnabledDeep(child, enabled);
			}
		}
	}

	/** Convenience for the plugin: run an action with the panel's state. */
	static Consumer<State> onSwing(MountStablePanel panel)
	{
		return s -> javax.swing.SwingUtilities.invokeLater(() -> panel.show(s));
	}
}
