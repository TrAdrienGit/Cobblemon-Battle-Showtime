package com.mystaria.showtime.move;

import static com.mystaria.showtime.move.Signatures.*;

import java.util.Set;

import com.mystaria.showtime.battle.BattleCameraDirector;
import com.mystaria.showtime.move.Motifs.Context;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Signature motifs, third batch (2026-11-26): the moves ranked 101 to 150 in Smogon National Dex usage, built on the
 * helpers of {@link Signatures} and {@link Signatures2}.
 */
final class Signatures3 {

	private static final Set<String> NAMES = Set.of("synthesis", "rockslide", "dragontail", "bulkup", "ruination", "waterfall",
			"toxicspikes", "flashcannon", "aurasphere", "bitterblade", "facade", "dragonpulse", "nuzzle", "fierydance", "pyroball",
			"healingwish", "dragonclaw", "malignantchain", "storedpower", "grassyglide", "airslash", "painsplit", "partingshot",
			"lightscreen", "darkpulse", "reflect", "haze", "woodhammer", "shadowsneak", "dualwingbeat", "courtchange", "slackoff",
			"overheat", "stickyweb", "roar", "leafstorm", "energyball", "hydropump", "hypervoice", "iceshard", "foulplay", "thunder",
			"acrobatics", "discharge", "mortalspin", "agility", "headsmash", "highhorsepower", "moonlight");

	/** Moves whose hit shakes the camera, and how hard. */
	private static final Set<String> SHAKE = Set.of("rockslide", "thunder", "headsmash", "hypervoice", "roar", "highhorsepower",
			"woodhammer", "overheat", "ruination");

	private Signatures3() {
	}

	static boolean render(Context c) {
		switch (c.layer().motif) {
			case "synthesis" -> synthesis(c);
			case "rockslide" -> rockSlide(c);
			case "dragontail" -> dragonTail(c);
			case "bulkup" -> bulkUp(c);
			case "ruination" -> ruination(c);
			case "waterfall" -> waterfall(c);
			case "toxicspikes" -> toxicSpikes(c);
			case "flashcannon" -> flashCannon(c);
			case "aurasphere" -> auraSphere(c);
			case "bitterblade" -> bitterBlade(c);
			case "facade" -> facade(c);
			case "dragonpulse" -> dragonPulse(c);
			case "nuzzle" -> nuzzle(c);
			case "fierydance" -> fieryDance(c);
			case "pyroball" -> pyroBall(c);
			case "healingwish" -> healingWish(c);
			case "dragonclaw" -> dragonClaw(c);
			case "malignantchain" -> malignantChain(c);
			case "storedpower" -> storedPower(c);
			case "grassyglide" -> grassyGlide(c);
			case "airslash" -> airSlash(c);
			case "painsplit" -> painSplit(c);
			case "partingshot" -> partingShot(c);
			case "lightscreen" -> lightScreen(c);
			case "darkpulse" -> darkPulse(c);
			case "reflect" -> reflect(c);
			case "haze" -> haze(c);
			case "woodhammer" -> woodHammer(c);
			case "shadowsneak" -> shadowSneak(c);
			case "dualwingbeat" -> dualWingbeat(c);
			case "courtchange" -> courtChange(c);
			case "slackoff" -> slackOff(c);
			case "overheat" -> overheat(c);
			case "stickyweb" -> stickyWeb(c);
			case "roar" -> roar(c);
			case "leafstorm" -> leafStorm(c);
			case "energyball" -> energyBall(c);
			case "hydropump" -> hydroPump(c);
			case "hypervoice" -> hyperVoice(c);
			case "iceshard" -> iceShard(c);
			case "foulplay" -> foulPlay(c);
			case "thunder" -> thunder(c);
			case "acrobatics" -> acrobatics(c);
			case "discharge" -> discharge(c);
			case "mortalspin" -> mortalSpin(c);
			case "agility" -> agility(c);
			case "headsmash" -> headSmash(c);
			case "highhorsepower" -> highHorsepower(c);
			case "moonlight" -> moonlight(c);
			default -> {
				return false;
			}
		}
		return true;
	}

	static boolean tick(Context c, ClientLevel level) {
		String motif = c.layer().motif;
		if (!NAMES.contains(motif)) {
			return false;
		}
		boolean first = c.t() < 1f;
		String phase = phase(c);
		if (first && phase.equals("hit") && SHAKE.contains(motif)) {
			BattleCameraDirector.shake(motif.equals("thunder") || motif.equals("headsmash") ? 0.35f : 0.22f, 0.8f);
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
	// Shared drawings
	// =====================================================================

	/** A stat arrow facing the camera: a chevron pointing up (or down). */
	static void chevron(Context c, Vec3 at, float size, int color, float alpha, boolean up) {
		if (alpha <= 0 || size <= 0.01f) {
			return;
		}
		Vec3[] b = c.screen(at);
		Vec3 tip = at.add(b[1].scale((up ? 0.5 : -0.5) * size));
		Vec3 left = at.add(b[0].scale(-size)).add(b[1].scale((up ? -0.5 : 0.5) * size));
		Vec3 right = at.add(b[0].scale(size)).add(b[1].scale((up ? -0.5 : 0.5) * size));
		line(c, left, tip, size * 0.28f, color, alpha);
		line(c, tip, right, size * 0.28f, color, alpha);
		line(c, left, tip, size * 0.09f, 0xFFFFFF, alpha);
		line(c, tip, right, size * 0.09f, 0xFFFFFF, alpha);
	}

	/** Chevrons rising (or sinking) round a body: a stat going up (or down). */
	static void statArrows(Context c, Entity body, int color, float alpha, boolean up) {
		float s = c.size();
		Vec3 base = body.position();
		float h = body.getBbHeight();
		float w = body.getBbWidth() * 0.6f + 0.4f;
		for (int i = 0; i < 4; i++) {
			float p = ((c.t() * 0.035f) + i / 4f) % 1f;
			double angle = i * 1.7 + 0.4;
			double y = up ? p * h * 1.5 : (1 - p) * h * 1.5;
			chevron(c, base.add(Math.cos(angle) * w, y, Math.sin(angle) * w), 0.35f * s, color, alpha * bell(p), up);
		}
	}

	/** A jet of water from {@code from} to {@code to}: a bright core wrapped in a twisting sheath. */
	static void waterJet(Context c, Vec3 from, Vec3 to, float width, float alpha) {
		Vec3 axis = to.subtract(from);
		Vec3[] b = Draw.basis(axis.normalize());
		Draw.streak(c.glow(), c.m(), to, from, c.camera(), width, c.color(), op(200 * alpha));
		Draw.streak(c.glow(), c.m(), to, from, c.camera(), width * 0.35f, 0xFFFFFF, op(230 * alpha));
		for (int j = 0; j < 2; j++) {
			Vec3[] pts = new Vec3[16];
			for (int i = 0; i < pts.length; i++) {
				double p = i / 15.0;
				double angle = -c.t() * 1.2 + p * 14 + j * Math.PI;
				double r = width * (0.7 + 0.3 * p);
				pts[i] = from.add(axis.scale(p)).add(b[0].scale(Math.cos(angle) * r)).add(b[1].scale(Math.sin(angle) * r));
			}
			ribbon(c, pts, width * 0.25f, width * 0.35f, c.accent(), 220 * alpha, 160 * alpha);
		}
	}

	/** Block chunks flung out of {@code at}: rubble of a smash. */
	static void rubble(Context c, Vec3 at, float q, BlockState state, int n, float reach, long seed) {
		if (q <= 0 || q >= 1) {
			return;
		}
		RandomSource jag = RandomSource.create(seed);
		float s = c.size();
		for (int i = 0; i < n; i++) {
			Vec3 d = randomUnit(jag).add(0, 0.7, 0).normalize();
			float size = (0.25f + 0.2f * jag.nextFloat()) * s;
			Vec3 pos = at.add(d.scale(ease(q) * reach * s)).add(0, -q * q * 2.2 * s, 0);
			Motifs.block(c, state, pos, size * (1 - seg(q, 0.8f, 1f)), c.t() * 30 + i * 45, c.t() * 20 + i * 15, i * 25);
		}
	}

	// =====================================================================
	// Self moves
	// =====================================================================

	/** Synthesis: sunbeams slanting down onto the user, leaves spiralling up it, a green glow of recovery. */
	private static void synthesis(Context c) {
		Entity user = c.move().user();
		float s = c.size();
		Vec3 base = user.position();
		float h = user.getBbHeight();
		float w = user.getBbWidth() * 0.6f + 0.4f;
		float fade = c.fade(0.12f, 0.2f);
		for (int i = 0; i < 5; i++) {
			double angle = Mth.TWO_PI * i / 5 + c.t() * 0.02;
			Vec3 top = base.add(Math.cos(angle) * 2.2, h + 3.5, Math.sin(angle) * 2.2);
			Vec3 bottom = base.add(Math.cos(angle) * w * 0.4, h * 0.5, Math.sin(angle) * w * 0.4);
			Draw.streak(c.glow(), c.m(), bottom, top, c.camera(), 0.55f * s, 0xFFF0A0, op(100 * fade));
			Draw.streak(c.glow(), c.m(), bottom, top, c.camera(), 0.15f * s, 0xFFFFFF, op(140 * fade));
		}
		BlockState leaves = Blocks.AZALEA_LEAVES.defaultBlockState();
		for (int i = 0; i < 9; i++) {
			float p = ((c.t() * 0.03f) + i / 9f) % 1f;
			double angle = i * 0.7 + c.t() * 0.15 + p * 4;
			double r = w * (1.3 - 0.4 * p);
			Vec3 pos = base.add(Math.cos(angle) * r, p * h * 1.5, Math.sin(angle) * r);
			Motifs.block(c, leaves, pos, 0.22f * s * bell(p) * fade, c.t() * 20 + i * 40, i * 30, 0);
		}
		float heal = seg(c.k(), 0.3f, 0.7f);
		Draw.halo(c.glow(), c.m(), c.userCentre(), c.camera(), 0, (w + 1.1f) * s, c.color(), op(130 * fade * heal));
		Draw.halo(c.glow(), c.m(), base.add(0, h + 0.4, 0), c.camera(), 0, 1.2f * s, 0xFFF4B0, op(160 * fade));
	}

	/** Bulk Up: three pumps of strength squeezing in on the user, a flash at each, red arrows rising. */
	private static void bulkUp(Context c) {
		Entity user = c.move().user();
		float s = c.size();
		Vec3 centre = c.userCentre();
		float w = user.getBbWidth() * 0.6f + 0.3f;
		float fade = c.fade(0.05f, 0.2f);
		Vec3 sd = side(flat(c.camera().subtract(centre)));
		for (int j = 0; j < 3; j++) {
			float p = seg(c.k(), j * 0.2f, j * 0.2f + 0.25f);
			if (p <= 0 || p >= 1) {
				continue;
			}
			float r = (w + 1.6f - 1.3f * ease(p)) * s;
			Draw.ring(c.glow(), c.m(), centre, UP, r, 0.35f * s, c.color(), op(230 * bell(p)));
			Draw.ring(c.glow(), c.m(), centre.add(0, 0.4 * s, 0), UP, r * 0.9f, 0.2f * s, c.accent(), op(200 * bell(p)));
			if (p > 0.7f) {
				float q = seg(p, 0.7f, 1f);
				for (int side = -1; side <= 1; side += 2) {
					Vec3 arm = centre.add(sd.scale(side * (w + 0.3) * s)).add(0, 0.2 * s, 0);
					twinkle(c, arm, (0.6f + 0.5f * q) * s, 0xFFFFFF, op(250 * (1 - q)));
					Draw.halo(c.glow(), c.m(), arm, c.camera(), 0, 0.8f * s, c.color(), op(200 * (1 - q)));
				}
			}
		}
		Signatures2.flames(c, user.position(), w, user.getBbHeight(), 8, s * 0.8f, fade * 0.7f);
		statArrows(c, user, 0xFF5030, 240 * fade * seg(c.k(), 0.3f, 0.5f), true);
	}

	/** Healing Wish: the user burns bright, its light rises as a star, and pink stars rain down where it stood. */
	private static void healingWish(Context c) {
		Entity user = c.move().user();
		float s = c.size();
		Vec3 base = user.position();
		float h = user.getBbHeight();
		float k = c.k();
		float glow = seg(k, 0, 0.35f) * (1 - seg(k, 0.4f, 0.55f));
		Draw.halo(c.glow(), c.m(), c.userCentre(), c.camera(), 0, (user.getBbWidth() + 1.6f) * s, 0xFFFFFF, op(200 * glow));
		Draw.streak(c.glow(), c.m(), base, base.add(0, 8, 0), c.camera(), 1.2f * s, c.color(), op(120 * glow));
		float rise = seg(k, 0.35f, 0.65f);
		if (rise > 0 && rise < 1) {
			Vec3 star = base.add(0, h * 0.5 + rise * 5, 0);
			twinkle(c, star, (1.2f - 0.6f * rise) * s, 0xFFFFFF, op(250 * (1 - rise * 0.5f)));
			Draw.streak(c.glow(), c.m(), star, star.subtract(0, 1.5 * s, 0), c.camera(), 0.3f * s, c.color(), op(220 * (1 - rise)));
		}
		float rain = seg(k, 0.55f, 1f);
		if (rain > 0 && rain < 1) {
			RandomSource jag = RandomSource.create(229);
			for (int i = 0; i < 14; i++) {
				double x = jag.nextGaussian() * 1.2 * s;
				double z = jag.nextGaussian() * 1.2 * s;
				float p = (rain * 1.6f + jag.nextFloat()) % 1f;
				Vec3 at = base.add(x, (1 - p) * 4, z);
				twinkle(c, at, 0.3f * s, i % 2 == 0 ? c.color() : 0xFFFFFF, op(230 * bell(p) * (1 - seg(rain, 0.8f, 1f))));
			}
			Draw.ring(c.glow(), c.m(), base.add(0, 0.07, 0), UP, (user.getBbWidth() * 0.6f + 0.6f) * s, 0.3f * s, c.color(), op(180 * bell(rain)));
		}
	}

	/** Light Screen: golden panes of light locking into a curved wall in front of the user, a shimmer running over it. */
	private static void lightScreen(Context c) {
		screenWall(c, Blocks.YELLOW_STAINED_GLASS.defaultBlockState(), 5, 1.5f);
	}

	/** Reflect: one tall pink mirror rising in front of the user, its edges glowing and a gleam crossing it. */
	private static void reflect(Context c) {
		screenWall(c, Blocks.MAGENTA_STAINED_GLASS.defaultBlockState(), 1, 2.4f);
	}

	private static void screenWall(Context c, BlockState glass, int panes, float tall) {
		Entity user = c.move().user();
		float s = c.size();
		Vec3 centre = c.userCentre();
		// Halfway between the foe and the camera, so the panes face the viewer instead of showing their edge.
		Vec3 dir = flat(c.toTarget()).add(flat(c.camera().subtract(centre))).normalize();
		if (dir.lengthSqr() < 1e-4) {
			dir = flat(c.toTarget());
		}
		float r = user.getBbWidth() * 0.6f + 1.0f * s;
		float appear = ease(seg(c.k(), 0, 0.3f));
		float gone = seg(c.k(), 0.85f, 1f);
		float size = appear * (1 - gone);
		double base = Math.atan2(dir.z, dir.x);
		float paneWidth = panes == 1 ? 2.2f * s : 0.75f * s;
		float height = tall * s * Math.max(1, user.getBbHeight() * 0.6f);
		for (int i = 0; i < panes; i++) {
			double angle = base + (i - (panes - 1) / 2.0) * 0.36;
			Vec3 d = dirOf(angle);
			Vec3 pos = centre.add(d.scale(r)).add(0, (1 - appear) * -1.5, 0);
			float yaw = (float) -Math.toDegrees(angle) + 90;
			box(c, glass, pos, paneWidth * size, height * size, 0.05f * s, yaw);
			Vec3 across = side(d).scale(paneWidth * 0.5 * size);
			Vec3 up = new Vec3(0, height * 0.5 * size, 0);
			Vec3 near = pos.subtract(d.scale(0.04));
			float edge = 220 * size;
			line(c, near.subtract(across).subtract(up), near.add(across).subtract(up), 0.06f * s, c.color(), edge);
			line(c, near.subtract(across).add(up), near.add(across).add(up), 0.06f * s, c.color(), edge);
			line(c, near.subtract(across).subtract(up), near.subtract(across).add(up), 0.06f * s, c.color(), edge);
			line(c, near.add(across).subtract(up), near.add(across).add(up), 0.06f * s, c.color(), edge);
		}
		float sweep = seg(c.k(), 0.35f, 0.65f);
		if (sweep > 0 && sweep < 1) {
			double angle = base + (sweep * 2 - 1) * (panes == 1 ? 0.45 : 0.8);
			Vec3 g = centre.add(dirOf(angle).scale(r - 0.06));
			line(c, g.subtract(0, height * 0.5, 0).add(side(dir).scale(0.25 * s)), g.add(0, height * 0.5, 0).subtract(side(dir).scale(0.25 * s)), 0.2f * s,
					0xFFFFFF, 240 * bell(sweep));
		}
		float twink = seg(c.k(), 0.6f, 0.85f);
		if (twink > 0 && twink < 1) {
			twinkle(c, centre.add(dir.scale(r)).add(0, height * 0.45, 0), 0.9f * s, 0xFFFFFF, op(250 * bell(twink)));
		}
	}

	/** Haze: a cold mist rolling over the whole field, every stat arrow drawn out of it. */
	private static void haze(Context c) {
		float s = c.size();
		Vec3 a = c.move().user().position();
		Vec3 b = c.aim();
		Vec3 mid = a.lerp(b, 0.5);
		float fade = c.fade(0.2f, 0.25f);
		float spread = ease(seg(c.k(), 0, 0.5f));
		RandomSource jag = RandomSource.create(233);
		for (int i = 0; i < 16; i++) {
			Vec3 off = new Vec3(jag.nextGaussian() * 2.4, 0.3 + jag.nextDouble() * 1.4, jag.nextGaussian() * 1.6);
			Vec3 drift = new Vec3(Math.sin(c.t() * 0.05 + i) * 0.4, 0, Math.cos(c.t() * 0.04 + i) * 0.4);
			Draw.halo(c.glow(), c.m(), mid.add(off.scale(spread)).add(drift), c.camera(), 0, (0.7f + 0.6f * jag.nextFloat()) * s, 0xD8E4F0, op(45 * fade));
		}
		statArrows(c, c.move().user(), 0xFFFFFF, 200 * fade * seg(c.k(), 0.3f, 0.5f), false);
		if (c.move().target != null) {
			statArrows(c, c.move().target, 0xFFFFFF, 200 * fade * seg(c.k(), 0.3f, 0.5f), false);
		}
	}

	/** Slack Off: the user lolling in a warm glow, plus signs bubbling up lazily round it. */
	private static void slackOff(Context c) {
		Entity user = c.move().user();
		float s = c.size();
		Vec3 base = user.position();
		float h = user.getBbHeight();
		float w = user.getBbWidth() * 0.6f + 0.4f;
		float fade = c.fade(0.1f, 0.2f);
		Draw.halo(c.glow(), c.m(), c.userCentre(), c.camera(), 0, (w + 1.2f) * s, c.color(), op(110 * fade));
		Draw.ring(c.glow(), c.m(), base.add(0, 0.07, 0), UP, (w + 0.3f + 0.2f * Mth.sin(c.t() * 0.2f)) * s, 0.3f * s, c.color(), op(180 * fade));
		for (int i = 0; i < 6; i++) {
			float p = ((c.t() * 0.02f) + i / 6f) % 1f;
			double angle = i * 1.05 + Math.sin(c.t() * 0.05 + i) * 0.3;
			Vec3 at = base.add(Math.cos(angle) * w, p * h * 1.6, Math.sin(angle) * w);
			Vec3[] b = c.screen(at);
			float m = 0.22f * s;
			float a = 240 * fade * bell(p);
			line(c, at.subtract(b[0].scale(m)), at.add(b[0].scale(m)), 0.1f * s, 0x80FF80, a);
			line(c, at.subtract(b[1].scale(m)), at.add(b[1].scale(m)), 0.1f * s, 0x80FF80, a);
			Draw.halo(c.glow(), c.m(), at, c.camera(), 0, m * 2, 0x80FF80, op(a * 0.4f));
		}
	}

	/** Agility: afterimages of the user flickering round it, speed lines whipping past, arrows rising. */
	private static void agility(Context c) {
		Entity user = c.move().user();
		float s = c.size();
		Vec3 centre = c.userCentre();
		float fade = c.fade(0.05f, 0.2f);
		float w = user.getBbWidth() * 0.6f + 0.6f;
		for (int i = 0; i < 4; i++) {
			double angle = c.t() * 0.5 + i * Mth.TWO_PI / 4;
			Vec3 ghost = centre.add(Math.cos(angle) * w * s, 0, Math.sin(angle) * w * s);
			Draw.halo(c.glow(), c.m(), ghost, c.camera(), 0, (user.getBbHeight() * 0.5f + 0.3f) * s, c.color(), op(90 * fade));
			Vec3 trail = centre.add(Math.cos(angle - 0.7) * w * s, 0, Math.sin(angle - 0.7) * w * s);
			Draw.streak(c.glow(), c.m(), ghost, trail, c.camera(), 0.2f * s, 0xFFFFFF, op(200 * fade));
		}
		RandomSource jag = RandomSource.create((long) (c.t() / 2) * 3);
		Vec3 sd = side(flat(c.camera().subtract(centre)));
		for (int i = 0; i < 6; i++) {
			Vec3 at = centre.add(0, jag.nextGaussian() * user.getBbHeight() * 0.5, 0).add(sd.scale(jag.nextGaussian() * 0.5));
			Draw.streak(c.glow(), c.m(), at.add(sd.scale(1.2 * s)), at.subtract(sd.scale(1.2 * s)), c.camera(), 0.05f * s, 0xFFFFFF, op(200 * fade));
		}
		statArrows(c, user, c.accent(), 240 * fade * seg(c.k(), 0.3f, 0.5f), true);
	}

	/** Moonlight: a crescent moon over the user, silver light pouring down from it, sparkles of recovery. */
	private static void moonlight(Context c) {
		Entity user = c.move().user();
		float s = c.size();
		Vec3 base = user.position();
		float h = user.getBbHeight();
		float fade = c.fade(0.15f, 0.2f);
		Vec3 moon = base.add(0, h + 1.6 * s, 0);
		Vec3[] b = c.screen(moon);
		float r = 0.7f * s * ease(seg(c.k(), 0, 0.3f));
		Draw.halo(c.glow(), c.m(), moon, c.camera(), 0, r * 2, c.color(), op(50 * fade));
		// The crescent as a thick band: two arcs whose centres are offset, the lit side wider than the horns.
		for (int i = 0; i < 6; i++) {
			float off = i * 0.06f;
			Vec3 ctr = moon.add(b[0].scale(-off * r));
			Draw.arc(c.glow(), c.m(), ctr, b[0], b[1], r * (1 - off * 0.5f), 1.75f, 4.55f, 1f, 0.22f * r, i < 2 ? 0xFFFFFF : 0xFFF6D8, op(230 * fade));
		}
		for (int i = 0; i < 4; i++) {
			double angle = Mth.TWO_PI * i / 4 + 0.4;
			Vec3 foot = base.add(Math.cos(angle) * user.getBbWidth() * 0.5, 0.1, Math.sin(angle) * user.getBbWidth() * 0.5);
			Draw.streak(c.glow(), c.m(), foot, moon, c.camera(), 0.4f * s, c.color(), op(80 * fade));
		}
		for (int i = 0; i < 8; i++) {
			float p = ((c.t() * 0.03f) + i / 8f) % 1f;
			Vec3 at = moon.lerp(base.add(Math.cos(i * 0.8) * 0.8 * s, 0, Math.sin(i * 0.8) * 0.8 * s), p);
			twinkle(c, at, 0.3f * s, 0xFFFFFF, op(230 * fade * bell(p)));
		}
		Draw.halo(c.glow(), c.m(), c.userCentre(), c.camera(), 0, (user.getBbWidth() + 0.6f) * s, c.color(), op(70 * fade * seg(c.k(), 0.4f, 0.7f)));
	}

	// =====================================================================
	// Physical blows
	// =====================================================================

	/** Rock Slide: real rocks tumbling in from the side and above, crashing round the target in rings of dust. */
	private static void rockSlide(Context c) {
		float s = c.size();
		Vec3 feet = c.move().aimFeet();
		Vec3 dir = flat(c.toTarget());
		Vec3 sd = side(dir);
		BlockState[] kinds = { Blocks.COBBLESTONE.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(),
				Blocks.MOSSY_COBBLESTONE.defaultBlockState() };
		RandomSource jag = RandomSource.create(223);
		float sink = seg(c.k(), 0.85f, 1f);
		for (int i = 0; i < 9; i++) {
			double x = jag.nextGaussian() * 0.9;
			double z = jag.nextGaussian() * 0.6;
			float size = (0.45f + 0.35f * jag.nextFloat()) * s;
			float start = i * 0.05f;
			float p = seg(c.k(), start, start + 0.28f);
			if (p <= 0) {
				continue;
			}
			Vec3 land = feet.add(sd.scale(x * s)).add(dir.scale(z * s)).add(0, size * 0.5, 0);
			Vec3 sky = land.add(sd.scale(-2.2 * s)).add(0, 4.5, 0);
			BlockState kind = kinds[i % kinds.length];
			if (p < 1) {
				Vec3 pos = sky.lerp(land, p * p);
				Motifs.block(c, kind, pos, size, c.t() * 25 + i * 50, c.t() * 18, i * 30);
				Draw.streak(c.glow(), c.m(), pos, sky.lerp(land, Math.max(0, p * p - 0.25)), c.camera(), size * 0.5f, 0xC8B898, 110);
				continue;
			}
			Motifs.block(c, kind, land.add(0, -sink * size, 0), size * (1 - sink * 0.5f), i * 50, 0, i * 30);
			float q = seg(c.k(), start + 0.28f, start + 0.6f);
			if (q < 1) {
				Draw.ring(c.glow(), c.m(), land.add(0, -size * 0.5 + 0.06, 0), UP, (0.3f + 1.3f * q) * s, 0.3f * s, 0xD8C8A0, op(200 * (1 - q)));
				Draw.halo(c.glow(), c.m(), land, c.camera(), 0, (0.5f + q) * s, 0xE0D8C0, op(140 * (1 - q)));
			}
		}
	}

	/** Dragon Tail: a huge tail of scales sweeping round through the target and flinging it away in a spin. */
	private static void dragonTail(Context c) {
		float s = c.size() * Math.max(1f, c.move().targetHeight() * 0.7f);
		Vec3 at = c.aim();
		Vec3 dir = flat(c.toTarget());
		Vec3 sd = side(dir);
		Vec3 centre = at.subtract(dir.scale(0.3 * s)).add(0, -0.2 * s, 0);
		float swing = ease(seg(c.k(), 0, 0.35f));
		float fade = 1 - seg(c.k(), 0.45f, 0.8f);
		Draw.arc(c.glow(), c.m(), centre, sd, dir.scale(-1), 1.9f * s, -0.8f, 3.6f, swing, 0.9f * s, c.color(), op(230 * fade));
		Draw.arc(c.glow(), c.m(), centre, sd, dir.scale(-1), 1.85f * s, -0.8f, 3.6f, swing, 0.3f * s, c.accent(), op(240 * fade));
		BlockState scale = Blocks.PURPLE_STAINED_GLASS.defaultBlockState();
		float tip = -0.8f + 4.4f * swing;
		for (int i = 0; i < 6; i++) {
			float angle = tip - i * 0.28f;
			if (angle < -0.8f) {
				break;
			}
			Vec3 d = sd.scale(Math.cos(angle)).add(dir.scale(-Math.sin(angle)));
			Vec3 pos = centre.add(d.scale(1.9 * s)).add(0, 0.15 * s, 0);
			crystalToward(c, scale, pos, (0.35f - i * 0.04f) * s * fade, (0.7f - i * 0.06f) * s * fade, d.add(0, 0.6, 0));
		}
		float q = seg(c.k(), 0.25f, 0.85f);
		if (q > 0 && q < 1) {
			Motifs.impact(c, at, q, 1.3f);
			Draw.ring(c.glow(), c.m(), at, UP, (c.move().targetWidth() * 0.6f + 0.5f + q) * s, 0.25f * s, c.color(), op(220 * (1 - q)));
			for (int i = 0; i < 5; i++) {
				Vec3 o = at.add(0, (i - 2) * 0.25 * s, 0);
				Draw.streak(c.glow(), c.m(), o.add(sd.scale((0.6 + 2.2 * q) * s)), o.add(sd.scale((0.4 + 1.2 * q) * s)), c.camera(), 0.08f * s, 0xFFFFFF,
						op(220 * (1 - q)));
			}
		}
	}

	/** Waterfall: the user climbing a rising column of water, then the cascade crashing down onto the target. */
	private static void waterfall(Context c) {
		float s = c.size();
		if (phase(c).equals("wind")) {
			Entity user = c.move().user();
			float fade = c.fade(0.15f, 0.05f);
			Vec3 base = user.position();
			float r = user.getBbWidth() * 0.6f + 0.3f;
			for (int i = 0; i < 10; i++) {
				double angle = Mth.TWO_PI * i / 10;
				float p = ((c.t() * 0.12f) + i * 0.37f) % 1f;
				Vec3 low = base.add(Math.cos(angle) * r, p * user.getBbHeight() * 1.4, Math.sin(angle) * r);
				Draw.streak(c.glow(), c.m(), low.add(0, 0.7 * s, 0), low, c.camera(), 0.15f * s, i % 2 == 0 ? c.color() : 0xFFFFFF, op(220 * fade * bell(p)));
			}
			return;
		}
		Vec3 feet = c.move().aimFeet();
		Vec3 at = c.aim();
		float w = c.move().targetWidth() * 0.5f + 0.4f;
		float pour = seg(c.k(), 0, 0.55f);
		float fade = 1 - seg(c.k(), 0.5f, 0.8f);
		double top = c.move().targetHeight() + 3.2 * s;
		for (int i = 0; i < 12; i++) {
			double x = (i - 5.5) / 5.5 * w * s;
			Vec3 sd = side(flat(c.camera().subtract(at)));
			Vec3 column = feet.add(sd.scale(x));
			double head = top - Math.min(1, pour * 2.2) * top;
			Vec3 from = column.add(0, top, 0);
			Vec3 to = column.add(0, Math.max(0, head), 0);
			Draw.streak(c.glow(), c.m(), to, from, c.camera(), 0.28f * s, i % 3 == 0 ? 0xFFFFFF : c.color(), op(190 * fade));
		}
		splashCrownAt(c, feet, seg(c.k(), 0.2f, 1f), s * 1.3f);
		Motifs.impact(c, at, seg(c.k(), 0.2f, 0.7f), 1.2f);
	}

	private static void splashCrownAt(Context c, Vec3 feet, float k, float s) {
		if (k > 0 && k < 1) {
			Signatures2.splashCrown(c, feet, k, s);
		}
	}

	/** Bitter Blade: a slash of fire, flames clinging to the cut, embers of the target's strength flowing back. */
	private static void bitterBlade(Context c) {
		float s = c.size() * Math.max(1.4f, c.move().targetHeight() * 0.95f);
		Vec3 at = c.aim();
		Vec3[] b = c.screen(at);
		Vec3 u = b[0].subtract(b[1].scale(0.5)).normalize();
		Vec3 v = b[1].add(b[0].scale(0.5)).normalize();
		float swing = ease(seg(c.k(), 0, 0.25f));
		float fade = 1 - seg(c.k(), 0.35f, 0.65f);
		Draw.arc(c.glow(), c.m(), at, u, v, 1.4f * s, -0.4f, 3.5f, swing, 0.7f * s, c.color(), op(240 * fade));
		Draw.arc(c.glow(), c.m(), at, u, v, 1.36f * s, -0.4f, 3.5f, swing, 0.28f * s, c.accent(), op(250 * fade));
		Draw.arc(c.glow(), c.m(), at, u, v, 1.33f * s, -0.4f, 3.5f, swing, 0.08f * s, 0xFFFFFF, op(255 * fade));
		Signatures2.flames(c, c.move().aimFeet(), c.move().targetWidth() * 0.5f + 0.2f, c.move().targetHeight(), 10, c.size(),
				seg(c.k(), 0.15f, 0.3f) * (1 - seg(c.k(), 0.6f, 0.8f)));
		Signatures2.drainBack(c, seg(c.k(), 0.4f, 1f), 7, 0xFF7040, 0.25f * c.size());
	}

	/** Facade: a throbbing anger mark over the user, then a furious body blow ringed in red shock waves. */
	private static void facade(Context c) {
		float s = c.size();
		if (phase(c).equals("wind")) {
			Entity user = c.move().user();
			Vec3 mark = user.position().add(0, user.getBbHeight() + 0.5 * s, 0).add(flat(c.camera().subtract(c.userCentre())).scale(0.3));
			float beat = 1 + 0.25f * Math.abs(Mth.sin(c.t() * 0.6f));
			angerMark(c, mark, 0.45f * s * beat, 0xFF3030, 250 * c.fade(0.1f, 0.05f));
			Draw.halo(c.glow(), c.m(), c.userCentre(), c.camera(), 0, (user.getBbWidth() + 1.2f) * s, 0xFF4030, op(110 * c.fade(0.1f, 0.05f)));
			return;
		}
		Vec3 at = c.aim();
		Motifs.impact(c, at, seg(c.k(), 0, 0.6f), 1.6f);
		for (int i = 0; i < 3; i++) {
			float q = seg(c.k(), i * 0.1f, i * 0.1f + 0.5f);
			if (q > 0 && q < 1) {
				Draw.ring(c.glow(), c.m(), at, c.toTarget(), (0.5f + 2.5f * q) * s, 0.25f * s, 0xFF3030, op(230 * (1 - q)));
			}
		}
		angerMark(c, at.add(0, c.move().targetHeight() * 0.5 + 0.5, 0), 0.4f * s, 0xFF3030, 240 * (1 - seg(c.k(), 0.4f, 0.8f)));
	}

	/** The anime anger mark: four bent veins round a centre. */
	private static void angerMark(Context c, Vec3 at, float size, int color, float alpha) {
		Vec3[] b = c.screen(at);
		for (int i = 0; i < 4; i++) {
			double angle = Math.PI / 4 + i * Math.PI / 2;
			Vec3 d = b[0].scale(Math.cos(angle)).add(b[1].scale(Math.sin(angle)));
			Vec3 e = b[0].scale(-Math.sin(angle)).add(b[1].scale(Math.cos(angle)));
			Vec3 corner = at.add(d.scale(size));
			line(c, corner.add(e.scale(size * 0.5)), corner, size * 0.22f, color, alpha);
			line(c, corner, corner.subtract(e.scale(size * 0.5)).subtract(d.scale(0)), size * 0.22f, color, alpha);
			line(c, corner.add(e.scale(size * 0.5)), corner.add(e.scale(size * 0.5)).subtract(d.scale(size * 0.5)), size * 0.22f, color, alpha);
			line(c, corner.subtract(e.scale(size * 0.5)), corner.subtract(e.scale(size * 0.5)).subtract(d.scale(size * 0.5)), size * 0.22f, color, alpha);
		}
	}

	/** Nuzzle: a cheek-to-cheek rub, little hearts and sparks, the target's body locking up in yellow zigzags. */
	private static void nuzzle(Context c) {
		float s = c.size();
		Vec3 at = c.aim();
		float fade = c.fade(0.05f, 0.3f);
		for (int i = 0; i < 3; i++) {
			float p = ((c.t() * 0.05f) + i / 3f) % 1f;
			Vec3 pos = at.add(0, 0.4 * s + p * 1.2 * s, 0).add(side(flat(c.toTarget())).scale((i - 1) * 0.5 * s));
			Signatures2.heart(c, pos, 0.28f * s, 0xFF80B0, 230 * fade * bell(p));
		}
		sparks(c, at, c.move().targetWidth() * 0.6f + 0.3f * s, 5, 0.05f * s);
		Vec3[] b = c.screen(at);
		for (int i = 0; i < 6; i++) {
			double angle = Mth.TWO_PI * i / 6 + c.t() * 0.1;
			Vec3 d = b[0].scale(Math.cos(angle)).add(b[1].scale(Math.sin(angle)));
			Vec3 from = at.add(d.scale(0.5 * s));
			Motifs.bolt(c.glow(), c.m(), c.camera(), from, from.add(d.scale(0.9 * s)), c.t() + i, 0.05f * s, c.color(), i);
		}
		Motifs.impact(c, at, seg(c.k(), 0, 0.5f), 0.8f);
	}

	/** Dragon Claw: three giant claw marks raked across the target, scales of light flying off. */
	private static void dragonClaw(Context c) {
		float s = c.size() * Math.max(1.1f, c.move().targetHeight() * 0.8f);
		Vec3 at = c.aim();
		Vec3[] b = c.screen(at);
		Vec3 slash = b[0].scale(-0.6).add(b[1]).normalize();
		Vec3 across = b[0].scale(1).add(b[1].scale(0.6)).normalize();
		float fade = 1 - seg(c.k(), 0.45f, 0.85f);
		for (int i = 0; i < 3; i++) {
			float p = ease(seg(c.k(), i * 0.05f, i * 0.05f + 0.22f));
			if (p <= 0) {
				continue;
			}
			// Thin curved strokes, well apart and tapered by the arc itself: three gashes, not a striped sheet.
			Vec3 o = at.add(across.scale((i - 1) * 0.7 * s));
			Vec3 ctr = o.add(across.scale(2.2 * s));
			float r = 2.2f * s;
			Draw.arc(c.glow(), c.m(), ctr, across.scale(-1), slash, r, -0.7f, 0.7f, p, 0.24f * s, c.color(), op(230 * fade));
			Draw.arc(c.glow(), c.m(), ctr, across.scale(-1), slash, r, -0.7f, 0.7f, p, 0.1f * s, c.accent(), op(245 * fade));
			Draw.arc(c.glow(), c.m(), ctr, across.scale(-1), slash, r, -0.7f, 0.7f, p, 0.035f * s, 0xFFFFFF, op(255 * fade));
		}
		float q = seg(c.k(), 0.2f, 0.9f);
		if (q > 0 && q < 1) {
			Motifs.impact(c, at, q, 1.2f);
			BlockState scale = Blocks.PURPLE_STAINED_GLASS.defaultBlockState();
			for (int i = 0; i < 7; i++) {
				Vec3 d = across.scale(Math.cos(i * 0.9)).add(slash.scale(Math.sin(i * 0.9))).add(c.toTarget().scale(-0.6)).normalize();
				crystalToward(c, scale, at.add(d.scale((0.4 + 2 * q) * s)).add(0, -q * q * s, 0), 0.2f * s * (1 - q), 0.45f * s * (1 - q), d);
			}
		}
	}

	/** Wood Hammer: a giant log raised over the user and brought down on the target, wood splinters and leaves flying. */
	private static void woodHammer(Context c) {
		float s = c.size() * 1.2f;
		BlockState log = Blocks.OAK_LOG.defaultBlockState();
		float yaw = (float) Math.toDegrees(Math.atan2(c.toTarget().x, c.toTarget().z));
		if (phase(c).equals("wind")) {
			Entity user = c.move().user();
			float lift = ease(seg(c.k(), 0, 0.7f));
			Vec3 pos = user.position().add(0, user.getBbHeight() + (0.3 + 1.2 * lift) * s, 0);
			Motifs.block(c, log, pos, 1.1f * s * lift, yaw, -20 * lift, 0);
			Draw.halo(c.glow(), c.m(), pos, c.camera(), 0, 1.4f * s, c.color(), op(120 * lift));
			return;
		}
		Vec3 at = c.aim();
		float drop = ease(seg(c.k(), 0, 0.2f));
		float gone = seg(c.k(), 0.6f, 0.75f);
		Vec3 pos = at.add(0, (c.move().targetHeight() * 0.5 + 0.6) * s + (1 - drop) * 3 * s, 0);
		Motifs.block(c, log, pos, 1.3f * s * (1 - gone), yaw, -90 * drop, 0);
		float q = seg(c.k(), 0.18f, 1f);
		if (q > 0) {
			Motifs.impact(c, at, seg(q, 0, 0.7f), 1.4f);
			rubble(c, at, seg(q, 0, 0.8f), Blocks.OAK_PLANKS.defaultBlockState(), 8, 2.4f, 239);
			rubble(c, at.add(0, 0.5, 0), seg(q, 0.05f, 0.9f), Blocks.AZALEA_LEAVES.defaultBlockState(), 5, 2f, 241);
			cracks(c, at, seg(q, 0, 0.7f), c.size(), c.accent());
		}
		float recoil = seg(c.k(), 0.3f, 0.6f);
		if (recoil > 0 && recoil < 1) {
			Draw.halo(c.glow(), c.m(), c.userCentre(), c.camera(), 0, (1 + recoil) * c.size(), 0xFF6040, op(160 * (1 - recoil)));
		}
	}

	/** Shadow Sneak: the user's shadow running along the ground to the target and a shadow claw striking up out of it. */
	private static void shadowSneak(Context c) {
		float s = c.size();
		Vec3 from = c.move().user().position();
		Vec3 to = c.move().aimFeet();
		if (phase(c).equals("travel")) {
			float p = ease(c.k());
			int n = 14;
			for (int i = 0; i < n; i++) {
				float f = i / (float) (n - 1) * p;
				Vec3 at = from.lerp(to, f).add(0, 0.05, 0);
				Draw.sphere(c.buffers().getBuffer(Draw.DARK), c.m(), at, 0.28f * s, 0x1A0A28, 220, false);
				Draw.halo(c.glow(), c.m(), at.add(0, 0.1, 0), c.camera(), 0, 0.5f * s, c.color(), 120);
			}
			return;
		}
		Vec3 at = c.aim();
		Draw.sphere(c.buffers().getBuffer(Draw.DARK), c.m(), to.add(0, 0.02, 0), (c.move().targetWidth() * 0.6f + 0.3f) * s * (1 - seg(c.k(), 0.7f, 1f)),
				0x1A0A28, 220, false);
		shadowHand(c, to, at, seg(c.k(), 0, 0.35f), 1 - seg(c.k(), 0.5f, 0.85f), s);
		Motifs.impact(c, at, seg(c.k(), 0.25f, 0.8f), 1f);
	}

	/** A hand of shadow reaching up out of the ground at {@code root} to {@code reach}, its claws spread. */
	private static void shadowHand(Context c, Vec3 root, Vec3 reach, float grow, float alpha, float s) {
		if (grow <= 0 || alpha <= 0) {
			return;
		}
		Vec3 palm = root.lerp(reach, ease(grow));
		Draw.streak(c.glow(), c.m(), palm, root, c.camera(), 0.5f * s, c.color(), op(200 * alpha));
		Draw.sphere(c.buffers().getBuffer(Draw.DARK), c.m(), palm, 0.18f * s, 0x2A1040, op(200 * alpha), false);
		Vec3[] b = c.screen(palm);
		for (int i = 0; i < 4; i++) {
			double angle = Math.PI / 2 + (i - 1.5) * 0.4;
			Vec3 d = b[0].scale(Math.cos(angle)).add(b[1].scale(Math.sin(angle)));
			Vec3 tip = palm.add(d.scale(0.7 * s));
			line(c, palm, tip, 0.12f * s, c.color(), 230 * alpha);
			line(c, palm.lerp(tip, 0.5), tip, 0.05f * s, c.accent(), 240 * alpha);
		}
		Draw.halo(c.glow(), c.m(), palm, c.camera(), 0, 0.9f * s, c.accent(), op(110 * alpha));
	}

	/** Dual Wingbeat: two great wing strokes, one from each side, feathers bursting off each. */
	private static void dualWingbeat(Context c) {
		float s = c.size() * Math.max(1.3f, c.move().targetHeight() * 0.9f);
		Vec3 at = c.aim();
		Vec3[] b = c.screen(at);
		ItemStack feather = new ItemStack(Items.FEATHER);
		for (int j = 0; j < 2; j++) {
			float p = seg(c.k(), j * 0.3f, j * 0.3f + 0.4f);
			if (p <= 0 || p >= 1) {
				continue;
			}
			int sign = j == 0 ? 1 : -1;
			float sweep = ease(Math.min(1, p * 2));
			float fade = 1 - seg(p, 0.5f, 1f);
			Vec3 root = at.add(b[0].scale(sign * 1.6 * s)).add(b[1].scale(0.4 * s));
			for (int f = 0; f < 7; f++) {
				double angle = Math.PI / 2 + sign * (0.3 + f * 0.22) * (1 - sweep * 1.4);
				Vec3 d = b[0].scale(-sign * Math.abs(Math.cos(angle))).add(b[1].scale(Math.sin(angle) - 0.3 * sweep));
				Vec3 tip = root.add(d.normalize().scale((2.8 - f * 0.15) * s));
				Draw.streak(c.glow(), c.m(), tip, root, c.camera(), 0.38f * s, c.color(), op(230 * fade));
				Draw.streak(c.glow(), c.m(), tip, root, c.camera(), 0.12f * s, 0xFFFFFF, op(240 * fade));
			}
			if (p > 0.45f) {
				float q = seg(p, 0.45f, 1f);
				Motifs.impact(c, at, q, 1f);
				for (int i = 0; i < 5; i++) {
					Vec3 d = b[0].scale(-sign).add(b[1].scale((i - 2) * 0.4)).normalize();
					Motifs.item(c, feather, at.add(d.scale(q * 2 * s)).add(0, -q * q * s, 0), 0.5f * s * (1 - q), c.t() * 15 + i * 40, i * 30,
							(float) Math.sin(c.t() * 0.3 + i) * 40);
				}
			}
		}
	}

	/** Acrobatics: the user flipping round the target, a trail of arcs from four angles, a hit at each. */
	private static void acrobatics(Context c) {
		float s = c.size();
		Vec3 at = c.aim();
		Vec3 dir = flat(c.toTarget());
		Vec3 sd = side(dir);
		for (int j = 0; j < 4; j++) {
			float p = seg(c.k(), j * 0.15f, j * 0.15f + 0.3f);
			if (p <= 0 || p >= 1) {
				continue;
			}
			double tilt = j * Math.PI / 4;
			Vec3 u = sd.scale(Math.cos(tilt)).add(dir.scale(Math.sin(tilt)));
			float sweep = ease(Math.min(1, p * 2));
			float fade = 1 - seg(p, 0.5f, 1f);
			Draw.arc(c.glow(), c.m(), at, u, UP, 1.8f * s, 0.2f, 5.5f, sweep, 0.6f * s, j % 2 == 0 ? c.color() : c.accent(), op(235 * fade));
			Draw.arc(c.glow(), c.m(), at, u, UP, 1.78f * s, 0.2f, 5.5f, sweep, 0.2f * s, 0xFFFFFF, op(250 * fade));
			if (p > 0.45f) {
				Motifs.impact(c, at.add(u.scale(0.3 * s)), seg(p, 0.45f, 1f), 0.9f);
			}
		}
	}

	/** Head Smash: a head as hard as rock driving into the target, the stone shattering everywhere, cracks underfoot. */
	private static void headSmash(Context c) {
		float s = c.size();
		if (!phase(c).equals("hit")) {
			Entity user = c.move().user();
			Vec3 head = user.position().add(0, user.getBbHeight() * 0.8, 0).add(c.toTarget().scale(user.getBbWidth() * 0.5));
			float fade = phase(c).equals("wind") ? c.fade(0.2f, 0.05f) : 1;
			Draw.halo(c.glow(), c.m(), head, c.camera(), 0, 1.1f * s, c.color(), op(200 * fade));
			Draw.halo(c.glow(), c.m(), head, c.camera(), 0, 0.5f * s, 0xFFFFFF, op(200 * fade));
			return;
		}
		Vec3 at = c.aim();
		Vec3 feet = c.move().aimFeet();
		float k = c.k();
		Motifs.impact(c, at, seg(k, 0, 0.6f), 1.8f);
		rubble(c, at, seg(k, 0, 0.85f), Blocks.STONE.defaultBlockState(), 10, 3f, 251);
		heave(c, feet, seg(k, 0, 0.6f) * 3 * s, 2.6f * s, 0.3f * s, ground(feet));
		cracks(c, at, seg(k, 0.05f, 0.7f), s * 1.4f, c.accent());
		float recoil = seg(k, 0.35f, 0.65f);
		if (recoil > 0 && recoil < 1) {
			Draw.halo(c.glow(), c.m(), c.userCentre(), c.camera(), 0, (1 + recoil) * s, 0xFF6040, op(170 * (1 - recoil)));
		}
	}

	/** High Horsepower: three hoof stamps, each splitting the ground and throwing up a ring of dust. */
	private static void highHorsepower(Context c) {
		float s = c.size();
		Vec3 feet = c.move().aimFeet();
		Vec3 at = c.aim();
		Vec3 dir = flat(c.toTarget());
		for (int j = 0; j < 3; j++) {
			float p = seg(c.k(), j * 0.18f, j * 0.18f + 0.45f);
			if (p <= 0 || p >= 1) {
				continue;
			}
			Vec3 spot = feet.subtract(dir.scale((0.6 - 0.4 * j) * s)).add(side(dir).scale((j - 1) * 0.4 * s));
			float drop = seg(p, 0, 0.2f);
			if (drop < 1) {
				Vec3 hoof = spot.add(0, (1 - drop) * 1.8 * s + 0.2, 0);
				Draw.halo(c.glow(), c.m(), hoof, c.camera(), 0, 0.5f * s, c.color(), 230);
				Draw.streak(c.glow(), c.m(), hoof, hoof.add(0, 1.2 * s, 0), c.camera(), 0.3f * s, c.color(), 180);
				continue;
			}
			float q = seg(p, 0.2f, 1f);
			Draw.ring(c.glow(), c.m(), spot.add(0, 0.07, 0), UP, (0.3f + 1.8f * q) * s, 0.35f * s, 0xD8C8A0, op(220 * (1 - q)));
			cracks(c, spot.add(0, 0.05, 0), q, s * (0.9f + 0.2f * j), c.accent());
			Draw.halo(c.glow(), c.m(), spot.add(0, 0.4, 0), c.camera(), 0, (0.6f + q) * s, 0xE0D0B0, op(150 * (1 - q)));
		}
		float big = seg(c.k(), 0.36f, 0.9f);
		if (big > 0 && big < 1) {
			heave(c, feet, big * 3.2f * s, 2.8f * s, 0.3f * s, ground(feet));
			Motifs.impact(c, at, big, 1.3f);
		}
	}

	/** Grassy Glide: the user skimming over a carpet of grass that springs up along its path, leaves at the hit. */
	private static void grassyGlide(Context c) {
		float s = c.size();
		Vec3 from = c.move().user().position();
		Vec3 to = c.move().aimFeet();
		ItemStack grass = new ItemStack(Items.SHORT_GRASS);
		if (phase(c).equals("travel")) {
			float p = ease(c.k());
			Draw.streak(c.glow(), c.m(), from.add(0, 0.1, 0), from.lerp(to, p).add(0, 0.1, 0), c.camera(), 1.2f * s, c.color(), 150);
			for (int i = 0; i < 10; i++) {
				float f = i / 9f;
				if (f > p) {
					break;
				}
				Vec3 at = from.lerp(to, f).add(side(flat(to.subtract(from))).scale((i % 2 == 0 ? 0.4 : -0.4) * s));
				Motifs.item(c, grass, at.add(0, 0.3 * s, 0), 0.7f * s * Math.min(1, (p - f) * 6), i * 37, 0, 0);
			}
			return;
		}
		Vec3 at = c.aim();
		Motifs.impact(c, at, seg(c.k(), 0, 0.6f), 1.2f);
		rubble(c, at, seg(c.k(), 0, 0.9f), Blocks.AZALEA_LEAVES.defaultBlockState(), 6, 2.2f, 257);
		Draw.ring(c.glow(), c.m(), to.add(0, 0.07, 0), UP, (0.4f + 2.2f * c.k()) * s, 0.3f * s, c.color(), op(200 * (1 - c.k())));
	}

	/** Foul Play: the target's own shadow rising behind it as a fist and striking it with its own strength. */
	private static void foulPlay(Context c) {
		float s = c.size() * Math.max(1f, c.move().targetHeight() * 0.7f);
		Vec3 at = c.aim();
		Vec3 feet = c.move().aimFeet();
		Vec3 behind = feet.add(flat(c.toTarget()).scale(c.move().targetWidth() * 0.5 + 0.7));
		float k = c.k();
		float pool = 1 - seg(k, 0.75f, 1f);
		Draw.ring(c.glow(), c.m(), behind.add(0, 0.06, 0), UP, 0.55f * s, 0.25f * s, c.color(), op(200 * pool));
		float rise = ease(seg(k, 0, 0.3f));
		Vec3 top = behind.add(0, c.move().targetHeight() + 0.6, 0);
		shadowHand(c, behind, top, rise, pool * (1 - seg(k, 0.3f, 0.4f)), s * 0.8f);
		top = behind.lerp(top, rise);
		float eyes = rise * pool;
		Vec3[] b = c.screen(top);
		for (int side = -1; side <= 1; side += 2) {
			Draw.halo(c.glow(), c.m(), top.add(b[0].scale(side * 0.15 * s)).add(b[1].scale(0.05 * s)), c.camera(), 0, 0.12f * s, 0xFF3050, op(240 * eyes));
		}
		float strike = seg(k, 0.3f, 0.45f);
		if (strike > 0) {
			Vec3 fist = top.lerp(at, ease(strike));
			if (strike < 1) {
				Draw.halo(c.glow(), c.m(), fist, c.camera(), 0, 0.6f * s, c.color(), 230);
				Draw.streak(c.glow(), c.m(), fist, top, c.camera(), 0.4f * s, c.accent(), 200);
			}
			Motifs.impact(c, at, seg(k, 0.4f, 0.95f), 1.3f);
		}
	}

	/** Mortal Spin: the user spinning with rings of poison blades, then the target splashed in a toxic spray. */
	private static void mortalSpin(Context c) {
		float s = c.size();
		if (phase(c).equals("wind")) {
			Entity user = c.move().user();
			Vec3 centre = c.userCentre();
			float fade = c.fade(0.15f, 0.1f);
			float r = user.getBbWidth() * 0.6f + 0.7f * s;
			for (int j = 0; j < 2; j++) {
				Vec3 axis = UP.add(dirOf(c.t() * 0.2 + j * Math.PI).scale(0.4)).normalize();
				Vec3[] b = Draw.basis(axis);
				Draw.arc(c.glow(), c.m(), centre, b[0], b[1], r, c.t() * 0.8f + j * 3, c.t() * 0.8f + j * 3 + 4.5f, 1f, 0.3f * s, j == 0 ? c.color() : c.accent(),
						op(220 * fade));
			}
			return;
		}
		Vec3 at = c.aim();
		RandomSource jag = RandomSource.create(263);
		float q = c.k();
		for (int i = 0; i < 14; i++) {
			Vec3 d = randomUnit(jag).add(0, 0.5, 0).normalize();
			Vec3 drop = at.add(d.scale((0.3 + 1.9 * q) * s)).add(0, -1.8 * q * q * s, 0);
			Draw.sphere(c.glow(), c.m(), drop, 0.13f * s * (1 - q), c.color(), 200, true);
		}
		Draw.ring(c.glow(), c.m(), c.move().aimFeet().add(0, 0.07, 0), UP, (0.4f + 2.4f * q) * s, 0.35f * s, c.color(), op(200 * (1 - q)));
		Motifs.impact(c, at, seg(q, 0, 0.6f), 1f);
	}

	// =====================================================================
	// Special moves
	// =====================================================================

	/** Ruination: a dark sphere swallowing the target, a red vortex closing round it, then caving in. */
	private static void ruination(Context c) {
		float s = c.size();
		Vec3 at = c.aim();
		float k = c.k();
		float grow = ease(seg(k, 0, 0.35f));
		float collapse = seg(k, 0.5f, 0.65f);
		float r = (c.move().targetWidth() * 0.6f + 0.9f) * s * grow * (1 - collapse * 0.85f);
		if (collapse < 1) {
			Draw.sphere(c.buffers().getBuffer(Draw.DARK), c.m(), at, r, 0x200818, 190, false);
			Draw.halo(c.glow(), c.m(), at, c.camera(), r * 0.8f, r * 1.5f, c.accent(), 160);
			zigRing(c, at, Draw.basis(UP), r * 1.15f, r * 0.12f, 22, c.accent(), 220);
			for (int i = 0; i < 12; i++) {
				float p = ((c.t() * 0.06f) + i / 12f) % 1f;
				double angle = i * 0.52 + c.t() * 0.25 + p * 2;
				double rr = r * (1.8 - 0.8 * p);
				Vec3 a = at.add(Math.cos(angle) * rr, Math.sin(i * 1.3) * r * 0.4, Math.sin(angle) * rr);
				Vec3 b = at.add(Math.cos(angle - 0.5) * rr * 1.15, Math.sin(i * 1.3) * r * 0.4, Math.sin(angle - 0.5) * rr * 1.15);
				Draw.streak(c.glow(), c.m(), a, b, c.camera(), 0.1f * s, c.color(), op(220 * bell(p)));
			}
		}
		float boom = seg(k, 0.6f, 1f);
		if (boom > 0 && boom < 1) {
			Draw.halo(c.glow(), c.m(), at, c.camera(), 0, (0.5f + 2.5f * boom) * s, c.accent(), op(220 * (1 - boom)));
			Draw.ring(c.glow(), c.m(), at, c.camera().subtract(at).normalize(), (0.4f + 2.6f * boom) * s, 0.25f * s, c.color(), op(230 * (1 - boom)));
		}
	}

	/** Toxic Spikes: purple crystal spikes thrown in arcs, landing round the target and pulsing with poison. */
	private static void toxicSpikes(Context c) {
		float s = c.size();
		Vec3 from = c.mouth();
		Vec3 feet = c.move().aimFeet();
		BlockState spike = Blocks.AMETHYST_CLUSTER.defaultBlockState();
		float sink = seg(c.k(), 0.88f, 1f);
		for (int i = 0; i < 8; i++) {
			float p = seg(c.k(), i * 0.03f, i * 0.03f + 0.3f);
			if (p <= 0) {
				continue;
			}
			Vec3 land = feet.add(dirOf(Mth.TWO_PI * i / 8 + 0.3).scale((c.move().targetWidth() * 0.5 + 0.9 + 0.4 * (i % 2)) * s));
			if (p < 1) {
				Vec3 pos = from.lerp(land, p).add(0, Math.sin(p * Math.PI) * 2.2 * s, 0);
				Motifs.block(c, spike, pos, 0.5f * s, c.t() * 30 + i * 45, c.t() * 20, 0);
				Draw.halo(c.glow(), c.m(), pos, c.camera(), 0, 0.35f * s, c.color(), 180);
				continue;
			}
			Motifs.block(c, spike, land.add(0, (0.25 - sink * 0.5) * s, 0), 0.6f * s, i * 45, 0, 0);
			float pulse = 0.5f + 0.5f * Mth.sin(c.t() * 0.4f + i);
			Draw.halo(c.glow(), c.m(), land.add(0, 0.3 * s, 0), c.camera(), 0, 0.5f * s, c.color(), op(140 * pulse * (1 - sink)));
			float bub = ((c.t() * 0.05f) + i * 0.3f) % 1f;
			Vec3 b = land.add(0, 0.3 * s + bub * 0.8 * s, 0);
			Draw.ring(c.glow(), c.m(), b, c.camera().subtract(b).normalize(), (0.06f + 0.1f * bub) * s, 0.03f * s, c.accent(), op(220 * bell(bub) * (1 - sink)));
		}
	}

	/** Flash Cannon: silver light pulled into the user, fired as a ringed beam, a lens flare blazing on the target. */
	private static void flashCannon(Context c) {
		float s = c.size();
		Vec3 mouth = c.mouth();
		switch (phase(c)) {
			case "wind" -> {
				float g = c.k();
				RandomSource jag = RandomSource.create(269);
				for (int i = 0; i < 10; i++) {
					Vec3 d = randomUnit(jag);
					float p = (g * 1.5f + i * 0.1f) % 1f;
					Vec3 a = mouth.add(d.scale((1 - p) * 2 * s));
					Draw.streak(c.glow(), c.m(), a, a.add(d.scale(0.4 * s)), c.camera(), 0.06f * s, 0xFFFFFF, op(220 * bell(p)));
				}
				Draw.halo(c.glow(), c.m(), mouth, c.camera(), 0, (0.3f + 0.6f * g) * s, c.color(), 220);
				Draw.halo(c.glow(), c.m(), mouth, c.camera(), 0, 0.3f * g * s, 0xFFFFFF, 240);
			}
			case "travel" -> {
				Vec3 to = c.aim();
				Vec3 end = mouth.lerp(to, Math.min(1, c.k() * 1.5f));
				Vec3 dir = to.subtract(mouth).normalize();
				Draw.streak(c.glow(), c.m(), end, mouth, c.camera(), 0.8f * s, c.color(), 180);
				Draw.streak(c.glow(), c.m(), end, mouth, c.camera(), 0.3f * s, 0xFFFFFF, 240);
				double len = mouth.distanceTo(end);
				for (int i = 0; i < 5; i++) {
					double p = ((c.t() * 0.15) + i / 5.0) % 1.0;
					Draw.ring(c.glow(), c.m(), mouth.add(dir.scale(p * len)), dir, 0.55f * s, 0.08f * s, c.accent(), 220);
				}
			}
			default -> {
				Vec3 at = c.aim();
				float q = c.k();
				Motifs.burst(c, at, seg(q, 0, 0.7f), 1.2f);
				Vec3[] b = c.screen(at);
				float flare = 1 - seg(q, 0.2f, 0.8f);
				line(c, at.subtract(b[0].scale(3 * s)), at.add(b[0].scale(3 * s)), 0.1f * s, 0xFFFFFF, 240 * flare);
				twinkle(c, at, 1.5f * s, 0xFFFFFF, op(250 * flare));
				for (int i = 1; i <= 3; i++) {
					Vec3 ghost = at.add(b[0].scale(-i * 0.8 * s)).add(b[1].scale(-i * 0.3 * s));
					Draw.ring(c.glow(), c.m(), ghost, c.camera().subtract(ghost).normalize(), 0.2f * i * s, 0.04f * s, c.accent(), op(160 * flare));
				}
			}
		}
	}

	/** Aura Sphere: a blue sphere of aura formed in rings, flying in a curve that cannot miss, bursting in shock waves. */
	private static void auraSphere(Context c) {
		float s = c.size();
		Vec3 mouth = c.mouth();
		switch (phase(c)) {
			case "wind" -> auraBall(c, mouth.add(c.toTarget().scale(0.4 * s)), 0.45f * s * ease(c.k()));
			case "travel" -> {
				Vec3 to = c.aim();
				Vec3 mid = mouth.lerp(to, 0.5).add(side(flat(to.subtract(mouth))).scale(1.5 * s)).add(0, 0.8, 0);
				Vec3 pos = bezier(mouth, mid, to, ease(c.k()));
				auraBall(c, pos, 0.45f * s);
				for (int i = 1; i <= 4; i++) {
					Vec3 tail = bezier(mouth, mid, to, Math.max(0, ease(c.k()) - i * 0.07));
					Draw.halo(c.glow(), c.m(), tail, c.camera(), 0, (0.4f - i * 0.07f) * s, c.color(), 200 - i * 40);
				}
			}
			default -> {
				Vec3 at = c.aim();
				float q = c.k();
				for (int i = 0; i < 3; i++) {
					float p = seg(q, i * 0.1f, i * 0.1f + 0.6f);
					if (p > 0 && p < 1) {
						Draw.ring(c.glow(), c.m(), at, i == 1 ? UP : c.camera().subtract(at).normalize(), (0.4f + 2.6f * p) * s, 0.3f * s, i == 0 ? 0xFFFFFF : c.color(),
								op(230 * (1 - p)));
					}
				}
				Draw.halo(c.glow(), c.m(), at, c.camera(), 0, (0.8f + 1.5f * q) * s, c.color(), op(220 * (1 - q)));
				Motifs.impact(c, at, seg(q, 0, 0.6f), 1.1f);
			}
		}
	}

	private static void auraBall(Context c, Vec3 at, float r) {
		if (r <= 0.01f) {
			return;
		}
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, r * 2.4f, c.color(), 200);
		Draw.sphere(c.glow(), c.m(), at, r, c.color(), 160, true);
		Draw.sphere(c.glow(), c.m(), at, r * 0.55f, 0xFFFFFF, 220, true);
		for (int i = 0; i < 3; i++) {
			Vec3 axis = new Vec3(Math.cos(i * 2.1 + c.t() * 0.2), Math.sin(i * 1.3), Math.sin(i * 2.1 + c.t() * 0.2)).normalize();
			Draw.ring(c.glow(), c.m(), at, axis, r * 1.3f, 0.06f * r * 3, c.accent(), 220);
		}
	}

	/** Dragon Pulse: two streams twisting round each other from the user's mouth, a draconic flare on the target. */
	private static void dragonPulse(Context c) {
		float s = c.size();
		Vec3 mouth = c.mouth();
		Vec3 to = c.aim();
		if (phase(c).equals("travel")) {
			Vec3 axis = to.subtract(mouth);
			Vec3 end = mouth.add(axis.scale(Math.min(1, c.k() * 1.5f)));
			Vec3[] b = Draw.basis(axis.normalize());
			for (int j = 0; j < 2; j++) {
				Vec3[] pts = new Vec3[24];
				for (int i = 0; i < pts.length; i++) {
					double p = i / 23.0;
					double angle = c.t() * 0.9 + p * 16 + j * Math.PI;
					double r = (0.2 + 0.25 * p) * s;
					pts[i] = mouth.lerp(end, p).add(b[0].scale(Math.cos(angle) * r)).add(b[1].scale(Math.sin(angle) * r));
				}
				ribbon(c, pts, 0.12f * s, 0.3f * s, j == 0 ? c.color() : c.accent(), 160, 240);
			}
			Draw.halo(c.glow(), c.m(), end, c.camera(), 0, 0.9f * s, c.color(), 220);
			Draw.halo(c.glow(), c.m(), end, c.camera(), 0, 0.4f * s, 0xFFFFFF, 230);
			return;
		}
		float q = c.k();
		Motifs.burst(c, to, seg(q, 0, 0.7f), 1.2f);
		BlockState scale = Blocks.PURPLE_STAINED_GLASS.defaultBlockState();
		RandomSource jag = RandomSource.create(271);
		for (int i = 0; i < 8; i++) {
			Vec3 d = randomUnit(jag);
			crystalToward(c, scale, to.add(d.scale((0.4 + 2 * q) * s)), 0.2f * s * (1 - q), 0.45f * s * (1 - q), d);
		}
		Draw.ring(c.glow(), c.m(), to, c.toTarget(), (0.5f + 2.5f * q) * s, 0.3f * s, c.accent(), op(220 * (1 - q)));
	}

	/** Fiery Dance: the user whirling in a ring of rising flames, then flinging them in waving streams at the target. */
	private static void fieryDance(Context c) {
		float s = c.size();
		Entity user = c.move().user();
		switch (phase(c)) {
			case "wind" -> {
				float fade = c.fade(0.15f, 0.05f);
				float r = user.getBbWidth() * 0.6f + 0.6f * s;
				Signatures2.flames(c, user.position(), r, user.getBbHeight() * 0.6f, 14, s, fade);
				Vec3[] pts = new Vec3[24];
				for (int i = 0; i < pts.length; i++) {
					double p = i / 23.0;
					double angle = c.t() * 0.5 + p * 8;
					pts[i] = user.position().add(Math.cos(angle) * r * 0.8, p * user.getBbHeight() * 1.3, Math.sin(angle) * r * 0.8);
				}
				ribbon(c, pts, 0.15f * s, 0.05f * s, c.accent(), 230 * fade, 80 * fade);
			}
			case "travel" -> {
				Vec3 from = c.mouth();
				Vec3 to = c.aim();
				Vec3 sd = side(flat(to.subtract(from)));
				for (int j = 0; j < 3; j++) {
					Vec3[] pts = new Vec3[16];
					float head = Math.min(1, c.k() * 1.4f);
					for (int i = 0; i < pts.length; i++) {
						double p = head * i / 15.0;
						pts[i] = from.lerp(to, p).add(sd.scale(Math.sin(p * 8 + j * 2 + c.t() * 0.4) * 0.5 * s)).add(0, Math.cos(p * 6 + j) * 0.3 * s, 0);
					}
					ribbon(c, pts, 0.05f * s, 0.3f * s, j == 1 ? c.accent() : c.color(), 120, 240);
				}
			}
			default -> {
				Vec3 at = c.aim();
				blast(c, at, seg(c.k(), 0, 0.7f), 1f);
				Signatures2.flames(c, c.move().aimFeet(), c.move().targetWidth() * 0.5f + 0.25f, c.move().targetHeight(), 12, s, 1 - seg(c.k(), 0.7f, 1f));
			}
		}
	}

	/** Pyro Ball: a ball of fire kicked up from the ground and lobbed onto the target in an explosion. */
	private static void pyroBall(Context c) {
		float s = c.size();
		Entity user = c.move().user();
		Vec3 start = user.position().add(c.toTarget().scale(user.getBbWidth() * 0.6 + 0.3)).add(0, 0.35 * s, 0);
		if (phase(c).equals("wind")) {
			// Juggled on the foot while Cinderace winds up (its kick comes about two seconds in), growing hotter.
			float grow = ease(Math.min(1, c.k() * 3)) * (1 - seg(c.k(), 0.95f, 1f));
			float bounce = Math.abs(Mth.sin(c.t() * 0.3f)) * 0.7f * s;
			fireBall(c, start.add(0, bounce, 0), (0.2f + 0.2f * c.k()) * s * grow);
			return;
		}
		// The hit: the kick sends the ball in a flash, then it bursts into flames on the target.
		Vec3 at = c.aim();
		float fly = seg(c.k(), 0, 0.15f);
		if (fly < 1) {
			Vec3 pos = start.lerp(at, ease(fly));
			fireBall(c, pos, 0.42f * s);
			Draw.streak(c.glow(), c.m(), pos, start.lerp(at, Math.max(0, ease(fly) - 0.35f)), c.camera(), 0.55f * s, c.color(), 220);
		}
		blast(c, at, seg(c.k(), 0.12f, 0.85f), 1.4f);
		Signatures2.flames(c, c.move().aimFeet(), c.move().targetWidth() * 0.5f + 0.3f, c.move().targetHeight(), 12, s,
				seg(c.k(), 0.12f, 0.25f) * (1 - seg(c.k(), 0.7f, 1f)));
	}

	private static void fireBall(Context c, Vec3 at, float r) {
		if (r <= 0.01f) {
			return;
		}
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, r * 2.6f, c.color(), 200);
		Draw.sphere(c.glow(), c.m(), at, r, c.color(), 200, true);
		Draw.sphere(c.glow(), c.m(), at, r * 0.6f, c.accent(), 230, true);
		for (int i = 0; i < 6; i++) {
			double angle = i * 1.05 + c.t() * 0.4;
			Vec3 tip = at.add(Math.cos(angle) * r * 0.6, r * (1.2 + 0.4 * Math.sin(c.t() * 0.7 + i)), Math.sin(angle) * r * 0.6);
			Draw.streak(c.glow(), c.m(), tip, at, c.camera(), r * 0.5f, i % 2 == 0 ? c.accent() : c.color(), 220);
		}
	}

	/** Malignant Chain: poisoned chains coiling round the target and cinching tight, venom bursting out. */
	private static void malignantChain(Context c) {
		float s = c.size();
		Vec3 feet = c.move().aimFeet();
		Vec3 at = c.aim();
		float h = c.move().targetHeight();
		float wrap = ease(seg(c.k(), 0, 0.4f));
		float tight = seg(c.k(), 0.4f, 0.55f);
		float gone = seg(c.k(), 0.8f, 1f);
		float r = (c.move().targetWidth() * 0.5f + 0.4f) * (1.6f - 0.6f * wrap - 0.2f * tight);
		// Glowing links along a helix, each turned a quarter from the last like a real chain (item chains read dark and small).
		int n = 44;
		float link = 0.24f * s;
		float alive = 1 - gone;
		for (int i = 0; i < n; i++) {
			float f = i / (float) n;
			if (f > wrap) {
				break;
			}
			double angle = f * Mth.TWO_PI * 2 + c.t() * 0.1;
			Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
			Vec3 pos = feet.add(radial.scale(r)).add(0, 0.2 + f * h, 0);
			Vec3 tangent = new Vec3(-Math.sin(angle) * r * Mth.TWO_PI * 2, h, Math.cos(angle) * r * Mth.TWO_PI * 2).normalize();
			Vec3 normal = i % 2 == 0 ? radial : tangent.cross(radial).normalize();
			Draw.ring(c.glow(), c.m(), pos, normal, link * alive, 0.11f * s, c.color(), op(240 * alive));
			Draw.ring(c.glow(), c.m(), pos, normal, link * alive, 0.04f * s, c.accent(), op(255 * alive));
		}
		float burst = seg(c.k(), 0.5f, 0.95f);
		if (burst > 0 && burst < 1) {
			Draw.halo(c.glow(), c.m(), at, c.camera(), 0, (0.8f + 1.6f * burst) * s, c.color(), op(220 * (1 - burst)));
			RandomSource jag = RandomSource.create(277);
			for (int i = 0; i < 12; i++) {
				Vec3 d = randomUnit(jag).add(0, 0.4, 0).normalize();
				Draw.sphere(c.glow(), c.m(), at.add(d.scale((0.4 + 1.8 * burst) * s)).add(0, -burst * burst * 1.4 * s, 0), 0.12f * s * (1 - burst), c.accent(), 200, true);
			}
		}
	}

	/** Stored Power: rainbow arrows of every boost rising round the user, gathered into one surge of psychic light. */
	private static void storedPower(Context c) {
		float s = c.size();
		Entity user = c.move().user();
		switch (phase(c)) {
			case "wind" -> {
				float fade = c.fade(0.1f, 0.05f);
				for (int i = 0; i < 6; i++) {
					double angle = Mth.TWO_PI * i / 6 + c.t() * 0.1;
					float p = ((c.t() * 0.05f) + i / 6f) % 1f;
					Vec3 at = user.position().add(Math.cos(angle) * (user.getBbWidth() * 0.6 + 0.5), p * user.getBbHeight() * 1.4, Math.sin(angle) * (user.getBbWidth() * 0.6 + 0.5));
					chevron(c, at, 0.3f * s, RAINBOW[i % RAINBOW.length], 240 * fade * bell(p), true);
				}
				Draw.halo(c.glow(), c.m(), c.userCentre(), c.camera(), 0, (user.getBbWidth() + 1.3f) * s, c.color(), op(120 * fade));
			}
			case "travel" -> {
				Vec3 from = c.mouth();
				Vec3 to = c.aim();
				Vec3 end = from.lerp(to, Math.min(1, c.k() * 1.5f));
				for (int i = 0; i < 6; i++) {
					Vec3 off = Draw.basis(to.subtract(from).normalize())[i % 2].scale((i / 2 - 1) * 0.15 * s);
					Draw.streak(c.glow(), c.m(), end.add(off), from.add(off), c.camera(), 0.12f * s, RAINBOW[i % RAINBOW.length], 220);
				}
				Draw.halo(c.glow(), c.m(), end, c.camera(), 0, 0.8f * s, 0xFFFFFF, 220);
			}
			default -> {
				Vec3 at = c.aim();
				float q = c.k();
				Motifs.burst(c, at, seg(q, 0, 0.7f), 1.2f);
				for (int i = 0; i < 6; i++) {
					Vec3 d = dirOf(Mth.TWO_PI * i / 6).add(0, Math.sin(i) * 0.5, 0).normalize();
					Draw.streak(c.glow(), c.m(), at.add(d.scale((0.4 + 2.2 * q) * s)), at.add(d.scale((0.2 + 1.2 * q) * s)), c.camera(), 0.15f * s,
							RAINBOW[i % RAINBOW.length], op(230 * (1 - q)));
				}
			}
		}
	}

	/** Air Slash: three crescent blades of air flying in a row, cutting a white X into the target. */
	private static void airSlash(Context c) {
		float s = c.size();
		Vec3 from = c.mouth();
		Vec3 to = c.aim();
		if (phase(c).equals("travel")) {
			for (int i = 0; i < 3; i++) {
				float p = Mth.clamp(c.k() * 1.5f - i * 0.2f, 0, 1);
				if (p <= 0 || p >= 1) {
					continue;
				}
				Vec3 pos = from.lerp(to, p).add(0, (i - 1) * 0.3 * s, 0);
				Vec3[] b = c.screen(pos);
				double spin = c.t() * 0.6 + i;
				Vec3 u = b[0].scale(Math.cos(spin)).add(b[1].scale(Math.sin(spin)));
				Vec3 v = b[0].scale(-Math.sin(spin)).add(b[1].scale(Math.cos(spin)));
				Draw.arc(c.glow(), c.m(), pos, u, v, 0.7f * s, 0, 3.2f, 1f, 0.35f * s, c.color(), 230);
				Draw.arc(c.glow(), c.m(), pos, u, v, 0.68f * s, 0, 3.2f, 1f, 0.1f * s, 0xFFFFFF, 250);
			}
			return;
		}
		Vec3[] b = c.screen(to);
		float q = c.k();
		float fade = 1 - seg(q, 0.4f, 0.8f);
		for (int j = 0; j < 2; j++) {
			float p = ease(seg(q, j * 0.12f, j * 0.12f + 0.2f));
			Vec3 d = b[0].scale(j == 0 ? 1 : -1).add(b[1]).normalize();
			Vec3 a = to.add(d.scale(1.3 * s));
			line(c, a, a.lerp(to.subtract(d.scale(1.3 * s)), p), 0.2f * s, 0xFFFFFF, 245 * fade);
			line(c, a, a.lerp(to.subtract(d.scale(1.3 * s)), p), 0.45f * s, c.color(), 160 * fade);
		}
		Motifs.impact(c, to, seg(q, 0.1f, 0.8f), 1f);
	}

	/** Pain Split: a bond of light between the two, a pulse running both ways along it, both glowing evened out. */
	private static void painSplit(Context c) {
		float s = c.size();
		Vec3 a = c.userCentre();
		Vec3 b = c.aim();
		Vec3 mid = a.lerp(b, 0.5).add(0, 1.2, 0);
		float link = ease(seg(c.k(), 0, 0.3f));
		float fade = 1 - seg(c.k(), 0.85f, 1f);
		Vec3[] pts = new Vec3[20];
		for (int i = 0; i < pts.length; i++) {
			double p = i / 19.0 * link;
			pts[i] = bezier(a, mid, b, p).add(0, Math.sin(p * 18 + c.t() * 0.5) * 0.08, 0);
		}
		ribbon(c, pts, 0.15f * s, 0.15f * s, c.color(), 220 * fade, 220 * fade);
		float pulse = seg(c.k(), 0.3f, 0.7f);
		if (pulse > 0 && pulse < 1) {
			for (int i = 0; i < 2; i++) {
				Vec3 at = bezier(a, mid, b, i == 0 ? pulse : 1 - pulse);
				Draw.halo(c.glow(), c.m(), at, c.camera(), 0, 0.6f * s, 0xFFFFFF, 240);
				Draw.halo(c.glow(), c.m(), at, c.camera(), 0, 1f * s, c.accent(), 180);
			}
			twinkle(c, mid, 0.8f * s * bell(pulse), 0xFFFFFF, op(230 * bell(pulse)));
		}
		float even = seg(c.k(), 0.65f, 0.95f);
		if (even > 0 && even < 1) {
			for (Vec3 end : new Vec3[] { a, b }) {
				Draw.halo(c.glow(), c.m(), end, c.camera(), 0, 1.4f * s, c.accent(), op(170 * bell(even)));
				Draw.ring(c.glow(), c.m(), end, UP, (0.6f + even) * s, 0.15f * s, c.color(), op(200 * bell(even)));
			}
		}
	}

	/** Parting Shot: stat arrows falling on the target, then the user spinning away in a swirl. */
	private static void partingShot(Context c) {
		float s = c.size();
		if (c.move().target != null) {
			statArrows(c, c.move().target, 0xFF4040, 240 * c.fade(0.1f, 0.3f), false);
		}
		Vec3 at = c.aim();
		Vec3[] b = c.screen(at);
		float sneer = 1 - seg(c.k(), 0.4f, 0.7f);
		for (int i = 0; i < 3; i++) {
			double angle = Math.PI / 2 + (i - 1) * 0.5;
			Vec3 d = b[0].scale(Math.cos(angle)).add(b[1].scale(Math.sin(angle)));
			Vec3 root = at.add(d.scale((c.move().targetHeight() * 0.5 + 0.3) * s));
			line(c, root, root.add(d.scale(0.5 * s)), 0.08f * s, c.accent(), 230 * sneer);
		}
		Entity user = c.move().user();
		float leave = seg(c.k(), 0.4f, 0.95f);
		if (leave > 0 && leave < 1) {
			Vec3[] pts = new Vec3[20];
			for (int i = 0; i < pts.length; i++) {
				double p = i / 19.0;
				double angle = -c.t() * 0.7 - p * 9;
				double r = (user.getBbWidth() * 0.7 + 0.4) * (1 - leave * 0.6);
				pts[i] = user.position().add(Math.cos(angle) * r, p * user.getBbHeight() * 1.3 + leave, Math.sin(angle) * r);
			}
			ribbon(c, pts, 0.14f * s, 0.14f * s, c.color(), 230 * bell(leave), 100 * bell(leave));
		}
	}

	/** Dark Pulse: rings of darkness pulsing out of the user in every direction, sweeping over the target. */
	private static void darkPulse(Context c) {
		float s = c.size();
		Vec3 centre = c.userCentre();
		if (phase(c).equals("wind")) {
			float fade = c.fade(0.1f, 0.1f);
			Draw.sphere(c.buffers().getBuffer(Draw.DARK), c.m(), centre, (0.5f + 0.4f * c.k()) * s, 0x1A0A28, op(180 * fade), false);
			Draw.halo(c.glow(), c.m(), centre, c.camera(), 0, 1.5f * s, c.accent(), op(140 * fade));
			return;
		}
		float reach = (float) centre.distanceTo(c.aim()) + 2;
		for (int i = 0; i < 4; i++) {
			float p = seg(c.k(), i * 0.08f, i * 0.08f + 0.6f);
			if (p <= 0 || p >= 1) {
				continue;
			}
			float r = 0.5f + reach * p;
			Draw.ring(c.glow(), c.m(), centre, UP, r, 0.4f * s, c.color(), op(210 * (1 - p)));
			Draw.ring(c.glow(), c.m(), centre, c.camera().subtract(centre).normalize(), r * 0.9f, 0.25f * s, c.accent(), op(170 * (1 - p)));
			int n = Math.max(8, Math.round(r * 3));
			for (int j = 0; j < n; j++) {
				double angle = Mth.TWO_PI * j / n + i;
				Vec3 blob = centre.add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
				Draw.sphere(c.buffers().getBuffer(Draw.DARK), c.m(), blob, 0.13f * s * (1 - p), 0x1A0A28, 200, false);
			}
		}
		Vec3 at = c.aim();
		float hit = seg(c.k(), 0.3f, 0.9f);
		if (hit > 0 && hit < 1) {
			Draw.halo(c.glow(), c.m(), at, c.camera(), 0, (1 + hit) * 1.3f * s, c.color(), op(200 * (1 - hit)));
		}
	}

	/** Court Change: two orbs of light trading sides of the field in a half turn, rings flashing at both ends. */
	private static void courtChange(Context c) {
		float s = c.size();
		Vec3 a = c.move().user().position().add(0, 0.6, 0);
		Vec3 b = c.move().aimFeet().add(0, 0.6, 0);
		Vec3 mid = a.lerp(b, 0.5);
		double r = a.distanceTo(b) * 0.5;
		Vec3 axis = a.subtract(mid).normalize();
		Vec3 across = side(flat(axis));
		float turn = ease(seg(c.k(), 0.15f, 0.75f));
		for (int i = 0; i < 2; i++) {
			double angle = turn * Math.PI + i * Math.PI;
			Vec3 pos = mid.add(axis.scale(Math.cos(angle) * r)).add(across.scale(Math.sin(angle) * r * 0.6));
			int color = i == 0 ? c.color() : c.accent();
			Draw.halo(c.glow(), c.m(), pos, c.camera(), 0, 0.8f * s, color, 230);
			Draw.sphere(c.glow(), c.m(), pos, 0.25f * s, 0xFFFFFF, 220, true);
			Draw.arc(c.glow(), c.m(), mid, axis, across.scale(0.6), (float) r, (float) (i * Math.PI), (float) (i * Math.PI + turn * Math.PI), 1f, 0.2f * s, color, 200);
		}
		for (Vec3 end : new Vec3[] { a, b }) {
			for (float at : new float[] { 0.1f, 0.75f }) {
				float q = seg(c.k(), at, at + 0.2f);
				if (q > 0 && q < 1) {
					Draw.ring(c.glow(), c.m(), end.add(0, -0.5, 0), UP, (0.4f + 1.6f * q) * s, 0.2f * s, c.color(), op(220 * (1 - q)));
				}
			}
		}
	}

	/** Overheat: the user blazing white-hot, a torrent of fire, a huge explosion, then the user steaming as it cools. */
	private static void overheat(Context c) {
		float s = c.size();
		Entity user = c.move().user();
		switch (phase(c)) {
			case "wind" -> {
				float fade = c.fade(0.15f, 0.05f);
				Signatures2.flames(c, user.position(), user.getBbWidth() * 0.6f + 0.3f, user.getBbHeight() * 1.3f, 20, s, fade);
				Draw.halo(c.glow(), c.m(), c.userCentre(), c.camera(), 0, (user.getBbWidth() + 1.8f) * s, 0xFFF0C0, op(160 * fade * c.k()));
			}
			case "travel" -> {
				Vec3 from = c.mouth();
				Vec3 to = c.aim();
				Vec3 end = from.lerp(to, Math.min(1, c.k() * 1.5f));
				Draw.streak(c.glow(), c.m(), end, from, c.camera(), 1.1f * s, c.color(), 200);
				Draw.streak(c.glow(), c.m(), end, from, c.camera(), 0.5f * s, c.accent(), 230);
				Draw.streak(c.glow(), c.m(), end, from, c.camera(), 0.18f * s, 0xFFFFFF, 240);
			}
			default -> {
				Vec3 at = c.aim();
				blast(c, at, seg(c.k(), 0, 0.8f), 1.8f);
				Signatures2.flames(c, c.move().aimFeet(), c.move().targetWidth() * 0.6f + 0.3f, c.move().targetHeight() * 1.2f, 16, s, 1 - seg(c.k(), 0.7f, 1f));
				float steam = seg(c.k(), 0.3f, 1f);
				if (steam > 0 && steam < 1) {
					for (int i = 0; i < 5; i++) {
						float p = (steam * 1.5f + i * 0.2f) % 1f;
						Vec3 puff = user.position().add(Math.cos(i * 1.3) * 0.4, user.getBbHeight() * (0.8 + p), Math.sin(i * 1.3) * 0.4);
						Draw.halo(c.glow(), c.m(), puff, c.camera(), 0, (0.4f + 0.4f * p) * s, 0xE8F0F8, op(120 * bell(p)));
					}
				}
			}
		}
	}

	/** Sticky Web: threads shot over the target, a web spun flat on the ground round its feet. */
	private static void stickyWeb(Context c) {
		float s = c.size();
		Vec3 from = c.mouth();
		Vec3 feet = c.move().aimFeet().add(0, 0.08, 0);
		float shoot = seg(c.k(), 0, 0.3f);
		if (shoot < 1) {
			for (int i = 0; i < 3; i++) {
				Vec3 land = feet.add(dirOf(i * 2.1).scale(1.4 * s));
				Vec3 mid = from.lerp(land, 0.5).add(0, 1.4, 0);
				Vec3 head = bezier(from, mid, land, ease(shoot));
				line(c, from, head, 0.04f * s, 0xFFFFFF, 220);
			}
		}
		float spin = ease(seg(c.k(), 0.2f, 0.7f));
		float fade = 1 - seg(c.k(), 0.88f, 1f);
		float r = (c.move().targetWidth() * 0.6f + 1.4f) * s;
		int spokes = 10;
		for (int i = 0; i < spokes; i++) {
			Vec3 d = dirOf(Mth.TWO_PI * i / spokes);
			line(c, feet, feet.add(d.scale(r * Math.min(1, spin * 1.5))), 0.05f * s, 0xFFFFFF, 220 * fade);
		}
		int rings = 5;
		for (int j = 1; j <= rings; j++) {
			float rr = r * j / rings;
			float shown = Mth.clamp(spin * rings - (j - 1), 0, 1);
			for (int i = 0; i < spokes * shown; i++) {
				Vec3 p0 = feet.add(dirOf(Mth.TWO_PI * i / spokes).scale(rr));
				Vec3 p1 = feet.add(dirOf(Mth.TWO_PI * (i + 1) / spokes).scale(rr));
				line(c, p0, p0.lerp(p1, 1).add(0, 0, 0).subtract(p0.subtract(p1).scale(0)), 0.035f * s, 0xFFFFFF, 200 * fade);
			}
		}
		twinkle(c, feet.add(dirOf(c.t() * 0.1).scale(r * 0.6)).add(0, 0.05, 0), 0.4f * s, c.accent(), op(200 * fade * spin));
	}

	/** Roar: a great cone of sound from the user's mouth, the target buffeted by gusts and driven back. */
	private static void roar(Context c) {
		float s = c.size();
		Vec3 mouth = c.mouth();
		Vec3 to = c.aim();
		Vec3 dir = to.subtract(mouth).normalize();
		double dist = mouth.distanceTo(to);
		float fade = c.fade(0.05f, 0.3f);
		for (int i = 0; i < 6; i++) {
			float p = ((c.t() * 0.09f) + i / 6f) % 1f;
			Vec3 at = mouth.add(dir.scale(p * (dist + 1.5)));
			float r = (0.4f + 1.8f * p) * s;
			Draw.ring(c.glow(), c.m(), at, dir, r, (0.25f - 0.1f * p) * s, i % 2 == 0 ? c.color() : 0xFFFFFF, op(230 * fade * (1 - p * 0.6f)));
		}
		float gust = seg(c.k(), 0.3f, 1f);
		if (gust > 0 && gust < 1) {
			Vec3[] b = Draw.basis(dir);
			for (int i = 0; i < 8; i++) {
				Vec3 o = to.add(b[0].scale(Math.cos(i * 0.8) * 0.6 * s)).add(b[1].scale(Math.sin(i * 0.8) * 0.6 * s));
				float p = (gust * 2 + i * 0.13f) % 1f;
				Draw.streak(c.glow(), c.m(), o.add(dir.scale((0.5 + 2.5 * p) * s)), o.add(dir.scale((p * 2.5) * s)), c.camera(), 0.07f * s, 0xFFFFFF,
						op(220 * bell(p)));
			}
		}
	}

	/** Leaf Storm: a funnel of real leaves storming from the user onto the target and whirling round it. */
	private static void leafStorm(Context c) {
		float s = c.size();
		Vec3 from = c.userCentre();
		Vec3 to = c.aim();
		Vec3 axis = to.subtract(from);
		Vec3[] b = Draw.basis(axis.normalize());
		BlockState[] kinds = { Blocks.AZALEA_LEAVES.defaultBlockState(), Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState(),
				Blocks.OAK_LEAVES.defaultBlockState() };
		float fade = c.fade(0.05f, 0.25f);
		for (int i = 0; i < 24; i++) {
			float p = ((c.t() * 0.04f) + i / 24f) % 1f;
			double angle = i * 0.9 + c.t() * 0.35 + p * 6;
			double r = (0.3 + 1.1 * p) * s;
			Vec3 pos;
			if (p < 0.6f) {
				pos = from.add(axis.scale(p / 0.6)).add(b[0].scale(Math.cos(angle) * r)).add(b[1].scale(Math.sin(angle) * r));
			} else {
				double rr = (c.move().targetWidth() * 0.6 + 0.9) * s;
				pos = c.move().aimFeet().add(Math.cos(angle) * rr, (p - 0.6) / 0.4 * c.move().targetHeight() * 1.6, Math.sin(angle) * rr);
			}
			Motifs.block(c, kinds[i % kinds.length], pos, 0.22f * s * fade, c.t() * 25 + i * 40, c.t() * 20 + i * 15, i * 20);
		}
		Draw.halo(c.glow(), c.m(), to, c.camera(), 0, 2 * s, c.color(), op(90 * fade));
	}

	/** Energy Ball: a ball of nature's power wrapped in swirling leaves, bursting green on the target. */
	private static void energyBall(Context c) {
		float s = c.size();
		Vec3 mouth = c.mouth();
		switch (phase(c)) {
			case "wind" -> natureBall(c, mouth.add(c.toTarget().scale(0.4 * s)), 0.4f * s * ease(c.k()));
			case "travel" -> natureBall(c, mouth.lerp(c.aim(), ease(c.k())), 0.45f * s);
			default -> {
				Vec3 at = c.aim();
				float q = c.k();
				Draw.halo(c.glow(), c.m(), at, c.camera(), 0, (0.8f + 2f * q) * s, c.color(), op(220 * (1 - q)));
				Draw.ring(c.glow(), c.m(), at, c.camera().subtract(at).normalize(), (0.5f + 2.2f * q) * s, 0.25f * s, c.accent(), op(230 * (1 - q)));
				rubble(c, at, seg(q, 0, 0.9f), Blocks.AZALEA_LEAVES.defaultBlockState(), 7, 2.4f, 281);
			}
		}
	}

	private static void natureBall(Context c, Vec3 at, float r) {
		if (r <= 0.01f) {
			return;
		}
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, r * 2.5f, c.color(), 200);
		Draw.sphere(c.glow(), c.m(), at, r, c.color(), 170, true);
		Draw.sphere(c.glow(), c.m(), at, r * 0.5f, c.accent(), 230, true);
		for (int i = 0; i < 4; i++) {
			double angle = c.t() * 0.4 + i * Mth.TWO_PI / 4;
			Vec3 leaf = at.add(Math.cos(angle) * r * 1.5, Math.sin(angle * 2) * r * 0.4, Math.sin(angle) * r * 1.5);
			Motifs.block(c, Blocks.AZALEA_LEAVES.defaultBlockState(), leaf, r * 0.5f, c.t() * 30 + i * 60, c.t() * 20, 0);
		}
	}

	/** Hydro Pump: two thick twisting jets of water blasting the target, a column of spray thrown up behind it. */
	private static void hydroPump(Context c) {
		float s = c.size();
		Vec3 mouth = c.mouth();
		Vec3 to = c.aim();
		Vec3 sd = side(flat(to.subtract(mouth)));
		if (phase(c).equals("wind")) {
			float g = c.k();
			Draw.halo(c.glow(), c.m(), mouth, c.camera(), 0, (0.3f + 0.7f * g) * s, c.color(), 220);
			Draw.ring(c.glow(), c.m(), mouth, c.toTarget(), (1.2f - 0.8f * g) * s, 0.1f * s, 0xFFFFFF, op(220 * g));
			return;
		}
		boolean hit = phase(c).equals("hit");
		float head = hit ? 1 : Math.min(1, c.k() * 1.5f);
		float fade = hit ? 1 - seg(c.k(), 0.4f, 0.7f) : 1;
		for (int i = -1; i <= 1; i += 2) {
			Vec3 from = mouth.add(sd.scale(i * 0.25 * s));
			waterJet(c, from, from.lerp(to, head), 0.45f * s, fade);
		}
		if (hit) {
			Vec3 behind = c.move().aimFeet().add(flat(c.toTarget()).scale(c.move().targetWidth() * 0.5 + 0.6));
			float col = seg(c.k(), 0.05f, 0.8f);
			if (col > 0 && col < 1) {
				Draw.streak(c.glow(), c.m(), behind, behind.add(0, 3 * s * Math.sin(col * Math.PI), 0).add(flat(c.toTarget()).scale(0.8)), c.camera(), 1f * s,
						c.color(), op(180 * (1 - col)));
			}
			splashCrownAt(c, c.move().aimFeet(), seg(c.k(), 0, 1f), s * 1.3f);
		}
	}

	/** Hyper Voice: bold rings of sound in alternating colours blasting out, the target shaken inside a ring of them. */
	private static void hyperVoice(Context c) {
		float s = c.size();
		Vec3 mouth = c.mouth();
		Vec3 to = c.aim();
		if (phase(c).equals("travel")) {
			Vec3 dir = to.subtract(mouth).normalize();
			double dist = mouth.distanceTo(to);
			for (int i = 0; i < 5; i++) {
				float p = ((c.k() * 1.4f) + i / 5f) % 1f;
				Vec3 at = mouth.add(dir.scale(p * dist));
				Draw.ring(c.glow(), c.m(), at, dir, (0.4f + 1.2f * p) * s, 0.3f * s, RAINBOW[i % RAINBOW.length], 230);
				Draw.ring(c.glow(), c.m(), at, dir, (0.4f + 1.2f * p) * s, 0.08f * s, 0xFFFFFF, 240);
			}
			return;
		}
		float q = c.k();
		for (int i = 0; i < 3; i++) {
			float p = seg(q, i * 0.12f, i * 0.12f + 0.55f);
			if (p > 0 && p < 1) {
				Draw.ring(c.glow(), c.m(), to, c.camera().subtract(to).normalize(), (0.6f + 2.4f * p) * s, 0.3f * s, RAINBOW[(i * 2) % RAINBOW.length],
						op(230 * (1 - p)));
			}
		}
		Motifs.impact(c, to, seg(q, 0, 0.6f), 1.1f);
	}

	/** Ice Shard: shards of ice fired in a quick volley, shattering into glittering frost on the target. */
	private static void iceShard(Context c) {
		float s = c.size();
		Vec3 from = c.mouth();
		Vec3 to = c.aim();
		BlockState ice = Blocks.PACKED_ICE.defaultBlockState();
		if (phase(c).equals("travel")) {
			for (int i = 0; i < 3; i++) {
				float p = Mth.clamp(c.k() * 1.6f - i * 0.18f, 0, 1);
				if (p <= 0 || p >= 1) {
					continue;
				}
				Vec3 end = to.add(0, (i - 1) * 0.3 * s, 0);
				Vec3 pos = from.lerp(end, p);
				crystalToward(c, ice, pos, 0.25f * s, 0.8f * s, end.subtract(from));
				Draw.streak(c.glow(), c.m(), pos, from.lerp(end, Math.max(0, p - 0.3f)), c.camera(), 0.2f * s, c.color(), 200);
			}
			return;
		}
		float q = c.k();
		RandomSource jag = RandomSource.create(283);
		for (int i = 0; i < 10; i++) {
			Vec3 d = randomUnit(jag).add(c.toTarget().scale(-0.6)).normalize();
			crystalToward(c, ice, to.add(d.scale((0.3 + 1.8 * q) * s)).add(0, -q * q * s, 0), 0.15f * s * (1 - q), 0.4f * s * (1 - q), d);
			twinkle(c, to.add(d.scale((0.5 + 2.2 * q) * s)), 0.25f * s, 0xFFFFFF, op(230 * (1 - q)));
		}
		Motifs.impact(c, to, seg(q, 0, 0.6f), 0.9f);
	}

	/** Thunder: a storm cloud gathering over the target and a huge bolt crashing down, the field flashing white. */
	private static void thunder(Context c) {
		float s = c.size();
		Vec3 feet = c.move().aimFeet();
		Vec3 at = c.aim();
		float k = c.k();
		Vec3 cloud = feet.add(0, c.move().targetHeight() + 1.8 * s, 0);
		float gather = ease(seg(k, 0, 0.25f)) * (1 - seg(k, 0.8f, 1f));
		RandomSource jag = RandomSource.create(293);
		for (int i = 0; i < 16; i++) {
			Vec3 puff = cloud.add(jag.nextGaussian() * 1.0 * s, jag.nextGaussian() * 0.18, jag.nextGaussian() * 0.7 * s);
			Draw.sphere(c.buffers().getBuffer(Draw.DARK), c.m(), puff, (0.28f + 0.2f * jag.nextFloat()) * s * gather, 0x505868, 190, false);
		}
		float strike = seg(k, 0.22f, 0.6f);
		if (strike > 0 && strike < 1) {
			float w = (1 - strike) * 0.3f * s;
			for (int i = 0; i < 3; i++) {
				Motifs.bolt(c.glow(), c.m(), c.camera(), cloud.add((i - 1) * 0.3, 0, 0), feet, c.t() + i * 3, w * (i == 1 ? 1 : 0.5f), i == 1 ? 0xFFFFFF : c.color(),
						i * 11);
			}
			Draw.halo(c.glow(), c.m(), at, c.camera(), 0, 4 * s, 0xFFFFFF, op(230 * (1 - seg(strike, 0, 0.3f))));
			Draw.ring(c.glow(), c.m(), feet.add(0, 0.07, 0), UP, (0.5f + 4f * strike) * s, 0.4f * s, c.color(), op(220 * (1 - strike)));
			Draw.halo(c.glow(), c.m(), cloud, c.camera(), 0, 2.5f * s, c.color(), op(160 * (1 - strike)));
		}
		if (k > 0.22f && k < 0.8f) {
			sparks(c, at, c.move().targetWidth() * 0.6f + 0.4f * s, 5, 0.05f * s);
		}
	}

	/** Discharge: the user crackling, then a dome of lightning bursting out of it, bolts lashing in every direction. */
	private static void discharge(Context c) {
		float s = c.size();
		Entity user = c.move().user();
		Vec3 centre = c.userCentre();
		if (phase(c).equals("wind")) {
			sparks(c, centre, user.getBbWidth() * 0.6f + 0.3f, 4, 0.04f * s);
			Draw.halo(c.glow(), c.m(), centre, c.camera(), 0, (user.getBbWidth() + 0.8f + c.k()) * s, c.color(), op(140 * c.k()));
			return;
		}
		float q = c.k();
		float reach = ease(seg(q, 0, 0.35f));
		float fade = 1 - seg(q, 0.5f, 0.9f);
		float r = (user.getBbWidth() * 0.6f + 0.6f + 2.4f * reach) * s;
		Draw.ring(c.glow(), c.m(), user.position().add(0, 0.07, 0), UP, r, 0.4f * s, c.color(), op(220 * fade));
		for (int i = 0; i < 10; i++) {
			Vec3 d = dirOf(Mth.TWO_PI * i / 10 + 0.2).add(0, Math.sin(i * 1.7) * 0.6, 0).normalize();
			Motifs.bolt(c.glow(), c.m(), c.camera(), centre, centre.add(d.scale(r)), c.t() + i, 0.07f * s * fade, i % 2 == 0 ? 0xFFFFFF : c.color(), i);
		}
		Motifs.bolt(c.glow(), c.m(), c.camera(), centre, c.aim(), c.t(), 0.12f * s * fade, 0xFFFFFF, 99);
		Motifs.impact(c, c.aim(), seg(q, 0.15f, 0.8f), 1f);
	}
}
