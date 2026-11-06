package com.mystaria.showtime.emote;

import java.util.Map;
import java.util.WeakHashMap;

import dev.kosmx.playerAnim.api.IPlayable;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * The trainers' animations on playerAnimator (KosmX, brought by Emotecraft): one layer of Showtime's per player, above
 * Emotecraft's own, faded in and out. Loaded by name only when playerAnimator is installed ({@link TrainerEmotes}).
 */
final class PlayerAnimatorEmotes implements EmotePlayer {

	/** Above Emotecraft's layer (1000): a trainer in battle acts the battle. */
	private static final int LAYER_PRIORITY = 1500;
	private static final int FADE_TICKS = 4;

	private final Map<AbstractClientPlayer, ModifierLayer<IAnimation>> layers = new WeakHashMap<>();

	@Override
	public boolean play(Player player, ResourceLocation id) {
		if (!(player instanceof AbstractClientPlayer client)) {
			return false;
		}
		IPlayable animation = PlayerAnimationRegistry.getAnimation(id);
		if (animation == null) {
			return false;
		}
		layer(client).replaceAnimationWithFade(fade(), animation.playAnimation());
		return true;
	}

	@Override
	public boolean play(Player player, ResourceLocation id, int startTick) {
		if (!(player instanceof AbstractClientPlayer client)) {
			return false;
		}
		IPlayable animation = PlayerAnimationRegistry.getAnimation(id);
		if (animation == null) {
			return false;
		}
		IAnimation playing = animation.playAnimation();
		if (playing instanceof dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer keyframes) {
			playing = new dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer(keyframes.getData(), startTick);
		}
		layer(client).replaceAnimationWithFade(fade(), playing);
		return true;
	}

	@Override
	public java.util.Set<String> names(String namespace) {
		return PlayerAnimationRegistry.getModAnimations(namespace).keySet();
	}

	@Override
	public boolean playing(Player player) {
		ModifierLayer<IAnimation> layer = player instanceof AbstractClientPlayer client ? layers.get(client) : null;
		return layer != null && layer.getAnimation() != null && layer.getAnimation().isActive();
	}

	@Override
	public void stop(Player player) {
		ModifierLayer<IAnimation> layer = player instanceof AbstractClientPlayer client ? layers.get(client) : null;
		if (layer != null && layer.getAnimation() != null) {
			layer.replaceAnimationWithFade(fade(), null);
		}
	}

	private ModifierLayer<IAnimation> layer(AbstractClientPlayer player) {
		return layers.computeIfAbsent(player, ignored -> {
			ModifierLayer<IAnimation> layer = new ModifierLayer<>();
			layer.addModifierLast(new NoBendWithShaders());
			PlayerAnimationAccess.getPlayerAnimLayer(player).addAnimLayer(LAYER_PRIORITY, layer);
			return layer;
		});
	}

	/**
	 * Elbows and knees bent by bendy-lib turned the outer skin layer black under shader packs (Adrien 2026-10-30,
	 * Complementary + Euphoria Patches): while one is on (Iris), the limbs stay straight; the rest of the animation plays.
	 */
	private static final class NoBendWithShaders extends dev.kosmx.playerAnim.api.layered.modifier.AbstractModifier {

		private static java.lang.reflect.Method inUse;
		private static Object iris;
		private static boolean checked;

		@Override
		public dev.kosmx.playerAnim.core.util.Vec3f get3DTransform(String modelName, dev.kosmx.playerAnim.api.TransformType type,
				float tickDelta, dev.kosmx.playerAnim.core.util.Vec3f value0) {
			if (type == dev.kosmx.playerAnim.api.TransformType.BEND && shadersOn()) {
				return value0;
			}
			return super.get3DTransform(modelName, type, tickDelta, value0);
		}

		private static boolean shadersOn() {
			if (!checked) {
				checked = true;
				try {
					Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
					iris = api.getMethod("getInstance").invoke(null);
					inUse = api.getMethod("isShaderPackInUse");
				} catch (ReflectiveOperationException | LinkageError ex) {
					inUse = null; // no Iris: no shader pack
				}
			}
			try {
				return inUse != null && (boolean) inUse.invoke(iris);
			} catch (ReflectiveOperationException ex) {
				return false;
			}
		}
	}

	private static AbstractFadeModifier fade() {
		return AbstractFadeModifier.standardFadeIn(FADE_TICKS, Ease.INOUTSINE);
	}
}
