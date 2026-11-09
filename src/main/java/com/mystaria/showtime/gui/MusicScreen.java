package com.mystaria.showtime.gui;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import com.mystaria.showtime.audio.BattleMusic;
import com.mystaria.showtime.audio.MusicSettings;
import com.mystaria.showtime.config.ShowtimeConfig;

/**
 * Showtime's own music menu (2026-10-29: for players without Phantasmon, which brings its own): one tab per
 * folder of the resource pack, each file ticked or not (unticked files are never picked) and playable, the music
 * volume. Plain Minecraft widgets. Opened from Showtime's menu (key N), the settings screen, or by Mod Menu.
 */
public final class MusicScreen extends Screen {

	private static final int ROWS = 8;
	private static final int ROW_H = 22;

	private final Screen parent;
	private BattleMusic.Track tab = BattleMusic.Track.BATTLE;
	private int page;

	public MusicScreen(Screen parent) {
		super(Component.translatable("cobblemon_battle_showtime.music.screen.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int center = width / 2;
		BattleMusic.Track[] tracks = BattleMusic.shownTracks();
		int tabW = 70;
		int tabsX = center - tracks.length * (tabW + 2) / 2;
		for (int i = 0; i < tracks.length; i++) {
			BattleMusic.Track track = tracks[i];
			Button button = Button.builder(Component.translatable("cobblemon_battle_showtime.music.tab." + track.name().toLowerCase(Locale.ROOT))
					.append(" (" + BattleMusic.allFiles(track).size() + ")"), b -> {
						tab = track;
						page = 0;
						rebuildWidgets();
					}).bounds(tabsX + i * (tabW + 2), 32, tabW, 20).build();
			button.active = track != tab;
			addRenderableWidget(button);
		}

		List<ResourceLocation> files = BattleMusic.allFiles(tab);
		int pages = Math.max(1, (files.size() + ROWS - 1) / ROWS);
		page = Mth.clamp(page, 0, pages - 1);
		int top = 60;
		for (int i = 0; i < ROWS; i++) {
			int index = page * ROWS + i;
			if (index >= files.size()) {
				break;
			}
			ResourceLocation file = files.get(index);
			int y = top + i * ROW_H;
			boolean enabled = MusicSettings.enabled(file);
			addRenderableWidget(Button.builder(Component.literal((enabled ? "☑ " : "☐ ") + BattleMusic.title(file)), b -> {
				MusicSettings.setEnabled(file, !MusicSettings.enabled(file));
				rebuildWidgets();
			}).bounds(center - 150, y, 240, 20).build());
			addRenderableWidget(Button.builder(Component.translatable(BattleMusic.previewing(file)
					? "cobblemon_battle_showtime.music.screen.stop" : "cobblemon_battle_showtime.music.screen.listen"), b -> {
						if (BattleMusic.previewing(file)) {
							BattleMusic.endPreview();
						} else {
							BattleMusic.preview(file);
						}
						rebuildWidgets();
					}).bounds(center + 94, y, 56, 20).build());
		}
		int bottom = top + ROWS * ROW_H + 4;
		if (pages > 1) {
			addRenderableWidget(Button.builder(Component.literal("<"), b -> {
				page--;
				rebuildWidgets();
			}).bounds(center - 150, bottom, 20, 20).build()).active = page > 0;
			addRenderableWidget(Button.builder(Component.literal(">"), b -> {
				page++;
				rebuildWidgets();
			}).bounds(center + 130, bottom, 20, 20).build()).active = page < pages - 1;
		}
		addRenderableWidget(new VolumeSlider(center - 100, bottom, 200, 20));
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(center - 50, bottom + 26, 100, 20).build());
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFFFF);
		if (BattleMusic.allFiles(tab).isEmpty()) {
			g.drawCenteredString(font, Component.translatable("cobblemon_battle_showtime.music.screen.empty",
					"sounds/music/" + tab.name().toLowerCase(Locale.ROOT)), width / 2, 90, 0xFFA0A0A0);
		}
	}

	@Override
	public void onClose() {
		BattleMusic.endPreview();
		minecraft.setScreen(parent);
	}

	/** The music's own volume, on top of Minecraft's Music slider. */
	static final class VolumeSlider extends AbstractSliderButton {

		VolumeSlider(int x, int y, int w, int h) {
			super(x, y, w, h, Component.empty(), ShowtimeConfig.musicVolume());
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable("cobblemon_battle_showtime.music.volume", Math.round(value * 100)));
		}

		@Override
		protected void applyValue() {
			ShowtimeConfig.setMusicVolume((float) value);
		}

		@Override
		public void onRelease(double mouseX, double mouseY) {
			super.onRelease(mouseX, mouseY);
			ShowtimeConfig.save();
		}
	}
}
