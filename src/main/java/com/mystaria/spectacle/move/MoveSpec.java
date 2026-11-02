package com.mystaria.spectacle.move;

import java.util.List;

/**
 * A redrawn move (M2), read from {@code assets/<namespace>/moves/<move>.json} (file name = the move's id as Cobblemon
 * names it: {@code knockoff}, {@code uturn}): a stack of {@link Layer}s, each one of the {@link Motifs} played in one
 * phase of the move. Gson fills the fields (snake_case in the file); colours are {@code "#rrggbb"}.
 *
 * <p>Phases, on Cobblemon's timeline (see {@link LayeredMove}): {@code wind} (the user winds up), {@code travel} (from the
 * user to the target), {@code hit} (from Cobblemon's hit cue), {@code self} (moves on the user alone: boosts, heals,
 * Protect — the whole move).
 */
public final class MoveSpec {

	/** {@code physical}, {@code special} or {@code status}: physical moves hit up close, so no travel by default. */
	public String category = "special";
	/** The move's colours (its type's), used by layers without their own. */
	public String color = "#FFFFFF";
	public String accent;
	/** Scale of the whole move. */
	public float size = 1f;
	public List<Layer> layers = List.of();

	public static final class Layer {
		/** One of {@link Motifs}' names. */
		public String motif;
		/** {@code wind}, {@code travel}, {@code hit} or {@code self}. */
		public String phase = "hit";
		/** Ticks into the phase before it starts. */
		public int delay;
		/** Its length in ticks; 0: the phase's. */
		public int duration;
		public String color;
		public String accent;
		public float size = 1f;
		/** How many (blows, rocks, coins, blades…), for motifs that repeat. */
		public int count;
		/** A block (rocks, hammers, ice) or an item (sword, coins) drawn by the motif. */
		public String block;
		public String item;
		/** Vanilla particle ids the motif sprays. */
		public List<String> particles = List.of();
	}

	static int color(String hex, int fallback) {
		if (hex == null) {
			return fallback;
		}
		try {
			return Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16) & 0xFFFFFF;
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}
}
