package ai_libs.logging;

import ai_libs.logging.AILibServerLogSender;
import ai_libs.logging.AILogPrinter;
import ai_libs.logging.AILogSender;
import ai_libs.logging.AILogSenderKind;
import ai_libs.logging.LogProcessor;

public class AILogSenderFactory {
	private static AILogSender aiLogSender;
	private static AILogSenderKind createdAILogSenderKind;

	public static synchronized AILogSender getAILogSender() {
		AILogSenderKind aConfiguredKind =
				LogProcessor.getAILogSenderKind();
		if (aiLogSender == null
				|| createdAILogSenderKind != aConfiguredKind) {
			aiLogSender = createAILogSender(aConfiguredKind);
			createdAILogSenderKind = aConfiguredKind;
		}
		return aiLogSender;
	}

	private static AILogSender createAILogSender(
			AILogSenderKind anAILogSenderKind) {
		if (anAILogSenderKind == AILogSenderKind.SERVER) {
			return new AILibServerLogSender();
		}
		return new AILogPrinter();
	}
}
