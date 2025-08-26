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


@Field Properties props = new Properties()
@Field Properties configuration = new Properties()
@Field def metadataStoreUtils = loadScript(new File("utils/metadataStoreUtils.groovy"))

// Initialization
parseArgs(args)

// Print parms
println("** Script configuration:")
props.each { k,v->
	println "   $k -> $v"
}

if (configuration.DBB_MODELER_METADATASTORE_TYPE.equals("file")) {
	metadataStoreUtils.initializeFileMetadataStore("${configuration.DBB_MODELER_FILE_METADATA_STORE_DIR}")
} else {
	Properties db2ConnectionProps = new Properties()
	db2ConnectionProps.load(new FileInputStream(configuration.DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE))
	metadataStoreUtils.initializeDb2MetadataStoreWithPasswordFile("${configuration.DBB_MODELER_DB2_METADATASTORE_JDBC_ID}", new File(configuration.DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE), db2ConnectionProps)
}

Collection collection = metadataStoreUtils.createCollection("DummyCollection-main", "DummyCollection-main")
if (collection) {
	println("** Successfully created the Dummy Collection and Build Group.");
}
metadataStoreUtils.deleteBuildGroup("DummyCollection-main")
println("** Successfully deleted the Dummy Collection and Build Group.")
println("** DBB MetadataStore operations successfully executed. The DBB Git Migration Modeler can be used with the provided configuration!") 

/**
 * Parse CLI config
 */
def parseArgs(String[] args) {

	String usage = 'checkDb2MetadataStore.groovy [options]'

	def cli = new CliBuilder(usage:usage)
	cli.c(longOpt:'configFile', args:1, required:true, 'Path to the DBB Git Migration Modeler Configuration file (created by the Setup script)')

	def opts = cli.parse(args)
	if (!opts) {
		System.exit(1)
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
}