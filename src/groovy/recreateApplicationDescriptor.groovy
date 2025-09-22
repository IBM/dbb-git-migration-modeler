/********************************************************************************
* Licensed Materials - Property of IBM                                          *
* (c) Copyright IBM Corporation 2018, 2024. All Rights Reserved.                *
*                                                                               *
* Note to U.S. Government Users Restricted Rights:                              *
* Use, duplication or disclosure restricted by GSA ADP Schedule                 *
* Contract with IBM Corp.                                                       *
********************************************************************************/

@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import groovy.transform.*
import groovy.util.*
import groovy.time.*
import groovy.cli.commons.*
import groovy.yaml.YamlSlurper
import java.nio.file.*
import java.nio.file.attribute.*
import static groovy.io.FileType.*

@Field def applicationDescriptorUtils = loadScript(new File("utils/applicationDescriptorUtils.groovy"))
@Field def logger = loadScript(new File("utils/logger.groovy"))
@Field def fileUtils = loadScript(new File("utils/fileUtils.groovy"))
@Field repositoryPathsMapping
// Lists of applications found in Applications Mapping files
@Field applicationsList = new ArrayList<Object>()

// Types Configurations
@Field HashMap<String, String> types

@Field Properties props = new Properties()
// Internal variables
def applicationDescriptor

/**
 * Processing logic
 */

println("** Recreate Application Descriptor file process started.")

// Parse arguments from command-line
parseArgs(args)

// Read the repository layout mapping file
logger.logMessage("** Reading the Repository Layout Mapping definition.")
if (props.REPOSITORY_PATH_MAPPING_FILE) {
	File repositoryPathsMappingFile = new File(props.REPOSITORY_PATH_MAPPING_FILE)
	def yamlSlurper = new groovy.yaml.YamlSlurper()
	repositoryPathsMappingFile.withReader("UTF-8") { reader ->
		repositoryPathsMapping = yamlSlurper.parse(reader)
	}
}

// Read the Types from file
logger.logMessage("** Reading the Type Mapping definition.")
if (props.APPLICATION_MEMBER_TYPE_MAPPING) {
	def typesFile = new File(props.APPLICATION_MEMBER_TYPE_MAPPING)
	if (!typesFile.exists()) {
		logger.logMessage("*! [WARNING] File ${props.APPLICATION_MEMBER_TYPE_MAPPING} not found in the current working directory. All artifacts will use the 'UNKNOWN' type.")
	} else {
		types = fileUtils.loadTypes(props.APPLICATION_MEMBER_TYPE_MAPPING)
//		println("loaded ${props.APPLICATION_MEMBER_TYPE_MAPPING} - ${types}")
	}
} else {
	logger.logMessage("*! [WARNING] No Types File provided. The 'UNKNOWN' type will be assigned by default to all artifacts.")
}

logger.logMessage("** Loading the provided Applications Mapping files.")
File applicationsMappingsDir = new File(props.DBB_MODELER_APPMAPPINGS_DIR)
applicationsMappingsDir.eachFile(FILES) { applicationsMappingFile ->
	logger.logMessage("*** Importing '${applicationsMappingFile.getName()}'")
	def yamlSlurper = new groovy.yaml.YamlSlurper()
	applicationsMappingFile.withReader("UTF-8") { reader ->
		applicationsMapping = yamlSlurper.parse(reader)
	}
	
	applicationsMapping.applications.each() { application ->
		applicationsList.add(application)
	}
}

// Initialize the Application Descriptor
File applicationDescriptorFile = new File("${props.DBB_MODELER_APPLICATION_DIR}/${props.application}/applicationDescriptor.yml")
if (applicationDescriptorFile.exists()) {
	logger.logMessage("** Importing existing Application Descriptor and reseting source groups, dependencies and consumers.")
	applicationDescriptor = applicationDescriptorUtils.readApplicationDescriptor(applicationDescriptorFile)
	applicationDescriptorUtils.resetAllSourceGroups(applicationDescriptor)
	applicationDescriptorUtils.resetConsumersAndDependencies(applicationDescriptor)
} else {
	logger.logMessage("** Creating a new Application Descriptor.")
	applicationDescriptor = applicationDescriptorUtils.createEmptyApplicationDescriptor()
	// Populating the AD file with info from the Applications mapping files
	def foundApplication = applicationsList.find { application ->
		application.application.equals(props.application)
	}

	if (foundApplication) {	
		applicationDescriptor.application = foundApplication.application
		applicationDescriptor.description = foundApplication.description
		applicationDescriptor.owner = foundApplication.owner
		// Adding baseline to ApplicationDescriptor
		applicationDescriptorUtils.addBaseline(applicationDescriptor, "main", "release", foundApplication.baseline)
		applicationDescriptorUtils.addBaseline(applicationDescriptor, "release/${foundApplication.baseline}", "release", foundApplication.baseline)
	} else {	
		logger.logMessage("*! [WARNING] The '${props.application}' Application definition was not found in the provided Applications Mapping files. Using default values.")
		applicationDescriptor.application = props.application
		applicationDescriptor.description = ""
		applicationDescriptor.owner = "None"
		applicationDescriptorUtils.addBaseline(applicationDescriptor, "main", "release", "rel-1.0.0")
		applicationDescriptorUtils.addBaseline(applicationDescriptor, "release/rel-1.0.0", "release", "rel-1.0.0")	
	}	
}

logger.logMessage("** Getting the list of mapped files from '${props.DBB_MODELER_APPLICATION_DIR}/${props.application}'")

HashMap<String, String> files = fileUtils.getMappedFilesFromApplicationDir(props.DBB_MODELER_APPLICATION_DIR, props.application, repositoryPathsMapping, logger)

files.each() { file, repositoryPath ->
	// finding the repository path mapping configuration based on the relative path
	def matchingRepositoryPath = repositoryPathsMapping.repositoryPaths.find {it ->
		it.repositoryPath.equals(repositoryPath)
	}
	// If a Repository Path was found in the RepositoryPathsMapping file
	if (matchingRepositoryPath) {
		usage = "undefined"
		fileExtension = matchingRepositoryPath.fileExtension
		// extract basefilename
		fileName = new File(file).getName()
		baseFileName = fileName.replace(".$fileExtension","")
		type = fileUtils.getType(types, baseFileName.toUpperCase())
		repositoryPath = file.replace("/${fileName}","")
		resolvedRepositoryPath = matchingRepositoryPath.repositoryPath.replaceAll('\\$application', props.application)
		logger.logMessage("** Adding '$file' to Application Descriptor into source group '${matchingRepositoryPath.sourceGroup}'.")
		applicationDescriptorUtils.appendFileDefinition(applicationDescriptor, matchingRepositoryPath.sourceGroup, matchingRepositoryPath.language, matchingRepositoryPath.languageProcessor, matchingRepositoryPath.artifactsType, fileExtension, resolvedRepositoryPath, baseFileName, type, usage)
	} else {
		logger.logMessage("*! [WARNING] '$file' did not match any rule defined in the Repository Path Mapping Configuration. Skipped.")
	}
}

if (files.size() != Files.list(Paths.get("${props.DBB_MODELER_APPLICATION_DIR}/${props.application}")).count()) {
	logger.logMessage("*! [WARNING] Some files were skipped as no matching Source Group was found based on their path. Check log file '${props.logFile}'.")	
}

applicationDescriptorUtils.writeApplicationDescriptor(applicationDescriptorFile, applicationDescriptor)
logger.logMessage("** Created Application Description file '${applicationDescriptorFile.getAbsolutePath()}'")

logger.close()

/**
 * Parse CLI config
 */
def parseArgs(String[] args) {
	Properties configuration = new Properties()
	String usage = 'recreateApplicationDescriptor.groovy [options]'
	String header = 'options:'
	def cli = new CliBuilder(usage:usage,header:header)
	cli.a(longOpt:'application', args:1, required:true, 'Application  name ')
	cli.l(longOpt:'logFile', args:1, required:false, 'Relative or absolute path to an output log file')
	cli.c(longOpt:'configFile', args:1, required:true, 'Path to the DBB Git Migration Modeler Configuration file (created by the Setup script)')
	
	def opts = cli.parse(args)
	if (!args || !opts) {
		cli.usage()
		System.exit(1)
	}

	if (opts.l) {
		props.logFile = opts.l
		logger.create(props.logFile)		
	}

	if (opts.a) {
		props.application = opts.a
	} else {
		logger.logMessage("*! [ERROR] The Application name (option -a/--application) must be provided. Exiting.")
		System.exit(1)		 			
	}

	if (opts.c) {
		props.configurationFilePath = opts.c
		File configurationFile = new File(props.configurationFilePath)
		if (configurationFile.exists()) {
			configurationFile.withReader() { reader ->
				configuration.load(reader)
			}
		} else {
			logger.logMessage("*! [ERROR] The DBB Git Migration Modeler Configuration file '${opts.c}' does not exist. Exiting.")
			System.exit(1)		 			
		}
	} else {
		logger.logMessage("*! [ERROR] The path to the DBB Git Migration Modeler Configuration file was not specified ('-c/--configFile' parameter). Exiting.")
		System.exit(1)
	}

	if (configuration.DBB_MODELER_APPMAPPINGS_DIR) {
		File directory = new File(configuration.DBB_MODELER_APPMAPPINGS_DIR)
		if (directory.exists()) {
			props.DBB_MODELER_APPMAPPINGS_DIR = configuration.DBB_MODELER_APPMAPPINGS_DIR
		} else {
			logger.logMessage("*! [ERROR] The Applications Mappings directory '${configuration.DBB_MODELER_APPMAPPINGS_DIR}' does not exist. Exiting.")
			System.exit(1)
		}
	} else {
		logger.logMessage("*! [ERROR] The Applications Mappings directory must be specified in the DBB Git Migration Modeler Configuration file. Exiting.")
		System.exit(1)
	}	

	if (configuration.DBB_MODELER_APPLICATION_DIR) {
		File directory = new File(configuration.DBB_MODELER_APPLICATION_DIR)
		if (directory.exists()) {
			props.DBB_MODELER_APPLICATION_DIR = configuration.DBB_MODELER_APPLICATION_DIR
		} else {
			logger.logMessage("*! [ERROR] The Applications directory '${configuration.DBB_MODELER_APPLICATION_DIR}' does not exist. Exiting.")
			System.exit(1)
		}
	} else {
		logger.logMessage("*! [ERROR] The Applications directory must be specified in the DBB Git Migration Modeler Configuration file. Exiting.")
		System.exit(1)
	}	

	if (configuration.REPOSITORY_PATH_MAPPING_FILE) {
		File file = new File(configuration.REPOSITORY_PATH_MAPPING_FILE)
		if (file.exists()) {
			props.REPOSITORY_PATH_MAPPING_FILE = configuration.REPOSITORY_PATH_MAPPING_FILE
		} else {
			logger.logMessage("*! [ERROR] The Repository Paths Mapping file '${configuration.REPOSITORY_PATH_MAPPING_FILE}' does not exist. Exiting.")
			System.exit(1)
		}
	} else {
		logger.logMessage("*! [ERROR] The path to the Repository Paths Mapping file must be specified in the DBB Git Migration Modeler Configuration file. Exiting.")
		System.exit(1)
	}
	
	if (configuration.APPLICATION_MEMBER_TYPE_MAPPING) {
		File file = new File(configuration.APPLICATION_MEMBER_TYPE_MAPPING)
		if (file.exists()) {
			props.APPLICATION_MEMBER_TYPE_MAPPING = configuration.APPLICATION_MEMBER_TYPE_MAPPING
		} else {
			logger.logMessage("*! [ERROR] The Types file '${configuration.APPLICATION_MEMBER_TYPE_MAPPING}' does not exist. Exiting.")
			System.exit(1)
		}
	}	

	logger.logMessage("** Script configuration:")
	props.each() { k, v ->
		logger.logMessage("\t$k -> $v")
	}
}