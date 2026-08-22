package justfatlard.cloud_kingdoms.block;

import justfatlard.cloud_kingdoms.CloudKingdoms;
import justfatlard.pandorical.api.BlockRegistration;
import justfatlard.pandorical.api.ItemRegistration;
import justfatlard.pandorical.api.PandoricalApi;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** The mod's blocks, and the cloud state constant the generators build everything out of. */
public final class ModBlocks {

	private ModBlocks() {}

	public static final String CLOUD_NAME = "cloud";
	public static final String BEANSTALK_NAME = "beanstalk";

	public static final CloudBlock CLOUD = new CloudBlock(cloudSettings());

	public static final BeanstalkBlock BEANSTALK = new BeanstalkBlock(beanstalkSettings());

	/** Handed to the generators so they never have to reach through the block to its default. */
	public static final BlockState CLOUD_STATE = CLOUD.defaultBlockState();

	private static BlockBehaviour.Properties cloudSettings() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK,
			Identifier.fromNamespaceAndPath(CloudKingdoms.MOD_ID, CLOUD_NAME));

		return BlockBehaviour.Properties.of()
			.setId(key)
			.mapColor(DyeColor.WHITE)
			// Soft enough to dig through a deck by hand, hard enough that tunnelling a kingdom is
			// a decision. Cloud is not the prize; what the drop needs Silk Touch for is.
			.strength(0.4f)
			.sound(SoundType.WOOL)
			// Random ticks are what carry the drip: see CloudBlock.randomTick.
			.randomTicks();
	}

	private static BlockBehaviour.Properties beanstalkSettings() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK,
			Identifier.fromNamespaceAndPath(CloudKingdoms.MOD_ID, BEANSTALK_NAME));

		return BlockBehaviour.Properties.of()
			.setId(key)
			.mapColor(DyeColor.GREEN)
			// No collision, because climbing a column means standing inside it. The block is in
			// #minecraft:climbable, which is what actually carries you up; without the collision
			// being off you would be stopped by the thing you are trying to climb.
			.noCollision()
			.noOcclusion()
			.strength(0.3f)
			.sound(SoundType.VINE)
			// Cut a beanstalk and you get nothing. Said here rather than as an empty loot table,
			// which is a file a reader has to open before learning it says nothing.
			.noLootTable();
	}

	public static void register() {
		Identifier cloudId = Identifier.fromNamespaceAndPath(CloudKingdoms.MOD_ID, CLOUD_NAME);
		Identifier beanstalkId = Identifier.fromNamespaceAndPath(CloudKingdoms.MOD_ID, BEANSTALK_NAME);

		Registry.register(BuiltInRegistries.BLOCK, cloudId, CLOUD);
		Registry.register(BuiltInRegistries.ITEM, cloudId,
			new BlockItem(CLOUD, new Item.Properties().setId(ResourceKey.create(Registries.ITEM, cloudId))));

		// Block only, with no item form: a beanstalk is planted from a bean and drops nothing when
		// cut, so there is no stack of them for an item to represent.
		Registry.register(BuiltInRegistries.BLOCK, beanstalkId, BEANSTALK);

		if (PandoricalApi.isAvailable()) {
			PandoricalApi.content().registerModAssets(CloudKingdoms.MOD_ID);
			PandoricalApi.content().registerBlock(CloudKingdoms.MOD_ID + ":" + CLOUD_NAME,
				new BlockRegistration()
					// Snow block, not wool: the client stand-in copies the base block's settings,
					// and wool is flammable. A cloud that catches fire from a lantern two blocks
					// away is not the material this is meant to be.
					.baseBlock("minecraft:snow_block")
					.model(CloudKingdoms.MOD_ID + ":block/" + CLOUD_NAME));
			PandoricalApi.content().registerItem(CloudKingdoms.MOD_ID + ":" + CLOUD_NAME,
				new ItemRegistration().model(CloudKingdoms.MOD_ID + ":item/" + CLOUD_NAME));

			// Bamboo as the stand-in: the client copies the base block's settings, and what matters
			// here is that it is a non-occluding plant column rather than a full cube. The model is
			// deliberately opaque - Pandorical has no way to tell a client stand-in to render on the
			// cutout layer, so a cross model with transparent pixels would come out black.
			PandoricalApi.content().registerBlock(CloudKingdoms.MOD_ID + ":" + BEANSTALK_NAME,
				new BlockRegistration()
					.baseBlock("minecraft:bamboo")
					.model(CloudKingdoms.MOD_ID + ":block/" + BEANSTALK_NAME));
		}
	}
}
