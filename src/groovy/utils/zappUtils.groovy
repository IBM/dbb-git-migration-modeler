@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import groovy.transform.*
import groovy.yaml.YamlSlurper
import groovy.yaml.YamlBuilder
import groovy.lang.GroovyShell
import groovy.util.*
import java.nio.file.*
import groovy.cli.commons.*


/**
 * Utilities to read, update or export existing ZAPP file from/to YAML 
 */

@Field Properties props = new Properties()
@Field def applicationDescriptorUtils = loadScript(new File("applicationDescriptorUtils.groovy"))
@Field def applicationDescriptor
@Field def zapp
@Field ArrayList<ZAppPropertyGroup> propertyGroups = new ArrayList<ZAppPropertyGroup>()


// Initialization
parseArgs(args)
 
File ZAPPFile = new File(props.ZAPPFilePath)
if (!ZAPPFile.exists()) {
	println("*! [ERROR] The ZAPP file '${props.ZAPPFilePath}' was not found. Exiting.")
	System.exit(1) 
}
File ApplicationDescriptorFile = new File(props.ApplicationDescriptorFilePath)
if (!ApplicationDescriptorFile.exists()) {
	println("*! [ERROR] The ZAPP file '${props.ApplicationDescriptorFilePath}' was not found. Exiting.")
	System.exit(1) 
}

readZAppFile(ZAPPFile)
applicationDescriptor = applicationDescriptorUtils.readApplicationDescriptor(ApplicationDescriptorFile)

zapp.name = "${applicationDescriptor.application}"
zapp.description = "ZAPP file for the ${applicationDescriptor.application} application"
def dbbBuildProfile = zapp.profiles.find() { profile ->
	profile.name.equals("dbb-build")
}
if (dbbBuildProfile) {
	dbbBuildProfile.settings.application = "${applicationDescriptor.application}"
	dbbBuildProfile.settings.buildScriptPath = "${props.buildScriptPath}"
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

writeZAppFile(ZAPPFile)


/**
 * Parse CLI config
 */
def parseArgs(String[] args) {

	String usage = 'zappUtils.groovy [options]'

	def cli = new CliBuilder(usage:usage)
	// required sandbox options
	cli.z(longOpt:'zapp', args:1, 'Absolute path to the ZAPP file')
	cli.a(longOpt:'applicationDescriptor', args:1, 'Absolute path to the application\'s Application Descriptor file')
	cli.b(longOpt:'buildScriptPath', args:1, 'Absolute path to the Build Script framework (dbb-zappbuild)')

	def opts = cli.parse(args)
	if (!opts) {
		System.exit(1)
	}

	if (opts.z) {
		props.ZAPPFilePath = opts.z
	} else {
		println("*! [ERROR] The path to the ZAPP file must be specified. Exiting.")
		System.exit(1) 
	}
	if (opts.a) {
		props.ApplicationDescriptorFilePath = opts.a
	} else {
		println("*! [ERROR] The path to the Application Descriptor file must be specified. Exiting.")
		System.exit(1) 
	}
	if (opts.b) {
		props.buildScriptPath = opts.b
	} else {
		println("*! [ERROR] The path to the Build Script framework must be specified. Exiting.")
		System.exit(1) 
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
    zapp = yamlSlurper.parse(yamlFile)
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
    yamlFile.withWriter("IBM-1047") { writer ->
        writer.write(yamlBuilder.toString())
    } 

	Process process = "chtag -tc IBM-1047 ${yamlFile.getAbsolutePath()}".execute()
	process.waitFor()   
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
