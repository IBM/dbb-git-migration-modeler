/********************************************************************************
 * Licensed Materials - Property of IBM                                          *
 * (c) Copyright IBM Corporation 2018, 2025. All Rights Reserved.                *
 *                                                                               *
 * Note to U.S. Government Users Restricted Rights:                              *
 * Use, duplication or disclosure restricted by GSA ADP Schedule                 *
 * Contract with IBM Corp.                                                       *
 ********************************************************************************/

package com.ibm.dbb.migration;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Orchestrator for the Application Descriptor refresh workflow.
 *
 * Replaces the Refresh-Application-Descriptor-Files.sh shell script.
 * The workflow mirrors the original shell script exactly:
 *
 *   1. Reset the file-based MetadataStore (if applicable).
 *   2. Scan all applications with {@link ScanApplication}.
 *   3. Reset and recreate Application Descriptor files with {@link RecreateApplicationDescriptor}.
 *   4. Assess Include File and Program usage with {@link AssessUsage}.
 *   5. Reset the file-based MetadataStore again, then re-scan all applications
 *      so that the final MetadataStore reflects the post-assessment state.
 */
public class RefreshApplicationDescriptorOrchestrator {

    private String configFile;
    private String applicationFilter;
    private Properties config;
    private int exitCode = 0;
    private String migrationModelerRelease;

    public static void main(String[] args) {
        RefreshApplicationDescriptorOrchestrator orchestrator = new RefreshApplicationDescriptorOrchestrator();
        orchestrator.run(args);
        System.exit(orchestrator.exitCode);
    }

    public void run(String[] args) {
        if (!parseArguments(args)) {
            return;
        }
        if (!loadConfiguration()) {
            return;
        }
        printProlog();

        // Step 1 — reset file MetadataStore before first scan
        if (!resetFileMetadataStore("before scan")) {
            return;
        }

        // Step 2 — scan all applications
        if (!scanApplications("scan")) {
            return;
        }

        // Step 3 — recreate Application Descriptors
        if (!recreateApplicationDescriptors()) {
            return;
        }

        // Step 4 — assess usage
        if (!assessUsage()) {
            return;
        }

        // Step 5 — reset file MetadataStore, then re-scan so the store is clean
        if (!resetFileMetadataStore("after assessment")) {
            return;
        }
        scanApplications("rescan");   // best-effort; don't abort on failure
    }

    // -------------------------------------------------------------------------
    // Argument parsing
    // -------------------------------------------------------------------------

    private boolean parseArguments(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if ("-c".equals(args[i]) && i + 1 < args.length) {
                configFile = args[++i];
            } else if ("-a".equals(args[i]) && i + 1 < args.length) {
                applicationFilter = args[++i];
            } else if ("-h".equals(args[i]) || "--help".equals(args[i])) {
                printUsage();
                return false;
            }
        }

        if (configFile == null || configFile.isEmpty()) {
            System.err.println("[ERROR] Configuration file (-c) is required.");
            printUsage();
            exitCode = 8;
            return false;
        }

        if (!new File(configFile).exists()) {
            System.err.println("[ERROR] Configuration file not found: " + configFile);
            exitCode = 8;
            return false;
        }

        return true;
    }

    private void printUsage() {
        System.out.println();
        System.out.println("Usage: java -cp <classpath> com.ibm.dbb.migration.RefreshApplicationDescriptorOrchestrator [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -c <file>    Configuration file (required)");
        System.out.println("  -a <apps>    Comma-separated list of applications to process (optional)");
        System.out.println("  -h, --help   Display this help message");
        System.out.println();
    }

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    private boolean loadConfiguration() {
        config = new Properties();
        try (FileInputStream fis = new FileInputStream(configFile)) {
            config.load(fis);

            String dbbModelerHome = System.getProperty("dbb.modeler.home");
            if (dbbModelerHome != null) {
                File releaseFile = new File(dbbModelerHome, "release.properties");
                if (releaseFile.exists()) {
                    Properties releaseProps = new Properties();
                    try (FileInputStream rfis = new FileInputStream(releaseFile)) {
                        releaseProps.load(rfis);
                        migrationModelerRelease = releaseProps.getProperty("Migration-Modeler-release", "Unknown");
                    }
                }
            }

            return true;
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to load configuration file: " + e.getMessage());
            exitCode = 8;
            return false;
        }
    }

    private void printProlog() {
        System.out.println();
        System.out.println(" DBB Git Migration Modeler (Java Edition)");
        System.out.println(" Release:     " + (migrationModelerRelease != null ? migrationModelerRelease : "Unknown"));
        System.out.println();
        System.out.println(" Class:       RefreshApplicationDescriptorOrchestrator");
        System.out.println();
        System.out.println(" Description: Refreshes Application Descriptor files for existing applications.");
        System.out.println("              Scans artifacts, resets source groups and re-runs usage assessment.");
        System.out.println("              Inspects all folders within the DBB_MODELER_APPLICATION_DIR.");
        System.out.println();
        System.out.println("              Customize this process as needed when applications are already");
        System.out.println("              migrated to a central Git provider.");
        System.out.println("              For more information: https://github.com/IBM/dbb-git-migration-modeler");
        System.out.println();
    }

    // -------------------------------------------------------------------------
    // MetadataStore management
    // -------------------------------------------------------------------------

    /**
     * Drop and recreate the file-based MetadataStore directory, if configured.
     * When using a Db2-based MetadataStore, collections are dropped by the
     * individual Java classes (ScanApplication), so nothing is done here.
     */
    private boolean resetFileMetadataStore(String context) {
        String metadataStoreType = config.getProperty("DBB_MODELER_METADATASTORE_TYPE", "file");
        if (!"file".equalsIgnoreCase(metadataStoreType)) {
            return true;  // Db2 — nothing to reset here
        }

        String metadataStoreDir = config.getProperty("DBB_MODELER_FILE_METADATA_STORE_DIR");
        if (metadataStoreDir == null || metadataStoreDir.isEmpty()) {
            return true;
        }

        try {
            Path path = Paths.get(metadataStoreDir);
            if (Files.exists(path)) {
                Files.walk(path)
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                System.out.println("[INFO] Removed MetadataStore directory '" + metadataStoreDir + "' (" + context + ")");
            }
            Files.createDirectories(path);
            System.out.println("[INFO] Created MetadataStore directory '" + metadataStoreDir + "' (" + context + ")");
            return true;
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to reset MetadataStore directory: " + e.getMessage());
            exitCode = 8;
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Phase implementations
    // -------------------------------------------------------------------------

    private boolean scanApplications(String logSuffix) {
        System.out.println();
        System.out.println("[PHASE] Scan application directories (" + logSuffix + ")");

        try {
            String appDir = config.getProperty("DBB_MODELER_APPLICATION_DIR");
            String logsDir = config.getProperty("DBB_MODELER_LOGS");
            Set<String> filterSet = parseApplicationFilter(applicationFilter);

            File[] appDirs = listApplicationDirectories(appDir);
            if (appDirs == null) return true;

            for (File dir : appDirs) {
                String appName = dir.getName();
                if (filterSet.isEmpty() || filterSet.contains(appName)) {
                    System.out.println("*******************************************************************");
                    System.out.println("Scan application directory '" + appName + "'");
                    System.out.println("*******************************************************************");

                    String[] args = buildArgs(appName, logsDir + "/3-" + appName + "-" + logSuffix + ".log");
                    ScanApplication.main(args);
                }
            }
            return true;
        } catch (Exception e) {
            System.err.println("[ERROR] Scan phase failed: " + e.getMessage());
            exitCode = 8;
            return false;
        }
    }

    private boolean recreateApplicationDescriptors() {
        System.out.println();
        System.out.println("[PHASE] Reset and recreate Application Descriptor files");

        try {
            String appDir = config.getProperty("DBB_MODELER_APPLICATION_DIR");
            String logsDir = config.getProperty("DBB_MODELER_LOGS");
            Set<String> filterSet = parseApplicationFilter(applicationFilter);

            File[] appDirs = listApplicationDirectories(appDir);
            if (appDirs == null) return true;

            for (File dir : appDirs) {
                String appName = dir.getName();
                if (filterSet.isEmpty() || filterSet.contains(appName)) {
                    System.out.println("*******************************************************************");
                    System.out.println("Reset Application Descriptor for '" + appName + "'");
                    System.out.println("*******************************************************************");

                    String[] args = buildArgs(appName, logsDir + "/3-" + appName + "-createApplicationDescriptor.log");
                    RecreateApplicationDescriptor.main(args);
                }
            }
            return true;
        } catch (Exception e) {
            System.err.println("[ERROR] Recreate Application Descriptor phase failed: " + e.getMessage());
            exitCode = 8;
            return false;
        }
    }

    private boolean assessUsage() {
        System.out.println();
        System.out.println("[PHASE] Assess Include files & Programs usage");

        try {
            String appDir = config.getProperty("DBB_MODELER_APPLICATION_DIR");
            String logsDir = config.getProperty("DBB_MODELER_LOGS");
            Set<String> filterSet = parseApplicationFilter(applicationFilter);

            File[] appDirs = listApplicationDirectories(appDir);
            if (appDirs == null) return true;

            for (File dir : appDirs) {
                String appName = dir.getName();
                if (filterSet.isEmpty() || filterSet.contains(appName)) {
                    System.out.println("*******************************************************************");
                    System.out.println("Assess Include files & Programs usage for '" + appName + "'");
                    System.out.println("*******************************************************************");

                    String[] args = buildArgs(appName, logsDir + "/3-" + appName + "-assessUsage.log");
                    AssessUsage.main(args);
                }
            }
            return true;
        } catch (Exception e) {
            System.err.println("[ERROR] Assess usage phase failed: " + e.getMessage());
            exitCode = 8;
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private File[] listApplicationDirectories(String appDir) {
        File appDirFile = new File(appDir);
        File[] dirs = appDirFile.listFiles(f -> f.isDirectory() && !f.getName().equals("dbb-zappbuild"));
        if (dirs != null) {
            Arrays.sort(dirs, Comparator.comparing(File::getName));
        }
        return dirs;
    }

    /**
     * Build the standard [-c, configFile, -a, appName, -l, logFile] argument array.
     */
    private String[] buildArgs(String appName, String logFile) {
        return new String[]{
            "-c", configFile,
            "-a", appName,
            "-l", logFile
        };
    }

    private Set<String> parseApplicationFilter(String filter) {
        Set<String> apps = new HashSet<>();
        if (filter != null && !filter.trim().isEmpty()) {
            for (String app : filter.split(",")) {
                apps.add(app.trim());
            }
        }
        return apps;
    }
}
