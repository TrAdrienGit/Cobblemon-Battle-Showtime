package com.mystaria.showtime.move;

import static com.mystaria.showtime.move.Signatures.*;

import java.util.Set;

import com.mystaria.showtime.battle.BattleCameraDirector;
import com.mystaria.showtime.move.Motifs.Context;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Signature motifs, second batch (2026-11-26): the moves ranked 51 to 100 in Smogon National Dex usage, each with a
 * drawing of its own, built on the helpers of {@link Signatures}. Elemental punches wear their element (ice, fire,
 * lightning), drains pull light back to the user, sound moves ripple, and so on.
 */
final class Signatures2 {

	private static final Set<String> NAMES = Set.of("icepunch", "firepunch", "thunderpunch", "bulletpunch", "drainpunch", "lowkick",
			"tripleaxel", "superpower", "fakeout", "extremespeed", "aquajet", "surgingstrikes", "liquidation", "razorshell", "surf",
			"poisonjab", "playrough", "outrage", "bravebird", "flareblitz", "sacredsword", "ceaselessedge", "scaleshot", "diamondstorm",
			"drainingkiss", "gigadrain", "leechseed", "freezedry", "hiddenpower", "weatherball", "thunderclap", "irondefense",
			"psychicnoise", "bugbuzz", "encore", "trick", "quiverdance", "dazzlinggleam", "chillyreception", "mirrorcoat", "hex",
			"poltergeist", "rest", "teleport", "trickroom", "whirlwind", "sludgewave");

	private Signatures2() {
	}

	static boolean render(Context c) {
		switch (c.layer().motif) {
			case "icepunch" -> icePunch(c);
			case "firepunch" -> firePunch(c);
			case "thunderpunch" -> thunderPunch(c);
			case "bulletpunch" -> bulletPunch(c);
			case "drainpunch" -> drainPunch(c);
			case "lowkick" -> lowKick(c);
			case "tripleaxel" -> tripleAxel(c);
			case "superpower" -> superpower(c);
			case "fakeout" -> fakeOut(c);
			case "extremespeed" -> extremeSpeed(c);
			case "aquajet" -> aquaJet(c);
			case "surgingstrikes" -> surgingStrikes(c);
			case "liquidation" -> liquidation(c);
			case "razorshell" -> razorShell(c);
			case "surf" -> surf(c);
			case "poisonjab" -> poisonJab(c);
			case "playrough" -> playRough(c);
			case "outrage" -> outrage(c);
			case "bravebird" -> braveBird(c);
			case "flareblitz" -> flareBlitz(c);
			case "sacredsword" -> sacredSword(c);
			case "ceaselessedge" -> ceaselessEdge(c);
			case "scaleshot" -> scaleShot(c);
			case "diamondstorm" -> diamondStorm(c);
			case "drainingkiss" -> drainingKiss(c);
			case "gigadrain" -> gigaDrain(c);
			case "leechseed" -> leechSeed(c);
			case "freezedry" -> freezeDry(c);
			case "hiddenpower" -> hiddenPower(c);
			case "weatherball" -> weatherBall(c);
			case "thunderclap" -> thunderclap(c);
			case "irondefense" -> ironDefense(c);
			case "psychicnoise" -> psychicNoise(c);
			case "bugbuzz" -> bugBuzz(c);
			case "encore" -> encore(c);
			case "trick" -> trick(c);
			case "quiverdance" -> quiverDance(c);
			case "dazzlinggleam" -> dazzlingGleam(c);
			case "chillyreception" -> chillyReception(c);
			case "mirrorcoat" -> mirrorCoat(c);
			case "hex" -> hex(c);
			case "poltergeist" -> poltergeist(c);
			case "rest" -> rest(c);
			case "teleport" -> teleport(c);
			case "trickroom" -> trickRoom(c);
			case "whirlwind" -> whirlwind(c);
			case "sludgewave" -> sludgeWave(c);
			default -> {
				return Signatures3.render(c);
			}
		}
		return true;
	}

	static boolean tick(Context c, ClientLevel level) {
		String motif = c.layer().motif;
		if (!NAMES.contains(motif)) {
			return Signatures3.tick(c, level);
		}
		boolean first = c.t() < 1f;
		String phase = phase(c);
		switch (motif) {
			case "thunderclap", "superpower", "outrage", "flareblitz", "bravebird" -> {
				if (first && phase.equals("hit")) {
					BattleCameraDirector.shake(motif.equals("thunderclap") ? 0.35f : 0.25f, 0.8f);
				}
			}
			case "surf" -> {
				if (phase.equals("wind")) {
					Vec3 crest = surfFront(c);
					RandomSource random = c.random();
					Vec3 sd = side(flat(c.toTarget()));
					for (int i = 0; i < 3; i++) {
						Vec3 at = crest.add(sd.scale(random.nextGaussian() * 2.5));
						level.addParticle(ParticleTypes.SPLASH, at.x, at.y, at.z, 0, 0.2, 0);
					}
				}
			}
			case "sludgewave" -> {
				if (phase.equals("wind") && c.k() < 0.9f) {
					RandomSource random = c.random();
					Vec3 centre = c.move().user().position();
					float r = sludgeRadius(c);
					double angle = random.nextDouble() * Mth.TWO_PI;
					level.addParticle(ParticleTypes.WITCH, centre.x + Math.cos(angle) * r, centre.y + 0.6, centre.z + Math.sin(angle) * r, 0, 0.1, 0);
				}
			}
			case "chillyreception" -> {
				RandomSource random = c.random();
				Vec3 mid = c.userCentre().lerp(c.aim(), 0.5);
				for (int i = 0; i < 2; i++) {
					level.addParticle(ParticleTypes.SNOWFLAKE, mid.x + random.nextGaussian() * 3, mid.y + 3, mid.z + random.nextGaussian() * 3, 0, -0.05, 0);
				}
			}
			default -> {
			}
		}
		if (first) {
			switch (phase) {
				case "self" -> Motifs.burstParticles(c, level, c.userCentre());
				case "hit" -> Motifs.burstParticles(c, level, c.aim());
				default -> {
				}
			}
		}
		return true;
	}

	// =====================================================================
	// Punches and kicks
	// =====================================================================

	/** Where a punch's fist is: thrust home over the first quarter of the layer. */
	private static Vec3 fistPos(Context c, float scale) {
		float thrust = Mth.clamp(c.k() * 4, 0, 1);
		return c.aim().subtract(c.toTarget().scale(0.9 * c.size() * scale * (1 - thrust)));
	}

	private static float landed(Context c) {
		return seg(c.k(), 0.25f, 1f);
	}

	/** Ice Punch: a fist cased in ice shards, the target sealed in a block of ice that bursts into shards. */
	private static void icePunch(Context c) {
		float s = c.size();
		Vec3 at = c.aim();
		Motifs.fist(c, at, c.k(), 1.4f);
		BlockState ice = Blocks.PACKED_ICE.defaultBlockState();
		Vec3 fist = fistPos(c, 1.4f);
		if (c.k() < 0.3f) {
			Vec3[] b = c.screen(fist);
			for (int i = 0; i < 5; i++) {
				double angle = Mth.TWO_PI * i / 5 + c.t() * 0.4;
				Vec3 d = b[0].scale(Math.cos(angle)).add(b[1].scale(Math.sin(angle)));
				crystalToward(c, ice, fist.add(d.scale(0.4 * s)), 0.18f * s, 0.55f * s, d.add(c.toTarget().scale(-0.8)));
			}
		}
		float q = landed(c);
		if (q <= 0) {
			return;
		}
		Vec3 feet = c.move().aimFeet();
		float w = c.move().targetWidth();
		float h = c.move().targetHeight();
		float seal = ease(seg(q, 0, 0.15f)) * (1 - seg(q, 0.55f, 0.62f));
		if (seal > 0) {
			box(c, Blocks.ICE.defaultBlockState(), feet.add(0, h * 0.55, 0), (w + 0.5f) * seal, (h + 0.4f) * seal, (w + 0.5f) * seal, 20);
		}
		float burst = seg(q, 0.58f, 1f);
		if (burst > 0 && burst < 1) {
			for (int i = 0; i < 12; i++) {
				Vec3 d = dirOf(Mth.TWO_PI * i / 12 + 0.3).add(0, 0.3 + 0.5 * Math.sin(i * 2.1), 0).normalize();
				Vec3 pos = at.add(d.scale((0.4 + 2.6 * ease(burst)) * s)).add(0, -1.5 * burst * burst * s, 0);
				crystalToward(c, ice, pos, 0.22f * s * (1 - burst), 0.6f * s * (1 - burst), d);
			}
			Draw.halo(c.glow(), c.m(), at, c.camera(), 0, (1 + burst) * 1.3f * s, c.color(), op(220 * (1 - burst)));
		}
		float frost = ease(seg(q, 0, 0.3f));
		Draw.ring(c.glow(), c.m(), feet.add(0, 0.06, 0), UP, (w * 0.5f + 0.3f + 1.8f * frost) * s, 0.45f * s, c.color(), op(200 * (1 - seg(q, 0.6f, 1f))));
	}

	/** Fire Punch: a fist trailing flames, the blow exploding in fire that climbs the target. */
	private static void firePunch(Context c) {
		float s = c.size();
		Vec3 at = c.aim();
		Motifs.fist(c, at, c.k(), 1.4f);
		Vec3 fist = fistPos(c, 1.4f);
		Vec3 back = c.toTarget().scale(-1);
		if (c.k() < 0.3f) {
			for (int i = 0; i < 5; i++) {
				double wave = Math.sin(c.t() * 0.9 + i * 1.7) * 0.15 * s;
				Vec3 tail = fist.add(back.scale((0.8 + 0.25 * i) * s)).add(0, 0.25 * s + wave, 0);
				Draw.streak(c.glow(), c.m(), fist, tail, c.camera(), (0.3f - i * 0.04f) * s, i % 2 == 0 ? c.color() : c.accent(), 220);
			}
		}
		float q = landed(c);
		if (q <= 0) {
			return;
		}
		blast(c, at, seg(q, 0, 0.7f), 0.9f);
		flames(c, c.move().aimFeet(), c.move().targetWidth() * 0.55f + 0.2f, c.move().targetHeight(), 14, s, 1 - seg(q, 0.7f, 1f));
	}

	/** Thunder Punch: a fist crackling with sparks, the blow throwing bolts out all round. */
	private static void thunderPunch(Context c) {
		float s = c.size();
		Vec3 at = c.aim();
		Motifs.fist(c, at, c.k(), 1.4f);
		Vec3 fist = fistPos(c, 1.4f);
		if (c.k() < 0.3f) {
			sparks(c, fist, 0.5f * s, 4, 0.04f * s);
			Draw.halo(c.glow(), c.m(), fist, c.camera(), 0, 0.8f * s, c.color(), 160);
		}
		float q = landed(c);
		if (q <= 0 || q >= 0.85f) {
			return;
		}
		float reach = ease(seg(q, 0, 0.25f));
		for (int i = 0; i < 8; i++) {
			Vec3 d = dirOf(Mth.TWO_PI * i / 8 + 0.2).add(0, Math.sin(i * 1.9) * 0.6, 0).normalize();
			Motifs.bolt(c.glow(), c.m(), c.camera(), at, at.add(d.scale((0.6 + 2.2 * reach) * s)), c.t() + i, 0.06f * s, c.color(), i);
		}
		sparks(c, at, c.move().targetWidth() * 0.6f + 0.3f * s, 5, 0.05f * s);
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, 2 * s, 0xFFFFFF, op(200 * (1 - seg(q, 0, 0.3f))));
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, 2.2f * s, c.color(), op(150 * (1 - q)));
	}

	/** Bullet Punch: three jabs too fast to follow, each a silver tracer from a new angle, ricocheting sparks. */
	private static void bulletPunch(Context c) {
		float s = c.size();
		Vec3 at = c.aim();
		Vec3[] b = c.screen(at);
		Vec3 dir = c.toTarget();
		float last = seg(c.k(), 0.4f, 1f);
		if (last > 0) {
			Motifs.fist(c, at, last, 1.2f);
		}
		for (int j = 0; j < 3; j++) {
			float p = seg(c.k(), j * 0.2f, j * 0.2f + 0.14f);
			float after = seg(c.k(), j * 0.2f + 0.14f, j * 0.2f + 0.5f);
			if (p <= 0 || after >= 1) {
				continue;
			}
			double angle = -0.6 + j * 0.6;
			Vec3 from = at.subtract(dir.scale(2.2 * s)).add(b[1].scale(Math.sin(angle) * 0.8 * s)).add(b[0].scale(Math.cos(angle) * 0.3 * s));
			Vec3 head = from.lerp(at, ease(p));
			float fade = 1 - after;
			Draw.streak(c.glow(), c.m(), head, from, c.camera(), 0.55f * s, c.color(), op(230 * fade));
			Draw.streak(c.glow(), c.m(), head, from.lerp(head, 0.4), c.camera(), 0.18f * s, 0xFFFFFF, op(255 * fade));
			Draw.halo(c.glow(), c.m(), head, c.camera(), 0, 0.8f * s, 0xFFFFFF, op(240 * fade));
			Draw.halo(c.glow(), c.m(), head, c.camera(), 0, 1.2f * s, c.color(), op(160 * fade));
			if (after > 0) {
				Motifs.impact(c, at.add(b[1].scale(Math.sin(angle) * 0.3 * s)), after, 1.1f);
				twinkle(c, at.add(b[1].scale(Math.sin(angle) * 0.3 * s)), (0.8f + 0.6f * after) * s, 0xFFFFFF, op(250 * (1 - after)));
				RandomSource jag = RandomSource.create(j * 41L);
				for (int i = 0; i < 6; i++) {
					Vec3 d = dir.scale(-0.7).add(randomUnit(jag)).normalize();
					Vec3 sp = at.add(d.scale(after * 1.8 * s));
					Draw.streak(c.glow(), c.m(), sp, at.add(d.scale(Math.max(0, after - 0.15f) * 1.8 * s)), c.camera(), 0.04f * s, 0xFFE8A0, op(255 * fade));
				}
			}
		}
	}

	/** Drain Punch: the blow lands, then motes of green light stream from the target back into the user. */
	private static void drainPunch(Context c) {
		float s = c.size();
		Motifs.fist(c, c.aim(), c.k(), 1.3f);
		drainBack(c, seg(c.k(), 0.3f, 0.95f), 10, c.accent(), 0.32f * s);
	}

	/** Motes flowing from the target back to the user over {@code p} 0 → 1, and the user glowing as they arrive. */
	static void drainBack(Context c, float p, int n, int color, float r) {
		if (p <= 0 || p >= 1) {
			return;
		}
		Vec3 from = c.aim();
		Vec3 to = c.userCentre();
		Vec3 sd = side(flat(to.subtract(from)));
		for (int i = 0; i < n; i++) {
			float q = seg(p, i * 0.05f, i * 0.05f + 0.6f);
			if (q <= 0 || q >= 1) {
				continue;
			}
			double bulge = (i % 2 == 0 ? 1 : -1) * (0.6 + 0.3 * (i % 3));
			Vec3 mid = from.lerp(to, 0.5).add(sd.scale(bulge)).add(0, 0.8 + 0.2 * i % 3, 0);
			Vec3 at = bezier(from, mid, to, ease(q));
			Vec3 tail = bezier(from, mid, to, ease(Math.max(0, q - 0.12f)));
			Draw.streak(c.glow(), c.m(), at, tail, c.camera(), r * 0.7f, color, op(200 * bell(q)));
			Draw.halo(c.glow(), c.m(), at, c.camera(), 0, r * 2, color, op(220 * bell(q)));
			Draw.halo(c.glow(), c.m(), at, c.camera(), 0, r * 0.7f, 0xFFFFFF, op(230 * bell(q)));
		}
		float glow = seg(p, 0.5f, 0.8f) * (1 - seg(p, 0.85f, 1f));
		Entity user = c.move().user();
		Draw.halo(c.glow(), c.m(), to, c.camera(), 0, (user.getBbWidth() + 1.2f) * c.size(), color, op(170 * glow));
	}

	/** Low Kick: a sweep skimming the ground through the target's legs, dust and a crack in its wake. */
	private static void lowKick(Context c) {
		float s = c.size() * Math.max(1f, c.move().targetWidth());
		Vec3 feet = c.move().aimFeet();
		Vec3 dir = flat(c.toTarget());
		Vec3 sd = side(dir);
		Vec3 centre = feet.add(0, 0.45, 0).subtract(dir.scale(0.4 * s));
		float swing = ease(seg(c.k(), 0, 0.3f));
		float fade = 1 - seg(c.k(), 0.35f, 0.8f);
		Draw.arc(c.glow(), c.m(), centre, sd, dir, 1.6f * s, -1.2f, 2.4f, swing, 0.9f * s, c.color(), op(240 * fade));
		Draw.arc(c.glow(), c.m(), centre.add(0, 0.25 * s, 0), sd, dir, 1.5f * s, -1.2f, 2.4f, swing, 0.4f * s, c.accent(), op(200 * fade));
		Draw.arc(c.glow(), c.m(), centre, sd, dir, 1.58f * s, -1.2f, 2.4f, swing, 0.2f * s, 0xFFFFFF, op(255 * fade));
		float angle = -1.2f + 3.6f * swing;
		Vec3 foot = centre.add(sd.scale(Math.cos(angle) * 1.6 * s)).add(dir.scale(Math.sin(angle) * 1.6 * s));
		Draw.halo(c.glow(), c.m(), foot, c.camera(), 0, 0.9f * s, c.accent(), op(240 * fade));
		float q = seg(c.k(), 0.15f, 0.7f);
		if (q > 0 && q < 1) {
			Motifs.impact(c, feet.add(0, 0.4, 0), q, 1.4f);
			cracks(c, feet.add(0, 0.05, 0), q, s, c.accent());
			Draw.ring(c.glow(), c.m(), feet.add(0, 0.06, 0), UP, (0.4f + 2f * q) * s, 0.25f * s, c.accent(), op(200 * (1 - q)));
		}
	}

	/** Triple Axel: three spinning kicks, each wider than the last, ice shards flung off every one. */
	private static void tripleAxel(Context c) {
		float s = c.size();
		Vec3 at = c.aim();
		Vec3 dir = flat(c.toTarget());
		Vec3 sd = side(dir);
		BlockState ice = Blocks.PACKED_ICE.defaultBlockState();
		for (int j = 0; j < 3; j++) {
			float p = seg(c.k(), j * 0.25f, j * 0.25f + 0.35f);
			if (p <= 0 || p >= 1) {
				continue;
			}
			float r = (1.1f + 0.4f * j) * s;
			float spin = ease(Math.min(1, p * 2));
			float fade = 1 - seg(p, 0.5f, 1f);
			Vec3 axis = sd.scale(Math.cos(j * 0.9)).add(dir.scale(Math.sin(j * 0.9)));
			Draw.arc(c.glow(), c.m(), at, axis, UP, r, -0.5f, 5.8f, spin, (0.5f + 0.12f * j) * s, c.color(), op(240 * fade));
			Draw.arc(c.glow(), c.m(), at, axis, UP, r * 0.97f, -0.5f, 5.8f, spin, 0.14f * s, 0xFFFFFF, op(255 * fade));
			if (p > 0.45f) {
				float q = seg(p, 0.45f, 1f);
				Motifs.impact(c, at, q, 0.7f + 0.2f * j);
				for (int i = 0; i < 5 + j * 2; i++) {
					Vec3 d = dirOf(i * 2.4 + j).add(0, 0.4, 0).normalize();
					crystalToward(c, ice, at.add(d.scale((0.3 + 2.2 * q) * s)), 0.22f * s * (1 - q), 0.65f * s * (1 - q), d);
				}
			}
		}
		float frost = seg(c.k(), 0.6f, 1f);
		if (frost > 0 && frost < 1) {
			Draw.ring(c.glow(), c.m(), c.move().aimFeet().add(0, 0.06, 0), UP, (0.5f + 2.5f * ease(frost)) * s, 0.4f * s, c.color(), op(200 * (1 - frost)));
		}
	}

	/** Superpower: a red surge of strength on the user, then one crushing blow: shock rings and a heaving ground. */
	private static void superpower(Context c) {
		float s = c.size();
		if (phase(c).equals("wind")) {
			Entity user = c.move().user();
			float fade = c.fade(0.15f, 0.1f);
			float w = user.getBbWidth() * 0.6f + 0.3f;
			flames(c, user.position(), w, user.getBbHeight() * 1.2f, 12, s, fade);
			float pulse = (c.t() * 0.12f) % 1f;
			Draw.ring(c.glow(), c.m(), user.position().add(0, 0.07, 0), UP, (w + 0.3f + 1.5f * pulse) * s, 0.2f * s, c.accent(), op(200 * fade * (1 - pulse)));
			Draw.halo(c.glow(), c.m(), c.userCentre(), c.camera(), w, (w + 1.4f) * s, c.color(), op(110 * fade));
			return;
		}
		Vec3 at = c.aim();
		Vec3 feet = c.move().aimFeet();
		float k = c.k();
		Motifs.impact(c, at, seg(k, 0, 0.6f), 1.8f);
		Vec3[] normals = { c.toTarget(), UP, side(flat(c.toTarget())) };
		for (int i = 0; i < 3; i++) {
			float q = seg(k, i * 0.08f, i * 0.08f + 0.6f);
			if (q > 0 && q < 1) {
				Draw.ring(c.glow(), c.m(), at, normals[i], (0.5f + 3f * q) * s, 0.3f * s, i == 1 ? c.accent() : c.color(), op(230 * (1 - q)));
			}
		}
		heave(c, feet, seg(k, 0, 0.6f) * 3.6f * s, 3f * s, 0.35f * s, ground(feet));
		cracks(c, at, seg(k, 0.05f, 0.7f), s * 1.3f, c.accent());
	}

	/** Fake Out: two palms clapping shut in front of the target's face, a startling flash. */
	private static void fakeOut(Context c) {
		float s = c.size() * Math.max(1f, c.move().targetHeight() * 0.7f);
		Vec3 face = c.aim().add(0, c.move().targetHeight() * 0.25, 0).subtract(c.toTarget().scale(c.move().targetWidth() * 0.5 + 0.3));
		Vec3[] b = c.screen(face);
		float close = seg(c.k(), 0, 0.25f);
		close = close * close;
		float fade = 1 - seg(c.k(), 0.3f, 0.6f);
		for (int side = -1; side <= 1; side += 2) {
			Vec3 palm = face.add(b[0].scale(side * (0.15 + 1.4 * (1 - close)) * s));
			Draw.halo(c.glow(), c.m(), palm, c.camera(), 0, 0.42f * s, c.color(), op(230 * fade));
			Draw.halo(c.glow(), c.m(), palm, c.camera(), 0, 0.2f * s, 0xFFFFFF, op(230 * fade));
			for (int f = 0; f < 4; f++) {
				Vec3 root = palm.add(b[1].scale(0.2 * s)).add(b[0].scale(side * (f - 1.5) * -0.02 * s));
				Vec3 tip = root.add(b[1].scale((0.35 + 0.08 * (f % 2)) * s)).add(b[0].scale((f - 1.5) * 0.09 * s));
				line(c, root, tip, 0.07f * s, c.color(), 230 * fade);
			}
		}
		float clap = seg(c.k(), 0.25f, 0.75f);
		if (clap > 0 && clap < 1) {
			Draw.halo(c.glow(), c.m(), face, c.camera(), 0, (0.8f + 2f * clap) * s, 0xFFFFFF, op(240 * (1 - clap)));
			Draw.ring(c.glow(), c.m(), face, c.camera().subtract(face).normalize(), (0.3f + 1.8f * clap) * s, 0.12f * s, c.accent(), op(230 * (1 - clap)));
			for (int i = 0; i < 10; i++) {
				double angle = Mth.TWO_PI * i / 10;
				Vec3 d = b[0].scale(Math.cos(angle)).add(b[1].scale(Math.sin(angle)));
				Draw.streak(c.glow(), c.m(), face.add(d.scale((0.5 + 1.2 * clap) * s)), face.add(d.scale((0.9 + 1.6 * clap) * s)), c.camera(), 0.07f * s, 0xFFFFFF,
						op(240 * (1 - clap)));
			}
		}
	}

	/** Extreme Speed: blurs crossing the target from every side faster than the eye, then the hit that lands. */
	private static void extremeSpeed(Context c) {
		float s = c.size();
		Vec3 at = c.aim();
		RandomSource jag = RandomSource.create(77);
		for (int j = 0; j < 6; j++) {
			Vec3 d = randomUnit(jag).multiply(1, 0.35, 1).normalize();
			float p = seg(c.k(), j * 0.08f, j * 0.08f + 0.14f);
			if (p <= 0 || p >= 1) {
				continue;
			}
			Vec3 from = at.subtract(d.scale(3 * s));
			Vec3 head = from.add(d.scale(6 * s * p));
			Vec3 tail = from.add(d.scale(6 * s * Math.max(0, p - 0.35f)));
			Draw.streak(c.glow(), c.m(), head, tail, c.camera(), 0.8f * s, 0xA0C8FF, op(200 * bell(p)));
			Draw.streak(c.glow(), c.m(), head, tail, c.camera(), 0.35f * s, c.color(), op(240 * bell(p)));
			Draw.streak(c.glow(), c.m(), head, tail, c.camera(), 0.12f * s, 0xFFFFFF, op(255 * bell(p)));
			if (Math.abs(p - 0.5f) < 0.2f) {
				Draw.halo(c.glow(), c.m(), at, c.camera(), 0, 1.5f * s, 0xFFFFFF, 220);
				twinkle(c, at, 1.2f * s, 0xFFFFFF, 240);
			}
		}
		float last = seg(c.k(), 0.5f, 1f);
		if (last > 0) {
			Motifs.impact(c, at, last, 1.4f);
		}
	}

	// =====================================================================
	// Water
	// =====================================================================

	/** Aqua Jet: a spiral of water wrapped round the user as it shoots in, a crown of spray on the hit. */
	private static void aquaJet(Context c) {
		float s = c.size();
		Entity user = c.move().user();
		if (!phase(c).equals("hit")) {
			float fade = phase(c).equals("wind") ? c.fade(0.2f, 0.05f) : 1;
			Vec3 base = user.position();
			float r = user.getBbWidth() * 0.65f + 0.25f * s;
			for (int j = 0; j < 2; j++) {
				Vec3[] pts = new Vec3[20];
				for (int i = 0; i < pts.length; i++) {
					double p = i / 19.0;
					double angle = -c.t() * 0.8 - p * 9 + j * Math.PI;
					pts[i] = base.add(Math.cos(angle) * r, p * user.getBbHeight() * 1.1, Math.sin(angle) * r);
				}
				ribbon(c, pts, 0.18f * s, 0.18f * s, j == 0 ? c.color() : c.accent(), 220 * fade, 180 * fade);
			}
			if (phase(c).equals("travel")) {
				Vec3 from = c.userCentre();
				Vec3 dir = c.toTarget();
				Draw.streak(c.glow(), c.m(), from, from.subtract(dir.scale(2.5 * s)), c.camera(), 0.6f * s, c.color(), 200);
				Draw.streak(c.glow(), c.m(), from, from.subtract(dir.scale(2 * s)), c.camera(), 0.2f * s, 0xFFFFFF, 220);
			}
			return;
		}
		splashCrown(c, c.move().aimFeet(), c.k(), s * 1.2f);
		Motifs.impact(c, c.aim(), seg(c.k(), 0, 0.5f), 1f);
	}

	/** A crown of water thrown up round {@code feet} and falling back as droplets. */
	static void splashCrown(Context c, Vec3 feet, float k, float s) {
		float r0 = c.move().targetWidth() * 0.6f + 0.3f;
		float fade = 1 - seg(k, 0.5f, 1f);
		for (int i = 0; i < 14; i++) {
			double angle = Mth.TWO_PI * i / 14;
			Vec3 d = dirOf(angle);
			Vec3 base = feet.add(d.scale(r0 * (1 + k * 0.6))).add(0, 0.1, 0);
			double up = Math.sin(Math.min(1, k * 1.6) * Math.PI) * 1.6 * s;
			Vec3 top = base.add(d.scale(0.4 * s)).add(0, up, 0);
			Draw.streak(c.glow(), c.m(), top, base, c.camera(), 0.16f * s, c.color(), op(220 * fade));
			Draw.streak(c.glow(), c.m(), top, base.lerp(top, 0.5), c.camera(), 0.06f * s, 0xFFFFFF, op(230 * fade));
			Vec3 drop = base.add(d.scale((0.5 + 1.2 * k) * s)).add(0, (2.2 * k - 2.6 * k * k) * s + 0.1, 0);
			Draw.sphere(c.glow(), c.m(), drop, 0.08f * s * fade, c.accent(), 200, true);
		}
		Draw.ring(c.glow(), c.m(), feet.add(0, 0.06, 0), UP, r0 + (0.3f + 1.6f * k) * s, 0.3f * s, c.accent(), op(200 * fade));
	}

	/** Surging Strikes: three crescents of water in a row, each one a critical hit (a gold star). */
	private static void surgingStrikes(Context c) {
		float s = c.size() * Math.max(1.1f, c.move().targetHeight() * 0.8f);
		Vec3 at = c.aim();
		Vec3[] b = c.screen(at);
		for (int j = 0; j < 3; j++) {
			float p = seg(c.k(), j * 0.2f, j * 0.2f + 0.4f);
			if (p <= 0 || p >= 1) {
				continue;
			}
			double tilt = -0.9 + j * 0.9;
			Vec3 u = b[0].scale(Math.cos(tilt)).add(b[1].scale(Math.sin(tilt)));
			Vec3 v = b[0].scale(-Math.sin(tilt)).add(b[1].scale(Math.cos(tilt)));
			float swing = ease(Math.min(1, p * 2.2f));
			float fade = 1 - seg(p, 0.45f, 1f);
			Draw.arc(c.glow(), c.m(), at, u, v, 1.4f * s, 0.3f, 2.84f, swing, 0.6f * s, c.color(), op(240 * fade));
			Draw.arc(c.glow(), c.m(), at, u, v, 1.35f * s, 0.3f, 2.84f, swing, 0.25f * s, c.accent(), op(250 * fade));
			Draw.arc(c.glow(), c.m(), at, u, v, 1.32f * s, 0.3f, 2.84f, swing, 0.08f * s, 0xFFFFFF, op(255 * fade));
			if (swing >= 1) {
				float q = seg(p, 0.45f, 1f);
				twinkle(c, at.add(v.scale(0.4 * s)), (0.6f + 0.4f * q) * s, 0xFFE040, op(250 * (1 - q)));
				for (int i = 0; i < 6; i++) {
					Vec3 d = u.scale(Math.cos(i)).add(v.scale(Math.sin(i))).add(c.toTarget().scale(-0.5)).normalize();
					Draw.sphere(c.glow(), c.m(), at.add(d.scale(q * 1.5 * s)).add(0, -q * q * s, 0), 0.08f * s * (1 - q), c.accent(), 200, true);
				}
			}
		}
	}

	/** Liquidation: a drill of water driven through the target, the water bursting out behind it in a column. */
	private static void liquidation(Context c) {
		float s = c.size();
		Vec3 at = c.aim();
		Vec3 dir = c.toTarget();
		Vec3[] b = Draw.basis(dir);
		float thrust = ease(seg(c.k(), 0, 0.3f));
		float fade = 1 - seg(c.k(), 0.4f, 0.7f);
		Vec3 tip = at.add(dir.scale((thrust - 0.5) * 1.6 * s));
		for (int j = 0; j < 2; j++) {
			Vec3[] pts = new Vec3[16];
			for (int i = 0; i < pts.length; i++) {
				double p = i / 15.0;
				double angle = c.t() * 1.4 + p * 10 + j * Math.PI;
				double r = (0.05 + 0.4 * p) * s;
				pts[i] = tip.subtract(dir.scale(p * 2.4 * s)).add(b[0].scale(Math.cos(angle) * r)).add(b[1].scale(Math.sin(angle) * r));
			}
			ribbon(c, pts, 0.1f * s, 0.25f * s, j == 0 ? c.color() : c.accent(), 240 * fade, 120 * fade);
		}
		Draw.halo(c.glow(), c.m(), tip, c.camera(), 0, 0.5f * s, 0xFFFFFF, op(230 * fade));
		float burst = seg(c.k(), 0.25f, 0.9f);
		if (burst <= 0 || burst >= 1) {
			return;
		}
		Vec3 behind = c.move().aimFeet().add(flat(dir).scale(c.move().targetWidth() * 0.5 + 0.5));
		Draw.streak(c.glow(), c.m(), behind, behind.add(0, 3 * s * Math.sin(burst * Math.PI), 0), c.camera(), 0.9f * s, c.color(), op(200 * (1 - burst)));
		Draw.streak(c.glow(), c.m(), behind, behind.add(0, 2.4 * s * Math.sin(burst * Math.PI), 0), c.camera(), 0.3f * s, 0xFFFFFF, op(220 * (1 - burst)));
		splashCrown(c, c.move().aimFeet(), burst, s);
		Motifs.impact(c, at, seg(c.k(), 0.25f, 0.7f), 1.1f);
	}

	/** Razor Shell: a shell spun like a blade through the target in a crossing double slash of water. */
	private static void razorShell(Context c) {
		float s = c.size() * Math.max(1.1f, c.move().targetHeight() * 0.8f);
		Vec3 at = c.aim();
		Vec3[] b = c.screen(at);
		ItemStack shell = new ItemStack(Items.NAUTILUS_SHELL);
		for (int j = 0; j < 2; j++) {
			float p = seg(c.k(), j * 0.2f, j * 0.2f + 0.3f);
			if (p <= 0) {
				continue;
			}
			Vec3 u = b[0].scale(j == 0 ? 1 : -1).add(b[1]).normalize();
			Vec3 v = b[1].subtract(b[0].scale(j == 0 ? 1 : -1)).normalize();
			float swing = ease(p);
			float fade = 1 - seg(c.k(), 0.55f, 0.9f);
			Draw.arc(c.glow(), c.m(), at, u, v, 1.2f * s, -0.3f, 3.4f, swing, 0.3f * s, c.color(), op(230 * fade));
			Draw.arc(c.glow(), c.m(), at, u, v, 1.18f * s, -0.3f, 3.4f, swing, 0.08f * s, 0xFFFFFF, op(250 * fade));
			if (p < 1) {
				double angle = -0.3 + 3.7 * swing;
				Vec3 pos = at.add(u.scale(Math.cos(angle) * 1.2 * s)).add(v.scale(Math.sin(angle) * 1.2 * s));
				Motifs.item(c, shell, pos, 0.9f * s, c.t() * 50, 0, c.t() * 40);
			}
		}
		float q = seg(c.k(), 0.25f, 0.8f);
		if (q > 0 && q < 1) {
			Motifs.impact(c, at, q, 1f);
		}
	}

	private static Vec3 surfFront(Context c) {
		Vec3 start = c.move().user().position().subtract(flat(c.toTarget()).scale(0.8));
		Vec3 end = c.move().aimFeet().add(flat(c.toTarget()).scale(1.5));
		return start.lerp(end, ease(seg(c.k(), 0.15f, 0.95f)));
	}

	/**
	 * Surf: a great wave rising behind the user, rolling across the field over it and breaking on the target: rows of
	 * water leaning forward, white foam along the crest.
	 */
	private static void surf(Context c) {
		float s = c.size();
		if (phase(c).equals("hit")) {
			splashCrown(c, c.move().aimFeet(), c.k(), s * 1.4f);
			return;
		}
		Vec3 dir = flat(c.toTarget());
		Vec3 sd = side(dir);
		Vec3 front = surfFront(c);
		float rise = ease(seg(c.k(), 0, 0.2f));
		float fall = seg(c.k(), 0.85f, 1f);
		float height = 2.4f * s * rise * (1 - fall * 0.8f);
		float fade = 1 - seg(c.k(), 0.9f, 1f);
		// A wall of water in glass columns: additive ribbons the size of a wave piled up into a white screen (2026-11-26),
		// and the wave is kept narrow so it never sweeps through the camera at the side of the field.
		float half = Math.min(3f, c.move().targetWidth() * 0.5f + 1.6f) * s;
		int n = 9;
		float col = 2 * half / n * 1.05f;
		float yaw = (float) -Math.toDegrees(Math.atan2(dir.z, dir.x)) + 90;
		BlockState water = Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState();
		BlockState foam = Blocks.WHITE_STAINED_GLASS.defaultBlockState();
		Vec3[] crest = new Vec3[n];
		for (int i = 0; i < n; i++) {
			double x = -half + 2 * half * (i + 0.5) / n;
			double edge = 1 - Math.pow(Math.abs(x) / half, 3) * 0.6;
			float colH = (float) (height * fade * edge * (0.85 + 0.15 * Math.sin(x * 1.4 + c.t() * 0.3)));
			Vec3 base = front.add(sd.scale(x));
			box(c, water, base.add(0, colH * 0.45, 0), col, colH * 0.9f, 0.7f * s, yaw);
			Vec3 lip = base.add(dir.scale(0.35 * s)).add(0, colH * 0.92, 0);
			box(c, foam, lip, col, 0.3f * s * fade, 0.55f * s, yaw);
			crest[i] = lip.add(dir.scale(0.3 * s)).add(0, 0.1 * s, 0);
		}
		ribbon(c, crest, 0.18f * s, 0.18f * s, 0xFFFFFF, 200 * fade * rise, 200 * fade * rise);
		for (int i = 0; i < n; i += 2) {
			Draw.halo(c.glow(), c.m(), crest[i].add(0, 0.1 * Math.sin(c.t() * 0.5 + i), 0), c.camera(), 0, 0.35f * s, 0xFFFFFF, op(130 * fade * rise));
		}
	}

	// =====================================================================
	// Strikes
	// =====================================================================

	/** Poison Jab: a venomous spear thrust through the target, poison splattering out of the wound. */
	private static void poisonJab(Context c) {
		float s = c.size();
		Vec3 at = c.aim();
		Vec3 dir = c.toTarget();
		float thrust = ease(seg(c.k(), 0, 0.25f));
		float fade = 1 - seg(c.k(), 0.4f, 0.75f);
		Vec3 tip = at.add(dir.scale((thrust - 0.4) * 1.4 * s));
		Draw.streak(c.glow(), c.m(), tip, tip.subtract(dir.scale(2.6 * s)), c.camera(), 0.35f * s, c.color(), op(230 * fade));
		Draw.streak(c.glow(), c.m(), tip, tip.subtract(dir.scale(2.0 * s)), c.camera(), 0.12f * s, c.accent(), op(250 * fade));
		Draw.halo(c.glow(), c.m(), tip, c.camera(), 0, 0.5f * s, c.accent(), op(230 * fade));
		float q = seg(c.k(), 0.22f, 0.9f);
		if (q <= 0 || q >= 1) {
			return;
		}
		Draw.ring(c.glow(), c.m(), at, dir, (0.3f + 1.6f * q) * s, 0.2f * s, c.color(), op(220 * (1 - q)));
		RandomSource jag = RandomSource.create(151);
		for (int i = 0; i < 12; i++) {
			Vec3 d = dir.scale(0.6).add(randomUnit(jag)).normalize();
			Vec3 drop = at.add(d.scale(q * 2 * s)).add(0, -q * q * 1.6 * s, 0);
			Draw.sphere(c.glow(), c.m(), drop, (0.1f + 0.05f * (i % 3)) * s * (1 - q), c.color(), 200, true);
		}
		for (int i = 0; i < 5; i++) {
			float p = seg(q, i * 0.1f, i * 0.1f + 0.5f);
			Vec3 bubble = at.add(dirOf(i * 1.3).scale(0.5 * s)).add(0, p * 0.8 * s, 0);
			Draw.ring(c.glow(), c.m(), bubble, c.camera().subtract(bubble).normalize(), (0.1f + 0.15f * p) * s, 0.04f * s, c.accent(), op(220 * bell(p)));
		}
	}

	/** Play Rough: a cartoon brawl, a puffing cloud over the target with fists, stars and hearts flying out. */
	private static void playRough(Context c) {
		float s = c.size() * Math.max(1f, c.move().targetHeight() * 0.7f);
		Vec3 at = c.aim();
		Vec3[] b = c.screen(at);
		float fade = c.fade(0.1f, 0.2f);
		RandomSource puffs = RandomSource.create((long) (c.t() / 2) * 7);
		for (int i = 0; i < 10; i++) {
			Vec3 puff = at.add(b[0].scale(puffs.nextGaussian() * 0.5 * s)).add(b[1].scale(puffs.nextGaussian() * 0.4 * s));
			Draw.halo(c.glow(), c.m(), puff, c.camera(), 0, (0.6f + puffs.nextFloat() * 0.4f) * s, i % 3 == 0 ? c.color() : 0xFFF0F8, op(130 * fade));
		}
		RandomSource jag = RandomSource.create((long) (c.t() / 3) * 13);
		for (int i = 0; i < 3; i++) {
			double angle = jag.nextDouble() * Mth.TWO_PI;
			Vec3 d = b[0].scale(Math.cos(angle)).add(b[1].scale(Math.sin(angle)));
			Vec3 out = at.add(d.scale((1.0 + jag.nextDouble() * 0.5) * s));
			switch (i) {
				case 0 -> {
					Draw.streak(c.glow(), c.m(), out, at.add(d.scale(0.5 * s)), c.camera(), 0.25f * s, c.color(), op(230 * fade));
					Draw.halo(c.glow(), c.m(), out, c.camera(), 0, 0.3f * s, 0xFFFFFF, op(230 * fade));
				}
				case 1 -> twinkle(c, out, 0.5f * s, 0xFFE040, op(250 * fade));
				default -> heart(c, out, 0.45f * s, c.color(), 240 * fade);
			}
		}
	}

	/** Outrage: the user raging in red and violet fire, then a rampage of heavy blows on the target. */
	private static void outrage(Context c) {
		float s = c.size();
		if (phase(c).equals("wind")) {
			Entity user = c.move().user();
			float fade = c.fade(0.15f, 0.1f);
			flames(c, user.position(), user.getBbWidth() * 0.6f + 0.3f, user.getBbHeight() * 1.3f, 16, s, fade);
			Draw.halo(c.glow(), c.m(), c.userCentre(), c.camera(), 0, (user.getBbWidth() + 1.6f) * s, c.accent(), op(130 * fade));
			return;
		}
		Vec3 at = c.aim();
		RandomSource jag = RandomSource.create(163);
		for (int j = 0; j < 5; j++) {
			Vec3 spot = at.add(randomUnit(jag).scale(0.5 * s));
			float q = seg(c.k(), j * 0.12f, j * 0.12f + 0.3f);
			if (q > 0 && q < 1) {
				Motifs.impact(c, spot, q, 1.1f + 0.1f * j);
			}
		}
		flames(c, c.move().aimFeet(), c.move().targetWidth() * 0.55f + 0.2f, c.move().targetHeight(), 12, s, 1 - seg(c.k(), 0.7f, 1f));
	}

	/** Brave Bird: the user blazing as a bird of light, wings spread, crashing into the target in a burst of feathers. */
	private static void braveBird(Context c) {
		float s = c.size();
		if (!phase(c).equals("hit")) {
			Entity user = c.move().user();
			Vec3 centre = c.userCentre();
			float fade = phase(c).equals("wind") ? c.fade(0.2f, 0.05f) : 1;
			Vec3 sd = side(flat(c.camera().subtract(centre)));
			float beat = Mth.sin(c.t() * 0.6f) * 0.3f;
			for (int wing = -1; wing <= 1; wing += 2) {
				for (int f = 0; f < 7; f++) {
					double angle = 1.1 + beat - f * 0.18;
					double len = (2.0 - f * 0.15) * s * Math.max(1, user.getBbHeight() * 0.6);
					Vec3 d = sd.scale(wing * Math.sin(angle)).add(0, Math.cos(angle), 0);
					Draw.streak(c.glow(), c.m(), centre.add(d.scale(len)), centre, c.camera(), 0.16f * s, f == 0 ? 0xFFFFFF : c.color(), op(210 * fade));
				}
			}
			Draw.halo(c.glow(), c.m(), centre, c.camera(), 0, (user.getBbWidth() + 1.5f) * s, c.accent(), op(140 * fade));
			return;
		}
		Vec3 at = c.aim();
		float k = c.k();
		Motifs.impact(c, at, seg(k, 0, 0.6f), 1.6f);
		Draw.ring(c.glow(), c.m(), at, c.toTarget(), (0.5f + 3f * seg(k, 0, 0.7f)) * s, 0.3f * s, c.color(), op(220 * (1 - seg(k, 0, 0.7f))));
		ItemStack feather = new ItemStack(Items.FEATHER);
		RandomSource jag = RandomSource.create(173);
		float fly = seg(k, 0.05f, 1f);
		for (int i = 0; i < 12; i++) {
			Vec3 d = randomUnit(jag).add(0, 0.5, 0).normalize();
			Vec3 pos = at.add(d.scale(ease(fly) * 2.6 * s)).add(0, -fly * fly * 1.2 * s, 0);
			Motifs.item(c, feather, pos, 0.6f * s * (1 - seg(fly, 0.8f, 1f)), c.t() * 12 + i * 30, i * 25, (float) Math.sin(c.t() * 0.3 + i) * 40);
		}
	}

	/** Flare Blitz: the user wrapped in fire, charging, and the target swallowed by an explosion and a column of flame. */
	private static void flareBlitz(Context c) {
		float s = c.size();
		Entity user = c.move().user();
		if (!phase(c).equals("hit")) {
			float fade = phase(c).equals("wind") ? c.fade(0.15f, 0.05f) : 1;
			flames(c, user.position(), user.getBbWidth() * 0.6f + 0.25f, user.getBbHeight() * 1.2f, 18, s, fade);
			Draw.halo(c.glow(), c.m(), c.userCentre(), c.camera(), 0, (user.getBbWidth() + 1.3f) * s, c.color(), op(150 * fade));
			return;
		}
		Vec3 at = c.aim();
		Vec3 feet = c.move().aimFeet();
		float k = c.k();
		blast(c, at, seg(k, 0, 0.75f), 1.4f);
		float col = seg(k, 0.1f, 0.8f);
		if (col > 0 && col < 1) {
			Draw.streak(c.glow(), c.m(), feet, feet.add(0, 4 * s * Math.sin(col * Math.PI), 0), c.camera(), 1.2f * s, c.color(), op(200 * (1 - col)));
			Draw.streak(c.glow(), c.m(), feet, feet.add(0, 3.2 * s * Math.sin(col * Math.PI), 0), c.camera(), 0.4f * s, c.accent(), op(230 * (1 - col)));
		}
		flames(c, feet, c.move().targetWidth() * 0.55f + 0.3f, c.move().targetHeight() * 1.2f, 16, s, 1 - seg(k, 0.7f, 1f));
		float recoil = seg(k, 0.4f, 0.7f);
		if (recoil > 0 && recoil < 1) {
			Draw.halo(c.glow(), c.m(), c.userCentre(), c.camera(), 0, (1 + recoil) * s, c.color(), op(180 * (1 - recoil)));
		}
	}

	/** Sacred Sword: a giant blade of light plunging from above into the target, a pillar and a cross of light. */
	private static void sacredSword(Context c) {
		float s = c.size();
		Vec3 at = c.aim();
		float k = c.k();
		float drop = ease(seg(k, 0, 0.25f));
		float fade = 1 - seg(k, 0.75f, 1f);
		float scale = 3.2f * s * Math.max(1, c.move().targetHeight() * 0.6f);
		float yaw = (float) Math.toDegrees(Math.atan2(c.toTarget().x, c.toTarget().z)) + 90;
		Vec3 centre = at.add(0, scale * 0.35 + (1 - drop) * 5 * s, 0);
		Motifs.item(c, new ItemStack(Items.DIAMOND_SWORD), centre, scale * fade, yaw, 0, 135);
		Vec3 top = centre.add(0, scale * 0.45, 0);
		Draw.streak(c.glow(), c.m(), at, top, c.camera(), 0.5f * s, c.color(), op(150 * fade));
		Draw.streak(c.glow(), c.m(), at, top, c.camera(), 0.15f * s, 0xFFFFFF, op(200 * fade));
		if (drop < 1) {
			return;
		}
		float q = seg(k, 0.25f, 0.9f);
		Vec3 sky = at.add(0, 9, 0);
		Draw.streak(c.glow(), c.m(), at.add(0, -0.5, 0), sky, c.camera(), 1.1f * s, c.accent(), op(140 * (1 - q)));
		Vec3[] b = c.screen(at);
		float cross = (1.2f + 1.5f * ease(Math.min(1, q * 3))) * s;
		line(c, at.subtract(b[0].scale(cross)), at.add(b[0].scale(cross)), 0.12f * s, 0xFFFFFF, 240 * (1 - q));
		line(c, at.subtract(b[1].scale(cross * 1.3)), at.add(b[1].scale(cross * 1.3)), 0.12f * s, 0xFFFFFF, 240 * (1 - q));
		Draw.ring(c.glow(), c.m(), c.move().aimFeet().add(0, 0.06, 0), UP, (0.5f + 3f * q) * s, 0.3f * s, c.accent(), op(220 * (1 - q)));
		Motifs.impact(c, at, q, 1.2f);
	}

	/** Ceaseless Edge: a dark crescent slash, and sharp splinters of shell left scattered round the target. */
	private static void ceaselessEdge(Context c) {
		float s = c.size() * Math.max(1.1f, c.move().targetHeight() * 0.8f);
		Vec3 at = c.aim();
		Vec3[] b = c.screen(at);
		float swing = ease(seg(c.k(), 0, 0.25f));
		float fade = 1 - seg(c.k(), 0.3f, 0.6f);
		Vec3 u = b[0].add(b[1].scale(0.6)).normalize();
		Vec3 v = b[1].subtract(b[0].scale(0.6)).normalize();
		Draw.arc(c.glow(), c.m(), at, u, v, 1.3f * s, -0.4f, 3.5f, swing, 0.5f * s, c.color(), op(240 * fade));
		Draw.arc(c.glow(), c.m(), at, u, v, 1.27f * s, -0.4f, 3.5f, swing, 0.16f * s, c.accent(), op(255 * fade));
		Draw.arc(c.glow(), c.m(), at, u, v, 1.25f * s, -0.4f, 3.5f, swing, 0.05f * s, 0xFFFFFF, op(255 * fade));
		float hit = seg(c.k(), 0.15f, 0.6f);
		if (hit > 0 && hit < 1) {
			Motifs.impact(c, at, hit, 1.1f);
		}
		Vec3 feet = c.move().aimFeet();
		BlockState shard = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
		float sink = seg(c.k(), 0.9f, 1f);
		for (int i = 0; i < 7; i++) {
			float p = seg(c.k(), 0.2f + i * 0.03f, 0.5f + i * 0.03f);
			if (p <= 0) {
				continue;
			}
			Vec3 d = dirOf(Mth.TWO_PI * i / 7 + 0.4);
			Vec3 land = feet.add(d.scale((c.move().targetWidth() * 0.5 + 0.8 + 0.3 * (i % 3)) * c.size()));
			Vec3 pos = p < 1 ? at.lerp(land, p).add(0, Math.sin(p * Math.PI) * 1.2, 0) : land.add(0, (0.15 - sink * 0.4) * c.size(), 0);
			crystalToward(c, shard, pos, 0.22f * c.size(), 0.6f * c.size(), p < 1 ? d.add(0, 1, 0) : d.scale(0.3).add(0, 1, 0));
			if (p >= 1) {
				twinkle(c, pos.add(0, 0.3 * c.size(), 0), 0.4f * c.size(), c.accent(), op(220 * bell(seg(c.k(), 0.5f + i * 0.03f, 0.75f + i * 0.03f))));
			}
		}
	}

	/** Scale Shot: dragon scales fired one after another, spinning, each striking with a flash. */
	private static void scaleShot(Context c) {
		float s = c.size();
		int n = c.count(5);
		Vec3 from = c.mouth();
		Vec3 to = c.aim();
		Vec3 sd = side(flat(to.subtract(from)));
		BlockState scale = Blocks.PURPLE_STAINED_GLASS.defaultBlockState();
		for (int i = 0; i < n; i++) {
			float p = seg(c.k(), i * 0.12f, i * 0.12f + 0.25f);
			if (p <= 0) {
				continue;
			}
			Vec3 end = to.add(sd.scale((i % 3 - 1) * 0.3 * s)).add(0, (i % 2) * 0.3 * s, 0);
			if (p < 1) {
				Vec3 pos = from.lerp(end, p);
				crystalToward(c, scale, pos, 0.5f * s, 0.85f * s, end.subtract(from).add(0, Math.sin(c.t() + i), 0));
				Draw.streak(c.glow(), c.m(), pos, from.lerp(end, Math.max(0, p - 0.3f)), c.camera(), 0.4f * s, c.color(), 220);
				Draw.halo(c.glow(), c.m(), pos, c.camera(), 0, 0.8f * s, c.accent(), 210);
				continue;
			}
			float q = seg(c.k(), i * 0.12f + 0.25f, i * 0.12f + 0.5f);
			if (q < 1) {
				Motifs.impact(c, end, q, 1.1f);
			}
		}
	}

	/** Diamond Storm: diamonds whirling up round the target in a storm, then shattering into glitter. */
	private static void diamondStorm(Context c) {
		float s = c.size();
		Vec3 feet = c.move().aimFeet();
		BlockState diamond = Blocks.DIAMOND_BLOCK.defaultBlockState();
		float shatter = seg(c.k(), 0.6f, 0.75f);
		float h = c.move().targetHeight();
		float w = c.move().targetWidth() * 0.6f + 0.6f * s;
		for (int i = 0; i < 14; i++) {
			float appear = seg(c.k(), i * 0.02f, i * 0.02f + 0.2f);
			if (appear <= 0) {
				continue;
			}
			double angle = Mth.TWO_PI * i / 14 + c.t() * 0.35;
			double y = ((i * 0.37) % 1.0) * h * 1.4 + c.k() * 0.8;
			double r = w * (1.4 - 0.5 * (y / (h * 1.4 + 0.8)));
			Vec3 pos = feet.add(Math.cos(angle) * r, y, Math.sin(angle) * r);
			float size = appear * (1 - shatter);
			crystal(c, diamond, pos, 0.3f * s * size, 0.6f * s * size, c.t() * 20 + i * 40, c.t() * 15);
			Draw.halo(c.glow(), c.m(), pos, c.camera(), 0, 0.4f * s, c.color(), op(120 * size));
		}
		Draw.ring(c.glow(), c.m(), feet.add(0, 0.06, 0), UP, w * 1.4f, 0.3f * s, c.color(), op(160 * (1 - seg(c.k(), 0.6f, 0.9f))));
		float glitter = seg(c.k(), 0.6f, 1f);
		if (glitter > 0 && glitter < 1) {
			Vec3 at = c.aim();
			RandomSource jag = RandomSource.create(181);
			for (int i = 0; i < 16; i++) {
				twinkle(c, at.add(randomUnit(jag).scale((0.5 + 2.8 * ease(glitter)) * s)), 0.35f * s, i % 2 == 0 ? 0xFFFFFF : c.color(),
						op(240 * (1 - glitter)));
			}
			Draw.halo(c.glow(), c.m(), at, c.camera(), 0, 2.2f * s, 0xFFFFFF, op(200 * (1 - glitter)));
		}
	}

	// =====================================================================
	// Drains and seeds
	// =====================================================================

	/** A heart, facing the camera: its outline and a glow inside. */
	static void heart(Context c, Vec3 at, float size, int color, float alpha) {
		if (size <= 0.01f || alpha <= 0) {
			return;
		}
		Vec3[] b = c.screen(at);
		Vec3 last = null;
		for (int i = 0; i <= 24; i++) {
			double t = Mth.TWO_PI * i / 24;
			double x = 16 * Math.pow(Math.sin(t), 3) / 17;
			double y = (13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t)) / 17;
			Vec3 p = at.add(b[0].scale(x * size)).add(b[1].scale(y * size));
			if (last != null) {
				line(c, last, p, size * 0.1f, color, alpha);
			}
			last = p;
		}
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, size * 0.9f, color, op(alpha * 0.6f));
		Draw.halo(c.glow(), c.m(), at.add(b[0].scale(-0.35 * size)).add(b[1].scale(0.3 * size)), c.camera(), 0, size * 0.2f, 0xFFFFFF, op(alpha));
	}

	/** Draining Kiss: a heart floating over to the target, popping into little hearts that float back to the user. */
	private static void drainingKiss(Context c) {
		float s = c.size();
		if (phase(c).equals("travel")) {
			Vec3 pos = c.mouth().lerp(c.aim(), ease(c.k())).add(0, Math.sin(c.k() * Math.PI) * 0.6, 0);
			float beat = 1 + 0.15f * Mth.sin(c.t() * 1.2f);
			heart(c, pos, 0.8f * s * beat, c.color(), 250);
			twinkle(c, pos.add(0, 0.6 * s, 0), 0.3f * s, 0xFFFFFF, 200);
			return;
		}
		Vec3 at = c.aim();
		float pop = seg(c.k(), 0, 0.3f);
		if (pop < 1) {
			heart(c, at, (0.8f + 1f * pop) * s, c.color(), 250 * (1 - pop));
			Draw.ring(c.glow(), c.m(), at, c.camera().subtract(at).normalize(), (0.4f + 1.8f * pop) * s, 0.12f * s, c.accent(), op(220 * (1 - pop)));
			for (int i = 0; i < 6; i++) {
				Vec3[] b = c.screen(at);
				double angle = Mth.TWO_PI * i / 6;
				heart(c, at.add(b[0].scale(Math.cos(angle) * pop * 1.4 * s)).add(b[1].scale(Math.sin(angle) * pop * 1.4 * s)), 0.2f * s, c.color(), 230 * (1 - pop));
			}
		}
		float back = seg(c.k(), 0.3f, 0.95f);
		if (back > 0 && back < 1) {
			Vec3 to = c.userCentre();
			for (int i = 0; i < 5; i++) {
				float q = seg(back, i * 0.08f, i * 0.08f + 0.6f);
				if (q <= 0 || q >= 1) {
					continue;
				}
				Vec3 pos = at.lerp(to, ease(q)).add(0, Math.sin(q * Math.PI) * (0.8 + 0.2 * i) + Math.sin(c.t() * 0.4 + i) * 0.15, 0);
				heart(c, pos, 0.34f * s, c.color(), 250 * bell(q));
			}
			Draw.halo(c.glow(), c.m(), to, c.camera(), 0, (c.move().user().getBbWidth() + 1) * s, c.color(), op(150 * seg(back, 0.6f, 0.85f)));
		}
	}

	/** Giga Drain: streams of green light and leaves pulled out of the target into the user. */
	private static void gigaDrain(Context c) {
		float s = c.size();
		Vec3 from = c.aim();
		Vec3 to = c.userCentre();
		Vec3 sd = side(flat(to.subtract(from)));
		float k = c.k();
		float flash = seg(k, 0, 0.25f);
		if (flash < 1) {
			Draw.halo(c.glow(), c.m(), from, c.camera(), 0, (1 + flash) * 1.2f * s, c.color(), op(200 * (1 - flash)));
		}
		float flow = seg(k, 0.1f, 0.9f);
		if (flow > 0 && flow < 1) {
			for (int j = 0; j < 4; j++) {
				Vec3 mid = from.lerp(to, 0.5).add(sd.scale((j - 1.5) * 0.8 * s)).add(0, 0.6 + 0.3 * (j % 2), 0);
				Vec3[] pts = new Vec3[14];
				for (int i = 0; i < pts.length; i++) {
					double q = Mth.clamp(flow * 1.4 - i * 0.04, 0, 1);
					pts[i] = bezier(from, mid, to, q).add(0, Math.sin(q * 12 + c.t() * 0.5 + j) * 0.1, 0);
				}
				ribbon(c, pts, 0.18f * s, 0.04f * s, j % 2 == 0 ? c.color() : c.accent(), 230 * bell(flow), 40);
				Vec3 leaf = pts[0];
				Motifs.block(c, Blocks.AZALEA_LEAVES.defaultBlockState(), leaf, 0.2f * s * bell(flow), c.t() * 30 + j * 50, c.t() * 20, 0);
			}
		}
		drainBack(c, seg(k, 0.5f, 1f), 4, c.accent(), 0.15f * s);
	}

	/** Leech Seed: seeds landing at the target's feet, sprouting vines that coil up round it. */
	private static void leechSeed(Context c) {
		float s = c.size();
		ItemStack seed = new ItemStack(Items.WHEAT_SEEDS);
		if (phase(c).equals("travel")) {
			for (int i = 0; i < 3; i++) {
				float p = Mth.clamp(c.k() * 1.3f - i * 0.12f, 0, 1);
				if (p <= 0 || p >= 1) {
					continue;
				}
				Vec3 land = c.move().aimFeet().add(dirOf(i * 2.1).scale(0.5 * s));
				Vec3 pos = c.mouth().lerp(land, p).add(0, Math.sin(p * Math.PI) * 2 * s, 0);
				Motifs.item(c, seed, pos, 0.6f * s, c.t() * 30 + i * 60, 0, c.t() * 20);
				Draw.halo(c.glow(), c.m(), pos, c.camera(), 0, 0.3f * s, c.color(), 180);
			}
			return;
		}
		Vec3 feet = c.move().aimFeet();
		float w = c.move().targetWidth() * 0.55f + 0.2f;
		float h = c.move().targetHeight();
		float grow = ease(seg(c.k(), 0.05f, 0.6f));
		float fade = 1 - seg(c.k(), 0.85f, 1f);
		for (int j = 0; j < 3; j++) {
			Vec3[] pts = new Vec3[18];
			for (int i = 0; i < pts.length; i++) {
				double p = (1 - i / 17.0) * grow;
				double angle = p * 9 + j * Mth.TWO_PI / 3;
				pts[i] = feet.add(Math.cos(angle) * w * (1.1 - 0.2 * p), p * h * 1.05, Math.sin(angle) * w * (1.1 - 0.2 * p));
			}
			ribbon(c, pts, 0.1f * s, 0.16f * s, c.color(), 230 * fade, 230 * fade);
			for (int i = 2; i < pts.length; i += 4) {
				Motifs.block(c, Blocks.AZALEA_LEAVES.defaultBlockState(), pts[i], 0.18f * s * fade, i * 40 + j * 70, i * 30, 20);
			}
		}
		float drain = seg(c.k(), 0.6f, 0.95f);
		if (drain > 0 && drain < 1) {
			Draw.halo(c.glow(), c.m(), c.aim(), c.camera(), 0, (w + 0.6f) * s, 0xFF4060, op(150 * bell(drain)));
		}
	}

	// =====================================================================
	// Ice, fire, lightning
	// =====================================================================

	/** Freeze-Dry: a freezing beam, then ice crystals growing up round the target in a cold mist. */
	private static void freezeDry(Context c) {
		float s = c.size();
		if (phase(c).equals("travel")) {
			Vec3 from = c.mouth();
			Vec3 to = c.aim();
			Vec3 end = from.lerp(to, Math.min(1, c.k() * 1.6f));
			Draw.streak(c.glow(), c.m(), end, from, c.camera(), 0.5f * s, c.color(), 170);
			Draw.streak(c.glow(), c.m(), end, from, c.camera(), 0.16f * s, 0xFFFFFF, 240);
			Vec3 axis = end.subtract(from);
			for (int i = 0; i < 8; i++) {
				double p = (i / 8.0 + c.t() * 0.1) % 1.0;
				twinkle(c, from.add(axis.scale(p)).add(0, Math.sin(p * 20) * 0.2, 0), 0.3f * s, 0xFFFFFF, 200);
			}
			return;
		}
		Vec3 feet = c.move().aimFeet();
		BlockState ice = Blocks.PACKED_ICE.defaultBlockState();
		float w = c.move().targetWidth() * 0.5f + 0.35f * s;
		float sink = seg(c.k(), 0.82f, 1f);
		for (int i = 0; i < 10; i++) {
			float p = seg(c.k(), i * 0.03f, i * 0.03f + 0.25f);
			if (p <= 0) {
				continue;
			}
			Vec3 radial = dirOf(Mth.TWO_PI * i / 10 + 0.2);
			float len = (1.1f + 0.3f * (i % 3)) * s;
			Vec3 axis = radial.scale(-0.35).add(0, 1, 0).normalize();
			Vec3 base = feet.add(radial.scale(w + 0.2 * (i % 2)));
			float rise = ease(p) - sink;
			crystalToward(c, ice, base.add(axis.scale(len * (rise - 0.5))), 0.35f * s, len, axis);
		}
		float mist = c.fade(0.1f, 0.3f);
		RandomSource jag = RandomSource.create(191);
		for (int i = 0; i < 8; i++) {
			Vec3 puff = feet.add(jag.nextGaussian() * w, 0.3 + jag.nextDouble() * 0.6, jag.nextGaussian() * w);
			Draw.halo(c.glow(), c.m(), puff, c.camera(), 0, 0.8f * s, 0xE8F8FF, op(90 * mist));
		}
		Motifs.impact(c, c.aim(), seg(c.k(), 0, 0.5f), 1f);
	}

	private static Vec3 orbitOrb(Context c, int i, int n, float r, Vec3 centre) {
		double angle = Mth.TWO_PI * i / n + c.t() * 0.25;
		return centre.add(Math.cos(angle) * r, Math.sin(c.t() * 0.2 + i) * 0.3, Math.sin(angle) * r);
	}

	/** Hidden Power: orbs of the move's element circling the user, then spiralling into the target and bursting. */
	private static void hiddenPower(Context c) {
		float s = c.size();
		int n = 6;
		Entity user = c.move().user();
		float r = user.getBbWidth() * 0.7f + 0.7f * s;
		switch (phase(c)) {
			case "wind" -> {
				float appear = ease(seg(c.k(), 0, 0.5f));
				for (int i = 0; i < n; i++) {
					Vec3 at = orbitOrb(c, i, n, r * appear, c.userCentre());
					orbGlow(c, at, 0.22f * s * appear);
				}
			}
			case "travel" -> {
				for (int i = 0; i < n; i++) {
					Vec3 start = orbitOrb(c, i, n, r, c.userCentre());
					Vec3 pos = start.lerp(c.aim(), ease(c.k())).add(0, Math.sin(c.k() * Math.PI + i) * 0.4, 0);
					orbGlow(c, pos, 0.22f * s);
				}
			}
			default -> {
				Vec3 at = c.aim();
				for (int i = 0; i < n; i++) {
					Vec3 d = dirOf(Mth.TWO_PI * i / n).add(0, Math.sin(i * 1.7) * 0.5, 0).normalize();
					float q = seg(c.k(), i * 0.04f, i * 0.04f + 0.5f);
					if (q > 0 && q < 1) {
						Vec3 spot = at.add(d.scale(0.6 * s));
						Draw.halo(c.glow(), c.m(), spot, c.camera(), 0, (0.3f + 0.9f * q) * s, c.color(), op(220 * (1 - q)));
						Draw.ring(c.glow(), c.m(), spot, c.camera().subtract(spot).normalize(), (0.2f + 0.8f * q) * s, 0.08f * s, c.accent(), op(220 * (1 - q)));
					}
				}
				Motifs.impact(c, at, seg(c.k(), 0, 0.6f), 1.1f);
			}
		}
	}

	static void orbGlow(Context c, Vec3 at, float r) {
		if (r <= 0.01f) {
			return;
		}
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, r * 2.6f, c.color(), 200);
		Draw.sphere(c.glow(), c.m(), at, r, c.accent(), 170, true);
		Draw.sphere(c.glow(), c.m(), at, r * 0.5f, 0xFFFFFF, 220, true);
	}

	/** Weather Ball: an orb rising wrapped in cloud, lobbed onto the target, bursting into cloud and a squall of rain. */
	private static void weatherBall(Context c) {
		float s = c.size();
		Vec3 high = c.userCentre().add(0, c.move().user().getBbHeight() * 0.5 + 1.4 * s, 0);
		switch (phase(c)) {
			case "wind" -> {
				Vec3 at = c.userCentre().lerp(high, ease(c.k()));
				weatherOrb(c, at, 0.4f * s * ease(Math.min(1, c.k() * 2)));
			}
			case "travel" -> {
				Vec3 at = high.lerp(c.aim(), c.k()).add(0, Math.sin(c.k() * Math.PI) * 1.2 * s, 0);
				weatherOrb(c, at, 0.4f * s);
			}
			default -> {
				Vec3 at = c.aim();
				float q = c.k();
				Motifs.burst(c, at, q, 1.1f);
				for (int i = 0; i < 8; i++) {
					double angle = i * 0.8;
					Vec3 puff = at.add(Math.cos(angle) * (0.5 + q * 1.5) * s, 0.4 * s + q * 0.6, Math.sin(angle) * (0.5 + q * 1.5) * s);
					Draw.halo(c.glow(), c.m(), puff, c.camera(), 0, (0.6f + 0.4f * q) * s, 0xE0E8F0, op(140 * (1 - q)));
				}
				RandomSource jag = RandomSource.create((long) (c.t() / 2) * 5);
				for (int i = 0; i < 14 && q < 0.85f; i++) {
					Vec3 drop = c.move().aimFeet().add(jag.nextGaussian() * 1.2 * s, 0.5 + jag.nextDouble() * 2.5 * s, jag.nextGaussian() * 1.2 * s);
					Draw.streak(c.glow(), c.m(), drop, drop.add(0, 0.6 * s, 0), c.camera(), 0.04f * s, 0x9FC8FF, 220);
				}
			}
		}
	}

	private static void weatherOrb(Context c, Vec3 at, float r) {
		if (r <= 0.01f) {
			return;
		}
		orbGlow(c, at, r);
		for (int i = 0; i < 6; i++) {
			double angle = c.t() * 0.4 + i * Mth.TWO_PI / 6;
			Vec3 puff = at.add(Math.cos(angle) * r * 1.6, Math.sin(angle * 2) * r * 0.3, Math.sin(angle) * r * 1.6);
			Draw.halo(c.glow(), c.m(), puff, c.camera(), 0, r * 0.9f, 0xF0F4FF, 150);
		}
	}

	/** Thunderclap: a bolt out of the blue straight onto the target, and the clap: a flash and rings of shock. */
	private static void thunderclap(Context c) {
		float s = c.size();
		if (phase(c).equals("wind")) {
			sparks(c, c.userCentre(), c.move().user().getBbWidth() * 0.6f + 0.3f, 3, 0.04f * s);
			return;
		}
		Vec3 at = c.aim();
		Vec3 feet = c.move().aimFeet();
		float k = c.k();
		float fade = 1 - seg(k, 0.25f, 0.6f);
		if (fade > 0) {
			Vec3 sky = feet.add(0.6, 10, -0.4);
			for (int i = 0; i < 3; i++) {
				Motifs.bolt(c.glow(), c.m(), c.camera(), sky.add(i * 0.4 - 0.4, 0, 0), feet, c.t() + i, (0.16f - i * 0.04f) * s * fade, i == 0 ? 0xFFFFFF : c.color(), i * 7);
			}
		}
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, 3 * s, 0xFFFFFF, op(230 * (1 - seg(k, 0, 0.25f))));
		for (int i = 0; i < 2; i++) {
			float q = seg(k, i * 0.1f, i * 0.1f + 0.6f);
			if (q > 0 && q < 1) {
				Draw.ring(c.glow(), c.m(), feet.add(0, 0.07, 0), UP, (0.5f + 4f * q) * s, 0.35f * s, c.color(), op(220 * (1 - q)));
				Draw.ring(c.glow(), c.m(), at, c.camera().subtract(at).normalize(), (0.5f + 2.5f * q) * s, 0.15f * s, c.accent(), op(220 * (1 - q)));
			}
		}
		if (k < 0.7f) {
			sparks(c, at, c.move().targetWidth() * 0.6f + 0.3f * s, 4, 0.05f * s);
		}
	}

	// =====================================================================
	// Status
	// =====================================================================

	/** Iron Defense: plates of iron flying in and locking round the user as armour, a gleam sweeping over them. */
	private static void ironDefense(Context c) {
		Entity user = c.move().user();
		float s = c.size();
		float w = user.getBbWidth() * 0.6f + 0.3f;
		float h = user.getBbHeight();
		Vec3 base = user.position();
		float gone = seg(c.k(), 0.88f, 1f);
		BlockState iron = Blocks.IRON_BLOCK.defaultBlockState();
		for (int i = 0; i < 8; i++) {
			float p = ease(seg(c.k(), i * 0.04f, i * 0.04f + 0.3f));
			if (p <= 0) {
				continue;
			}
			double angle = Mth.TWO_PI * (i % 4) / 4 + (i / 4) * Math.PI / 4;
			Vec3 d = dirOf(angle);
			double y = h * (i < 4 ? 0.3 : 0.7);
			Vec3 home = base.add(d.scale(w)).add(0, y, 0);
			Vec3 pos = home.add(d.scale((1 - p) * 3 * s)).add(0, (1 - p) * 1.5, 0);
			float yaw = (float) -Math.toDegrees(angle) + 90;
			box(c, iron, pos, 0.75f * s * (1 - gone), h * 0.42f * (1 - gone), 0.08f * s, yaw);
		}
		float sweep = seg(c.k(), 0.45f, 0.75f);
		if (sweep > 0 && sweep < 1) {
			Vec3 centre = c.userCentre();
			Vec3[] b = c.screen(centre);
			Vec3 g = centre.add(b[0].scale((sweep * 2 - 1) * (w + 0.4) * s));
			line(c, g.subtract(b[1].scale(h * 0.6)).subtract(b[0].scale(0.2 * s)), g.add(b[1].scale(h * 0.6)).add(b[0].scale(0.2 * s)), 0.18f * s, 0xFFFFFF, 230 * bell(sweep));
		}
		float shine = seg(c.k(), 0.75f, 0.95f);
		if (shine > 0 && shine < 1) {
			Draw.ring(c.glow(), c.m(), base.add(0, h * 0.5, 0), UP, (w + 0.3f + 1.5f * shine) * s, 0.15f * s, c.color(), op(220 * (1 - shine)));
			twinkle(c, base.add(0, h + 0.3, 0), 0.8f * s, 0xFFFFFF, op(240 * bell(shine)));
		}
	}

	/** Psychic Noise: warped rings of sound, and over the target's head a healing cross struck through. */
	private static void psychicNoise(Context c) {
		float s = c.size();
		Vec3 from = c.mouth();
		Vec3 to = c.aim();
		if (phase(c).equals("travel")) {
			Vec3 axis = to.subtract(from);
			Vec3[] b = Draw.basis(axis.normalize());
			for (int i = 0; i < 4; i++) {
				float p = (c.k() * 1.2f + i * 0.25f) % 1f;
				float r = (0.3f + 0.5f * p) * s;
				zigRing(c, from.add(axis.scale(p)), b, r, 0.12f * r, 18, i % 2 == 0 ? c.color() : c.accent(), 230);
			}
			return;
		}
		float fade = c.fade(0.08f, 0.25f);
		Vec3[] flat = Draw.basis(UP);
		Vec3 head = to.add(0, c.move().targetHeight() * 0.4, 0);
		for (int i = 0; i < 3; i++) {
			float p = ((c.t() * 0.05f) + i / 3f) % 1f;
			float r = (c.move().targetWidth() * 0.5f + 0.4f + 1.2f * p) * s;
			zigRing(c, head.add(0, -0.3 * i, 0), flat, r, 0.15f * r, 20, c.color(), 220 * fade * (1 - p));
		}
		Vec3 mark = head.add(0, 0.9 * s, 0);
		Vec3[] b = c.screen(mark);
		float m = 0.35f * s;
		line(c, mark.subtract(b[0].scale(m)), mark.add(b[0].scale(m)), 0.12f * s, 0x60FF80, 240 * fade);
		line(c, mark.subtract(b[1].scale(m)), mark.add(b[1].scale(m)), 0.12f * s, 0x60FF80, 240 * fade);
		Vec3 d1 = b[0].add(b[1]).normalize().scale(m * 1.5);
		Vec3 d2 = b[0].subtract(b[1]).normalize().scale(m * 1.5);
		float strike = seg(c.k(), 0.2f, 0.4f);
		line(c, mark.subtract(d1), mark.subtract(d1).lerp(mark.add(d1), strike), 0.1f * s, 0xFF3040, 250 * fade);
		line(c, mark.subtract(d2), mark.subtract(d2).lerp(mark.add(d2), strike), 0.1f * s, 0xFF3040, 250 * fade);
		Draw.halo(c.glow(), c.m(), mark, c.camera(), 0, m * 2.5f, 0xFF3040, op(90 * fade * strike));
	}

	/** Bug Buzz: waves of buzzing sound fanning out to the target, the air round it trembling. */
	private static void bugBuzz(Context c) {
		float s = c.size();
		Vec3 from = c.mouth();
		Vec3 to = c.aim();
		Vec3 dir = to.subtract(from).normalize();
		Vec3[] b = Draw.basis(dir);
		if (phase(c).equals("travel")) {
			double dist = from.distanceTo(to);
			for (int i = 0; i < 6; i++) {
				float p = (c.k() * 1.5f + i / 6f) % 1f;
				Vec3 centre = from.add(dir.scale(p * dist));
				float r = (0.3f + 1.2f * p) * s;
				float jitter = 0.05f * Mth.sin(c.t() * 3 + i);
				Draw.arc(c.glow(), c.m(), centre.subtract(dir.scale(r * 0.6)), b[0], dir, r + jitter, -0.9f, 0.9f, 1f, 0.12f * s, c.color(), op(230 * (1 - p * 0.5f)));
				Draw.arc(c.glow(), c.m(), centre.subtract(dir.scale(r * 0.6)), b[1], dir, r + jitter, -0.9f, 0.9f, 1f, 0.07f * s, c.accent(), op(200 * (1 - p * 0.5f)));
			}
			return;
		}
		float fade = c.fade(0.05f, 0.3f);
		Vec3[] sb = c.screen(to);
		RandomSource jag = RandomSource.create((long) c.t() * 3);
		float r = c.move().targetWidth() * 0.6f + 0.4f * s;
		for (int i = 0; i < 12; i++) {
			double angle = Mth.TWO_PI * i / 12;
			Vec3 d = sb[0].scale(Math.cos(angle)).add(sb[1].scale(Math.sin(angle)));
			Vec3 p = to.add(d.scale(r + jag.nextFloat() * 0.15)).add(sb[0].scale(jag.nextGaussian() * 0.05));
			Draw.streak(c.glow(), c.m(), p, p.add(d.scale(0.4 * s)), c.camera(), 0.06f * s, c.color(), op(230 * fade));
		}
		Motifs.impact(c, to, seg(c.k(), 0, 0.6f), 1f);
	}

	/** A musical note: a round head, a stem and a flag, facing the camera. */
	static void note(Context c, Vec3 at, float size, int color, float alpha) {
		Vec3[] b = c.screen(at);
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, size * 0.3f, color, op(alpha));
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, size * 0.14f, 0xFFFFFF, op(alpha));
		Vec3 stem = at.add(b[0].scale(size * 0.2));
		Vec3 top = stem.add(b[1].scale(size));
		line(c, stem, top, size * 0.07f, color, alpha);
		line(c, top, top.add(b[0].scale(size * 0.35)).subtract(b[1].scale(size * 0.3)), size * 0.07f, color, alpha);
	}

	/** Encore: a spotlight falling on the target, notes bouncing round it: do it again! */
	private static void encore(Context c) {
		float s = c.size();
		Vec3 feet = c.move().aimFeet();
		Vec3 at = c.aim();
		float fade = c.fade(0.1f, 0.2f);
		float w = c.move().targetWidth() * 0.6f + 0.5f * s;
		Vec3 lamp = feet.add(-1.5, 7, 0.5);
		Draw.streak(c.glow(), c.m(), feet.add(0, 0.2, 0), lamp, c.camera(), w * 2.2f, 0xFFF4C0, op(70 * fade));
		Draw.streak(c.glow(), c.m(), feet.add(0, 0.2, 0), lamp, c.camera(), w * 1.1f, 0xFFF4C0, op(60 * fade));
		Draw.ring(c.glow(), c.m(), feet.add(0, 0.06, 0), UP, w, 0.3f * s, 0xFFF4C0, op(180 * fade));
		for (int i = 0; i < 4; i++) {
			double angle = c.t() * 0.15 + i * Mth.TWO_PI / 4;
			double bounce = Math.abs(Math.sin(c.t() * 0.3 + i * 1.3)) * 0.4;
			Vec3 pos = at.add(Math.cos(angle) * (w + 0.2), 0.3 + bounce, Math.sin(angle) * (w + 0.2));
			note(c, pos, 0.6f * s, i % 2 == 0 ? c.color() : c.accent(), 240 * fade);
		}
		twinkle(c, at.add(0, c.move().targetHeight() * 0.6 + 0.4, 0), 0.6f * s, 0xFFFFFF, op(230 * bell((c.t() * 0.06f) % 1f) * fade));
	}

	/** Trick: two held items swapped, crossing in mid air on arcs, a puff of smoke at each end. */
	private static void trick(Context c) {
		float s = c.size();
		Vec3 a = c.userCentre();
		Vec3 b = c.aim();
		Vec3 sd = side(flat(b.subtract(a)));
		int mine = Math.floorMod(c.move().user().getId() + 2, KNOCKED.length);
		int theirs = Math.floorMod(c.move().target == null ? 0 : c.move().target.getId(), KNOCKED.length);
		if (theirs == mine) {
			theirs = (theirs + 1) % KNOCKED.length;
		}
		float p = ease(seg(c.k(), 0.1f, 0.75f));
		for (int i = 0; i < 2; i++) {
			Vec3 from = i == 0 ? a : b;
			Vec3 to = i == 0 ? b : a;
			Vec3 mid = from.lerp(to, 0.5).add(sd.scale(i == 0 ? 1.2 : -1.2)).add(0, 1.2, 0);
			Vec3 pos = bezier(from, mid, to, p);
			Item item = KNOCKED[i == 0 ? mine : theirs];
			if (p > 0 && p < 1) {
				Motifs.item(c, new ItemStack(item), pos, 1.4f * s, c.t() * 25, 0, c.t() * 15);
				Draw.streak(c.glow(), c.m(), pos, bezier(from, mid, to, Math.max(0, p - 0.15f)), c.camera(), 0.15f * s, c.color(), 180);
			}
		}
		for (float at : new float[] { 0.1f, 0.75f }) {
			float q = seg(c.k(), at, at + 0.2f);
			if (q > 0 && q < 1) {
				for (Vec3 end : new Vec3[] { a, b }) {
					Draw.halo(c.glow(), c.m(), end, c.camera(), 0, (0.6f + q) * s, 0xF0E8FF, op(170 * (1 - q)));
					twinkle(c, end.add(0, 0.5 * s, 0), 0.4f * s, c.accent(), op(220 * (1 - q)));
				}
			}
		}
	}

	/** Quiver Dance: glowing butterfly wings fluttering behind the user, scales of light rising round it. */
	private static void quiverDance(Context c) {
		Entity user = c.move().user();
		float s = c.size();
		Vec3 centre = c.userCentre().add(0, user.getBbHeight() * 0.15, 0);
		Vec3 toCamera = flat(c.camera().subtract(centre));
		Vec3 sd = side(toCamera);
		Vec3 back = centre.subtract(toCamera.scale(user.getBbWidth() * 0.4));
		float fade = c.fade(0.12f, 0.2f);
		float flap = 0.55f + 0.45f * Mth.cos(c.t() * 0.7f);
		float size = Math.max(1f, user.getBbHeight() * 0.7f) * s;
		for (int wing = -1; wing <= 1; wing += 2) {
			for (int lobe = 0; lobe < 2; lobe++) {
				double up = lobe == 0 ? 0.45 : -0.35;
				double out = (lobe == 0 ? 0.75 : 0.55) * flap;
				Vec3 c0 = back.add(sd.scale(wing * out * size)).add(0, up * size, 0);
				float r = (lobe == 0 ? 0.55f : 0.4f) * size;
				Vec3 u = sd.scale(flap);
				Draw.halo(c.glow(), c.m(), c0, c.camera(), 0, r, lobe == 0 ? c.color() : c.accent(), op(150 * fade));
				Draw.arc(c.glow(), c.m(), c0, u.lengthSqr() < 1.0E-4 ? sd : u.normalize(), UP, r, 0, Mth.TWO_PI, 1f, 0.05f * s, 0xFFFFFF, op(220 * fade));
			}
		}
		for (int i = 0; i < 12; i++) {
			float p = ((c.t() * 0.04f) + i / 12f) % 1f;
			double angle = i * 2.4 + c.t() * 0.1;
			double r = user.getBbWidth() * 0.6 + 0.6 * s;
			Vec3 at = user.position().add(Math.cos(angle) * r, p * user.getBbHeight() * 1.6, Math.sin(angle) * r);
			twinkle(c, at, 0.25f * s, i % 2 == 0 ? c.accent() : 0xFFFFFF, op(230 * fade * bell(p)));
		}
	}

	/** Dazzling Gleam: a fan of prismatic light flashing out from the user across the target. */
	private static void dazzlingGleam(Context c) {
		float s = c.size();
		Vec3 from = c.userCentre();
		Vec3 to = c.aim();
		Vec3 dir = to.subtract(from);
		Vec3[] b = Draw.basis(dir.normalize());
		float k = c.k();
		float reach = ease(seg(k, 0, 0.35f));
		float fade = 1 - seg(k, 0.5f, 0.9f);
		Draw.halo(c.glow(), c.m(), from, c.camera(), 0, 1.6f * s, 0xFFFFFF, op(230 * (1 - seg(k, 0, 0.3f))));
		for (int i = 0; i < 12; i++) {
			double angle = Mth.TWO_PI * i / 12;
			Vec3 spread = b[0].scale(Math.cos(angle) * 1.4 * s).add(b[1].scale(Math.sin(angle) * 1.0 * s));
			Vec3 end = from.add(dir.scale(reach * 1.15)).add(spread.scale(reach));
			int color = i % 3 == 0 ? 0xFFFFFF : i % 3 == 1 ? c.color() : c.accent();
			Draw.streak(c.glow(), c.m(), end, from, c.camera(), 0.2f * s, color, op(220 * fade));
			twinkle(c, end, 0.4f * s, color, op(240 * fade));
		}
		float star = seg(k, 0.3f, 0.9f);
		if (star > 0 && star < 1) {
			Vec3[] sb = c.screen(to);
			for (int i = 0; i < 8; i++) {
				double angle = Math.PI / 4 * i;
				Vec3 d = sb[0].scale(Math.cos(angle)).add(sb[1].scale(Math.sin(angle)));
				Draw.streak(c.glow(), c.m(), to, to.add(d.scale((i % 2 == 0 ? 2.4 : 1.2) * s * ease(Math.min(1, star * 3)))), c.camera(), 0.15f * s, 0xFFFFFF,
						op(240 * (1 - star)));
			}
			Draw.halo(c.glow(), c.m(), to, c.camera(), 0, 2 * s, c.color(), op(200 * (1 - star)));
		}
	}

	/** Chilly Reception: a snow cloud gathering over the field, snow falling, the user slipping away in a swirl. */
	private static void chillyReception(Context c) {
		float s = c.size();
		Vec3 mid = c.userCentre().lerp(c.aim(), 0.5).add(0, 3.2 * s, 0);
		float fade = c.fade(0.2f, 0.2f);
		float gather = ease(seg(c.k(), 0, 0.4f));
		for (int i = 0; i < 12; i++) {
			double angle = i * 0.52 + c.t() * 0.03;
			double r = (1 + 2 * ((i * 0.37) % 1.0)) * s * gather;
			Vec3 puff = mid.add(Math.cos(angle) * r * 1.4, Math.sin(i * 1.7) * 0.3, Math.sin(angle) * r * 0.8);
			Draw.halo(c.glow(), c.m(), puff, c.camera(), 0, 1.1f * s, 0xDDE6EE, op(110 * fade));
		}
		RandomSource jag = RandomSource.create(199);
		for (int i = 0; i < 24; i++) {
			double x = jag.nextGaussian() * 2.2 * s;
			double z = jag.nextGaussian() * 1.4 * s;
			float p = ((c.t() * 0.03f) + jag.nextFloat()) % 1f;
			Vec3 flake = mid.add(x + Math.sin(c.t() * 0.1 + i) * 0.2, -p * 3.4 * s, z);
			twinkle(c, flake, 0.15f * s, 0xFFFFFF, op(220 * fade * gather));
		}
		Entity user = c.move().user();
		float leave = seg(c.k(), 0.5f, 0.9f);
		if (leave > 0 && leave < 1) {
			Vec3[] pts = new Vec3[18];
			for (int i = 0; i < pts.length; i++) {
				double p = i / 17.0;
				double angle = -c.t() * 0.6 - p * 8;
				double r = (user.getBbWidth() * 0.7 + 0.3) * (1 - leave * 0.6);
				pts[i] = user.position().add(Math.cos(angle) * r, p * user.getBbHeight() * 1.3, Math.sin(angle) * r);
			}
			ribbon(c, pts, 0.12f * s, 0.12f * s, c.color(), 230 * bell(leave), 100 * bell(leave));
		}
	}

	/** Mirror Coat: a shield of mirror panes round the user, a gleam over them, light flashed back toward the foe. */
	private static void mirrorCoat(Context c) {
		Entity user = c.move().user();
		float s = c.size();
		Vec3 centre = c.userCentre();
		Vec3 dir = flat(c.toTarget());
		float r = user.getBbWidth() * 0.6f + 0.8f * s;
		float appear = ease(seg(c.k(), 0, 0.3f));
		float gone = seg(c.k(), 0.85f, 1f);
		BlockState glass = Blocks.WHITE_STAINED_GLASS.defaultBlockState();
		double base = Math.atan2(dir.z, dir.x);
		for (int i = 0; i < 6; i++) {
			double angle = base + (i - 2.5) * 0.38;
			Vec3 d = dirOf(angle);
			Vec3 pos = centre.add(d.scale(r * appear)).add(0, (i % 2) * 0.35 * s - 0.15 * s, 0);
			float yaw = (float) -Math.toDegrees(angle) + 90;
			box(c, glass, pos, 0.62f * s * appear * (1 - gone), 1.1f * s * appear * (1 - gone), 0.05f * s, yaw);
		}
		float sweep = seg(c.k(), 0.3f, 0.55f);
		if (sweep > 0 && sweep < 1) {
			double angle = base + (sweep * 2 - 1) * 1.1;
			Vec3 g = centre.add(dirOf(angle).scale(r + 0.05));
			line(c, g.subtract(0, 0.7 * s, 0), g.add(0, 0.7 * s, 0), 0.15f * s, 0xFFFFFF, 240 * bell(sweep));
		}
		float shoot = seg(c.k(), 0.55f, 0.85f);
		if (shoot > 0 && shoot < 1) {
			Vec3 from = centre.add(dir.scale(r));
			Vec3 to = from.lerp(c.aim(), ease(shoot));
			Draw.streak(c.glow(), c.m(), to, from, c.camera(), 0.4f * s, c.color(), op(200 * bell(shoot)));
			Draw.streak(c.glow(), c.m(), to, from, c.camera(), 0.12f * s, 0xFFFFFF, op(240 * bell(shoot)));
		}
	}

	/** Hex: a curse circle under the target, violet ghost flames, eyes in the dark closing in. */
	private static void hex(Context c) {
		float s = c.size();
		Vec3 feet = c.move().aimFeet();
		Vec3 at = c.aim();
		float w = c.move().targetWidth() * 0.55f + 0.25f;
		float fade = c.fade(0.08f, 0.3f);
		magicCircle(c, feet.add(0, 0.08, 0), UP, (w + 0.6f) * s * ease(seg(c.k(), 0, 0.3f)), fade);
		flames(c, feet, w, c.move().targetHeight(), 12, s, fade);
		float close = ease(seg(c.k(), 0.1f, 0.8f));
		Vec3[] b = c.screen(at);
		for (int i = 0; i < 3; i++) {
			double angle = Mth.TWO_PI * i / 3 + 0.5 + c.t() * 0.05;
			double r = (2.2 - 1.2 * close) * s;
			Vec3 eye = at.add(b[0].scale(Math.cos(angle) * r)).add(b[1].scale(Math.sin(angle) * r * 0.7));
			float open = 0.5f + 0.5f * Mth.sin(c.t() * 0.4f + i);
			for (int side = -1; side <= 1; side += 2) {
				Vec3 e = eye.add(b[0].scale(side * 0.16 * s));
				line(c, e.subtract(b[0].scale(0.1 * s)), e.add(b[0].scale(0.1 * s)), 0.04f * s + 0.05f * s * open, c.accent(), 240 * fade);
				Draw.halo(c.glow(), c.m(), e, c.camera(), 0, 0.15f * s, c.accent(), op(200 * fade));
			}
		}
	}

	/** Poltergeist: the target's own item lifted by ghost wisps, whirled overhead, then hurled down into it. */
	private static void poltergeist(Context c) {
		float s = c.size();
		Vec3 at = c.aim();
		int pick = c.move().target == null ? 0 : c.move().target.getId();
		ItemStack item = new ItemStack(KNOCKED[Math.floorMod(pick, KNOCKED.length)]);
		Vec3 high = at.add(0, c.move().targetHeight() * 0.6 + 1.4 * s, 0);
		float lift = ease(seg(c.k(), 0, 0.3f));
		float slam = seg(c.k(), 0.55f, 0.65f);
		Vec3 pos = slam > 0 ? high.lerp(at, slam * slam) : at.lerp(high, lift).add(Math.sin(c.t() * 0.5) * 0.2, 0, Math.cos(c.t() * 0.5) * 0.2);
		if (slam < 1) {
			Motifs.item(c, item, pos, 1.1f * s, c.t() * (20 + 40 * slam), c.t() * 10, 0);
			for (int i = 0; i < 3; i++) {
				double angle = c.t() * 0.4 + i * Mth.TWO_PI / 3;
				Vec3 w = pos.add(Math.cos(angle) * 0.7 * s, Math.sin(c.t() * 0.3 + i) * 0.2, Math.sin(angle) * 0.7 * s);
				wisp(c, w, 0.22f * s, 1 - slam);
			}
		}
		float q = seg(c.k(), 0.62f, 1f);
		if (q > 0 && q < 1) {
			Motifs.impact(c, at, q, 1.2f);
			Draw.halo(c.glow(), c.m(), at, c.camera(), 0, (1 + q) * 1.4f * s, c.color(), op(180 * (1 - q)));
		}
	}

	/** Rest: the user dozing off, Z's drifting up, a crescent moon over it, a soft glow of recovery. */
	private static void rest(Context c) {
		Entity user = c.move().user();
		float s = c.size();
		Vec3 head = user.position().add(0, user.getBbHeight() * 0.9, 0);
		float fade = c.fade(0.1f, 0.2f);
		Draw.halo(c.glow(), c.m(), c.userCentre(), c.camera(), 0, (user.getBbWidth() + 1.2f) * s, c.color(), op(90 * fade));
		Vec3[] b = c.screen(head);
		for (int i = 0; i < 3; i++) {
			float p = ((c.t() * 0.025f) + i / 3f) % 1f;
			float size = (0.25f + 0.35f * p) * s;
			Vec3 z = head.add(b[0].scale((0.3 + p * 0.8) * s)).add(b[1].scale(p * 1.6 * s));
			Vec3 tl = z.add(b[0].scale(-size)).add(b[1].scale(size));
			Vec3 tr = z.add(b[0].scale(size)).add(b[1].scale(size));
			Vec3 bl = z.add(b[0].scale(-size)).add(b[1].scale(-size));
			Vec3 br = z.add(b[0].scale(size)).add(b[1].scale(-size));
			float a = 240 * fade * bell(p);
			line(c, tl, tr, 0.08f * s, 0xFFFFFF, a);
			line(c, tr, bl, 0.08f * s, 0xFFFFFF, a);
			line(c, bl, br, 0.08f * s, 0xFFFFFF, a);
		}
		Vec3 moon = head.add(b[0].scale(-0.6 * s)).add(b[1].scale(1.2 * s));
		Draw.arc(c.glow(), c.m(), moon, b[0], b[1], 0.4f * s, 1.9f, 4.4f, 1f, 0.16f * s, 0xFFF0A0, op(230 * fade));
		Draw.halo(c.glow(), c.m(), moon, c.camera(), 0, 0.6f * s, 0xFFF0A0, op(90 * fade));
		float heal = seg(c.k(), 0.6f, 0.95f);
		if (heal > 0 && heal < 1) {
			for (int i = 0; i < 6; i++) {
				double angle = i * 1.05;
				Vec3 at = user.position().add(Math.cos(angle) * user.getBbWidth() * 0.7, heal * user.getBbHeight() * 1.3, Math.sin(angle) * user.getBbWidth() * 0.7);
				twinkle(c, at, 0.3f * s, 0xB0FFB0, op(230 * bell(heal)));
			}
		}
	}

	/** Teleport: rings of light rising up the user, a flash, the light pulled to a point and gone upward. */
	private static void teleport(Context c) {
		Entity user = c.move().user();
		float s = c.size();
		Vec3 base = user.position();
		float h = user.getBbHeight();
		float r = user.getBbWidth() * 0.7f + 0.3f * s;
		for (int i = 0; i < 4; i++) {
			float p = seg(c.k(), i * 0.08f, i * 0.08f + 0.45f);
			if (p > 0 && p < 1) {
				Draw.ring(c.glow(), c.m(), base.add(0, p * h * 1.2, 0), UP, r * (1 - 0.3f * p), 0.1f * s, i % 2 == 0 ? c.color() : c.accent(), op(230 * bell(p)));
			}
		}
		float flash = seg(c.k(), 0.4f, 0.6f);
		if (flash > 0 && flash < 1) {
			Draw.halo(c.glow(), c.m(), c.userCentre(), c.camera(), 0, (r + 1.2f * flash) * s * 1.5f, 0xFFFFFF, op(230 * bell(flash)));
		}
		float zip = seg(c.k(), 0.55f, 0.85f);
		if (zip > 0 && zip < 1) {
			Vec3 point = base.add(0, h + zip * 6, 0);
			Draw.streak(c.glow(), c.m(), point, point.subtract(0, 1.8 * s, 0), c.camera(), 0.25f * s, c.color(), op(230 * (1 - zip)));
			twinkle(c, point, 0.6f * s, 0xFFFFFF, op(240 * (1 - zip)));
		}
	}

	/** Trick Room: a wireframe room unfolding round the whole field, twisting as it settles, a grid on its floor. */
	private static void trickRoom(Context c) {
		float s = c.size();
		Vec3 a = c.move().user().position();
		Vec3 b = c.move().aimFeet();
		Vec3 centre = a.lerp(b, 0.5);
		float half = (float) (flatDist(a, b) * 0.5 + 2.5);
		float grow = ease(seg(c.k(), 0, 0.45f));
		float fade = 1 - seg(c.k(), 0.85f, 1f);
		float size = half * grow;
		float height = 4 * s * grow;
		double twist = (1 - grow) * Math.PI / 2 + Math.atan2(b.z - a.z, b.x - a.x);
		Vec3[] corners = new Vec3[8];
		for (int i = 0; i < 8; i++) {
			double angle = twist + Math.PI / 4 + (i % 4) * Math.PI / 2;
			corners[i] = centre.add(Math.cos(angle) * size * 1.414, i < 4 ? 0.05 : height, Math.sin(angle) * size * 1.414);
		}
		int color = c.color();
		for (int i = 0; i < 4; i++) {
			line(c, corners[i], corners[(i + 1) % 4], 0.12f * s, color, 230 * fade);
			line(c, corners[i + 4], corners[(i + 1) % 4 + 4], 0.12f * s, color, 230 * fade);
			line(c, corners[i], corners[i + 4], 0.12f * s, c.accent(), 230 * fade);
		}
		for (int i = 1; i < 6; i++) {
			float f = i / 6f;
			line(c, corners[0].lerp(corners[1], f), corners[3].lerp(corners[2], f), 0.04f * s, color, 120 * fade);
			line(c, corners[0].lerp(corners[3], f), corners[1].lerp(corners[2], f), 0.04f * s, color, 120 * fade);
		}
		for (int i = 0; i < 8 && grow < 1; i++) {
			twinkle(c, corners[i], 0.5f * s, 0xFFFFFF, op(240 * fade));
		}
	}

	/** Whirlwind: a sideways tunnel of wind blasting through the target and on past it, sweeping it away. */
	private static void whirlwind(Context c) {
		float s = c.size();
		Vec3 from = c.userCentre();
		Vec3 to = c.aim();
		Vec3 dir = to.subtract(from).normalize();
		Vec3[] b = Draw.basis(dir);
		float fade = c.fade(0.1f, 0.3f);
		double length = from.distanceTo(to) + 4;
		for (int j = 0; j < 4; j++) {
			Vec3[] pts = new Vec3[30];
			for (int i = 0; i < pts.length; i++) {
				double p = i / 29.0;
				double angle = -c.t() * 0.8 + p * 16 + j * Math.PI / 2;
				double r = (0.5 + 0.9 * p) * s;
				pts[i] = from.add(dir.scale(p * length)).add(b[0].scale(Math.cos(angle) * r)).add(b[1].scale(Math.sin(angle) * r));
			}
			ribbon(c, pts, 0.1f * s, 0.2f * s, j % 2 == 0 ? 0xFFFFFF : c.color(), 160 * fade, 220 * fade);
		}
		float push = seg(c.k(), 0.3f, 0.9f);
		if (push > 0 && push < 1) {
			Vec3 away = to.add(dir.scale(push * 3 * s));
			Draw.ring(c.glow(), c.m(), away, dir, (0.6f + push) * s, 0.15f * s, c.accent(), op(220 * (1 - push)));
		}
	}

	private static float sludgeRadius(Context c) {
		return 0.6f + c.k() * 10f;
	}

	/** Sludge Wave: a ring of toxic sludge swelling out from the user across the whole field, splashing the target. */
	private static void sludgeWave(Context c) {
		float s = c.size();
		if (phase(c).equals("hit")) {
			Vec3 at = c.aim();
			float q = c.k();
			RandomSource jag = RandomSource.create(211);
			for (int i = 0; i < 14; i++) {
				Vec3 d = randomUnit(jag).add(0, 0.6, 0).normalize();
				Vec3 drop = at.add(d.scale((0.3 + 1.8 * q) * s)).add(0, -1.8 * q * q * s, 0);
				Draw.sphere(c.glow(), c.m(), drop, 0.14f * s * (1 - q), c.color(), 200, true);
			}
			Draw.halo(c.glow(), c.m(), at, c.camera(), 0, (1 + q) * 1.3f * s, c.color(), op(200 * (1 - q)));
			return;
		}
		Vec3 centre = c.move().user().position();
		float r = sludgeRadius(c);
		float fade = 1 - seg(c.k(), 0.8f, 1f);
		float h = 1.4f * s * Math.min(1, c.k() * 4);
		for (int i = 0; i < 4; i++) {
			double y = h * (i + 0.5) / 4;
			Draw.ring(c.glow(), c.m(), centre.add(0, y, 0), UP, r - (float) y * 0.2f, 0.35f * s, i % 2 == 0 ? c.color() : c.accent(), op(200 * fade));
		}
		int n = Math.max(10, Math.round(r * 3));
		for (int i = 0; i < n; i++) {
			double angle = Mth.TWO_PI * i / n + c.t() * 0.02;
			Vec3 crest = centre.add(Math.cos(angle) * r, h + 0.1 * Math.sin(c.t() * 0.5 + i), Math.sin(angle) * r);
			Draw.sphere(c.buffers().getBuffer(Draw.DARK), c.m(), crest, 0.16f * s * fade, 0x3A1050, 220, false);
			Draw.sphere(c.glow(), c.m(), crest, 0.24f * s * fade, c.color(), 150, true);
		}
	}

	// =====================================================================
	// Helpers
	// =====================================================================

	/** Flame tongues licking up round a body: {@code n} of them on a circle of {@code w}, up to {@code h}. */
	static void flames(Context c, Vec3 feet, float w, float h, int n, float s, float alpha) {
		if (alpha <= 0) {
			return;
		}
		// Each tongue is a curved ribbon tapering to a point and swaying, with a hot pale core: a flat cone reads as a
		// triangle, not a flame.
		for (int i = 0; i < n; i++) {
			double angle = i * 2.4 + c.t() * 0.2;
			double rise = (c.t() * 0.07 + i * 0.29) % 1;
			double life = Math.sin(rise * Math.PI);
			Vec3 base = feet.add(Math.cos(angle) * w, rise * h * 0.8, Math.sin(angle) * w);
			double len = (0.5 + 0.6 * life) * s;
			Vec3[] pts = new Vec3[5];
			for (int j = 0; j < pts.length; j++) {
				double f = j / 4.0;
				double sway = Math.sin(c.t() * 0.5 + i * 1.3 + f * 3) * 0.12 * s * f;
				pts[j] = base.add(Math.cos(angle + 1.6) * sway, f * len, Math.sin(angle + 1.6) * sway);
			}
			ribbon(c, pts, 0.2f * s, 0.01f, c.color(), (float) (220 * alpha * life), (float) (60 * alpha * life));
			ribbon(c, pts, 0.09f * s, 0.01f, i % 2 == 0 ? c.accent() : 0xFFF4C0, (float) (240 * alpha * life), (float) (40 * alpha * life));
		}
	}
}
