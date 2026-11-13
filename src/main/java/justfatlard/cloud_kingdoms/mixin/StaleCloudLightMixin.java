package justfatlard.cloud_kingdoms.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import justfatlard.cloud_kingdoms.block.StaleCloudLight;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Loads a chunk lit under the old opaque-cloud rules as if it had never been lit: its saved sky
 * light is dropped and the chunk is marked unlit, so the light stage relights it from scratch. See
 * {@link StaleCloudLight} for the test and for why this happens at read rather than after load.
 *
 * <p>Block light is still loaded: cloud never changed how it travels, and the relight only adds to it.
 */
@Mixin(SerializableChunkData.class)
public abstract class StaleCloudLightMixin {

	@Shadow @Final private List<SerializableChunkData.SectionData> sectionData;

	/** Decided once at the top of a read, consulted by both wraps below. */
	@Unique
	private static final ThreadLocal<Boolean> STALE = ThreadLocal.withInitial(() -> false);

	@Inject(method = "read", at = @At("HEAD"))
	private void cloudKingdoms$decide(CallbackInfoReturnable<?> cir) {
		STALE.set(StaleCloudLight.isStale(sectionData));
	}

	@WrapOperation(method = "read", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/lighting/LevelLightEngine;queueSectionData(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/level/chunk/DataLayer;)V"))
	private void cloudKingdoms$dropStaleSky(LevelLightEngine engine, LightLayer layer, SectionPos pos,
			DataLayer data, Operation<Void> original) {
		if (layer == LightLayer.SKY && STALE.get()) return;
		original.call(engine, layer, pos, data);
	}

	@WrapOperation(method = "read", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/chunk/ChunkAccess;setLightCorrect(Z)V"))
	private void cloudKingdoms$markUnlit(ChunkAccess chunk, boolean lit, Operation<Void> original) {
		original.call(chunk, lit && !STALE.get());
	}
}
