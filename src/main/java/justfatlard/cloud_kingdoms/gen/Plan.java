package justfatlard.cloud_kingdoms.gen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything a kingdom puts on top of its cloud, resolved to absolute world positions before any of
 * it is written.
 *
 * <p>The plan exists because a kingdom is wider than a chunk. Its piece's {@code postProcess} is
 * called once per overlapping chunk and may only touch blocks inside the chunk it was handed, so a
 * generator that drew a tower by walking it block by block would draw a different quarter of that
 * tower on each call and have no way to agree with itself about the parts it skipped.
 *
 * <p>Instead the whole kingdom is drawn into a plan from a stored seed, and each chunk writes the
 * subset of the plan that lands inside it. The plan is rebuilt from scratch on every one of those
 * calls, which is the trade: a few milliseconds of redundant work per chunk buys the freedom to
 * write the generators as straight-line code with an ordinary sequential {@code RandomSource},
 * rather than forcing every decision to be a pure function of its own coordinates.
 */
public final class Plan {

	public enum Encounter { GIANT, VEX, BREEZE, HORSEMAN, CHARGED_CREEPER, SHULKER, AXOLOTL, GOLDEN_GOOSE }

	public record Chest(BlockPos pos, Direction facing) {}

	public record Spawn(BlockPos pos, Encounter encounter) {}

	/** A mob spawner and what it is set to produce. */
	public record Spawner(BlockPos pos, EntityType<?> entity) {}

	private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
	private final List<Chest> chests = new ArrayList<>();
	private final List<Spawn> spawns = new ArrayList<>();
	private final List<Spawner> spawners = new ArrayList<>();

	public void set(int x, int y, int z, BlockState state) {
		blocks.put(new BlockPos(x, y, z), state);
	}

	public void set(BlockPos pos, BlockState state) {
		blocks.put(pos, state);
	}

	public void chest(BlockPos pos, Direction facing) {
		chests.add(new Chest(pos, facing));
	}

	public void spawn(BlockPos pos, Encounter encounter) {
		spawns.add(new Spawn(pos, encounter));
	}

	public void spawner(BlockPos pos, EntityType<?> entity) {
		spawners.add(new Spawner(pos, entity));
	}

	public Map<BlockPos, BlockState> blocks() {
		return blocks;
	}

	public List<Chest> chests() {
		return chests;
	}

	public List<Spawn> spawns() {
		return spawns;
	}

	public List<Spawner> spawners() {
		return spawners;
	}
}
