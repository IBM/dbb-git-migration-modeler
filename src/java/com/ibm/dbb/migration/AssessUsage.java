/*
 * Licensed Materials - Property of IBM
 * (c) Copyright IBM Corporation 2018, 2025. All Rights Reserved.
 *
 * Note to U.S. Government Users Restricted Rights:
 * Use, duplication or disclosure restricted by GSA ADP Schedule
 * Contract with IBM Corp.
 */

package com.ibm.dbb.migration;

import com.ibm.dbb.build.BuildException;
import com.ibm.dbb.build.CopyToPDS;
import com.ibm.dbb.dependency.*;
import com.ibm.dbb.metadata.*;
import com.ibm.dbb.migration.model.ApplicationDescriptor;
import com.ibm.dbb.migration.model.RepositoryPathsMapping;
import com.ibm.dbb.migration.utils.ApplicationDescriptorUtils;
import com.ibm.dbb.migration.utils.ConfigurationUtility;
import com.ibm.dbb.migration.utils.FileUtility;
import com.ibm.dbb.migration.utils.Logger;
import com.ibm.dbb.migration.utils.MetadataStoreUtility;
import org.apache.commons.cli.*;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Assesses usage patterns of include files and programs across applications.
 * Analyzes dependencies and updates application descriptors with usage information.
 * Equivalent to assessUsage.groovy.
 */
public class AssessUsage {
    
    private Properties props;
    private Logger logger;
    private ApplicationDescriptorUtils appDescUtils;
    private MetadataStoreUtility metadataStoreUtils;
    private File originalApplicationDescriptorFile;
    private File updatedApplicationDescriptorFile;
    private ApplicationDescriptor applicationDescriptor;
    private RepositoryPathsMapping repositoryPathsMapping;
    
    public static void main(String[] args) {
        AssessUsage assessor = new AssessUsage();
        try {
            assessor.run(args);
        } catch (Exception e) {
            System.err.println("[ERROR] Assessment failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    public void run(String[] args) throws Exception {
        props = new Properties();
        logger = new Logger();
        appDescUtils = new ApplicationDescriptorUtils();
        metadataStoreUtils = new MetadataStoreUtility();
        
        // Parse command line arguments
        parseArgs(args);
        
        // Initialize script parameters
        initScriptParameters();
        
        // Get and assess include files
        logger.logMessage("** Getting the list of files of 'Include File' type.");
        Map<String, Map<String, String>> includeFiles = getIncludeFilesFromApplicationDescriptor();
        
        if (includeFiles != null && !includeFiles.isEmpty()) {
            assessImpactedFilesForIncludeFiles(includeFiles);
        } else {
            logger.logMessage("*** No source found with 'Include File' type.");
        }
        
        // Get and assess programs if control transfer scanning is enabled
        if (props.getProperty("SCAN_CONTROL_TRANSFERS", "false").equalsIgnoreCase("true")) {
            logger.logMessage("** Getting the list of files of 'Program' type.");
            Map<String, Map<String, String>> programs = getProgramsFromApplicationDescriptor();
            
            if (programs != null && !programs.isEmpty()) {
                assessImpactedFilesForPrograms(programs);
            } else {
                logger.logMessage("*** No source found with 'Program' type.");
            }
        }
        
        logger.close();
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
            formatter.printHelp("AssessUsage [options]",
                "Assesses usage patterns of include files and programs", 
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
            logger.logMessage("*! [ERROR] The path to the DBB Git Migration Modeler Configuration file was not specified ('-c/--configFile' parameter). Exiting.");
            System.exit(1);
        }
        
        // Log configuration
        logger.logMessage("** Script configuration:");
        props.forEach((k, v) -> logger.logMessage("\t" + k + " -> " + v));
    }
    
    private void validateAndLoadConfiguration(Properties configProperties) {
        // Validate and load DBB_MODELER_APPCONFIG_DIR
        try {
            ConfigurationUtility.loadRequiredProperty(configProperties, props,
                "DBB_MODELER_APPCONFIG_DIR", "The Configurations directory");
        } catch (IllegalArgumentException e) {
            logger.logMessage("*! [ERROR] " + e.getMessage() + " Exiting.");
            System.exit(1);
        }
        
        // Validate and load DBB_MODELER_APPLICATION_DIR
        try {
            ConfigurationUtility.loadRequiredProperty(configProperties, props,
                "DBB_MODELER_APPLICATION_DIR", "The Applications directory");
        } catch (IllegalArgumentException e) {
            logger.logMessage("*! [ERROR] " + e.getMessage() + " Exiting.");
            System.exit(1);
        }
        
        // Validate metadata store type
        try {
            ConfigurationUtility.validateAndLoadRequiredPropertyValue(configProperties, props,
                "DBB_MODELER_METADATASTORE_TYPE", "The type of MetadataStore");
            String metadataStoreType = props.getProperty("DBB_MODELER_METADATASTORE_TYPE");
            if (!metadataStoreType.equals("file") && !metadataStoreType.equals("db2")) {
                throw new IllegalArgumentException("The type of MetadataStore can only be 'file' or 'db2'");
            }
        } catch (IllegalArgumentException e) {
            logger.logMessage("*! [ERROR] " + e.getMessage() + " Exiting.");
            System.exit(1);
        }
        
        // Validate APPLICATION_DEFAULT_BRANCH
        try {
            ConfigurationUtility.validateAndLoadRequiredPropertyValue(configProperties, props,
                "APPLICATION_DEFAULT_BRANCH", "The default branch name setting APPLICATION_DEFAULT_BRANCH");
        } catch (IllegalArgumentException e) {
            logger.logMessage("*! [ERROR] " + e.getMessage() + " Exiting.");
            System.exit(1);
        }
        
        // Load MOVE_FILES_FLAG with default
        String moveFilesFlag = configProperties.getProperty("MOVE_FILES_FLAG", "true");
        props.setProperty("MOVE_FILES_FLAG", moveFilesFlag);
        if (configProperties.getProperty("MOVE_FILES_FLAG") == null) {
            logger.logMessage("** [WARNING] The MOVE_FILES_FLAG setting is not specified and will be set to 'true' by default.");
        }
        
        // Validate REPOSITORY_PATH_MAPPING_FILE
        try {
            ConfigurationUtility.loadRequiredProperty(configProperties, props,
                "REPOSITORY_PATH_MAPPING_FILE", "The reference to the REPOSITORY_PATH_MAPPING_FILE");
        } catch (IllegalArgumentException e) {
            logger.logMessage("*! [ERROR] " + e.getMessage() + " Exiting.");
            System.exit(1);
        }
        
        // Validate metadata store specific properties
        String metadataStoreType = props.getProperty("DBB_MODELER_METADATASTORE_TYPE");
        try {
            if (metadataStoreType.equals("file")) {
                ConfigurationUtility.loadRequiredProperty(configProperties, props,
                    "DBB_MODELER_FILE_METADATA_STORE_DIR", "The location for the File MetadataStore");
            } else if (metadataStoreType.equals("db2")) {
                // Validate Db2 JDBC ID (value only, no path check)
                ConfigurationUtility.validateAndLoadRequiredPropertyValue(configProperties, props,
                    "DBB_MODELER_DB2_METADATASTORE_JDBC_ID", "Db2 MetadataStore JDBC ID");
                
                // Validate Db2 config file (requires path check)
                ConfigurationUtility.loadRequiredProperty(configProperties, props,
                    "DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE", "The Db2 Connection configuration file");
                
                // Validate Db2 password file (value only, no path check needed)
                ConfigurationUtility.validateAndLoadRequiredPropertyValue(configProperties, props,
                    "DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE", "Db2 MetadataStore password file");
            }
            
            // Load SCAN_CONTROL_TRANSFERS
            ConfigurationUtility.validateAndLoadRequiredPropertyValue(configProperties, props,
                "SCAN_CONTROL_TRANSFERS", "The Scan Control Transfers parameter (SCAN_CONTROL_TRANSFERS)");
        } catch (IllegalArgumentException e) {
            logger.logMessage("*! [ERROR] " + e.getMessage() + " Exiting.");
            System.exit(1);
        }
    }
    
    private void initScriptParameters() throws Exception {
        String application = props.getProperty("application");
        String applicationFolder = props.getProperty("DBB_MODELER_APPLICATION_DIR") + "/" + application;
        
        if (!new File(applicationFolder).exists()) {
            logger.logMessage("*! [ERROR] The Application Directory '" + applicationFolder + "' does not exist. Exiting.");
            System.exit(1);
        }
        props.setProperty("applicationDir", applicationFolder);
        
        // Initialize metadata store
        if (props.getProperty("DBB_MODELER_FILE_METADATA_STORE_DIR") != null) {
            metadataStoreUtils.initializeFileMetadataStore(props.getProperty("DBB_MODELER_FILE_METADATA_STORE_DIR"));
        } else {
            File db2ConfigFile = new File(props.getProperty("DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE"));
            Properties db2Props = new Properties();
            try (FileInputStream fis = new FileInputStream(db2ConfigFile)) {
                db2Props.load(fis);
            }
            metadataStoreUtils.initializeDb2MetadataStoreWithPasswordFile(
                props.getProperty("DBB_MODELER_DB2_METADATASTORE_JDBC_ID"),
                new File(props.getProperty("DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE")),
                db2Props
            );
        }
        
        // Load application descriptor
        originalApplicationDescriptorFile = new File(props.getProperty("DBB_MODELER_APPCONFIG_DIR") + "/" + application + ".yml");
        updatedApplicationDescriptorFile = new File(applicationFolder + "/applicationDescriptor.yml");
        
        if (updatedApplicationDescriptorFile.exists()) {
            applicationDescriptor = appDescUtils.readApplicationDescriptor(updatedApplicationDescriptorFile);
        } else if (originalApplicationDescriptorFile.exists()) {
            FileUtility.copyFileWithTags(originalApplicationDescriptorFile, updatedApplicationDescriptorFile);
            applicationDescriptor = appDescUtils.readApplicationDescriptor(updatedApplicationDescriptorFile);
        } else {
            logger.logMessage("*! [ERROR] Application Descriptor file '" + originalApplicationDescriptorFile.getPath() + "' was not found. Exiting.");
            System.exit(1);
        }
        
        // Read repository paths mapping
        logger.logMessage("** Reading the Repository Layout Mapping definition.");
        File repoMappingFile = new File(props.getProperty("REPOSITORY_PATH_MAPPING_FILE"));
        if (!repoMappingFile.exists()) {
            logger.logMessage("*! [WARNING] The Repository Path Mapping file " + repoMappingFile.getPath() + " was not found. Exiting.");
            System.exit(1);
        }
        
        Yaml yaml = new Yaml();
        try (FileReader reader = new FileReader(repoMappingFile)) {
            repositoryPathsMapping = yaml.loadAs(reader, RepositoryPathsMapping.class);
        }
    }
    
    private Map<String, Map<String, String>> getIncludeFilesFromApplicationDescriptor() {
        Map<String, Map<String, String>> files = new HashMap<>();
        
        if (applicationDescriptor.getSources() == null) return files;
        
        applicationDescriptor.getSources().stream()
            .filter(source -> "Include File".equalsIgnoreCase(source.getArtifactsType()))
            .forEach(source -> {
                if (source.getFiles() != null) {
                    source.getFiles().forEach(file -> {
                        String impactSearchRule = String.format(
                            "search:[:COPY,SQL INCLUDE:]%s/?path=%s/%s/*.%s;**/%s/*.%s",
                            props.getProperty("DBB_MODELER_APPLICATION_DIR"),
                            props.getProperty("application"),
                            source.getRepositoryPath(),
                            source.getFileExtension(),
                            source.getRepositoryPath(),
                            source.getFileExtension()
                        );
                        
                        Map<String, String> properties = new HashMap<>();
                        properties.put("impactSearchRule", impactSearchRule);
                        properties.put("repositoryPath", source.getRepositoryPath());
                        properties.put("fileExtension", source.getFileExtension());
                        properties.put("artifactsType", source.getArtifactsType());
                        properties.put("sourceGroupName", source.getName());
                        properties.put("language", source.getLanguage());
                        properties.put("languageProcessor", source.getLanguageProcessor());
                        properties.put("type", file.getType());
                        
                        files.put(file.getName(), properties);
                    });
                }
            });
        
        return files;
    }
    
    private Map<String, Map<String, String>> getProgramsFromApplicationDescriptor() {
        Map<String, Map<String, String>> files = new HashMap<>();
        
        if (applicationDescriptor.getSources() == null) return files;
        
        applicationDescriptor.getSources().stream()
            .filter(source -> "Program".equalsIgnoreCase(source.getArtifactsType()))
            .forEach(source -> {
                if (source.getFiles() != null) {
                    source.getFiles().forEach(file -> {
                        String impactSearchRule = String.format(
                            "search:[:CALL]%s/?path=%s/%s/*.%s;**/%s/*.%s",
                            props.getProperty("DBB_MODELER_APPLICATION_DIR"),
                            props.getProperty("application"),
                            source.getRepositoryPath(),
                            source.getFileExtension(),
                            source.getRepositoryPath(),
                            source.getFileExtension()
                        );
                        
                        Map<String, String> properties = new HashMap<>();
                        properties.put("impactSearchRule", impactSearchRule);
                        properties.put("repositoryPath", source.getRepositoryPath());
                        properties.put("fileExtension", source.getFileExtension());
                        properties.put("artifactsType", source.getArtifactsType());
                        properties.put("sourceGroupName", source.getName());
                        properties.put("language", source.getLanguage());
                        properties.put("languageProcessor", source.getLanguageProcessor());
                        properties.put("type", file.getType());
                        
                        files.put(file.getName(), properties);
                    });
                }
            });
        
        return files;
    }
    
    private void assessImpactedFilesForIncludeFiles(Map<String, Map<String, String>> includeFiles) throws Exception {
        List<String> processedFiles = new ArrayList<>();
        
        // Build list of repository files for sorting
        List<String> repositoryFiles = includeFiles.entrySet().stream()
            .map(entry -> {
                Map<String, String> props = entry.getValue();
                return props.get("repositoryPath") + "/" + entry.getKey() + "." + props.get("fileExtension");
            })
            .collect(Collectors.toList());
        
        // Sort files by dependency tree
        SortResult sortResult = sortListByDependencyTree(repositoryFiles);
        List<String> sortedList = sortResult.sortedList;
        Map<String, List<LogicalDependency>> nestedDependencies = sortResult.nestedDependencies;
        
        for (String repoFile : sortedList) {
            // Find the file in the original map
            String file = includeFiles.keySet().stream()
                .filter(f -> CopyToPDS.createMemberName(repoFile).toLowerCase().equals(f.toLowerCase()))
                .findFirst()
                .orElse(null);
            
            if (file == null) continue;
            
            Map<String, String> properties = includeFiles.get(file);
            String repositoryPath = properties.get("repositoryPath");
            String fileExtension = properties.get("fileExtension");
            String qualifiedFile = repositoryPath + "/" + file + "." + fileExtension;
            
            File sourceFile = new File(props.getProperty("DBB_MODELER_APPLICATION_DIR") + "/" + 
                props.getProperty("application") + "/" + qualifiedFile);
            
            if (!sourceFile.exists()) {
                logger.logMessage("*! [WARNING] The Include File '" + file + "' was not found on the filesystem. Skipping analysis.");
                continue;
            }
            
            if (processedFiles.contains(file)) continue;
            
            // Analyze impacts
            logger.logMessage("** Analyzing impacted applications for file '" + props.getProperty("application") + "/" + qualifiedFile + "'.");
            Map<ImpactFile, String> impactedFilesWithBuildGroup = findImpactedFilesWithBuildGroup(
                properties.get("impactSearchRule"),
                props.getProperty("application") + "/" + qualifiedFile
            );
            
            Set<String> referencingCollections = new HashSet<>();
            if (!impactedFilesWithBuildGroup.isEmpty()) {
                logger.logMessage("\tFiles depending on '" + repositoryPath + "/" + file + "." + fileExtension + "' :");
                for (Map.Entry<ImpactFile, String> entry : impactedFilesWithBuildGroup.entrySet()) {
                    ImpactFile impactedFile = entry.getKey();
                    String buildGroupName = entry.getValue();
                    String referencingCollection = buildGroupName.replace("-" + props.getProperty("APPLICATION_DEFAULT_BRANCH"), "");
                    logger.logMessage("\t'" + impactedFile.getFile() + "' in Application '" + referencingCollection + "'");
                    referencingCollections.add(referencingCollection);
                }
            }
            
            // Assess usage based on number of referencing collections
            assessIncludeFileUsage(file, properties, referencingCollections, nestedDependencies.get(qualifiedFile), 
                includeFiles, processedFiles);
        }
    }
    
    private void assessIncludeFileUsage(String file, Map<String, String> properties, 
                                       Set<String> referencingCollections,
                                       List<LogicalDependency> nestedDeps,
                                       Map<String, Map<String, String>> includeFiles,
                                       List<String> processedFiles) throws Exception {
        
        String sourceGroupName = properties.get("sourceGroupName");
        String language = properties.get("language");
        String languageProcessor = properties.get("languageProcessor");
        String artifactsType = properties.get("artifactsType");
        String fileExtension = properties.get("fileExtension");
        String repositoryPath = properties.get("repositoryPath");
        String type = properties.get("type");
        
        if (referencingCollections.size() == 1) {
            String referencingApp = referencingCollections.iterator().next();
            logger.logMessage("\t==> '" + file + "' is owned by the '" + referencingApp + "' application");
            
            if (props.getProperty("application").equals(referencingApp)) {
                // Update usage to private
                appDescUtils.appendFileDefinition(applicationDescriptor, sourceGroupName, language, 
                    languageProcessor, artifactsType, fileExtension, repositoryPath, file, type, "private");
                logger.logMessage("\t==> Updating usage of Include File '" + file + "' to 'private' in '" + 
                    updatedApplicationDescriptorFile.getPath() + "'.");
            } else {
                // File belongs to another application
                if (Boolean.parseBoolean(props.getProperty("MOVE_FILES_FLAG"))) {
                    // Perform actual file movement
                    moveFileToOwningApplication(file, properties, referencingApp);
                } else {
                    String usageLabel = props.getProperty("application").equals("UNASSIGNED") ? "shared" : "public";
                    logger.logMessage("\t==> Updating usage of Include File '" + file + "' to '" + usageLabel + "'.");
                    appDescUtils.appendFileDefinition(applicationDescriptor, sourceGroupName, language,
                        languageProcessor, artifactsType, fileExtension, repositoryPath, file, type, usageLabel);
                    
                    // Update consumer application descriptor
                    updateConsumerApplicationDescriptor(referencingApp, "artifactrepository", applicationDescriptor);
                }
            }
            appDescUtils.writeApplicationDescriptor(updatedApplicationDescriptorFile, applicationDescriptor);
            
        } else if (referencingCollections.size() > 1) {
            logger.logMessage("\t==> '" + file + "' referenced by multiple applications - " + referencingCollections);
            String usageLabel = props.getProperty("application").equals("UNASSIGNED") ? "shared" : "public";
            logger.logMessage("\t==> Updating usage of Include File '" + file + "' to '" + usageLabel + "'.");
            appDescUtils.appendFileDefinition(applicationDescriptor, sourceGroupName, language,
                languageProcessor, artifactsType, fileExtension, repositoryPath, file, type, usageLabel);
            
            // Process nested dependencies
            if (nestedDeps != null && !nestedDeps.isEmpty()) {
                logger.logMessage("\t==> The " + file + " has nested dependencies. Updating usage.");
                for (LogicalDependency dependency : nestedDeps) {
                    String dependentFile = includeFiles.keySet().stream()
                        .filter(f -> CopyToPDS.createMemberName(dependency.getLname()).toLowerCase().equals(f.toLowerCase()))
                        .findFirst()
                        .orElse(null);
                    
                    if (dependentFile != null) {
                        Map<String, String> depProps = includeFiles.get(dependentFile);
                        logger.logMessage("\t==> Updating usage of Include File '" + dependentFile + "' to '" + usageLabel + "'.");
                        appDescUtils.appendFileDefinition(applicationDescriptor, depProps.get("sourceGroupName"),
                            depProps.get("language"), depProps.get("languageProcessor"), depProps.get("artifactsType"),
                            depProps.get("fileExtension"), depProps.get("repositoryPath"), dependentFile,
                            depProps.get("type"), usageLabel);
                        processedFiles.add(dependentFile);
                    }
                }
            }
            
            // Update consumers for files referenced by multiple applications
            for (String consumerCollection : referencingCollections) {
                if (!consumerCollection.equals(props.getProperty("application"))) {
                    updateConsumerApplicationDescriptor(consumerCollection, "artifactrepository", applicationDescriptor);
                }
            }
            
            appDescUtils.writeApplicationDescriptor(updatedApplicationDescriptorFile, applicationDescriptor);
            
        } else {
            logger.logMessage("\tThe Include File '" + file + "' is not referenced at all.");
            appDescUtils.appendFileDefinition(applicationDescriptor, sourceGroupName, language,
                languageProcessor, artifactsType, fileExtension, repositoryPath, file, type, "unused");
            appDescUtils.writeApplicationDescriptor(updatedApplicationDescriptorFile, applicationDescriptor);
        }
    }
    
    private void assessImpactedFilesForPrograms(Map<String, Map<String, String>> programs) throws Exception {
        for (Map.Entry<String, Map<String, String>> entry : programs.entrySet()) {
            String file = entry.getKey();
            Map<String, String> properties = entry.getValue();
            
            String repositoryPath = properties.get("repositoryPath");
            String fileExtension = properties.get("fileExtension");
            String qualifiedFile = repositoryPath + "/" + file + "." + fileExtension;
            
            logger.logMessage("** Analyzing impacted applications for file '" + props.getProperty("application") + "/" + qualifiedFile + "'.");
            Map<ImpactFile, String> impactedFilesWithBuildGroup = findImpactedFilesWithBuildGroup(
                properties.get("impactSearchRule"),
                props.getProperty("application") + "/" + qualifiedFile
            );
            
            Set<String> referencingCollections = new HashSet<>();
            if (!impactedFilesWithBuildGroup.isEmpty()) {
                logger.logMessage("\tFiles depending on '" + repositoryPath + "/" + file + "." + fileExtension + "' :");
                for (Map.Entry<ImpactFile, String> impactEntry : impactedFilesWithBuildGroup.entrySet()) {
                    ImpactFile impactedFile = impactEntry.getKey();
                    String buildGroupName = impactEntry.getValue();
                    String referencingCollection = buildGroupName.replace("-" + props.getProperty("APPLICATION_DEFAULT_BRANCH"), "");
                    logger.logMessage("\t'" + impactedFile.getFile() + "' in Application '" + referencingCollection + "'");
                    referencingCollections.add(referencingCollection);
                }
            }
            
            assessProgramUsage(file, properties, referencingCollections);
        }
    }
    
    private void assessProgramUsage(String file, Map<String, String> properties, 
                                    Set<String> referencingCollections) throws Exception {
        
        String sourceGroupName = properties.get("sourceGroupName");
        String language = properties.get("language");
        String languageProcessor = properties.get("languageProcessor");
        String artifactsType = properties.get("artifactsType");
        String fileExtension = properties.get("fileExtension");
        String repositoryPath = properties.get("repositoryPath");
        String type = properties.get("type");
        
        if (referencingCollections.size() == 1) {
            String referencingApp = referencingCollections.iterator().next();
            logger.logMessage("\t==> '" + file + "' is statically called from the '" + referencingApp + "' application");
            
            if (props.getProperty("application").equals(referencingApp)) {
                appDescUtils.appendFileDefinition(applicationDescriptor, sourceGroupName, language,
                    languageProcessor, artifactsType, fileExtension, repositoryPath, file, type, "internal submodule");
                logger.logMessage("\t==> Updating usage of Program '" + file + "' to 'internal submodule'.");
            } else {
                appDescUtils.appendFileDefinition(applicationDescriptor, sourceGroupName, language,
                    languageProcessor, artifactsType, fileExtension, repositoryPath, file, type, "service submodule");
                logger.logMessage("\t==> Updating usage of Program '" + file + "' to 'service submodule'.");
                
                // Update the target Application Descriptor to add Dependency
                updateConsumerApplicationDescriptor(referencingApp, "artifactrepository", applicationDescriptor);
            }
            appDescUtils.writeApplicationDescriptor(updatedApplicationDescriptorFile, applicationDescriptor);
            
        } else if (referencingCollections.size() > 1) {
            logger.logMessage("\t==> '" + file + "' is statically called by multiple applications - " + referencingCollections);
            appDescUtils.appendFileDefinition(applicationDescriptor, sourceGroupName, language,
                languageProcessor, artifactsType, fileExtension, repositoryPath, file, type, "service submodule");
            logger.logMessage("\t==> Updating usage of Program '" + file + "' to 'service submodule'.");
            
            // Update consumers for programs referenced by multiple applications
            for (String consumerCollection : referencingCollections) {
                if (!consumerCollection.equals(props.getProperty("application"))) {
                    updateConsumerApplicationDescriptor(consumerCollection, "artifactrepository", applicationDescriptor);
                }
            }
            
            appDescUtils.writeApplicationDescriptor(updatedApplicationDescriptorFile, applicationDescriptor);
            
        } else {
            logger.logMessage("\tThe Program '" + file + "' is not statically called by any other program.");
            appDescUtils.appendFileDefinition(applicationDescriptor, sourceGroupName, language,
                languageProcessor, artifactsType, fileExtension, repositoryPath, file, type, "main");
            appDescUtils.writeApplicationDescriptor(updatedApplicationDescriptorFile, applicationDescriptor);
        }
    }
    
    private Map<ImpactFile, String> findImpactedFilesWithBuildGroup(String impactSearch, String file) throws BuildException, DependencyException, IOException {
        Map<ImpactFile, String> impactsWithBuildGroup = new HashMap<>();
        
        for (BuildGroup buildGroup : metadataStoreUtils.getBuildGroups()) {
            if ("dbb_default".equals(buildGroup.getName())) continue;
            
            List<String> collections = new ArrayList<>();
            for (com.ibm.dbb.metadata.Collection collection : buildGroup.getCollections()) {
                collections.add(collection.getName());
            }
            
            if (!collections.isEmpty()) {
                SearchPathImpactFinder finder = new SearchPathImpactFinder(
                    impactSearch, buildGroup.getName(), collections);
                Set<ImpactFile> impacts = finder.findImpactedFiles(file, props.getProperty("DBB_MODELER_APPLICATION_DIR"));
                if (impacts != null) {
                    for (ImpactFile impact : impacts) {
                        impactsWithBuildGroup.put(impact, buildGroup.getName());
                    }
                }
            }
        }
        
        return impactsWithBuildGroup;
    }
    
    private SortResult sortListByDependencyTree(List<String> files) throws BuildException {
        List<String> sortedFileList = new ArrayList<>();
        Map<String, List<LogicalDependency>> nestedDependencies = new HashMap<>();
        
        // Initialize dependency list
        @SuppressWarnings("unchecked")
        List<Integer>[] dependencyList = new List[files.size()];
        for (int i = 0; i < files.size(); i++) {
            dependencyList[i] = new ArrayList<>();
        }
        
        // Build dependency graph
        for (int i = 0; i < files.size(); i++) {
            String file = files.get(i);
            String buildGroup = props.getProperty("application") + "-" + props.getProperty("APPLICATION_DEFAULT_BRANCH");
            String collectionName = "sources";
            LogicalFile lFile = metadataStoreUtils.getLogicalFile(file, buildGroup, collectionName);
            
            if (lFile != null) {
                List<LogicalDependency> logicalDependencies = lFile.getLogicalDependencies();
                nestedDependencies.put(file, logicalDependencies);
                
                for (LogicalDependency logicalDependency : logicalDependencies) {
                    String dependentFile = files.stream()
                        .filter(f -> f.toLowerCase().contains(logicalDependency.getLname().toLowerCase()))
                        .findFirst()
                        .orElse(null);
                    
                    if (dependentFile != null) {
                        int dependentIndex = files.indexOf(dependentFile);
                        dependencyList[i].add(dependentIndex);
                    }
                }
            } else {
                logger.logMessage("*! [WARNING] File '" + file + "' was not found in DBB Metadatastore.");
            }
        }
        
        // Perform topological sort
        List<Integer> sortedIndices = topologicalSort(files.size(), dependencyList);
        
        // Map indices back to file names
        for (Integer index : sortedIndices) {
            sortedFileList.add(files.get(index));
        }
        
        return new SortResult(sortedFileList, nestedDependencies);
    }
    
    private List<Integer> topologicalSort(int entities, List<Integer>[] dependencyList) {
        Stack<Integer> stack = new Stack<>();
        boolean[] visited = new boolean[entities];
        
        for (int i = 0; i < entities; i++) {
            if (!visited[i]) {
                topologicalSortUtil(i, visited, stack, dependencyList);
            }
        }
        
        List<Integer> result = new ArrayList<>();
        while (!stack.isEmpty()) {
            result.add(stack.pop());
        }
        
        return result;
    }
    
    private void topologicalSortUtil(int v, boolean[] visited, Stack<Integer> stack, List<Integer>[] dependencyList) {
        visited[v] = true;
        
        for (Integer i : dependencyList[v]) {
            if (!visited[i]) {
                topologicalSortUtil(i, visited, stack, dependencyList);
            }
        }
        
        stack.push(v);
    }
    
    // Helper class for sort results
    private static class SortResult {
        List<String> sortedList;
        Map<String, List<LogicalDependency>> nestedDependencies;
        
        SortResult(List<String> sortedList, Map<String, List<LogicalDependency>> nestedDependencies) {
            this.sortedList = sortedList;
            this.nestedDependencies = nestedDependencies;
        }
    }
    
    /**
     * Update the Application Descriptor of consuming application.
     * Adds dependencies to consumer applications and consumers to provider applications.
     */
    private void updateConsumerApplicationDescriptor(String consumer, String dependencyType,
                                                    ApplicationDescriptor providerApplicationDescriptor) throws Exception {
        // Determine which YAML file to use
        File consumerApplicationDescriptorFile = new File(props.getProperty("DBB_MODELER_APPLICATION_DIR") +
            "/" + consumer + "/applicationDescriptor.yml");
        
        ApplicationDescriptor consumerApplicationDescriptor = null;
        
        if (consumerApplicationDescriptorFile.exists()) {
            // Update the Application Descriptor that already exists in the Application repository
            consumerApplicationDescriptor = appDescUtils.readApplicationDescriptor(consumerApplicationDescriptorFile);
        } else {
            // Start from the original Application Descriptor created by the extraction phase
            File originalConsumerApplicationDescriptorFile = new File(props.getProperty("DBB_MODELER_APPCONFIG_DIR") +
                "/" + consumer + ".yml");
            
            if (originalConsumerApplicationDescriptorFile.exists()) {
                FileUtility.copyFileWithTags(originalConsumerApplicationDescriptorFile, consumerApplicationDescriptorFile);
                consumerApplicationDescriptor = appDescUtils.readApplicationDescriptor(consumerApplicationDescriptorFile);
            } else {
                logger.logMessage("*! [WARNING] Application Descriptor file '" +
                    originalConsumerApplicationDescriptorFile.getPath() +
                    "' was not found. Skipping the configuration update for Application '" + consumer + "'.");
            }
        }
        
        // Consumer's Application Descriptor file has been found and can be updated
        if (consumerApplicationDescriptor != null) {
            // Fetch the internal baseline that is added
            ApplicationDescriptor.Baseline providerInternalBaseline = null;
            if (providerApplicationDescriptor.getBaselines() != null) {
                for (ApplicationDescriptor.Baseline baseline : providerApplicationDescriptor.getBaselines()) {
                    if (baseline.getBranch().equals(props.getProperty("APPLICATION_DEFAULT_BRANCH"))) {
                        providerInternalBaseline = baseline;
                        break;
                    }
                }
            }
            
            if (providerInternalBaseline != null) {
                appDescUtils.addApplicationDependency(consumerApplicationDescriptor,
                    providerApplicationDescriptor.getApplication(),
                    providerInternalBaseline.getReference(),
                    providerInternalBaseline.getBuildid());
                appDescUtils.writeApplicationDescriptor(consumerApplicationDescriptorFile, consumerApplicationDescriptor);
            }
        }
        
        // Update provider's Application Descriptor
        appDescUtils.addApplicationConsumer(providerApplicationDescriptor, consumer);
    }
    
    /**
     * Move a file from the current application to the owning application.
     * This includes:
     * - Updating target application descriptor
     * - Moving the physical file
     * - Updating source application descriptor
     * - Moving DBB metadata
     * - Updating mapping files
     */
    private void moveFileToOwningApplication(String file, Map<String, String> properties,
                                            String owningApplication) throws Exception {
        String sourceGroupName = properties.get("sourceGroupName");
        String language = properties.get("language");
        String languageProcessor = properties.get("languageProcessor");
        String artifactsType = properties.get("artifactsType");
        String fileExtension = properties.get("fileExtension");
        String repositoryPath = properties.get("repositoryPath");
        String type = properties.get("type");
        String qualifiedFile = repositoryPath + "/" + file + "." + fileExtension;
        
        // Load target application descriptor
        File originalTargetAppDescFile = new File(props.getProperty("DBB_MODELER_APPCONFIG_DIR") +
            "/" + owningApplication + ".yml");
        File updatedTargetAppDescFile = new File(props.getProperty("DBB_MODELER_APPLICATION_DIR") +
            "/" + owningApplication + "/applicationDescriptor.yml");
        
        ApplicationDescriptor targetApplicationDescriptor = null;
        
        if (updatedTargetAppDescFile.exists()) {
            targetApplicationDescriptor = appDescUtils.readApplicationDescriptor(updatedTargetAppDescFile);
        } else if (originalTargetAppDescFile.exists()) {
            FileUtility.copyFileWithTags(originalTargetAppDescFile, updatedTargetAppDescFile);
            targetApplicationDescriptor = appDescUtils.readApplicationDescriptor(updatedTargetAppDescFile);
        } else {
            logger.logMessage("*! [WARNING] Application Descriptor file '" +
                originalTargetAppDescFile.getPath() + "' was not found. Skipping the configuration update for Include File '" +
                file + "'.");
            return;
        }
        
        // Strip component prefix to get the base source group name (e.g. "plouf:copy" -> "copy")
        String currentSourceGroup;
        String[] sourceGroupParts = sourceGroupName.split(":");
        if (sourceGroupParts.length == 2) {
            currentSourceGroup = sourceGroupParts[1];
        } else {
            currentSourceGroup = sourceGroupName;
        }
        
        // Moved files always land at application level in the target, never inside a component folder.
        String targetSourceGroupName = currentSourceGroup;
        
        // Compute target repository path (always application-level, no component)
        String targetRepositoryPath = computeTargetRepositoryPath(sourceGroupName, "");
        
        logger.logMessage("\t==> Moving Include File '" + file + "' to '" + targetRepositoryPath +
            "' in Application '" + owningApplication + "'.");
        
        // Copy file to target application folder
        copyFileToApplicationFolder(props.getProperty("application") + "/" + qualifiedFile,
            owningApplication + "/" + targetRepositoryPath);
        
        // Add file to target application descriptor
        appDescUtils.appendFileDefinition(targetApplicationDescriptor, targetSourceGroupName, language,
            languageProcessor, artifactsType, fileExtension, targetRepositoryPath, file, type, "private");
        logger.logMessage("\t==> Adding Include File '" + file + "' with usage 'private' to Application '" +
            owningApplication + "' described in '" + updatedTargetAppDescFile.getPath() + "'.");
        appDescUtils.writeApplicationDescriptor(updatedTargetAppDescFile, targetApplicationDescriptor);
        
        // Remove file from source application descriptor
        logger.logMessage("\t==> Removing Include File '" + file + "' from Application '" +
            props.getProperty("application") + "' described in '" + updatedApplicationDescriptorFile.getPath() + "'.");
        appDescUtils.removeFileDefinition(applicationDescriptor, sourceGroupName, file);
        
        // Move logical file in DBB Metadatastore
        String sourceFilePath = qualifiedFile;
        String targetFilePath = targetRepositoryPath + "/" + file + "." + fileExtension;
        
        // Update application mappings
        updateMappingFiles(props.getProperty("DBB_MODELER_APPCONFIG_DIR"),
            props.getProperty("application"), sourceFilePath, owningApplication, targetFilePath);
        
        logger.logMessage("\t==> Moving DBB Metadata for '" + file + "' from buildGroup " +
            props.getProperty("application") + "-" + props.getProperty("APPLICATION_DEFAULT_BRANCH") +
            " to new buildgroup " + owningApplication + "-" + props.getProperty("APPLICATION_DEFAULT_BRANCH") + ".");
        
        metadataStoreUtils.moveLogicalFile(
            props.getProperty("DBB_MODELER_APPLICATION_DIR") + "/" + owningApplication,
            sourceFilePath,
            props.getProperty("application") + "-" + props.getProperty("APPLICATION_DEFAULT_BRANCH"),
            props.getProperty("application") + "-" + props.getProperty("APPLICATION_DEFAULT_BRANCH"),
            targetFilePath,
            owningApplication + "-" + props.getProperty("APPLICATION_DEFAULT_BRANCH"),
            owningApplication + "-" + props.getProperty("APPLICATION_DEFAULT_BRANCH"),
            Boolean.parseBoolean(props.getProperty("SCAN_CONTROL_TRANSFERS", "false"))
        );
    }
    
    /**
     * Compute the target repository path based on repository path mapping configuration.
     */
    private String computeTargetRepositoryPath(String sourceGroupName, String targetApplicationComponent) {
        // Strip component prefix (e.g. "COMP1:copy" -> "copy") before looking up in mapping
        String[] sourceGroupParts = sourceGroupName.split(":");
        String baseSourceGroup = sourceGroupParts.length == 2 ? sourceGroupParts[1] : sourceGroupName;

        // Find repository configuration for the source group
        RepositoryPathsMapping.RepositoryPath repoPathConfig = null;
        if (repositoryPathsMapping != null && repositoryPathsMapping.getRepositoryPaths() != null) {
            for (RepositoryPathsMapping.RepositoryPath repoMapping : repositoryPathsMapping.getRepositoryPaths()) {
                if (repoMapping.getSourceGroup().equals(baseSourceGroup)) {
                    repoPathConfig = repoMapping;
                    break;
                }
            }
        }
        
        if (repoPathConfig != null) {
            String targetPath = repoPathConfig.getRepositoryPath()
                .replaceAll("\\$component", targetApplicationComponent != null ? targetApplicationComponent : "")
                .replaceAll("^/", "");
            return targetPath;
        }
        
        return baseSourceGroup; // Fallback to base source group name
    }
    
    /**
     * Copy a file from source to target application folder.
     * Moves the file using Java NIO Files.move().
     */
    private void copyFileToApplicationFolder(String sourceFile, String targetRepositoryPath) throws IOException {
        Path source = Paths.get(props.getProperty("DBB_MODELER_APPLICATION_DIR"), sourceFile);
        String fileName = source.getFileName().toString();
        Path target = Paths.get(props.getProperty("DBB_MODELER_APPLICATION_DIR"),
            targetRepositoryPath, fileName);
        Path targetDir = target.getParent();
        
        // Create target directory if it doesn't exist
        File targetDirFile = targetDir.toFile();
        if (!targetDirFile.exists()) {
            targetDirFile.mkdirs();
        }
        
        // Move file if source exists and paths are different
        if (source.toFile().exists() && !source.toString().equals(target.toString())) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
    
    /**
     * Update migration mapping files when a file is moved between applications.
     * Updates both source and target application mapping files.
     */
    private void updateMappingFiles(String configurationsDirectory, String sourceApplication,
                                   String oldFileLocation, String targetApplication,
                                   String targetRepositoryPath) {
        File sourceApplicationMappingFile = new File(configurationsDirectory + "/" + sourceApplication + ".mapping");
        File targetApplicationMappingFile = new File(configurationsDirectory + "/" + targetApplication + ".mapping");
        
        if (!sourceApplicationMappingFile.exists()) {
            logger.logMessage("*! [ERROR] Couldn't find the mapping file '" + sourceApplication + ".mapping'");
            return;
        }
        
        if (!targetApplicationMappingFile.exists()) {
            logger.logMessage("*! [ERROR] Couldn't find the mapping file '" + targetApplication + ".mapping'");
            return;
        }
        
        try {
            logger.logMessage("\t==> Updating Migration Mapping files for Applications '" +
                sourceApplication + "' and '" + targetApplication + "' for file '" + oldFileLocation + "'.");
            File newSourceApplicationMappingFile = new File(configurationsDirectory + "/" +
                sourceApplication + ".mapping.new");
            newSourceApplicationMappingFile.createNewFile();
            boolean foundMatch = false;
            
            try (BufferedReader sourceReader = new BufferedReader(new FileReader(sourceApplicationMappingFile));
                 BufferedWriter targetWriter = new BufferedWriter(new FileWriter(targetApplicationMappingFile, true));
                 BufferedWriter newSourceWriter = new BufferedWriter(new FileWriter(newSourceApplicationMappingFile))) {
                
                String line;
                while ((line = sourceReader.readLine()) != null) {
                    String[] lineSegments = line.split(" ");
                    String fullOldPath = props.getProperty("DBB_MODELER_APPLICATION_DIR") + "/" +
                        sourceApplication + "/" + oldFileLocation;
                    
                    if (lineSegments.length > 1 && lineSegments[1].equals(fullOldPath)) {
                        // Update path and write to target mapping file
                        lineSegments[1] = props.getProperty("DBB_MODELER_APPLICATION_DIR") + "/" +
                            targetApplication + "/" + targetRepositoryPath;
                        String updatedLine = String.join(" ", lineSegments);
                        targetWriter.write(updatedLine + "\n");
                        foundMatch = true;
                    } else {
                        // Keep in source mapping file
                        newSourceWriter.write(line + "\n");
                    }
                }
            }
            
            // Replace old source mapping file with new one
            sourceApplicationMappingFile.delete();
            Files.move(newSourceApplicationMappingFile.toPath(), sourceApplicationMappingFile.toPath());
            
            if (foundMatch) {
                logger.logMessage("\t==> Moved mapping definition for file " + sourceApplication + "/" +
                    oldFileLocation + " from " + configurationsDirectory + "/" + sourceApplication +
                    ".mapping to " + targetApplication + "/" + targetRepositoryPath + " in " +
                    configurationsDirectory + "/" + targetApplication + ".mapping.");
            } else {
                logger.logMessage("\t==> [WARNING] Update of DBB Migration Mapping files for " +
                    sourceApplication + "/" + oldFileLocation + " failed. " + sourceApplication + "/" +
                    oldFileLocation + " was not found in " + configurationsDirectory + "/" +
                    sourceApplication + ".mapping. The DBB Migration Mapping files " +
                    configurationsDirectory + "/" + sourceApplication + ".mapping and " +
                    configurationsDirectory + "/" + targetApplication + ".mapping were not updated.");
            }
                
        } catch (IOException e) {
            logger.logMessage("*! [ERROR] Failed to update mapping files: " + e.getMessage());
            e.printStackTrace();
        }
    }
}