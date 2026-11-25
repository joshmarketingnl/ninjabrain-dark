package ninjabrainbot.gui.mainwindow.triangulation;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.util.Collections;
import java.util.List;

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
import ninjabrainbot.model.datastate.stronghold.ChunkPrediction;
import ninjabrainbot.util.I18n;

/** Densely packed view of the current stronghold candidates. */
public class CompactTriangulationPanel extends ThemedPanel implements IDisposable {

	private final NinjabrainBotPreferences preferences;
	private final CurrentTheme theme;
	private final JLabel locationHeader = new JLabel();
	private final JLabel[] headings = { locationHeader, new JLabel(I18n.get("dist"), JLabel.RIGHT),
			new JLabel(I18n.get("angle"), JLabel.RIGHT), new JLabel(I18n.get("certainty_2"), JLabel.RIGHT) };
	private final JPanel divider = new JPanel();
	private final ThemedIcon lockIcon;
	private final Row[] rows = new Row[5];
	private final DisposeHandler disposeHandler = new DisposeHandler();
	private ICalculatorResult result;

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
		locationAndLock.add(locationHeader);
		locationAndLock.add(lockIcon);
		addCell(locationAndLock, 0, 0);
		for (int i = 1; i < headings.length; i++)
			addCell(headings[i], i, 0);

		GridBagConstraints rule = new GridBagConstraints();
		rule.gridx = 0;
		rule.gridy = 1;
		rule.gridwidth = headings.length;
		rule.fill = GridBagConstraints.HORIZONTAL;
		rule.insets = new Insets(1, 0, 1, 0);
		divider.setPreferredSize(new Dimension(1, 1));
		add(divider, rule);
		for (int i = 0; i < rows.length; i++)
			rows[i] = new Row(i + 2);

		updateLocations();
		disposeHandler.add(preferences.strongholdDisplayType.whenModified().subscribeEDT(this::updateLocations));
		disposeHandler.add(preferences.colorCodeNegativeCoords.whenModified().subscribeEDT(this::updateLocations));
	}

	private void addCell(Component component, int column, int row) {
		GridBagConstraints cell = new GridBagConstraints();
		cell.gridx = column;
		cell.gridy = row;
		cell.fill = GridBagConstraints.HORIZONTAL;
		cell.insets = new Insets(0, column == 0 ? 0 : 6, 0, 0);
		add(component, cell);
	}

	public void setResult(ICalculatorResult result) {
		if (this.result == result)
			return;
		Dimension before = getPreferredSize();
		this.result = result;
		List<ChunkPrediction> predictions = result != null && result.success() ? result.getTopPredictions() : Collections.emptyList();
		for (int i = 0; i < rows.length; i++)
			rows[i].setPrediction(i < predictions.size() ? predictions.get(i) : null);
		firePropertyChange("preferredSize", before, getPreferredSize());
	}

	public boolean isIdle() {
		return rows[0].prediction == null;
	}

	public void setLocked(boolean locked) {
		Dimension before = getPreferredSize();
		lockIcon.setVisible(locked);
		firePropertyChange("preferredSize", before, getPreferredSize());
	}

	private void updateLocations() {
		Dimension before = getPreferredSize();
		locationHeader.setText(I18n.get(preferences.strongholdDisplayType.get() == StrongholdDisplayType.CHUNK ? "chunk" : "location"));
		for (Row row : rows)
			row.updateLocation();
		firePropertyChange("preferredSize", before, getPreferredSize());
	}

	@Override
	public void updateSize(StyleManager styleManager) {
		Font headerFont = styleManager.fontSize(10, false);
		Font valueFont = styleManager.fontSize(12, true);
		for (JLabel heading : headings)
			heading.setFont(headerFont);
		for (Row row : rows)
			for (JLabel label : row.labels)
				label.setFont(valueFont);
	}

	@Override
	public void updateColors() {
		super.updateColors();
		for (JLabel heading : headings)
			heading.setForeground(theme.TEXT_COLOR_NEUTRAL.color());
		divider.setBackground(theme.COLOR_DIVIDER.color());
		for (Row row : rows)
			row.updateColors();
	}

	@Override
	public void dispose() {
		disposeHandler.dispose();
		for (Row row : rows)
			row.setPrediction(null);
	}

	private class Row {

		private final JLabel location = new JLabel();
		private final JLabel distance = new JLabel("", JLabel.RIGHT);
		private final JLabel angle = new JLabel("", JLabel.RIGHT);
		private final JLabel certainty = new JLabel("", JLabel.RIGHT);
		private final JLabel[] labels = { location, distance, angle, certainty };
		private ChunkPrediction prediction;
		private Subscription travelSubscription;

		Row(int row) {
			for (int i = 0; i < labels.length; i++) {
				labels[i].setVisible(false);
				addCell(labels[i], i, row);
			}
		}

		void setPrediction(ChunkPrediction prediction) {
			if (travelSubscription != null) {
				travelSubscription.dispose();
				travelSubscription = null;
			}
			this.prediction = prediction;
			for (JLabel label : labels)
				label.setVisible(prediction != null);
			if (prediction != null) {
				updateLocation();
				updateTravel();
				certainty.setText(prediction.formatCertainty());
				certainty.setForeground(theme.CERTAINTY_COLOR_MAP.get().get(prediction.chunk.weight));
				travelSubscription = prediction.whenRelativePlayerPositionChanged().subscribeEDT(() -> {
					Dimension before = getPreferredSize();
					updateTravel();
					firePropertyChange("preferredSize", before, getPreferredSize());
				});
			}
		}

		void updateLocation() {
			if (prediction != null)
				location.setText(ChunkCoordinates.format(prediction.chunk, preferences, theme));
		}

		void updateTravel() {
			if (prediction == null)
				return;
			distance.setText(prediction.formatDistanceInPlayersDimension());
			angle.setText(prediction.formatTravelAngle(false));
			angle.setForeground(theme.ANGLE_COLOR_MAP.get().get(prediction.getTravelAngleDiffColor()));
		}

		void updateColors() {
			location.setForeground(theme.TEXT_COLOR_SLIGHTLY_WEAK.color());
			distance.setForeground(theme.TEXT_COLOR_SLIGHTLY_WEAK.color());
			if (prediction != null) {
				updateLocation();
				angle.setForeground(theme.ANGLE_COLOR_MAP.get().get(prediction.getTravelAngleDiffColor()));
				certainty.setForeground(theme.CERTAINTY_COLOR_MAP.get().get(prediction.chunk.weight));
			}
		}
	}
}
