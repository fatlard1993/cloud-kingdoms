package justfatlard.cloud_kingdoms.gen;

import justfatlard.cloud_kingdoms.block.CloudBlock;
import net.minecraft.util.RandomSource;

/**
 * The shape of one cloud, as a function rather than a model.
 *
 * <p>A handful of squashed spheroids ("puffs") are scattered around the structure centre and their
 * falloffs summed; anywhere the sum clears {@link #SURFACE} is cloud. Overlapping puffs blend into
 * one billowing mass instead of reading as a pile of balls, which is the whole reason for summing a
 * soft falloff rather than testing each sphere separately.
 *
 * <p>Two deliberate asymmetries make it read as weather rather than as an asteroid:
 *
 * <ul>
 *   <li><b>The underside is cut flat</b> at {@link CloudBlock#SETTLE_Y}. Every puff is centred on
 *       that plane and everything below it is discarded, so the widest layer of the mass is its
 *       bottom one and the silhouette from the ground is a flat white shelf: the shape the client is
 *       already drawing at that altitude. The billowing is all on top, where only someone who flew
 *       up can see it.</li>
 *   <li><b>The edge is chewed by value noise</b> before the threshold test, so the outline is
 *       lobed and ragged instead of the smooth ellipse a bare sum of spheroids gives.</li>
 * </ul>
 *
 * <p><b>Every method here is a pure function of world position.</b> That is not tidiness, it is the
 * load-bearing property: a cloud spans several chunks, and its piece's {@code postProcess} runs once
 * per chunk, in whatever order the chunks happen to generate. Two neighbouring chunks agree on where
 * the cloud edge falls only because both evaluate the same function at the same coordinates. Nothing
 * here may consume a {@link RandomSource} outside the constructor.
 */
public final class CloudField {

	/** Field value at the cloud surface. Lower spreads the mass out; higher tightens it to the puff cores. */
	private static final double SURFACE = 0.34;

	/** How hard the edge noise pushes the surface in and out, in field units. */
	private static final double EDGE_BITE = 0.30;

	/** Block width of one edge-noise cell. Roughly the size of the lobes it carves. */
	private static final int EDGE_CELL = 13;

	private final long seed;
	private final int baseY;
	private final int topY;
	private final Puff[] puffs;

	/**
	 * A squashed spheroid. Wide and short: {@code radius} out, {@code halfHeight} up, and the two are
	 * independent so a tier can be made broader without also being made taller.
	 *
	 * <p><b>Every puff is centred on the base plane</b>, which is the one decision that makes the
	 * underside flat. A spheroid is widest at its own equator, so putting the equator exactly where
	 * the clip happens means the bottom layer of the cloud is its widest layer and everything above
	 * it draws inward: flat base, dome on top, the shape a cumulus actually has. Centring the puffs
	 * above the plane instead makes the widest layer float a few blocks up and the clip cuts a
	 * narrower slice, which from the ground reads as a stepped ziggurat rather than a cloud.
	 */
	private record Puff(double x, double z, double radius, double halfHeight) {}

	public CloudField(Kingdom kingdom, int centerX, int centerZ, long seed) {
		this.seed = seed;
		this.baseY = CloudBlock.SETTLE_Y;
		this.topY = baseY + kingdom.thickness;

		RandomSource random = RandomSource.create(seed);
		this.puffs = new Puff[kingdom.puffs];

		// One puff is always centred and large: it is what the smaller ones fuse into, and without
		// it a low puff count scatters into separate islands rather than one cloud. It is also the
		// only one allowed full height, so the mass is thickest in the middle.
		puffs[0] = new Puff(centerX, centerZ, kingdom.radius * 0.55, kingdom.thickness);

		for (int i = 1; i < puffs.length; i++) {
			// Sampled on a disc, biased outward by the square root so the puffs spread over the
			// area rather than crowding the middle.
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = Math.sqrt(random.nextDouble()) * kingdom.radius * 0.62;

			puffs[i] = new Puff(
				centerX + Math.cos(angle) * distance,
				centerZ + Math.sin(angle) * distance,
				kingdom.radius * (0.24 + random.nextDouble() * 0.26),
				// Shorter than the core, and by a varying amount: the outer puffs are what give the
				// top its lumps and the rim its thin ragged skirts.
				kingdom.thickness * (0.35 + random.nextDouble() * 0.5));
		}
	}

	public int baseY() {
		return baseY;
	}

	public int topY() {
		return topY;
	}

	/** Whether cloud belongs at this position, before the isolation prune. */
	public boolean isCloud(int x, int y, int z) {
		if (y < baseY || y > topY) return false;
		return density(x, y, z) + (valueNoise(x, z) - 0.5) * EDGE_BITE >= SURFACE;
	}

	/**
	 * Whether cloud should actually be placed here: {@link #isCloud} plus at least one cloud
	 * neighbour.
	 *
	 * <p>A threshold against a noisy field leaves single blocks stranded a little way off the mass,
	 * hanging in open sky. Six extra field samples per block is a cheap price for a silhouette with
	 * no specks around its edge.
	 */
	public boolean isCloudPlaced(int x, int y, int z) {
		if (!isCloud(x, y, z)) return false;
		return isCloud(x + 1, y, z) || isCloud(x - 1, y, z)
			|| isCloud(x, y + 1, z) || isCloud(x, y - 1, z)
			|| isCloud(x, y, z + 1) || isCloud(x, y, z - 1);
	}

	/** Highest cloud block in this column, or {@link Integer#MIN_VALUE} if the column is open sky. */
	public int surfaceY(int x, int z) {
		for (int y = topY; y >= baseY; y--) {
			if (isCloudPlaced(x, y, z)) return y;
		}
		return Integer.MIN_VALUE;
	}

	/**
	 * The cloud top at ({@code x}, {@code z}) if the column carries at least {@code minDepth} blocks
	 * of unbroken cloud under it, and {@link Integer#MIN_VALUE} otherwise. Callers use it to keep
	 * ruins and mobs off the thin ragged skirts, where a tower footing would hang over open air.
	 */
	public int firmSurfaceY(int x, int z, int minDepth) {
		int surface = surfaceY(x, z);
		if (surface == Integer.MIN_VALUE) return Integer.MIN_VALUE;

		for (int depth = 1; depth < minDepth; depth++) {
			if (!isCloudPlaced(x, surface - depth, z)) return Integer.MIN_VALUE;
		}
		return surface;
	}

	private double density(int x, int y, int z) {
		double sum = 0;

		for (Puff puff : puffs) {
			double dx = (x - puff.x) / puff.radius;
			double dy = (y - baseY) / puff.halfHeight;
			double dz = (z - puff.z) / puff.radius;

			double distanceSq = dx * dx + dy * dy + dz * dz;
			if (distanceSq >= 1) continue;

			// Squared falloff rather than linear: it meets zero with zero slope, so two puffs merge
			// into a smooth saddle instead of a visible crease.
			double falloff = 1 - distanceSq;
			sum += falloff * falloff;
		}

		return sum;
	}

	/** Smoothed value noise on a grid of {@link #EDGE_CELL} blocks, in [0, 1]. */
	private double valueNoise(int x, int z) {
		int cellX = Math.floorDiv(x, EDGE_CELL);
		int cellZ = Math.floorDiv(z, EDGE_CELL);

		double fx = smoothstep((x - cellX * EDGE_CELL) / (double) EDGE_CELL);
		double fz = smoothstep((z - cellZ * EDGE_CELL) / (double) EDGE_CELL);

		double x0z0 = hash01(cellX, cellZ);
		double x1z0 = hash01(cellX + 1, cellZ);
		double x0z1 = hash01(cellX, cellZ + 1);
		double x1z1 = hash01(cellX + 1, cellZ + 1);

		return lerp(lerp(x0z0, x1z0, fx), lerp(x0z1, x1z1, fx), fz);
	}

	private static double smoothstep(double t) {
		return t * t * (3 - 2 * t);
	}

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}

	/** SplitMix64 over the cell coordinates and the structure seed, in [0, 1). */
	private double hash01(int cellX, int cellZ) {
		long h = seed + cellX * 0x9E3779B97F4A7C15L + cellZ * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 30;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 27;
		h *= 0x94D049BB133111EBL;
		h ^= h >>> 31;
		return (h >>> 11) * 0x1.0p-53;
	}
}
