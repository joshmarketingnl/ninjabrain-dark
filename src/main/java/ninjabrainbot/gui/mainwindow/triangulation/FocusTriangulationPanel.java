package ninjabrainbot.gui.mainwindow.triangulation;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;
import java.util.Locale;

import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

import ninjabrainbot.event.DisposeHandler;
import ninjabrainbot.event.IDisposable;
import ninjabrainbot.event.Subscription;
import ninjabrainbot.gui.components.labels.ColorMapLabel;
import ninjabrainbot.gui.components.labels.ColoredLabel;
import ninjabrainbot.gui.components.labels.ThemedLabel;
import ninjabrainbot.gui.components.panels.ThemedPanel;
import ninjabrainbot.gui.style.SizePreference;
import ninjabrainbot.gui.style.StyleManager;
import ninjabrainbot.gui.style.theme.ColumnLayout;
import ninjabrainbot.io.preferences.NinjabrainBotPreferences;
import ninjabrainbot.io.preferences.enums.FocusLayoutType;
import ninjabrainbot.io.preferences.enums.StrongholdDisplayType;
import ninjabrainbot.model.datastate.calculator.ICalculatorResult;
import ninjabrainbot.model.datastate.stronghold.Chunk;
import ninjabrainbot.model.datastate.stronghold.ChunkPrediction;
import ninjabrainbot.util.I18n;

/**
 * Shows only the most likely stronghold, in large type, in one of three arrangements. The wide one
 * puts all five fields on a single line; the other two trade width for height so the window covers
 * less of the screen.
 */
public class FocusTriangulationPanel extends ThemedPanel implements IDisposable {

	/** Widest value that has to fit in a coordinate cell without being clipped. */
	private static final String WIDEST_COORDINATE = "(-12345, -12345)";

	/** The narrow layout right aligns its values, so it only budgets for a realistic coordinate. */
	private static final String TYPICAL_COORDINATE = "(-1234, -1234)";

	private final StyleManager styleManager;
	private final NinjabrainBotPreferences preferences;

	private final ThemedLabel location;
	private final ColoredLabel certainty;
	private final ThemedLabel distance;
	private final ThemedLabel nether;
	private final ColorMapLabel angle;

	private final FocusCell locationCell;
	private final FocusCell certaintyCell;
	private final FocusCell distanceCell;
	private final FocusCell netherCell;
	private final FocusCell angleCell;

	private FocusLayoutType currentLayout;
	private ChunkPrediction currentPrediction;
	private Subscription predictionSubscription;
	private final DisposeHandler disposeHandler = new DisposeHandler();

	public FocusTriangulationPanel(StyleManager styleManager, NinjabrainBotPreferences preferences) {
		super(styleManager);
		this.styleManager = styleManager;
		this.preferences = preferences;
		setAlignmentX(0);

		location = new ThemedLabel(styleManager, "", true, true);
		certainty = new ColoredLabel(styleManager, true);
		distance = new ThemedLabel(styleManager, "", true, true);
		nether = new ThemedLabel(styleManager, "", true, true);
		angle = new ColorMapLabel(styleManager, true, true);
		angle.setColorMap(styleManager.currentTheme.ANGLE_COLOR_MAP);

		locationCell = new FocusCell(styleManager, I18n.get("location"), location);
		certaintyCell = new FocusCell(styleManager, I18n.get("certainty_2"), certainty);
		distanceCell = new FocusCell(styleManager, I18n.get("dist"), distance);
		netherCell = new FocusCell(styleManager, I18n.get("nether"), nether);
		angleCell = new FocusCell(styleManager, I18n.get("angle"), angle);

		setBackgroundColor(styleManager.currentTheme.COLOR_SLIGHTLY_WEAK);
		location.setForegroundColor(styleManager.currentTheme.TEXT_COLOR_SLIGHTLY_WEAK);
		distance.setForegroundColor(styleManager.currentTheme.TEXT_COLOR_SLIGHTLY_WEAK);
		nether.setForegroundColor(styleManager.currentTheme.TEXT_COLOR_SLIGHTLY_WEAK);
		angle.setForegroundColor(styleManager.currentTheme.TEXT_COLOR_SLIGHTLY_WEAK);

		rebuild();
		disposeHandler.add(preferences.focusLayout.whenModified().subscribeEDT(__ -> rebuild()));
		disposeHandler.add(preferences.strongholdDisplayType.whenModified().subscribeEDT(__ -> updateCaptions()));
	}

	private FocusLayoutType selectedLayout() {
		return preferences.focusLayout.get();
	}

	private float textScale() {
		return textScale(preferences);
	}

	/** How much larger the values are than a normal row of text. */
	public static float textScale(NinjabrainBotPreferences preferences) {
		return preferences.focusTextScale.get() / 100f;
	}

	private void rebuild() {
		currentLayout = selectedLayout();
		removeAll();
		int gap = styleManager.size.PADDING;
		// Equal rows, so a value can never be clipped by the row below it.
		setLayout(new GridLayout(rowCount(), 1, 0, styleManager.size.PADDING_THIN));
		switch (currentLayout) {
			case STACKED:
				add(row(gap, new FocusCell[] { locationCell }, new float[] { 1f }));
				add(row(gap, new FocusCell[] { netherCell }, new float[] { 1f }));
				add(row(gap, new FocusCell[] { distanceCell }, new float[] { 1f }));
				add(row(gap, new FocusCell[] { angleCell }, new float[] { 1f }));
				add(row(gap, new FocusCell[] { certaintyCell }, new float[] { 1f }));
				break;
			case GRID:
				add(row(gap, new FocusCell[] { locationCell, netherCell }, new float[] { 1f, 1f }));
				add(row(gap, new FocusCell[] { certaintyCell, distanceCell, angleCell }, new float[] { 1f, 1f, 1.7f }));
				break;
			case ROW:
			default:
				add(row(gap, new FocusCell[] { locationCell, certaintyCell, distanceCell, netherCell, angleCell }, new float[] { 2f, 1f, 1f, 1.8f, 2.5f }));
				break;
		}
		boolean stacked = currentLayout == FocusLayoutType.STACKED;
		for (FocusCell cell : cells()) {
			cell.setHorizontal(stacked);
			cell.setEmphasis(styleManager.currentTheme.COLOR_SLIGHTLY_WEAK);
			cell.setAlignment(stacked ? SwingConstants.RIGHT : SwingConstants.CENTER);
		}
		if (currentLayout == FocusLayoutType.GRID) {
			// Pin the pairs to the window edges instead of letting them float, and give the
			// distance its own panel so it reads at a glance.
			locationCell.setAlignment(SwingConstants.LEFT);
			netherCell.setAlignment(SwingConstants.RIGHT);
			certaintyCell.setAlignment(SwingConstants.LEFT);
			angleCell.setAlignment(SwingConstants.RIGHT);
			distanceCell.setEmphasis(styleManager.currentTheme.COLOR_SLIGHTLY_STRONG);
		}
		updateCaptions();
		updateSize(styleManager);
		updateColors();
		revalidate();
		repaint();
	}

	private ThemedPanel row(int gap, FocusCell[] rowCells, float[] weights) {
		ThemedPanel panel = new ThemedPanel(styleManager);
		ColumnLayout columnLayout = new ColumnLayout(gap);
		panel.setLayout(columnLayout);
		panel.setAlignmentX(0);
		panel.setBackgroundColor(styleManager.currentTheme.COLOR_SLIGHTLY_WEAK);
		for (int i = 0; i < rowCells.length; i++) {
			columnLayout.setRelativeWidth(rowCells[i], weights[i]);
			panel.add(rowCells[i]);
		}
		return panel;
	}

	private FocusCell[] cells() {
		return new FocusCell[] { locationCell, certaintyCell, distanceCell, netherCell, angleCell };
	}

	private void updateCaptions() {
		boolean chunkMode = preferences.strongholdDisplayType.get() == StrongholdDisplayType.CHUNK;
		locationCell.setCaption(chunkMode ? I18n.get("chunk") : I18n.get("location"));
	}

	public void setResult(ICalculatorResult result) {
		if (predictionSubscription != null) {
			predictionSubscription.dispose();
			predictionSubscription = null;
		}
		List<ChunkPrediction> predictions = result == null ? null : result.getTopPredictions();
		ChunkPrediction prediction = predictions == null || predictions.isEmpty() ? null : predictions.get(0);
		currentPrediction = prediction;
		if (prediction == null) {
			for (FocusCell cell : cells())
				cell.clear();
			return;
		}
		setText(prediction);
		predictionSubscription = prediction.whenRelativePlayerPositionChanged().subscribeEDT(__ -> setText(prediction));
	}

	private void setText(ChunkPrediction prediction) {
		location.setText(formatCoordinates(prediction.chunk));
		certainty.setText(prediction.formatCertainty(), (float) prediction.chunk.weight);
		distance.setText(prediction.formatDistanceInPlayersDimension());
		nether.setText(formatCoordinates(prediction.xInNetherForDisplay(), prediction.zInNetherForDisplay()));
		angle.setText(prediction.formatTravelAngle(false));
		angle.setColoredText(prediction.formatTravelAngleDiff(), prediction.getTravelAngleDiffColor());
	}

	public void setAngleUpdatesEnabled(boolean b) {
		// The angle is the whole point of this view, it stays visible.
	}

	@Override
	public void updateColors() {
		super.updateColors();
		for (FocusCell cell : cells())
			cell.updateColors();
		angle.updateColor();
		certainty.updateColors();
		if (currentPrediction != null)
			setText(currentPrediction);
	}

	@Override
	public void updateSize(StyleManager styleManager) {
		float scale = textScale();
		for (FocusCell cell : cells())
			cell.setTextScale(scale);

		// Fonts need roughly a third more room than their point size for ascenders and descenders.
		int captionHeight = Math.round(styleManager.size.TEXT_SIZE_SMALL * 1.35f);
		int valueHeight = Math.round(styleManager.size.TEXT_SIZE_MEDIUM * scale * 1.35f);
		int rowHeight = currentLayout == FocusLayoutType.STACKED ? valueHeight : captionHeight + valueHeight;
		int rows = rowCount();
		setPreferredSize(new Dimension(0, rows * (rowHeight + styleManager.size.PADDING_THIN) + styleManager.size.PADDING));
		setBorder(new EmptyBorder(styleManager.size.PADDING_THIN, styleManager.size.PADDING, styleManager.size.PADDING_THIN, styleManager.size.PADDING));
		super.updateSize(styleManager);
	}

	private int rowCount() {
		switch (currentLayout == null ? selectedLayout() : currentLayout) {
			case STACKED:
				return 5;
			case GRID:
				return 2;
			default:
				return 1;
		}
	}

	/** Window width this layout needs so the widest coordinate still fits. */
	public static int preferredWindowWidth(StyleManager styleManager, NinjabrainBotPreferences preferences) {
		float scale = textScale(preferences);
		int coordinate = styleManager.getTextWidth(WIDEST_COORDINATE, styleManager.fontSize(styleManager.size.TEXT_SIZE_MEDIUM * scale, false));
		int padding = styleManager.size.PADDING;
		switch (preferences.focusLayout.get()) {
			case STACKED:
				int typical = styleManager.getTextWidth(TYPICAL_COORDINATE, styleManager.fontSize(styleManager.size.TEXT_SIZE_MEDIUM * scale, false));
				int caption = styleManager.getTextWidth("Location", styleManager.fontSize(styleManager.size.TEXT_SIZE_SMALL, true));
				return typical + caption + 4 * padding;
			case GRID:
				return 2 * coordinate + 6 * padding;
			default:
				// location + certainty + distance + nether + angle, by the same weights as the row.
				return Math.round(coordinate * (2f + 1f + 1f + 1.8f + 2.5f) / 2f) + 6 * padding;
		}
	}

	private String formatCoordinates(Chunk chunk) {
		switch (preferences.strongholdDisplayType.get()) {
			case FOURFOUR:
				return formatCoordinates(chunk.fourFourX(), chunk.fourFourZ());
			case EIGHTEIGHT:
				return formatCoordinates(chunk.eightEightX(), chunk.eightEightZ());
			default:
				return formatCoordinates(chunk.x, chunk.z);
		}
	}

	private String formatCoordinates(int x, int z) {
		if (!preferences.colorCodeNegativeCoords.get())
			return String.format(Locale.US, "(%d, %d)", x, z);
		Color negative = styleManager.currentTheme.COLOR_NEGATIVE.color();
		Color normal = styleManager.currentTheme.TEXT_COLOR_SLIGHTLY_WEAK.color();
		String xColor = hex(x < 0 ? negative : normal);
		String zColor = hex(z < 0 ? negative : normal);
		return String.format(Locale.US, "<html>(<font color='%s'>%d</font>, <font color='%s'>%d</font>)</html>", xColor, x, zColor, z);
	}

	private static String hex(Color c) {
		return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
	}

	@Override
	public int getTextSize(SizePreference p) {
		return p.TEXT_SIZE_MEDIUM;
	}

	@Override
	public void dispose() {
		if (predictionSubscription != null)
			predictionSubscription.dispose();
		disposeHandler.dispose();
		for (FocusCell cell : cells())
			cell.dispose();
	}

}
