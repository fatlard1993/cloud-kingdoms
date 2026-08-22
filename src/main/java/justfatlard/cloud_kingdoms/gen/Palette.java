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
 * that material occurs in the overworld sky. Finding one is meant to raise a question the mod never
 * answers.
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
