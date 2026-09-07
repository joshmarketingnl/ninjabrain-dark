package ninjabrainbot.gui.components.panels;

import java.awt.Color;
import java.awt.Graphics;

import javax.swing.JPanel;

import ninjabrainbot.gui.components.ThemedComponent;
import ninjabrainbot.gui.style.SizePreference;
import ninjabrainbot.gui.style.StyleManager;
import ninjabrainbot.gui.style.Translucency;
import ninjabrainbot.gui.style.theme.WrappedColor;

public class ThemedPanel extends JPanel implements ThemedComponent {

	public final boolean bold;

	private WrappedColor bgCol;
	private WrappedColor fgCol;

	public ThemedPanel(StyleManager styleManager) {
		this(styleManager, false);
	}

	public ThemedPanel(StyleManager styleManager, boolean bold) {
		super();
		styleManager.registerThemedComponent(this);
		this.bold = bold;

		bgCol = styleManager.currentTheme.COLOR_NEUTRAL;
		fgCol = styleManager.currentTheme.TEXT_COLOR_NEUTRAL;
	}

	public void updateSize(StyleManager styleManager) {
		setFont(styleManager.fontSize(getTextSize(styleManager.size), !bold));
	}

	@Override
	public void updateColors() {
		Color bg = getBackgroundColor();
		boolean translucent = Translucency.appliesTo(this);
		setOpaque(!translucent);
		setBackground(translucent ? Translucency.apply(bg, this) : bg);
		Color fg = getForegroundColor();
		setForeground(fg);
	}

	/** Swing only fills the background of opaque components, so a see-through panel paints its own. */
	@Override
	protected void paintComponent(Graphics g) {
		if (!isOpaque()) {
			Color bg = getBackground();
			if (bg != null) {
				g.setColor(Translucency.isSuspended() ? Translucency.opaque(bg) : bg);
				g.fillRect(0, 0, getWidth(), getHeight());
			}
		}
		super.paintComponent(g);
	}

	public int getTextSize(SizePreference p) {
		return p.TEXT_SIZE_MEDIUM;
	}

	public void setBackgroundColor(WrappedColor color) {
		bgCol = color;
	}

	public void setForegroundColor(WrappedColor color) {
		fgCol = color;
	}

	protected Color getBackgroundColor() {
		return bgCol.color();
	}

	protected Color getForegroundColor() {
		return fgCol.color();
	}

}
