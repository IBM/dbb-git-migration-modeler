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
  echo "                                                                                                            "
  echo " DBB Git Migration Modeler                                                                                  "
  echo "                                                                                                            "
  echo " Script:      Setup.sh                                                                                      "
  echo "                                                                                                            "
  echo " Description: This script is setting up a work folder including an configuration file that is used          "
  echo "              by the DBB Git Migration Modeler process.                                                     "
  echo "                                                                                                            "
  echo "              The user will be prompted for several configuration parameters that are saved                 "
  echo "              into a configuration file within the work folder.                                             "
  echo "              The configuration file is passed to the DBB Git Migration Modeler as a required                  "
  echo "              input parameter.                                                                              "
  echo "                                                                                                            "
  echo "              For more information please refer to:    https://github.com/IBM/dbb-git-migration-modeler     "
  echo "                                                                                                            "
}
#
Prolog

# Default is the root of the Git Repo
DBB_MODELER_HOME=$(cd "$(dirname "$0")" && pwd)

# Configure DBB Migration Modeler Home
echo "[INFO] Configure DBB Migration Modeler environment variables."

echo "Please specify the DBB Migration Modeler home DBB_MODELER_HOME :"
echo "Leave blank to default to $DBB_MODELER_HOME"
read variable
if [ "$variable" ]; then
    DBB_MODELER_HOME="${variable}"
fi
echo ""

# Configure DBB Migration Modeler Work Directory
DBB_MODELER_WORK="${DBB_MODELER_HOME}-work"
echo "Please specify the DBB Migration Modeler work directory DBB_MODELER_WORK :"
echo "Leave blank to default to $DBB_MODELER_WORK"
read variable
if [ "$variable" ]; then
    DBB_MODELER_WORK="${variable}"
fi
echo ""

# Default environment variables
DBB_MODELER_APPCONFIG_DIR="$DBB_MODELER_WORK/work-configs"
DBB_MODELER_APPLICATION_DIR="$DBB_MODELER_WORK/work-applications"
DBB_MODELER_LOGS="$DBB_MODELER_WORK/work-logs"
DBB_MODELER_METADATA_STORE_DIR="$DBB_MODELER_WORK/work-metadatastore"

# Internal variables
DBB_MODELER_SAMPLE_CONFIG="$DBB_MODELER_HOME/samples"

# Migration Modeler Configuration files

# Input files and configuration
APPLICATION_DATASETS=DBEHM.MIG.COBOL,DBEHM.MIG.COPY,DBEHM.MIG.BMS
# Reference to the configured application mapping file
APPLICATION_MAPPING_FILE=$DBB_MODELER_WORK/applicationsMapping.yaml
# Reference to the repository paths mapping file
REPOSITORY_PATH_MAPPING_FILE=$DBB_MODELER_WORK/repositoryPathsMapping.yaml
# Reference to the type mapping file
APPLICATION_MEMBER_TYPE_MAPPING=$DBB_MODELER_WORK/types.txt
# Reference to the type configuration file to generate build configuration
TYPE_CONFIGURATIONS_FILE=$DBB_MODELER_WORK/typesConfigurations.yaml
# Scanning options 
SCAN_DATASET_MEMBERS=false
SCAN_DATASET_MEMBERS_ENCODING=IBM-1047
# Reference to zAppBuild
DBB_ZAPPBUILD=/var/dbb/dbb-zappbuild_300
# Reference to default .gitattributes file
DBB_MODELER_DEFAULT_GIT_CONFIG="$DBB_MODELER_WORK/git-config"


# Arrays for configuration parameters, that will the Setup script will prompt the user for
path_config_array=(DBB_MODELER_APPCONFIG_DIR DBB_MODELER_APPLICATION_DIR DBB_MODELER_LOGS DBB_MODELER_METADATA_STORE_DIR DBB_MODELER_DEFAULT_GIT_CONFIG)
input_array=(APPLICATION_DATASETS APPLICATION_MAPPING_FILE REPOSITORY_PATH_MAPPING_FILE APPLICATION_MEMBER_TYPE_MAPPING SCAN_DATASET_MEMBERS SCAN_DATASET_MEMBERS_ENCODING TYPE_CONFIGURATIONS_FILE DBB_ZAPPBUILD)

# Prompt for configuration parameters
for config in ${path_config_array[@]}; do
    echo "Please configure DBB Migration Modeler configuration parameter $config :"
    echo "Leave blank to default to ${!config}"
    read variable
    if [ "$variable" ]; then
        declare ${config}="${variable}"
    fi
done

# Create work dir
echo "[INFO] Create DBB Migration Modeler work directory $DBB_MODELER_WORK"
echo "Do you want to create the directory $DBB_MODELER_WORK (Y/n) :"
read variable

if [[ $variable =~ ^[Yy]$ ]]; then
    if [ -d "${DBB_MODELER_WORK}" ]; then
        rc=4
        ERRMSG="[ERROR] Directory '$DBB_MODELER_WORK' already exists. rc="$rc
        echo $ERRMSG
    else
        mkdir -p $DBB_MODELER_WORK
        rc=$?
    fi
fi

# Copy samples
echo "Do you want to copy sample DBB Migration Modeler configuration files to the working directory [$DBB_MODELER_WORK] (Y/n) :"
read variable

if [[ $variable =~ ^[Yy]$ ]]; then
    cp $DBB_MODELER_HOME/samples/*.* $DBB_MODELER_WORK/
    rc=$?
fi

# Specify input files
echo "[INFO] Specify DBB Migration Modeler input configuration"
for config in ${input_array[@]}; do
    echo "Please configure DBB Migration Modeler input parameter $config :"
    echo "Leave blank to default to ${!config}"
    read variable
    if [ "$variable" ]; then
        declare ${config}="${variable}"
    fi
done

# Save DBB Git Migration Modeler Configuration

echo "[INFO] Save DBB Git Migration Modeler Configuration"

echo "# DBB Git Migration Modeler Configuration Settings " >$DBB_MODELER_WORK/DBB_GIT_MIGRATION_MODELER.config
echo "# Generated at $(date)" >>$DBB_MODELER_WORK/DBB_GIT_MIGRATION_MODELER.config
echo "" >> $DBB_MODELER_WORK/DBB_GIT_MIGRATION_MODELER.config

echo "DBB_MODELER_HOME=${DBB_MODELER_HOME} " >>$DBB_MODELER_WORK/DBB_GIT_MIGRATION_MODELER.config
echo "DBB_MODELER_WORK=${DBB_MODELER_WORK} " >>$DBB_MODELER_WORK/DBB_GIT_MIGRATION_MODELER.config

echo "" >> $DBB_MODELER_WORK/DBB_GIT_MIGRATION_MODELER.config
echo "# DBB Git Migration Modeler Working Folders " >>$DBB_MODELER_WORK/DBB_GIT_MIGRATION_MODELER.config
for config in ${path_config_array[@]}; do
    echo "${config}=${!config} " >>$DBB_MODELER_WORK/DBB_GIT_MIGRATION_MODELER.config
done

echo "" >> $DBB_MODELER_WORK/DBB_GIT_MIGRATION_MODELER.config
echo "# DBB Git Migration Modeler Input files" >>$DBB_MODELER_WORK/DBB_GIT_MIGRATION_MODELER.config

for config in ${input_array[@]}; do
    echo "${config}=${!config} " >>$DBB_MODELER_WORK/DBB_GIT_MIGRATION_MODELER.config
done

echo "Saved DBB Git Migration Modeler Configuration to $DBB_MODELER_WORK/DBB_GIT_MIGRATION_MODELER.config"
echo "This file will be imported by the DBB Git Migration Modeler Process"
echo ""
echo "Tailor the following input files for the DBB Git Migration Modeler"
echo "  - $APPLICATION_MAPPING_FILE "
echo "  - $REPOSITORY_PATH_MAPPING_FILE "
echo "  - $APPLICATION_MEMBER_TYPE_MAPPING (optional) "
echo "  - $TYPE_CONFIGURATIONS_FILE (optional) "
echo "before you start DBB Git Migration Modeler process by running"
echo " '$DBB_MODELER_HOME/src/scripts/Migration-Modeler-Start.sh -c $DBB_MODELER_WORK/DBB_GIT_MIGRATION_MODELER.config'"