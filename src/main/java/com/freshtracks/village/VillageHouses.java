package com.freshtracks.village;

import com.freshtracks.FreshTracks;
import com.freshtracks.mixin.StructureTemplatePoolAccessor;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

/**
 * Adds the Ski Shop and Board Shop houses to snowy villages. The pools are added to in memory when a server starts
 * (instead of replacing vanilla's pool files), so other village mods keep working and nothing is saved.
 */
public final class VillageHouses {
	private static final int WEIGHT = 5;

	private VillageHouses() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTING.register(VillageHouses::addHouses);
	}

	private static void addHouses(MinecraftServer server) {
		if (!com.freshtracks.FreshTracksConfig.villageShops) {
			return;
		}
		Registry<StructureTemplatePool> pools = server.registryAccess().lookupOrThrow(Registries.TEMPLATE_POOL);
		StructureTemplatePool snowy = pools.getValue(Identifier.withDefaultNamespace("village/snowy/houses"));
		if (snowy == null) {
			FreshTracks.LOG.warn("No snowy village house pool; ski and board shops won't appear in villages");
			return;
		}
		add(snowy, "village/ski_shop");
		add(snowy, "village/board_shop");
		FreshTracks.LOG.info("Ski and board shops added to snowy villages ({} house slots)", ((StructureTemplatePoolAccessor) snowy).freshtracks$templates().size());
	}

	private static void add(StructureTemplatePool pool, String template) {
		StructurePoolElement element = StructurePoolElement.legacy(FreshTracks.id(template).toString()).apply(StructureTemplatePool.Projection.RIGID);
		var templates = ((StructureTemplatePoolAccessor) pool).freshtracks$templates();
		for (int i = 0; i < WEIGHT; i++) {
			templates.add(element);
		}
	}
}
