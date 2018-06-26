package com.bringholm.nametagchanger;

import com.bringholm.packetinterceptor.v1_0.PacketInterceptor;
import com.bringholm.reflectutil.v1_1_1.ReflectUtil;
import com.google.common.collect.Lists;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.*;
import java.util.*;
import java.util.logging.Level;

/**
 * The non-ProtocolLib implementation of the packet handler.
 * @author AlvinB
 */
@SuppressWarnings("unchecked")
public class ChannelPacketHandler extends PacketInterceptor implements IPacketHandler {

    static {
        if (ReflectUtil.isVersionHigherThan(1, 9, 4)) {
            ENUM_GAMEMODE = ReflectUtil.getNMSClass("EnumGamemode").getOrThrow();
        } else {
            ENUM_GAMEMODE = ReflectUtil.getNMSClass("WorldSettings$EnumGamemode").getOrThrow();
        }
    }

    private static final Class<?> ENUM_GAMEMODE;
    private static final Class<?> GAME_PROFILE_CLASS = ReflectUtil.getClass("com.mojang.authlib.GameProfile").getOrThrow();

    private static final Class<?> PLAYER_INFO_DATA_CLASS = ReflectUtil.getNMSClass("PacketPlayOutPlayerInfo$PlayerInfoData").getOrThrow();
    private static final Field PLAYER_DATA_LIST = ReflectUtil.getDeclaredFieldByType(ReflectUtil.getNMSClass("PacketPlayOutPlayerInfo").getOrThrow(), List.class, 0, true).getOrThrow();
    private static final Method GET_GAME_PROFILE = ReflectUtil.getMethodByType(PLAYER_INFO_DATA_CLASS, GAME_PROFILE_CLASS, 0).getOrThrow();
    private static final Constructor<?> PLAYER_INFO_DATA_CONSTRUCTOR = ReflectUtil.getConstructor(PLAYER_INFO_DATA_CLASS, ReflectUtil.getNMSClass("PacketPlayOutPlayerInfo").getOrThrow(), GAME_PROFILE_CLASS,
            int.class, ENUM_GAMEMODE, ReflectUtil.getNMSClass("IChatBaseComponent").getOrThrow()).getOrThrow();
    private static final Method GET_LATENCY = ReflectUtil.getMethodByType(PLAYER_INFO_DATA_CLASS, int.class, 0).getOrThrow();
    private static final Method GET_GAMEMODE = ReflectUtil.getMethodByType(PLAYER_INFO_DATA_CLASS, ENUM_GAMEMODE, 0).getOrThrow();
    private static final Method GET_DISPLAY_NAME = ReflectUtil.getMethodByType(PLAYER_INFO_DATA_CLASS, ReflectUtil.getNMSClass("IChatBaseComponent").getOrThrow(), 0).getOrThrow();

    private static final Class<?> ENTITY_PLAYER = ReflectUtil.getNMSClass("EntityPlayer").getOrThrow();
    private static final Constructor<?> PACKET_PLAYER_INFO_CONSTRUCTOR = ReflectUtil.getConstructor(ReflectUtil.getNMSClass("PacketPlayOutPlayerInfo").getOrThrow(),
            ReflectUtil.getNMSClass("PacketPlayOutPlayerInfo$EnumPlayerInfoAction").getOrThrow(), Array.newInstance(ENTITY_PLAYER, 0).getClass()).getOrThrow();
    private static final Object REMOVE_PLAYER_CONSTANT = ReflectUtil.getEnumConstant(ReflectUtil.getNMSClass("PacketPlayOutPlayerInfo$EnumPlayerInfoAction").getOrThrow(), "REMOVE_PLAYER").getOrThrow();
    private static final Method GET_HANDLE = ReflectUtil.getMethod(ReflectUtil.getCBClass("entity.CraftPlayer").getOrThrow(), "getHandle").getOrThrow();
    private static final Field PLAYER_CONNECTION = ReflectUtil.getFieldByType(ENTITY_PLAYER, ReflectUtil.getNMSClass("PlayerConnection").getOrThrow(), 0).getOrThrow();
    private static final Method SEND_PACKET = ReflectUtil.getMethod(ReflectUtil.getNMSClass("PlayerConnection").getOrThrow(), "sendPacket", ReflectUtil.getNMSClass("Packet").getOrThrow()).getOrThrow();

    private static final Constructor<?> PACKET_PLAYER_INFO_CONSTRUCTOR_EMPTY = ReflectUtil.getConstructor(ReflectUtil.getNMSClass("PacketPlayOutPlayerInfo").getOrThrow()).getOrThrow();
    private static final Method ENTITY_PLAYER_GET_GAME_PROFILE = ReflectUtil.getMethodByType(ENTITY_PLAYER, GAME_PROFILE_CLASS, 0).getOrThrow();
    private static final Field PING = ReflectUtil.getField(ENTITY_PLAYER, "ping").getOrThrow();
    @SuppressWarnings("ConstantConditions")
    private static final Method GET_BY_ID = ReflectUtil.getMethod(ENUM_GAMEMODE, "getById", int.class).getOrThrow();
    private static final Constructor<?> CHAT_COMPONENT_TEXT_CONSTRUCTOR = ReflectUtil.getConstructor(ReflectUtil.getNMSClass("ChatComponentText").getOrThrow(), String.class).getOrThrow();
    private static final Field PLAYER_INFO_ACTION = ReflectUtil.getDeclaredFieldByType(ReflectUtil.getNMSClass("PacketPlayOutPlayerInfo").getOrThrow(), ReflectUtil.getNMSClass("PacketPlayOutPlayerInfo$EnumPlayerInfoAction").getOrThrow(), 0, true).getOrThrow();
    private static final Object ADD_PLAYER_CONSTANT = ReflectUtil.getEnumConstant(ReflectUtil.getNMSClass("PacketPlayOutPlayerInfo$EnumPlayerInfoAction").getOrThrow(), "ADD_PLAYER").getOrThrow();

    private static final Constructor<?> PACKET_ENTITY_DESTROY_CONSTRUCTOR = ReflectUtil.getConstructor(ReflectUtil.getNMSClass("PacketPlayOutEntityDestroy").getOrThrow(), int[].class).getOrThrow();

    private static final Constructor<?> PACKET_NAMED_ENTITY_SPAWN_CONSTRUCTOR = ReflectUtil.getConstructor(ReflectUtil.getNMSClass("PacketPlayOutNamedEntitySpawn").getOrThrow(), ReflectUtil.getNMSClass("EntityHuman").getOrThrow()).getOrThrow();

    private static final Class<?> ITEM_STACK_CLASS = ReflectUtil.getNMSClass("ItemStack").getOrThrow();
    private static final Method AS_NMS_COPY = ReflectUtil.getMethod(ReflectUtil.getCBClass("inventory.CraftItemStack").getOrThrow(), "asNMSCopy", ItemStack.class).getOrThrow();
    private static final Class<?> ENUM_ITEM_SLOT_CLASS = ReflectUtil.getNMSClass("EnumItemSlot").getOrThrow();
    private static final Method ENUM_ITEM_SLOT_BY_NAME = ReflectUtil.getMethodByPredicate(ENUM_ITEM_SLOT_CLASS, new ReflectUtil.MethodPredicate().withModifiers(Modifier.PUBLIC, Modifier.STATIC).withParams(String.class).withReturnType(ENUM_ITEM_SLOT_CLASS).withPredicate(method -> !method.getName().equals("valueOf")), 0).getOrThrow();
    private static final Constructor<?> PACKET_ENTITY_EQUIPMENT_CONSTRUCTOR = ReflectUtil.getConstructor(ReflectUtil.getNMSClass("PacketPlayOutEntityEquipment").getOrThrow(), int.class, ENUM_ITEM_SLOT_CLASS, ITEM_STACK_CLASS).getOrThrow();

    private static final Class<?> SCOREBOARD_TEAM_PACKET_CLASS = ReflectUtil.getNMSClass("PacketPlayOutScoreboardTeam").getOrThrow();
    private static final Field SCOREBOARD_TEAM_PACKET_MODE = ReflectUtil.getDeclaredField(SCOREBOARD_TEAM_PACKET_CLASS, "i", true).getOrThrow();
    private static final Field SCOREBOARD_TEAM_PACKET_ENTRIES_TO_ADD = ReflectUtil.getDeclaredFieldByType(SCOREBOARD_TEAM_PACKET_CLASS, Collection.class, 0, true).getOrThrow();
    private static final int CREATE_SCOREBOARD_TEAM_MODE = 0;
    private static final int JOIN_SCOREBOARD_TEAM_MODE = 3;
    private static final int LEAVE_SCOREBOARD_TEAM_MODE = 4;

    private static final Constructor<?> SCOREBOARD_TEAM_PACKET_CONSTRUCTOR = ReflectUtil.getConstructor(SCOREBOARD_TEAM_PACKET_CLASS).getOrThrow();
    private static final Field SCOREBOARD_TEAM_PACKET_TEAM_NAME = ReflectUtil.getDeclaredField(SCOREBOARD_TEAM_PACKET_CLASS, "a", true).getOrThrow();

    ChannelPacketHandler(Plugin plugin) {
        super(plugin, "PacketPlayOutPlayerInfo", "PacketPlayOutScoreboardTeam");
    }

    @Override
    public boolean packetSending(Player player, Object packet, String packetName) {
        if (NameTagChanger.INSTANCE.sendingPackets) {
            return true;
        }
        if (packetName.equals("PacketPlayOutPlayerInfo")) {
            List<Object> list = Lists.newArrayList();
            boolean modified = false;
            for (Object infoData : (List<Object>) ReflectUtil.getFieldValue(packet, PLAYER_DATA_LIST).getOrThrow()) {
                GameProfileWrapper gameProfile = GameProfileWrapper.fromHandle(ReflectUtil.invokeMethod(infoData, GET_GAME_PROFILE).getOrThrow());
                UUID uuid = gameProfile.getUUID();
                if (NameTagChanger.INSTANCE.gameProfiles.containsKey(uuid)) {
                    Object prevDisplayName = ReflectUtil.invokeMethod(infoData, GET_DISPLAY_NAME).getOrThrow();
                    Object displayName = prevDisplayName == null ? ReflectUtil.invokeConstructor(CHAT_COMPONENT_TEXT_CONSTRUCTOR, (Bukkit.getPlayer(uuid) == null ? gameProfile.getName() : Bukkit.getPlayer(uuid).getPlayerListName())).getOrThrow() : ReflectUtil.invokeMethod(infoData, GET_DISPLAY_NAME).getOrThrow();
                    GameProfileWrapper newGameProfile = NameTagChanger.INSTANCE.gameProfiles.get(uuid);
                    Object newInfoData = ReflectUtil.invokeConstructor(PLAYER_INFO_DATA_CONSTRUCTOR, packet, newGameProfile.getHandle(),
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
        } else {
            int mode = (int) ReflectUtil.getFieldValue(packet, SCOREBOARD_TEAM_PACKET_MODE).getOrThrow();
            if (mode == CREATE_SCOREBOARD_TEAM_MODE || mode == JOIN_SCOREBOARD_TEAM_MODE || mode == LEAVE_SCOREBOARD_TEAM_MODE) {
                Collection<String> entriesToAdd = (Collection<String>) ReflectUtil.getFieldValue(packet, SCOREBOARD_TEAM_PACKET_ENTRIES_TO_ADD).getOrThrow();
                Map<UUID, String> changedPlayerNames = NameTagChanger.INSTANCE.getChangedPlayers();
                //noinspection Duplicates
                for (String entry : entriesToAdd) {
                    for (UUID uuid : changedPlayerNames.keySet()) {
                        Player changedPlayer = Bukkit.getPlayer(uuid);
                        if (changedPlayer != null && changedPlayer.getName().equals(entry)) {
                            entriesToAdd.remove(entry);
                            entriesToAdd.add(changedPlayerNames.get(uuid));
                            break;
                        }
                    }
                }
            }
        }
        return true;
    }

    @Override
    protected void logMessage(Level level, String message, Exception e) {
        if (level == Level.SEVERE) {
            System.err.println("[NameTagChanger] " + message);
        } else {
            NameTagChanger.INSTANCE.printMessage(message);
        }
        if (e != null) {
            e.printStackTrace();
        }
    }

    @Override
    public void sendTabListRemovePacket(Player playerToRemove, Player seer) {
        Object array = Array.newInstance(ENTITY_PLAYER, 1);
        Array.set(array, 0, ReflectUtil.invokeMethod(playerToRemove, GET_HANDLE).getOrThrow());
        Object packet = ReflectUtil.invokeConstructor(PACKET_PLAYER_INFO_CONSTRUCTOR, REMOVE_PLAYER_CONSTANT, array).getOrThrow();
        sendPacket(seer, packet);
    }

    @Override
    public void sendTabListAddPacket(Player playerToAdd, GameProfileWrapper newProfile, Player seer) {
        Object packet = ReflectUtil.invokeConstructor(PACKET_PLAYER_INFO_CONSTRUCTOR_EMPTY).getOrThrow();
        Object entityPlayer = ReflectUtil.invokeMethod(playerToAdd, GET_HANDLE).getOrThrow();
        Object infoData = ReflectUtil.invokeConstructor(PLAYER_INFO_DATA_CONSTRUCTOR, packet, newProfile.getHandle(),
                ReflectUtil.getFieldValue(entityPlayer, PING).getOrThrow(), getEnumGameMode(playerToAdd.getGameMode()),
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
    public void sendEntityEquipmentPacket(Player playerToSpawn, Player seer) {
        int entityID = playerToSpawn.getEntityId();
        if (playerToSpawn.getInventory().getItemInMainHand() != null) {
            doEquipmentPacketSend(entityID, getEnumItemSlot(EquipmentSlot.HAND), getNMSItemStack(playerToSpawn.getInventory().getItemInMainHand()), seer);
        }
        if (playerToSpawn.getInventory().getItemInOffHand() != null) {
            doEquipmentPacketSend(entityID, getEnumItemSlot(EquipmentSlot.OFF_HAND), getNMSItemStack(playerToSpawn.getInventory().getItemInOffHand()), seer);
        }
        if (playerToSpawn.getInventory().getBoots() != null) {
            doEquipmentPacketSend(entityID, getEnumItemSlot(EquipmentSlot.FEET), getNMSItemStack(playerToSpawn.getInventory().getBoots()), seer);
        }
        if (playerToSpawn.getInventory().getLeggings() != null) {
            doEquipmentPacketSend(entityID, getEnumItemSlot(EquipmentSlot.LEGS), getNMSItemStack(playerToSpawn.getInventory().getLeggings()), seer);
        }
        if (playerToSpawn.getInventory().getChestplate() != null) {
            doEquipmentPacketSend(entityID, getEnumItemSlot(EquipmentSlot.CHEST), getNMSItemStack(playerToSpawn.getInventory().getChestplate()), seer);
        }
        if (playerToSpawn.getInventory().getHelmet() != null) {
            doEquipmentPacketSend(entityID, getEnumItemSlot(EquipmentSlot.HEAD), getNMSItemStack(playerToSpawn.getInventory().getHelmet()), seer);
        }
    }

    private void doEquipmentPacketSend(int entityID, Object enumItemSlot, Object itemStack, Player recipient) {
        Object packet = ReflectUtil.invokeConstructor(PACKET_ENTITY_EQUIPMENT_CONSTRUCTOR, entityID, enumItemSlot, itemStack).getOrThrow();
        sendPacket(recipient, packet);
    }

    private Object getNMSItemStack(ItemStack itemStack) {
        return ReflectUtil.invokeMethod(null, AS_NMS_COPY, itemStack).getOrThrow();
    }

    private Object getEnumItemSlot(EquipmentSlot slot) {
        switch (slot) {
            case HAND:
                return ReflectUtil.invokeMethod(null, ENUM_ITEM_SLOT_BY_NAME, "mainhand").getOrThrow();
            case OFF_HAND:
                return ReflectUtil.invokeMethod(null, ENUM_ITEM_SLOT_BY_NAME, "offhand").getOrThrow();
            case FEET:
                return ReflectUtil.invokeMethod(null, ENUM_ITEM_SLOT_BY_NAME, "feet").getOrThrow();
            case LEGS:
                return ReflectUtil.invokeMethod(null, ENUM_ITEM_SLOT_BY_NAME, "legs").getOrThrow();
            case CHEST:
                return ReflectUtil.invokeMethod(null, ENUM_ITEM_SLOT_BY_NAME, "chest").getOrThrow();
            case HEAD:
                return ReflectUtil.invokeMethod(null, ENUM_ITEM_SLOT_BY_NAME, "head").getOrThrow();
            default:
                logMessage(Level.SEVERE, "Unknown EquipmentSlot: " + slot, null);
                return null;
        }
    }

    @Override
    public void sendScoreboardRemovePacket(String playerToRemove, Player seer, String team) {
        sendPacket(seer, getScoreboardPacket(team, playerToRemove, LEAVE_SCOREBOARD_TEAM_MODE));
    }

    @Override
    public void sendScoreboardAddPacket(String playerToAdd, Player seer, String team) {
        sendPacket(seer, getScoreboardPacket(team, playerToAdd, JOIN_SCOREBOARD_TEAM_MODE));
    }

    private Object getScoreboardPacket(String team, String entryToAdd, int mode) {
        Object packet = ReflectUtil.invokeConstructor(SCOREBOARD_TEAM_PACKET_CONSTRUCTOR).getOrThrow();
        ReflectUtil.setFieldValue(packet, SCOREBOARD_TEAM_PACKET_TEAM_NAME, team).getOrThrow();
        ReflectUtil.setFieldValue(packet, SCOREBOARD_TEAM_PACKET_MODE, mode).getOrThrow();
        ((Collection<String>) ReflectUtil.getFieldValue(packet, SCOREBOARD_TEAM_PACKET_ENTRIES_TO_ADD).getOrThrow()).add(entryToAdd);
        return packet;
    }

    @Override
    public void shutdown() {
        close();
    }

    @Override
    public GameProfileWrapper getDefaultPlayerProfile(Player player) {
        Object entityPlayer = ReflectUtil.invokeMethod(player, GET_HANDLE).getOrThrow();
        return GameProfileWrapper.fromHandle(ReflectUtil.invokeMethod(entityPlayer, ENTITY_PLAYER_GET_GAME_PROFILE).getOrThrow());
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
