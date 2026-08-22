package justfatlard.cloud_kingdoms.block;

import justfatlard.cloud_kingdoms.CloudKingdoms;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Cloud substrate: sand with the sign flipped.
 *
 * <p>Sand falls until something stops it. Cloud rises until something stops it, and three things
 * stop it: the layer, anything sticky, and anything solid overhead.
 *
 * <p>The rules, in the order they are tested by {@link #canRise}:
 *
 * <ol>
 *   <li><b>At or above {@link #SETTLE_Y}, buoyancy is off entirely.</b> Not "it has arrived so it
 *       stops": off. A block placed at y=200 never moves, never schedules, never checks its
 *       neighbours. That is what makes the cloud layer buildable - up there this is an ordinary
 *       white block and a player can lay a floor one block at a time without each block bolting
 *       upward the moment it lands. It is also the only thing holding a generated kingdom still:
 *       the field clips its underside flat at exactly this height, so no block of a deck is ever
 *       below the line and no deck ever needs another reason to stay put.</li>
 *   <li><b>Cloud touching something in {@link #ANCHORS_CLOUD} is anchored.</b> Slime and honey, out
 *       of the box. This is the only rule a player can use deliberately, and with cloud no longer
 *       anchoring cloud it is the <em>only</em> way to keep any amount of cloud below the layer.</li>
 *   <li><b>Something solid overhead stops it</b> until that something moves. Cloud does not eat
 *       through a roof; it waits under it, and the neighbour update from the roof coming down is
 *       what wakes it. This is also what makes a rising raft stack up under an existing deck rather
 *       than trying to occupy it.</li>
 * </ol>
 *
 * <p><b>Cloud deliberately does not anchor cloud.</b> It used to, which meant any two blocks placed
 * together were inert and only a lone block ever moved. A raft is the more interesting object: build
 * one below the line and the whole thing lifts, held together by nothing but arriving at the same
 * time, and a strip of slime is what keeps it moored.
 *
 * <p><b>A rising block takes its cargo with it.</b> Whatever is standing in the space it moves into
 * is lifted onto its new top, and a fluid source there is carried up rather than swallowed. Without
 * the first, a raft slides out from under its passenger; without the second, sailing a pond upward
 * drinks it. Both are in {@link #tick}, and both are written to survive being applied by every block
 * of a raft at once.
 *
 * <p><b>Why block steps and not a falling-block entity.</b> An inverted {@code FallingBlockEntity}
 * would drift more smoothly, but it is a new entity type, and a new entity type is a client-side
 * renderer. This mod otherwise asks nothing of the client beyond Pandorical's block sync, and one
 * cosmetic upgrade is not worth spending that. A scheduled block tick is invisible plumbing:
 * vanilla clients see ordinary block updates and need no code at all.
 */
public class CloudBlock extends Block {

	/**
	 * The plane cloud settles onto, chosen to match where the client already draws clouds:
	 * {@code minecraft:visual/cloud_height} in the overworld dimension type is 192.33, so a cloud
	 * block at y=192 presents its underside at 192.0, a third of a block under the render plane.
	 * A player looking up from the ground sees one continuous white ceiling rather than a deck
	 * floating off the clouds it is pretending to be.
	 */
	public static final int SETTLE_Y = 192;

	/** Ticks per block of rise. Four blocks a second: a visible drift, not a jump. */
	private static final int RISE_DELAY = 5;

	/**
	 * How far up a block looks for the water it is dripping. Comfortably more than the thickest
	 * deck, so a pond sunk into the top of a tarn still reaches the underside below it.
	 */
	private static final int DRIP_REACH = 24;

	/**
	 * Blocks that hold cloud down by touching it. Slime and honey to begin with, which is vanilla's
	 * own idea of sticky.
	 *
	 * <p>A tag rather than a hardcoded pair, because vanilla has no public way to ask whether a block
	 * is sticky - the piston resolver keeps that answer to itself in a private method - and because
	 * "what can pin a cloud down" is exactly the sort of thing a datapack should be able to extend
	 * without touching this mod.
	 *
	 * <p><b>Sticky moors per block, not per raft.</b> Each cloud block asks only about its own six
	 * neighbours, so one slime block under the corner of a raft holds that corner and watches the
	 * rest of the raft leave without it. Mooring a whole raft means sticky contact under every block
	 * of it. That is a consequence of cloud no longer anchoring cloud and not an oversight: adhesion
	 * that propagated through the cloud would be the old contact rule back again under another name,
	 * and rafts would stop floating.
	 */
	public static final TagKey<Block> ANCHORS_CLOUD = TagKey.create(Registries.BLOCK,
		Identifier.fromNamespaceAndPath(CloudKingdoms.MOD_ID, "anchors_cloud"));

	public CloudBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	/**
	 * Landing on cloud does not hurt, however far the fall.
	 *
	 * <p>Not a reduction like hay's, a cancellation. The whole point of the material is that it is
	 * the soft thing at the top of a hundred-and-thirty block climb, and the situation it most has
	 * to survive is a player stepping off the edge of a deck and hitting the next one down. A
	 * multiplier that still killed someone from a long enough drop would make the deck read as
	 * ordinary ground with a smaller number attached.
	 *
	 * <p>{@code causeFallDamage} is still called with a zero multiplier rather than skipped, because
	 * it is also what resets the accumulated fall distance and fires the events that go with landing.
	 * Returning without calling it leaves the entity still holding the fall it just took, to be
	 * charged for in full by the first ordinary block it touches afterwards.
	 */
	@Override
	public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance) {
		entity.causeFallDamage(fallDistance, 0.0F, level.damageSources().fall());
	}

	/**
	 * Cloud leaks. Water anywhere in the cloud above a block drips out of its underside.
	 *
	 * <p>This is what makes a tarn rain on whatever is beneath it, and it is why the ponds are worth
	 * putting on top: the weather is the point, and it is visible from the ground where the cloud
	 * itself is not.
	 *
	 * <p><b>Server-side, deliberately.</b> The obvious place for a particle is {@code animateTick},
	 * which is client code, and the client is not running this class - it has a Pandorical stand-in
	 * built from a base block. Anything written there would never execute. Emitting from a random
	 * tick and pushing the particle out over the wire is what reaches a vanilla client at all.
	 *
	 * <p>Random ticks cost nothing extra for being enabled on a common block: the game picks a fixed
	 * few positions per section per tick regardless, so the price is two block lookups on the rare
	 * occasion one of them lands on cloud.
	 */
	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!level.getBlockState(pos.below()).isAir()) return;

		for (int up = 1; up <= DRIP_REACH; up++) {
			BlockState overhead = level.getBlockState(pos.above(up));

			if (overhead.getFluidState().is(FluidTags.WATER)) {
				level.sendParticles(ParticleTypes.DRIPPING_WATER,
					pos.getX() + 0.5D, pos.getY() - 0.05D, pos.getZ() + 0.5D,
					1, 0.25D, 0.0D, 0.25D, 0.0D);
				return;
			}

			// Only water carried through unbroken cloud counts. A pond two decks up should not rain
			// through the open sky between them.
			if (!overhead.is(this)) return;
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!canRise(level, pos)) return;

		BlockPos above = pos.above();

		// Read the fluid before the block overwrites it, or there is nothing left to carry.
		FluidState displaced = level.getFluidState(above);
		List<Entity> riders = level.getEntities((Entity) null, new AABB(above), entity -> true);

		level.setBlock(above, state, Block.UPDATE_ALL);
		level.removeBlock(pos, false);

		carry(level, displaced, above.above());
		lift(riders, above.getY() + 1.0D);

		level.scheduleTick(above, this, RISE_DELAY);
	}

	/**
	 * Takes whatever was standing in the block's destination up with it.
	 *
	 * <p><b>Set to a height, never nudged by one.</b> A raft is many blocks rising in the same tick
	 * and a player standing on it is inside the destination of every block they overlap - up to four
	 * at once on a corner, and every block of a 5x5 raft would happily claim a passenger sitting in
	 * the middle of it. Adding a block to their position per claim would fire them into the sky.
	 * Assigning an absolute target is idempotent: however many blocks push, the answer is the same.
	 */
	private static void lift(List<Entity> riders, double targetY) {
		for (Entity rider : riders) {
			// Passengers are carried by their vehicle; moving both moves them twice.
			if (rider.isPassenger()) continue;
			if (rider.getY() >= targetY) continue;

			rider.setPos(rider.getX(), targetY, rider.getZ());
		}
	}

	/**
	 * Carries a fluid the block displaced up into the space above it.
	 *
	 * <p>Source blocks only. Flowing water is a shadow cast by a source somewhere else, and copying
	 * one produces a block that either evaporates on the next fluid tick or, worse, becomes a second
	 * source: a rising deck under a pond would turn into a fountain.
	 */
	private static void carry(ServerLevel level, FluidState displaced, BlockPos target) {
		if (!displaced.isSource()) return;
		if (!level.getBlockState(target).canBeReplaced()) return;

		level.setBlock(target, displaced.createLegacyBlock(), Block.UPDATE_ALL);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
		scheduleIfBuoyant(level, pos);
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			Orientation orientation, boolean movedByPiston) {
		// The anchor conditions are all neighbour state, so any neighbour change can free a block
		// that was held: the cloud it was resting against was mined, or the roof above it opened.
		scheduleIfBuoyant(level, pos);
	}

	/**
	 * The altitude test is repeated here rather than left to {@link #tick} because it is what keeps
	 * worldgen free. A generated kingdom's underside sits exactly on {@link #SETTLE_Y} and every
	 * block of it is at or above that, so laying a citadel down schedules nothing at all; leaving
	 * the test to the tick would queue one no-op per cloud block, tens of thousands of them, to fire
	 * the moment a player first loads the chunk.
	 */
	private void scheduleIfBuoyant(Level level, BlockPos pos) {
		if (pos.getY() >= SETTLE_Y) return;
		level.scheduleTick(pos, this, RISE_DELAY);
	}

	/**
	 * Whether the block at {@code pos} is free to move up one. Pure function of the world, with no
	 * per-block memory: a cloud block never has to remember whether it is "falling", so it cannot
	 * get stuck in a stale state across a chunk unload.
	 */
	private boolean canRise(LevelReader level, BlockPos pos) {
		if (pos.getY() >= SETTLE_Y) return false;

		for (Direction direction : Direction.values()) {
			if (level.getBlockState(pos.relative(direction)).is(ANCHORS_CLOUD)) return false;
		}

		return level.getBlockState(pos.above()).canBeReplaced();
	}
}
