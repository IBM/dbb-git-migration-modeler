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
import groovy.util.*
import groovy.cli.commons.*

@Field def metadataStore
@Field Properties configuration = new Properties() // Db2 connection
@Field def logger = loadScript(new File("logger.groovy"))
@Field def defaultBuildGroup="dbb_default"

// Initialization if called from the command line
parseArgs(args)

logger.close()

/***
 * Internal methods interacting with the DBB Metadatatore
 */

def deleteBuildGroup(String buildGroupName) {
	if (metadataStore) {
		if (metadataStore.buildGroupExists(buildGroupName)) {
			metadataStore.deleteBuildGroup(buildGroupName)
		}
	}
}

def createCollection(String buildGroupName, String collectionName) {
	if (metadataStore) {
		BuildGroup buildGroup
		if (!metadataStore.buildGroupExists(buildGroupName)) {
			buildGroup = metadataStore.createBuildGroup(buildGroupName)
		} else {
			buildGroup = metadataStore.getBuildGroup(buildGroupName)
		}
		if (buildGroup.collectionExists(collectionName)) {
			buildGroup.deleteCollection(collectionName)
		}
		return buildGroup.createCollection(collectionName)
	} else {
		return null
	}
}

def setCollectionOwner(String buildGroupName, String collectionName, String owner) {
	if (metadataStore) {
		if (metadataStore.buildGroupExists(buildGroupName)) {
			BuildGroup buildGroup = metadataStore.getBuildGroup(buildGroupName)
			if (buildGroup.collectionExists(collectionName)) {
				buildGroup.getCollection(collectionName).setOwner(owner)
			}
		}
	}
}

def getBuildGroups() {
	return metadataStore.getBuildGroups()
}

def getBuildGroup(String buildGroupName) {
	return metadataStore.getBuildGroup(buildGroupName)
}


/***
 * Primary method when included into a groovy processes
 */

def moveLogicalFile(String workspace, String file, String sourceBuildGroupName, String sourceCollectionName, String targetFilePath, String targetBuildGroupName, String targetCollectionName) {

	def successful = true
	def logicalFile

	// delete logical file from source collection
	successful = deleteLogicalFile(file, sourceBuildGroupName, sourceCollectionName)

	// scan file
	if (successful.toBoolean()) {
		logicalFile = scanFile(workspace, targetFilePath)
	}

	// add file to target buildgroup
	if (successful.toBoolean() && logicalFile) {
		addLogicalFile(logicalFile, targetBuildGroupName, targetCollectionName)
	}
}

def deleteLogicalFile(String file, String sourceBuildGroupName,  String sourceCollectionName) {
	sourceBuildGroup = metadataStore.getBuildGroup(sourceBuildGroupName)
	if (sourceBuildGroup) {
		sourceCollection = sourceBuildGroup.getCollection(sourceCollectionName)
		if (sourceCollection){
			if (sourceCollection.getLogicalFile(file)){
				sourceCollection.deleteLogicalFile(file)
				logger.logMessage("\t==> Removed '$file' from collection '$sourceCollectionName' in buildgroup '$sourceBuildGroupName' .")
				return true
			} else {
				logger.logMessage("\t*! [WARNING] The source collection '$sourceCollectionName' does not contain a logical file for '$file' .")
			}
		} else {
			logger.logMessage("\t*! [ERROR] The source collection '$sourceCollectionName' in buildgroup '$sourceBuildGroupName' could not be found .")
		}
	} else {
		logger.logMessage("\t*! [ERROR] The source buildgroup '$sourceBuildGroupName' could not be found .")
	}

	return false
}

def addLogicalFile(LogicalFile file, String targetBuildGroupName,  String targetCollectionName) {
	targetBuildGroup = metadataStore.getBuildGroup(targetBuildGroupName)

	if (targetBuildGroup) {
		targetCollection = targetBuildGroup.getCollection(targetCollectionName)
		if (targetCollection){
			targetCollection.addLogicalFile(file)
			logger.logMessage("\t==> Saved scanned file '${file.getFile()}' to buildgroup '$targetBuildGroupName'.")

			return true
		} else {
			logger.logMessage("\t*! [ERROR] The source collection '$targetCollectionName' in buildgroup '$targetBuildGroupName' could not be found.")
		}
	} else {
		logger.logMessage("\t*! [ERROR] The source buildgroup '$targetBuildGroupName' could not be found .")
	}
}

def scanFile(String workspace, String file) {
	LogicalFile logicalFile = null
	DependencyScanner scanner = new DependencyScanner()
	// Enabling Control Transfer flag in DBB Scanner
	scanner.setCollectControlTransfers("true")
	//logger.logMessage("\t*! [INFO] Scanning file $file ")
	try {
		logicalFile = scanner.scan(file, workspace)
	} catch (Exception e) {
		logger.logMessage("\t*! [ERROR] Something went wrong when scanning the file '$file'.")
		logger.logMessage(e.getMessage())
	}
	return logicalFile
}

/***  
 * Various ACTIONS supported for the CLI invocation
 */

def initMetadatastoreConnection() {

	if (configuration.DBB_MODELER_METADATASTORE_TYPE.equals("file")) {
		initializeFileMetadataStore("${configuration.DBB_MODELER_FILE_METADATA_STORE_DIR}")
	} else {
		Properties db2ConnectionProps = new Properties()
		db2ConnectionProps.load(new FileInputStream(configuration.DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE))
		initializeDb2MetadataStoreWithPasswordFile("${configuration.DBB_MODELER_DB2_METADATASTORE_JDBC_ID}", new File(configuration.DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE), db2ConnectionProps)
	}
}

def initializeFileMetadataStore(String fileMetadataStoreLocation) {
	metadataStore = MetadataStoreFactory.createFileMetadataStore(fileMetadataStoreLocation)
}

def initializeDb2MetadataStoreWithPasswordFile(String db2User, File db2PasswordFile, Properties db2ConnectionProps) {
	metadataStore = MetadataStoreFactory.createDb2MetadataStore(db2User, db2PasswordFile, db2ConnectionProps)
}

def verifyDBBMetadatastoreConnectivity() {

	logger.logMessage("** Verifying DBB Metadatastore connection and permissions");
	Collection collection = createCollection("DummyCollection-main", "DummyCollection-main")
	if (collection) {
		logger.logMessage("** Successfully created the Dummy Collection and Build Group.");
	}
	deleteBuildGroup("DummyCollection-main")
	logger.logMessage("** Successfully deleted the Dummy Collection and Build Group.")
	logger.logMessage("** DBB MetadataStore operations successfully executed. The DBB Git Migration Modeler can be used with the provided configuration!")
}


// Delete existing build group
def deleteExistingBuildGroup(String buildGroup) {

	// for zBuilder
	logger.logMessage("** Deleting DBB BuildGroup ${buildGroup}");
	deleteBuildGroup("${buildGroup}")

	// for zAppBuild
	logger.logMessage("** Deleting legacy collections in DBB BuildGroup ${defaultBuildGroup}");

	defaultBG = getBuildGroup(defaultBuildGroup)
	if (defaultBG) {
		defaultBG.getCollections().each { collection ->
			if (collection.getName().contains("${buildGroup}")) {
				logger.logMessage("**  Delete collection ${collection.getName()} from build-group ${defaultBuildGroup}");
				defaultBG.deleteCollection(collection)
			}
		}
	}
}

// Set Collection Owners
def updateBuildGroupOwner(String buildGroup, String buildGroupOwner) {

	// the buildGroups are used for zBuilder
	logger.logMessage("** Update owner of DBB collections in DBB BuildGroup ${buildGroup} to ${buildGroupOwner}");
	BuildGroup bG = getBuildGroup("${buildGroup}")
	if (bG) {
		bG.getCollections().each { collection ->
			logger.logMessage("**  Set owner of collection ${collection.getName()} in build-group ${buildGroup} to ${buildGroupOwner}");
			collection.setOwner(buildGroupOwner)
		}
	}

	logger.logMessage("** Update owner of DBB legacy collections in DBB BuildGroup ${defaultBuildGroup} to ${buildGroupOwner}");

	// adjust zAppBuild based
	defaultBG = getBuildGroup(defaultBuildGroup)
	if (defaultBG) {
		defaultBG.getCollections().each { collection ->
			if (collection.getName().contains("${buildGroup}")) {
				logger.logMessage("**  Set owner of collection ${collection.getName()} in build-group ${defaultBuildGroup} to ${buildGroupOwner}");
				collection.setOwner(buildGroupOwner)
			}
		}
	}
}


/**
 * Parse CLI config
 */
def parseArgs(String[] args) {

	Properties props = new Properties()
	String usage = 'metadataStoreUtility.groovy [options]'

	def cli = new CliBuilder(usage:usage)
	cli.c(longOpt:'configFile', args:1, required:true, 'Path to the DBB Git Migration Modeler Configuration file (created by the Setup script)')
	cli.v(longOpt:'verify', 'Flag to verify the DBB Metadatastore Connection.')
	cli.d(longOpt:'deleteBuildGroup', 'Flag to delete the DBB Build Group.')
	cli.s(longOpt:'setBuildGroupOwner', args:1, 'Build Group Owner to be set.')
	cli.b(longOpt:'buildGroup', args:1, 'Build Group Reference.')
	cli.l(longOpt:'logFile', args:1, required:false, 'Relative or absolute path to an output log file')

	def opts = cli.parse(args)
	if (!opts) {
		System.exit(1)
	}

	if (opts.l) {
		props.logFile = opts.l
		logger.create(props.logFile)		
	}

	if (opts.b) props.buildGroup = opts.b
	if (opts.o) props.buildGroupOwner = opts.o

	// Action flags including assertions
	if (opts.v) props.verify = true
	if (opts.d) {
		props.deleteBuildGroup = true
		if (!props.buildGroup) {
			println("*! [ERROR] The Build Group to delete was not specified with the '--buildGroup' option. Exiting.")
			System.exit(1)
		}
	}
	if (opts.s) {
		props.setBuildGroupOwner = opts.s
		if (!props.buildGroup) {
			println("*! [ERROR] The Build Group for which to set the owner was not specified with the '--buildGroup' option. Exiting.")
			System.exit(1)
		}
	}

	if (opts.c) {
		props.configurationFilePath = opts.c
		File configurationFile = new File(props.configurationFilePath)
		if (!configurationFile.exists()) {
			println("*! [ERROR] The DBB Git Migration Modeler Configuration file '${opts.c}' does not exist. Exiting.")
			System.exit(1)
		} else {
			configurationFile.withReader() { reader ->
				configuration.load(reader)
			}
		}
	} else {
		println("*! [ERROR] The path to the DBB Git Migration Modeler Configuration file was not specified ('-c/--configFile' parameter). Exiting.")
		System.exit(1)
	}

	if (configuration.DBB_MODELER_METADATASTORE_TYPE) {
		if (configuration.DBB_MODELER_METADATASTORE_TYPE.equals("file")) {
			if (!configuration.DBB_MODELER_FILE_METADATA_STORE_DIR) {
				println("*! [ERROR] Missing Location for the File-based MetadataStore. Exiting.")
				System.exit(1)
			}
		} else if (configuration.DBB_MODELER_METADATASTORE_TYPE.equals("db2")) {
			if (!configuration.DBB_MODELER_DB2_METADATASTORE_JDBC_ID) {
				println("*! [ERROR] Missing User ID for Db2 MetadataStore connection. Exiting.")
				System.exit(1)
			}

			if (!configuration.DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE) {
				println("*! [ERROR] Missing Path to the Db2 Connection configuration file for Db2 MetadataStore connection. Exiting.")
				System.exit(1)
			} else {
				File db2ConfigFile = new File(configuration.DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE)
				if (!db2ConfigFile.exists()) {
					println("*! [ERROR] The Db2 Connection configuration file for Db2 MetadataStore connection '${props.DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE}' does not exist. Exiting.")
					System.exit(1)
				}
			}

			// Checks for correct configuration about MetadataStore
			if (configuration.DBB_MODELER_DB2_METADATASTORE_JDBC_ID && configuration.DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE) {
				if (!configuration.DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE) {
					println("*! [ERROR] Missing Password File for Db2 MetadataStore connection. Exiting.")
					System.exit(1)
				}
			}
		}
	} else {
		println("*! [ERROR] The type of MetadataStore (file or db2) must be specified in the DBB Git Migration Modeler Configuration file. Exiting.");
		System.exit(1)
	}

	// Print parms
	println("** Script configuration:")
	props.each { k,v->
		println "   $k -> $v"
	}

	// initialize metadatatstore to establish connection to either file or db2 metadatastore
	initMetadatastoreConnection()

	// Evaluate actions
	if (props.verify && props.verify.toBoolean()) {
		verifyDBBMetadatastoreConnectivity()
	}

	// process --deleteBuildGroup action
	if (props.deleteBuildGroup && props.deleteBuildGroup.toBoolean()) {
		deleteExistingBuildGroup(props.buildGroup)
	}

	// process --setBuildGroupOwner action
	if (props.setBuildGroupOwner) {
		updateBuildGroupOwner(props.buildGroup, props.setBuildGroupOwner)
	}
}