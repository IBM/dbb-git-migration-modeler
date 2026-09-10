/*
 * Licensed Materials - Property of IBM
 * (c) Copyright IBM Corporation 2018, 2025. All Rights Reserved.
 *
 * Note to U.S. Government Users Restricted Rights:
 * Use, duplication or disclosure restricted by GSA ADP Schedule
 * Contract with IBM Corp.
 */

package com.ibm.dbb.migration;

import com.ibm.dbb.migration.model.ApplicationDescriptor;
import com.ibm.dbb.migration.model.TypesMapping;
import com.ibm.dbb.migration.utils.ApplicationDescriptorUtils;
import com.ibm.dbb.migration.utils.ConfigurationUtility;
import com.ibm.dbb.migration.utils.Logger;
import org.apache.commons.cli.*;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates zBuilder configuration files (dbb-app.yaml and language configurations).
 * Equivalent to generateZBuilderProperties.groovy.
 */
public class GenerateZBuilderProperties {
    
    private Properties props;
    private Logger logger;
    private ApplicationDescriptorUtils appDescUtils;
    private ApplicationDescriptor applicationDescriptor;
    private TypesMapping typesConfigurations;
    
    public static void main(String[] args) {
        GenerateZBuilderProperties generator = new GenerateZBuilderProperties();
        try {
            generator.run(args);
        } catch (Exception e) {
            System.err.println("[ERROR] Generation failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    public void run(String[] args) throws Exception {
        props = new Properties();
        logger = new Logger();
        appDescUtils = new ApplicationDescriptorUtils();
        
        // Parse command line arguments
        parseArgs(args);
        
        // Read types configurations
        logger.logMessage("** Reading the Types Configurations definitions from '" + 
            props.getProperty("TYPE_CONFIGURATIONS_FILE") + "'.");
        File typesConfigFile = new File(props.getProperty("TYPE_CONFIGURATIONS_FILE"));
        if (!typesConfigFile.exists()) {
            logger.logMessage("!* [ERROR] the Types Configurations file '" + 
                props.getProperty("TYPE_CONFIGURATIONS_FILE") + "' does not exist. Exiting.");
            System.exit(1);
        }
        
        Yaml yaml = new Yaml();
        try (FileReader reader = new FileReader(typesConfigFile)) {
            typesConfigurations = yaml.loadAs(reader, TypesMapping.class);
        }
        
        // Read application descriptor
        String appDir = props.getProperty("DBB_MODELER_APPLICATION_DIR");
        String application = props.getProperty("application");
        File appDescFile = new File(appDir + "/" + application + "/applicationDescriptor.yml");
        
        if (!appDescFile.exists()) {
            logger.logMessage("!* [ERROR] The Application Descriptor file '" + 
                appDescFile.getPath() + "' does not exist. Exiting.");
            System.exit(1);
        }
        
        applicationDescriptor = appDescUtils.readApplicationDescriptor(appDescFile);
        
        logger.logMessage("** Gathering the defined types for files.");
        
        // Collect files and types
        List<FileToLanguageConfig> filesToLanguageConfigs = new ArrayList<>();
        Set<String> typesConfigsToCreate = new HashSet<>();
        Set<String> createdTypesConfigs = new HashSet<>();
        
        if (applicationDescriptor.getSources() != null) {
            for (ApplicationDescriptor.Source sourceGroup : applicationDescriptor.getSources()) {
                if (sourceGroup.getFiles() != null) {
                    for (ApplicationDescriptor.FileDef file : sourceGroup.getFiles()) {
                        if (file.getType() != null && !"UNKNOWN".equals(file.getType())) {
                            typesConfigsToCreate.add(file.getType());
                            filesToLanguageConfigs.add(new FileToLanguageConfig(
                                sourceGroup.getRepositoryPath() + "/" + file.getName() + "." + sourceGroup.getFileExtension(),
                                sourceGroup.getLanguageProcessor(),
                                file.getType()
                            ));
                        }
                    }
                }
            }
        }
        
        // Initialize application dbb-app.yaml structure
        Map<String, Object> applicationDBBAppYaml = new LinkedHashMap<>();
        applicationDBBAppYaml.put("name", application);
        applicationDBBAppYaml.put("tasks", new ArrayList<Map<String, Object>>());
        
        String applicationDBBAppYamlFolderPath = appDir + "/" + application;
        
        // Setup zBuilder configuration folder
        String zBuilderConfigPath = props.getProperty("DBB_MODELER_BUILD_CONFIGURATION");
        File zBuilderConfigFolder = new File(zBuilderConfigPath);
        if (!zBuilderConfigFolder.exists()) {
            zBuilderConfigFolder.mkdirs();
            logger.logMessage("** Creating folder '" + zBuilderConfigPath + "'");
        }
        
        // Generate zBuilder language configuration files
        if (!typesConfigsToCreate.isEmpty()) {
            logger.logMessage("** Generating zBuilder language configuration files.");
            
            for (String typeToCreate : typesConfigsToCreate) {
                TypesMapping.TypeConfiguration typeConfig = findTypeConfiguration(typeToCreate);
                
                if (typeConfig != null) {
                    logger.logMessage("\tType Configuration for type '" + typeToCreate + 
                        "' found in '" + props.getProperty("TYPE_CONFIGURATIONS_FILE") + "'.");
                    
                    File typeConfigYamlFile = new File(zBuilderConfigPath + "/" + typeToCreate + ".yaml");
                    
                    if (!typeConfigYamlFile.exists()) {
                        File yamlFileParentFolder = typeConfigYamlFile.getParentFile();
                        if (!yamlFileParentFolder.exists()) {
                            yamlFileParentFolder.mkdirs();
                        }
                        
                        // Build configuration variables list
                        List<Map<String, String>> typeConfigVariables = new ArrayList<>();
                        if (typeConfig.getVariables() != null) {
                            for (TypesMapping.Variable variable : typeConfig.getVariables()) {
                                Map<String, String> varMap = new LinkedHashMap<>();
                                varMap.put("name", variable.getName());
                                varMap.put("value", variable.getValue());
                                typeConfigVariables.add(varMap);
                            }
                        }
                        
                        // Create YAML structure
                        Map<String, Object> yamlContent = new LinkedHashMap<>();
                        yamlContent.put("config", typeConfigVariables);
                        
                        // Write YAML file
                        DumperOptions options = new DumperOptions();
                        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
                        options.setPrettyFlow(true);
                        Yaml yamlWriter = new Yaml(options);
                        
                        try (OutputStreamWriter writer = new OutputStreamWriter(
                                new FileOutputStream(typeConfigYamlFile), "UTF-8")) {
                            yamlWriter.dump(yamlContent, writer);
                        }
                        
                        // Set file tag for z/OS
                        try {
                            com.ibm.dbb.utils.FileUtils.setFileTag(typeConfigYamlFile.getAbsolutePath(), "UTF-8");
                        } catch (Exception e) {
                            // Ignore on non-z/OS systems
                        }
                    }
                    
                    createdTypesConfigs.add(typeToCreate);
                } else {
                    logger.logMessage("\t[WARNING] No Type Configuration for type '" + typeToCreate + 
                        "' found in '" + props.getProperty("TYPE_CONFIGURATIONS_FILE") + "'.");
                }
            }
        }
        
        // Generate application-level dbb-app.yaml configuration file
        if (!filesToLanguageConfigs.isEmpty() && !createdTypesConfigs.isEmpty()) {
            logger.logMessage("** Generating zBuilder Application configuration file.");
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tasks = (List<Map<String, Object>>) applicationDBBAppYaml.get("tasks");
            
            for (FileToLanguageConfig fileToLangConfig : filesToLanguageConfigs) {
                if (createdTypesConfigs.contains(fileToLangConfig.type)) {
                    // Find or create task
                    Map<String, Object> task = findTask(tasks, fileToLangConfig.task);
                    if (task == null) {
                        task = new LinkedHashMap<>();
                        task.put("task", fileToLangConfig.task);
                        task.put("variables", new ArrayList<Map<String, Object>>());
                        tasks.add(task);
                    }
                    
                    // Find or create languageConfigurationSource variable
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> variables = (List<Map<String, Object>>) task.get("variables");
                    String configPath = "${DBB_BUILD}/build-configuration/" + fileToLangConfig.type + ".yaml";
                    Map<String, Object> langConfigVar = findLanguageConfigurationVariable(variables, configPath);
                    
                    if (langConfigVar == null) {
                        langConfigVar = new LinkedHashMap<>();
                        langConfigVar.put("name", "languageConfigurationSource");
                        langConfigVar.put("value", configPath);
                        langConfigVar.put("forFiles", new ArrayList<String>());
                        variables.add(langConfigVar);
                    }
                    
                    // Add file to forFiles list
                    @SuppressWarnings("unchecked")
                    List<String> forFiles = (List<String>) langConfigVar.get("forFiles");
                    forFiles.add(fileToLangConfig.file);
                }
            }
        } else {
            logger.logMessage("** No Configuration type found for application '" + application + "'.");
        }
        
        // Log creation summary
        if (!createdTypesConfigs.isEmpty()) {
            logger.logMessage("** [INFO] " + createdTypesConfigs.size() + 
                " Language Configuration file" + (createdTypesConfigs.size() == 1 ? "" : "s") + 
                " created in '" + zBuilderConfigPath + "'.");
            logger.logMessage("** [INFO] Before running builds with zBuilder, please copy the content of the '" + 
                zBuilderConfigPath + "' folder to your zBuilder instance located at '" + 
                props.getProperty("DBB_ZBUILDER") + "'.");
        }
        
        // Generate dependencies search paths and impact analysis query patterns
        logger.logMessage("** Generating Dependencies Search Paths and Impact Analysis Query Patterns.");
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) applicationDBBAppYaml.get("tasks");
        
        generateDependencyConfiguration(tasks);
        
        // Write dbb-app.yaml file
        File applicationDBBAppYamlFile = new File(applicationDBBAppYamlFolderPath + "/dbb-app.yaml");
        
        Map<String, Object> rootYaml = new LinkedHashMap<>();
        rootYaml.put("version", "1.0.0");
        rootYaml.put("application", applicationDBBAppYaml);
        
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml yamlWriter = new Yaml(options);
        
        File yamlFileParentFolder = applicationDBBAppYamlFile.getParentFile();
        if (!yamlFileParentFolder.exists()) {
            yamlFileParentFolder.mkdirs();
        }
        
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(applicationDBBAppYamlFile), "UTF-8")) {
            yamlWriter.dump(rootYaml, writer);
        }
        
        // Set file tag for z/OS
        try {
            com.ibm.dbb.utils.FileUtils.setFileTag(applicationDBBAppYamlFile.getAbsolutePath(), "UTF-8");
        } catch (Exception e) {
            // Ignore on non-z/OS systems
        }
        
        logger.logMessage("** Application Configuration file '" + applicationDBBAppYamlFile.getAbsolutePath() + 
            "' successfully created.");
        logger.logMessage("** [INFO] Make sure the zBuilder Configuration files (Language Task definitions) are accurate before running a build with zBuilder.");
        logger.logMessage("** [INFO] For each Language Task definition, the Dependency Search Path variable potentially needs to be updated to match the layout of the Git repositories.");
        
        logger.close();
    }
    
    private void generateDependencyConfiguration(List<Map<String, Object>> tasks) {
        if (applicationDescriptor.getSources() == null) return;
        
        List<ApplicationDescriptor.Source> sourceGroupsWithPrograms = applicationDescriptor.getSources().stream()
            .filter(source -> "Program".equalsIgnoreCase(source.getArtifactsType()))
            .collect(Collectors.toList());
        
        for (ApplicationDescriptor.Source sourceGroupWithPrograms : sourceGroupsWithPrograms) {
            // Find or create ImpactAnalysis task
            Map<String, Object> impactAnalysisTask = findTask(tasks, "ImpactAnalysis");
            if (impactAnalysisTask == null) {
                impactAnalysisTask = new LinkedHashMap<>();
                impactAnalysisTask.put("task", "ImpactAnalysis");
                impactAnalysisTask.put("variables", new ArrayList<Map<String, Object>>());
                tasks.add(impactAnalysisTask);
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> impactVariables = (List<Map<String, Object>>) impactAnalysisTask.get("variables");
            
            // Find or create impactQueryPatterns variable
            Map<String, Object> impactQueryPatterns = findVariable(impactVariables, "impactQueryPatterns");
            if (impactQueryPatterns == null) {
                impactQueryPatterns = new LinkedHashMap<>();
                impactQueryPatterns.put("name", "impactQueryPatterns");
                impactQueryPatterns.put("value", new ArrayList<Map<String, Object>>());
                impactVariables.add(impactQueryPatterns);
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> impactQueryPatternsValue = (List<Map<String, Object>>) impactQueryPatterns.get("value");
            
            if ("COBOL".equalsIgnoreCase(sourceGroupWithPrograms.getLanguage())) {
                generateCobolDependencyConfiguration(sourceGroupWithPrograms, impactQueryPatternsValue, tasks);
            } else if ("ASM".equalsIgnoreCase(sourceGroupWithPrograms.getLanguage())) {
                generateAsmDependencyConfiguration(sourceGroupWithPrograms, impactQueryPatternsValue, tasks);
            } else if ("link".equalsIgnoreCase(sourceGroupWithPrograms.getLanguage())) {
                generateLinkDependencyConfiguration(sourceGroupWithPrograms, impactQueryPatternsValue);
            }
        }
    }
    
    private void generateCobolDependencyConfiguration(ApplicationDescriptor.Source sourceGroupWithPrograms,
                                                     List<Map<String, Object>> impactQueryPatternsValue,
                                                     List<Map<String, Object>> tasks) {
        // Find or create dependency pattern for this file extension
        Map<String, Object> dependencyPattern = findDependencyPattern(impactQueryPatternsValue, 
            sourceGroupWithPrograms.getFileExtension());
        if (dependencyPattern == null) {
            dependencyPattern = new LinkedHashMap<>();
            dependencyPattern.put("languageExt", sourceGroupWithPrograms.getFileExtension());
            dependencyPattern.put("dependencyPatterns", new ArrayList<String>());
            impactQueryPatternsValue.add(dependencyPattern);
        }
        
        @SuppressWarnings("unchecked")
        List<String> dependencyPatterns = (List<String>) dependencyPattern.get("dependencyPatterns");
        
        // Add BMS patterns
        List<ApplicationDescriptor.Source> bmsGroups = applicationDescriptor.getSources().stream()
            .filter(source -> "ASM".equalsIgnoreCase(source.getLanguage()) && 
                            "BMS".equals(source.getArtifactsType()))
            .collect(Collectors.toList());
        
        for (ApplicationDescriptor.Source bmsGroup : bmsGroups) {
            String pattern = "${APP_DIR_NAME}/" + bmsGroup.getRepositoryPath() + "/*." + bmsGroup.getFileExtension();
            if (!dependencyPatterns.contains(pattern)) {
                dependencyPatterns.add(pattern);
            }
        }
        
        // Add COBOL Copybook patterns
        List<ApplicationDescriptor.Source> copyGroups = applicationDescriptor.getSources().stream()
            .filter(source -> "COBOL".equalsIgnoreCase(source.getLanguage()) && 
                            "Include File".equals(source.getArtifactsType()))
            .collect(Collectors.toList());
        
        for (ApplicationDescriptor.Source copyGroup : copyGroups) {
            String pattern = "${APP_DIR_NAME}/" + copyGroup.getRepositoryPath() + "/*." + copyGroup.getFileExtension();
            if (!dependencyPatterns.contains(pattern)) {
                dependencyPatterns.add(pattern);
            }
        }
        
        // Add COBOL program pattern
        String programPattern = "${APP_DIR_NAME}/" + sourceGroupWithPrograms.getRepositoryPath() + 
            "/*." + sourceGroupWithPrograms.getFileExtension();
        if (!dependencyPatterns.contains(programPattern)) {
            dependencyPatterns.add(programPattern);
        }
        
        // Create dependency search paths for language task
        Map<String, Object> languageTask = findTask(tasks, sourceGroupWithPrograms.getLanguageProcessor());
        if (languageTask == null) {
            languageTask = new LinkedHashMap<>();
            languageTask.put("task", sourceGroupWithPrograms.getLanguageProcessor());
            languageTask.put("variables", new ArrayList<Map<String, Object>>());
            tasks.add(languageTask);
        }
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> languageVariables = (List<Map<String, Object>>) languageTask.get("variables");
        
        for (ApplicationDescriptor.Source copyGroup : copyGroups) {
            Map<String, Object> dependencySearchPath = new LinkedHashMap<>();
            dependencySearchPath.put("name", "dependencySearchPath");
            dependencySearchPath.put("value", "search:${WORKSPACE}/?path=${APP_DIR_NAME}/" + 
                copyGroup.getRepositoryPath() + "/*." + copyGroup.getFileExtension());
            
            if (!containsVariable(languageVariables, dependencySearchPath)) {
                languageVariables.add(dependencySearchPath);
            }
        }
    }
    
    private void generateAsmDependencyConfiguration(ApplicationDescriptor.Source sourceGroupWithPrograms,
                                                   List<Map<String, Object>> impactQueryPatternsValue,
                                                   List<Map<String, Object>> tasks) {
        Map<String, Object> dependencyPattern = new LinkedHashMap<>();
        dependencyPattern.put("languageExt", sourceGroupWithPrograms.getFileExtension());
        dependencyPattern.put("dependencyPatterns", new ArrayList<String>());
        
        @SuppressWarnings("unchecked")
        List<String> dependencyPatterns = (List<String>) dependencyPattern.get("dependencyPatterns");
        
        // Add ASM macro patterns
        List<ApplicationDescriptor.Source> asmMacroGroups = applicationDescriptor.getSources().stream()
            .filter(source -> "ASM".equalsIgnoreCase(source.getLanguage()) && 
                            "Include File".equals(source.getArtifactsType()))
            .collect(Collectors.toList());
        
        for (ApplicationDescriptor.Source asmMacroGroup : asmMacroGroups) {
            dependencyPatterns.add("${APP_DIR_NAME}/" + asmMacroGroup.getRepositoryPath() + 
                "/*." + asmMacroGroup.getFileExtension());
        }
        
        // Add ASM program pattern
        dependencyPatterns.add("${APP_DIR_NAME}/" + sourceGroupWithPrograms.getRepositoryPath() + 
            "/*." + sourceGroupWithPrograms.getFileExtension());
        
        impactQueryPatternsValue.add(dependencyPattern);
        
        // Create dependency search paths
        Map<String, Object> languageTask = findTask(tasks, sourceGroupWithPrograms.getLanguageProcessor());
        if (languageTask == null) {
            languageTask = new LinkedHashMap<>();
            languageTask.put("task", sourceGroupWithPrograms.getLanguageProcessor());
            languageTask.put("variables", new ArrayList<Map<String, Object>>());
            tasks.add(languageTask);
        }
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> languageVariables = (List<Map<String, Object>>) languageTask.get("variables");
        
        for (ApplicationDescriptor.Source asmMacroGroup : asmMacroGroups) {
            Map<String, Object> dependencySearchPath = new LinkedHashMap<>();
            dependencySearchPath.put("name", "dependencySearchPath");
            dependencySearchPath.put("value", "search:${WORKSPACE}/?path=${APP_DIR_NAME}/" + 
                asmMacroGroup.getRepositoryPath() + "/*." + asmMacroGroup.getFileExtension());
            
            if (!containsVariable(languageVariables, dependencySearchPath)) {
                languageVariables.add(dependencySearchPath);
            }
        }
    }
    
    private void generateLinkDependencyConfiguration(ApplicationDescriptor.Source sourceGroupWithPrograms,
                                                    List<Map<String, Object>> impactQueryPatternsValue) {
        Map<String, Object> dependencyPattern = new LinkedHashMap<>();
        dependencyPattern.put("languageExt", sourceGroupWithPrograms.getFileExtension());
        dependencyPattern.put("dependencyPatterns", new ArrayList<String>());
        
        @SuppressWarnings("unchecked")
        List<String> dependencyPatterns = (List<String>) dependencyPattern.get("dependencyPatterns");
        
        // Add COBOL program patterns
        List<ApplicationDescriptor.Source> cobolGroups = applicationDescriptor.getSources().stream()
            .filter(source -> "COBOL".equalsIgnoreCase(source.getLanguage()) && 
                            "Program".equals(source.getArtifactsType()))
            .collect(Collectors.toList());
        
        for (ApplicationDescriptor.Source cobolGroup : cobolGroups) {
            dependencyPatterns.add("${APP_DIR_NAME}/" + cobolGroup.getRepositoryPath() + 
                "/*." + cobolGroup.getFileExtension());
        }
    }
    
    private Map<String, Object> findTask(List<Map<String, Object>> tasks, String taskName) {
        return tasks.stream()
            .filter(task -> taskName.equals(task.get("task")))
            .findFirst()
            .orElse(null);
    }
    
    private Map<String, Object> findVariable(List<Map<String, Object>> variables, String varName) {
        return variables.stream()
            .filter(var -> varName.equals(var.get("name")))
            .findFirst()
            .orElse(null);
    }
    
    private Map<String, Object> findLanguageConfigurationVariable(List<Map<String, Object>> variables, String configPath) {
        return variables.stream()
            .filter(var -> "languageConfigurationSource".equals(var.get("name")) && 
                          configPath.equals(var.get("value")))
            .findFirst()
            .orElse(null);
    }
    
    private Map<String, Object> findDependencyPattern(List<Map<String, Object>> patterns, String languageExt) {
        return patterns.stream()
            .filter(pattern -> languageExt.equals(pattern.get("languageExt")))
            .findFirst()
            .orElse(null);
    }
    
    private boolean containsVariable(List<Map<String, Object>> variables, Map<String, Object> varToFind) {
        String nameToFind = (String) varToFind.get("name");
        String valueToFind = (String) varToFind.get("value");
        
        return variables.stream()
            .anyMatch(var -> nameToFind.equals(var.get("name")) && valueToFind.equals(var.get("value")));
    }
    
    private Options createOptions() {
        Options options = new Options();
        
        options.addOption(Option.builder("a")
            .longOpt("application")
            .hasArg()
            .required()
            .argName("application")
            .desc("Application name")
            .build());
            
        options.addOption(Option.builder("l")
            .longOpt("logFile")
            .hasArg()
            .argName("logFile")
            .desc("Relative or absolute path to an output log file")
            .build());
            
        options.addOption(Option.builder("c")
            .longOpt("configFile")
            .hasArg()
            .required()
            .argName("configFile")
            .desc("Path to the DBB Git Migration Modeler Configuration file")
            .build());
            
        options.addOption(Option.builder("h")
            .longOpt("help")
            .desc("Print this help message")
            .build());
            
        return options;
    }
    
    private void parseArgs(String[] args) throws Exception {
        Options options = createOptions();
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        
        CommandLine cmd;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.err.println("[ERROR] Error parsing command line: " + e.getMessage());
            formatter.printHelp("GenerateZBuilderProperties [options]",
                "Generates zBuilder configuration files", 
                options, "", true);
            System.exit(1);
            return;
        }
        
        // Process log file option
        if (cmd.hasOption("l")) {
            String logFile = cmd.getOptionValue("l");
            props.setProperty("logFile", logFile);
            logger.create(logFile);
        }
        
        // Process application option
        if (cmd.hasOption("a")) {
            props.setProperty("application", cmd.getOptionValue("a"));
        } else {
            logger.logMessage("*! [ERROR] The Application name (option -a/--application) must be provided. Exiting.");
            System.exit(1);
        }
        
        // Process configuration file option
        if (cmd.hasOption("c")) {
            String configFilePath = cmd.getOptionValue("c");
            props.setProperty("configurationFilePath", configFilePath);
            
            // Validate and load configuration using ValidateConfiguration
            logger.logMessage("** Validating configuration file...");
            try {
                Properties configProperties = ValidateConfiguration.validateAndLoadConfiguration(configFilePath);
                validateAndLoadConfiguration(configProperties);
            } catch (Exception e) {
                logger.logMessage("*! [ERROR] Configuration validation failed: " + e.getMessage());
                System.exit(1);
            }
        } else {
            logger.logMessage("*! [ERROR] The path to the DBB Git Migration Modeler Configuration file was not specified. Exiting.");
            System.exit(1);
        }
        
        // Log configuration
        logger.logMessage("** Script configuration:");
        props.forEach((k, v) -> logger.logMessage("\t" + k + " -> " + v));
    }
    
    private void validateAndLoadConfiguration(Properties configProperties) {
        try {
            // Validate DBB_MODELER_BUILD_CONFIGURATION (no path check, just required)
            ConfigurationUtility.validateAndLoadRequiredPropertyValue(configProperties, props,
                "DBB_MODELER_BUILD_CONFIGURATION", "The Build Configuration folder");
            
            // Validate DBB_ZBUILDER directory
            ConfigurationUtility.loadRequiredProperty(configProperties, props, "DBB_ZBUILDER",
                "The DBB zBuilder instance");
            
            // Validate DBB_MODELER_APPLICATION_DIR directory
            ConfigurationUtility.loadRequiredProperty(configProperties, props, "DBB_MODELER_APPLICATION_DIR",
                "The Application's directory");
            
            // Validate TYPE_CONFIGURATIONS_FILE
            ConfigurationUtility.loadRequiredProperty(configProperties, props, "TYPE_CONFIGURATIONS_FILE",
                "The Types Configurations file");
        } catch (IllegalArgumentException e) {
            logger.logMessage("*! [ERROR] " + e.getMessage() + " Exiting.");
            System.exit(1);
        }
    }
    
    private TypesMapping.TypeConfiguration findTypeConfiguration(String typeToFind) {
        if (typesConfigurations.getTypesConfigurations() != null) {
            return typesConfigurations.getTypesConfigurations().stream()
                .filter(config -> typeToFind.equals(config.getTypeConfiguration()))
                .findFirst()
                .orElse(null);
        }
        return null;
    }
    
    // Helper class for file to language configuration mapping
    private static class FileToLanguageConfig {
        String file;
        String task;
        String type;
        
        FileToLanguageConfig(String file, String task, String type) {
            this.file = file;
            this.task = task;
            this.type = type;
        }
    }
}