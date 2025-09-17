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
	echo "              This configuration file is then passed to the ValidateConfiguration.sh script                 "
	echo "              to ensure, as much as possible, the correctness of the provided information.                  "
	echo
	echo "              The configuration file is passed to the DBB Git Migration Modeler as a required               "
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
export MigrationModelerRelease=$(cat $DBB_MODELER_HOME/release.properties | awk -F '=' '{printf $2}')
Prolog

# Configure DBB Migration Modeler work folder
echo "[SETUP] Configuring DBB Git Migration Modeler work directory"

# Configure DBB Migration Modeler Work Directory
DBB_MODELER_WORK="${DBB_MODELER_HOME}-work"
read -p "Specify the DBB Git Migration Modeler work directory [default: $DBB_MODELER_WORK]: " variable
if [ "$variable" ]; then
	DBB_MODELER_WORK="${variable}"
fi

if [ -d "${DBB_MODELER_WORK}" ]; then
	CONTINUE_SETUP="N"
	echo
	echo "[WARNING] Directory '$DBB_MODELER_WORK' already exists!"
	echo "[WARNING] There might be configuration files and migrated applications already present in '$DBB_MODELER_WORK'."
	read -p "Do you want to remove this folder and continue the Setup? (N/y) [default: N]: " variable
	if [ "$variable" ]; then
		CONTINUE_SETUP="${variable}"
	fi
	if [ "${CONTINUE_SETUP}" != "y" ]; then
		echo "[INFO] You can check the content of the folder '$DBB_MODELER_WORK' and decide to re-use this folder or not."
		exit 2
	else
		echo "[INFO] Removing the DBB Git Migration Modeler working folder '$DBB_MODELER_WORK'"
		rm -rf $DBB_MODELER_WORK
		rc=$?
		if [ $rc -ne 0 ]; then
			echo "[ERROR] Failed to remove the DBB Git Migration Modeler working folder '${DBB_MODELER_WORK}'."
			exit 8
		fi

	fi
fi

# Default environment variables
DBB_MODELER_APPCONFIG_DIR="$DBB_MODELER_WORK/work/migration-configuration"
DBB_MODELER_APPLICATION_DIR="$DBB_MODELER_WORK/repositories"
DBB_MODELER_LOGS="$DBB_MODELER_WORK/logs"
DBB_MODELER_DEFAULT_GIT_CONFIG="$DBB_MODELER_WORK/config/git-config"

# Migration Modeler MetaDataStore configuration
# Default value for the Metadatastore type - Valid values are "file" or "db2"
DBB_MODELER_METADATASTORE_TYPE="file"
# Default path for the File Metadatastore location
DBB_MODELER_FILE_METADATA_STORE_DIR="$DBB_MODELER_WORK/work/dbb-filemetadatastore"
# Default path for the DB2 Metadatastore Connection configuration file
DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE="$DBB_MODELER_HOME/config/db2Connection.conf"
# DB2 User ID to connect through the JDBC driver
DBB_MODELER_DB2_METADATASTORE_JDBC_ID="user"
# Default path for the DB2 Password file to connect through the JDBC driver
# The password file has to be created as described in:
#    https://www.ibm.com/docs/en/dbb/2.0?topic=customization-encrypting-metadata-store-passwords#dbb-db2-password-file
DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE="$DBB_MODELER_HOME/config/db2Password.txt"

# Reference to the folder containing the Applications mapping files
DBB_MODELER_APPMAPPINGS_DIR="$DBB_MODELER_WORK/config/applications-mappings"
# Reference to the repository paths mapping file
REPOSITORY_PATH_MAPPING_FILE=$DBB_MODELER_WORK/config/repositoryPathsMapping.yaml
# Reference to the type mapping file
APPLICATION_MEMBER_TYPE_MAPPING=$DBB_MODELER_WORK/config/types/types.txt
# Reference to the type configuration file to generate build configuration
TYPE_CONFIGURATIONS_FILE=$DBB_MODELER_WORK/config/types/typesConfigurations.yaml
# Input files and configuration
# APPLICATION_DATASETS=DBEHM.MIG.COBOL,DBEHM.MIG.COPY,DBEHM.MIG.BMS
APPLICATION_ARTIFACTS_HLQ=DBEHM.MIG
# Scanning options
SCAN_DATASET_MEMBERS=false
SCAN_DATASET_MEMBERS_ENCODING=IBM-1047
# Build Framework to use. Either zBuilder or zAppBuild
# Default to zBuilder
BUILD_FRAMEWORK=
# Location of zBuilder configured instance
DBB_ZBUILDER=/var/dbb/zBuilder
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
# Artifact repository naming suffix
# e.q.:
ARTIFACT_REPOSITORY_SUFFIX=zos-local

# User ID of the pipeline user
PIPELINE_USER=ADO
# Group that the User ID of the pipeline user belongs to
PIPELINE_USER_GROUP=JENKINSG
# Pipeline template for initializing git project
# Corresponding to the Templates folder name in the DBB Community repo
# Default: 1-AzureDevOps
PIPELINE_CI=

# Arrays for configuration parameters, that will the Setup script will prompt the user for
path_config_array=(DBB_MODELER_APPCONFIG_DIR DBB_MODELER_APPLICATION_DIR DBB_MODELER_LOGS DBB_MODELER_DEFAULT_GIT_CONFIG)
input_array=(DBB_MODELER_APPMAPPINGS_DIR REPOSITORY_PATH_MAPPING_FILE APPLICATION_MEMBER_TYPE_MAPPING TYPE_CONFIGURATIONS_FILE APPLICATION_ARTIFACTS_HLQ SCAN_DATASET_MEMBERS SCAN_DATASET_MEMBERS_ENCODING DBB_COMMUNITY_REPO APPLICATION_DEFAULT_BRANCH INTERACTIVE_RUN PUBLISH_ARTIFACTS ARTIFACT_REPOSITORY_SERVER_URL ARTIFACT_REPOSITORY_USER ARTIFACT_REPOSITORY_PASSWORD ARTIFACT_REPOSITORY_SUFFIX PIPELINE_USER PIPELINE_USER_GROUP)

echo
# Specify DBB Build Framework and related options
# Ask until a valid option was provided
while [ -z $BUILD_FRAMEWORK ]; do
	echo "[SETUP] Specifying the Build Framework configuration"
	read -p "Specify the Build Framework to use with DBB ("zBuilder" or "zAppBuild") [default: zBuilder]: " variable
	if [ "$variable" ]; then
		BUILD_FRAMEWORK="${variable}"
	else 
		BUILD_FRAMEWORK="zBuilder"
	fi
	if [ "${BUILD_FRAMEWORK}" != "zBuilder" ] && [ "${BUILD_FRAMEWORK}" != "zAppBuild" ]; then
		echo "[WARNING] The Build Framework can only be 'zBuilder' or 'zAppBuild'. Please provide a valid option."
		BUILD_FRAMEWORK=""
	fi
done

if [ "$BUILD_FRAMEWORK" = "zBuilder" ]; then
	read -p "Specify the location of the DBB zBuilder installation [default: ${DBB_ZBUILDER}]: " variable
	if [ "$variable" ]; then
		declare DBB_ZBUILDER="${variable}"
	fi
fi
if [ "$BUILD_FRAMEWORK" = "zAppBuild" ]; then
	read -p "Specify the location of the zAppBuild installation [default: ${DBB_ZAPPBUILD}]: " variable
	if [ "$variable" ]; then
		declare DBB_ZAPPBUILD="${variable}"
	fi
fi

echo
echo "[SETUP] Specifying DBB MetadataStore type and configuration"
read -p "Specify the type of the DBB MetadataStore ("file" or "db2") [default: ${DBB_MODELER_METADATASTORE_TYPE}]: " variable
if [ "$variable" ]; then
	declare DBB_MODELER_METADATASTORE_TYPE="${variable}"
fi

if [ "$DBB_MODELER_METADATASTORE_TYPE" = "file" ]; then
	read -p "Specify the location of the DBB File MetadataStore [default: ${DBB_MODELER_FILE_METADATA_STORE_DIR}]: " variable
	if [ "$variable" ]; then
		declare DBB_MODELER_FILE_METADATA_STORE_DIR="${variable}"
	fi
fi

if [ "$DBB_MODELER_METADATASTORE_TYPE" = "db2" ]; then
	read -p "Specify the location of the DBB Db2 MetadataStore configuration file [default: ${DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE}]: " variable
	if [ "$variable" ]; then
		declare DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE="${variable}"
	fi
	read -p "Specify the DBB Db2 MetadataStore JDBC User ID [default: ${DBB_MODELER_DB2_METADATASTORE_JDBC_ID}]: " variable
	if [ "$variable" ]; then
		declare DBB_MODELER_DB2_METADATASTORE_JDBC_ID="${variable}"
	fi
	read -p "Specify the DBB Db2 MetadataStore JDBC Password File [default: ${DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE} ]: " variable
	if [ "$variable" ]; then
		declare DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE="${variable}"
	fi
fi

# Specify input files
echo
echo "[SETUP] Specifying DBB Git Migration Modeler input configuration"
for config in ${input_array[@]}; do
	read -p "Specify input parameter $config [default: ${!config}]: " variable
	if [ "$variable" ]; then
		declare ${config}="${variable}"
	fi
done

# Ask until a valid option was provided
while [ -z $PIPELINE_CI ]; do
	echo "Specify the pipeline orchestration technology to use. See available templates at https://github.com/IBM/dbb/tree/main/Templates"
	read -p "1 for 'Azure DevOps', 2 for 'GitLab CI with distributed runner', 3 for 'GitLab CI with z/OS-native runner', 4 for 'Jenkins', 5 for 'GitHub Actions' [default: 1]: " variable
	if [ "$variable" ]; then
		PIPELINE_CI="${variable}"
	else
		PIPELINE_CI=1
	fi
	case ${PIPELINE_CI} in
	"1")
		PIPELINE_CI="AzureDevOpsPipeline"
		;;
	"2")
		PIPELINE_CI="GitlabCIPipeline-for-distributed-runner"
		;;
	"3")
		PIPELINE_CI="GitlabCIPipeline-for-zos-native-runner"
		;;
	"4")
		PIPELINE_CI="JenkinsPipeline"
		;;
	"5")

		PIPELINE_CI="GitHubActionsPipeline"
		;;
	*)
		echo "[WARNING] The pipeline orchestration technology entered does not match any of the provided options. Please provide a valid option."
		PIPELINE_CI=""
		;;
	esac
done

echo
DBB_GIT_MIGRATION_MODELER_CONFIG_FILE="DBB_GIT_MIGRATION_MODELER-$(date +%Y-%m-%d.%H%M%S).config"
FOLDER_FOUND="false"
while [ "${FOLDER_FOUND}" = "false" ]; do
	DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER="$(pwd)"
	read -p "[SETUP] Specify the folder where to store the DBB Git Migration Modeler Configuration file '$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE' (The specified folder must exist) [default: $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER]: " variable
	if [ "$variable" ]; then
		DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER="${variable}"
	fi
	if [ ! -d "${DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER}" ]; then
		echo "[ERROR] The folder '${DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER}' does not exist."
	else
		FOLDER_FOUND="true"
	fi
done

touch $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER/$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE
rc=$?
if [ $rc -eq 0 ]; then
	chtag -tc IBM-1047 $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER/$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE

	echo "# DBB Git Migration Modeler configuration settings" >$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER/$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE
	echo "# Generated at $(date)" >>$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER/$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE
	echo "" >>$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER/$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE

	echo "DBB_MODELER_HOME=${DBB_MODELER_HOME}" >>$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER/$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE
	echo "DBB_MODELER_WORK=${DBB_MODELER_WORK}" >>$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER/$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE

	echo "" >>$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER/$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE
	echo "# DBB Git Migration Modeler working folders" >>$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER/$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE
	for config in ${path_config_array[@]}; do
		echo "${config}=${!config}" >>$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER/$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE
	done

	echo "" >>$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER/$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE
	echo "# DBB Git Migration Modeler - DBB Metadatastore configuration" >>$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER/$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE
	echo "DBB_MODELER_METADATASTORE_TYPE=${DBB_MODELER_METADATASTORE_TYPE}" >>$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER/$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE
	echo "DBB_MODELER_FILE_METADATA_STORE_DIR=${DBB_MODELER_FILE_METADATA_STORE_DIR}" >>$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER/$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE
	echo "DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE=${DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE}" >>$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER/$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE
	echo "DBB_MODELER_DB2_METADATASTORE_JDBC_ID=${DBB_MODELER_DB2_METADATASTORE_JDBC_ID}" >>$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER/$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE
	echo "DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE=${DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE}" >>$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER/$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE

	echo "" >>$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER/$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE
	echo "# DBB Git Migration Modeler - Build Framework configuration" >>$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER/$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE
	echo "BUILD_FRAMEWORK=${BUILD_FRAMEWORK}" >>$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER/$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE
	echo "DBB_ZBUILDER=${DBB_ZBUILDER}" >>$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER/$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE
	echo "DBB_ZAPPBUILD=${DBB_ZAPPBUILD}" >>$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER/$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE

	echo "" >>$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER/$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE
	echo "# DBB Git Migration Modeler input files" >>$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER/$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE
	for config in ${input_array[@]}; do
		echo "${config}=${!config}" >>$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER/$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE
	done
	echo "PIPELINE_CI=${PIPELINE_CI}" >>$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER/$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE

	echo
	echo "[SETUP] DBB Git Migration Modeler configuration saved to '$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER/$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE'"
	echo
else
	rc=8
	echo "[ERROR] Could not create file '$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER/$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE'."
fi

if [ $rc -eq 0 ]; then
	echo "[SETUP] Validating environment."
	./src/scripts/utils/0-validateConfiguration.sh -e
	rc=$?
	if [ $rc -ne 0 ]; then
		echo "[ERROR] Environment check failed. Please correct the environment and run again the Setup script. Exiting."
		exit $rc
	fi
fi

if [ $rc -eq 0 ]; then
	echo "[SETUP] Validating Configuration File and finalizing Setup."
	./src/scripts/utils/0-validateConfiguration.sh -f $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER/$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE
	rc=$?
	if [ $rc -ne 0 ]; then
		echo "[ERROR] Configuration check failed. Please correct the configuration and run again the Setup script. Exiting."
		exit $rc
	fi
fi

if [ $rc -eq 0 ]; then
	echo "[SETUP] Checking the access to the DBB MetadataStore."
	$DBB_MODELER_HOME/src/scripts/CheckMetadataStore.sh -c $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER/$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE
	rc=$?
	if [ $rc -ne 0 ]; then
		echo "[ERROR] DBB MetadataStore check failed. Please correct the configuration and run again the Setup script. Exiting."
	fi
fi

if [ $rc -eq 0 ]; then
	echo "*************************************************************************************************************"
	echo
	echo "Congratulations! The validation of the DBB Git Migration Modeler Setup was successful!"
	echo
	echo "********************************************* SUGGESTED ACTIONS *********************************************"
	echo "Tailor the following input files prior to using the DBB Git Migration Modeler:"
	echo "  - Applications Mapping file(s) located in $DBB_MODELER_APPMAPPINGS_DIR"
	echo "  - $REPOSITORY_PATH_MAPPING_FILE"
	echo "  - $APPLICATION_MEMBER_TYPE_MAPPING (optional)"
	echo "  - $TYPE_CONFIGURATIONS_FILE (optional)"
	echo
	echo "Once tailored, run the following command:"
	echo "'$DBB_MODELER_HOME/src/scripts/Migration-Modeler-Start.sh -c $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE_FOLDER/$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE'"
fi

exit $rc
