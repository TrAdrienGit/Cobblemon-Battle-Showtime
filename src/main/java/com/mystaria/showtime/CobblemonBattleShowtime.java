package com.mystaria.showtime;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import com.mystaria.showtime.audio.BattleMusic;
import com.mystaria.showtime.battle.BattleCameraDirector;
import com.mystaria.showtime.battle.BattleCinematic;
import com.mystaria.showtime.battle.BattleScreenButtons;
import com.mystaria.showtime.battle.BattleShowtime;
import com.mystaria.showtime.classic.ClassicBattles;

/**
 * Cobblemon Battle Showtime: the staging of battles (camera, intros, set pieces, music), on its own or for Phantasmon. Wires
 * the tick, the render passes, the battle screen's buttons, the trainers' emotes and {@code /showtime preview}.
 */
public class CobblemonBattleShowtime implements ClientModInitializer {

	private static final List<String> TYPES = List.of("normal", "fire", "water", "grass", "electric", "ice", "fighting",
			"poison", "ground", "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy", "stellar");

	/**
	 * Showtime's only key (N, Adrien 2026-10-30: the modpack has keys enough): its menu — the trainer's animation
	 * profile, the music (Phantasmon's own music menu when it is installed) and the settings.
	 */
	private static final net.minecraft.client.KeyMapping MENU_KEY = new net.minecraft.client.KeyMapping(
			"key.cobblemon_battle_showtime.menu", org.lwjgl.glfw.GLFW.GLFW_KEY_N, "key.categories.cobblemon_battle_showtime");

	@Override
	public void onInitializeClient() {
		net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(MENU_KEY);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (MENU_KEY.consumeClick()) {
				client.setScreen(new com.mystaria.showtime.gui.ShowtimeMenuScreen(null));
			}
			BattleCinematic.tick();
			BattleShowtime.tick();
			BattleMusic.tick();
			ClassicBattles.tick();
			com.mystaria.showtime.emote.TrainerEmotes.tick();
			com.mystaria.showtime.ambiance.BattleAmbiance.tick();
			com.mystaria.showtime.move.MoveEffects.tick();
			com.mystaria.showtime.classic.BallSignals.tick();
			com.mystaria.showtime.move.BallOpening.tick();
			com.mystaria.showtime.move.BallRecall.tick();
			com.mystaria.showtime.move.FieldEffects.tick();
			com.mystaria.showtime.move.PokemonEffects.tick();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClassicBattles.reset();
			com.mystaria.showtime.emote.TrainerEmotes.reset();
			com.mystaria.showtime.ambiance.BattleAmbiance.end();
			com.mystaria.showtime.move.MoveEffects.reset();
			com.mystaria.showtime.move.BallOpening.reset();
			com.mystaria.showtime.move.BallRecall.reset();
			com.mystaria.showtime.move.FieldEffects.reset();
			com.mystaria.showtime.move.PokemonEffects.reset();
			BattleMusic.stop();
			BattleCameraDirector.stop();
			BattleShowtime.clear();
			BattleCinematic.stop();
		});
		WorldRenderEvents.AFTER_ENTITIES.register(BattleCinematic::renderWorld);
		WorldRenderEvents.AFTER_ENTITIES.register(com.mystaria.showtime.ambiance.BattleAmbiance::render);
		WorldRenderEvents.AFTER_ENTITIES.register(com.mystaria.showtime.move.MoveEffects::render);
		WorldRenderEvents.AFTER_ENTITIES.register(com.mystaria.showtime.move.BallOpening::render);
		WorldRenderEvents.AFTER_ENTITIES.register(com.mystaria.showtime.move.BallRecall::render);
		WorldRenderEvents.AFTER_ENTITIES.register(com.mystaria.showtime.move.FieldEffects::render);
		WorldRenderEvents.AFTER_ENTITIES.register(com.mystaria.showtime.move.PokemonEffects::render);
		HudRenderCallback.EVENT.register((graphics, tickCounter) -> {
			com.mystaria.showtime.ambiance.BattleAmbiance.renderHud(graphics);
			BattleCinematic.renderHud(graphics);
		});
		com.mystaria.showtime.ambiance.Ambiances.register();
		com.mystaria.showtime.move.MoveSpecs.register();
		BattleScreenButtons.register();
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommandManager.literal("showtime").then(ClientCommandManager.literal("preview")
						.then(ClientCommandManager.literal("ambiance")
								.then(ClientCommandManager.argument("ambiance", net.minecraft.commands.arguments.ResourceLocationArgument.id())
										.suggests((context, builder) -> SharedSuggestionProvider.suggest(
												com.mystaria.showtime.ambiance.Ambiances.ids(), builder))
										.executes(context -> {
											String id = context.getArgument("ambiance", net.minecraft.resources.ResourceLocation.class).toString();
											Minecraft.getInstance().tell(() -> com.mystaria.showtime.ambiance.BattleAmbiance.preview(id));
											return Command.SINGLE_SUCCESS;
										})))
						.then(ClientCommandManager.literal("status")
								.then(ClientCommandManager.argument("effect", StringArgumentType.word())
										.suggests((context, builder) -> SharedSuggestionProvider.suggest(
												com.mystaria.showtime.move.PokemonEffects.previewIds(), builder))
										.executes(context -> {
											String effect = StringArgumentType.getString(context, "effect");
											Minecraft.getInstance().tell(() -> com.mystaria.showtime.move.PokemonEffects.preview(effect));
											return Command.SINGLE_SUCCESS;
										})))
						.then(ClientCommandManager.literal("field")
								.then(ClientCommandManager.argument("effect", StringArgumentType.word())
										.suggests((context, builder) -> SharedSuggestionProvider.suggest(
												com.mystaria.showtime.move.FieldEffects.previewIds(), builder))
										.executes(context -> {
											String effect = StringArgumentType.getString(context, "effect");
											Minecraft.getInstance().tell(() -> com.mystaria.showtime.move.FieldEffects.preview(effect));
											return Command.SINGLE_SUCCESS;
										})))
						.then(ClientCommandManager.literal("move")
								.then(ClientCommandManager.argument("move", StringArgumentType.word())
										.suggests((context, builder) -> SharedSuggestionProvider.suggest(
												com.mystaria.showtime.move.MoveEffects.ids(), builder))
										.executes(context -> {
											String move = StringArgumentType.getString(context, "move");
											Minecraft.getInstance().tell(() -> com.mystaria.showtime.move.MoveEffects.preview(move));
											return Command.SINGLE_SUCCESS;
										})))
						.then(ClientCommandManager.literal("emote")
								.then(ClientCommandManager.argument("moment", StringArgumentType.word())
										.suggests((context, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(
												com.mystaria.showtime.emote.TrainerEmotes.Moment.values()).map(m -> m.id()), builder))
										.executes(context -> emote(context.getSource(), StringArgumentType.getString(context, "moment")))))
						.then(ClientCommandManager.literal("intro")
								.then(ClientCommandManager.argument("intro", StringArgumentType.word())
										.suggests((context, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(BattleCinematic.Intro.values())
												.map(intro -> intro.name().toLowerCase(Locale.ROOT)), builder))
										.executes(context -> intro(context.getSource(), StringArgumentType.getString(context, "intro")))))
						.then(ClientCommandManager.argument("kind", StringArgumentType.word())
								.suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("mega", "primal", "zmove", "tera"), builder))
								.executes(context -> setPiece(context.getSource(), StringArgumentType.getString(context, "kind"), null))
								.then(ClientCommandManager.argument("type", StringArgumentType.word())
										.suggests((context, builder) -> SharedSuggestionProvider.suggest(TYPES, builder))
										.executes(context -> setPiece(context.getSource(), StringArgumentType.getString(context, "kind"),
												StringArgumentType.getString(context, "type"))))))));
	}

	/** {@code /showtime preview emote <moment>}: a trainer's animation on the player themselves (third person). */
	private static int emote(FabricClientCommandSource source, String name) {
		var moment = Arrays.stream(com.mystaria.showtime.emote.TrainerEmotes.Moment.values())
				.filter(m -> m.id().equalsIgnoreCase(name)).findFirst().orElse(null);
		if (moment == null) {
			source.sendError(Component.translatable("cobblemon_battle_showtime.preview.unknown_emote", name));
			return 0;
		}
		if (!com.mystaria.showtime.emote.TrainerEmotes.available()) {
			source.sendError(Component.translatable("cobblemon_battle_showtime.preview.no_emotes"));
			return 0;
		}
		Minecraft.getInstance().tell(() -> com.mystaria.showtime.emote.TrainerEmotes.preview(moment));
		return Command.SINGLE_SUCCESS;
	}

	/** {@code /showtime preview intro <name>}: one of the battle intros alone. */
	private static int intro(FabricClientCommandSource source, String name) {
		BattleCinematic.Intro intro = Arrays.stream(BattleCinematic.Intro.values())
				.filter(i -> i.name().equalsIgnoreCase(name)).findFirst().orElse(null);
		if (intro == null) {
			source.sendError(Component.translatable("cobblemon_battle_showtime.preview.unknown_intro", name));
			return 0;
		}
		// Run from the chat screen: the intro's screen opens once the chat has closed.
		Minecraft.getInstance().tell(() -> BattleCinematic.preview(intro));
		return Command.SINGLE_SUCCESS;
	}

	/**
	 * {@code /showtime preview <mega|primal|zmove|tera> [type]}: a set piece on the nearest Pokémon in the world,
	 * nothing changes on it (no Mega model).
	 */
	private static int setPiece(FabricClientCommandSource source, String kindName, String type) {
		BattleShowtime.Kind kind = switch (kindName.toLowerCase(Locale.ROOT)) {
			case "mega" -> BattleShowtime.Kind.MEGA;
			case "primal" -> BattleShowtime.Kind.PRIMAL;
			case "zmove", "z" -> BattleShowtime.Kind.Z_MOVE;
			case "tera" -> BattleShowtime.Kind.TERA;
			default -> null;
		};
		if (kind == null) {
			source.sendError(Component.translatable("cobblemon_battle_showtime.preview.unknown_effect", kindName));
			return 0;
		}
		Minecraft mc = Minecraft.getInstance();
		PokemonEntity target = mc.level == null || mc.player == null ? null
				: mc.level.getEntitiesOfClass(PokemonEntity.class, mc.player.getBoundingBox().inflate(24))
						.stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(mc.player))).orElse(null);
		if (target == null) {
			source.sendError(Component.translatable("cobblemon_battle_showtime.preview.no_pokemon"));
			return 0;
		}
		// Run from the chat screen: once it has closed.
		mc.tell(() -> BattleShowtime.preview(kind, target, type == null ? null : type.toLowerCase(Locale.ROOT)));
		return Command.SINGLE_SUCCESS;
	}
}
