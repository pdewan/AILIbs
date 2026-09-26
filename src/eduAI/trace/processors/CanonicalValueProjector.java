package eduAI.trace.processors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import eduAI.trace.processors.GenericTracesFileProcessor.TraceMessage;
import eduAI.trace.processors.GenericTracesFileProcessor.TracePart;

public class CanonicalValueProjector {
	public List<CanonicalValueOccurrence> projectContextWindow(
			CanonicalTraceRequest aRequest) {
		return aRequest == null
				? List.of()
				: projectMessages(aRequest.contextWindow(), "contextWindow");
	}

	public List<CanonicalValueOccurrence> projectContextWindowText(
			CanonicalTraceRequest aRequest) {
		ArrayList<CanonicalValueOccurrence> result = new ArrayList<>();
		for (CanonicalValueOccurrence occurrence : projectContextWindow(aRequest)) {
			if (occurrence.canonicalValue().kind()
					!= CanonicalValueKind.IMAGE_DATA) {
				result.add(occurrence);
			}
		}
		return List.copyOf(result);
	}

	public List<CanonicalValueOccurrence> projectParameters(
			CanonicalTraceRequest aRequest) {
		return aRequest == null
				? List.of()
				: projectProperties(aRequest.parameters(), "parameters");
	}

	public List<CanonicalValueOccurrence> projectResponseContent(
			CanonicalTraceResponse aResponse) {
		return aResponse == null
				? List.of()
				: projectMessages(aResponse.messages(), "messages");
	}

	public List<CanonicalValueOccurrence> projectMetadata(
			CanonicalTraceResponse aResponse) {
		return aResponse == null
				? List.of()
				: projectProperties(aResponse.metadata(), "metadata");
	}

	public List<CanonicalValueOccurrence> withoutIgnoredValues(
			List<CanonicalValueOccurrence> someValues,
			Predicate<CanonicalValue> anIgnorePredicate) {
		if (someValues == null || someValues.isEmpty()) {
			return List.of();
		}
		if (anIgnorePredicate == null) {
			return List.copyOf(someValues);
		}
		ArrayList<CanonicalValueOccurrence> result = new ArrayList<>();
		for (CanonicalValueOccurrence occurrence : someValues) {
			if (!anIgnorePredicate.test(occurrence.canonicalValue())) {
				result.add(occurrence);
			}
		}
		return List.copyOf(result);
	}

	private List<CanonicalValueOccurrence> projectMessages(
			List<TraceMessage> someMessages,
			String aRootLocation) {
		if (someMessages == null || someMessages.isEmpty()) {
			return List.of();
		}
		ArrayList<CanonicalValueOccurrence> result = new ArrayList<>();
		for (int messageIndex = 0;
				messageIndex < someMessages.size();
				messageIndex++) {
			TraceMessage message = someMessages.get(messageIndex);
			if (message == null) {
				continue;
			}
			String messageLocation =
					aRootLocation + "[" + messageIndex + "]";
			add(
					result,
					CanonicalValueKind.ROLE,
					message.role(),
					messageLocation + ".role");
			List<TracePart> parts = message.parts() == null
					? List.of()
					: message.parts();
			for (int partIndex = 0;
					partIndex < parts.size();
					partIndex++) {
				TracePart part = parts.get(partIndex);
				if (part == null) {
					continue;
				}
				String partLocation = messageLocation
						+ ".parts[" + partIndex + "]";
				add(
						result,
						CanonicalValueKind.PART_KIND,
						part.kind(),
						partLocation + ".kind");
				add(
						result,
						isImageKind(part.kind())
								? CanonicalValueKind.IMAGE_DATA
								: CanonicalValueKind.DATA,
						part.value(),
						partLocation + ".value");
			}
		}
		return List.copyOf(result);
	}

	private List<CanonicalValueOccurrence> projectProperties(
			Map<String, String> someProperties,
			String aRootLocation) {
		if (someProperties == null || someProperties.isEmpty()) {
			return List.of();
		}
		ArrayList<CanonicalValueOccurrence> result = new ArrayList<>();
		int index = 0;
		for (Map.Entry<String, String> entry : someProperties.entrySet()) {
			String propertyLocation =
					aRootLocation + "[" + index++ + "]";
			add(
					result,
					CanonicalValueKind.PROPERTY_NAME,
					entry.getKey(),
					propertyLocation + ".name");
			add(
					result,
					CanonicalValueKind.PROPERTY_VALUE,
					entry.getValue(),
					propertyLocation + ".value");
		}
		return List.copyOf(result);
	}

	private void add(
			List<CanonicalValueOccurrence> someValues,
			CanonicalValueKind aKind,
			String aValue,
			String aLocation) {
		if (aValue != null) {
			someValues.add(
					new CanonicalValueOccurrence(
							new CanonicalValue(aKind, aValue),
							aLocation));
		}
	}

	private boolean isImageKind(String aKind) {
		return aKind != null
				&& aKind.toLowerCase().contains("image");
	}
}
