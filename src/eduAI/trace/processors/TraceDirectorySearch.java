package eduAI.trace.processors;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Finds a trace file locally first, then in the nearest eligible descendant directory. */
final class TraceDirectorySearch {
	private TraceDirectorySearch() { }

	static Path find(Path root, List<String> names) {
		// A launch from bin still gives files in that directory first priority.
		Path localRoot = root;
		if (names.stream().anyMatch(name -> Files.isRegularFile(localRoot.resolve(name), LinkOption.NOFOLLOW_LINKS))) {
			return root;
		}
		Path absoluteRoot = root.toAbsolutePath().normalize();
		if (absoluteRoot.getFileName() != null
				&& absoluteRoot.getFileName().toString().equalsIgnoreCase("bin")) {
			root = absoluteRoot.getParent();
		}
		List<Path> level = List.of(root);
		try {
			while (!level.isEmpty()) {
				List<Path> candidates = level.stream().filter(dir -> names.stream()
						.anyMatch(name -> Files.isRegularFile(dir.resolve(name), LinkOption.NOFOLLOW_LINKS)))
						.sorted(Comparator.comparing(Path::toString)).toList();
				if (candidates.size() == 1) return candidates.get(0);
				if (candidates.size() > 1) throw new IllegalArgumentException(
						"Multiple trace directories found: " + candidates + ". Select the intended trace directory explicitly.");
				List<Path> next = new ArrayList<>();
				for (Path dir : level) {
					if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) continue;
					try (var children = Files.list(dir)) {
						children.filter(child -> Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS))
								.filter(child -> !child.getFileName().toString().equalsIgnoreCase("src")
										&& !child.getFileName().toString().equalsIgnoreCase("bin"))
								.forEach(next::add);
					}
				}
				level = next;
			}
		} catch (IOException e) {
			throw new UncheckedIOException("Unable to search for traces below " + root, e);
		}
		return root; // Preserve the existing missing-file diagnostics.
	}
}
