package com.mystaria.showtime.capture;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.client.net.animation.PlayPosableAnimationHandler;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.net.messages.client.animation.PlayPosableAnimationPacket;
import com.mojang.blaze3d.platform.NativeImage;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import com.mystaria.showtime.move.MoveEffects;
import com.mystaria.showtime.move.MoveSpecs;

/**
 * The move capture tool (dev only, 2026-11-26): films Showtime's moves with nobody at the keyboard. Started by
 * {@code tools/move_capture.py} through {@code ./gradlew runCapture}, it opens (or makes) a flat world, puts two
 * Pokémon face to face, plays each move of the list as Cobblemon's move pose packet would, pipes the frames to ffmpeg
 * (one MP4 per move) and quits the game.
 *
 * <p>Settings come from {@code run-capture/capture.properties}: {@code moves} (comma separated, or {@code all}),
 * {@code user} and {@code target} (Pokémon properties), {@code ffmpeg} (path), {@code out} (folder), {@code distance}
 * and {@code height} (the camera, blocks from the stage's middle).
 *
 * <p>Outside a battle no server sends the move's packets: its Cobblemon timeline is replayed ({@link Timeline}), so the
 * poses and cues come as in a battle. A move without one gets its pose and the hit cue at {@link #HIT_TICK}.
 */
public final class MoveCapture implements ClientModInitializer {

	private static final Logger LOG = LoggerFactory.getLogger("showtime_capture");
	private static final String WORLD = "showtime-capture";
	/** Marks the two Pokémon of the stage: any other one wandering in is removed before each move. */
	private static final String TAG = "showtime_capture";
	/** Ticks after the pose when the impact cue is given (Cobblemon's usually lands around there). */
	private static final int HIT_TICK = 24;
	/** Longest a move is filmed (a move effect lasts 140 ticks at most). */
	private static final int MAX_TICKS = 170;
	/** Ticks filmed after the effect ends. */
	private static final int TAIL = 10;
	/** Ticks between two moves (Cobblemon's pose settles). */
	private static final int GAP = 16;

	private enum State { BOOT, LOADING, SETTLING, STAGED, PLAYING, GAP, DONE }

	private State state = State.BOOT;
	private int wait;
	private Properties settings;
	private final List<String> moves = new ArrayList<>();
	private final List<String> report = new ArrayList<>();
	private int index;
	private int age;
	private int quiet;
	private volatile int userId = -1;
	private volatile int targetId = -1;
	private Path out;
	private Encoder encoder;
	/** The move's Cobblemon timeline being replayed, null for a move without one. */
	private List<Timeline.Event> timeline;
	private int next;
	private int lastEvent;

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	private void tick(Minecraft mc) {
		try {
			step(mc);
		} catch (Exception e) {
			LOG.error("Move capture failed", e);
			finish(mc);
		}
	}

	private void step(Minecraft mc) throws IOException {
		switch (state) {
			case BOOT -> {
				if (!(mc.screen instanceof TitleScreen)) {
					return;
				}
				loadSettings();
				state = State.LOADING;
				if (mc.getLevelSource().levelExists(WORLD)) {
					mc.createWorldOpenFlows().openWorld(WORLD, () -> mc.setScreen(new TitleScreen()));
				} else {
					createWorld(mc);
				}
			}
			case LOADING -> {
				if (mc.level != null && mc.player != null && mc.getSingleplayerServer() != null && mc.screen == null) {
					state = State.SETTLING;
					wait = 40;
				}
			}
			case SETTLING -> {
				if (--wait > 0) {
					return;
				}
				MinecraftServer server = mc.getSingleplayerServer();
				userId = -1;
				server.execute(() -> stage(server));
				state = State.STAGED;
				wait = 400;
			}
			case STAGED -> {
				// The stage is set on the server thread; then the entities reach the client.
				if (userId >= 0 && mc.level.getEntity(userId) != null && mc.level.getEntity(targetId) != null) {
					mc.options.hideGui = true;
					state = State.GAP;
					wait = 30;
				} else if (--wait <= 0) {
					throw new IllegalStateException("The Pokémon never appeared (" + settings.getProperty("user") + ", "
							+ settings.getProperty("target") + ")");
				}
			}
			case GAP -> {
				if (--wait > 0) {
					return;
				}
				if (index >= moves.size()) {
					finish(mc);
					return;
				}
				startMove(mc, moves.get(index));
			}
			case PLAYING -> {
				age++;
				while (timeline != null && next < timeline.size() && timeline.get(next).tick() <= age) {
					timeline.get(next++).action().accept(mc);
				}
				encoder.frame(mc);
				if (timeline == null && age == HIT_TICK) {
					Entity target = mc.level.getEntity(targetId);
					if (target != null) {
						MoveEffects.previewHit(target);
					}
				}
				quiet = MoveEffects.busy() ? 0 : quiet + 1;
				if ((age > Math.max(HIT_TICK + 10, lastEvent) && quiet >= TAIL) || age >= MAX_TICKS) {
					String move = moves.get(index);
					encoder.close();
					encoder = null;
					report.add(move + "\t" + age);
					LOG.info("Captured {} ({} ticks)", move, age);
					MoveEffects.reset();
					index++;
					state = State.GAP;
					wait = GAP;
				}
			}
			case DONE -> {
			}
		}
	}

	private void loadSettings() throws IOException {
		Path dir = FabricLoader.getInstance().getGameDir();
		settings = new Properties();
		Path file = dir.resolve("capture.properties");
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				settings.load(reader);
			}
		}
		String list = settings.getProperty("moves", "flamethrower").trim();
		if (list.equalsIgnoreCase("all")) {
			moves.addAll(new java.util.TreeSet<>(MoveSpecs.ids()));
		} else {
			for (String move : list.split("[,\\s]+")) {
				if (!move.isBlank()) {
					moves.add(move.toLowerCase(Locale.ROOT));
				}
			}
		}
		out = dir.resolve(settings.getProperty("out", "captures"));
		Files.createDirectories(out);
		LOG.info("Move capture: {} moves into {}", moves.size(), out);
	}

	private void createWorld(Minecraft mc) {
		GameRules rules = new GameRules();
		rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
		rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
		rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
		LevelSettings level = new LevelSettings(WORLD, GameType.SPECTATOR, false, Difficulty.PEACEFUL, true, rules, dataConfiguration());
		mc.createWorldOpenFlows().createFreshLevel(WORLD, level, new WorldOptions(0L, false, false),
				access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
				new TitleScreen());
	}

	/** Fabric's data pack selection (mods' data, Cobblemon's species among them), vanilla's if it cannot be had. */
	private static WorldDataConfiguration dataConfiguration() {
		try {
			return (WorldDataConfiguration) Class.forName("net.fabricmc.fabric.impl.resource.loader.ModResourcePackUtil")
					.getMethod("createDefaultDataConfiguration").invoke(null);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return WorldDataConfiguration.DEFAULT;
		}
	}

	/** Server thread: noon, nothing else around, the two Pokémon face to face along X, the camera on the side. */
	private void stage(MinecraftServer server) {
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		ServerLevel level = player.serverLevel();
		for (String command : new String[] {"time set 6000", "weather clear", "gamerule doDaylightCycle false",
				"gamerule doWeatherCycle false", "gamerule doMobSpawning false", "gamerule doPokemonSpawning false", "gamemode spectator @a"}) {
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
		}
		// Everything but the player goes: the stage of an earlier run is saved with the world (killing would leave
		// fainting Pokémon and their drops on screen).
		for (Entity entity : com.google.common.collect.Lists.newArrayList(level.getAllEntities())) {
			if (!(entity instanceof ServerPlayer)) {
				entity.discard();
			}
		}
		// Always at the world's spawn: the player is saved where the camera stood.
		int x = level.getSharedSpawnPos().getX();
		int z = level.getSharedSpawnPos().getZ();
		double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
		double cx = x + 0.5;
		double cz = z + 0.5;
		double gap = Double.parseDouble(settings.getProperty("gap", "3"));
		PokemonEntity user = spawn(level, settings.getProperty("user", "charizard"), cx - gap, y, cz, -90f);
		PokemonEntity target = spawn(level, settings.getProperty("target", "blastoise"), cx + gap, y, cz, 90f);
		double distance = Double.parseDouble(settings.getProperty("distance", "9"));
		double height = Double.parseDouble(settings.getProperty("height", "2.5"));
		// South of the middle, looking north at the pair's chest height.
		float pitch = (float) Math.toDegrees(Math.atan2(height - 1.0, distance));
		player.teleportTo(level, cx, y + height, cz + distance, 180f, pitch);
		targetId = target.getId();
		userId = user.getId();
	}

	private static PokemonEntity spawn(ServerLevel level, String properties, double x, double y, double z, float yaw) {
		PokemonEntity entity = PokemonProperties.Companion.parse(properties).createEntity(level);
		entity.moveTo(x, y, z, yaw, 0f);
		entity.setYHeadRot(yaw);
		entity.setYBodyRot(yaw);
		entity.setNoAi(true);
		entity.setInvulnerable(true);
		entity.setPersistenceRequired();
		entity.addTag(TAG);
		level.addFreshEntity(entity);
		return entity;
	}

	private void startMove(Minecraft mc, String move) throws IOException {
		age = 0;
		quiet = 0;
		MinecraftServer server = mc.getSingleplayerServer();
		// Discarded, not killed: a killed Pokémon faints on screen for a second and a half.
		server.execute(() -> {
			for (ServerLevel level : server.getAllLevels()) {
				for (Entity entity : com.google.common.collect.Lists.newArrayList(level.getAllEntities())) {
					if (entity instanceof PokemonEntity && !entity.getTags().contains(TAG)) {
						entity.discard();
					}
				}
			}
		});
		encoder = new Encoder(settings.getProperty("ffmpeg", "ffmpeg"), out.resolve(move + ".mp4"));
		timeline = Timeline.load(server, move, userId, targetId);
		next = 0;
		lastEvent = timeline == null || timeline.isEmpty() ? 0 : timeline.get(timeline.size() - 1).tick();
		if (timeline == null) {
			// No Cobblemon timeline: the move's own pose, then its category's as the fallback, as a battle sends it.
			LinkedHashSet<String> animations = new LinkedHashSet<>();
			animations.add(move);
			MoveTemplate template = Moves.getByName(move);
			if (template != null) {
				animations.add(template.getDamageCategory().getName().toLowerCase(Locale.ROOT));
			}
			PlayPosableAnimationHandler.INSTANCE.handle(new PlayPosableAnimationPacket(userId, animations, List.of()), mc);
		} else {
			while (next < timeline.size() && timeline.get(next).tick() <= 0) {
				timeline.get(next++).action().accept(mc);
			}
		}
		encoder.frame(mc);
		state = State.PLAYING;
	}

	private void finish(Minecraft mc) {
		if (state == State.DONE) {
			return;
		}
		state = State.DONE;
		if (encoder != null) {
			encoder.close();
		}
		try (Writer writer = Files.newBufferedWriter(FabricLoader.getInstance().getGameDir().resolve("capture-done.txt"), StandardCharsets.UTF_8)) {
			for (String line : report) {
				writer.write(line + "\n");
			}
		} catch (IOException e) {
			LOG.error("Could not write the capture report", e);
		}
		mc.stop();
	}

	/** One move's film: frames grabbed on the client thread, piped to ffmpeg on a worker. */
	private static final class Encoder {
		private final String ffmpeg;
		private final Path file;
		private final ExecutorService worker = Executors.newSingleThreadExecutor();
		private Process process;
		private OutputStream pipe;
		private int width;
		private int height;

		Encoder(String ffmpeg, Path file) {
			this.ffmpeg = ffmpeg;
			this.file = file;
		}

		void frame(Minecraft mc) throws IOException {
			int[] pixels;
			int w;
			int h;
			try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
				w = image.getWidth();
				h = image.getHeight();
				pixels = image.getPixelsRGBA();
			}
			if (process == null) {
				width = w;
				height = h;
				process = new ProcessBuilder(ffmpeg, "-hide_banner", "-loglevel", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgba",
						"-s", w + "x" + h, "-r", "20", "-i", "-", "-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2", "-c:v", "libx264",
						"-crf", "20", "-pix_fmt", "yuv420p", file.toString())
						.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
				pipe = process.getOutputStream();
			}
			if (w != width || h != height) {
				return; // window resized mid-move: skip rather than corrupt the stream
			}
			worker.execute(() -> {
				// NativeImage's ints are ABGR: little-endian bytes are R, G, B, A.
				ByteBuffer bytes = ByteBuffer.allocate(pixels.length * 4).order(ByteOrder.LITTLE_ENDIAN);
				bytes.asIntBuffer().put(pixels);
				try {
					pipe.write(bytes.array());
				} catch (IOException e) {
					LOG.error("ffmpeg pipe", e);
				}
			});
		}

		void close() {
			worker.shutdown();
			try {
				worker.awaitTermination(30, TimeUnit.SECONDS);
				if (pipe != null) {
					pipe.close();
				}
				if (process != null) {
					process.waitFor(30, TimeUnit.SECONDS);
				}
			} catch (IOException | InterruptedException e) {
				LOG.error("ffmpeg close", e);
			}
		}
	}
}
