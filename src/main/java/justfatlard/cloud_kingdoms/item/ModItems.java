package justfatlard.cloud_kingdoms.item;

import justfatlard.cloud_kingdoms.CloudKingdoms;
import justfatlard.pandorical.api.ItemRegistration;
import justfatlard.pandorical.api.PandoricalApi;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/** The mod's items. One so far, and it is a ticket rather than a material. */
public final class ModItems {

	private ModItems() {}

	public static final String MAGIC_BEAN_NAME = "magic_bean";

	public static final MagicBeanItem MAGIC_BEAN = new MagicBeanItem(settings());

	private static Item.Properties settings() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
			Identifier.fromNamespaceAndPath(CloudKingdoms.MOD_ID, MAGIC_BEAN_NAME));

		return new Item.Properties()
			.setId(key)
			// One per stack. A bean is a single trip to the sky, and a stack of sixty-four of them
			// in a pocket makes the trip a formality rather than a decision.
			.stacksTo(1)
			.rarity(Rarity.RARE);
	}

	public static void register() {
		Identifier id = Identifier.fromNamespaceAndPath(CloudKingdoms.MOD_ID, MAGIC_BEAN_NAME);

		Registry.register(BuiltInRegistries.ITEM, id, MAGIC_BEAN);

		if (PandoricalApi.isAvailable()) {
			PandoricalApi.content().registerItem(CloudKingdoms.MOD_ID + ":" + MAGIC_BEAN_NAME,
				new ItemRegistration().model(CloudKingdoms.MOD_ID + ":item/" + MAGIC_BEAN_NAME));
		}
	}
}
