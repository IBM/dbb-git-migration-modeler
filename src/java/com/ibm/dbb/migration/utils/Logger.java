/********************************************************************************
 * Licensed Materials - Property of IBM                                          *
 * (c) Copyright IBM Corporation 2018, 2024. All Rights Reserved.                *
 *                                                                               *
 * Note to U.S. Government Users Restricted Rights:                              *
 * Use, duplication or disclosure restricted by GSA ADP Schedule                 *
 * Contract with IBM Corp.                                                       *
 ********************************************************************************/

package com.ibm.dbb.migration.utils;

import com.ibm.dbb.utils.FileUtils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Logger utility class for logging messages to console and file.
 * Corresponds to the logger.groovy utility script.
 */
public class Logger {
    private BufferedWriter logfileWriter;
    private String logFilePath;
    private String logEncoding;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * Create a log file writer with UTF-8 encoding
     * @param loggerFilePath Path to the log file
     */
    public void create(String loggerFilePath) throws IOException {
        create(loggerFilePath, "UTF-8");
    }
    
    /**
     * Create a log file writer with specified encoding
     * @param loggerFilePath Path to the log file
     * @param encoding Character encoding for the log file
     */
    public void create(String loggerFilePath, String encoding) throws IOException {
        this.logFilePath = loggerFilePath;
        this.logEncoding = encoding;
        logfileWriter = new BufferedWriter(new OutputStreamWriter(
            new FileOutputStream(loggerFilePath, true), Charset.forName(encoding)));
        FileUtils.setFileTag(loggerFilePath, encoding);
    }

    /**
     * Close the log file writer
     */
    public void close() {
        if (logfileWriter != null) {
            try {
                logfileWriter.close();
                // Ensure file tag is set after closing
                if (logFilePath != null && logEncoding != null) {
                    if (!FileUtils.setFileTag(logFilePath, logEncoding)) {
                        System.out.println("*! Error while tagging file '" + logFilePath +
                            "' with '" + logEncoding + "' encoding, must be done manually.");
                    }
                }
            } catch (IOException e) {
                System.err.println("Error closing log file: " + e.getMessage());
            }
        }
    }

    /**
     * Log a message to console and file (if configured)
     * @param message The message to log
     */
    public void logMessage(String message) {
        if (logfileWriter != null) {
            try {
                logfileWriter.write(dateFormat.format(new Date()) + " " + message);
                logfileWriter.newLine();
                logfileWriter.flush();
            } catch (IOException e) {
                System.err.println("Error writing to log file: " + e.getMessage());
            }
        }
        System.out.println(message);
    }

    /**
     * Log a silent message (only to file, not to console)
     * Useful for logging command details without cluttering console output
     * @param message The message to log
     */
    public void logSilentMessage(String message) {
        if (logfileWriter != null) {
            try {
                logfileWriter.write(message);
                logfileWriter.newLine();
                logfileWriter.flush();
            } catch (IOException e) {
                System.err.println("Error writing to log file: " + e.getMessage());
            }
        }
    }
}