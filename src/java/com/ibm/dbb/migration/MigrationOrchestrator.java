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
 * Main orchestrator class for the DBB Git Migration Modeler.
 * Replaces the Migration-Modeler-Start-Java.sh shell script by calling
 * Java classes directly without intermediate shell scripts.
 */
public class MigrationOrchestrator {
    
    private String configFile;
    private String applicationFilter;
    private Properties config;
    private boolean interactiveMode;
    private int exitCode = 0;
    private String migrationModelerRelease;
    private Scanner scanner; // Reusable scanner for interactive prompts
    
    public static void main(String[] args) {
        MigrationOrchestrator orchestrator = new MigrationOrchestrator();
        orchestrator.run(args);
        System.exit(orchestrator.exitCode);
    }
    
    public void run(String[] args) {
        // Parse command line arguments
        if (!parseArguments(args)) {
            return;
        }
        
        // Load configuration
        if (!loadConfiguration()) {
            return;
        }
        
        // Print prolog
        printProlog();
        
        // Validate configuration
        if (!validateConfiguration()) {
            return;
        }
        
        // Phase 0: Cleanup working directories
        if (!cleanupWorkingDirectories()) {
            return;
        }
        
        // Phase 1: Extract applications
        if (!extractApplications()) {
            return;
        }
        
        // Phase 2: Run migrations
        if (!runMigrations()) {
            return;
        }
        
        // Phase 3: Classify and assess usage
        if (!classifyAndAssess()) {
            return;
        }
        
        // Phase 4: Generate build properties
        if (!generateBuildProperties()) {
            return;
        }
        
        // Phase 5: Initialize application repositories
        boolean repositoriesInitialized = initializeRepositories();
        
        // Phase 6: Print summary
        printSummary(repositoriesInitialized);
    }
    
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
        
        File configFileObj = new File(configFile);
        if (!configFileObj.exists()) {
            System.err.println("[ERROR] Configuration file not found: " + configFile);
            exitCode = 8;
            return false;
        }
        
        return true;
    }
    
    private void printUsage() {
        System.out.println();
        System.out.println("Usage: java -jar dbb-git-migration-modeler.jar [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -c <file>    Configuration file (required)");
        System.out.println("  -a <apps>    Comma-separated list of applications to process (optional)");
        System.out.println("  -h, --help   Display this help message");
        System.out.println();
    }
    
    private boolean loadConfiguration() {
        config = new Properties();
        try (FileInputStream fis = new FileInputStream(configFile)) {
            config.load(fis);
            interactiveMode = "true".equalsIgnoreCase(config.getProperty("INTERACTIVE_RUN", "false"));
            
            // Load release version
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
        System.out.println(" Class:       MigrationOrchestrator");
        System.out.println();
        System.out.println(" Description: Orchestrates the 5-phase migration process:");
        System.out.println("              1. Extract applications");
        System.out.println("              2. Run migrations");
        System.out.println("              3. Classify and assess usage");
        System.out.println("              4. Generate build properties");
        System.out.println("              5. Initialize application repositories");
        System.out.println();
        System.out.println(" For more information: https://github.com/IBM/dbb-git-migration-modeler");
        System.out.println();
    }
    
    private boolean validateConfiguration() {
        System.out.println("[PHASE] Validating configuration");
        try {
            ValidateConfiguration.validateAndLoadConfiguration(configFile);
            return true;
        } catch (Exception e) {
            System.err.println("[ERROR] Configuration validation failed: " + e.getMessage());
            exitCode = 8;
            return false;
        }
    }
    
    private boolean cleanupWorkingDirectories() {
        System.out.println();
        System.out.println("[PHASE] Cleanup working directories");
        
        if (interactiveMode) {
            String workDir = config.getProperty("DBB_MODELER_WORK");
            if (!promptUser("Do you want to clean the working directory '" + workDir + "' (Y/n): ")) {
                return true;
            }
        }
        
        try {
            deleteDirectory(config.getProperty("DBB_MODELER_APPCONFIG_DIR"));
            // deleteDirectory(config.getProperty("DBB_MODELER_APPLICATION_DIR"));
            deleteDirectory(config.getProperty("DBB_MODELER_LOGS"));
            deleteDirectory(config.getProperty("DBB_MODELER_BUILD_CONFIGURATION"));

            // Cleanup metadata store if file-based
            if (config.getProperty("DBB_MODELER_METADATASTORE_TYPE").equalsIgnoreCase("file")) {
                String metadataStoreDir = config.getProperty("DBB_MODELER_FILE_METADATA_STORE_DIR");
                if (metadataStoreDir != null) {
                    deleteDirectory(metadataStoreDir);
                    Files.createDirectories(Paths.get(metadataStoreDir));
                }
            }
            
            // Create logs directory
            String logsDir = config.getProperty("DBB_MODELER_LOGS");
            if (logsDir != null) {
                Files.createDirectories(Paths.get(logsDir));
                System.out.println("[INFO] Created '" + logsDir + "' folder");
            }
            
            return true;
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to cleanup directories: " + e.getMessage());
            exitCode = 8;
            return false;
        }
    }
    
    private boolean extractApplications() {
        System.out.println();
        System.out.println("[PHASE] Extract applications from Applications Mapping files");
        
        if (interactiveMode && !promptUser("Do you want to run the application extraction (Y/n): ")) {
            return true;
        }
        
        try {
            // Create required directories
            String appConfigDir = config.getProperty("DBB_MODELER_APPCONFIG_DIR");
            String appDir = config.getProperty("DBB_MODELER_APPLICATION_DIR");
            
            if (appConfigDir != null) {
                Files.createDirectories(Paths.get(appConfigDir));
            }
            if (appDir != null) {
                Files.createDirectories(Paths.get(appDir));
            }
            
            System.out.println("*******************************************************************");
            System.out.println("Extract applications using Java implementation");
            System.out.println("*******************************************************************");
            
            String logsDir = config.getProperty("DBB_MODELER_LOGS");

            List<String> args = new ArrayList<>();
            args.add("-c");
            args.add(configFile);
            if (applicationFilter != null && !applicationFilter.isEmpty()) {
                args.add("-a");
                args.add(applicationFilter);
            }
            if (logsDir != null) {
                args.add("-l");
                args.add(logsDir + "/1-extractApplications.log");
            }
            
            ExtractApplications.main(args.toArray(new String[0]));
            return true;
        } catch (Exception e) {
            System.err.println("[ERROR] Application extraction failed: " + e.getMessage());
            exitCode = 8;
            return false;
        }
    }
    
    private boolean runMigrations() {
        System.out.println();
        System.out.println("[PHASE] Execute migrations using DBB Migration mapping files");
        
        if (interactiveMode && !promptUser("Do you want to execute the migration (Y/n): ")) {
            return true;
        }
        
        try {
            String appConfigDir = config.getProperty("DBB_MODELER_APPCONFIG_DIR");
            String appDir = config.getProperty("DBB_MODELER_APPLICATION_DIR");
            String logsDir = config.getProperty("DBB_MODELER_LOGS");
            
            Set<String> filterSet = parseApplicationFilter(applicationFilter);
            
            File configDirFile = new File(appConfigDir);
            File[] mappingFiles = configDirFile.listFiles((dir, name) -> name.endsWith(".mapping"));
            
            if (mappingFiles != null) {
                for (File mappingFile : mappingFiles) {
                    String appName = mappingFile.getName().replace(".mapping", "");
                    
                    if (filterSet.isEmpty() || filterSet.contains(appName)) {
                        System.out.println("*******************************************************************");
                        System.out.println("Running DBB Migration Utility for '" + appName + "'");
                        System.out.println("*******************************************************************");
                        
                        File appDirFile = new File(appDir, appName);
                        if (!appDirFile.exists()) {
                            appDirFile.mkdirs();
                        }
                        
                        String[] args = {
                            "-l", logsDir + "/2-" + appName + ".migration.log",
                            "-np", "info",
                            "-r", appDir + "/" + appName,
                            mappingFile.getAbsolutePath()
                        };
                        
                        MigrateDatasets.main(args);
                    }
                }
            }
            
            return true;
        } catch (Exception e) {
            System.err.println("[ERROR] Migration execution failed: " + e.getMessage());
            exitCode = 8;
            return false;
        }
    }
    
    private boolean classifyAndAssess() {
        System.out.println();
        System.out.println("[PHASE] Assess usage and perform classification");
        
        if (interactiveMode && !promptUser("Do you want to perform usage assessment (Y/n): ")) {
            return true;
        }
        
        try {
            String appDir = config.getProperty("DBB_MODELER_APPLICATION_DIR");
            String logsDir = config.getProperty("DBB_MODELER_LOGS");
                        
            Set<String> filterSet = parseApplicationFilter(applicationFilter);
            File appDirFile = new File(appDir);
            File[] appDirs = appDirFile.listFiles(File::isDirectory);
            
            if (appDirs != null) {
                // Phase 3a: Scan applications
                for (File dir : appDirs) {
                    String appName = dir.getName();
                    if (appName.equals("dbb-zappbuild")) continue;
                    
                    if (filterSet.isEmpty() || filterSet.contains(appName)) {
                        System.out.println("*******************************************************************");
                        System.out.println("Scan application directory '" + appName + "'");
                        System.out.println("*******************************************************************");
                        
                        String[] args = {
                            "-c", configFile,
                            "-a", appName,
                            "-l", logsDir + "/3-" + appName + "-scan.log"
                        };
                        
                        ScanApplication.main(args);
                    }
                }
                
                // Phase 3b: Assess usage
                for (File dir : appDirs) {
                    String appName = dir.getName();
                    if (appName.equals("dbb-zappbuild")) continue;
                    
                    if (filterSet.isEmpty() || filterSet.contains(appName)) {
                        System.out.println("*******************************************************************");
                        System.out.println("Assess Include files & Programs usage for '" + appName + "'");
                        System.out.println("*******************************************************************");
                        
                        String[] args = {
                            "-c", configFile,
                            "-a", appName,
                            "-l", logsDir + "/3-" + appName + "-assessUsage.log"
                        };
                        
                        AssessUsage.main(args);
                    }
                }
            }
            
            return true;
        } catch (Exception e) {
            System.err.println("[ERROR] Classification failed: " + e.getMessage());
            exitCode = 8;
            return false;
        }
    }
    
    private boolean generateBuildProperties() {
        System.out.println();
        System.out.println("[PHASE] Generate build configuration");
        
        String buildFramework = config.getProperty("BUILD_FRAMEWORK");
        if (interactiveMode && !promptUser("Do you want to generate " + buildFramework + " configuration (Y/n): ")) {
            return true;
        }
        
        try {
            String appDir = config.getProperty("DBB_MODELER_APPLICATION_DIR");
            String logsDir = config.getProperty("DBB_MODELER_LOGS");
            
            Set<String> filterSet = parseApplicationFilter(applicationFilter);
            File appDirFile = new File(appDir);
            File[] appDirs = appDirFile.listFiles(File::isDirectory);
            
            if (appDirs != null) {
                for (File dir : appDirs) {
                    String appName = dir.getName();
                    if (appName.equals("dbb-zappbuild")) continue;
                    
                    if (filterSet.isEmpty() || filterSet.contains(appName)) {
                        System.out.println("*******************************************************************");
                        System.out.println("Generate properties for application '" + appName + "'");
                        System.out.println("*******************************************************************");
                        
                        String[] args = {
                            "-c", configFile,
                            "-a", appName,
                            "-l", logsDir + "/4-" + appName + "-generateProperties.log"
                        };
                        
                        if ("zBuilder".equalsIgnoreCase(buildFramework)) {
                            GenerateZBuilderProperties.main(args);
                        } else {
                            System.err.println("[ERROR] Unknown BUILD_FRAMEWORK: " + buildFramework);
                            exitCode = 8;
                            return false;
                        }
                    }
                }
            }
            
            return true;
        } catch (Exception e) {
            System.err.println("[ERROR] Property generation failed: " + e.getMessage());
            exitCode = 8;
            return false;
        }
    }
    
    private boolean initializeRepositories() {
        System.out.println();
        System.out.println("[PHASE] Initialize application repositories");
        
        if (interactiveMode && !promptUser("Do you want to initialize repositories (Y/n): ")) {
            return false;
        }
        
        try {
            String appDir = config.getProperty("DBB_MODELER_APPLICATION_DIR");
            String logsDir = config.getProperty("DBB_MODELER_LOGS");
            
            Set<String> filterSet = parseApplicationFilter(applicationFilter);
            File appDirFile = new File(appDir);
            File[] appDirs = appDirFile.listFiles(File::isDirectory);
            
            if (appDirs != null) {
                for (File dir : appDirs) {
                    String appName = dir.getName();
                    if (appName.equals("dbb-zappbuild")) continue;
                    
                    if (filterSet.isEmpty() || filterSet.contains(appName)) {
                        System.out.println("*******************************************************************");
                        System.out.println("Initialize repository for '" + appName + "'");
                        System.out.println("*******************************************************************");
                        
                        String[] args = {
                            "-c", configFile,
                            "-a", appName,
                            "-l", logsDir + "/5-" + appName + "-initApplicationRepository.log"
                        };
                        
                        InitApplicationRepository.main(args);
                    }
                }
            }
            
            return true;
        } catch (Exception e) {
            System.err.println("[ERROR] Repository initialization failed: " + e.getMessage());
            exitCode = 8;
            return false;
        }
    }
    
    private void printSummary(boolean repositoriesInitialized) {
        System.out.println();
        System.out.println("[PHASE] Summary");
        
        String appDir = config.getProperty("DBB_MODELER_APPLICATION_DIR");
        String[] args = {"-a", appDir};
        
        try {
            CalculateDependenciesOrder.main(args);
        } catch (Exception e) {
            System.err.println("[WARN] Failed to calculate dependencies order: " + e.getMessage());
        }
        
        if (repositoriesInitialized) {
            String pipelineCI = config.getProperty("PIPELINE_CI", "None");
            String gitDistribution, pipelineOrchestrator;
            
            switch (pipelineCI) {
                case "AzureDevOps":
                    gitDistribution = "Azure DevOps platform";
                    pipelineOrchestrator = "Azure DevOps";
                    break;
                case "GitlabCI":
                    gitDistribution = "GitLab platform";
                    pipelineOrchestrator = "GitLab CI";
                    break;
                case "GitHubActions":
                    gitDistribution = "GitHub platform";
                    pipelineOrchestrator = "GitHub Actions";
                    break;
                default:
                    gitDistribution = "Git Central server";
                    pipelineOrchestrator = "Pipeline Orchestrator";
                    break;
            }
            
            System.out.println();
            System.out.println("***********************************************************************************************************");
            System.out.println("*************************************    What needs to be done now    *************************************");
            System.out.println("***********************************************************************************************************");
            System.out.println();
            System.out.println("For each application:");
            System.out.println("- Create a Git project in your " + gitDistribution);
            System.out.println("- Add a remote configuration using 'git remote add' command");
            System.out.println("- Initialize the " + pipelineOrchestrator + " variables in pipeline configuration");
            System.out.println("- Push the application's Git repository in dependency order");
            System.out.println();
            System.out.println("***********************************************************************************************************");
        }
    }
    
    // Helper methods
    
    private boolean promptUser(String message) {
        System.out.print(message);
        System.out.flush();
        
        // Initialize scanner if not already done
        if (scanner == null) {
            scanner = new Scanner(System.in);
        }
        
        try {
            // Check if input is available
            if (!scanner.hasNextLine()) {
                System.out.println("Y (no input available, defaulting to Yes)");
                return true;
            }
            
            String response = scanner.nextLine().trim();
            return response.isEmpty() || response.toLowerCase().startsWith("y");
        } catch (NoSuchElementException e) {
            // No input available (e.g., stdin closed or redirected)
            System.out.println("Y (no input available, defaulting to Yes)");
            return true;
        } catch (IllegalStateException e) {
            // Scanner closed
            System.out.println("Y (scanner closed, defaulting to Yes)");
            return true;
        }
    }
    
    private void deleteDirectory(String dirPath) throws IOException {
        if (dirPath != null) {
            Path path = Paths.get(dirPath);
            if (Files.exists(path)) {
                Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
                System.out.println("[INFO] Removed '" + dirPath + "' folder");
            }
        }
    }
    
    private Set<String> parseApplicationFilter(String filter) {
        Set<String> apps = new HashSet<>();
        if (filter != null && !filter.trim().isEmpty()) {
            String[] appArray = filter.split(",");
            for (String app : appArray) {
                apps.add(app.trim());
            }
        }
        return apps;
    }
}
