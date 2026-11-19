package com.mystaria.showtime.ambiance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Where an ambiance's props may go (2026-11-19, the split between stadiums and ambiances): surfaces round the field,
 * found by scanning the real blocks once when the ambiance starts, or given by a stadium. Positions are relative to the
 * field's centre, like everything the ambiance draws.
 * <p>
 * The scan keeps a face only when it lies in a cylinder round the field ({@code fieldRadius + 12} across, from 2 blocks
 * under the field to {@code reach} above it: a ceiling higher than that is open sky), outside the field and away from
 * the trainers, and when a line from above the field's centre reaches it without crossing a solid block (no prop inside
 * a wall or in the next room). Faces are thinned to about one every 1.5 blocks and ordered by a fixed hash, so the
 * same arena always gets the same props.
 */
public final class Anchors {

	public enum Kind {
		CEILING, WALL, FLOOR, FLOAT;

		static Kind of(String name) {
			return switch (name == null ? "" : name.toLowerCase(java.util.Locale.ROOT)) {
				case "ceiling" -> CEILING;
				case "wall" -> WALL;
				case "floor" -> FLOOR;
				case "float" -> FLOAT;
				default -> null;
			};
		}
	}

	/** A surface: where (relative to the field), which way it faces, how lit it is (0..1, the world's light). */
	public record Anchor(Vec3 at, Vec3 normal, float light) {
	}

	public static final int DEFAULT_REACH = 14;
	public static final int MAX_REACH = 32;
	private static final double SPACING = 1.5;

	private final Map<Kind, List<Anchor>> byKind = new EnumMap<>(Kind.class);
	private final Map<Kind, Integer> used = new EnumMap<>(Kind.class);

	public Anchors() {
		for (Kind kind : Kind.values()) {
			byKind.put(kind, new ArrayList<>());
			used.put(kind, 0);
		}
	}

	public void add(Kind kind, Anchor anchor) {
		byKind.get(kind).add(anchor);
	}

	public List<Anchor> all(Kind kind) {
		return byKind.get(kind);
	}

	/** The next unused anchor of this kind, or null when none is left. */
	public Anchor take(Kind kind) {
		List<Anchor> list = byKind.get(kind);
		int next = used.get(kind);
		if (next >= list.size()) {
			return null;
		}
		used.put(kind, next + 1);
		return list.get(next);
	}

	/**
	 * Scans the real blocks round the field. {@code ground} is the field's ground height relative to its centre;
	 * {@code avoid} the trainers' positions (relative); {@code floatRadius} where floating anchors may go.
	 */
	public static Anchors scan(ClientLevel level, Vec3 center, float fieldRadius, double ground, Collection<Vec3> avoid,
			int reach) {
		Anchors anchors = new Anchors();
		int radius = Mth.ceil(fieldRadius + 12);
		int bottom = Mth.floor(center.y + ground) - 2;
		int top = Mth.floor(center.y + ground) + Mth.clamp(reach, 2, MAX_REACH);
		int cx = Mth.floor(center.x);
		int cz = Mth.floor(center.z);
		Vec3 eye = new Vec3(0, ground + 2, 0);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		Set<Long> taken = new HashSet<>();
		List<Object[]> found = new ArrayList<>();
		for (int x = cx - radius; x <= cx + radius; x++) {
			for (int z = cz - radius; z <= cz + radius; z++) {
				double dx = x + 0.5 - center.x;
				double dz = z + 0.5 - center.z;
				double flat = Math.sqrt(dx * dx + dz * dz);
				if (flat > radius) {
					continue;
				}
				for (int y = bottom; y <= top; y++) {
					if (!solid(level, pos.set(x, y, z))) {
						continue;
					}
					double ry = y - center.y;
					// Top face: a floor, beyond the field.
					if (flat > fieldRadius + 0.8 && !solid(level, pos.set(x, y + 1, z))) {
						found.add(new Object[] {Kind.FLOOR, new Vec3(dx, ry + 1, dz), new Vec3(0, 1, 0), new BlockPos(x, y + 1, z)});
					}
					// Bottom face: a ceiling, high enough over its floor to hang something.
					if (!solid(level, pos.set(x, y - 1, z)) && ry - ground >= 3) {
						found.add(new Object[] {Kind.CEILING, new Vec3(dx, ry, dz), new Vec3(0, -1, 0), new BlockPos(x, y - 1, z)});
					}
					// Side faces: walls, from the field's ground up.
					if (ry + 1 > ground + 0.5) {
						int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
						for (int[] side : sides) {
							if (!solid(level, pos.set(x + side[0], y, z + side[1]))) {
								found.add(new Object[] {Kind.WALL, new Vec3(dx + side[0] * 0.5, ry + 0.5, dz + side[1] * 0.5),
										new Vec3(side[0], 0, side[1]), new BlockPos(x + side[0], y, z + side[1])});
							}
						}
					}
				}
			}
		}
		// A fixed order (by a hash of the place), then thinning and the checks, cheapest first.
		found.sort((a, b) -> Integer.compare(hash((Vec3) a[1]), hash((Vec3) b[1])));
		for (Object[] f : found) {
			Kind kind = (Kind) f[0];
			Vec3 at = (Vec3) f[1];
			Vec3 normal = (Vec3) f[2];
			long cell = cell(kind, at);
			if (taken.contains(cell)) {
				continue;
			}
			boolean nearTrainer = false;
			for (Vec3 trainer : avoid) {
				double ax = at.x - trainer.x;
				double az = at.z - trainer.z;
				if (ax * ax + az * az < 4) {
					nearTrainer = true;
					break;
				}
			}
			if (nearTrainer || !seen(level, center, eye, at.add(normal.scale(0.3)))) {
				continue;
			}
			taken.add(cell);
			BlockPos air = (BlockPos) f[3];
			float light = level.getMaxLocalRawBrightness(air) / 15f;
			anchors.add(kind, new Anchor(at, normal, Mth.clamp(light, 0.15f, 1f)));
		}
		anchors.fillFloat(fieldRadius, ground);
		return anchors;
	}

	/** Floating anchors: points in the air round the field, always there (the last resort of crystals). */
	public void fillFloat(float fieldRadius, double ground) {
		List<Anchor> floating = byKind.get(Kind.FLOAT);
		floating.clear();
		for (int i = 0; i < 96; i++) {
			double angle = Math.PI * 2 * (i + 0.6 * h(i * 31 + 1)) / 96 * 7 % (Math.PI * 2);
			double r = fieldRadius + 2 + 8 * h(i * 31 + 2);
			double y = ground + 2 + 5 * h(i * 31 + 3);
			floating.add(new Anchor(new Vec3(Math.cos(angle) * r, y, Math.sin(angle) * r), new Vec3(0, 1, 0), 1f));
		}
	}

	private static boolean solid(ClientLevel level, BlockPos pos) {
		return level.getBlockState(pos).isSolidRender(level, pos);
	}

	/** Whether a line from {@code eye} to {@code at} (both relative) crosses no solid block. */
	private static boolean seen(ClientLevel level, Vec3 center, Vec3 eye, Vec3 at) {
		Vec3 step = at.subtract(eye);
		double length = step.length();
		int steps = (int) Math.ceil(length / 0.35);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int s = 1; s < steps; s++) {
			Vec3 p = center.add(eye.add(step.scale(s / (double) steps)));
			if (solid(level, pos.set(Mth.floor(p.x), Mth.floor(p.y), Mth.floor(p.z)))) {
				return false;
			}
		}
		return true;
	}

	private static long cell(Kind kind, Vec3 at) {
		long x = (long) Math.floor(at.x / SPACING);
		long y = (long) Math.floor(at.y / SPACING);
		long z = (long) Math.floor(at.z / SPACING);
		return ((x & 0xFFFFF) << 42) | ((y & 0x3FFF) << 28) | ((z & 0xFFFFF) << 8) | kind.ordinal();
	}

	private static int hash(Vec3 at) {
		int x = (int) Math.floor(at.x * 2) * 73856093 ^ (int) Math.floor(at.y * 2) * 19349663 ^ (int) Math.floor(at.z * 2) * 83492791;
		x ^= x >>> 15;
		x *= 0x85EBCA77;
		return x ^ (x >>> 13);
	}

	private static double h(int seed) {
		int x = seed * 0x9E3779B1;
		x ^= x >>> 15;
		x *= 0x85EBCA77;
		x ^= x >>> 13;
		return (x & 0xFFFFFF) / (double) 0x1000000;
	}

	static Kind kind(String name) {
		return Kind.of(name);
	}
}
