package org.afterlike.moonlight.peers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.afterlike.moonlight.Moonlight;
import org.afterlike.moonlight.peers.auth.AuthClient;

public final class SubscriptionManager {
	private static SubscriptionManager instance;
	private final Set<UUID> fetchedSet = new HashSet<>();
	private WSClient wsClient;
	private SubscriptionManager() {
	}

	public static void init() {
		instance = new SubscriptionManager();
		MinecraftForge.EVENT_BUS.register(instance);
	}

	public static boolean isConnected() {
		return instance != null && instance.wsClient != null && instance.wsClient.isConnected();
	}

	/**
	 * Disconnects any existing WS and creates a fresh connection. Called on first
	 * auth and on account switch.
	 */
	public static void connect(String jwt) {
		if (instance == null)
			return;
		if (instance.wsClient != null) {
			instance.wsClient.disconnect();
		}
		instance.fetchedSet.clear();
		PeerRegistry.getInstance().clear();
		instance.wsClient = new WSClient();
		instance.wsClient.setOnReconnect(() -> {
			instance.fetchedSet.clear();
		});
		instance.wsClient.connect(() -> {
			String freshJwt = AuthClient.fetchJwt();
			Moonlight.get().setAuthenticatorJwt(freshJwt,
					Minecraft.getMinecraft().getSession().getProfile().getId());
			return freshJwt;
		});
	}

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.START || wsClient == null || !wsClient.isConnected())
			return;
		syncWithTabList();
	}

	private void syncWithTabList() {
		Minecraft mc = Minecraft.getMinecraft();
		NetHandlerPlayClient netHandler = mc.getNetHandler();
		if (netHandler == null || mc.thePlayer == null)
			return;
		UUID self = mc.thePlayer.getUniqueID();
		Collection<NetworkPlayerInfo> tabList = netHandler.getPlayerInfoMap();
		Set<UUID> current = new HashSet<>();
		for (NetworkPlayerInfo info : tabList) {
			UUID uuid = info.getGameProfile().getId();
			if (!uuid.equals(self)) {
				current.add(uuid);
			}
		}
		List<UUID> toFetch = new ArrayList<>();
		for (UUID uuid : current) {
			if (!fetchedSet.contains(uuid)) {
				toFetch.add(uuid);
			}
		}
		if (!toFetch.isEmpty()) {
			fetchedSet.addAll(toFetch);
			wsClient.loadTabLogos(toFetch);
		}
		List<UUID> left = new ArrayList<>();
		for (UUID uuid : fetchedSet) {
			if (!current.contains(uuid)) {
				left.add(uuid);
			}
		}
		if (!left.isEmpty()) {
			left.forEach(fetchedSet::remove);
			left.forEach(u -> PeerRegistry.getInstance().remove(u));
		}
	}
}
