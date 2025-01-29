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


@Field BuildProperties props = BuildProperties.getInstance()
@Field def metadataStoreUtils = loadScript(new File("utils/metadataStoreUtils.groovy"))

// Initialization
parseArgs(args)

// Print parms
println("** Script configuration:")
props.each { k,v->
	println "   $k -> $v"
}

initScriptParameters()

Collection collection = metadataStoreUtils.createCollection("DummyCollection-main", "DummyCollection-main")
if (collection) {
	println("** Successfully created the Dummy Collection and Build Group.");
}
metadataStoreUtils.deleteBuildGroup("DummyCollection-main")
println("** Successfully deleted the Dummy Collection and Build Group.")
println("** Db2 operations successfully executed. The DBB Git Migration Modeler can be used with the provided configuration!") 

/**
 * Parse CLI config
 */
def parseArgs(String[] args) {

	String usage = 'checkDb2MetadataStore.groovy [options]'

	def cli = new CliBuilder(usage:usage)
	cli.du(longOpt:'db2-user', args:1, required:false, 'Db2 User ID for DBB Db2 MetadataStore')
	cli.dp(longOpt:'db2-password', args:1, required:false, 'Db2 User\'s Password for DBB Db2 MetadataStore')
	cli.dpf(longOpt:'db2-password-file', args:1, required:false, 'Absolute path to the Db2 Password file for DBB Db2 MetadataStore')
	cli.dc(longOpt:'db2-config', args:1, required:false, 'Absolute path to the Db2 Connection configuration file')

	def opts = cli.parse(args)
	if (!opts) {
		System.exit(1)
	}

	if (opts.du) props.db2User = opts.du
	if (opts.dp) props.db2Password = opts.dp
	if (opts.dpf) props.db2PasswordFile = opts.dpf
	if (opts.dc) props.db2ConfigFile = opts.dc

	if (!props.db2User) {
		logger.logMessage("*! [ERROR] Missing User ID for Db2 MetadataStore connection. Exiting.")
		System.exit(1)		 
	}

	if (!props.db2ConfigFile) {
		logger.logMessage("*! [ERROR] Missing Path to the Db2 Connection configuration file for Db2 MetadataStore connection. Exiting.")
		System.exit(1)		 
	}

	// Checks for correct configuration about MetadataStore
	if (props.db2User && props.db2ConfigFile) {
		if (!props.db2Password && !props.db2PasswordFile) {
			logger.logMessage("*! [ERROR] Missing Password and Password File for Db2 Metadatastore connection. Exiting.")
			System.exit(1)		 
		}
	}	
}

/* 
 * init additional parameters
 */
def initScriptParameters() {
	File db2ConnectionConfigurationFile = new File(props.db2ConfigFile)
	if (!db2ConnectionConfigurationFile.exists()){
		logger.logMessage("!* [ERROR] Db2 Connection configuration file '${props.db2ConfigFile}' does not exist. Exiting.")
		System.exit(1)
	} else {
		Properties db2ConnectionProps = new Properties()
		db2ConnectionProps.load(new FileInputStream(db2ConnectionConfigurationFile))
		// Call correct Db2 MetadataStore constructor
		if (props.db2Password) {
			metadataStoreUtils.initializeDb2MetadataStore("${props.db2User}", "${props.db2Password}", db2ConnectionProps)
		} else if (props.db2PasswordFile) {
			metadataStoreUtils.initializeDb2MetadataStore("${props.db2User}", new File(props.db2PasswordFile), db2ConnectionProps)
		}
	}
}