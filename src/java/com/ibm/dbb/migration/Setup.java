/********************************************************************************
 * Licensed Materials - Property of IBM                                          *
 * (c) Copyright IBM Corporation 2018, 2025. All Rights Reserved.                *
 *                                                                               *
 * Note to U.S. Government Users Restricted Rights:                              *
 * Use, duplication or disclosure restricted by GSA ADP Schedule                 *
 * Contract with IBM Corp.                                                       *
 ********************************************************************************/

package com.ibm.dbb.migration;

import com.ibm.dbb.migration.utils.MetadataStoreUtility;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Sets up a work folder and configuration file for the DBB Git Migration Modeler.
 * Equivalent to the Setup.sh shell script.
 *
 * Prompts the user for configuration parameters, writes them to a timestamped
 * configuration file, then invokes ValidateConfiguration and MetadataStoreUtility
 * to verify the environment and metadata store connectivity.
 */
public class Setup {

    private static final String[] PATH_CONFIG_KEYS = {
        "DBB_MODELER_APPCONFIG_DIR",
        "DBB_MODELER_APPLICATION_DIR",
        "DBB_MODELER_LOGS",
        "DBB_MODELER_BUILD_CONFIGURATION",
        "DBB_MODELER_DEFAULT_APP_REPO_CONFIG"
    };

    private static final String[] INPUT_KEYS = {
        "DBB_MODELER_APPMAPPINGS_DIR",
        "REPOSITORY_PATH_MAPPING_FILE",
        "APPLICATION_TYPES_MAPPING",
        "TYPE_CONFIGURATIONS_FILE",
        "APPLICATION_ARTIFACTS_HLQ",
        "SCAN_CONTROL_TRANSFERS",
        "SCAN_DATASET_MEMBERS",
        "SCAN_DATASET_MEMBERS_ENCODING",
        "DBB_COMMUNITY_REPO",
        "APPLICATION_DEFAULT_BRANCH",
        "GIT_COMMIT_MESSAGE",
        "GIT_TAG_RELEASE",
        "MOVE_FILES_FLAG",
        "PUBLISH_ARTIFACTS",
        "INTERACTIVE_RUN"
    };

    private static final String[] PUBLISHING_KEYS = {
        "ARTIFACT_REPOSITORY_SERVER_URL",
        "ARTIFACT_REPOSITORY_USER",
        "ARTIFACT_REPOSITORY_PASSWORD",
        "ARTIFACT_REPOSITORY_SUFFIX",
        "PIPELINE_USER",
        "PIPELINE_USER_GROUP"
    };

    private final Scanner console = new Scanner(System.in);
    private String modelerHome;
    private String release;

    public static void main(String[] args) {
        Setup setup = new Setup();
        int rc = setup.run();
        System.exit(rc);
    }

    public int run() {
        // Resolve modelerHome from the location of this class's code base
        modelerHome = resolveModelerHome();

        // Read release version
        release = readRelease();

        printProlog();

        // ----------------------------------------------------------------
        // Step 1 – work directory
        // ----------------------------------------------------------------
        System.out.println("[SETUP] Configuring DBB Git Migration Modeler work directory");

        String defaultWork = modelerHome + "-work";
        String modelerWork = prompt("Specify the DBB Git Migration Modeler work directory", defaultWork);

        if (Files.isDirectory(Paths.get(modelerWork))) {
            System.out.println();
            System.out.println("[WARNING] Directory '" + modelerWork + "' already exists!");
            System.out.println("[WARNING] There might be configuration files and migrated applications already present in '" + modelerWork + "'.");
            String confirm = prompt("Do you want to remove this folder and continue the Setup? (N/y)", "N");
            if (!"y".equals(confirm)) {
                System.out.println("[INFO] You can check the content of the folder '" + modelerWork + "' and decide to re-use this folder or not.");
                return 2;
            }
            System.out.println("[INFO] Removing the DBB Git Migration Modeler working folder '" + modelerWork + "'");
            try {
                deleteDirectory(Paths.get(modelerWork));
            } catch (IOException e) {
                System.err.println("[ERROR] Failed to remove the DBB Git Migration Modeler working folder '" + modelerWork + "': " + e.getMessage());
                return 8;
            }
        }

        // ----------------------------------------------------------------
        // Step 2 – build defaults from chosen work directory
        // ----------------------------------------------------------------
        Properties config = new Properties();
        config.setProperty("DBB_MODELER_WORK",                      modelerWork);
        config.setProperty("DBB_MODELER_APPCONFIG_DIR",             modelerWork + "/work/migration-configuration");
        config.setProperty("DBB_MODELER_APPLICATION_DIR",           modelerWork + "/repositories");
        config.setProperty("DBB_MODELER_LOGS",                      modelerWork + "/logs");
        config.setProperty("DBB_MODELER_BUILD_CONFIGURATION",       modelerWork + "/build-configuration");
        config.setProperty("DBB_MODELER_DEFAULT_APP_REPO_CONFIG",   modelerWork + "/config/default-app-repo-config-files");
        config.setProperty("DBB_MODELER_METADATASTORE_TYPE",        "file");
        config.setProperty("DBB_MODELER_FILE_METADATA_STORE_DIR",   modelerWork + "/work/dbb-filemetadatastore");
        config.setProperty("DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE", modelerHome + "/config/db2Connection.conf");
        config.setProperty("DBB_MODELER_DB2_METADATASTORE_JDBC_ID",         "user");
        config.setProperty("DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE", modelerHome + "/config/db2Password.txt");
        config.setProperty("DBB_MODELER_DB2_URL",                   "jdbc:db2://localhost:5035/DBC");
        config.setProperty("DBB_MODELER_APPMAPPINGS_DIR",           modelerWork + "/config/applications-mappings");
        config.setProperty("REPOSITORY_PATH_MAPPING_FILE",          modelerWork + "/config/repositoryPathsMapping.yaml");
        config.setProperty("APPLICATION_TYPES_MAPPING",             modelerWork + "/config/types/typesMapping.yaml");
        config.setProperty("TYPE_CONFIGURATIONS_FILE",              modelerWork + "/config/types/typesConfigurations.yaml");
        config.setProperty("APPLICATION_ARTIFACTS_HLQ",             "DBEHM.MIG");
        config.setProperty("SCAN_DATASET_MEMBERS",                  "false");
        config.setProperty("SCAN_DATASET_MEMBERS_ENCODING",         "IBM-1047");
        config.setProperty("SCAN_CONTROL_TRANSFERS",                "true");
        config.setProperty("BUILD_FRAMEWORK",                       "zBuilder");
        config.setProperty("DBB_ZBUILDER",                          "/var/dbb/zBuilder");
        config.setProperty("DBB_COMMUNITY_REPO",                    "/var/dbb/dbb");
        config.setProperty("APPLICATION_DEFAULT_BRANCH",            "main");
        config.setProperty("GIT_COMMIT_MESSAGE",                    "Initial Load");
        config.setProperty("GIT_TAG_RELEASE",                       "true");
        config.setProperty("MOVE_FILES_FLAG",                       "true");
        config.setProperty("INTERACTIVE_RUN",                       "true");
        config.setProperty("PUBLISH_ARTIFACTS",                     "false");
        config.setProperty("ARTIFACT_REPOSITORY_SERVER_URL",        "http://10.3.20.231:8081/artifactory");
        config.setProperty("ARTIFACT_REPOSITORY_USER",              "user");
        config.setProperty("ARTIFACT_REPOSITORY_PASSWORD",          "password");
        config.setProperty("ARTIFACT_REPOSITORY_SUFFIX",            "zos-local");
        config.setProperty("PIPELINE_USER",                         "ADO");
        config.setProperty("PIPELINE_USER_GROUP",                   "JENKINSG");
        config.setProperty("PIPELINE_CI",                           "");

        // ----------------------------------------------------------------
        // Step 3 – Build Framework
        // ----------------------------------------------------------------
        System.out.println();
        System.out.println("[SETUP] Specifying the Build Framework configuration");
        System.out.println("[INFO] Only zBuilder is supported as the build framework");
        config.setProperty("DBB_ZBUILDER",
            prompt("Specify the location of the DBB zBuilder installation", config.getProperty("DBB_ZBUILDER")));

        // ----------------------------------------------------------------
        // Step 4 – MetadataStore type
        // ----------------------------------------------------------------
        System.out.println();
        System.out.println("[SETUP] DBB Metadatastore type and configuration");
        config.setProperty("DBB_MODELER_METADATASTORE_TYPE",
            prompt("Specify the type of the DBB Metadatastore (\"file\" or \"db2\")", config.getProperty("DBB_MODELER_METADATASTORE_TYPE")));

        if ("file".equals(config.getProperty("DBB_MODELER_METADATASTORE_TYPE"))) {
            config.setProperty("DBB_MODELER_FILE_METADATA_STORE_DIR",
                prompt("Specify the location of the DBB File MetadataStore", config.getProperty("DBB_MODELER_FILE_METADATA_STORE_DIR")));
        } else if ("db2".equals(config.getProperty("DBB_MODELER_METADATASTORE_TYPE"))) {
            config.setProperty("DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE",
                prompt("Specify the location of the DBB Db2 MetadataStore configuration file", config.getProperty("DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE")));
            config.setProperty("DBB_MODELER_DB2_METADATASTORE_JDBC_ID",
                prompt("Specify the DBB Db2 MetadataStore JDBC User ID", config.getProperty("DBB_MODELER_DB2_METADATASTORE_JDBC_ID")));
            config.setProperty("DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE",
                prompt("Specify the DBB Db2 MetadataStore JDBC Password File", config.getProperty("DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE")));
            config.setProperty("DBB_MODELER_DB2_URL",
                prompt("Specify the DB2 URL in JDBC format", config.getProperty("DBB_MODELER_DB2_URL")));
        }

        // ----------------------------------------------------------------
        // Step 5 – Input configuration parameters
        // ----------------------------------------------------------------
        System.out.println();
        System.out.println("[SETUP] DBB Git Migration Modeler input configuration");
        for (String key : INPUT_KEYS) {
            config.setProperty(key, prompt("Specify input parameter " + key, config.getProperty(key, "")));
        }

        // ----------------------------------------------------------------
        // Step 6 – Publishing options (conditional)
        // ----------------------------------------------------------------
        if ("true".equals(config.getProperty("PUBLISH_ARTIFACTS"))) {
            System.out.println();
            System.out.println("[SETUP] Artifact Repository configuration parameters for publishing application baseline packages.");
            for (String key : PUBLISHING_KEYS) {
                config.setProperty(key, prompt("Specify input parameter " + key, config.getProperty(key, "")));
            }
        }

        // ----------------------------------------------------------------
        // Step 7 – Pipeline CI template
        // ----------------------------------------------------------------
        String pipelineCi = "";
        while (pipelineCi.isEmpty()) {
            System.out.println();
            System.out.println("Specify the pipeline orchestration template to use for initializing the repositories.");
            System.out.println("See available templates at https://github.com/IBM/dbb/tree/main/Templates");
            String choice = prompt(
                "1 for 'Azure DevOps', 2 for 'GitLab CI with distributed runner', 3 for 'GitLab CI with z/OS-native runner', 4 for 'Jenkins', 5 for 'GitHub Actions', 6 for 'None'",
                "1");
            switch (choice) {
                case "1": pipelineCi = "AzureDevOpsPipeline"; break;
                case "2": pipelineCi = "GitlabCIPipeline-for-distributed-runner"; break;
                case "3": pipelineCi = "GitlabCIPipeline-for-zos-native-runner"; break;
                case "4": pipelineCi = "JenkinsPipeline"; break;
                case "5": pipelineCi = "GitHubActionsPipeline"; break;
                case "6": pipelineCi = "None"; break;
                default:
                    System.out.println("[WARNING] The chosen pipeline orchestration template does not match with any of the possible options. Please provide a valid option.");
                    break;
            }
        }
        config.setProperty("PIPELINE_CI", pipelineCi);

        // ----------------------------------------------------------------
        // Step 8 – Choose output folder and write config file
        // ----------------------------------------------------------------
        System.out.println();
        String configFileName = "DBB_GIT_MIGRATION_MODELER-" + new SimpleDateFormat("yyyy-MM-dd.HHmmss").format(new Date()) + ".config";
        String configFolder = "";
        while (configFolder.isEmpty()) {
            String defaultFolder = Paths.get("").toAbsolutePath().resolve("config").toString();
            String input = prompt(
                "[SETUP] Specify the folder where to store the configuration file '" + configFileName + "' (The specified folder must exist)",
                defaultFolder);
            if (!Files.isDirectory(Paths.get(input))) {
                System.err.println("[ERROR] The folder '" + input + "' does not exist.");
            } else {
                configFolder = input;
            }
        }

        String configFilePath = configFolder + File.separator + configFileName;
        int rc = writeConfigFile(config, configFilePath);
        if (rc != 0) {
            return rc;
        }
        System.out.println();
        System.out.println("[SETUP] DBB Git Migration Modeler configuration saved to '" + configFilePath + "'");
        System.out.println();

        // ----------------------------------------------------------------
        // Step 9 – Validate environment
        // ----------------------------------------------------------------
        System.out.println("[SETUP] Validating environment.");
        try {
            ValidateConfiguration.validateEnvironment();
        } catch (Exception e) {
            System.err.println("[ERROR] Environment check failed. Please correct the environment and run again the Setup script. Exiting.");
            System.err.println("[ERROR] " + e.getMessage());
            return 8;
        }

        // ----------------------------------------------------------------
        // Step 10 – Validate configuration file and finalize setup
        // ----------------------------------------------------------------
        System.out.println("[SETUP] Validating Configuration File and finalizing Setup.");
        try {
            ValidateConfiguration.initializeWorkDirectory(configFilePath);
        } catch (Exception e) {
            System.err.println("[ERROR] Configuration check failed. Please correct the configuration and run again the Setup script. Exiting.");
            System.err.println("[ERROR] " + e.getMessage());
            return 8;
        }

        // ----------------------------------------------------------------
        // Step 11 – Check MetadataStore access
        // ----------------------------------------------------------------
        System.out.println("[SETUP] Checking the access to the DBB MetadataStore.");
        try {
            Properties validatedConfig = ValidateConfiguration.validateAndLoadConfiguration(configFilePath);
            MetadataStoreUtility msu = new MetadataStoreUtility();
            String metadataStoreType = validatedConfig.getProperty("DBB_MODELER_METADATASTORE_TYPE", "file");
            if ("file".equals(metadataStoreType)) {
                String fileMetadataStoreDir = validatedConfig.getProperty("DBB_MODELER_FILE_METADATA_STORE_DIR");
                msu.initializeFileMetadataStore(fileMetadataStoreDir);
            } else if ("db2".equals(metadataStoreType)) {
                String jdbcId = validatedConfig.getProperty("DBB_MODELER_DB2_METADATASTORE_JDBC_ID");
                File passwordFile = new File(validatedConfig.getProperty("DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE"));
                Properties db2Props = loadDb2Properties(validatedConfig.getProperty("DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE"));
                msu.initializeDb2MetadataStoreWithPasswordFile(jdbcId, passwordFile, db2Props);
            }
            msu.verifyConnectivity();
        } catch (Exception e) {
            System.err.println("[ERROR] DBB MetadataStore check failed. Please correct the configuration and run again the Setup script. Exiting.");
            System.err.println("[ERROR] " + e.getMessage());
            return 8;
        }

        // ----------------------------------------------------------------
        // Step 12 – Success summary
        // ----------------------------------------------------------------
        System.out.println("*************************************************************************************************************");
        System.out.println();
        System.out.println("Congratulations! The validation of the DBB Git Migration Modeler Setup was successful!");
        System.out.println();
        System.out.println("********************************************* SUGGESTED ACTIONS *********************************************");
        System.out.println("Tailor the following input files prior to using the DBB Git Migration Modeler:");
        System.out.println("  - Applications Mapping file(s) located in " + config.getProperty("DBB_MODELER_APPMAPPINGS_DIR"));
        System.out.println("  - " + config.getProperty("REPOSITORY_PATH_MAPPING_FILE"));
        System.out.println("  - " + config.getProperty("APPLICATION_TYPES_MAPPING") + " (optional)");
        System.out.println("  - " + config.getProperty("TYPE_CONFIGURATIONS_FILE") + " (optional)");
        System.out.println();
        System.out.println("Once tailored, run the following command:");
        System.out.println("  " + modelerHome + "/Migration-Modeler-Start.sh -c " + configFilePath);

        return 0;
    }

    // ----------------------------------------------------------------
    // Config file writer
    // ----------------------------------------------------------------

    private int writeConfigFile(Properties config, String configFilePath) {
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(configFilePath), "IBM-1047"))) {

            writer.println("# DBB Git Migration Modeler configuration settings");
            writer.println("# Generated at " + new Date());
            writer.println();

            writer.println("DBB_MODELER_WORK=" + config.getProperty("DBB_MODELER_WORK"));
            writer.println();

            writer.println("# DBB Git Migration Modeler working folders");
            for (String key : PATH_CONFIG_KEYS) {
                writer.println(key + "=" + config.getProperty(key, ""));
            }
            writer.println();

            writer.println("# DBB Git Migration Modeler - DBB Metadatastore configuration");
            writer.println("DBB_MODELER_METADATASTORE_TYPE=" + config.getProperty("DBB_MODELER_METADATASTORE_TYPE"));
            writer.println("DBB_MODELER_FILE_METADATA_STORE_DIR=" + config.getProperty("DBB_MODELER_FILE_METADATA_STORE_DIR"));
            writer.println("DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE=" + config.getProperty("DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE"));
            writer.println("DBB_MODELER_DB2_METADATASTORE_JDBC_ID=" + config.getProperty("DBB_MODELER_DB2_METADATASTORE_JDBC_ID"));
            writer.println("DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE=" + config.getProperty("DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE"));
            writer.println("DBB_MODELER_DB2_URL=" + config.getProperty("DBB_MODELER_DB2_URL", ""));
            writer.println();

            writer.println("# DBB Git Migration Modeler - Build Framework configuration");
            writer.println("BUILD_FRAMEWORK=" + config.getProperty("BUILD_FRAMEWORK"));
            writer.println("DBB_ZBUILDER=" + config.getProperty("DBB_ZBUILDER"));
            writer.println();

            writer.println("# DBB Git Migration Modeler configuration parms");
            for (String key : INPUT_KEYS) {
                if ("GIT_COMMIT_MESSAGE".equals(key)) {
                    writer.println(key + "=\"" + config.getProperty(key, "") + "\"");
                } else {
                    writer.println(key + "=" + config.getProperty(key, ""));
                }
            }
            for (String key : PUBLISHING_KEYS) {
                writer.println(key + "=" + config.getProperty(key, ""));
            }
            writer.println("PIPELINE_CI=" + config.getProperty("PIPELINE_CI", ""));

        } catch (IOException e) {
            System.err.println("[ERROR] Could not create file '" + configFilePath + "': " + e.getMessage());
            return 8;
        }
        return 0;
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    /**
     * Prints a prompt with a default value, reads user input, and returns the
     * input if non-empty, or the default otherwise.
     */
    private String prompt(String message, String defaultValue) {
        String displayDefault = (defaultValue == null || defaultValue.isEmpty()) ? "" : " [default: " + defaultValue + "]";
        System.out.print(message + displayDefault + ": ");
        String input = console.nextLine().trim();
        return input.isEmpty() ? (defaultValue == null ? "" : defaultValue) : input;
    }

    private String resolveModelerHome() {
        // Prefer the system property set by the launcher; fall back to current directory
        String home = System.getProperty("dbb.modeler.home");
        if (home != null && !home.isEmpty()) {
            return home;
        }
        return Paths.get("").toAbsolutePath().toString();
    }

    private String readRelease() {
        try {
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(modelerHome + "/release.properties")) {
                props.load(fis);
            }
            return props.getProperty("Migration-Modeler-release", "");
        } catch (IOException e) {
            return "";
        }
    }

    private Properties loadDb2Properties(String configFilePath) throws IOException {
        Properties db2Props = new Properties();
        try (FileInputStream fis = new FileInputStream(configFilePath)) {
            db2Props.load(fis);
        }
        return db2Props;
    }

    /** Recursively deletes a directory tree. */
    private void deleteDirectory(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path entry : stream) {
                    deleteDirectory(entry);
                }
            }
        }
        Files.delete(path);
    }

    private void printProlog() {
        System.out.println();
        System.out.println(" DBB Git Migration Modeler");
        System.out.println(" Release:     " + release);
        System.out.println();
        System.out.println(" Script:      Setup.java");
        System.out.println();
        System.out.println(" Description: This class is setting up a work folder including a configuration file that is used");
        System.out.println("              by the DBB Git Migration Modeler process.");
        System.out.println();
        System.out.println("              The user will be prompted for several configuration parameters that are saved");
        System.out.println("              into a configuration file within the work folder.");
        System.out.println("              This configuration file is then passed to ValidateConfiguration");
        System.out.println("              to ensure, as much as possible, the correctness of the provided information.");
        System.out.println();
        System.out.println("              The configuration file is passed to the DBB Git Migration Modeler as a required");
        System.out.println("              input parameter.");
        System.out.println();
        System.out.println("              For more information please refer to: https://github.com/IBM/dbb-git-migration-modeler");
        System.out.println();
    }
}
