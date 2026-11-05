package com.mystaria.showtime.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;

import com.mystaria.showtime.battle.BattleCinematic;

/** The intro's two trainers are drawn however far the camera stands ({@link BattleCinematic#forcedVisible}). */
@Mixin(EntityRenderer.class)
abstract class EntityRendererCinematicMixin {

	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void showtime$introTrainers(Entity entity, Frustum frustum, double camX, double camY, double camZ,
			CallbackInfoReturnable<Boolean> cir) {
		if (BattleCinematic.forcedVisible(entity)) {
			cir.setReturnValue(true);
		}
	}
}
