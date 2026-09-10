/********************************************************************************
 * Licensed Materials - Property of IBM                                          *
 * (c) Copyright IBM Corporation 2018, 2025. All Rights Reserved.                *
 *                                                                               *
 * Note to U.S. Government Users Restricted Rights:                              *
 * Use, duplication or disclosure restricted by GSA ADP Schedule                 *
 * Contract with IBM Corp.                                                       *
 ********************************************************************************/

package com.ibm.dbb.migration.utils;

import java.io.File;
import java.util.Properties;

/**
 * Utility class for loading and validating configuration properties.
 */
public class ConfigurationUtility {
    
    /**
     * Load a required property that must point to an existing file or directory.
     * Validates property exists, checks path exists, and loads to target.
     *
     * @param configuration Source properties object
     * @param target Target properties object to store the validated property
     * @param propertyName Name of the property to load
     * @param description Human-readable description for error messages
     * @throws IllegalArgumentException if property is missing, empty, or path doesn't exist
     */
    public static void loadRequiredProperty(Properties configuration, Properties target,
                                           String propertyName, String description) {
        validateRequiredProperty(configuration, propertyName, description);
        
        String value = configuration.getProperty(propertyName);
        File path = new File(value);
        if (!path.exists()) {
            throw new IllegalArgumentException(description + " does not exist: " + value);
        }
        
        target.setProperty(propertyName, value);
    }
    
    /**
     * Load an optional property with a default value.
     * 
     * @param configuration Source properties object
     * @param target Target properties object to store the property
     * @param propertyName Name of the property to load
     * @param defaultValue Default value if property is not set
     */
    public static void loadOptionalProperty(Properties configuration, Properties target,
                                           String propertyName, String defaultValue) {
        String value = configuration.getProperty(propertyName);
        if (value != null && !value.trim().isEmpty()) {
            target.setProperty(propertyName, value);
        } else if (defaultValue != null) {
            target.setProperty(propertyName, defaultValue);
        }
    }
    
    /**
     * Load an optional property without a default value.
     * Only sets the property if it exists in the source configuration.
     * 
     * @param configuration Source properties object
     * @param target Target properties object to store the property
     * @param propertyName Name of the property to load
     */
    public static void loadOptionalProperty(Properties configuration, Properties target,
                                           String propertyName) {
        loadOptionalProperty(configuration, target, propertyName, null);
    }
    
    /**
     * Validate that a required property exists and is not empty.
     * 
     * @param properties Properties object to check
     * @param propertyName Name of the property to validate
     * @param description Human-readable description for error messages
     * @throws IllegalArgumentException if property is missing or empty
     */
    public static void validateRequiredProperty(Properties properties, String propertyName, 
                                               String description) {
        String value = properties.getProperty(propertyName);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(description + " (" + propertyName + 
                ") must be specified in configuration file");
        }
    }
    
    /**
     * Validate and load a required property (no path validation).
     * Use this for properties that don't represent file system paths.
     *
     * @param configuration Source properties object
     * @param target Target properties object to store the validated property
     * @param propertyName Name of the property to validate
     * @param description Human-readable description for error messages
     * @throws IllegalArgumentException if property is missing or empty
     */
    public static void validateAndLoadRequiredPropertyValue(Properties configuration, Properties target,
                                                           String propertyName, String description) {
        validateRequiredProperty(configuration, propertyName, description);
        target.setProperty(propertyName, configuration.getProperty(propertyName));
    }
    
    /**
     * Validate that a property points to an existing file.
     *
     * @param properties Properties object to check
     * @param propertyName Name of the property to validate
     * @param description Human-readable description for error messages
     * @throws IllegalArgumentException if property is missing, empty, or file doesn't exist
     */
    public static void validateFileProperty(Properties properties, String propertyName,
                                           String description) {
        validateRequiredProperty(properties, propertyName, description);
        
        String value = properties.getProperty(propertyName);
        File file = new File(value);
        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException(description + " does not exist or is not a file: " + value);
        }
    }
    
    /**
     * Validate that a property points to an existing directory.
     *
     * @param properties Properties object to check
     * @param propertyName Name of the property to validate
     * @param description Human-readable description for error messages
     * @throws IllegalArgumentException if property is missing, empty, or directory doesn't exist
     */
    public static void validateDirectoryProperty(Properties properties, String propertyName,
                                                String description) {
        validateRequiredProperty(properties, propertyName, description);
        
        String value = properties.getProperty(propertyName);
        File dir = new File(value);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IllegalArgumentException(description + " does not exist or is not a directory: " + value);
        }
    }
}
