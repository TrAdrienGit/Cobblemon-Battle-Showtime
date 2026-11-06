package com.mystaria.showtime.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

import com.mystaria.showtime.emote.TrainerEmotes;

/**
 * A battle's trainers are drawn turned to their opponent (Adrien 2026-10-30: they look each other in the eyes), on this
 * client only, whatever their player looks at ({@link TrainerEmotes#facingYaw}).
 */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererFacingMixin {

	@ModifyArgs(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V"))
	private void showtime$faceOpponent(Args args) {
		LivingEntity entity = args.get(0);
		float bodyYaw = args.get(3);
		Float preview = com.mystaria.showtime.gui.MenuPreview.facingYaw(entity);
		args.set(3, preview != null ? preview : TrainerEmotes.facingYaw(entity, bodyYaw));
	}
}
