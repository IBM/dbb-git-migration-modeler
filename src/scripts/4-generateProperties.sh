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

	cd $DBB_MODELER_APPLICATIONS
	for applicationDir in `ls | grep -v dbb-zappbuild`
	do
		echo "*******************************************************************"
		echo "Generate properties for application '$applicationDir'"
		echo "*******************************************************************"
    	CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/src/groovy/generateProperties.groovy \
			-w $DBB_MODELER_APPLICATIONS \
			-a $applicationDir \
			-l $DBB_MODELER_LOGS/4-$applicationDir-generateProperties.log"
		$CMD "$@"
	done
fi