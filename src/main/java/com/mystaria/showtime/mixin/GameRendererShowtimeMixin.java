package com.mystaria.showtime.mixin;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;

import com.mystaria.showtime.battle.BattleShowtime;
import com.mystaria.showtime.battle.ShowtimeOverlay;

/**
 * Battle set pieces ({@link BattleShowtime}): their field-of-view effects (dolly zoom, punches), and their 2D layer
 * drawn last, over the HUD and every screen — Cobblemon's battle screen included.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererShowtimeMixin {

	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void showtime$showtimeFov(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Double> cir) {
		double scale = BattleShowtime.fovScale();
		if (scale != 1.0) {
			cir.setReturnValue(cir.getReturnValue() * scale);
		}
	}

	/** The cinema camera (battle shots, intro, set pieces) never shows the first-person hand (Adrien 2026-10-30). */
	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void showtime$noHand(Camera camera, float partialTick, org.joml.Matrix4f projection, CallbackInfo ci) {
		if (com.mystaria.showtime.battle.BattleCinematic.cameraTaken()) {
			ci.cancel();
		}
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void showtime$showtimeOverlay(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
		if (!BattleShowtime.playing()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Window window = mc.getWindow();
		RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
		RenderSystem.backupProjectionMatrix();
		RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0f, (float) (window.getWidth() / window.getGuiScale()),
				(float) (window.getHeight() / window.getGuiScale()), 0f, 1000f, 21000f), VertexSorting.ORTHOGRAPHIC_Z);
		Matrix4fStack modelView = RenderSystem.getModelViewStack();
		modelView.pushMatrix();
		modelView.translation(0f, 0f, -11000f);
		RenderSystem.applyModelViewMatrix();
		GuiGraphics graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
		ShowtimeOverlay.render(graphics);
		graphics.flush();
		modelView.popMatrix();
		RenderSystem.applyModelViewMatrix();
		RenderSystem.restoreProjectionMatrix();
	}
}
