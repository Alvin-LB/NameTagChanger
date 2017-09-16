package com.bringholm.nametagchanger;

import com.bringholm.mojangapiutil.v1_1.MojangAPIUtil;
import com.bringholm.nametagchanger.metrics.Metrics;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Maps;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Allows changing of a player's overhead name using packet manipulation
 * @author AlvinB
 */
public class NameTagChanger {
    // Access to this must be asynchronous!
    private static final LoadingCache<UUID, Skin> SKIN_CACHE = CacheBuilder.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).build(new CacheLoader<UUID, Skin>() {
        @Override
        public Skin load(UUID uuid) throws Exception {
            MojangAPIUtil.Result<MojangAPIUtil.SkinData> result = MojangAPIUtil.getSkinData(uuid);
            if (result.wasSuccessful()) {
                if (result.getValue() != null) {
                    MojangAPIUtil.SkinData data = result.getValue();
                    return new Skin(data.getUUID(), data.getBase64());
                }
            } else {
                throw result.getException();
            }
            return Skin.EMPTY_SKIN;
        }
    });

    /**
     * The version of NameTagChanger
     */
    public static final String VERSION = "1.1-SNAPSHOT";

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
     * @return an unmodifiable map containing all the changed players
     */
    public Map<UUID, String> getChangedPlayers() {
        return Collections.unmodifiableMap(this.players);
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
        ConfigurationSerialization.registerClass(Skin.class);
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
     * Gets the skin for a username.
     *
     * Since fetching this skin requires making asynchronous requests
     * to Mojang's servers, a call back mechanism using the SkinCallBack
     * class is implemented. This call back allows you to also handle
     * any errors that might have occurred while fetching the skin.
     *
     * The call back will always be fired on the main thread.
     *
     * @param username the username to get the skin of
     * @param callBack the call back to handle the result of the request
     */
    public void getSkin(String username, SkinCallBack callBack) {
        new BukkitRunnable() {
            @Override
            public void run() {
                MojangAPIUtil.Result<Map<String, MojangAPIUtil.Profile>> result = MojangAPIUtil.getUUID(Collections.singletonList(username));
                if (result.wasSuccessful()) {
                    getSkin(result.getValue().get(username).getUUID(), callBack);
                } else {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            callBack.callBack(null, false, result.getException());
                        }
                    }.runTask(plugin);
                }
            }
        }.runTaskAsynchronously(this.plugin);
    }

    /**
     * Gets the skin for a UUID.
     *
     * Since fetching this skin might require making asynchronous requests
     * to Mojang's servers, a call back mechanism using the SkinCallBack
     * class is implemented. This call back allows you to also handle
     * any errors that might have occurred while fetching the skin.
     *
     * The call back will always be fired on the main thread.
     *
     * @param uuid the uuid to get the skin of
     * @param callBack the call back to handle the result of the request
     */
    public void getSkin(UUID uuid, SkinCallBack callBack) {
        if (SKIN_CACHE.asMap().containsKey(uuid)) {
            try {
                callBack.callBack(SKIN_CACHE.get(uuid), true, null);
            } catch (ExecutionException e) {
                callBack.callBack(null, false, e);
            }
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        Skin skin = SKIN_CACHE.get(uuid);
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                callBack.callBack(skin, true, null);
                            }
                        }.runTask(plugin);
                    } catch (ExecutionException e) {
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                callBack.callBack(null, false, e);
                            }
                        }.runTask(plugin);
                    }
                }
            }.runTaskAsynchronously(this.plugin);
        }
    }

    /**
     * Empty method to call for initializing the class. May be useful
     * if you want packet/event listeners to be setup in an onEnable for example.
     */
    public void init() {

    }
}
