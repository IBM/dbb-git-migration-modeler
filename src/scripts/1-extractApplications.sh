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

	if [ ! -d $DBB_MODELER_APPCONFIGS ] 
	then
		mkdir -p $DBB_MODELER_APPCONFIGS
	fi
	CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/src/groovy/extractApplications.groovy"
	$CMD "$@"
fi