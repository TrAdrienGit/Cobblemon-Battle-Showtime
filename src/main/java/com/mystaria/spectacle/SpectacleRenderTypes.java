package com.mystaria.spectacle;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderType;

/**
 * Spectacle's own render types. {@link #GLOW}: additive light like Minecraft's lightning, but <b>without writing
 * depth</b> and <b>without back-face culling</b> (Adrien's test 2026-11-02: drawn before translucent blocks, lightning's
 * depth hid the glass behind a weather haze; and flat shapes — walls, rings — showed from one side only).
 */
public final class SpectacleRenderTypes extends RenderType {

	public static final RenderType GLOW = create("cobblemon_spectacle_glow", DefaultVertexFormat.POSITION_COLOR,
			VertexFormat.Mode.QUADS, 1536, false, true, CompositeState.builder()
					.setShaderState(RENDERTYPE_LIGHTNING_SHADER)
					.setWriteMaskState(COLOR_WRITE)
					.setTransparencyState(LIGHTNING_TRANSPARENCY)
					.setCullState(NO_CULL)
					.setOutputState(WEATHER_TARGET)
					.createCompositeState(false));

	private SpectacleRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling,
			boolean sortOnUpload, Runnable setup, Runnable clear) {
		super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
	}
}
