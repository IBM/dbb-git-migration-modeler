#!/bin/env bash
#*******************************************************************************
# Licensed Materials - Property of IBM
# (c) Copyright IBM Corporation 2018, 2024. All Rights Reserved.
#
# Note to U.S. Government Users Restricted Rights:
# Use, duplication or disclosure restricted by GSA ADP Schedule
# Contract with IBM Corp.
#*******************************************************************************

Prolog() {
	echo "                                                                                                            "
	echo " DBB Git Migration Modeler                                                                                  "
	echo " Release:     $MigrationModelerRelease                                                                      "
	echo "                                                                                                            "
	echo " Script:      InitApplicationRepositories.sh                                                                "
	echo "                                                                                                            "
	echo " Description: The purpose of this script is to initialize the Git repositories, by adding a default         "
	echo "              .gitattributes file. It additionally performs build preview that generates and initial        "
	echo "              DBB Build Report that can be examined by the application team.                                "
	echo "                                                                                                            "
	echo "              For more information please refer to:    https://github.com/IBM/dbb-git-migration-modeler     "
	echo "                                                                                                            "
}

# Read Migration Modeler Configuration
dir=$(dirname "$0")
. $dir/utils/0-environment.sh "$@"

# Internal variables
DBB_GIT_MIGRATION_MODELER_CONFIG_FILE=
rc=0

Prolog

if [ "$DBB_HOME" = "" ]; then
	echo "[ERROR] Environment variable DBB_HOME is not set. Exiting."
else
	if [ ! -d "$DBB_MODELER_APPLICATION_DIR" ]; then
		echo "[ERROR] The folder indicated by the 'DBB_MODELER_APPLICATION_DIR' variable does not exist. Exiting."
		exit 1
	fi

    echo
    echo "[PHASE] Initialize Git repositories"
	read -p "Do you want to initialize the Git repositories for the applications in '$DBB_MODELER_APPLICATION_DIR' (Y/n): " variable

	if [[ -z "$variable" || $variable =~ ^[Yy]$ ]]; then

	    # Initialize Repositories

		cd $DBB_MODELER_APPLICATION_DIR
		for applicationDir in $(ls | grep -v dbb-zappbuild); do
			if [ $rc -eq 0 ]; then
				echo
				cd $DBB_MODELER_APPLICATION_DIR/$applicationDir
				
				touch $DBB_MODELER_LOGS/Init-Application-Repositories.log
				chtag -tc IBM-1047 $DBB_MODELER_LOGS/Init-Application-Repositories.log	
				
				if [ $(git rev-parse --is-inside-work-tree 2>/dev/null | wc -l) -eq 1 ]; then
				    echo "[INFO] '$DBB_MODELER_APPLICATION_DIR/$applicationDir' is already a Git repository"
				else
					echo "[INFO] Initialize Git repository for application '$applicationDir'"
					
					CMD="git init --initial-branch=main"
					echo "[CMD] ${CMD}" >> $DBB_MODELER_LOGS/Init-Application-Repositories.log
					$CMD >> $DBB_MODELER_LOGS/Init-Application-Repositories.log
					rc=$?

					# tag application descriptor file
					if [ $rc -eq 0 ]; then
					
						if [ -f "${applicationDir}.yaml" ]; then
							echo "[INFO] Set file tag for '$applicationDir.yaml'"
							CMD="chtag -c IBM-1047 -t $applicationDir.yaml"
							echo "[CMD] ${CMD}" >> $DBB_MODELER_LOGS/Init-Application-Repositories.log
							$CMD >> $DBB_MODELER_LOGS/Init-Application-Repositories.log
							rc=$?
						fi
					fi

					# tag .gitattributes file
					if [ $rc -eq 0 ]; then
						echo "[INFO] Update Git configuration files '.gitattributes'"
						if [ -f ".gitattributes" ]; then
							CMD="rm .gitattributes"
							echo "[CMD] ${CMD}" >> $DBB_MODELER_LOGS/Init-Application-Repositories.log
							$CMD >> $DBB_MODELER_LOGS/Init-Application-Repositories.log
							rc=$?
						fi
						CMD="cp $DBB_MODELER_DEFAULT_GIT_CONFIG/.gitattributes .gitattributes"
						echo "[CMD] ${CMD}" >> $DBB_MODELER_LOGS/Init-Application-Repositories.log
						$CMD >> $DBB_MODELER_LOGS/Init-Application-Repositories.log
						rc=$?
					fi

					# Git list all changes
					if [ $rc -eq 0 ]; then
						echo "[INFO] Prepare initial commit"
						CMD="git status"
						echo "[CMD] ${CMD}" >> $DBB_MODELER_LOGS/Init-Application-Repositories.log
						$CMD >> $DBB_MODELER_LOGS/Init-Application-Repositories.log
						rc=$?
					fi
			
			        # Git add all changes
			        if [ $rc -eq 0 ]; then
			            CMD="git add --all"
			            echo "[CMD] ${CMD}" >> $DBB_MODELER_LOGS/Init-Application-Repositories.log
			            $CMD >> $DBB_MODELER_LOGS/Init-Application-Repositories.log
			            rc=$?
			        fi

					# Git commit changes
					if [ $rc -eq 0 ]; then
						echo "[INFO] Commit files to new Git repository"
						CMD="git commit -m 'Initial Commit'"
						echo "[CMD] ${CMD}" >> $DBB_MODELER_LOGS/Init-Application-Repositories.log
						git commit -m 'Initial Commit' >> $DBB_MODELER_LOGS/Init-Application-Repositories.log
						rc=$?
					fi

					# Git commit changes
					if [ $rc -eq 0 ]; then
						CMD="git tag rel-1.0.0"
						echo "[CMD] ${CMD}"  >> $DBB_MODELER_LOGS/Init-Application-Repositories.log
			            $CMD >> $DBB_MODELER_LOGS/Init-Application-Repositories.log
						rc=$?
					fi

					if [ $rc -eq 0 ]; then
						echo "[INFO] Initializing Git repository for application '$applicationDir' completed. rc="$rc
					else
						echo "[ERROR] Initializing Git repository for application '$applicationDir' failed. rc="$rc
					fi
			    fi
			fi
		done
    fi

	if [ $rc -eq 0 ]; then
	    echo
		echo "[PHASE] Run preview builds for applications"
	
		read -p "Do you want to run preview builds for the applications in $DBB_MODELER_APPLICATION_DIR (Y/n): " variable
		if [[ -z "$variable" || $variable =~ ^[Yy]$ ]]; then
			# Run zAppBuild to generate baselines
			cd $DBB_MODELER_APPLICATION_DIR

			for applicationDir in $(ls | grep -v dbb-zappbuild); do
				if [ $rc -eq 0 ]; then
					echo
					echo "[INFO] Build of application '$applicationDir' started"
				
					# mkdir application log directory
					mkdir -p $DBB_MODELER_LOGS/$applicationDir
					
					CMD="$DBB_HOME/bin/groovyz $DBB_ZAPPBUILD/build.groovy \
						--workspace $DBB_MODELER_APPLICATION_DIR/$applicationDir \
						--application $applicationDir \
						--outDir $DBB_MODELER_LOGS/$applicationDir \
						--fullBuild \
						--hlq DBEHM.DBB.MIG --preview \
						--logEncoding UTF-8 \
						--applicationCurrentBranch main \
						--propOverwrites createBuildOutputSubfolder=false \
						--propFiles /var/dbb/dbb-zappbuild-config/build.properties,/var/dbb/dbb-zappbuild-config/datasets.properties"
					echo "[INFO] $CMD"  >> $DBB_MODELER_LOGS/Init-Application-Repositories.log
					$CMD > $DBB_MODELER_LOGS/$applicationDir/build-preview-$applicationDir.log
					rc=$?
					echo "[INFO] Build of application '$applicationDir' completed with rc="$rc
					echo "[INFO] Build logs and reports available at '$DBB_MODELER_LOGS/$applicationDir'"
				fi
			done
			
			if [ $rc -eq 0 ]; then
				echo "[INFO] Performing preview builds completed successfully. rc="$rc
			else
				echo "[ERROR] Performing preview builds completed failed. rc="$rc
			fi
			
			if [ $rc -eq 0 ]; then
			    echo
				echo "[PHASE] Create baseline packages for applications"
				read -p "Do you want to create the baseline packages for the application in '$DBB_MODELER_APPLICATION_DIR' (Y/n): " variable
				if [[ -z "$variable" || $variable =~ ^[Yy]$ ]]; then

					# Run zAppBuild to generate baselines
					cd $DBB_MODELER_APPLICATION_DIR
					
					if [ ! -d "${DBB_COMMUNITY_REPO}" ]; then
						rc=4
						ERRMSG="[ERROR] Directory '$DBB_COMMUNITY_REPO' does not exist. rc="$rc
						echo $ERRMSG
					fi

					if [ ! -f "${DBB_COMMUNITY_REPO}/Pipeline/PackageBuildOutputs/PackageBuildOutputs.groovy" ]; then
						rc=4
						ERRMSG="[ERROR] Packaging Script '${DBB_COMMUNITY_REPO}/Pipeline/PackageBuildOutputs/PackageBuildOutputs.groovy' does not exist. rc="$rc
						echo $ERRMSG
					fi

					for applicationDir in $(ls | grep -v dbb-zappbuild); do
						if [ $rc -eq 0 ]; then
							echo
							echo "[INFO] Packaging of application '$applicationDir' started"
						
							# mkdir application log directory
							mkdir -p $DBB_MODELER_LOGS/$applicationDir
							
							CMD="$DBB_HOME/bin/groovyz $DBB_COMMUNITY_REPO/Pipeline/PackageBuildOutputs/PackageBuildOutputs.groovy \
								--workDir $DBB_MODELER_LOGS/$applicationDir \ 
								--addExtension \
								--branch main \
								--version rel-1.0.0 \
								--tarFileName $applicationDir-fullBaseline-rel-1.0.0.tar"
							echo "[INFO] $CMD"  >> $DBB_MODELER_LOGS/Init-Application-Repositories.log
							$CMD > $DBB_MODELER_LOGS/$applicationDir/packaging-preview-$applicationDir.log
							rc=$?
							echo "[INFO] Packaging of application '$applicationDir' completed with rc="$rc
							echo "[INFO] Packaging log available at '$DBB_MODELER_LOGS/$applicationDir/packaging-preview-$applicationDir.log'"
						fi
					done
				fi
			fi
		fi
	fi
fi
