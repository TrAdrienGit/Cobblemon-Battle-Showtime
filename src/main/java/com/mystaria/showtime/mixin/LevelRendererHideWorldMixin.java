package com.mystaria.showtime.mixin;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;

import com.mystaria.showtime.ambiance.BattleAmbiance;

/**
 * An ambiance hiding the world ({@link BattleAmbiance#hidesWorld}): no terrain layer drawn. Sodium overwrites this
 * method with its own renderer, and this head injection still runs before its body: the terrain disappears with or
 * without Sodium. Only drawing is skipped: the world itself (collisions, movements) is untouched.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererHideWorldMixin {

	@Inject(method = "renderSectionLayer", at = @At("HEAD"), cancellable = true)
	private void showtime$hideTerrain(RenderType layer, double x, double y, double z, Matrix4f frustum, Matrix4f projection,
			CallbackInfo ci) {
		if (BattleAmbiance.hidesWorld()) {
			ci.cancel();
		}
	}
}
