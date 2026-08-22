package al_libs.logging;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

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
