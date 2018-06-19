package com.bringholm.nametagchanger;

import com.bringholm.mojangapiutil.v1_2.MojangAPIUtil;
import com.bringholm.nametagchanger.metrics.Metrics;
import com.bringholm.nametagchanger.skin.Skin;
import com.bringholm.nametagchanger.skin.SkinCallBack;
import com.bringholm.reflectutil.v1_1_1.ReflectUtil;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Team;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Allows changing of a player's overhead name using packet manipulation
 *
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
                    if (data.getSkinURL() == null && data.getCapeURL() == null) {
                        return Skin.EMPTY_SKIN;
                    }
                    return new Skin(data.getUUID(), data.getBase64(), data.getSignedBase64());
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

    boolean sendingPackets;
    private IPacketHandler packetHandler;
    HashMap<UUID, GameProfileWrapper> gameProfiles = Maps.newHashMap();
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
     * Sets a players skin.
     * <p>
     * NOTE: This does not update the player's skin, so a call to
     * updatePlayer() is needed for the changes to take effect.
     *
     * @param player the player to set the skin of
     * @param skin   the skin
     */
    public void setPlayerSkin(Player player, Skin skin) {
        Validate.isTrue(enabled, "NameTagChanger is disabled");
        Validate.notNull(player, "player cannot be null");
        Validate.notNull(skin, "skin cannot be null");
        Validate.isTrue(!skin.equals(getDefaultSkinFromPlayer(player)), "Skin cannot be the default skin of the player! If you intended to reset the skin, use resetPlayerSkin() instead.");
        GameProfileWrapper profile = gameProfiles.get(player.getUniqueId());
        if (profile == null) {
            profile = packetHandler.getDefaultPlayerProfile(player);
        }
        profile.getProperties().removeAll("textures");
        if (skin != Skin.EMPTY_SKIN) {
            profile.getProperties().put("textures", new GameProfileWrapper.PropertyWrapper("textures", skin.getBase64(), skin.getSignedBase64()));
        }
        gameProfiles.put(player.getUniqueId(), profile);
    }

    /**
     * Resets a player's skin back to normal
     * <p>
     * NOTE: This does not update the player's skin, so a call to
     * updatePlayer() is needed for the changes to take effect.
     *
     * @param player the player to reset
     */
    public void resetPlayerSkin(Player player) {
        Validate.isTrue(enabled, "NameTagChanger is disabled");
        if (player == null || !gameProfiles.containsKey(player.getUniqueId())) {
            return;
        }
        GameProfileWrapper profile = gameProfiles.get(player.getUniqueId());
        profile.getProperties().removeAll("textures");
        GameProfileWrapper defaultProfile = packetHandler.getDefaultPlayerProfile(player);
        if (defaultProfile.getProperties().containsKey("textures")) {
            profile.getProperties().putAll("textures", defaultProfile.getProperties().get("textures"));
        }
        checkForRemoval(player);
    }

    /**
     * Changes the name displayed above the player's head. Please note that these names
     * must follow the regular rules of minecraft names (ie maximum 16 characters)
     *
     * @param player  the player
     * @param newName the new name of the player
     */
    public void changePlayerName(Player player, String newName) {
        Validate.isTrue(enabled, "NameTagChanger is disabled");
        Validate.notNull(player, "player cannot be null");
        Validate.notNull(newName, "newName cannot be null");
        Validate.isTrue(!newName.equals(player.getName()), "The new name cannot be the same of the player's! If you intended to reset the player's name, use resetPlayerName()!");
        Validate.isTrue(newName.length() <= 16, "newName cannot be longer than 16 characters!");
        GameProfileWrapper profile = new GameProfileWrapper(player.getUniqueId(), newName);
        if (gameProfiles.containsKey(player.getUniqueId())) {
            profile.getProperties().putAll(gameProfiles.get(player.getUniqueId()).getProperties());
        } else {
            // If the player doesn't already have a skin specified, make sure to carry over their default one.
            profile.getProperties().putAll(packetHandler.getDefaultPlayerProfile(player).getProperties());
        }
        gameProfiles.put(player.getUniqueId(), profile);
        updatePlayer(player, player.getName());
    }

    /**
     * Resets the player's name back to normal.
     *
     * @param player the player
     */
    public void resetPlayerName(Player player) {
        Validate.isTrue(enabled, "NameTagChanger is disabled");
        if (player == null || !gameProfiles.containsKey(player.getUniqueId())) {
            return;
        }
        GameProfileWrapper oldProfile = gameProfiles.get(player.getUniqueId());
        GameProfileWrapper newProfile = packetHandler.getDefaultPlayerProfile(player);
        newProfile.getProperties().removeAll("textures");
        if (oldProfile.getProperties().containsKey("textures")) {
            newProfile.getProperties().putAll("textures", oldProfile.getProperties().get("textures"));
        }
        gameProfiles.put(player.getUniqueId(), newProfile);
        updatePlayer(player, oldProfile.getName());
        checkForRemoval(player);
    }

    private void checkForRemoval(Player player) {
        if (gameProfiles.get(player.getUniqueId()).equals(packetHandler.getDefaultPlayerProfile(player))) {
            gameProfiles.remove(player.getUniqueId());
        }
    }

    /**
     * Gets a player's changed name
     *
     * @param player the player to get the changed name of
     * @return the changed name
     */
    public String getChangedName(Player player) {
        Validate.isTrue(enabled, "NameTagChanger is disabled");
        GameProfileWrapper profile = gameProfiles.get(player.getUniqueId());
        // If the name is the same as the original, this means that it is not changed, and
        // probably that a skin is present.
        return (profile == null || profile.getName().equals(player.getName()) ? null : profile.getName());
    }

    /**
     * Gets a player's changed skin
     *
     * @param player the player to get the changed skin of
     * @return the changed skin
     */
    public Skin getChangedSkin(Player player) {
        Validate.isTrue(enabled, "NameTagChanger is disabled");
        GameProfileWrapper profile = gameProfiles.get(player.getUniqueId());
        if (profile == null) {
            return null;
        }
        Skin skin = getSkinFromGameProfile(profile);
        if (skin.equals(getDefaultSkinFromPlayer(player))) {
            // The skin is the normal skin for the player,
            // meaning there is no changed skin
            return null;
        } else {
            return skin;
        }
    }

    /**
     * Gets the default skin from a player, if one exists locally, otherwise Skin.EMPTY_SKIN is returned.
     *
     * @param player the player
     * @return the player's default skin
     */
    public Skin getDefaultSkinFromPlayer(Player player) {
        return getSkinFromGameProfile(packetHandler.getDefaultPlayerProfile(player));
    }

    /**
     * Gets the skin from a game profile, if one exists, otherwise Skin.EMPTY_SKIN is returned.
     *
     * @param profile the profile
     * @return the skin of the profile
     */
    public Skin getSkinFromGameProfile(GameProfileWrapper profile) {
        Validate.isTrue(enabled, "NameTagChanger is disabled");
        Validate.notNull(profile, "profile cannot be null");
        if (profile.getProperties().containsKey("textures")) {
            GameProfileWrapper.PropertyWrapper property = Iterables.getFirst(profile.getProperties().get("textures"), null);
            if (property == null) {
                return Skin.EMPTY_SKIN;
            } else {
                return new Skin(profile.getUUID(), property.getValue(), property.getSignature());
            }
        } else {
            return Skin.EMPTY_SKIN;
        }
    }

    /**
     * Sends packets to update a player to reflect any changes in skin or
     * name.
     * <p>
     * NOTE: This is done automatically by #changePlayerName and #resetPlayerName,
     * so the only real use for this is when changing skins, as those methods
     * do not automatically update.
     *
     * @param player the player to update
     */
    public void updatePlayer(Player player) {
        updatePlayer(player, null);
    }

    private void updatePlayer(Player player, String oldName) {
        Validate.isTrue(enabled, "NameTagChanger is disabled");
        GameProfileWrapper newProfile = gameProfiles.get(player.getUniqueId());
        if (newProfile == null) {
            newProfile = packetHandler.getDefaultPlayerProfile(player);
        }
        List<Team> scoreboardTeamsToUpdate = Lists.newArrayList();
        sendingPackets = true;
        for (Player otherPlayer : Bukkit.getOnlinePlayers()) {
            if (otherPlayer.equals(player)) {
                if (otherPlayer.getScoreboard().getEntryTeam(player.getName()) != null) {
                    scoreboardTeamsToUpdate.add(otherPlayer.getScoreboard().getEntryTeam(player.getName()));
                }
                continue;
            }
            if (otherPlayer.canSee(player)) {
                packetHandler.sendTabListRemovePacket(player, otherPlayer);
                packetHandler.sendTabListAddPacket(player, newProfile, otherPlayer);
                if (otherPlayer.getWorld().equals(player.getWorld())) {
                    packetHandler.sendEntityDestroyPacket(player, otherPlayer);
                    packetHandler.sendNamedEntitySpawnPacket(player, otherPlayer);
                }
            }
            // The player we want to rename is in a scoreboard team.
            if (otherPlayer.getScoreboard().getEntryTeam(player.getName()) != null) {
                scoreboardTeamsToUpdate.add(otherPlayer.getScoreboard().getEntryTeam(player.getName()));
            }
        }
        if (oldName != null) {
            String newName = newProfile.getName();
            for (Team team : scoreboardTeamsToUpdate) {
                Bukkit.getOnlinePlayers().stream().filter(p -> p.getScoreboard() == team.getScoreboard()).forEach(p -> {
                    packetHandler.sendScoreboardRemovePacket(oldName, p, team.getName());
                    packetHandler.sendScoreboardAddPacket(newName, p, team.getName());
                });
            }
        }
        sendingPackets = false;
    }

    /**
     * Gets all players who currently have changed names.
     *
     * @return an unmodifiable map containing all the changed players
     */
    public Map<UUID, String> getChangedPlayers() {
        Map<UUID, String> changedPlayers = Maps.newHashMap();
        for (Map.Entry<UUID, GameProfileWrapper> entry : this.gameProfiles.entrySet()) {
            changedPlayers.put(entry.getKey(), entry.getValue().getName());
        }
        return Collections.unmodifiableMap(changedPlayers);
    }

    /**
     * Checks if NameTagChanger is enabled.
     *
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
        
        // Prevent ConcurrentModificationException by wrapping keySet in a list
        List<UUID> playerUuids = new ArrayList<UUID>(gameProfiles.keySet());
        for (UUID uuid : playerUuids) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            resetPlayerName(player);
            resetPlayerSkin(player);
        }
        gameProfiles.clear();
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
        if (!ReflectUtil.isVersionHigherThan(1, 7, 10)) {
            printMessage("NameTagChanger has detected that you are running 1.7 or lower. This probably means that NameTagChanger will not work or throw errors, but you are still free to try and use it.\nIf you are not a developer, please consider contacting the developer of " + plugin.getName() + " and informing them about this message.");
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

    void printMessage(String message) {
        System.out.println("[NameTagChanger] " + message);
    }

    /**
     * Sets the plugin instance to use for registering packet/event listeners.
     * This is done automatically by the constructor, so this should only be used
     * if the correct plugin is not found.
     *
     * @param plugin the plugin instance
     */
    public void setPlugin(Plugin plugin) {
        Validate.notNull(plugin, "plugin cannot be null");
        this.plugin = plugin;
    }

    /**
     * Gets the skin for a username.
     * <p>
     * Since fetching this skin requires making asynchronous requests
     * to Mojang's servers, a call back mechanism using the SkinCallBack
     * class is implemented. This call back allows you to also handle
     * any errors that might have occurred while fetching the skin.
     * If no users with the specified username can be found, the
     * skin passed to the callback will be Skin.EMPTY_SKIN.
     * <p>
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
                    if (result.getValue() == null || result.getValue().isEmpty()) {
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                callBack.callBack(Skin.EMPTY_SKIN, true, null);
                            }
                        }.runTask(plugin);
                        return;
                    }
                    for (Map.Entry<String, MojangAPIUtil.Profile> entry : result.getValue().entrySet()) {
                        if (entry.getKey().equalsIgnoreCase(username)) {
                            getSkin(entry.getValue().getUUID(), callBack);
                            return;
                        }
                    }
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
     * <p>
     * Since fetching this skin might require making asynchronous requests
     * to Mojang's servers, a call back mechanism using the SkinCallBack
     * class is implemented. This call back allows you to also handle
     * any errors that might have occurred while fetching the skin.
     * <p>
     * The call back will always be fired on the main thread.
     *
     * @param uuid     the uuid to get the skin of
     * @param callBack the call back to handle the result of the request
     */
    public void getSkin(UUID uuid, SkinCallBack callBack) {
        Map<UUID, Skin> asMap = SKIN_CACHE.asMap();
        if (asMap.containsKey(uuid)) {
            callBack.callBack(asMap.get(uuid), true, null);
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
