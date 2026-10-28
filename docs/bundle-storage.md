# Cube bundle storage

Bundle storage (`cubicchunks:anvil3d-bundles`) is the default storage format. It
keeps [compact empty cubes](compact-empty-storage.md) and stores every other cube
in records of aligned 2x2x2 cube groups ("bundles"). A bundle holds the block data
of up to eight cubes with one block palette shared by all of them, which together
with fewer, larger records needs considerably less disk space than one record per
cube. Columns are stored in aligned 2x2 groups in the `region2d` files.

## Layout

Bundles live in the ordinary `region3d` files. Each cube keeps its own header
entry, and the header entries of all cubes of a bundle point at the same record,
so existence checks remain header lookups and need no decompression. A 16x16x16
cube region holds 8x8x8 complete bundles; bundles never cross regions.

A bundle record has two parts, compressed separately so that either can be read
without the other. The client part holds what clients are sent of each member:
its `Sections` (block and light arrays) and `Biomes`. The server part holds the
rest of each member's NBT: position and status, entities, tile entities, scheduled
ticks and lighting info.

A record starts with `CCB`, a version byte (2), a codec byte and the mask of member
cubes, then the uncompressed length of the client part, the compressed length of
the client part and the uncompressed length of the server part, followed by the
compressed client part and the compressed server part.

The client part holds the mask of members that have one, the mask of members that
use the palette, the palette of block states (16-bit block ID and 16-bit metadata,
from the vanilla and EndlessIDs arrays) and each member's client NBT. Members with a
single section of plain block arrays store byte indices into the palette (16-bit
once a bundle has more than 256 states) and a mask of the arrays they had. Cubes
with full 24-bit block IDs (`BlocksB3`), several sections or unusual arrays keep
their sections unchanged. The server part holds the member mask and each member's
remaining NBT. Decoding restores each cube's NBT exactly. Both parts use the
configured compression codec.

Columns are grouped the same way: the columns of an aligned 2x2 group, the footprint
of a stack of bundles, share one record in the `region2d` files. A column group
record starts with `CCG`, a version byte, a codec byte, the mask of member columns
and the uncompressed length, followed by the compressed NBT of each member.

Writing one cube of a bundle rewrites the bundle record. While the palette only
grows within its index width, the other members keep their encoded bytes; otherwise
the bundle is encoded again. A bundle that would not fit into 255 sectors is not
written as a bundle: the saved cubes get their own records and the other members
stay where they are. Bundle storage always uses shadow paging, whatever
`useShadowPagingIO` is set to. A member whose stored data cannot be decoded is
logged and left unchanged while the rest of its bundle is written.

## Saving and the save cache

When a cube unloads, its saved data may wait for the other loaded cubes of its
bundle to unload as well, so that the bundle is written once instead of once per
cube. A bundle is written on the next tick after its last loaded cube unloads,
or at the latest `bundleWriteWaitSeconds` (default 10) after its first cube's
save; while more than 4096 cubes wait, the oldest bundles are written early.
Saves of cubes that are still loaded, such as autosaves, and columns are written
right away. `/save-all flush`, unloading a dimension and stopping the server write
everything that waits; a crash can lose the waiting saves of the last few
seconds. Set `bundleWriteWaitSeconds` to 0 to write every save right away.

The save cache (`saveCacheMegabytes`) keeps the written records themselves for
recently unloaded cubes: the cubes of a bundle share their record, so it is held
once, compressed, and nothing is serialized a second time. Reloading one of these
cubes decodes the whole record once and keeps its other members briefly, as for
read-ahead. Compact empty cubes are not kept, since their tables are cheap to read.

## Existing worlds

Worlds using `cubicchunks:anvil3d` or `cubicchunks:anvil3d-compact-empty` are
switched to bundle storage when they are opened, unless `storageFormat` in
`config/cubicchunks.cfg` is set to a format other than `cubicchunks:anvil3d-bundles`. Conversion is gradual: per-cube
and per-column records keep being read, and a bundle or column group is written in
the new format the first time one of its cubes or columns is saved, after which its
old records are released. Region
compaction reclaims the freed space on a later start.

Older CubicChunks versions cannot read bundle or column group records. After a world has been
opened with this version, keep backups if you may need to go back. Do not edit the
format marker in `data/cubicchunks.world_format.dat`.
