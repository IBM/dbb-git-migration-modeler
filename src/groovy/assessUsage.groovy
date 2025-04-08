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

@Field Properties props = new Properties()
@Field def applicationDescriptorUtils = loadScript(new File("utils/applicationDescriptorUtils.groovy"))
@Field def logger = loadScript(new File("utils/logger.groovy"))
@Field def metadataStoreUtils = loadScript(new File("utils/metadataStoreUtils.groovy"))
@Field File originalApplicationDescriptorFile //Original Application Descriptor file in CONFIGS
@Field File updatedApplicationDescriptorFile  //Updated Application Descriptor file in APPLICATIONS
@Field def applicationDescriptor


// Initialization
parseArgs(args)

initScriptParameters()

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
				def impactSearchRule = 	"search:[:COPY]${props.DBB_MODELER_APPLICATION_DIR}/?path=${props.application}/${matchingSource.repositoryPath}/*." + matchingSource.fileExtension + ";**/${matchingSource.repositoryPath}/*."  + matchingSource.fileExtension as String
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
				def impactSearchRule = 	"search:[:CALL]${props.DBB_MODELER_APPLICATION_DIR}/?path=${props.application}/${matchingSource.repositoryPath}/*." + matchingSource.fileExtension + ";**/${matchingSource.repositoryPath}/*."  + matchingSource.fileExtension as String
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
		File sourceFile = new File ("${props.DBB_MODELER_APPLICATION_DIR}/${props.application}/${qualifiedFile}")
		if (sourceFile.exists()) {
			// Obtain impacts
			logger.logMessage("** Analyzing impacted applications for file '${props.application}/${qualifiedFile}'.")
			def impactedFiles = findImpactedFiles(impactSearchRule, props.application + '/' + qualifiedFile)
			
			// Assess impacted files
			if (impactedFiles.size() > 0) 
				logger.logMessage("\tFiles depending on '${repositoryPath}/${file}.${fileExtension}' :")
			
			impactedFiles.each { impactedFile ->
				def referencingCollection = impactedFile.getCollection().getName().replace("-main", "")
				logger.logMessage("\t'${impactedFile.getFile()}' in  Application  '$referencingCollection'")
				referencingCollections.add(referencingCollection)			
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
						// Update the target Application Descriptor 
						originalTargetApplicationDescriptorFile = new File("${props.DBB_MODELER_APPCONFIG_DIR}/${referencingCollections[0]}.yml")
						updatedTargetApplicationDescriptorFile = new File("${props.DBB_MODELER_APPLICATION_DIR}/${referencingCollections[0]}/applicationDescriptor.yml")
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
							// Move the file
							copyFileToApplicationFolder(props.application + '/' + qualifiedFile, props.application, referencingCollections[0], file)
							targetRepositoryPath = computeTargetFilePath(repositoryPath, props.application, referencingCollections[0])
							applicationDescriptorUtils.appendFileDefinition(targetApplicationDescriptor, sourceGroupName, language, languageProcessor, artifactsType, fileExtension, targetRepositoryPath, file, type, "private")
							logger.logMessage("\t==> Adding Include File '$file' with usage 'private' to Application '${referencingCollections[0]}' described in '${updatedTargetApplicationDescriptorFile.getPath()}'.")
							applicationDescriptorUtils.writeApplicationDescriptor(updatedTargetApplicationDescriptorFile, targetApplicationDescriptor)
							// Remove the file for the application
							applicationDescriptorUtils.removeFileDefinition(applicationDescriptor, sourceGroupName, file)
							logger.logMessage("\t==> Removing Include File '$file' from Application '${props.application}' described in '${updatedApplicationDescriptorFile.getPath()}'.")
							// Update application mappings
							updateMappingFiles(props.DBB_MODELER_APPCONFIG_DIR, props.application, referencingCollections[0], props.DBB_MODELER_APPLICATION_DIR + '/' + props.application + '/' + qualifiedFile, file);
						}
					} else {
						// just modify the scope as PUBLIC or SHARED
						def usageLabel = props.application.equals("UNASSIGNED") ? 'shared' : 'public'
						logger.logMessage("\t==> Updating usage of Include File '$file' to '$usageLabel' in '${updatedApplicationDescriptorFile.getPath()}'.")
						applicationDescriptorUtils.appendFileDefinition(applicationDescriptor, sourceGroupName, language, languageProcessor, artifactsType, fileExtension, repositoryPath, file, type, usageLabel)
						
						updateConsumerApplicationDescriptor(referencingCollections[0], "artifactrepository", applicationDescriptor)
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
						updateConsumerApplicationDescriptor(consumerCollection, "artifactrepository", applicationDescriptor)
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
			def referencingCollection = impactedFile.getCollection().getName().replace("-main", "")
			logger.logMessage("\t'${impactedFile.getFile()}' in  Application  '$referencingCollection'")
			referencingCollections.add(referencingCollection)
		}

		// Assess usage when only 1 application reference the file
		if (referencingCollections.size() == 1) {
			logger.logMessage("\t==> '$file' is called from the '${referencingCollections[0]}' application")
		
			// If Program belongs to the scanned application
			if (props.application.equals(referencingCollections[0])) {
				// Just update the usage to INTERNAL
				applicationDescriptorUtils.appendFileDefinition(applicationDescriptor, sourceGroupName, language, languageProcessor, artifactsType, fileExtension, repositoryPath, file, type, "internal submodule")
				logger.logMessage("\t==> Updating usage of Program '$file' to 'internal submodule' in '${updatedApplicationDescriptorFile.getPath()}'.")
			} else { // Only one other application references this Program, so changing the USAGE to SERVICE
				
				applicationDescriptorUtils.appendFileDefinition(applicationDescriptor, sourceGroupName, language, languageProcessor, artifactsType, fileExtension, repositoryPath, file, type, "service submodule")
				logger.logMessage("\t==> Updating usage of Program '$file' to 'service submodule' in '${updatedApplicationDescriptorFile.getPath()}'.")
				
				// Update the target Application Descriptor to add Dependency
				updateConsumerApplicationDescriptor(referencingCollections[0], "artifactrepository", applicationDescriptor)
			}
			applicationDescriptorUtils.writeApplicationDescriptor(updatedApplicationDescriptorFile, applicationDescriptor)

		} else if (referencingCollections.size() > 1) {
			logger.logMessage("\t==> '$file' is called by multiple applications - $referencingCollections")
			
			// just modify the scope to SERVICE 
			logger.logMessage("\t==> Updating usage of Program '$file' to 'service submodule' in '${updatedApplicationDescriptorFile.getPath()}'.")
			applicationDescriptorUtils.appendFileDefinition(applicationDescriptor, sourceGroupName, language, languageProcessor, artifactsType, fileExtension, repositoryPath, file, type, "service submodule")
			referencingCollections.each { consumerCollection ->
				if (!consumerCollection.equals(props.application)) {
					updateConsumerApplicationDescriptor(consumerCollection, "artifactrepository", applicationDescriptor)
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
	Properties configuration = new Properties()
	String usage = 'assessUsage.groovy [options]'
	String header = 'options:'
	def cli = new CliBuilder(usage:usage,header:header)
	cli.a(longOpt:'application', args:1, required:true, 'Application  name.')
	cli.m(longOpt:'moveFiles', args:0, 'Flag to move files when usage is assessed.')
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

	if (opts.m) {
		props.moveFiles = "true"
	} else {
		props.moveFiles = "false"
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
	
	if (configuration.DBB_MODELER_APPCONFIG_DIR) {
		File directory = new File(configuration.DBB_MODELER_APPCONFIG_DIR)
		if (directory.exists()) {
			props.DBB_MODELER_APPCONFIG_DIR = configuration.DBB_MODELER_APPCONFIG_DIR
		} else {
			logger.logMessage("*! [ERROR] The Configurations directory '${configuration.DBB_MODELER_APPCONFIG_DIR}' does not exist. Exiting.")
			System.exit(1)
		}
	} else {
		logger.logMessage("*! [ERROR] The Configurations directory must be specified in the DBB Git Migration Modeler Configuration file. Exiting.")
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
		
	if (configuration.DBB_MODELER_METADATASTORE_TYPE) {
		props.DBB_MODELER_METADATASTORE_TYPE = configuration.DBB_MODELER_METADATASTORE_TYPE
		if (!props.DBB_MODELER_METADATASTORE_TYPE.equals("file") && !props.DBB_MODELER_METADATASTORE_TYPE.equals("db2")) {
			logger.logMessage("*! [ERROR] The type of MetadataStore can only be 'file' or 'db2'. Exiting.")
			System.exit(1)
		} 
	} else {
		logger.logMessage("*! [ERROR] The type of MetadataStore (file or db2) must be specified in the DBB Git Migration Modeler Configuration file. Exiting.")
		System.exit(1)
	}
	
	if (props.DBB_MODELER_METADATASTORE_TYPE.equals("file")) {
		if (configuration.DBB_MODELER_FILE_METADATA_STORE_DIR) {
			File directory = new File(configuration.DBB_MODELER_FILE_METADATA_STORE_DIR)
			if (directory.exists()) {
				props.DBB_MODELER_FILE_METADATA_STORE_DIR = configuration.DBB_MODELER_FILE_METADATA_STORE_DIR
			} else {
				logger.logMessage("*! [ERROR] The location for the File MetadataStore '${configuration.DBB_MODELER_FILE_METADATA_STORE_DIR}' does not exist. Exiting.")
				System.exit(1)
			}
		} else {
			logger.logMessage("*! [ERROR] The location of the File MetadataStore must be specified in the DBB Git Migration Modeler Configuration file. Exiting.")
			System.exit(1)
		} 
	} else if (props.DBB_MODELER_METADATASTORE_TYPE.equals("db2")) {
		if (configuration.DBB_MODELER_DB2_METADATASTORE_JDBC_ID) {
			props.DBB_MODELER_DB2_METADATASTORE_JDBC_ID = configuration.DBB_MODELER_DB2_METADATASTORE_JDBC_ID
		} else {
			logger.logMessage("*! [ERROR] The User ID for Db2 MetadataStore JDBC connection must be specified in the DBB Git Migration Modeler Configuration file. Exiting.")
			System.exit(1)		 
		}
		if (configuration.DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE) {
			File file = new File(configuration.DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE)
			if (file.exists()) {
				props.DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE = configuration.DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE
			} else {
				logger.logMessage("*! [ERROR] The Db2 Connection configuration file for Db2 MetadataStore JDBC connection '${configuration.DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE}' does not exist. Exiting.")
				System.exit(1)		 
			}
		} else {
			logger.logMessage("*! [ERROR] The path to the Db2 Connection configuration file for Db2 MetadataStore JDBC connection must be specified in the DBB Git Migration Modeler Configuration file. Exiting.")
			System.exit(1)		 
		}
	
		if (!configuration.DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORD && !configuration.DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE) {
			logger.logMessage("*! [ERROR] Either the Password or the Password File for Db2 Metadatastore JDBC connection must be specified in the DBB Git Migration Modeler Configuration file. Exiting.")
			System.exit(1)		 
		} else {
			props.DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORD = configuration.DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORD
			props.DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE = configuration.DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE			
		}
	} else {
		logger.logMessage("*! [ERROR] The type of MetadataStore (file or db2) must be specified in the DBB Git Migration Modeler Configuration file. Exiting.")
		System.exit(1)
	}

	logger.logMessage("** Script configuration:")
	props.each() { k, v ->
		logger.logMessage("\t$k -> $v")
	}	
}

/**** updateConsumerApplicationDescriptor - Update the Application Descriptor of consuming application ****/
def updateConsumerApplicationDescriptor(consumer, dependencyType, providerApplicationDescriptor) {
	// update consumer applications
	def consumerApplicationDescriptor
	// determine which YAML file to use
	consumerApplicationDescriptorFile = new File("${props.DBB_MODELER_APPLICATION_DIR}/${consumer}/applicationDescriptor.yml")
	if (consumerApplicationDescriptorFile.exists()) { // update the Application Descriptor that already exists in the Application repository
		consumerApplicationDescriptor = applicationDescriptorUtils.readApplicationDescriptor(consumerApplicationDescriptorFile)
	} else { // Start from the original Application Descriptor created by the extraction phase
		originalConsumerApplicationDescriptorFile = new File("${props.DBB_MODELER_APPCONFIG_DIR}/${consumer}.yml")
		if (originalConsumerApplicationDescriptorFile.exists()) {
			Files.copy(originalConsumerApplicationDescriptorFile.toPath(), consumerApplicationDescriptorFile.toPath(), REPLACE_EXISTING, COPY_ATTRIBUTES)
			consumerApplicationDescriptor = applicationDescriptorUtils.readApplicationDescriptor(consumerApplicationDescriptorFile)
		} else {
			logger.logMessage("*! [WARNING] Application Descriptor file '${originalConsumerApplicationDescriptorFile.getPath()}' was not found. Skipping the configuration update for Application '${consumer}'.")
		}
	}
	// Consumer's Application Descriptor file has been found and can be updated
	if (consumerApplicationDescriptor) {
		logger.logMessage("Adding ${providerApplicationDescriptor.application} as dependency for ${consumerApplicationDescriptor.application}")
		applicationDescriptorUtils.addApplicationDependency(consumerApplicationDescriptor, providerApplicationDescriptor.application, "latest", dependencyType)
		applicationDescriptorUtils.writeApplicationDescriptor(consumerApplicationDescriptorFile, consumerApplicationDescriptor)
	}
	// update provider's Application Descriptor
	applicationDescriptorUtils.addApplicationConsumer(providerApplicationDescriptor, consumer)
}


/**** findImpactedFiles -  method to configure and invoke SearchPathImpactFinder ****/
def findImpactedFiles(String impactSearch, String file) {
	HashSet<ImpactFile> allImpacts = new HashSet<ImpactFile>()
	metadataStoreUtils.getBuildGroups().each { buildGroup ->
		if (!buildGroup.getName().equals("dbb_default")) {
			List<String> collections = new ArrayList<String>()
			buildGroup.getCollections().each { collection ->
				collections.add(collection.getName())
			}
			if (collections) {
				def finder = new SearchPathImpactFinder(impactSearch, buildGroup.getName(), collections)
				// Find all files impacted by the changed file
				if (finder) {
					impacts = finder.findImpactedFiles(file, props.DBB_MODELER_APPLICATION_DIR)
					if (impacts) {
						allImpacts.addAll(impacts)
					}
				}
			}
		}
	}
	return allImpacts
}

/**** Copies a relative source member to the relative target directory. ****/
def copyFileToApplicationFolder(String file, String sourceApplication, String targetApplication, String shortFile) {
	targetFilePath = computeTargetFilePath(file, sourceApplication, targetApplication)
	Path source = Paths.get("${props.DBB_MODELER_APPLICATION_DIR}", file)
	def target = Paths.get("${props.DBB_MODELER_APPLICATION_DIR}", targetFilePath)
	def targetDir = target.getParent()
	File targetDirFile = new File(targetDir.toString())
	if (!targetDirFile.exists()) targetDirFile.mkdirs()
	if (source.toFile().exists() && source.toString() != target.toString()) {
		Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		logger.logMessage("\t==> Moving Include File '$shortFile' to '${target.toString()}' in Application '${targetApplication}'.")
	}
}

/**** Initialize additional parameters ****/
def initScriptParameters() {
	// Settings
	String applicationFolder = "${props.DBB_MODELER_APPLICATION_DIR}/${props.application}"
	if (new File(applicationFolder).exists()){
		props.applicationDir = applicationFolder
	} else {
		logger.logMessage("*! [ERROR] The Application Directory '$applicationFolder' does not exist. Exiting.")
		System.exit(1)
	}

	if (props.DBB_MODELER_FILE_METADATA_STORE_DIR) {	
		metadataStoreUtils.initializeFileMetadataStore("${props.DBB_MODELER_FILE_METADATA_STORE_DIR}")
	} else {
		File db2ConnectionConfigurationFile = new File(props.DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE)
		Properties db2ConnectionProps = new Properties()
		db2ConnectionProps.load(new FileInputStream(db2ConnectionConfigurationFile))
		// Call correct Db2 MetadataStore constructor
		if (props.DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORD) {
			metadataStoreUtils.initializeDb2MetadataStore("${props.DBB_MODELER_DB2_METADATASTORE_JDBC_ID}", "${props.DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORD}", db2ConnectionProps)
		} else if (props.DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE) {
			metadataStoreUtils.initializeDb2MetadataStore("${props.DBB_MODELER_DB2_METADATASTORE_JDBC_ID}", new File(props.DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE), db2ConnectionProps)
		}
	}
	
	originalApplicationDescriptorFile = new File("${props.DBB_MODELER_APPCONFIG_DIR}/${props.application}.yml")
	updatedApplicationDescriptorFile = new File("${props.DBB_MODELER_APPLICATION_DIR}/${props.application}/applicationDescriptor.yml")
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

def updateMappingFiles(String configurationsDirectory, String sourceApplication, String targetApplication, String file, String shortFile) {
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
                logger.logMessage("\t==> Updating Migration Mapping files for Applications '${sourceApplication}' and '${targetApplication}' for file '${shortFile}'.")                
            }
            catch (IOException e) {
                e.printStackTrace()
            }
        }
    }
}
