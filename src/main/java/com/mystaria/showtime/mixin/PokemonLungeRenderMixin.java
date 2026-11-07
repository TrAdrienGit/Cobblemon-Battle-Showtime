package com.mystaria.showtime.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

import com.mystaria.showtime.move.MoveLunge;

/** Moves the model of a Pokémon using a move without an attack animation ({@link MoveLunge}). */
@Mixin(LivingEntityRenderer.class)
abstract class PokemonLungeRenderMixin {

	@Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At("HEAD"))
	private void showtime$lunge(LivingEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
		if (entity instanceof PokemonEntity) {
			MoveLunge.apply(entity, pose, partialTick);
		}
	}
}
