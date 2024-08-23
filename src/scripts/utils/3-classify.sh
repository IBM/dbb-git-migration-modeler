#!/bin/env bash
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
	echo "[ERROR] Environment variable DBB_HOME is not set. Exiting."
else
	# Environment variables setup
	dir=$(dirname "$0")
	. $dir/0-environment.sh "$@"

	# Build Metadatastore
    if [ -d $DBB_MODELER_METADATA_STORE_DIR ] 
    then
        rm -rf $DBB_MODELER_METADATA_STORE_DIR
    fi

    if [ ! -d $DBB_MODELER_METADATA_STORE_DIR ] 
    then
        mkdir -p $DBB_MODELER_METADATA_STORE_DIR
    fi

    # Scan files
    cd $DBB_MODELER_APPLICATION_DIR
    for applicationDir in `ls | grep -v dbb-zappbuild`
    do
        echo "*******************************************************************"
        echo "Scan application directory $DBB_MODELER_APPLICATION_DIR/$applicationDir"
        echo "*******************************************************************"
        CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/src/groovy/scanApplication.groovy \
            -w $DBB_MODELER_APPLICATION_DIR \
            -a $applicationDir \
            -m $DBB_MODELER_METADATA_STORE_DIR \
            -l $DBB_MODELER_LOGS/3-$applicationDir-scan.log"    
        echo " [INFO] ${CMD}"
        $CMD
    done

    cd $DBB_MODELER_APPLICATION_DIR
    for applicationDir in `ls | grep -v dbb-zappbuild`
    do
        echo "*******************************************************************"
        echo "Assess Include files & Programs usage for $applicationDir"
        echo "*******************************************************************"
        CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/src/groovy/assessUsage.groovy \
            --workspace $DBB_MODELER_APPLICATION_DIR \
            --configurations $DBB_MODELER_APPCONFIG_DIR \
            --metadatastore $DBB_MODELER_METADATA_STORE_DIR \
            --application $applicationDir \
            --moveFiles \
            --logFile $DBB_MODELER_LOGS/3-$applicationDir-assessUsage.log"
        echo " [INFO] ${CMD}"
        $CMD
    done
fi
