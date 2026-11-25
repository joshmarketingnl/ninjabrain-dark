package ninjabrainbot.gui.mainwindow.triangulation;

import java.util.Locale;

import ninjabrainbot.gui.style.theme.CurrentTheme;
import ninjabrainbot.io.preferences.NinjabrainBotPreferences;
import ninjabrainbot.model.datastate.stronghold.Chunk;

final class ChunkCoordinates {

	private ChunkCoordinates() {
	}

	static String format(Chunk chunk, NinjabrainBotPreferences preferences, CurrentTheme theme) {
		switch (preferences.strongholdDisplayType.get()) {
			case FOURFOUR:
				return format(chunk.fourFourX(), chunk.fourFourZ(), preferences, theme);
			case EIGHTEIGHT:
				return format(chunk.eightEightX(), chunk.eightEightZ(), preferences, theme);
			default:
				return format(chunk.x, chunk.z, preferences, theme);
		}
	}

	static String format(int x, int z, NinjabrainBotPreferences preferences, CurrentTheme theme) {
		if (!preferences.colorCodeNegativeCoords.get())
			return String.format(Locale.US, "(%d, %d)", x, z);
		String negative = theme.COLOR_NEGATIVE.hex();
		String normal = theme.TEXT_COLOR_SLIGHTLY_WEAK.hex();
		return String.format(Locale.US, "<html>(<font color='%s'>%d</font>, <font color='%s'>%d</font>)</html>",
				x < 0 ? negative : normal, x, z < 0 ? negative : normal, z);
	}

}
