@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import groovy.transform.*
import groovy.yaml.YamlSlurper
import groovy.yaml.YamlBuilder
import groovy.lang.GroovyShell
import groovy.util.*
import java.nio.file.*
import groovy.cli.commons.*

@Field Properties props = new Properties()
@Field def applicationDescriptorUtils = loadScript(new File("applicationDescriptorUtils.groovy"))
@Field def applicationDescriptor
@Field HashMap<String, ArrayList<String>> dependencies = new HashMap<String, ArrayList<String>>()


// Initialization
parseArgs(args)

File applicationsFolder = new File(props.ApplicationsFolderPath)
if (!applicationsFolder.exists()) {
	println("*! [ERROR] The Applications' folder '${props.ApplicationsFolderPath}' was not found. Exiting.")
	System.exit(1) 
} else {
	applicationsFolder.eachDir() { application ->
		if (!application.name.equals("dbb-zappbuild")) {
			File applicationDescriptorFile = new File(application.getAbsolutePath() + "/applicationDescriptor.yml")
			if (!applicationDescriptorFile.exists()) {
				println("*! [ERROR] The Application Descriptor file '${props.ApplicationDescriptorFilePath}' was not found. Skipping.")
			} else {
				applicationDescriptor = applicationDescriptorUtils.readApplicationDescriptor(applicationDescriptorFile)
				if (applicationDescriptor) {
					ArrayList<String> applicationDependencies = new ArrayList<String>()
					if (applicationDescriptor.dependencies) {
						applicationDescriptor.dependencies.each() { applicationDependency ->
							applicationDependencies.add(applicationDependency.name as String)
						}
					}
					dependencies.put(applicationDescriptor.application, applicationDependencies)
				}
			}
		}
	}
}

println("** Dependencies for each application:") 
dependencies.each() { application, applicationDependencies ->
	println("\t$application - $applicationDependencies")
}

 

/**
 * Parse CLI config
 */
def parseArgs(String[] args) {

	String usage = 'zappUtils.groovy [options]'

	def cli = new CliBuilder(usage:usage)
	// required sandbox options
	cli.a(longOpt:'applicationsFolder', args:1, 'Absolute path to the applications\' folder')

	def opts = cli.parse(args)
	if (!opts) {
		System.exit(1)
	}

	if (opts.a) {
		props.ApplicationsFolderPath = opts.a
	} else {
		println("*! [ERROR] The path to the Applications' folder file must be specified. Exiting.")
		System.exit(1) 
	}
}