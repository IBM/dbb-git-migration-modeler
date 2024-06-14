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
dir=$(dirname "$0")
. $dir/0-environment.sh

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
$DBB_MODELER_HOME/src/scripts/1-extractApplications.sh -d DBEHM.MIG.COBOL,DBEHM.MIG.COPY,DBEHM.MIG.BMS --applicationsMapping $DBB_MODELER_WORK/applicationsMapping.yaml --repositoryPathsMapping $DBB_MODELER_WORK/repositoryPathsMapping.yaml --types $DBB_MODELER_WORK/types.txt -oc $DBB_MODELER_APPCONFIGS -oa $DBB_MODELER_APPLICATIONS -l $DBB_MODELER_LOGS/1-extractApplications.log
## The following command can be used when datasets contain mixed types of artifacts, the use of the scanDatasetMembers option enables the DBB Scanner to understand the type of artifacts and route them to the right subfolder in USS
#$DBB_MODELER_HOME/src/scripts/1-extractApplications.sh -d DBEHM.MIG.MIXED,DBEHM.MIG.BMS --applicationsMapping $DBB_MODELER_WORK/applicationsMapping.yaml --repositoryPathsMapping $DBB_MODELER_WORK/repositoryPathsMapping.yaml --types $DBB_MODELER_WORK/types.txt -oc $DBB_MODELER_APPCONFIGS -oa $DBB_MODELER_APPLICATIONS -l $DBB_MODELER_LOGS/1-extractApplications.log -scanDatasetMembers -scanEncoding IBM-1047
## The following command can be used when wildcards are used to list the datasets that should be scanned.
#$DBB_MODELER_HOME/src/scripts/1-extractApplications.sh -d GITLAB.CATMAN.**.CO*,DBEHM.MIG.COBOL,DBEHM.MIG.COPY --applicationsMapping $DBB_MODELER_WORK/applicationsMapping-CATMAN.yaml --repositoryPathsMapping $DBB_MODELER_WORK/repositoryPathsMapping.yaml --types $DBB_MODELER_WORK/types.txt -oc $DBB_MODELER_APPCONFIGS -oa $DBB_MODELER_APPLICATIONS

echo "Press ENTER to continue or Ctrl+C to quit..."
read

#### Migration execution step
$DBB_MODELER_HOME/src/scripts/2-runMigrations.sh

echo "Press ENTER to continue or Ctrl+C to quit..."
read

#### Classification step
$DBB_MODELER_HOME/src/scripts/3-classify.sh

echo "Press ENTER to continue or Ctrl+C to quit..."
read

#### Property Generation step
$DBB_MODELER_HOME/src/scripts/4-generateProperties.sh --typesConfigurations $DBB_MODELER_WORK/typesConfigurations.yaml -z /u/mdalbin/dbb-zappbuild