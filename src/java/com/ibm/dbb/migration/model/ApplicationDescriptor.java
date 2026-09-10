/********************************************************************************
 * Licensed Materials - Property of IBM                                          *
 * (c) Copyright IBM Corporation 2018, 2024. All Rights Reserved.                *
 *                                                                               *
 * Note to U.S. Government Users Restricted Rights:                              *
 * Use, duplication or disclosure restricted by GSA ADP Schedule                 *
 * Contract with IBM Corp.                                                       *
 ********************************************************************************/

package com.ibm.dbb.migration.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Application Descriptor model class.
 * Represents the structure of an application descriptor YAML file.
 */
public class ApplicationDescriptor {
    private String application;
    private String schemaVersion = "applicationDescriptor/0.11.0";
    private String description;
    private String owner;
    private List<Source> sources = new ArrayList<>();
    private List<Baseline> baselines = new ArrayList<>();
    private List<DependencyDescriptor> dependencies = new ArrayList<>();
    private List<Consumer> consumers = new ArrayList<>();

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public List<Source> getSources() {
        return sources;
    }

    public void setSources(List<Source> sources) {
        this.sources = sources;
    }

    public List<Baseline> getBaselines() {
        return baselines;
    }

    public void setBaselines(List<Baseline> baselines) {
        this.baselines = baselines;
    }

    public List<DependencyDescriptor> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<DependencyDescriptor> dependencies) {
        this.dependencies = dependencies;
    }

    public List<Consumer> getConsumers() {
        return consumers;
    }

    public void setConsumers(List<Consumer> consumers) {
        this.consumers = consumers;
    }

    /**
     * Source group definition
     */
    public static class Source {
        private String name;
        private String repositoryPath;
        private String language;
        private String languageProcessor;
        private String fileExtension;
        private String artifactsType;
        private List<FileDef> files = new ArrayList<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getRepositoryPath() {
            return repositoryPath;
        }

        public void setRepositoryPath(String repositoryPath) {
            this.repositoryPath = repositoryPath;
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

        public String getFileExtension() {
            return fileExtension;
        }

        public void setFileExtension(String fileExtension) {
            this.fileExtension = fileExtension;
        }

        public String getArtifactsType() {
            return artifactsType;
        }

        public void setArtifactsType(String artifactsType) {
            this.artifactsType = artifactsType;
        }

        public List<FileDef> getFiles() {
            return files;
        }

        public void setFiles(List<FileDef> files) {
            this.files = files;
        }
    }

    /**
     * File definition
     */
    public static class FileDef {
        private String name;
        private String type;
        private String usage;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getUsage() {
            return usage;
        }

        public void setUsage(String usage) {
            this.usage = usage;
        }
    }

    /**
     * Baseline definition
     */
    public static class Baseline {
        private String branch;
        private String type;
        private String reference;
        private String buildid;

        public String getBranch() {
            return branch;
        }

        public void setBranch(String branch) {
            this.branch = branch;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getReference() {
            return reference;
        }

        public void setReference(String reference) {
            this.reference = reference;
        }

        public String getBuildid() {
            return buildid;
        }

        public void setBuildid(String buildid) {
            this.buildid = buildid;
        }
    }

    /**
     * Dependency descriptor
     */
    public static class DependencyDescriptor {
        private String name;
        private String type;
        private String reference;
        private String buildid;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getReference() {
            return reference;
        }

        public void setReference(String reference) {
            this.reference = reference;
        }

        public String getBuildid() {
            return buildid;
        }

        public void setBuildid(String buildid) {
            this.buildid = buildid;
        }
    }

    /**
     * Consumer definition
     */
    public static class Consumer {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}