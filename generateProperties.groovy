#*******************************************************************************
# Licensed Materials - Property of IBM
# (c) Copyright IBM Corporation 2018, 2024. All Rights Reserved.
#
# Note to U.S. Government Users Restricted Rights:
# Use, duplication or disclosure restricted by GSA ADP Schedule
# Contract with IBM Corp. 
#*******************************************************************************

@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import groovy.transform.*
import groovy.util.*
import groovy.time.*
import groovy.cli.commons.*
import groovy.yaml.YamlSlurper
import java.util.Properties;
import java.nio.file.*
import static java.nio.file.StandardCopyOption.*


// script properties
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

// Print parms
println("** Script configuration:")
props.each { k,v->
	println "   $k -> $v"
}

// Handle log file
if (props.logFile) {
	logger.create(props.logFile)
}

def typesConfigurations
// Build the Types Configuration object from Types Configurations file
logger.logMessage ("** Reading the Types Configurations definitions from '${props.typesConfigurationsFilePath}'. ")
def typesConfigurationsFile = new File(props.typesConfigurationsFilePath)
if (!typesConfigurationsFile.exists()) {
	logger.logMessage "!* Error: the Types Configurations file '${props.typesConfigurationsFilePath}' doesn't exist. Exiting."
	System.exit(1);	
} else {
	def yamlSlurper = new groovy.yaml.YamlSlurper()
	typesConfigurations = yamlSlurper.parse(typesConfigurationsFile)
}

// Parses the Application Descriptor File of the application, to retrieve the list of programs
applicationDescriptorFile = new File("${props.workspace}/${props.application}/${props.application}.yaml")
if (applicationDescriptorFile.exists()) {
	applicationDescriptor = applicationDescriptorUtils.readApplicationDescriptor(applicationDescriptorFile)
} else {
	logger.logMessage ("!* Application Descriptor file ${applicationDescriptorFile.getPath()} was not found. Exiting.")
	System.exit(1)
}

// Path to the dbb-zAppBuild instance we will customize
def customZAppBuildFolderPath = props.workspace + "/dbb-zappbuild"
// Path of the original dbb-zAppBuild instance got from the script parms
def originalZAppBuildFolder = new File(props.zAppBuildFolderPath)
if (!originalZAppBuildFolder.exists()) {
	logger.logMessage "!* Error: the original dbb-zAppBuild folder '${props.zAppBuildFolderPath}' doesn't exist. Exiting."
	System.exit(1);	
} else if (!originalZAppBuildFolder.isDirectory()) {
	logger.logMessage "!* Error: the path '${props.zAppBuildFolderPath}' doesn't point to a folder. Exiting."
	System.exit(1);	
} else {
	// Copying the original zAppBuild to the customer instance if it doesn't exist
	File customZAppBuildFolder = new File(customZAppBuildFolderPath)
	if (!customZAppBuildFolder.exists()) {
		
		logger.logMessage("** Copying the zAppBuild from $originalZAppBuildFolder to $customZAppBuildFolderPath.")
		
		Files.walk(Paths.get(props.zAppBuildFolderPath)).forEach(source -> {
			Path destination = Paths.get(customZAppBuildFolderPath, source.toString().substring(props.zAppBuildFolderPath.length()));
			try {
				Files.copy(source, destination);
			} catch (IOException e) {
				logger.logMessage "!* Error when copying the original dbb-zAppBuild folder '${props.zAppBuildFolderPath}'. Exiting."
				System.exit(1);	
			}
		});		
	}
}

// Path to the Language Configuration folder in the custom dbb-zAppBuild
def customLanguageConfigurationFolderPath = customZAppBuildFolderPath + "/build-conf/language-conf"
File customLanguageConfigurationFolder = new File(customLanguageConfigurationFolderPath)
if (!customLanguageConfigurationFolder.exists()) {
	customLanguageConfigurationFolder.mkdirs()
}

def applicationConfFolderPath = "${props.workspace}/${props.application}/${props.application}/application-conf"
File applicationConfFolder = new File(applicationConfFolderPath)
if (!applicationConfFolder.exists()) {
	def sampleApplicationConfFolderPath = "${props.zAppBuildFolderPath}/samples/application-conf"
	Files.walk(Paths.get(sampleApplicationConfFolderPath)).forEach( source -> {
		Path destination = Paths.get(applicationConfFolderPath, source.toString().substring(sampleApplicationConfFolderPath.length()));
		try {
			Files.copy(source, destination);
		} catch (IOException e) {
			e.printStackTrace();
		}
	});		
}

// languageConfigurationMapping file
def languageConfigurationMappingFilePath = applicationConfFolderPath + "/languageConfigurationMapping.properties"
File languageConfigurationMappingFile = new File(languageConfigurationMappingFilePath)
if (!languageConfigurationMappingFile.exists()) {
	languageConfigurationMappingFile.createNewFile()
}

def filePropertiesFilePath = applicationConfFolderPath + "/file.properties"

logger.logMessage("** Getting the list of files.")

logger.logMessage("*** Generate/Validate Language Configuration properties files.")

// Internal map to collect all information : repositoryFile <> language configuration type 
TreeMap<String, String> filesToLanguageConfigurationMap = new TreeMap<String, String>()

applicationDescriptor.sources.each { sourceGroup ->

	repositoryPath = sourceGroup.repositoryPath
	fileExtension = sourceGroup.fileExtension

	sourceGroup.files.each { file ->
		def type = file.type.split(",").sort().join("-")
		if (type != null && !type.equals("UNKNOWN")) {
			def languageConfigurationFilePath = customLanguageConfigurationFolderPath + "/" + type + ".properties"
			File languageConfigurationFile = new File(languageConfigurationFilePath)

			logger.logMessage("\tAssessing file ${file.name} with type $type.")
			
			if (!languageConfigurationFile.exists()) {
				logger.logMessage("\t Generating new Language Configuration $languageConfigurationFilePath for type '${type}'")
				Properties combinedTypeConfigurationProperties = new Properties()
				type.split("-").each() { typeConfiguration ->

					def matchingTypesConfigurations = typesConfigurations.find() {
						it.typeConfiguration.equals(type)
					}
					matchingTypesConfigurations.each() { matchingTypeConfiguration ->
						def property = matchingTypeConfiguration.getKey() as String
						def value = matchingTypeConfiguration.getValue() as String
						if (!property.equals("typeConfiguration")) {
							if (combinedTypeConfigurationProperties.getProperty(property)) {
								logger.logMessage("!* Warning: property $property was already found in the '$type' property files. Overriding.")
							}
							combinedTypeConfigurationProperties.setProperty(property, value)
						}
					}
				}
				// Save language configuration properties file
				combinedTypeConfigurationProperties.store(new FileWriter(languageConfigurationFilePath), "Generated by the Migration-Modeler utility")
			} else {
				logger.logMessage("\t Found existing Language Configuration $languageConfigurationFilePath for type '${type}'")
			}

			// add each file to the Language Configuration Map to be written to languageConfigurationMapping.properties file
			filesToLanguageConfigurationMap.put(repositoryPath + "/" + file.name + "." + fileExtension, type)

		}
	}
}



if (filesToLanguageConfigurationMap.size() > 0) {


	// Generate languageConfigurationmapping.properties file
	logger.logMessage("*** Generate the language configuration mapping file $languageConfigurationMappingFilePath.")

	// append build files to languageConfiguration.properties file
	BufferedWriter writer = new BufferedWriter(new FileWriter(languageConfigurationMappingFilePath, false))
	filesToLanguageConfigurationMap.each { file, languageConfiguration ->
		writer.write(file + "=" + languageConfiguration  + "\n")
	}
	writer.close()

	// Update build property loadLanguageConfigurationProperties configuration in file.properties
	logger.logMessage("*** Generate loadLanguageConfigurationProperties configuration in $filePropertiesFilePath.")

	BufferedWriter newFilePropertiesWriter = new BufferedWriter(new FileWriter(filePropertiesFilePath + ".new", false))
	BufferedReader filePropertiesReader = new BufferedReader(new FileReader(filePropertiesFilePath))
	while((line = filePropertiesReader.readLine()) != null) {
		def lineSegments = line.split('=')
		if (lineSegments.size() == 2 && lineSegments[0].contains("loadLanguageConfigurationProperties")) {
			// Skip line in new file
		} else {
			newFilePropertiesWriter.write(line + "\n")
		}
	}

	// Append new line
	def currentDate = new Date()
	newFilePropertiesWriter.write("\n\n" + "### Generated by the Migration-Modeler utility on " + currentDate.format("yyyy/MM/dd HH:mm:ss") + " ###\n")
	String fileList = new String()
	fileList = filesToLanguageConfigurationMap.keySet().join(",")
	
	newFilePropertiesWriter.write("# loadLanguageConfigurationProperties=true :: " + fileList + "\n")
	newFilePropertiesWriter.close()

	// Replace old with new file.properties
	File filePropertiesFile = new File(filePropertiesFilePath)
	filePropertiesFile.delete()
	Files.move(Paths.get(filePropertiesFilePath + ".new"), Paths.get(filePropertiesFilePath))
	logger.logMessage("** INFO: Don't forget to enable the use of Language Configuration by uncommenting the 'loadLanguageConfigurationProperties' property in '$filePropertiesFilePath'")
}


// close logger file
logger.close()


/*  ==== Utilities ====  */

/* parseArgs: parse arguments provided through CLI */
def parseArgs(String[] args) {
	String usage = 'generateProperties.groovy [options]'
	String header = 'options:'
	def cli = new CliBuilder(usage:usage,header:header);
	cli.w(longOpt:'workspace', args:1, required:true, 'Absolute path to workspace (root) directory containing all required source directories')
	cli.a(longOpt:'application', args:1, required:true, 'Application  name.')
	cli.t(longOpt:'typesConfigurations', args:1, required:true, 'Path of the Types Configurations YAML file.')
	cli.z(longOpt:'zAppBuild', args:1, required:true, 'Path of the original dbb-zAppBuild folder.')
	cli.l(longOpt:'logFile', args:1, required:false, 'Relative or absolute path to an output log file')

	def opts = cli.parse(args);
	if (!args || !opts) {
		cli.usage();
		System.exit(1);
	}

	if (opts.w) {
		props.workspace = opts.w;
	} else {
		logger.logMessage("*! Error: a workspace ('-w' parameter) must be provided. Exiting.");
		System.exit(1);
	}
	
	if (opts.a) {
		props.application = opts.a;
	} else {
		logger.logMessage("*! Error: an application ('-a' parameter) must be specified. Exiting.");
		System.exit(1);
	}

	if (opts.t) {
		props.typesConfigurationsFilePath = opts.t
	} else {
		logger.logMessage("*! Error: the path to the Types Configurations file ('-t' parameter) must be specified. Exiting.");
		System.exit(1);
	}

	if (opts.z) {
		props.zAppBuildFolderPath = opts.z
	} else {
		logger.logMessage("*! Error: the path of the original dbb-zAppBuild folder ('-z' parameter) must be specified. Exiting.");
		System.exit(1);
	}
	
	if (opts.l) {
		props.logFile = opts.l
	}	
	
}
