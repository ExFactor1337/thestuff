import Auditor
import Utils
import java.nio.file.Files
import groovy.util.ConfigSlurper

class Logger { 
    static boolean enabled = true 
    static void log(msg) { 
        if (enabled) println msg 
    } 
}

class ParamsChecker {
    private final Map definitions
    
    //Constructs a ParamsChecker with the parameter definitions.
    //@param definitions A map defining the expected parameters and their constraints.
    ParamsChecker(String configFilePath) {
        Logger.log("Loading parameter definitions from config file: ${configFilePath}")
        def configFile = new File(configFilePath)
        // Make sure the config file exists
        if (!configFile.exists()) {
            throw new IllegalArgumentException("Configuration file not found at path: ${configFilePath}")
        }
        def config = new ConfigSlurper().parse(configFile.toURL())
        if (!config.containsKey('params') || !config.params.containsKey('definitions')) {
            throw new IllegalArgumentException("Configuration file is missing 'params.definitions'. Please check ${configFile.path}.")
        }
        this.definitions = config.params.definitions
        validateDefinitionStructure()
    }

    /* This function will enforce the following rules:
        1. The Definitions object must be a Map, whose keys are Parameters and whose values are a Definition Set.
        2. A valid Definition Set has the following criteria
            a. A Definition Set is itself a Map.
            b. Each Definition Set must include 'type' and 'required' keys.
            c. If 'required' is false, a 'default_value' must be provided.
            d. The 'type' must be one of the recognized types: 'string', 'integer', 'float', 'path', 'flag'.
        3. Descriptions are used in the help message - if one is not provided, a filler description will be used.
    */
    private void validateDefinitionStructure() { 
        // 1. Ensure definitions is a Map
        if (!(definitions instanceof Map)) { 
            throw new IllegalArgumentException("Parameter definitions must be a Map.") 
        }else{
            Logger.log("Definitions object is a Map.")
        } 

        // 2. Validate each parameter definition
        definitions.each { paramName, paramDef -> 
            // Required keys 
            List<String> requiredKeys = ["type", "required"] 
            // Allowed types
            List<String> allowedTypes = ["string", "integer", "float", "path", "flag"]

            Logger.log("Validating structure for parameter: ${paramName}")
            // 2a. Ensure each entry is itself a Map 
            if (!(paramDef instanceof Map)) { 
                throw new IllegalArgumentException( "Definition for parameter '${paramName}' must be a Map, but got: ${paramDef?.getClass()?.name}" ) 
            }else{
                Logger.log("Definition for parameter [${paramName}] is a Map.")
            }
            // 2b. Check that'required' and 'type' keys are present in the Definition Set
            requiredKeys.each { key -> 
                if (!paramDef.containsKey(key)) { 
                    throw new IllegalArgumentException( "Parameter '${paramName}' is missing required key '${key}'." ) 
                }
            } 
            Logger.log("Definition for parameter [${paramName}] contains all required DefinitionSet keys.")
            // 2c. Check that 'type' values are in the allowed set
            if (!allowedTypes.contains(paramDef.type)) {
                throw new IllegalArgumentException( "Parameter '${paramName}' has unrecognized type '${paramDef.type}'. Allowed types are: ${allowedTypes.join(', ')}." )
            } else{
                Logger.log("Definition for parameter [${paramName}] has valid type '${paramDef.type}'.")
            }   
            // 2d. If a Definition Set is marked as NOT required, ensure a default_value is provided
            if(!paramDef.required && !paramDef.containsKey('default_value')) { 
                throw new IllegalArgumentException( "Parameter '${paramName}' is marked as NOT required, so a 'default_value' must be specified. If a required parameter is user-submitted, its default_value must be 'null'" ) 
            } 
            // 3. Ensure a description is provided; if not, add a filler description
            if (!paramDef.containsKey('description')) { 
                Logger.log("No description found for parameter [${paramName}]. Adding filler description.")
                paramDef.description = "No description provided for parameter '${paramName}'."  
            }
        } 
    } 

    //Validates and processes user-supplied parameters against the stored definitions.
    //userParams is a map of parameters supplied by the user (e.g., from CLI).
    //Returns a map containing only the validated and converted parameters.
    Map validate(Map userParams) {
        def validatedParams = [:] // Output map to hold validated parameters
        def workingDefinitions = [:] // the copy of definitions whose default_value may be overridden

        // 1. Create a copy of definitions and override any default_value their 
        // respective with user-provided params, if they exist.
        this.definitions.each { paramName, definitionSet ->
            workingDefinitions[paramName] = new HashMap(definitionSet)
        }
        // Specifically deal with flags here: CLI flags in the User Submitted Params do no have values, their presence means 'true'  
        // We assume that flag params will always be optionally required, defaulting to false in the definitionSet.
        userParams.each { paramName, value ->
            if (workingDefinitions.containsKey(paramName) && workingDefinitions[paramName].type == 'flag') {
                    workingDefinitions[paramName].default_value = true
            } else if (workingDefinitions.containsKey(paramName)) {
                workingDefinitions[paramName].default_value = value
            } else {
                throw new RuntimeException("Unrecognized parameter '${paramName}' provided by user.")
            }
        }

        // 2. Validate and build the output map
        workingDefinitions.each { paramName, definitionSet ->
            def value = definitionSet.default_value
            def type = definitionSet.type
            
            // Required Check
            if (definitionSet.containsKey('required') && (value == null || (value instanceof String && value.trim().isEmpty()))) {
                throw new RuntimeException("Parameter '${paramName}' is required but was not provided.")
            }
            // Skip null values - they can be in the definitionSet, but if not overridden by user, we do not include them in the output map.
            if (value == null) {
                return
            }

            // Trim string values
            if (value instanceof String) {
                value = value.trim()
            }
            /*
            // There are 2 reasons why a user would input a string
            1. They want the string to be passed as-is into the pipeline (e.g., a sample name)
            2. They want the string to represent a specific selection from a set of allowed patterns
            In the former case, we do not need to monitor the user's input. 
            In the latter case, we need to ensure the user's input matches one of the allowed patterns. 

            The 'allow' key contains a list of allowed patterns. 
            If the user's input does not match any of these patterns, we throw an error.
            */
            // Numerical Type Conversion and Range Checks
            if (type == 'integer') {
                try {
                    value = value.toString() as int
                } catch (Exception e) {
                    throw new RuntimeException("Parameter '${paramName}' expected an integer but got '${value}'.")
                }
                if (definitionSet.containsKey('min') && value < definitionSet.min) {
                    throw new RuntimeException("Value for parameter '${paramName}' must be >= ${definitionSet.min}, but was ${value}.")
                }
                if (definitionSet.containsKey('max') && value > definitionSet.max) {
                    throw new RuntimeException("Value for parameter '${paramName}' must be <= ${definitionSet.max}, but was ${value}.")
                }
            } else if (type == 'float') {
                try {
                    value = value.toString() as float
                } catch (Exception e) {
                    throw new RuntimeException("Parameter '${paramName}' expected a float but got '${value}'.")
                }
                if (definitionSet.containsKey('min') && value < definitionSet.min) {
                    throw new RuntimeException("Value for parameter '${paramName}' must be >= ${definitionSet.min}, but was ${value}.")
                }
                if (definitionSet.containsKey('max') && value > definitionSet.max) {
                    throw new RuntimeException("Value for parameter '${paramName}' must be <= ${definitionSet.max}, but was ${value}.")
                }
            } else if (type == 'path') {
                def file = new File(value)
                if (!file.exists()) {
                    throw new RuntimeException("Path for parameter '${paramName}' does not exist: '${value}'.")
                }
                value = file.getCanonicalPath()
            } else if (type == 'flag') {
                // In the UserParams stage, all flags are set to true if present, false if absent, as set in the definitionSet.
                if (!(value instanceof Boolean)) {
                    throw new RuntimeException("Parameter '${paramName}' is a flag and must be a boolean value (true/false). Got '${value}'.")
                }
            } else if (type == 'string') {
                if (!(value instanceof String)) {
                    value = value.toString().trim()
                    // String Validation for 'string' type with 'allow' patterns
                    if (definitionSet.containsKey('allow')) {
                        if (!(definitionSet.allow instanceof List)) {
                            throw new RuntimeException("Error in parameter definition for '${paramName}': 'allow' must be a list of allowed values. e.g. ['value1', 'value2']")
                        }
                        def matched = definitionSet.allow.any { pattern ->
                            value == pattern
                        }
                        if (!matched) {
                            throw new RuntimeException("Error for value of parameter '${paramName}'. '${strValue}' does not match allowed patterns: '${definitionSet.allow}'.")
                        }
                    }
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
        if (asciiArtFilePath) {
            def f = new File(asciiArtFilePath)
            if (f.exists()) {
                println()
                println f.text
                println()
            } else {
                println "\nWarning: ASCII art file not found at ${asciiArtFilePath}\n"
            }
        }
        def output = new StringBuilder()
        output.append("\n${"=" * 120}\n")
        output.append("\nREQUIRED and OPTIONAL PARAMETERS\n")
        output.append("\n${"=" * 120}\n")

        def formatHeader = "%-20s %-10s %-10s %-15s %s\n"
        def formatLine   = "%-20s %-10s %-10s %-15s %s\n"

        output.append(String.format(formatHeader, "Parameter", "Type", "Required", "Default", "Description & Constraints"))
        output.append(String.format(formatHeader, "---------", "----", "--------", "-------", "-----------------------------"))

        definitions.each { paramName, definitionSet ->
            def requiredStatus = definitionSet.required ? "YES" : "NO"
            def defaultValue
        
            // 1. Check if the parameter is a 'flag'
            if (definitionSet.type == 'flag') {
                // A flag's default is typically 'false' if not specified, or 'true'/'false' if specified.
                // Using getOrDefault to safely check for 'default_value', defaulting to null if missing.
                def definedDefault = definitionSet.getOrDefault('default_value', null) 
                
                // If a default is explicitly provided, use it. Otherwise, assume 'false'.
                defaultValue = definedDefault == null ? 'false' : definedDefault.toString()
            } else {
                // 2. For all other types, check for 'default_value'.
                // Use the Elvis operator (?:) combined with toString() to handle null or missing keys.
                // This line covers cases where 'default_value' is null or not present in the map.
                // A missing key in Groovy map often returns null, but in GPath/GString context it can be subtle.
                // Using the toString() on the result (which might be null) is often where the `[:]` comes from.
                
                // We'll safely get the value, defaulting to null if not present.
                def rawDefault = definitionSet.getOrDefault('default_value', null)
                
                // If rawDefault is null, set the display value to "N/A".
                // Otherwise, convert it to a string.
                defaultValue = rawDefault == null ? "NULL" : rawDefault.toString()
            }

            def constraints = ""
            if (definitionSet.type in ['integer', 'float']) {
                if (definitionSet.min != null || definitionSet.max != null) {
                    def min = definitionSet.min != null ? definitionSet.min : "-inf"
                    def max = definitionSet.max != null ? definitionSet.max : "+inf"
                    constraints += " [Range: $min to $max]"
                }
            }
            if (definitionSet.type == 'string' && definitionSet.allow) {
                constraints += " [Allowed: ${definitionSet.allow.join(', ')}]"
            }
            
            def fullDescription = "${definitionSet.description}${constraints}"
            
            output.append(String.format(
                formatLine,
                paramName,
                definitionSet.type,
                requiredStatus,
                defaultValue,
                fullDescription
            ))
        }

        output.append("\n${"=" * 120}\n")
        
        println output.toString()
    }
}