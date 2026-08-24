package justfatlard.cloud_kingdoms.gen;

import justfatlard.cloud_kingdoms.gen.Plan.Encounter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

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
	 * Cloud a bank needs under a cairn before it can hide a spawner cell there.
	 *
	 * <p>The cell hangs five blocks below the standable surface and is five tall, so its floor sits
	 * at the sixth and one more block of cloud under that keeps it from showing through the underside
	 * of the shelf, which anyone flying past below would read as a mistake.
	 *
	 * <p><b>Seven and not eight.</b> A bank is thirteen thick at the core and thins fast, and the
	 * search has eight attempts to find a column this deep inside a third of the radius. Measured
	 * over two hundred seeds, seven finds one every time and eight misses one bank in twenty; nine
	 * misses seven in ten. The margin is one block because that is what the geometry affords, not
	 * because one block is comfortable.
	 */
	private static final int CAGE_DEPTH = 7;

	/**
	 * Encounters the architecture places itself, and which the scattered garrison therefore skips: a
	 * giant belongs on its plinth, shulkers in the vault, axolotls and striders in the fluid their
	 * tier cut a basin for, frogs on the pads at its edge, and a piglin family at the farm it lives
	 * on rather than scattered over a deck it has no reason to be standing in the middle of.
	 */
	private static final java.util.EnumSet<Encounter> STRUCTURALLY_PLACED =
		java.util.EnumSet.of(Encounter.GIANT, Encounter.SHULKER, Encounter.AXOLOTL, Encounter.STRIDER,
			Encounter.FROG, Encounter.PIGLIN, Encounter.PIGLIN_CHILD, Encounter.PIGLIN_BRUTE);

	/** Percent of citadels whose giant is keeping a golden goose. */
	private static final int GOOSE_CHANCE = 20;

	/** Percent of banks with somebody's allay still in its hutch. */
	private static final int HUTCH_CHANCE = 25;

	/** Erosion never passes this, or the top of a tower stops reading as a tower at all. */
	private static final int MAX_EROSION = 82;

	/** Half-width of a forge's smithy hall, walls included. */
	private static final int SMITHY_HALF = 6;

	/** Height of the smithy's walls above its floor, before erosion takes the top off. */
	private static final int SMITHY_WALLS = 5;

	/**
	 * Percent of forges with a ruined portal somewhere on the deck.
	 *
	 * <p>A chance rather than a fixture, for the same reason the spire's spawner is one: the portal
	 * is the best thing a forge can hold, and a prize that is always there is inventory rather than
	 * a find. High enough that it is worth checking the deck of every forge, low enough that finding
	 * one is worth something.
	 */
	private static final int PORTAL_CHANCE = 40;

	/** Vanilla ships ten ordinary ruined portals, {@code portal_1} through {@code portal_10}. */
	private static final int PORTAL_VARIANTS = 10;

	/** Cloud a portal needs under it, so its footings are not hanging over open sky. */
	private static final int PORTAL_DEPTH = 6;

	/**
	 * How many times a bowl will look for somewhere level enough to hold its fluid.
	 *
	 * <p><b>Forty, and the number is load-bearing.</b> A basin site has to find a footprint that is
	 * both deep enough and level within two blocks, and most of a deck is neither. At the twelve
	 * tries this used to run, measured over a hundred and twenty tarns, <em>eighteen percent came
	 * out with no pond at all</em> - which is not a thinner tarn, it is a tarn with no water, no
	 * rain, no axolotls and no frogs on a tier that is nothing but those. Twenty-four tries cuts
	 * that to five percent and forty to none.
	 *
	 * <p>The cost is paid only by the sites that fail, since the search stops the moment one lands,
	 * and it is paid once per kingdom rather than once per chunk. That is the right trade for a tier
	 * that is otherwise empty one time in five.
	 */
	private static final int BASIN_ATTEMPTS = 40;

	public static Plan draw(Kingdom kingdom, CloudField field, int centerX, int centerZ, long seed) {
		Plan plan = new Plan();
		// Offset from the field's own seed so a kingdom's architecture is not correlated with the
		// shape of the cloud it stands on.
		RandomSource random = RandomSource.create(seed ^ 0x5EEDC10D5EEDC10DL);

		switch (kingdom) {
			case BANK -> shelf(plan, kingdom, field, centerX, centerZ, random);
			case TARN -> tarn(plan, kingdom, field, centerX, centerZ, random);
			case SPIRE -> spire(plan, kingdom, field, centerX, centerZ, random);
			case FORGE -> forge(plan, kingdom, field, centerX, centerZ, random);
			case HOMESTEAD -> homestead(plan, kingdom, field, centerX, centerZ, random);
			case CITADEL -> citadel(plan, kingdom, field, centerX, centerZ, random);
			case WRECK -> wreck(plan, kingdom, field, centerX, centerZ, random);
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
	 * Cairns scattered over an open shelf, and under most of them the reason to have walked out
	 * there.
	 *
	 * <p>Nothing is built on a bank, which is the tier's whole identity and the reason it cannot be
	 * given a tower to make it worth visiting. So the find is put <em>under</em> the deck instead,
	 * and the cairns are the map: a stack of stones on an otherwise empty shelf is the only thing
	 * telling anyone where to dig. Caches sit beneath a single block of cloud, so finding one is a
	 * swing of a shovel rather than a search, and one cairn in the middle has a sealed cell under it
	 * instead - which is a different afternoon entirely.
	 *
	 * <p><b>Better than half of them carry something.</b> Not all, because a marker that always pays
	 * is a checklist rather than a prospect; not a quarter, because a player who opens three empty
	 * holes stops checking and walks past the one that mattered.
	 */
	private static void shelf(Plan plan, Kingdom kingdom, CloudField field, int centerX, int centerZ,
			RandomSource random) {
		// The cell first, and near the middle: it needs more cloud under it than anything else on
		// this tier, and the middle is the only place a shelf is reliably that thick.
		BlockPos marker = deckSpot(field, centerX, centerZ, kingdom.radius / 3, random, CAGE_DEPTH);
		if (marker != null) {
			BlockPos cage = marker.below(5);
			spawnerCell(plan, cage);
			plan.spawner(cage, EntityTypes.BREEZE);
			// A taller cairn than the rest, because it is the one worth telling apart.
			cairn(plan, marker, 4 + random.nextInt(3), random);
		}

		int cairns = 3 + random.nextInt(3);
		for (int i = 0; i < cairns; i++) {
			BlockPos crest = deckSpot(field, centerX, centerZ, kingdom.radius - 8, random);
			if (crest == null) continue;

			cairn(plan, crest, 2 + random.nextInt(3), random);

			// A cairn over the cell is fine and reads as a second marker. A cache over it is not:
			// the cache sits three down, which is inside the cell, and the lid the piece clears above
			// every chest would take the cell's roof off with it. A sealed cell with a skylight in it
			// is a breeze spawner that never runs, and nothing about the deck would show why.
			if (overCage(crest, marker)) continue;

			if (random.nextInt(100) < 55) {
				// Three down, so the lid clearing in the piece leaves one block of deck over it.
				plan.chest(crest.below(3), Direction.Plane.HORIZONTAL.getRandomDirection(random));
			}
		}

		if (random.nextInt(100) < HUTCH_CHANCE) {
			BlockPos held = deckSpot(field, centerX, centerZ, kingdom.radius - 6, random);
			// Not on top of the buried cell, whose taller cairn is the only thing on the deck saying
			// what is under it. A hutch landing there overwrites that marker, and the cell stops
			// being a find and becomes a room nobody has a reason to dig for.
			if (held != null && !overCage(held, marker)) hutch(plan, held, random);
		}

		brokenArches(plan, field, centerX, centerZ, kingdom.radius / 2, 3 + random.nextInt(4), random,
			Palette::skyMasonry);
	}

	/**
	 * An allay in a barred box on the deck, sometimes, and no explanation for either.
	 *
	 * <p>The bank is the tier where things are found rather than built, and this is the only find on
	 * it that is alive. Somebody carried a hutch up here and left it, and the mod says nothing more
	 * about who: a cairn marks a cache, and this marks a question.
	 *
	 * <p><b>The bars are given their connections by hand.</b> Everything here is written with
	 * {@code UPDATE_CLIENTS}, which does not run the neighbour updates a bar uses to work out what it
	 * is touching. Left at their default state the whole thing renders as eight loose posts standing
	 * near each other - which is exactly why the homestead's garden got stems rather than a fence. A
	 * ring this small can simply be told what it is touching, so it is.
	 *
	 * <p>Called a hutch and not a cage because this file already has cages: the sealed cells the
	 * spawners sit in. Two different things under one word in one file is how the wrong one gets
	 * edited.
	 */
	private static void hutch(Plan plan, BlockPos floor, RandomSource random) {
		int baseY = floor.getY() - 1;

		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				plan.set(floor.getX() + dx, baseY, floor.getZ() + dz, Palette.rubble(random));
				if (dx == 0 && dz == 0) continue;

				BlockState bars = Blocks.IRON_BARS.defaultBlockState()
					.setValue(IronBarsBlock.NORTH, barAt(dx, dz - 1))
					.setValue(IronBarsBlock.SOUTH, barAt(dx, dz + 1))
					.setValue(IronBarsBlock.WEST, barAt(dx - 1, dz))
					.setValue(IronBarsBlock.EAST, barAt(dx + 1, dz));

				for (int y = 1; y <= 2; y++) {
					plan.set(floor.getX() + dx, baseY + y, floor.getZ() + dz, bars);
				}
			}
		}

		// A solid lid rather than more bars. The sides are what you look through, and on a deck lit
		// from straight overhead a barred roof mostly just shades what is inside it.
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				plan.set(floor.getX() + dx, baseY + 3, floor.getZ() + dz,
					Blocks.SMOOTH_QUARTZ.defaultBlockState());
			}
		}

		plan.spawn(new BlockPos(floor.getX(), baseY + 1, floor.getZ()), Encounter.ALLAY);
	}

	/** Whether this offset is one of the hutch's eight bars, for working out what connects to what. */
	private static boolean barAt(int dx, int dz) {
		return Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && !(dx == 0 && dz == 0);
	}

	/**
	 * Whether this spot sits over the buried cell, with a block of margin on the cell's five-wide
	 * footprint. Always false when the cell never found deep enough cloud to be placed.
	 */
	private static boolean overCage(BlockPos spot, BlockPos marker) {
		if (marker == null) return false;
		return Math.abs(spot.getX() - marker.getX()) <= 3 && Math.abs(spot.getZ() - marker.getZ()) <= 3;
	}

	/** A stack of stones, all mineral: a cairn of cloud on a cloud deck is a cairn nobody can see. */
	private static void cairn(Plan plan, BlockPos crest, int height, RandomSource random) {
		for (int i = 0; i < height; i++) {
			plan.set(crest.getX(), crest.getY() + i, crest.getZ(), Palette.rubble(random));
		}
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
		int frogs = kingdom.countOf(Encounter.FROG, random);

		for (int i = 0; i < ponds; i++) {
			int radius = 3 + random.nextInt(4);
			BlockPos site = basinSite(field, centerX, centerZ, kingdom.radius, radius, random);
			if (site == null) continue;

			int rimY = site.getY();
			int depth = 2 + random.nextInt(2);

			bowl(plan, site, radius, depth, Blocks.WATER.defaultBlockState());

			for (int pad = 0; pad < random.nextInt(3); pad++) {
				int[] cell = disc(radius - 1).get(random.nextInt(Math.max(1, disc(radius - 1).size())));
				plan.set(site.getX() + cell[0], rimY + 1, site.getZ() + cell[1],
					Blocks.LILY_PAD.defaultBlockState());
			}

			// Frogs on the lip rather than in the water: they are the one thing here that belongs to
			// both the pond and the deck, and a frog put in the middle of a bowl just swims.
			int frogShare = Math.max(1, frogs / ponds);
			for (int f = 0; f < frogShare && frogs > 0; f++, frogs--) {
				int[] edge = ring(radius).get(random.nextInt(Math.max(1, ring(radius).size())));
				plan.spawn(new BlockPos(site.getX() + edge[0], rimY + 1, site.getZ() + edge[1]),
					Encounter.FROG);
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
	 * Cuts a bowl into the deck and fills it, for the two tiers that take something out of their
	 * cloud rather than putting something on it.
	 *
	 * <p>The bowl is left with a <b>cloud floor</b> on purpose. A lining - clay under a pond, basalt
	 * under a basin - was the first thing tried and it looked better and broke the tier: the leak
	 * scan in {@code CloudBlock} walks up a column of cloud looking for fluid and stops at the first
	 * block that is neither, so a pan under every bowl means a tarn that cannot rain and a forge that
	 * cannot smoke. Fluid sitting straight on cloud is also the honest version of the fiction - it
	 * leaks because there is nothing under it but cloud.
	 */
	private static void bowl(Plan plan, BlockPos site, int radius, int depth, BlockState fluid) {
		int rimY = site.getY();

		for (int[] cell : disc(radius)) {
			int x = site.getX() + cell[0];
			int z = site.getZ() + cell[1];

			// Deeper in the middle, feathering to a single block at the lip, so it reads as a bowl
			// rather than a cylinder punched out of the cloud.
			double distance = Math.sqrt(cell[0] * cell[0] + cell[1] * cell[1]);
			int localDepth = Math.max(1, (int) Math.round(depth * (1.0 - distance / (radius + 1.0))));

			for (int down = 0; down < localDepth; down++) {
				plan.set(x, rimY - down, z, fluid);
			}

			// Take the lip off. The rim is the lowest surface across the footprint, so any column that
			// was higher still has cloud standing above the fluid line, and left alone it roofs the
			// bowl over. The survey tolerates two blocks of variation, so two is what clears.
			plan.set(x, rimY + 1, z, Blocks.AIR.defaultBlockState());
			plan.set(x, rimY + 2, z, Blocks.AIR.defaultBlockState());
		}
	}

	/**
	 * A basin site: a centre whose whole footprint sits on cloud level enough to hold a fluid,
	 * returned at the lowest surface height found across it. Null when nothing flat enough turned up.
	 */
	private static BlockPos basinSite(CloudField field, int centerX, int centerZ, int reach, int radius,
			RandomSource random) {
		for (int attempt = 0; attempt < BASIN_ATTEMPTS; attempt++) {
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
			BlockPos vexCage = base.relative(doorway.getClockWise(), radius - 2).above();
			spawnerCell(plan, vexCage);
			plan.spawner(vexCage, EntityTypes.VEX);
		}

		brokenArches(plan, field, base.getX(), base.getZ(), radius + 5, 3 + random.nextInt(3), random,
			Palette::skyMasonry);
	}

	// ---------------------------------------------------------------- forge

	/**
	 * A smithy with its fires still in, and lava basins cut into the deck around it.
	 *
	 * <p>This is the tarn's method turned over. Both tiers cut bowls into their own cloud and let
	 * what sits in them leak out of the underside; a tarn rains and a forge smokes, and the pair of
	 * them are the only kingdoms that announce themselves from below. What separates them is that a
	 * tarn is a landscape and this is a workshop: the bowls out on the deck are the spill, and the
	 * thing worth walking to is the hall in the middle of them.
	 *
	 * <p>The hall's plan is a shop floor rather than a ruin's footprint. The hearth and the anvil
	 * face each other across the door end, and the back third is a sealed blackstone room with a
	 * blaze spawner in it - the same idea as the citadel's vault chamber, at a smithy's scale. The
	 * player walks into a room that is obviously missing its back wall, and what is behind it is
	 * lit by nothing.
	 */
	private static void forge(Plan plan, Kingdom kingdom, CloudField field, int centerX, int centerZ,
			RandomSource random) {
		BlockPos base = deckSpot(field, centerX, centerZ, 10, random);
		if (base == null) return;

		int baseY = base.getY();
		Direction doorway = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		Direction across = doorway.getClockWise();

		// Floor first, for the same reason the spire has one: the deck top is billowy and a hall
		// drawn straight onto it stands on open air at a corner.
		for (int u = -SMITHY_HALF; u <= SMITHY_HALF; u++) {
			for (int v = -SMITHY_HALF; v <= SMITHY_HALF; v++) {
				BlockPos floor = shop(base, doorway, across, u, v);
				plan.set(floor.getX(), baseY, floor.getZ(), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
			}
		}

		// Walls, eroded upward like every other wall in this file.
		for (int u = -SMITHY_HALF; u <= SMITHY_HALF; u++) {
			for (int v = -SMITHY_HALF; v <= SMITHY_HALF; v++) {
				if (Math.abs(u) != SMITHY_HALF && Math.abs(v) != SMITHY_HALF) continue;

				BlockPos column = shop(base, doorway, across, u, v);
				for (int y = 1; y <= SMITHY_WALLS; y++) {
					int erosion = Math.min(MAX_EROSION, kingdom.erosion + (60 * y) / SMITHY_WALLS);
					if (random.nextInt(100) < erosion) continue;

					plan.set(column.getX(), baseY + y, column.getZ(), Palette.forgeMasonry(random));
				}
			}
		}

		// Hollow the inside, then punch the door through whatever the wall left standing in it.
		for (int u = -SMITHY_HALF + 1; u <= SMITHY_HALF - 1; u++) {
			for (int v = -SMITHY_HALF + 1; v <= SMITHY_HALF - 1; v++) {
				BlockPos column = shop(base, doorway, across, u, v);
				for (int y = 1; y <= SMITHY_WALLS + 1; y++) {
					plan.set(column.getX(), baseY + y, column.getZ(), Blocks.AIR.defaultBlockState());
				}
			}
		}
		for (int v = -1; v <= 1; v++) {
			BlockPos gap = shop(base, doorway, across, SMITHY_HALF, v);
			for (int y = 1; y <= 3; y++) {
				plan.set(gap.getX(), baseY + y, gap.getZ(), Blocks.AIR.defaultBlockState());
			}
		}

		hearth(plan, shop(base, doorway, across, 3, -3), baseY, across);

		// The anvil, across the floor from the fire. Damaged, because everything else up here is.
		BlockPos anvil = shop(base, doorway, across, 3, 3);
		plan.set(anvil.getX(), baseY + 1, anvil.getZ(), Blocks.DAMAGED_ANVIL.defaultBlockState());
		plan.chest(anvil.relative(doorway.getOpposite()).above(), doorway);

		// The back room. Written after the hollow pass, so the cell survives it.
		BlockPos cage = shop(base, doorway, across, -3, 0).above(2);
		spawnerCell(plan, cage, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
		plan.spawner(cage, EntityTypes.BLAZE);

		basins(plan, kingdom, field, centerX, centerZ, base, random);
		if (random.nextInt(100) < PORTAL_CHANCE) {
			portal(plan, field, centerX, centerZ, kingdom.radius - 14, base, SMITHY_HALF + 10, random);
		}
		brokenArches(plan, field, base.getX(), base.getZ(), SMITHY_HALF + 6, 3 + random.nextInt(4), random,
			Palette::forgeMasonry);
	}

	/**
	 * A position on the smithy's floor plan, in door-relative coordinates: {@code u} runs toward the
	 * doorway, {@code v} runs across it.
	 *
	 * <p>The hall is drawn in its own frame rather than in world axes so the layout is written once
	 * and holds whichever of the four ways the door came out facing. Drawn in world axes, "the anvil
	 * is across the floor from the fire" is true for one door in four.
	 */
	private static BlockPos shop(BlockPos base, Direction doorway, Direction across, int u, int v) {
		return base.relative(doorway, u).relative(across, v);
	}

	/**
	 * A pan of lava sunk into the smithy floor, ringed with magma.
	 *
	 * <p>Sunk rather than raised, because the lava has to sit on cloud to be seen from the ground:
	 * the leak scan reaches up through unbroken cloud and stops at the first block that is not, so a
	 * hearth built up on a blackstone plinth is a hearth nobody below ever knows about.
	 *
	 * <p>The magma ring is the warning. It is the one block on a cloud deck that hurts to stand on,
	 * and it is placed where a player heading for the anvil will step on it before they fall in the
	 * fire rather than after.
	 */
	private static void hearth(Plan plan, BlockPos centre, int baseY, Direction across) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				boolean pan = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
				plan.set(centre.getX() + dx, baseY, centre.getZ() + dz, pan
					? Blocks.LAVA.defaultBlockState()
					: Blocks.MAGMA_BLOCK.defaultBlockState());
			}
		}

		// Soul lanterns on the two ring corners facing into the shop, so the light falls on the floor
		// a player crosses rather than into the wall behind the fire. The smithy is lit blue from
		// inside, which is what makes the doorway read as a doorway on a deck where everything else
		// is white.
		BlockPos lip = centre.relative(across, 2);
		for (int step = -2; step <= 2; step += 4) {
			BlockPos corner = lip.relative(across.getClockWise(), step);
			plan.set(corner.getX(), baseY + 1, corner.getZ(), Blocks.SOUL_LANTERN.defaultBlockState());
		}
	}

	/**
	 * Sometimes, out on the deck, the hole the Nether came through.
	 *
	 * <p>Vanilla ships thirteen ruined portals and this uses ten of them, whole, rather than
	 * drawing one: they already have the broken arch, the scattered netherrack, the gold blocks and
	 * a chest with {@code minecraft:chests/ruined_portal} written into the template file, so a stamp
	 * is the entire implementation. The loot is not even wired up here - it is in the NBT.
	 *
	 * <p>They come through vanilla's <b>blackstone</b> conversion, which is the same switch its own
	 * nether-biome portals use. That is not decoration: the forge's whole fiction is a smithy the
	 * Nether burnt up through, and a portal in the local stone reads as the hole it came through
	 * rather than as something unrelated that happens to share the cloud.
	 *
	 * <p>Set a block or two into the deck, because a ruined portal that sits flat on the surface
	 * reads as a model of one. Its own air blocks cut the shallow pit around the footings.
	 */
	private static void portal(Plan plan, CloudField field, int centerX, int centerZ, int reach,
			BlockPos keepClearOf, int clearance, RandomSource random) {
		for (int attempt = 0; attempt < 8; attempt++) {
			BlockPos spot = deckSpot(field, centerX, centerZ, reach, random, PORTAL_DEPTH);
			if (spot == null) return;

			// Clear of whatever else the tier built, the way the basins are. A portal through the
			// middle of a hall is not a second find, it is two structures fighting over blocks.
			if (Math.abs(spot.getX() - keepClearOf.getX()) < clearance
				&& Math.abs(spot.getZ() - keepClearOf.getZ()) < clearance) continue;

			Identifier which = Identifier.withDefaultNamespace(
				"ruined_portal/portal_" + (1 + random.nextInt(PORTAL_VARIANTS)));

			// Full integrity: it is already a ruin, and rotting a ruin twice just deletes it.
			plan.template(which, spot.below(1 + random.nextInt(2)), Rotation.getRandom(random),
				1.0F, Plan.Dressing.NETHER);
			return;
		}
	}

	/**
	 * The spill: bowls of lava cut into the open deck, with whatever is wading in them.
	 *
	 * <p>Kept clear of the smithy by hand. A tarn has nothing built on it and can drop a pond
	 * anywhere its survey allows; a forge would happily cut one through its own hall, and a hall with
	 * a lava bowl bitten out of the middle of it is not a ruin, it is a bug.
	 */
	private static void basins(Plan plan, Kingdom kingdom, CloudField field, int centerX, int centerZ,
			BlockPos smithy, RandomSource random) {
		int basins = 2 + random.nextInt(3);
		int striders = kingdom.countOf(Encounter.STRIDER, random);

		for (int i = 0; i < basins; i++) {
			int radius = 3 + random.nextInt(3);
			BlockPos site = basinSite(field, centerX, centerZ, kingdom.radius, radius, random);
			if (site == null) continue;

			int clearance = SMITHY_HALF + radius + 3;
			if (Math.abs(site.getX() - smithy.getX()) < clearance
				&& Math.abs(site.getZ() - smithy.getZ()) < clearance) continue;

			bowl(plan, site, radius, 2 + random.nextInt(2), Blocks.LAVA.defaultBlockState());

			// Spread the tier's striders across however many basins it ended up with, the same way a
			// tarn spreads its axolotls, so the last one is not always the empty one.
			int share = Math.max(1, striders / basins);
			for (int s = 0; s < share && striders > 0; s++, striders--) {
				plan.spawn(new BlockPos(site.getX(), site.getY(), site.getZ()), Encounter.STRIDER);
			}
		}
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
		BlockPos breezeCage = base.relative(hall, inner + 1).above();
		spawnerCell(plan, breezeCage);
		plan.spawner(breezeCage, EntityTypes.BREEZE);

		if (random.nextInt(100) < 50) {
			BlockPos hallVexCage = base.relative(hall.getOpposite(), inner + 1).above();
			spawnerCell(plan, hallVexCage);
			plan.spawner(hallVexCage, EntityTypes.VEX);
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

		brokenArches(plan, field, base.getX(), base.getZ(), ringRadius + 6, 4 + random.nextInt(4), random,
			Palette::skyMasonry);
	}

	// ---------------------------------------------------------------- homestead

	/** Half-width of the homestead's hut, walls included. */
	private static final int HUT_HALF = 4;

	/** Height of the hut's walls above its floor. */
	private static final int HUT_WALLS = 4;

	/**
	 * A piglin family's holding: a hut, a farm, and the broken portal they arrived through.
	 *
	 * <p>Everything else in this file is drawn as something that failed, and the shared erosion rule
	 * is what makes that read. Here it is turned almost all the way down, because the tier's whole
	 * point is that nothing failed: the walls stand because a household is keeping them standing, and
	 * a homestead with a caved-in roof would be telling the player the opposite of the truth.
	 *
	 * <p>The three parts are the story in order. The <b>portal</b> is how they got here and it is
	 * broken, which on this tier is not decay - a ruined portal siting itself away from the house
	 * with a farm between them says they arrived, walked off, and did not go back. The <b>hut</b> is
	 * built out of timber they carried through rather than out of the cloud, unlike every other
	 * structure in the mod. The <b>farm</b> is nether wart and fungus, because that is what they
	 * know how to grow, planted in soil they must have brought up a bucket at a time.
	 */
	private static void homestead(Plan plan, Kingdom kingdom, CloudField field, int centerX, int centerZ,
			RandomSource random) {
		BlockPos base = deckSpot(field, centerX, centerZ, 10, random);
		if (base == null) return;

		Direction doorway = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		Direction across = doorway.getClockWise();

		hut(plan, kingdom, base, doorway, across, random);
		farm(plan, field, base, doorway, across, random);

		// Always, and never near the house. It is the reason the tier exists.
		portal(plan, field, centerX, centerZ, kingdom.radius - 16, base, HUT_HALF + 14, random);

		family(plan, field, kingdom, base, doorway, across, centerX, centerZ, random);
	}

	/** A one-room house with a door, a light, a workbench and the household's gold in the corner. */
	private static void hut(Plan plan, Kingdom kingdom, BlockPos base, Direction doorway, Direction across,
			RandomSource random) {
		int baseY = base.getY();

		for (int u = -HUT_HALF; u <= HUT_HALF; u++) {
			for (int v = -HUT_HALF; v <= HUT_HALF; v++) {
				BlockPos at = shop(base, doorway, across, u, v);
				plan.set(at.getX(), baseY, at.getZ(), Blocks.POLISHED_BLACKSTONE.defaultBlockState());

				boolean wall = Math.abs(u) == HUT_HALF || Math.abs(v) == HUT_HALF;
				for (int y = 1; y <= HUT_WALLS; y++) {
					if (wall) {
						// Erosion is almost nothing on this tier, so this reads as a wall with the odd
						// board missing rather than as a ruin.
						if (random.nextInt(100) < kingdom.erosion) continue;
						plan.set(at.getX(), baseY + y, at.getZ(), Palette.homestead(random));
					} else if (y < HUT_WALLS) {
						plan.set(at.getX(), baseY + y, at.getZ(), Blocks.AIR.defaultBlockState());
					}
				}

				// A flat roof, whole: the one intact roof in the mod.
				plan.set(at.getX(), baseY + HUT_WALLS, at.getZ(), Blocks.CRIMSON_PLANKS.defaultBlockState());
			}
		}

		for (int y = 1; y <= 2; y++) {
			BlockPos door = shop(base, doorway, across, HUT_HALF, 0);
			plan.set(door.getX(), baseY + y, door.getZ(), Blocks.AIR.defaultBlockState());
		}

		BlockPos lamp = shop(base, doorway, across, 0, 0);
		plan.set(lamp.getX(), baseY + HUT_WALLS - 1, lamp.getZ(),
			Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));

		BlockPos bench = shop(base, doorway, across, -HUT_HALF + 1, HUT_HALF - 1);
		plan.set(bench.getX(), baseY + 1, bench.getZ(), Blocks.CRAFTING_TABLE.defaultBlockState());

		// The household's gold, indoors and in plain sight. Breaking it is what turns a trade into a
		// fight, and the mod does nothing to arrange that - it is what piglins already do.
		BlockPos hoard = shop(base, doorway, across, -HUT_HALF + 1, -HUT_HALF + 1);
		plan.set(hoard.getX(), baseY + 1, hoard.getZ(), Blocks.GOLD_BLOCK.defaultBlockState());
		plan.set(hoard.getX(), baseY + 2, hoard.getZ(), Blocks.GILDED_BLACKSTONE.defaultBlockState());

		plan.chest(shop(base, doorway, across, -HUT_HALF + 1, 0).above(), doorway);
	}

	/**
	 * The farm: wart beds and a fungus patch, edged in crimson stems.
	 *
	 * <p>Soul sand rather than soul soil, because nether wart will only sit on the one and this is
	 * meant to be a working bed rather than a decorative one. Somebody carried every block of it up.
	 */
	private static void farm(Plan plan, CloudField field, BlockPos base, Direction doorway, Direction across,
			RandomSource random) {
		int baseY = base.getY();
		int beds = 2 + random.nextInt(2);

		for (int bed = 0; bed < beds; bed++) {
			int offU = -(HUT_HALF + 3 + bed * 4);
			int span = 2 + random.nextInt(2);

			for (int u = offU; u > offU - 3; u--) {
				for (int v = -span; v <= span; v++) {
					BlockPos at = shop(base, doorway, across, u, v);
					int top = field.firmSurfaceY(at.getX(), at.getZ(), FOOTING_DEPTH);
					if (top == Integer.MIN_VALUE) continue;

					plan.set(at.getX(), top, at.getZ(), Blocks.SOUL_SAND.defaultBlockState());
					if (random.nextInt(100) < 80) {
						plan.set(at.getX(), top + 1, at.getZ(), Blocks.NETHER_WART.defaultBlockState()
							.setValue(NetherWartBlock.AGE, random.nextInt(4)));
					}
				}
			}
		}

		// A fungus patch beside the beds, on the nylium it needs to spread on.
		for (int i = 0; i < 6 + random.nextInt(6); i++) {
			int u = -(HUT_HALF + 2) - random.nextInt(10);
			int v = (HUT_HALF + 1) + random.nextInt(5);
			BlockPos at = shop(base, doorway, across, u, v);
			int top = field.firmSurfaceY(at.getX(), at.getZ(), FOOTING_DEPTH);
			if (top == Integer.MIN_VALUE) continue;

			boolean crimson = random.nextBoolean();
			plan.set(at.getX(), top, at.getZ(), crimson
				? Blocks.CRIMSON_NYLIUM.defaultBlockState()
				: Blocks.WARPED_NYLIUM.defaultBlockState());
			plan.set(at.getX(), top + 1, at.getZ(), switch (random.nextInt(3)) {
				case 0 -> crimson ? Blocks.CRIMSON_FUNGUS.defaultBlockState()
					: Blocks.WARPED_FUNGUS.defaultBlockState();
				case 1 -> crimson ? Blocks.CRIMSON_ROOTS.defaultBlockState()
					: Blocks.WARPED_ROOTS.defaultBlockState();
				default -> Blocks.AIR.defaultBlockState();
			});
		}

		// Stems marking the row ends, which is the only thing here saying where the farm stops. Not a
		// fence: a fence placed without neighbour updates renders as unconnected posts, and a garden
		// fenced in broken fencing says nobody is keeping this place up.
		for (int v : new int[] { -4, 4 }) {
			for (int u = -(HUT_HALF + 2); u > -(HUT_HALF + 14); u -= 4) {
				BlockPos at = shop(base, doorway, across, u, v);
				int top = field.firmSurfaceY(at.getX(), at.getZ(), FOOTING_DEPTH);
				if (top == Integer.MIN_VALUE) continue;
				plan.set(at.getX(), top + 1, at.getZ(), Blocks.CRIMSON_STEM.defaultBlockState());
			}
		}
	}

	/** The household, put where a household would be: in the house and out among the beds. */
	private static void family(Plan plan, CloudField field, Kingdom kingdom, BlockPos base,
			Direction doorway, Direction across, int centerX, int centerZ, RandomSource random) {
		int adults = kingdom.countOf(Encounter.PIGLIN, random);
		int children = kingdom.countOf(Encounter.PIGLIN_CHILD, random);
		int brutes = kingdom.countOf(Encounter.PIGLIN_BRUTE, random);

		for (int i = 0; i < adults; i++) {
			// Out among the rows, where the work is.
			BlockPos at = shop(base, doorway, across, -(HUT_HALF + 2) - random.nextInt(12),
				random.nextInt(9) - 4);
			int top = field.firmSurfaceY(at.getX(), at.getZ(), FOOTING_DEPTH);
			if (top != Integer.MIN_VALUE) plan.spawn(new BlockPos(at.getX(), top + 1, at.getZ()), Encounter.PIGLIN);
		}

		for (int i = 0; i < children; i++) {
			BlockPos at = shop(base, doorway, across, random.nextInt(5) - 2, random.nextInt(5) - 2);
			plan.spawn(at.above(), Encounter.PIGLIN_CHILD);
		}

		// The brute at the door, which is where a household would put one.
		for (int i = 0; i < brutes; i++) {
			plan.spawn(shop(base, doorway, across, HUT_HALF + 1, 0).above(), Encounter.PIGLIN_BRUTE);
		}
	}

	// ---------------------------------------------------------------- wreck

	/** Vanilla's End ship. The mod draws every other ruin itself; this one the game already ships. */
	private static final Identifier END_SHIP =
		Identifier.withDefaultNamespace("end_city/ship");

	/**
	 * Which way the ship points before it is rotated. The dragon head sits at the template's
	 * {@code z = 0} face, so an unrotated ship has its bow toward north.
	 */
	private static final Direction SHIP_BOW = Direction.NORTH;

	/**
	 * An End ship that came down on a cloud.
	 *
	 * <p><b>The hull is not drawn here.</b> Vanilla ships {@code end_city/ship} as a structure
	 * template, with the dragon head, the ladder, the brewing stand of healing potions, the two
	 * treasure chests, the three shulkers and the elytra all where Mojang put them. Re-authoring
	 * that by hand would be a worse ship, a lot of code, and a promise to keep it matching a vanilla
	 * asset forever. So the tier stamps the real one.
	 *
	 * <p>What this mod adds is the crash, which vanilla has no notion of:
	 *
	 * <ul>
	 *   <li><b>It is buried.</b> The keel is set below the deck surface, so the ship sits in the
	 *       cloud rather than on it. The template's own air blocks do the excavating - there is no
	 *       {@code structure_void} anywhere in it, so every block of its box is written and the hull
	 *       arrives in a hole exactly its own shape.</li>
	 *   <li><b>It is broken.</b> A {@link BlockRotProcessor} drops a share of the hull, which is the
	 *       same idea as the erosion every other ruin in this file gets, borrowed rather than
	 *       reimplemented.</li>
	 *   <li><b>It ploughed to get here.</b> The furrow is the part that says crash rather than
	 *       parked, and it is the only part of the tier drawn by rule.</li>
	 * </ul>
	 *
	 * <p>Rotation is the randomiser vanilla gives for free: there is exactly one ship template in
	 * the game, so four headings, a burial depth, an integrity roll and a furrow of its own are what
	 * keep two wrecks from being the same wreck.
	 */
	private static void wreck(Plan plan, Kingdom kingdom, CloudField field, int centerX, int centerZ,
			RandomSource random) {
		BlockPos rest = deckSpot(field, centerX, centerZ, 12, random);
		if (rest == null) return;

		Rotation rotation = Rotation.getRandom(random);
		Direction bow = rotation.rotate(SHIP_BOW);

		// Keel below the deck top, so the hull is sitting in the cloud rather than on it.
		int burial = 2 + random.nextInt(3);
		BlockPos keel = rest.below(burial);

		float integrity = 0.60F + random.nextFloat() * 0.25F;
		plan.template(END_SHIP, keel, rotation, integrity, Plan.Dressing.NONE);

		furrow(plan, field, rest, bow.getOpposite(), random);
	}

	/**
	 * The gouge the ship ploughed on the way in: a trench cut back from the stern, deepest where the
	 * ship came to rest and feathering to nothing where it first touched down.
	 *
	 * <p>Cut to the <em>local</em> surface at every step rather than to one altitude. The deck is
	 * quantised into twelve-block plateaus, so a trench at a fixed height would hang in open air over
	 * a low cell and bury itself under a high one. Following the surface makes it step with the deck,
	 * which is what a furrow ploughed across uneven ground does anyway.
	 *
	 * <p>It starts inside the ship's own footprint on purpose. The trench only clears cloud, and the
	 * hull has already cleared its own hole there, so the two meet instead of leaving a lip of deck
	 * between the wreck and the mark it made getting there.
	 */
	private static void furrow(Plan plan, CloudField field, BlockPos rest, Direction back,
			RandomSource random) {
		int reach = 24 + random.nextInt(16);
		Direction across = back.getClockWise();

		for (int step = 6; step <= reach; step++) {
			double out = (step - 6) / (double) (reach - 6);
			int half = Math.max(1, (int) Math.round(7 * (1 - out)) + 1);
			int depth = (int) Math.round(3 * (1 - out));
			if (depth < 1) continue;

			for (int v = -half; v <= half; v++) {
				BlockPos at = rest.relative(back, step).relative(across, v);
				int top = field.firmSurfaceY(at.getX(), at.getZ(), 2);
				if (top == Integer.MIN_VALUE) continue;

				for (int d = 0; d < depth; d++) {
					plan.set(at.getX(), top - d, at.getZ(), Blocks.AIR.defaultBlockState());
				}

				// Pieces shed on the way, lying on the floor of the trench they made.
				if (random.nextInt(100) < 6) {
					plan.set(at.getX(), top - depth, at.getZ(), Palette.wreckage(random));
				}
			}
		}
	}

	// ---------------------------------------------------------------- shared decoration

	/**
	 * Free-standing arcs of masonry, the last standing bits of whatever enclosed the site.
	 *
	 * <p>The mix is the caller's, because an arch is the tier's own ruin and not a shared prop. A
	 * forge ringed in white quartz would be a black smithy with somebody else's stonework around it,
	 * which is the one thing that tier cannot afford to look like.
	 */
	private static void brokenArches(Plan plan, CloudField field, int centerX, int centerZ, int radius,
			int count, RandomSource random, Function<RandomSource, BlockState> masonry) {
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
				plan.set(at, masonry.apply(random));
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
		return deckSpot(field, centerX, centerZ, radius, random, FOOTING_DEPTH);
	}

	/** As above, but for the callers that need to know the cloud runs deeper than a footing. */
	private static BlockPos deckSpot(CloudField field, int centerX, int centerZ, int radius,
			RandomSource random, int minDepth) {
		for (int attempt = 0; attempt < 8; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double distance = Math.sqrt(random.nextDouble()) * radius;
			int x = centerX + (int) Math.round(Math.cos(angle) * distance);
			int z = centerZ + (int) Math.round(Math.sin(angle) * distance);

			int top = field.firmSurfaceY(x, z, minDepth);
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

	/**
	 * A sealed cell around a spawner, so it works in daylight.
	 *
	 * <p>A spawner runs the mob's ordinary spawn checks, and for a vex, a breeze or a blaze that
	 * includes darkness - so one standing in an open cloud hall is scenery from dawn until dusk.
	 * Vanilla answers this by burying its spawners in a room with no windows, and so does this:
	 * walls, floor and roof of the same stone the ruin is built from, with three blocks of air
	 * inside for the mobs to appear in.
	 *
	 * <p>Sealed rather than doored. A doorway lets in exactly the skylight the cell exists to keep
	 * out, and breaking into a room that should not be there is the oldest thing a spawner does.
	 *
	 * <p>The wall material is the tier's, so the cell reads as part of the ruin it is buried in
	 * rather than as quartz somebody left inside a blackstone smithy.
	 *
	 * <p>Written into the plan rather than placed directly, so a cell straddling a chunk boundary
	 * is clipped by the same code that clips everything else here.
	 */
	private static void spawnerCell(Plan plan, BlockPos centre) {
		spawnerCell(plan, centre, Blocks.SMOOTH_QUARTZ.defaultBlockState());
	}

	private static void spawnerCell(Plan plan, BlockPos centre, BlockState wall) {
		BlockState air = Blocks.AIR.defaultBlockState();

		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = -1; dy <= 3; dy++) {
				for (int dz = -2; dz <= 2; dz++) {
					BlockPos pos = centre.offset(dx, dy, dz);
					boolean shell = Math.abs(dx) == 2 || Math.abs(dz) == 2 || dy == -1 || dy == 3;

					if (shell) plan.set(pos, wall);
					else if (!pos.equals(centre)) plan.set(pos, air);
				}
			}
		}
	}
}
