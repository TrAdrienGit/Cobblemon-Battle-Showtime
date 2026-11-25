package com.mystaria.showtime.battle;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.joml.Vector3f;

import com.cobblemon.mod.common.client.net.animation.PlayPosableAnimationHandler;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.mystaria.showtime.api.ShowtimeHooks;
import com.mystaria.showtime.api.Transformation;

/**
 * The battle gimmicks as set pieces (2026-10-06; staged in the world since 2026-11-25), on both clients' scene. While
 * one plays, the camera is taken ({@code CameraMixin}, {@code GameRendererShowtimeMixin}), Cobblemon's battle screen and
 * the HUD are hidden, and a battle that can wait for it does (Phantasmon's host engine, {@link #pauseSeconds}). This
 * class keeps the timeline, the camera, the sounds and the models' colours; {@link SetPieceScene} draws the set in the
 * world, {@link ShowtimeOverlay} only the letterbox and the flashes.
 *
 * <p>Each one starts on the trainer, as in the games: the camera finds the Pokémon's trainer (the stage's trainer
 * nearest to it, and the person standing there), films the gesture, follows the power to the Pokémon, then stays on
 * it. During the most stylised moments the world and every other entity are hidden behind the set's backdrop
 * ({@link #hidesWorld}, {@link #hides}).
 *
 * <ul>
 *   <li><b>Mega Evolution</b>: Key Stone, light link, black silhouettes on a pink burst, cocoon, shatter (the model
 *   switches in the flash), the new form under its emblem. <b>Primal Reversion</b>: the same in Groudon's red /
 *   Kyogre's blue, with Ω / α.</li>
 *   <li><b>Z-Move</b>: Z-Crystal, stream of power, golden blaze under a giant Z with a dolly zoom, release.</li>
 *   <li><b>Terastallization</b>: Tera Orb charged and thrown, crystal star, crystal growing round the Pokémon,
 *   shatter, the Pokémon crystalline under its Tera jewel; from then on it keeps a crystal tint and glows in its Tera
 *   colour until the end of the battle (kept through switches).</li>
 * </ul>
 * Client thread only.
 */
public final class BattleShowtime {

	public enum Kind {
		MEGA(7.2f), PRIMAL(7.2f), Z_MOVE(6.2f), TERA(7.4f);

		final float seconds;

		Kind(float seconds) {
			this.seconds = seconds;
		}
	}

	/** When each set piece's climax happens (model switch, release, shatter), in seconds. */
	static final float MEGA_SHATTER = 4.8f;
	static final float Z_RELEASE = 4.6f;
	static final float TERA_SHATTER = 5.0f;
	/** How long the Mega shell takes to close (from 3.2 s), the Pokémon shrinking inside it meanwhile. */
	static final float MEGA_COVERED = 0.95f;

	/** When the world and the other entities are hidden behind the set's backdrop, per kind: from, to (seconds). */
	private static final float[] MEGA_HIDDEN = { 2.45f, 4.85f };
	private static final float[] Z_HIDDEN = { 2.65f, 4.6f };
	private static final float[] TERA_HIDDEN = { 0.45f, 6.1f };
	/** How far from the stage's trainer position a person may stand and still be taken for the trainer. */
	private static final double TRAINER_REACH = 2.5;

	private static final Random RANDOM = new Random();

	private static Kind kind;
	private static PokemonEntity entity;
	private static Transformation change;
	private static long start = -1;
	/** Tera colour (RGB) of the set piece playing; for Stellar, cycles. */
	private static String teraType;
	private static boolean climaxDone;
	private static final Set<String> cues = new HashSet<>();
	/** The Pokémon's trainer: the person standing there (may be null), where they stand, how tall they are. */
	private static LivingEntity trainer;
	private static Vec3 trainerAt = Vec3.ZERO;
	private static double trainerTall = 1.8;
	/** From the trainer to the Pokémon, flat; and its right. Fixed for the whole set piece. */
	private static Vec3 forward = new Vec3(0, 0, 1);
	private static Vec3 right = new Vec3(-1, 0, 0);

	/** Battle Pokémon uuid → Tera type, for the whole battle (its entity is rebuilt on each switch-in). */
	private static final Map<String, String> teraByPokemon = new HashMap<>();
	/** Entity id → Tera type, for the glow ({@code EntityGlowMixin}) and the floating particles. */
	private static final Map<Integer, String> glowing = new HashMap<>();
	/** Entity id → when a preview's glow goes away (admin preview only). */
	private static final Map<Integer, Long> glowUntil = new HashMap<>();

	private BattleShowtime() {
	}

	// =====================================================================
	// Lifecycle
	// =====================================================================

	/** How long a battle that can wait does for the set piece of this change ({@link Transformation#aspect()}). */
	public static float pauseSeconds(String aspect) {
		return kindOf(aspect).seconds;
	}

	private static Kind kindOf(String aspect) {
		if (Transformation.Z_POWER.equals(aspect)) {
			return Kind.Z_MOVE;
		}
		if (aspect != null && aspect.startsWith(Transformation.TERA_PREFIX)) {
			return Kind.TERA;
		}
		return "primal".equals(aspect) ? Kind.PRIMAL : Kind.MEGA;
	}

	/** A Mega Evolution / Primal Reversion / Z-Move / Terastallization announced by the filmed battle. */
	public static void play(Transformation visual) {
		Kind which = kindOf(visual.aspect());
		if (which == Kind.TERA) {
			teraByPokemon.put(visual.uuid(), visual.teraType());
		}
		var stage = BattleCameraDirector.stage();
		if (stage == null || !(stage.pokemonAt(visual.pnx()) instanceof PokemonEntity target)) {
			return;
		}
		begin(which, target, visual, which == Kind.TERA ? visual.teraType() : null);
	}


	private static void begin(Kind which, PokemonEntity target, Transformation visual, String type) {
		if (playing()) {
			finish();
		}
		kind = which;
		entity = target;
		change = visual;
		teraType = type;
		start = System.currentTimeMillis();
		climaxDone = false;
		cues.clear();
		findTrainer(target);
		// The trainer's gesture (players only: Player Animation Library animates players).
		com.mystaria.showtime.emote.TrainerEmotes.special(trainer, switch (which) {
			case MEGA, PRIMAL -> "special_mega";
			case Z_MOVE -> "special_z";
			case TERA -> "special_tera";
		});
	}

	/**
	 * The trainer of {@code target}: the stage's trainer position nearest to it, and the person (not a Pokémon) nearest
	 * to that within {@link #TRAINER_REACH}. Without a stage position, a point a few blocks behind the Pokémon.
	 */
	private static void findTrainer(PokemonEntity target) {
		Vec3 feet = target.position();
		Vec3 spot = null;
		var stage = BattleCameraDirector.stage();
		if (stage != null) {
			for (boolean local : new boolean[] { true, false }) {
				Vec3 at = stage.trainerPosition(local);
				if (at != null && (spot == null || at.distanceToSqr(feet) < spot.distanceToSqr(feet))) {
					spot = at;
				}
			}
		}
		if (spot == null || spot.distanceToSqr(feet) < 0.25) {
			spot = feet.subtract(Vec3.directionFromRotation(0, target.getYRot()).scale(3.5));
		}
		trainer = null;
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null) {
			Vec3 near = spot;
			trainer = level.getEntitiesOfClass(LivingEntity.class, new AABB(spot, spot).inflate(TRAINER_REACH),
					e -> !(e instanceof PokemonEntity) && e.isAlive())
					.stream().min(java.util.Comparator.comparingDouble(e -> e.position().distanceToSqr(near))).orElse(null);
		}
		trainerAt = trainer != null ? trainer.position() : spot;
		trainerTall = trainer != null ? trainer.getBbHeight() : 1.8;
		Vec3 flat = new Vec3(feet.x - trainerAt.x, 0, feet.z - trainerAt.z);
		forward = flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, target.getYRot()).scale(-1) : flat.normalize();
		right = new Vec3(-forward.z, 0, forward.x);
	}

	public static boolean playing() {
		return start >= 0 && entity != null;
	}

	/** Seconds since the set piece began. */
	static float time() {
		return start < 0 ? 0f : (System.currentTimeMillis() - start) / 1000f;
	}

	static Kind kind() {
		return kind;
	}

	static PokemonEntity entity() {
		return entity;
	}

	static String species() {
		return change == null ? (entity == null ? "" : entity.getPokemon().getSpecies().getResourceIdentifier().getPath()) : change.species();
	}

	/** RGB of the Tera type being shown ({@link #teraColor}). */
	static int color() {
		return teraColor(teraType);
	}

	/** A Pokémon was sent out: if it terastallized earlier in this battle, it glows again. */
	public static void onSentOut(PokemonEntity sent, java.util.UUID battlePokemonUuid) {
		String type = battlePokemonUuid == null ? null : teraByPokemon.get(battlePokemonUuid.toString());
		if (type != null) {
			glowing.put(sent.getId(), type);
		}
	}

	/** Battle over / scene cleared. */
	public static void clear() {
		if (playing()) {
			finish();
		}
		teraByPokemon.clear();
		// Battles seen from afar keep their own Tera glows (Phantasmon's field viewers).
		glowing.keySet().removeIf(id -> !ShowtimeHooks.keepsGlow(id));
		glowUntil.clear();
	}

	private static void finish() {
		if (!climaxDone) {
			climax();
		}
		start = -1;
		entity = null;
		change = null;
		kind = null;
		trainer = null;
	}

	// =====================================================================
	// What the scene and the mixins read
	// =====================================================================

	/**
	 * The colour a living entity's model is drawn with this frame (ARGB), or -1 for its own ({@code
	 * LivingEntityRendererTintMixin}): the Mega silhouettes in black, the Pokémon turning to crystal, a terastallized
	 * Pokémon's lasting crystal tint.
	 */
	public static int modelColor(Entity candidate) {
		if (playing()) {
			float t = time();
			boolean actor = candidate == entity || (trainer != null && candidate == trainer);
			if (actor && (kind == Kind.MEGA || kind == Kind.PRIMAL) && t >= 2.42f && t < 3.1f) {
				return 0xFF0C0A10;
			}
			if (candidate == entity && kind == Kind.TERA && t >= 3.0f) {
				// Turning to crystal inside the growth, then crystalline.
				float k = t < TERA_SHATTER ? smooth((t - 3.0f) / 1.6f) : 1f;
				return 0xFF000000 | mix(0xFFFFFF, mix(color(), 0xFFFFFF, 0.45f), k);
			}
		}
		if (glowing.isEmpty()) {
			return -1;
		}
		String type = glowing.get(candidate.getId());
		if (type == null) {
			return -1;
		}
		float shimmer = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 400.0 + candidate.getId());
		return 0xFF000000 | mix(teraColor(type), 0xFFFFFF, 0.45f + 0.15f * shimmer);
	}

	/** Whether the world's blocks are hidden this frame: the set's backdrop stands in for them. */
	public static boolean hidesWorld() {
		if (!playing()) {
			return false;
		}
		float[] hidden = switch (kind) {
			case MEGA, PRIMAL -> MEGA_HIDDEN;
			case Z_MOVE -> Z_HIDDEN;
			case TERA -> TERA_HIDDEN;
		};
		float t = time();
		return t >= hidden[0] && t < hidden[1];
	}

	/** Whether {@code candidate} is hidden this frame: everyone but the trainer and the Pokémon, while the world is. */
	public static boolean hides(Entity candidate) {
		return candidate != entity && candidate != trainer && hidesWorld();
	}

	/**
	 * How big {@code candidate} is drawn this frame, about its middle ({@code LivingEntityRendererTintMixin}): the Pokémon
	 * curling up inside the closing Mega shell, so that none of it sticks out (2026-11-25: hiding it at once was seen
	 * as a pop), then bursting back out at the shatter.
	 */
	public static float modelScale(Entity candidate) {
		if (candidate != entity || !playing() || (kind != Kind.MEGA && kind != Kind.PRIMAL)) {
			return 1f;
		}
		float t = time();
		if (t < 3.2f) {
			return 1f;
		}
		if (t < MEGA_SHATTER) {
			return Mth.lerp(smooth((t - 3.2f) / MEGA_COVERED), 1f, 0.55f);
		}
		float k = Mth.clamp((t - MEGA_SHATTER) / 0.35f, 0f, 1f);
		// Out with a little overshoot.
		float back = 1f + 2.2f * (float) Math.pow(k - 1, 3) + 1.2f * (float) Math.pow(k - 1, 2);
		return Mth.lerp(back, 0.55f, 1f);
	}

	static Vec3 center(float pt) {
		return entity.getPosition(pt).add(0, entity.getBbHeight() / 2.0, 0);
	}

	static Vec3 feet(float pt) {
		return entity.getPosition(pt);
	}

	static Vec3 trainerFeet() {
		return trainer != null && trainer.isAlive() ? trainer.position() : trainerAt;
	}

	static double trainerHeight() {
		return trainerTall;
	}

	static Vec3 forward() {
		return forward;
	}

	static Vec3 right() {
		return right;
	}

	/** The trainer's raised wrist (the Key Stone). */
	static Vec3 hand() {
		return trainerFeet().add(0, trainerTall * 0.68, 0).add(forward.scale(0.42)).add(right.scale(0.18));
	}

	/** Before the trainer's chest (the Z-Crystal). */
	static Vec3 chest() {
		return trainerFeet().add(0, trainerTall * 0.62, 0).add(forward.scale(0.2));
	}

	/** The Tera Orb at {@code t}: held up above the trainer's head, thrown in an arc, hanging over the Pokémon. */
	static Vec3 orb(float t) {
		Vec3 held = trainerFeet().add(0, trainerTall + 0.55 + 0.05 * Math.sin(t * 4), 0).add(forward.scale(0.15));
		Vec3 over = entity.position().add(0, Math.max(1.0, radius()) * 2 + 1.0, 0);
		if (t < 1.7f) {
			return held;
		}
		if (t >= 2.5f) {
			return over;
		}
		float k = smooth((t - 1.7f) / 0.8f);
		return held.lerp(over, k).add(0, Math.sin(k * Math.PI) * (1.5 + held.distanceTo(over) * 0.15), 0);
	}

	// =====================================================================
	// Glow (EntityGlowMixin)
	// =====================================================================

	/** The Tera glow's colour for this entity this frame, or -1: a slow shimmer toward white. */
	public static int glowColor(Entity candidate) {
		if (glowing.isEmpty()) {
			return -1;
		}
		String type = glowing.get(candidate.getId());
		if (type == null) {
			return -1;
		}
		float shimmer = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 260.0);
		return mix(teraColor(type), 0xFFFFFF, 0.15f + 0.3f * shimmer);
	}

	// =====================================================================
	// Tick: particles and sounds
	// =====================================================================

	public static void tick() {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		tickGlowing(level);
		if (!playing()) {
			return;
		}
		if (entity.isRemoved()) {
			finish();
			return;
		}
		float t = time();
		if (t >= kind.seconds) {
			finish();
			return;
		}
		switch (kind) {
			case MEGA, PRIMAL -> tickMega(level, t);
			case Z_MOVE -> tickZMove(level, t);
			case TERA -> tickTera(level, t);
		}
	}

	private static void tickGlowing(ClientLevel level) {
		long now = System.currentTimeMillis();
		Iterator<Map.Entry<Integer, String>> it = glowing.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, String> entry = it.next();
			Entity glowingEntity = level.getEntity(entry.getKey());
			Long until = glowUntil.get(entry.getKey());
			if (glowingEntity == null || glowingEntity.isRemoved() || (until != null && now > until)) {
				glowUntil.remove(entry.getKey());
				it.remove();
				continue;
			}
			int rgb = teraColor(entry.getValue());
			double radius = Math.max(0.5, glowingEntity.getBbWidth() * 0.7);
			double height = glowingEntity.getBbHeight();
			// Motes drifting up round the Pokémon, now and then a sparkle or a crystal chip.
			for (int i = 0; i < 2; i++) {
				double angle = RANDOM.nextDouble() * Math.PI * 2;
				double r = radius * (0.6 + 0.6 * RANDOM.nextDouble());
				level.addParticle(dust(rgb, 0.7f + RANDOM.nextFloat() * 0.6f),
						glowingEntity.getX() + Math.cos(angle) * r, glowingEntity.getY() + RANDOM.nextDouble() * height,
						glowingEntity.getZ() + Math.sin(angle) * r, 0, 0.03, 0);
			}
			if (RANDOM.nextInt(5) == 0) {
				double angle = RANDOM.nextDouble() * Math.PI * 2;
				level.addParticle(ParticleTypes.END_ROD, glowingEntity.getX() + Math.cos(angle) * radius,
						glowingEntity.getY() + height * (0.3 + 0.7 * RANDOM.nextDouble()), glowingEntity.getZ() + Math.sin(angle) * radius,
						0, 0.015, 0);
			}
			if (RANDOM.nextInt(8) == 0) {
				level.addParticle(shard(entry.getValue()), glowingEntity.getX(), glowingEntity.getY() + height * 0.8, glowingEntity.getZ(),
						0, 0.1, 0);
			}
		}
	}

	private static void tickMega(ClientLevel level, float t) {
		boolean primal = kind == Kind.PRIMAL;
		boolean kyogre = "kyogre".equals(species());
		Vec3 c = center();
		double r = radius();
		cue("stone", t >= 0.15f, SoundEvents.AMETHYST_BLOCK_CHIME, primal ? 0.7f : 1.2f, 1f);
		cue("hum1", t >= 0.45f, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 1f);
		cue("hum2", t >= 0.85f, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1f);
		cue("link", t >= 1.3f, SoundEvents.BEACON_ACTIVATE, primal ? 0.6f : 1.3f, 1f);
		cue("burst", t >= 2.4f, SoundEvents.BEACON_POWER_SELECT, primal ? 0.7f : 1.6f, 1f);
		cue("cocoon", t >= 3.1f, SoundEvents.BEACON_AMBIENT, primal ? 0.8f : 1.5f, 1f);
		cue("swell", t >= 4.0f, SoundEvents.BEACON_POWER_SELECT, primal ? 0.5f : 0.8f, 1f);
		cue("crack1", t >= 4.3f, SoundEvents.AMETHYST_BLOCK_BREAK, 0.6f, 1f);
		cue("crack2", t >= 4.6f, SoundEvents.AMETHYST_BLOCK_BREAK, 0.8f, 1f);
		cue("cry", t >= MEGA_SHATTER + 0.6f, null, 1f, 1f);
		if (t >= 0.2f && t < 1.3f && RANDOM.nextInt(3) == 0) {
			Vec3 hand = hand();
			level.addParticle(ParticleTypes.END_ROD, hand.x, hand.y, hand.z, (RANDOM.nextDouble() - 0.5) * 0.06, 0.03,
					(RANDOM.nextDouble() - 0.5) * 0.06);
		}
		if (!climaxDone && t >= MEGA_SHATTER) {
			climax();
		}
		if (t >= MEGA_SHATTER + 0.1f && t < MEGA_SHATTER + 1.8f) {
			for (int i = 0; i < 3; i++) {
				double angle = RANDOM.nextDouble() * Math.PI * 2;
				int color = primal ? (kyogre ? 0x7FE0FF : 0xFFB030) : (RANDOM.nextBoolean() ? 0xFFE070 : rainbow(RANDOM.nextFloat()));
				level.addParticle(dust(color, 1.2f), c.x + Math.cos(angle) * r, c.y - r + RANDOM.nextDouble() * r * 2, c.z + Math.sin(angle) * r,
						0, 0.05, 0);
			}
		}
	}

	private static void tickZMove(ClientLevel level, float t) {
		Vec3 feet = entity.position();
		double r = radius();
		cue("start", t >= 0f, SoundEvents.BEACON_POWER_SELECT, 1.5f, 1f);
		cue("charge1", t >= 0.3f, SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.8f, 1f);
		cue("charge2", t >= 0.9f, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.0f, 1f);
		cue("stream", t >= 1.5f, SoundEvents.BEACON_ACTIVATE, 1.6f, 1f);
		cue("blaze", t >= 2.4f, SoundEvents.BLAZE_SHOOT, 0.6f, 0.8f);
		cue("charge3", t >= 3.0f, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.3f, 1f);
		cue("spin", t >= 3.9f, SoundEvents.ELYTRA_FLYING, 1.8f, 0.6f);
		if (t >= 2.4f && t < Z_RELEASE) {
			// Embers rising round the Pokémon.
			for (int i = 0; i < 3; i++) {
				double angle = RANDOM.nextDouble() * Math.PI * 2;
				double rr = r * (0.8 + 0.5 * RANDOM.nextDouble());
				level.addParticle(dust(RANDOM.nextBoolean() ? 0xFFC020 : 0xFFF080, 1.1f),
						feet.x + Math.cos(angle) * rr, feet.y + RANDOM.nextDouble() * r, feet.z + Math.sin(angle) * rr, 0, 0.2, 0);
			}
			if (RANDOM.nextInt(3) == 0) {
				level.addParticle(ParticleTypes.ELECTRIC_SPARK, feet.x, feet.y + r, feet.z, (RANDOM.nextDouble() - 0.5) * 0.4, 0.2,
						(RANDOM.nextDouble() - 0.5) * 0.4);
			}
		}
		if (!climaxDone && t >= Z_RELEASE) {
			climax();
		}
	}

	private static void tickTera(ClientLevel level, float t) {
		Vec3 c = center();
		double r = radius();
		cue("start", t >= 0.2f, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1f);
		cue("charge", t >= 0.6f, SoundEvents.BEACON_AMBIENT, 1.8f, 1f);
		cue("ring", t >= 1.1f, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4f, 1f);
		cue("throw", t >= 1.7f, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.4f, 0.8f);
		cue("star", t >= 2.5f, SoundEvents.AMETHYST_BLOCK_BREAK, 1.6f, 1f);
		cue("twinkle", t >= 2.55f, SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.2f, 0.7f);
		cue("grow1", t >= 3.0f, SoundEvents.AMETHYST_CLUSTER_PLACE, 0.8f, 1f);
		cue("grow2", t >= 3.4f, SoundEvents.AMETHYST_CLUSTER_PLACE, 1.0f, 1f);
		cue("grow3", t >= 3.8f, SoundEvents.AMETHYST_CLUSTER_PLACE, 1.2f, 1f);
		cue("grow4", t >= 4.2f, SoundEvents.AMETHYST_CLUSTER_PLACE, 1.5f, 1f);
		cue("crack", t >= 4.6f, SoundEvents.AMETHYST_BLOCK_BREAK, 1.4f, 1f);
		cue("cry", t >= TERA_SHATTER + 0.6f, null, 1f, 1f);
		if (t >= 3.0f && t < TERA_SHATTER && RANDOM.nextInt(2) == 0) {
			// Chips falling off the growing crystal.
			double angle = RANDOM.nextDouble() * Math.PI * 2;
			level.addParticle(shard(teraType), c.x + Math.cos(angle) * r * 1.2, c.y + r * 0.5, c.z + Math.sin(angle) * r * 1.2, 0, 0.05, 0);
		}
		if (!climaxDone && t >= TERA_SHATTER) {
			climax();
		}
	}

	/** The climax: the model switches (Mega / Primal), the Z-Power is released, the crystal shatters. */
	private static void climax() {
		climaxDone = true;
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || entity == null || entity.isRemoved()) {
			return;
		}
		climax(level, kind, entity, change, teraType, "kyogre".equals(species()), BattleCinematic::play);
	}

	/**
	 * A gimmick in a battle seen from afar (Phantasmon's field viewers): no camera, no overlay, no
	 * pause — only the climax around the Pokémon (flash, burst, model switch, Tera glow), heard from where it stands.
	 */
	public static void playInWorld(Transformation visual, PokemonEntity target) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || target == null || target.isRemoved()) {
			return;
		}
		Kind which = kindOf(visual.aspect());
		Vec3 at = target.position();
		climax(level, which, target, visual, which == Kind.TERA ? visual.teraType() : null, "kyogre".equals(visual.species()),
				(sound, pitch, volume) -> level.playLocalSound(at.x, at.y, at.z, sound, net.minecraft.sounds.SoundSource.NEUTRAL,
						volume, pitch, false));
	}

	/** Same Tera glow as after the set piece, for a Pokémon sent out again in a battle seen from afar. */
	public static void glow(PokemonEntity target, String type) {
		if (target != null && type != null) {
			glowing.put(target.getId(), type);
		}
	}

	private interface Sound {
		void play(SoundEvent sound, float pitch, float volume);
	}

	private static void climax(ClientLevel level, Kind which, PokemonEntity target, Transformation visual, String type,
			boolean kyogre, Sound sound) {
		Vec3 c = target.position().add(0, target.getBbHeight() / 2.0, 0);
		double r = Math.max(0.5, Math.max(target.getBbHeight(), target.getBbWidth()) / 2.0);
		level.addParticle(ParticleTypes.FLASH, c.x, c.y, c.z, 0, 0, 0);
		switch (which) {
			case MEGA, PRIMAL -> {
				if (visual != null) {
					Set<String> aspects = new HashSet<>(target.getEntityData().get(PokemonEntity.Companion.getASPECTS()));
					aspects.add(visual.aspect());
					target.getEntityData().set(PokemonEntity.Companion.getASPECTS(), aspects);
				}
				boolean primal = which == Kind.PRIMAL;
				burst(level, c, r, 70, ParticleTypes.END_ROD, 0.35);
				burst(level, c, r, 40, ParticleTypes.FIREWORK, 0.45);
				for (int i = 0; i < 40; i++) {
					Vec3 v = randomUnit().scale(0.5);
					int color = primal ? (kyogre ? 0x50A0FF : 0xFF5020) : rainbow(i / 40f);
					level.addParticle(dust(color, 2.5f), c.x, c.y, c.z, v.x, v.y, v.z);
				}
				sound.play(SoundEvents.GLASS_BREAK, 0.8f, 1f);
				sound.play(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.9f, 1f);
				sound.play(SoundEvents.TOTEM_USE, primal ? 0.7f : 1.2f, 0.6f);
			}
			case Z_MOVE -> {
				burst(level, c, r, 80, ParticleTypes.END_ROD, 0.6);
				for (int i = 0; i < 50; i++) {
					Vec3 v = randomUnit().scale(0.6);
					level.addParticle(dust(i % 2 == 0 ? 0xFFC020 : 0xFFFFFF, 2.0f), c.x, c.y, c.z, v.x, v.y, v.z);
				}
				Vec3 feet = target.position();
				for (int i = 0; i < 48; i++) {
					double angle = i * Math.PI * 2 / 48;
					double vx = Math.cos(angle) * 0.6;
					double vz = Math.sin(angle) * 0.6;
					level.addParticle(ParticleTypes.CLOUD, feet.x, feet.y + 0.1, feet.z, vx, 0.01, vz);
					level.addParticle(dust(0xFFD040, 2.0f), feet.x, feet.y + 0.2, feet.z, vx * 1.3, 0.02, vz * 1.3);
				}
				sound.play(SoundEvents.WARDEN_SONIC_BOOM, 1.3f, 0.7f);
				sound.play(SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.7f, 1f);
				sound.play(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.6f, 0.4f);
			}
			case TERA -> {
				for (int i = 0; i < 60; i++) {
					Vec3 v = randomUnit().scale(0.45);
					level.addParticle(shard(type), c.x + v.x * r, c.y + v.y * r, c.z + v.z * r, v.x, v.y + 0.1, v.z);
				}
				for (int i = 0; i < 40; i++) {
					Vec3 v = randomUnit().scale(0.4);
					level.addParticle(dust(teraColor(type), 2.2f), c.x, c.y, c.z, v.x, v.y, v.z);
				}
				burst(level, c, r, 30, ParticleTypes.END_ROD, 0.3);
				sound.play(SoundEvents.GLASS_BREAK, 1.0f, 1f);
				sound.play(SoundEvents.AMETHYST_BLOCK_BREAK, 0.6f, 1f);
				sound.play(SoundEvents.BEACON_ACTIVATE, 1.6f, 0.8f);
				glowing.put(target.getId(), type);
				if (visual == null) {
					glowUntil.put(target.getId(), System.currentTimeMillis() + 12_000);
				}
			}
		}
	}

	private static void cue(String id, boolean due, SoundEvent sound, float pitch, float volume) {
		if (!due || !cues.add(id)) {
			return;
		}
		if (sound == null) {
			// The cry.
			PlayPosableAnimationHandler.INSTANCE.handle(new PlayPosableAnimationPacket(entity.getId(), Set.of("cry"), List.of()),
					Minecraft.getInstance());
			return;
		}
		BattleCinematic.play(sound, pitch, volume);
	}

	private static void burst(ClientLevel level, Vec3 c, double r, int count, ParticleOptions particle, double speed) {
		for (int i = 0; i < count; i++) {
			Vec3 v = randomUnit().scale(speed * (0.6 + 0.6 * RANDOM.nextDouble()));
			level.addParticle(particle, c.x, c.y, c.z, v.x, v.y, v.z);
		}
	}

	// =====================================================================
	// Camera (CameraMixin, GameRendererShowtimeMixin)
	// =====================================================================

	/**
	 * The camera this frame while a set piece plays, else null. {@code base} is where it would be otherwise (the
	 * director's shot, or the player's own view): the set piece blends in from it and back to it.
	 */
	public static BattleCinematic.CameraPose cameraPose(Camera camera, BattleCinematic.CameraPose base, float partialTick) {
		if (!playing()) {
			return null;
		}
		float t = time();
		BattleCinematic.CameraPose shot = switch (kind) {
			case MEGA, PRIMAL -> megaShot(t, partialTick);
			case Z_MOVE -> zShot(t, partialTick);
			case TERA -> teraShot(t, partialTick);
		};
		float in = kind == Kind.Z_MOVE ? 0.35f : 0.45f;
		float out = 0.6f;
		if (t < in) {
			return blend(base, shot, smooth(t / in));
		}
		if (t > kind.seconds - out) {
			return blend(shot, base, smooth((t - (kind.seconds - out)) / out));
		}
		return shot;
	}

	/** Field of view multiplier: the Z-Move's dolly zoom, the punches at each climax. */
	public static double fovScale() {
		if (!playing()) {
			return 1.0;
		}
		float t = time();
		return switch (kind) {
			case Z_MOVE -> {
				if (t < 2.4f) {
					yield 1.0;
				}
				if (t < 4.0f) {
					yield Mth.lerp(smooth((t - 2.4f) / 1.6f), 1.0f, 1.35f);
				}
				if (t < Z_RELEASE) {
					yield Mth.lerp(smooth((t - 4.0f) / 0.6f), 1.35f, 1.15f);
				}
				yield punch(t - Z_RELEASE, 0.72f, Math.max(0f, 1f - (t - (kind.seconds - 0.5f)) / 0.5f) * 0.15f + 1f);
			}
			case MEGA, PRIMAL -> t < MEGA_SHATTER ? 1.0 : punch(t - MEGA_SHATTER, 0.85f, 1f);
			case TERA -> t < TERA_SHATTER ? 1.0 : punch(t - TERA_SHATTER, 0.85f, 1f);
		};
	}

	/** From {@code low} back to {@code rest} in half a second, overshooting a little. */
	private static double punch(float since, float low, float rest) {
		if (since >= 0.6f) {
			return rest;
		}
		float k = since / 0.6f;
		float spring = 1f - (float) Math.exp(-6 * k) * (float) Math.cos(k * 9);
		return Mth.lerp(spring, low, rest);
	}

	private static BattleCinematic.CameraPose megaShot(float t, float pt) {
		double r = radius();
		if (t < 1.3f) {
			// The Key Stone: close on the trainer's wrist, pushing in.
			return closeUp(hand(), smooth(t / 1.3f), 2.3, 1.5, 0.75, -0.15, t);
		}
		if (t < 2.4f) {
			// The light link: from the side, trainer and Pokémon in one frame.
			return sideShot(smooth((t - 1.3f) / 1.1f), 0.95, 0.85, t, 0f);
		}
		if (t < 3.1f) {
			// The silhouettes: the same frame, closer.
			return sideShot(smooth((t - 2.4f) / 0.7f), 0.8, 0.72, t, 0f);
		}
		if (t < MEGA_SHATTER) {
			float k = (t - 3.1f) / (MEGA_SHATTER - 3.1f);
			float shake = t > 4.3f ? 0.03f + 0.12f * (t - 4.3f) / 0.5f : 0f;
			return orbit(pt, -30f + 50f * smooth(k), 1.05 - 0.3 * k, r * 0.3, 0, shake, t);
		}
		float k = Math.min(1f, (t - MEGA_SHATTER) / 1.6f);
		float shake = 0.25f * (float) Math.exp(-(t - MEGA_SHATTER) * 6);
		// Pulled back by the blast, then looking up at the new form.
		return orbit(pt, 20f - 20f * smooth(k), Mth.lerp(easeOut(Math.min(1f, (t - MEGA_SHATTER) / 0.4f)), 0.7f, 1.35f), -r * 0.25,
				r * 0.25, shake, t);
	}

	private static BattleCinematic.CameraPose zShot(float t, float pt) {
		double r = radius();
		if (t < 1.5f) {
			// The Z-Crystal before the trainer: low, from three quarters, pushing in.
			return closeUp(chest(), smooth(t / 1.5f), 2.6, 1.8, -0.9, -0.35, t);
		}
		if (t < 2.4f) {
			return sideShot(smooth((t - 1.5f) / 0.9f), 1.0, 0.9, t, 0f);
		}
		float z = t - 2.4f;
		if (z < 1.6f) {
			float k = smooth(z / 1.6f);
			return orbit(pt, Mth.lerp(k, 140f, 100f), Mth.lerp(k, 2.2f, 0.9f), r * 0.2, 0, 0f, t);
		}
		if (t < Z_RELEASE) {
			float k = smooth((z - 1.6f) / 0.6f);
			return orbit(pt, Mth.lerp(k, 100f, -20f), 0.9, Mth.lerp(k, r * 0.1, r * 0.4), 0, 0.03f, t);
		}
		float k = easeOut(Math.min(1f, (t - Z_RELEASE) / 0.6f));
		return orbit(pt, -20f, Mth.lerp(k, 0.9f, 1.6f), r * 0.4, 0, 0.3f * (float) Math.exp(-(t - Z_RELEASE) * 5), t);
	}

	private static BattleCinematic.CameraPose teraShot(float t, float pt) {
		double r = radius();
		if (t < 1.7f) {
			// The Tera Orb held up: low, looking up past the trainer's face at it.
			Vec3 look = trainerFeet().add(0, trainerTall * 0.85, 0).lerp(orb(t), 0.6);
			return closeUp(look, smooth(t / 1.7f), 2.6, 2.0, 0.6, -0.7, t);
		}
		if (t < 2.5f) {
			// The throw: from the side, following the orb.
			BattleCinematic.CameraPose side = sideShot(smooth((t - 1.7f) / 0.8f), 1.05, 0.95, t, 0f);
			return lookAt(side.position(), side.position().lerp(orb(t), 0.5).lerp(orb(t), 0.5));
		}
		if (t < 3.0f) {
			// The star over the Pokémon: looking up at it.
			float k = smooth((t - 2.5f) / 0.5f);
			return orbit(pt, -10f + 10f * k, 1.25, -r * 0.2, Mth.lerp(k, r * 1.6, r * 1.0), 0f, t);
		}
		if (t < TERA_SHATTER) {
			float k = (t - 3.0f) / (TERA_SHATTER - 3.0f);
			float shake = t > 4.6f ? 0.04f + 0.1f * (t - 4.6f) / 0.4f : 0f;
			return orbit(pt, Mth.lerp(smooth(k), -35f, 30f), Mth.lerp(k, 1.3f, 0.95f), r * 0.6, r * 0.2, shake, t);
		}
		float k = easeOut(Math.min(1f, (t - TERA_SHATTER) / 0.4f));
		float slow = Math.min(1f, (t - TERA_SHATTER) / 2.0f);
		return orbit(pt, 30f + 12f * slow, Mth.lerp(k, 0.75f, 1.3f), Mth.lerp(k, r * 0.5, -r * 0.1), r * 0.3,
				0.2f * (float) Math.exp(-(t - TERA_SHATTER) * 6), t);
	}

	/**
	 * Close on the trainer: in front of them (between them and their Pokémon), {@code side} blocks to their right,
	 * {@code height} above {@code look}, coming from {@code far} to {@code near} blocks as {@code k} goes to 1.
	 */
	private static BattleCinematic.CameraPose closeUp(Vec3 look, float k, double far, double near, double side, double height, float t) {
		double d = Mth.lerp(k, far, near);
		Vec3 position = look.add(forward.scale(d)).add(right.scale(side)).add(0, height + 0.05 * Math.sin(t * 1.3), 0);
		return lookAt(position, look);
	}

	/**
	 * From the side of the line between the trainer and the Pokémon, both in frame, drifting along it; {@code far} and
	 * {@code near} scale the distance as {@code k} goes to 1.
	 */
	private static BattleCinematic.CameraPose sideShot(float k, double far, double near, float t, float shake) {
		Vec3 feet = entity.position();
		Vec3 trainerFeet = trainerFeet();
		Vec3 mid = trainerFeet.lerp(feet, 0.5).add(0, Math.max(trainerTall * 0.6, radius()), 0);
		double half = trainerFeet.distanceTo(feet) * 0.5 + radius() + 0.6;
		double d = Mth.clamp(half * 1.55, 3.2, 18.0) * Mth.lerp(k, far, near);
		Vec3 position = mid.add(right.scale(d)).add(forward.scale(Mth.lerp(k, -0.6, 0.6))).add(0, 0.6 + d * 0.08, 0);
		if (shake > 0f) {
			position = position.add(Math.sin(t * 71) * shake, Math.sin(t * 53 + 1) * shake, Math.cos(t * 67) * shake);
		}
		return lookAt(position, mid);
	}

	private static BattleCinematic.CameraPose lookAt(Vec3 position, Vec3 target) {
		Vec3 d = target.subtract(position);
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		return new BattleCinematic.CameraPose(position, yaw, pitch, true);
	}

	/**
	 * In front of the Pokémon ({@code angle} 0 = right in front, degrees round it), {@code distance} times its
	 * framing distance away, {@code height} above its centre, looking at its centre raised by {@code lookUp}.
	 */
	private static BattleCinematic.CameraPose orbit(float pt, float angle, double distance, double height, double lookUp, float shake, float t) {
		Vec3 c = entity.getPosition(pt).add(0, entity.getBbHeight() / 2.0, 0);
		Vec3 forward = Vec3.directionFromRotation(0, entity.getYRot() + angle);
		double frame = Mth.clamp(radius() * 2.6 + 1.6, 2.6, 14.0) * distance;
		Vec3 position = c.add(forward.scale(frame)).add(0, height, 0);
		if (shake > 0f) {
			double s = shake * Math.max(1.0, radius());
			position = position.add(Math.sin(t * 71) * s, Math.sin(t * 53 + 1) * s, Math.cos(t * 67) * s);
		}
		Vec3 d = c.add(0, lookUp, 0).subtract(position);
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		return new BattleCinematic.CameraPose(position, yaw, pitch, true);
	}

	private static BattleCinematic.CameraPose blend(BattleCinematic.CameraPose from, BattleCinematic.CameraPose to, float k) {
		return new BattleCinematic.CameraPose(from.position().lerp(to.position(), k), Mth.rotLerp(k, from.yaw(), to.yaw()),
				Mth.lerp(k, from.pitch(), to.pitch()), true);
	}

	// =====================================================================
	// Helpers
	// =====================================================================

	private static Vec3 center() {
		return entity.position().add(0, entity.getBbHeight() / 2.0, 0);
	}

	/** Half the Pokémon's larger dimension, at least half a block. */
	static double radius() {
		return Math.max(0.5, Math.max(entity.getBbHeight(), entity.getBbWidth()) / 2.0);
	}

	private static Vec3 randomUnit() {
		double z = RANDOM.nextDouble() * 2 - 1;
		double a = RANDOM.nextDouble() * Math.PI * 2;
		double s = Math.sqrt(1 - z * z);
		return new Vec3(Math.cos(a) * s, z, Math.sin(a) * s);
	}

	static float smooth(float k) {
		k = Mth.clamp(k, 0f, 1f);
		return k * k * (3 - 2 * k);
	}

	static float easeOut(float k) {
		k = Mth.clamp(k, 0f, 1f);
		return 1f - (1f - k) * (1f - k) * (1f - k);
	}

	private static DustParticleOptions dust(int rgb, float size) {
		return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f), size);
	}

	/** A crystal chip: a stained glass fragment of the Tera type's colour. */
	private static BlockParticleOption shard(String type) {
		return new BlockParticleOption(ParticleTypes.BLOCK, glass(type).defaultBlockState());
	}

	private static Block glass(String type) {
		return switch (type == null ? "" : type) {
			case "fire" -> Blocks.ORANGE_STAINED_GLASS;
			case "water", "dragon" -> Blocks.BLUE_STAINED_GLASS;
			case "grass", "bug" -> Blocks.LIME_STAINED_GLASS;
			case "electric" -> Blocks.YELLOW_STAINED_GLASS;
			case "ice", "flying" -> Blocks.LIGHT_BLUE_STAINED_GLASS;
			case "fighting" -> Blocks.RED_STAINED_GLASS;
			case "poison", "ghost" -> Blocks.PURPLE_STAINED_GLASS;
			case "ground", "rock" -> Blocks.BROWN_STAINED_GLASS;
			case "psychic", "fairy" -> Blocks.PINK_STAINED_GLASS;
			case "dark" -> Blocks.BLACK_STAINED_GLASS;
			case "steel" -> Blocks.LIGHT_GRAY_STAINED_GLASS;
			default -> Blocks.WHITE_STAINED_GLASS;
		};
	}

	/** Each Tera type's colour (the games' type colours); Stellar cycles through the rainbow. */
	public static int teraColor(String type) {
		return switch (type == null ? "" : type) {
			case "normal" -> 0xC6C6A7;
			case "fire" -> 0xFF7F24;
			case "water" -> 0x4F8BFF;
			case "grass" -> 0x6CD640;
			case "electric" -> 0xFFD81F;
			case "ice" -> 0x8FE8E8;
			case "fighting" -> 0xE0302A;
			case "poison" -> 0xB044B8;
			case "ground" -> 0xE8C060;
			case "flying" -> 0xA890FF;
			case "psychic" -> 0xFF5590;
			case "bug" -> 0xA8C820;
			case "rock" -> 0xC8A840;
			case "ghost" -> 0x8060B8;
			case "dragon" -> 0x7038FF;
			case "dark" -> 0x6E5848;
			case "steel" -> 0xB8B8D8;
			case "fairy" -> 0xFF9AD8;
			default -> rainbow((System.currentTimeMillis() % 3000) / 3000f);
		};
	}

	/** A bright colour along the rainbow, {@code k} in [0, 1). */
	static int rainbow(float k) {
		return Mth.hsvToRgb(k - (float) Math.floor(k), 0.75f, 1f) & 0xFFFFFF;
	}

	static int mix(int a, int b, float k) {
		int r = Math.round(Mth.lerp(k, (a >> 16) & 0xFF, (b >> 16) & 0xFF));
		int g = Math.round(Mth.lerp(k, (a >> 8) & 0xFF, (b >> 8) & 0xFF));
		int bl = Math.round(Mth.lerp(k, a & 0xFF, b & 0xFF));
		return (r << 16) | (g << 8) | bl;
	}
}
