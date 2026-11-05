package com.mystaria.showtime.gui;

import java.util.Locale;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.mystaria.showtime.api.ShowtimeHooks;
import com.mystaria.showtime.config.ShowtimeConfig;

/**
 * Showtime's settings (phase S3), opened by Mod Menu: per kind of battle — Ghost (Phantasmon), PvP, NPC trainer —
 * the camera staged or free at the start, the intro (not for Ghost battles: their intro keeps both players in step),
 * our music; the music volume and the music menu; the trainer's animation profile. Saved on close.
 */
public final class SettingsScreen extends Screen {

	private final Screen parent;

	public SettingsScreen(Screen parent) {
		super(Component.translatable("cobblemon_battle_showtime.settings.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int center = width / 2;
		int colW = 100;
		int labelW = 110;
		int x0 = center - (labelW + 3 * (colW + 4)) / 2 + labelW;
		int y = 56;
		for (ShowtimeConfig.Kind kind : ShowtimeConfig.Kind.values()) {
			ShowtimeConfig.Battle battle = ShowtimeConfig.battle(kind);
			addRenderableWidget(CycleButton.onOffBuilder(battle.stagedCamera)
					.create(x0, y, colW, 20, Component.translatable("cobblemon_battle_showtime.settings.camera"),
							(button, value) -> battle.stagedCamera = value));
			if (kind != ShowtimeConfig.Kind.GHOST) {
				addRenderableWidget(CycleButton.onOffBuilder(battle.intro)
						.create(x0 + colW + 4, y, colW, 20, Component.translatable("cobblemon_battle_showtime.settings.intro"),
								(button, value) -> battle.intro = value));
			}
			addRenderableWidget(CycleButton.onOffBuilder(battle.music)
					.create(x0 + 2 * (colW + 4), y, colW, 20, Component.translatable("cobblemon_battle_showtime.settings.music"),
							(button, value) -> battle.music = value));
			y += 24;
		}
		addRenderableWidget(new MusicScreen.VolumeSlider(center - 154, y + 8, 150, 20));
		addRenderableWidget(Button.builder(Component.translatable("cobblemon_battle_showtime.settings.tracks"),
				b -> minecraft.setScreen(ShowtimeHooks.musicScreen(this))).bounds(center + 4, y + 8, 150, 20).build());
		java.util.List<String> ambiances = new java.util.ArrayList<>();
		ambiances.add(com.mystaria.showtime.ambiance.BattleAmbiance.NONE);
		ambiances.addAll(com.mystaria.showtime.ambiance.Ambiances.ids());
		String current = ambiances.contains(ShowtimeConfig.ambiance()) ? ShowtimeConfig.ambiance() : ambiances.get(0);
		addRenderableWidget(CycleButton.<String>builder(SettingsScreen::ambianceName).withValues(ambiances).withInitialValue(current)
				.create(center - 154, y + 32 + 24, 308, 20, Component.translatable("cobblemon_battle_showtime.settings.ambiance"),
						(button, value) -> ShowtimeConfig.setAmbiance(value)));
		addRenderableWidget(Button.builder(Component.translatable("cobblemon_battle_showtime.settings.profile"),
				b -> minecraft.setScreen(new TrainerProfileScreen(this))).bounds(center - 154, y + 32, 308, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(center - 50, y + 64 + 24, 100, 20).build());
	}

	static Component ambianceName(String id) {
		return com.mystaria.showtime.ambiance.Ambiances.displayName(id);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		g.drawCenteredString(font, title, width / 2, 16, 0xFFFFFFFF);
		int colW = 100;
		int labelW = 110;
		int x0 = width / 2 - (labelW + 3 * (colW + 4)) / 2;
		int y = 62;
		for (ShowtimeConfig.Kind kind : ShowtimeConfig.Kind.values()) {
			g.drawString(font, Component.translatable("cobblemon_battle_showtime.settings.kind." + kind.name().toLowerCase(Locale.ROOT)),
					x0, y, 0xFFFFFFFF, true);
			y += 24;
		}
	}

	@Override
	public void onClose() {
		ShowtimeConfig.save();
		minecraft.setScreen(parent);
	}
}
