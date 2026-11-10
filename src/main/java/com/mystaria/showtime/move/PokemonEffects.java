package com.mystaria.showtime.move;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joml.Matrix4f;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattleActor;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattleSide;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import com.mystaria.showtime.api.BattleStage;
import com.mystaria.showtime.battle.BattleCameraDirector;

/**
 * Lasting effects on one Pokémon (2026-11-02): Substitute, Leech Seed, confusion, Taunt, Encore, Yawn, Perish
 * Song, Stockpile, Curse, Ingrain, Aqua Ring, Focus Energy, Magnet Rise, Charge, Salt Cure, Protosynthesis and Quark
 * Drive… — read from Cobblemon's messages {@code cobblemon.battle.start|end|activate.<id>} (first argument: the
 * Pokémon), drawn on it while they last. They end with their end message, or when the Pokémon leaves the field
 * (switched out, fainted). Client thread only.
 */
public final class PokemonEffects {

	/** One effect on one Pokémon: where it stands now ({@code pnx}), who it is ({@code uuid}). */
	private static final class Lasting {
		final String id;
		final String pnx;
		final UUID uuid;
		int count = 1;
		final int startedAt;

		Lasting(String id, String pnx, UUID uuid, int startedAt) {
			this.id = id;
			this.pnx = pnx;
			this.uuid = uuid;
			this.startedAt = startedAt;
		}
	}

	/** Effects drawn, the others ignored; {@code activate} ones only for the abilities that work that way. */
	private static final java.util.Set<String> KNOWN = java.util.Set.of("substitute", "leechseed", "confusion", "taunt",
			"encore", "yawn", "perish", "stockpile", "curse", "ingrain", "aquaring", "focusenergy", "magnetrise", "charge",
			"saltcure", "protosynthesis", "quarkdrive", "flashfire", "attract", "nightmare", "disable", "torment",
			"healblock", "embargo", "laserfocus", "smackdown", "tarshot", "octolock", "noretreat", "telekinesis", "uproar",
			"syrupbomb", "futuresight", "doomdesire", "imprison");
	private static final java.util.Set<String> ON_ACTIVATE = java.util.Set.of("protosynthesis", "quarkdrive", "flashfire");

	private static final Map<String, Lasting> LASTING = new LinkedHashMap<>();
	private static final RandomSource RANDOM = RandomSource.create();
	private static int ticks;

	private PokemonEffects() {
	}

	public static void onMessages(List<Component> messages) {
		for (Component message : messages) {
			read(message);
			for (Component sibling : message.getSiblings()) {
				read(sibling);
			}
		}
	}

	private static void read(Component message) {
		if (!(message.getContents() instanceof TranslatableContents contents) || !contents.getKey().startsWith("cobblemon.battle.")) {
			return;
		}
		String[] p = contents.getKey().substring("cobblemon.battle.".length()).split("\\.");
		if (p.length == 2 && p[0].equals("damage") && p[1].equals("magmastorm")) {
			magmaStormHurt(contents.getArgs());
			return;
		}
		if (p.length != 2 || !KNOWN.contains(p[1])) {
			return;
		}
		boolean start = p[0].equals("start") || p[0].equals("activate") && ON_ACTIVATE.contains(p[1]);
		boolean end = p[0].equals("end");
		if (!start && !end) {
			return;
		}
		Object[] args = contents.getArgs();
		String pnx = pnxOf(args.length > 0 ? args[0] : null);
		ClientBattlePokemon pokemon = battlePokemon(pnx);
		if (pokemon == null) {
			return;
		}
		String key = pokemon.getUuid() + ":" + p[1];
		if (end) {
			LASTING.remove(key);
			return;
		}
		Lasting lasting = LASTING.get(key);
		if (lasting == null) {
			LASTING.put(key, new Lasting(p[1], pnx, pokemon.getUuid(), ticks));
		} else {
			lasting.count++;
		}
		if (p[1].equals("perish") || p[1].equals("stockpile")) {
			Lasting now = LASTING.get(key);
			now.count = args.length > 1 ? parseCount(args[1], now.count) : now.count;
		}
	}

	/**
	 * Magma Storm's end-of-turn hurt: Cobblemon has an action effect for Fire Spin's ({@code damage_firespin}) but none
	 * for Magma Storm's, so nothing showed. Fire Spin's is played on the hurt Pokémon instead (2026-11-08).
	 */
	private static void magmaStormHurt(Object[] args) {
		BattleStage stage = BattleCameraDirector.stage();
		String pnx = pnxOf(args.length > 0 ? args[0] : null);
		Entity entity = stage == null || pnx == null ? null : stage.pokemonAt(pnx);
		Minecraft mc = Minecraft.getInstance();
		if (entity == null || mc.level == null) {
			return;
		}
		// Past Showtime's own filter: the hurt comes while a redrawn move still plays on that Pokémon, which dropped it.
		MoveEffects.spawnCobblemonParticle(
				new com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormEntityParticlePacket(
						net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", "firespin_target"),
						entity.getId(), List.of("root"), null, List.of()));		net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getOptional(
				net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", "move.firespin.target"))
				.ifPresent(sound -> mc.level.playLocalSound(entity.getX(), entity.getY(), entity.getZ(), sound,
						net.minecraft.sounds.SoundSource.NEUTRAL, 1f, 1f, false));
	}

	private static int parseCount(Object arg, int fallback) {
		String text = arg instanceof Component c ? c.getString() : String.valueOf(arg);
		try {
			return Integer.parseInt(text.trim());
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}

	/**
	 * The battle position of the Pokémon a message names: "Owner's Pokémon" ({@code cobblemon.battle.owned_pokemon},
	 * owner first) for a player's, else its name alone.
	 */
	private static String pnxOf(Object arg) {
		ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
		if (battle == null || arg == null) {
			return null;
		}
		String owner = null;
		String name = arg instanceof Component c ? c.getString() : String.valueOf(arg);
		if (arg instanceof Component c && c.getContents() instanceof TranslatableContents owned
				&& "cobblemon.battle.owned_pokemon".equals(owned.getKey()) && owned.getArgs().length > 1) {
			owner = text(owned.getArgs()[0]);
			name = text(owned.getArgs()[1]);
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

	private static String text(Object arg) {
		return arg instanceof Component c ? c.getString() : String.valueOf(arg);
	}

	private static ClientBattlePokemon battlePokemon(String pnx) {
		ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
		if (battle == null || pnx == null || pnx.length() < 3) {
			return null;
		}
		for (ClientBattleSide side : battle.getSides()) {
			for (ClientBattleActor actor : side.getActors()) {
				if (pnx.startsWith(actor.getShowdownId())) {
					int index = pnx.charAt(2) - 'a';
					List<ActiveClientBattlePokemon> active = actor.getActivePokemon();
					return index >= 0 && index < active.size() ? active.get(index).getBattlePokemon() : null;
				}
			}
		}
		return null;
	}

	public static void reset() {
		LASTING.clear();
	}

	/** The entity an effect is drawn on, or null; the effect is dropped when its Pokémon left the field. */
	private static Entity entityOf(Lasting lasting, Iterator<Lasting> drop) {
		ClientBattlePokemon now = battlePokemon(lasting.pnx);
		if (now == null || !lasting.uuid.equals(now.getUuid())) {
			if (drop != null) {
				drop.remove();
			}
			return null;
		}
		BattleStage stage = BattleCameraDirector.stage();
		return stage == null ? null : stage.pokemonAt(lasting.pnx);
	}

	// ---------------------------------------------------------------- particles

	public static void tick() {
		ticks++;
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null || LASTING.isEmpty()) {
			return;
		}
		for (Iterator<Lasting> it = LASTING.values().iterator(); it.hasNext();) {
			Lasting lasting = it.next();
			Entity entity = entityOf(lasting, it);
			if (entity == null || ticks % 3 != 0) {
				continue;
			}
			ParticleOptions particle = switch (lasting.id) {
				case "attract" -> ParticleTypes.HEART;
				case "leechseed", "ingrain" -> ParticleTypes.HAPPY_VILLAGER;
				case "charge" -> ParticleTypes.ELECTRIC_SPARK;
				case "curse", "nightmare" -> ParticleTypes.SMOKE;
				case "yawn" -> ParticleTypes.CLOUD;
				case "protosynthesis", "flashfire" -> ParticleTypes.FLAME;
				case "quarkdrive" -> ParticleTypes.ELECTRIC_SPARK;
				case "saltcure" -> ParticleTypes.WHITE_ASH;
				case "aquaring" -> ParticleTypes.SPLASH;
				case "tarshot", "syrupbomb", "octolock" -> ParticleTypes.SQUID_INK;
				default -> null;
			};
			if (particle != null) {
				double angle = RANDOM.nextDouble() * Math.PI * 2;
				double r = entity.getBbWidth() * 0.6 + 0.2;
				level.addParticle(particle, entity.getX() + Math.cos(angle) * r, entity.getY() + RANDOM.nextDouble() * entity.getBbHeight(),
						entity.getZ() + Math.sin(angle) * r, 0, 0.03, 0);
			}
		}
	}

	// ---------------------------------------------------------------- drawing

	public static void render(WorldRenderContext context) {
		if (LASTING.isEmpty() || context.matrixStack() == null) {
			return;
		}
		float t = ticks + context.tickCounter().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.camera().getPosition();
		PoseStack pose = context.matrixStack();
		pose.pushPose();
		pose.translate(-camera.x, -camera.y, -camera.z);
		MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
		Matrix4f m = pose.last().pose();
		List<Lasting> all = new ArrayList<>(LASTING.values());
		for (Lasting lasting : all) {
			Entity entity = entityOf(lasting, null);
			if (entity != null) {
				float appear = Mth.clamp((t - lasting.startedAt) / 8f, 0f, 1f);
				draw(buffers.getBuffer(Draw.GLOW), m, camera, entity, lasting, t, appear);
			}
		}
		buffers.endBatch();
		pose.popPose();
	}

	private static void draw(VertexConsumer glow, Matrix4f m, Vec3 camera, Entity e, Lasting lasting, float t, float appear) {
		float w = e.getBbWidth() * 0.6f + 0.25f;
		float h = e.getBbHeight();
		Vec3 feet = e.position();
		Vec3 centre = feet.add(0, h * 0.5, 0);
		Vec3 head = feet.add(0, h + 0.35, 0);
		int a = (int) (220 * appear);
		switch (lasting.id) {
			case "substitute" -> {
				// A ghostly doll shell: a pale bubble, rings turning on it.
				float r = Math.max(e.getBbWidth(), h) * 0.7f + 0.2f;
				Draw.sphere(glow, m, centre, r, 0xF0E8D0, a / 6, true);
				for (int i = 0; i < 2; i++) {
					double angle = t * 0.05 + i * Math.PI / 2;
					Draw.ring(glow, m, centre, new Vec3(Math.cos(angle), 0.3, Math.sin(angle)).normalize(), r, 0.05f, 0xFFFFFF, a / 2);
				}
			}
			case "leechseed" -> {
				for (int v = 0; v < 3; v++) {
					Vec3 last = null;
					for (int i = 0; i <= 16; i++) {
						double p = i / 16.0;
						double angle = p * 8 + v * 2.1;
						Vec3 at = feet.add(Math.cos(angle) * w, p * h, Math.sin(angle) * w);
						if (last != null) {
							Draw.streak(glow, m, at, last, camera, 0.05f, 0x48C840, a);
						}
						last = at;
					}
				}
				float pulse = (t % 30) / 30f;
				Draw.halo(glow, m, centre, camera, 0, w * (1 + pulse), 0x80FF60, (int) (90 * (1 - pulse) * appear));
			}
			case "confusion" -> orbit(glow, m, camera, head, w * 0.8f, 4, t * 0.15f, 0xFFE040, a, 0.12f);
			case "taunt", "torment" -> glyph(glow, m, camera, head, t, lasting.id.equals("taunt") ? 0xFF3030 : 0xC03060, a);
			case "disable", "healblock", "embargo", "imprison" -> cross(glow, m, camera, head, lasting.id.equals("disable") ? 0xA0A0A0 : 0xFF6060, a);
			case "encore", "uproar" -> orbit(glow, m, camera, head.add(0, -0.1, 0), w, 3, t * 0.1f, 0xFF90E0, a, 0.14f);
			case "yawn" -> {
				for (int i = 0; i < 3; i++) {
					float p = (t * 0.02f + i / 3f) % 1f;
					Draw.halo(glow, m, head.add(p * 0.6, p * 1.2, 0), camera, 0, 0.1f + 0.15f * p, 0xC0C0FF, (int) (a * (1 - p)));
				}
			}
			case "perish" -> {
				// A dark halo above, one bead per turn left.
				Draw.ring(glow, m, head.add(0, 0.2, 0), new Vec3(0, 1, 0), w * 0.6f, 0.06f, 0x6040A0, a);
				for (int i = 0; i < Math.max(0, lasting.count); i++) {
					double angle = Math.PI * 2 * i / 3 + t * 0.03;
					Draw.halo(glow, m, head.add(Math.cos(angle) * w * 0.6, 0.2, Math.sin(angle) * w * 0.6), camera, 0, 0.14f, 0xB080FF, a);
				}
			}
			case "stockpile" -> {
				for (int i = 0; i < Math.min(3, lasting.count); i++) {
					Draw.halo(glow, m, feet.add(0, h * (0.3 + 0.2 * i), 0), camera, 0, w * 0.5f, 0xFFC860, (int) (a * 0.6f));
				}
			}
			case "curse", "nightmare" -> {
				Draw.halo(glow, m, head, camera, 0, 0.45f, lasting.id.equals("curse") ? 0x7030A0 : 0x302050, a);
				Draw.streak(glow, m, head.add(0, 0.5, 0), head, camera, 0.12f, 0xA060E0, (int) (a * (0.6f + 0.4f * Mth.sin(t * 0.3f))));
			}
			case "ingrain" -> {
				for (int i = 0; i < 6; i++) {
					double angle = Math.PI * 2 * i / 6;
					Vec3 root = feet.add(Math.cos(angle) * w * 0.5, 0.05, Math.sin(angle) * w * 0.5);
					Draw.streak(glow, m, root, root.add(Math.cos(angle) * w, -0.02, Math.sin(angle) * w), camera, 0.06f, 0x8A6030, a);
				}
			}
			case "aquaring" -> orbitRing(glow, m, centre, w * 1.2f, t, 0x50A8FF, a);
			case "magnetrise", "telekinesis" -> {
				Draw.ring(glow, m, feet.add(0, 0.1, 0), new Vec3(0, 1, 0), w, 0.08f, lasting.id.equals("magnetrise") ? 0x80C0FF : 0xFF60C0, a);
				Draw.ring(glow, m, feet.add(0, 0.1, 0), new Vec3(0, 1, 0), w * (0.6f + 0.4f * ((t * 0.05f) % 1f)), 0.04f, 0xFFFFFF, a / 2);
			}
			case "focusenergy", "laserfocus" -> {
				for (int i = 0; i < 2; i++) {
					float p = (t * 0.04f + i * 0.5f) % 1f;
					Draw.ring(glow, m, feet.add(0, p * h * 1.1, 0), new Vec3(0, 1, 0), w * (1 - 0.3f * p), 0.06f, lasting.id.equals("focusenergy") ? 0xFF4030 : 0xFF2020, (int) (a * (1 - p)));
				}
			}
			case "charge" -> {
				RandomSource jag = RandomSource.create((long) (t / 3));
				for (int i = 0; i < 3; i++) {
					Vec3 x = centre.add(new Vec3(jag.nextGaussian(), jag.nextGaussian(), jag.nextGaussian()).normalize().scale(w));
					Vec3 y = centre.add(new Vec3(jag.nextGaussian(), jag.nextGaussian(), jag.nextGaussian()).normalize().scale(w));
					Motifs.bolt(glow, m, camera, x, y, t + i, 0.025f, 0xFFE030, i);
				}
			}
			case "saltcure" -> orbit(glow, m, camera, centre, w * 1.1f, 6, t * 0.06f, 0xF8F8F0, a, 0.09f);
			case "protosynthesis", "flashfire" -> Draw.halo(glow, m, centre, camera, w * 0.5f, w * 1.8f, 0xFFA030, (int) (a * (0.5f + 0.2f * Mth.sin(t * 0.2f))));
			case "quarkdrive" -> Draw.halo(glow, m, centre, camera, w * 0.5f, w * 1.8f, 0x40E0FF, (int) (a * (0.5f + 0.2f * Mth.sin(t * 0.2f))));
			case "attract" -> orbit(glow, m, camera, head, w * 0.7f, 3, t * 0.08f, 0xFF60A0, a, 0.13f);
			case "smackdown" -> {
				for (int i = 0; i < 3; i++) {
					float p = (t * 0.05f + i / 3f) % 1f;
					Vec3 at = head.add(0, 0.8 * (1 - p), 0);
					Draw.streak(glow, m, at, at.add(0, 0.4, 0), camera, 0.06f, 0xC8A050, (int) (a * (1 - p)));
				}
			}
			case "tarshot", "syrupbomb" -> Draw.halo(glow, m, centre, camera, w * 0.6f, w * 1.4f, lasting.id.equals("tarshot") ? 0x403020 : 0xC06030, a / 2);
			case "octolock", "noretreat" -> {
				for (int i = 0; i < 4; i++) {
					double angle = Math.PI / 2 * i + t * 0.02;
					Draw.arc(glow, m, centre, new Vec3(Math.cos(angle), 0, Math.sin(angle)), new Vec3(0, 1, 0), w * 1.1f, -1.2f, 1.2f, 1f, 0.06f, lasting.id.equals("octolock") ? 0x5040A0 : 0xE05030, a);
				}
			}
			case "futuresight", "doomdesire" -> {
				Vec3 above = head.add(0, 0.8 + 0.1 * Math.sin(t * 0.1), 0);
				Draw.halo(glow, m, above, camera, 0, 0.35f, lasting.id.equals("futuresight") ? 0xFF60C0 : 0xC0E0FF, a);
				Draw.ring(glow, m, above, camera.subtract(above).normalize(), 0.45f, 0.04f, 0xFFFFFF, a / 2);
			}
			default -> {
			}
		}
	}

	private static void orbit(VertexConsumer glow, Matrix4f m, Vec3 camera, Vec3 at, float r, int count, float spin, int color, int alpha, float size) {
		for (int i = 0; i < count; i++) {
			double angle = spin + Math.PI * 2 * i / count;
			Draw.halo(glow, m, at.add(Math.cos(angle) * r, Math.sin(spin * 2 + i) * 0.08, Math.sin(angle) * r), camera, 0, size, color, alpha);
		}
	}

	private static void orbitRing(VertexConsumer glow, Matrix4f m, Vec3 centre, float r, float t, int color, int alpha) {
		Draw.ring(glow, m, centre, new Vec3(Math.sin(t * 0.03) * 0.3, 1, Math.cos(t * 0.03) * 0.3).normalize(), r, 0.08f, color, alpha);
	}

	/** An anger mark over the head, pulsing. */
	private static void glyph(VertexConsumer glow, Matrix4f m, Vec3 camera, Vec3 at, float t, int color, int alpha) {
		Vec3[] b = Draw.basis(camera.subtract(at).normalize());
		float s = 0.25f * (1 + 0.15f * Mth.sin(t * 0.5f));
		for (int i = 0; i < 4; i++) {
			double angle = Math.PI / 2 * i + Math.PI / 4;
			Vec3 d = b[0].scale(Math.cos(angle)).add(b[1].scale(Math.sin(angle)));
			Vec3 side = b[0].scale(-Math.sin(angle)).add(b[1].scale(Math.cos(angle)));
			Draw.streak(glow, m, at.add(d.scale(s * 1.4)).add(side.scale(s * 0.5)), at.add(d.scale(s * 0.4)), camera, 0.04f, color, alpha);
			Draw.streak(glow, m, at.add(d.scale(s * 1.4)).subtract(side.scale(s * 0.5)), at.add(d.scale(s * 0.4)), camera, 0.04f, color, alpha);
		}
	}

	/** A barred mark over the head (Disable, Heal Block, Embargo). */
	private static void cross(VertexConsumer glow, Matrix4f m, Vec3 camera, Vec3 at, int color, int alpha) {
		Vec3[] b = Draw.basis(camera.subtract(at).normalize());
		Draw.ring(glow, m, at, camera.subtract(at).normalize(), 0.25f, 0.04f, color, alpha);
		Draw.streak(glow, m, at.add(b[0].scale(-0.18)).add(b[1].scale(0.18)), at.add(b[0].scale(0.18)).add(b[1].scale(-0.18)), camera, 0.04f, color, alpha);
	}

}
