package com.mystaria.showtime.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.client.net.battle.BattleMessageHandler;
import com.cobblemon.mod.common.net.messages.client.battle.BattleMessagePacket;

import net.minecraft.client.Minecraft;

import com.mystaria.showtime.classic.ClassicBattles;

/** Battle messages: moves filmed, Mega / Z / Tera staged ({@link ClassicBattles}), once Cobblemon's own handler has run. */
@Mixin(value = BattleMessageHandler.class, remap = false)
public abstract class ClassicBattleMessageHandlerMixin {

	@Inject(method = "handle(Lcom/cobblemon/mod/common/net/messages/client/battle/BattleMessagePacket;Lnet/minecraft/client/Minecraft;)V", at = @At("RETURN"))
	private void showtime$stage(BattleMessagePacket packet, Minecraft client, CallbackInfo ci) {
		ClassicBattles.onMessages(packet.getMessages());
	}
}
