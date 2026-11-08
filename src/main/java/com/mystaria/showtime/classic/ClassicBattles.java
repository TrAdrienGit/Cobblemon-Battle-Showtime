package com.mystaria.showtime.classic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattleActor;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattleSide;
import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import com.cobblemon.mod.common.net.messages.client.battle.BattleInitializePacket;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import com.mystaria.showtime.api.ShowtimeHooks;
import com.mystaria.showtime.api.Transformation;
import com.mystaria.showtime.audio.BattleMusic;
import com.mystaria.showtime.battle.BattleCameraDirector;
import com.mystaria.showtime.battle.BattleCinematic;
import com.mystaria.showtime.battle.BattleShowtime;
import com.mystaria.showtime.config.ShowtimeConfig;

/**
 * Cobblemon's own battles staged (phase S2, 2026-10-29): player against player and against NPC trainers, not
 * wild Pokémon. Fed by Cobblemon's client battle handlers ({@code Classic*HandlerMixin}), after they ran, from the
 * packets the server sends: battle start, choices, switches, faints, move and gimmick messages, end. Packets another
 * mod hands to those handlers for a battle it stages itself (Phantasmon's Ghost battles) are left alone
 * ({@link ShowtimeHooks#handlingForeignPacket}).
 *
 * <p>The server never waits for us: the PvP intro plays while the battle starts behind it (Cobblemon's battle screen
 * comes back when it ends), and the Mega / Z / Tera set pieces play over a battle that goes on. Client thread only.
 */
public final class ClassicBattles {

	private static final Logger LOG = LoggerFactory.getLogger(ClassicBattles.class);

	private static ClassicStage stage;
	/** The PvP intro took the screen from Cobblemon's battle screen: give it back once the intro is over. */
	private static boolean reopenBattleScreen;
	/** The victory / defeat music started: the battle's end lets it play out. */
	private static boolean outcomePlayed;

	private ClassicBattles() {
	}

	private static boolean ours() {
		return !ShowtimeHooks.handlingForeignPacket() && stage != null && BattleCameraDirector.stage() == stage;
	}

	/** A battle start not staged yet (see {@code ClassicBattleInitializeHandlerMixin}), or null. */
	private static BattleInitializePacket pendingInitialize;

	/** Before Cobblemon's handler: our own battles only (another mod's are told apart now, not later). */
	public static void initializing(BattleInitializePacket packet) {
		pendingInitialize = ShowtimeHooks.handlingForeignPacket() ? null : packet;
	}

	public static void onInitialize(BattleInitializePacket packet) {
		if (ShowtimeHooks.handlingForeignPacket()) {
			return;
		}
		pendingInitialize = null;
		try {
			end();
			List<BattleInitializePacket.BattleActorDTO> actors = new ArrayList<>(packet.getSide1().getActors());
			actors.addAll(packet.getSide2().getActors());
			if (actors.stream().anyMatch(actor -> actor.getType() == ActorType.WILD)) {
				return; // wild battles stay as they are
			}
			boolean pvp = actors.stream().allMatch(actor -> actor.getType() == ActorType.PLAYER);
			ShowtimeConfig.Kind kind = pvp ? ShowtimeConfig.Kind.PVP : ShowtimeConfig.Kind.NPC;
			stage = new ClassicStage(packet.getBattleId());
			BattleCameraDirector.start(stage, kind);
			BattleMusic.setKind(kind);
			ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
			if (pvp && ShowtimeConfig.battle(kind).intro && battle != null && !battle.getSpectating()
					&& !battle.getSide2().getActors().isEmpty()) {
				ClientBattleActor opponent = battle.getSide2().getActors().get(0);
				int own = CobblemonClient.INSTANCE.getStorage().getParty().getSlots().stream().filter(java.util.Objects::nonNull)
						.mapToInt(pokemon -> 1).sum();
				BattleCinematic.startIntro(opponent.getUuid(), opponent.getDisplayName().getString(), 6, own,
						BattleCinematic.Intro.of(new java.util.Random().nextInt(BattleCinematic.Intro.values().length)), null);
				BattleCinematic.skipSendOuts();
				reopenBattleScreen = true;
			} else {
				BattleMusic.play(BattleMusic.Track.BATTLE);
			}
			silencesCobblemonMusic(); // in case the server's music came first
		} catch (RuntimeException ex) {
			LOG.warn("Could not stage a Cobblemon battle", ex);
		}
	}

	public static void onChoosing() {
		if (ours()) {
			BattleCameraDirector.onChoosing();
		}
	}

	public static void onFocus(String pnx) {
		if (ours()) {
			BattleCameraDirector.onFocus(pnx);
		}
	}

	public static void onMessages(List<Component> messages) {
		if (!ours()) {
			return;
		}
		try {
			for (Component message : messages) {
				onMessage(message);
			}
		} catch (RuntimeException ex) {
			LOG.warn("Could not read a Cobblemon battle message", ex);
		}
	}

	public static void onEnd() {
		if (ours()) {
			end();
		}
	}

	/**
	 * Cobblemon's battle music, sent by the server ({@code BattleMusicPacket}): while it plays, Cobblemon blocks every
	 * other Music sound — ours included. In a battle staged here, it gives way whenever the resource pack has our own
	 * music for it; without one, Cobblemon's plays as usual.
	 */
	public static boolean silencesCobblemonMusic() {
		if (!ours() || !BattleMusic.wanted()) {
			return false; // music off for this kind of battle: Cobblemon's own plays
		}
		boolean replaced =BattleMusic.available(BattleMusic.Track.BATTLE) || BattleMusic.available(BattleMusic.Track.INTRO);
		if (replaced) {
			com.cobblemon.mod.common.client.sound.BattleMusicController.INSTANCE.endMusic();
		}
		return replaced;
	}

	/** Every client tick: a start whose handler was cancelled, and Cobblemon's battle screen back after the PvP intro. */
	public static void tick() {
		if (pendingInitialize != null && CobblemonClient.INSTANCE.getBattle() != null) {
			BattleInitializePacket packet = pendingInitialize;
			pendingInitialize = null;
			onInitialize(packet);
		}
		if (!reopenBattleScreen || BattleCinematic.introPlaying()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen != null && !(mc.screen instanceof com.mystaria.showtime.battle.BattleIntroScreen)) {
			reopenBattleScreen = false; // the player opened something else
			return;
		}
		if (mc.screen != null) {
			return; // the intro's screen closes on its own tick
		}
		reopenBattleScreen = false;
		ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
		if (battle != null && !battle.getMinimised()) {
			mc.setScreen(new BattleGUI());
		}
	}

	/** Disconnected: forget the battle (the director and the set pieces are cleared by the caller). */
	public static void reset() {
		stage = null;
		pendingInitialize = null;
		outcomePlayed = false;
		BattleMusic.setKind(null);
		reopenBattleScreen = false;
	}

	private static void end() {
		if (stage != null && BattleCameraDirector.stage() == stage) {
			BattleCameraDirector.stop();
			BattleShowtime.clear();
			BattleCinematic.stop();
			if (!outcomePlayed) {
				BattleMusic.stop(); // else the victory / defeat jingle ends on its own
			}
		}
		reset();
	}

	private static void onMessage(Component message) {
		if (!(message.getContents() instanceof TranslatableContents contents)) {
			return;
		}
		Object[] args = contents.getArgs();
		switch (contents.getKey()) {
			case "cobblemon.battle.used_move" -> {
				String user = pnxNamed(args, 0);
				if (user != null) {
					BattleCameraDirector.onAction(List.of(user), List.of());
				}
			}
			case "cobblemon.battle.used_move_on" -> {
				String user = pnxNamed(args, 0);
				String target = pnxNamed(args, 2);
				if (user != null) {
					BattleCameraDirector.onAction(List.of(user), target == null ? List.of() : List.of(target));
				}
			}
			// Victory / defeat jingle: the winners' names, or who gave up.
			case "cobblemon.battle.win" -> outcome(args.length > 0 && isOurs(args[0]));
			case "cobblemon.battle.forfeit" -> outcome(args.length > 0 && !isOurs(args[0]));
			case "cobblemon.battle.mega" ->transform(pnxNamed(args, 0), "mega");
			case "cobblemon.battle.zpower" -> transform(pnxNamed(args, 0), Transformation.Z_POWER);
			case "cobblemon.battle.terastallize" -> transform(pnxNamed(args, 0), Transformation.TERA_PREFIX + typeId(args.length > 1 ? args[1] : null));
			default -> {
			}
		}
	}

	/** Plays the victory or defeat music, once; it then outlives the battle's end (10 s at most). */
	private static void outcome(boolean won) {
		ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
		if (outcomePlayed || battle == null || battle.getSpectating()) {
			return;
		}
		outcomePlayed = true;
		BattleMusic.play(won ? BattleMusic.Track.VICTORY : BattleMusic.Track.DEFEAT);
	}

	/** Whether the message argument names our side (one of our actors' names in it: "A & B" for teams). */
	private static boolean isOurs(Object arg) {
		String names = arg instanceof Component component ? component.getString() : String.valueOf(arg);
		ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
		if (battle == null) {
			return false;
		}
		for (ClientBattleActor actor : battle.getSide1().getActors()) {
			for (String name : names.split(" & ")) {
				if (name.trim().equals(actor.getDisplayName().getString())) {
					return true;
				}
			}
		}
		return false;
	}

	private static void transform(String pnx, String aspect) {
		ClientBattlePokemon pokemon = pnx == null ? null : battlePokemon(pnx);
		if (pokemon == null) {
			return;
		}
		BattleShowtime.play(new Transformation(pnx, pokemon.getUuid().toString(),
				pokemon.getSpecies().getResourceIdentifier().getPath(), aspect));
	}

	/** The Tera type's id from the message's type argument ({@code cobblemon.type.fire} → {@code fire}). */
	private static String typeId(Object arg) {
		if (arg instanceof Component component && component.getContents() instanceof TranslatableContents contents) {
			String key = contents.getKey();
			return key.substring(key.lastIndexOf('.') + 1);
		}
		return arg == null ? "normal" : (arg instanceof Component c ? c.getString() : arg.toString()).toLowerCase(Locale.ROOT);
	}

	/**
	 * The battle position of the active Pokémon named by the message's argument {@code index}, or null. A trainer's
	 * Pokémon (a player's, an NPC's) is named "Owner's Pokémon" ({@code cobblemon.battle.owned_pokemon}, owner first):
	 * compared whole with the Pokémon's own name it never matched, so no move was filmed and no Mega / Z / Tera set
	 * piece played in Cobblemon's own battles (audit 2026-11-08).
	 */
	static String pnxNamed(Object[] args, int index) {
		if (args.length <= index) {
			return null;
		}
		String owner = null;
		String name = args[index] instanceof Component component ? component.getString() : String.valueOf(args[index]);
		if (args[index] instanceof Component component && component.getContents() instanceof TranslatableContents owned
				&& "cobblemon.battle.owned_pokemon".equals(owned.getKey()) && owned.getArgs().length > 1) {
			owner = owned.getArgs()[0] instanceof Component c ? c.getString() : String.valueOf(owned.getArgs()[0]);
			name = owned.getArgs()[1] instanceof Component c ? c.getString() : String.valueOf(owned.getArgs()[1]);
		}
		ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
		if (battle == null) {
			return null;
		}
		for (ClientBattleSide side : battle.getSides()) {
			for (ClientBattleActor actor : side.getActors()) {
				if (owner != null && !actor.getDisplayName().getString().equals(owner)) {
					continue;
				}
				char letter = 'a';
				for (ActiveClientBattlePokemon active : actor.getActivePokemon()) {
					ClientBattlePokemon pokemon = active.getBattlePokemon();
					if (pokemon != null && pokemon.getDisplayName().getString().equals(name)) {
						return actor.getShowdownId() + letter;
					}
					letter++;
				}
			}
		}
		return null;
	}

	static ClientBattlePokemon battlePokemon(String pnx) {
		ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
		if (battle == null || pnx == null || pnx.length() < 3) {
			return null;
		}
		for (ClientBattleSide side : battle.getSides()) {
			for (ClientBattleActor actor : side.getActors()) {
				if (!pnx.startsWith(actor.getShowdownId())) {
					continue;
				}
				int index = pnx.charAt(2) - 'a';
				List<ActiveClientBattlePokemon> active = actor.getActivePokemon();
				return index >= 0 && index < active.size() ? active.get(index).getBattlePokemon() : null;
			}
		}
		return null;
	}

	static UUID currentBattleId() {
		ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
		return battle == null ? null : battle.getBattleId();
	}
}
