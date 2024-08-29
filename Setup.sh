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

# internal variables
rc=0          # return code management
CONFIG_DIR="" # Path to store the DBB_GIT_MIGRATION_MODELER.config

# Default is the root of the Git Repo
DBB_MODELER_HOME=$(cd "$(dirname "$0")" && pwd)

# Configure DBB Migration Modeler Home
echo "[INFO] Configure DBB Git Migration Modeler environment variables."
read -p "Specify the DBB Git Migration Modeler Home [default: $DBB_MODELER_HOME]: " variable

if [ "$variable" ]; then
    DBB_MODELER_HOME="${variable}"
fi

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
DBB_MODELER_METADATA_STORE_DIR="$DBB_MODELER_WORK/dbb-metadatastore"

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
# Reference to default .gitattributes file
DBB_MODELER_DEFAULT_GIT_CONFIG="$DBB_MODELER_WORK/git-config"
# Reference to zAppBuild
DBB_ZAPPBUILD=/var/dbb/dbb-zappbuild_300
# Reference to DBB Community Repo
DBB_COMMUNITY_REPO=/var/dbb/extensions/dbb20

# Arrays for configuration parameters, that will the Setup script will prompt the user for
path_config_array=(DBB_MODELER_APPCONFIG_DIR DBB_MODELER_APPLICATION_DIR DBB_MODELER_LOGS DBB_MODELER_METADATA_STORE_DIR DBB_MODELER_DEFAULT_GIT_CONFIG)
input_array=(APPLICATION_DATASETS APPLICATION_MAPPING_FILE REPOSITORY_PATH_MAPPING_FILE APPLICATION_MEMBER_TYPE_MAPPING SCAN_DATASET_MEMBERS SCAN_DATASET_MEMBERS_ENCODING TYPE_CONFIGURATIONS_FILE DBB_MODELER_DEFAULT_GIT_CONFIG DBB_ZAPPBUILD DBB_COMMUNITY_REPO)

# Prompt for configuration parameters
for config in ${path_config_array[@]}; do
    read -p "Specify configuration parameter $config [default: ${!config}]: " variable
    if [ "$variable" ]; then
        declare ${config}="${variable}"
    fi
done

# Create work dir
echo "[INFO] Create DBB Git Migration Modeler work directory $DBB_MODELER_WORK and copy DBB Migration Modeler configuration files to it"
read -p "Do you want to create the directory $DBB_MODELER_WORK and copy the DBB Git Migration Modeler configuration files to it (Y/n): " variable

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
        
           cp $DBB_MODELER_HOME/samples/git-config/.* $DBB_MODELER_DEFAULT_GIT_CONFIG/
           rc=$?
        fi
    fi
fi

if [ $rc -eq 0 ]; then

    # Specify input files
    echo "[INFO] Specify DBB Git Migration Modeler input configuration"
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
    echo "[INFO] Save DBB Git Migration Modeler Configuration"
    read -p "Specify directory where the DBB_GIT_MIGRATION_MODELER.config file should be saved [default: $DBB_MODELER_WORK]: " variable
    if [ "$variable" ]; then
        CONFIG_DIR="${variable}"
    fi

    #Check that CONFIG DIR exists
    if [ ! -d $CONFIG_DIR ]; then
        rc=4
        ERRMSG="[ERROR] Specified directory '$CONFIG_DIR' to save DBB Git Migration Modeler Configuration does not exist. rc="$rc
        echo $ERRMSG
    fi
fi

if [ $rc -eq 0 ]; then

    CONFIG_FILE="${CONFIG_DIR}/DBB_GIT_MIGRATION_MODELER.config"

    echo "# DBB Git Migration Modeler Configuration Settings " >$CONFIG_FILE
    echo "# Generated at $(date)" >>$CONFIG_FILE
    echo "" >>$CONFIG_FILE

    echo "DBB_MODELER_HOME=${DBB_MODELER_HOME} " >>$CONFIG_FILE
    echo "DBB_MODELER_WORK=${DBB_MODELER_WORK} " >>$CONFIG_FILE

    echo "" >>$CONFIG_FILE
    echo "# DBB Git Migration Modeler Working Folders " >>$CONFIG_FILE
    for config in ${path_config_array[@]}; do
        echo "${config}=${!config} " >>$CONFIG_FILE
    done

    echo "" >>$CONFIG_FILE
    echo "# DBB Git Migration Modeler Input files" >>$CONFIG_FILE

    for config in ${input_array[@]}; do
        echo "${config}=${!config} " >>$CONFIG_FILE
    done

    echo "Saved DBB Git Migration Modeler Configuration to $CONFIG_FILE"
    echo "This file will be imported by the DBB Git Migration Modeler process"
    echo ""
    echo "Tailor the following input files for the DBB Git Migration Modeler"
    echo "  - $APPLICATION_MAPPING_FILE "
    echo "  - $REPOSITORY_PATH_MAPPING_FILE "
    echo "  - $APPLICATION_MEMBER_TYPE_MAPPING (optional) "
    echo "  - $TYPE_CONFIGURATIONS_FILE (optional) "
    echo "before you start DBB Git Migration Modeler process by running"
    echo " '$DBB_MODELER_HOME/src/scripts/Migration-Modeler-Start.sh -c $CONFIG_FILE'"

fi
