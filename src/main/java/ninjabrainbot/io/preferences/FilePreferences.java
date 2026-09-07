package ninjabrainbot.io.preferences;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.prefs.Preferences;

import ninjabrainbot.Main;
import ninjabrainbot.util.Logger;

/**
 * Settings in a plain properties file instead of java.util.prefs.
 * <p>
 * The registry backed Preferences API fails silently: when it cannot read its node it hands back
 * the default for every key, so the app starts with no hotkeys and a standard deviation of 0.1
 * instead of the measured one, and looks completely normal while doing it. That is impossible to
 * spot from the interface and it cost a whole evening. A file we open ourselves either works or
 * throws.
 * <p>
 * The first time the file is created it is seeded from the original Ninjabrain Bot's registry
 * node, so a new copy starts with the same calibration rather than factory defaults.
 */
public class FilePreferences implements IPreferenceSource {

	/** Node the stock Ninjabrain Bot uses, read only, never written. */
	private static final String ORIGINAL_NODE = "ninjabrainbot";

	/** Settings that describe this copy rather than how it calculates. */
	private static final java.util.List<String> NOT_INHERITED = java.util.Arrays.asList(
			"theme", "custom_themes", "custom_themes_names",
			"translucent", "blurred_background",
			"view", "focus_layout", "focus_text_scale", "show_throw_coordinates");

	private final Path file;
	private final Properties properties = new Properties();

	public FilePreferences() {
		file = settingsFile();
		load();
		// Not isEmpty: I18n writes the language into the file before this runs.
		if (properties.getProperty("settings_version") == null) {
			int inherited = inheritFromOriginal();
			Logger.log("No settings file yet, inherited " + inherited + " settings from the original Ninjabrain Bot.");
			save();
		}
	}

	public static Path settingsFile() {
		String appData = System.getenv("APPDATA");
		Path directory = appData != null ? Paths.get(appData, Main.APP_NAME.replace(" ", "")) : Paths.get(System.getProperty("user.home"), "." + Main.PREFERENCES_NODE);
		return directory.resolve("settings.properties");
	}

	/** Where the settings ended up, for the startup diagnostics. */
	public Path getFile() {
		return file;
	}

	public int size() {
		return properties.size();
	}

	private void load() {
		if (!Files.exists(file))
			return;
		try (InputStream in = Files.newInputStream(file)) {
			properties.load(in);
		} catch (IOException e) {
			Logger.log("Could not read " + file + ": " + e);
		}
	}

	private synchronized void save() {
		try {
			Files.createDirectories(file.getParent());
			try (OutputStream out = Files.newOutputStream(file)) {
				properties.store(out, Main.APP_NAME + " settings");
			}
		} catch (IOException e) {
			Logger.log("Could not write " + file + ": " + e);
		}
	}

	private int inheritFromOriginal() {
		try {
			if (!Preferences.userRoot().nodeExists(ORIGINAL_NODE))
				return 0;
			Preferences source = Preferences.userRoot().node(ORIGINAL_NODE);
			int copied = 0;
			for (String key : source.keys()) {
				if (NOT_INHERITED.contains(key))
					continue;
				String value = source.get(key, null);
				if (value == null)
					continue;
				properties.setProperty(key, value);
				copied++;
			}
			nudgeWindowFullyOntoItsMonitor();
			return copied;
		} catch (Exception e) {
			Logger.log("Could not inherit settings from the original Ninjabrain Bot: " + e);
			return 0;
		}
	}

	/** Roughly the largest the window gets, used to keep the inherited position on one monitor. */
	private static final int WINDOW_WIDTH = 540;
	private static final int WINDOW_HEIGHT = 340;

	/**
	 * The original window may straddle two monitors. A position that is not wholly on one screen
	 * makes the app drop the window in the middle of the primary one, so slide it back in.
	 */
	private void nudgeWindowFullyOntoItsMonitor() {
		try {
			int x = Integer.parseInt(properties.getProperty("window_x", "no"));
			int y = Integer.parseInt(properties.getProperty("window_y", "no"));
			for (java.awt.GraphicsDevice device : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
				java.awt.Rectangle screen = device.getDefaultConfiguration().getBounds();
				if (!screen.contains(x, y))
					continue;
				properties.setProperty("window_x", Integer.toString(Math.max(screen.x, Math.min(x, screen.x + screen.width - WINDOW_WIDTH))));
				properties.setProperty("window_y", Integer.toString(Math.max(screen.y, Math.min(y, screen.y + screen.height - WINDOW_HEIGHT))));
				return;
			}
		} catch (RuntimeException ignored) {
			// No usable position inherited, the app will pick its own.
		}
	}

	/** Standalone read, for code that runs before the preference object exists (I18n). */
	public static String readSetting(String key, String defaultValue) {
		Properties standalone = new Properties();
		Path path = settingsFile();
		if (Files.exists(path)) {
			try (InputStream in = Files.newInputStream(path)) {
				standalone.load(in);
			} catch (IOException ignored) {
			}
		}
		return standalone.getProperty(key, defaultValue);
	}

	public static void writeSetting(String key, String value) {
		Properties standalone = new Properties();
		Path path = settingsFile();
		if (Files.exists(path)) {
			try (InputStream in = Files.newInputStream(path)) {
				standalone.load(in);
			} catch (IOException ignored) {
			}
		}
		standalone.setProperty(key, value);
		try {
			Files.createDirectories(path.getParent());
			try (OutputStream out = Files.newOutputStream(path)) {
				standalone.store(out, Main.APP_NAME + " settings");
			}
		} catch (IOException e) {
			Logger.log("Could not write " + path + ": " + e);
		}
	}

	private String get(String key) {
		return properties.getProperty(key);
	}

	private synchronized void put(String key, String value) {
		properties.setProperty(key, value);
		save();
	}

	@Override
	public int getInt(String key, int defaultValue) {
		try {
			String value = get(key);
			return value == null ? defaultValue : Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	@Override
	public void putInt(String key, int value) {
		put(key, Integer.toString(value));
	}

	@Override
	public float getFloat(String key, float defaultValue) {
		try {
			String value = get(key);
			return value == null ? defaultValue : Float.parseFloat(value.trim());
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	@Override
	public void putFloat(String key, float value) {
		put(key, Float.toString(value));
	}

	@Override
	public double getDouble(String key, double defaultValue) {
		try {
			String value = get(key);
			return value == null ? defaultValue : Double.parseDouble(value.trim());
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	@Override
	public void putDouble(String key, double value) {
		put(key, Double.toString(value));
	}

	@Override
	public boolean getBoolean(String key, boolean defaultValue) {
		String value = get(key);
		return value == null ? defaultValue : Boolean.parseBoolean(value.trim());
	}

	@Override
	public void putBoolean(String key, boolean value) {
		put(key, Boolean.toString(value));
	}

	@Override
	public String getString(String key, String defaultValue) {
		String value = get(key);
		return value == null ? defaultValue : value;
	}

	@Override
	public void putString(String key, String value) {
		put(key, value);
	}

}
