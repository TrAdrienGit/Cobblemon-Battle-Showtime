package com.mystaria.showtime;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderType;

/**
 * Showtime's own render types. {@link #GLOW}: additive light like Minecraft's lightning, but <b>without writing
 * depth</b> and <b>without back-face culling</b> (in-game test 2026-11-02: drawn before translucent blocks, lightning's
 * depth hid the glass behind a weather haze; and flat shapes — walls, rings — showed from one side only).
 */
public final class ShowtimeRenderTypes extends RenderType {

	public static final RenderType GLOW = create("cobblemon_battle_showtime_glow", DefaultVertexFormat.POSITION_COLOR,
			VertexFormat.Mode.QUADS, 1536, false, true, CompositeState.builder()
					.setShaderState(RENDERTYPE_LIGHTNING_SHADER)
					.setWriteMaskState(COLOR_WRITE)
					.setTransparencyState(LIGHTNING_TRANSPARENCY)
					.setCullState(NO_CULL)
					.setOutputState(WEATHER_TARGET)
					.createCompositeState(false));

	/**
	 * {@link #GLOW}'s solid sibling (2026-11-10): ordinary translucent colour, tested against depth (hidden behind
	 * blocks), not writing it. For shapes that must keep their own colour in daylight (butterflies, a mirror ball).
	 */
	public static final RenderType TINT = create("cobblemon_battle_showtime_tint", DefaultVertexFormat.POSITION_COLOR,
			VertexFormat.Mode.QUADS, 1536, false, true, CompositeState.builder()
					.setShaderState(RENDERTYPE_LIGHTNING_SHADER)
					.setWriteMaskState(COLOR_WRITE)
					.setTransparencyState(TRANSLUCENT_TRANSPARENCY)
					.setCullState(NO_CULL)
					.setOutputState(WEATHER_TARGET)
					.createCompositeState(false));

	private ShowtimeRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling,
			boolean sortOnUpload, Runnable setup, Runnable clear) {
		super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
	}
}
