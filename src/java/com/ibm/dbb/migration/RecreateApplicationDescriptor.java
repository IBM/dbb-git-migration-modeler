/********************************************************************************
 * Licensed Materials - Property of IBM                                          *
 * (c) Copyright IBM Corporation 2018, 2025. All Rights Reserved.                *
 *                                                                               *
 * Note to U.S. Government Users Restricted Rights:                              *
 * Use, duplication or disclosure restricted by GSA ADP Schedule                 *
 * Contract with IBM Corp.                                                       *
 ********************************************************************************/

package com.ibm.dbb.migration;

import com.ibm.dbb.migration.model.ApplicationDescriptor;
import com.ibm.dbb.migration.model.RepositoryPathsMapping;
import com.ibm.dbb.migration.model.RepositoryPathsMapping.RepositoryPath;
import com.ibm.dbb.migration.utils.ApplicationDescriptorUtils;
import com.ibm.dbb.migration.utils.ConfigurationUtility;
import com.ibm.dbb.migration.utils.Logger;
import org.apache.commons.cli.*;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Recreates Application Descriptor files for existing applications.
 *
 * This class resets the source groups and dependency/consumer information of an
 * existing Application Descriptor, then re-populates it by walking the files
 * present in the application's working directory and matching them against the
 * Repository Paths Mapping configuration.  If no Application Descriptor exists
 * yet, a new one is created.
 *
 * Equivalent to the former recreateApplicationDescriptor.groovy script.
 */
public class RecreateApplicationDescriptor {

    private Properties props;
    private Logger logger;
    private ApplicationDescriptorUtils appDescUtils;
    private RepositoryPathsMapping repositoryPathsMapping;

    public static void main(String[] args) {
        RecreateApplicationDescriptor recreator = new RecreateApplicationDescriptor();
        try {
            recreator.run(args);
        } catch (Exception e) {
            System.err.println("[ERROR] Recreate Application Descriptor failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void run(String[] args) throws Exception {
        props = new Properties();
        logger = new Logger();
        appDescUtils = new ApplicationDescriptorUtils();

        parseArgs(args);
        loadRepositoryPathsMapping();
        recreateDescriptor();

        logger.close();
    }

    // -------------------------------------------------------------------------
    // Argument parsing
    // -------------------------------------------------------------------------

    private void parseArgs(String[] args) throws Exception {
        Options options = new Options();
        options.addOption(Option.builder("a")
                .longOpt("application")
                .hasArg().required()
                .desc("Application name")
                .build());
        options.addOption(Option.builder("c")
                .longOpt("configFile")
                .hasArg().required()
                .desc("Path to the DBB Git Migration Modeler Configuration file")
                .build());
        options.addOption(Option.builder("l")
                .longOpt("logFile")
                .hasArg()
                .desc("Relative or absolute path to an output log file")
                .build());
        options.addOption(Option.builder("h")
                .longOpt("help")
                .desc("Display this help message")
                .build());

        CommandLine cmd;
        try {
            cmd = new DefaultParser().parse(options, args);
        } catch (ParseException e) {
            System.err.println("[ERROR] " + e.getMessage());
            new HelpFormatter().printHelp("RecreateApplicationDescriptor [options]", options);
            System.exit(1);
            return;
        }

        if (cmd.hasOption("h")) {
            new HelpFormatter().printHelp("RecreateApplicationDescriptor [options]", options);
            System.exit(0);
        }

        if (cmd.hasOption("l")) {
            props.setProperty("logFile", cmd.getOptionValue("l"));
            logger.create(props.getProperty("logFile"));
        }

        props.setProperty("application", cmd.getOptionValue("a"));

        String configFilePath = cmd.getOptionValue("c");
        props.setProperty("configurationFilePath", configFilePath);
        Properties configProperties = ValidateConfiguration.validateAndLoadConfiguration(configFilePath);
        validateAndLoadConfiguration(configProperties);

        logger.logMessage("** Script configuration:");
        props.stringPropertyNames().stream().sorted()
                .forEach(k -> logger.logMessage("\t" + k + " -> " + props.getProperty(k)));
    }

    private void validateAndLoadConfiguration(Properties cfg) {
        ConfigurationUtility.loadRequiredProperty(cfg, props,
                "DBB_MODELER_APPLICATION_DIR", "The Applications directory");
        ConfigurationUtility.loadRequiredProperty(cfg, props,
                "REPOSITORY_PATH_MAPPING_FILE", "The Repository Paths Mapping file");
    }

    // -------------------------------------------------------------------------
    // Repository Paths Mapping
    // -------------------------------------------------------------------------

    private void loadRepositoryPathsMapping() throws IOException {
        logger.logMessage("** Reading the Repository Layout Mapping definition.");
        File mappingFile = new File(props.getProperty("REPOSITORY_PATH_MAPPING_FILE"));
        if (!mappingFile.exists()) {
            logger.logMessage("*! [ERROR] Repository Paths Mapping file not found: " + mappingFile.getPath());
            System.exit(1);
        }
        try (FileReader reader = new FileReader(mappingFile)) {
            repositoryPathsMapping = new Yaml().loadAs(reader, RepositoryPathsMapping.class);
        }
    }

    // -------------------------------------------------------------------------
    // Core logic
    // -------------------------------------------------------------------------

    private void recreateDescriptor() throws Exception {
        String application = props.getProperty("application");
        String appDir = props.getProperty("DBB_MODELER_APPLICATION_DIR") + "/" + application;

        File applicationDescriptorFile = new File(appDir + "/applicationDescriptor.yml");

        ApplicationDescriptor descriptor;

        if (applicationDescriptorFile.exists()) {
            logger.logMessage("** Importing existing Application Descriptor and reset source groups, dependencies and consumers.");
            descriptor = appDescUtils.readApplicationDescriptor(applicationDescriptorFile);
            appDescUtils.resetAllSourceGroups(descriptor);
            appDescUtils.resetConsumersAndDependencies(descriptor);
        } else {
            logger.logMessage("** No Application Descriptor found — creating a new one.");
            descriptor = appDescUtils.createEmptyApplicationDescriptor();
            descriptor.setApplication(application);
        }

        logger.logMessage("** Getting List of files " + appDir);
        List<Path> allFiles = collectFiles(Paths.get(appDir));

        for (Path filePath : allFiles) {
            String relPath = Paths.get(appDir).relativize(filePath).toString().replace('\\', '/');

            // Skip hidden files / git internals
            if (isHiddenPath(relPath)) {
                logger.logMessage("*! [WARNING] '" + relPath + "' is a hidden file. Skipped.");
                continue;
            }

            // Find the matching Repository Path rule
            RepositoryPath match = findMatchingRepositoryPath(relPath);
            if (match == null) {
                logger.logMessage("*! [WARNING] '" + relPath + "' did not match any rule defined in the repository path mapping configuration. Skipped.");
                continue;
            }

            // Derive the file name without extension
            String fileName = filePath.getFileName().toString();
            String nameNoExt = stripExtension(fileName);

            logger.logMessage("** Adding '" + relPath + "' to Application Descriptor into source group '" + match.getSourceGroup() + "'.");
            appDescUtils.appendFileDefinition(
                    descriptor,
                    match.getSourceGroup(),
                    match.getLanguage(),
                    match.getLanguageProcessor(),
                    match.getArtifactsType(),
                    match.getFileExtension(),
                    match.getRepositoryPath(),
                    nameNoExt,
                    null,   // type — unknown at this stage, resolved during assessUsage
                    null    // usage — resolved during assessUsage
            );
        }

        appDescUtils.writeApplicationDescriptor(applicationDescriptorFile, descriptor);
        logger.logMessage("** Application Descriptor written to '" + applicationDescriptorFile.getPath() + "'.");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<Path> collectFiles(Path root) throws IOException {
        if (!Files.exists(root)) {
            return Collections.emptyList();
        }
        return Files.walk(root)
                .filter(Files::isRegularFile)
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Returns true for any path component that starts with a dot (hidden files /
     * git internal objects).
     */
    private boolean isHiddenPath(String relPath) {
        for (String part : relPath.split("/")) {
            if (part.startsWith(".")) return true;
        }
        return false;
    }

    /**
     * Finds the first RepositoryPath whose repositoryPath value matches the
     * directory portion of the given relative file path.
     */
    private RepositoryPath findMatchingRepositoryPath(String relPath) {
        if (repositoryPathsMapping == null || repositoryPathsMapping.getRepositoryPaths() == null) {
            return null;
        }
        String dir = relPath.contains("/") ? relPath.substring(0, relPath.lastIndexOf('/')) : "";
        for (RepositoryPath rp : repositoryPathsMapping.getRepositoryPaths()) {
            if (dir.equals(rp.getRepositoryPath()) || dir.endsWith("/" + rp.getRepositoryPath())) {
                return rp;
            }
        }
        return null;
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
