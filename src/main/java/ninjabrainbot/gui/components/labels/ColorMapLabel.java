package ninjabrainbot.gui.components.labels;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.Box;
import javax.swing.JPanel;

import ninjabrainbot.gui.style.StyleManager;
import ninjabrainbot.gui.style.theme.WrappedColor;

public class ColorMapLabel extends JPanel implements ILabel {

	private final boolean textFirst;
	public final ThemedLabel textLabel;
	public final ColoredLabel coloredLabel;

	public ColorMapLabel(StyleManager styleManager, boolean textFirst) {
		this(styleManager, textFirst, false);
	}

	public ColorMapLabel(StyleManager styleManager, boolean textFirst, boolean centered) {
		this.textFirst = textFirst;
		textLabel = new ThemedLabel(styleManager, "");
		coloredLabel = new ColoredLabel(styleManager);
		setLayout(new GridBagLayout());
		setOpaque(false);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = GridBagConstraints.RELATIVE;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.NORTH;
		gbc.fill = GridBagConstraints.VERTICAL;
		gbc.weightx = 0;
		if (textFirst) {
			add(textLabel, gbc);
			add(coloredLabel, gbc);
		} else {
			add(coloredLabel, gbc);
			add(textLabel, gbc);
		}
		gbc.weightx = 1;
		add(Box.createGlue(), gbc);
		setAlignmentX(0);
	}

	public void setForegroundColor(WrappedColor color) {
		textLabel.setForegroundColor(color);
	}

	/** Moves the glue so the text can sit left, centred or right inside the cell. */
	public void setAlignment(int swingConstant) {
		removeAll();
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.gridx = GridBagConstraints.RELATIVE;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.NORTH;
		gbc.fill = GridBagConstraints.VERTICAL;
		if (swingConstant != javax.swing.SwingConstants.LEFT) {
			gbc.weightx = 1;
			add(Box.createGlue(), gbc);
		}
		gbc.weightx = 0;
		if (textFirst) {
			add(textLabel, gbc);
			add(coloredLabel, gbc);
		} else {
			add(coloredLabel, gbc);
			add(textLabel, gbc);
		}
		if (swingConstant != javax.swing.SwingConstants.RIGHT) {
			gbc.weightx = 1;
			add(Box.createGlue(), gbc);
		}
		revalidate();
	}

	public void setColorMap(ninjabrainbot.util.Wrapper<ninjabrainbot.gui.style.theme.ColorMap> colorMap) {
		coloredLabel.setColorMap(colorMap);
	}

	public void setTextSizeScale(float textSizeScale) {
		textLabel.setTextSizeScale(textSizeScale);
		coloredLabel.setTextSizeScale(textSizeScale);
	}

	public void setColoredText(String text, float color) {
		coloredLabel.setText(text, color);
	}

	public void setText(String text) {
		textLabel.setText(text);
	}

	public void updateColor() {
		coloredLabel.updateColors();
	}

	public void clear() {
		textLabel.setText("");
		coloredLabel.setText("");
	}

}