# Cubic Chunks for 1.7.10

A port of https://github.com/OpenCubicChunks/CubicChunks targeting 1.7.10.

EXPECT MANY BUGS WITH CURRENT BUILDS, AND NO COMPAT TESTING HAS BEEN DONE YET.

Huge thanks to contributors:
RecursivePineapple
DarkShadow44

For helping get the port off the ground.

TODO:
- World Conversions
- Compat Testing - [Link](https://docs.google.com/spreadsheets/d/1Ruar7KJ-upAkgA64MK3t-Yh3VcDsk0b1kO10thN_T9w/edit?usp=sharing)

## World storage

New worlds use [compact-empty Anvil3D storage](docs/compact-empty-storage.md) by default.
[Zstandard](docs/zstd-storage.md) is the default save compression, with GZIP, LZ4 and NONE also available.
[Region compaction](docs/region-compaction.md) runs automatically before each dimension's storage opens.
Existing world-format markers and configured compression choices are retained.
