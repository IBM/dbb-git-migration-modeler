#!/bin/sh
#*******************************************************************************
# Licensed Materials - Property of IBM
# (c) Copyright IBM Corporation 2018, 2019. All Rights Reserved.
#
# Note to U.S. Government Users Restricted Rights:
# Use, duplication or disclosure restricted by GSA ADP Schedule
# Contract with IBM Corp. 
#*******************************************************************************

#### Environment variables setup
. ./0-environment.sh

#### Cleanup output directories
if [ -d $DBB_MODELER_APPCONFIGS ] 
then
	rm -rf $DBB_MODELER_APPCONFIGS
fi
if [ -d $DBB_MODELER_APPLICATIONS ] 
then
	rm -rf $DBB_MODELER_APPLICATIONS
fi

if [ -d $DBB_MODELER_LOGS ] 
then
    rm -rf $DBB_MODELER_LOGS
fi


#### Create work directories
if [ ! -d $DBB_MODELER_LOGS ] 
then
   mkdir -p $DBB_MODELER_LOGS
fi

#### Application Extraction step
./1-extractApplications.sh -d DBEHM.MIG.COBOL,DBEHM.MIG.COPY,DBEHM.MIG.BMS --applicationMapping $DBB_MODELER_HOME/applicationMappings.yaml --repositoryPathsMapping $DBB_MODELER_HOME/repositoryPathsMapping.yaml --types $DBB_MODELER_HOME/types.txt -oc $DBB_MODELER_APPCONFIGS -oa $DBB_MODELER_APPLICATIONS -l $DBB_MODELER_LOGS/1-extractApplications.log
#./1-extractApplications.sh -d GITLAB.CATMAN.**.CO*,DBEHM.MIG.COBOL,DBEHM.MIG.COPY --applicationMapping $DBB_MODELER_HOME/applicationMappings-CATMAN.yaml --repositoryPathsMapping $DBB_MODELER_HOME/repositoryPathsMapping.yaml --types $DBB_MODELER_HOME/types.txt -oc $DBB_MODELER_APPCONFIGS -oa $DBB_MODELER_APPLICATIONS

echo "Press ENTER to continue or Ctrl+C to quit..."
read

#### Migration execution step
./2-runMigrations.sh

echo "Press ENTER to continue or Ctrl+C to quit..."
read

#### Classification step
./3-classify.sh

echo "Press ENTER to continue or Ctrl+C to quit..."
read

#### Property Generation step
./4-generateProperties.sh --typesConfigurations $DBB_MODELER_HOME/typesConfigurations.yaml -z /u/mdalbin/dbb-zappbuild