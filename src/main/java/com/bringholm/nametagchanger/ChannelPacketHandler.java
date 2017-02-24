package com.bringholm.nametagchanger;

import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The non-ProtocolLib implementation of the packet handler.
 * @author AlvinB
 */
@SuppressWarnings("unchecked")
public class ChannelPacketHandler extends PacketInterceptor implements IPacketHandler {

    private static final Class<?> PLAYER_INFO_DATA_CLASS = ReflectUtil.getNMSClass("PacketPlayOutPlayerInfo$PlayerInfoData").getOrThrow();
    private static final Field PLAYER_DATA_LIST = ReflectUtil.getDeclaredFieldByType(ReflectUtil.getNMSClass("PacketPlayOutPlayerInfo").getOrThrow(), List.class, 0, true).getOrThrow();
    private static final Method GET_GAME_PROFILE = ReflectUtil.getMethodByType(PLAYER_INFO_DATA_CLASS, GameProfile.class, 0).getOrThrow();
    private static final Constructor<?> PLAYER_INFO_DATA_CONSTRUCTOR = ReflectUtil.getConstructor(PLAYER_INFO_DATA_CLASS, ReflectUtil.getNMSClass("PacketPlayOutPlayerInfo").getOrThrow(), GameProfile.class,
            int.class, ReflectUtil.getNMSClass("EnumGamemode").getOrThrow(), ReflectUtil.getNMSClass("IChatBaseComponent").getOrThrow()).getOrThrow();
    private static final Method GET_LATENCY = ReflectUtil.getMethodByType(PLAYER_INFO_DATA_CLASS, int.class, 0).getOrThrow();
    private static final Method GET_GAMEMODE = ReflectUtil.getMethodByType(PLAYER_INFO_DATA_CLASS, ReflectUtil.getNMSClass("EnumGamemode").getOrThrow(), 0).getOrThrow();
    private static final Method GET_DISPLAY_NAME = ReflectUtil.getMethodByType(PLAYER_INFO_DATA_CLASS, ReflectUtil.getNMSClass("IChatBaseComponent").getOrThrow(), 0).getOrThrow();

    private static final Class<?> ENTITY_PLAYER = ReflectUtil.getNMSClass("EntityPlayer").getOrThrow();
    private static final Constructor<?> PACKET_PLAYER_INFO_CONSTRUCTOR = ReflectUtil.getConstructor(ReflectUtil.getNMSClass("PacketPlayOutPlayerInfo").getOrThrow(),
            ReflectUtil.getNMSClass("PacketPlayOutPlayerInfo$EnumPlayerInfoAction").getOrThrow(), Array.newInstance(ENTITY_PLAYER, 0).getClass()).getOrThrow();
    private static final Object REMOVE_PLAYER_CONSTANT = ReflectUtil.getEnumConstant(ReflectUtil.getNMSClass("PacketPlayOutPlayerInfo$EnumPlayerInfoAction").getOrThrow(), "REMOVE_PLAYER").getOrThrow();
    private static final Method GET_HANDLE = ReflectUtil.getMethod(ReflectUtil.getCBClass("entity.CraftPlayer").getOrThrow(), "getHandle").getOrThrow();
    private static final Field PLAYER_CONNECTION = ReflectUtil.getFieldByType(ENTITY_PLAYER, ReflectUtil.getNMSClass("PlayerConnection").getOrThrow(), 0).getOrThrow();
    private static final Method SEND_PACKET = ReflectUtil.getMethod(ReflectUtil.getNMSClass("PlayerConnection").getOrThrow(), "sendPacket", ReflectUtil.getNMSClass("Packet").getOrThrow()).getOrThrow();

    private static final Constructor<?> PACKET_PLAYER_INFO_CONSTRUCTOR_EMPTY = ReflectUtil.getConstructor(ReflectUtil.getNMSClass("PacketPlayOutPlayerInfo").getOrThrow()).getOrThrow();
    private static final Field PING = ReflectUtil.getField(ENTITY_PLAYER, "ping").getOrThrow();
    private static final Method GET_BY_ID = ReflectUtil.getMethod(ReflectUtil.getNMSClass("EnumGamemode").getOrThrow(), "getById", int.class).getOrThrow();
    private static final Constructor<?> CHAT_COMPONENT_TEXT_CONSTRUCTOR = ReflectUtil.getConstructor(ReflectUtil.getNMSClass("ChatComponentText").getOrThrow(), String.class).getOrThrow();
    private static final Field PLAYER_INFO_ACTION = ReflectUtil.getDeclaredFieldByType(ReflectUtil.getNMSClass("PacketPlayOutPlayerInfo").getOrThrow(), ReflectUtil.getNMSClass("PacketPlayOutPlayerInfo$EnumPlayerInfoAction").getOrThrow(), 0, true).getOrThrow();
    private static final Object ADD_PLAYER_CONSTANT = ReflectUtil.getEnumConstant(ReflectUtil.getNMSClass("PacketPlayOutPlayerInfo$EnumPlayerInfoAction").getOrThrow(), "ADD_PLAYER").getOrThrow();

    private static final Constructor<?> PACKET_ENTITY_DESTROY_CONSTRUCTOR = ReflectUtil.getConstructor(ReflectUtil.getNMSClass("PacketPlayOutEntityDestroy").getOrThrow(), int[].class).getOrThrow();

    private static final Constructor<?> PACKET_NAMED_ENTITY_SPAWN_CONSTRUCTOR = ReflectUtil.getConstructor(ReflectUtil.getNMSClass("PacketPlayOutNamedEntitySpawn").getOrThrow(), ReflectUtil.getNMSClass("EntityHuman").getOrThrow()).getOrThrow();

    ChannelPacketHandler(Plugin plugin) {
        super(plugin, "PacketPlayOutPlayerInfo");
    }

    @Override
    public boolean packetSending(Player player, Object packet, String packetName) {
        List<Object> list = Lists.newArrayList();
        boolean modified = false;
        for (Object infoData : (List<Object>) ReflectUtil.getFieldValue(packet, PLAYER_DATA_LIST).getOrThrow()) {
            UUID uuid = ((GameProfile) ReflectUtil.invokeMethod(infoData, GET_GAME_PROFILE).getOrThrow()).getId();
            if (NameTagChanger.INSTANCE.players.containsKey(uuid)) {
                Object prevDisplayName = ReflectUtil.invokeMethod(infoData, GET_DISPLAY_NAME).getOrThrow();
                Object displayName = prevDisplayName == null ? ReflectUtil.invokeConstructor(CHAT_COMPONENT_TEXT_CONSTRUCTOR, Bukkit.getPlayer(uuid).getPlayerListName()).getOrThrow() : ReflectUtil.invokeMethod(infoData, GET_DISPLAY_NAME).getOrThrow();
                Object newInfoData = ReflectUtil.invokeConstructor(PLAYER_INFO_DATA_CONSTRUCTOR, packet, new GameProfile(uuid, NameTagChanger.INSTANCE.players.get(uuid)),
                        ReflectUtil.invokeMethod(infoData, GET_LATENCY).getOrThrow(), ReflectUtil.invokeMethod(infoData, GET_GAMEMODE).getOrThrow(), displayName).getOrThrow();
                list.add(newInfoData);
                modified = true;
            } else {
                list.add(infoData);
            }
        }
        if (modified) {
            ReflectUtil.setFieldValue(packet, PLAYER_DATA_LIST, list);
        }
        return true;
    }

    @Override
    public void sendTabListRemovePacket(Player playerToRemove, Player seer) {
        Object array = Array.newInstance(ENTITY_PLAYER, 1);
        Array.set(array, 0, ReflectUtil.invokeMethod(playerToRemove, GET_HANDLE).getOrThrow());
        Object packet = ReflectUtil.invokeConstructor(PACKET_PLAYER_INFO_CONSTRUCTOR, REMOVE_PLAYER_CONSTANT, array).getOrThrow();
        sendPacket(seer, packet);
    }

    @Override
    public void sendTabListAddPacket(Player playerToAdd, String newName, Player seer) {
        Object packet = ReflectUtil.invokeConstructor(PACKET_PLAYER_INFO_CONSTRUCTOR_EMPTY).getOrThrow();
        Object infoData = ReflectUtil.invokeConstructor(PLAYER_INFO_DATA_CONSTRUCTOR, packet, new GameProfile(playerToAdd.getUniqueId(), newName),
                ReflectUtil.getFieldValue(ReflectUtil.invokeMethod(playerToAdd, GET_HANDLE).getOrThrow(), PING).getOrThrow(), getEnumGameMode(playerToAdd.getGameMode()),
                ReflectUtil.invokeConstructor(CHAT_COMPONENT_TEXT_CONSTRUCTOR, playerToAdd.getPlayerListName()).getOrThrow()).getOrThrow();
        ReflectUtil.setFieldValue(packet, PLAYER_DATA_LIST, Collections.singletonList(infoData)).getOrThrow();
        ReflectUtil.setFieldValue(packet, PLAYER_INFO_ACTION, ADD_PLAYER_CONSTANT).getOrThrow();
        sendPacket(seer, packet);
    }

    @Override
    public void sendEntityDestroyPacket(Player playerToDestroy, Player seer) {
        Object packet = ReflectUtil.invokeConstructor(PACKET_ENTITY_DESTROY_CONSTRUCTOR, (Object) new int[] {playerToDestroy.getEntityId()}).getOrThrow();
        sendPacket(seer, packet);
    }

    @Override
    public void sendNamedEntitySpawnPacket(Player playerToSpawn, Player seer) {
        Object packet = ReflectUtil.invokeConstructor(PACKET_NAMED_ENTITY_SPAWN_CONSTRUCTOR, ReflectUtil.invokeMethod(playerToSpawn, GET_HANDLE).getOrThrow()).getOrThrow();
        sendPacket(seer, packet);
    }

    @Override
    public void shutdown() {
        close();
    }

    private void sendPacket(Player player, Object packet) {
        Object playerConnection = ReflectUtil.getFieldValue(ReflectUtil.invokeMethod(player, GET_HANDLE).getOrThrow(), PLAYER_CONNECTION).getOrThrow();
        ReflectUtil.invokeMethod(playerConnection, SEND_PACKET, packet).getOrThrow();
    }

    private Object getEnumGameMode(GameMode bukkitGameMode) {
        int id = 0;
        switch (bukkitGameMode) {
            case CREATIVE:
                id = 1;
                break;
            case ADVENTURE:
                id = 2;
                break;
            case SPECTATOR:
                id = 3;
                break;
        }
        return ReflectUtil.invokeMethod(null, GET_BY_ID, id).getOrThrow();
    }
}
