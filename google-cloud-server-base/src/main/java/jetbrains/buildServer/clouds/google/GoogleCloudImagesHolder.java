

package jetbrains.buildServer.clouds.google;

import jetbrains.buildServer.clouds.base.AbstractCloudImage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores info about cloud images and instances
 */
public class GoogleCloudImagesHolder {
  private final Map<String, AbstractCloudImage> myImages = new ConcurrentHashMap<>();

  @Nullable
  public AbstractCloudImage findImage(@NotNull final String profileId, @NotNull final String sourceId) {
    final String key = getKey(profileId, sourceId);
    return myImages.get(key);
  }

  public void addImage(@NotNull String profileId, @NotNull AbstractCloudImage image) {
    final String key = getKey(profileId, image.getId());
    myImages.put(key, image);
  }

  private static String getKey(@NotNull String profileId, @NotNull String sourceId) {
    return String.format("%s-%s", profileId, sourceId);
  }
}