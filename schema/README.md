# Schemas for enabling syntax validation and completion in IDEs

This folder contains the jsonschema files to be used by the DBB Git Migration Modeler users when performing syntax validation and completion with their preferred YAML editor.

## Index

| File | Purpose |
| --   |  --     | 
| [application-descriptor-schema.json](application-descriptor-schema.json) | Schema for Application Descriptor files |
| [application-mapping-schema.json](application-mapping-schema.json) | Schema for Application Mapping file |
| [repository-paths-mapping-schema.json](repository-paths-mapping-schema.json) | Schema for Repository Paths mapping file |

## Sample configuration

Please follow the instructions of your preferred YAML editor. The below bullets 

## VS Code YAML extension

To code YAML in VS Code, most users take advantage of the [YAML](https://marketplace.visualstudio.com/items?itemName=redhat.vscode-yaml) extension by Red Hat. 

You can associate the schema using a modeline (a comment). All sample files come with this modeline.

```yaml
# yaml-language-server: $schema=<urlToTheSchema>
```

## YAML Editor in Eclipse

This is based on the [Eclipse Wild Web developer](https://projects.eclipse.org/projects/tools.wildwebdeveloper) project. 

Please configure the preferences the *File associations* and add the corresponding Migration Modeler files under the `YAML` content type. Then add the jsonschema references under *General Y Schema associations*, feel free to point to this repository (or your forked version).

For your reference, the DBB zBuilder tutorial leverages similar capabilities of the Wild Web developer plugin that comes with IDz. 

