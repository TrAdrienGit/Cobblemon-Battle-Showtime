package com.mystaria.showtime.ambiance.blocks;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.vertex.PoseStack;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

/**
 * Turns a stadium's blocks into a {@link SceneMesh} (2026-11-19), in the background: every block through Minecraft's
 * own block renderer (its model, textures, ambient occlusion, hidden faces dropped), water and lava through its liquid
 * renderer, the vertices kept.
 */
final class SceneBuilder {

	private static final Logger LOG = LoggerFactory.getLogger("cobblemon_battle_showtime");

	/**
	 * One part of a stadium: its blocks, drawn {@code scale} blocks a cell (far parts are coarser). Ground parts are cut
	 * into square pieces; an objects part (trees, rocks) gives a piece per object, its block keys in {@code objects}.
	 */
	record Part(SceneLevel level, int scale, List<LongArrayList> objects) {
		Part(SceneLevel level, int scale) {
			this(level, scale, new ArrayList<>());
		}
	}

	/** Cells a ground piece spans: 16 blocks near, 32 and 64 for the coarse parts (fewer, bigger pieces far off). */
	private static final int PIECE = 16;

	private SceneBuilder() {
	}

	static SceneMesh compile(List<Part> parts, int originX, int originZ) {
		BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
		RandomSource random = RandomSource.create();
		PoseStack pose = new PoseStack();
		List<SceneMesh.Piece> pieces = new ArrayList<>();
		int[] failures = {0};
		for (Part part : parts) {
			SceneLevel level = part.level();
			if (part.objects().isEmpty()) {
				Long2ObjectOpenHashMap<LongArrayList> groups = new Long2ObjectOpenHashMap<>();
				for (long key : level.blocks.keySet()) {
					long group = SceneLevel.column(Math.floorDiv(BlockPos.getX(key), PIECE), Math.floorDiv(BlockPos.getZ(key), PIECE));
					groups.computeIfAbsent(group, g -> new LongArrayList()).add(key);
				}
				for (LongArrayList keys : groups.values()) {
					add(pieces, piece(keys, part, false, originX, originZ, dispatcher, random, pose, failures));
				}
			} else {
				LongOpenHashSet owned = new LongOpenHashSet();
				for (LongArrayList keys : part.objects()) {
					owned.addAll(keys);
					add(pieces, piece(keys, part, true, originX, originZ, dispatcher, random, pose, failures));
				}
				// Blocks of the part no object claims (none today): one piece, never hidden.
				LongArrayList rest = new LongArrayList();
				for (long key : level.blocks.keySet()) {
					if (!owned.contains(key)) {
						rest.add(key);
					}
				}
				if (!rest.isEmpty()) {
					add(pieces, piece(rest, part, false, originX, originZ, dispatcher, random, pose, failures));
				}
			}
		}
		if (failures[0] > 0) {
			LOG.warn("Stadium blocks: {} blocks could not be drawn and were left out", failures[0]);
		}
		return new SceneMesh(originX, originZ, pieces);
	}

	private static void add(List<SceneMesh.Piece> pieces, SceneMesh.Piece piece) {
		if (piece != null) {
			pieces.add(piece);
		}
	}

	private static SceneMesh.Piece piece(LongArrayList keys, Part part, boolean hideNear, int originX, int originZ,
			BlockRenderDispatcher dispatcher, RandomSource random, PoseStack pose, int[] failures) {
		SceneLevel level = part.level();
		int s = part.scale();
		MeshCapture[] layers = new MeshCapture[SceneMesh.LAYERS.length];
		for (int i = 0; i < layers.length; i++) {
			layers[i] = new MeshCapture();
		}
		for (int i = 0; i < keys.size(); i++) {
			long key = keys.getLong(i);
			BlockState state = level.blocks.get(key);
			if (state == null || state.isAir()) {
				continue;
			}
			BlockPos pos = BlockPos.of(key);
			try {
				if (state.getRenderShape() == RenderShape.MODEL) {
					MeshCapture out = layers[SceneMesh.layer(ItemBlockRenderTypes.getMovingBlockRenderType(state))];
					pose.pushPose();
					pose.translate((double) pos.getX() * s - originX, (double) pos.getY() * s, (double) pos.getZ() * s - originZ);
					if (s != 1) {
						pose.scale(s, s, s);
					}
					random.setSeed(state.getSeed(pos));
					dispatcher.renderBatched(state, pos, level, pose, out, true, random);
					pose.popPose();
				}
				FluidState fluid = state.getFluidState();
				if (!fluid.isEmpty()) {
					// The liquid renderer writes positions within the block's 16-block section.
					MeshCapture out = layers[SceneMesh.layer(ItemBlockRenderTypes.getRenderLayer(fluid))];
					out.transform((pos.getX() & ~15) * s - originX, (pos.getY() & ~15) * s, (pos.getZ() & ~15) * s - originZ, s);
					dispatcher.renderLiquid(pos, level, out, state, fluid);
					out.noTransform();
				}
			} catch (Throwable ex) {
				// A modded block whose model wants a real world: left out, never the whole stadium.
				failures[0]++;
				if (failures[0] == 1) {
					LOG.warn("Stadium block {} could not be drawn", state, ex);
				}
				while (!pose.clear()) {
					pose.popPose();
				}
				for (MeshCapture layer : layers) {
					layer.noTransform();
				}
			}
		}
		int[][] data = new int[layers.length][];
		float minX = Float.MAX_VALUE;
		float minY = Float.MAX_VALUE;
		float minZ = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;
		float maxZ = -Float.MAX_VALUE;
		boolean any = false;
		for (int l = 0; l < layers.length; l++) {
			if (layers[l].vertices() == 0) {
				continue;
			}
			int[] d = layers[l].toArray();
			data[l] = d;
			any = true;
			for (int k = 0; k < d.length; k += MeshCapture.STRIDE) {
				float x = Float.intBitsToFloat(d[k]);
				float y = Float.intBitsToFloat(d[k + 1]);
				float z = Float.intBitsToFloat(d[k + 2]);
				minX = Math.min(minX, x);
				minY = Math.min(minY, y);
				minZ = Math.min(minZ, z);
				maxX = Math.max(maxX, x);
				maxY = Math.max(maxY, y);
				maxZ = Math.max(maxZ, z);
			}
		}
		if (!any) {
			return null;
		}
		return new SceneMesh.Piece(new AABB(minX, minY, minZ, maxX, maxY, maxZ), hideNear, data);
	}
}
