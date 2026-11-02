package com.mystaria.spectacle.move;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Shapes for the move effects: glowing ones (additive, {@link com.mystaria.spectacle.SpectacleRenderTypes#GLOW}) and dark solid ones. */
final class Draw {

	/** Tinted by the vertex colour: a dark translucent body (what additive light can't draw). */
	static final RenderType DARK = RenderType.entityTranslucent(
			ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png"));
	static final RenderType GLOW = com.mystaria.spectacle.SpectacleRenderTypes.GLOW;

	private Draw() {
	}

	/** A sphere: {@code glow} for the additive buffer, else the dark one ({@link #DARK}). */
	static void sphere(VertexConsumer out, Matrix4f m, Vec3 at, float radius, int color, int alpha, boolean glow) {
		int rings = 10;
		int segments = 18;
		for (int r = 0; r < rings; r++) {
			double t0 = Math.PI * r / rings;
			double t1 = Math.PI * (r + 1) / rings;
			for (int s = 0; s < segments; s++) {
				double p0 = Math.PI * 2 * s / segments;
				double p1 = Math.PI * 2 * (s + 1) / segments;
				for (Vec3 n : new Vec3[] { unit(t0, p0), unit(t0, p1), unit(t1, p1), unit(t1, p0) }) {
					Vec3 v = at.add(n.scale(radius));
					if (glow) {
						vertex(out, m, v, color, alpha);
					} else {
						solid(out, m, v, color, alpha);
					}
				}
			}
		}
	}

	/** A glowing halo facing the camera: bright at {@code inner}, nothing at {@code outer}. */
	static void halo(VertexConsumer out, Matrix4f m, Vec3 at, Vec3 camera, float inner, float outer, int color, int alpha) {
		Vec3 normal = camera.subtract(at).normalize();
		Vec3 u = normal.cross(new Vec3(0, 1, 0));
		u = u.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : u.normalize();
		Vec3 v = normal.cross(u).normalize();
		int segments = 24;
		for (int s = 0; s < segments; s++) {
			double a0 = Math.PI * 2 * s / segments;
			double a1 = Math.PI * 2 * (s + 1) / segments;
			Vec3 d0 = u.scale(Math.cos(a0)).add(v.scale(Math.sin(a0)));
			Vec3 d1 = u.scale(Math.cos(a1)).add(v.scale(Math.sin(a1)));
			vertex(out, m, at.add(d0.scale(inner)), color, alpha);
			vertex(out, m, at.add(d0.scale(outer)), color, 0);
			vertex(out, m, at.add(d1.scale(outer)), color, 0);
			vertex(out, m, at.add(d1.scale(inner)), color, alpha);
		}
	}

	/** A flat ring around {@code at} in the plane normal to {@code normal}: a shockwave. */
	static void ring(VertexConsumer out, Matrix4f m, Vec3 at, Vec3 normal, float radius, float width, int color, int alpha) {
		Vec3 u = normal.cross(new Vec3(0, 1, 0));
		u = u.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : u.normalize();
		Vec3 v = normal.cross(u).normalize();
		int segments = 32;
		for (int s = 0; s < segments; s++) {
			double a0 = Math.PI * 2 * s / segments;
			double a1 = Math.PI * 2 * (s + 1) / segments;
			Vec3 d0 = u.scale(Math.cos(a0)).add(v.scale(Math.sin(a0)));
			Vec3 d1 = u.scale(Math.cos(a1)).add(v.scale(Math.sin(a1)));
			// Bright in the middle of the band, soft on both edges.
			vertex(out, m, at.add(d0.scale(radius - width)), color, 0);
			vertex(out, m, at.add(d0.scale(radius)), color, alpha);
			vertex(out, m, at.add(d1.scale(radius)), color, alpha);
			vertex(out, m, at.add(d1.scale(radius - width)), color, 0);
			vertex(out, m, at.add(d0.scale(radius)), color, alpha);
			vertex(out, m, at.add(d0.scale(radius + width)), color, 0);
			vertex(out, m, at.add(d1.scale(radius + width)), color, 0);
			vertex(out, m, at.add(d1.scale(radius)), color, alpha);
		}
	}

	/** A thin glowing streak from {@code from} (bright) to {@code to} (faded), facing the camera. */
	static void streak(VertexConsumer out, Matrix4f m, Vec3 from, Vec3 to, Vec3 camera, float width, int color, int alpha) {
		Vec3 axis = to.subtract(from);
		if (axis.lengthSqr() < 1.0E-6) {
			return;
		}
		Vec3 side = axis.cross(camera.subtract(from)).normalize().scale(width);
		vertex(out, m, from.add(side), color, alpha);
		vertex(out, m, from.subtract(side), color, alpha);
		vertex(out, m, to.subtract(side.scale(0.2)), color, 0);
		vertex(out, m, to.add(side.scale(0.2)), color, 0);
	}

	/**
	 * A swept crescent: a ribbon around {@code center} in the plane ({@code u}, {@code v}), from angle {@code from} to
	 * {@code to}, drawn up to {@code progress} (0..1) — bright at its leading edge, fading towards where it started,
	 * thin at both ends. The shape of a slash, a tail or a kick.
	 */
	static void arc(VertexConsumer out, Matrix4f m, Vec3 center, Vec3 u, Vec3 v, float radius, float from, float to,
			float progress, float width, int color, int alpha) {
		int segments = 20;
		float end = from + (to - from) * Math.max(0f, Math.min(1f, progress));
		for (int s = 0; s < segments; s++) {
			float p0 = s / (float) segments;
			float p1 = (s + 1) / (float) segments;
			float a0 = from + (end - from) * p0;
			float a1 = from + (end - from) * p1;
			float w0 = width * (float) Math.sin(Math.PI * Math.min(1, p0 * 1.15));
			float w1 = width * (float) Math.sin(Math.PI * Math.min(1, p1 * 1.15));
			int al0 = (int) (alpha * p0);
			int al1 = (int) (alpha * p1);
			Vec3 d0 = u.scale(Math.cos(a0)).add(v.scale(Math.sin(a0)));
			Vec3 d1 = u.scale(Math.cos(a1)).add(v.scale(Math.sin(a1)));
			vertex(out, m, center.add(d0.scale(radius - w0)), color, 0);
			vertex(out, m, center.add(d0.scale(radius)), color, al0);
			vertex(out, m, center.add(d1.scale(radius)), color, al1);
			vertex(out, m, center.add(d1.scale(radius - w1)), color, 0);
			vertex(out, m, center.add(d0.scale(radius)), color, al0);
			vertex(out, m, center.add(d0.scale(radius + w0 * 0.4f)), color, 0);
			vertex(out, m, center.add(d1.scale(radius + w1 * 0.4f)), color, 0);
			vertex(out, m, center.add(d1.scale(radius)), color, al1);
		}
	}

	/** A flat glowing quad (a wall of light: Reflect, Light Screen), {@code u} and {@code v} its half sides. */
	static void panel(VertexConsumer out, Matrix4f m, Vec3 at, Vec3 u, Vec3 v, int color, int alpha) {
		vertex(out, m, at.subtract(u).subtract(v), color, alpha);
		vertex(out, m, at.add(u).subtract(v), color, alpha);
		vertex(out, m, at.add(u).add(v), color, alpha);
		vertex(out, m, at.subtract(u).add(v), color, alpha);
	}

	/** Two axes across {@code normal}: {@code [u, v]}. */
	static Vec3[] basis(Vec3 normal) {
		Vec3 u = normal.cross(new Vec3(0, 1, 0));
		u = u.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : u.normalize();
		return new Vec3[] { u, normal.cross(u).normalize() };
	}

	static Vec3 unit(double theta, double phi) {
		return new Vec3(Math.sin(theta) * Math.cos(phi), Math.cos(theta), Math.sin(theta) * Math.sin(phi));
	}

	static void vertex(VertexConsumer out, Matrix4f m, Vec3 at, int color, int alpha) {
		out.addVertex(m, (float) at.x, (float) at.y, (float) at.z)
				.setColor((color >> 16) & 255, (color >> 8) & 255, color & 255, alpha);
	}

	private static void solid(VertexConsumer out, Matrix4f m, Vec3 at, int color, int alpha) {
		out.addVertex(m, (float) at.x, (float) at.y, (float) at.z).setColor((color >> 16) & 255, (color >> 8) & 255, color & 255, alpha)
				.setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, 1, 0);
	}
}
