package ai_libs.logging;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import ai_libs.logging.AILogSenderFactory;
import ai_libs.logging.LogSender;
import ai_libs.logging.SendingData;

public class AFutureLogSender implements LogSender {

	@Override
	public CompletableFuture<Void> send(SendingData aSendingData) {
		return CompletableFuture.runAsync(() -> {
			try {
				AILogSenderFactory.getAILogSender().sendToServer(
						aSendingData);
			} catch (Exception e) {
				throw new CompletionException(e);
			}
		});
	}

	@Override
	public void close() {
	}
}
