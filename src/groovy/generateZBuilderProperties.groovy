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
import com.ibm.dbb.utils.FileUtils

@Field Properties props = new Properties()
@Field def applicationDescriptorUtils = loadScript(new File("utils/applicationDescriptorUtils.groovy"))
@Field def logger = loadScript(new File("utils/logger.groovy"))
@Field File applicationDescriptorFile
@Field def applicationDescriptor

/**
 * Processing logic
 */

// Parse arguments from command-line
parseArgs(args)

def typesConfigurations
// Build the Types Configuration object from Types Configurations file
logger.logMessage("** Reading the Types Configurations definitions from '${props.TYPE_CONFIGURATIONS_FILE}'.")
def typesConfigurationsFile = new File(props.TYPE_CONFIGURATIONS_FILE)
if (!typesConfigurationsFile.exists()) {
    logger.logMessage("!* [ERROR] the Types Configurations file '${props.TYPE_CONFIGURATIONS_FILE}' does not exist. Exiting.")
    System.exit(1);    
} else {
    def yamlSlurper = new groovy.yaml.YamlSlurper()
    typesConfigurations = yamlSlurper.parse(typesConfigurationsFile)
}

// Parses the Application Descriptor File of the application, to retrieve the list of programs
applicationDescriptorFile = new File("${props.DBB_MODELER_APPLICATION_DIR}/${props.application}/applicationDescriptor.yml")
if (applicationDescriptorFile.exists()) {
    applicationDescriptor = applicationDescriptorUtils.readApplicationDescriptor(applicationDescriptorFile)
} else {
    logger.logMessage("!* [ERROR] The Application Descriptor file '${applicationDescriptorFile.getPath()}' does not exist. Exiting.")
    System.exit(1)
}

logger.logMessage("** Gathering the defined types for files.")

// Internal map to collect all information : type <-> list of files mapped to that type
//HashMap<String, ArrayList<String>> typesToFilesMap = new HashMap<String, ArrayList<String>>()
// Internal map to collect all information : files <-> list of types for this file
HashMap<String, HashMap<String, String>> fileToTypesMap = new HashMap<String, ArrayList<String>>()
HashSet<String> typesConfigurationsToCreate = []
HashSet<String> createdTypesConfigurations = []

applicationDescriptor.sources.each { sourceGroup ->
    sourceGroup.files.each { file ->
        if (file.type) {
            def types = file.type.split(",")
            if (types != null && types.size() > 0 && !types[0].equals("UNKNOWN") && !sourceGroup.languageProcessor.isEmpty()) {
                fileToTypesMap.put("${sourceGroup.repositoryPath}/${file.name}.${sourceGroup.fileExtension}", ["task":sourceGroup.languageProcessor, "types":types])
                types.each() { typeToCreate ->    
                    typesConfigurationsToCreate << typeToCreate
                }
            }
        }
    }
}

def applicationDBBAppYaml = [:]
applicationDBBAppYaml.name = props.application
applicationDBBAppYaml.tasks = []    
def applicationDBBAppYamlFolderPath = "${props.DBB_MODELER_APPLICATION_DIR}/${props.application}"

// Path to the zBuilder Configuration folder in the application's folder
def zBuilderConfigurationFolderPath = "${props.DBB_MODELER_APPLICATION_DIR}/${props.application}/config"
File zBuilderConfigurationFolder = new File(zBuilderConfigurationFolderPath)
if (!zBuilderConfigurationFolder.exists()) {
    zBuilderConfigurationFolder.mkdirs()
}

// If there are Types Configurations files to create
if (typesConfigurationsToCreate && typesConfigurationsToCreate.size() > 0) {
    logger.logMessage("** Generating zBuilder language configuration files.")
    typesConfigurationsToCreate.each() { typeToCreate ->
        typeConfiguration = typesConfigurations.typesConfigurations.find() { typeConfiguration ->
            typeConfiguration.typeConfiguration.equals(typeToCreate)
        }
    
        if (typeConfiguration) {
            logger.logMessage("\tType Configuration for type '${typeToCreate}' found in '${props.TYPE_CONFIGURATIONS_FILE}'.")
            def typeConfigurationVariables = []
            typeConfiguration.variables.each() { variable ->
                typeConfigurationVariables << [ "name": variable.name, "value": variable.value ]
            }
            File typeConfigurationYamlFile = new File("${zBuilderConfigurationFolderPath}/${typeToCreate}.yaml")
            def yamlBuilder = new YamlBuilder()
                yamlBuilder {
                config typeConfigurationVariables
            }
            File yamlFileParentFolder = typeConfigurationYamlFile.getParentFile()
            if (!yamlFileParentFolder.exists()) {
                yamlFileParentFolder.mkdirs()
            }                
            
            typeConfigurationYamlFile.withWriter("UTF-8") { writer ->
                writer.write(yamlBuilder.toString())
            }
            FileUtils.setFileTag(typeConfigurationYamlFile.getAbsolutePath(), "UTF-8")
            // We build a set of types that were actually found and created
            // This set will be used when we later process all the files to check if it's necessary
            //   to create the languageConfigurationSource entry
            createdTypesConfigurations << typeToCreate
        } else {
            logger.logMessage("\t[WARNING] No Type Configuration for type '${typeToCreate}' found in '${props.TYPE_CONFIGURATIONS_FILE}'.")
        }
    }
}

if (fileToTypesMap && fileToTypesMap.size() > 0 && createdTypesConfigurations && createdTypesConfigurations.size() > 0) {
    logger.logMessage("** Generating zBuilder Application configuration file.")
    fileToTypesMap.each() { file, info ->
        info.types.each() { type ->
            if (createdTypesConfigurations.contains(type)) {
    			def task = applicationDBBAppYaml.tasks.find() { applicationTask ->
        			applicationTask.task.equals(info.task)
                }
                if (!task) {
                    task = [ "task": info.task, "variables": [] ]
                    applicationDBBAppYaml.tasks << task
                }
                languageConfigurationVariable = task.variables.find() { variable ->
                    variable.name.equals("languageConfigurationSource") &&
                    variable.value.equals("\${APP_DIR}/config/${type}.yaml")
                }
                if (!languageConfigurationVariable) {
                    languageConfigurationVariable = [ "name": "languageConfigurationSource", "value": "\${APP_DIR}/config/${type}.yaml", "forFiles": [] ]        
                }
                languageConfigurationVariable.forFiles << file
                task.variables << languageConfigurationVariable
            }                
        }
    }
} else {
    logger.logMessage("** No Configuration type found for application '${props.application}'.")
}

// generating dependencies path in configuration file

def matchingSourcesGroup = applicationDescriptor.sources.findAll { source ->
    source.artifactsType.equalsIgnoreCase("Program") 
}
matchingSourcesGroup.each() { matchingSourceGroup ->
    
    def impactAnalysisTask = applicationDBBAppYaml.tasks.find() { task ->
        task.task.equals("ImpactAnalysis")
    }
    if (!impactAnalysisTask) {
        impactAnalysisTask = [ "task": "ImpactAnalysis", "variables": [] ]
        applicationDBBAppYaml.tasks << impactAnalysisTask
    }
    def impactQueryPatterns = impactAnalysisTask.variables.find() { variable ->
        variable.name.equals("impactQueryPatterns")
    }
    if (!impactQueryPatterns) {
        impactQueryPatterns = [ "name": "impactQueryPatterns", "value": [] ]
        impactAnalysisTask.variables << impactQueryPatterns
    }
    if (matchingSourceGroup.name.equals("cobol")) {
        def dependencyPattern = [ "languageExt": matchingSourceGroup.fileExtension, "dependencyPatterns": [] ]
        // BMS
        def BMSGroup = applicationDescriptor.sources.find() { sourceGroup ->
            sourceGroup.name.equals("bms")
        }
        if (BMSGroup) {
            dependencyPattern.dependencyPatterns << "\${APP_DIR_NAME}/${BMSGroup.repositoryPath}/*.${BMSGroup.fileExtension}"
        }
        // Cobol Copybook
        def CopyGroup = applicationDescriptor.sources.find() { sourceGroup ->
            sourceGroup.name.equals("copy")
        }
        if (CopyGroup) {
            dependencyPattern.dependencyPatterns << "\${APP_DIR_NAME}/${CopyGroup.repositoryPath}/*.${CopyGroup.fileExtension}"
        }
        // Cobol program
        dependencyPattern.dependencyPatterns << "\${APP_DIR_NAME}/${matchingSourceGroup.repositoryPath}/*.${matchingSourceGroup.fileExtension}"
        impactQueryPatterns.value << dependencyPattern
    }
    if (matchingSourceGroup.name.equals("link")) {
        def dependencyPattern = [ "languageExt": matchingSourceGroup.fileExtension, "dependencyPatterns": [] ]
        // Cobol program
        def CobolGroup = applicationDescriptor.sources.find() { sourceGroup ->
            sourceGroup.name.equals("cobol")
        }
        if (CobolGroup) {
            dependencyPattern.dependencyPatterns << "\${APP_DIR_NAME}/${CobolGroup.repositoryPath}/*.${CobolGroup.fileExtension}"
        }
        impactQueryPatterns.value << dependencyPattern
    }
}

// generating and writing the configuration file
File applicationDBBAppYamlFile = new File("${applicationDBBAppYamlFolderPath}/dbb-app.yaml")
def yamlBuilder = new YamlBuilder()
    yamlBuilder {
    version "1.0.0"
    application applicationDBBAppYaml
}
File yamlFileParentFolder = applicationDBBAppYamlFile.getParentFile()
if (!yamlFileParentFolder.exists()) {
    yamlFileParentFolder.mkdirs()
}                
    
applicationDBBAppYamlFile.withWriter("UTF-8") { writer ->
    writer.write(yamlBuilder.toString())
}
FileUtils.setFileTag(applicationDBBAppYamlFile.getAbsolutePath(), "UTF-8")
logger.logMessage("** Application Configuration file '${applicationDBBAppYamlFile.getAbsolutePath()}' successfully created")

// close logger file
logger.close()


/*  ==== Utilities ====  */

/* parseArgs: parse arguments provided through CLI */
def parseArgs(String[] args) {
    Properties configuration = new Properties()
    String usage = 'generateProperties.groovy [options]'
    String header = 'options:'
    def cli = new CliBuilder(usage:usage,header:header);
    cli.a(longOpt:'application', args:1, required:true, 'Application name.')
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
            logger.logMessage("*! [ERROR] The Application's directory '${configuration.DBB_MODELER_APPLICATION_DIR}' does not exist. Exiting.")
            System.exit(1)
        }
    } else {
        logger.logMessage("*! [ERROR] The Applications directory must be specified in the DBB Git Migration Modeler Configuration file. Exiting.")
        System.exit(1)
    }    

    if (configuration.TYPE_CONFIGURATIONS_FILE) {
        File file = new File(configuration.TYPE_CONFIGURATIONS_FILE)
        if (file.exists()) {
            props.TYPE_CONFIGURATIONS_FILE = configuration.TYPE_CONFIGURATIONS_FILE
        } else {
            logger.logMessage("*! [ERROR] The Types Configurations file '${configuration.TYPE_CONFIGURATIONS_FILE}' does not exist. Exiting.")
            System.exit(1)
        }
    } else {
        logger.logMessage("*! [ERROR] The path to the Types Configurations file must be specified in the DBB Git Migration Modeler Configuration file. Exiting.")
        System.exit(1)
    }

    logger.logMessage("** Script configuration:")
    props.each() { k, v ->
        logger.logMessage("\t$k -> $v")
    }
}

// Methods
def runShellCmd(String cmd){
    StringBuffer resp = new StringBuffer()
    StringBuffer error = new StringBuffer()

    Process process = cmd.execute()
    process.waitForProcessOutput(resp, error)
    if (error) {
        String warningMsg = "*! Failed to execute shell command $cmd"
        println(warningMsg)
        println(error)
    }
}