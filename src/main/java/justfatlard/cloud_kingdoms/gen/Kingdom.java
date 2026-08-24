package justfatlard.cloud_kingdoms.gen;

import justfatlard.cloud_kingdoms.CloudKingdoms;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.List;
import java.util.Locale;

/**
 * The five tiers of cloud kingdom, and every number that distinguishes them.
 *
 * <p>One structure class serves all of them; which one a given structure is comes off its JSON as a
 * string and resolves to a constant here. Keeping the dimensions, the erosion rate and the garrison
 * in one table is what lets the tiers be compared at a glance instead of chased through three
 * near-identical generators.
 *
 * <p><b>Counts are ranges, not numbers.</b> Fixed counts made every spire in the world hold exactly
 * three vexes and one horseman: the shape of a kingdom varied and what lived on it never did, so the
 * second one you visited told you everything about the third. A range costs one extra int per
 * encounter and means the garrison is something you have to look at rather than recall.
 */
public enum Kingdom {

	/**
	 * A drifting shelf with nothing built on it, and everything worth having buried in it.
	 *
	 * <p>The floor of the ladder, and the floor is not a consolation prize. Getting onto any cloud
	 * at all costs a magic bean or an elytra, so the cheapest kingdom in the sky is still somewhere
	 * a player went out of their way to reach: a bank has to pay for the trip on its own terms
	 * rather than by being a stepping stone to a spire.
	 *
	 * <p>What it pays with is an open deck and no cover. The garrison is breezes, in numbers, on the
	 * one tier with nothing built on it to duck behind - and every wind charge is aimed at somebody
	 * standing on a shelf with a hundred and thirty blocks under the edge.
	 */
	BANK(46, 13, 8, 16, 2, 3,
		new Roll(Plan.Encounter.BREEZE, 5, 9),
		new Roll(Plan.Encounter.ILLUSIONER, 0, 1)),

	/**
	 * Ponds cut into the deck, axolotls in them, and rain falling out of the underside. The only
	 * tier with nothing hostile on it, and one of the two that give themselves away from the ground:
	 * the water leaks through the cloud the same way a forge's lava does, and what falls out is
	 * visible where the cloud itself is not.
	 */
	TARN(52, 18, 10, 12, 2, 3,
		new Roll(Plan.Encounter.AXOLOTL, 3, 8),
		// On the lily pads rather than in the water, which is the only reason to have put pads there.
		new Roll(Plan.Encounter.FROG, 2, 5),
		// Sometimes none, which is what makes the ones that do turn up worth the flight up.
		new Roll(Plan.Encounter.ALLAY, 0, 2)),

	/**
	 * A ruined watchtower, its garrison still on station.
	 *
	 * <p>The floors here follow the bank's rather than sitting at the old numbers. Raising the
	 * bottom of the ladder made a spire that rolled low come out thinner than the tier under it,
	 * which reads as a mistake however good the reason for it.
	 */
	SPIRE(56, 20, 11, 22, 2, 4,
		new Roll(Plan.Encounter.VEX, 2, 5),
		new Roll(Plan.Encounter.BREEZE, 3, 5),
		new Roll(Plan.Encounter.HORSEMAN, 0, 2),
		new Roll(Plan.Encounter.ILLUSIONER, 0, 2)),

	/**
	 * A smithy the Nether burnt up through, with the fires still lit and something still tending
	 * them. The tarn's opposite in the one way that matters: both cut basins into their own deck and
	 * both leak through the underside, but a tarn is the tier with nothing on it that wants you dead
	 * and a forge is a floor you can fall through.
	 */
	FORGE(62, 23, 12, 24, 3, 4,
		new Roll(Plan.Encounter.BLAZE, 3, 6),
		new Roll(Plan.Encounter.MAGMA_CUBE, 2, 4),
		// The tarn's axolotls, in the other fluid. Harmless, at home in what would kill anything
		// else standing there, and the only thing on a forge that is not trying to end the visit.
		new Roll(Plan.Encounter.STRIDER, 1, 3)),

	/**
	 * A piglin family who came through a portal, liked what they found, and broke the portal.
	 *
	 * <p>The mod's only inhabited tier, and the only one that is not a ruin. Everything else here is
	 * something that failed - a tower that lost its top, a smithy that burnt through, a ship that
	 * came down. This is the one place where somebody arrived and it went well.
	 *
	 * <p><b>Which is why its erosion is almost nothing.</b> That number is how much of a structure
	 * has fallen off, and it is low here for a reason a reader should be able to guess from the
	 * fiction: the walls are in good repair because somebody is repairing them.
	 *
	 * <p>Hostile only in the way piglins are hostile. Walk up wearing gold and the homestead is a
	 * place to trade; walk up without it, or open their chests in front of them, and it is a fight
	 * with a family defending a farm. The mod adds nothing to make that work - it is the vanilla
	 * mechanic, and putting a piglin household somewhere a player will arrive by air is the whole
	 * design.
	 */
	HOMESTEAD(54, 19, 10, 8, 2, 3,
		new Roll(Plan.Encounter.PIGLIN, 3, 5),
		// A family, not a garrison. The children are the difference between a home and an outpost.
		new Roll(Plan.Encounter.PIGLIN_CHILD, 1, 2),
		new Roll(Plan.Encounter.PIGLIN_BRUTE, 0, 1)),

	/** Where the sky has worn through onto something else. */
	CITADEL(76, 29, 15, 34, 4, 6,
		// Exactly one, expressed as a range that cannot roll otherwise: a citadel without its
		// colossus is a citadel missing the thing it is built around.
		new Roll(Plan.Encounter.GIANT, 1, 1),
		new Roll(Plan.Encounter.VEX, 2, 6),
		new Roll(Plan.Encounter.BREEZE, 2, 5),
		new Roll(Plan.Encounter.HORSEMAN, 1, 3),
		new Roll(Plan.Encounter.CHARGED_CREEPER, 1, 3),
		new Roll(Plan.Encounter.SHULKER, 3, 5),
		new Roll(Plan.Encounter.ILLUSIONER, 1, 2)),

	/**
	 * An End ship that came down on a cloud and did not leave.
	 *
	 * <p>Last in the list and rarest in the world, and the only tier that is not a place. The other
	 * five are somewhere the sky put something; this is somewhere something arrived, badly, and the
	 * cloud has the gouge to prove it. A citadel raises the question of why End stone is in the
	 * overworld sky and never answers it. A wreck answers it, once, and the answer is that something
	 * flew here.
	 *
	 * <p>Eroded harder than anything else in the mod, and hardest at the bow: the hull is drawn whole
	 * and then taken apart by the same rule every other ruin uses, with the damage weighted toward
	 * the end that hit first.
	 */
	WRECK(58, 21, 11, 30, 3, 4,
		// Breezes only. The ship's own crew is three shulkers, and they are not rolled here because
		// they are not this mod's to place: they are data markers inside the vanilla template, put
		// where Mojang put them, and they arrive when it is stamped.
		new Roll(Plan.Encounter.BREEZE, 2, 4),
		new Roll(Plan.Encounter.ILLUSIONER, 0, 1));

	/** How many of one kind of mob a kingdom gets, drawn per kingdom rather than fixed. */
	public record Roll(Plan.Encounter encounter, int min, int max) {
		public int count(RandomSource random) {
			return min + random.nextInt(max - min + 1);
		}
	}

	/**
	 * Horizontal reach of the cloud mass from the structure centre, in blocks.
	 *
	 * <p>This decides how a tier <em>reads</em> as well as how big it is. {@link CloudField} samples
	 * the field once per twelve-block cell, so a tier is roughly {@code radius / 7.5} cells across
	 * and that is the whole budget its outline has to be interesting in. Under about six cells a
	 * kingdom comes out as two or three rectangles, which is one of the two reasons the smallest
	 * tier is not small any more.
	 */
	public final int radius;
	/**
	 * Vertical half-axis of the core puff, and the hard ceiling on the mass. The cloud tops out
	 * lower than this, around two thirds of it: the field reaches zero at a puff's pole, so the
	 * surface threshold is crossed well before the arithmetic top. Treat it as a dial, not a
	 * measurement.
	 */
	public final int thickness;
	/** Number of blended spheroids the mass is built from. More puffs, more lobes. */
	public final int puffs;
	/** Percent of ruin blocks dropped on the floor, before the extra erosion applied with height. */
	public final int erosion;

	public final int minChests;
	public final int maxChests;

	private final List<Roll> garrison;

	Kingdom(int radius, int thickness, int puffs, int erosion, int minChests, int maxChests,
			Roll... garrison) {
		this.radius = radius;
		this.thickness = thickness;
		this.puffs = puffs;
		this.erosion = erosion;
		this.minChests = minChests;
		this.maxChests = maxChests;
		this.garrison = List.of(garrison);
	}

	public List<Roll> garrison() {
		return garrison;
	}

	/**
	 * How many of one encounter this kingdom gets this time, or zero if the tier does not have
	 * that kind at all. For the ones the architecture places itself - the giant on its plinth,
	 * shulkers in the vault, axolotls in a pond - so they can still be rolled from the same table
	 * as everything scattered over the deck.
	 */
	public int countOf(Plan.Encounter encounter, RandomSource random) {
		for (Roll roll : garrison) {
			if (roll.encounter() == encounter) return roll.count(random);
		}
		return 0;
	}

	public int chests(RandomSource random) {
		return minChests + random.nextInt(maxChests - minChests + 1);
	}

	/** The name this tier goes by in JSON and in the {@code /cloudkingdom} command. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public ResourceKey<LootTable> lootTable() {
		return ResourceKey.create(Registries.LOOT_TABLE,
			Identifier.fromNamespaceAndPath(CloudKingdoms.MOD_ID, "chests/cloud_" + id()));
	}

	/** Falls back to {@link #BANK} rather than throwing: a typo in a datapack should cost a cloud, not the world. */
	public static Kingdom byId(String id) {
		for (Kingdom kingdom : values()) {
			if (kingdom.id().equals(id)) return kingdom;
		}
		CloudKingdoms.LOGGER.warn("Unknown cloud kingdom tier '{}', falling back to bank", id);
		return BANK;
	}
}
