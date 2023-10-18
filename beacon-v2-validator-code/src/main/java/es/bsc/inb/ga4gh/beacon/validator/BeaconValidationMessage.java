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

import es.elixir.bsc.json.schema.ValidationError;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;

/**
 * @author Dmitry Repchevsky
 */

public class BeaconValidationMessage {
    
    public final BeaconValidationErrorType type;
    public final Integer code;
    public final String location;
    public final String path;
    public final String message;
    
    public BeaconValidationMessage(ValidationError error) {
        this(BeaconValidationErrorType.JSON_SCHEMA_ERROR, error.code, 
                error.id == null ? null : error.id.toString(), error.path, error.message);
    }
    
    public BeaconValidationMessage(BeaconValidationErrorType type, String message) {
        this(type, null, null, null, message);
    }
    
    public BeaconValidationMessage(BeaconValidationErrorType type, Integer code, 
            String location, String path, String message) {
        
        this.type = type;
        this.code = code;
        this.location = location;
        this.path = path;
        this.message = message;
    }
    
    @Override
    public String toString() {
        return (message != null ? message : "") +
               (path != null ? path : "") +
               (code != null ? " (" + code + ") " : " ") +
               (location != null ? location : "");
    }
}
