package eduAI.trace.processors.mutations;

import eduAI.trace.processors.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eduAI.trace.TraceLine;
import eduAI.trace.TraceLineParser;

public class ImageComparisonBrokenTraceFixturesGenerator {
	public static final Path DEFAULT_SOURCE = Path.of("correct_filtered");
	public static final Path DEFAULT_TARGET = Path.of("image_comparison_broken");
	private static final Pattern IMAGE_SUMMARY = Pattern.compile(
			"imageBytes\\(count=(\\d+),prefix=([0-9a-f]*),suffix=([0-9a-f]*)\\)");

	private enum ImageField {
		COUNT,
		PREFIX,
		SUFFIX
	}

	public List<Path> generate(Path aSource, Path aTarget)
			throws IOException {
		List<Path> result = new ArrayList<>();
		for (String provider : List.of("Gemini", "Ollama")) {
			for (ImageField field : ImageField.values()) {
				result.add(generate(aSource, aTarget, provider, field));
			}
		}
		return List.copyOf(result);
	}

	private Path generate(
			Path aSource,
			Path aTarget,
			String aProvider,
			ImageField aField) throws IOException {
		Path directory = aTarget.resolve(
				aProvider.toLowerCase() + "_wrong_image_"
						+ aField.name().toLowerCase());
		Files.createDirectories(directory);
		String targetFile = "Gemini".equals(aProvider)
				? "TraceGeminiBridgeNonStreamingDemo.txt"
				: "TraceOllamaNonStreamingBridgeDemo.txt";
		boolean changed = false;
		for (String fileName : TraceFixtureFilter.TRACE_FILE_NAMES) {
			List<String> lines = Files.readAllLines(
					aSource.resolve(fileName),
					StandardCharsets.UTF_8);
			if (fileName.equals(targetFile)) {
				for (int i = 0; i < lines.size(); i++) {
					TraceLine traceLine = TraceLineParser.parse(lines.get(i));
					if (traceLine != null
							&& "request_sent".equals(traceLine.getEventName())) {
						String mutated = mutateProviderImage(lines.get(i), aField);
						if (!mutated.equals(lines.get(i))) {
							lines.set(i, mutated);
							changed = true;
						}
					}
				}
			}
			Files.write(
					directory.resolve(fileName),
					lines,
					StandardCharsets.UTF_8);
		}
		if (!changed) {
			throw new IOException(
					"No nonempty provider image summary found for "
							+ aProvider + " " + aField);
		}
		return directory;
	}

	private String mutateProviderImage(String aLine, ImageField aField) {
		int fieldStart = aLine.indexOf("providerDependentContextWindow=");
		int fieldEnd = aLine.indexOf(
				" providerDependentConfiguration=", fieldStart);
		if (fieldStart < 0 || fieldEnd < 0) {
			return aLine;
		}
		String field = aLine.substring(fieldStart, fieldEnd);
		Matcher matcher = IMAGE_SUMMARY.matcher(field);
		while (matcher.find()) {
			int count = Integer.parseInt(matcher.group(1));
			if (count == 0) {
				continue;
			}
			String replacement = switch (aField) {
				case COUNT -> "imageBytes(count=" + (count + 1)
						+ ",prefix=" + matcher.group(2)
						+ ",suffix=" + matcher.group(3) + ")";
				case PREFIX -> "imageBytes(count=" + count
						+ ",prefix=" + changedHex(matcher.group(2))
						+ ",suffix=" + matcher.group(3) + ")";
				case SUFFIX -> "imageBytes(count=" + count
						+ ",prefix=" + matcher.group(2)
						+ ",suffix=" + changedHex(matcher.group(3)) + ")";
			};
			String changedField = field.substring(0, matcher.start())
					+ replacement + field.substring(matcher.end());
			return aLine.substring(0, fieldStart)
					+ changedField + aLine.substring(fieldEnd);
		}
		return aLine;
	}

	private String changedHex(String aHexValue) {
		if (aHexValue == null || aHexValue.isEmpty()) {
			return "ff";
		}
		char replacement = aHexValue.charAt(0) == '0' ? 'f' : '0';
		return replacement + aHexValue.substring(1);
	}

	public static void main(String[] args) throws IOException {
		Path source = args.length > 0 ? Path.of(args[0]) : DEFAULT_SOURCE;
		Path target = args.length > 1 ? Path.of(args[1]) : DEFAULT_TARGET;
		for (Path directory :
				new ImageComparisonBrokenTraceFixturesGenerator()
						.generate(source, target)) {
			System.out.println(directory);
		}
	}
}
