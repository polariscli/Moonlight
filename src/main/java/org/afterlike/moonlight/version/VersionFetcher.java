package org.afterlike.moonlight.version;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.UUID;
import java.util.zip.Inflater;
import org.afterlike.moonlight.Moonlight;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class VersionFetcher {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final String LAUNCH_API = "https://api.lunarclientprod.com/launcher/launch";
	private static final String USER_AGENT = "LunarClient/3.3.3";
	private static final Gson GSON = new Gson();
	private final MoonlightConfig config;
	public VersionFetcher(MoonlightConfig config) {
		this.config = config;
	}

	public void fetchAsync() {
		Thread t = new Thread(this::fetch, "Moonlight-VersionFetch");
		t.setDaemon(true);
		t.start();
	}

	public void fetch() {
		try {
			String jarUrl = fetchLunarJarUrl();
			if (jarUrl == null)
				return;
			long jarSize = fetchContentLength(jarUrl);
			if (jarSize <= 0) {
				LOGGER.warn("Could not determine lunar.jar size, using fallback");
				return;
			}
			Properties buildData = extractBuildData(jarUrl, jarSize);
			if (buildData == null)
				return;
			String version = buildData.getProperty("lunarVersion");
			String fullHash = buildData.getProperty("fullGitHash");
			if (version == null || fullHash == null) {
				LOGGER.warn("lunarBuildData.txt missing version fields, using fallback");
				return;
			}
			config.lunarVersion = version;
			config.lunarGitCommit = fullHash;
			config.fetchedAt = System.currentTimeMillis();
			config.save();
			Moonlight.get().setLunarVersion(version, fullHash);
		} catch (Exception e) {
			LOGGER.warn("Version fetch failed ({}), using fallback", e.getMessage());
		}
	}

	private String fetchLunarJarUrl() throws IOException {
		JsonObject body = new JsonObject();
		body.addProperty("hwid", "0");
		body.addProperty("hwid_private", "0");
		body.addProperty("os", detectOs());
		body.addProperty("arch", detectArch());
		body.addProperty("launcher_version", "3.3.3");
		body.addProperty("version", "1.8.9");
		body.addProperty("branch", "master");
		body.addProperty("launch_type", "OFFLINE");
		body.addProperty("installation_id", UUID.randomUUID().toString());
		body.addProperty("os_release", System.getProperty("os.version", "6.1.0"));
		body.addProperty("module", "lunar");
		byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
		HttpURLConnection conn = (HttpURLConnection) new URL(LAUNCH_API).openConnection();
		conn.setRequestMethod("POST");
		conn.setDoOutput(true);
		conn.setConnectTimeout(10000);
		conn.setReadTimeout(10000);
		conn.setRequestProperty("User-Agent", USER_AGENT);
		conn.setRequestProperty("Content-Type", "application/json");
		conn.setRequestProperty("Accept", "application/json");
		try (OutputStream os = conn.getOutputStream()) {
			os.write(payload);
		}
		if (conn.getResponseCode() != 200) {
			LOGGER.warn("Launch API returned {}, using fallback", conn.getResponseCode());
			return null;
		}
		try (Reader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
			JsonObject root = GSON.fromJson(reader, JsonObject.class);
			JsonObject data = root.getAsJsonObject("launchTypeData");
			if (data == null)
				return null;
			JsonArray artifacts = data.getAsJsonArray("artifacts");
			if (artifacts == null)
				return null;
			for (JsonElement el : artifacts) {
				JsonObject art = el.getAsJsonObject();
				String name = art.has("name") ? art.get("name").getAsString() : "";
				if ("lunar.jar".equals(name)) {
					return art.get("url").getAsString();
				}
			}
		}
		LOGGER.warn("lunar.jar not found in launch API response, using fallback");
		return null;
	}

	private long fetchContentLength(String url) throws IOException {
		HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
		conn.setRequestMethod("HEAD");
		conn.setRequestProperty("User-Agent", USER_AGENT);
		conn.setConnectTimeout(5000);
		conn.setReadTimeout(5000);
		conn.connect();
		return conn.getContentLengthLong();
	}

	private Properties extractBuildData(String url, long totalSize) throws Exception {
		long tailStart = Math.max(0, totalSize - 65536);
		byte[] tail = fetchRange(url, tailStart, totalSize - 1);
		int eocdPos = findEocdSignature(tail);
		if (eocdPos < 0) {
			LOGGER.warn("ZIP EOCD not found in lunar.jar, using fallback");
			return null;
		}
		ByteBuffer eocd = ByteBuffer.wrap(tail).order(ByteOrder.LITTLE_ENDIAN);
		eocd.position(eocdPos + 12);
		int cdSize = eocd.getInt();
		int cdOffset = eocd.getInt();
		byte[] cd = fetchRange(url, cdOffset, (long) cdOffset + cdSize - 1);
		ByteBuffer cdb = ByteBuffer.wrap(cd).order(ByteOrder.LITTLE_ENDIAN);
		while (cdb.remaining() > 46 && cdb.getInt() == 0x02014b50) {
			cdb.position(cdb.position() + 6);
			int method = cdb.getShort() & 0xFFFF;
			cdb.position(cdb.position() + 8);
			int compSize = cdb.getInt();
			cdb.position(cdb.position() + 4);
			int nameLen = cdb.getShort() & 0xFFFF;
			int extraLen = cdb.getShort() & 0xFFFF;
			int commentLen = cdb.getShort() & 0xFFFF;
			cdb.position(cdb.position() + 8);
			int localOffset = cdb.getInt();
			byte[] nameBytes = new byte[nameLen];
			cdb.get(nameBytes);
			String name = new String(nameBytes, StandardCharsets.UTF_8);
			if (name.equals("lunarBuildData.txt")) {
				return readZipEntry(url, localOffset, compSize, method);
			}
			cdb.position(cdb.position() + extraLen + commentLen);
		}
		LOGGER.warn("lunarBuildData.txt not found in lunar.jar, using fallback");
		return null;
	}

	private Properties readZipEntry(String url, int localOffset, int compSize, int method)
			throws Exception {
		byte[] local = fetchRange(url, localOffset, (long) localOffset + 30 + 256 + compSize);
		ByteBuffer hdr = ByteBuffer.wrap(local).order(ByteOrder.LITTLE_ENDIAN);
		hdr.position(26);
		int nameLen = hdr.getShort() & 0xFFFF;
		int extraLen = hdr.getShort() & 0xFFFF;
		int dataStart = 30 + nameLen + extraLen;
		byte[] raw = new byte[compSize];
		System.arraycopy(local, dataStart, raw, 0, compSize);
		if (method == 8) {
			Inflater inf = new Inflater(true);
			inf.setInput(raw);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[1024];
			while (!inf.finished()) {
				int n = inf.inflate(buf);
				out.write(buf, 0, n);
			}
			inf.end();
			raw = out.toByteArray();
		}
		Properties props = new Properties();
		props.load(new java.io.ByteArrayInputStream(raw));
		return props;
	}

	private static int findEocdSignature(byte[] data) {
		for (int i = data.length - 22; i >= 0; i--) {
			if (data[i] == 0x50 && data[i + 1] == 0x4b && data[i + 2] == 0x05
					&& data[i + 3] == 0x06) {
				return i;
			}
		}
		return -1;
	}

	private byte[] fetchRange(String url, long start, long end) throws IOException {
		HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
		conn.setRequestProperty("User-Agent", USER_AGENT);
		conn.setRequestProperty("Range", "bytes=" + start + "-" + end);
		conn.setConnectTimeout(10000);
		conn.setReadTimeout(10000);
		conn.connect();
		try (InputStream is = conn.getInputStream()) {
			return readAll(is);
		}
	}

	private static byte[] readAll(InputStream is) throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		byte[] tmp = new byte[8192];
		int n;
		while ((n = is.read(tmp)) != -1) {
			buf.write(tmp, 0, n);
		}
		return buf.toByteArray();
	}

	private static String detectOs() {
		String os = System.getProperty("os.name", "").toLowerCase();
		if (os.contains("mac") || os.contains("darwin"))
			return "darwin";
		if (os.contains("win"))
			return "win32";
		return "linux";
	}

	private static String detectArch() {
		String arch = System.getProperty("os.arch", "").toLowerCase();
		if (arch.contains("aarch64") || arch.contains("arm64"))
			return "arm64";
		return "x64";
	}
}
