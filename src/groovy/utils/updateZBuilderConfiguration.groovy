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
import groovy.yaml.YamlBuilder
import java.util.Properties;
import java.nio.file.StandardCopyOption;
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import com.ibm.dbb.utils.FileUtils
import java.text.SimpleDateFormat

@Field Properties props = new Properties()
@Field def logger = loadScript(new File("logger.groovy"))

def dbbBuildYAML
/**
 * Processing logic
 */

// Parse arguments from command-line
parseArgs(args)


// Reads the zBuilder dbb-buil.yaml file
def dbbBuildYAMLFilePath = props.DBB_ZBUILDER + "/dbb-build.yaml"
def dbbBuildYAMLFile = new File(dbbBuildYAMLFilePath)
if (!dbbBuildYAMLFile.exists()) {
    logger.logMessage("!* [ERROR] The DBB zBuilder dbb-build.yaml file was not found at the location '$dbbBuildYAMLFilePath'. Exiting.")
    System.exit(1);    
} else {
    def yamlSlurper = new groovy.yaml.YamlSlurper()
    dbbBuildYAML = yamlSlurper.parse(dbbBuildYAMLFile)
}

def date = new Date()
def dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss")
def dbbBuildYAMLBackupFilePath = props.DBB_ZBUILDER + "/dbb-build-backup-${dateFormat.format(date)}.yaml"
Files.copy(Paths.get(dbbBuildYAMLFilePath), Paths.get(dbbBuildYAMLBackupFilePath), StandardCopyOption.COPY_ATTRIBUTES)
FileUtils.setFileTag(dbbBuildYAMLBackupFilePath, FileUtils.getFileTag(dbbBuildYAMLFilePath))

logger.logMessage("** Modifying the DBB zBuilder 'dbb-build.yaml' file located at '$dbbBuildYAMLFilePath'.")
def metadataInitTask = dbbBuildYAML.tasks.find() { task ->
    task.task.equals("MetadataInit")
}

if (!metadataInitTask) {
    metadataInitTask = [ task: "MetadataInit" ]
    dbbBuildYAML.tasks << metadataInitTask
}

if (metadataInitTask.variables) {
    metadataInitTask.variables.clear()
} else {
    metadataInitTask.variables = []
}
metadataInitTask.variables << [ name: "type", value: "${props.DBB_MODELER_METADATASTORE_TYPE}" ]
if (props.DBB_MODELER_METADATASTORE_TYPE.equals("file")) {
    metadataInitTask.variables << [ name: "fileLocation", value: "${props.DBB_MODELER_FILE_METADATA_STORE_DIR}" ]
}

// generating and writing the configuration file
def yamlBuilder = new YamlBuilder()
yamlBuilder {
    version dbbBuildYAML.version
    include dbbBuildYAML.include
    lifecycles dbbBuildYAML.lifecycles
    tasks dbbBuildYAML.tasks
}


dbbBuildYAMLFile.withWriter("UTF-8") { writer ->
    writer.write(yamlBuilder.toString())
}
FileUtils.setFileTag(dbbBuildYAMLFile.getAbsolutePath(), "UTF-8")

logger.logMessage("** The DBB zBuilder 'dbb-build.yaml' file located at '$dbbBuildYAMLFilePath' was successfully modified.")

// close logger file
logger.close()


/*  ==== Utilities ====  */

/* parseArgs: parse arguments provided through CLI */
def parseArgs(String[] args) {
    Properties configuration = new Properties()
    String usage = 'updateZBuilderConfiguration.groovy [options]'
    String header = 'options:'
    def cli = new CliBuilder(usage:usage,header:header);
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

    if (configuration.BUILD_FRAMEWORK) {
        props.BUILD_FRAMEWORK = configuration.BUILD_FRAMEWORK
        println(configuration.BUILD_FRAMEWORK)
        if (!configuration.BUILD_FRAMEWORK.equals("zBuilder") && !configuration.BUILD_FRAMEWORK.equals("zAppBuild")) {
            logger.logMessage("*! [ERROR] The DBB Build Framework can only be 'zBuilder' or 'zAppBuild' The '${configuration.BUILD_FRAMEWORK}' value defined in the DBB Git Migration Modeler Configuration file is invalid. Exiting.")
            System.exit(1)
        }
    } else {
        logger.logMessage("*! [ERROR] The DBB Build Framework must be specified in the DBB Git Migration Modeler Configuration file. Exiting.")
        System.exit(1)
    }


    if (configuration.DBB_ZBUILDER) {
        File directory = new File(configuration.DBB_ZBUILDER)
        if (directory.exists()) {
            props.DBB_ZBUILDER = configuration.DBB_ZBUILDER
        } else {
            logger.logMessage("*! [ERROR] The DBB zBuilder instance '${configuration.DBB_ZBUILDER}' does not exist. Exiting.")
            System.exit(1)
        }
    } else {
        logger.logMessage("*! [ERROR] The DBB zBuilder instance must be specified in the DBB Git Migration Modeler Configuration file. Exiting.")
        System.exit(1)
    }

    if (configuration.DBB_MODELER_METADATASTORE_TYPE) {
        props.DBB_MODELER_METADATASTORE_TYPE = configuration.DBB_MODELER_METADATASTORE_TYPE
        if (!props.DBB_MODELER_METADATASTORE_TYPE.equals("file") && !props.DBB_MODELER_METADATASTORE_TYPE.equals("db2")) {
            logger.logMessage("*! [ERROR] The type of MetadataStore can only be 'file' or 'db2'. Exiting.")
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
        
            if (!configuration.DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE) {
                logger.logMessage("*! [ERROR] The Password File for Db2 Metadatastore JDBC connection must be specified in the DBB Git Migration Modeler Configuration file. Exiting.")
                System.exit(1)       
            } else {
                props.DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE = configuration.DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE           
            }
        } else {
            logger.logMessage("*! [ERROR] The type of MetadataStore (file or db2) must be specified in the DBB Git Migration Modeler Configuration file. Exiting.")
            System.exit(1)
        }
    } else {
        logger.logMessage("*! [ERROR] The type of MetadataStore (file or db2) must be specified in the DBB Git Migration Modeler Configuration file. Exiting.");
        System.exit(1)    
    }


    logger.logMessage("** Script configuration:")
    props.each() { k, v ->
        logger.logMessage("\t$k -> $v")
    }
}