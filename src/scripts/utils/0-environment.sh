#!/bin/env bash
#*******************************************************************************
# Licensed Materials - Property of IBM
# (c) Copyright IBM Corporation 2018, 2019. All Rights Reserved.
#
# Note to U.S. Government Users Restricted Rights:
# Use, duplication or disclosure restricted by GSA ADP Schedule
# Contract with IBM Corp.
#*******************************************************************************


# Internal variables
DBB_GIT_MIGRATION_MODELER_CONFIG_FILE=
rc=0

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
        ERRMSG=" [ERROR] DBB Git Migration Modeler Configuration file required. rc="$rc
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
    ERRMSG=" [ERROR] Argument to specify DBB Git Migration Modeler File (-c) is required. rc="$rc
    echo $ERRMSG
  fi

  if [ ! -f "${DBB_GIT_MIGRATION_MODELER_CONFIG_FILE}" ]; then
    rc=8
    ERRMSG=" [ERROR] DBB Git Migration Modeler File not found. rc="$rc
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
  MSG=" [INFO] Loading DBB Git Migration Modeler config file $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE. "
  echo $MSG 
  source $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE
fi
