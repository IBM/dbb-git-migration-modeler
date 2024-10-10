# The DBB Git Migration Modeler utility

## Description

This asset provides a guided approach to plan and migrate source codebase from MVS datasets to z/OS UNIX System Services (USS) folders, and helps to identify and document the boundaries of mainframe applications.

The main capabilities of the utility are:
* the identification of applications based on naming onventions applied to datasets members
* the copy of source code files contained in datasets members to files on USS following a standard internal repository layout,
* the usage assessment of include files and submodules across all application to understand application-level dependencies, documented in the **Application Descriptor** file,
* the generation of build properties based on existing types and configuration in the legacy SCM solution
* the initialization of Git repositories, pipeline templates and other configuration files related to the applications

The different phases of the migration workflow are described in [the Migration Storyboard section](./AdvandedInformation.md#migration-storyboard).

## How does it work

The DBB Git Migration Modeler comes with 3 main scripts:

* The [Setup script](./Setup.sh) is used to define parameters that will be used throughout the migration process with the DBB Git Migration Modeler. It must be executed first, to set the defined parameters and create a configuration file that will be used in subsequent steps.
* The [Migration-Modeler-Start script](./src/scripts/Migration-Modeler-Start.sh) runs the different phases of the migration process
* The [Refresh-Application-Descriptor-Files script](./src/scripts/Refresh-Application-Descriptor-Files.sh) can be used to refresh Application Descriptor files for applications that were already migrated to Git.

## Required configuration

The DBB Git Migration Modeler is using two types of configuration information:
* Environment variables that are set up during the Setup phase
* Configuration files, shipped in the [samples directory](./samples/) that must to tailored to meet requirements

The different Configuration files are described in [this section](./AdvancedInformation.ms#terminology-and-description-of-configuration-files).


## Cross-application dependencies

The benefit of the 

### Refreshing Application Descriptor files

For applications that are already migrated to Git, the DBB Git Migration Modeler provides a feature to generate Application Descriptor files. More information can be found in the [Refresh Application Descriptor files](#refresh-application-descriptor-files) section.

## Terminology and description of configuration files

###  Input configuration files

The following list details the content of these configuration files:
1. [Applications Mapping file](./samples/applicationsMapping.yaml) (YAML format): this file describes the content of each application.  
It can be created manually or can be fueled with information coming from external databases or provided by an SCM tool.  
For each application, naming conventions can be used to filter elements that belong to that application.
Each naming convention defines a pattern that is used to analyze the list of files in a list of provided libraries.  
If the pattern matches, the member is assigned to the owning application.
If no pattern of any application is matching the member name, it is assigned to a common application called *UNASSIGNED*.
2. [Repository Paths Mapping file](./samples/repositoryPathsMapping.yaml) (YAML format): This file describes the folder structure on z/OS UNIX System Services (USS) that will contain the files to are moved from the datasets.  
It is created manually, from the provided template.  
Each *Repository Path* entry described in this file documents the type of artifacts in this folder, their file extension, their encoding, the source group they belong to, the language processor (for instance, the language script in dbb-zAppBuild) and criteria to meet for classification.  
The *mvsMapping* attribute of the *Repository Path* entry defines the criteria of origin: it either is the last-level qualifier of the dataset in which the member is located, the type (see the next configuration file for details) that is defined for these artifacts, or, if enabled, the language and file type as returned by the DBB Scanner.
3. [Types file](./samples/types.txt) (CSV format): this optional file is a listing of the known members of all datasets and their associated type of the legacy SCM.  
Lines of this file are composed of the artifact's names, followed by a list of comma-separated types. A combination of types can be specified, which will then turn into a composite type definition in dbb-zAppBuild.  
This information should be extracted from the legacy SCM tool using its provided utilities. For a given member described in this file, its type can be used as a criteria to define the appropriate target *Repository Path* when analyzing the provided datasets.
Types mapping are meant to be used only for programs, not for Includes Files.
4. [Types Configurations file](./samples/typesConfigurations.yaml) (YAML format): this file defines the build configurations with their *dbb-zAppBuild* build properties and values.
This information is typically extracted from the legacy SCM tool and mapped to the equivalent build property in *dbb-zAppBuild*. It is recommended to use ad-hoc automation, when applicable, to facilitate the creation of this file.  
Each Type Configuration defines properties that are used by the [dbb-zAppBuild](https://github.com/IBM/dbb-zappbuild/) framework.
Types can be combined depending on definitions found in the [Types file](./samples/types.txt), to generate composite types combining different properties.

### Output files

When running this utility, two main types of files will be created for each application that are discovered:
* An Application Descriptor file (YAML format): this file is built during the analysis of the datasets members provided as input. It contains the list of artifacts that belong to this application, with properties that are updated when the usage of Include Files and Programs is performed. 
  * [The Framing phase](#the-framing-phase) stores the files in a shared configuration folder (named *work-configs* by default).
  * [The Assessment phase](#the-assessment-phase) produces an updated Application Descriptor file, which is stored within the application's folder.
  This allows to compare the Application Descriptor files between the Framing phase and the Assessment phase.
* A DBB Migration Mapping file (Text format): this file contains instructions on how the DBB Migration utility should operate when running a migration.
This structure of mapping file and how to invoke the DBB Migration utility with a mapping file is described in [the official DBB documentation](https://www.ibm.com/docs/en/dbb/2.0?topic=migrating-source-files-from-zos-git#running-migration-using-a-mapping-file).

For [the Property Generation phase](#the-property-generation-phase), the following output files are created:
* Language Configuration files, containing properties defined for types configurations (as defined in the [Types Configurations file](./samples/typesConfigurations.yaml)).
These Language Configuration files are stored in a custom *dbb-zAppBuild* instance which is copied from an original *dbb-zAppbuild* folder.
The location of these files is the [build-conf/language-conf](https://github.com/IBM/dbb-zappbuild/tree/develop/build-conf/language-conf) folder in the custom *dbb-zAppBuild* instance.
* For each analyzed application, an [application-conf](https://github.com/IBM/dbb-zappbuild/tree/develop/samples/application-conf) folder is copied from the original *dbb-zAppBuild* instance, in which two files are customized:
  * A line is added in [file.properties](https://github.com/IBM/dbb-zappbuild/blob/develop/samples/application-conf/file.properties) to enable the use of Language Configuration mappings.
  This line is commented by default, and users are asked to uncomment to enable this capability.
  * For each artifact of the application, an entry is added in [languageConfigurationMapping.properties](https://github.com/IBM/dbb-zappbuild/blob/develop/samples/application-conf/languageConfigurationMapping.properties), which maps the artifact with its Language Configuration defined in [build-conf/language-conf](https://github.com/IBM/dbb-zappbuild/tree/develop/build-conf/language-conf)


## Configuring the DBB Git Migration Modeler utility

### Installation

Install the DBB Git Migration Modeler by cloning the repository to z/OS Unix System Services.

### Setup DBB Git Migration Modeler configuration

Once installed on z/OS Unix System Services, the [Setup.sh](./Setup.sh) script must be run to configure the DBB Git Migration Modeler, and set configuration parameters.
This script prompts for the below environment variables and saves them in a configuration file, that is used as an input for the different DBB Git Migration Modeler scripts. 

| Configuration Parameter | Description | Default Value |
| --- | --- | --- |
| DBB_MODELER_HOME  | The home of the DBB Git Migration Modeler project | The current directory of Setup.sh | 
| DBB_MODELER_WORK  | The working directory for the DBB Git Migration Modeler. Requires to be sized to store the entire copy of all application programs. | `$DBB_MODELER_HOME-work`| 
| DBB_MODELER_APPCONFIG_DIR  | Stores the initial version of the Application Descriptor and the generated DBB Migration Mapping files | `$DBB_MODELER_WORK/work-configs`| 
| DBB_MODELER_APPLICATION_DIR  | Path where the DBB Git Migration Modeler will create the application directories | `$DBB_MODELER_WORK/work-applications` | 
| DBB_MODELER_LOGS  | Path where the DBB Git Migration Modeler will store the log files of the various steps of Migration Modeler process | `$DBB_MODELER_WORK/work-logs`| 
| DBB_MODELER_METADATA_STORE_DIR  | Path to create a DBB File Metadatastore. Required for the Assessment phase | `$DBB_MODELER_WORK/work-metadatastore` | 
| APPLICATION_DATASETS  | The list of input datasets that will be analyzed by the DBB Git Migration Modeler. These datasets need to hold a copy of the code base of your production system | `DBEHM.MIG.COBOL,DBEHM.MIG.COPY,DBEHM.MIG.BMS` | 
| **DBB Git Migration Modeler Input files** | | | 
| APPLICATION_MAPPING_FILE  | Application Mapping file containing the existing applications and their naming conventions, elements lists. See tailoring of input files. | `$DBB_MODELER_WORK/applicationsMapping.yaml` | 
| REPOSITORY_PATH_MAPPING_FILE  | Repository Paths Mapping file map the various types of members to the folder layout in Git. See tailoring of input files. | `$DBB_MODELER_WORK/repositoryPathsMapping.yaml` | 
| APPLICATION_MEMBER_TYPE_MAPPING  | Member to Type mapping | `$DBB_MODELER_WORK/types.txt` | 
| SCAN_DATASET_MEMBERS | Flag to determine if application extraction process should scan each member to identify source type. | `false` |
| SCAN_DATASET_MEMBERS_ENCODING | PDS encoding for scanner when determining the source type | `IBM-1047` |
| TYPE_CONFIGURATIONS_FILE | Type Configuration to generate zAppBuild Language Configurations to statically preserve existing build configuration | `$DBB_MODELER_WORK/typesConfigurations.yaml` |
| DEFAULT_GIT_CONFIG  | Folder containing a default .gitattributes and .gitignore files to initialize a Git repo for the Application repositories | `$DBB_MODELER_WORK/git-config` |
| DBB_ZAPPBUILD | Path to your customized [zAppBuild repository](https://github.com/IBM/dbb-zappbuild) on z/OS Unix System Services for baseline builds | `/var/dbb/dbb-zappbuild` |
| DBB_COMMUNITY_REPO | Path to your customized [DBB community repository](https://github.com/IBM/dbb) on z/OS Unix System Services | `/var/dbb/dbb` |


### Tailor the input files

The configuration files required to use the DBB Git Migration Modeler utility are copied by the [Setup.sh](./Setup.sh) script from the [samples](./samples/) folder to the **work** folder that was specified during setup process.

Four configuration files need to be reviewed and adapted to your installation and your needs, before using the DBB Git Migration Modeler: 

* The [Applications Mapping file](./samples/applicationsMapping.yaml) file contains the list of existing applications including the naming convention patterns to define the elements that belong to each application.
Instead of patterns for naming conventions, the file also accepts fully qualified member names that can be extracted from an existing data source or report provided by your legacy tool.  
Members in the input PDSs libraries that do not match any convention will be associated to the *UNASSIGNED* application and be treated as shared code.  
If no naming convention is applied for a given application, or if all the members of a given dataset belong to the same application, a naming convention whose value is `........` should be defined.

* The [Repository Paths Mapping file](./samples/repositoryPathsMapping.yaml) file is required and may be tailored to meet with your requirements, in terms of folder layout.
However, it is recommended to use the definitions provided in the template, and keep consistent definitions for all applications being migrated.  
The values provided in the sample file should meet most of the implementations, but these values can be customized if necessary.  
The file controls how dataset members should be dispatched to target subfolders on USS, depending on the low-level qualifiers of the dataset which hold them, their associated types (if any, as described in the [Types file](./samples/types.txt)) or, if enabled, the scan result provided by the DBB Scanner.  
For each repository path, the `artifactsType` property is used during [the Assessment phase](#the-assessment-phase), to filter out for each type of artifacts to perform the assessment.
Only artifacts of types `Program` or `Include File` will be included in the analysis.
It is recommended to keep the current settings defined in the provided [Repository Paths Mapping file](./samples/repositoryPathsMapping.yaml) for the `artifactsType` property.    

* The [Types file](./samples/types.txt) lists their dataset members and their associated type (like a language definition), as described in the legacy SCM tool. This CSV file is optional, and should be built with an SCM-provided utility or from an SCM-provided report.  
During the [Framing phase](#the-framing-phase), the *type* information can be used as a criteria to dispatch files.
If no type is assigned to a given artifact, this information will not be used to dispatch the file and this element will be of type *UNKNOWN* in the Application Descriptor file.  
The type assigned to each artifact is used in the [Property Generation phase](#the-property-generation-phase) to create Language Configuration in [dbb-zAppBuild](https://github.com/IBM/dbb-zappbuild/)'s configuration.

* The [Types Configurations file](./samples/typesConfigurations.yaml) defines the differents types, grouping together related properties.
This file is only used during the [Property Generation phase](#the-property-generation-phase).
Each type configuration contains properties used by the [dbb-zAppBuild](https://github.com/IBM/dbb-zappbuild/) framework.


### Required input libraries of codebase

The utility is operating on a set of provided PDS libraries that contain a copy of the recent codebase of the legacy SCM repository. These datasets should be extracted from the legacy SCM system, using a SCM-provided utility or mechanism.

In the sample walkthrough below, all COBOL programs files of all applications are stored in a library called `COBOL`. COBOL Include files are stored in the `COPYBOOK` library.
 
## Working with the DBB Git Migration Modeler utility

The DBB Git Migration Modeler utility is a set of shell scripts that are wrapping groovy scripts. The scripts are using DBB APIs and groovy APIs.

There are 3 primary command scripts located at `/src/scripts` :

* [Migration-Modeler-Start script](./src/scripts/Migration-Modeler-Start.sh) facilitates the extraction, migration, classification, and generating build configuration.
* [Init-Application-Repositories script](./src/scripts/Init-Application-Repositories.sh) initializes the application repositories with a default gitattributes file that can then be pushed to the preferred Git provider, performs a dbb-zappbuild full build with preview option, and creates a baseline package.
* [Refresh-Application-Descriptor-Files script](./src/scripts/Refresh-Application-Descriptor-Files.sh) to re-create Application Descriptors for existing applications that are already managed in git.

The below sections explain the different primary command scripts.

### Migration Modeler Start

To facilitate the extraction, migration, classification, and generation of build configuration, a sample script, called the [Migration-Modeler-Start](./src/scripts/Migration-Modeler-Start.sh) script, is provided to guide the user through the multiple steps of the process.

The [Migration-Modeler-Start](./src/scripts/Migration-Modeler-Start.sh) script is invoked with the path to the DBB Git Migration Modeler configuration file passed as a parameter.
The DBB Git Migration Modeler configuration file contains the input parameters to the process. 

The [Migration-Modeler-Start](./src/scripts/Migration-Modeler-Start.sh) script go through the following stages, that are represented by specific scripts under the covers.
All scripts require to pass in the path to the DBB Git Migration Modeler configuration file via the `-c` parameter.

1. [Extract Applications script (1-extractApplication.sh)](./src/scripts/utils/1-extractApplications.sh): this script scans the content of the provided datasets and assesses each member based on the provided input files.
For each member found, it searches in the `Applications Mapping` YAML file if a naming convention, after being applied as a filter, matches the member name:
   * If it's a match, the member is assigned to the application that owns the matching naming convention.
   * If no pattern is found matching, the member is assigned to the *UNASSIGNED* application.
   * **Outputs**: After the execution of this script, a *work-configs* directory is created with 2 files for each application found.
      * An initial Application Descriptor file.
      * DBB Migration mapping file depending on the definitions in the *Repository Paths* mapping file.

2. [Run Migrations script (2-runMigrations.sh)](./src/scripts/utils/2-runMigrations.sh): this script executes the DBB Migration utility for each application with the generated DBB Migration Mapping files created by the Extract Applications script. 
It will copy all the files assigned to the given applications subfolders. Unassigned members are migrated into an *UNASSIGNED* application.
The outcome of this script are subfolders created in the *work-applications* folder for each application. A side outcome of this step is the documentation about non-roundtripable and non-printable characters for each application. 

3. [Classification script (3-classify.sh)](./src/scripts/utils/3-classify.sh): this script facilitates the scanning of the source code and the classification process. It calls two groovy scripts ([scanApplication.groovy](./src/groovy/scanApplication.groovy) and [assessUsage.groovy](./src/groovy/assessUsage.groovy)) to respectively scans the content of each files of the applications using the DBB scanner, and assess how Include Files and Programs are used by all the applications.
   * For the scanning phase, the script iterates through the list of identified applications, and uses the DBB scanner to understand the dependencies for each artifacts.
   This information is stored in a local, temporary DBB metadatastore on the USS filesystem, that holds the dependencies information.

   * The second phase of the process uses this metadata information to understand how Include Files and Programs are used across all applications and classify the Include Files in three categories (Private, Public or Shared) and Programs in three categories ("main", "internal submodule", "service submodule").
   Depending on the results of this assessment, Include Files may be moved from one application to another, Programs are not subject to move.

   * **Outputs**
      * The initial Application Descriptor file for each application is stored in the application's subfolder located in the *work-applications* folder (if not already present) and is updated to reflect the findings of this step.
      As it contains additional details, we refer to is as the final Application Descriptor.
      * The DBB Migration mapping file is also updated accordingly, if files were moved from an owning application to another. 

4. [Property Generation script (4-generateProperties.sh)](./src/scripts/utils/4-generateProperties.sh): this script generates build properties for [dbb-zAppBuild](https://github.com/IBM/dbb-zappbuild/).
The script uses the type of each artifact to generate (or reuse if already existing) Language Configurations defined in *dbb-zAppBuild*, as configured in the [Types Configurations file](./samples/typesConfigurations.yaml).  
The outcome is property files defined for each application's *application-conf* folder and Language Configuration files defined in a custom *dbb-zAppBuild* folder. This step is optional. 

#### Extracting members from datasets into applications

The [Extract Applications script (1-extractApplication.sh)](./src/scripts/utils/1-extractApplications.sh) requires the path to the DBB Git Migration Modeler configuration file:

Optional configurations for the script:

*  The *types* (`APPLICATION_MEMBER_TYPE_MAPPING`) and the *Types Configuration* (`TYPE_CONFIGURATIONS_FILE`) files can be specified to generate correct build properties.
Typically, these information can be extracted from the SCM solution using reports, or can be manually tailored if necessary.

*  The use of the DBB Scanner (controlled via `SCAN_DATASET_MEMBERS` property) to automatically identify the language and type of a file (Cobol, PLI, etc.), is disabled by default, and must be enabled through DBB Git Migration Modeler configuration file.
When enabled, each file is scanned to identify its language and file type, and these criteria are used first when identifying which *repository path* the file should be assigned to.
When disabled, types and low-level qualifiers of the containing dataset are used, in this order.


<details>
  <summary>Output example</summary>
Execution of the command:

```
./src/scripts/utils/1-extractApplications.sh -c /u/mdalbin/Migration-Modeler-DBEHM-work/DBB_GIT_MIGRATION_MODELER.config
```

Output log:
~~~~  
** Extraction process started.
** Script configuration:
   datasetsList -> DBEHM.MIG.COBOL,DBEHM.MIG.COPY,DBEHM.MIG.BMS
   outputApplicationDirectory -> /u/mdalbin/Migration-Modeler-DBEHM-work/applications
   applicationsMappingFilePath -> /u/mdalbin/Migration-Modeler-DBEHM-work/applicationsMapping.yaml
   typesFilePath -> /u/mdalbin/Migration-Modeler-DBEHM-work/types.txt
   logFile -> /u/mdalbin/Migration-Modeler-DBEHM-work/logs/1-extractApplications.log
   scanDatasetMembers -> false
   repositoryPathsMappingFilePath -> /u/mdalbin/Migration-Modeler-DBEHM-work/repositoryPathsMapping.yaml
   outputConfigurationDirectory -> /u/mdalbin/Migration-Modeler-DBEHM-work/modeler-configs
** Reading the Repository Layout Mapping definition.
** Reading the Application Mapping definition.
** Reading the Type Mapping definition.
** Iterating through the provided datasets.
*** Found DBEHM.MIG.COBOL
**** 'DBEHM.MIG.COBOL(ABNDPROC)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(ACCLOAD)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(ACCOFFL)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(ACCTCTRL)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(BANKDATA)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(BNKMENU)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(BNK1CAC)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(BNK1CCA)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(BNK1CCS)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(BNK1CRA)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(BNK1DAC)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(BNK1DCS)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(BNK1TFN)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(BNK1UAC)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(CONSENT)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(CONSTTST)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(CRDTAGY1)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(CRDTAGY2)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(CRDTAGY3)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(CRDTAGY4)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(CRDTAGY5)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(CREACC)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(CRECUST)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(CUSTCTRL)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(DBCRFUN)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(DELACC)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(DELCUS)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(DPAYAPI)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(DPAYTST)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(EBUD0RUN)' - Mapped Application: RetirementCalculator
**** 'DBEHM.MIG.COBOL(EBUD01)' - Mapped Application: RetirementCalculator
**** 'DBEHM.MIG.COBOL(EBUD02)' - Mapped Application: RetirementCalculator
**** 'DBEHM.MIG.COBOL(EBUD03)' - Mapped Application: RetirementCalculator
**** 'DBEHM.MIG.COBOL(FLEMSMAI)' - Mapped Application: UNASSIGNED
**** 'DBEHM.MIG.COBOL(FLEMSSUB)' - Mapped Application: UNASSIGNED
**** 'DBEHM.MIG.COBOL(GETCOMPY)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(GETSCODE)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(INQACC)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(INQACCCU)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(INQCUST)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(LGACDB01)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COBOL(LGACDB02)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COBOL(LGACUS01)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COBOL(LGACVS01)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COBOL(LGAPDB01)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COBOL(LGAPOL01)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COBOL(LGAPVS01)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COBOL(LGASTAT1)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COBOL(LGDPDB01)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COBOL(LGDPOL01)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COBOL(LGDPVS01)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COBOL(LGICDB01)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COBOL(LGICUS01)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COBOL(LGICVS01)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COBOL(LGIPDB01)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COBOL(LGIPOL01)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COBOL(LGIPVS01)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COBOL(LGSETUP)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COBOL(LGSTSQ)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COBOL(LGTESTC1)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COBOL(LGTESTP1)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COBOL(LGTESTP2)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COBOL(LGTESTP3)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COBOL(LGTESTP4)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COBOL(LGUCDB01)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COBOL(LGUCUS01)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COBOL(LGUCVS01)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COBOL(LGUPDB01)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COBOL(LGUPOL01)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COBOL(LGUPVS01)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COBOL(LGWEBST5)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COBOL(OLDACDB1)' - Mapped Application: UNASSIGNED
**** 'DBEHM.MIG.COBOL(OLDACDB2)' - Mapped Application: UNASSIGNED
**** 'DBEHM.MIG.COBOL(PROLOAD)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(PROOFFL)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(UPDACC)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(UPDCUST)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COBOL(XFRFUN)' - Mapped Application: CBSA
*** Found DBEHM.MIG.COPY
**** 'DBEHM.MIG.COPY(ABNDINFO)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COPY(ACCDB2)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COPY(ACCOUNT)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COPY(ACCTCTRL)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COPY(BNK1ACC)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COPY(BNK1CAM)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COPY(BNK1CCM)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COPY(BNK1CDM)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COPY(BNK1DAM)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COPY(BNK1DCM)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COPY(BNK1MAI)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COPY(BNK1TFM)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COPY(BNK1UAM)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COPY(CONSENT)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COPY(CONSTAPI)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COPY(CONSTDB2)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COPY(CONTDB2)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COPY(CREACC)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COPY(CRECUST)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COPY(CUSTCTRL)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COPY(CUSTOMER)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COPY(DATASTR)' - Mapped Application: UNASSIGNED
**** 'DBEHM.MIG.COPY(DELACC)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COPY(DELCUS)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COPY(GETCOMPY)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COPY(GETSCODE)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COPY(INQACC)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COPY(INQACCCU)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COPY(INQCUST)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COPY(LGCMAREA)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COPY(LGCMARED)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COPY(LGPOLICY)' - Mapped Application: GenApp
**** 'DBEHM.MIG.COPY(LINPUT)' - Mapped Application: RetirementCalculator
**** 'DBEHM.MIG.COPY(PAYDBCR)' - Mapped Application: UNASSIGNED
**** 'DBEHM.MIG.COPY(PROCDB2)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COPY(PROCTRAN)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COPY(SORTCODE)' - Mapped Application: UNASSIGNED
**** 'DBEHM.MIG.COPY(UPDACC)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COPY(UPDCUST)' - Mapped Application: CBSA
**** 'DBEHM.MIG.COPY(XFRFUN)' - Mapped Application: CBSA
*** Found DBEHM.MIG.BMS
**** 'DBEHM.MIG.BMS(EPSMLIS)' - Mapped Application: UNASSIGNED
**** 'DBEHM.MIG.BMS(EPSMORT)' - Mapped Application: UNASSIGNED
**** 'DBEHM.MIG.BMS(SSMAP)' - Mapped Application: GenApp
** Generating Applications Configurations files.
** Generating Configuration files for application UNASSIGNED.
        Created DBB Migration Utility mapping file /u/mdalbin/Migration-Modeler-DBEHM-work/modeler-configs/UNASSIGNED.mapping
        Created Application Description file /u/mdalbin/Migration-Modeler-DBEHM-work/modeler-configs/UNASSIGNED.yaml
** Generating Configuration files for application CBSA.
        Created DBB Migration Utility mapping file /u/mdalbin/Migration-Modeler-DBEHM-work/modeler-configs/CBSA.mapping
        Created Application Description file /u/mdalbin/Migration-Modeler-DBEHM-work/modeler-configs/CBSA.yaml
** Generating Configuration files for application GenApp.
        Created DBB Migration Utility mapping file /u/mdalbin/Migration-Modeler-DBEHM-work/modeler-configs/GenApp.mapping
        Created Application Description file /u/mdalbin/Migration-Modeler-DBEHM-work/modeler-configs/GenApp.yaml
** Generating Configuration files for application RetirementCalculator.
        Created DBB Migration Utility mapping file /u/mdalbin/Migration-Modeler-DBEHM-work/modeler-configs/RetirementCalculator.mapping
        Created Application Description file /u/mdalbin/Migration-Modeler-DBEHM-work/modeler-configs/RetirementCalculator.yaml
** Build finished
~~~~
</details>

#### Migrating the members from MVS datasets to USS folders

The [Run Migrations script (2-runMigrations.sh)](./src/scripts/2-runMigrations.sh) only requires the path to the DBB Git Migration Modeler Configuration file as parameter, to locate the work directories (controlled via `DBB_MODELER_APPLICATION_DIR`).
It will search for all the DBB Migration mapping files located in the *work-configs* directory, and will process them in sequence.

<details>
  <summary>Output example</summary>
Execution of the command:

`./src/scripts/utils/2-runMigrations.sh -c /u/mdalbin/Migration-Modeler-DBEHM-work/DBB_GIT_MIGRATION_MODELER.config`

Output log:  
~~~~
***** Running the DBB Migration Utility for application CBSA using file CBSA.mapping *****
Messages will be saved in /u/mdalbin/Migration-Modeler-DBEHM-work/logs/2-CBSA.migration.log
Non-printable scan level is info
Local GIT repository: /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA
Migrate data sets using mapping file /u/mdalbin/Migration-Modeler-DBEHM-work/modeler-configs/CBSA.mapping
Copying [DBEHM.MIG.COPY, CUSTCTRL] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/custctrl.cpy using IBM-1047
Copying [DBEHM.MIG.COPY, ACCTCTRL] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/acctctrl.cpy using IBM-1047
Copying [DBEHM.MIG.COBOL, DELACC] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/delacc.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, BNK1CAC] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/bnk1cac.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, GETCOMPY] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/getcompy.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, XFRFUN] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/xfrfun.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, CREACC] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/creacc.cbl using IBM-1047
Copying [DBEHM.MIG.COPY, BNK1CCM] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/bnk1ccm.cpy using IBM-1047
Copying [DBEHM.MIG.COBOL, UPDCUST] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/updcust.cbl using IBM-1047
Copying [DBEHM.MIG.COPY, ABNDINFO] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/abndinfo.cpy using IBM-1047
Copying [DBEHM.MIG.COPY, BNK1CAM] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/bnk1cam.cpy using IBM-1047
Copying [DBEHM.MIG.COBOL, UPDACC] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/updacc.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, BNK1UAC] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/bnk1uac.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, CRDTAGY3] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/crdtagy3.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, BNK1CCS] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/bnk1ccs.cbl using IBM-1047
Copying [DBEHM.MIG.COPY, PAYDBCR] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/paydbcr.cpy using IBM-1047
Copying [DBEHM.MIG.COBOL, CRDTAGY4] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/crdtagy4.cbl using IBM-1047
Copying [DBEHM.MIG.COPY, CONSTDB2] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/constdb2.cpy using IBM-1047
Copying [DBEHM.MIG.COPY, BNK1ACC] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/bnk1acc.cpy using IBM-1047
Copying [DBEHM.MIG.COBOL, INQACCCU] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/inqacccu.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, CONSENT] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/consent.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, PROLOAD] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/proload.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, ACCLOAD] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/accload.cbl using IBM-1047
Copying [DBEHM.MIG.COPY, UPDACC] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/updacc.cpy using IBM-1047
Copying [DBEHM.MIG.COPY, BNK1TFM] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/bnk1tfm.cpy using IBM-1047
Copying [DBEHM.MIG.COPY, INQACCCU] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/inqacccu.cpy using IBM-1047
Copying [DBEHM.MIG.COBOL, ABNDPROC] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/abndproc.cbl using IBM-1047
Copying [DBEHM.MIG.COPY, ACCOUNT] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/account.cpy using IBM-1047
Copying [DBEHM.MIG.COPY, CRECUST] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/crecust.cpy using IBM-1047
Copying [DBEHM.MIG.COPY, PROCTRAN] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/proctran.cpy using IBM-1047
Copying [DBEHM.MIG.COPY, DELCUS] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/delcus.cpy using IBM-1047
Copying [DBEHM.MIG.COBOL, BNK1CRA] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/bnk1cra.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, DPAYAPI] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/dpayapi.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, CRDTAGY5] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/crdtagy5.cbl using IBM-1047
Copying [DBEHM.MIG.COPY, GETCOMPY] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/getcompy.cpy using IBM-1047
Copying [DBEHM.MIG.COBOL, INQCUST] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/inqcust.cbl using IBM-1047
Copying [DBEHM.MIG.COPY, SORTCODE] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/sortcode.cpy using IBM-1047
Copying [DBEHM.MIG.COBOL, CRECUST] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/crecust.cbl using IBM-1047
Copying [DBEHM.MIG.COPY, BNK1MAI] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/bnk1mai.cpy using IBM-1047
Copying [DBEHM.MIG.COBOL, BNK1CCA] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/bnk1cca.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, DELCUS] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/delcus.cbl using IBM-1047
Copying [DBEHM.MIG.COPY, CONSENT] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/consent.cpy using IBM-1047
Copying [DBEHM.MIG.COBOL, INQACC] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/inqacc.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, BNKMENU] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/bnkmenu.cbl using IBM-1047
Copying [DBEHM.MIG.COPY, CREACC] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/creacc.cpy using IBM-1047
Copying [DBEHM.MIG.COBOL, DBCRFUN] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/dbcrfun.cbl using IBM-1047
Copying [DBEHM.MIG.COPY, BNK1CDM] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/bnk1cdm.cpy using IBM-1047
Copying [DBEHM.MIG.COPY, ACCDB2] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/accdb2.cpy using IBM-1047
Copying [DBEHM.MIG.COBOL, PROOFFL] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/prooffl.cbl using IBM-1047
Copying [DBEHM.MIG.COPY, GETSCODE] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/getscode.cpy using IBM-1047
Copying [DBEHM.MIG.COBOL, ACCTCTRL] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/acctctrl.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, ACCOFFL] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/accoffl.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, BNK1TFN] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/bnk1tfn.cbl using IBM-1047
Copying [DBEHM.MIG.COPY, UPDCUST] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/updcust.cpy using IBM-1047
Copying [DBEHM.MIG.COBOL, CRDTAGY1] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/crdtagy1.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, CUSTCTRL] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/custctrl.cbl using IBM-1047
Copying [DBEHM.MIG.COPY, BNK1UAM] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/bnk1uam.cpy using IBM-1047
Copying [DBEHM.MIG.COPY, INQCUST] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/inqcust.cpy using IBM-1047
Copying [DBEHM.MIG.COBOL, DPAYTST] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/dpaytst.cbl using IBM-1047
Copying [DBEHM.MIG.COPY, CONSTAPI] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/constapi.cpy using IBM-1047
Copying [DBEHM.MIG.COPY, DELACC] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/delacc.cpy using IBM-1047
Copying [DBEHM.MIG.COBOL, BNK1DAC] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/bnk1dac.cbl using IBM-1047
Copying [DBEHM.MIG.COPY, PROCDB2] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/procdb2.cpy using IBM-1047
Copying [DBEHM.MIG.COBOL, BANKDATA] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/bankdata.cbl using IBM-1047
Copying [DBEHM.MIG.COPY, BNK1DCM] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/bnk1dcm.cpy using IBM-1047
Copying [DBEHM.MIG.COPY, DATASTR] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/datastr.cpy using IBM-1047
Copying [DBEHM.MIG.COPY, BNK1DAM] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/bnk1dam.cpy using IBM-1047
Copying [DBEHM.MIG.COPY, CONTDB2] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/contdb2.cpy using IBM-1047
Copying [DBEHM.MIG.COBOL, GETSCODE] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/getscode.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, BNK1DCS] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/bnk1dcs.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, CRDTAGY2] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/crdtagy2.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, CONSTTST] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/cobol/consttst.cbl using IBM-1047
Copying [DBEHM.MIG.COPY, INQACC] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/inqacc.cpy using IBM-1047
Copying [DBEHM.MIG.COPY, CUSTOMER] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/customer.cpy using IBM-1047
Copying [DBEHM.MIG.COPY, XFRFUN] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/src/copy/xfrfun.cpy using IBM-1047
** Build finished
***** Running the DBB Migration Utility for application GenApp using file GenApp.mapping *****
Messages will be saved in /u/mdalbin/Migration-Modeler-DBEHM-work/logs/2-GenApp.migration.log
Non-printable scan level is info
Local GIT repository: /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp
Migrate data sets using mapping file /u/mdalbin/Migration-Modeler-DBEHM-work/modeler-configs/GenApp.mapping
Copying [DBEHM.MIG.COBOL, LGAPOL01] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/cobol/lgapol01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGTESTC1] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/cobol/lgtestc1.cbl using IBM-1047
Copying [DBEHM.MIG.BMS, SSMAP] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/bms/ssmap.bms using IBM-1047
Copying [DBEHM.MIG.COPY, LGCMARED] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/copy/lgcmared.cpy using IBM-1047
Copying [DBEHM.MIG.COBOL, LGACDB01] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/cobol/lgacdb01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGAPVS01] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/cobol/lgapvs01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGDPVS01] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/cobol/lgdpvs01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGTESTP3] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/cobol/lgtestp3.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGUCUS01] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/cobol/lgucus01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGASTAT1] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/cobol/lgastat1.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGICDB01] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/cobol/lgicdb01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGUPVS01] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/cobol/lgupvs01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGDPDB01] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/cobol/lgdpdb01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGUCVS01] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/cobol/lgucvs01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGIPDB01] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/cobol/lgipdb01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGTESTP2] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/cobol/lgtestp2.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGUPOL01] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/cobol/lgupol01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGSTSQ] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/cobol/lgstsq.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGACUS01] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/cobol/lgacus01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGSETUP] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/cobol/lgsetup.cbl using IBM-1047
Copying [DBEHM.MIG.COPY, LGCMAREA] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/copy/lgcmarea.cpy using IBM-1047
Copying [DBEHM.MIG.COBOL, LGACVS01] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/cobol/lgacvs01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGACDB02] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/cobol/lgacdb02.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGTESTP4] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/cobol/lgtestp4.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGICVS01] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/cobol/lgicvs01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGWEBST5] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/cobol/lgwebst5.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGAPDB01] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/cobol/lgapdb01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGDPOL01] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/cobol/lgdpol01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGIPVS01] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/cobol/lgipvs01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGUCDB01] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/cobol/lgucdb01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGTESTP1] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/cobol/lgtestp1.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGIPOL01] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/cobol/lgipol01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, LGICUS01] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/cobol/lgicus01.cbl using IBM-1047
Copying [DBEHM.MIG.COPY, LGPOLICY] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/copy/lgpolicy.cpy using IBM-1047
Copying [DBEHM.MIG.COBOL, LGUPDB01] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/src/cobol/lgupdb01.cbl using IBM-1047
** Build finished
***** Running the DBB Migration Utility for application RetirementCalculator using file RetirementCalculator.mapping *****
Messages will be saved in /u/mdalbin/Migration-Modeler-DBEHM-work/logs/2-RetirementCalculator.migration.log
Non-printable scan level is info
Local GIT repository: /u/mdalbin/Migration-Modeler-DBEHM-work/applications/RetirementCalculator
Migrate data sets using mapping file /u/mdalbin/Migration-Modeler-DBEHM-work/modeler-configs/RetirementCalculator.mapping
Copying [DBEHM.MIG.COPY, LINPUT] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/RetirementCalculator/RetirementCalculator/src/copy/linput.cpy using IBM-1047
Copying [DBEHM.MIG.COBOL, EBUD03] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/RetirementCalculator/RetirementCalculator/src/cobol/ebud03.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, EBUD02] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/RetirementCalculator/RetirementCalculator/src/cobol/ebud02.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, EBUD01] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/RetirementCalculator/RetirementCalculator/src/cobol/ebud01.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, EBUD0RUN] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/RetirementCalculator/RetirementCalculator/src/cobol/ebud0run.cbl using IBM-1047
** Build finished
***** Running the DBB Migration Utility for application UNASSIGNED using file UNASSIGNED.mapping *****
Messages will be saved in /u/mdalbin/Migration-Modeler-DBEHM-work/logs/2-UNASSIGNED.migration.log
Non-printable scan level is info
Local GIT repository: /u/mdalbin/Migration-Modeler-DBEHM-work/applications/UNASSIGNED
Migrate data sets using mapping file /u/mdalbin/Migration-Modeler-DBEHM-work/modeler-configs/UNASSIGNED.mapping
Copying [DBEHM.MIG.COPY, SORTCODE] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/UNASSIGNED/UNASSIGNED/src/copy/sortcode.cpy using IBM-1047
Copying [DBEHM.MIG.COPY, DATASTR] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/UNASSIGNED/UNASSIGNED/src/copy/datastr.cpy using IBM-1047
Copying [DBEHM.MIG.BMS, EPSMORT] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/UNASSIGNED/UNASSIGNED/src/bms/epsmort.bms using IBM-1047
Copying [DBEHM.MIG.COBOL, FLEMSMAI] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/UNASSIGNED/UNASSIGNED/src/cobol/flemsmai.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, OLDACDB2] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/UNASSIGNED/UNASSIGNED/src/cobol/oldacdb2.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, FLEMSSUB] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/UNASSIGNED/UNASSIGNED/src/cobol/flemssub.cbl using IBM-1047
Copying [DBEHM.MIG.COBOL, OLDACDB1] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/UNASSIGNED/UNASSIGNED/src/cobol/oldacdb1.cbl using IBM-1047
Copying [DBEHM.MIG.BMS, EPSMLIS] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/UNASSIGNED/UNASSIGNED/src/bms/epsmlis.bms using IBM-1047
Copying [DBEHM.MIG.COPY, PAYDBCR] to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/UNASSIGNED/UNASSIGNED/src/copy/paydbcr.cpy using IBM-1047
** Build finished
~~~~
</details>

#### Assessing the usage of Include Files and Programs

The [Classification script (3-classify.sh)](./src/scripts/3-classify.sh) only requires the path to the DBB Git Migration Modeler Configuration file as parameter, to locate the work directories.

It will search for all DBB Migration mapping files located in the *work-configs* folder and will process application definitions found in this folder.
This script works in 2 phases:
1. The first phase is a scan of all the files found in the application subfolders,
2. The second phase is an analysis of how the different Include Files and Programs are used by all the known applications. 

<details>
  <summary>Output example</summary>
Execution of the command:

`./src/scripts/utils/3-classify.sh -c /u/mdalbin/Migration-Modeler-DBEHM-work/DBB_GIT_MIGRATION_MODELER.config`

Output log:
~~~~
*******************************************************************
Scan application directory /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA
*******************************************************************
** Script configuration:
   workspace -> /u/mdalbin/Migration-Modeler-DBEHM-work/applications
   metadatastore -> /u/mdalbin/Migration-Modeler-DBEHM-work/dbb-metadatastore
   application -> CBSA
   logFile -> /u/mdalbin/Migration-Modeler-DBEHM-work/logs/3-CBSA-scan.log
   dbb.DependencyScanner.controlTransfers -> true
** Scanning the files.
         Scanning file CBSA/CBSA/src/cobol/getscode.cbl
         Scanning file CBSA/CBSA/src/cobol/acctctrl.cbl
         Scanning file CBSA/CBSA/src/copy/procdb2.cpy
         Scanning file CBSA/CBSA/src/cobol/bnk1cca.cbl
         Scanning file CBSA/CBSA/src/copy/contdb2.cpy
         Scanning file CBSA/CBSA/src/cobol/bnk1cac.cbl
         Scanning file CBSA/CBSA/src/cobol/updcust.cbl
         Scanning file CBSA/CBSA/src/copy/abndinfo.cpy
         Scanning file CBSA/CBSA/src/copy/bnk1dcm.cpy
         Scanning file CBSA/CBSA/src/cobol/xfrfun.cbl
         Scanning file CBSA/CBSA/src/copy/consent.cpy
         Scanning file CBSA/CBSA/src/cobol/bnk1ccs.cbl
         Scanning file CBSA/CBSA/src/copy/sortcode.cpy
         Scanning file CBSA/CBSA/src/copy/custctrl.cpy
         Scanning file CBSA/CBSA/src/copy/xfrfun.cpy
         Scanning file CBSA/CBSA/src/cobol/inqcust.cbl
         Scanning file CBSA/CBSA/src/cobol/crdtagy1.cbl
         Scanning file CBSA/CBSA/src/copy/constdb2.cpy
         Scanning file CBSA/CBSA/src/cobol/bankdata.cbl
         Scanning file CBSA/CBSA/src/cobol/crecust.cbl
         Scanning file CBSA/CBSA/src/copy/getcompy.cpy
         Scanning file CBSA/CBSA/src/cobol/consent.cbl
         Scanning file CBSA/CBSA/src/copy/delacc.cpy
         Scanning file CBSA/CBSA/src/cobol/crdtagy2.cbl
         Scanning file CBSA/CBSA/src/cobol/delacc.cbl
         Scanning file CBSA/CBSA/src/cobol/dpayapi.cbl
         Scanning file CBSA/CBSA/src/copy/inqacccu.cpy
         Scanning file CBSA/CBSA/src/cobol/bnk1tfn.cbl
         Scanning file CBSA/CBSA/src/copy/constapi.cpy
         Scanning file CBSA/CBSA/src/cobol/proload.cbl
         Scanning file CBSA/CBSA/src/cobol/inqacccu.cbl
         Scanning file CBSA/CBSA/src/copy/bnk1cam.cpy
         Scanning file CBSA/CBSA/src/copy/bnk1cdm.cpy
         Scanning file CBSA/CBSA/src/cobol/dpaytst.cbl
         Scanning file CBSA/CBSA/src/cobol/consttst.cbl
         Scanning file CBSA/CBSA/src/cobol/bnk1cra.cbl
         Scanning file CBSA/CBSA/src/cobol/prooffl.cbl
         Scanning file CBSA/CBSA/src/cobol/crdtagy3.cbl
         Scanning file CBSA/CBSA/src/cobol/updacc.cbl
         Scanning file CBSA/CBSA/src/cobol/delcus.cbl
         Scanning file CBSA/CBSA/src/copy/acctctrl.cpy
         Scanning file CBSA/CBSA/src/cobol/accoffl.cbl
         Scanning file CBSA/CBSA/src/copy/updacc.cpy
         Scanning file CBSA/CBSA/src/copy/delcus.cpy
         Scanning file CBSA/.gitattributes
         Scanning file CBSA/CBSA/src/copy/proctran.cpy
         Scanning file CBSA/CBSA/src/copy/datastr.cpy
         Scanning file CBSA/CBSA/src/copy/updcust.cpy
         Scanning file CBSA/CBSA/src/cobol/crdtagy4.cbl
         Scanning file CBSA/CBSA/src/copy/getscode.cpy
         Scanning file CBSA/CBSA/src/cobol/creacc.cbl
         Scanning file CBSA/CBSA/src/cobol/crdtagy5.cbl
         Scanning file CBSA/CBSA/src/cobol/accload.cbl
         Scanning file CBSA/CBSA/src/copy/account.cpy
         Scanning file CBSA/CBSA/src/copy/bnk1ccm.cpy
         Scanning file CBSA/CBSA/src/copy/bnk1dam.cpy
         Scanning file CBSA/CBSA/src/copy/paydbcr.cpy
         Scanning file CBSA/CBSA/src/cobol/getcompy.cbl
         Scanning file CBSA/CBSA/src/cobol/custctrl.cbl
         Scanning file CBSA/CBSA/src/copy/accdb2.cpy
         Scanning file CBSA/CBSA/src/copy/inqacc.cpy
         Scanning file CBSA/CBSA/src/copy/bnk1mai.cpy
         Scanning file CBSA/CBSA/src/cobol/inqacc.cbl
         Scanning file CBSA/CBSA/src/cobol/bnk1dcs.cbl
         Scanning file CBSA/CBSA/src/cobol/bnk1dac.cbl
         Scanning file CBSA/CBSA/src/cobol/bnk1uac.cbl
         Scanning file CBSA/CBSA/src/copy/customer.cpy
         Scanning file CBSA/CBSA/src/copy/crecust.cpy
         Scanning file CBSA/CBSA/src/copy/creacc.cpy
         Scanning file CBSA/CBSA/src/cobol/bnkmenu.cbl
         Scanning file CBSA/CBSA/src/cobol/dbcrfun.cbl
         Scanning file CBSA/CBSA/src/copy/bnk1acc.cpy
         Scanning file CBSA/CBSA/src/copy/bnk1uam.cpy
         Scanning file CBSA/CBSA/src/copy/inqcust.cpy
         Scanning file CBSA/CBSA/src/cobol/abndproc.cbl
         Scanning file CBSA/CBSA/src/copy/bnk1tfm.cpy
** Storing results in the 'CBSA' DBB Collection.
** Build finished
*******************************************************************
Scan application directory /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp
*******************************************************************
** Script configuration:
   workspace -> /u/mdalbin/Migration-Modeler-DBEHM-work/applications
   metadatastore -> /u/mdalbin/Migration-Modeler-DBEHM-work/dbb-metadatastore
   application -> GenApp
   logFile -> /u/mdalbin/Migration-Modeler-DBEHM-work/logs/3-GenApp-scan.log
   dbb.DependencyScanner.controlTransfers -> true
** Scanning the files.
         Scanning file GenApp/GenApp/src/cobol/lgtestp2.cbl
         Scanning file GenApp/GenApp/src/cobol/lgicus01.cbl
         Scanning file GenApp/GenApp/src/cobol/lgucus01.cbl
         Scanning file GenApp/GenApp/src/cobol/lgucvs01.cbl
         Scanning file GenApp/GenApp/src/cobol/lgapdb01.cbl
         Scanning file GenApp/GenApp/src/cobol/lgdpdb01.cbl
         Scanning file GenApp/GenApp/src/cobol/lgicvs01.cbl
         Scanning file GenApp/GenApp/src/copy/lgpolicy.cpy
         Scanning file GenApp/GenApp/src/cobol/lgsetup.cbl
         Scanning file GenApp/GenApp/src/copy/lgcmarea.cpy
         Scanning file GenApp/GenApp/src/cobol/lgacdb01.cbl
         Scanning file GenApp/GenApp/src/cobol/lgipdb01.cbl
         Scanning file GenApp/GenApp/src/cobol/lgupvs01.cbl
         Scanning file GenApp/GenApp/src/cobol/lgtestp1.cbl
         Scanning file GenApp/GenApp/src/cobol/lgtestc1.cbl
         Scanning file GenApp/.gitattributes
         Scanning file GenApp/GenApp/src/cobol/lgdpol01.cbl
         Scanning file GenApp/GenApp/src/cobol/lgapol01.cbl
         Scanning file GenApp/GenApp/src/bms/ssmap.bms
         Scanning file GenApp/GenApp/src/copy/lgcmared.cpy
         Scanning file GenApp/GenApp/src/cobol/lgucdb01.cbl
         Scanning file GenApp/GenApp/src/cobol/lgacdb02.cbl
         Scanning file GenApp/GenApp/src/cobol/lgipol01.cbl
         Scanning file GenApp/GenApp/src/cobol/lgapvs01.cbl
         Scanning file GenApp/GenApp/src/cobol/lgicdb01.cbl
         Scanning file GenApp/GenApp/src/cobol/lgtestp4.cbl
         Scanning file GenApp/GenApp/src/cobol/lgdpvs01.cbl
         Scanning file GenApp/GenApp/src/cobol/lgupol01.cbl
         Scanning file GenApp/GenApp/src/cobol/lgacvs01.cbl
         Scanning file GenApp/GenApp/src/cobol/lgipvs01.cbl
         Scanning file GenApp/GenApp/src/cobol/lgastat1.cbl
         Scanning file GenApp/GenApp/src/cobol/lgacus01.cbl
         Scanning file GenApp/GenApp/src/cobol/lgupdb01.cbl
         Scanning file GenApp/GenApp/src/cobol/lgtestp3.cbl
         Scanning file GenApp/GenApp/src/cobol/lgstsq.cbl
         Scanning file GenApp/GenApp/src/cobol/lgwebst5.cbl
** Storing results in the 'GenApp' DBB Collection.
** Build finished
*******************************************************************
Scan application directory /u/mdalbin/Migration-Modeler-DBEHM-work/applications/RetirementCalculator
*******************************************************************
** Script configuration:
   workspace -> /u/mdalbin/Migration-Modeler-DBEHM-work/applications
   metadatastore -> /u/mdalbin/Migration-Modeler-DBEHM-work/dbb-metadatastore
   application -> RetirementCalculator
   logFile -> /u/mdalbin/Migration-Modeler-DBEHM-work/logs/3-RetirementCalculator-scan.log
   dbb.DependencyScanner.controlTransfers -> true
** Scanning the files.
         Scanning file RetirementCalculator/.gitattributes
         Scanning file RetirementCalculator/RetirementCalculator/src/cobol/ebud02.cbl
         Scanning file RetirementCalculator/RetirementCalculator/src/cobol/ebud01.cbl
         Scanning file RetirementCalculator/RetirementCalculator/src/cobol/ebud03.cbl
         Scanning file RetirementCalculator/RetirementCalculator/src/cobol/ebud0run.cbl
         Scanning file RetirementCalculator/RetirementCalculator/src/copy/linput.cpy
** Storing results in the 'RetirementCalculator' DBB Collection.
** Build finished
*******************************************************************
Scan application directory /u/mdalbin/Migration-Modeler-DBEHM-work/applications/UNASSIGNED
*******************************************************************
** Script configuration:
   workspace -> /u/mdalbin/Migration-Modeler-DBEHM-work/applications
   metadatastore -> /u/mdalbin/Migration-Modeler-DBEHM-work/dbb-metadatastore
   application -> UNASSIGNED
   logFile -> /u/mdalbin/Migration-Modeler-DBEHM-work/logs/3-UNASSIGNED-scan.log
   dbb.DependencyScanner.controlTransfers -> true
** Scanning the files.
         Scanning file UNASSIGNED/UNASSIGNED/src/bms/epsmlis.bms
         Scanning file UNASSIGNED/UNASSIGNED/src/cobol/oldacdb2.cbl
         Scanning file UNASSIGNED/UNASSIGNED/src/copy/datastr.cpy
         Scanning file UNASSIGNED/UNASSIGNED/src/bms/epsmort.bms
         Scanning file UNASSIGNED/UNASSIGNED/src/cobol/flemssub.cbl
         Scanning file UNASSIGNED/UNASSIGNED/src/cobol/oldacdb1.cbl
         Scanning file UNASSIGNED/.gitattributes
         Scanning file UNASSIGNED/UNASSIGNED/src/copy/sortcode.cpy
         Scanning file UNASSIGNED/UNASSIGNED/src/copy/paydbcr.cpy
         Scanning file UNASSIGNED/UNASSIGNED/src/cobol/flemsmai.cbl
** Storing results in the 'UNASSIGNED' DBB Collection.
** Build finished
*******************************************************************
Assess Include files & Programs usage for CBSA
*******************************************************************
** Script configuration:
   workspace -> /u/mdalbin/Migration-Modeler-DBEHM-work/applications
   metadatastore -> /u/mdalbin/Migration-Modeler-DBEHM-work/dbb-metadatastore
   application -> CBSA
   configurationsDirectory -> /u/mdalbin/Migration-Modeler-DBEHM-work/modeler-configs
   logFile -> /u/mdalbin/Migration-Modeler-DBEHM-work/logs/3-CBSA-assessUsage.log
   applicationDir -> /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA
   moveFiles -> true
** Getting the list of files of 'Include File' type.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/bnk1ccm.cpy'.
        Files depending on 'CBSA/src/copy/bnk1ccm.cpy' :
        'CBSA/CBSA/src/cobol/bnk1ccs.cbl' in 'CBSA' application context
        ==> 'bnk1ccm' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'bnk1ccm' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/bnk1dam.cpy'.
        Files depending on 'CBSA/src/copy/bnk1dam.cpy' :
        'CBSA/CBSA/src/cobol/bnk1dac.cbl' in 'CBSA' application context
        ==> 'bnk1dam' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'bnk1dam' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/bnk1cam.cpy'.
        Files depending on 'CBSA/src/copy/bnk1cam.cpy' :
        'CBSA/CBSA/src/cobol/bnk1cac.cbl' in 'CBSA' application context
        ==> 'bnk1cam' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'bnk1cam' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/creacc.cpy'.
        Files depending on 'CBSA/src/copy/creacc.cpy' :
        'CBSA/CBSA/src/cobol/creacc.cbl' in 'CBSA' application context
        ==> 'creacc' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'creacc' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/bnk1dcm.cpy'.
        Files depending on 'CBSA/src/copy/bnk1dcm.cpy' :
        'CBSA/CBSA/src/cobol/bnk1dcs.cbl' in 'CBSA' application context
        ==> 'bnk1dcm' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'bnk1dcm' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/procdb2.cpy'.
        The Include File 'procdb2' is not referenced at all.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/constdb2.cpy'.
        The Include File 'constdb2' is not referenced at all.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/abndinfo.cpy'.
        Files depending on 'CBSA/src/copy/abndinfo.cpy' :
        'CBSA/CBSA/src/cobol/delcus.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/updcust.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bnk1cca.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crdtagy3.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/acctctrl.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/inqacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/xfrfun.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bnk1dcs.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/creacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crdtagy2.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/delacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bnk1tfn.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bnkmenu.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bnk1ccs.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crdtagy1.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/inqcust.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/inqacccu.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bnk1dac.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/dbcrfun.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bnk1cra.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crdtagy5.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crecust.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/abndproc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/custctrl.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bnk1uac.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bnk1cac.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/updacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crdtagy4.cbl' in 'CBSA' application context
        ==> 'abndinfo' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'abndinfo' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/bnk1tfm.cpy'.
        Files depending on 'CBSA/src/copy/bnk1tfm.cpy' :
        'CBSA/CBSA/src/cobol/bnk1tfn.cbl' in 'CBSA' application context
        ==> 'bnk1tfm' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'bnk1tfm' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/bnk1acc.cpy'.
        Files depending on 'CBSA/src/copy/bnk1acc.cpy' :
        'CBSA/CBSA/src/cobol/bnk1cca.cbl' in 'CBSA' application context
        ==> 'bnk1acc' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'bnk1acc' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/proctran.cpy'.
        Files depending on 'CBSA/src/copy/proctran.cpy' :
        'CBSA/CBSA/src/cobol/delacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/delcus.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crecust.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/xfrfun.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/dbcrfun.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/creacc.cbl' in 'CBSA' application context
        ==> 'proctran' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'proctran' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/acctctrl.cpy'.
        Files depending on 'CBSA/src/copy/acctctrl.cpy' :
        'CBSA/CBSA/src/cobol/delacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/acctctrl.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bankdata.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/creacc.cbl' in 'CBSA' application context
        ==> 'acctctrl' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'acctctrl' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/custctrl.cpy'.
        Files depending on 'CBSA/src/copy/custctrl.cpy' :
        'CBSA/CBSA/src/cobol/custctrl.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bankdata.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crecust.cbl' in 'CBSA' application context
        ==> 'custctrl' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'custctrl' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/xfrfun.cpy'.
        Files depending on 'CBSA/src/copy/xfrfun.cpy' :
        'CBSA/CBSA/src/cobol/xfrfun.cbl' in 'CBSA' application context
        ==> 'xfrfun' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'xfrfun' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/crecust.cpy'.
        Files depending on 'CBSA/src/copy/crecust.cpy' :
        'CBSA/CBSA/src/cobol/crecust.cbl' in 'CBSA' application context
        ==> 'crecust' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'crecust' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/inqacccu.cpy'.
        Files depending on 'CBSA/src/copy/inqacccu.cpy' :
        'CBSA/CBSA/src/cobol/delcus.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bnk1cca.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/inqacccu.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/creacc.cbl' in 'CBSA' application context
        ==> 'inqacccu' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'inqacccu' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/bnk1cdm.cpy'.
        Files depending on 'CBSA/src/copy/bnk1cdm.cpy' :
        'CBSA/CBSA/src/cobol/bnk1cra.cbl' in 'CBSA' application context
        ==> 'bnk1cdm' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'bnk1cdm' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/getscode.cpy'.
        Files depending on 'CBSA/src/copy/getscode.cpy' :
        'CBSA/CBSA/src/cobol/getscode.cbl' in 'CBSA' application context
        ==> 'getscode' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'getscode' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/consent.cpy'.
        Files depending on 'CBSA/src/copy/consent.cpy' :
        'CBSA/CBSA/src/cobol/dpaytst.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/consent.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/dpayapi.cbl' in 'CBSA' application context
        ==> 'consent' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'consent' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/bnk1mai.cpy'.
        Files depending on 'CBSA/src/copy/bnk1mai.cpy' :
        'CBSA/CBSA/src/cobol/bnkmenu.cbl' in 'CBSA' application context
        ==> 'bnk1mai' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'bnk1mai' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/constapi.cpy'.
        Files depending on 'CBSA/src/copy/constapi.cpy' :
        'CBSA/CBSA/src/cobol/dpaytst.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/consttst.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/consent.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/dpayapi.cbl' in 'CBSA' application context
        ==> 'constapi' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'constapi' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/delacc.cpy'.
        Files depending on 'CBSA/src/copy/delacc.cpy' :
        'CBSA/CBSA/src/cobol/delacc.cbl' in 'CBSA' application context
        ==> 'delacc' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'delacc' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/delcus.cpy'.
        Files depending on 'CBSA/src/copy/delcus.cpy' :
        'CBSA/CBSA/src/cobol/delcus.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bnk1dcs.cbl' in 'CBSA' application context
        ==> 'delcus' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'delcus' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/getcompy.cpy'.
        Files depending on 'CBSA/src/copy/getcompy.cpy' :
        'CBSA/CBSA/src/cobol/getcompy.cbl' in 'CBSA' application context
        ==> 'getcompy' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'getcompy' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/accdb2.cpy'.
        The Include File 'accdb2' is not referenced at all.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/contdb2.cpy'.
        The Include File 'contdb2' is not referenced at all.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/inqcust.cpy'.
        Files depending on 'CBSA/src/copy/inqcust.cpy' :
        'CBSA/CBSA/src/cobol/delcus.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/inqcust.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/inqacccu.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bnk1dcs.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/creacc.cbl' in 'CBSA' application context
        ==> 'inqcust' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'inqcust' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/updacc.cpy'.
        Files depending on 'CBSA/src/copy/updacc.cpy' :
        'CBSA/CBSA/src/cobol/updacc.cbl' in 'CBSA' application context
        ==> 'updacc' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'updacc' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/inqacc.cpy'.
        Files depending on 'CBSA/src/copy/inqacc.cpy' :
        'CBSA/CBSA/src/cobol/inqacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bnk1dac.cbl' in 'CBSA' application context
        ==> 'inqacc' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'inqacc' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/updcust.cpy'.
        Files depending on 'CBSA/src/copy/updcust.cpy' :
        'CBSA/CBSA/src/cobol/updcust.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bnk1dcs.cbl' in 'CBSA' application context
        ==> 'updcust' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'updcust' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/bnk1uam.cpy'.
        Files depending on 'CBSA/src/copy/bnk1uam.cpy' :
        'CBSA/CBSA/src/cobol/bnk1uac.cbl' in 'CBSA' application context
        ==> 'bnk1uam' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'bnk1uam' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/account.cpy'.
        Files depending on 'CBSA/src/copy/account.cpy' :
        'CBSA/CBSA/src/cobol/dpaytst.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/delacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/delcus.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/consent.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/inqacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/inqacccu.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/xfrfun.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/dbcrfun.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/updacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/creacc.cbl' in 'CBSA' application context
        ==> 'account' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'account' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/customer.cpy'.
        Files depending on 'CBSA/src/copy/customer.cpy' :
        'CBSA/CBSA/src/cobol/delcus.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/updcust.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/inqcust.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/inqacccu.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bankdata.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crecust.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/creacc.cbl' in 'CBSA' application context
        ==> 'customer' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'customer' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Getting the list of files of 'Program' type.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/bnk1cac.cbl'.
        The Program 'bnk1cac' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/proload.cbl'.
        The Program 'proload' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/bnk1dac.cbl'.
        The Program 'bnk1dac' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/dpayapi.cbl'.
        The Program 'dpayapi' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/dpaytst.cbl'.
        The Program 'dpaytst' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/accoffl.cbl'.
        The Program 'accoffl' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/crdtagy5.cbl'.
        The Program 'crdtagy5' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/creacc.cbl'.
        The Program 'creacc' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/crdtagy4.cbl'.
        The Program 'crdtagy4' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/bnkmenu.cbl'.
        The Program 'bnkmenu' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/bankdata.cbl'.
        The Program 'bankdata' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/prooffl.cbl'.
        The Program 'prooffl' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/bnk1tfn.cbl'.
        The Program 'bnk1tfn' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/bnk1cca.cbl'.
        The Program 'bnk1cca' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/dbcrfun.cbl'.
        The Program 'dbcrfun' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/acctctrl.cbl'.
        The Program 'acctctrl' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/custctrl.cbl'.
        The Program 'custctrl' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/xfrfun.cbl'.
        The Program 'xfrfun' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/crecust.cbl'.
        The Program 'crecust' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/inqacccu.cbl'.
        The Program 'inqacccu' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/getscode.cbl'.
        The Program 'getscode' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/consent.cbl'.
        The Program 'consent' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/crdtagy3.cbl'.
        The Program 'crdtagy3' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/delacc.cbl'.
        The Program 'delacc' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/delcus.cbl'.
        The Program 'delcus' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/bnk1dcs.cbl'.
        The Program 'bnk1dcs' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/crdtagy2.cbl'.
        The Program 'crdtagy2' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/abndproc.cbl'.
        The Program 'abndproc' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/bnk1ccs.cbl'.
        The Program 'bnk1ccs' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/crdtagy1.cbl'.
        The Program 'crdtagy1' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/bnk1cra.cbl'.
        The Program 'bnk1cra' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/getcompy.cbl'.
        The Program 'getcompy' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/accload.cbl'.
        The Program 'accload' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/inqcust.cbl'.
        The Program 'inqcust' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/bnk1uac.cbl'.
        The Program 'bnk1uac' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/updacc.cbl'.
        The Program 'updacc' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/consttst.cbl'.
        The Program 'consttst' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/inqacc.cbl'.
        The Program 'inqacc' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/updcust.cbl'.
        The Program 'updcust' is not called by any other program.
** Build finished
*******************************************************************
Assess Include files & Programs usage for GenApp
*******************************************************************
** Script configuration:
   workspace -> /u/mdalbin/Migration-Modeler-DBEHM-work/applications
   metadatastore -> /u/mdalbin/Migration-Modeler-DBEHM-work/dbb-metadatastore
   application -> GenApp
   configurationsDirectory -> /u/mdalbin/Migration-Modeler-DBEHM-work/modeler-configs
   logFile -> /u/mdalbin/Migration-Modeler-DBEHM-work/logs/3-GenApp-assessUsage.log
   applicationDir -> /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp
   moveFiles -> true
** Getting the list of files of 'Include File' type.
** Analyzing impacted applications for file 'GenApp/GenApp/src/copy/lgpolicy.cpy'.
        Files depending on 'GenApp/src/copy/lgpolicy.cpy' :
        'GenApp/GenApp/src/cobol/lgacdb02.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgacus01.cbl' in 'GenApp' application context
        'UNASSIGNED/UNASSIGNED/src/cobol/oldacdb2.cbl' in 'UNASSIGNED' application context
        'GenApp/GenApp/src/cobol/lgipol01.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgacdb01.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgicus01.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgicdb01.cbl' in 'GenApp' application context
        'UNASSIGNED/UNASSIGNED/src/cobol/oldacdb1.cbl' in 'UNASSIGNED' application context
        ==> 'lgpolicy' referenced by multiple applications - [UNASSIGNED, GenApp]
        ==> Updating usage of Include File 'lgpolicy' to 'public' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp.yaml'.
** Analyzing impacted applications for file 'GenApp/GenApp/src/copy/lgcmared.cpy'.
        The Include File 'lgcmared' is not referenced at all.
** Analyzing impacted applications for file 'GenApp/GenApp/src/copy/lgcmarea.cpy'.
        Files depending on 'GenApp/src/copy/lgcmarea.cpy' :
        'GenApp/GenApp/src/cobol/lgapol01.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgtestc1.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgupvs01.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgacus01.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgastat1.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgacvs01.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgdpol01.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgipol01.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgdpvs01.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgtestp1.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgapvs01.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgucus01.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgupol01.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgtestp2.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgtestp4.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgicus01.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgucvs01.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgtestp3.cbl' in 'GenApp' application context
        ==> 'lgcmarea' is owned by the 'GenApp' application
        ==> Updating usage of Include File 'lgcmarea' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp.yaml'.
** Getting the list of files of 'Program' type.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgicus01.cbl'.
        The Program 'lgicus01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgdpol01.cbl'.
        The Program 'lgdpol01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgipdb01.cbl'.
        The Program 'lgipdb01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgtestp3.cbl'.
        The Program 'lgtestp3' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgtestp4.cbl'.
        The Program 'lgtestp4' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgacvs01.cbl'.
        The Program 'lgacvs01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgsetup.cbl'.
        The Program 'lgsetup' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgapol01.cbl'.
        The Program 'lgapol01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgipvs01.cbl'.
        The Program 'lgipvs01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgupol01.cbl'.
        The Program 'lgupol01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgacdb01.cbl'.
        The Program 'lgacdb01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgacdb02.cbl'.
        The Program 'lgacdb02' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgstsq.cbl'.
        The Program 'lgstsq' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgtestp1.cbl'.
        The Program 'lgtestp1' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgtestp2.cbl'.
        The Program 'lgtestp2' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgdpdb01.cbl'.
        The Program 'lgdpdb01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgucus01.cbl'.
        The Program 'lgucus01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgapvs01.cbl'.
        The Program 'lgapvs01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgucdb01.cbl'.
        The Program 'lgucdb01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgdpvs01.cbl'.
        The Program 'lgdpvs01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgtestc1.cbl'.
        The Program 'lgtestc1' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgastat1.cbl'.
        The Program 'lgastat1' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgapdb01.cbl'.
        The Program 'lgapdb01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgicvs01.cbl'.
        The Program 'lgicvs01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgipol01.cbl'.
        The Program 'lgipol01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgacus01.cbl'.
        The Program 'lgacus01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgwebst5.cbl'.
        The Program 'lgwebst5' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgucvs01.cbl'.
        The Program 'lgucvs01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgupdb01.cbl'.
        The Program 'lgupdb01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgicdb01.cbl'.
        The Program 'lgicdb01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgupvs01.cbl'.
        The Program 'lgupvs01' is not called by any other program.
** Build finished
*******************************************************************
Assess Include files & Programs usage for RetirementCalculator
*******************************************************************
** Script configuration:
   workspace -> /u/mdalbin/Migration-Modeler-DBEHM-work/applications
   metadatastore -> /u/mdalbin/Migration-Modeler-DBEHM-work/dbb-metadatastore
   application -> RetirementCalculator
   configurationsDirectory -> /u/mdalbin/Migration-Modeler-DBEHM-work/modeler-configs
   logFile -> /u/mdalbin/Migration-Modeler-DBEHM-work/logs/3-RetirementCalculator-assessUsage.log
   applicationDir -> /u/mdalbin/Migration-Modeler-DBEHM-work/applications/RetirementCalculator
   moveFiles -> true
** Getting the list of files of 'Include File' type.
** Analyzing impacted applications for file 'RetirementCalculator/RetirementCalculator/src/copy/linput.cpy'.
        Files depending on 'RetirementCalculator/src/copy/linput.cpy' :
        'RetirementCalculator/RetirementCalculator/src/cobol/ebud01.cbl' in 'RetirementCalculator' application context
        'GenApp/GenApp/src/cobol/lgacdb01.cbl' in 'GenApp' application context
        'RetirementCalculator/RetirementCalculator/src/cobol/ebud0run.cbl' in 'RetirementCalculator' application context
        ==> 'linput' referenced by multiple applications - [GenApp, RetirementCalculator]
        ==> Updating usage of Include File 'linput' to 'public' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/RetirementCalculator/RetirementCalculator.yaml'.
** Getting the list of files of 'Program' type.
** Analyzing impacted applications for file 'RetirementCalculator/RetirementCalculator/src/cobol/ebud01.cbl'.
        The Program 'ebud01' is not called by any other program.
** Analyzing impacted applications for file 'RetirementCalculator/RetirementCalculator/src/cobol/ebud03.cbl'.
        The Program 'ebud03' is not called by any other program.
** Analyzing impacted applications for file 'RetirementCalculator/RetirementCalculator/src/cobol/ebud02.cbl'.
        Files depending on 'RetirementCalculator/src/cobol/ebud02.cbl' :
        'CBSA/CBSA/src/cobol/abndproc.cbl' in 'CBSA' application context
        ==> 'ebud02' is called from the 'CBSA' application
Adding dependency to application CBSA
        ==> Updating usage of Program 'ebud02' to 'service submodule' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/RetirementCalculator/RetirementCalculator.yaml'.
** Analyzing impacted applications for file 'RetirementCalculator/RetirementCalculator/src/cobol/ebud0run.cbl'.
        The Program 'ebud0run' is not called by any other program.
** Build finished
*******************************************************************
Assess Include files & Programs usage for UNASSIGNED
*******************************************************************
** Script configuration:
   workspace -> /u/mdalbin/Migration-Modeler-DBEHM-work/applications
   metadatastore -> /u/mdalbin/Migration-Modeler-DBEHM-work/dbb-metadatastore
   application -> UNASSIGNED
   configurationsDirectory -> /u/mdalbin/Migration-Modeler-DBEHM-work/modeler-configs
   logFile -> /u/mdalbin/Migration-Modeler-DBEHM-work/logs/3-UNASSIGNED-assessUsage.log
   applicationDir -> /u/mdalbin/Migration-Modeler-DBEHM-work/applications/UNASSIGNED
   moveFiles -> true
** Getting the list of files of 'Include File' type.
** Analyzing impacted applications for file 'UNASSIGNED/UNASSIGNED/src/copy/datastr.cpy'.
        Files depending on 'UNASSIGNED/src/copy/datastr.cpy' :
        'CBSA/CBSA/src/cobol/delcus.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/updcust.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crdtagy3.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crdtagy5.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/inqacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bankdata.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crecust.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/xfrfun.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/creacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crdtagy2.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/delacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crdtagy1.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/inqcust.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/inqacccu.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/dbcrfun.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/updacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crdtagy4.cbl' in 'CBSA' application context
        ==> 'datastr' is owned by the 'CBSA' application
        ==> Moving Include File 'datastr' with usage 'private' to Application 'CBSA' described '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'UNASSIGNED/UNASSIGNED/src/copy/paydbcr.cpy'.
        Files depending on 'UNASSIGNED/src/copy/paydbcr.cpy' :
        'CBSA/CBSA/src/cobol/dbcrfun.cbl' in 'CBSA' application context
        ==> 'paydbcr' is owned by the 'CBSA' application
        ==> Moving Include File 'paydbcr' with usage 'private' to Application 'CBSA' described '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'UNASSIGNED/UNASSIGNED/src/copy/sortcode.cpy'.
        Files depending on 'UNASSIGNED/src/copy/sortcode.cpy' :
        'CBSA/CBSA/src/cobol/delcus.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/updcust.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crdtagy3.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crdtagy5.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/acctctrl.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/inqacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bankdata.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crecust.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/xfrfun.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/creacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crdtagy2.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/delacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/getscode.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/custctrl.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crdtagy1.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/inqcust.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/inqacccu.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/dbcrfun.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/updacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crdtagy4.cbl' in 'CBSA' application context
        ==> 'sortcode' is owned by the 'CBSA' application
        ==> Moving Include File 'sortcode' with usage 'private' to Application 'CBSA' described '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Getting the list of files of 'Program' type.
** Analyzing impacted applications for file 'UNASSIGNED/UNASSIGNED/src/cobol/flemssub.cbl'.
        Files depending on 'UNASSIGNED/src/cobol/flemssub.cbl' :
        'UNASSIGNED/UNASSIGNED/src/cobol/flemsmai.cbl' in 'UNASSIGNED' application context
        ==> 'flemssub' is called from the 'UNASSIGNED' application
        ==> Updating usage of Program 'flemssub' to 'internal submodule' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/UNASSIGNED/UNASSIGNED.yaml'.
** Analyzing impacted applications for file 'UNASSIGNED/UNASSIGNED/src/cobol/oldacdb2.cbl'.
        The Program 'oldacdb2' is not called by any other program.
** Analyzing impacted applications for file 'UNASSIGNED/UNASSIGNED/src/cobol/oldacdb1.cbl'.
        The Program 'oldacdb1' is not called by any other program.
** Analyzing impacted applications for file 'UNASSIGNED/UNASSIGNED/src/cobol/flemsmai.cbl'.
        The Program 'flemsmai' is not called by any other program.
** Build finished
~~~~
</details>

#### Generating Property files

The [Property Generation script (4-generateProperties.sh)](./src/scripts/4-generateProperties.sh) requires the path to the DBB Git Migration Modeler configuration file as parameter, to identify:
* The path to the [Types Configurations](./samples/typesConfigurations.yaml) file,
* The path to an original *dbb-zAppBuild* instance, that will be copied and customized during this phase.

The script will search for all DBB Migration mapping files located in the *work-configs* folder and will process application definitions found in this folder.
For each application found, it will search for the artifacts of type 'Program', and, for each of them, will check if a Language Configuration exists, based on the *type* information.
If the Language Configuration doesn't exist, the script will create it (potentially combining multiple type configurations if necessary).

This script will also generate application's related configuration, stored in a custom *application-conf* folder.
If configuration was changed, an *INFO* message is shown, explaining that a manual task must be performed to enable the use of the Language Configuration mapping for a given application.
 

<details>
  <summary>Output example</summary>
Execution of the command:
	
`./src/scripts/utils/4-generateProperties.sh  -c /u/mdalbin/Migration-Modeler-DBEHM-work/DBB_GIT_MIGRATION_MODELER.config`

Output log:
~~~~
*******************************************************************
Generate properties for application 'CBSA'
*******************************************************************
** Script configuration:
   workspace -> /u/mdalbin/Migration-Modeler-DBEHM-work/applications
   typesConfigurationsFilePath -> /u/mdalbin/Migration-Modeler-DBEHM-work/typesConfigurations.yaml
   application -> CBSA
   logFile -> /u/mdalbin/Migration-Modeler-DBEHM-work/logs/4-CBSA-generateProperties.log
   zAppBuildFolderPath -> /var/dbb/dbb-zappbuild_300
** Reading the Types Configurations definitions from '/u/mdalbin/Migration-Modeler-DBEHM-work/typesConfigurations.yaml'.
** Copying the zAppBuild from /var/dbb/dbb-zappbuild_300 to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/dbb-zappbuild.
** Copying default application-conf directory to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA/application-conf
** Getting the list of files.
*** Generate/Validate Language Configuration properties files.
** Build finished
*******************************************************************
Generate properties for application 'GenApp'
*******************************************************************
** Script configuration:
   workspace -> /u/mdalbin/Migration-Modeler-DBEHM-work/applications
   typesConfigurationsFilePath -> /u/mdalbin/Migration-Modeler-DBEHM-work/typesConfigurations.yaml
   application -> GenApp
   logFile -> /u/mdalbin/Migration-Modeler-DBEHM-work/logs/4-GenApp-generateProperties.log
   zAppBuildFolderPath -> /var/dbb/dbb-zappbuild_300
** Reading the Types Configurations definitions from '/u/mdalbin/Migration-Modeler-DBEHM-work/typesConfigurations.yaml'.
** Copying default application-conf directory to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/application-conf
** Getting the list of files.
*** Generate/Validate Language Configuration properties files.
        Assessing file lgacdb01 with type CBLCICSDB2.
         Generating new Language Configuration /u/mdalbin/Migration-Modeler-DBEHM-work/applications/dbb-zappbuild/build-conf/language-conf/CBLCICSDB2.properties for type 'CBLCICSDB2'
        Assessing file lgacdb02 with type CBLDB2.
         Generating new Language Configuration /u/mdalbin/Migration-Modeler-DBEHM-work/applications/dbb-zappbuild/build-conf/language-conf/CBLDB2.properties for type 'CBLDB2'
        Assessing file lgacus01 with type PLICICS.
         Generating new Language Configuration /u/mdalbin/Migration-Modeler-DBEHM-work/applications/dbb-zappbuild/build-conf/language-conf/PLICICS.properties for type 'PLICICS'
*** Generate the language configuration mapping file /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/application-conf/languageConfigurationMapping.properties.
*** Generate loadLanguageConfigurationProperties configuration in /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/application-conf/file.properties.
** INFO: Don't forget to enable the use of Language Configuration by uncommenting the 'loadLanguageConfigurationProperties' property in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp/application-conf/file.properties'
** Build finished
*******************************************************************
Generate properties for application 'RetirementCalculator'
*******************************************************************
** Script configuration:
   workspace -> /u/mdalbin/Migration-Modeler-DBEHM-work/applications
   typesConfigurationsFilePath -> /u/mdalbin/Migration-Modeler-DBEHM-work/typesConfigurations.yaml
   application -> RetirementCalculator
   logFile -> /u/mdalbin/Migration-Modeler-DBEHM-work/logs/4-RetirementCalculator-generateProperties.log
   zAppBuildFolderPath -> /var/dbb/dbb-zappbuild_300
** Reading the Types Configurations definitions from '/u/mdalbin/Migration-Modeler-DBEHM-work/typesConfigurations.yaml'.
** Copying default application-conf directory to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/RetirementCalculator/RetirementCalculator/application-conf
** Getting the list of files.
*** Generate/Validate Language Configuration properties files.
** Build finished
*******************************************************************
Generate properties for application 'UNASSIGNED'
*******************************************************************
** Script configuration:
   workspace -> /u/mdalbin/Migration-Modeler-DBEHM-work/applications
   typesConfigurationsFilePath -> /u/mdalbin/Migration-Modeler-DBEHM-work/typesConfigurations.yaml
   application -> UNASSIGNED
   logFile -> /u/mdalbin/Migration-Modeler-DBEHM-work/logs/4-UNASSIGNED-generateProperties.log
   zAppBuildFolderPath -> /var/dbb/dbb-zappbuild_300
** Reading the Types Configurations definitions from '/u/mdalbin/Migration-Modeler-DBEHM-work/typesConfigurations.yaml'.
** Copying default application-conf directory to /u/mdalbin/Migration-Modeler-DBEHM-work/applications/UNASSIGNED/UNASSIGNED/application-conf
** Getting the list of files.
*** Generate/Validate Language Configuration properties files.
** Build finished

~~~~
</details>

### Initializing Application Git Repositories

After running through the extraction, migration, classification and property generation stages, this stage is about preparing the applications' directories to be pushed to a central Git server. 
The [Init-Application-Repositories script](./src/scripts/Init-Application-Repositories.sh) is provided to perform the following steps for each application:
1. Initialize the Git repository using a default `.gitattributes` file,
2. (Optionally) Perform a full build with dbb-zappbuild, using the preview option (no file is actually built) as a preview of the expected outcomes,
3. (Optionally) Create a baseline package using the PackageBuildOutputs.groovy script based on the preview build report. The purpose of this step is to package the existing build artifacts (load modules, DBRMs, jobs, etc.) that correspond to the version of the migrated source code files.

The [Init-Application-Repositories script](./src/scripts/Init-Application-Repositories.sh) is ivoked by supplying the path to the DBB Git Migration Modeler configuration file as parameter.

<details>
  <summary> Execution example</summary>
Execution of command:
	
`./src/scripts/Init-Application-Repositories.sh -c /u/mdalbin/Migration-Modeler-DBEHM-work/DBB_GIT_MIGRATION_MODELER.config`

~~~~

 DBB Git Migration Modeler
 Release:

 Script:      InitApplicationRepositories.sh

 Description: The purpose of this script is to initialize the Git repositories, by adding a default
              .gitattributes file. It additionally performs build preview that generates and initial
              DBB Build Report that can be examined by the application team.

              For more information please refer to:    https://github.com/IBM/dbb-git-migration-modeler


[PHASE] Initialize Git repositories
Do you want to initialize the Git repositories for the applications in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications' (Y/n):

[INFO] Initialize Git repository for application 'CBSA'
[INFO] Set file tag for 'CBSA.yaml'
[INFO] Update Git configuration files '.gitattributes'
[INFO] Prepare initial commit
[INFO] Commit files to new Git repository
[INFO] Initializing Git repository for application 'CBSA' completed. rc=0

[INFO] Initialize Git repository for application 'GenApp'
[INFO] Set file tag for 'GenApp.yaml'
[INFO] Update Git configuration files '.gitattributes'
[INFO] Prepare initial commit
[INFO] Commit files to new Git repository
[INFO] Initializing Git repository for application 'GenApp' completed. rc=0

[INFO] Initialize Git repository for application 'RetirementCalculator'
[INFO] Set file tag for 'RetirementCalculator.yaml'
[INFO] Update Git configuration files '.gitattributes'
[INFO] Prepare initial commit
[INFO] Commit files to new Git repository
[INFO] Initializing Git repository for application 'RetirementCalculator' completed. rc=0

[INFO] Initialize Git repository for application 'UNASSIGNED'
[INFO] Set file tag for 'UNASSIGNED.yaml'
[INFO] Update Git configuration files '.gitattributes'
[INFO] Prepare initial commit
[INFO] Commit files to new Git repository
[INFO] Initializing Git repository for application 'UNASSIGNED' completed. rc=0

[PHASE] Run preview builds for applications
Do you want to run preview builds for the applications in /u/mdalbin/Migration-Modeler-DBEHM-work/applications (Y/n):

[INFO] Build of application 'CBSA' started
WARNING: An illegal reflective access operation has occurred
WARNING: Illegal reflective access by org.codehaus.groovy.vmplugin.v9.Java9 (file:/usr/lpp/dbb/v2r0/groovy/lib/groovy-4.0.15.jar) to method sun.nio.fs.UnixFileSystem.getPathMatcher(java.lang.String)
WARNING: Please consider reporting this to the maintainers of org.codehaus.groovy.vmplugin.v9.Java9
WARNING: Use --illegal-access=warn to enable warnings of further illegal reflective access operations
WARNING: All illegal access operations will be denied in a future release
[INFO] Build of application 'CBSA' completed with rc=0
[INFO] Build logs and reports available at '/u/mdalbin/Migration-Modeler-DBEHM-work/logs/CBSA'

[INFO] Build of application 'GenApp' started
WARNING: An illegal reflective access operation has occurred
WARNING: Illegal reflective access by org.codehaus.groovy.vmplugin.v9.Java9 (file:/usr/lpp/dbb/v2r0/groovy/lib/groovy-4.0.15.jar) to method sun.nio.fs.UnixFileSystem.getPathMatcher(java.lang.String)
WARNING: Please consider reporting this to the maintainers of org.codehaus.groovy.vmplugin.v9.Java9
WARNING: Use --illegal-access=warn to enable warnings of further illegal reflective access operations
WARNING: All illegal access operations will be denied in a future release
[INFO] Build of application 'GenApp' completed with rc=0
[INFO] Build logs and reports available at '/u/mdalbin/Migration-Modeler-DBEHM-work/logs/GenApp'

[INFO] Build of application 'RetirementCalculator' started
WARNING: An illegal reflective access operation has occurred
WARNING: Illegal reflective access by org.codehaus.groovy.vmplugin.v9.Java9 (file:/usr/lpp/dbb/v2r0/groovy/lib/groovy-4.0.15.jar) to method sun.nio.fs.UnixFileSystem.getPathMatcher(java.lang.String)
WARNING: Please consider reporting this to the maintainers of org.codehaus.groovy.vmplugin.v9.Java9
WARNING: Use --illegal-access=warn to enable warnings of further illegal reflective access operations
WARNING: All illegal access operations will be denied in a future release
[INFO] Build of application 'RetirementCalculator' completed with rc=0
[INFO] Build logs and reports available at '/u/mdalbin/Migration-Modeler-DBEHM-work/logs/RetirementCalculator'

[INFO] Build of application 'UNASSIGNED' started
WARNING: An illegal reflective access operation has occurred
WARNING: Illegal reflective access by org.codehaus.groovy.vmplugin.v9.Java9 (file:/usr/lpp/dbb/v2r0/groovy/lib/groovy-4.0.15.jar) to method sun.nio.fs.UnixFileSystem.getPathMatcher(java.lang.String)
WARNING: Please consider reporting this to the maintainers of org.codehaus.groovy.vmplugin.v9.Java9
WARNING: Use --illegal-access=warn to enable warnings of further illegal reflective access operations
WARNING: All illegal access operations will be denied in a future release
[INFO] Build of application 'UNASSIGNED' completed with rc=0
[INFO] Build logs and reports available at '/u/mdalbin/Migration-Modeler-DBEHM-work/logs/UNASSIGNED'
[INFO] Performing preview builds completed successfully. rc=0

[PHASE] Create baseline packages for applications
Do you want to create the baseline packages for the application in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications' (Y/n):

[INFO] Packaging of application 'CBSA' started
[INFO] Packaging of application 'CBSA' completed with rc=1
[INFO] Packaging log available at '/u/mdalbin/Migration-Modeler-DBEHM-work/logs/CBSA/packaging-preview-CBSA.log'

[INFO] Packaging of application 'GenApp' started
[INFO] Packaging of application 'GenApp' completed with rc=1
[INFO] Packaging log available at '/u/mdalbin/Migration-Modeler-DBEHM-work/logs/GenApp/packaging-preview-GenApp.log'

[INFO] Packaging of application 'RetirementCalculator' started
[INFO] Packaging of application 'RetirementCalculator' completed with rc=1
[INFO] Packaging log available at '/u/mdalbin/Migration-Modeler-DBEHM-work/logs/RetirementCalculator/packaging-preview-RetirementCalculator.log'

[INFO] Packaging of application 'UNASSIGNED' started
[INFO] Packaging of application 'UNASSIGNED' completed with rc=1
[INFO] Packaging log available at '/u/mdalbin/Migration-Modeler-DBEHM-work/logs/UNASSIGNED/packaging-preview-UNASSIGNED.log'
~~~~

</details>

### Refresh Application Descriptor files

When applications are migrated to Git and development teams leverage the pipeline, source files will be modified, added, or deleted.
It is expected that the list of elements composing an application and its cross-applications dependencies change over time.
To reflect these changes, the **Application Descriptor** file needs to be refreshed.

Additionally, if applications are already migrated to Git and use pipelines, but don't have an Application Descriptor file yet, and the development teams want to leverage its benefits, this creation process should be followed.

A second primary command is shipped for this workflow. The [Refresh Application Descriptor script (Refresh-Application-Descriptor-Files.sh)](./src/scripts/Refresh-Application-Descriptor-Files.sh) facilitates the refresh process by rescanning the source code, initializing new or resetting the Application Descriptor files, and performing the assessment phase for all applications. The refresh of the Application Descriptor files must occur on the entire code base like on the initial assessment process.

Like the other scripts, it requires the path to the DBB Git Migration Modeler configuration file as parameter. This configuration file can be created with the [Setup](#setup-dbb-git-migration-modeler-configuration) instructions.

The main script calls three groovy scripts ([scanApplication.groovy](./src/groovy/scanApplication.groovy), [recreateApplicationDescriptor.groovy](./src/groovy/recreateApplicationDescriptor.groovy) and [assessUsage.groovy](./src/groovy/assessUsage.groovy)) to scan the files of the applications using the DBB Scanner, initialize Application Descriptor files based on the files present in the working directories, and assess how Include Files and Programs are used across the applications landscape:

   * For the scanning phase, the script iterates through the files located within the work directory.
   It uses the DBB Scanner to understand the dependencies for each artifact.
   This information is stored in a local, temporary DBB metadatastore on the UNIX System Sservices filesystem, that holds the dependencies information.

   * In the second phase, the Application Descriptor files are initialized.
   If an Application Descriptor is found, the source groups and dependencies/consumers information are reset.
   If no Application Descriptor is found, a new one is created.
   For each Application Descriptor, the present files in the working folders are documented and grouped according to the `RepositoryPathsMapping.yaml` file.
   If no mapping is found, the files are added into the Application Descriptor with default values based on the low-level qualifier of the containing dataset.

   * The third phase of the process uses the dependency information to understand how Include Files and Programs are used across all applications. It then classifies the Include Files in three categories (Private, Public or Shared) and Programs in three categories (main, internal submodule, service submodule) and updates the Application Descriptor accordingly.

#### Outputs

For each application, a refreshed Application Descriptor is created at the root directory of the application's folder in z/OS UNIX System Services.

#### Recommended usage

Recreating the Application Descriptor files requires to scan all files and might be time and resource consuming based on the size of the applications landscape.
Consider using this process on a regular basis, like once a week.
The recommendation would be to set up a pipeline, that checks out all Git repositories to a working sandbox, and executes the `Recreate Application Descriptor` script. Once the Application Descriptor files updated within the Git repositories, the pipeline can be enabled to automatically commit and push the updates back to the central Git provider.

<details>
  <summary>Output example</summary>
Execution of command:
	
`./src/scripts/Refresh-Application-Descriptor-Files.sh -c /u/mdalbin/Migration-Modeler-DBEHM-work/DBB_GIT_MIGRATION_MODELER.config`

Output log:
~~~~

 DBB Git Migration Modeler
 Release:

 Script:      refreshApplicationDescriptorFiles.sh

 Description: The purpose of this script is to help keeping the Application Descriptor files of existing
              applications up-to-date. The script scans the artifacts belonging to the application,
              removes existing source groups from the Application Descriptor files and run
              the usage assessment process again to populate the Application Descriptor files correctly.
              The script inspects all folders within the referenced 'DBB_MODELER_APPLICATIONS' directory.

              You must customize the process to your needs if you want to update the Application Descriptor
              files of applications that are already migrated to a central Git provider.
              For more information please refer to:    https://github.com/IBM/dbb-git-migration-modeler

[INFO] Initializing DBB Metadatastore at /u/mdalbin/Migration-Modeler-DBEHM-work/dbb-metadatastore.
*******************************************************************
Scan application directory /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA
*******************************************************************
** Script configuration:
   workspace -> /u/mdalbin/Migration-Modeler-DBEHM-work/applications
   metadatastore -> /u/mdalbin/Migration-Modeler-DBEHM-work/dbb-metadatastore
   application -> CBSA
   logFile -> /u/mdalbin/Migration-Modeler-DBEHM-work/logs/3-CBSA-scan.log
   dbb.DependencyScanner.controlTransfers -> true
** Scanning the files.
         Scanning file CBSA/CBSA/src/cobol/bnk1cca.cbl
         Scanning file CBSA/CBSA/src/cobol/updcust.cbl
         Scanning file CBSA/.git/logs/HEAD
         Scanning file CBSA/CBSA/src/copy/bnk1dcm.cpy
         Scanning file CBSA/CBSA/src/cobol/bnk1ccs.cbl
         Scanning file CBSA/CBSA/application-conf/ZunitConfig.properties
         Scanning file CBSA/CBSA/src/copy/sortcode.cpy
         Scanning file CBSA/CBSA/application-conf/file.properties
         Scanning file CBSA/CBSA/src/copy/custctrl.cpy
         Scanning file CBSA/.git/objects/a1/4465df829b167bbb644dffc1027434adbf3c32
         Scanning file CBSA/.git/objects/f6/3ebe51d5520bc56b0a6911cfc2ed6705fdfa66
         Scanning file CBSA/.git/objects/47/f9f61e0fdb34ee5ebbf7fc11529e50b079a04b
         Scanning file CBSA/.git/objects/4a/58fdbf3761bccd3497ada688d343a15c33e8b0
         Scanning file CBSA/.git/objects/e3/df501f6a5529aff989412d6c4af4b43a9897d1
         Scanning file CBSA/.git/objects/e4/96c6a4e7a960de791e1fd97a02ae6614769936
         Scanning file CBSA/.git/objects/b6/deb95fdbfe6a2f08acb265c23cccc973e8b031
         Scanning file CBSA/CBSA/src/copy/delacc.cpy
         Scanning file CBSA/.git/refs/heads/main
         Scanning file CBSA/CBSA/src/cobol/dpayapi.cbl
         Scanning file CBSA/.git/objects/cb/75236314e2fba04aca378ad29061942e6900a5
         Scanning file CBSA/.git/objects/57/a7db352970bbfae82cf24c95aa6cecc159b0e0
         Scanning file CBSA/.git/hooks/pre-applypatch.sample
         Scanning file CBSA/.git/objects/b1/7e73e90052cbe5144318dc9cf00cdf04589042
         Scanning file CBSA/CBSA/src/copy/constapi.cpy
         Scanning file CBSA/.git/objects/5e/014abb1c1c7b87e5b7487894a0dd577ecd6903
         Scanning file CBSA/CBSA/src/copy/bnk1cam.cpy
         Scanning file CBSA/.git/objects/6f/3549f765104b58d630d2a4ce871fc1b9e4bb7a
         Scanning file CBSA/CBSA/src/cobol/consttst.cbl
         Scanning file CBSA/.git/objects/de/ce936b7a48fba884a6d376305fbce1a2fc99e5
         Scanning file CBSA/CBSA/src/cobol/crdtagy3.cbl
         Scanning file CBSA/.git/objects/94/7a658dffaf7b8a8a1348ad9dabbdca1f87fbb0
         Scanning file CBSA/CBSA/src/cobol/delcus.cbl
         Scanning file CBSA/CBSA/src/cobol/accoffl.cbl
         Scanning file CBSA/CBSA/src/copy/updacc.cpy
         Scanning file CBSA/.git/hooks/post-update.sample
         Scanning file CBSA/.git/objects/30/ec95859415287a39af962b759792828e403684
         Scanning file CBSA/CBSA/src/cobol/accload.cbl
         Scanning file CBSA/CBSA/application-conf/Transfer.properties
         Scanning file CBSA/.git/objects/b4/79ed3b38c3f9680850dc34a3c9d10e24ddb52f
         Scanning file CBSA/CBSA/src/copy/bnk1ccm.cpy
         Scanning file CBSA/CBSA/application-conf/Cobol.properties
         Scanning file CBSA/.git/objects/d9/7584fe7d7c5e0120ab762194b119287f6bc91d
         Scanning file CBSA/.git/objects/66/afa88844c422af69da0d35243993d4e50dac3c
         Scanning file CBSA/CBSA/application-conf/CRB.properties
         Scanning file CBSA/CBSA/src/copy/customer.cpy
         Scanning file CBSA/CBSA/src/copy/creacc.cpy
         Scanning file CBSA/CBSA/application-conf/languageConfigurationMapping.properties
         Scanning file CBSA/.git/objects/46/3a5519cbcb1b8db463d628173cafc3751fb323
         Scanning file CBSA/.git/objects/f5/5399eea902ae9bc01584c1e3bc71f4db98eef6
         Scanning file CBSA/.git/objects/04/9cc7eb352d85ce38026a8f3029f22e711b8b9a
         Scanning file CBSA/CBSA/src/copy/bnk1acc.cpy
         Scanning file CBSA/CBSA/src/copy/bnk1uam.cpy
         Scanning file CBSA/CBSA/src/cobol/abndproc.cbl
         Scanning file CBSA/.git/HEAD
         Scanning file CBSA/.git/objects/04/a5b554ae15152a060f462fe894e09e7188e394
         Scanning file CBSA/.git/objects/55/57d232d69aa70962e5580123403d3662157e2a
         Scanning file CBSA/CBSA/application-conf/MFS.properties
         Scanning file CBSA/.git/index
         Scanning file CBSA/CBSA/src/copy/abndinfo.cpy
         Scanning file CBSA/CBSA/src/copy/xfrfun.cpy
         Scanning file CBSA/.git/objects/01/d96e12b164d97cc7f2c72489c8cd3205a8b69f
         Scanning file CBSA/CBSA/application-conf/PSBgen.properties
         Scanning file CBSA/CBSA/application-conf/Easytrieve.properties
         Scanning file CBSA/.git/hooks/pre-commit.sample
         Scanning file CBSA/.git/objects/1d/7f5fcdba85d4c4d0bc6ab0bab4b287e69242db
         Scanning file CBSA/.git/objects/d3/70465392addcb5a86920019826deec0e531a77
         Scanning file CBSA/CBSA/src/copy/getcompy.cpy
         Scanning file CBSA/CBSA/src/cobol/delacc.cbl
         Scanning file CBSA/CBSA/application-conf/REXX.properties
         Scanning file CBSA/.git/hooks/pre-merge-commit.sample
         Scanning file CBSA/.git/hooks/fsmonitor-watchman.sample
         Scanning file CBSA/.git/objects/89/7bf2e97ca69ede559524c31bae8d639ae1b81d
         Scanning file CBSA/.git/objects/7e/0340c01a352c55eaf478a5c7dbe8c290e50728
         Scanning file CBSA/.git/objects/24/79cd7afe658ecc8801d10f9f8cb42382d53d16
         Scanning file CBSA/CBSA/src/copy/bnk1cdm.cpy
         Scanning file CBSA/CBSA/src/cobol/dpaytst.cbl
         Scanning file CBSA/.git/objects/71/aba7981c900888d8f74ef1f3aa3e1efe91d405
         Scanning file CBSA/.git/objects/ff/86efc8e05a7fc5e66defbf50820da4ab3bad95
         Scanning file CBSA/CBSA/src/cobol/bnk1cra.cbl
         Scanning file CBSA/CBSA/src/cobol/prooffl.cbl
         Scanning file CBSA/.git/objects/f7/f461db942e85d137f33609bdb50bd26015d1ec
         Scanning file CBSA/.git/objects/94/08dd2f2709f23766aa4d1ef89e6e175974b396
         Scanning file CBSA/CBSA/src/cobol/updacc.cbl
         Scanning file CBSA/CBSA/src/copy/acctctrl.cpy
         Scanning file CBSA/.git/objects/c9/5be47dd3ede400e93ba367b5f5ac433a714d5a
         Scanning file CBSA/.git/objects/fb/741632c192243a1f4e7799371635f854bd40db
         Scanning file CBSA/CBSA/src/copy/delcus.cpy
         Scanning file CBSA/.git/objects/c0/6aacd0c94d044b5fb1d2cb22bc796b946bcf6f
         Scanning file CBSA/.git/objects/ab/80f99d7e1e2cf005e04f11f43b710b6cfc765c
         Scanning file CBSA/CBSA/src/copy/proctran.cpy
         Scanning file CBSA/.git/objects/9d/8cdd3cfd001f9ff47534b9a741f61f757cc90c
         Scanning file CBSA/CBSA/src/copy/getscode.cpy
         Scanning file CBSA/CBSA/src/cobol/creacc.cbl
         Scanning file CBSA/CBSA/src/cobol/crdtagy5.cbl
         Scanning file CBSA/CBSA/src/copy/account.cpy
         Scanning file CBSA/.git/objects/f7/fbe29970a3bd547fcfd6e82df58e45190d46a8
         Scanning file CBSA/.git/objects/b0/aed0954293fc2763f3c02ec65cbaa53603015d
         Scanning file CBSA/CBSA/src/copy/paydbcr.cpy
         Scanning file CBSA/.git/objects/2f/bc2fdb9097a629e3d0d899d0d4912a5ce4a678
         Scanning file CBSA/CBSA/src/cobol/getcompy.cbl
         Scanning file CBSA/.git/hooks/commit-msg.sample
         Scanning file CBSA/.git/objects/c8/6c28e6b894571ccad1c6beaa040d1b916a1a77
         Scanning file CBSA/.git/hooks/update.sample
         Scanning file CBSA/CBSA/src/copy/bnk1mai.cpy
         Scanning file CBSA/.git/objects/b1/8656b5144b139b6a3b4515d4883a5d0e9ee2ce
         Scanning file CBSA/.git/objects/68/c29e32bba41130b5f6308b06ffbaf11d7214cc
         Scanning file CBSA/.git/objects/b6/97ad559100281f7737764166ced34b4398ae0d
         Scanning file CBSA/.git/objects/da/97ba1be5273d4a3265d6fdffd68c4a9cfe57f1
         Scanning file CBSA/CBSA/src/cobol/bnk1uac.cbl
         Scanning file CBSA/.git/objects/b0/2d733e80ba87c613c4becba1438cfea345bb63
         Scanning file CBSA/.git/refs/tags/rel-1.0.0
         Scanning file CBSA/.git/objects/99/a8f2520e0dc26a905446e52245f7b6314133d9
         Scanning file CBSA/.git/objects/33/44cbdf7b601794f0ef2341235f09f126fe4562
         Scanning file CBSA/CBSA/application-conf/DBDgen.properties
         Scanning file CBSA/CBSA/src/cobol/getscode.cbl
         Scanning file CBSA/.git/objects/f4/33cbfff90207efad95d399c2632acc1684f942
         Scanning file CBSA/CBSA/src/copy/contdb2.cpy
         Scanning file CBSA/CBSA/src/cobol/bnk1cac.cbl
         Scanning file CBSA/.git/objects/37/1a19b8d93fa4d1f491a4174865ff3b5dc57b6f
         Scanning file CBSA/CBSA/src/cobol/xfrfun.cbl
         Scanning file CBSA/CBSA/src/copy/consent.cpy
         Scanning file CBSA/.git/objects/21/b32b59cad6603ee75673876be89e6c04c4c122
         Scanning file CBSA/CBSA/application-conf/PLI.properties
         Scanning file CBSA/.git/objects/c2/432e4bf3b85f883fdcaff1adb419b1ebf3fd18
         Scanning file CBSA/.git/COMMIT_EDITMSG
         Scanning file CBSA/CBSA/src/cobol/crdtagy1.cbl
         Scanning file CBSA/.git/hooks/sendemail-validate.sample
         Scanning file CBSA/CBSA/src/cobol/bankdata.cbl
         Scanning file CBSA/CBSA/src/cobol/crecust.cbl
         Scanning file CBSA/CBSA.yaml
         Scanning file CBSA/.git/objects/78/c46a8b3d2f9bf33608f9ebaa1ae56260a546b2
         Scanning file CBSA/.git/objects/3e/aad50b56f466377be9bc01dca2e4188e888f53
         Scanning file CBSA/.git/objects/8e/b541c571cd537e557c27e56eb472e9cafb0308
         Scanning file CBSA/.git/hooks/applypatch-msg.sample
         Scanning file CBSA/.git/objects/97/0f6a926b868353d6a285d20b07d29abfba4292
         Scanning file CBSA/CBSA/application-conf/Assembler.properties
         Scanning file CBSA/.git/objects/f5/0cc01256b3b2f272a59bed37caeb1a61f5ba4c
         Scanning file CBSA/.git/objects/d3/7d2d4704218babc4ab9871cc3ea1f5271dc80d
         Scanning file CBSA/.git/objects/b2/849d92d4dd7bd253384f910a069f98802f64f1
         Scanning file CBSA/.git/objects/d4/c22ba5bfb0742e2395037184f5fc4174577a8c
         Scanning file CBSA/.git/objects/a6/ee2080f7c783724cafee89a81049a3f2893e75
         Scanning file CBSA/.git/objects/b5/6eafbe98c4e46afb0c8c60ee97cf437292a68c
         Scanning file CBSA/.gitattributes
         Scanning file CBSA/CBSA/src/copy/datastr.cpy
         Scanning file CBSA/CBSA/application-conf/application.properties
         Scanning file CBSA/CBSA/src/cobol/crdtagy4.cbl
         Scanning file CBSA/.git/objects/ff/7f1a74d6d78a6d35e4559b32cdff813a5fb12e
         Scanning file CBSA/.git/objects/14/833274735adb257e1062eaa63d495febe9e962
         Scanning file CBSA/.git/objects/2a/d1a2ba3dc994398cbf308b3e6bdb30db9c1f1b
         Scanning file CBSA/.git/objects/a7/e4ad4c1bde8c6ad9144199468403799cdd0e26
         Scanning file CBSA/CBSA/application-conf/bind.properties
         Scanning file CBSA/.git/config
         Scanning file CBSA/.git/objects/b8/33431450f198af575ebdf622a8144df7c0962a
         Scanning file CBSA/.git/objects/33/4b8f087b5e1bd5c05036a920378e8e1f3c0276
         Scanning file CBSA/CBSA/src/cobol/inqacc.cbl
         Scanning file CBSA/CBSA/src/cobol/bnk1dac.cbl
         Scanning file CBSA/.git/objects/82/14b4cdd014e9e1f1c45fae193c49364def5894
         Scanning file CBSA/CBSA/src/copy/crecust.cpy
         Scanning file CBSA/CBSA/application-conf/LinkEdit.properties
         Scanning file CBSA/.git/objects/d9/c46c2b0b76ac752b67f451dd45995cd5bc96d1
         Scanning file CBSA/CBSA/src/cobol/dbcrfun.cbl
         Scanning file CBSA/.git/objects/84/bc44ed9738bc69291a529f9b7b7a1b3cccdc88
         Scanning file CBSA/.git/info/exclude
         Scanning file CBSA/.git/hooks/pre-receive.sample
         Scanning file CBSA/.git/objects/56/eec383e79ddc7d93386976ba31b6f06180c1a0
         Scanning file CBSA/CBSA/src/cobol/acctctrl.cbl
         Scanning file CBSA/CBSA/src/copy/procdb2.cpy
         Scanning file CBSA/CBSA/application-conf/ACBgen.properties
         Scanning file CBSA/.git/objects/bb/6a183c5808c83f435ffe292d40ce3c1e78182e
         Scanning file CBSA/.git/objects/4d/3bc5c5136e4bfe98ceb8e5f5136b421afd8596
         Scanning file CBSA/CBSA/application-conf/reports.properties
         Scanning file CBSA/.git/objects/fa/7a23ca781e7e8e7afa7d20dc2caaf6ebba38dc
         Scanning file CBSA/.git/objects/34/390dbd6e6f281f6101d179897949a51393c264
         Scanning file CBSA/.git/objects/e4/a208249eb9f188dac631a80aa69560a1b5c812
         Scanning file CBSA/CBSA/src/cobol/inqcust.cbl
         Scanning file CBSA/.git/objects/27/0fd7eb4a2109c25b62d78595d8ddd044de4983
         Scanning file CBSA/.git/hooks/push-to-checkout.sample
         Scanning file CBSA/CBSA/src/copy/constdb2.cpy
         Scanning file CBSA/.git/hooks/prepare-commit-msg.sample
         Scanning file CBSA/CBSA/src/cobol/consent.cbl
         Scanning file CBSA/CBSA/src/cobol/crdtagy2.cbl
         Scanning file CBSA/.git/objects/12/c04ff4762844463e6e8d5b3a92c150fbb3c6ce
         Scanning file CBSA/.git/objects/31/2d56358b0f4597312ad7d68b78ebd080fc11f5
         Scanning file CBSA/.git/objects/b8/cea7df2b43bfac6d4e9336022a286e44a1147c
         Scanning file CBSA/CBSA/src/copy/inqacccu.cpy
         Scanning file CBSA/CBSA/src/cobol/bnk1tfn.cbl
         Scanning file CBSA/.git/hooks/pre-push.sample
         Scanning file CBSA/.git/objects/2b/b5e69e60b48517664e8bc178ce5047d2dc6239
         Scanning file CBSA/.git/objects/57/9fef02baff9b735fc28867aef660f088b64710
         Scanning file CBSA/.git/logs/refs/heads/main
         Scanning file CBSA/CBSA/src/cobol/proload.cbl
         Scanning file CBSA/CBSA/src/cobol/inqacccu.cbl
         Scanning file CBSA/.git/objects/71/95a42c31f86e0f70315660d9da6d62f9769d1e
         Scanning file CBSA/.git/objects/9c/3aec3ef67cd80287d375f825fe1b7abfb8be4d
         Scanning file CBSA/.git/objects/29/ef69588ebc7fb77045dc42407df52eb89b771b
         Scanning file CBSA/.git/objects/1c/b8db96a22a09cba20ddf3d7bb37fb098963100
         Scanning file CBSA/.git/objects/1e/cc8a7b26eee8c6498737ad40975ca9597e7809
         Scanning file CBSA/CBSA/src/copy/updcust.cpy
         Scanning file CBSA/.git/objects/40/46a14e3b7f9b0137176c8039e1034e9e8c39fd
         Scanning file CBSA/.git/objects/aa/3a09c5ec672fef16b4d689127e80ca5ce595ce
         Scanning file CBSA/.git/description
         Scanning file CBSA/.git/objects/6e/ba9fb7a278153965978bde08e8b79d7549a6e5
         Scanning file CBSA/CBSA/src/copy/bnk1dam.cpy
         Scanning file CBSA/.git/objects/69/27d3b72033e6e7e4f9d6527fb5d347e1fc67d4
         Scanning file CBSA/.git/objects/35/1b0c08fb96d69ec8f2e5c4a71121da780037dd
         Scanning file CBSA/.git/objects/d3/e104ac3f1682cf5c81e6a4df77a916b5601adb
         Scanning file CBSA/.git/objects/fa/3508648b495e92bc320f8110bfd3d78a4d5a3a
         Scanning file CBSA/CBSA/src/cobol/custctrl.cbl
         Scanning file CBSA/CBSA/src/copy/accdb2.cpy
         Scanning file CBSA/CBSA/application-conf/BMS.properties
         Scanning file CBSA/CBSA/src/copy/inqacc.cpy
         Scanning file CBSA/.git/objects/c8/82661ae39a9a8ed30486a8433c1b186cbc5159
         Scanning file CBSA/.git/hooks/pre-rebase.sample
         Scanning file CBSA/CBSA/src/cobol/bnk1dcs.cbl
         Scanning file CBSA/.git/objects/74/7f6becab8f715c30726f0adc2777d4e775a513
         Scanning file CBSA/.git/objects/bc/ecf21e6187f0d2dba5c129c53954a8363f0d0e
         Scanning file CBSA/.git/objects/41/c1fc24c5c355423d1cdad4477113b6c6f0945f
         Scanning file CBSA/.git/objects/56/364507a259c6881a4e9a961213a9aa5a6405e7
         Scanning file CBSA/CBSA/src/cobol/bnkmenu.cbl
         Scanning file CBSA/CBSA/application-conf/README.md
         Scanning file CBSA/.git/objects/d3/f1290864542e156079c4e165497994f1675139
         Scanning file CBSA/.git/objects/d0/c5bf02bc846be691c4ea906c10118719d3bed3
         Scanning file CBSA/CBSA/src/copy/inqcust.cpy
         Scanning file CBSA/.git/objects/02/20c1299e5ed367b9d602d8a11c9909a081c026
         Scanning file CBSA/CBSA/src/copy/bnk1tfm.cpy
** Storing results in the 'CBSA' DBB Collection.
** Build finished
*******************************************************************
Scan application directory /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp
*******************************************************************
** Script configuration:
   workspace -> /u/mdalbin/Migration-Modeler-DBEHM-work/applications
   metadatastore -> /u/mdalbin/Migration-Modeler-DBEHM-work/dbb-metadatastore
   application -> GenApp
   logFile -> /u/mdalbin/Migration-Modeler-DBEHM-work/logs/3-GenApp-scan.log
   dbb.DependencyScanner.controlTransfers -> true
** Scanning the files.
         Scanning file GenApp/.git/objects/82/14b4cdd014e9e1f1c45fae193c49364def5894
         Scanning file GenApp/.git/objects/2e/f0cfc9de9ca7521899a87cf9e216be7f109d88
         Scanning file GenApp/GenApp/src/cobol/lgdpdb01.cbl
         Scanning file GenApp/.git/refs/heads/main
         Scanning file GenApp/.git/index
         Scanning file GenApp/GenApp/application-conf/Assembler.properties
         Scanning file GenApp/.git/objects/a7/e4ad4c1bde8c6ad9144199468403799cdd0e26
         Scanning file GenApp/.git/objects/7d/f90877fb98ccba6508a94e6fe3ff1ad865d682
         Scanning file GenApp/.git/logs/refs/heads/main
         Scanning file GenApp/GenApp/src/copy/lgpolicy.cpy
         Scanning file GenApp/.git/objects/3e/aad50b56f466377be9bc01dca2e4188e888f53
         Scanning file GenApp/GenApp.yaml
         Scanning file GenApp/.git/objects/d1/e33757aa74694d0039e8162918a840172d24f8
         Scanning file GenApp/GenApp/src/copy/lgcmarea.cpy
         Scanning file GenApp/GenApp/src/cobol/lgacdb01.cbl
         Scanning file GenApp/GenApp/src/cobol/lgupvs01.cbl
         Scanning file GenApp/GenApp/src/cobol/lgtestp1.cbl
         Scanning file GenApp/.git/objects/69/27d3b72033e6e7e4f9d6527fb5d347e1fc67d4
         Scanning file GenApp/.git/objects/da/97ba1be5273d4a3265d6fdffd68c4a9cfe57f1
         Scanning file GenApp/.gitattributes
         Scanning file GenApp/.git/objects/bf/a3623bc647efd22c9550939cd8d5bf72cb91ad
         Scanning file GenApp/GenApp/src/cobol/lgapol01.cbl
         Scanning file GenApp/.git/objects/7e/36d0d65c7ae8ca0ce7a451692820010cf2c51f
         Scanning file GenApp/.git/objects/42/d3f2e669c2f9f6cf9565e61b2a3f96ad1ff503
         Scanning file GenApp/.git/objects/17/cd1d6b0325b04277c7fc7a1ec27ce9bcbd2598
         Scanning file GenApp/.git/objects/d8/f18d43e8afa308163aebcff561e7dedf67759e
         Scanning file GenApp/.git/objects/b8/0c506efef3e434fe92e8395a063bfb1d87b5f3
         Scanning file GenApp/.git/objects/b0/49dc9735257281c334afd74730dee59c62e2e8
         Scanning file GenApp/GenApp/src/cobol/lgdpvs01.cbl
         Scanning file GenApp/.git/objects/89/20ce0008397665b02a79f971898c033709bc6b
         Scanning file GenApp/.git/objects/c5/ea6c1fed91fd2154ac3f38533455da5481d974
         Scanning file GenApp/GenApp/src/cobol/lgipvs01.cbl
         Scanning file GenApp/GenApp/application-conf/Cobol.properties
         Scanning file GenApp/GenApp/src/cobol/lgacus01.cbl
         Scanning file GenApp/GenApp/src/cobol/lgtestp3.cbl
         Scanning file GenApp/GenApp/application-conf/DBDgen.properties
         Scanning file GenApp/GenApp/application-conf/CRB.properties
         Scanning file GenApp/.git/objects/1e/cc8a7b26eee8c6498737ad40975ca9597e7809
         Scanning file GenApp/.git/objects/78/c46a8b3d2f9bf33608f9ebaa1ae56260a546b2
         Scanning file GenApp/GenApp/application-conf/application.properties
         Scanning file GenApp/.git/objects/de/85d8fbe9f576dabc377e29616bc4e8fcf68a56
         Scanning file GenApp/.git/description
         Scanning file GenApp/.git/objects/16/73ab0e7f0e1744ab58379576e6c835d4108474
         Scanning file GenApp/.git/objects/6e/a11cb2dc20aa126f08701fe873ac2dae5ce0b6
         Scanning file GenApp/.git/logs/HEAD
         Scanning file GenApp/.git/objects/98/11fa56e0556c5d884a98ae06f7d007f64edafa
         Scanning file GenApp/.git/hooks/commit-msg.sample
         Scanning file GenApp/.git/objects/ed/7e8c1b79aaa76736f0af3b735f667d3d26ad36
         Scanning file GenApp/GenApp/application-conf/BMS.properties
         Scanning file GenApp/GenApp/src/cobol/lgipdb01.cbl
         Scanning file GenApp/.git/objects/22/b550bafdc6e9f5103b1a28ca501d6bdae4ec76
         Scanning file GenApp/GenApp/application-conf/Transfer.properties
         Scanning file GenApp/.git/objects/b6/53161403e5df737d6e540d8c5a1988a043eafc
         Scanning file GenApp/.git/hooks/pre-merge-commit.sample
         Scanning file GenApp/.git/hooks/sendemail-validate.sample
         Scanning file GenApp/.git/COMMIT_EDITMSG
         Scanning file GenApp/.git/objects/b2/849d92d4dd7bd253384f910a069f98802f64f1
         Scanning file GenApp/GenApp/application-conf/bind.properties
         Scanning file GenApp/.git/objects/48/cd97eb3d38cc15a850ed45ddfe76c7d3f6c7da
         Scanning file GenApp/GenApp/src/cobol/lgacdb02.cbl
         Scanning file GenApp/GenApp/src/cobol/lgipol01.cbl
         Scanning file GenApp/GenApp/src/cobol/lgapvs01.cbl
         Scanning file GenApp/GenApp/application-conf/ZunitConfig.properties
         Scanning file GenApp/GenApp/application-conf/MFS.properties
         Scanning file GenApp/.git/hooks/applypatch-msg.sample
         Scanning file GenApp/GenApp/application-conf/LinkEdit.properties
         Scanning file GenApp/.git/objects/d4/24e6a718eb9ad584e21f7a899488500484f7e2
         Scanning file GenApp/GenApp/src/cobol/lgupdb01.cbl
         Scanning file GenApp/.git/objects/12/5b26f553c5647a5aabc69a45f0191aed5d5e01
         Scanning file GenApp/GenApp/src/cobol/lgstsq.cbl
         Scanning file GenApp/GenApp/application-conf/PLI.properties
         Scanning file GenApp/GenApp/application-conf/Easytrieve.properties
         Scanning file GenApp/.git/hooks/pre-applypatch.sample
         Scanning file GenApp/.git/hooks/fsmonitor-watchman.sample
         Scanning file GenApp/.git/objects/e5/86c7d2e00e602158da102e4c8d30deaeb142ae
         Scanning file GenApp/GenApp/application-conf/README.md
         Scanning file GenApp/GenApp/src/cobol/lgtestp2.cbl
         Scanning file GenApp/GenApp/src/cobol/lgicus01.cbl
         Scanning file GenApp/GenApp/src/cobol/lgucus01.cbl
         Scanning file GenApp/GenApp/src/cobol/lgucvs01.cbl
         Scanning file GenApp/GenApp/src/cobol/lgapdb01.cbl
         Scanning file GenApp/.git/objects/a0/b94e23333057ca37382048c4f7fc6f2e0df75b
         Scanning file GenApp/GenApp/src/cobol/lgicvs01.cbl
         Scanning file GenApp/GenApp/application-conf/languageConfigurationMapping.properties
         Scanning file GenApp/.git/objects/31/2d56358b0f4597312ad7d68b78ebd080fc11f5
         Scanning file GenApp/.git/objects/2a/d1a2ba3dc994398cbf308b3e6bdb30db9c1f1b
         Scanning file GenApp/.git/objects/6b/70ac40882304b17e808848fc61b6c4fd833607
         Scanning file GenApp/GenApp/src/cobol/lgsetup.cbl
         Scanning file GenApp/.git/objects/24/79cd7afe658ecc8801d10f9f8cb42382d53d16
         Scanning file GenApp/.git/objects/f7/f461db942e85d137f33609bdb50bd26015d1ec
         Scanning file GenApp/.git/objects/1b/9d6bcb233214bd016ac6ffd87d5b4e5a0644cc
         Scanning file GenApp/GenApp/application-conf/REXX.properties
         Scanning file GenApp/.git/config
         Scanning file GenApp/GenApp/src/cobol/lgdpol01.cbl
         Scanning file GenApp/.git/objects/84/bc44ed9738bc69291a529f9b7b7a1b3cccdc88
         Scanning file GenApp/.git/hooks/update.sample
         Scanning file GenApp/.git/objects/82/766939ca20dfac5d9ab33782e4f45b2ade19fc
         Scanning file GenApp/.git/objects/0a/f04c7e31314b30efc3600507f62bfd1c2970c9
         Scanning file GenApp/.git/hooks/pre-receive.sample
         Scanning file GenApp/.git/hooks/pre-push.sample
         Scanning file GenApp/.git/objects/3e/9eed6daafd969231900049360b526396bf4091
         Scanning file GenApp/GenApp/src/copy/lgcmared.cpy
         Scanning file GenApp/.git/objects/fa/ffcce01f2da721aa453f5dda21d11f8d3ae693
         Scanning file GenApp/.git/hooks/push-to-checkout.sample
         Scanning file GenApp/.git/objects/28/2aa20f6c7d61d15b8922c8d8e0552880351472
         Scanning file GenApp/GenApp/src/cobol/lgtestp4.cbl
         Scanning file GenApp/.git/hooks/post-update.sample
         Scanning file GenApp/.git/objects/76/be470b4b4450038992dec6a9f9ac90a8611f2b
         Scanning file GenApp/GenApp/src/cobol/lgupol01.cbl
         Scanning file GenApp/GenApp/src/cobol/lgastat1.cbl
         Scanning file GenApp/.git/hooks/pre-commit.sample
         Scanning file GenApp/.git/objects/3e/8c9c7714c8622b1fe6077544b2b535dc3d0330
         Scanning file GenApp/.git/hooks/pre-rebase.sample
         Scanning file GenApp/.git/objects/3b/6b75b7fd2f100934f2ae236cbff5a174454de2
         Scanning file GenApp/.git/objects/37/0f90c505893d5ab01089e66e04528f8d40dab1
         Scanning file GenApp/.git/objects/68/5e0f68143caf8974be751db42bc6f6869e3af9
         Scanning file GenApp/.git/HEAD
         Scanning file GenApp/.git/objects/d9/455ae3c356b0e7a2440914f564ddbcbe30e28d
         Scanning file GenApp/.git/objects/b8/cea7df2b43bfac6d4e9336022a286e44a1147c
         Scanning file GenApp/.git/objects/db/2a6d69779b37f2aff873868afb262ed063d27d
         Scanning file GenApp/.git/objects/17/4119c31e4008790ec424427596d0859d696c96
         Scanning file GenApp/GenApp/application-conf/PSBgen.properties
         Scanning file GenApp/.git/objects/e1/52fbd8c03e836ad0046953854f04b4665d75b9
         Scanning file GenApp/.git/objects/f7/09ff109986301f101a1912b9d043756d7e596a
         Scanning file GenApp/.git/objects/68/c29e32bba41130b5f6308b06ffbaf11d7214cc
         Scanning file GenApp/GenApp/src/cobol/lgtestc1.cbl
         Scanning file GenApp/.git/objects/99/a8f2520e0dc26a905446e52245f7b6314133d9
         Scanning file GenApp/.git/objects/0d/b601b1f055ea023e104c7d24ab0ef5eea1ff05
         Scanning file GenApp/.git/hooks/prepare-commit-msg.sample
         Scanning file GenApp/GenApp/src/bms/ssmap.bms
         Scanning file GenApp/.git/info/exclude
         Scanning file GenApp/GenApp/src/cobol/lgucdb01.cbl
         Scanning file GenApp/GenApp/application-conf/reports.properties
         Scanning file GenApp/.git/objects/83/2f54aa68fe84f78461085d00e3b3206e39fdb7
         Scanning file GenApp/.git/objects/9a/a1e257384925e8015d7e0864175961ce258290
         Scanning file GenApp/GenApp/src/cobol/lgicdb01.cbl
         Scanning file GenApp/.git/objects/28/0e6f742c84b40da642115cad3a0c86aa9c0aac
         Scanning file GenApp/.git/objects/1c/b8db96a22a09cba20ddf3d7bb37fb098963100
         Scanning file GenApp/.git/refs/tags/rel-1.0.0
         Scanning file GenApp/GenApp/src/cobol/lgacvs01.cbl
         Scanning file GenApp/.git/objects/d3/f1290864542e156079c4e165497994f1675139
         Scanning file GenApp/.git/objects/40/46a14e3b7f9b0137176c8039e1034e9e8c39fd
         Scanning file GenApp/.git/objects/78/e7f1d24d01d4949e80fc149026a9d902eac1b9
         Scanning file GenApp/GenApp/src/cobol/lgwebst5.cbl
         Scanning file GenApp/GenApp/application-conf/ACBgen.properties
         Scanning file GenApp/GenApp/application-conf/file.properties
** Storing results in the 'GenApp' DBB Collection.
** Build finished
*******************************************************************
Scan application directory /u/mdalbin/Migration-Modeler-DBEHM-work/applications/RetirementCalculator
*******************************************************************
** Script configuration:
   workspace -> /u/mdalbin/Migration-Modeler-DBEHM-work/applications
   metadatastore -> /u/mdalbin/Migration-Modeler-DBEHM-work/dbb-metadatastore
   application -> RetirementCalculator
   logFile -> /u/mdalbin/Migration-Modeler-DBEHM-work/logs/3-RetirementCalculator-scan.log
   dbb.DependencyScanner.controlTransfers -> true
** Scanning the files.
         Scanning file RetirementCalculator/.git/objects/b8/cea7df2b43bfac6d4e9336022a286e44a1147c
         Scanning file RetirementCalculator/.git/refs/heads/main
         Scanning file RetirementCalculator/.git/objects/87/ff435e7003ef498860dfc30381bc07a03dabd2
         Scanning file RetirementCalculator/.git/hooks/applypatch-msg.sample
         Scanning file RetirementCalculator/.git/refs/tags/rel-1.0.0
         Scanning file RetirementCalculator/.git/description
         Scanning file RetirementCalculator/.git/objects/e3/df501f6a5529aff989412d6c4af4b43a9897d1
         Scanning file RetirementCalculator/.git/objects/da/2a610077413aed3719f8b6cceae7418fea61bf
         Scanning file RetirementCalculator/RetirementCalculator/src/copy/linput.cpy
         Scanning file RetirementCalculator/.git/logs/refs/heads/main
         Scanning file RetirementCalculator/RetirementCalculator/application-conf/application.properties
         Scanning file RetirementCalculator/RetirementCalculator.yaml
         Scanning file RetirementCalculator/.git/objects/68/c29e32bba41130b5f6308b06ffbaf11d7214cc
         Scanning file RetirementCalculator/RetirementCalculator/application-conf/bind.properties
         Scanning file RetirementCalculator/RetirementCalculator/application-conf/reports.properties
         Scanning file RetirementCalculator/RetirementCalculator/application-conf/Assembler.properties
         Scanning file RetirementCalculator/.git/objects/48/7e49969b9764ca1f1f6e4a8e100aafa634f04b
         Scanning file RetirementCalculator/.git/objects/c9/bcc2e7d175040d35f224a8ec4a9a96fe28e9ca
         Scanning file RetirementCalculator/.git/objects/40/46a14e3b7f9b0137176c8039e1034e9e8c39fd
         Scanning file RetirementCalculator/.git/COMMIT_EDITMSG
         Scanning file RetirementCalculator/.git/config
         Scanning file RetirementCalculator/RetirementCalculator/application-conf/CRB.properties
         Scanning file RetirementCalculator/.gitattributes
         Scanning file RetirementCalculator/RetirementCalculator/application-conf/BMS.properties
         Scanning file RetirementCalculator/RetirementCalculator/application-conf/PSBgen.properties
         Scanning file RetirementCalculator/RetirementCalculator/application-conf/languageConfigurationMapping.properties
         Scanning file RetirementCalculator/.git/hooks/post-update.sample
         Scanning file RetirementCalculator/.git/hooks/pre-receive.sample
         Scanning file RetirementCalculator/.git/hooks/pre-push.sample
         Scanning file RetirementCalculator/.git/objects/b2/849d92d4dd7bd253384f910a069f98802f64f1
         Scanning file RetirementCalculator/.git/objects/f3/2100e3cf13a183e80544d4a0ddd843c8d0d949
         Scanning file RetirementCalculator/.git/objects/1e/cc8a7b26eee8c6498737ad40975ca9597e7809
         Scanning file RetirementCalculator/.git/objects/c3/dbdbc790dc93a9b3e12cd5a220a613c72d0fab
         Scanning file RetirementCalculator/RetirementCalculator/application-conf/LinkEdit.properties
         Scanning file RetirementCalculator/.git/objects/f7/f461db942e85d137f33609bdb50bd26015d1ec
         Scanning file RetirementCalculator/.git/objects/6f/3549f765104b58d630d2a4ce871fc1b9e4bb7a
         Scanning file RetirementCalculator/.git/objects/3e/aad50b56f466377be9bc01dca2e4188e888f53
         Scanning file RetirementCalculator/.git/objects/24/79cd7afe658ecc8801d10f9f8cb42382d53d16
         Scanning file RetirementCalculator/.git/objects/ac/76a910965c68f48767578cd5a5b64957d98a4d
         Scanning file RetirementCalculator/.git/hooks/sendemail-validate.sample
         Scanning file RetirementCalculator/.git/logs/HEAD
         Scanning file RetirementCalculator/.git/hooks/prepare-commit-msg.sample
         Scanning file RetirementCalculator/.git/hooks/update.sample
         Scanning file RetirementCalculator/.git/objects/2a/d1a2ba3dc994398cbf308b3e6bdb30db9c1f1b
         Scanning file RetirementCalculator/RetirementCalculator/application-conf/MFS.properties
         Scanning file RetirementCalculator/.git/hooks/pre-commit.sample
         Scanning file RetirementCalculator/.git/objects/78/c46a8b3d2f9bf33608f9ebaa1ae56260a546b2
         Scanning file RetirementCalculator/RetirementCalculator/application-conf/Easytrieve.properties
         Scanning file RetirementCalculator/.git/hooks/pre-merge-commit.sample
         Scanning file RetirementCalculator/.git/objects/82/14b4cdd014e9e1f1c45fae193c49364def5894
         Scanning file RetirementCalculator/.git/objects/31/2d56358b0f4597312ad7d68b78ebd080fc11f5
         Scanning file RetirementCalculator/.git/objects/4a/58fdbf3761bccd3497ada688d343a15c33e8b0
         Scanning file RetirementCalculator/.git/objects/c8/46ec8770e850c9ebda2cc736d6c65f76d0e74b
         Scanning file RetirementCalculator/RetirementCalculator/application-conf/Cobol.properties
         Scanning file RetirementCalculator/RetirementCalculator/application-conf/ACBgen.properties
         Scanning file RetirementCalculator/.git/objects/69/27d3b72033e6e7e4f9d6527fb5d347e1fc67d4
         Scanning file RetirementCalculator/RetirementCalculator/application-conf/PLI.properties
         Scanning file RetirementCalculator/RetirementCalculator/application-conf/ZunitConfig.properties
         Scanning file RetirementCalculator/.git/HEAD
         Scanning file RetirementCalculator/RetirementCalculator/src/cobol/ebud03.cbl
         Scanning file RetirementCalculator/RetirementCalculator/application-conf/file.properties
         Scanning file RetirementCalculator/.git/info/exclude
         Scanning file RetirementCalculator/RetirementCalculator/application-conf/README.md
         Scanning file RetirementCalculator/.git/objects/a7/e4ad4c1bde8c6ad9144199468403799cdd0e26
         Scanning file RetirementCalculator/.git/objects/ea/ce47f23a335d6ead94dcb74c338a1e1adf65ae
         Scanning file RetirementCalculator/RetirementCalculator/application-conf/DBDgen.properties
         Scanning file RetirementCalculator/.git/hooks/fsmonitor-watchman.sample
         Scanning file RetirementCalculator/.git/hooks/push-to-checkout.sample
         Scanning file RetirementCalculator/.git/hooks/pre-rebase.sample
         Scanning file RetirementCalculator/RetirementCalculator/src/cobol/ebud02.cbl
         Scanning file RetirementCalculator/.git/hooks/commit-msg.sample
         Scanning file RetirementCalculator/RetirementCalculator/application-conf/REXX.properties
         Scanning file RetirementCalculator/RetirementCalculator/application-conf/Transfer.properties
         Scanning file RetirementCalculator/.git/objects/1c/b8db96a22a09cba20ddf3d7bb37fb098963100
         Scanning file RetirementCalculator/.git/hooks/pre-applypatch.sample
         Scanning file RetirementCalculator/.git/objects/da/97ba1be5273d4a3265d6fdffd68c4a9cfe57f1
         Scanning file RetirementCalculator/RetirementCalculator/src/cobol/ebud01.cbl
         Scanning file RetirementCalculator/.git/objects/d3/f1290864542e156079c4e165497994f1675139
         Scanning file RetirementCalculator/RetirementCalculator/src/cobol/ebud0run.cbl
         Scanning file RetirementCalculator/.git/objects/12/0c8e0025fdfa30c48032826c42450988f888a8
         Scanning file RetirementCalculator/.git/objects/79/9226e3830e77a8ecc42283cb31696cb02354b7
         Scanning file RetirementCalculator/.git/objects/57/c0328a170cd985ed20121f8a29719189b3a28f
         Scanning file RetirementCalculator/.git/index
         Scanning file RetirementCalculator/.git/objects/84/bc44ed9738bc69291a529f9b7b7a1b3cccdc88
         Scanning file RetirementCalculator/.git/objects/99/a8f2520e0dc26a905446e52245f7b6314133d9
** Storing results in the 'RetirementCalculator' DBB Collection.
** Build finished
*******************************************************************
Scan application directory /u/mdalbin/Migration-Modeler-DBEHM-work/applications/UNASSIGNED
*******************************************************************
** Script configuration:
   workspace -> /u/mdalbin/Migration-Modeler-DBEHM-work/applications
   metadatastore -> /u/mdalbin/Migration-Modeler-DBEHM-work/dbb-metadatastore
   application -> UNASSIGNED
   logFile -> /u/mdalbin/Migration-Modeler-DBEHM-work/logs/3-UNASSIGNED-scan.log
   dbb.DependencyScanner.controlTransfers -> true
** Scanning the files.
         Scanning file UNASSIGNED/.git/hooks/pre-push.sample
         Scanning file UNASSIGNED/.git/logs/refs/heads/main
         Scanning file UNASSIGNED/.git/objects/1c/b8db96a22a09cba20ddf3d7bb37fb098963100
         Scanning file UNASSIGNED/.git/objects/e3/34046d9c91d6a27d5b73a55fca62038df214e9
         Scanning file UNASSIGNED/UNASSIGNED/src/cobol/oldacdb1.cbl
         Scanning file UNASSIGNED/.git/hooks/push-to-checkout.sample
         Scanning file UNASSIGNED/UNASSIGNED/application-conf/PSBgen.properties
         Scanning file UNASSIGNED/UNASSIGNED/application-conf/ZunitConfig.properties
         Scanning file UNASSIGNED/UNASSIGNED/application-conf/LinkEdit.properties
         Scanning file UNASSIGNED/UNASSIGNED/application-conf/REXX.properties
         Scanning file UNASSIGNED/.git/COMMIT_EDITMSG
         Scanning file UNASSIGNED/.git/objects/6f/3549f765104b58d630d2a4ce871fc1b9e4bb7a
         Scanning file UNASSIGNED/.git/hooks/pre-rebase.sample
         Scanning file UNASSIGNED/UNASSIGNED.yaml
         Scanning file UNASSIGNED/UNASSIGNED/src/bms/epsmlis.bms
         Scanning file UNASSIGNED/.git/objects/a7/e4ad4c1bde8c6ad9144199468403799cdd0e26
         Scanning file UNASSIGNED/.gitattributes
         Scanning file UNASSIGNED/.git/objects/23/8aafb5fbd27ed05fd516e566f9ba78cec0c688
         Scanning file UNASSIGNED/.git/hooks/pre-applypatch.sample
         Scanning file UNASSIGNED/.git/hooks/pre-merge-commit.sample
         Scanning file UNASSIGNED/.git/objects/68/c29e32bba41130b5f6308b06ffbaf11d7214cc
         Scanning file UNASSIGNED/.git/objects/69/27d3b72033e6e7e4f9d6527fb5d347e1fc67d4
         Scanning file UNASSIGNED/.git/objects/82/14b4cdd014e9e1f1c45fae193c49364def5894
         Scanning file UNASSIGNED/.git/objects/20/e05460bad23da4b636a6d07cb06fddcf2434d0
         Scanning file UNASSIGNED/.git/hooks/update.sample
         Scanning file UNASSIGNED/.git/objects/b2/849d92d4dd7bd253384f910a069f98802f64f1
         Scanning file UNASSIGNED/UNASSIGNED/application-conf/Assembler.properties
         Scanning file UNASSIGNED/UNASSIGNED/application-conf/application.properties
         Scanning file UNASSIGNED/.git/hooks/post-update.sample
         Scanning file UNASSIGNED/.git/objects/e3/df501f6a5529aff989412d6c4af4b43a9897d1
         Scanning file UNASSIGNED/UNASSIGNED/application-conf/PLI.properties
         Scanning file UNASSIGNED/UNASSIGNED/application-conf/languageConfigurationMapping.properties
         Scanning file UNASSIGNED/.git/logs/HEAD
         Scanning file UNASSIGNED/UNASSIGNED/src/cobol/flemssub.cbl
         Scanning file UNASSIGNED/UNASSIGNED/application-conf/Transfer.properties
         Scanning file UNASSIGNED/.git/description
         Scanning file UNASSIGNED/.git/objects/a6/3ed1ad7270fd84bfb2eaa77886dc2be44d637e
         Scanning file UNASSIGNED/.git/objects/2a/d1a2ba3dc994398cbf308b3e6bdb30db9c1f1b
         Scanning file UNASSIGNED/.git/objects/70/8fdfbe162a13d3e1af05064b0c41a6077231a4
         Scanning file UNASSIGNED/.git/HEAD
         Scanning file UNASSIGNED/.git/objects/84/bc44ed9738bc69291a529f9b7b7a1b3cccdc88
         Scanning file UNASSIGNED/.git/objects/99/a8f2520e0dc26a905446e52245f7b6314133d9
         Scanning file UNASSIGNED/UNASSIGNED/src/cobol/oldacdb2.cbl
         Scanning file UNASSIGNED/UNASSIGNED/application-conf/ACBgen.properties
         Scanning file UNASSIGNED/.git/objects/40/46a14e3b7f9b0137176c8039e1034e9e8c39fd
         Scanning file UNASSIGNED/.git/objects/d3/f1290864542e156079c4e165497994f1675139
         Scanning file UNASSIGNED/.git/objects/72/b5040d6cc91160887c2a6a8ee4fe37a2482b01
         Scanning file UNASSIGNED/UNASSIGNED/application-conf/MFS.properties
         Scanning file UNASSIGNED/.git/info/exclude
         Scanning file UNASSIGNED/UNASSIGNED/application-conf/reports.properties
         Scanning file UNASSIGNED/.git/hooks/fsmonitor-watchman.sample
         Scanning file UNASSIGNED/.git/refs/tags/rel-1.0.0
         Scanning file UNASSIGNED/UNASSIGNED/application-conf/file.properties
         Scanning file UNASSIGNED/.git/hooks/commit-msg.sample
         Scanning file UNASSIGNED/UNASSIGNED/application-conf/CRB.properties
         Scanning file UNASSIGNED/UNASSIGNED/src/bms/epsmort.bms
         Scanning file UNASSIGNED/UNASSIGNED/application-conf/README.md
         Scanning file UNASSIGNED/.git/hooks/pre-receive.sample
         Scanning file UNASSIGNED/.git/index
         Scanning file UNASSIGNED/.git/objects/da/97ba1be5273d4a3265d6fdffd68c4a9cfe57f1
         Scanning file UNASSIGNED/UNASSIGNED/application-conf/Easytrieve.properties
         Scanning file UNASSIGNED/.git/hooks/applypatch-msg.sample
         Scanning file UNASSIGNED/.git/objects/b9/cb87f77cc02aa7e5537aeb97901f4a34561cc9
         Scanning file UNASSIGNED/UNASSIGNED/application-conf/Cobol.properties
         Scanning file UNASSIGNED/.git/hooks/pre-commit.sample
         Scanning file UNASSIGNED/.git/objects/15/3ef134971f66103c8ca566b513901678804eb7
         Scanning file UNASSIGNED/.git/objects/24/79cd7afe658ecc8801d10f9f8cb42382d53d16
         Scanning file UNASSIGNED/.git/objects/a1/8654b39a98bae40a80650315882df9f3e4199c
         Scanning file UNASSIGNED/UNASSIGNED/src/cobol/flemsmai.cbl
         Scanning file UNASSIGNED/.git/objects/f7/f461db942e85d137f33609bdb50bd26015d1ec
         Scanning file UNASSIGNED/.git/objects/a0/f87d9391c2f3626352c39999b631af70552f86
         Scanning file UNASSIGNED/UNASSIGNED/application-conf/bind.properties
         Scanning file UNASSIGNED/.git/hooks/prepare-commit-msg.sample
         Scanning file UNASSIGNED/.git/hooks/sendemail-validate.sample
         Scanning file UNASSIGNED/.git/objects/df/36b23c0258c461dbd1d9b47e9cab5fd4a1fc38
         Scanning file UNASSIGNED/UNASSIGNED/application-conf/BMS.properties
         Scanning file UNASSIGNED/.git/config
         Scanning file UNASSIGNED/.git/objects/a7/ba47b0b880d255394445b339da781f22ea4a01
         Scanning file UNASSIGNED/.git/refs/heads/main
         Scanning file UNASSIGNED/.git/objects/1e/cc8a7b26eee8c6498737ad40975ca9597e7809
         Scanning file UNASSIGNED/.git/objects/b8/cea7df2b43bfac6d4e9336022a286e44a1147c
         Scanning file UNASSIGNED/.git/objects/78/c46a8b3d2f9bf33608f9ebaa1ae56260a546b2
         Scanning file UNASSIGNED/.git/objects/3e/aad50b56f466377be9bc01dca2e4188e888f53
         Scanning file UNASSIGNED/UNASSIGNED/application-conf/DBDgen.properties
         Scanning file UNASSIGNED/.git/objects/56/62d387361e223a44d43d9f9152b94492222355
         Scanning file UNASSIGNED/.git/objects/31/2d56358b0f4597312ad7d68b78ebd080fc11f5
         Scanning file UNASSIGNED/.git/objects/4a/58fdbf3761bccd3497ada688d343a15c33e8b0
** Storing results in the 'UNASSIGNED' DBB Collection.
** Build finished
*******************************************************************
Reset Application Descriptor for CBSA
*******************************************************************
** Recreate Application Descriptor process started.
** Script configuration:
   workspace -> /u/mdalbin/Migration-Modeler-DBEHM-work/applications
   application -> CBSA
   logFile -> /u/mdalbin/Migration-Modeler-DBEHM-work/logs/3-CBSA-createApplicationDescriptor.log
   repositoryPathsMappingFilePath -> /u/mdalbin/Migration-Modeler-DBEHM-work/repositoryPathsMapping.yaml
** Reading the Repository Layout Mapping definition.
* Importing existing Application Descriptor and reset source groups, dependencies and consumers.
* Getting List of files /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA
*! A hidden file found (.git/objects/24/79cd7afe658ecc8801d10f9f8cb42382d53d16). Skipped.
*! A hidden file found (.git/objects/46/3a5519cbcb1b8db463d628173cafc3751fb323). Skipped.
*! A hidden file found (.git/objects/31/2d56358b0f4597312ad7d68b78ebd080fc11f5). Skipped.
*! The file (CBSA/application-conf/BMS.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/objects/12/c04ff4762844463e6e8d5b3a92c150fbb3c6ce). Skipped.
*! A hidden file found (.git/objects/74/7f6becab8f715c30726f0adc2777d4e775a513). Skipped.
* Adding file CBSA/src/cobol/delacc.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/2b/b5e69e60b48517664e8bc178ce5047d2dc6239). Skipped.
*! A hidden file found (.git/objects/29/ef69588ebc7fb77045dc42407df52eb89b771b). Skipped.
* Adding file CBSA/src/cobol/bnk1cac.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/71/95a42c31f86e0f70315660d9da6d62f9769d1e). Skipped.
*! A hidden file found (.git/objects/71/aba7981c900888d8f74ef1f3aa3e1efe91d405). Skipped.
*! A hidden file found (.git/objects/b0/2d733e80ba87c613c4becba1438cfea345bb63). Skipped.
* Adding file CBSA/src/cobol/creacc.cbl to Application Descriptor into source group cobol.
* Adding file CBSA/src/cobol/bnkmenu.cbl to Application Descriptor into source group cobol.
*! The file (CBSA/application-conf/Transfer.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/objects/e4/a208249eb9f188dac631a80aa69560a1b5c812). Skipped.
*! A hidden file found (.git/objects/bb/6a183c5808c83f435ffe292d40ce3c1e78182e). Skipped.
* Adding file CBSA/src/copy/datastr.cpy to Application Descriptor into source group copy.
*! A hidden file found (.git/objects/30/ec95859415287a39af962b759792828e403684). Skipped.
*! A hidden file found (.git/objects/fa/7a23ca781e7e8e7afa7d20dc2caaf6ebba38dc). Skipped.
*! A hidden file found (.git/objects/d3/e104ac3f1682cf5c81e6a4df77a916b5601adb). Skipped.
*! A hidden file found (.git/hooks/prepare-commit-msg.sample). Skipped.
* Adding file CBSA/src/copy/bnk1mai.cpy to Application Descriptor into source group copy.
*! A hidden file found (.git/objects/c8/6c28e6b894571ccad1c6beaa040d1b916a1a77). Skipped.
*! A hidden file found (.git/objects/b6/deb95fdbfe6a2f08acb265c23cccc973e8b031). Skipped.
*! The file (CBSA/application-conf/PSBgen.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/objects/35/1b0c08fb96d69ec8f2e5c4a71121da780037dd). Skipped.
*! A hidden file found (.git/objects/1d/7f5fcdba85d4c4d0bc6ab0bab4b287e69242db). Skipped.
*! A hidden file found (.git/objects/de/ce936b7a48fba884a6d376305fbce1a2fc99e5). Skipped.
*! A hidden file found (.git/objects/c8/82661ae39a9a8ed30486a8433c1b186cbc5159). Skipped.
* Adding file CBSA/src/cobol/crdtagy3.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/99/a8f2520e0dc26a905446e52245f7b6314133d9). Skipped.
*! A hidden file found (.git/objects/7e/0340c01a352c55eaf478a5c7dbe8c290e50728). Skipped.
*! A hidden file found (.git/objects/d3/f1290864542e156079c4e165497994f1675139). Skipped.
*! A hidden file found (.git/objects/94/08dd2f2709f23766aa4d1ef89e6e175974b396). Skipped.
*! A hidden file found (.git/objects/97/0f6a926b868353d6a285d20b07d29abfba4292). Skipped.
* Adding file CBSA/src/cobol/bnk1uac.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/d0/c5bf02bc846be691c4ea906c10118719d3bed3). Skipped.
*! A hidden file found (.git/objects/69/27d3b72033e6e7e4f9d6527fb5d347e1fc67d4). Skipped.
*! A hidden file found (.git/objects/ff/86efc8e05a7fc5e66defbf50820da4ab3bad95). Skipped.
*! A hidden file found (.git/objects/ab/80f99d7e1e2cf005e04f11f43b710b6cfc765c). Skipped.
*! A hidden file found (.git/objects/fb/741632c192243a1f4e7799371635f854bd40db). Skipped.
*! A hidden file found (.git/objects/b6/97ad559100281f7737764166ced34b4398ae0d). Skipped.
* Adding file CBSA/src/cobol/inqcust.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/c0/6aacd0c94d044b5fb1d2cb22bc796b946bcf6f). Skipped.
*! A hidden file found (.git/logs/HEAD). Skipped.
*! The file (CBSA/application-conf/PLI.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/objects/27/0fd7eb4a2109c25b62d78595d8ddd044de4983). Skipped.
*! A hidden file found (.git/objects/6e/ba9fb7a278153965978bde08e8b79d7549a6e5). Skipped.
*! A hidden file found (.git/objects/3e/aad50b56f466377be9bc01dca2e4188e888f53). Skipped.
* Adding file CBSA/src/copy/getcompy.cpy to Application Descriptor into source group copy.
* Adding file CBSA/src/copy/updacc.cpy to Application Descriptor into source group copy.
*! The file (CBSA/application-conf/README.md) did not match any rule defined in the repository path mapping configuration.
* Adding file CBSA/src/cobol/bankdata.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/da/97ba1be5273d4a3265d6fdffd68c4a9cfe57f1). Skipped.
* Adding file CBSA/src/cobol/dpaytst.cbl to Application Descriptor into source group cobol.
* Adding file CBSA/src/cobol/prooffl.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/config). Skipped.
*! A hidden file found (.git/objects/78/c46a8b3d2f9bf33608f9ebaa1ae56260a546b2). Skipped.
* Adding file CBSA/src/copy/inqcust.cpy to Application Descriptor into source group copy.
*! A hidden file found (.git/hooks/pre-push.sample). Skipped.
*! A hidden file found (.git/objects/5e/014abb1c1c7b87e5b7487894a0dd577ecd6903). Skipped.
* Adding file CBSA/src/copy/abndinfo.cpy to Application Descriptor into source group copy.
* Adding file CBSA/src/copy/accdb2.cpy to Application Descriptor into source group copy.
*! A hidden file found (.git/objects/37/1a19b8d93fa4d1f491a4174865ff3b5dc57b6f). Skipped.
*! A hidden file found (.git/hooks/pre-rebase.sample). Skipped.
*! A hidden file found (.git/info/exclude). Skipped.
* Adding file CBSA/src/cobol/bnk1dac.cbl to Application Descriptor into source group cobol.
*! The file (CBSA/application-conf/ACBgen.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/hooks/applypatch-msg.sample). Skipped.
*! The file (CBSA/application-conf/Assembler.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/objects/2f/bc2fdb9097a629e3d0d899d0d4912a5ce4a678). Skipped.
*! A hidden file found (.git/objects/57/a7db352970bbfae82cf24c95aa6cecc159b0e0). Skipped.
*! A hidden file found (.git/objects/e3/df501f6a5529aff989412d6c4af4b43a9897d1). Skipped.
* Adding file CBSA/src/cobol/getcompy.cbl to Application Descriptor into source group cobol.
* Adding file CBSA/src/cobol/crecust.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/f7/fbe29970a3bd547fcfd6e82df58e45190d46a8). Skipped.
*! A hidden file found (.git/hooks/pre-merge-commit.sample). Skipped.
*! A hidden file found (.git/objects/d9/c46c2b0b76ac752b67f451dd45995cd5bc96d1). Skipped.
*! A hidden file found (.git/objects/c9/5be47dd3ede400e93ba367b5f5ac433a714d5a). Skipped.
*! A hidden file found (.git/HEAD). Skipped.
* Adding file CBSA/src/copy/bnk1dcm.cpy to Application Descriptor into source group copy.
* Adding file CBSA/src/cobol/crdtagy4.cbl to Application Descriptor into source group cobol.
* Adding file CBSA/src/copy/bnk1acc.cpy to Application Descriptor into source group copy.
* Adding file CBSA/src/copy/inqacc.cpy to Application Descriptor into source group copy.
* Adding file CBSA/src/cobol/consttst.cbl to Application Descriptor into source group cobol.
* Adding file CBSA/src/copy/crecust.cpy to Application Descriptor into source group copy.
*! A hidden file found (.git/objects/d4/c22ba5bfb0742e2395037184f5fc4174577a8c). Skipped.
*! A hidden file found (.git/objects/d3/7d2d4704218babc4ab9871cc3ea1f5271dc80d). Skipped.
*! A hidden file found (.git/objects/89/7bf2e97ca69ede559524c31bae8d639ae1b81d). Skipped.
* Adding file CBSA/src/cobol/bnk1cra.cbl to Application Descriptor into source group cobol.
* Adding file CBSA/src/cobol/delcus.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/description). Skipped.
*! A hidden file found (.git/objects/2a/d1a2ba3dc994398cbf308b3e6bdb30db9c1f1b). Skipped.
*! A hidden file found (.git/objects/d9/7584fe7d7c5e0120ab762194b119287f6bc91d). Skipped.
*! A hidden file found (.git/objects/a7/e4ad4c1bde8c6ad9144199468403799cdd0e26). Skipped.
* Adding file CBSA/src/copy/bnk1ccm.cpy to Application Descriptor into source group copy.
* Adding file CBSA/src/cobol/accload.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/f7/f461db942e85d137f33609bdb50bd26015d1ec). Skipped.
*! A hidden file found (.gitattributes). Skipped.
*! A hidden file found (.git/objects/84/bc44ed9738bc69291a529f9b7b7a1b3cccdc88). Skipped.
* Adding file CBSA/src/cobol/bnk1ccs.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/41/c1fc24c5c355423d1cdad4477113b6c6f0945f). Skipped.
* Adding file CBSA/src/copy/updcust.cpy to Application Descriptor into source group copy.
*! A hidden file found (.git/objects/4d/3bc5c5136e4bfe98ceb8e5f5136b421afd8596). Skipped.
* Adding file CBSA/src/copy/bnk1uam.cpy to Application Descriptor into source group copy.
* Adding file CBSA/src/copy/delacc.cpy to Application Descriptor into source group copy.
* Adding file CBSA/src/cobol/crdtagy5.cbl to Application Descriptor into source group cobol.
* Adding file CBSA/src/cobol/bnk1cca.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/9d/8cdd3cfd001f9ff47534b9a741f61f757cc90c). Skipped.
*! A hidden file found (.git/objects/04/a5b554ae15152a060f462fe894e09e7188e394). Skipped.
*! A hidden file found (.git/objects/f5/0cc01256b3b2f272a59bed37caeb1a61f5ba4c). Skipped.
*! A hidden file found (.git/hooks/push-to-checkout.sample). Skipped.
*! The file (CBSA/application-conf/CRB.properties) did not match any rule defined in the repository path mapping configuration.
* Adding file CBSA/src/copy/customer.cpy to Application Descriptor into source group copy.
*! A hidden file found (.git/refs/heads/main). Skipped.
*! A hidden file found (.git/objects/d3/70465392addcb5a86920019826deec0e531a77). Skipped.
*! A hidden file found (.git/objects/55/57d232d69aa70962e5580123403d3662157e2a). Skipped.
* Adding file CBSA/src/copy/sortcode.cpy to Application Descriptor into source group copy.
* Adding file CBSA/src/cobol/custctrl.cbl to Application Descriptor into source group cobol.
* Adding file CBSA/src/copy/custctrl.cpy to Application Descriptor into source group copy.
*! The file (CBSA/application-conf/Easytrieve.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/objects/9c/3aec3ef67cd80287d375f825fe1b7abfb8be4d). Skipped.
* Adding file CBSA/src/copy/bnk1cdm.cpy to Application Descriptor into source group copy.
*! A hidden file found (.git/objects/68/c29e32bba41130b5f6308b06ffbaf11d7214cc). Skipped.
*! A hidden file found (.git/objects/bc/ecf21e6187f0d2dba5c129c53954a8363f0d0e). Skipped.
*! A hidden file found (.git/objects/40/46a14e3b7f9b0137176c8039e1034e9e8c39fd). Skipped.
*! A hidden file found (.git/objects/56/364507a259c6881a4e9a961213a9aa5a6405e7). Skipped.
*! A hidden file found (.git/objects/14/833274735adb257e1062eaa63d495febe9e962). Skipped.
*! The file (CBSA.yaml) did not match any rule defined in the repository path mapping configuration.
* Adding file CBSA/src/cobol/abndproc.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/b8/cea7df2b43bfac6d4e9336022a286e44a1147c). Skipped.
*! The file (CBSA/application-conf/LinkEdit.properties) did not match any rule defined in the repository path mapping configuration.
* Adding file CBSA/src/copy/proctran.cpy to Application Descriptor into source group copy.
*! A hidden file found (.git/objects/b1/8656b5144b139b6a3b4515d4883a5d0e9ee2ce). Skipped.
*! A hidden file found (.git/objects/02/20c1299e5ed367b9d602d8a11c9909a081c026). Skipped.
* Adding file CBSA/src/copy/xfrfun.cpy to Application Descriptor into source group copy.
*! A hidden file found (.git/logs/refs/heads/main). Skipped.
*! The file (CBSA/application-conf/bind.properties) did not match any rule defined in the repository path mapping configuration.
* Adding file CBSA/src/cobol/bnk1dcs.cbl to Application Descriptor into source group cobol.
* Adding file CBSA/src/cobol/updcust.cbl to Application Descriptor into source group cobol.
* Adding file CBSA/src/cobol/acctctrl.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/1c/b8db96a22a09cba20ddf3d7bb37fb098963100). Skipped.
* Adding file CBSA/src/copy/bnk1tfm.cpy to Application Descriptor into source group copy.
*! A hidden file found (.git/objects/f4/33cbfff90207efad95d399c2632acc1684f942). Skipped.
* Adding file CBSA/src/copy/acctctrl.cpy to Application Descriptor into source group copy.
*! A hidden file found (.git/objects/57/9fef02baff9b735fc28867aef660f088b64710). Skipped.
*! The file (CBSA/application-conf/MFS.properties) did not match any rule defined in the repository path mapping configuration.
* Adding file CBSA/src/cobol/crdtagy1.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/b1/7e73e90052cbe5144318dc9cf00cdf04589042). Skipped.
* Adding file CBSA/src/copy/paydbcr.cpy to Application Descriptor into source group copy.
*! A hidden file found (.git/objects/01/d96e12b164d97cc7f2c72489c8cd3205a8b69f). Skipped.
*! A hidden file found (.git/objects/b0/aed0954293fc2763f3c02ec65cbaa53603015d). Skipped.
*! A hidden file found (.git/objects/e4/96c6a4e7a960de791e1fd97a02ae6614769936). Skipped.
*! A hidden file found (.git/objects/aa/3a09c5ec672fef16b4d689127e80ca5ce595ce). Skipped.
* Adding file CBSA/src/copy/account.cpy to Application Descriptor into source group copy.
*! A hidden file found (.git/objects/fa/3508648b495e92bc320f8110bfd3d78a4d5a3a). Skipped.
*! A hidden file found (.git/objects/56/eec383e79ddc7d93386976ba31b6f06180c1a0). Skipped.
* Adding file CBSA/src/copy/creacc.cpy to Application Descriptor into source group copy.
* Adding file CBSA/src/copy/getscode.cpy to Application Descriptor into source group copy.
* Adding file CBSA/src/cobol/getscode.cbl to Application Descriptor into source group cobol.
* Adding file CBSA/src/copy/bnk1dam.cpy to Application Descriptor into source group copy.
*! A hidden file found (.git/index). Skipped.
* Adding file CBSA/src/cobol/bnk1tfn.cbl to Application Descriptor into source group cobol.
* Adding file CBSA/src/cobol/proload.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/a1/4465df829b167bbb644dffc1027434adbf3c32). Skipped.
* Adding file CBSA/src/copy/bnk1cam.cpy to Application Descriptor into source group copy.
*! A hidden file found (.git/objects/04/9cc7eb352d85ce38026a8f3029f22e711b8b9a). Skipped.
*! A hidden file found (.git/objects/8e/b541c571cd537e557c27e56eb472e9cafb0308). Skipped.
*! A hidden file found (.git/objects/47/f9f61e0fdb34ee5ebbf7fc11529e50b079a04b). Skipped.
*! A hidden file found (.git/objects/4a/58fdbf3761bccd3497ada688d343a15c33e8b0). Skipped.
* Adding file CBSA/src/copy/delcus.cpy to Application Descriptor into source group copy.
*! A hidden file found (.git/objects/82/14b4cdd014e9e1f1c45fae193c49364def5894). Skipped.
* Adding file CBSA/src/cobol/dbcrfun.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/94/7a658dffaf7b8a8a1348ad9dabbdca1f87fbb0). Skipped.
* Adding file CBSA/src/cobol/updacc.cbl to Application Descriptor into source group cobol.
* Adding file CBSA/src/cobol/consent.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/33/44cbdf7b601794f0ef2341235f09f126fe4562). Skipped.
*! A hidden file found (.git/hooks/update.sample). Skipped.
* Adding file CBSA/src/copy/inqacccu.cpy to Application Descriptor into source group copy.
*! A hidden file found (.git/hooks/pre-applypatch.sample). Skipped.
*! A hidden file found (.git/hooks/pre-commit.sample). Skipped.
*! A hidden file found (.git/objects/1e/cc8a7b26eee8c6498737ad40975ca9597e7809). Skipped.
*! The file (CBSA/application-conf/application.properties) did not match any rule defined in the repository path mapping configuration.
*! The file (CBSA/application-conf/file.properties) did not match any rule defined in the repository path mapping configuration.
* Adding file CBSA/src/cobol/xfrfun.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/hooks/commit-msg.sample). Skipped.
*! A hidden file found (.git/objects/cb/75236314e2fba04aca378ad29061942e6900a5). Skipped.
*! A hidden file found (.git/objects/b8/33431450f198af575ebdf622a8144df7c0962a). Skipped.
*! A hidden file found (.git/objects/f6/3ebe51d5520bc56b0a6911cfc2ed6705fdfa66). Skipped.
* Adding file CBSA/src/copy/consent.cpy to Application Descriptor into source group copy.
*! A hidden file found (.git/objects/ff/7f1a74d6d78a6d35e4559b32cdff813a5fb12e). Skipped.
*! A hidden file found (.git/objects/21/b32b59cad6603ee75673876be89e6c04c4c122). Skipped.
*! A hidden file found (.git/hooks/sendemail-validate.sample). Skipped.
*! A hidden file found (.git/hooks/pre-receive.sample). Skipped.
* Adding file CBSA/src/cobol/inqacccu.cbl to Application Descriptor into source group cobol.
* Adding file CBSA/src/cobol/crdtagy2.cbl to Application Descriptor into source group cobol.
* Adding file CBSA/src/copy/constapi.cpy to Application Descriptor into source group copy.
* Adding file CBSA/src/copy/constdb2.cpy to Application Descriptor into source group copy.
*! A hidden file found (.git/hooks/post-update.sample). Skipped.
*! The file (CBSA/application-conf/ZunitConfig.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/COMMIT_EDITMSG). Skipped.
*! A hidden file found (.git/objects/33/4b8f087b5e1bd5c05036a920378e8e1f3c0276). Skipped.
* Adding file CBSA/src/cobol/inqacc.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/b5/6eafbe98c4e46afb0c8c60ee97cf437292a68c). Skipped.
* Adding file CBSA/src/copy/procdb2.cpy to Application Descriptor into source group copy.
*! A hidden file found (.git/hooks/fsmonitor-watchman.sample). Skipped.
*! A hidden file found (.git/objects/6f/3549f765104b58d630d2a4ce871fc1b9e4bb7a). Skipped.
*! A hidden file found (.git/objects/f5/5399eea902ae9bc01584c1e3bc71f4db98eef6). Skipped.
*! The file (CBSA/application-conf/Cobol.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/objects/a6/ee2080f7c783724cafee89a81049a3f2893e75). Skipped.
*! A hidden file found (.git/objects/34/390dbd6e6f281f6101d179897949a51393c264). Skipped.
*! The file (CBSA/application-conf/REXX.properties) did not match any rule defined in the repository path mapping configuration.
*! The file (CBSA/application-conf/reports.properties) did not match any rule defined in the repository path mapping configuration.
*! The file (CBSA/application-conf/languageConfigurationMapping.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/refs/tags/rel-1.0.0). Skipped.
*! A hidden file found (.git/objects/c2/432e4bf3b85f883fdcaff1adb419b1ebf3fd18). Skipped.
*! A hidden file found (.git/objects/66/afa88844c422af69da0d35243993d4e50dac3c). Skipped.
* Adding file CBSA/src/cobol/accoffl.cbl to Application Descriptor into source group cobol.
* Adding file CBSA/src/cobol/dpayapi.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/b2/849d92d4dd7bd253384f910a069f98802f64f1). Skipped.
*! A hidden file found (.git/objects/b4/79ed3b38c3f9680850dc34a3c9d10e24ddb52f). Skipped.
* Adding file CBSA/src/copy/contdb2.cpy to Application Descriptor into source group copy.
*! The file (CBSA/application-conf/DBDgen.properties) did not match any rule defined in the repository path mapping configuration.
* Created Application Description file /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml
** Build finished
*******************************************************************
Reset Application Descriptor for GenApp
*******************************************************************
** Recreate Application Descriptor process started.
** Script configuration:
   workspace -> /u/mdalbin/Migration-Modeler-DBEHM-work/applications
   application -> GenApp
   logFile -> /u/mdalbin/Migration-Modeler-DBEHM-work/logs/3-GenApp-createApplicationDescriptor.log
   repositoryPathsMappingFilePath -> /u/mdalbin/Migration-Modeler-DBEHM-work/repositoryPathsMapping.yaml
** Reading the Repository Layout Mapping definition.
* Importing existing Application Descriptor and reset source groups, dependencies and consumers.
* Getting List of files /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp
*! A hidden file found (.git/objects/24/79cd7afe658ecc8801d10f9f8cb42382d53d16). Skipped.
*! A hidden file found (.git/objects/68/5e0f68143caf8974be751db42bc6f6869e3af9). Skipped.
*! A hidden file found (.git/objects/f7/f461db942e85d137f33609bdb50bd26015d1ec). Skipped.
*! A hidden file found (.git/objects/31/2d56358b0f4597312ad7d68b78ebd080fc11f5). Skipped.
*! A hidden file found (.gitattributes). Skipped.
*! A hidden file found (.git/objects/84/bc44ed9738bc69291a529f9b7b7a1b3cccdc88). Skipped.
* Adding file GenApp/src/cobol/lgtestc1.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/fa/ffcce01f2da721aa453f5dda21d11f8d3ae693). Skipped.
*! A hidden file found (.git/objects/82/766939ca20dfac5d9ab33782e4f45b2ade19fc). Skipped.
* Adding file GenApp/src/cobol/lgupdb01.cbl to Application Descriptor into source group cobol.
* Adding file GenApp/src/cobol/lgipvs01.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/3e/8c9c7714c8622b1fe6077544b2b535dc3d0330). Skipped.
*! The file (GenApp/application-conf/file.properties) did not match any rule defined in the repository path mapping configuration.
*! The file (GenApp/application-conf/languageConfigurationMapping.properties) did not match any rule defined in the repository path mapping configuration.
* Adding file GenApp/src/cobol/lgdpdb01.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/37/0f90c505893d5ab01089e66e04528f8d40dab1). Skipped.
*! The file (GenApp/application-conf/Cobol.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/objects/3e/9eed6daafd969231900049360b526396bf4091). Skipped.
*! A hidden file found (.git/objects/0a/f04c7e31314b30efc3600507f62bfd1c2970c9). Skipped.
*! A hidden file found (.git/hooks/push-to-checkout.sample). Skipped.
*! The file (GenApp.yaml) did not match any rule defined in the repository path mapping configuration.
* Adding file GenApp/src/cobol/lgipdb01.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/refs/heads/main). Skipped.
* Adding file GenApp/src/cobol/lgtestp1.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/6b/70ac40882304b17e808848fc61b6c4fd833607). Skipped.
* Adding file GenApp/src/cobol/lgapol01.cbl to Application Descriptor into source group cobol.
*! The file (GenApp/application-conf/CRB.properties) did not match any rule defined in the repository path mapping configuration.
* Adding file GenApp/src/cobol/lgupvs01.cbl to Application Descriptor into source group cobol.
*! The file (GenApp/application-conf/DBDgen.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/hooks/prepare-commit-msg.sample). Skipped.
*! The file (GenApp/application-conf/ACBgen.properties) did not match any rule defined in the repository path mapping configuration.
* Adding file GenApp/src/cobol/lgtestp4.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/68/c29e32bba41130b5f6308b06ffbaf11d7214cc). Skipped.
*! The file (GenApp/application-conf/REXX.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/objects/40/46a14e3b7f9b0137176c8039e1034e9e8c39fd). Skipped.
*! The file (GenApp/application-conf/BMS.properties) did not match any rule defined in the repository path mapping configuration.
*! The file (GenApp/application-conf/LinkEdit.properties) did not match any rule defined in the repository path mapping configuration.
* Adding file GenApp/src/cobol/lgdpvs01.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/b8/cea7df2b43bfac6d4e9336022a286e44a1147c). Skipped.
*! The file (GenApp/application-conf/PSBgen.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/objects/28/0e6f742c84b40da642115cad3a0c86aa9c0aac). Skipped.
*! A hidden file found (.git/objects/d9/455ae3c356b0e7a2440914f564ddbcbe30e28d). Skipped.
*! A hidden file found (.git/objects/99/a8f2520e0dc26a905446e52245f7b6314133d9). Skipped.
*! A hidden file found (.git/objects/0d/b601b1f055ea023e104c7d24ab0ef5eea1ff05). Skipped.
*! A hidden file found (.git/objects/9a/a1e257384925e8015d7e0864175961ce258290). Skipped.
*! A hidden file found (.git/objects/17/cd1d6b0325b04277c7fc7a1ec27ce9bcbd2598). Skipped.
*! A hidden file found (.git/logs/refs/heads/main). Skipped.
*! The file (GenApp/application-conf/ZunitConfig.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/objects/d3/f1290864542e156079c4e165497994f1675139). Skipped.
*! A hidden file found (.git/objects/e1/52fbd8c03e836ad0046953854f04b4665d75b9). Skipped.
*! A hidden file found (.git/objects/b0/49dc9735257281c334afd74730dee59c62e2e8). Skipped.
*! The file (GenApp/application-conf/MFS.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/objects/1b/9d6bcb233214bd016ac6ffd87d5b4e5a0644cc). Skipped.
*! A hidden file found (.git/objects/78/e7f1d24d01d4949e80fc149026a9d902eac1b9). Skipped.
*! A hidden file found (.git/objects/69/27d3b72033e6e7e4f9d6527fb5d347e1fc67d4). Skipped.
*! A hidden file found (.git/objects/1c/b8db96a22a09cba20ddf3d7bb37fb098963100). Skipped.
* Adding file GenApp/src/cobol/lgwebst5.cbl to Application Descriptor into source group cobol.
* Adding file GenApp/src/cobol/lgacvs01.cbl to Application Descriptor into source group cobol.
*! The file (GenApp/application-conf/Easytrieve.properties) did not match any rule defined in the repository path mapping configuration.
*! The file (GenApp/application-conf/Transfer.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/objects/83/2f54aa68fe84f78461085d00e3b3206e39fdb7). Skipped.
* Adding file GenApp/src/copy/lgpolicy.cpy to Application Descriptor into source group copy.
* Adding file GenApp/src/cobol/lgacdb01.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/db/2a6d69779b37f2aff873868afb262ed063d27d). Skipped.
*! The file (GenApp/application-conf/PLI.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/logs/HEAD). Skipped.
*! A hidden file found (.git/objects/f7/09ff109986301f101a1912b9d043756d7e596a). Skipped.
*! The file (GenApp/application-conf/Assembler.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/index). Skipped.
*! A hidden file found (.git/objects/3e/aad50b56f466377be9bc01dca2e4188e888f53). Skipped.
* Adding file GenApp/src/cobol/lgucdb01.cbl to Application Descriptor into source group cobol.
* Adding file GenApp/src/cobol/lgicdb01.cbl to Application Descriptor into source group cobol.
* Adding file GenApp/src/cobol/lgipol01.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/82/14b4cdd014e9e1f1c45fae193c49364def5894). Skipped.
* Adding file GenApp/src/cobol/lgupol01.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/da/97ba1be5273d4a3265d6fdffd68c4a9cfe57f1). Skipped.
*! A hidden file found (.git/objects/42/d3f2e669c2f9f6cf9565e61b2a3f96ad1ff503). Skipped.
*! A hidden file found (.git/config). Skipped.
*! A hidden file found (.git/objects/78/c46a8b3d2f9bf33608f9ebaa1ae56260a546b2). Skipped.
*! A hidden file found (.git/objects/2e/f0cfc9de9ca7521899a87cf9e216be7f109d88). Skipped.
*! A hidden file found (.git/hooks/update.sample). Skipped.
*! A hidden file found (.git/objects/d1/e33757aa74694d0039e8162918a840172d24f8). Skipped.
*! A hidden file found (.git/hooks/pre-push.sample). Skipped.
*! The file (GenApp/application-conf/README.md) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/hooks/pre-applypatch.sample). Skipped.
*! A hidden file found (.git/hooks/pre-commit.sample). Skipped.
*! A hidden file found (.git/objects/7d/f90877fb98ccba6508a94e6fe3ff1ad865d682). Skipped.
*! A hidden file found (.git/objects/1e/cc8a7b26eee8c6498737ad40975ca9597e7809). Skipped.
*! The file (GenApp/application-conf/application.properties) did not match any rule defined in the repository path mapping configuration.
* Adding file GenApp/src/cobol/lgucvs01.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/hooks/pre-rebase.sample). Skipped.
*! A hidden file found (.git/hooks/commit-msg.sample). Skipped.
* Adding file GenApp/src/cobol/lgtestp2.cbl to Application Descriptor into source group cobol.
*! The file (GenApp/application-conf/reports.properties) did not match any rule defined in the repository path mapping configuration.
* Adding file GenApp/src/cobol/lgicvs01.cbl to Application Descriptor into source group cobol.
* Adding file GenApp/src/cobol/lgsetup.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/info/exclude). Skipped.
* Adding file GenApp/src/copy/lgcmared.cpy to Application Descriptor into source group copy.
*! A hidden file found (.git/hooks/applypatch-msg.sample). Skipped.
*! A hidden file found (.git/objects/b8/0c506efef3e434fe92e8395a063bfb1d87b5f3). Skipped.
*! A hidden file found (.git/objects/bf/a3623bc647efd22c9550939cd8d5bf72cb91ad). Skipped.
*! A hidden file found (.git/objects/c5/ea6c1fed91fd2154ac3f38533455da5481d974). Skipped.
*! A hidden file found (.git/hooks/sendemail-validate.sample). Skipped.
*! A hidden file found (.git/hooks/pre-receive.sample). Skipped.
*! A hidden file found (.git/objects/17/4119c31e4008790ec424427596d0859d696c96). Skipped.
*! A hidden file found (.git/objects/89/20ce0008397665b02a79f971898c033709bc6b). Skipped.
* Adding file GenApp/src/cobol/lgacdb02.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/12/5b26f553c5647a5aabc69a45f0191aed5d5e01). Skipped.
*! A hidden file found (.git/objects/98/11fa56e0556c5d884a98ae06f7d007f64edafa). Skipped.
* Adding file GenApp/src/cobol/lgapvs01.cbl to Application Descriptor into source group cobol.
*! The file (GenApp/application-conf/bind.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/objects/de/85d8fbe9f576dabc377e29616bc4e8fcf68a56). Skipped.
* Adding file GenApp/src/copy/lgcmarea.cpy to Application Descriptor into source group copy.
*! A hidden file found (.git/hooks/post-update.sample). Skipped.
* Adding file GenApp/src/cobol/lgacus01.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/hooks/pre-merge-commit.sample). Skipped.
*! A hidden file found (.git/objects/28/2aa20f6c7d61d15b8922c8d8e0552880351472). Skipped.
*! A hidden file found (.git/objects/76/be470b4b4450038992dec6a9f9ac90a8611f2b). Skipped.
*! A hidden file found (.git/COMMIT_EDITMSG). Skipped.
* Adding file GenApp/src/cobol/lgastat1.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/6e/a11cb2dc20aa126f08701fe873ac2dae5ce0b6). Skipped.
* Adding file GenApp/src/cobol/lgtestp3.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/HEAD). Skipped.
*! A hidden file found (.git/objects/3b/6b75b7fd2f100934f2ae236cbff5a174454de2). Skipped.
*! A hidden file found (.git/hooks/fsmonitor-watchman.sample). Skipped.
*! A hidden file found (.git/objects/ed/7e8c1b79aaa76736f0af3b735f667d3d26ad36). Skipped.
* Adding file GenApp/src/bms/ssmap.bms to Application Descriptor into source group bms.
*! A hidden file found (.git/objects/d8/f18d43e8afa308163aebcff561e7dedf67759e). Skipped.
* Adding file GenApp/src/cobol/lgucus01.cbl to Application Descriptor into source group cobol.
* Adding file GenApp/src/cobol/lgicus01.cbl to Application Descriptor into source group cobol.
* Adding file GenApp/src/cobol/lgapdb01.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/22/b550bafdc6e9f5103b1a28ca501d6bdae4ec76). Skipped.
*! A hidden file found (.git/objects/e5/86c7d2e00e602158da102e4c8d30deaeb142ae). Skipped.
*! A hidden file found (.git/objects/d4/24e6a718eb9ad584e21f7a899488500484f7e2). Skipped.
* Adding file GenApp/src/cobol/lgstsq.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/7e/36d0d65c7ae8ca0ce7a451692820010cf2c51f). Skipped.
*! A hidden file found (.git/refs/tags/rel-1.0.0). Skipped.
*! A hidden file found (.git/objects/b6/53161403e5df737d6e540d8c5a1988a043eafc). Skipped.
*! A hidden file found (.git/objects/48/cd97eb3d38cc15a850ed45ddfe76c7d3f6c7da). Skipped.
*! A hidden file found (.git/objects/a0/b94e23333057ca37382048c4f7fc6f2e0df75b). Skipped.
*! A hidden file found (.git/description). Skipped.
*! A hidden file found (.git/objects/2a/d1a2ba3dc994398cbf308b3e6bdb30db9c1f1b). Skipped.
*! A hidden file found (.git/objects/b2/849d92d4dd7bd253384f910a069f98802f64f1). Skipped.
*! A hidden file found (.git/objects/16/73ab0e7f0e1744ab58379576e6c835d4108474). Skipped.
* Adding file GenApp/src/cobol/lgdpol01.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/a7/e4ad4c1bde8c6ad9144199468403799cdd0e26). Skipped.
* Created Application Description file /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp.yaml
** Build finished
*******************************************************************
Reset Application Descriptor for RetirementCalculator
*******************************************************************
** Recreate Application Descriptor process started.
** Script configuration:
   workspace -> /u/mdalbin/Migration-Modeler-DBEHM-work/applications
   application -> RetirementCalculator
   logFile -> /u/mdalbin/Migration-Modeler-DBEHM-work/logs/3-RetirementCalculator-createApplicationDescriptor.log
   repositoryPathsMappingFilePath -> /u/mdalbin/Migration-Modeler-DBEHM-work/repositoryPathsMapping.yaml
** Reading the Repository Layout Mapping definition.
* Importing existing Application Descriptor and reset source groups, dependencies and consumers.
* Getting List of files /u/mdalbin/Migration-Modeler-DBEHM-work/applications/RetirementCalculator
*! A hidden file found (.git/objects/24/79cd7afe658ecc8801d10f9f8cb42382d53d16). Skipped.
*! A hidden file found (.git/objects/3e/aad50b56f466377be9bc01dca2e4188e888f53). Skipped.
*! A hidden file found (.git/objects/f7/f461db942e85d137f33609bdb50bd26015d1ec). Skipped.
*! A hidden file found (.git/objects/31/2d56358b0f4597312ad7d68b78ebd080fc11f5). Skipped.
*! The file (RetirementCalculator/application-conf/Cobol.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.gitattributes). Skipped.
*! A hidden file found (.git/objects/84/bc44ed9738bc69291a529f9b7b7a1b3cccdc88). Skipped.
*! A hidden file found (.git/objects/4a/58fdbf3761bccd3497ada688d343a15c33e8b0). Skipped.
*! The file (RetirementCalculator/application-conf/PLI.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/objects/82/14b4cdd014e9e1f1c45fae193c49364def5894). Skipped.
*! A hidden file found (.git/objects/da/97ba1be5273d4a3265d6fdffd68c4a9cfe57f1). Skipped.
*! A hidden file found (.git/config). Skipped.
*! A hidden file found (.git/objects/78/c46a8b3d2f9bf33608f9ebaa1ae56260a546b2). Skipped.
*! A hidden file found (.git/objects/c3/dbdbc790dc93a9b3e12cd5a220a613c72d0fab). Skipped.
*! The file (RetirementCalculator/application-conf/reports.properties) did not match any rule defined in the repository path mapping configuration.
*! The file (RetirementCalculator/application-conf/CRB.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/hooks/update.sample). Skipped.
*! A hidden file found (.git/hooks/pre-push.sample). Skipped.
* Adding file RetirementCalculator/src/cobol/ebud0run.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/hooks/pre-applypatch.sample). Skipped.
*! A hidden file found (.git/hooks/pre-commit.sample). Skipped.
*! A hidden file found (.git/objects/1e/cc8a7b26eee8c6498737ad40975ca9597e7809). Skipped.
*! The file (RetirementCalculator/application-conf/REXX.properties) did not match any rule defined in the repository path mapping configuration.
*! The file (RetirementCalculator/application-conf/Assembler.properties) did not match any rule defined in the repository path mapping configuration.
*! The file (RetirementCalculator/application-conf/README.md) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/hooks/pre-rebase.sample). Skipped.
*! The file (RetirementCalculator/application-conf/file.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/hooks/commit-msg.sample). Skipped.
*! The file (RetirementCalculator/application-conf/PSBgen.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/objects/12/0c8e0025fdfa30c48032826c42450988f888a8). Skipped.
*! The file (RetirementCalculator/application-conf/ZunitConfig.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/hooks/push-to-checkout.sample). Skipped.
*! The file (RetirementCalculator.yaml) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/info/exclude). Skipped.
*! A hidden file found (.git/refs/heads/main). Skipped.
*! A hidden file found (.git/hooks/applypatch-msg.sample). Skipped.
*! A hidden file found (.git/objects/ea/ce47f23a335d6ead94dcb74c338a1e1adf65ae). Skipped.
*! A hidden file found (.git/objects/c9/bcc2e7d175040d35f224a8ec4a9a96fe28e9ca). Skipped.
*! The file (RetirementCalculator/application-conf/bind.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/hooks/sendemail-validate.sample). Skipped.
*! A hidden file found (.git/objects/48/7e49969b9764ca1f1f6e4a8e100aafa634f04b). Skipped.
*! A hidden file found (.git/hooks/pre-receive.sample). Skipped.
*! A hidden file found (.git/hooks/prepare-commit-msg.sample). Skipped.
*! The file (RetirementCalculator/application-conf/application.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/objects/68/c29e32bba41130b5f6308b06ffbaf11d7214cc). Skipped.
*! A hidden file found (.git/objects/e3/df501f6a5529aff989412d6c4af4b43a9897d1). Skipped.
*! The file (RetirementCalculator/application-conf/ACBgen.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/objects/f3/2100e3cf13a183e80544d4a0ddd843c8d0d949). Skipped.
*! A hidden file found (.git/objects/da/2a610077413aed3719f8b6cceae7418fea61bf). Skipped.
*! A hidden file found (.git/objects/40/46a14e3b7f9b0137176c8039e1034e9e8c39fd). Skipped.
*! The file (RetirementCalculator/application-conf/BMS.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/objects/79/9226e3830e77a8ecc42283cb31696cb02354b7). Skipped.
*! A hidden file found (.git/objects/ac/76a910965c68f48767578cd5a5b64957d98a4d). Skipped.
*! A hidden file found (.git/hooks/post-update.sample). Skipped.
*! A hidden file found (.git/hooks/pre-merge-commit.sample). Skipped.
*! A hidden file found (.git/objects/b8/cea7df2b43bfac6d4e9336022a286e44a1147c). Skipped.
*! A hidden file found (.git/COMMIT_EDITMSG). Skipped.
*! A hidden file found (.git/HEAD). Skipped.
*! A hidden file found (.git/objects/99/a8f2520e0dc26a905446e52245f7b6314133d9). Skipped.
*! The file (RetirementCalculator/application-conf/languageConfigurationMapping.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/hooks/fsmonitor-watchman.sample). Skipped.
*! A hidden file found (.git/logs/refs/heads/main). Skipped.
*! A hidden file found (.git/objects/d3/f1290864542e156079c4e165497994f1675139). Skipped.
* Adding file RetirementCalculator/src/cobol/ebud01.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/57/c0328a170cd985ed20121f8a29719189b3a28f). Skipped.
*! A hidden file found (.git/objects/6f/3549f765104b58d630d2a4ce871fc1b9e4bb7a). Skipped.
*! A hidden file found (.git/objects/69/27d3b72033e6e7e4f9d6527fb5d347e1fc67d4). Skipped.
*! A hidden file found (.git/objects/1c/b8db96a22a09cba20ddf3d7bb37fb098963100). Skipped.
*! The file (RetirementCalculator/application-conf/Transfer.properties) did not match any rule defined in the repository path mapping configuration.
*! The file (RetirementCalculator/application-conf/MFS.properties) did not match any rule defined in the repository path mapping configuration.
* Adding file RetirementCalculator/src/cobol/ebud02.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/refs/tags/rel-1.0.0). Skipped.
*! A hidden file found (.git/objects/c8/46ec8770e850c9ebda2cc736d6c65f76d0e74b). Skipped.
* Adding file RetirementCalculator/src/copy/linput.cpy to Application Descriptor into source group copy.
*! A hidden file found (.git/description). Skipped.
*! A hidden file found (.git/objects/2a/d1a2ba3dc994398cbf308b3e6bdb30db9c1f1b). Skipped.
*! A hidden file found (.git/logs/HEAD). Skipped.
*! The file (RetirementCalculator/application-conf/LinkEdit.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/objects/b2/849d92d4dd7bd253384f910a069f98802f64f1). Skipped.
*! The file (RetirementCalculator/application-conf/Easytrieve.properties) did not match any rule defined in the repository path mapping configuration.
* Adding file RetirementCalculator/src/cobol/ebud03.cbl to Application Descriptor into source group cobol.
*! The file (RetirementCalculator/application-conf/DBDgen.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/objects/a7/e4ad4c1bde8c6ad9144199468403799cdd0e26). Skipped.
*! A hidden file found (.git/objects/87/ff435e7003ef498860dfc30381bc07a03dabd2). Skipped.
*! A hidden file found (.git/index). Skipped.
* Created Application Description file /u/mdalbin/Migration-Modeler-DBEHM-work/applications/RetirementCalculator/RetirementCalculator.yaml
** Build finished
*******************************************************************
Reset Application Descriptor for UNASSIGNED
*******************************************************************
** Recreate Application Descriptor process started.
** Script configuration:
   workspace -> /u/mdalbin/Migration-Modeler-DBEHM-work/applications
   application -> UNASSIGNED
   logFile -> /u/mdalbin/Migration-Modeler-DBEHM-work/logs/3-UNASSIGNED-createApplicationDescriptor.log
   repositoryPathsMappingFilePath -> /u/mdalbin/Migration-Modeler-DBEHM-work/repositoryPathsMapping.yaml
** Reading the Repository Layout Mapping definition.
* Importing existing Application Descriptor and reset source groups, dependencies and consumers.
* Getting List of files /u/mdalbin/Migration-Modeler-DBEHM-work/applications/UNASSIGNED
*! A hidden file found (.git/objects/24/79cd7afe658ecc8801d10f9f8cb42382d53d16). Skipped.
*! A hidden file found (.git/objects/a1/8654b39a98bae40a80650315882df9f3e4199c). Skipped.
*! A hidden file found (.git/objects/3e/aad50b56f466377be9bc01dca2e4188e888f53). Skipped.
*! A hidden file found (.git/objects/f7/f461db942e85d137f33609bdb50bd26015d1ec). Skipped.
*! A hidden file found (.git/objects/31/2d56358b0f4597312ad7d68b78ebd080fc11f5). Skipped.
*! A hidden file found (.gitattributes). Skipped.
*! The file (UNASSIGNED/application-conf/README.md) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/objects/84/bc44ed9738bc69291a529f9b7b7a1b3cccdc88). Skipped.
* Adding file UNASSIGNED/src/cobol/flemsmai.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/4a/58fdbf3761bccd3497ada688d343a15c33e8b0). Skipped.
*! A hidden file found (.git/objects/a6/3ed1ad7270fd84bfb2eaa77886dc2be44d637e). Skipped.
*! A hidden file found (.git/objects/82/14b4cdd014e9e1f1c45fae193c49364def5894). Skipped.
*! A hidden file found (.git/objects/20/e05460bad23da4b636a6d07cb06fddcf2434d0). Skipped.
*! The file (UNASSIGNED/application-conf/reports.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/objects/15/3ef134971f66103c8ca566b513901678804eb7). Skipped.
*! A hidden file found (.git/objects/da/97ba1be5273d4a3265d6fdffd68c4a9cfe57f1). Skipped.
*! The file (UNASSIGNED/application-conf/BMS.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/config). Skipped.
*! The file (UNASSIGNED/application-conf/Cobol.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/objects/78/c46a8b3d2f9bf33608f9ebaa1ae56260a546b2). Skipped.
*! A hidden file found (.git/hooks/update.sample). Skipped.
*! A hidden file found (.git/hooks/pre-push.sample). Skipped.
*! The file (UNASSIGNED.yaml) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/hooks/pre-applypatch.sample). Skipped.
*! A hidden file found (.git/hooks/pre-commit.sample). Skipped.
* Adding file UNASSIGNED/src/cobol/oldacdb2.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/objects/1e/cc8a7b26eee8c6498737ad40975ca9597e7809). Skipped.
*! The file (UNASSIGNED/application-conf/languageConfigurationMapping.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/objects/a7/ba47b0b880d255394445b339da781f22ea4a01). Skipped.
*! A hidden file found (.git/hooks/pre-rebase.sample). Skipped.
*! A hidden file found (.git/hooks/commit-msg.sample). Skipped.
*! The file (UNASSIGNED/application-conf/PLI.properties) did not match any rule defined in the repository path mapping configuration.
*! The file (UNASSIGNED/application-conf/application.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/hooks/push-to-checkout.sample). Skipped.
* Adding file UNASSIGNED/src/bms/epsmort.bms to Application Descriptor into source group bms.
*! The file (UNASSIGNED/application-conf/Transfer.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/info/exclude). Skipped.
* Adding file UNASSIGNED/src/cobol/flemssub.cbl to Application Descriptor into source group cobol.
* Adding file UNASSIGNED/src/cobol/oldacdb1.cbl to Application Descriptor into source group cobol.
*! A hidden file found (.git/refs/heads/main). Skipped.
*! A hidden file found (.git/hooks/applypatch-msg.sample). Skipped.
* Adding file UNASSIGNED/src/bms/epsmlis.bms to Application Descriptor into source group bms.
*! A hidden file found (.git/objects/df/36b23c0258c461dbd1d9b47e9cab5fd4a1fc38). Skipped.
*! The file (UNASSIGNED/application-conf/ACBgen.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/hooks/sendemail-validate.sample). Skipped.
*! A hidden file found (.git/hooks/pre-receive.sample). Skipped.
*! The file (UNASSIGNED/application-conf/ZunitConfig.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/hooks/prepare-commit-msg.sample). Skipped.
*! The file (UNASSIGNED/application-conf/CRB.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/objects/68/c29e32bba41130b5f6308b06ffbaf11d7214cc). Skipped.
*! A hidden file found (.git/objects/e3/df501f6a5529aff989412d6c4af4b43a9897d1). Skipped.
*! A hidden file found (.git/objects/56/62d387361e223a44d43d9f9152b94492222355). Skipped.
*! The file (UNASSIGNED/application-conf/REXX.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/objects/40/46a14e3b7f9b0137176c8039e1034e9e8c39fd). Skipped.
*! A hidden file found (.git/objects/b9/cb87f77cc02aa7e5537aeb97901f4a34561cc9). Skipped.
*! The file (UNASSIGNED/application-conf/LinkEdit.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/objects/23/8aafb5fbd27ed05fd516e566f9ba78cec0c688). Skipped.
*! The file (UNASSIGNED/application-conf/PSBgen.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/hooks/post-update.sample). Skipped.
*! A hidden file found (.git/objects/72/b5040d6cc91160887c2a6a8ee4fe37a2482b01). Skipped.
*! A hidden file found (.git/hooks/pre-merge-commit.sample). Skipped.
*! A hidden file found (.git/objects/b8/cea7df2b43bfac6d4e9336022a286e44a1147c). Skipped.
*! A hidden file found (.git/COMMIT_EDITMSG). Skipped.
*! A hidden file found (.git/objects/a0/f87d9391c2f3626352c39999b631af70552f86). Skipped.
*! A hidden file found (.git/HEAD). Skipped.
*! A hidden file found (.git/objects/99/a8f2520e0dc26a905446e52245f7b6314133d9). Skipped.
*! The file (UNASSIGNED/application-conf/Easytrieve.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/hooks/fsmonitor-watchman.sample). Skipped.
*! A hidden file found (.git/logs/refs/heads/main). Skipped.
*! A hidden file found (.git/objects/d3/f1290864542e156079c4e165497994f1675139). Skipped.
*! A hidden file found (.git/objects/70/8fdfbe162a13d3e1af05064b0c41a6077231a4). Skipped.
*! A hidden file found (.git/objects/6f/3549f765104b58d630d2a4ce871fc1b9e4bb7a). Skipped.
*! A hidden file found (.git/objects/69/27d3b72033e6e7e4f9d6527fb5d347e1fc67d4). Skipped.
*! The file (UNASSIGNED/application-conf/DBDgen.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/objects/1c/b8db96a22a09cba20ddf3d7bb37fb098963100). Skipped.
*! A hidden file found (.git/objects/e3/34046d9c91d6a27d5b73a55fca62038df214e9). Skipped.
*! The file (UNASSIGNED/application-conf/MFS.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/refs/tags/rel-1.0.0). Skipped.
*! The file (UNASSIGNED/application-conf/bind.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/description). Skipped.
*! A hidden file found (.git/objects/2a/d1a2ba3dc994398cbf308b3e6bdb30db9c1f1b). Skipped.
*! A hidden file found (.git/logs/HEAD). Skipped.
*! A hidden file found (.git/objects/b2/849d92d4dd7bd253384f910a069f98802f64f1). Skipped.
*! The file (UNASSIGNED/application-conf/file.properties) did not match any rule defined in the repository path mapping configuration.
*! The file (UNASSIGNED/application-conf/Assembler.properties) did not match any rule defined in the repository path mapping configuration.
*! A hidden file found (.git/objects/a7/e4ad4c1bde8c6ad9144199468403799cdd0e26). Skipped.
*! A hidden file found (.git/index). Skipped.
* Created Application Description file /u/mdalbin/Migration-Modeler-DBEHM-work/applications/UNASSIGNED/UNASSIGNED.yaml
** Build finished
*******************************************************************
Assess Include files & Programs usage for CBSA
*******************************************************************
** Script configuration:
   workspace -> /u/mdalbin/Migration-Modeler-DBEHM-work/applications
   metadatastore -> /u/mdalbin/Migration-Modeler-DBEHM-work/dbb-metadatastore
   application -> CBSA
   logFile -> /u/mdalbin/Migration-Modeler-DBEHM-work/logs/3-CBSA-assessUsage.log
   applicationDir -> /u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA
   moveFiles -> false
** Getting the list of files of 'Include File' type.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/datastr.cpy'.
        Files depending on 'CBSA/src/copy/datastr.cpy' :
        'CBSA/CBSA/src/cobol/delcus.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/updcust.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crdtagy3.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crdtagy5.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bankdata.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crecust.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/inqacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/xfrfun.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crdtagy2.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/creacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/delacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crdtagy1.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/inqcust.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/inqacccu.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/dbcrfun.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/updacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crdtagy4.cbl' in 'CBSA' application context
        ==> 'datastr' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'datastr' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/bnk1ccm.cpy'.
        Files depending on 'CBSA/src/copy/bnk1ccm.cpy' :
        'CBSA/CBSA/src/cobol/bnk1ccs.cbl' in 'CBSA' application context
        ==> 'bnk1ccm' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'bnk1ccm' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/bnk1dam.cpy'.
        Files depending on 'CBSA/src/copy/bnk1dam.cpy' :
        'CBSA/CBSA/src/cobol/bnk1dac.cbl' in 'CBSA' application context
        ==> 'bnk1dam' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'bnk1dam' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/paydbcr.cpy'.
        Files depending on 'CBSA/src/copy/paydbcr.cpy' :
        'CBSA/CBSA/src/cobol/dbcrfun.cbl' in 'CBSA' application context
        ==> 'paydbcr' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'paydbcr' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/bnk1cam.cpy'.
        Files depending on 'CBSA/src/copy/bnk1cam.cpy' :
        'CBSA/CBSA/src/cobol/bnk1cac.cbl' in 'CBSA' application context
        ==> 'bnk1cam' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'bnk1cam' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/creacc.cpy'.
        Files depending on 'CBSA/src/copy/creacc.cpy' :
        'CBSA/CBSA/src/cobol/creacc.cbl' in 'CBSA' application context
        ==> 'creacc' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'creacc' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/bnk1dcm.cpy'.
        Files depending on 'CBSA/src/copy/bnk1dcm.cpy' :
        'CBSA/CBSA/src/cobol/bnk1dcs.cbl' in 'CBSA' application context
        ==> 'bnk1dcm' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'bnk1dcm' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/procdb2.cpy'.
        The Include File 'procdb2' is not referenced at all.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/constdb2.cpy'.
        The Include File 'constdb2' is not referenced at all.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/abndinfo.cpy'.
        Files depending on 'CBSA/src/copy/abndinfo.cpy' :
        'CBSA/CBSA/src/cobol/delcus.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/updcust.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bnk1cca.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crdtagy3.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/acctctrl.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/inqacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/xfrfun.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crdtagy2.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/creacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bnk1dcs.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/delacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bnk1tfn.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bnkmenu.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crdtagy1.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bnk1ccs.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/inqcust.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/inqacccu.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bnk1dac.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/dbcrfun.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bnk1cra.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crdtagy5.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crecust.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/abndproc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/custctrl.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bnk1uac.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bnk1cac.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/updacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crdtagy4.cbl' in 'CBSA' application context
        ==> 'abndinfo' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'abndinfo' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/bnk1tfm.cpy'.
        Files depending on 'CBSA/src/copy/bnk1tfm.cpy' :
        'CBSA/CBSA/src/cobol/bnk1tfn.cbl' in 'CBSA' application context
        ==> 'bnk1tfm' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'bnk1tfm' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/bnk1acc.cpy'.
        Files depending on 'CBSA/src/copy/bnk1acc.cpy' :
        'CBSA/CBSA/src/cobol/bnk1cca.cbl' in 'CBSA' application context
        ==> 'bnk1acc' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'bnk1acc' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/proctran.cpy'.
        Files depending on 'CBSA/src/copy/proctran.cpy' :
        'CBSA/CBSA/src/cobol/delacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/delcus.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crecust.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/xfrfun.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/dbcrfun.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/creacc.cbl' in 'CBSA' application context
        ==> 'proctran' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'proctran' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/sortcode.cpy'.
        Files depending on 'CBSA/src/copy/sortcode.cpy' :
        'CBSA/CBSA/src/cobol/delcus.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/updcust.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crdtagy3.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crdtagy5.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/acctctrl.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bankdata.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crecust.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/inqacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/xfrfun.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crdtagy2.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/creacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/delacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/getscode.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/custctrl.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crdtagy1.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/inqcust.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/inqacccu.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/dbcrfun.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/updacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crdtagy4.cbl' in 'CBSA' application context
        ==> 'sortcode' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'sortcode' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/acctctrl.cpy'.
        Files depending on 'CBSA/src/copy/acctctrl.cpy' :
        'CBSA/CBSA/src/cobol/delacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/acctctrl.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bankdata.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/creacc.cbl' in 'CBSA' application context
        ==> 'acctctrl' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'acctctrl' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/custctrl.cpy'.
        Files depending on 'CBSA/src/copy/custctrl.cpy' :
        'CBSA/CBSA/src/cobol/custctrl.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bankdata.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crecust.cbl' in 'CBSA' application context
        ==> 'custctrl' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'custctrl' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/xfrfun.cpy'.
        Files depending on 'CBSA/src/copy/xfrfun.cpy' :
        'CBSA/CBSA/src/cobol/xfrfun.cbl' in 'CBSA' application context
        ==> 'xfrfun' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'xfrfun' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/crecust.cpy'.
        Files depending on 'CBSA/src/copy/crecust.cpy' :
        'CBSA/CBSA/src/cobol/crecust.cbl' in 'CBSA' application context
        ==> 'crecust' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'crecust' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/inqacccu.cpy'.
        Files depending on 'CBSA/src/copy/inqacccu.cpy' :
        'CBSA/CBSA/src/cobol/delcus.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bnk1cca.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/inqacccu.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/creacc.cbl' in 'CBSA' application context
        ==> 'inqacccu' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'inqacccu' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/bnk1cdm.cpy'.
        Files depending on 'CBSA/src/copy/bnk1cdm.cpy' :
        'CBSA/CBSA/src/cobol/bnk1cra.cbl' in 'CBSA' application context
        ==> 'bnk1cdm' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'bnk1cdm' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/getscode.cpy'.
        Files depending on 'CBSA/src/copy/getscode.cpy' :
        'CBSA/CBSA/src/cobol/getscode.cbl' in 'CBSA' application context
        ==> 'getscode' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'getscode' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/consent.cpy'.
        Files depending on 'CBSA/src/copy/consent.cpy' :
        'CBSA/CBSA/src/cobol/dpaytst.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/consent.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/dpayapi.cbl' in 'CBSA' application context
        ==> 'consent' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'consent' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/bnk1mai.cpy'.
        Files depending on 'CBSA/src/copy/bnk1mai.cpy' :
        'CBSA/CBSA/src/cobol/bnkmenu.cbl' in 'CBSA' application context
        ==> 'bnk1mai' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'bnk1mai' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/constapi.cpy'.
        Files depending on 'CBSA/src/copy/constapi.cpy' :
        'CBSA/CBSA/src/cobol/dpaytst.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/consttst.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/consent.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/dpayapi.cbl' in 'CBSA' application context
        ==> 'constapi' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'constapi' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/delacc.cpy'.
        Files depending on 'CBSA/src/copy/delacc.cpy' :
        'CBSA/CBSA/src/cobol/delacc.cbl' in 'CBSA' application context
        ==> 'delacc' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'delacc' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/delcus.cpy'.
        Files depending on 'CBSA/src/copy/delcus.cpy' :
        'CBSA/CBSA/src/cobol/delcus.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bnk1dcs.cbl' in 'CBSA' application context
        ==> 'delcus' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'delcus' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/getcompy.cpy'.
        Files depending on 'CBSA/src/copy/getcompy.cpy' :
        'CBSA/CBSA/src/cobol/getcompy.cbl' in 'CBSA' application context
        ==> 'getcompy' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'getcompy' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/accdb2.cpy'.
        The Include File 'accdb2' is not referenced at all.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/contdb2.cpy'.
        The Include File 'contdb2' is not referenced at all.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/inqcust.cpy'.
        Files depending on 'CBSA/src/copy/inqcust.cpy' :
        'CBSA/CBSA/src/cobol/delcus.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/inqcust.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/inqacccu.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/creacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bnk1dcs.cbl' in 'CBSA' application context
        ==> 'inqcust' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'inqcust' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/updacc.cpy'.
        Files depending on 'CBSA/src/copy/updacc.cpy' :
        'CBSA/CBSA/src/cobol/updacc.cbl' in 'CBSA' application context
        ==> 'updacc' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'updacc' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/inqacc.cpy'.
        Files depending on 'CBSA/src/copy/inqacc.cpy' :
        'CBSA/CBSA/src/cobol/inqacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bnk1dac.cbl' in 'CBSA' application context
        ==> 'inqacc' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'inqacc' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/updcust.cpy'.
        Files depending on 'CBSA/src/copy/updcust.cpy' :
        'CBSA/CBSA/src/cobol/updcust.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bnk1dcs.cbl' in 'CBSA' application context
        ==> 'updcust' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'updcust' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/bnk1uam.cpy'.
        Files depending on 'CBSA/src/copy/bnk1uam.cpy' :
        'CBSA/CBSA/src/cobol/bnk1uac.cbl' in 'CBSA' application context
        ==> 'bnk1uam' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'bnk1uam' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/account.cpy'.
        Files depending on 'CBSA/src/copy/account.cpy' :
        'CBSA/CBSA/src/cobol/dpaytst.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/delacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/delcus.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/consent.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/inqacccu.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/inqacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/xfrfun.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/dbcrfun.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/updacc.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/creacc.cbl' in 'CBSA' application context
        ==> 'account' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'account' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Analyzing impacted applications for file 'CBSA/CBSA/src/copy/customer.cpy'.
        Files depending on 'CBSA/src/copy/customer.cpy' :
        'CBSA/CBSA/src/cobol/delcus.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/updcust.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/inqcust.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/bankdata.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/crecust.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/inqacccu.cbl' in 'CBSA' application context
        'CBSA/CBSA/src/cobol/creacc.cbl' in 'CBSA' application context
        ==> 'customer' is owned by the 'CBSA' application
        ==> Updating usage of Include File 'customer' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/CBSA/CBSA.yaml'.
** Getting the list of files of 'Program' type.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/bnk1cac.cbl'.
        The Program 'bnk1cac' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/proload.cbl'.
        The Program 'proload' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/bnk1dac.cbl'.
        The Program 'bnk1dac' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/dpayapi.cbl'.
        The Program 'dpayapi' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/dpaytst.cbl'.
        The Program 'dpaytst' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/accoffl.cbl'.
        The Program 'accoffl' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/crdtagy5.cbl'.
        The Program 'crdtagy5' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/creacc.cbl'.
        The Program 'creacc' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/crdtagy4.cbl'.
        The Program 'crdtagy4' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/bnkmenu.cbl'.
        The Program 'bnkmenu' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/bankdata.cbl'.
        The Program 'bankdata' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/prooffl.cbl'.
        The Program 'prooffl' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/bnk1tfn.cbl'.
        The Program 'bnk1tfn' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/bnk1cca.cbl'.
        The Program 'bnk1cca' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/dbcrfun.cbl'.
        The Program 'dbcrfun' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/acctctrl.cbl'.
        The Program 'acctctrl' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/custctrl.cbl'.
        The Program 'custctrl' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/xfrfun.cbl'.
        The Program 'xfrfun' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/crecust.cbl'.
        The Program 'crecust' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/inqacccu.cbl'.
        The Program 'inqacccu' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/getscode.cbl'.
        The Program 'getscode' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/consent.cbl'.
        The Program 'consent' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/crdtagy3.cbl'.
        The Program 'crdtagy3' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/delacc.cbl'.
        The Program 'delacc' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/delcus.cbl'.
        The Program 'delcus' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/bnk1dcs.cbl'.
        The Program 'bnk1dcs' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/crdtagy2.cbl'.
        The Program 'crdtagy2' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/abndproc.cbl'.
        The Program 'abndproc' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/bnk1ccs.cbl'.
        The Program 'bnk1ccs' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/crdtagy1.cbl'.
        The Program 'crdtagy1' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/bnk1cra.cbl'.
        The Program 'bnk1cra' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/getcompy.cbl'.
        The Program 'getcompy' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/accload.cbl'.
        The Program 'accload' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/inqcust.cbl'.
        The Program 'inqcust' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/bnk1uac.cbl'.
        The Program 'bnk1uac' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/updacc.cbl'.
        The Program 'updacc' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/consttst.cbl'.
        The Program 'consttst' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/inqacc.cbl'.
        The Program 'inqacc' is not called by any other program.
** Analyzing impacted applications for file 'CBSA/CBSA/src/cobol/updcust.cbl'.
        The Program 'updcust' is not called by any other program.
** Build finished
*******************************************************************
Assess Include files & Programs usage for GenApp
*******************************************************************
** Script configuration:
   workspace -> /u/mdalbin/Migration-Modeler-DBEHM-work/applications
   metadatastore -> /u/mdalbin/Migration-Modeler-DBEHM-work/dbb-metadatastore
   application -> GenApp
   logFile -> /u/mdalbin/Migration-Modeler-DBEHM-work/logs/3-GenApp-assessUsage.log
   applicationDir -> /u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp
   moveFiles -> false
** Getting the list of files of 'Include File' type.
** Analyzing impacted applications for file 'GenApp/GenApp/src/copy/lgpolicy.cpy'.
        Files depending on 'GenApp/src/copy/lgpolicy.cpy' :
        'GenApp/GenApp/src/cobol/lgacdb02.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgacus01.cbl' in 'GenApp' application context
        'UNASSIGNED/UNASSIGNED/src/cobol/oldacdb2.cbl' in 'UNASSIGNED' application context
        'GenApp/GenApp/src/cobol/lgipol01.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgacdb01.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgicus01.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgicdb01.cbl' in 'GenApp' application context
        'UNASSIGNED/UNASSIGNED/src/cobol/oldacdb1.cbl' in 'UNASSIGNED' application context
        ==> 'lgpolicy' referenced by multiple applications - [UNASSIGNED, GenApp]
        ==> Updating usage of Include File 'lgpolicy' to 'public' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp.yaml'.
** Analyzing impacted applications for file 'GenApp/GenApp/src/copy/lgcmared.cpy'.
        The Include File 'lgcmared' is not referenced at all.
** Analyzing impacted applications for file 'GenApp/GenApp/src/copy/lgcmarea.cpy'.
        Files depending on 'GenApp/src/copy/lgcmarea.cpy' :
        'GenApp/GenApp/src/cobol/lgapol01.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgtestc1.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgupvs01.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgacus01.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgastat1.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgdpol01.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgacvs01.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgipol01.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgdpvs01.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgtestp1.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgapvs01.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgucus01.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgupol01.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgtestp2.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgtestp4.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgucvs01.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgicus01.cbl' in 'GenApp' application context
        'GenApp/GenApp/src/cobol/lgtestp3.cbl' in 'GenApp' application context
        ==> 'lgcmarea' is owned by the 'GenApp' application
        ==> Updating usage of Include File 'lgcmarea' to 'private' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/GenApp/GenApp.yaml'.
** Getting the list of files of 'Program' type.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgicus01.cbl'.
        The Program 'lgicus01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgdpol01.cbl'.
        The Program 'lgdpol01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgipdb01.cbl'.
        The Program 'lgipdb01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgtestp3.cbl'.
        The Program 'lgtestp3' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgtestp4.cbl'.
        The Program 'lgtestp4' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgacvs01.cbl'.
        The Program 'lgacvs01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgsetup.cbl'.
        The Program 'lgsetup' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgapol01.cbl'.
        The Program 'lgapol01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgipvs01.cbl'.
        The Program 'lgipvs01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgupol01.cbl'.
        The Program 'lgupol01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgacdb01.cbl'.
        The Program 'lgacdb01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgacdb02.cbl'.
        The Program 'lgacdb02' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgstsq.cbl'.
        The Program 'lgstsq' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgtestp1.cbl'.
        The Program 'lgtestp1' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgtestp2.cbl'.
        The Program 'lgtestp2' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgdpdb01.cbl'.
        The Program 'lgdpdb01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgucus01.cbl'.
        The Program 'lgucus01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgapvs01.cbl'.
        The Program 'lgapvs01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgucdb01.cbl'.
        The Program 'lgucdb01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgdpvs01.cbl'.
        The Program 'lgdpvs01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgtestc1.cbl'.
        The Program 'lgtestc1' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgastat1.cbl'.
        The Program 'lgastat1' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgapdb01.cbl'.
        The Program 'lgapdb01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgicvs01.cbl'.
        The Program 'lgicvs01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgipol01.cbl'.
        The Program 'lgipol01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgacus01.cbl'.
        The Program 'lgacus01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgwebst5.cbl'.
        The Program 'lgwebst5' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgucvs01.cbl'.
        The Program 'lgucvs01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgupdb01.cbl'.
        The Program 'lgupdb01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgicdb01.cbl'.
        The Program 'lgicdb01' is not called by any other program.
** Analyzing impacted applications for file 'GenApp/GenApp/src/cobol/lgupvs01.cbl'.
        The Program 'lgupvs01' is not called by any other program.
** Build finished
*******************************************************************
Assess Include files & Programs usage for RetirementCalculator
*******************************************************************
** Script configuration:
   workspace -> /u/mdalbin/Migration-Modeler-DBEHM-work/applications
   metadatastore -> /u/mdalbin/Migration-Modeler-DBEHM-work/dbb-metadatastore
   application -> RetirementCalculator
   logFile -> /u/mdalbin/Migration-Modeler-DBEHM-work/logs/3-RetirementCalculator-assessUsage.log
   applicationDir -> /u/mdalbin/Migration-Modeler-DBEHM-work/applications/RetirementCalculator
   moveFiles -> false
** Getting the list of files of 'Include File' type.
** Analyzing impacted applications for file 'RetirementCalculator/RetirementCalculator/src/copy/linput.cpy'.
        Files depending on 'RetirementCalculator/src/copy/linput.cpy' :
        'RetirementCalculator/RetirementCalculator/src/cobol/ebud01.cbl' in 'RetirementCalculator' application context
        'GenApp/GenApp/src/cobol/lgacdb01.cbl' in 'GenApp' application context
        'RetirementCalculator/RetirementCalculator/src/cobol/ebud0run.cbl' in 'RetirementCalculator' application context
        ==> 'linput' referenced by multiple applications - [GenApp, RetirementCalculator]
        ==> Updating usage of Include File 'linput' to 'public' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/RetirementCalculator/RetirementCalculator.yaml'.
** Getting the list of files of 'Program' type.
** Analyzing impacted applications for file 'RetirementCalculator/RetirementCalculator/src/cobol/ebud01.cbl'.
        The Program 'ebud01' is not called by any other program.
** Analyzing impacted applications for file 'RetirementCalculator/RetirementCalculator/src/cobol/ebud03.cbl'.
        The Program 'ebud03' is not called by any other program.
** Analyzing impacted applications for file 'RetirementCalculator/RetirementCalculator/src/cobol/ebud02.cbl'.
        Files depending on 'RetirementCalculator/src/cobol/ebud02.cbl' :
        'CBSA/CBSA/src/cobol/abndproc.cbl' in 'CBSA' application context
        ==> 'ebud02' is called from the 'CBSA' application
Adding dependency to application CBSA
        ==> Updating usage of Program 'ebud02' to 'service submodule' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/RetirementCalculator/RetirementCalculator.yaml'.
** Analyzing impacted applications for file 'RetirementCalculator/RetirementCalculator/src/cobol/ebud0run.cbl'.
        The Program 'ebud0run' is not called by any other program.
** Build finished
*******************************************************************
Assess Include files & Programs usage for UNASSIGNED
*******************************************************************
** Script configuration:
   workspace -> /u/mdalbin/Migration-Modeler-DBEHM-work/applications
   metadatastore -> /u/mdalbin/Migration-Modeler-DBEHM-work/dbb-metadatastore
   application -> UNASSIGNED
   logFile -> /u/mdalbin/Migration-Modeler-DBEHM-work/logs/3-UNASSIGNED-assessUsage.log
   applicationDir -> /u/mdalbin/Migration-Modeler-DBEHM-work/applications/UNASSIGNED
   moveFiles -> false
** Getting the list of files of 'Include File' type.
*** No source found with 'Include File' type.
** Getting the list of files of 'Program' type.
** Analyzing impacted applications for file 'UNASSIGNED/UNASSIGNED/src/cobol/flemssub.cbl'.
        Files depending on 'UNASSIGNED/src/cobol/flemssub.cbl' :
        'UNASSIGNED/UNASSIGNED/src/cobol/flemsmai.cbl' in 'UNASSIGNED' application context
        ==> 'flemssub' is called from the 'UNASSIGNED' application
        ==> Updating usage of Program 'flemssub' to 'internal submodule' in '/u/mdalbin/Migration-Modeler-DBEHM-work/applications/UNASSIGNED/UNASSIGNED.yaml'.
** Analyzing impacted applications for file 'UNASSIGNED/UNASSIGNED/src/cobol/oldacdb2.cbl'.
        The Program 'oldacdb2' is not called by any other program.
** Analyzing impacted applications for file 'UNASSIGNED/UNASSIGNED/src/cobol/oldacdb1.cbl'.
        The Program 'oldacdb1' is not called by any other program.
** Analyzing impacted applications for file 'UNASSIGNED/UNASSIGNED/src/cobol/flemsmai.cbl'.
        The Program 'flemsmai' is not called by any other program.
** Build finished
~~~~

</details>

## Migrations scenarios for Migration-Modeler-Start script

### A group of datasets belongs to the same application

In this situation, a group of datasets already contain all artifacts that belong to the application. These identified artifacts can be spread across multiples libraries but you are certain they are all owned by the same application.

To limit the scope of the extraction, this list of datasets to analyze must be passed to the [Extract Applications script (1-extractApplication.sh)](./src/scripts/utils/1-extractApplications.sh).
In this use case, a specific `Applications Mapping` YAML file for each application should be passed to the [Extract Applications script](./src/scripts/utils/1-extractApplications.sh) via the Migration Modeler configuration file, with a universal filter being used as naming convention.

The following is an example of such an `Applications Mapping` YAML file (named *applicationsMapping-CATMAN.yaml*)
~~~~YAML
applications:
  - application: "Catalog Manager"
    description: "Catalog Manager"
    owner: "MDALBIN"
    namingConventions:
      - ........
~~~~

To extract the files, a sample command like the following should be used:

~~~~
./1-extractApplications.sh -c /u/dbehm/git/dbb-git-migration-modeler-mathieu/DBB_GIT_MIGRATION_MODELER-CATMAN.config
~~~~

While the `DBB_GIT_MIGRATION_MODELER-CATMAN.config` contains the CATMAN specific datasets:
~~~~
...
APPLICATION_DATASETS=GITLAB.CATMAN.RELEASE.COBOL,GITLAB.CATMAN.RELEASE.COPY,GITLAB.CATMAN.RELEASE.ASM,GITLAB.CATMAN.RELEASE.BMS,GITLAB.CATMAN.RELEASE.LINK
...
~~~~

The result of this command is an Application Descriptor file that documents all the artifacts contained in the list of the given datasets, and a DBB Migration mapping file to manages all the members found.

### A group of datasets contains artifacts that belong to multiple applications

In this configuration, the list of datasets provided as input contain artifacts from different applications, but a naming convention can be leveraged to filter members. In the following example, the naming convention is based on the first 3 letters of the members' name. There is one exception, where we have a fully qualified member name (*LINPUT*) that is owned by the *RetirementCalculator* application:

~~~~YAML
applications:
  - application: "RetirementCalculator"
    description: "RetirementCalculator"
    owner: "MDALBIN"
    namingConventions:
      - EBU.....
      - LINPUT..
  - application: "GenApp"
    description: "GenApp"
    owner: "DBEHM"
    namingConventions:
      - LGA.....
      - LGD.....
      - LGI.....
      - LGT.....
      - LGU.....
      - LGS.....
      - LGW.....
      - OLD.....
      - FLE.....
  - application: "CBSA"
    description: "CBSA"
    owner: "MDALBIN"
    namingConventions:
      - ABN.....
      - ACC.....
      - BAN.....
      - BNK.....
      - CON.....
      - CRD.....
      - CRE.....
      - CUS.....
      - DBC.....
      - DEL.....
      - DPA.....
      - GET.....
      - INQ.....
      - PRO.....
      - UPD.....
      - XFR.....
      - ZUN.....
~~~~

The result of this command is a set of Application Descriptor files and DBB Migratin mamming files for each discovered application.
If a member doesn't match any naming convention, it is assigned to a special application called *UNASSIGNED*.

### Working with the special *UNASSIGNED* application

A good strategy could be to store all the shared Include Files in this *UNASSIGNED* application.
This can be done in several ways: as mentioned earlier, all artifacts for which no naming convention is matching will be assigned to this special application.
Otherwise, if a library is known to contain only shared Include Files, a specific `Applications Mapping` file could be used, as follows:
~~~~YAML
applications:
  - application: "UNASSIGNED"
    description: "Shared include files"
    owner: "Shared ownership"
    namingConventions:
      - ........
~~~~

### Combining use cases

There can be situations where scenarios must be combined to extract the applications. For instance, a given library contains artifacts from one application, while other libraries contain files from multiple applications. Or you need to apply different naming conventions patterns for copybooks. 

In that case, the solution is to run the [Extract Applications script (1-extractApplication.sh)](./src/scripts/utils/1-extractApplications.sh) multiple times with different input configuration files: The [Migration-Modeler-Start.sh](./src/scripts/Migration-Modeler-Start.sh) can be customized in this way to contain multiple extractions:

~~~~bash
# Configuration specifies an applictionMappings file for CATMAN and the CATMAN PDS libraries
./1-extractApplications.sh -c /u/dbehm/git/dbb-git-migration-modeler-work/DBB_GIT_MIGRATION_MODELER-CATMAN.config
# Configuration specifies an applictionMappings file the perceived SHARED components
./1-extractApplications.sh -c /u/dbehm/git/dbb-git-migration-modeler-work/DBB_GIT_MIGRATION_MODELER-SHARED.config
~~~~

### Generating properties

We encourage, as much as possible, to use simple scenarios, to avoid unnecessary complexity in the combination of types configurations.
However, some configuration may require to use combination of types, depending on how properties are set in the originating SCM solution.

#### Common scenario

In a simple scenario, each artifact is assigned with one single type, that designates a known configuration in the legacy SCM tool.

For instance, the [Types file](./samples/types.txt) could contain the following lines:
~~~~
PGM001, COBBATCH
PGM002, COBCICSD
PMG003, PLIIMSDB
~~~~

Where *COBBATCH*, *COBCICSD* and *PLIIMSDB* represent configurations with specific properties. These types should be defined in the [Types Configurations file](./samples/typesConfigurations.yaml) accordingly, for instance:

~~~~YAML
- typeConfiguration: "COBBATCH"
  cobol_compileParms: "LIB,SOURCE"
  cobol_linkedit: true
  isCICS: false
  isSQL: false
- typeConfiguration: "COBCICSD"
  cobol_compileParms: "LIB,SOURCE,CICS,SQL"
  cobol_linkedit: true
  isCICS: true
  isSQL: true
- typeConfiguration: "PLIIMSDB"
  pli_compileParms: "PP(INCLUDE('ID(++INCLUDE)')),SYSTEM(IMS)"
  pli_linkedit: true
  isCICS: false
  isSQL: false
  isDLI: true
~~~~

With this configuration, the [Property Generation script](./src/scripts/utils//4-generateProperties.sh) will generate Language Configurations for each of these types.

#### Advanced scenario
In more sophisticated scenarios, which depend on how properties are set in the legacy SCM tool, multiple types can be assigned to an artifact:
~~~~
PGM001, COBOL, BATCH
PGM002, COBOL, CICSDB2
PMG003, PLI, IMSDB
~~~~

Each type configuration would be defined separately in the [Types Configurations file](./samples/typesConfigurations.yaml), for instance:

~~~~YAML
- typeConfiguration: "COBOL"
  cobol_compileParms: "LIB,SOURCE"
  cobol_linkedit: true
- typeConfiguration: "PLI"
  pli_compileParms: "PP(INCLUDE('ID(++INCLUDE)'))"
  pli_linkedit: true
- typeConfiguration: "BATCH"
  isCICS: false
- typeConfiguration: "CICSDB2"
  isCICS: true
  isSQL: true
- typeConfiguration: "IMSDB"
  pli_compileIMSParms: SYSTEM(IMS)  
  isCICS: false
  isSQL: false
  isDLI: true
~~~~

In this configuration, the [Property Generation script](./src/scripts/utils/4-generateProperties.sh) will generate composite Language Configurations files in *dbb-zAppbuild*'s [build-conf/language-conf](https://github.com/IBM/dbb-zappbuild/tree/develop/build-conf/language-conf) folder.
In this example, 3 files would be created:
* *BATCH-COBOL.properties* which combines properties from the *BATCH* and the *COBOL* types
* *CICSDB2-COBOL.properties*, which combines properties from the *CICSDB2* and the *COBOL* types
* *IMSDB-PLI.properties*, which combines properties from the *IMSDB* and *PLI* types

The name of composite types are based on the names of the originating types sorted alphabetically, to avoid duplication.
The Language Configuration mapping file in each application's *application-conf* folder contains mappings between artifacts and their associated composite types, also sorted alphabetically.
