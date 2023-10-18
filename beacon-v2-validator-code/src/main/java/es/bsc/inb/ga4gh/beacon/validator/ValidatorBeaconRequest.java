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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * @author Dmitry Repchevsky
 */
public final class ValidatorBeaconRequest {
    
    private final static HttpClient http_client = 
            HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();

    
    /**
     * Method to read beacons' metadata responses.
     * 
     * @param beacon_endpoint the endpoint URL
     * @return the HTTP Response object
     * @throws IOException
     * @throws InterruptedException 
     */
    public static HttpResponse<String> getHttpResponse(URI beacon_endpoint) 
            throws IOException, InterruptedException {

        HttpRequest.Builder builder = HttpRequest.newBuilder(beacon_endpoint)
                .header("User-Agent", "BN/2.0.0")
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Expires", "0")
                .GET();
        
        return http_client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    
    public static HttpResponse<String> postHttpRequest(URI beacon_endpoint, String query)
            throws IOException, InterruptedException {
        
        HttpRequest.Builder builder = HttpRequest.newBuilder(beacon_endpoint)
                .header("User-Agent", "BN/2.0.0")
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Expires", "0")
                .POST(BodyPublishers.ofString(query, StandardCharsets.UTF_8));
        
        return http_client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
