/********************************************************************************
* Licensed Materials - Property of IBM                                          *
* (c) Copyright IBM Corporation 2018, 2024. All Rights Reserved.                *
*                                                                               *
* Note to U.S. Government Users Restricted Rights:                              *
* Use, duplication or disclosure restricted by GSA ADP Schedule                 *
* Contract with IBM Corp.                                                       *
********************************************************************************/

@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import com.ibm.dbb.metadata.*
import com.ibm.dbb.dependency.*
import com.ibm.dbb.build.*
import groovy.transform.*
import com.ibm.dbb.build.report.*
import com.ibm.dbb.build.report.records.*
import groovy.yaml.YamlSlurper
import groovy.lang.GroovyShell
import groovy.util.*
import java.nio.file.*
import groovy.cli.commons.*
import static java.nio.file.StandardCopyOption.*


@Field BuildProperties props = BuildProperties.getInstance()
@Field MetadataStore metadataStore
@Field def applicationDescriptorUtils = loadScript(new File("utils/applicationDescriptorUtils.groovy"))
@Field def logger = loadScript(new File("utils/logger.groovy"))
@Field File originalApplicationDescriptorFile //Original Application Descriptor file in CONFIGS
@Field File updatedApplicationDescriptorFile  //Updated Application Descriptor file in APPLICATIONS
@Field def applicationDescriptor


/**
 * Processing logic
 */

// Initialization
parseArgs(args)
initScriptParameters()

// Print parms
println("** Script configuration:")
props.each { k,v->
	println "   $k -> $v"
}

// Handle log file
if (props.logFile) {
	logger.create(props.logFile)
}

// create metadatastore
metadataStore = MetadataStoreFactory.createFileMetadataStore("${props.metadatastore}")
if (!metadataStore) {
	logger.logMessage("*! [ERROR] Failed to initialize the DBB File Metatadastore at '${props.metadatastore}'. Exiting.")
	System.exit(1)
} 

logger.logMessage("** Getting the list of files of 'Include File' type.")
//HashMap<String, ArrayList<String>> includesFiles = getIncludeFilesFromApplicationDescriptor()
HashMap<String, HashMap<String, String>> includesFiles = getIncludeFilesFromApplicationDescriptor()

if (includesFiles && includesFiles.size() > 0) {
	assessImpactedFilesForIncludeFiles(includesFiles)
} else {
	logger.logMessage("*** No source found with 'Include File' type.")
}

logger.logMessage("** Getting the list of files of 'Program' type.")
HashMap<String, HashMap<String, String>> programs = getProgramsFromApplicationDescriptor()

if (programs && programs.size() > 0) {
	assessImpactedFilesForPrograms(programs)
} else {
	logger.logMessage("*** No source found with 'Program' type.")
}

logger.close()

/** Methods **/

/**** Get the list of files of type 'Include File' from the Application Descriptor of the application ****/
def getIncludeFilesFromApplicationDescriptor() {
	HashMap<String, HashMap<String, String>> files = new HashMap<String, HashMap<String, String>>()

	def matchingSources = applicationDescriptor.sources.findAll { source ->
		source.artifactsType.equalsIgnoreCase("Include File") 
	}
	if (matchingSources) {
		matchingSources.each() { matchingSource ->
			matchingSource.files.each() { file ->
				def impactSearchRule = 	"search:[:COPY]${props.workspace}/?path=${props.application}/${matchingSource.repositoryPath}/*." + matchingSource.fileExtension + ";**/${matchingSource.repositoryPath}/*."  + matchingSource.fileExtension as String
				HashMap<String, String> properties = new HashMap<String, String>()
				properties.put("impactSearchRule", impactSearchRule)
				properties.put("repositoryPath", matchingSource.repositoryPath)
				properties.put("fileExtension", matchingSource.fileExtension)
				properties.put("artifactsType", matchingSource.artifactsType)
				properties.put("sourceGroupName", matchingSource.name) 
				properties.put("language", matchingSource.language) 
				properties.put("languageProcessor", matchingSource.languageProcessor) 
				properties.put("type", file.type)
				files.put(file.name, properties)
			}
		}
	}
	return files
}

/**** Get the list of files of type 'Program' from the Application Descriptor of the application ****/
def getProgramsFromApplicationDescriptor() {
	HashMap<String, HashMap<String, String>> files = new HashMap<String, HashMap<String, String>>()

	def matchingSources = applicationDescriptor.sources.findAll { source ->
		source.artifactsType.equalsIgnoreCase("Program") 
	}
	if (matchingSources) {
		matchingSources.each() { matchingSource ->
			matchingSource.files.each() { file ->
				// run impact analysis and only look for Static CALL dependencies
				def impactSearchRule = 	"search:[:CALL]${props.workspace}/?path=${props.application}/${matchingSource.repositoryPath}/*." + matchingSource.fileExtension + ";**/${matchingSource.repositoryPath}/*."  + matchingSource.fileExtension as String
				HashMap<String, String> properties = new HashMap<String, String>()
				properties.put("impactSearchRule", impactSearchRule)
				properties.put("repositoryPath", matchingSource.repositoryPath)
				properties.put("fileExtension", matchingSource.fileExtension)
				properties.put("artifactsType", matchingSource.artifactsType)
				properties.put("sourceGroupName", matchingSource.name)
				properties.put("language", matchingSource.language) 
				properties.put("languageProcessor", matchingSource.languageProcessor) 
				properties.put("type", file.type) 
				files.put(file.name, properties)
			}
		}
	}
	return files
}

/**** Assess Usage of Include Files ****/
 def assessImpactedFilesForIncludeFiles(HashMap<String, ArrayList<String>> includeFiles) {

	includeFiles.each { file, properties ->
		def impactSearchRule = properties.get("impactSearchRule")
		def repositoryPath = properties.get("repositoryPath")
		def fileExtension = properties.get("fileExtension")
		def artifactsType = properties.get("artifactsType")
		def sourceGroupName = properties.get("sourceGroupName")
		def language = properties.get("language")
		def languageProcessor = properties.get("languageProcessor")
		def type = properties.get("type")
		def qualifiedFile = repositoryPath + '/' + file + '.' + fileExtension
		
		Set<String> referencingCollections = new HashSet<String>()

		// Check if the file physically exists
		File sourceFile = new File ("${props.workspace}/${props.application}/${qualifiedFile}")
		if (sourceFile.exists()) { 
			// Obtain impacts
			logger.logMessage("** Analyzing impacted applications for file '${props.application}/${qualifiedFile}'.")
			def impactedFiles = findImpactedFiles(impactSearchRule, props.application + '/' + qualifiedFile)
			
			// Assess impacted files
			if (impactedFiles.size() > 0) 
				logger.logMessage("\tFiles depending on '${repositoryPath}/${file}.${fileExtension}' :")
			
			impactedFiles.each { impactedFile ->
				logger.logMessage("\t'${impactedFile.getFile()}' in '${impactedFile.getCollection().getName()}' application context")
				referencingCollections.add(impactedFile.getCollection().getName())
			}
	
			// Assess usage when only 1 application reference the file
			if (referencingCollections.size() == 1) {
				logger.logMessage("\t==> '$file' is owned by the '${referencingCollections[0]}' application")
			
				// If Include File belongs to the scanned application
				if (props.application.equals(referencingCollections[0])) {
					// Just update the usage to PRIVATE
					applicationDescriptorUtils.appendFileDefinition(applicationDescriptor, sourceGroupName, language, languageProcessor, artifactsType, fileExtension, repositoryPath, file, type, "private")
					logger.logMessage("\t==> Updating usage of Include File '$file' to 'private' in '${updatedApplicationDescriptorFile.getPath()}'.")
				} else { // Only an other application references this Include File, so update the definitions and maybe move it
					if (props.moveFiles.toBoolean()) {
						// Start by removing the file for the application
						applicationDescriptorUtils.removeFileDefinition(applicationDescriptor, sourceGroupName, file)
						// Update the target Application Descriptor 
						originalTargetApplicationDescriptorFile = new File("${props.configurationsDirectory}/${referencingCollections[0]}.yml")
						updatedTargetApplicationDescriptorFile = new File("${props.workspace}/${referencingCollections[0]}/applicationDescriptor.yml")
						def targetApplicationDescriptor
						// determine which YAML file to use
						if (updatedTargetApplicationDescriptorFile.exists()) { // update the Application Descriptor that already exists in the Application repository
							targetApplicationDescriptor = applicationDescriptorUtils.readApplicationDescriptor(updatedTargetApplicationDescriptorFile)
						} else { // Start from the original Application Descriptor created by the extraction phase
							if (originalTargetApplicationDescriptorFile.exists()) {
								Files.copy(originalTargetApplicationDescriptorFile.toPath(), updatedTargetApplicationDescriptorFile.toPath(), REPLACE_EXISTING, COPY_ATTRIBUTES)
								targetApplicationDescriptor = applicationDescriptorUtils.readApplicationDescriptor(updatedTargetApplicationDescriptorFile)
							} else {
								logger.logMessage("*! [WARNING] Application Descriptor file '${originalTargetApplicationDescriptorFile.getPath()}' was not found. Skipping the configuration update for Include File '${file}'.")
							}
						}
						// Target Application Descriptor file has been found and can be updated
						if (targetApplicationDescriptor) {
							targetRepositoryPath = computeTargetFilePath(repositoryPath, props.application, referencingCollections[0])
							applicationDescriptorUtils.appendFileDefinition(targetApplicationDescriptor, sourceGroupName, language, languageProcessor, artifactsType, fileExtension, targetRepositoryPath, file, type, "private")
							applicationDescriptorUtils.writeApplicationDescriptor(updatedTargetApplicationDescriptorFile, targetApplicationDescriptor)
							copyFileToApplicationFolder(props.application + '/' + qualifiedFile, props.application, referencingCollections[0])
							// Update application mappings
							updateMappingFiles(props.configurationsDirectory, props.application, referencingCollections[0], props.workspace + '/' + props.application + '/' + qualifiedFile);
							logger.logMessage("\t==> Moving Include File '$file' with usage 'private' to Application '${referencingCollections[0]}' described '${updatedTargetApplicationDescriptorFile.getPath()}'.")
						}
					} else {
						// just modify the scope as PUBLIC or SHARED
						def usageLabel = props.application.equals("UNASSIGNED") ? 'shared' : 'public'
						logger.logMessage("\t==> Updating usage of Include File '$file' to '$usageLabel' in '${updatedApplicationDescriptorFile.getPath()}'.")
						applicationDescriptorUtils.appendFileDefinition(applicationDescriptor, sourceGroupName, language, languageProcessor, artifactsType, fileExtension, repositoryPath, file, type, usageLabel)
						
						updateConsumerApplicationDescriptor(referencingCollections[0], "source", applicationDescriptor)
					}
				}
				applicationDescriptorUtils.writeApplicationDescriptor(updatedApplicationDescriptorFile, applicationDescriptor)
	
			} else if (referencingCollections.size() > 1) {
				logger.logMessage("\t==> '$file' referenced by multiple applications - $referencingCollections")
				
				// just modify the scope as PUBLIC or SHARED
				def usageLabel = props.application.equals("UNASSIGNED") ? 'shared' : 'public'
				logger.logMessage("\t==> Updating usage of Include File '$file' to '$usageLabel' in '${updatedApplicationDescriptorFile.getPath()}'.")
				applicationDescriptorUtils.appendFileDefinition(applicationDescriptor, sourceGroupName, language, languageProcessor, artifactsType, fileExtension, repositoryPath, file, type, usageLabel)
				
				// update consumers
				referencingCollections.each { consumerCollection ->
					if (!consumerCollection.equals(props.application)) {
						updateConsumerApplicationDescriptor(consumerCollection, "source", applicationDescriptor)
					}
				}
				applicationDescriptorUtils.writeApplicationDescriptor(updatedApplicationDescriptorFile, applicationDescriptor)
				
			} else {
				logger.logMessage("\tThe Include File '$file' is not referenced at all.")
				// Just update the usage to 'unused'
				applicationDescriptorUtils.appendFileDefinition(applicationDescriptor, sourceGroupName, language, languageProcessor, artifactsType, fileExtension, repositoryPath, file, type, "unused")
				applicationDescriptorUtils.writeApplicationDescriptor(updatedApplicationDescriptorFile, applicationDescriptor)
			}
		} else {
			logger.logMessage("*! [WARNING] The Include File '$file' was not found on the filesystem. Skipping analysis.")
		}
	}
}


/**** Assess Usage of programs ****/
def assessImpactedFilesForPrograms(HashMap<String, ArrayList<String>> programs) {

	programs.each { file, properties ->
		def impactSearchRule = properties.get("impactSearchRule")
		def repositoryPath = properties.get("repositoryPath")
		def fileExtension = properties.get("fileExtension")
		def artifactsType = properties.get("artifactsType")
		def sourceGroupName = properties.get("sourceGroupName")
		def language = properties.get("language")
		def languageProcessor = properties.get("languageProcessor")
		def type = properties.get("type")
		def qualifiedFile = repositoryPath + '/' + file + '.' + fileExtension
		
		Set<String> referencingCollections = new HashSet<String>()

		// Obtain impacts
		logger.logMessage("** Analyzing impacted applications for file '${props.application}/${qualifiedFile}'.")
		def impactedFiles = findImpactedFiles(impactSearchRule, props.application + '/' + qualifiedFile)
		
		// Assess impacted files
		if (impactedFiles.size() > 0) 
			logger.logMessage("\tFiles depending on '${repositoryPath}/${file}.${fileExtension}' :")
		
		impactedFiles.each { impactedFile ->
			logger.logMessage("\t'${impactedFile.getFile()}' in '${impactedFile.getCollection().getName()}' application context")
			referencingCollections.add(impactedFile.getCollection().getName())
		}

		// Assess usage when only 1 application reference the file
		if (referencingCollections.size() == 1) {
			logger.logMessage("\t==> '$file' is called from the '${referencingCollections[0]}' application")
		
			// If Program belongs to the scanned application
			if (props.application.equals(referencingCollections[0])) {
				// Just update the usage to INTERNAL
				applicationDescriptorUtils.appendFileDefinition(applicationDescriptor, sourceGroupName, language, languageProcessor, artifactsType, fileExtension, repositoryPath, file, type, "internal submodule")
				logger.logMessage("\t==> Updating usage of Program '$file' to 'internal submodule' in '${updatedApplicationDescriptorFile.getPath()}'.")
			} else { // Only an other application references this Program, so changing the USAGE to SERVICE
				// Update the target Application Descriptor to add Dependency 
				originalTargetApplicationDescriptorFile = new File("${props.configurationsDirectory}/${referencingCollections[0]}.yml")
				updatedTargetApplicationDescriptorFile = new File("${props.workspace}/${referencingCollections[0]}/applicationDescriptor.yml")
				def targetApplicationDescriptor
				// determine which YAML file to use
				if (updatedTargetApplicationDescriptorFile.exists()) { // update the Application Descriptor that already exists in the Application repository
					targetApplicationDescriptor = applicationDescriptorUtils.readApplicationDescriptor(updatedTargetApplicationDescriptorFile)
				} else { // Start from the original Application Descriptor created by the extraction phase
					if (originalTargetApplicationDescriptorFile.exists()) {
						Files.copy(originalTargetApplicationDescriptorFile.toPath(), updatedTargetApplicationDescriptorFile.toPath(), REPLACE_EXISTING, COPY_ATTRIBUTES)
						targetApplicationDescriptor = applicationDescriptorUtils.readApplicationDescriptor(updatedTargetApplicationDescriptorFile)
					} else {
						logger.logMessage("*! [WARNING] Application Descriptor file '${originalTargetApplicationDescriptorFile.getPath()}' was not found. Skipping the configuration update for Include File '${file}'.")
					}
				}
				// Target Application Descriptor file has been found and can be updated
				if (targetApplicationDescriptor) {
					logger.logMessage("\t\tAdding dependency to application ${referencingCollections[0]}") 						
					applicationDescriptorUtils.addApplicationDependency(targetApplicationDescriptor, applicationDescriptor.application, "latest", "binary")
					applicationDescriptorUtils.writeApplicationDescriptor(updatedTargetApplicationDescriptorFile, targetApplicationDescriptor)
				}
				applicationDescriptorUtils.appendFileDefinition(applicationDescriptor, sourceGroupName, language, languageProcessor, artifactsType, fileExtension, repositoryPath, file, type, "service submodule")
				applicationDescriptorUtils.addApplicationConsumer(applicationDescriptor, referencingCollections[0])
				logger.logMessage("\t==> Updating usage of Program '$file' to 'service submodule' in '${updatedApplicationDescriptorFile.getPath()}'.")
			}
			applicationDescriptorUtils.writeApplicationDescriptor(updatedApplicationDescriptorFile, applicationDescriptor)

		} else if (referencingCollections.size() > 1) {
			logger.logMessage("\t==> '$file' is called by multiple applications - $referencingCollections")
			
			// just modify the scope to SERVICE 
			logger.logMessage("\t==> Updating usage of Program '$file' to 'service submodule' in '${updatedApplicationDescriptorFile.getPath()}'.")
			applicationDescriptorUtils.appendFileDefinition(applicationDescriptor, sourceGroupName, language, languageProcessor, artifactsType, fileExtension, repositoryPath, file, type, "service submodule")
			referencingCollections.each { consumerCollection ->
				if (!consumerCollection.equals(props.application)) {
					updateConsumerApplicationDescriptor(referencingCollections[0], "binary", applicationDescriptor)
				}
			}
			applicationDescriptorUtils.writeApplicationDescriptor(updatedApplicationDescriptorFile, applicationDescriptor)
			
		} else {
			logger.logMessage("\tThe Program '$file' is not called by any other program.")
			// Just update the usage to 'main'
			applicationDescriptorUtils.appendFileDefinition(applicationDescriptor, sourceGroupName, language, languageProcessor, artifactsType, fileExtension, repositoryPath, file, type, "main")
			applicationDescriptorUtils.writeApplicationDescriptor(updatedApplicationDescriptorFile, applicationDescriptor)
		}
	}
}


/**
 * Parse CLI config
 */
def parseArgs(String[] args) {

	String usage = 'classifyIncludeFiles.groovy [options]'

	def cli = new CliBuilder(usage:usage)
	// required sandbox options
	cli.w(longOpt:'workspace', args:1, 'Absolute path to workspace (root) directory containing all required source directories')
	cli.d(longOpt:'metadatastore', args:1, 'Absolute path to DBB Dependency Metadatastore used by the modeler')
	cli.a(longOpt:'application', args:1, required:true, 'Application  name.')
	cli.c(longOpt:'configurations', args:1, required:false, 'Path of the directory containing Application Configurations YAML files.')
	cli.m(longOpt:'moveFiles', args:0, 'Flag to move files when usage is assessed.')
	cli.l(longOpt:'logFile', args:1, required:false, 'Relative or absolute path to an output log file')

	def opts = cli.parse(args)
	if (!opts) {
		System.exit(1)
	}

	if (opts.w) props.workspace = opts.w
	if (opts.d) props.metadatastore = opts.d
	if (opts.a) props.application = opts.a
	if (opts.c) props.configurationsDirectory = opts.c
	if (opts.m) {
		props.moveFiles = "true"
	} else {
		props.moveFiles = "false"
	}
	if (opts.l) {
		props.logFile = opts.l
	}	
}

/**** updateConsumerApplicationDescriptor - Update the Application Descriptor of consuming application ****/
def updateConsumerApplicationDescriptor(consumer, dependencyType, providerApplicationDescriptor) {
	// update consumer applications
	def consumerApplicationDescriptor
	// determine which YAML file to use
	consumerApplicationDescriptorFile = new File("${props.workspace}/${consumer}/applicationDescriptor.yml")
	if (consumerApplicationDescriptorFile.exists()) { // update the Application Descriptor that already exists in the Application repository
		consumerApplicationDescriptor = applicationDescriptorUtils.readApplicationDescriptor(consumerApplicationDescriptorFile)
	} else { // Start from the original Application Descriptor created by the extraction phase
		originalConsumerApplicationDescriptorFile = new File("${props.configurationsDirectory}/${consumer}.yml")
		if (originalConsumerApplicationDescriptorFile.exists()) {
			Files.copy(originalConsumerApplicationDescriptorFile.toPath(), consumerApplicationDescriptorFile.toPath(), REPLACE_EXISTING, COPY_ATTRIBUTES)
			consumerApplicationDescriptor = applicationDescriptorUtils.readApplicationDescriptor(consumerApplicationDescriptorFile)
		} else {
			logger.logMessage("*! [WARNING] Application Descriptor file '${originalConsumerApplicationDescriptorFile.getPath()}' was not found. Skipping the configuration update for Include File '${file}'.")
		}
	}
	// Consumer's Application Descriptor file has been found and can be updated
	if (consumerApplicationDescriptor) {						
		applicationDescriptorUtils.addApplicationDependency(consumerApplicationDescriptor, providerApplicationDescriptor.application, "latest", dependencyType)
		applicationDescriptorUtils.writeApplicationDescriptor(consumerApplicationDescriptorFile, consumerApplicationDescriptor)
	}
	// update provider's Application Descriptor
	applicationDescriptorUtils.addApplicationConsumer(providerApplicationDescriptor, consumer)
}


/**** findImpactedFiles -  method to configure and invoke SearchPathImpactFinder ****/
def findImpactedFiles(String impactSearch, String file) {

	List<String> collections = new ArrayList<String>()
	metadataStore.getCollections().each{ collection ->
		collections.add(collection.getName())
	}
	def finder = new SearchPathImpactFinder(impactSearch, collections)
	// Find all files impacted by the changed file
	impacts = finder.findImpactedFiles(file, props.workspace)
	return impacts
}

/**** Copies a relative source member to the relative target directory. ****/
def copyFileToApplicationFolder(String file, String sourceApplication, String targetApplication) {
	targetFilePath = computeTargetFilePath(file, sourceApplication, targetApplication)
	Path source = Paths.get("${props.workspace}", file)
	def target = Paths.get("${props.workspace}", targetFilePath)
	def targetDir = target.getParent()
	File targetDirFile = new File(targetDir.toString())
	if (!targetDirFile.exists()) targetDirFile.mkdirs()
	if (source.toFile().exists() && source.toString() != target.toString()) {
		Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		return target.toString()
	}
}

/**** Initialize additional parameters ****/
def initScriptParameters() {
	// Settings

	String applicationFolder = "${props.workspace}/${props.application}"

	// application folder
	if (new File(applicationFolder).exists()){
		props.applicationDir = applicationFolder
	} else {
		logger.logMessage("*! [ERROR] Application Directory $applicationFolder does not exist. Exiting.")
		System.exit(1)
	}
	
	originalApplicationDescriptorFile = new File("${props.configurationsDirectory}/${props.application}.yml")
	updatedApplicationDescriptorFile = new File("${props.workspace}/${props.application}/applicationDescriptor.yml")
	// determine which YAML file to use
	if (updatedApplicationDescriptorFile.exists()) { // update the Application Descriptor that already exists in the Application repository
		applicationDescriptor = applicationDescriptorUtils.readApplicationDescriptor(updatedApplicationDescriptorFile)
	} else { // Start from the original Application Descriptor created by the extraction phase
		if (originalApplicationDescriptorFile.exists()) {
			Files.copy(originalApplicationDescriptorFile.toPath(), updatedApplicationDescriptorFile.toPath(), REPLACE_EXISTING)
			applicationDescriptor = applicationDescriptorUtils.readApplicationDescriptor(updatedApplicationDescriptorFile)
		} else {
			logger.logMessage("*! [ERROR] Application Descriptor file '${originalApplicationDescriptorFile.getPath()}' was not found. Exiting.")
			System.exit(1)
		}
	}
}

def computeTargetFilePath(String file, String sourceApplication, String targetApplication) {
	def filenameSegments = file.split('/')
	ArrayList<String> targetFilename = new ArrayList<String>()
	filenameSegments.each() { filenameSegment ->
		if (filenameSegment.equals(sourceApplication)) {
			targetFilename.add(targetApplication)
		} else {
			targetFilename.add(filenameSegment)
		}
	}
	return targetFilename.join('/')
}

def updateMappingFiles(String configurationsDirectory, String sourceApplication, String targetApplication, String file) {
    File sourceApplicationMappingFile = new File("${configurationsDirectory}/${sourceApplication}.mapping")
    File targetApplicationMappingFile = new File("${configurationsDirectory}/${targetApplication}.mapping")
    if (!sourceApplicationMappingFile.exists()) {
        logger.logMessage("*! [ERROR] Couldn't find the mapping file '${sourceApplication}.mapping'") 
    } else {
        if (!targetApplicationMappingFile.exists()) {
            logger.logMessage("*! [ERROR] Couldn't find the mapping file '${targetApplication}.mapping'")
        } else {    
            try {
                File newSourceApplicationMappingFile = new File(sourceApplication + ".mapping.new")
                newSourceApplicationMappingFile.createNewFile()
                boolean append = true
                BufferedReader sourceApplicationMappingReader = new BufferedReader(new FileReader(sourceApplicationMappingFile))
                BufferedWriter targetApplicationMappingWriter = new BufferedWriter(new FileWriter(targetApplicationMappingFile, append))
                BufferedWriter newSourceApplicationMappingWriter = new BufferedWriter(new FileWriter(newSourceApplicationMappingFile, append))
                String line;
                while((line = sourceApplicationMappingReader.readLine()) != null) {
					def lineSegments = line.split(' ')
                    if (lineSegments[1].equals(file)) {
						lineSegments[1] = computeTargetFilePath(lineSegments[1], sourceApplication, targetApplication)
						line = String.join(' ', lineSegments)
                        targetApplicationMappingWriter.write(line + "\n")
                    } else {
                        newSourceApplicationMappingWriter.write(line + "\n")
                    }
                }
                targetApplicationMappingWriter.close()
                newSourceApplicationMappingWriter.close()
                sourceApplicationMappingReader.close()
                sourceApplicationMappingFile.delete()
                Files.move(newSourceApplicationMappingFile.toPath(), sourceApplicationMappingFile.toPath())
            }
            catch (IOException e) {
                e.printStackTrace()
            }
        }
    }
}