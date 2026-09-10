/*
 * Licensed Materials - Property of IBM
 * (c) Copyright IBM Corporation 2018, 2025. All Rights Reserved.
 *
 * Note to U.S. Government Users Restricted Rights:
 * Use, duplication or disclosure restricted by GSA ADP Schedule
 * Contract with IBM Corp.
 */

package com.ibm.dbb.migration;

import com.ibm.dbb.migration.utils.ConfigurationUtility;
import com.ibm.dbb.migration.utils.FileUtility;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Validates the DBB Git Migration Modeler configuration and environment.
 * All methods are static. Validation state is carried via a ValidationContext
 * that accumulates errors across all checks so every problem is reported at once.
 */
public class ValidateConfiguration {

    // -----------------------------------------------------------------------
    // Validation context – passed through all static helpers
    // -----------------------------------------------------------------------

    private static class ValidationContext {
        final List<String> errors = new ArrayList<>();
        int exitCode = 0;

        void addError(String message) {
            errors.add(message);
            exitCode = 8;
        }

        boolean hasErrors() {
            return exitCode != 0;
        }
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Validates a configuration file and returns the loaded properties.
     * Runs every check before throwing so all errors are reported at once.
     *
     * @param configFilePath Path to the configuration file
     * @return validated Properties
     * @throws Exception if any validation check fails
     */
    public static Properties validateAndLoadConfiguration(String configFilePath) throws Exception {
        File configFile = new File(configFilePath);
        if (!configFile.exists()) {
            throw new Exception("DBB Git Migration Modeler configuration file not found: " + configFilePath);
        }

        Properties configProperties = new Properties();
        try (FileInputStream fis = new FileInputStream(configFilePath)) {
            configProperties.load(fis);
        }

        ValidationContext ctx = new ValidationContext();

        String modelerHome = System.getProperty("dbb.modeler.home");

        validateEnvironment(ctx);

        // Toolkit version requires both DBB_HOME and modelerHome
        if (System.getenv("DBB_HOME") != null && !System.getenv("DBB_HOME").isEmpty()
                && modelerHome != null && !modelerHome.isEmpty()) {
            validateDBBToolkitVersion(ctx, modelerHome);
        }

        validateMetadataStore(ctx, configProperties);
        validateBuildFramework(ctx, configProperties);
        validateCommunityRepo(ctx, configProperties);

        String publishArtifacts = configProperties.getProperty("PUBLISH_ARTIFACTS");
        if ("true".equals(publishArtifacts)) {
            validateArtifactRepository(ctx, configProperties);
        }

        if (ctx.hasErrors()) {
            StringBuilder sb = new StringBuilder();
            for (String error : ctx.errors) {
                sb.append("\n  - ").append(error);
            }
            throw new Exception("Configuration validation failed with " + ctx.errors.size() + " error(s):" + sb);
        }

        return configProperties;
    }

    /**
     * Validates the environment (DBB_HOME presence and git availability).
     * Throws an Exception listing all environment errors if any are found.
     *
     * @throws Exception if the environment is not correctly set up
     */
    public static void validateEnvironment() throws Exception {
        ValidationContext ctx = new ValidationContext();
        validateEnvironment(ctx);
        if (ctx.hasErrors()) {
            StringBuilder sb = new StringBuilder();
            for (String error : ctx.errors) {
                sb.append("\n  - ").append(error);
            }
            throw new Exception("Environment validation failed with " + ctx.errors.size() + " error(s):" + sb);
        }
    }

    // -----------------------------------------------------------------------
    // Private static helpers
    // -----------------------------------------------------------------------

    private static void validateEnvironment(ValidationContext ctx) {
        String dbbHome = System.getenv("DBB_HOME");
        if (dbbHome == null || dbbHome.isEmpty()) {
            ctx.addError("Environment variable 'DBB_HOME' is not set.");
            return; // remaining checks require DBB_HOME
        }

        if (!new File(dbbHome, "bin/dbb").exists()) {
            ctx.addError("The 'dbb' program was not found in DBB_HOME '" + dbbHome + "'.");
        }

        try {
            Process process = Runtime.getRuntime().exec("git --version");
            if (process.waitFor() != 0) {
                ctx.addError("The 'git' command is not available.");
            }
        } catch (Exception e) {
            ctx.addError("The 'git' command is not available: " + e.getMessage());
        }
    }

    private static void validateDBBToolkitVersion(ValidationContext ctx, String modelerHome) {
        try {
            Properties releaseProps = new Properties();
            try (FileInputStream fis = new FileInputStream(new File(modelerHome, "release.properties"))) {
                releaseProps.load(fis);
            }

            String requiredVersion = releaseProps.getProperty("Minimal-DBB-version");
            if (requiredVersion == null) {
                ctx.addError("Unable to read Minimal-DBB-version from release.properties");
                return;
            }

            String dbbHome = System.getenv("DBB_HOME");
            Process process = Runtime.getRuntime().exec(dbbHome + "/bin/dbb --version");
            String currentVersion = null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("Dependency Based Build version")) {
                        String[] parts = line.split("\\s+");
                        currentVersion = parts[parts.length - 1];
                        break;
                    }
                }
            }
            process.waitFor();

            if (currentVersion == null) {
                ctx.addError("Unable to determine current DBB Toolkit version.");
                return;
            }

            if (!isVersionSufficient(currentVersion, requiredVersion)) {
                ctx.addError("The DBB Toolkit's version is " + currentVersion +
                    ". The minimal recommended version is " + requiredVersion + ".");
            }
        } catch (Exception e) {
            ctx.addError("Failed to validate DBB Toolkit version: " + e.getMessage());
        }
    }

    private static void validateMetadataStore(ValidationContext ctx, Properties configProperties) {
        String type = configProperties.getProperty("DBB_MODELER_METADATASTORE_TYPE");
        if ("db2".equals(type)) {
            validateDb2Configuration(ctx, configProperties);
        } else if ("file".equals(type)) {
            validateFileMetadataStore(ctx, configProperties);
        } else {
            ctx.addError("The specified DBB MetadataStore technology is not 'file' or 'db2'.");
        }
    }

    private static void validateDb2Configuration(ValidationContext ctx, Properties configProperties) {
        checkRequired(ctx, configProperties, "DBB_MODELER_DB2_METADATASTORE_JDBC_ID",     "The Db2 MetadataStore User");
        checkFile    (ctx, configProperties, "DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE",  "The Db2 Connection configuration file");
        checkFile    (ctx, configProperties, "DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE", "The Db2 MetadataStore Password File");
    }

    private static void validateFileMetadataStore(ValidationContext ctx, Properties configProperties) {
        checkRequired(ctx, configProperties, "DBB_MODELER_FILE_METADATA_STORE_DIR", "The location of the DBB File-based MetadataStore");
    }

    private static void validateBuildFramework(ValidationContext ctx, Properties configProperties) {
        String buildFramework = configProperties.getProperty("BUILD_FRAMEWORK");
        if (!"zBuilder".equals(buildFramework)) {
            ctx.addError("The specified Build Framework '" + buildFramework + "' is not valid. Only 'zBuilder' is supported.");
        } else {
            checkDirectory(ctx, configProperties, "DBB_ZBUILDER", "The zBuilder instance");
        }
    }

    private static void validateCommunityRepo(ValidationContext ctx, Properties configProperties) {
        String dbbCommunityRepo = configProperties.getProperty("DBB_COMMUNITY_REPO");
        if (dbbCommunityRepo != null && !dbbCommunityRepo.trim().isEmpty()) {
            checkDirectory(ctx, configProperties, "DBB_COMMUNITY_REPO", "The DBB Community repository instance");
        }
    }

    private static void validateArtifactRepository(ValidationContext ctx, Properties configProperties) {
        checkRequired(ctx, configProperties, "ARTIFACT_REPOSITORY_SERVER_URL", "The URL of the Artifact Repository Server");

        String serverUrl = configProperties.getProperty("ARTIFACT_REPOSITORY_SERVER_URL");
        if (serverUrl != null && !serverUrl.trim().isEmpty()) {
            try {
                URL url = new URL(serverUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                int responseCode = connection.getResponseCode();
                if (responseCode != 200 && responseCode != 302) {
                    ctx.addError("The Artifact Repository Server '" + serverUrl +
                        "' is not reachable. HTTP response code: " + responseCode);
                }
            } catch (Exception e) {
                ctx.addError("The Artifact Repository Server '" + serverUrl + "' is not reachable: " + e.getMessage());
            }
        }

        checkRequired(ctx, configProperties, "ARTIFACT_REPOSITORY_USER",     "The User for the Artifact Repository Server");
        checkRequired(ctx, configProperties, "ARTIFACT_REPOSITORY_PASSWORD",  "The Password of the User for the Artifact Repository Server");
        checkRequired(ctx, configProperties, "ARTIFACT_REPOSITORY_SUFFIX",    "The Suffix for Artifact Repositories");
    }

    // -----------------------------------------------------------------------
    // Work directory initialization
    // -----------------------------------------------------------------------

    /**
     * Validates the configuration file and finalizes setup by creating work
     * directories and copying sample files into them.
     * Equivalent to calling 0-validateConfiguration.sh -f <configFile>.
     *
     * @param configFilePath Path to the configuration file
     * @throws Exception if validation or directory initialization fails
     */
    public static void initializeWorkDirectory(String configFilePath) throws Exception {
        ValidationContext ctx = new ValidationContext();
        initializeWorkDirectory(ctx, configFilePath);
        if (ctx.hasErrors()) {
            StringBuilder sb = new StringBuilder();
            for (String error : ctx.errors) {
                sb.append("\n  - ").append(error);
            }
            throw new Exception("Setup finalization failed with " + ctx.errors.size() + " error(s):" + sb);
        }
    }

    private static void initializeWorkDirectory(ValidationContext ctx, String configFilePath) {
        Properties configProperties;
        try {
            configProperties = validateAndLoadConfiguration(configFilePath);
        } catch (Exception e) {
            ctx.addError(e.getMessage());
            return;
        }

        String modelerHome = System.getProperty("dbb.modeler.home");

        try {
            String workDir = configProperties.getProperty("DBB_MODELER_WORK");
            Path workPath = Paths.get(workDir);

            if (Files.exists(workPath)) {
                ctx.addError("Directory '" + workDir + "' already exists.");
                return;
            }

            System.out.println("  [INFO] Creating the DBB Git Migration Modeler working folder '" + workDir + "'");
            Files.createDirectories(workPath);

            String appMappingsDir = configProperties.getProperty("DBB_MODELER_APPMAPPINGS_DIR");
            if (!Files.exists(Paths.get(appMappingsDir))) {
                System.out.println("  [INFO] Creating the DBB Git Migration Modeler Applications Mappings folder '" + appMappingsDir + "'");
                Files.createDirectories(Paths.get(appMappingsDir));
            }

            copySampleFiles(configProperties, modelerHome, appMappingsDir);

        } catch (IOException e) {
            ctx.addError("Failed to initialize work directory: " + e.getMessage());
        }
    }

    private static void copySampleFiles(Properties configProperties, String modelerHome,
                                        String appMappingsDir) throws IOException {
        System.out.println("  [INFO] Copying sample Applications Mappings files to '" + appMappingsDir + "'");
        copyDirectory(new File(modelerHome, "samples/applications-mapping"), new File(appMappingsDir));

        String repoPathMapping = configProperties.getProperty("REPOSITORY_PATH_MAPPING_FILE");
        System.out.println("  [INFO] Copying sample Repository Paths Mapping file to '" + repoPathMapping + "'");
        Files.createDirectories(Paths.get(repoPathMapping).getParent());
        FileUtility.copyFileWithTags(Paths.get(modelerHome, "samples/repositoryPathsMapping.yaml"), Paths.get(repoPathMapping));

        String typesMapping = configProperties.getProperty("APPLICATION_TYPES_MAPPING");
        System.out.println("  [INFO] Copying sample Types Mapping file to '" + typesMapping + "'");
        Files.createDirectories(Paths.get(typesMapping).getParent());
        FileUtility.copyFileWithTags(Paths.get(modelerHome, "samples/typesMapping.yaml"), Paths.get(typesMapping));

        String typesConfig = configProperties.getProperty("TYPE_CONFIGURATIONS_FILE");
        System.out.println("  [INFO] Copying sample Types Configurations file to '" + typesConfig + "'");
        Files.createDirectories(Paths.get(typesConfig).getParent());
        FileUtility.copyFileWithTags(Paths.get(modelerHome, "samples/typesConfigurations.yaml"), Paths.get(typesConfig));

        String defaultAppRepoConfig = configProperties.getProperty("DBB_MODELER_DEFAULT_APP_REPO_CONFIG");
        if (!Files.exists(Paths.get(defaultAppRepoConfig))) {
            System.out.println("  [INFO] Creating the sample Application Repository Configuration folder '" + defaultAppRepoConfig + "'");
            Files.createDirectories(Paths.get(defaultAppRepoConfig));
        }
        System.out.println("  [INFO] Copying sample Git Configuration files to '" + defaultAppRepoConfig + "'");
        copyDirectory(new File(modelerHome, "samples/application-repository-configuration"), new File(defaultAppRepoConfig));
    }

    private static void copyDirectory(File source, File target) throws IOException {
        if (!source.exists()) {
            throw new IOException("Source directory does not exist: " + source);
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

    // -----------------------------------------------------------------------
    // Property check helpers
    // -----------------------------------------------------------------------

    private static void checkRequired(ValidationContext ctx, Properties props, String key, String description) {
        try {
            ConfigurationUtility.validateRequiredProperty(props, key, description);
        } catch (IllegalArgumentException e) {
            ctx.addError(e.getMessage());
        }
    }

    private static void checkFile(ValidationContext ctx, Properties props, String key, String description) {
        try {
            ConfigurationUtility.validateFileProperty(props, key, description);
        } catch (IllegalArgumentException e) {
            ctx.addError(e.getMessage());
        }
    }

    private static void checkDirectory(ValidationContext ctx, Properties props, String key, String description) {
        try {
            ConfigurationUtility.validateDirectoryProperty(props, key, description);
        } catch (IllegalArgumentException e) {
            ctx.addError(e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Version comparison
    // -----------------------------------------------------------------------

    private static boolean isVersionSufficient(String current, String required) {
        String[] currentParts = current.split("\\.");
        String[] requiredParts = required.split("\\.");
        for (int i = 0; i < Math.min(currentParts.length, requiredParts.length); i++) {
            int c = Integer.parseInt(currentParts[i]);
            int r = Integer.parseInt(requiredParts[i]);
            if (c < r) return false;
            if (c > r) return true;
        }
        return true;
    }
}
