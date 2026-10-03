# Compact empty-cube storage

Compact-empty Anvil3D is the default storage format for new CubicChunks worlds.
It stores repeated metadata for sectionless cubes once per region, while keeping
columns and other cubes in ordinary Anvil3D region files. It uses
[Zstandard compression](zstd-storage.md) by default and supports all four codecs.
[Region compaction](region-compaction.md) automatically reclaims unused space in
ordinary region files before each dimension's storage opens.

## Format selection and compatibility

Leave `storageFormat` empty in `config/cubicchunks.cfg` to use the default, or set
it explicitly to `cubicchunks:anvil3d-compact-empty`. The alternative
`cubicchunks:anvil3d` format stores all cubes in ordinary region files.

Existing CC worlds with `data/cubicchunks.world_format.dat` retain their recorded
format. Changing the config does not migrate them. A world without that marker
uses the configured format, or the default if the setting is empty. There is no
in-place migration command between the two formats.

Keep backups before changing mod versions or save formats. CC versions and tools
that only support ordinary Anvil3D cannot read the compact tables. The ordinary
storage reader refuses this format instead of silently dropping compact cubes.
Do not remove the format marker or `region3d-empty` directory to change formats.

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

Compact entries take precedence over ordinary entries during interrupted transitions
between a cube's two representations. Full data and headers are written before
removing compact entries. Replacement tables are written to a forced temporary
file and atomically renamed before old full records are retired. Failed table
writes leave the previous committed cache state intact. Unchanged templates do
not rewrite tables.

The table cache targets 4 MiB/32 regions, counting shared arrays only once. It may
retain one larger table (at most roughly 33 MiB) to avoid rereading it per cube.
Batch operations retain their tables for the batch, independent of cache eviction.

Atomic replacement applies to individual tables, not a whole world save or
multi-region batch. It does not guarantee recovery of filesystem directory metadata
after power loss. Keep regular backups. Changing metadata can require rewriting
its compact table, so the storage savings can add work during saves.
