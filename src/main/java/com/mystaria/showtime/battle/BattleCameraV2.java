package com.mystaria.showtime.battle;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import com.mystaria.showtime.api.BattleStage;

/**
 * The battle camera, second version (2026-11-09), after Pokémon Battle Revolution's: low shots at the Pokémon's height,
 * each one drifting slowly (dolly, orbit, push-in), joined by clean cuts rather than long flights across the field. A
 * move is two shots: three-quarter behind the attacker with its target beyond, then a cut onto the target when the hit
 * lands, held while it reacts. {@link BattleCameraDirector} keeps the battle's state and forwards here when the player
 * chose this camera; the first version stays as it was.
 *
 * <p>Three things keep it smooth whatever the world around the field:
 * <ul>
 *   <li><b>Shots are checked before they start.</b> Each candidate is sampled along its length; a camera inside blocks
 *   or with blocks between it and its subjects is set aside for a mirrored, closer or higher variant.</li>
 *   <li><b>A spring arm while it plays.</b> If something comes between the camera and what it looks at, the camera
 *   moves in front of it, from the subject's side, instead of seeing through or jumping.</li>
 *   <li><b>Everything is damped.</b> The field's anchors follow the Pokémon slowly (a lunge or a bob doesn't shake the
 *   frame), and the camera's eye and aim are critically damped springs, snapped only on a cut.</li>
 * </ul>
 * Client thread only.
 */
final class BattleCameraV2 {

	/** What a shot asks for at a moment: where the eye is, what it aims at, what must stay in sight. */
	record Frame(Vec3 eye, Vec3 look, List<Vec3> subjects) {
	}

	interface Shot {
		Frame frame(Field field, float t);
	}

	/** The battlefield this frame, from damped anchors. */
	record Field(Vec3 local, Vec3 opponent, Vec3 center, Vec3 axis, Vec3 side, double gap, Entity localMon, Entity opponentMon) {
	}

	private static final float CHOOSING_MIN = 5.5f;
	private static final float CHOOSING_MAX = 8.5f;
	private static final float WINDUP_MAX = 1.6f;
	private static final float WINDUP_MIN = 0.55f;
	private static final float IMPACT_HOLD = 2.4f;
	private static final float SELF_HOLD = 3.0f;
	private static final float FOCUS_HOLD = 2.8f;
	private static final float KNOCK_OUT_HOLD = 3.2f;
	/** Spring times, in seconds: the eye, the aim, the field's anchors. */
	private static final double EYE_SMOOTH = 0.16;
	private static final double LOOK_SMOOTH = 0.12;
	private static final double ANCHOR_TAU = 0.6;

	private static final Random RANDOM = new Random();

	private static Shot shot;
	private static long shotStart;
	private static float shotLength;
	private static long holdUntil;
	private static int lastChoosing = -1;
	private static boolean cut = true;

	/** A move being filmed: its target, and when its hit landed (0: not yet). */
	private static Entity actionTarget;
	private static String actionTargetPnx;
	private static long actionStart;
	private static long hitAt;
	private static boolean impactShown = true;

	private static Vec3 anchorLocal;
	private static Vec3 anchorOpponent;
	private static Vec3 eye;
	private static Vec3 look;
	private static Vec3 eyeVelocity = Vec3.ZERO;
	private static Vec3 lookVelocity = Vec3.ZERO;
	private static long lastFrame;

	private BattleCameraV2() {
	}

	// =====================================================================
	// Events (from BattleCameraDirector)
	// =====================================================================

	static void reset() {
		shot = null;
		holdUntil = 0;
		anchorLocal = null;
		anchorOpponent = null;
		eye = null;
		look = null;
		actionTarget = null;
		impactShown = true;
	}

	static void onChoosing() {
		holdUntil = Math.min(holdUntil, System.currentTimeMillis() + 600);
	}

	static void onAction(BattleStage stage, String user, String target) {
		long now = System.currentTimeMillis();
		if (target == null || target.equals(user)) {
			impactShown = true;
			actionTarget = null;
			cutTo(pick(List.of(() -> selfShot(stage, user, 1), () -> selfShot(stage, user, -1))), SELF_HOLD, now);
			return;
		}
		actionTargetPnx = target;
		actionTarget = stage.pokemonAt(target);
		actionStart = now;
		hitAt = 0;
		impactShown = false;
		cutTo(pick(List.of(() -> windup(stage, user, target, 1), () -> windup(stage, user, target, -1),
				() -> windupWide(stage, user, target, 1), () -> windupWide(stage, user, target, -1))), WINDUP_MAX + IMPACT_HOLD, now);
	}

	/** A move's hit landed on {@code entity} (Cobblemon's impact cue): the cut onto the target can come. */
	static void onHit(Entity entity) {
		if (!impactShown && entity != null && entity == actionTarget && hitAt == 0) {
			hitAt = System.currentTimeMillis();
		}
	}

	static void onFocus(BattleStage stage, String pnx) {
		impactShown = true;
		cutTo(pick(List.of(() -> portrait(() -> stage.pokemonAt(pnx), 1, 1.0), () -> portrait(() -> stage.pokemonAt(pnx), -1, 1.0),
				() -> portrait(() -> stage.pokemonAt(pnx), 1, 0.75))), FOCUS_HOLD, System.currentTimeMillis());
	}

	static void onKnockOut(Entity entity) {
		impactShown = true;
		cutTo(pick(List.of(() -> knockOut(entity, 1), () -> knockOut(entity, -1))), KNOCK_OUT_HOLD, System.currentTimeMillis());
	}

	static void onThrow(Entity entity, float hold) {
		impactShown = true;
		cutTo(pick(List.of(() -> portrait(() -> entity, 1, 1.1), () -> portrait(() -> entity, -1, 1.1))), hold, System.currentTimeMillis());
	}

	// =====================================================================
	// Camera
	// =====================================================================

	static BattleCinematic.CameraPose cameraPose(BattleStage stage, float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || stage == null) {
			return null;
		}
		long nanos = System.nanoTime();
		double dt = lastFrame == 0 ? 0 : Math.min(0.1, (nanos - lastFrame) / 1.0E9);
		lastFrame = nanos;
		Field field = field(stage, partialTick, dt);
		if (field == null) {
			return null;
		}
		currentField = field;
		long now = System.currentTimeMillis();
		// The move's second shot: when the hit lands (or a while after the move began, if no cue came).
		if (!impactShown) {
			long since = now - actionStart;
			boolean hit = hitAt != 0 && since >= WINDUP_MIN * 1000;
			if (hit || since >= WINDUP_MAX * 1000) {
				impactShown = true;
				Entity target = actionTarget != null && !actionTarget.isRemoved() ? actionTarget : stage.pokemonAt(actionTargetPnx);
				if (target != null) {
					cutTo(pick(List.of(() -> impact(target, 1), () -> impact(target, -1), () -> impactWide(target, 1),
							() -> impactWide(target, -1))), IMPACT_HOLD, now);
				}
			}
		}
		if (shot == null || (now >= holdUntil && (now - shotStart) / 1000f >= shotLength)) {
			nextChoosingShot(now);
		}
		Frame frame = shot.frame(field, (now - shotStart) / 1000f);
		Vec3 wantedLook = frame.look();
		Vec3 wantedEye = springArm(level, field, frame.eye(), wantedLook);
		if (cut || eye == null) {
			eye = wantedEye;
			look = wantedLook;
			eyeVelocity = Vec3.ZERO;
			lookVelocity = Vec3.ZERO;
			cut = false;
		} else {
			Vec3[] e = smoothDamp(eye, wantedEye, eyeVelocity, EYE_SMOOTH, dt);
			eye = e[0];
			eyeVelocity = e[1];
			Vec3[] l = smoothDamp(look, wantedLook, lookVelocity, LOOK_SMOOTH, dt);
			look = l[0];
			lookVelocity = l[1];
		}
		return lookAt(eye, look);
	}

	private static Field currentField;

	// =====================================================================
	// Choosing shots
	// =====================================================================

	private static void nextChoosingShot(long now) {
		int kind;
		do {
			kind = RANDOM.nextInt(5);
		} while (kind == lastChoosing);
		lastChoosing = kind;
		int s = RANDOM.nextBoolean() ? 1 : -1;
		List<Supplier<Shot>> options = switch (kind) {
			case 0 -> List.of(() -> lateralDolly(s), () -> lateralDolly(-s));
			case 1 -> {
				float start = RANDOM.nextFloat() * 360f;
				yield List.of(() -> lowOrbit(start, s), () -> lowOrbit(start + 90, s), () -> lowOrbit(start + 180, s), () -> lowOrbit(start + 270, s));
			}
			case 2 -> List.of(() -> overShoulder(true, s), () -> overShoulder(true, -s), () -> overShoulder(false, s));
			case 3 -> {
				boolean local = RANDOM.nextBoolean();
				yield List.of(() -> portraitOf(local, s), () -> portraitOf(local, -s), () -> portraitOf(!local, s));
			}
			default -> List.of(() -> establishing(s), () -> establishing(-s));
		};
		switchTo(pick(options), CHOOSING_MIN + RANDOM.nextFloat() * (CHOOSING_MAX - CHOOSING_MIN), now);
	}

	/** Low beside the field, sliding slowly along it, both Pokémon in frame. */
	private static Shot lateralDolly(int sideSign) {
		float drift = RANDOM.nextBoolean() ? 1 : -1;
		return (f, t) -> {
			double distance = 3.6 + f.gap() * 0.62;
			double along = drift * (-0.25 * f.gap() + t * 0.22);
			Vec3 eye = f.center().add(f.side().scale(sideSign * distance)).add(f.axis().scale(along)).add(0, 1.1, 0);
			return new Frame(eye, f.center().add(0, 0.8, 0), subjects(f));
		};
	}

	/** A slow circle round the field, low. */
	private static Shot lowOrbit(float startDegrees, int direction) {
		return (f, t) -> {
			double radius = 3.4 + f.gap() * 0.7;
			double angle = Math.toRadians(startDegrees + direction * 4.5 * t);
			Vec3 eye = f.center().add(Math.cos(angle) * radius, 1.3 + f.gap() * 0.06, Math.sin(angle) * radius);
			return new Frame(eye, f.center().add(0, 0.7, 0), subjects(f));
		};
	}

	/** Behind one side's Pokémon, a little to the side, on the other: a slow push-in. */
	private static Shot overShoulder(boolean localSide, int sideSign) {
		return (f, t) -> {
			Vec3 from = localSide ? f.local() : f.opponent();
			Vec3 to = localSide ? f.opponent() : f.local();
			Entity mon = localSide ? f.localMon() : f.opponentMon();
			double size = size(mon);
			Vec3 forward = horizontal(to.subtract(from));
			Vec3 right = new Vec3(-forward.z, 0, forward.x);
			double back = 2.6 + size * 1.1 - Math.min(0.8, t * 0.1);
			Vec3 eye = from.subtract(forward.scale(back)).add(right.scale(sideSign * (1.1 + size * 0.45))).add(0, 0.9 + size * 0.55, 0);
			return new Frame(eye, to.add(0, 0.7, 0), List.of(to.add(0, 0.6, 0)));
		};
	}

	private static Shot portraitOf(boolean localSide, int sideSign) {
		return (f, t) -> portrait(() -> localSide ? f.localMon() : f.opponentMon(), sideSign, 1.0).frame(f, t);
	}

	/** Wide, a little higher, from a corner: the whole field, slowly pushing in. */
	private static Shot establishing(int sideSign) {
		return (f, t) -> {
			double distance = (5.0 + f.gap() * 0.9) * (1.0 - Math.min(0.18, t * 0.025));
			Vec3 eye = f.center().add(f.side().scale(sideSign * distance * 0.8)).add(f.axis().scale(-distance * 0.6))
					.add(0, 2.6 + f.gap() * 0.12, 0);
			return new Frame(eye, f.center().add(0, 0.6, 0), subjects(f));
		};
	}

	// =====================================================================
	// Action shots
	// =====================================================================

	/** Three-quarter behind the attacker, low, its target beyond: a slow push toward it. */
	private static Shot windup(BattleStage stage, String userPnx, String targetPnx, int sideSign) {
		return (f, t) -> {
			Entity user = stage.pokemonAt(userPnx);
			Entity target = stage.pokemonAt(targetPnx);
			if (user == null || target == null) {
				return fallback(f);
			}
			Vec3 from = user.position();
			Vec3 to = target.position();
			double size = size(user);
			Vec3 forward = horizontal(to.subtract(from));
			Vec3 right = new Vec3(-forward.z, 0, forward.x);
			double back = 2.1 + size * 1.0 - Math.min(0.5, t * 0.35);
			Vec3 eye = from.subtract(forward.scale(back)).add(right.scale(sideSign * (1.2 + size * 0.4))).add(0, 0.5 + size * 0.5, 0);
			Vec3 aim = from.add(0, user.getBbHeight() * 0.55, 0).lerp(to.add(0, target.getBbHeight() * 0.5, 0), 0.55);
			return new Frame(eye, aim, List.of(from.add(0, user.getBbHeight() * 0.5, 0), to.add(0, target.getBbHeight() * 0.5, 0)));
		};
	}

	/** The same moment from the side, both Pokémon across the frame: when the close one is blocked. */
	private static Shot windupWide(BattleStage stage, String userPnx, String targetPnx, int sideSign) {
		return (f, t) -> {
			Entity user = stage.pokemonAt(userPnx);
			Entity target = stage.pokemonAt(targetPnx);
			if (user == null || target == null) {
				return fallback(f);
			}
			Vec3 from = user.position();
			Vec3 to = target.position();
			Vec3 mid = from.lerp(to, 0.4);
			Vec3 forward = horizontal(to.subtract(from));
			Vec3 right = new Vec3(-forward.z, 0, forward.x);
			double distance = 3.0 + Math.sqrt(from.distanceToSqr(to)) * 0.55;
			Vec3 eye = mid.add(right.scale(sideSign * distance)).add(forward.scale(-0.8 + t * 0.2)).add(0, 1.2, 0);
			return new Frame(eye, mid.add(0, 0.8, 0), List.of(from.add(0, 0.6, 0), to.add(0, 0.6, 0)));
		};
	}

	/** Three-quarter front of the target, low, drifting sideways while it takes the hit. */
	private static Shot impact(Entity target, int sideSign) {
		return (f, t) -> {
			if (target.isRemoved()) {
				return fallback(f);
			}
			Vec3 at = target.position();
			Vec3 facing = at.distanceToSqr(f.local()) < at.distanceToSqr(f.opponent()) ? f.opponent() : f.local();
			double size = size(target);
			Vec3 forward = horizontal(facing.subtract(at));
			Vec3 right = new Vec3(-forward.z, 0, forward.x);
			double distance = 2.2 + size * 1.5;
			Vec3 eye = at.add(forward.scale(distance)).add(right.scale(sideSign * (distance * 0.5 + t * 0.25))).add(0, 0.4 + size * 0.45, 0);
			Vec3 aim = at.add(0, target.getBbHeight() * 0.5, 0);
			return new Frame(eye, aim, List.of(aim));
		};
	}

	private static Shot impactWide(Entity target, int sideSign) {
		return (f, t) -> {
			if (target.isRemoved()) {
				return fallback(f);
			}
			Vec3 at = target.position();
			double size = size(target);
			Vec3 right = f.side();
			double distance = 3.2 + size * 1.8;
			Vec3 eye = at.add(right.scale(sideSign * distance)).add(f.axis().scale(t * 0.2)).add(0, 1.0 + size * 0.5, 0);
			Vec3 aim = at.add(0, target.getBbHeight() * 0.5, 0);
			return new Frame(eye, aim, List.of(aim));
		};
	}

	/** A move on itself: from the front, a slow push-in. */
	private static Shot selfShot(BattleStage stage, String pnx, int sideSign) {
		return portrait(() -> stage.pokemonAt(pnx), sideSign, 0.85);
	}

	/** Three-quarter front, low, a slow sideways drift. */
	private static Shot portrait(Supplier<Entity> who, int sideSign, double scale) {
		return (f, t) -> {
			Entity entity = who.get();
			if (entity == null || entity.isRemoved()) {
				return fallback(f);
			}
			Vec3 at = entity.position();
			Vec3 facing = at.distanceToSqr(f.local()) < at.distanceToSqr(f.opponent()) ? f.opponent() : f.local();
			double size = size(entity);
			Vec3 forward = horizontal(facing.subtract(at));
			Vec3 right = new Vec3(-forward.z, 0, forward.x);
			double distance = (2.4 + size * 1.5) * scale * (1.0 - Math.min(0.12, t * 0.03));
			Vec3 eye = at.add(forward.scale(distance)).add(right.scale(sideSign * (distance * 0.45 + t * 0.12)))
					.add(0, 0.35 + size * 0.45, 0);
			Vec3 aim = at.add(0, entity.getBbHeight() * 0.55, 0);
			return new Frame(eye, aim, List.of(aim));
		};
	}

	/** A knock-out: low, close, slowly pulling back. */
	private static Shot knockOut(Entity entity, int sideSign) {
		return (f, t) -> {
			Vec3 at = entity.position();
			Vec3 facing = at.distanceToSqr(f.local()) < at.distanceToSqr(f.opponent()) ? f.opponent() : f.local();
			double size = size(entity);
			Vec3 forward = horizontal(facing.subtract(at));
			Vec3 right = new Vec3(-forward.z, 0, forward.x);
			double distance = (1.9 + size * 1.3) * (1.0 + Math.min(0.3, t * 0.09));
			Vec3 eye = at.add(forward.scale(distance)).add(right.scale(sideSign * distance * 0.6)).add(0, 0.3 + size * 0.3, 0);
			Vec3 aim = at.add(0, entity.getBbHeight() * 0.4, 0);
			return new Frame(eye, aim, List.of(aim));
		};
	}

	private static Frame fallback(Field f) {
		Vec3 eye = f.center().add(f.side().scale(4.5 + f.gap() * 0.6)).add(0, 2.2, 0);
		return new Frame(eye, f.center().add(0, 0.6, 0), subjects(f));
	}

	// =====================================================================
	// Picking a clear shot, keeping it clear
	// =====================================================================

	/**
	 * The first option whose camera stays out of blocks with its subjects in sight, sampled along its length; failing
	 * that, the one that does best. Options are tried as given, then a closer and a higher take of each.
	 */
	private static Shot pick(List<Supplier<Shot>> options) {
		ClientLevel level = Minecraft.getInstance().level;
		Field field = currentField;
		List<Shot> candidates = new ArrayList<>();
		for (Supplier<Shot> option : options) {
			candidates.add(option.get());
		}
		for (Supplier<Shot> option : options) {
			candidates.add(closer(option.get(), 0.7));
			candidates.add(higher(option.get(), 1.4));
		}
		if (level == null || field == null) {
			return candidates.get(0);
		}
		Shot best = candidates.get(0);
		int bestScore = Integer.MAX_VALUE;
		for (Shot candidate : candidates) {
			int score = score(level, field, candidate);
			if (score == 0) {
				return candidate;
			}
			if (score < bestScore) {
				bestScore = score;
				best = candidate;
			}
		}
		return best;
	}

	/**
	 * People around the field (trainers, players watching, NPCs; not the Pokémon): the camera must not stand in them
	 * or film through them (2026-11-09: a trainer's body filled half a shot).
	 */
	private static List<net.minecraft.world.phys.AABB> obstacles(ClientLevel level, Field field) {
		List<net.minecraft.world.phys.AABB> boxes = new ArrayList<>();
		net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(field.center(), field.center()).inflate(24 + field.gap());
		for (Entity entity : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, area,
				each -> !(each instanceof com.cobblemon.mod.common.entity.pokemon.PokemonEntity) && !each.isInvisible())) {
			boxes.add(entity.getBoundingBox().inflate(0.35));
		}
		return boxes;
	}

	private static int score(ClientLevel level, Field field, Shot shot) {
		List<net.minecraft.world.phys.AABB> people = obstacles(level, field);
		int score = 0;
		for (float t : new float[] {0f, 2f, 5f}) {
			Frame frame;
			try {
				frame = shot.frame(field, t);
			} catch (RuntimeException ex) {
				return 1000;
			}
			if (inside(level, frame.eye())) {
				score += 4;
			}
			for (net.minecraft.world.phys.AABB box : people) {
				if (box.inflate(0.4).contains(frame.eye())) {
					score += 4;
				}
			}
			for (Vec3 subject : frame.subjects()) {
				if (blocked(level, frame.eye(), subject)) {
					score += 2;
				}
				for (net.minecraft.world.phys.AABB box : people) {
					if (!box.contains(subject) && box.clip(frame.eye(), subject).isPresent()) {
						score += 2;
					}
				}
			}
		}
		return score;
	}

	/** The same shot with its eye pulled toward what it looks at. */
	private static Shot closer(Shot base, double factor) {
		return (f, t) -> {
			Frame frame = base.frame(f, t);
			return new Frame(frame.look().add(frame.eye().subtract(frame.look()).scale(factor)), frame.look(), frame.subjects());
		};
	}

	/** The same shot raised, looking a little down. */
	private static Shot higher(Shot base, double lift) {
		return (f, t) -> {
			Frame frame = base.frame(f, t);
			return new Frame(frame.eye().add(0, lift, 0), frame.look(), frame.subjects());
		};
	}

	/** Something between the eye and what it looks at: the eye comes in front of it, on the subject's side. */
	private static Vec3 springArm(ClientLevel level, Field field, Vec3 wanted, Vec3 look) {
		Vec3 toward = wanted.subtract(look);
		double length = toward.length();
		if (length < 1.0E-3) {
			return wanted;
		}
		double free = length;
		HitResult hit = level.clip(new ClipContext(look, wanted, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
				CollisionContext.empty()));
		if (hit.getType() != HitResult.Type.MISS) {
			free = hit.getLocation().distanceTo(look);
		}
		// People too: a trainer between the subject and the camera, or the camera inside one.
		for (net.minecraft.world.phys.AABB box : obstacles(level, field)) {
			if (box.contains(look)) {
				continue;
			}
			var through = box.clip(look, wanted);
			if (through.isPresent()) {
				free = Math.min(free, through.get().distanceTo(look));
			} else if (box.contains(wanted)) {
				free = Math.min(free, length * 0.6);
			}
		}
		if (free >= length) {
			return wanted;
		}
		double reach = Math.max(0.6, free - 0.35);
		return look.add(toward.scale(Math.min(1.0, reach / length)));
	}

	private static boolean blocked(ClientLevel level, Vec3 from, Vec3 to) {
		return level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()))
				.getType() != HitResult.Type.MISS;
	}

	/** The eye or a point around it inside a block (the near plane would show its inside). */
	private static boolean inside(ClientLevel level, Vec3 eye) {
		for (double dx = -0.25; dx <= 0.25; dx += 0.5) {
			for (double dz = -0.25; dz <= 0.25; dz += 0.5) {
				Vec3 point = eye.add(dx, 0, dz);
				BlockPos pos = BlockPos.containing(point);
				if (!level.getBlockState(pos).getVisualShape(level, pos, CollisionContext.empty()).isEmpty()) {
					return true;
				}
			}
		}
		return false;
	}

	// =====================================================================
	// Field, springs, helpers
	// =====================================================================

	/** The two active Pokémon, through anchors that follow them slowly; a switch (a far jump) snaps them. */
	private static Field field(BattleStage stage, float partialTick, double dt) {
		Entity localMon = stage.activePokemon(true);
		Entity opponentMon = stage.activePokemon(false);
		Vec3 local = localMon != null ? localMon.getPosition(partialTick) : stage.trainerPosition(true);
		Vec3 opponent = opponentMon != null ? opponentMon.getPosition(partialTick) : stage.trainerPosition(false);
		if (local == null || opponent == null) {
			return null;
		}
		anchorLocal = follow(anchorLocal, local, dt);
		anchorOpponent = follow(anchorOpponent, opponent, dt);
		Vec3 axis = horizontal(anchorOpponent.subtract(anchorLocal));
		Vec3 side = new Vec3(-axis.z, 0, axis.x);
		return new Field(anchorLocal, anchorOpponent, anchorLocal.add(anchorOpponent).scale(0.5), axis, side,
				Math.sqrt(anchorLocal.distanceToSqr(anchorOpponent)), localMon, opponentMon);
	}

	private static Vec3 follow(Vec3 anchor, Vec3 target, double dt) {
		if (anchor == null || anchor.distanceToSqr(target) > 16) {
			return target;
		}
		return anchor.lerp(target, 1 - Math.exp(-dt / ANCHOR_TAU));
	}

	/** A critically damped spring (as game engines' SmoothDamp): returns the new value and velocity. */
	private static Vec3[] smoothDamp(Vec3 current, Vec3 target, Vec3 velocity, double smoothTime, double dt) {
		if (dt <= 0) {
			return new Vec3[] {current, velocity};
		}
		double omega = 2.0 / smoothTime;
		double x = omega * dt;
		double exp = 1.0 / (1.0 + x + 0.48 * x * x + 0.235 * x * x * x);
		Vec3 change = current.subtract(target);
		Vec3 temp = velocity.add(change.scale(omega)).scale(dt);
		Vec3 newVelocity = velocity.subtract(temp.scale(omega)).scale(exp);
		Vec3 value = target.add(change.add(temp).scale(exp));
		return new Vec3[] {value, newVelocity};
	}

	private static List<Vec3> subjects(Field f) {
		return List.of(f.local().add(0, 0.6, 0), f.opponent().add(0, 0.6, 0));
	}

	private static double size(Entity entity) {
		return entity == null ? 1.0 : Mth.clamp(Math.max(entity.getBbHeight(), entity.getBbWidth()), 0.4, 4.0);
	}

	private static void cutTo(Shot next, float hold, long now) {
		switchTo(next, hold, now);
		holdUntil = now + (long) (hold * 1000);
	}

	private static void switchTo(Shot next, float length, long now) {
		shot = next;
		shotStart = now;
		shotLength = length;
		cut = true;
	}

	private static BattleCinematic.CameraPose lookAt(Vec3 position, Vec3 target) {
		Vec3 d = target.subtract(position);
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		return new BattleCinematic.CameraPose(position, yaw, pitch, true);
	}

	private static Vec3 horizontal(Vec3 vector) {
		Vec3 flat = new Vec3(vector.x, 0, vector.z);
		return flat.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : flat.normalize();
	}
}
