package ninjabrainbot.io.preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import ninjabrainbot.Main;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FlierpPreferencesTests {

	@TempDir
	Path appData;

	@Test
	void flierpUsesItsOwnDefaultsAndPreservesUserChanges() throws Exception {
		String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
		String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
		Path log = appData.resolve("flierp.log");
		ProcessBuilder builder = new ProcessBuilder(java, "-Djava.awt.headless=true", "-Dninjabrain.edition=flierp",
				"-cp", classPath, getClass().getName(), appData.toString());
		builder.environment().put("APPDATA", appData.toString());
		Process process = builder.redirectErrorStream(true).redirectOutput(log.toFile()).start();
		try {
			assertTrue(process.waitFor(15, TimeUnit.SECONDS), "Flierp settings check timed out");
			assertEquals(0, process.exitValue(), new String(Files.readAllBytes(log), StandardCharsets.UTF_8));
		} finally {
			process.destroyForcibly();
		}
		assertTrue(Files.isRegularFile(appData.resolve("NinjabrainFlierp").resolve("settings.properties")));
		assertFalse(Files.exists(appData.resolve("NinjabrainDark")));
		assertFalse(Files.exists(appData.resolve("NinjabrainLight")));
	}

	public static void main(String[] args) {
		assertEquals("Ninjabrain Flierp", Main.APP_NAME);
		assertEquals("NinjabrainFlierp-save-state.txt", Main.SAVE_STATE_FILE);
		assertEquals("nb-flierp-overlay.png", Main.OVERLAY_FILE);
		FilePreferences source = new FilePreferences();
		assertEquals(Paths.get(args[0], "NinjabrainFlierp", "settings.properties"), source.getFile());
		NinjabrainBotPreferences preferences = new NinjabrainBotPreferences(source);
		assertEquals(12, preferences.theme.get()); // Original dark
		assertTrue(preferences.compactMode.get());
		assertTrue(preferences.useOverlay.get());
		assertTrue(preferences.alwaysOnTop.get());

		preferences.theme.set(11);
		preferences.compactMode.set(false);
		preferences.useOverlay.set(false);
		preferences.alwaysOnTop.set(false);

		NinjabrainBotPreferences reloaded = new NinjabrainBotPreferences(new FilePreferences());
		assertEquals(11, reloaded.theme.get());
		assertFalse(reloaded.compactMode.get());
		assertFalse(reloaded.useOverlay.get());
		assertFalse(reloaded.alwaysOnTop.get());
	}
}
