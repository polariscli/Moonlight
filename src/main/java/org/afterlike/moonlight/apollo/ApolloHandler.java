package org.afterlike.moonlight.apollo;

import com.google.gson.JsonObject;
import com.google.protobuf.Any;
import com.lunarclient.apollo.common.v1.LunarClientVersion;
import com.lunarclient.apollo.common.v1.MinecraftVersion;
import com.lunarclient.apollo.player.v1.PlayerHandshakeMessage;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.afterlike.moonlight.Moonlight;
import org.jetbrains.annotations.NotNull;

public class ApolloHandler {
	public static final @NotNull String CHANNEL_APOLLO = "lunar:apollo";
	public static final @NotNull String CHANNEL_APOLLO_JSON = "apollo:json";
	private static ApolloHandler instance;
	private final Set<NetworkManager> registeredManagers = new HashSet<>();
	public static void init() {
		if (instance == null) {
			instance = new ApolloHandler();
			MinecraftForge.EVENT_BUS.register(instance);
			Moonlight.getLogger().info("Apollo handler initialized");
		}
	}

	public static ApolloHandler getInstance() {
		return instance;
	}

	private ApolloHandler() {
	}

	/**
	 * Called when the client connects to a server. Registers Apollo plugin channels
	 * to appear as Lunar Client.
	 */
	@SubscribeEvent
	public void onClientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
		if (Minecraft.getMinecraft().isSingleplayer()) {
			return;
		}
		NetworkManager manager = event.manager;
		// Prevent duplicate registration (event fires twice: vanilla then modded)
		if (registeredManagers.contains(manager)) {
			return;
		}
		registeredManagers.add(manager);
		Moonlight.getLogger().info(
				"ApolloHandler.onClientConnected - Connection: {}, Remote: {}, HandlerType: {}",
				manager.isChannelOpen() ? "OPEN" : "CLOSED", manager.getRemoteAddress(),
				event.getHandlerType());
		registerChannels(manager);
		sendPlayerHandshake(manager);
	}

	/**
	 * Registers the lunar:apollo and apollo:json channels via REGISTER packet.
	 */
	private void registerChannels(NetworkManager manager) {
		// Channel registration uses REGISTER channel with null-separated channel names
		String channels = CHANNEL_APOLLO + "\0" + CHANNEL_APOLLO_JSON;
		byte[] data = channels.getBytes(StandardCharsets.UTF_8);
		PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
		buffer.writeBytes(data);
		C17PacketCustomPayload packet = new C17PacketCustomPayload("REGISTER", buffer);
		manager.sendPacket(packet);
		Moonlight.getLogger().info(
				"ApolloHandler.registerChannels - Registered Apollo channels: {} and {}",
				CHANNEL_APOLLO, CHANNEL_APOLLO_JSON);
	}

	/**
	 * Sends the PlayerHandshakeMessage to the server using Protocol Buffer format.
	 */
	private void sendPlayerHandshake(NetworkManager manager) {
		Moonlight.getLogger().debug(
				"ApolloHandler.sendPlayerHandshake - Constructing protobuf handshake message");
		// Build Minecraft version (V1_8 for 1.8.9)
		MinecraftVersion minecraftVersion = MinecraftVersion.newBuilder().setEnum("V1_8").build();
		// Build Lunar Client version
		LunarClientVersion lunarClientVersion = LunarClientVersion.newBuilder()
				.setGitBranch("master").setGitCommit("production").setSemver(Moonlight.LUNAR_SEMVER)
				.build();
		// Build PlayerHandshakeMessage
		PlayerHandshakeMessage handshake = PlayerHandshakeMessage.newBuilder()
				.setMinecraftVersion(minecraftVersion).setLunarClientVersion(lunarClientVersion)
				// Empty mod list - we don't report any mods
				.build();
		// Wrap in Any and convert to byte array
		Any any = Any.pack(handshake);
		byte[] data = any.toByteArray();
		Moonlight.getLogger().debug(
				"ApolloHandler.sendPlayerHandshake - Sending handshake on channel: {}, size: {} bytes, channel open: {}",
				CHANNEL_APOLLO, data.length, manager.isChannelOpen());
		// Send on lunar:apollo channel (protobuf format)
		PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
		buffer.writeBytes(data);
		C17PacketCustomPayload packet = new C17PacketCustomPayload(CHANNEL_APOLLO, buffer);
		manager.sendPacket(packet);
		Moonlight.getLogger().info(
				"ApolloHandler.sendPlayerHandshake - Sent Apollo player handshake (protobuf)");
	}

	/**
	 * Sends a JSON packet on the specified channel.
	 *
	 * @param manager
	 *            the network manager
	 * @param channel
	 *            the plugin channel
	 * @param message
	 *            the JSON message to send
	 */
	public void sendPacket(NetworkManager manager, @NotNull String channel,
			@NotNull JsonObject message) {
		byte[] data = message.toString().getBytes(StandardCharsets.UTF_8);
		PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
		buffer.writeBytes(data);
		C17PacketCustomPayload packet = new C17PacketCustomPayload(channel, buffer);
		manager.sendPacket(packet);
	}

	/**
	 * Sends a JSON packet on the specified channel using the current connection.
	 *
	 * @param channel
	 *            the plugin channel
	 * @param message
	 *            the JSON message to send
	 */
	public void sendPacket(@NotNull String channel, @NotNull JsonObject message) {
		if (Minecraft.getMinecraft().getNetHandler() == null) {
			return;
		}
		byte[] data = message.toString().getBytes(StandardCharsets.UTF_8);
		PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
		buffer.writeBytes(data);
		C17PacketCustomPayload packet = new C17PacketCustomPayload(channel, buffer);
		Minecraft.getMinecraft().getNetHandler().addToSendQueue(packet);
	}
}
