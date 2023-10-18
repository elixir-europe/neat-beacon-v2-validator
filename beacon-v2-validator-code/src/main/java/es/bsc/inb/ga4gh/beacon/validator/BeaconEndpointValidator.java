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

import es.bsc.inb.ga4gh.beacon.framework.model.v200.common.SchemaPerEntity;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.configuration.BeaconConfiguration;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.configuration.BeaconMap;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.configuration.Endpoint;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.configuration.RelatedEndpoint;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.AbstractBeaconResponse;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconCollections;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconCollectionsResponse;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconInformationalResponseMeta;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconResultset;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconResultsets;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconResultsetsResponse;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.EntryTypeDefinition;
import es.elixir.bsc.json.schema.JsonSchemaReader;
import es.elixir.bsc.json.schema.ValidationError;
import es.elixir.bsc.json.schema.model.JsonSchema;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * @author Dmitry Repchevsky
 */

public class BeaconEndpointValidator {
    
    private static final Jsonb JSONB = JsonbBuilder.create();
    
    private final BeaconMetadataModel model;
    private final JsonSchema response_schema;
    
    private final JsonObject dummy_request;
    private final Pattern param_pattern = Pattern.compile("\\{.*\\}");
    
    public BeaconEndpointValidator(BeaconMetadataModel model) {
        this.model = model;
        
        JsonSchema schema = null;
        try {
            final URL url = BeaconEndpointValidator.class.getClassLoader().getResource(BeaconFrameworkSchema.BEACON_RESPONSE_SCHEMA.SCHEMA);
            if (url != null) {
                schema = JsonSchemaReader.getReader().read(url);
            }
        } catch(Exception ex) {
            Logger.getLogger(BeaconEndpointValidator.class.getName()).log(Level.SEVERE, "error loading schema {0} {1}", 
                    new Object[]{BeaconFrameworkSchema.BEACON_RESPONSE_SCHEMA.SCHEMA, ex.getMessage()});
        }

        response_schema = schema;
        dummy_request = createDummyRequest();
    }

    /**
     * Validate the Beacon API.
     * 
     * @param beacon_endpoint Beacon's API endpoint
     * @param reporter validation process observer
     */
    public void validate(String beacon_endpoint, ValidationObserver reporter) {
        
        if (model.map != null) {
            final BeaconMap response = model.map.getResponse();
            final Map<String, Endpoint> endpoints = response.getEndpointSets();
            if (endpoints != null) {
                final URI beacon_endpoint_uri = URI.create(beacon_endpoint);
                for (Map.Entry<String, Endpoint> entry : endpoints.entrySet()) {
                    validateEndpoint(beacon_endpoint_uri, entry.getKey(), 
                            entry.getValue(), reporter);
                }
            }
        }
    }
    
    private void validateEndpoint(URI beacon_endpoint_uri, String endpoint_name, 
            Endpoint endpoint, ValidationObserver reporter) {
        
        reporter.message(String.format("validate endpoints: [%s] %s", endpoint_name, beacon_endpoint_uri));
        
        final String root = endpoint.getRootUrl();
        if (root == null) {
            reporter.error(new BeaconValidationMessage(
                    BeaconValidationErrorType.CONTENT_ERROR,
                    null, beacon_endpoint_uri.toString(), null,
                    "no 'root' endpoint found."));
            return;
        }
        
        final String root_endpoint = resolve(beacon_endpoint_uri, root);
        if (root_endpoint == null) {
            reporter.error(new BeaconValidationMessage(
                    BeaconValidationErrorType.CONTENT_ERROR,
                    null, beacon_endpoint_uri.toString(), null,
                    String.format("invalid 'root' endpoint: %s", root)));
            return;
        }
        
        final URI root_endpoint_uri = URI.create(root_endpoint);
        
        final AbstractBeaconResponse response = 
                validateEntryEndpoint(root_endpoint, reporter);
        
        if (response != null) {
            final String entryType = endpoint.getEntryType();
            final JsonObject entry = validateResponse(response, entryType, reporter);
            if (entry == null) {
                reporter.error(new BeaconValidationMessage(
                        BeaconValidationErrorType.CONTENT_ERROR,
                        null, beacon_endpoint_uri.toString(), null,
                        String.format("unable to resolve [%s] identifier, as %s returned no 'results'", endpoint_name, root_endpoint)));
            } else {
                final String singleEntryUrl = endpoint.getSingleEntryUrl();
                validateEndpoint(root_endpoint_uri, singleEntryUrl, entryType, entry, reporter);
                
                final Map<String, RelatedEndpoint> endpoints = endpoint.getEndpoints();
                if (endpoints != null) {
                    for (RelatedEndpoint related_endpoint : endpoints.values()) {
                        final String url = related_endpoint.getUrl();
                        final String returnedEntryType = related_endpoint.getReturnedEntryType();
                        validateEndpoint(root_endpoint_uri, url, returnedEntryType, entry, reporter);
                    }
                }
            }
        }
    }
    
    private void validateEndpoint(URI root_endpoint_uri, String endpoint_template, 
            String entryType, JsonObject entry, ValidationObserver reporter) {

        if (endpoint_template != null) {
            final String template = resolve(root_endpoint_uri, endpoint_template);
            final String single_entry_endpoint = resolveTemplateParameters(template, entry);

            if (param_pattern.matcher(single_entry_endpoint).find()) {
                reporter.error(new BeaconValidationMessage(
                        BeaconValidationErrorType.CONTENT_ERROR,
                        null, single_entry_endpoint, null,
                        "can't resolve identifier"));
            } else {
                final AbstractBeaconResponse response = 
                    validateEntryEndpoint(single_entry_endpoint, reporter);

                if (response != null) {
                    validateResponse(response, entryType, reporter);
                }
            }
        }
    }
            
    private String resolveTemplateParameters(String template, JsonObject entry) {
        final JsonValue _id = entry.get("id");
        
        final StringBuilder single_entry_endpoint = new StringBuilder(template);

        // need reverse processing to properly replace.
        final List<MatchResult> matches = param_pattern.matcher(template).results().toList();
        for (int i = matches.size() - 1; i >= 0; i--) {
            final MatchResult match = matches.get(i);
            final String name = template.substring(match.start() + 1, match.end() - 1);
            final JsonValue id = entry.getOrDefault(name, _id);
            if (id != null && id.getValueType() == JsonValue.ValueType.STRING) {
                final String entryId = ((JsonString)id).getString();
                single_entry_endpoint.replace(match.start(), match.end(), entryId);
            }
        }
        return single_entry_endpoint.toString();
    }

    private String getSchemaURL(String entityType) {
        if (model.info != null) {
            final BeaconInformationalResponseMeta meta = model.info.getMeta();
            final List<SchemaPerEntity> returnedSchemas = meta.getReturnedSchemas();
            if (returnedSchemas != null) {
                for (SchemaPerEntity returnedSchema : returnedSchemas) {
                    if (entityType.equals(returnedSchema.getEntityType())) {
                        return returnedSchema.getSchema();
                    }
                }
            }
        }
        
        if (model.configuration != null) {
            final BeaconConfiguration configuration = model.configuration.getResponse();
            if (configuration != null) {
                final Map<String, EntryTypeDefinition> entryTypes = configuration.getEntryTypes();
                if (entryTypes != null) {
                    final EntryTypeDefinition def = entryTypes.get(entityType);
                    if (def != null && def.getDefaultSchema() != null) {
                        return def.getDefaultSchema().getReferenceToSchemaDefinition();
                    }
                }
            }
        }

        return null;
    }

    private AbstractBeaconResponse validateEntryEndpoint(String endpoint,
            ValidationObserver reporter) {

        reporter.message(String.format("  validate endpoint: %s", endpoint));
        
        AbstractBeaconResponse response = null;
        
        final String json = callEndpoint(endpoint, dummy_request, reporter);
        if (json != null) {
            try (JsonReader reader = Json.createReader(new StringReader(json))) {
                final JsonValue value = reader.readValue();
                if (response_schema != null) {
                    final List<ValidationError> errors = new ArrayList();
                    if (!response_schema.validate(value, errors)) {
                        for (ValidationError error : errors) {
                            reporter.error(new BeaconValidationMessage(error));
                        }
                    }
                    
                }
                    
                final JsonObject o = value.asJsonObject().getJsonObject("response");
                if (o.containsKey("collections")) {
                    return JSONB.fromJson(json, 
                            new BeaconCollectionsResponse<JsonObject>(){}
                                    .getClass().getGenericSuperclass());
                } else {
                    return JSONB.fromJson(json, 
                            new BeaconResultsetsResponse<JsonObject>(){}
                                    .getClass().getGenericSuperclass());
                }                    
            } catch (Exception ex) {
                reporter.error(new BeaconValidationMessage(
                        BeaconValidationErrorType.CONTENT_ERROR,
                        null, endpoint, null, ex.getMessage()));

            }
        }

        return response;
    }

    private JsonObject validateResponse(AbstractBeaconResponse response, 
            String entryType, ValidationObserver reporter) {

        JsonObject entry = null;
        
        final String entryTypeSchema = (entryType == null) ? null : getSchemaURL(entryType);
        final JsonSchema schema = model.loadSchema(entryTypeSchema, entryType, reporter);

        final List<JsonObject> entries = new ArrayList();
        if (response instanceof BeaconCollectionsResponse res) {
            final BeaconCollections<JsonObject> col = res.getResponse();
            if (col != null && col.getCollections() != null) {
                entries.addAll(col.getCollections());
            }
        } else if (response instanceof BeaconResultsetsResponse res) {
            final BeaconResultsets<JsonObject> resultsets = res.getResponse();
            if (resultsets != null && resultsets.getResultSets() != null) {
                for (BeaconResultset resultset : resultsets.getResultSets()) {
                    final List<JsonObject> results = resultset.getResults();
                    if (results != null) {
                        entries.addAll(results);
                    }
                }
            }
        }
        
        if (schema != null) {
            final List<ValidationError> errors = new ArrayList();
            for (JsonObject obj : entries) {
                if (schema.validate(obj, errors) && entry == null) {
                    entry = obj; // keep first found valid entry;
                }
            }
            for (ValidationError ve : errors) {
                reporter.error(new BeaconValidationMessage(ve));
            }
        }

        // return first entry when no schema or all are invalid
        if (entry == null && !entries.isEmpty()) {
            entry = entries.get(0);
        }
                
        return entry;
    }
    
    private String callEndpoint(String endpoint, JsonObject request, 
            ValidationObserver reporter) {

        try {
            final URI uri = new URI(endpoint);
            if (!uri.isAbsolute()) {
                reporter.error(new BeaconValidationMessage(
                        BeaconValidationErrorType.CONNECTION_ERROR,
                        0,
                        endpoint, null,
                        String.format("relative Beacon endpoint '%s'", endpoint)));
                return null;
            }
            final HttpResponse<String> http_response = 
                    ValidatorBeaconRequest.postHttpRequest(uri, request.toString());

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
    
    private JsonObject createDummyRequest() {

        final JsonObjectBuilder builder = Json.createObjectBuilder();
        final BeaconInformationalResponseMeta meta = model.info == null ? null : model.info.getMeta();
        builder.add("meta", Json.createObjectBuilder().add("apiVersion", 
                meta == null || meta.getApiVersion() == null ?  "v2.0.0" : meta.getApiVersion()));
        
        builder.add("query", Json.createObjectBuilder()
                .add("testMode", true)
                .add("requestedGranularity","record")
                .add("pagination", Json.createObjectBuilder().add("skip", 0).add("limit", 1)));
        
        return builder.build();
    }

    private String resolve(URI base_uri, String url) {
        url = url.replaceAll("\\{", "%7B");
        url = url.replaceAll("\\}", "%7D");
        try {
            URI uri = URI.create(url);
            if (!uri.isAbsolute()) {
                uri = base_uri.resolve(uri);
            }
            return URLDecoder.decode(uri.toString(), StandardCharsets.UTF_8);
        } catch(IllegalArgumentException ex) {}
        
        return null;
    }
}
