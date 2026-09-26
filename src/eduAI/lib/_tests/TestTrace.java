package eduAI.lib._tests;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.IdentityHashMap;
import java.util.Set;

public final class TestTrace {
	private static final Set<String> announcedTestPrograms =
			Collections.synchronizedSet(new LinkedHashSet<>());
	private static final Set<String> explicitProgramGoals =
			Collections.synchronizedSet(new LinkedHashSet<>());
	private static final Set<Object> activeMainDrivenTests =
			Collections.synchronizedSet(
					Collections.newSetFromMap(
							new IdentityHashMap<>()));

	private TestTrace() {
	}

	public static void programGoal(
			boolean isTrace,
			Object aTest,
			String aGoal) {
		if (!isTrace) {
			return;
		}
		explicitProgramGoals.add(testName(aTest));
		announceTestProgram(aTest);
		println("  ", "goal: " + aGoal);
	}

	public static void trace(
			boolean isTrace,
			Object aTest,
			String aMessage) {
		if (!isTrace) {
			return;
		}
		if (!isActive(aTest)) {
			return;
		}
		announceTestProgram(aTest);
		println("    ", aMessage);
	}

	public static void run(
			boolean isTrace,
			Object aTest,
			String aTestName,
			CheckedRunnable aRunnable) {
		run(
				isTrace,
				aTest,
				aTestName,
				goalFromTestMethod(aTestName),
				aRunnable);
	}

	public static void run(
			boolean isTrace,
			Object aTest,
			String aTestName,
			String aGoal,
			CheckedRunnable aRunnable) {
		try {
			if (isTrace) {
				activeMainDrivenTests.add(aTest);
				announceTestProgram(aTest);
				println("  ", aTestName);
				println("    ", "goal: " + aGoal);
			}
			aRunnable.run();
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException(e);
		} finally {
			activeMainDrivenTests.remove(aTest);
		}
	}

	private static boolean isActive(Object aTest) {
		return activeMainDrivenTests.contains(aTest);
	}

	private static void announceTestProgram(Object aTest) {
		String name = testName(aTest);
		if (announcedTestPrograms.add(name)) {
			println("", name);
			if (!explicitProgramGoals.contains(name)) {
				println(
						"  ",
						"goal: " + defaultGoalFromTestProgram(name));
			}
		}
	}

	private static void println(
			String anIndent,
			String aMessage) {
		String message = aMessage == null ? "null" : aMessage;
		String[] lines = message.split("\\R", -1);
		for (String line : lines) {
			System.out.println(
					threadPrefix()
							+ " "
							+ anIndent
							+ line);
		}
	}

	private static String threadPrefix() {
		return "[" + Thread.currentThread().getName() + "]";
	}

	private static String defaultGoalFromTestProgram(String aName) {
		String baseName = aName.endsWith("Test")
				? aName.substring(0, aName.length() - "Test".length())
				: aName;
		return "test " + wordsFromIdentifier(baseName);
	}

	public static String goalFromTestMethod(String aMethodName) {
		String baseName = aMethodName.startsWith("test")
				? aMethodName.substring("test".length())
				: aMethodName;
		return wordsFromIdentifier(baseName);
	}

	private static String testName(Object aTest) {
		if (aTest instanceof Class<?>) {
			return ((Class<?>) aTest).getSimpleName();
		}
		return aTest == null
				? "Test"
				: aTest.getClass().getSimpleName();
	}

	private static String wordsFromIdentifier(String anIdentifier) {
		String withSpaces = anIdentifier
				.replaceAll("([a-z])([A-Z])", "$1 $2")
				.replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
				.replace('_', ' ')
				.trim()
				.toLowerCase();
		return withSpaces.isEmpty() ? anIdentifier : withSpaces;
	}

	public interface CheckedRunnable {
		void run() throws Exception;
	}
}
