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
	. $dir/utils/0-environment.sh "$@"

	# Build Metadatastore
	# Exit if File MetadataStore is configured
	if [ "$DBB_MODELER_METADATASTORE_TYPE" = "file" ]; then
		echo "[ERROR] The File MetadataStore is configured in the Configuration file. Exiting."
		exit 1
	elif [ "$DBB_MODELER_METADATASTORE_TYPE" = "db2" ]; then
		if [ -z "${DBB_MODELER_DB2_METADATASTORE_JDBC_ID}" ]; then
			echo "[ERROR] The Db2 MetadataStore User is missing from the Configuration file. Exiting."
			exit 1
		fi
		if [ -z "${DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE}" ]; then
			echo "[ERROR] The Db2 Connection configuration file is missing from the Configuration file. Exiting."
			exit 1
		else 
			if [ ! -f "${DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE}" ]; then
				echo "[ERROR] The Db2 Connection configuration file '${DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE}' does not exist. Exiting."
				exit 1
			fi
		fi
		if [ -z "${DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORD}" ] && [ -z "${DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE}" ]; then
			echo "[ERROR] Either the Db2 MetadataStore User's Password or the Db2 MetadataStore Password File are missing from the Configuration file. Exiting."
			exit 1
		fi	
	fi

	CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/src/groovy/checkDb2MetadataStore.groovy $@"
	$CMD
fi
