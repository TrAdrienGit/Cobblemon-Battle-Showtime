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

	/**
	 * Solid shapes that hide one another properly (2026-11-10): they write depth, so an island's top hides its own
	 * underside and a stand's rows hide the ones behind. Opaque colour, no transparency.
	 */
	public static final RenderType OPAQUE = create("cobblemon_battle_showtime_opaque", DefaultVertexFormat.POSITION_COLOR,
			VertexFormat.Mode.QUADS, 1536, false, false, CompositeState.builder()
					.setShaderState(RENDERTYPE_LIGHTNING_SHADER)
					.setWriteMaskState(COLOR_DEPTH_WRITE)
					.setTransparencyState(NO_TRANSPARENCY)
					.setCullState(NO_CULL)
					.setOutputState(WEATHER_TARGET)
					.createCompositeState(false));

	/** A plain white texture, for the entity formats below: the vertex colour alone shows. */
	private static final net.minecraft.resources.ResourceLocation WHITE = net.minecraft.resources.ResourceLocation
			.fromNamespaceAndPath("cobblemon_battle_showtime", "textures/misc/white.png");

	/**
	 * {@link #GLOW} under a shader pack (2026-11-19). Shader packs draw lightning's shader with their own program, made
	 * for bolts: the ambiances' glows came out dull, or not at all. This one uses glowing eyes' shader, which packs draw
	 * emissive (often with bloom); same blending, no depth write, no culling. Entity vertex format: write through
	 * {@link ShaderPackQuads}.
	 */
	public static final RenderType GLOW_SHADERS = create("cobblemon_battle_showtime_glow_shaders", DefaultVertexFormat.NEW_ENTITY,
			VertexFormat.Mode.QUADS, 1536, false, true, CompositeState.builder()
					.setShaderState(RENDERTYPE_EYES_SHADER)
					.setTextureState(new TextureStateShard(WHITE, false, false))
					.setWriteMaskState(COLOR_WRITE)
					.setTransparencyState(LIGHTNING_TRANSPARENCY)
					.setCullState(NO_CULL)
					.createCompositeState(false));

	/** {@link #TINT} under a shader pack: translucent, its own colour whatever the light (emissive entity shader). */
	public static final RenderType TINT_SHADERS = create("cobblemon_battle_showtime_tint_shaders", DefaultVertexFormat.NEW_ENTITY,
			VertexFormat.Mode.QUADS, 1536, false, true, CompositeState.builder()
					.setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
					.setTextureState(new TextureStateShard(WHITE, false, false))
					.setWriteMaskState(COLOR_WRITE)
					.setTransparencyState(TRANSLUCENT_TRANSPARENCY)
					.setCullState(NO_CULL)
					.setOverlayState(OVERLAY)
					.createCompositeState(false));

	/**
	 * {@link #OPAQUE} under a shader pack: a solid entity surface the pack lights and shades (sun, shadows, its normal)
	 * instead of a flat colour drawn as weather.
	 */
	public static final RenderType OPAQUE_SHADERS = create("cobblemon_battle_showtime_opaque_shaders", DefaultVertexFormat.NEW_ENTITY,
			VertexFormat.Mode.QUADS, 1536, true, false, CompositeState.builder()
					.setShaderState(RENDERTYPE_ENTITY_SOLID_SHADER)
					.setTextureState(new TextureStateShard(WHITE, false, false))
					.setTransparencyState(NO_TRANSPARENCY)
					.setCullState(NO_CULL)
					.setLightmapState(LIGHTMAP)
					.setOverlayState(OVERLAY)
					.createCompositeState(true));

	private ShowtimeRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling,
			boolean sortOnUpload, Runnable setup, Runnable clear) {
		super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
	}
}
