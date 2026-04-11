package org.afterlike.moonlight.platform;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.afterlike.moonlight.Moonlight;
import org.afterlike.moonlight.apollo.ApolloHandler;
import org.afterlike.moonlight.peers.SubscriptionManager;
import org.afterlike.moonlight.peers.auth.AuthClient;
import org.afterlike.moonlight.version.MoonlightConfig;
import org.afterlike.moonlight.version.VersionFetcher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = "moonlight", useMetadata = true)
public class ForgeModEntry {
	private static final Logger LOGGER = LogManager.getLogger();
	@Mod.EventHandler
	public void init(FMLInitializationEvent event) {
		LOGGER.info("Moonlight {} initializing...", Moonlight.get().getVersion());
		MoonlightConfig config = MoonlightConfig.load();
		if (config.lunarVersion != null && config.lunarGitCommit != null) {
			Moonlight.get().setLunarVersion(config.lunarVersion, config.lunarGitCommit);
		}
		if (config.isStale()) {
			VersionFetcher fetcher = new VersionFetcher(config);
			if (Moonlight.get().getLunarVersion() == null) {
				// First launch with no cache: block so the version is available
				// before any server connection.
				fetcher.fetch();
			} else {
				fetcher.fetchAsync();
			}
		}
		ApolloHandler.init();
		SubscriptionManager.init();
		MinecraftForge.EVENT_BUS.register(this);
	}

	/**
	 * Authenticates with Lunar on every server connection. Re-authenticates if the
	 * account changed (e.g. via an account switcher mod).
	 */
	@SubscribeEvent
	public void onConnectedToServer(FMLNetworkEvent.ClientConnectedToServerEvent event) {
		UUID currentUuid = Minecraft.getMinecraft().getSession().getProfile().getId();
		if (currentUuid != null && currentUuid.equals(Moonlight.get().getAuthenticatedUuid())
				&& SubscriptionManager.isConnected()) {
			return;
		}
		Moonlight.get().clearAuth();
		AuthClient.fetchJwtAsync(jwt -> {
			Moonlight.get().setAuthenticatorJwt(jwt, currentUuid);
			SubscriptionManager.connect(jwt);
		}, err -> LOGGER.warn("JWT fetch failed, peer icons disabled", err));
	}
}
