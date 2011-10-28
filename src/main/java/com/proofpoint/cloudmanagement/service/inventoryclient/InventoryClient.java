/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.proofpoint.cloudmanagement.service.inventoryclient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.JsonBodyGenerator;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.RequestBuilder;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.json.JsonCodec;
import org.jclouds.encryption.internal.Base64;

import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.Map;
import java.util.Set;

public class InventoryClient
{
    private final HttpClient client;
    private final URI inventoryHost;
    private final String authorization;

    private static final JsonCodec<System> SYSTEM_DATA_CODEC = JsonCodec.jsonCodec(System.class);
    private static final JsonCodec<Map<String, String>> MAP_JSON_CODEC = JsonCodec.mapJsonCodec(String.class, String.class);

    @Inject
    public InventoryClient(InventoryClientConfig config, HttpClient client)
    {
        this.inventoryHost = config.getInventoryUri();
        this.authorization = basicAuthEncode(config.getUserId(), config.getPassword());

        this.client = client;
    }

    public String getPcmSystemName(String instanceId)
            throws Exception
    {
        Preconditions.checkNotNull(instanceId, "instanceId is null");

        Request request =
                RequestBuilder.prepareGet()
                        .setUri(UriBuilder.fromUri(inventoryHost).path("pcmsystemname/{instanceId}").build(instanceId))
                        .setHeader("Authorization", authorization)
                        .build();

        Map<String, String> response = client.execute(request, JsonResponseHandler.newHandler(MAP_JSON_CODEC)).checkedGet();
        return response.get("fqdn");
    }

    public System getSystem(String systemName)
            throws Exception
    {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(systemName), "systemName is required");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(authorization), "authToken is required");

        Request request =
                RequestBuilder.prepareGet()
                        .setUri(UriBuilder.fromUri(inventoryHost).path("system/{system}").build(systemName))
                        .setHeader("Authorization", authorization)
                        .build();

        return client.execute(request, JsonResponseHandler.newHandler(SYSTEM_DATA_CODEC)).checkedGet();
    }

    public void setSystemRoles(String systemName, Set<String> roles)
        throws Exception
    {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(systemName), "systemName is required");
        Preconditions.checkNotNull(roles, "roles is null");

        Map<String, String> data = ImmutableMap.of("roles", Joiner.on(',').join(roles));

        Request request =
                RequestBuilder.preparePut()
                        .setUri(UriBuilder.fromUri(inventoryHost).path("system/{system}").build(systemName))
                        .setHeader("Authorization", authorization)
                        .setBodyGenerator(JsonBodyGenerator.jsonBodyGenerator(MAP_JSON_CODEC, data))
                        .build();

        client.execute(request, new AssertSuccessHandler()).checkedGet();
    }

    @VisibleForTesting
    static String basicAuthEncode(String user, String pass)
    {
        return String.format("Basic %s",
                Base64.encodeBytes(String.format("%s:%s", user, pass).getBytes(Charsets.UTF_8)));
    }

    private static class JsonResponseHandler<T> implements ResponseHandler<T, Exception>
    {
        private final JsonCodec<T> codec;

        public static <T> JsonResponseHandler<T> newHandler(JsonCodec<T> codec)
        {
            return new JsonResponseHandler<T>(codec);
        }

        private JsonResponseHandler(JsonCodec<T> codec)
        {
            this.codec = codec;
        }

        @Override
        public Exception handleException(Request request, Exception e)
        {
            return e;
        }

        @Override
        public T handle(Request request, Response response)
                throws Exception
        {
            String body = new String(ByteStreams.toByteArray(response.getInputStream()), Charsets.UTF_8);

            if (response.getStatusCode()/100 != 2) {
                throw new RuntimeException(String.format("Request failed with code %d: Body -->|%s|<--", response.getStatusCode(), body));
            }

            if (Strings.isNullOrEmpty(body))
                return null;

            try {
                return codec.fromJson(body);
            }
            catch (IllegalArgumentException ex) {
                throw new RuntimeException(String.format("Invalid body -->|%s|<--", body), ex);
            }
        }
    }

    private static class AssertSuccessHandler implements ResponseHandler<Void, Exception>
    {
        private final Set<Integer> acceptableCodes;

        public AssertSuccessHandler()
        {
            this(ImmutableSet.of(200, 204));
        }

        public AssertSuccessHandler(Set<Integer> acceptableCodes)
        {
            this.acceptableCodes = ImmutableSet.copyOf(acceptableCodes);
        }

        @Override
        public Exception handleException(Request request, Exception exception)
        {
            return exception;
        }

        @Override
        public Void handle(Request request, Response response)
                throws Exception
        {
            String body = new String(ByteStreams.toByteArray(response.getInputStream()), Charsets.UTF_8);

            if (!acceptableCodes.contains(response.getStatusCode())) {
                throw new RuntimeException(String.format("Request failed with code %d: Body -->|%s|<--", response.getStatusCode(), body));
            }

            return null;
        }
    }
}
