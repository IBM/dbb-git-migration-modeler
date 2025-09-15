@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import groovy.transform.*
import groovy.lang.GroovyShell
import groovy.util.*
import com.ibm.dbb.metadata.*
import com.ibm.dbb.dependency.*

/**
 * Utilities to manage the Metadatastore 
 */

@Field def metadataStore
@Field def logger = loadScript(new File("logger.groovy"))

def initializeFileMetadataStore(String fileMetadataStoreLocation) {
	metadataStore = MetadataStoreFactory.createFileMetadataStore(fileMetadataStoreLocation)
}

def initializeDb2MetadataStoreWithPasswordFile(String db2User, File db2PasswordFile, Properties db2ConnectionProps) {
	metadataStore = MetadataStoreFactory.createDb2MetadataStore(db2User, db2PasswordFile, db2ConnectionProps)
}

def deleteBuildGroup(String buildGroupName) {
	if (metadataStore) {
		if (metadataStore.buildGroupExists(buildGroupName)) {
			metadataStore.deleteBuildGroup(buildGroupName)
		}
	}
}

def createCollection(String buildGroupName, String collectionName) {
	if (metadataStore) {
		BuildGroup buildGroup
		if (!metadataStore.buildGroupExists(buildGroupName)) {
			buildGroup = metadataStore.createBuildGroup(buildGroupName)
		} else {
			buildGroup = metadataStore.getBuildGroup(buildGroupName)
		}
		if (buildGroup.collectionExists(collectionName)) {
			buildGroup.deleteCollection(collectionName)
		}
		return buildGroup.createCollection(collectionName)
	} else {
		return null
	}
}

def setCollectionOwner(String buildGroupName, String collectionName, String owner) {
	if (metadataStore) {
		if (metadataStore.buildGroupExists(buildGroupName)) {
			BuildGroup buildGroup = metadataStore.getBuildGroup(buildGroupName)
			if (buildGroup.collectionExists(collectionName)) {
				buildGroup.getCollection(collectionName).setOwner(owner)
			}
		}
	}
}

def getBuildGroups() {
	return metadataStore.getBuildGroups()
}

def moveLogicalFile(String workspace, String file, String sourceBuildGroupName, String sourceCollectionName, String targetFilePath, String targetBuildGroupName, String targetCollectionName) {

	def successful = true
	def logicalFile

	// delete logical file from source collection
	successful = deleteLogicalFile(file, sourceBuildGroupName, sourceCollectionName)

	// scan file
	if (successful.toBoolean()) {
		logicalFile = scanFile(workspace, targetFilePath)
	}

	// add file to target buildgroup
	if (successful.toBoolean() && logicalFile) {
		addLogicalFile(logicalFile, targetBuildGroupName, targetCollectionName)
	}
}

def deleteLogicalFile(String file, String sourceBuildGroupName,  String sourceCollectionName) {
	sourceBuildGroup = metadataStore.getBuildGroup(sourceBuildGroupName)
	if (sourceBuildGroup) {
		sourceCollection = sourceBuildGroup.getCollection(sourceCollectionName)
		if (sourceCollection){
			if (sourceCollection.getLogicalFile(file)){
				sourceCollection.deleteLogicalFile(file)
				logger.logMessage("\t==> Removed '$file' from collection '$sourceCollectionName' in buildgroup '$sourceBuildGroupName' .")
				return true
			} else {
				logger.logMessage("\t*! [WARNING] The source collection '$sourceCollectionName' does not contain a logical file for '$file' .")
			}
		} else {
			logger.logMessage("\t*! [ERROR] The source collection '$sourceCollectionName' in buildgroup '$sourceBuildGroupName' could not be found .")
		}
	} else {
		logger.logMessage("\t*! [ERROR] The source buildgroup '$sourceBuildGroupName' could not be found .")
	}

	return false
}

def addLogicalFile(LogicalFile file, String targetBuildGroupName,  String targetCollectionName) {
	targetBuildGroup = metadataStore.getBuildGroup(targetBuildGroupName)

	if (targetBuildGroup) {
		targetCollection = targetBuildGroup.getCollection(targetCollectionName)
		if (targetCollection){
			targetCollection.addLogicalFile(file)
			logger.logMessage("\t==> Saved scanned file '${file.getFile()}' to buildgroup '$targetBuildGroupName'.")
			
			return true
		} else {
			logger.logMessage("\t*! [ERROR] The source collection '$targetCollectionName' in buildgroup '$targetBuildGroupName' could not be found.")
		}
	} else {
		logger.logMessage("\t*! [ERROR] The source buildgroup '$targetBuildGroupName' could not be found .")
	}
}

/**
 * 
 */
def scanFile(String workspace, String file) {
	LogicalFile logicalFile = null
	DependencyScanner scanner = new DependencyScanner()
	// Enabling Control Transfer flag in DBB Scanner
	scanner.setCollectControlTransfers("true")
	//logger.logMessage("\t*! [INFO] Scanning file $file ")
	try {
		logicalFile = scanner.scan(file, workspace)
	} catch (Exception e) {
		logger.logMessage("\t*! [ERROR] Something went wrong when scanning the file '$file'.")
		logger.logMessage(e.getMessage())
	}
	return logicalFile
}