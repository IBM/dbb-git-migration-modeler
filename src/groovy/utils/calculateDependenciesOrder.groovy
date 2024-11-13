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

Graph graph = new Graph();
dependencies.each() { application, applicationDependencies ->
    graph.addApplication(application);
    applicationDependencies.each() { dependency ->
    	graph.addDependencyToApplication(application, dependency);
    }
}

graph.topologicalSort()
 

/**
 * Parse CLI config
 */
def parseArgs(String[] args) {

	String usage = 'calculateDependenciesOrder.groovy [options]'

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

class Graph {
	HashMap<String, ArrayList<String>> dependencies
	
	Graph() {
		dependencies = new HashMap<String, ArrayList<String>>()
	}
	
	void addApplication(String application) {
		dependencies.put(application, new ArrayList<String>())
	}
	
	void addDependencyToApplication(String application, String dependency) {
		ArrayList<String> dependencyList = dependencies.get(application);
		if (!dependencyList) {
			dependencyList = new ArrayList<String>()
		}
		dependencyList.add(dependency);
		dependencies.put(application, dependencyList)
	}
	
	void deleteApplication(String application) {
		dependencies.remove(application)
		dependencies.each() { dependency, dependencyList ->
			dependencyList.remove(application)
		}
	}

	void topologicalSort() {
		ArrayList<String> applicationsWithNoDependency = new ArrayList<String>() 
		HashMap<Integer, ArrayList<String>> rankedApplications = new HashMap<Integer, ArrayList<String>>()
		int rank = 1

		// Initialize list with applications that have no dependencies
		dependencies.each() { application, applicationDependencies ->
			if (applicationDependencies.isEmpty()) {
				applicationsWithNoDependency.add(application as String)
			}
		}

		// Main loop		
		while (!applicationsWithNoDependency.isEmpty()) {
			ArrayList<String> applicationsInRank = new ArrayList<String>()
			applicationsWithNoDependency.each() { application ->
				deleteApplication(application)
				applicationsInRank.add(application)
			}
			rankedApplications.put(rank, applicationsInRank)
			applicationsWithNoDependency.clear()
			rank++
			dependencies.each() { application, applicationDependencies ->
				if (applicationDependencies.isEmpty()) {
					applicationsWithNoDependency.add(application as String)
				}
			}
		}
		
		println("*** Level-ranked applications:")
		rankedApplications.each() { applicationsRank, applications ->
			println("\t" + applicationsRank + " " + applications)
		}
		if (dependencies) {
			println("\n*** Remaining applications:\n\t[")
			dependencies.each(){ application, dependencies ->
				print(application + " ")
			}
			println("\n]")
		} else {
			println("*** All applications were ranked successfully!")
		}
	}
}

