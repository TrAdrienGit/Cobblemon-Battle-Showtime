package com.mystaria.showtime.api;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import com.mystaria.showtime.battle.BattleCinematic;

/**
 * A battle intro another mod brings ({@link ShowtimeHooks#registerIntro}): Phantasmon Events' gym leader intro, for
 * instance. It plays like Showtime's own: on a screen over the world, from the player's own view, its clock starting at
 * 0 when it opens, the battle music already playing; it may also move the camera. Showtime adds the white flash of its
 * end ({@link #flashes()}), lists it in the menu (Intros) and lets players switch it on, off or make it a favourite.
 *
 * <p>A {@link #reserved() reserved} intro is shown and drawn only for the players it is unlocked for
 * ({@link ShowtimeHooks#setUnlocks}); an intro imposed on a battle by the driving mod plays whatever the unlocks.
 * Client thread only.
 */
public interface CustomIntro {

	/** Its id, {@code <namespace>:<name>} ({@code phantasmon_events:gym_leader}), stable: players' choices use it. */
	String id();

	/** Its name in the menu. */
	Component name();

	/** How long it lasts, in milliseconds, from its first frame to its last (the flash included). */
	long length();

	/** Whether only the players it is unlocked for may pick it ({@link ShowtimeHooks#setUnlocks}). */
	default boolean reserved() {
		return false;
	}

	/** Whether it ends on Showtime's white flash (its last 300 ms), as most intros do. */
	default boolean flashes() {
		return true;
	}

	/** Every client tick while it plays ({@code t}: its own clock, in ms): sounds, model animation. */
	default void tick(IntroContext context, long t) {
	}

	/** One frame of it, over the world; {@code t}: its own clock, in ms. */
	void render(GuiGraphics graphics, IntroContext context, long t, float partialTick);

	/** Where the camera stands this frame, or null for the player's own view. {@code t}: its own clock, in ms. */
	default BattleCinematic.CameraPose camera(IntroContext context, long t, float partialTick) {
		return null;
	}
}
