package com.mystaria.showtime.gui;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.mystaria.showtime.battle.BattleCinematic;

/**
 * The camera of {@link ShowtimeMenuScreen}'s preview (2026-11-06): the menu's portrait is an opening on the real
 * world, so the camera stands where the player appears exactly inside it, with the real effects around them.
 */
public final class MenuPreview {

	/** World height shown in the opening, in blocks: the trainer with some room for the effects. */
	private static final double SHOWN_HEIGHT = 3.2;
	private static final double MAX_DISTANCE = 24;
	/** Three-quarter views first, then whichever other side has no wall between the camera and the player. */
	private static final float[] SIDES = {30, -30, 60, -60, 0, 90, -90, 120, -120, 180};

	private static boolean active;
	private static double openingX;
	private static double openingY;
	private static double openingSize;
	private static float yaw;

	private MenuPreview() {
	}

	/** The opening's center and size, in GUI units. */
	public static void start(double centerX, double centerY, double size) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) {
			return;
		}
		openingX = centerX;
		openingY = centerY;
		openingSize = size;
		float base = mc.player.getYRot() + 180;
		yaw = base + SIDES[0];
		for (float side : SIDES) {
			Vec3 target = target(mc.player, 1);
			Vec3 from = position(mc, target, base + side);
			if (mc.level.clip(new ClipContext(target, from, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player))
					.getType() == HitResult.Type.MISS) {
				yaw = base + side;
				break;
			}
		}
		active = true;
	}

	public static void stop() {
		active = false;
	}

	/** Called by {@code CameraMixin}: where the camera stands, or null when no preview is showing. */
	public static BattleCinematic.CameraPose cameraPose(Camera camera, float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		if (!active || mc.player == null) {
			return null;
		}
		return new BattleCinematic.CameraPose(position(mc, target(mc.player, partialTick), yaw), yaw, 0, true);
	}

	/** Called when drawing an entity: the local trainer always faces the preview's camera, wherever it had to stand. */
	public static Float facingYaw(net.minecraft.world.entity.LivingEntity entity) {
		return active && entity == Minecraft.getInstance().player ? yaw + 180 : null;
	}

	private static Vec3 target(LocalPlayer player, float partialTick) {
		return player.getPosition(partialTick).add(0, 1, 0);
	}

	/** Solves the projection: the target lands on the opening's center, off the screen's center. */
	private static Vec3 position(Minecraft mc, Vec3 target, float cameraYaw) {
		double width = mc.getWindow().getGuiScaledWidth();
		double height = mc.getWindow().getGuiScaledHeight();
		double tan = Math.tan(Math.toRadians(mc.options.fov().get() / 2.0));
		double right = (openingX - width / 2) / (height / 2) * tan;
		double up = (height / 2 - openingY) / (height / 2) * tan;
		double distance = Math.min(MAX_DISTANCE, SHOWN_HEIGHT * height / (openingSize * 2 * tan));
		double radians = Math.toRadians(cameraYaw);
		double sin = Math.sin(radians);
		double cos = Math.cos(radians);
		Vec3 ray = new Vec3(-sin - right * cos, up, cos - right * sin);
		return target.subtract(ray.scale(distance));
	}
}
