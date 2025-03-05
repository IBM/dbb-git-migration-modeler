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

Prolog() {
	echo
	echo " DBB Git Migration Modeler                                                                                  "
	echo " Release:     $MigrationModelerRelease                                                                      "
	echo
	echo " Script:      Setup.sh                                                                                      "
	echo
	echo " Description: This script is setting up a work folder including an configuration file that is used          "
	echo "              by the DBB Git Migration Modeler process.                                                     "
	echo
	echo "              The user will be prompted for several configuration parameters that are saved                 "
	echo "              into a configuration file within the work folder.                                             "
	echo "              The configuration file is passed to the DBB Git Migration Modeler as a required                  "
	echo "              input parameter.                                                                              "
	echo
	echo "              For more information please refer to:    https://github.com/IBM/dbb-git-migration-modeler     "
	echo
}

# internal variables
rc=0          # return code management
CONFIG_DIR="" # Path to store the DBB_GIT_MIGRATION_MODELER.config

# Default is the root of the Git Repo
DBB_MODELER_HOME=$(cd "$(dirname "$0")" && pwd)

#
export MigrationModelerRelease=`cat $DBB_MODELER_HOME/release.properties | awk -F '=' '{printf $2}'`
Prolog

if [  "$DBB_HOME" = "" ]
then
	echo "[ERROR] Environment variable DBB_HOME is not set. Exiting."
	exit 1
fi

# Configure DBB Migration Modeler Home
echo "[SETUP] Configuring DBB Git Migration Modeler environment variables"

# Configure DBB Migration Modeler Work Directory
DBB_MODELER_WORK="${DBB_MODELER_HOME}-work"
read -p "Specify the DBB Git Migration Modeler work directory [default: $DBB_MODELER_WORK]: " variable
if [ "$variable" ]; then
    DBB_MODELER_WORK="${variable}"
fi

# Default environment variables
DBB_MODELER_APPCONFIG_DIR="$DBB_MODELER_WORK/modeler-configs"
DBB_MODELER_APPLICATION_DIR="$DBB_MODELER_WORK/applications"
DBB_MODELER_LOGS="$DBB_MODELER_WORK/logs"
DBB_MODELER_DEFAULT_GIT_CONFIG="$DBB_MODELER_WORK/git-config"

# Migration Modeler MetaDataStore configuration
# Default value for the Metadatastore type - Valid values are "file" or "db2"
DBB_MODELER_METADATASTORE_TYPE="file"
# Default path for the File Metadatastore location
DBB_MODELER_FILE_METADATA_STORE_DIR="$DBB_MODELER_WORK/dbb-metadatastore"
# Default path for the DB2 Metadatastore Connection configuration file
DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE="$DBB_MODELER_WORK/db2Connection.conf"
# DB2 User ID to connect through the JDBC driver
DBB_MODELER_DB2_METADATASTORE_JDBC_ID="user"
# DB2 User ID's Password to connect through the JDBC driver
# The password has to be encrypted as described in:
#    https://www.ibm.com/docs/en/dbb/2.0?topic=customization-encrypting-metadata-store-passwords#db2-encrypted-password-argument
DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORD=""
# Default path for the DB2 Password file to connect through the JDBC driver
# The password file has to be created as described in:
#    https://www.ibm.com/docs/en/dbb/2.0?topic=customization-encrypting-metadata-store-passwords#dbb-db2-password-file
DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE=""

# Migration Modeler Configuration files
# Reference to the configured application mapping file
APPLICATION_MAPPING_FILE=$DBB_MODELER_WORK/applicationsMapping.yaml
# Reference to the repository paths mapping file
REPOSITORY_PATH_MAPPING_FILE=$DBB_MODELER_WORK/repositoryPathsMapping.yaml
# Reference to the type mapping file
APPLICATION_MEMBER_TYPE_MAPPING=$DBB_MODELER_WORK/types.txt
# Reference to the type configuration file to generate build configuration
TYPE_CONFIGURATIONS_FILE=$DBB_MODELER_WORK/typesConfigurations.yaml
# Input files and configuration
APPLICATION_DATASETS=DBEHM.MIG.COBOL,DBEHM.MIG.COPY,DBEHM.MIG.BMS
APPLICATION_ARTIFACTS_HLQ=DBEHM.MIG
# Scanning options
SCAN_DATASET_MEMBERS=false
SCAN_DATASET_MEMBERS_ENCODING=IBM-1047
# Reference to zAppBuild
DBB_ZAPPBUILD=/var/dbb/dbb-zappbuild
# Reference to DBB Community Repo
DBB_COMMUNITY_REPO=/var/dbb/dbb
# Default branch name when initializing Git repositories and scanning files into DBB collections
APPLICATION_DEFAULT_BRANCH=main

# Run the DBB Git Migration Modeler interactively
INTERACTIVE_RUN=true

# Publish to Artifact Repository Server
PUBLISH_ARTIFACTS=true
# URL of the Artifact Repository Server
# e.q.: artifactRepositoryUrl=http://artifactoryserver:8081/artifactory
ARTIFACT_REPOSITORY_SERVER_URL=http://10.3.20.231:8081/artifactory
# User to connect to the Artifact Repository Server
# e.q.:  ARTIFACT_REPOSITORY_USER=admin
ARTIFACT_REPOSITORY_USER=user
# Password to connect to the Artifact Repository Server
# e.q.: ARTIFACT_REPOSITORY_PASSWORD=xxxxx
ARTIFACT_REPOSITORY_PASSWORD=password

# User ID of the pipeline user
PIPELINE_USER=ADO
# Group that the User ID of the pipeline user belongs to
PIPELINE_USER_GROUP=JENKINSG
# Pipeline technology used
# Either '1' for 'AzureDevOps', '2' for 'GitlabCI', '3' for 'Jenkins' or '4' for 'GitHubActions'
# The parameter will then be translated later in the process to its final value 
# as defined in the Templates folder of the DBB Community repo (without the 'Pipeline' suffix)
PIPELINE_CI=1

# Arrays for configuration parameters, that will the Setup script will prompt the user for
path_config_array=(DBB_MODELER_APPCONFIG_DIR DBB_MODELER_APPLICATION_DIR DBB_MODELER_LOGS DBB_MODELER_DEFAULT_GIT_CONFIG)
input_array=(APPLICATION_MAPPING_FILE REPOSITORY_PATH_MAPPING_FILE APPLICATION_MEMBER_TYPE_MAPPING TYPE_CONFIGURATIONS_FILE APPLICATION_DATASETS APPLICATION_ARTIFACTS_HLQ SCAN_DATASET_MEMBERS SCAN_DATASET_MEMBERS_ENCODING DBB_ZAPPBUILD DBB_COMMUNITY_REPO APPLICATION_DEFAULT_BRANCH INTERACTIVE_RUN PUBLISH_ARTIFACTS ARTIFACT_REPOSITORY_SERVER_URL ARTIFACT_REPOSITORY_USER ARTIFACT_REPOSITORY_PASSWORD PIPELINE_USER PIPELINE_USER_GROUP)

# Create work dir
echo
echo "[SETUP] Creating DBB Git Migration Modeler work directory '$DBB_MODELER_WORK'"
echo "[SETUP] Copying DBB Git Migration Modeler configuration files to '$DBB_MODELER_WORK'"
read -p "Do you want to create the directory '$DBB_MODELER_WORK' and copy the DBB Git Migration Modeler configuration files to it (Y/n): " variable

if [[ -z "$variable" || $variable =~ ^[Yy]$ ]]; then
	if [ -d "${DBB_MODELER_WORK}" ]; then
		rc=4
		ERRMSG="[ERROR] Directory '$DBB_MODELER_WORK' already exists. rc="$rc
		echo $ERRMSG
	else
		mkdir -p $DBB_MODELER_WORK
		rc=$?
		if [ $rc -eq 0 ]; then
			cp $DBB_MODELER_HOME/samples/*.* $DBB_MODELER_WORK/
			rc=$?
		fi

		if [ $rc -eq 0 ]; then
			if [ ! -d "${DBB_MODELER_DEFAULT_GIT_CONFIG}" ]; then
				mkdir -p $DBB_MODELER_DEFAULT_GIT_CONFIG
			fi

			cp $DBB_MODELER_HOME/samples/git-config/* $DBB_MODELER_DEFAULT_GIT_CONFIG/
			cp $DBB_MODELER_HOME/samples/git-config/.* $DBB_MODELER_DEFAULT_GIT_CONFIG/
			rc=$?
		fi
	fi
fi

if [ $rc -eq 0 ]; then
	# Specify DBB Metadatastore type and config
	echo
	echo "[SETUP] Specifying DBB Metadatastore type and configuration"
	read -p "Specify the type of the DBB Metadatastore ("file" or "db2") [default: ${DBB_MODELER_METADATASTORE_TYPE}]: " variable
	if [ "$variable" ]; then
		declare DBB_MODELER_METADATASTORE_TYPE="${variable}"
	fi
	
	if [ "$DBB_MODELER_METADATASTORE_TYPE" = "file" ]; then
		read -p "Specify the location of the DBB File Metadatastore [default: ${DBB_MODELER_FILE_METADATA_STORE_DIR}]: " variable
		if [ "$variable" ]; then
			declare DBB_MODELER_FILE_METADATA_STORE_DIR="${variable}"
		fi
	fi

	if [ "$DBB_MODELER_METADATASTORE_TYPE" = "db2" ]; then
		read -p "Specify the location of the DBB Db2 Metadatastore configuration file [default: ${DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE}]: " variable
		if [ "$variable" ]; then
			declare DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE="${variable}"
		fi
		read -p "Specify the DBB Db2 Metadatastore JDBC User ID [default: ${DBB_MODELER_DB2_METADATASTORE_JDBC_ID}]: " variable
		if [ "$variable" ]; then
			declare DBB_MODELER_DB2_METADATASTORE_JDBC_ID="${variable}"
		fi
		read -p "Specify the DBB Db2 Metadatastore JDBC User Password [leave empty if not used]: " variable
		if [ "$variable" ]; then
			declare DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORD="${variable}"
		fi
		read -p "Specify the DBB Db2 Metadatastore JDBC Password File [leave empty if not used]: " variable
		if [ "$variable" ]; then
			declare DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE="${variable}"
		fi
		if [ "$DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORD" = "" ] & [ "$DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE" = "" ]; then
			echo "[ERROR] Either the Db2 JDBC User Password or the Db2 JDBC Password File must be specified. Exiting."
			rm -rf $DBB_MODELER_WORK
			exit 1
		fi		
	fi	
fi

if [ $rc -eq 0 ]; then
	# Specify input files
	echo
	echo "[SETUP] Specifying DBB Git Migration Modeler input configuration"
	for config in ${input_array[@]}; do
		read -p "Specify input parameter $config [default: ${!config}]: " variable
		if [ "$variable" ]; then
			declare ${config}="${variable}"
		fi
	done
fi

# Save DBB Git Migration Modeler Configuration
CONFIG_DIR="$DBB_MODELER_WORK"

if [ $rc -eq 0 ]; then
	# Check that CONFIG DIR exists
	if [ ! -d $CONFIG_DIR ]; then
		rc=4
		ERRMSG="[ERROR] Specified directory '$CONFIG_DIR' to save DBB Git Migration Modeler configuration does not exist. rc="$rc
		echo $ERRMSG
	fi
fi

if [ $rc -eq 0 ]; then
	CONFIG_FILE="${CONFIG_DIR}/DBB_GIT_MIGRATION_MODELER.config"
	touch $CONFIG_FILE
	chtag -tc IBM-1047 $CONFIG_FILE 

	echo "# DBB Git Migration Modeler configuration settings" > $CONFIG_FILE
	echo "# Generated at $(date)" >> $CONFIG_FILE
	echo "" >> $CONFIG_FILE

	echo "DBB_MODELER_HOME=${DBB_MODELER_HOME}" >> $CONFIG_FILE
	echo "DBB_MODELER_WORK=${DBB_MODELER_WORK}" >> $CONFIG_FILE

	echo "" >> $CONFIG_FILE
	echo "# DBB Git Migration Modeler working folders" >> $CONFIG_FILE
	for config in ${path_config_array[@]}; do
	    echo "${config}=${!config}" >> $CONFIG_FILE
	done

	echo "" >> $CONFIG_FILE
	echo "# DBB Git Migration Modeler - DBB Metadatastore configuration" >> $CONFIG_FILE
	echo "DBB_MODELER_METADATASTORE_TYPE=${DBB_MODELER_METADATASTORE_TYPE}" >> $CONFIG_FILE
	echo "DBB_MODELER_FILE_METADATA_STORE_DIR=${DBB_MODELER_FILE_METADATA_STORE_DIR}" >> $CONFIG_FILE
	echo "DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE=${DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE}" >> $CONFIG_FILE
	echo "DBB_MODELER_DB2_METADATASTORE_JDBC_ID=${DBB_MODELER_DB2_METADATASTORE_JDBC_ID}" >> $CONFIG_FILE
	echo "DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORD=${DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORD}" >> $CONFIG_FILE
	echo "DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE=${DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE}" >> $CONFIG_FILE

	echo "" >> $CONFIG_FILE
	echo "# DBB Git Migration Modeler input files" >> $CONFIG_FILE

	for config in ${input_array[@]}; do
		echo "${config}=${!config}" >> $CONFIG_FILE
	done

	echo "Specify the pipeline orchestration technology to use."
	read -p "1 for 'AzureDevOps', 2 for 'GitlabCI', 3 for 'Jenkins' or 4 for 'GitHubActions' [default: 1]: " variable
	if [ "$variable" ]; then
		declare PIPELINE_CI="${variable}"
	else
		declare PIPELINE_CI="1"
	fi
	case ${PIPELINE_CI} in
	"1")
		PIPELINE_CI="AzureDevOps"
		;;
	"2")
		PIPELINE_CI="GitlabCI"
		;;
	"3")
		PIPELINE_CI="Jenkins"
		;;
	"4")
		PIPELINE_CI="GitHubActions"
		;;
	esac		
	echo "PIPELINE_CI=${PIPELINE_CI}" >> $CONFIG_FILE


	echo
	echo "[SETUP] DBB Git Migration Modeler configuration saved to '$CONFIG_FILE'"
	echo "This DBB Git Migration Modeler configuration file will be imported by the DBB Git Migration Modeler process."
	echo
	
	dbbVersion=`$DBB_HOME/bin/dbb --version | grep "Dependency Based Build version" | awk -F' ' '{print $5}'`
	dbbVersionMajor=`echo $dbbVersion | awk -F'.' '{print $1}'`
	dbbVersionMinor=`echo $dbbVersion | awk -F'.' '{print $2}'`
	dbbVersionPatch=`echo $dbbVersion | awk -F'.' '{print $3}'`
	if [ "$DBB_MODELER_METADATASTORE_TYPE" = "db2" ]; then
		## Checking DBB Toolkit version		
		if [ "$dbbVersionMajor" -lt "3" ]; then
			echo
			echo "[ERROR] The DBB Toolkit is $dbbVersion. The minimal required version for the DBB Toolkit to use the Db2-based MetadataStore is 3.0.1."
			exit 1
		elif [ "$dbbVersionMajor" -lt "3" ]; then
			if [ "$dbbVersionMinor" -eq "0" ]; then
				if [ "$dbbVersionPatch" -lt "1" ]; then
					echo
					echo "[ERROR] The DBB Toolkit is $dbbVersion. The minimal required version for the DBB Toolkit to use the Db2-based MetadataStore is 3.0.1."
					exit 1
				fi
			fi
		fi
		echo "********************************************* SUGGESTED ACTION *********************************************"
		echo "Check the successful configuration and access to the Db2-based MetadataStore with the following command:"
		echo "'$DBB_MODELER_HOME/src/scripts/CheckDb2MetadataStore.sh -c $CONFIG_FILE'"
	else
		## Checking DBB Toolkit version		
		if [ "$dbbVersionMajor" -lt "2" ]; then
			echo
			echo "[ERROR] The DBB Toolkit is $dbbVersion. The minimal required version for the DBB Toolkit to use the File-based MetadataStore is 2.0.2."
			exit 1
		elif [ "$dbbVersionMajor" -eq "2" ]; then
			if [ "$dbbVersionMinor" -eq "0" ]; then
				if [ "$dbbVersionPatch" -lt "2" ]; then
					echo
					echo "[ERROR] The DBB Toolkit is $dbbVersion. The minimal required version for the DBB Toolkit to use the File-based MetadataStore is 2.0.2."
					exit 1
				fi
			fi
		fi
	fi
	echo
	echo "********************************************* SUGGESTED ACTION *********************************************"
	echo "Tailor the following input files prior to using the DBB Git Migration Modeler:"
	echo "  - $APPLICATION_MAPPING_FILE "
	echo "  - $REPOSITORY_PATH_MAPPING_FILE "
	echo "  - $APPLICATION_MEMBER_TYPE_MAPPING (optional) "
	echo "  - $TYPE_CONFIGURATIONS_FILE (optional) "
	echo
	echo "Once tailored, run the following command:"
	echo "'$DBB_MODELER_HOME/src/scripts/Migration-Modeler-Start.sh -c $CONFIG_FILE'"
fi
