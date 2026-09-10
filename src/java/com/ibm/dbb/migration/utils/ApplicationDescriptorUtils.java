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
import com.ibm.dbb.migration.model.ApplicationDescriptor.*;
import com.ibm.dbb.utils.FileUtils;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;
import com.ibm.dbb.migration.utils.ApplicationDescriptorRepresenter;

import java.io.*;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * Utilities to read, update or export existing ApplicationDescriptor from/to YAML.
 * Corresponds to the applicationDescriptorUtils.groovy utility script.
 */
public class ApplicationDescriptorUtils {

    /**
     * Read an existing application descriptor YAML file
     * @param yamlFile The YAML file to read
     * @return ApplicationDescriptor object
     */
    public ApplicationDescriptor readApplicationDescriptor(File yamlFile) throws IOException {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setTagInspector(tag -> false);
        Constructor constructor = new Constructor(ApplicationDescriptor.class, loaderOptions);
        Yaml yaml = new Yaml(constructor);
        try (FileReader reader = new FileReader(yamlFile)) {
            return yaml.loadAs(reader, ApplicationDescriptor.class);
        }
    }

    /**
     * Write an ApplicationDescriptor object to a YAML file
     * @param yamlFile The file to write to
     * @param applicationDescriptor The ApplicationDescriptor to write
     */
    public void writeApplicationDescriptor(File yamlFile, ApplicationDescriptor applicationDescriptor) throws IOException {
        // Sort source groups and files by name before writing
        if (applicationDescriptor.getSources() != null) {
            applicationDescriptor.getSources().sort(Comparator.comparing(Source::getName));
            for (Source source : applicationDescriptor.getSources()) {
                if (source.getFiles() != null) {
                    source.getFiles().sort(Comparator.comparing(FileDef::getName));
                }
            }
        }

        // Configure YAML dumper options
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);

        ApplicationDescriptorRepresenter representer = new ApplicationDescriptorRepresenter(options);
//        representer.addClassTag(ApplicationDescriptor.class, Tag.MAP);

        Yaml yaml = new Yaml(representer, options);

        // Write to file
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(yamlFile), "UTF-8"))) {
            yaml.dump(applicationDescriptor, writer);
        }

        FileUtils.setFileTag(yamlFile.getAbsolutePath(), "UTF-8");
    }

    /**
     * Append a file definition to the application descriptor
     * @param applicationDescriptor The application descriptor to update
     * @param sourceGroupName The source group name
     * @param language The language
     * @param languageProcessor The language processor
     * @param artifactsType The artifacts type
     * @param fileExtension The file extension
     * @param repositoryPath The repository path
     * @param name The file name
     * @param type The file type
     * @param usage The file usage
     */
    public void appendFileDefinition(ApplicationDescriptor applicationDescriptor, String sourceGroupName,
                                     String language, String languageProcessor, String artifactsType,
                                     String fileExtension, String repositoryPath, String name,
                                     String type, String usage) {

        FileDef fileRecord = new FileDef();
        fileRecord.setName(name);
        fileRecord.setType(type);
        fileRecord.setUsage(usage);

        if (applicationDescriptor.getSources() == null) {
            applicationDescriptor.setSources(new ArrayList<>());
        }

        Source existingSourceGroup = null;
        for (Source source : applicationDescriptor.getSources()) {
            if (source.getName().equals(sourceGroupName)) {
                existingSourceGroup = source;
                break;
            }
        }

        if (existingSourceGroup != null) {
            // Append file record definition to existing sourceGroup
            FileDef existingFileRecord = null;
            for (FileDef file : existingSourceGroup.getFiles()) {
                if (file.getName().equals(fileRecord.getName())) {
                    existingFileRecord = file;
                    break;
                }
            }

            if (existingFileRecord != null) {
                // Update existing file record
                existingFileRecord.setType(type);
                existingFileRecord.setUsage(usage);
            } else {
                // Add a new record
                existingSourceGroup.getFiles().add(fileRecord);
            }
        } else {
            // Create a new source group entry
            Source sourceGroupRecord = new Source();
            sourceGroupRecord.setName(sourceGroupName);
            sourceGroupRecord.setLanguage(language);
            sourceGroupRecord.setLanguageProcessor(languageProcessor);
            sourceGroupRecord.setFileExtension(fileExtension);
            sourceGroupRecord.setArtifactsType(artifactsType);
            sourceGroupRecord.setRepositoryPath(repositoryPath);
            sourceGroupRecord.setFiles(new ArrayList<>());
            sourceGroupRecord.getFiles().add(fileRecord);
            applicationDescriptor.getSources().add(sourceGroupRecord);
        }
    }

    /**
     * Remove a file definition from the application descriptor
     * @param applicationDescriptor The application descriptor to update
     * @param sourceGroupName The source group name
     * @param name The file name to remove
     */
    public void removeFileDefinition(ApplicationDescriptor applicationDescriptor, String sourceGroupName, String name) {
        if (applicationDescriptor.getSources() != null) {
            Source existingSourceGroup = null;
            for (Source source : applicationDescriptor.getSources()) {
                if (source.getName().equals(sourceGroupName)) {
                    existingSourceGroup = source;
                    break;
                }
            }

            if (existingSourceGroup != null) {
                FileDef existingFileDef = null;
                for (FileDef file : existingSourceGroup.getFiles()) {
                    if (file.getName().equals(name)) {
                        existingFileDef = file;
                        break;
                    }
                }

                if (existingFileDef != null) {
                    existingSourceGroup.getFiles().remove(existingFileDef);
                }
            }
        }
    }

    /**
     * Add a baseline to the application descriptor
     * @param applicationDescriptor The application descriptor to update
     * @param branch The branch name
     * @param type The baseline type
     * @param reference The baseline reference
     */
    public void addBaseline(ApplicationDescriptor applicationDescriptor, String branch, String type, String reference) {
        if (applicationDescriptor.getBaselines() != null) {
            // Remove existing baselines for the same branch
            applicationDescriptor.getBaselines().removeIf(baseline -> baseline.getBranch().equals(branch));
        } else {
            applicationDescriptor.setBaselines(new ArrayList<>());
        }

        Baseline newBaseline = new Baseline();
        newBaseline.setBranch(branch);
        newBaseline.setType(type);
        newBaseline.setReference(reference);
        newBaseline.setBuildid("baseline");  // hard-coded default identifier
        applicationDescriptor.getBaselines().add(newBaseline);
    }

    /**
     * Add an application dependency
     * @param applicationDescriptor The application descriptor to update
     * @param applicationDependency The dependency application name
     * @param reference The dependency reference
     * @param buildid The build ID
     */
    public void addApplicationDependency(ApplicationDescriptor applicationDescriptor, String applicationDependency,
                                        String reference, String buildid) {
        if (applicationDescriptor.getDependencies() == null) {
            applicationDescriptor.setDependencies(new ArrayList<>());
        }

        // Skip re-adding same/similar entries
        boolean exists = false;
        for (DependencyDescriptor dep : applicationDescriptor.getDependencies()) {
            if (dep.getName().equals(applicationDependency)) {
                exists = true;
                break;
            }
        }

        if (!exists) {
            DependencyDescriptor dependency = new DependencyDescriptor();
            dependency.setName(applicationDependency);
            dependency.setType("release"); // hard-coded
            dependency.setReference(reference);
            dependency.setBuildid(buildid);
            applicationDescriptor.getDependencies().add(dependency);
            applicationDescriptor.getDependencies().sort(Comparator.comparing(DependencyDescriptor::getName));
        }
    }

    /**
     * Add a consumer to the list of consumers
     * @param applicationDescriptor The application descriptor to update
     * @param consumingApplication The consuming application name
     */
    public void addApplicationConsumer(ApplicationDescriptor applicationDescriptor, String consumingApplication) {
        if (applicationDescriptor.getConsumers() == null) {
            applicationDescriptor.setConsumers(new ArrayList<>());
        }

        // Don't add the "owning" application
        if (!applicationDescriptor.getApplication().equals(consumingApplication)) {
            boolean exists = false;
            for (Consumer consumer : applicationDescriptor.getConsumers()) {
                if (consumer.getName().equals(consumingApplication)) {
                    exists = true;
                    break;
                }
            }

            if (!exists) {
                Consumer consumer = new Consumer();
                consumer.setName(consumingApplication);
                applicationDescriptor.getConsumers().add(consumer);
                applicationDescriptor.getConsumers().sort(Comparator.comparing(Consumer::getName));
            }
        }
    }

    /**
     * Reset all source groups
     * @param applicationDescriptor The application descriptor to update
     */
    public void resetAllSourceGroups(ApplicationDescriptor applicationDescriptor) {
        applicationDescriptor.setSources(new ArrayList<>());
    }

    /**
     * Reset consumers and dependencies
     * @param applicationDescriptor The application descriptor to update
     */
    public void resetConsumersAndDependencies(ApplicationDescriptor applicationDescriptor) {
        applicationDescriptor.setConsumers(new ArrayList<>());
        applicationDescriptor.setDependencies(new ArrayList<>());
    }

    /**
     * Create an empty application descriptor object
     * @return A new empty ApplicationDescriptor
     */
    public ApplicationDescriptor createEmptyApplicationDescriptor() {
        ApplicationDescriptor applicationDescriptor = new ApplicationDescriptor();
        applicationDescriptor.setSources(new ArrayList<>());
        applicationDescriptor.setBaselines(new ArrayList<>());
        return applicationDescriptor;
    }

    /**
     * Get file usage from application descriptor
     * @param applicationDescriptor The application descriptor
     * @param sourceGroup The source group name
     * @param name The file name
     * @return The file usage or null if not found
     */
    public String getFileUsage(ApplicationDescriptor applicationDescriptor, String sourceGroup, String name) {
        if (applicationDescriptor == null || applicationDescriptor.getSources() == null) {
            return null;
        }

        int matchingSourceGroupsCount = 0;
        Source matchingSourceGroup = null;

        for (Source source : applicationDescriptor.getSources()) {
            if (source.getName().equals(sourceGroup)) {
                matchingSourceGroupsCount++;
                matchingSourceGroup = source;
            }
        }

        if (matchingSourceGroupsCount == 1 && matchingSourceGroup != null) {
            int matchingFilesCount = 0;
            FileDef matchingFile = null;

            for (FileDef file : matchingSourceGroup.getFiles()) {
                if (file.getName().equalsIgnoreCase(name)) {
                    matchingFilesCount++;
                    matchingFile = file;
                }
            }

            if (matchingFilesCount == 1) {
                return matchingFile.getUsage();
            } else if (matchingFilesCount > 1) {
                System.out.println("*! [WARNING] Multiple files found matching '" + name + "'. Skipping search.");
            } else {
                System.out.println("*! [WARNING] No file found matching '" + name + "'. Skipping search.");
            }
        } else if (matchingSourceGroupsCount > 1) {
            System.out.println("*! [WARNING] Multiple Source Groups found matching '" + sourceGroup + "'. Skipping search.");
        }

        return null;
    }
}