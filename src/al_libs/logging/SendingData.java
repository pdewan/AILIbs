package al_libs.logging;
public class SendingData {
	private final String logFileName;
	private final String log;
	private final int iteration;
	private final boolean stopRequest;
	private LogEntryKind logEntryKind = LogEntryKind.SOURCE;
	
	public SendingData(LogEntryKind aLogEntryKind, String aLogFileName, String log, int iteration) {
		logFileName = aLogFileName;
		this.log=log;
		this.iteration=iteration;
		logEntryKind = aLogEntryKind;
		stopRequest = false;
//		isTests = anIsTests;
	}

	private SendingData() {
		logFileName = null;
		log = null;
		iteration = -1;
		stopRequest = true;
	}

	public static SendingData stopRequest() {
		return new SendingData();
	}
	
	public String getLogFileName() {
		return logFileName;
	}
	public LogEntryKind getLogEntryKind() {
		return logEntryKind;
	}
	
	public String getLog() {
		return log;
	}
	
	public int getIteration() {
		return iteration;
	}

	public boolean isStopRequest() {
		return stopRequest;
	}
}
