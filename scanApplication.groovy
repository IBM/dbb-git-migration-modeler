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
@Field MetadataStore metadataStore
@Field def logger = loadScript(new File("utils/logger.groovy"))

/******** Enabling Control Transfer flag */
props.put("dbb.DependencyScanner.controlTransfers", "true")


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

initScriptParameters()

logger.logMessage ("** Scanning the files.")
Set<String> appFiles = getFileList()
List<LogicalFile> logicalFiles = scanFiles(appFiles)

logger.logMessage ("** Storing results in the '${props.application}' DBB Collection.")
// manage collection
if (!metadataStore.collectionExists(props.application)) {
	// create collection
	metadataStore.createCollection(props.application)
} else {
	// reset collection
	metadataStore.deleteCollection(props.application)
	metadataStore.createCollection(props.application)
}
// store results
metadataStore.getCollection(props.application).addLogicalFiles(logicalFiles)

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
		logicalFile = scanner.scan(file, props.workspace)
		logicalFiles.add(logicalFile)
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
	cli.w(longOpt:'workspace', args:1, 'Absolute path to workspace (root) directory containing all required source directories')
	cli.a(longOpt:'application', args:1, required:true, 'Application  name ')
	cli.l(longOpt:'logFile', args:1, required:false, 'Relative or absolute path to an output log file')

	def opts = cli.parse(args)
	if (!opts) {
		System.exit(1)
	}

	if (opts.w) props.workspace = opts.w
	if (opts.a) props.application = opts.a
	if (opts.l) {
		props.logFile = opts.l
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
		logger.logMessage ("!* Application Directory ${props.applicationDir} does not exist.")
		System.exit(1)
	}
	
	metadataStore = MetadataStoreFactory.createFileMetadataStore("${props.workspace}/.dbb")
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