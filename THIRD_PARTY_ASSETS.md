# Third-party assets and notices

The root [MIT license](LICENSE) applies to original Kettoryn code. It does not relicense the resources below. Full notices are available offline in Settings → Illustration credits → Full legal notices and in [the bundled notice document](app/src/main/assets/legal/THIRD_PARTY_NOTICES.txt).

## Workout Guide exercise illustrations — CC BY-SA 4.0

Original pose artwork by [Everkinetic](https://github.com/everkinetic/data), expanded and normalized by [Bryl Lim](https://bryllim.com) for [Workout Guide](https://github.com/bryllim/workout-guide). Copyright © 2026 Bryl Lim except where upstream attribution identifies another source. [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/).

Pinned revision: `aac599224bb9780305239607ef98540b7e0ce389`. Kettoryn distributes 906 frames for 302 exercises as 512×512 lossless WebP; ten additional PNG frames and their SVG sources are retained. SVG rasterization and image encoding preserve poses, geometry, aspect and colors. These image adaptations remain **CC BY-SA 4.0**. They are used in the exercise UI, not workout posters. No endorsement is implied.

Full attribution, legal code, source-frame URLs, modification notes and source/output hashes:

- [302-exercise source and raster inventory](app/src/main/assets/illustration-credits/workout-guide-302/README.md)
- [Full CC BY-SA 4.0 text](app/src/main/assets/illustration-credits/workout-guide-302/CC-BY-SA-4.0-LEGALCODE.txt)
- [Original upstream attribution](app/src/main/assets/illustration-credits/workout-guide-302/ATTRIBUTION.md)
- [Additional PNG/SVG frame manifest](app/src/main/assets/illustration-credits/manifest.json)

## MuscleMap anatomy vectors — MIT

Copyright © 2026 Jsplice / MuscleMap contributors. [Source](https://github.com/Jsplice/MuscleMap/tree/170cfacda79376dd59ac0cf2f7dfab8ff9f4443a), [full MIT notice](app/src/main/assets/anatomy/LICENSE).

Kettoryn uses 56 front/back vector regions, adapted to Android drawing, different palette/strokes and primary-muscle highlighting. No body photographs are included. The [source manifest](app/src/main/assets/anatomy/source-manifest.json) and [unmodified upstream provenance](app/src/main/assets/anatomy/ASSET_PROVENANCE.md) retain path hashes and lineage. MIT notices accompany the application distribution; public poster pixels do not contain technical credits.

## Manrope — SIL Open Font License 1.1

Copyright 2018 The Manrope Project Authors ([source](https://github.com/googlefonts/manrope)). [Official font distribution](https://github.com/google/fonts/tree/main/ofl/manrope); [full bundled OFL notice](app/src/main/assets/font-licenses/Manrope-OFL.txt).

The four bundled static weights (400/500/600/700) were instantiated from the official variable font without glyph design changes. Font files retain OFL 1.1; the application-code MIT license does not apply to them.

## Runtime dependencies — Apache 2.0

AndroidX (except its repackaged Protocol Buffers runtime), Compose and Material icons, Android tooling runtime, Kotlin, kotlinx.coroutines, kotlinx.serialization, Okio, Guava listenablefuture, JetBrains annotations and JSpecify retain their respective authors' notices and Apache 2.0 terms. See [bundled Apache 2.0 text](app/src/main/assets/legal/Apache-2.0.txt), [AndroidX source](https://android.googlesource.com/platform/frameworks/support/), [Kotlin](https://github.com/JetBrains/kotlin), [coroutines](https://github.com/Kotlin/kotlinx.coroutines) and [Material icons](https://github.com/google/material-design-icons). Build and test tools are separate dependencies and retain their own licenses.

## Protocol Buffers runtime — BSD-3-Clause

DataStore bundles `androidx.datastore:datastore-preferences-external-protobuf:1.1.2`, a repackaged proto-lite runtime. Its published Maven POM identifies BSD-3-Clause. Copyright 2008 Google Inc. All rights reserved. [Original source](https://github.com/protocolbuffers/protobuf), [full bundled copyright, conditions and disclaimer](app/src/main/assets/legal/Protobuf-BSD-3-Clause.txt).

Additional Apache-2.0 sources: [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization), [Okio](https://github.com/square/okio), [Guava](https://github.com/google/guava), [JetBrains annotations](https://github.com/JetBrains/java-annotations), [JSpecify](https://github.com/jspecify/jspecify). These are runtime transitive dependencies, not additional app services.
