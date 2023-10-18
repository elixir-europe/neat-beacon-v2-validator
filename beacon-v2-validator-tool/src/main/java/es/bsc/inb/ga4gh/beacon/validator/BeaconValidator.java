/**
 * *****************************************************************************
 * Copyright (C) 2023 ELIXIR ES, Spanish National Bioinformatics Institute (INB)
 * and Barcelona Supercomputing Center (BSC)
 *
 * Modifications to the initial code base are copyright of their respective
 * authors, or their employers as appropriate.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 *****************************************************************************
 */

package es.bsc.inb.ga4gh.beacon.validator;

import jakarta.json.Json;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonGeneratorFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Dmitry Repchevsky
 */

public class BeaconValidator {
    
    private final static String HELP = 
            """
            beacon-validator -f url [-o file] 
            parameters:
              -h (--help)           - this help message
              -f (--framework)      - location of the beacon
              -o (--output)         - report output file
            examples:
              >java -jar beacon-validator.jar -f https://beacons.bsc.es/beacon/v2.0.0/
              >java -jar beacon-validator.jar -f https://beacons.bsc.es/beacon/v2.0.0/ -o report.json
            """;

    public static void main(String[] args) {
        Map<String, List<String>> params = parameters(args);
        
        if (params.isEmpty() || 
            params.get("-h") != null ||
            params.get("--help") != null) {
            System.out.println(HELP);
            System.exit(0);            
        }
        
        List<String> frameworks = params.get("-f");
        if (frameworks == null) {
            frameworks = params.get("--framework");
        } else if (params.containsKey("--framework")) {
            System.err.println("only one of the forms may be used - either '-f' or '--framework'");
            System.exit(1);
        }
        
        if (frameworks == null) {
            System.err.println("no beacon lacation specified");
            System.exit(1);
        } else if (frameworks.size() > 1) {
            System.err.println("more than one locations specified");
            System.exit(1);            
        }

        final String framework = frameworks.get(0);
        
        final List<BeaconValidationMessage> errors = new ArrayList();
        final ConsoleValidationObserver reporter = new ConsoleValidationObserver(errors);
        
        final BeaconMetadataModel model = BeaconMetadataModel.load(framework, reporter);
        final BeaconEndpointValidator validator = new BeaconEndpointValidator(model);
        
        validator.validate(framework, reporter);
        
        List<String> outputs = params.get("-o");
        if (outputs == null) {
            outputs = params.get("--output");
        }
        
        if (outputs != null && !outputs.isEmpty()) {
            writeErrors(outputs.get(0), errors);
        }
    }
    
    private static void writeErrors(String file, List<BeaconValidationMessage> errors) {
        
        final JsonGeneratorFactory f = Json.createGeneratorFactory(Map.of(JsonGenerator.PRETTY_PRINTING, true));
        try(JsonGenerator g = f.createGenerator(Files.newBufferedWriter(
                Paths.get(file), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            g.writeStartArray();
            for (BeaconValidationMessage error : errors) {
                g.writeStartObject();
                if (error.code != null) {
                    g.write("code", error.code);
                }
                if (error.path != null) {
                    g.write("path", error.path);
                }
                if (error.location != null) {
                    g.write("location", error.location);
                }
                if (error.message != null) {
                    g.write("message", error.message);
                }
                g.writeEnd();
            }
            g.writeEnd();
        } catch (IOException ex) {
            Logger.getLogger(BeaconValidator.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private static Map<String, List<String>> parameters(String[] args) {
        TreeMap<String, List<String>> parameters = new TreeMap();        
        List<String> values = null;
        for (String arg : args) {
            switch(arg) {
                case "-h", "--help", "-f", "--framework", "-o", "--output" -> {
                    values = parameters.get(arg);
                    if (values == null) {
                        values = new ArrayList();
                        parameters.put(arg, values);
                    }
                }
                default -> {
                    if (values != null) {
                        values.add(arg);
                    }
                }
            }
        }
        return parameters;
    }
}
