package com.mystaria.showtime.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.client.net.battle.BattleMakeChoiceHandler;
import com.cobblemon.mod.common.net.messages.client.battle.BattleMakeChoicePacket;

import net.minecraft.client.Minecraft;

import com.mystaria.showtime.classic.ClassicBattles;

/** Players choose again: back to the choosing shots ({@link ClassicBattles}), once Cobblemon's own handler has run. */
@Mixin(value = BattleMakeChoiceHandler.class, remap = false)
public abstract class ClassicBattleMakeChoiceHandlerMixin {

	@Inject(method = "handle(Lcom/cobblemon/mod/common/net/messages/client/battle/BattleMakeChoicePacket;Lnet/minecraft/client/Minecraft;)V", at = @At("RETURN"))
	private void showtime$stage(BattleMakeChoicePacket packet, Minecraft client, CallbackInfo ci) {
		ClassicBattles.onChoosing();
	}
}
