package eduAI.trace;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TraceObjectPrinter {
	private static final ThreadLocal<Boolean> COMPACT = ThreadLocal.withInitial(() -> true);
	private static final ThreadLocal<Boolean> SYSTEM_TEXT = ThreadLocal.withInitial(() -> false);

	/** Temporary in-memory evidence for trace calculations; never written to the trace. */
	public static String formatFullEvidence(String name, Object value) {
		boolean previous = COMPACT.get();
		COMPACT.set(false);
		try { return format(name, value); }
		finally { COMPACT.set(previous); }
	}
	private static final List<String> EXTERNAL_CLASS_NAMES =
			new ArrayList<>();
	private static final List<String> EXTERNAL_PACKAGE_PREFIXES =
			new ArrayList<>();
	private static final Map<Class<?>, TraceObjectFormatter>
			EXTERNAL_FORMATTERS = new LinkedHashMap<>();

	static {
		registerExternalClassName("java.util.UUID");
		registerExternalClassName("java.util.Locale");
		registerExternalClassName("java.util.Currency");
		registerExternalPackagePrefix("java.math.");
		registerExternalPackagePrefix("java.net.");
		registerExternalPackagePrefix("java.nio.file.");
		registerExternalPackagePrefix("java.time.");
		registerExternalPackagePrefix("java.util.regex.");
		registerExternalPackagePrefix("com.");
		registerExternalPackagePrefix("io.github.");
		registerExternalPackagePrefix("okhttp3.");
		registerExternalPackagePrefix("okio.");
		registerExternalPackagePrefix("org.");
		registerExternalPackagePrefix("eduAI.eliza.");
	}

	private TraceObjectPrinter() {
	}

	public static String format(String aVariableName, Object anObject) {
		return format(variableName(aVariableName), anObject, new IdentityHashMap<>());
	}

	/**
	 * Compatibility overload. Traversal now visits all elements and fields;
	 * reference cycles, rather than a depth cutoff, stop recursive expansion.
	 * @deprecated The depth argument is ignored. Use the two-argument overload.
	 */
	@Deprecated
	public static String format(
			String aVariableName,
			Object anObject,
			int aMaxDepth) {
		return format(aVariableName, anObject);
	}

	public static String format(Object anObject) {
		return format("value", anObject);
	}

	public static synchronized void registerExternalClassName(
			String aClassName) {
		String className = cleanRule(aClassName);
		if (!className.isEmpty()
				&& !EXTERNAL_CLASS_NAMES.contains(className)) {
			EXTERNAL_CLASS_NAMES.add(className);
		}
	}

	public static synchronized void clearExternalClassNames() {
		EXTERNAL_CLASS_NAMES.clear();
	}

	public static synchronized List<String> externalClassNames() {
		return new ArrayList<>(EXTERNAL_CLASS_NAMES);
	}

	public static synchronized void registerExternalPackagePrefix(
			String aPackagePrefix) {
		String packagePrefix = cleanRule(aPackagePrefix);
		if (!packagePrefix.isEmpty()
				&& !EXTERNAL_PACKAGE_PREFIXES.contains(packagePrefix)) {
			EXTERNAL_PACKAGE_PREFIXES.add(packagePrefix);
		}
	}

	public static synchronized void clearExternalPackagePrefixes() {
		EXTERNAL_PACKAGE_PREFIXES.clear();
	}

	public static synchronized List<String> externalPackagePrefixes() {
		return new ArrayList<>(EXTERNAL_PACKAGE_PREFIXES);
	}

	public static synchronized void registerExternalFormatter(
			Class<?> aClass,
			TraceObjectFormatter aFormatter) {
		if (aClass == null || aFormatter == null) {
			return;
		}
		EXTERNAL_FORMATTERS.put(aClass, aFormatter);
	}

	public static synchronized void clearExternalFormatters() {
		EXTERNAL_FORMATTERS.clear();
	}

	public static String className(Object anObject) {
		return className(objectClass(anObject));
	}

	public static String className(Class<?> aClass) {
		if (aClass == null) {
			return "";
		}
		return aClass.getName();
	}

	public static String interfaceNames(Object anObject) {
		Class<?> objectClass = objectClass(anObject);
		if (objectClass == null) {
			return "";
		}
		List<String> names = new ArrayList<>();
		for (Class<?> anInterface : objectClass.getInterfaces()) {
			String name = className(anInterface);
			if (!name.isEmpty() && !names.contains(name)) {
				names.add(name);
			}
		}
		return names.toString();
	}

	public static String methodSignatures(Object anObject) {
		Class<?> objectClass = objectClass(anObject);
		if (objectClass == null) {
			return "";
		}
		List<String> signatures = new ArrayList<>();
		Method[] methods = objectClass.getMethods();
		java.util.Arrays.sort(
				methods,
				Comparator.comparing(TraceObjectPrinter::methodSignature));
		for (Method method : methods) {
			if (method.getDeclaringClass() == Object.class
					|| method.isSynthetic()) {
				continue;
			}
			String signature = methodSignature(method);
			if (!signatures.contains(signature)) {
				signatures.add(signature);
			}
		}
		return signatures.toString();
	}

	private static String format(
			String aVariableName,
			Object anObject,
			IdentityHashMap<Object, Boolean> aVisited) {
		boolean previous = SYSTEM_TEXT.get();
		String role = roleOf(anObject);
		if (!role.isEmpty()) SYSTEM_TEXT.set(role.equalsIgnoreCase("SYSTEM"));
		try { return formatObject(aVariableName, anObject, aVisited, role); }
		finally { SYSTEM_TEXT.set(previous); }
	}

	private static String roleOf(Object object) {
		if (object == null || isSimpleValue(object.getClass()) || object.getClass().isArray()
				|| object instanceof Iterable<?> || object instanceof Map<?, ?>) return "";
		for (Field f : instanceFields(object.getClass())) {
			if (!f.getName().equals("role")) continue;
			Object value = fieldValue(f, object);
			if (value instanceof java.util.Optional<?> optional) value = optional.orElse(null);
			if (value instanceof String || value instanceof Enum<?>) return value.toString();
		}
		return "";
	}

	private static String formatObject(String aVariableName, Object anObject,
			IdentityHashMap<Object, Boolean> aVisited, String role) {
		if (anObject == null) {
			return aVariableName + ": null";
		}
		Class<?> objectClass = anObject.getClass();
		String header = aVariableName
				+ ": "
				+ className(objectClass);
		if (isSimpleValue(objectClass)) {
			return header + " value=" + simpleValue(anObject);
		}
		header += " interfaces=" + interfaceNames(anObject);
		// Path is a scalar runtime value. Its iterator creates new Path objects for
		// each component, so identity-based cycle detection cannot stop recursion.
		if (anObject instanceof java.nio.file.Path) {
			return header + " toString=" + safeToString(anObject) + " value=<opaque>";
		}
		if (objectClass == byte[].class) {
			return header + " value="
					+ ImageByteSummary.from((byte[]) anObject).formatted();
		}
		if (aVisited.containsKey(anObject)) {
			return header + " value=<cycle>";
		}
		if (COMPACT.get() && !role.isEmpty() && !usesExternalToString(objectClass)) {
			String text = TraceMessageText.fromFullDump(formatFullEvidence(aVariableName, anObject));
			if (text != null) {
				TraceTextSummary summary = SYSTEM_TEXT.get()
						? TraceTextSummary.fromEdges(text) : TraceTextSummary.from(text);
				header += " textAggregate=\"" + summary.token() + "\"";
			}
		}
		// Containers are traversed completely below. Their toString is redundant
		// and may duplicate an entire nested graph; never use its summary as evidence.
		boolean container = objectClass.isArray()
				|| anObject instanceof Map<?, ?> || anObject instanceof Iterable<?>;
		String toStringText = container
				? " toStringSummary=" + safeDescriptionSummary(anObject)
				: " toString=" + safeToString(anObject);
		TraceObjectFormatter formatter = externalFormatter(objectClass);
		if (formatter != null) {
			return header
					+ toStringText
					+ " formatted="
					+ safeFormattedValue(formatter, anObject);
		}
		if (usesExternalToString(objectClass)) {
			return header + toStringText;
		}
		aVisited.put(anObject, Boolean.TRUE);
		try {
			if (objectClass.isArray()) {
				return header
						+ toStringText
						+ " elements="
						+ formatArray(anObject, aVisited);
			}
			if (anObject instanceof Map<?, ?> map) {
				return header
						+ toStringText
						+ " entries="
						+ formatMap(map, aVisited);
			}
			if (anObject instanceof Iterable<?> iterable) {
				return header
						+ toStringText
						+ " elements="
						+ formatIterable(
								iterable,
								aVisited);
			}
			if (isOpaqueRuntimeObject(objectClass)) {
				return header + toStringText + " value=<opaque>";
			}
			return header
					+ toStringText
					+ " fields="
					+ formatFields(anObject, aVisited);
		} finally {
			aVisited.remove(anObject);
		}
	}

	private static String formatArray(
			Object anArray,
			IdentityHashMap<Object, Boolean> aVisited) {
		int length = Array.getLength(anArray);
		StringBuilder builder = new StringBuilder("[");
		for (int index = 0; index < length; index++) {
			appendSeparator(builder, index);
			builder.append(
					format(
							"[" + index + "]",
							Array.get(anArray, index),
							aVisited));
		}
		builder.append("]");
		return builder.toString();
	}

	private static String formatIterable(
			Iterable<?> anIterable,
			IdentityHashMap<Object, Boolean> aVisited) {
		StringBuilder builder = new StringBuilder("[");
		int index = 0;
		for (Object element : anIterable) {
			appendSeparator(builder, index);
			builder.append(
					format(
							"[" + index + "]",
							element,
							aVisited));
			index++;
		}
		builder.append("]");
		return builder.toString();
	}

	private static String formatMap(
			Map<?, ?> aMap,
			IdentityHashMap<Object, Boolean> aVisited) {
		StringBuilder builder = new StringBuilder("{");
		int index = 0;
		for (Map.Entry<?, ?> entry : aMap.entrySet()) {
			appendSeparator(builder, index);
			builder.append(
					format(
							"key",
							entry.getKey(),
							aVisited));
			builder.append(" -> ");
			builder.append(
					format(
							"value",
							entry.getValue(),
							aVisited));
			index++;
		}
		builder.append("}");
		return builder.toString();
	}

	private static String formatFields(
			Object anObject,
			IdentityHashMap<Object, Boolean> aVisited) {
		List<Field> fields = instanceFields(anObject.getClass());
		StringBuilder builder = new StringBuilder("{");
		for (int index = 0; index < fields.size(); index++) {
			Field field = fields.get(index);
			appendSeparator(builder, index);
			Object fieldValue = fieldValue(field, anObject);
			if (fieldValue == InaccessibleField.VALUE) {
				builder.append(field.getName()).append(": <inaccessible>");
			} else {
				builder.append(
						format(
								field.getName(),
								fieldValue,
								aVisited));
			}
		}
		builder.append("}");
		return builder.toString();
	}

	private static List<Field> instanceFields(Class<?> aClass) {
		List<Field> result = new ArrayList<>();
		Class<?> currentClass = aClass;
		while (currentClass != null && currentClass != Object.class) {
			for (Field field : currentClass.getDeclaredFields()) {
				int modifiers = field.getModifiers();
				if (!Modifier.isStatic(modifiers)
						&& !field.isSynthetic()) {
					result.add(field);
				}
			}
			currentClass = currentClass.getSuperclass();
		}
		return result;
	}

	private static Object fieldValue(Field aField, Object anObject) {
		try {
			if (!aField.canAccess(anObject)) {
				aField.setAccessible(true);
			}
			return aField.get(anObject);
		} catch (RuntimeException | IllegalAccessException e) {
			return InaccessibleField.VALUE;
		}
	}

	private static boolean isSimpleValue(Class<?> aClass) {
		return aClass.isPrimitive()
				|| String.class.equals(aClass)
				|| Character.class.equals(aClass)
				|| Boolean.class.equals(aClass)
				|| Number.class.isAssignableFrom(aClass)
				|| Enum.class.isAssignableFrom(aClass);
	}

	private static boolean isOpaqueRuntimeObject(Class<?> aClass) {
		Package objectPackage = aClass.getPackage();
		String packageName =
				objectPackage == null ? "" : objectPackage.getName();
		return packageName.startsWith("java.")
				|| packageName.startsWith("javax.")
				|| packageName.startsWith("jdk.")
				|| packageName.startsWith("sun.");
	}

	private static TraceObjectFormatter externalFormatter(Class<?> aClass) {
		synchronized (TraceObjectPrinter.class) {
			for (Map.Entry<Class<?>, TraceObjectFormatter> entry :
					EXTERNAL_FORMATTERS.entrySet()) {
				if (entry.getKey().isAssignableFrom(aClass)) {
					return entry.getValue();
				}
			}
		}
		return null;
	}

	private static boolean usesExternalToString(Class<?> aClass) {
		String className = aClass.getName();
		synchronized (TraceObjectPrinter.class) {
			if (EXTERNAL_CLASS_NAMES.contains(className)) {
				return true;
			}
			for (String packagePrefix : EXTERNAL_PACKAGE_PREFIXES) {
				if (className.startsWith(packagePrefix)) {
					return true;
				}
			}
		}
		return false;
	}

	private static String simpleValue(Object anObject) {
		if (anObject instanceof String text) {
			return "\"" + escaped(COMPACT.get() ? TraceTextSummary.compact(text, SYSTEM_TEXT.get()) : text) + "\"";
		}
		if (anObject instanceof Character ch) {
			return "'" + escaped(String.valueOf(ch)) + "'";
		}
		if (anObject instanceof Enum<?> enumValue) {
			return enumValue.name();
		}
		return String.valueOf(anObject);
	}

	private static String safeToString(Object anObject) {
		try {
			String value = String.valueOf(anObject);
			if (COMPACT.get()) {
				if (usesExternalToString(anObject.getClass()))
					value = CompactNativeText.format(value, anObject.getClass().getName().contains("GenerateContentConfig"));
				else if (!value.equals(anObject.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(anObject))))
					value = TraceTextSummary.compact(value, SYSTEM_TEXT.get());
			}
			return "\"" + escaped(value) + "\"";
		} catch (Throwable e) {
			return "<toString-threw:"
					+ className(e.getClass())
					+ ">";
		}
	}

	private static String safeDescriptionSummary(Object anObject) {
		try {
			String text = String.valueOf(anObject);
			return (SYSTEM_TEXT.get() ? TraceTextSummary.fromEdges(text)
					: TraceTextSummary.from(text)).formatted();
		} catch (Throwable e) {
			return "<toString-threw:" + className(e.getClass()) + ">";
		}
	}

	private static String safeFormattedValue(
			TraceObjectFormatter aFormatter,
			Object anObject) {
		try {
			return "\"" + escaped(aFormatter.format(anObject)) + "\"";
		} catch (Throwable e) {
			return "<formatter-threw:"
					+ className(e.getClass())
					+ ">";
		}
	}

	private static String escaped(String aText) {
		return aText
				.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\r", "\\r")
				.replace("\n", "\\n");
	}

	private static void appendSeparator(
			StringBuilder aBuilder,
			int anIndex) {
		if (anIndex > 0) {
			aBuilder.append(", ");
		}
	}

	private static Class<?> objectClass(Object anObject) {
		return anObject == null ? null : anObject.getClass();
	}

	private static String variableName(String aVariableName) {
		if (aVariableName == null || aVariableName.isBlank()) {
			return "value";
		}
		return aVariableName.trim();
	}

	private static String cleanRule(String aText) {
		return aText == null ? "" : aText.trim();
	}

	private static String methodSignature(Method aMethod) {
		StringBuilder builder = new StringBuilder();
		builder.append(className(aMethod.getReturnType()))
				.append(" ")
				.append(aMethod.getName())
				.append("(");
		Class<?>[] parameterTypes = aMethod.getParameterTypes();
		for (int index = 0; index < parameterTypes.length; index++) {
			appendSeparator(builder, index);
			builder.append(className(parameterTypes[index]));
		}
		builder.append(")");
		return builder.toString();
	}

	private enum InaccessibleField {
		VALUE
	}
}
