package eduAI.trace.lib;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.HttpOptions;

import io.github.ollama4j.Ollama;
import io.github.ollama4j.models.chat.OllamaChatMessage;
import io.github.ollama4j.utils.OptionsBuilder;

public final class NativePayloadFormatter {
	private static final Map<Class<?>, NativePayloadObjectFormatter>
			MESSAGE_FORMATTERS = new LinkedHashMap<>();
	private static final Map<Class<?>, NativePayloadObjectFormatter>
			CONTEXT_WINDOW_FORMATTERS = new LinkedHashMap<>();
	private static final Map<Class<?>, NativePayloadObjectFormatter>
			CONFIGURATION_FORMATTERS = new LinkedHashMap<>();
	private static final Map<Class<?>, NativePayloadObjectFormatter>
			RESPONSE_FORMATTERS = new LinkedHashMap<>();
	private static final Map<Class<?>, NativePayloadObjectFormatter>
			PROPERTIES_FORMATTERS = new LinkedHashMap<>();

	static {
		registerMessageFormatter(
				Content.class,
				message -> GeminiNativeMessageFormatter.formatContextWindow(
						List.of(Content.class.cast(message))));
		registerContextWindowFormatter(
				Content.class,
				window -> GeminiNativeMessageFormatter.formatContextWindow(
						contentList(List.class.cast(window))));
		registerMessageFormatter(
				OllamaChatMessage.class,
				message -> OllamaNativeMessageFormatter.formatContextWindow(
						List.of(OllamaChatMessage.class.cast(message))));
		registerContextWindowFormatter(
				OllamaChatMessage.class,
				window -> OllamaNativeMessageFormatter.formatContextWindow(
						ollamaMessageList(List.class.cast(window))));
		registerPropertiesFormatter(
				GenerateContentConfig.Builder.class,
				target -> GenerateContentConfig.Builder.class
						.cast(target).build().toString());
		registerPropertiesFormatter(
				HttpOptions.Builder.class,
				target -> HttpOptions.Builder.class
						.cast(target).build().toString());
		registerPropertiesFormatter(
				OptionsBuilder.class,
				target -> OptionsBuilder.class.cast(target)
						.build().toString());
		registerPropertiesFormatter(
				Ollama.class,
				NativePayloadFormatter::formatSimpleFields);
	}

	private static String formatSimpleFields(Object aTarget) {
		ArrayList<String> fields = new ArrayList<>();
		for (Field field : aTarget.getClass().getDeclaredFields()) {
			if (Modifier.isStatic(field.getModifiers())) {
				continue;
			}
			try {
				field.setAccessible(true);
				Object value = field.get(aTarget);
				if (value == null
						|| value instanceof Number
						|| value instanceof Boolean
						|| value instanceof CharSequence
						|| value.getClass().isEnum()) {
					fields.add(field.getName() + "=" + value);
				}
			} catch (RuntimeException | IllegalAccessException e) {
				// An inaccessible native field is omitted from the trace view.
			}
		}
		return "{" + String.join(", ", fields) + "}";
	}

	private NativePayloadFormatter() {
	}

	public static synchronized void registerMessageFormatter(
			Class<?> aMessageClass,
			NativePayloadObjectFormatter aFormatter) {
		if (aMessageClass == null || aFormatter == null) {
			return;
		}
		MESSAGE_FORMATTERS.put(aMessageClass, aFormatter);
	}

	public static synchronized void registerContextWindowFormatter(
			Class<?> aMessageClass,
			NativePayloadObjectFormatter aFormatter) {
		if (aMessageClass == null || aFormatter == null) {
			return;
		}
		CONTEXT_WINDOW_FORMATTERS.put(aMessageClass, aFormatter);
	}

	public static synchronized void registerConfigurationFormatter(
			Class<?> aConfigurationClass,
			NativePayloadObjectFormatter aFormatter) {
		if (aConfigurationClass == null || aFormatter == null) {
			return;
		}
		CONFIGURATION_FORMATTERS.put(aConfigurationClass, aFormatter);
	}

	public static synchronized void clearConfigurationFormatters() {
		CONFIGURATION_FORMATTERS.clear();
	}

	public static synchronized void registerResponseFormatter(
			Class<?> aResponseClass,
			NativePayloadObjectFormatter aFormatter) {
		if (aResponseClass == null || aFormatter == null) {
			return;
		}
		RESPONSE_FORMATTERS.put(aResponseClass, aFormatter);
	}

	public static synchronized void clearResponseFormatters() {
		RESPONSE_FORMATTERS.clear();
	}

	public static synchronized void registerPropertiesFormatter(
			Class<?> aPropertiesClass,
			NativePayloadObjectFormatter aFormatter) {
		if (aPropertiesClass == null || aFormatter == null) {
			return;
		}
		PROPERTIES_FORMATTERS.put(aPropertiesClass, aFormatter);
	}

	public static synchronized void clearPropertiesFormatters() {
		PROPERTIES_FORMATTERS.clear();
	}

	public static String formatContextWindow(Object aContextWindow) {
		if (!(aContextWindow instanceof List<?> messages)) {
			return String.valueOf(aContextWindow);
		}
		NativePayloadObjectFormatter formatter =
				contextWindowFormatter(messages);
		if (formatter != null) {
			return formatter.format(aContextWindow);
		}
		return String.valueOf(aContextWindow);
	}

	public static String formatMessage(Object aMessage) {
		NativePayloadObjectFormatter formatter =
				messageFormatter(aMessage);
		if (formatter != null) {
			return formatter.format(aMessage);
		}
		return String.valueOf(aMessage);
	}

	public static String formatConfiguration(Object aConfiguration) {
		NativePayloadObjectFormatter formatter =
				configurationFormatter(aConfiguration);
		if (formatter != null) {
			return formatter.format(aConfiguration);
		}
		return String.valueOf(aConfiguration);
	}

	public static String formatResponse(Object aResponse) {
		NativePayloadObjectFormatter formatter =
				responseFormatter(aResponse);
		if (formatter != null) {
			return formatter.format(aResponse);
		}
		return String.valueOf(aResponse);
	}

	public static String formatProperties(Object aProperties) {
		NativePayloadObjectFormatter formatter =
				propertiesFormatter(aProperties);
		if (formatter != null) {
			return formatter.format(aProperties);
		}
		return String.valueOf(aProperties);
	}

	private static NativePayloadObjectFormatter contextWindowFormatter(
			List<?> aMessages) {
		NativePayloadObjectFormatter result = null;
		for (Object message : aMessages) {
			if (message == null) {
				continue;
			}
			NativePayloadObjectFormatter formatter =
					contextWindowFormatter(message);
			if (formatter == null) {
				return null;
			}
			if (result == null) {
				result = formatter;
			} else if (result != formatter) {
				return null;
			}
		}
		return result;
	}

	private static NativePayloadObjectFormatter contextWindowFormatter(
			Object aMessage) {
		return formatter(aMessage, CONTEXT_WINDOW_FORMATTERS);
	}

	private static NativePayloadObjectFormatter messageFormatter(
			Object aMessage) {
		return formatter(aMessage, MESSAGE_FORMATTERS);
	}

	private static NativePayloadObjectFormatter configurationFormatter(
			Object aConfiguration) {
		return formatter(aConfiguration, CONFIGURATION_FORMATTERS);
	}

	private static NativePayloadObjectFormatter responseFormatter(
			Object aResponse) {
		return formatter(aResponse, RESPONSE_FORMATTERS);
	}

	private static NativePayloadObjectFormatter propertiesFormatter(
			Object aProperties) {
		return formatter(aProperties, PROPERTIES_FORMATTERS);
	}

	private static NativePayloadObjectFormatter formatter(
			Object aMessage,
			Map<Class<?>, NativePayloadObjectFormatter> aFormatters) {
		if (aMessage == null) {
			return null;
		}
		Class<?> messageClass = aMessage.getClass();
		synchronized (NativePayloadFormatter.class) {
			for (Map.Entry<Class<?>, NativePayloadObjectFormatter> entry :
					aFormatters.entrySet()) {
				if (entry.getKey().isAssignableFrom(messageClass)) {
					return entry.getValue();
				}
			}
		}
		return null;
	}

	private static List<Content> contentList(List<?> aMessages) {
		List<Content> result = new ArrayList<>();
		for (Object message : aMessages) {
			result.add(Content.class.cast(message));
		}
		return result;
	}

	private static List<OllamaChatMessage> ollamaMessageList(
			List<?> aMessages) {
		List<OllamaChatMessage> result = new ArrayList<>();
		for (Object message : aMessages) {
			result.add(OllamaChatMessage.class.cast(message));
		}
		return result;
	}

	@FunctionalInterface
	public interface NativePayloadObjectFormatter {
		String format(Object anObject);
	}
}
