# Cloud Kingdoms

Clouds you can land on.

From the ground they are weather, and nothing else. The undersides are cut flat
at y=192, which is where the client already draws its cloud layer, and the
outlines are squared off to a 6-block grid, half the step the layer itself is drawn on, so a
kingdom overhead reads as the same white ceiling as everything else up there -
right angles and all. Finding one means `/locate` or flying up to look - with
two exceptions, below.

Everything above that line is somewhere to be.

## Magic beans

The way up, for anyone without an elytra.

A **magic bean** turns up rarely in ground-level chests - village houses,
shipwreck supplies, pillager outposts, jungle temples, desert pyramids - and a
wandering trader will part with one for 24 emeralds. They are deliberately not in
the cloud chests: the bean is how you reach a cloud, so putting the only source
up there is a locked room with the key inside it.

Plant one on the ground and it does nothing until nightfall. Over that night it
grows a **beanstalk** straight up to y=202, ten blocks clear of the cloud layer,
through anything in the way - stone, your roof, a kingdom's deck. Blocks it
breaks are dropped rather than deleted, so what it smashes ends up at the bottom
rather than gone.

It grows only if the roots have **water within four blocks, or open sky**. Either
one. Outdoors the sky is enough; underground or indoors you need a bucket, and
then it punches up through the ceiling that was blocking the sky in the first
place. Once it has started it runs to the top without stopping at dawn, so
sleeping through does not strand a half-height stalk.

The stalk is climbable and drops nothing when cut.

## Rafts

Cloud below the layer rises until something stops it, and a connected mass rises **as one thing** - laid a block at a time or a hundred at once, it leaves the ground together and arrives together. Build a deck under the line and the whole deck lifts.

Three things stop it. The layer itself, at y=192, where buoyancy switches off entirely so the ceiling is somewhere you can build. Anything solid overhead, until that moves. And **slime or honey touching any block of it** - one block is enough to moor a whole raft, because the mass only rises if every block of it can.

## The seven tiers

| | Cloud | What is on it | Who is there |
|---|---|---|---|
| **Bank** | ~70 blocks across | Cairns marking buried caches, arches, 2-5 chests, a buried breeze spawner, sometimes a caged allay | 5-10: breezes, sometimes an illusioner, and nowhere to hide from either |
| **Tarn** | ~90 blocks | Ponds cut into the deck, lily pads, 2-3 chests | 5-15: axolotls in the water, frogs on the pads, sometimes an allay or two, nothing hostile |
| **Spire** | ~95 blocks | A ruined watchtower, arches, 2-4 chests, usually a vex spawner | 5-14: vexes, breezes, sometimes horsemen or an illusioner |
| **Forge** | ~115 blocks | A blackstone smithy, lava basins cut into the deck, 3-4 chests, a blaze spawner, sometimes a ruined portal | 6-13: blazes, magma cubes, striders in the lava |
| **Homestead** | ~100 blocks | A piglin family's hut, a nether wart farm, the broken portal they arrived through, 2-3 chests | 4-8 piglins, children among them |
| **Citadel** | ~140 blocks | A pillar ring, a sealed vault, 4-6 chests, a breeze spawner | 11-25: a giant, charged creepers, shulkers, horsemen, vexes, illusioners |
| **Wreck** | ~105 blocks | Vanilla's End ship, buried in the furrow it ploughed, 3-4 deck chests | 2-5: breezes, sometimes an illusioner, plus the ship's own three shulkers |

**Two tiers give themselves away from the ground.** Fluid sitting in cloud leaks,
so a tarn **rains** out of its underside and a forge **smokes** out of its own -
the cloud is invisible against the cloud layer but what falls out of it is not.
They are the two that cut bowls into their deck instead of building on top of it,
and they are opposites in every other respect: a tarn is the only tier with
nothing hostile on it, and a forge is a floor you can fall through.

Counts roll per kingdom rather than being fixed, so the second spire you visit
does not tell you what the third holds. A spire can come out with no horsemen at
all; a citadel always has exactly one giant, because a citadel without its
colossus is missing the thing it is built around.

**There is no throwaway tier.** Getting onto any cloud at all costs a magic bean
or an elytra, so the cheapest kingdom in the sky is still somewhere you went out
of your way to reach, and it has to pay for the trip on its own terms rather than
by being a stepping stone to a spire. A bank is smaller and less built than a
citadel; it is not a lesser visit.

Banks are still the common one. Spacing is 64 chunks for banks, 112 for tarns,
144 for spires, 208 for forges, 288 for homesteads, 320 for citadels and 384 for
wrecks, all in `data/.../worldgen/structure_set/`.

## The bank

Nothing is built on a bank. That is the tier's whole identity and the reason it
cannot be handed a tower to make it worth the flight, so the find is put *under*
the deck instead - and the **cairns are the map**.

A stack of stones on an otherwise empty shelf is the only thing telling you where
to dig. Better than half of them have a chest beneath, sunk under a single block
of cloud: one swing of a shovel, not a search. Not all of them, because a marker
that always pays is a checklist; not a quarter of them, because someone who opens
three empty holes stops checking and walks past the one that mattered.

One cairn near the middle is taller than the rest, and what is under that one is
a **sealed cell with a breeze spawner in it**.

About one bank in four also has a **hutch** standing out on the deck - a small box
of iron bars on a stone plinth, with an **allay** inside it. Nothing says who
carried it up or why they left it, and the mod never answers that. Break the bars
and it is yours.

The garrison is breezes and nothing else, in numbers, on the one tier with
nothing built on it to duck behind. Every wind charge is aimed at someone
standing on an open shelf with a hundred and thirty blocks under the edge, and
the cloud does not hurt to land on but the ground does.

## The forge

The one kingdom that is not white, and the only place in the overworld sky where
the Nether shows through.

A **smithy** of blackstone and basalt stands in the middle of it, walls eroded
down to a shell, with a pan of lava sunk flush into its floor and a magma ring
around that as the only warning you get. A damaged anvil sits across the shop
floor from the fire. The back third of the hall is a sealed room with no door and
a **blaze spawner** in it, which is the same idea as the citadel's vault chamber
at a smithy's scale: you walk into a room that is obviously missing its back
wall, and what is behind it is lit by nothing.

Out on the deck are **lava basins**, cut the same way a tarn's ponds are and by
the same code - surveyed for a level rim, dug as a bowl, left with a cloud floor
so they leak. **Striders** wade in them, which is the forge's answer to the
tarn's axolotls: harmless, native to the material, and the one thing on the deck
that is there because it likes it. The chests carry a saddle and a warped fungus
on a stick often enough that taking one home is a plan rather than an accident.

One wall block in ten is **gilded blackstone**. A forge is the one tier worth
stripping rather than only looting.

## The hole it came through

About two forges in five have a **ruined portal** somewhere out on the deck.

These are vanilla's, whole - ten of them, `portal_1` through `portal_10`, stamped
straight out of the game's own structure files. The broken arch, the scattered
netherrack, the gold blocks and a chest full of ruined-portal loot are all in the
template already; none of it is written here.

They arrive through vanilla's **blackstone** conversion, the same one it uses for
ruined portals in the Nether itself, so a forge's portal is built out of the same
stone as its smithy. That is the point rather than a coincidence: the tier's whole
fiction is a smithy the Nether burnt up through, and this is the hole it came
through.

They are set a block or two into the deck, because a ruined portal sitting flat on
the surface reads as a model of one rather than as something that has been there.

Some of them come out with a complete frame, which vanilla decides and this mod
does not. If yours does, it will light - and a portal at y=192 in the overworld
comes out around y=24 in the Nether.

The **homestead** uses the same machinery and always has one, because on that tier
the portal is not scenery: it is how the residents got there.

What the chests hold is what a smithy would have: blaze rods, magma cream, fire
charges, gold, and at the bottom of the rare pool the two things that are
otherwise a trip to the Nether - ancient debris and netherite scrap.

Blaze rods above the clouds are deliberate, and they are the same move the
citadel already makes with shulker shells: a tier whose material has no business
being in the overworld, found somewhere with no explanation attached.

## The homestead

The only tier anybody lives on, and the only one that is not a ruin.

A piglin family built a portal, came through it, and came out on a cloud. They
liked it enough to stay: they carried their timber up, carried their soil up, put
a roof on, planted a farm, and broke the portal behind them.

You can read the whole thing in the order it happened. The **portal** is off on
its own with the farm between it and the house - they arrived, walked away, and
did not go back. The **hut** is crimson timber on blackstone footings, the one
structure in the mod not built out of the cloud it stands on, because they
brought it with them. The **farm** is nether wart and fungus, because that is
what they know how to grow.

**Nothing here has fallen down.** Every other tier is drawn as something that
failed, and the erosion rule is what sells it. On a homestead that number is
turned almost all the way off, and it is the clearest thing about the place: the
walls stand because somebody is keeping them standing.

They are "friendly" the way piglins are friendly. **Wear gold and you are a
guest**; arrive without it, or break the gold block sitting in their front room,
and you are a stranger robbing a family in front of their children. The mod adds
nothing to arrange that - it is the vanilla behaviour, put somewhere you will
arrive by air with no way to back out quickly.

They do not turn, either. Piglins zombify anywhere but the Nether, so a homestead
left to the default would be a homestead of zombified piglins before anyone ever
saw it. The flag that stops it is vanilla's own, and it is also the fiction: these
are the ones who broke ties, and the game's word for that is that they no longer
change.

## The wreck

The rarest of the six, and the only one that is not a place. The others are
somewhere the sky put something. This is somewhere something arrived, badly.

**The ship is vanilla's.** `end_city/ship` is a real End ship that the game
already ships as a structure template - the dragon head on the prow, the ladder
up the mast, the brewing stand of healing potions, the two treasure chests, the
three shulkers and the elytra hanging in its frame, all exactly where Mojang put
them. Drawing a worse hull by hand would have been more code and a standing
promise to keep it looking like an asset it is not.

What this mod adds is the crash, which vanilla has no notion of:

- **The keel is buried.** The ship is set into the deck rather than onto it, and
  the template's own air blocks do the digging, so the hull arrives in a hole
  exactly its own shape.
- **The hull is broken.** A share of it is dropped on the way in, which is the
  same erosion rule every other ruin here gets, borrowed rather than rewritten.
- **It ploughed to get there.** A furrow is gouged back from the stern, deepest
  where the ship stopped and feathering out to nothing where it first touched
  down, with pieces of hull shed along it. That trench is the only part of the
  tier drawn by rule, and it is the part that says crash rather than parked.

There is exactly one ship template in the game, so what keeps two wrecks apart is
which way it is facing, how deep it dug in, how much of it survived, and the
shape of its own furrow.

The ship's two chests hold End city treasure, because they are End city chests.
The chests out on the deck are this mod's, and hold what the cloud caught.

## Cloud substrate

The mod adds one block, `cloud`, and it is the mod's other idea.

It is **sand with the sign flipped**. Below y=192 a cloud block with nothing
holding it drifts upward, one block at a time, until it reaches the layer or
runs into something solid. Place one at sea level and watch it go home.

Only three things hold it down: the layer itself, anything solid directly
overhead, and **anything sticky** - slime and honey out of the box, via the
`anchors_cloud` block tag, which a datapack can extend.

A rising block **takes its cargo with it**. Anything standing in the space it
moves into is set down on its new top, and a fluid source there is carried up
rather than swallowed - so a raft lifts its passengers instead of sliding out
from under them, and sailing a pond upward does not drink it.

Cloud also **leaks**. Any cloud block with water above it in unbroken cloud, and
air below, drips. That is the whole mechanism behind a tarn's rain, and it works
on anything you build: sink a pond into a deck you made and it will rain under
that too.

Cloud does *not* anchor cloud. Build a raft of it below the line and the whole
raft lifts, every block leaving together.

Sticky moors **per block**, not per raft: one slime block under a corner holds
that corner and lets the rest go. Holding a whole raft down means sticky contact
under all of it.

At or above y=192 buoyancy is off entirely: up there it is an ordinary white
building block, so the cloud layer is somewhere you can actually build. That
split is the whole design. Below the line the block has a mind of its own;
above it, you do.

Landing on cloud does **no fall damage at all**, from any height. It drops
**only to Silk Touch**; without it, a cloud mined is a cloud gone.

## Generated, not modelled

There is no NBT in this repository. Every kingdom is a function of its seed, and
every ruin in the mod is drawn by rule rather than stamped from a model - with
one deliberate exception, the wreck, which stamps a template the game already
ships rather than re-drawing a worse copy of it.

- The **cloud mass** is a handful of squashed spheroids summed into a density
  field, thresholded, then bitten into by value noise so the outline is lobed
  rather than regular. Flat underneath, billowing on top, and **squared off in
  plan**: the field is sampled once per 12-block cell rather than once per
  block, so a cell is cloud or sky as a whole and the edge steps at right
  angles instead of curving. Twelve is the client's own number - the vanilla
  cloud texture is drawn at twelve blocks to the texel - so a kingdom breaks on
  the same interval as the layer it is hiding in, and a tier is about as many
  cells across as vanilla would spend on a cloud that size.
- **The squaring is horizontal only.** The top keeps its full per-block relief,
  because the deck is a place to stand: ruins need footings at different
  heights and the tarn and forge need a surface worth cutting a basin into. A
  kingdom flattened to a constant-thickness slab would be truer to the clouds
  overhead and much worse to arrive on.
- The **ruins** are drawn by rule and then holed, with the chance of losing a
  block rising with height, because that is how buildings actually fail. Towers
  come out with different heights, different jagged rims, different doorways.
- The **walls are mostly cloud**. A kingdom is built out of the cloud it stands
  on, with quartz, End stone or blackstone mixed through the bulk at about a
  third, depending on the tier. The
  mineral fraction is not decoration: an all-cloud tower on an all-cloud deck has
  no edges, and it is what keeps the shape legible. Floors, the vault's inner
  chamber, bars and rods are placed as themselves, because those have to stay
  solid however the mix rolls.
- **Stone stops at the structures.** The open deck is cloud and nothing else - no
  scattered rubble, no crystals. If you are standing on something that is not
  cloud, somebody built it.

So no two clouds are the same shape, and nothing is a copy of anything.

## The giant

Vanilla registers `minecraft:giant` with a hundred hearts, fifty attack damage,
and no AI whatsoever. It cannot see you, walk, or swing. This mod gives it goals
and turns its absurd base speed down to a zombie's, so the thing guarding a
citadel is an encounter instead of a statue.

That applies to **every** giant, not only the ones on clouds. It has to: goals
added at spawn time do not survive a chunk unload, because a reloaded entity is
rebuilt through the constructor. Vanilla spawns no giants, so in practice this
reaches this mod's citadels and anything summoned by hand.

## The golden goose

About one citadel in five keeps a chicken beside its giant. It looks like an
ordinary white chicken and wears a name tag reading **Golden Goose**, and every
time an ordinary chicken would lay an egg, it lays gold nuggets instead.

It is not a new entity type - no registry entry, no model, nothing a client has
to be taught. It is a chicken with a scoreboard tag, and the tag is what the
gold-laying checks. Deliberately not the name: a name tag and an anvil are all it
would take to mint a fake, and a mob that lays gold because of what it is called
is a duplication bug in a costume.

## Finding one

```
/locate structure cloud-kingdoms-justfatlard:cloud_spire
```

Or build one where you are standing, which is what the tiers were tuned with:

```
/cloudkingdom citadel          # random seed
/cloudkingdom citadel 12345    # reproducible
```

## Installation

Server-side, with Pandorical on the client. The cloud block is registered
through Pandorical's content sync, so a connecting player needs Pandorical and
nothing else; the worldgen registries are marked optional, so nothing else about
the mod asks anything of the client.

## License

MIT, see [LICENSE](LICENSE).
