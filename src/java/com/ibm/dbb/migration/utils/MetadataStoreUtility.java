/********************************************************************************
 * Licensed Materials - Property of IBM                                          *
 * (c) Copyright IBM Corporation 2018, 2024. All Rights Reserved.                *
 *                                                                               *
 * Note to U.S. Government Users Restricted Rights:                              *
 * Use, duplication or disclosure restricted by GSA ADP Schedule                 *
 * Contract with IBM Corp.                                                       *
 ********************************************************************************/

package com.ibm.dbb.migration.utils;

import com.ibm.dbb.build.BuildException;
import com.ibm.dbb.dependency.LogicalFile;
import com.ibm.dbb.metadata.BuildGroup;
import com.ibm.dbb.metadata.Collection;
import com.ibm.dbb.metadata.MetadataStore;
import com.ibm.dbb.metadata.MetadataStoreFactory;

import java.io.File;
import java.util.List;
import java.util.Properties;

/**
 * Utility class for DBB MetadataStore operations.
 * Provides methods to initialize and interact with file-based or DB2-based metadata stores.
 */
public class MetadataStoreUtility {
    private MetadataStore metadataStore;
    
    /**
     * Initialize a file-based metadata store
     * @param directory Directory path for the file metadata store
     * @throws BuildException if initialization fails
     */
    public void initializeFileMetadataStore(String directory) throws BuildException {
        metadataStore = MetadataStoreFactory.createFileMetadataStore(directory);
    }
    
    /**
     * Initialize a DB2-based metadata store with password file
     * @param jdbcId JDBC user ID
     * @param passwordFile File containing the password
     * @param db2Props DB2 connection properties
     * @throws BuildException if initialization fails
     */
    public void initializeDb2MetadataStoreWithPasswordFile(String jdbcId, File passwordFile,
                                                           Properties db2Props) throws BuildException {
        metadataStore = MetadataStoreFactory.createDb2MetadataStore(jdbcId, passwordFile, db2Props);
    }
    
    /**
     * Verifies MetadataStore connectivity by creating a dummy build group and collection,
     * then deleting them. Mirrors the Groovy verifyDBBMetadatastoreConnectivity() logic.
     *
     * @throws BuildException if any MetadataStore operation fails
     */
    public void verifyConnectivity() throws BuildException {
        if (metadataStore == null) {
            throw new IllegalStateException("MetadataStore not initialized");
        }

        System.out.println("** Verifying DBB Metadatastore connection and permissions");

        Collection collection = createCollection("DummyCollection-main", "DummyCollection-main");
        if (collection != null) {
            System.out.println("** Successfully created the Dummy Collection and Build Group.");
        }

        deleteBuildGroup("DummyCollection-main");
        System.out.println("** Successfully deleted the Dummy Collection and Build Group.");
        System.out.println("** DBB MetadataStore operations successfully executed. The DBB Git Migration Modeler can be used with the provided configuration!");
    }

    /**
     * Delete a build group if it exists
     * @param name Build group name
     * @throws BuildException if deletion fails
     */
    public void deleteBuildGroup(String name) throws BuildException {
        if (metadataStore != null && metadataStore.buildGroupExists(name)) {
            metadataStore.deleteBuildGroup(name);
        }
    }
    
    /**
     * Create a collection within a build group
     * @param buildGroupName Build group name
     * @param collectionName Collection name
     * @return The created collection
     * @throws BuildException if creation fails
     */
    public Collection createCollection(String buildGroupName, String collectionName) throws BuildException {
        if (metadataStore == null) {
            throw new IllegalStateException("MetadataStore not initialized");
        }
        
        BuildGroup buildGroup;
        if (!metadataStore.buildGroupExists(buildGroupName)) {
            buildGroup = metadataStore.createBuildGroup(buildGroupName);
        } else {
            buildGroup = metadataStore.getBuildGroup(buildGroupName);
        }
        
        if (buildGroup.collectionExists(collectionName)) {
            buildGroup.deleteCollection(collectionName);
        }
        
        return buildGroup.createCollection(collectionName);
    }
    
    /**
     * Get all build groups from the metadata store
     * @return List of build groups
     * @throws BuildException if retrieval fails
     */
    public List<BuildGroup> getBuildGroups() throws BuildException {
        if (metadataStore == null) {
            throw new IllegalStateException("MetadataStore not initialized");
        }
        return metadataStore.getBuildGroups();
    }
    
    /**
     * Get a logical file from a specific collection
     * @param file File path
     * @param buildGroup Build group name
     * @param collection Collection name
     * @return The logical file
     * @throws BuildException if retrieval fails
     */
    public LogicalFile getLogicalFile(String file, String buildGroup, String collection) throws BuildException {
        if (metadataStore == null) {
            throw new IllegalStateException("MetadataStore not initialized");
        }
        return metadataStore.getBuildGroup(buildGroup).getCollection(collection).getLogicalFile(file);
    }
    
    /**
     * Set the owner of a build group (DB2 metadata store only)
     * Note: The DBB API does not provide a direct setOwner method on BuildGroup.
     * This method is a placeholder for future DBB API enhancements.
     * For now, it validates the build group exists but does not set the owner.
     *
     * @param buildGroupName Build group name
     * @param owner Owner user ID
     * @throws BuildException if operation fails
     */
    public void setBuildGroupOwner(String buildGroupName, String owner) throws BuildException {
        if (metadataStore == null) {
            throw new IllegalStateException("MetadataStore not initialized");
        }
        
        if (!metadataStore.buildGroupExists(buildGroupName)) {
            throw new BuildException("Build group does not exist: " + buildGroupName);
        }
        
        // Note: The DBB BuildGroup API does not currently provide a setOwner() method
        // This functionality may need to be implemented via direct DB2 SQL updates
        // or wait for future DBB API enhancements
        // For now, we just validate the build group exists
    }
    
    /**
     * Get the underlying metadata store instance
     * @return The metadata store
     */
    public MetadataStore getMetadataStore() {
        return metadataStore;
    }
    
    /**
     * Move a logical file from one build group/collection to another.
     * This replicates the Groovy behavior:
     * 1. Delete the logical file from the source collection
     * 2. Rescan the file at the target location (captures all dependencies automatically)
     * 3. Add the rescanned logical file to the target collection
     *
     * @param applicationDir Base application directory
     * @param sourceFilePath Source file path (relative to application)
     * @param sourceBuildGroup Source build group name
     * @param sourceCollection Source collection name
     * @param targetFilePath Target file path (relative to application)
     * @param targetBuildGroup Target build group name
     * @param targetCollection Target collection name
     * @throws BuildException if the move operation fails
     */
    public void moveLogicalFile(String applicationDir, String sourceFilePath,
                               String sourceBuildGroup, String sourceCollection,
                               String targetFilePath, String targetBuildGroup,
                               String targetCollection, boolean scanControlTransfers) throws BuildException {
        if (metadataStore == null) {
            throw new IllegalStateException("MetadataStore not initialized");
        }
        
        // Step 1: Delete logical file from source collection
        boolean deleteSuccessful = deleteLogicalFile(applicationDir + "/" + sourceFilePath,
            sourceBuildGroup, sourceCollection);
        
        if (!deleteSuccessful) {
            // If deletion failed, the file might not exist in source - continue anyway
            return;
        }
        
        // Step 2: Scan the file at the target location (this captures all dependencies)
        LogicalFile scannedLogicalFile = scanFile(applicationDir, targetFilePath, scanControlTransfers);
        
        if (scannedLogicalFile == null) {
            throw new BuildException("Failed to scan file at target location: " + targetFilePath);
        }
        
        // Step 3: Add the scanned logical file to the target build group/collection
        addLogicalFile(scannedLogicalFile, targetBuildGroup, targetCollection);
    }
    
    /**
     * Delete a logical file from a collection.
     *
     * @param file Full file path
     * @param buildGroupName Build group name
     * @param collectionName Collection name
     * @return true if deletion was successful, false otherwise
     * @throws BuildException if operation fails
     */
    private boolean deleteLogicalFile(String file, String buildGroupName, String collectionName)
            throws BuildException {
        BuildGroup buildGroup = metadataStore.getBuildGroup(buildGroupName);
        if (buildGroup == null) {
            return false;
        }
        
        com.ibm.dbb.metadata.Collection collection = buildGroup.getCollection(collectionName);
        if (collection == null) {
            return false;
        }
        
        LogicalFile logicalFile = collection.getLogicalFile(file);
        if (logicalFile != null) {
            collection.deleteLogicalFile(file);
            return true;
        }
        
        return false;
    }
    
    /**
     * Scan a file to create a LogicalFile with all dependencies.
     *
     * @param workspace Workspace directory
     * @param file File path relative to workspace
     * @return Scanned LogicalFile or null if scan fails
     */
    private LogicalFile scanFile(String workspace, String file, boolean scanControlTransfers) {
        LogicalFile logicalFile = null;
        com.ibm.dbb.dependency.DependencyScanner scanner = new com.ibm.dbb.dependency.DependencyScanner();
        
        // Enabling Control Transfer flag in DBB Scanner
        scanner.setCollectControlTransfers(String.valueOf(scanControlTransfers));
        
        try {
            logicalFile = scanner.scan(file, workspace);
        } catch (Exception e) {
            // Scan failed - return null
            logicalFile = null;
        }
        
        return logicalFile;
    }
    
    /**
     * Add a logical file to a target collection.
     *
     * @param logicalFile The logical file to add
     * @param buildGroupName Target build group name
     * @param collectionName Target collection name
     * @return true if addition was successful
     * @throws BuildException if operation fails
     */
    private boolean addLogicalFile(LogicalFile logicalFile, String buildGroupName,
                                   String collectionName) throws BuildException {
        // Ensure target build group exists
        BuildGroup buildGroup;
        if (!metadataStore.buildGroupExists(buildGroupName)) {
            buildGroup = metadataStore.createBuildGroup(buildGroupName);
        } else {
            buildGroup = metadataStore.getBuildGroup(buildGroupName);
        }
        
        if (buildGroup == null) {
            return false;
        }
        
        // Ensure target collection exists
        com.ibm.dbb.metadata.Collection collection;
        if (!buildGroup.collectionExists(collectionName)) {
            collection = buildGroup.createCollection(collectionName);
        } else {
            collection = buildGroup.getCollection(collectionName);
        }
        
        if (collection == null) {
            return false;
        }
        
        // Add the logical file to the collection
        collection.addLogicalFile(logicalFile);
        
        return true;
    }
}