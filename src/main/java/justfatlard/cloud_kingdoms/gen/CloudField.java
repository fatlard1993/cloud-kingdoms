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
 * <p>Three deliberate distortions make it read as weather rather than as an asteroid:
 *
 * <ul>
 *   <li><b>The underside is cut flat</b> at {@link CloudBlock#SETTLE_Y}. Every puff is centred on
 *       that plane and everything below it is discarded, so the widest layer of the mass is its
 *       bottom one and the silhouette from the ground is a flat white shelf: the shape the client is
 *       already drawing at that altitude. The billowing is all on top, where only someone who flew
 *       up can see it.</li>
 *   <li><b>The edge is chewed by value noise</b> before the threshold test, so the outline is
 *       lobed and ragged instead of the smooth ellipse a bare sum of spheroids gives.</li>
 *   <li><b>The plan is quantised to {@link #CELL}-block cells</b>, which is what stops the whole
 *       thing reading as a boulder. A field sampled per block gives a smooth curved outline; the
 *       clouds the client draws overhead are axis-aligned rectangles, and a round kingdom parked
 *       among them announces itself as something else from a long way off. Sampling once per cell
 *       squares the edge to the same right angles the cloud layer already has.</li>
 * </ul>

 * <p><b>The quantisation is horizontal only.</b> The top keeps its full per-block relief, because
 * the deck is a place to stand: ruins need footings at different heights, ponds and lava basins
 * need a surface to be cut into, and a kingdom flattened to a slab would be truer to the client's
 * clouds and much worse to arrive on. Vanilla-shaped from underneath, somewhere to be on top.
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

	/**
	 * Block width of one cloud cell. The field is sampled once per cell rather than once per block,
	 * so a cell is cloud or sky as a whole and the outline comes out as axis-aligned rectangles.
	 *
	 * <p>Twelve because that is the client's own number: the vanilla cloud texture is drawn at
	 * twelve blocks to the texel, so a kingdom's edge steps at exactly the interval of the layer it
	 * is hiding in. A tier is then as many cells across as vanilla would spend on a cloud that size
	 * - about five for a bank, sixteen for a citadel - which is the whole reason the sizes still
	 * read differently after being squared off.
	 *
	 * <p>Sampling per cell also means every column inside a cell is identical, which is what makes
	 * the isolation prune this class used to run unnecessary: see {@link #isCloudPlaced}.
	 */
	private static final int CELL = 12;

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

	/**
	 * Whether cloud belongs at this position.
	 *
	 * <p>This used to test the six neighbours as well, and drop any block that had none: a threshold
	 * against a noisy field sampled per block strands the odd speck in open sky a little way off the
	 * mass. Cell quantisation removed the failure rather than the symptom. Every column inside a
	 * cell samples the field at the same point and so agrees exactly, and a cell is {@link #CELL}
	 * blocks wide, so any cloud block has at least one of its four horizontal neighbours inside its
	 * own cell and that neighbour is always cloud too. The test could no longer fail, and six field
	 * samples per block were being spent to prove it.
	 */
	public boolean isCloudPlaced(int x, int y, int z) {
		if (y < baseY || y > topY) return false;
		return density(x, y, z) + (valueNoise(x, z) - 0.5) * EDGE_BITE >= SURFACE;
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

	/**
	 * The centre of the cell a coordinate falls in, on a grid fixed to the world rather than to the
	 * structure. Sampling here instead of at the coordinate itself is the whole quantisation: every
	 * block in a cell asks the field the same question and gets the same answer, so the cell goes in
	 * or out as one square.
	 *
	 * <p>World-aligned deliberately. A grid centred on each kingdom would make every cloud
	 * symmetrical about its own middle and put a cell boundary in the same place relative to every
	 * ruin; hanging it off world coordinates means a kingdom sits wherever it happens to sit in the
	 * grid, the way the client's cloud texture does.
	 */
	private static double cellCentre(int v) {
		return Math.floorDiv(v, CELL) * (double) CELL + (CELL - 1) / 2.0;
	}

	private double density(int x, int y, int z) {
		double cellX = cellCentre(x);
		double cellZ = cellCentre(z);
		double sum = 0;

		for (Puff puff : puffs) {
			double dx = (cellX - puff.x) / puff.radius;
			// Not quantised: the vertical is per block, so the deck keeps its relief.
			double dy = (y - baseY) / puff.halfHeight;
			double dz = (cellZ - puff.z) / puff.radius;

			double distanceSq = dx * dx + dy * dy + dz * dz;
			if (distanceSq >= 1) continue;

			// Squared falloff rather than linear: it meets zero with zero slope, so two puffs merge
			// into a smooth saddle instead of a visible crease.
			double falloff = 1 - distanceSq;
			sum += falloff * falloff;
		}

		return sum;
	}

	/**
	 * Smoothed value noise on a grid of {@link #EDGE_CELL} blocks, in [0, 1].
	 *
	 * <p>Sampled at the cloud cell's centre like everything else, or the noise would vary across a
	 * cell that the density has already decided is one square and the edge would go back to being
	 * curved. {@link #EDGE_CELL} sitting one block off {@link #CELL} is what keeps this useful after
	 * quantisation: the two grids drift against each other rather than locking in step, so
	 * neighbouring cloud cells keep drawing different numbers and the outline stays ragged instead
	 * of settling into a rectangle.
	 */
	private double valueNoise(int x, int z) {
		double sampleX = cellCentre(x);
		double sampleZ = cellCentre(z);

		int cellX = (int) Math.floor(sampleX / EDGE_CELL);
		int cellZ = (int) Math.floor(sampleZ / EDGE_CELL);

		double fx = smoothstep((sampleX - cellX * (double) EDGE_CELL) / EDGE_CELL);
		double fz = smoothstep((sampleZ - cellZ * (double) EDGE_CELL) / EDGE_CELL);

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
