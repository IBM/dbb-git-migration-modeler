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
import static groovy.io.FileType.*
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
import com.ibm.dmh.scan.classify.Dmh5210;
import com.ibm.dmh.scan.classify.ScanProperties;
import com.ibm.teamz.classify.ClassifyFileContent;
import com.ibm.dmh.scan.classify.IncludedFileMetaData;
import com.ibm.dmh.scan.classify.SingleFilesMetadata;
import java.text.DecimalFormat

@Field def applicationDescriptorUtils = loadScript(new File("utils/applicationDescriptorUtils.groovy"))
@Field def logger = loadScript(new File("utils/logger.groovy"))

//Map between applications name and owned datasetMembers
@Field HashMap<String, HashSet<String>> applicationsToDatasetMembersMap = new HashMap<String, HashSet<String>>()
//Map between datasets (represented as List) and the applications defined in the Applications Mapping files
@Field HashMap<ArrayList<String>, ArrayList<Object>> datasetsMap = new HashMap<ArrayList<String>, ArrayList<Object>>()
//Map between members and datasets (represented as List)
@Field HashMap<String, ArrayList<String>> membersToDatasetsMap = new HashMap<String, ArrayList<String>>()

// Types Configurations
@Field HashMap<String, String> types
// script properties
@Field Properties props = new Properties()
@Field repositoryPathsMapping
@Field Dmh5210 scanner
HashMap<String, Long> storageRequirements = new HashMap<String, Long>() 

/**
 * Processing logic
 */

logger.logMessage("** Extraction process started.")

// Parse arguments from command-line
parseArgs(args)

// Read the repository layout mapping file
logger.logMessage("** Reading the Repository Layout Mapping definition.")
if (props.REPOSITORY_PATH_MAPPING_FILE) {
	File repositoryPathsMappingFile = new File(props.REPOSITORY_PATH_MAPPING_FILE)
	if (!repositoryPathsMappingFile.exists()) {
		logger.logMessage("*! [WARNING] The Repository Path Mapping file ${props.REPOSITORY_PATH_MAPPING_FILE} was not found. Exiting.")
		System.exit(1)
	} else {		
		def yamlSlurper = new groovy.yaml.YamlSlurper()
		repositoryPathsMapping = yamlSlurper.parse(repositoryPathsMappingFile)
	}
}

// Read the Types from file
logger.logMessage("** Reading the Type Mapping definition.")
if (props.APPLICATION_MEMBER_TYPE_MAPPING) {
	def typesFile = new File(props.APPLICATION_MEMBER_TYPE_MAPPING)
	if (!typesFile.exists()) {
		logger.logMessage("*! [WARNING] File ${props.APPLICATION_MEMBER_TYPE_MAPPING} not found in the current working directory. All artifacts will use the 'UNKNOWN' type.")
	} else {
		types = loadMapFromFile(props.APPLICATION_MEMBER_TYPE_MAPPING)
	}
} else {
	logger.logMessage("*! [WARNING] No Types File provided. The 'UNKNOWN' type will be assigned by default to all artifacts.")
}

logger.logMessage("** Loading the provided Applications Mapping files.")
File applicationsMappingsDir = new File(props.DBB_MODELER_APPMAPPINGS_DIR)
applicationsMappingsDir.eachFile(FILES) { applicationsMappingFile ->
	logger.logMessage("*** Importing '${applicationsMappingFile.getName()}'")
	def yamlSlurper = new groovy.yaml.YamlSlurper()
	applicationsMapping = yamlSlurper.parse(applicationsMappingFile)
	ArrayList<Object> applicationsList = datasetsMap.get(applicationsMapping.datasets)
	if (!applicationsList) {
		applicationsList = new ArrayList<Object>()
		datasetsMap.put(applicationsMapping.datasets, applicationsList)
	}
	applicationsMapping.applications.each() { application ->
		applicationsList.add(application)
	}
}

if (findDuplicateApplications()) {
	System.exit(1)
}

logger.logMessage("** Iterating through the applications.")
datasetsMap.each() { datasets, applicationsList ->
	logger.logMessage("*** Processing '$datasets'")

	datasets.each() { dataset ->
		String qdsn = constructPDSForZFileOperation(dataset)
		if (ZFile.dsExists(qdsn)) {
			logger.logMessage("**** Found '$dataset'");
			try {
				PdsDirectory directoryList = new PdsDirectory(qdsn)
				Iterator directoryListIterator = directoryList.iterator();
				while (directoryListIterator.hasNext()) {
					PdsDirectory.MemberInfo memberInfo = (PdsDirectory.MemberInfo) directoryListIterator.next();
					String member = (memberInfo.getName());
					addDatasetToMember(member, dataset)
					def mappedApplication = findMappedApplicationFromMemberName(applicationsList, member)
					logger.logMessage("***** '$dataset($member)' - Mapped Application: $mappedApplication");
					addDatasetMemberToApplication(mappedApplication, "$dataset($member)")
				}
				directoryList.close();
			}
			catch (java.io.IOException exception) {
				logger.logMessage("*! [ERROR] Problem when accessing the dataset '$qdsn'.");
			}
		}
		else {
			logger.logMessage("*! [ERROR] Dataset '$qdsn' does not exist.");
		}
	}
}

findDuplicateProcessingOfMembers()

DecimalFormat df = new DecimalFormat("###,###,###,###")

logger.logMessage("** Generating Applications Configurations files.")
applicationsToDatasetMembersMap.each() { application, members ->
	logger.logMessage("** Generating Configuration files for application $application.")
	generateApplicationFiles(application)
	storageRequirements.put(application, calculateStorageSizeForMembers(members))  
	
	logger.logMessage("\tEstimated storage size of migrated members: ${df.format(storageRequirements.get(application))} bytes")
}
def globalStorageRequirements = 0
storageRequirements.each() { application, storageRequirement ->
	globalStorageRequirements = globalStorageRequirements + storageRequirement
}
logger.logMessage("** Estimated storage size of all migrated members: ${df.format(globalStorageRequirements)} bytes")

logger.close()


/*  ==== Utilities ====  */

/* parseArgs: parse arguments provided through CLI */
def parseArgs(String[] args) {
	Properties configuration = new Properties()
	String usage = 'extractApplications.groovy [options]'
	String header = 'options:'
	def cli = new CliBuilder(usage:usage,header:header)
	cli.c(longOpt:'configFile', args:1, required:true, 'Path to the DBB Git Migration Modeler Configuration file (created by the Setup script)')
	cli.l(longOpt:'logFile', args:1, required:false, 'Relative or absolute path to an output log file')
	
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

	if (configuration.DBB_MODELER_APPCONFIG_DIR) {
		File directory = new File(configuration.DBB_MODELER_APPCONFIG_DIR)
		if (directory.exists()) {
			props.DBB_MODELER_APPCONFIG_DIR = configuration.DBB_MODELER_APPCONFIG_DIR
		} else {
			logger.logMessage("*! [ERROR] The Configurations directory '${configuration.DBB_MODELER_APPCONFIG_DIR}' does not exist. Exiting.")
			System.exit(1)
		}
	} else {
		logger.logMessage("*! [ERROR] The Configurations directory must be specified in the DBB Git Migration Modeler Configuration file. Exiting.")
		System.exit(1)
	}	

	if (configuration.DBB_MODELER_APPMAPPINGS_DIR) {
		File directory = new File(configuration.DBB_MODELER_APPMAPPINGS_DIR)
		if (directory.exists()) {
			props.DBB_MODELER_APPMAPPINGS_DIR = configuration.DBB_MODELER_APPMAPPINGS_DIR
		} else {
			logger.logMessage("*! [ERROR] The Applications Mappings directory '${configuration.DBB_MODELER_APPMAPPINGS_DIR}' does not exist. Exiting.")
			System.exit(1)
		}
	} else {
		logger.logMessage("*! [ERROR] The Applications Mappings directory must be specified in the DBB Git Migration Modeler Configuration file. Exiting.")
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

	if (configuration.REPOSITORY_PATH_MAPPING_FILE) {
		File file = new File(configuration.REPOSITORY_PATH_MAPPING_FILE)
		if (file.exists()) {
			props.REPOSITORY_PATH_MAPPING_FILE = configuration.REPOSITORY_PATH_MAPPING_FILE
		} else {
			logger.logMessage("*! [ERROR] The Repository Paths Mapping file '${configuration.REPOSITORY_PATH_MAPPING_FILE}' does not exist. Exiting.")
			System.exit(1)
		}
	} else {
		logger.logMessage("*! [ERROR] The path to the Repository Paths Mapping file must be specified in the DBB Git Migration Modeler Configuration file. Exiting.")
		System.exit(1)
	}

	if (configuration.APPLICATION_MEMBER_TYPE_MAPPING) {
		File file = new File(configuration.APPLICATION_MEMBER_TYPE_MAPPING)
		if (file.exists()) {
			props.APPLICATION_MEMBER_TYPE_MAPPING = configuration.APPLICATION_MEMBER_TYPE_MAPPING
		} else {
			logger.logMessage("*! [ERROR] The Types file '${configuration.APPLICATION_MEMBER_TYPE_MAPPING}' does not exist. Exiting.")
			System.exit(1)
		}
	}	

	if (configuration.SCAN_DATASET_MEMBERS) {
		props.SCAN_DATASET_MEMBERS = configuration.SCAN_DATASET_MEMBERS
		if (configuration.SCAN_DATASET_MEMBERS_ENCODING) {
			props.SCAN_DATASET_MEMBERS_ENCODING = configuration.SCAN_DATASET_MEMBERS_ENCODING
		} else {
			props.SCAN_DATASET_MEMBERS_ENCODING = "IBM-1047"
		}
	} else {
		props.SCAN_DATASET_MEMBERS = "false"
	}
	
	logger.logMessage("** Script configuration:")
	props.each() { k, v ->
		logger.logMessage("\t$k -> $v")
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

def generateApplicationFiles(String application) {
	// If an existing DBB Migration Mapping file already exists in the CONFIG directory,
	// we read it and store it into a HashMap where the key in the input dataset member 
	File mappingFile = new File(props.DBB_MODELER_APPCONFIG_DIR + '/' + application + ".mapping");
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

	// If an existing Application Descriptor file already exists in the CONFIG directory,
	// we read it into an Application Descriptor object 
	File applicationDescriptorFile = new File(props.DBB_MODELER_APPCONFIG_DIR + '/' + application + ".yml")
	def applicationDescriptor	
	if (applicationDescriptorFile.exists()) {
		applicationDescriptor = applicationDescriptorUtils.readApplicationDescriptor(applicationDescriptorFile)
	} else {
		applicationDescriptor = applicationDescriptorUtils.createEmptyApplicationDescriptor()
	}	
	
	// Trying to find information about the application we are dealing with
	foundApplication = findApplication(application)
	if (foundApplication != null) {
		applicationDescriptor.application = foundApplication.application
		applicationDescriptor.description = foundApplication.description
		applicationDescriptor.owner = foundApplication.owner
		// Adding baseline to ApplicationDescriptor
		applicationDescriptorUtils.addBaseline(applicationDescriptor, "main" as String, foundApplication.baseline)
		applicationDescriptorUtils.addBaseline(applicationDescriptor, "release/${foundApplication.baseline}" as String, foundApplication.baseline)	
	} else {
		applicationDescriptor.application = "UNASSIGNED"
		applicationDescriptor.description = "Unassigned components"
		applicationDescriptor.owner = "None"
		applicationDescriptorUtils.addBaseline(applicationDescriptor, "main" as String, "rel-1.0.0" as String)
		applicationDescriptorUtils.addBaseline(applicationDescriptor, "release/rel-1.0.0" as String, "rel-1.0.0" as String)	
	}

	// Main loop, iterating through the dataset members assigned to the current application
	def datasetMembersCollection = applicationsToDatasetMembersMap.get(application)
	// For each dataset member...
	datasetMembersCollection.each () { datasetMember ->
		// Get the dataset and the member separated
		def (dataset, member) = getDatasetAndMember(datasetMember)
		// Using the DBB Scanner if activated
		String scannedLanguage, scannedFileType
		if (props.SCAN_DATASET_MEMBERS && props.SCAN_DATASET_MEMBERS.toBoolean()) {
			(scannedLanguage, scannedFileType) = scanDatasetMember(constructDatasetForZFileOperation(dataset, member))
		}
		def lastQualifier = getLastQualifier(dataset)
		def memberType = getType(member)
		// Identifying the matching Repository Path
		// based on 1) the scan result if enabled
		// 2) the type if set
		// 3) the last level qualifier of the containing dataset
		def matchingRepositoryPath = repositoryPathsMapping.repositoryPaths.find {repositoryPath ->
			(props.SCAN_DATASET_MEMBERS && props.SCAN_DATASET_MEMBERS.toBoolean() && repositoryPath.mvsMapping.scan ? repositoryPath.mvsMapping.scan.language.equals(scannedLanguage) && repositoryPath.mvsMapping.scan.fileType.equals(scannedFileType) : false) ||
			(repositoryPath.mvsMapping.types ? repositoryPath.mvsMapping.types.contains(memberType) : false) ||
			(repositoryPath.mvsMapping.datasetLastLevelQualifiers ? repositoryPath.mvsMapping.datasetLastLevelQualifiers.contains(lastQualifier) : false) 
		}
	
		def targetRepositoryPath
		def pdsEncoding
		def fileExtension
		def artifactsType
		def sourceGroup
		def language
		def languageProcessor
		if (matchingRepositoryPath) {
			// if Matching Repository Path found, we retrieve the information from the RepositoryPathsMapping file
			if (matchingRepositoryPath.toLowerCase && matchingRepositoryPath.toLowerCase.toBoolean()) {
				member = member.toLowerCase()
				lastQualifier = lastQualifier.toLowerCase()
			}
			fileExtension = (matchingRepositoryPath.fileExtension) ? (matchingRepositoryPath.fileExtension) : lastQualifier
			sourceGroup = (matchingRepositoryPath.sourceGroup) ? (matchingRepositoryPath.sourceGroup) : lastQualifier
			language = (matchingRepositoryPath.language) ? (matchingRepositoryPath.language) : lastQualifier
			languageProcessor = (matchingRepositoryPath.languageProcessor) ? (matchingRepositoryPath.languageProcessor) : lastQualifier + ".groovy"
			targetRepositoryPath = (matchingRepositoryPath.repositoryPath) ? matchingRepositoryPath.repositoryPath.replaceAll('\\$application',application) : "$application/$lastQualifier"
			pdsEncoding = (matchingRepositoryPath.encoding) ? (matchingRepositoryPath.encoding) : "IBM-1047"
			artifactsType = (matchingRepositoryPath.artifactsType) ? (matchingRepositoryPath.artifactsType) : lastQualifier

		} else {
			// if Matching Repository Path not found, we set default values based on the last qualifier of the dataset name
			member = member.toLowerCase()
			lastQualifier = lastQualifier.toLowerCase()
			fileExtension = lastQualifier				
			sourceGroup = lastQualifier
			language = lastQualifier
			languageProcessor = lastQualifier + ".groovy"
			targetRepositoryPath = "$application/$lastQualifier"
			pdsEncoding = "IBM-1047"
			artifactsType = lastQualifier
		}
		// Appending the dataset member to the Application Descriptor file
		applicationDescriptorUtils.appendFileDefinition(applicationDescriptor, sourceGroup, language, languageProcessor, artifactsType, fileExtension, targetRepositoryPath, member, memberType, "undefined")
		// Adding a line into the DBB Migration Mapping file
		targetRepositoryPath = props.DBB_MODELER_APPLICATION_DIR + "/" + application + "/" + targetRepositoryPath
		mappings.put(datasetMember, "$targetRepositoryPath/$member.$fileExtension pdsEncoding=$pdsEncoding")
	}
	
	// Writing the DBB Migration Mapping file
	try {
		mappingFile.withWriter("IBM-1047") { writer ->
			mappings.each() { datasetMember, target ->
				writer.write("$datasetMember $target\n");
			}
		}
		Process process = "chtag -tc IBM-1047 ${mappingFile.getAbsolutePath()}".execute()
		process.waitFor()   
		
	}
	catch (IOException e) {
		e.printStackTrace();
	}
	logger.logMessage("\tCreated DBB Migration Utility mapping file " + mappingFile.getAbsolutePath());
	// Writing the Application Descriptor file
	applicationDescriptorUtils.writeApplicationDescriptor(applicationDescriptorFile, applicationDescriptor)
	logger.logMessage("\tCreated Application Description file " + applicationDescriptorFile.getAbsolutePath());
}

def addDatasetMemberToApplication(String application, String datasetMember) {
	HashSet<String> applicationHashSet = applicationsToDatasetMembersMap.get(application)
	if (!application.equals("UNASSIGNED")) {
		HashSet<String> unassignedHashSet = applicationsToDatasetMembersMap.get("UNASSIGNED")
		if (unassignedHashSet && unassignedHashSet.contains(datasetMember)) {
			unassignedHashSet.remove(datasetMember)
		}
		if (!applicationHashSet) {
			applicationHashSet = new HashSet<String>();
			applicationsToDatasetMembersMap.put(application, applicationHashSet);
		}
		applicationHashSet.add(datasetMember);
	} else {
		HashSet<String> foundDatasetMembers = new HashSet<String>()
		applicationsToDatasetMembersMap.forEach { searchedApplication, searchedApplicationHashSet ->
			HashSet<String> foundDatasetMembersInSearchedApplication = searchedApplicationHashSet.findAll { searchedDatasetMember ->
				searchedDatasetMember.equals(datasetMember) && !searchedApplication.equals("UNASSIGNED")
			}
			foundDatasetMembers.addAll(foundDatasetMembersInSearchedApplication)
		}
		if (!foundDatasetMembers) {
			if (!applicationHashSet) {
				applicationHashSet = new HashSet<String>();
				applicationsToDatasetMembersMap.put(application, applicationHashSet);
			}
			applicationHashSet.add(datasetMember);
		}		
	}
}

def findMappedApplicationFromMemberName(ArrayList<Object> applicationsList, String memberName) {
	// Finding the owning application in the list of applications using the same dataset list
	def foundApplications = applicationsList.findAll { application ->
		application.namingConventions.find { namingConvention ->
			isFilterOnMemberMatching(memberName, namingConvention)
		}
	}

	if (foundApplications.size() == 1) { // one application claimed ownership
		// Finding other potential applications mapped to other datasets which could claim the ownership 
		ArrayList<Object> otherApplications = new ArrayList<Object>()
		datasetsMap.each() { datasets, otherApplicationsList ->
			def foundOtherApplications = otherApplicationsList.findAll { application ->
				//Skip applications that have the same name as the found application in the first pass
				if (!application.application.equals(foundApplications[0].application)) {
					application.namingConventions.find { namingConvention ->
						isFilterOnMemberMatching(memberName, namingConvention)
					}
				} 
			}
			if (foundOtherApplications) {
				otherApplications.addAll(foundOtherApplications)
			}
		}
		if (otherApplications.size() > 0) {
			logger.logMessage("*! [WARNING] Other applications claim ownership of member '$memberName' in other mappings:")
			otherApplications.each { application -> 
				logger.logMessage("\t\tClaiming ownership: '${application.application}'") 
			}
			logger.logMessage("*! [WARNING] The owner is identified as '${foundApplications[0].application}'")
		}
		return foundApplications[0].application
	} else if (foundApplications.size() > 1) { // multiple applications claimed ownership
		logger.logMessage("*! [WARNING] Multiple applications claim ownership of member '$memberName':")
		foundApplications.each { application -> 
			logger.logMessage("\t\tClaiming ownership: '${application.application}'")
		}
		logger.logMessage("*! [WARNING] The owner cannot be defined. Map '$memberName' to UNASSIGNED")
		return "UNASSIGNED"
	} else { // no match found
		return "UNASSIGNED"
	}
}

def findApplication(String applicationName) {
	def applications = new ArrayList<Object>()
	datasetsMap.each() { datasets, applicationsList ->
		def foundApplications = datasetsMap.get(datasets).findAll { application -> application.application.equals(applicationName) }
		applications.addAll(foundApplications)
	}
	if (applications && applications.size() == 1) {
		return applications[0]
	} else {
		return null
	}
}

def findDuplicateApplications() {
	def foundDuplicates = false
	ArrayList<Object> consolidatedApplicationsList = new ArrayList<Object>()
	datasetsMap.each() { datasets, applicationsList ->
		applicationsList.each() { application ->
			consolidatedApplicationsList.add(application)
		}
	}
	applicationsMap = consolidatedApplicationsList.groupBy { application -> application.application }
	applicationsMap.each() { application, applicationsList ->
		if (applicationsList.size() > 1) {
			logger.logMessage("*! [ERROR] Application '$application' is defined multiple times. Exiting.")
			foundDuplicates = true
		}
	}
	return foundDuplicates
}

// Add dataset to the list of datasets where the member can be found
def addDatasetToMember(String member, String dataset) {
	ArrayList<String> datasetsList = membersToDatasetsMap.get(member)
	if (!datasetsList) {
		datasetsList = new ArrayList<String>()
		membersToDatasetsMap.put(member, datasetsList)
	}
	datasetsList.add(dataset)
}

def findDuplicateProcessingOfMembers() {
	def foundDuplicates = false
	membersToDatasetsMap
	membersToDatasetsMap.each() { member, datasetsList ->
		if (datasetsList.size() > 1) {
			logger.logMessage("*! [WARNING] Member '$member' was processed multiple times:")
			datasetsList.each { dataset -> 
				logger.logMessage("\t\tFrom dataset '${dataset}'") 
			}
			foundDuplicates = true
		}
	}
	return foundDuplicates
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
		reader.close()
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

def scanDatasetMember(String datasetMemberToScan) {
	ZFile zFile = new ZFile(datasetMemberToScan, "r", ZFileConstants.FLAG_DISP_SHR)
	InputStream zFileInputStream = zFile.getInputStream();
	// Init scanner
	if (!scanner) scanner = initializeScanner()
	// Scan file
	Object scanMetadata = scanner.processSingleFile(zFileInputStream);
	SingleFilesMetadata dmhfile = (SingleFilesMetadata) scanMetadata;
	// Close file allocation	
	zFile.close()

	return [dmhfile.getLanguageCd(), dmhfile.getFileTypeCd()]
}

def initializeScanner() {
	ScanProperties scanProperties = new ScanProperties();
	scanProperties.setCodePage(props.SCAN_DATASET_MEMBERS_ENCODING);
	Dmh5210 dmh5210 = new Dmh5210();
	dmh5210.init(scanProperties);
	return dmh5210;
}

def calculateStorageSizeForMembers(HashSet<String> datasetMembers) {
	def storageSize = 0
	datasetMembers.forEach { datasetMember ->
		storageSize = storageSize + estimateDatasetMemberSize(datasetMember)
	}
	return storageSize
}

def estimateDatasetMemberSize(String datasetMember) {
	ZFile file = new ZFile(constructPDSForZFileOperation(datasetMember), "r")
	InputStreamReader streamReader = new InputStreamReader(file.getInputStream())
	long storageSize = 0
	long bytesSkipped = -1
	try {
		while (bytesSkipped != 0) {
			bytesSkipped = streamReader.skip(Long.MAX_VALUE)
			storageSize = storageSize + bytesSkipped
		}
		file.close()
		return storageSize
	} catch (IOException exception) {
		logger.logMessage("*! [WARNING] Unable to retrieve the estimated storage size for '$dataset($member)'")
		return 0
	}
}