package com.mystaria.showtime.ambiance.blocks;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Trees built of logs and leaves like Minecraft's own (2026-11-19), in the shapes its saplings grow: a round oak,
 * a tall birch, a spruce in tiers, a dark oak on four logs, a leaning acacia, a pink cherry, a bare dead tree. Leaves
 * buried in the crown are left out (nobody sees them, and each costs faces).
 */
final class Trees {

	private Trees() {
	}

	/**
	 * The tree {@code species} standing on (x, y - 1, z), about {@code height} blocks tall, its blocks by position.
	 * {@code big}: a broader, older one; {@code charred}: burnt, no leaves.
	 */
	static Long2ObjectOpenHashMap<BlockState> grow(String species, int x, int y, int z, int height, boolean big, boolean charred,
			int seed) {
		Long2ObjectOpenHashMap<BlockState> tree = new Long2ObjectOpenHashMap<>();
		Builder b = new Builder(tree, seed);
		if (charred) {
			dead(b, x, y, z, height, Blocks.STRIPPED_DARK_OAK_LOG, seed);
			return tree;
		}
		switch (species) {
			case "birch" -> {
				int t = Mth.clamp(Math.round(height * 0.7f), 5, 9);
				b.trunk(x, y, z, t, Blocks.BIRCH_LOG);
				b.blob(x, y + t - 1, z, 2.2, 2.4, 2.2, Blocks.BIRCH_LEAVES);
				b.put(x, y + t + 1, z, Blocks.BIRCH_LEAVES);
			}
			case "spruce", "pine" -> spruce(b, x, y, z, Math.max(6, height), big);
			case "dark_oak" -> {
				int t = Mth.clamp(Math.round(height * 0.6f), 5, 11);
				for (int dx = 0; dx < 2; dx++) {
					for (int dz = 0; dz < 2; dz++) {
						b.trunk(x + dx, y, z + dz, t - (dx + dz == 2 ? 1 : 0), Blocks.DARK_OAK_LOG);
					}
				}
				double r = big ? 4.6 : 3.6;
				b.blob(x + 0.5, y + t, z + 0.5, r, big ? 2.4 : 1.9, r, Blocks.DARK_OAK_LEAVES);
				b.blob(x + 0.5 + (b.rand(1) - 0.5) * 3, y + t + 1, z + 0.5 + (b.rand(2) - 0.5) * 3, r * 0.6, 1.4, r * 0.6,
						Blocks.DARK_OAK_LEAVES);
				if (big) {
					branches(b, x, y, z, t, Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_LEAVES);
				}
			}
			case "jungle" -> {
				int t = Math.max(8, height);
				b.trunk(x, y, z, t, Blocks.JUNGLE_LOG);
				b.blob(x, y + t, z, 3.2, 2.0, 3.2, Blocks.JUNGLE_LEAVES);
				b.blob(x, y + t - 4, z, 2.0, 1.2, 2.0, Blocks.JUNGLE_LEAVES);
			}
			case "acacia" -> {
				int t = Mth.clamp(Math.round(height * 0.6f), 4, 8);
				Direction lean = Direction.from2DDataValue((int) (b.rand(3) * 4));
				int bend = t / 2;
				b.trunk(x, y, z, bend, Blocks.ACACIA_LOG);
				int tx = x;
				int tz = z;
				for (int i = bend; i < t; i++) {
					tx += lean.getStepX();
					tz += lean.getStepZ();
					b.put(tx, y + i, tz, Blocks.ACACIA_LOG);
				}
				b.disc(tx, y + t, tz, 3.2, Blocks.ACACIA_LEAVES);
				b.disc(tx, y + t + 1, tz, 2.1, Blocks.ACACIA_LEAVES);
			}
			case "cherry" -> {
				int t = Mth.clamp(Math.round(height * 0.55f), 4, 8);
				b.trunk(x, y, z, t, Blocks.CHERRY_LOG);
				b.blob(x, y + t, z, big ? 3.6 : 3.0, 2.0, big ? 3.6 : 3.0, Blocks.CHERRY_LEAVES);
			}
			case "azalea" -> {
				int t = Mth.clamp(height / 3, 2, 4);
				b.trunk(x, y, z, t, Blocks.OAK_LOG);
				b.blob(x, y + t, z, 2.2, 1.6, 2.2, b.rand(4) < 0.5f ? Blocks.FLOWERING_AZALEA_LEAVES : Blocks.AZALEA_LEAVES);
			}
			case "dead" -> dead(b, x, y, z, height, Blocks.DARK_OAK_LOG, seed);
			default -> { // oak
				int t = Mth.clamp(Math.round(height * 0.6f), 4, 9);
				b.trunk(x, y, z, t, Blocks.OAK_LOG);
				double r = big ? 3.4 : (height >= 9 ? 2.8 : 2.3);
				b.blob(x, y + t - 1, z, r, big ? 2.6 : 2.1, r, Blocks.OAK_LEAVES);
				if (big) {
					branches(b, x, y, z, t, Blocks.OAK_LOG, Blocks.OAK_LEAVES);
				}
			}
		}
		b.hollow();
		return tree;
	}

	/** A spruce: a straight trunk, leaves in tiers narrowing to the top, each tier a little wider than the next. */
	private static void spruce(Builder b, int x, int y, int z, int height, boolean big) {
		int t = height;
		b.trunk(x, y, z, t, Blocks.SPRUCE_LOG);
		int from = Math.max(1, t / 4);
		double widest = (big ? 3.4 : 2.4) + height / 12.0;
		for (int i = from; i <= t; i++) {
			double k = (t - i) / (double) Math.max(1, t - from);
			double r = widest * k;
			if ((t - i) % 2 == 1) {
				r = Math.max(0.6, r - 1);
			}
			b.disc(x, y + i, z, Math.max(0.6, r), Blocks.SPRUCE_LEAVES);
		}
		b.put(x, y + t, z, Blocks.SPRUCE_LEAVES);
		b.put(x, y + t + 1, z, Blocks.SPRUCE_LEAVES);
	}

	/** Bare: a trunk and a few crooked branches, out then up. */
	private static void dead(Builder b, int x, int y, int z, int height, Block log, int seed) {
		int t = Mth.clamp(Math.round(height * 0.8f), 3, 10);
		b.trunk(x, y, z, t, log);
		int count = 2 + (int) (b.rand(10) * 3);
		for (int i = 0; i < count; i++) {
			Direction d = Direction.from2DDataValue((int) (b.rand(11 + i) * 4));
			int at = y + Math.max(2, (int) (t * (0.4 + 0.5 * b.rand(20 + i))));
			int length = 1 + (int) (b.rand(30 + i) * 3);
			int bx = x;
			int bz = z;
			for (int k = 0; k < length; k++) {
				bx += d.getStepX();
				bz += d.getStepZ();
				b.put(bx, at, bz, log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, d.getAxis()));
			}
			if (b.rand(40 + i) < 0.6f) {
				b.put(bx, at + 1, bz, log.defaultBlockState());
			}
		}
	}

	/** An old tree's boughs: logs leaning out from the top of the trunk, a clump of leaves at each end. */
	private static void branches(Builder b, int x, int y, int z, int t, Block log, Block leaves) {
		for (int i = 0; i < 3; i++) {
			Direction d = Direction.from2DDataValue((i + (int) (b.rand(50) * 4)) % 4);
			int bx = x;
			int bz = z;
			int at = y + t - 3 + i;
			for (int k = 0; k < 3; k++) {
				bx += d.getStepX();
				bz += d.getStepZ();
				b.put(bx, at + k / 2, bz, log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, d.getAxis()));
			}
			b.blob(bx, at + 2, bz, 2.2, 1.5, 2.2, leaves);
		}
	}

	private static final class Builder {
		final Long2ObjectOpenHashMap<BlockState> tree;
		final int seed;

		Builder(Long2ObjectOpenHashMap<BlockState> tree, int seed) {
			this.tree = tree;
			this.seed = seed;
		}

		float rand(int salt) {
			return Noise.hash(seed * 7919 + salt * 104729);
		}

		void put(int x, int y, int z, Block block) {
			put(x, y, z, block.defaultBlockState());
		}

		void put(int x, int y, int z, BlockState state) {
			tree.put(BlockPos.asLong(x, y, z), state);
		}

		/** Leaves never replace wood. */
		void leaf(int x, int y, int z, Block leaves) {
			long key = BlockPos.asLong(x, y, z);
			BlockState there = tree.get(key);
			if (there == null) {
				tree.put(key, leaves.defaultBlockState());
			}
		}

		void trunk(int x, int y, int z, int height, Block log) {
			for (int i = 0; i < height; i++) {
				put(x, y + i, z, log);
			}
		}

		/** Leaves filling an ellipsoid, its edge ragged. */
		void blob(double cx, double cy, double cz, double rx, double ry, double rz, Block leaves) {
			for (int x = Mth.floor(cx - rx); x <= Mth.ceil(cx + rx); x++) {
				for (int y = Mth.floor(cy - ry); y <= Mth.ceil(cy + ry); y++) {
					for (int z = Mth.floor(cz - rz); z <= Mth.ceil(cz + rz); z++) {
						double dx = (x - cx) / rx;
						double dy = (y - cy) / ry;
						double dz = (z - cz) / rz;
						double d = dx * dx + dy * dy + dz * dz;
						if (d <= 1 - 0.35 * Noise.at(x, y, z, seed)) {
							leaf(x, y, z, leaves);
						}
					}
				}
			}
		}

		/** One layer of leaves, a ragged disc. */
		void disc(int cx, int y, int cz, double r, Block leaves) {
			int reach = Mth.ceil(r);
			for (int x = cx - reach; x <= cx + reach; x++) {
				for (int z = cz - reach; z <= cz + reach; z++) {
					double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
					if (d <= r - 0.3 * Noise.at(x, y, z, seed)) {
						leaf(x, y, z, leaves);
					}
				}
			}
		}

		/** Drops the leaves closed in on every side: never seen. */
		void hollow() {
			var buried = new it.unimi.dsi.fastutil.longs.LongArrayList();
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			for (var entry : tree.long2ObjectEntrySet()) {
				if (!(entry.getValue().getBlock() instanceof net.minecraft.world.level.block.LeavesBlock)) {
					continue;
				}
				boolean closed = true;
				for (Direction d : Direction.values()) {
					pos.set(entry.getLongKey()).move(d);
					if (!tree.containsKey(pos.asLong())) {
						closed = false;
						break;
					}
				}
				if (closed) {
					buried.add(entry.getLongKey());
				}
			}
			for (int i = 0; i < buried.size(); i++) {
				tree.remove(buried.getLong(i));
			}
		}
	}
}
