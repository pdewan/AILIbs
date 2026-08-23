package ai_libs.logging;

import java.util.concurrent.CompletableFuture;

import ai_libs.logging.LogEntryKind;
import ai_libs.logging.SendingData;

public interface LogSender {
	CompletableFuture<Void> send(SendingData aSendingData);

	default CompletableFuture<Void> send(LogEntryKind aLogEntryKind,
			String aLogFileName, String aLog, int anIteration) {
		return send(new SendingData(aLogEntryKind, aLogFileName, aLog,
				anIteration));
	}

	void close();
}
