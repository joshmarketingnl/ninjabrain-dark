package ninjabrainbot.gui.mainwindow.triangulation;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import ninjabrainbot.event.DisposeHandler;
import ninjabrainbot.event.IDisposable;
import ninjabrainbot.event.Subscription;
import ninjabrainbot.gui.components.labels.ThemedIcon;
import ninjabrainbot.gui.components.panels.ThemedPanel;
import ninjabrainbot.gui.style.StyleManager;
import ninjabrainbot.gui.style.theme.CurrentTheme;
import ninjabrainbot.io.preferences.NinjabrainBotPreferences;
import ninjabrainbot.io.preferences.enums.StrongholdDisplayType;
import ninjabrainbot.model.datastate.calculator.ICalculatorResult;
import ninjabrainbot.model.datastate.common.StructureInformation;
import ninjabrainbot.model.datastate.stronghold.ChunkPrediction;
import ninjabrainbot.util.I18n;

/** Shows the most likely stronghold in four stacked lines. */
public class CompactTriangulationPanel extends ThemedPanel implements IDisposable {

	private static final int WIDTH = 147;
	private static final int HEIGHT = 72;
	private static final int SIDE_PADDING = 5;
	private static final int COLUMN_GAP = 5;
	private static final String IDLE = "idle";
	private static final String RESULT = "result";

	private final NinjabrainBotPreferences preferences;
	private final CurrentTheme theme;
	private final CardLayout cards = new CardLayout();
	private final JLabel waiting = new JLabel(I18n.get("waiting_f3c"));
	private final JLabel dragHint = new JLabel(I18n.get("compact.drag_hint"));
	private final JLabel locationCaption = new JLabel();
	private final JLabel[] captions = { locationCaption, new JLabel(I18n.get("dist")),
			new JLabel(I18n.get("angle")), new JLabel(I18n.get("certainty_2")) };
	private final JLabel location = new JLabel("", JLabel.RIGHT);
	private final JLabel distance = new JLabel("", JLabel.RIGHT);
	private final JLabel angle = new JLabel("", JLabel.RIGHT);
	private final JLabel certainty = new JLabel("", JLabel.RIGHT);
	private final JLabel[] values = { location, distance, angle, certainty };
	private final ThemedIcon lockIcon;
	private final JPanel locationAndLock = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
	private final DisposeHandler disposeHandler = new DisposeHandler();
	private Font valueFont;
	private ChunkPrediction prediction;
	private Subscription travelSubscription;

	public CompactTriangulationPanel(StyleManager styleManager, NinjabrainBotPreferences preferences) {
		super(styleManager);
		this.preferences = preferences;
		theme = styleManager.currentTheme;
		setBackgroundColor(theme.COLOR_SLIGHTLY_WEAK);
		setAlignmentX(0);
		setLayout(cards);
		setPreferredSize(new Dimension(WIDTH, HEIGHT));
		setBorder(new EmptyBorder(2, SIDE_PADDING, 2, SIDE_PADDING));

		Image lock = new ImageIcon(getClass().getResource("/lock_icon.png")).getImage();
		lockIcon = new ThemedIcon(styleManager, new ImageIcon(lock.getScaledInstance(9, 9, Image.SCALE_SMOOTH)));
		lockIcon.getAccessibleContext().setAccessibleName(I18n.get("compact.locked"));
		lockIcon.setBorder(new EmptyBorder(0, 3, 0, 0));
		lockIcon.setVisible(false);
		locationAndLock.setOpaque(false);
		locationAndLock.add(locationCaption);
		locationAndLock.add(lockIcon);

		JPanel result = new JPanel(new GridLayout(values.length, 1));
		result.setOpaque(false);
		for (int row = 0; row < values.length; row++) {
			captions[row].setLabelFor(values[row]);
			JPanel line = new JPanel(new BorderLayout(COLUMN_GAP, 0));
			line.setOpaque(false);
			line.add(row == 0 ? locationAndLock : captions[row], BorderLayout.WEST);
			line.add(values[row], BorderLayout.CENTER);
			result.add(line);
		}
		add(result, RESULT);

		JPanel idle = new JPanel(new GridBagLayout());
		idle.setOpaque(false);
		GridBagConstraints idleRow = new GridBagConstraints();
		idleRow.gridy = 0;
		idle.add(waiting, idleRow);
		idleRow.gridy++;
		idle.add(dragHint, idleRow);
		add(idle, IDLE);

		updateSize(styleManager);
		updateResult();
		disposeHandler.add(preferences.strongholdDisplayType.whenModified().subscribeEDT(this::refreshLocation));
		disposeHandler.add(preferences.colorCodeNegativeCoords.whenModified().subscribeEDT(this::refreshLocation));
	}

	public void setResult(ICalculatorResult result) {
		ChunkPrediction nextPrediction = result != null && result.success() ? result.getBestPrediction() : null;
		if (prediction == nextPrediction)
			return;
		if (travelSubscription != null)
			travelSubscription.dispose();
		prediction = nextPrediction;
		updateResult();
		travelSubscription = isIdle() ? null : prediction.whenRelativePlayerPositionChanged().subscribeEDT(this::refreshTravel);
	}

	private void updateResult() {
		cards.show(this, isIdle() ? IDLE : RESULT);
		if (!isIdle()) {
			updateLocation();
			updateTravel();
			certainty.setText(prediction.formatCertainty());
			fitValue(3);
			updateCertaintyColor();
		}
	}

	public boolean isIdle() {
		return prediction == null;
	}

	public void setLocked(boolean locked) {
		lockIcon.setVisible(locked);
		fitValue(0);
	}

	private void refreshLocation() {
		if (!isIdle())
			updateLocation();
	}

	private void updateLocation() {
		boolean chunkMode = preferences.strongholdDisplayType.get() == StrongholdDisplayType.CHUNK;
		locationCaption.setText(I18n.get(chunkMode ? "chunk" : "location"));
		location.setText(ChunkCoordinates.format(prediction.chunk, preferences, theme));
		fitValue(0);
	}

	private void refreshTravel() {
		if (!isIdle())
			updateTravel();
	}

	private void updateTravel() {
		distance.setText(prediction.formatDistanceInPlayersDimension());
		int greenDegrees = StructureInformation.TRAVEL_ANGLE_GREEN_DEGREES;
		String angleText = prediction.formatTravelAngle(false) + "\u00B0";
		if (Math.abs(prediction.getTravelAngleDiff()) <= greenDegrees)
			angleText += " \u2264" + greenDegrees + "\u00B0";
		angle.setText(angleText);
		angle.setForeground(theme.ANGLE_COLOR_MAP.get().get(prediction.getTravelAngleDiffColor()));
		fitValue(1);
		fitValue(2);
	}

	private void fitValue(int row) {
		int captionWidth = (row == 0 ? locationAndLock : captions[row]).getPreferredSize().width;
		fitLabel(values[row], valueFont, WIDTH - 2 * SIDE_PADDING - COLUMN_GAP - captionWidth);
	}

	private static void fitLabel(JLabel label, Font font, int availableWidth) {
		if (!font.equals(label.getFont()))
			label.setFont(font);
		for (int size = font.getSize() - 1; size > 0 && label.getPreferredSize().width > availableWidth; size--)
			label.setFont(font.deriveFont((float) size));
	}

	private void updateCertaintyColor() {
		certainty.setForeground(theme.CERTAINTY_COLOR_MAP.get().get(prediction.chunk.weight));
	}

	@Override
	public void updateSize(StyleManager styleManager) {
		Font captionFont = styleManager.fontSize(10, true);
		valueFont = styleManager.fontSize(12, false);
		for (JLabel caption : captions)
			caption.setFont(captionFont);
		for (int row = 0; row < values.length; row++)
			fitValue(row);
		fitLabel(waiting, valueFont, WIDTH - 2 * SIDE_PADDING);
		fitLabel(dragHint, styleManager.fontSize(9, true), WIDTH - 2 * SIDE_PADDING);
	}

	@Override
	public void updateColors() {
		super.updateColors();
		for (JLabel caption : captions)
			caption.setForeground(theme.TEXT_COLOR_NEUTRAL.color());
		waiting.setForeground(theme.TEXT_COLOR_SLIGHTLY_WEAK.color());
		dragHint.setForeground(theme.TEXT_COLOR_NEUTRAL.color());
		location.setForeground(theme.TEXT_COLOR_SLIGHTLY_WEAK.color());
		distance.setForeground(theme.TEXT_COLOR_SLIGHTLY_WEAK.color());
		if (!isIdle()) {
			updateLocation();
			updateTravel();
			updateCertaintyColor();
		}
	}

	@Override
	public void dispose() {
		disposeHandler.dispose();
		if (travelSubscription != null)
			travelSubscription.dispose();
		prediction = null;
	}
}
