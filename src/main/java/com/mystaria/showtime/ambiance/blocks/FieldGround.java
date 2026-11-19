package com.mystaria.showtime.ambiance.blocks;

import java.util.function.DoubleBinaryOperator;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.phys.Vec3;

/**
 * The real ground a stadium is laid on (2026-11-19), read on the client thread before the stadium is built in the
 * background (the world is not safe to read there): its height under every block within {@code reach} of the centre,
 * and round the field's edge. Also what the build needs of the world: its biomes and height.
 */
final class FieldGround {

	static final int RIM_STEPS = 720;

	final Level host;
	final double cx;
	final double cy;
	final double cz;
	final double field;
	/** The field's edge: inside it, the real ground; outside, the stadium's. */
	final double from;
	final double baseAbs;
	final int baseFace;
	final int minY;
	final int height;
	private final Long2DoubleOpenHashMap real = new Long2DoubleOpenHashMap();
	private final double[] rim = new double[RIM_STEPS];

	FieldGround(Vec3 center, float fieldRadius, double reach, DoubleBinaryOperator ground) {
		Level level = Minecraft.getInstance().level;
		if (level == null) {
			throw new IllegalStateException("no world to build a stadium in");
		}
		this.host = level;
		this.cx = center.x;
		this.cy = center.y;
		this.cz = center.z;
		this.field = fieldRadius;
		this.from = fieldRadius + 0.5;
		this.baseAbs = center.y + ground.applyAsDouble(0, 0);
		this.baseFace = (int) Math.round(baseAbs);
		this.minY = level.getMinBuildHeight();
		this.height = level.getHeight();
		int r = Mth.ceil(reach);
		for (int bx = Mth.floor(cx - r); bx <= Mth.floor(cx + r); bx++) {
			for (int bz = Mth.floor(cz - r); bz <= Mth.floor(cz + r); bz++) {
				double x = bx + 0.5 - cx;
				double z = bz + 0.5 - cz;
				if (x * x + z * z <= reach * reach) {
					real.put(SceneLevel.column(bx, bz), center.y + ground.applyAsDouble(x, z));
				}
			}
		}
		for (int i = 0; i < RIM_STEPS; i++) {
			double a = Math.PI * 2 * i / RIM_STEPS;
			rim[i] = center.y + ground.applyAsDouble(Math.cos(a) * from, Math.sin(a) * from);
		}
	}

	/** The real ground's top under a block, as the y of the first air: a path's sliver counts, a slab's half not. */
	int top(int bx, int bz) {
		return Mth.floor(real.getOrDefault(SceneLevel.column(bx, bz), baseAbs) + 0.25);
	}

	/** The real ground's height (absolute) at the field's edge, towards (x, z) from the centre. */
	double rim(double x, double z) {
		return rim[Math.floorMod((int) Math.round(Math.atan2(z, x) / (Math.PI * 2) * RIM_STEPS), RIM_STEPS)];
	}

	double distance(double wx, double wz) {
		return Math.sqrt((wx - cx) * (wx - cx) + (wz - cz) * (wz - cz));
	}

	/** The biome named (its colours tint grass, leaves, water), plains when it names none. */
	Holder<Biome> biome(String name) {
		var registry = host.registryAccess().registryOrThrow(Registries.BIOME);
		ResourceLocation id = ResourceLocation.tryParse(name == null ? "minecraft:plains" : name);
		return (id == null ? java.util.Optional.<Holder.Reference<Biome>>empty() : registry.getHolder(ResourceKey.create(Registries.BIOME, id)))
				.or(() -> registry.getHolder(Biomes.PLAINS))
				.map(holder -> (Holder<Biome>) holder)
				.orElseThrow(() -> new IllegalStateException("no plains biome"));
	}

	/** Which resolution draws a world column: 1 block a cell near the centre, then 2, then 4 (whole cells, nested). */
	int scale(int bx, int bz) {
		double r4 = distance(Math.floorDiv(bx, 4) * 4 + 2, Math.floorDiv(bz, 4) * 4 + 2);
		if (r4 > 84) {
			return 4;
		}
		double r2 = distance(Math.floorDiv(bx, 2) * 2 + 1, Math.floorDiv(bz, 2) * 2 + 1);
		return r2 > 44 ? 2 : 1;
	}

	/** A ragged ball of {@code stone} (a boulder), its foot sunk in the ground ({@code groundTop}: first air y). */
	static void boulder(it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<net.minecraft.world.level.block.state.BlockState> out,
			double px, int ground, double pz, float size, int seed, net.minecraft.world.level.block.state.BlockState[] stone,
			java.util.function.IntBinaryOperator groundTop) {
		double rx = 0.7 + size * 0.9;
		double ry = 0.6 + size * 0.7;
		double rz = 0.7 + size * 0.85;
		double py = ground + ry * 0.35 - 0.5;
		for (int x = Mth.floor(px - rx); x <= Mth.floor(px + rx); x++) {
			for (int y = Mth.floor(py - ry); y <= Mth.floor(py + ry); y++) {
				for (int z = Mth.floor(pz - rz); z <= Mth.floor(pz + rz); z++) {
					if (y < groundTop.applyAsInt(x, z) - 1) {
						continue;
					}
					double dx = (x + 0.5 - px) / rx;
					double dy = (y + 0.5 - py) / ry;
					double dz = (z + 0.5 - pz) / rz;
					if (dx * dx + dy * dy + dz * dz <= 1 - 0.3 * Noise.at(x, y, z, seed)) {
						out.put(net.minecraft.core.BlockPos.asLong(x, y, z), Palette.pick(stone, Noise.at(x, y, z, seed + 1)));
					}
				}
			}
		}
	}
}
