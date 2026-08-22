package justfatlard.cloud_kingdoms;

import justfatlard.cloud_kingdoms.block.ModBlocks;
import justfatlard.cloud_kingdoms.command.CloudKingdomCommand;
import justfatlard.cloud_kingdoms.item.BeanLoot;
import justfatlard.cloud_kingdoms.item.ModItems;
import justfatlard.cloud_kingdoms.worldgen.CloudStructureRegistration;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloudKingdoms implements ModInitializer {

	public static final String MOD_ID = "cloud-kingdoms-justfatlard";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModBlocks.register();
		ModItems.register();
		BeanLoot.register();
		CloudStructureRegistration.register();

		CommandRegistrationCallback.EVENT.register(
			(dispatcher, registryAccess, environment) -> CloudKingdomCommand.register(dispatcher));

		// Guarded, and the guard is the reason the call sits behind its own class: naming a
		// block-tip type here would load it whether or not block-tip is installed.
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("block-tip")) {
			justfatlard.cloud_kingdoms.integration.CloudTipRegistration.register();
		}

		LOGGER.info("Cloud Kingdoms loaded");
	}
}
