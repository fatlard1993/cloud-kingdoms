package justfatlard.cloud_kingdoms.gen;

import justfatlard.cloud_kingdoms.block.ModBlocks;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The stone the kingdoms are built from, which is mostly not stone.
 *
 * <p>A cloud kingdom is built out of its own cloud. Bulk walls come out majority substrate with a
 * mineral minority mixed through them, so a tower reads as something the cloud grew rather than
 * something quarried and carried up. The stone is what keeps the shape legible: an all-cloud tower
 * on an all-cloud deck has no edges, and the mineral fraction is doing the work of an outline.
 *
 * <p>Which mineral is the one thing that still separates the tiers. A spire is quartz and calcite,
 * white on white, chalk against cloud. A citadel is purpur and end stone, and it is the only place
 * that material occurs in the overworld sky. A forge is blackstone and basalt, and it is the other
 * one. Finding either is meant to raise a question the mod never answers.
 *
 * <p>Structural accents - floors, the vault's inner chamber, bars and rods - are placed as
 * themselves by the generators rather than drawn from here, because those are the parts that have
 * to stay solid and readable however the mix rolls.
 *
 * <p>Nothing here reaches the open deck. Mineral belongs to the things that were built; the cloud
 * between them is cloud and nothing else.
 */
final class Palette {

	private Palette() {}

	/** Spire bulk: cloud, with worked quartz gone chalky through it. */
	static BlockState skyMasonry(RandomSource random) {
		return switch (random.nextInt(10)) {
			case 0, 1, 2, 3, 4, 5 -> ModBlocks.CLOUD_STATE;
			case 6, 7 -> Blocks.QUARTZ_BRICKS.defaultBlockState();
			case 8 -> Blocks.CALCITE.defaultBlockState();
			default -> Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState();
		};
	}

	/** Citadel bulk: cloud, with the End showing through it. */
	static BlockState endMasonry(RandomSource random) {
		return switch (random.nextInt(10)) {
			case 0, 1, 2, 3, 4, 5 -> ModBlocks.CLOUD_STATE;
			case 6, 7 -> Blocks.END_STONE_BRICKS.defaultBlockState();
			case 8 -> Blocks.PURPUR_BLOCK.defaultBlockState();
			default -> Blocks.CRYING_OBSIDIAN.defaultBlockState();
		};
	}

	/**
	 * Forge bulk: cloud, with the Nether burnt through it.
	 *
	 * <p>Dark where the other two are pale, which is the point - a forge is the one tier a player
	 * can pick out of the deck from the air, because every other kingdom is white on white and this
	 * one is a black mark on it.
	 */
	static BlockState forgeMasonry(RandomSource random) {
		return switch (random.nextInt(10)) {
			case 0, 1, 2, 3, 4, 5 -> ModBlocks.CLOUD_STATE;
			case 6, 7 -> Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
			case 8 -> Blocks.BASALT.defaultBlockState();
			// Gilded, one wall block in ten. The forge's walls are worth mining, which is the only
			// place in the mod where that is true, and it is why a forge is worth stripping rather
			// than only looting.
			default -> Blocks.GILDED_BLACKSTONE.defaultBlockState();
		};
	}

	/**
	 * A homestead's walls: crimson timber over blackstone footings.
	 *
	 * <p>The only mix here that is not stone and not salvage, because it is the only one somebody
	 * is still living behind. Piglins build in blackstone in the Nether, and this family brought
	 * their own timber through with them - so the walls read as carried rather than quarried, and
	 * nothing in them has been left to fall down.
	 */
	static BlockState homestead(RandomSource random) {
		return switch (random.nextInt(10)) {
			case 0, 1, 2, 3, 4 -> Blocks.CRIMSON_PLANKS.defaultBlockState();
			case 5, 6 -> Blocks.CRIMSON_STEM.defaultBlockState();
			case 7, 8 -> Blocks.POLISHED_BLACKSTONE.defaultBlockState();
			default -> Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
		};
	}

	/**
	 * Wreckage shed along a crash furrow, and the one mix in this file with no cloud in it at all.
	 *
	 * <p>Every other tier is built <em>out of</em> the cloud it stands on, which is why the bulk
	 * mixes are majority substrate. This is the opposite case: it is debris off a ship that was
	 * built somewhere else out of somewhere else's stone. Cloud among the pieces would say the sky
	 * grew them, and the whole point of the tier is that the sky did not.
	 *
	 * <p>The hull itself is not drawn from here or anywhere else in this mod - it is vanilla's own
	 * {@code end_city/ship} template. This is only what fell off it on the way down.
	 */
	static BlockState wreckage(RandomSource random) {
		return switch (random.nextInt(10)) {
			case 0, 1, 2, 3, 4 -> Blocks.PURPUR_BLOCK.defaultBlockState();
			case 5, 6 -> Blocks.PURPUR_PILLAR.defaultBlockState();
			case 7, 8 -> Blocks.END_STONE_BRICKS.defaultBlockState();
			default -> Blocks.END_STONE.defaultBlockState();
		};
	}

	/**
	 * Loose stone, for the one thing built out of nothing else: a bank's cairn.
	 *
	 * <p>All mineral, unlike the bulk walls, because a cairn is a handful of blocks and a cairn of
	 * cloud on a cloud deck is a cairn nobody can see.
	 */
	static BlockState rubble(RandomSource random) {
		return switch (random.nextInt(6)) {
			case 0, 1 -> Blocks.CALCITE.defaultBlockState();
			case 2, 3 -> Blocks.QUARTZ_SLAB.defaultBlockState();
			case 4 -> Blocks.SMOOTH_QUARTZ_SLAB.defaultBlockState();
			default -> Blocks.QUARTZ_BLOCK.defaultBlockState();
		};
	}
}
