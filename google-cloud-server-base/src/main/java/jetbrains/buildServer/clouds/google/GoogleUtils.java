

package jetbrains.buildServer.clouds.google;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import jetbrains.buildServer.clouds.CloudClientParameters;
import jetbrains.buildServer.clouds.CloudImageParameters;
import jetbrains.buildServer.clouds.CloudInstanceUserData;
import jetbrains.buildServer.clouds.base.beans.CloudImageDetails;
import jetbrains.buildServer.clouds.base.beans.CloudImagePasswordDetails;
import jetbrains.buildServer.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Provides utils for google services.
 */
public final class GoogleUtils {
    private static final Type stringStringMapType = new TypeToken<Map<String, String>>() {
    }.getType();

    public static <T extends CloudImageDetails> Collection<T> parseImageData(Class<T> clazz, final CloudClientParameters params) {
        Gson gson = new Gson();
        final String imageData = StringUtil.emptyIfNull(params.getParameter(CloudImageParameters.SOURCE_IMAGES_JSON));
        if (StringUtil.isEmpty(imageData)) {
            return Collections.emptyList();
        }

        final ListParametrizedType listType = new ListParametrizedType(clazz);
        final List<T> images = gson.fromJson(imageData, listType);
        if (CloudImagePasswordDetails.class.isAssignableFrom(clazz)) {
            final String passwordData = params.getParameter("secure:passwords_data");
            final Map<String, String> data = gson.fromJson(passwordData, stringStringMapType);
            if (data != null) {
                for (T image : images) {
                    final CloudImagePasswordDetails userImage = (CloudImagePasswordDetails) image;
                    if (data.get(image.getSourceId()) != null) {
                        userImage.setPassword(data.get(image.getSourceId()));
                    }
                }
            }
        }

        return new ArrayList<>(images);
    }

    private static class ListParametrizedType implements ParameterizedType {

        private Type type;

        private ListParametrizedType(Type type) {
            this.type = type;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return new Type[]{type};
        }

        @Override
        public Type getRawType() {
            return ArrayList.class;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }

        // implement equals method too! (as per javadoc)
    }

    /**
     * Updates tag data.
     *
     * @param tag    original tag.
     * @param vmName virtual machine name.
     * @return updated tag.
     */
    public static CloudInstanceUserData setVmNameForTag(@NotNull final CloudInstanceUserData tag, @NotNull final String vmName) {
        return new CloudInstanceUserData(vmName,
                tag.getAuthToken(),
                tag.getServerAddress(),
                tag.getIdleTimeout(),
                tag.getProfileId(),
                tag.getProfileDescription(),
                tag.getCustomAgentConfigurationParameters());
    }
}