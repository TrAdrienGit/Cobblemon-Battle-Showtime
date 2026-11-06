package com.mystaria.showtime.classic;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;

import net.minecraft.world.entity.Entity;

import com.mystaria.showtime.emote.TrainerEmotes;
import com.mystaria.showtime.emote.TrainerEmotes.Moment;

/**
 * Plan P1.1: Cobblemon's own signal that a Pokémon is thrown or called back, its synced beam mode, read on this client.
 * The trainers act on it rather than on the switch packet, which only comes once the recall is over and just as the
 * ball leaves. Client thread only.
 */
public final class BallSignals {

	private static final int THROWN = 1;
	private static final int RECALLED = 3;

	/** The server often sends a mode again: only a change counts. */
	private static final Map<PokemonEntity, Integer> LAST = new WeakHashMap<>();

	private static final java.util.List<PokemonEntity> THROWN_NOW = new java.util.ArrayList<>();

	private BallSignals() {
	}

	/** A recall with a Pokémon at 0 HP still on the field is its K.O.: Cobblemon only announces it after the recall. */
	private static boolean knockedOut() {
		var battle = com.cobblemon.mod.common.client.CobblemonClient.INSTANCE.getBattle();
		if (battle == null) {
			return false;
		}
		for (var side : battle.getSides()) {
			for (var active : side.getActiveClientBattlePokemon()) {
				var pokemon = active.getBattlePokemon();
				if (pokemon != null && pokemon.getHpValue() <= 0) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean inBattle(PokemonEntity entity) {
		return entity.isBattling() || entity.getOwnerUUID() == null
				&& com.cobblemon.mod.common.client.CobblemonClient.INSTANCE.getBattle() != null;
	}

	/** Every client tick: the throws of the last one, now that all their data has arrived. */
	public static void tick() {
		for (PokemonEntity entity : THROWN_NOW) {
			if (inBattle(entity) && !entity.isRemoved()) {
				com.mystaria.showtime.battle.BattleCameraDirector.onThrow(entity);
				if (com.mystaria.showtime.battle.BattleCameraDirector.ballEffects()) {
					com.mystaria.showtime.move.BallOpening.start(entity);
				}
			}
		}
		THROWN_NOW.clear();
	}

	public static void onBeamMode(PokemonEntity entity) {
		int mode = entity.getBeamMode();
		Integer last = LAST.put(entity, mode);
		if (last != null && last == mode) {
			return;
		}
		UUID owner = entity.getOwnerUUID();
		// Only battles: a player sending a Pokémon out for a walk has an owner and no battle. A Pokémon of a client-side
		// battle (Phantasmon's Ghost battles) has neither.
		// The owner alone doesn't tell: it can reach this client after the beam mode, on a Pokémon sent out for a walk.
		// Cobblemon's spawn packet sets the beam mode before the battle id: a throw is judged at the next tick.
		if (mode == THROWN) {
			THROWN_NOW.add(entity);
		}
		boolean inBattle = inBattle(entity);
		boolean effects = inBattle && com.mystaria.showtime.battle.BattleCameraDirector.ballEffects();
		Moment moment = mode == THROWN ? Moment.SEND_OUT : mode == RECALLED ? Moment.RECALL : null;
		if (moment == null) {
			return;
		}
		// The ball's trainer, which Ghost battles name; the server's order of these two values isn't known: else the owner.
		Entity thrower = entity.level().getEntity(entity.getPhasingTargetId());
		if (mode == RECALLED && inBattle) {
			boolean knockedOut = knockedOut();
			if (effects) {
				com.mystaria.showtime.move.BallRecall.start(entity, thrower, knockedOut);
			}
			if (knockedOut) {
				com.mystaria.showtime.battle.BattleCameraDirector.onKnockOut(entity);
			}
		}
		if (thrower != null) {
			TrainerEmotes.onBall(thrower, moment);
		} else if (owner != null) {
			TrainerEmotes.onBall(owner, moment);
		}
	}
}
