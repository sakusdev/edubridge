package dev.sakus.geyseredu.command;

import dev.sakus.geyseredu.GeyserEduGatePlugin;
import dev.sakus.geyseredu.session.EduSession;
import dev.sakus.geyseredu.session.SessionStore;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class EduSessionCommand implements CommandExecutor, TabCompleter {
    private final GeyserEduGatePlugin plugin;
    private final SessionStore sessionStore;

    public EduSessionCommand(GeyserEduGatePlugin plugin, SessionStore sessionStore) {
        this.plugin = plugin;
        this.sessionStore = sessionStore;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "issue" -> issue(sender, label, args);
            case "revoke" -> revoke(sender, label, args);
            case "list" -> list(sender);
            case "reload" -> reload(sender);
            default -> {
                sendUsage(sender, label);
                yield true;
            }
        };
    }

    private boolean issue(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("geyseredu.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to issue sessions.");
            return true;
        }
        if (!plugin.getConfig().getBoolean("local-session-issuer.enabled", false)) {
            sender.sendMessage(prefix() + ChatColor.RED + " ローカルセッション発行は無効です。");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " issue <tenantId> [ttlMinutes]");
            return true;
        }

        long ttlMinutes = plugin.getConfig().getLong("sessions.default-ttl-minutes", 60);
        if (args.length >= 3) {
            try {
                ttlMinutes = Long.parseLong(args[2]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(ChatColor.RED + "ttlMinutes must be a number.");
                return true;
            }
        }
        if (ttlMinutes <= 0) {
            sender.sendMessage(ChatColor.RED + "ttlMinutes must be greater than zero.");
            return true;
        }
        if (!isTenantAllowed(args[1])) {
            sender.sendMessage(prefix() + ChatColor.RED + " このテナントは許可されていません。config.yml の allowed-tenants を確認してください。");
            return true;
        }

        try {
            EduSession session = sessionStore.issue(args[1], sender.getName(), ttlMinutes);
            sender.sendMessage(prefix() + ChatColor.GREEN + " 参加IDを発行しました: " + session.id());
            sender.sendMessage(prefix() + ChatColor.GRAY + " 有効期限: " + session.expiresAt());
        } catch (IllegalStateException ex) {
            sender.sendMessage(prefix() + ChatColor.RED + " " + ex.getMessage());
        }
        return true;
    }

    private boolean revoke(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("geyseredu.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to revoke sessions.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " revoke <participationId>");
            return true;
        }

        if (sessionStore.revoke(args[1])) {
            sender.sendMessage(prefix() + ChatColor.GREEN + " 参加IDを失効しました。");
        } else {
            sender.sendMessage(prefix() + ChatColor.RED + " 参加IDが見つかりません。");
        }
        return true;
    }

    private boolean list(CommandSender sender) {
        if (!sender.hasPermission("geyseredu.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to list sessions.");
            return true;
        }

        List<EduSession> sessions = new ArrayList<>(sessionStore.activeSessions());
        if (sessions.isEmpty()) {
            sender.sendMessage(prefix() + ChatColor.GRAY + " 有効な参加IDはありません。");
            return true;
        }

        sender.sendMessage(prefix() + ChatColor.GRAY + " 有効な参加ID:");
        for (EduSession session : sessions) {
            long remainingMinutes = Math.max(0, Duration.between(Instant.now(), session.expiresAt()).toMinutes());
            sender.sendMessage(ChatColor.GRAY + "- " + session.id()
                + " tenant=" + session.tenantId()
                + " users=" + session.claimedPlayers().size()
                + " remaining=" + remainingMinutes + "m");
        }
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("geyseredu.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to reload this plugin.");
            return true;
        }

        plugin.reloadPluginConfig();
        sender.sendMessage(prefix() + ChatColor.GREEN + " 設定を再読み込みしました。");
        return true;
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.GRAY + "Usage:");
        if (sender.hasPermission("geyseredu.admin")) {
            sender.sendMessage(ChatColor.GRAY + "/" + label + " issue <tenantId> [ttlMinutes]");
            sender.sendMessage(ChatColor.GRAY + "/" + label + " revoke <participationId>");
            sender.sendMessage(ChatColor.GRAY + "/" + label + " list");
            sender.sendMessage(ChatColor.GRAY + "/" + label + " reload");
        }
    }

    private String prefix() {
        return plugin.getConfig().getString("message-prefix", "[GeyserEdu]");
    }

    private boolean isTenantAllowed(String tenantId) {
        List<String> allowedTenants = plugin.getConfig().getStringList("allowed-tenants");
        if (allowedTenants.isEmpty()) {
            return true;
        }

        String normalizedTenantId = tenantId.toLowerCase(Locale.ROOT);
        return allowedTenants.stream()
            .map(value -> value.toLowerCase(Locale.ROOT))
            .anyMatch(normalizedTenantId::equals);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (sender.hasPermission("geyseredu.admin")) {
                options.add("issue");
                options.add("revoke");
                options.add("list");
                options.add("reload");
            }
            return options.stream().filter(option -> option.startsWith(args[0].toLowerCase())).toList();
        }
        return List.of();
    }
}
