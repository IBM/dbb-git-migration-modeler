# Setting up the DBB Git Migration Modeler utility

Install the DBB Git Migration Modeler by cloning [this repository](https://github.com/IBM/dbb-git-migration-modeler) to z/OS Unix System Services.

## Pre-requisites when using a Db2-based MetadataStore with DBB

The DBB Git Migration Modeler can use either a file-based MetadataStore or a Db2-based MetadataStore with DBB.
When using a file-based MetadataStore, the location of the MetadataStore is specified during the setup phase of the DBB Git Migration Modeler, as described in the next section, through the `DBB_MODELER_FILE_METADATA_STORE_DIR` property. No additional setup is required.

When using a Db2-based MetadataStore, some configuration steps must be executed prior to using the DBB Git Migration Modeler. A Db2 database and the Db2 tables corresponding to the DBB-provided schema must be created.
Instructions to create a Db2-based MetadataStore with DBB can be found in this [documentation page](https://www.ibm.com/docs/en/dbb/3.0?topic=setup-configuring-db2-zos-as-metadata-database) for Db2 z/OS and this [documentation page](https://www.ibm.com/docs/en/dbb/3.0?topic=setup-configuring-db2-luw-as-metadata-database) for Db2 LUW.

The configuration to access the Db2-based MetadataStore with the DBB Git Migration Modeler is performed through the `DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE`, `DBB_MODELER_DB2_METADATASTORE_ID`, `DBB_MODELER_DB2_METADATASTORE_PASSWORD` and `DBB_MODELER_DB2_METADATASTORE_PASSWORDFILE` properties.
These required properties are collected during the Setup phase, as described in the next section. The Setup script then suggests to use the `CheckDb2MetadataStore.sh` script to verify the correct connectivity to the Db2 database.
Once the Db2 MetadataStore connection is correctly configured and checked, the DBB Git Migration Modeler is ready to be used with a Db2-based MetadataStore.

## Setting up the DBB Git Migration Modeler configuration

Once installed on z/OS Unix System Services, the [Setup.sh script](./Setup.sh) must be run to configure the DBB Git Migration Modeler, and set configuration parameters.
This script prompts for the below environment variables and saves them in a configuration file, that is used as an input for the different DBB Git Migration Modeler scripts.

| Configuration Parameter | Description | Default Value |
| --- | --- | --- |
| DBB_MODELER_HOME  | The home of the DBB Git Migration Modeler on z/OS Unix System Services. This parameter is automatically computed and typically represents the location where the DBB Git Migration Modeler has been cloned. | The current directory of Setup.sh |
| DBB_MODELER_WORK  | The working directory for the DBB Git Migration Modeler on z/OS Unix System Services, where the all the migration activities will be performed. Requires to be sized to store the entire copy of all application programs. | The default value is computed based on the home of the DBB Git Migration Modeler, to which a `-work` suffix is appended: `$DBB_MODELER_HOME-work` |
| DBB_MODELER_APPCONFIG_DIR  | The working folder where the initial version of the Application Descriptor files and the generated DBB Migration Mapping files are stored during the migration process. | The default value is computed based on the DBB Git Migration Modeler working folder, where a `work-configs` subfolder is created: `$DBB_MODELER_WORK/work-configs` |
| DBB_MODELER_APPLICATION_DIR  | The working folder where the DBB Git Migration Modeler will create the application directories. | The default value is computed based on the DBB Git Migration Modeler working folder, where a `work-applications` subfolder is created: `$DBB_MODELER_WORK/work-applications` |
| DBB_MODELER_LOGS  | The working folder where the DBB Git Migration Modeler will store the log files for the various steps of migration process. | The default value is computed based on the DBB Git Migration Modeler working folder, where a `work-logs` subfolder is created: `$DBB_MODELER_WORK/work-logs` | 
| DEFAULT_GIT_CONFIG  | The configuration folder containing default `.gitattributes` and `zapp.yaml` files. | The default value is computed based on the DBB Git Migration Modeler working folder, where a `git-config` subfolder is created: `$DBB_MODELER_WORK/git-config` |
| **DBB Git Migration Modeler MetadataStore configuration** | | | 
| DBB_MODELER_METADATASTORE_TYPE  | The type of DBB MetadataStore to be used by the DBB Git Migration Modeler. Valid values are `file` or `db2`. | `file` |
| DBB_MODELER_FILE_METADATA_STORE_DIR  | If a File-based DBB MetadataStore is used, this parameters indicates the location of the File MetadataStore. | The default value is computed based on the DBB Git Migration Modeler working folder, where a `dbb-metadatastore` subfolder is created: `$DBB_MODELER_WORK/dbb-metadatastore` |
| DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE  | If a Db2-based DBB MetadataStore is used, this parameters indicates the path to a Db2 MetadataStore Connection configuration file. This file is used to configure the connection details to the Db2 Database Manager (either Db2 z/OS or Db2 LUW). Documentation about this file is available [on this page](https://www.ibm.com/docs/en/adffz/dbb/3.0.0?topic=apis-metadata-store). | The default value is computed based on the DBB Git Migration Modeler working folder: `$DBB_MODELER_WORK/db2Connection.conf` |
| DBB_MODELER_DB2_METADATASTORE_JDBC_ID  | If a Db2-based DBB MetadataStore is used, this parameters indicates the DB2 JDBC User ID to connect through the JDBC driver. | `user` |
| DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORD  | If a Db2-based DBB MetadataStore is used, this parameters indicates the DB2 JDBC User ID's Password to connect through the JDBC driver. | No default value |
| DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE  | If a Db2-based DBB MetadataStore is used, this parameters indicates the path to the DB2 JDBC Password file to connect through the JDBC driver. This approach is recommended, documentation can be found [on this page](https://www.ibm.com/docs/en/adffz/dbb/3.0.0?topic=customization-encrypting-metadata-store-passwords). | No default value |
| **DBB Git Migration Modeler Input files** | | | 
| DBB_MODELER_APPMAPPINGS_DIR  | The folder containing the Applications Mapping file(s) defining the input datasets for applications and their naming conventions. More information can be found in [Configuring the Migration Modeler input files](03-Configuration.md#configuring-the-migration-modeler-input-files). | The default value is computed based on the DBB Git Migration Modeler working folder, where a `applications-mappings` subfolder is created: `$DBB_MODELER_WORK/applications-mappings` | 
| REPOSITORY_PATH_MAPPING_FILE  | The path to the Repository Paths Mapping file that maps the various types of members to the folder layout in Git. More information can be found in [Configuring the Migration Modeler input files](03-Configuration.md#configuring-the-migration-modeler-input-files). | The default value is computed based on the DBB Git Migration Modeler working folder: `$DBB_MODELER_WORK/repositoryPathsMapping.yaml` | 
| APPLICATION_MEMBER_TYPE_MAPPING  | The path to the Member to Type mapping (types.txt). More information can be found in [Configuring the Migration Modeler input files](03-Configuration.md#configuring-the-migration-modeler-input-files). | The default value is computed based on the DBB Git Migration Modeler working folder: `$DBB_MODELER_WORK/types.txt` | 
| TYPE_CONFIGURATIONS_FILE | The path to the Type Configuration file, which helps generate zAppBuild Language Configurations to reproduce existing build configurations. More information can be found in [Configuring the Migration Modeler input files](03-Configuration.md#configuring-the-migration-modeler-input-files). | The default value is computed based on the DBB Git Migration Modeler working folder: `$DBB_MODELER_WORK/typesConfigurations.yaml` |
| **DBB Git Migration Modeler configuration parameters** | | | 
| APPLICATION_ARTIFACTS_HLQ | The High-Level Qualifier of the datasets used during the Preview Build. These datasets contain a copy of the artifacts to be packaged as a baseline during [The Initialization phase](01-Storyboard.md#the-initialization-phase). | `DBEHM.MIG` | 
| SCAN_DATASET_MEMBERS | The flag that determines if [The Framing phase](01-Storyboard.md#the-framing-phase) process should scan each member to identify source type. If set to `true`, the DBB Scanner is used to understand the nature of the artifact (COBOL program, COBOL copybook, etc.) and this information is used first when assigning the artifact to a source group. When set to false, other informations like the type (defined in Types.txt) or the Low-Level Qualifier is used to assign the artifact to the correct source group. | `false` |
| SCAN_DATASET_MEMBERS_ENCODING | If the flag `SCAN_DATASET_MEMBERS`is set to `true`, this parameter indicated the encoding to be used by the DBB Scanner when determining the artifacts type. | `IBM-1047` |
| DBB_ZAPPBUILD | The path to a customized [dbb-zAppBuild repository](https://github.com/IBM/dbb-zappbuild) on z/OS Unix System Services for baseline builds during [The Initialization phase](01-Storyboard.md#the-initialization-phase). | `/var/dbb/dbb-zappbuild` |
| DBB_COMMUNITY_REPO | The path to a customized [DBB community repository](https://github.com/IBM/dbb) on z/OS Unix System Services for baseline packaging during [The Initialization phase](01-Storyboard.md#the-initialization-phase). | `/var/dbb/dbb` |
| APPLICATION_DEFAULT_BRANCH | The default branch name when initializing Git repositories and scanning files into DBB collections. |  `main` |
| INTERACTIVE_RUN | The flag to indicate if the Migration-Modeler-Start script should run interactively (set to `true`) or in batch (set to `false`). | `false` |
| PUBLISH_ARTIFACTS | The flag to indicate if baseline packages should be uploaded to an Artifact Repository server (set to `true`) or not (set to `false`). | `true` |
| ARTIFACT_REPOSITORY_SERVER_URL | If the `PUBLISH_ARTIFACTS` parameter is set to `true`, the URL of the Artifact Repository Server when baseline archives are meant to be uploaded. | No default value |
| ARTIFACT_REPOSITORY_USER | If the `PUBLISH_ARTIFACTS` parameter is set to `true`, the user ID to connect to the Artifact Repository Server. | `admin` |
| ARTIFACT_REPOSITORY_PASSWORD | If the `PUBLISH_ARTIFACTS` parameter is set to `true`, the password to connect to the Artifact Repository Server. | No default value |
| PIPELINE_USER | The Security Server User ID of the pipeline user on z/OS. This user is typically the owner of the DBB Collections in the DBB MetadataStore and has authorities to run builds and deployments. This user is often the user the runs the pipeline tasks on z/OS. | `ADO` |
| PIPELINE_USER_GROUP | The group that the Security Server User ID of the pipeline user belongs to. | `JENKINSG` |
| PIPELINE_CI | The pipeline orchestrator used. This parameter is used during [The Initialization phase](01-Storyboard.md#the-initialization-phase) to create pipeline configurations. Valid values are `AzureDevOps`, `GitlabCI`, `Jenkins` or `GitHubActions`. | `AzureDevOps` |

# Configuring the Migration Modeler input files

To configure the DBB Git Migration Modeler, follow the instructions [on this page](./03-Configuration.md).