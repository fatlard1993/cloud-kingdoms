# Cloud Kingdoms - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

## Installation

Server-side, with Pandorical on the client. The cloud block is registered
through Pandorical's content sync, so a connecting player needs Pandorical and
nothing else; the worldgen registries are marked optional, so nothing else about
the mod asks anything of the client.

## How a kingdom is generated

There is no NBT in this repository. Every kingdom is a function of its seed, and every ruin is drawn
by rule (`gen/Ruins.java`) rather than stamped from a template, with two exceptions that stamp
templates vanilla already ships: the wreck (`end_city/ship`) and the ruined portals on forges and
homesteads (`ruined_portal/portal_1` to `portal_10`, run through vanilla's blackstone conversion).

- **Cloud mass** (`gen/CloudField.java`): a handful of squashed spheroids summed into a density
  field, thresholded, then bitten into by value noise so the outline is lobed. Flat underneath at
  y=192 (`CloudBlock.SETTLE_Y`, matching the overworld's 192.33 cloud render height), billowing on
  top. The field is sampled once per 6-block cell horizontally, half the 12-block texel the vanilla
  cloud texture is drawn at, so the edge steps at right angles. The top keeps full per-block relief
  so ruins have footings and the tarn and forge have a surface to cut basins into.
- **Ruins** are drawn and then holed, with the chance of losing a block rising with height
  (`Kingdom.erosion`, raised towards the top of pillars). The homestead's erosion is near zero.
- **Walls** are mostly cloud, with quartz, End stone or blackstone mixed through at about a third
  depending on tier (`gen/Palette.java`). Floors, vault chambers, bars and rods are placed as
  themselves. The open deck is cloud only.
- **Wreck**: the ship is set into the deck so the template's own air blocks dig its hole, eroded
  with the shared rule, and a furrow is gouged back from the stern.
- Generated decks sit entirely at or above `SETTLE_Y`, so laying one down schedules no block ticks.

## Entities

- **Giant** (`mixin/GiantAiMixin.java`): goals are installed in the constructor rather than at spawn
  time, because goals added at spawn do not survive a chunk unload. This applies to every giant.
  Base speed is set to 0.24.
- **Golden goose** (`entity/GoldenGoose.java`, `mixin/GoldenGooseMixin.java`): a vanilla chicken
  with the scoreboard tag `cloud_kingdoms_golden_goose`, pinned to the temperate variant. The mixin
  checks the tag, not the name, so a name tag cannot make one.
- **Homestead piglins** are spawned with `setImmuneToZombification(true)`.

## Cloud block

Rise is a scheduled block tick, not a falling-block entity, so vanilla clients need nothing beyond
Pandorical's block sync. A connected mass is gathered (faces only, up to 2,048 blocks), tested as a
whole and moved in one tick, driven by its lowest, most north-west block. Riders are teleported to an
absolute height with an upward impulse so the ride is smooth. Leak particles are emitted server-side
from random ticks, because the client runs a Pandorical stand-in, not `CloudBlock`.
