package com.mystaria.showtime.ambiance.blocks;

import java.util.Arrays;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * A vertex consumer that keeps what Minecraft's block renderer writes (2026-11-19), so a stadium's blocks are
 * tessellated once, in the background, and only replayed each frame. Eight ints a vertex: x, y, z (float bits), ARGB
 * colour, u, v (float bits), packed light, packed normal.
 * <p>
 * {@link #transform} moves what is written afterwards: the liquid renderer writes positions within their 16-block
 * section, not through a pose.
 */
final class MeshCapture implements VertexConsumer {

	static final int STRIDE = 8;

	private int[] data = new int[STRIDE * 256];
	private int size;
	private int at = -1;
	private float tx;
	private float ty;
	private float tz;
	private float scale = 1f;

	void transform(float x, float y, float z, float scale) {
		this.tx = x;
		this.ty = y;
		this.tz = z;
		this.scale = scale;
	}

	void noTransform() {
		transform(0, 0, 0, 1);
	}

	int vertices() {
		return size / STRIDE;
	}

	int[] toArray() {
		return Arrays.copyOf(data, size);
	}

	@Override
	public VertexConsumer addVertex(float x, float y, float z) {
		if (size + STRIDE > data.length) {
			data = Arrays.copyOf(data, data.length * 2);
		}
		at = size;
		size += STRIDE;
		data[at] = Float.floatToRawIntBits(x * scale + tx);
		data[at + 1] = Float.floatToRawIntBits(y * scale + ty);
		data[at + 2] = Float.floatToRawIntBits(z * scale + tz);
		data[at + 3] = 0xFFFFFFFF;
		data[at + 4] = 0;
		data[at + 5] = 0;
		data[at + 6] = 0x00F000F0;
		data[at + 7] = packNormal(0, 1, 0);
		return this;
	}

	@Override
	public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny,
			float nz) {
		addVertex(x, y, z);
		data[at + 3] = color;
		data[at + 4] = Float.floatToRawIntBits(u);
		data[at + 5] = Float.floatToRawIntBits(v);
		data[at + 6] = light;
		data[at + 7] = packNormal(nx, ny, nz);
	}

	@Override
	public VertexConsumer setColor(int r, int g, int b, int a) {
		data[at + 3] = (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255);
		return this;
	}

	@Override
	public VertexConsumer setUv(float u, float v) {
		data[at + 4] = Float.floatToRawIntBits(u);
		data[at + 5] = Float.floatToRawIntBits(v);
		return this;
	}

	@Override
	public VertexConsumer setUv1(int u, int v) {
		return this; // overlay: blocks have none
	}

	@Override
	public VertexConsumer setUv2(int u, int v) {
		data[at + 6] = (u & 0xFFFF) | (v & 0xFFFF) << 16;
		return this;
	}

	@Override
	public VertexConsumer setNormal(float x, float y, float z) {
		data[at + 7] = packNormal(x, y, z);
		return this;
	}

	static int packNormal(float x, float y, float z) {
		return (Math.round(x * 127) & 255) | (Math.round(y * 127) & 255) << 8 | (Math.round(z * 127) & 255) << 16;
	}
}
