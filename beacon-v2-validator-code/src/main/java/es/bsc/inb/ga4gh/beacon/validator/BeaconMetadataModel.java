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
import es.bsc.inb.ga4gh.beacon.framework.model.v200.configuration.ServiceConfiguration;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconEntryTypesResponse;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconFilteringTermsResponse;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconInfoResponse;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconInformationalResponse;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconInformationalResponseMeta;
import es.bsc.inb.ga4gh.beacon.framework.model.v200.responses.BeaconMapResponse;
import es.elixir.bsc.json.schema.JsonSchemaException;
import es.elixir.bsc.json.schema.JsonSchemaReader;
import es.elixir.bsc.json.schema.model.JsonSchema;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Repchevsky
 */

public class BeaconMetadataModel {
    
    public final BeaconInfoResponse info;
    public final BeaconMapResponse map;
    public final ServiceConfiguration configuration;
    public final BeaconEntryTypesResponse entry_types;
    public final BeaconFilteringTermsResponse filtering_terms;
    
    private final JsonSchemaReader reader;
    
    private BeaconMetadataModel(Map<BeaconMetadataSchema, ? extends BeaconInformationalResponse> metadata) {
        info = (BeaconInfoResponse)metadata.get(BeaconMetadataSchema.BEACON_INFO_RESPONSE_SCHEMA);
        map = (BeaconMapResponse)metadata.get(BeaconMetadataSchema.BEACON_MAP_RESPONSE_SCHEMA);
        configuration = (ServiceConfiguration)metadata.get(BeaconMetadataSchema.BEACON_CONFIGURATION_SCHEMA);
        entry_types = (BeaconEntryTypesResponse)metadata.get(BeaconMetadataSchema.BEACON_ENTRY_TYPES_SCHEMA);
        filtering_terms = (BeaconFilteringTermsResponse)metadata.get(BeaconMetadataSchema.BEACON_FILTERING_TERMS_SCHEMA);
        
        reader = JsonSchemaReader.getReader();
    }

    public JsonSchema loadSchema(String schemaEndpoint, String entityType, ValidationObserver reporter) {
        try {
            final URI uri = new URI(schemaEndpoint);
            if (uri.isAbsolute()) {
                final URL url = uri.toURL();
                return reader.read(url);
            } else {
                reporter.error(new BeaconValidationMessage(
                        BeaconValidationErrorType.CONTENT_ERROR,
                        null, null, null,
                        String.format("not absolute URL for the '%s' returned schema: '%s'", 
                                entityType, schemaEndpoint)));
            }
        } catch (URISyntaxException | MalformedURLException ex) {
                reporter.error(new BeaconValidationMessage(
                        BeaconValidationErrorType.CONTENT_ERROR,
                        null, null, null,
                        String.format("malformed URL for the '%s' returned schema: '%s'", 
                                entityType, schemaEndpoint)));
        } catch (JsonSchemaException ex) {
            reporter.error(new BeaconValidationMessage(
                    BeaconValidationErrorType.CONTENT_ERROR,
                    ex.error.code, null, null,
                    String.format("%s error parsing the '%s' schema: %s", 
                            entityType, schemaEndpoint, ex.error.message)));
        }
        
        return null;
    }

    private void loadInfoSchemas(ValidationObserver reporter) {
        if (info != null) {
            final BeaconInformationalResponseMeta meta = info.getMeta();
            final List<SchemaPerEntity> returnedSchemas = meta.getReturnedSchemas();
            if (returnedSchemas != null) {
                for (int i = 0, n = returnedSchemas.size(); i < n; i++) {
                    final SchemaPerEntity returnedSchema = returnedSchemas.get(i);
                    final List<BeaconValidationMessage> err = new ArrayList();
                    loadSchema(returnedSchema.getSchema(), returnedSchema.getEntityType(), 
                            new ValidationErrorsCollector(err));
                    
                    // set up 'location'
                    for (BeaconValidationMessage e : err) {
                        reporter.error(new BeaconValidationMessage(
                            e.type, e.code, String.format("/info/meta/returnedSchemas/%d/schema", i), 
                            e.path, e.message));
                    }
                }
            }
        }
    }
    
    public static BeaconMetadataModel load(String beacon_api_endpoint,
            ValidationObserver reporter) {
        
        final BeaconMetadataValidator metadata_validator = new BeaconMetadataValidator();
        
        final Map<BeaconMetadataSchema, ? extends BeaconInformationalResponse> metadata = 
                metadata_validator.validate(beacon_api_endpoint, reporter);
        
        final BeaconMetadataModel model = new BeaconMetadataModel(metadata);
        
        model.loadInfoSchemas(reporter);
                
        return model;
    }
}
