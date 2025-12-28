import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

// Define the Auditor class in the script header so it's available everywhere
class Auditor {
    def logFile
    
    // --- Utility Methods ---

    //Find all regular files in a directory recursively and returns their relative paths.
    private List<String> listOutputFiles(String out_dir) {
        def outDirPath = Paths.get(out_dir)
        if (!Files.exists(outDirPath)) {
            return ["Directory not found: ${out_dir}"]
        }
        
        def outputFiles = []
        Files.walk(outDirPath)
            .filter { Files.isRegularFile(it) }
            .forEach { outputFiles.add(outDirPath.relativize(it).toString()) }
            
        return outputFiles.sort()
    }
    
    //Logging/Printing Methods
    private void writeAndPrint(String content) {
        Files.write(this.logFile, content.bytes, StandardOpenOption.APPEND)
        println content
    }
    
    private void printAsciiBanner(String asciiArtFilePath) {
        if (Files.exists(Paths.get(asciiArtFilePath))) {
            def asciiArt = Files.readAllBytes(Paths.get(asciiArtFilePath))
            this.writeAndPrint("\n" + new String(asciiArt) + "\n")
        } else {
            this.writeAndPrint("\nWarning: ASCII art file not found at ${asciiArtFilePath}\n")
        }
    }

    private void printBasicInfo(def workflow, Map vparams) {
        def summary = [:]
        def startTimeMillis = workflow.start.toInstant().toEpochMilli()

        summary['Pipeline Name']    = workflow.manifest.name ?: 'Polly Pipeline'
        summary['Version']          = workflow.manifest.version ?: 'N/A'
        summary['Config Files']     = workflow.configFiles.join(', ')
        summary['User']             = workflow.userName ?: 'unknown'
        summary['Start Time']       = new Date(startTimeMillis).format('yyyy-MM-dd HH:mm:ss z') // Corrected usage
        summary['Nextflow Version'] = workflow.nextflow.version
        summary['Launch Dir']       = workflow.launchDir
        summary['Project Dir']      = workflow.projectDir
        summary['Working Dir']      = workflow.workDir
        summary['Container']        = workflow.containerEngine ? "${workflow.containerEngine} - ENABLED" : "DISABLED"
        summary['Output Dir']       = vparams.out_dir ?: 'N/A'

        def maxKeyLength = summary.keySet().collect{ it.length() }.max() ?: 18
        
        def info = summary.collect { k, v ->
            "${k.padRight(maxKeyLength)}: $v" 
        }.join("\n")

        def header = "\n====================== PIPELINE SUMMARY ======================\n"
        def footer = "\n============================================================\n"
        
        this.writeAndPrint(header + info + footer)
    }

    private void printVparams(Map vparams) {
        def content = "\n======================= PARAMETERS =======================\n"
        content += vparams.collect { key, value -> "  ${key}: ${value}" }.join("\n") + "\n"
        content += "============================================================\n"
        this.writeAndPrint(content)
    }

    private void printOutputFiles(String out_dir) {
        def outputFiles = this.listOutputFiles(out_dir)
        def content = "\n======================= OUTPUT FILES =======================\n"
        content += "Total files found in ${out_dir}: ${outputFiles.size()}\n"
        content += outputFiles.collect { f -> "  - ${f}" }.join("\n")
        content += "\n============================================================\n"
        this.writeAndPrint(content)
    }
    
    private void printEndStatus(def workflow, String out_dir) {
        def end_time = new Date().format('yyyy-MM-dd HH:mm:ss z')
        def status = workflow.success ? "SUCCESS" : "FAILURE"
        
        def finalContent = """
========================= RUN FINISHED =========================
Run Status: ${status}
Exit Code: ${workflow.exitStatus}
End Time: ${end_time}
Total Duration: ${workflow.duration}
============================================================
"""
        this.writeAndPrint(finalContent)
        println "Pipeline finished. Status: ${status}. Audit log written to: ${this.logFile}"
    }

    // THE CORE METHOD CALLED IN onComplete
    public void generateAuditLog(def workflow, Map vparams, String asciiArtFilePath) {
        def pipelineName = workflow.manifest.name ?: 'Enrico Pipeline'
        def outDir = vparams.containsKey('out_dir') ? vparams.out_dir.toString() : "logs"
        def outDirPath = Paths.get(outDir)

        // 1. Setup Log File Path (using a fresh timestamp for the file name)
        if (!Files.exists(outDirPath)) {
            Files.createDirectories(outDirPath)
        }
        def timestamp = new Date().format("yyyyMMdd_HHmmss")
        def fileName = "${pipelineName.replace(' ', '_')}_${timestamp}.log"
        this.logFile = Paths.get(outDirPath.toString(), fileName)

        // 2. Write initial header and run log steps
        Files.write(this.logFile, "Starting Pipeline Audit Log...\n".bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        
        this.printAsciiBanner(asciiArtFilePath)
        this.printBasicInfo(workflow, vparams)
        this.printVparams(vparams)
        this.printOutputFiles(outDir)
        this.printEndStatus(workflow, outDir)
    }
}