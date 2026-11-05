package com.mystaria.showtime.gui;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

import com.mystaria.showtime.config.ShowtimeConfig;
import com.mystaria.showtime.emote.TrainerEmotes;
import com.mystaria.showtime.emote.TrainerEmotes.Moment;

/**
 * The trainer's animation profile (plan-showtime.md, phase E2, Adrien 2026-10-30): for each moment of a battle — guard,
 * send-out, recall, attack, victory, defeat — the variant this player's trainer acts, chosen from a list; the selected
 * one plays over and over on the player's own model beside the list. Saved in Showtime's settings; other players see
 * it through Phantasmon (phase E3).
 */
public final class TrainerProfileScreen extends Screen {

	private static final int LIST_W = 170;
	private static final int ROW_H = 22;

	private final Screen parent;
	private Moment moment = Moment.GUARD;

	public TrainerProfileScreen(Screen parent) {
		super(Component.translatable("cobblemon_battle_showtime.profile.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int tabW = Math.min(90, (width - 20) / Moment.values().length - 4);
		int tabsX = width / 2 - (Moment.values().length * (tabW + 4) - 4) / 2;
		for (int i = 0; i < Moment.values().length; i++) {
			Moment tab = Moment.values()[i];
			Button button = Button.builder(Component.translatable("cobblemon_battle_showtime.profile.moment." + tab.id()), b -> {
				moment = tab;
				rebuildWidgets();
				preview();
			}).bounds(tabsX + i * (tabW + 4), 34, tabW, 20).build();
			button.active = tab != moment;
			addRenderableWidget(button);
		}
		if (available()) {
			String chosen = chosen();
			List<String> variants = TrainerEmotes.variants(moment);
			int listX = width / 2 - LIST_W - 10;
			for (int i = 0; i < variants.size(); i++) {
				String name = variants.get(i);
				Component label = name.equals(chosen) ? Component.literal("▶ ").append(label(name)) : label(name);
				addRenderableWidget(Button.builder(label, b -> {
					ShowtimeConfig.setTrainerAnimation(moment.id(), name);
					com.mystaria.showtime.api.ShowtimeHooks.localProfileChanged();
					rebuildWidgets();
					preview();
				}).bounds(listX, 66 + i * ROW_H, LIST_W, 20).build());
			}
		}
		addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
				.bounds(width / 2 - 50, height - 28, 100, 20).build());
		if (!previewed) {
			previewed = true;
			preview();
		}
	}

	private boolean previewed;

	private static boolean available() {
		return TrainerEmotes.available() && net.minecraft.client.Minecraft.getInstance().player != null;
	}

	private String chosen() {
		return TrainerEmotes.animationFor(minecraft.player.getUUID(), moment);
	}

	private void preview() {
		if (available()) {
			TrainerEmotes.previewLoop(chosen());
		}
	}

	/** {@code cobblemon_battle_showtime.emote.<name>}, else the name made readable ({@code send_out_spin} → "Spin"). */
	private static Component label(String name) {
		String style = name.contains("_") ? name.substring(name.indexOf('_') + 1) : name;
		for (Moment m : Moment.values()) {
			if (name.startsWith(m.id() + "_")) {
				style = name.substring(m.id().length() + 1);
			}
		}
		String readable = style.replace('_', ' ');
		readable = readable.isEmpty() ? name : readable.substring(0, 1).toUpperCase(Locale.ROOT) + readable.substring(1);
		return Component.translatableWithFallback("cobblemon_battle_showtime.emote." + name, readable);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFFFF);
		if (!available()) {
			g.drawCenteredString(font, Component.translatable(TrainerEmotes.available()
					? "cobblemon_battle_showtime.profile.in_world" : "cobblemon_battle_showtime.profile.no_emotes"),
					width / 2, height / 2, 0xFFFF8080);
			return;
		}
		// The player's own model, three-quarter view, playing the selected animation.
		int x1 = width / 2 + 10;
		int x2 = x1 + 160;
		int y1 = 62;
		int y2 = Math.min(height - 36, y1 + 200);
		g.fill(x1, y1, x2, y2, 0x60000000);
		InventoryScreen.renderEntityInInventoryFollowsMouse(g, x1, y1, x2, y2, (y2 - y1) / 3, 0.0625f,
				(x1 + x2) / 2f - 60, y1 + (y2 - y1) * 0.3f, minecraft.player);
	}

	@Override
	public boolean isPauseScreen() {
		return false; // the preview plays on: the world keeps ticking
	}

	@Override
	public void onClose() {
		ShowtimeConfig.save();
		TrainerEmotes.stopPreview();
		minecraft.setScreen(parent);
	}
}
