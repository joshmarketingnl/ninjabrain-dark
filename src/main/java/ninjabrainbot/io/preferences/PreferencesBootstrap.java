package ninjabrainbot.io.preferences;

import java.util.Arrays;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import ninjabrainbot.Main;
import ninjabrainbot.util.Logger;

/**
 * Gives a fresh edition the calibration of the original Ninjabrain Bot instead of factory defaults.
 * <p>
 * Standard deviation, boat sensitivity and hotkeys are measured once and then relied on for months;
 * starting a second copy from scratch silently produces worse triangulations and dead hotkeys, and
 * that is very hard to spot from the outside. So the first time this edition runs, it copies what
 * the original has. The original node is only ever read, never written.
 */
public class PreferencesBootstrap {

	/** Node the stock Ninjabrain Bot uses, Preferences.userNodeForPackage(ninjabrainbot.Main). */
	private static final String ORIGINAL_NODE = "ninjabrainbot";

	/** Anything that identifies this copy rather than describing how it calculates. */
	private static final List<String> NOT_INHERITED = Arrays.asList(
			"theme", "custom_themes", "custom_themes_names",
			"translucent", "blurred_background",
			"view", "focus_layout", "focus_text_scale", "show_throw_coordinates");

	/** Roughly the largest the window gets, used to keep the inherited position on one monitor. */
	private static final int WINDOW_WIDTH = 540;
	private static final int WINDOW_HEIGHT = 340;

	/**
	 * The original window may sit half over the edge between two monitors. The app rejects a
	 * position that is not fully on one screen and drops the window in the middle of the primary
	 * one, which is the last place a second copy should appear, so slide it back in instead.
	 */
	private static void nudgeWindowFullyOntoItsMonitor(Preferences target) {
		int x = target.getInt("window_x", Integer.MIN_VALUE);
		int y = target.getInt("window_y", Integer.MIN_VALUE);
		if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE)
			return;
		for (java.awt.GraphicsDevice device : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
			java.awt.Rectangle screen = device.getDefaultConfiguration().getBounds();
			if (!screen.contains(x, y))
				continue;
			int clampedX = Math.max(screen.x, Math.min(x, screen.x + screen.width - WINDOW_WIDTH));
			int clampedY = Math.max(screen.y, Math.min(y, screen.y + screen.height - WINDOW_HEIGHT));
			target.putInt("window_x", clampedX);
			target.putInt("window_y", clampedY);
			return;
		}
	}

	public static void inheritFromOriginalIfNew() {
		try {
			if (!Preferences.userRoot().nodeExists(ORIGINAL_NODE))
				return;
			Preferences target = Preferences.userRoot().node(Main.PREFERENCES_NODE);
			// Not keys().length: I18n has already written language_v2 into the node by this point.
			// settings_version is written the first time NinjabrainBotPreferences runs, so its
			// absence is what actually marks a node this edition has never used.
			if (target.get("settings_version", null) != null)
				return; // Already set up, never overwrite what the user has since changed.

			Preferences source = Preferences.userRoot().node(ORIGINAL_NODE);
			int copied = 0;
			for (String key : source.keys()) {
				if (NOT_INHERITED.contains(key))
					continue;
				String value = source.get(key, null);
				if (value == null)
					continue;
				target.put(key, value);
				copied++;
			}
			nudgeWindowFullyOntoItsMonitor(target);
			target.flush();
			Logger.log("First run of this edition, inherited " + copied + " settings from the original Ninjabrain Bot.");
		} catch (BackingStoreException | RuntimeException e) {
			Logger.log("Could not inherit settings from the original Ninjabrain Bot: " + e);
		}
	}

}
