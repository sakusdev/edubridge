package dev.sakus.geyseredu.fabric;

import java.lang.reflect.Method;
import java.util.UUID;

public final class FabricFloodgateDetector {
    private Object floodgateApi;
    private Method isFloodgatePlayer;
    private boolean initialized;

    public boolean isFloodgatePlayer(UUID uuid) {
        initialize();
        if (floodgateApi == null || isFloodgatePlayer == null) {
            return false;
        }

        try {
            Object result = isFloodgatePlayer.invoke(floodgateApi, uuid);
            return result instanceof Boolean && (Boolean) result;
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    private void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Method getInstance = apiClass.getMethod("getInstance");
            floodgateApi = getInstance.invoke(null);
            isFloodgatePlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            floodgateApi = null;
            isFloodgatePlayer = null;
        }
    }
}
