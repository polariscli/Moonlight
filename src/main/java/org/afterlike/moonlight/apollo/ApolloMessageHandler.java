package org.afterlike.moonlight.apollo;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.lunarclient.apollo.beam.v1.DisplayBeaconBeamMessage;
import com.lunarclient.apollo.beam.v1.RemoveBeaconBeamMessage;
import com.lunarclient.apollo.beam.v1.ResetBeaconBeamsMessage;
import com.lunarclient.apollo.border.v1.DisplayBorderMessage;
import com.lunarclient.apollo.border.v1.RemoveBorderMessage;
import com.lunarclient.apollo.border.v1.ResetBordersMessage;
import com.lunarclient.apollo.chat.v1.DisplayLiveChatMessageMessage;
import com.lunarclient.apollo.chat.v1.RemoveLiveChatMessageMessage;
import com.lunarclient.apollo.coloredfire.v1.OverrideColoredFireMessage;
import com.lunarclient.apollo.coloredfire.v1.ResetColoredFireMessage;
import com.lunarclient.apollo.coloredfire.v1.ResetColoredFiresMessage;
import com.lunarclient.apollo.configurable.v1.ConfigurableSettings;
import com.lunarclient.apollo.configurable.v1.OverrideConfigurableSettingsMessage;
import com.lunarclient.apollo.cooldown.v1.DisplayCooldownMessage;
import com.lunarclient.apollo.cooldown.v1.RemoveCooldownMessage;
import com.lunarclient.apollo.cooldown.v1.ResetCooldownsMessage;
import com.lunarclient.apollo.entity.v1.FlipEntityMessage;
import com.lunarclient.apollo.entity.v1.OverrideRainbowSheepMessage;
import com.lunarclient.apollo.entity.v1.ResetFlipedEntityMessage;
import com.lunarclient.apollo.entity.v1.ResetRainbowSheepMessage;
import com.lunarclient.apollo.glow.v1.OverrideGlowEffectMessage;
import com.lunarclient.apollo.glow.v1.ResetGlowEffectMessage;
import com.lunarclient.apollo.glow.v1.ResetGlowEffectsMessage;
import com.lunarclient.apollo.hologram.v1.DisplayHologramMessage;
import com.lunarclient.apollo.hologram.v1.RemoveHologramMessage;
import com.lunarclient.apollo.hologram.v1.ResetHologramsMessage;
import com.lunarclient.apollo.limb.v1.HideArmorPiecesMessage;
import com.lunarclient.apollo.limb.v1.HideBodyPartMessage;
import com.lunarclient.apollo.limb.v1.ResetArmorPiecesMessage;
import com.lunarclient.apollo.limb.v1.ResetBodyPartMessage;
import com.lunarclient.apollo.nametag.v1.OverrideNametagMessage;
import com.lunarclient.apollo.nametag.v1.ResetNametagMessage;
import com.lunarclient.apollo.nametag.v1.ResetNametagsMessage;
import com.lunarclient.apollo.nickhider.v1.OverrideNickHiderMessage;
import com.lunarclient.apollo.nickhider.v1.ResetNickHiderMessage;
import com.lunarclient.apollo.notification.v1.DisplayNotificationMessage;
import com.lunarclient.apollo.notification.v1.ResetNotificationsMessage;
import com.lunarclient.apollo.paynow.v1.OpenPayNowEmbeddedCheckoutMessage;
import com.lunarclient.apollo.player.v1.UpdatePlayerWorldMessage;
import com.lunarclient.apollo.richpresence.v1.OverrideServerRichPresenceMessage;
import com.lunarclient.apollo.richpresence.v1.ResetServerRichPresenceMessage;
import com.lunarclient.apollo.staffmod.v1.DisableStaffModsMessage;
import com.lunarclient.apollo.staffmod.v1.EnableStaffModsMessage;
import com.lunarclient.apollo.stopwatch.v1.ResetStopwatchMessage;
import com.lunarclient.apollo.stopwatch.v1.StartStopwatchMessage;
import com.lunarclient.apollo.stopwatch.v1.StopStopwatchMessage;
import com.lunarclient.apollo.team.v1.ResetTeamMembersMessage;
import com.lunarclient.apollo.team.v1.UpdateTeamMembersMessage;
import com.lunarclient.apollo.tebex.v1.OpenTebexEmbeddedCheckoutMessage;
import com.lunarclient.apollo.title.v1.DisplayTitleMessage;
import com.lunarclient.apollo.title.v1.ResetTitlesMessage;
import com.lunarclient.apollo.tntcountdown.v1.SetTntCountdownMessage;
import com.lunarclient.apollo.transfer.v1.PingRequest;
import com.lunarclient.apollo.transfer.v1.PingResponse;
import com.lunarclient.apollo.transfer.v1.TransferRequest;
import com.lunarclient.apollo.transfer.v1.TransferResponse;
import com.lunarclient.apollo.vignette.v1.DisplayVignetteMessage;
import com.lunarclient.apollo.vignette.v1.ResetVignetteMessage;
import com.lunarclient.apollo.waypoint.v1.DisplayWaypointMessage;
import com.lunarclient.apollo.waypoint.v1.RemoveWaypointMessage;
import com.lunarclient.apollo.waypoint.v1.ResetWaypointsMessage;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import org.afterlike.moonlight.Moonlight;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public class ApolloMessageHandler {
	private static ApolloMessageHandler instance;
	public static ApolloMessageHandler getInstance() {
		if (instance == null) {
			instance = new ApolloMessageHandler();
		}
		return instance;
	}

	private ApolloMessageHandler() {
	}

	/**
	 * Handles a protobuf message received on the lunar:apollo channel.
	 *
	 * @param data
	 *            the raw protobuf bytes
	 */
	public void handleProtobufMessage(byte[] data) {
		try {
			Any any = Any.parseFrom(data);
			handleProtobufAny(any);
		} catch (InvalidProtocolBufferException e) {
			Moonlight.getLogger().warn("Failed to parse Apollo protobuf message: {}",
					e.getMessage());
		}
	}

	/**
	 * Handles a JSON message received on the apollo:json channel.
	 *
	 * @param data
	 *            the raw JSON bytes
	 */
	public void handleJsonMessage(byte[] data) {
		try {
			String json = new String(data, StandardCharsets.UTF_8);
			JsonObject message = JsonParser.parseString(json).getAsJsonObject();
			handleJsonObject(message);
		} catch (Exception e) {
			Moonlight.getLogger().warn("Failed to parse Apollo JSON message: {}", e.getMessage());
		}
	}

	private void handleProtobufAny(@NotNull Any any) throws InvalidProtocolBufferException {
		// Module configuration
		if (any.is(OverrideConfigurableSettingsMessage.class)) {
			OverrideConfigurableSettingsMessage message = any
					.unpack(OverrideConfigurableSettingsMessage.class);
			handleOverrideConfigurableSettings(message);
		}
		// Player world updates
		else if (any.is(UpdatePlayerWorldMessage.class)) {
			UpdatePlayerWorldMessage message = any.unpack(UpdatePlayerWorldMessage.class);
			handleUpdatePlayerWorld(message);
		}
		// Waypoints
		else if (any.is(DisplayWaypointMessage.class)) {
			DisplayWaypointMessage message = any.unpack(DisplayWaypointMessage.class);
			handleDisplayWaypoint(message);
		} else if (any.is(RemoveWaypointMessage.class)) {
			RemoveWaypointMessage message = any.unpack(RemoveWaypointMessage.class);
			handleRemoveWaypoint(message);
		} else if (any.is(ResetWaypointsMessage.class)) {
			handleResetWaypoints();
		}
		// Borders
		else if (any.is(DisplayBorderMessage.class)) {
			DisplayBorderMessage message = any.unpack(DisplayBorderMessage.class);
			handleDisplayBorder(message);
		} else if (any.is(RemoveBorderMessage.class)) {
			RemoveBorderMessage message = any.unpack(RemoveBorderMessage.class);
			handleRemoveBorder(message);
		} else if (any.is(ResetBordersMessage.class)) {
			handleResetBorders();
		}
		// Notifications
		else if (any.is(DisplayNotificationMessage.class)) {
			DisplayNotificationMessage message = any.unpack(DisplayNotificationMessage.class);
			handleDisplayNotification(message);
		} else if (any.is(ResetNotificationsMessage.class)) {
			handleResetNotifications();
		}
		// Titles
		else if (any.is(DisplayTitleMessage.class)) {
			DisplayTitleMessage message = any.unpack(DisplayTitleMessage.class);
			handleDisplayTitle(message);
		} else if (any.is(ResetTitlesMessage.class)) {
			handleResetTitles();
		}
		// Holograms
		else if (any.is(DisplayHologramMessage.class)) {
			DisplayHologramMessage message = any.unpack(DisplayHologramMessage.class);
			handleDisplayHologram(message);
		} else if (any.is(RemoveHologramMessage.class)) {
			RemoveHologramMessage message = any.unpack(RemoveHologramMessage.class);
			handleRemoveHologram(message);
		} else if (any.is(ResetHologramsMessage.class)) {
			handleResetHolograms();
		}
		// Teams
		else if (any.is(UpdateTeamMembersMessage.class)) {
			UpdateTeamMembersMessage message = any.unpack(UpdateTeamMembersMessage.class);
			handleUpdateTeamMembers(message);
		} else if (any.is(ResetTeamMembersMessage.class)) {
			handleResetTeamMembers();
		}
		// Staff Mod
		else if (any.is(EnableStaffModsMessage.class)) {
			EnableStaffModsMessage message = any.unpack(EnableStaffModsMessage.class);
			handleEnableStaffMods(message);
		} else if (any.is(DisableStaffModsMessage.class)) {
			handleDisableStaffMods();
		}
		// Vignette
		else if (any.is(DisplayVignetteMessage.class)) {
			DisplayVignetteMessage message = any.unpack(DisplayVignetteMessage.class);
			handleDisplayVignette(message);
		} else if (any.is(ResetVignetteMessage.class)) {
			handleResetVignette();
		}
		// TNT Countdown
		else if (any.is(SetTntCountdownMessage.class)) {
			SetTntCountdownMessage message = any.unpack(SetTntCountdownMessage.class);
			handleSetTntCountdown(message);
		}
		// Beam
		else if (any.is(DisplayBeaconBeamMessage.class)) {
			DisplayBeaconBeamMessage message = any.unpack(DisplayBeaconBeamMessage.class);
			handleDisplayBeam(message);
		} else if (any.is(RemoveBeaconBeamMessage.class)) {
			RemoveBeaconBeamMessage message = any.unpack(RemoveBeaconBeamMessage.class);
			handleRemoveBeam(message);
		} else if (any.is(ResetBeaconBeamsMessage.class)) {
			handleResetBeams();
		}
		// Cooldown
		else if (any.is(DisplayCooldownMessage.class)) {
			DisplayCooldownMessage message = any.unpack(DisplayCooldownMessage.class);
			handleDisplayCooldown(message);
		} else if (any.is(RemoveCooldownMessage.class)) {
			RemoveCooldownMessage message = any.unpack(RemoveCooldownMessage.class);
			handleRemoveCooldown(message);
		} else if (any.is(ResetCooldownsMessage.class)) {
			handleResetCooldowns();
		}
		// Entity
		else if (any.is(FlipEntityMessage.class)) {
			FlipEntityMessage message = any.unpack(FlipEntityMessage.class);
			handleFlipEntity(message);
		} else if (any.is(ResetFlipedEntityMessage.class)) {
			ResetFlipedEntityMessage message = any.unpack(ResetFlipedEntityMessage.class);
			handleResetFlippedEntity(message);
		} else if (any.is(OverrideRainbowSheepMessage.class)) {
			OverrideRainbowSheepMessage message = any.unpack(OverrideRainbowSheepMessage.class);
			handleOverrideRainbowSheep(message);
		} else if (any.is(ResetRainbowSheepMessage.class)) {
			ResetRainbowSheepMessage message = any.unpack(ResetRainbowSheepMessage.class);
			handleResetRainbowSheep(message);
		}
		// Glow
		else if (any.is(OverrideGlowEffectMessage.class)) {
			OverrideGlowEffectMessage message = any.unpack(OverrideGlowEffectMessage.class);
			handleOverrideGlowEffect(message);
		} else if (any.is(ResetGlowEffectMessage.class)) {
			ResetGlowEffectMessage message = any.unpack(ResetGlowEffectMessage.class);
			handleResetGlowEffect(message);
		} else if (any.is(ResetGlowEffectsMessage.class)) {
			handleResetGlowEffects();
		}
		// Limb
		else if (any.is(HideArmorPiecesMessage.class)) {
			HideArmorPiecesMessage message = any.unpack(HideArmorPiecesMessage.class);
			handleHideArmorPieces(message);
		} else if (any.is(ResetArmorPiecesMessage.class)) {
			ResetArmorPiecesMessage message = any.unpack(ResetArmorPiecesMessage.class);
			handleResetArmorPieces(message);
		} else if (any.is(HideBodyPartMessage.class)) {
			HideBodyPartMessage message = any.unpack(HideBodyPartMessage.class);
			handleHideBodyPart(message);
		} else if (any.is(ResetBodyPartMessage.class)) {
			ResetBodyPartMessage message = any.unpack(ResetBodyPartMessage.class);
			handleResetBodyPart(message);
		}
		// Nametag
		else if (any.is(OverrideNametagMessage.class)) {
			OverrideNametagMessage message = any.unpack(OverrideNametagMessage.class);
			handleOverrideNametag(message);
		} else if (any.is(ResetNametagMessage.class)) {
			ResetNametagMessage message = any.unpack(ResetNametagMessage.class);
			handleResetNametag(message);
		} else if (any.is(ResetNametagsMessage.class)) {
			handleResetNametags();
		}
		// Chat
		else if (any.is(DisplayLiveChatMessageMessage.class)) {
			DisplayLiveChatMessageMessage message = any.unpack(DisplayLiveChatMessageMessage.class);
			handleDisplayLiveChatMessage(message);
		} else if (any.is(RemoveLiveChatMessageMessage.class)) {
			RemoveLiveChatMessageMessage message = any.unpack(RemoveLiveChatMessageMessage.class);
			handleRemoveLiveChatMessage(message);
		}
		// Colored Fire
		else if (any.is(OverrideColoredFireMessage.class)) {
			OverrideColoredFireMessage message = any.unpack(OverrideColoredFireMessage.class);
			handleOverrideColoredFire(message);
		} else if (any.is(ResetColoredFireMessage.class)) {
			ResetColoredFireMessage message = any.unpack(ResetColoredFireMessage.class);
			handleResetColoredFire(message);
		} else if (any.is(ResetColoredFiresMessage.class)) {
			handleResetColoredFires();
		}
		// Stopwatch
		else if (any.is(StartStopwatchMessage.class)) {
			handleStartStopwatch();
		} else if (any.is(StopStopwatchMessage.class)) {
			handleStopStopwatch();
		} else if (any.is(ResetStopwatchMessage.class)) {
			handleResetStopwatch();
		}
		// Nick Hider
		else if (any.is(OverrideNickHiderMessage.class)) {
			OverrideNickHiderMessage message = any.unpack(OverrideNickHiderMessage.class);
			handleOverrideNickHider(message);
		} else if (any.is(ResetNickHiderMessage.class)) {
			handleResetNickHider();
		}
		// Rich Presence
		else if (any.is(OverrideServerRichPresenceMessage.class)) {
			OverrideServerRichPresenceMessage message = any
					.unpack(OverrideServerRichPresenceMessage.class);
			handleOverrideServerRichPresence(message);
		} else if (any.is(ResetServerRichPresenceMessage.class)) {
			handleResetServerRichPresence();
		}
		// PayNow
		else if (any.is(OpenPayNowEmbeddedCheckoutMessage.class)) {
			OpenPayNowEmbeddedCheckoutMessage message = any
					.unpack(OpenPayNowEmbeddedCheckoutMessage.class);
			handleOpenPayNowEmbeddedCheckout(message);
		}
		// Tebex
		else if (any.is(OpenTebexEmbeddedCheckoutMessage.class)) {
			OpenTebexEmbeddedCheckoutMessage message = any
					.unpack(OpenTebexEmbeddedCheckoutMessage.class);
			handleOpenTebexEmbeddedCheckout(message);
		}
		// Roundtrip messages (server requests client response)
		else if (any.is(TransferRequest.class)) {
			TransferRequest message = any.unpack(TransferRequest.class);
			handleTransferRequest(message);
		} else if (any.is(PingRequest.class)) {
			PingRequest message = any.unpack(PingRequest.class);
			handlePingRequest(message);
		}
		// Unknown message - log for debugging
		else {
			Moonlight.getLogger().debug("Received unknown Apollo protobuf message type: {}",
					any.getTypeUrl());
		}
	}

	private void handleJsonObject(@NotNull JsonObject message) {
		String type = message.has("@type") ? message.get("@type").getAsString() : "unknown";
		Moonlight.getLogger().debug("Received Apollo JSON message: {}", type);
		if (type.contains("OverrideConfigurableSettingsMessage")) {
			Moonlight.getLogger().info("Server sent Apollo module configuration");
		} else if (type.contains("UpdatePlayerWorldMessage")) {
			if (message.has("world")) {
				String world = message.get("world").getAsString();
				Moonlight.getLogger().debug("Server updated player world: {}", world);
			}
		} else if (type.contains("DisplayWaypointMessage")) {
			if (message.has("name")) {
				String name = message.get("name").getAsString();
				Moonlight.getLogger().debug("Server displayed waypoint: {}", name);
			}
		} else if (type.contains("RemoveWaypointMessage")) {
			if (message.has("name")) {
				String name = message.get("name").getAsString();
				Moonlight.getLogger().debug("Server removed waypoint: {}", name);
			}
		} else if (type.contains("TransferRequest")) {
			Moonlight.getLogger().debug("Server sent transfer request");
		} else if (type.contains("ResetWaypointsMessage")) {
			Moonlight.getLogger().debug("Server reset all waypoints");
		} else if (type.contains("DisplayBorderMessage") || type.contains("RemoveBorderMessage")
				|| type.contains("ResetBordersMessage")) {
			Moonlight.getLogger().debug("Server sent border message");
		} else if (type.contains("DisplayNotificationMessage")
				|| type.contains("ResetNotificationsMessage")) {
			Moonlight.getLogger().debug("Server sent notification message");
		} else if (type.contains("DisplayTitleMessage") || type.contains("ResetTitlesMessage")) {
			Moonlight.getLogger().debug("Server sent title message");
		} else if (type.contains("DisplayHologramMessage") || type.contains("RemoveHologramMessage")
				|| type.contains("ResetHologramsMessage")) {
			Moonlight.getLogger().debug("Server sent hologram message");
		} else if (type.contains("UpdateTeamMembersMessage")
				|| type.contains("ResetTeamMembersMessage")) {
			Moonlight.getLogger().debug("Server sent team message");
		} else if (type.contains("EnableStaffModsMessage")
				|| type.contains("DisableStaffModsMessage")) {
			Moonlight.getLogger().debug("Server sent staff mod message");
		} else if (type.contains("DisplayVignetteMessage")
				|| type.contains("ResetVignetteMessage")) {
			Moonlight.getLogger().debug("Server sent vignette message");
		} else if (type.contains("SetTntCountdownMessage")) {
			Moonlight.getLogger().debug("Server sent TNT countdown message");
		} else if (type.contains("DisplayBeaconBeamMessage")
				|| type.contains("RemoveBeaconBeamMessage")
				|| type.contains("ResetBeaconBeamsMessage")) {
			Moonlight.getLogger().debug("Server sent beacon beam message");
		} else if (type.contains("DisplayCooldownMessage") || type.contains("RemoveCooldownMessage")
				|| type.contains("ResetCooldownsMessage")) {
			Moonlight.getLogger().debug("Server sent cooldown message");
		} else if (type.contains("FlipEntityMessage") || type.contains("ResetFlipedEntityMessage")
				|| type.contains("OverrideRainbowSheepMessage")
				|| type.contains("ResetRainbowSheepMessage")) {
			Moonlight.getLogger().debug("Server sent entity message");
		} else if (type.contains("OverrideGlowEffectMessage")
				|| type.contains("ResetGlowEffectMessage")
				|| type.contains("ResetGlowEffectsMessage")) {
			Moonlight.getLogger().debug("Server sent glow effect message");
		} else if (type.contains("HideArmorPiecesMessage")
				|| type.contains("ResetArmorPiecesMessage") || type.contains("HideBodyPartMessage")
				|| type.contains("ResetBodyPartMessage")) {
			Moonlight.getLogger().debug("Server sent limb message");
		} else if (type.contains("OverrideNametagMessage") || type.contains("ResetNametagMessage")
				|| type.contains("ResetNametagsMessage")) {
			Moonlight.getLogger().debug("Server sent nametag message");
		} else if (type.contains("DisplayLiveChatMessageMessage")
				|| type.contains("RemoveLiveChatMessageMessage")) {
			Moonlight.getLogger().debug("Server sent live chat message");
		} else if (type.contains("OverrideColoredFireMessage")
				|| type.contains("ResetColoredFireMessage")
				|| type.contains("ResetColoredFiresMessage")) {
			Moonlight.getLogger().debug("Server sent colored fire message");
		} else if (type.contains("StartStopwatchMessage") || type.contains("StopStopwatchMessage")
				|| type.contains("ResetStopwatchMessage")) {
			Moonlight.getLogger().debug("Server sent stopwatch message");
		} else if (type.contains("OverrideNickHiderMessage")
				|| type.contains("ResetNickHiderMessage")) {
			Moonlight.getLogger().debug("Server sent nick hider message");
		} else if (type.contains("OverrideServerRichPresenceMessage")
				|| type.contains("ResetServerRichPresenceMessage")) {
			Moonlight.getLogger().debug("Server sent rich presence message");
		} else if (type.contains("OpenPayNowEmbeddedCheckoutMessage")) {
			Moonlight.getLogger().debug("Server sent PayNow checkout message");
		} else if (type.contains("OpenTebexEmbeddedCheckoutMessage")) {
			Moonlight.getLogger().debug("Server sent Tebex checkout message");
		}
	}

	private void handleOverrideConfigurableSettings(
			@NotNull OverrideConfigurableSettingsMessage message) {
		int moduleCount = message.getConfigurableSettingsCount();
		Moonlight.getLogger().info("Server configured {} Apollo module(s)", moduleCount);
		for (ConfigurableSettings setting : message.getConfigurableSettingsList()) {
			String module = setting.getApolloModule();
			boolean enabled = setting.getEnable();
			Moonlight.getLogger().debug("  - Module '{}': {}", module,
					enabled ? "enabled" : "disabled");
		}
	}

	private void handleUpdatePlayerWorld(@NotNull UpdatePlayerWorldMessage message) {
		String world = message.getWorld();
		Moonlight.getLogger().debug("Server updated player world: {}", world);
	}

	private void handleDisplayWaypoint(@NotNull DisplayWaypointMessage message) {
		String name = message.getName();
		Moonlight.getLogger().debug("Server displayed waypoint: {} at ({}, {}, {})", name,
				message.hasLocation() ? message.getLocation().getX() : "?",
				message.hasLocation() ? message.getLocation().getY() : "?",
				message.hasLocation() ? message.getLocation().getZ() : "?");
	}

	private void handleRemoveWaypoint(@NotNull RemoveWaypointMessage message) {
		String name = message.getName();
		Moonlight.getLogger().debug("Server removed waypoint: {}", name);
	}

	private void handleResetWaypoints() {
		Moonlight.getLogger().debug("Server reset all waypoints");
	}

	private void handleDisplayBorder(@NotNull DisplayBorderMessage message) {
		String id = message.getId();
		Moonlight.getLogger().debug("Server displayed border: {}", id);
	}

	private void handleRemoveBorder(@NotNull RemoveBorderMessage message) {
		String id = message.getId();
		Moonlight.getLogger().debug("Server removed border: {}", id);
	}

	private void handleResetBorders() {
		Moonlight.getLogger().debug("Server reset all borders");
	}

	private void handleDisplayNotification(@NotNull DisplayNotificationMessage message) {
		Moonlight.getLogger().debug("Server displayed notification");
	}

	private void handleResetNotifications() {
		Moonlight.getLogger().debug("Server reset all notifications");
	}

	private void handleDisplayTitle(@NotNull DisplayTitleMessage message) {
		Moonlight.getLogger().debug("Server displayed title");
	}

	private void handleResetTitles() {
		Moonlight.getLogger().debug("Server reset all titles");
	}

	private void handleDisplayHologram(@NotNull DisplayHologramMessage message) {
		String id = message.getId();
		Moonlight.getLogger().debug("Server displayed hologram: {}", id);
	}

	private void handleRemoveHologram(@NotNull RemoveHologramMessage message) {
		String id = message.getId();
		Moonlight.getLogger().debug("Server removed hologram: {}", id);
	}

	private void handleResetHolograms() {
		Moonlight.getLogger().debug("Server reset all holograms");
	}

	private void handleUpdateTeamMembers(@NotNull UpdateTeamMembersMessage message) {
		int memberCount = message.getMembersCount();
		Moonlight.getLogger().debug("Server updated team members: {} member(s)", memberCount);
	}

	private void handleResetTeamMembers() {
		Moonlight.getLogger().debug("Server reset team members");
	}

	private void handleEnableStaffMods(@NotNull EnableStaffModsMessage message) {
		int modCount = message.getStaffModsCount();
		Moonlight.getLogger().debug("Server enabled staff mods: {} mod(s)", modCount);
	}

	private void handleDisableStaffMods() {
		Moonlight.getLogger().debug("Server disabled staff mods");
	}

	private void handleDisplayVignette(@NotNull DisplayVignetteMessage message) {
		Moonlight.getLogger().debug("Server displayed vignette");
	}

	private void handleResetVignette() {
		Moonlight.getLogger().debug("Server reset vignette");
	}

	private void handleSetTntCountdown(@NotNull SetTntCountdownMessage message) {
		int ticks = message.getDurationTicks();
		Moonlight.getLogger().debug("Server set TNT countdown: {} ticks", ticks);
	}

	private void handleDisplayBeam(@NotNull DisplayBeaconBeamMessage message) {
		String id = message.getId();
		Moonlight.getLogger().debug("Server displayed beacon beam: {}", id);
	}

	private void handleRemoveBeam(@NotNull RemoveBeaconBeamMessage message) {
		String id = message.getId();
		Moonlight.getLogger().debug("Server removed beacon beam: {}", id);
	}

	private void handleResetBeams() {
		Moonlight.getLogger().debug("Server reset all beacon beams");
	}

	private void handleDisplayCooldown(@NotNull DisplayCooldownMessage message) {
		String name = message.getName();
		Moonlight.getLogger().debug("Server displayed cooldown: {}", name);
	}

	private void handleRemoveCooldown(@NotNull RemoveCooldownMessage message) {
		String name = message.getName();
		Moonlight.getLogger().debug("Server removed cooldown: {}", name);
	}

	private void handleResetCooldowns() {
		Moonlight.getLogger().debug("Server reset all cooldowns");
	}

	private void handleFlipEntity(@NotNull FlipEntityMessage message) {
		int count = message.getEntityIdsCount();
		Moonlight.getLogger().debug("Server flipped {} entity/entities", count);
	}

	private void handleResetFlippedEntity(@NotNull ResetFlipedEntityMessage message) {
		int count = message.getEntityIdsCount();
		Moonlight.getLogger().debug("Server reset {} flipped entity/entities", count);
	}

	private void handleOverrideRainbowSheep(@NotNull OverrideRainbowSheepMessage message) {
		int count = message.getEntityIdsCount();
		Moonlight.getLogger().debug("Server overrode rainbow sheep for {} entity/entities", count);
	}

	private void handleResetRainbowSheep(@NotNull ResetRainbowSheepMessage message) {
		int count = message.getEntityIdsCount();
		Moonlight.getLogger().debug("Server reset rainbow sheep for {} entity/entities", count);
	}

	private void handleOverrideGlowEffect(@NotNull OverrideGlowEffectMessage message) {
		Moonlight.getLogger().debug("Server overrode glow effect");
	}

	private void handleResetGlowEffect(@NotNull ResetGlowEffectMessage message) {
		Moonlight.getLogger().debug("Server reset glow effect");
	}

	private void handleResetGlowEffects() {
		Moonlight.getLogger().debug("Server reset all glow effects");
	}

	private void handleHideArmorPieces(@NotNull HideArmorPiecesMessage message) {
		int count = message.getArmorPiecesCount();
		Moonlight.getLogger().debug("Server hid {} armor piece(s)", count);
	}

	private void handleResetArmorPieces(@NotNull ResetArmorPiecesMessage message) {
		int count = message.getArmorPiecesCount();
		Moonlight.getLogger().debug("Server reset {} armor piece(s)", count);
	}

	private void handleHideBodyPart(@NotNull HideBodyPartMessage message) {
		int count = message.getBodyPartsCount();
		Moonlight.getLogger().debug("Server hid {} body part(s)", count);
	}

	private void handleResetBodyPart(@NotNull ResetBodyPartMessage message) {
		int count = message.getBodyPartsCount();
		Moonlight.getLogger().debug("Server reset {} body part(s)", count);
	}

	private void handleOverrideNametag(@NotNull OverrideNametagMessage message) {
		Moonlight.getLogger().debug("Server overrode nametag");
	}

	private void handleResetNametag(@NotNull ResetNametagMessage message) {
		Moonlight.getLogger().debug("Server reset nametag");
	}

	private void handleResetNametags() {
		Moonlight.getLogger().debug("Server reset all nametags");
	}

	private void handleDisplayLiveChatMessage(@NotNull DisplayLiveChatMessageMessage message) {
		int messageId = message.getMessageId();
		Moonlight.getLogger().debug("Server displayed live chat message: {}", messageId);
	}

	private void handleRemoveLiveChatMessage(@NotNull RemoveLiveChatMessageMessage message) {
		int messageId = message.getMessageId();
		Moonlight.getLogger().debug("Server removed live chat message: {}", messageId);
	}

	private void handleOverrideColoredFire(@NotNull OverrideColoredFireMessage message) {
		Moonlight.getLogger().debug("Server overrode colored fire");
	}

	private void handleResetColoredFire(@NotNull ResetColoredFireMessage message) {
		Moonlight.getLogger().debug("Server reset colored fire");
	}

	private void handleResetColoredFires() {
		Moonlight.getLogger().debug("Server reset all colored fires");
	}

	private void handleStartStopwatch() {
		Moonlight.getLogger().debug("Server started stopwatch");
	}

	private void handleStopStopwatch() {
		Moonlight.getLogger().debug("Server stopped stopwatch");
	}

	private void handleResetStopwatch() {
		Moonlight.getLogger().debug("Server reset stopwatch");
	}

	private void handleOverrideNickHider(@NotNull OverrideNickHiderMessage message) {
		String nick = message.getNick();
		Moonlight.getLogger().debug("Server overrode nick hider: {}", nick);
	}

	private void handleResetNickHider() {
		Moonlight.getLogger().debug("Server reset nick hider");
	}

	private void handleOverrideServerRichPresence(
			@NotNull OverrideServerRichPresenceMessage message) {
		String gameName = message.getGameName();
		Moonlight.getLogger().debug("Server overrode rich presence: {} - {}", gameName,
				message.getGameState());
	}

	private void handleResetServerRichPresence() {
		Moonlight.getLogger().debug("Server reset rich presence");
	}

	private void handleOpenPayNowEmbeddedCheckout(
			@NotNull OpenPayNowEmbeddedCheckoutMessage message) {
		Moonlight.getLogger().debug("Server opened PayNow embedded checkout");
	}

	private void handleOpenTebexEmbeddedCheckout(
			@NotNull OpenTebexEmbeddedCheckoutMessage message) {
		String basketIdent = message.getBasketIdent();
		Moonlight.getLogger().debug("Server opened Tebex embedded checkout: {}", basketIdent);
	}

	/**
	 * Handles a TransferRequest from the server.
	 * <p>
	 * TransferRequest is used by servers to request the client to connect to
	 * another server. The client can either ACCEPT (connects to new server) or
	 * REJECT (stays on current server).
	 * <p>
	 * If we reject, nothing happens - the player stays on the current server and
	 * the server is notified. This is safe for spoofing purposes as we don't want
	 * to actually transfer.
	 */
	private void handleTransferRequest(@NotNull TransferRequest message) {
		String serverIp = message.getServerIp();
		UUID requestId = UUID.fromString(message.getRequestId().toStringUtf8());
		Moonlight.getLogger().info("Server requested transfer to {} (request ID: {})", serverIp,
				requestId);
		sendTransferResponse(requestId, TransferResponse.Status.STATUS_REJECTED);
	}

	private void handlePingRequest(@NotNull PingRequest message) {
		UUID requestId = UUID.fromString(message.getRequestId().toStringUtf8());
		Moonlight.getLogger().debug("Server sent ping request (request ID: {})", requestId);
		sendPingResponse(requestId);
	}

	/**
	 * Sends a TransferResponse to the server.
	 *
	 * @param requestId
	 *            the request ID from the TransferRequest
	 * @param status
	 *            the response status (ACCEPTED or REJECTED)
	 */
	private void sendTransferResponse(@NotNull UUID requestId,
			@NotNull TransferResponse.Status status) {
		if (Minecraft.getMinecraft().getNetHandler() == null) {
			return;
		}
		NetworkManager manager = Minecraft.getMinecraft().getNetHandler().getNetworkManager();
		if (manager == null || !manager.isChannelOpen()) {
			return;
		}
		TransferResponse response = TransferResponse.newBuilder()
				.setRequestId(ByteString.copyFromUtf8(requestId.toString())).setStatus(status)
				.build();
		Any any = Any.pack(response);
		byte[] data = any.toByteArray();
		PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
		buffer.writeBytes(data);
		C17PacketCustomPayload packet = new C17PacketCustomPayload(ApolloHandler.CHANNEL_APOLLO,
				buffer);
		manager.sendPacket(packet);
		Moonlight.getLogger().debug("Sent transfer response: {}", status);
	}

	/**
	 * Sends a PingResponse to the server.
	 *
	 * @param requestId
	 *            the request ID from the PingRequest
	 */
	private void sendPingResponse(@NotNull UUID requestId) {
		if (Minecraft.getMinecraft().getNetHandler() == null) {
			return;
		}
		NetworkManager manager = Minecraft.getMinecraft().getNetHandler().getNetworkManager();
		if (manager == null || !manager.isChannelOpen()) {
			return;
		}
		PingResponse response = PingResponse.newBuilder()
				.setRequestId(ByteString.copyFromUtf8(requestId.toString())).build();
		Any any = Any.pack(response);
		byte[] data = any.toByteArray();
		PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
		buffer.writeBytes(data);
		C17PacketCustomPayload packet = new C17PacketCustomPayload(ApolloHandler.CHANNEL_APOLLO,
				buffer);
		manager.sendPacket(packet);
		Moonlight.getLogger().debug("Sent ping response");
	}
}
