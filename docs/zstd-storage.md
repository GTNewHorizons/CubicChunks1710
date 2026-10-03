# Zstandard save compression

Zstandard level 1 is the default compression for saved columns and cubes. The
`chunkCompression` setting in `config/cubicchunks.cfg` accepts `ZSTD`, `GZIP`,
`LZ4` and `NONE`. An existing configured choice is not overwritten; set
`S:chunkCompression=ZSTD` to switch future writes to the default. Both ordinary
Anvil3D and [compact-empty storage](compact-empty-storage.md) support all four codecs.

## Record format

Each record is an independent standard Zstd frame, with content size and checksum.
Native contexts are reused through bounded pools; buffers contain only the written NBT bytes.
The bundled zstd-jni library works without client-side LWJGL. Its Java packages
must not be relocated or minimized, because native entry points depend on them.

Zstd record decoding requires a known size of at most 64 MiB and exactly one
complete frame. Compact templates retain their stricter 8 KiB bound. Unusually
large NBT records exceeding the Zstd bound are written using the existing GZIP
codec instead, so selecting ZSTD does not prevent them from being saved.

## Configuration and compatibility

Reads recognize legacy raw NBT, GZIP, LZ4, NONE and ZSTD regardless of the selected
writer. Changing `chunkCompression` affects future writes, not existing records;
mixed-codec worlds are supported. It does not migrate storage backends or trigger
compaction. [Automatic region compaction](region-compaction.md) runs separately
when each dimension's storage opens and preserves the existing compressed bytes.

Back up before changing mod versions. Older CC versions may not support every
codec or storage format used by a save. Selecting GZIP does not make already
written ZSTD or LZ4 records readable by a version that lacks those codecs.

This setting changes disk storage, not network packet compression.
