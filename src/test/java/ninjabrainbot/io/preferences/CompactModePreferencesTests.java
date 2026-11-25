package ninjabrainbot.io.preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import ninjabrainbot.io.preferences.enums.FocusLayoutType;
import ninjabrainbot.io.preferences.enums.MainViewType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class CompactModePreferencesTests {

	@TempDir
	Path appData;

	@Test
	void compactModeIsOffForExistingSettingsAndPersistsSeparatelyPerEdition() throws Exception {
		seedExistingSettings("NinjabrainDark");
		seedExistingSettings("NinjabrainLight");

		runEdition("dark", false, true);
		runEdition("light", false, true);
		runEdition("dark", true, false);
		runEdition("light", true, false);
	}

	private void seedExistingSettings(String edition) throws Exception {
		Path settings = Files.createDirectory(appData.resolve(edition)).resolve("settings.properties");
		Files.write(settings, "settings_version=1\nlanguage_v2=en-US\nview=2\nfocus_layout=2\n".getBytes(StandardCharsets.US_ASCII));
	}

	private void runEdition(String edition, boolean expected, boolean newValue) throws Exception {
		Path log = appData.resolve(edition + ".log");
		String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
		String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
		ProcessBuilder builder = new ProcessBuilder(java, "-Djava.awt.headless=true", "-Dninjabrain.edition=" + edition,
				"-cp", classPath, getClass().getName(), Boolean.toString(expected), Boolean.toString(newValue));
		builder.environment().put("APPDATA", appData.toString());
		Process process = builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
		try {
			assertTrue(process.waitFor(15, TimeUnit.SECONDS), edition + " settings check timed out");
			assertEquals(0, process.exitValue(), new String(Files.readAllBytes(log), StandardCharsets.UTF_8));
		} finally {
			process.destroyForcibly();
		}
	}

	public static void main(String[] args) {
		NinjabrainBotPreferences preferences = new NinjabrainBotPreferences(new FilePreferences());
		assertFalse(preferences.hasSavedWindowPosition());
		assertEquals(Boolean.parseBoolean(args[0]), preferences.compactMode.get());
		boolean newValue = Boolean.parseBoolean(args[1]);
		preferences.compactMode.set(newValue);

		NinjabrainBotPreferences reloaded = new NinjabrainBotPreferences(new FilePreferences());
		assertEquals(newValue, reloaded.compactMode.get());
		assertSame(MainViewType.FOCUS, reloaded.view.get());
		assertSame(FocusLayoutType.STACKED, reloaded.focusLayout.get());
	}
}
