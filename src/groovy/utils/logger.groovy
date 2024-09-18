@groovy.transform.BaseScript com.ibm.dbb.groovy.ScriptLoader baseScript
import groovy.transform.*
import groovy.util.*


@Field def logfileWriter = null

def create(String loggerFilePath){
	def logFile = new File(loggerFilePath)
	logfileWriter = logFile.newWriter('IBM-1047', true)
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