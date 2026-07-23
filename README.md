# Panoptic

A client-side, in-game inspection toolkit for Minecraft modpacks.

- **Inspector** - capture any block / item / entity / biome / fluid and see all its data (tags, states, NBT, properties) plus every asset/data file it comes from, across all mods and packs.
- **Seed Inspector** - a biome & structure map of your world for any seed and dimension.
- **Trade Inspector** - every villager trade in the entire pack, searchable by item.
- **Screen Inspector** - smart screenshots that remember the full data of the items on screen.

For dedicated servers, pair it with its server-side companion [Panopticon](https://github.com/Mokich/Panopticon) (world/trade data + permissions).

## Supported versions

| Minecraft | Forge | NeoForge | Fabric |
|:---:|:---:|:---:|:---:|
| 1.20.1 | ✅ | - | ✅ |
| 1.21.1 | ✅ | ✅ | ✅ |

## Repository layout

The repository is five self-contained Gradle projects, one per loader target, plus a composite root that builds them all:

```
Panoptic/
├── 1.20.1-forge/      ForgeGradle 6, Java 17
├── 1.21.1-forge/      ForgeGradle 6, Java 21
├── 1.21.1-neoforge/   ModDevGradle, Java 21
├── 1.21.1-fabric/     Fabric Loom, Java 21
├── 1.20.1-fabric/     Fabric Loom, Java 17 target
├── settings.gradle    composite build (forge/neoforge projects)
├── build.gradle       buildAll / cleanAll / collectJars
└── stampDates.gradle  reproducible jar timestamps
```

There is no shared source set: each target carries its own full copy of the code, adapted to that loader's APIs. Pure logic and UI classes are identical across targets; loader-facing classes (networking, events, resource access) differ per target.

The Forge/NeoForge projects are part of a Gradle composite build. The two Fabric projects require a newer Gradle for Loom, so the root drives them through their own wrappers instead of including them in the composite.

## Building

Requires JDK 17 and JDK 21 installed (Gradle toolchains pick them up automatically). From the repository root:

```
./gradlew buildAll
```

This builds all five targets and collects the final jars into `build/libs/`. Individual targets: `./gradlew build_1_20_1_fabric`, `./gradlew build_1_21_1_fabric`, or run `gradlew build` inside any Forge/NeoForge subproject.

Builds are reproducible: file order inside the jars is fixed and every zip entry carries the same constant timestamp.

## How it works

Panoptic runs entirely on the client:

- **Inspector** builds its data cards from the live game registries, then scans every loaded mod jar and datapack for the files behind an id (models, textures, recipes, loot tables, lang entries) and previews them in-game (JSON, NBT, PNG, OGG, structure files).
- **Seed Inspector** runs vanilla worldgen on the client for the requested seed and dimension to compute biome tiles and structure placement. On dedicated servers, where the seed is unknown, the same data is served remotely by Panopticon.
- **Trade Inspector** samples every villager profession and trade tier into a searchable book, expanding enchanted-book listings; trades that can only be resolved server-side are re-sampled on the integrated server.
- **Screen Inspector** records the draw operations of a GUI frame through mixins, so a screenshot can be reopened later with pan/zoom while every item on it keeps its full data on hover.

## Networking

In singleplayer everything works with no server side. On servers the client speaks a small optional protocol in the `panoptic:` namespace to either the integrated server or the Panopticon companion: structure/biome oracle queries, permission sync, villager spawning, item giving. Message names and payload layout are identical across all five targets; only the per-loader transport differs (SimpleChannel, ChannelBuilder, custom payloads, Fabric networking). The full channel table is documented in the Panopticon repository.

## Permissions

On a server running Panopticon every tool is gated by permission nodes synced to the client. Without a server counterpart, restricted tools stay off unless you are an operator; singleplayer access is configurable.

## Localization

17 languages with a uniform key set (~540 keys) in every target: en, ru, uk, be, nl, de, fr, es, es_mx, pt_br, pl, it, tr, ko, ja, zh_cn, zh_tw.

## Support

If this project is useful to you, you can support its development - thank you!

- **Boosty:** https://boosty.to/velikiybogmolokich/donate
- **DonationAlerts:** https://donationalerts.com/r/mokichchannel
- **TON:** `UQAEfqFEFDGDdp5fkePrEiJ_xiGEnsTGRNN6HccyOreXWgQ_`
- **Ethereum (ERC-20):** `0xD405a3B4a479B5820F4977C54a3e3ef366fc3c57`
- **Solana (SOL):** `C973HmGDedHE3mFVcNNnLYnpGeSCwxd9D9KavtvTPgPA`

## License

Released under the [GNU Lesser General Public License v3.0](LICENSE).