#!/bin/sh
#*******************************************************************************
# Licensed Materials - Property of IBM
# (c) Copyright IBM Corporation 2018, 2024. All Rights Reserved.
#
# Note to U.S. Government Users Restricted Rights:
# Use, duplication or disclosure restricted by GSA ADP Schedule
# Contract with IBM Corp. 
#*******************************************************************************

Prolog() {
	echo $PGM":                                                                                                            "
	echo $PGM": DBB Git Migration Modeler                                                                                  "
	echo $PGM": Release:     $MigrationModelerRelease                                                                      "
	echo $PGM":                                                                                                            "
	echo $PGM": Script:      refreshApplicationDescriptorFiles.sh                                                          "
	echo $PGM":                                                                                                            "
	echo $PGM": Description: The purpose of this script is to help keeping the Application Descriptor files of existing    "
	echo $PGM":              applications up-to-date. The script scans the artifacts belonging to the application,         "
	echo $PGM":              removes existing source groups from the Application Descriptor files and run                  "
	echo $PGM":              the usage assessment process again to populate the Application Descriptor files correctly.    "
	echo $PGM":              The script inspects all folders within the referenced 'DBB_MODELER_APPLICATIONS' directory.   "
	echo $PGM":                                                                                                            "
	echo $PGM":              You must customize the process to your needs if you want to update the Application Descriptor "
	echo $PGM":              files of applications that are already migrated to a central Git provider.                    "
	echo $PGM":              For more information please refer to:    https://github.com/IBM/dbb-git-migration-modeler     "
	echo $PGM":                                                                                                            "
}

# Internal variables
DBB_GIT_MIGRATION_MODELER_CONFIG_FILE=""
rc=0
PGM="Refresh-Application-Descriptor-Files.sh"
#$PGM":

# Initialize Migration Modeler
dir=$(dirname "$0")
. $dir/utils/0-environment.sh "$@"

# Print Prolog
Prolog

if [  "$DBB_HOME" = "" ]
then
	echo $PGM":[ERROR] Environment variable DBB_HOME is not set. Exiting."
else
	#### Build Metadatastore
	echo $PGM":[ERROR] Initializing DBB Metadatastore at $DBB_MODELER_METADATA_STORE_DIR."
	if [ -d $DBB_MODELER_METADATA_STORE_DIR ] 
	then
		rm -rf $DBB_MODELER_METADATA_STORE_DIR
	fi

	if [ ! -d $DBB_MODELER_METADATA_STORE_DIR ] 
	then
		mkdir -p $DBB_MODELER_METADATA_STORE_DIR
	fi

	if [ ! -d "$DBB_MODELER_APPLICATION_DIR" ]; then
		echo $PGM":[ERROR] The folder ($DBB_MODELER_APPLICATION_DIR) indicated by the 'DBB_MODELER_APPLICATION_DIR' does not exist. Exiting."
		exit 1
	fi

	# Scan files
	cd $DBB_MODELER_APPLICATION_DIR
	for applicationDir in `ls | grep -v dbb-zappbuild`
	do
		echo $PGM":*******************************************************************"
		echo $PGM":Scan application directory $DBB_MODELER_APPLICATION_DIR/$applicationDir"
		echo $PGM":*******************************************************************"
		CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/src/groovy/scanApplication.groovy \
			-w $DBB_MODELER_APPLICATION_DIR \
			-a $applicationDir \
			-m $DBB_MODELER_METADATA_STORE_DIR \
			-l $DBB_MODELER_LOGS/3-$applicationDir-scan.log"   
		echo $PGM": [CMD] $CMD"
		$CMD
	done

	# Reset Application Descriptor
	cd $DBB_MODELER_APPLICATION_DIR
	for applicationDir in `ls | grep -v dbb-zappbuild`
	do
		echo $PGM":*******************************************************************"
		echo $PGM":Reset Application Descriptor for $applicationDir"
		echo $PGM":*******************************************************************"
		CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/src/groovy/recreateApplicationDescriptor.groovy \
			--workspace $DBB_MODELER_APPLICATION_DIR \
			--application $applicationDir \
			--repositoryPathsMapping $DBB_MODELER_WORK/repositoryPathsMapping.yaml \
			--logFile $DBB_MODELER_LOGS/3-$applicationDir-createApplicationDescriptor.log"
		echo $PGM": [CMD] $CMD"
		$CMD
	done

	cd $DBB_MODELER_APPLICATION_DIR
	for applicationDir in `ls | grep -v dbb-zappbuild`
	do
		echo $PGM":*******************************************************************"
		echo $PGM":Assess Include files & Programs usage for $applicationDir"
		echo $PGM":*******************************************************************"
		CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/src/groovy/assessUsage.groovy \
			--workspace $DBB_MODELER_APPLICATION_DIR \
			--metadatastore $DBB_MODELER_METADATA_STORE_DIR \
			--application $applicationDir \
			--logFile $DBB_MODELER_LOGS/3-$applicationDir-assessUsage.log"
		echo $PGM": [CMD] $CMD"			
		$CMD
	done
fi
