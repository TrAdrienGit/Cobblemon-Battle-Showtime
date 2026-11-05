package com.mystaria.showtime.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.client.net.battle.BattleInitializeHandler;
import com.cobblemon.mod.common.net.messages.client.battle.BattleInitializePacket;

import net.minecraft.client.Minecraft;

import com.mystaria.showtime.classic.ClassicBattles;

/** A Cobblemon battle starts: staged unless wild ({@link ClassicBattles}), once Cobblemon's own handler has run. */
@Mixin(value = BattleInitializeHandler.class, remap = false)
public abstract class ClassicBattleInitializeHandlerMixin {

	@Inject(method = "handle(Lcom/cobblemon/mod/common/net/messages/client/battle/BattleInitializePacket;Lnet/minecraft/client/Minecraft;)V", at = @At("RETURN"))
	private void showtime$stage(BattleInitializePacket packet, Minecraft client, CallbackInfo ci) {
		ClassicBattles.onInitialize(packet);
	}
}
