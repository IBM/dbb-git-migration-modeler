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
	dir=$(dirname "$0")
	. $dir/0-environment.sh

	if [ -d $DBB_MODELER_APPLICATIONS ] 
	then
		rm -rf $DBB_MODELER_APPLICATIONS
	fi

	cd $DBB_MODELER_APPCONFIGS
	for mappingFile in `ls *.mapping`
	do
		application=`echo $mappingFile | awk -F. '{ print $1 }'`
		echo "***** Running the DBB Migration Utility for application $application using file $mappingFile *****"
		mkdir -p $DBB_MODELER_APPLICATIONS/$application
		cd $DBB_MODELER_APPLICATIONS/$application
		CMD="$DBB_HOME/bin/groovyz $DBB_HOME/migration/bin/migrate.groovy -l $DBB_MODELER_LOGS/2-$application.migration.log -np info -r $DBB_MODELER_APPLICATIONS/$application $DBB_MODELER_APPCONFIGS/$mappingFile"
		$CMD "$@"
	done
fi