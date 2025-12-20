package org.afterlike.moonlight.platform.mixin.minecraft.client.network;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import org.afterlike.moonlight.Moonlight;
import org.afterlike.moonlight.apollo.ApolloHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerPlayClient.class)
public class NetHandlerPlayClientMixin {
	@Inject(method = "handleCustomPayload", at = @At("HEAD"))
	private void moonlight$onHandleCustomPayload(S3FPacketCustomPayload packet, CallbackInfo ci) {
		String channel = packet.getChannelName();
		if (channel.equals(ApolloHandler.CHANNEL_APOLLO_JSON)) {
			try {
				byte[] data = new byte[packet.getBufferData().readableBytes()];
				packet.getBufferData().getBytes(packet.getBufferData().readerIndex(), data);
				String json = new String(data, StandardCharsets.UTF_8);
				JsonObject message = JsonParser.parseString(json).getAsJsonObject();
				moonlight$handleApolloMessage(message);
			} catch (Exception e) {
				Moonlight.getLogger().warn("Failed to parse Apollo JSON message: {}",
						e.getMessage());
			}
		}
	}

	@Unique private void moonlight$handleApolloMessage(JsonObject message) {
		String type = message.has("@type") ? message.get("@type").getAsString() : "unknown";
		Moonlight.getLogger().debug("Received Apollo message: {}", type);
		if (type.contains("OverrideConfigurableSettingsMessage")) {
			Moonlight.getLogger().info("Server enabled Apollo modules");
		} else if (type.contains("UpdatePlayerWorldMessage")) {
			Moonlight.getLogger().debug("Server updated player world info");
		}
	}
}
