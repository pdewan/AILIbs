package al_libs.logging;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;

public class ALogSendingRunnable implements Runnable, LogSender {

	private static final int LOG_QUEUE_SIZE = 100;
	private static final SendingData STOP_REQUEST =
			SendingData.stopRequest();
	private final ArrayBlockingQueue<SendingData> logQueue =
			new ArrayBlockingQueue<>(LOG_QUEUE_SIZE);
	private static ALogSendingRunnable instance;
	private Thread thread;
	private volatile boolean acceptingLogs = true;

	public static ALogSendingRunnable getInstance() {
		if (instance == null) {
			instance = new ALogSendingRunnable(); 
		}
		return instance;
	}
	private ALogSendingRunnable() {
//		instance = this;
	}

	public synchronized void start() {
		if (thread != null && thread.isAlive()) {
			return;
		}
		acceptingLogs = true;
		thread = new Thread(this, "AILibLogSender");
		thread.setDaemon(true);
		thread.start();
	}

	public void addToQueue(SendingData log) {
		if (log == null || !acceptingLogs) {
			return;
		}
		logQueue.add(log);
	}

	@Override
	public CompletableFuture<Void> send(SendingData aSendingData) {
		addToQueue(aSendingData);
		return CompletableFuture.completedFuture(null);
	}
	

	
	public void addToQueue(LogEntryKind aLogEntryKind, String aLogFileName, String log, int intr) {
		addToQueue(new SendingData(aLogEntryKind, aLogFileName, log,intr));
	}
	
	public void endProcess(boolean b) {
		if (b) {
			stopAfterDraining();
		}
	}

	public synchronized void stopAfterDraining() {
		if (thread == null || !thread.isAlive()) {
			return;
		}
		acceptingLogs = false;
		try {
			logQueue.put(STOP_REQUEST);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	public void awaitStop() throws InterruptedException {
		Thread aThread = thread;
		if (aThread != null) {
			aThread.join();
		}
	}

	@Override
	public void close() {
		stopAfterDraining();
	}
	
	@Override
	public void run() {
			for(;;) {
				SendingData log;
				try {
					log = logQueue.take();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
//					System.out.println("Log Taken " + System.currentTimeMillis());
					if(log.isStopRequest())
						break;
				try {
					AILogSenderFactory.getAILogSender().sendToServer(log);
				} catch (Exception e) {
//					  System.err.println("Error sending log: "+e.getMessage());
				}
//					System.out.println("Log Sent " + System.currentTimeMillis());
			}
	}

	

}
