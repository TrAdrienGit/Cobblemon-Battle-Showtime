package com.mystaria.showtime.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.client.net.battle.BattleFaintHandler;
import com.cobblemon.mod.common.net.messages.client.battle.BattleFaintPacket;

import net.minecraft.client.Minecraft;

import com.mystaria.showtime.classic.ClassicBattles;

/** A Pokémon faints: a close-up ({@link ClassicBattles}), once Cobblemon's own handler has run. */
@Mixin(value = BattleFaintHandler.class, remap = false)
public abstract class ClassicBattleFaintHandlerMixin {

	@Inject(method = "handle(Lcom/cobblemon/mod/common/net/messages/client/battle/BattleFaintPacket;Lnet/minecraft/client/Minecraft;)V", at = @At("RETURN"))
	private void showtime$stage(BattleFaintPacket packet, Minecraft client, CallbackInfo ci) {
		ClassicBattles.onFocus(packet.getPnx());
	}
}
