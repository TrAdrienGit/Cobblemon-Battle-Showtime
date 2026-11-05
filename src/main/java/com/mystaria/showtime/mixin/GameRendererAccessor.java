package com.mystaria.showtime.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;

/** Minecraft's screen filters (post effects), for the battle ambiances' {@code filter}. */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {

	@Invoker("loadEffect")
	void showtime$loadEffect(ResourceLocation id);
}
