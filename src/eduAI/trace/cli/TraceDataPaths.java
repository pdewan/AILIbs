package eduAI.trace.cli;

import java.lang.reflect.*;
import java.util.*;
import eduAI.trace.ImageByteSummary;
import eduAI.trace.TraceObjectPrinter;

/**
 * Structural evidence with explicit paths. No application getters, hashCode or
 * toString calls. Map entry indices keep keys paired with their values.
 */
public final class TraceDataPaths {
    public static final String ID=".@identity", VALUE=".@value", ERROR=".@error", NULL=".@null";
    private TraceDataPaths() { }
    public static Map<String,String> flatten(String root, Object value,
            Set<Class<?>> participants, boolean registry) {
        var result = new LinkedHashMap<String,String>();
        visit(root, value, result, new IdentityHashMap<>(), participants, registry, true);
        return Collections.unmodifiableMap(result);
    }
    private static void visit(String path, Object object, Map<String,String> result,
            IdentityHashMap<Object,String> seen, Set<Class<?>> participants,
            boolean registry, boolean root) {
        if (object == null) { result.put(path+NULL, "true"); return; }
        if (object instanceof Class<?> type) { result.put(path+VALUE, type.getName()); return; }
        if (object instanceof String || object instanceof Character || object instanceof Boolean
                || object instanceof Byte || object instanceof Short || object instanceof Integer
                || object instanceof Long || object instanceof Float || object instanceof Double
                || object instanceof java.math.BigInteger || object instanceof java.math.BigDecimal
                || object instanceof Enum<?>) {
            result.put(path+VALUE, object instanceof Enum<?> e ? e.name() : String.valueOf(object));
            return;
        }
        result.put(path+ID, TraceObjectPrinter.objectIdentity(object));
        if (object instanceof byte[] bytes) {
            result.put(path+VALUE, ImageByteSummary.from(bytes).formatted()); return;
        }
        if (object instanceof java.nio.file.Path file) {
            result.put(path+VALUE, file.toString()); return;
        }
        if (object instanceof java.time.temporal.TemporalAccessor
                || object instanceof java.time.temporal.TemporalAmount) {
            result.put(path+VALUE,object.toString()); return;
        }
        Class<?> type = object.getClass();
        boolean container = type.isArray() || object instanceof Map<?,?>
                || object instanceof Iterable<?> || object instanceof Optional<?>;
        // Registry map values are handlers, not arbitrary payload to inspect.
        if (!root && !container && (participants.contains(type)
                || registry)) return;
        String previous = seen.putIfAbsent(object, path);
        if (previous != null) { result.put(path+".@reference", previous); return; }
        if (object instanceof Map<?,?> map) {
            int index=0;
            for (var entry : map.entrySet()) {
                String prefix=path+".entries."+(index++);
                visit(prefix+".key", entry.getKey(), result, seen, participants, registry, false);
                visit(prefix+".value", entry.getValue(), result, seen, participants, registry, false);
            }
        } else if (object instanceof Iterable<?> iterable) {
            int index=0;
            for (Object value : iterable)
                visit(path+".items."+(index++), value, result, seen, participants, registry, false);
        } else if (object instanceof Optional<?> optional) {
            visit(path+".optional", optional.orElse(null), result, seen, participants, registry, false);
        } else if (type.isArray()) {
            for (int i=0; i<Array.getLength(object); i++)
                visit(path+".items."+i, Array.get(object,i), result, seen, participants, registry, false);
        } else {
            List<Field> fields=new ArrayList<>();
            for (Class<?> c=type; c!=null && c!=Object.class; c=c.getSuperclass())
                for (Field field:c.getDeclaredFields())
                    if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isTransient(field.getModifiers())
                            && !field.isSynthetic()) fields.add(field);
            fields.sort(Comparator.comparing(f->f.getDeclaringClass().getName()+"."+f.getName()));
            for (Field field:fields) {
                String child=path+"."+field.getDeclaringClass().getSimpleName()+"."+field.getName();
                try {
                    if (!field.trySetAccessible()) result.put(child+ERROR,"inaccessible");
                    else visit(child,field.get(object),result,seen,participants,registry,false);
                } catch (IllegalAccessException | RuntimeException e) {
                    result.put(child+ERROR,e.getClass().getSimpleName());
                }
            }
        }
    }
}
