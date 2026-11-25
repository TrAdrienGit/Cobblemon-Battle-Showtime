package com.mystaria.showtime.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

import com.mystaria.showtime.battle.BattleShowtime;

/**
 * The set pieces' model colours (2026-11-25, {@link BattleShowtime#modelColor}): the Mega silhouettes drawn black, a
 * terastallized Pokémon's crystal tint. The colour multiplies the model's texture, so the details stay. Cobblemon's
 * Pokémon renderer and the players' go through this method.
 */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererTintMixin {

	/** The entity being drawn: the colour argument below has no access to it. Client thread, no nesting. */
	@Unique
	private static LivingEntity showtime$drawing;
	/**
	 * Per render under way, whether it pushed a pose for {@link BattleShowtime#modelScale}: popped at the end of the
	 * same render, even if another living entity is drawn inside it.
	 */
	@Unique
	private static final java.util.ArrayDeque<Boolean> showtime$scaled = new java.util.ArrayDeque<>();

	@Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("HEAD"))
	private void showtime$remember(LivingEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light,
			CallbackInfo ci) {
		showtime$drawing = entity;
		float scale = BattleShowtime.modelScale(entity);
		showtime$scaled.push(scale != 1f);
		if (scale != 1f) {
			float middle = entity.getBbHeight() / 2f;
			pose.pushPose();
			pose.translate(0, middle, 0);
			pose.scale(scale, scale, scale);
			pose.translate(0, -middle, 0);
		}
	}

	@Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("RETURN"))
	private void showtime$unscale(LivingEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light,
			CallbackInfo ci) {
		Boolean scaled = showtime$scaled.poll();
		if (scaled != null && scaled) {
			pose.popPose();
		}
	}

	@ModifyArg(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"),
			index = 4, require = 0)
	private int showtime$tint(int color) {
		LivingEntity entity = showtime$drawing;
		if (entity == null) {
			return color;
		}
		int tint = BattleShowtime.modelColor(entity);
		if (tint == -1) {
			return color;
		}
		// Keep the model's own alpha (an invisible-but-seen entity is drawn faint).
		return (color & 0xFF000000) | (tint & 0xFFFFFF);
	}
}
