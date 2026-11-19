package com.mystaria.showtime.ambiance.blocks;

import java.util.List;
import java.util.function.DoubleBinaryOperator;
import java.util.function.Supplier;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.mystaria.showtime.ambiance.Ambiance;

/**
 * A hall stadium built of blocks (2026-11-19): a room ({@code box}: walls with pilasters, a ceiling with beams and
 * hanging lamps) or a circus tent (a round wall striped in wool, a stepped cone over it), the floor plain or a
 * checkerboard on the world's grid. As in a cave, only the inner skin is stored and nothing faces outwards, so a camera
 * outside sees in.
 * <p>
 * Mirrors are glass walls with the room built again, mirrored, behind each (and behind each corner, the reflection of
 * a reflection): real blocks, so shader packs light the reflection like the room, and their own reflections play on
 * the glass. The people in the room are drawn mirrored there by {@code BattleAmbiance}, across the planes
 * {@link #mirrorPlanes} gives.
 */
final class HallScene {

	private static final int NONE = Integer.MIN_VALUE;

	private final Ambiance.Hall hall;
	private final boolean dark;
	private final FieldGround g;
	private final boolean tent;
	private final boolean mirrors;
	private final double half;
	private final int ox;
	private final int oz;
	/** The glass columns (mirrors), or the first wall columns: west, east, north, south. */
	private final int wx0;
	private final int wx1;
	private final int wz0;
	private final int wz1;
	private final BlockState[] floor;
	private final BlockState[] checker;
	private final BlockState[] wall;
	private final BlockState[] ceiling;
	private final BlockState[] stripes;
	private final BlockState trim;
	private final BlockState glass;
	private final BlockState wainscot;
	private final BlockState pedestal;
	private final BlockState[] wallProps;
	private final BlockState carpet;
	private final Long2IntOpenHashMap floors = new Long2IntOpenHashMap();
	/** Lamps, chains and hidden lights in the room (not mirrored: the build mirrors them). */
	private final Long2ObjectOpenHashMap<BlockState> extras = new Long2ObjectOpenHashMap<>();

	private HallScene(Ambiance.Hall hall, boolean dark, Vec3 center, float fieldRadius, DoubleBinaryOperator ground) {
		this.hall = hall;
		this.dark = dark;
		this.half = fieldRadius + hall.margin;
		this.g = new FieldGround(center, fieldRadius, fieldRadius + 2.5, ground);
		this.tent = "tent".equals(hall.shape);
		this.mirrors = hall.mirrors && !tent;
		this.ox = (int) Math.round(center.x);
		this.oz = (int) Math.round(center.z);
		double[] planes = mirrorPlanes(center, half);
		this.wx0 = (int) Math.floor(planes[0]);
		this.wx1 = (int) Math.floor(planes[1]);
		this.wz0 = (int) Math.floor(planes[2]);
		this.wz1 = (int) Math.floor(planes[3]);
		this.floor = Palette.blocks(hall.floorBlocks, Blocks.SMOOTH_STONE.defaultBlockState());
		this.checker = Palette.blocks(hall.checkerBlocks, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
		this.wall = Palette.blocks(hall.wallBlocks, Blocks.STONE_BRICKS.defaultBlockState());
		this.ceiling = Palette.blocks(hall.ceilingBlocks, Blocks.DARK_OAK_PLANKS.defaultBlockState());
		this.stripes = Palette.blocks(hall.stripeBlocks, Blocks.WHITE_WOOL.defaultBlockState());
		this.trim = Palette.block(hall.trim, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
		this.glass = Palette.block(hall.mirrorBlock, Blocks.GLASS.defaultBlockState());
		this.wainscot = tent ? null : Palette.block(hall.wainscot, null);
		this.pedestal = tent ? null : Palette.block(hall.pedestal, null);
		this.wallProps = hall.wallProps == null || hall.wallProps.isEmpty() ? null : Palette.blocks(hall.wallProps, Blocks.AIR.defaultBlockState());
		this.carpet = tent ? null : Palette.block(hall.carpet, null);
		floors.defaultReturnValue(NONE + 1);
	}

	static Supplier<SceneMesh> recipe(Ambiance.Hall hall, boolean dark, Vec3 center, float fieldRadius, DoubleBinaryOperator ground) {
		HallScene scene = new HallScene(hall, dark, center, fieldRadius, ground);
		return scene::build;
	}

	/**
	 * A box hall's mirror planes (absolute x west, x east, z north, z south): the middle of its first wall columns, where
	 * the glass stands.
	 */
	static double[] mirrorPlanes(Vec3 center, double half) {
		return new double[] {Math.floor(center.x - half - 0.5) + 0.5, Math.ceil(center.x + half - 0.5) + 0.5,
				Math.floor(center.z - half - 0.5) + 0.5, Math.ceil(center.z + half - 0.5) + 0.5};
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

	/** A pilaster (a mirror's frame) stands against the wall every six blocks. */
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

	/** A pedestal against the wall between two pilasters (none in the corners). */
	private boolean pedestal(int bx, int bz) {
		if (pedestal == null) {
			return false;
		}
		boolean byX = half - Math.abs(x(bx)) < 1;
		boolean byZ = half - Math.abs(z(bz)) < 1;
		return byX != byZ && (byX ? Math.floorMod(bz - oz, 6) == 3 : Math.floorMod(bx - ox, 6) == 3);
	}

	private boolean beam(int bz, int y, int bx) {
		return !tent && y == ceiling(bx, bz) - 1 && Math.floorMod(bz - oz, 8) == 4;
	}

	private boolean solid(int x, int y, int z) {
		int f = floorTop(x, z);
		if (f == NONE || y < f || y >= ceiling(x, z)) {
			return true;
		}
		return pilaster(x, z) || beam(z, y, x) || y == f && pedestal(x, z);
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
		if (f != NONE && y == f && pedestal(x, z)) {
			return pedestal;
		}
		if (f == NONE && !tent) {
			// The walls' foot and top: a wainscot under the mirrors, a cornice under the ceiling.
			if (wainscot != null && y < g.baseFace + hall.wainscotHeight) {
				return wainscot;
			}
			if (hall.cornice && y == g.baseFace + Math.round(hall.height) - 1) {
				return trim;
			}
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

	// ---------------------------------------------------------------- mirrors

	/** A coordinate folded back into the room across the mirror it lies behind (one bounce each way). */
	private static int fold(int v, int lo, int hi) {
		return v > hi ? 2 * hi - v : v < lo ? 2 * lo - v : v;
	}

	/** Whether a column lies within the room and its reflections (beyond: solid). */
	private boolean inReflections(int x, int z) {
		return x >= 2 * wx0 - wx1 && x <= 2 * wx1 - wx0 && z >= 2 * wz0 - wz1 && z <= 2 * wz1 - wz0;
	}

	/** The glass of a mirror at a room column (not a corner), between floor and ceiling. */
	private boolean mirrorAt(int sx, int y, int sz) {
		boolean onX = sx == wx0 || sx == wx1;
		boolean onZ = sz == wz0 || sz == wz1;
		if (onX == onZ) {
			return false;
		}
		int inX = onX ? (sx == wx0 ? sx + 1 : sx - 1) : sx;
		int inZ = onZ ? (sz == wz0 ? sz + 1 : sz - 1) : sz;
		int f = floorTop(inX, inZ);
		return f != NONE && y >= f + (wainscot != null ? hall.wainscotHeight : 0)
				&& y < ceiling(inX, inZ) - (hall.cornice ? 1 : 0);
	}

	/** What stands at a block: null for air (open), else its block. */
	private BlockState cell(int x, int y, int z) {
		if (!mirrors) {
			BlockState extra = extras.get(BlockPos.asLong(x, y, z));
			return extra != null ? extra : solid(x, y, z) ? material(x, y, z) : null;
		}
		if (!inReflections(x, z)) {
			return SceneLevel.FILL;
		}
		int sx = fold(x, wx0, wx1);
		int sz = fold(z, wz0, wz1);
		if (mirrorAt(sx, y, sz)) {
			// A mirror stays glass where it really stands; the far side of a reflection is a wall.
			boolean real = sx == wx0 || sx == wx1 ? x == sx : z == sz;
			return real ? glass : material(sx, y, sz);
		}
		BlockState state = extras.get(BlockPos.asLong(sx, y, sz));
		if (state == null) {
			state = solid(sx, y, sz) ? material(sx, y, sz) : null;
		}
		if (state != null) {
			if (sx != x) {
				state = state.mirror(Mirror.FRONT_BACK);
			}
			if (sz != z) {
				state = state.mirror(Mirror.LEFT_RIGHT);
			}
		}
		return state;
	}

	/** Air, light, glass and lamps let the eye through: the faces behind them are drawn. */
	private boolean open(BlockState state) {
		return state == null || state == glass || !state.canOcclude();
	}

	// ---------------------------------------------------------------- building

	private SceneMesh build() {
		lamps();
		SceneLevel level = new SceneLevel(g.host, g.biome("minecraft:plains"), null, g.minY, g.height).skyLight(dark ? 3 : 9);
		level.fallback((x, y, z) -> open(cell(x, y, z)) ? SceneLevel.AIR : SceneLevel.FILL);
		int x0;
		int x1;
		int z0;
		int z1;
		if (mirrors) {
			x0 = 2 * wx0 - wx1;
			x1 = 2 * wx1 - wx0;
			z0 = 2 * wz0 - wz1;
			z1 = 2 * wz1 - wz0;
		} else {
			int reach = Mth.ceil(half) + 3;
			x0 = Mth.floor(g.cx - reach);
			x1 = Mth.floor(g.cx + reach);
			z0 = Mth.floor(g.cz - reach);
			z1 = Mth.floor(g.cz + reach);
		}
		int lo = g.baseFace - 8;
		int hi = g.baseFace + Math.round(hall.height * 1.5f) + 2;
		for (int bx = x0; bx <= x1; bx++) {
			for (int bz = z0; bz <= z1; bz++) {
				for (int y = lo; y <= hi; y++) {
					BlockState state = cell(bx, y, bz);
					if (state != null && (open(state) || touchesOpen(bx, y, bz))) {
						level.set(bx, y, bz, state);
					}
				}
			}
		}
		level.spreadLight();
		return SceneBuilder.compile(List.of(new SceneBuilder.Part(level, 1)), Mth.floor(g.cx), Mth.floor(g.cz));
	}

	private boolean touchesOpen(int x, int y, int z) {
		for (Direction d : Direction.values()) {
			if (open(cell(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ()))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Lamps hanging where the smooth hall has its lights (their glow is still drawn there), on chains; in a tent, one
	 * from the cone's top. Light blocks under the ceiling light the room evenly, none in a power cut.
	 */
	private void lamps() {
		BlockState lamp = Palette.block(hall.lampBlock, Blocks.LANTERN.defaultBlockState());
		int lampY = (int) Math.floor(g.baseAbs + hall.height - 1.6);
		if (tent) {
			hang(Mth.floor(g.cx), Mth.floor(g.cz), lampY + Math.round(hall.height * 0.5f), lamp);
		} else {
			for (int ix = -1; ix <= 1; ix += 2) {
				for (int iz = -1; iz <= 1; iz += 2) {
					for (int q = 0; q < 2; q++) {
						int bx = Mth.floor(g.cx + ix * half * (0.25 + 0.4 * q));
						int bz = Mth.floor(g.cz + iz * half * (0.25 + 0.4 * (1 - q)));
						hang(bx, bz, lampY, lamp);
					}
				}
			}
		}
		furnish();
		if (dark) {
			return;
		}
		int lightY = g.baseFace + Math.round(hall.height) - 3;
		int step = 7;
		for (int dx = -(int) half; dx <= half; dx += step) {
			for (int dz = -(int) half; dz <= half; dz += step) {
				int bx = Mth.floor(g.cx) + dx;
				int bz = Mth.floor(g.cz) + dz;
				long key = BlockPos.asLong(bx, lightY, bz);
				if (inRoom(bx, bz) && !extras.containsKey(key) && !solid(bx, lightY, bz)) {
					extras.put(key, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 13));
				}
			}
		}
	}

	/** The things on the pedestals, in turn, and a carpet round the room two blocks from the walls. */
	private void furnish() {
		if (tent || wallProps == null && carpet == null) {
			return;
		}
		int reach = Mth.ceil(half);
		for (int bx = Mth.floor(g.cx) - reach; bx <= Mth.floor(g.cx) + reach; bx++) {
			for (int bz = Mth.floor(g.cz) - reach; bz <= Mth.floor(g.cz) + reach; bz++) {
				int f = floorTop(bx, bz);
				if (f == NONE) {
					continue;
				}
				if (wallProps != null && pedestal(bx, bz)) {
					BlockState prop = wallProps[Math.floorMod(bx * 31 + bz * 17, wallProps.length)];
					if (!prop.isAir()) {
						extras.put(BlockPos.asLong(bx, f + 1, bz), prop);
					}
				}
				double inset = Math.min(half - Math.abs(x(bx)), half - Math.abs(z(bz)));
				if (carpet != null && inset >= 2 && inset < 3 && !solid(bx, f, bz)) {
					extras.put(BlockPos.asLong(bx, f, bz), carpet);
				}
			}
		}
	}

	/** A chain from the ceiling down to {@code y}, the lamp at its end (none lit in a power cut: the chain alone). */
	private void hang(int bx, int bz, int y, BlockState lamp) {
		int c = ceiling(bx, bz);
		if (floorTop(bx, bz) == NONE || y >= c || y <= floorTop(bx, bz) + 2) {
			return;
		}
		for (int k = c - 1; k > y; k--) {
			if (!solid(bx, k, bz)) {
				extras.putIfAbsent(BlockPos.asLong(bx, k, bz), Blocks.CHAIN.defaultBlockState());
			}
		}
		extras.put(BlockPos.asLong(bx, y, bz), dark ? Blocks.CHAIN.defaultBlockState() : lamp);
		if (hall.chandeliers && !dark) {
			// A chandelier: rods of light out to the four sides and one down.
			BlockState rod = Blocks.END_ROD.defaultBlockState();
			for (Direction d : Direction.Plane.HORIZONTAL) {
				extras.put(BlockPos.asLong(bx + d.getStepX(), y, bz + d.getStepZ()), rod.setValue(net.minecraft.world.level.block.EndRodBlock.FACING, d));
			}
			extras.put(BlockPos.asLong(bx, y - 1, bz), rod.setValue(net.minecraft.world.level.block.EndRodBlock.FACING, Direction.DOWN));
		}
	}
}
