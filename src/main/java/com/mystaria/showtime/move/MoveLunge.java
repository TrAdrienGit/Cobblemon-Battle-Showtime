package com.mystaria.showtime.move;

import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.client.entity.PokemonClientDelegate;
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Most Cobblemon models have no attack animation, and then a Pokémon stands still when it uses a move. This moves the
 * whole model instead (Adrien 2026-11-07): it works on any model, since it needs none of its bones. Physical: a rush
 * forward; special: a recoil and a thrust; status: a hop. A cry goes with it now and then, so that it doesn't get
 * repetitive.
 */
public final class MoveLunge {

	/** Moves whose Cobblemon timeline already animates the user's whole body, whatever its model. */
	private static final Set<String> BODY_ANIMATED = Set.of("aerialace", "amnesia", "bodyslam", "bulldoze", "closecombat",
			"doubleteam", "explosion", "flamecharge", "flamewheel", "minimize", "nastyplot", "playrough", "quickattack",
			"seismictoss", "selfdestruct", "suckerpunch", "tackle", "tailwhip");

	private static final float CRY_CHANCE = 0.4f;
	private static final int TICK = 0, FORWARD = 1, UP = 2, PITCH = 3, SQUASH = 4;

	/** Keys: tick, forward (a share of the reach), up, pitch in degrees, height factor. */
	private static final float[][] PHYSICAL = {
			{0, 0, 0, 0, 1}, {8, -0.25f, 0, -15, 1}, {14, -0.3f, 0, -15, 1}, {22, 1, 0, 20, 1}, {26, 1, 0, 10, 1}, {36, 0, 0, 0, 1}};
	private static final float[][] SPECIAL = {
			{0, 0, 0, 0, 1}, {10, -0.3f, 0, -20, 1}, {14, 0.3f, 0, 15, 1}, {20, 0.3f, 0, 10, 1}, {30, 0, 0, 0, 1}};
	private static final float[][] STATUS = {
			{0, 0, 0, 0, 1}, {5, 0, 0, 0, 0.85f}, {10, 0, 0.5f, 0, 1.1f}, {15, 0, 0, 0, 0.9f}, {20, 0, 0, 0, 1}};

	private static final Map<Entity, Motion> PLAYING = new WeakHashMap<>();
	private static final Random RANDOM = new Random();

	private MoveLunge() {
	}

	private static final class Motion {
		final float[][] keys;
		final float reach;
		final float yaw;
		int age;

		Motion(float[][] keys, float reach, float yaw) {
			this.keys = keys;
			this.reach = reach;
			this.yaw = yaw;
		}
	}

	/** Before Cobblemon handles the packet: with no animation to play for this move, the model moves instead. */
	public static void onAnimation(PlayPosableAnimationPacket packet) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || packet.getAnimation().isEmpty()
				|| !(level.getEntity(packet.getEntityId()) instanceof PokemonEntity entity)
				|| !(entity.getDelegate() instanceof PokemonClientDelegate delegate)) {
			return;
		}
		String move = packet.getAnimation().iterator().next();
		float[][] keys = keysFor(move, packet.getAnimation());
		if (keys == null || BODY_ANIMATED.contains(move) || hasAnimation(delegate, packet.getAnimation())) {
			return;
		}
		float size = Mth.clamp(entity.getBbWidth(), 0.6f, 2f);
		float reach = keys == PHYSICAL ? 1.2f + size : size;
		PLAYING.put(entity, new Motion(keys, reach, entity.yBodyRot));
		if (RANDOM.nextFloat() < CRY_CHANCE) {
			delegate.cry();
		}
	}

	/** The motion of this packet's move, or null when the packet isn't a move's. */
	private static float[][] keysFor(String move, Set<String> animation) {
		if (animation.contains("physical")) {
			return PHYSICAL;
		}
		if (animation.contains("special")) {
			return SPECIAL;
		}
		if (animation.contains("status")) {
			return STATUS;
		}
		MoveTemplate template = Moves.INSTANCE.getByName(move);
		if (template == null) {
			return null;
		}
		return switch (template.getDamageCategory().getName()) {
			case "physical" -> PHYSICAL;
			case "status" -> STATUS;
			default -> SPECIAL;
		};
	}

	private static boolean hasAnimation(PokemonClientDelegate delegate, Set<String> animation) {
		PosableModel model = delegate.getCurrentModel();
		if (model == null) {
			return false;
		}
		for (String name : animation) {
			if (model.getAnimation(delegate, name, delegate.getRuntime()) != null) {
				return true;
			}
		}
		return false;
	}

	public static void tick() {
		PLAYING.values().removeIf(motion -> ++motion.age >= motion.keys[motion.keys.length - 1][TICK]);
	}

	public static void reset() {
		PLAYING.clear();
	}

	/** At the start of the entity's render: the caller's own push and pop around it undo this. */
	public static void apply(Entity entity, PoseStack pose, float partialTick) {
		Motion motion = PLAYING.get(entity);
		if (motion == null) {
			return;
		}
		float time = motion.age + partialTick;
		float forward = value(motion.keys, FORWARD, time) * motion.reach;
		float up = value(motion.keys, UP, time);
		float pitch = value(motion.keys, PITCH, time);
		float squash = value(motion.keys, SQUASH, time);
		float middle = entity.getBbHeight() / 2f;
		pose.mulPose(Axis.YP.rotationDegrees(-motion.yaw));
		pose.translate(0, up, forward);
		pose.translate(0, middle, 0);
		pose.mulPose(Axis.XP.rotationDegrees(pitch));
		pose.translate(0, -middle, 0);
		pose.scale(1 / Mth.sqrt(squash), squash, 1 / Mth.sqrt(squash));
		pose.mulPose(Axis.YP.rotationDegrees(motion.yaw));
	}

	private static float value(float[][] keys, int column, float time) {
		for (int i = 1; i < keys.length; i++) {
			if (time <= keys[i][TICK]) {
				float span = keys[i][TICK] - keys[i - 1][TICK];
				float t = Mth.clamp((time - keys[i - 1][TICK]) / span, 0, 1);
				return Mth.lerp(t * t * (3 - 2 * t), keys[i - 1][column], keys[i][column]);
			}
		}
		return keys[keys.length - 1][column];
	}
}
