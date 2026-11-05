package com.mystaria.showtime.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;

import com.mystaria.showtime.ambiance.BattleAmbiance;

/** A battle ambiance's fog: its colour (the sky's horizon too) and its distance. */
@Mixin(FogRenderer.class)
abstract class FogRendererAmbianceMixin {

	@Shadow
	private static float fogRed;
	@Shadow
	private static float fogGreen;
	@Shadow
	private static float fogBlue;

	@Inject(method = "setupColor", at = @At("TAIL"))
	private static void showtime$fogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance,
			float darkenWorldAmount, CallbackInfo ci) {
		float[] rgb = { fogRed, fogGreen, fogBlue };
		BattleAmbiance.fogColor(rgb);
		if (rgb[0] != fogRed || rgb[1] != fogGreen || rgb[2] != fogBlue) {
			fogRed = rgb[0];
			fogGreen = rgb[1];
			fogBlue = rgb[2];
			RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0F);
		}
	}

	@Inject(method = "setupFog", at = @At("TAIL"))
	private static void showtime$fogDistance(Camera camera, FogRenderer.FogMode mode, float farPlane, boolean thickFog,
			float partialTick, CallbackInfo ci) {
		float start = BattleAmbiance.fogStart();
		float end = BattleAmbiance.fogEnd();
		if (!Float.isNaN(start) && !Float.isNaN(end)) {
			RenderSystem.setShaderFogStart(start);
			RenderSystem.setShaderFogEnd(end);
		}
	}
}
