@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import groovy.transform.*
import groovy.lang.GroovyShell
import groovy.util.*
import com.ibm.dbb.metadata.*

/**
 * Utilities to manage the Metadatastore 
 */
 
@Field def metadataStore

def initializeFileMetadataStore(String fileMetadataStoreLocation) {
	metadataStore = MetadataStoreFactory.createFileMetadataStore(fileMetadataStoreLocation)
}

def initializeDb2MetadataStore(String db2User, String db2Password, Properties db2ConnectionProps) {
	metadataStore = MetadataStoreFactory.createDb2MetadataStore(db2User, db2Password, db2ConnectionProps)
}

def initializeDb2MetadataStore(String db2User, File db2PasswordFile, Properties db2ConnectionProps) {
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