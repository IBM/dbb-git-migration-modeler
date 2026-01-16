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
import java.util.Properties;
import java.nio.file.*
import static java.nio.file.StandardCopyOption.*
import com.ibm.dbb.utils.FileUtils

@Field Properties props = new Properties()
@Field def applicationDescriptorUtils = loadScript(new File("utils/applicationDescriptorUtils.groovy"))
@Field def logger = loadScript(new File("utils/logger.groovy"))
@Field File applicationDescriptorFile
@Field def applicationDescriptor

/**
 * Processing logic
 */

// Parse arguments from command-line
parseArgs(args)

// Handle log file
if (props.logFile) {
	logger.create(props.logFile)
}

def typesConfigurations
// Build the Types Configuration object from Types Configurations file
logger.logMessage("** Reading the Types Configurations definitions from '${props.TYPE_CONFIGURATIONS_FILE}'.")
def typesConfigurationsFile = new File(props.TYPE_CONFIGURATIONS_FILE)
if (!typesConfigurationsFile.exists()) {
	logger.logMessage("!* [ERROR] the Types Configurations file '${props.TYPE_CONFIGURATIONS_FILE}' does not exist. Exiting.")
	System.exit(1);	
} else {
	def yamlSlurper = new groovy.yaml.YamlSlurper()
	typesConfigurationsFile.withReader("UTF-8") { reader ->
		typesConfigurations = yamlSlurper.parse(reader)
	}	
}

// Parses the Application Descriptor File of the application, to retrieve the list of programs
applicationDescriptorFile = new File("${props.DBB_MODELER_APPLICATION_DIR}/${props.application}/applicationDescriptor.yml")
if (applicationDescriptorFile.exists()) {
	applicationDescriptor = applicationDescriptorUtils.readApplicationDescriptor(applicationDescriptorFile)
} else {
	logger.logMessage("!* [ERROR] The Application Descriptor file '${applicationDescriptorFile.getPath()}' does not exist. Exiting.")
	System.exit(1)
}

// Path to the dbb-zAppBuild instance we will customize
def customZAppBuildFolderPath = props.DBB_MODELER_APPLICATION_DIR + "/dbb-zappbuild"
// Path of the original dbb-zAppBuild instance got from the script parms
def originalZAppBuildFolder = new File(props.DBB_ZAPPBUILD)
if (!originalZAppBuildFolder.exists()) {
	logger.logMessage("!* [ERROR] The original dbb-zAppBuild folder '${props.DBB_ZAPPBUILD}' does not exist. Exiting.")
	System.exit(1);	
} else if (!originalZAppBuildFolder.isDirectory()) {
	logger.logMessage("!* [ERROR] The path '${props.DBB_ZAPPBUILD}' does not point to a folder. Exiting.")
	System.exit(1);	
} else {
	// Copying the original zAppBuild to the customer instance if it doesn't exist
	File customZAppBuildFolder = new File(customZAppBuildFolderPath)
	if (!customZAppBuildFolder.exists()) {
		logger.logMessage("** Copying the zAppBuild from $originalZAppBuildFolder to $customZAppBuildFolderPath.")
		runShellCmd("cp -Rf $originalZAppBuildFolder $customZAppBuildFolderPath")
	}
}

// Path to the Language Configuration folder in the custom dbb-zAppBuild
def customLanguageConfigurationFolderPath = customZAppBuildFolderPath + "/build-conf/language-conf"
File customLanguageConfigurationFolder = new File(customLanguageConfigurationFolderPath)
if (!customLanguageConfigurationFolder.exists()) {
	customLanguageConfigurationFolder.mkdirs()
}

def applicationConfFolderPath = "${props.DBB_MODELER_APPLICATION_DIR}/${props.application}/application-conf"
def sampleApplicationConfFolderPath = "${props.DBB_ZAPPBUILD}/samples/application-conf"

File applicationConfFolder = new File(applicationConfFolderPath)
if (!applicationConfFolder.exists()) {
	logger.logMessage("** Copying default application-conf directory to ${applicationConfFolderPath}")
	runShellCmd("mkdir -p ${applicationConfFolderPath}")
	runShellCmd("cp -R ${sampleApplicationConfFolderPath}/. ${applicationConfFolderPath}/")
} else {
	logger.logMessage("** For ${props.application} an application-conf directory already exists.")
}

// languageConfigurationMapping file
def languageConfigurationMappingFilePath = applicationConfFolderPath + "/languageConfigurationMapping.properties"

def filePropertiesFilePath = applicationConfFolderPath + "/file.properties"

logger.logMessage("** Getting the list of files.")

// Internal map to collect all information : repositoryFile <> language configuration type 
def filesToLanguageConfigurations = []
HashSet<String> typesConfigurationsToCreate = []
HashSet<String> createdTypesConfigurations = []

logger.logMessage("** Building a list of types to create.")
applicationDescriptor.sources.each { sourceGroup ->
	repositoryPath = sourceGroup.repositoryPath
	fileExtension = sourceGroup.fileExtension

	sourceGroup.files.each { file ->
		if (file.type != null && !file.type.equals("UNKNOWN")) {
            typesConfigurationsToCreate << file.type
            // add each file to the Language Configuration Map to be written to languageConfigurationMapping.properties file
            filesToLanguageConfigurations << [ "file": "${repositoryPath}/${file.name}.${fileExtension}", "type": file.type ]
        }
    }
}

// If there are types to create, do so
if (typesConfigurationsToCreate && typesConfigurationsToCreate.size() > 0) {
    logger.logMessage("** Generating/Validating Language Configuration properties files.")
    typesConfigurationsToCreate.each() { typeToCreate ->
        def languageConfigurationFilePath = customLanguageConfigurationFolderPath + "/" + typeToCreate + ".properties"
        File languageConfigurationFile = new File(languageConfigurationFilePath)
        if (!languageConfigurationFile.exists()) {
            logger.logMessage("\tGenerating new Language Configuration '$languageConfigurationFilePath' for type '${typeToCreate}'")
            Properties typeConfigurationProperties = new Properties()
            def matchingTypeConfiguration = typesConfigurations.typesConfigurations.find() { matchingTypeConfiguration ->
                matchingTypeConfiguration.typeConfiguration.equals(typeToCreate)
            }
            if (matchingTypeConfiguration) {
                matchingTypeConfiguration.variables.each() { variable ->
                    if (typeConfigurationProperties.getProperty(variable.name)) {
                        logger.logMessage("\t[WARNING] Property '${variable.name}' was already found in the '$type' property files. Overriding.")
                    }
                    typeConfigurationProperties.setProperty(variable.name, variable.value)
                }
                createdTypesConfigurations << typeToCreate
                // Save language configuration properties file
                typeConfigurationProperties.store(new FileWriter(languageConfigurationFilePath), "Generated by the Migration-Modeler utility")
            } else {
                logger.logMessage("\t[WARNING] No definition found for type '${typeToCreate}' in '${typesConfigurationsFile.getAbsolutePath()}'")
            }
        } else {
            logger.logMessage("\tFound existing Language Configuration '$languageConfigurationFilePath' for type '${typeToCreate}'")
            createdTypesConfigurations << typeToCreate
        }
    }
} else {
    logger.logMessage("\t[WARNING] No Type Configuration to create.")
} 

if (filesToLanguageConfigurations.size() > 0) {

	// Generate languageConfigurationmapping.properties file
	logger.logMessage("*** Generate the language configuration mapping file $languageConfigurationMappingFilePath.")
	
    def fileList = []	

	// append build files to languageConfiguration.properties file
	File languageConfigurationMappingFile = new File(languageConfigurationMappingFilePath)
	languageConfigurationMappingFile.withWriter("IBM-1047") { writer ->
		filesToLanguageConfigurations.each { fileToLanguageConfiguration ->
            if (createdTypesConfigurations.contains(fileToLanguageConfiguration.type)) { 
                writer.write("${fileToLanguageConfiguration.file}=${fileToLanguageConfiguration.type}\n")
                fileList << fileToLanguageConfiguration.file
            } else {
                logger.logMessage("\t[WARNING] File '${fileToLanguageConfiguration.file}' was discarded because no definition was found for type '${fileToLanguageConfiguration.type}'")
            } 
		}
	}
	FileUtils.setFileTag(languageConfigurationMappingFilePath, "IBM-1047")

	// Update build property loadLanguageConfigurationProperties configuration in file.properties
	logger.logMessage("*** Generate loadLanguageConfigurationProperties configuration in $filePropertiesFilePath.")

    File filePropertiesFile = new File(filePropertiesFilePath)
    def filePropertiesLines = filePropertiesFile.readLines()

    filePropertiesFile.withWriter('IBM-1047') { writer ->
        for (int i = 1; i < filePropertiesLines.size(); i++) {
            def lineSegments = filePropertiesLines[i].split('=')
            if (lineSegments.size() == 2 && lineSegments[0].contains("loadLanguageConfigurationProperties")) {
                // Skip line in new file
            } else {
                writer.write(filePropertiesLines[i] + "\n")
            }
        }
        def currentDate = new Date()
        writer.write("\n\n" + "### Generated by the Migration-Modeler utility on " + currentDate.format("yyyy/MM/dd HH:mm:ss") + " ###\n")
        
        writer.write("# loadLanguageConfigurationProperties=true :: ${fileList.join(",")}\n")
    }
		
	logger.logMessage("** INFO: Don't forget to enable the use of Language Configuration by uncommenting the 'loadLanguageConfigurationProperties' property in '$filePropertiesFilePath'")
}

// close logger file
logger.close()


/*  ==== Utilities ====  */

/* parseArgs: parse arguments provided through CLI */
def parseArgs(String[] args) {
	Properties configuration = new Properties()
	String usage = 'generateProperties.groovy [options]'
	String header = 'options:'
	def cli = new CliBuilder(usage:usage,header:header);
	cli.a(longOpt:'application', args:1, required:true, 'Application name.')
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

	if (configuration.TYPE_CONFIGURATIONS_FILE) {
		File file = new File(configuration.TYPE_CONFIGURATIONS_FILE)
		if (file.exists()) {
			props.TYPE_CONFIGURATIONS_FILE = configuration.TYPE_CONFIGURATIONS_FILE
		} else {
			logger.logMessage("*! [ERROR] The Types Configurations file '${configuration.TYPE_CONFIGURATIONS_FILE}' does not exist. Exiting.")
			System.exit(1)
		}
	} else {
		logger.logMessage("*! [ERROR] The path to the Types Configurations file must be specified in the DBB Git Migration Modeler Configuration file. Exiting.")
		System.exit(1)
	}	

	if (configuration.DBB_ZAPPBUILD) {
		File directory = new File(configuration.DBB_ZAPPBUILD)
		if (directory.exists()) {
			props.DBB_ZAPPBUILD = configuration.DBB_ZAPPBUILD
		} else {
			logger.logMessage("*! [ERROR] The dbb-zAppBuild instance '${configuration.DBB_ZAPPBUILD}' does not exist. Exiting.")
			System.exit(1)
		}
	} else {
		logger.logMessage("*! [ERROR] The dbb-zAppBuild instance must be specified in the DBB Git Migration Modeler Configuration file. Exiting.")
		System.exit(1)
	}	
	
	logger.logMessage("** Script configuration:")
	props.each() { k, v ->
		logger.logMessage("\t$k -> $v")
	}
}

// Methods
def runShellCmd(String cmd){
	StringBuffer resp = new StringBuffer()
	StringBuffer error = new StringBuffer()

	Process process = cmd.execute()
	process.waitForProcessOutput(resp, error)
	if (error) {
		String warningMsg = "*! Failed to execute shell command $cmd"
		println(warningMsg)
		println(error)
	}
}