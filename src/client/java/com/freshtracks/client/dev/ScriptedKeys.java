package com.freshtracks.client.dev;

import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.Screenshot;

/**
 * Holds movement keys down from a script for a number of ticks (dev bridge only).
 *
 * <p>A timeline is a list of steps "keys:ticks[@shot]" separated by ';' where keys is any of f b l r j s g
 * (forward, back, left, right, jump, sneak, grab) or '-' for none. A step with @name saves a screenshot
 * halfway through it.
 */
final class ScriptedKeys {
	private record Step(boolean f, boolean b, boolean l, boolean r, boolean j, boolean s, boolean g, int ticks, String shot, String until) {
	}

	private final Deque<Step> queue = new ArrayDeque<>();
	private Step current;
	private int left;
	private int total;

	synchronized String set(Map<String, String> q) {
		queue.clear();
		String keys = (on(q, "f") ? "f" : "") + (on(q, "b") ? "b" : "") + (on(q, "l") ? "l" : "") + (on(q, "r") ? "r" : "")
			+ (on(q, "j") ? "j" : "") + (on(q, "s") ? "s" : "") + (on(q, "g") ? "g" : "");
		queue.add(parse(keys + ":" + q.getOrDefault("ticks", "20")));
		current = null;
		return "{\"ok\":true}";
	}

	synchronized String timeline(String spec) {
		queue.clear();
		current = null;
		int ticks = 0;
		for (String part : spec.split(";")) {
			if (!part.isBlank()) {
				Step step = parse(part.trim());
				queue.add(step);
				ticks += step.ticks();
			}
		}
		return "{\"ok\":true,\"ticks\":" + ticks + "}";
	}

	private static Step parse(String part) {
		String shot = null;
		int at = part.indexOf('@');
		if (at >= 0) {
			shot = part.substring(at + 1);
			part = part.substring(0, at);
		}
		String[] kv = part.split(":");
		String k = kv[0];
		String until = null;
		int ticks;
		if (kv.length > 1 && (kv[1].equals("air") || kv[1].equals("ground"))) {
			// hold these keys until the rig leaves / reaches the ground (at most 30 s)
			until = kv[1];
			ticks = 600;
		} else {
			ticks = kv.length > 1 ? Integer.parseInt(kv[1]) : 20;
		}
		return new Step(k.contains("f"), k.contains("b"), k.contains("l"), k.contains("r"), k.contains("j"), k.contains("s"), k.contains("g"), ticks, shot, until);
	}

	private static boolean on(Map<String, String> q, String key) {
		String v = q.get(key);
		return v != null && (v.equals("1") || v.equalsIgnoreCase("true"));
	}

	synchronized String describe() {
		if (current == null && queue.isEmpty()) {
			return "idle";
		}
		return "running (" + (queue.size() + (current == null ? 0 : 1)) + " steps left)";
	}

	synchronized void tick(Minecraft mc) {
		Options o = mc.options;
		if (current == null || left <= 0) {
			current = queue.poll();
			if (current == null) {
				if (total > 0) {
					release(o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keyShift, o.keyAttack);
					total = 0;
				}
				return;
			}
			left = current.ticks();
			total = current.ticks();
		}
		o.keyUp.setDown(current.f());
		o.keyDown.setDown(current.b());
		o.keyLeft.setDown(current.l());
		o.keyRight.setDown(current.r());
		o.keyJump.setDown(current.j());
		o.keyShift.setDown(current.s());
		o.keyAttack.setDown(current.g());
		if (current.until() != null) {
			boolean air = mc.player != null && mc.player.getVehicle() instanceof com.freshtracks.rig.RigEntity rig && !rig.motion.grounded;
			if (current.until().equals("air") == air) {
				if (current.shot() != null) {
					takeShot(mc, current.shot());
				}
				left = 0;
				return;
			}
		} else if (current.shot() != null && left == Math.max(1, total / 2)) {
			takeShot(mc, current.shot());
		}
		left--;
	}

	private static void takeShot(Minecraft mc, String name) {
		try {
			Path dir = mc.gameDirectory.toPath().resolve("screenshots");
			Files.createDirectories(dir);
			Path file = dir.resolve(name.replaceAll("[^A-Za-z0-9_\\-]", "_") + ".png");
			Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), 1, (NativeImage image) -> {
				try (image) {
					image.writeToFile(file);
				} catch (Exception ignored) {
				}
			});
		} catch (Exception ignored) {
		}
	}

	private static void release(KeyMapping... keys) {
		for (KeyMapping key : keys) {
			key.setDown(false);
		}
	}
}
