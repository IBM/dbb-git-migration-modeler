/********************************************************************************
 * Licensed Materials - Property of IBM                                          *
 * (c) Copyright IBM Corporation 2018, 2024. All Rights Reserved.                *
 *                                                                               *
 * Note to U.S. Government Users Restricted Rights:                              *
 * Use, duplication or disclosure restricted by GSA ADP Schedule                 *
 * Contract with IBM Corp.                                                       *
 ********************************************************************************/

package com.ibm.dbb.migration.utils;

import com.ibm.dbb.migration.model.ApplicationDescriptor;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility class for file operations related to application descriptors.
 */
public class FileUtility {
    
    /**
     * Get all files mapped in the application descriptor
     * @param applicationDirectory Root directory of the application
     * @param descriptor Application descriptor containing source definitions
     * @param logger Logger for messages (can be null)
     * @return Set of relative file paths (relative to applicationDirectory)
     * @throws IOException if file system operations fail
     */
    public Set<String> getMappedFilesFromApplicationDescriptor(String applicationDirectory,
                                                                ApplicationDescriptor descriptor,
                                                                Logger logger) throws IOException {
        Set<String> files = new HashSet<>();
        
        if (descriptor == null || descriptor.getSources() == null) {
            return files;
        }
        
        // Walk the file system to find actual files
        Path appDirPath = Paths.get(applicationDirectory);
        Files.walk(appDirPath)
            .filter(Files::isRegularFile)
            .forEach(filePath -> {
                try {
                    String relativeFilePath = relativizePath(filePath.toString(), applicationDirectory);
                    Path relativeFilePathObj = Paths.get(relativeFilePath);
                    String relativeDirectory = relativeFilePathObj.getParent() != null ?
                        relativeFilePathObj.getParent().toString() : "";
                    
                    // Find matching source definition
                    boolean matched = descriptor.getSources().stream()
                        .anyMatch(source -> source.getRepositoryPath().equals(relativeDirectory));
                    
                    if (matched) {
                        // Add relative path instead of absolute path (matching Groovy behavior)
                        files.add(relativeFilePath);
                    } else {
                        if (logger != null) {
                            logger.logSilentMessage("[INFO] No matching Repository Path was found for file '" +
                                filePath + "'. Skipping.");
                        }
                    }
                } catch (Exception e) {
                    if (logger != null) {
                        logger.logMessage("*! [WARNING] Error processing file: " + filePath + " - " + e.getMessage());
                    }
                }
            });
        
        return files;
    }
    
    /**
     * Convert an absolute path to a relative path
     * @param path Absolute path
     * @param root Root directory
     * @return Relative path with forward slashes
     */
    private String relativizePath(String path, String root) {
        if (!path.startsWith("/") && !path.matches("^[A-Za-z]:.*")) {
            return path;
        }
        try {
            Path rootPath = Paths.get(root);
            Path filePath = Paths.get(path.trim());
            Path relativePath = rootPath.relativize(filePath);
            return relativePath.toString().replace('\\', '/');
        } catch (Exception e) {
            return path;
        }
    }
    
    /**
     * Copy a file while preserving USS file tags on z/OS systems.
     * Uses DBB FileUtils newReader/newWriter to properly handle encoding and file tags.
     *
     * @param sourceFile Source file to copy from
     * @param targetFile Target file to copy to
     * @throws IOException if copy operation fails
     */
    public static void copyFileWithTags(File sourceFile, File targetFile) throws IOException {
        // Get the source file's encoding tag
        String fileTag = null;
        try {
            fileTag = com.ibm.dbb.utils.FileUtils.getFileTag(sourceFile.getAbsolutePath());
        } catch (Exception e) {
            fileTag = "UTF-8";
        }
             
        // Copy file using DBB FileUtils to preserve encoding
        try (Reader r = com.ibm.dbb.utils.FileUtils.newReader(sourceFile.getAbsolutePath(), fileTag);
             Writer w = com.ibm.dbb.utils.FileUtils.newWriter(targetFile.getAbsolutePath(), fileTag)) {
            
            BufferedReader reader = new BufferedReader(r);
            BufferedWriter writer = new BufferedWriter(w);
            
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (!firstLine) {
                    writer.newLine();
                }
                writer.write(line);
                firstLine = false;
            }
            writer.flush();
            com.ibm.dbb.utils.FileUtils.setFileTag(targetFile.getAbsolutePath(), fileTag);
        }
    }
    
    /**
     * Copy a file while preserving USS file tags on z/OS systems.
     * Uses DBB FileUtils to get and set file tags to ensure proper encoding is maintained.
     *
     * @param sourcePath Source file path to copy from
     * @param targetPath Target file path to copy to
     * @throws IOException if copy operation fails
     */
    public static void copyFileWithTags(Path sourcePath, Path targetPath) throws IOException {
        copyFileWithTags(sourcePath.toFile(), targetPath.toFile());
    }
}