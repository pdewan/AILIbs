package eduAI.trace.processors.mutations;

import eduAI.trace.processors.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import eduAI.trace.TraceLine;
import eduAI.trace.TraceLineParser;

public class TraceFixtureMutator {
	public enum MutationKind {
		REMOVE_EVENT,
		CHANGE_FIELD_VALUE,
		COPY_FIELD_VALUE,
		REMOVE_SOURCE_CLASS
	}

	public record Mutation(
			MutationKind kind,
			String eventName,
			String fieldName,
			String sourceFieldName,
			String provider,
			String fileName,
			Integer occurrence) {
		public Mutation {
			if (kind == null || isBlank(eventName)) {
				throw new IllegalArgumentException(
						"Mutation kind and event name are required");
			}
			if (kind == MutationKind.CHANGE_FIELD_VALUE && isBlank(fieldName)) {
				throw new IllegalArgumentException(
						"A field name is required for CHANGE_FIELD_VALUE");
			}
			if (kind == MutationKind.COPY_FIELD_VALUE
					&& (isBlank(fieldName) || isBlank(sourceFieldName))) {
				throw new IllegalArgumentException(
						"Target and source field names are required for COPY_FIELD_VALUE");
			}
			if (occurrence != null && occurrence < 1) {
				throw new IllegalArgumentException(
						"Occurrence numbers start at 1");
			}
		}

		public static Mutation removeEvent(String anEventName) {
			return new Mutation(
					MutationKind.REMOVE_EVENT,
					anEventName,
					null,
					null,
					null,
					null,
					null);
		}

		public static Mutation wrongFieldValue(
				String anEventName,
				String aFieldName) {
			return new Mutation(
					MutationKind.CHANGE_FIELD_VALUE,
					anEventName,
					aFieldName,
					null,
					null,
					null,
					null);
		}

		public static Mutation copyFieldValue(
				String anEventName,
				String aSourceFieldName,
				String aTargetFieldName) {
			return new Mutation(
					MutationKind.COPY_FIELD_VALUE,
					anEventName,
					aTargetFieldName,
					aSourceFieldName,
					null,
					null,
					null);
		}

		public static Mutation removeSourceClass(String anEventName) {
			return new Mutation(
					MutationKind.REMOVE_SOURCE_CLASS,
					anEventName,
					null,
					null,
					null,
					null,
					null);
		}

		public Mutation forProvider(String aProvider) {
			return new Mutation(
					kind,
					eventName,
					fieldName,
					sourceFieldName,
					aProvider,
					fileName,
					occurrence);
		}

		public Mutation inFile(String aFileName) {
			return new Mutation(
					kind,
					eventName,
					fieldName,
					sourceFieldName,
					provider,
					aFileName,
					occurrence);
		}

		public Mutation atOccurrence(int anOccurrence) {
			return new Mutation(
					kind,
					eventName,
					fieldName,
					sourceFieldName,
					provider,
					fileName,
					anOccurrence);
		}

		private String directoryToken() {
			StringBuilder result = new StringBuilder(switch (kind) {
				case REMOVE_EVENT -> "remove_event_";
				case CHANGE_FIELD_VALUE -> "wrong_field_";
				case COPY_FIELD_VALUE -> "copy_field_";
				case REMOVE_SOURCE_CLASS -> "remove_source_class_";
			});
			result.append(sanitize(eventName));
			if (kind == MutationKind.CHANGE_FIELD_VALUE) {
				result.append('_').append(sanitize(fieldName));
			}
			if (kind == MutationKind.COPY_FIELD_VALUE) {
				result.append('_')
						.append(sanitize(sourceFieldName))
						.append("_to_")
						.append(sanitize(fieldName));
			}
			if (!isBlank(provider)) {
				result.append("_provider_").append(sanitize(provider));
			}
			if (!isBlank(fileName)) {
				result.append("_file_").append(sanitize(fileName));
			}
			if (occurrence != null) {
				result.append("_occurrence_").append(occurrence);
			}
			return result.toString();
		}
	}

	public record MutationResult(
			Path directory,
			Map<Mutation, Integer> changedLineCounts) {
	}

	public MutationResult createBrokenDataset(
			String aSourceDirectory,
			String aTargetRoot,
			Mutation... someMutations) throws IOException {
		return createBrokenDataset(
				Path.of(aSourceDirectory),
				Path.of(aTargetRoot),
				List.of(someMutations));
	}

	public MutationResult createBrokenDataset(
			Path aSourceDirectory,
			Path aTargetRoot,
			List<Mutation> someMutations) throws IOException {
		if (someMutations == null || someMutations.isEmpty()) {
			throw new IllegalArgumentException(
					"At least one mutation is required");
		}
		Path targetDirectory = aTargetRoot.resolve(
				directoryName(someMutations));
		Files.createDirectories(targetDirectory);

		Map<Mutation, Integer> matchingOccurrences = new HashMap<>();
		Map<Mutation, Integer> changedLines = new HashMap<>();
		for (Mutation mutation : someMutations) {
			matchingOccurrences.put(mutation, 0);
			changedLines.put(mutation, 0);
		}

		for (String fileName : TraceFixtureFilter.TRACE_FILE_NAMES) {
			Path source = aSourceDirectory.resolve(fileName);
			if (!Files.isRegularFile(source)) {
				throw new IOException("Required trace file is missing: " + source);
			}
			List<String> result = new ArrayList<>();
			for (String originalLine : Files.readAllLines(
					source,
					StandardCharsets.UTF_8)) {
				String line = originalLine;
				boolean removed = false;
				for (Mutation mutation : someMutations) {
					if (!matches(
							line,
							fileName,
							mutation,
							matchingOccurrences)) {
						continue;
					}
					if (mutation.kind() == MutationKind.REMOVE_EVENT) {
						removed = true;
						changedLines.compute(mutation, (key, value) -> value + 1);
						break;
					}
					String changed = switch (mutation.kind()) {
						case CHANGE_FIELD_VALUE -> replaceTopLevelFieldValue(
								line,
								mutation.fieldName());
						case COPY_FIELD_VALUE -> copyTopLevelFieldValue(
								line,
								mutation.sourceFieldName(),
								mutation.fieldName());
						case REMOVE_SOURCE_CLASS -> removeSourceClass(line);
						case REMOVE_EVENT -> line;
					};
					if (!changed.equals(line)) {
						line = changed;
						changedLines.compute(mutation, (key, value) -> value + 1);
					}
				}
				if (!removed) {
					result.add(line);
				}
			}
			Files.write(
					targetDirectory.resolve(fileName),
					result,
					StandardCharsets.UTF_8);
		}

		for (Mutation mutation : someMutations) {
			if (changedLines.get(mutation) == 0) {
				throw new IOException(
						"Mutation changed no trace lines: " + mutation);
			}
		}
		return new MutationResult(
				targetDirectory,
				Map.copyOf(changedLines));
	}

	public String directoryName(List<Mutation> someMutations) {
		return someMutations.stream()
				.map(Mutation::directoryToken)
				.reduce((first, second) -> first + "__" + second)
				.orElseThrow();
	}

	private boolean matches(
			String aLine,
			String aFileName,
			Mutation aMutation,
			Map<Mutation, Integer> matchingOccurrences) {
		if (!isBlank(aMutation.fileName())
				&& !aMutation.fileName().equals(aFileName)) {
			return false;
		}
		TraceLine traceLine = TraceLineParser.parse(aLine);
		if (traceLine == null
				|| !aMutation.eventName().equals(traceLine.getEventName())) {
			return false;
		}
		if (!isBlank(aMutation.provider())) {
			String provider = traceLine.getAuxiliaryData().get("provider");
			if (!aMutation.provider().equals(provider)) {
				return false;
			}
		}
		int occurrence = matchingOccurrences.compute(
				aMutation,
				(key, value) -> value + 1);
		return aMutation.occurrence() == null
				|| aMutation.occurrence() == occurrence;
	}

	private String replaceTopLevelFieldValue(
			String aLine,
			String aFieldName) {
		int dataStart = aLine.indexOf(" <");
		int dataEnd = aLine.lastIndexOf("> ##");
		if (dataStart < 0 || dataEnd < dataStart) {
			return aLine;
		}
		String prefix = aLine.substring(0, dataStart + 2);
		String data = aLine.substring(dataStart + 2, dataEnd);
		String suffix = aLine.substring(dataEnd);
		List<String> fields = splitTopLevelFields(data);
		List<String> changedFields = new ArrayList<>();
		boolean found = false;
		for (String field : fields) {
			int equals = field.indexOf('=');
			String name = equals < 0 ? field : field.substring(0, equals);
			if (aFieldName.equals(name.trim())) {
				found = true;
				changedFields.add(name + "=\"incorrect\"");
			} else {
				changedFields.add(field);
			}
		}
		return found
				? prefix + String.join(" ", changedFields) + suffix
				: aLine;
	}

	private String copyTopLevelFieldValue(
			String aLine,
			String aSourceFieldName,
			String aTargetFieldName) {
		int dataStart = aLine.indexOf(" <");
		int dataEnd = aLine.lastIndexOf("> ##");
		if (dataStart < 0 || dataEnd < dataStart) {
			return aLine;
		}
		String prefix = aLine.substring(0, dataStart + 2);
		String data = aLine.substring(dataStart + 2, dataEnd);
		String suffix = aLine.substring(dataEnd);
		List<String> fields = splitTopLevelFields(data);
		String sourceValue = null;
		for (String field : fields) {
			int equals = field.indexOf('=');
			if (equals >= 0
					&& aSourceFieldName.equals(
							field.substring(0, equals).trim())) {
				sourceValue = field.substring(equals + 1);
				break;
			}
		}
		if (sourceValue == null) {
			return aLine;
		}
		List<String> changedFields = new ArrayList<>();
		boolean foundTarget = false;
		for (String field : fields) {
			int equals = field.indexOf('=');
			String name = equals < 0 ? field : field.substring(0, equals);
			if (aTargetFieldName.equals(name.trim())) {
				foundTarget = true;
				changedFields.add(name + "=" + sourceValue);
			} else {
				changedFields.add(field);
			}
		}
		return foundTarget
				? prefix + String.join(" ", changedFields) + suffix
				: aLine;
	}

	private String removeSourceClass(String aLine) {
		int dataStart = aLine.indexOf(" <");
		int patternEnd = dataStart >= 0 ? dataStart : aLine.length();
		int close = aLine.lastIndexOf(')', patternEnd);
		int open = close < 0 ? -1 : aLine.lastIndexOf('(', close);
		int colon = open < 0 ? -1 : aLine.indexOf(':', open);
		if (open < 0 || colon < 0 || colon > close) {
			return aLine;
		}
		return aLine.substring(0, colon + 1)
				+ " "
				+ aLine.substring(close);
	}

	private List<String> splitTopLevelFields(String aData) {
		List<String> result = new ArrayList<>();
		int start = 0;
		boolean quoted = false;
		boolean escaped = false;
		for (int index = 0; index < aData.length(); index++) {
			char character = aData.charAt(index);
			if (escaped) {
				escaped = false;
				continue;
			}
			if (character == '\\' && quoted) {
				escaped = true;
				continue;
			}
			if (character == '"') {
				quoted = !quoted;
				continue;
			}
			if (!quoted && character == ' ') {
				result.add(aData.substring(start, index));
				start = index + 1;
			}
		}
		result.add(aData.substring(start));
		result.removeIf(String::isBlank);
		return result;
	}

	private static boolean isBlank(String aValue) {
		return aValue == null || aValue.isBlank();
	}

	private static String sanitize(String aValue) {
		return aValue.toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9]+", "_")
				.replaceAll("^_+|_+$", "");
	}

	public static void main(String[] args) throws IOException {
		if (args.length < 3) {
			throw new IllegalArgumentException(
					"Usage: <sourceDirectory> <targetRoot> "
							+ "event:<event> | field:<event>:<field> "
							+ "| copy:<event>:<sourceField>:<targetField> "
							+ "| source:<event> [...]");
		}
		List<Mutation> mutations = new ArrayList<>();
		for (int index = 2; index < args.length; index++) {
			String[] parts = args[index].split(":", -1);
			if (parts.length == 2 && "event".equals(parts[0])) {
				mutations.add(Mutation.removeEvent(parts[1]));
			} else if (parts.length == 2 && "source".equals(parts[0])) {
				mutations.add(Mutation.removeSourceClass(parts[1]));
			} else if (parts.length == 3 && "field".equals(parts[0])) {
				mutations.add(Mutation.wrongFieldValue(parts[1], parts[2]));
			} else if (parts.length == 4 && "copy".equals(parts[0])) {
				mutations.add(
						Mutation.copyFieldValue(
								parts[1], parts[2], parts[3]));
			} else {
				throw new IllegalArgumentException(
						"Invalid mutation: " + args[index]);
			}
		}
		MutationResult result = new TraceFixtureMutator()
				.createBrokenDataset(
						Path.of(args[0]),
						Path.of(args[1]),
						mutations);
		System.out.println(
				"Broken trace dataset created: "
						+ result.directory()
						+ " changes="
						+ result.changedLineCounts());
	}
}
