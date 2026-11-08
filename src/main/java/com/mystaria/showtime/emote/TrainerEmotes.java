package com.mystaria.showtime.emote;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import com.mystaria.showtime.api.ShowtimeHooks;

/**
 * The trainers' emotes (plan-showtime.md, phase E1, Adrien 2026-10-30): during a battle each player trainer holds a
 * battle guard (one foot forward, knees bent, fists up, ready to spring), and acts the battle's moments — sending a
 * Pokémon out, calling it back (a switch is both), ordering an attack, winning, losing — then goes back to the guard.
 *
 * <p>Played on this client only, on every trainer it sees (the opponent and Phantasmon's Hub avatars included), from
 * the battle events it already receives: nothing is sent anywhere. The animations ship in Showtime
 * ({@code assets/cobblemon_battle_showtime/player_animations/}), so every player has the same ones. They run on
 * playerAnimator (KosmX), which Emotecraft brings; without it the trainers simply don't move. Client thread only.
 */
public final class TrainerEmotes {

	private static final Logger LOG = LoggerFactory.getLogger(TrainerEmotes.class);
	/** A preview of the looping guard stops after 5 s. */
	private static final int PREVIEW_LOOP_TICKS = 100;

	/** The moments a trainer acts; the guard loops between the others. */
	public enum Moment {
		GUARD, SEND_OUT, RECALL, ATTACK, VICTORY, DEFEAT;

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		boolean loops() {
			return this == GUARD;
		}
	}

	private static final String NAMESPACE = "cobblemon_battle_showtime";

	/**
	 * Each moment's default animation. A moment's variants are every animation named {@code <moment>_<style>}
	 * ({@code send_out_spin}...): Showtime's own and any a resource pack adds. A trainer's animation profile (phase
	 * E2) picks one per moment: the local player's from Showtime's settings, the others' from
	 * {@link ShowtimeHooks#trainerAnimation} (Phantasmon, phase E3).
	 */
	/**
	 * Where a throw starts when the ball is already leaving (it flies for 0.5 s, whatever the trainer does): most of
	 * the wind-up is skipped so that the arm comes through with the ball. The fade from the guard covers the jump.
	 */
	private static final Map<String, Integer> THROW_START = Map.of("send_out_overhand", 9, "send_out_underhand", 9,
			"send_out_spin", 8);

	private static final Map<Moment, String> DEFAULTS = Map.of(
			Moment.GUARD, "guard_ready",
			Moment.SEND_OUT, "send_out_overhand",
			Moment.RECALL, "recall_reach",
			Moment.ATTACK, "attack_point",
			Moment.VICTORY, "victory_fists",
			Moment.DEFEAT, "defeat_slump");

	private static final class Trainer {
		final Deque<Moment> queue = new ArrayDeque<>();
		/** The other side's trainers (players or NPCs), the first one found is faced. */
		List<UUID> opponents = List.of();
		Moment playing;
		/** Ticks left before stopping (a preview of the guard), or -1. */
		int stopIn = -1;
		/** The battle's uuid for this trainer (a Hub avatar's entity may have another). */
		UUID uuid;
		/** The profile screen's preview: this animation, replayed whenever it ends, until stopped. */
		String looped;
	}

	private static final EmotePlayer PLAYER = create();
	private static final Map<UUID, Trainer> TRAINERS = new HashMap<>();
	private static final Set<String> MISSING = new HashSet<>();
	/** A battle is on: trainers go back to the guard between moments; else they stop once their moment is over. */
	private static boolean inBattle;

	private TrainerEmotes() {
	}

	private static EmotePlayer create() {
		if (!FabricLoader.getInstance().isModLoaded("playeranimator")) {
			LOG.info("playerAnimator not installed (Emotecraft brings it): the trainers' emotes are off");
			return null;
		}
		try {
			// By name: the class touches playerAnimator, loaded only when it is there.
			return (EmotePlayer) Class.forName("com.mystaria.showtime.emote.PlayerAnimatorEmotes").getDeclaredConstructor().newInstance();
		} catch (ReflectiveOperationException | LinkageError ex) {
			LOG.warn("playerAnimator found but unusable: the trainers' emotes are off", ex);
			return null;
		}
	}

	/** {@code moment}'s variants, default first then by name. */
	public static List<String> variants(Moment moment) {
		List<String> list = new java.util.ArrayList<>();
		list.add(DEFAULTS.get(moment));
		if (PLAYER != null) {
			PLAYER.names(NAMESPACE).stream().filter(name -> name.startsWith(moment.id() + "_") && !list.contains(name))
					.sorted().forEach(list::add);
		}
		return list;
	}

	/** The animation {@code trainer} plays for {@code moment}: its profile's choice when it exists, else the default. */
	public static String animationFor(UUID trainer, Moment moment) {
		Minecraft mc = Minecraft.getInstance();
		String chosen = mc.player != null && mc.player.getUUID().equals(trainer)
				? com.mystaria.showtime.config.ShowtimeConfig.trainerAnimation(moment.id())
				: ShowtimeHooks.trainerAnimation(trainer, moment.id());
		if (chosen != null && chosen.startsWith(moment.id() + "_") && PLAYER != null && PLAYER.names(NAMESPACE).contains(chosen)) {
			return chosen;
		}
		return DEFAULTS.get(moment);
	}

	/** The profile screen: the local player plays {@code animation} over and over until {@link #stopPreview}. */
	public static void previewLoop(String animation) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || PLAYER == null) {
			return;
		}
		Trainer state = TRAINERS.computeIfAbsent(mc.player.getUUID(), ignored -> new Trainer());
		state.queue.clear();
		state.stopIn = -1;
		state.looped = animation;
		state.playing = null;
		PLAYER.play(mc.player, ResourceLocation.fromNamespaceAndPath(NAMESPACE, animation));
	}

	/** The profile screen closed: the local player stops (unless in a battle, where the guard goes on). */
	public static void stopPreview() {
		Minecraft mc = Minecraft.getInstance();
		Trainer state = mc.player == null ? null : TRAINERS.get(mc.player.getUUID());
		if (state == null || state.looped == null) {
			return;
		}
		state.looped = null;
		if (!inBattle || state.opponents.isEmpty()) {
			PLAYER.stop(mc.player);
			TRAINERS.remove(mc.player.getUUID());
		}
	}

	/** Whether trainers can be animated here (playerAnimator installed). */
	public static boolean available() {
		return PLAYER != null;
	}

	/**
	 * A battle starts: these player trainers (each with the other side's trainers, to face) send their first Pokémon
	 * out, then hold the guard.
	 */
	public static void start(Map<UUID, List<UUID>> trainers) {
		stopAll();
		inBattle = true;
		trainers.forEach((uuid, opponents) -> {
			Trainer state = TRAINERS.computeIfAbsent(uuid, ignored -> new Trainer());
			state.opponents = List.copyOf(opponents);
		});
		ShowtimeHooks.trainersStarting(trainers.keySet());
		LOG.debug("Trainer emotes: battle with {} player trainer(s) {}", trainers.size(), trainers.keySet());
	}

	/**
	 * {@code LivingEntityRendererFacingMixin}: the body yaw a trainer of the battle is drawn with — turned to its
	 * opponent, whatever the player looks at (the animation keeps the head straight: eyes in the eyes) — or
	 * {@code fallback} if it isn't one, or the opponent isn't in sight.
	 */
	public static float facingYaw(Entity entity, float fallback) {
		if (PLAYER == null || !inBattle || !(entity instanceof Player)) {
			return fallback;
		}
		Trainer state = TRAINERS.get(entity.getUUID());
		if (state == null || state.opponents.isEmpty()) {
			return fallback;
		}
		Minecraft mc = Minecraft.getInstance();
		// Only the trainer in the world: the intro's stand-ins share its UUID but are drawn at their own angle
		// (they showed their back, Adrien 2026-11-08).
		if (mc.level == null || mc.level.getEntity(entity.getId()) != entity) {
			return fallback;
		}
		for (UUID uuid : state.opponents) {
			Entity opponent = ShowtimeHooks.player(mc.level, uuid);
			if (opponent == null && mc.level != null) {
				for (Entity candidate : mc.level.entitiesForRendering()) {
					if (uuid.equals(candidate.getUUID())) {
						opponent = candidate;
						break;
					}
				}
			}
			if (opponent != null) {
				double dx = opponent.getX() - entity.getX();
				double dz = opponent.getZ() - entity.getZ();
				if (dx * dx + dz * dz < 1.0E-4) {
					return fallback;
				}
				return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
			}
		}
		return fallback;
	}

	/** {@code trainer} acts {@code moments} in turn, after what it is playing now (the guard is cut short). */
	public static void act(UUID trainer, Moment... moments) {
		Trainer state = trainer == null ? null : TRAINERS.get(trainer);
		if (state == null) {
			return; // not a player trainer of this battle
		}
		for (Moment moment : moments) {
			if (moment == Moment.ATTACK && state.queue.contains(Moment.ATTACK)) {
				continue;
			}
			state.queue.add(moment);
		}
	}

	/**
	 * {@code BallSignals}: {@code trainer}'s ball is thrown or calling back now. Acted at once, cutting what is playing,
	 * a throw from {@link #THROW_START}.
	 */
	public static void onBall(Entity thrower, Moment moment) {
		Minecraft mc = Minecraft.getInstance();
		for (UUID uuid : TRAINERS.keySet()) {
			// By entity: a Hub avatar's own uuid isn't the battle's.
			if (ShowtimeHooks.player(mc.level, uuid) == thrower) {
				onBall(uuid, moment);
				return;
			}
		}
	}

	public static void onBall(UUID trainer, Moment moment) {
		Trainer state = TRAINERS.get(trainer);
		Minecraft mc = Minecraft.getInstance();
		if (PLAYER == null || state == null || !inBattle || mc.level == null) {
			return;
		}
		Player player = ShowtimeHooks.player(mc.level, trainer);
		if (player == null) {
			return;
		}
		state.uuid = trainer;
		state.queue.clear();
		state.playing = moment;
		String name = animationFor(trainer, moment);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath(NAMESPACE, name);
		if (moment == Moment.SEND_OUT) {
			PLAYER.play(player, id, THROW_START.getOrDefault(name, 0));
		} else {
			PLAYER.play(player, id);
		}
	}

	/** The battle is over: the guard stops, a victory or defeat still playing ends first. */
	public static void end() {
		inBattle = false;
	}

	/** {@code /showtime preview emote <moment>}: on the player themselves (seen in third person). */
	public static void preview(Moment moment) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		Trainer state = TRAINERS.computeIfAbsent(mc.player.getUUID(), ignored -> new Trainer());
		state.queue.clear();
		state.queue.add(moment);
		state.playing = null;
		state.stopIn = moment.loops() ? PREVIEW_LOOP_TICKS : -1;
	}

	/** Left the world: everything stops at once. */
	public static void reset() {
		stopAll();
		inBattle = false;
	}

	/** Every client tick: the next moment, the guard again, or the end. */
	public static void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (PLAYER == null || mc.level == null || mc.isPaused()) {
			return;
		}
		Iterator<Map.Entry<UUID, Trainer>> it = TRAINERS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Trainer> entry = it.next();
			Trainer state = entry.getValue();
			state.uuid = entry.getKey();
			Player player = ShowtimeHooks.player(mc.level, entry.getKey());
			if (player == null) {
				continue; // out of sight for now: picked up again when seen
			}
			if (state.looped != null) {
				// Ticked even with a screen open: the profile screen shows it.
				if (!PLAYER.playing(player)) {
					PLAYER.play(player, ResourceLocation.fromNamespaceAndPath(NAMESPACE, state.looped));
				}
				continue;
			}
			if (state.stopIn > 0 && --state.stopIn == 0) {
				PLAYER.stop(player);
				it.remove();
				continue;
			}
			boolean busy = state.playing != null && !state.playing.loops() && PLAYER.playing(player);
			if (busy) {
				continue;
			}
			Moment next = state.queue.poll();
			if (next != null) {
				play(player, state, next);
			} else if (inBattle || state.stopIn > 0) {
				if (state.playing != Moment.GUARD || !PLAYER.playing(player)) {
					play(player, state, Moment.GUARD);
				}
			} else {
				PLAYER.stop(player);
				it.remove();
			}
		}
	}

	private static void play(Player player, Trainer state, Moment moment) {
		state.playing = moment;
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath(NAMESPACE, animationFor(state.uuid, moment));
		boolean played = PLAYER.play(player, id);
		if (!played && MISSING.add(id.toString())) {
			LOG.warn("Trainer animation {} not found", id);
		}
		if (moment != Moment.GUARD) {
			LOG.debug("Trainer emote {} on {} ({}): {}", moment.id(), player.getGameProfile().getName(),
					player.getClass().getSimpleName(), played ? "played" : "not played");
		}
	}

	private static void stopAll() {
		Minecraft mc = Minecraft.getInstance();
		if (PLAYER != null && mc.level != null) {
			for (UUID uuid : TRAINERS.keySet()) {
				Player player = ShowtimeHooks.player(mc.level, uuid);
				if (player != null) {
					PLAYER.stop(player);
				}
			}
		}
		TRAINERS.clear();
	}
}
