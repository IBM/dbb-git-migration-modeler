/********************************************************************************
 * Licensed Materials - Property of IBM                                          *
 * (c) Copyright IBM Corporation 2018, 2025. All Rights Reserved.                *
 *                                                                               *
 * Note to U.S. Government Users Restricted Rights:                              *
 * Use, duplication or disclosure restricted by GSA ADP Schedule                 *
 * Contract with IBM Corp.                                                       *
 ********************************************************************************/

package com.ibm.dbb.migration;

import com.ibm.dbb.build.BuildProperties;
import com.ibm.dbb.build.CopyToHFS;
import com.ibm.dbb.build.DBBConstants.CopyMode;
import com.ibm.dbb.utils.FileUtils;
import com.ibm.dbb.migration.utils.Logger;
import com.ibm.jzos.RecordReader;
import com.ibm.jzos.ZFileConstants;
import org.apache.commons.cli.*;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Standalone Java application to migrate members from mainframe datasets
 * to a local Git repository on HFS.
 * 
 * This is a Java conversion of the original migrateDatasets.groovy script.
 */
public class MigrateDatasets {
    
    // Character constants for detection
    private static final byte CHAR_NL = 0x15;
    private static final byte CHAR_CR = 0x0D;
    private static final byte CHAR_LF = 0x25;
    private static final byte CHAR_SHIFT_IN = 0x0F;
    private static final byte CHAR_SHIFT_OUT = 0x0E;
    private static final byte CHAR_DEL = (byte) 0xFF;
    
    private File messageFile;
    private Logger logger;
    private String npLevel;
    private boolean hasErrorTable;
    private List<String> gitAttributePathCache;
    
    public static void main(String[] args) {
        MigrateDatasets migrator = new MigrateDatasets();
        try {
            migrator.run(args);
        } catch (Exception e) {
            System.err.println("Error executing migration: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    public void run(String[] args) throws Exception {
        // Initialize
        gitAttributePathCache = new ArrayList<>();
        
        // Create CLI parser
        Options options = createOptions();
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        
        CommandLine cmd;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.err.println("Error parsing command line: " + e.getMessage());
            formatter.printHelp("migrate [options] <data sets>|<mapping files>", 
                "Use this migration tool to migrate members from data sets to a local GIT repository on HFS", 
                options, "", true);
            System.exit(2);
            return;
        }
        
        // Validate arguments
        if (cmd.getArgs().length != 1) {
            System.err.println("Error: Exactly one argument required (datasets or mapping file)");
            formatter.printHelp("migrate [options] <data sets>|<mapping files>", options);
            System.exit(2);
            return;
        }
        
        // Validate required parameter
        if (!cmd.hasOption("r")) {
            System.err.println("Error: Repository parameter (-r) is required");
            formatter.printHelp("migrate [options] <data sets>|<mapping files>", options);
            System.exit(2);
            return;
        }
        
        // Enable DBB file tagging
        BuildProperties.setProperty("dbb.file.tagging", "true");
        
        // Handle output parameter
        BufferedWriter writer = null;
        if (cmd.hasOption("o")) {
            String outputPath = cmd.getOptionValue("o");
            File outputFile = new File(outputPath);
            writer = new BufferedWriter(new FileWriter(outputFile, true));
            logger.logMessage("Generated mappings will be saved in " + outputFile);
        }
        
        // Handle log file
        // Initialize logger
        logger = new Logger();
        if (cmd.hasOption("l")) {
            String logFilePath = cmd.getOptionValue("l");
            try {
                logger.create(logFilePath); // Uses UTF-8 by default
                logger.logMessage("Messages will be saved in '" + logFilePath + "' with UTF-8 encoding");
            } catch (IOException e) {
                System.err.println("Error creating log file: " + e.getMessage());
                System.exit(1);
            }
        }
        
        // Handle non-printable flag
        if (cmd.hasOption("np")) {
            npLevel = cmd.getOptionValue("np");
            if (!npLevel.equals("info") && !npLevel.equals("warning")) {
                logger.logMessage(" ! Error: Unrecognized value for non-printable scan level.");
                formatter.printHelp("migrate [options] <data sets>|<mapping files>", options);
                System.exit(2);
                return;
            }
            logger.logMessage("Non-printable scan level is " + npLevel);
        }
        
        // Handle error-table flag
        hasErrorTable = cmd.hasOption("t");
        
        // Create temporary file for migration messages
        messageFile = File.createTempFile("migration", null);
        messageFile.deleteOnExit();
        
        String arg = cmd.getArgs()[0];
        boolean isMappingFileSpecified = isMappingFileSpecified(arg);
        
        // Handle preview parameter and mapping file
        if (cmd.hasOption("p") && isMappingFileSpecified) {
            logger.logMessage("Error: Preview flag is not supported when using a mapping file");
            formatter.printHelp("migrate [options] <data sets>|<mapping files>", options);
            System.exit(2);
            return;
        }
        
        // Handle preview parameter
        boolean isPreview = cmd.hasOption("p");
        if (isPreview) {
            logger.logMessage("Preview flag is specified, no members will be copied to HFS");
        }
        
        // Create the repository directory if not exist
        File repository = new File(cmd.getOptionValue("r"));
        if (!repository.exists()) {
            repository.mkdirs();
        }
        logger.logMessage("Local GIT repository: " + repository);
        
        File gitAttributesFile = new File(repository, ".gitattributes");
        BufferedWriter gitAttributeWriter = new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream(gitAttributesFile, true), "ISO8859-1"));
        
        try {
            if (isMappingFileSpecified) {
                // Process mapping files
                processMappingFiles(arg, repository, gitAttributeWriter, isPreview);
            } else {
                // Process datasets with mapping rule
                processDatasets(arg, cmd.getOptionValue("m"), repository, gitAttributeWriter, isPreview, writer);
            }
        } finally {
            // Cleanup
            if (writer != null) {
                writer.close();
            }
            if (gitAttributeWriter != null) {
                gitAttributeWriter.close();
            }
            
            // Tag .gitattributes file
            if (!FileUtils.setFileTag(gitAttributesFile.getPath(), "ISO8859-1")) {
                System.out.println("*! Error tagging .gitattributes file, must be done manually");
            }
            
            if (logger != null) {
                logger.close();
            }
        }
    }
    
    private Options createOptions() {
        Options options = new Options();
        
        options.addOption(Option.builder("r")
            .longOpt("repository")
            .hasArg()
            .argName("repository")
            .desc("Local GIT repository to migrate to (required)")
            .build());
            
        options.addOption(Option.builder("o")
            .longOpt("output")
            .hasArg()
            .argName("output")
            .desc("Output of the generated mapping file (optional)")
            .build());
            
        options.addOption(Option.builder("m")
            .longOpt("mapping")
            .hasArg()
            .argName("mapping")
            .desc("The ID of mapping rule (optional), for example: com.ibm.dbb.migration.MappingRule")
            .build());
            
        options.addOption(Option.builder("p")
            .longOpt("preview")
            .desc("Perform a dry-run to generate a mapping file (optional)")
            .build());
            
        options.addOption(Option.builder("l")
            .longOpt("log")
            .hasArg()
            .argName("logfile")
            .desc("Path to a logfile (optional, UTF-8 encoding)")
            .build());
            
        options.addOption(Option.builder("np")
            .longOpt("non-printable")
            .hasArg()
            .argName("level")
            .desc("Scan level (\"info\" or \"warning\") for non-printable characters (optional)")
            .build());
            
        options.addOption(Option.builder("t")
            .longOpt("error-table")
            .desc("Print a table to visualize non-printable and non-roundtrippable character errors")
            .build());
            
        return options;
    }
    
    private void processMappingFiles(String arg, File repository, BufferedWriter gitAttributeWriter, 
                                     boolean isPreview) throws Exception {
        String[] mappingFiles = arg.split(",");
        
        for (String path : mappingFiles) {
            File mappingFile = new File(path);
            logger.logMessage("Migrate data sets using mapping file " + mappingFile);
            
            try (BufferedReader reader = new BufferedReader(new FileReader(mappingFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Ignore comments
                    if (line.trim().startsWith("#")) {
                        continue;
                    }
                    
                    // Each line should be: "dataset hfsPath pdsEncoding=Cp1047"
                    String[] lineSegments = line.split("\\s+");
                    if (lineSegments.length >= 2) {
                        String datasetMember = lineSegments[0];
                        
                        // Validate dataset(member) format
                        Pattern pattern = Pattern.compile(".*\\((.*[a-zA-Z\\*].*)\\)");
                        Matcher matcher = pattern.matcher(datasetMember);
                        
                        if (matcher.matches()) {
                            String[] datasetMemberSegment = datasetMember.split("[\\(\\)]");
                            String dataset = datasetMemberSegment[0];
                            String member = datasetMemberSegment[1];
                            String hfsPath = lineSegments[1];
                            String pdsEncoding = null;
                            
                            if (lineSegments.length > 2) {
                                String[] pdsEncodingSegments = lineSegments[2].split("=");
                                if (pdsEncodingSegments.length == 2 && 
                                    pdsEncodingSegments[0].equals("pdsEncoding")) {
                                    pdsEncoding = pdsEncodingSegments[1].trim();
                                }
                            }
                            
                            String encodingString = pdsEncoding != null ? pdsEncoding : "default encoding";
                            
                            int rc = detector(dataset, member);
                            
                            if (rc == 4) {
                                logger.logMessage("[INFO] Copying " + datasetMember + " to " + hfsPath +
                                    " using " + encodingString);
                                logger.logMessage(" ! Possible migration issue:\n");
                                printMessageFile();
                                new CopyToHFS().dataset(dataset).member(member)
                                    .file(new File(hfsPath)).pdsEncoding(pdsEncoding).execute();
                            } else if (rc == 8) {
                                logger.logMessage("[WARNING] Copying " + datasetMember + " to " + hfsPath + ".");
                                logger.logMessage(" ! Possible migration issue:\n");
                                printMessageFile();
                                logger.logMessage(" ! Copying using BINARY mode");
                                new CopyToHFS().dataset(dataset).member(member)
                                    .file(new File(hfsPath)).pdsEncoding(pdsEncoding)
                                    .copyMode(CopyMode.BINARY).execute();
                            } else if (rc == 12) {
                                logger.logMessage("[ERROR] Error while reading " + datasetMember + ":\n");
                                printMessageFile();
                            } else {
                                logger.logMessage("Copying " + datasetMember + " to " + hfsPath +
                                    " using " + encodingString);
                                new CopyToHFS().dataset(dataset).member(member)
                                    .file(new File(hfsPath)).pdsEncoding(pdsEncoding).execute();
                            }
                            
                            if (gitAttributeWriter != null) {
                                String gitAttributeLine = null;
                                if (rc == 8) {
                                    Path relPath = repository.toPath().relativize(new File(hfsPath).toPath());
                                    gitAttributeLine = relPath.toString() + " binary";
                                } else if (rc <= 4) {
                                    gitAttributeLine = generateGitAttributeEncodingLine(
                                        repository, new File(hfsPath), pdsEncoding);
                                }
                                if (gitAttributeLine != null) {
                                    gitAttributeWriter.write(gitAttributeLine);
                                    gitAttributeWriter.newLine();
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    private void processDatasets(String arg, String mappingRuleParam, File repository, 
                                 BufferedWriter gitAttributeWriter, boolean isPreview, 
                                 BufferedWriter writer) throws Exception {
        String[] datasets = arg.split(",");
        
        // Parse mapping rule
        String mappingRuleId = "com.ibm.dbb.migration.MappingRule";
        Map<String, String> mappingRuleAttrs = new HashMap<>();
        
        if (mappingRuleParam != null) {
            Object[] parsed = parseMappingRule(mappingRuleParam);
            mappingRuleId = (String) parsed[0];
            mappingRuleAttrs = (Map<String, String>) parsed[1];
        }
        
        logger.logMessage("Using mapping rule " + mappingRuleId + " to migrate the data sets");
        
        // Create mapping rule instance using reflection (works with real or stub classes)
        Class<?> mappingRuleClass = Class.forName(mappingRuleId);
        Object mappingRule = mappingRuleClass
            .getConstructor(File.class, Map.class)
            .newInstance(repository, mappingRuleAttrs);
        
        for (String dataset : datasets) {
            logger.logMessage("Migrating data set " + dataset);
            // Use reflection to call generateMapping
            List<?> mappingInfos = (List<?>) mappingRuleClass
                .getMethod("generateMapping", String.class)
                .invoke(mappingRule, dataset);
            
            for (Object mappingInfoObj : mappingInfos) {
                // Use reflection to access MappingInfo properties
                Class<?> mappingInfoClass = mappingInfoObj.getClass();
                String datasetName = (String) mappingInfoClass.getMethod("getDataset").invoke(mappingInfoObj);
                String member = (String) mappingInfoClass.getMethod("getMember").invoke(mappingInfoObj);
                String hfsPath = (String) mappingInfoClass.getMethod("getHfsPath").invoke(mappingInfoObj);
                String pdsEncoding = (String) mappingInfoClass.getMethod("getPdsEncoding").invoke(mappingInfoObj);
                String fullyQualifiedDsn = (String) mappingInfoClass.getMethod("getFullyQualifiedDsn").invoke(mappingInfoObj);
                
                String encodingString = pdsEncoding != null ? pdsEncoding : "default encoding";
                int rc = detector(datasetName, member);
                
                if (isPreview) {
                    if (rc == 4) {
                        logger.logMessage("[INFO] Previewing " + fullyQualifiedDsn);
                        logger.logMessage(" ! Possible migration issue:");
                        printMessageFile();
                    } else if (rc == 8) {
                        logger.logMessage("[WARNING] Previewing " + fullyQualifiedDsn);
                        logger.logMessage(" ! Possible migration issue:");
                        logger.logMessage(" ! Will copy using BINARY mode");
                        printMessageFile();
                    } else if (rc == 12) {
                        logger.logMessage("[ERROR] Error while reading " + fullyQualifiedDsn + ":");
                        printMessageFile();
                    } else {
                        logger.logMessage("Previewing " + fullyQualifiedDsn + ". Using " + encodingString + ".");
                    }
                } else {
                    if (rc == 4) {
                        logger.logMessage("[INFO] Copying " + fullyQualifiedDsn + " to " + hfsPath +
                            " using " + encodingString);
                        logger.logMessage(" ! Possible migration issue:");
                        printMessageFile();
                        new CopyToHFS().dataset(datasetName).member(member)
                            .file(new File(hfsPath)).pdsEncoding(pdsEncoding).execute();
                    } else if (rc == 8) {
                        logger.logMessage("[WARNING] Copying " + fullyQualifiedDsn + " to " + hfsPath);
                        logger.logMessage(" ! Possible migration issue:");
                        printMessageFile();
                        logger.logMessage(" ! Copying using BINARY mode");
                        new CopyToHFS().dataset(datasetName).member(member)
                            .file(new File(hfsPath)).pdsEncoding(pdsEncoding)
                            .copyMode(CopyMode.BINARY).execute();
                    } else if (rc == 12) {
                        logger.logMessage("[ERROR] Error while reading " + fullyQualifiedDsn + ":");
                        printMessageFile();
                    } else {
                        logger.logMessage("Copying " + fullyQualifiedDsn + " to " + hfsPath +
                            " using " + encodingString);
                        new CopyToHFS().dataset(datasetName).member(member)
                            .file(new File(hfsPath)).pdsEncoding(pdsEncoding).execute();
                    }
                    
                    if (gitAttributeWriter != null) {
                        File hfsFile = new File(hfsPath);
                        String gitAttributeLine = null;
                        if (rc == 8) {
                            Path relPath = repository.toPath().relativize(hfsFile.toPath());
                            gitAttributeLine = relPath.toString() + " binary";
                        } else if (rc <= 4) {
                            gitAttributeLine = generateGitAttributeEncodingLine(
                                repository, hfsFile, pdsEncoding);
                        }
                        if (gitAttributeLine != null) {
                            gitAttributeWriter.write(gitAttributeLine);
                            gitAttributeWriter.newLine();
                        }
                    }
                }
                
                if (writer != null) {
                    writer.write(mappingInfoObj.toString());
                    writer.newLine();
                }
            }
        }
    }
    
    private Object[] parseMappingRule(String mappingRuleId) {
        Map<String, String> mappingIds = new HashMap<>();
        mappingIds.put("MappingRule", "com.ibm.dbb.migration.MappingRule");
        mappingIds.put("com.ibm.dbb.migration.MappingRule", "com.ibm.dbb.migration.MappingRule");
        
        String[] temp = mappingRuleId.split("[\\[\\]]");
        if (temp.length == 1) {
            return new Object[]{temp[0], new HashMap<String, String>()};
        } else if (temp.length == 2) {
            String id = temp[0];
            id = mappingIds.getOrDefault(id, id);
            String str = temp[1];
            Map<String, String> attrMap = new HashMap<>();
            String[] attrStrs = str.split(",");
            for (String attrStr : attrStrs) {
                String[] attr = attrStr.split(":");
                String attrName = attr[0];
                String attrValue = attr[1];
                if (attrValue.startsWith("\"") && attrValue.endsWith("\"") && attrValue.length() > 2) {
                    attrValue = attrValue.substring(1, attrValue.length() - 1);
                }
                attrMap.put(attrName, attrValue);
            }
            return new Object[]{id, attrMap};
        }
        return new Object[]{"com.ibm.dbb.migration.MappingRule", new HashMap<String, String>()};
    }
    
    private String generateGitAttributeEncodingLine(File root, File file, String encoding) {
        if (encoding == null) {
            encoding = "ibm-1047";
        }
        
        Path relPath = root.toPath().relativize(file.getParentFile().toPath());
        String fileName = file.getName();
        int index = fileName.lastIndexOf(".");
        String fileExtension = (index == -1 || index == (fileName.length() - 1)) ? 
            null : fileName.substring(index + 1);
        String extension = "*";
        if (fileExtension != null) {
            extension = "*." + fileExtension;
        }
        String path = relPath.toString() + "/" + extension;
        if (path.startsWith("/")) {
            path = extension;
        }
        if (gitAttributePathCache.contains(path)) {
            return null;
        }
        gitAttributePathCache.add(path);
        return path + " zos-working-tree-encoding=" + encoding + " git-encoding=utf-8";
    }
    
    private boolean isMappingFileSpecified(String argString) {
        boolean filesExist = true;
        for (String path : argString.split(",")) {
            File file = new File(path);
            if (!file.exists()) {
                filesExist = false;
                break;
            }
        }
        return filesExist;
    }
    
    /**
     * Detect whether a member contains record that contains non-roundtripable characters
     * (line separator or an empty Shift-Out Shift-In) and optionally whether a member 
     * contains a record that contains any non-printable characters (below 0x40)
     * 
     * @param dataset the data set contains the member to test
     * @param member the member to test
     * @return Return Code: 0 = No Errors found, 4 = [info] non-printable character detected,
     *         8 = [warning] non-printable or non-roundtripable char detected, 12 = internal error
     */
    private int detector(String dataset, String member) {
        emptyMessageFile();
        String fullyQualifiedDsn = constructDatasetForZFileOperation(dataset, member);
        RecordReader reader = null;
        
        try {
            reader = RecordReader.newReader(fullyQualifiedDsn, ZFileConstants.FLAG_DISP_SHR);
            int lrecl = reader.getLrecl();
            byte[] buf = new byte[lrecl];
            int line = 0;
            
            boolean foundNonRoundtripable = false;
            boolean foundNonPrintable = false;
            
            int numBytesRead;
            while ((numBytesRead = reader.read(buf)) >= 0) {
                line++;
                int prevIndex = -1;
                StringBuilder nrMsg = new StringBuilder();
                StringBuilder npMsg = new StringBuilder();
                StringBuilder errorMessage = new StringBuilder();
                int countNR = 0;
                int countNP = 0;
                List<Integer> npColumnList = new ArrayList<>();
                List<Integer> nrColumnList = new ArrayList<>();
                
                // Find NL, CR, LF, empty SO&SI, optionally non-printable chars in a record
                for (int i = 0; i < numBytesRead; i++) {
                    // Check for non-roundtrippable line separators: NL, CR, LF
                    if (buf[i] == CHAR_NL || buf[i] == CHAR_CR || buf[i] == CHAR_LF || buf[i] == CHAR_DEL) {
                        nrMsg.append(String.format("        Char 0x%02X at column %d\n", buf[i], i + 1));
                        nrColumnList.add(i + 1);
                        countNR++;
                    }
                    // Check for non-roundtrippable empty Shift Out and Shift In
                    else if (buf[i] == CHAR_SHIFT_OUT) {
                        prevIndex = i;
                    } else if (buf[i] == CHAR_SHIFT_IN && prevIndex != -1 && prevIndex == (i - 1)) {
                        nrMsg.append(String.format("        Empty Shift Out and Shift In at column %d\n", prevIndex + 1));
                        nrColumnList.add(prevIndex + 1);
                        countNR++;
                    }
                    // Check for other non-printable chars in a record (less than 0x40)
                    else if (npLevel != null && Integer.compareUnsigned(0x40, buf[i] & 0xFF) > 0) {
                        npMsg.append(String.format("        Char 0x%02X at column %d\n", buf[i], i + 1));
                        npColumnList.add(i + 1);
                        countNP++;
                    }
                }
                
                if (countNR >= 1) {
                    errorMessage.append("      Line ").append(line).append(" contains non-roundtripable characters:\n").append(nrMsg);
                    foundNonRoundtripable = true;
                    printTable(buf, errorMessage, lrecl, nrColumnList);
                }
                
                if (countNP >= 1) {
                    errorMessage.append("      Line ").append(line).append(" contains non-printable characters:\n").append(npMsg);
                    foundNonPrintable = true;
                    printTable(buf, errorMessage, lrecl, npColumnList);
                }
                
                if (errorMessage.length() > 0) {
                    // Append error messages to temporary messageFile
                    try (FileWriter fw = new FileWriter(messageFile, true)) {
                        fw.write(errorMessage.toString());
                    }
                }
            }
            
            if (foundNonRoundtripable) {
                return 8;
            }
            if (foundNonPrintable) {
                if ("info".equals(npLevel)) {
                    return 4;
                }
                if ("warning".equals(npLevel)) {
                    return 8;
                }
            }
        } catch (IOException e) {
            // Print IOException to temporary file
            try (FileWriter fw = new FileWriter(messageFile, true)) {
                fw.write(e.getMessage());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            return 12;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return 0;
    }
    
    private String constructDatasetForZFileOperation(String dataset, String member) {
        return "//'" + dataset + "(" + member + ")'";
    }
    
    private void printlnLog(String message) {
        logger.logMessage(message);
    }
    
    private void printMessageFile() {
        try (BufferedReader reader = new BufferedReader(new FileReader(messageFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                printlnLog(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void emptyMessageFile() {
        try (PrintWriter writer = new PrintWriter(messageFile)) {
            writer.print("");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Print a table to visualize errors in records with non-printable 
     * and non-roundtrippable characters.
     */
    private void printTable(byte[] buf, StringBuilder errorMessage, int lrecl, List<Integer> columns) {
        if (!hasErrorTable) {
            return;
        }
        
        StringBuilder line1 = new StringBuilder();
        StringBuilder line2 = new StringBuilder();
        StringBuilder line3 = new StringBuilder();
        StringBuilder line4 = new StringBuilder();
        StringBuilder line5 = new StringBuilder();
        
        // Generate header
        for (int i = 1; i <= lrecl; i++) {
            String s;
            if ((i % 10) == 0) {
                s = String.valueOf((i / 10) % 10);
            } else if ((i % 5) == 0) {
                s = "+";
            } else {
                s = "-";
            }
            line1.append(s);
        }
        line1.append("\n");
        
        // Print line, replace bad characters with spaces
        String bufString = new String(buf);
        for (int i = 0; i < bufString.length(); i++) {
            if (columns.contains(i + 1)) {
                line2.append(" ");
            } else {
                line2.append(bufString.charAt(i));
            }
        }
        line2.append("\n");
        
        // Generate hex lines
        for (byte b : buf) {
            String hex = String.format("%02X", b);
            if (hex.length() != 2) {
                System.err.println("ERROR hex length is not two.");
            }
            line3.append(hex.substring(0, 1));
            line4.append(hex.substring(1));
        }
        line3.append("\n");
        line4.append("\n");
        
        // Generate indicators over bad characters
        for (int i = 1; i <= lrecl; i++) {
            if (columns.contains(i)) {
                line5.append("^");
            } else {
                line5.append(" ");
            }
        }
        line5.append("\n");
        
        errorMessage.append(line1).append(line2).append(line3).append(line4).append(line5);
    }
}