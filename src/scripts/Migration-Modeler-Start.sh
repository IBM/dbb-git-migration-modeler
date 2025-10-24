#!/bin/env bash
#*******************************************************************************
# Licensed Materials - Property of IBM
# (c) Copyright IBM Corporation 2018, 2019. All Rights Reserved.
#
# Note to U.S. Government Users Restricted Rights:
# Use, duplication or disclosure restricted by GSA ADP Schedule
# Contract with IBM Corp.
#*******************************************************************************

Prolog() {
	echo
	echo " DBB Git Migration Modeler                                                                                  "
	echo " Release:     $MigrationModelerRelease                                                                      "
	echo
	echo " Script:      Migration-Modeler-Start.sh                                                                    "
	echo
	echo " Description: The purpose of this script is to facilitate the execution of the 4-step process supported     "
	echo "              by the DBB Git Migration Modeler.                                                             "
	echo "              For more information please refer to:    https://github.com/IBM/dbb-git-migration-modeler     "
	echo
}

# Internal variables
DBB_GIT_MIGRATION_MODELER_CONFIG_FILE=
APPLICATION_FILTER=
rc=0

# Get Options
if [ $rc -eq 0 ]; then
	while getopts "c:a:" opt; do
		case $opt in
		c)
			argument="$OPTARG"
			nextchar="$(expr substr $argument 1 1)"
			if [ -z "$argument" ] || [ "$nextchar" = "-" ]; then
				rc=4
				ERRMSG="[ERROR] DBB Git Migration Modeler Configuration file required. rc="$rc
				echo $ERRMSG
				break
			fi
			DBB_GIT_MIGRATION_MODELER_CONFIG_FILE="$argument"
			;;
		a)
			argument="$OPTARG"
			nextchar="$(expr substr $argument 1 1)"
			if [ -z "$argument" ] || [ "$nextchar" = "-" ]; then
				rc=4
				ERRMSG="[ERROR] Comma-separated Applications list required. rc="$rc
				echo $ERRMSG
				break
			fi
			APPLICATION_FILTER="-a $argument"
			;;
		esac
	done
fi
#

# Validate Options
validateOptions() {
	if [ -z "${DBB_GIT_MIGRATION_MODELER_CONFIG_FILE}" ]; then
		rc=8
		ERRMSG="[ERROR] Argument to specify DBB Git Migration Modeler configuration file (-c) is required. rc="$rc
	fi
	
	if [ ! -f "${DBB_GIT_MIGRATION_MODELER_CONFIG_FILE}" ]; then
		rc=8
		ERRMSG="[ERROR] DBB Git Migration Modeler configuration file not found. rc="$rc
	fi
}

# Call validate Options
if [ $rc -eq 0 ]; then
 	validateOptions
fi

if [ $rc -eq 0 ]; then
	# Environment variables setup
	dir=$(dirname "$0")
	. $dir/utils/0-validateConfiguration.sh -e -c ${DBB_GIT_MIGRATION_MODELER_CONFIG_FILE}
	rc=$?

	if [ $rc -ne 0 ]; then
		exit $rc
	fi

	PGM="Migration-Modeler-Start.sh"
	# Print Prolog
	export MigrationModelerRelease=`cat $DBB_MODELER_HOME/release.properties | awk -F '=' '{printf $2}'`
	Prolog
fi
	
if [ $rc -eq 0 ]; then
	echo ""
	echo "[PHASE] Cleanup working directories"
	if [[ $INTERACTIVE_RUN == "true" ]]; then
		read -p "Do you want to clean the working directory '$DBB_MODELER_WORK' (Y/n): " variable
	else
		variable="Y"
	fi

	if [[ -z "$variable" || $variable =~ ^[Yy]$ ]]; then

		#### Cleanup output directories
		if [ -d $DBB_MODELER_APPCONFIG_DIR ]; then
			rm -rf $DBB_MODELER_APPCONFIG_DIR
			echo "[INFO] Removed '${DBB_MODELER_APPCONFIG_DIR}' folder"
		fi
		if [ -d $DBB_MODELER_APPLICATION_DIR ]; then
			rm -rf $DBB_MODELER_APPLICATION_DIR
			echo "[INFO] Removed '${DBB_MODELER_APPLICATION_DIR}' folder"
		fi
		if [ -d $DBB_MODELER_LOGS ]; then
			rm -rf $DBB_MODELER_LOGS
			echo "[INFO] Removed '${DBB_MODELER_LOGS}' folder"
		fi
	fi
fi

if [ $rc -eq 0 ]; then
	#### Create work directories
	if [ ! -d $DBB_MODELER_LOGS ]; then
		mkdir -p $DBB_MODELER_LOGS
		echo "[INFO] Created '${DBB_MODELER_LOGS}' folder"
		rc=$?
	fi
fi

if [ $rc -eq 0 ]; then
	echo
	echo "[PHASE] Extract applications from using Applications Mapping files located at '$DBB_MODELER_APPMAPPINGS_DIR'"
	if [[ $INTERACTIVE_RUN == "true" ]]; then
		read -p "Do you want run the application extraction (Y/n): " variable
	else
		variable="Y"
	fi

	if [[ -z "$variable" || $variable =~ ^[Yy]$ ]]; then
  	
		#### Application Extraction step
		$DBB_MODELER_HOME/src/scripts/utils/1-extractApplications.sh -c $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE $APPLICATION_FILTER
    	rc=$?
		# Note - create multiple Migration Modeler Configuration files, if you want to run the extraction step with different datasets configurations.
		
		## The following command can be used when datasets contain mixed types of artifacts, the use of the scanDatasetMembers option enables the DBB Scanner to understand the type of artifacts and route them to the right subfolder in USS
		#$DBB_MODELER_HOME/src/scripts/1-extractApplications.sh -d DBEHM.MIG.MIXED,DBEHM.MIG.BMS --applicationsMapping $DBB_MODELER_WORK/applicationsMapping.yaml --repositoryPathsMapping $DBB_MODELER_WORK/repositoryPathsMapping.yaml --types $DBB_MODELER_WORK/types.txt -oc $DBB_MODELER_APPCONFIGS -oa $DBB_MODELER_APPLICATIONS -l $DBB_MODELER_LOGS/1-extractApplications.log -scanDatasetMembers -scanEncoding IBM-1047
		## The following command can be used when wildcards are used to list the datasets that should be scanned.
		#$DBB_MODELER_HOME/src/scripts/1-extractApplications.sh -d GITLAB.CATMAN.**.CO*,DBEHM.MIG.COBOL,DBEHM.MIG.COPY --applicationsMapping $DBB_MODELER_WORK/applicationsMapping-CATMAN.yaml --repositoryPathsMapping $DBB_MODELER_WORK/repositoryPathsMapping.yaml --types $DBB_MODELER_WORK/types.txt -oc $DBB_MODELER_APPCONFIGS -oa $DBB_MODELER_APPLICATIONS
    
	fi
fi

if [ $rc -eq 0 ]; then
	#### Migration execution step
	echo
	echo "[PHASE] Execute migrations using DBB Migration mapping files stored in '$DBB_MODELER_APPCONFIG_DIR'"
	if [[ $INTERACTIVE_RUN == "true" ]]; then
		read -p "Do you want to execute the migration using DBB Migration utility (Y/n): " variable
	else
		variable="Y"
	fi
	
	if [[ -z "$variable" || $variable =~ ^[Yy]$ ]]; then
		$DBB_MODELER_HOME/src/scripts/utils/2-runMigrations.sh -c $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE $APPLICATION_FILTER
    	rc=$?
	fi
fi

if [ $rc -eq 0 ]; then
	#### Classification step
	echo
	echo "[PHASE] Assess usage and perform classification"
	if [[ $INTERACTIVE_RUN == "true" ]]; then
		read -p "Do you want to perform the usage assessment and classification process (Y/n): " variable
	else
		variable="Y"
	fi
	if [[ -z "$variable" || $variable =~ ^[Yy]$ ]]; then
		$DBB_MODELER_HOME/src/scripts/utils/3-classify.sh -c $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE $APPLICATION_FILTER
    	rc=$?
	fi
fi

if [ $rc -eq 0 ]; then
	#### Property Generation step
	echo
	echo "[PHASE] Generate build configuration"
	if [[ $INTERACTIVE_RUN == "true" ]]; then
		read -p "Do you want to generate the $BUILD_FRAMEWORK configurations (Y/n): " variable
	else
		variable="Y"
	fi
	if [[ -z "$variable" || $variable =~ ^[Yy]$ ]]; then
		$DBB_MODELER_HOME/src/scripts/utils/4-generateProperties.sh -c $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE $APPLICATION_FILTER
    	rc=$?
	fi
fi

repositoriesInitialized=false

if [ $rc -eq 0 ]; then
	#### Application repository initialization
	echo
	echo "[PHASE] Initialize application's repositories"
	if [[ $INTERACTIVE_RUN == "true" ]]; then
		read -p "Do you want to initialize application's repositories (Y/n): " variable
	else
		variable="Y"
	fi
	if [[ -z "$variable" || $variable =~ ^[Yy]$ ]]; then
		$DBB_MODELER_HOME/src/scripts/utils/5-initApplicationRepositories.sh -c $DBB_GIT_MIGRATION_MODELER_CONFIG_FILE $APPLICATION_FILTER
		rc=$?
		if [ $rc -eq 0 ]; then
			repositoriesInitialized=true
		fi			
	fi
fi

if [ $rc -eq 0 ]; then
	#### Summary
	echo
	echo "[PHASE] Summary"
	$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/src/groovy/utils/calculateDependenciesOrder.groovy -a $DBB_MODELER_APPLICATION_DIR
	if [ "$repositoriesInitialized" = true ]; then
		echo
		echo "***********************************************************************************************************"
		echo "*************************************    What needs to be done now    *************************************"
		echo "***********************************************************************************************************"
		echo
		
		case $PIPELINE_CI in
			AzureDevOps)
				GitDistribution="Azure DevOps platform"
				PipelineOrchestrator="Azure DevOps"
			;;
			GitlabCI)
				GitDistribution="GitLab platform"
				PipelineOrchestrator="GitLab CI"
			;;
			GitHubActions)
				GitDistribution="GitHub platform"
				PipelineOrchestrator="GitHub Actions"
			;;
			*)
				GitDistribution="Git Central server"
				PipelineOrchestrator="Pipeline Orchestrator's"
			;;
		esac
		
		
		echo "For each application:                                                                                      "
		echo "- Create a Git project in your $GitDistribution                                                            "
		echo "- Add a remote configuration for the application's Git repository on USS using the 'git remote add' command"
		echo "- Initialize the $PipelineOrchestrator variables in the pipeline configuration"
		echo "- Push the application's Git repository in the order of the above ranked list                            "
		echo
		echo "***********************************************************************************************************"
	fi	
fi

if [ $rc -ne 0 ]; then
	echo ${ERRMSG}
	exit $rc
fi