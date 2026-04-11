package org.afterlike.moonlight.apollo;

import com.google.gson.JsonObject;
import com.google.protobuf.Any;
import com.lunarclient.apollo.common.v1.LunarClientVersion;
import com.lunarclient.apollo.common.v1.MinecraftVersion;
import com.lunarclient.apollo.player.v1.EmbeddedCheckoutSupport;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class ApolloHandler {
	public static final @NotNull String CHANNEL_APOLLO = "lunar:apollo";
	public static final @NotNull String CHANNEL_APOLLO_JSON = "apollo:json";
	public static final @NotNull String CHANNEL_PM = "lunarclient:pm";
	public static final @NotNull String CHANNEL_TIMERS = "badlion:timers";
	public static final @NotNull String CHANNEL_TRANSFER = "transfer:channel";
	private static final Logger LOGGER = LogManager.getLogger();
	private static ApolloHandler instance;
	private final Set<NetworkManager> registeredManagers = new HashSet<>();
	public static void init() {
		if (instance == null) {
			instance = new ApolloHandler();
			MinecraftForge.EVENT_BUS.register(instance);
			LOGGER.info("Apollo handler initialized");
		}
	}

	public static ApolloHandler getInstance() {
		return instance;
	}

	private ApolloHandler() {
	}

	@SubscribeEvent
	public void onClientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
		if (Minecraft.getMinecraft().isSingleplayer()) {
			return;
		}
		NetworkManager manager = event.manager;
		if (registeredManagers.contains(manager)) {
			return;
		}
		registeredManagers.add(manager);
		registerChannels(manager);
		sendPlayerHandshake(manager);
	}

	private void registerChannels(NetworkManager manager) {
		String channels = String.join("\0", CHANNEL_APOLLO, CHANNEL_APOLLO_JSON, CHANNEL_PM,
				CHANNEL_TIMERS, CHANNEL_TRANSFER);
		byte[] data = channels.getBytes(StandardCharsets.UTF_8);
		PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
		buffer.writeBytes(data);
		C17PacketCustomPayload packet = new C17PacketCustomPayload("REGISTER", buffer);
		manager.sendPacket(packet);
		LOGGER.info("Registered 5 Lunar channels");
	}

	private void sendPlayerHandshake(NetworkManager manager) {
		String version = Moonlight.get().getLunarVersion();
		String gitCommit = Moonlight.get().getLunarGitCommit();
		if (version == null || gitCommit == null) {
			LOGGER.warn("Lunar version not yet available, skipping Apollo handshake");
			return;
		}
		MinecraftVersion minecraftVersion = MinecraftVersion.newBuilder().setEnum("v1_8").build();
		String semver = version.startsWith("v") ? version : "v" + version;
		LunarClientVersion lunarClientVersion = LunarClientVersion.newBuilder()
				.setGitBranch("master").setGitCommit(gitCommit).setSemver(semver).build();
		PlayerHandshakeMessage.Builder handshake = PlayerHandshakeMessage.newBuilder()
				.setMinecraftVersion(minecraftVersion).setLunarClientVersion(lunarClientVersion)
				.setEmbeddedCheckoutSupport(
						EmbeddedCheckoutSupport.EMBEDDED_CHECKOUT_SUPPORT_WINDOW);
		ModStatusGenerator.apply(handshake,
				Minecraft.getMinecraft().getSession().getProfile().getId());
		Any any = Any.pack(handshake.build());
		byte[] data = any.toByteArray();
		PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
		buffer.writeBytes(data);
		C17PacketCustomPayload packet = new C17PacketCustomPayload(CHANNEL_APOLLO, buffer);
		manager.sendPacket(packet);
		LOGGER.info("Sent Apollo player handshake");
	}

	public void sendPacket(NetworkManager manager, @NotNull String channel,
			@NotNull JsonObject message) {
		byte[] data = message.toString().getBytes(StandardCharsets.UTF_8);
		PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
		buffer.writeBytes(data);
		C17PacketCustomPayload packet = new C17PacketCustomPayload(channel, buffer);
		manager.sendPacket(packet);
	}

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
