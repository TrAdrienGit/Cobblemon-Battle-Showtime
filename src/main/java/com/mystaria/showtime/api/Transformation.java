package com.mystaria.showtime.api;

/**
 * A Mega Evolution, Primal Reversion, Z-Move or Terastallization announced by a battle, played as a set piece
 * ({@code BattleShowtime}). {@code aspect} is what the model resolvers key on for the forms that change the model:
 * {@code mega}, {@code mega_x}, {@code mega_y}, {@code primal}; and two markers that don't: {@link #Z_POWER},
 * {@code tera:<type>}.
 *
 * @param pnx     battle position ({@code p1a}...), see {@link BattleStage#pokemonAt}
 * @param uuid    the battle Pokémon's uuid (its Tera glow follows it through switches)
 * @param species species id, for the effect's colours
 * @param aspect  the aspect to add, or a marker
 */
public record Transformation(String pnx, String uuid, String species, String aspect) {

	/** Not an aspect: a Z-Move's burst, the model doesn't change. */
	public static final String Z_POWER = "zpower";

	/** Not an aspect either: a Terastallization, {@code tera:<type>} (fire, water... stellar). */
	public static final String TERA_PREFIX = "tera:";

	public boolean primal() {
		return "primal".equals(aspect);
	}

	public boolean zPower() {
		return Z_POWER.equals(aspect);
	}

	public boolean tera() {
		return aspect != null && aspect.startsWith(TERA_PREFIX);
	}

	public String teraType() {
		return tera() ? aspect.substring(TERA_PREFIX.length()) : null;
	}

	/** Whether the Pokémon's model changes (Mega Evolution, Primal Reversion). */
	public static boolean changesModel(String aspect) {
		return aspect != null && !Z_POWER.equals(aspect) && !aspect.startsWith(TERA_PREFIX);
	}
}
