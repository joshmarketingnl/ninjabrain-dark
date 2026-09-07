package ninjabrainbot.gui.mainwindow.triangulation;

import java.awt.Dimension;
import java.util.List;

import javax.swing.BoxLayout;

import ninjabrainbot.event.IDisposable;
import ninjabrainbot.gui.components.panels.ThemedPanel;
import ninjabrainbot.gui.style.StyleManager;
import ninjabrainbot.io.preferences.NinjabrainBotPreferences;
import ninjabrainbot.model.datastate.calculator.ICalculatorResult;
import ninjabrainbot.model.datastate.stronghold.ChunkPrediction;

/**
 * Shows only the most likely stronghold, in large type. For runs where the top prediction is the
 * only line that gets read, and the four runners-up are just noise.
 */
public class FocusTriangulationPanel extends ThemedPanel implements IDisposable {

	private final NinjabrainBotPreferences preferences;

	private final ChunkPanelHeader header;
	private final ChunkPanel panel;

	public FocusTriangulationPanel(StyleManager styleManager, NinjabrainBotPreferences preferences) {
		super(styleManager);
		this.preferences = preferences;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setAlignmentX(0);
		header = new ChunkPanelHeader(styleManager, preferences);
		add(header);
		panel = new ChunkPanel(styleManager, preferences);
		panel.setTextScale(textScale(preferences));
		add(panel);

		setBackgroundColor(styleManager.currentTheme.COLOR_NEUTRAL);
	}

	public void setResult(ICalculatorResult result) {
		header.updateHeaderText(preferences.strongholdDisplayType.get());
		if (result == null) {
			panel.setPrediction(null);
			return;
		}
		List<ChunkPrediction> predictions = result.getTopPredictions();
		panel.setPrediction(predictions.isEmpty() ? null : predictions.get(0));
	}

	public void setAngleUpdatesEnabled(boolean b) {
		header.setAngleUpdatesEnabled(b);
		panel.setAngleUpdatesEnabled(b);
	}

	@Override
	public void updateColors() {
		super.updateColors();
		panel.updateColors();
	}

	/** How much larger the single result row is than a normal one. */
	public static float textScale(NinjabrainBotPreferences preferences) {
		return preferences.focusTextScale.get() / 100f;
	}

	@Override
	public void updateSize(StyleManager styleManager) {
		float scale = textScale(preferences);
		panel.setTextScale(scale);
		int headerHeight = styleManager.size.TEXT_SIZE_MEDIUM + styleManager.size.PADDING;
		int rowHeight = Math.round(styleManager.size.TEXT_SIZE_MEDIUM * scale) + styleManager.size.PADDING * 2;
		setPreferredSize(new Dimension(0, headerHeight + rowHeight));
		super.updateSize(styleManager);
	}

	@Override
	public void dispose() {
		header.dispose();
		panel.dispose();
	}

}
