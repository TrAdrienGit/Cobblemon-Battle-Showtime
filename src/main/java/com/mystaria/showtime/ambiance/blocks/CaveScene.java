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
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.phys.Vec3;

import com.mystaria.showtime.ambiance.Ambiance;

/**
 * A cave stadium built of blocks (2026-11-19): the floor on the real ground, a vault of rock in strata closing over the
 * field, pillars, dripstone hanging and rising, a lava lake when the cave has one. Only the rock's inner skin is stored,
 * and the rock behind it reads solid: no face looks outwards, so a camera outside the vault sees through it into the
 * cave (the smooth vault's cutaway, for free).
 * <p>
 * Lit like a cave: little sky light, the field lit by hidden light blocks (the stadium's warm light), the lava glowing.
 */
final class CaveScene {

	private final Ambiance.Cavern cavern;
	private final FieldGround g;
	private final Holder<Biome> biome;
	private final double radius;
	private final double lavaIn;
	private final double lavaOut;
	private final float lightStrength;
	private final BlockState[] floor;
	private final BlockState[] rock;
	private final BlockState[] bands;
	private final Long2IntOpenHashMap floors = new Long2IntOpenHashMap();
	private final Long2IntOpenHashMap ceilings = new Long2IntOpenHashMap();

	private CaveScene(Ambiance.Cavern cavern, float lightStrength, Vec3 center, float fieldRadius, DoubleBinaryOperator ground) {
		this.cavern = cavern;
		this.lightStrength = lightStrength;
		this.radius = fieldRadius + cavern.margin;
		this.g = new FieldGround(center, fieldRadius, radius + 3, ground);
		this.biome = g.biome("minecraft:dripstone_caves");
		this.lavaIn = cavern.lava == null ? Double.MAX_VALUE : fieldRadius + cavern.lava.gap;
		this.lavaOut = cavern.lava == null ? Double.MAX_VALUE : Math.min(radius + 2, lavaIn + cavern.lava.width);
		Ambiance.Ground blocks = cavern.blocks;
		this.floor = blocks != null ? Palette.blocks(blocks.surface, Blocks.STONE.defaultBlockState())
				: new BlockState[] {Blocks.STONE.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(), Blocks.TUFF.defaultBlockState()};
		this.rock = blocks != null ? Palette.blocks(blocks.rock, Blocks.STONE.defaultBlockState())
				: new BlockState[] {Blocks.STONE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(), Blocks.TUFF.defaultBlockState()};
		this.bands = blocks != null && blocks.bands != null && !blocks.bands.isEmpty() ? Palette.blocks(blocks.bands, rock[0]) : null;
		floors.defaultReturnValue(Integer.MIN_VALUE);
		ceilings.defaultReturnValue(Integer.MIN_VALUE);
	}

	static Supplier<SceneMesh> recipe(Ambiance.Cavern cavern, float lightStrength, Vec3 center, float fieldRadius,
			DoubleBinaryOperator ground) {
		CaveScene scene = new CaveScene(cavern, lightStrength, center, fieldRadius, ground);
		return scene::build;
	}

	// ---------------------------------------------------------------- the cave's shape

	/** The vault's unevenness towards {@code angle}, as the smooth vault has it at its foot. */
	private static double bump(double angle) {
		return 1 + 0.09 * Math.sin(3 * angle + 1.3) * Math.cos(5 * angle) + 0.05 * Math.sin(11 * angle) + 0.04 * Math.sin(17 * angle + 0.5);
	}

	/** The floor's top (first air y) of a column, or MIN_VALUE where the rock is whole (outside the cave). */
	private int floorTop(int bx, int bz) {
		shape(bx, bz);
		return floors.get(SceneLevel.column(bx, bz));
	}

	/** The vault's underside (first rock y above the floor) of a column. */
	private int ceiling(int bx, int bz) {
		shape(bx, bz);
		return ceilings.get(SceneLevel.column(bx, bz));
	}

	private void shape(int bx, int bz) {
		long key = SceneLevel.column(bx, bz);
		if (floors.containsKey(key)) {
			return;
		}
		double x = bx + 0.5 - g.cx;
		double z = bz + 0.5 - g.cz;
		double a = Math.atan2(z, x);
		double c = Math.pow(Mth.clamp(Math.sqrt(x * x + z * z) / (radius * bump(a)), 0, 1), 1 / 0.55);
		double vault = g.cy - 3 + (cavern.height + 3) * Math.sin(Math.acos(c)) * (1 + 0.05 * Math.sin(7 * a));
		int top = g.top(bx, bz);
		int ceil = (int) Math.floor(vault + 0.5);
		if (c >= 1 || ceil - top < 2) {
			floors.put(key, Integer.MIN_VALUE);
			ceilings.put(key, Integer.MIN_VALUE);
		} else {
			floors.put(key, top);
			ceilings.put(key, ceil);
		}
	}

	/** Whether a block is the cave's rock: everything but the cave's room. */
	private boolean solid(int x, int y, int z) {
		int f = floorTop(x, z);
		return f == Integer.MIN_VALUE || y < f || y >= ceiling(x, z);
	}

	private BlockState rockAt(int x, int y, int z) {
		if (bands != null) {
			int shift = (int) Math.floor(3 * Noise.value(x * 0.05, 11, z * 0.05));
			return bands[Math.floorMod(Math.floorDiv(y + shift, 2), bands.length)];
		}
		return Palette.pick(rock, (float) Mth.clamp(Noise.value(x * 0.13, y * 0.13, z * 0.13) * 1.4 - 0.2, 0, 0.999));
	}

	private BlockState floorAt(int x, int y, int z) {
		double patch = Noise.value(x * 0.09, 9, z * 0.09);
		double patch2 = Noise.value(x * 0.21 + 30, 9, z * 0.21);
		if (floor.length >= 3 && patch2 > 0.68) {
			return floor[2];
		}
		return floor.length >= 2 && patch > 0.55 ? floor[1] : floor[0];
	}

	// ---------------------------------------------------------------- building

	private SceneMesh build() {
		int sky = Mth.clamp(Math.round(cavern.ambient * 15), 2, 15);
		SceneLevel level = new SceneLevel(g.host, biome, null, g.minY, g.height).skyLight(sky);
		level.fallback((x, y, z) -> solid(x, y, z) ? SceneLevel.FILL : SceneLevel.AIR);
		int reach = Mth.ceil(radius * 1.2) + 2;
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		for (int bx = Mth.floor(g.cx - reach); bx <= Mth.floor(g.cx + reach); bx++) {
			for (int bz = Mth.floor(g.cz - reach); bz <= Mth.floor(g.cz + reach); bz++) {
				int f = floorTop(bx, bz);
				if (f != Integer.MIN_VALUE) {
					lo = Math.min(lo, f - 1);
					hi = Math.max(hi, ceiling(bx, bz));
				}
			}
		}
		if (lo > hi) {
			return SceneBuilder.compile(List.of(), Mth.floor(g.cx), Mth.floor(g.cz));
		}
		// The rock's skin: every rock block with the room beside it.
		for (int bx = Mth.floor(g.cx - reach); bx <= Mth.floor(g.cx + reach); bx++) {
			for (int bz = Mth.floor(g.cz - reach); bz <= Mth.floor(g.cz + reach); bz++) {
				int f = floorTop(bx, bz);
				for (int y = lo; y <= hi; y++) {
					if (!solid(bx, y, bz) || !touchesRoom(bx, y, bz)) {
						continue;
					}
					boolean isFloor = f != Integer.MIN_VALUE && y == f - 1;
					double r = g.distance(bx + 0.5, bz + 0.5);
					BlockState state = isFloor ? floorAt(bx, y, bz) : rockAt(bx, y, bz);
					if (isFloor && r > lavaIn && r < lavaOut) {
						state = Blocks.LAVA.defaultBlockState();
						level.set(bx, y - 1, bz, Blocks.MAGMA_BLOCK.defaultBlockState());
					}
					level.set(bx, y, bz, state);
				}
			}
		}
		// The field's light: hidden light blocks over it, as strong as the stadium's light is.
		int glow = Mth.clamp(Math.round(9 + lightStrength * 8), 9, 15);
		int lampY = g.baseFace + 3;
		level.set(Mth.floor(g.cx), lampY, Mth.floor(g.cz), Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, glow));
		for (int i = 0; i < 6; i++) {
			double a = Math.PI * 2 * i / 6;
			double r = g.field * 0.75;
			level.set(Mth.floor(g.cx + Math.cos(a) * r), lampY, Mth.floor(g.cz + Math.sin(a) * r),
					Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, glow - 2));
		}
		plants(level);
		level.spreadLight();
		List<SceneBuilder.Part> parts = new ArrayList<>();
		parts.add(new SceneBuilder.Part(level, 1));
		SceneLevel objectsLevel = new SceneLevel(g.host, biome, null, g.minY, g.height).skyLight(sky).lightOf(level);
		objectsLevel.fallback((x, y, z) -> solid(x, y, z) ? SceneLevel.FILL : SceneLevel.AIR);
		SceneBuilder.Part objects = new SceneBuilder.Part(objectsLevel, 1);
		pillars(objects);
		if (cavern.dripstone) {
			dripstone(objects);
		}
		parts.add(objects);
		return SceneBuilder.compile(parts, Mth.floor(g.cx), Mth.floor(g.cz));
	}

	private boolean touchesRoom(int x, int y, int z) {
		for (Direction d : Direction.values()) {
			if (!solid(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ())) {
				return true;
			}
		}
		return false;
	}

	private void plants(SceneLevel level) {
		List<LandScene.Plants.Kind> kinds = LandScene.Plants.of(cavern.plants);
		if (kinds.isEmpty()) {
			return;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int reach = Mth.ceil(radius) + 2;
		for (int bx = Mth.floor(g.cx - reach); bx <= Mth.floor(g.cx + reach); bx++) {
			for (int bz = Mth.floor(g.cz - reach); bz <= Mth.floor(g.cz + reach); bz++) {
				int f = floorTop(bx, bz);
				if (f == Integer.MIN_VALUE) {
					continue;
				}
				double r = g.distance(bx + 0.5, bz + 0.5);
				try {
					LandScene.Plants.place(level, pos, bx, f, bz, r, r <= g.from ? 0.3f : 1f, kinds, false);
				} catch (RuntimeException ex) {
					// A modded plant asking the world for more than blocks: no plant there.
				}
			}
		}
	}

	private static void add(SceneBuilder.Part part, Long2ObjectOpenHashMap<BlockState> blocks) {
		LongArrayList keys = new LongArrayList();
		for (var entry : blocks.long2ObjectEntrySet()) {
			if (part.level().blocks.putIfAbsent(entry.getLongKey(), entry.getValue()) == null) {
				keys.add(entry.getLongKey());
			}
		}
		if (!keys.isEmpty()) {
			part.objects().add(keys);
		}
	}

	/** Pillars where the smooth cave has them: a stalagmite and a stalactite grown into one, thick at both ends. */
	private void pillars(SceneBuilder.Part part) {
		BlockState block = Palette.block(cavern.pillar, Blocks.DRIPSTONE_BLOCK.defaultBlockState());
		for (int i = 0; i < cavern.pillars; i++) {
			double a = Math.PI * 2 * (i + 0.5 * Noise.hash(i * 23 + 1)) / Math.max(1, cavern.pillars) + 0.4;
			double r = radius * (0.74 + 0.1 * Noise.hash(i * 23 + 2));
			int bx = Mth.floor(g.cx + Math.cos(a) * r);
			int bz = Mth.floor(g.cz + Math.sin(a) * r);
			int f = floorTop(bx, bz);
			if (f == Integer.MIN_VALUE) {
				continue;
			}
			int c = ceiling(bx, bz);
			Long2ObjectOpenHashMap<BlockState> pillar = new Long2ObjectOpenHashMap<>();
			for (int y = f; y < c; y++) {
				double k = (y - f) / (double) Math.max(1, c - 1 - f);
				double w = 0.6 + 1.3 * Math.pow(Math.abs(k - 0.5) * 2, 2);
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						if (dx * dx + dz * dz <= w * w + 0.3 * Noise.at(bx + dx, y, bz + dz, i)) {
							pillar.put(BlockPos.asLong(bx + dx, y, bz + dz), block);
						}
					}
				}
			}
			add(part, pillar);
		}
	}

	/** Pointed dripstone hanging from the vault and rising from the floor round the field (never on it). */
	private void dripstone(SceneBuilder.Part part) {
		int count = (int) (radius * 2.2);
		for (int i = 0; i < count; i++) {
			boolean down = i % 3 != 0;
			double a = Math.PI * 2 * Noise.hash(i * 31 + 1);
			double r = down ? radius * Math.sqrt(Noise.hash(i * 31 + 2)) * 0.95 : g.field + 2 + (radius - g.field - 2) * Noise.hash(i * 31 + 2);
			int bx = Mth.floor(g.cx + Math.cos(a) * r);
			int bz = Mth.floor(g.cz + Math.sin(a) * r);
			int f = floorTop(bx, bz);
			if (f == Integer.MIN_VALUE) {
				continue;
			}
			int c = ceiling(bx, bz);
			int room = c - f;
			int length = Math.min(1 + (int) (Noise.hash(i * 31 + 3) * 4), Math.max(1, room / 3));
			Direction dir = down ? Direction.DOWN : Direction.UP;
			int start = down ? c - 1 : f;
			Long2ObjectOpenHashMap<BlockState> drip = new Long2ObjectOpenHashMap<>();
			for (int k = 0; k < length; k++) {
				int fromTip = length - 1 - k;
				DripstoneThickness thickness = fromTip == 0 ? DripstoneThickness.TIP
						: fromTip == 1 ? DripstoneThickness.FRUSTUM : k == 0 ? DripstoneThickness.BASE : DripstoneThickness.MIDDLE;
				drip.put(BlockPos.asLong(bx, start + (down ? -k : k), bz), Blocks.POINTED_DRIPSTONE.defaultBlockState()
						.setValue(PointedDripstoneBlock.TIP_DIRECTION, dir).setValue(PointedDripstoneBlock.THICKNESS, thickness));
			}
			add(part, drip);
		}
	}
}
