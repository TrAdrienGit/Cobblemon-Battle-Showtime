package com.mystaria.showtime.ambiance.blocks;

import java.util.List;
import java.util.function.Supplier;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A stadium seen from around a battle, by someone who neither fights nor watches it (2026-11-22): the real blocks
 * round the field, dressed in the stadium's own (its surface on top, its soil under it, its rock deeper), and nothing
 * else. No block is added where there is none (a hole stays a hole, a path stays open), so the people around still
 * see where they walk; the world stays drawn, these over it.
 * <p>
 * The world is read on the client thread when the build starts; only whole blocks (full to walk on, glass included) someone can see are dressed (a
 * plant, a slab, a fence keeps its own look).
 */
final class ReskinScene {

	/** How far below and above the field's height the real blocks are dressed. */
	private static final int BELOW = 16;
	private static final int ABOVE = 24;
	/** Blocks under the surface that are soil; deeper, rock. */
	private static final int SOIL_DEPTH = 3;

	private ReskinScene() {
	}

	/**
	 * {@code checker}: a hall's dark squares ({@code tile} blocks wide) among its floor's, or null. Reads the world now;
	 * the returned build runs in the background.
	 */
	static Supplier<SceneMesh> recipe(List<String> top, List<String> checker, float tile, List<String> soil, List<String> rock,
			List<LandScene.Plants.Kind> plants, Vec3 center, double reach) {
		Level level = Minecraft.getInstance().level;
		if (level == null) {
			throw new IllegalStateException("no world to dress");
		}
		BlockState stone = SceneLevel.FILL;
		BlockState[] tops = Palette.blocks(top, stone);
		BlockState[] checkers = checker == null || checker.isEmpty() ? null : Palette.blocks(checker, stone);
		BlockState[] soils = Palette.blocks(soil, stone);
		BlockState[] rocks = Palette.blocks(rock, stone);
		int cy = Mth.floor(center.y);
		int minY = Math.max(level.getMinBuildHeight(), cy - BELOW);
		int maxY = Math.min(level.getMaxBuildHeight() - 1, cy + ABOVE);
		int r = Mth.ceil(reach);
		int x0 = Mth.floor(center.x) - r;
		int z0 = Mth.floor(center.z) - r;
		int x1 = Mth.floor(center.x) + r;
		int z1 = Mth.floor(center.z) + r;
		// Which real blocks are whole (they hide what is behind them), one block of margin round the dressed box.
		LongOpenHashSet solid = new LongOpenHashSet();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = x0 - 1; x <= x1 + 1; x++) {
			for (int z = z0 - 1; z <= z1 + 1; z++) {
				for (int y = minY - 1; y <= maxY + 1; y++) {
					pos.set(x, y, z);
					BlockState state = level.getBlockState(pos);
					// Whole as people walk on it (glass included: a hub's floor and pillars are often glass).
					if (!state.isAir() && state.isCollisionShapeFullBlock(level, pos) && state.getFluidState().isEmpty()) {
						solid.add(pos.asLong());
					}
				}
			}
		}
		pos.set(Mth.floor(center.x), cy + 1, Mth.floor(center.z));
		int sky = level.getBrightness(LightLayer.SKY, pos);
		SceneLevel out = new SceneLevel(level, level.getBiome(pos), null, level.getMinBuildHeight(), level.getHeight())
				// Beside a dressed block: the real world's solidity (no face against a hidden block); under the box,
				// rock; above it, air.
				.fallback((x, y, z) -> solid.contains(BlockPos.asLong(x, y, z)) || y < minY ? SceneLevel.FILL : SceneLevel.AIR)
				.skyLight(sky);
		double reach2 = reach * reach;
		java.util.Map<String, Integer> kinds = new java.util.HashMap<>();
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				double dx = x + 0.5 - center.x;
				double dz = z + 0.5 - center.z;
				if (dx * dx + dz * dz > reach2) {
					continue;
				}
				int depth = 0; // whole blocks above this one since the last open space
				for (int y = maxY; y >= minY; y--) {
					long key = BlockPos.asLong(x, y, z);
					if (!solid.contains(key)) {
						depth = 0;
						continue;
					}
					if (seen(solid, x, y, z)) {
						if (depth == 0) {
							kinds.merge(level.getBlockState(new BlockPos(x, y, z)).getBlock().getDescriptionId(), 1, Integer::sum);
						}
						float pick = (float) Noise.value(x * 0.18, y * 0.18, z * 0.18); // in patches, as the stadiums lay them
						BlockState dressed;
						if (depth == 0) {
							boolean dark = checkers != null && (Math.floorDiv(x, Math.max(1, (int) tile)) + Math.floorDiv(z, Math.max(1, (int) tile)) & 1) == 1;
							dressed = Palette.pick(dark ? checkers : tops, pick);
						} else {
							dressed = Palette.pick(depth <= SOIL_DEPTH ? soils : rocks, pick);
						}
						out.set(x, y, z, dressed);
						light(level, solid, out, pos, x, y, z);
						// Minecraft's own plants on top, where the real world has nothing (they hold no one back).
						if (depth == 0 && !plants.isEmpty() && level.getBlockState(pos.set(x, y + 1, z)).isAir()
								&& level.getBlockState(pos.set(x, y + 2, z)).isAir()) {
							LandScene.Plants.place(out, pos, x, y + 1, z, Math.sqrt(dx * dx + dz * dz), 1f, plants, false);
						}
					}
					depth++;
				}
			}
		}
		out.spreadLight();
		org.slf4j.LoggerFactory.getLogger("cobblemon_battle_showtime").debug(
				"Dressing round {} (y {}..{}, reach {}): {} whole blocks, {} shown, under the field {}; tops dressed {}",
				center, minY, maxY, reach, solid.size(), out.blocks.size(),
				level.getBlockState(BlockPos.containing(center.x, center.y - 0.5, center.z)), kinds);
		int originX = Mth.floor(center.x);
		int originZ = Mth.floor(center.z);
		return () -> SceneBuilder.compile(List.of(new SceneBuilder.Part(out, 1)), originX, originZ);
	}

	/** The real light round a dressed block, where its faces and their shading read it. */
	private static void light(Level level, LongOpenHashSet solid, SceneLevel out, BlockPos.MutableBlockPos pos, int x, int y, int z) {
		for (int ox = -1; ox <= 1; ox++) {
			for (int oy = -1; oy <= 2; oy++) {
				for (int oz = -1; oz <= 1; oz++) {
					long key = BlockPos.asLong(x + ox, y + oy, z + oz);
					if (!solid.contains(key)) {
						pos.set(x + ox, y + oy, z + oz);
						out.realLight(key, level.getBrightness(LightLayer.SKY, pos), level.getBrightness(LightLayer.BLOCK, pos));
					}
				}
			}
		}
	}

	/** Whether a face of this whole block shows (one of its neighbours is not whole). */
	private static boolean seen(LongOpenHashSet solid, int x, int y, int z) {
		for (Direction d : Direction.values()) {
			if (!solid.contains(BlockPos.asLong(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ()))) {
				return true;
			}
		}
		return false;
	}
}
