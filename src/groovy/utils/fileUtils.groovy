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
def loadFilesToTypesMapping(String APPLICATION_FILES_TYPES_MAPPING) {
	File filesToTypeMappingFile = new File(APPLICATION_FILES_TYPES_MAPPING)
	if (!filesToTypeMappingFile.exists()) {
		logger.logMessage("*! [WARNING] The Files to Types Mapping file '$APPLICATION_FILES_TYPES_MAPPING' was not found. Exiting.")
		return null
	} else {		
		def yamlSlurper = new groovy.yaml.YamlSlurper()
        return yamlSlurper.parse(filesToTypeMappingFile).files
	}
}

def getType(ArrayList filesToTypes, String file) {
	if (!filesToTypes) {
		return "UNKNOWN"
	} else {
		def foundFile = filesToTypes.find { fileToTypes ->
		  fileToTypes.file.equalsIgnoreCase(file)
		} 
		if (foundFile) {
			return foundFile.types.toString().replaceAll("\\[", "").replaceAll("\\]", "").replaceAll(" ", "")
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