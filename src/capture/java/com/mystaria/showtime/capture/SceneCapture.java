package com.mystaria.showtime.capture;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.mojang.blaze3d.platform.NativeImage;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import com.mystaria.showtime.ambiance.Ambiances;
import com.mystaria.showtime.ambiance.BattleAmbiance;

/**
 * The scene capture tool (dev only, 2026-11-28): a picture of each battle scene (preset, stadium, ambiance, and the
 * scenes other mods ask for, such as League Reborn fields), for the choice cards of a battle lobby. Started by
 * {@code tools/scene_capture.py} through {@code ./gradlew runCapture} with {@code mode=scenes}: in the flat world of
 * {@link MoveCapture}, two Pokémon face to face, each scene played as a battle seen from afar (its field between the
 * two), a screenshot without the HUD once it has settled, then the next one; the game quits at the end.
 *
 * <p>Settings ({@code run-capture/capture.properties}): {@code kinds} (comma separated: {@code preset}, {@code stadium},
 * {@code ambiance}), {@code only} (ids to keep, comma separated, empty for all), {@code extra} (a file of
 * {@code kind<TAB>id<TAB>scene} lines), {@code out}, {@code settle} (ticks before the picture), {@code user},
 * {@code target}, {@code distance}, {@code height}, {@code gap}. A picture lands in {@code out/<kind>/<ns>/<name>.png}.
 */
public final class SceneCapture implements ClientModInitializer {

	private static final Logger LOG = LoggerFactory.getLogger("showtime_capture");

	private enum State { BOOT, LOADING, SETTLING, STAGED, CENTERING, SHOWING, GAP, DONE }

	private record Shot(String kind, String id, String scene) {
	}

	private State state = State.BOOT;
	private int wait;
	private Properties settings;
	private final List<Shot> shots = new ArrayList<>();
	private final List<String> report = new ArrayList<>();
	private int index;
	private volatile List<Vec3> spots;
	/** The stage's middle and the camera's spot (with its look). */
	private volatile double[] middle;
	private volatile double[] camera;
	private volatile int userId = -1;
	private volatile int targetId = -1;
	private Path out;

	@Override
	public void onInitializeClient() {
		if ("scenes".equals(MoveCapture.readSettings().getProperty("mode"))) {
			ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		}
	}

	private void tick(Minecraft mc) {
		try {
			step(mc);
		} catch (Exception e) {
			LOG.error("Scene capture failed", e);
			finish(mc);
		}
	}

	private void step(Minecraft mc) throws IOException, ReflectiveOperationException {
		switch (state) {
			case BOOT -> {
				if (!(mc.screen instanceof TitleScreen)) {
					return;
				}
				settings = MoveCapture.readSettings();
				out = FabricLoader.getInstance().getGameDir().resolve(settings.getProperty("out", "scenes"));
				Files.createDirectories(out);
				state = State.LOADING;
				if (mc.getLevelSource().levelExists(MoveCapture.WORLD)) {
					mc.createWorldOpenFlows().openWorld(MoveCapture.WORLD, () -> mc.setScreen(new TitleScreen()));
				} else {
					MoveCapture.createWorld(mc);
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
				listShots(); // the ambiances are loaded with the resources, by now
				MinecraftServer server = mc.getSingleplayerServer();
				server.execute(() -> stage(server));
				state = State.STAGED;
				wait = 400;
			}
			case STAGED -> {
				if (userId >= 0 && mc.level.getEntity(userId) != null && mc.level.getEntity(targetId) != null) {
					mc.options.hideGui = true;
					state = State.GAP;
					wait = 30;
				} else if (--wait <= 0) {
					throw new IllegalStateException("The Pokémon never appeared");
				}
			}
			case GAP -> {
				if (--wait > 0) {
					return;
				}
				if (index >= shots.size()) {
					finish(mc);
					return;
				}
				Shot shot = shots.get(index);
				if ("distant".equals(settings.getProperty("view", "battle"))) {
					List<Vec3> field = spots;
					BattleAmbiance.startDistant(shot.scene(), () -> field);
					state = State.SHOWING;
					wait = Integer.parseInt(settings.getProperty("settle", "80"));
				} else {
					// As in a battle: the scene is built around the player standing in the stage's middle, then the
					// camera goes back to its spot.
					teleport(mc, middle);
					state = State.CENTERING;
					wait = 4;
				}
			}
			case CENTERING -> {
				if (--wait > 0) {
					return;
				}
				beginAround(shots.get(index).scene());
				teleport(mc, camera);
				state = State.SHOWING;
				wait = Integer.parseInt(settings.getProperty("settle", "80"));
			}
			case SHOWING -> {
				if (--wait > 0) {
					return;
				}
				Shot shot = shots.get(index);
				Path file = out.resolve(shot.kind()).resolve(path(shot.id()) + ".png");
				Files.createDirectories(file.getParent());
				try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
					image.writeToFile(file);
				}
				report.add(shot.kind() + "\t" + shot.id() + "\t" + (BattleAmbiance.active() ? "ok" : "nothing shown"));
				LOG.info("Captured {} {} ({})", shot.kind(), shot.id(), shot.scene());
				BattleAmbiance.endDistant();
				BattleAmbiance.end();
				index++;
				state = State.GAP;
				wait = 10;
			}
			case DONE -> {
			}
		}
	}

	/** The scenes to take: the kinds asked for (as the lobby lists them), then the extra file's lines. */
	private void listShots() throws IOException {
		List<String> only = new ArrayList<>();
		for (String id : settings.getProperty("only", "").split("[,\\s]+")) {
			if (!id.isBlank()) {
				only.add(id.trim());
			}
		}
		for (String kind : settings.getProperty("kinds", "preset,stadium,ambiance").split("[,\\s]+")) {
			List<String> ids = switch (kind.trim()) {
				case "preset" -> Ambiances.scenePresetIds();
				case "stadium" -> Ambiances.stadiumIds();
				case "ambiance" -> Ambiances.plainAmbianceIds();
				default -> List.of();
			};
			for (String id : ids) {
				String scene = switch (kind.trim()) {
					case "stadium" -> Ambiances.scene(Ambiances.NONE, id); // the stadium alone
					case "ambiance" -> Ambiances.scene(id, Ambiances.NONE); // the ambiance alone, no stadium
					default -> id; // a preset brings its own stadium
				};
				shots.add(new Shot(kind.trim(), id, scene));
			}
		}
		String extra = settings.getProperty("extra", "");
		if (!extra.isBlank()) {
			for (String line : Files.readAllLines(FabricLoader.getInstance().getGameDir().resolve(extra), StandardCharsets.UTF_8)) {
				String[] parts = line.split("\t");
				if (parts.length == 3) {
					shots.add(new Shot(parts[0], parts[1], parts[2]));
				}
			}
		}
		if (!only.isEmpty()) {
			shots.removeIf(shot -> only.stream().noneMatch(id -> shot.id().equals(id) || shot.id().endsWith(":" + id)));
		}
		LOG.info("Scene capture: {} scenes into {}", shots.size(), out);
	}

	/** Server thread: noon, nothing else around, the two Pokémon face to face along X, the camera on the side. */
	private void stage(MinecraftServer server) {
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		ServerLevel level = player.serverLevel();
		for (String command : new String[] {"time set 6000", "weather clear", "gamerule doDaylightCycle false",
				"gamerule doWeatherCycle false", "gamerule doMobSpawning false", "gamerule doPokemonSpawning false", "gamemode spectator @a"}) {
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
		}
		for (Entity entity : com.google.common.collect.Lists.newArrayList(level.getAllEntities())) {
			if (!(entity instanceof ServerPlayer)) {
				entity.discard();
			}
		}
		int x = level.getSharedSpawnPos().getX();
		int z = level.getSharedSpawnPos().getZ();
		double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
		double cx = x + 0.5;
		double cz = z + 0.5;
		double gap = Double.parseDouble(settings.getProperty("gap", "3"));
		PokemonEntity user = MoveCapture.spawn(level, settings.getProperty("user", "charizard"), cx - gap, y, cz, -90f);
		PokemonEntity target = MoveCapture.spawn(level, settings.getProperty("target", "blastoise"), cx + gap, y, cz, 90f);
		double distance = Double.parseDouble(settings.getProperty("distance", "9"));
		double height = Double.parseDouble(settings.getProperty("height", "3"));
		float pitch = (float) Math.toDegrees(Math.atan2(height - 1.0, distance));
		player.teleportTo(level, cx, y + height, cz + distance, 180f, pitch);
		middle = new double[] {cx, y, cz + 0.01, 180f, pitch};
		camera = new double[] {cx, y + height, cz + distance, 180f, pitch};
		// The field's two sides, as a battle seen from afar gives them: where each side's Pokémon stands.
		spots = List.of(new Vec3(cx - gap, y, cz), new Vec3(cx + gap, y, cz));
		targetId = target.getId();
		userId = user.getId();
	}

	private static void teleport(Minecraft mc, double[] where) {
		MinecraftServer server = mc.getSingleplayerServer();
		server.execute(() -> {
			ServerPlayer player = server.getPlayerList().getPlayers().get(0);
			player.teleportTo(player.serverLevel(), where[0], where[1], where[2], (float) where[3], (float) where[4]);
		});
	}

	/**
	 * The scene as a battle shows it (its stadium built whole, around the player where they stand), held long enough:
	 * Showtime's own start, reached by reflection since only a battle or the menu's preview call it.
	 */
	private static void beginAround(String scene) throws ReflectiveOperationException {
		String ambiance = Ambiances.sceneAmbiance(scene);
		String stadium = Ambiances.sceneStadium(scene);
		if (stadium == null) {
			stadium = Ambiances.presetStadium(ambiance);
		}
		Object resolved = Ambiances.resolve(ambiance, stadium);
		java.lang.reflect.Method begin = BattleAmbiance.class.getDeclaredMethod("begin",
				com.mystaria.showtime.ambiance.Ambiance.class, java.util.Collection.class, int.class);
		begin.setAccessible(true);
		begin.invoke(null, resolved, List.of(), 100_000);
		java.lang.reflect.Field owned = BattleAmbiance.class.getDeclaredField("owned");
		owned.setAccessible(true);
		owned.set(null, true);
	}

	/** An id as the lobby looks its picture up: lower case, "ns:name" → "ns/name", anything else odd → "_". */
	private static String path(String id) {
		return id.toLowerCase(Locale.ROOT).replace(':', '/').replaceAll("[^a-z0-9_./-]", "_");
	}

	private void finish(Minecraft mc) {
		if (state == State.DONE) {
			return;
		}
		state = State.DONE;
		BattleAmbiance.endDistant();
		try (Writer writer = Files.newBufferedWriter(FabricLoader.getInstance().getGameDir().resolve("capture-done.txt"), StandardCharsets.UTF_8)) {
			for (String line : report) {
				writer.write(line + "\n");
			}
		} catch (IOException e) {
			LOG.error("Could not write the capture report", e);
		}
		mc.stop();
	}
}
