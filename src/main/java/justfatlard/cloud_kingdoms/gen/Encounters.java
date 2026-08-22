package justfatlard.cloud_kingdoms.gen;

import justfatlard.cloud_kingdoms.CloudKingdoms;
import justfatlard.cloud_kingdoms.entity.GoldenGoose;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.WorldGenLevel;

/**
 * Puts the kingdom's garrison on the deck.
 *
 * <p>Every mob here is placed once, during the one worldgen pass its chunk ever gets, and marked
 * persistent. Nothing spawns a cloud kingdom's mobs a second time: a chunk that unloads and reloads
 * reads its entities back off disk and never re-runs structure placement, so no "already spawned"
 * marker is needed and there is no way for a kingdom to accumulate a mob every time a player flies
 * past it.
 *
 * <p>Persistence is not decoration either. These are placed at y=192 in permanent daylight, which is
 * exactly the condition under which the despawn rules would clear the deck before anyone arrived.
 */
public final class Encounters {

	private Encounters() {}

	public static void spawn(WorldGenLevel level, Plan.Spawn spawn, RandomSource random) {
		try {
			switch (spawn.encounter()) {
				case GIANT -> simple(level, EntityTypes.GIANT, spawn.pos(), random);
				case VEX -> simple(level, EntityTypes.VEX, spawn.pos(), random);
				case BREEZE -> simple(level, EntityTypes.BREEZE, spawn.pos(), random);
				case SHULKER -> simple(level, EntityTypes.SHULKER, spawn.pos(), random);
				case AXOLOTL -> simple(level, EntityTypes.AXOLOTL, spawn.pos(), random);
				case GOLDEN_GOOSE -> goldenGoose(level, spawn.pos(), random);
				case CHARGED_CREEPER -> chargedCreeper(level, spawn.pos(), random);
				case HORSEMAN -> horseman(level, spawn.pos(), random);
			}
		} catch (Exception e) {
			// A garrison that cannot be placed is a quiet cloud, not a broken chunk.
			CloudKingdoms.LOGGER.error("Could not place {} at {}", spawn.encounter(), spawn.pos(), e);
		}
	}

	private static <T extends Mob> T simple(WorldGenLevel level, net.minecraft.world.entity.EntityType<T> type,
			BlockPos pos, RandomSource random) {
		T mob = type.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
		if (mob == null) return null;

		mob.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.STRUCTURE, null);
		mob.setPersistenceRequired();
		level.addFreshEntityWithPassengers(mob);
		return mob;
	}

	/**
	 * A creeper charged the only way the game exposes: by being struck.
	 *
	 * <p>{@code Creeper.thunderHit} is what sets the powered flag, and it calls up to
	 * {@code Entity.thunderHit} on the way, which sets the creeper on fire and takes five off it.
	 * Both are side effects of the mechanism rather than anything wanted here, so both are undone
	 * immediately. The bolt itself is never added to the world: it exists only as the argument.
	 */
	private static void chargedCreeper(WorldGenLevel level, BlockPos pos, RandomSource random) {
		Creeper creeper = simple(level, EntityTypes.CREEPER, pos, random);
		if (creeper == null) return;

		ServerLevel serverLevel = level.getLevel();
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(serverLevel, EntitySpawnReason.EVENT);
		if (bolt == null) return;

		bolt.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
		creeper.thunderHit(serverLevel, bolt);
		creeper.clearFire();
		creeper.setHealth(creeper.getMaxHealth());
	}

	/** The citadel's other resident, for anyone who waits around long enough to notice. */
	private static void goldenGoose(WorldGenLevel level, BlockPos pos, RandomSource random) {
		Chicken chicken = simple(level, EntityTypes.CHICKEN, pos, random);
		if (chicken == null) return;

		GoldenGoose.anoint(chicken);
	}

	/** A skeleton on a skeleton horse: the sky patrol. */
	private static void horseman(WorldGenLevel level, BlockPos pos, RandomSource random) {
		SkeletonHorse horse = EntityTypes.SKELETON_HORSE.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
		Skeleton rider = EntityTypes.SKELETON.create(level.getLevel(), EntitySpawnReason.JOCKEY);
		if (horse == null || rider == null) return;

		horse.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
		// Not a trap horse: the trap is the lightning ambush that fires when a player walks up to a
		// riderless one, and this horse already has its rider.
		horse.setTrap(false);
		horse.setPersistenceRequired();
		horse.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.STRUCTURE, null);

		rider.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
		rider.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.JOCKEY, null);
		// After finalizeSpawn, which rolls its own equipment and would otherwise overwrite these.
		rider.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		rider.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		rider.setPersistenceRequired();
		rider.startRiding(horse);

		level.addFreshEntityWithPassengers(horse);
	}
}
