# Experimental compact empty-cube storage

Opt in for a new world by setting `storageFormat` to
`cubicchunks:anvil3d-compact-empty`. Leaving it empty keeps ordinary Anvil3D.
CC worlds with `data/cubicchunks.world_format.dat` keep their recorded format;
changing the config does not migrate them. Very old or damaged worlds without
that marker use the configured format on first load. Test only on backed-up copies;
there is no in-place migration command or automatic way back to ordinary Anvil3D.

This is a new, experimental on-disk format. Keep a backup before testing. Older
CC jars and ordinary Anvil3D tools cannot read it. Reverse conversion is not yet
supported: the ordinary storage reader refuses this format instead of dropping
compact cubes. Do not remove the format marker or `region3d-empty` directory.

## Representation

- Columns and cubes with allocated block storage remain in `region2d`/`region3d`.
- Cubes without `Sections`, entities, tile entities or scheduled ticks may use
  compact tables in `region3d-empty`. Oversized metadata falls back to Anvil3D.
- Each table covers 16x16x16 cube positions. Identical full NBT templates, excluding
  only the three cube coordinates, are written once with a four-byte slot/index
  pair per cube. Different metadata uses different templates.
- Population, lighting initialization, pending neighbor-light checks, heightmap
  snapshots, biome data and unknown mod tags are retained. An all-air allocated
  block section is deliberately not treated as sectionless; its light arrays matter.
- Reads restore coordinates into a freshly decoded NBT object. Cubes never share
  mutable NBT or lighting state in memory.
- Templates use the configured GZIP, LZ4, NONE or ZSTD codec, with bounded decoding.

Tables have an eight-byte `CCEMPTY`/version-1 magic, a big-endian int template count,
then int-length-prefixed compressed NBT templates, an int entry count and unsigned
short slot/template index pairs. Slots are `(x & 15) << 8 | (y & 15) << 4 | (z & 15)`.
Region coordinates use arithmetic shifts, including below zero. Each count is
bounded by 4096; normalized raw NBT and compressed templates are limited to 8192
bytes. Unknown versions, truncated tables and invalid entries fail reads.

## Save Ordering

Compact entries take precedence over ordinary entries during interrupted format
transitions. Full data and headers are written before removing compact entries; replacement
tables are written to a forced temporary file and atomically renamed before old
full records are retired. Failed table writes leave the previous committed cache
state intact. Unchanged templates do not rewrite tables.

The table cache targets 4 MiB/32 regions, counting shared arrays only once. It may
retain one larger table (at most roughly 33 MiB) to avoid rereading it per cube.
Batch operations retain their tables for the batch, independent of cache eviction.

This does not make a whole world save or multi-region batch atomic. Directory
fsync/power-loss recovery is not newly guaranteed. Tests cover caught write
failures, close/reopen cycles, both RegionLib writers and all supported codecs, not
machine power loss. Compact table rewrites add work when metadata changes; this
is a disk-space/saving-cost tradeoff, not a claim of free performance.
