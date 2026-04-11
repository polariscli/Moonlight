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
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

public final class SubscriptionManager {
	private static SubscriptionManager instance;
	private final Set<UUID> subscribedSet = new HashSet<>();
	private boolean active;
	private WSClient wsClient;
	private SubscriptionManager() {
	}

	public static void init() {
		instance = new SubscriptionManager();
		MinecraftForge.EVENT_BUS.register(instance);
	}

	public static void connect(String jwt) {
		if (instance == null)
			return;
		if (instance.wsClient != null) {
			instance.wsClient.disconnect();
		}
		instance.subscribedSet.clear();
		PeerRegistry.getInstance().clear();
		instance.wsClient = new WSClient();
		instance.wsClient.connect(jwt);
	}

	@SubscribeEvent
	public void onWorldLoad(WorldEvent.Load event) {
		if (event.world.isRemote)
			active = true;
	}

	@SubscribeEvent
	public void onWorldUnload(WorldEvent.Unload event) {
		if (event.world.isRemote)
			cleanupAndDeactivate();
	}

	@SubscribeEvent
	public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
		cleanupAndDeactivate();
	}

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.START || !active || wsClient == null
				|| !wsClient.isConnected())
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
		// Subscribe to players who appeared in the tab list.
		List<UUID> toSubscribe = new ArrayList<>();
		for (UUID uuid : current) {
			if (!subscribedSet.contains(uuid)) {
				toSubscribe.add(uuid);
			}
		}
		if (!toSubscribe.isEmpty()) {
			subscribedSet.addAll(toSubscribe);
			wsClient.subscribeV2(toSubscribe);
		}
		// Unsubscribe from players who left the tab list.
		List<UUID> toUnsubscribe = new ArrayList<>();
		for (UUID uuid : subscribedSet) {
			if (!current.contains(uuid)) {
				toUnsubscribe.add(uuid);
			}
		}
		if (!toUnsubscribe.isEmpty()) {
			toUnsubscribe.forEach(subscribedSet::remove);
			wsClient.unsubscribe(toUnsubscribe);
			toUnsubscribe.forEach(u -> PeerRegistry.getInstance().remove(u));
		}
	}

	private void cleanupAndDeactivate() {
		subscribedSet.clear();
		PeerRegistry.getInstance().clear();
		active = false;
	}
}
