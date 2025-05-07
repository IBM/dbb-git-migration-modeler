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

@Field Properties props = new Properties()
@Field def applicationDescriptorUtils = loadScript(new File("utils/applicationDescriptorUtils.groovy"))
@Field def logger = loadScript(new File("utils/logger.groovy"))
@Field File applicationDescriptorFile
@Field def applicationDescriptor
@Field File applicationDBBAppYamlFile
@Field def applicationDBBAppYaml

class ZBuilderApplication {
    String name
    ArrayList<Task> tasks
}

class Task {
    String task
    ArrayList<Variable> variables
}

class Variable {
    String name
    String value
    ArrayList<String> forFiles
}

class IncludeFile {
    String file
}

class DependencyPattern {
    String languageExt
    ArrayList<String> dependencyPatterns
}
 
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

originalDBBAppYamlFile = new File("${props.DBB_MODELER_DEFAULT_GIT_CONFIG}/dbb-app.yaml")
if (originalDBBAppYamlFile.exists()) {
    applicationDBBAppYamlFile = new File("${props.DBB_MODELER_APPLICATION_DIR}/${props.application}/dbb-app.yaml")
    Files.copy(originalDBBAppYamlFile.toPath(), applicationDBBAppYamlFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)
    runShellCmd("chtag -t -c UTF-8 ${applicationDBBAppYamlFile.getAbsolutePath()}")    
    def yamlSlurper = new groovy.yaml.YamlSlurper()
    applicationDBBAppYaml = yamlSlurper.parse(applicationDBBAppYamlFile)
    applicationDBBAppYaml.application.name = props.application
} else {
    logger.logMessage("!* [ERROR] The dbb-app.yaml sample file '${props.DBB_MODELER_DEFAULT_GIT_CONFIG}/dbb-app.yaml' does not exist. Exiting.")
    System.exit(1)
}

logger.logMessage("** Gathering the defined types for files.")

// Internal map to collect all information : type <-> list of files mapped to that type
//HashMap<String, ArrayList<String>> typesToFilesMap = new HashMap<String, ArrayList<String>>()
// Internal map to collect all information : files <-> list of types for this file
HashMap<String, ArrayList<String>> fileToTypesMap = new HashMap<String, ArrayList<String>>()

applicationDescriptor.sources.each { sourceGroup ->
    repositoryPath = sourceGroup.repositoryPath
    fileExtension = sourceGroup.fileExtension
    sourceGroup.files.each { file ->
        if (file.type) {
            def types = file.type.split(",")
            if (types != null && types.size() > 0 && !types[0].equals("UNKNOWN")) {
                fileToTypesMap.put("${sourceGroup.repositoryPath}/${file.name}.${fileExtension}", types)
            }
        }
    }
}

if (fileToTypesMap && fileToTypesMap.size() > 0) {
    logger.logMessage("** Generating zBuilder configuration files.")

    // Path to the zBuilder Configuration folder in the application's folder
    def zBuilderConfigurationFolderPath = "${props.DBB_MODELER_APPLICATION_DIR}/${props.application}/zBuilder-config"
    File zBuilderConfigurationFolder = new File(zBuilderConfigurationFolderPath)
    if (!zBuilderConfigurationFolder.exists()) {
        zBuilderConfigurationFolder.mkdirs()
    }

    fileToTypesMap.each() { file, types ->
	    ZBuilderApplication zBuilderApplication = new ZBuilderApplication()
	    zBuilderApplication.name = props.application
	    zBuilderApplication.tasks = new ArrayList<Task>()
        types.each() { type ->
            typeConfiguration = typesConfigurations.typesConfigurations.find() { configuration ->
                configuration.typeConfiguration.equals(type)
            }

			if (typeConfiguration) {
				typeConfiguration.tasks.each() { typeConfigurationTask ->
					Task task = zBuilderApplication.tasks.find() { applicationTask ->
						applicationTask.task.equals(typeConfigurationTask)
                    }
                    if (!task) {
	                    task = new Task()
    	                task.task = typeConfigurationTask
    	                zBuilderApplication.tasks.add(task)
	                    task.variables = new ArrayList<Variable>()
    	            }

                    typeConfiguration.variables.each() { typeConfigurationVariable ->
                        Variable newVariable = new Variable()
                        newVariable.name = typeConfigurationVariable.name
                        newVariable.value = typeConfigurationVariable.value
                        newVariable.forFiles = new ArrayList<String>()
                        newVariable.forFiles.add(file)
                        task.variables.add(newVariable)
                    }
                }
                // generating and writing the configuration file
                File yamlFile = new File("${zBuilderConfigurationFolderPath}/${file}.yaml")
                def yamlBuilder = new YamlBuilder()
                    yamlBuilder {
                    version "1.0.0"
                    application zBuilderApplication
                }
                File yamlFileParentFolder = yamlFile.getParentFile()
			    if (!yamlFileParentFolder.exists()) {
			        yamlFileParentFolder.mkdirs()
			    }                
                
                yamlFile.withWriter("UTF-8") { writer ->
                    writer.write(yamlBuilder.toString())
                }
                runShellCmd("chtag -t -c UTF-8 ${yamlFile.getAbsolutePath()}")

                if (!applicationDBBAppYaml.application.name) {
                     applicationDBBAppYaml.application.name = props.application
                }
                // Adding the file to the include files in the dbb-app.yaml file
                if (!applicationDBBAppYaml.application.include) {
                    applicationDBBAppYaml.application.include = new ArrayList<IncludeFile>()
                }
                foundIncludeFiles = applicationDBBAppYaml.application.include.findAll() { foundIncludeFile ->
                	foundIncludeFile.file.equals("zBuilder-config/${file}.yaml" as String)
                	
                }
                if (!foundIncludeFiles) {
	                IncludeFile newIncludeFile = new IncludeFile()
	                newIncludeFile.file = "zBuilder-config/${file}.yaml"
	                applicationDBBAppYaml.application.include.add(newIncludeFile)
	            }
            } else {
                logger.logMessage("** [WARNING] No Type Configuration for type '${type}' found in '${props.TYPE_CONFIGURATIONS_FILE}'.")
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
matchingSourcesGroup.each() { matchingMourceGroup ->
    
    def impactAnalysisTask = applicationDBBAppYaml.application.tasks.find() { task ->
        task.task.equals("ImpactAnalysis")
    }
    if (impactAnalysisTask) {
        def impactQueryPatterns = impactAnalysisTask.variables.find() { variable ->
            variable.name.equals("impactQueryPatterns")
        }
        if (impactQueryPatterns) {
            if (!impactQueryPatterns.value) {
                impactQueryPatterns.value = new ArrayList<DependencyPattern>()
            }
            if (matchingMourceGroup.name.equals("cobol")) {
                DependencyPattern dependencyPattern = new DependencyPattern()
                dependencyPattern.languageExt = matchingMourceGroup.fileExtension
                dependencyPattern.dependencyPatterns = new ArrayList<String>()
                // BMS
                def BMSGroup = applicationDescriptor.sources.find() { sourceGroup ->
                    sourceGroup.name.equals("bms")
                }
                if (BMSGroup) {
                    dependencyPattern.dependencyPatterns.add("\${APP_DIR_NAME}/${BMSGroup.repositoryPath}/*.${BMSGroup.fileExtension}")
                }
                // Cobol Copybook
                def CopyGroup = applicationDescriptor.sources.find() { sourceGroup ->
                    sourceGroup.name.equals("copy")
                }
                if (CopyGroup) {
                    dependencyPattern.dependencyPatterns.add("\${APP_DIR_NAME}/${CopyGroup.repositoryPath}/*.${CopyGroup.fileExtension}")
                }
                // Cobol program
                dependencyPattern.dependencyPatterns.add("\${APP_DIR_NAME}/${matchingMourceGroup.repositoryPath}/*.${matchingMourceGroup.fileExtension}")
                impactQueryPatterns.value.add(dependencyPattern)
            }
            if (matchingMourceGroup.name.equals("link")) {
                DependencyPattern dependencyPattern = new DependencyPattern()
                dependencyPattern.languageExt = sourceGroup.fileExtension
                dependencyPattern.dependencyPatterns = new ArrayList<String>()
                // Cobol program
                def CobolGroup = applicationDescriptor.sources.find() { sourceGroup ->
                    sourceGroup.name.equals("cobol")
                }
                if (CobolGroup) {
                    dependencyPattern.dependencyPatterns.add("\${APP_DIR_NAME}/${CobolGroup.repositoryPath}/*.${CobolGroup.fileExtension}")
                }        
                impactQueryPatterns.value.add(dependencyPattern)
            }
        }
    }
}


// generating and writing the configuration file    
def yamlBuilder = new YamlBuilder()
    yamlBuilder {
    version "1.0.0"
    application applicationDBBAppYaml.application
}
applicationDBBAppYamlFile.withWriter("UTF-8") { writer ->
    writer.write(yamlBuilder.toString())
}    

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
            logger.logMessage("*! [ERROR] The Applications directory '${configuration.DBB_MODELER_APPLICATION_DIR}' does not exist. Exiting.")
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

    if (configuration.DBB_MODELER_DEFAULT_GIT_CONFIG) {
        File file = new File(configuration.DBB_MODELER_DEFAULT_GIT_CONFIG)
        if (file.exists()) {
            props.DBB_MODELER_DEFAULT_GIT_CONFIG = configuration.DBB_MODELER_DEFAULT_GIT_CONFIG
        } else {
            logger.logMessage("*! [ERROR] The DBB Git Configuration samples folder '${configuration.DBB_MODELER_DEFAULT_GIT_CONFIG}' does not exist. Exiting.")
            System.exit(1)
        }
    } else {
        logger.logMessage("*! [ERROR] The path to the DBB Git Configuration samples folder must be specified in the DBB Git Migration Modeler Configuration file. Exiting.")
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