## DBB Git Migration Modeler - Config folder

This folder is a placeholder for specific configuration files:
* The db2Connection.conf file can be placed here, to configure the JDBC connection to the Db2 database used by DBB for the MetadataStore
* The db2Password.txt file can be placed in this folder as well. This file can contain encrypted passwords for the JDBC connection for a given user. The file must be generated with the `pwf.sh` utility provided by DBB.

These files can also be placed elsewhere and are a pre-requisites to running the [Setup](../Setup.sh) script if the db2 MetadataStore is used. Their paths can be specified during the execution of the [Setup](../Setup.sh) script.

* At the end of the [Setup](../Setup.sh) script execution, the location of the generated DBB Git Migration Modeler Configuration file will be prompted to the user.
  The default location is the `config` subfolder, but this value can be changed as well, to fit your requirements.