package ninjabrainbot.gui.components.labels;

import java.awt.Color;

import ninjabrainbot.gui.style.StyleManager;
import ninjabrainbot.gui.style.theme.ColorMap;
import ninjabrainbot.util.Wrapper;

public class ColoredLabel extends ThemedLabel {

	private Wrapper<ColorMap> colorMap;

	private double lastColor = 0.0;

	public ColoredLabel(StyleManager styleManager) {
		this(styleManager, false);
	}

	public ColoredLabel(StyleManager styleManager, boolean centered) {
		super(styleManager, centered);
		colorMap = styleManager.currentTheme.CERTAINTY_COLOR_MAP;
	}

	/** Lets a label use a different gradient than the certainty one, e.g. the angle gradient. */
	public void setColorMap(Wrapper<ColorMap> colorMap) {
		this.colorMap = colorMap;
	}

	public void setText(String text, float color) {
		lastColor = color;
		setText(text);
		updateColors();
	}

	@Override
	public Color getForegroundColor() {
		return colorMap.get().get(lastColor);
	}

}
