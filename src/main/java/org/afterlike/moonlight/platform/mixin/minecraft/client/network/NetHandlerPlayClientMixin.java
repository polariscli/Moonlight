package org.afterlike.moonlight.platform.mixin.minecraft.client.network;

import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import org.afterlike.moonlight.apollo.ApolloHandler;
import org.afterlike.moonlight.apollo.ApolloMessageHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerPlayClient.class)
public class NetHandlerPlayClientMixin {
	@Inject(method = "handleCustomPayload", at = @At("HEAD"))
	private void moonlight$onHandleCustomPayload(S3FPacketCustomPayload packet, CallbackInfo ci) {
		String channel = packet.getChannelName();
		ApolloMessageHandler handler = ApolloMessageHandler.getInstance();
		if (channel.equals(ApolloHandler.CHANNEL_APOLLO)) {
			try {
				byte[] data = new byte[packet.getBufferData().readableBytes()];
				packet.getBufferData().getBytes(packet.getBufferData().readerIndex(), data);
				handler.handleProtobufMessage(data);
			} catch (Exception ignored) {
			}
		} else if (channel.equals(ApolloHandler.CHANNEL_APOLLO_JSON)) {
			try {
				byte[] data = new byte[packet.getBufferData().readableBytes()];
				packet.getBufferData().getBytes(packet.getBufferData().readerIndex(), data);
				handler.handleJsonMessage(data);
			} catch (Exception ignored) {
			}
		}
	}
}
