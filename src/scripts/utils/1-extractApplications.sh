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

	if [ ! -d $DBB_MODELER_APPCONFIG_DIR ] 
	then
		mkdir -p $DBB_MODELER_APPCONFIG_DIR
	fi

	touch $DBB_MODELER_LOGS/1-extractApplications.log
	chtag -tc IBM-1047 $DBB_MODELER_LOGS/1-extractApplications.log

	CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/src/groovy/extractApplications.groovy \
		-d $APPLICATION_DATASETS \
		--applicationsMapping $APPLICATION_MAPPING_FILE \
		--repositoryPathsMapping $REPOSITORY_PATH_MAPPING_FILE \
		--types $APPLICATION_MEMBER_TYPE_MAPPING \
		-oc $DBB_MODELER_APPCONFIG_DIR \
		-oa $DBB_MODELER_APPLICATION_DIR \
		-l $DBB_MODELER_LOGS/1-extractApplications.log"

	if [ "${SCAN_DATASET_MEMBERS}" == "true" ]; then
		CMD="${CMD} --scanDatasetMembers"

		if [ ! -z "${SCAN_DATASET_MEMBERS_ENCODING}" ]; then
			CMD="${CMD} --scanEncoding ${SCAN_DATASET_MEMBERS_ENCODING}"
		fi
	fi
	
	echo "[INFO] ${CMD}" >> $DBB_MODELER_LOGS/1-extractApplications.log
	$CMD
fi