package org.afterlike.moonlight.peers;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslHandler;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;
import org.afterlike.moonlight.Moonlight;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class WSClient {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final String HOST = "websocket.lunarclientprod.com";
	private static final int PORT = 443;
	private static final String PATH = "/game";
	private static final String COSMETIC_SERVICE = "lunarclient.websocket.cosmetic.v2.CosmeticService";
	private static final String HEARTBEAT_SERVICE = "lunarclient.websocket.heartbeat.v1.HeartbeatService";
	private static final long MAX_BACKOFF_MS = 30_000;
	private static final long HEARTBEAT_INTERVAL_MS = 60_000;
	private final AtomicInteger requestCounter = new AtomicInteger(1);
	private final Set<String> pendingLoginIds = ConcurrentHashMap.newKeySet();
	private volatile Channel channel;
	private volatile boolean wsReady = false;
	private volatile boolean shouldReconnect = false;
	private volatile ScheduledFuture<?> heartbeatTask;
	private NioEventLoopGroup group;
	private Runnable onReconnect;
	public boolean isConnected() {
		return wsReady && channel != null && channel.isActive();
	}

	public void setOnReconnect(Runnable onReconnect) {
		this.onReconnect = onReconnect;
	}

	public void connect(Callable<String> jwtFetcher) {
		if (isConnected())
			return;
		shouldReconnect = true;
		Thread t = new Thread(() -> {
			long backoff = 2000;
			while (shouldReconnect) {
				group = new NioEventLoopGroup(1);
				try {
					String jwt = jwtFetcher.call();
					establishConnection(jwt);
				} catch (Exception e) {
					LOGGER.warn("Connection failed: {}", e.getMessage());
				} finally {
					wsReady = false;
					stopHeartbeat();
					channel = null;
					group.shutdownGracefully(0, 5, TimeUnit.SECONDS);
				}
				if (!shouldReconnect)
					break;
				LOGGER.info("Reconnecting in {}ms...", backoff);
				try {
					Thread.sleep(backoff);
				} catch (InterruptedException e) {
					break;
				}
				backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
			}
		}, "Moonlight-WS");
		t.setDaemon(true);
		t.start();
	}

	private void establishConnection(String jwt) throws Exception {
		URI uri = new URI("wss://" + HOST + PATH);
		DefaultHttpHeaders wsHeaders = new DefaultHttpHeaders();
		wsHeaders.add("Accept", "application/x-protobuf");
		WebSocketClientHandshaker hs = WebSocketClientHandshakerFactory.newHandshaker(uri,
				WebSocketVersion.V13, null, false, wsHeaders);
		ChannelFuture cf = new Bootstrap().group(group).channel(NioSocketChannel.class)
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
				.handler(new ChannelInitializer<SocketChannel>() {
					@Override
					protected void initChannel(SocketChannel ch) throws Exception {
						SSLEngine engine = SSLContext.getDefault().createSSLEngine(HOST, PORT);
						engine.setUseClientMode(true);
						SSLParameters sslParams = engine.getSSLParameters();
						sslParams.setEndpointIdentificationAlgorithm("HTTPS");
						engine.setSSLParameters(sslParams);
						SslHandler sslHandler = new SslHandler(engine);
						sslHandler.setHandshakeTimeoutMillis(10_000);
						WsHandler wsHandler = new WsHandler(hs, jwt);
						sslHandler.handshakeFuture().addListener(f -> {
							if (f.isSuccess()) {
								hs.handshake(ch);
							} else {
								LOGGER.warn("SSL failed", f.cause());
								ch.close();
							}
						});
						ch.pipeline().addLast(sslHandler).addLast(new HttpClientCodec())
								.addLast(new HttpObjectAggregator(65536)).addLast(wsHandler);
					}
				}).connect(HOST, PORT).sync();
		channel = cf.channel();
		channel.closeFuture().sync();
	}

	public void disconnect() {
		shouldReconnect = false;
		wsReady = false;
		stopHeartbeat();
		if (channel != null)
			channel.close();
	}

	private void startHeartbeat() {
		stopHeartbeat();
		heartbeatTask = channel.eventLoop().scheduleAtFixedRate(() -> {
			if (!isConnected())
				return;
			try {
				send(HEARTBEAT_SERVICE, "GameHeartbeat", new byte[0]);
			} catch (IOException e) {
				LOGGER.warn("Heartbeat failed: {}", e.getMessage());
			}
		}, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
	}

	private void stopHeartbeat() {
		ScheduledFuture<?> task = heartbeatTask;
		if (task != null) {
			task.cancel(false);
			heartbeatTask = null;
		}
	}

	public void loadTabLogos(Collection<UUID> uuids) {
		if (!isConnected() || uuids.isEmpty())
			return;
		try {
			send(COSMETIC_SERVICE, "LoadTabLogos", encodeUuidList(uuids));
			LOGGER.info("LoadTabLogos for {} player(s)", uuids.size());
		} catch (IOException e) {
			LOGGER.warn("loadTabLogos encode failed: {}", e.getMessage());
		}
	}

	private String send(String service, String method, byte[] input) throws IOException {
		String id = Integer.toString(requestCounter.getAndIncrement());
		byte[] msg = encodeServerboundMessage(id.getBytes(StandardCharsets.UTF_8), service, method,
				input);
		channel.writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(msg)));
		return id;
	}

	private static byte[] encodeHandshake(String jwt) throws IOException {
		Session session = Minecraft.getMinecraft().getSession();
		UUID uuid = session.getProfile().getId();
		ByteArrayOutputStream uuidBuf = new ByteArrayOutputStream();
		CodedOutputStream uuidOut = CodedOutputStream.newInstance(uuidBuf);
		uuidOut.writeFixed64(1, uuid.getMostSignificantBits());
		uuidOut.writeFixed64(2, uuid.getLeastSignificantBits());
		uuidOut.flush();
		ByteArrayOutputStream playerBuf = new ByteArrayOutputStream();
		CodedOutputStream playerOut = CodedOutputStream.newInstance(playerBuf);
		playerOut.writeBytes(1, ByteString.copyFrom(uuidBuf.toByteArray()));
		playerOut.writeString(2, session.getUsername());
		playerOut.flush();
		ByteArrayOutputStream identityBuf = new ByteArrayOutputStream();
		CodedOutputStream identityOut = CodedOutputStream.newInstance(identityBuf);
		identityOut.writeBytes(1, ByteString.copyFrom(playerBuf.toByteArray()));
		identityOut.writeInt32(2, 1); // TYPE_MICROSOFT
		identityOut.writeString(3, jwt);
		identityOut.flush();
		ByteArrayOutputStream versionBuf = new ByteArrayOutputStream();
		CodedOutputStream versionOut = CodedOutputStream.newInstance(versionBuf);
		versionOut.writeString(1, Moonlight.get().getLunarVersion());
		versionOut.flush();
		ByteArrayOutputStream langBuf = new ByteArrayOutputStream();
		CodedOutputStream langOut = CodedOutputStream.newInstance(langBuf);
		langOut.writeString(1, "eng");
		langOut.writeString(2, "en_US");
		langOut.flush();
		ByteArrayOutputStream mcVerBuf = new ByteArrayOutputStream();
		CodedOutputStream mcVerOut = CodedOutputStream.newInstance(mcVerBuf);
		mcVerOut.writeString(1, "V1_8");
		mcVerOut.flush();
		String fullHash = Moonlight.get().getLunarGitCommit();
		String shortHash = fullHash.length() >= 7 ? fullHash.substring(0, 7) : fullHash;
		ByteArrayOutputStream lcVerBuf = new ByteArrayOutputStream();
		CodedOutputStream lcVerOut = CodedOutputStream.newInstance(lcVerBuf);
		lcVerOut.writeString(1, "dev");
		lcVerOut.writeString(2, shortHash);
		lcVerOut.writeString(3, Moonlight.get().getLunarVersion());
		lcVerOut.flush();
		ByteArrayOutputStream gameBuf = new ByteArrayOutputStream();
		CodedOutputStream gameOut = CodedOutputStream.newInstance(gameBuf);
		gameOut.writeBytes(1, ByteString.copyFrom(mcVerBuf.toByteArray()));
		gameOut.writeBytes(2, ByteString.copyFrom(lcVerBuf.toByteArray()));
		gameOut.flush();
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		CodedOutputStream out = CodedOutputStream.newInstance(buf);
		out.writeBytes(1, ByteString.copyFrom(identityBuf.toByteArray()));
		out.writeBytes(2, ByteString.copyFrom(versionBuf.toByteArray()));
		out.writeString(5, detectOs());
		out.writeString(6, detectArch());
		out.writeBytes(7, ByteString.copyFrom(langBuf.toByteArray()));
		out.writeBytes(8, ByteString.copyFrom(gameBuf.toByteArray()));
		out.flush();
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

	private static byte[] encodeServerboundMessage(byte[] requestId, String service, String method,
			byte[] input) throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		CodedOutputStream out = CodedOutputStream.newInstance(buf);
		out.writeBytes(1, ByteString.copyFrom(requestId));
		out.writeString(2, service);
		out.writeString(3, method);
		out.writeBytes(4, ByteString.copyFrom(input));
		out.flush();
		return buf.toByteArray();
	}

	private static byte[] encodeUuidList(Collection<UUID> uuids) throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		CodedOutputStream out = CodedOutputStream.newInstance(buf);
		for (UUID uuid : uuids) {
			ByteArrayOutputStream uuidBuf = new ByteArrayOutputStream();
			CodedOutputStream uuidOut = CodedOutputStream.newInstance(uuidBuf);
			uuidOut.writeFixed64(1, uuid.getMostSignificantBits());
			uuidOut.writeFixed64(2, uuid.getLeastSignificantBits());
			uuidOut.flush();
			out.writeBytes(1, ByteString.copyFrom(uuidBuf.toByteArray()));
		}
		out.flush();
		return buf.toByteArray();
	}

	private static UUID decodeUuid(byte[] bytes) throws IOException {
		CodedInputStream in = CodedInputStream.newInstance(bytes);
		long high = 0, low = 0;
		while (!in.isAtEnd()) {
			int tag = in.readTag();
			switch (tag >>> 3) {
				case 1 :
					high = in.readFixed64();
					break;
				case 2 :
					low = in.readFixed64();
					break;
				default :
					in.skipField(tag);
			}
		}
		return new UUID(high, low);
	}

	private static int decodePackedColor(byte[] bytes) throws IOException {
		CodedInputStream in = CodedInputStream.newInstance(bytes);
		while (!in.isAtEnd()) {
			int tag = in.readTag();
			if ((tag >>> 3) == 1)
				return in.readInt32();
			in.skipField(tag);
		}
		return 0;
	}

	/**
	 * Parse LoadTabLogosResponse (field 1 = repeated TabLogo). Each TabLogo: field
	 * 1 = Uuid player_uuid, field 2 = Color logo_color, field 4 = int32 badge_id.
	 */
	private static void parseLoadTabLogosResponse(byte[] bytes) throws IOException {
		CodedInputStream in = CodedInputStream.newInstance(bytes);
		while (!in.isAtEnd()) {
			int tag = in.readTag();
			if ((tag >>> 3) == 1) {
				parseTabLogo(in.readBytes().toByteArray());
			} else {
				in.skipField(tag);
			}
		}
	}

	private static void parseTabLogo(byte[] bytes) throws IOException {
		CodedInputStream in = CodedInputStream.newInstance(bytes);
		UUID uuid = null;
		float r = 1f, g = 1f, b = 1f, a = 1f;
		int badgeId = 0;
		while (!in.isAtEnd()) {
			int tag = in.readTag();
			switch (tag >>> 3) {
				case 1 :
					uuid = decodeUuid(in.readBytes().toByteArray());
					break;
				case 2 : {
					int packed = decodePackedColor(in.readBytes().toByteArray());
					int rawA = (packed >> 24) & 0xFF;
					a = rawA == 0 ? 1f : rawA / 255.0f;
					r = ((packed >> 16) & 0xFF) / 255.0f;
					g = ((packed >> 8) & 0xFF) / 255.0f;
					b = (packed & 0xFF) / 255.0f;
					break;
				}
				case 3 :
					in.readBytes(); // plus_color — unused
					break;
				case 4 :
					badgeId = in.readInt32();
					break;
				default :
					in.skipField(tag);
			}
		}
		if (uuid != null) {
			PeerRegistry.getInstance().update(new PeerData(uuid, r, g, b, a, true, badgeId));
		}
	}

	private static void parseLoginResponse(byte[] bytes) throws IOException {
		CodedInputStream in = CodedInputStream.newInstance(bytes);
		float r = 1f, g = 1f, b = 1f, a = 1f;
		boolean hasLogoColor = false;
		boolean logoAlwaysShow = false;
		while (!in.isAtEnd()) {
			int tag = in.readTag();
			switch (tag >>> 3) {
				case 1 : {
					// logo_color = field 1 (v2 LoginResponse)
					CodedInputStream color = CodedInputStream
							.newInstance(in.readBytes().toByteArray());
					while (!color.isAtEnd()) {
						int ct = color.readTag();
						if ((ct >>> 3) == 1) {
							hasLogoColor = true;
							int packed = color.readInt32();
							int rawA = (packed >> 24) & 0xFF;
							a = rawA == 0 ? 1f : rawA / 255.0f;
							r = ((packed >> 16) & 0xFF) / 255.0f;
							g = ((packed >> 8) & 0xFF) / 255.0f;
							b = (packed & 0xFF) / 255.0f;
						} else {
							color.skipField(ct);
						}
					}
					break;
				}
				case 2 :
					// logo_always_show = field 2 (v2 LoginResponse)
					logoAlwaysShow = in.readBool();
					break;
				default :
					in.skipField(tag);
			}
		}
		if (!hasLogoColor) {
			LOGGER.warn("No Lunar cosmetics configured on this account");
			return;
		}
		// Register ourselves in PeerRegistry so our own icon shows in the tab list.
		UUID self = Minecraft.getMinecraft().getSession().getProfile().getId();
		PeerRegistry.getInstance().update(new PeerData(self, r, g, b, a, logoAlwaysShow, 0));
		LOGGER.info("CosmeticService.Login successful, presence registered");
	}

	private void parseClientbound(byte[] bytes) {
		try {
			CodedInputStream in = CodedInputStream.newInstance(bytes);
			while (!in.isAtEnd()) {
				int tag = in.readTag();
				if ((tag >>> 3) == 1) {
					CodedInputStream rpc = CodedInputStream
							.newInstance(in.readBytes().toByteArray());
					String requestId = null;
					byte[] output = null;
					while (!rpc.isAtEnd()) {
						int t = rpc.readTag();
						switch (t >>> 3) {
							case 1 :
								requestId = new String(rpc.readBytes().toByteArray(),
										StandardCharsets.UTF_8);
								break;
							case 2 :
								output = rpc.readBytes().toByteArray();
								break;
							default :
								rpc.skipField(t);
						}
					}
					if (output != null) {
						if (requestId != null && pendingLoginIds.remove(requestId)) {
							parseLoginResponse(output);
						} else {
							parseLoadTabLogosResponse(output);
						}
					}
				} else {
					in.skipField(tag);
				}
			}
		} catch (Exception e) {
			LOGGER.warn("Failed to parse frame: {}", e.getMessage());
		}
	}
	private class WsHandler extends SimpleChannelInboundHandler<Object> {
		private final WebSocketClientHandshaker handshaker;
		private final String jwt;
		WsHandler(WebSocketClientHandshaker handshaker, String jwt) {
			this.handshaker = handshaker;
			this.jwt = jwt;
		}

		@Override
		protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
			if (!handshaker.isHandshakeComplete()) {
				handshaker.finishHandshake(ctx.channel(), (FullHttpResponse) msg);
				ctx.channel().writeAndFlush(
						new BinaryWebSocketFrame(Unpooled.wrappedBuffer(encodeHandshake(jwt))))
						.addListener(f -> {
							if (f.isSuccess()) {
								wsReady = true;
								LOGGER.info("Ready");
								startHeartbeat();
								if (onReconnect != null) {
									onReconnect.run();
								}
								// Register our presence so peers can see our cosmetics.
								try {
									String loginId = send(COSMETIC_SERVICE, "Login", new byte[0]);
									pendingLoginIds.add(loginId);
									LOGGER.info("CosmeticService.Login sent (id={})", loginId);
								} catch (IOException ex) {
									LOGGER.warn("CosmeticService.Login failed: {}",
											ex.getMessage());
								}
							} else {
								LOGGER.warn("Handshake send failed", f.cause());
								ctx.close();
							}
						});
				return;
			}
			if (msg instanceof BinaryWebSocketFrame) {
				BinaryWebSocketFrame frame = (BinaryWebSocketFrame) msg;
				byte[] bytes = new byte[frame.content().readableBytes()];
				frame.content().readBytes(bytes);
				parseClientbound(bytes);
			} else if (msg instanceof CloseWebSocketFrame) {
				CloseWebSocketFrame f = (CloseWebSocketFrame) msg;
				LOGGER.info("Closed: {} {}", f.statusCode(), f.reasonText());
				ctx.channel().close();
			}
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
			LOGGER.warn("Channel error: {}", cause.getMessage());
			ctx.close();
		}
	}
}
