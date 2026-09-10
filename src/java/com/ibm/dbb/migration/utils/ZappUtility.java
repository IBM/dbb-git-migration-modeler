/********************************************************************************
 * Licensed Materials - Property of IBM                                          *
 * (c) Copyright IBM Corporation 2018, 2025. All Rights Reserved.                *
 *                                                                               *
 * Note to U.S. Government Users Restricted Rights:                              *
 * Use, duplication or disclosure restricted by GSA ADP Schedule                 *
 * Contract with IBM Corp.                                                       *
 ********************************************************************************/

package com.ibm.dbb.migration.utils;

import com.ibm.dbb.migration.model.ApplicationDescriptor;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.representer.Representer;

import java.io.*;
import java.util.*;

import com.ibm.dbb.utils.FileUtils;

/**
 * Utility class for ZAPP file operations.
 * Handles reading, updating, and writing ZAPP YAML files for zBuilder framework.
 */
public class ZappUtility {
    
    /**
     * Customize ZAPP file for an application using zBuilder framework
     * @param zappFile The zapp.yaml file to customize
     * @param applicationDescriptor The application descriptor
     * @throws IOException if file operations fail
     */
    public static void customizeZappFile(File zappFile, ApplicationDescriptor applicationDescriptor) throws IOException {
        if (!zappFile.exists()) {
            throw new FileNotFoundException("ZAPP file not found: " + zappFile.getAbsolutePath());
        }
        
        // Read existing ZAPP file
        Map<String, Object> zapp;
        try (FileInputStream fis = new FileInputStream(zappFile);
             InputStreamReader reader = new InputStreamReader(fis, "UTF-8")) {
            Yaml yaml = new Yaml();
            zapp = yaml.load(reader);
        }
        
        // Update name and description
        zapp.put("name", applicationDescriptor.getApplication());
        zapp.put("description", "ZAPP file for the " + applicationDescriptor.getApplication() + " application");
        
        // Add property groups for include files
        List<Map<String, Object>> propertyGroups = new ArrayList<>();
        List<ApplicationDescriptor.Source> sources = applicationDescriptor.getSources();
        
        if (sources != null) {
            for (ApplicationDescriptor.Source source : sources) {
                if ("Include File".equals(source.getArtifactsType())) {
                    Map<String, Object> propertyGroup = new LinkedHashMap<>();
                    propertyGroup.put("name", source.getName());
                    propertyGroup.put("language", source.getLanguage().toLowerCase());
                    
                    // Add syslib library
                    List<Map<String, Object>> libraries = new ArrayList<>();
                    Map<String, Object> library = new LinkedHashMap<>();
                    library.put("name", "syslib");
                    library.put("type", "local");
                    
                    List<String> locations = new ArrayList<>();
                    locations.add(source.getRepositoryPath());
                    library.put("locations", locations);
                    
                    libraries.add(library);
                    propertyGroup.put("libraries", libraries);
                    
                    propertyGroups.add(propertyGroup);
                }
            }
        }
        
        zapp.put("propertyGroups", propertyGroups);
        
        // Write updated ZAPP file
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);

        Representer representer = new Representer(options);
        representer.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        
        Yaml yaml = new Yaml(representer, options);
        try (FileOutputStream fos = new FileOutputStream(zappFile);
             OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8")) {
            yaml.dump(zapp, writer);
        }
        
        // Set file tag to UTF-8 (z/OS specific)
        FileUtils.setFileTag(zappFile.getAbsolutePath(), "UTF-8");
    }
}