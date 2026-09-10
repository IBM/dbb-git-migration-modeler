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
import java.util.Map;

/**
 * Types Mapping model class.
 * Represents the structure of the typesMapping YAML file and typesConfigurations YAML file.
 */
public class TypesMapping {
    private List<Map<String, String>> datasetMembers;
    private List<TypeConfiguration> typesConfigurations;

    public List<Map<String, String>> getDatasetMembers() {
        return datasetMembers;
    }

    public void setDatasetMembers(List<Map<String, String>> datasetMembers) {
        this.datasetMembers = datasetMembers;
    }

    public List<TypeConfiguration> getTypesConfigurations() {
        return typesConfigurations;
    }

    public void setTypesConfigurations(List<TypeConfiguration> typesConfigurations) {
        this.typesConfigurations = typesConfigurations;
    }

    /**
     * Type Configuration inner class.
     * Represents a single type configuration with its variables.
     */
    public static class TypeConfiguration {
        private String typeConfiguration;
        private List<Variable> variables;

        public String getTypeConfiguration() {
            return typeConfiguration;
        }

        public void setTypeConfiguration(String typeConfiguration) {
            this.typeConfiguration = typeConfiguration;
        }

        public List<Variable> getVariables() {
            return variables;
        }

        public void setVariables(List<Variable> variables) {
            this.variables = variables;
        }
    }

    /**
     * Variable inner class.
     * Represents a configuration variable with name and value.
     */
    public static class Variable {
        private String name;
        private String value;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}