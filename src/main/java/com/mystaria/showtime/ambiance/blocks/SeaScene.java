package com.mystaria.showtime.ambiance.blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleBinaryOperator;
import java.util.function.Supplier;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.mystaria.showtime.ambiance.Ambiance;

/**
 * A sea stadium built of blocks (2026-11-19): the field on an islet of sand, Minecraft's water out to the horizon over a
 * sandy bed, rocks on the shore; or, under water, the field on the seabed among seagrass, kelp and coral, the surface
 * overhead. Frozen, the water is ice, snowed in patches, floes standing on it.
 * <p>
 * Only the water's surface is stored; the water under it is read, not drawn. Far off, cells of 2 then 4 blocks, raised
 * a little so their surface meets the near one's.
 */
final class SeaScene {

	private static final int NONE = Integer.MIN_VALUE;

	private final Ambiance.Sea sea;
	private final boolean frozen;
	private final FieldGround g;
	private final Holder<Biome> biome;
	private final int tint;
	/** Islet: the y of the surface's water blocks. Under: the y of the water overhead. */
	private final int waterY;
	private final BlockState[] surface;
	private final BlockState[] soil;
	private final BlockState[] rock;
	private final BlockState[] stones;
	private final Long2IntOpenHashMap tops = new Long2IntOpenHashMap();

	private SeaScene(Ambiance.Sea sea, boolean frozen, int tint, Vec3 center, float fieldRadius, DoubleBinaryOperator ground) {
		this.sea = sea;
		this.frozen = frozen && !sea.under;
		this.tint = tint;
		this.g = new FieldGround(center, fieldRadius, fieldRadius + 2.5, ground);
		this.biome = g.biome(sea.biome);
		this.waterY = sea.under ? g.baseFace + Math.max(4, Math.round(sea.depth)) - 1 : g.baseFace - 1;
		Ambiance.Ground blocks = sea.blocks;
		this.surface = blocks != null ? Palette.blocks(blocks.surface, Blocks.SAND.defaultBlockState())
				: new BlockState[] {Blocks.SAND.defaultBlockState(), Blocks.SAND.defaultBlockState(), Blocks.GRAVEL.defaultBlockState()};
		this.soil = blocks != null ? Palette.blocks(blocks.soil, Blocks.SAND.defaultBlockState())
				: new BlockState[] {Blocks.SAND.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState()};
		this.rock = blocks != null ? Palette.blocks(blocks.rock, Blocks.STONE.defaultBlockState())
				: new BlockState[] {Blocks.STONE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState()};
		this.stones = sea.rockBlocks != null ? Palette.blocks(sea.rockBlocks, rock[0]) : rock;
		tops.defaultReturnValue(NONE + 1);
	}

	static Supplier<SceneMesh> recipe(Ambiance.Sea sea, boolean frozen, int tint, Vec3 center, float fieldRadius,
			DoubleBinaryOperator ground) {
		SeaScene scene = new SeaScene(sea, frozen, tint, center, fieldRadius, ground);
		return scene::build;
	}

	// ---------------------------------------------------------------- the bed's shape

	/** The islet's shore towards {@code angle}: the field's edge and the margin, uneven. */
	private double edge(double angle) {
		double n = Noise.value(Math.cos(angle) * 1.6 + 5, 0.5, Math.sin(angle) * 1.6 + 5);
		return g.field + sea.margin * (0.6 + 0.9 * n);
	}

	/** The bed's top (the y of the first block of air or water) under a world column, or {@link #NONE} past the sea. */
	private int top(int bx, int bz) {
		long key = SceneLevel.column(bx, bz);
		int cached = tops.get(key);
		if (cached != NONE + 1) {
			return cached;
		}
		int t = topAt(bx, bz);
		tops.put(key, t);
		return t;
	}

	private int topAt(int bx, int bz) {
		double x = bx + 0.5 - g.cx;
		double z = bz + 0.5 - g.cz;
		double r = Math.sqrt(x * x + z * z);
		if (r > sea.radius) {
			return NONE;
		}
		if (r <= g.from) {
			return g.top(bx, bz);
		}
		double rim = g.rim(x, z);
		double y;
		if (sea.under) {
			double dunes = g.baseAbs + 1.8 * (Noise.value(x * 0.06, 3, z * 0.06) - 0.5)
					+ 0.12 * Math.sin(x * 0.5 + z * 0.15 + 3 * Noise.value(x * 0.1, 7, z * 0.1)) - 0.035 * (r - g.from);
			y = Mth.lerp(Noise.smooth(Mth.clamp((r - g.from) / 5, 0, 1)), rim, dunes);
		} else {
			double e = edge(Math.atan2(z, x));
			double floor = g.baseAbs - 1.25;
			y = r > e ? Math.max(floor - (r - e) * 0.6, g.baseAbs - 14) // a shelf, then the deep
					: Mth.lerp(Noise.smooth(Mth.clamp((r - g.from) / Math.max(0.5, e - g.from), 0, 1)), rim, floor);
		}
		return g.baseFace + (int) Math.floor(y - g.baseAbs + 0.5);
	}

	private int cellTop(int s, int ix, int iz) {
		if (s == 1) {
			return top(ix, iz);
		}
		int t = top(ix * s + s / 2, iz * s + s / 2);
		return t == NONE ? NONE : Math.round(t / (float) s);
	}

	private BlockState material(int x, int y, int z, int depth, boolean steep) {
		if (depth == 0 && !steep) {
			double patch = Noise.value((x - g.cx) * 0.06, 9, (z - g.cz) * 0.06);
			double patch2 = Noise.value((x - g.cx) * 0.17 + 30, 9, (z - g.cz) * 0.17);
			if (surface.length >= 3 && patch2 > 0.68) {
				return surface[2];
			}
			return surface.length >= 2 && patch > 0.52 ? surface[1] : surface[0];
		}
		if (depth <= 3 && !steep) {
			return Palette.pick(soil, Noise.at(x, y, z, 5));
		}
		return Palette.pick(rock, (float) Mth.clamp(Noise.value(x * 0.11, y * 0.11, z * 0.11) * 1.4 - 0.2, 0, 0.999));
	}

	// ---------------------------------------------------------------- building

	private SceneMesh build() {
		List<SceneBuilder.Part> parts = new ArrayList<>();
		SceneLevel fine = null;
		for (int s : new int[] {1, 2, 4}) {
			// Water's cell, and how much the part is raised so its water meets the near water: its surface (seen from
			// above) on an islet, its underside (seen from below) on a seabed.
			int w = Math.floorDiv(waterY, s);
			float lift = sea.under ? waterY - w * s : (waterY + 8f / 9f) - (w * s + s * 8f / 9f);
			Long2IntOpenHashMap bottoms = new Long2IntOpenHashMap();
			Long2IntOpenHashMap bedTops = new Long2IntOpenHashMap();
			SceneLevel level = new SceneLevel(g.host, biome, null, Math.floorDiv(g.minY, s), g.height / s).waterTint(tint);
			level.fallback((x, y, z) -> fallback(bottoms, bedTops, w, x, y, z));
			if (sea.under) {
				level.skyLight(15);
			}
			buildPart(level, s, w, bottoms, bedTops);
			parts.add(new SceneBuilder.Part(level, s, lift));
			if (s == 1) {
				fine = level;
			}
		}
		plants(fine);
		fine.spreadLight();
		SceneBuilder.Part objects = new SceneBuilder.Part(new SceneLevel(g.host, biome, this::groundTop, g.minY, g.height), 1);
		rocks(objects);
		parts.add(objects);
		return SceneBuilder.compile(parts, Mth.floor(g.cx), Mth.floor(g.cz));
	}

	/**
	 * What is not stored: rock under a column's lowest block; water from the bed up to the surface (islet) or above
	 * the water overhead (under); air elsewhere. Past the part's columns, water at the same heights, so no part draws
	 * its water's side where it meets the next.
	 */
	private BlockState fallback(Long2IntOpenHashMap bottoms, Long2IntOpenHashMap bedTops, int w, int x, int y, int z) {
		long key = SceneLevel.column(x, z);
		if (bottoms.containsKey(key)) {
			if (y < bottoms.get(key)) {
				return SceneLevel.FILL;
			}
			if (sea.under) {
				return y > w ? water() : SceneLevel.AIR;
			}
			return y >= bedTops.get(key) && y <= w ? water() : SceneLevel.AIR;
		}
		if (sea.under) {
			return y >= w ? water() : SceneLevel.AIR;
		}
		return y <= w ? water() : SceneLevel.AIR;
	}

	private BlockState water() {
		return frozen ? Blocks.ICE.defaultBlockState() : Blocks.WATER.defaultBlockState();
	}

	private void buildPart(SceneLevel level, int s, int w, Long2IntOpenHashMap bottoms, Long2IntOpenHashMap bedTops) {
		int reach = Mth.ceil(sea.radius / s) + 2;
		int ccx = Mth.floor(g.cx / s);
		int ccz = Mth.floor(g.cz / s);
		for (int ix = ccx - reach; ix <= ccx + reach; ix++) {
			for (int iz = ccz - reach; iz <= ccz + reach; iz++) {
				if (g.scale(ix * s, iz * s) != s) {
					continue;
				}
				int t = cellTop(s, ix, iz);
				if (t == NONE) {
					continue;
				}
				if (s > 1 && !sea.under) {
					t = Math.min(t, w); // far off, all under water
				}
				if (sea.under && t >= w) {
					t = w - 1;
				}
				int topY = t - 1;
				int lowest = Integer.MAX_VALUE;
				boolean edge = false;
				for (Direction d : Direction.Plane.HORIZONTAL) {
					int nx = ix + d.getStepX();
					int nz = iz + d.getStepZ();
					int nt = cellTop(s, nx, nz);
					if (nt == NONE) {
						nt = t;
					}
					if (s > 1 && !sea.under) {
						nt = Math.min(nt, w);
					}
					if (g.scale(nx * s, nz * s) != s) {
						edge = true;
					}
					lowest = Math.min(lowest, nt - 1);
				}
				boolean steep = (topY - lowest) * s >= 3;
				int bottom = Math.min(topY, lowest + 1);
				if (edge) {
					bottom = Math.min(bottom, topY - 2);
				}
				bottom = Math.max(bottom, topY - 48 / s);
				int wx = ix * s + s / 2;
				int wz = iz * s + s / 2;
				for (int y = bottom; y <= topY; y++) {
					int depth = (topY - y) * s;
					level.set(ix, y, iz, material(wx, y * s, wz, depth == 0 ? 0 : Math.max(depth, s == 1 ? depth : 4), steep));
				}
				long key = SceneLevel.column(ix, iz);
				bottoms.put(key, bottom);
				bedTops.put(key, t);
				level.column(ix, iz, bottom);
				if (sea.under || t <= w) {
					level.set(ix, w, iz, water());
					if (frozen && s == 1) {
						snowOnIce(level, ix, w, iz);
					}
				}
			}
		}
	}

	/** On frozen water: snow lying in patches, and floes (packed ice) standing on the sheet near the islet. */
	private void snowOnIce(SceneLevel level, int x, int w, int z) {
		double r = g.distance(x + 0.5, z + 0.5);
		if (r < g.field + sea.margin + 1) {
			return;
		}
		boolean floe = r < g.field + sea.margin + 22 && Noise.value(x * 0.35, 13, z * 0.35) > 0.78;
		if (floe) {
			level.set(x, w + 1, z, Blocks.PACKED_ICE.defaultBlockState());
		}
		double snow = Noise.value(x * 0.15, 1, z * 0.15);
		if (snow > 0.6) {
			level.set(x, w + (floe ? 2 : 1), z, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, snow > 0.75 ? 2 : 1));
		}
	}

	/** The stadium's plants on the near part: on the beach (islet) or the seabed. */
	private void plants(SceneLevel level) {
		List<LandScene.Plants.Kind> kinds = LandScene.Plants.of(sea.plants);
		if (kinds.isEmpty()) {
			return;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int reach = Mth.ceil(Math.min(sea.radius, 44));
		for (int bx = Mth.floor(g.cx - reach); bx <= Mth.floor(g.cx + reach); bx++) {
			for (int bz = Mth.floor(g.cz - reach); bz <= Mth.floor(g.cz + reach); bz++) {
				if (g.scale(bx, bz) != 1) {
					continue;
				}
				int t = top(bx, bz);
				if (t == NONE || level.stored(bx, t - 1, bz) == null) {
					continue;
				}
				double r = g.distance(bx + 0.5, bz + 0.5);
				try {
					LandScene.Plants.place(level, pos, bx, t, bz, r, r <= g.from ? 0.4f : 1f, kinds, false);
				} catch (RuntimeException ex) {
					// A modded plant asking the world for more than blocks: no plant there.
				}
			}
		}
	}

	/** The bed under a column as drawn, for rocks: the near part's own; far off, the cell's. */
	private int groundTop(int bx, int bz) {
		int s = g.scale(bx, bz);
		if (s == 1) {
			int t = top(bx, bz);
			return t == NONE ? NONE : (sea.under ? Math.min(t, waterY - 1) : t);
		}
		int c = cellTop(s, Math.floorDiv(bx, s), Math.floorDiv(bz, s));
		return c == NONE ? NONE : c * s;
	}

	private void rocks(SceneBuilder.Part part) {
		int count = sea.under ? sea.rocks + 4 : sea.rocks;
		for (int i = 0; i < count; i++) {
			double a = Math.PI * 2 * (i + 0.6 * Noise.hash(i * 53 + 1)) / Math.max(1, count);
			double r = sea.under ? g.field + 3 + 15 * Noise.hash(i * 53 + 2) : edge(a) + 0.4 * Noise.hash(i * 53 + 2) - 0.6;
			double px = g.cx + Math.cos(a) * r;
			double pz = g.cz + Math.sin(a) * r;
			int ground = groundTop(Mth.floor(px), Mth.floor(pz));
			if (ground == NONE) {
				continue;
			}
			float size = sea.under ? 0.6f + 1.1f * Noise.hash(i * 53 + 3) : 0.5f + 0.8f * Noise.hash(i * 53 + 3);
			Long2ObjectOpenHashMap<BlockState> boulder = new Long2ObjectOpenHashMap<>();
			// On the shore, standing out of the water whatever the bed under them.
			int foot = sea.under ? ground : Math.max(ground, waterY);
			FieldGround.boulder(boulder, px, foot, pz, size, i * 7 + 3, stones, this::groundTop);
			LongArrayList keys = new LongArrayList();
			for (var entry : boulder.long2ObjectEntrySet()) {
				if (part.level().blocks.putIfAbsent(entry.getLongKey(), entry.getValue()) == null) {
					keys.add(entry.getLongKey());
				}
			}
			if (!keys.isEmpty()) {
				part.objects().add(keys);
			}
		}
	}
}
