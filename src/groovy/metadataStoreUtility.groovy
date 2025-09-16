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
@Field Properties configuration = new Properties() // Db2 connection
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

if (props.verify) {

	println("** Verifying DBB Metadatastore connection and permissions");
	Collection collection = metadataStoreUtils.createCollection("DummyCollection-main", "DummyCollection-main")
	if (collection) {
		println("** Successfully created the Dummy Collection and Build Group.");
	}
	metadataStoreUtils.deleteBuildGroup("DummyCollection-main")
	println("** Successfully deleted the Dummy Collection and Build Group.")
	println("** DBB MetadataStore operations successfully executed. The DBB Git Migration Modeler can be used with the provided configuration!")
}


// Delete existing build group
if (props.deleteBuildGroup) {

	// for zBuilder
	println("** Deleting DBB BuildGroup ${props.buildGroup}");
	metadataStoreUtils.deleteBuildGroup("${props.buildGroup}")

	// for zAppBuild
	defaultBG = metadataStoreUtils.getBuildGroup("dbb_default")
	defaultBG.getCollections().each { collection ->
		if (collection.getName().contains("${props.buildGroup}")) {
			println("**  Delete collection ${collection.getName()} from build-group dbb_default");
			defaultBG.deleteCollection(collection)
		}
	}
}

// Set Collection Owners
if (props.setBuildGroupOwner) {

	// the buildGroups are used for zBuilder
	println("** Update Build Group owner to  ${props.buildGroupOwner}");
	BuildGroup bG = metadataStoreUtils.getBuildGroup("${props.buildGroup}")
	bG.getCollections().each { collection ->
		println("**  Set owner of collection ${collection.getName()} in build-group ${props.buildGroup} to ${props.buildGroupOwner}");
		collection.setOwner(props.buildGroupOwner)
	}

	// adjust zAppBuild based
	defaultBG = metadataStoreUtils.getBuildGroup("dbb_default")
	defaultBG.getCollections().each { collection ->
		if (collection.getName().contains("${props.buildGroup}")) {
			println("**  Set owner of collection ${collection.getName()} in build-group dbb_default to ${props.buildGroupOwner}");
			collection.setOwner(props.buildGroupOwner)
		}
	}
}


/**
 * Parse CLI config
 */
def parseArgs(String[] args) {

	String usage = 'checkDb2MetadataStore.groovy [options]'

	def cli = new CliBuilder(usage:usage)
	cli.c(longOpt:'configFile', args:1, required:true, 'Path to the DBB Git Migration Modeler Configuration file (created by the Setup script)')
	cli.v(longOpt:'verify', 'Flag to verify the DBB Metadatastore Connection.')
	cli.d(longOpt:'deleteBuildGroup', 'Flag to delete the DBB Build Group.')
	cli.s(longOpt:'setBuildGroupOwner', 'Flag to set the DBB Build Group Owner.')
	cli.b(longOpt:'buildGroup', args:1, 'Build Group Reference.')
	cli.o(longOpt:'buildGroupOwner', args:1, 'Build Group Owner to be set.')
	def opts = cli.parse(args)
	if (!opts) {
		System.exit(1)
	}

	if (opts.b) props.buildGroup = opts.b
	if (opts.o) props.buildGroupOwner = opts.o

	// Action flags including assertions
	if (opts.v) props.verify = true
	if (opts.d) {
		props.deleteBuildGroup = true
		assert (props.buildGroup) : "*! Cli argument (--buildGroup) is required to perform deletion."
	}
	if (opts.s) {
		props.setBuildGroupOwner = true
		assert (props.buildGroup) : "*! Cli argument (--buildGroup) is required to set buildgroup owner."
		assert (props.buildGroupOwner) : "*! Cli argument (--buildGroupOwner) is required to set buildgroup owner."
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