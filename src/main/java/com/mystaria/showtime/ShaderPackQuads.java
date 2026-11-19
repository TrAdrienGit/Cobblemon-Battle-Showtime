package com.mystaria.showtime;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Lets shapes written for a position-and-colour render type (quads, a colour per vertex) go into one of the entity
 * formats a shader pack understands (2026-11-19): each quad is held until its four vertices are in, then written with
 * a plain white texture point, no overlay, the given light and the quad's own normal, turned towards the camera (the
 * shapes are seen from both sides; a normal facing away would leave them unlit). Positions arrive relative to the
 * camera, so the camera is the origin.
 */
public final class ShaderPackQuads implements VertexConsumer {

	private final VertexConsumer out;
	private final int light;
	private final float[] xyz = new float[12];
	private final int[] rgba = new int[16];
	private int count;

	public ShaderPackQuads(VertexConsumer out, int light) {
		this.out = out;
		this.light = light;
	}

	@Override
	public VertexConsumer addVertex(float x, float y, float z) {
		if (count == 4) {
			count = 0; // a quad whose last colour never came: dropped rather than written half-done
		}
		xyz[count * 3] = x;
		xyz[count * 3 + 1] = y;
		xyz[count * 3 + 2] = z;
		rgba[count * 4] = 255;
		rgba[count * 4 + 1] = 255;
		rgba[count * 4 + 2] = 255;
		rgba[count * 4 + 3] = 255;
		count++;
		return this;
	}

	@Override
	public VertexConsumer setColor(int r, int g, int b, int a) {
		int i = (count - 1) * 4;
		if (i < 0) {
			return this;
		}
		rgba[i] = r;
		rgba[i + 1] = g;
		rgba[i + 2] = b;
		rgba[i + 3] = a;
		if (count == 4) {
			flush();
		}
		return this;
	}

	private void flush() {
		// The normal from the diagonals (right for any quad, even a bent one), towards the camera.
		float ax = xyz[6] - xyz[0];
		float ay = xyz[7] - xyz[1];
		float az = xyz[8] - xyz[2];
		float bx = xyz[9] - xyz[3];
		float by = xyz[10] - xyz[4];
		float bz = xyz[11] - xyz[5];
		float nx = ay * bz - az * by;
		float ny = az * bx - ax * bz;
		float nz = ax * by - ay * bx;
		float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (len < 1.0e-6f) {
			nx = 0;
			ny = 1;
			nz = 0;
		} else {
			nx /= len;
			ny /= len;
			nz /= len;
		}
		float cx = (xyz[0] + xyz[3] + xyz[6] + xyz[9]) * 0.25f;
		float cy = (xyz[1] + xyz[4] + xyz[7] + xyz[10]) * 0.25f;
		float cz = (xyz[2] + xyz[5] + xyz[8] + xyz[11]) * 0.25f;
		if (nx * cx + ny * cy + nz * cz > 0) {
			nx = -nx;
			ny = -ny;
			nz = -nz;
		}
		for (int v = 0; v < 4; v++) {
			out.addVertex(xyz[v * 3], xyz[v * 3 + 1], xyz[v * 3 + 2])
					.setColor(rgba[v * 4], rgba[v * 4 + 1], rgba[v * 4 + 2], rgba[v * 4 + 3])
					.setUv(0.5f, 0.5f)
					.setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(light)
					.setNormal(nx, ny, nz);
		}
		count = 0;
	}

	@Override
	public VertexConsumer setUv(float u, float v) {
		return this;
	}

	@Override
	public VertexConsumer setUv1(int u, int v) {
		return this;
	}

	@Override
	public VertexConsumer setUv2(int u, int v) {
		return this;
	}

	@Override
	public VertexConsumer setNormal(float x, float y, float z) {
		return this;
	}
}
