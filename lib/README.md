# Local Library Directory

This directory is configured to hold z/OS-specific libraries for local compilation on Windows, Linux or MacOS.

## Directory Structure

```
lib/
├── jzos/          # IBM JZOS libraries
│   └── *.jar      # Place JZOS JAR files here
├── dbb/           # IBM DBB libraries
│   └── *.jar      # Place DBB JAR files here
└── README.md      # This file
```

## Setup Instructions

### Option 1: Copy from z/OS System

If you have access to a z/OS system, copy the required JAR files:

```bash
# On z/OS, locate the libraries
ls $JZOS_HOME/*.jar
ls $DBB_HOME/lib/*.jar

# Copy to your local machine (example using scp)
scp user@zos-host:$JZOS_HOME/*.jar ./lib/jzos/
scp user@zos-host:$DBB_HOME/lib/*.jar ./lib/dbb/
```

### Option 2: Request from System Administrator

Contact your z/OS system administrator to obtain copies of:

**JZOS Libraries (typically from `/usr/lpp/IBM/izoda/v1r1/IBM/jzos/`):**
- `ibmjzos.jar`
- Any other JZOS-related JAR files

**DBB Libraries (typically from `/var/dbb/lib/` or `$DBB_HOME/lib/`):**
- `dbb.jar`
- `dbb-core.jar`
- `dmh-scanner.jar` (or similar DMH Scanner JAR)
- Any other DBB-related JAR files

### Option 3: Use Stub Libraries (Compilation Only)

If you cannot obtain the real libraries, you can create stub libraries for compilation:

```powershell
# Run the stub creation script
.\create-stubs.bat

# This creates:
# - jzos-stubs.jar
# - dmh-stubs.jar
```

**Note:** Stub libraries allow compilation but the application cannot run. You must deploy to z/OS for execution.

## Required Libraries

### JZOS (IBM z/OS Java SDK)

**Purpose:** Provides Java APIs for z/OS-specific operations like dataset access

**Key Classes:**
- `com.ibm.jzos.ZFile` - z/OS file operations
- `com.ibm.jzos.PdsDirectory` - PDS directory listing
- `com.ibm.jzos.ZFileConstants` - File operation constants

**Typical Files:**
- `ibmjzos.jar`

### DBB (IBM Dependency Based Build)

**Purpose:** Provides build framework and dependency scanning capabilities

**Key Classes:**
- `com.ibm.dbb.dependency.DependencyScanner` - Dependency scanning
- `com.ibm.dbb.repository.RepositoryClient` - Metadata repository access
- `com.ibm.dbb.repository.LogicalFile` - Logical file representation
- `com.ibm.dmh.scan.classify.Dmh5210` - DMH file scanner
- `com.ibm.dmh.scan.classify.ScanProperties` - Scanner configuration
- `com.ibm.dmh.scan.classify.SingleFilesMetadata` - Scan results

**Typical Files:**
- `dbb.jar`
- `dbb-core.jar`
- `dmh-scanner.jar`

## Build Configuration

The [`build.gradle`](../build.gradle:34) file is configured to look for libraries in this order:

1. **Local lib directory** (this directory)
   - `lib/jzos/*.jar`
   - `lib/dbb/*.jar`

2. **Environment variables**
   - `$JZOS_HOME/*.jar`
   - `$DBB_HOME/lib/*.jar`

3. **Default z/OS paths**
   - `/usr/lpp/IBM/izoda/v1r1/IBM/jzos/*.jar`
   - `/var/dbb/lib/*.jar`

This means you can place the JAR files in this `lib` directory and they will be used automatically during compilation.

## Verification

After placing the JAR files, verify the setup:

```powershell
# List JZOS libraries
dir lib\jzos\*.jar

# List DBB libraries
dir lib\dbb\*.jar

# Test compilation (using Gradle Wrapper)
.\gradlew.bat clean build --no-daemon
```

## Important Notes

1. **Licensing:** Ensure you have proper licensing for IBM JZOS and DBB libraries
2. **Version Compatibility:** Use library versions compatible with your z/OS environment
3. **Compile-Only:** These libraries are marked as `compileOnly` in Gradle, meaning they won't be packaged in the final JAR
4. **Runtime:** The application requires these libraries at runtime on z/OS

## Troubleshooting

### Build fails with "package com.ibm.jzos does not exist"

**Cause:** JZOS libraries not found

**Solution:**
1. Verify JAR files exist in `lib/jzos/`
2. Check JAR files contain the required classes: `jar tf lib/jzos/ibmjzos.jar | grep ZFile`
3. Try cleaning and rebuilding: `.\gradlew.bat clean build --no-daemon`

### Build fails with "package com.ibm.dbb does not exist"

**Cause:** DBB libraries not found

**Solution:**
1. Verify JAR files exist in `lib/dbb/`
2. Check JAR files contain the required classes: `jar tf lib/dbb/dbb.jar | grep DependencyScanner`
3. Try cleaning and rebuilding: `.\gradlew.bat clean build --no-daemon`

### Cannot obtain real libraries

**Solution:** Use stub libraries for compilation:
```powershell
.\create-stubs.bat
```

Then update [`build.gradle`](../build.gradle:34) to use stubs as fallback (see [`BUILD-ON-WINDOWS.md`](../BUILD-ON-WINDOWS.md) for details).

## .gitignore Configuration

The JAR files in this directory are ignored by Git (see [`.gitignore`](../.gitignore:45)):

```gitignore
*.jar
!gradle/wrapper/*.jar
!*-stubs.jar
```

This prevents accidentally committing proprietary IBM libraries to version control.

## Summary

To build on Windows:

1. **Obtain libraries** from z/OS or system administrator
2. **Place in lib directory**:
   - JZOS JARs → `lib/jzos/`
   - DBB JARs → `lib/dbb/`
3. **Build**: `.\gradlew.bat clean build --no-daemon`
4. **Deploy** JAR to z/OS for execution

For more information, see:
- [`BUILD-ON-WINDOWS.md`](../BUILD-ON-WINDOWS.md) - Complete build guide
- [`WINDOWS-BUILD-SOLUTION.md`](../WINDOWS-BUILD-SOLUTION.md) - Technical details
- [`build.gradle`](../build.gradle) - Build configuration