# Cube bundle storage

Bundle storage (`cubicchunks:bundles`) is the default storage format. It
stores [empty cubes](#empty-cubes) as two bytes in their column's record and every
other cube in records of aligned 2x2x2 cube groups ("bundles"). A bundle holds the block data
of up to eight cubes with one block palette shared by all of them, which together
with fewer, larger records needs considerably less disk space than one record per
cube. Columns are stored in aligned 2x2 groups in the `region2d` files.

## Layout

Bundles live in the ordinary `region3d` files. Each cube keeps its own header
entry, and the header entries of all cubes of a bundle point at the same record,
so existence checks remain header lookups and need no decompression. A 16x16x16
cube region holds 8x8x8 complete bundles; bundles never cross regions.

A record starts with `CCB`, a version byte (3), a codec byte and the mask of member
cubes, then the uncompressed length of the payload, followed by the payload as one
compressed frame.

The payload holds the mask of member cubes, the mask of members that use the palette,
the palette of block states (16-bit block ID and 16-bit metadata, from the vanilla and
EndlessIDs arrays) and each member's NBT. Members with a single section of plain block
arrays store byte indices into the palette (16-bit once a bundle has more than 256
states) and a mask of the arrays they had. Cubes with full 24-bit block IDs
(`BlocksB3`), several sections or unusual arrays keep their sections unchanged.
Decoding restores each cube's NBT exactly. The frame uses the configured compression
codec.

Columns are grouped the same way: the columns of an aligned 2x2 group, the footprint
of a stack of bundles, share one record in the `region2d` files. A column group
record starts with `CCG`, a version byte, a codec byte, the mask of member columns
and the uncompressed length, followed by the compressed NBT of each member.

Writing one cube of a bundle rewrites the bundle record. While the palette only
grows within its index width, the other members keep their encoded bytes; otherwise
the bundle is encoded again. A bundle that would not fit into 255 sectors is not
written as a bundle: the saved cubes get their own records and the other members
stay where they are. Bundle storage always uses shadow paging. A member whose
stored data cannot be decoded is logged and left unchanged while the rest of its
bundle is written.

## Empty cubes

Most cubes of a world are empty, above the terrain and, in converted worlds, also
below it. An empty cube has no record of its own: it is a state in its column's
record, from which its data is rebuilt when it is read. A state is a flags byte
(initial lighting done, surface tracked and in the height map and whether each of
those two tags is present, whether the saved height map is entirely below or above
the cube, and whether the cube has the game's lighting info or, like cubes
converted from vanilla worlds, none) and the cube's population status. A cube is
stored this way only when rebuilding it gives back exactly its data. One with
blocks, entities, tile entities, scheduled ticks, pending neighbor light checks, 3D
biomes, a saved height map that crosses it or any other tag keeps a full record.

The states of a column's cubes are a `CubeStates` tag of the column's record: the
lowest cube Y, then two bytes per cube Y up to the highest, at most 4096 cube Ys.
The tag belongs to the storage; columns are read without it.

A full record always wins over a state. A cube that becomes empty has its state
written before its full record is removed, and one that gets a full record keeps its
old state until the next write of its column drops it. An interrupted save
therefore leaves either the old or the new cube, and a read never finds a cube in
neither place. An empty cube whose column has no record yet is written as a full
record and becomes a state when its column is written.

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
read-ahead. Empty cubes are not kept, since their states are cheap to read.

## Worlds from other versions

This version only reads its own format, `cubicchunks:bundles`. It refuses to open a
world in any other format, such as the `cubicchunks:anvil3d` of 0.1 and 0.2.0 or the
`cubicchunks:anvil3d-bundles` of the 0.1 pre-releases. The first releases with bundle
storage convert such worlds when they are opened; later releases no longer do, so a
world in an old format has to be opened once with one of those first releases.

Older CubicChunks versions cannot read bundle or column group records, so keep
backups if you may need to go back. Do not edit the format marker in
`data/cubicchunks.world_format.dat`.
