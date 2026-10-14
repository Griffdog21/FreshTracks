package com.freshtracks;

import com.freshtracks.command.FreshTracksCommand;
import com.freshtracks.lift.LiftManager;
import com.freshtracks.weather.SnowWeather;
import com.freshtracks.world.ModWorldgen;
import com.freshtracks.net.ModNetworking;
import com.freshtracks.registry.ModBlockEntities;
import com.freshtracks.registry.ModEffects;
import com.freshtracks.registry.ModParticles;
import com.freshtracks.registry.ModBlocks;
import com.freshtracks.registry.ModComponents;
import com.freshtracks.registry.ModCreativeTab;
import com.freshtracks.registry.ModEntities;
import com.freshtracks.registry.ModItems;
import com.freshtracks.registry.ModSounds;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FreshTracks implements ModInitializer {
	public static final String MOD_ID = "freshtracks";
	public static final Logger LOG = LoggerFactory.getLogger("Fresh Tracks");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		FreshTracksConfig.load();
		ModSounds.init();
		ModComponents.init();
		ModEffects.init();
		ModParticles.init();
		ModEntities.init();
		ModItems.init();
		ModBlocks.init();
		ModBlockEntities.init();
		ModWorldgen.init();
		com.freshtracks.village.ModVillagers.init();
		com.freshtracks.village.VillageHouses.init();
		com.freshtracks.race.RaceTiming.init();
		com.freshtracks.advancement.ModCriteria.init();
		ModCreativeTab.init();
		ModNetworking.init();
		FreshTracksCommand.init();
		com.freshtracks.dev.BiomeMapCommand.init();
		com.freshtracks.dev.ResortBuildCommands.init();
		LiftManager.init();
		SnowWeather.init();
		com.freshtracks.weather.SlopeGrooming.init();
		LOG.info("Fresh Tracks is waxing its skis");
	}
}
