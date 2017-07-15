package com.bringholm.nametagchanger;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.authlib.GameProfile;
import io.netty.channel.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * A packet listener using netty channel injection.
 *
 * Based off of TinyProtocol by dmulloy2:
 * https://github.com/aadnk/ProtocolLib/blob/master/modules/TinyProtocol/src/main/java/com/comphenix/tinyprotocol/
 *
 * @author AlvinB
 */
@SuppressWarnings({"SameParameterValue", "WeakerAccess", "SameReturnValue", "unchecked"})
public abstract class PacketInterceptor implements Listener {


    private static final Method GET_HANDLE = ReflectUtil.getMethod(ReflectUtil.getCBClass("entity.CraftPlayer").getOrThrow(), "getHandle").getOrThrow();
    private static final Field PLAYER_CONNECTION = ReflectUtil.getFieldByType(ReflectUtil.getNMSClass("EntityPlayer").getOrThrow(), ReflectUtil.getNMSClass("PlayerConnection").getOrThrow(), 0).getOrThrow();
    private static final Class<?> NETWORK_MANAGER_CLASS = ReflectUtil.getNMSClass("NetworkManager").getOrThrow();
    private static final Field NETWORK_MANAGER = ReflectUtil.getFieldByType(ReflectUtil.getNMSClass("PlayerConnection").getOrThrow(), NETWORK_MANAGER_CLASS, 0).getOrThrow();
    private static final Field CHANNEL = ReflectUtil.getFieldByType(NETWORK_MANAGER_CLASS, Channel.class, 0).getOrThrow();

    private static final Method GET_MINECRAFT_SERVER = ReflectUtil.getMethodByType(ReflectUtil.getCBClass("CraftServer").getOrThrow(), ReflectUtil.getNMSClass("MinecraftServer").getOrThrow(), 0).getOrThrow();
    private static final Class<?> SERVER_CONNECTION_CLASS = ReflectUtil.getNMSClass("ServerConnection").getOrThrow();
    private static final Field SERVER_CONNECTION = ReflectUtil.getDeclaredFieldByType(ReflectUtil.getNMSClass("MinecraftServer").getOrThrow(), SERVER_CONNECTION_CLASS, 0, true).getOrThrow();
    private static final Class<?> PACKET_LOGIN_START = ReflectUtil.getNMSClass("PacketLoginInStart").getOrThrow();
    private static final Method GET_GAME_PROFILE = ReflectUtil.getMethodByType(PACKET_LOGIN_START, GameProfile.class, 0).getOrThrow();

    private static Field NETWORK_MANAGERS = null;
    private static Field CHANNEL_FUTURES = null;

    private static int id = 0;

    private final Set<String> packets;
    private final boolean blackList;
    private final Plugin plugin;
    private final String handlerName;
    private final List<Channel> serverChannels = Lists.newArrayList();
    // We have to store player names instead of UUIDs as PacketLoginInStart ignores UUIDs. Name changing should not be an issue,
    // as the player is only kept in this list while they are online.
    private final Map<String, Channel> injectedPlayerChannels = Maps.newHashMap();
    private ChannelInboundHandlerAdapter serverChannelHandler;
    private boolean syncWrite = doSyncWrite();
    private boolean syncRead = doSyncRead();

    public PacketInterceptor(Plugin plugin) {
        this(plugin, true);
    }


    public PacketInterceptor(Plugin plugin, String... packets) {
        this(plugin, false, packets);
    }

    public PacketInterceptor(Plugin plugin, boolean blackList, String... packets) {
        this.packets = Arrays.stream(packets).collect(Collectors.toSet());
        this.blackList = blackList;
        this.plugin = plugin;
        this.handlerName = "packet_interceptor_" + plugin.getName() + "_" + id++;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        injectServer();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!injectedPlayerChannels.containsKey(player.getName())) {
                injectPlayer(player);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerLoginEvent e) {
        // We already have the name, but we want to
        // set the player instance as well.
        injectPlayer(e.getPlayer());
    }

    @EventHandler
    public void onPlayerLeave(PlayerQuitEvent e) {
        // Remove disconnected players.
        if (injectedPlayerChannels.containsKey(e.getPlayer().getName())) {
            injectedPlayerChannels.remove(e.getPlayer().getName());
        }
    }

    private void injectServer() {
        Object minecraftServer = ReflectUtil.invokeMethod(Bukkit.getServer(), GET_MINECRAFT_SERVER).getOrThrow();
        Object serverConnection = ReflectUtil.getFieldValue(minecraftServer, SERVER_CONNECTION).getOrThrow();
        for (int i = 0; NETWORK_MANAGERS == null || CHANNEL_FUTURES == null; i++) {
            Field field = ReflectUtil.getDeclaredFieldByType(SERVER_CONNECTION_CLASS, List.class, i, true).getOrThrow();
            List<Object> list = (List<Object>) ReflectUtil.getFieldValue(serverConnection, field).getOrThrow();
            for (Object object : list) {
                if (NETWORK_MANAGERS == null && NETWORK_MANAGER_CLASS.isInstance(object)) {
                    NETWORK_MANAGERS = field;
                }
                if (CHANNEL_FUTURES == null && ChannelFuture.class.isInstance(object)) {
                    CHANNEL_FUTURES = field;
                }
            }
            if (CHANNEL_FUTURES != null && NETWORK_MANAGERS == null) {
                NETWORK_MANAGERS = field;
            }
        }
        List<Object> networkManagers = (List<Object>) ReflectUtil.getFieldValue(serverConnection, NETWORK_MANAGERS).getOrThrow();
        List<ChannelFuture> channelFutures = (List<ChannelFuture>) ReflectUtil.getFieldValue(serverConnection, CHANNEL_FUTURES).getOrThrow();
        ChannelInitializer<Channel> channelInitializer = new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel channel) throws Exception {
                try {
                    synchronized (networkManagers) {
                        channel.eventLoop().submit(() -> {
                            injectChannel(channel, null);
                        });
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to inject Channel " + channel + " due to " + e + "!");
                }
            }
        };
        ChannelInitializer<Channel> channelPreInitializer = new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel channel) throws Exception {
                channel.pipeline().addLast(channelInitializer);
            }
        };
        serverChannelHandler = new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
                Channel channel = (Channel) message;
                channel.pipeline().addFirst(channelPreInitializer);
                context.fireChannelRead(message);
            }
        };
        for (ChannelFuture channelFuture : channelFutures) {
            Channel channel = channelFuture.channel();
            serverChannels.add(channel);
            channel.pipeline().addFirst(serverChannelHandler);
        }
    }

    private void injectPlayer(Player player) {
        Channel channel;
        if (injectedPlayerChannels.containsKey(player.getName())) {
            channel = injectedPlayerChannels.get(player.getName());
        } else {
            Object handle = ReflectUtil.invokeMethod(player, GET_HANDLE).getOrThrow();
            Object playerConnection = ReflectUtil.getFieldValue(handle, PLAYER_CONNECTION).getOrThrow();
            if (playerConnection == null) {
                plugin.getLogger().warning("Failed to inject Channel for player " + player.getName() + "!");
                return;
            }
            Object networkManager = ReflectUtil.getFieldValue(playerConnection, NETWORK_MANAGER).getOrThrow();
            channel = (Channel) ReflectUtil.getFieldValue(networkManager, CHANNEL).getOrThrow();
        }
        injectChannel(channel, player);
        if (!injectedPlayerChannels.containsKey(player.getName())) {
            injectedPlayerChannels.put(player.getName(), channel);
        }
    }

    private void injectChannel(Channel channel, Player player) {
        ChannelInterceptor handler = (ChannelInterceptor) channel.pipeline().get(handlerName);
        if (handler == null) {
            handler = new ChannelInterceptor();
            channel.pipeline().addBefore("packet_handler", handlerName, handler);
        }
        if (player != null) {
            handler.player = player;
        }
    }

    public void close() {
        for (Channel channel : injectedPlayerChannels.values()) {
            try {
                channel.eventLoop().execute(() -> channel.pipeline().remove(handlerName));
            } catch (NoSuchElementException ignored) {

            }
        }
        injectedPlayerChannels.clear();
        for (Channel channel : serverChannels) {
            channel.pipeline().remove(serverChannelHandler);
        }
        HandlerList.unregisterAll(this);
    }

    private boolean doSyncRead() {
        try {
            return this.getClass().getMethod("packetReading", Player.class, Object.class, String.class).getDeclaringClass() != PacketInterceptor.class;
        } catch (NoSuchMethodException e) {
            // Should not happen
            e.printStackTrace();
            return false;
        }
    }

    private boolean doSyncWrite() {
        try {
            return this.getClass().getMethod("packetSending", Player.class, Object.class, String.class).getDeclaringClass() != PacketInterceptor.class;
        } catch (NoSuchMethodException e) {
            // Should not happen
            e.printStackTrace();
            return false;
        }
    }

    public boolean packetSendingAsync(Player player, Object packet, String packetName) {
        return true;
    }

    public boolean packetReadingAsync(Player player, Object packet, String packetName) {
        return true;
    }

    public boolean packetSending(Player player, Object packet, String packetName) {
        return true;
    }

    public boolean packetReading(Player player, Object packet, String packetName) {
        return true;
    }

    private class ChannelInterceptor extends ChannelDuplexHandler {
        private Player player;

        @Override
        public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
            if ((blackList && packets.contains(message.getClass().getSimpleName())) || (!blackList && !packets.contains(message.getClass().getSimpleName()))) {
                super.write(context, message, promise);
                return;
            }
            if (syncWrite) {
                final boolean[] result = new boolean[2];
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        try {
                            result[0] = packetSending(player, message, message.getClass().getSimpleName());
                        } catch (Exception e) {
                            System.out.println("An error occurred while plugin " + plugin.getName() + " was handling packet " + message.getClass().getSimpleName() + "!");
                            e.printStackTrace();
                            result[0] = true;
                        }
                        result[1] = true;
                        synchronized (result) {
                            result.notifyAll();
                        }
                    }
                }.runTask(plugin);
                synchronized (result) {
                    while (!result[1]) {
                        result.wait();
                    }
                }
                if (result[0]) {
                    super.write(context, message, promise);
                }
            } else {
                try {
                    if (packetSendingAsync(player, message, message.getClass().getSimpleName())) {
                        super.write(context, message, promise);
                    }
                } catch (Exception e) {
                    System.out.println("An error occurred while plugin " + plugin.getName() + " was handling packet " + message.getClass().getSimpleName() + "!");
                    e.printStackTrace();
                    super.write(context, message, promise);
                }
            }
        }

        @Override
        public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
            if (PACKET_LOGIN_START.isInstance(message)) {
                injectedPlayerChannels.put(((GameProfile) ReflectUtil.invokeMethod(message, GET_GAME_PROFILE).getOrThrow()).getName(), context.channel());
            }
            if ((blackList && packets.contains(message.getClass().getSimpleName())) || (!blackList && !packets.contains(message.getClass().getSimpleName()))) {
                super.channelRead(context, message);
                return;
            }
            if (syncRead) {
                // result[0] is for the result of the packet handling, result [1] is whether the
                // packet handling is finished.
                final boolean[] result = new boolean[2];
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        try {
                            result[0] = packetReading(player, message, message.getClass().getSimpleName());
                        } catch (Exception e) {
                            System.out.println("An error occurred while plugin " + plugin.getName() + " was handling packet " + message.getClass().getSimpleName() + "!");
                            e.printStackTrace();
                            result[0] = true;
                        }
                        result[1] = true;
                        synchronized (result) {
                            result.notifyAll();
                        }
                    }
                }.runTask(plugin);
                synchronized (result) {
                    while (!result[1]) {
                        result.wait();
                    }
                }
                if (result[0]) {
                    super.channelRead(context, message);
                }
            } else {
                try {
                    if (packetReadingAsync(player, message, message.getClass().getSimpleName())) {
                        super.channelRead(context, message);
                    }
                } catch (Exception e) {
                    System.out.println("An error occurred while plugin " + plugin.getName() + " was handling packet " + message.getClass().getSimpleName() + "!");
                    e.printStackTrace();
                    super.channelRead(context, message);
                }
            }
        }
    }
}