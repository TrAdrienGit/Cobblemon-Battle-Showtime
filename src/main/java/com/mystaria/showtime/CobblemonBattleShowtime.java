package com.mystaria.showtime;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

import com.mystaria.showtime.audio.BattleMusic;
import com.mystaria.showtime.battle.BattleCameraDirector;
import com.mystaria.showtime.battle.BattleCinematic;
import com.mystaria.showtime.battle.BattleScreenButtons;
import com.mystaria.showtime.battle.BattleShowtime;
import com.mystaria.showtime.classic.ClassicBattles;

/**
 * Cobblemon Battle Showtime: the staging of battles (camera, intros, set pieces, music), on its own or for Phantasmon. Wires
 * the tick, the render passes, the battle screen's buttons and the trainers' emotes. No commands (Adrien 2026-11-06: one menu, one key).
 */
public class CobblemonBattleShowtime implements ClientModInitializer {

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
	}
}
