package com.mystaria.showtime.battle;

import org.joml.Matrix4f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;

import com.mystaria.showtime.battle.BattleShowtime.Kind;

/**
 * The 2D layer of {@link BattleShowtime}, over everything ({@code GameRendererShowtimeMixin}): since 2026-11-25 only the
 * film's frame, black letterbox bars, and the flashes that cut between its moments. The set piece itself stands in
 * the world ({@link SetPieceScene}). Client thread only.
 */
public final class ShowtimeOverlay {

	/** Flashes per kind: time (s), colour, strength (0..1), how long it fades (s). */
	private static final float[][] MEGA = { { 1.3f, 0xFFFFFF, 0.45f, 0.3f }, { 2.4f, 0xFFFFFF, 1f, 0.4f }, { 3.1f, 0xFFF0FF, 0.95f, 0.35f },
			{ BattleShowtime.MEGA_SHATTER, 0xFFFFFF, 1f, 0.8f } };
	private static final float[][] Z_MOVE = { { 1.5f, 0xFFE9A0, 0.45f, 0.3f }, { 2.4f, 0xFFF4D0, 0.9f, 0.4f },
			{ BattleShowtime.Z_RELEASE, 0xFFFFFF, 1f, 0.8f } };
	private static final float[][] TERA = { { 2.5f, 0xFFFFFF, 0.8f, 0.35f }, { BattleShowtime.TERA_SHATTER, 0xFFFFFF, 1f, 0.8f },
			{ Kind.TERA.seconds - 0.55f, 0xFFFFFF, 0.6f, 0.5f } };

	private ShowtimeOverlay() {
	}

	public static void render(GuiGraphics g) {
		if (!BattleShowtime.playing()) {
			return;
		}
		float t = BattleShowtime.time();
		Kind kind = BattleShowtime.kind();
		int w = g.guiWidth();
		int h = g.guiHeight();

		// The letterbox, sliding in and out.
		float bars = Math.min(BattleShowtime.smooth(t / 0.45f), BattleShowtime.smooth((kind.seconds - t) / 0.5f));
		if (bars > 0f) {
			float bar = h * 0.105f * bars;
			Tris solid = Tris.begin(g, false);
			solid.quad(0, 0, 0xFF000000, w, 0, 0xFF000000, w, bar, 0xFF000000, 0, bar, 0xFF000000);
			solid.quad(0, h - bar, 0xFF000000, w, h - bar, 0xFF000000, w, h, 0xFF000000, 0, h, 0xFF000000);
			solid.end();
		}

		// The flashes: up in a twentieth of a second, then fading.
		float[][] flashes = switch (kind) {
			case MEGA, PRIMAL -> MEGA;
			case Z_MOVE -> Z_MOVE;
			case TERA -> TERA;
		};
		float white = 0f;
		int color = 0xFFFFFF;
		for (float[] f : flashes) {
			float since = t - f[0];
			if (since < -0.05f || since > f[3]) {
				continue;
			}
			float a = since < 0f ? (since + 0.05f) / 0.05f : (float) Math.pow(1f - since / f[3], 2);
			if (a * f[2] > white) {
				white = a * f[2];
				color = (int) f[1];
			}
		}
		if (white > 0.01f) {
			int argb = ((int) (Mth.clamp(white, 0f, 1f) * 255) << 24) | color;
			Tris light = Tris.begin(g, false);
			light.quad(0, 0, argb, w, 0, argb, w, h, argb, 0, h, argb);
			light.end();
		}
	}

	/** A batch of coloured triangles on the GUI, normal or additive blending, no depth test. */
	static final class Tris {
		private final BufferBuilder buffer;
		private final Matrix4f matrix;

		private Tris(BufferBuilder buffer, Matrix4f matrix) {
			this.buffer = buffer;
			this.matrix = matrix;
		}

		static Tris begin(GuiGraphics g, boolean additive) {
			g.flush();
			RenderSystem.enableBlend();
			if (additive) {
				RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
			} else {
				RenderSystem.defaultBlendFunc();
			}
			RenderSystem.disableDepthTest();
			RenderSystem.disableCull();
			RenderSystem.setShader(GameRenderer::getPositionColorShader);
			return new Tris(Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR),
					new Matrix4f(g.pose().last().pose()));
		}

		void end() {
			MeshData mesh = buffer.build();
			if (mesh != null) {
				BufferUploader.drawWithShader(mesh);
			}
			RenderSystem.defaultBlendFunc();
			RenderSystem.enableCull();
			RenderSystem.enableDepthTest();
		}

		void tri(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3) {
			buffer.addVertex(matrix, x1, y1, 0).setColor(c1);
			buffer.addVertex(matrix, x2, y2, 0).setColor(c2);
			buffer.addVertex(matrix, x3, y3, 0).setColor(c3);
		}

		void quad(float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3, float x4, float y4, int c4) {
			tri(x1, y1, c1, x2, y2, c2, x3, y3, c3);
			tri(x1, y1, c1, x3, y3, c3, x4, y4, c4);
		}

		/** A disc, {@code inner} colour at the centre fading to {@code outer} at the rim. */
		void disc(float cx, float cy, float r, int inner, int outer, int segments) {
			for (int i = 0; i < segments; i++) {
				float a0 = i * Mth.TWO_PI / segments;
				float a1 = (i + 1) * Mth.TWO_PI / segments;
				tri(cx, cy, inner, cx + Mth.cos(a0) * r, cy + Mth.sin(a0) * r, outer, cx + Mth.cos(a1) * r, cy + Mth.sin(a1) * r, outer);
			}
		}

		/** A ray: a thin wedge {@code spread} radians wide, from {@code r1} to {@code r2}. */
		void wedge(float cx, float cy, float angle, float spread, float r1, float r2, int inner, int outer) {
			float a0 = angle - spread / 2;
			float a1 = angle + spread / 2;
			quad(cx + Mth.cos(a0) * r1, cy + Mth.sin(a0) * r1, inner, cx + Mth.cos(a0) * r2, cy + Mth.sin(a0) * r2, outer,
					cx + Mth.cos(a1) * r2, cy + Mth.sin(a1) * r2, outer, cx + Mth.cos(a1) * r1, cy + Mth.sin(a1) * r1, inner);
		}

		void line(float x1, float y1, float x2, float y2, float width, int color) {
			float dx = x2 - x1;
			float dy = y2 - y1;
			float length = Mth.sqrt(dx * dx + dy * dy);
			if (length < 1.0E-3f) {
				return;
			}
			float nx = -dy / length * width / 2;
			float ny = dx / length * width / 2;
			quad(x1 + nx, y1 + ny, color, x2 + nx, y2 + ny, color, x2 - nx, y2 - ny, color, x1 - nx, y1 - ny, color);
		}

		/** A crystal shard: a thin triangle turned by {@code rotation}. */
		void shard(float x, float y, float size, float rotation, int color) {
			int tip = (color & 0x00FFFFFF) | ((((color >>> 24) & 0xFF) / 3) << 24);
			tri(x + Mth.cos(rotation) * size, y + Mth.sin(rotation) * size, color,
					x + Mth.cos(rotation + 2.4f) * size * 0.45f, y + Mth.sin(rotation + 2.4f) * size * 0.45f, tip,
					x + Mth.cos(rotation - 2.4f) * size * 0.45f, y + Mth.sin(rotation - 2.4f) * size * 0.45f, tip);
		}
	}
}
