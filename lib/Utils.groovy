import java.nio.file.Paths
import java.text.SimpleDateFormat
class Utils {
    static final BWA_INDEX_EXTENSIONS = [
        '.fa',   
        '.amb',
        '.ann',
        '.bwt',
        '.pac',
        '.sa'
    ].toSet()

    public static String usable_mem(){
        def free_call = "free -ghL".execute().text
        def mem_map = [:]
        def matcher = free_call =~ /(\S+)\s+(\S+)/
        matcher.each { match ->
            def key = match[1]
            def value = match[2]
            mem_map[key] = value
        }
        def usable_mem = mem_map['CachUse'].replaceAll(/Gi/,'.GB')
        assert usable_mem.endsWith('.GB') : "Incorrect format detected: ${usable_mem}"
        return usable_mem
    }

    public static Integer usable_cores(){
        return "nproc".execute().text.toInteger() - 1
    }

    public static String getCommonPrefix(filenames){
        if (filenames.isEmpty()) return ""
        def prefix = filenames[0].getFileName().toString()
        filenames[1..-1].each { file -> 
            file = file.getFileName().toString()
            def i = 0
            while (i < Math.min(prefix.length(), file.length()) && prefix[i] == file[i]) {
                i++
            }
        prefix = prefix[0..<i]
        }
        return prefix[0..-2]
    }

    public static String getTimestamp(){
        def timestamp = new SimpleDateFormat("yyyy-MM-dd")
        return timestamp.format(new Date())
    }

    public static String toAbsPath(stringPath){ 
        def path = Paths.get(stringPath)
        return path.toAbsolutePath().toString()
    }
    
    // Define the set of required extensions outside the function (constant)
    public static void validateReferenceFiles(String ref_dir_path){
        def dir = new File(ref_dir_path)
        def all_files_in_dir = dir.listFiles() ?: []
        def found_extensions = all_files_in_dir.collect { file ->
            def name = file.name
            def ext = name.substring(name.lastIndexOf('.'))
            return ext
        }.toSet()
        def missing_extensions = BWA_INDEX_EXTENSIONS - found_extensions
        if (missing_extensions) {
            throw new IllegalArgumentException("Error: Missing required reference files with extensions: ${missing_extensions.join(', ')} in directory: ${ref_dir_path}")
        }
    }
}
