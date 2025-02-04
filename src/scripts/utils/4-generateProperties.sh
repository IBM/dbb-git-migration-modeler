#!/bin/env bash
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
	. $dir/0-environment.sh "$@"

	cd $DBB_MODELER_APPLICATION_DIR
	for applicationDir in `ls | grep -v dbb-zappbuild`
	do
		echo "*******************************************************************"
		echo "Generate properties for application '$applicationDir'"
		echo "*******************************************************************"
		touch $DBB_MODELER_LOGS/4-$applicationDir-generateProperties.log
		chtag -tc IBM-1047 $DBB_MODELER_LOGS/4-$applicationDir-generateProperties.log
		CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/src/groovy/generateProperties.groovy \
			$@ \
			--application $applicationDir \
			--logFile $DBB_MODELER_LOGS/4-$applicationDir-generateProperties.log"
		echo "[INFO] ${CMD}" >> $DBB_MODELER_LOGS/4-$applicationDir-generateProperties.log
		$CMD
	done
fi