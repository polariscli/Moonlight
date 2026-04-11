package org.afterlike.moonlight.peers;

import java.util.UUID;

public class PeerData {
	public final UUID uuid;
	/** Logo tint color components, each in [0, 1]. */
	public final float r, g, b, a;
	/** Whether the logo is shown without the player being hovered. */
	public final boolean logoAlwaysShow;
	/** Tablist badge icon ID; 0 = no badge. */
	public final int badgeId;
	public PeerData(UUID uuid, float r, float g, float b, float a, boolean logoAlwaysShow,
			int badgeId) {
		this.uuid = uuid;
		this.r = r;
		this.g = g;
		this.b = b;
		this.a = a;
		this.logoAlwaysShow = logoAlwaysShow;
		this.badgeId = badgeId;
	}
}
