package com.mystaria.showtime.emote;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/** Plays the trainers' animations: {@link PlayerAnimatorEmotes} when playerAnimator is installed. */
interface EmotePlayer {

	/** Fades into animation {@code id} on {@code player}; false if there is no such animation. */
	boolean play(Player player, ResourceLocation id);

	/** The same, the animation starting at {@code startTick} rather than at its beginning. */
	boolean play(Player player, ResourceLocation id, int startTick);

	/** Whether the animation last played on {@code player} is still running (a loop always is). */
	boolean playing(Player player);

	/** The animation names a namespace has (Showtime's own and those resource packs add). */
	java.util.Set<String> names(String namespace);

	/** Fades back to the player's own movements. */
	void stop(Player player);
}
