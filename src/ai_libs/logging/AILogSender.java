package ai_libs.logging;

import java.io.File;

import ai_libs.logging.LogEntryKind;
import ai_libs.logging.SendingData;

public interface AILogSender {
	String sendToServer(SendingData aSendingData) throws Exception;

	String sendToServer(LogEntryKind aLogEntryKind, String aLogFileName,
			String aLog, int anIteration) throws Exception;

	String sendSourceView(File aProjectDirectory, int anIteration)
			throws Exception;

	String getSourceView(File aProjectDirectory);
}
