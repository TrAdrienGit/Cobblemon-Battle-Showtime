package com.mystaria.showtime.move;

import java.util.Set;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mystaria.showtime.battle.BattleCameraDirector;
import com.mystaria.showtime.move.Motifs.Context;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
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
 * Signature motifs (2026-11-26): one drawing of its own for each of the most played moves (Smogon National Dex, first
 * batch of 50), where the shared {@link Motifs} read faint, generic or too alike. Each is named after its move and
 * may play in several phases (a wind-up, a travel and a hit drawn by the same motif). Real blocks and items do the
 * matter (ground heaving, stones, coins, swords, a vine), additive light the energy.
 *
 * <p>One vertex buffer at a time: every glowing shape asks {@code c.glow()} again, as a block or an item in between
 * takes the buffer source for itself.
 */
final class Signatures {

	private static final Vec3 UP = new Vec3(0, 1, 0);

	private static final Set<String> NAMES = Set.of("earthquake", "knockoff", "stealthrock", "uturn", "flipturn", "swordsdance",
			"roost", "toxic", "rapidspin", "defog", "earthpower", "recover", "moonblast", "substitute", "stoneedge", "focusblast",
			"voltswitch", "icespinner", "thunderbolt", "calmmind", "suckerpunch", "dracometeor", "bodypress", "mysticalfire",
			"nastyplot", "hurricane", "thunderwave", "taunt_rage", "ironhead", "psyshock", "crunch", "headlongrush", "pursuit",
			"wish", "scald", "terastarstorm", "spikes", "makeitrain", "futuresight", "sludgebomb", "willowisp", "powerwhip");

	private Signatures() {
	}

	/** Draws the layer if its motif is a signature one; false leaves it to {@link Motifs}. */
	static boolean render(Context c) {
		switch (c.layer().motif) {
			case "earthquake" -> earthquake(c);
			case "knockoff" -> knockOff(c);
			case "stealthrock" -> stealthRock(c);
			case "uturn" -> uTurn(c, false);
			case "flipturn" -> uTurn(c, true);
			case "swordsdance" -> swordsDance(c);
			case "roost" -> roost(c);
			case "toxic" -> toxic(c);
			case "rapidspin" -> rapidSpin(c);
			case "defog" -> defog(c);
			case "earthpower" -> earthPower(c);
			case "recover" -> recover(c);
			case "moonblast" -> moonblast(c);
			case "substitute" -> substitute(c);
			case "stoneedge" -> stoneEdge(c);
			case "focusblast" -> focusBlast(c);
			case "voltswitch" -> voltSwitch(c);
			case "icespinner" -> iceSpinner(c);
			case "thunderbolt" -> thunderbolt(c);
			case "calmmind" -> calmMind(c);
			case "suckerpunch" -> suckerPunch(c);
			case "dracometeor" -> dracoMeteor(c);
			case "bodypress" -> bodyPress(c);
			case "mysticalfire" -> mysticalFire(c);
			case "nastyplot" -> nastyPlot(c);
			case "hurricane" -> hurricane(c);
			case "thunderwave" -> thunderWave(c);
			case "taunt_rage" -> tauntRage(c);
			case "ironhead" -> ironHead(c);
			case "psyshock" -> psyshock(c);
			case "crunch" -> crunch(c);
			case "headlongrush" -> headlongRush(c);
			case "pursuit" -> pursuit(c);
			case "wish" -> wish(c);
			case "scald" -> scald(c);
			case "terastarstorm" -> teraStarstorm(c);
			case "spikes" -> spikes(c);
			case "makeitrain" -> makeItRain(c);
			case "futuresight" -> futureSight(c);
			case "sludgebomb" -> sludgeBomb(c);
			case "willowisp" -> willOWisp(c);
			case "powerwhip" -> powerWhip(c);
			default -> {
				return false;
			}
		}
		return true;
	}

	/**
	 * The layer's particles if its motif is a signature one (false leaves it to {@link Motifs}): its own where it has
	 * some, else the layer's particles burst once, on the user for a move on itself, on the target for a hit.
	 */
	static boolean tick(Context c, ClientLevel level) {
		String motif = c.layer().motif;
		if (!NAMES.contains(motif)) {
			return false;
		}
		switch (motif) {
			case "earthquake" -> earthquakeTick(c, level);
			case "earthpower" -> {
				if (crossed(c, 0.3f)) {
					Motifs.dust(level, c.move().aimFeet(), 50, c.size());
					BattleCameraDirector.shake(0.15f, 0.8f);
				}
			}
			case "bodypress", "headlongrush", "stoneedge" -> {
				if (phase(c).equals("hit") && c.t() < 1f) {
					Motifs.dust(level, c.move().aimFeet(), 50, c.size());
					if (!motif.equals("stoneedge")) {
						BattleCameraDirector.shake(0.25f, 1f);
					}
				}
			}
			case "dracometeor" -> meteorTick(c, level);
			case "defog" -> defogTick(c, level);
			case "hurricane" -> hurricaneTick(c, level);
			case "scald" -> scaldTick(c, level);
			case "willowisp" -> wispTick(c, level);
			default -> {
			}
		}
		if (c.t() < 1f) {
			switch (phase(c)) {
				case "self" -> Motifs.burstParticles(c, level, c.userCentre());
				case "hit" -> Motifs.burstParticles(c, level, c.aim());
				default -> {
				}
			}
		}
		return true;
	}

	// =====================================================================
	// Ground
	// =====================================================================

	/** Where the earthquake's wave front is: it leaves the user at a steady pace, whatever the target's distance. */
	private static float quakeFront(Context c) {
		return c.k() * 11f;
	}

	/**
	 * Earthquake: the user stamps and the ground itself heaves, blocks of it lifting in rings that run out across the
	 * field (a second, weaker wave after the first), jolting the target as the front passes under it.
	 */
	private static void earthquake(Context c) {
		Vec3 centre = c.move().user().position();
		double dist = flatDist(centre, c.move().aimFeet());
		float reach = (float) Math.max(6, dist + 3);
		float s = c.size();
		BlockState state = ground(centre);
		for (int wave = 0; wave < 2; wave++) {
			float front = quakeFront(c) - wave * 2.6f;
			if (front <= 0 || front > reach + 1.4f) {
				continue;
			}
			float amp = (wave == 0 ? 0.55f : 0.3f) * s;
			heave(c, centre, front, reach, amp, state);
			float fade = 1 - front / (reach + 1.4f);
			Draw.ring(c.glow(), c.m(), centre.add(0, 0.08, 0), UP, front, 0.6f * s, c.color(), op(170 * fade * (wave == 0 ? 1 : 0.6f)));
		}
		float stomp = seg(c.k(), 0, 0.2f);
		if (stomp < 1) {
			Draw.ring(c.glow(), c.m(), centre.add(0, 0.08, 0), UP, (0.4f + 2.4f * stomp) * s, 0.4f * s, c.accent(), op(230 * (1 - stomp)));
		}
		float jolt = (quakeFront(c) - (float) dist) / 2f;
		if (jolt > 0 && jolt < 1) {
			Motifs.impact(c, c.aim(), jolt, 1.1f);
		}
	}

	private static void earthquakeTick(Context c, ClientLevel level) {
		if (c.t() < 1f) {
			BattleCameraDirector.shake(0.3f, 1.8f);
		}
		Vec3 centre = c.move().user().position();
		float front = quakeFront(c);
		float reach = (float) Math.max(6, flatDist(centre, c.move().aimFeet()) + 3);
		if (front < 0.5f || front > reach) {
			return;
		}
		BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, ground(centre));
		RandomSource random = c.random();
		for (int i = 0; i < 14; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			level.addParticle(dust, centre.x + Math.cos(angle) * front, centre.y + 0.1, centre.z + Math.sin(angle) * front, 0,
					0.2 + random.nextDouble() * 0.3, 0);
		}
	}

	/** Ground blocks lifting where a wave front ({@code front} blocks out from {@code centre}) passes, out to {@code reach}. */
	private static void heave(Context c, Vec3 centre, float front, float reach, float amp, BlockState state) {
		for (float r = 0.9f; r <= reach; r += 0.68f) {
			float x = (front - r) / 1.4f;
			if (x <= 0 || x >= 1) {
				continue;
			}
			float lift = Mth.sin(x * Mth.PI) * amp * (1 - 0.5f * r / reach);
			int n = Math.max(7, Math.round(Mth.TWO_PI * r / 0.7f));
			for (int j = 0; j < n; j++) {
				double angle = Mth.TWO_PI * j / n + r * 1.7;
				// Sunk 0.36 so a block barely lifted stays under the ground's surface (no flicker against it).
				Vec3 at = centre.add(Math.cos(angle) * r, lift - 0.36, Math.sin(angle) * r);
				Motifs.block(c, state, at, 0.68f, (float) Math.toDegrees(angle), lift * 40 * ((j & 1) == 0 ? 1 : -1), lift * 25);
			}
		}
	}

	/** Earth Power: the ground glows and trembles under the target, then bursts in a column of light and earth. */
	private static void earthPower(Context c) {
		Vec3 feet = c.move().aimFeet();
		float s = c.size();
		float k = c.k();
		BlockState state = ground(feet);
		float build = seg(k, 0, 0.3f);
		float erupt = seg(k, 0.3f, 0.5f);
		float fade = 1 - seg(k, 0.65f, 1f);
		RandomSource jag = RandomSource.create(c.move().move.hashCode() * 31L);
		for (int i = 0; i < 8; i++) {
			double angle = Mth.TWO_PI * i / 8 + jag.nextFloat() * 0.5;
			Vec3 last = feet.add(0, 0.07, 0);
			for (int j = 1; j <= 4; j++) {
				double r = j * 0.45 * s * Math.min(1, build * 1.5);
				angle += (jag.nextFloat() - 0.5) * 0.7;
				Vec3 next = feet.add(Math.cos(angle) * r, 0.07, Math.sin(angle) * r);
				Draw.streak(c.glow(), c.m(), next, last, c.camera(), 0.1f * s, c.accent(), op(240 * fade));
				last = next;
			}
		}
		if (erupt <= 0) {
			Draw.halo(c.glow(), c.m(), feet.add(0, 0.2, 0), c.camera(), 0, (0.6f + build) * s, c.accent(), op(120 * build));
			for (int i = 0; i < 8; i++) {
				double angle = Mth.TWO_PI * i / 8 + 0.4;
				float lift = 0.06f + 0.06f * Mth.sin(c.t() * 2.5f + i);
				Vec3 at = feet.add(Math.cos(angle) * 0.9 * s, lift - 0.32, Math.sin(angle) * 0.9 * s);
				Motifs.block(c, state, at, 0.6f, i * 40, lift * 120, 0);
			}
			return;
		}
		float column = Math.min(1, erupt * 1.5f);
		Vec3 top = feet.add(0, 4.5 * s * column, 0);
		Draw.streak(c.glow(), c.m(), feet, top, c.camera(), 0.9f * s, c.color(), op(200 * fade));
		Draw.streak(c.glow(), c.m(), feet, top, c.camera(), 0.4f * s, c.accent(), op(230 * fade));
		Draw.streak(c.glow(), c.m(), feet, top, c.camera(), 0.14f * s, 0xFFFFFF, op(240 * fade));
		Draw.halo(c.glow(), c.m(), feet.add(0, 0.3, 0), c.camera(), 0, 1.6f * s, c.accent(), op(220 * fade));
		float ring = seg(k, 0.3f, 0.8f);
		Draw.ring(c.glow(), c.m(), feet.add(0, 0.07, 0), UP, (0.5f + 3f * ring) * s, 0.4f * s, c.color(), op(220 * (1 - ring)));
		for (int i = 0; i < 10; i++) {
			float p = seg(k, 0.3f + i * 0.012f, 1f);
			if (p <= 0) {
				continue;
			}
			double angle = Mth.TWO_PI * i / 10 + 0.3;
			double out = (0.4 + 2.2 * p) * s * (0.7 + 0.15 * (i % 3));
			double up = (6.5 * p - 6.0 * p * p) * s;
			Vec3 at = feet.add(Math.cos(angle) * out, up + 0.2, Math.sin(angle) * out);
			Motifs.block(c, state, at, 0.45f * s * (1 - seg(p, 0.8f, 1f)), c.t() * 20 + i * 40, c.t() * 25, i * 30);
		}
	}

	/** Stone Edge: two rings of sharp stones stabbing up out of the ground round the target, one after the other. */
	private static void stoneEdge(Context c) {
		Vec3 feet = c.move().aimFeet();
		float s = c.size();
		BlockState rock = Motifs.block(c, Blocks.DEEPSLATE.defaultBlockState());
		float w = c.move().targetWidth() * 0.5f + 0.45f * s;
		float sink = seg(c.k(), 0.8f, 1f);
		for (int ring = 0; ring < 2; ring++) {
			for (int i = 0; i < 8; i++) {
				float start = i * 0.035f + ring * 0.12f;
				float p = seg(c.k(), start, start + 0.14f);
				if (p <= 0) {
					continue;
				}
				double angle = Mth.TWO_PI * i / 8 + 0.3 + ring * 0.4;
				Vec3 radial = dirOf(angle);
				float len = (1.5f + 0.35f * ((i * 7) % 4) - ring * 0.5f) * s;
				float rise = ease(p) - sink;
				double lean = Math.toRadians(22 + ring * 15);
				Vec3 axis = radial.scale(Math.sin(lean)).add(0, Math.cos(lean), 0);
				Vec3 base = feet.add(radial.scale(w + ring * 0.7 * s));
				// Half its length out of the ground once risen: the bottom point at the ground, the top one high.
				crystalToward(c, rock, base.add(axis.scale(len * (rise - 0.5))), 0.5f * s, len, axis);
				float glint = 1 - seg(c.k(), start + 0.14f, start + 0.35f);
				if (p >= 1 && glint > 0) {
					Vec3 tip = base.add(axis.scale(len * rise));
					Draw.streak(c.glow(), c.m(), tip, base, c.camera(), 0.07f * s, c.accent(), op(220 * glint));
					twinkle(c, tip, 0.45f * s, c.accent(), op(240 * glint));
				}
			}
		}
		float hit = seg(c.k(), 0.05f, 0.45f);
		if (hit > 0 && hit < 1) {
			Motifs.impact(c, c.aim(), hit, 1f);
			Draw.ring(c.glow(), c.m(), feet.add(0, 0.07, 0), UP, w + 2 * hit * s, 0.3f * s, c.color(), op(200 * (1 - hit)));
		}
	}

	/** Stealth Rock: pointed stones tossed over to the foe's side, floating and turning round it, then lying in wait. */
	private static void stealthRock(Context c) {
		int n = c.count(6);
		Vec3 feet = c.move().aimFeet();
		float s = c.size();
		float w = c.move().targetWidth() * 0.6f + 0.9f * s;
		float h = c.move().targetHeight();
		BlockState rock = Motifs.block(c, Blocks.DRIPSTONE_BLOCK.defaultBlockState());
		float vanish = 1 - seg(c.k(), 0.88f, 1f);
		for (int i = 0; i < n; i++) {
			float appear = seg(c.k(), i * 0.05f, i * 0.05f + 0.3f);
			if (appear <= 0) {
				continue;
			}
			double angle = Mth.TWO_PI * i / n + c.t() * 0.05;
			Vec3 home = feet.add(Math.cos(angle) * w, h * (0.35 + 0.3 * Math.sin(i * 1.9)) + 0.2 * Math.sin(c.t() * 0.15 + i), Math.sin(angle) * w);
			float e = ease(appear);
			Vec3 at = c.mouth().lerp(home, e).add(0, Math.sin(e * Math.PI) * 1.6 * s, 0);
			float len = (0.75f + 0.2f * (i % 3)) * s;
			crystal(c, rock, at, 0.42f * s * vanish, len * vanish, c.t() * 4 + i * 60, 18 * Mth.sin(i * 2.3f));
			Draw.halo(c.glow(), c.m(), at, c.camera(), 0, 0.55f * s, c.color(), op(70 * vanish));
			float glint = ((c.t() + i * 7) % 24) / 4f;
			if (glint < 1 && appear >= 1) {
				twinkle(c, at.add(0, len * 0.4, 0), 0.45f * s, c.accent(), op(240 * bell(glint) * vanish));
			}
		}
	}

	/** Spikes: a handful of metal spikes thrown in an arc, landing point up before the target, each with a glint. */
	private static void spikes(Context c) {
		float s = c.size();
		Vec3 feet = c.move().aimFeet();
		Vec3 from = c.mouth();
		BlockState metal = Motifs.block(c, Blocks.IRON_BLOCK.defaultBlockState());
		RandomSource jag = RandomSource.create(c.move().move.hashCode() * 17L);
		Vec3 dir = flat(c.toTarget());
		Vec3 sd = side(dir);
		float sink = seg(c.k(), 0.9f, 1f);
		for (int i = 0; i < 9; i++) {
			Vec3 land = feet.add(sd.scale(jag.nextGaussian() * 1.1 * s)).add(dir.scale(-0.8 * s + jag.nextGaussian() * 0.7 * s));
			float start = i * 0.03f;
			float p = seg(c.k(), start, start + 0.35f);
			if (p <= 0) {
				continue;
			}
			if (p < 1) {
				Vec3 at = from.lerp(land, p).add(0, Math.sin(p * Math.PI) * 2.2 * s, 0);
				crystal(c, metal, at, 0.28f * s, 0.8f * s, c.t() * 30 + i * 40, c.t() * 40);
				Draw.halo(c.glow(), c.m(), at, c.camera(), 0, 0.5f * s, c.color(), 140);
				continue;
			}
			Vec3 at = land.add(0, (0.2 - sink * 0.5) * s, 0);
			crystal(c, metal, at, 0.28f * s, 0.8f * s, i * 40, 8 * ((i % 3) - 1));
			Draw.halo(c.glow(), c.m(), at, c.camera(), 0, 0.4f * s, c.color(), op(90 * (1 - sink)));
			float since = seg(c.k(), start + 0.35f, start + 0.5f);
			if (since < 1) {
				twinkle(c, at.add(0, 0.4 * s, 0), 0.55f * s, 0xFFFFFF, op(240 * bell(since)));
				Draw.ring(c.glow(), c.m(), land.add(0, 0.06, 0), UP, (0.2f + 0.8f * since) * s, 0.1f * s, c.color(), op(200 * (1 - since)));
			}
		}
	}

	/** Body Press: the landing spot marked under the target, then the crash: the ground heaves, a dome of dust. */
	private static void bodyPress(Context c) {
		float s = c.size();
		Vec3 feet = c.move().aimFeet();
		if (phase(c).equals("wind")) {
			float fade = c.fade(0.15f, 0.1f);
			for (int i = 0; i < 3; i++) {
				float p = ((c.t() * 0.07f) + i / 3f) % 1f;
				Draw.ring(c.glow(), c.m(), feet.add(0, 0.07, 0), UP, (2.6f - 2.0f * p) * s, 0.15f * s, c.color(), op(220 * fade * p));
			}
			for (int i = 0; i < 4; i++) {
				float drop = (c.t() * 0.08f + i * 0.25f) % 1f;
				Vec3 top = feet.add(dirOf(i * Math.PI / 2 + 0.6).scale(1.0 * s)).add(0, (2.4 - drop * 1.6) * s, 0);
				Draw.streak(c.glow(), c.m(), top.subtract(0, 0.6 * s, 0), top, c.camera(), 0.12f * s, c.accent(), op(200 * fade * bell(drop)));
			}
			return;
		}
		float k = c.k();
		float hit = seg(k, 0, 0.5f);
		if (hit < 1) {
			Motifs.impact(c, c.aim(), hit, 1.6f);
		}
		heave(c, feet, seg(k, 0, 0.7f) * 4.5f * s, 4f * s, 0.5f * s, ground(feet));
		float out = ease(seg(k, 0, 0.6f));
		float fade = 1 - seg(k, 0, 0.8f);
		Draw.ring(c.glow(), c.m(), feet.add(0, 0.07, 0), UP, (0.6f + 4f * out) * s, 0.5f * s, c.color(), op(220 * fade));
		Draw.sphere(c.glow(), c.m(), feet, (0.5f + 1.5f * out) * s, c.accent(), op(40 * (1 - seg(k, 0.1f, 0.8f))), true);
	}

	/** Headlong Rush: the ground torn up in the user's wake as it charges, then blasted out round the target. */
	private static void headlongRush(Context c) {
		float s = c.size();
		Vec3 from = c.move().user().position();
		Vec3 to = c.move().aimFeet();
		BlockState state = ground(from);
		if (phase(c).equals("wind")) {
			Vec3 dir = flat(to.subtract(from));
			Vec3 sd = side(dir);
			double dist = flatDist(from, to);
			for (int i = 0; i < 10; i++) {
				float f = i / 10f;
				float q = (c.k() - f * 0.35f) / 0.45f;
				if (q <= 0 || q >= 1) {
					continue;
				}
				int sign = i % 2 == 0 ? 1 : -1;
				Vec3 spot = from.add(dir.scale(f * dist)).add(sd.scale(sign * 0.5 * s));
				Vec3 at = spot.add(sd.scale(sign * q * 1.2 * s)).add(0, (2.2 * q - 2.4 * q * q) * s - 0.1, 0);
				Motifs.block(c, state, at, 0.5f * s * (1 - seg(q, 0.7f, 1f)), q * 300 + i * 40, q * 240, i * 20);
			}
			return;
		}
		float k = c.k();
		float hit = seg(k, 0, 0.5f);
		if (hit < 1) {
			Motifs.impact(c, c.aim(), hit, 1.5f);
		}
		float fly = seg(k, 0, 0.9f);
		for (int i = 0; i < 12; i++) {
			double angle = Mth.TWO_PI * i / 12 + 0.2;
			double out = (0.5 + 2.6 * ease(fly)) * s * (0.8 + 0.1 * (i % 3));
			Vec3 at = to.add(Math.cos(angle) * out, (3.4 * fly - 3.6 * fly * fly) * s + 0.2, Math.sin(angle) * out);
			Motifs.block(c, state, at, 0.42f * s * (1 - seg(fly, 0.75f, 1f)), fly * 400 + i * 30, fly * 300, i * 15);
		}
		float ring = seg(k, 0, 0.8f);
		Draw.ring(c.glow(), c.m(), to.add(0, 0.07, 0), UP, (0.5f + 3f * ease(seg(k, 0, 0.6f))) * s, 0.45f * s, c.color(), op(220 * (1 - ring)));
	}

	// =====================================================================
	// Blows
	// =====================================================================

	private static final Item[] KNOCKED = { Items.APPLE, Items.GOLDEN_APPLE, Items.ENDER_PEARL, Items.EMERALD, Items.AMETHYST_SHARD,
			Items.SWEET_BERRIES };

	/** Knock Off: a wide backhand swipe, and the target's held item sent spinning away with a glint. */
	private static void knockOff(Context c) {
		Vec3 at = c.aim();
		float s = c.size() * Math.max(1f, c.move().targetHeight() * 0.8f);
		Vec3 dir = flat(c.toTarget());
		Vec3 sd = side(dir);
		float swing = ease(seg(c.k(), 0, 0.28f));
		float fade = 1 - seg(c.k(), 0.3f, 0.75f);
		Vec3 v = dir.scale(-1).add(0, 0.45, 0).normalize();
		Draw.arc(c.glow(), c.m(), at, sd, v, 1.4f * s, -0.4f, 3.5f, swing, 0.8f * s, c.color(), op(240 * fade));
		Draw.arc(c.glow(), c.m(), at, sd, v, 1.36f * s, -0.4f, 3.5f, swing, 0.32f * s, c.accent(), op(255 * fade));
		Draw.arc(c.glow(), c.m(), at, sd, v, 1.33f * s, -0.4f, 3.5f, swing, 0.1f * s, 0xFFFFFF, op(255 * fade));
		float smack = seg(c.k(), 0.14f, 0.6f);
		if (smack > 0 && smack < 1) {
			Motifs.impact(c, at, smack, 1.1f);
		}
		float p = seg(c.k(), 0.16f, 1f);
		if (p <= 0) {
			return;
		}
		Vec3 launch = dir.scale(2.4 * s).add(sd.scale(1.3 * s));
		double floor = c.move().aimFeet().y + 0.25;
		Vec3 pos = thrown(at, launch, p, s, floor);
		int pick = c.move().target == null ? 0 : c.move().target.getId();
		Item kind = KNOCKED[Math.floorMod(pick, KNOCKED.length)];
		Motifs.item(c, new ItemStack(kind), pos, 1.2f * s * (1 - seg(c.k(), 0.9f, 1f)), c.t() * 35, c.t() * 20, 0);
		Draw.streak(c.glow(), c.m(), pos, thrown(at, launch, Math.max(0, p - 0.1f), s, floor), c.camera(), 0.12f * s, c.accent(), op(180 * (1 - p)));
		if (p < 0.5f) {
			twinkle(c, pos, 0.5f * s * (1 - p), 0xFFFFFF, op(230 * (1 - p * 2)));
		}
	}

	private static Vec3 thrown(Vec3 from, Vec3 launch, float p, float s, double floor) {
		Vec3 at = from.add(launch.scale(p)).add(0, (3.2 * p - 3.8 * p * p) * s, 0);
		return at.y < floor ? new Vec3(at.x, floor, at.z) : at;
	}

	/** Sucker Punch: a shadow slips along the ground behind the target, and the blow comes up from there. */
	private static void suckerPunch(Context c) {
		float s = c.size();
		Vec3 dir = flat(c.toTarget());
		Vec3 behind = c.move().aimFeet().add(dir.scale(c.move().targetWidth() * 0.5 + 0.7)).add(0, 0.1, 0);
		if (phase(c).equals("travel")) {
			Vec3 from = c.move().user().position().add(0, 0.1, 0);
			float p = ease(c.k());
			Vec3[] pts = new Vec3[10];
			for (int i = 0; i < pts.length; i++) {
				pts[i] = from.lerp(behind, Math.max(0, p - i * 0.06f));
			}
			ribbon(c, pts, 0.55f * s, 0.05f, c.color(), 240, 0);
			ribbon(c, pts, 0.14f * s, 0.01f, c.accent(), 255, 0);
			Draw.halo(c.glow(), c.m(), pts[0], c.camera(), 0, 0.8f * s, c.accent(), 220);
			return;
		}
		Vec3 at = c.aim();
		float k = c.k();
		float thrust = ease(seg(k, 0, 0.2f));
		Vec3 start = behind.add(0, 0.2, 0);
		Vec3 fist = start.lerp(at, thrust);
		float fade = 1 - seg(k, 0.3f, 0.7f);
		Draw.streak(c.glow(), c.m(), fist, start, c.camera(), 0.55f * s, c.color(), op(230 * fade));
		Draw.streak(c.glow(), c.m(), fist, start, c.camera(), 0.18f * s, c.accent(), op(250 * fade));
		Draw.halo(c.glow(), c.m(), fist, c.camera(), 0, 0.9f * s, c.color(), op(230 * fade));
		Draw.halo(c.glow(), c.m(), fist, c.camera(), 0, 0.4f * s, c.accent(), op(250 * fade));
		if (thrust < 1) {
			return;
		}
		float q = seg(k, 0.2f, 0.75f);
		Motifs.impact(c, at, q, 1.4f);
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, (1 + q) * 1.3f * s, c.accent(), op(200 * (1 - q)));
		cracks(c, at, q, s * 1.3f, c.accent());
	}

	/** Jagged cracks of light breaking out from a blow, facing the camera. */
	private static void cracks(Context c, Vec3 at, float q, float s, int color) {
		RandomSource jag = RandomSource.create(71);
		Vec3[] b = c.screen(at);
		for (int i = 0; i < 6; i++) {
			double angle = Mth.TWO_PI * i / 6 + 0.3;
			Vec3 d = b[0].scale(Math.cos(angle)).add(b[1].scale(Math.sin(angle)));
			Vec3 last = at;
			for (int j = 1; j <= 3; j++) {
				Vec3 next = at.add(d.scale(j * 0.45 * s * (0.3 + q))).add(b[0].scale(jag.nextGaussian() * 0.12)).add(b[1].scale(jag.nextGaussian() * 0.12));
				Draw.streak(c.glow(), c.m(), next, last, c.camera(), 0.07f * s, color, op(240 * (1 - q)));
				last = next;
			}
		}
	}

	/** Pursuit: three dark comets hunting the target down on curving paths, then a cross of dark slashes. */
	private static void pursuit(Context c) {
		float s = c.size();
		Vec3 from = c.userCentre();
		Vec3 to = c.aim();
		Vec3 sd = side(flat(to.subtract(from)));
		if (phase(c).equals("travel")) {
			for (int j = 0; j < 3; j++) {
				float off = (j - 1) * 1.4f * s;
				Vec3[] pts = new Vec3[12];
				for (int i = 0; i < pts.length; i++) {
					float p = Math.max(0, c.k() - i * 0.045f);
					pts[i] = from.lerp(to, p).add(sd.scale(off * Math.sin(p * Math.PI))).add(0, (j == 1 ? 0.6 : 0.15) * Math.sin(p * Math.PI) * s, 0);
				}
				ribbon(c, pts, 0.3f * s, 0.02f, c.color(), 230, 0);
				ribbon(c, pts, 0.08f * s, 0.01f, c.accent(), 255, 0);
				Draw.halo(c.glow(), c.m(), pts[0], c.camera(), 0, 0.4f * s, c.accent(), 220);
			}
			return;
		}
		float k = c.k();
		Vec3[] b = c.screen(to);
		float size = s * Math.max(1.1f, c.move().targetHeight() * 0.9f);
		float fade = 1 - seg(k, 0.5f, 0.9f);
		for (int i = 0; i < 2; i++) {
			float p = ease(seg(k, i * 0.12f, i * 0.12f + 0.22f));
			if (p <= 0) {
				continue;
			}
			Vec3 u = b[0].scale(i == 0 ? 1 : -1).add(b[1].scale(1.2)).normalize();
			Vec3 start = to.add(u.scale(size));
			Vec3 head = start.lerp(to.subtract(u.scale(size)), p);
			Vec3[] pts = { head, start.lerp(head, 0.5), start };
			ribbon(c, pts, 0.35f * size, 0.05f, c.color(), 240 * fade, 120 * fade);
			ribbon(c, pts, 0.09f * size, 0.02f, c.accent(), 255 * fade, 150 * fade);
		}
		if (k > 0.12f) {
			Motifs.impact(c, to, seg(k, 0.12f, 0.7f), 1f);
		}
	}

	/** Crunch: huge spectral jaws gaping over the target, slamming shut, the bite cracking it. */
	private static void crunch(Context c) {
		Vec3 at = c.aim();
		Vec3[] b = c.screen(at);
		float s = c.size() * Math.max(1.2f, c.move().targetHeight() * 0.95f);
		float k = c.k();
		float open = ease(seg(k, 0, 0.3f));
		float shut = seg(k, 0.3f, 0.4f);
		shut = shut * shut;
		float gap = (0.25f + 0.9f * open) * (1 - shut);
		float fade = 1 - seg(k, 0.55f, 0.85f);
		for (int jaw = -1; jaw <= 1; jaw += 2) {
			Vec3 gum = at.add(b[1].scale(jaw * s * gap));
			Vec3 centre = gum.add(b[1].scale(jaw * s * 0.55));
			Vec3 inward = b[1].scale(-jaw);
			Draw.arc(c.glow(), c.m(), centre, b[0], inward, s * 0.95f, 0.35f, 2.79f, 1f, 0.3f * s, c.color(), op(220 * fade));
			Draw.arc(c.glow(), c.m(), centre, b[0], inward, s * 0.92f, 0.35f, 2.79f, 1f, 0.1f * s, c.accent(), op(240 * fade));
			for (int f = 0; f < 7; f++) {
				double theta = 0.65 + f * (2.49 - 0.65) / 6;
				Vec3 root = centre.add(b[0].scale(Math.cos(theta) * 0.95 * s)).add(inward.scale(Math.sin(theta) * 0.95 * s));
				double len = (f == 1 || f == 5 ? 0.55 : 0.32) * s;
				Vec3 tip = root.add(inward.scale(len));
				Vec3 l = root.subtract(b[0].scale(0.09 * s));
				Vec3 r = root.add(b[0].scale(0.09 * s));
				line(c, l, tip, 0.05f * s, 0xFFFFFF, 255 * fade);
				line(c, r, tip, 0.05f * s, 0xFFFFFF, 255 * fade);
				Draw.halo(c.glow(), c.m(), tip, c.camera(), 0, 0.12f * s, 0xFFFFFF, op(200 * fade));
			}
		}
		if (shut >= 1) {
			float q = seg(k, 0.4f, 0.9f);
			Motifs.impact(c, at, q, 1f);
			cracks(c, at, q, s * 0.8f, 0xFFFFFF);
		}
	}

	/** Iron Head: the head turns to gleaming steel, then the blow throws sparks of metal back off the target. */
	private static void ironHead(Context c) {
		float s = c.size();
		if (phase(c).equals("wind")) {
			Entity user = c.move().user();
			Vec3 head = user.position().add(0, user.getBbHeight() * 0.8, 0).add(flat(c.toTarget()).scale(user.getBbWidth() * 0.35));
			float r = Math.max(0.45f, user.getBbWidth() * 0.45f) * s;
			float fade = c.fade(0.2f, 0.1f);
			Draw.sphere(c.glow(), c.m(), head, r, c.color(), op(90 * fade), true);
			Draw.ring(c.glow(), c.m(), head, c.camera().subtract(head).normalize(), r, 0.06f * s, 0xFFFFFF, op(230 * fade));
			Vec3[] b = c.screen(head);
			float sweep = (c.t() % 10) / 10f;
			Vec3 diagonal = b[0].subtract(b[1]).normalize();
			Vec3 g = head.add(b[0].scale((sweep * 2 - 1) * r)).add(b[1].scale((sweep * 2 - 1) * r * 0.5));
			line(c, g.subtract(diagonal.scale(r * 0.6)), g.add(diagonal.scale(r * 0.6)), 0.07f * s, 0xFFFFFF, 220 * fade * bell(sweep));
			twinkle(c, head.add(b[0].scale(r * 0.6)).add(b[1].scale(r * 0.6)), 0.5f * s, 0xFFFFFF, op(240 * bell(seg(c.k(), 0.6f, 1f))));
			return;
		}
		Vec3 at = c.aim();
		float k = c.k();
		float hit = seg(k, 0, 0.5f);
		if (hit < 1) {
			Motifs.impact(c, at, hit, 1.3f);
			Draw.ring(c.glow(), c.m(), at, flat(c.toTarget()), (0.5f + 2.2f * hit) * s, 0.2f * s, c.color(), op(220 * (1 - hit)));
		}
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, 1.4f * s, 0xFFFFFF, op(200 * (1 - seg(k, 0, 0.3f))));
		RandomSource jag = RandomSource.create(83);
		Vec3 back = flat(c.toTarget()).scale(-1);
		for (int i = 0; i < 20; i++) {
			Vec3 d = back.scale(0.6).add(jag.nextGaussian() * 0.8, jag.nextFloat(), jag.nextGaussian() * 0.8).normalize();
			float speed = 2.5f + jag.nextFloat() * 2;
			float q = seg(k, 0, 0.6f + jag.nextFloat() * 0.3f);
			if (q >= 1) {
				continue;
			}
			float q0 = Math.max(0, q - 0.12f);
			Vec3 p = at.add(d.scale(q * speed * s)).add(0, -q * q * 1.5 * s, 0);
			Vec3 tail = at.add(d.scale(q0 * speed * s)).add(0, -q0 * q0 * 1.5 * s, 0);
			Draw.streak(c.glow(), c.m(), p, tail, c.camera(), 0.05f * s, i % 3 == 0 ? 0xFFE080 : 0xFFFFFF, op(255 * (1 - q)));
		}
	}

	/** Power Whip: a thick vine of leaves raised high, lashed down onto the target with a crack, then drawn back. */
	private static void powerWhip(Context c) {
		float s = c.size();
		Vec3 from = c.mouth();
		Vec3 to = c.aim();
		Vec3 dir = flat(to.subtract(from));
		Vec3 sd = side(dir);
		double dist = from.distanceTo(to);
		Vec3 raised = from.add(0, 2.6 * s + 0.3, 0).subtract(dir.scale(1.0 * s));
		Vec3 tip;
		Vec3 ctrl;
		float fade = 1f;
		if (phase(c).equals("wind")) {
			float raise = ease(c.k());
			tip = from.lerp(raised, raise).add(sd.scale(Math.sin(c.t() * 0.3) * 0.3));
			ctrl = from.add(dir.scale(0.6 * s)).add(0, 1.0 * s * raise, 0);
		} else {
			float k = c.k();
			float lash = ease(seg(k, 0, 0.22f));
			float back = ease(seg(k, 0.55f, 1f));
			tip = raised.lerp(to, lash).add(0, Math.sin(lash * Math.PI) * 1.8 * s, 0);
			ctrl = from.add(dir.scale(dist * 0.45 * lash)).add(0, (1.0 + 1.2 * (1 - lash)) * s, 0);
			if (back > 0) {
				tip = tip.lerp(from.add(dir.scale(0.5)), back);
				ctrl = ctrl.lerp(from.add(0, 0.5, 0), back);
			}
			fade = 1 - back;
			float crack = seg(k, 0.2f, 0.65f);
			if (crack > 0 && crack < 1) {
				Motifs.impact(c, to, crack, 1.3f);
				Draw.ring(c.glow(), c.m(), c.move().aimFeet().add(0, 0.07, 0), UP, (0.4f + 2.4f * crack) * s, 0.3f * s, c.color(), op(220 * (1 - crack)));
				for (int i = 0; i < 8; i++) {
					double angle = i * 0.785 + 0.3;
					Vec3 leaf = to.add(Math.cos(angle) * crack * 2 * s, (1.6 * crack - 2.2 * crack * crack) * s + Math.sin(i) * 0.3, Math.sin(angle) * crack * 2 * s);
					Motifs.block(c, Blocks.AZALEA_LEAVES.defaultBlockState(), leaf, 0.2f * s * (1 - crack), crack * 500 + i * 40, crack * 300, 0);
				}
			}
		}
		// The vine: a chain of moss, thick at the user, thin at the tip, a light running along it.
		int n = 26;
		Vec3[] pts = new Vec3[n];
		for (int i = 0; i < n; i++) {
			double q = 1 - i / (n - 1.0);
			pts[i] = bezier(from, ctrl, tip, q).add(sd.scale(Math.sin(q * 7 - c.t() * 0.6) * 0.08 * s * q));
		}
		for (int i = 0; i < n; i++) {
			float thick = (0.16f + 0.26f * i / (n - 1f)) * s * fade;
			Motifs.block(c, i % 5 == 2 ? Blocks.AZALEA_LEAVES.defaultBlockState() : Blocks.MOSS_BLOCK.defaultBlockState(), pts[i],
					i % 5 == 2 ? thick * 1.6f : thick, i * 37, i * 23, 0);
		}
		ribbon(c, pts, 0.1f * s, 0.25f * s, c.color(), 220 * fade, 170 * fade);
		ribbon(c, pts, 0.02f * s, 0.04f * s, c.accent(), 220 * fade, 160 * fade);
	}

	// =====================================================================
	// Dashes
	// =====================================================================

	/**
	 * U-turn and Flip Turn: the user strikes on its way and turns back, its trail drawing a U round the target (Flip
	 * Turn somersaults over the turn).
	 */
	private static void uTurn(Context c, boolean flip) {
		float s = c.size();
		Vec3 from = c.userCentre();
		Vec3 to = c.aim();
		float r = 0.9f * s;
		Vec3 turned = to.add(side(flat(to.subtract(from))).scale(2 * r));
		double out = from.distanceTo(to);
		double total = out + Math.PI * r + turned.distanceTo(from);
		float uHit = (float) (out / total);
		boolean travel = phase(c).equals("travel");
		float u = travel ? c.k() * uHit : uHit + (1 - uHit) * seg(c.k(), 0, 0.8f);
		float fade = travel ? 1 : 1 - seg(c.k(), 0.8f, 1f);
		int n = 18;
		Vec3[] pts = new Vec3[n];
		for (int i = 0; i < n; i++) {
			pts[i] = uPath(c, Math.max(0, u - 0.3f * i / (n - 1f)), flip);
		}
		ribbon(c, pts, 0.32f * s, 0.02f, c.color(), 220 * fade, 0);
		ribbon(c, pts, 0.1f * s, 0.01f, c.accent(), 255 * fade, 0);
		Draw.halo(c.glow(), c.m(), pts[0], c.camera(), 0, 0.55f * s, c.color(), op(200 * fade));
		Draw.halo(c.glow(), c.m(), pts[0], c.camera(), 0, 0.22f * s, 0xFFFFFF, op(240 * fade));
		if (flip) {
			for (int i = 2; i < n; i += 3) {
				Vec3 drop = pts[i].add(0, -0.15 * i / n - Math.sin(c.t() + i) * 0.1, 0);
				Draw.sphere(c.glow(), c.m(), drop, 0.07f * s, c.accent(), op(200 * fade * (1 - i / (float) n)), true);
			}
		}
		if (!travel) {
			float hit = seg(c.k(), 0, 0.45f);
			if (hit < 1) {
				Motifs.impact(c, to, hit, 1f);
			}
		}
	}

	/** Along the U-turn, {@code u} 0 → 1: out to the target, round a half circle beside it, back to the user. */
	private static Vec3 uPath(Context c, float u, boolean flip) {
		float s = c.size();
		Vec3 from = c.userCentre();
		Vec3 to = c.aim();
		Vec3 dir = flat(to.subtract(from));
		Vec3 sd = side(dir);
		float r = 0.9f * s;
		Vec3 centre = to.add(sd.scale(r));
		Vec3 turned = to.add(sd.scale(2 * r));
		double a = from.distanceTo(to);
		double b = Math.PI * r;
		double back = turned.distanceTo(from);
		double d = Mth.clamp(u, 0, 1) * (a + b + back);
		if (d < a) {
			double p = d / a;
			return from.lerp(to, p).add(0, Math.sin(p * Math.PI) * 0.5 * s, 0);
		}
		d -= a;
		if (d < b) {
			double phi = d / r;
			Vec3 q = centre.add(sd.scale(-r * Math.cos(phi))).add(dir.scale(r * Math.sin(phi)));
			return flip ? q.add(0, Math.sin(phi) * 1.4 * s, 0) : q;
		}
		double p = (d - b) / back;
		return turned.lerp(from, p).add(0, Math.sin(p * Math.PI) * 0.4 * s, 0);
	}

	/** Rapid Spin: a whirl round the user as it spins in, then the target struck and debris flung clear. */
	private static void rapidSpin(Context c) {
		float s = c.size();
		Entity user = c.move().user();
		if (!phase(c).equals("hit")) {
			float fade = c.fade(0.1f, 0.15f);
			float r = user.getBbWidth() * 0.75f + 0.3f * s;
			float h = user.getBbHeight();
			Vec3 base = user.position();
			for (int i = 0; i < 5; i++) {
				Vec3 centre = base.add(0, h * (0.15 + 0.18 * i), 0);
				float spin = c.t() * 1.6f + i * 1.3f;
				Draw.arc(c.glow(), c.m(), centre, new Vec3(1, 0, 0), new Vec3(0, 0, 1), r * (1 + 0.08f * i), spin, spin + 3.6f, 1f, 0.14f * s,
						i % 2 == 0 ? c.color() : c.accent(), op(230 * fade));
			}
			Vec3[] pts = new Vec3[24];
			for (int i = 0; i < pts.length; i++) {
				double p = i / 23.0;
				double angle = -c.t() * 1.6 - p * 9;
				pts[i] = base.add(Math.cos(angle) * r * 1.1, p * h * 1.1, Math.sin(angle) * r * 1.1);
			}
			ribbon(c, pts, 0.08f * s, 0.08f * s, 0xFFFFFF, 200 * fade, 60 * fade);
			return;
		}
		Vec3 at = c.aim();
		float hit = seg(c.k(), 0, 0.5f);
		if (hit < 1) {
			Motifs.impact(c, at, hit, 1f);
			Draw.ring(c.glow(), c.m(), at, UP, (0.4f + 1.8f * hit) * s, 0.2f * s, c.accent(), op(220 * (1 - hit)));
		}
		Vec3 base = user.position().add(0, 0.4, 0);
		float fly = seg(c.k(), 0.05f, 0.9f);
		for (int i = 0; i < 6; i++) {
			Vec3 d = dirOf(Mth.TWO_PI * i / 6 + 0.5);
			Vec3 pos = base.add(d.scale((0.8 + 3.2 * fly) * s)).add(0, (2.4 * fly - 2.6 * fly * fly) * s, 0);
			BlockState state = i % 2 == 0 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.IRON_BLOCK.defaultBlockState();
			crystal(c, state, pos, 0.25f * s * (1 - fly), 0.55f * s * (1 - fly), c.t() * 25 + i * 60, c.t() * 30);
		}
	}

	/** Ice Spinner: a saw of ice shards whirling round the user, flung out in all directions on the hit. */
	private static void iceSpinner(Context c) {
		float s = c.size();
		BlockState ice = Blocks.PACKED_ICE.defaultBlockState();
		if (!phase(c).equals("hit")) {
			Entity user = c.move().user();
			float r = user.getBbWidth() * 0.75f + 0.5f * s;
			Vec3 centre = user.position().add(0, user.getBbHeight() * 0.45, 0);
			float fade = c.fade(0.1f, 0.1f);
			for (int i = 0; i < 8; i++) {
				double angle = Mth.TWO_PI * i / 8 + c.t() * 0.9;
				Vec3 radial = dirOf(angle);
				Vec3 axis = radial.add(dirOf(angle + Math.PI / 2).scale(-0.6)).normalize();
				crystalToward(c, ice, centre.add(radial.scale(r)), 0.28f * s * fade, 0.8f * s * fade, axis);
			}
			float spin = c.t() * 0.9f;
			Draw.arc(c.glow(), c.m(), centre, new Vec3(1, 0, 0), new Vec3(0, 0, 1), r * 1.25f, spin, spin + 4f, 1f, 0.18f * s, c.color(), op(220 * fade));
			Draw.arc(c.glow(), c.m(), centre, new Vec3(1, 0, 0), new Vec3(0, 0, 1), r * 0.9f, spin + 3, spin + 7f, 1f, 0.12f * s, c.accent(), op(220 * fade));
			Draw.halo(c.glow(), c.m(), centre, c.camera(), 0, r * 1.4f, c.color(), op(80 * fade));
			return;
		}
		Vec3 at = c.aim();
		Vec3 feet = c.move().aimFeet();
		float k = c.k();
		float hit = seg(k, 0, 0.45f);
		if (hit < 1) {
			Motifs.impact(c, at, hit, 1f);
		}
		float fly = seg(k, 0, 0.85f);
		float shrink = 1 - seg(fly, 0.7f, 1f);
		for (int i = 0; i < 10; i++) {
			Vec3 radial = dirOf(Mth.TWO_PI * i / 10 + 0.2);
			Vec3 pos = at.add(radial.scale((0.3 + 2.4 * ease(fly)) * s)).add(0, (1.4 * fly - 2.2 * fly * fly) * s + Math.sin(i * 1.7) * 0.3, 0);
			crystalToward(c, ice, pos, 0.24f * s * shrink, 0.6f * s * shrink, radial.add(0, 0.3, 0).normalize());
		}
		float frost = ease(seg(k, 0, 0.4f));
		float melt = 1 - seg(k, 0.5f, 1f);
		Draw.ring(c.glow(), c.m(), feet.add(0, 0.06, 0), UP, (0.4f + 2.2f * frost) * s, 0.4f * s, c.color(), op(200 * melt));
		Draw.ring(c.glow(), c.m(), feet.add(0, 0.07, 0), UP, (0.3f + 1.8f * frost) * s, 0.1f * s, 0xFFFFFF, op(230 * melt));
	}

	// =====================================================================
	// Projectiles and beams
	// =====================================================================

	/** Focus Blast: a great orb crackling with a corona, rings spinning round it, and an explosion on the target. */
	private static void focusBlast(Context c) {
		float s = c.size();
		if (!phase(c).equals("travel")) {
			blast(c, c.aim(), c.k(), 1.3f);
			return;
		}
		Vec3 at = Motifs.along(c, c.k(), 0.4f);
		float r = 0.55f * s;
		for (int i = 1; i <= 5; i++) {
			Draw.halo(c.glow(), c.m(), Motifs.along(c, Math.max(0, c.k() - i * 0.06f), 0.4f), c.camera(), 0, r * (1.8f - i * 0.25f), c.color(), 110 - i * 18);
		}
		Draw.halo(c.glow(), c.m(), at, c.camera(), r * 0.8f, r * 3f, c.color(), 190);
		Draw.sphere(c.glow(), c.m(), at, r, c.color(), 150, true);
		Draw.sphere(c.glow(), c.m(), at, r * 0.6f, c.accent(), 170, true);
		Draw.sphere(c.glow(), c.m(), at, r * 0.3f, 0xFFFFFF, 220, true);
		RandomSource jag = RandomSource.create((long) (c.t() / 2) * 17);
		Vec3[] b = c.screen(at);
		for (int i = 0; i < 9; i++) {
			double angle = jag.nextDouble() * Mth.TWO_PI;
			Vec3 d = b[0].scale(Math.cos(angle)).add(b[1].scale(Math.sin(angle)));
			Draw.streak(c.glow(), c.m(), at.add(d.scale(r * (1.6 + jag.nextDouble()))), at.add(d.scale(r * 0.9)), c.camera(), 0.06f * s, c.accent(), 230);
		}
		for (int i = 0; i < 3; i++) {
			double angle = c.t() * 0.5 + i * 2.1;
			Draw.ring(c.glow(), c.m(), at, new Vec3(Math.cos(angle), Math.sin(angle * 1.3), Math.sin(angle)).normalize(), r * 1.4f, 0.07f * s, c.accent(), 220);
		}
	}

	/** An explosion: a swelling fireball, rings in three planes, rays, a white flash. */
	private static void blast(Context c, Vec3 at, float k, float scale) {
		float s = c.size() * scale;
		float fade = 1 - k;
		float grow = ease(Math.min(1, k * 2.5f));
		Draw.sphere(c.glow(), c.m(), at, (0.4f + 1.3f * grow) * s, c.color(), op(140 * fade * fade), true);
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, (1 + 2.2f * grow) * s, c.color(), op(200 * fade));
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, 0.9f * s * fade, 0xFFFFFF, op(240 * fade));
		Vec3[] normals = { UP, new Vec3(1, 0.3, 0.2).normalize(), new Vec3(-0.3, 0.4, 1).normalize() };
		for (int i = 0; i < 3; i++) {
			float q = seg(k, i * 0.06f, i * 0.06f + 0.7f);
			Draw.ring(c.glow(), c.m(), at, normals[i], (0.5f + 3f * q) * s, 0.25f * s, i == 0 ? c.accent() : c.color(), op(220 * (1 - q)));
		}
		RandomSource jag = RandomSource.create(61);
		for (int i = 0; i < 14; i++) {
			Vec3 d = randomUnit(jag);
			Draw.streak(c.glow(), c.m(), at.add(d.scale((0.4 + 2.6 * grow) * s)), at.add(d.scale((0.2 + 1.4 * grow) * s)), c.camera(), 0.1f * s,
					c.accent(), op(230 * fade));
		}
	}

	/** Volt Switch: a spark zigzagging to the target, then the charge leaping back to the user, who flashes away. */
	private static void voltSwitch(Context c) {
		float s = c.size();
		Vec3 from = c.mouth();
		Vec3 to = c.aim();
		Vec3 sd = side(flat(to.subtract(from)));
		if (phase(c).equals("travel")) {
			Vec3 last = null;
			for (int i = 0; i <= 6; i++) {
				Vec3 at = zig(from, to, sd, Math.max(0, c.k() - i * 0.06f), s);
				if (last != null) {
					Motifs.bolt(c.glow(), c.m(), c.camera(), last, at, c.t() + i, 0.05f * s, c.color(), i);
				}
				last = at;
			}
			Vec3 head = zig(from, to, sd, c.k(), s);
			Draw.halo(c.glow(), c.m(), head, c.camera(), 0.2f * s, 0.9f * s, c.color(), 200);
			Draw.sphere(c.glow(), c.m(), head, 0.3f * s, c.accent(), 150, true);
			Draw.sphere(c.glow(), c.m(), head, 0.15f * s, 0xFFFFFF, 220, true);
			return;
		}
		float k = c.k();
		float hit = seg(k, 0, 0.4f);
		if (hit < 1) {
			Motifs.burst(c, to, hit, 1.1f);
			sparks(c, to, c.move().targetWidth() * 0.6f + 0.3f * s, 5, 0.04f * s);
		}
		float back = seg(k, 0.3f, 0.6f);
		Vec3 home = c.userCentre();
		if (back > 0 && k < 0.8f) {
			Vec3 head = to.lerp(home, back);
			Motifs.bolt(c.glow(), c.m(), c.camera(), to, head, c.t(), 0.07f * s, c.color(), 3);
			Draw.halo(c.glow(), c.m(), head, c.camera(), 0, 0.6f * s, c.accent(), 230);
		}
		float flash = seg(k, 0.55f, 0.9f);
		if (flash > 0 && flash < 1) {
			Draw.halo(c.glow(), c.m(), home, c.camera(), 0, (0.8f + 1.5f * flash) * s, 0xFFFFFF, op(220 * (1 - flash)));
			Draw.ring(c.glow(), c.m(), home, UP, (0.5f + 2f * flash) * s, 0.15f * s, c.color(), op(220 * (1 - flash)));
		}
	}

	/** A zigzag from {@code from} to {@code to}: two and a half zigs, widest halfway. */
	private static Vec3 zig(Vec3 from, Vec3 to, Vec3 sd, float p, float s) {
		float tri = Math.abs((p * 5) % 2f - 1f) * 2 - 1;
		return from.lerp(to, p).add(sd.scale(tri * 0.55 * s * Math.sin(p * Math.PI))).add(0, 0.2 * Math.sin(p * Math.PI), 0);
	}

	/** Arcs crackling between random points round {@code at}, re-drawn every two ticks. */
	private static void sparks(Context c, Vec3 at, float r, int count, float width) {
		RandomSource jag = RandomSource.create((long) (c.t() / 2) * 13);
		for (int i = 0; i < count; i++) {
			Vec3 p1 = at.add(randomUnit(jag).scale(r));
			Vec3 p2 = at.add(randomUnit(jag).scale(r * 1.3));
			Motifs.bolt(c.glow(), c.m(), c.camera(), p1, p2, c.t() + i, width, c.color(), i);
		}
	}

	/** Thunderbolt: three thick bolts forking from the user onto the target, which flashes in a cage of sparks. */
	private static void thunderbolt(Context c) {
		float s = c.size();
		Vec3 from = c.mouth();
		Vec3 to = c.aim();
		boolean travel = phase(c).equals("travel");
		float reach = travel ? Math.min(1, c.k() * 1.6f) : 1;
		float fade = travel ? 1 : 1 - seg(c.k(), 0.35f, 0.7f);
		if (fade > 0) {
			Vec3 sd = side(flat(to.subtract(from)));
			for (int i = 0; i < 3; i++) {
				Vec3 mid = from.lerp(to, 0.5).add(sd.scale((i - 1) * 0.9 * s)).add(0, (i == 1 ? 0.6 : 0.2) * s, 0);
				Motifs.bolt(c.glow(), c.m(), c.camera(), from, from.lerp(mid, Math.min(1, reach * 2)), c.t(), 0.1f * s * fade, c.color(), i * 5);
				if (reach > 0.5f) {
					Motifs.bolt(c.glow(), c.m(), c.camera(), mid, mid.lerp(to, (reach - 0.5f) * 2), c.t(), 0.1f * s * fade, c.color(), i * 5 + 1);
				}
			}
			Draw.halo(c.glow(), c.m(), from, c.camera(), 0, 0.8f * s, c.accent(), op(200 * fade));
		}
		if (travel) {
			return;
		}
		float k = c.k();
		float flash = seg(k, 0, 0.3f);
		Draw.halo(c.glow(), c.m(), to, c.camera(), 0, (1.2f + 1.5f * flash) * s, 0xFFFFFF, op(230 * (1 - flash)));
		Draw.halo(c.glow(), c.m(), to, c.camera(), 0, 2.2f * s, c.color(), op(180 * (1 - seg(k, 0.2f, 1f))));
		float r = c.move().targetWidth() * 0.6f + 0.4f * s;
		if (k < 0.85f) {
			sparks(c, to, r, 7, 0.05f * s);
			for (int i = 0; i < 4; i++) {
				if (((int) (c.t() / 2) + i) % 3 == 0) {
					Vec3 g = c.move().aimFeet().add(dirOf(i * 1.6 + c.t() * 0.3).scale(r + 0.8 * s));
					Motifs.bolt(c.glow(), c.m(), c.camera(), to, g, c.t(), 0.05f * s, c.color(), 20 + i);
				}
			}
		}
	}

	/** Thunder Wave: jagged rings of electricity rolling to the target, then a crackling cage round it. */
	private static void thunderWave(Context c) {
		float s = c.size();
		Vec3 from = c.mouth();
		Vec3 axis = c.aim().subtract(from);
		Vec3[] b = Draw.basis(axis.normalize());
		float k = c.k();
		for (int i = 0; i < 3; i++) {
			float p = seg(k, i * 0.1f, i * 0.1f + 0.4f);
			if (p <= 0 || p >= 1) {
				continue;
			}
			float r = (0.35f + 0.5f * p) * s;
			zigRing(c, from.add(axis.scale(p)), b, r, 0.14f * r, 16, c.color(), 240 * (1 - p * 0.3f));
		}
		float cage = seg(k, 0.35f, 0.5f);
		if (cage <= 0) {
			return;
		}
		float fade = 1 - seg(k, 0.85f, 1f);
		Vec3 feet = c.move().aimFeet();
		float w = c.move().targetWidth() * 0.6f + 0.35f * s;
		float h = c.move().targetHeight() + 0.3f;
		for (int i = 0; i < 6; i++) {
			double angle = Mth.TWO_PI * i / 6 + 0.25;
			Vec3 bottom = feet.add(Math.cos(angle) * w, 0.05, Math.sin(angle) * w);
			if (((int) (c.t() / 2) + i) % 4 != 0) {
				Motifs.bolt(c.glow(), c.m(), c.camera(), bottom, bottom.add(0, h * cage, 0), c.t() + i * 3, 0.04f * s * fade, c.color(), i);
			}
		}
		Vec3[] flat = Draw.basis(UP);
		zigRing(c, feet.add(0, 0.05, 0), flat, w, 0.12f * w, 12, c.color(), 220 * fade);
		zigRing(c, feet.add(0, h * cage, 0), flat, w, 0.12f * w, 12, c.accent(), 220 * fade);
		Draw.halo(c.glow(), c.m(), c.aim(), c.camera(), 0, w + 0.6f * s, c.color(), op(100 * fade * cage));
	}

	/** A ring of {@code points} zigzags in the plane ({@code b[0]}, {@code b[1]}), turning. */
	private static void zigRing(Context c, Vec3 centre, Vec3[] b, float r, float jag, int points, int color, float alpha) {
		Vec3 last = null;
		for (int i = 0; i <= points; i++) {
			double angle = Mth.TWO_PI * i / points + c.t() * 0.3;
			float rr = r + (i % 2 == 0 ? jag : -jag);
			Vec3 at = centre.add(b[0].scale(Math.cos(angle) * rr)).add(b[1].scale(Math.sin(angle) * rr));
			if (last != null) {
				line(c, last, at, 0.04f * c.size(), color, alpha);
			}
			last = at;
		}
	}

	private static Vec3 moonAt(Context c) {
		return c.userCentre().add(0, c.move().user().getBbHeight() * 0.6 + 1.5 * c.size(), 0);
	}

	/**
	 * Moonblast: a full moon rising over the user with stars circling it, its light poured onto the target in a beam
	 * wound with sparkles, and a great star of moonlight bursting there.
	 */
	private static void moonblast(Context c) {
		float s = c.size();
		switch (phase(c)) {
			case "wind" -> moon(c, ease(seg(c.k(), 0, 0.6f)), 1 - seg(c.k(), 0.85f, 1f));
			case "travel" -> {
				Vec3 from = moonAt(c);
				Vec3 to = c.aim();
				float reach = Math.min(1, c.k() * 1.8f);
				Vec3 end = from.lerp(to, reach);
				Draw.streak(c.glow(), c.m(), end, from, c.camera(), 0.7f * s, c.color(), 150);
				Draw.streak(c.glow(), c.m(), end, from, c.camera(), 0.32f * s, c.accent(), 220);
				Draw.streak(c.glow(), c.m(), end, from, c.camera(), 0.1f * s, 0xFFFFFF, 255);
				Vec3 axis = to.subtract(from);
				Vec3[] b = Draw.basis(axis.normalize());
				for (int i = 0; i < 10; i++) {
					double p = (i / 10.0 + c.t() * 0.08) % 1.0;
					if (p > reach) {
						continue;
					}
					double angle = p * 14 + c.t() * 0.6;
					Vec3 at = from.add(axis.scale(p)).add(b[0].scale(Math.cos(angle) * 0.5 * s)).add(b[1].scale(Math.sin(angle) * 0.5 * s));
					twinkle(c, at, 0.3f * s, i % 2 == 0 ? 0xFFFFFF : c.color(), 230);
				}
			}
			default -> {
				Vec3 at = c.aim();
				float k = c.k();
				float fade = 1 - k;
				float open = ease(seg(k, 0, 0.25f));
				Vec3[] b = c.screen(at);
				for (int i = 0; i < 8; i++) {
					double angle = Math.PI / 4 * i;
					Vec3 d = b[0].scale(Math.cos(angle)).add(b[1].scale(Math.sin(angle)));
					float len = (i % 2 == 0 ? 2.6f : 1.2f) * s * open;
					Draw.streak(c.glow(), c.m(), at, at.add(d.scale(len)), c.camera(), (i % 2 == 0 ? 0.22f : 0.12f) * s, i % 2 == 0 ? 0xFFFFFF : c.color(), op(240 * fade));
				}
				Draw.halo(c.glow(), c.m(), at, c.camera(), 0, (1 + k) * 1.6f * s, c.color(), op(200 * fade));
				Draw.halo(c.glow(), c.m(), at, c.camera(), 0, 0.7f * s * fade, 0xFFFFFF, op(240 * fade));
				for (int i = 0; i < 3; i++) {
					float q = seg(k, i * 0.1f, i * 0.1f + 0.6f);
					Draw.ring(c.glow(), c.m(), at, c.camera().subtract(at).normalize(), (0.4f + 2.5f * q) * s, 0.12f * s, c.accent(), op(220 * (1 - q)));
				}
				RandomSource jag = RandomSource.create(41);
				for (int i = 0; i < 12; i++) {
					Vec3 sparkle = at.add(jag.nextGaussian() * 1.2 * s, jag.nextGaussian() * 0.8 * s - k * 1.2, jag.nextGaussian() * 1.2 * s);
					twinkle(c, sparkle, 0.25f * s, i % 2 == 0 ? 0xFFFFFF : c.accent(), op(220 * bell(seg(k, i * 0.03f, i * 0.03f + 0.6f))));
				}
			}
		}
	}

	private static void moon(Context c, float grow, float alpha) {
		Vec3 at = moonAt(c);
		float s = c.size();
		float r = 0.5f * s * grow;
		Draw.halo(c.glow(), c.m(), at, c.camera(), r, r * 2.2f, c.color(), op(120 * alpha));
		Draw.sphere(c.glow(), c.m(), at, r, c.color(), op(150 * alpha), true);
		Draw.sphere(c.glow(), c.m(), at, r * 0.6f, 0xFFFFFF, op(110 * alpha), true);
		Draw.ring(c.glow(), c.m(), at, c.camera().subtract(at).normalize(), r, 0.08f * s, 0xFFFFFF, op(230 * alpha));
		Vec3[] b = c.screen(at);
		for (int i = 0; i < 4; i++) {
			double angle = c.t() * 0.12 + i * Math.PI / 2;
			Vec3 star = at.add(b[0].scale(Math.cos(angle) * r * 1.9)).add(b[1].scale(Math.sin(angle) * r * 1.9));
			twinkle(c, star, 0.35f * s * grow, c.accent(), op(230 * alpha * (0.6f + 0.4f * Mth.sin(c.t() * 0.7f + i))));
		}
	}

	private static Vec3 circleAt(Context c) {
		return c.mouth().add(flat(c.toTarget()).scale(0.6 * c.size()));
	}

	/** A circle of magic: two rings, a turning six-pointed star, runes going round the other way. */
	private static void magicCircle(Context c, Vec3 at, Vec3 normal, float r, float alpha) {
		if (r <= 0.01f) {
			return;
		}
		float s = c.size();
		Vec3[] b = Draw.basis(normal);
		Draw.ring(c.glow(), c.m(), at, normal, r, 0.06f * s, c.color(), op(230 * alpha));
		Draw.ring(c.glow(), c.m(), at, normal, r * 0.78f, 0.04f * s, c.accent(), op(230 * alpha));
		double spin = c.t() * 0.15;
		Vec3[] pts = new Vec3[6];
		for (int i = 0; i < 6; i++) {
			double angle = spin + Mth.TWO_PI * i / 6;
			pts[i] = at.add(b[0].scale(Math.cos(angle) * r * 0.78)).add(b[1].scale(Math.sin(angle) * r * 0.78));
		}
		for (int i = 0; i < 6; i++) {
			line(c, pts[i], pts[(i + 2) % 6], 0.04f * s, c.color(), 220 * alpha);
		}
		for (int i = 0; i < 8; i++) {
			double angle = -spin * 1.5 + Mth.TWO_PI * i / 8;
			Vec3 rune = at.add(b[0].scale(Math.cos(angle) * r * 0.89)).add(b[1].scale(Math.sin(angle) * r * 0.89));
			Draw.halo(c.glow(), c.m(), rune, c.camera(), 0, 0.09f * s, c.accent(), op(230 * alpha));
		}
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, r * 1.2f, c.color(), op(90 * alpha));
	}

	/** Mystical Fire: a magic circle opens before the user, pink fire twists out of it and wraps the target. */
	private static void mysticalFire(Context c) {
		float s = c.size();
		Vec3 circle = circleAt(c);
		switch (phase(c)) {
			case "wind" -> magicCircle(c, circle, c.toTarget(), 0.8f * s * ease(seg(c.k(), 0, 0.5f)), 1 - seg(c.k(), 0.9f, 1f));
			case "travel" -> {
				Vec3 axis = c.aim().subtract(circle);
				Vec3[] b = Draw.basis(axis.normalize());
				float reach = Math.min(1, c.k() * 1.5f);
				for (int j = 0; j < 3; j++) {
					for (int i = 0; i < 14; i++) {
						double p = i / 13.0 * reach;
						double angle = p * 9 + c.t() * 0.5 + j * Mth.TWO_PI / 3;
						Vec3 at = circle.add(axis.scale(p)).add(b[0].scale(Math.cos(angle) * 0.35 * s)).add(b[1].scale(Math.sin(angle) * 0.35 * s));
						float r = (0.18f + 0.22f * (float) Math.sin(p * Math.PI)) * s;
						Draw.halo(c.glow(), c.m(), at, c.camera(), 0, r, (i + j) % 3 == 0 ? c.accent() : c.color(), 200);
					}
				}
			}
			default -> {
				Vec3 feet = c.move().aimFeet();
				float w = c.move().targetWidth() * 0.55f + 0.25f;
				float h = c.move().targetHeight();
				float fade = c.fade(0.05f, 0.35f);
				magicCircle(c, feet.add(0, 0.08, 0), UP, w + 0.5f * s, fade);
				for (int i = 0; i < 16; i++) {
					double angle = i * 0.39 + c.t() * 0.25;
					double rise = (c.t() * 0.08 + i * 0.27) % 1;
					Vec3 base = feet.add(Math.cos(angle) * w, rise * h * 1.1, Math.sin(angle) * w);
					Draw.streak(c.glow(), c.m(), base.add(0, 0.7 * s, 0), base, c.camera(), 0.2f * s, i % 3 == 0 ? c.accent() : c.color(),
							op(230 * fade * Math.sin(rise * Math.PI)));
				}
				Draw.halo(c.glow(), c.m(), c.aim(), c.camera(), 0, w + 0.9f * s, c.color(), op(150 * fade));
				for (int i = 0; i < 5; i++) {
					double angle = c.t() * 0.2 + i * Mth.TWO_PI / 5;
					Vec3 star = c.aim().add(Math.cos(angle) * (w + 0.4f), Math.sin(c.t() * 0.3 + i) * h * 0.4, Math.sin(angle) * (w + 0.4f));
					twinkle(c, star, 0.3f * s, 0xFFFFFF, op(220 * fade));
				}
			}
		}
	}

	/** Scald: twin jets of boiling water, steam curling off them, and the target lost in a burst of steam. */
	private static void scald(Context c) {
		float s = c.size();
		Vec3 from = c.mouth();
		Vec3 to = c.aim();
		if (phase(c).equals("travel")) {
			float reach = Math.min(1, c.k() * 1.6f);
			Vec3 axis = to.subtract(from);
			Vec3[] b = Draw.basis(axis.normalize());
			for (int j = 0; j < 2; j++) {
				Vec3[] pts = new Vec3[16];
				for (int i = 0; i < pts.length; i++) {
					double p = reach * (1 - i / 15.0);
					double angle = p * 8 + c.t() * 0.5 + j * Math.PI;
					pts[i] = from.add(axis.scale(p)).add(b[0].scale(Math.cos(angle) * 0.15 * s)).add(b[1].scale(Math.sin(angle) * 0.15 * s));
				}
				ribbon(c, pts, 0.28f * s, 0.18f * s, c.color(), 230, 200);
				ribbon(c, pts, 0.08f * s, 0.06f * s, 0xFF8060, 200, 150);
			}
			for (int i = 0; i < 8; i++) {
				double p = (i / 8.0 + c.t() * 0.05) % 1.0;
				if (p > reach) {
					continue;
				}
				double rise = (c.t() * 0.07 + i * 0.37) % 1.0;
				Vec3 at = from.add(axis.scale(p)).add(0, 0.2 + rise * 0.8 * s, 0);
				Draw.halo(c.glow(), c.m(), at, c.camera(), 0, (float) (0.2 + 0.35 * rise) * s, 0xFFFFFF, op(120 * (1 - rise)));
			}
			return;
		}
		Vec3 feet = c.move().aimFeet();
		float k = c.k();
		float r0 = c.move().targetWidth() * 0.6f + 0.3f;
		float splash = Math.min(1, k * 1.5f);
		for (int i = 0; i < 12; i++) {
			double angle = Mth.TWO_PI * i / 12;
			Vec3 base = feet.add(Math.cos(angle) * r0 * (1 + k), 0.1, Math.sin(angle) * r0 * (1 + k));
			double up = Math.sin(splash * Math.PI) * 1.4 * s;
			Draw.streak(c.glow(), c.m(), base.add(Math.cos(angle) * 0.3, up, Math.sin(angle) * 0.3), base, c.camera(), 0.1f * s, c.accent(), op(220 * (1 - k)));
		}
		RandomSource jag = RandomSource.create(107);
		for (int i = 0; i < 14; i++) {
			double angle = jag.nextDouble() * Mth.TWO_PI;
			double r = (c.move().targetWidth() * 0.5 + jag.nextDouble() * 0.6) * s;
			float start = jag.nextFloat() * 0.3f;
			float p = seg(k, start, start + 0.6f);
			if (p <= 0 || p >= 1) {
				continue;
			}
			Vec3 at = feet.add(Math.cos(angle) * r * (1 + p), 0.3 + p * 2.6 * s, Math.sin(angle) * r * (1 + p));
			Draw.halo(c.glow(), c.m(), at, c.camera(), 0, (0.4f + 0.7f * p) * s, 0xF0F4FF, op(150 * bell(p)));
		}
		for (float pulse : new float[] { 0.15f, 0.45f }) {
			float q = seg(k, pulse, pulse + 0.2f);
			if (q > 0 && q < 1) {
				Draw.halo(c.glow(), c.m(), to, c.camera(), 0, (1 + q) * 1.2f * s, 0xFF5030, op(160 * (1 - q)));
			}
		}
		float hit = seg(k, 0, 0.4f);
		if (hit < 1) {
			Motifs.impact(c, to, hit, 0.9f);
		}
	}

	/** Sludge Bomb: glistening globs of poison lobbed high, dripping, splattering on the target into a bubbling pool. */
	private static void sludgeBomb(Context c) {
		float s = c.size();
		int core = 0x3A1050;
		if (phase(c).equals("travel")) {
			int blobs = c.count(3);
			for (int i = 0; i < blobs; i++) {
				float k = Mth.clamp(c.k() * 1.3f - i * 0.12f, 0, 1);
				if (k <= 0 || k >= 1) {
					continue;
				}
				Vec3 at = lobbed(c, k, s);
				float r = (0.5f - i * 0.07f) * s;
				Draw.sphere(c.buffers().getBuffer(Draw.DARK), c.m(), at, r * 0.8f, core, 220, false);
				Draw.sphere(c.glow(), c.m(), at, r, c.color(), 150, true);
				Draw.halo(c.glow(), c.m(), at, c.camera(), r * 0.8f, r * 2.2f, c.color(), 150);
				Draw.halo(c.glow(), c.m(), at.add(-0.3 * r, 0.35 * r, 0), c.camera(), 0, r * 0.35f, 0xFFFFFF, 200);
				for (int d = 1; d <= 3; d++) {
					Vec3 drip = lobbed(c, Math.max(0, k - d * 0.06f), s).add(0, -d * 0.15, 0);
					Draw.sphere(c.glow(), c.m(), drip, r * 0.25f, c.accent(), 160, true);
				}
			}
			return;
		}
		Vec3 at = c.aim();
		Vec3 feet = c.move().aimFeet();
		float k = c.k();
		float fade = 1 - seg(k, 0.55f, 1f);
		float q = ease(seg(k, 0, 0.6f));
		RandomSource jag = RandomSource.create(131);
		for (int i = 0; i < 14; i++) {
			Vec3 d = new Vec3(jag.nextGaussian(), Math.abs(jag.nextGaussian()) * 0.7 + 0.2, jag.nextGaussian()).normalize();
			Vec3 drop = at.add(d.scale((0.3 + 1.8 * q) * s)).add(0, -2.0 * q * q * s, 0);
			if (drop.y < feet.y + 0.1) {
				drop = new Vec3(drop.x, feet.y + 0.1, drop.z);
			}
			float r = (0.16f + 0.08f * (i % 3)) * s * fade;
			Draw.sphere(c.glow(), c.m(), drop, r, c.color(), 170, true);
			Draw.halo(c.glow(), c.m(), drop, c.camera(), 0, r * 0.5f, c.accent(), 200);
		}
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, (0.8f + 1.4f * q) * s, c.color(), op(200 * (1 - seg(k, 0, 0.5f))));
		float pool = ease(seg(k, 0, 0.4f));
		Draw.ring(c.glow(), c.m(), feet.add(0, 0.06, 0), UP, (0.5f + 1.2f * pool) * s, 0.6f * s, c.color(), op(170 * fade));
		for (int i = 0; i < 6; i++) {
			float p = ((c.t() * 0.06f) + i / 6f) % 1f;
			double angle = i * 1.9;
			Vec3 bubble = feet.add(Math.cos(angle) * 0.9 * pool * s, 0.08, Math.sin(angle) * 0.9 * pool * s);
			Draw.sphere(c.glow(), c.m(), bubble, (0.05f + 0.12f * p) * s * fade, c.accent(), op(160 * (1 - p)), true);
		}
	}

	private static Vec3 lobbed(Context c, float k, float s) {
		return c.mouth().lerp(c.aim(), k).add(0, Math.sin(k * Math.PI) * 2.2 * s, 0);
	}

	/** Dark comets with long trails: the meteors of Draco Meteor. */
	private static void dracoMeteor(Context c) {
		float s = c.size();
		if (phase(c).equals("wind")) {
			Vec3 from = c.mouth();
			float charge = seg(c.k(), 0, 0.3f);
			Draw.halo(c.glow(), c.m(), from, c.camera(), 0, 0.9f * s * charge, c.color(), op(200 * (1 - seg(c.k(), 0.3f, 0.5f))));
			float p = ease(seg(c.k(), 0.15f, 0.85f));
			if (p > 0) {
				Vec3 head = from.add(0, p * 10, 0);
				Draw.streak(c.glow(), c.m(), head, head.add(0, -2.5 * s * Math.min(1, p * 4), 0), c.camera(), 0.35f * s, c.accent(), 220);
				Draw.halo(c.glow(), c.m(), head, c.camera(), 0, 0.7f * s, c.color(), 230);
				Draw.sphere(c.glow(), c.m(), head, 0.25f * s, 0xFFFFFF, 220, true);
			}
			return;
		}
		int n = c.count(7);
		RandomSource jag = RandomSource.create(c.move().move.hashCode() * 3L);
		Vec3 aim = c.move().aimFeet();
		BlockState rock = Motifs.block(c, Blocks.CRYING_OBSIDIAN.defaultBlockState());
		for (int i = 0; i < n; i++) {
			Vec3 land = i == 0 ? aim : aim.add(jag.nextGaussian() * 1.5 * s, 0, jag.nextGaussian() * 1.5 * s);
			float start = i == 0 ? 0.04f : jag.nextFloat() * 0.5f;
			float p = (c.k() - start) / METEOR_FALL;
			if (p < 0) {
				continue;
			}
			// Slanting in from the side rather than from straight above: a camera framing the field sees little of the sky,
			// so a steep fall would only show its last metres.
			Vec3 sky = land.add(side(flat(c.toTarget())).scale(-6 * s)).add(0, 5.5, 0);
			if (p < 1) {
				Vec3 head = sky.lerp(land, p);
				Vec3 back = sky.subtract(land).normalize();
				Vec3[] pts = new Vec3[6];
				for (int t = 0; t < pts.length; t++) {
					pts[t] = head.add(back.scale(t * 0.9 * s));
				}
				ribbon(c, pts, 0.8f * s, 0.05f, c.color(), 240, 0);
				ribbon(c, pts, 0.32f * s, 0.02f, c.accent(), 250, 0);
				Draw.halo(c.glow(), c.m(), head, c.camera(), 0, 1.3f * s, c.color(), 240);
				Motifs.block(c, rock, head, 0.7f * s, c.t() * 15 + i * 50, c.t() * 12, i * 30);
				Draw.halo(c.glow(), c.m(), head, c.camera(), 0, 0.5f * s, 0xFFFFFF, 160);
				continue;
			}
			float q = (p - 1) * METEOR_FALL / 0.3f;
			if (q >= 1) {
				continue;
			}
			Vec3 hit = land.add(0, 0.3, 0);
			Draw.halo(c.glow(), c.m(), hit, c.camera(), 0, (1 + 2 * q) * 1.2f * s, c.color(), op(230 * (1 - q)));
			Draw.halo(c.glow(), c.m(), hit, c.camera(), 0, 0.8f * s * (1 - q), 0xFFFFFF, op(240 * (1 - q)));
			Draw.ring(c.glow(), c.m(), land.add(0, 0.06, 0), UP, (0.5f + 2.8f * q) * s, 0.35f * s, c.accent(), op(220 * (1 - q)));
			Draw.streak(c.glow(), c.m(), hit, hit.add(0, 2.5 * s * (1 - q), 0), c.camera(), 0.5f * s, c.color(), op(200 * (1 - q)));
			BlockState earth = ground(land);
			for (int d = 0; d < 4; d++) {
				double angle = d * 1.57 + i;
				Vec3 bit = land.add(Math.cos(angle) * q * 1.6 * s, (2.2 * q - 2.6 * q * q) * s + 0.1, Math.sin(angle) * q * 1.6 * s);
				Motifs.block(c, earth, bit, 0.3f * s * (1 - q), q * 400 + d * 60, q * 300, 0);
			}
		}
	}

	/** How long a meteor falls, as a share of the layer. */
	private static final float METEOR_FALL = 0.3f;

	private static void meteorTick(Context c, ClientLevel level) {
		if (!phase(c).equals("hit")) {
			return;
		}
		int n = c.count(7);
		RandomSource jag = RandomSource.create(c.move().move.hashCode() * 3L);
		Vec3 aim = c.move().aimFeet();
		for (int i = 0; i < n; i++) {
			Vec3 land = i == 0 ? aim : aim.add(jag.nextGaussian() * 1.5 * c.size(), 0, jag.nextGaussian() * 1.5 * c.size());
			float start = i == 0 ? 0.04f : jag.nextFloat() * 0.5f;
			if (crossed(c, start + METEOR_FALL)) {
				Motifs.dust(level, land, 25, c.size());
				level.addParticle(ParticleTypes.EXPLOSION, land.x, land.y + 0.5, land.z, 0, 0, 0);
				BattleCameraDirector.shake(0.15f, 0.5f);
			}
		}
	}

	private static Vec3 clockAt(Context c) {
		return c.aim().add(0, c.move().targetHeight() * 0.5 + 1.4 * c.size(), 0);
	}

	/**
	 * Future Sight: a light sent up over the foe opens into a clock whose hands race ahead (the blow comes turns
	 * later); if the move strikes now, light pours down from it onto the target.
	 */
	private static void futureSight(Context c) {
		float s = c.size();
		Vec3 at = clockAt(c);
		if (phase(c).equals("hit")) {
			float strike = seg(c.k(), 0, 0.7f);
			if (strike < 1) {
				Draw.streak(c.glow(), c.m(), c.aim(), at, c.camera(), 0.8f * s, c.color(), op(200 * bell(strike)));
				Draw.streak(c.glow(), c.m(), c.aim(), at, c.camera(), 0.25f * s, 0xFFFFFF, op(240 * bell(strike)));
			}
			Motifs.burst(c, c.aim(), c.k(), 1.1f);
			return;
		}
		float k = c.k();
		float launch = ease(seg(k, 0, 0.25f));
		if (launch < 1) {
			Vec3 orb = c.mouth().lerp(at, launch).add(0, Math.sin(launch * Math.PI) * 1.2, 0);
			Draw.halo(c.glow(), c.m(), orb, c.camera(), 0, 0.6f * s, c.color(), 220);
			Draw.sphere(c.glow(), c.m(), orb, 0.22f * s, 0xFFFFFF, 200, true);
		}
		float appear = ease(seg(k, 0.2f, 0.4f));
		float fade = 1 - seg(k, 0.85f, 1f);
		if (appear <= 0) {
			return;
		}
		Vec3[] b = c.screen(at);
		float r = 0.95f * s * appear;
		Vec3 normal = c.camera().subtract(at).normalize();
		Draw.ring(c.glow(), c.m(), at, normal, r, 0.1f * s, c.color(), op(230 * fade));
		Draw.ring(c.glow(), c.m(), at, normal, r * 0.82f, 0.05f * s, c.accent(), op(220 * fade));
		Draw.halo(c.glow(), c.m(), at, c.camera(), r * 0.5f, r * 1.6f, c.color(), op(90 * fade));
		for (int i = 0; i < 12; i++) {
			double angle = Mth.TWO_PI * i / 12;
			Vec3 d = b[0].scale(Math.cos(angle)).add(b[1].scale(Math.sin(angle)));
			line(c, at.add(d.scale(r * 0.86)), at.add(d.scale(r * (i % 3 == 0 ? 0.66 : 0.76))), 0.05f * s, 0xFFFFFF, 230 * fade);
		}
		float run = seg(k, 0.25f, 1f);
		float turns = 6 * run * run;
		double hour = -turns * Mth.TWO_PI / 12 + Math.PI / 2;
		double minute = -turns * Mth.TWO_PI + Math.PI / 2;
		line(c, at, at.add(b[0].scale(Math.cos(hour) * r * 0.45)).add(b[1].scale(Math.sin(hour) * r * 0.45)), 0.09f * s, c.accent(), 240 * fade);
		line(c, at, at.add(b[0].scale(Math.cos(minute) * r * 0.7)).add(b[1].scale(Math.sin(minute) * r * 0.7)), 0.06f * s, 0xFFFFFF, 240 * fade);
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, 0.2f * s, 0xFFFFFF, op(240 * fade));
	}

	/** Psyshock: psychic power hardened into crystals round the target, slammed into it, shattering. */
	private static void psyshock(Context c) {
		Vec3 at = c.aim();
		float s = c.size();
		float k = c.k();
		BlockState amethyst = Blocks.AMETHYST_BLOCK.defaultBlockState();
		float far = c.move().targetWidth() * 0.6f + 1.3f * s;
		float near = c.move().targetWidth() * 0.45f + 0.15f;
		float form = ease(seg(k, 0, 0.35f));
		float slam = seg(k, 0.38f, 0.5f);
		slam = slam * slam;
		float gone = seg(k, 0.5f, 0.56f);
		for (int i = 0; i < 9; i++) {
			// Spread over a sphere (a golden-angle spiral), the lowest ones left out: they would be in the ground.
			double y = 0.85 - 1.4 * (i + 0.5) / 9;
			double ring = Math.sqrt(1 - y * y);
			double angle = i * 2.39996 + c.t() * 0.03 * (1 - slam);
			Vec3 d = new Vec3(Math.cos(angle) * ring, y, Math.sin(angle) * ring);
			Vec3 pos = at.add(d.scale(Mth.lerp(slam, far, near)));
			float size = form * (1 - gone);
			crystalToward(c, amethyst, pos, 0.32f * s * size, 0.95f * s * size, d.scale(-1));
			Draw.halo(c.glow(), c.m(), pos, c.camera(), 0, 0.45f * s, c.color(), op(140 * size));
		}
		float shock = seg(k, 0.48f, 1f);
		if (shock <= 0 || shock >= 1) {
			return;
		}
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, (1 + 1.5f * shock) * 1.2f * s, c.color(), op(220 * (1 - shock)));
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, 0.8f * s * (1 - shock), 0xFFFFFF, op(240 * (1 - shock)));
		Draw.ring(c.glow(), c.m(), at, c.camera().subtract(at).normalize(), (0.5f + 2.5f * shock) * s, 0.18f * s, c.accent(), op(220 * (1 - shock)));
		RandomSource jag = RandomSource.create(97);
		for (int i = 0; i < 12; i++) {
			twinkle(c, at.add(randomUnit(jag).scale((0.4 + 2.2 * ease(shock)) * s)), 0.3f * s, c.accent(), op(230 * (1 - shock)));
		}
	}

	private static final int[] RAINBOW = { 0xFF4040, 0xFF9A30, 0xFFE840, 0x50E060, 0x40D8FF, 0x4870FF, 0xB060FF };

	private static final BlockState[] PRISMS = { Blocks.RED_STAINED_GLASS.defaultBlockState(), Blocks.ORANGE_STAINED_GLASS.defaultBlockState(),
			Blocks.YELLOW_STAINED_GLASS.defaultBlockState(), Blocks.LIME_STAINED_GLASS.defaultBlockState(),
			Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(), Blocks.BLUE_STAINED_GLASS.defaultBlockState(),
			Blocks.PURPLE_STAINED_GLASS.defaultBlockState() };

	/** Tera Starstorm: prisms of every colour turning round the user, then beams of each colour from the sky. */
	private static void teraStarstorm(Context c) {
		float s = c.size();
		if (phase(c).equals("wind")) {
			Entity user = c.move().user();
			float r = user.getBbWidth() * 0.8f + 0.8f * s;
			float fade = c.fade(0.15f, 0.1f);
			for (int i = 0; i < 7; i++) {
				double angle = Mth.TWO_PI * i / 7 + c.t() * 0.12;
				Vec3 at = user.position().add(Math.cos(angle) * r, user.getBbHeight() * (0.3 + 0.7 * c.k()) + 0.2 * Math.sin(c.t() * 0.2 + i), Math.sin(angle) * r);
				crystal(c, PRISMS[i], at, 0.35f * s * fade, 0.8f * s * fade, c.t() * 5 + i * 50, 0);
				Draw.halo(c.glow(), c.m(), at, c.camera(), 0, 0.5f * s, RAINBOW[i], op(150 * fade));
			}
			twinkle(c, user.position().add(0, user.getBbHeight() + 0.6 * s, 0), 0.9f * s, 0xFFFFFF, op(230 * bell(c.k())));
			return;
		}
		Vec3 at = c.aim();
		float k = c.k();
		for (int i = 0; i < 7; i++) {
			float p = seg(k, i * 0.05f, i * 0.05f + 0.45f);
			if (p <= 0 || p >= 1) {
				continue;
			}
			double angle = Mth.TWO_PI * i / 7;
			Vec3 sky = at.add(Math.cos(angle) * 3.5 * s, 9, Math.sin(angle) * 3.5 * s);
			Vec3 end = sky.lerp(at, Math.min(1, p * 4));
			float fade = 1 - seg(p, 0.6f, 1f);
			Draw.streak(c.glow(), c.m(), end, sky, c.camera(), 0.35f * s, RAINBOW[i], op(200 * fade));
			Draw.streak(c.glow(), c.m(), end, sky, c.camera(), 0.1f * s, 0xFFFFFF, op(230 * fade));
		}
		float burst = seg(k, 0.25f, 0.9f);
		if (burst <= 0 || burst >= 1) {
			return;
		}
		Vec3 normal = c.camera().subtract(at).normalize();
		for (int i = 0; i < 7; i++) {
			Draw.ring(c.glow(), c.m(), at, normal, (0.4f + 2.8f * burst) * s + i * 0.14f * s, 0.1f * s, RAINBOW[i], op(220 * (1 - burst)));
		}
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, 2 * s, 0xFFFFFF, op(220 * (1 - burst)));
		RandomSource jag = RandomSource.create(139);
		for (int i = 0; i < 10; i++) {
			twinkle(c, at.add(randomUnit(jag).scale((0.5 + 2.5 * burst) * s)), 0.35f * s, RAINBOW[i % 7], op(240 * (1 - burst)));
		}
	}

	/** Make It Rain: a fountain of coins out of the user, then a downpour of gold on the target, bouncing. */
	private static void makeItRain(Context c) {
		float s = c.size();
		ItemStack coin = new ItemStack(Items.GOLD_NUGGET);
		if (phase(c).equals("wind")) {
			Vec3 from = c.userCentre().add(0, c.move().user().getBbHeight() * 0.4, 0);
			RandomSource jag = RandomSource.create(113);
			for (int i = 0; i < 18; i++) {
				float start = jag.nextFloat() * 0.6f;
				double vx = jag.nextGaussian() * 0.25;
				double vz = jag.nextGaussian() * 0.25;
				float p = seg(c.k(), start, start + 0.4f);
				if (p <= 0 || p >= 1) {
					continue;
				}
				Vec3 at = from.add(vx * p * 2 * s, (5 * p - 2 * p * p) * s, vz * p * 2 * s);
				Motifs.item(c, coin, at, 0.6f * s, c.t() * 40 + i * 30, 0, c.t() * 25);
			}
			Draw.halo(c.glow(), c.m(), from, c.camera(), 0, 1.0f * s, c.color(), op(160 * c.fade(0.2f, 0.2f)));
			return;
		}
		int n = c.count(36);
		Vec3 feet = c.move().aimFeet();
		RandomSource jag = RandomSource.create(127);
		for (int i = 0; i < n; i++) {
			float start = jag.nextFloat() * 0.6f;
			double gx = jag.nextGaussian();
			double gz = jag.nextGaussian();
			float p = seg(c.k(), start, start + 0.3f);
			if (p <= 0 || p >= 1) {
				continue;
			}
			Vec3 land = feet.add(gx * 0.9 * s, 0.15, gz * 0.9 * s);
			float fall = Math.min(1, p / 0.7f);
			Vec3 at = p > 0.7f ? land.add(0, Math.sin((p - 0.7f) / 0.3f * Math.PI) * 0.3 * s, 0) : land.add(0, 5.5 * s * (1 - fall * fall), 0);
			boolean ingot = i % 9 == 0;
			Motifs.item(c, ingot ? new ItemStack(Items.GOLD_INGOT) : coin, at, (ingot ? 0.8f : 0.6f) * s * (1 - seg(p, 0.85f, 1f)), c.t() * 35 + i * 40,
					i * 20, c.t() * 20);
		}
		Vec3 at = c.aim();
		RandomSource glints = RandomSource.create(149);
		for (int i = 0; i < 6; i++) {
			Vec3 spot = at.add(randomUnit(glints).scale(c.move().targetWidth() * 0.6 + 0.3));
			twinkle(c, spot, 0.45f * s, 0xFFF4B0, op(240 * bell((c.t() * 0.08f + i * 0.17f) % 1f) * (1 - seg(c.k(), 0.8f, 1f))));
		}
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, 1.8f * s, c.color(), op(120 * (1 - seg(c.k(), 0.6f, 1f))));
	}

	/** A ghostly flame: glow, a pale heart, three flickering tongues. */
	private static void wisp(Context c, Vec3 at, float size, float alpha) {
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, size * 1.6f, c.color(), op(170 * alpha));
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, size * 0.7f, c.accent(), op(220 * alpha));
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, size * 0.3f, 0xFFFFFF, op(240 * alpha));
		for (int f = 0; f < 3; f++) {
			double flick = Math.sin(c.t() * 0.9 + f * 2.1);
			Vec3 tip = at.add((f - 1) * 0.12 * size, (1.3 + 0.3 * flick) * size, 0.05 * flick);
			Draw.streak(c.glow(), c.m(), at.add(0, size * 0.2, 0), tip, c.camera(), size * 0.35f, c.color(), op(200 * alpha));
		}
	}

	private static final int WISPS = 4;

	private static Vec3 wispPath(Context c, int i, float p) {
		float s = c.size();
		Vec3 start = c.userCentre().add(dirOf(i * 2.1).scale(0.8 * s)).add(0, 0.4 * Math.sin(i), 0);
		Vec3 end = c.aim().add(dirOf(i * 2.1 + 1).scale(c.move().targetWidth() * 0.6 + 0.6 * s));
		return start.lerp(end, ease(p)).add(0, Math.sin(p * Math.PI) * 0.8 + Math.sin(c.t() * 0.25 + i) * 0.2, 0);
	}

	private static float wispProgress(Context c, int i) {
		return seg(c.k(), i * 0.08f, 0.7f + i * 0.08f);
	}

	/** Will-O-Wisp: ghostly blue flames drifting over to the target, circling it, sinking in as a burn. */
	private static void willOWisp(Context c) {
		float s = c.size();
		if (phase(c).equals("wind")) {
			for (int i = 0; i < WISPS; i++) {
				float p = wispProgress(c, i);
				if (p > 0) {
					wisp(c, wispPath(c, i, p), 0.28f * s * Math.min(1, p * 5), 1f);
				}
			}
			return;
		}
		Vec3 at = c.aim();
		float k = c.k();
		float close = ease(seg(k, 0, 0.45f));
		float r0 = c.move().targetWidth() * 0.6f + 0.6f * s;
		for (int i = 0; i < WISPS && close < 1; i++) {
			double angle = i * Mth.TWO_PI / WISPS + c.t() * 0.35 + close * 4;
			Vec3 pos = at.add(Math.cos(angle) * r0 * (1 - close), Math.sin(c.t() * 0.25 + i) * 0.2 * (1 - close), Math.sin(angle) * r0 * (1 - close));
			wisp(c, pos, 0.28f * s, 1);
		}
		float flare = seg(k, 0.42f, 0.7f);
		if (flare > 0 && flare < 1) {
			Draw.halo(c.glow(), c.m(), at, c.camera(), 0, (0.8f + 2 * flare) * s, c.color(), op(230 * (1 - flare)));
			Draw.ring(c.glow(), c.m(), at, c.camera().subtract(at).normalize(), (0.4f + 2.2f * flare) * s, 0.15f * s, c.accent(), op(220 * (1 - flare)));
		}
		float burn = seg(k, 0.45f, 0.6f) * (1 - seg(k, 0.85f, 1f));
		if (burn > 0) {
			Vec3 feet = c.move().aimFeet();
			float w = c.move().targetWidth() * 0.55f + 0.2f;
			float h = c.move().targetHeight();
			for (int i = 0; i < 10; i++) {
				double angle = i * 0.63 + c.t() * 0.2;
				double rise = (c.t() * 0.09 + i * 0.29) % 1;
				Vec3 base = feet.add(Math.cos(angle) * w, rise * h, Math.sin(angle) * w);
				Draw.streak(c.glow(), c.m(), base.add(0, 0.6 * s, 0), base, c.camera(), 0.16f * s, i % 2 == 0 ? c.color() : c.accent(),
						op(220 * burn * Math.sin(rise * Math.PI)));
			}
		}
	}

	private static void wispTick(Context c, ClientLevel level) {
		if (phase(c).equals("wind")) {
			for (int i = 0; i < WISPS; i++) {
				float p = wispProgress(c, i);
				if (p > 0 && p < 1) {
					Vec3 at = wispPath(c, i, p);
					level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 0, 0.02, 0);
				}
			}
		} else if (c.k() > 0.45f && c.k() < 0.85f) {
			Vec3 at = c.aim();
			RandomSource random = c.random();
			level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, at.x + random.nextGaussian() * 0.3, at.y + random.nextGaussian() * 0.3,
					at.z + random.nextGaussian() * 0.3, 0, 0.04, 0);
		}
	}

	// =====================================================================
	// Wind and weather
	// =====================================================================

	/** Hurricane: a true funnel of wind (narrow at the ground, wide at the top) round the target, a storm cloud on it. */
	private static void hurricane(Context c) {
		Vec3 base = c.move().aimFeet();
		float s = c.size();
		float grow = ease(seg(c.k(), 0, 0.25f));
		float fade = 1 - seg(c.k(), 0.8f, 1f);
		float height = 4.8f * s * grow;
		for (int j = 0; j < 4; j++) {
			Vec3[] pts = new Vec3[36];
			for (int i = 0; i < pts.length; i++) {
				double p = i / 35.0;
				double angle = c.t() * 0.75 + p * 11 + j * Math.PI / 2;
				double r = funnel(p, s);
				pts[i] = base.add(Math.cos(angle) * r, p * height, Math.sin(angle) * r);
			}
			ribbon(c, pts, 0.1f * s, 0.22f * s, j % 2 == 0 ? c.color() : 0xFFFFFF, 230 * fade, 200 * fade);
		}
		for (int i = 0; i < 6; i++) {
			double p = (i + 0.5) / 6.0;
			float spin = c.t() * (0.9f - i * 0.08f);
			Draw.arc(c.glow(), c.m(), base.add(0, p * height, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 1), (float) funnel(p, s), spin, spin + 4.2f, 1f,
					0.12f * s, c.accent(), op(200 * fade));
		}
		for (int i = 0; i < 9; i++) {
			double angle = i * 0.7 + c.t() * 0.05;
			double r = (1.6 + 0.6 * Math.sin(i * 2.1)) * s;
			Vec3 cloud = base.add(Math.cos(angle) * r, height + 0.4 * s + 0.2 * Math.sin(i), Math.sin(angle) * r);
			Draw.halo(c.glow(), c.m(), cloud, c.camera(), 0, 1.1f * s, 0xC8D4E4, op(80 * fade * grow));
		}
	}

	/** The hurricane's radius {@code p} of the way up. */
	private static double funnel(double p, float s) {
		return (0.3 + 1.7 * Math.pow(p, 1.4)) * s;
	}

	private static void hurricaneTick(Context c, ClientLevel level) {
		Vec3 base = c.move().aimFeet();
		float s = c.size();
		float height = 4.8f * s * ease(seg(c.k(), 0, 0.25f));
		RandomSource random = c.random();
		for (int i = 0; i < 1; i++) {
			double p = random.nextDouble();
			double angle = random.nextDouble() * Mth.TWO_PI;
			double r = funnel(p, s);
			level.addParticle(ParticleTypes.CLOUD, base.x + Math.cos(angle) * r, base.y + p * height, base.z + Math.sin(angle) * r,
					-Math.sin(angle) * 0.25, 0.08, Math.cos(angle) * 0.25);
		}
	}

	/** Defog: a gale from behind the user sweeping the field, the haze on the far side and the hazards blown away. */
	private static void defog(Context c) {
		Vec3 from = c.userCentre();
		Vec3 to = c.aim();
		Vec3 dir = flat(to.subtract(from));
		Vec3 sd = side(dir);
		float s = c.size();
		double dist = flatDist(from, to);
		float fade = 1 - seg(c.k(), 0.8f, 1f);
		Vec3 feet = c.move().user().position();
		for (int i = 0; i < 14; i++) {
			float q = seg(c.k(), i * 0.025f, i * 0.025f + 0.6f);
			if (q <= 0 || q >= 1) {
				continue;
			}
			double lane = (i % 7 - 3) * 0.45 * s;
			double y = 0.25 + (i * 0.37 % 1.0) * 2.0 * s;
			double head = -1.5 + q * (dist + 5);
			Vec3[] pts = new Vec3[12];
			for (int j = 0; j < pts.length; j++) {
				double x = head - j * 0.22 * s;
				pts[j] = feet.add(dir.scale(x)).add(sd.scale(lane + Math.sin(x * 1.3 + i) * 0.3)).add(0, y + Math.sin(x * 2.1 + i * 0.7) * 0.15, 0);
			}
			ribbon(c, pts, 0.14f * s, 0.01f, i % 3 == 0 ? c.accent() : 0xFFFFFF, 250 * bell(q) * fade, 0);
			ribbon(c, pts, 0.04f * s, 0.01f, 0xFFFFFF, 255 * bell(q) * fade, 0);
		}
		RandomSource jag = RandomSource.create(c.move().move.hashCode() * 29L);
		float blow = ease(seg(c.k(), 0.15f, 0.85f));
		for (int i = 0; i < 12; i++) {
			Vec3 puff = to.add(jag.nextGaussian() * 1.2 * s, jag.nextGaussian() * 0.5 * s, jag.nextGaussian() * 1.2 * s).add(dir.scale(blow * 4 * s)).add(0, blow * 0.8, 0);
			Draw.halo(c.glow(), c.m(), puff, c.camera(), 0, (0.6f + 0.6f * blow) * s, 0xD8E4F0, op(150 * (1 - blow) * seg(c.k(), 0, 0.12f)));
		}
		Vec3 ground = c.move().aimFeet();
		for (int i = 0; i < 4; i++) {
			float q = seg(c.k(), 0.2f + i * 0.06f, 0.75f + i * 0.06f);
			if (q <= 0 || q >= 1) {
				continue;
			}
			Vec3 start = ground.add(sd.scale((i - 1.5) * 0.8 * s)).add(dir.scale(-0.4 * (i % 2))).add(0, 0.2, 0);
			Vec3 pos = start.add(dir.scale(q * 5 * s)).add(0, Math.sin(q * Math.PI) * 1.5 * s, 0);
			BlockState state = i % 2 == 0 ? Blocks.DRIPSTONE_BLOCK.defaultBlockState() : Blocks.IRON_BLOCK.defaultBlockState();
			crystal(c, state, pos, 0.3f * s * (1 - q), 0.6f * s * (1 - q), c.t() * 30 + i * 70, c.t() * 40);
		}
	}

	private static void defogTick(Context c, ClientLevel level) {
		Vec3 from = c.move().user().position();
		Vec3 dir = flat(c.aim().subtract(from));
		Vec3 sd = side(dir);
		double dist = flatDist(from, c.aim());
		RandomSource random = c.random();
		for (int i = 0; i < 1; i++) {
			Vec3 at = from.add(dir.scale(-1 + random.nextDouble() * dist)).add(sd.scale(random.nextGaussian() * 1.5)).add(0, 0.3 + random.nextDouble() * 1.8, 0);
			level.addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, dir.x * 0.45, 0.02, dir.z * 0.45);
		}
	}

	private static void scaldTick(Context c, ClientLevel level) {
		if (!phase(c).equals("hit") || c.k() > 0.7f) {
			return;
		}
		Vec3 feet = c.move().aimFeet();
		RandomSource random = c.random();
		for (int i = 0; i < 2; i++) {
			level.addParticle(ParticleTypes.CLOUD, feet.x + random.nextGaussian() * 0.5, feet.y + 0.3 + random.nextDouble(), feet.z + random.nextGaussian() * 0.5,
					0, 0.08, 0);
		}
	}

	// =====================================================================
	// On the user
	// =====================================================================

	/** Swords Dance: glowing swords circling the user, rising, then gathered into a crown over its head with a flash. */
	private static void swordsDance(Context c) {
		Entity user = c.move().user();
		int n = c.count(8);
		float s = c.size();
		float fade = c.fade(0.06f, 0.1f);
		float r = user.getBbWidth() * 0.8f + 0.7f * s;
		float gather = ease(seg(c.k(), 0.55f, 0.8f));
		float h = user.getBbHeight();
		Vec3 base = user.position();
		Vec3 top = base.add(0, h + 1.0 * s, 0);
		ItemStack sword = Motifs.stack(c, Items.IRON_SWORD);
		float scale = 1.3f * s;
		for (int i = 0; i < n; i++) {
			double angle = Mth.TWO_PI * i / n + c.t() * 0.22 * (1 - 0.6 * gather);
			double radius = Mth.lerp(gather, r * (1.3f - 0.3f * seg(c.k(), 0, 0.5f)), 0.42f * s);
			double y = Mth.lerp(gather, h * (0.15f + 0.6f * seg(c.k(), 0, 0.55f)), h + 1.0f * s);
			Vec3 at = base.add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
			// Leaning out while they circle, tips together once gathered.
			float lean = 30 * (1 - gather) - 12 * gather;
			float yaw = (float) -Math.toDegrees(angle);
			float roll = -45 - lean;
			Motifs.item(c, sword, at, scale * fade, yaw, 0, roll);
			Vec3 blade = bladeDirection(yaw, roll);
			Draw.streak(c.glow(), c.m(), at.add(blade.scale(0.32 * scale)), at.subtract(blade.scale(0.18 * scale)), c.camera(), 0.07f * s, c.accent(), op(200 * fade));
			Draw.halo(c.glow(), c.m(), at, c.camera(), 0, 0.45f * s, c.color(), op(90 * fade));
		}
		float flash = seg(c.k(), 0.78f, 1f);
		if (flash > 0 && flash < 1) {
			Draw.ring(c.glow(), c.m(), top, UP, (0.3f + 2.2f * flash) * s, 0.25f * s, c.color(), op(230 * (1 - flash)));
			twinkle(c, top.add(0, 0.4 * s, 0), 1.4f * s * (1 - flash * 0.5f), 0xFFFFFF, op(255 * (1 - flash)));
			Draw.halo(c.glow(), c.m(), top, c.camera(), 0, 1.5f * s, c.accent(), op(180 * (1 - flash)));
		}
	}

	/**
	 * Where a sword drawn by {@link Motifs#item} points (hilt to tip): the item frame's display turns the sprite round, so
	 * its blade points up-left (135°) before the roll, then the yaw turns it.
	 */
	private static Vec3 bladeDirection(float yaw, float roll) {
		double angle = Math.toRadians(135 + roll);
		double x = Math.cos(angle);
		double yr = Math.toRadians(yaw);
		return new Vec3(x * Math.cos(yr), Math.sin(angle), -x * Math.sin(yr));
	}

	/** Roost: the wings folding round the user, feathers drifting down, a soft glow. */
	private static void roost(Context c) {
		Entity user = c.move().user();
		float s = c.size();
		float h = user.getBbHeight();
		float r = user.getBbWidth() * 0.7f + 0.5f * s;
		Vec3 base = user.position();
		Vec3 centre = c.userCentre();
		float fade = c.fade(0.08f, 0.2f);
		Vec3 toCamera = flat(c.camera().subtract(centre));
		Vec3 sd = side(toCamera);
		float fold = ease(seg(c.k(), 0, 0.45f));
		float wings = fade * (1 - seg(c.k(), 0.5f, 0.75f));
		for (int wing = -1; wing <= 1; wing += 2) {
			for (int f = 0; f < 6; f++) {
				double angle = 1.5 - 1.2 * fold - f * 0.2;
				Vec3 root = centre.add(sd.scale(wing * 0.2 * s)).add(0, h * 0.15, 0);
				double len = (1.5 - f * 0.12) * s * Math.max(1, h * 0.6);
				Vec3 d = sd.scale(wing * Math.sin(angle)).add(0, Math.cos(angle), 0).add(toCamera.scale(fold * 0.6)).normalize();
				Draw.streak(c.glow(), c.m(), root.add(d.scale(len)), root, c.camera(), 0.12f * s, f == 0 ? 0xFFFFFF : c.color(), op(180 * wings));
			}
		}
		ItemStack feather = new ItemStack(Items.FEATHER);
		for (int i = 0; i < 12; i++) {
			float p = seg(c.k(), i * 0.04f, i * 0.04f + 0.55f);
			if (p <= 0 || p >= 1) {
				continue;
			}
			double angle = i * 2.4;
			double sway = Math.sin(c.t() * 0.25 + i) * 0.35;
			Vec3 at = base.add(Math.cos(angle) * r + sway, (h + 1.6 * s) * (1 - p), Math.sin(angle) * r + sway * 0.5);
			float pop = Math.min(1, Math.min(p, 1 - p) * 6);
			Motifs.item(c, feather, at, 0.55f * s * pop, c.t() * 6 + i * 40, (float) (Math.sin(c.t() * 0.3 + i) * 35), (float) (sway * 60));
			Draw.halo(c.glow(), c.m(), at, c.camera(), 0, 0.25f * s, 0xFFFFFF, op(90 * pop));
		}
		Draw.halo(c.glow(), c.m(), centre, c.camera(), r * 0.3f, r * 1.7f, c.accent(), op(110 * fade * seg(c.k(), 0.3f, 0.6f)));
	}

	/** Recover: rings of light coming down over the user, motes drawn into it, and it glows whole again. */
	private static void recover(Context c) {
		Entity user = c.move().user();
		float s = c.size();
		float h = user.getBbHeight();
		float r = user.getBbWidth() * 0.75f + 0.5f * s;
		Vec3 base = user.position();
		Vec3 centre = c.userCentre();
		float fade = c.fade(0.08f, 0.2f);
		for (int i = 0; i < 3; i++) {
			float p = seg(c.k(), i * 0.12f, i * 0.12f + 0.55f);
			if (p <= 0 || p >= 1) {
				continue;
			}
			Vec3 at = base.add(0, (h + 1.2 * s) * (1 - ease(p)), 0);
			float radius = r * (1.15f - 0.25f * p);
			Draw.ring(c.glow(), c.m(), at, UP, radius, 0.16f * s, c.color(), op(230 * bell(p)));
			Draw.ring(c.glow(), c.m(), at, UP, radius, 0.05f * s, 0xFFFFFF, op(230 * bell(p)));
		}
		float motes = fade * (1 - seg(c.k(), 0.6f, 0.8f));
		for (int i = 0; i < 18; i++) {
			float p = ((c.t() * 0.045f) + i / 18f) % 1f;
			double angle = i * 2.4 + c.t() * 0.05;
			double dist = (2.2 - 2.0 * p) * s;
			Vec3 at = centre.add(Math.cos(angle) * dist, Math.sin(i * 1.3) * dist * 0.5, Math.sin(angle) * dist);
			Draw.streak(c.glow(), c.m(), at, at.add(at.subtract(centre).normalize().scale(0.35 * s)), c.camera(), 0.05f * s, c.accent(), op(220 * motes * bell(p)));
		}
		float whole = seg(c.k(), 0.55f, 0.75f) * (1 - seg(c.k(), 0.8f, 1f));
		Draw.halo(c.glow(), c.m(), centre, c.camera(), r * 0.2f, r * 2.0f, c.color(), op(170 * whole));
		Vec3[] b = c.screen(centre);
		for (int i = 0; i < 4; i++) {
			double angle = i * Math.PI / 2 + 0.5;
			Vec3 at = centre.add(b[0].scale(Math.cos(angle) * r)).add(b[1].scale(Math.sin(angle) * r * 1.1));
			twinkle(c, at, 0.6f * s, 0xFFFFFF, op(230 * bell(seg(c.k(), 0.6f + i * 0.05f, 0.85f + i * 0.05f))));
		}
	}

	/** Wish: a star rising from the user into the sky, flaring, its light falling back as a shower of stars. */
	private static void wish(Context c) {
		Entity user = c.move().user();
		float s = c.size();
		float k = c.k();
		Vec3 head = user.position().add(0, user.getBbHeight() + 0.3, 0);
		float rise = ease(seg(k, 0, 0.35f));
		Vec3 star = head.add(0, rise * 5 * s, 0);
		float flare = seg(k, 0.35f, 0.5f);
		float shine = 1 - seg(k, 0.45f, 0.6f);
		if (shine > 0) {
			Vec3[] pts = new Vec3[8];
			for (int i = 0; i < pts.length; i++) {
				pts[i] = head.add(0, Math.max(0, rise - i * 0.04f) * 5 * s, 0);
			}
			ribbon(c, pts, 0.18f * s, 0.02f, c.color(), 220 * shine, 0);
			twinkle(c, star, (0.7f + 1.6f * bell(flare)) * s, 0xFFFFFF, op(255 * shine));
			Draw.halo(c.glow(), c.m(), star, c.camera(), 0, (0.8f + 2 * bell(flare)) * s, c.color(), op(200 * shine));
			if (flare > 0 && flare < 1) {
				Draw.ring(c.glow(), c.m(), star, c.camera().subtract(star).normalize(), (0.4f + 2.4f * flare) * s, 0.12f * s, c.accent(), op(220 * (1 - flare)));
			}
		}
		RandomSource jag = RandomSource.create(101);
		float r = user.getBbWidth() * 0.7f + 0.8f * s;
		for (int i = 0; i < 16; i++) {
			float start = 0.45f + jag.nextFloat() * 0.3f;
			double angle = jag.nextDouble() * Mth.TWO_PI;
			double dist = jag.nextDouble() * r;
			float p = seg(k, start, start + 0.3f);
			if (p <= 0 || p >= 1) {
				continue;
			}
			Vec3 at = user.position().add(Math.cos(angle) * dist + Math.sin(c.t() * 0.2 + i) * 0.15, (user.getBbHeight() + 3.5 * s) * (1 - p), Math.sin(angle) * dist);
			twinkle(c, at, 0.28f * s, i % 2 == 0 ? 0xFFFFFF : c.color(), op(240 * bell(p)));
		}
		float glow = seg(k, 0.7f, 0.85f) * (1 - seg(k, 0.88f, 1f));
		Draw.halo(c.glow(), c.m(), c.userCentre(), c.camera(), 0, (user.getBbWidth() + 1.2f) * s, c.color(), op(140 * glow));
	}

	/** Substitute: the user lost in a puff of smoke, a little wool doll popping up in front of it as its decoy. */
	private static void substitute(Context c) {
		Entity user = c.move().user();
		float s = c.size();
		Vec3 centre = c.userCentre();
		float k = c.k();
		float puff = seg(k, 0, 0.35f);
		if (puff < 1) {
			RandomSource jag = RandomSource.create(53);
			for (int i = 0; i < 12; i++) {
				Vec3 d = new Vec3(jag.nextGaussian(), jag.nextGaussian() * 0.5 + 0.2, jag.nextGaussian()).normalize();
				Vec3 at = centre.add(d.scale((0.3 + 1.4 * ease(puff)) * s * Math.max(1, user.getBbWidth())));
				Draw.halo(c.glow(), c.m(), at, c.camera(), 0, (0.4f + 0.5f * puff) * s, 0xE8E8E8, op(150 * (1 - puff)));
			}
			Draw.halo(c.glow(), c.m(), centre, c.camera(), 0, 1.6f * s, 0xFFFFFF, op(200 * (1 - puff)));
		}
		float pop = seg(k, 0.15f, 0.4f);
		float gone = seg(k, 0.85f, 1f);
		if (pop <= 0 || gone >= 1) {
			return;
		}
		// Overshoots, then settles: a bounce.
		float bounce = pop < 1 ? pop * (1 + Mth.sin(pop * Mth.PI * 1.5f) * (1 - pop) * 0.6f) : 1f;
		float size = 0.95f * s * bounce * (1 - gone);
		Vec3 dir = flat(c.toTarget());
		Vec3 sd = side(dir);
		Vec3 front = user.position().add(dir.scale(user.getBbWidth() * 0.6 + 0.9 * s));
		// Half turned to the camera, so its face reads.
		dir = dir.add(flat(c.camera().subtract(front))).normalize();
		sd = side(dir);
		float yaw = (float) Math.toDegrees(Math.atan2(dir.x, dir.z));
		BlockState wool = Blocks.WHITE_WOOL.defaultBlockState();
		box(c, wool, front.add(0, 0.45 * size, 0), 0.8f * size, 0.9f * size, 0.6f * size, yaw);
		Vec3 head = front.add(0, 1.15 * size, 0);
		box(c, wool, head, 0.75f * size, 0.6f * size, 0.6f * size, yaw);
		for (int side = -1; side <= 1; side += 2) {
			box(c, wool, head.add(sd.scale(side * 0.25 * size)).add(0, 0.5 * size, 0), 0.16f * size, 0.45f * size, 0.12f * size, yaw);
		}
		Vec3 face = head.add(dir.scale(0.31 * size)).add(0, 0.05 * size, 0);
		for (int side = -1; side <= 1; side += 2) {
			Draw.sphere(c.buffers().getBuffer(Draw.DARK), c.m(), face.add(sd.scale(side * 0.15 * size)), 0.06f * size, 0x202020, 255, false);
		}
		Draw.halo(c.glow(), c.m(), front.add(0, 0.7 * size, 0), c.camera(), 0, 1.2f * s, 0xFFFFFF, op(160 * (1 - pop)));
	}

	/** Calm Mind: slow ripples spreading level from the user's head, a third eye shining, motes rising in stillness. */
	private static void calmMind(Context c) {
		Entity user = c.move().user();
		float s = c.size();
		float h = user.getBbHeight();
		float r = user.getBbWidth() * 0.7f + 0.5f * s;
		Vec3 base = user.position();
		float fade = c.fade(0.12f, 0.2f);
		Vec3 head = base.add(0, h * 0.85, 0);
		for (int i = 0; i < 4; i++) {
			float p = ((c.t() * 0.025f) + i / 4f) % 1f;
			Draw.ring(c.glow(), c.m(), head, UP, (0.3f + 2.4f * p) * s * Math.max(1, user.getBbWidth()), 0.07f * s, i % 2 == 0 ? c.color() : c.accent(),
					op(200 * fade * (1 - p)));
		}
		Vec3 eye = head.add(0, h * 0.2 + 0.25 * s, 0);
		float pulse = 0.75f + 0.25f * Mth.sin(c.t() * 0.3f);
		twinkle(c, eye, 0.6f * s * pulse, 0xFFFFFF, op(230 * fade));
		Draw.halo(c.glow(), c.m(), eye, c.camera(), 0, 0.9f * s * pulse, c.accent(), op(160 * fade));
		Draw.halo(c.glow(), c.m(), c.userCentre(), c.camera(), r * 0.5f, r * 1.8f, c.color(), op(70 * fade));
		for (int i = 0; i < 10; i++) {
			float p = ((c.t() * 0.02f) + i / 10f) % 1f;
			double angle = i * 2.4;
			Vec3 at = base.add(Math.cos(angle) * r, p * h * 1.6, Math.sin(angle) * r);
			Draw.halo(c.glow(), c.m(), at, c.camera(), 0, 0.12f * s, c.accent(), op(200 * fade * bell(p)));
		}
	}

	/** Nasty Plot: shadows coiling up round the user, red eyes opening over its head and narrowing, a menacing pulse. */
	private static void nastyPlot(Context c) {
		Entity user = c.move().user();
		float s = c.size();
		float h = user.getBbHeight();
		float r = user.getBbWidth() * 0.7f + 0.5f * s;
		Vec3 base = user.position();
		float fade = c.fade(0.1f, 0.2f);
		float climb = Math.min(1, c.k() * 2.5f);
		for (int j = 0; j < 4; j++) {
			Vec3[] pts = new Vec3[20];
			for (int i = 0; i < pts.length; i++) {
				double p = i / 19.0;
				double angle = -c.t() * 0.25 + p * 5 + j * Math.PI / 2;
				double rr = r * (1.25 - 0.5 * p);
				pts[i] = base.add(Math.cos(angle) * rr, p * h * 1.3 * climb, Math.sin(angle) * rr);
			}
			ribbon(c, pts, 0.05f * s, 0.2f * s, c.color(), 60 * fade, 220 * fade);
		}
		Draw.halo(c.glow(), c.m(), c.userCentre(), c.camera(), 0, r * 1.8f, c.color(), op(90 * fade));
		Vec3 at = base.add(0, h + 0.55 * s, 0);
		Vec3[] b = c.screen(at);
		float open = seg(c.k(), 0.15f, 0.35f);
		float squint = 1 - 0.65f * seg(c.k(), 0.55f, 0.7f);
		for (int side = -1; side <= 1; side += 2) {
			Vec3 eye = at.add(b[0].scale(side * 0.3 * s));
			// Slanted, the inner corner lower: a scowl.
			Vec3 u = b[0].scale(side).add(b[1].scale(0.35)).normalize();
			Vec3 v = b[1].subtract(b[0].scale(side * 0.35)).normalize();
			float tall = 0.12f * s * open * squint;
			Vec3 l = eye.subtract(u.scale(0.2 * s));
			Vec3 rr = eye.add(u.scale(0.2 * s));
			Vec3 top = eye.add(v.scale(tall));
			Vec3 bottom = eye.subtract(v.scale(tall * 0.6));
			line(c, l, top, 0.05f * s, c.accent(), 240 * fade * open);
			line(c, top, rr, 0.05f * s, c.accent(), 240 * fade * open);
			line(c, rr, bottom, 0.05f * s, c.accent(), 240 * fade * open);
			line(c, bottom, l, 0.05f * s, c.accent(), 240 * fade * open);
			Draw.halo(c.glow(), c.m(), eye, c.camera(), 0, 0.3f * s * open, c.accent(), op(200 * fade));
			Draw.halo(c.glow(), c.m(), eye, c.camera(), 0, 0.07f * s, 0xFFFFFF, op(240 * fade * open));
		}
		float pulse = seg(c.k(), 0.6f, 0.95f);
		if (pulse > 0 && pulse < 1) {
			Draw.ring(c.glow(), c.m(), base.add(0, 0.07, 0), UP, r + 2.5f * pulse * s, 0.3f * s, c.accent(), op(200 * (1 - pulse)));
		}
	}

	// =====================================================================
	// On the target
	// =====================================================================

	/** Toxic: venom bursting on the target, bubbles swelling and popping all over it, goo running down, two pulses. */
	private static void toxic(Context c) {
		Vec3 at = c.aim();
		Vec3 feet = c.move().aimFeet();
		float s = c.size();
		float w = c.move().targetWidth() * 0.55f + 0.2f;
		float h = c.move().targetHeight();
		float fade = 1 - seg(c.k(), 0.82f, 1f);
		float burst = seg(c.k(), 0, 0.3f);
		if (burst < 1) {
			RandomSource jag = RandomSource.create(c.move().move.hashCode() * 19L);
			for (int i = 0; i < 12; i++) {
				Vec3 d = new Vec3(jag.nextGaussian(), Math.abs(jag.nextGaussian()) * 0.8 + 0.3, jag.nextGaussian()).normalize();
				Vec3 drop = at.add(d.scale((0.3 + 1.6 * burst) * s)).add(0, -1.8 * burst * burst * s, 0);
				Draw.sphere(c.glow(), c.m(), drop, 0.1f * s * (1 - burst), c.color(), 200, true);
			}
			Draw.halo(c.glow(), c.m(), at, c.camera(), 0, (0.6f + 1.4f * burst) * s, c.color(), op(200 * (1 - burst)));
		}
		RandomSource bubbles = RandomSource.create(c.move().move.hashCode() * 23L);
		for (int i = 0; i < 16; i++) {
			double angle = bubbles.nextDouble() * Mth.TWO_PI;
			double y = bubbles.nextDouble() * h * 0.9 + 0.1;
			float start = 0.15f + bubbles.nextFloat() * 0.6f;
			float life = 0.12f + bubbles.nextFloat() * 0.1f;
			float p = (c.k() - start) / life;
			if (p < 0 || p > 1.3f) {
				continue;
			}
			Vec3 spot = feet.add(Math.cos(angle) * w, y, Math.sin(angle) * w);
			if (p <= 1) {
				float r = (0.1f + 0.24f * p) * s;
				Draw.sphere(c.glow(), c.m(), spot, r, c.color(), 140, true);
				Draw.halo(c.glow(), c.m(), spot.add(-r * 0.3, r * 0.4, 0), c.camera(), 0, r * 0.4f, 0xFFFFFF, 160);
			} else {
				float q = (p - 1) / 0.3f;
				Draw.ring(c.glow(), c.m(), spot, c.camera().subtract(spot).normalize(), (0.3f + 0.35f * q) * s, 0.08f * s, c.accent(), op(230 * (1 - q)));
			}
		}
		for (int i = 0; i < 7; i++) {
			double angle = i * 0.9 + 0.3;
			float p = seg(c.k(), 0.1f + i * 0.05f, 0.6f + i * 0.05f);
			if (p <= 0) {
				continue;
			}
			Vec3 top = feet.add(Math.cos(angle) * w * 0.95, h * 0.85, Math.sin(angle) * w * 0.95);
			Vec3 tip = top.add(0, -h * 0.8 * ease(p), 0);
			Draw.streak(c.glow(), c.m(), tip, top, c.camera(), 0.16f * s, c.color(), op(230 * fade));
			Draw.sphere(c.glow(), c.m(), tip, 0.13f * s, c.accent(), op(180 * fade), true);
		}
		// The poison soaking in: a sickly glow over the whole body.
		Draw.halo(c.glow(), c.m(), at, c.camera(), w * 0.5f, (w + 0.9f) * s, c.color(), op(110 * fade * seg(c.k(), 0.05f, 0.25f)));
		for (float pulse : new float[] { 0.45f, 0.7f }) {
			float q = seg(c.k(), pulse, pulse + 0.2f);
			if (q > 0 && q < 1) {
				Draw.halo(c.glow(), c.m(), at, c.camera(), 0, (w + 0.6f) * (1 + q) * s, c.color(), op(160 * (1 - q)));
				Draw.ring(c.glow(), c.m(), feet.add(0, 0.06, 0), UP, (w + 0.2f + 1.5f * q) * s, 0.25f * s, c.accent(), op(200 * (1 - q)));
			}
		}
	}

	/** Taunt: a throbbing anger mark over the target's head and stress lines flickering round it. */
	private static void tauntRage(Context c) {
		Vec3 centre = c.aim();
		Vec3 head = centre.add(0, c.move().targetHeight() * 0.55 + 0.25, 0);
		Vec3[] b = c.screen(head);
		float s = c.size();
		float fade = c.fade(0.08f, 0.25f);
		float pop = ease(seg(c.k(), 0, 0.15f));
		float throb = 1 + 0.22f * Math.max(0, Mth.sin(c.t() * 0.9f));
		Vec3 mark = head.add(b[0].scale(0.45 * s)).add(b[1].scale(0.25 * s));
		float m = 0.42f * s * pop * throb;
		for (int i = 0; i < 4; i++) {
			double angle = Math.PI / 2 * i + Math.PI / 4;
			Vec3 d = b[0].scale(Math.cos(angle)).add(b[1].scale(Math.sin(angle)));
			Vec3 across = b[0].scale(-Math.sin(angle)).add(b[1].scale(Math.cos(angle)));
			Vec3 tip = mark.add(d.scale(m * 0.35));
			for (int edge = -1; edge <= 1; edge += 2) {
				Vec3 end = mark.add(d.scale(m * 1.3)).add(across.scale(edge * m * 0.55));
				line(c, end, tip, 0.09f * s, c.color(), 240 * fade);
				line(c, end, tip, 0.03f * s, 0xFFFFFF, 240 * fade);
			}
		}
		Draw.halo(c.glow(), c.m(), mark, c.camera(), 0, m * 2, c.color(), op(120 * fade));
		RandomSource jag = RandomSource.create((long) (c.t() / 3) * 37);
		float r = c.move().targetWidth() * 0.6f + 0.6f * s;
		Vec3[] around = c.screen(centre);
		for (int i = 0; i < 10; i++) {
			double angle = jag.nextDouble() * Mth.TWO_PI;
			Vec3 d = around[0].scale(Math.cos(angle)).add(around[1].scale(Math.sin(angle)));
			Draw.streak(c.glow(), c.m(), centre.add(d.scale(r)), centre.add(d.scale(r + (0.4 + jag.nextDouble() * 0.6) * s)), c.camera(), 0.05f * s, c.color(),
					op(220 * fade));
		}
		float pulse = (c.t() * 0.06f) % 1f;
		Draw.ring(c.glow(), c.m(), centre, c.camera().subtract(centre).normalize(), r * (1 + pulse), 0.08f * s, c.color(), op(160 * fade * (1 - pulse)));
	}

	// =====================================================================
	// Helpers
	// =====================================================================

	private static String phase(Context c) {
		return c.layer().phase == null ? "hit" : c.layer().phase;
	}

	private static float seg(float k, float from, float to) {
		return Mth.clamp((k - from) / (to - from), 0f, 1f);
	}

	private static float ease(float x) {
		x = Mth.clamp(x, 0f, 1f);
		return x * x * (3 - 2 * x);
	}

	/** 0 at both ends, 1 halfway. */
	private static float bell(float x) {
		return x <= 0 || x >= 1 ? 0 : Mth.sin(x * Mth.PI);
	}

	private static int op(double alpha) {
		return Mth.clamp((int) alpha, 0, 255);
	}

	/** True on the one tick the layer goes past {@code share} of its length. */
	private static boolean crossed(Context c, float share) {
		float at = share * c.length();
		return c.t() >= at && c.t() - 1 < at;
	}

	private static Vec3 flat(Vec3 v) {
		Vec3 f = v.multiply(1, 0, 1);
		return f.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : f.normalize();
	}

	private static Vec3 side(Vec3 dir) {
		Vec3 s = dir.cross(UP);
		return s.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : s.normalize();
	}

	private static Vec3 dirOf(double angle) {
		return new Vec3(Math.cos(angle), 0, Math.sin(angle));
	}

	private static double flatDist(Vec3 a, Vec3 b) {
		double dx = a.x - b.x;
		double dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static Vec3 randomUnit(RandomSource jag) {
		return new Vec3(jag.nextGaussian(), jag.nextGaussian(), jag.nextGaussian()).normalize();
	}

	private static Vec3 bezier(Vec3 a, Vec3 b, Vec3 c, double t) {
		double u = 1 - t;
		return a.scale(u * u).add(b.scale(2 * u * t)).add(c.scale(t * t));
	}

	/** The block under {@code feet}, for ground thrown up; dirt when it isn't a full block (or nothing is there). */
	private static BlockState ground(Vec3 feet) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null) {
			BlockPos pos = BlockPos.containing(feet.x, feet.y - 0.05, feet.z);
			BlockState state = level.getBlockState(pos);
			if (!state.isAir() && state.isCollisionShapeFullBlock(level, pos)) {
				return state;
			}
		}
		return Blocks.DIRT.defaultBlockState();
	}

	/** An even line from {@code a} to {@code b}: two streaks facing each other. */
	private static void line(Context c, Vec3 a, Vec3 b, float width, int color, float alpha) {
		Draw.streak(c.glow(), c.m(), a, b, c.camera(), width, color, op(alpha));
		Draw.streak(c.glow(), c.m(), b, a, c.camera(), width, color, op(alpha));
	}

	/** A four-pointed star of light (a glint): two crossed streaks and a small glow. */
	private static void twinkle(Context c, Vec3 at, float size, int color, int alpha) {
		if (alpha <= 0 || size <= 0) {
			return;
		}
		Vec3[] b = c.screen(at);
		Vec3[] arms = { b[0], b[0].scale(-1), b[1], b[1].scale(-1) };
		for (Vec3 arm : arms) {
			Draw.streak(c.glow(), c.m(), at, at.add(arm.scale(size)), c.camera(), size * 0.12f, color, alpha);
		}
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, size * 0.45f, color, alpha * 3 / 4);
		Draw.halo(c.glow(), c.m(), at, c.camera(), 0, size * 0.18f, 0xFFFFFF, alpha);
	}

	/**
	 * A soft ribbon of light through {@code pts} (smooth, unlike chained streaks), facing the camera: width and alpha
	 * go from the first point's to the last's.
	 */
	private static void ribbon(Context c, Vec3[] pts, float w0, float w1, int color, float a0, float a1) {
		int n = pts.length;
		if (n < 2) {
			return;
		}
		Vec3[] across = new Vec3[n];
		for (int i = 0; i < n; i++) {
			Vec3 tangent = pts[Math.min(i + 1, n - 1)].subtract(pts[Math.max(i - 1, 0)]);
			Vec3 d = tangent.cross(c.camera().subtract(pts[i]));
			across[i] = d.lengthSqr() < 1.0E-10 ? UP : d.normalize();
		}
		VertexConsumer out = c.glow();
		Matrix4f m = c.m();
		for (int i = 0; i < n - 1; i++) {
			float f0 = i / (float) (n - 1);
			float f1 = (i + 1) / (float) (n - 1);
			float wa = Mth.lerp(f0, w0, w1);
			float wb = Mth.lerp(f1, w0, w1);
			int aa = op(Mth.lerp(f0, a0, a1));
			int ab = op(Mth.lerp(f1, a0, a1));
			Vec3 p = pts[i];
			Vec3 q = pts[i + 1];
			Draw.vertex(out, m, p.subtract(across[i].scale(wa)), color, 0);
			Draw.vertex(out, m, p, color, aa);
			Draw.vertex(out, m, q, color, ab);
			Draw.vertex(out, m, q.subtract(across[i + 1].scale(wb)), color, 0);
			Draw.vertex(out, m, p, color, aa);
			Draw.vertex(out, m, p.add(across[i].scale(wa)), color, 0);
			Draw.vertex(out, m, q.add(across[i + 1].scale(wb)), color, 0);
			Draw.vertex(out, m, q, color, ab);
		}
	}

	/**
	 * A pointed crystal of a block: the cube stood on one corner and stretched along that diagonal, so both ends are
	 * points. {@code width} across, {@code length} tip to tip, standing up, then turned by {@code yaw} and leant by
	 * {@code tilt} (degrees).
	 */
	private static void crystal(Context c, BlockState state, Vec3 centre, float width, float length, float yaw, float tilt) {
		if (width <= 0.01f || length <= 0.01f) {
			return;
		}
		PoseStack pose = c.pose();
		pose.pushPose();
		pose.translate(centre.x, centre.y, centre.z);
		pose.mulPose(Axis.YP.rotationDegrees(yaw));
		pose.mulPose(Axis.XP.rotationDegrees(tilt));
		pose.scale(width / 1.63f, length / 1.732f, width / 1.63f);
		// (1, 1, 1) turned onto the vertical: 45° round Y, then 54.74° round Z.
		pose.mulPose(Axis.ZP.rotationDegrees(54.7356f));
		pose.mulPose(Axis.YP.rotationDegrees(45));
		pose.translate(-0.5, -0.5, -0.5);
		Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, c.buffers(), 0xF000F0, OverlayTexture.NO_OVERLAY);
		pose.popPose();
	}

	/** A crystal ({@link #crystal}) along {@code axis}. */
	private static void crystalToward(Context c, BlockState state, Vec3 centre, float width, float length, Vec3 axis) {
		Vec3 d = axis.normalize();
		float tilt = (float) Math.toDegrees(Math.acos(Mth.clamp(d.y, -1, 1)));
		float yaw = (float) Math.toDegrees(Math.atan2(d.x, d.z));
		crystal(c, state, centre, width, length, yaw, tilt);
	}

	/** A block stretched to {@code sx} × {@code sy} × {@code sz} round {@code centre}, turned by {@code yaw}. */
	private static void box(Context c, BlockState state, Vec3 centre, float sx, float sy, float sz, float yaw) {
		if (sx <= 0.01f || sy <= 0.01f || sz <= 0.01f) {
			return;
		}
		PoseStack pose = c.pose();
		pose.pushPose();
		pose.translate(centre.x, centre.y, centre.z);
		pose.mulPose(Axis.YP.rotationDegrees(yaw));
		pose.scale(sx, sy, sz);
		pose.translate(-0.5, -0.5, -0.5);
		Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, c.buffers(), 0xF000F0, OverlayTexture.NO_OVERLAY);
		pose.popPose();
	}
}
