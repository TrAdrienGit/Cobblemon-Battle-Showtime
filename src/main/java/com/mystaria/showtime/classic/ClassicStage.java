package com.mystaria.showtime.classic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattleActor;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattleSide;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import com.mystaria.showtime.api.BattleStage;

/**
 * A Cobblemon battle's field: the server's own entities. A battle Pokémon is found by its uuid, which the server's
 * spawn packet gives the client entity ({@code SpawnPokemonPacket.pokemonUUID}); a trainer by its uuid among the
 * players, else among the loaded entities (an NPC). Cobblemon's client battle puts the local player's side first.
 */
final class ClassicStage implements BattleStage {

	private final UUID battleId;
	/** Battle Pokémon uuid → its entity, checked again on each use. */
	private final Map<UUID, PokemonEntity> found = new HashMap<>();

	ClassicStage(UUID battleId) {
		this.battleId = battleId;
	}

	@Override
	public Entity pokemonAt(String pnx) {
		return entityOf(ClassicBattles.battlePokemon(pnx));
	}

	@Override
	public Entity activePokemon(boolean local) {
		ClientBattleSide side = side(local);
		if (side == null) {
			return null;
		}
		for (ClientBattleActor actor : side.getActors()) {
			for (ActiveClientBattlePokemon active : actor.getActivePokemon()) {
				PokemonEntity entity = entityOf(active.getBattlePokemon());
				if (entity != null) {
					return entity;
				}
			}
		}
		return null;
	}

	@Override
	public Vec3 trainerPosition(boolean local) {
		ClientBattleSide side = side(local);
		ClientLevel level = Minecraft.getInstance().level;
		if (side == null || level == null || side.getActors().isEmpty()) {
			return null;
		}
		UUID trainer = side.getActors().get(0).getUuid();
		Entity player = level.getPlayerByUUID(trainer);
		if (player != null) {
			return player.position();
		}
		for (Entity entity : level.entitiesForRendering()) {
			if (trainer.equals(entity.getUUID())) {
				return entity.position();
			}
		}
		return null;
	}

	private ClientBattleSide side(boolean local) {
		ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
		if (battle == null || !battleId.equals(battle.getBattleId())) {
			return null;
		}
		return local ? battle.getSide1() : battle.getSide2();
	}

	private PokemonEntity entityOf(ClientBattlePokemon pokemon) {
		ClientLevel level = Minecraft.getInstance().level;
		if (pokemon == null || level == null || !battleId.equals(ClassicBattles.currentBattleId())) {
			return null;
		}
		UUID uuid = pokemon.getUuid();
		PokemonEntity known = found.get(uuid);
		if (known != null && !known.isRemoved() && known.level() == level) {
			return known;
		}
		found.remove(uuid);
		for (Entity entity : level.entitiesForRendering()) {
			if (entity instanceof PokemonEntity candidate && !candidate.isRemoved() && uuid.equals(candidate.getPokemon().getUuid())) {
				found.put(uuid, candidate);
				return candidate;
			}
		}
		return null;
	}
}
