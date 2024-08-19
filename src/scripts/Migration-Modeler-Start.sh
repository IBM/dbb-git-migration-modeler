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


# Initialize Migration Modeler
dir=$(dirname "$0")
. $dir/utils/0-environment.sh "$@"

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
    CMD="$DBB_MODELER_HOME/src/scripts/utils/1-extractApplications.sh -c $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE "
    echo "${CMD}"
    $CMD
    
    # Note - create multiple Migration Modeler Configuration files, if you want to run the extraction step with different datasets configurations.
    
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
    $DBB_MODELER_HOME/src/scripts/utils/2-runMigrations.sh -c $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE
  fi
fi

if [ $rc -eq 0 ]; then
  #### Classification step
  echo ""
  echo "[PHASE] Execute Dependency Assessment and Classification"
  echo "Do you want to perform dependency assessment and classification process (Y/n) :"
  read variable
  if [[ $variable =~ ^[Yy]$ ]]; then
    $DBB_MODELER_HOME/src/scripts/utils/3-classify.sh -c $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE
  fi
fi

if [ $rc -eq 0 ]; then
  #### Property Generation step
  echo ""
  echo "[PHASE] Generate Build Configuration"
  echo "Do you want to generate the zAppBuild Build configurations for the applications (Y/n) :"
  read variable
  if [[ $variable =~ ^[Yy]$ ]]; then
    $DBB_MODELER_HOME/src/scripts/utils/4-generateProperties.sh -c $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE
  fi
fi
