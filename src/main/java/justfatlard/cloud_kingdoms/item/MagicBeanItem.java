package justfatlard.cloud_kingdoms.item;

import justfatlard.cloud_kingdoms.block.BeanstalkBlock;
import justfatlard.cloud_kingdoms.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Plants a beanstalk, or explains why it will not take.
 *
 * <p>The two refusals are separated on purpose. Somewhere the seed physically cannot go is a
 * {@code FAIL} and says nothing, because the player can see the wall they clicked. Somewhere the
 * seed <em>could</em> go but will never sprout is worth a sentence: the water-or-sky rule is the
 * only part of this item that is not guessable, and a bean silently consumed into ground that never
 * grows anything is the kind of thing a player writes off as a broken mod.
 */
public class MagicBeanItem extends Item {

	public MagicBeanItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos clicked = context.getClickedPos();
		Direction face = context.getClickedFace();
		BlockPos target = BeanstalkBlock.plantingSpot(clicked, face);

		// Planted on top of something solid, the way a seed is. Anything else and the stalk would be
		// growing out of the side of a wall or out of thin air.
		if (face != Direction.UP) return InteractionResult.FAIL;

		BlockState below = level.getBlockState(clicked);
		if (!below.isFaceSturdy(level, clicked, Direction.UP)) return InteractionResult.FAIL;

		if (!level.getBlockState(target).canBeReplaced()) return InteractionResult.FAIL;

		if (!BeanstalkBlock.canGrow(level, target)) {
			Player player = context.getPlayer();
			if (player != null && !level.isClientSide()) {
				player.sendOverlayMessage(
					Component.literal("The bean needs water nearby, or open sky above."));
			}
			return InteractionResult.FAIL;
		}

		if (level.isClientSide()) return InteractionResult.SUCCESS;

		level.setBlock(target, ModBlocks.BEANSTALK.defaultBlockState(), Block.UPDATE_ALL);
		level.playSound(null, target, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 0.8F);

		Player player = context.getPlayer();
		if (player == null || !player.hasInfiniteMaterials()) context.getItemInHand().shrink(1);

		return InteractionResult.SUCCESS;
	}
}
