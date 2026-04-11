package org.afterlike.moonlight.peers.auth;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
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
import java.math.BigInteger;
import java.net.URI;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AuthClient {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final String HOST = "authenticator.lunarclientprod.com";
	private static final int PORT = 443;
	private static final String PATH = "/game";
	private static final String INITIATOR = "assetServer";
	public static String fetchJwt() throws Exception {
		Session session = Minecraft.getMinecraft().getSession();
		CompletableFuture<String> jwtFuture = new CompletableFuture<>();
		NioEventLoopGroup group = new NioEventLoopGroup(1);
		try {
			URI uri = new URI("wss://" + HOST + PATH);
			DefaultHttpHeaders wsHeaders = new DefaultHttpHeaders();
			wsHeaders.add("Accept", "application/x-protobuf");
			wsHeaders.add("X-Initiator", INITIATOR);
			WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory
					.newHandshaker(uri, WebSocketVersion.V13, null, false, wsHeaders);
			Bootstrap b = new Bootstrap().group(group).channel(NioSocketChannel.class)
					.handler(new ChannelInitializer<SocketChannel>() {
						@Override
						protected void initChannel(SocketChannel ch) throws Exception {
							SSLEngine engine = SSLContext.getDefault().createSSLEngine(HOST, PORT);
							engine.setUseClientMode(true);
							SSLParameters sslParams = engine.getSSLParameters();
							sslParams.setEndpointIdentificationAlgorithm("HTTPS");
							engine.setSSLParameters(sslParams);
							SslHandler sslHandler = new SslHandler(engine);
							// MC 1.8.9's Netty (4.0.x) propagates channelActive before SSL
							// finishes, so we must wait for the SSL future before upgrading.
							sslHandler.handshakeFuture().addListener(f -> {
								if (f.isSuccess()) {
									handshaker.handshake(ch);
								} else {
									LOGGER.warn("SSL handshake failed", f.cause());
									jwtFuture.completeExceptionally(f.cause());
								}
							});
							ch.pipeline().addLast(sslHandler).addLast(new HttpClientCodec())
									.addLast(new HttpObjectAggregator(65536)).addLast(
											new AuthChannelHandler(handshaker, session, jwtFuture));
						}
					});
			ChannelFuture cf = b.connect(HOST, PORT).sync();
			try {
				return jwtFuture.get(15, TimeUnit.SECONDS);
			} finally {
				cf.channel().close().sync();
			}
		} finally {
			group.shutdownGracefully(0, 5, TimeUnit.SECONDS);
		}
	}

	public static void fetchJwtAsync(java.util.function.Consumer<String> onSuccess,
			java.util.function.Consumer<Exception> onError) {
		Thread t = new Thread(() -> {
			try {
				onSuccess.accept(fetchJwt());
			} catch (Exception e) {
				onError.accept(e);
			}
		}, "Moonlight-Auth");
		t.setDaemon(true);
		t.start();
	}
	private static class AuthChannelHandler extends SimpleChannelInboundHandler<Object> {
		private final WebSocketClientHandshaker handshaker;
		private final Session session;
		private final CompletableFuture<String> jwtFuture;
		AuthChannelHandler(WebSocketClientHandshaker handshaker, Session session,
				CompletableFuture<String> jwtFuture) {
			this.handshaker = handshaker;
			this.session = session;
			this.jwtFuture = jwtFuture;
		}

		@Override
		public void channelActive(ChannelHandlerContext ctx) {
		}

		@Override
		public void channelInactive(ChannelHandlerContext ctx) {
			if (!jwtFuture.isDone()) {
				jwtFuture.completeExceptionally(
						new Exception("Channel closed before auth completed"));
			}
		}

		@Override
		protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
			Channel ch = ctx.channel();
			if (!handshaker.isHandshakeComplete()) {
				handshaker.finishHandshake(ch, (FullHttpResponse) msg);
				sendHello(ctx);
				return;
			}
			if (msg instanceof BinaryWebSocketFrame) {
				BinaryWebSocketFrame frame = (BinaryWebSocketFrame) msg;
				byte[] bytes = new byte[frame.content().readableBytes()];
				frame.content().readBytes(bytes);
				handleClientbound(ctx, bytes);
			} else if (msg instanceof CloseWebSocketFrame) {
				CloseWebSocketFrame close = (CloseWebSocketFrame) msg;
				jwtFuture.completeExceptionally(new Exception(
						"Server closed: " + close.statusCode() + " " + close.reasonText()));
			}
		}

		private void sendHello(ChannelHandlerContext ctx) throws Exception {
			UUID uuid = session.getProfile().getId();
			byte[] payload = wrapServerbound(1, encodeHello(uuid, session.getUsername()));
			ctx.channel().writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(payload)));
		}

		private void handleClientbound(ChannelHandlerContext ctx, byte[] bytes) throws Exception {
			CodedInputStream in = CodedInputStream.newInstance(bytes);
			int tag = in.readTag();
			int field = tag >>> 3;
			byte[] inner = in.readBytes().toByteArray();
			if (field == 1) {
				handleEncryptionRequest(ctx, inner);
			} else if (field == 2) {
				handleAuthSuccess(inner);
			} else {
				LOGGER.warn("Unknown clientbound field {}", field);
			}
		}

		private void handleEncryptionRequest(ChannelHandlerContext ctx, byte[] bytes)
				throws Exception {
			byte[] serverPubKeyBytes = null;
			byte[] randomBytes = null;
			CodedInputStream in = CodedInputStream.newInstance(bytes);
			while (!in.isAtEnd()) {
				int tag = in.readTag();
				switch (tag >>> 3) {
					case 1 :
						serverPubKeyBytes = in.readBytes().toByteArray();
						break;
					case 2 :
						randomBytes = in.readBytes().toByteArray();
						break;
					default :
						in.skipField(tag);
				}
			}
			if (serverPubKeyBytes == null || randomBytes == null) {
				fail(ctx, "EncryptionRequestMessage missing required fields");
				return;
			}
			PublicKey serverKey = KeyFactory.getInstance("RSA")
					.generatePublic(new X509EncodedKeySpec(serverPubKeyBytes));
			SecretKey secretKey = KeyGenerator.getInstance("AES").generateKey();
			Cipher rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding");
			rsa.init(Cipher.ENCRYPT_MODE, serverKey);
			byte[] encryptedSecretKey = rsa.doFinal(secretKey.getEncoded());
			byte[] encryptedVerifyToken = rsa.doFinal(randomBytes);
			MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
			sha1.update(secretKey.getEncoded());
			sha1.update(serverPubKeyBytes);
			String serverId = new BigInteger(sha1.digest()).toString(16);
			Minecraft.getMinecraft().getSessionService().joinServer(session.getProfile(),
					session.getToken(), serverId);
			byte[] payload = wrapServerbound(2,
					encodeEncryptionResponse(encryptedSecretKey, encryptedVerifyToken));
			ctx.channel().writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(payload)));
		}

		private void handleAuthSuccess(byte[] bytes) throws Exception {
			CodedInputStream in = CodedInputStream.newInstance(bytes);
			while (!in.isAtEnd()) {
				int tag = in.readTag();
				if ((tag >>> 3) == 1) {
					String jwt = in.readString();
					LOGGER.info("Got Lunar Client Token for {}", session.getUsername());
					jwtFuture.complete(jwt);
					return;
				}
				in.skipField(tag);
			}
			jwtFuture.completeExceptionally(new Exception("AuthSuccessMessage missing jwt field"));
		}

		private void fail(ChannelHandlerContext ctx, String reason) throws Exception {
			byte[] payload = wrapServerbound(3, encodeEncryptionFail(reason));
			ctx.channel().writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(payload)))
					.addListener(f -> ctx.close());
			jwtFuture.completeExceptionally(new Exception(reason));
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
			jwtFuture.completeExceptionally(cause);
			ctx.close();
		}
	}
	private static byte[] wrapServerbound(int fieldNum, byte[] inner) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		CodedOutputStream out = CodedOutputStream.newInstance(baos);
		out.writeBytes(fieldNum, ByteString.copyFrom(inner));
		out.flush();
		return baos.toByteArray();
	}

	private static byte[] encodeHello(UUID uuid, String username) throws IOException {
		ByteArrayOutputStream uuidBaos = new ByteArrayOutputStream();
		CodedOutputStream uuidOut = CodedOutputStream.newInstance(uuidBaos);
		uuidOut.writeFixed64(1, uuid.getMostSignificantBits());
		uuidOut.writeFixed64(2, uuid.getLeastSignificantBits());
		uuidOut.flush();
		ByteArrayOutputStream identityBaos = new ByteArrayOutputStream();
		CodedOutputStream identityOut = CodedOutputStream.newInstance(identityBaos);
		identityOut.writeBytes(1, ByteString.copyFrom(uuidBaos.toByteArray()));
		identityOut.writeString(2, username);
		identityOut.flush();
		ByteArrayOutputStream helloBaos = new ByteArrayOutputStream();
		CodedOutputStream helloOut = CodedOutputStream.newInstance(helloBaos);
		helloOut.writeBytes(1, ByteString.copyFrom(identityBaos.toByteArray()));
		helloOut.writeString(2, INITIATOR);
		helloOut.flush();
		return helloBaos.toByteArray();
	}

	private static byte[] encodeEncryptionResponse(byte[] encryptedSecretKey,
			byte[] encryptedVerifyToken) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		CodedOutputStream out = CodedOutputStream.newInstance(baos);
		out.writeBytes(1, ByteString.copyFrom(encryptedSecretKey));
		out.writeBytes(2, ByteString.copyFrom(encryptedVerifyToken));
		out.flush();
		return baos.toByteArray();
	}

	private static byte[] encodeEncryptionFail(String reason) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		CodedOutputStream out = CodedOutputStream.newInstance(baos);
		out.writeString(1, reason);
		out.flush();
		return baos.toByteArray();
	}
}
