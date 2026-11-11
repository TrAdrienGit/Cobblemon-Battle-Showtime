package com.mystaria.showtime.api;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;

/**
 * What a {@link CustomIntro} is given to draw with: the battle it opens and Showtime's own drawing helpers, so that it
 * looks like the rest. Valid while the intro plays; client thread only.
 */
public interface IntroContext {

	/** The screen's size, in GUI pixels. */
	int width();

	int height();

	String opponentName();

	String selfName();

	/** The opponent's team size, or -1 when unknown (a Cobblemon battle does not send it). */
	int opponentTeamSize();

	int ownTeamSize();

	/**
	 * The opponent as it is drawn on screen (a copy of the real player, with their skin and equipment), or null when
	 * none can be made. Animate it in {@link CustomIntro#tick} ({@code walkAnimation}, {@code swing}).
	 */
	Player opponent();

	/** The local player as drawn on screen, or null. */
	Player self();

	/**
	 * Whether the local player is the intro's star (whose intro it is): the driving mod says who picked it; else the
	 * local player when the intro is reserved and unlocked for them, else the opponent. A gym leader's intro shows the
	 * gym leader, whoever watches it.
	 */
	boolean starIsSelf();

	/** The real opponent in the world (for the camera), or null when the world doesn't have them. */
	Player opponentInWorld();

	/**
	 * Draws a trainer standing, lit as Showtime's intros do: feet at ({@code x}, {@code feetY}), {@code scale} GUI
	 * pixels per block, turned by {@code bodyYaw} degrees (180: facing the screen; 150: turned towards the right of the
	 * screen, for a trainer on the left; 210: towards the left, for one on the right; 0: the back),
	 * {@code brightness} 0 (a silhouette) to 1. Draw text over it after pushing the pose forward (z 300 and more).
	 */
	void drawTrainer(GuiGraphics graphics, boolean opponent, float x, float feetY, float scale, float bodyYaw, float brightness);

	/** A Poké Ball item centred at ({@code cx}, {@code cy}), {@code size} pixels, turned by {@code degrees}. */
	void drawBall(GuiGraphics graphics, float cx, float cy, float size, float degrees);

	/** A sound heard by this player only (calm effects lower nothing here: keep it soft). */
	void sound(SoundEvent sound, float pitch, float volume);
}
