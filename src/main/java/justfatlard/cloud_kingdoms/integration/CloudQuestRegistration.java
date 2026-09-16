package justfatlard.cloud_kingdoms.integration;

import justfatlard.cloud_kingdoms.CloudKingdoms;
import justfatlard.cloud_kingdoms.block.ModBlocks;
import justfatlard.cloud_kingdoms.entity.GoldenGoose;
import justfatlard.cloud_kingdoms.item.ModItems;
import justfatlard.village_quests.api.DialogueRegistry;
import justfatlard.village_quests.api.QuestRegistry;
import justfatlard.village_quests.quest.FetchItemQuest;
import justfatlard.village_quests.quest.VillagerQuest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * What a village says about the sky, when there is something in it.
 *
 * <p>The mod's own rule is that from the ground a kingdom is weather and
 * nothing else. Everything here is written from under that line. No villager
 * knows there is a place up there, none of them says cloud kingdom, and none
 * of them is going to find out: they report what they can actually see from a
 * field, which is rain that falls on one paddock, smoke with nothing under it,
 * and a cloud that has not moved since they were a child. They are wrong about
 * the cause in a way that is exactly right about the observation, which is the
 * register Village Quests keeps for the things its villagers half-notice.
 *
 * <p>Everything is gated on the thing being real. The rain lines need a tarn
 * actually overhead; the smoke lines need a forge; the beanstalk lines and the
 * one errand need a stalk actually standing in the village, which only happens
 * because somebody planted a bean in town. A village with clear sky over it and
 * no stalk in the fields has nothing extra to say, and says nothing.
 *
 * <p>Names Village Quests types outright, so it must only be loaded behind the
 * isModLoaded guard in the entry point.
 */
public final class CloudQuestRegistration {
	private CloudQuestRegistration() {}

	/**
	 * Only two tiers give themselves away from below, and they are the only two
	 * spoken about by name of what they do. Fluid sitting in cloud leaks, so a
	 * tarn rains out of its underside and a forge smokes out of its own. The
	 * other five are invisible against the cloud layer, so the village has
	 * nothing to report about them and is given nothing to say.
	 */
	private static final ResourceKey<Structure> TARN = structure("cloud_tarn");
	private static final ResourceKey<Structure> FORGE = structure("cloud_forge");

	/** Every tier, for the one line that works on all of them: it does not drift. */
	private static final List<ResourceKey<Structure>> ALL_TIERS = List.of(
		structure("cloud_bank"), TARN, structure("cloud_spire"), FORGE,
		structure("cloud_homestead"), structure("cloud_citadel"), structure("cloud_wreck"));

	private static ResourceKey<Structure> structure(String path) {
		return ResourceKey.create(Registries.STRUCTURE,
			Identifier.fromNamespaceAndPath(CloudKingdoms.MOD_ID, path));
	}

	/**
	 * How far a kingdom can sit from the village and still be the thing raining
	 * on its fields. A tarn is about ninety blocks across, so one whose centre is
	 * further out than this is somebody else's weather.
	 */
	private static final int OVERHEAD_CHUNKS = 6;

	/** How far out from a villager a stalk in town could be. The village, not its outfields. */
	private static final int STALK_RADIUS = 24;

	/** A remark about the sky, sometimes. One that is always on the list is a menu, not a remark. */
	private static final double REMARK_CHANCE = 0.3;

	private static final long DAY_TICKS = 24000L;

	private record Sighting(long tick, boolean present) {}

	private static final Map<UUID, Sighting> STALKS = new ConcurrentHashMap<>();
	private static final Map<Long, Optional<BlockPos>> OVERHEAD = new ConcurrentHashMap<>();

	public static void register() {
		registerErrand();
		registerRemarks();
		CloudKingdoms.LOGGER.info("Registered cloud remarks with Village Quests");
	}

	// ---------------------------------------------------------------
	// What is actually there
	// ---------------------------------------------------------------

	/**
	 * A beanstalk standing in this villager's village. Read once a day per
	 * villager: it is a plane of block reads, which is cheap enough once and
	 * silly enough to repeat every time somebody says hello.
	 *
	 * <p>One horizontal plane finds it, because a stalk runs unbroken from the
	 * ground it was planted on to ten blocks above the cloud layer, so it passes
	 * through every height between. Reading it a little above the villager's own
	 * feet keeps the scan out of the ground.
	 */
	private static boolean stalkInVillage(Villager villager) {
		if (!(villager.level() instanceof ServerLevel world)) return false;

		long now = world.getGameTime();
		Sighting seen = STALKS.get(villager.getUUID());
		if (seen != null && now - seen.tick() < DAY_TICKS) return seen.present();

		// Villagers die and the map would keep their answers forever. Nothing here is
		// worth remembering across a clear-out, so the cheapest sweep is the whole map.
		if (STALKS.size() > 512) STALKS.clear();

		BlockPos centre = villager.blockPosition().above(2);
		boolean found = false;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		outer:
		for (int dx = -STALK_RADIUS; dx <= STALK_RADIUS; dx++) {
			for (int dz = -STALK_RADIUS; dz <= STALK_RADIUS; dz++) {
				cursor.set(centre.getX() + dx, centre.getY(), centre.getZ() + dz);
				if (!world.hasChunkAt(cursor)) continue;
				if (world.getBlockState(cursor).is(ModBlocks.BEANSTALK)) {
					found = true;
					break outer;
				}
			}
		}

		STALKS.put(villager.getUUID(), new Sighting(now, found));
		return found;
	}

	/**
	 * The nearest kingdom of this tier, when it is close enough to be the thing
	 * over the village. Cached per region, because this is the search behind the
	 * locate command and nobody's greeting is worth running it twice.
	 */
	private static boolean overhead(Villager villager, ResourceKey<Structure> tier) {
		if (!(villager.level() instanceof ServerLevel world)) return false;

		BlockPos pos = villager.blockPosition();
		long key = ((long) (pos.getX() >> 9) << 40) ^ ((long) (pos.getZ() >> 9) << 8) ^ tier.hashCode();
		return OVERHEAD.computeIfAbsent(key, k -> {
			Optional<Holder.Reference<Structure>> holder = world.registryAccess()
				.lookup(Registries.STRUCTURE)
				.flatMap(registry -> registry.get(tier));
			if (holder.isEmpty()) return Optional.empty();

			BlockPos found = world.findNearestMapStructure(
				HolderSet.direct(holder.get()), pos, OVERHEAD_CHUNKS, false);
			return Optional.ofNullable(found);
		}).isPresent();
	}

	private static boolean anythingOverhead(Villager villager) {
		for (ResourceKey<Structure> tier : ALL_TIERS) {
			if (overhead(villager, tier)) return true;
		}
		return false;
	}

	private static boolean carryingBean(ServerPlayer player) {
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (stack.is(ModItems.MAGIC_BEAN)) return true;
		}
		return false;
	}

	/** A chicken with the tag, close enough that somebody has definitely noticed it. */
	private static boolean gooseNearby(Villager villager) {
		if (!(villager.level() instanceof ServerLevel world)) return false;

		return !world.getEntities(EntityTypeTest.forClass(Chicken.class),
			new AABB(villager.blockPosition()).inflate(24.0),
			chicken -> chicken.entityTags().contains(GoldenGoose.TAG)).isEmpty();
	}

	// ---------------------------------------------------------------
	// The errand
	// ---------------------------------------------------------------

	/**
	 * A stalk comes up through whatever is above the bean, overnight, and drops
	 * what it broke at the foot of itself rather than deleting it. So somebody in
	 * this village woke up to a hole and a pile, and the pile is not the same
	 * shape as the hole. That is a roof to patch, and it is the only thing the
	 * sky ever asks this village for.
	 */
	private static void registerErrand() {
		QuestRegistry.registerUniversalQuest(CloudQuestRegistration::offerPatch);
	}

	private static VillagerQuest offerPatch(Villager villager, String villagerName, int reputation, Random random) {
		if (reputation < 5 || random.nextDouble() >= 0.12) return null;
		if (!stalkInVillage(villager)) return null;

		Item roofing = roofingFor(villager);
		return new FetchItemQuest(villagerName, villager.getUUID(), roofing, 12, 4)
			.withAsk("That green thing came up through a roof on the north side in the night and took the roof with it. "
				+ "There's a family in there sleeping under a sky they did not ask for. Twelve of these and I'll close it back up.");
	}

	/** What this village roofs in, so the patch matches the house it goes on. */
	private static Item roofingFor(Villager villager) {
		if (!(villager.level() instanceof ServerLevel world)) return Items.OAK_PLANKS;

		String biome = world.getBiome(villager.blockPosition()).unwrapKey()
			.map(k -> k.identifier().getPath()).orElse("");
		// A desert village has no timber in its roofs and would not send anyone for planks.
		if (biome.contains("desert")) return Items.SMOOTH_SANDSTONE;
		if (biome.contains("taiga") || biome.contains("snowy") || biome.contains("grove")) return Items.SPRUCE_PLANKS;
		if (biome.contains("savanna")) return Items.ACACIA_PLANKS;
		if (biome.contains("jungle")) return Items.JUNGLE_PLANKS;
		return Items.OAK_PLANKS;
	}

	// ---------------------------------------------------------------
	// The remarks
	// ---------------------------------------------------------------

	/** Where a remark is allowed to come up at all. */
	private enum When { STALK, RAINS, SMOKES, STILL, BEAN, GOOSE }

	private record Node(String text, String walkAway, List<Branch> branches) {}

	private record Branch(String label, Node node) {}

	private record Topic(String id, int minReputation, When when, String question, Node tree) {}

	private static Node close(String text, String walkAway) {
		return new Node(text, walkAway, List.of());
	}

	private static Node node(String text, String walkAway, Branch... branches) {
		return new Node(text, walkAway, List.of(branches));
	}

	private static Branch then(String label, Node node) {
		return new Branch(label, node);
	}

	private static void registerRemarks() {
		topics("farmer", List.of(
			new Topic("ck_stalk_field", 0, When.STALK, "Where did that green thing come from?",
				node("Overnight. It was not there when I shut the gate and it was there when I opened it. "
						+ "Thick as my leg and it goes up further than I can follow it.",
					"I'll leave it be, then.",
					then("Did anyone see it happen?",
						close("Nobody. That is the part I keep going back to. A thing that size and it managed it quietly.",
							"Quietly. Right.")),
					then("What's at the top of it?",
						close("How would I know? I have a field. You can go and look, if looking up all day is how you spend yourself.",
							"Maybe I will.")))),

			new Topic("ck_rain_paddock", 0, When.RAINS, "Does it always rain over there?",
				node("On the far paddock, yes. Just that paddock. Not the field beside it, not the roof, not me stood between them. "
						+ "The grass there is a different green and always has been.",
					"Good grass, at least.",
					then("Since when?",
						close("Since my mother, and she had it off hers. Whatever it is, it is older than the complaining.",
							"Older than the complaining. Ha.")),
					then("Have you moved the animals under it?",
						close("In a dry summer, every year. They will not go. Sheep will stand in a drought and look at wet grass "
								+ "and choose the drought. Ask the shepherd, they will tell you the same and take longer about it.",
							"I'll ask them.")))),

			new Topic("ck_bean_pocket", 10, When.BEAN, "Do you know what this bean is?",
				node("*looks at it, then at you* I know what one of those did to the roof on the north side. Whatever you mean to do with it, "
						+ "do it in a field. Not in my field. A field.",
					"Understood. A field.",
					then("What happens if I plant it here?",
						close("Then in the morning there is a green pillar where my wheat was and you and I have a different conversation. "
								+ "Take it out past the wall.",
							"Out past the wall. Fine."))))));

		topics("shepherd", List.of(
			new Topic("ck_rain_grass", 0, When.RAINS, "The grass on that paddock looks different.",
				node("It is. Wetter, softer, comes up faster and the flock will not touch it. Not once, not in a dry year. "
						+ "They walk round it like there is a fence there.",
					"Animals know things.",
					then("What do you think is wrong with it?",
						close("Nothing is wrong with it. That is what bothers me. It is the best grass I own and the sheep have "
								+ "decided otherwise and sheep are not usually the ones being careful.",
							"Fair point.")))),

			new Topic("ck_goose", 20, When.GOOSE, "There's a chicken here with a name on it.",
				node("There is. Turned up on its own, walked in like it had been here before, and it is not one of mine. "
						+ "I have counted mine twice.",
					"Somebody's, then.",
					then("Whose do you think it is?",
						close("Nobody's, I think. It does not act like a bird that was kept. It acts like a bird that came down. "
								+ "I have stopped asking and started leaving it grain.",
							"Leave it the grain."))))));

		topics("weaponsmith", List.of(
			new Topic("ck_smoke", 0, When.SMOKES, "There's smoke over the ridge.",
				node("Most days. And nothing under it burning, before you ask. I walked out to look the once. "
						+ "No fire, no camp, no scorch. Walked back.",
					"Strange.",
					then("Could it be a forge?",
						close("Whose? Mine is here and it is cold half the week. If somebody is working out there they are doing it "
								+ "without wood, without ore and without ever coming in to sell anything, which is not a smith. "
								+ "That is a man with a hobby and a secret.",
							"A hobby and a secret. Ha."))))));

		topics("cartographer", List.of(
			new Topic("ck_still", 30, When.STILL, "You look at the sky a lot.",
				node("I draw what does not move, which is most things. The clouds move. All of them, west, every day of my life. "
						+ "Except the one over the east field. That one has been over the east field since I was a boy.",
					"Maybe you're misremembering.",
					then("You're sure it's the same one?",
						close("It has a notch out of the south side like a bite. I have drawn it forty times. It is the same notch. "
								+ "I do not put it on the maps I sell, because a cloud on a map is a thing people ask about.",
							"I won't ask further.")),
					then("What do you think it is?",
						close("A cloud that will not move. I am a mapmaker. I write down where things are and I do not "
								+ "make up why. *goes back to the table* That one has been the hardest discipline of my life.",
							"Keep drawing it."))))));

		topics("librarian", List.of(
			new Topic("ck_still_books", 30, When.STILL, "Is there anything in the books about the sky?",
				node("There is a line in the parish record, four keepers back, about a white thing over the east field "
						+ "that the writer says has been there since their own predecessor. That is as far back as we go, "
						+ "and it was already old.",
					"So it predates the records.",
					then("Nobody went up to look?",
						close("With what? *sets the book down* Every generation writes the same sentence and passes it on. "
								+ "We are a village of people who noted it down and got on with the harvest. I include myself.",
							"Somebody should look."))))));

		topics("mason", List.of(
			new Topic("ck_stalk_hole", 0, When.STALK, "What did that thing do to the roof?",
				node("Went straight through it. Not a crack, not a lean, straight through, like the roof was not an opinion it had to consider. "
						+ "And it put the pieces down neatly at the foot of itself, which somehow bothers me more.",
					"Neatly.",
					then("Can you patch it?",
						close("I can patch anything. What I cannot do is patch it while the thing is standing in the hole, "
								+ "so first somebody cuts it down, and cutting it gives you nothing, so nobody wants to.",
							"I'll see about that."))))));
	}

	/**
	 * One profession's remarks. Of the ones whose condition is actually met, at
	 * most one is offered, and only sometimes, so the sky comes up the way weather
	 * comes up rather than as a list of things to ask about it.
	 */
	private static void topics(String profession, List<Topic> topics) {
		for (Topic topic : topics) {
			DialogueRegistry.registerRichDialogueHandler(topic.id(),
				(villager, player, id) -> reply(topic.tree()));
		}

		DialogueRegistry.registerProfessionDialogue(profession, (villager, player, reputation) -> {
			ThreadLocalRandom rng = ThreadLocalRandom.current();
			if (rng.nextDouble() >= REMARK_CHANCE) return List.of();

			List<Topic> fitting = new ArrayList<>();
			for (Topic topic : topics) {
				if (reputation < topic.minReputation()) continue;
				boolean fits = switch (topic.when()) {
					case STALK -> stalkInVillage(villager);
					case RAINS -> overhead(villager, TARN);
					case SMOKES -> overhead(villager, FORGE);
					case STILL -> anythingOverhead(villager);
					case BEAN -> carryingBean(player);
					case GOOSE -> gooseNearby(villager);
				};
				if (fits) fitting.add(topic);
			}
			if (fitting.isEmpty()) return List.of();

			Topic picked = fitting.get(rng.nextInt(fitting.size()));
			return List.of(new DialogueRegistry.DialogueOption(picked.id(),
				Component.literal(picked.question()), picked.minReputation(), Integer.MAX_VALUE));
		});
	}

	/** A tree node as a Village Quests reply: the line, its exit, and a handler per follow-up. */
	private static DialogueRegistry.Reply reply(Node node) {
		DialogueRegistry.Reply reply = DialogueRegistry.Reply.of(node.text());
		if (node.walkAway() != null) reply.walkAway(node.walkAway());
		for (Branch branch : node.branches()) {
			reply.option(branch.label(), (villager, player, id) -> reply(branch.node()));
		}
		return reply;
	}
}
