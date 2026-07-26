package com.example.anticheatlite;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class AntiCheatLite extends JavaPlugin {

    private VisibilityManager visibilityManager;
    private SuspiciousActivityListener suspiciousActivityListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        boolean visibilityEnabled = getConfig().getBoolean("visibility.enabled", true);
        boolean suspiciousEnabled = getConfig().getBoolean("suspicious-activity.enabled", true);

        if (visibilityEnabled) {
            visibilityManager = new VisibilityManager(this);
            visibilityManager.start();
        }

        if (suspiciousEnabled) {
            suspiciousActivityListener = new SuspiciousActivityListener(this);
            getServer().getPluginManager().registerEvents(suspiciousActivityListener, this);
            suspiciousActivityListener.start();
        }

        getLogger().info("AntiCheatLite enabled. Remember: this does NOT replace a real anticheat");
        getLogger().info("for combat/movement cheats (GrimAC/Vulcan) or Paper's built-in anti-xray.");
        getLogger().info("See config.yml for the anti-xray config snippet to paste into paper-world-defaults.yml.");
    }

    @Override
    public void onDisable() {
        if (visibilityManager != null) {
            visibilityManager.stop();
        }
        if (suspiciousActivityListener != null) {
            suspiciousActivityListener.stop();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("acl")) {
            return false;
        }

        if (args.length == 0) {
            sender.sendMessage("§7[AntiCheatLite] Usage: /acl <reload|flags|toggle>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                reloadConfig();
                sender.sendMessage("§a[AntiCheatLite] Config reloaded. Restart the plugin (or server) for");
                sender.sendMessage("§arange/threshold changes to fully take effect.");
            }
            case "flags" -> {
                if (suspiciousActivityListener == null) {
                    sender.sendMessage("§c[AntiCheatLite] Suspicious activity logging is disabled.");
                    return true;
                }
                sender.sendMessage("§7[AntiCheatLite] Recent flags:");
                suspiciousActivityListener.getRecentFlags().forEach(sender::sendMessage);
            }
            case "toggle" -> {
                if (visibilityManager != null) {
                    boolean now = visibilityManager.toggle();
                    sender.sendMessage("§7[AntiCheatLite] Distance-based visibility hiding: " + (now ? "§aON" : "§cOFF"));
                } else {
                    sender.sendMessage("§c[AntiCheatLite] Visibility manager is disabled in config.");
                }
            }
            default -> sender.sendMessage("§7[AntiCheatLite] Usage: /acl <reload|flags|toggle>");
        }
        return true;
    }
                  }
