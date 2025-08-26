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
VALIDATE_CONFIGURATION_FILE="false"
VALIDATE_ENVIRONMENT="false"
FINALIZE_SETUP="false"
VALIDATE_DBB_TOOLKIT_VERSION="false"
ERRMSG=""
rc=0
global_rc=0

OPTIND=1
# Get Options
if [ $rc -eq 0 ]; then
	while getopts "c:ef:v:" opt; do
		case $opt in
		c)
			argument="$OPTARG"
			nextchar="$(expr substr $argument 1 1)"
			if [ -z "$argument" ] || [ "$nextchar" = "-" ]; then
				rc=4
				ERRMSG="[ERROR] DBB Git Migration Modeler Configuration file required."
				echo $ERRMSG
				break
			fi
			DBB_GIT_MIGRATION_MODELER_CONFIG_FILE="$argument"
			VALIDATE_CONFIGURATION_FILE="true"
			;;
		e)
			VALIDATE_ENVIRONMENT="true"
			;;
		f)
			argument="$OPTARG"
			nextchar="$(expr substr $argument 1 1)"
			if [ -z "$argument" ] || [ "$nextchar" = "-" ]; then
				rc=4
				ERRMSG="[ERROR] DBB Git Migration Modeler Configuration file required."
				echo $ERRMSG
				break
			fi
			DBB_GIT_MIGRATION_MODELER_CONFIG_FILE="$argument"
			FINALIZE_SETUP="true"
			;;
		v)
			argument="$OPTARG"
			nextchar="$(expr substr $argument 1 1)"
			if [ -z "$argument" ] || [ "$nextchar" = "-" ]; then
				rc=4
				ERRMSG="[ERROR] DBB Toolkit Version required."
				echo $ERRMSG
				break
			fi
			REQUIRED_DBB_TOOLKIT_VERSION="$argument"
			VALIDATE_DBB_TOOLKIT_VERSION="true"
			;;
			
		esac
	done
fi
#

# Validate Options
validateOptions() {
	if [ -z "${DBB_GIT_MIGRATION_MODELER_CONFIG_FILE}" ]; then
		global_rc=8
		ERRMSG="[ERROR] Argument to specify DBB Git Migration Modeler configuration file (-c) is required."
		echo $ERRMSG
	fi
	
	if [ ! -f "${DBB_GIT_MIGRATION_MODELER_CONFIG_FILE}" ]; then
		global_rc=8
		ERRMSG="[ERROR] DBB Git Migration Modeler configuration file not found."
		echo $ERRMSG
	fi
}

# Validate Environment Configuration
validateEnvironment() {
	if [ -z "$DBB_HOME" ]; then
		global_rc=8
		ERRMSG="[ERROR] Environment variable 'DBB_HOME' is not set."
		echo $ERRMSG
	fi
	if [ ! -f "$DBB_HOME/bin/dbb" ]; then
		global_rc=8
		ERRMSG="[ERROR] The 'dbb' program was not found in DBB_HOME '$DBB_HOME'."
		echo $ERRMSG
	fi
	GIT_VERSION=`git --version`
	rc=$?
	if [ $rc -ne 0 ]; then
		global_rc=8
		ERRMSG="[ERROR] The 'git' command is not available."
		echo $ERRMSG
	fi
}

# Validate Configuration File
validateConfigurationFile() {
	. $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE
	rc=$?
	if [ $rc -ne 0 ]; then
		global_rc=8
		ERRMSG="[ERROR] Unable to source the DBB Git Migration Modeler Configuration file '${DBB_GIT_MIGRATION_MODELER_CONFIG_FILE}'."
		echo $ERRMSG
	else
		if [ "$DBB_MODELER_METADATASTORE_TYPE" = "db2" ]; then
			if [ -z "${DBB_MODELER_DB2_METADATASTORE_JDBC_ID}" ]; then
				global_rc=8
				ERRMSG="[ERROR] The Db2 MetadataStore User is missing from the Configuration file."
				echo $ERRMSG
			fi
			if [ -z "${DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE}" ]; then
				global_rc=8
				ERRMSG="[ERROR] The Db2 Connection configuration file is missing from the Configuration file."
				echo $ERRMSG
			fi
			if [ ! -f "${DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE}" ]; then
				global_rc=8
				ERRMSG="[ERROR] The Db2 Connection configuration file '${DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE}' does not exist."
				echo $ERRMSG
			fi
			if [ -z "${DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE}" ]; then
				global_rc=8
				ERRMSG="[ERROR] The Db2 MetadataStore Password File is missing from the Configuration file."
				echo $ERRMSG
			fi	
			if [ ! -f "${DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE}" ]; then
				global_rc=8
				ERRMSG="[ERROR] The Db2 MetadataStore Password File '${DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE}' does not exist."
				echo $ERRMSG
			fi
			## Checking DBB Toolkit version
			REQUIRED_DBB_TOOLKIT_VERSION="2.0.2"
			validateDBBTookitVersion
			rc=$?
			if [ $rc -ne 0 ]; then
				global_rc=8
				ERRMSG="[ERROR] The DBB Toolkit's version is $CURRENT_DBB_TOOLKIT_VERSION. To use the File-based MetadataStore, the minimal recommended version for the DBB Toolkit is 2.0.2."
				echo $ERRMSG
			fi
		elif [ "$DBB_MODELER_METADATASTORE_TYPE" = "file" ]; then
			if [ "$DBB_MODELER_FILE_METADATA_STORE_DIR" = "" ]; then
				global_rc=8
				ERRMSG="[ERROR] The location of the DBB File-based MetadataStore must be specified."
				echo $ERRMSG
			fi
			## Checking DBB Toolkit version
			REQUIRED_DBB_TOOLKIT_VERSION="3.0.1"
			validateDBBTookitVersion
			
			if [ $global_rc -ne 0 ]; then
				global_rc=8
				ERRMSG="[ERROR] The DBB Toolkit's version is $CURRENT_DBB_TOOLKIT_VERSION. To use the Db2-based MetadataStore, the minimal recommended version for the DBB Toolkit is 3.0.1."
				echo $ERRMSG
			fi
		else
			global_rc=8
			ERRMSG="[ERROR] The specified DBB MetadataStore technology is not 'file' or 'db2'."
			echo $ERRMSG
		fi
		if [ ! -d "${DBB_ZAPPBUILD}" ]; then
			global_rc=8
			ERRMSG="[ERROR] The dbb-zappbuild instance '${DBB_ZAPPBUILD}' doesn't exist."
			echo $ERRMSG
		fi
		
		if [ ! -d "${DBB_COMMUNITY_REPO}" ]; then
			global_rc=8
			ERRMSG="[ERROR] The DBB Community repository instance '${DBB_COMMUNITY_REPO}' doesn't exist."
			echo $ERRMSG
		fi
		if [ "${PUBLISH_ARTIFACTS}" == "true" ]; then
			if [ -z "${ARTIFACT_REPOSITORY_SERVER_URL}" ]; then
				global_rc=8
				ERRMSG="[ERROR] The URL of the Artifact Repository Server was not specified."
				echo $ERRMSG
			else
				HTTP_CODE=`curl -s -S -o /dev/null -w "%{http_code}\n" ${ARTIFACT_REPOSITORY_SERVER_URL}`
				if [ $HTTP_CODE -ne 200 ] & [ $HTTP_CODE -ne 302 ]; then
					global_rc=8
					ERRMSG="[ERROR] The Artifact Repository Server '${ARTIFACT_REPOSITORY_SERVER_URL}' is not reachable. Check cURL error message with command 'curl -S ${ARTIFACT_REPOSITORY_SERVER_URL}'."
					echo $ERRMSG
				fi				
			fi
			if [ -z "${ARTIFACT_REPOSITORY_USER}" ]; then
				global_rc=8
				ERRMSG="[ERROR] The User for the Artifact Repository Server was not specified."
				echo $ERRMSG
			fi
			if [ -z "${ARTIFACT_REPOSITORY_PASSWORD}" ]; then
				global_rc=8
				ERRMSG="[ERROR] The Password of the User for the Artifact Repository Server was not specified."
				echo $ERRMSG
			fi
			if [ -z "${ARTIFACT_REPOSITORY_SUFFIX}" ]; then
				global_rc=8
				ERRMSG="[ERROR] The Suffix for Artifact Repositories was not specified."
				echo $ERRMSG
			fi
		fi
	fi
}

# Finalize Setup
finalizeSetup() {
	validateConfigurationFile

	if [ $rc -eq 0 ]; then
		if [ -d "${DBB_MODELER_WORK}" ]; then
			rc=8
			ERRMSG="[ERROR] Directory '$DBB_MODELER_WORK' already exists."
			echo $ERRMSG
		else
			mkdir -p $DBB_MODELER_WORK
			rc=$?
			if [ $rc -ne 0 ]; then
				rc=8
				ERRMSG="[ERROR] Unable to create the DBB Git Migration Modeler working folder '${DBB_MODELER_WORK}'."
				echo $ERRMSG
			fi	
		fi
	fi
	
	if [ $rc -eq 0 ]; then
		if [ ! -d "${DBB_MODELER_APPMAPPINGS_DIR}" ]; then
			mkdir -p $DBB_MODELER_APPMAPPINGS_DIR
			rc=$?
			if [ $rc -ne 0 ]; then
				ERRMSG="[ERROR] Unable to create the DBB Git Migration Modeler Applications Mappings folder '${DBB_MODELER_APPMAPPINGS_DIR}'."
				echo $ERRMSG
			fi	
		fi
		if [ $rc -eq 0 ]; then
			cp $DBB_MODELER_HOME/samples/applications-mapping/*.* $DBB_MODELER_APPMAPPINGS_DIR/
			rc=$?
			if [ $rc -ne 0 ]; then
				ERRMSG="[ERROR] Unable to copy sample Applications Mappings files to folder '${DBB_MODELER_APPMAPPINGS_DIR}'."
				echo $ERRMSG
			fi	
		fi
		if [ $rc -eq 0 ]; then
			cp $DBB_MODELER_HOME/samples/repositoryPathsMapping.yaml $REPOSITORY_PATH_MAPPING_FILE
			rc=$?
			if [ $rc -ne 0 ]; then
				ERRMSG="[ERROR] Unable to copy sample Repository Paths Mapping file to '${$REPOSITORY_PATH_MAPPING_FILE}'."
				echo $ERRMSG
			fi	
		fi
		if [ $rc -eq 0 ]; then
			cp $DBB_MODELER_HOME/samples/types.txt $APPLICATION_MEMBER_TYPE_MAPPING
			rc=$?
			if [ $rc -ne 0 ]; then
				ERRMSG="[ERROR] Unable to copy sample Types file to '${$APPLICATION_MEMBER_TYPE_MAPPING}'."
				echo $ERRMSG
			fi	
		fi
		if [ $rc -eq 0 ]; then
			cp $DBB_MODELER_HOME/samples/typesConfigurations.yaml $TYPE_CONFIGURATIONS_FILE
			rc=$?
			if [ $rc -ne 0 ]; then
				ERRMSG="[ERROR] Unable to copy sample Types Configurations file to '${$TYPE_CONFIGURATIONS_FILE}'."
				echo $ERRMSG
			fi	
		fi
		if [ $rc -eq 0 ]; then
			if [ ! -d "${DBB_MODELER_DEFAULT_GIT_CONFIG}" ]; then
				mkdir -p $DBB_MODELER_DEFAULT_GIT_CONFIG
				rc=$?
				if [ $rc -ne 0 ]; then
					ERRMSG="[ERROR] Unable to create sample Git Configuration folder '${$DBB_MODELER_DEFAULT_GIT_CONFIG}'."
					echo $ERRMSG
				fi	
			fi
	
			cp $DBB_MODELER_HOME/samples/git-config/* $DBB_MODELER_DEFAULT_GIT_CONFIG/
			rc=$?
			if [ $rc -ne 0 ]; then
				ERRMSG="[ERROR] Unable to copy sample Git Configuration files to '${$DBB_MODELER_DEFAULT_GIT_CONFIG}'."
				echo $ERRMSG
			fi	
			cp $DBB_MODELER_HOME/samples/git-config/.* $DBB_MODELER_DEFAULT_GIT_CONFIG/
			rc=$?
			if [ $rc -ne 0 ]; then
				ERRMSG="[ERROR] Unable to copy sample Git Configuration files to '${$DBB_MODELER_DEFAULT_GIT_CONFIG}'."
				echo $ERRMSG
			fi	
		fi
	fi
}

# Validate DBB Toolkit Version
validateDBBTookitVersion() {
	validateEnvironment
	if [ $rc -eq 0 ]; then
		export CURRENT_DBB_TOOLKIT_VERSION=`$DBB_HOME/bin/dbb --version | grep "Dependency Based Build version" | awk -F' ' '{print $5}'`
		currentDBBToolkitVersionMajor=`echo $CURRENT_DBB_TOOLKIT_VERSION | awk -F'.' '{print $1}'`
		currentDBBToolkitVersionMinor=`echo $CURRENT_DBB_TOOLKIT_VERSION | awk -F'.' '{print $2}'`
		currentDBBToolkitVersionPatch=`echo $CURRENT_DBB_TOOLKIT_VERSION | awk -F'.' '{print $3}'`
		expectedDBBToolkitVersionMajor=`echo $REQUIRED_DBB_TOOLKIT_VERSION | awk -F'.' '{print $1}'`
		expectedDBBToolkitVersionMinor=`echo $REQUIRED_DBB_TOOLKIT_VERSION | awk -F'.' '{print $2}'`
		expectedDBBToolkitVersionPatch=`echo $REQUIRED_DBB_TOOLKIT_VERSION | awk -F'.' '{print $3}'`
		
		if [ "$currentDBBToolkitVersionMajor" -lt "$expectedDBBToolkitVersionMajor" ]; then
			rc=8
		elif [ "$currentDBBToolkitVersionMajor" -eq "$expectedDBBToolkitVersionMajor" ]; then
			if [ "$currentDBBToolkitVersionMinor" -lt "$expectedDBBToolkitVersionMinor" ]; then
				rc=8
			elif [ "$currentDBBToolkitVersionMinor" -eq "$expectedDBBToolkitVersionMinor" ]; then
				if [ "$currentDBBToolkitVersionPatch" -lt "$expectedDBBToolkitVersionPatch" ]; then
					rc=8
				fi
			fi
		fi
	fi
}


# Call Validate Environment
if [ $global_rc -eq 0 ] && [ "$VALIDATE_ENVIRONMENT" == "true" ]; then
 	validateEnvironment
fi

# Call Validate Configuration File
if [ $global_rc -eq 0 ] && [ "$VALIDATE_CONFIGURATION_FILE" == "true" ]; then
 	validateOptions
 	if [ $rc -eq 0 ]; then
		validateConfigurationFile
	fi	
fi

# Call Finalize Setup
if [ $global_rc -eq 0 ] && [ "$FINALIZE_SETUP" == "true" ]; then
 	validateOptions
 	if [ $rc -eq 0 ]; then
		finalizeSetup
	fi
fi

# Call Validate DBB Toolkit Version
if [ $global_rc -eq 0 ] && [ "${VALIDATE_DBB_TOOLKIT_VERSION}" == "true" ]; then
	validateDBBTookitVersion
fi

if [ $global_rc -ne 0 ]; then
	echo "[ERROR] Failures detected while checking the DBB Git Migration Modeler configuration. Please see above issues. rc="$global_rc
	exit $global_rc
fi

