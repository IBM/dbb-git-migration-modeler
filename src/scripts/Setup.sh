#!/bin/bash
#*******************************************************************************
# Licensed Materials - Property of IBM
# (c) Copyright IBM Corporation 2018, 2024. All Rights Reserved.
#
# Note to U.S. Government Users Restricted Rights:
# Use, duplication or disclosure restricted by GSA ADP Schedule
# Contract with IBM Corp.
#*******************************************************************************
# DBB Git Migration Modeler Configuration

DBB_MODELER_HOME=$(cd "$(dirname "$0")" && pwd)

# Configure DBB Migration Modeler Home
echo "[INFO] Configure DBB Migration Modeler environment variables."

echo "  Please specify the DBB Migration Modeler home DBB_MODELER_HOME :"
echo "  Leave blank to default to $DBB_MODELER_HOME"
read variable
if [ "$variable" ]; then
    DBB_MODELER_HOME="${variable}"
fi
echo ""

# Configure DBB Migration Modeler Work Directory
DBB_MODELER_WORK="${DBB_MODELER_HOME}-work"
echo "  Please specify the DBB Migration Modeler work directory DBB_MODELER_WORK :"
echo "  Leave blank to default to $DBB_MODELER_WORK"
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
DBB_MODELER_DEFAULT_GIT_CONFIG="$DBB_MODELER_WORK/git-config"

# Internal variables
DBB_MODELER_SAMPLE_CONFIG="$DBB_MODELER_HOME/samples"

# Migration Modeler Configuration files

# Input files
APPLICATION_DATASETS=DBEHM.MIG.COBOL,DBEHM.MIG.COPY,DBEHM.MIG.BMS
# Reference to the configured application mapping file
APPLICATION_MAPPING_FILE=$DBB_MODELER_WORK/applicationsMapping.yaml
# Reference to the repository paths mapping file
REPOSITORY_PATH_MAPPING_FILE=$DBB_MODELER_WORK/repositoryPathsMapping.yaml
# Reference to the type mapping file
APPLICATION_MEMBER_TYPE_MAPPING=$DBB_MODELER_WORK/types.txt
# Reference to the type configuration file to generate build configuration
TYPE_CONFIGURATIONS_FILE=$DBB_MODELER_WORK/typesConfigurations.yaml

# Arrays for configuration parameters, that will the Setup script will prompt the user for
config_array=(DBB_MODELER_APPCONFIG_DIR DBB_MODELER_APPLICATION_DIR DBB_MODELER_METADATA_STORE_DIR DBB_MODELER_DEFAULT_GIT_CONFIG)
input_array=(APPLICATION_DATASETS APPLICATION_MAPPING_FILE REPOSITORY_PATH_MAPPING_FILE APPLICATION_MEMBER_TYPE_MAPPING TYPE_CONFIGURATIONS_FILE)

# Prompt for configuration parameters 
for config in ${config_array[@]}; do
    echo "  Please configure DBB Migration Modeler configuration parameter $config :"
    echo "  Leave blank to default to ${!config}"
    read variable
    if [ "$variable" ]; then
        declare ${config}="${variable}"
    fi
done

# Create work dir
echo "  [INFO] Create DBB Migration Modeler work directory $DBB_MODELER_WORK"
echo "  Do you want to create the directory $DBB_MODELER_WORK (Y/n) :"
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
echo "  Do you want to copy sample DBB Migration Modeler configuration files to the working directory [$DBB_MODELER_WORK] (Y/n) :"
read variable

if [[ $variable =~ ^[Yy]$ ]]; then
        cp $DBB_MODELER_HOME/samples/*.* $DBB_MODELER_WORK/
        rc=$?
fi

# Specify input files
echo "[INFO] Specify DBB Migration Modeler input files"
for config in ${input_array[@]}; do
    echo "  Please configure DBB Migration Modeler input parameter $config :"
    echo "  Leave blank to default to ${!config}"
    read variable
    if [ "$variable" ]; then
        declare ${config}="${variable}"
    fi
done

# debugging
for config in ${config_array[@]}; do
    echo "[INFO] ${config}=${!config} "
done
for config in ${input_array[@]}; do
    echo "[INFO] ${config}=${!config} "
done

