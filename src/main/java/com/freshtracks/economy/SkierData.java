package com.freshtracks.economy;

import com.freshtracks.FreshTracks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Every skier's Ski Bucks record on this world (data/freshtracks/skiers.dat), and the limited-edition counters. */
public final class SkierData extends SavedData {
	public static final Codec<SkierData> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.unboundedMap(UUIDUtil.STRING_CODEC, SkierProfile.CODEC).optionalFieldOf("skiers", Map.of()).forGetter(d -> d.skiers),
		Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("editions", Map.of()).forGetter(d -> d.editions)
	).apply(i, SkierData::new));

	public static final SavedDataType<SkierData> TYPE = new SavedDataType<>(
		FreshTracks.id("skiers"), SkierData::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<UUID, SkierProfile> skiers = new HashMap<>();
	/** How many of each limited-edition catalog item have been sold on this world. */
	private final Map<String, Integer> editions = new HashMap<>();

	public SkierData() {
	}

	private SkierData(Map<UUID, SkierProfile> skiers, Map<String, Integer> editions) {
		this.skiers.putAll(skiers);
		this.editions.putAll(editions);
	}

	public static SkierData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public SkierProfile profile(UUID id) {
		return skiers.computeIfAbsent(id, k -> {
			setDirty();
			return new SkierProfile();
		});
	}

	public int sold(String edition) {
		return editions.getOrDefault(edition, 0);
	}

	public void sell(String edition) {
		editions.merge(edition, 1, Integer::sum);
		setDirty();
	}
}
