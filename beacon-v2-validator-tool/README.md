###  Simple Beacon v2 validator

Building the **neat-beacon-v2-validator** leaves validator commandline tool in the **target** directory:  
neat-beacon-v2-validator/beacon-v2-validator-tool/target/neat-beacon-v2-validator.jar

The tool needs the Beacon's API endpoint for validation and optionally output report file.  

Usage:
```
beacon-validator -f url [-o file] 

parameters:
  -h (--help)           - help message
  -f (--framework)      - location of the beacon
  -o (--output)         - report output file
examples:

java -jar neat-beacon-v2-validator.jar -f https://beacon-apis-demo.ega-archive.org/api

java -jar neat-beacon-v2-validator.jar -f https://beacon-apis-demo.ega-archive.org/api -o report.json
```
