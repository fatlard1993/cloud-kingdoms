package justfatlard.cloud_kingdoms.worldgen;

import justfatlard.cloud_kingdoms.CloudKingdoms;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.fabricmc.fabric.api.event.registry.RegistryAttributeHolder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * Puts the structure type and the piece type into the built-in registries.
 *
 * <p>The structures themselves are datapack objects: the five tiers, their biome lists and their
 * spacing all live in {@code data/.../worldgen/}. What has to be registered in code is only the pair
 * of codecs those files are deserialized through.
 */
public final class CloudStructureRegistration {

	private CloudStructureRegistration() {}

	public static StructureType<CloudKingdomStructure> CLOUD_KINGDOM;
	public static StructurePieceType CLOUD_KINGDOM_PIECE;

	public static void register() {
		CLOUD_KINGDOM_PIECE = Registry.register(
			BuiltInRegistries.STRUCTURE_PIECE,
			Identifier.fromNamespaceAndPath(CloudKingdoms.MOD_ID, "cloud_kingdom"),
			(context, tag) -> new CloudKingdomPiece(tag));

		CLOUD_KINGDOM = Registry.register(
			BuiltInRegistries.STRUCTURE_TYPE,
			Identifier.fromNamespaceAndPath(CloudKingdoms.MOD_ID, "cloud_kingdom"),
			() -> CloudKingdomStructure.CODEC);

		// Marked optional so a client that does not have this mod is not dropped during Fabric's
		// config-phase registry sync. The blocks still need Pandorical; these two do not.
		RegistryAttributeHolder.get(BuiltInRegistries.STRUCTURE_TYPE).addAttribute(RegistryAttribute.OPTIONAL);
		RegistryAttributeHolder.get(BuiltInRegistries.STRUCTURE_PIECE).addAttribute(RegistryAttribute.OPTIONAL);
	}
}
