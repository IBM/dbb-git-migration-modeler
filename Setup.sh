#!/bin/env bash
#*******************************************************************************
# Licensed Materials - Property of IBM
# (c) Copyright IBM Corporation 2018, 2025. All Rights Reserved.
#
# Note to U.S. Government Users Restricted Rights:
# Use, duplication or disclosure restricted by GSA ADP Schedule
# Contract with IBM Corp.
#*******************************************************************************
# DBB Git Migration Modeler Configuration

# Default is the root of the Git Repo
DBB_MODELER_HOME=$(cd "$(dirname "$0")" && pwd)

rc=0

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
CLASSPATH="$JAR_FILE:$CLASSPATH"
if [ -d "$LIB_DIR" ]; then
	for jar in "$LIB_DIR"/*.jar; do
		CLASSPATH="$CLASSPATH:$jar"
	done
fi

# Add DBB libraries if DBB_HOME is set
if [ -n "$DBB_HOME" ]; then
	CLASSPATH="$CLASSPATH:$DBB_HOME/lib/*"
fi

# Run the Setup Java class
java -Dfile.encoding=COMPAT \
     -Ddbb.modeler.home="$DBB_MODELER_HOME" \
     -cp "$CLASSPATH" \
     com.ibm.dbb.migration.Setup
rc=$?

exit $rc
