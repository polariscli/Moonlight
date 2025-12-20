package org.afterlike.moonlight.platform.mixin.minecraftforge.fml.common.network.handshake;

import io.netty.channel.ChannelHandlerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;
import org.afterlike.moonlight.Moonlight;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NetworkDispatcher.class)
public abstract class NetworkDispatcherMixin {
	@Shadow(remap = false)
	@Final
	public NetworkManager manager;
	@Shadow(remap = false)
	public abstract void abortClientHandshake(String type);
	@Unique private boolean moonlight$handshakeAborted = false;
	@Inject(method = "channelRead0*", at = @At("HEAD"), cancellable = true, remap = false)
	private void moonlight$onChannelRead0(ChannelHandlerContext ctx, Packet<?> msg,
			CallbackInfo ci) {
		if (moonlight$handshakeAborted || Minecraft.getMinecraft().isSingleplayer()) {
			return;
		}
		// If it's an FML handshake payload, abort as vanilla immediately
		if (msg instanceof S3FPacketCustomPayload) {
			S3FPacketCustomPayload payload = (S3FPacketCustomPayload) msg;
			String ch = payload.getChannelName();
			if ("FML|HS".equals(ch) || "FML|MP".equals(ch)) {
				Moonlight.getLogger().info("Server sent FML handshake, completing as VANILLA");
				moonlight$handshakeAborted = true;
				abortClientHandshake("VANILLA");
				// Don't cancel - let it go to handleVanilla
				return;
			}
		}
		if (msg instanceof S01PacketJoinGame) {
			Moonlight.getLogger().info("Received JoinGame packet, completing as VANILLA");
		} else {
			Moonlight.getLogger().info("Non-FML packet detected, completing as VANILLA");
		}
		moonlight$handshakeAborted = true;
		abortClientHandshake("VANILLA");
		ctx.fireChannelRead(msg);
		ci.cancel();
	}

	@Inject(method = "handleClientSideCustomPacket", at = @At("HEAD"), cancellable = true,
			remap = false)
	private void moonlight$onHandleClientSideCustomPacket(S3FPacketCustomPayload msg,
			ChannelHandlerContext context, CallbackInfoReturnable<Boolean> cir) {
		if (!Minecraft.getMinecraft().isSingleplayer()) {
			String channelName = msg.getChannelName();
			if (moonlight$handshakeAborted
					&& ("FML|HS".equals(channelName) || "FML|MP".equals(channelName))) {
				return;
			}
			// Block FML handshake packets if we haven't aborted yet
			if ("FML|HS".equals(channelName) || "FML|MP".equals(channelName)) {
				cir.setReturnValue(true); // Pretend we handled it
			}
		}
	}
}
