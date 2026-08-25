package justfatlard.cloud_kingdoms.block;

import justfatlard.cloud_kingdoms.CloudKingdoms;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
	 * How far up a block looks for the fluid it is leaking. Comfortably more than the thickest deck,
	 * so a bowl sunk into the top of a tarn or a forge still reaches the underside below it.
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
	 * Cloud leaks. Fluid anywhere in the cloud above a block shows at its underside: water drips,
	 * lava smokes.
	 *
	 * <p>This is what makes a tarn rain and a forge smoke, and it is why both tiers cut their bowls
	 * into the deck rather than building basins on top of it: what leaks out is the point, and it is
	 * visible from the ground where the cloud itself is not.
	 *
	 * <p><b>Cosmetic on purpose.</b> The particle is the whole effect - no fire is set, no block
	 * changes, and nothing lands on anybody. A cloud that actually dropped its lava on the terrain a
	 * hundred and thirty blocks below would be a tier that burns down whatever it drifts over, and
	 * the drip is meant to be a tell, not a weapon.
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
			FluidState fluid = overhead.getFluidState();

			if (fluid.is(FluidTags.WATER)) {
				leak(level, pos, ParticleTypes.DRIPPING_WATER);
				return;
			}
			if (fluid.is(FluidTags.LAVA)) {
				leak(level, pos, ParticleTypes.DRIPPING_LAVA);
				return;
			}

			// Only fluid carried through unbroken cloud counts. A pond two decks up should not rain
			// through the open sky between them.
			if (!overhead.is(this)) return;
		}
	}

	private static void leak(ServerLevel level, BlockPos pos, ParticleOptions particle) {
		level.sendParticles(particle,
			pos.getX() + 0.5D, pos.getY() - 0.05D, pos.getZ() + 0.5D,
			1, 0.25D, 0.0D, 0.25D, 0.0D);
	}

	/**
	 * The most cloud that moves as one thing.
	 *
	 * <p>Past this a mass rises block by block again, which looks worse but costs nothing to
	 * work out. Nothing a player builds by hand comes close; the ceiling is here so that a
	 * pathological shape cannot make one block tick walk a hundred thousand neighbours.
	 */
	private static final int MAX_MASS = 2048;

	/**
	 * A raft is one thing, and rises as one.
	 *
	 * <p>Every block used to move on its own schedule. Two blocks laid a moment apart were half a
	 * second out of phase for ever after, so a deck built by hand came apart on the way up - and
	 * an anchor only ever held the blocks it was touching while the rest of the raft floated off
	 * without them. Neither is what "a raft" means.
	 *
	 * <p>So the connected mass is gathered and tested as a whole: it rises only if every block of
	 * it can, which is what lets one slime block moor a whole deck, and it moves in one tick,
	 * which is what keeps it a deck. One block of the mass drives - the lowest, and of those the
	 * one furthest north-west, chosen by position so that every block of the mass agrees who it
	 * is without anybody having to remember.
	 */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		List<BlockPos> mass = gather(level, pos);

		// Too big to reason about as one body: fall back to the old solitary rise.
		if (mass == null) {
			riseAlone(state, level, pos);
			return;
		}

		BlockPos leader = leaderOf(mass);
		if (!leader.equals(pos)) {
			// Woken through a block that does not drive this mass - a neighbour changed, or one
			// was just placed. Hand it to the one that does.
			level.scheduleTick(leader, this, RISE_DELAY);
			return;
		}

		Set<BlockPos> coming = new HashSet<>(mass);
		for (BlockPos member : mass) {
			if (!canRise(level, member, coming)) return;
		}

		// Everything read before anything is written: once the first block moves, the world the
		// others were measured against is gone.
		//
		// A rider is claimed by every block whose destination they overlap - four at once on a
		// corner - so the target is kept as the highest claim rather than applied per claim. The
		// answer is then the same however many blocks push, which is what the single-block
		// version was already careful about.
		Map<Entity, Double> lifting = new HashMap<>();
		Map<BlockPos, FluidState> displaced = new HashMap<>();
		for (BlockPos member : mass) {
			BlockPos above = member.above();
			displaced.put(above, level.getFluidState(above));

			double target = member.getY() + 2.0D;
			for (Entity rider : level.getEntities((Entity) null, new AABB(above), entity -> true)) {
				lifting.merge(rider, target, Math::max);
			}
		}

		// Highest first, so each block moves into a space the one above it has already left.
		List<BlockPos> order = new ArrayList<>(mass);
		order.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
		for (BlockPos member : order) {
			level.setBlock(member.above(), state, Block.UPDATE_ALL);
			level.removeBlock(member, false);
		}

		for (Map.Entry<BlockPos, FluidState> entry : displaced.entrySet()) {
			carry(level, entry.getValue(), entry.getKey().above());
		}

		// One block up, each to the top of whatever they were standing on.
		lifting.forEach((rider, target) -> lift(List.of(rider), target));

		level.scheduleTick(leader.above(), this, RISE_DELAY);
	}

	/** The old behaviour, for a mass too large to move in one piece. */
	private void riseAlone(BlockState state, ServerLevel level, BlockPos pos) {
		if (!canRise(level, pos, Set.of())) return;

		BlockPos above = pos.above();
		FluidState displaced = level.getFluidState(above);
		List<Entity> riders = level.getEntities((Entity) null, new AABB(above), entity -> true);

		level.setBlock(above, state, Block.UPDATE_ALL);
		level.removeBlock(pos, false);

		carry(level, displaced, above.above());
		lift(riders, above.getY() + 1.0D);

		level.scheduleTick(above, this, RISE_DELAY);
	}

	/**
	 * Every cloud block joined to this one, or null if there are more than {@link #MAX_MASS}.
	 *
	 * <p>Faces only. A deck touching another at one corner is two decks, which is also how it
	 * looks.
	 */
	private List<BlockPos> gather(ServerLevel level, BlockPos start) {
		List<BlockPos> found = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();

		seen.add(start.immutable());
		queue.add(start.immutable());

		while (!queue.isEmpty()) {
			BlockPos here = queue.poll();
			found.add(here);
			if (found.size() > MAX_MASS) return null;

			for (Direction direction : Direction.values()) {
				BlockPos next = here.relative(direction);
				if (!level.getBlockState(next).is(this)) continue;
				if (!seen.add(next.immutable())) continue;
				queue.add(next.immutable());
			}
		}
		return found;
	}

	/** Lowest, then furthest north-west: an ordering every block of the mass computes the same. */
	private static BlockPos leaderOf(List<BlockPos> mass) {
		BlockPos best = mass.getFirst();
		for (BlockPos candidate : mass) {
			if (candidate.getY() < best.getY()
				|| (candidate.getY() == best.getY() && candidate.getX() < best.getX())
				|| (candidate.getY() == best.getY() && candidate.getX() == best.getX()
					&& candidate.getZ() < best.getZ())) {
				best = candidate;
			}
		}
		return best;
	}

	/**
	 * Takes whatever was standing in the block's destination up with it.
	 *
	 * <p><b>Set to a height, never nudged by one.</b> A raft is many blocks rising in the same tick
	 * and a player standing on it is inside the destination of every block they overlap - up to four
	 * at once on a corner, and every block of a 5x5 raft would happily claim a passenger sitting in
	 * the middle of it. Adding a block to their position per claim would fire them into the sky.
	 * Assigning an absolute target is idempotent: however many blocks push, the answer is the same.
	 *
	 * <p><b>A player has to be told.</b> {@code setPos} moves the entity the server is holding and
	 * says nothing to the client, which is enough for a mob or a dropped item - the tracker
	 * broadcasts those - but a player's client keeps reporting the position it still believes in,
	 * and the server reconciles to it. The cloud rose and the rider stayed where they were: stood
	 * on a piece to ride it up, and it dropped them. A teleport through the connection is the same
	 * absolute assertion, delivered somewhere it lands.
	 */
	private static void lift(List<Entity> riders, double targetY) {
		for (Entity rider : riders) {
			// Passengers are carried by their vehicle; moving both moves them twice.
			if (rider.isPassenger()) continue;
			if (rider.getY() >= targetY) continue;

			if (rider instanceof ServerPlayer player) {
				// Only the height is asserted. Everything else stays relative, so the rider keeps
				// walking about the deck, keeps looking where they were looking, and keeps
				// whatever momentum they had while the floor climbs underneath them.
				player.connection.teleport(
					new PositionMoveRotation(new Vec3(0.0D, targetY, 0.0D), Vec3.ZERO, 0.0F, 0.0F),
					RIDING);
				continue;
			}

			rider.setPos(rider.getX(), targetY, rider.getZ());
		}
	}

	/** Everything except the height, left as it was. */
	private static final java.util.Set<Relative> RIDING = java.util.Set.of(
		Relative.X, Relative.Z, Relative.Y_ROT, Relative.X_ROT,
		Relative.DELTA_X, Relative.DELTA_Y, Relative.DELTA_Z);

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
	private boolean canRise(LevelReader level, BlockPos pos, Set<BlockPos> mass) {
		if (pos.getY() >= SETTLE_Y) return false;

		for (Direction direction : Direction.values()) {
			if (level.getBlockState(pos.relative(direction)).is(ANCHORS_CLOUD)) return false;
		}

		// The block overhead is only a ceiling if it is not coming with us.
		BlockPos above = pos.above();
		if (mass.contains(above)) return true;

		return level.getBlockState(above).canBeReplaced();
	}
}
