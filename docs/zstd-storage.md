# Zstandard save compression

Set `S:chunkCompression=ZSTD` in `config/cubicchunks.cfg` to use Zstandard level 1
without a dictionary for newly saved columns and cubes. GZIP remains the default,
matching upstream master. LZ4 and uncompressed records are also optional.
Both ordinary Anvil3D and experimental compact-empty storage support this codec.

Each record is an independent standard Zstd frame, with content size and checksum.
No other cube or external dictionary is needed to decode it. Native contexts are
reused through bounded pools; buffers contain only the written NBT bytes.
The bundled zstd-jni library works without client-side LWJGL. Its Java packages
must not be relocated or minimized, because native entry points depend on them.

Reads recognize legacy raw NBT, GZIP, LZ4, NONE and ZSTD regardless of the currently selected
writer. Changing the setting does not rewrite existing records, compact the
region files, or migrate storage backends. Mixed-codec worlds are supported.
Back up before testing: current upstream master only reads GZIP and legacy raw NBT;
older experimental LZ4 jars also do not understand ZSTD. Setting the writer back to
GZIP is not a downgrade procedure for already written records using newer codecs.

Zstd record decoding requires a known size of at most 64 MiB and exactly one
complete frame. Compact templates retain their stricter 8 KiB bound. Unusually
large NBT records exceeding the Zstd bound are written using the existing GZIP
codec instead, so selecting ZSTD does not prevent them from being saved.

This option changes disk storage, not network packet compression. The checksum
adds four bytes per frame versus the initial checksum-disabled offline benchmark.
