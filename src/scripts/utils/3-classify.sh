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
	# Drop and recreate the Build Metadatastore folder
	if [ "$DBB_MODELER_METADATASTORE_TYPE" = "file" ]; then
		if [ -z "${DBB_MODELER_FILE_METADATA_STORE_DIR}" ]; then
			echo "[ERROR] File Metadatastore location is missing from the Configuration file. Exiting."
			exit 1
		else
			if [ -d $DBB_MODELER_FILE_METADATA_STORE_DIR ] 
			then
				rm -rf $DBB_MODELER_FILE_METADATA_STORE_DIR
			fi
			if [ ! -d $DBB_MODELER_FILE_METADATA_STORE_DIR ] 
			then
				mkdir -p $DBB_MODELER_FILE_METADATA_STORE_DIR
			fi
		fi
	elif [ "$DBB_MODELER_METADATASTORE_TYPE" = "db2" ]; then
		if [ -z "${DBB_MODELER_DB2_METADATASTORE_ID}" ]; then
			echo "[ERROR] The Db2 Metadatastore User is missing from the Configuration file. Exiting."
			exit 1
		fi
		if [ -z "${DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE}" ]; then
			echo "[ERROR] The Db2 Connection Configuration file is missing from the Configuration file. Exiting."
			exit 1
		else 
			if [ ! -f "${DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE}" ]; then
				echo "[ERROR] The Db2 Connection Configuration file '${DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE}' does not exist. Exiting."
				exit 1
			fi
		fi
		if [ -z "${DBB_MODELER_DB2_METADATASTORE_PASSWORD}" ] && [ -z "${DBB_MODELER_DB2_METADATASTORE_PASSWORDFILE}" ]; then
			echo "[ERROR] Either the Db2 Metadatastore User's Password or the Db2 Metadatastore Password File are missing from the Configuration file. Exiting."
			exit 1
		fi	
	fi

	# Scan files
	cd $DBB_MODELER_APPLICATION_DIR
	for applicationDir in `ls | grep -v dbb-zappbuild`
	do
		echo "*******************************************************************"
		echo "Scan application directory '$DBB_MODELER_APPLICATION_DIR/$applicationDir'"
		echo "*******************************************************************"
		touch $DBB_MODELER_LOGS/3-$applicationDir-scan.log
		chtag -tc IBM-1047 $DBB_MODELER_LOGS/3-$applicationDir-scan.log
		if [ "$DBB_MODELER_METADATASTORE_TYPE" = "file" ]; then
			declare METADATASTORE_OPTIONS="--file-metadatastore $DBB_MODELER_FILE_METADATA_STORE_DIR"
		elif [ "$DBB_MODELER_METADATASTORE_TYPE" = "db2" ]; then
			if [ -n "$DBB_MODELER_DB2_METADATASTORE_PASSWORD" ]; then
				declare METADATASTORE_OPTIONS="--db2-user $DBB_MODELER_DB2_METADATASTORE_ID --db2-password $DBB_MODELER_DB2_METADATASTORE_PASSWORD --db2-config $DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE"
			elif [ -n "$DBB_MODELER_DB2_METADATASTORE_PASSWORDFILE" ]; then
				declare METADATASTORE_OPTIONS="--db2-user $DBB_MODELER_DB2_METADATASTORE_ID --db2-password-file $DBB_MODELER_DB2_METADATASTORE_PASSWORDFILE --db2-config $DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE"
			fi
		fi		
		CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/src/groovy/scanApplication.groovy \
			-w $DBB_MODELER_APPLICATION_DIR \
			-a $applicationDir \
			${METADATASTORE_OPTIONS} \
			-l $DBB_MODELER_LOGS/3-$applicationDir-scan.log"    
		echo "[INFO] ${CMD}" >> $DBB_MODELER_LOGS/3-$applicationDir-scan.log
		$CMD
	done

	cd $DBB_MODELER_APPLICATION_DIR
	for applicationDir in `ls | grep -v dbb-zappbuild`
	do
		echo "*******************************************************************"
		echo "Assess Include files & Programs usage for '$applicationDir'"
		echo "*******************************************************************"
		touch $DBB_MODELER_LOGS/3-$applicationDir-assessUsage.log
		chtag -tc IBM-1047 $DBB_MODELER_LOGS/3-$applicationDir-assessUsage.log
		if [ "$DBB_MODELER_METADATASTORE_TYPE" = "file" ]; then
			declare METADATASTORE_OPTIONS="--file-metadatastore $DBB_MODELER_FILE_METADATA_STORE_DIR"
		elif [ "$DBB_MODELER_METADATASTORE_TYPE" = "db2" ]; then
			if [ -n "$DBB_MODELER_DB2_METADATASTORE_PASSWORD" ]; then
				declare METADATASTORE_OPTIONS="--db2-user $DBB_MODELER_DB2_METADATASTORE_ID --db2-password $DBB_MODELER_DB2_METADATASTORE_PASSWORD --db2-config $DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE"
			elif [ -n "$DBB_MODELER_DB2_METADATASTORE_PASSWORDFILE" ]; then
				declare METADATASTORE_OPTIONS="--db2-user $DBB_MODELER_DB2_METADATASTORE_ID --db2-password-file $DBB_MODELER_DB2_METADATASTORE_PASSWORDFILE --db2-config $DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE"
			fi
		fi
		CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/src/groovy/assessUsage.groovy \
			--workspace $DBB_MODELER_APPLICATION_DIR \
			--configurations $DBB_MODELER_APPCONFIG_DIR \
			${METADATASTORE_OPTIONS} \
			--application $applicationDir \
			--moveFiles \
			--logFile $DBB_MODELER_LOGS/3-$applicationDir-assessUsage.log"
		echo "[INFO] ${CMD}" >> $DBB_MODELER_LOGS/3-$applicationDir-assessUsage.log
		$CMD
	done
fi
