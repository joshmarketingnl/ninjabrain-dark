package ninjabrainbot;

import java.util.Locale;
import java.util.Optional;

import ninjabrainbot.gui.GUI;
import ninjabrainbot.gui.splash.Progress;
import ninjabrainbot.gui.splash.Splash;
import ninjabrainbot.io.ErrorHandler;
import ninjabrainbot.io.KeyboardListener;
import ninjabrainbot.io.preferences.NinjabrainBotPreferences;
import ninjabrainbot.model.datastate.statistics.ApproximatedDensity;
import ninjabrainbot.util.I18n;
import ninjabrainbot.util.Logger;
import ninjabrainbot.util.Profiler;

public class Main {

	public static final String VERSION = "1.5.2";

	private static final Edition EDITION = Edition.fromProperty();
	private static final String EDITION_ID = EDITION.name().toLowerCase(Locale.ROOT);

	public static final boolean FLIERP_EDITION = EDITION == Edition.FLIERP;
	public static final String APP_NAME = "Ninjabrain " + EDITION.displayName;
	public static final String PREFERENCES_NODE = "ninjabrainbot_" + EDITION_ID;
	public static final String SAVE_STATE_FILE = "Ninjabrain" + EDITION.displayName + "-save-state.txt";
	public static final String OVERLAY_FILE = "nb-" + EDITION_ID + "-overlay.png";
	public static final String ICON = EDITION.icon;
	public static final int HTTP_PORT = EDITION.httpPort;
	public static final int DEFAULT_THEME_UID = EDITION.defaultThemeUid;

	private enum Edition {
		LIGHT("Light", "/icon.png", 52534, 11),
		DARK("Dark", "/icon_dark.png", 52535, 12),
		FLIERP("Flierp", "/icon_dark.png", 52536, 12);

		private final String displayName;
		private final String icon;
		private final int httpPort;
		private final int defaultThemeUid;

		Edition(String displayName, String icon, int httpPort, int defaultThemeUid) {
			this.displayName = displayName;
			this.icon = icon;
			this.httpPort = httpPort;
			this.defaultThemeUid = defaultThemeUid;
		}

		private static Edition fromProperty() {
			String id = System.getProperty("ninjabrain.edition", "light");
			for (Edition edition : values()) {
				if (edition.name().equalsIgnoreCase(id))
					return edition;
			}
			return LIGHT;
		}
	}

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
	private static void writeStartupDiagnostics(NinjabrainBotPreferences preferences, ninjabrainbot.io.preferences.FilePreferences source) {
		try {
			java.io.File file = new java.io.File(System.getProperty("java.io.tmpdir"), "Ninjabrain-" + EDITION.displayName + "-startup.log");
			StringBuilder sb = new StringBuilder();
			sb.append(new java.util.Date()).append(System.lineSeparator());
			sb.append("edition        : ").append(EDITION_ID).append(System.lineSeparator());
			sb.append("settingsFile   : ").append(source.getFile()).append(System.lineSeparator());
			sb.append("settingsCount  : ").append(source.size()).append(System.lineSeparator());
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
		ninjabrainbot.io.preferences.FilePreferences preferenceSource = new ninjabrainbot.io.preferences.FilePreferences();
		NinjabrainBotPreferences preferences = new NinjabrainBotPreferences(preferenceSource);

		writeStartupDiagnostics(preferences, preferenceSource);

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
