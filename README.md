# Cloud Kingdoms

Clouds you can land on.

From the ground they are weather, and nothing else. The undersides are cut flat
at y=192, which is where the client already draws its cloud layer, so a kingdom
overhead reads as the same white ceiling as everything else up there. Finding one
means `/locate` or flying up to look - with one exception, below.

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

## The four tiers

| | Cloud | What is on it | Who is there |
|---|---|---|---|
| **Bank** | 30 blocks across | A cairn, a broken arch, 1-2 chests | 1-3 breezes |
| **Tarn** | 38 blocks | Ponds cut into the deck, lily pads, 1-2 chests | 3-8 axolotls, nothing hostile |
| **Spire** | 42 blocks | A ruined watchtower, arches, 1-3 chests, usually a vex spawner | 3-10: vexes, breezes, sometimes horsemen |
| **Citadel** | 64 blocks | A pillar ring, a sealed vault, a breeze spawner | 10-23: a giant, charged creepers, shulkers, horsemen, vexes |

A tarn is the odd one out twice over: it is the only tier with nothing hostile
on it, and the only one you can find from the ground. Water sitting in cloud
leaks, so the underside of a tarn **rains** - the cloud is invisible against the
cloud layer but the weather falling out of it is not.

Counts roll per kingdom rather than being fixed, so the second spire you visit
does not tell you what the third holds. A spire can come out with no horsemen at
all; a citadel always has exactly one giant, because a citadel without its
colossus is missing the thing it is built around.

Banks are common on purpose. Most clouds you fly to should be a place to stand
and a small find, so that a spire on the horizon still means something. Spacing
is 32 chunks for banks (about a village's worth), 56 for tarns, 72 for spires and
160 for citadels, all in `data/.../worldgen/structure_set/`.

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

There is no NBT in this repository and no structure templates. Every kingdom is
a function of its seed:

- The **cloud mass** is a handful of squashed spheroids summed into a density
  field, thresholded, then bitten into by value noise so the outline is lobed
  rather than round. Flat underneath, billowing on top.
- The **ruins** are drawn by rule and then holed, with the chance of losing a
  block rising with height, because that is how buildings actually fail. Towers
  come out with different heights, different jagged rims, different doorways.
- The **walls are mostly cloud**. A kingdom is built out of the cloud it stands
  on, with quartz or End stone mixed through the bulk at about a third. The
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
