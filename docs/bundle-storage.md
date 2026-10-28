# Cube bundle storage

Bundle storage (`cubicchunks:anvil3d-bundles`) is the default storage format. It
keeps [compact empty cubes](compact-empty-storage.md) and stores every other cube
in records of aligned 2x2x2 cube groups ("bundles"). A bundle holds the block data
of up to eight cubes with one block palette shared by all of them, which together
with fewer, larger records needs considerably less disk space than one record per
cube. Columns stay in ordinary Anvil3D `region2d` files.

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

Writing one cube of a bundle rewrites the bundle record. While the palette only
grows within its index width, the other members keep their encoded bytes; otherwise
the bundle is encoded again. A bundle that would not fit into 255 sectors is not
written as a bundle: the saved cubes get their own records and the other members
stay where they are. Bundle storage always uses shadow paging, whatever
`useShadowPagingIO` is set to. A member whose stored data cannot be decoded is
logged and left unchanged while the rest of its bundle is written.

## Existing worlds

Worlds using `cubicchunks:anvil3d` or `cubicchunks:anvil3d-compact-empty` are
switched to bundle storage when they are opened, unless `storageFormat` in
`config/cubicchunks.cfg` is set to a format other than `cubicchunks:anvil3d-bundles`. Conversion is gradual: per-cube
records keep being read, and a bundle is written in the new format the first time
one of its cubes is saved, after which its old records are released. Region
compaction reclaims the freed space on a later start.

Older CubicChunks versions cannot read bundle records. After a world has been
opened with this version, keep backups if you may need to go back. Do not edit the
format marker in `data/cubicchunks.world_format.dat`.
