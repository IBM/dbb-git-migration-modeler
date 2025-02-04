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

@Field Properties props = new Properties()
@Field Properties configuration = new Properties()
@Field def logger = loadScript(new File("utils/logger.groovy"))
@Field def metadataStoreUtils = loadScript(new File("utils/metadataStoreUtils.groovy"))

/******** Enabling Control Transfer flag */
props.put("dbb.DependencyScanner.controlTransfers", "true")

// Initialization
parseArgs(args)

initScriptParameters()

logger.logMessage("** Scanning the files.")
Set<String> appFiles = getFileList()
List<LogicalFile> logicalFiles = scanFiles(appFiles)

logger.logMessage("** Storing results in the '${props.application}-main' DBB Collection.")
// Manage Build Groups and Collections

metadataStoreUtils.deleteBuildGroup("${props.application}-main")
Collection collection = metadataStoreUtils.createCollection("${props.application}-main", "${props.application}-main")
// store results
collection.addLogicalFiles(logicalFiles)
if (props.PIPELINE_USER) {
	logger.logMessage("** Setting collection owner to ${props.PIPELINE_USER}")
	metadataStoreUtils.setCollectionOwner("${props.application}-main", "${props.application}-main", props.PIPELINE_USER)
}

logger.close()

/**
 * Get list relative files in the application directory
 */
def getFileList() {
	Set<String> fileSet = new HashSet<String>()

	Files.walk(Paths.get(props.applicationDir)).forEach { filePath ->
		if (Files.isRegularFile(filePath)) {
			relFile = relativizePath(filePath.toString())
			fileSet.add(relFile)
		}
	}
	return fileSet
}

/**
 * 
 */
def scanFiles(fileList) {
	List<LogicalFile> logicalFiles = new ArrayList<LogicalFile>()
	fileList.each{ file ->
		DependencyScanner scanner = new DependencyScanner()
		logger.logMessage("\t Scanning file $file ")
		try {
			logicalFile = scanner.scan(file, props.DBB_MODELER_APPLICATION_DIR)
			logicalFiles.add(logicalFile)
		} catch (Exception e) {
			logger.logMessage("\t*! [ERROR] Something went wrong when scanning the file '$file'.")
			logger.logMessage(e.getMessage())
		}
	}
	return logicalFiles
}

/**
 * Parse CLI config
 */
def parseArgs(String[] args) {

	String usage = 'scanApplication.groovy [options]'

	def cli = new CliBuilder(usage:usage)
	// required sandbox options
	cli.a(longOpt:'application', args:1, required:true, 'Application name')
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
	
	if (configuration.PIPELINE_USER) {
		props.PIPELINE_USER = configuration.PIPELINE_USER
	} else {
		logger.logMessage("*! [ERROR] The Pipeline User (owner of DBB collections) must be specified in the DBB Git Migration Modeler Configuration file. Exiting.")
		System.exit(1)
	}

	logger.logMessage("** Script configuration:")
	props.each() { k, v ->
		logger.logMessage("\t$k -> $v")
	}
}

/* 
 * init additional parameters
 */
def initScriptParameters() {
	// Settings
	String applicationFolder = "${props.DBB_MODELER_APPLICATION_DIR}/${props.application}"
	if (new File(applicationFolder).exists()){
		props.applicationDir = applicationFolder
	} else {
		logger.logMessage("*! [ERROR] Application Directory '$applicationFolder' does not exist. Exiting.")
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
}

/*
 * relativizePath - converts an absolute path to a relative path from the workspace directory
 */
def relativizePath(String path) {
	if (!path.startsWith('/'))
		return path
	String relPath = new File(props.DBB_MODELER_APPLICATION_DIR).toURI().relativize(new File(path.trim()).toURI()).getPath()
	// Directories have '/' added to the end.  Lets remove it.
	if (relPath.endsWith('/'))
		relPath = relPath.take(relPath.length()-1)
	return relPath
}
