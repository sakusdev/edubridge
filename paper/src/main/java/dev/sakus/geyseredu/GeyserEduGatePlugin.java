package dev.sakus.geyseredu;

import dev.sakus.geyseredu.auth.EmbeddedAuthService;
import dev.sakus.geyseredu.command.EduSessionCommand;
import dev.sakus.geyseredu.floodgate.FloodgateDetector;
import dev.sakus.geyseredu.listener.SessionGateListener;
import dev.sakus.geyseredu.session.SessionStore;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class GeyserEduGatePlugin extends JavaPlugin {
    private SessionStore sessionStore;
    private FloodgateDetector floodgateDetector;
    private EmbeddedAuthService embeddedAuthService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.sessionStore = new SessionStore(this);
        this.floodgateDetector = new FloodgateDetector(getLogger());
        this.embeddedAuthService = new EmbeddedAuthService(this);
        this.embeddedAuthService.startIfEnabled();

        var command = new EduSessionCommand(this, sessionStore);
        PluginCommand pluginCommand = getCommand("edu-session");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        getServer().getPluginManager().registerEvents(
            new SessionGateListener(this, sessionStore, floodgateDetector, embeddedAuthService),
            this
        );

        getLogger().info("GeyserEduGate enabled.");
    }

    @Override
    public void onDisable() {
        if (sessionStore != null) {
            sessionStore.save();
        }
        if (embeddedAuthService != null) {
            embeddedAuthService.stop();
        }
    }

    public SessionStore sessionStore() {
        return sessionStore;
    }

    public void reloadPluginConfig() {
        reloadConfig();
        sessionStore.reload();
    }
}
