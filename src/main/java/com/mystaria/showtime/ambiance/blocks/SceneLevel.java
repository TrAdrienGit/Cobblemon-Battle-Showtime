package com.mystaria.showtime.ambiance.blocks;

import java.util.ArrayDeque;
import java.util.List;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A stadium's blocks as a small world of their own (2026-11-19), for Minecraft's block renderer to read: what is next
 * to a block (hidden faces dropped, ambient occlusion), the biome's colours (grass, leaves, water) and the light.
 * <p>
 * The world stored is only the shell people can see. Under a column's lowest stored block, {@link #FILL} stands in
 * for the rock below, so no face is drawn against it; outside the part's columns there is air, so a part's edge
 * draws its sides (the next part, coarser, draws its own). An objects part (trees, rocks) stands on the ground: under
 * {@link #ground} it reads rock.
 * <p>
 * A {@link LevelReader}, so a plant can be asked whether it grows where it is put ({@code canSurvive}); what is not
 * about blocks (registries, the dimension) is the real world's, read only, never its blocks.
 */
final class SceneLevel implements LevelReader {

	static final BlockState AIR = Blocks.AIR.defaultBlockState();
	static final BlockState FILL = Blocks.STONE.defaultBlockState();

	/** The ground's top (the y of the first air block) of a column, for an objects part. */
	interface Ground {
		int top(int x, int z);
	}

	/** What stands where nothing is stored, for a part that is not a plain ground (a sea's water, a cave's rock). */
	interface Fallback {
		BlockState at(int x, int y, int z);
	}

	private Fallback fallback;
	/** The sky's light everywhere: 15 out of doors, low in a cave (its rock lit only by what glows in it). */
	private int skyLight = 15;
	/** The water's colour, an ambiance's own; -1: the biome's. */
	private int waterTint = -1;
	/** Another part whose glowing blocks light this one too (a cave's pillars, lit by the cave's lamps). */
	private SceneLevel lightOf;

	final Long2ObjectOpenHashMap<BlockState> blocks = new Long2ObjectOpenHashMap<>();
	/** Column (x, z) -> its lowest stored block's y. A column absent is outside the part. */
	private final Long2IntOpenHashMap bottoms = new Long2IntOpenHashMap();
	private final Long2ByteOpenHashMap blockLight = new Long2ByteOpenHashMap();
	private final Ground ground;
	private final Level host;
	private final Holder<Biome> biome;
	private final int minY;
	private final int height;

	SceneLevel(Level host, Holder<Biome> biome, Ground ground, int minY, int height) {
		this.host = host;
		this.biome = biome;
		this.ground = ground;
		this.minY = minY;
		this.height = height;
		bottoms.defaultReturnValue(Integer.MAX_VALUE);
	}

	static long column(int x, int z) {
		return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
	}

	void set(int x, int y, int z, BlockState state) {
		blocks.put(BlockPos.asLong(x, y, z), state);
		long c = column(x, z);
		if (ground == null && y < bottoms.get(c)) {
			bottoms.put(c, y);
		}
	}

	/** Marks the column part of the stadium, filled down from {@code bottom} (rock below it, never drawn). */
	void column(int x, int z, int bottom) {
		long c = column(x, z);
		if (bottom < bottoms.get(c)) {
			bottoms.put(c, bottom);
		}
	}

	SceneLevel fallback(Fallback fallback) {
		this.fallback = fallback;
		return this;
	}

	SceneLevel skyLight(int skyLight) {
		this.skyLight = skyLight;
		return this;
	}

	SceneLevel lightOf(SceneLevel other) {
		this.lightOf = other;
		return this;
	}

	private int light(long key) {
		int own = blockLight.get(key);
		return lightOf == null ? own : Math.max(own, lightOf.blockLight.get(key));
	}

	SceneLevel waterTint(int rgb) {
		this.waterTint = rgb;
		return this;
	}

	BlockState stored(int x, int y, int z) {
		return blocks.get(BlockPos.asLong(x, y, z));
	}

	boolean isAir(int x, int y, int z) {
		return getBlockState(new BlockPos(x, y, z)).isAir();
	}

	@Override
	public BlockState getBlockState(BlockPos pos) {
		BlockState state = blocks.get(pos.asLong());
		if (state != null) {
			return state;
		}
		if (fallback != null) {
			return fallback.at(pos.getX(), pos.getY(), pos.getZ());
		}
		if (ground != null) {
			return pos.getY() < ground.top(pos.getX(), pos.getZ()) ? FILL : AIR;
		}
		int bottom = bottoms.get(column(pos.getX(), pos.getZ()));
		// A column outside the part is air: the part's edge draws its sides, down to where the next part's ground is.
		return bottom != Integer.MAX_VALUE && pos.getY() < bottom ? FILL : AIR;
	}

	@Override
	public FluidState getFluidState(BlockPos pos) {
		return getBlockState(pos).getFluidState();
	}

	@Override
	public BlockEntity getBlockEntity(BlockPos pos) {
		return null;
	}

	/**
	 * The light of the glowing blocks (lava, fire, lanterns), spread out like Minecraft's: one level less a block,
	 * stopped by opaque blocks. Called once every block is in place.
	 */
	void spreadLight() {
		ArrayDeque<long[]> open = new ArrayDeque<>();
		for (var entry : blocks.long2ObjectEntrySet()) {
			int emission = entry.getValue().getLightEmission();
			if (emission > 1) {
				open.add(new long[] {entry.getLongKey(), emission});
			}
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		while (!open.isEmpty()) {
			long[] next = open.poll();
			long key = next[0];
			int level = (int) next[1];
			if (blockLight.get(key) >= level) {
				continue;
			}
			blockLight.put(key, (byte) level);
			if (level <= 1) {
				continue;
			}
			for (Direction d : Direction.values()) {
				pos.set(key).move(d);
				BlockState there = getBlockState(pos);
				if (there.isSolidRender(this, pos)) {
					continue;
				}
				long k = pos.asLong();
				if (blockLight.get(k) < level - 1) {
					open.add(new long[] {k, level - 1});
				}
			}
		}
	}

	@Override
	public float getShade(Direction direction, boolean shade) {
		if (!shade) {
			return 1f;
		}
		return switch (direction) {
			case DOWN -> 0.5f;
			case UP -> 1f;
			case NORTH, SOUTH -> 0.8f;
			default -> 0.6f;
		};
	}

	@Override
	public int getBrightness(LightLayer layer, BlockPos pos) {
		return layer == LightLayer.SKY ? skyLight : light(pos.asLong());
	}

	@Override
	public int getRawBrightness(BlockPos pos, int darkening) {
		return Math.max(skyLight - darkening, light(pos.asLong()));
	}

	@Override
	public boolean canSeeSky(BlockPos pos) {
		return skyLight == 15;
	}

	@Override
	public LevelLightEngine getLightEngine() {
		// Never read for light (getBrightness answers), but must not be null for code that only checks it.
		return host.getLightEngine();
	}

	@Override
	public int getBlockTint(BlockPos pos, ColorResolver resolver) {
		if (waterTint >= 0 && resolver == net.minecraft.client.renderer.BiomeColors.WATER_COLOR_RESOLVER) {
			return waterTint;
		}
		return resolver.getColor(biome.value(), pos.getX(), pos.getZ());
	}

	@Override
	public Holder<Biome> getBiome(BlockPos pos) {
		return biome;
	}

	@Override
	public Holder<Biome> getNoiseBiome(int x, int y, int z) {
		return biome;
	}

	@Override
	public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
		return biome;
	}

	// What follows is asked of a level by code that is not about these blocks: answered by the real world, or empty.

	@Override
	public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean require) {
		return null;
	}

	@Override
	@Deprecated
	public boolean hasChunk(int x, int z) {
		return true;
	}

	@Override
	public int getHeight(Heightmap.Types type, int x, int z) {
		return minY;
	}

	@Override
	public int getSkyDarken() {
		return 0;
	}

	@Override
	public BiomeManager getBiomeManager() {
		return host.getBiomeManager();
	}

	@Override
	public boolean isClientSide() {
		return true;
	}

	@Override
	@Deprecated
	public int getSeaLevel() {
		return host.getSeaLevel();
	}

	@Override
	public DimensionType dimensionType() {
		return host.dimensionType();
	}

	@Override
	public RegistryAccess registryAccess() {
		return host.registryAccess();
	}

	@Override
	public FeatureFlagSet enabledFeatures() {
		return host.enabledFeatures();
	}

	@Override
	public WorldBorder getWorldBorder() {
		return host.getWorldBorder();
	}

	@Override
	public BlockGetter getChunkForCollisions(int x, int z) {
		return this;
	}

	@Override
	public List<VoxelShape> getEntityCollisions(Entity entity, AABB box) {
		return List.of();
	}

	@Override
	public int getHeight() {
		return height;
	}

	@Override
	public int getMinBuildHeight() {
		return minY;
	}
}
