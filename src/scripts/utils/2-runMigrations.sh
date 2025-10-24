#!/bin/env bash
#*******************************************************************************
# Licensed Materials - Property of IBM
# (c) Copyright IBM Corporation 2018, 2024. All Rights Reserved.
#
# Note to U.S. Government Users Restricted Rights:
# Use, duplication or disclosure restricted by GSA ADP Schedule
# Contract with IBM Corp. 
#*******************************************************************************
 
# Internal variables
DBB_GIT_MIGRATION_MODELER_CONFIG_FILE=
rc=0

# Get Options
if [ $rc -eq 0 ]; then
	while getopts "c:" opt; do
		case $opt in
		c)
			argument="$OPTARG"
			nextchar="$(expr substr $argument 1 1)"
			if [ -z "$argument" ] || [ "$nextchar" = "-" ]; then
				rc=4
				ERRMSG="[ERROR] DBB Git Migration Modeler Configuration file required. rc="$rc
				echo $ERRMSG
				break
			fi
			DBB_GIT_MIGRATION_MODELER_CONFIG_FILE="$argument"
			;;
		esac
	done
fi
#

# Validate Options
validateOptions() {
	if [ -z "${DBB_GIT_MIGRATION_MODELER_CONFIG_FILE}" ]; then
		rc=8
		ERRMSG="[ERROR] Argument to specify DBB Git Migration Modeler configuration file (-c) is required. rc="$rc
		echo $ERRMSG
	fi
	
	if [ ! -f "${DBB_GIT_MIGRATION_MODELER_CONFIG_FILE}" ]; then
		rc=8
		ERRMSG="[ERROR] DBB Git Migration Modeler configuration file not found. rc="$rc
		echo $ERRMSG
	fi
}

# Call validate Options
if [ $rc -eq 0 ]; then
 	validateOptions
fi

if [ $rc -eq 0 ]; then
	# Environment variables setup
	dir=$(dirname "$0")
	. $dir/0-validateConfiguration.sh -c ${DBB_GIT_MIGRATION_MODELER_CONFIG_FILE}

	if [ -d $DBB_MODELER_APPLICATION_DIR ]; then
		rm -rf $DBB_MODELER_APPLICATION_DIR
    fi

	cd $DBB_MODELER_APPCONFIG_DIR
	for applicationConfig in `ls *.yml`
    do
        application=`echo $applicationConfig | awk -F. '{ print $1 }'`
        
        echo "*******************************************************************"
        echo "Running the DBB Migration Utility for '$application'"
        echo "*******************************************************************"
        
        cd $DBB_MODELER_APPCONFIG_DIR
        for mappingFile in `ls $application*.mapping`
        do 
            echo "** Processing '$mappingFile'"
            
            mkdir -p $DBB_MODELER_APPLICATION_DIR/$application
            cd $DBB_MODELER_APPLICATION_DIR/$application
            
            CMD="$DBB_HOME/bin/groovyz $DBB_HOME/migration/bin/migrate.groovy -l $DBB_MODELER_LOGS/2-$application.migration.log -le UTF-8 -np info -r $DBB_MODELER_APPLICATION_DIR/$application $DBB_MODELER_APPCONFIG_DIR/$mappingFile"
            echo "[INFO] ${CMD}" >> $DBB_MODELER_LOGS/2-$mappingFile.migration.log
            $CMD
        done
        
        echo "${DBB_MODELER_APPCONFIG_DIR}/${application}.yml"
        if [ -f "${DBB_MODELER_APPCONFIG_DIR}/${application}.yml" ]; then
            echo "** Copy base Application Descriptor for application '$application' to $DBB_MODELER_APPLICATION_DIR/applicationDescriptor.yml"
            CMD="cp $DBB_MODELER_APPCONFIG_DIR/$application.yml $DBB_MODELER_APPLICATION_DIR/$application/applicationDescriptor.yml"
            echo "[INFO] ${CMD}" >> $DBB_MODELER_LOGS/2-$mappingFile.migration.log
            $CMD
        fi  
            
    done
fi