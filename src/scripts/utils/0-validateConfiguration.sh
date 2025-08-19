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
rc=0

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
				ERRMSG="[ERROR] DBB Git Migration Modeler Configuration file required. rc="$rc
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
				ERRMSG="[ERROR] DBB Git Migration Modeler Configuration file required. rc="$rc
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
				ERRMSG="[ERROR] DBB Toolkit Version required. rc="$rc
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
		rc=8
		ERRMSG="[ERROR] Argument to specify DBB Git Migration Modeler configuration file (-c) is required. rc="$rc
	fi
	
	if [ ! -f "${DBB_GIT_MIGRATION_MODELER_CONFIG_FILE}" ]; then
		rc=8
		ERRMSG="[ERROR] DBB Git Migration Modeler configuration file not found. rc="$rc
	fi
}

# Validate Environment Configuration
validateEnvironment() {
	if [ -z "$DBB_HOME" ]; then
		rc=8
		ERRMSG="[ERROR] Environment variable DBB_HOME is not set. rc="$rc
	fi
	GIT_VERSION=`git --version`
	rc=$?
	if [ $rc -ne 0 ]; then
		rc=8
		ERRMSG="[ERROR] The 'git' command is not available. rc="$rc
	fi	
}

# Validate Configuration File
validateConfigurationFile() {
	. $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE
	rc=$?
	if [ $rc -ne 0 ]; then
		rc=8
		ERRMSG="[ERROR] Unable to source the DBB Git Migration Modeler Configuration file '${DBB_GIT_MIGRATION_MODELER_CONFIG_FILE}'. rc="$rc
	fi	

	if [ $rc -eq 0 ]; then
		if [ "$DBB_MODELER_METADATASTORE_TYPE" = "db2" ]; then
			if [ -z "${DBB_MODELER_DB2_METADATASTORE_JDBC_ID}" ]; then
				rc=8
				ERRMSG="[ERROR] The Db2 MetadataStore User is missing from the Configuration file. Exiting. rc="$rc
			fi
			if [ -z "${DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE}" ]; then
				rc=8
				ERRMSG="[ERROR] The Db2 Connection configuration file is missing from the Configuration file. Exiting. rc="$rc
			else 
				if [ ! -f "${DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE}" ]; then
					rc=8
					ERRMSG="[ERROR] The Db2 Connection configuration file '${DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE}' does not exist. Exiting. rc="$rc
				fi
			fi
			if [ -z "${DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORD}" ] && [ -z "${DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE}" ]; then
				rc=8
				ERRMSG="[ERROR] Either the Db2 MetadataStore User's Password or the Db2 MetadataStore Password File are missing from the Configuration file. Exiting. rc="$rc
			fi	
		elif [ "$DBB_MODELER_METADATASTORE_TYPE" = "file" ]; then
			if [ "$DBB_MODELER_FILE_METADATA_STORE_DIR" = "" ]; then
				rc=8
				ERRMSG="[ERROR] The location of the DBB File-based MetadataStore must be specified. Exiting. rc="$rc
			fi
		else
			rc=8
			ERRMSG="[ERROR] The specified DBB MetadataStore technology is not 'file' or 'db2'. Exiting. rc="$rc
		fi
	fi

	if [ $rc -eq 0 ]; then
		if [ "$DBB_MODELER_METADATASTORE_TYPE" = "db2" ]; then
			## Checking DBB Toolkit version
			REQUIRED_DBB_TOOLKIT_VERSION="3.0.1"
			validateDBBTookitVersion
			rc=$?
			if [ $rc -ne 0 ]; then
				rc=8
				ERRMSG="[ERROR] The DBB Toolkit's version is $CURRENT_DBB_TOOLKIT_VERSION. To use the Db2-based MetadataStore, the minimal recommended version for the DBB Toolkit is 3.0.1."
			fi
		else
			## Checking DBB Toolkit version
			REQUIRED_DBB_TOOLKIT_VERSION="2.0.2"
			validateDBBTookitVersion
			rc=$?
			if [ $rc -ne 0 ]; then
				rc=8
				ERRMSG="[ERROR] The DBB Toolkit's version is $CURRENT_DBB_TOOLKIT_VERSION. To use the File-based MetadataStore, the minimal recommended version for the DBB Toolkit is 2.0.2."
			fi
		fi
	fi
	
	if [ $rc -eq 0 ]; then
		if [ ! -d "${DBB_ZAPPBUILD}" ]; then
			rc=8
			ERRMSG="[ERROR] The dbb-zappbuild instance '${DBB_ZAPPBUILD}' doesn't exist. Exiting. rc="$rc
		fi
	fi
	
	if [ $rc -eq 0 ]; then
		if [ ! -d "${DBB_COMMUNITY_REPO}" ]; then
			rc=8
			ERRMSG="[ERROR] The DBB Community repository instance '${DBB_COMMUNITY_REPO}' doesn't exist. Exiting. rc="$rc
		fi
	fi

	if [ $rc -eq 0 ]; then
		if [ "${PUBLISH_ARTIFACTS}" == "true" ]; then
			if [ -z "${ARTIFACT_REPOSITORY_SERVER_URL}" ]; then
				rc=8
				ERRMSG="[ERROR] The URL of the Artifact Repository Server was not specified. Exiting. rc="$rc
			fi
			if [ $rc -eq 0 ] & [ -z "${ARTIFACT_REPOSITORY_USER}" ]; then
				rc=8
				ERRMSG="[ERROR] The User for the Artifact Repository Server was not specified. Exiting. rc="$rc
			fi
			if [ $rc -eq 0 ] & [ -z "${ARTIFACT_REPOSITORY_PASSWORD}" ]; then
				rc=8
				ERRMSG="[ERROR] The Password of the User for the Artifact Repository Server was not specified. Exiting. rc="$rc
			fi
			if [ $rc -eq 0 ] & [ -z "${ARTIFACT_REPOSITORY_SUFFIX}" ]; then
				rc=8
				ERRMSG="[ERROR] The Suffix for Artifact Repositories was not specified. Exiting. rc="$rc
			fi
			if [ $rc -eq 0 ]; then
				HTTP_CODE=`curl -s -S -o /dev/null -w "%{http_code}\n" ${ARTIFACT_REPOSITORY_SERVER_URL}`
				if [ $HTTP_CODE -ne 200 ] & [ $HTTP_CODE -ne 302 ]; then
					rc=8
					ERRMSG="[ERROR] The Artifact Repository Server '${ARTIFACT_REPOSITORY_SERVER_URL}' is not reachable. See cURL error message. Exiting. rc="$rc
				fi				
			fi
		fi
	fi
}

# Finalize Setup
finalizeSetup() {
	validateConfigurationFile
	rc=$?
	if [ $rc -ne 0 ]; then
		rc=8
		ERRMSG="[ERROR] Unable to source the DBB Git Migration Modeler Configuration file '${DBB_GIT_MIGRATION_MODELER_CONFIG_FILE}'. rc="$rc
	fi	

	if [ $rc -eq 0 ]; then
		if [ -d "${DBB_MODELER_WORK}" ]; then
			rc=8
			ERRMSG="[ERROR] Directory '$DBB_MODELER_WORK' already exists. rc="$rc
		else
			mkdir -p $DBB_MODELER_WORK
			rc=$?
			if [ $rc -ne 0 ]; then
				rc=8
				ERRMSG="[ERROR] Unable to create the DBB Git Migration Modeler working folder '${DBB_MODELER_WORK}'. rc="$rc
			fi	
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
}

# Validate DBB Toolkit Version
validateDBBTookitVersion() {
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
}


# Call Validate Environment
if [ $rc -eq 0 ] & [ "$VALIDATE_ENVIRONMENT" == "true" ]; then
 	validateEnvironment
fi

# Call Validate Configuration File
if [ $rc -eq 0 ] & [ "$VALIDATE_CONFIGURATION_FILE" == "true" ]; then
 	validateOptions
	validateConfigurationFile	
fi

# Call Finalize Setup
if [ $rc -eq 0 ] & [ "$FINALIZE_SETUP" == "true" ]; then
 	validateOptions
	finalizeSetup
fi

# Call Validate DBB Toolkit Version
if [ $rc -eq 0 ] & [ "${VALIDATE_DBB_TOOLKIT_VERSION}" == "true" ]; then
	validateDBBTookitVersion
fi

if [ $rc -ne 0 ]; then
	echo ${ERRMSG}
	export ERRMSG=${ERRMSG}
	exit $rc
fi