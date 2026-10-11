package com.freshtracks.registry;

import com.freshtracks.FreshTracks;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class ModCreativeTab {
	public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, FreshTracks.id("main"),
		FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.freshtracks"))
			.icon(() -> new ItemStack(ModItems.ALPINE_SKIS))
			.displayItems((params, output) -> {
				for (Item item : ModItems.CREATIVE_ORDER) {
					output.accept(item);
				}
			})
			.build());

	private ModCreativeTab() {
	}

	public static void init() {
	}
}
