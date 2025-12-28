import Auditor
import java.nio.file.Files

class ParamsChecker {
    private final Map definitions
    
    //Constructs a ParamsChecker with the parameter definitions.
    //@param definitions A map defining the expected parameters and their constraints.
    ParamsChecker(Map definitions) {
        this.definitions = definitions
    }

    //Validates and processes user-supplied parameters against the stored definitions.
    //@param userParams A map of parameters supplied by the user (e.g., from CLI).
    //@return A map containing only the validated and converted parameters.
    //@throws RuntimeException if any validation or type check fails.
    Map validate(Map userParams) {
        def validatedParams = [:]
        def workingDefinitions = [:]

        // 1. Create a deep copy of definitions and initialize/override with user-provided params.
        this.definitions.each { paramName, defs ->
            workingDefinitions[paramName] = new HashMap(defs)
            
            // If 'flag' type and no default_value is set, 
            // implicitly set it to false.
            if (defs.type == 'flag' && !defs.containsKey('default_value')) {
                workingDefinitions[paramName].default_value = false
            }
        }

        userParams.each { paramName, value ->
            if (workingDefinitions.containsKey(paramName)) {
                if (workingDefinitions[paramName].type == 'flag') {
                    workingDefinitions[paramName].default_value = true
                } else {
                    workingDefinitions[paramName].default_value = value
                }
            }
        }

        // 2. Validate and build the output map
        workingDefinitions.each { paramName, defs ->
            def value = defs.default_value
            def type = defs.type
            
            // Required Check
            if (defs.required && (value == null || (value instanceof String && value.trim().isEmpty()))) {
                throw new RuntimeException("Parameter '${paramName}' is required but was not provided.")
            }
            
            if (value == null) {
                return
            }

            // Trim string values
            if (value instanceof String) {
                value = value.trim()
            }

            // String Validation for 'string' type with 'allow' patterns
            if (defs.allow) {
                def valueAsString = value.toString()
                def matched = defs.allow.any { pattern ->
                    pattern == '*' || (valueAsString =~ pattern)
                }
                if (!matched) {
                    throw new RuntimeException("Value for parameter '${paramName}' does not match allowed patterns: '${valueAsString}'.")
                }
            }

            // Type Conversion and Range Checks
            if (type == 'integer') {
                try {
                    value = value.toString() as int
                } catch (Exception e) {
                    throw new RuntimeException("Parameter '${paramName}' expected an integer but got '${value}'.")
                }
                if (defs.min != null && value < defs.min) {
                    throw new RuntimeException("Value for parameter '${paramName}' must be >= ${defs.min}, but was ${value}.")
                }
                if (defs.max != null && value > defs.max) {
                    throw new RuntimeException("Value for parameter '${paramName}' must be <= ${defs.max}, but was ${value}.")
                }
            } else if (type == 'float') {
                try {
                    value = value.toString() as float
                } catch (Exception e) {
                    throw new RuntimeException("Parameter '${paramName}' expected a float but got '${value}'.")
                }
                if (defs.min != null && value < defs.min) {
                    throw new RuntimeException("Value for parameter '${paramName}' must be >= ${defs.min}, but was ${value}.")
                }
                if (defs.max != null && value > defs.max) {
                    throw new RuntimeException("Value for parameter '${paramName}' must be <= ${defs.max}, but was ${value}.")
                }
            } else if (type == 'path') {
                def file = new File(value)
                if (!file.exists()) {
                    throw new RuntimeException("Path for parameter '${paramName}' does not exist: '${value}'.")
                }
                value = file.getCanonicalPath()
            } else if (type == 'flag') {
                if (!(value instanceof Boolean)) {
                    def lowerValue = value.toString().toLowerCase()
                    value = (lowerValue in ['true', 't', 'yes', 'y', '1'])
                }
            } else if (type == 'string') {
                if (!(value instanceof String)) {
                    value = value.toString()
                }
            } else {
                // Unknown type
                // Print the help message for context.
                this.printHelp()                
                throw new RuntimeException("\nFATAL CONFIGURATION ERROR: Unrecognized type '${type}' found for parameter '${paramName}'. Please correct your parameter definitions.")
            }
            
            validatedParams[paramName] = value
        }

        return validatedParams
    }
    
    void printHelp(String asciiArtFilePath = null) {
        Auditor.printAsciiBanner(asciiArtFilePath)
        def output = new StringBuilder()
        output.append("\n======================================================\n")
        output.append("REQUIRED and OPTIONAL PARAMETERS\n")
        output.append("======================================================\n")

        def formatHeader = "%-20s %-10s %-10s %-15s %s\n"
        def formatLine   = "%-20s %-10s %-10s %-15s %s\n"

        output.append(String.format(formatHeader, "Parameter", "Type", "Required", "Default", "Description & Constraints"))
        output.append(String.format(formatHeader, "---------", "----", "--------", "-------", "-----------------------------"))

        definitions.each { paramName, defs ->
            def requiredStatus = defs.required ? "YES" : "NO"
            def defaultValue
        
            // 1. Check if the parameter is a 'flag'
            if (defs.type == 'flag') {
                // A flag's default is typically 'false' if not specified, or 'true'/'false' if specified.
                // Using getOrDefault to safely check for 'default_value', defaulting to null if missing.
                def definedDefault = defs.getOrDefault('default_value', null) 
                
                // If a default is explicitly provided, use it. Otherwise, assume 'false'.
                defaultValue = definedDefault == null ? 'false' : definedDefault.toString()
            } else {
                // 2. For all other types, check for 'default_value'.
                // Use the Elvis operator (?:) combined with toString() to handle null or missing keys.
                // This line covers cases where 'default_value' is null or not present in the map.
                // A missing key in Groovy map often returns null, but in GPath/GString context it can be subtle.
                // Using the toString() on the result (which might be null) is often where the `[:]` comes from.
                
                // We'll safely get the value, defaulting to null if not present.
                def rawDefault = defs.getOrDefault('default_value', null)
                
                // If rawDefault is null, set the display value to "N/A".
                // Otherwise, convert it to a string.
                defaultValue = rawDefault == null ? "N/A" : rawDefault.toString()
            }

            def constraints = ""
            if (defs.type in ['integer', 'float']) {
                if (defs.min != null || defs.max != null) {
                    def min = defs.min != null ? defs.min : "-inf"
                    def max = defs.max != null ? defs.max : "+inf"
                    constraints += " [Range: $min to $max]"
                }
            }
            if (defs.type == 'string' && defs.allow) {
                constraints += " [Allowed: ${defs.allow.join(', ')}]"
            }
            
            def fullDescription = "${defs.description}${constraints}"
            
            output.append(String.format(
                formatLine,
                paramName,
                defs.type,
                requiredStatus,
                defaultValue,
                fullDescription
            ))
        }

        output.append("\n======================================================\n")
        
        println output.toString()
    }
}