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

import com.google.common.collect.ImmutableMap;
import com.proofpoint.configuration.testing.ConfigAssertions;
import org.testng.annotations.Test;

public class TestInventoryClientConfig
{
    @Test
    public void testDefaults()
    {
        ConfigAssertions.assertRecordedDefaults(ConfigAssertions.recordDefaults(InventoryClientConfig.class)
                .setUserId(null)
                .setPassword(null)
                .setInventoryUri(null));
    }

    @Test
    public void testExplicitPropertyMappings()
    {
        ConfigAssertions.assertFullMapping(
                ImmutableMap.<String, String>builder()
                        .put("inventory.user", "user")
                        .put("inventory.password", "password")
                        .put("inventory.base-uri", "http://localhost/api/")
                        .build(),
                new InventoryClientConfig()
                        .setUserId("user")
                        .setPassword("password")
                        .setInventoryUri("http://localhost/api/"));
    }
}
