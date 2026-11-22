package com.mystaria.showtime.ambiance.blocks;

import java.util.concurrent.CompletableFuture;
import java.util.function.DoubleBinaryOperator;
import java.util.function.Supplier;

import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.Util;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;

import com.mystaria.showtime.ambiance.Ambiance;

/**
 * Stadiums built of Minecraft's own blocks (2026-11-19). The smooth stadiums were coloured sheets drawn with a shader
 * that shader packs treat as weather: under Iris they came out dark and flat. These are real blocks (textures, ambient
 * occlusion, biome colours), drawn in the render types Minecraft uses for falling blocks, so shader packs light and
 * shade them like the world around.
 * <p>
 * A stadium is laid out and tessellated once, in the background, when its battle starts (or its field grows); until
 * it is ready the smooth stadium stands in. Each frame only replays the vertices of the pieces in view.
 * <p>
 * Client thread, except the build itself.
 */
public final class BlockScenery {

	private static final Logger LOG = LoggerFactory.getLogger("cobblemon_battle_showtime");

	/** What a mesh shows: the same stadium (by identity), as the ambiance sees it ({@code look}), on the same field. */
	private record Key(Object stadium, Object look, Vec3 center, float fieldRadius) {
	}

	private static Key shownKey;
	private static SceneMesh shown;
	private static Key pendingKey;
	private static CompletableFuture<SceneMesh> pending;
	private static Key failedKey;
	/** The stadium shown may stand in for another until that one is built (an ambiance turning into another, 2026-11-20). */
	private static boolean holdOver;
	/** The stadium shown stays, even once another is built, until released (a sheet spreading over it, 2026-11-20). */
	private static boolean pinned;

	private BlockScenery() {
	}

	/**
	 * Draws an outdoor stadium in blocks, its build started the first time it is asked for. {@code pose}: at
	 * {@code center}, relative to the camera; {@code ground}: the real ground's height at (x, z) from the centre, relative
	 * to it (read now, on this thread). False while the blocks are not ready, or could not be built: draw the smooth one.
	 */
	public static boolean drawLand(PoseStack pose, MultiBufferSource buffers, Vec3 center, Vec3 camera, Frustum frustum,
			Ambiance.Land land, Ambiance.Grass grass, Ambiance.Flowers flowers, float snowCover, boolean ablaze, float fieldRadius,
			DoubleBinaryOperator ground) {
		Key wanted = new Key(land, java.util.Arrays.asList(grass, flowers, snowCover, ablaze), center, fieldRadius);
		return draw(wanted, () -> LandScene.recipe(land, grass, flowers, snowCover, ablaze, center, fieldRadius, ground), pose,
				buffers, center, camera, frustum);
	}

	/**
	 * Draws a sea stadium in blocks (an islet, or the seabed); {@code frozen}: ice instead of water; {@code tint}: the
	 * water's colour. As {@link #drawLand}.
	 */
	public static boolean drawSea(PoseStack pose, MultiBufferSource buffers, Vec3 center, Vec3 camera, Frustum frustum,
			Ambiance.Sea sea, boolean frozen, int tint, float fieldRadius, DoubleBinaryOperator ground) {
		Key wanted = new Key(sea, java.util.Arrays.asList(frozen, tint), center, fieldRadius);
		return draw(wanted, () -> SeaScene.recipe(sea, frozen, tint, center, fieldRadius, ground), pose, buffers, center, camera,
				frustum);
	}

	/**
	 * Draws a cave stadium in blocks; {@code lightStrength}: how bright its light on the field is; {@code stalks}: the
	 * ambiance's stalactites and stalagmites (x, z from the centre, length, 1 hanging / 0 rising, in fours). As
	 * {@link #drawLand}.
	 */
	public static boolean drawCave(PoseStack pose, MultiBufferSource buffers, Vec3 center, Vec3 camera, Frustum frustum,
			Ambiance.Cavern cavern, float lightStrength, java.util.List<Float> stalks, float fieldRadius, DoubleBinaryOperator ground) {
		Key wanted = new Key(cavern, java.util.Arrays.asList(lightStrength, stalks), center, fieldRadius);
		return draw(wanted, () -> CaveScene.recipe(cavern, lightStrength, stalks, center, fieldRadius, ground), pose, buffers,
				center, camera, frustum);
	}

	/**
	 * Draws a hall stadium in blocks (a room, with its mirrored rooms behind glass mirrors, or a tent); {@code powerCut}: lamps out, the
	 * room dark. As {@link #drawLand}.
	 */
	public static boolean drawHall(PoseStack pose, MultiBufferSource buffers, Vec3 center, Vec3 camera, Frustum frustum,
			Ambiance.Hall hall, boolean powerCut, float fieldRadius, DoubleBinaryOperator ground) {
		Key wanted = new Key(hall, powerCut, center, fieldRadius);
		return draw(wanted, () -> HallScene.recipe(hall, powerCut, center, fieldRadius, ground), pose, buffers, center, camera,
				frustum);
	}

	/**
	 * A stadium seen from around a battle (2026-11-22): the real blocks round the field dressed in its blocks, none
	 * added ({@link ReskinScene}). {@code stadium}: what is shown (by identity); {@code checker}: a hall's dark squares,
	 * or null. Drawn a hair towards the camera, over the real blocks it covers. False while not ready.
	 */
	public static boolean drawReskin(PoseStack pose, MultiBufferSource buffers, Vec3 center, Vec3 camera, Frustum frustum,
			Object stadium, java.util.List<String> top, java.util.List<String> checker, float tile, java.util.List<String> soil,
			java.util.List<String> rock, Ambiance.Land land, Ambiance.Grass grass, Ambiance.Flowers flowers, double reach) {
		Key wanted = new Key(stadium, "reskin", center, (float) reach);
		java.util.List<LandScene.Plants.Kind> plants = land != null ? LandScene.Plants.kinds(land, grass, flowers, reach)
				: java.util.List.of();
		pose.pushPose();
		// Scaled round the camera: every vertex a thousandth closer, so these win over the real faces under them.
		pose.last().pose().set(new Matrix4f().scaling(0.999f).mul(pose.last().pose()));
		boolean drawn = draw(wanted, () -> ReskinScene.recipe(top, checker, tile, soil, rock, plants, center, reach), pose, buffers, center,
				camera, frustum);
		if (drawn && reskinLogged != shown) {
			reskinLogged = shown;
			LOG.debug("Dressed blocks drawn: {} of {} pieces in view", visiblePieces(camera, frustum), shown.pieces.size());
		}
		pose.popPose();
		return drawn;
	}

	/**
	 * A box hall's mirror planes as built of blocks (absolute x west, x east, z north, z south), across which the people
	 * in it are drawn mirrored.
	 */
	public static double[] hallMirrorPlanes(Vec3 center, double half) {
		return HallScene.mirrorPlanes(center, half);
	}

	private static SceneMesh reskinLogged;

	private static int visiblePieces(Vec3 camera, Frustum frustum) {
		int n = 0;
		for (SceneMesh.Piece piece : shown.pieces) {
			if (frustum == null || frustum.isVisible(piece.box().move(shown.originX, 0, shown.originZ))) {
				n++;
			}
		}
		return n;
	}

	private static boolean draw(Key wanted, Supplier<Supplier<SceneMesh>> recipe, PoseStack pose, MultiBufferSource buffers,
			Vec3 center, Vec3 camera, Frustum frustum) {
		collect();
		if (!wanted.equals(shownKey) && !wanted.equals(pendingKey) && !wanted.equals(failedKey)) {
			start(wanted, recipe);
		}
		// The previous build of the same stadium (before the field grew) stands in until the new one is ready.
		if (shown == null || shownKey.stadium() != wanted.stadium() && !holdOver) {
			return false;
		}
		Matrix4f toOrigin = new Matrix4f(pose.last().pose()).translate((float) (shown.originX - center.x), (float) -center.y,
				(float) (shown.originZ - center.z));
		shown.render(toOrigin, buffers, camera, frustum);
		return true;
	}

	/**
	 * The ambiance changes in the same place (2026-11-20: a transition between two fields of one stadium): the blocks
	 * shown stay until the new ones are built, instead of the bare floor standing in meanwhile.
	 */
	public static void holdOver() {
		holdOver = shown != null;
	}

	/** Keeps the stadium shown until {@link #release()}, standing in for the next one even once it is built. */
	public static void pin() {
		pinned = shown != null;
		holdOver = pinned;
	}

	/** The next stadium may take over (at once if it is built). */
	public static void release() {
		pinned = false;
	}

	/** Forgets every stadium built (battle over, the ground's look changed, resource packs reloaded). */
	public static void clear() {
		pinned = false;
		holdOver = false;
		shown = null;
		shownKey = null;
		pending = null; // its result, when it comes, is dropped
		pendingKey = null;
		failedKey = null;
	}

	private static void start(Key key, Supplier<Supplier<SceneMesh>> recipe) {
		Supplier<SceneMesh> build;
		try {
			build = recipe.get(); // reads the world: here, on the client thread
		} catch (RuntimeException ex) {
			LOG.warn("Stadium blocks could not be laid out, the smooth stadium stays", ex);
			failedKey = key;
			return;
		}
		pendingKey = key;
		pending = CompletableFuture.supplyAsync(build, Util.backgroundExecutor());
	}

	/** Takes a finished build, if any. */
	private static void collect() {
		if (pending == null || !pending.isDone() || pinned) {
			return;
		}
		CompletableFuture<SceneMesh> done = pending;
		Key key = pendingKey;
		pending = null;
		pendingKey = null;
		try {
			SceneMesh mesh = done.join();
			shown = mesh;
			shownKey = key;
			holdOver = false;
			if ("reskin".equals(key.look())) {
				LOG.debug("Blocks round a battle seen from afar dressed: {} quads", mesh.quads);
			} else {
				LOG.debug("Stadium blocks ready: {} quads in {} pieces", mesh.quads, mesh.pieces.size());
			}
		} catch (RuntimeException ex) {
			LOG.warn("Stadium blocks could not be built, the smooth stadium stays", ex);
			failedKey = key;
		}
	}
}
