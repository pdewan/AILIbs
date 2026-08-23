package ai_libs.logging;

import java.io.File;

import ai_libs.logging.AILogSender;
import ai_libs.logging.ASourceAndTestLogWriter;
import ai_libs.logging.LogEntryKind;
import ai_libs.logging.LogProcessor;
import ai_libs.logging.SendingData;
import ai_libs.logging.SourceLogKind;

public abstract class AbstractAILogSender implements AILogSender {

	@Override
	public String sendToServer(SendingData aSendingData) throws Exception {
		return sendToServer(aSendingData.getLogEntryKind(),
				aSendingData.getLogFileName(), aSendingData.getLog(),
				aSendingData.getIteration());
	}

	@Override
	public String sendSourceView(File aProjectDirectory, int anIteration)
			throws Exception {
		ASourceAndTestLogWriter aLogWriter =
				new ASourceAndTestLogWriter(aProjectDirectory);
		String aLogFileName = new File(aLogWriter.getSourceLogFileName())
				.getName();
		return sendToServer(LogEntryKind.SOURCE, aLogFileName,
				getSourceView(aLogWriter), anIteration);
	}

	@Override
	public String getSourceView(File aProjectDirectory) {
		return getSourceView(new ASourceAndTestLogWriter(aProjectDirectory));
	}

	protected String getSourceView(ASourceAndTestLogWriter aLogWriter) {
		if (LogProcessor.getSourceLogKind()
				== SourceLogKind.FULL_TEXT_MAP) {
			return aLogWriter.getTextManager()
					.getAllSourcesText()
					.toString();
		}
		return aLogWriter.getTextManager().getSourceTreeText();
	}
}
