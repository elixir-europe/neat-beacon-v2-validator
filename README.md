# Beacon validator for the Beacon Network

The following code validates a beacon instance to know if it is suitable to query in the ELIXIR Beacon Network. Also, it reports the schema errors that the instance might have.

## Run the script

If you want to validate your beacon you need [Apache Maven](https://maven.apache.org/index.html).

- Enter to the folder and build the code:

```
cd beacon-v2-validator-tool
mvn install
```

Once it is build, you should see a `target` directory in the `beacon-v2-validator-tool` folder. There there is the script, which need the Beacon's API endpoint for validation.

- Usage with examples:

```
java -jar neat-beacon-v2-validator.jar -f https://beacons.bsc.es/beacon/v2.0.0/

java -jar neat-beacon-v2-validator.jar -f https://beacons.bsc.es/beacon/v2.0.0/ -o report.json
```

And available parameters:

```
  -h (--help)           - help message
  -f (--framework)      - location of the beacon
  -o (--output)         - report output file
```

