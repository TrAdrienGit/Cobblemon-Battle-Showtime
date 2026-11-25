package com.mystaria.showtime.battle;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import com.mystaria.showtime.ShowtimeRenderTypes;
import com.mystaria.showtime.battle.BattleShowtime.Kind;

/**
 * The set pieces drawn in the world (2026-11-25), in place of the flat 2D layer they had before: everything stands round
 * the trainer and the Pokémon and the camera moves through it, like the games' own cut scenes. Built from coloured
 * shapes only (rule: no game assets): a veil dome that replaces the arena behind the actors, glows, flares, ribbons,
 * a faceted shell, crystal prisms, shards and emblems.
 *
 * <ul>
 *   <li><b>Mega Evolution</b> (after Omega Ruby / Alpha Sapphire): the Key Stone lights up on the trainer's wrist, two
 *   rainbow strands link it to the Pokémon, a pink burst with both drawn black against it, then the Pokémon inside a
 *   faceted shell wrapped in ribbons, cracking with gold, shattering into its facets; light rays and the emblem over
 *   the new form. Primal Reversion: the same in Groudon's red or Kyogre's blue, with its Ω / α.</li>
 *   <li><b>Z-Move</b>: the Z-Crystal spinning before the trainer in golden spirals, a stream of power to the Pokémon,
 *   a golden blaze round it under a giant Z, then a pillar of light and a shockwave.</li>
 *   <li><b>Terastallization</b> (after Scarlet / Violet): the Tera Orb charged above the trainer's head by streams of
 *   sparkles under a night sky, thrown over the Pokémon, bursting into a crystal star; crystal prisms grow from the
 *   ground and encase it, shatter, and the Pokémon stands crystalline under its Tera jewel in a storm of sparkles.</li>
 * </ul>
 * Timings and positions come from {@link BattleShowtime}, which moves the camera along the same timeline. Client thread.
 */
public final class SetPieceScene {

	private static final Vec3 UP = new Vec3(0, 1, 0);
	/** Unit icospheres: the Mega shell (320 facets) and the Tera Orb (80). */
	private static final List<Vec3[]> SHELL = icosphere(2);
	private static final List<Vec3[]> ORB = icosphere(1);

	private static boolean irisChecked;
	private static Object iris;
	private static java.lang.reflect.Method irisInUse;

	private SetPieceScene() {
	}

	public static void render(WorldRenderContext context) {
		if (!BattleShowtime.playing() || context.matrixStack() == null) {
			return;
		}
		Kind kind = BattleShowtime.kind();
		float t = BattleShowtime.time();
		float pt = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.camera().getPosition();
		PoseStack pose = context.matrixStack();
		pose.pushPose();
		pose.translate(-camera.x, -camera.y, -camera.z);
		Matrix4f m = pose.last().pose();
		MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
		Scene s = new Scene(m, camera, t, pt);
		// One buffer at a time: every translucent shape first, then every glow.
		VertexConsumer tint = buffer(buffers, ShowtimeRenderTypes.TINT);
		switch (kind) {
			case MEGA, PRIMAL -> megaTint(s, tint, kind == Kind.PRIMAL);
			case Z_MOVE -> zTint(s, tint);
			case TERA -> teraTint(s, tint);
		}
		VertexConsumer glow = buffer(buffers, ShowtimeRenderTypes.GLOW);
		switch (kind) {
			case MEGA, PRIMAL -> megaGlow(s, glow, kind == Kind.PRIMAL);
			case Z_MOVE -> zGlow(s, glow);
			case TERA -> teraGlow(s, glow);
		}
		buffers.endBatch();
		pose.popPose();
	}

	/** What a frame needs: the matrix, the camera, the time, and the actors' places this frame. */
	private record Scene(Matrix4f m, Vec3 camera, float t, float pt, Vec3 center, double r, Vec3 feet, Vec3 trainer,
			Vec3 forward, Vec3 right, double height) {

		Scene(Matrix4f m, Vec3 camera, float t, float pt) {
			this(m, camera, t, pt, BattleShowtime.center(pt), BattleShowtime.radius(), BattleShowtime.feet(pt),
					BattleShowtime.trainerFeet(), BattleShowtime.forward(), BattleShowtime.right(), BattleShowtime.trainerHeight());
		}

		/** Between the trainer and the Pokémon, at chest height. */
		Vec3 middle() {
			return trainer.add(feet).scale(0.5).add(0, Math.max(1.0, r), 0);
		}
	}

	// =====================================================================
	// Mega Evolution / Primal Reversion
	// =====================================================================

	private static int[] megaPalette(boolean primal) {
		boolean kyogre = "kyogre".equals(BattleShowtime.species());
		// core, ribbon A, ribbon B, veil deep, veil bright
		return primal ? (kyogre ? new int[] { 0x9FE8FF, 0x2A70FF, 0x7FE8FF, 0x020A2A, 0x1E4FC0 }
				: new int[] { 0xFFD090, 0xFF3A18, 0xFFB040, 0x2A0602, 0xC03010 })
				: new int[] { 0xF4D8FF, 0xFF4FD8, 0xB45CFF, 0x2A0430, 0xE040B0 };
	}

	private static void megaTint(Scene s, VertexConsumer out, boolean primal) {
		int[] pal = megaPalette(primal);
		float t = s.t;
		// The burst behind both silhouettes.
		float burst = window(t, 2.4f, 3.1f, 0.05f, 0.08f);
		if (burst > 0f) {
			Vec3 mid = s.middle();
			double radius = Math.max(mid.distanceTo(s.camera) + 2.5, s.trainer.distanceTo(s.feet) * 0.5 + 5);
			Vec3 away = mid.subtract(s.camera).normalize();
			dome(out, s, mid, radius, away, primal ? pal[4] : 0xFF9AE0, primal ? pal[3] : 0xC0208A, burst);
			floor(out, s, mid, s.feet.y + 0.03, radius, primal ? pal[3] : 0xB01880, burst);
		}
		// The veil round the cocoon, fading as the new form shows.
		float veil = window(t, 3.1f, BattleShowtime.MEGA_SHATTER + 0.9f, 0.1f, 0.8f);
		if (veil > 0f) {
			double radius = s.center.distanceTo(s.camera) + 3 + s.r * 2;
			Vec3 away = s.center.subtract(s.camera).normalize();
			dome(out, s, s.center, radius, away, pal[4], pal[3], veil * 0.96f);
			floor(out, s, s.center, s.feet.y + 0.03, radius, pal[3], veil * 0.96f);
		}
		// The shell's body: a pale membrane at first, filling in until it hides the Pokémon (2026-11-25: the reveal is
		// the shatter); its facets lit from above the camera, brighter at the rim.
		float shell = window(t, 3.2f, BattleShowtime.MEGA_SHATTER, 0.35f, 0f);
		if (shell > 0f) {
			double rs = s.r * 1.3 * (0.85 + 0.15 * BattleShowtime.easeOut((t - 3.2f) / 0.6f));
			float cover = BattleShowtime.smooth((t - 3.2f) / BattleShowtime.MEGA_COVERED);
			Vec3 view = s.center.subtract(s.camera).normalize();
			Vec3 light = view.scale(-1).add(0, 0.9, 0).normalize();
			for (int i = 0; i < SHELL.size(); i++) {
				Vec3[] f = SHELL.get(i);
				Vec3 n = f[0].add(f[1]).add(f[2]).normalize();
				float facing = (float) -n.dot(view);
				float rim = 1f - Math.abs(facing);
				float lit = (float) Math.max(0, n.dot(light));
				int color = mix(mix(pal[1], pal[0], 0.35f + 0.65f * lit), 0xFFFFFF, 0.45f * rim * rim + 0.12f * hash(i * 7 + 5));
				// Back facets only while it is still see-through: once solid, the front ones alone (no depth written).
				float a = facing > 0f ? Mth.lerp(cover, 70 + 150 * rim * rim, 252 + 3 * rim) : (70 + 150 * rim * rim) * (1f - cover);
				tri(out, s.m, at(s.center, f[0], rs), at(s.center, f[1], rs), at(s.center, f[2], rs), color, (int) (shell * a));
			}
		}
	}

	private static void megaGlow(Scene s, VertexConsumer out, boolean primal) {
		int[] pal = megaPalette(primal);
		float t = s.t;
		Vec3 hand = BattleShowtime.hand();
		float shatter = BattleShowtime.MEGA_SHATTER;

		// 1. The Key Stone wakes on the wrist: motes drawn in, a pulsing core, a rainbow ring, a four-point flare.
		float stone = window(t, 0.15f, 2.5f, 0.3f, 0.3f);
		if (stone > 0f) {
			float pulse = 0.8f + 0.2f * Mth.sin(t * 14f);
			glow(out, s, hand, 0.55f * pulse, 0xFFFFFF, (int) (230 * stone));
			glow(out, s, hand, 1.1f, pal[1], (int) (110 * stone));
			flare(out, s, hand, 1.4f * stone * pulse, 0.05f, t * 0.6f, 0xFFFFFF, (int) (220 * stone));
			for (int i = 0; i < 18; i++) {
				float a = i / 18f;
				ringPoint(out, s, hand, 0.32 + 0.04 * Mth.sin(t * 6 + i), a * Mth.TWO_PI + t * 3, rainbow(a + t * 0.3f), (int) (200 * stone));
			}
			for (int i = 0; i < 26; i++) {
				float delay = hash(i * 7 + 1) * 0.8f;
				float p = Mth.clamp((t - 0.2f - delay) / 0.7f, 0f, 1f);
				if (p <= 0f || p >= 1f) {
					continue;
				}
				Vec3 dir = sphere(hash(i * 7 + 2), hash(i * 7 + 3));
				Vec3 at = hand.add(dir.scale(2.6 * Math.pow(1 - p, 1.6)));
				glow(out, s, at, 0.07f, 0xFFFFFF, (int) (255 * Math.sin(p * Math.PI)));
			}
		}

		// 2. Two strands of light, rainbow and white, from the Key Stone to the Mega Stone.
		float link = window(t, 1.3f, 2.5f, 0.0f, 0.15f);
		if (link > 0f) {
			float reach = BattleShowtime.easeOut((t - 1.3f) / 0.55f);
			Vec3 target = s.center;
			helix(out, s, hand, target, reach, 0.16, t, 0, true, (int) (230 * link));
			helix(out, s, hand, target, reach, 0.16, t, Mth.PI, false, (int) (230 * link));
			beam(out, s, hand, hand.add(target.subtract(hand).scale(reach)), 0.05f, 0xFFFFFF, (int) (200 * link), (int) (120 * link));
			if (reach > 0.95f) {
				glow(out, s, target, (float) s.r * 0.9f, pal[0], (int) (200 * link));
				flare(out, s, target, (float) s.r * 2.2f, 0.07f, -t, 0xFFFFFF, (int) (200 * link));
			}
		}

		// 3. The burst: rays from behind the pair, sparkles.
		float burst = window(t, 2.4f, 3.1f, 0.05f, 0.08f);
		if (burst > 0f) {
			Vec3 mid = s.middle();
			double radius = Math.max(mid.distanceTo(s.camera) + 2.5, s.trainer.distanceTo(s.feet) * 0.5 + 5);
			Vec3 away = mid.subtract(s.camera).normalize();
			Vec3 focus = mid.add(away.scale(radius * 0.97));
			rays(out, s, focus, away, radius * 1.4, 28, t * 0.5f, 0xFFFFFF, (int) (150 * burst), 0.10);
			glow(out, s, focus, (float) radius * 0.45f, 0xFFFFFF, (int) (200 * burst));
			for (int i = 0; i < 40; i++) {
				Vec3 dir = sphere(hash(i * 3 + 11), hash(i * 3 + 12));
				double d = radius * (0.35 + 0.55 * ((hash(i * 3 + 13) + (t - 2.4f) * 0.6f) % 1f));
				glow(out, s, mid.add(dir.scale(d)), 0.12f, 0xFFFFFF, (int) (200 * burst));
			}
		}

		// 4. The cocoon: an inner light, ribbons round it, the shell's facet edges, gold cracks, flares from below.
		float cocoon = window(t, 3.1f, shatter, 0.3f, 0f);
		if (cocoon > 0f) {
			double rs = s.r * 1.3 * (0.85 + 0.15 * BattleShowtime.easeOut((t - 3.2f) / 0.6f));
			float swell = Mth.clamp((t - 4.0f) / (shatter - 4.0f), 0f, 1f);
			// The light within, drawn on the shell's near side: at the centre, the Pokémon would cut its outline in it.
			Vec3 front = s.center.add(s.camera.subtract(s.center).normalize().scale(rs * 1.02));
			glow(out, s, front, (float) (rs * (1.4 + 0.6 * swell)), pal[0], (int) (cocoon * (120 + 100 * swell)));
			glow(out, s, front, (float) (rs * 0.7), 0xFFFFFF, (int) (cocoon * (60 + 180 * swell)));
			for (int k = 0; k < 3; k++) {
				orbitRibbon(out, s, s.center, rs * (1.12 + 0.06 * k), k * 1.1f + 0.4f, t * (2.2f + 0.5f * k) + k * 2f,
						k == 1 ? pal[2] : pal[1], (int) (220 * cocoon), 0.11 * s.r);
			}
			for (int i = 0; i < SHELL.size(); i += 2) {
				Vec3[] f = SHELL.get(i);
				edge(out, s, at(s.center, f[0], rs), at(s.center, f[1], rs), 0.012f * (float) s.r, 0xFFFFFF, (int) (cocoon * 70));
			}
			if (t > 4.0f) {
				// Cracks: more facet edges turn gold the closer the shatter.
				int cracks = (int) (swell * 60);
				for (int i = 0; i < cracks; i++) {
					Vec3[] f = SHELL.get((i * 53) % SHELL.size());
					edge(out, s, at(s.center, f[0], rs * 1.01), at(s.center, f[(i % 2) + 1], rs * 1.01), 0.03f * (float) s.r,
							primal ? pal[2] : 0xFFE070, 230);
				}
				for (int i = 0; i < 10; i++) {
					float a = hash(i * 5 + 3) * Mth.TWO_PI;
					float p = ((t * 1.7f + hash(i * 5 + 4)) % 1f);
					Vec3 base = s.feet.add(Math.cos(a) * rs * 1.1, 0, Math.sin(a) * rs * 1.1);
					beam(out, s, base.add(0, p * rs * 2.4, 0), base.add(0, p * rs * 2.4 + rs * 0.8, 0), 0.08f * (float) s.r,
							primal ? pal[1] : rainbow(hash(i) + t * 0.2f), (int) (200 * (1 - p)), 0);
				}
			}
		}

		// 5. The shatter: facets flying, a shockwave, rays.
		float since = t - shatter;
		if (since >= 0f && since < 1.4f) {
			double rs = s.r * 1.3;
			float fade = 1f - since / 1.4f;
			for (int i = 0; i < SHELL.size(); i++) {
				Vec3[] f = SHELL.get(i);
				Vec3 n = f[0].add(f[1]).add(f[2]).normalize();
				double fly = rs * (1 + since * (3.5 + 3 * hash(i * 3 + 1)));
				Vec3 c = s.center.add(n.scale(fly)).add(0, -since * since * 1.2, 0);
				Vec3 axis = sphere(hash(i * 3 + 2), hash(i * 3 + 3));
				float spin = since * (6 + 8 * hash(i));
				float shrink = (float) rs * 0.4f * fade;
				Vec3[] p = new Vec3[3];
				for (int k = 0; k < 3; k++) {
					p[k] = c.add(rotate(f[k].subtract(n).scale(shrink * 3.2), axis, spin));
				}
				tri(out, s.m, p[0], p[1], p[2], i % 3 == 0 ? pal[1] : mix(pal[0], 0xFFFFFF, 0.5f), (int) (200 * fade));
			}
			shockwave(out, s, s.feet.add(0, 0.1, 0), s.r * (1.2 + since * 9), 0.6 * s.r, 0xFFFFFF, (int) (220 * fade));
			glow(out, s, s.center, (float) (s.r * (2.4 + since * 3)), 0xFFFFFF, (int) (255 * fade * fade));
			rays(out, s, s.center, s.center.subtract(s.camera).normalize(), s.r * (3 + since * 6), 22, since, pal[0], (int) (170 * fade), 0.07);
		}

		// 6. The new form: rays behind it, its emblem above its head.
		if (since >= 0f) {
			float out1 = window(t, shatter, Kind.MEGA.seconds, 0.2f, 0.6f);
			Vec3 behind = s.center.add(s.center.subtract(s.camera).normalize().scale(s.r * 1.5));
			rays(out, s, behind, s.center.subtract(s.camera).normalize(), s.r * 6, 18, t * 0.15f, primal ? pal[2] : 0xFFF4D0,
					(int) (90 * out1), 0.09);
			float emblem = window(t, shatter + 0.5f, Kind.MEGA.seconds - 0.2f, 0.45f, 0.5f);
			if (emblem > 0f) {
				float pop = elastic(Mth.clamp((t - shatter - 0.5f) / 0.6f, 0f, 1f));
				Vec3 above = s.feet.add(0, s.r * 2 + 0.9 + 0.1 * Mth.sin(t * 3), 0);
				emblem(out, s, above, (float) Math.max(0.55, s.r * 0.55) * pop, primal, t, (int) (255 * emblem));
			}
		}
	}

	/** The Mega emblem (a ring round a twisting strand, rainbow sheen), or Primal's Ω / α, facing the camera. */
	private static void emblem(VertexConsumer out, Scene s, Vec3 at, float size, boolean primal, float t, int alpha) {
		Vec3[] b = billboard(s, at);
		boolean kyogre = "kyogre".equals(BattleShowtime.species());
		glow(out, s, at, size * 2.2f, primal ? (kyogre ? 0x4F8BFF : 0xFF5020) : 0xFFFFFF, alpha / 3);
		int segments = 40;
		for (int i = 0; i < segments; i++) {
			float a0 = i * Mth.TWO_PI / segments;
			float a1 = (i + 1) * Mth.TWO_PI / segments;
			int color = primal ? (kyogre ? 0x8FD8FF : 0xFFB060) : rainbow(i / (float) segments + t * 0.4f);
			Vec3 p0 = point(at, b, a0, size);
			Vec3 p1 = point(at, b, a1, size);
			edge(out, s, p0, p1, size * 0.09f, color, alpha);
		}
		if (!primal) {
			// The strand: two crossing S curves.
			for (int strand = 0; strand < 2; strand++) {
				Vec3 last = null;
				for (int i = 0; i <= 24; i++) {
					float k = i / 24f;
					double y = (k - 0.5) * 1.5 * size;
					double x = Math.sin(k * Mth.PI * 2 + strand * Mth.PI) * size * 0.32;
					Vec3 p = at.add(b[0].scale(x)).add(b[1].scale(y));
					if (last != null) {
						edge(out, s, last, p, size * 0.07f, strand == 0 ? 0xFFFFFF : rainbow(k + t * 0.4f), alpha);
					}
					last = p;
				}
			}
			glow(out, s, at, size * 0.25f, 0xFFFFFF, alpha);
		} else if (kyogre) {
			// α: a loop and a tail.
			Vec3 last = null;
			for (int i = 0; i <= 30; i++) {
				float k = i / 30f;
				float a = 0.4f + k * Mth.TWO_PI * 0.95f;
				Vec3 p = at.add(b[0].scale(Math.cos(a) * size * 0.38 - size * 0.08)).add(b[1].scale(Math.sin(a) * size * 0.34));
				if (last != null) {
					edge(out, s, last, p, size * 0.08f, 0xFFFFFF, alpha);
				}
				last = p;
			}
			edge(out, s, last, at.add(b[0].scale(size * 0.45)).add(b[1].scale(-size * 0.45)), size * 0.08f, 0xFFFFFF, alpha);
		} else {
			// Ω: an open arch on two feet.
			Vec3 last = null;
			for (int i = 0; i <= 30; i++) {
				float a = Mth.PI * (-0.25f + 1.5f * i / 30f);
				Vec3 p = at.add(b[0].scale(Math.cos(a) * size * 0.42)).add(b[1].scale(Math.sin(a) * size * 0.42 + size * 0.08));
				if (last != null) {
					edge(out, s, last, p, size * 0.08f, 0xFFFFFF, alpha);
				}
				last = p;
			}
			Vec3 l = point(at, b, Mth.PI * 1.25f, size * 0.42f).add(b[1].scale(size * 0.08));
			Vec3 r = point(at, b, -Mth.PI * 0.25f, size * 0.42f).add(b[1].scale(size * 0.08));
			edge(out, s, l, l.add(b[0].scale(-size * 0.22)), size * 0.08f, 0xFFFFFF, alpha);
			edge(out, s, r, r.add(b[0].scale(size * 0.22)), size * 0.08f, 0xFFFFFF, alpha);
		}
	}

	// =====================================================================
	// Z-Move
	// =====================================================================

	private static final int GOLD = 0xFFC020;
	private static final int GOLD_LIGHT = 0xFFF0A0;
	private static final int AMBER = 0xFF7A10;

	private static void zTint(Scene s, VertexConsumer out) {
		float veil = window(s.t, 2.4f, BattleShowtime.Z_RELEASE + 0.8f, 0.25f, 0.7f);
		if (veil > 0f) {
			double radius = s.center.distanceTo(s.camera) + 4 + s.r * 2;
			Vec3 away = s.center.subtract(s.camera).normalize();
			dome(out, s, s.center, radius, away, 0x8A4A08, 0x120802, veil * 0.95f);
			floor(out, s, s.center, s.feet.y + 0.03, radius, 0x1A0C02, veil * 0.95f);
		}
		zEmblem(s, out, true);
	}

	private static void zGlow(Scene s, VertexConsumer out) {
		float t = s.t;
		float release = BattleShowtime.Z_RELEASE;
		Vec3 chest = BattleShowtime.chest();

		// 1. The Z-Crystal before the trainer, golden spirals rising round them.
		float crystal = window(t, 0.1f, 2.6f, 0.3f, 0.4f);
		if (crystal > 0f) {
			gem(out, s, chest.add(s.forward.scale(0.25)), 0.16f + 0.03f * Mth.sin(t * 9), t * 3.5f, GOLD, (int) (240 * crystal));
			glow(out, s, chest.add(s.forward.scale(0.25)), 0.8f, GOLD, (int) (150 * crystal));
			flare(out, s, chest.add(s.forward.scale(0.25)), 1.6f * crystal, 0.05f, t * 0.8f, GOLD_LIGHT, (int) (220 * crystal));
			for (int k = 0; k < 3; k++) {
				Vec3 last = null;
				for (int i = 0; i <= 30; i++) {
					float p = i / 30f;
					double h = p * (s.height + 0.6);
					double a = p * Mth.TWO_PI * 1.6 + k * Mth.TWO_PI / 3 + t * 4;
					double rad = 0.75 - 0.2 * p;
					Vec3 q = s.trainer.add(Math.cos(a) * rad, h, Math.sin(a) * rad);
					if (last != null) {
						float head = Mth.clamp((t * 1.2f - p + 0.3f) % 1.2f, 0f, 1f);
						edge(out, s, last, q, 0.05f, k == 1 ? GOLD_LIGHT : GOLD, (int) (crystal * 230 * head));
					}
					last = q;
				}
			}
			shockwave(out, s, s.trainer.add(0, 0.05, 0), 0.9 + 0.1 * Mth.sin(t * 5), 0.3, GOLD, (int) (160 * crystal));
		}

		// 2. The stream of power to the Pokémon, pulses running along it.
		float stream = window(t, 1.5f, 3.0f, 0.0f, 0.4f);
		if (stream > 0f) {
			Vec3 from = chest.add(s.forward.scale(0.25));
			float reach = BattleShowtime.easeOut((t - 1.5f) / 0.5f);
			Vec3 to = from.add(s.center.subtract(from).scale(reach));
			beam(out, s, from, to, 0.22f, GOLD, (int) (200 * stream), (int) (200 * stream));
			beam(out, s, from, to, 0.08f, 0xFFFFFF, (int) (230 * stream), (int) (230 * stream));
			helix(out, s, from, s.center, reach, 0.3, t * 1.5f, 0, false, (int) (160 * stream));
			for (int i = 0; i < 6; i++) {
				float p = ((t - 1.5f) * 1.8f + i / 6f) % 1f;
				if (p < reach) {
					glow(out, s, from.add(s.center.subtract(from).scale(p)), 0.35f, GOLD_LIGHT, (int) (220 * stream));
				}
			}
		}

		// 3. The golden blaze round the Pokémon under a giant Z.
		float blaze = window(t, 2.4f, release + 0.3f, 0.35f, 0.3f);
		if (blaze > 0f) {
			float build = Mth.clamp((t - 2.4f) / (release - 2.4f), 0f, 1f);
			glow(out, s, s.center, (float) (s.r * (1.8 + 0.8 * build)), GOLD, (int) (blaze * (110 + 100 * build)));
			glow(out, s, s.center, (float) (s.r * 0.9), 0xFFFFFF, (int) (blaze * 140 * build));
			for (int i = 0; i < 26; i++) {
				// Flame tongues: rising, flickering, swaying.
				float a = hash(i * 3 + 1) * Mth.TWO_PI + t * 0.4f;
				float p = (t * (0.9f + 0.6f * hash(i * 3 + 2)) + hash(i * 3 + 3)) % 1f;
				double rad = s.r * (0.9 + 0.35 * hash(i));
				Vec3 base = s.feet.add(Math.cos(a) * rad, p * s.r * 1.2, Math.sin(a) * rad);
				Vec3 tip = base.add(Math.sin(t * 7 + i) * 0.2 * s.r, s.r * (1.0 + 1.4 * build) * (1 - p * 0.5), 0);
				beam(out, s, base, tip, 0.22f * (float) s.r * (1 - p), i % 3 == 0 ? AMBER : GOLD, (int) (blaze * 200 * (1 - p)), 0);
			}
			for (int k = 0; k < 3; k++) {
				float p = ((t - 2.4f) * 0.8f + k / 3f) % 1f;
				shockwave(out, s, s.feet.add(0, p * s.r * 3, 0), s.r * (1.5 - 0.4 * p), 0.15 * s.r, GOLD_LIGHT, (int) (blaze * 180 * (1 - p)));
			}
		}
		zEmblem(s, out, false);

		// 4. The release: a pillar of light, two shockwaves, rays.
		float since = t - release;
		if (since >= 0f && since < 1.6f) {
			float fade = 1f - since / 1.6f;
			double width = s.r * (0.8 + 1.2 * Math.sin(Math.min(1f, since / 0.5f) * Mth.HALF_PI)) * fade;
			beam(out, s, s.feet, s.feet.add(0, 48, 0), (float) width, GOLD, (int) (230 * fade), (int) (60 * fade));
			beam(out, s, s.feet, s.feet.add(0, 48, 0), (float) width * 0.4f, 0xFFFFFF, (int) (255 * fade), (int) (80 * fade));
			shockwave(out, s, s.feet.add(0, 0.1, 0), s.r * (1.5 + since * 12), 0.8 * s.r, GOLD_LIGHT, (int) (230 * fade));
			shockwave(out, s, s.feet.add(0, 0.2, 0), s.r * (1 + since * 7), 0.5 * s.r, 0xFFFFFF, (int) (200 * fade));
			rays(out, s, s.center, s.center.subtract(s.camera).normalize(), s.r * (4 + since * 6), 20, since, GOLD_LIGHT, (int) (160 * fade), 0.08);
			glow(out, s, s.center, (float) (s.r * (2.5 + since * 2)), 0xFFFFFF, (int) (255 * fade * fade));
		}
	}

	/**
	 * The Z-Move emblem (2026-11-25): a Z-Crystal standing over the Pokémon, a bold Z in it, unfolding with a bounce,
	 * rays turning behind it, bursting at the release. {@code fill}: the gem's solid body (translucent buffer), else
	 * its light (glow buffer).
	 */
	private static void zEmblem(Scene s, VertexConsumer out, boolean fill) {
		float t = s.t;
		float release = BattleShowtime.Z_RELEASE;
		float shown = window(t, 2.7f, release + 0.35f, 0.15f, 0.35f);
		if (shown <= 0f) {
			return;
		}
		Vec3 away = s.center.subtract(s.camera).normalize();
		float size = (float) Math.max(0.9, s.r * 1.3);
		Vec3 at = s.center.add(away.scale(s.r * 1.3)).add(0, s.r * 0.7 + size * 0.6 + 0.06 * Mth.sin(t * 3), 0);
		float pop = elastic(Mth.clamp((t - 2.7f) / 0.7f, 0f, 1f));
		float burst = t > release ? 1f + (t - release) * 2.5f : 1f;
		float height = size * pop * burst;
		// Unfolding sideways, as if turning to face the camera.
		float width = size * BattleShowtime.easeOut((t - 2.75f) / 0.45f) * burst;
		if (width <= 0.01f) {
			return;
		}
		Vec3[] b = billboard(s, at);
		Vec3[] gem = new Vec3[6];
		float[][] shape = { { 0, 1f }, { 0.6f, 0.42f }, { 0.6f, -0.42f }, { 0, -1f }, { -0.6f, -0.42f }, { -0.6f, 0.42f } };
		for (int i = 0; i < 6; i++) {
			gem[i] = at.add(b[0].scale(shape[i][0] * width)).add(b[1].scale(shape[i][1] * height));
		}
		int alpha = (int) (255 * shown);
		if (fill) {
			// The body: a deep amber gem, golden at its heart, each facet its own shade.
			for (int i = 0; i < 6; i++) {
				int edgeColor = i % 2 == 0 ? 0x8A3E00 : 0xB05A00;
				Draw3.vertex(out, s.m, at, 0xFFC020, alpha * 9 / 10);
				Draw3.vertex(out, s.m, gem[i], edgeColor, alpha * 9 / 10);
				Draw3.vertex(out, s.m, gem[(i + 1) % 6], edgeColor, alpha * 9 / 10);
				Draw3.vertex(out, s.m, at, 0xFFC020, alpha * 9 / 10);
			}
			return;
		}
		rays(out, s, at.add(away.scale(0.05)), away, size * 3.2 * burst, 16, t * 0.8f, GOLD, (int) (130 * shown), 0.07);
		glow(out, s, at, size * 2.2f * burst, GOLD, (int) (110 * shown));
		float w = size * 0.07f;
		for (int i = 0; i < 6; i++) {
			edge(out, s, gem[i], gem[(i + 1) % 6], w, GOLD_LIGHT, alpha);
			Vec3 outer0 = at.add(gem[i].subtract(at).scale(1.14));
			Vec3 outer1 = at.add(gem[(i + 1) % 6].subtract(at).scale(1.14));
			edge(out, s, outer0, outer1, w * 0.5f, GOLD, alpha * 2 / 3);
		}
		// The Z: three bars, gold with a white core.
		Vec3 tl = at.add(b[0].scale(-0.34 * width)).add(b[1].scale(0.4 * height));
		Vec3 tr = at.add(b[0].scale(0.34 * width)).add(b[1].scale(0.4 * height));
		Vec3 bl = at.add(b[0].scale(-0.34 * width)).add(b[1].scale(-0.4 * height));
		Vec3 br = at.add(b[0].scale(0.34 * width)).add(b[1].scale(-0.4 * height));
		for (Vec3[] bar : new Vec3[][] { { tl, tr }, { tr, bl }, { bl, br } }) {
			edge(out, s, bar[0], bar[1], size * 0.16f, GOLD, alpha);
			edge(out, s, bar[0], bar[1], size * 0.07f, 0xFFFFFF, alpha);
		}
		flare(out, s, gem[0], size * 1.2f * shown, 0.05f, t, 0xFFFFFF, (int) (200 * shown * (0.6f + 0.4f * Mth.sin(t * 6))));
	}

	// =====================================================================
	// Terastallization
	// =====================================================================

	/** The prisms that grow round the Pokémon: base angle, distance, tilt, length, delay (fractions). */
	private static final int PRISMS = 16;

	private static void teraTint(Scene s, VertexConsumer out) {
		float t = s.t;
		float shatter = BattleShowtime.TERA_SHATTER;
		int col = BattleShowtime.color();
		// A night sky behind the whole set piece.
		float veil = window(t, 0.0f, Kind.TERA.seconds - 0.2f, 0.4f, 0.9f);
		if (veil > 0f) {
			Vec3 focus = t < 2.5f ? s.middle() : s.center;
			double radius = Math.max(focus.distanceTo(s.camera) + 4 + s.r * 2, s.trainer.distanceTo(s.feet) * 0.5 + 6);
			Vec3 away = focus.subtract(s.camera).normalize();
			dome(out, s, focus, radius, away, mix(0x101A40, col, 0.25f), 0x02030A, veil * 0.97f);
			floor(out, s, focus, s.feet.y + 0.03, radius, 0x04060F, veil * 0.97f);
		}
		// The crystal: prisms from the ground, a block round the Pokémon.
		float grow = Mth.clamp((t - 3.0f) / 1.4f, 0f, 1f);
		if (t >= 3.0f && t < shatter) {
			int fill = mix(col, 0xFFFFFF, 0.55f);
			for (int i = 0; i < PRISMS; i++) {
				Prism p = prism(s, i, grow);
				if (p != null) {
					prismFaces(out, s, p, fill, 120);
				}
			}
			double h = s.r * 2.3 * BattleShowtime.easeOut(Mth.clamp((t - 3.2f) / 1.0f, 0f, 1f));
			if (h > 0.05) {
				prismFaces(out, s, new Prism(s.feet, UP, s.r * 1.15, h, s.r * 0.6, 0.3f), fill, 90);
			}
		}
	}

	private static void teraGlow(Scene s, VertexConsumer out) {
		float t = s.t;
		float shatter = BattleShowtime.TERA_SHATTER;
		int col = BattleShowtime.color();
		Vec3 orb = BattleShowtime.orb(t);

		// Stars on the night sky.
		float veil = window(t, 0.0f, Kind.TERA.seconds - 0.2f, 0.4f, 0.9f);
		if (veil > 0f) {
			Vec3 focus = t < 2.5f ? s.middle() : s.center;
			double radius = Math.max(focus.distanceTo(s.camera) + 4 + s.r * 2, s.trainer.distanceTo(s.feet) * 0.5 + 6) * 0.96;
			for (int i = 0; i < 90; i++) {
				Vec3 dir = sphere(hash(i * 2 + 31), 0.35f + 0.65f * hash(i * 2 + 32));
				float twinkle = 0.5f + 0.5f * Mth.sin(t * (2 + 3 * hash(i)) + i);
				glow(out, s, focus.add(dir.scale(radius)), 0.12f + 0.12f * hash(i + 5), 0xFFFFFF, (int) (veil * 200 * twinkle));
			}
		}

		// 1. The Tera Orb charged above the trainer's head: streams of sparkles spiralling in, a halo ring.
		float charge = window(t, 0.2f, 1.75f, 0.3f, 0f);
		if (charge > 0f) {
			for (int k = 0; k < 4; k++) {
				for (int i = 0; i < 26; i++) {
					float p = ((t * 0.7f + i / 26f) % 1f);
					double a = k * Mth.HALF_PI + p * Mth.TWO_PI * 1.5 + t;
					double rad = 3.2 * (1 - p);
					Vec3 q = orb.add(Math.cos(a) * rad, (1 - p) * (k % 2 == 0 ? 1.8 : -0.8), Math.sin(a) * rad);
					glow(out, s, q, 0.05f + 0.05f * p, k % 2 == 0 ? 0xBFF8FF : rainbow(p + k * 0.25f), (int) (charge * 230 * p));
				}
			}
		}
		float orbShown = window(t, 0.25f, 2.55f, 0.3f, 0.05f);
		if (orbShown > 0f) {
			float power = Mth.clamp(t / 1.6f, 0f, 1f);
			faceted(out, s, ORB, orb, 0.2 + 0.05 * power, t * 0.8f, 0xBFF8FF, (int) (orbShown * 220));
			glow(out, s, orb, 0.5f + 0.9f * power, 0x9FEFFF, (int) (orbShown * 170));
			glow(out, s, orb, 0.22f, 0xFFFFFF, (int) (orbShown * 255));
			flare(out, s, orb, 1.2f + 1.4f * power, 0.04f, t * 0.4f, 0xFFFFFF, (int) (orbShown * 200 * power));
			if (t > 1.1f && t < 1.8f) {
				float ring = (t - 1.1f) / 0.7f;
				Vec3[] b = billboard(s, orb);
				circle(out, s, orb, b, 0.35f + ring * 1.4f, 0.035f, 0x7FF0FF, (int) (240 * (1 - ring)));
			}
			if (t > 1.7f) {
				// Its trail across the sky.
				for (int i = 1; i <= 16; i++) {
					Vec3 back = BattleShowtime.orb(t - i * 0.03f);
					glow(out, s, back, 0.18f * (1 - i / 17f), rainbow(i / 16f + t), (int) (orbShown * 200 * (1 - i / 17f)));
				}
			}
		}

		// 2. Over the Pokémon, the orb bursts into a crystal star.
		float star = window(t, 2.5f, 3.4f, 0.05f, 0.4f);
		if (star > 0f) {
			Vec3 at = BattleShowtime.orb(2.5f);
			float size = (float) Math.max(0.8, s.r * 0.9) * BattleShowtime.easeOut((t - 2.5f) / 0.3f);
			Vec3[] b = billboard(s, at);
			for (int i = 0; i < 6; i++) {
				float a = i * Mth.TWO_PI / 6 + (t - 2.5f) * 0.6f;
				Vec3 tip = point(at, b, a, size * 1.5f);
				Vec3 l = point(at, b, a - 0.35f, size * 0.42f);
				Vec3 r = point(at, b, a + 0.35f, size * 0.42f);
				tri(out, s.m, at, l, tip, mix(col, 0xFFFFFF, 0.5f), (int) (200 * star));
				tri(out, s.m, at, r, tip, mix(col, 0xFFFFFF, 0.2f), (int) (200 * star));
				edge(out, s, l, tip, size * 0.03f, 0xFFFFFF, (int) (230 * star));
				edge(out, s, r, tip, size * 0.03f, 0xFFFFFF, (int) (230 * star));
			}
			glow(out, s, at, size * 1.2f, 0xFFFFFF, (int) (230 * star));
			rays(out, s, at, at.subtract(s.camera).normalize(), size * 5, 16, t, mix(col, 0xFFFFFF, 0.6f), (int) (140 * star), 0.06);
		}

		// 3. The crystal growing: lit edges, a glint climbing, sparkles.
		float grow = Mth.clamp((t - 3.0f) / 1.4f, 0f, 1f);
		if (t >= 3.0f && t < shatter) {
			float glint = ((t - 3.0f) * 0.9f) % 1f;
			for (int i = 0; i < PRISMS; i++) {
				Prism p = prism(s, i, grow);
				if (p != null) {
					prismEdges(out, s, p, 0xFFFFFF, 200, glint);
				}
			}
			double h = s.r * 2.3 * BattleShowtime.easeOut(Mth.clamp((t - 3.2f) / 1.0f, 0f, 1f));
			if (h > 0.05) {
				prismEdges(out, s, new Prism(s.feet, UP, s.r * 1.15, h, s.r * 0.6, 0.3f), 0xFFFFFF, 170, glint);
			}
			glow(out, s, s.center, (float) (s.r * 1.8), col, (int) (90 + 80 * grow));
			for (int i = 0; i < 24; i++) {
				Vec3 dir = sphere(hash(i * 4 + 61), hash(i * 4 + 62));
				float tw = 0.5f + 0.5f * Mth.sin(t * 9 + i * 1.7f);
				glow(out, s, s.center.add(dir.scale(s.r * (1.2 + 0.8 * hash(i)))), 0.1f, 0xFFFFFF, (int) (220 * tw));
			}
		}

		// 4. The shatter: every face a flying shard, a spray of chips towards the camera.
		float since = t - shatter;
		if (since >= 0f && since < 1.5f) {
			float fade = 1f - since / 1.5f;
			for (int i = 0; i < 140; i++) {
				Vec3 dir = sphere(hash(i * 5 + 71), hash(i * 5 + 72));
				Vec3 toward = s.camera.subtract(s.center).normalize();
				Vec3 d = i % 3 == 0 ? dir.add(toward.scale(1.2)).normalize() : dir;
				Vec3 c = s.center.add(d.scale(s.r * (0.6 + since * (4 + 6 * hash(i * 5 + 73))))).add(0, -since * since * 1.5, 0);
				Vec3 axis = sphere(hash(i * 5 + 74), hash(i * 5 + 75));
				float size = (float) (s.r * (0.12 + 0.2 * hash(i))) * fade;
				Vec3 a = rotate(new Vec3(size, 0, 0), axis, since * 9 + i);
				Vec3 bb = rotate(new Vec3(-size * 0.5, size * 0.9, 0), axis, since * 9 + i);
				Vec3 cc = rotate(new Vec3(-size * 0.4, -size * 0.8, size * 0.3), axis, since * 9 + i);
				int color = i % 4 == 0 ? 0xFFFFFF : i % 4 == 1 ? col : mix(col, 0xFFFFFF, 0.5f);
				tri(out, s.m, c.add(a), c.add(bb), c.add(cc), color, (int) (230 * fade));
			}
			glow(out, s, s.center, (float) (s.r * (2.5 + since * 3)), 0xFFFFFF, (int) (255 * fade * fade));
			shockwave(out, s, s.feet.add(0, 0.1, 0), s.r * (1.3 + since * 8), 0.6 * s.r, mix(col, 0xFFFFFF, 0.5f), (int) (220 * fade));
		}

		// 5. Crystalline: beams behind it, the Tera jewel above its head, a storm of sparkles.
		if (since >= 0f) {
			float shown = window(t, shatter, Kind.TERA.seconds, 0.15f, 0.6f);
			Vec3 away = s.center.subtract(s.camera).normalize();
			rays(out, s, s.center.add(away.scale(s.r * 1.5)), away, s.r * 7, 20, t * 0.2f, mix(col, 0xFFFFFF, 0.5f), (int) (110 * shown), 0.08);
			float crown = window(t, shatter + 0.3f, Kind.TERA.seconds - 0.1f, 0.4f, 0.5f);
			if (crown > 0f) {
				float pop = elastic(Mth.clamp((t - shatter - 0.3f) / 0.6f, 0f, 1f));
				Vec3 above = s.feet.add(0, s.r * 2 + 0.6, 0);
				jewel(out, s, above, (float) Math.max(0.4, s.r * 0.45) * pop, t, col, (int) (240 * crown));
			}
			for (int i = 0; i < 70; i++) {
				Vec3 dir = sphere(hash(i * 3 + 91), hash(i * 3 + 92));
				double d = s.r * (1.0 + 4 * ((hash(i * 3 + 93) + since * 0.25f) % 1f));
				float tw = 0.5f + 0.5f * Mth.sin(t * 8 + i);
				glow(out, s, s.center.add(dir.scale(d)), 0.06f + 0.06f * hash(i), i % 3 == 0 ? col : 0xFFFFFF, (int) (shown * 220 * tw));
			}
		}
	}

	/** The Tera jewel: a tall central crystal between two leaning ones, on a lit base. */
	private static void jewel(VertexConsumer out, Scene s, Vec3 at, float size, float t, int col, int alpha) {
		glow(out, s, at, size * 2.4f, col, alpha / 3);
		float spin = t * 0.7f;
		Vec3 axis = new Vec3(Math.cos(spin), 0, Math.sin(spin));
		Vec3[] dirs = { UP, UP.add(axis.scale(0.6)).normalize(), UP.subtract(axis.scale(0.6)).normalize() };
		double[] length = { size * 1.6, size * 1.0, size * 1.0 };
		for (int i = 0; i < 3; i++) {
			Prism p = new Prism(at.add(0, -size * 0.5, 0), dirs[i], size * (i == 0 ? 0.32 : 0.24), length[i], size * 0.45, spin);
			prismFaces(out, s, p, mix(col, 0xFFFFFF, i == 0 ? 0.25f : 0.45f), alpha * 3 / 4);
			prismEdges(out, s, p, 0xFFFFFF, alpha, (t * 0.8f) % 1f);
		}
	}

	/** A hexagonal crystal: base, axis, radius, body length, tip length, turn. */
	private record Prism(Vec3 base, Vec3 axis, double radius, double length, double tip, float turn) {
	}

	/** Prism {@code i} of the growth round the Pokémon at {@code grow} (0..1), or null before it starts. */
	private static Prism prism(Scene s, int i, float grow) {
		float delay = hash(i * 9 + 1) * 0.45f;
		float k = BattleShowtime.easeOut(Mth.clamp((grow - delay) / (1f - delay), 0f, 1f));
		if (k <= 0.01f) {
			return null;
		}
		double a = hash(i * 9 + 2) * Mth.TWO_PI;
		double d = s.r * (0.3 + 0.9 * hash(i * 9 + 3));
		Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
		double tilt = 0.15 + 0.7 * hash(i * 9 + 4);
		Vec3 axis = UP.add(out.scale(tilt)).normalize();
		double length = s.r * (1.2 + 1.4 * hash(i * 9 + 5)) * k;
		double radius = s.r * (0.16 + 0.16 * hash(i * 9 + 6)) * Math.min(1, k * 2);
		return new Prism(s.feet.add(out.scale(d)).add(0, -0.2, 0), axis, radius, length, radius * 1.6, hash(i * 9 + 7) * Mth.TWO_PI);
	}

	private static Vec3[] ring(Prism p, double along) {
		Vec3[] b = Draw3.basis(p.axis);
		Vec3 c = p.base.add(p.axis.scale(along));
		Vec3[] pts = new Vec3[6];
		for (int i = 0; i < 6; i++) {
			double a = p.turn + i * Math.PI / 3;
			pts[i] = c.add(b[0].scale(Math.cos(a) * p.radius)).add(b[1].scale(Math.sin(a) * p.radius));
		}
		return pts;
	}

	/** The prism's faces, each a shade of {@code color} by how it faces a light from above and behind the camera. */
	private static void prismFaces(VertexConsumer out, Scene s, Prism p, int color, int alpha) {
		Vec3[] low = ring(p, 0);
		Vec3[] high = ring(p, p.length);
		Vec3 tip = p.base.add(p.axis.scale(p.length + p.tip));
		Vec3 light = s.camera.subtract(p.base).normalize().add(0, 1.2, 0).normalize();
		for (int i = 0; i < 6; i++) {
			int j = (i + 1) % 6;
			Vec3 mid = low[i].add(low[j]).scale(0.5).subtract(p.base.add(p.axis.scale(p.length * 0.5)));
			float lit = (float) Math.max(0, mid.normalize().dot(light));
			int shade = mix(mix(color, 0x203050, 0.35f), 0xFFFFFF, lit * 0.6f);
			quad(out, s.m, low[i], low[j], high[j], high[i], shade, alpha);
			tri(out, s.m, high[i], high[j], tip, mix(shade, 0xFFFFFF, 0.25f), alpha);
		}
	}

	/** The prism's edges, lit, with a glint band climbing it ({@code glint} 0..1). */
	private static void prismEdges(VertexConsumer out, Scene s, Prism p, int color, int alpha, float glint) {
		Vec3[] low = ring(p, 0);
		Vec3[] high = ring(p, p.length);
		Vec3 tip = p.base.add(p.axis.scale(p.length + p.tip));
		float w = (float) Math.max(0.012, p.radius * 0.06);
		for (int i = 0; i < 6; i++) {
			edge(out, s, low[i], high[i], w, color, alpha / 2);
			edge(out, s, high[i], tip, w, color, alpha);
			edge(out, s, high[i], high[(i + 1) % 6], w, color, alpha / 2);
		}
		Vec3[] band = ring(p, (p.length + p.tip * 0.5) * glint);
		for (int i = 0; i < 6; i++) {
			edge(out, s, band[i], band[(i + 1) % 6], w * 2.5f, 0xFFFFFF, (int) (alpha * Math.sin(glint * Math.PI)));
		}
	}

	// =====================================================================
	// Shapes
	// =====================================================================

	/**
	 * A sphere seen from inside: the backdrop that replaces the arena. Bright round the point straight ahead of the
	 * camera ({@code away}), deep elsewhere.
	 */
	private static void dome(VertexConsumer out, Scene s, Vec3 at, double radius, Vec3 away, int bright, int deep, float alpha) {
		int rings = 16;
		int segments = 32;
		int a = (int) (255 * Mth.clamp(alpha, 0f, 1f));
		for (int r = 0; r < rings; r++) {
			double t0 = Math.PI * r / rings;
			double t1 = Math.PI * (r + 1) / rings;
			for (int k = 0; k < segments; k++) {
				double p0 = Math.PI * 2 * k / segments;
				double p1 = Math.PI * 2 * (k + 1) / segments;
				Vec3[] n = { Draw3.unit(t0, p0), Draw3.unit(t0, p1), Draw3.unit(t1, p1), Draw3.unit(t1, p0) };
				for (Vec3 v : n) {
					float lit = (float) Math.max(0, v.dot(away));
					Draw3.vertex(out, s.m, at.add(v.scale(radius)), mix(deep, bright, lit * lit * lit), a);
				}
			}
		}
	}

	/** A floor disc that hides the ground under the backdrop. */
	private static void floor(VertexConsumer out, Scene s, Vec3 at, double y, double radius, int color, float alpha) {
		int a = (int) (255 * Mth.clamp(alpha, 0f, 1f));
		int segments = 32;
		Vec3 c = new Vec3(at.x, y, at.z);
		for (int k = 0; k < segments; k++) {
			double a0 = Math.PI * 2 * k / segments;
			double a1 = Math.PI * 2 * (k + 1) / segments;
			Draw3.vertex(out, s.m, c, color, a);
			Draw3.vertex(out, s.m, c.add(Math.cos(a0) * radius, 0, Math.sin(a0) * radius), color, a);
			Draw3.vertex(out, s.m, c.add(Math.cos(a1) * radius, 0, Math.sin(a1) * radius), color, a);
			Draw3.vertex(out, s.m, c, color, a);
		}
	}

	/** A soft light facing the camera: {@code color} at the centre, nothing at {@code radius}. */
	private static void glow(VertexConsumer out, Scene s, Vec3 at, float radius, int color, int alpha) {
		if (alpha <= 0 || radius <= 0f) {
			return;
		}
		Vec3[] b = billboard(s, at);
		int segments = 14;
		for (int k = 0; k < segments; k++) {
			float a0 = k * Mth.TWO_PI / segments;
			float a1 = (k + 1) * Mth.TWO_PI / segments;
			Draw3.vertex(out, s.m, at, color, Math.min(255, alpha));
			Draw3.vertex(out, s.m, point(at, b, a0, radius), color, 0);
			Draw3.vertex(out, s.m, point(at, b, a1, radius), color, 0);
			Draw3.vertex(out, s.m, at, color, Math.min(255, alpha));
		}
	}

	/** A four-point lens flare, {@code length} from tip to tip. */
	private static void flare(VertexConsumer out, Scene s, Vec3 at, float length, float width, float turn, int color, int alpha) {
		if (alpha <= 0) {
			return;
		}
		Vec3[] b = billboard(s, at);
		for (int i = 0; i < 4; i++) {
			float a = turn + i * Mth.HALF_PI;
			float len = i % 2 == 0 ? length : length * 0.6f;
			Vec3 dir = b[0].scale(Math.cos(a)).add(b[1].scale(Math.sin(a)));
			Vec3 side = b[0].scale(-Math.sin(a)).add(b[1].scale(Math.cos(a))).scale(width * len);
			Draw3.vertex(out, s.m, at.add(side), color, alpha);
			Draw3.vertex(out, s.m, at.subtract(side), color, alpha);
			Draw3.vertex(out, s.m, at.add(dir.scale(len * 0.5)), color, 0);
			Draw3.vertex(out, s.m, at.add(dir.scale(len * 0.5)), color, 0);
		}
	}

	/** A glowing beam facing the camera, soft on both sides; alpha {@code a0} at {@code from}, {@code a1} at {@code to}. */
	private static void beam(VertexConsumer out, Scene s, Vec3 from, Vec3 to, float width, int color, int a0, int a1) {
		Vec3 axis = to.subtract(from);
		if (axis.lengthSqr() < 1.0E-6) {
			return;
		}
		Vec3 side = axis.cross(s.camera.subtract(from)).normalize().scale(width);
		Draw3.vertex(out, s.m, from.add(side), color, 0);
		Draw3.vertex(out, s.m, from, color, a0);
		Draw3.vertex(out, s.m, to, color, a1);
		Draw3.vertex(out, s.m, to.add(side), color, 0);
		Draw3.vertex(out, s.m, from, color, a0);
		Draw3.vertex(out, s.m, from.subtract(side), color, 0);
		Draw3.vertex(out, s.m, to.subtract(side), color, 0);
		Draw3.vertex(out, s.m, to, color, a1);
	}

	/** A line of light of even width (an edge, a stroke). */
	private static void edge(VertexConsumer out, Scene s, Vec3 from, Vec3 to, float width, int color, int alpha) {
		if (alpha > 0) {
			beam(out, s, from, to, width, color, alpha, alpha);
		}
	}

	/** A dot of a ring (a bead of light). */
	private static void ringPoint(VertexConsumer out, Scene s, Vec3 at, double radius, float angle, int color, int alpha) {
		Vec3[] b = billboard(s, at);
		glow(out, s, point(at, b, angle, (float) radius), 0.06f, color, alpha);
	}

	/** A thin ring in the plane ({@code b[0]}, {@code b[1]}). */
	private static void circle(VertexConsumer out, Scene s, Vec3 at, Vec3[] b, float radius, float width, int color, int alpha) {
		int segments = 36;
		for (int i = 0; i < segments; i++) {
			edge(out, s, point(at, b, i * Mth.TWO_PI / segments, radius), point(at, b, (i + 1) * Mth.TWO_PI / segments, radius), width,
					color, alpha);
		}
	}

	/** A flat ring on the ground: bright in the band, soft both sides. */
	private static void shockwave(VertexConsumer out, Scene s, Vec3 at, double radius, double width, int color, int alpha) {
		if (alpha <= 0) {
			return;
		}
		int segments = 48;
		for (int i = 0; i < segments; i++) {
			double a0 = Math.PI * 2 * i / segments;
			double a1 = Math.PI * 2 * (i + 1) / segments;
			Vec3 d0 = new Vec3(Math.cos(a0), 0, Math.sin(a0));
			Vec3 d1 = new Vec3(Math.cos(a1), 0, Math.sin(a1));
			Draw3.vertex(out, s.m, at.add(d0.scale(Math.max(0, radius - width))), color, 0);
			Draw3.vertex(out, s.m, at.add(d0.scale(radius)), color, alpha);
			Draw3.vertex(out, s.m, at.add(d1.scale(radius)), color, alpha);
			Draw3.vertex(out, s.m, at.add(d1.scale(Math.max(0, radius - width))), color, 0);
			Draw3.vertex(out, s.m, at.add(d0.scale(radius)), color, alpha);
			Draw3.vertex(out, s.m, at.add(d0.scale(radius + width * 0.4)), color, 0);
			Draw3.vertex(out, s.m, at.add(d1.scale(radius + width * 0.4)), color, 0);
			Draw3.vertex(out, s.m, at.add(d1.scale(radius)), color, alpha);
		}
	}

	/** Rays fanning out from {@code at} across the plane facing {@code normal}, slowly turning. */
	private static void rays(VertexConsumer out, Scene s, Vec3 at, Vec3 normal, double length, int count, float turn, int color, int alpha,
			double width) {
		if (alpha <= 0) {
			return;
		}
		Vec3[] b = Draw3.basis(normal);
		for (int i = 0; i < count; i++) {
			double a = turn + i * Math.PI * 2 / count + hash(i * 13) * 0.3;
			double len = length * (0.55 + 0.45 * hash(i * 13 + 1));
			double half = width * (0.6 + 0.8 * hash(i * 13 + 2));
			Vec3 d0 = b[0].scale(Math.cos(a - half)).add(b[1].scale(Math.sin(a - half)));
			Vec3 d1 = b[0].scale(Math.cos(a + half)).add(b[1].scale(Math.sin(a + half)));
			Draw3.vertex(out, s.m, at, color, alpha);
			Draw3.vertex(out, s.m, at.add(d0.scale(len)), color, 0);
			Draw3.vertex(out, s.m, at.add(d1.scale(len)), color, 0);
			Draw3.vertex(out, s.m, at, color, alpha);
		}
	}

	/** One strand of a double helix from {@code from} to {@code to}, drawn up to {@code reach}. */
	private static void helix(VertexConsumer out, Scene s, Vec3 from, Vec3 to, float reach, double radius, float t, float phase,
			boolean rainbow, int alpha) {
		Vec3 axis = to.subtract(from);
		Vec3[] b = Draw3.basis(axis.normalize());
		int steps = 48;
		Vec3 last = null;
		for (int i = 0; i <= steps; i++) {
			float p = i / (float) steps;
			if (p > reach) {
				break;
			}
			double a = p * Math.PI * 7 - t * 9 + phase;
			double rad = radius * Math.sin(Math.PI * Math.min(1, p * 1.05)) + 0.02;
			Vec3 q = from.add(axis.scale(p)).add(b[0].scale(Math.cos(a) * rad)).add(b[1].scale(Math.sin(a) * rad));
			if (last != null) {
				edge(out, s, last, q, 0.035f, rainbow ? rainbow(p * 1.5f - t * 0.5f) : 0xFFFFFF, alpha);
			}
			last = q;
		}
	}

	/** A ribbon on a tilted orbit round {@code at}, fading towards its tail. */
	private static void orbitRibbon(VertexConsumer out, Scene s, Vec3 at, double radius, float tilt, float angle, int color, int alpha,
			double width) {
		Vec3 normal = new Vec3(Math.sin(tilt), Math.cos(tilt), Math.sin(tilt * 1.7) * 0.5).normalize();
		Vec3[] b = Draw3.basis(normal);
		int steps = 28;
		Vec3 last = null;
		for (int i = 0; i <= steps; i++) {
			float p = i / (float) steps;
			double a = angle - p * Math.PI * 1.4;
			Vec3 q = at.add(b[0].scale(Math.cos(a) * radius)).add(b[1].scale(Math.sin(a) * radius));
			if (last != null) {
				beam(out, s, last, q, (float) (width * (1 - p * 0.7)), color, (int) (alpha * (1 - p)), (int) (alpha * (1 - p)));
			}
			last = q;
		}
	}

	/** A faceted ball (the Tera Orb): each facet its own shade of iridescence. */
	private static void faceted(VertexConsumer out, Scene s, List<Vec3[]> mesh, Vec3 at, double radius, float turn, int color, int alpha) {
		Vec3 axis = new Vec3(0.3, 1, 0.2).normalize();
		Vec3 view = at.subtract(s.camera).normalize();
		for (int i = 0; i < mesh.size(); i++) {
			Vec3[] f = mesh.get(i);
			Vec3 a = rotate(f[0], axis, turn);
			Vec3 b = rotate(f[1], axis, turn);
			Vec3 c = rotate(f[2], axis, turn);
			Vec3 n = a.add(b).add(c).normalize();
			float facing = (float) Math.abs(n.dot(view));
			int shade = mix(color, rainbow(hash(i) + turn * 0.2f), 0.35f * (1 - facing));
			tri(out, s.m, at.add(a.scale(radius)), at.add(b.scale(radius)), at.add(c.scale(radius)), mix(shade, 0xFFFFFF, facing * facing * 0.5f),
					(int) (alpha * (0.4f + 0.6f * facing)));
		}
	}

	/** A spinning gem (two pyramids base to base). */
	private static void gem(VertexConsumer out, Scene s, Vec3 at, float size, float turn, int color, int alpha) {
		Vec3 top = at.add(0, size * 1.4, 0);
		Vec3 bottom = at.add(0, -size * 1.4, 0);
		for (int i = 0; i < 4; i++) {
			double a0 = turn + i * Math.PI / 2;
			double a1 = turn + (i + 1) * Math.PI / 2;
			Vec3 p0 = at.add(Math.cos(a0) * size, 0, Math.sin(a0) * size);
			Vec3 p1 = at.add(Math.cos(a1) * size, 0, Math.sin(a1) * size);
			tri(out, s.m, top, p0, p1, i % 2 == 0 ? mix(color, 0xFFFFFF, 0.5f) : color, alpha);
			tri(out, s.m, bottom, p0, p1, i % 2 == 0 ? color : mix(color, 0x000000, 0.2f), alpha);
			edge(out, s, top, p0, size * 0.06f, 0xFFFFFF, alpha);
			edge(out, s, bottom, p0, size * 0.06f, 0xFFFFFF, alpha / 2);
		}
	}

	private static void tri(VertexConsumer out, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, int color, int alpha) {
		Draw3.vertex(out, m, a, color, alpha);
		Draw3.vertex(out, m, b, color, alpha);
		Draw3.vertex(out, m, c, color, alpha);
		Draw3.vertex(out, m, c, color, alpha);
	}

	private static void quad(VertexConsumer out, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color, int alpha) {
		Draw3.vertex(out, m, a, color, alpha);
		Draw3.vertex(out, m, b, color, alpha);
		Draw3.vertex(out, m, c, color, alpha);
		Draw3.vertex(out, m, d, color, alpha);
	}

	// =====================================================================
	// Helpers
	// =====================================================================

	/** Two axes across the line from the camera to {@code at}: right and up on screen. */
	private static Vec3[] billboard(Scene s, Vec3 at) {
		Vec3 normal = s.camera.subtract(at).normalize();
		Vec3 u = UP.cross(normal);
		u = u.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : u.normalize();
		return new Vec3[] { u, normal.cross(u).normalize() };
	}

	private static Vec3 point(Vec3 at, Vec3[] b, float angle, float radius) {
		return at.add(b[0].scale(Math.cos(angle) * radius)).add(b[1].scale(Math.sin(angle) * radius));
	}

	private static Vec3 at(Vec3 center, Vec3 unit, double radius) {
		return center.add(unit.scale(radius));
	}

	/** A direction on the unit sphere from two numbers in [0, 1). */
	private static Vec3 sphere(float u, float v) {
		double z = u * 2 - 1;
		double a = v * Math.PI * 2;
		double r = Math.sqrt(Math.max(0, 1 - z * z));
		return new Vec3(Math.cos(a) * r, z, Math.sin(a) * r);
	}

	/** {@code v} turned by {@code angle} radians round the unit {@code axis} (Rodrigues). */
	private static Vec3 rotate(Vec3 v, Vec3 axis, float angle) {
		double c = Math.cos(angle);
		double sn = Math.sin(angle);
		return v.scale(c).add(axis.cross(v).scale(sn)).add(axis.scale(axis.dot(v) * (1 - c)));
	}

	/** 0 before {@code from}, up to 1 over {@code in}, back to 0 over {@code out} before {@code to}. */
	private static float window(float t, float from, float to, float in, float out) {
		if (t < from || t > to) {
			return 0f;
		}
		float a = in <= 0f ? 1f : BattleShowtime.smooth((t - from) / in);
		float b = out <= 0f ? 1f : BattleShowtime.smooth((to - t) / out);
		return Math.min(a, b);
	}

	private static float elastic(float k) {
		if (k <= 0f || k >= 1f) {
			return Mth.clamp(k, 0f, 1f);
		}
		return (float) (Math.pow(2, -9 * k) * Math.sin((k - 0.1) * 5 * Math.PI) + 1);
	}

	private static float hash(int seed) {
		int x = seed * 0x27d4eb2d;
		x ^= x >>> 15;
		x *= 0x2c1b3c6d;
		x ^= x >>> 12;
		return (x & 0xFFFFFF) / (float) 0x1000000;
	}

	private static int rainbow(float k) {
		return BattleShowtime.rainbow(k);
	}

	private static int mix(int a, int b, float k) {
		return BattleShowtime.mix(a, b, Mth.clamp(k, 0f, 1f));
	}

	/** An icosahedron split {@code depth} times, on the unit sphere: its triangles. */
	private static List<Vec3[]> icosphere(int depth) {
		double p = (1 + Math.sqrt(5)) / 2;
		Vec3[] v = { new Vec3(-1, p, 0), new Vec3(1, p, 0), new Vec3(-1, -p, 0), new Vec3(1, -p, 0), new Vec3(0, -1, p), new Vec3(0, 1, p),
				new Vec3(0, -1, -p), new Vec3(0, 1, -p), new Vec3(p, 0, -1), new Vec3(p, 0, 1), new Vec3(-p, 0, -1), new Vec3(-p, 0, 1) };
		int[][] f = { { 0, 11, 5 }, { 0, 5, 1 }, { 0, 1, 7 }, { 0, 7, 10 }, { 0, 10, 11 }, { 1, 5, 9 }, { 5, 11, 4 }, { 11, 10, 2 },
				{ 10, 7, 6 }, { 7, 1, 8 }, { 3, 9, 4 }, { 3, 4, 2 }, { 3, 2, 6 }, { 3, 6, 8 }, { 3, 8, 9 }, { 4, 9, 5 }, { 2, 4, 11 },
				{ 6, 2, 10 }, { 8, 6, 7 }, { 9, 8, 1 } };
		List<Vec3[]> faces = new ArrayList<>();
		for (int[] face : f) {
			faces.add(new Vec3[] { v[face[0]].normalize(), v[face[1]].normalize(), v[face[2]].normalize() });
		}
		for (int d = 0; d < depth; d++) {
			List<Vec3[]> next = new ArrayList<>();
			for (Vec3[] t : faces) {
				Vec3 ab = t[0].add(t[1]).normalize();
				Vec3 bc = t[1].add(t[2]).normalize();
				Vec3 ca = t[2].add(t[0]).normalize();
				next.add(new Vec3[] { t[0], ab, ca });
				next.add(new Vec3[] { t[1], bc, ab });
				next.add(new Vec3[] { t[2], ca, bc });
				next.add(new Vec3[] { ab, bc, ca });
			}
			faces = next;
		}
		return faces;
	}

	/**
	 * The buffer for one of Showtime's colour-only render types; under a shader pack, its entity-format sibling, as the
	 * ambiances do (packs draw the lightning shader those use as a bolt).
	 */
	private static VertexConsumer buffer(MultiBufferSource buffers, RenderType type) {
		if (!shadersOn()) {
			return buffers.getBuffer(type);
		}
		RenderType sibling = type == ShowtimeRenderTypes.GLOW ? ShowtimeRenderTypes.GLOW_SHADERS : ShowtimeRenderTypes.TINT_SHADERS;
		return new com.mystaria.showtime.ShaderPackQuads(buffers.getBuffer(sibling), 0xF000F0);
	}

	private static boolean shadersOn() {
		if (!irisChecked) {
			irisChecked = true;
			try {
				Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
				iris = api.getMethod("getInstance").invoke(null);
				irisInUse = api.getMethod("isShaderPackInUse");
			} catch (ReflectiveOperationException | LinkageError ex) {
				irisInUse = null; // no Iris: no shader pack
			}
		}
		try {
			return irisInUse != null && (boolean) irisInUse.invoke(iris);
		} catch (ReflectiveOperationException ex) {
			return false;
		}
	}

	/** The few vertex helpers of the move effects' {@code Draw}, which is private to its package. */
	private static final class Draw3 {

		static Vec3[] basis(Vec3 normal) {
			Vec3 u = normal.cross(UP);
			u = u.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : u.normalize();
			return new Vec3[] { u, normal.cross(u).normalize() };
		}

		static Vec3 unit(double theta, double phi) {
			return new Vec3(Math.sin(theta) * Math.cos(phi), Math.cos(theta), Math.sin(theta) * Math.sin(phi));
		}

		static void vertex(VertexConsumer out, Matrix4f m, Vec3 at, int color, int alpha) {
			out.addVertex(m, (float) at.x, (float) at.y, (float) at.z)
					.setColor((color >> 16) & 255, (color >> 8) & 255, color & 255, Mth.clamp(alpha, 0, 255));
		}
	}
}
