callExtractApplications() {
    
    if [ ! -d $DBB_MODELER_APPCONFIG_DIR ]; then
      mkdir -p $DBB_MODELER_APPCONFIG_DIR
    fi
    CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/src/groovy/extractApplications.groovy \
          -d $APPLICATION_DATASETS \
          --applicationsMapping $APPLICATION_MAPPING_FILE \
          --repositoryPathsMapping $REPOSITORY_PATH_MAPPING_FILE \
          --types $APPLICATION_MEMBER_TYPE_MAPPING \
          -oc $DBB_MODELER_APPCONFIG_DIR \
          -oa $DBB_MODELER_APPLICATION_DIR \
          -l $DBB_MODELER_LOGS/1-extractApplications.log
      "
    echo "${CMD}"
    $CMD
   
}

callRunMigrations() {

    if [ -d $DBB_MODELER_APPLICATION_DIR ] 
    then
        rm -rf $DBB_MODELER_APPLICATION_DIR
    fi

    cd $DBB_MODELER_APPCONFIG_DIR
    for mappingFile in `ls *.mapping`
    do
        application=`echo $mappingFile | awk -F. '{ print $1 }'`
        echo "***** Running the DBB Migration Utility for application $application using file $mappingFile *****"
        mkdir -p $DBB_MODELER_APPLICATION_DIR/$application
        cd $DBB_MODELER_APPLICATION_DIR/$application
        CMD="$DBB_HOME/bin/groovyz $DBB_HOME/migration/bin/migrate.groovy -l $DBB_MODELER_LOGS/2-$application.migration.log -np info -r $DBB_MODELER_APPLICATION_DIR/$application $DBB_MODELER_APPCONFIG_DIR/$mappingFile"
        echo "${CMD}"
        $CMD
    done
 }
 
 callClassificationAssessment() {

    # Build Metadatastore
    if [ -d $DBB_MODELER_METADATA_STORE_DIR ] 
    then
        rm -rf $DBB_MODELER_METADATA_STORE_DIR
    fi

    if [ ! -d $DBB_MODELER_METADATA_STORE_DIR ] 
    then
        mkdir -p $DBB_MODELER_METADATA_STORE_DIR
    fi

    # Scan files
    cd $DBB_MODELER_APPLICATION_DIR
    for applicationDir in `ls | grep -v dbb-zappbuild`
    do
        echo "*******************************************************************"
        echo "Scan application directory $DBB_MODELER_APPLICATION_DIR/$applicationDir"
        echo "*******************************************************************"
        CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/src/groovy/scanApplication.groovy \
            -w $DBB_MODELER_APPLICATION_DIR \
            -a $applicationDir \
            -m $DBB_MODELER_METADATA_STORE_DIR \
            -l $DBB_MODELER_LOGS/3-$applicationDir-scan.log"    
        echo "${CMD}"
        $CMD
    done

    cd $DBB_MODELER_APPLICATION_DIR
    for applicationDir in `ls | grep -v dbb-zappbuild`
    do
        echo "*******************************************************************"
        echo "Assess Include files & Programs usage for $applicationDir"
        echo "*******************************************************************"
        CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/src/groovy/assessUsage.groovy \
            --workspace $DBB_MODELER_APPLICATION_DIR \
            --configurations $DBB_MODELER_APPCONFIG_DIR \
            --metadatastore $DBB_MODELER_METADATA_STORE_DIR \
            --application $applicationDir \
            --moveFiles \
            --logFile $DBB_MODELER_LOGS/3-$applicationDir-assessUsage.log"
        echo "${CMD}"
        $CMD
    done
    }
    
callPropertyGeneration() {
 
    cd $DBB_MODELER_APPLICATION_DIR
    for applicationDir in `ls | grep -v dbb-zappbuild`
    do
        echo "*******************************************************************"
        echo "Generate properties for application '$applicationDir'"
        echo "*******************************************************************"
        CMD="$DBB_HOME/bin/groovyz $DBB_MODELER_HOME/src/groovy/generateProperties.groovy \
            --workspace $DBB_MODELER_APPLICATION_DIR \
            --application $applicationDir \
            --zAppBuild $DBB_ZAPPBUILD \
            --typesConfigurations $TYPE_CONFIGURATIONS_FILE \
            --logFile $DBB_MODELER_LOGS/4-$applicationDir-generateProperties.log"
        echo "${CMD}"
        $CMD
    done
}