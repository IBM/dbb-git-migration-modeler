@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import groovy.transform.*
import groovy.util.*
import com.ibm.dbb.utils.FileUtils


@Field def logfileWriter = null

def create(String loggerFilePath){
	def logFile = new File(loggerFilePath)
	logfileWriter = logFile.newWriter("UTF-8", true)
	FileUtils.setFileTag(loggerFilePath, "UTF-8")
}

def close() {
	logfileWriter?.close()
}

def logMessage(String message){
	if (logfileWriter != null) {
		logfileWriter.writeLine(new Date().format("yyyy-MM-dd HH:mm:ss.SSS") + " " + message)
	}
	println(message)	
}

def logSilentMessage(String message){
	if (logfileWriter != null) {
		logfileWriter.writeLine(new Date().format("yyyy-MM-dd HH:mm:ss.SSS") + " - Additional message - " + message)
	}	
}