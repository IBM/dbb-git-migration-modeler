# Configuring the Migration Modeler input files 

The configuration files required to use the DBB Git Migration Modeler utility are copied by the [Validation](../src/scripts/utils/0-validateConfiguration.sh) script (called by the [Setup](../Setup.sh) script) from the [samples](../samples/) folder to the **work** folder that was specified during setup process.

Four types of configuration files need to be reviewed and potentially adapted to your installation and your needs, before using the DBB Git Migration Modeler: 

1. The [Applications Mapping file(s)](../samples/applications-mapping/applicationsMapping.yaml) (YAML format) contains the list of existing applications with their naming convention patterns, using standard glob patterns, used for filtering members. It can be created manually or can be filled with information coming from external databases or provided by a report from an SCM solution. Instead of patterns for naming conventions, the file also accepts fully qualified member names that can be extracted from an existing data source or report provided by your legacy tool.  
If no naming convention is applied for a given application, or if all the members of a given list of datasets belong to the same application, a naming convention whose value is `*` should be defined.
Members in the input PDSs libraries that do not match any convention will be associated to the *UNASSIGNED* application. This is often applicable for include files that do not have an owner assigned.
Multiple Applications Mapping files can be specified, that define one or multiple application configurations. The DBB Git Migration Modeler will import all the Applications Mapping files first, before processing the mappings. This configuration helps for more granular configurations and advanced scenarios, for instance when the input datasets contain members from only one application or when multiple applications have artifacts mixed in the same group of datasets.

2. The [Repository Paths Mapping file](../samples/repositoryPathsMapping.yaml) (YAML format) is required and describes the folder structure on z/OS UNIX System Services (USS) that will contain the files to are moved from the datasets. It is recommended to use the definitions provided in the template, and keep consistent definitions for all applications being migrated.
The file controls how dataset members should be assigned to target subfolders on USS during the migration process. 
Each *Repository Path* entry described in this file documents the type of artifacts in this folder, their file extension, their encoding, the source group they belong to, the language processor (for instance, the language script in dbb-zAppBuild) and criteria to meet for classification. Thse criterai can either be the low-level qualifiers of the dataset which hold them, or their associated types (if any, as described in the [Types file](../samples/types.txt)) or, if enabled, the scan result provided by the DBB Scanner.  
For each repository path, the `artifactsType` property is used during [the Assessment phase](01-Storyboard.md#the-assessment-phase), to filter out for each type of artifacts to perform the assessment.
Only artifacts of types `Program` or `Include File` will be included in the analysis.
It is recommended to keep the current settings defined in the provided [Repository Paths Mapping file](../samples/repositoryPathsMapping.yaml) for the `artifactsType` property.

3. The [Types file](../samples/types.txt) (CSV format) lists the dataset members and their associated type (like a language definition), as described in the legacy SCM tool. This CSV file is optional, and should be built with an SCM-provided utility or from an SCM-provided report. Types mapping are meant to be used only for programs, not for Includes Files.  
Lines of this file are composed of the artifact's names, followed by a list of comma-separated types. A combination of types can be specified, which will then turn into a composite type definition in dbb-zAppBuild.  
During the [Framing phase](01-Storyboard.md#the-framing-phase), the *type* information can be used as a criteria to dispatch files.
If no type is assigned to a given artifact, this information will not be used to dispatch the file and this element will be of type *UNKNOWN* in the Application Descriptor file.  
The type assigned to each artifact is used in the [Property Generation phase](01-Storyboard.md#the-property-generation-phase) to create Language Configuration in [dbb-zAppBuild](https://github.com/IBM/dbb-zappbuild/)'s configuration.

4. The [Types Configurations file](../samples/typesConfigurations.yaml) (YAML format) defines the build configurations with their *dbb-zAppBuild* build properties and values.
This information is typically extracted from the legacy SCM tool and mapped to the equivalent build property in *dbb-zAppBuild*. It is recommended to use ad-hoc automation, when applicable, to facilitate the creation of this file.
This file is only used during the [Property Generation phase](01-Storyboard.md#the-property-generation-phase).
Each Type Configuration defines properties that are used by the [dbb-zAppBuild](https://github.com/IBM/dbb-zappbuild/) framework.
Types can be combined depending on definitions found in the [Types file](../samples/types.txt), to generate composite types combining different properties.

## Required input datasets

The utility is operating on a set of provided PDS libraries that contain a copy of the codebase to be migrated. Depending on your configuration, it may be required to unload the source files from the legacy SCM's storage, prior to using the DBB Git Migration Modeler. These datasets should be extracted from the legacy SCM system, using a SCM-provided utility or mechanism. The list of datasets that contain source files is defined [Applications Mapping file](../samples/applications-mapping/applicationsMapping.yaml) for a set of applications.

Also, the latest steps of the whole migration process are performing a preview build and the packaging of existing artifacts. These existing artifacts (loadmodules, DBRMs, and any other artifacts meant to be deployed belonging to the applications) are expected to be found in datasets, following the naming convention in dbb-zAppBuild for output datasets. Typically, loadmodules are stored in to a `HLQ.LOAD` library, object decks in a `HLQ.OBJ` library and DBRMS in a `HLQ.DBRM` library. The HLQ used during this phase is provided through the `APPLICATION_ARTIFACTS_HLQ` environment variable defined during the execution of the [Setup](../Setup.sh) script.

## Define Applications Mapping files

This section outlines examples how to configure the `Applications Mapping` files to define the known ownership of files for [the framing phase](01-Storyboard.md#the-framing-phase).

### A group of datasets contains artifacts belonging to multiple applications

In this configuration, the list of datasets defined in the `Applications Mapping` file contain artifacts from different applications, but a naming convention can be used to filter members. In the following example, the naming convention is based on the first 3 letters of the members' name. There is one exception, where we have a fully qualified member name (*LINPUT*) that is owned by the *RetirementCalculator* application:

~~~~YAML
datasets:
  - APPS.COBOL
  - APPS.COPY
  - APPS.BMS
applications:
  - application: "RetirementCalculator"
    description: "RetirementCalculator"
    owner: "MDALBIN"
    namingConventions:
      - EBU*
      - LINPUT
  - application: "GenApp"
    description: "GenApp"
    owner: "DBEHM"
    namingConventions:
      - LGA*
      - LGD*
      - LGI*
      - LGT*
      - LGU*
      - LGS*
      - LGW*
      - OLD*
      - FLE*
  - application: "CBSA"
    description: "CBSA"
    owner: "MDALBIN"
    namingConventions:
      - ABN*
      - ACC*
      - BAN*
      - BNK*
      - CON*
      - CRD*
      - CRE*
      - CUS*
      - DBC*
      - DEL*
      - DPA*
      - GET*
      - INQ*
      - PRO*
      - UPD*
      - XFR*
      - ZUN*
~~~~

The result of this command is a set of Application Descriptor files and DBB Migration mapping files for each discovered application.
If a member of the input datasets doesn't match any naming convention, it is assigned to a special application called *UNASSIGNED*.

*Alternatively* you can also split up the configuration in multiple application mapping files, allowing teams to pass in their information separately:

~~~~YAML
datasets:
  - APPS.COBOL
  - APPS.COPY
  - APPS.BMS
applications:
  - application: "RetirementCalculator"
    description: "RetirementCalculator"
    owner: "MDALBIN"
    namingConventions:
      - EBU*
      - LINPUT
~~~~

~~~~YAML
datasets:
  - APPS.COBOL
  - APPS.COPY
  - application: "GenApp"
    description: "GenApp"
    owner: "DBEHM"
    namingConventions:
      - LGA*
      - LGD*
      - LGI*
      - LGT*
      - LGU*
      - LGS*
      - LGW*
      - OLD*
      - FLE*
~~~~

~~~~YAML
datasets:
  - APPS.COBOL
  - APPS.COPY
  - application: "CBSA"
    description: "CBSA"
    owner: "MDALBIN"
    namingConventions:
      - ABN*
      - ACC*
      - BAN*
      - BNK*
      - CON*
      - CRD*
      - CRE*
      - CUS*
      - DBC*
      - DEL*
      - DPA*
      - GET*
      - INQ*
      - PRO*
      - UPD*
      - XFR*
      - ZUN*
~~~~

### A group of datasets contains artifacts belonging to just one application

In this scenario, a group of datasets only contains artifacts that belong to one application exclusively.

To configure this case, an application-specific `Applications Mapping` file for the application needs to be provided in the folder of the Applications Mappings.

An universal naming convention filter like in the below sample should be used, because all artifacts from the application-specific input datasets are mapped to the defined application.

The following is an example of such an `Applications Mapping` YAML file (named *applicationsMapping-CATMAN.yaml*)
~~~~YAML
datasets:
  - CATMAN.COBOL
  - CATMAN.COPY
  - CATMAN.BMS
applications:
  - application: "Catalog Manager"
    description: "Catalog Manager"
    owner: "MDALBIN"
    namingConventions:
      - *
~~~~

It is common to have shared files that do not have an owner, and do not belong to an application - for instance copybooks or include files.

To pass information to Migration Modeler about datasets containing these shared copybooks or include files in this scenario, create an Applications Mapping file (ex: *SHARED.yaml*) pointing to the shared libraries, but don't list any applications:

~~~~YAML
datasets:
  - APPS.COPYLIB
  - APPS.INCLIB
applications: []
~~~~

When running the Migration Modeler with these Applications Mapping files, all the artifacts found in the input datasets (CATMAN.COBOL, CATMAN.COPY and CATMAN.BMS) will be assigned to the Catalog Manager application, and artifacts found in APPS.COPYLIB and APPS.INCLIB will be initially assigned to the special application called *UNASSIGNED*.

### Working with source code that is known to be shared

For files that are already known as shared between applications and are planned to be managed in their own git repository, you can define an Applications Mapping configuration to define their dedicated context. If one library already contains these known shared include files, configure a specific `Applications Mapping` file alike the below sample:

~~~~YAML
datasets:
  - SHARED.COPY
applications:
  - application: "SHARED"
    description: "Shared include files"
    owner: "Shared ownership"
    namingConventions:
      - *
~~~~

With this application mapping file, all files of 'SHARED.COPY' will be initially assigned to the application *SHARED*.

If shared code follows naming conventions, you can also provide naming conventions.

### Combining use cases

You can combine the above scenarios to configure Applications Mapping files to use with the Migration Modeler. For instance, a given library contains artifacts from one application, while other libraries contain files from multiple applications. Or you need to apply specific naming conventions for include files.

In that case, the solution is to configure multiple Applications Mapping files:
- One Applications Mapping file would contain the definition for the datasets having artifacts belonging to only one application
- A second Applications Mapping file would contain the definitions for the datasets having artifacts from multiple applications.

The [Migration-Modeler-Start script](../src/scripts/Migration-Modeler-Start.sh) will process all available Applications Mapping files. 

## Generating properties

We encourage, as much as possible, to use simple scenarios, to avoid unnecessary complexity in the combination of types configurations.
However, some configuration may require to use combination of types, depending on how properties are set in the originating SCM solution.

### Common scenario

In a simple scenario, each artifact is assigned with one single type, that designates a known configuration in the legacy SCM tool.

For instance, the [Types file](../samples/types.txt) could contain the following lines:
~~~~
PGM001, COBBATCH
PGM002, COBCICSD
PMG003, PLIIMSDB
~~~~

Where *COBBATCH*, *COBCICSD* and *PLIIMSDB* represent configurations with specific properties. These types should be defined in the [Types Configurations file](../samples/typesConfigurations.yaml) accordingly, for instance:

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

With this configuration, the [Property Generation script](../src/scripts/utils/4-generateProperties.sh) will generate Language Configurations for each of these types.

### Advanced scenario
In more sophisticated scenarios, which depend on how properties are set in the legacy SCM tool, multiple types can be assigned to an artifact:
~~~~
PGM001, COBOL, BATCH
PGM002, COBOL, CICSDB2
PMG003, PLI, IMSDB
~~~~

Each type configuration would be defined separately in the [Types Configurations file](../samples/typesConfigurations.yaml), for instance:

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

In this configuration, the [Property Generation script](../src/scripts/utils/4-generateProperties.sh) will generate composite Language Configurations files in *dbb-zAppbuild*'s [build-conf/language-conf](https://github.com/IBM/dbb-zappbuild/tree/main/build-conf/language-conf) folder.
In this example, 3 files would be created:
* *BATCH-COBOL.properties* which combines properties from the *BATCH* and the *COBOL* types
* *CICSDB2-COBOL.properties*, which combines properties from the *CICSDB2* and the *COBOL* types
* *IMSDB-PLI.properties*, which combines properties from the *IMSDB* and *PLI* types

The name of composite types are based on the names of the originating types sorted alphabetically, to avoid duplication.
The Language Configuration mapping file in each application's *application-conf* folder contains mappings between artifacts and their associated composite types, also sorted alphabetically.

# Working with the DBB Git Migration Modeler utility

To work with the DBB Git Migration Modeler, follow the instructions [on this page](./04-Usage.md).