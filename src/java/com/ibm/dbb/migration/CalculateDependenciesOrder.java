/*
 * Licensed Materials - Property of IBM
 * (c) Copyright IBM Corporation 2018, 2025. All Rights Reserved.
 *
 * Note to U.S. Government Users Restricted Rights:
 * Use, duplication or disclosure restricted by GSA ADP Schedule
 * Contract with IBM Corp.
 */

package com.ibm.dbb.migration;

import com.ibm.dbb.migration.model.ApplicationDescriptor;
import com.ibm.dbb.migration.utils.ApplicationDescriptorUtils;
import org.apache.commons.cli.*;

import java.io.File;
import java.util.*;

/**
 * Calculates and displays the dependency order of applications using topological sorting.
 * Equivalent to calculateDependenciesOrder.groovy.
 */
public class CalculateDependenciesOrder {
    
    private String applicationsFolderPath;
    private Map<String, List<String>> dependencies = new HashMap<>();
    
    public static void main(String[] args) {
        CalculateDependenciesOrder calculator = new CalculateDependenciesOrder();
        try {
            calculator.run(args);
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to calculate dependencies order: " + e.getMessage());
            e.printStackTrace();
            System.exit(8);
        }
    }
    
    public void run(String[] args) throws Exception {
        // Parse command line arguments
        parseArgs(args);
        
        // Load dependencies from application descriptors
        loadDependencies();
        
        // Perform topological sort
        System.out.println("** Dependencies for each application:");
        topologicalSort();
    }
    
    private void parseArgs(String[] args) {
        Options options = new Options();
        
        options.addOption(Option.builder("a")
            .longOpt("applicationsFolder")
            .desc("Absolute path to the applications' folder")
            .hasArg()
            .required()
            .build());
        
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        
        try {
            CommandLine cmd = parser.parse(options, args);
            applicationsFolderPath = cmd.getOptionValue("a");
        } catch (ParseException e) {
            System.err.println("[ERROR] " + e.getMessage());
            formatter.printHelp("CalculateDependenciesOrder", options);
            System.exit(1);
        }
    }
    
    private void loadDependencies() throws Exception {
        File applicationsFolder = new File(applicationsFolderPath);
        
        if (!applicationsFolder.exists()) {
            System.err.println("*! [ERROR] The Applications' folder '" + applicationsFolderPath + "' was not found. Exiting.");
            System.exit(1);
        }
        
        File[] applicationDirs = applicationsFolder.listFiles(File::isDirectory);
        if (applicationDirs == null) {
            return;
        }
        
        ApplicationDescriptorUtils appDescUtils = new ApplicationDescriptorUtils();
        
        for (File applicationDir : applicationDirs) {
            // Skip dbb-zappbuild directory
            if ("dbb-zappbuild".equals(applicationDir.getName())) {
                continue;
            }
            
            File applicationDescriptorFile = new File(applicationDir, "applicationDescriptor.yml");
            
            if (!applicationDescriptorFile.exists()) {
                System.out.println("*! [ERROR] The Application Descriptor file '" + 
                    applicationDescriptorFile.getAbsolutePath() + "' was not found. Skipping.");
                continue;
            }
            
            try {
                ApplicationDescriptor appDescriptor = appDescUtils.readApplicationDescriptor(applicationDescriptorFile);
                
                if (appDescriptor != null) {
                    List<String> appDependencies = new ArrayList<>();
                    
                    if (appDescriptor.getDependencies() != null) {
                        for (ApplicationDescriptor.DependencyDescriptor dependency : appDescriptor.getDependencies()) {
                            appDependencies.add(dependency.getName());
                        }
                    }
                    
                    dependencies.put(appDescriptor.getApplication(), appDependencies);
                }
            } catch (Exception e) {
                System.err.println("*! [WARNING] Failed to read application descriptor for " + 
                    applicationDir.getName() + ": " + e.getMessage());
            }
        }
    }
    
    private void topologicalSort() {
        DependencyGraph graph = new DependencyGraph(dependencies);
        graph.performTopologicalSort();
    }
    
    /**
     * Inner class to represent the dependency graph and perform topological sorting
     */
    private static class DependencyGraph {
        private Map<String, List<String>> dependencies;
        
        public DependencyGraph(Map<String, List<String>> dependencies) {
            // Create a deep copy to avoid modifying the original
            this.dependencies = new HashMap<>();
            for (Map.Entry<String, List<String>> entry : dependencies.entrySet()) {
                this.dependencies.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
        
        public void performTopologicalSort() {
            Map<Integer, List<String>> rankedApplications = new LinkedHashMap<>();
            int rank = 1;
            
            while (true) {
                // Find applications with no dependencies
                List<String> applicationsWithNoDependency = new ArrayList<>();
                
                for (Map.Entry<String, List<String>> entry : dependencies.entrySet()) {
                    if (entry.getValue().isEmpty()) {
                        applicationsWithNoDependency.add(entry.getKey());
                    }
                }
                
                // If no applications found, break the loop
                if (applicationsWithNoDependency.isEmpty()) {
                    break;
                }
                
                // Add applications to current rank
                rankedApplications.put(rank, new ArrayList<>(applicationsWithNoDependency));
                
                // Remove these applications from the graph
                for (String application : applicationsWithNoDependency) {
                    deleteApplication(application);
                }
                
                rank++;
            }
            
            // Print results
            System.out.println("*** Level-ranked applications:");
            for (Map.Entry<Integer, List<String>> entry : rankedApplications.entrySet()) {
                System.out.println("\t" + entry.getKey() + " " + entry.getValue());
            }
            
            if (!dependencies.isEmpty()) {
                System.out.println("\n*** Remaining applications:\n\t[");
                for (String application : dependencies.keySet()) {
                    System.out.print(application + " ");
                }
                System.out.println("\n]");
            } else {
                System.out.println("*** All applications were ranked successfully!");
            }
        }
        
        private void deleteApplication(String application) {
            // Remove the application itself
            dependencies.remove(application);
            
            // Remove the application from all dependency lists
            for (List<String> dependencyList : dependencies.values()) {
                dependencyList.remove(application);
            }
        }
    }
}