/********************************************************************************
 * Licensed Materials - Property of IBM                                          *
 * (c) Copyright IBM Corporation 2018, 2024. All Rights Reserved.                *
 *                                                                               *
 * Note to U.S. Government Users Restricted Rights:                              *
 * Use, duplication or disclosure restricted by GSA ADP Schedule                 *
 * Contract with IBM Corp.                                                       *
 ********************************************************************************/

package com.ibm.dbb.migration.model;

import java.util.List;

/**
 * Repository Paths Mapping model class.
 * Represents the structure of the repositoryPathsMapping YAML file.
 */
public class RepositoryPathsMapping {
    private List<RepositoryPath> repositoryPaths;

    public List<RepositoryPath> getRepositoryPaths() {
        return repositoryPaths;
    }

    public void setRepositoryPaths(List<RepositoryPath> repositoryPaths) {
        this.repositoryPaths = repositoryPaths;
    }

    /**
     * Repository Path definition
     */
    public static class RepositoryPath {
        private String repositoryPath;
        private String fileExtension;
        private String sourceGroup;
        private String language;
        private String languageProcessor;
        private String encoding;
        private String artifactsType;
        private boolean lowercase;
        private MvsMapping mvsMapping;

        public String getRepositoryPath() {
            return repositoryPath;
        }

        public void setRepositoryPath(String repositoryPath) {
            this.repositoryPath = repositoryPath;
        }

        public String getFileExtension() {
            return fileExtension;
        }

        public void setFileExtension(String fileExtension) {
            this.fileExtension = fileExtension;
        }

        public String getSourceGroup() {
            return sourceGroup;
        }

        public void setSourceGroup(String sourceGroup) {
            this.sourceGroup = sourceGroup;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public String getLanguageProcessor() {
            return languageProcessor;
        }

        public void setLanguageProcessor(String languageProcessor) {
            this.languageProcessor = languageProcessor;
        }

        public String getEncoding() {
            return encoding;
        }

        public void setEncoding(String encoding) {
            this.encoding = encoding;
        }

        public String getArtifactsType() {
            return artifactsType;
        }

        public void setArtifactsType(String artifactsType) {
            this.artifactsType = artifactsType;
        }

        public boolean isLowercase() {
            return lowercase;
        }

        public void setLowercase(boolean lowercase) {
            this.lowercase = lowercase;
        }

        // Alias for YAML compatibility (toLowerCase in YAML maps to lowercase in Java)
        public boolean isToLowerCase() {
            return lowercase;
        }

        public void setToLowerCase(boolean toLowerCase) {
            this.lowercase = toLowerCase;
        }

        public MvsMapping getMvsMapping() {
            return mvsMapping;
        }

        public void setMvsMapping(MvsMapping mvsMapping) {
            this.mvsMapping = mvsMapping;
        }
    }

    /**
     * MVS Mapping definition
     */
    public static class MvsMapping {
        private List<String> types;
        private List<String> datasetLastLevelQualifiers;
        private Scan scan;

        public List<String> getTypes() {
            return types;
        }

        public void setTypes(List<String> types) {
            this.types = types;
        }

        public List<String> getDatasetLastLevelQualifiers() {
            return datasetLastLevelQualifiers;
        }

        public void setDatasetLastLevelQualifiers(List<String> datasetLastLevelQualifiers) {
            this.datasetLastLevelQualifiers = datasetLastLevelQualifiers;
        }

        public Scan getScan() {
            return scan;
        }

        public void setScan(Scan scan) {
            this.scan = scan;
        }
    }

    /**
     * Scan definition
     */
    public static class Scan {
        private String language;
        private String fileType;

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public String getFileType() {
            return fileType;
        }

        public void setFileType(String fileType) {
            this.fileType = fileType;
        }
    }
}