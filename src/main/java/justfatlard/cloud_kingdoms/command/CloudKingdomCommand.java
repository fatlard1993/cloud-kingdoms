package justfatlard.cloud_kingdoms.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import justfatlard.cloud_kingdoms.block.CloudBlock;
import justfatlard.cloud_kingdoms.block.ModBlocks;
import justfatlard.cloud_kingdoms.gen.CloudField;
import justfatlard.cloud_kingdoms.gen.Encounters;
import justfatlard.cloud_kingdoms.gen.Kingdom;
import justfatlard.cloud_kingdoms.gen.Plan;
import justfatlard.cloud_kingdoms.gen.Ruins;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/**
 * {@code /cloudkingdom <tier> [seed]}: builds one kingdom in the sky above the caller.
 *
 * <p>Worldgen structures are hard to iterate on. A cloud kingdom is worse than most, because it is
 * 130 blocks above the ground and the tiers that are worth looking at are the rare ones. This drops
 * one overhead on demand, with an optional seed so a shape worth studying can be reproduced.
 *
 * <p>It reuses the real generators rather than reimplementing them, which is the point: what the
 * command shows is what worldgen would produce. {@link ServerLevel} implements
 * {@code WorldGenLevel}, so even the garrison goes in through the same path.
 */
public final class CloudKingdomCommand {

	private CloudKingdomCommand() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("cloudkingdom")
			.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));

		for (Kingdom kingdom : Kingdom.values()) {
			root.then(Commands.literal(kingdom.id())
				.executes(context -> build(context.getSource(), kingdom,
					context.getSource().getLevel().getRandom().nextLong()))
				.then(Commands.argument("seed", LongArgumentType.longArg())
					.executes(context -> build(context.getSource(), kingdom,
						LongArgumentType.getLong(context, "seed")))));
		}

		dispatcher.register(root);
	}

	private static int build(CommandSourceStack source, Kingdom kingdom, long seed) {
		ServerLevel level = source.getLevel();
		Vec3 at = source.getPosition();
		int centerX = (int) Math.floor(at.x);
		int centerZ = (int) Math.floor(at.z);

		CloudField field = new CloudField(kingdom, centerX, centerZ, seed);
		Plan plan = Ruins.draw(kingdom, field, centerX, centerZ, seed);
		RandomSource random = RandomSource.create(seed);

		int reach = kingdom.radius + 12;
		int placed = 0;

		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int x = centerX - reach; x <= centerX + reach; x++) {
			for (int z = centerZ - reach; z <= centerZ + reach; z++) {
				for (int y = field.baseY(); y <= field.topY(); y++) {
					if (!field.isCloudPlaced(x, y, z)) continue;
					level.setBlock(cursor.set(x, y, z), ModBlocks.CLOUD_STATE, Block.UPDATE_CLIENTS);
					placed++;
				}
			}
		}

		for (Map.Entry<BlockPos, BlockState> entry : plan.blocks().entrySet()) {
			level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS);
		}

		for (Plan.Chest chest : plan.chests()) {
			// Same lid clearing the worldgen path does, for the same reason: this command has to
			// build what worldgen builds or it is not worth testing with.
			level.setBlock(chest.pos().above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			level.setBlock(chest.pos(), Blocks.CHEST.defaultBlockState()
				.setValue(ChestBlock.FACING, chest.facing()), Block.UPDATE_CLIENTS);
			if (level.getBlockEntity(chest.pos()) instanceof ChestBlockEntity container) {
				container.setLootTable(kingdom.lootTable(), seed ^ chest.pos().asLong());
			}
		}

		for (Plan.Spawner spawner : plan.spawners()) {
			level.setBlock(spawner.pos(), Blocks.SPAWNER.defaultBlockState(), Block.UPDATE_CLIENTS);
			if (level.getBlockEntity(spawner.pos()) instanceof SpawnerBlockEntity cage) {
				cage.setEntityId(spawner.entity(), random);
			}
		}

		for (Plan.Spawn spawn : plan.spawns()) {
			Encounters.spawn(level, spawn, random);
		}

		int cloudBlocks = placed;
		source.sendSuccess(() -> Component.literal(
			"Raised a cloud " + kingdom.id() + " at " + centerX + ", " + CloudBlock.SETTLE_Y + ", " + centerZ
				+ " (seed " + seed + ", " + cloudBlocks + " cloud blocks)"), true);

		return 1;
	}
}
