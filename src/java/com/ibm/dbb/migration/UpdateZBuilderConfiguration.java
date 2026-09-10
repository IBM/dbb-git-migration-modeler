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
import com.ibm.dbb.migration.utils.Logger;
import org.apache.commons.cli.*;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Updates the DBB zBuilder dbb-build.yaml file to configure the MetadataInit task
 * based on the DBB Git Migration Modeler configuration.
 * Equivalent to updateZBuilderConfiguration.groovy.
 */
public class UpdateZBuilderConfiguration {

    private Properties props;
    private Logger logger;

    public static void main(String[] args) {
        UpdateZBuilderConfiguration updater = new UpdateZBuilderConfiguration();
        try {
            updater.run(args);
        } catch (Exception e) {
            System.err.println("[ERROR] UpdateZBuilderConfiguration failed: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void run(String[] args) throws Exception {
        props = new Properties();
        logger = new Logger();

        parseArgs(args);

        // Read the zBuilder dbb-build.yaml file
        String dbbBuildYamlFilePath = props.getProperty("DBB_ZBUILDER") + "/dbb-build.yaml";
        File dbbBuildYamlFile = new File(dbbBuildYamlFilePath);
        if (!dbbBuildYamlFile.exists()) {
            logger.logMessage("!* [ERROR] The DBB zBuilder dbb-build.yaml file was not found at the location '" +
                dbbBuildYamlFilePath + "'. Exiting.");
            System.exit(1);
        }

        Yaml yaml = new Yaml();
        Map<String, Object> dbbBuildYaml;
        try (FileReader reader = new FileReader(dbbBuildYamlFile)) {
            dbbBuildYaml = yaml.load(reader);
        }

        // Create a timestamped backup
        String timestamp = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date());
        String backupFilePath = props.getProperty("DBB_ZBUILDER") + "/dbb-build-backup-" + timestamp + ".yaml";
        Files.copy(Paths.get(dbbBuildYamlFilePath), Paths.get(backupFilePath), StandardCopyOption.COPY_ATTRIBUTES);
        try {
            com.ibm.dbb.utils.FileUtils.setFileTag(backupFilePath,
                com.ibm.dbb.utils.FileUtils.getFileTag(dbbBuildYamlFilePath));
        } catch (Exception e) {
            // Ignore file tagging on non-z/OS systems
        }

        logger.logMessage("** Modifying the DBB zBuilder 'dbb-build.yaml' file located at '" +
            dbbBuildYamlFilePath + "'.");

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

        String metadataStoreType = props.getProperty("DBB_MODELER_METADATASTORE_TYPE");
        Map<String, String> typeVar = new LinkedHashMap<>();
        typeVar.put("name", "type");
        typeVar.put("value", "\"" + metadataStoreType + "\"");
        variables.add(typeVar);

        if ("file".equals(metadataStoreType)) {
            Map<String, String> locationVar = new LinkedHashMap<>();
            locationVar.put("name", "fileLocation");
            locationVar.put("value", "\"" + props.getProperty("DBB_MODELER_FILE_METADATA_STORE_DIR") + "\"");
            variables.add(locationVar);
        } else if ("db2".equals(metadataStoreType)) {
            Map<String, String> db2UrlVar = new LinkedHashMap<>();
            db2UrlVar.put("name", "db2Url");
            db2UrlVar.put("value", "\"" + props.getProperty("DBB_MODELER_DB2_URL") + "\"");
            variables.add(db2UrlVar);

            Map<String, String> db2ConfVar = new LinkedHashMap<>();
            db2ConfVar.put("name", "db2Conf");
            db2ConfVar.put("value", "\"" + props.getProperty("DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE") + "\"");
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

        logger.logMessage("** The DBB zBuilder 'dbb-build.yaml' file located at '" +
            dbbBuildYamlFilePath + "' was successfully modified.");

        logger.close();
    }

    private void parseArgs(String[] args) throws Exception {
        Options options = new Options();
        options.addOption(Option.builder("l").longOpt("logFile").hasArg().argName("logFile")
            .desc("Relative or absolute path to an output log file").build());
        options.addOption(Option.builder("c").longOpt("configFile").hasArg().required().argName("configFile")
            .desc("Path to the DBB Git Migration Modeler Configuration file").build());

        CommandLine cmd;
        try {
            cmd = new DefaultParser().parse(options, args);
        } catch (ParseException e) {
            System.err.println("[ERROR] Error parsing command line: " + e.getMessage());
            new HelpFormatter().printHelp("UpdateZBuilderConfiguration [options]",
                "Updates the DBB zBuilder dbb-build.yaml with MetadataInit task configuration",
                options, "", true);
            throw new Exception("Error parsing command line: " + e.getMessage(), e);
        }

        if (cmd.hasOption("l")) {
            props.setProperty("logFile", cmd.getOptionValue("l"));
            logger.create(props.getProperty("logFile"));
        }

        String configFilePath = cmd.getOptionValue("c");
        props.setProperty("configurationFilePath", configFilePath);

        Properties configProperties;
        try {
            configProperties = ValidateConfiguration.validateAndLoadConfiguration(configFilePath);
        } catch (Exception e) {
            logger.logMessage("*! [ERROR] Configuration validation failed: " + e.getMessage() + ". Exiting.");
            throw new Exception("Configuration validation failed: " + e.getMessage(), e);
        }

        validateAndLoadConfiguration(configProperties);

        logger.logMessage("** Script configuration:");
        props.forEach((k, v) -> logger.logMessage("\t" + k + " -> " + v));
    }

    private void validateAndLoadConfiguration(Properties configProperties) throws Exception {
        try {
            ConfigurationUtility.loadRequiredProperty(configProperties, props, "DBB_ZBUILDER",
                "The DBB zBuilder instance");

            ConfigurationUtility.validateAndLoadRequiredPropertyValue(configProperties, props,
                "DBB_MODELER_METADATASTORE_TYPE", "The type of MetadataStore");

            String metadataStoreType = props.getProperty("DBB_MODELER_METADATASTORE_TYPE");
            if (!metadataStoreType.equals("file") && !metadataStoreType.equals("db2")) {
                throw new IllegalArgumentException("The type of MetadataStore can only be 'file' or 'db2'.");
            }

            if ("file".equals(metadataStoreType)) {
                ConfigurationUtility.loadRequiredProperty(configProperties, props,
                    "DBB_MODELER_FILE_METADATA_STORE_DIR", "The location for the File MetadataStore");
            } else if ("db2".equals(metadataStoreType)) {
                ConfigurationUtility.validateAndLoadRequiredPropertyValue(configProperties, props,
                    "DBB_MODELER_DB2_URL", "The DB2 URL");
                ConfigurationUtility.loadRequiredProperty(configProperties, props,
                    "DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE", "The DB2 connection configuration file");
            }
        } catch (IllegalArgumentException e) {
            logger.logMessage("*! [ERROR] " + e.getMessage() + " Exiting.");
            throw new Exception(e.getMessage(), e);
        }
    }
}
