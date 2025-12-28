# The Stuff: Nextflow Pipeline Template Repository 

A robust, efficient, and professional template designed to power all your **Nextflow-orchestrated pipelines**. "The Stuff" provides the foundational components—from smart configuration parsing to automatic auditing and seamless containerization—to ensure your pipelines run smoothly every time.

---

## 🚀 Key Features

This template is packed with utilities and configurations to streamline your pipeline development:

| Feature | Description | Benefit |
| :--- | :--- | :--- |
| **Groovy Config Runner** | Uses **Nextflow Domain Specific Language (DSL)** to parse configuration files. It **validates inputs**, ensuring they exist and all parameters fall within expected ranges and formats. | **Guaranteed execution** reliability and immediate feedback on configuration errors. |
| **🕵️ Auditor Log** | **Automatic log creation** for every pipeline run. Logs include: input parameters, executed command, pipeline version, user, timestamp, and a complete list of generated output files. | **Full transparency** and reproducibility for every single pipeline execution. |
| **🐳 GitHub Actions Matrix Docker Build** | The `docker-build.yaml` automatically builds and pushes images for all Dockerfiles in the `containers/` directory. Uses a **matrix build** to handle multiple images. | **Zero-effort container management** with automatic SHA-unique IDs and `latest` tags for every image. |
| **📦 Nextflow Runner Dockerfile** | A dedicated Dockerfile with all instructions and dependencies required to build the core Nextflow runner image, enabling **containerized execution** of all pipelines. | **Consistent, reproducible environment** for running pipelines across different systems. |
| **🛠️ Groovy Helper Library** | A growing library of reusable Groovy scripts designed for easy and rapid construction of complex Nextflow pipelines. | **Accelerated development** and increased code quality/consistency. |
| **🎨 Banner Art Utilities** | Simple utilities to add **ASCII flair** and visually appealing banners to your project output or logs. | **Professional presentation** and engaging user experience. |

---

Auditor Log Utility
- Logs the 'who/what/when/where/how' of each pipeline run in a timestamped auditor logfile. 
    - Logs username, time of execution, important directories, configurations, and user inputs
    - Logs files created in out_dir location by pipeline
    - Includes run summary based on 'workflow' object, 'workflow.manifest' object created in nextflow.config, and vparams object created by ParamsChecker. This allows developer to consolidate important information about the pipeline build, pipeline run, and user inputs.

Parameter Checker Utility
- Allows the user to provide parameter definitions to validate command line arguments
- How it works:
    - Each pipeline has a base.config that defines the pipeline's parameters
    - Each parameter has a 'definition' that looks like this:
    definitions {
        parameter_1 {
            default_value = null
            type = <supported: "integer", "float", "path", "flag", "string">
            description = "Describe your parameter here"
            required = <supported: true, false>
        }
    }
    - Each parameter definition has its default value replaced if 
    - Based on definitions
        - if 'required' is true, 