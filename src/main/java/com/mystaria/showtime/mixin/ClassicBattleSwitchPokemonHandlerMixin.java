package com.mystaria.showtime.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.cobblemon.mod.common.client.net.battle.BattleSwitchPokemonHandler;
import com.cobblemon.mod.common.net.messages.client.battle.BattleSwitchPokemonPacket;

import net.minecraft.client.Minecraft;

import com.mystaria.showtime.classic.ClassicBattles;
import com.mystaria.showtime.classic.ClassicEmotes;

/** A Pokémon comes in: a close-up ({@link ClassicBattles}), once Cobblemon's own handler has run. */
@Mixin(value = BattleSwitchPokemonHandler.class, remap = false)
public abstract class ClassicBattleSwitchPokemonHandlerMixin {

	/** Before Cobblemon's handler: whether a Pokémon still standing there is called back. */
	@Inject(method = "handle(Lcom/cobblemon/mod/common/net/messages/client/battle/BattleSwitchPokemonPacket;Lnet/minecraft/client/Minecraft;)V", at = @At("HEAD"))
	private void showtime$emote(BattleSwitchPokemonPacket packet, Minecraft client, CallbackInfo ci) {
		ClassicEmotes.onSwitch(packet.getPnx());
	}

	@Inject(method = "handle(Lcom/cobblemon/mod/common/net/messages/client/battle/BattleSwitchPokemonPacket;Lnet/minecraft/client/Minecraft;)V", at = @At("RETURN"))
	private void showtime$stage(BattleSwitchPokemonPacket packet, Minecraft client, CallbackInfo ci) {
		ClassicBattles.onFocus(packet.getPnx());
	}
}
