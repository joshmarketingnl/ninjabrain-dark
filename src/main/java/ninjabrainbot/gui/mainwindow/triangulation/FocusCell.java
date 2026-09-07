package ninjabrainbot.gui.mainwindow.triangulation;

import java.awt.BorderLayout;


import ninjabrainbot.event.IDisposable;
import ninjabrainbot.gui.components.labels.ColorMapLabel;
import ninjabrainbot.gui.components.labels.ColoredLabel;
import ninjabrainbot.gui.components.labels.ILabel;
import ninjabrainbot.gui.components.labels.ThemedLabel;
import ninjabrainbot.gui.components.panels.ThemedPanel;
import ninjabrainbot.gui.style.SizePreference;
import ninjabrainbot.gui.style.StyleManager;

/** One field of the focus view: a small caption and the value in large type. */
public class FocusCell extends ThemedPanel implements IDisposable {

	private final ThemedLabel captionLabel;
	private final javax.swing.JComponent value;
	private final ILabel valueLabel;

	private boolean horizontal = false;
	private int alignment = javax.swing.SwingConstants.CENTER;

	public FocusCell(StyleManager styleManager, String caption, ILabel value) {
		super(styleManager);
		this.value = (javax.swing.JComponent) value;
		this.valueLabel = value;
		captionLabel = new ThemedLabel(styleManager, caption, false, true) {
			@Override
			public int getTextSize(SizePreference p) {
				return p.TEXT_SIZE_SMALL;
			}
		};
		captionLabel.setForegroundColor(styleManager.currentTheme.TEXT_COLOR_NEUTRAL);
		setBackgroundColor(styleManager.currentTheme.COLOR_SLIGHTLY_WEAK);
		setAlignmentX(0);
		applyLayout();
	}

	public void setHorizontal(boolean horizontal) {
		this.horizontal = horizontal;
		applyLayout();
	}

	/** Left, centre or right, so neighbouring cells line up along the window edges. */
	public void setAlignment(int swingConstant) {
		this.alignment = swingConstant;
		applyAlignment();
	}

	/** Gives this cell its own background, so one value stands out from the rest. */
	public void setEmphasis(ninjabrainbot.gui.style.theme.WrappedColor color) {
		setBackgroundColor(color);
		updateColors();
	}

	private void applyAlignment() {
		if (valueLabel instanceof ColorMapLabel)
			((ColorMapLabel) valueLabel).setAlignment(alignment);
		else if (valueLabel instanceof ThemedLabel)
			((ThemedLabel) valueLabel).setHorizontalAlignment(alignment);
		captionLabel.setHorizontalAlignment(horizontal ? javax.swing.SwingConstants.LEFT : alignment);
	}

	private void applyLayout() {
		removeAll();
		setLayout(new BorderLayout());
		if (horizontal) {
			add(captionLabel, BorderLayout.WEST);
			add(value, BorderLayout.CENTER);
		} else {
			add(captionLabel, BorderLayout.NORTH);
			add(value, BorderLayout.CENTER);
		}
		applyAlignment();
		revalidate();
	}

	public void setCaption(String caption) {
		captionLabel.setText(caption);
	}

	public void setTextScale(float scale) {
		if (valueLabel instanceof ThemedLabel)
			((ThemedLabel) valueLabel).setTextSizeScale(scale);
		else if (valueLabel instanceof ColorMapLabel)
			((ColorMapLabel) valueLabel).setTextSizeScale(scale);
	}

	public void clear() {
		valueLabel.setText("");
		if (valueLabel instanceof ColorMapLabel)
			((ColorMapLabel) valueLabel).setColoredText("", 0);
		else if (valueLabel instanceof ColoredLabel)
			((ColoredLabel) valueLabel).setText("", 0);
	}

	@Override
	public void dispose() {
	}

}
