package eduAI.trace.processors;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractProviderTraceInterpreter
		implements ProviderTraceInterpreter {
	protected GenericTracesFileProcessor.TracePart textPart(String aText) {
		return new GenericTracesFileProcessor.TracePart("text", aText);
	}

	protected GenericTracesFileProcessor.TracePart imageBytesPart(
			String aByteCount,
			String aPrefix,
			String aSuffix) {
		return new GenericTracesFileProcessor.TracePart(
				"imageBytes",
				"count=" + aByteCount
						+ ",prefix=" + aPrefix
						+ ",suffix=" + aSuffix);
	}

	protected GenericTracesFileProcessor.TraceMessage message(
			String aRole,
			List<GenericTracesFileProcessor.TracePart> aParts) {
		return new GenericTracesFileProcessor.TraceMessage(
				providerRole(aRole),
				List.copyOf(aParts));
	}

	protected List<GenericTracesFileProcessor.TracePart> mutableParts() {
		return new ArrayList<>();
	}

	protected String providerRole(String aRole) {
		String role = standardRole(aRole);
		if ("MODEL".equals(role)) {
			return "ASSISTANT";
		}
		return role;
	}

	protected String standardRole(String aRole) {
		if (aRole == null) {
			return "";
		}
		String role = aRole.trim();
		if (role.startsWith("\"") && role.endsWith("\"")
				&& role.length() >= 2) {
			role = role.substring(1, role.length() - 1);
		}
		if ("null".equalsIgnoreCase(role)) {
			return "";
		}
		return role.toUpperCase();
	}

	protected int integerValue(String aText) {
		try {
			return Integer.parseInt(aText);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	protected String unescapeTraceString(String aValue) {
		if (aValue == null) {
			return "";
		}
		return aValue.replace("\\\"", "\"").replace("\\\\", "\\");
	}

	public boolean canInterpretProviderConfiguration(
			String aConfigurationDump) {
		return false;
	}

	public List<GenericTracesFileProcessor.TraceMessage>
			providerConfigurationMessages(String aConfigurationDump) {
		return List.of();
	}
}
