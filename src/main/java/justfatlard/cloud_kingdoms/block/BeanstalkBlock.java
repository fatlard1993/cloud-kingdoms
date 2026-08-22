package justfatlard.cloud_kingdoms.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The beanstalk: the way up.
 *
 * <p>Cutting the chains that used to hang under a kingdom left the clouds with no tell and no route.
 * This is the route. A bean planted on the ground grows overnight into a climbable column that ends
 * ten blocks above the cloud layer, which is to say through whatever kingdom happens to be overhead
 * and out the other side.
 *
 * <p><b>The base drives the growth, not the tip.</b> Only the lowest block of a stalk schedules
 * ticks; it walks up to find its own tip and adds one block there. That is what lets the growing
 * conditions be asked about the roots, where they belong: a plant's access to water and sky is a
 * fact about where it is planted, not about where its topmost leaf currently is. Driving it from the
 * tip instead would have made a stalk stall the moment it grew under a roof, which is the exact
 * situation it is supposed to smash through.
 */
public class BeanstalkBlock extends Block {

	/** How far past the cloud layer the stalk finishes, so it ends somewhere you can step off. */
	public static final int OVERSHOOT = 10;

	public static final int TOP_Y = CloudBlock.SETTLE_Y + OVERSHOOT;

	/** How far from the roots water counts as water, matching farmland's reach. */
	private static final int WATER_RANGE = 4;

	/** Ticks per block once it is going. A stalk from sea level finishes well inside one night. */
	private static final int GROW_DELAY = 30;

	/** Ticks between "is it night yet, is there still water" re-checks while it waits. */
	private static final int WAIT_DELAY = 100;

	private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 16, 11);

	public BeanstalkBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos,
			CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
		wake(level, pos);
	}

	/**
	 * Also woken by a neighbour changing, not only by being placed.
	 *
	 * <p>{@code onPlace} alone is not enough, because it is not called by every route a block can
	 * arrive by: {@code /setblock} writes with update flag 2 and skips it entirely, so a stalk placed
	 * by an operator or a structure would stand there inert forever with no way to ever start. This
	 * is also what lets a bucket emptied beside a stalk that gave up for lack of water restart it.
	 */
	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			net.minecraft.world.level.redstone.Orientation orientation, boolean movedByPiston) {
		wake(level, pos);
	}

	private void wake(Level level, BlockPos pos) {
		if (!isBase(level, pos)) return;

		// A stalk that has lost its footing is on its way down, not waiting to grow. The game keeps
		// one scheduled tick per position, so whichever is asked for first is the one that happens:
		// cutting the bottom out fires this before updateShape gets to ask for the removal, and a
		// growth tick a hundred ticks out would swallow it. That is what made a cut stalk come apart
		// at one block every five seconds instead of one a tick, which on a hundred-block stalk is
		// indistinguishable from nothing happening at all.
		if (!canSurvive(level.getBlockState(pos), level, pos)) return;

		level.scheduleTick(pos, this, WAIT_DELAY);
	}

	/**
	 * A stalk stands on the ground it was planted on, or on more stalk.
	 *
	 * <p>Which is what makes cutting one bring down everything above it: each block is held up by
	 * the one below, so removing any block leaves its neighbour unsupported, and that one the next.
	 */
	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockState below = level.getBlockState(pos.below());

		return below.is(this) || below.isFaceSturdy(level, pos.below(), Direction.UP);
	}

	/**
	 * Losing its footing schedules the block's own removal rather than returning air here.
	 *
	 * <p>Returning air would unwind the whole column inside one neighbour update, and a stalk can be
	 * a hundred and forty blocks tall - deep enough to run into the update limit and leave the top
	 * half standing on nothing. A tick apiece costs seven seconds for the tallest stalk and reads as
	 * the thing coming apart from the bottom, which is what it is.
	 */
	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickView,
			BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState,
			RandomSource random) {
		if (!canSurvive(state, level, pos)) tickView.scheduleTick(pos, this, 1);

		return state;
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		// Asked before anything else: a stalk with nothing under it has no growing left to do, and
		// the tick that brought us here may well be the one scheduled by the block below going.
		if (!canSurvive(state, level, pos)) {
			level.destroyBlock(pos, false);
			return;
		}

		// Every block of a stalk is the same block, so without this the whole column would tick and
		// every one of them would race to extend the same tip.
		if (!isBase(level, pos)) return;

		BlockPos tip = findTip(level, pos);

		if (tip.getY() >= TOP_Y) return;

		if (!canGrow(level, pos)) {
			// Not dead, just dry: a bucket emptied beside the roots later is meant to start it.
			level.scheduleTick(pos, this, WAIT_DELAY);
			return;
		}

		// Waits for the first nightfall, then runs to the top without stopping at dawn. Gating every
		// block on darkness instead would leave a half-height stalk standing over anyone who slept
		// through the night they planted it.
		boolean started = tip.getY() > pos.getY();
		if (!started && level.isBrightOutside()) {
			level.scheduleTick(pos, this, WAIT_DELAY);
			return;
		}

		if (grow(level, tip)) level.scheduleTick(pos, this, GROW_DELAY);
	}

	/**
	 * Adds one block on top of the tip, through whatever is in the way. Returns false when the stalk
	 * has hit something it cannot pass, which is the one case where it gives up for good.
	 */
	private boolean grow(ServerLevel level, BlockPos tip) {
		BlockPos next = tip.above();
		BlockState blocking = level.getBlockState(next);

		if (!blocking.isAir()) {
			// Bedrock and the like. Everything else the stalk is entitled to break, but a stalk that
			// kept rescheduling against something indestructible would tick forever.
			if (blocking.getDestroySpeed(level, next) < 0) return false;

			// Dropped rather than deleted: most of what a stalk meets on the way up is either
			// terrain or somebody's roof, and silently eating a chest full of things is a worse
			// surprise than a pile of items at the bottom of the beanstalk.
			level.destroyBlock(next, true, null, 512);
		}

		level.setBlock(next, defaultBlockState(), Block.UPDATE_ALL);
		return true;
	}

	/** The lowest block of this stalk: the one that was planted. */
	private boolean isBase(LevelReader level, BlockPos pos) {
		return !level.getBlockState(pos.below()).is(this);
	}

	private BlockPos findTip(LevelReader level, BlockPos base) {
		BlockPos tip = base;
		while (level.getBlockState(tip.above()).is(this)) tip = tip.above();
		return tip;
	}

	/**
	 * Whether the roots have what they need: water within {@link #WATER_RANGE}, or open sky.
	 *
	 * <p>Either, not both, and the pair is what decides where a stalk can be planted. Outdoors, the
	 * sky is enough. Underground or indoors it needs water, and then it grows up through the ceiling
	 * that was blocking the sky in the first place.
	 */
	public static boolean canGrow(LevelReader level, BlockPos pos) {
		return hasWaterNear(level, pos) || hasSkyView(level, pos);
	}

	private static boolean hasWaterNear(LevelReader level, BlockPos pos) {
		for (BlockPos nearby : BlockPos.betweenClosed(
				pos.offset(-WATER_RANGE, -1, -WATER_RANGE),
				pos.offset(WATER_RANGE, 1, WATER_RANGE))) {
			if (level.getFluidState(nearby).is(FluidTags.WATER)) return true;
		}
		return false;
	}

	/**
	 * Nothing solid overhead. Read off the heightmap rather than by walking the column, which is the
	 * same thing the vanilla sky check does and is a lookup instead of a scan.
	 */
	private static boolean hasSkyView(LevelReader level, BlockPos pos) {
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) <= pos.getY() + 1;
	}

	/** Where a bean planted against {@code face} of {@code clicked} would put its roots. */
	public static BlockPos plantingSpot(BlockPos clicked, Direction face) {
		return clicked.relative(face);
	}
}
