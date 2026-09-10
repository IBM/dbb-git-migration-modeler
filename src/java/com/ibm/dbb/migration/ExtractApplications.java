/********************************************************************************
 * Licensed Materials - Property of IBM                                          *
 * (c) Copyright IBM Corporation 2018, 2024. All Rights Reserved.                *
 *                                                                               *
 * Note to U.S. Government Users Restricted Rights:                              *
 * Use, duplication or disclosure restricted by GSA ADP Schedule                 *
 * Contract with IBM Corp.                                                       *
 ********************************************************************************/

package com.ibm.dbb.migration;

import com.ibm.dbb.migration.model.ApplicationMappingConfiguration;
import com.ibm.dbb.migration.model.ApplicationDescriptor;
import com.ibm.dbb.migration.model.RepositoryPathsMapping;
import com.ibm.dbb.migration.model.TypesMapping;
import com.ibm.dbb.migration.utils.Logger;
import com.ibm.dbb.migration.utils.ApplicationDescriptorUtils;
import com.ibm.dbb.migration.utils.ConfigurationUtility;
import com.ibm.dmh.scan.classify.Dmh5210;
import com.ibm.dmh.scan.classify.ScanProperties;
import com.ibm.dmh.scan.classify.SingleFilesMetadata;
import com.ibm.jzos.PdsDirectory;
import com.ibm.jzos.ZFile;
import com.ibm.jzos.ZFileConstants;
import org.apache.commons.cli.*;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.text.DecimalFormat;
import java.util.*;

/**
 * Standalone Java application to extract applications from mainframe datasets
 * and generate application configuration files for DBB Git Migration Modeler.
 * 
 * This is a Java conversion of the original extractApplications.groovy script.
 */
public class ExtractApplications {

    private static final String UNASSIGNED_APPLICATION = "UNASSIGNED";
    
    private Properties props = new Properties();
    private ApplicationMappingConfiguration unassignedApplicationMappingConfiguration;
    private Map<ApplicationMappingConfiguration, Set<String>> applicationsToDatasetMembersMap = new HashMap<>();
    private Map<String, List<ApplicationMappingConfiguration>> datasetsMap = new HashMap<>();
    private Set<String> filteredApplications = new HashSet<>();
    private RepositoryPathsMapping repositoryPathsMapping;
    private TypesMapping typesMapping;
    private Dmh5210 scanner;
    private Map<String, Long> storageRequirements = new HashMap<>();
    private Logger logger = new Logger();
    private ApplicationDescriptorUtils applicationDescriptorUtils = new ApplicationDescriptorUtils();

    public static void main(String[] args) {
        ExtractApplications extractor = new ExtractApplications();
        try {
            extractor.execute(args);
        } catch (Exception e) {
            System.err.println("Error executing extraction: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void execute(String[] args) throws Exception {
        logger.logMessage("** Extraction process started.");
        
        unassignedApplicationMappingConfiguration = new ApplicationMappingConfiguration();
        unassignedApplicationMappingConfiguration.setApplication(UNASSIGNED_APPLICATION);
        
        parseArgs(args);
        
        if (props.getProperty("applications") != null) {
            String[] applications = props.getProperty("applications").split(",");
            for (String app : applications) {
                filteredApplications.add(app.trim());
            }
        }
        
        logger.logMessage("** Reading the Repository Layout Mapping definition.");
        loadRepositoryPathsMapping();
        
        logger.logMessage("** Reading the Type Mapping definition.");
        loadTypesMapping();
        
        logger.logMessage("** Loading the provided Applications Mapping files.");
        loadApplicationMappings();
        
        logger.logMessage("** Iterating through the provided datasets and mapped applications.");
        processDatasets();
        
        logger.logMessage("** Generating Applications Configurations files.");
        generateApplicationFiles();
        
        displayStorageRequirements();
        
        logger.close();
    }

    private void parseArgs(String[] args) throws Exception {
        // Create Options object
        Options options = new Options();
        
        // Define command-line options
        Option configFileOption = Option.builder("c")
                .longOpt("configFile")
                .hasArg()
                .argName("path")
                .desc("Path to the DBB Git Migration Modeler Configuration file (created by the Setup script)")
                .required(true)
                .build();
        
        Option applicationsOption = Option.builder("a")
                .longOpt("applications")
                .hasArg()
                .argName("list")
                .desc("Comma-separated list of applications to extract. If not specified, all applications will be extracted")
                .required(false)
                .build();
        
        Option logFileOption = Option.builder("l")
                .longOpt("logFile")
                .hasArg()
                .argName("path")
                .desc("Relative or absolute path to an output log file")
                .required(false)
                .build();
        
        options.addOption(configFileOption);
        options.addOption(applicationsOption);
        options.addOption(logFileOption);
        
        // Create parser
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;
        
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.err.println("Error parsing command-line arguments: " + e.getMessage());
            System.err.println();
            formatter.printHelp("ExtractApplications [options]", "options:", options, "", true);
            System.exit(1);
            return;
        }
        
        // Process log file option first (if specified)
        if (cmd.hasOption("l")) {
            String logFile = cmd.getOptionValue("l");
            props.setProperty("logFile", logFile);
            logger.create(logFile);
        }
        
        // Process applications option
        if (cmd.hasOption("a")) {
            props.setProperty("applications", cmd.getOptionValue("a"));
        }
        
        // Process config file option (required)
        if (cmd.hasOption("c")) {
            loadConfiguration(cmd.getOptionValue("c"));
        }
        
        // Log configuration
        logger.logMessage("** Script configuration:");
        for (Object key : props.keySet()) {
            logger.logMessage("\t" + key + " -> " + props.get(key));
        }
    }

    private void loadConfiguration(String configFilePath) throws Exception {
        props.setProperty("configurationFilePath", configFilePath);
        
        // Validate and load configuration using ValidateConfiguration
        Properties configProperties = ValidateConfiguration.validateAndLoadConfiguration(configFilePath);
        
        // Load only the properties used by extractApplications (matching Groovy script)
        ConfigurationUtility.loadRequiredProperty(configProperties, props, "DBB_MODELER_APPCONFIG_DIR", "Configurations directory");
        ConfigurationUtility.loadRequiredProperty(configProperties, props, "DBB_MODELER_APPMAPPINGS_DIR", "Applications Mappings directory");
        ConfigurationUtility.loadRequiredProperty(configProperties, props, "DBB_MODELER_APPLICATION_DIR", "Applications directory");
        ConfigurationUtility.loadRequiredProperty(configProperties, props, "REPOSITORY_PATH_MAPPING_FILE", "Repository Paths Mapping file");
        
        // Optional properties
        ConfigurationUtility.loadOptionalProperty(configProperties, props, "APPLICATION_TYPES_MAPPING");
        
        // SCAN_DATASET_MEMBERS with default
        ConfigurationUtility.loadOptionalProperty(configProperties, props, "SCAN_DATASET_MEMBERS", "false");
        
        // SCAN_DATASET_MEMBERS_ENCODING with conditional default
        if (props.getProperty("SCAN_DATASET_MEMBERS") != null) {
            ConfigurationUtility.loadOptionalProperty(configProperties, props, "SCAN_DATASET_MEMBERS_ENCODING", "IBM-1047");
        }
        
        // APPLICATION_DEFAULT_BRANCH is required
        String defaultBranch = configProperties.getProperty("APPLICATION_DEFAULT_BRANCH");
        if (defaultBranch == null || defaultBranch.trim().isEmpty()) {
            throw new IllegalArgumentException("APPLICATION_DEFAULT_BRANCH must be specified in configuration file");
        }
        props.setProperty("APPLICATION_DEFAULT_BRANCH", defaultBranch);
    }


    private void loadRepositoryPathsMapping() throws Exception {
        String mappingFile = props.getProperty("REPOSITORY_PATH_MAPPING_FILE");
        File file = new File(mappingFile);
        
        Yaml yaml = new Yaml();
        try (FileReader reader = new FileReader(file)) {
            repositoryPathsMapping = yaml.loadAs(reader, RepositoryPathsMapping.class);
        }
    }

    private void loadTypesMapping() throws Exception {
        String typesFile = props.getProperty("APPLICATION_TYPES_MAPPING");
        if (typesFile == null) {
            logger.logMessage("*! [WARNING] No Types Mapping file provided. The 'UNKNOWN' type will be assigned by default.");
            return;
        }
        
        File file = new File(typesFile);
        if (!file.exists()) {
            logger.logMessage("*! [WARNING] Types Mapping file not found: " + typesFile);
            return;
        }
        
        Yaml yaml = new Yaml();
        try (FileReader reader = new FileReader(file)) {
            Map<String, Object> data = yaml.load(reader);
            typesMapping = new TypesMapping();
            typesMapping.setDatasetMembers((List<Map<String, String>>) data.get("datasetMembers"));
        }
    }

    private void loadApplicationMappings() throws Exception {
        String mappingsDir = props.getProperty("DBB_MODELER_APPMAPPINGS_DIR");
        File dir = new File(mappingsDir);
        
        File[] files = dir.listFiles((d, name) -> name.endsWith(".yaml") || name.endsWith(".yml"));
        if (files == null) return;
        
        Yaml yaml = new Yaml();
        for (File mappingFile : files) {
            logger.logMessage("*** Importing '" + mappingFile.getName() + "'");
            
            try (FileReader reader = new FileReader(mappingFile)) {
                Map<String, Object> applicationsMapping = yaml.load(reader);
                
                @SuppressWarnings("unchecked")
                List<String> datasets = (List<String>) applicationsMapping.get("datasets");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> applications = (List<Map<String, Object>>) applicationsMapping.get("applications");
                
                for (String dataset : datasets) {
                    List<ApplicationMappingConfiguration> applicationsList = datasetsMap.get(dataset);
                    if (applicationsList == null) {
                        applicationsList = new ArrayList<>();
                        datasetsMap.put(dataset, applicationsList);
                    }
                    
                    for (Map<String, Object> appConfig : applications) {
                        String appName = (String) appConfig.get("application");
                        if (filteredApplications.isEmpty() || filteredApplications.contains(appName)) {
                            ApplicationMappingConfiguration config = new ApplicationMappingConfiguration();
                            config.setApplication(appName);
                            config.setComponent((String) appConfig.get("component"));
                            config.setDescription((String) appConfig.get("description"));
                            config.setOwner((String) appConfig.get("owner"));
                            config.setBaseline((String) appConfig.get("baseline"));
                            
                            @SuppressWarnings("unchecked")
                            List<String> namingConventions = (List<String>) appConfig.get("namingConventions");
                            config.setNamingConventions(namingConventions != null ? namingConventions : new ArrayList<>());
                            
                            applicationsList.add(config);
                        }
                    }
                }
            }
        }
    }

    private void processDatasets() throws Exception {
        for (Map.Entry<String, List<ApplicationMappingConfiguration>> entry : datasetsMap.entrySet()) {
            String dataset = entry.getKey();
            List<ApplicationMappingConfiguration> applicationConfigurations = entry.getValue();
            
            String qdsn = constructPDSForZFileOperation(dataset);
            if (ZFile.dsExists(qdsn)) {
                List<String> applications = new ArrayList<>();
                for (ApplicationMappingConfiguration config : applicationConfigurations) {
                    String appName = config.getComponent() != null ? 
                        "'" + config.getApplication() + ":" + config.getComponent() + "'" : 
                        "'" + config.getApplication() + "'";
                    applications.add(appName);
                }
                
                logger.logMessage("**** Found '" + dataset + "' referenced by applications " + 
                    String.join(", ", applications));
                
                try {
                    PdsDirectory directoryList = new PdsDirectory(qdsn);
                    Iterator<?> directoryListIterator = directoryList.iterator();
                    
                    while (directoryListIterator.hasNext()) {
                        PdsDirectory.MemberInfo memberInfo = (PdsDirectory.MemberInfo) directoryListIterator.next();
                        String member = memberInfo.getName();
                        
                        ApplicationMappingConfiguration mappedApplicationConfiguration = 
                            findMappedApplicationFromMemberName(applicationConfigurations, member);
                        
                        String msg = "***** '" + dataset + "(" + member + ")' - Mapped Application: " + 
                            mappedApplicationConfiguration.getApplication();
                        if (mappedApplicationConfiguration.getComponent() != null) {
                            msg += ":" + mappedApplicationConfiguration.getComponent();
                        }
                        logger.logMessage(msg);
                        
                        addDatasetMemberToApplication(mappedApplicationConfiguration, dataset + "(" + member + ")");
                    }
                    directoryList.close();
                } catch (IOException exception) {
                    logger.logMessage("*! [ERROR] Problem when accessing the dataset '" + qdsn + "'.");
                }
            } else {
                logger.logMessage("*! [ERROR] Dataset '" + qdsn + "' does not exist.");
            }
        }
    }

    private void generateApplicationFiles() throws Exception {
        DecimalFormat df = new DecimalFormat("###,###,###,###");
        
        for (Map.Entry<ApplicationMappingConfiguration, Set<String>> entry : applicationsToDatasetMembersMap.entrySet()) {
            ApplicationMappingConfiguration applicationConfiguration = entry.getKey();
            Set<String> members = entry.getValue();
            
            String msg = applicationConfiguration.getComponent() != null ? 
                applicationConfiguration.getApplication() + " Component: " + applicationConfiguration.getComponent() : 
                applicationConfiguration.getApplication();
            
            logger.logMessage("** Generating Configuration files for Application: " + msg);
            generateApplicationFilesForConfig(applicationConfiguration);
            
            String applicationConfName = applicationConfiguration.getComponent() != null ? 
                applicationConfiguration.getApplication() + ":" + applicationConfiguration.getComponent() : 
                applicationConfiguration.getApplication();
            
            long storageSize = calculateStorageSizeForMembers(members);
            storageRequirements.put(applicationConfName, storageSize);
            logger.logMessage("\tEstimated storage size of migrated members: " + df.format(storageSize) + " bytes");
        }
    }

    private void displayStorageRequirements() {
        DecimalFormat df = new DecimalFormat("###,###,###,###");
        long globalStorageRequirements = 0;
        
        for (Long storageRequirement : storageRequirements.values()) {
            globalStorageRequirements += storageRequirement;
        }
        
        logger.logMessage("** Estimated storage size of all migrated members: " + 
            df.format(globalStorageRequirements) + " bytes");
    }

    private void generateApplicationFilesForConfig(ApplicationMappingConfiguration applicationConfiguration) throws Exception {
        String application = applicationConfiguration.getApplication();
        String component = applicationConfiguration.getComponent();
        
        // Load existing mapping file if it exists
        File mappingFile = new File(props.getProperty("DBB_MODELER_APPCONFIG_DIR") + "/" + application + ".mapping");
        Map<String, String> mappings = new HashMap<>();
        
        if (mappingFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(mappingFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] lineSegments = line.split(" ", 2);
                    if (lineSegments.length >= 2) {
                        mappings.put(lineSegments[0], lineSegments[1]);
                    }
                }
            }
        }
        
        // Load or create application descriptor
        File applicationDescriptorFile = new File(props.getProperty("DBB_MODELER_APPLICATION_DIR") + 
            "/" + application + "/applicationDescriptor.yml");
        File applicationDir = new File(props.getProperty("DBB_MODELER_APPLICATION_DIR") + "/" + application);
        
        if (!applicationDir.exists()) {
            applicationDir.mkdirs();
        }
        
        ApplicationDescriptor applicationDescriptor;
        if (applicationDescriptorFile.exists()) {
            applicationDescriptor = applicationDescriptorUtils.readApplicationDescriptor(applicationDescriptorFile);
        } else {
            applicationDescriptor = applicationDescriptorUtils.createEmptyApplicationDescriptor();
        }
        
        // Set application information
        if (!UNASSIGNED_APPLICATION.equals(applicationConfiguration.getApplication())) {
            applicationDescriptor.setApplication(applicationConfiguration.getApplication());
            applicationDescriptor.setDescription(applicationConfiguration.getDescription());
            applicationDescriptor.setOwner(applicationConfiguration.getOwner());
            
            String defaultBranch = props.getProperty("APPLICATION_DEFAULT_BRANCH");
            applicationDescriptorUtils.addBaseline(applicationDescriptor, defaultBranch, "release", 
                applicationConfiguration.getBaseline());
            applicationDescriptorUtils.addBaseline(applicationDescriptor, "release/" + 
                applicationConfiguration.getBaseline(), "release", applicationConfiguration.getBaseline());
        } else {
            applicationDescriptor.setApplication(UNASSIGNED_APPLICATION);
            applicationDescriptor.setDescription("Unassigned components");
            applicationDescriptor.setOwner("None");
            
            String defaultBranch = props.getProperty("APPLICATION_DEFAULT_BRANCH");
            applicationDescriptorUtils.addBaseline(applicationDescriptor, defaultBranch, "release", "rel-1.0.0");
            applicationDescriptorUtils.addBaseline(applicationDescriptor, "release/rel-1.0.0", "release", "rel-1.0.0");
        }
        
        // Process dataset members
        Set<String> datasetMembersCollection = applicationsToDatasetMembersMap.get(applicationConfiguration);
        for (String datasetMember : datasetMembersCollection) {
            String[] parts = getDatasetAndMember(datasetMember);
            String dataset = parts[0];
            String member = parts[1];
            
            // Scan dataset member if enabled
            String scannedLanguage = null, scannedFileType = null;
            if (Boolean.parseBoolean(props.getProperty("SCAN_DATASET_MEMBERS"))) {
                String[] scanResult = scanDatasetMember(constructDatasetForZFileOperation(dataset, member));
                scannedLanguage = scanResult[0];
                scannedFileType = scanResult[1];
            }
            
            String lastQualifier = getLastQualifier(dataset);
            String memberType = getTypeForDatasetMember(datasetMember);
            
            // Find matching repository path
            RepositoryPathsMapping.RepositoryPath matchingRepositoryPath = findMatchingRepositoryPath(
                scannedLanguage, scannedFileType, memberType, lastQualifier);
            
            String targetRepositoryPath, pdsEncoding, fileExtension, artifactsType, sourceGroup, language, languageProcessor;
            
            if (matchingRepositoryPath != null) {
                if (matchingRepositoryPath.isLowercase()) {
                    member = member.toLowerCase();
                    lastQualifier = lastQualifier.toLowerCase();
                }
                
                fileExtension = matchingRepositoryPath.getFileExtension() != null ? 
                    matchingRepositoryPath.getFileExtension() : lastQualifier;
                sourceGroup = matchingRepositoryPath.getSourceGroup() != null ? 
                    matchingRepositoryPath.getSourceGroup() : lastQualifier;
                
                if (component != null && !component.isEmpty()) {
                    sourceGroup = component + ":" + sourceGroup;
                }
                
                language = matchingRepositoryPath.getLanguage() != null ? 
                    matchingRepositoryPath.getLanguage() : lastQualifier;
                languageProcessor = matchingRepositoryPath.getLanguageProcessor() != null ? 
                    matchingRepositoryPath.getLanguageProcessor() : lastQualifier;
                
                targetRepositoryPath = matchingRepositoryPath.getRepositoryPath() != null ?
                    matchingRepositoryPath.getRepositoryPath()
                        .replaceAll("\\$component", component != null ? component : "")
                        .replaceAll("^/", "") :
                    lastQualifier;
                
                pdsEncoding = matchingRepositoryPath.getEncoding() != null ? 
                    matchingRepositoryPath.getEncoding() : "IBM-1047";
                artifactsType = matchingRepositoryPath.getArtifactsType() != null ? 
                    matchingRepositoryPath.getArtifactsType() : lastQualifier;
            } else {
                member = member.toLowerCase();
                lastQualifier = lastQualifier.toLowerCase();
                fileExtension = lastQualifier;
                sourceGroup = lastQualifier;
                language = lastQualifier;
                languageProcessor = lastQualifier;
                targetRepositoryPath = "src/" + lastQualifier;
                pdsEncoding = "IBM-1047";
                artifactsType = lastQualifier;
            }
            
            applicationDescriptorUtils.appendFileDefinition(applicationDescriptor, sourceGroup, language, 
                languageProcessor, artifactsType, fileExtension, targetRepositoryPath, member, memberType, "undefined");
            
            String fullTargetPath = props.getProperty("DBB_MODELER_APPLICATION_DIR") + "/" + application + 
                "/" + targetRepositoryPath;
            mappings.put(datasetMember, fullTargetPath + "/" + member + "." + fileExtension + 
                " pdsEncoding=" + pdsEncoding);
        }
        
        // Write mapping file
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(mappingFile), "IBM-1047"))) {
            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                writer.write(entry.getKey() + " " + entry.getValue() + "\n");
            }
        }
        
        // Set file tag to IBM-1047 (z/OS specific)
        try {
            com.ibm.dbb.utils.FileUtils.setFileTag(mappingFile.getAbsolutePath(), "IBM-1047");
        } catch (Exception e) {
            // Ignore file tagging errors on non-z/OS systems
        }
        
        logger.logMessage("\tCreated DBB Migration Utility mapping file " + mappingFile.getAbsolutePath());
        
        // Write application descriptor
        applicationDescriptorUtils.writeApplicationDescriptor(applicationDescriptorFile, applicationDescriptor);
        logger.logMessage("\tCreated/Updated Application Description file " + applicationDescriptorFile.getAbsolutePath());
    }

    private RepositoryPathsMapping.RepositoryPath findMatchingRepositoryPath(
            String scannedLanguage, String scannedFileType, String memberType, String lastQualifier) {
        
        if (repositoryPathsMapping == null || repositoryPathsMapping.getRepositoryPaths() == null) {
            return null;
        }
        
        for (RepositoryPathsMapping.RepositoryPath repositoryPath : repositoryPathsMapping.getRepositoryPaths()) {
            // Check scan result match
            if (scannedLanguage != null && scannedFileType != null && 
                repositoryPath.getMvsMapping() != null && 
                repositoryPath.getMvsMapping().getScan() != null) {
                if (scannedLanguage.equals(repositoryPath.getMvsMapping().getScan().getLanguage()) && 
                    scannedFileType.equals(repositoryPath.getMvsMapping().getScan().getFileType())) {
                    return repositoryPath;
                }
            }
            
            // Check type match
            if (repositoryPath.getMvsMapping() != null && 
                repositoryPath.getMvsMapping().getTypes() != null && 
                repositoryPath.getMvsMapping().getTypes().contains(memberType)) {
                return repositoryPath;
            }
            
            // Check last qualifier match
            if (repositoryPath.getMvsMapping() != null && 
                repositoryPath.getMvsMapping().getDatasetLastLevelQualifiers() != null && 
                repositoryPath.getMvsMapping().getDatasetLastLevelQualifiers().contains(lastQualifier)) {
                return repositoryPath;
            }
        }
        
        return null;
    }

    private void addDatasetMemberToApplication(ApplicationMappingConfiguration tmpApplicationMappingConfiguration, 
            String datasetMember) {
        
        ApplicationMappingConfiguration applicationMappingConfiguration = null;
        for (ApplicationMappingConfiguration config : applicationsToDatasetMembersMap.keySet()) {
            if (config.getApplication().equals(tmpApplicationMappingConfiguration.getApplication()) && 
                Objects.equals(config.getComponent(), tmpApplicationMappingConfiguration.getComponent())) {
                applicationMappingConfiguration = config;
                break;
            }
        }
        
        if (applicationMappingConfiguration == null) {
            applicationMappingConfiguration = tmpApplicationMappingConfiguration;
        }
        
        Set<String> applicationDatasetMembersMap = applicationsToDatasetMembersMap.get(applicationMappingConfiguration);
        if (applicationDatasetMembersMap == null) {
            applicationDatasetMembersMap = new HashSet<>();
            applicationsToDatasetMembersMap.put(applicationMappingConfiguration, applicationDatasetMembersMap);
        }
        
        applicationDatasetMembersMap.add(datasetMember);
    }

    private ApplicationMappingConfiguration findMappedApplicationFromMemberName(
            List<ApplicationMappingConfiguration> applicationConfigurationList, String memberName) {
        
        List<ApplicationMappingConfiguration> foundApplicationConfigurations = new ArrayList<>();
        
        for (ApplicationMappingConfiguration config : applicationConfigurationList) {
            for (String namingConvention : config.getNamingConventions()) {
                if (matches(memberName, namingConvention)) {
                    foundApplicationConfigurations.add(config);
                    break;
                }
            }
        }
        
        if (foundApplicationConfigurations.size() == 1) {
            return foundApplicationConfigurations.get(0);
        } else if (foundApplicationConfigurations.size() > 1) {
            logger.logMessage("*! [WARNING] Multiple applications claim ownership of member '" + memberName + "':");
            for (ApplicationMappingConfiguration config : foundApplicationConfigurations) {
                logger.logMessage("\t\tClaiming ownership: '" + config.getApplication() + ":" + 
                    config.getComponent() + "'");
            }
            logger.logMessage("*! [WARNING] The owner cannot be defined. Map '" + memberName + "' to UNASSIGNED");
            return unassignedApplicationMappingConfiguration;
        } else {
            return unassignedApplicationMappingConfiguration;
        }
    }

    private boolean matches(String memberName, String filePattern) {
        if (!filePattern.startsWith("glob:") && !filePattern.startsWith("regex:")) {
            filePattern = "glob:" + filePattern;
        }
        
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher(filePattern.toUpperCase());
        Path path = FileSystems.getDefault().getPath(memberName.toUpperCase());
        return matcher.matches(path);
    }

    private String[] getDatasetAndMember(String fullname) {
        String[] elements = fullname.split("[\\(\\)]");
        String ds = elements[0];
        String member = elements.length > 1 ? elements[1] : "";
        return new String[]{ds, member};
    }

    private String getLastQualifier(String dataset) {
        String[] qualifiers = dataset.split("\\.");
        return qualifiers[qualifiers.length - 1];
    }

    private String[] scanDatasetMember(String datasetMemberToScan) throws Exception {
        ZFile zFile = new ZFile(datasetMemberToScan, "r", ZFileConstants.FLAG_DISP_SHR);
        InputStream zFileInputStream = zFile.getInputStream();
        
        if (scanner == null) {
            scanner = initializeScanner();
        }
        
        Object scanMetadata = scanner.processSingleFile(zFileInputStream);
        SingleFilesMetadata dmhfile = (SingleFilesMetadata) scanMetadata;
        zFile.close();
        
        return new String[]{dmhfile.getLanguageCd(), dmhfile.getFileTypeCd()};
    }

    private Dmh5210 initializeScanner() {
        ScanProperties scanProperties = new ScanProperties();
        scanProperties.setCodePage(props.getProperty("SCAN_DATASET_MEMBERS_ENCODING"));
        Dmh5210 dmh5210 = new Dmh5210();
        dmh5210.init(scanProperties);
        return dmh5210;
    }

    private long calculateStorageSizeForMembers(Set<String> datasetMembers) {
        long storageSize = 0;
        for (String datasetMember : datasetMembers) {
            storageSize += estimateDatasetMemberSize(datasetMember);
        }
        return storageSize;
    }

    private long estimateDatasetMemberSize(String datasetMember) {
        try {
            ZFile file = new ZFile(constructPDSForZFileOperation(datasetMember), "r");
            InputStreamReader streamReader = new InputStreamReader(file.getInputStream());
            long storageSize = 0;
            long bytesSkipped = -1;
            
            while (bytesSkipped != 0) {
                bytesSkipped = streamReader.skip(Long.MAX_VALUE);
                storageSize += bytesSkipped;
            }
            
            file.close();
            return storageSize;
        } catch (IOException exception) {
            logger.logMessage("*! [WARNING] Unable to retrieve the estimated storage size for '" + datasetMember + "'");
            return 0;
        }
    }

    private String getTypeForDatasetMember(String datasetMember) {
        if (typesMapping == null || typesMapping.getDatasetMembers() == null) {
            return "UNKNOWN";
        }
        
        for (Map<String, String> typeMapping : typesMapping.getDatasetMembers()) {
            if (datasetMember.equalsIgnoreCase(typeMapping.get("datasetMember"))) {
                return typeMapping.get("type");
            }
        }
        
        return "UNKNOWN";
    }

    private String constructPDSForZFileOperation(String PDS) {
        return "//'" + PDS + "'";
    }

    private String constructDatasetForZFileOperation(String PDS, String member) {
        return "//'" + PDS + "(" + member + ")'";
    }
}
