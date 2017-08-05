package com.bringholm.nametagchanger;

import com.bringholm.nametagchanger.metrics.Metrics;
import com.google.common.collect.Maps;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Allows changing of a player's overhead name using packet manipulation
 * @author AlvinB
 */
public class NameTagChanger {

    public static final String VERSION = "1.0-SNAPSHOT";

    /**
     * The singleton instance to access all NameTagChanger methods
     */
    public static final NameTagChanger INSTANCE = new NameTagChanger();

    private IPacketHandler packetHandler;
    HashMap<UUID, String> players = Maps.newHashMap();
    /**
     * The plugin to assign packet/event listeners to
     */
    private Plugin plugin;
    private boolean enabled;

    /**
     * Enables the packet handler as well as trying to find the appropriate plugin to use. The plugin
     * instance can be changed using setPlugin(Plugin)
     */
    private NameTagChanger() {
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            if (plugin.getClass().getProtectionDomain().getCodeSource().equals(this.getClass().getProtectionDomain().getCodeSource())) {
                this.plugin = plugin;
            }
        }
        enable();
    }

    /**
     * Changes the name displayed above the player's head. Please note that these names
     * must follow the regular rules of minecraft names (ie maximum 16 characters)
     * @param player the player
     * @param newName the new name of the player
     */
    public void changePlayerName(Player player, String newName) {
        Validate.isTrue(enabled, "NameTagChanger is disabled");
        Validate.notNull(player, "player cannot be null");
        Validate.notNull(newName, "newName cannot be null");
        Validate.isTrue(newName.length() <= 16, "newName cannot be longer than 16 characters!");
        players.put(player.getUniqueId(), newName);
        for (Player otherPlayer : Bukkit.getOnlinePlayers()) {
            if (otherPlayer.equals(player)) {
                continue;
            }
            if (otherPlayer.canSee(player)) {
                packetHandler.sendTabListRemovePacket(player, otherPlayer);
                packetHandler.sendTabListAddPacket(player, newName, otherPlayer);
                if (otherPlayer.getWorld().equals(player.getWorld())) {
                    packetHandler.sendEntityDestroyPacket(player, otherPlayer);
                    packetHandler.sendNamedEntitySpawnPacket(player, otherPlayer);
                }
            }
        }
    }

    /**
     * Resets the player's name back to normal.
     * @param player the player
     */
    public void resetPlayerName(Player player) {
        Validate.isTrue(enabled, "NameTagChanger is disabled");
        if (player == null || !players.containsKey(player.getUniqueId())) {
            return;
        }
        players.remove(player.getUniqueId());
        for (Player otherPlayer : Bukkit.getOnlinePlayers()) {
            if (otherPlayer.equals(player)) {
                continue;
            }
            if (otherPlayer.canSee(player)) {
                packetHandler.sendTabListRemovePacket(player, otherPlayer);
                packetHandler.sendTabListAddPacket(player, player.getName(), otherPlayer);
                if (otherPlayer.getWorld().equals(player.getWorld())) {
                    packetHandler.sendEntityDestroyPacket(player, otherPlayer);
                    packetHandler.sendNamedEntitySpawnPacket(player, otherPlayer);
                }
            }
        }
    }

    /**
     * Gets all players who currently have changed names.
     * @return a map containing all the changed players
     */
    public Map<UUID, String> getChangedPlayers() {
        return new HashMap<>(this.players);
    }

    /**
     * Checks if NameTagChanger is enabled.
     * @return whether NameTagChanger is enabled or not.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Disables NameTagChanger and restores all names to normal,
     * and removes all packet handlers.
     */
    public void disable() {
        Validate.isTrue(enabled, "NameTagChanger is already disabled");
        for (UUID uuid : players.keySet()) {
            resetPlayerName(Bukkit.getPlayer(uuid));
        }
        players.clear();
        packetHandler.shutdown();
        packetHandler = null;
        enabled = false;
    }

    /**
     * Enables NameTagChanger and creates necessary packet handlers.
     * Is done automatically by the constructor, so only use this method
     * if the disable() method has previously been called.
     */
    public void enable() {
        if (plugin == null) {
            return;
        }
        Validate.isTrue(!enabled, "NameTagChanger is already enabled");
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") != null) {
            packetHandler = new ProtocolLibPacketHandler(plugin);
        } else {
            packetHandler = new ChannelPacketHandler(plugin);
        }
        enabled = true;
        Metrics metrics = new Metrics(plugin);
        metrics.addCustomChart(new Metrics.SimplePie("packet_implementation", () -> packetHandler instanceof ProtocolLibPacketHandler ? "ProtocolLib" : "ChannelInjector"));
    }

    /**
     * Sets the plugin instance to use for registering packet/event listeners.
     * This is done automatically by the constructor, so this should only be used
     * if the correct plugin is not found.
     * @param plugin the plugin instance
     */
    public void setPlugin(Plugin plugin) {
        Validate.notNull(plugin, "plugin cannot be null");
        this.plugin = plugin;
    }

    /**
     * Empty method to call for initializing the class. May be useful
     * if you want packet/event listeners to be setup in an onEnable for example.
     */
    public void init() {

    }
}
