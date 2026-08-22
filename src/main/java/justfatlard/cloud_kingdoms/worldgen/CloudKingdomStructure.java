package justfatlard.cloud_kingdoms.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import justfatlard.cloud_kingdoms.block.CloudBlock;
import justfatlard.cloud_kingdoms.gen.Kingdom;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * A cloud kingdom, anchored to the sky rather than to the ground.
 *
 * <p>This is the whole reason the mod does not use a jigsaw or a template: every other structure in
 * the game asks the chunk generator where the terrain is and sits on the answer. A cloud does not
 * care. {@link #findGenerationPoint} does no heightmap query, no slope test and no water check,
 * because there is nothing underneath a cloud that could disqualify it. It hands back the chunk
 * centre at {@link CloudBlock#SETTLE_Y} and lets the placement in the structure set decide density.
 *
 * <p>The matching half of that decision lives in the JSON: {@code terrain_adaptation} must stay
 * {@code none}. Any other value turns the beardifier loose on a bounding box floating at y=192 and
 * it will carve the terrain 130 blocks below into a crater trying to make room for a cloud.
 */
public class CloudKingdomStructure extends Structure {

	public static final MapCodec<CloudKingdomStructure> CODEC = RecordCodecBuilder.mapCodec(instance ->
		instance.group(
			Structure.settingsCodec(instance),
			ExtraCodecs.NON_EMPTY_STRING
				.fieldOf("tier")
				.forGetter(structure -> structure.kingdom.id())
		).apply(instance, CloudKingdomStructure::new));

	private final Kingdom kingdom;

	public CloudKingdomStructure(StructureSettings settings, String tier) {
		super(settings);
		this.kingdom = Kingdom.byId(tier);
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		BlockPos origin = new BlockPos(
			context.chunkPos().getMiddleBlockX(),
			CloudBlock.SETTLE_Y,
			context.chunkPos().getMiddleBlockZ());

		// Drawn here and carried on the piece rather than re-derived at placement time: the piece's
		// postProcess runs once per overlapping chunk and every one of those calls has to rebuild
		// the identical cloud.
		long shapeSeed = context.random().nextLong();

		return Optional.of(new GenerationStub(origin, builder ->
			builder.addPiece(new CloudKingdomPiece(kingdom, origin, shapeSeed))));
	}

	@Override
	public StructureType<?> type() {
		return CloudStructureRegistration.CLOUD_KINGDOM;
	}
}
