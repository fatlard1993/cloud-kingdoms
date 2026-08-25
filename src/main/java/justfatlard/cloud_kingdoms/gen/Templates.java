package justfatlard.cloud_kingdoms.gen;

import justfatlard.cloud_kingdoms.CloudKingdoms;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlackstoneReplaceProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockRotProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

import java.util.Optional;

/**
 * Stamps a vanilla structure template into the world, data markers and all.
 *
 * <p>Everything else this mod builds is drawn by rule, because the mod ships no templates. A wreck
 * is the one case where the game already has the asset: {@code end_city/ship} is a real End ship
 * with a real dragon head on the prow, and hand-drawing a worse one would be more code and a
 * standing promise to keep it looking like a vanilla asset it is not.
 *
 * <p><b>A raw stamp is not enough.</b> The template's chests are empty and it contains no entities
 * at all - the elytra, the shulkers and the chest loot are not in the file. They are six
 * {@code structure_block} markers carrying one word each, which vanilla reads on the way past. Place
 * the template without reading them and you get a ship with seven structure blocks left standing in
 * it, two empty chests and no elytra: recognisably the shape of an End ship with everything that
 * makes it one missing. So this reads them, exactly the way
 * {@code EndCityPieces.EndCityPiece.handleDataMarker} does.
 *
 * <p><b>Why no random is set on the placement.</b> {@link BlockRotProcessor} asks the settings for a
 * random per block, and {@code StructurePlaceSettings.getRandom} falls back to one seeded from the
 * block's own position when none was given. That fallback is the property this needs: a kingdom
 * spans several chunks and this runs once per chunk, so a shared random stream would rot a different
 * share of the hull on every call and the ship would disagree with itself across chunk borders.
 * Leaving it unset makes the damage a pure function of world position, which is the same trick
 * {@link CloudField} uses to keep a cloud's edge continuous.
 */
public final class Templates {

	private Templates() {}

	public static void place(ServerLevelAccessor level, Plan.Template stamp, BoundingBox clip,
			RandomSource random) {
		StructureTemplateManager manager = level.getLevel().getServer().getStructureTemplateManager();
		Optional<StructureTemplate> found = manager.get(stamp.id());

		if (found.isEmpty()) {
			// A missing vanilla template is not worth aborting a chunk over, but it is worth saying
			// out loud: the tier is a hole in the cloud with nothing in it.
			CloudKingdoms.LOGGER.error("Missing structure template {}, wreck will be empty", stamp.id());
			return;
		}

		StructureTemplate template = found.get();
		BlockPos origin = centreOn(template, stamp);

		StructurePlaceSettings settings = new StructurePlaceSettings()
			.setRotation(stamp.rotation())
			.setIgnoreEntities(true)
			.setBoundingBox(clip)
			// Ignore the markers, keep the air. The markers are metadata rather than masonry, and
			// the air is what excavates a stamp's berth out of the cloud it landed in.
			.addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);

		// Vanilla's own nether conversion rather than a hand-written rule list, which also means
		// stairs and slabs keep their facing instead of being flattened to a default state.
		if (stamp.dressing() == Plan.Dressing.NETHER) {
			settings.addProcessor(BlackstoneReplaceProcessor.INSTANCE);
		}

		// Skipped outright at full integrity. A ruined portal arrives ruined; running every block of
		// it past a rot check that cannot fail is a pass that says something is being damaged when
		// nothing is.
		if (stamp.integrity() < 1.0F) {
			settings.addProcessor(new BlockRotProcessor(stamp.integrity()));
		}

		template.placeInWorld(level, origin, origin, settings, random, Block.UPDATE_CLIENTS);

		for (StructureTemplate.StructureBlockInfo marker :
				template.filterBlocks(origin, settings, Blocks.STRUCTURE_BLOCK)) {
			if (marker.nbt() == null) continue;
			handle(marker.nbt().getStringOr("metadata", ""), marker.pos(), level, settings, clip, random);
		}
	}

	/**
	 * Where to actually start the stamp so the template's footprint ends up centred on the plan's
	 * chosen point.
	 *
	 * <p>A template rotates about its placement position, so under three of the four rotations it
	 * lands somewhere other than where it was asked to go - by its own length, which is not a small
	 * number for a ship. The plan cannot correct for that because it has no way to know how big the
	 * template is. Correcting here means the furrow drawn around the centre and the hull stamped on
	 * it agree without either one knowing the ship's dimensions.
	 */
	private static BlockPos centreOn(StructureTemplate template, Plan.Template stamp) {
		BlockPos centre = stamp.centre();
		StructurePlaceSettings measure = new StructurePlaceSettings().setRotation(stamp.rotation());
		BoundingBox box = template.getBoundingBox(measure, centre);

		return centre.offset(
			centre.getX() - (box.minX() + box.getXSpan() / 2),
			0,
			centre.getZ() - (box.minZ() + box.getZSpan() / 2));
	}

	/**
	 * One data marker, handled the way vanilla handles it.
	 *
	 * <p>The three words the ship uses are {@code Chest}, {@code Sentry} and {@code Elytra}, and
	 * the behaviour behind each is copied rather than invented: End city treasure in the chest below
	 * the marker, a shulker standing on it, an elytra hanging in a frame at it. Anything else is
	 * ignored, so a template that grows a new marker in some future version degrades to that part
	 * being absent rather than to a crash.
	 */
	private static void handle(String metadata, BlockPos pos, ServerLevelAccessor level,
			StructurePlaceSettings settings, BoundingBox clip, RandomSource random) {
		if (!clip.isInside(pos)) return;

		if (metadata.startsWith("Chest")) {
			BlockPos below = pos.below();
			if (clip.isInside(below)) {
				RandomizableContainer.setBlockEntityLootTable(level, random, below,
					BuiltInLootTables.END_CITY_TREASURE);
			}
			return;
		}

		if (metadata.startsWith("Sentry")) {
			Shulker shulker = EntityTypes.SHULKER.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
			if (shulker == null) return;

			shulker.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
			// Persistent, unlike vanilla's: a deck at y=192 is in permanent daylight and a shulker
			// left despawnable is a crew that is gone before anybody flies up to find it.
			shulker.setPersistenceRequired();
			level.addFreshEntity(shulker);
			return;
		}

		if (metadata.startsWith("Elytra")) {
			Direction facing = settings.getRotation().rotate(Direction.SOUTH);

			// The one block the crash is not allowed to take. A frame hangs on the block behind it,
			// that block is hull, and hull is what the rot processor eats - so on an unlucky roll the
			// wall goes, the frame pops on the next update, and the elytra drops as an item and
			// despawns long before anyone flies up. The tier would still generate, still look right,
			// and quietly not contain the only reason to have come.
			BlockPos support = pos.relative(facing.getOpposite());
			if (clip.isInside(support) && !level.getBlockState(support).isSolid()) {
				level.setBlock(support, Blocks.PURPUR_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
			}

			ItemFrame frame = new ItemFrame(level.getLevel(), pos, facing);
			frame.setItem(new ItemStack(Items.ELYTRA), false);
			level.addFreshEntity(frame);
		}
	}
}
