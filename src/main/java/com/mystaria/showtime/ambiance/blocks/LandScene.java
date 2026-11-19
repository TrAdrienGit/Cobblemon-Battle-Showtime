package com.mystaria.showtime.ambiance.blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleBinaryOperator;
import java.util.function.Supplier;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

import com.mystaria.showtime.ambiance.Ambiance;

/**
 * An outdoor stadium built of blocks (2026-11-19): the same place as the smooth one (its hills, dunes, cliffs, ranges
 * far off, trees, rocks, pools), laid block by block on the world's grid out of Minecraft's own blocks.
 * <p>
 * Near the field every block is a block. Further out, where nobody tells, a cell stands for 2x2x2 blocks, then 4x4x4
 * (the same textures, larger): the place reaches the horizon for a fraction of the faces. The parts meet on whole
 * cells, each drawing its sides along the seam, so no gap opens between them.
 * <p>
 * The world is read on the main thread (the real ground under the field, where people stand); the rest is built in
 * the background.
 */
final class LandScene {

	/** Blocks of one block within this distance of the centre; 2-block cells within {@link #MID}; 4 beyond. */
	private static final double FINE = 44;
	private static final double MID = 84;
	private static final int NONE = Integer.MIN_VALUE;
	private static final int RIM_STEPS = 720;

	private final Ambiance.Land land;
	private final Ambiance.Grass grass;
	private final Ambiance.Flowers flowers;
	private final float snowCover;
	private final boolean ablaze;
	private final double cx;
	private final double cz;
	private final double field;
	private final double from;
	private final double baseAbs;
	private final int baseFace;
	private final Long2DoubleOpenHashMap interior = new Long2DoubleOpenHashMap();
	private final double[] rim = new double[RIM_STEPS];
	private final Level host;
	private final Holder<Biome> biome;
	private final int minY;
	private final int height;
	private final double outer;

	private final BlockState[] surface;
	private final BlockState[] soil;
	private final BlockState[] rock;
	private final BlockState[] bands;
	private final BlockState far;
	private final Long2IntOpenHashMap tops = new Long2IntOpenHashMap();
	private final Long2IntOpenHashMap groundTops = new Long2IntOpenHashMap();
	/** Columns of the field-side ground that hold water (pools) or lava (cracks): their liquid. */
	private final Long2ObjectOpenHashMap<BlockState> liquids = new Long2ObjectOpenHashMap<>();

	private LandScene(Ambiance.Land land, Ambiance.Grass grass, Ambiance.Flowers flowers, float snowCover, boolean ablaze, Vec3 center,
			float fieldRadius, DoubleBinaryOperator ground) {
		this.land = land;
		this.grass = grass;
		this.flowers = flowers;
		this.snowCover = snowCover;
		this.ablaze = ablaze;
		this.cx = center.x;
		this.cz = center.z;
		this.field = fieldRadius;
		this.from = fieldRadius + 0.5;
		this.baseAbs = center.y + ground.applyAsDouble(0, 0);
		this.baseFace = (int) Math.round(baseAbs);
		// Where people stand: the real ground under the field, read now (the world is not safe to read elsewhere).
		int reach = Mth.ceil(from + 2);
		for (int bx = Mth.floor(cx - reach); bx <= Mth.floor(cx + reach); bx++) {
			for (int bz = Mth.floor(cz - reach); bz <= Mth.floor(cz + reach); bz++) {
				double x = bx + 0.5 - cx;
				double z = bz + 0.5 - cz;
				if (x * x + z * z <= (from + 1.5) * (from + 1.5)) {
					interior.put(SceneLevel.column(bx, bz), center.y + ground.applyAsDouble(x, z));
				}
			}
		}
		for (int i = 0; i < RIM_STEPS; i++) {
			double a = Math.PI * 2 * i / RIM_STEPS;
			rim[i] = center.y + ground.applyAsDouble(Math.cos(a) * from, Math.sin(a) * from);
		}
		Level level = Minecraft.getInstance().level;
		if (level == null) {
			throw new IllegalStateException("no world to build a stadium in");
		}
		this.host = level;
		var registry = level.registryAccess().registryOrThrow(Registries.BIOME);
		ResourceLocation id = ResourceLocation.tryParse(land.biome == null ? "minecraft:plains" : land.biome);
		this.biome = (id == null ? java.util.Optional.<Holder.Reference<Biome>>empty() : registry.getHolder(ResourceKey.create(Registries.BIOME, id)))
				.or(() -> registry.getHolder(Biomes.PLAINS))
				.map(holder -> (Holder<Biome>) holder)
				.orElseThrow(() -> new IllegalStateException("no plains biome"));
		this.minY = level.getMinBuildHeight();
		this.height = level.getHeight();
		Ambiance.Ground blocks = land.blocks != null ? land.blocks : new Ambiance.Ground();
		BlockState[] top = Palette.blocks(blocks.surface, Blocks.GRASS_BLOCK.defaultBlockState());
		if (ablaze) {
			top = new BlockState[] {Blocks.COARSE_DIRT.defaultBlockState(), Blocks.PODZOL.defaultBlockState(),
					Blocks.ROOTED_DIRT.defaultBlockState()};
		}
		this.surface = top;
		this.soil = Palette.blocks(blocks.soil, Blocks.DIRT.defaultBlockState());
		this.rock = Palette.blocks(blocks.rock, Blocks.STONE.defaultBlockState());
		this.bands = blocks.bands == null || blocks.bands.isEmpty() ? null : Palette.blocks(blocks.bands, Blocks.STONE.defaultBlockState());
		this.far = Palette.block(blocks.far, surface[0]);
		double reachOut = land.radius;
		if (land.ranges != null) {
			Ambiance.Ranges r = land.ranges;
			reachOut = Math.max(reachOut, r.distance * (1 + 0.22 * (r.layers - 1)) + 12 + 0.45 * r.height * 1.55 + 4 * r.layers);
		}
		this.outer = Math.min(reachOut, 220);
		tops.defaultReturnValue(NONE - 1);
		groundTops.defaultReturnValue(NONE - 1);
	}

	/** The scene, read now and built later: call on the main thread, run the result in the background. */
	static Supplier<SceneMesh> recipe(Ambiance.Land land, Ambiance.Grass grass, Ambiance.Flowers flowers, float snowCover,
			boolean ablaze, Vec3 center, float fieldRadius, DoubleBinaryOperator ground) {
		LandScene scene = new LandScene(land, grass, flowers, snowCover, ablaze, center, fieldRadius, ground);
		return scene::build;
	}

	// ---------------------------------------------------------------- the ground's shape

	/** The ground's top at a world column, full resolution: the y of the first air above it, or {@link #NONE}. */
	private int top(int bx, int bz) {
		long key = SceneLevel.column(bx, bz);
		int cached = tops.get(key);
		if (cached != NONE - 1) {
			return cached;
		}
		int t = topAt(bx, bz);
		tops.put(key, t);
		return t;
	}

	private int topAt(int bx, int bz) {
		double x = bx + 0.5 - cx;
		double z = bz + 0.5 - cz;
		double r = Math.sqrt(x * x + z * z);
		if (r <= from) {
			double real = interior.getOrDefault(SceneLevel.column(bx, bz), baseAbs);
			return Mth.floor(real + 0.25); // a path's sliver counts as a block, a slab's half does not: nobody sinks in
		}
		boolean ground = r <= land.radius;
		double peak = 0;
		double beyond = Double.NEGATIVE_INFINITY;
		if (land.ranges != null) {
			Ambiance.Ranges ranges = land.ranges;
			double a = Math.atan2(z, x);
			for (int l = 0; l < ranges.layers; l++) {
				double distance = ranges.distance * (1 + 0.22 * l);
				double ux = Math.cos(a) * 3 + l * 7;
				double uz = Math.sin(a) * 3;
				double ridge = 0.7 * Noise.value(ux, l, uz) + 0.3 * Noise.value(ux * 3.1, l + 5, uz * 3.1);
				double h = ranges.height * (0.25 + Math.pow(ridge, 1.6) * 1.3) * (1 - 0.15 * l);
				h *= 0.8 + 0.4 * Noise.value(x * 0.045, 40 + l, z * 0.045);
				double half = 12 + 0.45 * h + 4 * l;
				double bump = 1 - Math.abs(r - distance) / half;
				if (bump <= 0) {
					continue;
				}
				double s = Noise.smooth(bump);
				peak = Math.max(peak, h * s);
				beyond = Math.max(beyond, -30 + (h + 30) * s);
			}
		}
		if (!ground) {
			// Past the ground's edge, only the mountains, rising from far below.
			return beyond < -26 ? NONE : baseFace + (int) Math.floor(beyond + 0.5);
		}
		double rimY = rim[Math.floorMod((int) Math.round(Math.atan2(z, x) / (Math.PI * 2) * RIM_STEPS), RIM_STEPS)];
		double t = Noise.smooth(Mth.clamp((r - from) / Math.max(1, land.flat), 0, 1));
		double smoothY = Mth.lerp(t, rimY, baseAbs + relief(x, z, r - from - land.flat) + peak);
		return baseFace + (int) Math.floor(smoothY - baseAbs + 0.5);
	}

	/** The relief past the level ground, as the smooth stadium has it: hills, dunes, a summit's fall, a marsh's dips. */
	private double relief(double x, double z, double out) {
		double o = Math.max(0, out);
		double grow = Mth.clamp(o / 12, 0, 1);
		switch (land.style) {
			case "dunes": {
				double crest = 0.5 + 0.5 * Math.sin(x * 0.07 + z * 0.03 + 4 * Noise.value(x * 0.02, 2, z * 0.02));
				return land.hills * crest * crest * grow;
			}
			case "summit":
				return -Math.pow(o, 1.35) * 0.55 - land.hills * Noise.value(x * 0.08, 4, z * 0.08) * grow;
			case "marsh":
				return land.hills * (Noise.value(x * 0.05, 5, z * 0.05) - 0.5) * grow;
			default:
				return land.hills * 2 * (0.75 * Noise.value(x * 0.035, 6, z * 0.035) + 0.25 * Noise.value(x * 0.09, 7, z * 0.09) - 0.3)
						* grow;
		}
	}

	/** Which part draws a world column: 1 (blocks), 2 or 4 (cells of so many blocks). Whole cells, so parts nest. */
	private int region(int bx, int bz) {
		double r4 = distance(Math.floorDiv(bx, 4) * 4 + 2, Math.floorDiv(bz, 4) * 4 + 2);
		if (r4 > MID) {
			return 4;
		}
		double r2 = distance(Math.floorDiv(bx, 2) * 2 + 1, Math.floorDiv(bz, 2) * 2 + 1);
		return r2 > FINE ? 2 : 1;
	}

	private double distance(double wx, double wz) {
		return Math.sqrt((wx - cx) * (wx - cx) + (wz - cz) * (wz - cz));
	}

	/** A cell's top, in cells: the ground at its middle, to the nearest whole cell. */
	private int cellTop(int scale, int cellX, int cellZ) {
		int t = top(cellX * scale + scale / 2, cellZ * scale + scale / 2);
		return t == NONE ? NONE : Math.round(t / (float) scale);
	}

	/** The ground's top under a world column as drawn (its part's resolution): what trees and rocks stand on. */
	private int groundTop(int bx, int bz) {
		long key = SceneLevel.column(bx, bz);
		int cached = groundTops.get(key);
		if (cached != NONE - 1) {
			return cached;
		}
		int s = region(bx, bz);
		int t;
		if (s == 1) {
			t = top(bx, bz);
			if (t != NONE && liquids.containsKey(key)) {
				t -= 1; // a pool's bed
			}
		} else {
			int c = cellTop(s, Math.floorDiv(bx, s), Math.floorDiv(bz, s));
			t = c == NONE ? NONE : c * s;
		}
		groundTops.put(key, t);
		return t;
	}

	// ---------------------------------------------------------------- what the ground is made of

	private BlockState surfaceAt(int bx, int bz) {
		double x = bx + 0.5 - cx;
		double z = bz + 0.5 - cz;
		if (land.tiles != null) {
			double paved = land.tiles.radius > 0 ? land.tiles.radius : field + land.flat;
			if (x * x + z * z <= paved * paved) {
				int size = Math.max(1, land.tiles.size);
				BlockState[] stones = Palette.blocks(land.tiles.blocks, Blocks.SMOOTH_QUARTZ.defaultBlockState());
				return Palette.pick(stones, Noise.hash(Math.floorDiv(bx, size) * 7349 + Math.floorDiv(bz, size) * 3571 + 17));
			}
		}
		if (surface.length == 1) {
			return surface[0];
		}
		double patch = Noise.value(x * 0.06, 9, z * 0.06);
		double patch2 = Noise.value(x * 0.17 + 30, 9, z * 0.17);
		if (surface.length >= 3 && patch2 > 0.68) {
			return surface[2];
		}
		return patch > 0.52 ? surface[1] : surface[0];
	}

	private BlockState rockAt(int x, int y, int z) {
		if (bands != null) {
			int shift = (int) Math.floor(3 * Noise.value(x * 0.03, 11, z * 0.03));
			return bands[Math.floorMod(Math.floorDiv(y + shift, 2), bands.length)];
		}
		if (rock.length == 1) {
			return rock[0];
		}
		return Palette.pick(rock, (float) Mth.clamp(Noise.value(x * 0.11, y * 0.11, z * 0.11) * 1.4 - 0.2, 0, 0.999));
	}

	/**
	 * The block {@code depth} under the top of a column whose top is {@code steep} (a cliff): the surface, then soil,
	 * then rock; a cliff shows its rock. High on a mountain the top is the far block, white above the snow line.
	 */
	private BlockState material(int x, int y, int z, int depth, boolean steep, int topY) {
		if (depth == 0 && !steep) {
			if (land.ranges != null && topY - baseFace > land.ranges.height * 0.3 && distance(x + 0.5, z + 0.5) > land.ranges.distance * 0.7) {
				boolean snowy = (land.ranges.snow != null || snowCover > 0.3f) && topY - baseFace > land.ranges.height * 0.6;
				return snowy ? Blocks.SNOW_BLOCK.defaultBlockState() : far;
			}
			return surfaceAt(x, z);
		}
		if (depth <= 3 && !steep) {
			return soil.length == 1 ? soil[0] : Palette.pick(soil, Noise.at(x, y, z, 5));
		}
		return rockAt(x, y, z);
	}

	// ---------------------------------------------------------------- building

	private SceneMesh build() {
		int originX = Mth.floor(cx);
		int originZ = Mth.floor(cz);
		markLiquids();
		List<SceneBuilder.Part> parts = new ArrayList<>();
		SceneLevel fine = new SceneLevel(host, biome, null, minY, height);
		buildFine(fine);
		parts.add(new SceneBuilder.Part(fine, 1));
		for (int scale : new int[] {2, 4}) {
			SceneLevel coarse = new SceneLevel(host, biome, null, Math.floorDiv(minY, scale), height / scale);
			buildCoarse(coarse, scale);
			parts.add(new SceneBuilder.Part(coarse, scale));
		}
		SceneBuilder.Part objects = new SceneBuilder.Part(new SceneLevel(host, biome, this::groundTop, minY, height), 1);
		buildObjects(objects);
		parts.add(objects);
		return SceneBuilder.compile(parts, originX, originZ);
	}

	/** Pools (water sunk a block into the ground) and cracks (lava), field-side only. */
	private void markLiquids() {
		if (land.pools != null) {
			Ambiance.Pools pools = land.pools;
			for (int i = 0; i < pools.count; i++) {
				double a = Math.PI * 2 * (i + 0.5 * Noise.hash(i * 107 + 1)) / Math.max(1, pools.count);
				double r = field + within(pools.ring, Noise.hash(i * 107 + 2));
				double px = cx + Math.cos(a) * r;
				double pz = cz + Math.sin(a) * r;
				double size = within(pools.size, Noise.hash(i * 107 + 3));
				for (int bx = Mth.floor(px - size - 1); bx <= Mth.floor(px + size + 1); bx++) {
					for (int bz = Mth.floor(pz - size - 1); bz <= Mth.floor(pz + size + 1); bz++) {
						double dx = bx + 0.5 - px;
						double dz = bz + 0.5 - pz;
						double edge = size * (0.8 + 0.35 * Noise.value(bx * 0.4, i, bz * 0.4));
						if (dx * dx + dz * dz <= edge * edge && region(bx, bz) == 1 && distance(bx + 0.5, bz + 0.5) > from + 0.5) {
							liquids.put(SceneLevel.column(bx, bz), Blocks.WATER.defaultBlockState());
						}
					}
				}
			}
		}
		if (land.cracks != null) {
			Ambiance.Cracks cracks = land.cracks;
			for (int i = 0; i < cracks.count; i++) {
				double a = Math.PI * 2 * Noise.hash(i * 113 + 1);
				double r = field + within(cracks.ring, Noise.hash(i * 113 + 2));
				double x = cx + Math.cos(a) * r;
				double z = cz + Math.sin(a) * r;
				double heading = a + (Noise.hash(i * 113 + 3) - 0.5) * 2.5;
				int segments = 5 + (int) (6 * Noise.hash(i * 113 + 4));
				for (int s = 0; s < segments; s++) {
					double length = 1 + 1.4 * Noise.hash(i * 113 + s * 5 + 7);
					heading += (Noise.hash(i * 113 + s * 5 + 8) - 0.5) * 1.2;
					for (double k = 0; k <= length; k += 0.5) {
						int bx = Mth.floor(x + Math.cos(heading) * k);
						int bz = Mth.floor(z + Math.sin(heading) * k);
						if (region(bx, bz) == 1 && distance(bx + 0.5, bz + 0.5) > from + 0.5) {
							liquids.put(SceneLevel.column(bx, bz), Blocks.LAVA.defaultBlockState());
						}
					}
					x += Math.cos(heading) * length;
					z += Math.sin(heading) * length;
				}
			}
		}
	}

	private static double within(List<Float> ring, double t) {
		return Mth.lerp(t, ring.get(0), ring.get(ring.size() - 1));
	}

	private void buildFine(SceneLevel level) {
		int reach = Mth.ceil(Math.min(outer, FINE + 4));
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		List<Plants.Kind> kinds = Plants.kinds(land, grass, flowers, field);
		for (int bx = Mth.floor(cx - reach); bx <= Mth.floor(cx + reach); bx++) {
			for (int bz = Mth.floor(cz - reach); bz <= Mth.floor(cz + reach); bz++) {
				if (region(bx, bz) != 1) {
					continue;
				}
				int t = top(bx, bz);
				if (t == NONE) {
					continue;
				}
				int topY = t - 1;
				int lowest = Integer.MAX_VALUE;
				boolean edge = false;
				for (Direction d : Direction.Plane.HORIZONTAL) {
					int nx = bx + d.getStepX();
					int nz = bz + d.getStepZ();
					int nt = region(nx, nz) == 1 ? top(nx, nz) : groundTop(nx, nz);
					if (nt == NONE) {
						nt = t - 12;
					}
					if (region(nx, nz) != 1) {
						edge = true;
					}
					lowest = Math.min(lowest, nt - 1);
				}
				boolean steep = topY - lowest >= 3;
				int bottom = Math.min(topY, lowest + 1);
				if (edge) {
					bottom = Math.min(bottom, topY - 3);
				}
				bottom = Math.max(bottom, topY - 96);
				BlockState liquid = liquids.get(SceneLevel.column(bx, bz));
				for (int y = bottom; y <= topY; y++) {
					int depth = topY - y;
					BlockState state;
					if (liquid != null && depth == 0) {
						state = liquid;
					} else if (liquid != null && depth == 1) {
						state = liquid.is(Blocks.LAVA) ? Blocks.MAGMA_BLOCK.defaultBlockState() : soilUnderWater(bx, bz);
					} else {
						state = material(bx, y, bz, liquid != null ? depth - 1 : depth, steep, topY);
					}
					level.set(bx, y, bz, state);
				}
				level.column(bx, bz, bottom);
			}
		}
		// Plants and snow once the ground is whole: a plant asks the block under it whether it can grow there.
		for (int bx = Mth.floor(cx - reach); bx <= Mth.floor(cx + reach); bx++) {
			for (int bz = Mth.floor(cz - reach); bz <= Mth.floor(cz + reach); bz++) {
				if (region(bx, bz) != 1) {
					continue;
				}
				int t = top(bx, bz);
				if (t == NONE) {
					continue;
				}
				BlockState under = level.stored(bx, t - 1, bz);
				if (under == null) {
					continue;
				}
				if (under.is(Blocks.WATER)) {
					if (land.pools != null && Noise.at(bx, t, bz, 61) < land.pools.lilies) {
						level.set(bx, t, bz, Blocks.LILY_PAD.defaultBlockState());
					}
					continue;
				}
				if (!under.getFluidState().isEmpty()) {
					continue;
				}
				if (snowed(bx, t, bz, level, under)) {
					continue;
				}
				double r = distance(bx + 0.5, bz + 0.5);
				try {
					Plants.place(level, pos, bx, t, bz, r, r <= from ? 0.4f : 1f, kinds, ablaze);
				} catch (RuntimeException ex) {
					// A modded plant asking the world for more than blocks: no plant there.
				}
			}
		}
		level.spreadLight();
	}

	private BlockState soilUnderWater(int bx, int bz) {
		return Noise.at(bx, 0, bz, 71) < 0.5f ? Blocks.MUD.defaultBlockState() : soil[0];
	}

	/** Snow on a column's top as the ambiance's snow cover asks: a layer or two, the grass under it snowy. */
	private boolean snowed(int bx, int t, int bz, SceneLevel level, BlockState under) {
		if (snowCover <= 0) {
			return false;
		}
		double n = Noise.value(bx * 0.12, t * 0.12 + 8, bz * 0.12);
		double amount = Noise.smooth(Mth.clamp(n * 0.6 + 0.8 - 1.05 + snowCover * 0.9, 0, 1));
		if (amount < 0.45 || !under.isFaceSturdy(level, new BlockPos(bx, t - 1, bz), Direction.UP)) {
			return false;
		}
		if (under.hasProperty(BlockStateProperties.SNOWY)) {
			level.set(bx, t - 1, bz, under.setValue(BlockStateProperties.SNOWY, true));
		}
		int layers = amount > 0.85 ? 3 : amount > 0.65 ? 2 : 1;
		level.set(bx, t, bz, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers));
		return true;
	}

	private void buildCoarse(SceneLevel level, int scale) {
		int reach = Mth.ceil(outer / scale) + 2;
		int ccx = Mth.floor(cx / scale);
		int ccz = Mth.floor(cz / scale);
		for (int ix = ccx - reach; ix <= ccx + reach; ix++) {
			for (int iz = ccz - reach; iz <= ccz + reach; iz++) {
				if (region(ix * scale, iz * scale) != scale) {
					continue;
				}
				int t = cellTop(scale, ix, iz);
				if (t == NONE) {
					continue;
				}
				int topY = t - 1;
				int lowest = Integer.MAX_VALUE;
				boolean edge = false;
				for (Direction d : Direction.Plane.HORIZONTAL) {
					int nx = ix + d.getStepX();
					int nz = iz + d.getStepZ();
					int nt = cellTop(scale, nx, nz);
					if (nt == NONE) {
						nt = t - 12 / scale - 1;
					}
					if (region(nx * scale, nz * scale) != scale) {
						edge = true;
					}
					lowest = Math.min(lowest, nt - 1);
				}
				boolean steep = (topY - lowest) * scale >= 3;
				int bottom = Math.min(topY, lowest + 1);
				if (edge) {
					bottom = Math.min(bottom, topY - 2);
				}
				bottom = Math.max(bottom, topY - 96 / scale);
				int wx = ix * scale + scale / 2;
				int wz = iz * scale + scale / 2;
				for (int y = bottom; y <= topY; y++) {
					int depth = (topY - y) * scale;
					BlockState state = material(wx, y * scale, wz, depth == 0 ? 0 : Math.max(depth, 4), steep, topY * scale);
					if (depth == 0 && snowCover > 0.3f && !state.is(Blocks.SNOW_BLOCK)) {
						double n = Noise.value(wx * 0.12, y * scale * 0.12 + 8, wz * 0.12);
						if (Noise.smooth(Mth.clamp(n * 0.6 + 0.8 - 1.05 + snowCover * 0.9, 0, 1)) > 0.6) {
							state = Blocks.SNOW_BLOCK.defaultBlockState();
						}
					}
					level.set(ix, y, iz, state);
				}
				level.column(ix, iz, bottom);
			}
		}
	}

	// ---------------------------------------------------------------- trees, rocks, columns, scrap

	private void buildObjects(SceneBuilder.Part part) {
		SceneLevel level = part.level();
		if (land.trees != null) {
			Ambiance.Trees trees = land.trees;
			List<String> species = trees.species;
			if (species == null || species.isEmpty()) {
				species = switch (trees.kind) {
					case "pine" -> List.of("spruce");
					case "dead" -> List.of("dead");
					default -> trees.canopy ? List.of("dark_oak", "oak") : List.of("oak", "oak", "birch");
				};
			}
			for (int i = 0; i < trees.count; i++) {
				double a = Math.PI * 2 * (i + 0.7 * Noise.hash(i * 83 + 1)) / Math.max(1, trees.count);
				double r = field + within(trees.ring, Math.pow(Noise.hash(i * 83 + 2), 0.8));
				int bx = Mth.floor(cx + Math.cos(a) * r);
				int bz = Mth.floor(cz + Math.sin(a) * r);
				int ground = groundTop(bx, bz);
				if (ground == NONE || liquids.containsKey(SceneLevel.column(bx, bz))) {
					continue;
				}
				int h = (int) Math.round(within(trees.height, Noise.hash(i * 83 + 3)));
				String kind = species.get(i % species.size());
				boolean big = trees.canopy || h >= 12;
				Long2ObjectOpenHashMap<BlockState> tree = Trees.grow(kind, bx, ground, bz, h, big, ablaze, i * 31 + 7);
				if (ablaze && Noise.hash(i * 83 + 9) < 0.6f) {
					// Burning still: a fire on the stump's top (a charred trunk is that tall, see Trees.dead).
					tree.put(BlockPos.asLong(bx, ground + Mth.clamp(Math.round(h * 0.8f), 3, 10), bz), Blocks.FIRE.defaultBlockState());
				}
				addObject(part, tree);
			}
		}
		if (land.rocks != null) {
			Ambiance.Rocks rocks = land.rocks;
			BlockState[] stone = rocks.blocks != null ? Palette.blocks(rocks.blocks, rock[0]) : null;
			for (int i = 0; i < rocks.count + rocks.spires; i++) {
				double a = Math.PI * 2 * Noise.hash(i * 67 + 1);
				double r = field + within(rocks.ring, Math.sqrt(Noise.hash(i * 67 + 2)));
				double px = cx + Math.cos(a) * r;
				double pz = cz + Math.sin(a) * r;
				int ground = groundTop(Mth.floor(px), Mth.floor(pz));
				if (ground == NONE) {
					continue;
				}
				float size = (float) within(rocks.size, Noise.hash(i * 67 + 3));
				Long2ObjectOpenHashMap<BlockState> object = new Long2ObjectOpenHashMap<>();
				if (i < rocks.count) {
					boulder(object, px, ground, pz, size, i, stone);
				} else {
					spire(object, px, ground, pz, size, i, stone);
				}
				addObject(part, object);
			}
		}
		if (land.columns != null) {
			columns(part, land.columns);
		}
		if (land.junk != null) {
			Ambiance.Junk junk = land.junk;
			BlockState[] scrap = Palette.blocks(junk.blocks, Blocks.BARREL.defaultBlockState());
			for (int i = 0; i < junk.count; i++) {
				double a = Math.PI * 2 * Noise.hash(i * 73 + 1);
				double r = field + within(junk.ring, Math.sqrt(Noise.hash(i * 73 + 2)));
				int bx = Mth.floor(cx + Math.cos(a) * r);
				int bz = Mth.floor(cz + Math.sin(a) * r);
				int ground = groundTop(bx, bz);
				if (ground == NONE || liquids.containsKey(SceneLevel.column(bx, bz))) {
					continue;
				}
				Long2ObjectOpenHashMap<BlockState> pile = new Long2ObjectOpenHashMap<>();
				pile.put(BlockPos.asLong(bx, ground, bz), Palette.pick(scrap, Noise.hash(i * 73 + 3)));
				if (Noise.hash(i * 73 + 4) < 0.35f) {
					pile.put(BlockPos.asLong(bx, ground + 1, bz), Palette.pick(scrap, Noise.hash(i * 73 + 5)));
				}
				if (Noise.hash(i * 73 + 6) < 0.3f) {
					Direction d = Direction.from2DDataValue((int) (Noise.hash(i * 73 + 7) * 4));
					int sx = bx + d.getStepX();
					int sz = bz + d.getStepZ();
					int sg = groundTop(sx, sz);
					if (sg != NONE) {
						pile.put(BlockPos.asLong(sx, sg, sz), Palette.pick(scrap, Noise.hash(i * 73 + 8)));
					}
				}
				addObject(part, pile);
			}
		}
		if (ablaze) {
			// Fire licking the ground round the field, on what can burn.
			int reach = Mth.ceil(Math.min(FINE, field + 30));
			for (int bx = Mth.floor(cx - reach); bx <= Mth.floor(cx + reach); bx++) {
				for (int bz = Mth.floor(cz - reach); bz <= Mth.floor(cz + reach); bz++) {
					double d = distance(bx + 0.5, bz + 0.5);
					if (d <= from + 1 || d > reach || region(bx, bz) != 1 || Noise.at(bx, 0, bz, 91) > 0.018f) {
						continue;
					}
					int g = groundTop(bx, bz);
					if (g != NONE && !liquids.containsKey(SceneLevel.column(bx, bz))) {
						Long2ObjectOpenHashMap<BlockState> fire = new Long2ObjectOpenHashMap<>();
						fire.put(BlockPos.asLong(bx, g, bz), Blocks.FIRE.defaultBlockState());
						addObject(part, fire);
					}
				}
			}
		}
		level.spreadLight();
	}

	private void addObject(SceneBuilder.Part part, Long2ObjectOpenHashMap<BlockState> blocks) {
		if (blocks.isEmpty()) {
			return;
		}
		LongArrayList keys = new LongArrayList();
		for (var entry : blocks.long2ObjectEntrySet()) {
			long key = entry.getLongKey();
			if (part.level().blocks.containsKey(key)) {
				continue; // two objects overlapping: the first keeps the block
			}
			part.level().blocks.put(key, entry.getValue());
			keys.add(key);
		}
		if (!keys.isEmpty()) {
			part.objects().add(keys);
		}
	}

	/** A boulder: a lumpy ball of rock half sunk in the ground, mossy in places when the rock allows. */
	private void boulder(Long2ObjectOpenHashMap<BlockState> out, double px, int ground, double pz, float size, int seed, BlockState[] stone) {
		double rx = 0.7 + size * 0.9;
		double ry = 0.6 + size * 0.7;
		double rz = 0.7 + size * 0.85;
		double py = ground + ry * 0.35 - 0.5;
		for (int x = Mth.floor(px - rx); x <= Mth.floor(px + rx); x++) {
			for (int y = Mth.floor(py - ry); y <= Mth.floor(py + ry); y++) {
				for (int z = Mth.floor(pz - rz); z <= Mth.floor(pz + rz); z++) {
					if (y < groundTop(x, z) - 1) {
						continue;
					}
					double dx = (x + 0.5 - px) / rx;
					double dy = (y + 0.5 - py) / ry;
					double dz = (z + 0.5 - pz) / rz;
					if (dx * dx + dy * dy + dz * dz <= 1 - 0.3 * Noise.at(x, y, z, seed)) {
						out.put(BlockPos.asLong(x, y, z), stone != null ? Palette.pick(stone, Noise.at(x, y, z, seed + 1)) : rockAt(x, y, z));
					}
				}
			}
		}
	}

	/** A spire: a needle of rock, wide at its root, narrowing to a point. */
	private void spire(Long2ObjectOpenHashMap<BlockState> out, double px, int ground, double pz, float size, int seed, BlockState[] stone) {
		int h = Math.max(4, (int) Math.round(size * (3 + 3 * Noise.hash(seed * 67 + 5)) * 1.6));
		double root = 0.8 + size * 0.7;
		double leanX = (Noise.hash(seed * 67 + 6) - 0.5) * 0.25;
		double leanZ = (Noise.hash(seed * 67 + 7) - 0.5) * 0.25;
		for (int k = -1; k < h; k++) {
			double w = root * (1 - k / (double) h) + 0.35;
			double sx = px + leanX * k;
			double sz = pz + leanZ * k;
			for (int x = Mth.floor(sx - w); x <= Mth.floor(sx + w); x++) {
				for (int z = Mth.floor(sz - w); z <= Mth.floor(sz + w); z++) {
					double dx = x + 0.5 - sx;
					double dz = z + 0.5 - sz;
					if (dx * dx + dz * dz <= w * w) {
						int y = ground + k;
						out.put(BlockPos.asLong(x, y, z), stone != null ? Palette.pick(stone, Noise.at(x, y, z, seed + 3)) : rockAt(x, y, z));
					}
				}
			}
		}
	}

	/** A temple's ring of columns: a plinth, a shaft, a capital, and lintels joining the standing ones. */
	private void columns(SceneBuilder.Part part, Ambiance.Columns columns) {
		BlockState shaft = Palette.block(columns.block, Blocks.QUARTZ_PILLAR.defaultBlockState());
		BlockState capital = Palette.block(columns.capital, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
		BlockState slab = Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState();
		double r = field + columns.distance;
		int[][] tops = new int[columns.count][];
		for (int i = 0; i < columns.count; i++) {
			double a = Math.PI * 2 * i / columns.count;
			int bx = Mth.floor(cx + Math.cos(a) * r);
			int bz = Mth.floor(cz + Math.sin(a) * r);
			int ground = groundTop(bx, bz);
			if (ground == NONE) {
				continue;
			}
			boolean broken = columns.ruined && i % 3 == 1;
			int h = (int) Math.round(columns.height * (broken ? 0.35 + 0.3 * Noise.hash(i * 127 + 1) : 1));
			Long2ObjectOpenHashMap<BlockState> column = new Long2ObjectOpenHashMap<>();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					column.put(BlockPos.asLong(bx + dx, ground, bz + dz), slab.setValue(SlabBlock.TYPE, SlabType.BOTTOM));
				}
			}
			column.put(BlockPos.asLong(bx, ground, bz), capital);
			for (int y = 1; y < h - 1; y++) {
				column.put(BlockPos.asLong(bx, ground + y, bz), shaft);
			}
			if (!broken) {
				column.put(BlockPos.asLong(bx, ground + h - 1, bz), capital);
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx != 0 || dz != 0) {
							column.put(BlockPos.asLong(bx + dx, ground + h - 1, bz + dz), slab.setValue(SlabBlock.TYPE, SlabType.TOP));
						}
					}
				}
				tops[i] = new int[] {bx, ground + h, bz};
			}
			addObject(part, column);
		}
		for (int i = 0; i < columns.count; i++) {
			int[] a = tops[i];
			int[] b = tops[(i + 1) % columns.count];
			if (a == null || b == null) {
				continue;
			}
			Long2ObjectOpenHashMap<BlockState> lintel = new Long2ObjectOpenHashMap<>();
			int steps = Math.max(Math.abs(b[0] - a[0]), Math.abs(b[2] - a[2]));
			LongOpenHashSet seen = new LongOpenHashSet();
			for (int k = 0; k <= steps; k++) {
				double t = steps == 0 ? 0 : k / (double) steps;
				int x = (int) Math.round(Mth.lerp(t, a[0], b[0]));
				int z = (int) Math.round(Mth.lerp(t, a[2], b[2]));
				int y = (int) Math.round(Mth.lerp(t, a[1], b[1]));
				if (seen.add(SceneLevel.column(x, z))) {
					lintel.put(BlockPos.asLong(x, y, z), slab.setValue(SlabBlock.TYPE, SlabType.BOTTOM));
				}
			}
			addObject(part, lintel);
		}
	}

	/** The plants of a land, as kinds with their chances; and placing them. */
	static final class Plants {

		/** A stadium's own plants, as kinds. */
		static List<Kind> of(List<Ambiance.Plant> plants) {
			List<Kind> kinds = new ArrayList<>();
			if (plants != null) {
				for (Ambiance.Plant plant : plants) {
					kinds.add(new Kind(Palette.block(plant.block, Blocks.SHORT_GRASS.defaultBlockState()), plant.chance, plant.ring));
				}
			}
			return kinds;
		}

		record Kind(BlockState block, float chance, List<Float> ring) {
		}

		private Plants() {
		}

		/** The stadium's plants, then the ambiance's grass and flowers (given as counts and colours) as Minecraft's. */
		static List<Kind> kinds(Ambiance.Land land, Ambiance.Grass grass, Ambiance.Flowers flowers, double field) {
			List<Kind> kinds = new ArrayList<>();
			if (flowers != null) {
				double r0 = flowers.ring.get(0);
				double r1 = flowers.ring.get(flowers.ring.size() - 1);
				float density = (float) Math.min(0.5, flowers.count / Math.max(1, Math.PI * (r1 * r1 - r0 * r0)));
				for (int c = 0; c < flowers.colors.size(); c++) {
					int rgb = rgb(flowers.colors.get(c), 0xFF6A8A);
					for (int v = 0; v < 2; v++) {
						kinds.add(new Kind(Palette.flower(rgb, v / 2f + 0.25f), density / flowers.colors.size() / 2,
								List.of((float) r0, (float) r1)));
					}
				}
			}
			if (land.plants != null) {
				for (Ambiance.Plant plant : land.plants) {
					BlockState state = Palette.block(plant.block, Blocks.SHORT_GRASS.defaultBlockState());
					kinds.add(new Kind(state, plant.chance, plant.ring));
				}
			}
			if (grass != null) {
				double r0 = grass.ring.get(0);
				double r1 = grass.ring.get(grass.ring.size() - 1);
				float density = (float) Math.min(0.55, grass.count / Math.max(1, Math.PI * (r1 * r1 - r0 * r0)));
				List<Float> ring = List.of((float) r0, (float) r1);
				kinds.add(new Kind(Blocks.TALL_GRASS.defaultBlockState(), density * 0.12f, ring));
				kinds.add(new Kind(Blocks.SHORT_GRASS.defaultBlockState(), density * 0.88f, ring));
			}
			return kinds;
		}

		private static int rgb(String hex, int fallback) {
			try {
				return Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16) & 0xFFFFFF;
			} catch (RuntimeException ex) {
				return fallback;
			}
		}

		/**
		 * At most one plant on (x, y - 1, z): the first kind whose chance comes up (a roll of its own, so a plant that
		 * grows only by water, sugar cane, never takes the others' place elsewhere) and that can grow there.
		 */
		static void place(SceneLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z, double r, float scale, List<Kind> kinds,
				boolean ablaze) {
			if (ablaze) {
				if (Noise.at(x, y, z, 77) < 0.012f * scale) {
					pos.set(x, y, z);
					if (Blocks.DEAD_BUSH.defaultBlockState().canSurvive(level, pos)) {
						level.set(x, y, z, Blocks.DEAD_BUSH.defaultBlockState());
					}
				}
				return;
			}
			pos.set(x, y, z);
			if (!level.getBlockState(pos).isAir()) {
				return;
			}
			for (int k = 0; k < kinds.size(); k++) {
				Kind kind = kinds.get(k);
				if (kind.ring != null && (r < kind.ring.get(0) || r > kind.ring.get(kind.ring.size() - 1))) {
					continue;
				}
				if (Noise.at(x, y, z, 33 + 7 * k) >= kind.chance * scale) {
					continue;
				}
				BlockState state = kind.block;
				if (!state.canSurvive(level, pos)) {
					continue;
				}
				if (state.getBlock() instanceof DoublePlantBlock) {
					if (!level.isAir(x, y + 1, z)) {
						return;
					}
					level.set(x, y, z, state.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
					level.set(x, y + 1, z, state.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
				} else if (state.is(Blocks.CACTUS) || state.is(Blocks.SUGAR_CANE)) {
					int tall = 1 + (int) (Noise.at(x, y, z, 34) * 3);
					for (int n = 0; n < tall; n++) {
						level.set(x, y + n, z, state);
					}
				} else if (state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT)) {
					// A kelp: its stem, then its head, swaying nowhere but tall.
					int tall = 2 + (int) (Noise.at(x, y, z, 37) * 7);
					for (int n = 0; n < tall - 1; n++) {
						if (!level.isAir(x, y + n + 1, z)) {
							tall = n + 1;
							break;
						}
						level.set(x, y + n, z, Blocks.KELP_PLANT.defaultBlockState());
					}
					level.set(x, y + tall - 1, z, Blocks.KELP.defaultBlockState());
				} else if (state.hasProperty(BlockStateProperties.PICKLES)) {
					level.set(x, y, z, state.setValue(BlockStateProperties.PICKLES, 1 + (int) (Noise.at(x, y, z, 38) * 4)));
				} else if (state.hasProperty(BlockStateProperties.FLOWER_AMOUNT)) {
					level.set(x, y, z, state.setValue(BlockStateProperties.FLOWER_AMOUNT, 1 + (int) (Noise.at(x, y, z, 35) * 4))
							.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.from2DDataValue((int) (Noise.at(x, y, z, 36) * 4))));
				} else {
					level.set(x, y, z, state);
				}
				return;
			}
		}
	}
}
