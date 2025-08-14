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

	# Drop and recreate the Build MetadataStore folder
	if [ "$DBB_MODELER_METADATASTORE_TYPE" = "file" ]; then
		if [ -d $DBB_MODELER_FILE_METADATA_STORE_DIR ]; then 
			rm -rf $DBB_MODELER_FILE_METADATA_STORE_DIR
		fi
		if [ ! -d $DBB_MODELER_FILE_METADATA_STORE_DIR ]; then 
			mkdir -p $DBB_MODELER_FILE_METADATA_STORE_DIR
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
		CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/src/groovy/scanApplication.groovy \
			--configFile $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE \
			--application $applicationDir \
			--logFile $DBB_MODELER_LOGS/3-$applicationDir-scan.log"    
		echo "[INFO] ${CMD}" >> $DBB_MODELER_LOGS/3-$applicationDir-scan.log
		$CMD
	done

	# Assess file usage across applications
	cd $DBB_MODELER_APPLICATION_DIR
	for applicationDir in `ls | grep -v dbb-zappbuild`
	do
		echo "*******************************************************************"
		echo "Assess Include files & Programs usage for '$applicationDir'"
		echo "*******************************************************************"
		touch $DBB_MODELER_LOGS/3-$applicationDir-assessUsage.log
		chtag -tc IBM-1047 $DBB_MODELER_LOGS/3-$applicationDir-assessUsage.log
		CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/src/groovy/assessUsage.groovy \
			--configFile $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE \
			--application $applicationDir \
			--moveFiles \
			--logFile $DBB_MODELER_LOGS/3-$applicationDir-assessUsage.log"
		echo "[INFO] ${CMD}" >> $DBB_MODELER_LOGS/3-$applicationDir-assessUsage.log
		$CMD
	done
	
	# Drop and recreate the Build Metadatastore folder
	if [ "$DBB_MODELER_METADATASTORE_TYPE" = "file" ]; then
		if [ -d $DBB_MODELER_METADATA_STORE_DIR ]; then 
			rm -rf $DBB_MODELER_FILE_METADATA_STORE_DIR
		fi
		if [ ! -d $DBB_MODELER_FILE_METADATA_STORE_DIR ]; then 
			mkdir -p $DBB_MODELER_FILE_METADATA_STORE_DIR
		fi
	fi

	# Scan files again after dropping the file metadatastore
	# Collections are dropped from the groovy script when using Db2 MetadataStore
	cd $DBB_MODELER_APPLICATION_DIR
	for applicationDir in `ls | grep -v dbb-zappbuild`
	do
		echo "*******************************************************************"
		echo "Rescan application directory '$DBB_MODELER_APPLICATION_DIR/$applicationDir'"
		echo "*******************************************************************"
		touch $DBB_MODELER_LOGS/3-$applicationDir-rescan.log
		chtag -tc IBM-1047 $DBB_MODELER_LOGS/3-$applicationDir-rescan.log
		CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/src/groovy/scanApplication.groovy \
			--configFile $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE \
			--application $applicationDir \
			--logFile $DBB_MODELER_LOGS/3-$applicationDir-rescan.log"    
		echo "[INFO] ${CMD}" >> $DBB_MODELER_LOGS/3-$applicationDir-rescan.log
		$CMD
	done
fi
