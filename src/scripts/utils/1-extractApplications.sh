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

	for applicationsMappingFile in `ls $DBB_MODELER_APPMAPPINGS_DIR`
	do		
		echo "*******************************************************************"
		echo "Extract applications using '$DBB_MODELER_APPMAPPINGS_DIR/$applicationsMappingFile'"
		echo "*******************************************************************"
		applicationMapping=`echo $applicationsMappingFile | awk -F '.' '{printf $1}'`
		
		touch $DBB_MODELER_LOGS/1-$applicationMapping-extractApplications.log
		chtag -tc IBM-1047 $DBB_MODELER_LOGS/1-$applicationMapping-extractApplications.log
	
		CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/src/groovy/extractApplications.groovy \
			--applicationsMapping $DBB_MODELER_APPMAPPINGS_DIR/$applicationsMappingFile \
			--repositoryPathsMapping $REPOSITORY_PATH_MAPPING_FILE \
			--types $APPLICATION_MEMBER_TYPE_MAPPING \
			-oc $DBB_MODELER_APPCONFIG_DIR \
			-oa $DBB_MODELER_APPLICATION_DIR \
			-l $DBB_MODELER_LOGS/1-$applicationMapping-extractApplications.log"
	
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