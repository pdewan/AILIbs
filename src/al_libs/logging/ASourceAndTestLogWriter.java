package al_libs.logging;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import name.fraser.neil.plaintext.diff_match_patch;
import name.fraser.neil.plaintext.diff_match_patch.Diff;

public class ASourceAndTestLogWriter {
    static final String SOURCE_LOG_FILE_NAME = "sources_log.txt";
    static final String LAST_SOURCES_FILE_NAME = "last_sources.txt";
    static final String SESSION_DIRECTORY_PREFIX = "log ";
    static final String SESSION_DIRECTORY_SUFFIX = "-session";
    static final String DIFF_INDICATOR = "\n(DIFF_FROM_PREVIOUS_FILE)\n";
    protected static final String SESSION_END = "//SESSION END";
    protected static final String SESSION_START = "//SESSION START";
    public static final int MAX_SOURCE_SIZE = 10000;
    protected static final String DELETED_FILE = "//@#$DELETED FILE&^%$";
    public static final String EMPTY_STRING = "";

    private List<String> lastSourcesLines;
    private Map<String, String> lastSourcesMap;
    private int lastSourcesLength;
    private Map<String, String> currentSourcesMap;
    private int currentSourcesLength;
    private String lastSourceFileName;
    private String sourceLogFileName;
    private boolean isAppended;
    private final String[] emptyStrings = {};
    private final File projectDirectory;
    private final File sourceFolder;
    private final File sessionDirectory;
    private final BasicTextManager textManager;
    private final StringBuffer retVal = new StringBuffer(MAX_SOURCE_SIZE);
    private static ASourceAndTestLogWriter instance;

    public ASourceAndTestLogWriter() {
        this(defaultProjectDirectory());
    }

    public ASourceAndTestLogWriter(File aProjectDirectory) {
        this(aProjectDirectory, defaultSourceFolder(aProjectDirectory));
    }

    public ASourceAndTestLogWriter(File aProjectDirectory, File aSourceFolder) {
        projectDirectory = canonicalFile(aProjectDirectory);
        sourceFolder = canonicalFile(aSourceFolder);
        sessionDirectory = new File(projectDirectory, sessionDirectoryName());
        textManager = new ABasicTextManager(sourceFolder);
        instance = this;
    }

    public void recordSessionSources() {
        if (!isAppended) {
            appendChanges();
            writeLastSourcesText();
            isAppended = true;
        }
    }

    protected String toLogEntry(Map<String, String> aDiffMap) {
        retVal.setLength(0);
        retVal.append(SESSION_START);
        retVal.append("(");
        retVal.append(getSessionNumber());
        retVal.append(",");
        retVal.append(currentSourcesLength - lastSourcesLength);
        retVal.append(",");
        retVal.append(getLastDate());
        retVal.append(")");
        retVal.append("\n");
        retVal.append(ABasicTextManager.toString(aDiffMap));
        retVal.append(SESSION_END);
        retVal.append("\n");
        return retVal.toString();
    }

    public void append(String aFileName, String aText) {
        try {
            File aFile = new File(aFileName);
            File aParent = aFile.getParentFile();
            if (aParent != null && !aParent.exists()) {
                aParent.mkdirs();
            }
            if (!aFile.exists()) {
                aFile.createNewFile();
            }
            FileWriter aFileWriter = new FileWriter(aFileName, true);
            aFileWriter.write(aText);
            aFileWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void appendChanges() {
        Map<String, String> aDiffMap = getDiffMap(getCurrentSourcesMap(),
                getLastSourcesMap());
        String aDiffText = ABasicTextManager.toString(aDiffMap);
        if (aDiffText.isEmpty()) {
            return;
        }
        append(getSourceLogFileName(), toLogEntry(aDiffMap));
    }

    public String getSourceLogFileName() {
        if (sourceLogFileName == null) {
            sourceLogFileName = new File(getSessionDirectory(),
                    SOURCE_LOG_FILE_NAME).getAbsolutePath();
        }
        return sourceLogFileName;
    }

    public List<String> getLastSourcesLines() {
        if (lastSourcesLines != null) {
            return lastSourcesLines;
        }
        try {
            lastSourcesLines = Files.readAllLines(new File(
                    getLastSourcesFileName()).toPath(),
                    Charset.defaultCharset());
        } catch (IOException e) {
            lastSourcesLines = new ArrayList<>();
        }
        return lastSourcesLines;
    }

    public String[] getLastSourcesLinesArray() {
        List<String> retVal = getLastSourcesLines();
        return retVal.toArray(emptyStrings);
    }

    public Map<String, String> getCurrentSourcesMap() {
        if (currentSourcesMap == null) {
            currentSourcesMap = getTextManager().getFileToText();
            currentSourcesLength = getLength(currentSourcesMap);
        }
        return currentSourcesMap;
    }

    public String getLastSourcesFileName() {
        if (lastSourceFileName == null) {
            lastSourceFileName = new File(getSessionDirectory(),
                    LAST_SOURCES_FILE_NAME).getAbsolutePath();
        }
        return lastSourceFileName;
    }

    public Date getLastDate() {
        File aLastSourceFile = new File(getLastSourcesFileName());
        if (!aLastSourceFile.exists()) {
            return null;
        }
        return new Date(aLastSourceFile.lastModified());
    }

    public Map<String, String> getLastSourcesMap() {
        if (lastSourcesMap == null) {
            lastSourcesMap = ABasicTextManager.extractFileContents(
                    getLastSourcesLinesArray());
            lastSourcesLength = getLength(lastSourcesMap);
        }
        return lastSourcesMap;
    }

    public void writeLastSourcesText() {
        try {
            File aLastSourcesFile = new File(getLastSourcesFileName());
            File aParent = aLastSourcesFile.getParentFile();
            if (aParent != null && !aParent.exists()) {
                aParent.mkdirs();
            }
            Files.writeString(aLastSourcesFile.toPath(),
                    getTextManager().getAllSourcesText().toString(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static int getLength(Map<String, String> aSourcesMap) {
        int retVal = 0;
        for (String aSource : aSourcesMap.values()) {
            retVal += aSource.length();
        }
        return retVal;
    }

    public static Set<String> subtract(Set<String> anOperand1,
            Set<String> anOperand2) {
        Set<String> retVal = new HashSet<>(anOperand1);
        retVal.removeAll(anOperand2);
        return retVal;
    }

    public static Set<String> intersect(Set<String> anOperand1,
            Set<String> anOperand2) {
        Set<String> retVal = new HashSet<>(anOperand1);
        retVal.retainAll(anOperand2);
        return retVal;
    }

    public static String toDelta(String anOldSource, String aNewSource) {
        diff_match_patch aDiffMatchPatch = new diff_match_patch();
        LinkedList<Diff> aDiffs = aDiffMatchPatch.diff_main(anOldSource,
                aNewSource);
        return aDiffMatchPatch.diff_toDelta(aDiffs);
    }

    public static String fromDelta(String anOldSource, String aDelta) {
        diff_match_patch aDiffMatchPatch = new diff_match_patch();
        try {
            LinkedList<Diff> aDiffs = aDiffMatchPatch.diff_fromDelta(
                    anOldSource, aDelta);
            return aDiffMatchPatch.diff_text2(aDiffs);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return "";
        }
    }

    public static String[] readNextSession(BufferedReader anInput) {
        List<String> retVal = new ArrayList<>();
        String aNextLine;
        try {
            while ((aNextLine = anInput.readLine()) != null) {
                if (aNextLine.startsWith(SESSION_START)) {
                    break;
                }
            }
            if (aNextLine == null) {
                return null;
            }
            while ((aNextLine = anInput.readLine()) != null) {
                if (aNextLine.equals(SESSION_END)) {
                    break;
                }
                retVal.add(aNextLine);
            }
            return retVal.toArray(new String[0]);
        } catch (IOException e) {
            return null;
        }
    }

    public static void merge(Map<String, String> aFileNameToContentsMap,
            Map<String, String> aSessionMap) {
        Set<String> aSessionKeySet = aSessionMap.keySet();
        Set<String> aCurrentKeySet = aFileNameToContentsMap.keySet();
        Set<String> aCommonKeySet = intersect(aSessionKeySet, aCurrentKeySet);
        Set<String> aNewKeySet = subtract(aSessionKeySet, aCurrentKeySet);
        for (String aFileName : aCommonKeySet) {
            String aPreviousFileContents = aFileNameToContentsMap.get(
                    aFileName);
            String aSessionContents = aSessionMap.get(aFileName);
            if (aSessionContents.equals(DELETED_FILE)) {
                aFileNameToContentsMap.remove(aFileName);
                continue;
            }
            String aDelta = deltaPart(aSessionContents);
            String aNextFileContents = fromDelta(aPreviousFileContents,
                    aDelta);
            aFileNameToContentsMap.put(aFileName, aNextFileContents);
        }
        for (String aFileName : aNewKeySet) {
            aFileNameToContentsMap.put(aFileName, aSessionMap.get(aFileName));
        }
    }

    public List<String[]> readAllSessions() {
        List<String[]> retVal = new ArrayList<>();
        File aSourceLogFile = new File(getSourceLogFileName());
        if (!aSourceLogFile.exists()) {
            return retVal;
        }
        try {
            BufferedReader aBufferedReader = new BufferedReader(
                    new FileReader(aSourceLogFile));
            while (true) {
                String[] aSessionLines = readNextSession(aBufferedReader);
                if (aSessionLines == null) {
                    break;
                }
                retVal.add(aSessionLines);
            }
            aBufferedReader.close();
        } catch (FileNotFoundException e) {
            return retVal;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return retVal;
    }

    public static String replayToSessionString(String aSourceLogFileName,
            int aSessionNumber) {
        Map<String, String> aReplayMap = replayToSessionMap(
                aSourceLogFileName, aSessionNumber);
        return ABasicTextManager.toString(aReplayMap);
    }

    public String replayToSessioString(int aSessionNumber) {
        Map<String, String> aReplayMap = replayToSessionMap(
                getSourceLogFileName(), aSessionNumber);
        return ABasicTextManager.toString(aReplayMap);
    }

    public static int getSessionNumber(String[] aSessionLines) {
        String aFirstLine = aSessionLines[0];
        return Integer.parseInt(aFirstLine.substring(SESSION_START.length(),
                aFirstLine.length()));
    }

    public int getSessionNumber() {
        return readAllSessions().size() + 1;
    }

    public static Map<String, String> replayToSessionMap(
            String aSourceLogFileName, int aSessionNumber) {
        Map<String, String> retVal = new HashMap<>();
        try {
            BufferedReader aBufferedReader = new BufferedReader(
                    new FileReader(aSourceLogFileName));
            for (int aSessionIndex = 1; aSessionIndex <= aSessionNumber;
                    aSessionIndex++) {
                String[] aSessionLines = readNextSession(aBufferedReader);
                if (aSessionLines == null) {
                    return retVal;
                }
                Map<String, String> aSessionMap =
                        ABasicTextManager.extractFileContents(aSessionLines);
                merge(retVal, aSessionMap);
            }
            aBufferedReader.close();
        } catch (FileNotFoundException e) {
            return retVal;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return retVal;
    }

    public static Map<String, String> getDiffMap(
            Map<String, String> aCurrentSourcesMap,
            Map<String, String> aLastSourcesMap) {
        Map<String, String> retVal = new HashMap<>();
        Set<String> aCurrentFiles = aCurrentSourcesMap.keySet();
        Set<String> aLastFiles = aLastSourcesMap.keySet();
        Set<String> aNewFiles = subtract(aCurrentFiles, aLastFiles);
        Set<String> aDeletedFiles = subtract(aLastFiles, aCurrentFiles);
        Set<String> aCommonFiles = intersect(aLastFiles, aCurrentFiles);
        for (String aFile : aNewFiles) {
            retVal.put(aFile, aCurrentSourcesMap.get(aFile));
        }
        for (String aFile : aDeletedFiles) {
            retVal.put(aFile, DELETED_FILE);
        }
        for (String aFile : aCommonFiles) {
            String aCurrent = aCurrentSourcesMap.get(aFile);
            String aLast = aLastSourcesMap.get(aFile);
            String aDiff = toDelta(aLast, aCurrent);
            if (aDiff.isEmpty()) {
                continue;
            }
            retVal.put(aFile, aCurrent + DIFF_INDICATOR + aDiff);
        }
        return retVal;
    }

    public Map<String, String> getDiffMap() {
        return getDiffMap(getCurrentSourcesMap(), getLastSourcesMap());
    }

    public File getProjectDirectory() {
        return projectDirectory;
    }

    public File getSourceFolder() {
        return sourceFolder;
    }

    public File getSessionDirectory() {
        if (!sessionDirectory.exists()) {
            sessionDirectory.mkdirs();
        }
        return sessionDirectory;
    }

    public BasicTextManager getTextManager() {
        return textManager;
    }

    public static ASourceAndTestLogWriter getInstance() {
        return instance;
    }

    public static void main(String[] args) {
        File aProjectDirectory = args.length == 0
                ? defaultProjectDirectory()
                : new File(args[0]);
        new ASourceAndTestLogWriter(aProjectDirectory).recordSessionSources();
    }

    private static File defaultProjectDirectory() {
        return canonicalFile(new File(System.getProperty("user.dir")));
    }

    private static File defaultSourceFolder(File aProjectDirectory) {
        Optional<File> aSourceFolder = DirectoryUtils.locateFolder(
                aProjectDirectory, "src");
        return aSourceFolder.orElse(aProjectDirectory);
    }

    private static File canonicalFile(File aFile) {
        try {
            return aFile.getCanonicalFile();
        } catch (IOException e) {
            return aFile.getAbsoluteFile();
        }
    }

    private static String deltaPart(String aSessionContents) {
        int anIndicatorIndex = aSessionContents.indexOf(DIFF_INDICATOR);
        if (anIndicatorIndex < 0) {
            return aSessionContents;
        }
        return aSessionContents.substring(
                anIndicatorIndex + DIFF_INDICATOR.length());
    }

    private static String sessionDirectoryName() {
        return SESSION_DIRECTORY_PREFIX
                + fileSafeName(LogNameManager.getLoggedName())
                + SESSION_DIRECTORY_SUFFIX;
    }

    private static String fileSafeName(String aName) {
        if (aName == null || aName.trim().isEmpty()) {
            return "unknown";
        }
        return aName.replaceAll("[<>:\"/\\\\|?*]", "_");
    }
}
