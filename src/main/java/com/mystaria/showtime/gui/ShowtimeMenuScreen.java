package com.mystaria.showtime.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.cobblemon.mod.common.client.gui.ExitButton;
import com.cobblemon.mod.common.client.render.RenderHelperKt;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import net.fabricmc.loader.api.FabricLoader;

import com.mystaria.showtime.ambiance.Ambiances;
import com.mystaria.showtime.ambiance.BattleAmbiance;
import com.mystaria.showtime.audio.BattleMusic;
import com.mystaria.showtime.audio.MusicSettings;
import com.mystaria.showtime.battle.BattleCameraDirector;
import com.mystaria.showtime.config.ShowtimeConfig;
import com.mystaria.showtime.emote.TrainerEmotes;
import com.mystaria.showtime.emote.TrainerEmotes.Moment;

/**
 * Showtime's own menu (key N and Mod Menu, 2026-11-06), laid out like a web app: a sidebar of categories, then
 * sections, then a list of choices. It borrows Cobblemon's palette, arrows and back button, not its PC's layout. For
 * animations and ambiances, a window on the real world shows the trainer playing the selection ({@link MenuPreview}).
 */
public final class ShowtimeMenuScreen extends Screen {

	private static final int DARK = 0xFF2F2F2F;
	private static final int PANEL = 0xFF3D3D3D;
	private static final int RAISED = 0xFF4B4B4B;
	private static final int EDGE = 0xFF676767;
	private static final int LIGHT = 0xFF8D8D8D;
	private static final int ACCENT = 0xFF38C4BC;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int MUTED = 0xFFC6C6C6;
	private static final int VEIL = 0xA0000000;
private static final int BLACK = 0xFF1A1A1A;
	private static final int SHINE = 0x30FFFFFF;
	private static final int SHADE = 0x50000000;
	private static final int ACCENT_DIM = 0xFF1F7F7A;
	private static final int STAR = 0xFFFFD84A;

	private static final int HEADER_H = 24;
	private static final int SIDEBAR_W = 88;
	private static final int NAV_H = 24;
	private static final int PREVIEW_W = 156;
	private static final int ROW_H = 18;
	private static final int ROW_PITCH = 20;
	private static final int CHIP_H = 14;
	private static final int CHIP_COLUMNS = 3;
	/** The music page's generation filters: All, I to IX, Other. */
	private static final int FILTER_COLUMNS = 6;
	private static final int BAR_W = 5;
	private static final ResourceLocation UNIFORM = ResourceLocation.parse("uniform");
	private static final String KEY = "cobblemon_battle_showtime.";
	private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};

	private enum Category {
		SETTINGS, ANIMATIONS, AMBIANCES, MUSIC
	}

	private enum Style {
		NOTE, SELECT, SWITCH, SLIDER, BUTTONS
	}

	/** Music page filter: the favourite tracks. */
	private static final int FAVORITES = -1;

	/** A line of the list. {@code on}: selected or switched on; {@code value}: the slider's position. */
	private record Row(Style style, Component text, boolean on, float value, Runnable click, Runnable side, Component sideText,
			Runnable star, boolean starred) {

		Row(Style style, Component text, boolean on, float value, Runnable click, Runnable side, Component sideText) {
			this(style, text, on, value, click, side, sideText, null, false);
		}
	}

	private record Section(Component label, boolean selected, Runnable click) {
	}

	private final Screen parent;
	private final List<Row> rows = new ArrayList<>();
	private final List<Section> sections = new ArrayList<>();
	private final List<Section> filters = new ArrayList<>();
	private Category category = Category.SETTINGS;
	private ShowtimeConfig.Kind kind = ShowtimeConfig.Kind.PVP;
	private Moment moment = Moment.GUARD;
	private BattleMusic.Track track = BattleMusic.Track.BATTLE;
	/** Rows scrolled out of sight at the top, in pixels. */
	private int scroll;
	/** Music page: 0 = every game, 1 to 9 = that generation, BattleMusic.OTHER, or FAVORITES. */
	private int generation;
	private boolean draggingBar;
	private int barGrab;
	private int left;
	private int top;
	private int right;
	private int bottom;
	private boolean savedHideGui;
	private boolean hidGui;
	private boolean draggingVolume;

	public ShowtimeMenuScreen(Screen parent) {
		super(Component.literal("Cobblemon Battle Showtime"));
		this.parent = parent;
	}

	private boolean inWorld() {
		return minecraft != null && minecraft.player != null && minecraft.level != null;
	}

	private boolean canPreview() {
		return inWorld() && !BattleCameraDirector.inBattle();
	}

	private boolean hasWindow() {
		return category == Category.ANIMATIONS || category == Category.AMBIANCES;
	}

	// ---------------------------------------------------------------- layout

	private int contentX() {
		return left + SIDEBAR_W + 10;
	}

	private int contentRight() {
		return hasWindow() ? windowX() - 13 : right - 10;
	}

	private int windowX() {
		return right - PREVIEW_W - 13;
	}

	private int windowY() {
		return top + HEADER_H + 24;
	}

	private int windowBottom() {
		return bottom - 12;
	}

	private int chipsY() {
		return top + HEADER_H + 24;
	}

	private int filtersY() {
		int lines = (sections.size() + CHIP_COLUMNS - 1) / CHIP_COLUMNS;
		return chipsY() + lines * (CHIP_H + 3) + (sections.isEmpty() ? 0 : 2);
	}

	private int rowsY() {
		int lines = (filters.size() + FILTER_COLUMNS - 1) / FILTER_COLUMNS;
		int y = filtersY() + lines * (CHIP_H + 3);
		return sections.isEmpty() && filters.isEmpty() ? y : y + 3;
	}

	private int rowsBottom() {
		return bottom - 8;
	}

	private int maxScroll() {
		return Math.max(0, rows.size() * ROW_PITCH - 2 - (rowsBottom() - rowsY()));
	}

	/** Rows leave room for the scrollbar when they don't all fit. */
	private int rowsRight() {
		return maxScroll() > 0 ? contentRight() - BAR_W - 4 : contentRight();
	}

	// ---------------------------------------------------------------- content

	@Override
	protected void init() {
		int w = Math.min(width - 16, 480);
		int h = Math.min(height - 16, 300);
		left = (width - w) / 2;
		top = (height - h) / 2;
		right = left + w;
		bottom = top + h;
		addRenderableWidget(new ExitButton(right - 32, top + 6, b -> onClose()));
		if (inWorld() && !hidGui) {
			hidGui = true;
			savedHideGui = minecraft.options.hideGui;
			minecraft.options.hideGui = true;
		}
		rebuild();
		applyPreview();
	}

	private void scrollTo(int pixels) {
		scroll = Mth.clamp(pixels, 0, maxScroll());
	}

	private void select(Category chosen) {
		if (chosen == category) {
			return;
		}
		category = chosen;
		scroll = 0;
		BattleMusic.endPreview();
		rebuild();
		applyPreview();
	}

	private void rebuild() {
		rows.clear();
		sections.clear();
		filters.clear();
		switch (category) {
			case SETTINGS -> settings();
			case ANIMATIONS -> animations();
			case AMBIANCES -> ambiances();
			case MUSIC -> music();
		}
		scrollTo(scroll);
	}

	private void settings() {
		for (ShowtimeConfig.Kind each : ShowtimeConfig.Kind.values()) {
			if (each == ShowtimeConfig.Kind.GHOST && !FabricLoader.getInstance().isModLoaded("phantasmon")) {
				continue;
			}
			sections.add(new Section(Component.translatable(KEY + "settings.kind." + each.name().toLowerCase(Locale.ROOT)),
					each == kind, () -> {
						kind = each;
						scroll = 0;
						rebuild();
					}));
		}
		ShowtimeConfig.Battle battle = ShowtimeConfig.battle(kind);
		rows.add(setting("settings.camera", battle.stagedCamera, () -> battle.stagedCamera = !battle.stagedCamera));
		if (kind != ShowtimeConfig.Kind.GHOST) { // a Ghost battle's intro keeps both players in step
			rows.add(setting("settings.intro", battle.intro, () -> battle.intro = !battle.intro));
		}
		rows.add(setting("settings.music", battle.music, () -> battle.music = !battle.music));
		rows.add(setting("settings.ball_effects", battle.ballEffects, () -> battle.ballEffects = !battle.ballEffects));
		rows.add(new Row(Style.SLIDER, Component.translatable(KEY + "music.volume", Math.round(ShowtimeConfig.musicVolume() * 100)),
				false, ShowtimeConfig.musicVolume(), null, null, null));
		// Latest → v1 → v2 → … → latest: "latest" follows each new camera, a number stays on that one.
		int chosen = ShowtimeConfig.cameraVersion();
		int latest = BattleCameraDirector.LATEST_CAMERA;
		Component version = chosen <= 0 || chosen > latest
				? Component.translatable(KEY + "settings.camera_latest", latest)
				: Component.translatable(KEY + "settings.camera_numbered", chosen);
		rows.add(new Row(Style.SELECT, Component.translatable(KEY + "settings.camera_version", version), true, 0, () -> {
			int now = ShowtimeConfig.cameraVersion();
			ShowtimeConfig.setCameraVersion(now <= 0 || now > latest ? 1 : now >= latest ? 0 : now + 1);
			ShowtimeConfig.save();
			rebuild();
		}, null, null));
		rows.add(setting("settings.check_updates", ShowtimeConfig.checkUpdates(),
				() -> ShowtimeConfig.setCheckUpdates(!ShowtimeConfig.checkUpdates())));
	}

	private Row setting(String key, boolean on, Runnable flip) {
		return new Row(Style.SWITCH, Component.translatable(KEY + key), on, 0, () -> {
			flip.run();
			ShowtimeConfig.save();
			rebuild();
		}, null, null);
	}

	private void note(Component text) {
		rows.add(new Row(Style.NOTE, text, false, 0, null, null, null));
	}

	private void animations() {
		for (Moment each : Moment.values()) {
			sections.add(new Section(Component.translatable(KEY + "profile.moment." + each.id()), each == moment, () -> {
				moment = each;
				scroll = 0;
				rebuild();
				applyPreview();
			}));
		}
		if (!TrainerEmotes.available() || !inWorld()) {
			note(Component.translatable(KEY + (TrainerEmotes.available() ? "profile.in_world" : "profile.no_emotes")));
			return;
		}
		String chosen = TrainerEmotes.animationFor(minecraft.player.getUUID(), moment);
		for (String name : TrainerEmotes.variants(moment)) {
			rows.add(new Row(Style.SELECT, animationLabel(name), name.equals(chosen), 0, () -> {
				ShowtimeConfig.setTrainerAnimation(moment.id(), name);
				ShowtimeConfig.save();
				com.mystaria.showtime.api.ShowtimeHooks.localProfileChanged();
				rebuild();
				applyPreview();
			}, null, null));
		}
	}

	private void ambiances() {
		List<String> ids = new ArrayList<>();
		ids.add(BattleAmbiance.NONE);
		ids.addAll(Ambiances.ids());
		String chosen = ids.contains(ShowtimeConfig.ambiance()) ? ShowtimeConfig.ambiance() : BattleAmbiance.NONE;
		for (String id : ids) {
			rows.add(new Row(Style.SELECT, Ambiances.displayName(id), id.equals(chosen), 0, () -> {
				ShowtimeConfig.setAmbiance(id);
				ShowtimeConfig.save();
				rebuild();
				applyPreview();
			}, null, null));
		}
	}

	private void music() {
		for (BattleMusic.Track each : BattleMusic.shownTracks()) {
			sections.add(new Section(Component.translatable(KEY + "music.tab." + each.name().toLowerCase(Locale.ROOT)),
					each == track, () -> {
						track = each;
						scroll = 0;
						BattleMusic.endPreview();
						rebuild();
					}));
		}
		List<ResourceLocation> all = BattleMusic.allFiles(track);
		java.util.Set<Integer> present = new java.util.TreeSet<>();
		for (ResourceLocation file : all) {
			present.add(BattleMusic.generation(file));
		}
		boolean anyFavorite = all.stream().anyMatch(MusicSettings::favorite);
		if (generation == FAVORITES ? !anyFavorite : generation != 0 && !present.contains(generation)) {
			generation = 0;
		}
		if (present.size() > 1 || anyFavorite) {
			filters.add(new Section(Component.translatable(KEY + "music.filter.all"), generation == 0, () -> {
				generation = 0;
				scroll = 0;
				rebuild();
			}));
			if (anyFavorite) {
				filters.add(new Section(Component.translatable(KEY + "music.filter.favorites"), generation == FAVORITES, () -> {
					generation = FAVORITES;
					scroll = 0;
					rebuild();
				}));
			}
			for (int each : present) {
				Component label = each == BattleMusic.OTHER ? Component.translatable(KEY + "music.filter.other")
						: Component.translatable(KEY + "music.filter.generation", ROMAN[each]);
				filters.add(new Section(label, generation == each, () -> {
					generation = each;
					scroll = 0;
					rebuild();
				}));
			}
		}
		List<ResourceLocation> files = generation == 0 ? all
				: generation == FAVORITES ? all.stream().filter(MusicSettings::favorite).toList()
				: all.stream().filter(file -> BattleMusic.generation(file) == generation).toList();
		if (all.isEmpty()) {
			note(Component.translatable(KEY + "music.screen.empty", "sounds/music/" + track.name().toLowerCase(Locale.ROOT)));
			return;
		}
		rows.add(new Row(Style.SWITCH, Component.translatable(KEY + "music.only_favorites"), MusicSettings.onlyFavorites(), 0, () -> {
			MusicSettings.setOnlyFavorites(!MusicSettings.onlyFavorites());
			rebuild();
		}, null, null));
		// On the tracks shown: the filter narrows what "all" means.
		rows.add(new Row(Style.BUTTONS, Component.translatable(KEY + "music.enable_all"), false, 0, () -> {
			MusicSettings.setEnabled(files, true);
			rebuild();
		}, () -> {
			MusicSettings.setEnabled(files, false);
			rebuild();
		}, Component.translatable(KEY + "music.disable_all")));
		for (ResourceLocation file : files) {
			rows.add(new Row(Style.SWITCH, Component.literal(BattleMusic.title(file)), MusicSettings.enabled(file), 0, () -> {
				MusicSettings.setEnabled(file, !MusicSettings.enabled(file));
				rebuild();
			}, () -> {
				if (BattleMusic.previewing(file)) {
					BattleMusic.endPreview();
				} else {
					BattleMusic.preview(file);
				}
				rebuild();
			}, Component.translatable(KEY + (BattleMusic.previewing(file) ? "music.screen.stop" : "music.screen.listen")), () -> {
				MusicSettings.setFavorite(file, !MusicSettings.favorite(file));
				rebuild();
			}, MusicSettings.favorite(file)));
		}
	}

	/** {@code <key>.emote.<name>}, else the name made readable ({@code send_out_spin} → "Spin"). */
	private static Component animationLabel(String name) {
		String style = name.contains("_") ? name.substring(name.indexOf('_') + 1) : name;
		for (Moment each : Moment.values()) {
			if (name.startsWith(each.id() + "_")) {
				style = name.substring(each.id().length() + 1);
			}
		}
		String readable = style.replace('_', ' ');
		readable = readable.isEmpty() ? name : readable.substring(0, 1).toUpperCase(Locale.ROOT) + readable.substring(1);
		return Component.translatableWithFallback(KEY + "emote." + name, readable);
	}

	// ---------------------------------------------------------------- preview

	/** The window and what plays in it follow the category; never during a battle, which keeps its camera. */
	private void applyPreview() {
		if (!canPreview()) {
			MenuPreview.stop();
			return;
		}
		if (hasWindow()) {
			MenuPreview.start(windowX() + PREVIEW_W / 2.0, (windowY() + windowBottom()) / 2.0, windowBottom() - windowY());
		} else {
			MenuPreview.stop();
		}
		if (category == Category.ANIMATIONS && TrainerEmotes.available()) {
			TrainerEmotes.previewLoop(TrainerEmotes.animationFor(minecraft.player.getUUID(), moment));
		} else {
			TrainerEmotes.stopPreview();
		}
		if (category == Category.AMBIANCES) {
			BattleAmbiance.preview(ShowtimeConfig.ambiance());
		} else {
			BattleAmbiance.endPreview();
		}
	}

	@Override
	public void tick() {
		// A preview ambiance lasts 30 s: this one goes on as long as it is selected.
		if (category == Category.AMBIANCES && canPreview() && !BattleAmbiance.active()
				&& !BattleAmbiance.NONE.equals(ShowtimeConfig.ambiance())) {
			BattleAmbiance.preview(ShowtimeConfig.ambiance());
		}
	}

	@Override
	public void removed() {
		MenuPreview.stop();
		BattleMusic.endPreview();
		ShowtimeConfig.save();
		if (canPreview()) {
			TrainerEmotes.stopPreview();
			BattleAmbiance.endPreview();
		}
		if (hidGui && minecraft != null) {
			minecraft.options.hideGui = savedHideGui;
			hidGui = false;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false; // the preview plays on: the world keeps ticking
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	// ---------------------------------------------------------------- drawing

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		if (minecraft.level == null) {
			super.renderBackground(g, mouseX, mouseY, partialTick);
		} else {
			g.fill(0, 0, width, top, VEIL);
			g.fill(0, bottom, width, height, VEIL);
			g.fill(0, top, left, bottom, VEIL);
			g.fill(right, top, width, bottom, VEIL);
		}
		int contentTop = top + HEADER_H;
		int windowLeft = windowX();
		int windowTop = windowY();
		int windowRight = windowLeft + PREVIEW_W;
		int windowEnd = windowBottom();
		boolean opening = hasWindow() && canPreview();
		// The content, around the opening on the world when there is one.
		if (opening) {
			g.fill(left + SIDEBAR_W, contentTop, windowLeft, bottom, DARK);
			g.fill(windowLeft, contentTop, right, windowTop, DARK);
			g.fill(windowLeft, windowEnd, right, bottom, DARK);
			g.fill(windowRight, windowTop, right, windowEnd, DARK);
		} else {
			g.fill(left + SIDEBAR_W, contentTop, right, bottom, DARK);
		}
		// A fine grid of dots, so the background isn't flat.
		for (int y = contentTop + 6; y < bottom - 2; y += 12) {
			for (int x = left + SIDEBAR_W + 6; x < right - 2; x += 12) {
				if (!opening || x < windowLeft - 1 || x > windowRight || y < windowTop - 1 || y > windowEnd) {
					g.fill(x, y, x + 1, y + 1, 0xFF3A3A3A);
				}
			}
		}
		if (hasWindow()) {
			bezel(g, windowLeft, windowTop, windowRight, windowEnd);
		}
		// Header, with its hatching and accent line.
		g.fillGradient(left, top, right, contentTop, EDGE, RAISED);
		g.fill(left, top, right, top + 1, LIGHT);
		for (int i = 0; i < 14; i++) {
			int x = right - 44 - i * 6;
			for (int step = 0; step < 8; step++) {
				g.fill(x - step, top + 8 + step, x - step + 2, top + 9 + step, SHADE);
			}
		}
		g.fill(left, contentTop - 2, right, contentTop - 1, BLACK);
		g.fillGradient(left, contentTop - 1, right, contentTop, ACCENT, ACCENT_DIM);
		// A small gem before the title.
		g.fill(left + 9, top + 8, left + 16, top + 15, BLACK);
		g.fillGradient(left + 10, top + 9, left + 15, top + 14, 0xFF7FF0E8, ACCENT_DIM);
		// Sidebar.
		g.fillGradient(left, contentTop, left + SIDEBAR_W, bottom, PANEL, DARK);
		g.fill(left + SIDEBAR_W - 1, contentTop, left + SIDEBAR_W, bottom, BLACK);
		g.fill(left + SIDEBAR_W, contentTop, left + SIDEBAR_W + 1, bottom, RAISED);
		// Frame, corners trimmed, lit from the top left.
		g.fill(left - 2, top - 1, left - 1, bottom + 1, BLACK);
		g.fill(right + 1, top - 1, right + 2, bottom + 1, BLACK);
		g.fill(left - 1, top - 2, right + 1, top - 1, BLACK);
		g.fill(left - 1, bottom + 1, right + 1, bottom + 2, BLACK);
		g.fill(left - 1, top - 1, right + 1, top, LIGHT);
		g.fill(left - 1, top, left, bottom + 1, LIGHT);
		g.fill(right, top, right + 1, bottom + 1, BLACK);
		g.fill(left, bottom, right + 1, bottom + 1, BLACK);
	}

	/** The window's raised metal frame, with accent corner brackets. */
	private void bezel(GuiGraphics g, int x1, int y1, int x2, int y2) {
		g.fill(x1 - 3, y1 - 3, x2 + 3, y1, RAISED);
		g.fill(x1 - 3, y2, x2 + 3, y2 + 3, PANEL);
		g.fill(x1 - 3, y1, x1, y2, RAISED);
		g.fill(x2, y1, x2 + 3, y2, PANEL);
		g.fill(x1 - 3, y1 - 3, x2 + 3, y1 - 2, LIGHT);
		g.fill(x1 - 3, y1 - 2, x1 - 2, y2 + 3, EDGE);
		g.fill(x1 - 3, y2 + 2, x2 + 3, y2 + 3, BLACK);
		g.fill(x2 + 2, y1 - 2, x2 + 3, y2 + 3, BLACK);
		outline(g, x1 - 1, y1 - 1, x2 + 1, y2 + 1, BLACK);
		int arm = 10;
		g.fill(x1 - 3, y1 - 3, x1 - 3 + arm, y1 - 1, ACCENT);
		g.fill(x1 - 3, y1 - 3, x1 - 1, y1 - 3 + arm, ACCENT);
		g.fill(x2 + 3 - arm, y1 - 3, x2 + 3, y1 - 1, ACCENT);
		g.fill(x2 + 1, y1 - 3, x2 + 3, y1 - 3 + arm, ACCENT);
		g.fill(x1 - 3, y2 + 1, x1 - 3 + arm, y2 + 3, ACCENT);
		g.fill(x1 - 3, y2 + 3 - arm, x1 - 1, y2 + 3, ACCENT);
		g.fill(x2 + 3 - arm, y2 + 1, x2 + 3, y2 + 3, ACCENT);
		g.fill(x2 + 1, y2 + 3 - arm, x2 + 3, y2 + 3, ACCENT);
	}

	/** Light on top, shade below: the shape stands out of the panel. */
	private static void raised(GuiGraphics g, int x1, int y1, int x2, int y2) {
		g.fill(x1, y1, x2, y1 + 1, SHINE);
		g.fill(x1, y1 + 1, x1 + 1, y2, SHINE);
		g.fill(x1, y2 - 1, x2, y2, SHADE);
		g.fill(x2 - 1, y1, x2, y2 - 1, SHADE);
	}

	/** Shade on top, light below: the shape sinks into the panel. */
	private static void sunken(GuiGraphics g, int x1, int y1, int x2, int y2) {
		g.fill(x1, y1, x2, y1 + 1, SHADE);
		g.fill(x1, y1 + 1, x1 + 1, y2, SHADE);
		g.fill(x1, y2 - 1, x2, y2, SHINE);
		g.fill(x2 - 1, y1, x2, y2 - 1, SHINE);
	}

	private static void outline(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
		g.fill(x1, y1, x2, y1 + 1, color);
		g.fill(x1, y2 - 1, x2, y2, color);
		g.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
		g.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		text(g, title.copy().withStyle(s -> s.withBold(true)), left + 21, top + 7, right - left - 150, WHITE);
		navigation(g, mouseX, mouseY);
		text(g, categoryName(category).copy().withStyle(s -> s.withBold(true)), contentX(), top + HEADER_H + 7, contentRight() - contentX(), WHITE);
		g.fillGradient(contentX(), top + HEADER_H + 18, contentRight(), top + HEADER_H + 19, ACCENT, ACCENT);
		g.fill(contentX(), top + HEADER_H + 19, contentRight(), top + HEADER_H + 20, BLACK);
		if (hasWindow()) {
			text(g, Component.translatable(KEY + "menu.preview"), windowX(), top + HEADER_H + 7, PREVIEW_W, MUTED);
			if (!canPreview()) {
				g.fill(windowX(), windowY(), windowX() + PREVIEW_W, windowBottom(), PANEL);
				centered(g, Component.translatable(KEY + "menu.no_preview"), windowX() + PREVIEW_W / 2, (windowY() + windowBottom()) / 2 - 4, PREVIEW_W - 8, MUTED);
			}
		}
		sections(g, mouseX, mouseY);
		rows(g, mouseX, mouseY);
		scrollbar(g, mouseX, mouseY);
	}

	private static Component categoryName(Category each) {
		return Component.translatable(KEY + "menu.category." + each.name().toLowerCase(Locale.ROOT));
	}

	private void navigation(GuiGraphics g, int mouseX, int mouseY) {
		Category[] all = Category.values();
		for (int i = 0; i < all.length; i++) {
			int y = navY(i);
			boolean selected = all[i] == category;
			if (selected) {
				gradientRight(g, left + 1, y, left + SIDEBAR_W - 1, y + NAV_H, 0xFF1F7F7A, RAISED);
				raised(g, left + 1, y, left + SIDEBAR_W - 1, y + NAV_H);
				g.fill(left + 1, y, left + 4, y + NAV_H, ACCENT);
				// A notch pointing at the content.
				for (int step = 0; step < 4; step++) {
					g.fill(left + SIDEBAR_W - 6 + step, y + 8 + step, left + SIDEBAR_W - 5 + step, y + 16 - step, WHITE);
				}
			} else if (inside(mouseX, mouseY, left, y, SIDEBAR_W, NAV_H)) {
				g.fill(left + 1, y, left + SIDEBAR_W - 1, y + NAV_H, RAISED);
				raised(g, left + 1, y, left + SIDEBAR_W - 1, y + NAV_H);
			}
			g.fill(left + 1, y + NAV_H, left + SIDEBAR_W - 1, y + NAV_H + 1, BLACK);
			g.fill(left + 9, y + 10, left + 13, y + 14, selected ? WHITE : EDGE);
			text(g, categoryName(all[i]), left + 17, y + 8, SIDEBAR_W - 26, selected ? WHITE : MUTED);
		}
	}

	/** A left-to-right gradient, in thin bands (GuiGraphics only has vertical ones). */
	private static void gradientRight(GuiGraphics g, int x1, int y1, int x2, int y2, int from, int to) {
		int steps = Math.max(1, (x2 - x1) / 4);
		for (int i = 0; i < steps; i++) {
			float t = i / (float) steps;
			int color = net.minecraft.util.FastColor.ARGB32.lerp(t, from, to);
			g.fill(x1 + (x2 - x1) * i / steps, y1, x1 + (x2 - x1) * (i + 1) / steps, y2, color);
		}
	}

	private int navY(int index) {
		return top + HEADER_H + 6 + index * (NAV_H + 2);
	}

	private int chipWidth() {
		return (contentRight() - contentX() - (CHIP_COLUMNS - 1) * 3) / CHIP_COLUMNS;
	}

	private int chipX(int index) {
		return contentX() + (index % CHIP_COLUMNS) * (chipWidth() + 3);
	}

	private int chipY(int index) {
		return chipsY() + (index / CHIP_COLUMNS) * (CHIP_H + 3);
	}

	private void sections(GuiGraphics g, int mouseX, int mouseY) {
		int w = chipWidth();
		for (int i = 0; i < sections.size(); i++) {
			Section section = sections.get(i);
			int x = chipX(i);
			int y = chipY(i);
			g.fill(x - 1, y - 1, x + w + 1, y + CHIP_H + 1, BLACK);
			if (section.selected()) {
				g.fillGradient(x, y, x + w, y + CHIP_H, ACCENT, ACCENT_DIM);
			} else {
				boolean hovered = inside(mouseX, mouseY, x, y, w, CHIP_H);
				g.fillGradient(x, y, x + w, y + CHIP_H, hovered ? LIGHT : EDGE, hovered ? EDGE : RAISED);
			}
			raised(g, x, y, x + w, y + CHIP_H);
			centered(g, section.label(), x + w / 2, y + 3, w - 6, WHITE);
		}
		int fw = filterWidth();
		for (int i = 0; i < filters.size(); i++) {
			Section filter = filters.get(i);
			int x = filterX(i);
			int y = filterY(i);
			boolean hovered = inside(mouseX, mouseY, x, y, fw, CHIP_H);
			g.fill(x - 1, y - 1, x + fw + 1, y + CHIP_H + 1, BLACK);
			g.fill(x, y, x + fw, y + CHIP_H, filter.selected() ? ACCENT_DIM : hovered ? RAISED : PANEL);
			if (filter.selected()) {
				g.fill(x, y + CHIP_H - 2, x + fw, y + CHIP_H, ACCENT);
			}
			centered(g, filter.label(), x + fw / 2, y + 3, fw - 4, filter.selected() ? WHITE : MUTED);
		}
	}

	private int filterWidth() {
		return (contentRight() - contentX() - (FILTER_COLUMNS - 1) * 3) / FILTER_COLUMNS;
	}

	private int filterX(int index) {
		return contentX() + (index % FILTER_COLUMNS) * (filterWidth() + 3);
	}

	private int filterY(int index) {
		return filtersY() + (index / FILTER_COLUMNS) * (CHIP_H + 3);
	}

	/** The thumb's top and height, for a list that doesn't fit; null when it does. */
	private int[] thumb() {
		int max = maxScroll();
		if (max == 0) {
			return null;
		}
		int view = rowsBottom() - rowsY();
		int height = Math.max(16, view * view / (view + max));
		return new int[] {rowsY() + Math.round((view - height) * (scroll / (float) max)), height};
	}

	private void scrollbar(GuiGraphics g, int mouseX, int mouseY) {
		int[] thumb = thumb();
		if (thumb == null) {
			return;
		}
		int x = contentRight() - BAR_W;
		g.fill(x, rowsY(), x + BAR_W, rowsBottom(), BLACK);
		boolean hot = draggingBar || inside(mouseX, mouseY, x, thumb[0], BAR_W, thumb[1]);
		g.fillGradient(x, thumb[0], x + BAR_W, thumb[0] + thumb[1], hot ? 0xFF7FF0E8 : ACCENT, ACCENT_DIM);
		raised(g, x, thumb[0], x + BAR_W, thumb[0] + thumb[1]);
	}

	/** Moves the list so that the thumb's grabbed point follows the mouse. */
	private void dragBar(double mouseY) {
		int[] thumb = thumb();
		if (thumb == null) {
			return;
		}
		int view = rowsBottom() - rowsY() - thumb[1];
		float t = view <= 0 ? 0 : (float) ((mouseY - barGrab - rowsY()) / view);
		scrollTo(Math.round(t * maxScroll()));
	}

	private int rowY(int index) {
		return rowsY() + index * ROW_PITCH - scroll;
	}

	private boolean rowVisible(int index) {
		return rowY(index) + ROW_H > rowsY() && rowY(index) < rowsBottom();
	}

	private void rows(GuiGraphics g, int mouseX, int mouseY) {
		int x = contentX();
		int w = rowsRight() - x;
		g.enableScissor(x, rowsY(), contentRight(), rowsBottom());
		for (int index = 0; index < rows.size(); index++) {
			if (!rowVisible(index)) {
				continue;
			}
			Row row = rows.get(index);
			int y = rowY(index);
			if (row.style() == Style.NOTE) {
				text(g, row.text(), x, y + 5, w, MUTED);
				continue;
			}
			boolean hovered = inside(mouseX, mouseY, x, y, w, ROW_H);
			g.fill(x, y, x + w, y + ROW_H, hovered ? RAISED : PANEL);
			sunken(g, x, y, x + w, y + ROW_H);
			int textW = w - 12;
			switch (row.style()) {
				case SELECT -> {
					if (row.on()) {
						gradientRight(g, x + 1, y + 1, x + w - 1, y + ROW_H - 1, 0xFF1F7F7A, PANEL);
						g.fill(x, y, x + 3, y + ROW_H, ACCENT);
						g.fill(x + w - 12, y + 7, x + w - 8, y + 11, ACCENT);
					}
				}
				case SWITCH -> {
					int pillX = x + w - 26;
					// A rounded switch: its corners are trimmed one pixel.
					g.fill(pillX + 1, y + 3, pillX + 19, y + 15, BLACK);
					g.fill(pillX, y + 4, pillX + 20, y + 14, BLACK);
					g.fillGradient(pillX + 1, y + 4, pillX + 19, y + 14, row.on() ? ACCENT_DIM : DARK, row.on() ? ACCENT : PANEL);
					int knobX = row.on() ? pillX + 11 : pillX + 2;
					g.fill(knobX - 1, y + 4, knobX + 8, y + 14, BLACK);
					g.fillGradient(knobX, y + 5, knobX + 7, y + 13, WHITE, MUTED);
					textW -= 26;
					if (row.side() != null) {
						int sideX = x + w - 48;
						boolean sideHover = inside(mouseX, mouseY, sideX, y, 18, ROW_H);
						g.fillGradient(sideX, y + 2, sideX + 18, y + 16, sideHover ? ACCENT : EDGE, sideHover ? ACCENT_DIM : RAISED);
						raised(g, sideX, y + 2, sideX + 18, y + 16);
						centered(g, row.sideText(), sideX + 9, y + 5, 16, WHITE);
						textW -= 22;
					}
					if (row.star() != null) {
						int starX = x + w - 70;
						boolean starHover = inside(mouseX, mouseY, starX, y, 18, ROW_H);
						g.fillGradient(starX, y + 2, starX + 18, y + 16, starHover ? LIGHT : EDGE, starHover ? EDGE : RAISED);
						raised(g, starX, y + 2, starX + 18, y + 16);
						centered(g, Component.literal(row.starred() ? "★" : "☆"), starX + 9, y + 5, 16, row.starred() ? STAR : MUTED);
						textW -= 22;
					}
				}
				case BUTTONS -> {
					int half = (w - 4) / 2;
					for (int b = 0; b < 2; b++) {
						int bx = x + b * (half + 4);
						boolean hover = inside(mouseX, mouseY, bx, y, half, ROW_H);
						g.fillGradient(bx, y, bx + half, y + ROW_H, hover ? LIGHT : EDGE, hover ? EDGE : RAISED);
						raised(g, bx, y, bx + half, y + ROW_H);
						centered(g, b == 0 ? row.text() : row.sideText(), bx + half / 2, y + 5, half - 6, WHITE);
					}
					continue;
				}
				case SLIDER -> {
					int barY = y + ROW_H - 5;
					g.fill(x + 6, barY, x + w - 6, barY + 2, BLACK);
					int knob = x + 6 + Math.round((w - 12) * row.value());
					g.fill(x + 6, barY, knob, barY + 2, ACCENT);
					g.fill(knob - 2, barY - 2, knob + 3, barY + 4, BLACK);
					g.fillGradient(knob - 1, barY - 1, knob + 2, barY + 3, WHITE, MUTED);
				}
				default -> {
				}
			}
			text(g, row.text(), x + 7, row.style() == Style.SLIDER ? y + 2 : y + 5, textW, row.on() || row.style() != Style.SELECT ? WHITE : MUTED);
		}
		g.disableScissor();
	}

	private void text(GuiGraphics g, Component text, int x, int y, int maxWidth, int color) {
		draw(g, text, x, y, maxWidth, color, false);
	}

	private void centered(GuiGraphics g, Component text, int centerX, int y, int maxWidth, int color) {
		draw(g, text, centerX, y, maxWidth, color, true);
	}

	/** Cobblemon's own text drawing, in its font; a text too long for its place shrinks to fit. */
	private static void draw(GuiGraphics g, Component text, int x, int y, int maxWidth, int color, boolean center) {
		RenderHelperKt.drawScaledText(g, UNIFORM, text.copy(), x, y, 1f, 1f, maxWidth, color, center, true, null, null);
	}

	private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	// ---------------------------------------------------------------- input

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && clicked(mouseX, mouseY)) {
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private boolean clicked(double mouseX, double mouseY) {
		Category[] all = Category.values();
		for (int i = 0; i < all.length; i++) {
			if (inside(mouseX, mouseY, left, navY(i), SIDEBAR_W, NAV_H)) {
				clickSound();
				select(all[i]);
				return true;
			}
		}
		for (int i = 0; i < sections.size(); i++) {
			if (inside(mouseX, mouseY, chipX(i), chipY(i), chipWidth(), CHIP_H)) {
				clickSound();
				sections.get(i).click().run();
				return true;
			}
		}
		for (int i = 0; i < filters.size(); i++) {
			if (inside(mouseX, mouseY, filterX(i), filterY(i), filterWidth(), CHIP_H)) {
				clickSound();
				filters.get(i).click().run();
				return true;
			}
		}
		int[] thumb = thumb();
		int barX = contentRight() - BAR_W;
		if (thumb != null && inside(mouseX, mouseY, barX - 1, rowsY(), BAR_W + 2, rowsBottom() - rowsY())) {
			// On the thumb: held where it was grabbed; on the track: the thumb jumps under the mouse.
			boolean onThumb = mouseY >= thumb[0] && mouseY < thumb[0] + thumb[1];
			barGrab = onThumb ? (int) mouseY - thumb[0] : thumb[1] / 2;
			draggingBar = true;
			dragBar(mouseY);
			return true;
		}
		if (mouseY < rowsY() || mouseY >= rowsBottom()) {
			return false;
		}
		int x = contentX();
		int w = rowsRight() - x;
		for (int index = 0; index < rows.size(); index++) {
			int i = index;
			if (!inside(mouseX, mouseY, x, rowY(index), w, ROW_H)) {
				continue;
			}
			Row row = rows.get(index);
			if (row.style() == Style.SLIDER) {
				draggingVolume = true;
				setVolume(mouseX);
				return true;
			}
			Runnable action;
			if (row.style() == Style.BUTTONS) {
				action = mouseX < x + w / 2 ? row.click() : row.side();
			} else if (row.star() != null && inside(mouseX, mouseY, x + w - 70, rowY(i), 18, ROW_H)) {
				action = row.star();
			} else {
				boolean onSide = row.side() != null && inside(mouseX, mouseY, x + w - 48, rowY(i), 18, ROW_H);
				action = onSide ? row.side() : row.click();
			}
			if (action != null) {
				clickSound();
				action.run();
				return true;
			}
		}
		return false;
	}

	private void clickSound() {
		minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
				com.cobblemon.mod.common.CobblemonSounds.PC_CLICK, 1f));
	}

	private void setVolume(double mouseX) {
		ShowtimeConfig.setMusicVolume((float) Mth.clamp((mouseX - contentX()) / (contentRight() - contentX()), 0, 1));
		rebuild();
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (draggingVolume) {
			setVolume(mouseX);
			return true;
		}
		if (draggingBar) {
			dragBar(mouseY);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		draggingBar = false;
		if (draggingVolume) {
			draggingVolume = false;
			ShowtimeConfig.save();
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		scrollTo(scroll - (int) Math.round(scrollY * ROW_PITCH * 2));
		return true;
	}
}
