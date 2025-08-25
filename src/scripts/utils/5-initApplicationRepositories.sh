#!/bin/env bash
#*******************************************************************************
# Licensed Materials - Property of IBM
# (c) Copyright IBM Corporation 2018, 2024. All Rights Reserved.
#
# Note to U.S. Government Users Restricted Rights:
# Use, duplication or disclosure restricted by GSA ADP Schedule
# Contract with IBM Corp.
#*******************************************************************************

# Internal variables
DBB_GIT_MIGRATION_MODELER_CONFIG_FILE=
rc=0

# Get Options
if [ $rc -eq 0 ]; then
	while getopts "c:" opt; do
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
		esac
	done
fi
#

# Validate Options
validateOptions() {
	if [ -z "${DBB_GIT_MIGRATION_MODELER_CONFIG_FILE}" ]; then
		rc=8
		ERRMSG="[ERROR] Argument to specify DBB Git Migration Modeler configuration file (-c) is required. rc="$rc
		echo $ERRMSG
	fi
	
	if [ ! -f "${DBB_GIT_MIGRATION_MODELER_CONFIG_FILE}" ]; then
		rc=8
		ERRMSG="[ERROR] DBB Git Migration Modeler configuration file not found. rc="$rc
		echo $ERRMSG
	fi
}

# Call validate Options
if [ $rc -eq 0 ]; then
 	validateOptions
fi

if [ $rc -eq 0 ]; then
	# Environment variables setup
	dir=$(dirname "$0")
	. $dir/0-environment.sh  -c ${DBB_GIT_MIGRATION_MODELER_CONFIG_FILE}

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
			
			CMD="git init --initial-branch=${APPLICATION_DEFAULT_BRANCH}"
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

            # Create baselineReference.config file
            #  See https://github.com/IBM/dbb/tree/main/Templates/Common-Backend-Scripts/samples
            if [ $rc -eq 0 ]; then
                echo "** Create file 'baselineReference.config'"
                baselineReferenceFile="$DBB_MODELER_APPLICATION_DIR/$applicationDir/application-conf/baselineReference.config"
                touch $baselineReferenceFile
                chtag -c ibm-1047 -t $baselineReferenceFile
                # Retrieve baseline versions
                version=`cat $DBB_MODELER_APPLICATION_DIR/$applicationDir/applicationDescriptor.yml | grep -A 2  "branch: \"$APPLICATION_DEFAULT_BRANCH\"" | tail -1 | awk -F ':' {'printf $3'} | sed "s/[\" ]//g"`
                if [ -z ${version} ]; then
                  version="rel-1.0.0"
                fi  
                
                # Write into file
                echo """# main branch - baseline reference for the next planned release 
main=refs/tags/${version} 

# release maintenance branch - for maintenance fixes for the current release in production ${version} 
release/${version}=refs/tags/${version}""" > $baselineReferenceFile
                rc=$?
            fi
			
			if [ $rc -eq 0 ]; then
				echo "** Prepare pipeline configuration for $PIPELINE_CI."
				if [ "$PIPELINE_CI" == "AzureDevOps" ]; then
				    CIFILE="$DBB_COMMUNITY_REPO/Templates/${PIPELINE_CI}Pipeline/azure-pipelines.yml"
                    if [ ! -f "${CIFILE}" ]; then
                        rc=8
                        ERRMSG="[ERROR] The pipeline template file '${CIFILE}' was not found. rc="$rc
                        echo $ERRMSG
                    else 
    					CMD="cp ${CIFILE} $DBB_MODELER_APPLICATION_DIR/$applicationDir/"
    					echo "[CMD] ${CMD}" >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
    					$CMD >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
    					rc=$?
    					mkdir -p $DBB_MODELER_APPLICATION_DIR/$applicationDir/deployment
    					CMD="cp -R $DBB_COMMUNITY_REPO/Templates/${PIPELINE_CI}Pipeline/templates/deployment/*.yml $DBB_MODELER_APPLICATION_DIR/$applicationDir/deployment/"
    					echo "[CMD] ${CMD}" >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
    					$CMD >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
    					rc=$?
    					mkdir -p $DBB_MODELER_APPLICATION_DIR/$applicationDir/tagging
    					CMD="cp -R $DBB_COMMUNITY_REPO/Templates/${PIPELINE_CI}Pipeline/templates/tagging/*.yml $DBB_MODELER_APPLICATION_DIR/$applicationDir/tagging/"
    					echo "[CMD] ${CMD}" >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
    					$CMD >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
    					rc=$?
					fi
				fi
				if [ "${PIPELINE_CI}" == "GitlabCIPipeline-for-zos-native-runner" ]; then
				    CIFILE="$DBB_COMMUNITY_REPO/Templates/${PIPELINE_CI}/.gitlab-ci.yml"
				    if [ ! -f "${CIFILE}" ]; then
                        rc=8
                        ERRMSG="[ERROR] The pipeline template file '${CIFILE}' was not found. rc="$rc
                        echo $ERRMSG
                    else
    					CMD="cp ${CIFILE} $DBB_MODELER_APPLICATION_DIR/$applicationDir/"
    					echo "[CMD] ${CMD}" >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
    					$CMD >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
    					rc=$?
					fi
				fi
                if [ "${PIPELINE_CI}" == "GitlabCIPipeline-for-distributed-runner" ]; then
                    CIFILE="$DBB_COMMUNITY_REPO/Templates/${PIPELINE_CI}/.gitlab-ci.yml"
                    if [ ! -f "${CIFILE}" ]; then
                        rc=8
                        ERRMSG="[ERROR] The pipeline template file '${CIFILE}' was not found. rc="$rc
                        echo $ERRMSG
                    else
                        CMD="cp ${CIFILE} $DBB_MODELER_APPLICATION_DIR/$applicationDir/"
                        echo "[CMD] ${CMD}" >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
                        $CMD >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
                        rc=$?
                    fi
                fi
				if [ "${PIPELINE_CI}" == "Jenkins" ]; then
                    CIFILE="$DBB_COMMUNITY_REPO/Templates/${PIPELINE_CI}Pipeline/Jenkinsfile"
                    if [ ! -f "${CIFILE}" ]; then
                        rc=8
                        ERRMSG="[ERROR] The pipeline template file '${CIFILE}' was not found. rc="$rc
                        echo $ERRMSG
                    else
    					CMD="cp ${CIFILE} $DBB_MODELER_APPLICATION_DIR/$applicationDir/"
    					echo "[CMD] ${CMD}" >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
    					$CMD >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
    					rc=$?
					fi
				fi
				if [ "${PIPELINE_CI}" == "GitHubActions" ]; then
				    CIFILE="$DBB_COMMUNITY_REPO/Templates/${PIPELINE_CI}Pipeline/.github"
                    if [ ! -f "${CIFILE}" ]; then
                        rc=8
                        ERRMSG="[ERROR] The pipeline template file '${CIFILE}' was not found. rc="$rc
                        echo $ERRMSG
                    else
    					CMD="cp -R ${CIFILE} $DBB_MODELER_APPLICATION_DIR/$applicationDir/"
    					echo "[CMD] ${CMD}" >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
    					$CMD >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
    					rc=$?
					fi
				fi		
			fi			


			# Git list all changes
			if [ $rc -eq 0 ]; then
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

			# Git create tag and release maintenance branch
			if [ $rc -eq 0 ]; then
				version=`cat $DBB_MODELER_APPLICATION_DIR/$applicationDir/applicationDescriptor.yml | grep -A 2  "branch: \"$APPLICATION_DEFAULT_BRANCH\"" | tail -1 | awk -F ':' {'printf $2'} | sed "s/[\" ]//g"`
				if [ -z ${version} ]; then
				  version="rel-1.0.0"
				fi		
				CMD="git tag $version"
				echo "[CMD] ${CMD}"  >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
				$CMD >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
				rc=$?
				if [ $rc -eq 0 ]; then
					CMD="git branch $version refs/tags/$version"
					echo "[CMD] ${CMD}"  >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
					$CMD >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
					rc=$?
				fi
			fi

			if [ $rc -eq 0 ]; then
				echo "** Initializing Git repository for application '$applicationDir' completed successfully. rc="$rc
			else
				echo "*! [ERROR] Initializing Git repository for application '$applicationDir' failed. rc="$rc
			fi
	    fi

		if [ $rc -eq 0 ]; then
			echo "** Preview Build of application '$applicationDir' started"
		
			# mkdir application log directory
			mkdir -p $DBB_MODELER_LOGS/$applicationDir

			if [ "$DBB_MODELER_METADATASTORE_TYPE" = "file" ]; then
				declare METADATASTORE_OPTIONS="--propOverwrites createBuildOutputSubfolder=false,metadataStoreType=$DBB_MODELER_METADATASTORE_TYPE,metadataStoreFileLocation=$DBB_MODELER_FILE_METADATA_STORE_DIR"
			elif [ "$DBB_MODELER_METADATASTORE_TYPE" = "db2" ]; then
				if [ -n "$DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORD" ]; then
					declare METADATASTORE_OPTIONS="--propOverwrites createBuildOutputSubfolder=false,metadataStoreType=$DBB_MODELER_METADATASTORE_TYPE,metadataStoreDb2ConnectionConf=$DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE --id $DBB_MODELER_DB2_METADATASTORE_JDBC_ID --pw $DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORD"
				elif [ -n "$DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE" ]; then
					declare METADATASTORE_OPTIONS="--propOverwrites createBuildOutputSubfolder=false,metadataStoreType=$DBB_MODELER_METADATASTORE_TYPE,metadataStoreDb2ConnectionConf=$DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE --id $DBB_MODELER_DB2_METADATASTORE_JDBC_ID --pwFile $DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE"
				fi
			fi
			
			
			CMD="$DBB_HOME/bin/groovyz $DBB_ZAPPBUILD/build.groovy \
				--workspace $DBB_MODELER_APPLICATION_DIR \
				--application $applicationDir \
				--outDir $DBB_MODELER_LOGS/$applicationDir \
				--fullBuild \
				--hlq $APPLICATION_ARTIFACTS_HLQ --preview \
				--logEncoding UTF-8 \
				--applicationCurrentBranch $APPLICATION_DEFAULT_BRANCH \
				${METADATASTORE_OPTIONS} "
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
			version=`cat $DBB_MODELER_APPLICATION_DIR/$applicationDir/applicationDescriptor.yml | grep -A 2  "branch: \"${APPLICATION_DEFAULT_BRANCH}\"" | tail -1 | awk -F ':' {'printf $2'} | sed "s/[\" ]//g"`
			if [ -z ${version} ]; then
			  version="rel-1.0.0"
			fi
			 
			CMD="$DBB_HOME/bin/groovyz $DBB_COMMUNITY_REPO/Pipeline/PackageBuildOutputs/PackageBuildOutputs.groovy \
				--workDir $DBB_MODELER_LOGS/$applicationDir \ 
				--addExtension \
				--branch $APPLICATION_DEFAULT_BRANCH \
				--version $version \
				--tarFileName $applicationDir-$version-baseline.tar \
				--applicationFolderPath $DBB_MODELER_APPLICATION_DIR/$applicationDir \
				--owner $PIPELINE_USER:$PIPELINE_USER_GROUP"
			if [ "$PUBLISH_ARTIFACTS" == "true" ]; then
				CMD="${CMD} -p --artifactRepositoryUrl $ARTIFACT_REPOSITORY_SERVER_URL \
				     --artifactRepositoryUser $ARTIFACT_REPOSITORY_USER \
				     --artifactRepositoryPassword $ARTIFACT_REPOSITORY_PASSWORD \
					 --artifactRepositoryDirectory release \
				     --artifactRepositoryName $applicationDir-$ARTIFACT_REPOSITORY_SUFFIX"
			fi
			echo "** $CMD"  >> $DBB_MODELER_LOGS/5-$applicationDir-initApplicationRepository.log
			$CMD > $DBB_MODELER_LOGS/$applicationDir/packaging-preview-$applicationDir.log
			rc=$?
			if [ $rc -eq 0 ]; then
				echo "** Packaging of application '$applicationDir' completed successfully. rc="$rc
			else
				echo "*! [ERROR] Packaging of application '$applicationDir' failed. rc="$rc
				echo "** Packaging log available at '$DBB_MODELER_LOGS/$applicationDir/packaging-preview-$applicationDir.log'"
			fi			
		fi
	done
fi
