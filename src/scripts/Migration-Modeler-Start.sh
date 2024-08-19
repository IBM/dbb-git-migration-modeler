#!/bin/env bash
#*******************************************************************************
# Licensed Materials - Property of IBM
# (c) Copyright IBM Corporation 2018, 2019. All Rights Reserved.
#
# Note to U.S. Government Users Restricted Rights:
# Use, duplication or disclosure restricted by GSA ADP Schedule
# Contract with IBM Corp.
#*******************************************************************************

Prolog() {
  echo "                                                                                                            "
  echo " DBB Git Migration Modeler                                                                                  "
  echo " Release:     $MigrationModelerRelease                                                                      "
  echo "                                                                                                            "
  echo " Script:      Migration-Modeler-Start.sh                                                                    "
  echo "                                                                                                            "
  echo " Description: The purpose of this script is to facilitate the execution of the 4-step process supported     "
  echo "              by the DBB Git Migration Modeler.                                                             "
  echo "              For more information please refer to:    https://github.com/IBM/dbb-git-migration-modeler     "
  echo "                                                                                                            "
}
#
Prolog

# Internal variables
DBB_GIT_MIGRATION_MODELER_CONFIG_FILE=
rc=0
PGM="Migration-Modeler-Start.sh"

# Get Options
if [ $rc -eq 0 ]; then
  while getopts "h:c:" opt; do
    case $opt in
    h)
      Prolog
      ;;
    c)
      argument="$OPTARG"
      nextchar="$(expr substr $argument 1 1)"
      if [ -z "$argument" ] || [ "$nextchar" = "-" ]; then
        rc=4
        ERRMSG=$PGM": [WARNING] Git Repository URL is required. rc="$rc
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
    ERRMSG=$PGM": [ERROR] Argument to specify DBB Git Migration Modeler File (-c) is required. rc="$rc
    echo $ERRMSG
  fi

  if [ ! -f "${DBB_GIT_MIGRATION_MODELER_CONFIG_FILE}" ]; then
    rc=8
    ERRMSG=$PGM": [ERROR] DBB Git Migration Modeler File not found. rc="$rc
    echo $ERRMSG
  fi
}

# Call validate Options
if [ $rc -eq 0 ]; then
  validateOptions
fi
#

# Load DBB Git Migration Modeler config
if [ $rc -eq 0 ]; then
  source $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE
fi

# Load DBB Git Migration Modeler Utilities
if [ $rc -eq 0 ]; then
  if [ -f "$DBB_MODELER_HOME/src/sripts/utils/migrationModelerUtilities.sh" ]; then
    source "$DBB_MODELER_HOME/src/sripts/utils/migrationModelerUtilities.sh"
  else 
    rc=8
    ERRMSG=$PGM": [ERROR] DBB Git Migration Modeler File Utils not found. rc="$rc
    echo $ERRMSG
  fi
fi

if [ $rc -eq 0 ]; then
  echo ""
  echo "[PHASE] Cleanup working directories"
  echo "Do you want to clean the working directory $DBB_MODELER_WORK (Y/n) :"
  read variable
  
  if [[ $variable =~ ^[Yy]$ ]]; then

    #### Cleanup output directories
    if [ -d $DBB_MODELER_APPCONFIG_DIR ]; then
      echo "[INFO] Removing '${DBB_MODELER_APPCONFIG_DIR}' folder."
      rm -rf $DBB_MODELER_APPCONFIG_DIR
    fi
    if [ -d $DBB_MODELER_APPLICATION_DIR ]; then
      echo "[INFO] Removing '${DBB_MODELER_APPLICATION_DIR}' folder."
      rm -rf $DBB_MODELER_APPLICATION_DIR
    fi
    if [ -d $DBB_MODELER_LOGS ]; then
      echo "[INFO] Removing '${DBB_MODELER_LOGS}' folder."
      rm -rf $DBB_MODELER_LOGS
    fi

    #### Create work directories
    if [ ! -d $DBB_MODELER_LOGS ]; then
      mkdir -p $DBB_MODELER_LOGS
    fi
  fi
fi

if [ $rc -eq 0 ]; then
  echo ""
  echo "[PHASE] Extract applications from $APPLICATION_DATASETS based on application mappings in $APPLICATION_MAPPING_FILE"
  echo "Do you want run the application extraction (Y/n) :"
  read variable

  if [[ $variable =~ ^[Yy]$ ]]; then
  	callExtractApplications
  fi
fi

if [ $rc -eq 0 ]; then
  #### Migration execution step
  echo ""
  echo "[PHASE] Execute Migrations"
  echo "Do you want to execute the DBB migration scripts in $DBB_MODELER_APPCONFIG_DIR (Y/n) :"
  read variable

  if [[ $variable =~ ^[Yy]$ ]]; then
    callRunMigrations
  fi
fi

if [ $rc -eq 0 ]; then
  #### Classification step
  echo ""
  echo "[PHASE] Execute Dependency Assessment and Classification"
  echo "Do you want to perform dependency assessment and classification process (Y/n) :"
  read variable
  if [[ $variable =~ ^[Yy]$ ]]; then
    callClassificationAssessment
  fi
fi

if [ $rc -eq 0 ]; then
  #### Property Generation step
  echo ""
  echo "[PHASE] Generate Build Configuration"
  echo "Do you want to generate the zAppBuild Build configurations for the applications (Y/n) :"
  read variable
  if [[ $variable =~ ^[Yy]$ ]]; then
    callPropertyGeneration
  fi
fi
