package al_libs.logging;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import net.datafaker.Faker;

public final class LogProcessor {
	private static final String DEFAULT_OLLAMA_LOGGING_LEVEL = "warn";
	private static final String SESSION_FILE_PREFIX = "session-";
	private static final String SESSION_FILE_SUFFIX = ".txt";
	private static final String SESSION_FILE_DATE_FORMAT =
			"yyyy-MM-dd'_at_'HH-mm-ss-SSS";
	private static final long DEFAULT_FAKER_SEED = 0;
	private static LogSenderKind logSenderKind = LogSenderKind.FUTURE;
	private static AILogSenderKind aiLogSenderKind = AILogSenderKind.SERVER;
	private static boolean ollamaDebugLogging;
	private static SourceLogKind sourceLogKind = SourceLogKind.TREE_STRUCTURE;
	private static boolean saveLogToDisk = true;
	private static long fakerSeed = DEFAULT_FAKER_SEED;
	private static boolean logSentThisSession;
	private static String lastProcessedLog = "";
	private static CompletableFuture<Void> lastSendFuture =
			CompletableFuture.completedFuture(null);

	private LogProcessor() {
	}

	public static String processLogging() {
		return processLogging(new File("."));
	}

	public static synchronized String processLogging(
			File aProjectDirectory) {
		initializeLoggedName();
		OllamaLoggingControl.configureProviderLogging(
				getOllamaLoggingLevel());
		ASourceAndTestLogWriter aLogWriter =
				new ASourceAndTestLogWriter(aProjectDirectory);
		if (logSentThisSession) {
			return lastProcessedLog;
		}
		String aSourceView = AILogSenderFactory.getAILogSender()
				.getSourceView(aProjectDirectory);
		lastProcessedLog = aSourceView;
		if (aSourceView.equals(readLastSessionLog(aLogWriter))) {
			logSentThisSession = true;
			return aSourceView;
		}
		String aSessionFileName = sessionFileName();
		int anIteration = sessionFileCount(aLogWriter) + 1;
		if (saveLogToDisk) {
			writeSessionLog(aLogWriter, aSessionFileName, aSourceView);
		}
		SendingData aSendingData = new SendingData(LogEntryKind.SOURCE,
				aSessionFileName, aSourceView, anIteration);
		lastSendFuture = LogSenderFactory.getLogSender().send(aSendingData);
		logSentThisSession = true;
		return aSourceView;
	}

	public static LogSenderKind getLogSenderKind() {
		return logSenderKind;
	}

	public static void setLogSenderKind(LogSenderKind aLogSenderKind) {
		logSenderKind = aLogSenderKind == null
				? LogSenderKind.FUTURE
				: aLogSenderKind;
	}

	public static AILogSenderKind getAILogSenderKind() {
		return aiLogSenderKind;
	}

	public static void setAILogSenderKind(AILogSenderKind anAILogSenderKind) {
		aiLogSenderKind = anAILogSenderKind == null
				? AILogSenderKind.SERVER
				: anAILogSenderKind;
	}

	public static boolean isOllamaDebugLogging() {
		return ollamaDebugLogging;
	}

	public static void setOllamaDebugLogging(boolean anOllamaDebugLogging) {
		ollamaDebugLogging = anOllamaDebugLogging;
	}

	public static String getOllamaLoggingLevel() {
		return ollamaDebugLogging ? "debug" : DEFAULT_OLLAMA_LOGGING_LEVEL;
	}

	public static SourceLogKind getSourceLogKind() {
		return sourceLogKind;
	}

	public static void setSourceLogKind(SourceLogKind aSourceLogKind) {
		sourceLogKind = aSourceLogKind == null
				? SourceLogKind.TREE_STRUCTURE
				: aSourceLogKind;
	}

	public static boolean isSaveLogToDisk() {
		return saveLogToDisk;
	}

	public static void setSaveLogToDisk(boolean aSaveLogToDisk) {
		saveLogToDisk = aSaveLogToDisk;
	}

	public static long getFakerSeed() {
		return fakerSeed;
	}

	public static void setFakerSeed(long aFakerSeed) {
		fakerSeed = aFakerSeed;
	}

	public static CompletableFuture<Void> getLastSendFuture() {
		return lastSendFuture;
	}

	public static void resetSessionState() {
		logSentThisSession = false;
		lastProcessedLog = "";
		lastSendFuture = CompletableFuture.completedFuture(null);
	}

	
	private static File newestSessionLog(ASourceAndTestLogWriter aLogWriter) {
		File[] aSessionFiles = aLogWriter.getSessionDirectory().listFiles(
				(aDirectory, aName) -> isSessionFileName(aName));
		if (aSessionFiles == null || aSessionFiles.length == 0) {
			return null;
		}
		File aNewestFile = aSessionFiles[0];
		for (File aSessionFile:aSessionFiles) {
			if (aSessionFile.lastModified() > aNewestFile.lastModified()) {
				aNewestFile = aSessionFile;
			}
		}
		return aNewestFile;
	}

	private static String readLastSessionLog(
			ASourceAndTestLogWriter aLogWriter) {
		File aNewestSessionLog = newestSessionLog(aLogWriter);
		if (aNewestSessionLog == null) {
			return null;
		}
		try {
			return Files.readString(aNewestSessionLog.toPath(),
					StandardCharsets.UTF_8);
		} catch (IOException e) {
			return null;
		}
	}

	private static void writeSessionLog(ASourceAndTestLogWriter aLogWriter,
			String aSessionFileName,
			String aSourceView) {
		File aSessionFile = new File(aLogWriter.getSessionDirectory(),
				aSessionFileName);
		try {
			Files.writeString(aSessionFile.toPath(), aSourceView,
					StandardCharsets.UTF_8);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static String sessionFileName() {
		String aDate = new SimpleDateFormat(SESSION_FILE_DATE_FORMAT)
				.format(new Date());
		String anId = UUID.randomUUID().toString()
				.replace("-", "")
				.substring(0, 8);
		return SESSION_FILE_PREFIX + aDate + "-local-" + anId
				+ SESSION_FILE_SUFFIX;
	}

	private static int sessionFileCount(ASourceAndTestLogWriter aLogWriter) {
		File[] aSessionFiles = aLogWriter.getSessionDirectory().listFiles(
				(aDirectory, aName) -> isSessionFileName(aName));
		return aSessionFiles == null ? 0 : aSessionFiles.length;
	}

	private static boolean isSessionFileName(String aName) {
		return aName.startsWith(SESSION_FILE_PREFIX);
	}

	private static void initializeLoggedName() {
		String aSavedName = LogNameManager.readSavedName();
		if (aSavedName != null && !aSavedName.trim().isEmpty()) {
			return;
		}
		Faker aFaker = new Faker(new Random(fakerSeed));
		LogNameManager.setLoggedName(aFaker.name().firstName() + " "
				+ aFaker.name().lastName());
	}
	
	public static void main(String[] args) {
		File aProjectDirectory = args.length == 0
				? new File(".")
				: new File(args[0]);
//		setAILogSenderKind(AILogSenderKind.PRINTER);
		
		processLogging(aProjectDirectory);
		lastSendFuture.join();
	}

}
