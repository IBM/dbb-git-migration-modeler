#!/bin/env bash
#*******************************************************************************
# Licensed Materials - Property of IBM
# (c) Copyright IBM Corporation 2018, 2024. All Rights Reserved.
#
# Note to U.S. Government Users Restricted Rights:
# Use, duplication or disclosure restricted by GSA ADP Schedule
# Contract with IBM Corp.
#*******************************************************************************

if [ "$DBB_HOME" = "" ]; then
	echo "[ERROR] Environment variable DBB_HOME is not set. Exiting."
else
	# Read Migration Modeler Configuration
	dir=$(dirname "$0")
	. $dir/0-environment.sh "$@"

	if [ ! -d "$DBB_MODELER_APPLICATION_DIR" ]; then
		echo "[ERROR] The folder indicated by the 'DBB_MODELER_APPLICATION_DIR' variable does not exist. Exiting."
		exit 1
	fi
	
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
	

    # Initialize Repositories

	cd $DBB_MODELER_APPLICATION_DIR
	for applicationDir in $(ls | grep -v dbb-zappbuild)
	do
		echo "*******************************************************************"
		echo "Initialize application's directory for application '$applicationDir'"
		echo "*******************************************************************"
		cd $DBB_MODELER_APPLICATION_DIR/$applicationDir
		
		touch $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
		chtag -tc IBM-1047 $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log	
		
		if [ $(git rev-parse --is-inside-work-tree 2>/dev/null | wc -l) -eq 1 ]; then
		    echo "*! [WARNING] '$DBB_MODELER_APPLICATION_DIR/$applicationDir' is already a Git repository"
		else
			echo "** Initialize Git repository for application '$applicationDir'"
			
			CMD="git init --initial-branch=main"
			echo "[CMD] ${CMD}" >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
			$CMD >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
			rc=$?

			# tag application descriptor file
			if [ $rc -eq 0 ]; then
				if [ -f "applicationDescriptor.yml" ]; then
					echo "** Set file tag for 'applicationDescriptor.yml'"
					CMD="chtag -c IBM-1047 -t applicationDescriptor.yml"
					echo "[CMD] ${CMD}" >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
					$CMD >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
					rc=$?
				fi
			fi

			# copy .gitattributes file
			if [ $rc -eq 0 ]; then
				echo "** Update Git configuration file '.gitattributes'"
				if [ -f ".gitattributes" ]; then
					CMD="rm .gitattributes"
					echo "[CMD] ${CMD}" >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
					$CMD >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
					rc=$?
				fi
				CMD="cp $DBB_MODELER_DEFAULT_GIT_CONFIG/.gitattributes .gitattributes"
				echo "[CMD] ${CMD}" >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
				$CMD >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
				rc=$?
			fi

			# copy and customize ZAPP file
			if [ $rc -eq 0 ]; then
				echo "** Update ZAPP file 'zapp.yaml'"
				if [ -f "zapp.yaml" ]; then
					CMD="rm zapp.yaml"
					echo "[CMD] ${CMD}" >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
					$CMD >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
					rc=$?
				fi
				CMD="cp $DBB_MODELER_DEFAULT_GIT_CONFIG/zapp.yaml zapp.yaml"
				echo "[CMD] ${CMD}" >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
				$CMD >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
				CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/src/groovy/utils/zappUtils.groovy \
					-z $DBB_MODELER_APPLICATION_DIR/$applicationDir/zapp.yaml -a $DBB_MODELER_APPLICATION_DIR/$applicationDir/applicationDescriptor.yml -b $DBB_ZAPPBUILD"
				echo "[CMD] ${CMD}" >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
				$CMD >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
				rc=$?
			fi


			# Git list all changes
			if [ $rc -eq 0 ]; then
				echo "** Prepare initial commit"
				CMD="git status"
				echo "[CMD] ${CMD}" >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
				$CMD >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
				rc=$?
			fi
	
	        # Git add all changes
	        if [ $rc -eq 0 ]; then
	            CMD="git add --all"
	            echo "[CMD] ${CMD}" >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
	            $CMD >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
	            rc=$?
	        fi

			# Git commit changes
			if [ $rc -eq 0 ]; then
				echo "** Commit files to new Git repository"
				CMD="git commit -m 'Initial Commit'"
				echo "[CMD] ${CMD}" >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
				git commit -m 'Initial Commit' >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
				rc=$?
			fi

			# Git commit changes
			if [ $rc -eq 0 ]; then
				CMD="git tag rel-1.0.0"
				echo "[CMD] ${CMD}"  >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
	            $CMD >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
				rc=$?
			fi

			if [ $rc -eq 0 ]; then
				echo "** Initializing Git repository for application '$applicationDir' completed. rc="$rc
			else
				echo "*! [ERROR] Initializing Git repository for application '$applicationDir' failed. rc="$rc
			fi
	    fi

		if [ $rc -eq 0 ]; then
			echo "** Preview Build of application '$applicationDir' started"
		
			# mkdir application log directory
			mkdir -p $DBB_MODELER_LOGS/$applicationDir
			
			CMD="$DBB_HOME/bin/groovyz $DBB_ZAPPBUILD/build.groovy \
				--workspace $DBB_MODELER_APPLICATION_DIR/$applicationDir \
				--application $applicationDir \
				--outDir $DBB_MODELER_LOGS/$applicationDir \
				--fullBuild \
				--hlq $APPLICATION_ARTIFACTS_HLQ --preview \
				--logEncoding UTF-8 \
				--applicationCurrentBranch main \
				--propOverwrites createBuildOutputSubfolder=false \
				--propFiles /var/dbb/dbb-zappbuild-config/build.properties,/var/dbb/dbb-zappbuild-config/datasets.properties"
			echo "** $CMD"  >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
			$CMD > $DBB_MODELER_LOGS/$applicationDir/build-preview-$applicationDir.log
			rc=$?
			if [ $rc -eq 0 ]; then
				echo "** Preview Build of application '$applicationDir' completed successfully. rc="$rc
			else
				echo "*! [ERROR] Preview Build of application '$applicationDir' failed. rc="$rc
				echo "** Build logs and reports available at '$DBB_MODELER_LOGS/$applicationDir'"
			fi
		fi
	
		if [ $rc -eq 0 ]; then
			echo "** Packaging of application '$applicationDir' started"
		
			# mkdir application log directory
			mkdir -p $DBB_MODELER_LOGS/$applicationDir
			version=`cat $DBB_MODELER_APPLICATION_DIR/$applicationDir/applicationDescriptor.yml | grep -A 1  "branch: \"main\"" | tail -1 | awk -F ':' {'printf $2'} | sed "s/[\" ]//g"`
			if [ -z ${version} ]; then
			  version="rel-1.0.0"
			fi
			 
			CMD="$DBB_HOME/bin/groovyz $DBB_COMMUNITY_REPO/Pipeline/PackageBuildOutputs/PackageBuildOutputs.groovy \
				--workDir $DBB_MODELER_LOGS/$applicationDir \ 
				--addExtension \
				--branch main \
				--version $version \
				--tarFileName $applicationDir-$version.tar \
				--owner $PIPELINE_USER:$PIPELINE_USER_GROUP"
			if [ "$PUBLISH_ARTIFACTS" == "true" ]; then
				CMD="${CMD} -p --artifactRepositoryUrl $ARTIFACT_REPOSITORY_SERVER_URL \
				     --artifactRepositoryUser $ARTIFACT_REPOSITORY_USER \
				     --artifactRepositoryPassword $ARTIFACT_REPOSITORY_PASSWORD \
				     --artifactRepositoryName $applicationDir"
			fi
			echo "** $CMD"  >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
			$CMD > $DBB_MODELER_LOGS/$applicationDir/packaging-preview-$applicationDir.log
			rc=$?
			if [ $rc -eq 0 ]; then
				echo "** Packaging of application '$applicationDir' completed with rc="$rc
			else
				echo "*! [ERROR] Packaging of application '$applicationDir' failed. rc="$rc
				echo "** Packaging log available at '$DBB_MODELER_LOGS/$applicationDir/packaging-preview-$applicationDir.log'"
			fi			
		fi
	done
fi
