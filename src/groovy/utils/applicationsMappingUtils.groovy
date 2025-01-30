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

	// write file
	yamlFile.withWriter() { writer ->
		writer.write(yamlBuilder.toString())
	}
}