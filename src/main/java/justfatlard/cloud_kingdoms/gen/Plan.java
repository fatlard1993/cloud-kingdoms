package justfatlard.cloud_kingdoms.gen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Rotation;
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

	public enum Encounter { GIANT, VEX, BREEZE, HORSEMAN, CHARGED_CREEPER, SHULKER, AXOLOTL, GOLDEN_GOOSE,
		BLAZE, MAGMA_CUBE, STRIDER, ILLUSIONER, FROG, PIGLIN, PIGLIN_CHILD, PIGLIN_BRUTE, ALLAY }

	public record Chest(BlockPos pos, Direction facing) {}

	public record Spawn(BlockPos pos, Encounter encounter) {}

	/**
	 * A vanilla structure template to stamp into the world, and how much of it survived.
	 *
	 * <p>The one thing in this plan that is not resolved to blocks here. Everything else is drawn by
	 * rule because the mod has no templates of its own; this is the opposite case, where the game
	 * already ships the asset and re-drawing it by hand would be worse in every way. What the plan
	 * carries is the decision - which template, where, facing how, how badly broken - and the stamp
	 * itself happens at write time, where the template manager can be reached.
	 *
	 * <p><b>{@code centre} is where the template's footprint should end up centred</b>, not its
	 * origin corner. A rotated template lands offset from its placement position by an amount that
	 * depends on its own size, which the plan has no way to know; naming the centre lets whoever
	 * stamps it work the corner out and lets everything drawn around it agree without either side
	 * knowing the template's dimensions.
	 */
	public record Template(Identifier id, BlockPos centre, Rotation rotation, float integrity,
			Dressing dressing) {}

	/**
	 * What to do to a template's stone on the way in.
	 *
	 * <p>{@link #NETHER} is vanilla's own conversion, the one behind {@code replace_with_blackstone}
	 * on its nether ruined portals: stone brick turns to blackstone and gold blocks to gilded
	 * blackstone, stairs and slabs included. It exists as a processor in the game already, so this
	 * is a switch rather than a rule list.
	 */
	public enum Dressing { NONE, NETHER }

	/** A mob spawner and what it is set to produce. */
	public record Spawner(BlockPos pos, EntityType<?> entity) {}

	private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
	private final List<Chest> chests = new ArrayList<>();
	private final List<Spawn> spawns = new ArrayList<>();
	private final List<Template> templates = new ArrayList<>();
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

	public void template(Identifier id, BlockPos centre, Rotation rotation, float integrity,
			Dressing dressing) {
		templates.add(new Template(id, centre, rotation, integrity, dressing));
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

	public List<Template> templates() {
		return templates;
	}
}
