package justfatlard.cloud_kingdoms.integration;

import justfatlard.block_tip.api.BlockTipApi;
import justfatlard.cloud_kingdoms.CloudKingdoms;
import justfatlard.cloud_kingdoms.block.ModBlocks;

/**
 * Gives the beanstalk a picture, since it has no item of its own.
 *
 * <p>Nothing is meant to carry a beanstalk: cutting one yields nothing, so no item is registered,
 * so the card drew an empty square where the answer should be. Bamboo is the block it is built on
 * and the nearest thing in the game to a green stalk you climb, which makes it the honest stand-in.
 *
 * <p>Names block-tip types directly, so it must only be loaded behind the isModLoaded guard in the
 * entry point.
 */
public final class CloudTipRegistration {
	private CloudTipRegistration() {}

	private static final String BAMBOO = "minecraft:bamboo";

	public static void register() {
		BlockTipApi.icon(CloudKingdoms.MOD_ID + ":" + ModBlocks.BEANSTALK_NAME, BAMBOO);
	}
}
