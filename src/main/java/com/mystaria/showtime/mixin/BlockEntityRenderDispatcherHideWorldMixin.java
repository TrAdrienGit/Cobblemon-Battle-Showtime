package com.mystaria.showtime.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.mystaria.showtime.ambiance.BattleAmbiance;

/** An ambiance hiding the world: the block entities (chests, signs, banners...) are not drawn either. */
@Mixin(BlockEntityRenderDispatcher.class)
abstract class BlockEntityRenderDispatcherHideWorldMixin {

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private <E extends BlockEntity> void showtime$hideBlockEntities(E blockEntity, float partialTick, PoseStack pose,
			MultiBufferSource buffers, CallbackInfo ci) {
		if (BattleAmbiance.hidesWorld()) {
			ci.cancel();
		}
	}
}
