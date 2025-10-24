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
import java.nio.file.*
import groovy.cli.commons.*


// Reads a HashMap from the MEMBER_TYPE_MAPPING file with comma separator (',') and returns it
def loadTypes(String APPLICATION_MEMBER_TYPE_MAPPING) {
	HashMap<String, String> types = new HashMap<>();
	String line;
	File applicationMemberTypeMappingFile = new File(APPLICATION_MEMBER_TYPE_MAPPING)
	if (!applicationMemberTypeMappingFile.exists()) {
		logger.logMessage("*! [WARNING] The Application Member Type Mapping file $APPLICATION_MEMBER_TYPE_MAPPING was not found. Exiting.")
		System.exit(1)
	} else {		
		def yamlSlurper = new groovy.yaml.YamlSlurper()
		applicationMemberTypeMappingFile.withReader("UTF-8") { reader ->
			while ((line = reader.readLine()) != null) {
				String[] keyValuePair = line.split(",", 2);
				if (keyValuePair.length > 1) {
					String key = keyValuePair[0].trim().toUpperCase();
					String value = keyValuePair[1].trim().replaceAll(" ", "");
					types.put(key, value);
				}
			}
		}
	}
	return types
}

def getType(HashMap<String, String> types, String member) {
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

/*
 * relativizePath - converts an absolute path to a relative path from the workspace directory
 */
def relativizePath(String path, String root) {
	if (!path.startsWith('/'))
		return path
	String relPath = new File(root).toURI().relativize(new File(path.trim()).toURI()).getPath()
	// Directories have '/' added to the end.  Lets remove it.
	if (relPath.endsWith('/'))
		relPath = relPath.take(relPath.length()-1)
	return relPath
}


/**
 * Get list of files relative to DBB_MODELER_APPLICATION_DIR
 */
def getMappedFilesFromApplicationDescriptor(String DBB_MODELER_APPLICATION_DIR, String application, applicationDescriptor, logger) {
	HashSet<String> filesList = new HashSet<String>()

	Files.walk(Paths.get("${DBB_MODELER_APPLICATION_DIR}/${application}")).forEach { filePath ->
		if (Files.isRegularFile(filePath)) {
			relativeFilePathFromAppDir = relativizePath(filePath.toString(), DBB_MODELER_APPLICATION_DIR)
			relativeFilePathFromApplication = relativizePath(filePath.toString(), "${DBB_MODELER_APPLICATION_DIR}/${application}")
			
			genericRepositoryFolder = Paths.get(relativeFilePathFromApplication.toString()).getParent().toString()
			def matchingApplicationDescriptorEntry = applicationDescriptor.sources.find() { sourceDefinition ->
				sourceDefinition.repositoryPath.equals(genericRepositoryFolder)
			}
			if (matchingApplicationDescriptorEntry) {
				filesList.add(relativeFilePathFromAppDir)
			} else {
				if (logger) {
					logger.logSilentMessage("[INFO] No matching Repository Path was found for file '${filePath}'. Skipping.")
				}
			}
		}
	}
	return filesList
}

/**
 * Get list of files relative to DBB_MODELER_APPLICATION_DIR
 */
def getMappedFilesFromApplicationDir(String DBB_MODELER_APPLICATION_DIR, String application, repositoryPathsMapping, logger) {
	HashMap<String, String> filesMap = new HashMap<String, String>()

	Files.walk(Paths.get("${DBB_MODELER_APPLICATION_DIR}/${application}")).forEach { filePath ->
		if (Files.isRegularFile(filePath)) {
			relativeFilePathFromAppDir = relativizePath(filePath.toString(), DBB_MODELER_APPLICATION_DIR)
			relativeFilePathFromApplication = relativizePath(filePath.toString(), "${DBB_MODELER_APPLICATION_DIR}/${application}")
			
			genericRepositoryFolder = Paths.get(relativeFilePathFromApplication.toString().replace(application, "\$application")).getParent().toString()
			def matchingRepositoryPaths = repositoryPathsMapping.repositoryPaths.find() { repositoryPath ->
				repositoryPath.repositoryPath.equals(genericRepositoryFolder)
			}
			if (matchingRepositoryPaths) {
				filesMap.put(relativeFilePathFromAppDir, matchingRepositoryPaths.repositoryPath)
			} else {
				if (logger) {
					logger.logSilentMessage("[INFO] No matching Repository Path was found for file '${filePath}'. Skipping.")
				}
			}
		}
	}
	return filesMap
}