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
		echo "Scan application directory $DBB_MODELER_APPLICATIONS/$application"
		echo "*******************************************************************"
		CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/scanApplication.groovy \
			-w $DBB_MODELER_APPLICATIONS \
			-a $application \
			-l $DBB_MODELER_LOGS/3-$application-scan.log"
		$CMD "$@"
	done

	cd $DBB_MODELER_APPCONFIGS
	for mappingFile in `ls *.mapping`
	do
		application=`echo $mappingFile | awk -F. '{ print $1 }'`
		echo "*******************************************************************"
		echo "Assess Include files & Programs usage for $application"
		echo "*******************************************************************"
		CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/assessUsage.groovy \
			--workspace $DBB_MODELER_APPLICATIONS \
			--application $application \
			--configurations $DBB_MODELER_APPCONFIGS \
			--moveFiles \
			--logFile $DBB_MODELER_LOGS/3-$application-assessUsage.log"
		$CMD "$@"
	done
fi