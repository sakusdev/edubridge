package dev.sakus.geyseredu.floodgate;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

public final class FloodgateDetector {
    private final Logger logger;
    private Object floodgateApi;
    private Method isFloodgatePlayer;
    private boolean initialized;

    public FloodgateDetector(Logger logger) {
        this.logger = logger;
    }

    public boolean isFloodgatePlayer(UUID uuid) {
        initialize();
        if (floodgateApi == null || isFloodgatePlayer == null) {
            return false;
        }

        try {
            Object result = isFloodgatePlayer.invoke(floodgateApi, uuid);
            return result instanceof Boolean && (Boolean) result;
        } catch (ReflectiveOperationException ex) {
            logger.warning("Failed to query Floodgate API: " + ex.getMessage());
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
            logger.info("Floodgate API detected.");
        } catch (ReflectiveOperationException | LinkageError ex) {
            logger.info("Floodgate API was not detected. Session checks will use username policy only.");
        }
    }
}
