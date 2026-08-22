package justfatlard.cloud_kingdoms.worldgen;

import justfatlard.cloud_kingdoms.CloudKingdoms;
import justfatlard.cloud_kingdoms.block.ModBlocks;
import justfatlard.cloud_kingdoms.gen.CloudField;
import justfatlard.cloud_kingdoms.gen.Encounters;
import justfatlard.cloud_kingdoms.gen.Kingdom;
import justfatlard.cloud_kingdoms.gen.Plan;
import justfatlard.cloud_kingdoms.gen.Ruins;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.Map;

/**
 * One cloud kingdom: the mass, the ruins on it, the chests in them and the garrison around them.
 *
 * <p>A kingdom is wider than a chunk, so this piece's {@link #postProcess} is called once for each
 * chunk its bounding box covers, and each call may only write inside the chunk it was handed. Two
 * different mechanisms keep those calls agreeing with each other:
 *
 * <ul>
 *   <li>The <b>cloud mass</b> is a pure function of world position, sampled per column. Two chunks
 *       meeting at an edge produce a continuous surface because both asked the same function.</li>
 *   <li>The <b>ruins</b> are drawn in full, for the whole kingdom, from the stored seed, and each
 *       chunk writes the subset that lands inside it. That also gives the encounters their
 *       once-and-only-once property for free: a spawn anchor is a single block position, a block
 *       position is inside exactly one chunk, so exactly one call places that mob.</li>
 * </ul>
 *
 * <p><b>Both are cached on the piece.</b> The structure start is shared across every chunk that
 * references it, so the field and the plan get built once instead of once per chunk. The fields are
 * volatile because chunk workers run in parallel: two threads racing here waste a build, which is
 * harmless, but a half-filled plan published to a second thread would silently drop blocks.
 */
public class CloudKingdomPiece extends StructurePiece {

	private static final String TAG_TIER = "Tier";
	private static final String TAG_SEED = "ShapeSeed";
	private static final String TAG_ORIGIN_X = "OriginX";
	private static final String TAG_ORIGIN_Z = "OriginZ";

	/**
	 * Slack on the bounding box beyond the cloud's own radius. Arches and rubble are sited on firm
	 * cloud but drawn outward from there, and a piece that writes outside its bounding box writes
	 * into chunks that were never told to call it: the overhang would simply go missing.
	 */
	private static final int OVERHANG = 12;

	private final Kingdom kingdom;
	private final long shapeSeed;
	private final int originX;
	private final int originZ;

	private volatile CloudField field;
	private volatile Plan plan;

	public CloudKingdomPiece(Kingdom kingdom, BlockPos origin, long shapeSeed) {
		super(CloudStructureRegistration.CLOUD_KINGDOM_PIECE, 0, bounds(kingdom, origin));
		this.kingdom = kingdom;
		this.shapeSeed = shapeSeed;
		this.originX = origin.getX();
		this.originZ = origin.getZ();
	}

	public CloudKingdomPiece(CompoundTag tag) {
		super(CloudStructureRegistration.CLOUD_KINGDOM_PIECE, tag);
		this.kingdom = Kingdom.byId(tag.getStringOr(TAG_TIER, Kingdom.BANK.id()));
		this.shapeSeed = tag.getLongOr(TAG_SEED, 0L);
		this.originX = tag.getIntOr(TAG_ORIGIN_X, this.boundingBox.getCenter().getX());
		this.originZ = tag.getIntOr(TAG_ORIGIN_Z, this.boundingBox.getCenter().getZ());
	}

	private static BoundingBox bounds(Kingdom kingdom, BlockPos origin) {
		int reach = kingdom.radius + OVERHANG;
		return new BoundingBox(
			origin.getX() - reach,
			// The underside is the floor of the whole structure: the cloud is clipped flat at the
			// origin's y and nothing is drawn below it any more.
			origin.getY(),
			origin.getZ() - reach,
			origin.getX() + reach,
			origin.getY() + kingdom.thickness + 6,
			origin.getZ() + reach);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putString(TAG_TIER, kingdom.id());
		tag.putLong(TAG_SEED, shapeSeed);
		tag.putInt(TAG_ORIGIN_X, originX);
		tag.putInt(TAG_ORIGIN_Z, originZ);
	}

	@Override
	public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
			RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
		try {
			CloudField cloud = field();
			placeCloud(level, cloud, chunkBox);

			Plan blueprint = plan(cloud);
			placePlan(level, blueprint, chunkBox);
			placeChests(level, blueprint, chunkBox);
			placeSpawners(level, blueprint, chunkBox, random);

			for (Plan.Spawn spawn : blueprint.spawns()) {
				if (chunkBox.isInside(spawn.pos())) Encounters.spawn(level, spawn, random);
			}
		} catch (Exception e) {
			// One bad chunk of a cloud beats aborting the chunk's whole decoration pass.
			CloudKingdoms.LOGGER.error("Cloud kingdom placement failed at {}", chunkPos, e);
		}
	}

	private CloudField field() {
		CloudField cached = field;
		if (cached == null) {
			cached = new CloudField(kingdom, originX, originZ, shapeSeed);
			field = cached;
		}
		return cached;
	}

	private Plan plan(CloudField cloud) {
		Plan cached = plan;
		if (cached == null) {
			cached = Ruins.draw(kingdom, cloud, originX, originZ, shapeSeed);
			plan = cached;
		}
		return cached;
	}

	private void placeCloud(WorldGenLevel level, CloudField cloud, BoundingBox chunkBox) {
		int minX = Math.max(boundingBox.minX(), chunkBox.minX());
		int maxX = Math.min(boundingBox.maxX(), chunkBox.maxX());
		int minZ = Math.max(boundingBox.minZ(), chunkBox.minZ());
		int maxZ = Math.min(boundingBox.maxZ(), chunkBox.maxZ());

		int minY = Math.max(cloud.baseY(), chunkBox.minY());
		int maxY = Math.min(cloud.topY(), chunkBox.maxY());

		BlockState cloudState = ModBlocks.CLOUD_STATE;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int y = minY; y <= maxY; y++) {
					if (!cloud.isCloudPlaced(x, y, z)) continue;
					level.setBlock(cursor.set(x, y, z), cloudState, Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	private void placePlan(WorldGenLevel level, Plan blueprint, BoundingBox chunkBox) {
		for (Map.Entry<BlockPos, BlockState> entry : blueprint.blocks().entrySet()) {
			BlockPos pos = entry.getKey();
			if (!chunkBox.isInside(pos)) continue;
			level.setBlock(pos, entry.getValue(), Block.UPDATE_CLIENTS);
		}
	}

	private void placeChests(WorldGenLevel level, Plan blueprint, BoundingBox chunkBox) {
		for (Plan.Chest chest : blueprint.chests()) {
			BlockPos pos = chest.pos();
			if (!chunkBox.isInside(pos)) continue;

			// A chest with anything solid over it cannot be opened. Clearing the lid here rather
			// than in the plan is what makes that unconditional: this runs after every block the
			// plan had to say, so no drawing pass, present or added later, can bury a chest.
			BlockPos lid = pos.above();
			if (chunkBox.isInside(lid)) {
				level.setBlock(lid, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}

			level.setBlock(pos, Blocks.CHEST.defaultBlockState()
				.setValue(ChestBlock.FACING, chest.facing()), Block.UPDATE_CLIENTS);

			if (level.getBlockEntity(pos) instanceof ChestBlockEntity container) {
				// Seeded off the position and the kingdom rather than the world's RNG, so a given
				// chest rolls the same contents whichever order its chunk happened to generate in.
				container.setLootTable(kingdom.lootTable(), shapeSeed ^ pos.asLong());
			}
		}
	}

	private void placeSpawners(WorldGenLevel level, Plan blueprint, BoundingBox chunkBox, RandomSource random) {
		for (Plan.Spawner spawner : blueprint.spawners()) {
			BlockPos pos = spawner.pos();
			if (!chunkBox.isInside(pos)) continue;

			level.setBlock(pos, Blocks.SPAWNER.defaultBlockState(), Block.UPDATE_CLIENTS);

			// A spawner block with no block entity behind it is an empty cage that spawns pigs when
			// a player finally touches it, so the mob is set here rather than left to a default.
			if (level.getBlockEntity(pos) instanceof SpawnerBlockEntity cage) {
				cage.setEntityId(spawner.entity(), random);
			}
		}
	}
}
