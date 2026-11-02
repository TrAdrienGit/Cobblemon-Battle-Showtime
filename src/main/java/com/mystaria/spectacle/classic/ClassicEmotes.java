package com.mystaria.spectacle.classic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattleActor;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattleSide;
import com.cobblemon.mod.common.net.messages.client.battle.BattleInitializePacket;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import com.mystaria.spectacle.emote.TrainerEmotes;
import com.mystaria.spectacle.emote.TrainerEmotes.Moment;

/**
 * The trainers' emotes ({@link TrainerEmotes}) fed by Cobblemon's battle packets — every battle with player trainers,
 * Spectacle's staged ones and those another mod hands to Cobblemon's handlers (Phantasmon's Ghost battles) alike; wild
 * battles stay as they are. Client thread only.
 */
public final class ClassicEmotes {

	private static final Logger LOG = LoggerFactory.getLogger(ClassicEmotes.class);

	private ClassicEmotes() {
	}

	/** The battle starts: its player trainers send out their first Pokémon. */
	public static void onInitialize(BattleInitializePacket packet) {
		List<BattleInitializePacket.BattleActorDTO> actors = new ArrayList<>(packet.getSide1().getActors());
		actors.addAll(packet.getSide2().getActors());
		if (actors.stream().anyMatch(actor -> actor.getType() == ActorType.WILD)) {
			return;
		}
		Map<UUID, List<UUID>> trainers = new HashMap<>();
		addSide(trainers, packet.getSide1(), packet.getSide2());
		addSide(trainers, packet.getSide2(), packet.getSide1());
		TrainerEmotes.start(trainers);
		com.mystaria.spectacle.ambiance.BattleAmbiance.start(trainers.keySet());
		com.mystaria.spectacle.ambiance.BattleAmbiance.moment("send_out");
	}

	/** The side's player trainers, each facing the other side's trainers. */
	private static void addSide(Map<UUID, List<UUID>> trainers, BattleInitializePacket.BattleSideDTO side,
			BattleInitializePacket.BattleSideDTO other) {
		List<UUID> opponents = other.getActors().stream().map(BattleInitializePacket.BattleActorDTO::getUuid).toList();
		for (BattleInitializePacket.BattleActorDTO actor : side.getActors()) {
			if (actor.getType() == ActorType.PLAYER) {
				trainers.put(actor.getUuid(), opponents);
			}
		}
	}

	/**
	 * A Pokémon comes in at {@code pnx}, before Cobblemon's handler ran: one still standing there is called back first
	 * (a switch), a fainted one was already gone.
	 */
	public static void onSwitch(String pnx) {
		try {
			ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
			if (battle == null) {
				return;
			}
			var found = battle.getPokemonFromPNX(pnx);
			ClientBattleActor actor = found.getFirst();
			ClientBattlePokemon leaving = found.getSecond().getBattlePokemon();
			if (leaving != null && leaving.getHpValue() > 0) {
				TrainerEmotes.act(actor.getUuid(), Moment.RECALL, Moment.SEND_OUT);
				com.mystaria.spectacle.ambiance.BattleAmbiance.moment("send_out");
			} else {
				TrainerEmotes.act(actor.getUuid(), Moment.SEND_OUT);
				com.mystaria.spectacle.ambiance.BattleAmbiance.moment("send_out");
			}
		} catch (RuntimeException ex) {
			LOG.debug("No trainer emote for the switch at {}", pnx, ex);
		}
	}

	/** Moves ordered, the winners and the losers. */
	public static void onMessages(List<Component> messages) {
		com.mystaria.spectacle.move.FieldEffects.onMessages(messages);
		com.mystaria.spectacle.move.PokemonEffects.onMessages(messages);
		try {
			for (Component message : messages) {
				if (message.getContents() instanceof TranslatableContents contents) {
					onMessage(contents.getKey(), contents.getArgs());
				}
			}
		} catch (RuntimeException ex) {
			LOG.debug("No trainer emote for a battle message", ex);
		}
	}

	public static void onEnd() {
		com.mystaria.spectacle.move.FieldEffects.reset();
		com.mystaria.spectacle.move.PokemonEffects.reset();
		TrainerEmotes.end();
		com.mystaria.spectacle.ambiance.BattleAmbiance.end();
	}

	private static void onMessage(String key, Object[] args) {
		switch (key) {
			case "cobblemon.battle.used_move", "cobblemon.battle.used_move_on" -> {
				TrainerEmotes.act(trainerOfUser(args), Moment.ATTACK);
				com.mystaria.spectacle.ambiance.BattleAmbiance.moment("attack");
			}
			case "cobblemon.battle.crit", "cobblemon.battle.crit_spread" -> com.mystaria.spectacle.ambiance.BattleAmbiance.moment("crit");
			case "cobblemon.battle.fainted" -> com.mystaria.spectacle.ambiance.BattleAmbiance.moment("faint");
			case "cobblemon.battle.win" -> {
				outcome(args, true);
				com.mystaria.spectacle.ambiance.BattleAmbiance.moment("victory");
			}
			case "cobblemon.battle.forfeit" -> outcome(args, false);
			default -> {
			}
		}
	}

	/** {@code args[0]} names the winners ({@code named} true) or who gave up: they and the others win or lose. */
	private static void outcome(Object[] args, boolean namedWin) {
		ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
		if (battle == null || args.length == 0) {
			return;
		}
		String names = args[0] instanceof Component component ? component.getString() : String.valueOf(args[0]);
		for (ClientBattleSide side : battle.getSides()) {
			for (ClientBattleActor actor : side.getActors()) {
				boolean named = false;
				for (String name : names.split(" & ")) {
					named |= name.trim().equals(actor.getDisplayName().getString());
				}
				TrainerEmotes.act(actor.getUuid(), named == namedWin ? Moment.VICTORY : Moment.DEFEAT);
			}
		}
	}

	/**
	 * The trainer of the Pokémon named by a move message's first argument: a player's Pokémon is named "X's Pokémon"
	 * ({@code cobblemon.battle.owned_pokemon}, the owner first — Adrien's test 2026-10-30: comparing that with the
	 * Pokémon's own name never matched), any other by its name alone.
	 */
	private static UUID trainerOfUser(Object[] args) {
		if (args.length == 0) {
			return null;
		}
		if (args[0] instanceof Component component && component.getContents() instanceof TranslatableContents owned
				&& "cobblemon.battle.owned_pokemon".equals(owned.getKey()) && owned.getArgs().length > 0) {
			Object owner = owned.getArgs()[0];
			String ownerName = owner instanceof Component c ? c.getString() : String.valueOf(owner);
			ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
			if (battle != null) {
				for (ClientBattleSide side : battle.getSides()) {
					for (ClientBattleActor actor : side.getActors()) {
						if (actor.getDisplayName().getString().equals(ownerName)) {
							return actor.getUuid();
						}
					}
				}
			}
		}
		return trainerOf(ClassicBattles.pnxNamed(args, 0));
	}

	/** The trainer of the Pokémon at {@code pnx} ({@code p1a}…), or null. */
	private static UUID trainerOf(String pnx) {
		ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
		if (battle == null || pnx == null) {
			return null;
		}
		for (ClientBattleSide side : battle.getSides()) {
			for (ClientBattleActor actor : side.getActors()) {
				if (pnx.startsWith(actor.getShowdownId())) {
					return actor.getUuid();
				}
			}
		}
		return null;
	}

}
