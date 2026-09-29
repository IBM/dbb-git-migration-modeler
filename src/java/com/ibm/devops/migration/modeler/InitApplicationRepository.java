/*
 * Licensed Materials - Property of IBM
 * (c) Copyright IBM Corporation 2018, 2025. All Rights Reserved.
 *
 * Note to U.S. Government Users Restricted Rights:
 * Use, duplication or disclosure restricted by GSA ADP Schedule
 * Contract with IBM Corp.
 */

package com.ibm.devops.migration.modeler;

import com.ibm.devops.migration.modeler.utils.Logger;
import com.ibm.devops.migration.modeler.utils.MetadataStoreUtility;
import com.ibm.devops.migration.modeler.utils.ZappUtility;
import com.ibm.devops.migration.modeler.utils.ApplicationDescriptorUtils;
import com.ibm.devops.migration.modeler.utils.FileUtility;
import com.ibm.devops.migration.modeler.model.ApplicationDescriptor;
import com.ibm.dbb.build.BuildException;
import com.ibm.dbb.build.report.BuildReport;
import com.ibm.dbb.build.report.records.ExecuteRecord;
import com.ibm.dbb.build.report.records.Record;
import com.ibm.jzos.ZFile;
import org.apache.commons.cli.*;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Initializes Git repository for a single migrated application.
 * Equivalent to the 5-initApplicationRepositories.sh shell script.
 */
public class InitApplicationRepository {
    
    private String configFilePath;
    private String applicationFilter;
    private Properties configProperties;
    private int exitCode = 0;
    private Logger logger;
    private String localZBuilderPath;
    
    public static void main(String[] args) {
        InitApplicationRepository initializer = new InitApplicationRepository();
        try {
            initializer.run(args);
        } catch (Exception e) {
            System.err.println("[ERROR] Repository initialization failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(8);
        }
    }
    
    public void run(String[] args) throws Exception {
        // Initialize logger
        logger = new Logger();
        
        // Parse command line options
        Options options = createOptions();
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        
        CommandLine cmd;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            logger.logMessage("[ERROR] Error parsing command line: " + e.getMessage());
            formatter.printHelp("InitApplicationRepository [options]",
                "Initializes Git repository for a migrated application",
                options, "", true);
            System.exit(2);
            return;
        }
        
        // Process options
        if (cmd.hasOption("c")) {
            configFilePath = cmd.getOptionValue("c");
        } else {
            logger.logMessage("[ERROR] Configuration file option (-c) is required.");
            formatter.printHelp("InitApplicationRepository [options]", options);
            System.exit(2);
            return;
        }
        
        if (cmd.hasOption("a")) {
            applicationFilter = cmd.getOptionValue("a");
        } else {
            logger.logMessage("[ERROR] Application name option (-a) is required.");
            formatter.printHelp("InitApplicationRepository [options]", options);
            System.exit(2);
            return;
        }
        
        if (cmd.hasOption("l")) {
            String logFile = cmd.getOptionValue("l");
            try {
                logger.create(logFile);
            } catch (IOException e) {
                logger.logMessage("[ERROR] Failed to create log file: " + e.getMessage());
                System.exit(8);
            }
        }
        
        // Validate options and load configuration
        validateOptions();
        
        if (exitCode == 0) {
            initializeRepositories();
        }
        
        if (exitCode != 0) {
            logger.logMessage("[ERROR] Repository initialization failed. rc=" + exitCode);
            logger.close();
            System.exit(exitCode);
        }
        
        logger.close();
    }
    
    private Options createOptions() {
        Options options = new Options();
        
        options.addOption(Option.builder("c")
            .longOpt("config")
            .hasArg()
            .argName("configFile")
            .desc("DBB Git Migration Modeler configuration file (required)")
            .required()
            .build());
            
        options.addOption(Option.builder("a")
            .longOpt("application")
            .hasArg()
            .argName("appName")
            .desc("Application name to initialize (required)")
            .required()
            .build());
            
        options.addOption(Option.builder("l")
            .longOpt("logFile")
            .hasArg()
            .argName("logFile")
            .desc("Relative or absolute path to an output log file (optional)")
            .build());
            
        return options;
    }
    
    private void validateOptions() {
        if (configFilePath == null || configFilePath.isEmpty()) {
            exitCode = 8;
            logger.logMessage("[ERROR] Configuration file path is required. rc=" + exitCode);
            return;
        }
        
        File configFile = new File(configFilePath);
        if (!configFile.exists()) {
            exitCode = 8;
            logger.logMessage("[ERROR] Configuration file not found: " + configFilePath + ". rc=" + exitCode);
            return;
        }
        
        // Load and validate configuration
        try {
            configProperties = ValidateConfiguration.validateAndLoadConfiguration(configFilePath);
        } catch (Exception e) {
            exitCode = 8;
            logger.logMessage("[ERROR] Configuration validation failed: " + e.getMessage() + ". rc=" + exitCode);
        }
    }
    
    private void initializeRepositories() {
        String applicationDir = configProperties.getProperty("DBB_MODELER_APPLICATION_DIR");
        String logsDir = configProperties.getProperty("DBB_MODELER_LOGS");
        String defaultBranch = configProperties.getProperty("APPLICATION_DEFAULT_BRANCH", "main");
        String currentBranch = configProperties.getProperty("APPLICATION_CURRENT_BRANCH", "main");

        // Resolve the local zBuilder copy that was placed in the work folder by MigrationOrchestrator
        String workDir = configProperties.getProperty("DBB_MODELER_WORK");
        localZBuilderPath = workDir + "/zBuilder";

        if (applicationDir == null || applicationDir.isEmpty()) {
            exitCode = 8;
            logger.logMessage("[ERROR] DBB_MODELER_APPLICATION_DIR not configured. rc=" + exitCode);
            return;
        }

        if (applicationFilter == null || applicationFilter.isEmpty()) {
            exitCode = 8;
            logger.logMessage("[ERROR] Application name is required. rc=" + exitCode);
            return;
        }

        String appName = applicationFilter.trim();
        File appRepoDir = new File(applicationDir, appName);

        if (!appRepoDir.exists() || !appRepoDir.isDirectory()) {
            exitCode = 8;
            logger.logMessage("[ERROR] Application directory does not exist: " + appRepoDir.getAbsolutePath() + ". rc=" + exitCode);
            return;
        }

        String logFile = logsDir + File.separator + "5-" + appName + "-initApplicationRepository.log";

        // Back up dbb-build.yaml once at the start; restore it in the finally block
        File dbbBuildYaml = new File(localZBuilderPath, "dbb-build.yaml");
        File dbbBuildYamlBackup = new File(localZBuilderPath, "dbb-build.yaml.bak");
        try {
            if (dbbBuildYaml.exists()) {
                Files.copy(dbbBuildYaml.toPath(), dbbBuildYamlBackup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                logger.logMessage("** Backed up '" + dbbBuildYaml.getAbsolutePath() + "' to '" + dbbBuildYamlBackup.getAbsolutePath() + "'");
            }
        } catch (IOException e) {
            exitCode = 8;
            logger.logMessage("*! [ERROR] Failed to back up dbb-build.yaml: " + e.getMessage() + ". rc=" + exitCode);
            return;
        }

        try {
            // Check if already a Git repository
            if (isGitRepository(appRepoDir)) {
                logger.logMessage("*! [WARNING] '" + appRepoDir.getAbsolutePath() +
                    "' is already a Git repository. Skip initialization for " + appName + ".");
            } else {
                // Initialize Git repository
                initializeGitRepository(appRepoDir, defaultBranch, logFile);
            }

            if (exitCode != 0) return;

            // Reset DBB Metadatastore buildGroup
            String buildGroupName = appName + "-" + defaultBranch;
            resetBuildGroup(buildGroupName, appName, logFile);

            if (exitCode != 0) return;

            // Copy .gitattributes file
            copyGitAttributes(appRepoDir, logFile);

            if (exitCode != 0) return;

            // Create .gitignore file
            createGitIgnore(appRepoDir);

            if (exitCode != 0) return;

            // Copy and customize ZAPP file
            customizeZappFile(appRepoDir, appName, logFile);

            if (exitCode != 0) return;

            // Create baselineReference.config file
            createBaselineReferenceConfig(appRepoDir, appName, defaultBranch);

            if (exitCode != 0) return;

            // Create IDZ project file
            createIdzProjectFile(appRepoDir, appName);

            if (exitCode != 0) return;

            // Prepare pipeline configuration
            preparePipelineConfiguration(appRepoDir, appName, logFile);

            if (exitCode != 0) return;

            // Git operations: status, add, commit
            performGitOperations(appRepoDir, currentBranch, defaultBranch, logFile);

            if (exitCode != 0) return;

            // Create tag and release branch
            if (configProperties.getProperty("GIT_TAG_RELEASE") != null && configProperties.getProperty("GIT_TAG_RELEASE").equalsIgnoreCase("true")) {
                createTagAndReleaseBranch(appRepoDir, appName, defaultBranch, logFile);
            }

            if (exitCode == 0) {
                logger.logMessage("** Initializing Git repository for application '" + appName +
                    "' completed successfully. rc=" + exitCode);

                logger.logMessage("** Scanning source-level dependency information started.");
                // Update the zBuilder dbb-build.yaml with the MetadataInit task configuration
                updateZBuilderConfiguration(appName, logsDir);

                if (exitCode != 0) return;

                // Disable the Languages task in the MetadataInit task based on configuration
                updateLanguagesTaskConfiguration(false);

                if (exitCode != 0) return;

                // Run Metadata lifecycle without the languages task
                // just scanning for source-level dependencies
                runDBBBuild(appRepoDir, appName, logsDir, logFile, "metadata");

                if (exitCode != 0) return;

                // If SCAN_OUTPUTS is enabled: run full build + metadata with languages
                if ("true".equals(configProperties.getProperty("SCAN_OUTPUTS", "false"))) {
                    logger.logMessage("** Initializing the DBB MetadataStore for application '" + appName + "' started.");
                    scanOutputs(appRepoDir, appName, defaultBranch, logsDir, logFile, buildGroupName);

                    if (exitCode != 0) return;

                    // Package and publish artifacts if enabled (requires SCAN_OUTPUTS=true)
                    if ("true".equals(configProperties.getProperty("PUBLISH_ARTIFACTS", "false"))) {
                        logger.logMessage("** Publishing archive for application '" + appName + "' started.");
                        publishArtifacts(appRepoDir, appName, defaultBranch, logsDir, logFile);
                    }
                }
            } else {
                logger.logMessage("*! [ERROR] Initializing Git repository for application '" + appName +
                    "' failed. rc=" + exitCode);
            }

        } catch (Exception e) {
            exitCode = 8;
            logger.logMessage("*! [ERROR] Failed to initialize repository for '" + appName + "': " +
                e.getMessage() + ". rc=" + exitCode);
            e.printStackTrace();
        } finally {
            // Restore the original dbb-build.yaml from the backup
            if (dbbBuildYamlBackup.exists()) {
                try {
                    Files.copy(dbbBuildYamlBackup.toPath(), dbbBuildYaml.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    dbbBuildYamlBackup.delete();
                    logger.logMessage("** Restored '" + dbbBuildYaml.getAbsolutePath() + "' from backup");
                } catch (IOException e) {
                    logger.logMessage("*! [WARNING] Failed to restore dbb-build.yaml from backup: " + e.getMessage());
                }
            }
        }
    }
    
    private boolean isGitRepository(File directory) {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "rev-parse", "--is-inside-work-tree");
            pb.directory(directory);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            process.waitFor();
            
            return line != null && line.trim().equals("true");
        } catch (Exception e) {
            return false;
        }
    }
    
    private void resetBuildGroup(String buildGroupName, String appName, String logFile) throws IOException {
        logger.logMessage("** Reset DBB Metadatastore buildGroup '" + buildGroupName +
            "' for repository '" + appName + "'");
        
        try {
            MetadataStoreUtility metadataStoreUtil = new MetadataStoreUtility();
            
            // Initialize metadata store based on type
            String metadataStoreType = configProperties.getProperty("DBB_MODELER_METADATASTORE_TYPE");
            
            if ("file".equalsIgnoreCase(metadataStoreType)) {
                String metadataStoreDir = configProperties.getProperty("DBB_MODELER_FILE_METADATA_STORE_DIR");
                metadataStoreUtil.initializeFileMetadataStore(metadataStoreDir);
            } else if ("db2".equalsIgnoreCase(metadataStoreType)) {
                String jdbcId = configProperties.getProperty("DBB_MODELER_DB2_METADATASTORE_JDBC_ID");
                String passwordFile = configProperties.getProperty("DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE");
                String db2ConfigFile = configProperties.getProperty("DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE");
                
                Properties db2Props = new Properties();
                try (FileInputStream fis = new FileInputStream(db2ConfigFile)) {
                    db2Props.load(fis);
                }
                
                metadataStoreUtil.initializeDb2MetadataStoreWithPasswordFile(jdbcId, new File(passwordFile), db2Props);
            }
            
            // Delete the build group
            metadataStoreUtil.deleteBuildGroup(buildGroupName);
            logger.logMessage("** Successfully deleted buildGroup '" + buildGroupName + "'");
            
        } catch (BuildException e) {
            exitCode = 8;
            logger.logMessage("[ERROR] Failed to reset buildGroup '" + buildGroupName + "': " + e.getMessage());
            throw new IOException("Failed to reset buildGroup", e);
        }
    }
    
    private void initializeGitRepository(File directory, String defaultBranch, String logFile) throws IOException {
        logger.logMessage("** Initialize Git repository for application '" + directory.getName() +
            "' with initial branch '" + defaultBranch + "'");
        
        List<String> command = Arrays.asList("git", "init", "--initial-branch=" + defaultBranch);
        executeCommand(command, directory, logFile);
    }
    
    private void createGitIgnore(File appRepoDir) throws IOException {
        File gitIgnoreFile = new File(appRepoDir, ".gitignore");
        if (gitIgnoreFile.exists()) {
            logger.logMessage("** Skipping '.gitignore' - file already exists");
            return;
        }
        logger.logMessage("** Create file '.gitignore'");

        try (PrintWriter writer = new PrintWriter(new FileWriter(gitIgnoreFile, java.nio.charset.StandardCharsets.UTF_8))) {
            writer.println("# Ignore logs folder");
            writer.println("logs/");
        }
        com.ibm.dbb.utils.FileUtils.setFileTag(gitIgnoreFile.getAbsolutePath(), "UTF-8");

        logger.logSilentMessage("[CMD] Created " + gitIgnoreFile.getAbsolutePath());
    }

    private void copyGitAttributes(File appRepoDir, String logFile) throws IOException {
        String defaultConfigDir = configProperties.getProperty("DBB_MODELER_DEFAULT_APP_REPO_CONFIG");
        File sourceFile = new File(defaultConfigDir, ".gitattributes");
        File targetFile = new File(appRepoDir, ".gitattributes");

        if (targetFile.exists()) {
            logger.logMessage("** Skipping '.gitattributes' - file already exists");
            return;
        }
        logger.logMessage("** Update Git configuration file '.gitattributes'");
        FileUtility.copyFileWithTags(sourceFile, targetFile);
        logger.logSilentMessage("[CMD] cp " + sourceFile + " " + targetFile);
    }
    
    private void customizeZappFile(File appRepoDir, String appName, String logFile) throws IOException {
        String defaultConfigDir = configProperties.getProperty("DBB_MODELER_DEFAULT_APP_REPO_CONFIG");
        File sourceFile = new File(defaultConfigDir, "zapp_template.yaml");
        File targetFile = new File(appRepoDir, "zapp.yaml");

        if (targetFile.exists()) {
            logger.logMessage("** Skipping 'zapp.yaml' - file already exists");
            return;
        }
        logger.logMessage("** Update ZAPP file 'zapp.yaml'");
        FileUtility.copyFileWithTags(sourceFile, targetFile);
        
        // Customize ZAPP file using Java utility
        try {
            // Read application descriptor
            File appDescriptorFile = new File(appRepoDir, "applicationDescriptor.yml");
            if (!appDescriptorFile.exists()) {
                exitCode = 8;
                logger.logMessage("[ERROR] Application descriptor file not found: " + appDescriptorFile.getAbsolutePath());
                return;
            }
            
            ApplicationDescriptorUtils appDescUtils = new ApplicationDescriptorUtils();
            ApplicationDescriptor appDescriptor = appDescUtils.readApplicationDescriptor(appDescriptorFile);
            
            // Customize ZAPP file
            ZappUtility.customizeZappFile(targetFile, appDescriptor);
            logger.logMessage("** Successfully customized ZAPP file for application '" + appName + "'");
            
        } catch (Exception e) {
            exitCode = 8;
            logger.logMessage("[ERROR] Failed to customize ZAPP file: " + e.getMessage());
            throw new IOException("Failed to customize ZAPP file", e);
        }
    }
    
    private void createBaselineReferenceConfig(File appRepoDir, String appName, String defaultBranch) throws IOException {
        File confDir = new File(appRepoDir, "application-conf");
        File baselineFile = new File(confDir, "baselineReference.config");

        if (baselineFile.exists()) {
            logger.logMessage("** Skipping 'baselineReference.config' - file already exists");
            return;
        }
        logger.logMessage("** Create file 'baselineReference.config'");

        if (!confDir.exists()) {
            confDir.mkdirs();
        }
        
        // Get version from applicationDescriptor.yml
        String version = extractVersionFromDescriptor(appRepoDir, appName, defaultBranch);
        if (version == null || version.isEmpty()) {
            version = "rel-1.0.0";
        }
        
        // Write baseline reference config
        try (PrintWriter writer = new PrintWriter(new FileWriter(baselineFile))) {
            writer.println("# main branch - baseline reference for the next planned release");
            writer.println("main=refs/tags/" + version);
            writer.println();
            writer.println("# release maintenance branch - for maintenance fixes for the current release in production " + version);
            writer.println("release/" + version + "=refs/tags/" + version);
        }
        
        // Set encoding tag (z/OS specific)
        try {
            com.ibm.dbb.utils.FileUtils.setFileTag(baselineFile.getAbsolutePath(), "IBM-1047");
        } catch (Exception e) {
            // Ignore file tagging errors on non-z/OS systems
        }
    }
    
    private void createIdzProjectFile(File appRepoDir, String appName) throws IOException {
        File projectFile = new File(appRepoDir, ".project");
        if (projectFile.exists()) {
            logger.logMessage("** Skipping '.project' - file already exists");
            return;
        }
        logger.logMessage("** Create file IDZ project configuration file '.project'");

        try (PrintWriter writer = new PrintWriter(new FileWriter(projectFile))) {
            writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            writer.println("<projectDescription>");
            writer.println("    <name>" + appName + "</name>");
            writer.println("    <comment></comment>");
            writer.println("    <projects>");
            writer.println("    </projects>");
            writer.println("    <buildSpec>");
            writer.println("    </buildSpec>");
            writer.println("    <natures>");
            writer.println("        <nature>com.ibm.ftt.ui.views.project.navigator.local</nature>");
            writer.println("        <nature>com.ibm.ftt.dbbz.integration.dbbzprojectnature</nature>");
            writer.println("    </natures>");
            writer.println("</projectDescription>");
        }
        com.ibm.dbb.utils.FileUtils.setFileTag(projectFile.getAbsolutePath(), "UTF-8");

    }
    
    private void preparePipelineConfiguration(File appRepoDir, String appName, String logFile) throws IOException {
        logger.logMessage("** Prepare pipeline configuration for '" +
            configProperties.getProperty("PIPELINE_CI", "None") + "'");
        
        String pipelineCI = configProperties.getProperty("PIPELINE_CI", "None");
        String dbbCommunityRepo = configProperties.getProperty("DBB_COMMUNITY_REPO");
        
        switch (pipelineCI) {
            case "AzureDevOpsPipeline":
                copyAzureDevOpsPipeline(appRepoDir, dbbCommunityRepo, logFile);
                break;
            case "GitlabCIPipeline-for-zos-native-runner":
            case "GitlabCIPipeline-for-distributed-runner":
                copyGitLabPipeline(appRepoDir, dbbCommunityRepo, pipelineCI, logFile);
                break;
            case "JenkinsPipeline":
                copyJenkinsPipeline(appRepoDir, dbbCommunityRepo, logFile);
                break;
            case "GitHubActionsPipeline":
                copyGitHubActionsPipeline(appRepoDir, dbbCommunityRepo, logFile);
                break;
            case "None":
                logger.logMessage("[INFO] Adding the pipeline orchestration technology template is skipped per configuration.");
                break;
            default:
                logger.logMessage("[WARNING] The pipeline orchestration technology provided (" + pipelineCI +
                    ") does not match any of the supported options. Skipped.");
                break;
        }
    }
    
    private void copyAzureDevOpsPipeline(File appRepoDir, String dbbCommunityRepo, String logFile) throws IOException {
        File ciFile = new File(dbbCommunityRepo, "Templates/AzureDevOpsPipeline/azure-pipelines.yml");
        if (!ciFile.exists()) {
            exitCode = 8;
            logger.logMessage("[ERROR] The pipeline template file '" + ciFile + "' was not found. rc=" + exitCode);
            return;
        }

        File targetCiFile = new File(appRepoDir, "azure-pipelines.yml");
        if (targetCiFile.exists()) {
            logger.logMessage("** Skipping 'azure-pipelines.yml' - file already exists");
        } else {
            FileUtility.copyFileWithTags(ciFile, targetCiFile);
        }

        // Copy deployment templates
        File deploymentDir = new File(appRepoDir, "deployment");
        if (deploymentDir.exists()) {
            logger.logMessage("** Skipping 'deployment' directory - already exists");
        } else {
            deploymentDir.mkdirs();
            copyDirectory(new File(dbbCommunityRepo, "Templates/AzureDevOpsPipeline/templates/deployment"),
                deploymentDir);
        }

        // Copy tagging templates
        File taggingDir = new File(appRepoDir, "tagging");
        if (taggingDir.exists()) {
            logger.logMessage("** Skipping 'tagging' directory - already exists");
        } else {
            taggingDir.mkdirs();
            copyDirectory(new File(dbbCommunityRepo, "Templates/AzureDevOpsPipeline/templates/tagging"),
                taggingDir);
        }
    }
    
    private void copyGitLabPipeline(File appRepoDir, String dbbCommunityRepo, String pipelineCI, String logFile) throws IOException {
        File ciFile = new File(dbbCommunityRepo, "Templates/" + pipelineCI + "/.gitlab-ci.yml");
        if (!ciFile.exists()) {
            exitCode = 8;
            logger.logMessage("[ERROR] The pipeline template file '" + ciFile + "' was not found. rc=" + exitCode);
            return;
        }

        File targetFile = new File(appRepoDir, ".gitlab-ci.yml");
        if (targetFile.exists()) {
            logger.logMessage("** Skipping '.gitlab-ci.yml' - file already exists");
            return;
        }
        FileUtility.copyFileWithTags(ciFile, targetFile);
    }

    private void copyJenkinsPipeline(File appRepoDir, String dbbCommunityRepo, String logFile) throws IOException {
        File ciFile = new File(dbbCommunityRepo, "Templates/JenkinsPipeline/Jenkinsfile");
        if (!ciFile.exists()) {
            exitCode = 8;
            logger.logMessage("[ERROR] The pipeline template file '" + ciFile + "' was not found. rc=" + exitCode);
            return;
        }

        File targetFile = new File(appRepoDir, "Jenkinsfile");
        if (targetFile.exists()) {
            logger.logMessage("** Skipping 'Jenkinsfile' - file already exists");
            return;
        }
        FileUtility.copyFileWithTags(ciFile, targetFile);
    }
    
    private void copyGitHubActionsPipeline(File appRepoDir, String dbbCommunityRepo, String logFile) throws IOException {
        File ciDir = new File(dbbCommunityRepo, "Templates/GitHubActionsPipeline/.github");
        if (!ciDir.exists()) {
            exitCode = 8;
            logger.logMessage("[ERROR] The pipeline template directory '" + ciDir + "' was not found. rc=" + exitCode);
            return;
        }

        File targetDir = new File(appRepoDir, ".github");
        if (targetDir.exists()) {
            logger.logMessage("** Skipping '.github' directory - already exists");
            return;
        }
        copyDirectory(ciDir, targetDir);
    }
    
    private void copyDirectory(File source, File target) throws IOException {
        if (!source.exists()) {
            return;
        }
        
        if (!target.exists()) {
            target.mkdirs();
        }
        
        File[] files = source.listFiles();
        if (files != null) {
            for (File file : files) {
                File targetFile = new File(target, file.getName());
                if (file.isDirectory()) {
                    copyDirectory(file, targetFile);
                } else {
                    FileUtility.copyFileWithTags(file, targetFile);
                }
            }
        }
    }
    
    private void performGitOperations(File directory, String currentBranch, String defaultBranch, String logFile) throws IOException {
        // Git checkout
        logger.logMessage("** Checkout branch '" + currentBranch + "'");
        executeCommand(Arrays.asList("git", "checkout", "-b", currentBranch), directory, logFile);

        if (exitCode != 0) return;

        // Git status
        executeCommand(Arrays.asList("git", "status"), directory, logFile);

        if (exitCode != 0) return;

        // Git add all
        logger.logMessage("** Add files to Git repository");
        executeCommand(Arrays.asList("git", "add", "--all"), directory, logFile);
        
        if (exitCode != 0) return;
        
        // Git commit
        String commitMessage = configProperties.getProperty("GIT_COMMIT_MESSAGE") != null ? configProperties.getProperty("GIT_COMMIT_MESSAGE").replaceAll("\"", "").trim() : "Initial Loading";
        logger.logMessage("** Commit files to Git repository with commit message '" + commitMessage + "'");
        executeCommand(Arrays.asList("git", "commit", "--allow-empty", "-m", commitMessage), directory, logFile);
    }
    
    private void createTagAndReleaseBranch(File directory, String appName, String defaultBranch, String logFile) throws IOException {
        String version = extractVersionFromDescriptor(directory, appName, defaultBranch);
        if (version == null || version.isEmpty()) {
            version = "rel-1.0.0";
        }
        
        logger.logMessage("** Create git tag '" + version + "'");
        executeCommand(Arrays.asList("git", "tag", "-f", version), directory, logFile);
        
        if (exitCode != 0) return;
        
        logger.logMessage("** Create release maintenance branch 'release/" + version + "'");
        // Force delete branch anyway
        executeCommand(Arrays.asList("git", "branch", "--delete", "-f", "release/" + version),
            directory, logFile);
        // Recreate branch anyway
        executeCommand(Arrays.asList("git", "branch", "release/" + version, "refs/tags/" + version),
            directory, logFile);
    }
    
    /**
     * Adds or removes the "Languages" entry from the tasks list of the "metadata" lifecycle
     * in dbb-build.yaml.
     *
     * @param enable {@code true} to include the Languages task, {@code false} to exclude it.
     */
    private void updateLanguagesTaskConfiguration(boolean enable) {
        try {
            String dbbBuildYamlFilePath = localZBuilderPath + "/dbb-build.yaml";
            File dbbBuildYamlFile = new File(dbbBuildYamlFilePath);
            if (!dbbBuildYamlFile.exists()) {
                throw new FileNotFoundException(
                    "The DBB zBuilder dbb-build.yaml file was not found at '" + dbbBuildYamlFilePath + "'.");
            }

            Yaml yaml = new Yaml();
            Map<String, Object> dbbBuildYaml;
            try (FileReader reader = new FileReader(dbbBuildYamlFile)) {
                dbbBuildYaml = yaml.load(reader);
            }

            // Find the lifecycle named "metadata"
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> lifecycles =
                (List<Map<String, Object>>) dbbBuildYaml.get("lifecycles");
            if (lifecycles == null) {
                throw new IllegalStateException(
                    "No 'lifecycles' section found in '" + dbbBuildYamlFilePath + "'.");
            }

            Map<String, Object> targetLifecycle = null;
            for (Map<String, Object> lifecycle : lifecycles) {
                if ("metadata".equals(lifecycle.get("lifecycle"))) {
                    targetLifecycle = lifecycle;
                    break;
                }
            }

            if (targetLifecycle == null) {
                throw new IllegalStateException(
                    "No lifecycle named 'metadata' was found in '" + dbbBuildYamlFilePath + "'.");
            }

            @SuppressWarnings("unchecked")
            List<Object> lifecycleTasks = (List<Object>) targetLifecycle.get("tasks");

            boolean alreadyPresent = lifecycleTasks.contains("Languages");

            if (enable && !alreadyPresent) {
                // Insert "Languages" after "ImpactAnalysis" if present, otherwise before "Finish"
                int insertIndex = lifecycleTasks.indexOf("ImpactAnalysis");
                if (insertIndex >= 0) {
                    lifecycleTasks.add(insertIndex + 1, "Languages");
                } else {
                    int finishIndex = lifecycleTasks.indexOf("Finish");
                    if (finishIndex >= 0) {
                        lifecycleTasks.add(finishIndex, "Languages");
                    } else {
                        lifecycleTasks.add("Languages");
                    }
                }
            } else if (!enable && alreadyPresent) {
                lifecycleTasks.remove("Languages");
            } else {
            }

            // Write updated YAML back, preserving top-level structure order
            DumperOptions dumperOptions = new DumperOptions();
            dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            dumperOptions.setPrettyFlow(true);
            Yaml yamlWriter = new Yaml(dumperOptions);

            Map<String, Object> output = new LinkedHashMap<>();
            if (dbbBuildYaml.containsKey("version"))    output.put("version",    dbbBuildYaml.get("version"));
            if (dbbBuildYaml.containsKey("include"))    output.put("include",    dbbBuildYaml.get("include"));
            output.put("lifecycles", lifecycles);
            if (dbbBuildYaml.containsKey("tasks"))      output.put("tasks",      dbbBuildYaml.get("tasks"));

            try (OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(dbbBuildYamlFile), "UTF-8")) {
                yamlWriter.dump(output, writer);
            }
            try {
                com.ibm.dbb.utils.FileUtils.setFileTag(dbbBuildYamlFile.getAbsolutePath(), "UTF-8");
            } catch (Exception e) {
                // Ignore file tagging on non-z/OS systems
            }
        } catch (Exception e) {
            exitCode = 8;
            logger.logMessage("*! [ERROR] Failed to update Languages task configuration: " +
                e.getMessage() + ". rc=" + exitCode);
        }
    }

    private void updateZBuilderConfiguration(String appName, String logsDir) throws IOException {
        try {
            String dbbBuildYamlFilePath = localZBuilderPath + "/dbb-build.yaml";
            File dbbBuildYamlFile = new File(dbbBuildYamlFilePath);
            if (!dbbBuildYamlFile.exists()) {
                throw new FileNotFoundException(
                    "The DBB zBuilder dbb-build.yaml file was not found at '" + dbbBuildYamlFilePath + "'.");
            }

            Yaml yaml = new Yaml();
            Map<String, Object> dbbBuildYaml;
            try (FileReader reader = new FileReader(dbbBuildYamlFile)) {
                dbbBuildYaml = yaml.load(reader);
            }

            // Replace "ImpactAnalysis" with "FullAnalysis" in the "metadata" lifecycle task list
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> lifecycles = (List<Map<String, Object>>) dbbBuildYaml.get("lifecycles");
            if (lifecycles != null) {
                for (Map<String, Object> lifecycle : lifecycles) {
                    if ("metadata".equals(lifecycle.get("lifecycle"))) {
                        @SuppressWarnings("unchecked")
                        List<Object> lifecycleTasks = (List<Object>) lifecycle.get("tasks");
                        if (lifecycleTasks != null) {
                            int impactIndex = lifecycleTasks.indexOf("ImpactAnalysis");
                            if (impactIndex >= 0) {
                                lifecycleTasks.set(impactIndex, "FullAnalysis");
                            }
                        }
                        break;
                    }
                }
            }

            // Find or create the MetadataInit task
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tasks = (List<Map<String, Object>>) dbbBuildYaml.get("tasks");
            if (tasks == null) {
                tasks = new ArrayList<>();
                dbbBuildYaml.put("tasks", tasks);
            }

            Map<String, Object> metadataInitTask = tasks.stream()
                .filter(t -> "MetadataInit".equals(t.get("task")))
                .findFirst()
                .orElse(null);

            if (metadataInitTask == null) {
                metadataInitTask = new LinkedHashMap<>();
                metadataInitTask.put("task", "MetadataInit");
                tasks.add(metadataInitTask);
            }

            // Reset and rebuild variables
            List<Map<String, String>> variables = new ArrayList<>();
            metadataInitTask.put("variables", variables);

            String metadataStoreType = configProperties.getProperty("DBB_MODELER_METADATASTORE_TYPE");
            Map<String, String> typeVar = new LinkedHashMap<>();
            typeVar.put("name", "type");
            typeVar.put("value", metadataStoreType);
            variables.add(typeVar);

            if ("file".equals(metadataStoreType)) {
                Map<String, String> locationVar = new LinkedHashMap<>();
                locationVar.put("name", "fileLocation");
                locationVar.put("value", configProperties.getProperty("DBB_MODELER_FILE_METADATA_STORE_DIR"));
                variables.add(locationVar);
            } else if ("db2".equals(metadataStoreType)) {
                Map<String, String> db2UrlVar = new LinkedHashMap<>();
                db2UrlVar.put("name", "db2Url");
                db2UrlVar.put("value", configProperties.getProperty("DBB_MODELER_DB2_URL"));
                variables.add(db2UrlVar);

                Map<String, String> db2ConfVar = new LinkedHashMap<>();
                db2ConfVar.put("name", "db2Conf");
                db2ConfVar.put("value", configProperties.getProperty("DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE"));
                variables.add(db2ConfVar);
            }

            // Write updated YAML back, preserving top-level structure order
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setPrettyFlow(true);
            Yaml yamlWriter = new Yaml(options);

            Map<String, Object> output = new LinkedHashMap<>();
            if (dbbBuildYaml.containsKey("version"))    output.put("version",    dbbBuildYaml.get("version"));
            if (dbbBuildYaml.containsKey("include"))    output.put("include",    dbbBuildYaml.get("include"));
            if (dbbBuildYaml.containsKey("lifecycles")) output.put("lifecycles", dbbBuildYaml.get("lifecycles"));
            output.put("tasks", tasks);

            try (OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(dbbBuildYamlFile), "UTF-8")) {
                yamlWriter.dump(output, writer);
            }
            try {
                com.ibm.dbb.utils.FileUtils.setFileTag(dbbBuildYamlFile.getAbsolutePath(), "UTF-8");
            } catch (Exception e) {
                // Ignore file tagging on non-z/OS systems
            }

        } catch (Exception e) {
            exitCode = 8;
            logger.logMessage("*! [ERROR] Failed to update zBuilder configuration: " + e.getMessage() + ". rc=" + exitCode);
        }
    }

    private void runDBBBuild(File appRepoDir, String appName, String logsDir, String logFile,
            String lifecycle) throws IOException {
        if (exitCode != 0) return;

        logger.logMessage("*** DBB Build of application '" + appName + "' (lifecycle: " + lifecycle + ") started");

        // Create application log directory
        File appLogDir = new File(appRepoDir, "logs");

        // Only zBuilder is supported
        String metadataStoreType = configProperties.getProperty("DBB_MODELER_METADATASTORE_TYPE");
        String dbbHome = System.getenv("DBB_HOME");

        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("DBB_BUILD", localZBuilderPath);

        List<String> command = new ArrayList<>();
        command.add(dbbHome + "/bin/dbb");
        command.add("build");
        command.add(lifecycle);
        command.add("--hlq");
        command.add(configProperties.getProperty("APPLICATION_ARTIFACTS_HLQ"));

        if ("full".equals(lifecycle)) {
            command.add("--preview");
        }

        if ("db2".equals(metadataStoreType)) {
            command.add("--dbid");
            command.add(configProperties.getProperty("DBB_MODELER_DB2_METADATASTORE_JDBC_ID"));
            command.add("--dbpf");
            command.add(configProperties.getProperty("DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE"));
        }

        executeCommandWithEnv(command, appRepoDir,
            new File(appLogDir, "build-" + lifecycle + "-" + appName + ".log").getAbsolutePath(), env);

        if (exitCode == 0) {
            logger.logMessage("*** DBB Build of application '" + appName + "' (lifecycle: " + lifecycle + ") completed successfully. rc=" + exitCode);
        } else {
            logger.logMessage("*! [ERROR] DBB Build of application '" + appName + "' (lifecycle: " + lifecycle + ") failed. rc=" + exitCode);
            logger.logMessage("*** Build logs and reports available at '" + logFile + "' and '" + appLogDir.getAbsolutePath() + "'");
        }
    }
    
    private void updateMetadataStoreOwners(String buildGroupName, String appName, String logFile) throws IOException {
        if (exitCode != 0) return;
        
        String metadataStoreType = configProperties.getProperty("DBB_MODELER_METADATASTORE_TYPE");
        if (!"db2".equals(metadataStoreType)) {
            return;
        }
        
        logger.logMessage("** Update owner of collections for DBB Metadatastore buildGroup '" +
            buildGroupName + "' for repository '" + appName + "'");
        
        String pipelineUser = configProperties.getProperty("PIPELINE_USER");
        
        try {
            MetadataStoreUtility metadataStoreUtil = new MetadataStoreUtility();
            
            // Initialize DB2 metadata store
            String jdbcId = configProperties.getProperty("DBB_MODELER_DB2_METADATASTORE_JDBC_ID");
            String passwordFile = configProperties.getProperty("DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE");
            String db2ConfigFile = configProperties.getProperty("DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE");
            
            Properties db2Props = new Properties();
            try (FileInputStream fis = new FileInputStream(db2ConfigFile)) {
                db2Props.load(fis);
            }
            
            metadataStoreUtil.initializeDb2MetadataStoreWithPasswordFile(jdbcId, new File(passwordFile), db2Props);
            
            // Set owner on all metadata objects of the build group
            metadataStoreUtil.setMetadataObjectsOwner(buildGroupName, pipelineUser);
            logger.logMessage("** Successfully set owner '" + pipelineUser + "' for all metadata objects (BuildMaps, Collections, BuildResults) of buildGroup '" + buildGroupName + "'");
            
        } catch (BuildException e) {
            exitCode = 8;
            logger.logMessage("[ERROR] Failed to set metadata objects owner for buildGroup '" + buildGroupName + "': " + e.getMessage());
            throw new IOException("Failed to set metadata objects owner for buildGroup '" + buildGroupName + "'", e);
        }
    }
    
    /**
     * Runs the full build, verifies outputs, then re-runs the metadata lifecycle with the
     * Languages task enabled.  Called when SCAN_OUTPUTS=true.
     */
    private void scanOutputs(File appRepoDir, String appName, String defaultBranch, String logsDir,
            String logFile, String buildGroupName) throws IOException {
        if (exitCode != 0) return;

        logger.logMessage("*** Perform a full build lifecycle in preview mode. No binaries will be created.");
        // Run Full lifecycle to compile/link and produce output datasets
        runDBBBuild(appRepoDir, appName, logsDir, logFile, "full");

        if (exitCode != 0) return;

        // Verify that all EXECUTE outputs from the full build exist on the system
        List<String> missingOutputs = verifyBuildOutputs(appRepoDir, appName);
        if (!missingOutputs.isEmpty()) {
            logger.logMessage("*! [ERROR] The following build outputs were not found on the provided libraries. Scanning existing build outputs for dependencies cannot be performed. " +
                "Skipping metadata lifecycle with languages.");
            for (String dsn : missingOutputs) {
                logger.logMessage("*!   Missing output: " + dsn);
            }
            exitCode = 8;
            return;
        }

        if (exitCode != 0) {
            logger.logMessage("*! [ERROR] Build output verification failed. " +
                "Skipping metadata lifecycle with languages. rc=" + exitCode);
            return;
        }

        // Enable the Languages task in the MetadataInit task
        updateLanguagesTaskConfiguration(true);

        if (exitCode != 0) return;

        logger.logMessage("*** Start scanning source and build outputs for dependencies.");

        // Run Metadata lifecycle with the languages task
        // scanning for source-level and output-level dependencies
        runDBBBuild(appRepoDir, appName, logsDir, logFile, "metadata");

        if (exitCode != 0) return;

        // Update metadata store owners (Db2 only)
        updateMetadataStoreOwners(buildGroupName, appName, logFile);

        if (exitCode == 0) {
            logger.logMessage("*** Scanning outputs for application '" + appName +
                "' completed successfully. rc=" + exitCode);
        } else {
            logger.logMessage("*! [ERROR] Scanning outputs for application '" + appName +
                "' failed. rc=" + exitCode);
        }
    }

    /**
     * Packages and publishes a baseline artifact using PackageBuildOutputs.
     * Called when PUBLISH_ARTIFACTS=true.
     */
    private void publishArtifacts(File appRepoDir, String appName, String defaultBranch, String logsDir,
            String logFile) throws IOException {
        if (exitCode != 0) return;

        logger.logMessage("*** Creating baseline package of application '" + appName + "' started");

        File appLogDir = new File(appRepoDir, "logs");
        appLogDir.mkdirs();

        String version = extractVersionFromDescriptor(appRepoDir, appName, defaultBranch);
        if (version == null || version.isEmpty()) {
            version = "rel-1.0.0";
        }

        String dbbHome = System.getenv("DBB_HOME");
        String dbbCommunityRepo = configProperties.getProperty("DBB_COMMUNITY_REPO");
        String pipelineUser = configProperties.getProperty("PIPELINE_USER");
        String pipelineUserGroup = configProperties.getProperty("PIPELINE_USER_GROUP");

        List<String> command = Arrays.asList(
            dbbHome + "/bin/groovyz",
            dbbCommunityRepo + "/Pipeline/PackageBuildOutputs/PackageBuildOutputs.groovy",
            "--workDir", appLogDir.getAbsolutePath(),
            "--addExtension",
            "--branch", defaultBranch,
            "--version", version,
            "--tarFileName", appName + "-" + version + "-baseline.tar",
            "--applicationFolderPath", appRepoDir.getAbsolutePath(),
            "--owner", pipelineUser + ":" + pipelineUserGroup,
            "--publish",
            "--artifactRepositoryUrl", configProperties.getProperty("ARTIFACT_REPOSITORY_SERVER_URL"),
            "--artifactRepositoryUser", configProperties.getProperty("ARTIFACT_REPOSITORY_USER"),
            "--artifactRepositoryPassword", configProperties.getProperty("ARTIFACT_REPOSITORY_PASSWORD"),
            "--artifactRepositoryDirectory", "release",
            "--artifactRepositoryName", appName + "-" + configProperties.getProperty("ARTIFACT_REPOSITORY_SUFFIX")
        );

        executeCommand(command, null,
            new File(appLogDir, "packaging-preview-" + appName + ".log").getAbsolutePath());

        if (exitCode == 0) {
            logger.logMessage("*** Creation of Baseline Package of application '" + appName +
                "' completed successfully. rc=" + exitCode);
        } else {
            logger.logMessage("*! [ERROR] Creation of Baseline Package of application '" + appName +
                "' failed. rc=" + exitCode);
            logger.logMessage("*** Packaging log available at '" +
                new File(appLogDir, "packaging-preview-" + appName + ".log").getAbsolutePath() + "'");
        }
    }
    /**
     * Reads the DBB Build Report produced by the most recent build of the given application,
     * collects all output datasets created by EXECUTE records, and checks via JZOS that each
     * dataset actually exists on the system.
     *
     * @param appRepoDir the application repository directory (build reports are under logs/)
     * @param appName    the application name (used for logging)
     * @return a list of dataset names that were referenced in the build report but do not exist;
     *         an empty list means all outputs are present.
     */
    private List<String> verifyBuildOutputs(File appRepoDir, String appName) {
        logger.logMessage("*** Inspect if configured build output libraries (APPLICATION_ARTIFACTS_HLQ) contain the expected output artifacts for application '" + appName + "'");     

        List<String> missingOutputs = new ArrayList<>();

        try {
            // The build report is written to the logs directory under the app repo
            File buildReportFile = new File(new File(appRepoDir, "logs"), "BuildReport.json");
            if (!buildReportFile.exists()) {
                exitCode = 8;
                logger.logMessage("*! [ERROR] Build report not found at '" +
                    buildReportFile.getAbsolutePath() + "'. Cannot verify build outputs. rc=" + exitCode);
                return missingOutputs;
            }

            // Load the build report using the DBB API
            BuildReport buildReport;
            try (FileInputStream fis = new FileInputStream(buildReportFile)) {
                buildReport = BuildReport.parse(fis);
            }

            // Retrieve all EXECUTE records from the build report by filtering the full record list
            List<ExecuteRecord> executeRecords = new ArrayList<>();
            for (Record record : buildReport.getRecords()) {
                if (record instanceof ExecuteRecord) {
                    executeRecords.add((ExecuteRecord) record);
                }
            }

            if (executeRecords.isEmpty()) {
                logger.logMessage("*** No EXECUTE records found in build report. Nothing to verify.");
                return missingOutputs;
            }


            for (ExecuteRecord record : executeRecords) {
                List<ExecuteRecord.OutputInfo> outputs = record.getOutputs();
                if (outputs == null) continue;

                for (ExecuteRecord.OutputInfo outputInfo : outputs) {
                    if (outputInfo == null || outputInfo.dataset == null || outputInfo.dataset.trim().isEmpty()) continue;

                    // Only verify outputs that have a deployType defined and are not intermediate object files
                    if (outputInfo.deployType == null || outputInfo.deployType.trim().isEmpty()
                            || "OBJ".equalsIgnoreCase(outputInfo.deployType.trim())) {
                        logger.logSilentMessage("** Skipping output with deployType '" +
                            outputInfo.deployType + "': " + outputInfo.dataset.trim());
                        continue;
                    }

                    // Normalise: strip surrounding quotes and whitespace
                    String normalised = outputInfo.dataset.trim().replaceAll("^['\"]|['\"]$", "").toUpperCase();

                    // Determine whether this is a PDS member (e.g. MY.LIB(MEMBER)) or a plain dataset.
                    // ZFile.exists() handles both "//DSN" (sequential) and "//PDS(MBR)" (member) notation.
                    boolean exists = false;
                    try {
                        exists = ZFile.exists("//'" + normalised + "'");
                    } catch (Exception e) {
                        logger.logMessage("*! [WARNING] Could not check existence of '" +
                            normalised + "': " + e.getMessage());
                        // Treat as missing to be safe
                    }

                    if (!exists) {
                        logger.logMessage("*!   Output not found: " + normalised);
                        missingOutputs.add(normalised);
                    } else {
                        logger.logSilentMessage("****   Output exists: " + normalised);
                    }
                }
            }

            if (missingOutputs.isEmpty()) {
                logger.logSilentMessage("**** All expected output artifacts verified successfully for application '" + appName + "'.");
            } else {
                logger.logMessage("*! [ERROR] " + missingOutputs.size() + " output dataset(s) missing for application '" + appName + "'.");
            }

        } catch (Exception e) {
            logger.logMessage("*! [ERROR] Failed to verify build outputs for application '" + appName +
                "': " + e.getMessage());
            // Return what we have so far; caller will act on non-empty list
        }

        return missingOutputs;
    }

    private String extractVersionFromDescriptor(File appRepoDir, String appName, String defaultBranch) {
        try {
            File descriptorFile = new File(appRepoDir, "applicationDescriptor.yml");
            if (!descriptorFile.exists()) {
                return null;
            }
            
            List<String> lines = Files.readAllLines(descriptorFile.toPath());
            boolean foundBranch = false;
            
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.contains("branch: \"" + defaultBranch + "\"")) {
                    foundBranch = true;
                } else if (foundBranch && line.contains("version:")) {
                    String version = line.split(":")[1].trim();
                    return version.replaceAll("[\" ]", "");
                }
            }
        } catch (Exception e) {
            // Ignore errors, return default
        }
        return null;
    }
    
    private void executeCommand(List<String> command, File workingDir, String logFile) throws IOException {
        executeCommandWithEnv(command, workingDir, logFile, null);
    }
    
    private void executeCommandWithEnv(List<String> command, File workingDir, String logFile, 
            Map<String, String> environment) throws IOException {
        
        logger.logSilentMessage("[CMD] " + String.join(" ", command));
        
        ProcessBuilder pb = new ProcessBuilder(command);
        if (workingDir != null) {
            pb.directory(workingDir);
        }
        if (environment != null) {
            pb.environment().putAll(environment);
        }
        
        pb.redirectErrorStream(true);
        
        try {
            Process process = pb.start();
            
            // Capture output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                logger.logSilentMessage(line);
            }
            
            int rc = process.waitFor();
            exitCode = rc;
            
        } catch (InterruptedException e) {
            exitCode = 8;
            Thread.currentThread().interrupt();
            throw new IOException("Command execution interrupted", e);
        }
    }
    
}
