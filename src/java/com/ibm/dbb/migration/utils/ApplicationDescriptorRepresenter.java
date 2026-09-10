/********************************************************************************
 * Licensed Materials - Property of IBM                                          *
 * (c) Copyright IBM Corporation 2018, 2024. All Rights Reserved.                *
 *                                                                               *
 * Note to U.S. Government Users Restricted Rights:                              *
 * Use, duplication or disclosure restricted by GSA ADP Schedule                 *
 * Contract with IBM Corp.                                                       *
 ********************************************************************************/

package com.ibm.dbb.migration.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.ibm.dbb.migration.model.ApplicationDescriptor;
import com.ibm.dbb.migration.model.ApplicationDescriptor.Baseline;
import com.ibm.dbb.migration.model.ApplicationDescriptor.Consumer;
import com.ibm.dbb.migration.model.ApplicationDescriptor.DependencyDescriptor;
import com.ibm.dbb.migration.model.ApplicationDescriptor.FileDef;
import com.ibm.dbb.migration.model.ApplicationDescriptor.Source;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

public class ApplicationDescriptorRepresenter extends Representer {

    /**
     * Create object without specified dumper object
     */
    public ApplicationDescriptorRepresenter() {
        this(new DumperOptions());
    }

    /**
     * Create object with dumper options
     *
     * @param options
     */
    public ApplicationDescriptorRepresenter(DumperOptions options) {
        super(options);
        setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        addClassTag(ApplicationDescriptor.class, Tag.MAP);
        addClassTag(Source.class, Tag.MAP);
        addClassTag(FileDef.class, Tag.MAP);
        addClassTag(Baseline.class, Tag.MAP);
        addClassTag(DependencyDescriptor.class, Tag.MAP);
        addClassTag(Consumer.class, Tag.MAP);
    }

    @Override
    protected Set<Property> getProperties(Class< ? extends Object> type) {
/*        Set<Property> propertySet;
        if (typeDefinitions.containsKey(type)) {
            propertySet = typeDefinitions.get(type).getProperties();
        } else {
            propertySet = getPropertyUtils().getProperties(type);
        }

        List<Property> propsList = new ArrayList<>(propertySet);
        Collections.sort(propsList, new BeanPropertyComparator());

        return new LinkedHashSet<>(propsList); */
        final List<String> order = List.of("application", "schemaVersion", "description", "owner", "sources", "name", "repositoryPath", "languageProcessor", "language", "fileExtension", "artifactsType", "files", "name", "usage", "type", "baselines", "type", "reference", "buildid", "branch", "dependencies", "name", "type", "reference", "buildid", "consumers", "name");
        final Set<Property> result = new TreeSet<>(Comparator.comparingInt(a -> order.indexOf(a.getName())));
        result.addAll(super.getProperties(type)
                           .stream()
                           .filter(property -> order.contains(property.getName()))
                           .collect(Collectors.toSet()));
        return result;
    }

    class BeanPropertyComparator implements Comparator<Property> {
        @Override
        public int compare(Property p1, Property p2) {
            // p1.getType().get
            if (p1.getType().getCanonicalName().contains("util") && !p2.getType().getCanonicalName().contains("util")) {
                return 1;
            } else if (p2.getName().endsWith("Name") || p2.getName().equalsIgnoreCase("name")) {
                return 1;
            } else {
                return -1;
            }
        }
    }
}

