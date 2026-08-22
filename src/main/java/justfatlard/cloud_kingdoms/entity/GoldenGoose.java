package justfatlard.cloud_kingdoms.entity;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.chicken.ChickenVariant;

/**
 * The goose that is not a goose.
 *
 * <p>An ordinary chicken with a name and a habit. Nothing about it is a new entity type: no registry
 * entry, no renderer, no model, and therefore nothing a vanilla client has to be taught before it
 * can see one. A player who finds it sees a white chicken called Golden Goose, and only works out
 * what it is by waiting.
 *
 * <p><b>Identified by a scoreboard tag, not by its name.</b> Names are player-editable - a name tag
 * and an anvil are all it takes to mint a fake - and a mob that lays gold because of what it is
 * called would be a duplication bug wearing a costume. Scoreboard tags survive saving and reloading,
 * cannot be set without commands, and are what {@code GoldenGooseMixin} actually checks.
 */
public final class GoldenGoose {

	private GoldenGoose() {}

	/** What marks a chicken as one of these, and the only thing that does. */
	public static final String TAG = "cloud_kingdoms_golden_goose";

	public static final Component NAME = Component.literal("Golden Goose");

	/**
	 * The classic white chicken. Pinned rather than left to the biome, because chickens pick a
	 * variant from where they hatch and a citadel over the wrong biome would produce a goose that is
	 * visibly not the white bird the story wants.
	 */
	private static final ResourceKey<ChickenVariant> TEMPERATE =
		ResourceKey.create(Registries.CHICKEN_VARIANT, Identifier.withDefaultNamespace("temperate"));

	/** Turns an already-spawned chicken into a golden goose. */
	public static void anoint(Chicken chicken) {
		chicken.addTag(TAG);
		chicken.setCustomName(NAME);
		chicken.setPersistenceRequired();

		chicken.level().registryAccess()
			.lookup(Registries.CHICKEN_VARIANT)
			.flatMap(registry -> registry.get(TEMPERATE))
			.ifPresent(chicken::setVariant);
	}
}
