package org.afterlike.moonlight.version;

import com.google.gson.Gson;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MoonlightConfig {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final Gson GSON = new Gson();
	private static final long STALE_MS = 24 * 60 * 60 * 1000L;
	public String lunarVersion;
	public String lunarGitCommit;
	public long fetchedAt;
	private MoonlightConfig() {
	}

	public boolean isStale() {
		return lunarVersion == null || lunarGitCommit == null
				|| (System.currentTimeMillis() - fetchedAt) > STALE_MS;
	}

	private static File getCacheFile() {
		File dir = new File(Minecraft.getMinecraft().mcDataDir, "moonlight");
		dir.mkdirs();
		return new File(dir, "version_cache.json");
	}

	public static MoonlightConfig load() {
		File file = getCacheFile();
		if (file.exists()) {
			try (Reader reader = new InputStreamReader(Files.newInputStream(file.toPath()),
					StandardCharsets.UTF_8)) {
				MoonlightConfig cfg = GSON.fromJson(reader, MoonlightConfig.class);
				if (cfg != null && cfg.lunarVersion != null && cfg.lunarGitCommit != null) {
					return cfg;
				}
			} catch (Exception e) {
				LOGGER.warn("Failed to load version cache: {}", e.getMessage());
			}
		}
		return new MoonlightConfig();
	}

	public void save() {
		try (Writer writer = new OutputStreamWriter(Files.newOutputStream(getCacheFile().toPath()),
				StandardCharsets.UTF_8)) {
			GSON.toJson(this, writer);
		} catch (Exception e) {
			LOGGER.warn("Failed to save version cache: {}", e.getMessage());
		}
	}
}
