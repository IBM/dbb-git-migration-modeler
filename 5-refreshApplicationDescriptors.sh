#!/bin/sh
#*******************************************************************************
# Licensed Materials - Property of IBM
# (c) Copyright IBM Corporation 2018, 2024. All Rights Reserved.
#
# Note to U.S. Government Users Restricted Rights:
# Use, duplication or disclosure restricted by GSA ADP Schedule
# Contract with IBM Corp. 
#*******************************************************************************

if [  "$DBB_HOME" = "" ]
then
    echo "Environment variable DBB_HOME is not set. Exiting..."
else
    # Environment variables setup
    . ./0-environment.sh
    
    #### Build Metadatastore
    if [ -d $DBB_MODELER_METADATA_STORE ] 
    then
        rm -rf $DBB_MODELER_METADATA_STORE
    fi
    
    if [ ! -d $DBB_MODELER_METADATA_STORE ] 
    then
        mkdir -p $DBB_MODELER_METADATA_STORE
    fi

    # Scan files
    cd $DBB_MODELER_APPLICATIONS
    for applicationDir in `ls`
    do
        echo "*******************************************************************"
        echo "Scan application directory $DBB_MODELER_APPLICATIONS/$applicationDir"
        echo "*******************************************************************"
        CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/scanApplication.groovy \
            -w $DBB_MODELER_APPLICATIONS \
            -a $applicationDir \
            -m $DBB_MODELER_METADATA_STORE \
            -l $DBB_MODELER_LOGS/3-$application-scan.log"   
        $CMD "$@"
    done
    
    # Reset Application Descriptor
    cd $DBB_MODELER_APPLICATIONS
    for applicationDir in `ls`
    do
        echo "*******************************************************************"
        echo "Reset Application Descriptor for $applicationDir"
        echo "*******************************************************************"
        CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/recreateApplicationDescriptor.groovy \
            --workspace $DBB_MODELER_APPLICATIONS \
            --application $applicationDir \
            --repositoryPathsMapping $DBB_MODELER_HOME/repositoryPathsMapping.yaml \
            --logFile $DBB_MODELER_LOGS/3-$applicationDir-createApplicationDescriptor.log"
        echo $CMD
        $CMD "$@"
    done

    cd $DBB_MODELER_APPLICATIONS
    for applicationDir in `ls`
    do
        echo "*******************************************************************"
        echo "Assess Include files & Programs usage for $applicationDir"
        echo "*******************************************************************"
        CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/assessUsage.groovy \
            --workspace $DBB_MODELER_APPLICATIONS \
            --metadatastore $DBB_MODELER_METADATA_STORE \
            --application $applicationDir \
            --logFile $DBB_MODELER_LOGS/3-$applicationDir-assessUsage.log"
        $CMD "$@"
    done
fi