package ai_libs.logging;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.MessageDigest;

import ai_libs.logging.LogNameManager;

import java.nio.charset.StandardCharsets;

public class LogNameManager {
	private static final String uuidFile = "LogsUUID.txt";
	private static File fileStore;
	private static String loggedName;
	private static String machineId;
	private static boolean cannotSaveName = false;
	private static boolean cannotReadName = false;
	private static boolean cannotGetHardwareAddress = false;

	 static {
		 initializeFileStore();
	
//	 File searchLoc = new File(System.getProperty("user.home") +
//	 "/helper-config/");
//	 if (searchLoc.exists())
//	 fileStore = new File(System.getProperty("user.home") + "/helper-config/"
//	 + uuidFile);
//	 else
//	 fileStore = new File(uuidFile);
	 }

	private static String byteToHexString(byte[] arr) {
		StringBuilder sb = new StringBuilder();
		for (byte b : arr)
			sb.append(Integer.toHexString(Byte.toUnsignedInt(b)));
		return sb.toString();
	}

//	public static String getRandomID() {
//		String val = Double.toString(Math.random()) + Double.toString(Math.random()) + Double.toString(Math.random());
//		return val.replaceAll("0.", "");
//	}
	
	public static String getRandomID() {
		Double aRandomDouble = Math.random();
		long aRandomInteger = Math.round(aRandomDouble*1000);
		return Long.toString(aRandomInteger);
	}

	public static void saveLoggedName(String aName) {
		if (cannotSaveName) {
			return;
		}
		try {
			maybeInitializeFileStore();
			if (!fileStore.exists()) {
				fileStore.createNewFile();
			}
			FileWriter fw = new FileWriter(fileStore);
			fw.write(aName);
			fw.close();
//			fileStore.setWritable(false);
		} catch (Exception e) {
			System.err.println("Cannot save file:" + e.getMessage());
			cannotSaveName = true;
		}
	}

	public static void setLoggedName(String aName) {
		loggedName = aName + " " + getRandomID();
		saveLoggedName(loggedName);
	}

	public static String getHashedHardwareAddress() {
		if (cannotGetHardwareAddress) {
			return null;
		}
		try {
			InetAddress localHost = InetAddress.getLocalHost();
			NetworkInterface network = NetworkInterface.getByInetAddress(localHost);
			String aHardwareAddress = byteToHexString(network.getHardwareAddress());
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return byteToHexString(digest.digest(aHardwareAddress.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			System.err.println("cannot get machine id:" + e.getMessage());
			cannotGetHardwareAddress = true;
			return null;
		}
	}

	public static String getMachineId() {
		if (machineId == null) {
			machineId = getHashedHardwareAddress();
		}

		if (machineId == null) {
			machineId = readSavedName();// only needed if we return machine id user id
		}
		if (machineId == null) {
			machineId = "R-" + getRandomID();
		}

		return machineId;
	}

	private static void maybeInitializeFileStore() {
		if (fileStore != null) {
			return;
		}
		initializeFileStore();
//		File searchLoc = new File(System.getProperty("user.home") + "/helper-config/");
//		if (searchLoc.exists())
//			fileStore = new File(System.getProperty("user.home") + "/helper-config/" + uuidFile);
//		else
//			fileStore = new File(uuidFile);
	}
	
	private static void initializeFileStore() {
		fileStore = new File(uuidFile); // in the current directory whataver it is

		File aHomeDirectory = new File(System.getProperty("user.home"));
		if (!aHomeDirectory.exists()) {
			return;
		}
		File searchLoc = new File(System.getProperty("user.home") + "/helper-config/");
		if (!searchLoc.exists()) {
			boolean createdHelperConfig = searchLoc.mkdir();
			if (!createdHelperConfig) {
				return;
			}
		}
		fileStore = new File(System.getProperty("user.home") + "/helper-config/" + uuidFile); 
	
//		if (searchLoc.exists())
//			fileStore = new File(System.getProperty("user.home") + "/helper-config/" + uuidFile);
//		else
//			fileStore = new File(uuidFile);
	}

	public static String readSavedName() {
		maybeInitializeFileStore();

		if (!fileStore.exists() || cannotReadName) {
			return null;
		}

		String retVal;
		try {
			BufferedReader br = new BufferedReader(new FileReader(fileStore));
			retVal = br.readLine();
			br.close();
			return retVal;
		} catch (Exception e) {
			cannotReadName = true;
			return null;
		}

	}

	public static String getLoggedName() {
		if (loggedName != null) {
			return loggedName;
		}

		loggedName = readSavedName();
		if (loggedName != null) {
			return loggedName;
		}
		if (loggedName == null) {
			loggedName = getMachineId(); //first save

			// bound to succeed at this point
			try {
				saveLoggedName(loggedName);
			} catch (Exception e) {
				System.out.println("Could not save logged name:" + loggedName);
			}
		}
		return loggedName;
	}
	public static void main (String[] args) {
//		String aRandomId = getRandomID();
//		aRandomId = getRandomID();
//		aRandomId = getRandomID();
		String aLoggedName = LogNameManager.getLoggedName();
		System.out.println("logged name" + aLoggedName);

	} 
	//
	// // String machineId;
	// if (fileStore.exists()) {
	// loggedName = readSavedName();
	// if (loggedName != null) {
	// return loggedName;
	// }
	// // try {
	// //// BufferedReader br = new BufferedReader(new
	// // FileReader(fileStore));
	// //// loggedName = br.readLine();
	// //// br.close();
	// //// return loggedName;
	// // } catch (Exception e) {
	// //
	// // }
	// }
	// loggedName = getMachineId();
	// if
	// try {
	// InetAddress localHost = InetAddress.getLocalHost();
	// NetworkInterface network = NetworkInterface.getByInetAddress(localHost);
	//
	// loggedName = byteToHexString(network.getHardwareAddress());
	// MessageDigest digest = MessageDigest.getInstance("SHA-256");
	// loggedName =
	// byteToHexString(digest.digest(loggedName.getBytes(StandardCharsets.UTF_8)));
	// } catch (Exception e) {
	// System.err.println("Warning: Cannot determine hardware addr generating id
	// for assignment...");
	// System.err.println("Thrown message:\n" + e.getMessage());
	// loggedName = "R-" + getRandomID();
	// }
	//
	// try {
	// saveLoggedName(loggedName);
	// } catch (Exception e) {
	// System.err.println("Could not save file");
	// }
	// // fileStore.createNewFile();
	// // FileWriter fw = new FileWriter(fileStore);
	// // fw.write(machineId);
	// // fw.close();
	// // fileStore.setWritable(false);
	//
	// return loggedName;
	//
	// }
	
	

}

