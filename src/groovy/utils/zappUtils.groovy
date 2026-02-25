@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import groovy.transform.*
import groovy.yaml.YamlSlurper
import groovy.yaml.YamlBuilder
import groovy.lang.GroovyShell
import groovy.util.*
import java.nio.file.*
import groovy.cli.commons.*
import com.ibm.dbb.utils.FileUtils


/**
 * Utilities to read, update or export existing ZAPP file from/to YAML 
 */

@Field Properties props = new Properties()
@Field def applicationDescriptorUtils = loadScript(new File("applicationDescriptorUtils.groovy"))
@Field def logger = loadScript(new File("logger.groovy"))

@Field def applicationDescriptor
@Field def zapp
@Field ArrayList<ZAppPropertyGroup> propertyGroups = new ArrayList<ZAppPropertyGroup>()


// Initialization
parseArgs(args)

def ZAPPFilePath = props.DBB_MODELER_APPLICATION_DIR + "/" + props.application + "/zapp.yaml"
File ZAPPFile = new File(ZAPPFilePath)
if (!ZAPPFile.exists()) {
	logger.logMessage("*! [ERROR] The ZAPP file '${ZAPPFilePath}' was not found. Exiting.")
	System.exit(1) 
}

def applicationDescriptorFilePath = props.DBB_MODELER_APPLICATION_DIR + "/" + props.application + "/applicationDescriptor.yml"
File applicationDescriptorFile = new File(applicationDescriptorFilePath)
if (!applicationDescriptorFile.exists()) {
	logger.logMessage("*! [ERROR] The Application Descriptor file '${applicationDescriptorFilePath}' was not found. Exiting.")
	System.exit(1) 
}

zapp = readZAppFile(ZAPPFile)
applicationDescriptor = applicationDescriptorUtils.readApplicationDescriptor(applicationDescriptorFile)

zapp.name = "${applicationDescriptor.application}"
zapp.description = "ZAPP file for the ${applicationDescriptor.application} application"

if (props.BUILD_FRAMEWORK.equals("zBuilder")) {
    def includeFilesSourceGroups = applicationDescriptor.sources.findAll() { source ->
        source.artifactsType.equals("Include File")
    }
    
    if (includeFilesSourceGroups) {
        includeFilesSourceGroups.each() { includeFilesSourceGroup ->
            def propertyGroup = addPropertyGroup(includeFilesSourceGroup.name, includeFilesSourceGroup.language)
            addLibrayToPropertyGroup(propertyGroup, "syslib", "local", includeFilesSourceGroup.repositoryPath)   
        }
    }
    def groovyzBuildProfile = zapp.profiles.find() { profile ->
        profile.name.equals("groovyz-userbuild")
    }
    if (groovyzBuildProfile) {
        zapp.profiles.removeElement(groovyzBuildProfile)
    }
} else if (props.BUILD_FRAMEWORK.equals("zAppBuild")) {
    def groovyzBuildProfile = zapp.profiles.find() { profile ->
        profile.name.equals("groovyz-userbuild")
    }
    if (groovyzBuildProfile) {
        groovyzBuildProfile.settings.application = "${applicationDescriptor.application}"
        groovyzBuildProfile.settings.buildScriptPath = "${props.DBB_ZAPPBUILD}"
    }
    
    def includeFilesSourceGroups = applicationDescriptor.sources.findAll() { source ->
        source.artifactsType.equals("Include File")
    }
    
    if (includeFilesSourceGroups) {
        includeFilesSourceGroups.each() { includeFilesSourceGroup ->
            def propertyGroup = addPropertyGroup(includeFilesSourceGroup.name, includeFilesSourceGroup.language)
            addLibrayToPropertyGroup(propertyGroup, "syslib", "local", includeFilesSourceGroup.repositoryPath)   
        }
    }
    def zBuilderProfile = zapp.profiles.find() { profile ->
        profile.name.equals("zBuilder-userbuild")
    }
    if (zBuilderProfile) {
        zapp.profiles.removeElement(zBuilderProfile)
    }
}

writeZAppFile(ZAPPFile)

logger.close()

/**
 * Parse CLI config
 */
def parseArgs(String[] args) {
    Properties configuration = new Properties()
	String usage = 'zappUtils.groovy [options]'
    String header = 'options:'
	def cli = new CliBuilder(usage:usage, header:header)
    cli.a(longOpt:'application', args:1, required:true, 'Application name.')
    cli.l(longOpt:'logFile', args:1, required:false, 'Relative or absolute path to an output log file')
    cli.c(longOpt:'configFile', args:1, required:true, 'Path to the DBB Git Migration Modeler Configuration file (created by the Setup script)')

	def opts = cli.parse(args)
	if (!opts) {
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
            logger.logMessage("*! [ERROR] The Application's directory '${configuration.DBB_MODELER_APPLICATION_DIR}' does not exist. Exiting.")
            System.exit(1)
        }
    } else {
        logger.logMessage("*! [ERROR] The Applications directory must be specified in the DBB Git Migration Modeler Configuration file. Exiting.")
        System.exit(1)
    }

    if (configuration.BUILD_FRAMEWORK) {
        props.BUILD_FRAMEWORK = configuration.BUILD_FRAMEWORK
        if (props.BUILD_FRAMEWORK.equals("zBuilder")) {
            File directory = new File(configuration.DBB_ZBUILDER)
            if (directory.exists()) {
                props.DBB_ZBUILDER = configuration.DBB_ZBUILDER
            } else {
                logger.logMessage("*! [ERROR] The DBB zBuilder instance '${configuration.DBB_ZBUILDER}' does not exist. Exiting.")
                System.exit(1)
            }
        } else if (props.BUILD_FRAMEWORK.equals("zAppBuild")) {
            File directory = new File(configuration.DBB_ZAPPBUILD)
            if (directory.exists()) {
                props.DBB_ZAPPBUILD = configuration.DBB_ZAPPBUILD
            } else {
                logger.logMessage("*! [ERROR] The DBB zAppBuild instance '${configuration.DBB_ZAPPBUILD}' does not exist. Exiting.")
                System.exit(1)
            }
        } else {
            logger.logMessage("*! [ERROR] The DBB Framework can only be 'zBuilder' or 'zAppBuild' The '${configuration.BUILD_FRAMEWORK}' value defined in the DBB Git Migration Modeler Configuration file is invalid. Exiting.")
            System.exit(1)
        }
    } else {
        logger.logMessage("*! [ERROR] The DBB Build Framework must be specified in the DBB Git Migration Modeler Configuration file. Exiting.")
        System.exit(1)
    }

    logger.logMessage("** Script configuration:")
    props.each() { k, v ->
        logger.logMessage("\t$k -> $v")
    }
}


class ZAppPropertyGroup {
    String name
    String language
    ArrayList<ZAppPropertyGroupLibrary> libraries
}

class ZAppPropertyGroupLibrary {
    String name
    String type
    ArrayList<String> locations
}

/**
 * 
 * Reads an existing ZAPP YAML and returns it
 * 
 */
def readZAppFile(File yamlFile) {
    // Internal objects
    def yamlSlurper = new groovy.yaml.YamlSlurper()
	yamlFile.withReader("UTF-8") { reader ->
		zappFileContent = yamlSlurper.parse(reader)
	}
	return zappFileContent
}

/**
 * Write a ZAPP file into a YAML file
 */
def writeZAppFile(File yamlFile) {
    def yamlBuilder = new YamlBuilder()

    yamlBuilder {
        name zapp.name
        description zapp.description
        version zapp.version
        author zapp.author
        profiles zapp.profiles
        propertyGroups propertyGroups 
    }

    // write file
    yamlFile.withWriter("UTF-8") { writer ->
        writer.write(yamlBuilder.toString())
    }
	FileUtils.setFileTag(yamlFile.getAbsolutePath(), "UTF-8")
}

/**
 * Method to add a Property Group
 */

def addPropertyGroup(String name, String language) {
    ZAppPropertyGroup newPropertyGroup = new ZAppPropertyGroup()
    newPropertyGroup.name = name
    newPropertyGroup.language = language
    newPropertyGroup.libraries = new ArrayList<ZAppPropertyGroupLibrary>()
    propertyGroups.add(newPropertyGroup)
    return newPropertyGroup
}

/**
 * Method to add a Property Group
 */

def addLibrayToPropertyGroup(ZAppPropertyGroup propertyGroup, String libraryName, String libraryType, String locations) {
    ZAppPropertyGroupLibrary newPropertyGroupLibrary = new ZAppPropertyGroupLibrary()
    newPropertyGroupLibrary.name = libraryName
    newPropertyGroupLibrary.type = libraryType
    newPropertyGroupLibrary.locations = new ArrayList<String>()
    locations.split(",").each { location ->
        newPropertyGroupLibrary.locations.add(location)
    }
    propertyGroup.libraries.add(newPropertyGroupLibrary)
}
