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
import com.ibm.jzos.CatalogSearch;
import com.ibm.jzos.CatalogSearchField;
import com.ibm.jzos.Format1DSCB;
import com.ibm.jzos.RcException;
import com.ibm.jzos.PdsDirectory;
import com.ibm.jzos.PdsDirectory.MemberInfo;
import com.ibm.jzos.PdsDirectory.MemberInfo.Statistics;
import com.ibm.jzos.ZFile;
import com.ibm.jzos.ZFileConstants;
import com.ibm.jzos.ZUtil;
import java.util.Properties;

@Field def applicationDescriptorUtils = loadScript(new File("utils/applicationDescriptorUtils.groovy"))
@Field def applicationsMappingUtils = loadScript(new File("utils/applicationsMappingUtils.groovy"))
@Field def logger = loadScript(new File("utils/logger.groovy"))

@Field HashMap<String, HashSet<String>> applicationMappingToDatasetMembers = new HashMap<String, HashSet<String>>()
// Types Configurations
@Field HashMap<String, String> types
// script properties
@Field Properties props = new Properties()
@Field repositoryPathsMapping

/**
 * Processing logic
 */

println("** Extraction process started. ")

// Parse arguments from command-line
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

// Read the repository layout mapping file
logger.logMessage ("** Reading the Repository Layout Mapping definition. ")
if (props.repositoryPathsMappingFilePath) {
	File repositoryPathsMappingFile = new File(props.repositoryPathsMappingFilePath)
	if (!repositoryPathsMappingFile.exists()) {
		logger.logMessage "!* Warning: File ${props.repositoryPathsMappingFilePath} not found. Process will exit."
		System.exit(1)
	} else {
		def yamlSlurper = new groovy.yaml.YamlSlurper()
		repositoryPathsMapping = yamlSlurper.parse(repositoryPathsMappingFile)
	}
}

// Applications Mapping read from YAML file (expected to be in applicationsMapping.yml so far)
logger.logMessage ("** Reading the Application Mapping definition. ")
@Field applicationsMapping
if (props.applicationsMappingFilePath) {
	def applicationsMappingFile = new File(props.applicationsMappingFilePath)
	if (!applicationsMappingFile.exists()) {
		logger.logMessage "!* Warning: File ${props.applicationsMappingFilePath} not found. All artifacts will be unassigned."
	} else {
		applicationsMapping = applicationsMappingUtils.readApplicationsMapping(applicationsMappingFile)
	}
} else {
	logger.logMessage "!* Warning: no Applications Mapping File defined. All artifacts will be unassigned."
}

// Read the Types from file
logger.logMessage ("** Reading the Type Mapping definition. ")
if (props.typesFilePath) {
	def typesFile = new File(props.typesFilePath)
	if (!typesFile.exists()) {
		logger.logMessage "!* Warning: File ${props.typesFilePath} not found in the current working directory. All artifacts will use the 'UNKNOWN' type."
	} else {
		types = loadMapFromFile(props.typesFilePath)
	}
} else {
	logger.logMessage "!* Warning: no Types File defined. All artifacts will use the 'UNKNOWN' type."
}

// Resolving datasets that contain wildcards
ArrayList<String> datasets = new ArrayList<String>()
props.datasetsList.split(',').each() { dataset ->
	if (dataset.contains("*")) {
		buildDatasetsList(datasets, dataset)
	} else {
		datasets.add(dataset)
	}
}


logger.logMessage ("** Iterating through the provided datasets. ")
datasets.each() { dataset ->
	String qdsn = constructPDSForZFileOperation(dataset)
	if (ZFile.dsExists(qdsn)) {
		logger.logMessage("*** Found $dataset");
		try {
			PdsDirectory directoryList = new PdsDirectory(qdsn)
			Iterator directoryListIterator = directoryList.iterator();
			while (directoryListIterator.hasNext()) {
				PdsDirectory.MemberInfo memberInfo = (PdsDirectory.MemberInfo) directoryListIterator.next();
				String member = (memberInfo.getName());
				def mappedApplication = findMappedApplicationFromMemberName(member)
				logger.logMessage("**** '$dataset($member)' - Mapped Application: " + mappedApplication);
				addDatasetMemberToApplication(mappedApplication, "$dataset($member)")
			}
			directoryList.close();
		}
		catch (java.io.IOException exception) {
			logger.logMessage("**** Error: Is $qdsn a valid dataset?");
		}
	}
	else {
		logger.logMessage("**** Error: dataset $qdsn does not exist.");
	}
}

logger.logMessage "** Generating Applications Configurations files. "
applicationMappingToDatasetMembers.each() { application, members ->
	logger.logMessage "** Generating Configuration files for application $application. "
	generateMappingFile(application)
	generateApplicationDescriptorFile(application)
}


logger.close()

/*  ==== Utilities ====  */

/* used to expand datasets when they contains wildcards characters */
def buildDatasetsList(ArrayList<String> datasetsList, String filter) {
	CatalogSearch catalogSearch = new CatalogSearch(filter, 64000);
	catalogSearch.addFieldName("ENTNAME");
	catalogSearch.search();
	def datasetCount = 0
	
	catalogSearch.each { searchEntry ->
		CatalogSearch.Entry catalogEntry = (CatalogSearch.Entry) searchEntry;
		if (catalogEntry.isDatasetEntry()) {
			datasetCount++;
			CatalogSearchField field = catalogEntry.getField("ENTNAME");
			String dsn = field.getFString().trim();
			datasetsList.add(dsn);
		}
	}
}



/* parseArgs: parse arguments provided through CLI */
def parseArgs(String[] args) {
	String usage = 'extractApplications.groovy [options]'
	String header = 'options:'
	def cli = new CliBuilder(usage:usage,header:header);
	cli.d(longOpt:'datasets', args:1, required:true, 'List of comma-separated datasets to scan');
	cli.oc(longOpt:'outputConfigurations', args:1, required:true, 'Output folder where Configurations files are written');
	cli.oa(longOpt:'outputApplications', args:1, required:true, 'Output folder where Applications files will be copied');
	cli.a(longOpt:'applicationMapping', args:1, required:false, 'Path to the Applications Mapping file')
	cli.r(longOpt:'repositoryPathsMapping', args:1, required:true, 'Path to the Repository Paths Mapping file')
	cli.t(longOpt:'types', args:1, required:false, 'Path to the Types file')
	cli.l(longOpt:'logFile', args:1, required:false, 'Relative or absolute path to an output log file')
	
	def opts = cli.parse(args);
	if (!args || !opts) {
		cli.usage();
		System.exit(1);
	}
	
	if (opts.d) {
		props.datasetsList = opts.d;
	} else {
		logger.logMessage("*! Error: a list of comma-separated datasets ('-d' parameter) to scan must be provided. Exiting.");
		System.exit(1);
	}
	
	if (opts.oc) {
		props.outputConfigurationDirectory = opts.oc;
	} else {
		logger.logMessage("*! Error: an output Configuration directory ('-oc' parameter) must be specified. Exiting.");
		System.exit(1);
	}

	if (opts.oa) {
		props.outputApplicationDirectory = opts.oa;
	} else {
		logger.logMessage("*! Error: an output Application directory ('-oa' parameter) must be specified. Exiting.");
		System.exit(1);
	}

	if (opts.r) {
		props.repositoryPathsMappingFilePath = opts.r
	} else {
		logger.logMessage("*! Error: the path to the Repository Paths mapping file ('-r' parameter) must be specified. Exiting.");
		System.exit(1);
	}

	if (opts.a) {
		props.applicationsMappingFilePath = opts.a
	}
	
	if (opts.t) {
		props.typesFilePath = opts.t
	}
	
	if (opts.l) {
		props.logFile = opts.l
	}
}

def constructPDSForZFileOperation(String PDS) {
	return "//'${PDS}'"
}

def constructDatasetForZFileOperation(String PDS, String member) {
	return "//'${PDS}($member)'"
}

def isFilterOnMemberMatching(String memberName, String filter) {
	StringBuilder expandedMemberNameStringBuilder = new StringBuilder(memberName);
	while (expandedMemberNameStringBuilder.length() < 8) {
		expandedMemberNameStringBuilder.append('.');
	}
	String expandedMemberName = expandedMemberNameStringBuilder.toString();

	StringBuilder expandedFilterStringBuilder = new StringBuilder(filter);
	while (expandedFilterStringBuilder.length() < 8) {
		expandedFilterStringBuilder.append('.');
	}
	String expandedFilter = expandedFilterStringBuilder.toString();
	
	StringBuilder result = new StringBuilder();
	int i = 0;
	while (i < expandedMemberName.length() && i < 8) {
		if (expandedFilter[i] != '.') {
			result.append(expandedMemberName[i])
		} else {
			result.append('.')
		}
		i++;
	}
	return result.toString().equalsIgnoreCase(expandedFilter);
}

def generateMappingFile(String application) {
	File mappingFile = new File(props.outputConfigurationDirectory + '/' + application + ".mapping");
	HashMap<String, String> mappings = new HashMap<String, String>()
	if (mappingFile.exists()) {
		BufferedReader mappingReader = new BufferedReader(new FileReader(mappingFile))
		String line;
		while((line = mappingReader.readLine()) != null) {
			def lineSegments = line.split(' ')
			mappings.put(lineSegments[0], lineSegments.tail().join(" "))
		}
		mappingReader.close()
	}

	def datasetMembersCollection = applicationMappingToDatasetMembers.get(application)
	datasetMembersCollection.each () { datasetMember ->
		def (dataset,member) = getDatasetAndMember(datasetMember)
		def lastQualifier = getLastQualifier(dataset)
		def memberType = getType(member)
		def matchingRepositoryPath = repositoryPathsMapping.repositoryPaths.find {repositoryPath ->
			(repositoryPath.mvsMapping.types ? repositoryPath.mvsMapping.types.contains(memberType) : false) ||
			(repositoryPath.mvsMapping.datasetLastLevelQualifiers ? repositoryPath.mvsMapping.datasetLastLevelQualifiers.contains(lastQualifier) : false) 
		}
	
		def targetRepositoryPath
		def pdsEncoding
		def fileExtension
		if (matchingRepositoryPath) {
			fileExtension = (matchingRepositoryPath.fileExtension) ? (matchingRepositoryPath.fileExtension) : lastQualifier
			member = member + "." + fileExtension 
			if (matchingRepositoryPath.toLowerCase && matchingRepositoryPath.toLowerCase.toBoolean()) {
				member = member.toLowerCase()
				lastQualifier = lastQualifier.toLowerCase()
				fileExtension = fileExtension.toLowerCase()				
			}
			targetRepositoryPath = (matchingRepositoryPath.repositoryPath) ? matchingRepositoryPath.repositoryPath.replaceAll('\\$application',application) : "$application/$lastQualifier"
			pdsEncoding = (matchingRepositoryPath.encoding) ? (matchingRepositoryPath.encoding) : "IBM-1047"
		} else {
			member = member.toLowerCase()
			lastQualifier = lastQualifier.toLowerCase()
			fileExtension = lastQualifier				
			member = member + "." + fileExtension
			targetRepositoryPath = "$application/$lastQualifier"
			pdsEncoding = "IBM-1047"
		}
		targetRepositoryPath = props.outputApplicationDirectory + "/" + application + "/" + targetRepositoryPath
		mappings.put(datasetMember, "$targetRepositoryPath/$member pdsEncoding=$pdsEncoding")
	}

	
	try {
		boolean append = false
		BufferedWriter writer = new BufferedWriter(new FileWriter(mappingFile, append))
		mappings.each() { datasetMember, target ->
			writer.write("$datasetMember $target\n");
		}
		writer.close();
	}
	catch (IOException e) {
		e.printStackTrace();
	}
	logger.logMessage("\tCreated DBB Migration Utility mapping file " + mappingFile.getAbsolutePath());
}

def generateApplicationDescriptorFile(String application) {
	File applicationDescriptorFile = new File(props.outputConfigurationDirectory + '/' + application + ".yaml")
	def applicationDescriptor	
	if (applicationDescriptorFile.exists()) {
		applicationDescriptor = applicationDescriptorUtils.readApplicationDescriptor(applicationDescriptorFile)
	} else {
		applicationDescriptor = applicationDescriptorUtils.createEmptyApplicationDescriptor()
	}	
	
	mappedApplication = findMappedApplication(application)
	if (mappedApplication != null) {
		applicationDescriptor.application = mappedApplication.application
		applicationDescriptor.description = mappedApplication.description
		applicationDescriptor.owner = mappedApplication.owner
	} else {
		applicationDescriptor.application = "UNASSIGNED"
		applicationDescriptor.description = "Unassigned components"
		applicationDescriptor.owner = "None"
	}

	def datasetMembersCollection = applicationMappingToDatasetMembers.get(application)
	datasetMembersCollection.each () { datasetMember ->
		def (dataset,member) = getDatasetAndMember(datasetMember)
		def lastQualifier = getLastQualifier(dataset)
		def memberType = getType(member)
		def matchingRepositoryPath = repositoryPathsMapping.repositoryPaths.find {repositoryPath ->
			(repositoryPath.mvsMapping.types ? repositoryPath.mvsMapping.types.contains(memberType) : false) ||
			(repositoryPath.mvsMapping.datasetLastLevelQualifiers ? repositoryPath.mvsMapping.datasetLastLevelQualifiers.contains(lastQualifier) : false) 
		}

		def targetRepositoryPath
		def pdsEncoding
		def fileExtension
		def artifactsType
		if (matchingRepositoryPath) {
			fileExtension = (matchingRepositoryPath.fileExtension) ? (matchingRepositoryPath.fileExtension) : lastQualifier
			if (matchingRepositoryPath.toLowerCase && matchingRepositoryPath.toLowerCase.toBoolean()) {
				member = member.toLowerCase()
				lastQualifier = lastQualifier.toLowerCase()
				fileExtension = fileExtension.toLowerCase()
			}
			artifactsType = (matchingRepositoryPath.artifactsType) ? (matchingRepositoryPath.artifactsType) : lastQualifier
			targetRepositoryPath = (matchingRepositoryPath.repositoryPath) ? matchingRepositoryPath.repositoryPath.replaceAll('\\$application',application) : "$application/$lastQualifier"
			pdsEncoding = (matchingRepositoryPath.encoding) ? (matchingRepositoryPath.encoding) : "IBM-1047"
		} else {
			member = member.toLowerCase()
			lastQualifier = lastQualifier.toLowerCase()
			targetRepositoryPath = "$application/$lastQualifier"
			pdsEncoding = "IBM-1047"
			artifactsType = lastQualifier
			fileExtension = lastQualifier
		}
		applicationDescriptorUtils.appendFileDefinition(applicationDescriptor, lastQualifier, lastQualifier + ".groovy", artifactsType, fileExtension, targetRepositoryPath, member, memberType, "undefined")
	}
	applicationDescriptorUtils.writeApplicationDescriptor(applicationDescriptorFile, applicationDescriptor)
	logger.logMessage("\tCreated Application Description file " + applicationDescriptorFile.getAbsolutePath());
}


def addDatasetMemberToApplication(String application, String datasetMember) {
	def applicationMap = applicationMappingToDatasetMembers.get(application)

	if (applicationMap == null) {
		applicationMap = new HashSet<String>();
		applicationMappingToDatasetMembers.put(application, applicationMap);
	}
	applicationMap.add(datasetMember);
}

def findMappedApplicationFromMemberName(String memberName) {
	if (!applicationsMapping) {
		return "UNASSIGNED"
	} else {
		def mappedApplications = applicationsMapping.applications.findAll { application ->
			application.namingConventions.find { namingConvention ->
				isFilterOnMemberMatching(memberName, namingConvention) 
			}
		}
		
		if (mappedApplications.size == 1) { // one application claimed ownership
			return mappedApplications[0].application
		} else if (mappedApplications.size > 1) { // multiple appliations claimed ownership
			logger.logMessage ("[WARNING] Multiple applications claim ownership of member $memberName:")
			mappedApplications.each {it -> 
					logger.logMessage ("          Claiming ownership : " + it.application) 
					}
			logger.logMessage ("[WARNING] The owner cannot be defined. Map $memberName to UNASSIGNED")
			return "UNASSIGNED"
		} else { // no match found
			return "UNASSIGNED"
		}
	}
}

def findMappedApplication(String applicationName) {
	if (!applicationsMapping) {
		return null
	} else {
		def mappedApplication = applicationsMapping.applications.find { application -> application.application.equals(applicationName) }
		if (mappedApplication) {
			return mappedApplication
		} else {
			return null
		}
	}
}


/**
 * Parse the fullname of a qualified dataset and member name
 * Returns its dataset name and member name)  
 * For instance: BLD.LOAD(PGM1)     --> [BLD.LOAD, PGM1]
 */
def getDatasetAndMember(String fullname) {
	def ds,member;
	def elements =  fullname.split("[\\(\\)]");
	ds = elements[0];
	member = elements.size()>1? elements[1] : "";
	return [ds, member];
}

def getLastQualifier(String dataset) {
	def qualifiers =  dataset.split("\\.");
	return qualifiers.last()
}

// Reads a HashMap from a file with comma separator (',')
def loadMapFromFile(String filePath) {
	HashMap<String, String> map = new HashMap<>();
	String line;
	try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
		while ((line = reader.readLine()) != null) {
			String[] keyValuePair = line.split(",", 2);
			if (keyValuePair.length > 1) {
				String key = keyValuePair[0].trim();
				String value = keyValuePair[1].trim().replaceAll(" ", "");
				map.put(key, value);
			}
		}
	} catch (IOException e) {
		e.printStackTrace();
	}
	return map;
}

def getType(String member) {
	if (!types) {
		return "UNKNOWN"
	} else {
		def type = types.get(member)
		if (type) {
			return type
		} else {
			return "UNKNOWN"
		}
	}
}