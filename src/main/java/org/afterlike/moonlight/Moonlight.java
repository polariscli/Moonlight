package org.afterlike.moonlight;

import java.util.Objects;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Moonlight {
	private static final @NotNull Logger logger = LogManager.getLogger(Moonlight.class);
	private static final @NotNull Moonlight instance = new Moonlight();
	private static final @NotNull String VERSION = org.afterlike.moonlight.BuildConstants.VERSION;
	private volatile @Nullable String lunarVersion = emptyToNull(BuildConstants.LUNAR_VERSION);
	private volatile @Nullable String lunarGitCommit = emptyToNull(BuildConstants.LUNAR_GIT_COMMIT);
	private volatile @Nullable String clientBrand = lunarVersion != null
			? "lunarclient:" + lunarVersion
			: null;
	private volatile @Nullable String authenticatorJwt;
	private volatile @Nullable UUID authenticatedUuid;
	private volatile long modStatusSeed;
	private Moonlight() {
	}

	public static @NotNull Moonlight get() {
		return Objects.requireNonNull(instance);
	}

	public @NotNull String getVersion() {
		return VERSION;
	}

	public @Nullable String getLunarVersion() {
		return lunarVersion;
	}

	public @Nullable String getLunarGitCommit() {
		return lunarGitCommit;
	}

	public @Nullable String getClientBrand() {
		return clientBrand;
	}

	public void setLunarVersion(@NotNull String version, @NotNull String gitCommit) {
		this.lunarVersion = version;
		this.lunarGitCommit = gitCommit;
		this.clientBrand = "lunarclient:" + version;
		logger.info("Updated spoof target: Lunar Client {} ({})", version,
				gitCommit.substring(0, Math.min(7, gitCommit.length())));
	}

	public @Nullable String getAuthenticatorJwt() {
		return authenticatorJwt;
	}

	public @Nullable UUID getAuthenticatedUuid() {
		return authenticatedUuid;
	}

	public void setAuthenticatorJwt(@NotNull String jwt, @NotNull UUID uuid) {
		this.authenticatorJwt = jwt;
		this.authenticatedUuid = uuid;
	}

	public long getModStatusSeed() {
		return modStatusSeed;
	}

	public void setModStatusSeed(long seed) {
		this.modStatusSeed = seed;
	}

	public void clearAuth() {
		this.authenticatorJwt = null;
		this.authenticatedUuid = null;
	}

	private static @Nullable String emptyToNull(@Nullable String s) {
		return s != null && !s.isEmpty() ? s : null;
	}
}
