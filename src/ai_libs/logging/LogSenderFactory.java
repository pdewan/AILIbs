package ai_libs.logging;

import ai_libs.logging.AFutureLogSender;
import ai_libs.logging.ALogSendingRunnable;
import ai_libs.logging.LogProcessor;
import ai_libs.logging.LogSender;
import ai_libs.logging.LogSenderKind;

public class LogSenderFactory {
	private static LogSender logSender;
	private static LogSenderKind createdLogSenderKind;

	public static synchronized LogSender getLogSender() {
		LogSenderKind aConfiguredKind =
				LogProcessor.getLogSenderKind();
		if (logSender == null || createdLogSenderKind != aConfiguredKind) {
			closeCurrentSender();
			logSender = createLogSender(aConfiguredKind);
			createdLogSenderKind = aConfiguredKind;
		}
		return logSender;
	}

	public static synchronized void setLogSenderKind(
			LogSenderKind aLogSenderKind) {
		closeCurrentSender();
		LogProcessor.setLogSenderKind(aLogSenderKind);
		logSender = null;
		createdLogSenderKind = null;
	}

	public static synchronized LogSenderKind getLogSenderKind() {
		return LogProcessor.getLogSenderKind();
	}

	private static LogSender createLogSender(LogSenderKind aLogSenderKind) {
		if (aLogSenderKind == LogSenderKind.RUNNABLE) {
			ALogSendingRunnable aRunnableSender =
					ALogSendingRunnable.getInstance();
			aRunnableSender.start();
			return aRunnableSender;
		}
		return new AFutureLogSender();
	}

	private static void closeCurrentSender() {
		if (logSender != null) {
			logSender.close();
		}
	}
}
