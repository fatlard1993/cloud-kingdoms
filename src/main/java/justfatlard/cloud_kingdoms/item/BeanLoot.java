package justfatlard.cloud_kingdoms.item;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

import java.util.Set;

/**
 * Puts magic beans in chests a player can actually reach on foot.
 *
 * <p>This is the whole reason the bean is injected into vanilla tables instead of simply added to
 * the mod's own: a cloud kingdom's chests are on a cloud, and the bean is how you get to a cloud.
 * Shipping the only source of the ticket inside the place the ticket is for is a locked room with
 * the key on the wrong side of the door.
 *
 * <p>The tables below are all ground-level and all reachable in early-to-mid play. Weighting is one
 * bean against a heavy empty entry, so a table that rolls this pool usually yields nothing: finding
 * one should be the thing that makes the trip happen, not a thing that happens on the way to
 * something else.
 */
public final class BeanLoot {

	private BeanLoot() {}

	/** Empty weight against a single bean. Roughly a one-in-nineteen roll where the pool applies. */
	private static final int EMPTY_WEIGHT = 18;

	private static final Set<ResourceKey<LootTable>> TABLES = Set.of(
		BuiltInLootTables.VILLAGE_PLAINS_HOUSE,
		BuiltInLootTables.VILLAGE_SAVANNA_HOUSE,
		BuiltInLootTables.VILLAGE_SNOWY_HOUSE,
		BuiltInLootTables.VILLAGE_TAIGA_HOUSE,
		BuiltInLootTables.VILLAGE_DESERT_HOUSE,
		BuiltInLootTables.SHIPWRECK_SUPPLY,
		BuiltInLootTables.PILLAGER_OUTPOST,
		BuiltInLootTables.JUNGLE_TEMPLE,
		BuiltInLootTables.DESERT_PYRAMID);

	public static void register() {
		LootTableEvents.MODIFY.register((key, builder, source, registries) -> {
			// Only vanilla's own copy. A datapack that has deliberately rewritten one of these
			// tables has said what it wants in it, and appending to that is overruling an author who
			// was more specific than this mod is.
			if (source != LootTableSource.VANILLA) return;
			if (!TABLES.contains(key)) return;

			builder.pool(LootPool.lootPool()
				.setRolls(ConstantValue.exactly(1))
				.add(EmptyLootItem.emptyItem().setWeight(EMPTY_WEIGHT))
				.add(LootItem.lootTableItem(ModItems.MAGIC_BEAN).setWeight(1))
				.build());
		});
	}
}
