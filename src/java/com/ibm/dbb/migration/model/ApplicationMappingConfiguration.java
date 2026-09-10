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
import java.util.Objects;

/**
 * Configuration class representing an application mapping.
 * Corresponds to the ApplicationMappingConfiguration class in the Groovy script.
 */
public class ApplicationMappingConfiguration {
    private String application;
    private String component;
    private String description;
    private String owner;
    private String baseline;
    private List<String> namingConventions = new ArrayList<>();
    private List<String> datasetMembers = new ArrayList<>();

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
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

    public String getBaseline() {
        return baseline;
    }

    public void setBaseline(String baseline) {
        this.baseline = baseline;
    }

    public List<String> getNamingConventions() {
        return namingConventions;
    }

    public void setNamingConventions(List<String> namingConventions) {
        this.namingConventions = namingConventions;
    }

    public List<String> getDatasetMembers() {
        return datasetMembers;
    }

    public void setDatasetMembers(List<String> datasetMembers) {
        this.datasetMembers = datasetMembers;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApplicationMappingConfiguration that = (ApplicationMappingConfiguration) o;
        return Objects.equals(application, that.application) &&
               Objects.equals(component, that.component);
    }

    @Override
    public int hashCode() {
        return Objects.hash(application, component);
    }

    @Override
    public String toString() {
        return "ApplicationMappingConfiguration{" +
                "application='" + application + '\'' +
                ", component='" + component + '\'' +
                ", description='" + description + '\'' +
                ", owner='" + owner + '\'' +
                ", baseline='" + baseline + '\'' +
                '}';
    }
}