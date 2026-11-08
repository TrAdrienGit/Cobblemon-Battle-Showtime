package com.mystaria.showtime.battle;

import java.util.List;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import com.mystaria.showtime.api.BattleButton;
import com.mystaria.showtime.api.ShowtimeHooks;
import com.mystaria.showtime.audio.BattleMusic;
import com.mystaria.showtime.audio.MusicSettings;
import com.mystaria.showtime.config.ShowtimeConfig;

/**
 * Over Cobblemon's battle screen while a battle is filmed, right side under the opponent's tile: a small music player
 * (Adrien 2026-10-29: the title, which opens the list of the folder's tracks to pick one; volume − / bar / +; next
 * track), then a column of buttons — Showtime's staged / free camera, plus those other mods add
 * ({@link ShowtimeHooks#addButton}: Phantasmon's turn timer). Drawn over the screen and clicked through Fabric's
 * screen events rather than added as widgets: Cobblemon rebuilds its own widgets at every sub-screen.
 */
public final class BattleScreenButtons {

	/**
	 * One compact card (Adrien 2026-11-08: fewer boxes on screen, room for Cobblemon Battle Extras): the track and
	 * "next" on one row, the volume under it, then the buttons, thinner. Cobblemon's colours, as Showtime's menu.
	 */
	private static final int W = 124;
	private static final int H = 14;
	/** The volume row. */
	private static final int CONTROLS_H = 10;
	private static final int GAP = 2;
	private static final int PAD = 3;
	/** Under Cobblemon's opponent tile (inset 10, tile 40 high)... */
	private static final int TOP_COBBLEMON = 56;
	/** ...or under Cobblemon Battle Extras' taller one, with its stat stages. */
	private static final int TOP_BATTLE_EXTRAS = 68;
	private static final boolean BATTLE_EXTRAS = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("cobblemon-battle-extras");
	private static final int TOP = (BATTLE_EXTRAS ? TOP_BATTLE_EXTRAS : TOP_COBBLEMON) + PAD;
	/** Clear of the screen edge, or of Battle Extras' column of the opponent's team down the right edge (26 wide). */
	private static final int RIGHT_INSET = BATTLE_EXTRAS ? 36 : 10;
	/** The track list, left of the column. */
	private static final int LIST_W = 160;
	private static final int ROW = 12;
	private static final int LIST_ROWS = 12;
	private static final int SMALL = 16;
	private static final int NEXT_W = 22;

	// Cobblemon's greys and teal, as Showtime's menu.
	private static final int CARD = 0xE02F2F2F;
	private static final int EDGE = 0xFF676767;
	private static final int BG_TOP = 0xFF4B4B4B;
	private static final int BG_BOTTOM = 0xFF3D3D3D;
	private static final int BG_HOVER_TOP = 0xFF5A5A5A;
	private static final int BG_HOVER_BOTTOM = 0xFF4B4B4B;
	private static final int SHINE = 0x30FFFFFF;

	private static boolean listOpen;
	private static int listScroll;

	private BattleScreenButtons() {
	}

	private static final net.minecraft.resources.ResourceLocation EARLY =
			net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon_battle_showtime", "early");

	public static void register() {
		ShowtimeHooks.addButton(new BattleButton(BattleButton.CAMERA_ORDER, () -> true,
				() -> Component.translatable(BattleCameraDirector.active() ? "cobblemon_battle_showtime.buttons.camera_staged"
						: "cobblemon_battle_showtime.buttons.camera_free").getString(),
				() -> BattleCameraDirector.active() ? BattleButton.GREEN : BattleButton.CYAN, () -> true, BattleCameraDirector::toggle));
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!"BattleGUI".equals(screen.getClass().getSimpleName())) {
				return;
			}
			// Drawn before the other mods' overlays (Battle Extras' team tooltips must cover the panel, Adrien 2026-11-08).
			var afterRender = ScreenEvents.afterRender(screen);
			afterRender.addPhaseOrdering(EARLY, net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE);
			afterRender.register(EARLY, (current, graphics, mouseX, mouseY, tickDelta) -> {
				if (BattleCameraDirector.inBattle() && !BattleShowtime.playing()) {
					render(current, graphics, mouseX, mouseY);
				}
			});
			ScreenMouseEvents.allowMouseClick(screen).register((current, mouseX, mouseY, button) ->
					button != 0 || !BattleCameraDirector.inBattle() || !click(current, mouseX, mouseY));
			ScreenMouseEvents.allowMouseScroll(screen).register((current, mouseX, mouseY, horizontal, vertical) -> {
				if (!listOpen || !BattleCameraDirector.inBattle() || !inList(current, mouseX, mouseY)) {
					return true;
				}
				listScroll = Mth.clamp(listScroll - (int) Math.signum(vertical), 0, Math.max(0, tracks().size() - LIST_ROWS));
				return false;
			});
		});
	}

	// =====================================================================
	// Layout
	// =====================================================================

	private static int x(Screen screen) {
		return screen.width - RIGHT_INSET - W;
	}

	private static int controlsY() {
		return TOP + H + GAP;
	}

	/** Top of the button column, under the player. */
	private static int buttonsTop() {
		return controlsY() + CONTROLS_H + GAP + 1;
	}

	private static int titleW() {
		return W - NEXT_W - GAP;
	}

	private static int buttonY(int index) {
		return buttonsTop() + index * (H + GAP);
	}

	private static int listX(Screen screen) {
		return x(screen) - LIST_W - 4;
	}

	private static List<BattleButton> shown() {
		return ShowtimeHooks.buttons().stream().filter(button -> button.visible().getAsBoolean()).toList();
	}

	private static List<ResourceLocation> tracks() {
		BattleMusic.Track track = BattleMusic.track();
		return track == null ? List.of() : BattleMusic.allFiles(track).stream().filter(MusicSettings::enabled).toList();
	}

	private static boolean inList(Screen screen, double mouseX, double mouseY) {
		int rows = Math.min(LIST_ROWS, tracks().size());
		return mouseX >= listX(screen) && mouseX < listX(screen) + LIST_W && mouseY >= TOP && mouseY < TOP + rows * ROW + 4;
	}

	private static boolean in(double mouseX, double mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	// =====================================================================
	// Clicks
	// =====================================================================

	/** True if the click was ours (Cobblemon's screen doesn't see it then). */
	private static boolean click(Screen screen, double mouseX, double mouseY) {
		int x = x(screen);
		if (listOpen && inList(screen, mouseX, mouseY)) {
			int index = listScroll + (int) ((mouseY - TOP - 2) / ROW);
			List<ResourceLocation> tracks = tracks();
			if (index >= 0 && index < tracks.size()) {
				BattleMusic.choose(tracks.get(index));
				listOpen = false;
				sound();
			}
			return true;
		}
		if (in(mouseX, mouseY, x + titleW() + GAP, TOP, NEXT_W, H)) {
			if (BattleMusic.skippable()) {
				BattleMusic.skip();
			}
			sound();
			return true;
		}
		if (in(mouseX, mouseY, x, TOP, titleW(), H)) {
			listOpen = !listOpen && !tracks().isEmpty();
			listScroll = 0;
			sound();
			return true;
		}
		int cy = controlsY();
		if (in(mouseX, mouseY, x, cy, W, CONTROLS_H)) {
			int barX = x + SMALL + GAP;
			int barW = W - (SMALL + GAP) * 2;
			if (mouseX < barX) {
				setVolume(ShowtimeConfig.musicVolume() - 0.1f);
			} else if (mouseX < barX + barW) {
				setVolume((float) ((mouseX - barX) / barW));
			} else {
				setVolume(ShowtimeConfig.musicVolume() + 0.1f);
			}
			sound();
			return true;
		}
		listOpen = false;
		List<BattleButton> shown = shown();
		for (int i = 0; i < shown.size(); i++) {
			if (in(mouseX, mouseY, x, buttonY(i), W, H)) {
				sound();
				if (shown.get(i).clickable().getAsBoolean()) {
					shown.get(i).onClick().run();
				}
				return true;
			}
		}
		return false;
	}

	private static void setVolume(float volume) {
		ShowtimeConfig.setMusicVolume(Math.round(Mth.clamp(volume, 0f, 1f) * 20) / 20f);
		ShowtimeConfig.save();
	}

	private static void sound() {
		Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
				net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
	}

	// =====================================================================
	// Rendering
	// =====================================================================

	private static void render(Screen screen, GuiGraphics g, int mouseX, int mouseY) {
		Font font = Minecraft.getInstance().font;
		int x = x(screen);
		g.pose().pushPose();
		g.pose().translate(0, 0, 400);

		List<BattleButton> shown = shown();
		// One card behind everything.
		int bottom = buttonY(shown.size()) - GAP;
		g.fill(x - PAD, TOP - PAD, x + W + PAD, bottom + PAD, CARD);
		outline(g, x - PAD, TOP - PAD, W + PAD * 2, bottom - TOP + PAD * 2, EDGE);

		// The player: title (opens the list) and next, then − volume +.
		ResourceLocation current = BattleMusic.current();
		String title = current == null ? Component.translatable("cobblemon_battle_showtime.player.none").getString()
				: "♪ " + BattleMusic.title(current);
		boolean hasList = !tracks().isEmpty();
		button(g, font, x, TOP, titleW(), H, in(mouseX, mouseY, x, TOP, titleW(), H) && hasList, title + (hasList ? (listOpen ? " ▲" : " ▼") : ""),
				current == null ? BattleButton.DIM : BattleButton.CYAN);
		int nextX = x + titleW() + GAP;
		button(g, font, nextX, TOP, NEXT_W, H, in(mouseX, mouseY, nextX, TOP, NEXT_W, H) && BattleMusic.skippable(),
				"⏭", BattleMusic.skippable() ? BattleButton.CYAN : BattleButton.DIM);
		int cy = controlsY();
		int barX = x + SMALL + GAP;
		int barW = W - (SMALL + GAP) * 2;
		button(g, font, x, cy, SMALL, CONTROLS_H, in(mouseX, mouseY, x, cy, SMALL, CONTROLS_H), "−", BattleButton.CYAN);
		g.fill(barX, cy, barX + barW, cy + CONTROLS_H, 0xFF1A1A1A);
		int filled = Math.round((barW - 2) * ShowtimeConfig.musicVolume());
		g.fill(barX + 1, cy + 1, barX + 1 + filled, cy + CONTROLS_H - 1, BattleButton.GREEN);
		String percent = Math.round(ShowtimeConfig.musicVolume() * 100) + " %";
		g.pose().pushPose();
		g.pose().translate(barX + barW / 2f, cy + 1.5f, 0);
		g.pose().scale(0.8f, 0.8f, 1f);
		g.drawString(font, percent, -font.width(percent) / 2, 0, 0xFFFFFFFF, true);
		g.pose().popPose();
		button(g, font, barX + barW + GAP, cy, SMALL, CONTROLS_H, in(mouseX, mouseY, barX + barW + GAP, cy, SMALL, CONTROLS_H), "+", BattleButton.CYAN);

		// The other buttons.
		for (int i = 0; i < shown.size(); i++) {
			BattleButton button = shown.get(i);
			int y = buttonY(i);
			button(g, font, x, y, W, H, in(mouseX, mouseY, x, y, W, H) && button.clickable().getAsBoolean(), button.label().get(),
					button.color().getAsInt());
		}

		// The track list.
		if (listOpen) {
			List<ResourceLocation> tracks = tracks();
			if (tracks.isEmpty()) {
				listOpen = false;
			} else {
				int lx = listX(screen);
				int rows = Math.min(LIST_ROWS, tracks.size());
				g.fillGradient(lx, TOP, lx + LIST_W, TOP + rows * ROW + 4, BG_TOP, BG_BOTTOM);
				outline(g, lx, TOP, LIST_W, rows * ROW + 4, EDGE);
				for (int i = 0; i < rows; i++) {
					ResourceLocation file = tracks.get(listScroll + i);
					int ry = TOP + 2 + i * ROW;
					boolean hovered = in(mouseX, mouseY, lx, ry, LIST_W, ROW);
					if (hovered) {
						g.fill(lx + 1, ry, lx + LIST_W - 1, ry + ROW, BG_HOVER_TOP);
					}
					String name = BattleMusic.title(file);
					name = font.width(name) > LIST_W - 10 ? font.plainSubstrByWidth(name, LIST_W - 14) + "…" : name;
					g.drawString(font, name, lx + 5, ry + 2, file.equals(current) ? BattleButton.GREEN : 0xFFFFFFFF, true);
				}
				if (tracks.size() > LIST_ROWS) {
					int barH = Math.max(8, rows * ROW * LIST_ROWS / tracks.size());
					int barY = TOP + 2 + (rows * ROW - barH) * listScroll / Math.max(1, tracks.size() - LIST_ROWS);
					g.fill(lx + LIST_W - 3, barY, lx + LIST_W - 1, barY + barH, BattleButton.CYAN);
				}
			}
		}
		g.pose().popPose();
	}

	private static void button(GuiGraphics g, Font font, int x, int y, int w, int h, boolean hovered, String label, int accent) {
		// A raised Cobblemon-grey key: light top edge, dark bottom edge, the accent on a thin left bar.
		g.fillGradient(x, y, x + w, y + h, hovered ? BG_HOVER_TOP : BG_TOP, hovered ? BG_HOVER_BOTTOM : BG_BOTTOM);
		g.fill(x, y, x + w, y + 1, SHINE);
		g.fill(x, y + h - 1, x + w, y + h, 0x60000000);
		g.fill(x, y, x + 2, y + h, accent);
		String text = font.width(label) > w - 10 ? font.plainSubstrByWidth(label, w - 14) + "…" : label;
		g.drawString(font, text, x + 2 + (w - 2 - font.width(text)) / 2, y + (h - 8) / 2 + 1, hovered ? 0xFFFFFFFF : accent, true);
	}

	private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + 1, color);
		g.fill(x, y + h - 1, x + w, y + h, color);
		g.fill(x, y, x + 1, y + h, color);
		g.fill(x + w - 1, y, x + w, y + h, color);
	}
}
