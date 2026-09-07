package ninjabrainbot.io.preferences.enums;

import ninjabrainbot.io.preferences.IMultipleChoicePreferenceDataType;
import ninjabrainbot.util.I18n;

/** How the focus view arranges the five fields of the top stronghold prediction. */
public enum FocusLayoutType implements IMultipleChoicePreferenceDataType {

	/** All five side by side. Widest, one line. */
	ROW(I18n.get("focus_layout.row")),

	/** Location and nether on top, certainty, distance and angle below. */
	GRID(I18n.get("focus_layout.grid")),

	/** One field per line, caption left, value right. Narrowest. */
	STACKED(I18n.get("focus_layout.stacked"));

	final String name;

	FocusLayoutType(String name) {
		this.name = name;
	}

	@Override
	public String choiceName() {
		return name;
	}
}
