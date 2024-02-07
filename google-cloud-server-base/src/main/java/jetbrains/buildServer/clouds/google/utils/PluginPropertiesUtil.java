

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