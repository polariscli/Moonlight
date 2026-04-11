package org.afterlike.moonlight.platform.mixin.minecraft.client.gui;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import org.afterlike.moonlight.peers.PeerData;
import org.afterlike.moonlight.peers.PeerRegistry;
import org.afterlike.moonlight.render.PeerIcon;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(GuiPlayerTabOverlay.class)
public abstract class GuiPlayerTabOverlayMixin {
	@Shadow
	@Final
	private Minecraft mc;
	@Shadow
	public abstract String getPlayerName(NetworkPlayerInfo info);
	@Unique private static final int ICON_SIZE = 8;
	@Unique private static final int ICON_PAD = 2;
	@Unique private static final int ICON_SHIFT = ICON_SIZE + ICON_PAD;
	@Unique private Map<String, NetworkPlayerInfo> moonlight$nameToInfo;
	@Inject(method = "renderPlayerlist", at = @At("HEAD"))
	private void moonlight$buildNameMap(int width, Scoreboard scoreboard, ScoreObjective objective,
			CallbackInfo ci) {
		moonlight$nameToInfo = new HashMap<>();
		if (mc.thePlayer == null || mc.thePlayer.sendQueue == null)
			return;
		for (NetworkPlayerInfo info : mc.thePlayer.sendQueue.getPlayerInfoMap()) {
			moonlight$nameToInfo.put(getPlayerName(info), info);
		}
	}

	@ModifyArgs(method = "renderPlayerlist", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/FontRenderer;drawStringWithShadow(Ljava/lang/String;FFI)I"))
	private void moonlight$onDrawString(Args args) {
		if (moonlight$nameToInfo == null)
			return;
		String text = args.get(0);
		String lookup = text;
		if (lookup.length() > 2 && lookup.charAt(0) == '\u00a7' && lookup.charAt(1) == 'o') {
			lookup = lookup.substring(2);
		}
		NetworkPlayerInfo info = moonlight$nameToInfo.get(lookup);
		if (info == null)
			return;
		PeerData data = PeerRegistry.getInstance().get(info.getGameProfile().getId());
		if (data == null)
			return;
		float x = args.get(1);
		float y = args.get(2);
		PeerIcon.draw((int) x, (int) y, ICON_SIZE, data.r, data.g, data.b, data.a);
		args.set(1, x + ICON_SHIFT);
	}
}
