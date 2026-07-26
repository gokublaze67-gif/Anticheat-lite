package com.example.anticheatlite;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IMPORTANT: This is a heuristic, not a detector. Freecam itself is
 * invisible to the server. What this tracks is a *pattern* that's common
 * when someone tabs into freecam, looks around, then tabs back and walks
 * straight to what they saw: their body stops moving and stops rotating
 * for a while (because they're not touching their character's controls),
 * then shortly after, they beeline toward a player or spot that was far
 * outside their normal awareness range.
 *
 * False positives are expected (AFK players, alt-tabbing to read chat,
 * players with genuinely good map/sound awareness, etc). Treat flags as
 * "worth a staff look", never as an auto-ban trigger.
 */
public class SuspiciousActivityListener implements Listener {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final AntiCheatLite plugin;
    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();
    private final Deque<String> recentFlags = new ArrayDeque<>();

    private final long frozenTicks;
    private final long beelineWindowTicks;
    private final double beelineMinDistance;
    private final boolean logToConsole;

    private BukkitTask cleanupTask;

    public SuspiciousActivityListener(AntiCheatLite plugin) {
        this.plugin = plugin;
        this.frozenTicks = plugin.getConfig().getLong("suspicious-activity.frozen-rotation-ticks", 100);
        this.beelineWindowTicks = plugin.getConfig().getLong("suspicious-activity.beeline-window-ticks", 200);
        this.beelineMinDistance = plugin.getConfig().getDouble("suspicious-activity.beeline-min-distance", 40);
        this.logToConsole = plugin.getConfig().getBoolean("suspicious-activity.log-to-console", true);
    }

    public void start() {
        // Periodic tick counter to age out frozen-state tracking without relying on event timing alone.
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::ageStates, 20L, 20L);
    }

    public void stop() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        states.clear();
    }

    public Deque<String> getRecentFlags() {
        return recentFlags;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;

        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        Location from = event.getFrom();
        Location to = event.getTo();

        PlayerState state = states.computeIfAbsent(id, k -> new PlayerState(to));

        boolean positionChanged = from.distanceSquared(to) > 0.0001;
        boolean rotationChanged = Math.abs(from.getYaw() - to.getYaw()) > 1.0f
                || Math.abs(from.getPitch() - to.getPitch()) > 1.0f;

        if (!positionChanged && !rotationChanged) {
            state.stillTicks++;
        } else {
            // Movement resumed. If they'd been frozen long enough, start watching for a beeline.
            if (state.stillTicks >= frozenTicks && !state.watching) {
                state.watching = true;
                state.watchStartLocation = from.clone();
                state.watchTicksRemaining = beelineWindowTicks;
            }
            state.stillTicks = 0;
        }

        if (state.watching) {
            state.watchTicksRemaining--;
            double traveled = state.watchStartLocation.distance(to);

            if (traveled >= beelineMinDistance) {
                flag(player, traveled);
                state.watching = false;
            } else if (state.watchTicksRemaining <= 0) {
                state.watching = false;
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
    }

    private void ageStates() {
        // No-op tick hook reserved for future use (e.g. expiring stale entries
        // for players who logged off mid-window). Kept lightweight intentionally.
    }

    private void flag(Player player, double traveled) {
        String time = LocalDateTime.now().format(TIME_FMT);
        String message = String.format(
                "[AntiCheatLite] [%s] %s moved %.1f blocks shortly after a long idle period (possible freecam scouting - staff review suggested, not proof)",
                time, player.getName(), traveled
        );

        if (recentFlags.size() >= 50) {
            recentFlags.removeFirst();
        }
        recentFlags.addLast(message);

        if (logToConsole) {
            plugin.getLogger().info(message);
        }

        String permission = plugin.getConfig().getString("suspicious-activity.notify-staff-permission", "anticheatlite.notify");
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(permission)) {
                staff.sendMessage("§e" + message);
            }
        }
    }

    private static class PlayerState {
        long stillTicks = 0;
        boolean watching = false;
        long watchTicksRemaining = 0;
        Location watchStartLocation;

        PlayerState(Location initial) {
            this.watchStartLocation = initial.clone();
        }
    }
}
