package ninjabrainbot.gui.mainwindow.triangulation;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;

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

	private final NinjabrainBotPreferences preferences;
	private final CurrentTheme theme;
	private final JLabel locationCaption = new JLabel();
	private final JLabel[] captions = { locationCaption, new JLabel(I18n.get("dist")),
			new JLabel(I18n.get("angle")), new JLabel(I18n.get("certainty_2")) };
	private final JLabel location = new JLabel("", JLabel.RIGHT);
	private final JLabel distance = new JLabel("", JLabel.RIGHT);
	private final JLabel angle = new JLabel("", JLabel.RIGHT);
	private final JLabel certainty = new JLabel("", JLabel.RIGHT);
	private final JLabel[] values = { location, distance, angle, certainty };
	private final ThemedIcon lockIcon;
	private final DisposeHandler disposeHandler = new DisposeHandler();
	private ChunkPrediction prediction;
	private Subscription travelSubscription;

	public CompactTriangulationPanel(StyleManager styleManager, NinjabrainBotPreferences preferences) {
		super(styleManager);
		this.preferences = preferences;
		theme = styleManager.currentTheme;
		setBackgroundColor(theme.COLOR_SLIGHTLY_WEAK);
		setAlignmentX(0);
		setLayout(new GridBagLayout());
		setBorder(new EmptyBorder(2, 5, 2, 5));

		Image lock = new ImageIcon(getClass().getResource("/lock_icon.png")).getImage();
		lockIcon = new ThemedIcon(styleManager, new ImageIcon(lock.getScaledInstance(9, 9, Image.SCALE_SMOOTH)));
		lockIcon.getAccessibleContext().setAccessibleName(I18n.get("compact.locked"));
		lockIcon.setBorder(new EmptyBorder(0, 3, 0, 0));
		lockIcon.setVisible(false);
		JPanel locationAndLock = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		locationAndLock.setOpaque(false);
		locationAndLock.add(locationCaption);
		locationAndLock.add(lockIcon);
		addCell(locationAndLock, 0, 0);

		for (int row = 0; row < values.length; row++) {
			captions[row].setLabelFor(values[row]);
			if (row > 0)
				addCell(captions[row], 0, row);
			addCell(values[row], 1, row);
		}

		updateResult();
		disposeHandler.add(preferences.strongholdDisplayType.whenModified().subscribeEDT(this::refreshLocation));
		disposeHandler.add(preferences.colorCodeNegativeCoords.whenModified().subscribeEDT(this::refreshLocation));
	}

	private void addCell(Component component, int column, int row) {
		GridBagConstraints cell = new GridBagConstraints();
		cell.gridx = column;
		cell.gridy = row;
		cell.fill = GridBagConstraints.HORIZONTAL;
		cell.insets = new Insets(0, column == 0 ? 0 : 5, 0, 0);
		add(component, cell);
	}

	public void setResult(ICalculatorResult result) {
		ChunkPrediction nextPrediction = result != null && result.success() ? result.getBestPrediction() : null;
		if (prediction == nextPrediction)
			return;
		Dimension before = getPreferredSize();
		if (travelSubscription != null)
			travelSubscription.dispose();
		prediction = nextPrediction;
		updateResult();
		travelSubscription = isIdle() ? null : prediction.whenRelativePlayerPositionChanged().subscribeEDT(this::refreshTravel);
		firePropertyChange("preferredSize", before, getPreferredSize());
	}

	private void updateResult() {
		boolean hasPrediction = !isIdle();
		for (int row = 0; row < values.length; row++) {
			captions[row].setVisible(row == 0 || hasPrediction);
			values[row].setVisible(hasPrediction);
		}
		updateLocation();
		if (hasPrediction) {
			updateTravel();
			certainty.setText(prediction.formatCertainty());
			updateCertaintyColor();
		}
	}

	public boolean isIdle() {
		return prediction == null;
	}

	public void setLocked(boolean locked) {
		Dimension before = getPreferredSize();
		lockIcon.setVisible(locked);
		firePropertyChange("preferredSize", before, getPreferredSize());
	}

	private void refreshLocation() {
		Dimension before = getPreferredSize();
		updateLocation();
		firePropertyChange("preferredSize", before, getPreferredSize());
	}

	private void updateLocation() {
		if (isIdle()) {
			locationCaption.setText(I18n.get("waiting_f3c"));
			return;
		}
		boolean chunkMode = preferences.strongholdDisplayType.get() == StrongholdDisplayType.CHUNK;
		locationCaption.setText(I18n.get(chunkMode ? "chunk" : "location"));
		location.setText(ChunkCoordinates.format(prediction.chunk, preferences, theme));
	}

	private void refreshTravel() {
		if (isIdle())
			return;
		Dimension before = getPreferredSize();
		updateTravel();
		firePropertyChange("preferredSize", before, getPreferredSize());
	}

	private void updateTravel() {
		distance.setText(prediction.formatDistanceInPlayersDimension());
		int greenDegrees = StructureInformation.TRAVEL_ANGLE_GREEN_DEGREES;
		String angleText = prediction.formatTravelAngle(false) + "\u00B0";
		if (Math.abs(prediction.getTravelAngleDiff()) <= greenDegrees)
			angleText += " \u2264" + greenDegrees + "\u00B0";
		angle.setText(angleText);
		angle.setForeground(theme.ANGLE_COLOR_MAP.get().get(prediction.getTravelAngleDiffColor()));
	}

	private void updateCertaintyColor() {
		certainty.setForeground(theme.CERTAINTY_COLOR_MAP.get().get(prediction.chunk.weight));
	}

	@Override
	public void updateSize(StyleManager styleManager) {
		Font captionFont = styleManager.fontSize(10, true);
		Font valueFont = styleManager.fontSize(12, false);
		for (JLabel caption : captions)
			caption.setFont(captionFont);
		for (JLabel value : values)
			value.setFont(valueFont);
	}

	@Override
	public void updateColors() {
		super.updateColors();
		for (JLabel caption : captions)
			caption.setForeground(theme.TEXT_COLOR_NEUTRAL.color());
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
