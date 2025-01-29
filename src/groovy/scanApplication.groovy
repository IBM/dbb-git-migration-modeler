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


@Field BuildProperties props = BuildProperties.getInstance()
@Field def logger = loadScript(new File("utils/logger.groovy"))
@Field def metadataStoreUtils = loadScript(new File("utils/metadataStoreUtils.groovy"))

/******** Enabling Control Transfer flag */
props.put("dbb.DependencyScanner.controlTransfers", "true")

// Initialization
parseArgs(args)

// Print parms
println("** Script configuration:")
props.each { k,v->
	println "   $k -> $v"
}

initScriptParameters()

// Handle log file
if (props.logFile) {
	logger.create(props.logFile)
}


logger.logMessage ("** Scanning the files.")
Set<String> appFiles = getFileList()
List<LogicalFile> logicalFiles = scanFiles(appFiles)

logger.logMessage ("** Storing results in the '${props.application}-main' DBB Collection.")
// Manage Build Groups and Collections

metadataStoreUtils.deleteBuildGroup("${props.application}-main")
Collection collection = metadataStoreUtils.createCollection("${props.application}-main", "${props.application}-main")
// store results
collection.addLogicalFiles(logicalFiles)
if (props.dbbOwner) {
	println("** Setting collection owner to ${props.dbbOwner}")
	metadataStoreUtils.setCollectionOwner("${props.application}-main", "${props.application}-main", props.dbbOwner)
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
		logger.logMessage "\t Scanning file $file "
		try {
			logicalFile = scanner.scan(file, props.workspace)
			logicalFiles.add(logicalFile)
		} catch (Exception e) {
			logger.logMessage "\t\tSomething went wrong when scanning this file."
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
	cli.w(longOpt:'workspace', args:1, required:true, 'Absolute path to workspace (root) directory containing all required source directories')
	cli.f(longOpt:'file-metadatastore', args:1, required:false, 'Absolute path to the folder containing the DBB File MetadataStore')
	cli.du(longOpt:'db2-user', args:1, required:false, 'Db2 User ID for DBB Db2 MetadataStore')
	cli.dp(longOpt:'db2-password', args:1, required:false, 'Db2 User\'s Password for DBB Db2 MetadataStore')
	cli.dpf(longOpt:'db2-password-file', args:1, required:false, 'Absolute path to the Db2 Password file for DBB Db2 MetadataStore')
	cli.dc(longOpt:'db2-config', args:1, required:false, 'Absolute path to the Db2 connection configuration file')
	cli.do(longOpt:'dbb-owner', args:1, required:false, 'Owner of the DBB MetadataStore collections')
	cli.a(longOpt:'application', args:1, required:true, 'Application name ')
	cli.l(longOpt:'logFile', args:1, required:false, 'Relative or absolute path to an output log file')

	def opts = cli.parse(args)
	if (!opts) {
		System.exit(1)
	}

	if (opts.w) props.workspace = opts.w
	if (opts.f) props.fileMetadatastore = opts.f
	if (opts.du) props.db2User = opts.du
	if (opts.dp) props.db2Password = opts.dp
	if (opts.dpf) props.db2PasswordFile = opts.dpf
	if (opts.dc) props.db2ConfigFile = opts.dc
	if (opts.do) props.dbbOwner = opts.do
	if (opts.a) props.application = opts.a
	if (opts.l) props.logFile = opts.l

	// Checks for correct configuration about MetadataStore
	if (!props.fileMetadatastore && (!props.db2User || !props.db2ConfigFile)) {
		logger.logMessage("*! [ERROR] Incomplete MetadataStore configuration. Either the File MetadataStore parameter (--file-metadatastore) or the Db2 Metadatastore parameters (--db2-user and --db2-config) are missing. Exiting.")
		System.exit(1)		 
	} else {
		if (props.db2User && props.db2ConfigFile) {
			if (!props.db2Password && !props.db2PasswordFile) {
				logger.logMessage("*! [ERROR] Missing Password and Password File for Db2 Metadatastore connection. Exiting.")
				System.exit(1)		 
			}
		}		
	}	
}

/* 
 * init additional parameters
 */
def initScriptParameters() {
	// Settings
	String applicationFolder = "${props.workspace}/${props.application}"
	if (new File(applicationFolder).exists()){
		props.applicationDir = applicationFolder
	} else {
		logger.logMessage("*! [ERROR] Application Directory '$applicationFolder' does not exist. Exiting.")
		System.exit(1)
	}

	if (props.fileMetadataStore) {	
		metadataStoreUtils.initializeFileMetadataStore("${props.fileMetadatastore}")
	} else {
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
}

/*
 * relativizePath - converts an absolute path to a relative path from the workspace directory
 */
def relativizePath(String path) {
	if (!path.startsWith('/'))
		return path
	String relPath = new File(props.workspace).toURI().relativize(new File(path.trim()).toURI()).getPath()
	// Directories have '/' added to the end.  Lets remove it.
	if (relPath.endsWith('/'))
		relPath = relPath.take(relPath.length()-1)
	return relPath
}