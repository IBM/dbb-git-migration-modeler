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
	. $dir/0-environment.sh  -c ${DBB_GIT_MIGRATION_MODELER_CONFIG_FILE}

	if [ ! -d $DBB_MODELER_APPCONFIG_DIR ]; then
		mkdir -p $DBB_MODELER_APPCONFIG_DIR
	fi

	if [ ! -d $DBB_MODELER_APPLICATIONS_DIR ]; then
		mkdir -p $DBB_MODELER_APPLICATIONS_DIR
	fi

	for applicationsMappingFile in `ls $DBB_MODELER_APPMAPPINGS_DIR`
	do		
		echo "*******************************************************************"
		echo "Extract applications using '$DBB_MODELER_APPMAPPINGS_DIR/$applicationsMappingFile'"
		echo "*******************************************************************"
		applicationMapping=`echo $applicationsMappingFile | awk -F '.' '{printf $1}'`
		
		touch $DBB_MODELER_LOGS/1-$applicationMapping-extractApplications.log
		chtag -tc IBM-1047 $DBB_MODELER_LOGS/1-$applicationMapping-extractApplications.log
	
		CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/src/groovy/extractApplications.groovy \
			--configFile $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE \
			--applicationsMapping $DBB_MODELER_APPMAPPINGS_DIR/$applicationsMappingFile \
			--logFile $DBB_MODELER_LOGS/1-$applicationMapping-extractApplications.log"
	
		if [ "${SCAN_DATASET_MEMBERS}" == "true" ]; then
			CMD="${CMD} --scanDatasetMembers"
			if [ ! -z "${SCAN_DATASET_MEMBERS_ENCODING}" ]; then
				CMD="${CMD} --scanEncoding ${SCAN_DATASET_MEMBERS_ENCODING}"
			fi
		fi

		echo "[INFO] ${CMD}" >> $DBB_MODELER_LOGS/1-$applicationMapping-extractApplications.log
		$CMD
	done
fi