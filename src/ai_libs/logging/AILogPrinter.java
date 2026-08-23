package ai_libs.logging;

import ai_libs.logging.AbstractAILogSender;
import ai_libs.logging.LogEntryKind;

public class AILogPrinter extends AbstractAILogSender {

	@Override
	public String sendToServer(LogEntryKind aLogEntryKind, String aLogFileName,
			String aLog, int anIteration) {
		System.out.println("AILogPrinter");
		System.out.println("kind=" + aLogEntryKind);
		System.out.println("file=" + aLogFileName);
		System.out.println("iteration=" + anIteration);
		System.out.println("log=");
		System.out.println(aLog);
		return aLog;
	}
}
