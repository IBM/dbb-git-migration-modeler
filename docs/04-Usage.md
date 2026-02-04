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
         * A `dbb-app.yaml` file is created within each repository's folder in the `DBB_MODELER_APPLICATION_DIR` folder, and contains configuration to enable the use of Language Configurations. The generated `dbb-app.yaml` file also contains configuration to enable impact analysis and dependency search paths for common types of artifact (Cobol, Assembler and LinkEdit artifacts). Additional manual configuration might be required for other types of artifacts.
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
[INFO] /usr/lpp/dbb/v3r0/bin/groovyz /u/mdalbin/Migration-Modeler-MDLB/src/groovy/extractApplications.groovy 		--configFile /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-02-02.093836.config 		--logFile /u/mdalbin/Migration-Modeler-MDLB-work/logs/1-extractApplications.log
2026-02-02 10:16:15.357 ** Script configuration:
2026-02-02 10:16:15.403 	DBB_MODELER_APPCONFIG_DIR -> /u/mdalbin/Migration-Modeler-MDLB-work/work/migration-configuration
2026-02-02 10:16:15.405 	REPOSITORY_PATH_MAPPING_FILE -> /u/mdalbin/Migration-Modeler-MDLB-work/config/repositoryPathsMapping.yaml
2026-02-02 10:16:15.407 	configurationFilePath -> /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-02-02.093836.config
2026-02-02 10:16:15.410 	DBB_MODELER_APPLICATION_DIR -> /u/mdalbin/Migration-Modeler-MDLB-work/repositories
2026-02-02 10:16:15.411 	logFile -> /u/mdalbin/Migration-Modeler-MDLB-work/logs/1-extractApplications.log
2026-02-02 10:16:15.412 	DBB_MODELER_APPMAPPINGS_DIR -> /u/mdalbin/Migration-Modeler-MDLB-work/config/applications-mappings
2026-02-02 10:16:15.414 	SCAN_DATASET_MEMBERS_ENCODING -> IBM-1047
2026-02-02 10:16:15.416 	APPLICATION_TYPES_MAPPING -> /u/mdalbin/Migration-Modeler-MDLB-work/config/types/typesMapping.yaml
2026-02-02 10:16:15.418 	SCAN_DATASET_MEMBERS -> false
2026-02-02 10:16:15.420 ** Reading the Repository Layout Mapping definition.
2026-02-02 10:16:15.669 ** Reading the Type Mapping definition.
2026-02-02 10:16:15.680 ** Loading the provided Applications Mapping files.
2026-02-02 10:16:15.686 *** Importing 'applicationsMapping.yaml'
2026-02-02 10:16:15.709 ** Iterating through the provided datasets and mapped applications.
2026-02-02 10:16:15.727 **** Found 'DBEHM.MIG.BMS' referenced by applications 'RetirementCalculator', 'GenApp', 'CBSA' 
2026-02-02 10:16:15.785 ***** 'DBEHM.MIG.BMS(EPSMLIS)' - Mapped Application: UNASSIGNED
2026-02-02 10:16:15.817 ***** 'DBEHM.MIG.BMS(EPSMORT)' - Mapped Application: UNASSIGNED
2026-02-02 10:16:15.845 ***** 'DBEHM.MIG.BMS(SSMAP)' - Mapped Application: GenApp
2026-02-02 10:16:15.849 **** Found 'DBEHM.MIG.COPY' referenced by applications 'RetirementCalculator', 'GenApp', 'CBSA' 
2026-02-02 10:16:15.860 ***** 'DBEHM.MIG.COPY(ABNDINFO)' - Mapped Application: CBSA
2026-02-02 10:16:15.872 ***** 'DBEHM.MIG.COPY(ACCDB2)' - Mapped Application: CBSA
2026-02-02 10:16:15.881 ***** 'DBEHM.MIG.COPY(ACCOUNT)' - Mapped Application: CBSA
2026-02-02 10:16:15.891 ***** 'DBEHM.MIG.COPY(ACCTCTRL)' - Mapped Application: CBSA
2026-02-02 10:16:15.901 ***** 'DBEHM.MIG.COPY(BNK1ACC)' - Mapped Application: CBSA
2026-02-02 10:16:15.907 ***** 'DBEHM.MIG.COPY(BNK1CAM)' - Mapped Application: CBSA
2026-02-02 10:16:15.915 ***** 'DBEHM.MIG.COPY(BNK1CCM)' - Mapped Application: CBSA
2026-02-02 10:16:15.922 ***** 'DBEHM.MIG.COPY(BNK1CDM)' - Mapped Application: CBSA
2026-02-02 10:16:15.927 ***** 'DBEHM.MIG.COPY(BNK1DAM)' - Mapped Application: CBSA
2026-02-02 10:16:15.932 ***** 'DBEHM.MIG.COPY(BNK1DCM)' - Mapped Application: CBSA
2026-02-02 10:16:15.937 ***** 'DBEHM.MIG.COPY(BNK1MAI)' - Mapped Application: CBSA
2026-02-02 10:16:15.942 ***** 'DBEHM.MIG.COPY(BNK1TFM)' - Mapped Application: CBSA
2026-02-02 10:16:15.946 ***** 'DBEHM.MIG.COPY(BNK1UAM)' - Mapped Application: CBSA
2026-02-02 10:16:15.951 ***** 'DBEHM.MIG.COPY(CONSENT)' - Mapped Application: CBSA
2026-02-02 10:16:15.955 ***** 'DBEHM.MIG.COPY(CONSTAPI)' - Mapped Application: CBSA
2026-02-02 10:16:15.958 ***** 'DBEHM.MIG.COPY(CONSTDB2)' - Mapped Application: CBSA
2026-02-02 10:16:15.962 ***** 'DBEHM.MIG.COPY(CONTDB2)' - Mapped Application: CBSA
2026-02-02 10:16:15.966 ***** 'DBEHM.MIG.COPY(CREACC)' - Mapped Application: CBSA
2026-02-02 10:16:15.970 ***** 'DBEHM.MIG.COPY(CRECUST)' - Mapped Application: CBSA
2026-02-02 10:16:15.974 ***** 'DBEHM.MIG.COPY(CUSTCTRL)' - Mapped Application: CBSA
2026-02-02 10:16:15.978 ***** 'DBEHM.MIG.COPY(CUSTOMER)' - Mapped Application: CBSA
2026-02-02 10:16:15.983 ***** 'DBEHM.MIG.COPY(DATASTR)' - Mapped Application: UNASSIGNED
2026-02-02 10:16:15.986 ***** 'DBEHM.MIG.COPY(DELACC)' - Mapped Application: CBSA
2026-02-02 10:16:15.990 ***** 'DBEHM.MIG.COPY(DELCUS)' - Mapped Application: CBSA
2026-02-02 10:16:15.994 ***** 'DBEHM.MIG.COPY(GETCOMPY)' - Mapped Application: CBSA
2026-02-02 10:16:15.998 ***** 'DBEHM.MIG.COPY(GETSCODE)' - Mapped Application: CBSA
2026-02-02 10:16:16.001 ***** 'DBEHM.MIG.COPY(INQACC)' - Mapped Application: CBSA
2026-02-02 10:16:16.005 ***** 'DBEHM.MIG.COPY(INQACCCU)' - Mapped Application: CBSA
2026-02-02 10:16:16.009 ***** 'DBEHM.MIG.COPY(INQCUST)' - Mapped Application: CBSA
2026-02-02 10:16:16.012 ***** 'DBEHM.MIG.COPY(LGCMAREA)' - Mapped Application: GenApp
2026-02-02 10:16:16.016 ***** 'DBEHM.MIG.COPY(LGCMARED)' - Mapped Application: GenApp
2026-02-02 10:16:16.019 ***** 'DBEHM.MIG.COPY(LGPOLICY)' - Mapped Application: GenApp
2026-02-02 10:16:16.024 ***** 'DBEHM.MIG.COPY(LINPUT)' - Mapped Application: RetirementCalculator
2026-02-02 10:16:16.028 ***** 'DBEHM.MIG.COPY(PAYDBCR)' - Mapped Application: UNASSIGNED
2026-02-02 10:16:16.031 ***** 'DBEHM.MIG.COPY(PROCDB2)' - Mapped Application: CBSA
2026-02-02 10:16:16.035 ***** 'DBEHM.MIG.COPY(PROCTRAN)' - Mapped Application: CBSA
2026-02-02 10:16:16.039 ***** 'DBEHM.MIG.COPY(SORTCODE)' - Mapped Application: UNASSIGNED
2026-02-02 10:16:16.043 ***** 'DBEHM.MIG.COPY(UPDACC)' - Mapped Application: CBSA
2026-02-02 10:16:16.046 ***** 'DBEHM.MIG.COPY(UPDCUST)' - Mapped Application: CBSA
2026-02-02 10:16:16.050 ***** 'DBEHM.MIG.COPY(XFRFUN)' - Mapped Application: CBSA
2026-02-02 10:16:16.052 **** Found 'DBEHM.MIG.COBOL' referenced by applications 'RetirementCalculator', 'GenApp', 'CBSA' 
2026-02-02 10:16:16.060 ***** 'DBEHM.MIG.COBOL(ABNDPROC)' - Mapped Application: CBSA
2026-02-02 10:16:16.062 ***** 'DBEHM.MIG.COBOL(ACCLOAD)' - Mapped Application: CBSA
2026-02-02 10:16:16.064 ***** 'DBEHM.MIG.COBOL(ACCOFFL)' - Mapped Application: CBSA
2026-02-02 10:16:16.066 ***** 'DBEHM.MIG.COBOL(ACCTCTRL)' - Mapped Application: CBSA
2026-02-02 10:16:16.068 ***** 'DBEHM.MIG.COBOL(BANKDATA)' - Mapped Application: CBSA
2026-02-02 10:16:16.070 ***** 'DBEHM.MIG.COBOL(BNKMENU)' - Mapped Application: CBSA
2026-02-02 10:16:16.072 ***** 'DBEHM.MIG.COBOL(BNK1CAC)' - Mapped Application: CBSA
2026-02-02 10:16:16.074 ***** 'DBEHM.MIG.COBOL(BNK1CCA)' - Mapped Application: CBSA
2026-02-02 10:16:16.076 ***** 'DBEHM.MIG.COBOL(BNK1CCS)' - Mapped Application: CBSA
2026-02-02 10:16:16.078 ***** 'DBEHM.MIG.COBOL(BNK1CRA)' - Mapped Application: CBSA
2026-02-02 10:16:16.079 ***** 'DBEHM.MIG.COBOL(BNK1DAC)' - Mapped Application: CBSA
2026-02-02 10:16:16.081 ***** 'DBEHM.MIG.COBOL(BNK1DCS)' - Mapped Application: CBSA
2026-02-02 10:16:16.083 ***** 'DBEHM.MIG.COBOL(BNK1TFN)' - Mapped Application: CBSA
2026-02-02 10:16:16.085 ***** 'DBEHM.MIG.COBOL(BNK1UAC)' - Mapped Application: CBSA
2026-02-02 10:16:16.087 ***** 'DBEHM.MIG.COBOL(CONSENT)' - Mapped Application: CBSA
2026-02-02 10:16:16.089 ***** 'DBEHM.MIG.COBOL(CONSTTST)' - Mapped Application: CBSA
2026-02-02 10:16:16.091 ***** 'DBEHM.MIG.COBOL(CRDTAGY1)' - Mapped Application: CBSA
2026-02-02 10:16:16.093 ***** 'DBEHM.MIG.COBOL(CRDTAGY2)' - Mapped Application: CBSA
2026-02-02 10:16:16.095 ***** 'DBEHM.MIG.COBOL(CRDTAGY3)' - Mapped Application: CBSA
2026-02-02 10:16:16.097 ***** 'DBEHM.MIG.COBOL(CRDTAGY4)' - Mapped Application: CBSA
2026-02-02 10:16:16.100 ***** 'DBEHM.MIG.COBOL(CRDTAGY5)' - Mapped Application: CBSA
2026-02-02 10:16:16.102 ***** 'DBEHM.MIG.COBOL(CREACC)' - Mapped Application: CBSA
2026-02-02 10:16:16.104 ***** 'DBEHM.MIG.COBOL(CRECUST)' - Mapped Application: CBSA
2026-02-02 10:16:16.106 ***** 'DBEHM.MIG.COBOL(CUSTCTRL)' - Mapped Application: CBSA
2026-02-02 10:16:16.108 ***** 'DBEHM.MIG.COBOL(DBCRFUN)' - Mapped Application: CBSA
2026-02-02 10:16:16.111 ***** 'DBEHM.MIG.COBOL(DELACC)' - Mapped Application: CBSA
2026-02-02 10:16:16.113 ***** 'DBEHM.MIG.COBOL(DELCUS)' - Mapped Application: CBSA
2026-02-02 10:16:16.116 ***** 'DBEHM.MIG.COBOL(DPAYAPI)' - Mapped Application: CBSA
2026-02-02 10:16:16.118 ***** 'DBEHM.MIG.COBOL(DPAYTST)' - Mapped Application: CBSA
2026-02-02 10:16:16.121 ***** 'DBEHM.MIG.COBOL(EBUD0RUN)' - Mapped Application: RetirementCalculator
2026-02-02 10:16:16.124 ***** 'DBEHM.MIG.COBOL(EBUD01)' - Mapped Application: RetirementCalculator
2026-02-02 10:16:16.127 ***** 'DBEHM.MIG.COBOL(EBUD02)' - Mapped Application: RetirementCalculator
2026-02-02 10:16:16.130 ***** 'DBEHM.MIG.COBOL(EBUD03)' - Mapped Application: RetirementCalculator
2026-02-02 10:16:16.133 ***** 'DBEHM.MIG.COBOL(GETCOMPY)' - Mapped Application: CBSA
2026-02-02 10:16:16.135 ***** 'DBEHM.MIG.COBOL(GETSCODE)' - Mapped Application: CBSA
2026-02-02 10:16:16.138 ***** 'DBEHM.MIG.COBOL(INQACC)' - Mapped Application: CBSA
2026-02-02 10:16:16.141 ***** 'DBEHM.MIG.COBOL(INQACCCU)' - Mapped Application: CBSA
2026-02-02 10:16:16.143 ***** 'DBEHM.MIG.COBOL(INQCUST)' - Mapped Application: CBSA
2026-02-02 10:16:16.146 ***** 'DBEHM.MIG.COBOL(LGACDB01)' - Mapped Application: GenApp
2026-02-02 10:16:16.149 ***** 'DBEHM.MIG.COBOL(LGACDB02)' - Mapped Application: GenApp
2026-02-02 10:16:16.151 ***** 'DBEHM.MIG.COBOL(LGACUS01)' - Mapped Application: GenApp
2026-02-02 10:16:16.154 ***** 'DBEHM.MIG.COBOL(LGACVS01)' - Mapped Application: GenApp
2026-02-02 10:16:16.157 ***** 'DBEHM.MIG.COBOL(LGAPDB01)' - Mapped Application: GenApp
2026-02-02 10:16:16.160 ***** 'DBEHM.MIG.COBOL(LGAPOL01)' - Mapped Application: GenApp
2026-02-02 10:16:16.163 ***** 'DBEHM.MIG.COBOL(LGAPVS01)' - Mapped Application: GenApp
2026-02-02 10:16:16.165 ***** 'DBEHM.MIG.COBOL(LGASTAT1)' - Mapped Application: GenApp
2026-02-02 10:16:16.168 ***** 'DBEHM.MIG.COBOL(LGDPDB01)' - Mapped Application: GenApp
2026-02-02 10:16:16.171 ***** 'DBEHM.MIG.COBOL(LGDPOL01)' - Mapped Application: GenApp
2026-02-02 10:16:16.174 ***** 'DBEHM.MIG.COBOL(LGDPVS01)' - Mapped Application: GenApp
2026-02-02 10:16:16.177 ***** 'DBEHM.MIG.COBOL(LGICDB01)' - Mapped Application: GenApp
2026-02-02 10:16:16.179 ***** 'DBEHM.MIG.COBOL(LGICUS01)' - Mapped Application: GenApp
2026-02-02 10:16:16.182 ***** 'DBEHM.MIG.COBOL(LGICVS01)' - Mapped Application: GenApp
2026-02-02 10:16:16.185 ***** 'DBEHM.MIG.COBOL(LGIPDB01)' - Mapped Application: GenApp
2026-02-02 10:16:16.187 ***** 'DBEHM.MIG.COBOL(LGIPOL01)' - Mapped Application: GenApp
2026-02-02 10:16:16.190 ***** 'DBEHM.MIG.COBOL(LGIPVS01)' - Mapped Application: GenApp
2026-02-02 10:16:16.193 ***** 'DBEHM.MIG.COBOL(LGSETUP)' - Mapped Application: GenApp
2026-02-02 10:16:16.195 ***** 'DBEHM.MIG.COBOL(LGSTSQ)' - Mapped Application: GenApp
2026-02-02 10:16:16.198 ***** 'DBEHM.MIG.COBOL(LGTESTC1)' - Mapped Application: GenApp
2026-02-02 10:16:16.201 ***** 'DBEHM.MIG.COBOL(LGTESTP1)' - Mapped Application: GenApp
2026-02-02 10:16:16.203 ***** 'DBEHM.MIG.COBOL(LGTESTP2)' - Mapped Application: GenApp
2026-02-02 10:16:16.206 ***** 'DBEHM.MIG.COBOL(LGTESTP3)' - Mapped Application: GenApp
2026-02-02 10:16:16.209 ***** 'DBEHM.MIG.COBOL(LGTESTP4)' - Mapped Application: GenApp
2026-02-02 10:16:16.211 ***** 'DBEHM.MIG.COBOL(LGUCDB01)' - Mapped Application: GenApp
2026-02-02 10:16:16.214 ***** 'DBEHM.MIG.COBOL(LGUCUS01)' - Mapped Application: GenApp
2026-02-02 10:16:16.216 ***** 'DBEHM.MIG.COBOL(LGUCVS01)' - Mapped Application: GenApp
2026-02-02 10:16:16.219 ***** 'DBEHM.MIG.COBOL(LGUPDB01)' - Mapped Application: GenApp
2026-02-02 10:16:16.222 ***** 'DBEHM.MIG.COBOL(LGUPOL01)' - Mapped Application: GenApp
2026-02-02 10:16:16.224 ***** 'DBEHM.MIG.COBOL(LGUPVS01)' - Mapped Application: GenApp
2026-02-02 10:16:16.227 ***** 'DBEHM.MIG.COBOL(LGWEBST5)' - Mapped Application: GenApp
2026-02-02 10:16:16.230 ***** 'DBEHM.MIG.COBOL(OLDACDB1)' - Mapped Application: UNASSIGNED
2026-02-02 10:16:16.233 ***** 'DBEHM.MIG.COBOL(OLDACDB2)' - Mapped Application: UNASSIGNED
2026-02-02 10:16:16.236 ***** 'DBEHM.MIG.COBOL(PROLOAD)' - Mapped Application: CBSA
2026-02-02 10:16:16.238 ***** 'DBEHM.MIG.COBOL(PROOFFL)' - Mapped Application: CBSA
2026-02-02 10:16:16.241 ***** 'DBEHM.MIG.COBOL(UPDACC)' - Mapped Application: CBSA
2026-02-02 10:16:16.243 ***** 'DBEHM.MIG.COBOL(UPDCUST)' - Mapped Application: CBSA
2026-02-02 10:16:16.246 ***** 'DBEHM.MIG.COBOL(XFRFUN)' - Mapped Application: CBSA
2026-02-02 10:16:16.253 ** Generating Applications Configurations files.
2026-02-02 10:16:16.255 ** Generating Configuration files for Application: RetirementCalculator
2026-02-02 10:16:16.353 	Created DBB Migration Utility mapping file /u/mdalbin/Migration-Modeler-MDLB-work/work/migration-configuration/RetirementCalculator.mapping
2026-02-02 10:16:16.403 	Created/Updated Application Description file /u/mdalbin/Migration-Modeler-MDLB-work/repositories/RetirementCalculator/applicationDescriptor.yml
2026-02-02 10:16:16.502 	Estimated storage size of migrated members: 12,838 bytes
2026-02-02 10:16:16.503 ** Generating Configuration files for Application: UNASSIGNED
2026-02-02 10:16:16.516 	Created DBB Migration Utility mapping file /u/mdalbin/Migration-Modeler-MDLB-work/work/migration-configuration/UNASSIGNED.mapping
2026-02-02 10:16:16.528 	Created/Updated Application Description file /u/mdalbin/Migration-Modeler-MDLB-work/repositories/UNASSIGNED/applicationDescriptor.yml
2026-02-02 10:16:16.566 	Estimated storage size of migrated members: 36,244 bytes
2026-02-02 10:16:16.566 ** Generating Configuration files for Application: CBSA
2026-02-02 10:16:16.652 	Created DBB Migration Utility mapping file /u/mdalbin/Migration-Modeler-MDLB-work/work/migration-configuration/CBSA.mapping
2026-02-02 10:16:16.695 	Created/Updated Application Description file /u/mdalbin/Migration-Modeler-MDLB-work/repositories/CBSA/applicationDescriptor.yml
2026-02-02 10:16:17.098 	Estimated storage size of migrated members: 1,147,571 bytes
2026-02-02 10:16:17.099 ** Generating Configuration files for Application: GenApp
2026-02-02 10:16:17.136 	Created DBB Migration Utility mapping file /u/mdalbin/Migration-Modeler-MDLB-work/work/migration-configuration/GenApp.mapping
2026-02-02 10:16:17.157 	Created/Updated Application Description file /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/applicationDescriptor.yml
2026-02-02 10:16:17.394 	Estimated storage size of migrated members: 463,749 bytes
2026-02-02 10:16:17.396 ** Estimated storage size of all migrated members: 1,660,402 bytes
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
[INFO] /usr/lpp/dbb/v3r0/bin/groovyz /u/mdalbin/Migration-Modeler-MDLB/src/groovy/scanApplication.groovy 				--configFile /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-02-02.093836.config 				--application GenApp 				--logFile /u/mdalbin/Migration-Modeler-MDLB-work/logs/3-GenApp-scan.log
2026-02-02 10:16:37.551 ** Script configuration:
2026-02-02 10:16:37.552 	REPOSITORY_PATH_MAPPING_FILE -> /u/mdalbin/Migration-Modeler-MDLB-work/config/repositoryPathsMapping.yaml
2026-02-02 10:16:37.552 	SCAN_CONTROL_TRANSFERS -> true
2026-02-02 10:16:37.552 	application -> GenApp
2026-02-02 10:16:37.552 	configurationFilePath -> /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-02-02.093836.config
2026-02-02 10:16:37.552 	DBB_MODELER_APPLICATION_DIR -> /u/mdalbin/Migration-Modeler-MDLB-work/repositories
2026-02-02 10:16:37.553 	DBB_MODELER_FILE_METADATA_STORE_DIR -> /u/mdalbin/Migration-Modeler-MDLB-work/work/dbb-filemetadatastore
2026-02-02 10:16:37.553 	logFile -> /u/mdalbin/Migration-Modeler-MDLB-work/logs/3-GenApp-scan.log
2026-02-02 10:16:37.554 	DBB_MODELER_METADATASTORE_TYPE -> file
2026-02-02 10:16:37.554 	APPLICATION_DEFAULT_BRANCH -> main
2026-02-02 10:16:37.554 ** Reading the existing Application Descriptor file.
2026-02-02 10:16:37.570 ** Retrieving the list of files mapped to Source Groups.
2026-02-02 10:16:37.571 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/applicationDescriptor.yml'. Skipping.
2026-02-02 10:16:37.571 - Additional message - [INFO] No matching Repository Path was found for file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.gitattributes'. Skipping.
2026-02-02 10:16:37.581 ** Scanning the files.
2026-02-02 10:16:37.581 	Scanning file GenApp/GenApp/src/cobol/lgtestp2.cbl 
2026-02-02 10:16:37.590 	Scanning file GenApp/GenApp/src/cobol/lgicus01.cbl 
2026-02-02 10:16:37.598 	Scanning file GenApp/GenApp/src/cobol/lgucus01.cbl 
2026-02-02 10:16:37.605 	Scanning file GenApp/GenApp/src/cobol/lgucvs01.cbl 
2026-02-02 10:16:37.612 	Scanning file GenApp/GenApp/src/cobol/lgapdb01.cbl 
2026-02-02 10:16:37.625 	Scanning file GenApp/GenApp/src/cobol/lgdpdb01.cbl 
2026-02-02 10:16:37.633 	Scanning file GenApp/GenApp/src/cobol/lgicvs01.cbl 
2026-02-02 10:16:37.642 	Scanning file GenApp/GenApp/src/copy/lgpolicy.cpy 
2026-02-02 10:16:37.661 	Scanning file GenApp/GenApp/src/cobol/lgsetup.cbl 
2026-02-02 10:16:37.673 	Scanning file GenApp/GenApp/src/copy/lgcmarea.cpy 
2026-02-02 10:16:37.690 	Scanning file GenApp/GenApp/src/cobol/lgacdb01.cbl 
2026-02-02 10:16:37.699 	Scanning file GenApp/GenApp/src/cobol/lgipdb01.cbl 
2026-02-02 10:16:37.720 	Scanning file GenApp/GenApp/src/cobol/lgupvs01.cbl 
2026-02-02 10:16:37.727 	Scanning file GenApp/GenApp/src/cobol/lgtestp1.cbl 
2026-02-02 10:16:37.735 	Scanning file GenApp/GenApp/src/cobol/lgtestc1.cbl 
2026-02-02 10:16:37.744 	Scanning file GenApp/GenApp/src/cobol/lgdpol01.cbl 
2026-02-02 10:16:37.751 	Scanning file GenApp/GenApp/src/cobol/lgapol01.cbl 
2026-02-02 10:16:37.758 	Scanning file GenApp/GenApp/src/bms/ssmap.bms 
2026-02-02 10:16:37.880 	Scanning file GenApp/GenApp/src/copy/lgcmared.cpy 
2026-02-02 10:16:37.887 	Scanning file GenApp/GenApp/src/cobol/lgucdb01.cbl 
2026-02-02 10:16:37.893 	Scanning file GenApp/GenApp/src/cobol/lgacdb02.cbl 
2026-02-02 10:16:37.900 	Scanning file GenApp/GenApp/src/cobol/lgipol01.cbl 
2026-02-02 10:16:37.912 	Scanning file GenApp/GenApp/src/cobol/lgapvs01.cbl 
2026-02-02 10:16:37.918 	Scanning file GenApp/GenApp/src/cobol/lgicdb01.cbl 
2026-02-02 10:16:37.923 	Scanning file GenApp/GenApp/src/cobol/lgtestp4.cbl 
2026-02-02 10:16:37.930 	Scanning file GenApp/GenApp/src/cobol/lgdpvs01.cbl 
2026-02-02 10:16:37.934 	Scanning file GenApp/GenApp/src/cobol/lgupol01.cbl 
2026-02-02 10:16:37.940 	Scanning file GenApp/GenApp/src/cobol/lgacvs01.cbl 
2026-02-02 10:16:37.944 	Scanning file GenApp/GenApp/src/cobol/lgipvs01.cbl 
2026-02-02 10:16:37.949 	Scanning file GenApp/GenApp/src/cobol/lgastat1.cbl 
2026-02-02 10:16:37.954 	Scanning file GenApp/GenApp/src/cobol/lgacus01.cbl 
2026-02-02 10:16:37.959 	Scanning file GenApp/GenApp/src/cobol/lgupdb01.cbl 
2026-02-02 10:16:37.968 	Scanning file GenApp/GenApp/src/cobol/lgtestp3.cbl 
2026-02-02 10:16:37.974 	Scanning file GenApp/GenApp/src/cobol/lgstsq.cbl 
2026-02-02 10:16:37.978 	Scanning file GenApp/GenApp/src/cobol/lgwebst5.cbl 
2026-02-02 10:16:37.991 ** Storing results in the 'GenApp-main' DBB Collection.
[INFO] /usr/lpp/dbb/v3r0/bin/groovyz /u/mdalbin/Migration-Modeler-MDLB/src/groovy/assessUsage.groovy 				--configFile /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-02-02.093836.config 				--application GenApp 				--logFile /u/mdalbin/Migration-Modeler-MDLB-work/logs/3-GenApp-assessUsage.log
2026-02-02 10:16:42.772 ** Script configuration:
2026-02-02 10:16:42.772 	DBB_MODELER_APPCONFIG_DIR -> /u/mdalbin/Migration-Modeler-MDLB-work/work/migration-configuration
2026-02-02 10:16:42.772 	MOVE_FILES_FLAG -> true
2026-02-02 10:16:42.772 	REPOSITORY_PATH_MAPPING_FILE -> /u/mdalbin/Migration-Modeler-MDLB-work/config/repositoryPathsMapping.yaml
2026-02-02 10:16:42.773 	SCAN_CONTROL_TRANSFERS -> true
2026-02-02 10:16:42.773 	application -> GenApp
2026-02-02 10:16:42.773 	configurationFilePath -> /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-02-02.093836.config
2026-02-02 10:16:42.773 	DBB_MODELER_APPLICATION_DIR -> /u/mdalbin/Migration-Modeler-MDLB-work/repositories
2026-02-02 10:16:42.774 	DBB_MODELER_FILE_METADATA_STORE_DIR -> /u/mdalbin/Migration-Modeler-MDLB-work/work/dbb-filemetadatastore
2026-02-02 10:16:42.774 	logFile -> /u/mdalbin/Migration-Modeler-MDLB-work/logs/3-GenApp-assessUsage.log
2026-02-02 10:16:42.774 	DBB_MODELER_METADATASTORE_TYPE -> file
2026-02-02 10:16:42.774 	APPLICATION_DEFAULT_BRANCH -> main
2026-02-02 10:16:42.785 ** Reading the Repository Layout Mapping definition.
2026-02-02 10:16:42.792 ** Getting the list of files of 'Include File' type.
2026-02-02 10:16:42.797 ** Analyzing impacted applications for file 'GenApp/GenApp/src/copy/lgcmarea.cpy'.
2026-02-02 10:16:42.845 	Files depending on 'GenApp/src/copy/lgcmarea.cpy' :
2026-02-02 10:16:42.845 	'GenApp/GenApp/src/cobol/lgdpol01.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.845 	'GenApp/GenApp/src/cobol/lgtestp3.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.845 	'GenApp/GenApp/src/cobol/lgipol01.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.845 	'GenApp/GenApp/src/cobol/lgupol01.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.846 	'GenApp/GenApp/src/cobol/lgastat1.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.846 	'GenApp/GenApp/src/cobol/lgacvs01.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.846 	'GenApp/GenApp/src/cobol/lgucus01.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.846 	'GenApp/GenApp/src/cobol/lgapdb01.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.846 	'UNASSIGNED/UNASSIGNED/src/cobol/oldacdb1.cbl' in  Application  'UNASSIGNED'
2026-02-02 10:16:42.846 	'GenApp/GenApp/src/cobol/lgdpdb01.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.846 	'GenApp/GenApp/src/cobol/lgacdb01.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.847 	'GenApp/GenApp/src/cobol/lgtestp2.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.847 	'GenApp/GenApp/src/cobol/lgtestc1.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.847 	'GenApp/GenApp/src/cobol/lgicdb01.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.847 	'GenApp/GenApp/src/cobol/lgapol01.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.847 	'GenApp/GenApp/src/cobol/lgicus01.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.847 	'GenApp/GenApp/src/cobol/lgupdb01.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.848 	'UNASSIGNED/UNASSIGNED/src/cobol/oldacdb2.cbl' in  Application  'UNASSIGNED'
2026-02-02 10:16:42.848 	'GenApp/GenApp/src/cobol/lgucvs01.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.848 	'GenApp/GenApp/src/cobol/lgtestp1.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.848 	'GenApp/GenApp/src/cobol/lgapvs01.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.848 	'GenApp/GenApp/src/cobol/lgupvs01.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.848 	'GenApp/GenApp/src/cobol/lgucdb01.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.848 	'GenApp/GenApp/src/cobol/lgtestp4.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.849 	'GenApp/GenApp/src/cobol/lgdpvs01.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.849 	'GenApp/GenApp/src/cobol/lgacus01.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.849 	'GenApp/GenApp/src/cobol/lgipdb01.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.850 	==> 'lgcmarea' referenced by multiple applications - [UNASSIGNED, GenApp]
2026-02-02 10:16:42.850 	==> Updating usage of Include File 'lgcmarea' to 'public' in '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/applicationDescriptor.yml'.
2026-02-02 10:16:42.875 ** Analyzing impacted applications for file 'GenApp/GenApp/src/copy/lgcmared.cpy'.
2026-02-02 10:16:42.880 	The Include File 'lgcmared' is not referenced at all.
2026-02-02 10:16:42.887 ** Analyzing impacted applications for file 'GenApp/GenApp/src/copy/lgpolicy.cpy'.
2026-02-02 10:16:42.907 	Files depending on 'GenApp/src/copy/lgpolicy.cpy' :
2026-02-02 10:16:42.907 	'UNASSIGNED/UNASSIGNED/src/cobol/oldacdb1.cbl' in  Application  'UNASSIGNED'
2026-02-02 10:16:42.907 	'GenApp/GenApp/src/cobol/lgipol01.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.907 	'GenApp/GenApp/src/cobol/lgicus01.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.907 	'GenApp/GenApp/src/cobol/lgacdb01.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.908 	'GenApp/GenApp/src/cobol/lgucdb01.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.908 	'GenApp/GenApp/src/cobol/lgupdb01.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.908 	'UNASSIGNED/UNASSIGNED/src/cobol/oldacdb2.cbl' in  Application  'UNASSIGNED'
2026-02-02 10:16:42.908 	'GenApp/GenApp/src/cobol/lgacus01.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.908 	'GenApp/GenApp/src/cobol/lgicdb01.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.908 	'GenApp/GenApp/src/cobol/lgipdb01.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.909 	'GenApp/GenApp/src/cobol/lgapdb01.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.909 	'GenApp/GenApp/src/cobol/lgacdb02.cbl' in  Application  'GenApp'
2026-02-02 10:16:42.909 	==> 'lgpolicy' referenced by multiple applications - [UNASSIGNED, GenApp]
2026-02-02 10:16:42.909 	==> Updating usage of Include File 'lgpolicy' to 'public' in '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/applicationDescriptor.yml'.
2026-02-02 10:16:42.926 ** Getting the list of files of 'Program' type.
2026-02-02 10:16:42.927 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgicus01.cbl'.
2026-02-02 10:16:42.935 	The Program 'lgicus01' is not statically called by any other program.
2026-02-02 10:16:42.941 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgdpol01.cbl'.
2026-02-02 10:16:42.948 	The Program 'lgdpol01' is not statically called by any other program.
2026-02-02 10:16:42.955 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgipdb01.cbl'.
2026-02-02 10:16:42.964 	The Program 'lgipdb01' is not statically called by any other program.
2026-02-02 10:16:42.971 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgtestp3.cbl'.
2026-02-02 10:16:42.976 	The Program 'lgtestp3' is not statically called by any other program.
2026-02-02 10:16:42.986 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgtestp4.cbl'.
2026-02-02 10:16:42.992 	The Program 'lgtestp4' is not statically called by any other program.
2026-02-02 10:16:42.998 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgacvs01.cbl'.
2026-02-02 10:16:43.003 	The Program 'lgacvs01' is not statically called by any other program.
2026-02-02 10:16:43.010 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgsetup.cbl'.
2026-02-02 10:16:43.017 	The Program 'lgsetup' is not statically called by any other program.
2026-02-02 10:16:43.025 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgapol01.cbl'.
2026-02-02 10:16:43.032 	The Program 'lgapol01' is not statically called by any other program.
2026-02-02 10:16:43.039 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgipvs01.cbl'.
2026-02-02 10:16:43.044 	The Program 'lgipvs01' is not statically called by any other program.
2026-02-02 10:16:43.050 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgupol01.cbl'.
2026-02-02 10:16:43.058 	The Program 'lgupol01' is not statically called by any other program.
2026-02-02 10:16:43.064 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgacdb01.cbl'.
2026-02-02 10:16:43.070 	The Program 'lgacdb01' is not statically called by any other program.
2026-02-02 10:16:43.076 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgacdb02.cbl'.
2026-02-02 10:16:43.082 	The Program 'lgacdb02' is not statically called by any other program.
2026-02-02 10:16:43.088 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgstsq.cbl'.
2026-02-02 10:16:43.100 	The Program 'lgstsq' is not statically called by any other program.
2026-02-02 10:16:43.107 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgtestp1.cbl'.
2026-02-02 10:16:43.113 	The Program 'lgtestp1' is not statically called by any other program.
2026-02-02 10:16:43.120 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgtestp2.cbl'.
2026-02-02 10:16:43.126 	The Program 'lgtestp2' is not statically called by any other program.
2026-02-02 10:16:43.140 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgdpdb01.cbl'.
2026-02-02 10:16:43.145 	The Program 'lgdpdb01' is not statically called by any other program.
2026-02-02 10:16:43.152 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgucus01.cbl'.
2026-02-02 10:16:43.157 	The Program 'lgucus01' is not statically called by any other program.
2026-02-02 10:16:43.164 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgapvs01.cbl'.
2026-02-02 10:16:43.169 	The Program 'lgapvs01' is not statically called by any other program.
2026-02-02 10:16:43.175 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgucdb01.cbl'.
2026-02-02 10:16:43.181 	The Program 'lgucdb01' is not statically called by any other program.
2026-02-02 10:16:43.187 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgdpvs01.cbl'.
2026-02-02 10:16:43.193 	The Program 'lgdpvs01' is not statically called by any other program.
2026-02-02 10:16:43.199 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgtestc1.cbl'.
2026-02-02 10:16:43.205 	The Program 'lgtestc1' is not statically called by any other program.
2026-02-02 10:16:43.212 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgastat1.cbl'.
2026-02-02 10:16:43.217 	The Program 'lgastat1' is not statically called by any other program.
2026-02-02 10:16:43.223 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgapdb01.cbl'.
2026-02-02 10:16:43.230 	The Program 'lgapdb01' is not statically called by any other program.
2026-02-02 10:16:43.236 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgicvs01.cbl'.
2026-02-02 10:16:43.242 	The Program 'lgicvs01' is not statically called by any other program.
2026-02-02 10:16:43.248 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgipol01.cbl'.
2026-02-02 10:16:43.255 	The Program 'lgipol01' is not statically called by any other program.
2026-02-02 10:16:43.261 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgacus01.cbl'.
2026-02-02 10:16:43.266 	The Program 'lgacus01' is not statically called by any other program.
2026-02-02 10:16:43.272 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgwebst5.cbl'.
2026-02-02 10:16:43.280 	The Program 'lgwebst5' is not statically called by any other program.
2026-02-02 10:16:43.286 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgucvs01.cbl'.
2026-02-02 10:16:43.292 	The Program 'lgucvs01' is not statically called by any other program.
2026-02-02 10:16:43.297 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgupdb01.cbl'.
2026-02-02 10:16:43.304 	The Program 'lgupdb01' is not statically called by any other program.
2026-02-02 10:16:43.310 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgicdb01.cbl'.
2026-02-02 10:16:43.315 	The Program 'lgicdb01' is not statically called by any other program.
2026-02-02 10:16:43.321 ** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgupvs01.cbl'.
2026-02-02 10:16:43.326 	The Program 'lgupvs01' is not statically called by any other program.
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
[INFO] /usr/lpp/dbb/v3r0/bin/groovyz /u/mdalbin/Migration-Modeler-MDLB/src/groovy/generateZBuilderProperties.groovy 				--configFile /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-02-02.093836.config 				--application GenApp 				--logFile /u/mdalbin/Migration-Modeler-MDLB-work/logs/4-GenApp-generateProperties.log
2026-02-02 10:16:46.488 ** Script configuration:
2026-02-02 10:16:46.488 	application -> GenApp
2026-02-02 10:16:46.488 	configurationFilePath -> /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-02-02.093836.config
2026-02-02 10:16:46.488 	DBB_MODELER_APPLICATION_DIR -> /u/mdalbin/Migration-Modeler-MDLB-work/repositories
2026-02-02 10:16:46.488 	logFile -> /u/mdalbin/Migration-Modeler-MDLB-work/logs/4-GenApp-generateProperties.log
2026-02-02 10:16:46.488 	TYPE_CONFIGURATIONS_FILE -> /u/mdalbin/Migration-Modeler-MDLB-work/config/types/typesConfigurations.yaml
2026-02-02 10:16:46.488 	DBB_MODELER_WORK -> /u/mdalbin/Migration-Modeler-MDLB-work
2026-02-02 10:16:46.488 	DBB_ZBUILDER -> /u/mdalbin/zBuilder
2026-02-02 10:16:46.489 ** Reading the Types Configurations definitions from '/u/mdalbin/Migration-Modeler-MDLB-work/config/types/typesConfigurations.yaml'.
2026-02-02 10:16:46.498 ** Gathering the defined types for files.
2026-02-02 10:16:46.501 ** Generating zBuilder language configuration files.
2026-02-02 10:16:46.502 	Type Configuration for type 'CBLCICSDB2' found in '/u/mdalbin/Migration-Modeler-MDLB-work/config/types/typesConfigurations.yaml'.
2026-02-02 10:16:46.504 	[WARNING] No Type Configuration for type 'CBLDB2' found in '/u/mdalbin/Migration-Modeler-MDLB-work/config/types/typesConfigurations.yaml'.
2026-02-02 10:16:46.504 	[WARNING] No Type Configuration for type 'CBLCICS' found in '/u/mdalbin/Migration-Modeler-MDLB-work/config/types/typesConfigurations.yaml'.
2026-02-02 10:16:46.505 ** Generating zBuilder Application configuration file.
2026-02-02 10:16:46.507 ** [INFO] 1 Language Configuration file created in '/u/mdalbin/Migration-Modeler-MDLB-work/build-configuration'.
2026-02-02 10:16:46.507 ** [INFO] Before running builds with zBuilder, please copy the content of the '/u/mdalbin/Migration-Modeler-MDLB-work/build-configuration' folder to your zBuilder instance located at '/u/mdalbin/zBuilder'.
2026-02-02 10:16:46.507 ** Generating Dependencies Search Paths and Impact Analysis Query Patterns.
2026-02-02 10:16:46.509 ** Application Configuration file '/u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/dbb-app.yaml' successfully created.
2026-02-02 10:16:46.509 ** [INFO] Make sure the zBuilder Configuration files (Language Task definitions) are accurate before running a build with zBuilder.
2026-02-02 10:16:46.509 ** [INFO] For each Language Task definition, the Dependency Search Path variable potentially needs to be updated to match the layout of the Git repositories.
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
[CMD] /usr/lpp/dbb/v3r0/bin/groovyz /u/mdalbin/Migration-Modeler-MDLB/src/groovy/utils/metadataStoreUtility.groovy -c /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-02-02.093836.config --deleteBuildGroup --buildGroup GenApp-main -l /u/mdalbin/Migration-Modeler-MDLB-work/logs/5-GenApp-initApplicationRepository.log
2026-02-02 10:17:09.501 ** Script configuration:
2026-02-02 10:17:09.501    deleteBuildGroup -> true
2026-02-02 10:17:09.501    buildGroup -> GenApp-main
2026-02-02 10:17:09.501    configurationFilePath -> /u/mdalbin/Migration-Modeler-MDLB/config/DBB_GIT_MIGRATION_MODELER-2026-02-02.093836.config
2026-02-02 10:17:09.502    logFile -> /u/mdalbin/Migration-Modeler-MDLB-work/logs/5-GenApp-initApplicationRepository.log
2026-02-02 10:17:09.502 ** Deleting DBB BuildGroup GenApp-main
2026-02-02 10:17:09.509 ** Deleting legacy collections in DBB BuildGroup dbb_default
[CMD] git init --initial-branch=main
Initialized empty Git repository in /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/.git/
[CMD] rm .gitattributes
[CMD] cp /u/mdalbin/Migration-Modeler-MDLB-work/config/default-app-repo-config-files/.gitattributes .gitattributes
[CMD] cp /u/mdalbin/Migration-Modeler-MDLB-work/config/default-app-repo-config-files/zapp_template.yaml zapp.yaml
[CMD] /usr/lpp/dbb/v3r0/bin/groovyz /u/mdalbin/Migration-Modeler-MDLB/src/groovy/utils/zappUtils.groovy 					-z /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/zapp.yaml 					-a /u/mdalbin/Migration-Modeler-MDLB-work/repositories/GenApp/applicationDescriptor.yml 					-b /var/dbb/dbb-zappbuild -l /u/mdalbin/Migration-Modeler-MDLB-work/logs/5-GenApp-initApplicationRepository.log
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
	dbb-app.yaml
	deployment/
	tagging/
	zapp.yaml

nothing added to commit but untracked files present (use "git add" to track)
[CMD] git add --all
[CMD] git commit -m 'Initial Commit'
[main (root-commit) 57a8f4a] Initial Commit
 44 files changed, 11755 insertions(+)
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
 create mode 100644 dbb-app.yaml
 create mode 100644 deployment/deployPackage.yml
 create mode 100644 tagging/createReleaseCandidate.yml
 create mode 100644 zapp.yaml
[CMD] git tag rel-1.0.0
[CMD] git branch release/rel-1.0.0 refs/tags/rel-1.0.0
** /usr/lpp/dbb/v3r0/bin/dbb build full     						--hlq DBEHM.MIG     						--preview     						
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
[INFO] /usr/lpp/dbb/v3r0/bin/groovyz /u/ibmuser/dbb-git-migration-modeler-work/src/groovy/scanApplication.groovy 				--configFile /u/ibmuser/dbb-git-migration-modeler-work/DBB_GIT_MIGRATION_MODELER.config 				--application CBSA 				--logFile /u/ibmuser/dbb-git-migration-modeler-work/logs/3-CBSA-scan.log
2025-04-09 13:52:36.462 ** Script configuration:
2025-04-09 13:52:36.505 	PIPELINE_USER -> ADO
2025-04-09 13:52:36.508 	application -> CBSA
2025-04-09 13:52:36.511 	configurationFilePath -> /u/ibmuser/dbb-git-migration-modeler-work/DBB_GIT_MIGRATION_MODELER.config
2025-04-09 13:52:36.514 	DBB_MODELER_APPLICATION_DIR -> /u/ibmuser/dbb-git-migration-modeler-work/repositories
2025-04-09 13:52:36.517 	logFile -> /u/ibmuser/dbb-git-migration-modeler-work/logs/3-CBSA-scan.log
2025-04-09 13:52:36.520 	DBB_MODELER_METADATASTORE_TYPE -> db2
2025-04-09 13:52:36.522 	DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE -> /u/ibmuser/dbb-git-migration-modeler-work/db2Connection.conf
2025-04-09 13:52:36.525 	DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORD -> 
2025-04-09 13:52:36.528 	DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE -> /u/ibmuser/dbb-git-migration-modeler-work/MDALBIN-password.txt
2025-04-09 13:52:36.533 	DBB_MODELER_DB2_METADATASTORE_JDBC_ID -> MDALBIN
2025-04-09 13:52:36.536 	APPLICATION_DEFAULT_BRANCH -> main
2025-04-09 13:52:36.971 ** Scanning the files.
2025-04-09 13:52:37.144 	Scanning file CBSA/CBSA/application-conf/DBDgen.properties 
2025-04-09 13:52:37.282 	Scanning file CBSA/CBSA/src/cobol/getscode.cbl 
2025-04-09 13:52:37.366 	Scanning file CBSA/CBSA/src/cobol/bnk1cca.cbl 
2025-04-09 13:52:37.436 	Scanning file CBSA/CBSA/src/copy/contdb2.cpy 
2025-04-09 13:52:37.444 	Scanning file CBSA/CBSA/src/cobol/updcust.cbl 
2025-04-09 13:52:37.485 	Scanning file CBSA/CBSA/src/cobol/bnk1cac.cbl 
2025-04-09 13:52:37.558 	Scanning file CBSA/CBSA/src/copy/bnk1dcm.cpy 
2025-04-09 13:52:37.656 	Scanning file CBSA/CBSA/src/cobol/xfrfun.cbl 
2025-04-09 13:52:37.742 	Scanning file CBSA/CBSA/src/copy/consent.cpy 
2025-04-09 13:52:37.758 	Scanning file CBSA/CBSA/src/cobol/bnk1ccs.cbl 
2025-04-09 13:52:37.827 	Scanning file CBSA/CBSA/src/copy/sortcode.cpy 
2025-04-09 13:52:37.831 	Scanning file CBSA/CBSA/application-conf/file.properties 
2025-04-09 13:52:37.871 	Scanning file CBSA/CBSA/src/copy/custctrl.cpy 
2025-04-09 13:52:37.880 	Scanning file CBSA/CBSA/application-conf/PLI.properties 
2025-04-09 13:52:37.895 	Scanning file CBSA/CBSA/src/cobol/crdtagy1.cbl 
2025-04-09 13:52:37.917 	Scanning file CBSA/CBSA/src/cobol/bankdata.cbl 
2025-04-09 13:52:37.996 	Scanning file CBSA/CBSA/src/cobol/crecust.cbl 
2025-04-09 13:52:38.071 	Scanning file CBSA/CBSA/application-conf/TazUnitTest.properties 
2025-04-09 13:52:38.083 	Scanning file CBSA/CBSA.yaml 
2025-04-09 13:52:38.118 	Scanning file CBSA/CBSA/src/copy/delacc.cpy 
2025-04-09 13:52:38.131 	Scanning file CBSA/CBSA/src/cobol/dpayapi.cbl 
2025-04-09 13:52:38.155 	Scanning file CBSA/CBSA/src/copy/constapi.cpy 
2025-04-09 13:52:38.166 	Scanning file CBSA/applicationDescriptor.yml 
2025-04-09 13:52:38.200 	Scanning file CBSA/CBSA/src/copy/bnk1cam.cpy 
2025-04-09 13:52:38.315 	Scanning file CBSA/CBSA/src/cobol/consttst.cbl 
2025-04-09 13:52:38.327 	Scanning file CBSA/CBSA/src/cobol/crdtagy3.cbl 
2025-04-09 13:52:38.346 	Scanning file CBSA/CBSA/src/cobol/delcus.cbl 
2025-04-09 13:52:38.385 	Scanning file CBSA/CBSA/application-conf/Assembler.properties 
2025-04-09 13:52:38.405 	Scanning file CBSA/CBSA/src/cobol/accoffl.cbl 
2025-04-09 13:52:38.425 	Scanning file CBSA/CBSA/src/copy/updacc.cpy 
2025-04-09 13:52:38.435 	Scanning file CBSA/.gitattributes 
2025-04-09 13:52:38.448 	Scanning file CBSA/CBSA/src/copy/datastr.cpy 
2025-04-09 13:52:38.453 	Scanning file CBSA/CBSA/application-conf/application.properties 
2025-04-09 13:52:38.485 	Scanning file CBSA/CBSA/src/cobol/crdtagy4.cbl 
2025-04-09 13:52:38.498 	Scanning file CBSA/CBSA/src/cobol/accload.cbl 
2025-04-09 13:52:38.513 	Scanning file CBSA/CBSA/application-conf/Transfer.properties 
2025-04-09 13:52:38.516 	Scanning file CBSA/tagging/createReleaseCandidate.yml 
2025-04-09 13:52:38.548 	Scanning file CBSA/CBSA/src/copy/bnk1ccm.cpy 
2025-04-09 13:52:38.594 	Scanning file CBSA/CBSA/application-conf/Cobol.properties 
2025-04-09 13:52:38.607 	Scanning file CBSA/deployment/deployReleasePackage.yml 
2025-04-09 13:52:38.623 	Scanning file CBSA/CBSA/application-conf/CRB.properties 
2025-04-09 13:52:38.626 	Scanning file CBSA/CBSA/application-conf/bind.properties 
2025-04-09 13:52:38.632 	Scanning file CBSA/CBSA/src/cobol/inqacc.cbl 
2025-04-09 13:52:38.652 	Scanning file CBSA/CBSA/src/cobol/bnk1dac.cbl 
2025-04-09 13:52:38.670 	Scanning file CBSA/CBSA/src/copy/customer.cpy 
2025-04-09 13:52:38.677 	Scanning file CBSA/CBSA/src/copy/crecust.cpy 
2025-04-09 13:52:38.683 	Scanning file CBSA/CBSA/src/copy/creacc.cpy 
2025-04-09 13:52:38.692 	Scanning file CBSA/CBSA/application-conf/languageConfigurationMapping.properties 
2025-04-09 13:52:38.696 	Scanning file CBSA/CBSA/application-conf/LinkEdit.properties 
2025-04-09 13:52:38.706 	Scanning file CBSA/CBSA/src/cobol/dbcrfun.cbl 
2025-04-09 13:52:38.724 	Scanning file CBSA/CBSA/src/copy/bnk1acc.cpy 
2025-04-09 13:52:38.735 	Scanning file CBSA/CBSA/src/copy/bnk1uam.cpy 
2025-04-09 13:52:38.776 	Scanning file CBSA/CBSA/src/cobol/abndproc.cbl 
2025-04-09 13:52:38.782 	Scanning file CBSA/CBSA/src/cobol/acctctrl.cbl 
2025-04-09 13:52:38.787 	Scanning file CBSA/CBSA/src/copy/procdb2.cpy 
2025-04-09 13:52:38.792 	Scanning file CBSA/CBSA/application-conf/ACBgen.properties 
2025-04-09 13:52:38.794 	Scanning file CBSA/tagging/createProductionReleaseTag.yml 
2025-04-09 13:52:38.801 	Scanning file CBSA/CBSA/application-conf/MFS.properties 
2025-04-09 13:52:38.806 	Scanning file CBSA/CBSA/application-conf/reports.properties 
2025-04-09 13:52:38.816 	Scanning file CBSA/CBSA/src/copy/abndinfo.cpy 
2025-04-09 13:52:38.821 	Scanning file CBSA/CBSA/src/copy/xfrfun.cpy 
2025-04-09 13:52:38.824 	Scanning file CBSA/CBSA/application-conf/PSBgen.properties 
2025-04-09 13:52:38.829 	Scanning file CBSA/CBSA/src/cobol/inqcust.cbl 
2025-04-09 13:52:38.841 	Scanning file CBSA/CBSA/application-conf/Easytrieve.properties 
2025-04-09 13:52:38.850 	Scanning file CBSA/CBSA/src/copy/constdb2.cpy 
2025-04-09 13:52:38.855 	Scanning file CBSA/CBSA/src/copy/getcompy.cpy 
2025-04-09 13:52:38.857 	Scanning file CBSA/CBSA/src/cobol/consent.cbl 
2025-04-09 13:52:38.865 	Scanning file CBSA/CBSA/src/cobol/crdtagy2.cbl 
2025-04-09 13:52:38.873 	Scanning file CBSA/CBSA/src/cobol/delacc.cbl 
2025-04-09 13:52:38.884 	Scanning file CBSA/CBSA/application-conf/REXX.properties 
2025-04-09 13:52:38.892 	Scanning file CBSA/zapp.yaml 
2025-04-09 13:52:38.895 	Scanning file CBSA/CBSA/src/copy/inqacccu.cpy 
2025-04-09 13:52:38.901 	Scanning file CBSA/CBSA/src/cobol/bnk1tfn.cbl 
2025-04-09 13:52:38.914 	Scanning file CBSA/CBSA/src/cobol/proload.cbl 
2025-04-09 13:52:38.921 	Scanning file CBSA/CBSA/src/cobol/inqacccu.cbl 
2025-04-09 13:52:38.930 	Scanning file CBSA/CBSA/src/copy/bnk1cdm.cpy 
2025-04-09 13:52:38.941 	Scanning file CBSA/CBSA/src/cobol/dpaytst.cbl 
2025-04-09 13:52:38.945 	Scanning file CBSA/CBSA/src/cobol/bnk1cra.cbl 
2025-04-09 13:52:38.956 	Scanning file CBSA/CBSA/src/cobol/prooffl.cbl 
2025-04-09 13:52:38.962 	Scanning file CBSA/CBSA/src/cobol/updacc.cbl 
2025-04-09 13:52:38.972 	Scanning file CBSA/CBSA/src/copy/acctctrl.cpy 
2025-04-09 13:52:38.975 	Scanning file CBSA/CBSA/src/copy/delcus.cpy 
2025-04-09 13:52:38.979 	Scanning file CBSA/CBSA/src/copy/proctran.cpy 
2025-04-09 13:52:38.992 	Scanning file CBSA/CBSA/src/copy/updcust.cpy 
2025-04-09 13:52:38.996 	Scanning file CBSA/CBSA/src/copy/getscode.cpy 
2025-04-09 13:52:38.997 	Scanning file CBSA/CBSA/src/cobol/creacc.cbl 
2025-04-09 13:52:39.011 	Scanning file CBSA/CBSA/src/cobol/crdtagy5.cbl 
2025-04-09 13:52:39.018 	Scanning file CBSA/CBSA/src/copy/account.cpy 
2025-04-09 13:52:39.024 	Scanning file CBSA/CBSA/src/copy/bnk1dam.cpy 
2025-04-09 13:52:39.050 	Scanning file CBSA/CBSA/src/copy/paydbcr.cpy 
2025-04-09 13:52:39.053 	Scanning file CBSA/CBSA/src/cobol/getcompy.cbl 
2025-04-09 13:52:39.056 	Scanning file CBSA/CBSA/src/cobol/custctrl.cbl 
2025-04-09 13:52:39.060 	Scanning file CBSA/CBSA/src/copy/accdb2.cpy 
2025-04-09 13:52:39.063 	Scanning file CBSA/CBSA/application-conf/BMS.properties 
2025-04-09 13:52:39.068 	Scanning file CBSA/CBSA/src/copy/inqacc.cpy 
2025-04-09 13:52:39.072 	Scanning file CBSA/CBSA/src/copy/bnk1mai.cpy 
2025-04-09 13:52:39.079 	Scanning file CBSA/CBSA/src/cobol/bnk1dcs.cbl 
2025-04-09 13:52:39.096 	Scanning file CBSA/azure-pipelines.yml 
2025-04-09 13:52:39.111 	Scanning file CBSA/CBSA/src/cobol/bnk1uac.cbl 
2025-04-09 13:52:39.124 	Scanning file CBSA/CBSA/src/cobol/bnkmenu.cbl 
2025-04-09 13:52:39.136 	Scanning file CBSA/CBSA/application-conf/README.md 
2025-04-09 13:52:39.198 	Scanning file CBSA/CBSA/src/copy/inqcust.cpy 
2025-04-09 13:52:39.201 	Scanning file CBSA/CBSA/src/copy/bnk1tfm.cpy 
2025-04-09 13:52:39.217 ** Storing results in the 'CBSA-main' DBB Collection.
2025-04-09 13:52:41.165 ** Setting collection owner to ADO
[CMD] /usr/lpp/dbb/v3r0/bin/groovyz /u/ibmuser/dbb-git-migration-modeler-work/src/groovy/recreateApplicationDescriptor.groovy 				--configFile /u/ibmuser/dbb-git-migration-modeler-work/DBB_GIT_MIGRATION_MODELER.config 				--application CBSA 				--logFile /u/ibmuser/dbb-git-migration-modeler-work/logs/3-CBSA-createApplicationDescriptor.log
2025-04-09 14:03:27.588 ** Script configuration:
2025-04-09 14:03:27.617 	REPOSITORY_PATH_MAPPING_FILE -> /u/ibmuser/dbb-git-migration-modeler-work/repositoryPathsMapping.yaml
2025-04-09 14:03:27.620 	application -> CBSA
2025-04-09 14:03:27.624 	configurationFilePath -> /u/ibmuser/dbb-git-migration-modeler-work/DBB_GIT_MIGRATION_MODELER.config
2025-04-09 14:03:27.628 	DBB_MODELER_APPLICATION_DIR -> /u/ibmuser/dbb-git-migration-modeler-work/repositories
2025-04-09 14:03:27.631 	logFile -> /u/ibmuser/dbb-git-migration-modeler-work/logs/3-CBSA-createApplicationDescriptor.log
2025-04-09 14:03:27.634 ** Reading the Repository Layout Mapping definition.
2025-04-09 14:03:27.864 ** Importing existing Application Descriptor and reset source groups, dependencies and consumers.
2025-04-09 14:03:27.906 ** Getting List of files /u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA
2025-04-09 14:03:28.112 *! [WARNING] '.git/objects/24/79cd7afe658ecc8801d10f9f8cb42382d53d16' is a hidden file. Skipped.
2025-04-09 14:03:28.113 *! [WARNING] '.git/objects/46/3a5519cbcb1b8db463d628173cafc3751fb323' is a hidden file. Skipped.
2025-04-09 14:03:28.115 *! [WARNING] '.git/objects/31/2d56358b0f4597312ad7d68b78ebd080fc11f5' is a hidden file. Skipped.
2025-04-09 14:03:28.116 *! [WARNING] '.git/objects/c2/1945e6c7fac12410c0d444fc1956f6b9ef090a' is a hidden file. Skipped.
2025-04-09 14:03:28.164 *! [WARNING] 'CBSA/application-conf/BMS.properties' did not match any rule defined in the repository path mapping configuration. Skipped.
2025-04-09 14:03:28.164 *! [WARNING] '.git/objects/12/c04ff4762844463e6e8d5b3a92c150fbb3c6ce' is a hidden file. Skipped.
2025-04-09 14:03:28.168 ** Adding 'CBSA/src/cobol/delacc.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.183 *! [WARNING] '.git/objects/2b/b5e69e60b48517664e8bc178ce5047d2dc6239' is a hidden file. Skipped.
2025-04-09 14:03:28.183 *! [WARNING] '.git/objects/29/ef69588ebc7fb77045dc42407df52eb89b771b' is a hidden file. Skipped.
2025-04-09 14:03:28.184 *! [WARNING] '.git/objects/e0/05e38176803fd06ebf016edf56c6347f7ebcca' is a hidden file. Skipped.
2025-04-09 14:03:28.185 ** Adding 'CBSA/src/cobol/bnk1cac.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.189 *! [WARNING] '.git/objects/71/95a42c31f86e0f70315660d9da6d62f9769d1e' is a hidden file. Skipped.
2025-04-09 14:03:28.189 *! [WARNING] '.git/objects/71/aba7981c900888d8f74ef1f3aa3e1efe91d405' is a hidden file. Skipped.
2025-04-09 14:03:28.190 *! [WARNING] 'CBSA/application-conf/TazUnitTest.properties' did not match any rule defined in the repository path mapping configuration. Skipped.
2025-04-09 14:03:28.190 *! [WARNING] '.git/objects/b0/2d733e80ba87c613c4becba1438cfea345bb63' is a hidden file. Skipped.
2025-04-09 14:03:28.192 ** Adding 'CBSA/src/cobol/creacc.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.194 ** Adding 'CBSA/src/cobol/bnkmenu.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.196 *! [WARNING] 'CBSA/application-conf/Transfer.properties' did not match any rule defined in the repository path mapping configuration. Skipped.
2025-04-09 14:03:28.196 *! [WARNING] '.git/objects/e4/a208249eb9f188dac631a80aa69560a1b5c812' is a hidden file. Skipped.
2025-04-09 14:03:28.197 *! [WARNING] '.git/objects/bb/6a183c5808c83f435ffe292d40ce3c1e78182e' is a hidden file. Skipped.
2025-04-09 14:03:28.198 ** Adding 'CBSA/src/copy/datastr.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.199 *! [WARNING] '.git/objects/30/ec95859415287a39af962b759792828e403684' is a hidden file. Skipped.
2025-04-09 14:03:28.199 *! [WARNING] '.git/objects/d3/e104ac3f1682cf5c81e6a4df77a916b5601adb' is a hidden file. Skipped.
2025-04-09 14:03:28.199 *! [WARNING] '.git/hooks/prepare-commit-msg.sample' is a hidden file. Skipped.
2025-04-09 14:03:28.200 ** Adding 'CBSA/src/copy/bnk1mai.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.201 *! [WARNING] '.git/objects/c8/6c28e6b894571ccad1c6beaa040d1b916a1a77' is a hidden file. Skipped.
2025-04-09 14:03:28.201 *! [WARNING] '.git/objects/b6/deb95fdbfe6a2f08acb265c23cccc973e8b031' is a hidden file. Skipped.
2025-04-09 14:03:28.203 *! [WARNING] 'CBSA/application-conf/PSBgen.properties' did not match any rule defined in the repository path mapping configuration. Skipped.
2025-04-09 14:03:28.203 *! [WARNING] '.git/objects/35/1b0c08fb96d69ec8f2e5c4a71121da780037dd' is a hidden file. Skipped.
2025-04-09 14:03:28.203 *! [WARNING] '.git/objects/1d/7f5fcdba85d4c4d0bc6ab0bab4b287e69242db' is a hidden file. Skipped.
2025-04-09 14:03:28.203 *! [WARNING] '.git/objects/de/ce936b7a48fba884a6d376305fbce1a2fc99e5' is a hidden file. Skipped.
2025-04-09 14:03:28.204 *! [WARNING] '.git/objects/c8/82661ae39a9a8ed30486a8433c1b186cbc5159' is a hidden file. Skipped.
2025-04-09 14:03:28.204 ** Adding 'CBSA/src/cobol/crdtagy3.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.206 *! [WARNING] '.git/objects/99/a8f2520e0dc26a905446e52245f7b6314133d9' is a hidden file. Skipped.
2025-04-09 14:03:28.206 *! [WARNING] '.git/objects/7e/0340c01a352c55eaf478a5c7dbe8c290e50728' is a hidden file. Skipped.
2025-04-09 14:03:28.206 *! [WARNING] '.git/objects/94/08dd2f2709f23766aa4d1ef89e6e175974b396' is a hidden file. Skipped.
2025-04-09 14:03:28.206 *! [WARNING] '.git/objects/97/0f6a926b868353d6a285d20b07d29abfba4292' is a hidden file. Skipped.
2025-04-09 14:03:28.207 ** Adding 'CBSA/src/cobol/bnk1uac.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.210 *! [WARNING] '.git/objects/d0/c5bf02bc846be691c4ea906c10118719d3bed3' is a hidden file. Skipped.
2025-04-09 14:03:28.210 *! [WARNING] '.git/objects/69/27d3b72033e6e7e4f9d6527fb5d347e1fc67d4' is a hidden file. Skipped.
2025-04-09 14:03:28.210 *! [WARNING] '.git/objects/ff/86efc8e05a7fc5e66defbf50820da4ab3bad95' is a hidden file. Skipped.
2025-04-09 14:03:28.211 *! [WARNING] '.git/objects/ab/80f99d7e1e2cf005e04f11f43b710b6cfc765c' is a hidden file. Skipped.
2025-04-09 14:03:28.211 *! [WARNING] '.git/objects/fb/741632c192243a1f4e7799371635f854bd40db' is a hidden file. Skipped.
2025-04-09 14:03:28.211 *! [WARNING] '.git/objects/b6/97ad559100281f7737764166ced34b4398ae0d' is a hidden file. Skipped.
2025-04-09 14:03:28.212 ** Adding 'CBSA/src/cobol/inqcust.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.213 *! [WARNING] '.git/objects/c0/6aacd0c94d044b5fb1d2cb22bc796b946bcf6f' is a hidden file. Skipped.
2025-04-09 14:03:28.213 *! [WARNING] '.git/logs/HEAD' is a hidden file. Skipped.
2025-04-09 14:03:28.214 *! [WARNING] 'CBSA/application-conf/PLI.properties' did not match any rule defined in the repository path mapping configuration. Skipped.
2025-04-09 14:03:28.214 *! [WARNING] '.git/objects/27/0fd7eb4a2109c25b62d78595d8ddd044de4983' is a hidden file. Skipped.
2025-04-09 14:03:28.214 *! [WARNING] '.git/objects/6e/ba9fb7a278153965978bde08e8b79d7549a6e5' is a hidden file. Skipped.
2025-04-09 14:03:28.214 *! [WARNING] '.git/objects/3e/aad50b56f466377be9bc01dca2e4188e888f53' is a hidden file. Skipped.
2025-04-09 14:03:28.215 ** Adding 'CBSA/src/copy/getcompy.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.216 *! [WARNING] '.git/objects/b9/91d0ce37c9ca4668b3329286418980cdb49f42' is a hidden file. Skipped.
2025-04-09 14:03:28.217 ** Adding 'CBSA/src/copy/updacc.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.218 *! [WARNING] 'CBSA/application-conf/README.md' did not match any rule defined in the repository path mapping configuration. Skipped.
2025-04-09 14:03:28.219 ** Adding 'CBSA/src/cobol/bankdata.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.220 ** Adding 'CBSA/src/cobol/dpaytst.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.222 ** Adding 'CBSA/src/cobol/prooffl.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.223 *! [WARNING] '.git/config' is a hidden file. Skipped.
2025-04-09 14:03:28.224 *! [WARNING] '.git/objects/78/c46a8b3d2f9bf33608f9ebaa1ae56260a546b2' is a hidden file. Skipped.
2025-04-09 14:03:28.224 *! [WARNING] 'deployment/deployReleasePackage.yml' did not match any rule defined in the repository path mapping configuration. Skipped.
2025-04-09 14:03:28.224 *! [WARNING] '.git/objects/58/fd7a3493875afbad7928a3b9156e5a83894735' is a hidden file. Skipped.
2025-04-09 14:03:28.225 ** Adding 'CBSA/src/copy/inqcust.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.226 *! [WARNING] '.git/hooks/pre-push.sample' is a hidden file. Skipped.
2025-04-09 14:03:28.226 *! [WARNING] '.git/objects/5e/014abb1c1c7b87e5b7487894a0dd577ecd6903' is a hidden file. Skipped.
2025-04-09 14:03:28.227 ** Adding 'CBSA/src/copy/accdb2.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.229 ** Adding 'CBSA/src/copy/abndinfo.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.230 *! [WARNING] '.git/objects/51/51bf1873f82fc708b223aaecccf9c628c35b1b' is a hidden file. Skipped.
2025-04-09 14:03:28.230 *! [WARNING] '.git/objects/37/1a19b8d93fa4d1f491a4174865ff3b5dc57b6f' is a hidden file. Skipped.
2025-04-09 14:03:28.230 *! [WARNING] '.git/objects/1a/6cc27fb0468b5f7c2a6608e4b3e64009467e22' is a hidden file. Skipped.
2025-04-09 14:03:28.231 *! [WARNING] '.git/hooks/pre-rebase.sample' is a hidden file. Skipped.
2025-04-09 14:03:28.231 *! [WARNING] '.git/info/exclude' is a hidden file. Skipped.
2025-04-09 14:03:28.232 ** Adding 'CBSA/src/cobol/bnk1dac.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.233 *! [WARNING] 'CBSA/application-conf/ACBgen.properties' did not match any rule defined in the repository path mapping configuration. Skipped.
2025-04-09 14:03:28.233 *! [WARNING] '.git/hooks/applypatch-msg.sample' is a hidden file. Skipped.
2025-04-09 14:03:28.234 *! [WARNING] 'CBSA/application-conf/Assembler.properties' did not match any rule defined in the repository path mapping configuration. Skipped.
2025-04-09 14:03:28.234 *! [WARNING] '.git/objects/2f/bc2fdb9097a629e3d0d899d0d4912a5ce4a678' is a hidden file. Skipped.
2025-04-09 14:03:28.234 *! [WARNING] '.git/objects/57/a7db352970bbfae82cf24c95aa6cecc159b0e0' is a hidden file. Skipped.
2025-04-09 14:03:28.235 ** Adding 'CBSA/src/cobol/getcompy.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.236 ** Adding 'CBSA/src/cobol/crecust.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.238 *! [WARNING] '.git/objects/f7/fbe29970a3bd547fcfd6e82df58e45190d46a8' is a hidden file. Skipped.
2025-04-09 14:03:28.238 ** Adding 'applicationDescriptor.yml' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.239 *! [WARNING] '.git/hooks/pre-merge-commit.sample' is a hidden file. Skipped.
2025-04-09 14:03:28.239 *! [WARNING] '.git/objects/d9/c46c2b0b76ac752b67f451dd45995cd5bc96d1' is a hidden file. Skipped.
2025-04-09 14:03:28.239 *! [WARNING] '.git/objects/56/5ded23ee5a835cf93564fb69486880ef001304' is a hidden file. Skipped.
2025-04-09 14:03:28.240 *! [WARNING] '.git/objects/c9/5be47dd3ede400e93ba367b5f5ac433a714d5a' is a hidden file. Skipped.
2025-04-09 14:03:28.240 *! [WARNING] '.git/HEAD' is a hidden file. Skipped.
2025-04-09 14:03:28.241 ** Adding 'CBSA/src/copy/bnk1dcm.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.242 ** Adding 'CBSA/src/cobol/crdtagy4.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.243 ** Adding 'CBSA/src/copy/bnk1acc.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.245 ** Adding 'CBSA/src/copy/inqacc.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.246 ** Adding 'azure-pipelines.yml' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.248 ** Adding 'CBSA/src/cobol/consttst.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.249 ** Adding 'CBSA/src/copy/crecust.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.250 *! [WARNING] '.git/objects/d4/c22ba5bfb0742e2395037184f5fc4174577a8c' is a hidden file. Skipped.
2025-04-09 14:03:28.250 *! [WARNING] '.git/objects/63/a545e907d8944efa88a4cc3619141468ee9446' is a hidden file. Skipped.
2025-04-09 14:03:28.250 *! [WARNING] '.git/objects/d3/7d2d4704218babc4ab9871cc3ea1f5271dc80d' is a hidden file. Skipped.
2025-04-09 14:03:28.251 *! [WARNING] '.git/objects/89/7bf2e97ca69ede559524c31bae8d639ae1b81d' is a hidden file. Skipped.
2025-04-09 14:03:28.251 ** Adding 'CBSA/src/cobol/bnk1cra.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.253 ** Adding 'CBSA/src/cobol/delcus.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.254 *! [WARNING] '.git/refs/tags/rel-1.4.0' is a hidden file. Skipped.
2025-04-09 14:03:28.255 *! [WARNING] '.git/description' is a hidden file. Skipped.
2025-04-09 14:03:28.255 *! [WARNING] '.git/objects/d9/7584fe7d7c5e0120ab762194b119287f6bc91d' is a hidden file. Skipped.
2025-04-09 14:03:28.256 ** Adding 'CBSA/src/copy/bnk1ccm.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.257 ** Adding 'CBSA/src/cobol/accload.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.258 *! [WARNING] '.git/objects/7e/b890a9162ae1995c768e29ce41093b6189ca19' is a hidden file. Skipped.
2025-04-09 14:03:28.258 *! [WARNING] '.git/objects/f7/f461db942e85d137f33609bdb50bd26015d1ec' is a hidden file. Skipped.
2025-04-09 14:03:28.258 *! [WARNING] '.gitattributes' is a hidden file. Skipped.
2025-04-09 14:03:28.258 *! [WARNING] '.git/objects/84/bc44ed9738bc69291a529f9b7b7a1b3cccdc88' is a hidden file. Skipped.
2025-04-09 14:03:28.259 ** Adding 'CBSA/src/cobol/bnk1ccs.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.260 *! [WARNING] '.git/objects/41/c1fc24c5c355423d1cdad4477113b6c6f0945f' is a hidden file. Skipped.
2025-04-09 14:03:28.261 ** Adding 'CBSA/src/copy/updcust.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.262 ** Adding 'CBSA/src/copy/bnk1uam.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.263 ** Adding 'CBSA/src/copy/delacc.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.264 ** Adding 'CBSA/src/cobol/crdtagy5.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.265 ** Adding 'CBSA/src/cobol/bnk1cca.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.266 *! [WARNING] '.git/objects/9d/8cdd3cfd001f9ff47534b9a741f61f757cc90c' is a hidden file. Skipped.
2025-04-09 14:03:28.266 *! [WARNING] '.git/objects/04/a5b554ae15152a060f462fe894e09e7188e394' is a hidden file. Skipped.
2025-04-09 14:03:28.266 *! [WARNING] '.git/objects/f5/0cc01256b3b2f272a59bed37caeb1a61f5ba4c' is a hidden file. Skipped.
2025-04-09 14:03:28.266 *! [WARNING] '.git/hooks/push-to-checkout.sample' is a hidden file. Skipped.
2025-04-09 14:03:28.267 *! [WARNING] 'CBSA/application-conf/CRB.properties' did not match any rule defined in the repository path mapping configuration. Skipped.
2025-04-09 14:03:28.268 ** Adding 'CBSA/src/copy/customer.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.268 *! [WARNING] '.git/logs/refs/heads/rel-1.4.0' is a hidden file. Skipped.
2025-04-09 14:03:28.268 *! [WARNING] '.git/refs/heads/main' is a hidden file. Skipped.
2025-04-09 14:03:28.269 *! [WARNING] '.git/objects/d3/70465392addcb5a86920019826deec0e531a77' is a hidden file. Skipped.
2025-04-09 14:03:28.269 *! [WARNING] '.git/objects/55/57d232d69aa70962e5580123403d3662157e2a' is a hidden file. Skipped.
2025-04-09 14:03:28.269 ** Adding 'CBSA/src/copy/sortcode.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.271 ** Adding 'CBSA/src/cobol/custctrl.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.271 *! [WARNING] '.git/refs/heads/rel-1.4.0' is a hidden file. Skipped.
2025-04-09 14:03:28.272 ** Adding 'CBSA/src/copy/custctrl.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.273 *! [WARNING] 'CBSA/application-conf/Easytrieve.properties' did not match any rule defined in the repository path mapping configuration. Skipped.
2025-04-09 14:03:28.273 *! [WARNING] '.git/objects/9c/3aec3ef67cd80287d375f825fe1b7abfb8be4d' is a hidden file. Skipped.
2025-04-09 14:03:28.274 ** Adding 'CBSA/src/copy/bnk1cdm.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.275 *! [WARNING] 'tagging/createReleaseCandidate.yml' did not match any rule defined in the repository path mapping configuration. Skipped.
2025-04-09 14:03:28.275 *! [WARNING] '.git/objects/68/c29e32bba41130b5f6308b06ffbaf11d7214cc' is a hidden file. Skipped.
2025-04-09 14:03:28.275 *! [WARNING] '.git/objects/bc/ecf21e6187f0d2dba5c129c53954a8363f0d0e' is a hidden file. Skipped.
2025-04-09 14:03:28.276 ** Adding 'zapp.yaml' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.277 *! [WARNING] '.git/objects/e2/f2519db2ebf10fdce9c1d18535aa730e9cc942' is a hidden file. Skipped.
2025-04-09 14:03:28.277 *! [WARNING] '.git/objects/40/46a14e3b7f9b0137176c8039e1034e9e8c39fd' is a hidden file. Skipped.
2025-04-09 14:03:28.277 *! [WARNING] '.git/objects/56/364507a259c6881a4e9a961213a9aa5a6405e7' is a hidden file. Skipped.
2025-04-09 14:03:28.278 *! [WARNING] '.git/objects/14/833274735adb257e1062eaa63d495febe9e962' is a hidden file. Skipped.
2025-04-09 14:03:28.278 *! [WARNING] 'CBSA.yaml' did not match any rule defined in the repository path mapping configuration. Skipped.
2025-04-09 14:03:28.279 ** Adding 'CBSA/src/cobol/abndproc.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.280 *! [WARNING] '.git/objects/b8/cea7df2b43bfac6d4e9336022a286e44a1147c' is a hidden file. Skipped.
2025-04-09 14:03:28.280 *! [WARNING] 'CBSA/application-conf/LinkEdit.properties' did not match any rule defined in the repository path mapping configuration. Skipped.
2025-04-09 14:03:28.281 ** Adding 'CBSA/src/copy/proctran.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.282 *! [WARNING] '.git/objects/02/20c1299e5ed367b9d602d8a11c9909a081c026' is a hidden file. Skipped.
2025-04-09 14:03:28.282 *! [WARNING] '.git/objects/b1/8656b5144b139b6a3b4515d4883a5d0e9ee2ce' is a hidden file. Skipped.
2025-04-09 14:03:28.282 *! [WARNING] '.git/objects/91/4e9ff51f5d103fd6d253b345de9ae1c3cd34d4' is a hidden file. Skipped.
2025-04-09 14:03:28.282 ** Adding 'CBSA/src/copy/xfrfun.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.283 *! [WARNING] '.git/logs/refs/heads/main' is a hidden file. Skipped.
2025-04-09 14:03:28.284 *! [WARNING] 'CBSA/application-conf/bind.properties' did not match any rule defined in the repository path mapping configuration. Skipped.
2025-04-09 14:03:28.284 ** Adding 'CBSA/src/cobol/bnk1dcs.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.285 ** Adding 'CBSA/src/cobol/updcust.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.286 *! [WARNING] '.git/objects/b4/de9dd27006fd84de0770e4a4fc0c9a0393e2f0' is a hidden file. Skipped.
2025-04-09 14:03:28.287 ** Adding 'CBSA/src/cobol/acctctrl.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.288 ** Adding 'CBSA/src/copy/bnk1tfm.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.289 *! [WARNING] '.git/objects/f4/33cbfff90207efad95d399c2632acc1684f942' is a hidden file. Skipped.
2025-04-09 14:03:28.289 ** Adding 'CBSA/src/copy/acctctrl.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.290 *! [WARNING] '.git/objects/57/9fef02baff9b735fc28867aef660f088b64710' is a hidden file. Skipped.
2025-04-09 14:03:28.290 *! [WARNING] 'CBSA/application-conf/MFS.properties' did not match any rule defined in the repository path mapping configuration. Skipped.
2025-04-09 14:03:28.291 ** Adding 'CBSA/src/cobol/crdtagy1.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.292 *! [WARNING] '.git/objects/b1/7e73e90052cbe5144318dc9cf00cdf04589042' is a hidden file. Skipped.
2025-04-09 14:03:28.292 ** Adding 'CBSA/src/copy/paydbcr.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.293 *! [WARNING] '.git/objects/01/d96e12b164d97cc7f2c72489c8cd3205a8b69f' is a hidden file. Skipped.
2025-04-09 14:03:28.293 *! [WARNING] '.git/objects/b0/aed0954293fc2763f3c02ec65cbaa53603015d' is a hidden file. Skipped.
2025-04-09 14:03:28.294 *! [WARNING] 'tagging/createProductionReleaseTag.yml' did not match any rule defined in the repository path mapping configuration. Skipped.
2025-04-09 14:03:28.294 *! [WARNING] '.git/objects/e4/96c6a4e7a960de791e1fd97a02ae6614769936' is a hidden file. Skipped.
2025-04-09 14:03:28.294 *! [WARNING] '.git/objects/aa/3a09c5ec672fef16b4d689127e80ca5ce595ce' is a hidden file. Skipped.
2025-04-09 14:03:28.295 ** Adding 'CBSA/src/copy/account.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.296 *! [WARNING] '.git/objects/fa/3508648b495e92bc320f8110bfd3d78a4d5a3a' is a hidden file. Skipped.
2025-04-09 14:03:28.296 *! [WARNING] '.git/objects/56/eec383e79ddc7d93386976ba31b6f06180c1a0' is a hidden file. Skipped.
2025-04-09 14:03:28.297 ** Adding 'CBSA/src/copy/creacc.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.298 ** Adding 'CBSA/src/copy/getscode.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.299 ** Adding 'CBSA/src/cobol/getscode.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.300 ** Adding 'CBSA/src/copy/bnk1dam.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.301 *! [WARNING] '.git/index' is a hidden file. Skipped.
2025-04-09 14:03:28.301 ** Adding 'CBSA/src/cobol/bnk1tfn.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.302 ** Adding 'CBSA/src/cobol/proload.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.303 *! [WARNING] '.git/objects/a1/4465df829b167bbb644dffc1027434adbf3c32' is a hidden file. Skipped.
2025-04-09 14:03:28.304 ** Adding 'CBSA/src/copy/bnk1cam.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.305 *! [WARNING] '.git/objects/04/9cc7eb352d85ce38026a8f3029f22e711b8b9a' is a hidden file. Skipped.
2025-04-09 14:03:28.305 *! [WARNING] '.git/objects/8e/b541c571cd537e557c27e56eb472e9cafb0308' is a hidden file. Skipped.
2025-04-09 14:03:28.305 *! [WARNING] '.git/objects/47/f9f61e0fdb34ee5ebbf7fc11529e50b079a04b' is a hidden file. Skipped.
2025-04-09 14:03:28.306 ** Adding 'CBSA/src/copy/delcus.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.306 *! [WARNING] '.git/objects/82/14b4cdd014e9e1f1c45fae193c49364def5894' is a hidden file. Skipped.
2025-04-09 14:03:28.307 ** Adding 'CBSA/src/cobol/dbcrfun.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.308 *! [WARNING] '.git/objects/94/7a658dffaf7b8a8a1348ad9dabbdca1f87fbb0' is a hidden file. Skipped.
2025-04-09 14:03:28.308 ** Adding 'CBSA/src/cobol/updacc.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.309 ** Adding 'CBSA/src/cobol/consent.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.310 *! [WARNING] '.git/objects/33/44cbdf7b601794f0ef2341235f09f126fe4562' is a hidden file. Skipped.
2025-04-09 14:03:28.310 *! [WARNING] '.git/hooks/update.sample' is a hidden file. Skipped.
2025-04-09 14:03:28.311 ** Adding 'CBSA/src/copy/inqacccu.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.312 *! [WARNING] '.git/hooks/pre-applypatch.sample' is a hidden file. Skipped.
2025-04-09 14:03:28.312 *! [WARNING] '.git/hooks/pre-commit.sample' is a hidden file. Skipped.
2025-04-09 14:03:28.312 *! [WARNING] '.git/objects/1e/cc8a7b26eee8c6498737ad40975ca9597e7809' is a hidden file. Skipped.
2025-04-09 14:03:28.313 *! [WARNING] 'CBSA/application-conf/application.properties' did not match any rule defined in the repository path mapping configuration. Skipped.
2025-04-09 14:03:28.313 *! [WARNING] 'CBSA/application-conf/file.properties' did not match any rule defined in the repository path mapping configuration. Skipped.
2025-04-09 14:03:28.314 ** Adding 'CBSA/src/cobol/xfrfun.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.315 *! [WARNING] '.git/hooks/commit-msg.sample' is a hidden file. Skipped.
2025-04-09 14:03:28.315 *! [WARNING] '.git/objects/cb/75236314e2fba04aca378ad29061942e6900a5' is a hidden file. Skipped.
2025-04-09 14:03:28.315 *! [WARNING] '.git/objects/b8/33431450f198af575ebdf622a8144df7c0962a' is a hidden file. Skipped.
2025-04-09 14:03:28.315 ** Adding 'CBSA/src/copy/consent.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.317 *! [WARNING] '.git/objects/01/61b1e6bcf09c021887fb147e8223ca06b5cd38' is a hidden file. Skipped.
2025-04-09 14:03:28.317 *! [WARNING] '.git/objects/ff/7f1a74d6d78a6d35e4559b32cdff813a5fb12e' is a hidden file. Skipped.
2025-04-09 14:03:28.317 *! [WARNING] '.git/objects/21/b32b59cad6603ee75673876be89e6c04c4c122' is a hidden file. Skipped.
2025-04-09 14:03:28.317 *! [WARNING] '.git/hooks/sendemail-validate.sample' is a hidden file. Skipped.
2025-04-09 14:03:28.317 *! [WARNING] '.git/hooks/pre-receive.sample' is a hidden file. Skipped.
2025-04-09 14:03:28.318 ** Adding 'CBSA/src/cobol/inqacccu.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.319 ** Adding 'CBSA/src/cobol/crdtagy2.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.320 ** Adding 'CBSA/src/copy/constapi.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.321 ** Adding 'CBSA/src/copy/constdb2.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.322 *! [WARNING] '.git/hooks/post-update.sample' is a hidden file. Skipped.
2025-04-09 14:03:28.322 *! [WARNING] '.git/objects/a5/3363cedacbac465382e28beb8d10c843b769cb' is a hidden file. Skipped.
2025-04-09 14:03:28.322 *! [WARNING] '.git/COMMIT_EDITMSG' is a hidden file. Skipped.
2025-04-09 14:03:28.322 *! [WARNING] '.git/objects/33/4b8f087b5e1bd5c05036a920378e8e1f3c0276' is a hidden file. Skipped.
2025-04-09 14:03:28.323 ** Adding 'CBSA/src/cobol/inqacc.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.324 *! [WARNING] '.git/objects/b5/6eafbe98c4e46afb0c8c60ee97cf437292a68c' is a hidden file. Skipped.
2025-04-09 14:03:28.324 ** Adding 'CBSA/src/copy/procdb2.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.325 *! [WARNING] '.git/hooks/fsmonitor-watchman.sample' is a hidden file. Skipped.
2025-04-09 14:03:28.325 *! [WARNING] '.git/objects/6f/3549f765104b58d630d2a4ce871fc1b9e4bb7a' is a hidden file. Skipped.
2025-04-09 14:03:28.325 *! [WARNING] '.git/objects/f5/5399eea902ae9bc01584c1e3bc71f4db98eef6' is a hidden file. Skipped.
2025-04-09 14:03:28.326 *! [WARNING] 'CBSA/application-conf/Cobol.properties' did not match any rule defined in the repository path mapping configuration. Skipped.
2025-04-09 14:03:28.326 *! [WARNING] '.git/objects/a6/ee2080f7c783724cafee89a81049a3f2893e75' is a hidden file. Skipped.
2025-04-09 14:03:28.326 *! [WARNING] '.git/objects/34/390dbd6e6f281f6101d179897949a51393c264' is a hidden file. Skipped.
2025-04-09 14:03:28.326 *! [WARNING] 'CBSA/application-conf/REXX.properties' did not match any rule defined in the repository path mapping configuration. Skipped.
2025-04-09 14:03:28.327 *! [WARNING] 'CBSA/application-conf/reports.properties' did not match any rule defined in the repository path mapping configuration. Skipped.
2025-04-09 14:03:28.327 *! [WARNING] 'CBSA/application-conf/languageConfigurationMapping.properties' did not match any rule defined in the repository path mapping configuration. Skipped.
2025-04-09 14:03:28.327 *! [WARNING] '.git/objects/02/166f4a72bce317273224f2a3d700916aca7d37' is a hidden file. Skipped.
2025-04-09 14:03:28.328 *! [WARNING] '.git/objects/c2/432e4bf3b85f883fdcaff1adb419b1ebf3fd18' is a hidden file. Skipped.
2025-04-09 14:03:28.328 *! [WARNING] '.git/objects/30/7bdf0f7c3097788578484f004d2a0fa05d9271' is a hidden file. Skipped.
2025-04-09 14:03:28.328 *! [WARNING] '.git/objects/66/afa88844c422af69da0d35243993d4e50dac3c' is a hidden file. Skipped.
2025-04-09 14:03:28.328 ** Adding 'CBSA/src/cobol/accoffl.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.329 *! [WARNING] '.git/objects/31/52dd3e66ff2ee3aa671a03aa7e3cb41ca864a5' is a hidden file. Skipped.
2025-04-09 14:03:28.330 ** Adding 'CBSA/src/cobol/dpayapi.cbl' to Application Descriptor into source group 'cobol'.
2025-04-09 14:03:28.331 *! [WARNING] '.git/objects/b2/849d92d4dd7bd253384f910a069f98802f64f1' is a hidden file. Skipped.
2025-04-09 14:03:28.331 *! [WARNING] '.git/objects/b4/79ed3b38c3f9680850dc34a3c9d10e24ddb52f' is a hidden file. Skipped.
2025-04-09 14:03:28.331 ** Adding 'CBSA/src/copy/contdb2.cpy' to Application Descriptor into source group 'copy'.
2025-04-09 14:03:28.332 *! [WARNING] '.git/objects/b6/f7290235118fd79e38875919d38e2885dc2335' is a hidden file. Skipped.
2025-04-09 14:03:28.333 *! [WARNING] 'CBSA/application-conf/DBDgen.properties' did not match any rule defined in the repository path mapping configuration. Skipped.
2025-04-09 14:03:28.453 ** Created Application Description file '/u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA/CBSA.yaml'
[INFO] /usr/lpp/dbb/v3r0/bin/groovyz /u/ibmuser/dbb-git-migration-modeler-work/src/groovy/assessUsage.groovy 				--configFile /u/ibmuser/dbb-git-migration-modeler-work/DBB_GIT_MIGRATION_MODELER.config 				--application CBSA 				--moveFiles 				--logFile /u/ibmuser/dbb-git-migration-modeler-work/logs/3-CBSA-assessUsage.log
2025-04-09 14:03:55.371 ** Script configuration:
2025-04-09 14:03:55.413 	DBB_MODELER_APPCONFIG_DIR -> /u/ibmuser/dbb-git-migration-modeler-work/modeler-configs
2025-04-09 14:03:55.416 	application -> CBSA
2025-04-09 14:03:55.419 	configurationFilePath -> /u/ibmuser/dbb-git-migration-modeler-work/DBB_GIT_MIGRATION_MODELER.config
2025-04-09 14:03:55.423 	DBB_MODELER_APPLICATION_DIR -> /u/ibmuser/dbb-git-migration-modeler-work/repositories
2025-04-09 14:03:55.427 	logFile -> /u/ibmuser/dbb-git-migration-modeler-work/logs/3-CBSA-assessUsage.log
2025-04-09 14:03:55.430 	DBB_MODELER_METADATASTORE_TYPE -> db2
2025-04-09 14:03:55.433 	DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE -> /u/ibmuser/dbb-git-migration-modeler-work/db2Connection.conf
2025-04-09 14:03:55.435 	DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORD -> 
2025-04-09 14:03:55.438 	moveFiles -> true
2025-04-09 14:03:55.441 	DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE -> /u/ibmuser/dbb-git-migration-modeler-work/MDALBIN-password.txt
2025-04-09 14:03:55.444 	DBB_MODELER_DB2_METADATASTORE_JDBC_ID -> MDALBIN
2025-04-09 14:03:56.121 ** Getting the list of files of 'Include File' type.
2025-04-09 14:03:56.197 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/datastr.cpy'.
2025-04-09 14:03:56.711 	Files depending on 'CBSA/src/copy/datastr.cpy' :
2025-04-09 14:03:56.720 	'CBSA/CBSA/src/cobol/bankdata.cbl' in  Application  'CBSA'
2025-04-09 14:03:56.721 	'CBSA/CBSA/src/cobol/xfrfun.cbl' in  Application  'CBSA'
2025-04-09 14:03:56.722 	'CBSA/CBSA/src/cobol/dbcrfun.cbl' in  Application  'CBSA'
2025-04-09 14:03:56.723 	'CBSA/CBSA/src/cobol/crdtagy5.cbl' in  Application  'CBSA'
2025-04-09 14:03:56.724 	'CBSA/CBSA/src/cobol/updcust.cbl' in  Application  'CBSA'
2025-04-09 14:03:56.724 	'CBSA/CBSA/src/cobol/delcus.cbl' in  Application  'CBSA'
2025-04-09 14:03:56.725 	'CBSA/CBSA/src/cobol/updacc.cbl' in  Application  'CBSA'
2025-04-09 14:03:56.726 	'CBSA/CBSA/src/cobol/delacc.cbl' in  Application  'CBSA'
2025-04-09 14:03:56.727 	'CBSA/CBSA/src/cobol/crdtagy4.cbl' in  Application  'CBSA'
2025-04-09 14:03:56.728 	'CBSA/CBSA/src/cobol/crecust.cbl' in  Application  'CBSA'
2025-04-09 14:03:56.729 	'CBSA/CBSA/src/cobol/inqcust.cbl' in  Application  'CBSA'
2025-04-09 14:03:56.730 	'CBSA/CBSA/src/cobol/inqacccu.cbl' in  Application  'CBSA'
2025-04-09 14:03:56.730 	'CBSA/CBSA/src/cobol/inqacc.cbl' in  Application  'CBSA'
2025-04-09 14:03:56.731 	'CBSA/CBSA/src/cobol/crdtagy1.cbl' in  Application  'CBSA'
2025-04-09 14:03:56.732 	'CBSA/CBSA/src/cobol/crdtagy3.cbl' in  Application  'CBSA'
2025-04-09 14:03:56.733 	'CBSA/CBSA/src/cobol/crdtagy2.cbl' in  Application  'CBSA'
2025-04-09 14:03:56.734 	'CBSA/CBSA/src/cobol/creacc.cbl' in  Application  'CBSA'
2025-04-09 14:03:56.735 	==> 'datastr' is owned by the 'CBSA' application
2025-04-09 14:03:56.747 	==> Updating usage of Include File 'datastr' to 'private' in '/u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA/applicationDescriptor.yml'.
2025-04-09 14:03:56.862 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/bnk1ccm.cpy'.
2025-04-09 14:03:57.058 	Files depending on 'CBSA/src/copy/bnk1ccm.cpy' :
2025-04-09 14:03:57.058 	'CBSA/CBSA/src/cobol/bnk1ccs.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.058 	==> 'bnk1ccm' is owned by the 'CBSA' application
2025-04-09 14:03:57.059 	==> Updating usage of Include File 'bnk1ccm' to 'private' in '/u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA/applicationDescriptor.yml'.
2025-04-09 14:03:57.107 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/bnk1dam.cpy'.
2025-04-09 14:03:57.197 	Files depending on 'CBSA/src/copy/bnk1dam.cpy' :
2025-04-09 14:03:57.197 	'CBSA/CBSA/src/cobol/bnk1dac.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.197 	==> 'bnk1dam' is owned by the 'CBSA' application
2025-04-09 14:03:57.198 	==> Updating usage of Include File 'bnk1dam' to 'private' in '/u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA/applicationDescriptor.yml'.
2025-04-09 14:03:57.238 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/paydbcr.cpy'.
2025-04-09 14:03:57.311 	Files depending on 'CBSA/src/copy/paydbcr.cpy' :
2025-04-09 14:03:57.311 	'CBSA/CBSA/src/cobol/dbcrfun.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.311 	==> 'paydbcr' is owned by the 'CBSA' application
2025-04-09 14:03:57.313 	==> Updating usage of Include File 'paydbcr' to 'private' in '/u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA/applicationDescriptor.yml'.
2025-04-09 14:03:57.346 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/bnk1cam.cpy'.
2025-04-09 14:03:57.417 	Files depending on 'CBSA/src/copy/bnk1cam.cpy' :
2025-04-09 14:03:57.417 	'CBSA/CBSA/src/cobol/bnk1cac.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.417 	==> 'bnk1cam' is owned by the 'CBSA' application
2025-04-09 14:03:57.418 	==> Updating usage of Include File 'bnk1cam' to 'private' in '/u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA/applicationDescriptor.yml'.
2025-04-09 14:03:57.448 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/creacc.cpy'.
2025-04-09 14:03:57.509 	Files depending on 'CBSA/src/copy/creacc.cpy' :
2025-04-09 14:03:57.510 	'CBSA/CBSA/src/cobol/creacc.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.510 	==> 'creacc' is owned by the 'CBSA' application
2025-04-09 14:03:57.511 	==> Updating usage of Include File 'creacc' to 'private' in '/u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA/applicationDescriptor.yml'.
2025-04-09 14:03:57.539 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/bnk1dcm.cpy'.
2025-04-09 14:03:57.602 	Files depending on 'CBSA/src/copy/bnk1dcm.cpy' :
2025-04-09 14:03:57.602 	'CBSA/CBSA/src/cobol/bnk1dcs.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.602 	==> 'bnk1dcm' is owned by the 'CBSA' application
2025-04-09 14:03:57.603 	==> Updating usage of Include File 'bnk1dcm' to 'private' in '/u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA/applicationDescriptor.yml'.
2025-04-09 14:03:57.631 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/procdb2.cpy'.
2025-04-09 14:03:57.685 	The Include File 'procdb2' is not referenced at all.
2025-04-09 14:03:57.715 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/constdb2.cpy'.
2025-04-09 14:03:57.765 	The Include File 'constdb2' is not referenced at all.
2025-04-09 14:03:57.791 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/abndinfo.cpy'.
2025-04-09 14:03:57.899 	Files depending on 'CBSA/src/copy/abndinfo.cpy' :
2025-04-09 14:03:57.899 	'CBSA/CBSA/src/cobol/bnk1cra.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.899 	'CBSA/CBSA/src/cobol/crdtagy5.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.899 	'CBSA/CBSA/src/cobol/custctrl.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.899 	'CBSA/CBSA/src/cobol/bnk1ccs.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.899 	'CBSA/CBSA/src/cobol/crdtagy4.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.900 	'CBSA/CBSA/src/cobol/acctctrl.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.900 	'CBSA/CBSA/src/cobol/bnk1uac.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.900 	'CBSA/CBSA/src/cobol/crecust.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.900 	'CBSA/CBSA/src/cobol/inqcust.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.900 	'CBSA/CBSA/src/cobol/inqacccu.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.900 	'CBSA/CBSA/src/cobol/inqacc.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.901 	'CBSA/CBSA/src/cobol/abndproc.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.901 	'CBSA/CBSA/src/cobol/creacc.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.901 	'CBSA/CBSA/src/cobol/xfrfun.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.901 	'CBSA/CBSA/src/cobol/dbcrfun.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.901 	'CBSA/CBSA/src/cobol/bnk1tfn.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.901 	'CBSA/CBSA/src/cobol/bnk1cca.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.901 	'CBSA/CBSA/src/cobol/updcust.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.902 	'CBSA/CBSA/src/cobol/delcus.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.902 	'CBSA/CBSA/src/cobol/bnk1cac.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.902 	'CBSA/CBSA/src/cobol/updacc.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.902 	'CBSA/CBSA/src/cobol/delacc.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.902 	'CBSA/CBSA/src/cobol/bnk1dac.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.902 	'CBSA/CBSA/src/cobol/bnkmenu.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.902 	'CBSA/CBSA/src/cobol/crdtagy1.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.903 	'CBSA/CBSA/src/cobol/crdtagy3.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.903 	'CBSA/CBSA/src/cobol/crdtagy2.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.903 	'CBSA/CBSA/src/cobol/bnk1dcs.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.903 	==> 'abndinfo' is owned by the 'CBSA' application
2025-04-09 14:03:57.904 	==> Updating usage of Include File 'abndinfo' to 'private' in '/u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA/applicationDescriptor.yml'.
2025-04-09 14:03:57.931 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/bnk1tfm.cpy'.
2025-04-09 14:03:57.989 	Files depending on 'CBSA/src/copy/bnk1tfm.cpy' :
2025-04-09 14:03:57.989 	'CBSA/CBSA/src/cobol/bnk1tfn.cbl' in  Application  'CBSA'
2025-04-09 14:03:57.989 	==> 'bnk1tfm' is owned by the 'CBSA' application
2025-04-09 14:03:57.990 	==> Updating usage of Include File 'bnk1tfm' to 'private' in '/u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA/applicationDescriptor.yml'.
2025-04-09 14:03:58.016 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/bnk1acc.cpy'.
2025-04-09 14:03:58.078 	Files depending on 'CBSA/src/copy/bnk1acc.cpy' :
2025-04-09 14:03:58.079 	'CBSA/CBSA/src/cobol/bnk1cca.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.079 	==> 'bnk1acc' is owned by the 'CBSA' application
2025-04-09 14:03:58.080 	==> Updating usage of Include File 'bnk1acc' to 'private' in '/u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA/applicationDescriptor.yml'.
2025-04-09 14:03:58.106 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/proctran.cpy'.
2025-04-09 14:03:58.176 	Files depending on 'CBSA/src/copy/proctran.cpy' :
2025-04-09 14:03:58.176 	'CBSA/CBSA/src/cobol/crecust.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.176 	'CBSA/CBSA/src/cobol/xfrfun.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.177 	'CBSA/CBSA/src/cobol/dbcrfun.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.177 	'CBSA/CBSA/src/cobol/delcus.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.177 	'CBSA/CBSA/src/cobol/delacc.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.177 	'CBSA/CBSA/src/cobol/creacc.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.177 	==> 'proctran' is owned by the 'CBSA' application
2025-04-09 14:03:58.178 	==> Updating usage of Include File 'proctran' to 'private' in '/u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA/applicationDescriptor.yml'.
2025-04-09 14:03:58.203 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/sortcode.cpy'.
2025-04-09 14:03:58.285 	Files depending on 'CBSA/src/copy/sortcode.cpy' :
2025-04-09 14:03:58.286 	'CBSA/CBSA/src/cobol/bankdata.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.286 	'CBSA/CBSA/src/cobol/xfrfun.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.286 	'CBSA/CBSA/src/cobol/dbcrfun.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.286 	'CBSA/CBSA/src/cobol/crdtagy5.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.286 	'CBSA/CBSA/src/cobol/custctrl.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.286 	'CBSA/CBSA/src/cobol/updcust.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.287 	'CBSA/CBSA/src/cobol/delcus.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.287 	'CBSA/CBSA/src/cobol/getscode.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.287 	'CBSA/CBSA/src/cobol/updacc.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.287 	'CBSA/CBSA/src/cobol/delacc.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.287 	'CBSA/CBSA/src/cobol/crdtagy4.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.287 	'CBSA/CBSA/src/cobol/acctctrl.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.287 	'CBSA/CBSA/src/cobol/crecust.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.287 	'CBSA/CBSA/src/cobol/inqcust.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.288 	'CBSA/CBSA/src/cobol/inqacccu.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.288 	'CBSA/CBSA/src/cobol/inqacc.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.288 	'CBSA/CBSA/src/cobol/crdtagy1.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.288 	'CBSA/CBSA/src/cobol/crdtagy3.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.288 	'CBSA/CBSA/src/cobol/crdtagy2.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.288 	'CBSA/CBSA/src/cobol/creacc.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.288 	==> 'sortcode' is owned by the 'CBSA' application
2025-04-09 14:03:58.289 	==> Updating usage of Include File 'sortcode' to 'private' in '/u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA/applicationDescriptor.yml'.
2025-04-09 14:03:58.315 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/acctctrl.cpy'.
2025-04-09 14:03:58.375 	Files depending on 'CBSA/src/copy/acctctrl.cpy' :
2025-04-09 14:03:58.375 	'CBSA/CBSA/src/cobol/acctctrl.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.375 	'CBSA/CBSA/src/cobol/bankdata.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.376 	'CBSA/CBSA/src/cobol/delacc.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.376 	'CBSA/CBSA/src/cobol/creacc.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.376 	==> 'acctctrl' is owned by the 'CBSA' application
2025-04-09 14:03:58.380 	==> Updating usage of Include File 'acctctrl' to 'private' in '/u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA/applicationDescriptor.yml'.
2025-04-09 14:03:58.408 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/custctrl.cpy'.
2025-04-09 14:03:58.484 	Files depending on 'CBSA/src/copy/custctrl.cpy' :
2025-04-09 14:03:58.485 	'CBSA/CBSA/src/cobol/bankdata.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.485 	'CBSA/CBSA/src/cobol/crecust.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.485 	'CBSA/CBSA/src/cobol/custctrl.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.485 	==> 'custctrl' is owned by the 'CBSA' application
2025-04-09 14:03:58.489 	==> Updating usage of Include File 'custctrl' to 'private' in '/u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA/applicationDescriptor.yml'.
2025-04-09 14:03:58.519 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/xfrfun.cpy'.
2025-04-09 14:03:58.568 	Files depending on 'CBSA/src/copy/xfrfun.cpy' :
2025-04-09 14:03:58.568 	'CBSA/CBSA/src/cobol/xfrfun.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.568 	==> 'xfrfun' is owned by the 'CBSA' application
2025-04-09 14:03:58.569 	==> Updating usage of Include File 'xfrfun' to 'private' in '/u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA/applicationDescriptor.yml'.
2025-04-09 14:03:58.595 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/crecust.cpy'.
2025-04-09 14:03:58.636 	Files depending on 'CBSA/src/copy/crecust.cpy' :
2025-04-09 14:03:58.636 	'CBSA/CBSA/src/cobol/crecust.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.636 	==> 'crecust' is owned by the 'CBSA' application
2025-04-09 14:03:58.637 	==> Updating usage of Include File 'crecust' to 'private' in '/u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA/applicationDescriptor.yml'.
2025-04-09 14:03:58.667 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/inqacccu.cpy'.
2025-04-09 14:03:58.717 	Files depending on 'CBSA/src/copy/inqacccu.cpy' :
2025-04-09 14:03:58.717 	'CBSA/CBSA/src/cobol/inqacccu.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.717 	'CBSA/CBSA/src/cobol/bnk1cca.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.717 	'CBSA/CBSA/src/cobol/delcus.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.717 	'CBSA/CBSA/src/cobol/creacc.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.717 	==> 'inqacccu' is owned by the 'CBSA' application
2025-04-09 14:03:58.718 	==> Updating usage of Include File 'inqacccu' to 'private' in '/u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA/applicationDescriptor.yml'.
2025-04-09 14:03:58.743 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/bnk1cdm.cpy'.
2025-04-09 14:03:58.791 	Files depending on 'CBSA/src/copy/bnk1cdm.cpy' :
2025-04-09 14:03:58.792 	'CBSA/CBSA/src/cobol/bnk1cra.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.792 	==> 'bnk1cdm' is owned by the 'CBSA' application
2025-04-09 14:03:58.792 	==> Updating usage of Include File 'bnk1cdm' to 'private' in '/u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA/applicationDescriptor.yml'.
2025-04-09 14:03:58.816 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/getscode.cpy'.
2025-04-09 14:03:58.854 	Files depending on 'CBSA/src/copy/getscode.cpy' :
2025-04-09 14:03:58.854 	'CBSA/CBSA/src/cobol/getscode.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.854 	==> 'getscode' is owned by the 'CBSA' application
2025-04-09 14:03:58.855 	==> Updating usage of Include File 'getscode' to 'private' in '/u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA/applicationDescriptor.yml'.
2025-04-09 14:03:58.878 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/consent.cpy'.
2025-04-09 14:03:58.922 	Files depending on 'CBSA/src/copy/consent.cpy' :
2025-04-09 14:03:58.922 	'CBSA/CBSA/src/cobol/dpayapi.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.922 	'CBSA/CBSA/src/cobol/dpaytst.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.922 	'CBSA/CBSA/src/cobol/consent.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.922 	==> 'consent' is owned by the 'CBSA' application
2025-04-09 14:03:58.923 	==> Updating usage of Include File 'consent' to 'private' in '/u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA/applicationDescriptor.yml'.
2025-04-09 14:03:58.947 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/bnk1mai.cpy'.
2025-04-09 14:03:58.991 	Files depending on 'CBSA/src/copy/bnk1mai.cpy' :
2025-04-09 14:03:58.991 	'CBSA/CBSA/src/cobol/bnkmenu.cbl' in  Application  'CBSA'
2025-04-09 14:03:58.991 	==> 'bnk1mai' is owned by the 'CBSA' application
2025-04-09 14:03:58.992 	==> Updating usage of Include File 'bnk1mai' to 'private' in '/u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA/applicationDescriptor.yml'.
2025-04-09 14:03:59.020 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/constapi.cpy'.
2025-04-09 14:03:59.080 	Files depending on 'CBSA/src/copy/constapi.cpy' :
2025-04-09 14:03:59.080 	'CBSA/CBSA/src/cobol/dpayapi.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.080 	'CBSA/CBSA/src/cobol/dpaytst.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.080 	'CBSA/CBSA/src/cobol/consttst.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.080 	'CBSA/CBSA/src/cobol/consent.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.081 	==> 'constapi' is owned by the 'CBSA' application
2025-04-09 14:03:59.081 	==> Updating usage of Include File 'constapi' to 'private' in '/u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA/applicationDescriptor.yml'.
2025-04-09 14:03:59.108 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/delacc.cpy'.
2025-04-09 14:03:59.147 	Files depending on 'CBSA/src/copy/delacc.cpy' :
2025-04-09 14:03:59.147 	'CBSA/CBSA/src/cobol/delacc.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.147 	==> 'delacc' is owned by the 'CBSA' application
2025-04-09 14:03:59.148 	==> Updating usage of Include File 'delacc' to 'private' in '/u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA/applicationDescriptor.yml'.
2025-04-09 14:03:59.173 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/delcus.cpy'.
2025-04-09 14:03:59.222 	Files depending on 'CBSA/src/copy/delcus.cpy' :
2025-04-09 14:03:59.223 	'CBSA/CBSA/src/cobol/delcus.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.223 	'CBSA/CBSA/src/cobol/bnk1dcs.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.223 	==> 'delcus' is owned by the 'CBSA' application
2025-04-09 14:03:59.223 	==> Updating usage of Include File 'delcus' to 'private' in '/u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA/applicationDescriptor.yml'.
2025-04-09 14:03:59.247 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/getcompy.cpy'.
2025-04-09 14:03:59.288 	Files depending on 'CBSA/src/copy/getcompy.cpy' :
2025-04-09 14:03:59.288 	'CBSA/CBSA/src/cobol/getcompy.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.288 	==> 'getcompy' is owned by the 'CBSA' application
2025-04-09 14:03:59.289 	==> Updating usage of Include File 'getcompy' to 'private' in '/u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA/applicationDescriptor.yml'.
2025-04-09 14:03:59.314 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/accdb2.cpy'.
2025-04-09 14:03:59.351 	The Include File 'accdb2' is not referenced at all.
2025-04-09 14:03:59.374 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/contdb2.cpy'.
2025-04-09 14:03:59.416 	The Include File 'contdb2' is not referenced at all.
2025-04-09 14:03:59.440 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/inqcust.cpy'.
2025-04-09 14:03:59.487 	Files depending on 'CBSA/src/copy/inqcust.cpy' :
2025-04-09 14:03:59.487 	'CBSA/CBSA/src/cobol/inqcust.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.487 	'CBSA/CBSA/src/cobol/inqacccu.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.487 	'CBSA/CBSA/src/cobol/delcus.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.487 	'CBSA/CBSA/src/cobol/creacc.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.487 	'CBSA/CBSA/src/cobol/bnk1dcs.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.487 	==> 'inqcust' is owned by the 'CBSA' application
2025-04-09 14:03:59.488 	==> Updating usage of Include File 'inqcust' to 'private' in '/u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA/applicationDescriptor.yml'.
2025-04-09 14:03:59.513 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/updacc.cpy'.
2025-04-09 14:03:59.551 	Files depending on 'CBSA/src/copy/updacc.cpy' :
2025-04-09 14:03:59.551 	'CBSA/CBSA/src/cobol/updacc.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.551 	==> 'updacc' is owned by the 'CBSA' application
2025-04-09 14:03:59.552 	==> Updating usage of Include File 'updacc' to 'private' in '/u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA/applicationDescriptor.yml'.
2025-04-09 14:03:59.575 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/inqacc.cpy'.
2025-04-09 14:03:59.622 	Files depending on 'CBSA/src/copy/inqacc.cpy' :
2025-04-09 14:03:59.623 	'CBSA/CBSA/src/cobol/bnk1dac.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.623 	'CBSA/CBSA/src/cobol/inqacc.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.623 	==> 'inqacc' is owned by the 'CBSA' application
2025-04-09 14:03:59.624 	==> Updating usage of Include File 'inqacc' to 'private' in '/u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA/applicationDescriptor.yml'.
2025-04-09 14:03:59.647 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/updcust.cpy'.
2025-04-09 14:03:59.691 	Files depending on 'CBSA/src/copy/updcust.cpy' :
2025-04-09 14:03:59.691 	'CBSA/CBSA/src/cobol/updcust.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.691 	'CBSA/CBSA/src/cobol/bnk1dcs.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.691 	==> 'updcust' is owned by the 'CBSA' application
2025-04-09 14:03:59.693 	==> Updating usage of Include File 'updcust' to 'private' in '/u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA/applicationDescriptor.yml'.
2025-04-09 14:03:59.718 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/bnk1uam.cpy'.
2025-04-09 14:03:59.767 	Files depending on 'CBSA/src/copy/bnk1uam.cpy' :
2025-04-09 14:03:59.767 	'CBSA/CBSA/src/cobol/bnk1uac.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.767 	==> 'bnk1uam' is owned by the 'CBSA' application
2025-04-09 14:03:59.767 	==> Updating usage of Include File 'bnk1uam' to 'private' in '/u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA/applicationDescriptor.yml'.
2025-04-09 14:03:59.791 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/account.cpy'.
2025-04-09 14:03:59.844 	Files depending on 'CBSA/src/copy/account.cpy' :
2025-04-09 14:03:59.844 	'CBSA/CBSA/src/cobol/inqacccu.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.844 	'CBSA/CBSA/src/cobol/xfrfun.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.844 	'CBSA/CBSA/src/cobol/dbcrfun.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.845 	'CBSA/CBSA/src/cobol/inqacc.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.845 	'CBSA/CBSA/src/cobol/dpaytst.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.845 	'CBSA/CBSA/src/cobol/delcus.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.845 	'CBSA/CBSA/src/cobol/consent.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.845 	'CBSA/CBSA/src/cobol/updacc.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.845 	'CBSA/CBSA/src/cobol/delacc.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.845 	'CBSA/CBSA/src/cobol/creacc.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.845 	==> 'account' is owned by the 'CBSA' application
2025-04-09 14:03:59.846 	==> Updating usage of Include File 'account' to 'private' in '/u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA/applicationDescriptor.yml'.
2025-04-09 14:03:59.870 ** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/customer.cpy'.
2025-04-09 14:03:59.922 	Files depending on 'CBSA/src/copy/customer.cpy' :
2025-04-09 14:03:59.923 	'CBSA/CBSA/src/cobol/bankdata.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.923 	'CBSA/CBSA/src/cobol/crecust.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.923 	'CBSA/CBSA/src/cobol/inqcust.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.923 	'CBSA/CBSA/src/cobol/inqacccu.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.923 	'CBSA/CBSA/src/cobol/updcust.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.923 	'CBSA/CBSA/src/cobol/delcus.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.923 	'CBSA/CBSA/src/cobol/creacc.cbl' in  Application  'CBSA'
2025-04-09 14:03:59.923 	==> 'customer' is owned by the 'CBSA' application
2025-04-09 14:03:59.924 	==> Updating usage of Include File 'customer' to 'private' in '/u/ibmuser/dbb-git-migration-modeler-work/repositories/CBSA/applicationDescriptor.yml'.
2025-04-09 14:03:59.951 ** Getting the list of files of 'Program' type.
2025-04-09 14:03:59.976 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/bnk1cac.cbl'.
2025-04-09 14:04:00.019 	The Program 'bnk1cac' is not called by any other program.
2025-04-09 14:04:00.120 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/proload.cbl'.
2025-04-09 14:04:00.163 	The Program 'proload' is not called by any other program.
2025-04-09 14:04:00.188 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/bnk1dac.cbl'.
2025-04-09 14:04:00.225 	The Program 'bnk1dac' is not called by any other program.
2025-04-09 14:04:00.248 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/dpayapi.cbl'.
2025-04-09 14:04:00.288 	The Program 'dpayapi' is not called by any other program.
2025-04-09 14:04:00.312 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/dpaytst.cbl'.
2025-04-09 14:04:00.351 	The Program 'dpaytst' is not called by any other program.
2025-04-09 14:04:00.399 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/accoffl.cbl'.
2025-04-09 14:04:00.435 	The Program 'accoffl' is not called by any other program.
2025-04-09 14:04:00.461 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/crdtagy5.cbl'.
2025-04-09 14:04:00.496 	The Program 'crdtagy5' is not called by any other program.
2025-04-09 14:04:00.519 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/creacc.cbl'.
2025-04-09 14:04:00.540 	The Program 'creacc' is not called by any other program.
2025-04-09 14:04:00.584 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/crdtagy4.cbl'.
2025-04-09 14:04:00.623 	The Program 'crdtagy4' is not called by any other program.
2025-04-09 14:04:00.646 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/bnkmenu.cbl'.
2025-04-09 14:04:00.680 	The Program 'bnkmenu' is not called by any other program.
2025-04-09 14:04:00.705 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/bankdata.cbl'.
2025-04-09 14:04:00.746 	The Program 'bankdata' is not called by any other program.
2025-04-09 14:04:00.774 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/prooffl.cbl'.
2025-04-09 14:04:00.812 	The Program 'prooffl' is not called by any other program.
2025-04-09 14:04:00.835 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/bnk1tfn.cbl'.
2025-04-09 14:04:00.867 	The Program 'bnk1tfn' is not called by any other program.
2025-04-09 14:04:00.890 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/bnk1cca.cbl'.
2025-04-09 14:04:00.924 	The Program 'bnk1cca' is not called by any other program.
2025-04-09 14:04:00.948 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/dbcrfun.cbl'.
2025-04-09 14:04:00.980 	The Program 'dbcrfun' is not called by any other program.
2025-04-09 14:04:01.008 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/acctctrl.cbl'.
2025-04-09 14:04:01.027 	The Program 'acctctrl' is not called by any other program.
2025-04-09 14:04:01.052 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/custctrl.cbl'.
2025-04-09 14:04:01.070 	The Program 'custctrl' is not called by any other program.
2025-04-09 14:04:01.092 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/xfrfun.cbl'.
2025-04-09 14:04:01.116 	The Program 'xfrfun' is not called by any other program.
2025-04-09 14:04:01.137 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/crecust.cbl'.
2025-04-09 14:04:01.159 	The Program 'crecust' is not called by any other program.
2025-04-09 14:04:01.182 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/inqacccu.cbl'.
2025-04-09 14:04:01.201 	The Program 'inqacccu' is not called by any other program.
2025-04-09 14:04:01.223 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/getscode.cbl'.
2025-04-09 14:04:01.239 	The Program 'getscode' is not called by any other program.
2025-04-09 14:04:01.261 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/consent.cbl'.
2025-04-09 14:04:01.278 	The Program 'consent' is not called by any other program.
2025-04-09 14:04:01.300 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/crdtagy3.cbl'.
2025-04-09 14:04:01.334 	The Program 'crdtagy3' is not called by any other program.
2025-04-09 14:04:01.356 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/delacc.cbl'.
2025-04-09 14:04:01.377 	The Program 'delacc' is not called by any other program.
2025-04-09 14:04:01.398 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/delcus.cbl'.
2025-04-09 14:04:01.429 	The Program 'delcus' is not called by any other program.
2025-04-09 14:04:01.450 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/bnk1dcs.cbl'.
2025-04-09 14:04:01.490 	The Program 'bnk1dcs' is not called by any other program.
2025-04-09 14:04:01.513 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/crdtagy2.cbl'.
2025-04-09 14:04:01.548 	The Program 'crdtagy2' is not called by any other program.
2025-04-09 14:04:01.571 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/abndproc.cbl'.
2025-04-09 14:04:01.610 	The Program 'abndproc' is not called by any other program.
2025-04-09 14:04:01.642 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/bnk1ccs.cbl'.
2025-04-09 14:04:01.684 	The Program 'bnk1ccs' is not called by any other program.
2025-04-09 14:04:01.708 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/crdtagy1.cbl'.
2025-04-09 14:04:01.744 	The Program 'crdtagy1' is not called by any other program.
2025-04-09 14:04:01.770 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/bnk1cra.cbl'.
2025-04-09 14:04:01.807 	The Program 'bnk1cra' is not called by any other program.
2025-04-09 14:04:01.832 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/getcompy.cbl'.
2025-04-09 14:04:01.865 	The Program 'getcompy' is not called by any other program.
2025-04-09 14:04:01.901 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/accload.cbl'.
2025-04-09 14:04:01.939 	The Program 'accload' is not called by any other program.
2025-04-09 14:04:01.963 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/inqcust.cbl'.
2025-04-09 14:04:01.982 	The Program 'inqcust' is not called by any other program.
2025-04-09 14:04:02.007 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/bnk1uac.cbl'.
2025-04-09 14:04:02.053 	The Program 'bnk1uac' is not called by any other program.
2025-04-09 14:04:02.077 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/updacc.cbl'.
2025-04-09 14:04:02.100 	The Program 'updacc' is not called by any other program.
2025-04-09 14:04:02.123 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/consttst.cbl'.
2025-04-09 14:04:02.160 	The Program 'consttst' is not called by any other program.
2025-04-09 14:04:02.185 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/inqacc.cbl'.
2025-04-09 14:04:02.206 	The Program 'inqacc' is not called by any other program.
2025-04-09 14:04:02.230 ** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/updcust.cbl'.
2025-04-09 14:04:02.245 	The Program 'updcust' is not called by any other program.
[INFO] /usr/lpp/dbb/v3r0/bin/groovyz /u/ibmuser/dbb-git-migration-modeler-work/src/groovy/scanApplication.groovy 				--configFile /u/ibmuser/dbb-git-migration-modeler-work/DBB_GIT_MIGRATION_MODELER.config 				--application CBSA 				--logFile /u/ibmuser/dbb-git-migration-modeler-work/logs/3-CBSA-rescan.log
2025-04-09 14:04:40.138 ** Script configuration:
2025-04-09 14:04:40.175 	PIPELINE_USER -> ADO
2025-04-09 14:04:40.178 	application -> CBSA
2025-04-09 14:04:40.182 	configurationFilePath -> /u/ibmuser/dbb-git-migration-modeler-work/DBB_GIT_MIGRATION_MODELER.config
2025-04-09 14:04:40.185 	DBB_MODELER_APPLICATION_DIR -> /u/ibmuser/dbb-git-migration-modeler-work/repositories
2025-04-09 14:04:40.187 	logFile -> /u/ibmuser/dbb-git-migration-modeler-work/logs/3-CBSA-rescan.log
2025-04-09 14:04:40.190 	DBB_MODELER_METADATASTORE_TYPE -> db2
2025-04-09 14:04:40.193 	DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE -> /u/ibmuser/dbb-git-migration-modeler-work/db2Connection.conf
2025-04-09 14:04:40.196 	DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORD -> 
2025-04-09 14:04:40.198 	DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE -> /u/ibmuser/dbb-git-migration-modeler-work/MDALBIN-password.txt
2025-04-09 14:04:40.201 	DBB_MODELER_DB2_METADATASTORE_JDBC_ID -> MDALBIN
2025-04-09 14:04:40.204 	APPLICATION_DEFAULT_BRANCH -> main
2025-04-09 14:04:40.660 ** Scanning the files.
2025-04-09 14:04:40.819 	Scanning file CBSA/CBSA/application-conf/DBDgen.properties 
2025-04-09 14:04:40.922 	Scanning file CBSA/CBSA/src/cobol/getscode.cbl 
2025-04-09 14:04:40.949 	Scanning file CBSA/CBSA/src/cobol/bnk1cca.cbl 
2025-04-09 14:04:41.106 	Scanning file CBSA/CBSA/src/copy/contdb2.cpy 
2025-04-09 14:04:41.116 	Scanning file CBSA/CBSA/src/cobol/updcust.cbl 
2025-04-09 14:04:41.156 	Scanning file CBSA/CBSA/src/cobol/bnk1cac.cbl 
2025-04-09 14:04:41.230 	Scanning file CBSA/CBSA/src/copy/bnk1dcm.cpy 
2025-04-09 14:04:41.328 	Scanning file CBSA/CBSA/src/cobol/xfrfun.cbl 
2025-04-09 14:04:41.437 	Scanning file CBSA/CBSA/src/copy/consent.cpy 
2025-04-09 14:04:41.452 	Scanning file CBSA/CBSA/src/cobol/bnk1ccs.cbl 
2025-04-09 14:04:41.528 	Scanning file CBSA/CBSA/src/copy/sortcode.cpy 
2025-04-09 14:04:41.532 	Scanning file CBSA/CBSA/application-conf/file.properties 
2025-04-09 14:04:41.575 	Scanning file CBSA/CBSA/src/copy/custctrl.cpy 
2025-04-09 14:04:41.584 	Scanning file CBSA/CBSA/application-conf/PLI.properties 
2025-04-09 14:04:41.599 	Scanning file CBSA/CBSA/src/cobol/crdtagy1.cbl 
2025-04-09 14:04:41.623 	Scanning file CBSA/CBSA/src/cobol/bankdata.cbl 
2025-04-09 14:04:41.710 	Scanning file CBSA/CBSA/src/cobol/crecust.cbl 
2025-04-09 14:04:41.789 	Scanning file CBSA/CBSA/application-conf/TazUnitTest.properties 
2025-04-09 14:04:41.802 	Scanning file CBSA/CBSA.yaml 
2025-04-09 14:04:41.839 	Scanning file CBSA/CBSA/src/copy/delacc.cpy 
2025-04-09 14:04:41.853 	Scanning file CBSA/CBSA/src/cobol/dpayapi.cbl 
2025-04-09 14:04:41.881 	Scanning file CBSA/CBSA/src/copy/constapi.cpy 
2025-04-09 14:04:41.892 	Scanning file CBSA/applicationDescriptor.yml 
2025-04-09 14:04:41.927 	Scanning file CBSA/CBSA/src/copy/bnk1cam.cpy 
2025-04-09 14:04:41.999 	Scanning file CBSA/CBSA/src/cobol/consttst.cbl 
2025-04-09 14:04:42.016 	Scanning file CBSA/CBSA/src/cobol/crdtagy3.cbl 
2025-04-09 14:04:42.032 	Scanning file CBSA/CBSA/src/cobol/delcus.cbl 
2025-04-09 14:04:42.064 	Scanning file CBSA/CBSA/application-conf/Assembler.properties 
2025-04-09 14:04:42.082 	Scanning file CBSA/CBSA/src/cobol/accoffl.cbl 
2025-04-09 14:04:42.098 	Scanning file CBSA/CBSA/src/copy/updacc.cpy 
2025-04-09 14:04:42.107 	Scanning file CBSA/.gitattributes 
2025-04-09 14:04:42.119 	Scanning file CBSA/CBSA/src/copy/datastr.cpy 
2025-04-09 14:04:42.124 	Scanning file CBSA/CBSA/application-conf/application.properties 
2025-04-09 14:04:42.154 	Scanning file CBSA/CBSA/src/cobol/crdtagy4.cbl 
2025-04-09 14:04:42.167 	Scanning file CBSA/CBSA/src/cobol/accload.cbl 
2025-04-09 14:04:42.180 	Scanning file CBSA/CBSA/application-conf/Transfer.properties 
2025-04-09 14:04:42.183 	Scanning file CBSA/tagging/createReleaseCandidate.yml 
2025-04-09 14:04:42.256 	Scanning file CBSA/CBSA/src/copy/bnk1ccm.cpy 
2025-04-09 14:04:42.301 	Scanning file CBSA/CBSA/application-conf/Cobol.properties 
2025-04-09 14:04:42.317 	Scanning file CBSA/deployment/deployReleasePackage.yml 
2025-04-09 14:04:42.336 	Scanning file CBSA/CBSA/application-conf/CRB.properties 
2025-04-09 14:04:42.339 	Scanning file CBSA/CBSA/application-conf/bind.properties 
2025-04-09 14:04:42.347 	Scanning file CBSA/CBSA/src/cobol/inqacc.cbl 
2025-04-09 14:04:42.368 	Scanning file CBSA/CBSA/src/cobol/bnk1dac.cbl 
2025-04-09 14:04:42.390 	Scanning file CBSA/CBSA/src/copy/customer.cpy 
2025-04-09 14:04:42.399 	Scanning file CBSA/CBSA/src/copy/crecust.cpy 
2025-04-09 14:04:42.405 	Scanning file CBSA/CBSA/src/copy/creacc.cpy 
2025-04-09 14:04:42.414 	Scanning file CBSA/CBSA/application-conf/languageConfigurationMapping.properties 
2025-04-09 14:04:42.419 	Scanning file CBSA/CBSA/application-conf/LinkEdit.properties 
2025-04-09 14:04:42.431 	Scanning file CBSA/CBSA/src/cobol/dbcrfun.cbl 
2025-04-09 14:04:42.453 	Scanning file CBSA/CBSA/src/copy/bnk1acc.cpy 
2025-04-09 14:04:42.466 	Scanning file CBSA/CBSA/src/copy/bnk1uam.cpy 
2025-04-09 14:04:42.510 	Scanning file CBSA/CBSA/src/cobol/abndproc.cbl 
2025-04-09 14:04:42.519 	Scanning file CBSA/CBSA/src/cobol/acctctrl.cbl 
2025-04-09 14:04:42.525 	Scanning file CBSA/CBSA/src/copy/procdb2.cpy 
2025-04-09 14:04:42.530 	Scanning file CBSA/CBSA/application-conf/ACBgen.properties 
2025-04-09 14:04:42.533 	Scanning file CBSA/tagging/createProductionReleaseTag.yml 
2025-04-09 14:04:42.540 	Scanning file CBSA/CBSA/application-conf/MFS.properties 
2025-04-09 14:04:42.546 	Scanning file CBSA/CBSA/application-conf/reports.properties 
2025-04-09 14:04:42.557 	Scanning file CBSA/CBSA/src/copy/abndinfo.cpy 
2025-04-09 14:04:42.562 	Scanning file CBSA/CBSA/src/copy/xfrfun.cpy 
2025-04-09 14:04:42.566 	Scanning file CBSA/CBSA/application-conf/PSBgen.properties 
2025-04-09 14:04:42.574 	Scanning file CBSA/CBSA/src/cobol/inqcust.cbl 
2025-04-09 14:04:42.592 	Scanning file CBSA/CBSA/application-conf/Easytrieve.properties 
2025-04-09 14:04:42.600 	Scanning file CBSA/CBSA/src/copy/constdb2.cpy 
2025-04-09 14:04:42.606 	Scanning file CBSA/CBSA/src/copy/getcompy.cpy 
2025-04-09 14:04:42.608 	Scanning file CBSA/CBSA/src/cobol/consent.cbl 
2025-04-09 14:04:42.617 	Scanning file CBSA/CBSA/src/cobol/crdtagy2.cbl 
2025-04-09 14:04:42.626 	Scanning file CBSA/CBSA/src/cobol/delacc.cbl 
2025-04-09 14:04:42.639 	Scanning file CBSA/CBSA/application-conf/REXX.properties 
2025-04-09 14:04:42.647 	Scanning file CBSA/zapp.yaml 
2025-04-09 14:04:42.651 	Scanning file CBSA/CBSA/src/copy/inqacccu.cpy 
2025-04-09 14:04:42.658 	Scanning file CBSA/CBSA/src/cobol/bnk1tfn.cbl 
2025-04-09 14:04:42.675 	Scanning file CBSA/CBSA/src/cobol/proload.cbl 
2025-04-09 14:04:42.683 	Scanning file CBSA/CBSA/src/cobol/inqacccu.cbl 
2025-04-09 14:04:42.696 	Scanning file CBSA/CBSA/src/copy/bnk1cdm.cpy 
2025-04-09 14:04:42.712 	Scanning file CBSA/CBSA/src/cobol/dpaytst.cbl 
2025-04-09 14:04:42.717 	Scanning file CBSA/CBSA/src/cobol/bnk1cra.cbl 
2025-04-09 14:04:42.734 	Scanning file CBSA/CBSA/src/cobol/prooffl.cbl 
2025-04-09 14:04:42.742 	Scanning file CBSA/CBSA/src/cobol/updacc.cbl 
2025-04-09 14:04:42.752 	Scanning file CBSA/CBSA/src/copy/acctctrl.cpy 
2025-04-09 14:04:42.757 	Scanning file CBSA/CBSA/src/copy/delcus.cpy 
2025-04-09 14:04:42.761 	Scanning file CBSA/CBSA/src/copy/proctran.cpy 
2025-04-09 14:04:42.779 	Scanning file CBSA/CBSA/src/copy/updcust.cpy 
2025-04-09 14:04:42.783 	Scanning file CBSA/CBSA/src/copy/getscode.cpy 
2025-04-09 14:04:42.785 	Scanning file CBSA/CBSA/src/cobol/creacc.cbl 
2025-04-09 14:04:42.803 	Scanning file CBSA/CBSA/src/cobol/crdtagy5.cbl 
2025-04-09 14:04:42.810 	Scanning file CBSA/CBSA/src/copy/account.cpy 
2025-04-09 14:04:42.816 	Scanning file CBSA/CBSA/src/copy/bnk1dam.cpy 
2025-04-09 14:04:42.839 	Scanning file CBSA/CBSA/src/copy/paydbcr.cpy 
2025-04-09 14:04:42.842 	Scanning file CBSA/CBSA/src/cobol/getcompy.cbl 
2025-04-09 14:04:42.845 	Scanning file CBSA/CBSA/src/cobol/custctrl.cbl 
2025-04-09 14:04:42.849 	Scanning file CBSA/CBSA/src/copy/accdb2.cpy 
2025-04-09 14:04:42.853 	Scanning file CBSA/CBSA/application-conf/BMS.properties 
2025-04-09 14:04:42.857 	Scanning file CBSA/CBSA/src/copy/inqacc.cpy 
2025-04-09 14:04:42.862 	Scanning file CBSA/CBSA/src/copy/bnk1mai.cpy 
2025-04-09 14:04:42.867 	Scanning file CBSA/CBSA/src/cobol/bnk1dcs.cbl 
2025-04-09 14:04:42.883 	Scanning file CBSA/azure-pipelines.yml 
2025-04-09 14:04:42.895 	Scanning file CBSA/CBSA/src/cobol/bnk1uac.cbl 
2025-04-09 14:04:42.908 	Scanning file CBSA/CBSA/src/cobol/bnkmenu.cbl 
2025-04-09 14:04:42.920 	Scanning file CBSA/CBSA/application-conf/README.md 
2025-04-09 14:04:42.974 	Scanning file CBSA/CBSA/src/copy/inqcust.cpy 
2025-04-09 14:04:42.976 	Scanning file CBSA/CBSA/src/copy/bnk1tfm.cpy 
2025-04-09 14:04:42.987 ** Storing results in the 'CBSA-main' DBB Collection.
2025-04-09 14:04:44.955 ** Setting collection owner to ADO
~~~~

</details>