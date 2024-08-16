#!/bin/sh
#*******************************************************************************
# Licensed Materials - Property of IBM
# (c) Copyright IBM Corporation 2018, 2024. All Rights Reserved.
#
# Note to U.S. Government Users Restricted Rights:
# Use, duplication or disclosure restricted by GSA ADP Schedule
# Contract with IBM Corp. 
#*******************************************************************************

Help() {
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

#### Environment variables setup
dir=$(dirname "$0")
. $dir/0-environment.sh

Help

if [  "$DBB_HOME" = "" ]
then
    echo "[ERROR] Environment variable DBB_HOME is not set. Exiting."
else


    if [ ! -d "$DBB_MODELER_APPLICATIONS" ]; then
        echo "[ERROR] The folder indicated by the 'DBB_MODELER_APPLICATIONS' does not exist. Exiting."
        exit 1
    fi

   
    # Initialize Repositories
    echo "*******************************************************************"
    echo "[STEP] Initialize Git Repositories                                 "
    echo "*******************************************************************"
    
    cd $DBB_MODELER_APPLICATIONS
    for applicationDir in `ls | grep -v dbb-zappbuild`
    do
        echo "[APPLICATION] Initialize Git Repo for $DBB_MODELER_APPLICATIONS/$applicationDir"

        cd $DBB_MODELER_APPLICATIONS/$applicationDir

        if [ $(git rev-parse --is-inside-work-tree) ]; then
            echo "[INFO] $DBB_MODELER_APPLICATIONS/$applicationDir is already a git repository "
        else 
            echo "[INFO] Initialize Git Repository for application $applicationDir"
    
            CMD="git init --initial-branch=main"
            echo "[INFO] ${CMD}"
            $CMD
            rc=$?
    
            # tag application descriptor file
            if [ $rc -eq 0 ]; then
        
                CMD="chtag -c IBM-1047 -t $applicationDir.yaml"
                echo "[INFO] ${CMD}"
                $CMD
                rc=$?
            fi
    
            # tag .gitattributes file
            if [ $rc -eq 0 ]; then
        
                CMD="rm .gitattributes"
                echo "[INFO] ${CMD}"
                $CMD
                rc=$?
        
                CMD="cp $DBB_MODELER_DEFAULT_GIT_CONFIG/.gitattributes .gitattributes"
                echo "[INFO] ${CMD}"
                $CMD
                rc=$?
            fi
                
            # Git list all changes
            if [ $rc -eq 0 ]; then
                echo "[INFO] Git status"
                CMD="git status"
                echo "[INFO] ${CMD}"
                $CMD
                rc=$?
            fi
        
            # Git add all changes
            if [ $rc -eq 0 ]; then
                CMD="git add --all"
                echo "[INFO] ${CMD}"
                $CMD
                rc=$?
            fi
        
            # Git commit changes
            if [ $rc -eq 0 ]; then
                echo "[INFO] Commit files to new Git repository"
                CMD="git commit -m 'Initial Commit'"
                echo "[INFO] ${CMD}"
                git commit -m 'Initial Commit'
                rc=$?
            fi
        
            # Git commit changes
            if [ $rc -eq 0 ]; then
                echo "[INFO] Create rel-1.0.0 tag"
                git tag rel-1.0.0
            fi
         fi #  
    done

    # Run zAppBuild to generate baselines
    cd $DBB_MODELER_APPLICATIONS
    echo "*******************************************************************"
    echo "[STEP] Run preview builds for applications"
    echo "*******************************************************************"
    
    for applicationDir in `ls | grep -v dbb-zappbuild`
    do
        echo "[APPLICATION] Run preview build for application $applicationDir"
        CMD="$DBB_HOME/bin/groovyz /var/dbb/dbb-zappbuild_300/build.groovy \
--workspace $DBB_MODELER_APPLICATIONS/$applicationDir \
--application $applicationDir \
--outDir $DBB_MODELER_LOGS/$applicationDir \
--fullBuild --preview \
--hlq IBMUSER.DBB.PREVIEW \
--logEncoding UTF-8 \
--propFiles /var/dbb/dbb-zappbuild-config/build.properties,/var/dbb/dbb-zappbuild-config/datasets.properties,/var/dbb/dbb-zappbuild-config/buildFolder.properties \
            "
        echo "[INFO] $CMD"
        $CMD "$@" > $DBB_MODELER_LOGS/$applicationDir/build-preview-$applicationDir.log
        
        echo "[INFO] Build console available at $DBB_MODELER_LOGS/$applicationDir/build-preview-$applicationDir.log"
        echo "[INFO] Build reports available at $DBB_MODELER_LOGS/$applicationDir"
    done    
fi
