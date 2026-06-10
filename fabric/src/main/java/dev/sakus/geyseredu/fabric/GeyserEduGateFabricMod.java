package dev.sakus.geyseredu.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.InvocationTargetException;

public final class GeyserEduGateFabricMod implements ModInitializer {
    @Override
    public void onInitialize() {
        try {
            Class<?> runtimeClass = Class.forName("dev.sakus.geyseredu.fabric.GeyserEduGateFabricRuntime");
            Object runtime = runtimeClass.getConstructor().newInstance();
            runtimeClass.getMethod("initialize").invoke(runtime);
        } catch (InvocationTargetException ex) {
            handleStartupFailure(ex.getCause());
        } catch (Throwable ex) {
            handleStartupFailure(ex);
        }
    }

    private static void handleStartupFailure(Throwable failure) {
        if (isMinecraftLinkageFailure(failure)) {
            System.err.println("[GeyserEdu] Fabric runtime was not enabled because this Minecraft runtime does not expose the 1.21.4 Fabric/Yarn classes used by the mod.");
            System.err.println("[GeyserEdu] Detected Minecraft version: " + FabricLoader.getInstance().getModContainer("minecraft")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown"));
            logRuntimeProbe();
            System.err.println("[GeyserEdu] Use the Paper plugin path for now, or provide the full Education Fabric mappings/logs so an Education-specific adapter can be built.");
            System.err.println("[GeyserEdu] Startup compatibility detail: " + failure);
            return;
        }

        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException("Failed to initialize Geyser Edu Gate Fabric runtime", failure);
    }

    private static boolean isMinecraftLinkageFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof NoClassDefFoundError || current instanceof ClassNotFoundException) {
                String message = current.getMessage();
                if (message != null && (message.startsWith("net/minecraft/") || message.startsWith("net.minecraft."))) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static void logRuntimeProbe() {
        System.err.println("[GeyserEdu] Runtime probe:");
        probe("text yarn/intermediary", "net.minecraft.class_2561");
        probe("text named", "net.minecraft.text.Text");
        probe("text official", "net.minecraft.network.chat.Component");
        probe("server player intermediary", "net.minecraft.class_3222");
        probe("server player named", "net.minecraft.server.network.ServerPlayerEntity");
        probe("server player official", "net.minecraft.server.level.ServerPlayer");
        probe("server command source intermediary", "net.minecraft.class_2168");
        probe("server command source named", "net.minecraft.server.command.ServerCommandSource");
        probe("server command source official", "net.minecraft.commands.CommandSourceStack");
        probe("command manager intermediary", "net.minecraft.class_2170");
        probe("command manager named", "net.minecraft.server.command.CommandManager");
        probe("command manager official", "net.minecraft.commands.Commands");
        probe("minecraft server named", "net.minecraft.server.MinecraftServer");
        probe("fabric command callback", "net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback");
        probe("fabric message events", "net.fabricmc.fabric.api.message.v1.ServerMessageEvents");
        probe("fabric connection events", "net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents");
    }

    private static void probe(String label, String className) {
        try {
            Class.forName(className, false, GeyserEduGateFabricMod.class.getClassLoader());
            System.err.println("[GeyserEdu]   present: " + label + " -> " + className);
        } catch (Throwable ignored) {
            System.err.println("[GeyserEdu]   missing: " + label + " -> " + className);
        }
    }
}
