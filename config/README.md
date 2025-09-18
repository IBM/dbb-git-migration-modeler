## DBB Git Migration Modeler - Config folder

This folder is a placeholder for specific configuration files:
* The db2Connection.conf file can be placed here, to configure the JDBC connection to the db2 database used by DBB for the MetadataStore
* The db2Password.txt file can be placed in this folder as well. This file can contain encrypted passwords for the JDBC connection for a given user. The file must be generated with the `pwf.sh` utility provided by DBB.
These files can also be placed elsewhere, and their paths can be specified during the execution of the Setup script.

At the end of the Setup script execution, the location of the generated DBB Git Migration Modeler Configuration file will be prompted to the user.
The default location is the `config` subfolder, but this value can be changed as well, to fit your requirements.