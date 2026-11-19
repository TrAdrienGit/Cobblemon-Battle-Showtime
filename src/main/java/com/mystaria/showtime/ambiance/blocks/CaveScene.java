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
	/** The ambiance's stalactites and stalagmites: x, z (from the centre), length, 1 hanging / 0 rising, in fours. */
	private final List<Float> stalks;
	private final BlockState[] floor;
	private final BlockState[] rock;
	private final BlockState[] bands;
	private final Long2IntOpenHashMap floors = new Long2IntOpenHashMap();
	private final Long2IntOpenHashMap ceilings = new Long2IntOpenHashMap();

	private CaveScene(Ambiance.Cavern cavern, float lightStrength, List<Float> stalks, Vec3 center, float fieldRadius,
			DoubleBinaryOperator ground) {
		this.cavern = cavern;
		this.lightStrength = lightStrength;
		this.stalks = stalks;
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

	static Supplier<SceneMesh> recipe(Ambiance.Cavern cavern, float lightStrength, List<Float> stalks, Vec3 center,
			float fieldRadius, DoubleBinaryOperator ground) {
		CaveScene scene = new CaveScene(cavern, lightStrength, stalks, center, fieldRadius, ground);
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
		stalks(objects);
		if (cavern.hoard != null) {
			hoard(objects);
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

	/**
	 * The ambiance's stalactites and stalagmites where it places them (its water drips from their tips): pointed
	 * dripstone, the long ones on a dripstone block base, the longest widened at the root.
	 */
	private void stalks(SceneBuilder.Part part) {
		BlockState block = Blocks.DRIPSTONE_BLOCK.defaultBlockState();
		for (int i = 0; i + 3 < stalks.size(); i += 4) {
			int bx = Mth.floor(g.cx + stalks.get(i));
			int bz = Mth.floor(g.cz + stalks.get(i + 1));
			boolean down = stalks.get(i + 3) > 0.5f;
			int f = floorTop(bx, bz);
			if (f == Integer.MIN_VALUE) {
				continue;
			}
			int c = ceiling(bx, bz);
			int length = Mth.clamp(Math.round(stalks.get(i + 2)), 1, Math.max(1, (c - f) / 2));
			int start = down ? c - 1 : f;
			int step = down ? -1 : 1;
			int base = length >= 3 ? length / 3 : 0;
			Long2ObjectOpenHashMap<BlockState> stalk = new Long2ObjectOpenHashMap<>();
			for (int k = 0; k < length; k++) {
				int y = start + step * k;
				if (k < base) {
					stalk.put(BlockPos.asLong(bx, y, bz), block);
					if (k == 0 && length >= 4) {
						for (Direction d : Direction.Plane.HORIZONTAL) {
							stalk.put(BlockPos.asLong(bx + d.getStepX(), y, bz + d.getStepZ()), block);
						}
					}
					continue;
				}
				int fromTip = length - 1 - k;
				DripstoneThickness thickness = fromTip == 0 ? DripstoneThickness.TIP
						: fromTip == 1 ? DripstoneThickness.FRUSTUM : k == base ? DripstoneThickness.BASE : DripstoneThickness.MIDDLE;
				stalk.put(BlockPos.asLong(bx, y, bz), Blocks.POINTED_DRIPSTONE.defaultBlockState()
						.setValue(PointedDripstoneBlock.TIP_DIRECTION, down ? Direction.DOWN : Direction.UP)
						.setValue(PointedDripstoneBlock.THICKNESS, thickness));
			}
			add(part, stalk);
		}
	}

	/**
	 * A dragon's hoard (as the smooth one, same places): piles of gold blocks with gold plates for coins round them, and
	 * the skeleton along the ring, a fossil of bone blocks, sunk in the lava when there is some.
	 */
	private void hoard(SceneBuilder.Part part) {
		Ambiance.Hoard hoard = cavern.hoard;
		BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
		BlockState raw = Blocks.RAW_GOLD_BLOCK.defaultBlockState();
		BlockState coins = Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE.defaultBlockState();
		double from = g.field + 0.5;
		double to = lavaIn == Double.MAX_VALUE ? radius - 2 : lavaIn - 0.4;
		for (int i = 0; i < hoard.piles; i++) {
			double a = Math.PI * 2 * (i + 0.6 * Noise.hash(i * 61 + 1)) / Math.max(1, hoard.piles) + 1.1;
			double r = Mth.lerp(Noise.hash(i * 61 + 2), from, Math.max(from, to));
			int bx = Mth.floor(g.cx + Math.cos(a) * r);
			int bz = Mth.floor(g.cz + Math.sin(a) * r);
			double h = 0.45 + 0.6 * Noise.hash(i * 61 + 3);
			double width = 0.7 + 0.8 * Noise.hash(i * 61 + 4);
			int f = floorTop(bx, bz);
			if (f == Integer.MIN_VALUE) {
				continue;
			}
			Long2ObjectOpenHashMap<BlockState> pile = new Long2ObjectOpenHashMap<>();
			pile.put(BlockPos.asLong(bx, f, bz), gold);
			if (h > 0.8) {
				pile.put(BlockPos.asLong(bx, f + 1, bz), Noise.hash(i * 61 + 5) < 0.5 ? gold : raw);
			}
			int reach = (int) Math.ceil(width + 1);
			for (int dx = -reach; dx <= reach; dx++) {
				for (int dz = -reach; dz <= reach; dz++) {
					int d2 = dx * dx + dz * dz;
					if (d2 == 0 || floorTop(bx + dx, bz + dz) != f) {
						continue;
					}
					float roll = Noise.at(bx + dx, f, bz + dz, 61 + i);
					if (d2 <= width * width && roll < 0.75f) {
						pile.put(BlockPos.asLong(bx + dx, f, bz + dz), roll < 0.4f ? gold : raw);
					} else if (d2 <= (width + 1) * (width + 1) && roll < 0.45f) {
						pile.put(BlockPos.asLong(bx + dx, f, bz + dz), coins);
					}
				}
			}
			add(part, pile);
		}
		if (hoard.bones) {
			bones(part);
		}
	}

	/** The skeleton: spine, ribs arching up, a skull with a snout and two horns, as the smooth one lies. */
	private void bones(SceneBuilder.Part part) {
		double rs = lavaIn == Double.MAX_VALUE ? (g.field + radius) / 2 : (lavaIn + lavaOut) / 2;
		boolean sunk = lavaIn != Double.MAX_VALUE;
		int vertebrae = 18;
		double span = 13 / rs;
		Vec3[] spine = new Vec3[vertebrae];
		for (int k = 0; k < vertebrae; k++) {
			double a = 2.3 + span * k / (vertebrae - 1);
			double r = rs + Math.sin(k * 0.5) * 0.6;
			double x = Math.cos(a) * r;
			double z = Math.sin(a) * r;
			int f = floorTop(Mth.floor(g.cx + x), Mth.floor(g.cz + z));
			double floorY = f == Integer.MIN_VALUE ? g.baseFace : f;
			spine[k] = new Vec3(x, floorY + (sunk ? -0.5 : 0.5) + 0.15 * Math.sin(k * 0.8), z);
		}
		Long2ObjectOpenHashMap<BlockState> bone = new Long2ObjectOpenHashMap<>();
		for (int k = 0; k + 1 < vertebrae; k++) {
			line(bone, spine[k], spine[k + 1]);
		}
		for (int k = 3; k < 11; k++) {
			Vec3 tangent = spine[k + 1].subtract(spine[k - 1]).normalize();
			Vec3 side = tangent.cross(new Vec3(0, 1, 0)).normalize();
			double reach = 1.6 + 1.2 * Math.sin(Math.PI * (k - 3) / 8.0);
			for (int s = -1; s <= 1; s += 2) {
				Vec3 prev = spine[k];
				for (int q = 1; q <= 7; q++) {
					double t = q / 7.0;
					Vec3 p = spine[k].add(side.scale(s * reach * t)).add(0, reach * 1.3 * Math.sin(Math.PI * t * 0.95), 0)
							.add(tangent.scale(-0.4 * t));
					line(bone, prev, p);
					prev = p;
				}
			}
		}
		Vec3 head = spine[0];
		Vec3 along = spine[0].subtract(spine[1]).normalize();
		Vec3 outward = new Vec3(head.x, 0, head.z).normalize();
		Vec3 cranium = head.add(0, 0.7, 0);
		line(bone, cranium.add(0, -0.5, 0), cranium.add(0, 0.5, 0));
		line(bone, cranium.add(along.scale(0.4)), cranium.add(along.scale(2.1)).add(0, -0.35, 0));
		for (int s = -1; s <= 1; s += 2) {
			Vec3 root = cranium.add(outward.scale(0.35 * s)).add(0, 0.35, 0);
			line(bone, root, root.add(along.scale(-1.9)).add(0, 1.1, 0).add(outward.scale(0.5 * s)));
		}
		add(part, bone);
	}

	/** Bone blocks along a segment (from the centre, y absolute), each turned along it. */
	private void line(Long2ObjectOpenHashMap<BlockState> into, Vec3 a, Vec3 b) {
		Vec3 d = b.subtract(a);
		Direction.Axis axis = Math.abs(d.y) >= Math.max(Math.abs(d.x), Math.abs(d.z)) ? Direction.Axis.Y
				: Math.abs(d.x) >= Math.abs(d.z) ? Direction.Axis.X : Direction.Axis.Z;
		BlockState state = Blocks.BONE_BLOCK.defaultBlockState().setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, axis);
		int steps = (int) Math.ceil(d.length() * 2) + 1;
		for (int i = 0; i <= steps; i++) {
			Vec3 p = a.add(d.scale(i / (double) steps));
			into.putIfAbsent(BlockPos.asLong(Mth.floor(g.cx + p.x), Mth.floor(p.y), Mth.floor(g.cz + p.z)), state);
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
