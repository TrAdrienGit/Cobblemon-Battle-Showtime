package com.mystaria.showtime.api;

/**
 * (1.4, 2026-11-19) An ambiance laid on a place rather than on a battle: a box of the world, {@code halfX} by
 * {@code halfZ} blocks either side of its centre ({@code centerX}, {@code centerZ}), turned {@code yaw} degrees (0, 90,
 * 180 or 270), from {@code bottomY} up to {@code topY}. Phantasmon Events: the ambiance of a hub, the same for everyone in it.
 * Given by {@link ShowtimeHooks#setAreaAmbiance} while the local player stands in it.
 *
 * <p>Everything of the ambiance stays put in the box, wherever the player walks or flies: its effects are drawn on
 * every floor of it (two platforms one above the other each have theirs), its particles fall around the player on the
 * floor there; the sky, the weather, the filter and the sound are
 * the player's while they are in it. Never a stadium: the place is the world as it is. A battle of the player's own,
 * the menu's preview or a battle seen from afar takes over, and the area comes back once it is over.
 */
public record AmbianceArea(String ambiance, double centerX, double centerZ, double halfX, double halfZ, int yaw,
		double bottomY, double topY) {

	/** Whether the column (x, z) of the world is in the box. */
	public boolean contains(double x, double z) {
		double dx = x - centerX;
		double dz = z - centerZ;
		double a = Math.toRadians(-yaw);
		double hx = dx * Math.cos(a) - dz * Math.sin(a);
		double hz = dx * Math.sin(a) + dz * Math.cos(a);
		return Math.abs(hx) <= halfX && Math.abs(hz) <= halfZ;
	}
}
