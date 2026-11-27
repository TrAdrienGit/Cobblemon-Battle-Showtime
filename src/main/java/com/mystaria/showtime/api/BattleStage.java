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

	/**
	 * Every active Pokémon of that side, in slot order: two in a doubles battle, which the camera frames together.
	 * Defaults to {@link #activePokemon} alone, so a stage written for singles keeps working.
	 */
	default java.util.List<Entity> activePokemons(boolean local) {
		Entity first = activePokemon(local);
		return first == null ? java.util.List.of() : java.util.List.of(first);
	}

	/**
	 * Where that side's Pokémon stand together: the middle of its active Pokémon (one in singles), else its trainer,
	 * else null.
	 */
	default Vec3 sidePosition(boolean local, float partialTick) {
		java.util.List<Entity> active = activePokemons(local);
		if (active.isEmpty()) {
			return trainerPosition(local);
		}
		Vec3 sum = Vec3.ZERO;
		for (Entity entity : active) {
			sum = sum.add(entity.getPosition(partialTick));
		}
		return sum.scale(1.0 / active.size());
	}
}
