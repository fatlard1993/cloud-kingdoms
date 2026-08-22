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
 * The three sizes of cloud kingdom, and every number that distinguishes them.
 *
 * <p>One structure class serves all three; which one a given structure is comes off its JSON as a
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
	 * A drifting shelf with nothing built on it. The common case on purpose: most clouds a player
	 * flies to should be a place to stand and a small find, so that the ones with a tower on them
	 * still read as an event.
	 */
	BANK(30, 9, 6, 12, 1, 2,
		new Roll(Plan.Encounter.BREEZE, 1, 3)),

	/**
	 * Ponds cut into the deck, axolotls in them, and rain falling out of the underside. The only
	 * tier with nothing hostile on it and the only one visible from the ground, because the water
	 * leaks through the cloud and gives the whole thing away.
	 */
	TARN(38, 15, 8, 10, 1, 2,
		new Roll(Plan.Encounter.AXOLOTL, 3, 8)),

	/** A ruined watchtower, its garrison still on station. */
	SPIRE(42, 16, 9, 22, 1, 3,
		new Roll(Plan.Encounter.VEX, 1, 4),
		new Roll(Plan.Encounter.BREEZE, 2, 4),
		new Roll(Plan.Encounter.HORSEMAN, 0, 2)),

	/** Where the sky has worn through onto something else. */
	CITADEL(64, 26, 14, 34, 3, 6,
		// Exactly one, expressed as a range that cannot roll otherwise: a citadel without its
		// colossus is a citadel missing the thing it is built around.
		new Roll(Plan.Encounter.GIANT, 1, 1),
		new Roll(Plan.Encounter.VEX, 2, 6),
		new Roll(Plan.Encounter.BREEZE, 2, 5),
		new Roll(Plan.Encounter.HORSEMAN, 1, 3),
		new Roll(Plan.Encounter.CHARGED_CREEPER, 1, 3),
		new Roll(Plan.Encounter.SHULKER, 3, 5));

	/** How many of one kind of mob a kingdom gets, drawn per kingdom rather than fixed. */
	public record Roll(Plan.Encounter encounter, int min, int max) {
		public int count(RandomSource random) {
			return min + random.nextInt(max - min + 1);
		}
	}

	/** Horizontal reach of the cloud mass from the structure centre, in blocks. */
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
