package com.mystaria.showtime.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.client.net.battle.BattleMusicHandler;
import com.cobblemon.mod.common.net.messages.client.battle.BattleMusicPacket;

import net.minecraft.client.Minecraft;

import com.mystaria.showtime.classic.ClassicBattles;

/**
 * Cobblemon's battle music gives way to the resource pack's in a staged battle: while Cobblemon's plays, its sound
 * filter blocks every other Music sound ({@link ClassicBattles#silencesCobblemonMusic}).
 */
@Mixin(value = BattleMusicHandler.class, remap = false)
public abstract class ClassicBattleMusicHandlerMixin {

	@Inject(method = "handle(Lcom/cobblemon/mod/common/net/messages/client/battle/BattleMusicPacket;Lnet/minecraft/client/Minecraft;)V",
			at = @At("HEAD"), cancellable = true)
	private void showtime$ourMusicInstead(BattleMusicPacket packet, Minecraft client, CallbackInfo ci) {
		if (ClassicBattles.silencesCobblemonMusic()) {
			ci.cancel();
		}
	}
}
