/*
 * Licensed Materials - Property of IBM
 * (c) Copyright IBM Corporation 2018, 2025. All Rights Reserved.
 *
 * Note to U.S. Government Users Restricted Rights:
 * Use, duplication or disclosure restricted by GSA ADP Schedule
 * Contract with IBM Corp.
 */

package com.ibm.dbb.migration;

import com.ibm.dbb.migration.utils.Logger;
import com.ibm.dbb.migration.utils.MetadataStoreUtility;
import com.ibm.dbb.migration.utils.ZappUtility;
import com.ibm.dbb.migration.utils.ApplicationDescriptorUtils;
import com.ibm.dbb.migration.utils.FileUtility;
import com.ibm.dbb.migration.model.ApplicationDescriptor;
import com.ibm.dbb.build.BuildException;
import org.apache.commons.cli.*;

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
            performGitOperations(appRepoDir, logFile);
            
            if (exitCode != 0) return;
            
            // Create tag and release branch
            if (configProperties.getProperty("GIT_TAG_RELEASE") != null && configProperties.getProperty("GIT_TAG_RELEASE").equalsIgnoreCase("true")) {
                createTagAndReleaseBranch(appRepoDir, appName, defaultBranch, logFile);
            }
            
            if (exitCode == 0) {
                logger.logMessage("** Initializing Git repository for application '" + appName +
                    "' completed successfully. rc=" + exitCode);
                
                // Run preview build
                runPreviewBuild(appRepoDir, appName, logsDir, logFile);
                
                // Update metadata store owners (Db2 only)
                updateMetadataStoreOwners(buildGroupName, appName, logFile);
                
                // Publish artifacts if enabled
                publishArtifacts(appRepoDir, appName, defaultBranch, logsDir, logFile);
            } else {
                logger.logMessage("*! [ERROR] Initializing Git repository for application '" + appName +
                    "' failed. rc=" + exitCode);
            }
            
        } catch (Exception e) {
            exitCode = 8;
            logger.logMessage("*! [ERROR] Failed to initialize repository for '" + appName + "': " +
                e.getMessage() + ". rc=" + exitCode);
            e.printStackTrace();
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
        logger.logMessage("** Create file '.gitignore'");

        File gitIgnoreFile = new File(appRepoDir, ".gitignore");

        try (PrintWriter writer = new PrintWriter(new FileWriter(gitIgnoreFile, java.nio.charset.StandardCharsets.UTF_8))) {
            writer.println("# Ignore logs folder");
            writer.println("logs/");
        }
        com.ibm.dbb.utils.FileUtils.setFileTag(gitIgnoreFile.getAbsolutePath(), "UTF-8");

        logger.logSilentMessage("[CMD] Created " + gitIgnoreFile.getAbsolutePath());
    }

    private void copyGitAttributes(File appRepoDir, String logFile) throws IOException {
        logger.logMessage("** Update Git configuration file '.gitattributes'");
        
        String defaultConfigDir = configProperties.getProperty("DBB_MODELER_DEFAULT_APP_REPO_CONFIG");
        File sourceFile = new File(defaultConfigDir, ".gitattributes");
        File targetFile = new File(appRepoDir, ".gitattributes");
        
        if (targetFile.exists()) {
            targetFile.delete();
        }
        
        FileUtility.copyFileWithTags(sourceFile, targetFile);
        logger.logSilentMessage("[CMD] cp " + sourceFile + " " + targetFile);
    }
    
    private void customizeZappFile(File appRepoDir, String appName, String logFile) throws IOException {
        logger.logMessage("** Update ZAPP file 'zapp.yaml'");
        
        String defaultConfigDir = configProperties.getProperty("DBB_MODELER_DEFAULT_APP_REPO_CONFIG");
        File sourceFile = new File(defaultConfigDir, "zapp_template.yaml");
        File targetFile = new File(appRepoDir, "zapp.yaml");
        
        if (targetFile.exists()) {
            targetFile.delete();
        }
        
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
        logger.logMessage("** Create file 'baselineReference.config'");
        
        File confDir = new File(appRepoDir, "application-conf");
        if (!confDir.exists()) {
            confDir.mkdirs();
        }
        
        File baselineFile = new File(confDir, "baselineReference.config");
        
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
        logger.logMessage("** Create file IDZ project configuration file '.project'");
        
        File projectFile = new File(appRepoDir, ".project");
        
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
        
        FileUtility.copyFileWithTags(ciFile, new File(appRepoDir, "azure-pipelines.yml"));
        
        // Copy deployment templates
        File deploymentDir = new File(appRepoDir, "deployment");
        deploymentDir.mkdirs();
        copyDirectory(new File(dbbCommunityRepo, "Templates/AzureDevOpsPipeline/templates/deployment"), 
            deploymentDir);
        
        // Copy tagging templates
        File taggingDir = new File(appRepoDir, "tagging");
        taggingDir.mkdirs();
        copyDirectory(new File(dbbCommunityRepo, "Templates/AzureDevOpsPipeline/templates/tagging"), 
            taggingDir);
    }
    
    private void copyGitLabPipeline(File appRepoDir, String dbbCommunityRepo, String pipelineCI, String logFile) throws IOException {
        File ciFile = new File(dbbCommunityRepo, "Templates/" + pipelineCI + "/.gitlab-ci.yml");
        if (!ciFile.exists()) {
            exitCode = 8;
            logger.logMessage("[ERROR] The pipeline template file '" + ciFile + "' was not found. rc=" + exitCode);
            return;
        }
        
        FileUtility.copyFileWithTags(ciFile, new File(appRepoDir, ".gitlab-ci.yml"));
    }
    
    private void copyJenkinsPipeline(File appRepoDir, String dbbCommunityRepo, String logFile) throws IOException {
        File ciFile = new File(dbbCommunityRepo, "Templates/JenkinsPipeline/Jenkinsfile");
        if (!ciFile.exists()) {
            exitCode = 8;
            logger.logMessage("[ERROR] The pipeline template file '" + ciFile + "' was not found. rc=" + exitCode);
            return;
        }
        
        FileUtility.copyFileWithTags(ciFile, new File(appRepoDir, "Jenkinsfile"));
    }
    
    private void copyGitHubActionsPipeline(File appRepoDir, String dbbCommunityRepo, String logFile) throws IOException {
        File ciDir = new File(dbbCommunityRepo, "Templates/GitHubActionsPipeline/.github");
        if (!ciDir.exists()) {
            exitCode = 8;
            logger.logMessage("[ERROR] The pipeline template directory '" + ciDir + "' was not found. rc=" + exitCode);
            return;
        }
        
        File targetDir = new File(appRepoDir, ".github");
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
    
    private void performGitOperations(File directory, String logFile) throws IOException {
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
    
    private void updateZBuilderConfiguration(String appName, String logsDir) throws IOException {
        logger.logMessage("** Updating zBuilder 'dbb-build.yaml' with MetadataInit task configuration");

        List<String> updateArgs = new ArrayList<>(Arrays.asList("-c", configFilePath));
        String updateLog = logsDir + File.separator + "5-" + appName + "-updateZBuilderConfiguration.log";
        updateArgs.add("-l");
        updateArgs.add(updateLog);

        try {
            UpdateZBuilderConfiguration.main(updateArgs.toArray(new String[0]));
        } catch (Exception e) {
            exitCode = 8;
            logger.logMessage("*! [ERROR] Failed to update zBuilder configuration: " + e.getMessage() + ". rc=" + exitCode);
        }
    }

    private void runPreviewBuild(File appRepoDir, String appName, String logsDir, String logFile) throws IOException {
        if (exitCode != 0) return;
        
        logger.logMessage("** Preview Build of application '" + appName + "' started");
        
        // Create application log directory
        File appLogDir = new File(appRepoDir, "logs");
        
        // Only zBuilder is supported
        String metadataStoreType = configProperties.getProperty("DBB_MODELER_METADATASTORE_TYPE");
        String dbbHome = System.getenv("DBB_HOME");
        String zBuilderPath = configProperties.getProperty("DBB_ZBUILDER");

        // Update the zBuilder dbb-build.yaml with the MetadataInit task configuration
        updateZBuilderConfiguration(appName, logsDir);
        if (exitCode != 0) return;

        
        Map<String, String> env = new HashMap<>(System.getenv());
        env.put("DBB_BUILD", zBuilderPath);
        
        List<String> command = new ArrayList<>();
        command.add(dbbHome + "/bin/dbb");
        command.add("build");
        command.add("full");
        command.add("--hlq");
        command.add(configProperties.getProperty("APPLICATION_ARTIFACTS_HLQ"));
        command.add("--preview");
        
        if ("db2".equals(metadataStoreType)) {
            command.add("--dbid");
            command.add(configProperties.getProperty("DBB_MODELER_DB2_METADATASTORE_JDBC_ID"));
            command.add("--dbpf");
            command.add(configProperties.getProperty("DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE"));
        }
        
        executeCommandWithEnv(command, appRepoDir,
            new File(appLogDir, "build-preview-" + appName + ".log").getAbsolutePath(), env);
        
        if (exitCode == 0) {
            logger.logMessage("** Preview Build of application '" + appName + "' completed successfully. rc=" + exitCode);
        } else {
            logger.logMessage("*! [ERROR] Preview Build of application '" + appName + "' failed. rc=" + exitCode);
            logger.logMessage("** Build logs and reports available at '" + logFile + "' and '" + appLogDir.getAbsolutePath() + "'");
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
            
            // Set build group owner
            metadataStoreUtil.setBuildGroupOwner(buildGroupName, pipelineUser);
            logger.logMessage("** Successfully set owner '" + pipelineUser + "' for buildGroup '" + buildGroupName + "'");
            
        } catch (BuildException e) {
            exitCode = 8;
            logger.logMessage("[ERROR] Failed to set buildGroup owner: " + e.getMessage());
            throw new IOException("Failed to set buildGroup owner", e);
        }
    }
    
    private void publishArtifacts(File appRepoDir, String appName, String defaultBranch, String logsDir, String logFile) throws IOException {
        if (exitCode != 0) return;
        
        String publishArtifacts = configProperties.getProperty("PUBLISH_ARTIFACTS", "false");
        if (!"true".equals(publishArtifacts)) {
            return;
        }
        
        logger.logMessage("** Creating baseline package of application '" + appName + "' started");
        
        File appLogDir = new File(logsDir, appName);
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
            "--artifactRepositoryUrl", configProperties.getProperty("ARTIFACT_REPOSITORY_SERVER_URL"),
            "--artifactRepositoryUser", configProperties.getProperty("ARTIFACT_REPOSITORY_USER"),
            "--artifactRepositoryPassword", configProperties.getProperty("ARTIFACT_REPOSITORY_PASSWORD"),
            "--artifactRepositoryDirectory", "release",
            "--artifactRepositoryName", appName + "-" + configProperties.getProperty("ARTIFACT_REPOSITORY_SUFFIX")
        );
        
        executeCommand(command, null, 
            new File(appLogDir, "packaging-preview-" + appName + ".log").getAbsolutePath());
        
        if (exitCode == 0) {
            logger.logMessage("** Creation of Baseline Package of application '" + appName +
                "' completed successfully. rc=" + exitCode);
        } else {
            logger.logMessage("*! [ERROR] Creation of Baseline Package of application '" + appName +
                "' failed. rc=" + exitCode);
            logger.logMessage("** Packaging log available at '" +
                new File(appLogDir, "packaging-preview-" + appName + ".log").getAbsolutePath() + "'");
        }
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
