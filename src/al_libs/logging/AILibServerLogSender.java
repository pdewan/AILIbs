package al_libs.logging;

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
