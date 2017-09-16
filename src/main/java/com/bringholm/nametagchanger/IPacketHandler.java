package com.bringholm.nametagchanger;

import org.bukkit.entity.Player;

/**
 * An interface for all the packet handler methods
 * @author AlvinB
 */
public interface IPacketHandler {
    void sendTabListRemovePacket(Player playerToRemove, Player seer);

    void sendTabListAddPacket(Player playerToAdd, GameProfileWrapper newProfile, Player seer);

    void sendEntityDestroyPacket(Player playerToDestroy, Player seer);

    void sendNamedEntitySpawnPacket(Player playerToSpawn, Player seer);

    GameProfileWrapper getDefaultPlayerProfile(Player player);

    void shutdown();
}
