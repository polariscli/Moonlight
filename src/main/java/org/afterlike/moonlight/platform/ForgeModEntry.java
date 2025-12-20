package org.afterlike.moonlight.platform;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import org.afterlike.moonlight.Moonlight;
import org.afterlike.moonlight.apollo.ApolloHandler;

@Mod(modid = "moonlight", useMetadata = true)
public class ForgeModEntry {
	@Mod.EventHandler
	public void init(FMLInitializationEvent event) {
		Moonlight.getLogger().info("Moonlight {} initializing...", Moonlight.get().getVersion());
		Moonlight.getLogger().info("Spoofing as Lunar Client {}", Moonlight.LUNAR_VERSION);
		ApolloHandler.init();
	}
}
