# Working with the DBB Git Migration Modeler utility

In the sample walkthrough below, all COBOL programs files of all applications are stored in a library called `COBOL`. COBOL Include files are stored in the `COPYBOOK` library.

The DBB Git Migration Modeler utility is a set of shell scripts that are wrapping groovy scripts. The scripts are using DBB APIs and groovy APIs.

There are 2 primary command scripts located in the [src/scripts subfolder](../src/scripts) :

* The [Migration-Modeler-Start script](../src/scripts/Migration-Modeler-Start.sh) facilitates the assignment of source files to applications, the migration of source files to USS folders, the usage assessment of include files and submodules,  the generation of build configurations, and the initialization of Git repositories.
* The [Refresh-Application-Descriptor-Files script](../src/scripts/Refresh-Application-Descriptor-Files.sh) is used to re-create Application Descriptors for existing applications that are already managed in Git.

The below sections explain these two primary scripts.

## The Migration-Modeler-Start script

To facilitate the extraction, migration, classification, generation of build configuration and initialization of Git repositories, a sample script, called the [Migration-Modeler-Start script](../src/scripts/Migration-Modeler-Start.sh), is provided to guide the user through the multiple steps of the process.

This script is invoked with the path to the DBB Git Migration Modeler configuration file passed as a parameter.
The DBB Git Migration Modeler configuration file contains the input parameters to the process.

Each stage of the process is represented by specific scripts under the covers. The Migration-Modeler-Start script calls the individual scripts in sequence, passing them the path to the DBB Git Migration Modeler configuration file via the `-c` parameter. We recommend to execute the `Migration-Modeler-Start.sh` script.

For reference, the following list is a description of the scripts called by the `Migration-Modeler-Start.sh` script:

1. [Extract Applications script (1-extractApplication.sh)](../src/scripts/utils/1-extractApplications.sh): this script scans the content of the provided datasets and assesses each member based on the applications' naming conventions defined in Applications Mapping files.
For each member found, it searches in the *Applications Mapping* YAML file if a naming convention, applied as a filter, matches the member name:
   * If it's a match, the member is assigned to the application that owns the matching naming convention.
   * If no convention is matching, the member is assigned to the *UNASSIGNED* application.
   * **Outputs**: After the execution of this script, the `DBB_MODELER_APPCONFIG_DIR` folder contains 2 files per application found:
      * An initial Application Descriptor file.
      * A DBB Migration mapping file depending on the definitions in the *Repository Paths* mapping file.

2. [Run Migrations script (2-runMigrations.sh)](../src/scripts/utils/2-runMigrations.sh): this script executes the DBB Migration utility for each application with the generated DBB Migration Mapping files created by the previous step.
It will copy all the files assigned to the given applications' subfolders. Unassigned members are migrated into an *UNASSIGNED* application.
The outcome of this script are subfolders created in the `DBB_MODELER_APPLICATION_DIR` folder for each application. A benefit from this step is the documentation about non-roundtripable and non-printable characters for each application. 

3. [Classification script (3-classify.sh)](../src/scripts/utils/3-classify.sh): this script scans the source code and performs the classification process. It calls two groovy scripts ([scanApplication.groovy](../src/groovy/scanApplication.groovy) and [assessUsage.groovy](../src/groovy/assessUsage.groovy)) to respectively scans the content of each files of the applications using the DBB scanner, and assesses how Include Files and Programs are used by all the applications.
   * For the scanning phase, the script iterates through the list of identified applications, and uses the DBB scanner to understand the dependencies for each artifact.
   This information is stored in the DBB Metadatastore that holds the dependencies information.

   * The second phase of the process uses this Metadata information to understand how Include Files and Programs are used across all applications and classify the Include Files in three categories (Private, Public or Shared) and Programs in three categories ("main", "internal submodule", "service submodule").
   Depending on the results of this assessment, Include Files may be moved from one application to another, Programs are not subject to move.

   * **Outputs**
      * The Application Descriptor file for each application is updated to reflect the findings of this step, and stored in the application's subfolder located in the `DBB_MODELER_APPLICATION_DIR` folder (if not already present).
      As it contains additional details, we refer to is as the final Application Descriptor file.
      * The DBB Migration mapping file is also updated accordingly, if files were moved from an owning application to another. 

4. [Property Generation script (4-generateProperties.sh)](../src/scripts/utils/4-generateProperties.sh): this script generates build properties for [DBB zBuilder](https://www.ibm.com/docs/en/adffz/dbb/3.0.x?topic=building-zos-applications-zbuilder) or [dbb-zAppBuild](https://github.com/IBM/dbb-zappbuild/), depending on the chosen configuration. This step is optional, but it is highly encouraged to leverage the automatic generation of build properties, to facilitate the migration to Git.  
The script uses the type of each artifact to generate (or reuse if already existing) Language Configurations, as configured in the [Types Configurations file](../samples/typesConfigurations.yaml).
   * **Outputs**
      * When using *DBB zBuilder*:
         * These Language Configurations files are turned into DBB zBuilder's Language Configuration definition files, placed into a `config` subfolder in each Git repository.  
         * A `dbb-app.yaml` file is created within each repository's folder in the `DBB_MODELER_APPLICATION_DIR` folder, and contains statements to enable the use of Language Configurations. The generated `dbb-app.yaml` file also contains statements to enable impact analysis and dependency search paths for common types of artifact (Cobol, Assembler and LinkEdit artifacts). Additional manual configuration might be required for other types of artifacts.
      * When using *dbb-zAppBuild*:
         * These Language Configurations files are placed into a copy of the *dbb-zAppBuild* instance pointed by the `DBB_ZAPPBUILD` variable, the copy being stored in the `DBB_MODELER_APPLICATION_DIR` folder.  
         * An **application-conf** folder is created within each application's subfolder in the `DBB_MODELER_APPLICATION_DIR` folder, and contains customized files to enable the use of the Language Configurations. A manual step needs to be performed to completely enable this configuration.

5. [Init Application Repositories script (5-initApplicationRepositories.sh)](../src/scripts/utils/5-initApplicationRepositories.sh) is provided to perform the following steps for each application:
   1. Initialization of the Git repository using a default `.gitattributes` file, creation of a customized `zapp.yaml` file, creation of a customized `.project` file, creation of a `baselineReference.config` file, copy of the pipeline definitions, creation of a baseline tag and commit of the changes,
   2. Execution of a full build with dbb-zAppBuild, using the preview option (no file is actually built) as a preview of the expected outcomes,
   3. Creation of a baseline package using the `PackageBuildOutputs.groovy` script based on the preview build report. The purpose of this step is to package the existing build artifacts (load modules, DBRMs, jobs, etc.) that correspond to the version of the migrated source code files.

An additional parameter can be passed to these five scripts and to the [Migration-Modeler-Start script](../src/scripts/Migration-Modeler-Start.sh) to specify a list of applications.
This list can be used to filter down the applications to process during the migration. Through the `-a` parameter, the user can specify a comma-separated list of applications, for which the migration will be performed. When provided to the [Migration-Modeler-Start script](../src/scripts/Migration-Modeler-Start.sh), this list is conveyed to the subsequent scripts and used throughout the process.
This parameter can be used to limit the scope of the migration to applications that are ready to be migrated, even if many others applications are defined in the Applications Mappings files. This filtering capability can help in a phased migration approach, to successively target individual applications.

### Extracting members from datasets into applications

The [Extract Applications script (1-extractApplication.sh)](../src/scripts/utils/1-extractApplications.sh) requires the path to the DBB Git Migration Modeler configuration file.

The use of the DBB Scanner (controlled via `SCAN_DATASET_MEMBERS` variable) can be used to automatically identify the language and type of a file (Cobol, PLI, etc...). When enabled, each file is scanned to identify its language and file type, and these criteria are used first when identifying which *repository path* the file should be assigned to.
When disabled, types and low-level qualifiers of the containing dataset are used, in this order.

<details>
  <summary>Output example</summary>
Execution of the command:

```
./src/scripts/utils/1-extractApplications.sh -c /u/ibmuser/dbb-git-migration-modeler-work/DBB_GIT_MIGRATION_MODELER.config
```

Output log:
~~~~  
[INFO] /usr/lpp/dbb/v3r0/bin/groovyz /u/mdalbin/Migration-Modeler-MDLB/src/groovy/extractApplications.groovy        --configFile /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-01-15.175657.config        --logFile /u/mdalbin/Migration-Modeler-MDLB-work/logs/1-extractApplications.log
2026-01-15 17:12:55.988 ** Script configuration:
2026-01-15 17:12:55.989     DBB_MODELER_APPCONFIG_DIR -> /u/mdalbin/Migration-Modeler-MDLB-work/work/migration-configuration
2026-01-15 17:12:55.989     REPOSITORY_PATH_MAPPING_FILE -> /u/mdalbin/Migration-Modeler-MDLB-work/config/repositoryPathsMapping.yaml
2026-01-15 17:12:55.989     configurationFilePath -> /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-01-15.175657.config
2026-01-15 17:12:55.990     DBB_MODELER_APPLICATION_DIR -> /u/mdalbin/Migration-Modeler-MDLB-work/repositories
2026-01-15 17:12:55.990     logFile -> /u/mdalbin/Migration-Modeler-MDLB-work/logs/1-extractApplications.log
2026-01-15 17:12:55.991     DBB_MODELER_APPMAPPINGS_DIR -> /u/mdalbin/Migration-Modeler-MDLB-work/config/applications-mappings
2026-01-15 17:12:55.991     SCAN_DATASET_MEMBERS_ENCODING -> IBM-1047
2026-01-15 17:12:55.992     APPLICATION_TYPES_MAPPING -> /u/mdalbin/Migration-Modeler-MDLB-work/config/types/typesMapping.yaml
2026-01-15 17:12:55.992     SCAN_DATASET_MEMBERS -> false
2026-01-15 17:12:55.993 ** Reading the Repository Layout Mapping definition.
2026-01-15 17:12:55.997 ** Reading the Type Mapping definition.
2026-01-15 17:12:55.999 ** Loading the provided Applications Mapping files.
2026-01-15 17:12:56.003 *** Importing 'applicationsMapping.yaml'
2026-01-15 17:12:56.023 ** Iterating through the provided datasets and mapped applications.
2026-01-15 17:12:56.036 **** Found 'DBEHM.MIG.BMS' referenced by applications 'RetirementCalculator', 'GenApp', 'CBSA' 
2026-01-15 17:12:56.110 ***** 'DBEHM.MIG.BMS(EPSMLIS)' - Mapped Application: UNASSIGNED
2026-01-15 17:12:56.162 ***** 'DBEHM.MIG.BMS(EPSMORT)' - Mapped Application: UNASSIGNED
2026-01-15 17:12:56.201 ***** 'DBEHM.MIG.BMS(SSMAP)' - Mapped Application: GenApp
2026-01-15 17:12:56.209 **** Found 'DBEHM.MIG.COPY' referenced by applications 'RetirementCalculator', 'GenApp', 'CBSA' 
2026-01-15 17:12:56.225 ***** 'DBEHM.MIG.COPY(ABNDINFO)' - Mapped Application: CBSA
2026-01-15 17:12:56.239 ***** 'DBEHM.MIG.COPY(ACCDB2)' - Mapped Application: CBSA
2026-01-15 17:12:56.251 ***** 'DBEHM.MIG.COPY(ACCOUNT)' - Mapped Application: CBSA
2026-01-15 17:12:56.263 ***** 'DBEHM.MIG.COPY(ACCTCTRL)' - Mapped Application: CBSA
2026-01-15 17:12:56.276 ***** 'DBEHM.MIG.COPY(BNK1ACC)' - Mapped Application: CBSA
2026-01-15 17:12:56.284 ***** 'DBEHM.MIG.COPY(BNK1CAM)' - Mapped Application: CBSA
2026-01-15 17:12:56.291 ***** 'DBEHM.MIG.COPY(BNK1CCM)' - Mapped Application: CBSA
2026-01-15 17:12:56.296 ***** 'DBEHM.MIG.COPY(BNK1CDM)' - Mapped Application: CBSA
2026-01-15 17:12:56.303 ***** 'DBEHM.MIG.COPY(BNK1DAM)' - Mapped Application: CBSA
2026-01-15 17:12:56.308 ***** 'DBEHM.MIG.COPY(BNK1DCM)' - Mapped Application: CBSA
2026-01-15 17:12:56.311 ***** 'DBEHM.MIG.COPY(BNK1MAI)' - Mapped Application: CBSA
2026-01-15 17:12:56.315 ***** 'DBEHM.MIG.COPY(BNK1TFM)' - Mapped Application: CBSA
2026-01-15 17:12:56.318 ***** 'DBEHM.MIG.COPY(BNK1UAM)' - Mapped Application: CBSA
2026-01-15 17:12:56.321 ***** 'DBEHM.MIG.COPY(CONSENT)' - Mapped Application: CBSA
2026-01-15 17:12:56.325 ***** 'DBEHM.MIG.COPY(CONSTAPI)' - Mapped Application: CBSA
2026-01-15 17:12:56.328 ***** 'DBEHM.MIG.COPY(CONSTDB2)' - Mapped Application: CBSA
2026-01-15 17:12:56.332 ***** 'DBEHM.MIG.COPY(CONTDB2)' - Mapped Application: CBSA
2026-01-15 17:12:56.335 ***** 'DBEHM.MIG.COPY(CREACC)' - Mapped Application: CBSA
2026-01-15 17:12:56.338 ***** 'DBEHM.MIG.COPY(CRECUST)' - Mapped Application: CBSA
2026-01-15 17:12:56.341 ***** 'DBEHM.MIG.COPY(CUSTCTRL)' - Mapped Application: CBSA
2026-01-15 17:12:56.344 ***** 'DBEHM.MIG.COPY(CUSTOMER)' - Mapped Application: CBSA
2026-01-15 17:12:56.348 ***** 'DBEHM.MIG.COPY(DATASTR)' - Mapped Application: UNASSIGNED
2026-01-15 17:12:56.351 ***** 'DBEHM.MIG.COPY(DELACC)' - Mapped Application: CBSA
2026-01-15 17:12:56.354 ***** 'DBEHM.MIG.COPY(DELCUS)' - Mapped Application: CBSA
2026-01-15 17:12:56.357 ***** 'DBEHM.MIG.COPY(GETCOMPY)' - Mapped Application: CBSA
2026-01-15 17:12:56.359 ***** 'DBEHM.MIG.COPY(GETSCODE)' - Mapped Application: CBSA
2026-01-15 17:12:56.362 ***** 'DBEHM.MIG.COPY(INQACC)' - Mapped Application: CBSA
2026-01-15 17:12:56.364 ***** 'DBEHM.MIG.COPY(INQACCCU)' - Mapped Application: CBSA
2026-01-15 17:12:56.367 ***** 'DBEHM.MIG.COPY(INQCUST)' - Mapped Application: CBSA
2026-01-15 17:12:56.370 ***** 'DBEHM.MIG.COPY(LGCMAREA)' - Mapped Application: GenApp
2026-01-15 17:12:56.373 ***** 'DBEHM.MIG.COPY(LGCMARED)' - Mapped Application: GenApp
2026-01-15 17:12:56.375 ***** 'DBEHM.MIG.COPY(LGPOLICY)' - Mapped Application: GenApp
2026-01-15 17:12:56.377 ***** 'DBEHM.MIG.COPY(LINPUT)' - Mapped Application: RetirementCalculator
2026-01-15 17:12:56.379 ***** 'DBEHM.MIG.COPY(PAYDBCR)' - Mapped Application: UNASSIGNED
2026-01-15 17:12:56.381 ***** 'DBEHM.MIG.COPY(PROCDB2)' - Mapped Application: CBSA
2026-01-15 17:12:56.382 ***** 'DBEHM.MIG.COPY(PROCTRAN)' - Mapped Application: CBSA
2026-01-15 17:12:56.385 ***** 'DBEHM.MIG.COPY(SORTCODE)' - Mapped Application: UNASSIGNED
2026-01-15 17:12:56.387 ***** 'DBEHM.MIG.COPY(UPDACC)' - Mapped Application: CBSA
2026-01-15 17:12:56.389 ***** 'DBEHM.MIG.COPY(UPDCUST)' - Mapped Application: CBSA
2026-01-15 17:12:56.391 ***** 'DBEHM.MIG.COPY(XFRFUN)' - Mapped Application: CBSA
2026-01-15 17:12:56.393 **** Found 'DBEHM.MIG.COBOL' referenced by applications 'RetirementCalculator', 'GenApp', 'CBSA' 
2026-01-15 17:12:56.398 ***** 'DBEHM.MIG.COBOL(ABNDPROC)' - Mapped Application: CBSA
2026-01-15 17:12:56.399 ***** 'DBEHM.MIG.COBOL(ACCLOAD)' - Mapped Application: CBSA
2026-01-15 17:12:56.400 ***** 'DBEHM.MIG.COBOL(ACCOFFL)' - Mapped Application: CBSA
2026-01-15 17:12:56.401 ***** 'DBEHM.MIG.COBOL(ACCTCTRL)' - Mapped Application: CBSA
2026-01-15 17:12:56.402 ***** 'DBEHM.MIG.COBOL(BANKDATA)' - Mapped Application: CBSA
2026-01-15 17:12:56.403 ***** 'DBEHM.MIG.COBOL(BNKMENU)' - Mapped Application: CBSA
2026-01-15 17:12:56.404 ***** 'DBEHM.MIG.COBOL(BNK1CAC)' - Mapped Application: CBSA
2026-01-15 17:12:56.405 ***** 'DBEHM.MIG.COBOL(BNK1CCA)' - Mapped Application: CBSA
2026-01-15 17:12:56.406 ***** 'DBEHM.MIG.COBOL(BNK1CCS)' - Mapped Application: CBSA
2026-01-15 17:12:56.407 ***** 'DBEHM.MIG.COBOL(BNK1CRA)' - Mapped Application: CBSA
2026-01-15 17:12:56.408 ***** 'DBEHM.MIG.COBOL(BNK1DAC)' - Mapped Application: CBSA
2026-01-15 17:12:56.409 ***** 'DBEHM.MIG.COBOL(BNK1DCS)' - Mapped Application: CBSA
2026-01-15 17:12:56.410 ***** 'DBEHM.MIG.COBOL(BNK1TFN)' - Mapped Application: CBSA
2026-01-15 17:12:56.411 ***** 'DBEHM.MIG.COBOL(BNK1UAC)' - Mapped Application: CBSA
2026-01-15 17:12:56.412 ***** 'DBEHM.MIG.COBOL(CONSENT)' - Mapped Application: CBSA
2026-01-15 17:12:56.413 ***** 'DBEHM.MIG.COBOL(CONSTTST)' - Mapped Application: CBSA
2026-01-15 17:12:56.414 ***** 'DBEHM.MIG.COBOL(CRDTAGY1)' - Mapped Application: CBSA
2026-01-15 17:12:56.415 ***** 'DBEHM.MIG.COBOL(CRDTAGY2)' - Mapped Application: CBSA
2026-01-15 17:12:56.416 ***** 'DBEHM.MIG.COBOL(CRDTAGY3)' - Mapped Application: CBSA
2026-01-15 17:12:56.417 ***** 'DBEHM.MIG.COBOL(CRDTAGY4)' - Mapped Application: CBSA
2026-01-15 17:12:56.418 ***** 'DBEHM.MIG.COBOL(CRDTAGY5)' - Mapped Application: CBSA
2026-01-15 17:12:56.419 ***** 'DBEHM.MIG.COBOL(CREACC)' - Mapped Application: CBSA
2026-01-15 17:12:56.420 ***** 'DBEHM.MIG.COBOL(CRECUST)' - Mapped Application: CBSA
2026-01-15 17:12:56.421 ***** 'DBEHM.MIG.COBOL(CUSTCTRL)' - Mapped Application: CBSA
2026-01-15 17:12:56.422 ***** 'DBEHM.MIG.COBOL(DBCRFUN)' - Mapped Application: CBSA
2026-01-15 17:12:56.423 ***** 'DBEHM.MIG.COBOL(DELACC)' - Mapped Application: CBSA
2026-01-15 17:12:56.424 ***** 'DBEHM.MIG.COBOL(DELCUS)' - Mapped Application: CBSA
2026-01-15 17:12:56.425 ***** 'DBEHM.MIG.COBOL(DPAYAPI)' - Mapped Application: CBSA
2026-01-15 17:12:56.426 ***** 'DBEHM.MIG.COBOL(DPAYTST)' - Mapped Application: CBSA
2026-01-15 17:12:56.427 ***** 'DBEHM.MIG.COBOL(EBUD0RUN)' - Mapped Application: RetirementCalculator
2026-01-15 17:12:56.429 ***** 'DBEHM.MIG.COBOL(EBUD01)' - Mapped Application: RetirementCalculator
2026-01-15 17:12:56.430 ***** 'DBEHM.MIG.COBOL(EBUD02)' - Mapped Application: RetirementCalculator
2026-01-15 17:12:56.431 ***** 'DBEHM.MIG.COBOL(EBUD03)' - Mapped Application: RetirementCalculator
2026-01-15 17:12:56.433 ***** 'DBEHM.MIG.COBOL(GETCOMPY)' - Mapped Application: CBSA
2026-01-15 17:12:56.434 ***** 'DBEHM.MIG.COBOL(GETSCODE)' - Mapped Application: CBSA
2026-01-15 17:12:56.435 ***** 'DBEHM.MIG.COBOL(INQACC)' - Mapped Application: CBSA
2026-01-15 17:12:56.436 ***** 'DBEHM.MIG.COBOL(INQACCCU)' - Mapped Application: CBSA
2026-01-15 17:12:56.437 ***** 'DBEHM.MIG.COBOL(INQCUST)' - Mapped Application: CBSA
2026-01-15 17:12:56.438 ***** 'DBEHM.MIG.COBOL(LGACDB01)' - Mapped Application: GenApp
2026-01-15 17:12:56.439 ***** 'DBEHM.MIG.COBOL(LGACDB02)' - Mapped Application: GenApp
2026-01-15 17:12:56.440 ***** 'DBEHM.MIG.COBOL(LGACUS01)' - Mapped Application: GenApp
2026-01-15 17:12:56.441 ***** 'DBEHM.MIG.COBOL(LGACVS01)' - Mapped Application: GenApp
2026-01-15 17:12:56.442 ***** 'DBEHM.MIG.COBOL(LGAPDB01)' - Mapped Application: GenApp
2026-01-15 17:12:56.443 ***** 'DBEHM.MIG.COBOL(LGAPOL01)' - Mapped Application: GenApp
2026-01-15 17:12:56.444 ***** 'DBEHM.MIG.COBOL(LGAPVS01)' - Mapped Application: GenApp
2026-01-15 17:12:56.445 ***** 'DBEHM.MIG.COBOL(LGASTAT1)' - Mapped Application: GenApp
2026-01-15 17:12:56.446 ***** 'DBEHM.MIG.COBOL(LGDPDB01)' - Mapped Application: GenApp
2026-01-15 17:12:56.447 ***** 'DBEHM.MIG.COBOL(LGDPOL01)' - Mapped Application: GenApp
2026-01-15 17:12:56.448 ***** 'DBEHM.MIG.COBOL(LGDPVS01)' - Mapped Application: GenApp
2026-01-15 17:12:56.449 ***** 'DBEHM.MIG.COBOL(LGICDB01)' - Mapped Application: GenApp
2026-01-15 17:12:56.450 ***** 'DBEHM.MIG.COBOL(LGICUS01)' - Mapped Application: GenApp
2026-01-15 17:12:56.451 ***** 'DBEHM.MIG.COBOL(LGICVS01)' - Mapped Application: GenApp
2026-01-15 17:12:56.452 ***** 'DBEHM.MIG.COBOL(LGIPDB01)' - Mapped Application: GenApp
2026-01-15 17:12:56.453 ***** 'DBEHM.MIG.COBOL(LGIPOL01)' - Mapped Application: GenApp
2026-01-15 17:12:56.454 ***** 'DBEHM.MIG.COBOL(LGIPVS01)' - Mapped Application: GenApp
2026-01-15 17:12:56.455 ***** 'DBEHM.MIG.COBOL(LGSETUP)' - Mapped Application: GenApp
2026-01-15 17:12:56.456 ***** 'DBEHM.MIG.COBOL(LGSTSQ)' - Mapped Application: GenApp
2026-01-15 17:12:56.457 ***** 'DBEHM.MIG.COBOL(LGTESTC1)' - Mapped Application: GenApp
2026-01-15 17:12:56.458 ***** 'DBEHM.MIG.COBOL(LGTESTP1)' - Mapped Application: GenApp
2026-01-15 17:12:56.459 ***** 'DBEHM.MIG.COBOL(LGTESTP2)' - Mapped Application: GenApp
2026-01-15 17:12:56.460 ***** 'DBEHM.MIG.COBOL(LGTESTP3)' - Mapped Application: GenApp
2026-01-15 17:12:56.461 ***** 'DBEHM.MIG.COBOL(LGTESTP4)' - Mapped Application: GenApp
2026-01-15 17:12:56.462 ***** 'DBEHM.MIG.COBOL(LGUCDB01)' - Mapped Application: GenApp
2026-01-15 17:12:56.463 ***** 'DBEHM.MIG.COBOL(LGUCUS01)' - Mapped Application: GenApp
2026-01-15 17:12:56.464 ***** 'DBEHM.MIG.COBOL(LGUCVS01)' - Mapped Application: GenApp
2026-01-15 17:12:56.465 ***** 'DBEHM.MIG.COBOL(LGUPDB01)' - Mapped Application: GenApp
2026-01-15 17:12:56.466 ***** 'DBEHM.MIG.COBOL(LGUPOL01)' - Mapped Application: GenApp
2026-01-15 17:12:56.467 ***** 'DBEHM.MIG.COBOL(LGUPVS01)' - Mapped Application: GenApp
2026-01-15 17:12:56.468 ***** 'DBEHM.MIG.COBOL(LGWEBST5)' - Mapped Application: GenApp
2026-01-15 17:12:56.469 ***** 'DBEHM.MIG.COBOL(OLDACDB1)' - Mapped Application: UNASSIGNED
2026-01-15 17:12:56.470 ***** 'DBEHM.MIG.COBOL(OLDACDB2)' - Mapped Application: UNASSIGNED
2026-01-15 17:12:56.471 ***** 'DBEHM.MIG.COBOL(PROLOAD)' - Mapped Application: CBSA
2026-01-15 17:12:56.472 ***** 'DBEHM.MIG.COBOL(PROOFFL)' - Mapped Application: CBSA
2026-01-15 17:12:56.473 ***** 'DBEHM.MIG.COBOL(UPDACC)' - Mapped Application: CBSA
2026-01-15 17:12:56.474 ***** 'DBEHM.MIG.COBOL(UPDCUST)' - Mapped Application: CBSA
2026-01-15 17:12:56.475 ***** 'DBEHM.MIG.COBOL(XFRFUN)' - Mapped Application: CBSA
2026-01-15 17:12:56.481 ** Generating Applications Configurations files.
2026-01-15 17:12:56.483 ** Generating Configuration files for Application: RetirementCalculator
2026-01-15 17:12:56.559     Created DBB Migration Utility mapping file /u/mdalbin/Migration-Modeler-MDLB-work/work/migration-configuration/RetirementCalculator.mapping
2026-01-15 17:12:56.575     Created/Updated Application Description file /u/mdalbin/Migration-Modeler-MDLB-work/repositories/RetirementCalculator/applicationDescriptor.yml
2026-01-15 17:12:56.649     Estimated storage size of migrated members: 12,838 bytes
2026-01-15 17:12:56.650 ** Generating Configuration files for Application: GenApp
2026-01-15 17:12:56.691     Created DBB Migration Utility mapping file /u/mdalbin/Migration-Modeler-MDLB-work/work/migration-configuration/GenApp.mapping
2026-01-15 17:12:56.708     Created/Updated Application Description file /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/applicationDescriptor.yml
2026-01-15 17:12:56.908     Estimated storage size of migrated members: 463,749 bytes
2026-01-15 17:12:56.908 ** Generating Configuration files for Application: UNASSIGNED
2026-01-15 17:12:56.916     Created DBB Migration Utility mapping file /u/mdalbin/Migration-Modeler-MDLB-work/work/migration-configuration/UNASSIGNED.mapping
2026-01-15 17:12:56.921     Created/Updated Application Description file /u/mdalbin/Migration-Modeler-MDLB-work/repositories/UNASSIGNED/applicationDescriptor.yml
2026-01-15 17:12:56.956     Estimated storage size of migrated members: 36,244 bytes
2026-01-15 17:12:56.956 ** Generating Configuration files for Application: CBSA
2026-01-15 17:12:56.991     Created DBB Migration Utility mapping file /u/mdalbin/Migration-Modeler-MDLB-work/work/migration-configuration/CBSA.mapping
2026-01-15 17:12:57.008     Created/Updated Application Description file /u/mdalbin/Migration-Modeler-MDLB-work/repositories/CBSA/applicationDescriptor.yml
2026-01-15 17:12:57.380     Estimated storage size of migrated members: 1,147,571 bytes
2026-01-15 17:12:57.381 ** Estimated storage size of all migrated members: 1,660,402 bytes
~~~~
</details>

### Migrating the members from MVS datasets to USS folders

The [Run Migrations script (2-runMigrations.sh)](../src/scripts/utils/2-runMigrations.sh) only requires the path to the DBB Git Migration Modeler Configuration file as parameter, to locate the work directories (controlled via `DBB_MODELER_APPLICATION_DIR`).
It will search for all the DBB Migration mapping files located in the *work-configs* directory, and will process them in sequence.

<details>
  <summary>Output example for a single application (CBSA)</summary>
Execution of the command:

`./src/scripts/utils/2-runMigrations.sh -c /u/ibmuser/dbb-git-migration-modeler-work/DBB_GIT_MIGRATION_MODELER.config`

Output log:  
~~~~
[INFO] /usr/lpp/dbb/v3r0/bin/groovyz /usr/lpp/dbb/v3r0/migration/bin/migrate.groovy -l /u/mdalbin/Migration-Modeler-MDLB-work/logs/2-GenApp.migration.log -le UTF-8 -np info -r /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp /u/mdalbin/Migration-Modeler-MDLB-work/work/migration-configuration/GenApp.mapping
Messages will be saved in '/u/mdalbin/Migration-Modeler-MDLB-work/logs/2-GenApp.migration.log' with encoding 'UTF-8'
Non-printable scan level is info
Local GIT repository: /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp
Migrate data sets using mapping file /u/mdalbin/Migration-Modeler-MDLB-work/work/migration-configuration/GenApp.mapping
Copying [DBEHM.MIG.COBOL, LGAPOL01] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgapol01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGTESTC1] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgtestc1.cbl using IBM-1047
Copying [DBEHM.MIG.BMS, SSMAP] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/bms/ssmap.bms using IBM-1047
Copying [DBEHM.MIG.COPY, LGCMARED] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/copy/lgcmared.cpy using IBM-1047
Copying [DBEHM.MIG.COBOL, LGACDB01] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgacdb01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGAPVS01] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgapvs01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGDPVS01] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgdpvs01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGTESTP3] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgtestp3.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGUCUS01] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgucus01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGASTAT1] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgastat1.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGICDB01] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgicdb01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGUPVS01] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgupvs01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGDPDB01] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgdpdb01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGUCVS01] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgucvs01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGIPDB01] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgipdb01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGTESTP2] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgtestp2.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGUPOL01] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgupol01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGSTSQ] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgstsq.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGACUS01] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgacus01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGSETUP] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgsetup.cbl using IBM-1047
Copying [DBEHM.MIG.COPY, LGCMAREA] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/copy/lgcmarea.cpy using IBM-1047
Copying [DBEHM.MIG.COBOL, LGACVS01] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgacvs01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGACDB02] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgacdb02.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGTESTP4] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgtestp4.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGICVS01] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgicvs01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGWEBST5] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgwebst5.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGAPDB01] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgapdb01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGDPOL01] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgdpol01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGIPVS01] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgipvs01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGUCDB01] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgucdb01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGTESTP1] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgtestp1.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGIPOL01] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgipol01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGICUS01] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgicus01.cbl using IBM-1047
Copying [DBEHM.MIG.COPY, LGPOLICY] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/copy/lgpolicy.cpy using IBM-1047
Copying [DBEHM.MIG.COBOL, LGUPDB01] to /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgupdb01.cbl using IBM-1047
~~~~
</details>

### Assessing the usage of Include Files and Programs

The [Classification script (3-classify.sh)](../src/scripts/utils/3-classify.sh) only requires the path to the DBB Git Migration Modeler Configuration file as parameter, to locate the work directories.

It will search for all DBB Migration mapping files located in the `DBB_MODELER_APPCONFIG_DIR` folder and will process applications' definitions found in this folder.
This script works in 2 phases:
1. The first phase is a scan of all the files found in the applications' subfolders,
2. The second phase is an analysis of how the different Include Files and Programs are used by all known applications. 

<details>
  <summary>Output example for a single application (CBSA)</summary>
Execution of the command:

`./src/scripts/utils/3-classify.sh -c /u/ibmuser/dbb-git-migration-modeler-work/DBB_GIT_MIGRATION_MODELER.config`

Output log:
~~~~
[INFO] /usr/lpp/dbb/v3r0/bin/groovyz /u/mdalbin/Migration-Modeler-MDLB/src/groovy/scanApplication.groovy                --configFile /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-01-15.175657.config                --application GenApp                --logFile /u/mdalbin/Migration-Modeler-MDLB-work/logs/3-GenApp-scan.log
2026-01-15 17:13:38.935 ** Script configuration:
2026-01-15 17:13:38.935     REPOSITORY_PATH_MAPPING_FILE -> /u/mdalbin/Migration-Modeler-MDLB-work/config/repositoryPathsMapping.yaml
2026-01-15 17:13:38.935     SCAN_CONTROL_TRANSFERS -> true
2026-01-15 17:13:38.935     application -> GenApp
2026-01-15 17:13:38.935     configurationFilePath -> /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-01-15.175657.config
2026-01-15 17:13:38.935     DBB_MODELER_APPLICATION_DIR -> /u/mdalbin/Migration-Modeler-MDLB-work/repositories
2026-01-15 17:13:38.936     DBB_MODELER_FILE_METADATA_STORE_DIR -> /u/mdalbin/Migration-Modeler-MDLB-work/work/dbb-filemetadatastore
2026-01-15 17:13:38.936     logFile -> /u/mdalbin/Migration-Modeler-MDLB-work/logs/3-GenApp-scan.log
2026-01-15 17:13:38.936     DBB_MODELER_METADATASTORE_TYPE -> file
2026-01-15 17:13:38.936     APPLICATION_DEFAULT_BRANCH -> main
2026-01-15 17:13:38.937 ** Reading the existing Application Descriptor file.
2026-01-15 17:13:38.940 ** Retrieving the list of files mapped to Source Groups.
2026-01-15 17:13:38.940 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/applicationDescriptor.yml'. Skipping.
2026-01-15 17:13:38.941 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.gitattributes'. Skipping.
2026-01-15 17:13:38.951 ** Scanning the files.
2026-01-15 17:13:38.951     Scanning file GenApp/GenApp/src/cobol/lgtestp2.cbl 
2026-01-15 17:13:38.959     Scanning file GenApp/GenApp/src/cobol/lgicus01.cbl 
2026-01-15 17:13:38.965     Scanning file GenApp/GenApp/src/cobol/lgucus01.cbl 
2026-01-15 17:13:38.972     Scanning file GenApp/GenApp/src/cobol/lgucvs01.cbl 
2026-01-15 17:13:38.977     Scanning file GenApp/GenApp/src/cobol/lgapdb01.cbl 
2026-01-15 17:13:38.987     Scanning file GenApp/GenApp/src/cobol/lgdpdb01.cbl 
2026-01-15 17:13:38.994     Scanning file GenApp/GenApp/src/cobol/lgicvs01.cbl 
2026-01-15 17:13:39.015     Scanning file GenApp/GenApp/src/copy/lgpolicy.cpy 
2026-01-15 17:13:39.035     Scanning file GenApp/GenApp/src/cobol/lgsetup.cbl 
2026-01-15 17:13:39.045     Scanning file GenApp/GenApp/src/copy/lgcmarea.cpy 
2026-01-15 17:13:39.062     Scanning file GenApp/GenApp/src/cobol/lgacdb01.cbl 
2026-01-15 17:13:39.069     Scanning file GenApp/GenApp/src/cobol/lgipdb01.cbl 
2026-01-15 17:13:39.085     Scanning file GenApp/GenApp/src/cobol/lgupvs01.cbl 
2026-01-15 17:13:39.092     Scanning file GenApp/GenApp/src/cobol/lgtestp1.cbl 
2026-01-15 17:13:39.099     Scanning file GenApp/GenApp/src/cobol/lgtestc1.cbl 
2026-01-15 17:13:39.107     Scanning file GenApp/GenApp/src/cobol/lgdpol01.cbl 
2026-01-15 17:13:39.113     Scanning file GenApp/GenApp/src/cobol/lgapol01.cbl 
2026-01-15 17:13:39.119     Scanning file GenApp/GenApp/src/bms/ssmap.bms 
2026-01-15 17:13:39.229     Scanning file GenApp/GenApp/src/copy/lgcmared.cpy 
2026-01-15 17:13:39.235     Scanning file GenApp/GenApp/src/cobol/lgucdb01.cbl 
2026-01-15 17:13:39.239     Scanning file GenApp/GenApp/src/cobol/lgacdb02.cbl 
2026-01-15 17:13:39.244     Scanning file GenApp/GenApp/src/cobol/lgipol01.cbl 
2026-01-15 17:13:39.248     Scanning file GenApp/GenApp/src/cobol/lgapvs01.cbl 
2026-01-15 17:13:39.253     Scanning file GenApp/GenApp/src/cobol/lgicdb01.cbl 
2026-01-15 17:13:39.258     Scanning file GenApp/GenApp/src/cobol/lgtestp4.cbl 
2026-01-15 17:13:39.263     Scanning file GenApp/GenApp/src/cobol/lgdpvs01.cbl 
2026-01-15 17:13:39.267     Scanning file GenApp/GenApp/src/cobol/lgupol01.cbl 
2026-01-15 17:13:39.272     Scanning file GenApp/GenApp/src/cobol/lgacvs01.cbl 
2026-01-15 17:13:39.276     Scanning file GenApp/GenApp/src/cobol/lgipvs01.cbl 
2026-01-15 17:13:39.281     Scanning file GenApp/GenApp/src/cobol/lgastat1.cbl 
2026-01-15 17:13:39.285     Scanning file GenApp/GenApp/src/cobol/lgacus01.cbl 
2026-01-15 17:13:39.289     Scanning file GenApp/GenApp/src/cobol/lgupdb01.cbl 
2026-01-15 17:13:39.296     Scanning file GenApp/GenApp/src/cobol/lgtestp3.cbl 
2026-01-15 17:13:39.301     Scanning file GenApp/GenApp/src/cobol/lgstsq.cbl 
2026-01-15 17:13:39.305     Scanning file GenApp/GenApp/src/cobol/lgwebst5.cbl 
2026-01-15 17:13:39.314 ** Storing results in the 'GenApp-main' DBB Collection.
[INFO] /usr/lpp/dbb/v3r0/bin/groovyz /u/mdalbin/Migration-Modeler-MDLB/src/groovy/assessUsage.groovy                --configFile /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-01-15.175657.config                --application GenApp                --logFile /u/mdalbin/Migration-Modeler-MDLB-work/logs/3-GenApp-assessUsage.log
2026-01-15 17:13:43.173 ** Script configuration:
2026-01-15 17:13:43.173     DBB_MODELER_APPCONFIG_DIR -> /u/mdalbin/Migration-Modeler-MDLB-work/work/migration-configuration
2026-01-15 17:13:43.173     MOVE_FILES_FLAG -> true
2026-01-15 17:13:43.173     REPOSITORY_PATH_MAPPING_FILE -> /u/mdalbin/Migration-Modeler-MDLB-work/config/repositoryPathsMapping.yaml
2026-01-15 17:13:43.174     SCAN_CONTROL_TRANSFERS -> true
2026-01-15 17:13:43.174     application -> GenApp
2026-01-15 17:13:43.174     configurationFilePath -> /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-01-15.175657.config
2026-01-15 17:13:43.174     DBB_MODELER_APPLICATION_DIR -> /u/mdalbin/Migration-Modeler-MDLB-work/repositories
2026-01-15 17:13:43.174     DBB_MODELER_FILE_METADATA_STORE_DIR -> /u/mdalbin/Migration-Modeler-MDLB-work/work/dbb-filemetadatastore
2026-01-15 17:13:43.174     logFile -> /u/mdalbin/Migration-Modeler-MDLB-work/logs/3-GenApp-assessUsage.log
2026-01-15 17:13:43.175     DBB_MODELER_METADATASTORE_TYPE -> file
2026-01-15 17:13:43.175     APPLICATION_DEFAULT_BRANCH -> main
2026-01-15 17:13:43.178 ** Reading the Repository Layout Mapping definition.
2026-01-15 17:13:43.179 ** Getting the list of files of 'Include File' type.
2026-01-15 17:13:43.183 ** Analyzing impacted applications for file 'GenApp/GenApp/src/copy/lgcmarea.cpy'.
2026-01-15 17:13:43.224     Files depending on 'GenApp/src/copy/lgcmarea.cpy' :
2026-01-15 17:13:43.224     'GenApp/GenApp/src/cobol/lgdpol01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.224     'GenApp/GenApp/src/cobol/lgtestp3.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.224     'GenApp/GenApp/src/cobol/lgipol01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.225     'GenApp/GenApp/src/cobol/lgupol01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.225     'GenApp/GenApp/src/cobol/lgastat1.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.225     'GenApp/GenApp/src/cobol/lgacvs01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.225     'GenApp/GenApp/src/cobol/lgucus01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.225     'GenApp/GenApp/src/cobol/lgapdb01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.225     'UNASSIGNED/UNASSIGNED/src/cobol/oldacdb1.cbl' in  Application  'UNASSIGNED'
2026-01-15 17:13:43.225     'GenApp/GenApp/src/cobol/lgdpdb01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.226     'GenApp/GenApp/src/cobol/lgacdb01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.226     'GenApp/GenApp/src/cobol/lgtestp2.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.226     'GenApp/GenApp/src/cobol/lgtestc1.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.226     'GenApp/GenApp/src/cobol/lgicdb01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.226     'GenApp/GenApp/src/cobol/lgapol01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.226     'GenApp/GenApp/src/cobol/lgicus01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.226     'GenApp/GenApp/src/cobol/lgupdb01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.226     'UNASSIGNED/UNASSIGNED/src/cobol/oldacdb2.cbl' in  Application  'UNASSIGNED'
2026-01-15 17:13:43.226     'GenApp/GenApp/src/cobol/lgucvs01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.227     'GenApp/GenApp/src/cobol/lgtestp1.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.227     'GenApp/GenApp/src/cobol/lgapvs01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.227     'GenApp/GenApp/src/cobol/lgupvs01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.227     'GenApp/GenApp/src/cobol/lgucdb01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.227     'GenApp/GenApp/src/cobol/lgtestp4.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.227     'GenApp/GenApp/src/cobol/lgdpvs01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.227     'GenApp/GenApp/src/cobol/lgacus01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.227     'GenApp/GenApp/src/cobol/lgipdb01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.228     ==> 'lgcmarea' referenced by multiple applications - [UNASSIGNED, GenApp]
2026-01-15 17:13:43.229     ==> Updating usage of Include File 'lgcmarea' to 'public' in '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/applicationDescriptor.yml'.
2026-01-15 17:13:43.245 ** Analyzing impacted applications for file 'GenApp/GenApp/src/copy/lgcmared.cpy'.
2026-01-15 17:13:43.250     The Include File 'lgcmared' is not referenced at all.
2026-01-15 17:13:43.253 ** Analyzing impacted applications for file 'GenApp/GenApp/src/copy/lgpolicy.cpy'.
2026-01-15 17:13:43.270     Files depending on 'GenApp/src/copy/lgpolicy.cpy' :
2026-01-15 17:13:43.270     'UNASSIGNED/UNASSIGNED/src/cobol/oldacdb1.cbl' in  Application  'UNASSIGNED'
2026-01-15 17:13:43.270     'GenApp/GenApp/src/cobol/lgipol01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.270     'GenApp/GenApp/src/cobol/lgicus01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.270     'GenApp/GenApp/src/cobol/lgacdb01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.270     'GenApp/GenApp/src/cobol/lgucdb01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.270     'GenApp/GenApp/src/cobol/lgupdb01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.271     'UNASSIGNED/UNASSIGNED/src/cobol/oldacdb2.cbl' in  Application  'UNASSIGNED'
2026-01-15 17:13:43.271     'GenApp/GenApp/src/cobol/lgacus01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.271     'GenApp/GenApp/src/cobol/lgicdb01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.271     'GenApp/GenApp/src/cobol/lgipdb01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.271     'GenApp/GenApp/src/cobol/lgapdb01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.271     'GenApp/GenApp/src/cobol/lgacdb02.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.271     ==> 'lgpolicy' referenced by multiple applications - [UNASSIGNED, GenApp]
2026-01-15 17:13:43.271     ==> Updating usage of Include File 'lgpolicy' to 'public' in '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/applicationDescriptor.yml'.
2026-01-15 17:13:43.278 ** Getting the list of files of 'Program' type.
2026-01-15 17:13:43.279 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgicus01.cbl'.
2026-01-15 17:13:43.286     The Program 'lgicus01' is not statically called by any other program.
2026-01-15 17:13:43.289 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgdpol01.cbl'.
2026-01-15 17:13:43.295     The Program 'lgdpol01' is not statically called by any other program.
2026-01-15 17:13:43.298 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgipdb01.cbl'.
2026-01-15 17:13:43.307     The Program 'lgipdb01' is not statically called by any other program.
2026-01-15 17:13:43.309 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgtestp3.cbl'.
2026-01-15 17:13:43.315     The Program 'lgtestp3' is not statically called by any other program.
2026-01-15 17:13:43.318 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgtestp4.cbl'.
2026-01-15 17:13:43.323     The Program 'lgtestp4' is not statically called by any other program.
2026-01-15 17:13:43.326 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgacvs01.cbl'.
2026-01-15 17:13:43.331     The Program 'lgacvs01' is not statically called by any other program.
2026-01-15 17:13:43.333 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgsetup.cbl'.
2026-01-15 17:13:43.340     The Program 'lgsetup' is not statically called by any other program.
2026-01-15 17:13:43.343 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgapol01.cbl'.
2026-01-15 17:13:43.349     The Program 'lgapol01' is not statically called by any other program.
2026-01-15 17:13:43.352 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgipvs01.cbl'.
2026-01-15 17:13:43.357     The Program 'lgipvs01' is not statically called by any other program.
2026-01-15 17:13:43.360 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgupol01.cbl'.
2026-01-15 17:13:43.366     The Program 'lgupol01' is not statically called by any other program.
2026-01-15 17:13:43.368 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgacdb01.cbl'.
2026-01-15 17:13:43.374     The Program 'lgacdb01' is not statically called by any other program.
2026-01-15 17:13:43.377 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgacdb02.cbl'.
2026-01-15 17:13:43.382     The Program 'lgacdb02' is not statically called by any other program.
2026-01-15 17:13:43.384 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgstsq.cbl'.
2026-01-15 17:13:43.395     The Program 'lgstsq' is not statically called by any other program.
2026-01-15 17:13:43.397 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgtestp1.cbl'.
2026-01-15 17:13:43.403     The Program 'lgtestp1' is not statically called by any other program.
2026-01-15 17:13:43.406 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgtestp2.cbl'.
2026-01-15 17:13:43.412     The Program 'lgtestp2' is not statically called by any other program.
2026-01-15 17:13:43.414 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgdpdb01.cbl'.
2026-01-15 17:13:43.419     The Program 'lgdpdb01' is not statically called by any other program.
2026-01-15 17:13:43.422 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgucus01.cbl'.
2026-01-15 17:13:43.427     The Program 'lgucus01' is not statically called by any other program.
2026-01-15 17:13:43.430 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgapvs01.cbl'.
2026-01-15 17:13:43.435     The Program 'lgapvs01' is not statically called by any other program.
2026-01-15 17:13:43.438 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgucdb01.cbl'.
2026-01-15 17:13:43.443     The Program 'lgucdb01' is not statically called by any other program.
2026-01-15 17:13:43.445 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgdpvs01.cbl'.
2026-01-15 17:13:43.450     The Program 'lgdpvs01' is not statically called by any other program.
2026-01-15 17:13:43.453 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgtestc1.cbl'.
2026-01-15 17:13:43.459     The Program 'lgtestc1' is not statically called by any other program.
2026-01-15 17:13:43.461 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgastat1.cbl'.
2026-01-15 17:13:43.466     The Program 'lgastat1' is not statically called by any other program.
2026-01-15 17:13:43.469 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgapdb01.cbl'.
2026-01-15 17:13:43.475     The Program 'lgapdb01' is not statically called by any other program.
2026-01-15 17:13:43.478 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgicvs01.cbl'.
2026-01-15 17:13:43.483     The Program 'lgicvs01' is not statically called by any other program.
2026-01-15 17:13:43.486 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgipol01.cbl'.
2026-01-15 17:13:43.492     The Program 'lgipol01' is not statically called by any other program.
2026-01-15 17:13:43.494 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgacus01.cbl'.
2026-01-15 17:13:43.500     The Program 'lgacus01' is not statically called by any other program.
2026-01-15 17:13:43.503 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgwebst5.cbl'.
2026-01-15 17:13:43.511     The Program 'lgwebst5' is not statically called by any other program.
2026-01-15 17:13:43.514 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgucvs01.cbl'.
2026-01-15 17:13:43.519     The Program 'lgucvs01' is not statically called by any other program.
2026-01-15 17:13:43.521 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgupdb01.cbl'.
2026-01-15 17:13:43.528     The Program 'lgupdb01' is not statically called by any other program.
2026-01-15 17:13:43.530 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgicdb01.cbl'.
2026-01-15 17:13:43.535     The Program 'lgicdb01' is not statically called by any other program.
2026-01-15 17:13:43.538 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgupvs01.cbl'.
2026-01-15 17:13:43.544     The Program 'lgupvs01' is not statically called by any other program.
~~~~
</details>

### Generating Property files

The [Property Generation script (4-generateProperties.sh)](../src/scripts/utils/4-generateProperties.sh) requires the path to the DBB Git Migration Modeler configuration file as parameter.

The script will search for all the applications' subfolders in the `DBB_MODELER_APPLICATION_DIR` folder and will process application definitions found in this folder.
For each application found, it will search for the artifacts of type 'Program', and, for each of them, will check if a Language Configuration exists, based on the *type* information.
If the Language Configuration doesn't exist, the script will create it.

This script will also generate application's related configuration, stored in a `config` subfolder when using DBB zBuilder or in a custom `application-conf` subfolder when using zAppBuild.
If configuration was changed, an *INFO* message is shown, explaining that a manual task must be performed to enable the use of the Language Configuration mapping for a given application.

<details>
  <summary>Output example for a single application (GenApp)</summary>
Execution of the command:
	
`./src/scripts/utils/4-generateProperties.sh  -c /u/ibmuser/dbb-git-migration-modeler-work/DBB_GIT_MIGRATION_MODELER.config`

Output log:
~~~~
[INFO] /usr/lpp/dbb/v3r0/bin/groovyz /u/mdalbin/Migration-Modeler-MDLB/src/groovy/generateZBuilderProperties.groovy                 --configFile /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-01-15.175657.config                --application GenApp                --logFile /u/mdalbin/Migration-Modeler-MDLB-work/logs/4-GenApp-generateProperties.log
2026-01-15 17:13:46.877 ** Script configuration:
2026-01-15 17:13:46.877     application -> GenApp
2026-01-15 17:13:46.877     configurationFilePath -> /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-01-15.175657.config
2026-01-15 17:13:46.877     DBB_MODELER_APPLICATION_DIR -> /u/mdalbin/Migration-Modeler-MDLB-work/repositories
2026-01-15 17:13:46.877     logFile -> /u/mdalbin/Migration-Modeler-MDLB-work/logs/4-GenApp-generateProperties.log
2026-01-15 17:13:46.877     TYPE_CONFIGURATIONS_FILE -> /u/mdalbin/Migration-Modeler-MDLB-work/config/types/typesConfigurations.yaml
2026-01-15 17:13:46.878 ** Reading the Types Configurations definitions from '/u/mdalbin/Migration-Modeler-MDLB-work/config/types/typesConfigurations.yaml'.
2026-01-15 17:13:46.880 ** Gathering the defined types for files.
2026-01-15 17:13:46.881 ** Generating zBuilder language configuration files.
2026-01-15 17:13:46.881     Type Configuration for type 'CBLCICSDB2' found in '/u/mdalbin/Migration-Modeler-MDLB-work/config/types/typesConfigurations.yaml'.
2026-01-15 17:13:46.882     [WARNING] No Type Configuration for type 'CBLDB2' found in '/u/mdalbin/Migration-Modeler-MDLB-work/config/types/typesConfigurations.yaml'.
2026-01-15 17:13:46.882     [WARNING] No Type Configuration for type 'CBLCICS' found in '/u/mdalbin/Migration-Modeler-MDLB-work/config/types/typesConfigurations.yaml'.
2026-01-15 17:13:46.882 ** Generating zBuilder Application configuration file.
2026-01-15 17:13:46.882 ** Generating Dependencies Search Paths and Impact Analysis Query Patterns.
2026-01-15 17:13:46.883 ** Application Configuration file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/dbb-app.yaml' successfully created.
2026-01-15 17:13:46.883 ** [INFO] Make sure the zBuilder Configuration files (Language Task definitions) are accurate before running a build with zBuilder.
2026-01-15 17:13:46.883 ** [INFO] For each Language Task definition, the Dependency Search Path variable potentially needs to be updated to match the layout of the Git repositories.
~~~~
</details>

### Initializing Application Git Repositories

The [Init Application Repositories script (5-initApplicationRepositories.sh)](../src/scripts/utils/5-initApplicationRepositories.sh) requires the path to the DBB Git Migration Modeler configuration file as parameter, to locate the work directories.

It will search for all applications located in the `DBB_MODELER_APPLICATION_DIR` folder and will process application definitions found in this folder.

<details>
  <summary> Output example for a single application (CBSA)</summary>
Execution of command:
	
`./src/scripts/utils/5-initApplicationRepositories.sh -c /u/ibmuser/dbb-git-migration-modeler-work/DBB_GIT_MIGRATION_MODELER.config`

~~~~
[CMD] /usr/lpp/dbb/v3r0/bin/groovyz /u/mdalbin/Migration-Modeler-MDLB/src/groovy/utils/metadataStoreUtility.groovy -c /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-01-15.175657.config --deleteBuildGroup --buildGroup GenApp-main -l /u/mdalbin/Migration-Modeler-MDLB-work/logs/5-GenApp-initApplicationRepository.log
2026-01-15 17:14:04.960 ** Script configuration:
2026-01-15 17:14:04.960    deleteBuildGroup -> true
2026-01-15 17:14:04.960    buildGroup -> GenApp-main
2026-01-15 17:14:04.961    configurationFilePath -> /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-01-15.175657.config
2026-01-15 17:14:04.961    logFile -> /u/mdalbin/Migration-Modeler-MDLB-work/logs/5-GenApp-initApplicationRepository.log
2026-01-15 17:14:04.961 ** Deleting DBB BuildGroup GenApp-main
2026-01-15 17:14:04.968 ** Deleting legacy collections in DBB BuildGroup dbb_default
[CMD] git init --initial-branch=main
Initialized empty Git repository in /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/
[CMD] rm .gitattributes
[CMD] cp /u/mdalbin/Migration-Modeler-MDLB-work/config/default-app-repo-config-files/.gitattributes .gitattributes
[CMD] cp /u/mdalbin/Migration-Modeler-MDLB-work/config/default-app-repo-config-files/zapp_template.yaml zapp.yaml
[CMD] /usr/lpp/dbb/v3r0/bin/groovyz /u/mdalbin/Migration-Modeler-MDLB/src/groovy/utils/zappUtils.groovy                     -z /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/zapp.yaml                     -a /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/applicationDescriptor.yml                     -b /var/dbb/dbb-zappbuild -l /u/mdalbin/Migration-Modeler-MDLB-work/logs/5-GenApp-initApplicationRepository.log
[CMD] cp /u/mdalbin/dbb-MD/Templates/AzureDevOpsPipeline/azure-pipelines.yml /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/
[CMD] cp -R /u/mdalbin/dbb-MD/Templates/AzureDevOpsPipeline/templates/deployment/*.yml /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/deployment/
[CMD] cp -R /u/mdalbin/dbb-MD/Templates/AzureDevOpsPipeline/templates/tagging/*.yml /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/tagging/
[CMD] git status
On branch main

No commits yet

Untracked files:
  (use "git add <file>..." to include in what will be committed)
    .gitattributes
    .project
    GenApp/
    application-conf/
    applicationDescriptor.yml
    azure-pipelines.yml
    config/
    dbb-app.yaml
    deployment/
    tagging/
    zapp.yaml

nothing added to commit but untracked files present (use "git add" to track)
[CMD] git add --all
[CMD] git commit -m 'Initial Commit'
[main (root-commit) d0cba95] Initial Commit
 45 files changed, 11765 insertions(+)
 create mode 100644 .gitattributes
 create mode 100644 .project
 create mode 100644 GenApp/src/bms/ssmap.bms
 create mode 100644 GenApp/src/cobol/lgacdb01.cbl
 create mode 100644 GenApp/src/cobol/lgacdb02.cbl
 create mode 100644 GenApp/src/cobol/lgacus01.cbl
 create mode 100644 GenApp/src/cobol/lgacvs01.cbl
 create mode 100644 GenApp/src/cobol/lgapdb01.cbl
 create mode 100644 GenApp/src/cobol/lgapol01.cbl
 create mode 100644 GenApp/src/cobol/lgapvs01.cbl
 create mode 100644 GenApp/src/cobol/lgastat1.cbl
 create mode 100644 GenApp/src/cobol/lgdpdb01.cbl
 create mode 100644 GenApp/src/cobol/lgdpol01.cbl
 create mode 100644 GenApp/src/cobol/lgdpvs01.cbl
 create mode 100644 GenApp/src/cobol/lgicdb01.cbl
 create mode 100644 GenApp/src/cobol/lgicus01.cbl
 create mode 100644 GenApp/src/cobol/lgicvs01.cbl
 create mode 100644 GenApp/src/cobol/lgipdb01.cbl
 create mode 100644 GenApp/src/cobol/lgipol01.cbl
 create mode 100644 GenApp/src/cobol/lgipvs01.cbl
 create mode 100644 GenApp/src/cobol/lgsetup.cbl
 create mode 100644 GenApp/src/cobol/lgstsq.cbl
 create mode 100644 GenApp/src/cobol/lgtestc1.cbl
 create mode 100644 GenApp/src/cobol/lgtestp1.cbl
 create mode 100644 GenApp/src/cobol/lgtestp2.cbl
 create mode 100644 GenApp/src/cobol/lgtestp3.cbl
 create mode 100644 GenApp/src/cobol/lgtestp4.cbl
 create mode 100644 GenApp/src/cobol/lgucdb01.cbl
 create mode 100644 GenApp/src/cobol/lgucus01.cbl
 create mode 100644 GenApp/src/cobol/lgucvs01.cbl
 create mode 100644 GenApp/src/cobol/lgupdb01.cbl
 create mode 100644 GenApp/src/cobol/lgupol01.cbl
 create mode 100644 GenApp/src/cobol/lgupvs01.cbl
 create mode 100644 GenApp/src/cobol/lgwebst5.cbl
 create mode 100644 GenApp/src/copy/lgcmarea.cpy
 create mode 100644 GenApp/src/copy/lgcmared.cpy
 create mode 100644 GenApp/src/copy/lgpolicy.cpy
 create mode 100644 application-conf/baselineReference.config
 create mode 100644 applicationDescriptor.yml
 create mode 100644 azure-pipelines.yml
 create mode 100644 config/CBLCICSDB2.yaml
 create mode 100644 dbb-app.yaml
 create mode 100644 deployment/deployPackage.yml
 create mode 100644 tagging/createReleaseCandidate.yml
 create mode 100644 zapp.yaml
[CMD] git tag rel-1.0.0
[CMD] git branch release/rel-1.0.0 refs/tags/rel-1.0.0
** /usr/lpp/dbb/v3r0/bin/dbb build full                             --hlq DBEHM.MIG                             --preview                           
~~~~

</details>

## Refreshing Application Descriptor files

When applications are migrated to Git and development teams leverage the modern CI/CD pipeline, source files will be modified, added, or deleted.
It is expected that the list of elements composing an application and its cross-applications dependencies change over time.
To reflect these changes, the **Application Descriptor** file needs to be refreshed.

Additionally, if applications are already migrated to Git and use pipelines, but don't have an Application Descriptor file yet, and the development teams want to leverage its benefits, this creation process should be followed.

A second command is shipped for this workflow. The [Refresh Application Descriptor script](../src/scripts/Refresh-Application-Descriptor-Files.sh) facilitates the refresh process by rescanning the source code, initializing new or resetting the Application Descriptor files, and performing the assessment phase for all applications. The refresh of the Application Descriptor files must occur on the entire code base like on the initial assessment process.

Like the other scripts, it requires the path to the DBB Git Migration Modeler configuration file as parameter. This configuration file can be created with the [Setup](./02-Setup.md#setting-up-the-dbb-git-migration-modeler-configuration) instructions.

An additional parameter can be passed to the [Refresh Application Descriptor script](../src/scripts/Refresh-Application-Descriptor-Files.sh) to specify a list of applications.
This list can be used to filter down the applications to process during the migration. Through the `-a` parameter, the user can specify a comma-separated list of applications, for which the migration will be performed.
This parameter can be used to limit the scope of the refresh process to applications that require an new Application Descriptor file, even if other applications are available in the workspace. This filtering capability can help in a phased migration approach, to successively target individual applications.

The main script calls three groovy scripts ([scanApplication.groovy](../src/groovy/scanApplication.groovy), [recreateApplicationDescriptor.groovy](../src/groovy/recreateApplicationDescriptor.groovy) and [assessUsage.groovy](../src/groovy/assessUsage.groovy)) to scan the files of the applications using the DBB Scanner, initialize Application Descriptor files based on the files present in the working directories, and assess how Include Files and Programs are used across the applications landscape:

   * For the scanning phase, the script iterates through the files located within applications' subfolder in the `DBB_MODELER_APPLICATION_DIR` folder.
   It uses the DBB Scanner to understand the dependencies for each artifact.
   This information is stored in the DBB Metadatastore that holds the dependencies information.

   * In the second phase, the Application Descriptor files are initialized.
   If an Application Descriptor is found, the source groups and dependencies/consumers information are reset.
   If no Application Descriptor is found, a new one is created.
   For each Application Descriptor, the present files in the working folders are documented and grouped according to the `RepositoryPathsMapping.yaml` file.
   If no mapping is found, the files are added into the Application Descriptor with default values based on the low-level qualifier of the containing dataset.

   * The third phase of the process uses the dependency information to understand how Include Files and Programs are used across all applications. It then classifies the Include Files in three categories (Private, Public or Shared) and Programs in three categories (main, internal submodule, service submodule) and updates the Application Descriptor accordingly.

### Outputs

For each application, a refreshed Application Descriptor is created at the root directory of the application's folder in z/OS UNIX System Services.

### Recommended usage

Recreating the Application Descriptor files requires to scan all files and might be time and resource consuming based on the size of the applications landscape.
Consider using this process on a regular basis, like once a week.
The recommendation would be to set up a pipeline, that checks out all Git repositories to a working sandbox, and executes the `Recreate Application Descriptor` script. Once the Application Descriptor files updated within the Git repositories, the pipeline can be enabled to automatically commit and push the updates back to the central Git provider.

<details>
  <summary>Output example</summary>
Execution of command:
	
`./src/scripts/Refresh-Application-Descriptor-Files.sh -c /u/mdalbin/Migration-Modeler-DBEHM-work/DBB_GIT_MIGRATION_MODELER.config`

Output log:
~~~~
[INFO] /usr/lpp/dbb/v3r0/bin/groovyz /u/mdalbin/Migration-Modeler-MDLB/src/groovy/scanApplication.groovy                --configFile /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-01-15.175657.config                --application GenApp                --logFile /u/mdalbin/Migration-Modeler-MDLB-work/logs/3-GenApp-scan.log
2026-01-15 17:13:38.935 ** Script configuration:
2026-01-15 17:13:38.935     REPOSITORY_PATH_MAPPING_FILE -> /u/mdalbin/Migration-Modeler-MDLB-work/config/repositoryPathsMapping.yaml
2026-01-15 17:13:38.935     SCAN_CONTROL_TRANSFERS -> true
2026-01-15 17:13:38.935     application -> GenApp
2026-01-15 17:13:38.935     configurationFilePath -> /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-01-15.175657.config
2026-01-15 17:13:38.935     DBB_MODELER_APPLICATION_DIR -> /u/mdalbin/Migration-Modeler-MDLB-work/repositories
2026-01-15 17:13:38.936     DBB_MODELER_FILE_METADATA_STORE_DIR -> /u/mdalbin/Migration-Modeler-MDLB-work/work/dbb-filemetadatastore
2026-01-15 17:13:38.936     logFile -> /u/mdalbin/Migration-Modeler-MDLB-work/logs/3-GenApp-scan.log
2026-01-15 17:13:38.936     DBB_MODELER_METADATASTORE_TYPE -> file
2026-01-15 17:13:38.936     APPLICATION_DEFAULT_BRANCH -> main
2026-01-15 17:13:38.937 ** Reading the existing Application Descriptor file.
2026-01-15 17:13:38.940 ** Retrieving the list of files mapped to Source Groups.
2026-01-15 17:13:38.940 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/applicationDescriptor.yml'. Skipping.
2026-01-15 17:13:38.941 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.gitattributes'. Skipping.
2026-01-15 17:13:38.951 ** Scanning the files.
2026-01-15 17:13:38.951     Scanning file GenApp/GenApp/src/cobol/lgtestp2.cbl 
2026-01-15 17:13:38.959     Scanning file GenApp/GenApp/src/cobol/lgicus01.cbl 
2026-01-15 17:13:38.965     Scanning file GenApp/GenApp/src/cobol/lgucus01.cbl 
2026-01-15 17:13:38.972     Scanning file GenApp/GenApp/src/cobol/lgucvs01.cbl 
2026-01-15 17:13:38.977     Scanning file GenApp/GenApp/src/cobol/lgapdb01.cbl 
2026-01-15 17:13:38.987     Scanning file GenApp/GenApp/src/cobol/lgdpdb01.cbl 
2026-01-15 17:13:38.994     Scanning file GenApp/GenApp/src/cobol/lgicvs01.cbl 
2026-01-15 17:13:39.015     Scanning file GenApp/GenApp/src/copy/lgpolicy.cpy 
2026-01-15 17:13:39.035     Scanning file GenApp/GenApp/src/cobol/lgsetup.cbl 
2026-01-15 17:13:39.045     Scanning file GenApp/GenApp/src/copy/lgcmarea.cpy 
2026-01-15 17:13:39.062     Scanning file GenApp/GenApp/src/cobol/lgacdb01.cbl 
2026-01-15 17:13:39.069     Scanning file GenApp/GenApp/src/cobol/lgipdb01.cbl 
2026-01-15 17:13:39.085     Scanning file GenApp/GenApp/src/cobol/lgupvs01.cbl 
2026-01-15 17:13:39.092     Scanning file GenApp/GenApp/src/cobol/lgtestp1.cbl 
2026-01-15 17:13:39.099     Scanning file GenApp/GenApp/src/cobol/lgtestc1.cbl 
2026-01-15 17:13:39.107     Scanning file GenApp/GenApp/src/cobol/lgdpol01.cbl 
2026-01-15 17:13:39.113     Scanning file GenApp/GenApp/src/cobol/lgapol01.cbl 
2026-01-15 17:13:39.119     Scanning file GenApp/GenApp/src/bms/ssmap.bms 
2026-01-15 17:13:39.229     Scanning file GenApp/GenApp/src/copy/lgcmared.cpy 
2026-01-15 17:13:39.235     Scanning file GenApp/GenApp/src/cobol/lgucdb01.cbl 
2026-01-15 17:13:39.239     Scanning file GenApp/GenApp/src/cobol/lgacdb02.cbl 
2026-01-15 17:13:39.244     Scanning file GenApp/GenApp/src/cobol/lgipol01.cbl 
2026-01-15 17:13:39.248     Scanning file GenApp/GenApp/src/cobol/lgapvs01.cbl 
2026-01-15 17:13:39.253     Scanning file GenApp/GenApp/src/cobol/lgicdb01.cbl 
2026-01-15 17:13:39.258     Scanning file GenApp/GenApp/src/cobol/lgtestp4.cbl 
2026-01-15 17:13:39.263     Scanning file GenApp/GenApp/src/cobol/lgdpvs01.cbl 
2026-01-15 17:13:39.267     Scanning file GenApp/GenApp/src/cobol/lgupol01.cbl 
2026-01-15 17:13:39.272     Scanning file GenApp/GenApp/src/cobol/lgacvs01.cbl 
2026-01-15 17:13:39.276     Scanning file GenApp/GenApp/src/cobol/lgipvs01.cbl 
2026-01-15 17:13:39.281     Scanning file GenApp/GenApp/src/cobol/lgastat1.cbl 
2026-01-15 17:13:39.285     Scanning file GenApp/GenApp/src/cobol/lgacus01.cbl 
2026-01-15 17:13:39.289     Scanning file GenApp/GenApp/src/cobol/lgupdb01.cbl 
2026-01-15 17:13:39.296     Scanning file GenApp/GenApp/src/cobol/lgtestp3.cbl 
2026-01-15 17:13:39.301     Scanning file GenApp/GenApp/src/cobol/lgstsq.cbl 
2026-01-15 17:13:39.305     Scanning file GenApp/GenApp/src/cobol/lgwebst5.cbl 
2026-01-15 17:13:39.314 ** Storing results in the 'GenApp-main' DBB Collection.
[INFO] /usr/lpp/dbb/v3r0/bin/groovyz /u/mdalbin/Migration-Modeler-MDLB/src/groovy/scanApplication.groovy                --configFile /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-01-15.175657.config                --application GenApp                --logFile /u/mdalbin/Migration-Modeler-MDLB-work/logs/3-GenApp-scan.log
2026-01-16 10:38:33.846 ** Script configuration:
2026-01-16 10:38:33.847     REPOSITORY_PATH_MAPPING_FILE -> /u/mdalbin/Migration-Modeler-MDLB-work/config/repositoryPathsMapping.yaml
2026-01-16 10:38:33.847     SCAN_CONTROL_TRANSFERS -> true
2026-01-16 10:38:33.847     application -> GenApp
2026-01-16 10:38:33.847     configurationFilePath -> /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-01-15.175657.config
2026-01-16 10:38:33.848     DBB_MODELER_APPLICATION_DIR -> /u/mdalbin/Migration-Modeler-MDLB-work/repositories
2026-01-16 10:38:33.848     DBB_MODELER_FILE_METADATA_STORE_DIR -> /u/mdalbin/Migration-Modeler-MDLB-work/work/dbb-filemetadatastore
2026-01-16 10:38:33.848     logFile -> /u/mdalbin/Migration-Modeler-MDLB-work/logs/3-GenApp-scan.log
2026-01-16 10:38:33.849     DBB_MODELER_METADATASTORE_TYPE -> file
2026-01-16 10:38:33.849     APPLICATION_DEFAULT_BRANCH -> main
2026-01-16 10:38:33.849 ** Reading the existing Application Descriptor file.
2026-01-16 10:38:33.876 ** Retrieving the list of files mapped to Source Groups.
2026-01-16 10:38:33.877 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/applicationDescriptor.yml'. Skipping.
2026-01-16 10:38:33.878 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.gitattributes'. Skipping.
2026-01-16 10:38:33.889 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/config/CBLCICSDB2.yaml'. Skipping.
2026-01-16 10:38:33.890 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/dbb-app.yaml'. Skipping.
2026-01-16 10:38:33.890 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/description'. Skipping.
2026-01-16 10:38:33.891 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/applypatch-msg.sample'. Skipping.
2026-01-16 10:38:33.891 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/commit-msg.sample'. Skipping.
2026-01-16 10:38:33.891 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/fsmonitor-watchman.sample'. Skipping.
2026-01-16 10:38:33.892 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/post-update.sample'. Skipping.
2026-01-16 10:38:33.892 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/pre-applypatch.sample'. Skipping.
2026-01-16 10:38:33.893 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/pre-commit.sample'. Skipping.
2026-01-16 10:38:33.893 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/pre-merge-commit.sample'. Skipping.
2026-01-16 10:38:33.893 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/prepare-commit-msg.sample'. Skipping.
2026-01-16 10:38:33.894 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/pre-push.sample'. Skipping.
2026-01-16 10:38:33.894 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/pre-rebase.sample'. Skipping.
2026-01-16 10:38:33.894 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/pre-receive.sample'. Skipping.
2026-01-16 10:38:33.895 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/push-to-checkout.sample'. Skipping.
2026-01-16 10:38:33.895 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/sendemail-validate.sample'. Skipping.
2026-01-16 10:38:33.896 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/update.sample'. Skipping.
2026-01-16 10:38:33.896 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/info/exclude'. Skipping.
2026-01-16 10:38:33.897 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/refs/heads/release/rel-1.0.0'. Skipping.
2026-01-16 10:38:33.897 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/refs/heads/main'. Skipping.
2026-01-16 10:38:33.898 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/refs/tags/rel-1.0.0'. Skipping.
2026-01-16 10:38:33.898 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/config'. Skipping.
2026-01-16 10:38:33.899 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/36/512daf923067d0311f15995925416be25290b5'. Skipping.
2026-01-16 10:38:33.899 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/8d/0eaea2f7e487513d472afe1a66d7da07f663b9'. Skipping.
2026-01-16 10:38:33.900 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/7d/f90877fb98ccba6508a94e6fe3ff1ad865d682'. Skipping.
2026-01-16 10:38:33.900 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/d8/f18d43e8afa308163aebcff561e7dedf67759e'. Skipping.
2026-01-16 10:38:33.901 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/ed/7e8c1b79aaa76736f0af3b735f667d3d26ad36'. Skipping.
2026-01-16 10:38:33.901 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/7e/36d0d65c7ae8ca0ce7a451692820010cf2c51f'. Skipping.
2026-01-16 10:38:33.902 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/17/cd1d6b0325b04277c7fc7a1ec27ce9bcbd2598'. Skipping.
2026-01-16 10:38:33.902 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/e1/52fbd8c03e836ad0046953854f04b4665d75b9'. Skipping.
2026-01-16 10:38:33.903 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/28/2aa20f6c7d61d15b8922c8d8e0552880351472'. Skipping.
2026-01-16 10:38:33.903 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/28/0e6f742c84b40da642115cad3a0c86aa9c0aac'. Skipping.
2026-01-16 10:38:33.904 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/13/0e880deea1c41c3ba7e57cbb0aa4e19f5ce9ad'. Skipping.
2026-01-16 10:38:33.904 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/1b/9d6bcb233214bd016ac6ffd87d5b4e5a0644cc'. Skipping.
2026-01-16 10:38:33.905 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/22/b550bafdc6e9f5103b1a28ca501d6bdae4ec76'. Skipping.
2026-01-16 10:38:33.905 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/e5/86c7d2e00e602158da102e4c8d30deaeb142ae'. Skipping.
2026-01-16 10:38:33.906 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/0d/b601b1f055ea023e104c7d24ab0ef5eea1ff05'. Skipping.
2026-01-16 10:38:33.906 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/12/5b26f553c5647a5aabc69a45f0191aed5d5e01'. Skipping.
2026-01-16 10:38:33.906 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/12/6b62bf8e868002e76f970f411637e7488e05bb'. Skipping.
2026-01-16 10:38:33.907 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/42/d3f2e669c2f9f6cf9565e61b2a3f96ad1ff503'. Skipping.
2026-01-16 10:38:33.907 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/fa/ffcce01f2da721aa453f5dda21d11f8d3ae693'. Skipping.
2026-01-16 10:38:33.908 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/16/73ab0e7f0e1744ab58379576e6c835d4108474'. Skipping.
2026-01-16 10:38:33.909 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/b0/49dc9735257281c334afd74730dee59c62e2e8'. Skipping.
2026-01-16 10:38:33.909 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/f7/09ff109986301f101a1912b9d043756d7e596a'. Skipping.
2026-01-16 10:38:33.909 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/f7/1aff317ceeb25dca6e497b93a6ff9a5c8e2518'. Skipping.
2026-01-16 10:38:33.910 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/a0/b94e23333057ca37382048c4f7fc6f2e0df75b'. Skipping.
2026-01-16 10:38:33.910 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/d4/24e6a718eb9ad584e21f7a899488500484f7e2'. Skipping.
2026-01-16 10:38:33.911 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/d9/455ae3c356b0e7a2440914f564ddbcbe30e28d'. Skipping.
2026-01-16 10:38:33.911 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/83/2f54aa68fe84f78461085d00e3b3206e39fdb7'. Skipping.
2026-01-16 10:38:33.912 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/6e/a11cb2dc20aa126f08701fe873ac2dae5ce0b6'. Skipping.
2026-01-16 10:38:33.912 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/2e/f0cfc9de9ca7521899a87cf9e216be7f109d88'. Skipping.
2026-01-16 10:38:33.913 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/82/766939ca20dfac5d9ab33782e4f45b2ade19fc'. Skipping.
2026-01-16 10:38:33.913 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/9a/a1e257384925e8015d7e0864175961ce258290'. Skipping.
2026-01-16 10:38:33.914 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/78/e7f1d24d01d4949e80fc149026a9d902eac1b9'. Skipping.
2026-01-16 10:38:33.914 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/98/11fa56e0556c5d884a98ae06f7d007f64edafa'. Skipping.
2026-01-16 10:38:33.915 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/37/0f90c505893d5ab01089e66e04528f8d40dab1'. Skipping.
2026-01-16 10:38:33.915 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/bf/a3623bc647efd22c9550939cd8d5bf72cb91ad'. Skipping.
2026-01-16 10:38:33.916 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/3e/9eed6daafd969231900049360b526396bf4091'. Skipping.
2026-01-16 10:38:33.916 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/de/85d8fbe9f576dabc377e29616bc4e8fcf68a56'. Skipping.
2026-01-16 10:38:33.917 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/c5/ea6c1fed91fd2154ac3f38533455da5481d974'. Skipping.
2026-01-16 10:38:33.917 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/b6/53161403e5df737d6e540d8c5a1988a043eafc'. Skipping.
2026-01-16 10:38:33.918 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/cd/d0f9ff2c8b8046cce071bae83571570bc073e4'. Skipping.
2026-01-16 10:38:33.918 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/6f/f0de34ffe1105f2acfa133f76f4c2a9fe2c95d'. Skipping.
2026-01-16 10:38:33.919 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/72/756588cafd0baa190b8cbe9d51e697be473354'. Skipping.
2026-01-16 10:38:33.920 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/b2/7bd4267d2f3d7188095a0f549fb5cd87009a5d'. Skipping.
2026-01-16 10:38:33.920 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/15/11e2f080004f3829fc9de21a0b681684da7ff7'. Skipping.
2026-01-16 10:38:33.921 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/81/80f5cd516eb542440a4573db174af1369f44a8'. Skipping.
2026-01-16 10:38:33.921 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/d1/e33757aa74694d0039e8162918a840172d24f8'. Skipping.
2026-01-16 10:38:33.922 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/59/fee299f9b88edb6925432ca41dc8a51dd50b97'. Skipping.
2026-01-16 10:38:33.922 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/76/be470b4b4450038992dec6a9f9ac90a8611f2b'. Skipping.
2026-01-16 10:38:33.923 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/75/1def8d69161d12e708eb37c902e2923fef560b'. Skipping.
2026-01-16 10:38:33.923 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/b4/a48625756286d8d8159714882707ed2da27fda'. Skipping.
2026-01-16 10:38:33.924 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/31/94c3fdd79ab7638c2cc6c2c328829028c02c51'. Skipping.
2026-01-16 10:38:33.924 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/8a/c3edea77f489aa91c07e6834824fa89412ab32'. Skipping.
2026-01-16 10:38:33.925 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/f0/f472bd00702a4d900984524a41504dd6359755'. Skipping.
2026-01-16 10:38:33.925 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/b3/3fe2dd683b6c6457cb46d0e3fa96d46fcfdd21'. Skipping.
2026-01-16 10:38:33.926 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/55/71999ba294064ffb9518ffc6f3775a59ab742b'. Skipping.
2026-01-16 10:38:33.926 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/d0/cba9510054564db07d0a2e000e91337c10f3ec'. Skipping.
2026-01-16 10:38:33.927 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/HEAD'. Skipping.
2026-01-16 10:38:33.927 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/index'. Skipping.
2026-01-16 10:38:33.927 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/COMMIT_EDITMSG'. Skipping.
2026-01-16 10:38:33.928 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/logs/HEAD'. Skipping.
2026-01-16 10:38:33.928 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/logs/refs/heads/main'. Skipping.
2026-01-16 10:38:33.929 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/logs/refs/heads/release/rel-1.0.0'. Skipping.
2026-01-16 10:38:33.929 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/zapp.yaml'. Skipping.
2026-01-16 10:38:33.930 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/application-conf/baselineReference.config'. Skipping.
2026-01-16 10:38:33.930 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.project'. Skipping.
2026-01-16 10:38:33.931 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/azure-pipelines.yml'. Skipping.
2026-01-16 10:38:33.931 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/deployment/deployPackage.yml'. Skipping.
2026-01-16 10:38:33.932 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/tagging/createReleaseCandidate.yml'. Skipping.
2026-01-16 10:38:33.932 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/logs/BuildReport.json'. Skipping.
2026-01-16 10:38:33.932 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/logs/BuildReport.html'. Skipping.
2026-01-16 10:38:33.933 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/logs/buildList.txt'. Skipping.
2026-01-16 10:38:33.933 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/logs/RuntimeReport.json'. Skipping.
2026-01-16 10:38:33.933 ** Scanning the files.
2026-01-16 10:38:33.933     Scanning file GenApp/GenApp/src/cobol/lgtestp2.cbl 
2026-01-16 10:38:33.967     Scanning file GenApp/GenApp/src/cobol/lgicus01.cbl 
2026-01-16 10:38:33.998     Scanning file GenApp/GenApp/src/cobol/lgucus01.cbl 
2026-01-16 10:38:34.024     Scanning file GenApp/GenApp/src/cobol/lgucvs01.cbl 
2026-01-16 10:38:34.030     Scanning file GenApp/GenApp/src/cobol/lgapdb01.cbl 
2026-01-16 10:38:34.065     Scanning file GenApp/GenApp/src/cobol/lgdpdb01.cbl 
2026-01-16 10:38:34.072     Scanning file GenApp/GenApp/src/cobol/lgicvs01.cbl 
2026-01-16 10:38:34.109     Scanning file GenApp/GenApp/src/copy/lgpolicy.cpy 
2026-01-16 10:38:34.123     Scanning file GenApp/GenApp/src/cobol/lgsetup.cbl 
2026-01-16 10:38:34.131     Scanning file GenApp/GenApp/src/copy/lgcmarea.cpy 
2026-01-16 10:38:34.145     Scanning file GenApp/GenApp/src/cobol/lgacdb01.cbl 
2026-01-16 10:38:34.156     Scanning file GenApp/GenApp/src/cobol/lgipdb01.cbl 
2026-01-16 10:38:34.188     Scanning file GenApp/GenApp/src/cobol/lgupvs01.cbl 
2026-01-16 10:38:34.194     Scanning file GenApp/GenApp/src/cobol/lgtestp1.cbl 
2026-01-16 10:38:34.201     Scanning file GenApp/GenApp/src/cobol/lgtestc1.cbl 
2026-01-16 10:38:34.221     Scanning file GenApp/GenApp/src/cobol/lgdpol01.cbl 
2026-01-16 10:38:34.226     Scanning file GenApp/GenApp/src/cobol/lgapol01.cbl 
2026-01-16 10:38:34.232     Scanning file GenApp/GenApp/src/bms/ssmap.bms 
2026-01-16 10:38:34.341     Scanning file GenApp/GenApp/src/copy/lgcmared.cpy 
2026-01-16 10:38:34.350     Scanning file GenApp/GenApp/src/cobol/lgucdb01.cbl 
2026-01-16 10:38:34.356     Scanning file GenApp/GenApp/src/cobol/lgacdb02.cbl 
2026-01-16 10:38:34.363     Scanning file GenApp/GenApp/src/cobol/lgipol01.cbl 
2026-01-16 10:38:34.368     Scanning file GenApp/GenApp/src/cobol/lgapvs01.cbl 
2026-01-16 10:38:34.373     Scanning file GenApp/GenApp/src/cobol/lgicdb01.cbl 
2026-01-16 10:38:34.381     Scanning file GenApp/GenApp/src/cobol/lgtestp4.cbl 
2026-01-16 10:38:34.387     Scanning file GenApp/GenApp/src/cobol/lgdpvs01.cbl 
2026-01-16 10:38:34.392     Scanning file GenApp/GenApp/src/cobol/lgupol01.cbl 
2026-01-16 10:38:34.397     Scanning file GenApp/GenApp/src/cobol/lgacvs01.cbl 
2026-01-16 10:38:34.402     Scanning file GenApp/GenApp/src/cobol/lgipvs01.cbl 
2026-01-16 10:38:34.407     Scanning file GenApp/GenApp/src/cobol/lgastat1.cbl 
2026-01-16 10:38:34.412     Scanning file GenApp/GenApp/src/cobol/lgacus01.cbl 
2026-01-16 10:38:34.419     Scanning file GenApp/GenApp/src/cobol/lgupdb01.cbl 
2026-01-16 10:38:34.427     Scanning file GenApp/GenApp/src/cobol/lgtestp3.cbl 
2026-01-16 10:38:34.434     Scanning file GenApp/GenApp/src/cobol/lgstsq.cbl 
2026-01-16 10:38:34.440     Scanning file GenApp/GenApp/src/cobol/lgwebst5.cbl 
2026-01-16 10:38:34.451 ** Storing results in the 'GenApp-main' DBB Collection.
[INFO] /usr/lpp/dbb/v3r0/bin/groovyz /u/mdalbin/Migration-Modeler-MDLB/src/groovy/scanApplication.groovy                --configFile /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-01-15.175657.config                --application GenApp                --logFile /u/mdalbin/Migration-Modeler-MDLB-work/logs/3-GenApp-scan.log
2026-01-16 11:41:10.115 ** Script configuration:
2026-01-16 11:41:10.144     REPOSITORY_PATH_MAPPING_FILE -> /u/mdalbin/Migration-Modeler-MDLB-work/config/repositoryPathsMapping.yaml
2026-01-16 11:41:10.145     SCAN_CONTROL_TRANSFERS -> true
2026-01-16 11:41:10.146     application -> GenApp
2026-01-16 11:41:10.147     configurationFilePath -> /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-01-15.175657.config
2026-01-16 11:41:10.149     DBB_MODELER_APPLICATION_DIR -> /u/mdalbin/Migration-Modeler-MDLB-work/repositories
2026-01-16 11:41:10.151     DBB_MODELER_FILE_METADATA_STORE_DIR -> /u/mdalbin/Migration-Modeler-MDLB-work/work/dbb-filemetadatastore
2026-01-16 11:41:10.152     logFile -> /u/mdalbin/Migration-Modeler-MDLB-work/logs/3-GenApp-scan.log
2026-01-16 11:41:10.154     DBB_MODELER_METADATASTORE_TYPE -> file
2026-01-16 11:41:10.155     APPLICATION_DEFAULT_BRANCH -> main
2026-01-16 11:41:10.166 ** Reading the existing Application Descriptor file.
2026-01-16 11:41:10.391 ** Retrieving the list of files mapped to Source Groups.
2026-01-16 11:41:10.429 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/applicationDescriptor.yml'. Skipping.
2026-01-16 11:41:10.430 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.gitattributes'. Skipping.
2026-01-16 11:41:10.432 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgapol01.cbl'. Skipping.
2026-01-16 11:41:10.434 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgtestc1.cbl'. Skipping.
2026-01-16 11:41:10.436 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgacdb01.cbl'. Skipping.
2026-01-16 11:41:10.437 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgapvs01.cbl'. Skipping.
2026-01-16 11:41:10.438 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgdpvs01.cbl'. Skipping.
2026-01-16 11:41:10.440 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgtestp3.cbl'. Skipping.
2026-01-16 11:41:10.441 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgucus01.cbl'. Skipping.
2026-01-16 11:41:10.443 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgastat1.cbl'. Skipping.
2026-01-16 11:41:10.444 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgicdb01.cbl'. Skipping.
2026-01-16 11:41:10.446 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgupvs01.cbl'. Skipping.
2026-01-16 11:41:10.447 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgdpdb01.cbl'. Skipping.
2026-01-16 11:41:10.448 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgucvs01.cbl'. Skipping.
2026-01-16 11:41:10.450 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgipdb01.cbl'. Skipping.
2026-01-16 11:41:10.451 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgtestp2.cbl'. Skipping.
2026-01-16 11:41:10.453 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgupol01.cbl'. Skipping.
2026-01-16 11:41:10.454 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgstsq.cbl'. Skipping.
2026-01-16 11:41:10.456 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgacus01.cbl'. Skipping.
2026-01-16 11:41:10.458 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgsetup.cbl'. Skipping.
2026-01-16 11:41:10.461 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgacvs01.cbl'. Skipping.
2026-01-16 11:41:10.463 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgacdb02.cbl'. Skipping.
2026-01-16 11:41:10.465 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgtestp4.cbl'. Skipping.
2026-01-16 11:41:10.467 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgicvs01.cbl'. Skipping.
2026-01-16 11:41:10.470 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgwebst5.cbl'. Skipping.
2026-01-16 11:41:10.471 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgapdb01.cbl'. Skipping.
2026-01-16 11:41:10.473 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgdpol01.cbl'. Skipping.
2026-01-16 11:41:10.476 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgipvs01.cbl'. Skipping.
2026-01-16 11:41:10.478 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgucdb01.cbl'. Skipping.
2026-01-16 11:41:10.480 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgtestp1.cbl'. Skipping.
2026-01-16 11:41:10.482 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgipol01.cbl'. Skipping.
2026-01-16 11:41:10.483 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgicus01.cbl'. Skipping.
2026-01-16 11:41:10.486 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgupdb01.cbl'. Skipping.
2026-01-16 11:41:10.489 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/bms/ssmap.bms'. Skipping.
2026-01-16 11:41:10.491 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/copy/lgcmared.cpy'. Skipping.
2026-01-16 11:41:10.493 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/copy/lgcmarea.cpy'. Skipping.
2026-01-16 11:41:10.495 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/copy/lgpolicy.cpy'. Skipping.
2026-01-16 11:41:10.496 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/config/CBLCICSDB2.yaml'. Skipping.
2026-01-16 11:41:10.497 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/dbb-app.yaml'. Skipping.
2026-01-16 11:41:10.499 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/description'. Skipping.
2026-01-16 11:41:10.501 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/applypatch-msg.sample'. Skipping.
2026-01-16 11:41:10.502 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/commit-msg.sample'. Skipping.
2026-01-16 11:41:10.503 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/fsmonitor-watchman.sample'. Skipping.
2026-01-16 11:41:10.504 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/post-update.sample'. Skipping.
2026-01-16 11:41:10.505 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/pre-applypatch.sample'. Skipping.
2026-01-16 11:41:10.506 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/pre-commit.sample'. Skipping.
2026-01-16 11:41:10.507 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/pre-merge-commit.sample'. Skipping.
2026-01-16 11:41:10.508 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/prepare-commit-msg.sample'. Skipping.
2026-01-16 11:41:10.509 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/pre-push.sample'. Skipping.
2026-01-16 11:41:10.510 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/pre-rebase.sample'. Skipping.
2026-01-16 11:41:10.511 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/pre-receive.sample'. Skipping.
2026-01-16 11:41:10.512 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/push-to-checkout.sample'. Skipping.
2026-01-16 11:41:10.513 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/sendemail-validate.sample'. Skipping.
2026-01-16 11:41:10.514 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/update.sample'. Skipping.
2026-01-16 11:41:10.515 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/info/exclude'. Skipping.
2026-01-16 11:41:10.517 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/refs/heads/release/rel-1.0.0'. Skipping.
2026-01-16 11:41:10.518 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/refs/heads/main'. Skipping.
2026-01-16 11:41:10.519 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/refs/tags/rel-1.0.0'. Skipping.
2026-01-16 11:41:10.520 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/config'. Skipping.
2026-01-16 11:41:10.521 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/36/512daf923067d0311f15995925416be25290b5'. Skipping.
2026-01-16 11:41:10.522 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/8d/0eaea2f7e487513d472afe1a66d7da07f663b9'. Skipping.
2026-01-16 11:41:10.523 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/7d/f90877fb98ccba6508a94e6fe3ff1ad865d682'. Skipping.
2026-01-16 11:41:10.524 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/d8/f18d43e8afa308163aebcff561e7dedf67759e'. Skipping.
2026-01-16 11:41:10.525 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/ed/7e8c1b79aaa76736f0af3b735f667d3d26ad36'. Skipping.
2026-01-16 11:41:10.526 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/7e/36d0d65c7ae8ca0ce7a451692820010cf2c51f'. Skipping.
2026-01-16 11:41:10.527 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/17/cd1d6b0325b04277c7fc7a1ec27ce9bcbd2598'. Skipping.
2026-01-16 11:41:10.528 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/e1/52fbd8c03e836ad0046953854f04b4665d75b9'. Skipping.
2026-01-16 11:41:10.529 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/28/2aa20f6c7d61d15b8922c8d8e0552880351472'. Skipping.
2026-01-16 11:41:10.530 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/28/0e6f742c84b40da642115cad3a0c86aa9c0aac'. Skipping.
2026-01-16 11:41:10.531 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/13/0e880deea1c41c3ba7e57cbb0aa4e19f5ce9ad'. Skipping.
2026-01-16 11:41:10.532 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/1b/9d6bcb233214bd016ac6ffd87d5b4e5a0644cc'. Skipping.
2026-01-16 11:41:10.533 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/22/b550bafdc6e9f5103b1a28ca501d6bdae4ec76'. Skipping.
2026-01-16 11:41:10.534 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/e5/86c7d2e00e602158da102e4c8d30deaeb142ae'. Skipping.
2026-01-16 11:41:10.535 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/0d/b601b1f055ea023e104c7d24ab0ef5eea1ff05'. Skipping.
2026-01-16 11:41:10.535 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/12/5b26f553c5647a5aabc69a45f0191aed5d5e01'. Skipping.
2026-01-16 11:41:10.537 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/12/6b62bf8e868002e76f970f411637e7488e05bb'. Skipping.
2026-01-16 11:41:10.538 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/42/d3f2e669c2f9f6cf9565e61b2a3f96ad1ff503'. Skipping.
2026-01-16 11:41:10.539 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/fa/ffcce01f2da721aa453f5dda21d11f8d3ae693'. Skipping.
2026-01-16 11:41:10.541 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/16/73ab0e7f0e1744ab58379576e6c835d4108474'. Skipping.
2026-01-16 11:41:10.542 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/b0/49dc9735257281c334afd74730dee59c62e2e8'. Skipping.
2026-01-16 11:41:10.543 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/f7/09ff109986301f101a1912b9d043756d7e596a'. Skipping.
2026-01-16 11:41:10.544 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/f7/1aff317ceeb25dca6e497b93a6ff9a5c8e2518'. Skipping.
2026-01-16 11:41:10.545 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/a0/b94e23333057ca37382048c4f7fc6f2e0df75b'. Skipping.
2026-01-16 11:41:10.546 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/d4/24e6a718eb9ad584e21f7a899488500484f7e2'. Skipping.
2026-01-16 11:41:10.546 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/d9/455ae3c356b0e7a2440914f564ddbcbe30e28d'. Skipping.
2026-01-16 11:41:10.547 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/83/2f54aa68fe84f78461085d00e3b3206e39fdb7'. Skipping.
2026-01-16 11:41:10.548 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/6e/a11cb2dc20aa126f08701fe873ac2dae5ce0b6'. Skipping.
2026-01-16 11:41:10.548 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/2e/f0cfc9de9ca7521899a87cf9e216be7f109d88'. Skipping.
2026-01-16 11:41:10.549 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/82/766939ca20dfac5d9ab33782e4f45b2ade19fc'. Skipping.
2026-01-16 11:41:10.550 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/9a/a1e257384925e8015d7e0864175961ce258290'. Skipping.
2026-01-16 11:41:10.551 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/78/e7f1d24d01d4949e80fc149026a9d902eac1b9'. Skipping.
2026-01-16 11:41:10.551 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/98/11fa56e0556c5d884a98ae06f7d007f64edafa'. Skipping.
2026-01-16 11:41:10.552 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/37/0f90c505893d5ab01089e66e04528f8d40dab1'. Skipping.
2026-01-16 11:41:10.553 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/bf/a3623bc647efd22c9550939cd8d5bf72cb91ad'. Skipping.
2026-01-16 11:41:10.553 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/3e/9eed6daafd969231900049360b526396bf4091'. Skipping.
2026-01-16 11:41:10.554 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/de/85d8fbe9f576dabc377e29616bc4e8fcf68a56'. Skipping.
2026-01-16 11:41:10.555 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/c5/ea6c1fed91fd2154ac3f38533455da5481d974'. Skipping.
2026-01-16 11:41:10.555 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/b6/53161403e5df737d6e540d8c5a1988a043eafc'. Skipping.
2026-01-16 11:41:10.556 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/cd/d0f9ff2c8b8046cce071bae83571570bc073e4'. Skipping.
2026-01-16 11:41:10.557 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/6f/f0de34ffe1105f2acfa133f76f4c2a9fe2c95d'. Skipping.
2026-01-16 11:41:10.557 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/72/756588cafd0baa190b8cbe9d51e697be473354'. Skipping.
2026-01-16 11:41:10.558 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/b2/7bd4267d2f3d7188095a0f549fb5cd87009a5d'. Skipping.
2026-01-16 11:41:10.559 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/15/11e2f080004f3829fc9de21a0b681684da7ff7'. Skipping.
2026-01-16 11:41:10.559 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/81/80f5cd516eb542440a4573db174af1369f44a8'. Skipping.
2026-01-16 11:41:10.560 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/d1/e33757aa74694d0039e8162918a840172d24f8'. Skipping.
2026-01-16 11:41:10.561 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/59/fee299f9b88edb6925432ca41dc8a51dd50b97'. Skipping.
2026-01-16 11:41:10.561 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/76/be470b4b4450038992dec6a9f9ac90a8611f2b'. Skipping.
2026-01-16 11:41:10.562 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/75/1def8d69161d12e708eb37c902e2923fef560b'. Skipping.
2026-01-16 11:41:10.563 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/b4/a48625756286d8d8159714882707ed2da27fda'. Skipping.
2026-01-16 11:41:10.563 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/31/94c3fdd79ab7638c2cc6c2c328829028c02c51'. Skipping.
2026-01-16 11:41:10.564 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/8a/c3edea77f489aa91c07e6834824fa89412ab32'. Skipping.
2026-01-16 11:41:10.565 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/f0/f472bd00702a4d900984524a41504dd6359755'. Skipping.
2026-01-16 11:41:10.566 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/b3/3fe2dd683b6c6457cb46d0e3fa96d46fcfdd21'. Skipping.
2026-01-16 11:41:10.566 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/55/71999ba294064ffb9518ffc6f3775a59ab742b'. Skipping.
2026-01-16 11:41:10.567 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/d0/cba9510054564db07d0a2e000e91337c10f3ec'. Skipping.
2026-01-16 11:41:10.568 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/HEAD'. Skipping.
2026-01-16 11:41:10.568 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/index'. Skipping.
2026-01-16 11:41:10.569 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/COMMIT_EDITMSG'. Skipping.
2026-01-16 11:41:10.569 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/logs/HEAD'. Skipping.
2026-01-16 11:41:10.570 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/logs/refs/heads/main'. Skipping.
2026-01-16 11:41:10.570 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/logs/refs/heads/release/rel-1.0.0'. Skipping.
2026-01-16 11:41:10.571 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/zapp.yaml'. Skipping.
2026-01-16 11:41:10.571 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/application-conf/baselineReference.config'. Skipping.
2026-01-16 11:41:10.572 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.project'. Skipping.
2026-01-16 11:41:10.572 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/azure-pipelines.yml'. Skipping.
2026-01-16 11:41:10.573 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/deployment/deployPackage.yml'. Skipping.
2026-01-16 11:41:10.574 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/tagging/createReleaseCandidate.yml'. Skipping.
2026-01-16 11:41:10.574 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/logs/BuildReport.json'. Skipping.
2026-01-16 11:41:10.574 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/logs/BuildReport.html'. Skipping.
2026-01-16 11:41:10.575 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/logs/buildList.txt'. Skipping.
2026-01-16 11:41:10.575 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/logs/RuntimeReport.json'. Skipping.
2026-01-16 11:41:10.576 ** Scanning the files.
2026-01-16 11:41:10.586 ** Storing results in the 'GenApp-main' DBB Collection.
[CMD] /usr/lpp/dbb/v3r0/bin/groovyz /u/mdalbin/Migration-Modeler-MDLB/src/groovy/recreateApplicationDescriptor.groovy               --configFile /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-01-15.175657.config                --application GenApp                --logFile /u/mdalbin/Migration-Modeler-MDLB-work/logs/3-GenApp-createApplicationDescriptor.log
2026-01-16 11:41:39.169 ** Script configuration:
2026-01-16 11:41:39.199     REPOSITORY_PATH_MAPPING_FILE -> /u/mdalbin/Migration-Modeler-MDLB-work/config/repositoryPathsMapping.yaml
2026-01-16 11:41:39.201     application -> GenApp
2026-01-16 11:41:39.202     configurationFilePath -> /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-01-15.175657.config
2026-01-16 11:41:39.203     DBB_MODELER_APPLICATION_DIR -> /u/mdalbin/Migration-Modeler-MDLB-work/repositories
2026-01-16 11:41:39.204     logFile -> /u/mdalbin/Migration-Modeler-MDLB-work/logs/3-GenApp-createApplicationDescriptor.log
2026-01-16 11:41:39.205     DBB_MODELER_APPMAPPINGS_DIR -> /u/mdalbin/Migration-Modeler-MDLB-work/config/applications-mappings
2026-01-16 11:41:39.206 ** Reading the Repository Layout Mapping definition.
2026-01-16 11:41:39.433 ** Reading the Type Mapping definition.
2026-01-16 11:41:39.434 *! [WARNING] No Types File provided. The 'UNKNOWN' type will be assigned by default to all artifacts.
2026-01-16 11:41:39.434 ** Loading the provided Applications Mapping files.
2026-01-16 11:41:39.441 *** Importing 'applicationsMapping.yaml'
2026-01-16 11:41:39.456 ** Importing existing Application Descriptor and reseting source groups, dependencies and consumers.
2026-01-16 11:41:39.468 ** Getting the list of mapped files from '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp'
2026-01-16 11:41:39.509 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/applicationDescriptor.yml'. Skipping.
2026-01-16 11:41:39.516 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.gitattributes'. Skipping.
2026-01-16 11:41:39.523 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgapol01.cbl'. Skipping.
2026-01-16 11:41:39.531 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgtestc1.cbl'. Skipping.
2026-01-16 11:41:39.537 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgacdb01.cbl'. Skipping.
2026-01-16 11:41:39.543 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgapvs01.cbl'. Skipping.
2026-01-16 11:41:39.549 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgdpvs01.cbl'. Skipping.
2026-01-16 11:41:39.554 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgtestp3.cbl'. Skipping.
2026-01-16 11:41:39.558 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgucus01.cbl'. Skipping.
2026-01-16 11:41:39.562 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgastat1.cbl'. Skipping.
2026-01-16 11:41:39.567 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgicdb01.cbl'. Skipping.
2026-01-16 11:41:39.572 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgupvs01.cbl'. Skipping.
2026-01-16 11:41:39.578 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgdpdb01.cbl'. Skipping.
2026-01-16 11:41:39.582 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgucvs01.cbl'. Skipping.
2026-01-16 11:41:39.587 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgipdb01.cbl'. Skipping.
2026-01-16 11:41:39.591 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgtestp2.cbl'. Skipping.
2026-01-16 11:41:39.595 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgupol01.cbl'. Skipping.
2026-01-16 11:41:39.598 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgstsq.cbl'. Skipping.
2026-01-16 11:41:39.602 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgacus01.cbl'. Skipping.
2026-01-16 11:41:39.605 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgsetup.cbl'. Skipping.
2026-01-16 11:41:39.611 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgacvs01.cbl'. Skipping.
2026-01-16 11:41:39.616 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgacdb02.cbl'. Skipping.
2026-01-16 11:41:39.619 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgtestp4.cbl'. Skipping.
2026-01-16 11:41:39.623 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgicvs01.cbl'. Skipping.
2026-01-16 11:41:39.628 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgwebst5.cbl'. Skipping.
2026-01-16 11:41:39.632 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgapdb01.cbl'. Skipping.
2026-01-16 11:41:39.634 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgdpol01.cbl'. Skipping.
2026-01-16 11:41:39.636 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgipvs01.cbl'. Skipping.
2026-01-16 11:41:39.639 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgucdb01.cbl'. Skipping.
2026-01-16 11:41:39.642 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgtestp1.cbl'. Skipping.
2026-01-16 11:41:39.646 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgipol01.cbl'. Skipping.
2026-01-16 11:41:39.649 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgicus01.cbl'. Skipping.
2026-01-16 11:41:39.652 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgupdb01.cbl'. Skipping.
2026-01-16 11:41:39.654 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/bms/ssmap.bms'. Skipping.
2026-01-16 11:41:39.657 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/copy/lgcmared.cpy'. Skipping.
2026-01-16 11:41:39.659 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/copy/lgcmarea.cpy'. Skipping.
2026-01-16 11:41:39.661 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/copy/lgpolicy.cpy'. Skipping.
2026-01-16 11:41:39.662 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/config/CBLCICSDB2.yaml'. Skipping.
2026-01-16 11:41:39.665 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/dbb-app.yaml'. Skipping.
2026-01-16 11:41:39.668 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/description'. Skipping.
2026-01-16 11:41:39.669 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/applypatch-msg.sample'. Skipping.
2026-01-16 11:41:39.671 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/commit-msg.sample'. Skipping.
2026-01-16 11:41:39.672 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/fsmonitor-watchman.sample'. Skipping.
2026-01-16 11:41:39.673 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/post-update.sample'. Skipping.
2026-01-16 11:41:39.674 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/pre-applypatch.sample'. Skipping.
2026-01-16 11:41:39.675 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/pre-commit.sample'. Skipping.
2026-01-16 11:41:39.676 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/pre-merge-commit.sample'. Skipping.
2026-01-16 11:41:39.677 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/prepare-commit-msg.sample'. Skipping.
2026-01-16 11:41:39.678 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/pre-push.sample'. Skipping.
2026-01-16 11:41:39.679 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/pre-rebase.sample'. Skipping.
2026-01-16 11:41:39.680 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/pre-receive.sample'. Skipping.
2026-01-16 11:41:39.681 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/push-to-checkout.sample'. Skipping.
2026-01-16 11:41:39.682 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/sendemail-validate.sample'. Skipping.
2026-01-16 11:41:39.683 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/update.sample'. Skipping.
2026-01-16 11:41:39.684 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/info/exclude'. Skipping.
2026-01-16 11:41:39.685 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/refs/heads/release/rel-1.0.0'. Skipping.
2026-01-16 11:41:39.686 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/refs/heads/main'. Skipping.
2026-01-16 11:41:39.687 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/refs/tags/rel-1.0.0'. Skipping.
2026-01-16 11:41:39.687 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/config'. Skipping.
2026-01-16 11:41:39.689 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/36/512daf923067d0311f15995925416be25290b5'. Skipping.
2026-01-16 11:41:39.690 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/8d/0eaea2f7e487513d472afe1a66d7da07f663b9'. Skipping.
2026-01-16 11:41:39.691 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/7d/f90877fb98ccba6508a94e6fe3ff1ad865d682'. Skipping.
2026-01-16 11:41:39.692 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/d8/f18d43e8afa308163aebcff561e7dedf67759e'. Skipping.
2026-01-16 11:41:39.693 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/ed/7e8c1b79aaa76736f0af3b735f667d3d26ad36'. Skipping.
2026-01-16 11:41:39.694 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/7e/36d0d65c7ae8ca0ce7a451692820010cf2c51f'. Skipping.
2026-01-16 11:41:39.695 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/17/cd1d6b0325b04277c7fc7a1ec27ce9bcbd2598'. Skipping.
2026-01-16 11:41:39.696 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/e1/52fbd8c03e836ad0046953854f04b4665d75b9'. Skipping.
2026-01-16 11:41:39.697 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/28/2aa20f6c7d61d15b8922c8d8e0552880351472'. Skipping.
2026-01-16 11:41:39.698 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/28/0e6f742c84b40da642115cad3a0c86aa9c0aac'. Skipping.
2026-01-16 11:41:39.699 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/13/0e880deea1c41c3ba7e57cbb0aa4e19f5ce9ad'. Skipping.
2026-01-16 11:41:39.699 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/1b/9d6bcb233214bd016ac6ffd87d5b4e5a0644cc'. Skipping.
2026-01-16 11:41:39.700 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/22/b550bafdc6e9f5103b1a28ca501d6bdae4ec76'. Skipping.
2026-01-16 11:41:39.701 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/e5/86c7d2e00e602158da102e4c8d30deaeb142ae'. Skipping.
2026-01-16 11:41:39.703 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/0d/b601b1f055ea023e104c7d24ab0ef5eea1ff05'. Skipping.
2026-01-16 11:41:39.704 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/12/5b26f553c5647a5aabc69a45f0191aed5d5e01'. Skipping.
2026-01-16 11:41:39.704 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/12/6b62bf8e868002e76f970f411637e7488e05bb'. Skipping.
2026-01-16 11:41:39.705 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/42/d3f2e669c2f9f6cf9565e61b2a3f96ad1ff503'. Skipping.
2026-01-16 11:41:39.706 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/fa/ffcce01f2da721aa453f5dda21d11f8d3ae693'. Skipping.
2026-01-16 11:41:39.707 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/16/73ab0e7f0e1744ab58379576e6c835d4108474'. Skipping.
2026-01-16 11:41:39.708 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/b0/49dc9735257281c334afd74730dee59c62e2e8'. Skipping.
2026-01-16 11:41:39.709 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/f7/09ff109986301f101a1912b9d043756d7e596a'. Skipping.
2026-01-16 11:41:39.709 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/f7/1aff317ceeb25dca6e497b93a6ff9a5c8e2518'. Skipping.
2026-01-16 11:41:39.710 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/a0/b94e23333057ca37382048c4f7fc6f2e0df75b'. Skipping.
2026-01-16 11:41:39.711 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/d4/24e6a718eb9ad584e21f7a899488500484f7e2'. Skipping.
2026-01-16 11:41:39.712 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/d9/455ae3c356b0e7a2440914f564ddbcbe30e28d'. Skipping.
2026-01-16 11:41:39.713 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/83/2f54aa68fe84f78461085d00e3b3206e39fdb7'. Skipping.
2026-01-16 11:41:39.714 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/6e/a11cb2dc20aa126f08701fe873ac2dae5ce0b6'. Skipping.
2026-01-16 11:41:39.715 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/2e/f0cfc9de9ca7521899a87cf9e216be7f109d88'. Skipping.
2026-01-16 11:41:39.715 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/82/766939ca20dfac5d9ab33782e4f45b2ade19fc'. Skipping.
2026-01-16 11:41:39.716 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/9a/a1e257384925e8015d7e0864175961ce258290'. Skipping.
2026-01-16 11:41:39.717 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/78/e7f1d24d01d4949e80fc149026a9d902eac1b9'. Skipping.
2026-01-16 11:41:39.718 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/98/11fa56e0556c5d884a98ae06f7d007f64edafa'. Skipping.
2026-01-16 11:41:39.719 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/37/0f90c505893d5ab01089e66e04528f8d40dab1'. Skipping.
2026-01-16 11:41:39.719 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/bf/a3623bc647efd22c9550939cd8d5bf72cb91ad'. Skipping.
2026-01-16 11:41:39.720 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/3e/9eed6daafd969231900049360b526396bf4091'. Skipping.
2026-01-16 11:41:39.721 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/de/85d8fbe9f576dabc377e29616bc4e8fcf68a56'. Skipping.
2026-01-16 11:41:39.721 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/c5/ea6c1fed91fd2154ac3f38533455da5481d974'. Skipping.
2026-01-16 11:41:39.722 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/b6/53161403e5df737d6e540d8c5a1988a043eafc'. Skipping.
2026-01-16 11:41:39.723 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/cd/d0f9ff2c8b8046cce071bae83571570bc073e4'. Skipping.
2026-01-16 11:41:39.724 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/6f/f0de34ffe1105f2acfa133f76f4c2a9fe2c95d'. Skipping.
2026-01-16 11:41:39.724 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/72/756588cafd0baa190b8cbe9d51e697be473354'. Skipping.
2026-01-16 11:41:39.725 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/b2/7bd4267d2f3d7188095a0f549fb5cd87009a5d'. Skipping.
2026-01-16 11:41:39.726 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/15/11e2f080004f3829fc9de21a0b681684da7ff7'. Skipping.
2026-01-16 11:41:39.726 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/81/80f5cd516eb542440a4573db174af1369f44a8'. Skipping.
2026-01-16 11:41:39.727 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/d1/e33757aa74694d0039e8162918a840172d24f8'. Skipping.
2026-01-16 11:41:39.728 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/59/fee299f9b88edb6925432ca41dc8a51dd50b97'. Skipping.
2026-01-16 11:41:39.729 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/76/be470b4b4450038992dec6a9f9ac90a8611f2b'. Skipping.
2026-01-16 11:41:39.729 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/75/1def8d69161d12e708eb37c902e2923fef560b'. Skipping.
2026-01-16 11:41:39.730 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/b4/a48625756286d8d8159714882707ed2da27fda'. Skipping.
2026-01-16 11:41:39.731 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/31/94c3fdd79ab7638c2cc6c2c328829028c02c51'. Skipping.
2026-01-16 11:41:39.732 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/8a/c3edea77f489aa91c07e6834824fa89412ab32'. Skipping.
2026-01-16 11:41:39.733 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/f0/f472bd00702a4d900984524a41504dd6359755'. Skipping.
2026-01-16 11:41:39.733 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/b3/3fe2dd683b6c6457cb46d0e3fa96d46fcfdd21'. Skipping.
2026-01-16 11:41:39.734 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/55/71999ba294064ffb9518ffc6f3775a59ab742b'. Skipping.
2026-01-16 11:41:39.735 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/d0/cba9510054564db07d0a2e000e91337c10f3ec'. Skipping.
2026-01-16 11:41:39.735 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/HEAD'. Skipping.
2026-01-16 11:41:39.736 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/index'. Skipping.
2026-01-16 11:41:39.736 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/COMMIT_EDITMSG'. Skipping.
2026-01-16 11:41:39.737 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/logs/HEAD'. Skipping.
2026-01-16 11:41:39.738 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/logs/refs/heads/main'. Skipping.
2026-01-16 11:41:39.739 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/logs/refs/heads/release/rel-1.0.0'. Skipping.
2026-01-16 11:41:39.739 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/zapp.yaml'. Skipping.
2026-01-16 11:41:39.740 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/application-conf/baselineReference.config'. Skipping.
2026-01-16 11:41:39.740 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.project'. Skipping.
2026-01-16 11:41:39.741 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/azure-pipelines.yml'. Skipping.
2026-01-16 11:41:39.741 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/deployment/deployPackage.yml'. Skipping.
2026-01-16 11:41:39.742 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/tagging/createReleaseCandidate.yml'. Skipping.
2026-01-16 11:41:39.743 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/logs/BuildReport.json'. Skipping.
2026-01-16 11:41:39.743 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/logs/BuildReport.html'. Skipping.
2026-01-16 11:41:39.744 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/logs/buildList.txt'. Skipping.
2026-01-16 11:41:39.744 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/logs/RuntimeReport.json'. Skipping.
2026-01-16 11:41:39.751 *! [WARNING] Some files were skipped as no matching Source Group was found based on their path. Check log file '/u/mdalbin/Migration-Modeler-MDLB-work/logs/3-GenApp-createApplicationDescriptor.log'.
2026-01-16 11:41:39.805 ** Created Application Description file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/applicationDescriptor.yml'
[INFO] /usr/lpp/dbb/v3r0/bin/groovyz /u/mdalbin/Migration-Modeler-MDLB/src/groovy/assessUsage.groovy                --configFile /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-01-15.175657.config                --application GenApp                --logFile /u/mdalbin/Migration-Modeler-MDLB-work/logs/3-GenApp-assessUsage.log
2026-01-15 17:13:43.173 ** Script configuration:
2026-01-15 17:13:43.173     DBB_MODELER_APPCONFIG_DIR -> /u/mdalbin/Migration-Modeler-MDLB-work/work/migration-configuration
2026-01-15 17:13:43.173     MOVE_FILES_FLAG -> true
2026-01-15 17:13:43.173     REPOSITORY_PATH_MAPPING_FILE -> /u/mdalbin/Migration-Modeler-MDLB-work/config/repositoryPathsMapping.yaml
2026-01-15 17:13:43.174     SCAN_CONTROL_TRANSFERS -> true
2026-01-15 17:13:43.174     application -> GenApp
2026-01-15 17:13:43.174     configurationFilePath -> /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-01-15.175657.config
2026-01-15 17:13:43.174     DBB_MODELER_APPLICATION_DIR -> /u/mdalbin/Migration-Modeler-MDLB-work/repositories
2026-01-15 17:13:43.174     DBB_MODELER_FILE_METADATA_STORE_DIR -> /u/mdalbin/Migration-Modeler-MDLB-work/work/dbb-filemetadatastore
2026-01-15 17:13:43.174     logFile -> /u/mdalbin/Migration-Modeler-MDLB-work/logs/3-GenApp-assessUsage.log
2026-01-15 17:13:43.175     DBB_MODELER_METADATASTORE_TYPE -> file
2026-01-15 17:13:43.175     APPLICATION_DEFAULT_BRANCH -> main
2026-01-15 17:13:43.178 ** Reading the Repository Layout Mapping definition.
2026-01-15 17:13:43.179 ** Getting the list of files of 'Include File' type.
2026-01-15 17:13:43.183 ** Analyzing impacted applications for file 'GenApp/GenApp/src/copy/lgcmarea.cpy'.
2026-01-15 17:13:43.224     Files depending on 'GenApp/src/copy/lgcmarea.cpy' :
2026-01-15 17:13:43.224     'GenApp/GenApp/src/cobol/lgdpol01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.224     'GenApp/GenApp/src/cobol/lgtestp3.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.224     'GenApp/GenApp/src/cobol/lgipol01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.225     'GenApp/GenApp/src/cobol/lgupol01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.225     'GenApp/GenApp/src/cobol/lgastat1.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.225     'GenApp/GenApp/src/cobol/lgacvs01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.225     'GenApp/GenApp/src/cobol/lgucus01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.225     'GenApp/GenApp/src/cobol/lgapdb01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.225     'UNASSIGNED/UNASSIGNED/src/cobol/oldacdb1.cbl' in  Application  'UNASSIGNED'
2026-01-15 17:13:43.225     'GenApp/GenApp/src/cobol/lgdpdb01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.226     'GenApp/GenApp/src/cobol/lgacdb01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.226     'GenApp/GenApp/src/cobol/lgtestp2.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.226     'GenApp/GenApp/src/cobol/lgtestc1.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.226     'GenApp/GenApp/src/cobol/lgicdb01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.226     'GenApp/GenApp/src/cobol/lgapol01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.226     'GenApp/GenApp/src/cobol/lgicus01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.226     'GenApp/GenApp/src/cobol/lgupdb01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.226     'UNASSIGNED/UNASSIGNED/src/cobol/oldacdb2.cbl' in  Application  'UNASSIGNED'
2026-01-15 17:13:43.226     'GenApp/GenApp/src/cobol/lgucvs01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.227     'GenApp/GenApp/src/cobol/lgtestp1.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.227     'GenApp/GenApp/src/cobol/lgapvs01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.227     'GenApp/GenApp/src/cobol/lgupvs01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.227     'GenApp/GenApp/src/cobol/lgucdb01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.227     'GenApp/GenApp/src/cobol/lgtestp4.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.227     'GenApp/GenApp/src/cobol/lgdpvs01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.227     'GenApp/GenApp/src/cobol/lgacus01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.227     'GenApp/GenApp/src/cobol/lgipdb01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.228     ==> 'lgcmarea' referenced by multiple applications - [UNASSIGNED, GenApp]
2026-01-15 17:13:43.229     ==> Updating usage of Include File 'lgcmarea' to 'public' in '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/applicationDescriptor.yml'.
2026-01-15 17:13:43.245 ** Analyzing impacted applications for file 'GenApp/GenApp/src/copy/lgcmared.cpy'.
2026-01-15 17:13:43.250     The Include File 'lgcmared' is not referenced at all.
2026-01-15 17:13:43.253 ** Analyzing impacted applications for file 'GenApp/GenApp/src/copy/lgpolicy.cpy'.
2026-01-15 17:13:43.270     Files depending on 'GenApp/src/copy/lgpolicy.cpy' :
2026-01-15 17:13:43.270     'UNASSIGNED/UNASSIGNED/src/cobol/oldacdb1.cbl' in  Application  'UNASSIGNED'
2026-01-15 17:13:43.270     'GenApp/GenApp/src/cobol/lgipol01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.270     'GenApp/GenApp/src/cobol/lgicus01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.270     'GenApp/GenApp/src/cobol/lgacdb01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.270     'GenApp/GenApp/src/cobol/lgucdb01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.270     'GenApp/GenApp/src/cobol/lgupdb01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.271     'UNASSIGNED/UNASSIGNED/src/cobol/oldacdb2.cbl' in  Application  'UNASSIGNED'
2026-01-15 17:13:43.271     'GenApp/GenApp/src/cobol/lgacus01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.271     'GenApp/GenApp/src/cobol/lgicdb01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.271     'GenApp/GenApp/src/cobol/lgipdb01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.271     'GenApp/GenApp/src/cobol/lgapdb01.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.271     'GenApp/GenApp/src/cobol/lgacdb02.cbl' in  Application  'GenApp'
2026-01-15 17:13:43.271     ==> 'lgpolicy' referenced by multiple applications - [UNASSIGNED, GenApp]
2026-01-15 17:13:43.271     ==> Updating usage of Include File 'lgpolicy' to 'public' in '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/applicationDescriptor.yml'.
2026-01-15 17:13:43.278 ** Getting the list of files of 'Program' type.
2026-01-15 17:13:43.279 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgicus01.cbl'.
2026-01-15 17:13:43.286     The Program 'lgicus01' is not statically called by any other program.
2026-01-15 17:13:43.289 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgdpol01.cbl'.
2026-01-15 17:13:43.295     The Program 'lgdpol01' is not statically called by any other program.
2026-01-15 17:13:43.298 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgipdb01.cbl'.
2026-01-15 17:13:43.307     The Program 'lgipdb01' is not statically called by any other program.
2026-01-15 17:13:43.309 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgtestp3.cbl'.
2026-01-15 17:13:43.315     The Program 'lgtestp3' is not statically called by any other program.
2026-01-15 17:13:43.318 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgtestp4.cbl'.
2026-01-15 17:13:43.323     The Program 'lgtestp4' is not statically called by any other program.
2026-01-15 17:13:43.326 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgacvs01.cbl'.
2026-01-15 17:13:43.331     The Program 'lgacvs01' is not statically called by any other program.
2026-01-15 17:13:43.333 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgsetup.cbl'.
2026-01-15 17:13:43.340     The Program 'lgsetup' is not statically called by any other program.
2026-01-15 17:13:43.343 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgapol01.cbl'.
2026-01-15 17:13:43.349     The Program 'lgapol01' is not statically called by any other program.
2026-01-15 17:13:43.352 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgipvs01.cbl'.
2026-01-15 17:13:43.357     The Program 'lgipvs01' is not statically called by any other program.
2026-01-15 17:13:43.360 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgupol01.cbl'.
2026-01-15 17:13:43.366     The Program 'lgupol01' is not statically called by any other program.
2026-01-15 17:13:43.368 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgacdb01.cbl'.
2026-01-15 17:13:43.374     The Program 'lgacdb01' is not statically called by any other program.
2026-01-15 17:13:43.377 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgacdb02.cbl'.
2026-01-15 17:13:43.382     The Program 'lgacdb02' is not statically called by any other program.
2026-01-15 17:13:43.384 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgstsq.cbl'.
2026-01-15 17:13:43.395     The Program 'lgstsq' is not statically called by any other program.
2026-01-15 17:13:43.397 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgtestp1.cbl'.
2026-01-15 17:13:43.403     The Program 'lgtestp1' is not statically called by any other program.
2026-01-15 17:13:43.406 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgtestp2.cbl'.
2026-01-15 17:13:43.412     The Program 'lgtestp2' is not statically called by any other program.
2026-01-15 17:13:43.414 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgdpdb01.cbl'.
2026-01-15 17:13:43.419     The Program 'lgdpdb01' is not statically called by any other program.
2026-01-15 17:13:43.422 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgucus01.cbl'.
2026-01-15 17:13:43.427     The Program 'lgucus01' is not statically called by any other program.
2026-01-15 17:13:43.430 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgapvs01.cbl'.
2026-01-15 17:13:43.435     The Program 'lgapvs01' is not statically called by any other program.
2026-01-15 17:13:43.438 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgucdb01.cbl'.
2026-01-15 17:13:43.443     The Program 'lgucdb01' is not statically called by any other program.
2026-01-15 17:13:43.445 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgdpvs01.cbl'.
2026-01-15 17:13:43.450     The Program 'lgdpvs01' is not statically called by any other program.
2026-01-15 17:13:43.453 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgtestc1.cbl'.
2026-01-15 17:13:43.459     The Program 'lgtestc1' is not statically called by any other program.
2026-01-15 17:13:43.461 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgastat1.cbl'.
2026-01-15 17:13:43.466     The Program 'lgastat1' is not statically called by any other program.
2026-01-15 17:13:43.469 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgapdb01.cbl'.
2026-01-15 17:13:43.475     The Program 'lgapdb01' is not statically called by any other program.
2026-01-15 17:13:43.478 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgicvs01.cbl'.
2026-01-15 17:13:43.483     The Program 'lgicvs01' is not statically called by any other program.
2026-01-15 17:13:43.486 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgipol01.cbl'.
2026-01-15 17:13:43.492     The Program 'lgipol01' is not statically called by any other program.
2026-01-15 17:13:43.494 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgacus01.cbl'.
2026-01-15 17:13:43.500     The Program 'lgacus01' is not statically called by any other program.
2026-01-15 17:13:43.503 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgwebst5.cbl'.
2026-01-15 17:13:43.511     The Program 'lgwebst5' is not statically called by any other program.
2026-01-15 17:13:43.514 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgucvs01.cbl'.
2026-01-15 17:13:43.519     The Program 'lgucvs01' is not statically called by any other program.
2026-01-15 17:13:43.521 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgupdb01.cbl'.
2026-01-15 17:13:43.528     The Program 'lgupdb01' is not statically called by any other program.
2026-01-15 17:13:43.530 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgicdb01.cbl'.
2026-01-15 17:13:43.535     The Program 'lgicdb01' is not statically called by any other program.
2026-01-15 17:13:43.538 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgupvs01.cbl'.
2026-01-15 17:13:43.544     The Program 'lgupvs01' is not statically called by any other program.
[INFO] /usr/lpp/dbb/v3r0/bin/groovyz /u/mdalbin/Migration-Modeler-MDLB/src/groovy/assessUsage.groovy                --configFile /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-01-15.175657.config                --application GenApp                --moveFiles                 --logFile /u/mdalbin/Migration-Modeler-MDLB-work/logs/3-GenApp-assessUsage.log
[INFO] /usr/lpp/dbb/v3r0/bin/groovyz /u/mdalbin/Migration-Modeler-MDLB/src/groovy/assessUsage.groovy                --configFile /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-01-15.175657.config                --application GenApp                --moveFiles                 --logFile /u/mdalbin/Migration-Modeler-MDLB-work/logs/3-GenApp-assessUsage.log
[INFO] /usr/lpp/dbb/v3r0/bin/groovyz /u/mdalbin/Migration-Modeler-MDLB/src/groovy/scanApplication.groovy                --configFile /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-01-15.175657.config                --application GenApp                --logFile /u/mdalbin/Migration-Modeler-MDLB-work/logs/3-GenApp-rescan.log
2026-01-16 11:42:49.236 ** Script configuration:
2026-01-16 11:42:49.267     REPOSITORY_PATH_MAPPING_FILE -> /u/mdalbin/Migration-Modeler-MDLB-work/config/repositoryPathsMapping.yaml
2026-01-16 11:42:49.268     SCAN_CONTROL_TRANSFERS -> true
2026-01-16 11:42:49.268     application -> GenApp
2026-01-16 11:42:49.269     configurationFilePath -> /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-01-15.175657.config
2026-01-16 11:42:49.270     DBB_MODELER_APPLICATION_DIR -> /u/mdalbin/Migration-Modeler-MDLB-work/repositories
2026-01-16 11:42:49.271     DBB_MODELER_FILE_METADATA_STORE_DIR -> /u/mdalbin/Migration-Modeler-MDLB-work/work/dbb-filemetadatastore
2026-01-16 11:42:49.272     logFile -> /u/mdalbin/Migration-Modeler-MDLB-work/logs/3-GenApp-rescan.log
2026-01-16 11:42:49.273     DBB_MODELER_METADATASTORE_TYPE -> file
2026-01-16 11:42:49.275     APPLICATION_DEFAULT_BRANCH -> main
2026-01-16 11:42:49.284 ** Reading the existing Application Descriptor file.
2026-01-16 11:42:49.488 ** Retrieving the list of files mapped to Source Groups.
2026-01-16 11:42:49.536 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/applicationDescriptor.yml'. Skipping.
2026-01-16 11:42:49.540 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.gitattributes'. Skipping.
2026-01-16 11:42:49.543 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgapol01.cbl'. Skipping.
2026-01-16 11:42:49.544 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgtestc1.cbl'. Skipping.
2026-01-16 11:42:49.545 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgacdb01.cbl'. Skipping.
2026-01-16 11:42:49.547 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgapvs01.cbl'. Skipping.
2026-01-16 11:42:49.548 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgdpvs01.cbl'. Skipping.
2026-01-16 11:42:49.550 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgtestp3.cbl'. Skipping.
2026-01-16 11:42:49.551 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgucus01.cbl'. Skipping.
2026-01-16 11:42:49.552 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgastat1.cbl'. Skipping.
2026-01-16 11:42:49.554 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgicdb01.cbl'. Skipping.
2026-01-16 11:42:49.556 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgupvs01.cbl'. Skipping.
2026-01-16 11:42:49.558 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgdpdb01.cbl'. Skipping.
2026-01-16 11:42:49.559 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgucvs01.cbl'. Skipping.
2026-01-16 11:42:49.560 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgipdb01.cbl'. Skipping.
2026-01-16 11:42:49.561 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgtestp2.cbl'. Skipping.
2026-01-16 11:42:49.563 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgupol01.cbl'. Skipping.
2026-01-16 11:42:49.564 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgstsq.cbl'. Skipping.
2026-01-16 11:42:49.565 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgacus01.cbl'. Skipping.
2026-01-16 11:42:49.566 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgsetup.cbl'. Skipping.
2026-01-16 11:42:49.567 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgacvs01.cbl'. Skipping.
2026-01-16 11:42:49.569 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgacdb02.cbl'. Skipping.
2026-01-16 11:42:49.570 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgtestp4.cbl'. Skipping.
2026-01-16 11:42:49.571 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgicvs01.cbl'. Skipping.
2026-01-16 11:42:49.572 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgwebst5.cbl'. Skipping.
2026-01-16 11:42:49.573 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgapdb01.cbl'. Skipping.
2026-01-16 11:42:49.574 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgdpol01.cbl'. Skipping.
2026-01-16 11:42:49.576 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgipvs01.cbl'. Skipping.
2026-01-16 11:42:49.577 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgucdb01.cbl'. Skipping.
2026-01-16 11:42:49.578 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgtestp1.cbl'. Skipping.
2026-01-16 11:42:49.579 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgipol01.cbl'. Skipping.
2026-01-16 11:42:49.581 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgicus01.cbl'. Skipping.
2026-01-16 11:42:49.582 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/cobol/lgupdb01.cbl'. Skipping.
2026-01-16 11:42:49.583 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/bms/ssmap.bms'. Skipping.
2026-01-16 11:42:49.584 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/copy/lgcmared.cpy'. Skipping.
2026-01-16 11:42:49.585 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/copy/lgcmarea.cpy'. Skipping.
2026-01-16 11:42:49.587 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/GenApp/src/copy/lgpolicy.cpy'. Skipping.
2026-01-16 11:42:49.588 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/config/CBLCICSDB2.yaml'. Skipping.
2026-01-16 11:42:49.589 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/dbb-app.yaml'. Skipping.
2026-01-16 11:42:49.590 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/description'. Skipping.
2026-01-16 11:42:49.591 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/applypatch-msg.sample'. Skipping.
2026-01-16 11:42:49.592 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/commit-msg.sample'. Skipping.
2026-01-16 11:42:49.593 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/fsmonitor-watchman.sample'. Skipping.
2026-01-16 11:42:49.595 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/post-update.sample'. Skipping.
2026-01-16 11:42:49.596 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/pre-applypatch.sample'. Skipping.
2026-01-16 11:42:49.597 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/pre-commit.sample'. Skipping.
2026-01-16 11:42:49.598 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/pre-merge-commit.sample'. Skipping.
2026-01-16 11:42:49.599 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/prepare-commit-msg.sample'. Skipping.
2026-01-16 11:42:49.600 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/pre-push.sample'. Skipping.
2026-01-16 11:42:49.602 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/pre-rebase.sample'. Skipping.
2026-01-16 11:42:49.603 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/pre-receive.sample'. Skipping.
2026-01-16 11:42:49.604 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/push-to-checkout.sample'. Skipping.
2026-01-16 11:42:49.605 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/sendemail-validate.sample'. Skipping.
2026-01-16 11:42:49.606 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/hooks/update.sample'. Skipping.
2026-01-16 11:42:49.608 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/info/exclude'. Skipping.
2026-01-16 11:42:49.609 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/refs/heads/release/rel-1.0.0'. Skipping.
2026-01-16 11:42:49.611 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/refs/heads/main'. Skipping.
2026-01-16 11:42:49.613 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/refs/tags/rel-1.0.0'. Skipping.
2026-01-16 11:42:49.614 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/config'. Skipping.
2026-01-16 11:42:49.617 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/36/512daf923067d0311f15995925416be25290b5'. Skipping.
2026-01-16 11:42:49.618 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/8d/0eaea2f7e487513d472afe1a66d7da07f663b9'. Skipping.
2026-01-16 11:42:49.621 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/7d/f90877fb98ccba6508a94e6fe3ff1ad865d682'. Skipping.
2026-01-16 11:42:49.623 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/d8/f18d43e8afa308163aebcff561e7dedf67759e'. Skipping.
2026-01-16 11:42:49.625 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/ed/7e8c1b79aaa76736f0af3b735f667d3d26ad36'. Skipping.
2026-01-16 11:42:49.627 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/7e/36d0d65c7ae8ca0ce7a451692820010cf2c51f'. Skipping.
2026-01-16 11:42:49.629 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/17/cd1d6b0325b04277c7fc7a1ec27ce9bcbd2598'. Skipping.
2026-01-16 11:42:49.630 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/e1/52fbd8c03e836ad0046953854f04b4665d75b9'. Skipping.
2026-01-16 11:42:49.632 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/28/2aa20f6c7d61d15b8922c8d8e0552880351472'. Skipping.
2026-01-16 11:42:49.633 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/28/0e6f742c84b40da642115cad3a0c86aa9c0aac'. Skipping.
2026-01-16 11:42:49.634 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/13/0e880deea1c41c3ba7e57cbb0aa4e19f5ce9ad'. Skipping.
2026-01-16 11:42:49.635 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/1b/9d6bcb233214bd016ac6ffd87d5b4e5a0644cc'. Skipping.
2026-01-16 11:42:49.636 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/22/b550bafdc6e9f5103b1a28ca501d6bdae4ec76'. Skipping.
2026-01-16 11:42:49.637 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/e5/86c7d2e00e602158da102e4c8d30deaeb142ae'. Skipping.
2026-01-16 11:42:49.639 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/0d/b601b1f055ea023e104c7d24ab0ef5eea1ff05'. Skipping.
2026-01-16 11:42:49.640 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/12/5b26f553c5647a5aabc69a45f0191aed5d5e01'. Skipping.
2026-01-16 11:42:49.642 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/12/6b62bf8e868002e76f970f411637e7488e05bb'. Skipping.
2026-01-16 11:42:49.643 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/42/d3f2e669c2f9f6cf9565e61b2a3f96ad1ff503'. Skipping.
2026-01-16 11:42:49.644 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/fa/ffcce01f2da721aa453f5dda21d11f8d3ae693'. Skipping.
2026-01-16 11:42:49.645 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/16/73ab0e7f0e1744ab58379576e6c835d4108474'. Skipping.
2026-01-16 11:42:49.646 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/b0/49dc9735257281c334afd74730dee59c62e2e8'. Skipping.
2026-01-16 11:42:49.647 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/f7/09ff109986301f101a1912b9d043756d7e596a'. Skipping.
2026-01-16 11:42:49.649 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/f7/1aff317ceeb25dca6e497b93a6ff9a5c8e2518'. Skipping.
2026-01-16 11:42:49.651 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/a0/b94e23333057ca37382048c4f7fc6f2e0df75b'. Skipping.
2026-01-16 11:42:49.653 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/d4/24e6a718eb9ad584e21f7a899488500484f7e2'. Skipping.
2026-01-16 11:42:49.654 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/d9/455ae3c356b0e7a2440914f564ddbcbe30e28d'. Skipping.
2026-01-16 11:42:49.655 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/83/2f54aa68fe84f78461085d00e3b3206e39fdb7'. Skipping.
2026-01-16 11:42:49.656 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/6e/a11cb2dc20aa126f08701fe873ac2dae5ce0b6'. Skipping.
2026-01-16 11:42:49.657 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/2e/f0cfc9de9ca7521899a87cf9e216be7f109d88'. Skipping.
2026-01-16 11:42:49.658 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/82/766939ca20dfac5d9ab33782e4f45b2ade19fc'. Skipping.
2026-01-16 11:42:49.659 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/9a/a1e257384925e8015d7e0864175961ce258290'. Skipping.
2026-01-16 11:42:49.660 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/78/e7f1d24d01d4949e80fc149026a9d902eac1b9'. Skipping.
2026-01-16 11:42:49.661 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/98/11fa56e0556c5d884a98ae06f7d007f64edafa'. Skipping.
2026-01-16 11:42:49.662 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/37/0f90c505893d5ab01089e66e04528f8d40dab1'. Skipping.
2026-01-16 11:42:49.663 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/bf/a3623bc647efd22c9550939cd8d5bf72cb91ad'. Skipping.
2026-01-16 11:42:49.664 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/3e/9eed6daafd969231900049360b526396bf4091'. Skipping.
2026-01-16 11:42:49.665 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/de/85d8fbe9f576dabc377e29616bc4e8fcf68a56'. Skipping.
2026-01-16 11:42:49.666 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/c5/ea6c1fed91fd2154ac3f38533455da5481d974'. Skipping.
2026-01-16 11:42:49.667 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/b6/53161403e5df737d6e540d8c5a1988a043eafc'. Skipping.
2026-01-16 11:42:49.667 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/cd/d0f9ff2c8b8046cce071bae83571570bc073e4'. Skipping.
2026-01-16 11:42:49.668 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/6f/f0de34ffe1105f2acfa133f76f4c2a9fe2c95d'. Skipping.
2026-01-16 11:42:49.669 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/72/756588cafd0baa190b8cbe9d51e697be473354'. Skipping.
2026-01-16 11:42:49.670 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/b2/7bd4267d2f3d7188095a0f549fb5cd87009a5d'. Skipping.
2026-01-16 11:42:49.670 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/15/11e2f080004f3829fc9de21a0b681684da7ff7'. Skipping.
2026-01-16 11:42:49.671 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/81/80f5cd516eb542440a4573db174af1369f44a8'. Skipping.
2026-01-16 11:42:49.672 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/d1/e33757aa74694d0039e8162918a840172d24f8'. Skipping.
2026-01-16 11:42:49.673 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/59/fee299f9b88edb6925432ca41dc8a51dd50b97'. Skipping.
2026-01-16 11:42:49.673 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/76/be470b4b4450038992dec6a9f9ac90a8611f2b'. Skipping.
2026-01-16 11:42:49.674 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/75/1def8d69161d12e708eb37c902e2923fef560b'. Skipping.
2026-01-16 11:42:49.675 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/b4/a48625756286d8d8159714882707ed2da27fda'. Skipping.
2026-01-16 11:42:49.676 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/31/94c3fdd79ab7638c2cc6c2c328829028c02c51'. Skipping.
2026-01-16 11:42:49.677 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/8a/c3edea77f489aa91c07e6834824fa89412ab32'. Skipping.
2026-01-16 11:42:49.678 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/f0/f472bd00702a4d900984524a41504dd6359755'. Skipping.
2026-01-16 11:42:49.679 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/b3/3fe2dd683b6c6457cb46d0e3fa96d46fcfdd21'. Skipping.
2026-01-16 11:42:49.679 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/55/71999ba294064ffb9518ffc6f3775a59ab742b'. Skipping.
2026-01-16 11:42:49.680 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/objects/d0/cba9510054564db07d0a2e000e91337c10f3ec'. Skipping.
2026-01-16 11:42:49.681 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/HEAD'. Skipping.
2026-01-16 11:42:49.681 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/index'. Skipping.
2026-01-16 11:42:49.682 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/COMMIT_EDITMSG'. Skipping.
2026-01-16 11:42:49.683 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/logs/HEAD'. Skipping.
2026-01-16 11:42:49.683 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/logs/refs/heads/main'. Skipping.
2026-01-16 11:42:49.684 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/logs/refs/heads/release/rel-1.0.0'. Skipping.
2026-01-16 11:42:49.685 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/zapp.yaml'. Skipping.
2026-01-16 11:42:49.685 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/application-conf/baselineReference.config'. Skipping.
2026-01-16 11:42:49.686 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.project'. Skipping.
2026-01-16 11:42:49.687 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/azure-pipelines.yml'. Skipping.
2026-01-16 11:42:49.687 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/deployment/deployPackage.yml'. Skipping.
2026-01-16 11:42:49.688 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/tagging/createReleaseCandidate.yml'. Skipping.
2026-01-16 11:42:49.689 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/logs/BuildReport.json'. Skipping.
2026-01-16 11:42:49.689 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/logs/BuildReport.html'. Skipping.
2026-01-16 11:42:49.690 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/logs/buildList.txt'. Skipping.
2026-01-16 11:42:49.690 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/logs/RuntimeReport.json'. Skipping.
2026-01-16 11:42:49.691 ** Scanning the files.
2026-01-16 11:42:49.701 ** Storing results in the 'GenApp-main' DBB Collection.

~~~~

</details>