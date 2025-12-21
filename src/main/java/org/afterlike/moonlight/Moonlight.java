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
	public static final @NotNull String LUNAR_GIT_COMMIT = "c0c266ce58d2c9a9b7d18fade6086ca9dbc5b22b";
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