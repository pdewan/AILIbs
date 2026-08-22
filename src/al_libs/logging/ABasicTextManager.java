package al_libs.logging;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;



public class ABasicTextManager implements BasicTextManager{
    protected StringBuffer allSourcesText;
    protected String[] allSourcesLines;
    private Map<String, StringBuffer> fileToText ;
	protected static String sourceSuffix;
	protected static String sourceSuffix2;
	protected File sourceFolder;
	protected List<File> sourceFiles = new ArrayList();
	
	protected boolean initializedSourceText = false;

    
    public ABasicTextManager(File aSourceFolder) {
    	sourceSuffix = DirectoryUtils.getSourceFileSuffix();
    	sourceSuffix2 = sourceSuffix;
    	
    	sourceFolder = aSourceFolder;
    	allSourcesText = new StringBuffer();
    }

    //@Override
    public void writeAllSourcesText(String aFileName) {
        try {
        	File sourceFile = new File(aFileName);
        	if (sourceFile.exists()) return;
            PrintWriter out = new PrintWriter(aFileName);
            String allText = getAllSourcesText().toString();
            out.print(allText);
            out.close();
        } catch (Exception e) {
//            e.printStackTrace(); // Commented out by Josh
        }
    }
    @Override
    public StringBuffer getAllSourcesText() {
        if (!initializedSourceText)

//        if (allSourcesText == null)
            initializeAllSourcesText();
        return allSourcesText;
    }
    @Override
    public Map<String, String> getFileToText() {
    	if (fileToText == null) {
        	StringBuffer aSource = getAllSourcesText();
        	return extractFileContents(aSource.toString());

    	}
//        if (!initializedSourceText)
//
////        if (allSourcesText == null)
//            initializeAllSourcesText();
    	
        
//        return fileToText;
        return null;
    }
    @Override
    public void initializeAllSourcesText() {
    	allSourcesText.setLength(0);
//    	fileToText.clear();
    	if (sourceFolder == null) {
    		System.err.println("No source folder found in basic text manager");
    		return;
    	}
    	addFolder(sourceFolder);
    	initializedSourceText = true;
//        Collection<ViewableClassDescription> filteredClasses = classesManager.getViewableClassDescriptions();
//        allSourcesText = toStringBuffer(filteredClasses);
    }
    protected void addFolder(File aFolder) {
    	File[] aFiles = aFolder.listFiles();
    	if (aFiles == null) {
    		System.err.println ("No files in:" + aFolder);
    		return;
    	}
    	for (File aFile:aFiles) {
    		if (aFile.isDirectory()) {
    			addFolder(aFile);
    		} else {
    			addSourceFile(aFile);
    		}
    	}
    	
    	
    }
    public static Map<String, String> extractFileContents(String anAllSourcesText) {
    	String[] anAllSourcesLines = anAllSourcesText.split("\n");
    	return extractFileContents(anAllSourcesLines);
    	
    }
    public static Map<String, String> extractFileContents(String[] anAllSourcesLines) {
    	Map<String, String> aFileNameToContentsMap = new HashMap();
    	int aNextFileIndex = 0;
    	while (true) {
    		int size = fillNextFileContents(anAllSourcesLines, aNextFileIndex, aFileNameToContentsMap);
    		if (size <= 0) {
    			break;
    		}
    		aNextFileIndex += size + 2;
    		
    	}
    	return aFileNameToContentsMap;
    }
    protected static int fillNextFileContents (String[] anAllSourcesLines, int aStartIndex, Map<String, String> aFileNameToContentsMap ) {
    	int aContentsIndex = aStartIndex;
    	int aContentsStartIndex = 0;
    	int aContentsEndIndex = 0;
    	String aFileName = null;
    	StringBuffer aContentsBuffer = new StringBuffer();
    	while (true) {
    		if (aContentsIndex == anAllSourcesLines.length) {
    			break;
    		}
    		String aNextLine = anAllSourcesLines[aContentsIndex];
    		aContentsIndex++;
    		if (!aNextLine.startsWith(BasicTextManager.SOURCE_PREFIX)) {
    			continue;
    		}
    		aFileName = aNextLine.substring(BasicTextManager.SOURCE_PREFIX.length(), aNextLine.length() );
//    		aFileNameToContentsMap.put(aFileName, aContentsBuffer.toString());
    		aContentsStartIndex = aContentsIndex;
    		break;
    	}
    	while (true) {
    		if (aContentsIndex == anAllSourcesLines.length) {
    			break;
    		}
    		String aNextLine = anAllSourcesLines[aContentsIndex];
    		if (BasicTextManager.SOURCE_SUFFIX.equals(aNextLine)) {
    			aContentsEndIndex = aContentsIndex;
    			break;
    		}
    		if (aNextLine.endsWith("\r")) {
    			aNextLine = aNextLine.substring(0, aNextLine.length() -1);
    		}
    		aContentsBuffer.append(aNextLine + "\n");  
    		aContentsIndex++;
    		
    	}
    	if (aFileName == null) {
    		return -1;
    	}
    	aContentsBuffer.deleteCharAt(aContentsBuffer.length() - 1); // last new line
		aFileNameToContentsMap.put(aFileName, aContentsBuffer.toString());

    	return aContentsEndIndex - aContentsStartIndex;
    }
    public static final int MAX_SOURCE_SIZE = 10000;
    static StringBuffer stringBuffer = new StringBuffer(MAX_SOURCE_SIZE);
    public static  String toString (Map<String, String> aFileNameToContents) {
    	stringBuffer.setLength(0);
    	for (String aKey:aFileNameToContents.keySet()) {
    		String aFileName = aKey;
    		File aFile = new File(aFileName);
    		if (!isSourceFile(aFile)) {
//    		if (!aFileName.endsWith(BasicLanguageDependencyManager.getSourceFileSuffix())) {
        		continue;
        	}
    		stringBuffer.append(BasicTextManager.SOURCE_PREFIX + aKey + "\n");
    		stringBuffer.append(aFileNameToContents.get(aKey));    
    		stringBuffer.append("\n" + BasicTextManager.SOURCE_SUFFIX + "\n");
  		
    	}
    	return stringBuffer.toString();
    	
    }
    protected void addSourceFile(File aFile) {
    	if (!isSourceFile(aFile)) {
//    	if (!aFile.getName().endsWith(sourceSuffix) && 
//    			sourceSuffix!= sourceSuffix2 &&
//    			!aFile.getName().endsWith(sourceSuffix2) 
//    			) 
    	
    		return;
    	}

//    	if (!aFile.getName().endsWith(BasicLanguageDependencyManager.getSourceFileSuffix())) {
//    		return;
//    	}
    	try {
			String contents = new String(Files.readAllBytes(aFile.toPath()));
			String aRelativePath = relativePath(sourceFolder, aFile);
//			String prefix = BasicTextManager.SOURCE_PREFIX + aFile.getName() + "\n";
			String prefix = BasicTextManager.SOURCE_PREFIX + aRelativePath + "\n";

			allSourcesText.append(prefix);			
			allSourcesText.append(contents);        
			allSourcesText.append("\n" + BasicTextManager.SOURCE_SUFFIX + "\n");
			sourceFiles.add(aFile);
//			fileToText.put(aFile.getName(), contents);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

    	
    	
    }
    protected void addFileOrFolder(File aFile) {
    	if (!aFile.exists()) {
    		return;
    	}
    	if (aFile.isDirectory()) {
    		addFolder(aFile);
    	}
//    	if (aFile.getName().endsWith(sourceSuffix) || (sourceSuffix != sourceSuffix2) && aFile.getName().endsWith(sourceSuffix2)) {
    	if (isSourceFile (aFile)) {	
    		addSourceFile(aFile);
    	}
    	
    }
    
    protected static boolean isSourceFile (File aFile) {
    	String aName = aFile.getName();
    	return (sourceSuffix != null && aName.endsWith(sourceSuffix)) ||
    			(sourceSuffix2 != null && !sourceSuffix2.equals(sourceSuffix)
    					&& aName.endsWith(sourceSuffix2)); 
    }

    protected static String relativePath(
    		File aBaseFolder,
    		File aFile) {
    	try {
    		Path basePath = aBaseFolder
    				.toPath()
    				.toAbsolutePath()
    				.normalize();
    		Path filePath = aFile
    				.toPath()
    				.toAbsolutePath()
    				.normalize();
    		return basePath
    				.relativize(filePath)
    				.toString();
    	} catch (Exception e) {
    		return aFile.getName();
    	}
    }
    @Override
    public void setAllSourcesText(StringBuffer anAllSourcesText) {
        allSourcesText = anAllSourcesText;
    }
    @Override
    public List<File> getSourceFiles() {
    	if (!initializedSourceText)

//          if (allSourcesText == null)
              initializeAllSourcesText();
		return sourceFiles;
	}
    @Override
	public void setSourceFiles(List<File> sourceFiles) {
		this.sourceFiles = sourceFiles;
	}

    @Override
	public String getSourceTreeText() {
    	List<File> aSourceFiles = new ArrayList(getSourceFiles());
    	Collections.sort(aSourceFiles, new Comparator<File>() {
			@Override
			public int compare(File aFile1, File aFile2) {
				return relativePath(sourceFolder, aFile1).compareTo(
						relativePath(sourceFolder, aFile2));
			}
		});
    	StringBuilder aBuilder = new StringBuilder();
    	String aRootName = sourceFolder == null
    			? "source"
    			: sourceFolder.getName();
    	aBuilder.append(aRootName);
    	aBuilder.append("\n");
    	Set<String> aPrintedDirectories = new HashSet();
    	for (File aSourceFile:aSourceFiles) {
    		String aRelativePath = relativePath(sourceFolder, aSourceFile);
    		String[] aPathParts = aRelativePath.replace("\\", "/").split("/");
    		StringBuilder aCurrentDirectory = new StringBuilder();
    		for (int anIndex = 0; anIndex < aPathParts.length; anIndex++) {
    			boolean isFile = anIndex == aPathParts.length - 1;
    			if (!isFile) {
    				if (aCurrentDirectory.length() > 0) {
    					aCurrentDirectory.append("/");
    				}
    				aCurrentDirectory.append(aPathParts[anIndex]);
    				if (aPrintedDirectories.contains(
    						aCurrentDirectory.toString())) {
    					continue;
    				}
    				aPrintedDirectories.add(aCurrentDirectory.toString());
    			}
    			appendIndent(aBuilder, anIndex + 1);
    			aBuilder.append(aPathParts[anIndex]);
    			if (isFile) {
    				aBuilder.append(" (");
    				aBuilder.append(lineCount(aSourceFile));
    				aBuilder.append(" lines)");
    			}
    			aBuilder.append("\n");
    		}
    	}
    	return aBuilder.toString();
	}

    protected static void appendIndent(StringBuilder aBuilder, int anIndent) {
    	for (int anIndex = 0; anIndex < anIndent; anIndex++) {
    		aBuilder.append("  ");
    	}
    }

    protected static long lineCount(File aFile) {
    	try {
    		return Files.lines(aFile.toPath()).count();
    	} catch (IOException e) {
    		return 0;
    	}
    }
}
