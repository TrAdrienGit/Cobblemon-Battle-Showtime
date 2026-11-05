package com.mystaria.showtime.api;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * A button of the column drawn over Cobblemon's battle screen while a battle is filmed
 * ({@code BattleScreenButtons}); Showtime's own is the camera one. Added with {@link ShowtimeHooks#addButton}.
 *
 * @param order     place in the column, smallest first (Showtime's camera button: {@link #CAMERA_ORDER})
 * @param visible   whether it shows this frame
 * @param label     its text this frame
 * @param color     its accent this frame ({@link #CYAN}, {@link #GREEN}, {@link #DIM}, {@link #RED} or any ARGB)
 * @param clickable whether it highlights on hover and takes clicks this frame
 * @param onClick   run on the client thread on a left click
 */
public record BattleButton(int order, BooleanSupplier visible, Supplier<String> label, IntSupplier color,
		BooleanSupplier clickable, Runnable onClick) {

	public static final int CAMERA_ORDER = 100;

	public static final int CYAN = 0xFF50E6FF;
	public static final int GREEN = 0xFF76FFB0;
	public static final int DIM = 0xFF6C8C96;
	public static final int RED = 0xFFFF5078;
}
