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

import es.bsc.inb.ga4gh.beacon.framework.model.v200.configuration.ServiceConfiguration;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconEntryTypesResponse;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconFilteringTermsResponse;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconInfoResponse;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconInformationalResponse;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconMapResponse;
import es.elixir.bsc.json.schema.JsonSchemaException;
import es.elixir.bsc.json.schema.JsonSchemaReader;
import es.elixir.bsc.json.schema.ValidationError;
import es.elixir.bsc.json.schema.model.JsonSchema;
import jakarta.json.Json;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author Dmitry Repchevsky
 */

public class BeaconMetadataValidator {

    private final Jsonb jsonb;
    
    private final Map<BeaconMetadataSchema, JsonSchema> schemas;

    public final Map<BeaconMetadataSchema, String> ENDPOINTS;
    public final Map<BeaconMetadataSchema, Class<? extends BeaconInformationalResponse>> MODELS;
    
    public BeaconMetadataValidator() {
        this(JsonbBuilder.newBuilder().build());
    }
    
    public BeaconMetadataValidator(Jsonb jsonb) {
        this.jsonb = jsonb;

        schemas = Arrays.stream(BeaconMetadataSchema.values())
                .collect(Collectors.toMap(s -> s, s -> loadSchema(s.SCHEMA)));
        
        ENDPOINTS = Map.of(
            BeaconMetadataSchema.BEACON_INFO_RESPONSE_SCHEMA, "/info", 
            BeaconMetadataSchema.BEACON_CONFIGURATION_SCHEMA, "/configuration", 
            BeaconMetadataSchema.BEACON_MAP_RESPONSE_SCHEMA, "/map", 
            BeaconMetadataSchema.BEACON_ENTRY_TYPES_SCHEMA, "/entry_types",
            BeaconMetadataSchema.BEACON_FILTERING_TERMS_SCHEMA, "/filtering_terms");
        
        MODELS = Map.of(
            BeaconMetadataSchema.BEACON_INFO_RESPONSE_SCHEMA, BeaconInfoResponse.class, 
            BeaconMetadataSchema.BEACON_CONFIGURATION_SCHEMA, ServiceConfiguration.class, 
            BeaconMetadataSchema.BEACON_MAP_RESPONSE_SCHEMA, BeaconMapResponse.class, 
            BeaconMetadataSchema.BEACON_ENTRY_TYPES_SCHEMA, BeaconEntryTypesResponse.class,
            BeaconMetadataSchema.BEACON_FILTERING_TERMS_SCHEMA, BeaconFilteringTermsResponse.class);
    }

    public Map<BeaconMetadataSchema, ? extends BeaconInformationalResponse> validate(
            String endpoint, ValidationObserver reporter) {
        
        final Map<BeaconMetadataSchema, BeaconInformationalResponse> metadata = new HashMap();
                
        for (BeaconMetadataSchema schema : BeaconMetadataSchema.values()) {
            final String json = loadMetadata(endpoint, schema, reporter);
            if (json != null) {
                try (JsonReader reader = Json.createReader(new StringReader(json))) {
                    final JsonValue value = reader.readValue();
                    final List<ValidationError> errors = new ArrayList();
                    validate(schema, value, errors);
                    for (ValidationError ve : errors) {
                        reporter.error(new BeaconValidationMessage(ve));
                    }
                    final BeaconInformationalResponse response = parseMetadata(json, schema);
                    if (response != null) {
                        metadata.put(schema, response);
                    }
                }
            }
        }
        
        return metadata;
    }

    public List<BeaconValidationMessage> validate(BeaconMetadataSchema schema, JsonValue json) {
        final List<ValidationError> errors = new ArrayList();
        validate(schema, json,  errors);
        return errors.stream().map(BeaconValidationMessage::new).collect(Collectors.toList());
    }
    
    public BeaconInformationalResponse parseMetadata(String json, BeaconMetadataSchema schema) {
        final Class<? extends BeaconInformationalResponse> clazz = MODELS.get(schema);
        try {
            return jsonb.fromJson(json, clazz);
        } catch (Exception ex) {}
        
        return null;
    }

    public String loadMetadata(String endpoint, BeaconMetadataSchema schema, ValidationObserver reporter) {
        return loadMetadata(endpoint + ENDPOINTS.get(schema), reporter);
    }
    
    public String loadMetadata(String endpoint, ValidationObserver reporter) {
        reporter.message(String.format("loading metadata: %s", endpoint));
        try {
            final URI uri = new URI(endpoint + "?limit=0");
            if (!uri.isAbsolute()) {
                reporter.error(new BeaconValidationMessage(
                        BeaconValidationErrorType.CONNECTION_ERROR,
                        0,
                        endpoint, null,
                        String.format("relative Beacon endpoint %s", endpoint)));
                return null;
            }

            final HttpResponse<String> http_response = ValidatorBeaconRequest.getHttpResponse(uri);
            if (http_response.statusCode() >= 300) {
                reporter.error(new BeaconValidationMessage(
                        BeaconValidationErrorType.CONNECTION_ERROR,
                        http_response.statusCode(),
                        http_response.uri().toString(), null,
                        String.format("error loading from %s", endpoint)));
                return null;
            }
            
            final String content = http_response.body();
            if (content == null) {
                reporter.error(new BeaconValidationMessage(
                        BeaconValidationErrorType.CONTENT_ERROR,
                        HttpURLConnection.HTTP_NO_CONTENT,
                        http_response.uri().toString(), null,
                        String.format("empty response from %s", endpoint)));
                return null;
            }
            return content;
        } catch(Exception ex) {
            reporter.error(new BeaconValidationMessage(
                    BeaconValidationErrorType.CONNECTION_ERROR,
                    null, endpoint, null,
                    String.format("error loading from %s ", ex.getMessage())));
        }
        
        return null;
    }
    
    private boolean validate(BeaconMetadataSchema schema, JsonValue json, List<ValidationError> errors) {
        final JsonSchema jschema = schemas.get(schema);
        if (jschema == null) {
            errors.add(new ValidationError("internal error: unresolved schema"));
            return false;
        }

        return jschema.validate(json, errors);
    }
    
    private JsonSchema loadSchema(String path) {
        final URL url = BeaconMetadataValidator.class.getClassLoader().getResource(path);
        try {
            return JsonSchemaReader.getReader().read(url);
        } catch (JsonSchemaException ex) {
            Logger.getLogger(BeaconMetadataValidator.class.getName())
                    .log(Level.SEVERE, null, ex.error.message);
        }
        return null;
    }
}
