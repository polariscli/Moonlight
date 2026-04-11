package org.afterlike.moonlight.platform.mixin.minecraftforge.fml.common;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.afterlike.moonlight.Moonlight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FMLCommonHandler.class)
public class FMLCommonHandlerMixin {
	@Inject(method = "getModName", at = @At("HEAD"), cancellable = true, remap = false)
	private void moonlight$getModName(CallbackInfoReturnable<String> cir) {
		String brand = Moonlight.get().getClientBrand();
		if (!Minecraft.getMinecraft().isSingleplayer() && brand != null) {
			cir.setReturnValue(brand);
		}
	}
}
