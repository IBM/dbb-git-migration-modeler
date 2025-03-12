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
CURRENT_DBB_TOOLKIT_VERSION=
REQUIRED_DBB_TOOLKIT_VERSION=
rc=0

OPTIND=1
# Get Options
if [ $rc -eq 0 ]; then
	while getopts "c:v:" opt; do
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
		echo $ERRMSG
	fi
	
	if [ ! -f "${DBB_GIT_MIGRATION_MODELER_CONFIG_FILE}" ]; then
		rc=8
		ERRMSG="[ERROR] DBB Git Migration Modeler configuration file not found. rc="$rc
		echo $ERRMSG
	fi
}

# Validate Environment Configuration
validateConfig() {
	if [ "$DBB_HOME" = "" ]; then
		rc=8
		ERRMSG="[ERROR] Environment variable DBB_HOME is not set. rc="$rc
		echo $ERRMSG
	fi
}

# Validate DBB Toolkit Version
validateDBBTookitVersion() {
	CURRENT_DBB_TOOLKIT_VERSION=`$DBB_HOME/bin/dbb --version | grep "Dependency Based Build version" | awk -F' ' '{print $5}'`
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

# Call Validate Options
if [ $rc -eq 0 ]; then
 	validateOptions
fi

# Call Validate Config
if [ $rc -eq 0 ]; then
 	validateConfig
fi

# Call Validate DBB Toolkit Version
if [ $rc -eq 0 ]; then
	if [ -n "${REQUIRED_DBB_TOOLKIT_VERSION}" ]; then
 		validateDBBTookitVersion
 	fi
fi

# Load DBB Git Migration Modeler Config file
if [ $rc -eq 0 ]; then
	if [ -z "${CURRENT_DBB_TOOLKIT_VERSION}" ]; then
		. $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE
	fi
else
	if [ -z "${CURRENT_DBB_TOOLKIT_VERSION}" ]; then
		echo "[ERROR] Environment configuration and validation failed. Exiting."
	else 
		echo $CURRENT_DBB_TOOLKIT_VERSION
	fi
	exit $rc
fi
