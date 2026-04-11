package org.afterlike.moonlight.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

public final class PeerIcon {
	private static final ResourceLocation TEXTURE = new ResourceLocation("moonlight",
			"textures/lunar_logo.png");
	private PeerIcon() {
	}

	public static void draw(int x, int y, int size, float r, float g, float b, float a) {
		GlStateManager.enableBlend();
		GlStateManager.blendFunc(770, 771);
		GlStateManager.color(r, g, b, a);
		Minecraft.getMinecraft().getTextureManager().bindTexture(TEXTURE);
		Gui.drawScaledCustomSizeModalRect(x, y, 0, 0, 100, 100, size, size, 100, 100);
		GlStateManager.color(1f, 1f, 1f, 1f);
	}
}
