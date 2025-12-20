package org.afterlike.moonlight;

import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Moonlight {
	private static final @NotNull Logger logger = LogManager.getLogger(Moonlight.class);
	private static final @Nullable Moonlight instance = new Moonlight();
	private static final @NotNull String VERSION = org.afterlike.moonlight.BuildConstants.VERSION;
	public static final @NotNull String LUNAR_VERSION = "v2.21.20-2551";
	public static final @NotNull String LUNAR_SEMVER = "2.21.20";
	public static final @NotNull String CLIENT_BRAND = "lunarclient:" + LUNAR_VERSION;
	public Moonlight() {
	}

	public @NotNull String getVersion() {
		return VERSION;
	}

	public static @NotNull Moonlight get() {
		return Objects.requireNonNull(instance);
	}

	public static @NotNull Logger getLogger() {
		return logger;
	}
}