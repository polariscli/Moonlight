package org.afterlike.moonlight.peers;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

public final class PeerRegistry {
	private static final PeerRegistry INSTANCE = new PeerRegistry();
	private final ConcurrentHashMap<UUID, PeerData> peers = new ConcurrentHashMap<>();
	private PeerRegistry() {
	}

	public static PeerRegistry getInstance() {
		return INSTANCE;
	}

	public void update(PeerData data) {
		peers.put(data.uuid, data);
	}

	@Nullable public PeerData get(UUID uuid) {
		return peers.get(uuid);
	}

	void remove(UUID uuid) {
		peers.remove(uuid);
	}

	void clear() {
		peers.clear();
	}
}
