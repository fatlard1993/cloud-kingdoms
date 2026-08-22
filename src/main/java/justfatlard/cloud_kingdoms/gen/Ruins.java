package justfatlard.cloud_kingdoms.gen;

import justfatlard.cloud_kingdoms.gen.Plan.Encounter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a kingdom's ruins by rule.
 *
 * <p>There are no templates here and no NBT on disk. A tower is a stack of eroded rings whose top
 * edge is chewed away column by column; a vault is a hollow box with a sealed chamber inside it; a
 * cairn is three or four blocks that used to be more. Every one of them comes out different because
 * the numbers that describe it are drawn fresh from the kingdom's seed, and the same seed always
 * draws the same one.
 *
 * <p><b>Erosion is the shared idiom.</b> Every wall is drawn whole and then holed, with the chance
 * of a block being dropped rising with height, because that is how a building actually fails: the
 * footings outlast the parapet. It is one rule, applied everywhere, and it is most of what makes
 * these read as ruins rather than as buildings with the roof left off.
 */
public final class Ruins {

	private Ruins() {}

	/** Cloud a ruin needs under it before it is allowed a footing, in blocks. */
	private static final int FOOTING_DEPTH = 4;

	/**
	 * Encounters the architecture places itself, and which the scattered garrison therefore skips: a
	 * giant belongs on its plinth, shulkers in the vault, axolotls in water rather than on a deck.
	 */
	private static final java.util.EnumSet<Encounter> STRUCTURALLY_PLACED =
		java.util.EnumSet.of(Encounter.GIANT, Encounter.SHULKER, Encounter.AXOLOTL);

	/** Percent of citadels whose giant is keeping a golden goose. */
	private static final int GOOSE_CHANCE = 20;

	/** Erosion never passes this, or the top of a tower stops reading as a tower at all. */
	private static final int MAX_EROSION = 82;

	public static Plan draw(Kingdom kingdom, CloudField field, int centerX, int centerZ, long seed) {
		Plan plan = new Plan();
		// Offset from the field's own seed so a kingdom's architecture is not correlated with the
		// shape of the cloud it stands on.
		RandomSource random = RandomSource.create(seed ^ 0x5EEDC10D5EEDC10DL);

		switch (kingdom) {
			case BANK -> cairn(plan, field, centerX, centerZ, random);
			case TARN -> tarn(plan, kingdom, field, centerX, centerZ, random);
			case SPIRE -> spire(plan, kingdom, field, centerX, centerZ, random);
			case CITADEL -> citadel(plan, kingdom, field, centerX, centerZ, random);
		}

		strongboxes(plan, kingdom, field, centerX, centerZ, random);
		garrison(plan, kingdom, field, centerX, centerZ, random);

		return plan;
	}

	/**
	 * Tops the kingdom up to its chest count.
	 *
	 * <p>Each tier's own drawing puts one chest where that tier's architecture wants it: at the foot
	 * of the cairn, inside the tower, sealed in the vault. The rest are sunk into the deck out in the
	 * open, which is what makes a citadel worth walking over rather than breaking into and leaving.
	 */
	private static void strongboxes(Plan plan, Kingdom kingdom, CloudField field, int centerX, int centerZ,
			RandomSource random) {
		int wanted = kingdom.chests(random);

		while (plan.chests().size() < wanted) {
			BlockPos spot = deckSpot(field, centerX, centerZ, kingdom.radius, random);
			if (spot == null) return;

			// One below the standable spot, so the lid finishes flush with the deck rather than
			// perched on it. Whatever is above it is cleared when the chest is written to the world.
			plan.chest(spot.below(), Direction.Plane.HORIZONTAL.getRandomDirection(random));
		}
	}

	// ---------------------------------------------------------------- bank

	/**
	 * A stack of stones and a chest half sunk into the cloud beside it. Deliberately almost nothing:
	 * the bank tier exists so that most clouds are a landing and a small find, which is what keeps a
	 * spire on the horizon worth flying to.
	 */
	private static void cairn(Plan plan, CloudField field, int centerX, int centerZ, RandomSource random) {
		BlockPos crest = deckSpot(field, centerX, centerZ, 8, random);
		if (crest == null) return;

		int height = 2 + random.nextInt(3);
		for (int i = 0; i < height; i++) {
			plan.set(crest.getX(), crest.getY() + i, crest.getZ(), Palette.rubble(random));
		}

		// A stub of an arch: two footings and whatever of the span has not come down yet.
		int span = 3 + random.nextInt(3);
		Direction run = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		BlockPos foot = crest.relative(run.getClockWise(), 3);
		for (int i = 0; i <= span; i++) {
			if (random.nextInt(100) < 35) continue;
			int lift = Math.min(i, span - i);
			plan.set(foot.relative(run, i).above(lift), Palette.skyMasonry(random));
		}

		plan.chest(crest.relative(run.getOpposite(), 2).below(), run);
	}

	// ---------------------------------------------------------------- tarn

	/**
	 * Ponds sunk into the deck, with axolotls in them.
	 *
	 * <p>Nothing is built here. The other tiers put something on the cloud; this one takes something
	 * out of it, and what it leaves behind leaks: {@code CloudBlock} drips any water it finds above
	 * it, so a tarn rains on the ground below and is the one kingdom you can spot without flying up.
	 *
	 * <p><b>A pond has to be level or it empties itself.</b> The deck top is billowy, so a bowl cut
	 * to a fixed height on a slope has an open side and the water pours off the edge of the world.
	 * Each site is therefore surveyed first: the rim is set to the <em>lowest</em> surface across the
	 * whole footprint, which guarantees undug cloud standing at or above the waterline the whole way
	 * round, and sites too uneven for that to leave a pond worth having are rejected outright.
	 */
	private static void tarn(Plan plan, Kingdom kingdom, CloudField field, int centerX, int centerZ,
			RandomSource random) {
		int ponds = 2 + random.nextInt(3);
		int axolotls = kingdom.countOf(Encounter.AXOLOTL, random);

		for (int i = 0; i < ponds; i++) {
			int radius = 3 + random.nextInt(4);
			BlockPos site = pondSite(field, centerX, centerZ, kingdom.radius, radius, random);
			if (site == null) continue;

			int rimY = site.getY();
			int depth = 2 + random.nextInt(2);

			for (int[] cell : disc(radius)) {
				int x = site.getX() + cell[0];
				int z = site.getZ() + cell[1];

				// Deeper in the middle, feathering to a single block at the lip, so the pond reads as
				// a bowl rather than a cylinder punched out of the cloud.
				double distance = Math.sqrt(cell[0] * cell[0] + cell[1] * cell[1]);
				int localDepth = Math.max(1, (int) Math.round(depth * (1.0 - distance / (radius + 1.0))));

				for (int down = 0; down < localDepth; down++) {
					plan.set(x, rimY - down, z, Blocks.WATER.defaultBlockState());
				}

				// The bowl is left with a cloud floor on purpose. A clay lining was the first thing
				// tried here and it looked better and broke the tier: the drip scan walks up a column
				// of cloud looking for water and stops at the first block that is neither, so a clay
				// pan under every pond meant a tarn that could not rain. Water sitting straight on
				// cloud is also the honest version of the fiction - it leaks because there is nothing
				// under it but cloud.

				// Take the lip off. The rim is the lowest surface across the footprint, so any column
				// that was higher still has cloud standing above the waterline, and left alone it roofs
				// the pond over. The survey tolerates two blocks of variation, so two is what clears.
				plan.set(x, rimY + 1, z, Blocks.AIR.defaultBlockState());
				plan.set(x, rimY + 2, z, Blocks.AIR.defaultBlockState());
			}

			for (int pad = 0; pad < random.nextInt(3); pad++) {
				int[] cell = disc(radius - 1).get(random.nextInt(Math.max(1, disc(radius - 1).size())));
				plan.set(site.getX() + cell[0], rimY + 1, site.getZ() + cell[1],
					Blocks.LILY_PAD.defaultBlockState());
			}

			// Spread the tier's axolotls across however many ponds it ended up with, so the last pond
			// is not always the empty one.
			int share = Math.max(1, axolotls / ponds);
			for (int a = 0; a < share && axolotls > 0; a++, axolotls--) {
				plan.spawn(new BlockPos(site.getX(), rimY, site.getZ()), Encounter.AXOLOTL);
			}
		}
	}

	/**
	 * A pond site: a centre whose whole footprint sits on cloud level enough to hold water, returned
	 * at the lowest surface height found across it. Null when nothing flat enough turned up.
	 */
	private static BlockPos pondSite(CloudField field, int centerX, int centerZ, int reach, int radius,
			RandomSource random) {
		for (int attempt = 0; attempt < 12; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = Math.sqrt(random.nextDouble()) * (reach - radius - 2);
			int x = centerX + (int) Math.round(Math.cos(angle) * distance);
			int z = centerZ + (int) Math.round(Math.sin(angle) * distance);

			int lowest = Integer.MAX_VALUE;
			int highest = Integer.MIN_VALUE;
			boolean solid = true;

			for (int[] cell : disc(radius + 1)) {
				// Enough cloud under the deepest part of the bowl that the pond has a floor rather
				// than a hole through to the sky.
				int top = field.firmSurfaceY(x + cell[0], z + cell[1], FOOTING_DEPTH + 3);
				if (top == Integer.MIN_VALUE) { solid = false; break; }
				lowest = Math.min(lowest, top);
				highest = Math.max(highest, top);
			}

			if (!solid) continue;
			if (highest - lowest > 2) continue;

			return new BlockPos(x, lowest, z);
		}
		return null;
	}

	// ---------------------------------------------------------------- spire

	/** A watchtower that has lost its top, and the arches of whatever stood around it. */
	private static void spire(Plan plan, Kingdom kingdom, CloudField field, int centerX, int centerZ,
			RandomSource random) {
		BlockPos base = deckSpot(field, centerX, centerZ, 10, random);
		if (base == null) return;

		int radius = 4;
		int height = 14 + random.nextInt(8);
		int baseY = base.getY();
		Direction doorway = Direction.Plane.HORIZONTAL.getRandomDirection(random);

		// Floor first, so the tower has something to stand on where the cloud top is uneven.
		for (int[] cell : disc(radius)) {
			plan.set(base.getX() + cell[0], baseY, base.getZ() + cell[1], Blocks.SMOOTH_QUARTZ.defaultBlockState());
		}

		List<int[]> wall = ring(radius);
		for (int[] cell : wall) {
			// Each column of the wall gives up somewhere different, which is what makes the rim
			// jagged rather than a clean cut at one height.
			int columnTop = baseY + height - random.nextInt(Math.max(2, height / 2));

			for (int y = baseY + 1; y <= columnTop; y++) {
				int erosion = Math.min(MAX_EROSION,
					kingdom.erosion + (70 * (y - baseY)) / Math.max(1, height));
				if (random.nextInt(100) < erosion) continue;

				plan.set(base.getX() + cell[0], y, base.getZ() + cell[1], Palette.skyMasonry(random));
			}
		}

		// Interior: hollowed above the floor so the shaft is climbable and the chest is reachable.
		for (int[] cell : disc(radius - 1)) {
			for (int y = baseY + 1; y <= baseY + height; y++) {
				plan.set(base.getX() + cell[0], y, base.getZ() + cell[1], Blocks.AIR.defaultBlockState());
			}
		}

		// Doorway, punched after the wall so it survives whatever the wall put there.
		for (int y = baseY + 1; y <= baseY + 2; y++) {
			for (int r = radius - 1; r <= radius; r++) {
				BlockPos gap = base.relative(doorway, r);
				plan.set(gap.getX(), y, gap.getZ(), Blocks.AIR.defaultBlockState());
			}
		}

		// Window slits, one course each, on the two axes the door is not on.
		for (int course : new int[] { baseY + 5, baseY + 10 }) {
			if (course > baseY + height - 2) continue;
			for (Direction facing : Direction.Plane.HORIZONTAL) {
				if (facing == doorway) continue;
				BlockPos slit = base.relative(facing, radius);
				plan.set(slit.getX(), course, slit.getZ(), Blocks.AIR.defaultBlockState());
				plan.set(slit.getX(), course + 1, slit.getZ(), Blocks.IRON_BARS.defaultBlockState());
			}
		}

		// A lantern on a chain down the middle of the shaft: the tower is lit from inside, so the
		// slits read as windows from the deck at night. The beam at the top is not decoration -
		// the interior pass cleared this column, and a chain with nothing over it pops the first
		// time anything updates it.
		plan.set(base.getX(), baseY + height, base.getZ(), Palette.skyMasonry(random));
		int lampLength = 2 + random.nextInt(3);
		for (int i = 1; i <= lampLength; i++) {
			plan.set(base.getX(), baseY + height - i, base.getZ(), Blocks.IRON_CHAIN.defaultBlockState());
		}
		plan.set(base.getX(), baseY + height - lampLength - 1, base.getZ(),
			Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));

		plan.chest(base.relative(doorway.getOpposite(), radius - 2).above(), doorway);

		// A vex spawner on the tower floor, in most spires but not all. Rolled rather than given
		// because a spawner is the most valuable thing a spire can hold, and a prize that is always
		// there is inventory rather than a find.
		if (random.nextInt(100) < 60) {
			plan.spawner(base.relative(doorway.getClockWise(), radius - 2).above(), EntityTypes.VEX);
		}

		brokenArches(plan, field, base.getX(), base.getZ(), radius + 5, 3 + random.nextInt(3), random);
	}

	// ---------------------------------------------------------------- citadel

	/** A ring of pillars, and in the middle of it a sealed vault built of the wrong material. */
	private static void citadel(Plan plan, Kingdom kingdom, CloudField field, int centerX, int centerZ,
			RandomSource random) {
		BlockPos base = deckSpot(field, centerX, centerZ, 12, random);
		if (base == null) return;

		int baseY = base.getY();

		// Outer ring of broken pillars: the courtyard wall, mostly gone.
		int pillars = 9 + random.nextInt(4);
		int ringRadius = 16 + random.nextInt(5);
		for (int i = 0; i < pillars; i++) {
			double angle = (Math.PI * 2 * i) / pillars;
			int x = base.getX() + (int) Math.round(Math.cos(angle) * ringRadius);
			int z = base.getZ() + (int) Math.round(Math.sin(angle) * ringRadius);

			int top = field.firmSurfaceY(x, z, FOOTING_DEPTH);
			if (top == Integer.MIN_VALUE) continue;

			int pillarHeight = 4 + random.nextInt(9);
			for (int y = 0; y < pillarHeight; y++) {
				int erosion = Math.min(MAX_EROSION, kingdom.erosion + (60 * y) / pillarHeight);
				if (random.nextInt(100) < erosion) continue;
				plan.set(x, top + y, z, Palette.endMasonry(random));
			}
			if (random.nextInt(3) == 0) {
				plan.set(x, top + pillarHeight, z, Blocks.END_ROD.defaultBlockState());
			}
		}

		// The vault: an eroded shell with an intact chamber inside it. The shell is meant to be
		// walked into; the chamber is meant to be broken into, and the shulkers are the reason
		// that is a decision rather than a formality.
		int outer = 5;
		int vaultHeight = 7;

		for (int x = -outer; x <= outer; x++) {
			for (int z = -outer; z <= outer; z++) {
				boolean shell = Math.abs(x) == outer || Math.abs(z) == outer;
				plan.set(base.getX() + x, baseY, base.getZ() + z, Palette.endMasonry(random));

				if (!shell) continue;
				for (int y = 1; y <= vaultHeight; y++) {
					int erosion = Math.min(MAX_EROSION, kingdom.erosion + (55 * y) / vaultHeight);
					if (random.nextInt(100) < erosion) continue;
					plan.set(base.getX() + x, baseY + y, base.getZ() + z, Palette.endMasonry(random));
				}
			}
		}

		for (int x = -outer + 1; x <= outer - 1; x++) {
			for (int z = -outer + 1; z <= outer - 1; z++) {
				for (int y = 1; y <= vaultHeight; y++) {
					plan.set(base.getX() + x, baseY + y, base.getZ() + z, Blocks.AIR.defaultBlockState());
				}
			}
		}

		int inner = 2;
		for (int x = -inner; x <= inner; x++) {
			for (int z = -inner; z <= inner; z++) {
				for (int y = 1; y <= 4; y++) {
					boolean face = Math.abs(x) == inner || Math.abs(z) == inner || y == 4;
					BlockState state = face
						? Blocks.PURPUR_BLOCK.defaultBlockState()
						: Blocks.AIR.defaultBlockState();
					plan.set(base.getX() + x, baseY + y, base.getZ() + z, state);
				}
			}
		}

		// Spawners in the vault's outer hall, between the shell a player walks into and the chamber
		// they have to break into. The breeze is the constant, because a citadel is a wind-scoured
		// place and one is what makes the hall hostile to stand in; the second is a coin toss.
		Direction hall = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		plan.spawner(base.relative(hall, inner + 1).above(), EntityTypes.BREEZE);

		if (random.nextInt(100) < 50) {
			plan.spawner(base.relative(hall.getOpposite(), inner + 1).above(), EntityTypes.VEX);
		}

		plan.chest(base.above(), Direction.NORTH);
		// Walked round the chamber rather than drawn at random: four random draws from four
		// directions collide often, and two shulkers sharing a block is one shulker's worth of
		// fight with two shulkers' worth of drops.
		Direction[] walls = Direction.Plane.HORIZONTAL.stream().toArray(Direction[]::new);
		int shulkers = kingdom.countOf(Encounter.SHULKER, random);
		for (int i = 0; i < shulkers; i++) {
			plan.spawn(base.above().relative(walls[i % walls.length]), Encounter.SHULKER);
		}

		// End rods on the vault corners, the one bit of it that still works.
		for (int dx = -outer; dx <= outer; dx += outer * 2) {
			for (int dz = -outer; dz <= outer; dz += outer * 2) {
				plan.set(base.getX() + dx, baseY + vaultHeight + 1, base.getZ() + dz,
					Blocks.END_ROD.defaultBlockState());
			}
		}

		// The colossus, on a plinth outside the vault so it is the first thing seen on approach.
		BlockPos plinth = deckSpot(field, base.getX() + ringRadius / 2, base.getZ() - ringRadius / 2, 10, random);
		if (plinth != null) {
			for (int x = -2; x <= 2; x++) {
				for (int z = -2; z <= 2; z++) {
					plan.set(plinth.getX() + x, plinth.getY(), plinth.getZ() + z,
						Blocks.PURPUR_BLOCK.defaultBlockState());
				}
			}
			int giants = kingdom.countOf(Encounter.GIANT, random);
			for (int i = 0; i < giants; i++) {
				plan.spawn(plinth.above(), Encounter.GIANT);
			}

			// Something is keeping a bird up here, and about one citadel in five will tell you so.
			// Tied to the giant rather than rolled on its own: the joke only lands if the thing
			// guarding the treasure and the thing laying it are found in the same place.
			if (giants > 0 && random.nextInt(100) < GOOSE_CHANCE) {
				plan.spawn(plinth.above().relative(
					Direction.Plane.HORIZONTAL.getRandomDirection(random), 3), Encounter.GOLDEN_GOOSE);
			}
		}

		brokenArches(plan, field, base.getX(), base.getZ(), ringRadius + 6, 4 + random.nextInt(4), random);
	}

	// ---------------------------------------------------------------- shared decoration

	/** Free-standing arcs of masonry, the last standing bits of whatever enclosed the site. */
	private static void brokenArches(Plan plan, CloudField field, int centerX, int centerZ, int radius,
			int count, RandomSource random) {
		for (int i = 0; i < count; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			int x = centerX + (int) Math.round(Math.cos(angle) * radius);
			int z = centerZ + (int) Math.round(Math.sin(angle) * radius);

			int top = field.firmSurfaceY(x, z, FOOTING_DEPTH);
			if (top == Integer.MIN_VALUE) continue;

			Direction run = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			int span = 4 + random.nextInt(4);
			for (int step = 0; step <= span; step++) {
				if (random.nextInt(100) < 30) continue;
				int lift = Math.min(step, span - step) + 1;
				BlockPos at = new BlockPos(x, top, z).relative(run, step).above(lift);
				plan.set(at, Palette.skyMasonry(random));
			}
		}
	}

	/**
	 * Scatters the tier's mobs over the deck, rolling each kind's count from {@link Kingdom}.
	 *
	 * <p>Giants and shulkers are skipped here because the architecture places them: a giant stands
	 * on its plinth and shulkers are sealed in the vault, and neither means anything dropped at a
	 * random spot on the deck. They still roll their counts from the same table, just in
	 * {@link #citadel}.
	 */
	private static void garrison(Plan plan, Kingdom kingdom, CloudField field, int centerX, int centerZ,
			RandomSource random) {
		for (Kingdom.Roll roll : kingdom.garrison()) {
			if (STRUCTURALLY_PLACED.contains(roll.encounter())) continue;

			place(plan, field, centerX, centerZ, kingdom.radius, roll.count(random), roll.encounter(), random);
		}
	}

	private static void place(Plan plan, CloudField field, int centerX, int centerZ, int radius, int count,
			Encounter encounter, RandomSource random) {
		for (int i = 0; i < count; i++) {
			BlockPos spot = deckSpot(field, centerX, centerZ, radius, random);
			// A block above the deck rather than on it, so a mob that lands where a structure block
			// also wants to be steps down onto the deck instead of generating inside masonry.
			if (spot != null) plan.spawn(spot.above(), encounter);
		}
	}

	// ---------------------------------------------------------------- geometry

	/**
	 * A standable spot on the cloud top within {@code radius} of the centre, or null if eight tries
	 * all landed on thin cloud or open sky. Returns the position <em>above</em> the surface: the
	 * first free block, which is where a mob stands and where a loose block sits.
	 */
	private static BlockPos deckSpot(CloudField field, int centerX, int centerZ, int radius,
			RandomSource random) {
		for (int attempt = 0; attempt < 8; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = Math.sqrt(random.nextDouble()) * radius;
			int x = centerX + (int) Math.round(Math.cos(angle) * distance);
			int z = centerZ + (int) Math.round(Math.sin(angle) * distance);

			int top = field.firmSurfaceY(x, z, FOOTING_DEPTH);
			if (top != Integer.MIN_VALUE) return new BlockPos(x, top + 1, z);
		}
		return null;
	}

	/** Every offset on a one-block-thick circle outline of this radius. */
	private static List<int[]> ring(int radius) {
		List<int[]> cells = new ArrayList<>();
		int outerSq = radius * radius;
		int innerSq = (radius - 1) * (radius - 1);

		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				int distanceSq = x * x + z * z;
				if (distanceSq <= outerSq && distanceSq > innerSq) cells.add(new int[] { x, z });
			}
		}
		return cells;
	}

	/** Every offset inside a filled circle of this radius. */
	private static List<int[]> disc(int radius) {
		List<int[]> cells = new ArrayList<>();
		int outerSq = radius * radius;

		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				if (x * x + z * z <= outerSq) cells.add(new int[] { x, z });
			}
		}
		return cells;
	}
}
