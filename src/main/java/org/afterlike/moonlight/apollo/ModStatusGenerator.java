package org.afterlike.moonlight.apollo;

import com.google.protobuf.Value;
import com.lunarclient.apollo.player.v1.PlayerHandshakeMessage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Generates a realistic mod_status map seeded by the player's UUID. Only
 * non-default values are included, matching Lunar Client behavior.
 */
final class ModStatusGenerator {
	private ModStatusGenerator() {
	}

	static void apply(PlayerHandshakeMessage.Builder handshake, UUID playerUuid) {
		Map<String, Value> settings = generate(playerUuid);
		for (Map.Entry<String, Value> entry : settings.entrySet()) {
			handshake.putModStatus(entry.getKey(), entry.getValue());
		}
	}

	private static Map<String, Value> generate(UUID playerUuid) {
		Random rng = new Random(playerUuid.getLeastSignificantBits());
		Map<String, Value> s = new LinkedHashMap<>();
		maybeBool(s, rng, "cooldowns.enabled", false, 0.30);
		maybeBool(s, rng, "direction-hud.enabled", false, 0.35);
		maybeBool(s, rng, "horse-stats.enabled", false, 0.40);
		maybeBool(s, rng, "markers.enabled", false, 0.35);
		maybeBool(s, rng, "waypoints.enabled", false, 0.30);
		maybeBool(s, rng, "radio.enabled", false, 0.50);
		maybeBool(s, rng, "rewind.enabled", false, 0.40);
		maybeBool(s, rng, "menu-blur.enabled", false, 0.35);
		maybeBool(s, rng, "team-view.enabled", false, 0.40);
		maybeBool(s, rng, "mumble-link.enabled", false, 0.25);
		maybeBool(s, rng, "audio-subtitles.enabled", false, 0.20);
		maybeBool(s, rng, "hitbox.enabled", true, 0.55);
		maybeBool(s, rng, "crosshair.enabled", true, 0.50);
		maybeBool(s, rng, "hit-color.enabled", true, 0.45);
		maybeBool(s, rng, "particle-changer.enabled", true, 0.40);
		maybeBool(s, rng, "overlay-mod.enabled", true, 0.35);
		maybeBool(s, rng, "hurt-cam.enabled", true, 0.40);
		maybeBool(s, rng, "block-outline.enabled", true, 0.35);
		maybeBool(s, rng, "nick-hider.hide-lobby-i-d", true, 0.30);
		maybeBool(s, rng, "scoreboard.text-shadow", true, 0.45);
		maybeBool(s, rng, "scoreboard.numbers", true, 0.35);
		maybeBool(s, rng, "nametag.nametag-shadow", true, 0.40);
		maybeBool(s, rng, "chat.chat-name-bold", true, 0.30);
		maybeBool(s, rng, "chat.modern-chat-length-hypixel", true, 0.35);
		maybeBool(s, rng, "chat.chat-height", true, 0.25);
		maybeBool(s, rng, "tab.hide-n-p-c", true, 0.35);
		maybeBool(s, rng, "zoom.variable-zoom", true, 0.40);
		maybeBool(s, rng, "zoom.smooth-zoom", false, 0.30);
		maybeBool(s, rng, "zoom.smooth-camera", false, 0.35);
		maybeBool(s, rng, "freelook.smooth-camera", false, 0.30);
		maybeBool(s, rng, "hurt-cam.disable-hurt-cam", true, 0.35);
		maybeBool(s, rng, "nick-hider.hide-real-name", false, 0.25);
		maybeBool(s, rng, "bossbar.render-bar", false, 0.30);
		maybeBool(s, rng, "bossbar.use-minecraft-g-u-i-scale", false, 0.20);
		maybeBool(s, rng, "coordinates.background", false, 0.25);
		maybeBool(s, rng, "potion-effects.background", false, 0.30);
		maybeBool(s, rng, "overlay-mod.custom-fishing-line", true, 0.25);
		maybeBool(s, rng, "block-outline.block-overlay", true, 0.30);
		maybeBool(s, rng, "particle-changer.always-enchant-strikes", true, 0.30);
		if (s.containsKey("hitbox.enabled")) {
			String[] hitboxColors = {"hitbox-player-line-color", "hitbox-monster-line-color",
					"hitbox-passive-line-color", "hitbox-projectile-line-color",
					"hitbox-arrow-line-color", "hitbox-item-line-color",
					"hitbox-snowball-line-color", "hitbox-fireball-line-color",
					"hitbox-wither-skull-line-color", "hitbox-firework-line-color",
					"hitbox-exp-orb-line-color", "hitbox-item-frame-line-color",
					"hitbox-other-line-color"};
			String color = randomHitboxColor(rng);
			for (String key : hitboxColors) {
				s.put("hitbox." + key, strVal(color));
			}
		}
		maybeNumber(s, rng, "nametag.nametag-background-opacity", 0.25,
				new double[]{0.0, 0.0, 0.15, 0.25});
		maybeNumber(s, rng, "chat.chat-background-opacity", 0.20, new double[]{0.0, 0.0, 0.1});
		maybeNumber(s, rng, "block-outline.block-outline-width", 0.25,
				new double[]{3.0, 3.0, 4.0, 5.0});
		maybeNumber(s, rng, "overlay-mod.fishing-line-thickness", 0.20,
				new double[]{2.0, 2.0, 3.0});
		maybeColor(s, rng, "hit-color.hit-armor-color", 0.30,
				new String[]{"91000000", "91000000", "64ff0000", "91333333"});
		maybeColor(s, rng, "block-outline.block-outline-color", 0.25,
				new String[]{"a0000000", "ff000000", "a0333333"});
		maybeColor(s, rng, "block-outline.block-overlay-color", 0.20,
				new String[]{"46000000", "20000000", "33000000"});
		return s;
	}

	private static void maybeBool(Map<String, Value> s, Random rng, String key, boolean value,
			double probability) {
		if (rng.nextDouble() < probability) {
			s.put(key, boolVal(value));
		}
	}

	private static void maybeNumber(Map<String, Value> s, Random rng, String key,
			double probability, double[] options) {
		if (rng.nextDouble() < probability) {
			s.put(key, numVal(options[rng.nextInt(options.length)]));
		}
	}

	private static void maybeColor(Map<String, Value> s, Random rng, String key, double probability,
			String[] options) {
		if (rng.nextDouble() < probability) {
			s.put(key, strVal(options[rng.nextInt(options.length)]));
		}
	}

	private static String randomHitboxColor(Random rng) {
		String[] common = {"ff000000", "ff000000", "ff000000", "ff333333", "ffff0000", "ff00ff00"};
		return common[rng.nextInt(common.length)];
	}

	private static Value boolVal(boolean v) {
		return Value.newBuilder().setBoolValue(v).build();
	}

	private static Value numVal(double v) {
		return Value.newBuilder().setNumberValue(v).build();
	}

	private static Value strVal(String v) {
		return Value.newBuilder().setStringValue(v).build();
	}
}
