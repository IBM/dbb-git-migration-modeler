@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import com.ibm.dbb.metadata.*
import com.ibm.dbb.dependency.*
import com.ibm.dbb.build.*
import groovy.transform.*
import com.ibm.dbb.build.report.*
import com.ibm.dbb.build.report.records.*
import groovy.yaml.YamlSlurper
import groovy.yaml.YamlBuilder
import groovy.lang.GroovyShell
import groovy.util.*
import java.nio.file.*

/**
 * Utilities to read, update or export existing ApplicationDescriptor from/to YAML 
 */
class ApplicationMapping {
    String application
    String description
    String owner
    ArrayList<String> namingConventions
}

class ApplicationsMapping {
	ArrayList<ApplicationMapping> applications
}

/////// Test
//ApplicationsMapping applicationsMapping = readApplicationsMapping(new File("applicationsMapping.yml"))
//println applicationsMapping.applications
//println applicationDescriptor.description
//println applicationDescriptor.sources.size()
//
//applicationDescriptor = appendFileDefinition(applicationDescriptor, "copy", "none", "COPY", "COPYBOOK", "PRIVATE")
//
//println applicationDescriptor.application
//println applicationDescriptor.description
//println applicationDescriptor.sources.size()
//
//writeApplicationDescriptor(new File("/u/dbehm/componentization/work/retirementCalculator.yaml"), applicationDescriptor)

/**
 * 
 * Reads an existing application descriptor YAML
 * returns an ApplicationDescriptor Object
 * 
 */
def readApplicationsMapping(File yamlFile){
	def yamlSlurper = new groovy.yaml.YamlSlurper()
	ApplicationsMapping applicationMapping = yamlSlurper.parse(yamlFile)
	return applicationMapping
}

/**
 * Write an ApplicationDescriptor Object into a YAML file
 */
def writeApplicationsMapping(File yamlFile, ApplicationsMapping applicationsMapping){
	def yamlBuilder = new YamlBuilder()

	yamlBuilder {
		applications applicationsMapping
	}

	// debug
	// println yamlBuilder.toString()

	// write file
	yamlFile.withWriter() { writer ->
		writer.write(yamlBuilder.toString())
	}
}