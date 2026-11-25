package ninjabrainbot.gui.mainwindow.triangulation;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import ninjabrainbot.event.ObservableField;
import ninjabrainbot.gui.components.labels.ThemedIcon;
import ninjabrainbot.gui.style.StyleManager;
import ninjabrainbot.io.preferences.NinjabrainBotPreferences;
import ninjabrainbot.io.preferences.UnsavedPreferences;
import ninjabrainbot.io.preferences.enums.McVersion;
import ninjabrainbot.io.preferences.enums.StrongholdDisplayType;
import ninjabrainbot.model.datastate.calculator.CalculatorResult;
import ninjabrainbot.model.datastate.calculator.ICalculatorResult;
import ninjabrainbot.model.datastate.common.IPlayerPosition;
import ninjabrainbot.model.datastate.stronghold.Chunk;
import ninjabrainbot.model.datastate.stronghold.ChunkPrediction;
import ninjabrainbot.util.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompactTriangulationPanelTests {

	private final NinjabrainBotPreferences preferences = new NinjabrainBotPreferences(new UnsavedPreferences());
	private final ObservableField<IPlayerPosition> position = new ObservableField<>(TestUtils.createPlayerPosition(4, 0, 0));
	private final ChunkPrediction best = new ChunkPrediction(new Chunk(0, 10, 0.65), position, McVersion.PRE_119);
	private StyleManager style;
	private CompactTriangulationPanel panel;

	@BeforeEach
	void createPanel() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			preferences.strongholdDisplayType.set(StrongholdDisplayType.FOURFOUR);
			preferences.colorCodeNegativeCoords.set(false);
			style = TestUtils.createStyleManager();
			panel = new CompactTriangulationPanel(style, preferences);
			style.init();
		});
	}

	@AfterEach
	void disposePanel() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			panel.dispose();
			best.dispose();
		});
	}

	@Test
	void showsOnlyTheBestPredictionOnFourLinesAndShowsTheLockWhenFrozen() throws Exception {
		ChunkPrediction other = new ChunkPrediction(new Chunk(3, 11, 0.35), null, McVersion.PRE_119);
		ICalculatorResult result = resultWithBestPrediction();
		when(result.getTopPredictions()).thenReturn(Arrays.asList(best, other));

		SwingUtilities.invokeAndWait(() -> {
			assertTrue(panel.isIdle());
			panel.setResult(result);
			assertFalse(panel.isIdle());
			assertFalse(isLockVisible());
			panel.setLocked(true);
			assertTrue(isLockVisible());

			panel.setSize(panel.getPreferredSize());
			panel.doLayout();
			List<JLabel> values = visibleValues();
			assertEquals(Arrays.asList("(4, 164)", best.formatDistanceInPlayersDimension(),
					best.formatTravelAngle(false) + "\u00B0 \u226410\u00B0", best.formatCertainty()),
					values.stream().map(JLabel::getText).collect(Collectors.toList()));
			int previousY = -1;
			for (JLabel value : values) {
				assertTrue(value.getY() > previousY);
				assertTrue(value.getWidth() >= value.getPreferredSize().width);
				assertTrue(value.getHeight() >= value.getPreferredSize().height);
				previousY = value.getY();
			}

			panel.setResult(new CalculatorResult());
			assertTrue(panel.isIdle());
			assertTrue(visibleValues().isEmpty());
		});
	}

	@Test
	void updatesTravelAndMarksTheTenDegreeBoundary() throws Exception {
		ICalculatorResult result = resultWithBestPrediction();
		SwingUtilities.invokeAndWait(() -> {
			panel.setResult(result);
			position.set(TestUtils.createPlayerPosition(4, 5, -10));
		});
		SwingUtilities.invokeAndWait(() -> {
			JLabel angle = visibleValues().get(2);
			assertEquals("159", visibleValues().get(1).getText());
			assertTrue(angle.getText().endsWith("\u226410\u00B0"));
			assertEquals(style.currentTheme.ANGLE_COLOR_MAP.get().get(1), angle.getForeground());
			position.set(TestUtils.createPlayerPosition(4, 5, -11));
		});
		SwingUtilities.invokeAndWait(() -> {
			JLabel angle = visibleValues().get(2);
			assertEquals(best.formatTravelAngle(false) + "\u00B0", angle.getText());
			assertNotEquals(style.currentTheme.ANGLE_COLOR_MAP.get().get(1), angle.getForeground());
			position.set(TestUtils.createPlayerPosition(4, 5, 180));
		});
		SwingUtilities.invokeAndWait(() -> {
			JLabel angle = visibleValues().get(2);
			assertEquals(style.currentTheme.ANGLE_COLOR_MAP.get().get(0), angle.getForeground());
		});
	}

	private ICalculatorResult resultWithBestPrediction() {
		ICalculatorResult result = mock(ICalculatorResult.class);
		when(result.success()).thenReturn(true);
		when(result.getBestPrediction()).thenReturn(best);
		return result;
	}

	private List<JLabel> visibleValues() {
		return visibleLabels(panel).stream()
				.filter(label -> label.getLabelFor() == null && !(label instanceof ThemedIcon))
				.collect(Collectors.toList());
	}

	private boolean isLockVisible() {
		return visibleLabels(panel).stream().anyMatch(ThemedIcon.class::isInstance);
	}

	private static List<JLabel> visibleLabels(Container container) {
		List<JLabel> labels = new ArrayList<>();
		for (Component child : container.getComponents()) {
			if (!child.isVisible())
				continue;
			if (child instanceof JLabel)
				labels.add((JLabel) child);
			else if (child instanceof Container)
				labels.addAll(visibleLabels((Container) child));
		}
		return labels;
	}
}
