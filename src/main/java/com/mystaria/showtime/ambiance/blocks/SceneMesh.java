package com.mystaria.showtime.ambiance.blocks;

import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A stadium's blocks, tessellated (2026-11-19): pieces of the place (16-block columns of ground, a tree, a rock), each
 * with its box, so what the camera does not see is skipped, and per layer the vertices Minecraft's block renderer
 * wrote. Replayed each frame in Minecraft's own block render types, the ones falling blocks use: shader packs (Iris)
 * draw them as blocks, lit and shaded like the world.
 */
final class SceneMesh {

	/** Layers in drawing order: opaque first, the translucent (water, glass) last. */
	static final RenderType[] LAYERS = {RenderType.solid(), RenderType.cutoutMipped(), RenderType.cutout(),
			RenderType.translucentMovingBlock()};

	static int layer(RenderType type) {
		for (int i = 0; i < LAYERS.length; i++) {
			if (LAYERS[i] == type) {
				return i;
			}
		}
		return type == RenderType.translucent() ? 3 : 1;
	}

	record Piece(AABB box, boolean hideNear, int[][] layers) {
	}

	/** World block the vertices are relative to (x and z; y is absolute). */
	final int originX;
	final int originZ;
	final List<Piece> pieces;
	final int quads;

	SceneMesh(int originX, int originZ, List<Piece> pieces) {
		this.originX = originX;
		this.originZ = originZ;
		this.pieces = pieces;
		int q = 0;
		for (Piece piece : pieces) {
			for (int[] data : piece.layers) {
				if (data != null) {
					q += data.length / MeshCapture.STRIDE / 4;
				}
			}
		}
		this.quads = q;
	}

	/**
	 * Draws the visible pieces. {@code toOrigin}: the pose at the mesh's origin, relative to the camera. A piece the
	 * camera stands in (a tree it flies through) is left out, so it never fills the screen with leaves.
	 */
	void render(Matrix4f toOrigin, MultiBufferSource buffers, Vec3 camera, Frustum frustum) {
		boolean[] visible = new boolean[pieces.size()];
		for (int i = 0; i < visible.length; i++) {
			Piece piece = pieces.get(i);
			AABB box = piece.box.move(originX, 0, originZ);
			if (piece.hideNear && box.inflate(1.2).contains(camera)) {
				continue;
			}
			visible[i] = frustum == null || frustum.isVisible(box);
		}
		for (int l = 0; l < LAYERS.length; l++) {
			VertexConsumer out = null;
			for (int i = 0; i < visible.length; i++) {
				int[] d = visible[i] ? pieces.get(i).layers[l] : null;
				if (d == null) {
					continue;
				}
				if (out == null) {
					// One layer's buffer at a time (hard rule 9): asked for once the previous layer is done with.
					out = buffers.getBuffer(LAYERS[l]);
				}
				for (int k = 0; k < d.length; k += MeshCapture.STRIDE) {
					int normal = d[k + 7];
					out.addVertex(toOrigin, Float.intBitsToFloat(d[k]), Float.intBitsToFloat(d[k + 1]), Float.intBitsToFloat(d[k + 2]))
							.setColor(d[k + 3])
							.setUv(Float.intBitsToFloat(d[k + 4]), Float.intBitsToFloat(d[k + 5]))
							.setLight(d[k + 6])
							.setNormal((byte) normal / 127f, (byte) (normal >> 8) / 127f, (byte) (normal >> 16) / 127f);
				}
			}
		}
	}
}
