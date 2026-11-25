package com.mystaria.showtime.api;

/**
 * The colours of the panel drawn over Cobblemon's battle screen (music, volume, the {@link BattleButton}s), so a mod
 * can match it to its own look (2026-11-25: Phantasmon's two looks). Given with {@link ShowtimeHooks#setPanelStyle};
 * Showtime's own is {@link #SHOWTIME}. A button whose accent is one of {@link BattleButton}'s named colours gets this
 * style's matching one ({@link #accent(int)}); any other ARGB stays as it is.
 *
 * @param card        the card behind the whole panel (with its alpha)
 * @param edge        the card's and the track list's outline
 * @param top         a key's gradient, top
 * @param bottom      a key's gradient, bottom
 * @param hoverTop    a hovered key's gradient, top (also a hovered track row)
 * @param hoverBottom a hovered key's gradient, bottom
 * @param well        the sunken volume bar
 * @param cyan        stands for {@link BattleButton#CYAN}: the usual accent
 * @param green       stands for {@link BattleButton#GREEN}: on, playing, the volume
 * @param dim         stands for {@link BattleButton#DIM}: unavailable
 * @param red         stands for {@link BattleButton#RED}: urgent
 * @param text        a hovered key's label, the track names
 */
public record BattlePanelStyle(int card, int edge, int top, int bottom, int hoverTop, int hoverBottom, int well,
		int cyan, int green, int dim, int red, int text) {

	/** Cobblemon's greys with Showtime's cyan, as before 2026-11-25. */
	public static final BattlePanelStyle SHOWTIME = new BattlePanelStyle(0xE02F2F2F, 0xFF676767, 0xFF4B4B4B, 0xFF3D3D3D,
			0xFF5A5A5A, 0xFF4B4B4B, 0xFF1A1A1A, BattleButton.CYAN, BattleButton.GREEN, BattleButton.DIM, BattleButton.RED,
			0xFFFFFFFF);

	/** This style's colour for a button's accent: its own for {@link BattleButton}'s named ones, else {@code color}. */
	public int accent(int color) {
		return color == BattleButton.CYAN ? cyan
				: color == BattleButton.GREEN ? green
				: color == BattleButton.DIM ? dim
				: color == BattleButton.RED ? red
				: color;
	}
}
