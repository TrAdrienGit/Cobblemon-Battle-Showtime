package com.mystaria.spectacle.move;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The shapes moves are made of (M2, Adrien 2026-11-01: physical moves are blows up close, special ones travel; each move
 * its own look). A move stacks a few of them ({@link MoveSpec.Layer}), each in one phase; {@code k} runs 0 → 1 over the
 * layer. Glowing shapes are additive light; rocks, hammers, swords, coins and ice are real blocks and items.
 *
 * <p><b>Blows</b> (on the target): {@code cross_slash}, {@code claw}, {@code slash}, {@code sword}, {@code barrage},
 * {@code fist}, {@code kick}, {@code bite}, {@code hammer}, {@code tail}, {@code whip}, {@code stab}, {@code wings},
 * {@code impact}, {@code knock_item}, {@code shadow_hand}. <b>On the user</b>: {@code engulf} (an element wraps the
 * user), {@code spin}, {@code dash} (afterimages rushing to the target), {@code moon}, {@code solar_charge},
 * {@code gather}. <b>Travelling</b>: {@code orb}, {@code orb_rings}, {@code orb_leaves}, {@code homing}, {@code lob},
 * {@code beam}, {@code beam_rings}, {@code ice_beam}, {@code stream_fire}, {@code jets_water}, {@code bolt},
 * {@code psy_shards}, {@code dark_rings}, {@code crescents}, {@code sound_rings}, {@code heat_wave}, {@code wave},
 * {@code coins}. <b>From above or below</b>: {@code sky_bolt}, {@code meteors}, {@code fall_rocks}, {@code rise_rocks},
 * {@code quake}, {@code pillars}, {@code cracks}, {@code tornado}. <b>On the target</b>: {@code star_fire},
 * {@code freeze}, {@code lift}, {@code splash}, {@code splat}, {@code sparks_on}, {@code flames_on}, {@code rays},
 * {@code burst}. <b>Self and status</b>: {@code swords}, {@code dance}, {@code aura}, {@code shell}, {@code heal},
 * {@code dome}, {@code wall}, {@code rocks_float}, {@code caltrops}, {@code web}, {@code wisps}, {@code cloud},
 * {@code seeds}, {@code notes}, {@code glare}, {@code taunt}.
 */
final class Motifs {

	private Motifs() {
	}

	/** One layer this frame (or tick: {@code pose} null): its time {@code t} in ticks and progress {@code k}. */
	record Context(LayeredMove move, MoveSpec.Layer layer, PoseStack pose, MultiBufferSource buffers, Vec3 camera,
			float t, float k, int length) {

		int color() {
			return MoveSpec.color(layer.color, move.color);
		}

		int accent() {
			return MoveSpec.color(layer.accent, move.accent);
		}

		float size() {
			return move.size * layer.size;
		}

		int count(int fallback) {
			return layer.count > 0 ? layer.count : fallback;
		}

		VertexConsumer glow() {
			return buffers.getBuffer(Draw.GLOW);
		}

		Matrix4f m() {
			return pose.last().pose();
		}

		/** Fades in over the first {@code in} and out over the last {@code out} of the layer. */
		float fade(float in, float out) {
			return Mth.clamp(Math.min(k / Math.max(1.0E-3f, in), (1 - k) / Math.max(1.0E-3f, out)), 0f, 1f);
		}

		Vec3 aim() {
			return move.aim();
		}

		Vec3 mouth() {
			return move.mouth();
		}

		Vec3 userCentre() {
			return MoveEffect.bodyCentre(move.user());
		}

		Vec3 toTarget() {
			Vec3 d = aim().subtract(userCentre());
			return d.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : d.normalize();
		}

		/** Axes facing the camera at {@code at}: {@code [u, v]}, v roughly up. */
		Vec3[] screen(Vec3 at) {
			return Draw.basis(camera.subtract(at).normalize());
		}

		RandomSource random() {
			return move.random;
		}
	}

	// =====================================================================
	// Ticks: particles
	// =====================================================================

	static void tick(Context c, ClientLevel level) {
		boolean first = c.t() < 1f;
		RandomSource random = c.random();
		float s = c.size();
		switch (c.layer().motif) {
			case "engulf", "aura", "dance", "heal", "spin" -> {
				Entity user = c.move().user();
				ParticleOptions p = particle(c, 0);
				if (p != null) {
					for (int i = 0; i < 2; i++) {
						double angle = random.nextDouble() * Math.PI * 2;
						double r = user.getBbWidth() * 0.6 + 0.2;
						Vec3 at = user.position().add(Math.cos(angle) * r, random.nextDouble() * user.getBbHeight(), Math.sin(angle) * r);
						level.addParticle(p, at.x, at.y, at.z, 0, c.layer().motif.equals("heal") ? 0.08 : 0.03, 0);
					}
				}
			}
			case "stream_fire", "jets_water", "heat_wave" -> {
				ParticleOptions p = particle(c, 0);
				if (p == null) {
					p = c.layer().motif.equals("jets_water") ? ParticleTypes.SPLASH : ParticleTypes.FLAME;
				}
				Vec3 from = c.mouth();
				Vec3 dir = c.aim().subtract(from);
				for (int i = 0; i < 6; i++) {
					Vec3 v = dir.scale(0.12 + random.nextDouble() * 0.06).add(random.nextGaussian() * 0.04 * s, random.nextGaussian() * 0.04 * s, random.nextGaussian() * 0.04 * s);
					level.addParticle(p, from.x, from.y, from.z, v.x, v.y, v.z);
				}
			}
			case "tornado" -> {
				Vec3 base = c.move().aimFeet();
				ParticleOptions p = particle(c, 0);
				if (p == null) {
					p = ParticleTypes.CLOUD;
				}
				for (int i = 0; i < 3; i++) {
					double h = random.nextDouble() * 3 * s;
					double angle = c.t() * 0.6 + random.nextDouble() * 6.28;
					double r = (0.4 + h * 0.35) * s;
					level.addParticle(p, base.x + Math.cos(angle) * r, base.y + h, base.z + Math.sin(angle) * r, -Math.sin(angle) * 0.2, 0.05, Math.cos(angle) * 0.2);
				}
			}
			case "lob", "orb", "orb_rings", "orb_leaves", "homing", "psy_shards", "coins", "bolt" -> {
				ParticleOptions p = particle(c, 0);
				if (p != null) {
					Vec3 at = c.mouth().lerp(c.aim(), c.k());
					level.addParticle(p, at.x, at.y, at.z, 0, 0.01, 0);
				}
			}
			default -> {
				if (first) {
					burstParticles(c, level, spot(c));
				}
			}
		}
		if (first && (c.layer().motif.equals("quake") || c.layer().motif.equals("rise_rocks") || c.layer().motif.equals("pillars")
				|| c.layer().motif.equals("hammer") || c.layer().motif.equals("fall_rocks"))) {
			dust(level, c.move().aimFeet(), 40, s);
		}
	}

	/** Where a layer's particles burst: on the user for the user's motifs, else on the target. */
	private static Vec3 spot(Context c) {
		return switch (c.layer().motif) {
			case "swords", "shell", "dome", "wall", "moon", "solar_charge", "gather" -> c.userCentre();
			default -> c.aim();
		};
	}

	private static void burstParticles(Context c, ClientLevel level, Vec3 at) {
		RandomSource random = c.random();
		for (int i = 0; i < c.layer().particles.size(); i++) {
			ParticleOptions p = particle(c, i);
			if (p == null) {
				continue;
			}
			for (int n = 0; n < 16; n++) {
				Vec3 v = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize()
						.scale(0.08 + random.nextFloat() * 0.25 * c.size());
				level.addParticle(p, at.x, at.y, at.z, v.x, v.y, v.z);
			}
		}
	}

	private static void dust(ClientLevel level, Vec3 feet, int count, float size) {
		BlockState ground = level.getBlockState(BlockPos.containing(feet).below());
		if (ground.isAir()) {
			return;
		}
		RandomSource random = level.random;
		for (int i = 0; i < count; i++) {
			level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), feet.x + random.nextGaussian() * 0.6 * size,
					feet.y + 0.1, feet.z + random.nextGaussian() * 0.6 * size, random.nextGaussian() * 0.15,
					0.2 + random.nextFloat() * 0.4, random.nextGaussian() * 0.15);
		}
	}

	private static ParticleOptions particle(Context c, int index) {
		if (c.layer().particles.size() <= index) {
			return null;
		}
		ResourceLocation location = ResourceLocation.tryParse(c.layer().particles.get(index));
		return location != null && BuiltInRegistries.PARTICLE_TYPE.get(location) instanceof SimpleParticleType simple ? simple : null;
	}

	// =====================================================================
	// Drawing
	// =====================================================================

	static void render(Context c) {
		switch (c.layer().motif) {
			case "cross_slash" -> crossSlash(c);
			case "claw" -> claw(c);
			case "slash" -> slash(c);
			case "sword" -> sword(c);
			case "barrage" -> barrage(c);
			case "fist" -> fist(c, c.aim(), c.k(), 1f);
			case "kick" -> kick(c);
			case "bite" -> bite(c);
			case "hammer" -> hammer(c);
			case "tail" -> tail(c);
			case "whip" -> whip(c);
			case "stab" -> stab(c);
			case "wings" -> wings(c);
			case "impact" -> impact(c, c.aim(), c.k(), 1f);
			case "knock_item" -> knockItem(c);
			case "shadow_hand" -> shadowHand(c);
			case "engulf" -> engulf(c);
			case "spin" -> spin(c);
			case "dash" -> dash(c);
			case "moon" -> moon(c);
			case "solar_charge" -> solarCharge(c);
			case "gather" -> gather(c);
			case "orb" -> orb(c, false);
			case "orb_rings" -> orbRings(c);
			case "orb_leaves" -> orbLeaves(c);
			case "homing" -> homing(c);
			case "lob" -> lob(c);
			case "beam" -> beam(c, c.mouth(), c.aim(), 1f);
			case "beam_rings" -> beamRings(c);
			case "ice_beam" -> iceBeam(c);
			case "stream_fire" -> stream(c);
			case "jets_water" -> jets(c);
			case "bolt" -> bolt(c.glow(), c.m(), c.camera(), c.mouth(), c.mouth().lerp(c.aim(), Math.min(1f, c.k() * 2)), c.t(), 0.07f * c.size(), c.color(), c.layer().motif.hashCode());
			case "psy_shards" -> psyShards(c);
			case "dark_rings" -> darkRings(c);
			case "crescents" -> crescents(c);
			case "sound_rings" -> soundRings(c);
			case "heat_wave" -> heatWave(c);
			case "wave" -> wave(c);
			case "coins" -> coins(c);
			case "sky_bolt" -> skyBolt(c);
			case "meteors" -> meteors(c);
			case "fall_rocks" -> fallRocks(c);
			case "rise_rocks" -> riseRocks(c);
			case "quake" -> quake(c);
			case "pillars" -> pillars(c);
			case "cracks" -> cracks(c);
			case "tornado" -> tornado(c);
			case "star_fire" -> starFire(c);
			case "freeze" -> freeze(c);
			case "lift" -> lift(c);
			case "splash" -> splash(c);
			case "splat" -> splat(c);
			case "sparks_on" -> sparksOn(c);
			case "flames_on" -> flamesOn(c);
			case "rays" -> rays(c, c.layer().phase.equals("self") || c.layer().phase.equals("wind") ? c.userCentre() : c.aim());
			case "burst" -> burst(c, c.aim(), c.k(), 1f);
			case "swords" -> swords(c);
			case "dance" -> dance(c);
			case "aura" -> aura(c);
			case "shell" -> shell(c);
			case "heal" -> heal(c);
			case "dome" -> dome(c);
			case "wall" -> wall(c);
			case "rocks_float" -> rocksFloat(c);
			case "caltrops" -> caltrops(c);
			case "web" -> web(c);
			case "wisps" -> wisps(c);
			case "cloud" -> cloud(c);
			case "seeds" -> seeds(c);
			case "notes" -> notes(c);
			case "glare" -> glare(c);
			case "taunt" -> taunt(c);
			default -> burst(c, c.aim(), c.k(), 1f);
		}
	}

	// ---------------------------------------------------------------- blows

	/** X-Scissor, Cross Chop: two blades cross on the target, one after the other. */
	private static void crossSlash(Context c) {
		Vec3 at = c.aim();
		Vec3[] b = c.screen(at);
		float s = c.size() * Math.max(0.8f, c.move().targetHeight() * 0.7f);
		float fade = 1 - Mth.clamp((c.k() - 0.5f) * 2, 0, 1);
		for (int i = 0; i < 2; i++) {
			float p = Mth.clamp(c.k() * 3.5f - i * 0.6f, 0, 1);
			Vec3 u = b[0].scale(i == 0 ? 1 : -1).add(b[1]).normalize();
			Vec3 from = at.subtract(u.scale(s));
			Vec3 to = at.add(u.scale(s));
			Vec3 head = from.lerp(to, p);
			Draw.streak(c.glow(), c.m(), head, from, c.camera(), 0.16f * s, c.color(), (int) (200 * fade));
			Draw.streak(c.glow(), c.m(), head, from, c.camera(), 0.06f * s, 0xFFFFFF, (int) (255 * fade));
		}
		if (c.k() > 0.3f) {
			Draw.halo(c.glow(), c.m(), at, c.camera(), 0, s * (0.3f + c.k()), c.accent(), (int) (200 * fade));
		}
	}

	/** Claws: three parallel rakes swept down across the target. */
	private static void claw(Context c) {
		Vec3 at = c.aim();
		Vec3[] b = c.screen(at);
		float s = c.size() * Math.max(0.8f, c.move().targetHeight() * 0.6f);
		float p = Mth.clamp(c.k() * 3f, 0, 1);
		float fade = 1 - Mth.clamp((c.k() - 0.4f) / 0.6f, 0, 1);
		Vec3 dir = b[0].scale(0.5).subtract(b[1]).normalize();
		Vec3 side = b[0].add(b[1].scale(0.5)).normalize();
		for (int i = -1; i <= 1; i++) {
			Vec3 from = at.subtract(dir.scale(s)).add(side.scale(i * 0.28 * s));
			Vec3 head = from.add(dir.scale(2 * s * p));
			Draw.streak(c.glow(), c.m(), head, from, c.camera(), 0.08f * s, c.color(), (int) (230 * fade));
			Draw.streak(c.glow(), c.m(), head, from, c.camera(), 0.03f * s, 0xFFFFFF, (int) (255 * fade));
		}
	}

	/** A single wide crescent swept across the target (Leaf Blade, Night Slash, Razor Shell…). */
	private static void slash(Context c) {
		Vec3 at = c.aim();
		Vec3[] b = c.screen(at);
		float s = c.size() * Math.max(0.9f, c.move().targetHeight() * 0.8f);
		float fade = 1 - Mth.clamp((c.k() - 0.45f) / 0.55f, 0, 1);
		Vec3 u = b[0].add(b[1].scale(0.6)).normalize();
		Vec3 v = b[1].subtract(b[0].scale(0.6)).normalize();
		Draw.arc(c.glow(), c.m(), at, u, v, s, -2.2f, 1.0f, c.k() * 3f, 0.28f * s, c.color(), (int) (230 * fade));
		Draw.arc(c.glow(), c.m(), at, u, v, s * 0.97f, -2.2f, 1.0f, c.k() * 3f, 0.1f * s, 0xFFFFFF, (int) (255 * fade));
	}

	/** A real sword (an item, default an iron sword) cutting down across the target, leaving its trail. */
	private static void sword(Context c) {
		Vec3 at = c.aim();
		Vec3[] b = c.screen(at);
		float s = c.size() * Math.max(1f, c.move().targetHeight() * 0.8f);
		float swing = Mth.clamp(c.k() * 2.5f, 0, 1);
		float angle = -2.4f + 3.2f * (swing * swing * (3 - 2 * swing));
		float fade = 1 - Mth.clamp((c.k() - 0.5f) * 2, 0, 1);
		Draw.arc(c.glow(), c.m(), at, b[0], b[1], s * 1.1f, -2.4f, 0.8f, swing, 0.35f * s, c.color(), (int) (220 * fade));
		Vec3 hilt = at.add(b[0].scale(Math.cos(angle) * s * 0.2)).add(b[1].scale(Math.sin(angle) * s * 0.2));
		float yaw = (float) Math.toDegrees(Math.atan2(c.camera().x - at.x, c.camera().z - at.z));
		item(c, stack(c, Items.IRON_SWORD), hilt.add(b[0].scale(Math.cos(angle) * s * 0.6)).add(b[1].scale(Math.sin(angle) * s * 0.6)),
				1.6f * s, yaw, 0, (float) Math.toDegrees(angle) - 45);
	}

	/** A flurry of fists all over the target (Close Combat, Surging Strikes). */
	private static void barrage(Context c) {
		int blows = c.count(6);
		RandomSource spread = RandomSource.create(c.move().move.hashCode());
		Vec3 at = c.aim();
		Vec3[] b = c.screen(at);
		float r = c.move().targetWidth() * 0.5f + 0.2f;
		for (int i = 0; i < blows; i++) {
			float local = c.k() * (blows + 2) - i;
			if (local < 0 || local > 2.5f) {
				continue;
			}
			Vec3 spot = at.add(b[0].scale(spread.nextGaussian() * r)).add(b[1].scale(spread.nextGaussian() * r));
			fist(c, spot, local / 2.5f, 0.7f);
		}
	}

	/** One punch: a fist of light thrust in, a star, a shockwave. */
	private static void fist(Context c, Vec3 at, float k, float scale) {
		float s = c.size() * scale;
		Vec3 dir = c.toTarget();
		float thrust = Mth.clamp(k * 4, 0, 1);
		float fade = 1 - Mth.clamp((k - 0.3f) / 0.7f, 0, 1);
		Vec3 fistAt = at.subtract(dir.scale(0.9 * s * (1 - thrust)));
		Draw.halo(c.glow(), c.m(), fistAt, c.camera(), 0, 0.42f * s, c.color(), (int) (230 * fade));
		Vec3[] b = c.screen(fistAt);
		for (int i = -1; i <= 2; i++) {
			Draw.halo(c.glow(), c.m(), fistAt.add(b[0].scale(i * 0.14 * s - 0.07 * s)).add(b[1].scale(0.16 * s)), c.camera(), 0, 0.12f * s, c.accent(), (int) (230 * fade));
		}
		Draw.streak(c.glow(), c.m(), fistAt, fistAt.subtract(dir.scale(1.0 * s)), c.camera(), 0.22f * s, c.color(), (int) (150 * fade));
		if (thrust >= 1) {
			impact(c, at, (k - 0.25f) / 0.75f, scale);
		}
	}

	/** A kick: a low curved sweep with the foot's glow at its head (Low Kick, Triple Axel). */
	private static void kick(Context c) {
		int kicks = c.count(1);
		Vec3 at = c.aim().subtract(0, c.move().targetHeight() * 0.2, 0);
		Vec3[] b = c.screen(at);
		float s = c.size() * Math.max(0.9f, c.move().targetHeight() * 0.7f);
		for (int i = 0; i < kicks; i++) {
			float local = Mth.clamp(c.k() * (kicks + 1) - i, 0, 1);
			if (local <= 0 || local >= 1) {
				continue;
			}
			float fade = 1 - local;
			float tilt = i % 2 == 0 ? 1 : -1;
			Vec3 u = b[0].scale(tilt);
			Draw.arc(c.glow(), c.m(), at, u, b[1], s, -2.8f, -0.3f, local * 2f, 0.3f * s, c.color(), (int) (220 * fade));
			float angle = -2.8f + 2.5f * Math.min(1, local * 2);
			Vec3 foot = at.add(u.scale(Math.cos(angle) * s)).add(b[1].scale(Math.sin(angle) * s));
			Draw.halo(c.glow(), c.m(), foot, c.camera(), 0, 0.35f * s, c.accent(), (int) (230 * fade));
			if (local > 0.5f) {
				impact(c, at, (local - 0.5f) * 2, 0.7f);
			}
		}
	}

	/** Jaws: an upper and a lower row of fangs snapping shut on the target. */
	private static void bite(Context c) {
		Vec3 at = c.aim();
		Vec3[] b = c.screen(at);
		float s = c.size() * Math.max(0.9f, c.move().targetHeight() * 0.7f);
		float close = Mth.clamp(c.k() * 3f, 0, 1);
		close = close * close;
		float fade = 1 - Mth.clamp((c.k() - 0.45f) / 0.55f, 0, 1);
		for (int jaw = -1; jaw <= 1; jaw += 2) {
			Vec3 gum = at.add(b[1].scale(jaw * s * (1.1 - 0.85 * close)));
			Draw.arc(c.glow(), c.m(), gum.add(b[1].scale(jaw * s * 0.6)), b[0], b[1].scale(-jaw), s * 0.7f, 0.5f, 2.64f, 1f, 0.12f * s, c.color(), (int) (200 * fade));
			for (int f = -2; f <= 2; f++) {
				Vec3 root = gum.add(b[0].scale(f * 0.26 * s));
				Vec3 tip = root.add(b[1].scale(-jaw * (f == -2 || f == 2 ? 0.5 : 0.32) * s));
				Draw.streak(c.glow(), c.m(), root, tip, c.camera(), 0.08f * s, 0xFFFFFF, (int) (255 * fade));
			}
		}
		if (close >= 1) {
			impact(c, at, (c.k() - 0.33f) * 1.5f, 0.8f);
		}
	}

	/** A hammer (a block: a log for Wood Hammer, iron for Gigaton Hammer) raised high and brought down on the target. */
	private static void hammer(Context c) {
		Vec3 feet = c.move().aimFeet();
		float s = c.size() * Math.max(1f, c.move().targetHeight() * 0.7f);
		BlockState head = block(c, Blocks.OAK_LOG.defaultBlockState());
		float swing = Mth.clamp(c.k() * 2.2f, 0, 1);
		float angle = 100 * (1 - swing * swing);
		Vec3 pivot = feet.add(c.toTarget().scale(-1.6 * s)).add(0, 0.2, 0);
		float yaw = (float) Math.toDegrees(Math.atan2(c.toTarget().x, c.toTarget().z));
		PoseStack pose = c.pose();
		pose.pushPose();
		pose.translate(pivot.x, pivot.y, pivot.z);
		pose.mulPose(Axis.YP.rotationDegrees(yaw));
		pose.mulPose(Axis.XP.rotationDegrees(-angle));
		// The handle, then the head at its end.
		pose.pushPose();
		pose.translate(0, 0, 1.0 * s);
		pose.scale(0.18f * s, 0.18f * s, 2.0f * s);
		pose.translate(-0.5, -0.5, -0.5);
		Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), pose, c.buffers(), 0xF000F0, OverlayTexture.NO_OVERLAY);
		pose.popPose();
		pose.translate(0, 0, 2.0 * s);
		pose.scale(1.1f * s, 0.8f * s, 0.8f * s);
		pose.translate(-0.5, -0.5, -0.5);
		Minecraft.getInstance().getBlockRenderer().renderSingleBlock(head, pose, c.buffers(), 0xF000F0, OverlayTexture.NO_OVERLAY);
		pose.popPose();
		if (swing >= 1) {
			float k = (c.k() - 0.45f) / 0.55f;
			Draw.ring(c.glow(), c.m(), feet.add(0, 0.06, 0), new Vec3(0, 1, 0), s * (0.5f + 2.5f * k), 0.35f * s, c.color(), (int) (220 * (1 - k)));
			impact(c, c.aim(), k, 1.2f);
		}
	}

	/** A tail sweeping wide and low across the target (Dragon Tail, Iron Tail). */
	private static void tail(Context c) {
		Vec3 at = c.aim().subtract(0, c.move().targetHeight() * 0.15, 0);
		float s = c.size() * Math.max(1.2f, c.move().targetWidth() + 0.6f);
		Vec3 dir = c.toTarget();
		Vec3 side = dir.cross(new Vec3(0, 1, 0)).normalize();
		Vec3 centre = at.subtract(dir.scale(s * 0.6));
		float fade = 1 - Mth.clamp((c.k() - 0.5f) * 2, 0, 1);
		Draw.arc(c.glow(), c.m(), centre, side, dir.add(0, 0.25, 0).normalize(), s, 0.2f, 2.9f, c.k() * 2.4f, 0.55f * s, c.color(), (int) (210 * fade));
		Draw.arc(c.glow(), c.m(), centre, side, dir.add(0, 0.25, 0).normalize(), s * 0.98f, 0.2f, 2.9f, c.k() * 2.4f, 0.18f * s, c.accent(), (int) (240 * fade));
		if (c.k() > 0.35f) {
			impact(c, at, (c.k() - 0.35f) / 0.65f, 1f);
		}
	}

	/** A vine or whip lashing in a wave onto the target (Power Whip). */
	private static void whip(Context c) {
		Vec3 from = c.userCentre();
		Vec3 to = c.aim();
		Vec3 axis = to.subtract(from);
		Vec3 side = axis.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
		float reach = Mth.clamp(c.k() * 2.5f, 0, 1);
		float fade = 1 - Mth.clamp((c.k() - 0.5f) * 2, 0, 1);
		Vec3 last = from;
		for (int i = 1; i <= 20; i++) {
			double p = i / 20.0 * reach;
			Vec3 at = from.add(axis.scale(p)).add(side.scale(Math.sin(p * 9 - c.t() * 1.2) * 0.4 * c.size() * (1 - p)))
					.add(0, Math.sin(p * Math.PI) * 0.8 * c.size(), 0);
			Draw.streak(c.glow(), c.m(), at, last, c.camera(), 0.09f * c.size(), c.color(), (int) (230 * fade));
			last = at;
		}
		if (reach >= 1) {
			impact(c, to, (c.k() - 0.4f) / 0.6f, 0.9f);
		}
	}

	/** A thrust: a spear of light driven through the target (Poison Jab, Liquidation, Megahorn). */
	private static void stab(Context c) {
		Vec3 dir = c.toTarget();
		Vec3 at = c.aim();
		float s = c.size();
		float thrust = Mth.clamp(c.k() * 3.5f, 0, 1);
		float fade = 1 - Mth.clamp((c.k() - 0.4f) / 0.6f, 0, 1);
		Vec3 tip = at.add(dir.scale((thrust - 0.4) * 1.2 * s));
		Draw.streak(c.glow(), c.m(), tip, tip.subtract(dir.scale(2.2 * s)), c.camera(), 0.18f * s, c.color(), (int) (220 * fade));
		Draw.streak(c.glow(), c.m(), tip, tip.subtract(dir.scale(1.6 * s)), c.camera(), 0.06f * s, 0xFFFFFF, (int) (255 * fade));
		if (thrust >= 1) {
			Draw.ring(c.glow(), c.m(), at, dir, s * (0.3f + 1.4f * c.k()), 0.15f * s, c.accent(), (int) (200 * fade));
		}
	}

	/** Wings beating down on the target, one on each side (Dual Wingbeat, Acrobatics, Brave Bird). */
	private static void wings(Context c) {
		Vec3 at = c.aim();
		Vec3[] b = c.screen(at);
		float s = c.size() * Math.max(1f, c.move().targetHeight() * 0.8f);
		float beat = Mth.clamp(c.k() * 2.5f, 0, 1);
		float fade = 1 - Mth.clamp((c.k() - 0.5f) * 2, 0, 1);
		for (int side = -1; side <= 1; side += 2) {
			Vec3 u = b[0].scale(side);
			float open = 1.6f - 1.4f * beat;
			for (int f = 0; f < 5; f++) {
				float angle = open - f * 0.22f;
				Vec3 root = at.add(u.scale(0.25 * s)).add(b[1].scale(0.3 * s));
				Vec3 tip = root.add(u.scale(Math.cos(angle) * (1.4 - f * 0.12) * s)).add(b[1].scale(Math.sin(angle) * (1.4 - f * 0.12) * s));
				Draw.streak(c.glow(), c.m(), tip, root, c.camera(), 0.13f * s, f == 0 ? 0xFFFFFF : c.color(), (int) (220 * fade));
			}
		}
		if (beat >= 1) {
			impact(c, at, (c.k() - 0.4f) / 0.6f, 0.8f);
		}
	}

	/** The common blow: a white flash, a star of rays, a ring facing the camera. */
	private static void impact(Context c, Vec3 at, float k, float scale) {
		k = Mth.clamp(k, 0, 1);
		float fade = 1 - k;
		float s = c.size() * scale;
		Vec3[] b = c.screen(at);
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, s * 0.55f * fade, 0xFFFFFF, (int) (230 * fade));
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, s * (0.5f + k), c.color(), (int) (170 * fade * fade));
		for (int i = 0; i < 6; i++) {
			double angle = Math.PI * 2 * i / 6 + 0.4;
			Vec3 d = b[0].scale(Math.cos(angle)).add(b[1].scale(Math.sin(angle)));
			Draw.streak(c.glow(), c.m(), at.add(d.scale(s * (0.2 + 0.8 * k))), at.add(d.scale(s * (0.6 + 1.2 * k))), c.camera(), 0.06f * s, c.accent(), (int) (220 * fade));
		}
		Draw.ring(c.glow(), c.m(), at, c.camera().subtract(at).normalize(), s * (0.3f + 1.3f * k), 0.12f * s, c.accent(), (int) (180 * fade));
	}

	/** Knock Off: a dark swipe, and an item knocked out of the target's grip, spinning away. */
	private static void knockItem(Context c) {
		slash(c);
		Vec3 at = c.aim();
		Vec3 away = c.toTarget().add(0, 0.6, 0).normalize();
		float fly = Mth.clamp((c.k() - 0.2f) / 0.8f, 0, 1);
		Vec3 pos = at.add(away.scale(fly * 3 * c.size())).add(0, -fly * fly * 2, 0);
		net.minecraft.world.item.Item kind = ITEMS_KNOCKED[Math.floorMod(c.move().move.hashCode() + (int) c.move().aim().x, ITEMS_KNOCKED.length)];
		item(c, new ItemStack(kind), pos, 0.8f * c.size(), c.t() * 30, c.t() * 25, 0);
	}

	private static final net.minecraft.world.item.Item[] ITEMS_KNOCKED = { Items.APPLE, Items.GOLDEN_APPLE, Items.ENDER_PEARL, Items.EMERALD, Items.AMETHYST_SHARD };

	/** A shadow stretching from the user to under the target, a claw of darkness rising out of it (Shadow Sneak). */
	private static void shadowHand(Context c) {
		Vec3 from = c.move().user().position().add(0, 0.05, 0);
		Vec3 to = c.move().aimFeet().add(0, 0.05, 0);
		float reach = Mth.clamp(c.k() * 2.5f, 0, 1);
		float fade = 1 - Mth.clamp((c.k() - 0.6f) / 0.4f, 0, 1);
		Draw.streak(c.glow(), c.m(), from.lerp(to, reach), from, c.camera(), 0.35f * c.size(), c.color(), (int) (180 * fade));
		if (reach >= 1) {
			float rise = Mth.clamp((c.k() - 0.4f) * 3, 0, 1);
			for (int f = -2; f <= 2; f++) {
				Vec3 root = to.add(c.toTarget().cross(new Vec3(0, 1, 0)).normalize().scale(f * 0.2 * c.size()));
				Draw.streak(c.glow(), c.m(), root.add(0, rise * (1.4 - Math.abs(f) * 0.2) * c.size(), 0), root, c.camera(), 0.1f * c.size(), c.accent(), (int) (230 * fade));
			}
		}
	}

	// ---------------------------------------------------------------- on the user

	/** The user wrapped in its element (fire for Flare Blitz, water for Wave Crash…): a glow and licking tongues. */
	private static void engulf(Context c) {
		Entity user = c.move().user();
		Vec3 centre = c.userCentre();
		float fade = c.fade(0.15f, 0.2f);
		float r = Math.max(user.getBbWidth(), user.getBbHeight()) * 0.65f + 0.2f;
		Draw.halo(c.glow(), c.m(), centre, c.camera(), r * 0.4f, r * 1.5f, c.color(), (int) (190 * fade));
		for (int i = 0; i < 10; i++) {
			double angle = i * 0.63 + c.t() * 0.3;
			double phase = (c.t() * 0.08 + i * 0.37) % 1;
			Vec3 base = user.position().add(Math.cos(angle) * r * 0.7, phase * user.getBbHeight() * 1.2, Math.sin(angle) * r * 0.7);
			Draw.streak(c.glow(), c.m(), base.add(0, 0.5 * c.size(), 0), base, c.camera(), 0.12f * c.size(), c.accent(), (int) (200 * fade * Math.sin(phase * Math.PI)));
		}
	}

	/** Spinning rings around the user (Rapid Spin, Ice Spinner, Mortal Spin). */
	private static void spin(Context c) {
		Entity user = c.move().user();
		float fade = c.fade(0.15f, 0.25f);
		float r = user.getBbWidth() * 0.8f + 0.3f * c.size();
		for (int i = 0; i < 3; i++) {
			Vec3 at = user.position().add(0, user.getBbHeight() * (0.25 + i * 0.25), 0);
			Draw.arc(c.glow(), c.m(), at, new Vec3(1, 0, 0), new Vec3(0, 0, 1), r, c.t() * 0.9f + i * 2, c.t() * 0.9f + i * 2 + 4.5f, 1f, 0.15f * c.size(), i == 1 ? c.accent() : c.color(), (int) (220 * fade));
		}
	}

	/** Afterimages: streaks and fading copies of a glow rushing from the user to the target (a charge). */
	private static void dash(Context c) {
		Vec3 from = c.userCentre();
		Vec3 to = c.aim();
		Vec3 dir = to.subtract(from).normalize();
		Vec3 side = dir.cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
		float s = c.size();
		float p = c.k();
		for (int i = 0; i < 4; i++) {
			Vec3 at = from.lerp(to, Math.max(0, p - i * 0.12f));
			Draw.halo(c.glow(), c.m(), at, c.camera(), 0, c.move().user().getBbWidth() * 0.6f + 0.2f, c.color(), 120 - i * 25);
		}
		for (int i = 0; i < 8; i++) {
			Vec3 offset = side.scale((i - 3.5) * 0.15 * s).add(0, Math.sin(i * 1.7) * 0.3 * s, 0);
			Vec3 head = from.lerp(to, p).add(offset);
			Draw.streak(c.glow(), c.m(), head, head.subtract(dir.scale(1.4 * s)), c.camera(), 0.03f * s, c.accent(), 210);
		}
	}

	/** A full moon rising above the user, shining (Moonblast's wind-up). */
	private static void moon(Context c) {
		Vec3 at = c.userCentre().add(0, c.move().user().getBbHeight() * 0.6 + 1.4 * c.size(), 0);
		float fade = c.fade(0.3f, 0.2f);
		float r = 0.55f * c.size();
		Draw.halo(c.glow(), c.m(), at, c.camera(), r, r * 3, c.color(), (int) (150 * fade));
		Draw.sphere(c.glow(), c.m(), at, r, 0xFFF4FA, (int) (200 * fade), true);
		Draw.sphere(c.glow(), c.m(), at, r * 0.98f, c.accent(), (int) (90 * fade), true);
	}

	/** Light gathering from the sky into the user (Solar Beam's charge). */
	private static void solarCharge(Context c) {
		Vec3 at = c.mouth();
		for (int i = 0; i < 6; i++) {
			double angle = i * 1.05 + c.t() * 0.05;
			Vec3 high = at.add(Math.cos(angle) * 2, 5, Math.sin(angle) * 2);
			Draw.streak(c.glow(), c.m(), at.lerp(high, 1 - c.k()), high, c.camera(), 0.1f * c.size(), c.color(), (int) (180 * c.fade(0.2f, 0.1f)));
		}
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, 0.7f * c.size() * c.k(), c.accent(), 200);
	}

	/** Specks spiralling into the user's mouth (a charge before a beam or an orb). */
	private static void gather(Context c) {
		Vec3 at = c.mouth();
		float s = c.size();
		for (int i = 0; i < 10; i++) {
			float phase = (c.t() * 0.06f + i / 10f) % 1f;
			double angle = i * 2.4 + c.t() * 0.3;
			double dist = (0.4 + 2.2 * (1 - phase)) * s;
			Vec3 head = at.add(Math.cos(angle) * dist, Math.sin(angle * 0.7) * dist * 0.5, Math.sin(angle) * dist);
			Draw.streak(c.glow(), c.m(), head, head.add(head.subtract(at).normalize().scale(0.4 * s)), c.camera(), 0.04f * s, c.accent(), (int) (220 * Math.sin(phase * Math.PI)));
		}
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, 0.5f * s * c.k(), c.color(), 200);
	}

	// ---------------------------------------------------------------- travelling

	private static Vec3 along(Context c, float k, float arc) {
		return c.mouth().lerp(c.aim(), k).add(0, Math.sin(k * Math.PI) * arc * c.size(), 0);
	}

	/** A glowing orb flying to the target (in its travel layer) — Moonblast, Energy Ball, Volt Switch. */
	private static void orb(Context c, boolean darkCore) {
		Vec3 at = along(c, c.k(), 0.3f);
		float r = 0.32f * c.size();
		Draw.halo(c.glow(), c.m(), at, c.camera(), r * 0.8f, r * 2.6f, c.color(), 170);
		Draw.sphere(c.glow(), c.m(), at, r, c.accent(), 130, true);
		Draw.sphere(c.glow(), c.m(), at, r * 0.55f, 0xFFFFFF, 180, true);
		for (int i = 1; i <= 4; i++) {
			Draw.halo(c.glow(), c.m(), along(c, Math.max(0, c.k() - i * 0.07f), 0.3f), c.camera(), 0, r * (1.5f - i * 0.2f), c.color(), 100 - i * 20);
		}
	}

	/** An orb wrapped in spinning rings (Focus Blast, Aura Sphere). */
	private static void orbRings(Context c) {
		orb(c, false);
		Vec3 at = along(c, c.k(), 0.3f);
		float r = 0.5f * c.size();
		for (int i = 0; i < 3; i++) {
			double angle = c.t() * 0.5 + i * 2.1;
			Vec3 normal = new Vec3(Math.cos(angle), Math.sin(angle * 1.3), Math.sin(angle)).normalize();
			Draw.ring(c.glow(), c.m(), at, normal, r, 0.06f * c.size(), c.accent(), 220);
		}
	}

	/** A green orb with leaves (real leaf blocks, tiny) whirling around it (Energy Ball, Giga Drain). */
	private static void orbLeaves(Context c) {
		orb(c, false);
		Vec3 at = along(c, c.k(), 0.3f);
		for (int i = 0; i < 6; i++) {
			double angle = c.t() * 0.4 + i * 1.05;
			Vec3 leaf = at.add(Math.cos(angle) * 0.55 * c.size(), Math.sin(angle * 2) * 0.2, Math.sin(angle) * 0.55 * c.size());
			block(c, block(c, Blocks.OAK_LEAVES.defaultBlockState()), leaf, 0.16f * c.size(), c.t() * 20 + i * 40, c.t() * 15, 0);
		}
	}

	/** An orb curving in from the side, as if homing (Aura Sphere). */
	private static void homing(Context c) {
		Vec3 from = c.mouth();
		Vec3 to = c.aim();
		Vec3 side = to.subtract(from).cross(new Vec3(0, 1, 0));
		side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
		float k = c.k();
		Vec3 at = from.lerp(to, k).add(side.scale(Math.sin(k * Math.PI) * 1.5 * c.size()));
		float r = 0.3f * c.size();
		Draw.halo(c.glow(), c.m(), at, c.camera(), r * 0.8f, r * 2.8f, c.color(), 180);
		Draw.sphere(c.glow(), c.m(), at, r, c.accent(), 140, true);
		for (int i = 1; i <= 6; i++) {
			float back = Math.max(0, k - i * 0.05f);
			Vec3 echo = from.lerp(to, back).add(side.scale(Math.sin(back * Math.PI) * 1.5 * c.size()));
			Draw.halo(c.glow(), c.m(), echo, c.camera(), 0, r * (1.6f - i * 0.18f), c.color(), 110 - i * 15);
		}
	}

	/** Blobs lobbed in a high arc (Sludge Bomb, Gunk Shot), each a dark gooey sphere. */
	private static void lob(Context c) {
		int blobs = c.count(3);
		for (int i = 0; i < blobs; i++) {
			float k = Mth.clamp(c.k() * 1.3f - i * 0.12f, 0, 1);
			if (k <= 0 || k >= 1) {
				continue;
			}
			Vec3 at = c.mouth().lerp(c.aim(), k).add(0, Math.sin(k * Math.PI) * 2.2 * c.size(), 0);
			float r = (0.26f - i * 0.04f) * c.size();
			Draw.sphere(c.buffers().getBuffer(Draw.DARK), c.m(), at, r, darken(c.color()), 235, false);
			Draw.halo(c.glow(), c.m(), at, c.camera(), r, r * 2, c.color(), 140);
		}
	}

	/** A straight beam, three layers: a wide soft glow, the colour, a white core. */
	private static void beam(Context c, Vec3 from, Vec3 to, float strength) {
		float k = c.layer().phase.equals("travel") ? Math.min(1f, c.k() * 2) : 1f;
		Vec3 end = from.lerp(to, k);
		int alpha = (int) (220 * strength * (c.layer().phase.equals("hit") ? 1 - c.k() : 1));
		float w = 0.18f * c.size() * (0.85f + 0.15f * Mth.sin(c.t() * 2f));
		Draw.streak(c.glow(), c.m(), end, from, c.camera(), w * 2.4f, c.color(), alpha / 2);
		Draw.streak(c.glow(), c.m(), end, from, c.camera(), w, c.accent(), alpha);
		Draw.streak(c.glow(), c.m(), end, from, c.camera(), w * 0.35f, 0xFFFFFF, alpha);
		Draw.halo(c.glow(), c.m(), end, c.camera(), 0, 0.6f * c.size(), c.color(), alpha);
		Draw.halo(c.glow(), c.m(), from, c.camera(), 0, 0.5f * c.size(), c.accent(), alpha);
	}

	/** A beam with rings rolling along it (Flash Cannon, Psybeam). */
	private static void beamRings(Context c) {
		beam(c, c.mouth(), c.aim(), 0.8f);
		Vec3 from = c.mouth();
		Vec3 axis = c.aim().subtract(from);
		for (int i = 0; i < 6; i++) {
			double p = (i / 6.0 + c.t() * 0.06) % 1.0;
			if (c.layer().phase.equals("travel") && p > c.k() * 2) {
				continue;
			}
			Draw.ring(c.glow(), c.m(), from.add(axis.scale(p)), axis.normalize(), 0.35f * c.size(), 0.06f * c.size(), i % 2 == 0 ? c.color() : c.accent(), 210);
		}
	}

	/** An icy beam that leaves frost crystals (real ice blocks, small) along its path (Ice Beam, Freeze-Dry). */
	private static void iceBeam(Context c) {
		beam(c, c.mouth(), c.aim(), 1f);
		Vec3 from = c.mouth();
		Vec3 axis = c.aim().subtract(from);
		RandomSource jag = RandomSource.create(c.move().move.hashCode());
		for (int i = 1; i < 8; i++) {
			double p = i / 8.0;
			if (c.layer().phase.equals("travel") && p > c.k() * 2) {
				break;
			}
			Vec3 at = from.add(axis.scale(p)).add(jag.nextGaussian() * 0.15, jag.nextGaussian() * 0.15, jag.nextGaussian() * 0.15);
			block(c, Blocks.PACKED_ICE.defaultBlockState(), at, 0.18f * c.size(), jag.nextFloat() * 360, jag.nextFloat() * 360, 45);
		}
	}

	/** A cone of fire from the mouth to the target, swelling and flickering (Flamethrower, Overheat). */
	private static void stream(Context c) {
		Vec3 from = c.mouth();
		Vec3 to = c.aim();
		float reach = c.layer().phase.equals("travel") ? Math.min(1f, c.k() * 1.6f) : 1f;
		Vec3 axis = to.subtract(from).scale(reach);
		int alpha = (int) (220 * (c.layer().phase.equals("hit") ? 1 - c.k() : 1));
		for (int i = 0; i < 14; i++) {
			double p = (i / 14.0 + c.t() * 0.09) % 1.0;
			Vec3 at = from.add(axis.scale(p)).add(Math.sin(c.t() + i) * 0.1 * p, Math.cos(c.t() * 1.3 + i) * 0.1 * p, 0);
			float r = (float) (0.15 + p * 0.55) * c.size();
			Draw.halo(c.glow(), c.m(), at, c.camera(), 0, r, i % 3 == 0 ? c.accent() : c.color(), (int) (alpha * (0.5 + 0.5 * (1 - p))));
		}
		Draw.streak(c.glow(), c.m(), from.add(axis), from, c.camera(), 0.12f * c.size(), 0xFFFFFF, alpha / 2);
	}

	/** Several water jets corkscrewing to the target (Hydro Pump, Scald). */
	private static void jets(Context c) {
		Vec3 from = c.mouth();
		Vec3 to = c.aim();
		float reach = c.layer().phase.equals("travel") ? Math.min(1f, c.k() * 1.6f) : 1f;
		Vec3 axis = to.subtract(from);
		Vec3[] b = Draw.basis(axis.normalize());
		int alpha = (int) (220 * (c.layer().phase.equals("hit") ? 1 - c.k() : 1));
		int jets = c.count(3);
		for (int j = 0; j < jets; j++) {
			Vec3 last = from;
			for (int i = 1; i <= 16; i++) {
				double p = i / 16.0 * reach;
				double angle = p * 10 + c.t() * 0.5 + j * Math.PI * 2 / jets;
				Vec3 at = from.add(axis.scale(p)).add(b[0].scale(Math.cos(angle) * 0.22 * c.size())).add(b[1].scale(Math.sin(angle) * 0.22 * c.size()));
				Draw.streak(c.glow(), c.m(), at, last, c.camera(), 0.12f * c.size(), j == 0 ? c.accent() : c.color(), alpha);
				last = at;
			}
		}
	}

	/** Triangular shards of psychic energy fired in a fan, converging on the target (Psyshock, Stored Power). */
	private static void psyShards(Context c) {
		int shards = c.count(6);
		Vec3 from = c.mouth();
		Vec3 to = c.aim();
		Vec3[] b = Draw.basis(to.subtract(from).normalize());
		for (int i = 0; i < shards; i++) {
			double angle = Math.PI * 2 * i / shards + c.t() * 0.2;
			float p = Mth.clamp(c.k() * 1.2f - i * 0.03f, 0, 1);
			double spread = Math.sin(p * Math.PI) * 1.0 * c.size();
			Vec3 at = from.lerp(to, p).add(b[0].scale(Math.cos(angle) * spread)).add(b[1].scale(Math.sin(angle) * spread));
			Vec3[] sb = c.screen(at);
			Vec3 a = at.add(sb[1].scale(0.25 * c.size()));
			Vec3 l = at.add(sb[0].scale(-0.18 * c.size())).add(sb[1].scale(-0.15 * c.size()));
			Vec3 r = at.add(sb[0].scale(0.18 * c.size())).add(sb[1].scale(-0.15 * c.size()));
			Draw.streak(c.glow(), c.m(), a, l, c.camera(), 0.04f * c.size(), c.accent(), 230);
			Draw.streak(c.glow(), c.m(), l, r, c.camera(), 0.04f * c.size(), c.accent(), 230);
			Draw.streak(c.glow(), c.m(), r, a, c.camera(), 0.04f * c.size(), c.accent(), 230);
			Draw.halo(c.glow(), c.m(), at, c.camera(), 0, 0.25f * c.size(), c.color(), 150);
		}
	}

	/** Dark rings rolling out from the user to the target (Dark Pulse). */
	private static void darkRings(Context c) {
		Vec3 from = c.mouth();
		Vec3 to = c.aim();
		Vec3 axis = to.subtract(from).normalize();
		for (int i = 0; i < 5; i++) {
			float p = c.k() - i * 0.15f;
			if (p <= 0 || p >= 1) {
				continue;
			}
			Vec3 at = from.lerp(to, p);
			float r = (0.3f + p * 0.7f) * c.size();
			Draw.ring(c.glow(), c.m(), at, axis, r, 0.14f * c.size(), i % 2 == 0 ? c.color() : c.accent(), (int) (220 * (1 - p * 0.4f)));
			Draw.ring(c.buffers().getBuffer(Draw.GLOW), c.m(), at, axis, r * 0.8f, 0.05f * c.size(), 0xFFFFFF, 120);
		}
	}

	/** Crescent blades of air spinning to the target (Air Slash). */
	private static void crescents(Context c) {
		int blades = c.count(3);
		Vec3 from = c.mouth();
		Vec3 to = c.aim();
		for (int i = 0; i < blades; i++) {
			float p = Mth.clamp(c.k() * 1.3f - i * 0.12f, 0, 1);
			if (p <= 0 || p >= 1) {
				continue;
			}
			Vec3 at = from.lerp(to, p).add(0, (i - 1) * 0.35 * c.size(), 0);
			Vec3[] b = c.screen(at);
			float spin = c.t() * 0.8f + i;
			Vec3 u = b[0].scale(Math.cos(spin)).add(b[1].scale(Math.sin(spin)));
			Vec3 v = b[1].scale(Math.cos(spin)).subtract(b[0].scale(Math.sin(spin)));
			Draw.arc(c.glow(), c.m(), at, u, v, 0.5f * c.size(), -1.2f, 1.2f, 1f, 0.12f * c.size(), c.color(), 230);
		}
	}

	/** Rings of sound widening from the mouth to the target (Hyper Voice, Boomburst, Bug Buzz). */
	private static void soundRings(Context c) {
		Vec3 from = c.mouth();
		Vec3 to = c.aim();
		Vec3 axis = to.subtract(from).normalize();
		for (int i = 0; i < 5; i++) {
			float p = (c.t() / c.length() * 1.4f - i * 0.18f);
			if (p <= 0 || p >= 1.2f) {
				continue;
			}
			Vec3 at = from.add(axis.scale(p * from.distanceTo(to)));
			Draw.ring(c.glow(), c.m(), at, axis, (0.3f + p * 1.4f) * c.size(), 0.08f * c.size(), i % 2 == 0 ? c.color() : c.accent(), (int) (220 * Mth.clamp(1.2f - p, 0, 1)));
		}
	}

	/** A shimmering wall of heat rolling over the target (Heat Wave). */
	private static void heatWave(Context c) {
		Vec3 from = c.userCentre();
		Vec3 to = c.aim();
		Vec3 dir = to.subtract(from).normalize();
		Vec3 side = dir.cross(new Vec3(0, 1, 0)).normalize();
		Vec3 front = from.lerp(to, Math.min(1.1f, c.k() * 1.2f));
		for (int i = 0; i < 12; i++) {
			double x = (i - 5.5) * 0.35 * c.size();
			Vec3 base = front.add(side.scale(x)).add(0, -0.8, 0);
			Vec3 top = base.add(0, 2 * c.size() + Math.sin(c.t() + i) * 0.3, 0).add(dir.scale(Math.sin(c.t() * 0.6 + i) * 0.3));
			Draw.streak(c.glow(), c.m(), base, top, c.camera(), 0.2f * c.size(), i % 2 == 0 ? c.color() : c.accent(), (int) (130 * c.fade(0.1f, 0.3f)));
		}
	}

	/** A wave rising and rolling from the user over the target (Surf, Sludge Wave). */
	private static void wave(Context c) {
		Vec3 from = c.move().user().position();
		Vec3 to = c.move().aimFeet();
		Vec3 dir = to.subtract(from).multiply(1, 0, 1).normalize();
		Vec3 side = dir.cross(new Vec3(0, 1, 0)).normalize();
		Vec3 crest = from.lerp(to, Math.min(1.15f, c.k() * 1.25f));
		float height = (float) Math.sin(Math.min(1, c.k() * 1.2) * Math.PI * 0.8) * 2.6f * c.size();
		int alpha = (int) (200 * c.fade(0.1f, 0.25f));
		for (int i = 0; i < 14; i++) {
			double x = (i - 6.5) * 0.4 * c.size();
			Vec3 base = crest.add(side.scale(x));
			Vec3 top = base.add(0, height * (0.8 + 0.2 * Math.sin(c.t() * 0.5 + i)), 0).add(dir.scale(0.4 * c.size()));
			Draw.streak(c.glow(), c.m(), top, base.subtract(dir.scale(0.6)), c.camera(), 0.3f * c.size(), c.color(), alpha);
			Draw.halo(c.glow(), c.m(), top, c.camera(), 0, 0.3f * c.size(), c.accent(), alpha);
		}
	}

	/** Coins (gold nuggets) — flung from the user, or raining down on the target (Make It Rain). */
	private static void coins(Context c) {
		int count = c.count(14);
		RandomSource jag = RandomSource.create(c.move().move.hashCode() * 7L);
		boolean rain = c.layer().phase.equals("hit");
		for (int i = 0; i < count; i++) {
			float delay = jag.nextFloat() * 0.5f;
			float p = Mth.clamp((c.k() - delay) / 0.5f, 0, 1);
			if (p <= 0 || p >= 1) {
				continue;
			}
			Vec3 spread = new Vec3(jag.nextGaussian(), 0, jag.nextGaussian()).scale(0.7 * c.size());
			Vec3 at = rain ? c.aim().add(spread).add(0, 4 * (1 - p) - 0.5, 0)
					: c.mouth().lerp(c.aim().add(spread.scale(0.6)), p).add(0, Math.sin(p * Math.PI) * 1.5, 0);
			item(c, new ItemStack(Items.GOLD_NUGGET), at, 0.45f * c.size(), c.t() * 40 + i * 30, 0, c.t() * 20);
		}
		if (rain && c.k() > 0.5f) {
			burst(c, c.aim(), (c.k() - 0.5f) * 2, 0.8f);
		}
	}

	// ---------------------------------------------------------------- above and below

	/** A bolt from the sky onto the target (Thunder), re-struck twice. */
	private static void skyBolt(Context c) {
		Vec3 to = c.aim();
		if (c.k() < 0.55f && (int) (c.t() / 3) % 2 == 0 || c.k() < 0.15f) {
			bolt(c.glow(), c.m(), c.camera(), to.add(0, 12, 0), to, c.t(), 0.14f * c.size(), c.color(), 7);
			Draw.halo(c.glow(), c.m(), to, c.camera(), 0, 1.2f * c.size(), 0xFFFFFF, 220);
		}
		burst(c, to, c.k(), 1.3f);
	}

	/** Meteors streaking down around and onto the target (Draco Meteor, Tera Starstorm). */
	private static void meteors(Context c) {
		int count = c.count(5);
		RandomSource jag = RandomSource.create(c.move().move.hashCode() * 3L);
		Vec3 aim = c.aim();
		for (int i = 0; i < count; i++) {
			float p = Mth.clamp(c.k() * 1.6f - i * 0.12f, 0, 1.4f);
			Vec3 land = aim.add(jag.nextGaussian() * 1.2 * c.size(), -0.3, jag.nextGaussian() * 1.2 * c.size());
			Vec3 high = land.add(-3, 10, -2);
			if (p < 1) {
				Vec3 head = high.lerp(land, p);
				Draw.halo(c.glow(), c.m(), head, c.camera(), 0, 0.45f * c.size(), c.color(), 220);
				Draw.sphere(c.glow(), c.m(), head, 0.2f * c.size(), 0xFFFFFF, 200, true);
				Draw.streak(c.glow(), c.m(), head, head.add(high.subtract(land).normalize().scale(3 * c.size())), c.camera(), 0.25f * c.size(), c.accent(), 200);
			} else {
				float k = (p - 1) / 0.4f;
				Draw.halo(c.glow(), c.m(), land, c.camera(), 0, (0.6f + 1.4f * k) * c.size(), c.color(), (int) (220 * (1 - k)));
				Draw.ring(c.glow(), c.m(), land, new Vec3(0, 1, 0), (0.4f + 1.8f * k) * c.size(), 0.2f * c.size(), c.accent(), (int) (200 * (1 - k)));
			}
		}
	}

	/** Rocks (blocks: stone by default, ice for Icicle Crash) falling on the target (Rock Slide). */
	private static void fallRocks(Context c) {
		int count = c.count(5);
		BlockState rock = block(c, Blocks.COBBLESTONE.defaultBlockState());
		RandomSource jag = RandomSource.create(c.move().move.hashCode() * 5L);
		Vec3 aim = c.aim();
		for (int i = 0; i < count; i++) {
			float p = Mth.clamp(c.k() * 1.5f - i * 0.1f, 0, 1);
			Vec3 land = aim.add(jag.nextGaussian() * 0.8 * c.size(), 0, jag.nextGaussian() * 0.8 * c.size());
			float sz = (0.4f + jag.nextFloat() * 0.35f) * c.size();
			if (p > 0 && p < 1) {
				block(c, rock, land.add(0, 6 * (1 - p * p), 0), sz, i * 50 + c.t() * 8, i * 30 + c.t() * 6, i * 20);
			} else if (p >= 1) {
				block(c, rock, land.add(0, -0.3, 0), sz * (1 - Mth.clamp((c.k() - 0.8f) * 5, 0, 1)), i * 50, i * 30, i * 20);
			}
		}
	}

	/** Stone blades (blocks) bursting out of the ground under the target (Stone Edge). */
	private static void riseRocks(Context c) {
		int count = c.count(5);
		BlockState rock = block(c, Blocks.STONE.defaultBlockState());
		Vec3 feet = c.move().aimFeet();
		float rise = (float) Math.sin(Mth.clamp(c.k() * 1.8f, 0, 1) * Math.PI * 0.5);
		float sink = Mth.clamp((c.k() - 0.75f) * 4, 0, 1);
		for (int i = 0; i < count; i++) {
			double angle = Math.PI * 2 * i / count + 0.4;
			double r = (c.move().targetWidth() * 0.5 + 0.3) * c.size();
			Vec3 base = feet.add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
			PoseStack pose = c.pose();
			pose.pushPose();
			pose.translate(base.x, base.y - 0.2 + (rise - sink) * 1.2 * c.size() - 1.2 * c.size(), base.z);
			pose.mulPose(Axis.YP.rotationDegrees((float) Math.toDegrees(angle)));
			pose.mulPose(Axis.ZP.rotationDegrees(15 + i * 7));
			pose.scale(0.35f * c.size(), 2.2f * c.size(), 0.35f * c.size());
			pose.translate(-0.5, 0, -0.5);
			Minecraft.getInstance().getBlockRenderer().renderSingleBlock(rock, pose, c.buffers(), 0xF000F0, OverlayTexture.NO_OVERLAY);
			pose.popPose();
		}
	}

	/** Earthquake: the ground pulses outward from the user in rings, and cracks open under the target. */
	private static void quake(Context c) {
		Vec3 feet = c.move().user().position().add(0, 0.05, 0);
		double reach = feet.distanceTo(c.move().aimFeet()) + 3;
		for (int i = 0; i < 4; i++) {
			float p = c.k() * 1.4f - i * 0.15f;
			if (p > 0 && p < 1) {
				Draw.ring(c.glow(), c.m(), feet, new Vec3(0, 1, 0), (float) (p * reach), 0.45f * c.size(), c.color(), (int) (200 * (1 - p)));
			}
		}
		cracks(c);
	}

	/** Pillars of light bursting from the ground under the target (Earth Power). */
	private static void pillars(Context c) {
		Vec3 feet = c.move().aimFeet();
		float height = (float) Math.sin(Mth.clamp(c.k() * 1.6f, 0, 1) * Math.PI) * 3f * c.size();
		for (int i = 0; i < 5; i++) {
			double angle = Math.PI * 2 * i / 5;
			Vec3 base = feet.add(Math.cos(angle) * 0.6 * c.size(), 0.05, Math.sin(angle) * 0.6 * c.size());
			Draw.streak(c.glow(), c.m(), base, base.add(0, height, 0), c.camera(), 0.35f * c.size(), c.color(), 220);
			Draw.streak(c.glow(), c.m(), base, base.add(0, height * 0.8, 0), c.camera(), 0.12f * c.size(), c.accent(), 240);
		}
		Draw.ring(c.glow(), c.m(), feet.add(0, 0.05, 0), new Vec3(0, 1, 0), (0.4f + 1.6f * c.k()) * c.size(), 0.3f * c.size(), c.color(), (int) (220 * (1 - c.k())));
	}

	/** Glowing cracks spreading on the ground from under the target. */
	private static void cracks(Context c) {
		Vec3 feet = c.move().aimFeet().add(0, 0.06, 0);
		RandomSource jag = RandomSource.create(c.move().move.hashCode() * 11L);
		float spread = Mth.clamp(c.k() * 2, 0, 1);
		int alpha = (int) (230 * (1 - Mth.clamp((c.k() - 0.6f) / 0.4f, 0, 1)));
		for (int i = 0; i < 7; i++) {
			double angle = Math.PI * 2 * i / 7 + jag.nextFloat() * 0.4;
			Vec3 last = feet;
			for (int s = 1; s <= 4; s++) {
				double r = s * 0.5 * c.size() * spread;
				angle += (jag.nextFloat() - 0.5) * 0.6;
				Vec3 at = feet.add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
				Draw.streak(c.glow(), c.m(), at, last, c.camera(), 0.07f * c.size(), c.accent(), alpha);
				last = at;
			}
		}
	}

	/** A whirlwind standing on the target (Hurricane, Blizzard, Leaf Storm), with what it carries. */
	private static void tornado(Context c) {
		Vec3 base = c.move().aimFeet();
		float fade = c.fade(0.15f, 0.3f);
		for (int i = 0; i < 26; i++) {
			double h = i / 26.0 * 3.5 * c.size();
			double r = (0.35 + h * 0.4) * c.size();
			double angle = c.t() * 0.6 + i * 0.75;
			Vec3 a = base.add(Math.cos(angle) * r, h, Math.sin(angle) * r);
			Vec3 b = base.add(Math.cos(angle - 0.9) * r, h + 0.1, Math.sin(angle - 0.9) * r);
			Draw.streak(c.glow(), c.m(), a, b, c.camera(), 0.07f * c.size(), i % 2 == 0 ? c.color() : c.accent(), (int) (210 * fade));
		}
		if (c.layer().block != null) {
			BlockState carried = block(c, Blocks.OAK_LEAVES.defaultBlockState());
			for (int i = 0; i < 8; i++) {
				double h = (i / 8.0 * 3 + c.t() * 0.05) % 3 * c.size();
				double angle = c.t() * 0.7 + i * 0.8;
				double r = (0.4 + h * 0.4) * c.size();
				block(c, carried, base.add(Math.cos(angle) * r, h, Math.sin(angle) * r), 0.2f * c.size(), c.t() * 20 + i * 45, c.t() * 30, 0);
			}
		}
	}

	// ---------------------------------------------------------------- on the target

	/** Fire Blast's star: five arms of flame (the 大 kanji's shape) bursting out of the target. */
	private static void starFire(Context c) {
		Vec3 at = c.aim();
		Vec3[] b = c.screen(at);
		float s = c.size() * Math.max(1f, c.move().targetHeight() * 0.7f);
		float open = Mth.clamp(c.k() * 3, 0, 1);
		float fade = 1 - Mth.clamp((c.k() - 0.55f) / 0.45f, 0, 1);
		double[] angles = { Math.PI / 2, Math.PI * 0.05, Math.PI * 0.95, -Math.PI * 0.3, -Math.PI * 0.7 };
		for (double angle : angles) {
			Vec3 d = b[0].scale(Math.cos(angle)).add(b[1].scale(Math.sin(angle)));
			Vec3 tip = at.add(d.scale(1.5 * s * open));
			Draw.streak(c.glow(), c.m(), tip, at, c.camera(), 0.35f * s, c.color(), (int) (220 * fade));
			Draw.streak(c.glow(), c.m(), tip, at, c.camera(), 0.14f * s, c.accent(), (int) (240 * fade));
			Draw.halo(c.glow(), c.m(), tip, c.camera(), 0, 0.4f * s, c.color(), (int) (200 * fade));
		}
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, 0.9f * s, 0xFFFFFF, (int) (200 * fade));
	}

	/** The target frozen in: ice blocks closing round it, then shattering. */
	private static void freeze(Context c) {
		Vec3 feet = c.move().aimFeet();
		float w = c.move().targetWidth() * 0.6f + 0.3f;
		float h = c.move().targetHeight();
		float grow = Mth.clamp(c.k() * 3, 0, 1);
		float shatter = Mth.clamp((c.k() - 0.7f) / 0.3f, 0, 1);
		for (int i = 0; i < 8; i++) {
			double angle = Math.PI * 2 * i / 8;
			Vec3 at = feet.add(Math.cos(angle) * w * (1 + shatter * 1.5), h * (0.2 + 0.25 * (i % 3)) + shatter * (i % 2) * 0.5, Math.sin(angle) * w * (1 + shatter * 1.5));
			block(c, Blocks.ICE.defaultBlockState(), at, 0.5f * c.size() * grow * (1 - shatter), (float) Math.toDegrees(angle), i * 25, i * 15);
		}
	}

	/** The target lifted and shaken in a pink glow, rings of distortion around it (Psychic). */
	private static void lift(Context c) {
		Vec3 at = c.aim().add(0, Math.sin(c.k() * Math.PI) * 0.5, 0);
		float r = c.move().targetWidth() * 0.7f + 0.4f * c.size();
		float fade = c.fade(0.1f, 0.3f);
		Draw.halo(c.glow(), c.m(), at, c.camera(), r * 0.5f, r * 1.8f, c.color(), (int) (170 * fade));
		for (int i = 0; i < 3; i++) {
			double angle = c.t() * 0.4 + i * 2.1;
			Vec3 normal = new Vec3(Math.cos(angle), 0.6, Math.sin(angle)).normalize();
			Draw.ring(c.glow(), c.m(), at, normal, r * (1 + 0.2f * i), 0.06f * c.size(), c.accent(), (int) (220 * fade));
		}
	}

	/** A splash: a crown of water around the target and a flat ripple (Waterfall, Liquidation, Flip Turn). */
	private static void splash(Context c) {
		Vec3 feet = c.move().aimFeet();
		float fade = 1 - c.k();
		float r = c.move().targetWidth() * 0.6f + 0.3f;
		for (int i = 0; i < 12; i++) {
			double angle = Math.PI * 2 * i / 12;
			Vec3 base = feet.add(Math.cos(angle) * r * (1 + c.k()), 0.1, Math.sin(angle) * r * (1 + c.k()));
			double up = Math.sin(Math.min(1, c.k() * 1.5) * Math.PI) * 1.4 * c.size();
			Draw.streak(c.glow(), c.m(), base.add(Math.cos(angle) * 0.3, up, Math.sin(angle) * 0.3), base, c.camera(), 0.1f * c.size(), c.accent(), (int) (220 * fade));
		}
		Draw.ring(c.glow(), c.m(), feet.add(0, 0.06, 0), new Vec3(0, 1, 0), r + 2f * c.k() * c.size(), 0.2f * c.size(), c.color(), (int) (200 * fade));
		impact(c, c.aim(), c.k(), 0.8f);
	}

	/** Goo splattered on and around the target (Sludge Bomb, Gunk Shot). */
	private static void splat(Context c) {
		Vec3 at = c.aim();
		float fade = 1 - Mth.clamp((c.k() - 0.5f) * 2, 0, 1);
		for (int i = 0; i < 9; i++) {
			double angle = i * 0.7;
			Vec3 blob = at.add(Math.cos(angle) * c.k() * 1.6 * c.size(), Math.sin(i * 1.9) * 0.6 * c.k() - c.k() * c.k() * 0.8, Math.sin(angle) * c.k() * 1.6 * c.size());
			Draw.sphere(c.buffers().getBuffer(Draw.DARK), c.m(), blob, (0.16f + (i % 3) * 0.05f) * c.size() * fade, darken(c.color()), 230, false);
		}
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, (0.6f + c.k()) * c.size(), c.color(), (int) (180 * fade));
		Draw.ring(c.glow(), c.m(), c.move().aimFeet().add(0, 0.06, 0), new Vec3(0, 1, 0), (0.4f + 1.6f * c.k()) * c.size(), 0.35f * c.size(), c.color(), (int) (180 * fade));
	}

	/** Electric arcs crawling over the target. */
	private static void sparksOn(Context c) {
		Vec3 at = c.aim();
		float r = c.move().targetWidth() * 0.6f + 0.3f * c.size();
		RandomSource jag = RandomSource.create((long) (c.t() / 2));
		for (int i = 0; i < 6; i++) {
			Vec3 a = at.add(new Vec3(jag.nextGaussian(), jag.nextGaussian(), jag.nextGaussian()).normalize().scale(r));
			Vec3 b = at.add(new Vec3(jag.nextGaussian(), jag.nextGaussian(), jag.nextGaussian()).normalize().scale(r));
			bolt(c.glow(), c.m(), c.camera(), a, b, c.t() + i, 0.04f * c.size(), c.color(), i);
		}
	}

	/** Flames licking up the target. */
	private static void flamesOn(Context c) {
		Vec3 feet = c.move().aimFeet();
		float w = c.move().targetWidth() * 0.55f + 0.2f;
		float h = c.move().targetHeight();
		float fade = c.fade(0.1f, 0.4f);
		for (int i = 0; i < 12; i++) {
			double angle = i * 0.52 + c.t() * 0.2;
			double phase = (c.t() * 0.09 + i * 0.29) % 1;
			Vec3 base = feet.add(Math.cos(angle) * w, phase * h, Math.sin(angle) * w);
			Draw.streak(c.glow(), c.m(), base.add(0, 0.6 * c.size(), 0), base, c.camera(), 0.16f * c.size(), i % 2 == 0 ? c.color() : c.accent(), (int) (220 * fade * Math.sin(phase * Math.PI)));
		}
	}

	/** Rays of light shooting out in every direction (Dazzling Gleam, Discharge). */
	private static void rays(Context c, Vec3 at) {
		float fade = c.fade(0.1f, 0.4f);
		RandomSource jag = RandomSource.create(c.move().move.hashCode() * 13L);
		for (int i = 0; i < 16; i++) {
			Vec3 d = new Vec3(jag.nextGaussian(), jag.nextGaussian() * 0.6, jag.nextGaussian()).normalize();
			float length = (1 + jag.nextFloat() * 2) * c.size() * Math.min(1, c.k() * 3);
			Draw.streak(c.glow(), c.m(), at.add(d.scale(0.3)), at.add(d.scale(length)), c.camera(), 0.1f * c.size(), i % 2 == 0 ? c.color() : c.accent(), (int) (220 * fade));
		}
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, 1.2f * c.size(), 0xFFFFFF, (int) (160 * fade));
	}

	/** A plain burst on the target: flash, rings. */
	private static void burst(Context c, Vec3 at, float k, float scale) {
		float fade = 1 - k;
		float s = c.size() * scale;
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, s * (0.6f + 1.6f * k), c.color(), (int) (210 * fade * fade));
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, s * 0.5f * fade, 0xFFFFFF, (int) (200 * fade));
		Draw.ring(c.glow(), c.m(), at, c.camera().subtract(at).normalize(), s * (0.4f + 2f * k), s * 0.2f, c.accent(), (int) (200 * fade));
	}

	// ---------------------------------------------------------------- self and status

	/** Swords Dance: real swords circling the user, rising, then pointing up all together. */
	private static void swords(Context c) {
		Entity user = c.move().user();
		int blades = c.count(6);
		float fade = c.fade(0.1f, 0.15f);
		float r = user.getBbWidth() * 0.8f + 0.6f * c.size();
		for (int i = 0; i < blades; i++) {
			double angle = Math.PI * 2 * i / blades + c.t() * 0.25;
			double radius = r * (1.3 - 0.5 * c.k());
			Vec3 at = user.position().add(Math.cos(angle) * radius, user.getBbHeight() * (0.3 + 0.6 * c.k()), Math.sin(angle) * radius);
			item(c, stack(c, Items.IRON_SWORD), at, 1.1f * c.size() * fade, (float) -Math.toDegrees(angle), 0, 135);
			Draw.halo(c.glow(), c.m(), at, c.camera(), 0, 0.4f * c.size(), c.color(), (int) (120 * fade));
		}
	}

	/** Two strands spiralling up the user (Dragon Dance, Quiver Dance, Agility). */
	private static void dance(Context c) {
		Entity user = c.move().user();
		float fade = c.fade(0.15f, 0.2f);
		float r = user.getBbWidth() * 0.7f + 0.4f * c.size();
		for (int strand = 0; strand < 2; strand++) {
			Vec3 last = null;
			for (int i = 0; i <= 30; i++) {
				double p = i / 30.0;
				double angle = p * 12 + c.t() * 0.4 + strand * Math.PI;
				Vec3 at = user.position().add(Math.cos(angle) * r, p * user.getBbHeight() * 1.3, Math.sin(angle) * r);
				if (last != null && p < c.k() * 1.6) {
					Draw.streak(c.glow(), c.m(), at, last, c.camera(), 0.08f * c.size(), strand == 0 ? c.color() : c.accent(), (int) (220 * fade));
				}
				last = at;
			}
		}
	}

	/** A glow and rings rising up the user (Nasty Plot, Calm Mind, Bulk Up). */
	private static void aura(Context c) {
		Entity user = c.move().user();
		float fade = c.fade(0.15f, 0.2f);
		float r = user.getBbWidth() * 0.8f + 0.4f * c.size();
		Draw.halo(c.glow(), c.m(), c.userCentre(), c.camera(), 0, r * 1.6f, c.color(), (int) (110 * fade));
		for (int i = 0; i < 3; i++) {
			float p = ((c.t() * 0.04f) + i / 3f) % 1f;
			Draw.ring(c.glow(), c.m(), user.position().add(0, p * user.getBbHeight() * 1.2, 0), new Vec3(0, 1, 0), r * (1 - 0.3f * p), 0.1f * c.size(), c.accent(), (int) (220 * fade * (1 - p)));
		}
	}

	/** A hard shell flashing round the user (Iron Defense, Substitute, Shell Smash). */
	private static void shell(Context c) {
		Entity user = c.move().user();
		float fade = c.fade(0.1f, 0.3f);
		float r = Math.max(user.getBbWidth(), user.getBbHeight()) * 0.7f + 0.2f * c.size();
		Vec3 centre = c.userCentre();
		Draw.sphere(c.glow(), c.m(), centre, r, c.color(), (int) (60 * fade), true);
		for (int i = 0; i < 3; i++) {
			double angle = i * 1.05 + c.t() * 0.1;
			Draw.ring(c.glow(), c.m(), centre, new Vec3(Math.cos(angle), 0.4, Math.sin(angle)).normalize(), r, 0.07f * c.size(), c.accent(), (int) (230 * fade));
		}
		float glint = (c.t() % 12) / 12f;
		Draw.streak(c.glow(), c.m(), centre.add(-r * (1 - 2 * glint), r * 0.6, 0), centre.add(-r * (1 - 2 * glint) + 0.3, r * 0.6 + 0.3, 0), c.camera(), 0.1f * c.size(), 0xFFFFFF, (int) (230 * fade));
	}

	/** Sparkles rising round the user in a warm glow (Recover, Roost, Wish, Synthesis). */
	private static void heal(Context c) {
		Entity user = c.move().user();
		float fade = c.fade(0.15f, 0.25f);
		float r = user.getBbWidth() * 0.8f + 0.4f * c.size();
		Draw.halo(c.glow(), c.m(), c.userCentre(), c.camera(), 0, r * 1.6f, c.color(), (int) (120 * fade));
		for (int i = 0; i < 12; i++) {
			float p = ((c.t() * 0.035f) + i / 12f) % 1f;
			double angle = i * 2.4;
			Vec3 at = user.position().add(Math.cos(angle) * r, p * user.getBbHeight() * 1.4, Math.sin(angle) * r);
			Vec3[] b = c.screen(at);
			int alpha = (int) (230 * fade * Math.sin(p * Math.PI));
			Draw.streak(c.glow(), c.m(), at.add(b[1].scale(0.15)), at.subtract(b[1].scale(0.15)), c.camera(), 0.04f * c.size(), c.accent(), alpha);
			Draw.streak(c.glow(), c.m(), at.add(b[0].scale(0.15)), at.subtract(b[0].scale(0.15)), c.camera(), 0.04f * c.size(), c.accent(), alpha);
		}
	}

	/** Protect: a dome of light, rippling where it is struck. */
	private static void dome(Context c) {
		Entity user = c.move().user();
		float fade = c.fade(0.08f, 0.25f);
		float r = Math.max(user.getBbWidth(), user.getBbHeight()) * 0.75f + 0.3f * c.size();
		Vec3 centre = c.userCentre();
		Draw.sphere(c.glow(), c.m(), centre, r, c.color(), (int) (45 * fade), true);
		Draw.ring(c.glow(), c.m(), centre, c.camera().subtract(centre).normalize(), r, 0.1f * c.size(), c.accent(), (int) (220 * fade));
		for (int i = 0; i < 3; i++) {
			float p = ((c.t() * 0.05f) + i / 3f) % 1f;
			Draw.ring(c.glow(), c.m(), centre, c.camera().subtract(centre).normalize(), r * p, 0.05f * c.size(), c.accent(), (int) (160 * fade * (1 - p)));
		}
	}

	/** A wall of light in front of the user (Reflect, Light Screen), its edges bright. */
	private static void wall(Context c) {
		Vec3 front = c.userCentre().add(c.toTarget().scale(c.move().user().getBbWidth() * 0.6 + 0.6));
		Vec3[] b = Draw.basis(c.toTarget());
		float s = Math.max(1.2f, c.move().user().getBbHeight()) * c.size();
		float fade = c.fade(0.1f, 0.3f);
		Draw.panel(c.glow(), c.m(), front, b[0].scale(s), b[1].scale(s * 0.9), c.color(), (int) (70 * fade));
		Vec3 u = b[0].scale(s);
		Vec3 v = b[1].scale(s * 0.9);
		Vec3[] corners = { front.subtract(u).subtract(v), front.add(u).subtract(v), front.add(u).add(v), front.subtract(u).add(v) };
		for (int i = 0; i < 4; i++) {
			Draw.streak(c.glow(), c.m(), corners[i], corners[(i + 1) % 4], c.camera(), 0.05f * c.size(), c.accent(), (int) (230 * fade));
		}
		float sweep = (c.t() % 14) / 14f;
		Draw.streak(c.glow(), c.m(), front.subtract(u).add(v.scale(1 - 2 * sweep)), front.add(u).add(v.scale(1 - 2 * sweep)), c.camera(), 0.08f * c.size(), 0xFFFFFF, (int) (180 * fade));
	}

	/** Stealth Rock: pointed stones (blocks) floating and turning round the target, then settling. */
	private static void rocksFloat(Context c) {
		Vec3 feet = c.move().aimFeet();
		BlockState rock = block(c, Blocks.COBBLESTONE.defaultBlockState());
		float r = c.move().targetWidth() * 0.6f + 0.6f * c.size();
		float appear = Mth.clamp(c.k() * 4, 0, 1);
		float settle = Mth.clamp((c.k() - 0.6f) / 0.4f, 0, 1);
		for (int i = 0; i < 5; i++) {
			double angle = Math.PI * 2 * i / 5 + c.t() * 0.05;
			Vec3 at = feet.add(Math.cos(angle) * r, (1.2 + 0.3 * Math.sin(c.t() * 0.2 + i)) * (1 - settle) * c.move().targetHeight() + 0.2, Math.sin(angle) * r);
			block(c, rock, at, 0.35f * c.size() * appear, c.t() * 6 + i * 70, 45, 30 + i * 11);
			Draw.halo(c.glow(), c.m(), at, c.camera(), 0, 0.35f * c.size(), c.color(), (int) (90 * (1 - settle)));
		}
	}

	/** Spikes (pointed dripstone, or a block of the layer's) scattered on the ground before the target. */
	private static void caltrops(Context c) {
		Vec3 feet = c.move().aimFeet();
		BlockState spike = block(c, Blocks.POINTED_DRIPSTONE.defaultBlockState());
		RandomSource jag = RandomSource.create(c.move().move.hashCode() * 17L);
		for (int i = 0; i < 6; i++) {
			float p = Mth.clamp(c.k() * 2 - i * 0.1f, 0, 1);
			Vec3 land = feet.add(jag.nextGaussian() * 1.2 * c.size(), 0, jag.nextGaussian() * 1.2 * c.size());
			Vec3 at = c.mouth().lerp(land, p).add(0, Math.sin(p * Math.PI) * 2, 0);
			block(c, spike, at, 0.5f * c.size(), i * 60, p < 1 ? c.t() * 20 : 0, 0);
		}
	}

	/** Sticky Web: a web (a cobweb block, flattened) spreading on the ground, its threads glinting. */
	private static void web(Context c) {
		Vec3 feet = c.move().aimFeet().add(0, 0.05, 0);
		float spread = Mth.clamp(c.k() * 2, 0, 1);
		float fade = c.fade(0.05f, 0.3f);
		for (int i = 0; i < 8; i++) {
			double angle = Math.PI * 2 * i / 8;
			Draw.streak(c.glow(), c.m(), feet, feet.add(Math.cos(angle) * 1.6 * spread * c.size(), 0, Math.sin(angle) * 1.6 * spread * c.size()), c.camera(), 0.03f * c.size(), c.accent(), (int) (220 * fade));
		}
		for (int ring = 1; ring <= 3; ring++) {
			Draw.ring(c.glow(), c.m(), feet, new Vec3(0, 1, 0), ring * 0.5f * spread * c.size(), 0.02f * c.size(), c.accent(), (int) (200 * fade));
		}
	}

	/** Will-O-Wisp: ghostly flames circling the target. */
	private static void wisps(Context c) {
		Vec3 at = c.aim();
		float r = c.move().targetWidth() * 0.6f + 0.5f * c.size();
		float fade = c.fade(0.15f, 0.3f);
		for (int i = 0; i < 3; i++) {
			double angle = c.t() * 0.25 + i * Math.PI * 2 / 3;
			Vec3 wisp = at.add(Math.cos(angle) * r, Math.sin(c.t() * 0.2 + i) * 0.3, Math.sin(angle) * r);
			Draw.halo(c.glow(), c.m(), wisp, c.camera(), 0, 0.35f * c.size(), c.color(), (int) (220 * fade));
			Draw.streak(c.glow(), c.m(), wisp.add(0, 0.5 * c.size(), 0), wisp, c.camera(), 0.15f * c.size(), c.accent(), (int) (220 * fade));
		}
	}

	/** A poisonous or sleepy cloud hanging round the target (Toxic, Spore, Haze). */
	private static void cloud(Context c) {
		Vec3 at = c.aim();
		float r = c.move().targetWidth() * 0.6f + 0.4f * c.size();
		float fade = c.fade(0.15f, 0.35f);
		for (int i = 0; i < 9; i++) {
			double angle = i * 0.8 + c.t() * 0.05;
			Vec3 puff = at.add(Math.cos(angle) * r * 0.8, Math.sin(i * 1.7) * 0.4, Math.sin(angle) * r * 0.8);
			Draw.halo(c.glow(), c.m(), puff, c.camera(), 0, 0.5f * c.size(), i % 2 == 0 ? c.color() : c.accent(), (int) (110 * fade));
		}
	}

	/** Leech Seed: seeds landing on the target, sprouting vines that wrap it. */
	private static void seeds(Context c) {
		Vec3 feet = c.move().aimFeet();
		float w = c.move().targetWidth() * 0.55f + 0.2f;
		float h = c.move().targetHeight();
		float grow = Mth.clamp(c.k() * 1.5f, 0, 1);
		float fade = c.fade(0.05f, 0.3f);
		for (int v = 0; v < 3; v++) {
			Vec3 last = null;
			for (int i = 0; i <= 20; i++) {
				double p = i / 20.0;
				if (p > grow) {
					break;
				}
				double angle = p * 9 + v * 2.1;
				Vec3 at = feet.add(Math.cos(angle) * w, p * h * 1.05, Math.sin(angle) * w);
				if (last != null) {
					Draw.streak(c.glow(), c.m(), at, last, c.camera(), 0.06f * c.size(), c.color(), (int) (230 * fade));
				}
				last = at;
			}
		}
		for (int i = 0; i < 3; i++) {
			item(c, new ItemStack(Items.WHEAT_SEEDS), feet.add(Math.cos(i * 2.1) * w, h * 0.3, Math.sin(i * 2.1) * w), 0.4f * c.size() * fade, i * 120, 0, 0);
		}
	}

	/** Notes floating round the target (Encore, Yawn, Sing): note blocks' particles drawn as glowing dots. */
	private static void notes(Context c) {
		Vec3 at = c.aim();
		float r = c.move().targetWidth() * 0.6f + 0.4f * c.size();
		float fade = c.fade(0.15f, 0.3f);
		for (int i = 0; i < 4; i++) {
			double angle = i * Math.PI / 2 + c.t() * 0.15;
			Vec3 note = at.add(Math.cos(angle) * r, 0.4 + Math.sin(i + c.t() * 0.2) * 0.3, Math.sin(angle) * r);
			Vec3[] b = c.screen(note);
			Draw.halo(c.glow(), c.m(), note, c.camera(), 0, 0.14f * c.size(), i % 2 == 0 ? c.color() : c.accent(), (int) (230 * fade));
			Draw.streak(c.glow(), c.m(), note.add(b[0].scale(0.1)), note.add(b[0].scale(0.1)).add(b[1].scale(0.4 * c.size())), c.camera(), 0.03f * c.size(), c.accent(), (int) (230 * fade));
		}
	}

	/** Eyes of light flashing over the target, a pulse (Glare, Psychic's gaze, Trick, Strength Sap). */
	private static void glare(Context c) {
		Vec3 at = c.aim().add(0, c.move().targetHeight() * 0.3, 0);
		Vec3[] b = c.screen(at);
		float fade = c.fade(0.1f, 0.3f);
		float s = c.size();
		for (int side = -1; side <= 1; side += 2) {
			Vec3 eye = at.add(b[0].scale(side * 0.35 * s)).add(b[1].scale(0.6 * s));
			Draw.arc(c.glow(), c.m(), eye, b[0], b[1], 0.25f * s, 0.3f, 2.84f, 1f, 0.06f * s, c.accent(), (int) (230 * fade));
			Draw.halo(c.glow(), c.m(), eye, c.camera(), 0, 0.12f * s, 0xFFFFFF, (int) (230 * fade));
		}
		Draw.halo(c.glow(), c.m(), c.aim(), c.camera(), 0, (c.move().targetWidth() + 0.6f) * s, c.color(), (int) (100 * fade));
	}

	/** Taunt: an angry mark pulsing over the target. */
	private static void taunt(Context c) {
		Vec3 at = c.aim().add(0, c.move().targetHeight() * 0.6 + 0.3, 0);
		Vec3[] b = c.screen(at);
		float pulse = 1 + 0.2f * Mth.sin(c.t() * 0.8f);
		float s = 0.3f * c.size() * pulse;
		float fade = c.fade(0.1f, 0.3f);
		for (int i = 0; i < 4; i++) {
			double angle = Math.PI / 2 * i + Math.PI / 4;
			Vec3 d = b[0].scale(Math.cos(angle)).add(b[1].scale(Math.sin(angle)));
			Vec3 side = b[0].scale(-Math.sin(angle)).add(b[1].scale(Math.cos(angle)));
			Draw.streak(c.glow(), c.m(), at.add(d.scale(s * 1.4)).add(side.scale(s * 0.5)), at.add(d.scale(s * 0.4)), c.camera(), 0.05f * c.size(), c.color(), (int) (240 * fade));
			Draw.streak(c.glow(), c.m(), at.add(d.scale(s * 1.4)).subtract(side.scale(s * 0.5)), at.add(d.scale(s * 0.4)), c.camera(), 0.05f * c.size(), c.color(), (int) (240 * fade));
		}
	}

	// ---------------------------------------------------------------- helpers

	/** A jagged lightning bolt, re-drawn every two ticks. */
	static void bolt(VertexConsumer glow, Matrix4f m, Vec3 camera, Vec3 from, Vec3 to, float t, float width, int color, int seed) {
		RandomSource jag = RandomSource.create((long) (t / 2) * 31 + seed);
		Vec3 axis = to.subtract(from);
		int steps = Math.max(4, (int) (axis.length() * 2));
		Vec3 last = from;
		for (int i = 1; i <= steps; i++) {
			double p = i / (double) steps;
			Vec3 at = i == steps ? to : from.add(axis.scale(p)).add(new Vec3(jag.nextGaussian(), jag.nextGaussian(), jag.nextGaussian()).scale(0.25));
			Draw.streak(glow, m, at, last, camera, width * 2.5f, color, 130);
			Draw.streak(glow, m, at, last, camera, width, 0xFFFFFF, 245);
			last = at;
		}
	}

	private static BlockState block(Context c, BlockState fallback) {
		if (c.layer().block == null) {
			return fallback;
		}
		ResourceLocation id = ResourceLocation.tryParse(c.layer().block);
		return id == null || !BuiltInRegistries.BLOCK.containsKey(id) ? fallback : BuiltInRegistries.BLOCK.get(id).defaultBlockState();
	}

	private static ItemStack stack(Context c, net.minecraft.world.item.Item fallback) {
		if (c.layer().item == null) {
			return new ItemStack(fallback);
		}
		ResourceLocation id = ResourceLocation.tryParse(c.layer().item);
		return id == null || !BuiltInRegistries.ITEM.containsKey(id) ? new ItemStack(fallback) : new ItemStack(BuiltInRegistries.ITEM.get(id));
	}

	/** A block drawn at {@code at} (its centre), {@code scale} blocks wide, turned. */
	private static void block(Context c, BlockState state, Vec3 at, float scale, float yaw, float pitch, float roll) {
		if (scale <= 0.01f) {
			return;
		}
		PoseStack pose = c.pose();
		pose.pushPose();
		pose.translate(at.x, at.y, at.z);
		pose.mulPose(Axis.YP.rotationDegrees(yaw));
		pose.mulPose(Axis.XP.rotationDegrees(pitch));
		pose.mulPose(Axis.ZP.rotationDegrees(roll));
		pose.scale(scale, scale, scale);
		pose.translate(-0.5, -0.5, -0.5);
		Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, c.buffers(), 0xF000F0, OverlayTexture.NO_OVERLAY);
		pose.popPose();
	}

	/** An item drawn at {@code at}, {@code scale} blocks tall, turned. */
	private static void item(Context c, ItemStack stack, Vec3 at, float scale, float yaw, float pitch, float roll) {
		if (scale <= 0.01f) {
			return;
		}
		PoseStack pose = c.pose();
		pose.pushPose();
		pose.translate(at.x, at.y, at.z);
		pose.mulPose(Axis.YP.rotationDegrees(yaw));
		pose.mulPose(Axis.XP.rotationDegrees(pitch));
		pose.mulPose(Axis.ZP.rotationDegrees(roll));
		pose.scale(scale, scale, scale);
		Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, 0xF000F0, OverlayTexture.NO_OVERLAY,
				pose, c.buffers(), Minecraft.getInstance().level, 0);
		pose.popPose();
	}

	private static int darken(int color) {
		return ((color >> 16 & 255) / 4) << 16 | ((color >> 8 & 255) / 4) << 8 | (color & 255) / 4;
	}
}
