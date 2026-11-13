package justfatlard.cloud_kingdoms.block;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.storage.SerializableChunkData.SectionData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Recognises a saved chunk lit while cloud still blocked the sky.
 *
 * <p>A chunk saves its light, and loading trusts what was saved, so a kingdom lit back when cloud
 * was opaque would keep its shadow - and the mob spawns under it - after cloud stopped casting one.
 * {@code StaleCloudLightMixin} asks this on every chunk read, and a stale chunk is loaded without
 * its saved sky light and marked unlit, so vanilla lights it from scratch the way it lights a chunk
 * fresh from worldgen.
 *
 * <p><b>The stale light is its own marker.</b> Open air directly under cloud that has nothing but
 * more cloud and then sky above it is lit by the current rules, and was black under the old ones.
 * So a chunk is stale when such a column saved zero, and a chunk saved after relighting no longer
 * is. Nothing extra is written to remember which chunks are done, and a chunk with no cloud on the
 * layer costs one palette lookup.
 *
 * <p><b>Why at read and not after load.</b> Relighting a loaded chunk loses a race it cannot see:
 * the saved light for a section waits in the light engine's queue until the engine starts storing
 * that section, which for open air under a deck can be seconds later, and when it lands it writes
 * the old shadow over the relight. Not queueing it is the only ordering that holds.
 */
public final class StaleCloudLight {

	private StaleCloudLight() {}

	public static boolean isStale(List<SectionData> saved) {
		Map<Integer, SectionData> sections = new HashMap<>();
		for (SectionData section : saved) sections.put(section.y(), section);

		int y = CloudBlock.SETTLE_Y;
		LevelChunkSection layer = blocks(sections, y);
		if (layer == null || !layer.maybeHas(state -> state.is(ModBlocks.CLOUD))) return false;

		SectionData under = sections.get(SectionPos.blockToSectionCoord(y - 1));
		if (under == null || under.skyLight() == null) return false;
		int underY = SectionPos.sectionRelative(y - 1);

		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				if (!state(sections, x, y, z).is(ModBlocks.CLOUD)) continue;
				if (!openToSky(sections, x, y, z)) continue;
				if (!state(sections, x, y - 1, z).isAir()) continue;
				if (under.skyLight().get(x, underY, z) == 0) return true;
			}
		}
		return false;
	}

	/** Nothing above the cloud at {@code y} but more cloud and then air. */
	private static boolean openToSky(Map<Integer, SectionData> sections, int x, int y, int z) {
		while (state(sections, x, y, z).is(ModBlocks.CLOUD)) y++;
		return state(sections, x, y, z).isAir();
	}

	/** A section that was not saved is empty, so reads as air - which is also where the sky starts. */
	private static BlockState state(Map<Integer, SectionData> sections, int x, int y, int z) {
		LevelChunkSection section = blocks(sections, y);
		if (section == null) return Blocks.AIR.defaultBlockState();
		return section.getBlockState(x, SectionPos.sectionRelative(y), z);
	}

	private static LevelChunkSection blocks(Map<Integer, SectionData> sections, int y) {
		SectionData section = sections.get(SectionPos.blockToSectionCoord(y));
		return section == null ? null : section.chunkSection();
	}
}
