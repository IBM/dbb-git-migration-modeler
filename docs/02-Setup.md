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
| DBB_MODELER_HOME  | The home of the DBB Git Migration Modeler project | The current directory of Setup.sh |
| DBB_MODELER_WORK  | The working directory for the DBB Git Migration Modeler. Requires to be sized to store the entire copy of all application programs. | `$DBB_MODELER_HOME-work` | 
| DBB_MODELER_APPCONFIG_DIR  | Stores the initial version of the Application Descriptor and the generated DBB Migration Mapping files | `$DBB_MODELER_WORK/work-configs` |
| DBB_MODELER_APPLICATION_DIR  | Path to where the DBB Git Migration Modeler will create the application directories | `$DBB_MODELER_WORK/work-applications` | 
| DBB_MODELER_LOGS  | Path to where the DBB Git Migration Modeler will store the log files of the various steps of Migration Modeler process | `$DBB_MODELER_WORK/work-logs` | 
| DEFAULT_GIT_CONFIG  | Folder containing default `.gitattributes` and `zapp.yaml` files | `$DBB_MODELER_WORK/git-config` |
| **DBB Git Migration Modeler MetadataStore configuration** | | | 
| DBB_MODELER_METADATASTORE_TYPE  | Type of MetadataStore - Valid values are "file" or "db2" | `file` |
| DBB_MODELER_FILE_METADATA_STORE_DIR  | Location of the File MetadataStore | `$DBB_MODELER_WORK/dbb-metadatastore` |
| DBB_MODELER_DB2_METADATASTORE_CONFIG_FILE  | Path for the DB2 Metadatastore Connection configuration file | `$DBB_MODELER_WORK/db2Connection.conf` |
| DBB_MODELER_DB2_METADATASTORE_JDBC_ID  | DB2 JDBC User ID to connect through the JDBC driver | `user` |
| DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORD  | DB2 JDBC User ID's Password to connect through the JDBC driver |  |
| DBB_MODELER_DB2_METADATASTORE_JDBC_PASSWORDFILE  | Default path for the DB2 JDBC Password file to connect through the JDBC driver (recommended) |  |
| **DBB Git Migration Modeler Input files** | | | 
| DBB_MODELER_APPMAPPINGS_DIR  | Folder containing the Applications Mapping file(s) defining the input datasets for applications and their naming conventions. See [Tailoring of input files](#tailoring-the-input-files). | `$DBB_MODELER_WORK/applications-mappings` | 
| REPOSITORY_PATH_MAPPING_FILE  | Repository Paths Mapping file map the various types of members to the folder layout in Git. See [Tailoring of input files](#tailoring-the-input-files). | `$DBB_MODELER_WORK/repositoryPathsMapping.yaml` | 
| APPLICATION_MEMBER_TYPE_MAPPING  | Member to Type mapping. See [Tailoring of input files](#tailoring-the-input-files). | `$DBB_MODELER_WORK/types.txt` | 
| TYPE_CONFIGURATIONS_FILE | Type Configuration to generate zAppBuild Language Configurations to statically preserve existing build configuration. See [Tailoring of input files](#tailoring-the-input-files). | `$DBB_MODELER_WORK/typesConfigurations.yaml` |
| **DBB Git Migration Modeler configuration parameters** | | | 
| APPLICATION_ARTIFACTS_HLQ | High-Level Qualifier of the datasets used during the Preview Build. These datasets need to hold a copy of the artifacts to be packaged as a baseline. | `DBEHM.MIG` | 
| SCAN_DATASET_MEMBERS | Flag to determine if application extraction process should scan each member to identify source type. | `false` |
| SCAN_DATASET_MEMBERS_ENCODING | PDS encoding for scanner when determining the source type | `IBM-1047` |
| DBB_ZAPPBUILD | Path to your customized [dbb-zAppBuild repository](https://github.com/IBM/dbb-zappbuild) on z/OS Unix System Services for baseline builds | `/var/dbb/dbb-zappbuild` |
| DBB_COMMUNITY_REPO | Path to your customized [DBB community repository](https://github.com/IBM/dbb) on z/OS Unix System Services | `/var/dbb/dbb` |
| APPLICATION_DEFAULT_BRANCH | Default branch name when initializing Git repositories and scanning files into DBB collections | `main` |
| INTERACTIVE_RUN | Flag to indicate if the Migration-Modeler-Start script should run interactively or not (`true` or `false`) | `false` |
| PUBLISH_ARTIFACTS | Flag to indicate if baseline packages should be uploaded to an Artifact Repository server or not (`true` or `false`) | `true` |
| ARTIFACT_REPOSITORY_SERVER_URL | URL of the Artifact Repository Server | |
| ARTIFACT_REPOSITORY_USER | User to connect to the Artifact Repository Server | `admin` |
| ARTIFACT_REPOSITORY_PASSWORD | Password to connect to the Artifact Repository Server | |
| PIPELINE_USER | User ID of the pipeline user on z/OS | `ADO` |
| PIPELINE_USER_GROUP | Group that the User ID of the pipeline user belongs to | `JENKINSG` |
| PIPELINE_CI | Pipeline technology used - either `AzureDevOps`, `GitlabCI`, `Jenkins` or `GitHubActions` | `AzureDevOps` |

# Configuring the Migration Modeler input files

To configure the DBB Git Migration Modeler, follow the instructions [on this page](./03-Configuration.md).