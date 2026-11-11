package com.mystaria.showtime.battle;

import java.util.List;
import java.util.UUID;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.mystaria.showtime.battle.BattleCinematic;
import com.mystaria.showtime.battle.BattleCinematic.Intro;

/**
 * The 2D part of the battle intro ({@link BattleCinematic}), one scene per {@link Intro} (TODO-26), all on the same
 * clock ({@link BattleCinematic#INTRO_END}) and all ending on the same white flash:
 * <ul>
 *   <li><b>X/Y</b>: the "eyes meet" dialog box over the world shot, the diamond wipe to black, the challenger panel
 *   (speed-line background, the opponent running in as a silhouette then revealed, their team's balls, "You are
 *   challenged by …!");</li>
 *   <li><b>Sword/Shield</b>: blue and pink halves slamming in along a diagonal, both trainers face to face and a "VS",
 *   then the opponent throwing their ball over pink speed lines;</li>
 *   <li><b>Diamond/Pearl/Platinum</b>: a spinning Poké Ball closed in by black corners, a blue band, a white burst, then
 *   the field's platforms sliding in from both sides with both trainers, the ball bar and the challenge text;</li>
 *   <li><b>Emerald</b>: the typed dialog box, bands of black behind rolling Poké Balls, the screen opening on a line,
 *   the trainers sliding across the field, both ball bars, "… would like to battle!";</li>
 *   <li><b>Black/White</b>: radial streaks while the world camera rushes at the opponent, white, then the view
 *   pulling back from the opponent's silhouette on their platform before revealing them.</li>
 * </ul>
 * Driven by the cinematic's clock only; it closes itself when the intro is over. No input.
 */
public final class BattleIntroScreen extends Screen implements com.mystaria.showtime.api.IntroContext {

	// ---- X/Y ----
	private static final long PANEL_RUN_MS = 650;
	private static final long PANEL_REVEAL_MS = 450;
	private static final long PANEL_TEXT_DELAY = 450;
	private static final int SPEED_LINES = 80;
	private static final int[] XY_SPEED = { 0xFF0A1A35, 0xFF2C5DA6, 0xFFE6F2FF, 0xFFFFFFFF, 0xFF4C7CC2, 0xFF26395E, 0xFF0D1729 };

	// ---- Sword/Shield (ms since the session started) ----
	private static final long SWSH_SPLIT = 2450;
	private static final long SWSH_VS = 2750;
	private static final long SWSH_THROW = 4600;
	private static final float SWSH_TILT = 16f;
	private static final int SWSH_BLUE = 0xFF1C4ED6;
	private static final int SWSH_PINK = 0xFFE5307F;
	private static final int[] SWSH_SPEED = { 0xFF3A0A28, 0xFFC0306E, 0xFFFFE0EE, 0xFFFFFFFF, 0xFFE0569A, 0xFF6A1E48, 0xFF2A0A1C };

	// ---- Diamond/Pearl/Platinum ----
	private static final long DP_WIPE_END = 2700;
	private static final long DP_BAND = 2850;
	private static final long DP_BURST = 3050;
	private static final long DP_FIELD = 3300;

	// ---- Emerald ----
	private static final long EM_ROLL_END = 2750;
	private static final long EM_OPEN = 2950;
	private static final long EM_OPEN_END = 3350;
	private static final int EM_BANDS = 5;

	// ---- Black/White ----
	private static final long BW_WHITE = 2450;
	private static final long BW_FIELD = BattleCinematic.ZOOM_END;

	// ---- Battle Revolution, the match card (ms since the session started) ----
	private static final long PBR_CARDS = 2450;
	private static final long PBR_TRAINERS = 3950;
	private static final long PBR_VS = 4250;
	private static final long PBR_WIPE = 7100;
	private static final long PBR_BLACK = 7650;
	private static final int PBR_DEEP = 0xFF041838;
	private static final int PBR_BLUE = 0xFF0B4E96;
	private static final int PBR_GLOW = 0xFF39C6F0;
	private static final int PBR_GOLD = 0xFFF6DE52;

	// ---- Battle Revolution, the entrance (world shots in BattleCinematic) ----
	public static final long PBR_ARENA = 2450;
	public static final long PBR_OPPONENT = 4950;
	public static final long PBR_SELF = 6950;
	public static final long PBR_FADE = 8950;

	// ---- Sun/Moon, the vortex ----
	private static final long SM_WHITE = 2700;
	private static final long SM_VORTEX = 2950;
	private static final long SM_TEXT = 3400;
	private static final long SM_THROW = 6500;

	// ---- Ultra Sun/Ultra Moon, the surge ----
	private static final long US_WHITE = 2850;
	private static final long US_REVEAL = 3150;
	private static final long US_TEXT = 3700;
	private static final long US_IRIS = 6700;
	private static final long US_BALL = 7350;

	// ---- Omega Ruby/Alpha Sapphire, the silhouette ----
	private static final long OR_BLACK = 2750;
	private static final long OR_LINE = 3200;
	private static final long OR_PANEL = 3450;
	private static final long OR_COLOUR = 4000;
	private static final long OR_TEXT = 4200;

	// ---- Battle Revolution, the face-off; Scarlet/Violet, the showdown (world shots in BattleCinematic) ----
	public static final long FO_SHOTS = 2250;
	public static final long FO_SHOT = 1250;
	public static final long FO_VS = 7250;
	public static final long SV_REVERSE = 3800;
	public static final long SV_CRANE = 5600;

	// ---- Red/Blue, the clock ----
	private static final long RB_BLACK = 2900;
	private static final long RB_FIELD = 3100;
	private static final long RB_COLOUR = 4700;

	// ---- Gold/Silver, the stairs ----
	private static final long GS_STAIRS = 2750;
	private static final long GS_BLACK = 3450;
	private static final long GS_FIELD = 3600;
	private static final long GS_COLOUR = 4500;

	// ---- Diamond/Pearl, the blinds ----
	private static final long DB_BALL = 2650;
	private static final long DB_BLINDS = 3150;
	private static final long DB_BLACK = 3750;
	private static final long DB_FIELD = 3850;

	// ---- Colosseum, the twisting world (world shots in BattleCinematic) ----
	public static final long CO_WIDE = 3500;
	public static final long CO_GROUND = 5800;

	/** Sounds of each scene, at their phase boundaries. */
	private record Cue(long at, SoundEvent sound, float pitch, float volume) {
	}

	private final Intro intro;
	/** Another mod's intro (1.2.0), drawn instead of {@link #intro}; null for Showtime's own. */
	private final com.mystaria.showtime.api.CustomIntro custom;
	private final List<Cue> cues;
	private final boolean[] cuePlayed;
	private IntroTrainer trainer;
	private IntroTrainer self;
	private boolean throwStarted;

	public BattleIntroScreen() {
		super(Component.translatable("cobblemon_battle_showtime.intro.title"));
		this.intro = BattleCinematic.intro();
		this.custom = BattleCinematic.customIntro();
		this.cues = custom != null ? List.of() : cues(intro);
		this.cuePlayed = new boolean[cues.size()];
	}

	private static List<Cue> cues(Intro intro) {
		Cue flash = new Cue(intro.flashStart(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1f);
		return switch (intro) {
			case XY -> List.of(
					new Cue(BattleCinematic.EYES_MEET_END, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 1f),
					new Cue(BattleCinematic.PANEL_START, SoundEvents.ELYTRA_FLYING, 1.6f, 0.35f),
					flash);
			case SWORD_SHIELD -> List.of(
					new Cue(BattleCinematic.EYES_MEET_END, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 1f),
					new Cue(SWSH_SPLIT, SoundEvents.PLAYER_ATTACK_STRONG, 0.7f, 1f),
					new Cue(SWSH_VS, SoundEvents.FIREWORK_ROCKET_BLAST, 0.7f, 1f),
					new Cue(SWSH_THROW, SoundEvents.ELYTRA_FLYING, 1.8f, 0.35f),
					new Cue(SWSH_THROW + 200, SoundEvents.PLAYER_ATTACK_SWEEP, 1.5f, 0.8f),
					flash);
			case DIAMOND_PEARL -> List.of(
					new Cue(BattleCinematic.EYES_MEET_END, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 1f),
					new Cue(DP_BAND, SoundEvents.PLAYER_ATTACK_STRONG, 1.2f, 0.7f),
					new Cue(DP_BURST, SoundEvents.FIREWORK_ROCKET_BLAST, 1.1f, 0.9f),
					new Cue(DP_FIELD, SoundEvents.ELYTRA_FLYING, 1.2f, 0.3f),
					flash);
			case EMERALD -> List.of(
					new Cue(BattleCinematic.EYES_MEET_END, SoundEvents.ITEM_PICKUP, 0.5f, 1f),
					new Cue(BattleCinematic.EYES_MEET_END + 250, SoundEvents.ITEM_PICKUP, 0.6f, 1f),
					new Cue(EM_OPEN, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 1f),
					new Cue(EM_OPEN_END, SoundEvents.ELYTRA_FLYING, 1.0f, 0.3f),
					flash);
			case PBR_CARD -> List.of(
					new Cue(BattleCinematic.EYES_MEET_END, SoundEvents.ELYTRA_FLYING, 1.4f, 0.4f),
					new Cue(PBR_CARDS, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.8f),
					new Cue(PBR_TRAINERS, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.9f),
					new Cue(PBR_VS, SoundEvents.FIREWORK_ROCKET_BLAST, 0.8f, 1f),
					new Cue(PBR_WIPE, SoundEvents.ELYTRA_FLYING, 1.9f, 0.5f),
					new Cue(PBR_BLACK, SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 1f));
			case PBR_ENTRANCE -> List.of(
					new Cue(PBR_ARENA, SoundEvents.ELYTRA_FLYING, 0.9f, 0.35f),
					new Cue(PBR_ARENA + 300, SoundEvents.AMETHYST_BLOCK_CHIME, 0.5f, 0.9f),
					new Cue(PBR_OPPONENT, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 0.7f),
					new Cue(PBR_SELF, SoundEvents.PLAYER_ATTACK_SWEEP, 1.1f, 0.7f),
					new Cue(PBR_FADE, SoundEvents.ELYTRA_FLYING, 1.6f, 0.4f));
			case SM_VORTEX -> List.of(
					new Cue(BattleCinematic.EYES_MEET_END, SoundEvents.ELYTRA_FLYING, 1.8f, 0.5f),
					new Cue(SM_WHITE, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 1f),
					new Cue(SM_VORTEX, SoundEvents.FIREWORK_ROCKET_TWINKLE, 0.9f, 0.8f),
					new Cue(SM_THROW, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.9f),
					flash);
			case USUM_SURGE -> List.of(
					new Cue(BattleCinematic.EYES_MEET_END, SoundEvents.BEACON_POWER_SELECT, 1.6f, 0.6f),
					new Cue(US_WHITE, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.4f, 0.7f),
					new Cue(US_REVEAL, SoundEvents.TRIDENT_THUNDER.value(), 1.6f, 0.35f),
					new Cue(US_IRIS, SoundEvents.ELYTRA_FLYING, 1.9f, 0.4f),
					new Cue(US_BALL, SoundEvents.ITEM_PICKUP, 0.6f, 1f));
			case ORAS_SILHOUETTE -> List.of(
					new Cue(BattleCinematic.EYES_MEET_END, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7f, 0.9f),
					new Cue(OR_BLACK, SoundEvents.ITEM_PICKUP, 0.5f, 1f),
					new Cue(OR_BLACK + 200, SoundEvents.ITEM_PICKUP, 0.6f, 1f),
					new Cue(OR_PANEL, SoundEvents.ELYTRA_FLYING, 1.6f, 0.35f),
					new Cue(OR_COLOUR, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 0.9f),
					flash);
			case PBR_FACE_OFF -> List.of(
					new Cue(FO_SHOTS, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9f, 0.6f),
					new Cue(FO_SHOTS + FO_SHOT * 2, SoundEvents.PLAYER_ATTACK_SWEEP, 1.1f, 0.6f),
					new Cue(FO_VS, SoundEvents.FIREWORK_ROCKET_BLAST, 0.8f, 1f),
					flash);
			case SV_SHOWDOWN -> List.of(
					new Cue(BattleCinematic.EYES_MEET_END + 400, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.5f),
					new Cue(SV_REVERSE, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.5f),
					new Cue(SV_CRANE, SoundEvents.ELYTRA_FLYING, 1.2f, 0.4f),
					new Cue(SV_CRANE + 600, SoundEvents.FIREWORK_ROCKET_BLAST, 1.0f, 0.9f));
			case RBY_CLOCK -> List.of(
					new Cue(BattleCinematic.EYES_MEET_END, SoundEvents.NOTE_BLOCK_BIT.value(), 1.4f, 0.8f),
					new Cue(BattleCinematic.EYES_MEET_END + 450, SoundEvents.NOTE_BLOCK_BIT.value(), 1.0f, 0.8f),
					new Cue(RB_FIELD, SoundEvents.PLAYER_ATTACK_SWEEP, 1.3f, 0.7f),
					new Cue(RB_COLOUR, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1f),
					flash);
			case GSC_STAIRS -> List.of(
					new Cue(BattleCinematic.EYES_MEET_END, SoundEvents.NOTE_BLOCK_BIT.value(), 1.8f, 0.7f),
					new Cue(BattleCinematic.EYES_MEET_END + 250, SoundEvents.NOTE_BLOCK_BIT.value(), 1.8f, 0.7f),
					new Cue(GS_STAIRS, SoundEvents.STONE_PLACE, 0.8f, 0.8f),
					new Cue(GS_FIELD, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.7f),
					new Cue(GS_COLOUR, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1f),
					flash);
			case DP_BLINDS -> List.of(
					new Cue(BattleCinematic.EYES_MEET_END, SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.4f, 0.7f),
					new Cue(DB_BALL, SoundEvents.ITEM_PICKUP, 0.7f, 1f),
					new Cue(DB_BLINDS, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.9f),
					new Cue(DB_FIELD, SoundEvents.ELYTRA_FLYING, 1.3f, 0.3f),
					flash);
			case COLOSSEUM_SWIRL -> List.of(
					new Cue(BattleCinematic.EYES_MEET_END, SoundEvents.ELYTRA_FLYING, 0.7f, 0.6f),
					new Cue(CO_WIDE, SoundEvents.PLAYER_ATTACK_STRONG, 0.6f, 0.8f),
					new Cue(CO_GROUND, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.6f),
					flash);
			case BLACK_WHITE -> List.of(
					new Cue(BattleCinematic.EYES_MEET_END, SoundEvents.ELYTRA_FLYING, 2.0f, 0.6f),
					new Cue(BW_WHITE, SoundEvents.FIREWORK_ROCKET_BLAST, 1.2f, 0.9f),
					new Cue(BW_FIELD + 200, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6f, 0.8f),
					flash);
		};
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// The world shot shows through during the first part.
	}

	@Override
	public void tick() {
		if (!BattleCinematic.introPlaying()) {
			onClose();
			return;
		}
		long elapsed = BattleCinematic.introElapsed();
		for (int i = 0; i < cues.size(); i++) {
			Cue cue = cues.get(i);
			if (!cuePlayed[i] && elapsed >= cue.at()) {
				cuePlayed[i] = true;
				BattleCinematic.play(cue.sound(), cue.pitch(), cue.volume());
			}
		}
		IntroTrainer model = trainer();
		if (model != null) {
			boolean running = intro == Intro.XY && elapsed >= BattleCinematic.PANEL_START
					&& elapsed - BattleCinematic.PANEL_START < PANEL_RUN_MS;
			animate(model, running);
			if (intro == Intro.SM_VORTEX && elapsed >= SM_THROW && !throwStarted) {
				throwStarted = true;
				model.swing(InteractionHand.MAIN_HAND);
			}
			if (intro == Intro.SWORD_SHIELD && elapsed >= SWSH_THROW + 150 && !throwStarted) {
				throwStarted = true;
				model.swing(InteractionHand.MAIN_HAND);
			}
			model.tickSwing();
		}
		IntroTrainer me = selfModel();
		if (me != null) {
			animate(me, false);
		}
		if (custom != null && elapsed >= BattleCinematic.EYES_MEET_END) {
			custom.tick(this, elapsed - BattleCinematic.EYES_MEET_END);
		}
	}

	private static void animate(RemotePlayer model, boolean running) {
		model.walkAnimation.update(running ? 1.0f : 0f, running ? 0.6f : 0.25f);
		model.tickCount++;
	}

	// =====================================================================
	// Rendering
	// =====================================================================

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		long elapsed = BattleCinematic.introElapsed();
		if (custom != null) {
			renderCustom(g, elapsed, partialTick);
			return;
		}
		switch (intro) {
			case XY -> renderXy(g, elapsed, partialTick);
			case SWORD_SHIELD -> renderSwordShield(g, elapsed, partialTick);
			case DIAMOND_PEARL -> renderDiamondPearl(g, elapsed);
			case EMERALD -> renderEmerald(g, elapsed);
			case BLACK_WHITE -> renderBlackWhite(g, elapsed);
			case PBR_CARD -> renderPbrCard(g, elapsed);
			case PBR_ENTRANCE -> renderPbrEntrance(g, elapsed);
			case SM_VORTEX -> renderVortex(g, elapsed);
			case USUM_SURGE -> renderSurge(g, elapsed);
			case ORAS_SILHOUETTE -> renderSilhouette(g, elapsed);
			case PBR_FACE_OFF -> renderFaceOff(g, elapsed);
			case SV_SHOWDOWN -> renderShowdown(g, elapsed);
			case RBY_CLOCK -> renderClock(g, elapsed);
			case GSC_STAIRS -> renderStairs(g, elapsed);
			case DP_BLINDS -> renderBlinds(g, elapsed);
			case COLOSSEUM_SWIRL -> renderColosseum(g, elapsed);
		}
		if (intro.flashes() && elapsed >= intro.flashStart()) {
			float k = Math.min(1f, (elapsed - intro.flashStart()) / 150f);
			front(g, 900);
			g.fill(0, 0, width, height, (Math.round(255 * k) << 24) | 0xFFFFFF);
			g.pose().popPose();
		}
	}

	/** Another mod's intro on its own clock, then Showtime's white flash over its last 300 ms. */
	private void renderCustom(GuiGraphics g, long elapsed, float partialTick) {
		long t = Math.max(0, elapsed - BattleCinematic.EYES_MEET_END);
		custom.render(g, this, t, partialTick);
		long flashStart = custom.length() - (BattleCinematic.INTRO_END - BattleCinematic.FLASH_START);
		if (custom.flashes() && t >= flashStart) {
			float k = Math.min(1f, (t - flashStart) / 150f);
			front(g, 900);
			g.fill(0, 0, width, height, (Math.round(255 * k) << 24) | 0xFFFFFF);
			g.pose().popPose();
		}
	}

	// ---- IntroContext (other mods' intros) ------------------------------

	@Override
	public int width() {
		return width;
	}

	@Override
	public int height() {
		return height;
	}

	@Override
	public String opponentName() {
		return BattleCinematic.opponentName();
	}

	@Override
	public String selfName() {
		Player player = Minecraft.getInstance().player;
		return player == null ? "?" : player.getGameProfile().getName();
	}

	@Override
	public int opponentTeamSize() {
		return BattleCinematic.opponentTeamSize();
	}

	@Override
	public int ownTeamSize() {
		return BattleCinematic.ownTeamSize();
	}

	@Override
	public Player opponent() {
		return trainer();
	}

	@Override
	public Player self() {
		return selfModel();
	}

	@Override
	public boolean starIsSelf() {
		return BattleCinematic.introStarIsSelf();
	}

	@Override
	public Player opponentInWorld() {
		Minecraft mc = Minecraft.getInstance();
		return com.mystaria.showtime.api.ShowtimeHooks.player(mc.level, BattleCinematic.opponentUuid());
	}

	@Override
	public void drawTrainer(GuiGraphics graphics, boolean opponent, float x, float feetY, float scale, float bodyYaw, float brightness) {
		IntroTrainer model = opponent ? trainer() : selfModel();
		if (model != null) {
			drawModel(graphics, model, x, feetY, scale, bodyYaw, brightness);
		}
	}

	@Override
	public void drawBall(GuiGraphics graphics, float cx, float cy, float size, float degrees) {
		item(graphics, ball(), cx, cy, size, degrees);
	}

	@Override
	public void sound(net.minecraft.sounds.SoundEvent sound, float pitch, float volume) {
		BattleCinematic.play(sound, pitch, volume);
	}

	// ---- X/Y ------------------------------------------------------------

	private void renderXy(GuiGraphics g, long elapsed, float partialTick) {
		if (elapsed < BattleCinematic.EYES_MEET_END) {
			renderEyesMeetBox(g, elapsed, false);
		} else if (elapsed < BattleCinematic.PANEL_START) {
			renderWipe(g, (elapsed - BattleCinematic.EYES_MEET_END) / (float) (BattleCinematic.WIPE_END - BattleCinematic.EYES_MEET_END));
		} else {
			renderPanel(g, elapsed - BattleCinematic.PANEL_START, partialTick);
		}
	}

	/** Dialog box at the top, typed out, as in the overworld encounter: X/Y's white one, or Emerald's framed one. */
	private void renderEyesMeetBox(GuiGraphics g, long elapsed, boolean gba) {
		if (elapsed < 350) {
			return;
		}
		Component text = Component.translatable("cobblemon_battle_showtime.intro.eyes_meet");
		float scale = width >= 640 ? 1.5f : 1f;
		int boxW = Math.min(width - 24, Math.round(360 * scale));
		int boxX = 12;
		int boxY = gba ? 0 : 10;
		List<FormattedCharSequence> lines = font.split(text, Math.round((boxW - 28) / scale));
		int boxH = Math.round((lines.size() * 11 + 14) * scale);
		if (gba) {
			boxY = height - boxH - 12;
			g.fill(boxX - 3, boxY - 3, boxX + boxW + 3, boxY + boxH + 3, 0xFF5A6C84);
			g.fill(boxX - 1, boxY - 1, boxX + boxW + 1, boxY + boxH + 1, 0xFF9CD0D8);
			g.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFFF8F8F8);
		} else {
			g.fill(boxX + 2, boxY + 2, boxX + boxW + 2, boxY + boxH + 2, 0x40000000);
			g.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFFFAFAFA);
			outline(g, boxX, boxY, boxW, boxH, 0xFFB8B8B8);
		}
		int visible = (int) ((elapsed - 350) / 22);
		String full = text.getString();
		int drawn = 0;
		PoseStack pose = g.pose();
		for (int i = 0; i < lines.size(); i++) {
			String line = sequenceText(lines.get(i));
			int keep = Math.max(0, Math.min(line.length(), visible - drawn));
			drawn += line.length();
			pose.pushPose();
			pose.translate(boxX + 14, boxY + 8 * scale + i * 11 * scale, 0);
			pose.scale(scale, scale, 1f);
			if (gba) {
				g.drawString(font, line.substring(0, keep), 1, 1, 0xFFD0D0C8, false);
				g.drawString(font, line.substring(0, keep), 0, 0, 0xFF484848, false);
			} else {
				g.drawString(font, line.substring(0, keep), 0, 0, 0xFF3C3C3C, false);
			}
			pose.popPose();
		}
		if (visible >= full.length() && (elapsed / 300) % 2 == 0) {
			g.drawString(font, "▼", boxX + boxW - 14, boxY + boxH - 12, gba ? 0xFFE04830 : 0xFF3C3C3C, false);
		}
	}

	/** Black diamonds growing from the right edge until the screen is black. */
	private void renderWipe(GuiGraphics g, float progress) {
		if (progress >= 1f) {
			g.fill(0, 0, width, height, 0xFF000000);
			return;
		}
		float cell = Math.max(16, height / 7f);
		float front = progress * 1.6f;
		PoseStack pose = g.pose();
		for (int i = 0; i * cell < width + cell; i++) {
			float cx = (i + 0.5f) * cell;
			float local = Mth.clamp((front - (1f - cx / width)) / 0.6f, 0f, 1f);
			if (local <= 0f) {
				continue;
			}
			float half = local * cell * 1.05f / (float) Math.sqrt(2);
			for (int j = 0; j * cell < height + cell; j++) {
				float cy = (j + 0.5f) * cell;
				pose.pushPose();
				pose.translate(cx, cy, 0);
				pose.mulPose(Axis.ZP.rotationDegrees(45));
				pose.scale(half, half, 1f);
				g.fill(-1, -1, 1, 1, 0xFF000000);
				pose.popPose();
			}
		}
	}

	/** The challenger panel. {@code t} = ms since it appeared. */
	private void renderPanel(GuiGraphics g, long t, float partialTick) {
		renderSpeedBackground(g, t, XY_SPEED, 0x9080B8FF);
		renderTrainer(g, t);
		front(g, 500);
		renderTeamBalls(g, t);
		renderChallengeText(g, t);
		g.pose().popPose();
	}

	private void renderSpeedBackground(GuiGraphics g, long t, int[] c, int lineColor) {
		int h = height;
		int w = width;
		g.fillGradient(0, 0, w, (int) (h * 0.28f), c[0], c[1]);
		g.fillGradient(0, (int) (h * 0.28f), w, (int) (h * 0.44f), c[1], c[2]);
		g.fillGradient(0, (int) (h * 0.44f), w, (int) (h * 0.54f), c[2], c[3]);
		g.fillGradient(0, (int) (h * 0.54f), w, (int) (h * 0.68f), c[3], c[4]);
		g.fillGradient(0, (int) (h * 0.68f), w, h, c[5], c[6]);
		// Dotted floor, scrolling.
		float scroll = (t * 0.45f) % 10f;
		for (int y = (int) (h * 0.70f); y < h; y += 6) {
			for (float x = -scroll; x < w; x += 10) {
				g.fill((int) x, y, (int) x + 1, y + 1, 0x30FFFFFF);
			}
		}
		// Speed lines rushing to the left.
		for (int i = 0; i < SPEED_LINES; i++) {
			float r1 = hash(i * 3 + 1);
			float r2 = hash(i * 3 + 2);
			float r3 = hash(i * 3 + 3);
			float bias = (r1 - 0.5f) * (r1 - 0.5f) * 4f * Math.signum(r1 - 0.5f);
			int y = (int) (h * (0.47f + 0.24f * bias));
			int length = (int) (w * (0.08f + 0.30f * r2));
			float speed = w * (1.3f + 2.2f * r3) / 1000f;
			float travel = (t * speed + r2 * w * 3) % (w + length);
			int x = (int) (w - travel);
			int thickness = r3 > 0.7f ? 2 : 1;
			int color = r1 > 0.5f ? 0xA0FFFFFF : lineColor;
			g.fill(x, y, x + length, y + thickness, color);
		}
	}

	/** The opponent: runs in from the left as a silhouette, then their colors come in. */
	private void renderTrainer(GuiGraphics g, long t) {
		RemotePlayer model = trainer();
		if (model == null) {
			return;
		}
		float run = Math.min(1f, t / (float) PANEL_RUN_MS);
		float ease = 1f - (1f - run) * (1f - run);
		float centerX = Mth.lerp(ease, -width * 0.15f, width * 0.30f);
		float scale = height * 0.62f / 1.8f;
		float brightness = t < PANEL_RUN_MS ? 0.05f
				: Mth.lerp(Math.min(1f, (t - PANEL_RUN_MS) / (float) PANEL_REVEAL_MS), 0.05f, 1f);
		boolean running = t < PANEL_RUN_MS;
		model.setXRot(running ? 8f : -4f);
		drawModel(g, model, centerX, height * 0.80f, scale, running ? 125f : 150f, brightness);
	}

	/** Top right: one ball per Pokémon in the opponent's team, slid in. */
	private void renderTeamBalls(GuiGraphics g, long t) {
		int size = 14;
		int gap = 4;
		int barW = 6 * (size + gap) + 14;
		float slide = Math.min(1f, t / 400f);
		int x = Math.round(width - 8 - barW * (1f - (1f - slide) * (1f - slide)));
		int y = 8;
		if (BattleCinematic.opponentTeamSize() < 0) {
			return; // unknown: no made-up balls
		}
		g.fill(x, y, x + barW, y + size + 8, 0xA0101828);
		outline(g, x, y, barW, size + 8, 0x80FFFFFF);
		for (int i = 0; i < 6; i++) {
			int bx = x + 7 + i * (size + gap);
			int by = y + 4;
			if (i < BattleCinematic.opponentTeamSize()) {
				item(g, ball(), bx + size / 2f, by + size / 2f, size, 0f);
			} else {
				g.fill(bx + 4, by + 4, bx + size - 4, by + size - 4, 0xFF8A8F99);
			}
		}
	}

	/** Bottom text bar: "You are challenged by …!", typed out. */
	private void renderChallengeText(GuiGraphics g, long t) {
		int barY = (int) (height * 0.80f);
		g.fillGradient(0, barY, width, height, 0xE0383C44, 0xF0101216);
		g.fill(0, barY, width, barY + 2, 0xFFD0D4DC);
		if (t < PANEL_TEXT_DELAY) {
			return;
		}
		String text = Component.translatable("cobblemon_battle_showtime.intro.challenged", BattleCinematic.opponentName()).getString();
		int visible = (int) Math.min(text.length(), (t - PANEL_TEXT_DELAY) / 28);
		float scale = width >= 640 ? 2f : 1.5f;
		text(g, text.substring(0, visible), width * 0.05f, barY + (height - barY - 9 * scale) / 2f, scale, 0xFFFFFFFF, true);
	}

	// ---- Sword/Shield ---------------------------------------------------

	private void renderSwordShield(GuiGraphics g, long elapsed, float partialTick) {
		if (elapsed < BattleCinematic.EYES_MEET_END) {
			return;
		}
		if (elapsed >= SWSH_THROW) {
			renderSwordShieldThrow(g, elapsed - SWSH_THROW);
			return;
		}
		// The two halves slam in along the diagonal, then stay, their stripes streaming.
		float slam = Math.min(1f, (elapsed - BattleCinematic.EYES_MEET_END) / (float) (SWSH_SPLIT - BattleCinematic.EYES_MEET_END));
		float ease = slam * slam;
		float d = (float) Math.sqrt(width * (float) width + height * (float) height);
		long t = Math.max(0, elapsed - SWSH_SPLIT);
		PoseStack pose = g.pose();
		pose.pushPose();
		pose.translate(width / 2f, height / 2f, 0);
		pose.mulPose(Axis.ZP.rotationDegrees(SWSH_TILT));
		int left = Math.round(Mth.lerp(ease, -d, 0));
		int right = Math.round(Mth.lerp(ease, d, 0));
		g.fill(Math.round(-d), Math.round(-d), left, Math.round(d), SWSH_BLUE);
		g.fill(right, Math.round(-d), Math.round(d), Math.round(d), SWSH_PINK);
		for (int i = 0; i < 18; i++) {
			float r1 = hash(i * 7 + 11);
			float r2 = hash(i * 7 + 12);
			float span = 2 * d;
			float speed = d * (0.4f + 0.8f * r2) / 1000f;
			int thickness = 2 + Math.round(6 * r1);
			int yDown = Math.round((r1 * span + t * speed) % span - d);
			int yUp = Math.round(d - (r2 * span + t * speed) % span);
			g.fill(Math.round(-d), yDown, left, yDown + thickness, 0x2CFFFFFF);
			g.fill(right, yUp, Math.round(d), yUp + thickness, 0x2CFFFFFF);
		}
		if (slam >= 1f) {
			g.fill(-5, Math.round(-d), 5, Math.round(d), 0xFF101018);
			g.fill(-3, Math.round(-d), 3, Math.round(d), 0xFFFFFFFF);
		} else {
			g.fill(left - 3, Math.round(-d), left, Math.round(d), 0xFFFFFFFF);
			g.fill(right, Math.round(-d), right + 3, Math.round(d), 0xFFFFFFFF);
		}
		pose.popPose();
		if (elapsed < SWSH_SPLIT) {
			return;
		}

		// Both trainers, sliding in from their side, a ball in hand.
		float in = Math.min(1f, t / 450f);
		float slide = 1f - (1f - in) * (1f - in);
		float scale = height * 0.58f / 1.8f;
		IntroTrainer me = selfModel();
		if (me != null) {
			me.setXRot(-4f);
			drawModel(g, me, Mth.lerp(slide, -width * 0.25f, width * 0.24f), height * 0.97f, scale, 150f, 1f);
		}
		IntroTrainer opponent = trainer();
		if (opponent != null) {
			opponent.setXRot(-4f);
			drawModel(g, opponent, Mth.lerp(slide, width * 1.25f, width * 0.76f), height * 0.97f, scale, 210f, 1f);
		}

		front(g, 500);
		float textScale = width >= 640 ? 2f : 1.5f;
		float plate = 9 * textScale + 10;
		String selfName = selfName();
		String opponentName = BattleCinematic.opponentName();
		float plateIn = Math.min(1f, t / 300f);
		int selfPlateW = Math.round((font.width(selfName) * textScale + 30) * plateIn);
		int opponentPlateW = Math.round((font.width(opponentName) * textScale + 30) * plateIn);
		g.fill(0, 10, selfPlateW, Math.round(10 + plate), 0xC0081A55);
		g.fill(width - opponentPlateW, 10, width, Math.round(10 + plate), 0xC055081E);
		if (plateIn >= 1f) {
			text(g, selfName, 14, 15, textScale, 0xFFFFFFFF, true);
			text(g, opponentName, width - 14 - font.width(opponentName) * textScale, 15, textScale, 0xFFFFFFFF, true);
		}
		if (elapsed >= SWSH_VS) {
			long v = elapsed - SWSH_VS;
			float pop = v < 160 ? Mth.lerp(v / 160f, 3.2f, 0.9f) : v < 260 ? Mth.lerp((v - 160) / 100f, 0.9f, 1f) : 1f;
			float shake = v < 400 ? (float) Math.sin(v * 0.35) * 3f * (1f - v / 400f) : 0f;
			String vs = Component.translatable("cobblemon_battle_showtime.intro.vs").getString();
			float vsScale = height / 55f * pop;
			float vx = width / 2f - font.width(vs) * vsScale / 2f + shake;
			float vy = height * 0.48f - 4.5f * vsScale;
			for (int[] o : new int[][] { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 }, { 1, 1 } }) {
				text(g, vs, vx + o[0] * vsScale * 0.6f, vy + o[1] * vsScale * 0.6f, vsScale, 0xFF14141E, false);
			}
			text(g, vs, vx, vy, vsScale, 0xFFFFD43A, false);
		}
		g.pose().popPose();
	}

	/** The opponent, close, throws their ball at us over pink speed lines. */
	private void renderSwordShieldThrow(GuiGraphics g, long t) {
		renderSpeedBackground(g, t, SWSH_SPEED, 0x90FF9CCB);
		IntroTrainer opponent = trainer();
		if (opponent != null) {
			float push = 1f + 0.06f * Math.min(1f, t / 600f);
			opponent.setXRot(4f);
			drawModel(g, opponent, width * 0.40f, height * 1.30f, height * 1.0f / 1.8f * push, 205f, 1f);
		}
		if (t >= 300) {
			float k = Math.min(1f, (t - 300) / 300f);
			float x = Mth.lerp(k, width * 0.52f, width * 0.78f);
			float y = Mth.lerp(k, height * 0.42f, height * 0.30f) - (float) Math.sin(k * Math.PI) * height * 0.08f;
			front(g, 500);
			item(g, ball(), x, y, Mth.lerp(k * k, 18f, height * 0.45f), t * 1.4f);
			g.pose().popPose();
		}
	}

	// ---- Diamond/Pearl/Platinum -----------------------------------------

	private void renderDiamondPearl(GuiGraphics g, long elapsed) {
		if (elapsed < BattleCinematic.EYES_MEET_END) {
			return;
		}
		if (elapsed < DP_WIPE_END) {
			// The screen's four corners close in on a spinning Poké Ball.
			float p = (elapsed - BattleCinematic.EYES_MEET_END) / (float) (DP_WIPE_END - BattleCinematic.EYES_MEET_END);
			float a = p * p * (3 - 2 * p);
			int hw = Math.round(width / 2f * a);
			int hh = Math.round(height / 2f * a);
			g.fill(0, 0, hw, hh, 0xFF000000);
			g.fill(width - hw, 0, width, hh, 0xFF000000);
			g.fill(0, height - hh, hw, height, 0xFF000000);
			g.fill(width - hw, height - hh, width, height, 0xFF000000);
			if (p < 0.92f) {
				item(g, ball(), width / 2f, height / 2f, height * (0.55f - 0.35f * p), 720f * p);
			}
			return;
		}
		if (elapsed < DP_FIELD) {
			g.fill(0, 0, width, height, 0xFF000000);
			if (elapsed >= DP_BAND) {
				// A blue band opens across the middle, then a white burst out of it.
				float b = Math.min(1f, (elapsed - DP_BAND) / 200f);
				int half = Math.round(height * 0.18f * (1f - (1f - b) * (1f - b)));
				g.fillGradient(0, height / 2 - half, width, height / 2, 0xFF1C3C90, 0xFF5C9CF0);
				g.fillGradient(0, height / 2, width, height / 2 + half, 0xFF5C9CF0, 0xFF1C3C90);
				g.fill(0, height / 2 - 1, width, height / 2 + 1, 0xC0FFFFFF);
			}
			if (elapsed >= DP_BURST) {
				float k = (elapsed - DP_BURST) / (float) (DP_FIELD - DP_BURST);
				rays(g, width / 2f, height / 2f, 48, 0f, Math.max(width, height) * (0.2f + 0.9f * k), 3f + 6f * k,
						(Math.round(255 * Math.min(1f, k * 2f)) << 24) | 0xFFFFFF, 0f);
				g.fill(0, 0, width, height, (Math.round(255 * k * k) << 24) | 0xFFFFFF);
			}
			return;
		}

		long t = elapsed - DP_FIELD;
		backdrop(g, Scene.DIAMOND_PEARL, 0.36f, t);
		float in = Math.min(1f, t / 700f);
		float slide = 1f - (1f - in) * (1f - in) * (1f - in);
		float farX = Mth.lerp(slide, -width * 0.3f, width * 0.70f);
		float nearX = Mth.lerp(slide, width * 1.3f, width * 0.26f);
		float farY = height * 0.46f;
		float nearY = height * 0.84f;
		platform(g, farX, farY, width * 0.19f, height * 0.065f, 0xFF46682C, 0xFF78AC50, 0xFFC0E290);
		platform(g, nearX, nearY, width * 0.27f, height * 0.09f, 0xFF46682C, 0xFF78AC50, 0xFFC0E290);
		IntroTrainer opponent = trainer();
		if (opponent != null) {
			opponent.setXRot(-4f);
			drawModel(g, opponent, farX, farY - height * 0.01f, height * 0.30f / 1.8f, 205f, 1f);
		}
		IntroTrainer me = selfModel();
		if (me != null) {
			me.setXRot(0f);
			drawModel(g, me, nearX - width * 0.03f, height * 1.12f, height * 0.62f / 1.8f, 30f, 1f);
		}
		front(g, 500);
		if (t < 200) {
			g.fill(0, 0, width, height, (Math.round(255 * (1f - t / 200f)) << 24) | 0xFFFFFF);
		}
		if (t >= 700) {
			ballBar(g, 0, height * 0.30f, width * 0.42f, BattleCinematic.opponentTeamSize(), true, Math.min(1f, (t - 700) / 300f), 0xFF404858);
		}
		if (t >= 950) {
			int boxY = Math.round(height * 0.78f);
			g.fill(0, boxY, width, height, 0xFF586878);
			g.fill(3, boxY + 3, width - 3, height - 3, 0xFFA8B8C8);
			g.fill(6, boxY + 6, width - 6, height - 6, 0xFFF8F8F8);
			typed(g, Component.translatable("cobblemon_battle_showtime.intro.challenged", BattleCinematic.opponentName()).getString(),
					t - 950, 16, boxY + 6, height - 6, 0xFF505058, 0xFFD0D0D8);
		}
		g.pose().popPose();
	}

	// ---- Emerald ---------------------------------------------------------

	private void renderEmerald(GuiGraphics g, long elapsed) {
		if (elapsed < BattleCinematic.EYES_MEET_END) {
			renderEyesMeetBox(g, elapsed, true);
			return;
		}
		if (elapsed < EM_OPEN) {
			// Bands of black, each pushed across by a rolling Poké Ball, every other one from the other side.
			float bandH = height / (float) EM_BANDS;
			for (int i = 0; i < EM_BANDS; i++) {
				long local = elapsed - BattleCinematic.EYES_MEET_END - i * 70L;
				float p = Mth.clamp(local / (float) (EM_ROLL_END - BattleCinematic.EYES_MEET_END - (EM_BANDS - 1) * 70L), 0f, 1f);
				float front = p * (width + bandH);
				int top = Math.round(i * bandH);
				int bottom = Math.round((i + 1) * bandH);
				boolean fromLeft = i % 2 == 0;
				if (fromLeft) {
					g.fill(0, top, Math.round(front - bandH / 2f), bottom, 0xFF000000);
				} else {
					g.fill(Math.round(width - front + bandH / 2f), top, width, bottom, 0xFF000000);
				}
				if (p > 0f && p < 1f) {
					float x = fromLeft ? front - bandH / 2f : width - front + bandH / 2f;
					float roll = (float) Math.toDegrees(front / (bandH / 2f)) * (fromLeft ? 1 : -1);
					item(g, ball(), x, top + bandH / 2f, bandH * 0.95f, roll);
				}
			}
			return;
		}

		long t = elapsed - EM_OPEN;
		backdrop(g, Scene.EMERALD, 0.33f, t);
		float slide = Math.min(1f, t / 1000f);
		float farX = Mth.lerp(slide, -width * 0.25f, width * 0.70f);
		float nearX = Mth.lerp(slide, width * 1.25f, width * 0.28f);
		float farY = height * 0.42f;
		float nearY = height * 0.74f;
		platform(g, farX, farY, width * 0.17f, height * 0.06f, 0xFF4C7834, 0xFF84B85C, 0xFFC8EA9C);
		platform(g, nearX, nearY, width * 0.22f, height * 0.075f, 0xFF4C7834, 0xFF84B85C, 0xFFC8EA9C);
		IntroTrainer opponent = trainer();
		if (opponent != null) {
			opponent.setXRot(-4f);
			drawModel(g, opponent, farX, farY - height * 0.01f, height * 0.28f / 1.8f, 200f, 1f);
		}
		IntroTrainer me = selfModel();
		if (me != null) {
			me.setXRot(0f);
			drawModel(g, me, nearX, nearY + height * 0.02f, height * 0.40f / 1.8f, 30f, 1f);
		}
		front(g, 500);
		if (elapsed < EM_OPEN_END) {
			// The black opens from a white line across the middle.
			float o = (elapsed - EM_OPEN) / (float) (EM_OPEN_END - EM_OPEN);
			int half = Math.round(height / 2f * (1f - o * o));
			g.fill(0, 0, width, half, 0xFF000000);
			g.fill(0, height - half, width, height, 0xFF000000);
			g.fill(0, half - 1, width, half, 0xFFFFFFFF);
			g.fill(0, height - half, width, height - half + 1, 0xFFFFFFFF);
		}
		if (t >= 1050) {
			float k = Math.min(1f, (t - 1050) / 250f);
			ballBar(g, 0, height * 0.24f, width * 0.40f, BattleCinematic.opponentTeamSize(), true, k, 0xFF303038);
			ballBar(g, width * 0.58f, height * 0.60f, width * 0.42f, BattleCinematic.ownTeamSize(), false, k, 0xFF303038);
		}
		if (t >= 1250) {
			int boxY = Math.round(height * 0.78f);
			g.fill(0, boxY, width, height, 0xFFC85030);
			g.fill(3, boxY + 3, width - 3, height - 3, 0xFFE8D0A0);
			g.fill(6, boxY + 6, width - 6, height - 6, 0xFF284860);
			typed(g, Component.translatable("cobblemon_battle_showtime.intro.would_like", BattleCinematic.opponentName()).getString(),
					t - 1250, 16, boxY + 6, height - 6, 0xFFF8F8F8, 0xFF687888);
		}
		g.pose().popPose();
	}

	// ---- Black/White -----------------------------------------------------

	private void renderBlackWhite(GuiGraphics g, long elapsed) {
		if (elapsed < BattleCinematic.EYES_MEET_END) {
			return;
		}
		if (elapsed < BW_FIELD) {
			// The world camera rushes at the opponent (BattleCinematic): radial streaks over it, then white.
			float p = (elapsed - BattleCinematic.EYES_MEET_END) / (float) (BW_FIELD - BattleCinematic.EYES_MEET_END);
			float reach = Math.max(width, height) * 0.8f;
			rays(g, width / 2f, height / 2f, 90, reach * (0.55f - 0.35f * p), reach, 1.5f,
					(Math.round(170 * Math.min(1f, p * 2.5f)) << 24) | 0xFFFFFF, elapsed * 0.0004f);
			if (elapsed >= BW_WHITE) {
				float w = (elapsed - BW_WHITE) / (float) (BW_FIELD - BW_WHITE);
				g.fill(0, 0, width, height, (Math.round(255 * Math.min(1f, w * 1.4f)) << 24) | 0xFFFFFF);
			}
			return;
		}

		long t = elapsed - BW_FIELD;
		backdrop(g, Scene.BLACK_WHITE, 0.38f, t);
		// The view pulls back from the opponent's platform.
		float pull = Math.min(1f, t / 1000f);
		float zoom = Mth.lerp(1f - (1f - pull) * (1f - pull) * (1f - pull), 2.6f, 1f);
		float focusX = width * 0.66f;
		float focusY = height * 0.40f;
		float farX = width * 0.66f;
		float farY = height * 0.50f;
		float nearX = width * 0.20f;
		float nearY = height * 0.93f;
		platform(g, focusX + (nearX - focusX) * zoom, focusY + (nearY - focusY) * zoom, width * 0.30f * zoom, height * 0.10f * zoom,
				0xFF1E5E58, 0xFF2F7F78, 0xFF6CC2B0);
		platform(g, focusX + (farX - focusX) * zoom, focusY + (farY - focusY) * zoom, width * 0.20f * zoom, height * 0.065f * zoom,
				0xFF1E5E58, 0xFF2F7F78, 0xFF6CC2B0);
		IntroTrainer opponent = trainer();
		if (opponent != null) {
			float brightness = t < 1000 ? 0.05f : Mth.lerp(Math.min(1f, (t - 1000) / 400f), 0.05f, 1f);
			opponent.setXRot(-4f);
			drawModel(g, opponent, focusX + (farX - focusX) * zoom, focusY + (farY - height * 0.01f - focusY) * zoom,
					height * 0.32f / 1.8f * zoom, 200f, brightness);
		}
		front(g, 500);
		if (t < 400) {
			g.fill(0, 0, width, height, (Math.round(255 * (1f - t / 400f)) << 24) | 0xFFFFFF);
		}
		if (t >= 1150) {
			ballBar(g, 0, height * 0.18f, width * 0.40f, BattleCinematic.opponentTeamSize(), true, Math.min(1f, (t - 1150) / 300f), 0xFF20242C);
		}
		if (t >= 1400) {
			int boxY = Math.round(height * 0.80f);
			g.fill(4, boxY, width - 4, height - 4, 0xD0182028);
			outline(g, 4, boxY, width - 8, height - boxY - 4, 0xFF8CA0B0);
			typed(g, Component.translatable("cobblemon_battle_showtime.intro.challenged", BattleCinematic.opponentName()).getString(),
					t - 1400, 18, boxY, height - 4, 0xFFFFFFFF, 0xFF404850);
		}
		g.pose().popPose();
	}

	// =====================================================================
	// Drawing helpers
	// =====================================================================

	// ---- Sun/Moon: the vortex ------------------------------------------

	private void renderVortex(GuiGraphics g, long elapsed) {
		if (elapsed < BattleCinematic.EYES_MEET_END) {
			return;
		}
		float cx = width / 2f;
		float cy = height / 2f;
		float diagonal = (float) Math.hypot(width, height);
		if (elapsed < SM_WHITE) {
			// Over the world: a soft core swelling, rainbow rings glowing outward, rays spinning, up to white.
			float k = (elapsed - BattleCinematic.EYES_MEET_END) / (float) (SM_WHITE - BattleCinematic.EYES_MEET_END);
			radial(g, cx, cy, diagonal * (0.15f + 0.5f * k), (Math.round(255 * k) << 24) | 0xFFFFFF,
					(Math.round(140 * k) << 24) | 0xFFE8B0, 0x00FFC070, 24);
			for (int i = 0; i < 7; i++) {
				float r = diagonal * (0.06f + 0.075f * i) * (0.5f + 0.9f * k);
				glowRing(g, cx, cy, r, hue(i / 7f + k * 0.5f) & 0xFFFFFF, k * (i % 2 == 0 ? 0.4f : -0.4f));
			}
			rays(g, cx, cy, 30, diagonal * 0.04f, diagonal * (0.3f + 0.6f * k), 2f + 4f * k, (Math.round(150 * k) << 24) | 0xFFFFFF, k);
			g.fill(0, 0, width, height, (Math.round(255 * k * k * k) << 24) | 0xFFFFFF);
			return;
		}
		long t = elapsed - SM_WHITE;
		// The vortex: a deep warm gradient, spiral arms in two layers (a soft glow under bright bands), light at its eye.
		g.fill(0, 0, width, height, 0xFF3A0C02);
		radial(g, cx, cy, diagonal * 0.75f, 0xFFFFE6A0, 0xFFF07818, 0xFF5A1404, 30);
		float spin = -t / 1100f;
		spiral(g, cx, cy, diagonal * 0.8f, 7, 3.0f / diagonal, spin, 0x26FFE0A0, 0x18A02008);
		spiral(g, cx, cy, diagonal * 0.8f, 7, 3.0f / diagonal, spin + 0.012f, 0x70FFD070, 0x40B03810);
		// Embers drawn into the eye of the vortex.
		for (int i = 0; i < 46; i++) {
			float speed = 0.25f + 0.35f * hash(i * 5 + 1);
			float life = ((t * speed / 1000f) + hash(i * 5 + 2)) % 1f;
			float r = diagonal * 0.5f * (1f - life);
			float angle = hash(i * 5 + 3) * Mth.TWO_PI + life * 3.5f;
			float size = 1.5f + 3f * hash(i * 5 + 4) * (1f - life * 0.5f);
			int a = Math.round(230 * Math.min(1f, life * 4f) * (1f - life));
			g.fill(Math.round(cx + Mth.cos(angle) * r), Math.round(cy + Mth.sin(angle) * r * 0.8f),
					Math.round(cx + Mth.cos(angle) * r + size), Math.round(cy + Mth.sin(angle) * r * 0.8f + size), (a << 24) | 0xFFF0C0);
		}
		rays(g, cx, cy, 16, diagonal * 0.05f, diagonal * 0.3f, 3f, 0x30FFF4D0, t / 4000f);
		IntroTrainer model = trainer();
		if (model != null) {
			float grow = Math.min(1f, t / 600f);
			float ease = 1f - (1f - grow) * (1f - grow);
			float scale = height * (0.56f + 0.10f * ease) / 1.8f;
			model.setXRot(-6f);
			// A glow behind the trainer, so their outline stands out of the vortex.
			ellipse(g, cx, height * 0.62f, height * 0.20f, height * 0.34f, 0x40FFF0C0);
			drawModel(g, model, cx, height * 0.86f + (1 - ease) * height * 0.1f, scale, 180f + 12f * (float) Math.sin(t / 700.0), 1f);
		}
		if (elapsed >= SM_THROW) {
			// The ball flies at us, with a trail of light.
			float k = Math.min(1f, (elapsed - SM_THROW - 150) / 600f);
			if (k > 0) {
				front(g, 450);
				for (int i = 1; i <= 5; i++) {
					float kk = Math.max(0, k - i * 0.05f);
					ellipse(g, cx + width * 0.12f * (1 - kk), height * (0.45f - 0.1f * kk), (8 + height * 0.16f * kk * kk) * 0.6f,
							(8 + height * 0.16f * kk * kk) * 0.6f, ((60 - i * 10) << 24) | 0xFFF4D0);
				}
				item(g, ball(), cx + width * 0.12f * (1 - k), height * (0.45f - 0.1f * k), 16 + height * 0.35f * k * k, 720f * k);
				g.pose().popPose();
			}
		}
		front(g, 500);
		// A vignette, darker at the corners.
		g.fillGradient(0, 0, width, Math.round(height * 0.18f), 0x60200400, 0x00000000);
		g.fillGradient(0, Math.round(height * 0.78f), width, height, 0x00000000, 0x70200400);
		if (elapsed >= SM_TEXT) {
			textBox(g, Component.translatable("cobblemon_battle_showtime.intro.challenged", BattleCinematic.opponentName()).getString(),
					elapsed - SM_TEXT, 0xF0103848, 0xFF2CA0B8);
		}
		if (t < 350) {
			g.fill(0, 0, width, height, (Math.round(255 * (1f - t / 350f)) << 24) | 0xFFFFFF);
		}
		g.pose().popPose();
	}

	// ---- Red/Blue: the clock ---------------------------------------------

	private void renderClock(GuiGraphics g, long elapsed) {
		if (elapsed < BattleCinematic.EYES_MEET_END) {
			return;
		}
		if (elapsed < RB_FIELD) {
			// Two flashes, then two hands sweeping from the centre, each taking half the screen to black.
			long t = elapsed - BattleCinematic.EYES_MEET_END;
			if (t < 700 && (t / 120) % 2 == 0) {
				g.fill(0, 0, width, height, 0x90C8E0A8);
			}
			float k = Mth.clamp((elapsed - BattleCinematic.EYES_MEET_END - 650) / (float) (RB_BLACK - BattleCinematic.EYES_MEET_END - 650), 0f, 1f);
			if (k > 0) {
				sweep(g, width / 2f, height / 2f, (float) Math.hypot(width, height), -Mth.HALF_PI, k * Mth.PI, 0xFF000000);
				sweep(g, width / 2f, height / 2f, (float) Math.hypot(width, height), Mth.HALF_PI, k * Mth.PI, 0xFF000000);
			}
			if (elapsed >= RB_BLACK) {
				g.fill(0, 0, width, height, 0xFF000000);
			}
			return;
		}
		// The field in Game Boy greens; the colour floods in from RB_COLOUR.
		long t = elapsed - RB_FIELD;
		float mono = elapsed < RB_COLOUR ? 1f : 1f - Math.min(1f, (elapsed - RB_COLOUR) / 700f);
		fieldScene(g, Scene.RED_BLUE, 0.36f, t, mono, 900, RB_COLOUR - RB_FIELD, 0xFF2A4A2A, true);
		front(g, 500);
		if (t < 200) {
			g.fill(0, 0, width, height, (Math.round(255 * (1f - t / 200f)) << 24));
		}
		if (elapsed >= RB_COLOUR + 300) {
			textBox(g, Component.translatable("cobblemon_battle_showtime.intro.retro.wants", BattleCinematic.opponentName()).getString(),
					elapsed - RB_COLOUR - 300, 0xF0F8F8F0, 0xFF303830);
		}
		g.pose().popPose();
	}

	/** A wedge from {@code (cx, cy)}, from angle {@code from} turning {@code turn} radians clockwise, filled. */
	private static void sweep(GuiGraphics g, float cx, float cy, float radius, float from, float turn, int color) {
		int steps = Math.max(2, Math.round(turn / Mth.TWO_PI * 180));
		for (int i = 0; i < steps; i++) {
			float a = from + turn * i / steps;
			// A thin triangle as a long rotated bar from the centre, wide enough to overlap its neighbour.
			PoseStack pose = g.pose();
			pose.pushPose();
			pose.translate(cx, cy, 0);
			pose.mulPose(Axis.ZP.rotation(a));
			float width = radius * (turn / steps) + 2f;
			g.fill(0, Math.round(-width / 2), Math.round(radius), Math.round(width / 2 + 1), color);
			pose.popPose();
		}
	}

	// ---- Gold/Silver: the stairs ----------------------------------------

	private void renderStairs(GuiGraphics g, long elapsed) {
		if (elapsed < BattleCinematic.EYES_MEET_END) {
			return;
		}
		if (elapsed < GS_FIELD) {
			// Flashes in pink and grey, then a staircase of raised blocks eating the screen from the top left.
			long t = elapsed - BattleCinematic.EYES_MEET_END;
			if (t < 700) {
				int phase = (int) (t / 110) % 4;
				if (phase == 0) {
					g.fill(0, 0, width, height, 0x80F0A8C0);
				} else if (phase == 2) {
					g.fill(0, 0, width, height, 0x90707070);
				}
			}
			float k = Mth.clamp((elapsed - GS_STAIRS) / (float) (GS_BLACK - GS_STAIRS), 0f, 1f);
			if (elapsed >= GS_STAIRS) {
				int cell = Math.max(18, height / 8);
				int columns = width / cell + 1;
				int rows = height / cell + 1;
				for (int i = 0; i < columns; i++) {
					for (int j = 0; j < rows; j++) {
						// Steps: a whole row of the staircase falls at once.
						float order = (i + j * 1.6f) / (columns + rows * 1.6f);
						float drop = Mth.clamp((k * 1.15f - order) * 6f, 0f, 1f);
						if (drop > 0) {
							block(g, i * cell, Math.round(j * cell - cell * (1 - drop) * 0.6f), cell, Math.round(255 * drop));
						}
					}
				}
			}
			if (elapsed >= GS_BLACK) {
				g.fill(0, 0, width, height, 0xFF000000);
			}
			return;
		}
		long t = elapsed - GS_FIELD;
		fieldScene(g, Scene.GOLD_SILVER, 0.36f, t, 0f, 800, GS_COLOUR - GS_FIELD, 0xFF5A3A20, true);
		front(g, 500);
		if (t < 250) {
			g.fill(0, 0, width, height, (Math.round(255 * (1f - t / 250f)) << 24) | 0xFFFFFF);
		}
		if (elapsed >= GS_COLOUR + 250) {
			textBox(g, Component.translatable("cobblemon_battle_showtime.intro.retro.wants", BattleCinematic.opponentName()).getString(),
					elapsed - GS_COLOUR - 250, 0xF0FFF8E8, 0xFF8A5A30);
		}
		g.pose().popPose();
	}

	/** A raised black block: lit top and left edges, a shaded bottom and right. */
	private static void block(GuiGraphics g, int x, int y, int size, int alpha) {
		int a = alpha << 24;
		g.fill(x, y, x + size, y + size, a | 0x101010);
		g.fill(x, y, x + size, y + 3, a | 0x484848);
		g.fill(x, y, x + 3, y + size, a | 0x383838);
		g.fill(x, y + size - 3, x + size, y + size, a);
		g.fill(x + size - 3, y, x + size, y + size, a);
	}

	// ---- Diamond/Pearl: the blinds ----------------------------------------

	private void renderBlinds(GuiGraphics g, long elapsed) {
		if (elapsed < BattleCinematic.EYES_MEET_END) {
			return;
		}
		float cx = width / 2f;
		float cy = height / 2f;
		if (elapsed < DB_FIELD) {
			long t = elapsed - BattleCinematic.EYES_MEET_END;
			// Two white flashes over the world.
			for (long at : new long[] {0, 330}) {
				long since = t - at;
				if (since >= 0 && since < 260) {
					float k = since < 80 ? since / 80f : 1f - (since - 80) / 180f;
					g.fill(0, 0, width, height, (Math.round(230 * k) << 24) | 0xFFFFFF);
				}
			}
			if (elapsed >= DB_BALL && elapsed < DB_BLACK) {
				float k = (elapsed - DB_BALL) / (float) (DB_BLACK - DB_BALL);
				float size = height * (0.12f + 0.25f * k);
				ellipse(g, cx, cy, size * 0.9f, size * 0.9f, (Math.round(120 * (1 - k)) << 24) | 0xFFFFFF);
				item(g, ball(), cx, cy, size, k * 1080f);
			}
			if (elapsed >= DB_BLINDS) {
				// Blinds: bands sliding in from alternate sides, each a little after the last.
				float k = (elapsed - DB_BLINDS) / (float) (DB_BLACK - DB_BLINDS);
				int bands = 10;
				float bandHeight = height / (float) bands;
				for (int i = 0; i < bands; i++) {
					float d = Mth.clamp(k * 1.5f - i * 0.05f, 0f, 1f);
					float ease = 1f - (1f - d) * (1f - d);
					int y1 = Math.round(i * bandHeight);
					int y2 = Math.round((i + 1) * bandHeight) + 1;
					if (i % 2 == 0) {
						g.fill(0, y1, Math.round(width * ease), y2, 0xFF000000);
						g.fill(Math.round(width * ease) - 3, y1, Math.round(width * ease), y2, 0xFF606878);
					} else {
						g.fill(Math.round(width * (1 - ease)), y1, width, y2, 0xFF000000);
						g.fill(Math.round(width * (1 - ease)), y1, Math.round(width * (1 - ease)) + 3, y2, 0xFF606878);
					}
				}
			}
			return;
		}
		long t = elapsed - DB_FIELD;
		fieldScene(g, Scene.DIAMOND_PEARL, 0.36f, t, 0f, 700, 0, 0, false);
		front(g, 500);
		// The blinds open again, from the middle out.
		float open = Math.min(1f, t / 450f);
		int bands = 10;
		float bandHeight = height / (float) bands;
		for (int i = 0; i < bands; i++) {
			int y1 = Math.round(i * bandHeight);
			int y2 = Math.round((i + 1) * bandHeight) + 1;
			int w = Math.round(width * (1 - open));
			if (i % 2 == 0) {
				g.fill(width - w, y1, width, y2, 0xFF000000);
			} else {
				g.fill(0, y1, w, y2, 0xFF000000);
			}
		}
		g.pose().popPose();
	}

	// ---- Colosseum: the twisting world (overlay of BattleCinematic's shots)

	private void renderColosseum(GuiGraphics g, long elapsed) {
		if (elapsed < BattleCinematic.EYES_MEET_END) {
			return;
		}
		float cx = width / 2f;
		float cy = height / 2f;
		float diagonal = (float) Math.hypot(width, height);
		front(g, 500);
		if (elapsed < CO_WIDE) {
			// The spiral twisting the view: warm and green arms turning faster, the world dissolving into them.
			float k = (elapsed - BattleCinematic.EYES_MEET_END) / (float) (CO_WIDE - BattleCinematic.EYES_MEET_END);
			float spin = k * k * 3f;
			spiral(g, cx, cy, diagonal * 0.7f, 5, 4f / diagonal, spin, (Math.round(140 * k) << 24) | 0xE0A040,
					(Math.round(140 * k) << 24) | 0x50A050);
			spiral(g, cx, cy, diagonal * 0.7f, 5, 4f / diagonal, -spin * 0.7f, (Math.round(90 * k) << 24) | 0xC04080,
					(Math.round(90 * k) << 24) | 0x3060C0);
			if (k > 0.8f) {
				g.fill(0, 0, width, height, (Math.round(255 * (k - 0.8f) / 0.2f) << 24) | 0x201810);
			}
		} else {
			int bars = Math.round(height * 0.08f);
			g.fill(0, 0, width, bars, 0xFF000000);
			g.fill(0, height - bars, width, height, 0xFF000000);
			long since = elapsed - CO_WIDE;
			if (since < 300) {
				g.fill(0, 0, width, height, (Math.round(255 * (1f - since / 300f)) << 24) | 0x201810);
			}
			if (elapsed < CO_GROUND) {
				textBox(g, Component.translatable("cobblemon_battle_showtime.intro.retro.launched", BattleCinematic.opponentName()).getString(),
						elapsed - CO_WIDE - 200, 0xE0202020, 0xFFB0A080);
			} else {
				long cut = elapsed - CO_GROUND;
				if (cut < 120) {
					g.fill(0, 0, width, height, 0xB0000000);
				}
			}
		}
		g.pose().popPose();
	}

	/**
	 * The battlefield with both trainers on their platforms (2026-11-10, the retro revisits): they slide in from the
	 * sides as silhouettes over {@code slideMs}, and take their colours at {@code colourAt} (0: from the start).
	 */
	private void fieldScene(GuiGraphics g, Scene scene, float horizon, long t, float mono, long slideMs, long colourAt,
			int silhouette, boolean silhouettes) {
		backdrop(g, scene, horizon, t, mono);
		float in = Math.min(1f, t / (float) slideMs);
		float slide = 1f - (1f - in) * (1f - in) * (1f - in);
		float farX = Mth.lerp(slide, width * 1.3f, width * 0.70f);
		float nearX = Mth.lerp(slide, -width * 0.3f, width * 0.26f);
		float farY = height * 0.46f;
		float nearY = height * 0.84f;
		platform(g, farX, farY, width * 0.19f, height * 0.065f, tone(0xFF46682C, mono), tone(0xFF78AC50, mono), tone(0xFFC0E290, mono));
		platform(g, nearX, nearY, width * 0.27f, height * 0.09f, tone(0xFF46682C, mono), tone(0xFF78AC50, mono), tone(0xFFC0E290, mono));
		float colour = !silhouettes || t >= colourAt + 400 ? 1f : t < colourAt ? 0f : (t - colourAt) / 400f;
		IntroTrainer opponent = trainer();
		if (opponent != null) {
			opponent.setXRot(-4f);
			drawModel(g, opponent, farX, farY - height * 0.01f, height * 0.30f / 1.8f, 205f, colour);
		}
		IntroTrainer me = selfModel();
		if (me != null) {
			me.setXRot(0f);
			drawModel(g, me, nearX - width * 0.03f, height * 1.12f, height * 0.62f / 1.8f, 30f, colour);
		}
	}

	/** A colour turned toward a Game Boy's four greens, by its brightness ({@code mono}: how far). */
	private static int tone(int color, float mono) {
		if (mono <= 0f) {
			return color;
		}
		float luma = (0.30f * (color >> 16 & 0xFF) + 0.59f * (color >> 8 & 0xFF) + 0.11f * (color & 0xFF)) / 255f;
		int[] greens = {0xFF0F380F, 0xFF306230, 0xFF8BAC0F, 0xFF9BBC0F};
		float scaled = Mth.clamp(luma * 3f, 0f, 2.999f);
		int low = (int) scaled;
		int green = lerpColor(greens[low], greens[low + 1], scaled - low);
		return lerpColor(color, color & 0xFF000000 | green & 0xFFFFFF, mono);
	}

	// ---- Ultra Sun/Ultra Moon: the surge --------------------------------

	private void renderSurge(GuiGraphics g, long elapsed) {
		if (elapsed < BattleCinematic.EYES_MEET_END) {
			return;
		}
		float cx = width / 2f;
		float cy = height / 2f;
		float diagonal = (float) Math.hypot(width, height);
		if (elapsed < US_WHITE) {
			// The starburst: a glowing core over the world, its light shifting through the colours, rays turning out.
			float k = (elapsed - BattleCinematic.EYES_MEET_END) / (float) (US_WHITE - BattleCinematic.EYES_MEET_END);
			float shift = k * 1.3f + 0.5f;
			radial(g, cx, cy, diagonal * (0.25f + 0.6f * k), 0xFFFFFF | (Math.round(255 * k) << 24),
					(hue(shift) & 0xFFFFFF) | (Math.round(150 * k) << 24), 0x00000000 | (hue(shift + 0.15f) & 0xFFFFFF), 28);
			rays(g, cx, cy, 36, diagonal * 0.02f, diagonal * (0.25f + 0.5f * k), 2f + 5f * k,
					(Math.round(110 * k) << 24) | (hue(shift + 0.3f) & 0xFFFFFF), k * 0.8f);
			rays(g, cx, cy, 18, 0, diagonal * (0.15f + 0.35f * k), 1.5f + 2f * k, (Math.round(220 * k) << 24) | 0xFFFFFF, -k * 0.5f);
			g.fill(0, 0, width, height, (Math.round(255 * k * k * k) << 24) | 0xFFFFFF);
			return;
		}
		long t = elapsed - US_WHITE;
		// Deep space violet round the edges, cyan-white energy in the middle.
		g.fill(0, 0, width, height, 0xFF070420);
		radial(g, cx, cy * 0.98f, diagonal * 0.62f, 0xFFF4FBFF, 0xFF4FC8FF, 0xFF2A1068, 30);
		// Two glowing rings turning opposite ways, breathing.
		float breathe = 1f + 0.04f * Mth.sin(t / 180f);
		glowRing(g, cx, cy, diagonal * 0.27f * breathe, 0x7FE6FF, t / 2400f);
		glowRing(g, cx, cy, diagonal * 0.36f / breathe, 0xB07CFF, -t / 3000f);
		// Lightning: bolts flaring out from the inner ring, each on its own rhythm, with branches.
		for (int i = 0; i < 12; i++) {
			long period = 260 + Math.round(hash(i * 13 + 3) * 300);
			long phase = (t + Math.round(hash(i * 13 + 4) * period)) % period;
			float life = phase / (float) period;
			if (life > 0.55f) {
				continue; // dark between flashes
			}
			int seed = i * 977 + (int) ((t + Math.round(hash(i * 13 + 4) * period)) / period);
			float alpha = life < 0.1f ? life / 0.1f : 1f - (life - 0.1f) / 0.45f;
			float angle = (float) (i * Mth.TWO_PI / 12) + (hash(seed) - 0.5f) * 0.4f;
			float from = diagonal * 0.2f;
			float to = diagonal * (0.36f + 0.16f * hash(seed + 1));
			bolt(g, cx + Mth.cos(angle) * from, cy + Mth.sin(angle) * from, cx + Mth.cos(angle) * to, cy + Mth.sin(angle) * to,
					seed, 4, alpha, i % 3 == 0 ? 0xC8A0FF : 0x9AF0FF);
		}
		// Sparks drifting outward.
		for (int i = 0; i < 40; i++) {
			float speed = 0.06f + 0.1f * hash(i * 7 + 11);
			float life = ((t * speed / 100f) + hash(i * 7 + 12)) % 1f;
			float angle = hash(i * 7 + 13) * Mth.TWO_PI;
			float r = diagonal * (0.12f + 0.45f * life);
			int a = Math.round(220 * (1f - life));
			float size = 1.5f + 2.5f * hash(i * 7 + 14);
			g.fill(Math.round(cx + Mth.cos(angle) * r), Math.round(cy + Mth.sin(angle) * r),
					Math.round(cx + Mth.cos(angle) * r + size), Math.round(cy + Mth.sin(angle) * r + size), (a << 24) | 0xE6FAFF);
		}
		IntroTrainer model = trainer();
		if (model != null) {
			model.setXRot(-4f);
			drawModel(g, model, cx, height * 0.94f, height * 0.72f / 1.8f, 190f, 1f);
		}
		front(g, 500);
		if (elapsed < US_REVEAL + 600) {
			// Out of the white: the white thins from the edges inward, the trainer last.
			float k = elapsed < US_REVEAL ? 1f : 1f - (elapsed - US_REVEAL) / 600f;
			g.fill(0, 0, width, height, (Math.round(255 * k * k) << 24) | 0xFFFFFF);
		}
		if (elapsed >= US_TEXT && elapsed < US_IRIS + 400) {
			textBox(g, Component.translatable("cobblemon_battle_showtime.intro.challenged", BattleCinematic.opponentName()).getString(),
					elapsed - US_TEXT, 0xF0141C4A, 0xFF6A8CFF);
		}
		if (elapsed >= US_IRIS) {
			// The iris closes on the trainer, rimmed with light, then a Poké Ball alone in the black.
			float k = Math.min(1f, (elapsed - US_IRIS) / (float) (US_BALL - US_IRIS));
			float radius = diagonal * 0.6f * (1f - k) * (1f - k);
			iris(g, cx, cy, radius, 0xFF000000);
			if (radius > 2) {
				ring(g, cx, cy, radius, 3f, 0xC09AF0FF, k);
			}
			if (elapsed >= US_BALL) {
				float b = Math.min(1f, (elapsed - US_BALL) / 300f);
				item(g, ball(), cx, cy, height * 0.1f * b, 0f);
			}
		}
		g.pose().popPose();
	}

	/** A smooth radial gradient: {@code inner} at the centre, {@code middle} halfway, {@code outer} at the rim. */
	private static void radial(GuiGraphics g, float cx, float cy, float radius, int inner, int middle, int outer, int steps) {
		for (int i = steps; i >= 1; i--) {
			float k = i / (float) steps;
			int color = k < 0.5f ? lerpColor(inner, middle, k * 2) : lerpColor(middle, outer, (k - 0.5f) * 2);
			int alpha = (color >>> 24) / Math.max(1, steps / 6);
			ellipse(g, cx, cy, radius * k, radius * k, (Math.min(255, alpha + 8) << 24) | (color & 0xFFFFFF));
		}
	}

	private static int lerpColor(int a, int b, float t) {
		int ca = (int) Mth.lerp(t, a >>> 24, b >>> 24);
		int cr = (int) Mth.lerp(t, a >> 16 & 0xFF, b >> 16 & 0xFF);
		int cg = (int) Mth.lerp(t, a >> 8 & 0xFF, b >> 8 & 0xFF);
		int cb = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
		return ca << 24 | cr << 16 | cg << 8 | cb;
	}

	/** A ring with a soft glow: wide faint passes under a thin bright one. */
	private static void glowRing(GuiGraphics g, float cx, float cy, float radius, int rgb, float spin) {
		ring(g, cx, cy, radius, 14f, 0x18000000 | rgb, spin);
		ring(g, cx, cy, radius, 7f, 0x40000000 | rgb, spin);
		ring(g, cx, cy, radius, 2.5f, 0xE0000000 | 0xFFFFFF, spin);
	}

	/**
	 * A lightning bolt from one point to another: jagged by midpoint displacement, with branches, drawn as a wide
	 * coloured glow under a thin white core.
	 */
	private static void bolt(GuiGraphics g, float x1, float y1, float x2, float y2, int seed, int depth, float alpha, int rgb) {
		List<float[]> points = new java.util.ArrayList<>();
		points.add(new float[] {x1, y1});
		points.add(new float[] {x2, y2});
		float length = (float) Math.hypot(x2 - x1, y2 - y1);
		for (int level = 0; level < depth; level++) {
			List<float[]> next = new java.util.ArrayList<>();
			for (int i = 0; i < points.size() - 1; i++) {
				float[] a = points.get(i);
				float[] b = points.get(i + 1);
				float dx = b[0] - a[0];
				float dy = b[1] - a[1];
				float offset = (hash(seed + level * 101 + i * 7) - 0.5f) * length * 0.45f / (1 << level);
				next.add(a);
				next.add(new float[] {(a[0] + b[0]) / 2 - dy / length * offset, (a[1] + b[1]) / 2 + dx / length * offset});
			}
			next.add(points.get(points.size() - 1));
			points = next;
		}
		int glow = (Math.round(70 * alpha) << 24) | rgb;
		int core = (Math.round(255 * alpha) << 24) | 0xFFFFFF;
		for (int i = 0; i < points.size() - 1; i++) {
			float[] a = points.get(i);
			float[] b = points.get(i + 1);
			segment(g, a[0], a[1], b[0], b[1], 7f, glow);
			segment(g, a[0], a[1], b[0], b[1], 1.6f, core);
			if (depth > 2 && i % 5 == 2 && hash(seed + i * 31) > 0.45f) {
				// A branch, shorter and fainter, heading off at an angle.
				float angle = (float) Math.atan2(b[1] - a[1], b[0] - a[0]) + (hash(seed + i * 37) > 0.5f ? 0.6f : -0.6f);
				float reach = length * 0.22f;
				bolt(g, a[0], a[1], a[0] + Mth.cos(angle) * reach, a[1] + Mth.sin(angle) * reach, seed + i * 53, 2, alpha * 0.6f, rgb);
			}
		}
	}

	// ---- Omega Ruby/Alpha Sapphire: the silhouette ----------------------

	private void renderSilhouette(GuiGraphics g, long elapsed) {
		if (elapsed < BattleCinematic.EYES_MEET_END) {
			return;
		}
		if (elapsed < OR_LINE) {
			// A checker of black diamonds spreading from the bottom right, then two balls rolling across.
			float k = Math.min(1f, (elapsed - BattleCinematic.EYES_MEET_END) / (float) (OR_BLACK - BattleCinematic.EYES_MEET_END));
			int cell = Math.max(16, height / 9);
			int columns = width / cell + 2;
			int rows = height / cell + 2;
			for (int i = 0; i < columns; i++) {
				for (int j = 0; j < rows; j++) {
					float order = ((columns - i) + (rows - j)) / (float) (columns + rows);
					float grow = Mth.clamp((k * 1.6f - order * 0.9f) * 3f, 0f, 1f);
					if (grow > 0) {
						diamond(g, i * cell, j * cell, cell * 0.75f * grow * ((i + j) % 2 == 0 ? 1f : 0.85f), 0xFF000000);
					}
				}
			}
			if (elapsed >= OR_BLACK) {
				g.fill(0, 0, width, height, 0xFF000000);
				float r = Math.min(1f, (elapsed - OR_BLACK) / (float) (OR_LINE - OR_BLACK));
				float size = height * 0.08f;
				g.fill(0, Math.round(height * 0.35f - 1), Math.round(width * r), Math.round(height * 0.35f + 1), 0x80FFFFFF);
				g.fill(Math.round(width * (1 - r)), Math.round(height * 0.65f - 1), width, Math.round(height * 0.65f + 1), 0x80FFFFFF);
				item(g, ball(), width * r, height * 0.35f, size, r * 900f);
				item(g, ball(), width * (1 - r), height * 0.65f, size, -r * 900f);
			}
			return;
		}
		// The panel opens from a line, then speed lines, the silhouette, the colours.
		float open = Math.min(1f, (elapsed - OR_LINE) / (float) (OR_PANEL - OR_LINE));
		int half = Math.round(height / 2f * open);
		g.fill(0, 0, width, height, 0xFF000000);
		g.enableScissor(0, height / 2 - half, width, height / 2 + half + 1);
		g.fillGradient(0, 0, width, height, 0xFFEAF4FF, 0xFF9CC4F0);
		long t = elapsed - OR_LINE;
		for (int i = 0; i < 26; i++) {
			float y = height * hash(i * 7 + 1);
			float speed = 0.6f + hash(i * 7 + 2);
			float length = width * (0.2f + 0.4f * hash(i * 7 + 3));
			float x = width - ((t * speed * 1.4f + hash(i * 7 + 4) * width * 2) % (width + length * 2)) + length;
			g.fill(Math.round(x - length), Math.round(y), Math.round(x), Math.round(y + 2 + 3 * hash(i * 7 + 5)), 0x90FFFFFF);
		}
		g.fill(0, Math.round(height * 0.18f), width, Math.round(height * 0.19f), 0xFF2C5CB0);
		g.fill(0, Math.round(height * 0.81f), width, Math.round(height * 0.82f), 0xFFB02C3C);
		IntroTrainer model = trainer();
		if (model != null && elapsed >= OR_PANEL) {
			float in = Math.min(1f, (elapsed - OR_PANEL) / 350f);
			float ease = 1f - (1f - in) * (1f - in);
			float colour = elapsed < OR_COLOUR ? 0f : Math.min(1f, (elapsed - OR_COLOUR) / 300f);
			drawModel(g, model, Mth.lerp(ease, -width * 0.2f, width * 0.32f), height * 0.88f, height * 0.62f / 1.8f, 150f, colour);
		}
		front(g, 500);
		ballBar(g, width * 0.55f, height * 0.30f, width * 0.4f, BattleCinematic.opponentTeamSize(), false,
				Math.min(1f, Math.max(0, elapsed - OR_COLOUR) / 400f), 0xFF2C5CB0);
		if (elapsed >= OR_TEXT) {
			textBox(g, Component.translatable("cobblemon_battle_showtime.intro.challenged", BattleCinematic.opponentName()).getString(),
					elapsed - OR_TEXT, 0xF0202830, 0xFFD04050);
		}
		g.pose().popPose();
		g.disableScissor();
	}

	// ---- Battle Revolution: the face-off (overlay of BattleCinematic's shots)

	private void renderFaceOff(GuiGraphics g, long elapsed) {
		if (elapsed < BattleCinematic.EYES_MEET_END) {
			return;
		}
		front(g, 500);
		int bars = Math.round(height * 0.09f);
		g.fill(0, 0, width, bars, 0xFF000000);
		g.fill(0, height - bars, width, height, 0xFF000000);
		if (elapsed < FO_SHOTS + 200) {
			float k = elapsed < FO_SHOTS ? 1f : 1f - (elapsed - FO_SHOTS) / 200f;
			g.fill(0, 0, width, height, (Math.round(255 * k) << 24));
		}
		// A short dip between shots.
		for (int i = 1; i <= 4; i++) {
			long cut = elapsed - (FO_SHOTS + i * FO_SHOT);
			if (cut >= -70 && cut < 90) {
				g.fill(0, 0, width, height, 0xB0000000);
			}
		}
		if (elapsed >= FO_SHOTS && elapsed < FO_SHOTS + FO_SHOT * 2) {
			subtitle(g, BattleCinematic.opponentName(), line(1), elapsed - FO_SHOTS, 0xFFFF6A8E, bars);
		} else if (elapsed >= FO_SHOTS + FO_SHOT * 2 && elapsed < FO_VS) {
			subtitle(g, selfName(), line(0), elapsed - FO_SHOTS - FO_SHOT * 2, 0xFF6AAAFF, bars);
		}
		if (elapsed >= FO_VS) {
			float pop = Math.min(1f, (elapsed - FO_VS) / 220f);
			float scale = (pop < 1f ? 2f - pop : 1f) * (width >= 640 ? 5f : 3.5f);
			float cx = width / 2f;
			float y = height / 2f - 4.5f * scale;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					centeredText(g, "VS", cx + dx * scale * 0.6f, y + dy * scale * 0.6f, scale, 0xFF000000);
				}
			}
			centeredText(g, "VS", cx, y, scale, 0xFFFFD040);
			text(g, BattleCinematic.opponentName(), width * 0.62f, height - bars - 22, 1.5f, 0xFFFF6A8E, true);
			text(g, selfName(), width * 0.08f, height - bars - 22, 1.5f, 0xFF6AAAFF, true);
		}
		g.pose().popPose();
	}

	// ---- Scarlet/Violet: the showdown (overlay of BattleCinematic's shots)

	private void renderShowdown(GuiGraphics g, long elapsed) {
		if (elapsed < BattleCinematic.EYES_MEET_END) {
			return;
		}
		front(g, 500);
		long end = Intro.SV_SHOWDOWN.length;
		float bars = height * 0.08f * Math.min(1f, (elapsed - BattleCinematic.EYES_MEET_END) / 300f)
				* Math.min(1f, Math.max(0, end - elapsed) / 300f);
		g.fill(0, 0, width, Math.round(bars), 0xFF000000);
		g.fill(0, height - Math.round(bars), width, height, 0xFF000000);
		// Name tags: the opponent's from the right, ours from the left, both gone when the crane rises.
		float out = elapsed < SV_CRANE ? 1f : 1f - Math.min(1f, (elapsed - SV_CRANE) / 300f);
		if (elapsed >= BattleCinematic.EYES_MEET_END + 400) {
			float in = Math.min(1f, (elapsed - BattleCinematic.EYES_MEET_END - 400) / 350f) * out;
			nameTag(g, BattleCinematic.opponentName(), BattleCinematic.opponentTeamSize(), false, in, height * 0.2f, 0xFFE8365A);
		}
		if (elapsed >= SV_REVERSE + 300) {
			float in = Math.min(1f, (elapsed - SV_REVERSE - 300) / 350f) * out;
			nameTag(g, selfName(), BattleCinematic.ownTeamSize(), true, in, height * 0.66f, 0xFF3C8CFF);
		}
		if (elapsed >= SV_CRANE + 600) {
			long t = elapsed - SV_CRANE - 600;
			float alpha = Math.min(1f, t / 200f) * Math.min(1f, Math.max(0, end - 150 - elapsed) / 250f);
			int a = Math.round(255 * alpha) << 24;
			float scale = width >= 640 ? 3.2f : 2.4f;
			String go = Component.translatable("cobblemon_battle_showtime.intro.sv.start").getString();
			float w = font.width(go) * scale;
			float wipe = Math.min(1f, t / 250f);
			g.fill(Math.round(width / 2f - w * 0.65f * wipe), Math.round(height / 2f - 7 * scale),
					Math.round(width / 2f + w * 0.65f * wipe), Math.round(height / 2f + 7 * scale), (Math.round(170 * alpha) << 24) | 0x101010);
			centeredText(g, go, width / 2f, height / 2f - 4.5f * scale, scale, a | 0xFFFFFF);
		}
		g.pose().popPose();
	}

	/** A modern name tag: a slanted dark bar with a coloured edge, the name and one ball per Pokémon. */
	private void nameTag(GuiGraphics g, String name, int team, boolean fromLeft, float in, float y, int color) {
		float ease = 1f - (1f - in) * (1f - in);
		int w = Math.round(width * 0.36f);
		int h = Math.round(height * 0.11f);
		int x = Math.round(fromLeft ? Mth.lerp(ease, -w, 0) : Mth.lerp(ease, width, width - w));
		int alpha = Math.round(230 * in);
		g.fill(x, Math.round(y), x + w, Math.round(y) + h, (alpha << 24) | 0x0C0C14);
		g.fill(fromLeft ? x + w - 4 : x, Math.round(y), fromLeft ? x + w : x + 4, Math.round(y) + h, (alpha << 24) | (color & 0xFFFFFF));
		float scale = width >= 640 ? 2f : 1.5f;
		text(g, name, x + 14, y + 6, scale, (Math.round(255 * in) << 24) | 0xFFFFFF, false);
		float size = h * 0.32f;
		for (int i = 0; i < 6 && team >= 0; i++) {
			float bx = x + 14 + size / 2 + i * size * 1.2f;
			float by = y + h - size * 0.8f;
			if (i < team) {
				item(g, ball(), bx, by, size, 0f);
			} else {
				ellipse(g, bx, by, size / 2f - 2, size / 2f - 2, (alpha << 24) | 0x505660);
			}
		}
	}

	/** A modern text box at the bottom: dark, a coloured edge, the text typed. */
	private void textBox(GuiGraphics g, String text, long t, int fill, int edge) {
		int top = Math.round(height * 0.84f);
		int left = Math.round(width * 0.03f);
		int right = Math.round(width * 0.97f);
		int bottom = Math.round(height * 0.96f);
		g.fill(left, top, right, bottom, fill);
		g.fill(left, top, right, top + 2, edge);
		typed(g, text, t, left + width * 0.03f, top, bottom, 0xFFFFFFFF, 0xFF000000);
	}

	/** Spiral arms out of {@code (cx, cy)}, alternating two colours, turned by {@code spin} turns. */
	private static void spiral(GuiGraphics g, float cx, float cy, float radius, int arms, float twist, float spin, int a, int b) {
		PoseStack pose = g.pose();
		int steps = 70;
		for (int arm = 0; arm < arms; arm++) {
			for (int i = 1; i < steps; i++) {
				float r = radius * i / steps;
				float angle = (float) (arm * Mth.TWO_PI / arms + r * twist + spin * Mth.TWO_PI);
				float thick = 2f + r * 0.16f;
				pose.pushPose();
				pose.translate(cx + Mth.cos(angle) * r, cy + Mth.sin(angle) * r, 0);
				pose.mulPose(Axis.ZP.rotation(angle + Mth.HALF_PI * 0.7f));
				g.fill(Math.round(-thick), Math.round(-thick * 0.35f), Math.round(thick), Math.round(thick * 0.35f), arm % 2 == 0 ? a : b);
				pose.popPose();
			}
		}
	}


	/** A straight line, {@code thickness} px wide. */
	private static void segment(GuiGraphics g, float x1, float y1, float x2, float y2, float thickness, int color) {
		PoseStack pose = g.pose();
		float length = (float) Math.hypot(x2 - x1, y2 - y1);
		pose.pushPose();
		pose.translate(x1, y1, 0);
		pose.mulPose(Axis.ZP.rotation((float) Math.atan2(y2 - y1, x2 - x1)));
		int half = Math.max(1, Math.round(thickness / 2));
		g.fill(0, -half, Math.round(length) + 1, half, color);
		pose.popPose();
	}

	/** Everything outside a circle, filled: an iris closing. */
	private void iris(GuiGraphics g, float cx, float cy, float radius, int color) {
		int top = Math.round(cy - radius);
		int bottom = Math.round(cy + radius);
		g.fill(0, 0, width, Math.max(0, top), color);
		g.fill(0, Math.min(height, bottom), width, height, color);
		for (int y = Math.max(0, top); y < Math.min(height, bottom); y++) {
			float dy = (y + 0.5f - cy) / radius;
			float half = radius * (float) Math.sqrt(Math.max(0f, 1f - dy * dy));
			g.fill(0, y, Math.round(cx - half), y + 1, color);
			g.fill(Math.round(cx + half), y, width, y + 1, color);
		}
	}

	/** A square turned 45°, centered on {@code (cx, cy)}. */
	private static void diamond(GuiGraphics g, float cx, float cy, float half, int color) {
		PoseStack pose = g.pose();
		pose.pushPose();
		pose.translate(cx, cy, 0);
		pose.mulPose(Axis.ZP.rotationDegrees(45));
		g.fill(Math.round(-half), Math.round(-half), Math.round(half), Math.round(half), color);
		pose.popPose();
	}

	/** A bright colour round the hue circle ({@code h} in turns). */
	private static int hue(float h) {
		float x = h - (float) Math.floor(h);
		float r = Mth.clamp(Math.abs(x * 6 - 3) - 1, 0, 1);
		float gr = Mth.clamp(2 - Math.abs(x * 6 - 2), 0, 1);
		float b = Mth.clamp(2 - Math.abs(x * 6 - 4), 0, 1);
		return 0xFF000000 | Math.round(255 * (0.35f + 0.65f * r)) << 16 | Math.round(255 * (0.35f + 0.65f * gr)) << 8 | Math.round(255 * (0.35f + 0.65f * b));
	}

	// ---- Battle Revolution: the match card ------------------------------

	/**
	 * A glowing blue stage with rings and a large faint Poké Ball, the title band, the two team cards sliding in, then
	 * both trainers face to face round a "VS", and a Poké Ball wipe to black (2026-11-09, after Battle Revolution's
	 * match screen; drawn from scratch, nothing of the game's).
	 */
	private void renderPbrCard(GuiGraphics g, long elapsed) {
		if (elapsed < BattleCinematic.EYES_MEET_END) {
			return;
		}
		long t = elapsed - BattleCinematic.EYES_MEET_END;
		float cx = width / 2f;
		float cy = height * 0.52f;
		// Stage.
		g.fillGradient(0, 0, width, height, PBR_DEEP, PBR_BLUE);
		for (int i = 6; i >= 1; i--) {
			ellipse(g, cx, cy, width * 0.09f * i, height * 0.16f * i, ((10 + i * 3) << 24) | 0x39C6F0);
		}
		float spin = t / 2600f;
		for (int i = 1; i <= 4; i++) {
			ring(g, cx, cy, height * (0.17f + 0.11f * i), 2f, (0x60 - i * 10) << 24 | 0x8FE6FF, spin * (i % 2 == 0 ? 1 : -1));
		}
		emblem(g, cx, cy, height * 0.30f, 0x2E9FE8FF);
		// Title band, footer band.
		int band = Math.round(height * 0.15f);
		g.fillGradient(0, 0, width, band, 0xF0030C22, 0xE0081E46);
		g.fill(0, band, width, band + 2, PBR_GLOW);
		centeredText(g, Component.translatable("cobblemon_battle_showtime.intro.pbr.title").getString(), cx, band * 0.22f,
				width >= 640 ? 2.6f : 2f, PBR_GOLD);
		centeredText(g, BattleCinematic.placeName(), cx, band * 0.68f, 1f, 0xFFDCEFFF);
		int foot = Math.round(height * 0.92f);
		g.fillGradient(0, foot, width, height, 0xE0081E46, 0xF0030C22);
		g.fill(0, foot, width, foot + 1, PBR_GLOW);
		text(g, Component.translatable("cobblemon_battle_showtime.intro.pbr.rule").getString(), width * 0.06f, foot + (height - foot) / 2f - 4, 1f, 0xFFFFE27A, false);
		String style = Component.translatable("cobblemon_battle_showtime.intro.pbr.style").getString();
		text(g, style, width * 0.94f - font.width(style), foot + (height - foot) / 2f - 4, 1f, 0xFFFFE27A, false);
		if (elapsed >= PBR_CARDS && elapsed < PBR_TRAINERS + 250) {
			float in = Math.min(1f, (elapsed - PBR_CARDS) / 320f);
			float out = elapsed < PBR_TRAINERS ? 1f : 1f - (elapsed - PBR_TRAINERS) / 250f;
			teamCard(g, true, in, out);
			teamCard(g, false, in, out);
		}
		if (elapsed >= PBR_TRAINERS) {
			float in = Math.min(1f, (elapsed - PBR_TRAINERS) / 380f);
			float ease = 1f - (1f - in) * (1f - in);
			float scale = height * 0.70f / 1.8f;
			float drift = (elapsed - PBR_TRAINERS) / 1000f * height * 0.004f;
			IntroTrainer me = selfModel();
			if (me != null) {
				drawModel(g, me, Mth.lerp(ease, -width * 0.2f, width * 0.27f) + drift, foot - 2, scale, 145f, 1f);
			}
			IntroTrainer them = trainer();
			if (them != null) {
				drawModel(g, them, Mth.lerp(ease, width * 1.2f, width * 0.73f) - drift, foot - 2, scale, 215f, 1f);
			}
			front(g, 400);
			namePlate(g, selfName(), width * 0.27f, foot - height * 0.075f, 0xFF3C8CFF, ease);
			namePlate(g, BattleCinematic.opponentName(), width * 0.73f, foot - height * 0.075f, 0xFFFF4A7A, ease);
			if (elapsed >= PBR_VS) {
				float pop = Math.min(1f, (elapsed - PBR_VS) / 200f);
				float vs = (pop < 1f ? 1.6f - 0.6f * pop : 1f + 0.04f * (float) Math.sin((elapsed - PBR_VS) / 120.0))
						* (width >= 640 ? 4.5f : 3.2f);
				for (int dx = -1; dx <= 1; dx++) {
					for (int dy = -1; dy <= 1; dy++) {
						centeredText(g, "VS", cx + dx * vs * 0.6f, cy - 4.5f * vs + dy * vs * 0.6f, vs, 0xFFFFFFFF);
					}
				}
				centeredText(g, "VS", cx, cy - 4.5f * vs, vs, 0xFFE8365A);
			}
			g.pose().popPose();
		}
		if (elapsed >= PBR_WIPE) {
			// The Poké Ball wipe: a glowing ball outline rushing at the screen, the black closing in behind it.
			float k = Math.min(1f, (elapsed - PBR_WIPE) / (float) (PBR_BLACK - PBR_WIPE));
			front(g, 800);
			g.fill(0, 0, width, height, (Math.round(255 * Math.min(1f, k * 1.25f)) << 24));
			if (k < 1f) {
				emblem(g, cx, height / 2f, height * (0.25f + 1.6f * k * k), (Math.round(230 * (1f - k)) << 24) | 0x5FD8FF);
			}
			g.pose().popPose();
		}
	}

	/** A team card: the trainer, their name, one ball per Pokémon. */
	private void teamCard(GuiGraphics g, boolean mine, float in, float out) {
		float ease = 1f - (1f - in) * (1f - in);
		int w = Math.round(width * 0.28f);
		int h = Math.round(height * 0.30f);
		float target = mine ? width * 0.10f : width * 0.62f;
		int x = Math.round(Mth.lerp(ease, mine ? -w - 10 : width + 10, target));
		int y = Math.round(height * 0.27f);
		int alpha = Math.round(255 * Mth.clamp(out, 0f, 1f));
		if (alpha <= 4) {
			return;
		}
		int top = mine ? 0x2A6CD8 : 0x2EA06A;
		int bottom = mine ? 0x123068 : 0x0E4A30;
		g.fill(x - 2, y - 2, x + w + 2, y + h + 2, (alpha << 24) | 0xE6F6FF);
		g.fillGradient(x, y, x + w, y + h, (alpha << 24) | top, (alpha << 24) | bottom);
		IntroTrainer who = mine ? selfModel() : trainer();
		if (who != null && out >= 1f) {
			drawModel(g, who, x + w * 0.24f, y + h * 1.25f, h * 1.05f / 1.8f, mine ? 160f : 200f, 1f);
		}
		front(g, 300);
		int count = mine ? BattleCinematic.ownTeamSize() : BattleCinematic.opponentTeamSize();
		float size = Math.min(w * 0.13f, h * 0.24f);
		for (int i = 0; i < 6 && count >= 0; i++) {
			float bx = x + w * 0.56f + (i % 3) * size * 1.25f;
			float by = y + h * 0.30f + (i / 3) * size * 1.3f;
			ellipse(g, bx, by, size / 2f + 1, size / 2f + 1, (alpha << 24) | 0x061430);
			if (i < count && out >= 1f) {
				item(g, ball(), bx, by, size, 0f);
			} else {
				ellipse(g, bx, by, size / 2f - 2, size / 2f - 2, (alpha << 24) | 0x56607A);
			}
		}
		String name = mine ? selfName() : BattleCinematic.opponentName();
		centeredText(g, name, x + w / 2f, y + h + 6, 1.5f, (alpha << 24) | 0xFFFFFF);
		g.pose().popPose();
	}

	/** Under a trainer: "Trainer" and the name, on a dark plate with their side's colour. */
	private void namePlate(GuiGraphics g, String name, float cx, float y, int color, float in) {
		float scale = width >= 640 ? 2f : 1.5f;
		int w = Math.round(Math.max(font.width(name) * scale, 60) + 20);
		int h = Math.round(9 * scale + 16);
		int x = Math.round(cx - w / 2f);
		int alpha = Math.round(230 * in);
		g.fill(x, Math.round(y), x + w, Math.round(y) + h, (alpha << 24) | 0x06122C);
		g.fill(x, Math.round(y) + h - 2, x + w, Math.round(y) + h, (alpha << 24) | (color & 0xFFFFFF));
		centeredText(g, Component.translatable("cobblemon_battle_showtime.intro.pbr.trainer").getString(), cx, y + 2, 1f, 0xFFB8D4F0);
		centeredText(g, name, cx, y + 12, scale, 0xFFFFFFFF);
	}

	// ---- Battle Revolution: the entrance -------------------------------

	/**
	 * Over the world shots of {@link BattleCinematic}: out of black, cinema bars and the battle's title over the wide
	 * shot, then each trainer's name and line as they are filmed, and a fade to black.
	 */
	private void renderPbrEntrance(GuiGraphics g, long elapsed) {
		if (elapsed < BattleCinematic.EYES_MEET_END) {
			return;
		}
		front(g, 500);
		int bars = Math.round(height * 0.10f);
		g.fill(0, 0, width, bars, 0xFF000000);
		g.fill(0, height - bars, width, height, 0xFF000000);
		if (elapsed < PBR_ARENA + 500) {
			float k = elapsed < PBR_ARENA ? 1f : 1f - (elapsed - PBR_ARENA) / 500f;
			g.fill(0, 0, width, height, (Math.round(255 * k) << 24));
		}
		if (elapsed >= PBR_ARENA + 300 && elapsed < PBR_OPPONENT) {
			long t = elapsed - PBR_ARENA - 300;
			float alpha = Math.min(1f, t / 400f) * Math.min(1f, (PBR_OPPONENT - elapsed) / 300f);
			int a = Math.round(255 * alpha) << 24;
			float scale = width >= 640 ? 3f : 2.2f;
			float y = height * 0.36f;
			centeredText(g, BattleCinematic.placeName(), width / 2f, y, scale, a | 0xF2FAFF);
			g.fill(Math.round(width * 0.3f), Math.round(y + 10 * scale), Math.round(width * 0.7f), Math.round(y + 10 * scale) + 1, a | 0x8FE6FF);
			centeredText(g, Component.translatable("cobblemon_battle_showtime.intro.pbr.title").getString(), width / 2f,
					y + 14 * scale, scale * 0.6f, a | 0xF6DE52);
		}
		if (elapsed >= PBR_OPPONENT && elapsed < PBR_SELF) {
			subtitle(g, BattleCinematic.opponentName(), line(1), elapsed - PBR_OPPONENT, 0xFFFF6A8E, bars);
		}
		if (elapsed >= PBR_SELF && elapsed < PBR_FADE) {
			subtitle(g, selfName(), line(0), elapsed - PBR_SELF, 0xFF6AAAFF, bars);
		}
		if (elapsed >= PBR_FADE) {
			float k = Math.min(1f, (elapsed - PBR_FADE) / 420f);
			g.fill(0, 0, width, height, (Math.round(255 * k) << 24));
		}
		// The cut between the two trainers: a short dip to black.
		long sinceCut = elapsed - PBR_SELF;
		if (sinceCut >= -90 && sinceCut < 110) {
			g.fill(0, 0, width, height, 0xC0000000);
		}
		g.pose().popPose();
	}

	/** Bottom left, in the cinema bar's edge: the trainer's name in their colour, their line typed below. */
	private void subtitle(GuiGraphics g, String name, String line, long t, int color, int bars) {
		float scale = width >= 640 ? 1.5f : 1.2f;
		float x = width * 0.08f;
		float y = height - bars - 30 * scale;
		float alpha = Math.min(1f, t / 250f);
		int a = Math.round(255 * alpha) << 24;
		g.fillGradient(0, Math.round(y - 6), Math.round(width * 0.62f), height - bars, (Math.round(140 * alpha) << 24), 0);
		text(g, name, x, y, scale, a | (color & 0xFFFFFF), true);
		int visible = (int) Math.min(line.length(), Math.max(0, t - 200) / 32);
		text(g, line.substring(0, visible), x, y + 12 * scale, scale, a | 0xFFFFFF, true);
	}

	/** One of the entrance lines, a different one for each trainer, fixed for this intro. */
	private String line(int who) {
		int count = 5;
		int index = (Math.floorMod(lineSeed, count) + who * 3) % count;
		return Component.translatable("cobblemon_battle_showtime.intro.pbr.line." + index).getString();
	}

	private final int lineSeed = new java.util.Random().nextInt(1000);

	private void centeredText(GuiGraphics g, String text, float cx, float y, float scale, int color) {
		text(g, text, cx - font.width(text) * scale / 2f, y, scale, color, true);
	}

	/** A thin ring, in short straight segments, turned by {@code spin} turns. */
	private static void ring(GuiGraphics g, float cx, float cy, float radius, float thickness, int color, float spin) {
		PoseStack pose = g.pose();
		int segments = 96;
		float length = (float) (Mth.TWO_PI * radius / segments) + 1f;
		for (int i = 0; i < segments; i++) {
			if (i % 12 == 11) {
				continue; // small gaps: it reads as a moving ring
			}
			pose.pushPose();
			pose.translate(cx, cy, 0);
			pose.mulPose(Axis.ZP.rotation((float) (i * Mth.TWO_PI / segments + spin * Mth.TWO_PI)));
			pose.translate(radius, 0, 0);
			g.fill(0, Math.round(-length / 2), Math.max(1, Math.round(thickness)), Math.round(length / 2), color);
			pose.popPose();
		}
	}

	/** A large Poké Ball outline: its rim, its band, its button. */
	private static void emblem(GuiGraphics g, float cx, float cy, float radius, int color) {
		float thick = Math.max(2f, radius * 0.07f);
		ring(g, cx, cy, radius, thick, color, 0f);
		ring(g, cx, cy, radius * 0.30f, thick, color, 0f);
		g.fill(Math.round(cx - radius), Math.round(cy - thick / 2), Math.round(cx - radius * 0.30f), Math.round(cy + thick / 2), color);
		g.fill(Math.round(cx + radius * 0.30f), Math.round(cy - thick / 2), Math.round(cx + radius), Math.round(cy + thick / 2), color);
	}

	/**
	 * Draws a stand-in on screen: feet at {@code (x, feetY)}, {@code scale} pixels per block, body turned by
	 * {@code bodyYaw} (180 = facing us, 0 = its back), darkened to {@code brightness} (a silhouette near 0).
	 */
	private void drawModel(GuiGraphics g, RemotePlayer model, float x, float feetY, float scale, float bodyYaw, float brightness) {
		model.yBodyRot = bodyYaw;
		model.yBodyRotO = bodyYaw;
		model.setYRot(bodyYaw + 10f);
		model.yRotO = model.getYRot();
		model.xRotO = model.getXRot();
		model.yHeadRot = bodyYaw + 15f;
		model.yHeadRotO = model.yHeadRot;
		Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
		Quaternionf camera = new Quaternionf().rotateX(-6f * Mth.DEG_TO_RAD);
		pose.mul(camera);
		g.flush();
		RenderSystem.setShaderColor(brightness, brightness, brightness * 1.08f, 1f);
		InventoryScreen.renderEntityInInventory(g, x, feetY - model.getBbHeight() / 2f * scale, scale,
				new Vector3f(0f, model.getBbHeight() / 2f, 0f), pose, camera, model);
		g.flush();
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
	}

	/** Pushes the pose forward, so that what follows covers the models (pop it after). */
	private static void front(GuiGraphics g, float z) {
		g.pose().pushPose();
		g.pose().translate(0, 0, z);
	}

	/**
	 * A battle platform: its soft shadow on the ground, its side (the body seen below the rim, so it has a thickness),
	 * its top lit from the centre, a fine inner ring, and a highlight along the near-left edge.
	 */
	private static void platform(GuiGraphics g, float cx, float cy, float rx, float ry, int rim, int body, int top) {
		float thick = ry * 0.32f;
		// Shadow, wide and faint, then tighter.
		ellipse(g, cx + rx * 0.04f, cy + thick + ry * 0.18f, rx * 1.12f, ry * 1.05f, 0x22000000);
		ellipse(g, cx + rx * 0.02f, cy + thick + ry * 0.08f, rx * 1.04f, ry * 0.95f, 0x33000000);
		// The side: the rim's colour, darker toward the bottom.
		for (int i = 6; i >= 0; i--) {
			float k = i / 6f;
			ellipse(g, cx, cy + thick * k, rx * 1.02f, ry * 1.02f, lerpColor(rim, darker(rim, 0.55f), k));
		}
		// The top: from its rim inward, lighter at the centre.
		ellipse(g, cx, cy, rx * 1.02f, ry * 1.02f, darker(rim, 0.85f));
		ellipse(g, cx, cy, rx, ry, body);
		for (int i = 0; i < 8; i++) {
			float k = 1f - i / 8f;
			ellipse(g, cx - rx * 0.06f * (1 - k), cy - ry * 0.08f * (1 - k), rx * 0.86f * k, ry * 0.86f * k, lerpColor(body, top, 1f - k * 0.85f));
		}
		ellipseRing(g, cx, cy, rx * 0.70f, ry * 0.70f, 0x30FFFFFF);
		// A light catch on the near-left rim.
		for (int i = 0; i < 18; i++) {
			float a = (float) Math.toRadians(150 + i * 3.5);
			float x = cx + Mth.cos(a) * rx * 0.97f;
			float y = cy + Mth.sin(a) * ry * 0.97f;
			g.fill(Math.round(x), Math.round(y), Math.round(x + rx * 0.04f), Math.round(y + 1), 0x50FFFFFF);
		}
	}

	/** A one-pixel ellipse outline. */
	private static void ellipseRing(GuiGraphics g, float cx, float cy, float rx, float ry, int color) {
		int steps = Math.max(24, Math.round(rx));
		for (int i = 0; i < steps; i++) {
			float a = (float) (i * Mth.TWO_PI / steps);
			g.fill(Math.round(cx + Mth.cos(a) * rx), Math.round(cy + Mth.sin(a) * ry),
					Math.round(cx + Mth.cos(a) * rx) + 1, Math.round(cy + Mth.sin(a) * ry) + 1, color);
		}
	}

	private static int darker(int color, float factor) {
		return color & 0xFF000000 | Math.round((color >> 16 & 0xFF) * factor) << 16 | Math.round((color >> 8 & 0xFF) * factor) << 8
				| Math.round((color & 0xFF) * factor);
	}

	/** The colours of one game's battlefield (2026-11-10). */
	private enum Scene {
		DIAMOND_PEARL(0xFFBFE0F4, 0xFFF6F2D8, 0xFFFFF6C8, 0xFFB8D6A8, 0xFF86B676, 0xFFB4D888, 0xFF6EA44C, 0x0C000000, true),
		EMERALD(0xFFA8D8F8, 0xFFF0F8E0, 0xFFFFFAD8, 0xFFC4E2B4, 0xFF94C47C, 0xFFC0E49C, 0xFF84BC60, 0x0A000000, true),
		GOLD_SILVER(0xFFF8D8A8, 0xFFFFF0D0, 0xFFFFE0A0, 0xFFD8C890, 0xFFA8B068, 0xFFC8D890, 0xFF8CAA58, 0x0C000000, true),
		RED_BLUE(0xFF98C8F0, 0xFFE8F4F8, 0xFFFFFFE0, 0xFFB0D0B0, 0xFF78A878, 0xFFA8D898, 0xFF6CA868, 0x0C000000, true),
		BLACK_WHITE(0xFF2C4470, 0xFFB8C8D0, 0xFFF4E0C0, 0xFF6C8C9C, 0xFF3C6470, 0xFF5C9A8E, 0xFF1E4C48, 0x10000000, false);

		final int skyTop;
		final int skyHorizon;
		final int sun;
		final int hillFar;
		final int hillNear;
		final int groundTop;
		final int groundBottom;
		final int stripe;
		final boolean clouds;

		Scene(int skyTop, int skyHorizon, int sun, int hillFar, int hillNear, int groundTop, int groundBottom, int stripe, boolean clouds) {
			this.skyTop = skyTop;
			this.skyHorizon = skyHorizon;
			this.sun = sun;
			this.hillFar = hillFar;
			this.hillNear = hillNear;
			this.groundTop = groundTop;
			this.groundBottom = groundBottom;
			this.stripe = stripe;
			this.clouds = clouds;
		}
	}

	/**
	 * The battlefield behind the Gen III to V intros (2026-11-10): a sky in a smooth gradient with a sun's glow and
	 * drifting clouds, two rows of hills and trees on the horizon (the far one hazed), and ground with mown bands in
	 * perspective and grass tufts, denser near us. {@code horizon}: its height as a share of the screen.
	 */
	private void backdrop(GuiGraphics g, Scene scene, float horizon, long t) {
		backdrop(g, scene, horizon, t, 0f);
	}

	/** The same, turned toward a Game Boy's four greens by {@code mono} (0: the scene's colours, 1: all greens). */
	private void backdrop(GuiGraphics g, Scene scene, float horizon, long t, float mono) {
		int skyTop = tone(scene.skyTop, mono);
		int skyHorizon = tone(scene.skyHorizon, mono);
		int sun = tone(scene.sun, mono);
		int hillFar = tone(scene.hillFar, mono);
		int hillNear = tone(scene.hillNear, mono);
		int groundTop = tone(scene.groundTop, mono);
		int groundBottom = tone(scene.groundBottom, mono);
		int line = Math.round(height * horizon);
		// Sky, in three stops: top, two thirds down, the hazy horizon.
		int middle = lerpColor(skyTop, skyHorizon, 0.55f);
		g.fillGradient(0, 0, width, Math.round(line * 0.6f), skyTop, middle);
		g.fillGradient(0, Math.round(line * 0.6f), width, line, middle, skyHorizon);
		float sunX = width * 0.78f;
		float sunY = line * 0.32f;
		for (int i = 10; i >= 1; i--) {
			ellipse(g, sunX, sunY, width * 0.05f * i, width * 0.05f * i, (6 << 24) | (sun & 0xFFFFFF));
		}
		ellipse(g, sunX, sunY, width * 0.035f, width * 0.035f, 0xC0000000 | (sun & 0xFFFFFF));
		if (scene.clouds) {
			for (int i = 0; i < 5; i++) {
				float speed = 6f + 6f * hash(i * 17 + 1);
				float x = ((hash(i * 17 + 2) * (width + 300) + t / 1000f * speed) % (width + 300)) - 150;
				float y = line * (0.12f + 0.45f * hash(i * 17 + 3));
				float size = width * (0.05f + 0.05f * hash(i * 17 + 4));
				cloud(g, x, y, size);
			}
		}
		// The horizon: a hazed far row of hills, a nearer row of tree crowns.
		hills(g, line, height * 0.075f, 0.011f, 31, lerpColor(hillFar, skyHorizon, 0.35f));
		hills(g, line, height * 0.045f, 0.035f, 57, hillNear);
		// Ground: gradient, then mown bands in perspective, closer = wider.
		g.fillGradient(0, line, width, height, groundTop, groundBottom);
		float y = line;
		int band = 0;
		while (y < height) {
			float depth = (y - line) / (float) (height - line);
			float bandHeight = 3 + depth * depth * height * 0.12f;
			if (band % 2 == 1) {
				g.fill(0, Math.round(y), width, Math.round(y + bandHeight), scene.stripe);
			}
			y += bandHeight;
			band++;
		}
		g.fillGradient(0, line, width, line + Math.max(2, height / 60), 0x50FFFFFF, 0x00FFFFFF);
		// Grass tufts, small and pale far away, bigger and darker near us.
		for (int i = 0; i < 120; i++) {
			float depth = hash(i * 23 + 7);
			depth = depth * depth;
			float ty = line + 4 + depth * (height - line - 6);
			float tx = hash(i * 23 + 8) * width;
			int size = 1 + Math.round(depth * 3);
			int color = lerpColor(lerpColor(groundTop, 0xFFFFFFFF, 0.25f), darker(groundBottom, 0.75f), depth);
			g.fill(Math.round(tx), Math.round(ty - size * 2), Math.round(tx) + size, Math.round(ty), color);
			g.fill(Math.round(tx) + size + 1, Math.round(ty - size * 1.4f), Math.round(tx) + size * 2 + 1, Math.round(ty), color);
		}
		// A soft vignette pulls the eye to the middle.
		g.fillGradient(0, 0, width, Math.round(height * 0.12f), 0x18000000, 0x00000000);
		g.fillGradient(0, Math.round(height * 0.86f), width, height, 0x00000000, 0x22000000);
	}

	/** A row of rounded hills (or tree crowns) standing on {@code line}. */
	private void hills(GuiGraphics g, int line, float amplitude, float frequency, int seed, int color) {
		for (int x = 0; x < width; x += 2) {
			float h = amplitude * (0.55f + 0.30f * Mth.sin(x * frequency + seed) + 0.15f * Mth.sin(x * frequency * 2.7f + seed * 3));
			g.fill(x, Math.round(line - h), x + 2, line, color);
		}
	}

	/** A soft cloud: a few overlapping translucent puffs. */
	private static void cloud(GuiGraphics g, float x, float y, float size) {
		ellipse(g, x, y, size, size * 0.38f, 0x70FFFFFF);
		ellipse(g, x - size * 0.45f, y + size * 0.05f, size * 0.55f, size * 0.30f, 0x60FFFFFF);
		ellipse(g, x + size * 0.5f, y + size * 0.08f, size * 0.6f, size * 0.28f, 0x60FFFFFF);
		ellipse(g, x + size * 0.1f, y - size * 0.18f, size * 0.5f, size * 0.32f, 0x80FFFFFF);
	}

	private static void ellipse(GuiGraphics g, float cx, float cy, float rx, float ry, int color) {
		if (rx < 1f || ry < 1f) {
			return;
		}
		int top = Math.round(cy - ry);
		int bottom = Math.round(cy + ry);
		for (int y = top; y < bottom; y++) {
			float dy = (y + 0.5f - cy) / ry;
			float half = rx * (float) Math.sqrt(Math.max(0f, 1f - dy * dy));
			g.fill(Math.round(cx - half), y, Math.round(cx + half), y + 1, color);
		}
	}

	/** Lines out of {@code (cx, cy)}, from {@code from} to {@code to} px, at stable pseudo-random angles. */
	private static void rays(GuiGraphics g, float cx, float cy, int count, float from, float to, float thickness, int color, float spin) {
		PoseStack pose = g.pose();
		for (int i = 0; i < count; i++) {
			float angle = hash(i * 5 + 101) * Mth.TWO_PI + spin;
			float start = from * (0.8f + 0.4f * hash(i * 5 + 102));
			int half = Math.max(1, Math.round(thickness * (0.5f + hash(i * 5 + 103))));
			pose.pushPose();
			pose.translate(cx, cy, 0);
			pose.mulPose(Axis.ZP.rotation(angle));
			g.fill(Math.round(start), -half / 2, Math.round(to), half - half / 2, color);
			pose.popPose();
		}
	}

	/**
	 * A ball bar: a line with one ball per Pokémon above it (empty slots greyed), sliding in from its side;
	 * {@code fromLeft} = anchored on the left edge.
	 */
	private void ballBar(GuiGraphics g, float x, float y, float length, int count, boolean fromLeft, float progress, int lineColor) {
		float ease = 1f - (1f - progress) * (1f - progress);
		float offset = (1f - ease) * (length + 20) * (fromLeft ? -1 : 1);
		int size = Math.max(10, Math.round(height / 22f));
		int x0 = Math.round(x + offset);
		g.fill(x0, Math.round(y), Math.round(x0 + length), Math.round(y + 3), lineColor);
		g.fill(x0, Math.round(y + 3), Math.round(x0 + length), Math.round(y + 4), 0x80FFFFFF);
		float start = fromLeft ? x0 + length - 8 - 6 * (size + 2) : x0 + 8;
		for (int i = 0; i < 6 && count >= 0; i++) {
			float bx = start + i * (size + 2) + size / 2f;
			float by = y - size / 2f - 2;
			if (i < count) {
				item(g, ball(), bx, by, size, 0f);
			} else {
				ellipse(g, bx, by, size / 2f - 1, size / 2f - 1, 0xFF505660);
				ellipse(g, bx, by, size / 2f - 3, size / 2f - 3, 0xFF8A8F99);
			}
		}
	}

	/** Typed text in a box's band {@code [top, bottom]}, with a drop shadow. */
	private void typed(GuiGraphics g, String text, long t, float x, float top, float bottom, int color, int shadow) {
		int visible = (int) Math.min(text.length(), Math.max(0, t) / 28);
		float scale = width >= 640 ? 2f : 1.5f;
		float y = top + (bottom - top - 9 * scale) / 2f;
		String shown = text.substring(0, visible);
		text(g, shown, x + scale, y + scale, scale, shadow, false);
		text(g, shown, x, y, scale, color, false);
	}

	private void text(GuiGraphics g, String text, float x, float y, float scale, int color, boolean shadow) {
		PoseStack pose = g.pose();
		pose.pushPose();
		pose.translate(x, y, 0);
		pose.scale(scale, scale, 1f);
		g.drawString(font, text, 0, 0, color, shadow);
		pose.popPose();
	}

	/** An item centered on {@code (cx, cy)}, {@code size} px wide, turned by {@code degrees}. */
	private static void item(GuiGraphics g, ItemStack stack, float cx, float cy, float size, float degrees) {
		if (stack.isEmpty()) {
			return;
		}
		PoseStack pose = g.pose();
		pose.pushPose();
		pose.translate(cx, cy, 0);
		pose.mulPose(Axis.ZP.rotationDegrees(degrees));
		pose.scale(size / 16f, size / 16f, 1f);
		g.renderItem(stack, -8, -8);
		pose.popPose();
	}

	private static ItemStack ball() {
		return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", "poke_ball")));
	}

	// =====================================================================
	// Stand-ins
	// =====================================================================

	/** The opponent's stand-in, never added to the world: their skin, their worn equipment, a Poké Ball in hand. */
	private IntroTrainer trainer() {
		if (trainer == null) {
			trainer = standIn(BattleCinematic.opponentUuid(), BattleCinematic.opponentName());
		}
		return trainer;
	}

	/** Ours, for the scenes showing both trainers. */
	private IntroTrainer selfModel() {
		if (self == null) {
			Player player = Minecraft.getInstance().player;
			self = player == null ? null : standIn(player.getUUID(), player.getGameProfile().getName());
		}
		return self;
	}

	private static IntroTrainer standIn(UUID uuid, String name) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || uuid == null) {
			return null;
		}
		PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(uuid);
		GameProfile profile = info != null ? info.getProfile() : new GameProfile(uuid, name);
		// The real player if loaded, else whoever the hooks give (Phantasmon: a Hub avatar) — skin, layers, equipment.
		Player real = com.mystaria.showtime.api.ShowtimeHooks.player(mc.level, uuid);
		IntroTrainer standIn = new IntroTrainer(mc.level, profile, real);
		if (real != null) {
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				standIn.setItemSlot(slot, real.getItemBySlot(slot).copy());
			}
		}
		standIn.setItemSlot(EquipmentSlot.MAINHAND, ball());
		return standIn;
	}

	/**
	 * The stand-in's shown skin parts (outer layer: hat, jacket, sleeves, trousers; cape) are synced entity data,
	 * all off on a fresh entity — they come from the real player when loaded, else all on (2026-10-05: the
	 * intro showed neither the outer layer nor the cape). A subclass, for the protected accessors.
	 */
	private static final class IntroTrainer extends RemotePlayer {
		/** Whose skin to wear: the stand-in's source when it has one (a Hub avatar's comes from Mojang, not the connection). */
		private final net.minecraft.client.player.AbstractClientPlayer skinSource;

		IntroTrainer(net.minecraft.client.multiplayer.ClientLevel level, GameProfile profile, Player real) {
			super(level, profile);
			byte parts = real != null ? real.getEntityData().get(DATA_PLAYER_MODE_CUSTOMISATION) : (byte) 0x7F;
			getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, parts);
			this.skinSource = real instanceof net.minecraft.client.player.AbstractClientPlayer source ? source : null;
		}

		@Override
		public net.minecraft.client.resources.PlayerSkin getSkin() {
			return skinSource != null ? skinSource.getSkin() : super.getSkin();
		}

		/** Advances an arm swing (the Sword/Shield throw); the stand-in never ticks in the world. */
		void tickSwing() {
			oAttackAnim = attackAnim;
			updateSwingTime();
		}
	}

	private static String sequenceText(FormattedCharSequence sequence) {
		StringBuilder builder = new StringBuilder();
		sequence.accept((index, style, codePoint) -> {
			builder.appendCodePoint(codePoint);
			return true;
		});
		return builder.toString();
	}

	private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + 1, color);
		g.fill(x, y + h - 1, x + w, y + h, color);
		g.fill(x, y, x + 1, y + h, color);
		g.fill(x + w - 1, y, x + w, y + h, color);
	}

	/** Stable pseudo-random value in [0, 1). */
	private static float hash(int seed) {
		int x = seed * 0x9E3779B1;
		x ^= x >>> 15;
		x *= 0x85EBCA77;
		x ^= x >>> 13;
		return (x & 0xFFFFFF) / (float) 0x1000000;
	}
}
