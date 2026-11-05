package com.mystaria.showtime.api;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * What the battle camera needs to know of a battle to film it: where its Pokémon and trainers are. Given by whoever
 * runs the battle's scene when it starts ({@code BattleCameraDirector.start}): Phantasmon for its Ghost battles (its
 * own client-side entities), Showtime itself for Cobblemon's battles (the server's entities). Client thread only.
 */
public interface BattleStage {

	/** The entity standing for the battle position {@code pnx} ({@code p1a}, {@code p2a}...), or null. */
	Entity pokemonAt(String pnx);

	/** The first active Pokémon of the local player's side ({@code local}) or of the opponent's, or null. */
	Entity activePokemon(boolean local);

	/** Where that side's trainer stands, or null if not loaded: the field before any Pokémon is out. */
	Vec3 trainerPosition(boolean local);
}
