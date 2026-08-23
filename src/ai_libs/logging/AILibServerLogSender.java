package ai_libs.logging;

import ai_libs.logging.AILibLogSender;
import ai_libs.logging.AbstractAILogSender;
import ai_libs.logging.LogEntryKind;

public class AILibServerLogSender extends AbstractAILogSender {

	@Override
	public String sendToServer(LogEntryKind aLogEntryKind, String aLogFileName,
			String aLog, int anIteration)
			throws Exception {
		AILibLogSender.sendToServer(aLogEntryKind, aLogFileName, aLog,
				anIteration);
		return aLog;
	}
}
