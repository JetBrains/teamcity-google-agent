/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.clouds.google.utils;

import jetbrains.buildServer.controllers.BasePropertiesBean;
import jetbrains.buildServer.serverSide.crypt.RSACipher;

import java.util.Map;

/**
 * @author Pavel.Sher
 *         Date: 25.05.2006
 */
public class PluginPropertiesUtil {
    private final static String PROPERTY_PREFIX = "prop:";
    private static final String ENCRYPTED_PROPERTY_PREFIX = "prop:encrypted:";

    private PluginPropertiesUtil() {}

    public static void bindPropertiesFromRequest(Map<String, String> parameters, BasePropertiesBean bean) {
        bindPropertiesFromRequest(parameters, bean, false);
    }

    public static void bindPropertiesFromRequest(Map<String, String> parameters, BasePropertiesBean bean, boolean includeEmptyValues) {
        bean.clearProperties();

        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            final String paramName = entry.getKey();
            if (paramName.startsWith(PROPERTY_PREFIX)) {
                if (paramName.startsWith(ENCRYPTED_PROPERTY_PREFIX)) {
                    setEncryptedProperty(paramName, entry.getValue(), bean, includeEmptyValues);
                } else {
                    setStringProperty(paramName, entry.getValue(), bean, includeEmptyValues);
                }
            }
        }
    }

    private static void setStringProperty(final String paramName, final String propertyValue,
                                          final BasePropertiesBean bean, final boolean includeEmptyValues) {
        String propName = paramName.substring(PROPERTY_PREFIX.length());
        if (includeEmptyValues || propertyValue.length() > 0) {
            bean.setProperty(propName, toUnixLineFeeds(propertyValue));
        }
    }

    private static void setEncryptedProperty(final String paramName, final String value,
                                             final BasePropertiesBean bean, final boolean includeEmptyValues) {
        String propName = paramName.substring(ENCRYPTED_PROPERTY_PREFIX.length());
        String propertyValue = RSACipher.decryptWebRequestData(value);
        if (propertyValue != null && (includeEmptyValues || propertyValue.length() > 0)) {
            bean.setProperty(propName, toUnixLineFeeds(propertyValue));
        }
    }

    private static String toUnixLineFeeds(final String str) {
        return str.replace("\r", "");
    }
}
