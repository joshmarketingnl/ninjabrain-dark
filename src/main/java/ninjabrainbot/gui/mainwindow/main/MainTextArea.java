package ninjabrainbot.gui.mainwindow.main;

import java.awt.CardLayout;
import java.awt.Dimension;

import ninjabrainbot.gui.components.panels.ResizablePanel;
import ninjabrainbot.gui.mainwindow.alladvancements.AllAdvancementsPanel;
import ninjabrainbot.gui.mainwindow.triangulation.BasicTriangulationPanel;
import ninjabrainbot.gui.mainwindow.triangulation.DetailedTriangulationPanel;
import ninjabrainbot.gui.mainwindow.triangulation.FocusTriangulationPanel;
import ninjabrainbot.gui.style.StyleManager;
import ninjabrainbot.io.preferences.NinjabrainBotPreferences;
import ninjabrainbot.io.preferences.enums.MainViewType;
import ninjabrainbot.model.datastate.IDataState;
import ninjabrainbot.model.datastate.blind.BlindResult;
import ninjabrainbot.model.datastate.calculator.ICalculatorResult;
import ninjabrainbot.model.datastate.common.ResultType;
import ninjabrainbot.model.datastate.divine.DivineResult;
import ninjabrainbot.model.input.IButtonInputHandler;

public class MainTextArea extends ResizablePanel {

	private final String BLIND = "BLIND", DIVINE = "DIVINE", TRIANGULATION = "TRI", TRIANGULATION_DETAILED = "DET", TRIANGULATION_FOCUS = "FOC", ALL_ADVANCEMENTS = "AA";

	private final NinjabrainBotPreferences preferences;

	final IDataState dataState;

	final BasicTriangulationPanel basicTriangulation;
	final DetailedTriangulationPanel detailedTriangulation;
	final FocusTriangulationPanel focusTriangulation;
	final BlindPanel blind;
	final DivinePanel divine;
	final AllAdvancementsPanel allAdvancements;

	boolean idle;
	final CardLayout layout;

	public MainTextArea(StyleManager styleManager, IButtonInputHandler buttonInputHandler, NinjabrainBotPreferences preferences, IDataState dataState) {
		this.preferences = preferences;
		this.dataState = dataState;
		layout = new CardLayout();
		idle = true;
		setLayout(layout);
		setAlignmentX(0);
		basicTriangulation = new BasicTriangulationPanel(styleManager, preferences);
		detailedTriangulation = new DetailedTriangulationPanel(styleManager, preferences);
		focusTriangulation = new FocusTriangulationPanel(styleManager, preferences);
		blind = new BlindPanel(styleManager);
		divine = new DivinePanel(styleManager);
		allAdvancements = new AllAdvancementsPanel(styleManager, buttonInputHandler, dataState.allAdvancementsDataState(), preferences);
		add(basicTriangulation, TRIANGULATION);
		add(detailedTriangulation, TRIANGULATION_DETAILED);
		add(focusTriangulation, TRIANGULATION_FOCUS);
		add(blind, BLIND);
		add(divine, DIVINE);
		add(allAdvancements, ALL_ADVANCEMENTS);
		setOpaque(false);
		layout.show(this, triangulationCard());
		setupSubscriptions();

		setResult(dataState.calculatorResult().get());
		setResult(dataState.blindResult().get());
		setResult(dataState.divineResult().get());
		updateResult();
	}

	private void setupSubscriptions() {
		// Settings
		disposeHandler.add(preferences.showNetherCoords.whenModified().subscribeEDT(this::setNetherCoordsEnabled));
		disposeHandler.add(preferences.showAngleUpdates.whenModified().subscribeEDT(this::setAngleUpdatesEnabled));
		disposeHandler.add(preferences.view.whenModified().subscribeEDT(this::onViewTypeChanged));
		disposeHandler.add(preferences.oneDotTwentyPlusAA.whenModified().subscribeEDT(this::updateOneDotTwentyPlusAAEnabled));
		// Data state
		disposeHandler.add(dataState.calculatorResult().subscribeEDT(this::setResult));
		disposeHandler.add(dataState.blindResult().subscribeEDT(this::setResult));
		disposeHandler.add(dataState.divineResult().subscribeEDT(this::setResult));
		disposeHandler.add(dataState.resultType().subscribeEDT(this::updateResult));
	}

	/** Which triangulation card the current view setting maps to. */
	private String triangulationCard() {
		switch (preferences.view.get()) {
			case BASIC:
				return TRIANGULATION;
			case FOCUS:
				return TRIANGULATION_FOCUS;
			default:
				return TRIANGULATION_DETAILED;
		}
	}

	private void onViewTypeChanged() {
		ICalculatorResult result = dataState.calculatorResult().get();
		setResult(result);
		switch (preferences.view.get()) {
			case BASIC:
				basicTriangulation.updateColors();
				break;
			case FOCUS:
				focusTriangulation.updateColors();
				break;
			default:
				detailedTriangulation.updateColors();
		}
		updateResult();
	}

	private void updateResult() {
		ResultType resultType = dataState.resultType().get();
		idle = false;
		switch (resultType) {
			case NONE:
				layout.show(this, triangulationCard());
				idle = true;
				break;
			case FAILED:
				layout.show(this, TRIANGULATION);
				break;
			case TRIANGULATION:
				layout.show(this, triangulationCard());
				break;
			case BLIND:
				layout.show(this, BLIND);
				break;
			case DIVINE:
				layout.show(this, DIVINE);
				break;
			case ALL_ADVANCEMENTS:
				layout.show(this, ALL_ADVANCEMENTS);
		}
		revalidate();
		whenSizeModified.notifySubscribers(this);
	}

	private void setResult(ICalculatorResult result) {
		if (preferences.view.get() == MainViewType.BASIC || (result != null && !result.success())) {
			basicTriangulation.setResult(result);
			basicTriangulation.updateColors();
		} else if (preferences.view.get() == MainViewType.FOCUS) {
			focusTriangulation.setResult(result);
		} else {
			detailedTriangulation.setResult(result);
		}
	}

	private void setResult(BlindResult result) {
		blind.setResult(result);
		blind.updateColors();
	}

	private void setResult(DivineResult result) {
		divine.setResult(result);
		divine.updateColors();
	}

	private void setNetherCoordsEnabled(boolean b) {
		basicTriangulation.netherLabel.setVisible(b);
	}

	private void setAngleUpdatesEnabled(boolean b) {
		basicTriangulation.setAngleUpdatesEnabled(b);
		detailedTriangulation.setAngleUpdatesEnabled(b);
		focusTriangulation.setAngleUpdatesEnabled(b);
		whenSizeModified.notifySubscribers(this);
	}

	private void updateOneDotTwentyPlusAAEnabled() {
		allAdvancements.updateOneDotTwentyPlusAAEnabled();
		whenSizeModified.notifySubscribers(this);
	}

	@Override
	public Dimension getPreferredSize() {
		if (dataState.allAdvancementsDataState().allAdvancementsModeEnabled().get()) {
			return allAdvancements.getPreferredSize();
		} else if (preferences.view.get() == MainViewType.BASIC) {
			return basicTriangulation.getPreferredSize();
		} else if (preferences.view.get() == MainViewType.FOCUS) {
			return focusTriangulation.getPreferredSize();
		} else {
			return detailedTriangulation.getPreferredSize();
		}
	}

	public boolean isIdle() {
		return idle;
	}

	@Override
	public void dispose() {
		super.dispose();
		detailedTriangulation.dispose();
		focusTriangulation.dispose();
		basicTriangulation.dispose();
	}

}
