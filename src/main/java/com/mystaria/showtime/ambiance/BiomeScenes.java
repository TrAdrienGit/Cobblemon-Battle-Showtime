package com.mystaria.showtime.ambiance;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * The scene of the place (2026-11-19): with "Scene by biome" on, a battle's stadium and ambiance follow where the
 * player stands instead of their own choice: a meadow on the plains, the sea on the ocean, a cave underground...
 *
 * <p>Biomes added by other mods are read the same way as Minecraft's: first by their tags (the common {@code c:}
 * ones, Cobblemon's own, which most biome mods fill in for spawns, and Minecraft's), then by words of their name
 * ({@code volcanic}, {@code ash}...), last by their climate (temperature and downfall, as the server sends them), so
 * every biome falls somewhere. The world around counts too: the Nether, the End, underground, under water, high
 * up, a thunderstorm, the night.
 *
 * <p>It is the player's own choice, nothing more: a scene another mod imposes (a lobby's, an event NPC's, a Pokémon
 * Reborn field's, a tournament's...) wins over it, the ambiance and the stadium each; the biome fills only what is left
 * to the player ({@code BattleAmbiance.scene}).
 *
 * <p>Read once per battle (the scene never changes in its middle), again at the next battle or preview.
 */
public final class BiomeScenes {

	private static final String NS = "cobblemon_battle_showtime:";
	/** The last scene picked ({@code [stadium, ambiance]}), kept until {@link #forget}. */
	private static String[] picked;

	private BiomeScenes() {
	}

	/** Read the place again at the next ask: a battle or a preview starts. */
	public static void forget() {
		picked = null;
	}

	/** The scene where the local player stands, {@code [stadium, ambiance]}; null: nowhere to read it. */
	public static String[] scene() {
		if (picked == null) {
			picked = read();
		}
		return picked;
	}

	private static String[] read() {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		Level level = mc.level;
		if (player == null || level == null) {
			return null;
		}
		BlockPos pos = player.blockPosition();
		Holder<Biome> biome = level.getBiome(pos);
		String name = biome.unwrapKey().map(key -> key.location().getPath()).orElse("").toLowerCase(Locale.ROOT);
		String place = place(level, player, pos, biome, name);
		String[] scene = preset(place);
		if (scene == null) {
			return null;
		}
		// A thunderstorm over the place, out in the open: the storm's ambiance on the place's stadium.
		if (level.isThundering() && level.canSeeSky(pos) && level.dimension() == Level.OVERWORLD
				&& Ambiances.isAmbiance(NS + "storm")) {
			scene[1] = NS + "storm";
		}
		return scene;
	}

	/** The preset (or stadium + ambiance pair) the place looks like. */
	private static String place(Level level, Player player, BlockPos pos, Holder<Biome> biome, String name) {
		boolean night = level.dimension() == Level.OVERWORLD && level.isNight();
		if (level.dimension() == Level.NETHER || any(biome, "nether")) {
			if (name.contains("soul") || name.contains("warped")) {
				return "dark_grotto";
			}
			return "dragon_lair";
		}
		if (level.dimension() == Level.END || any(biome, "end")) {
			return "cosmos";
		}
		if (player.isEyeInFluid(FluidTags.WATER)) {
			return any(biome, "frozen_ocean") || cold(biome) ? "frozen_sea" : "deep_sea";
		}
		// Underground: a cave biome, or no sky far below the sea's level.
		boolean underground = any(biome, "cave") || any(biome, "underground")
				|| !level.canSeeSky(pos) && pos.getY() < level.getSeaLevel() - 8;
		if (underground) {
			if (name.contains("deep_dark") || name.contains("sculk") || any(biome, "deep_dark")) {
				return "dark_grotto";
			}
			if (name.contains("lush") || name.contains("crystal") || name.contains("amethyst") || name.contains("geode")) {
				return "crystal_grotto";
			}
			return "grotto";
		}
		// Words of the name: what tags rarely say.
		if (has(name, "volcan", "lava", "magma", "basalt", "obsidian")) {
			return "dragon_lair";
		}
		if (has(name, "ash", "burnt", "scorched", "wasteland")) {
			return "scorched_badlands";
		}
		if (has(name, "cherry", "sakura", "blossom")) {
			return "meadow|sakura";
		}
		if (any(biome, "mushroom") || has(name, "mushroom", "fungal", "fungus")) {
			return "meadow|fairy";
		}
		if (any(biome, "spooky") || any(biome, "dead") || has(name, "dark_forest", "haunted", "spooky", "dead", "gloom")) {
			return "forest|spectral";
		}
		if (any(biome, "magical") || has(name, "enchanted", "fairy", "magic")) {
			return "storybook_court";
		}
		// Water.
		if (any(biome, "ocean") || any(biome, "deep_ocean")) {
			if (any(biome, "frozen_ocean") || cold(biome)) {
				return "frozen_sea";
			}
			return "open_sea";
		}
		if (any(biome, "river")) {
			return cold(biome) ? "frozen_sea" : "open_sea";
		}
		if (any(biome, "beach") || any(biome, "stony_shores") || has(name, "shore", "beach", "coast")) {
			if (cold(biome)) {
				return "frozen_sea";
			}
			return has(name, "stony", "stone", "gravel") || any(biome, "stony_shores") ? "grey_shore" : "ashen_shore|water_surface";
		}
		if (any(biome, "swamp") || any(biome, "mangrove") || has(name, "swamp", "marsh", "bog", "bayou", "mangrove", "fen")) {
			return "bog";
		}
		// Hot and dry.
		if (any(biome, "badlands") || any(biome, "mesa") || has(name, "badlands", "mesa", "canyon")) {
			return "rocky_canyon";
		}
		if (any(biome, "desert") || any(biome, "sandy") || has(name, "desert", "dune")) {
			return "sand_dunes";
		}
		// Cold and high.
		boolean snowy = any(biome, "snowy") || any(biome, "icy") || any(biome, "frozen") || any(biome, "glacial")
				|| any(biome, "tundra") || cold(biome);
		boolean high = any(biome, "mountain_peak") || any(biome, "peak") || any(biome, "mountain")
				|| any(biome, "mountain_slope") || any(biome, "windswept") || pos.getY() > 160;
		if (high) {
			return snowy ? "snowy_peak" : "peak";
		}
		if (snowy) {
			return "snowy_peak";
		}
		// Green.
		if (any(biome, "flower_forest") || any(biome, "floral") || has(name, "flower", "garden", "bloom")) {
			return "blooming_garden";
		}
		if (any(biome, "jungle") || any(biome, "forest") || any(biome, "taiga") || any(biome, "dense_vegetation")
				|| has(name, "forest", "wood", "grove", "taiga", "jungle", "rainforest")) {
			return "deep_forest";
		}
		if (any(biome, "savanna") || any(biome, "arid")) {
			return "rocky_canyon";
		}
		if (any(biome, "plains") || any(biome, "meadow") || has(name, "plain", "meadow", "field", "prairie", "steppe")) {
			return night ? "starry_meadow" : "sunny_meadow";
		}
		// Nothing said: the climate.
		float temperature = biome.value().getBaseTemperature();
		float downfall = downfall(biome);
		if (temperature >= 1.5f && downfall <= 0.2f) {
			return "sand_dunes";
		}
		if (temperature >= 1.0f && downfall <= 0.3f) {
			return "rocky_canyon";
		}
		if (temperature <= 0.15f) {
			return "snowy_peak";
		}
		if (downfall >= 0.85f) {
			return temperature >= 0.7f ? "deep_forest" : "misty_meadow";
		}
		if (downfall >= 0.6f) {
			return "deep_forest";
		}
		return night ? "starry_meadow" : "sunny_meadow";
	}

	/** A preset's stadium and ambiance, or a {@code stadium|ambiance} pair; null if not loaded. */
	private static String[] preset(String place) {
		if (place.contains("|")) {
			String[] pair = place.split("\\|", 2);
			String stadium = NS + pair[0];
			String ambiance = NS + pair[1];
			return Ambiances.isStadium(stadium) && Ambiances.usable(ambiance) ? new String[] {stadium, ambiance} : null;
		}
		String id = NS + place;
		if (!Ambiances.isPreset(id) || !Ambiances.usable(id)) {
			return null;
		}
		return new String[] {Ambiances.presetStadium(id), Ambiances.presetAmbiance(id)};
	}

	/** Whether the biome has the tag {@code is_<what>} of the common tags, Cobblemon's or Minecraft's. */
	private static boolean any(Holder<Biome> biome, String what) {
		return biome.is(tag("c", "is_" + what)) || biome.is(tag("cobblemon", "is_" + what))
				|| biome.is(tag("minecraft", "is_" + what));
	}

	private static TagKey<Biome> tag(String namespace, String path) {
		return TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath(namespace, path));
	}

	private static boolean has(String name, String... words) {
		for (String word : words) {
			if (name.contains(word)) {
				return true;
			}
		}
		return false;
	}

	/** Snow falls there (Minecraft's own threshold). */
	private static boolean cold(Holder<Biome> biome) {
		return biome.value().getBaseTemperature() < 0.15f;
	}

	/** The biome's downfall (its humidity), as the server sent it; 0.5 when it cannot be read. */
	private static float downfall(Holder<Biome> biome) {
		try {
			return biome.value().climateSettings.downfall;
		} catch (RuntimeException | LinkageError ex) {
			return 0.5f;
		}
	}
}
