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
	echo "Environment variable DBB_HOME is not set. Exiting..."
else
	# Environment variables setup
	. ./0-environment.sh

	cd $DBB_MODELER_APPCONFIGS
	for mappingFile in `ls *.mapping`
	do
		application=`echo $mappingFile | awk -F. '{ print $1 }'`
		echo "*******************************************************************"
		echo "Generate properties for application '$application'"
		echo "*******************************************************************"
    	CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/generateProperties.groovy \
			-w $DBB_MODELER_APPLICATIONS \
			-a $application \
			-l $DBB_MODELER_LOGS/4-$application-generateProperties.log"
    	$CMD "$@"		
	done
fi