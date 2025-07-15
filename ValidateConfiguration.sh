#!/bin/env bash
#*******************************************************************************
# Licensed Materials - Property of IBM
# (c) Copyright IBM Corporation 2018, 2024. All Rights Reserved.
#
# Note to U.S. Government Users Restricted Rights:
# Use, duplication or disclosure restricted by GSA ADP Schedule
# Contract with IBM Corp.
#*******************************************************************************
# DBB Git Migration Modeler Configuration

# Default is the root of the Git Repo
DBB_MODELER_HOME=$(cd "$(dirname "$0")" && pwd)

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
	fi
	
	echo ${DBB_GIT_MIGRATION_MODELER_CONFIG_FILE}
	if [ ! -f "${DBB_GIT_MIGRATION_MODELER_CONFIG_FILE}" ]; then
		rc=8
		ERRMSG="[ERROR] DBB Git Migration Modeler configuration file not found. rc="$rc
	fi
}

# Validate Environment Configuration
validateConfig() {
	if [ "$DBB_HOME" = "" ]; then
		rc=8
		ERRMSG="[ERROR] Environment variable DBB_HOME is not set. rc="$rc
	fi
}

# Call validate Options
if [ $rc -eq 0 ]; then
 	validateOptions
fi

# Call Validate Config
if [ $rc -eq 0 ]; then
 	validateConfig
fi

if [ $rc -eq 0 ]; then
	. $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE
	rc=$?
	if [ $rc -ne 0 ]; then
		rc=8
		ERRMSG="[ERROR] Unable to source the DBB Git Migration Modeler Configuration file '${DBB_GIT_MIGRATION_MODELER_CONFIG_FILE}'. rc="$rc
	fi	
fi

if [ $rc -eq 0 ]; then
	if [ -d "${DBB_MODELER_WORK}" ]; then
		rc=8
		ERRMSG="[ERROR] Directory '$DBB_MODELER_WORK' already exists. rc="$rc
	else
		mkdir -p $DBB_MODELER_WORK
		rc=$?
		if [ $rc -ne 0 ]; then
			ERRMSG="[ERROR] Unable to create the DBB Git Migration Modeler working folder '${DBB_MODELER_WORK}'. rc="$rc
		fi	
	fi
fi
	
if [ $rc -eq 0 ]; then
	if [ "$DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORD" = "" ] & [ "$DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE" = "" ]; then
		rc=8
		ERRMSG="[ERROR] Either the Db2 JDBC User Password or the Db2 JDBC Password File must be specified. Exiting. rc="$rc
	fi
fi

if [ $rc -eq 0 ]; then
	if [ ! -d "${DBB_MODELER_APPMAPPINGS_DIR}" ]; then
		mkdir -p $DBB_MODELER_APPMAPPINGS_DIR
		rc=$?
		if [ $rc -ne 0 ]; then
			ERRMSG="[ERROR] Unable to create the DBB Git Migration Modeler Applications Mappings folder '${DBB_MODELER_APPMAPPINGS_DIR}'. rc="$rc
		fi	
	fi
	if [ $rc -eq 0 ]; then
		cp $DBB_MODELER_HOME/samples/applications-mapping/*.* $DBB_MODELER_APPMAPPINGS_DIR/
		rc=$?
		if [ $rc -ne 0 ]; then
			ERRMSG="[ERROR] Unable to copy sample Applications Mappings files to folder '${DBB_MODELER_APPMAPPINGS_DIR}'. rc="$rc
		fi	
	fi
	if [ $rc -eq 0 ]; then
		cp $DBB_MODELER_HOME/samples/repositoryPathsMapping.yaml $REPOSITORY_PATH_MAPPING_FILE
		rc=$?
		if [ $rc -ne 0 ]; then
			ERRMSG="[ERROR] Unable to copy sample Repository Paths Mapping file to '${$REPOSITORY_PATH_MAPPING_FILE}'. rc="$rc
		fi	
	fi
	if [ $rc -eq 0 ]; then
		cp $DBB_MODELER_HOME/samples/types.txt $APPLICATION_MEMBER_TYPE_MAPPING
		rc=$?
		if [ $rc -ne 0 ]; then
			ERRMSG="[ERROR] Unable to copy sample Types file to '${$APPLICATION_MEMBER_TYPE_MAPPING}'. rc="$rc
		fi	
	fi
	if [ $rc -eq 0 ]; then
		cp $DBB_MODELER_HOME/samples/typesConfigurations.yaml $TYPE_CONFIGURATIONS_FILE
		rc=$?
		if [ $rc -ne 0 ]; then
			ERRMSG="[ERROR] Unable to copy sample Types Configurations file to '${$TYPE_CONFIGURATIONS_FILE}'. rc="$rc
		fi	
	fi
	if [ $rc -eq 0 ]; then
		if [ ! -d "${DBB_MODELER_DEFAULT_GIT_CONFIG}" ]; then
			mkdir -p $DBB_MODELER_DEFAULT_GIT_CONFIG
			rc=$?
			if [ $rc -ne 0 ]; then
				ERRMSG="[ERROR] Unable to create sample Git Configuration folder '${$DBB_MODELER_DEFAULT_GIT_CONFIG}'. rc="$rc
			fi	
		fi

		cp $DBB_MODELER_HOME/samples/git-config/* $DBB_MODELER_DEFAULT_GIT_CONFIG/
		rc=$?
		if [ $rc -ne 0 ]; then
			ERRMSG="[ERROR] Unable to copy sample Git Configuration files to '${$DBB_MODELER_DEFAULT_GIT_CONFIG}'. rc="$rc
		fi	
		cp $DBB_MODELER_HOME/samples/git-config/.* $DBB_MODELER_DEFAULT_GIT_CONFIG/
		rc=$?
		if [ $rc -ne 0 ]; then
			ERRMSG="[ERROR] Unable to copy sample Git Configuration files to '${$DBB_MODELER_DEFAULT_GIT_CONFIG}'. rc="$rc
		fi	
	fi
fi

if [ $rc -eq 0 ]; then
	if [ "$DBB_MODELER_METADATASTORE_TYPE" = "db2" ]; then
		## Checking DBB Toolkit version
		CURRENT_DBB_TOOLKIT_VERSION=`$DBB_MODELER_HOME/src/scripts/utils/0-environment.sh -c $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE -v 3.0.1`
		rc=$?
		if [ $rc -ne 0 ]; then
			rc=8
			ERRMSG="[ERROR] The DBB Toolkit's version is $CURRENT_DBB_TOOLKIT_VERSION. To use the Db2-based MetadataStore, the minimal recommended version for the DBB Toolkit is 3.0.1."
		fi
	else
		## Checking DBB Toolkit version
		CURRENT_DBB_TOOLKIT_VERSION=`$DBB_MODELER_HOME/src/scripts/utils/0-environment.sh -c $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE -v 2.0.2`
		rc=$?
		if [ $rc -ne 0 ]; then
			rc=8
			ERRMSG="[ERROR] The DBB Toolkit's version is $CURRENT_DBB_TOOLKIT_VERSION. To use the File-based MetadataStore, the minimal recommended version for the DBB Toolkit is 2.0.2."
		fi
	fi
fi


if [ $rc -ne 0 ]; then
	echo ${ERRMSG}
	exit $rc
else
	if [ "$DBB_MODELER_METADATASTORE_TYPE" = "db2" ]; then
		echo "********************************************* SUGGESTED ACTION *********************************************"
		echo "Check the successful configuration and access to the Db2-based MetadataStore with the following command:"
		echo "'$DBB_MODELER_HOME/src/scripts/CheckDb2MetadataStore.sh -c $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE'"
	fi
	echo
	echo "********************************************* SUGGESTED ACTION *********************************************"
	echo "Tailor the following input files prior to using the DBB Git Migration Modeler:"
	echo "  - Applications Mapping file(s) located in $DBB_MODELER_APPMAPPINGS_DIR"
	echo "  - $REPOSITORY_PATH_MAPPING_FILE"
	echo "  - $APPLICATION_MEMBER_TYPE_MAPPING (optional)"
	echo "  - $TYPE_CONFIGURATIONS_FILE (optional)"
	echo
	echo "Once tailored, run the following command:"
	echo "'$DBB_MODELER_HOME/src/scripts/Migration-Modeler-Start.sh -c $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE'"
fi