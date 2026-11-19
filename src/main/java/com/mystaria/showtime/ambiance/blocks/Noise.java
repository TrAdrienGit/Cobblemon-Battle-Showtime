package com.mystaria.showtime.ambiance.blocks;

import net.minecraft.util.Mth;

/**
 * The same hash and value noise as {@code BattleAmbiance}'s (2026-11-19): a stadium built of blocks keeps the shapes
 * the smooth one had (its hills, dunes, ranges), so both looks show the same place.
 */
final class Noise {

	private Noise() {
	}

	static float hash(int seed) {
		int x = seed * 0x9E3779B1;
		x ^= x >>> 15;
		x *= 0x85EBCA77;
		x ^= x >>> 13;
		return (x & 0xFFFFFF) / (float) 0x1000000;
	}

	/** A hash of a block position, 0..1, different for each {@code salt}. */
	static float at(int x, int y, int z, int salt) {
		return hash(x * 73856093 ^ y * 19349663 ^ z * 83492791 ^ salt * 0x2545F491);
	}

	static double value(double x, double y, double z) {
		int x0 = Mth.floor(x);
		int y0 = Mth.floor(y);
		int z0 = Mth.floor(z);
		double fx = smooth(x - x0);
		double fy = smooth(y - y0);
		double fz = smooth(z - z0);
		double c000 = lattice(x0, y0, z0);
		double c100 = lattice(x0 + 1, y0, z0);
		double c010 = lattice(x0, y0 + 1, z0);
		double c110 = lattice(x0 + 1, y0 + 1, z0);
		double c001 = lattice(x0, y0, z0 + 1);
		double c101 = lattice(x0 + 1, y0, z0 + 1);
		double c011 = lattice(x0, y0 + 1, z0 + 1);
		double c111 = lattice(x0 + 1, y0 + 1, z0 + 1);
		double a = Mth.lerp(fx, c000, c100);
		double b = Mth.lerp(fx, c010, c110);
		double c = Mth.lerp(fx, c001, c101);
		double d = Mth.lerp(fx, c011, c111);
		return Mth.lerp(fz, Mth.lerp(fy, a, b), Mth.lerp(fy, c, d));
	}

	static double smooth(double t) {
		return t * t * (3 - 2 * t);
	}

	private static double lattice(int x, int y, int z) {
		return hash(x * 73856093 ^ y * 19349663 ^ z * 83492791);
	}
}
