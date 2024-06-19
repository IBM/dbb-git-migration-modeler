#!/bin/sh
#*******************************************************************************
# Licensed Materials - Property of IBM
# (c) Copyright IBM Corporation 2018, 2024. All Rights Reserved.
#
# Note to U.S. Government Users Restricted Rights:
# Use, duplication or disclosure restricted by GSA ADP Schedule
# Contract with IBM Corp. 
#*******************************************************************************

if [  "$DBB_HOME" = "" ]
then
	echo "[ERROR] Environment variable DBB_HOME is not set. Exiting."
else
	# Environment variables setup
	dir=$(dirname "$0")
	. $dir/0-environment.sh

	# Build Metadatastore
 	if [ -d $DBB_MODELER_METADATA_STORE ] 
	then
		rm -rf $DBB_MODELER_METADATA_STORE
	fi

	if [ ! -d $DBB_MODELER_METADATA_STORE ] 
	then
		mkdir -p $DBB_MODELER_METADATA_STORE
	fi

	# Scan files
	cd $DBB_MODELER_APPLICATIONS
	for applicationDir in `ls | grep -v dbb-zappbuild`
	do
		echo "*******************************************************************"
		echo "Scan application directory $DBB_MODELER_APPLICATIONS/$applicationDir"
		echo "*******************************************************************"
		CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/src/groovy/scanApplication.groovy \
			-w $DBB_MODELER_APPLICATIONS \
			-a $applicationDir \
			-m $DBB_MODELER_METADATA_STORE \
			-l $DBB_MODELER_LOGS/3-$applicationDir-scan.log"	
		$CMD "$@"
	done

	cd $DBB_MODELER_APPLICATIONS
	for applicationDir in `ls | grep -v dbb-zappbuild`
	do
		echo "*******************************************************************"
		echo "Assess Include files & Programs usage for $applicationDir"
		echo "*******************************************************************"
		CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/src/groovy/assessUsage.groovy \
			--workspace $DBB_MODELER_APPLICATIONS \
			--configurations $DBB_MODELER_APPCONFIGS \
			--metadatastore $DBB_MODELER_METADATA_STORE \
			--application $applicationDir \
			--moveFiles \
			--logFile $DBB_MODELER_LOGS/3-$applicationDir-assessUsage.log"
		$CMD "$@"
	done
fi
