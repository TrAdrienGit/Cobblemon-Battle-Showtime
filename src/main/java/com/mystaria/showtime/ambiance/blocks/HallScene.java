package com.mystaria.showtime.ambiance.blocks;

import java.util.List;
import java.util.function.DoubleBinaryOperator;
import java.util.function.Supplier;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.mystaria.showtime.ambiance.Ambiance;

/**
 * A hall stadium built of blocks (2026-11-19): a room ({@code box}: walls with pilasters, a ceiling with beams and
 * hanging lamps) or a circus tent (a round wall striped in wool, a stepped cone over it), the floor plain or a
 * checkerboard on the world's grid. As in a cave, only the inner skin is stored and nothing faces outwards, so a camera
 * outside sees in. A hall with mirrors keeps its smooth walls (its reflections are drawn behind them): only its floor
 * is blocks.
 */
final class HallScene {

	private static final int NONE = Integer.MIN_VALUE;

	private final Ambiance.Hall hall;
	private final boolean dark;
	private final FieldGround g;
	private final boolean tent;
	private final boolean floorOnly;
	private final double half;
	private final int ox;
	private final int oz;
	private final BlockState[] floor;
	private final BlockState[] checker;
	private final BlockState[] wall;
	private final BlockState[] ceiling;
	private final BlockState[] stripes;
	private final BlockState trim;
	private final Long2IntOpenHashMap floors = new Long2IntOpenHashMap();

	private HallScene(Ambiance.Hall hall, boolean dark, Vec3 center, float fieldRadius, DoubleBinaryOperator ground) {
		this.hall = hall;
		this.dark = dark;
		this.half = fieldRadius + hall.margin;
		this.g = new FieldGround(center, fieldRadius, fieldRadius + 2.5, ground);
		this.tent = "tent".equals(hall.shape);
		this.floorOnly = hall.mirrors;
		this.ox = (int) Math.round(center.x);
		this.oz = (int) Math.round(center.z);
		this.floor = Palette.blocks(hall.floorBlocks, Blocks.SMOOTH_STONE.defaultBlockState());
		this.checker = Palette.blocks(hall.checkerBlocks, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
		this.wall = Palette.blocks(hall.wallBlocks, Blocks.STONE_BRICKS.defaultBlockState());
		this.ceiling = Palette.blocks(hall.ceilingBlocks, Blocks.DARK_OAK_PLANKS.defaultBlockState());
		this.stripes = Palette.blocks(hall.stripeBlocks, Blocks.WHITE_WOOL.defaultBlockState());
		this.trim = Palette.block(hall.trim, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
		floors.defaultReturnValue(NONE + 1);
	}

	static Supplier<SceneMesh> recipe(Ambiance.Hall hall, boolean dark, Vec3 center, float fieldRadius, DoubleBinaryOperator ground) {
		HallScene scene = new HallScene(hall, dark, center, fieldRadius, ground);
		return scene::build;
	}

	// ---------------------------------------------------------------- the room's shape

	private double x(int bx) {
		return bx + 0.5 - g.cx;
	}

	private double z(int bz) {
		return bz + 0.5 - g.cz;
	}

	private boolean inRoom(int bx, int bz) {
		double x = x(bx);
		double z = z(bz);
		return tent ? x * x + z * z < half * half : Math.abs(x) < half && Math.abs(z) < half;
	}

	/** The floor's top (first air y), or NONE outside the room. */
	private int floorTop(int bx, int bz) {
		long key = SceneLevel.column(bx, bz);
		int cached = floors.get(key);
		if (cached != NONE + 1) {
			return cached;
		}
		int t = NONE;
		if (inRoom(bx, bz)) {
			if (hall.flat) {
				t = g.baseFace;
			} else {
				double x = x(bx);
				double z = z(bz);
				double r = Math.sqrt(x * x + z * z);
				t = r <= g.from ? g.top(bx, bz)
						: g.baseFace + (int) Math.floor(Mth.lerp(Noise.smooth(Mth.clamp((r - g.from) / 2, 0, 1)), g.rim(x, z), g.baseAbs)
								- g.baseAbs + 0.5);
			}
		}
		floors.put(key, t);
		return t;
	}

	/** The ceiling's underside (first solid y): flat in a room, a stepped cone in a tent. */
	private int ceiling(int bx, int bz) {
		int wallTop = g.baseFace + Math.round(hall.height);
		if (!tent) {
			return wallTop;
		}
		double r = Math.sqrt(x(bx) * x(bx) + z(bz) * z(bz));
		return wallTop + (int) Math.floor(hall.height * 0.5 * Mth.clamp(1 - r / half, 0, 1));
	}

	/** A pilaster stands against the wall every six blocks. */
	private boolean pilaster(int bx, int bz) {
		if (tent) {
			return false;
		}
		double x = x(bx);
		double z = z(bz);
		boolean byX = half - Math.abs(x) < 1;
		boolean byZ = half - Math.abs(z) < 1;
		return byX && Math.floorMod(bz - oz, 6) == 0 || byZ && Math.floorMod(bx - ox, 6) == 0;
	}

	private boolean beam(int bz, int y, int bx) {
		return !tent && y == ceiling(bx, bz) - 1 && Math.floorMod(bz - oz, 8) == 4;
	}

	private boolean solid(int x, int y, int z) {
		int f = floorTop(x, z);
		if (floorOnly) {
			return f != NONE && y < f;
		}
		if (f == NONE || y < f || y >= ceiling(x, z)) {
			return true;
		}
		return pilaster(x, z) || beam(z, y, x);
	}

	private BlockState material(int x, int y, int z) {
		int f = floorTop(x, z);
		if (f != NONE && y == f - 1) {
			if (hall.checker != null) {
				int t = Math.max(1, Math.round(hall.tile));
				boolean odd = ((Math.floorDiv(x - ox, t) + Math.floorDiv(z - oz, t)) & 1) == 1;
				return Palette.pick(odd ? checker : floor, Noise.at(x, 0, z, 3));
			}
			double patch = Noise.value(x * 0.12, 9, z * 0.12);
			return floor.length >= 2 && patch > 0.6 ? floor[1] : floor[0];
		}
		if (pilaster(x, z) || beam(z, y, x)) {
			return trim;
		}
		if (tent) {
			double a = Math.atan2(z(z), x(x));
			int band = (int) Math.floor((a + Math.PI) / (Math.PI * 2) * 16);
			return stripes[Math.floorMod(band, stripes.length)];
		}
		if (f != NONE && y >= ceiling(x, z)) {
			return Palette.pick(ceiling, Noise.at(x, y, z, 7));
		}
		return Palette.pick(wall, (float) Mth.clamp(Noise.value(x * 0.2, y * 0.2, z * 0.2) * 1.4 - 0.2, 0, 0.999));
	}

	// ---------------------------------------------------------------- building

	private SceneMesh build() {
		SceneLevel level = new SceneLevel(g.host, g.biome("minecraft:plains"), null, g.minY, g.height)
				.skyLight(dark ? 3 : floorOnly ? 15 : 9);
		level.fallback((x, y, z) -> solid(x, y, z) ? SceneLevel.FILL : SceneLevel.AIR);
		int reach = Mth.ceil(half) + 3;
		int lo = g.baseFace - 8;
		int hi = g.baseFace + Math.round(hall.height * 1.5f) + 2;
		for (int bx = Mth.floor(g.cx - reach); bx <= Mth.floor(g.cx + reach); bx++) {
			for (int bz = Mth.floor(g.cz - reach); bz <= Mth.floor(g.cz + reach); bz++) {
				for (int y = lo; y <= hi; y++) {
					if (solid(bx, y, bz) && touchesRoom(bx, y, bz)) {
						level.set(bx, y, bz, material(bx, y, bz));
					}
				}
			}
		}
		if (!floorOnly) {
			lamps(level);
		}
		level.spreadLight();
		return SceneBuilder.compile(List.of(new SceneBuilder.Part(level, 1)), Mth.floor(g.cx), Mth.floor(g.cz));
	}

	private boolean touchesRoom(int x, int y, int z) {
		for (Direction d : Direction.values()) {
			if (!solid(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Lamps hanging where the smooth hall has its lights (their glow is still drawn there), on chains; in a tent, one
	 * from the cone's top. Light blocks under the ceiling light the room evenly, none in a power cut.
	 */
	private void lamps(SceneLevel level) {
		BlockState lamp = Palette.block(hall.lampBlock, Blocks.LANTERN.defaultBlockState());
		int lampY = (int) Math.floor(g.baseAbs + hall.height - 1.6);
		if (tent) {
			hang(level, Mth.floor(g.cx), Mth.floor(g.cz), lampY + Math.round(hall.height * 0.5f), lamp);
		} else {
			for (int ix = -1; ix <= 1; ix += 2) {
				for (int iz = -1; iz <= 1; iz += 2) {
					for (int q = 0; q < 2; q++) {
						int bx = Mth.floor(g.cx + ix * half * (0.25 + 0.4 * q));
						int bz = Mth.floor(g.cz + iz * half * (0.25 + 0.4 * (1 - q)));
						hang(level, bx, bz, lampY, lamp);
					}
				}
			}
		}
		if (dark) {
			return;
		}
		int lightY = g.baseFace + Math.round(hall.height) - 3;
		int step = 7;
		for (int dx = -(int) half; dx <= half; dx += step) {
			for (int dz = -(int) half; dz <= half; dz += step) {
				int bx = Mth.floor(g.cx) + dx;
				int bz = Mth.floor(g.cz) + dz;
				if (inRoom(bx, bz) && level.stored(bx, lightY, bz) == null) {
					level.set(bx, lightY, bz, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 13));
				}
			}
		}
	}

	/** A chain from the ceiling down to {@code y}, the lamp at its end (none lit in a power cut: the chain alone). */
	private void hang(SceneLevel level, int bx, int bz, int y, BlockState lamp) {
		int c = ceiling(bx, bz);
		if (floorTop(bx, bz) == NONE || y >= c || y <= floorTop(bx, bz) + 2) {
			return;
		}
		for (int k = c - 1; k > y; k--) {
			if (level.stored(bx, k, bz) == null) {
				level.set(bx, k, bz, Blocks.CHAIN.defaultBlockState());
			}
		}
		level.set(bx, y, bz, dark ? Blocks.CHAIN.defaultBlockState() : lamp);
	}
}
