package ninjabrainbot.io;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import ninjabrainbot.Main;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LanguageResourcesTest {

	@Test
	void allLanguagesAreRegistered() throws URISyntaxException {
		URL url = Main.class.getResource("/lang");
		File[] languageFiles = new File(Objects.requireNonNull(url).toURI()).listFiles();
		List<String> expectedLanguageFileNames = Arrays
				.stream(Objects.requireNonNull(languageFiles))
				.map(File::getName)
				.filter(name -> !name.contentEquals("I18n.properties"))
				.collect(Collectors.toList());
		List<String> actualLanguageFileNames = LanguageResources.getAllLanguageResourceNames();
		expectedLanguageFileNames.sort(Comparator.comparing(String::toString));
		actualLanguageFileNames.sort(Comparator.comparing(String::toString));
		Assertions.assertIterableEquals(expectedLanguageFileNames, actualLanguageFileNames);
	}

}
