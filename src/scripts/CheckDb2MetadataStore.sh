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
	. $dir/utils/0-environment.sh -c ${DBB_GIT_MIGRATION_MODELER_CONFIG_FILE}
	
	# Build Metadatastore
	# Exit if File MetadataStore is configured
	if [ "$DBB_MODELER_METADATASTORE_TYPE" = "file" ]; then
		rc=1
		echo "[ERROR] The File MetadataStore is configured in the Configuration file. Exiting."
	elif [ "$DBB_MODELER_METADATASTORE_TYPE" = "db2" ]; then
		CURRENT_DBB_TOOLKIT_VERSION=`$DBB_MODELER_HOME/src/scripts/utils/0-environment.sh -c $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE -v 3.0.1`
		rc=$?
		if [ $rc -eq 0 ]; then
			if [ -z "${DBB_MODELER_DB2_METADATASTORE_JDBC_ID}" ]; then
				rc=1
				echo "[ERROR] The Db2 MetadataStore User is missing from the Configuration file. Exiting."
			fi
			if [ -z "${DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE}" ]; then
				rc=1
				echo "[ERROR] The Db2 Connection configuration file is missing from the Configuration file. Exiting."
			else 
				if [ ! -f "${DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE}" ]; then
					rc=1
					echo "[ERROR] The Db2 Connection configuration file '${DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE}' does not exist. Exiting."
				fi
			fi
			if [ -z "${DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORD}" ] && [ -z "${DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE}" ]; then
				rc=1
				echo "[ERROR] Either the Db2 MetadataStore User's Password or the Db2 MetadataStore Password File are missing from the Configuration file. Exiting."
			fi
			CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/src/groovy/checkDb2MetadataStore.groovy -c $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE"
			$CMD
		else
			rc=8
			echo "[ERROR] The DBB Toolkit's version is $CURRENT_DBB_TOOLKIT_VERSION. To use the Db2-based MetadataStore, the minimal recommended version for the DBB Toolkit is 3.0.1."
		fi	
	fi
fi