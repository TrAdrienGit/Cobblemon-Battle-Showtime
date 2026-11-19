package com.mystaria.showtime.ambiance.blocks;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block ids read from stadium files (2026-11-19): {@code stone}, {@code minecraft:oak_log[axis=x]}. Parsed once each;
 * an id that names no block logs once and gives the fallback, so a typo in a resource pack never breaks a battle.
 */
final class Palette {

	private static final Logger LOG = LoggerFactory.getLogger("cobblemon_battle_showtime");
	private static final Map<String, BlockState> PARSED = new ConcurrentHashMap<>();
	private static final BlockState MISSING = Blocks.AIR.defaultBlockState();

	private Palette() {
	}

	static BlockState block(String id, BlockState fallback) {
		if (id == null || id.isBlank()) {
			return fallback;
		}
		BlockState state = PARSED.computeIfAbsent(id, key -> {
			try {
				return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), key, false).blockState();
			} catch (Exception ex) {
				LOG.warn("Stadium block '{}' names no block: {}", key, ex.getMessage());
				return MISSING;
			}
		});
		return state == MISSING ? fallback : state;
	}

	static BlockState[] blocks(List<String> ids, BlockState fallback) {
		if (ids == null || ids.isEmpty()) {
			return new BlockState[] {fallback};
		}
		BlockState[] out = new BlockState[ids.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = block(ids.get(i), fallback);
		}
		return out;
	}

	/** One of {@code states}, by a 0..1 pick. */
	static BlockState pick(BlockState[] states, float t) {
		return states[Math.min(states.length - 1, (int) (t * states.length))];
	}

	// ---------------------------------------------------------------- flowers by colour

	/** Minecraft's flowers by the colour they read as from afar, for an ambiance's flowers given as colours. */
	private static final Object[][] FLOWERS = {
			{0xED302C, new String[] {"minecraft:poppy", "minecraft:red_tulip"}},
			{0xFFEC4F, new String[] {"minecraft:dandelion"}},
			{0x2FBAF7, new String[] {"minecraft:blue_orchid"}},
			{0x466AEB, new String[] {"minecraft:cornflower"}},
			{0xB878EC, new String[] {"minecraft:allium"}},
			{0xF08D2C, new String[] {"minecraft:orange_tulip", "minecraft:torchflower"}},
			{0xEEB4D9, new String[] {"minecraft:pink_tulip", "minecraft:pink_petals"}},
			{0xF7F7F2, new String[] {"minecraft:oxeye_daisy", "minecraft:lily_of_the_valley", "minecraft:white_tulip",
					"minecraft:azure_bluet"}},
			{0x2A2030, new String[] {"minecraft:wither_rose"}},
	};

	/** A flower that reads as {@code rgb}: the nearest colour's, one of its kinds by {@code t} (0..1). */
	static BlockState flower(int rgb, float t) {
		Object[] best = FLOWERS[0];
		long bestDistance = Long.MAX_VALUE;
		for (Object[] flower : FLOWERS) {
			int c = (int) flower[0];
			long dr = ((c >> 16) & 255) - ((rgb >> 16) & 255);
			long dg = ((c >> 8) & 255) - ((rgb >> 8) & 255);
			long db = (c & 255) - (rgb & 255);
			long d = 3 * dr * dr + 4 * dg * dg + 2 * db * db;
			if (d < bestDistance) {
				bestDistance = d;
				best = flower;
			}
		}
		String[] kinds = (String[]) best[1];
		return block(kinds[Math.min(kinds.length - 1, (int) (t * kinds.length))], Blocks.POPPY.defaultBlockState());
	}
}
