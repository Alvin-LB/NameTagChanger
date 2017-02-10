package com.bringholm.nametagchanger;

import org.bukkit.entity.Player;

/**
 * An interface for all the packet handler methods
 * @author AlvinB
 */
public interface IPacketHandler {
    void sendTabListRemovePacket(Player playerToRemove, Player seer);

    void sendTabListAddPacket(Player playerToAdd, String newName, Player seer);

    void sendEntityDestroyPacket(Player playerToDestroy, Player seer);

    void sendNamedEntitySpawnPacket(Player playerToSpawn, Player seer);

    void shutdown();
}
