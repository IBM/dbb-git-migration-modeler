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
  	
    #### Application Extraction step
    CMD="$DBB_MODELER_HOME/src/scripts/2-runMigrations.sh -c $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE \
          -d $APPLICATION_DATASETS \
          --applicationsMapping $APPLICATION_MAPPING_FILE \
          --repositoryPathsMapping $REPOSITORY_PATH_MAPPING_FILE \
          --types $APPLICATION_MEMBER_TYPE_MAPPING \
          -oc $DBB_MODELER_APPCONFIG_DIR \
          -oa $DBB_MODELER_APPLICATION_DIR \
          -l $DBB_MODELER_LOGS/1-extractApplications.log
      "
    echo "${CMD}"
    $CMD
    ## The following command can be used when datasets contain mixed types of artifacts, the use of the scanDatasetMembers option enables the DBB Scanner to understand the type of artifacts and route them to the right subfolder in USS
    #$DBB_MODELER_HOME/src/scripts/1-extractApplications.sh -d DBEHM.MIG.MIXED,DBEHM.MIG.BMS --applicationsMapping $DBB_MODELER_WORK/applicationsMapping.yaml --repositoryPathsMapping $DBB_MODELER_WORK/repositoryPathsMapping.yaml --types $DBB_MODELER_WORK/types.txt -oc $DBB_MODELER_APPCONFIGS -oa $DBB_MODELER_APPLICATIONS -l $DBB_MODELER_LOGS/1-extractApplications.log -scanDatasetMembers -scanEncoding IBM-1047
    ## The following command can be used when wildcards are used to list the datasets that should be scanned.
    #$DBB_MODELER_HOME/src/scripts/1-extractApplications.sh -d GITLAB.CATMAN.**.CO*,DBEHM.MIG.COBOL,DBEHM.MIG.COPY --applicationsMapping $DBB_MODELER_WORK/applicationsMapping-CATMAN.yaml --repositoryPathsMapping $DBB_MODELER_WORK/repositoryPathsMapping.yaml --types $DBB_MODELER_WORK/types.txt -oc $DBB_MODELER_APPCONFIGS -oa $DBB_MODELER_APPLICATIONS

    
  fi
fi

if [ $rc -eq 0 ]; then
  #### Migration execution step
  echo ""
  echo "[PHASE] Execute Migrations"
  echo "Do you want to execute the DBB migration scripts in $DBB_MODELER_APPCONFIG_DIR (Y/n) :"
  read variable

  if [[ $variable =~ ^[Yy]$ ]]; then
    $DBB_MODELER_HOME/src/scripts/2-runMigrations.sh -c $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE
  fi
fi

if [ $rc -eq 0 ]; then
  #### Classification step
  echo ""
  echo "[PHASE] Execute Dependency Assessment and Classification"
  echo "Do you want to perform dependency assessment and classification process (Y/n) :"
  read variable
  if [[ $variable =~ ^[Yy]$ ]]; then
    $DBB_MODELER_HOME/src/scripts/3-classify.sh -c $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE
  fi
fi

if [ $rc -eq 0 ]; then
  #### Property Generation step
  echo ""
  echo "[PHASE] Generate Build Configuration"
  echo "Do you want to generate the zAppBuild Build configurations for the applications (Y/n) :"
  read variable
  if [[ $variable =~ ^[Yy]$ ]]; then
    $DBB_MODELER_HOME/src/scripts/4-generateProperties.sh --typesConfigurations $DBB_MODELER_WORK/typesConfigurations.yaml -z /u/mdalbin/dbb-zappbuild
  fi
fi
