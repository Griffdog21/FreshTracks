package com.freshtracks.client.render;

import com.freshtracks.FreshTracks;
import com.freshtracks.net.TrailMapPayload;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** Trail map pictures the server sent (in pieces), turned into textures for the boards. */
public final class TrailMaps {
	/** A received map: its texture and the world area it shows (picture bottom-left = (x0, z0), top-right = (x1, z1)). */
	public record Map2(Identifier texture, double x0, double z0, double x1, double z1) {
	}

	private static final Map<String, Map2> MAPS = new HashMap<>();
	private static final Map<String, byte[][]> PENDING = new HashMap<>();

	private TrailMaps() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(TrailMapPayload.TYPE, (payload, context) -> receive(payload));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(TrailMaps::clear));
	}

	public static @Nullable Map2 get(String mapId) {
		return mapId.isEmpty() ? null : MAPS.get(mapId);
	}

	private static void receive(TrailMapPayload p) {
		if (p.parts() < 1 || p.parts() > 64 || p.part() < 0 || p.part() >= p.parts()) {
			return;
		}
		byte[][] pieces = PENDING.computeIfAbsent(p.mapId(), k -> new byte[p.parts()][]);
		if (pieces.length != p.parts()) {
			pieces = new byte[p.parts()][];
			PENDING.put(p.mapId(), pieces);
		}
		pieces[p.part()] = p.data();
		for (byte[] piece : pieces) {
			if (piece == null) {
				return;
			}
		}
		PENDING.remove(p.mapId());
		ByteArrayOutputStream all = new ByteArrayOutputStream();
		for (byte[] piece : pieces) {
			all.writeBytes(piece);
		}
		try {
			NativeImage image = NativeImage.read(all.toByteArray());
			String path = "trail_map/" + p.mapId().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
			Identifier id = FreshTracks.id(path);
			Minecraft mc = Minecraft.getInstance();
			mc.getTextureManager().register(id, new DynamicTexture(() -> "freshtracks trail map " + p.mapId(), image));
			MAPS.put(p.mapId(), new Map2(id, p.x0(), p.z0(), p.x1(), p.z1()));
		} catch (Exception e) {
			FreshTracks.LOG.warn("Couldn't read the trail map {} from the server", p.mapId(), e);
		}
	}

	private static void clear() {
		Minecraft mc = Minecraft.getInstance();
		for (Map2 map : MAPS.values()) {
			mc.getTextureManager().release(map.texture());
		}
		MAPS.clear();
		PENDING.clear();
	}
}
