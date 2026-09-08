#!/bin/env bash
#*******************************************************************************
# Licensed Materials - Property of IBM
# (c) Copyright IBM Corporation 2018, 2025. All Rights Reserved.
#
# Note to U.S. Government Users Restricted Rights:
# Use, duplication or disclosure restricted by GSA ADP Schedule
# Contract with IBM Corp.
#*******************************************************************************

# Default is the root of the Git Repo
DBB_MODELER_HOME=$(cd "$(dirname "$0")" && pwd)

rc=0

# Get Options
while getopts "c:a:" opt; do
	case $opt in
	c)
		argument="$OPTARG"
		nextchar="$(expr substr $argument 1 1)"
		if [ -z "$argument" ] || [ "$nextchar" = "-" ]; then
			rc=4
			echo "[ERROR] DBB Git Migration Modeler Configuration file required. rc=$rc"
			break
		fi
		DBB_GIT_MIGRATION_MODELER_CONFIG_FILE="$argument"
		;;
	a)
		argument="$OPTARG"
		nextchar="$(expr substr $argument 1 1)"
		if [ -z "$argument" ] || [ "$nextchar" = "-" ]; then
			rc=4
			echo "[ERROR] Comma-separated Applications list required. rc=$rc"
			break
		fi
		APPLICATION_FILTER="-a $argument"
		;;
	esac
done

# Validate options
if [ $rc -eq 0 ]; then
	if [ -z "${DBB_GIT_MIGRATION_MODELER_CONFIG_FILE}" ]; then
		rc=8
		echo "[ERROR] Argument to specify DBB Git Migration Modeler configuration file (-c) is required. rc=$rc"
	elif [ ! -f "${DBB_GIT_MIGRATION_MODELER_CONFIG_FILE}" ]; then
		rc=8
		echo "[ERROR] DBB Git Migration Modeler configuration file not found: '${DBB_GIT_MIGRATION_MODELER_CONFIG_FILE}'. rc=$rc"
	fi
fi

if [ $rc -ne 0 ]; then
	exit $rc
fi

# Set up Java classpath
MODELER_VERSION=$(grep '^Migration-Modeler-release=' "$DBB_MODELER_HOME/release.properties" | cut -d'=' -f2)
JAR_FILE="$DBB_MODELER_HOME/build/libs/dbb-git-migration-modeler-${MODELER_VERSION}.jar"
LIB_DIR="$DBB_MODELER_HOME/build/libs/lib"

if [ ! -f "$JAR_FILE" ]; then
	rc=8
	echo "[ERROR] JAR file not found: $JAR_FILE. Please run 'gradlew build' first. rc=$rc"
	exit $rc
fi

# Build classpath with all dependencies
CLASSPATH="$JAR_FILE"
if [ -d "$LIB_DIR" ]; then
	for jar in "$LIB_DIR"/*.jar; do
		CLASSPATH="$CLASSPATH:$jar"
	done
fi

# Add DBB libraries if DBB_HOME is set
if [ -n "$DBB_HOME" ]; then
	CLASSPATH="$CLASSPATH:$DBB_HOME/lib/*"
fi

echo $CLASSPATH

# Run the MigrationOrchestrator Java class
java -Ddbb.modeler.home="$DBB_MODELER_HOME" \
     -cp "$CLASSPATH" \
     com.ibm.dbb.migration.MigrationOrchestrator \
     -c "$DBB_GIT_MIGRATION_MODELER_CONFIG_FILE" \
     $APPLICATION_FILTER
rc=$?

exit $rc
