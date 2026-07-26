package com.example.anticheatlite;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Hides players from each other's clients entirely once they exceed a
 * configured distance. This means a player cannot use freecam to "peek"
 * at another player's exact position beyond this range, because their
 * client never receives that entity's packets in the first place -
 * there's nothing to render, freecam or not.
 *
 * This is a real, enforceable limit (unlike trying to detect freecam
 * directly, which isn't possible server-side).
 */
public class VisibilityManager {

    private final AntiCheatLite plugin;
    private BukkitTask task;
    private boolean enabled = true;
    private double maxRangeSquared;
    private long intervalTicks;

    public VisibilityManager(AntiCheatLite plugin) {
        this.plugin = plugin;
        double range = plugin.getConfig().getDouble("visibility.max-visible-range", 48);
        this.maxRangeSquared = range * range;
        this.intervalTicks = plugin.getConfig().getLong("visibility.check-interval-ticks", 20);
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        // Make sure nobody is left hidden when the plugin shuts down.
        for (Player a : Bukkit.getOnlinePlayers()) {
            for (Player b : Bukkit.getOnlinePlayers()) {
                if (a != b) {
                    a.showPlayer(plugin, b);
                }
            }
        }
    }

    public boolean toggle() {
        enabled = !enabled;
        if (!enabled) {
            for (Player a : Bukkit.getOnlinePlayers()) {
                for (Player b : Bukkit.getOnlinePlayers()) {
                    if (a != b) {
                        a.showPlayer(plugin, b);
                    }
                }
            }
        }
        return enabled;
    }

    private void tick() {
        if (!enabled) {
            return;
        }

        Player[] players = Bukkit.getOnlinePlayers().toArray(new Player[0]);

        for (int i = 0; i < players.length; i++) {
            for (int j = i + 1; j < players.length; j++) {
                Player a = players[i];
                Player b = players[j];

                if (!a.getWorld().equals(b.getWorld())) {
                    // Different worlds: hide from each other, no meaningful distance check needed.
                    a.hidePlayer(plugin, b);
                    b.hidePlayer(plugin, a);
                    continue;
                }

                double distSq = a.getLocation().distanceSquared(b.getLocation());

                if (distSq > maxRangeSquared) {
                    a.hidePlayer(plugin, b);
                    b.hidePlayer(plugin, a);
                } else {
                    a.showPlayer(plugin, b);
                    b.showPlayer(plugin, a);
                }
            }
        }
    }
}
