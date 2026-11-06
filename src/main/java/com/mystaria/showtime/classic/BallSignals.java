package com.mystaria.showtime.classic;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;

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

	private BallSignals() {
	}

	public static void onBeamMode(PokemonEntity entity) {
		int mode = entity.getBeamMode();
		Integer last = LAST.put(entity, mode);
		if (last != null && last == mode) {
			return;
		}
		UUID owner = entity.getOwnerUUID();
		if (owner == null) {
			return;
		}
		if (mode == THROWN) {
			TrainerEmotes.onBall(owner, Moment.SEND_OUT);
		} else if (mode == RECALLED) {
			TrainerEmotes.onBall(owner, Moment.RECALL);
		}
	}
}
