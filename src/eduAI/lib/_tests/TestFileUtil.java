package eduAI.lib._tests;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public final class TestFileUtil {
	private TestFileUtil() {
	}

	public static void deleteCreatedTempDirectory(
			Path aDirectory,
			String anExpectedPrefix) {
		if (aDirectory == null || !Files.exists(aDirectory)) {
			return;
		}
		if (anExpectedPrefix == null
				|| anExpectedPrefix.trim().isEmpty()) {
			throw new IllegalArgumentException(
					"Expected temp directory prefix must not be blank");
		}
		Path directory = aDirectory.toAbsolutePath().normalize();
		Path fileName = directory.getFileName();
		if (fileName == null
				|| !fileName.toString().startsWith(anExpectedPrefix)) {
			throw new IllegalArgumentException(
					"Refusing to delete non-test temp directory: "
							+ directory);
		}
		try (java.util.stream.Stream<Path> paths =
				Files.walk(directory)) {
			paths.sorted(Comparator.reverseOrder())
					.forEach(TestFileUtil::deletePath);
		} catch (IOException e) {
			throw new IllegalStateException(
					"Could not delete test temp directory: "
							+ directory,
					e);
		}
	}

	private static void deletePath(Path aPath) {
		try {
			Files.deleteIfExists(aPath);
		} catch (IOException e) {
			throw new IllegalStateException(
					"Could not delete test temp path: " + aPath,
					e);
		}
	}
}
