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

@Field def applicationDescriptorUtils = loadScript(new File("utils/applicationDescriptorUtils.groovy"))
@Field def logger = loadScript(new File("utils/logger.groovy"))
@Field repositoryPathsMapping

// script properties
@Field Properties props = new Properties()
// Internal variables
def applicationDescriptor

/**
 * Processing logic
 */

println("** Recreate Application Descriptor process started. ")

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

// Read the repository layout mapping file
logger.logMessage ("** Reading the Repository Layout Mapping definition. ")

if (props.repositoryPathsMappingFilePath) {
	File repositoryPathsMappingFile = new File(props.repositoryPathsMappingFilePath)
	if (!repositoryPathsMappingFile.exists()) {
		logger.logMessage "!* Warning: File ${props.repositoryPathsMappingFilePath} not found. Process will exit."
		System.exit(1)
	} else {
		def yamlSlurper = new groovy.yaml.YamlSlurper()
		repositoryPathsMapping = yamlSlurper.parse(repositoryPathsMappingFile)
	}
}

// Initialize the Application Descriptor
File applicationDescriptorFile = new File("${props.workspace}/${props.application}/${props.application}.yaml")
if (applicationDescriptorFile.exists()) {
	logger.logMessage("* Importing existing Application Descriptor and reset source groups, dependencies and consumers.");
	applicationDescriptor = applicationDescriptorUtils.readApplicationDescriptor(applicationDescriptorFile)
	applicationDescriptorUtils.resetAllSourceGroups(applicationDescriptor)
	applicationDescriptorUtils.resetConsumersAndDependencies(applicationDescriptor)
} else {
	logger.logMessage("* Creating a new Application Descriptor.");
	applicationDescriptor = applicationDescriptorUtils.createEmptyApplicationDescriptor()
	applicationDescriptor.application = props.application
}

logger.logMessage("* Getting List of files ${props.workspace}/${props.application}");
Set<String> fileList = getFileList("${props.workspace}/${props.application}")

fileList.each() { file ->

	if (!file.startsWith(".")) {

		// path to look up in the repository path mapping configuration
		def pathToLookup = file.replace(props.application, "\$application").replace(new File(file).getName(),"")
		if (pathToLookup.endsWith('/'))
			pathToLookup = pathToLookup.take(pathToLookup.length()-1)

		// finding the repository path mapping configuration based on the relative path
		def matchingRepositoryPath = repositoryPathsMapping.repositoryPaths.find {it ->
			it.repositoryPath.contains(pathToLookup)
		}

		// Loop through directories and append file definitions
		if (matchingRepositoryPath) {
			type = "UNKNOWN" // TODO - New Service Util
			usage = "undefined"
			fileExtension = matchingRepositoryPath.fileExtension
			// extract basefilename
			fileName = new File(file).getName()
			baseFileName = fileName.replace(".$fileExtension","")
			repositoryPath = file.replace("/${fileName}","")

			logger.logMessage("* Adding file $file to Application Descriptor into source group ${matchingRepositoryPath.sourceGroup}.");
			
			
			applicationDescriptorUtils.appendFileDefinition(applicationDescriptor, matchingRepositoryPath.sourceGroup, matchingRepositoryPath.languageProcessor, matchingRepositoryPath.artifactsType, fileExtension, repositoryPath, baseFileName, type, usage)

		} else {
			logger.logMessage("*! The file ($file) did not match any rule defined in the repository path mapping configuration.");
		}

	} else {
		// A hidden file found
		logger.logMessage("*! A hidden file found ($file). Skipped.");

	}


}


applicationDescriptorUtils.writeApplicationDescriptor(applicationDescriptorFile, applicationDescriptor)
logger.logMessage("* Created Application Description file " + applicationDescriptorFile.getAbsolutePath());

/**
 * Parse CLI config
 */
def parseArgs(String[] args) {

	String usage = 'createApplicationDescriptor.groovy [options]'

	def cli = new CliBuilder(usage:usage)
	// required sandbox options
	cli.w(longOpt:'workspace', args:1, 'Absolute path to workspace (root) directory containing all required source directories')
	cli.a(longOpt:'application', args:1, required:true, 'Application  name ')
	cli.r(longOpt:'repositoryPathsMapping', args:1, required:true, 'Path to the Repository Paths Mapping file')
	cli.l(longOpt:'logFile', args:1, required:false, 'Relative or absolute path to an output log file')

	def opts = cli.parse(args)
	if (!opts) {
		System.exit(1)
	}

	if (opts.w) props.workspace = opts.w
	if (opts.a) props.application = opts.a
	if (opts.r) props.repositoryPathsMappingFilePath = opts.r
	if (opts.l) props.logFile = opts.l

}

/**
 * Returns the list of files within the directory structure 
 * 
 */

def getFileList(String dir) {
	Set<String> fileList = new HashSet();
	Files.walkFileTree(Paths.get(dir), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					if (!Files.isDirectory(file)) {

						String fileName = file.toString();
						if (fileName.startsWith('/')) {
							String relPath = new File(dir).toURI().relativize(new File(fileName.trim()).toURI()).getPath()
							fileList.add(relPath);
						}
					}
					return FileVisitResult.CONTINUE;
				}
			});
	return fileList;
}
