#!/bin/env bash
#*******************************************************************************
# Licensed Materials - Property of IBM
# (c) Copyright IBM Corporation 2018, 2024. All Rights Reserved.
#
# Note to U.S. Government Users Restricted Rights:
# Use, duplication or disclosure restricted by GSA ADP Schedule
# Contract with IBM Corp. 
#*******************************************************************************

Prolog() {
	echo
	echo " DBB Git Migration Modeler                                                                                  "
	echo " Release:     $MigrationModelerRelease                                                                      "
	echo
	echo " Script:      Refresh-Application-Descriptor-Files.sh                                                          "
	echo
	echo " Description: The purpose of this script is to help keeping the Application Descriptor files of existing    "
	echo "              applications up-to-date. The script scans the artifacts belonging to the application,         "
	echo "              removes existing source groups from the Application Descriptor files and run                  "
	echo "              the usage assessment process again to populate the Application Descriptor files correctly.    "
	echo "              The script inspects all folders within the referenced 'DBB_MODELER_APPLICATIONS' directory.   "
	echo
	echo "              You must customize the process to your needs if you want to update the Application Descriptor "
	echo "              files of applications that are already migrated to a central Git provider.                    "
	echo "              For more information please refer to:    https://github.com/IBM/dbb-git-migration-modeler     "
	echo
}

# Internal variables
DBB_GIT_MIGRATION_MODELER_CONFIG_FILE=
APPLICATIONS=
rc=0

# Get Options
if [ $rc -eq 0 ]; then
	while getopts "c:a:" opt; do
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
		a)
			argument="$OPTARG"
			nextchar="$(expr substr $argument 1 1)"
			if [ -z "$argument" ] || [ "$nextchar" = "-" ]; then
				rc=4
				ERRMSG="[ERROR] Comma-separated Applications list required. rc="$rc
				echo $ERRMSG
				break
			fi
			APPLICATIONS="$argument"
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
	fi
	
	if [ ! -f "${DBB_GIT_MIGRATION_MODELER_CONFIG_FILE}" ]; then
		rc=8
		ERRMSG="[ERROR] DBB Git Migration Modeler configuration file not found. rc="$rc
	fi
}

# Call validate Options
if [ $rc -eq 0 ]; then
 	validateOptions
fi

if [ $rc -eq 0 ]; then
	# Environment variables setup
	dir=$(dirname "$0")
	. $dir/utils/0-validateConfiguration.sh -e -c ${DBB_GIT_MIGRATION_MODELER_CONFIG_FILE}
	rc=$?

	if [ $rc -ne 0 ]; then
		exit $rc
	fi

	PGM="Refresh-Application-Descriptor-Files.sh"
	# Print Prolog
	export MigrationModelerRelease=`cat $DBB_MODELER_HOME/release.properties | awk -F '=' '{printf $2}'`
	Prolog
fi
	
if [ $rc -eq 0 ]; then
	# Build MetadataStore
	# Drop and recreate the Build MetadataStore folder
	if [ "$DBB_MODELER_METADATASTORE_TYPE" = "file" ]; then
		if [ -d $DBB_MODELER_FILE_METADATA_STORE_DIR ]; then 
			rm -rf $DBB_MODELER_FILE_METADATA_STORE_DIR
		fi
		if [ ! -d $DBB_MODELER_FILE_METADATA_STORE_DIR ]; then 
			mkdir -p $DBB_MODELER_FILE_METADATA_STORE_DIR
		fi
	fi

    APPLICATIONS=",${APPLICATIONS},"
    
	# Scan files
	cd $DBB_MODELER_APPLICATION_DIR
	for applicationDir in `ls | grep -v dbb-zappbuild`
	do
		if [ "$APPLICATIONS" == ",," ] || [[ ${APPLICATIONS} == *",${applicationDir},"* ]]; then
			echo "*******************************************************************"
			echo "Scan application directory '$DBB_MODELER_APPLICATION_DIR/$applicationDir'"
			echo "*******************************************************************"
			CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/src/groovy/scanApplication.groovy \
				--configFile $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE \
				--application $applicationDir \
				--logFile $DBB_MODELER_LOGS/3-$applicationDir-scan.log"    
			echo "[INFO] ${CMD}" >> $DBB_MODELER_LOGS/3-$applicationDir-scan.log
			$CMD
		fi
	done

	# Reset Application Descriptor
	cd $DBB_MODELER_APPLICATION_DIR
	for applicationDir in `ls | grep -v dbb-zappbuild`
	do
		if [ "$APPLICATIONS" == ",," ] || [[ ${APPLICATIONS} == *",${applicationDir},"* ]]; then
			echo "*******************************************************************"
			echo "Reset Application Descriptor for $applicationDir"
			echo "*******************************************************************"
##			touch $DBB_MODELER_LOGS/3-$applicationDir-createApplicationDescriptor.log
##			chtag -tc IBM-1047 $DBB_MODELER_LOGS/3-$applicationDir-createApplicationDescriptor.log
			CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/src/groovy/recreateApplicationDescriptor.groovy \
				--configFile $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE \
				--application $applicationDir \
				--logFile $DBB_MODELER_LOGS/3-$applicationDir-createApplicationDescriptor.log"
			echo "[CMD] $CMD" > $DBB_MODELER_LOGS/3-$applicationDir-createApplicationDescriptor.log
			$CMD
		fi
	done

	# Assess file usage across applications
	cd $DBB_MODELER_APPLICATION_DIR
	for applicationDir in `ls | grep -v dbb-zappbuild`
	do
		if [ "$APPLICATIONS" == ",," ] || [[ ${APPLICATIONS} == *",${applicationDir},"* ]]; then
			echo "*******************************************************************"
			echo "Assess Include files & Programs usage for '$applicationDir'"
			echo "*******************************************************************"
			CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/src/groovy/assessUsage.groovy \
				--configFile $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE \
				--application $applicationDir \
				--moveFiles \
				--logFile $DBB_MODELER_LOGS/3-$applicationDir-assessUsage.log"
			echo "[INFO] ${CMD}" >> $DBB_MODELER_LOGS/3-$applicationDir-assessUsage.log
			$CMD
		fi
	done
	
	# Drop and recreate the Build Metadatastore folder
	if [ "$DBB_MODELER_METADATASTORE_TYPE" = "file" ]; then
		if [ -d $DBB_MODELER_FILE_METADATA_STORE_DIR ]; then 
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
		if [ "$APPLICATIONS" == ",," ] || [[ ${APPLICATIONS} == *",${applicationDir},"* ]]; then
			echo "*******************************************************************"
			echo "Rescan application directory '$DBB_MODELER_APPLICATION_DIR/$applicationDir'"
			echo "*******************************************************************"
			CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/src/groovy/scanApplication.groovy \
				--configFile $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE \
				--application $applicationDir \
				--logFile $DBB_MODELER_LOGS/3-$applicationDir-rescan.log"    
			echo "[INFO] ${CMD}" >> $DBB_MODELER_LOGS/3-$applicationDir-rescan.log
			$CMD
		fi
	done
fi

if [ $rc -ne 0 ]; then
	echo ${ERRMSG}
	exit $rc
fi