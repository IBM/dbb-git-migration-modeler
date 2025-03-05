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
rc=0
PGM="Refresh-Application-Descriptor-Files.sh"

# Initialize Migration Modeler
dir=$(dirname "$0")
srcdir=$(dirname "$dir")
rootdir=$(dirname "$srcdir")
export MigrationModelerRelease=`cat $rootdir/release.properties | awk -F '=' '{printf $2}'`
. $dir/utils/0-environment.sh "$@"

# Print Prolog
Prolog

if [  "$DBB_HOME" = "" ]
then
	echo "[ERROR] Environment variable DBB_HOME is not set. Exiting."
else
	# Build MetadataStore
	# Drop and recreate the Build MetadataStore folder
	if [ "$DBB_MODELER_METADATASTORE_TYPE" = "file" ]; then
		if [ -z "${DBB_MODELER_FILE_METADATA_STORE_DIR}" ]; then
			echo "[ERROR] File MetadataStore location is missing from the Configuration file. Exiting."
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


	# Reset Application Descriptor
	cd $DBB_MODELER_APPLICATION_DIR
	for applicationDir in `ls | grep -v dbb-zappbuild`
	do
		echo "*******************************************************************"
		echo "Reset Application Descriptor for $applicationDir"
		echo "*******************************************************************"
		CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/src/groovy/recreateApplicationDescriptor.groovy \
			--configFile $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE \
			--application $applicationDir \
			--logFile $DBB_MODELER_LOGS/3-$applicationDir-createApplicationDescriptor.log"
		echo "[CMD] $CMD" > $DBB_MODELER_LOGS/3-$applicationDir-createApplicationDescriptor.log
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
		if [ -d $DBB_MODELER_METADATA_STORE_DIR ] 
		then
			rm -rf $DBB_MODELER_FILE_METADATA_STORE_DIR
		fi
		if [ ! -d $DBB_MODELER_FILE_METADATA_STORE_DIR ] 
		then
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