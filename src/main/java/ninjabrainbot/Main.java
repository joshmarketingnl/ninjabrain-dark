package ninjabrainbot;

import java.util.Locale;
import java.util.Optional;

import ninjabrainbot.gui.GUI;
import ninjabrainbot.gui.splash.Progress;
import ninjabrainbot.gui.splash.Splash;
import ninjabrainbot.io.ErrorHandler;
import ninjabrainbot.io.KeyboardListener;
import ninjabrainbot.io.preferences.NinjabrainBotPreferences;
import ninjabrainbot.io.preferences.SavedPreferences;
import ninjabrainbot.model.datastate.statistics.ApproximatedDensity;
import ninjabrainbot.util.I18n;
import ninjabrainbot.util.Logger;
import ninjabrainbot.util.Profiler;

public class Main {

	public static final String VERSION = "1.5.2";

	/**
	 * Fork identity. One jar serves two apps: the shortcut passes
	 * {@code -Dninjabrain.edition=dark} for the dark one. Everything that could collide with the
	 * other edition, or with the original Ninjabrain Bot, is derived from this.
	 */
	public static final boolean DARK_EDITION = "dark".equalsIgnoreCase(System.getProperty("ninjabrain.edition", "light"));

	public static final String APP_NAME = DARK_EDITION ? "Ninjabrain Dark" : "Ninjabrain Light";
	public static final String PREFERENCES_NODE = DARK_EDITION ? "ninjabrainbot_dark" : "ninjabrainbot_light";
	public static final String SAVE_STATE_FILE = DARK_EDITION ? "NinjabrainDark-save-state.txt" : "NinjabrainLight-save-state.txt";
	public static final String OVERLAY_FILE = DARK_EDITION ? "nb-dark-overlay.png" : "nb-light-overlay.png";
	public static final String ICON = DARK_EDITION ? "/icon_dark.png" : "/icon.png";
	public static final int HTTP_PORT = DARK_EDITION ? 52535 : 52534;

	/** UID of the theme this edition starts with, see StandardThemes. */
	public static final int DEFAULT_THEME_UID = DARK_EDITION ? 12 : 11;

	public static void main(String[] args) {
		ErrorHandler errorHandler = new ErrorHandler();
		try {
			start();
		} catch (Exception e) {
			errorHandler.handleStartupException(e);
		}
	}

	/**
	 * Writes what the app actually loaded to a file next to the save state. javaw has no console,
	 * so this is the only way to see afterwards which settings a given launch picked up.
	 */
	private static void writeStartupDiagnostics(NinjabrainBotPreferences preferences) {
		try {
			java.io.File file = new java.io.File(System.getProperty("java.io.tmpdir"), "Ninjabrain-" + (DARK_EDITION ? "Dark" : "Light") + "-startup.log");
			StringBuilder sb = new StringBuilder();
			sb.append(new java.util.Date()).append(System.lineSeparator());
			sb.append("edition        : ").append(DARK_EDITION ? "dark" : "light").append(System.lineSeparator());
			sb.append("preferencesNode: ").append(PREFERENCES_NODE).append(System.lineSeparator());
			sb.append("jar            : ").append(Main.class.getProtectionDomain().getCodeSource().getLocation()).append(System.lineSeparator());
			sb.append("hotkey reset   : ").append(preferences.hotkeyReset.getCode()).append(System.lineSeparator());
			sb.append("hotkey incr    : ").append(preferences.hotkeyIncrement.getCode()).append(System.lineSeparator());
			sb.append("hotkey decr    : ").append(preferences.hotkeyDecrement.getCode()).append(System.lineSeparator());
			sb.append("hotkey undo    : ").append(preferences.hotkeyUndo.getCode()).append(System.lineSeparator());
			sb.append("hotkey redo    : ").append(preferences.hotkeyRedo.getCode()).append(System.lineSeparator());
			sb.append("sigma          : ").append(preferences.sigma.get()).append(System.lineSeparator());
			sb.append("sigmaBoat      : ").append(preferences.sigmaBoat.get()).append(System.lineSeparator());
			sb.append("usePreciseAngle: ").append(preferences.usePreciseAngle.get()).append(System.lineSeparator());
			sb.append("defaultBoatType: ").append(preferences.defaultBoatType.get()).append(System.lineSeparator());
			java.nio.file.Files.write(file.toPath(), sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
		} catch (Exception e) {
			Logger.log("Could not write startup diagnostics: " + e);
		}
	}

	private static void start() {
		boolean isSplashScreenDisabled = Optional.ofNullable(System.getenv("XDG_SESSION_TYPE")).isPresent(); // Splash screen does not work for linux (wayland) so we turn it off
		Progress.init(new Splash(isSplashScreenDisabled));
		Progress.setTask("Loading language", 0.02f);
		Profiler.start("Initialize language");
		Logger.log("Language: " + I18n.get("lang"));

		Progress.setTask("Loading preferences", 0.04f);
		Profiler.stopAndStart("Initialize preferences");
		ninjabrainbot.io.preferences.PreferencesBootstrap.inheritFromOriginalIfNew();
		NinjabrainBotPreferences preferences = new NinjabrainBotPreferences(new SavedPreferences());

		writeStartupDiagnostics(preferences);

		Progress.setTask("Calculating approximated stronghold density", 0.05f);
		Profiler.stopAndStart("Calculate approximated density");
		ApproximatedDensity.init();

		Progress.setTask("Starting keyboard listener", 0.08f);
		Profiler.stopAndStart("Register keyboard listener");
		KeyboardListener.preInit();

		System.setProperty("apple.awt.application.name", APP_NAME.replace(" ", ""));
		Progress.startCompoundTask("", 1f);
		Profiler.stopAndStart("Initialize GUI");
		Locale.setDefault(Locale.US);
		new GUI(preferences);
		Progress.endCompoundTask();

		Profiler.stop();
		Profiler.print();
	}
}
