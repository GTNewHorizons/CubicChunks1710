# Region compaction

CubicChunks automatically compacts region files before opening each dimension's storage. Region writes reuse
free sectors, but unused space can remain inside files or at their ends. Compaction packs the current records
together and rebuilds their offsets. It does not decode or recompress the records, change the save format,
or change the selected compression codec.

## Automatic compaction

`B:compactRegionsOnWorldLoad=true` in `config/cubicchunks.cfg` enables compaction by default for both built-in
Anvil3D storage formats. Each dimension is checked when its chunk provider initializes, before its region files
are opened for normal reads and writes. This includes dimensions initialized during world/server startup and
dimensions initialized later on demand. Unloading a dimension and opening it again runs the check again.
This is not a scan of every saved dimension whenever the Overworld loads, and it does not run on individual
chunk/cube loads, autosaves or while the dimension's storage is in use.

Only `region2d/*.2dr` and `region3d/*.3dr` in the dimension being opened are processed, one file at a time.
Oversized `.ext` entries and compact-empty `.cce` tables are not changed. Already packed files are checked but
not replaced. Normal play can create new holes, which a later load can reclaim. Large fragmented dimensions
can take longer to load; the delay depends on their region files and unused space. The log reports the number
of checked and compacted regions, reclaimed bytes and elapsed time. Set `compactRegionsOnWorldLoad=false`
to disable the automatic pass without changing compression or the storage format.

Files shorter than a complete region header are left for RegionLib to initialize. If a selected region file,
`region2d` directory or `region3d` directory is a symbolic link, automatic compaction skips that dimension and
logs a warning; normal storage opening still proceeds. Invalid allocations in initialized region files stop
the pass before any region is replaced and prevent that storage from opening. Compaction is not a repair tool.

## Optional offline tool

The command-line tool uses the same packing logic for manual inspection or maintenance without starting
Minecraft. It is not required for automatic compaction. Stop the server, or exit Minecraft for a singleplayer
world, and back up the save first. Use the same machine, OS account and Java `user.home` as Minecraft.
Run the following with the actual built mod jar, not a dev/sources jar:

```sh
java -cp cubicchunks.jar com.cardinalstar.cubicchunks.server.chunkio.RegionCompactor /path/to/world
java -cp cubicchunks.jar com.cardinalstar.cubicchunks.server.chunkio.RegionCompactor /path/to/world --apply --world-stopped
```

The first command only reports the space that could be reclaimed. Both commands acquire maintenance locks.
The second actually replaces files. `--world-stopped` is an explicit acknowledgement that Minecraft is stopped;
it is not a way to bypass an active lock. No Minecraft installation or native compression library is needed to
run the compactor itself.

### Command-line scope

- Scans `region2d` and `region3d` in the supplied directory and its direct `DIM<number>` directories.
- For a dimension saved in a custom location, supply that dimension directory separately. It does not recursively
  discover dimensions or descend into backups, conversion staging areas, or unrelated formats.
- Supports standard CC `.2dr` and `.3dr` region files with 512-byte sectors, including mixed codecs.
- Leaves oversized `.ext` entries, compact-empty `.cce` tables, vanilla `.mca`, and all other world data alone.
- Rejects symlinked selected directories/files and malformed or overlapping region allocations. Missing final
  sector padding is accepted if the full record exists; never grows an already packed file just to add padding.
  This is not a corruption-repair tool. A dry run creates persistent zero-byte lock files outside the save if absent.
- Already packed files are not replaced. Running the command twice without intervening saves reclaims nothing
  the second time. Normal play can fragment the files again; this is not a permanent storage-size guarantee.

## Safety and limitations

`RegionCubeStorage` holds an OS lock for the lifetime of its cached region storage. Automatic compaction holds
an exclusive maintenance lease, then hands the lock to normal storage without releasing it in between. The
offline tool takes the same exclusive lock for every selected dimension. A second server/compactor that uses
these locks on the same machine/account/Java home is rejected rather than allowed to use stale file handles.
Multiple storage wrappers within one JVM share a reference-counted normal storage lease; maintenance never
shares that lease.

Locks live in `${user.home}/.cubicchunks/storage-locks`, named by SHA-256 of each canonical storage path. This
keeps them out of ordinary world/instance backups: POSIX can release a process's lock if *any* descriptor for the
same file is closed by that process. Other code in the game JVM must not open these lock files. The locked byte is
beyond EOF to avoid blocking Windows backup readers. Do not delete the lock files. They are intentionally empty
and persistent; process exit releases the OS lock. A writable home directory with working file locks is required.

For service accounts or containers with no writable home, set an absolute lock-directory override on **both**
the game and the compactor JVM: `-Dcubicchunks.storageLockDirectory=/absolute/local/cc-locks` (before `-cp`/`-jar`).
Keep it outside the world/instance backup tree. Both processes must use the same directory; there is deliberately
no automatic fallback that could silently select different locks. Relative or invalid paths fail closed.
Lock files are zero bytes but do accumulate for distinct
storage paths; remove them only during explicit housekeeping with all cooperating JVMs stopped.

**Older jars, other accounts/machines and external tools do not participate in this lock.** Vanilla 1.7.10's
`session.lock` is a timestamp, not an OS lock. Stop those writers yourself. Source header, payload, identity,
size and modification-time checks detect many unexpected changes but are not a substitute for excluding
concurrent writers. This local guard is not a distributed lock for network-hosted worlds.
Both processes must also access the save via the same canonical path. Symlinks resolve to that path, but bind
mounts, Windows `subst` drives and similar alternative paths are not guaranteed to share the lock.

Automatic compaction checks all initialized regions in the dimension before replacing any; the offline tool
checks all selected regions across its selected dimensions first. Each changed region then gets a temporary
file in the same directory, containing only its header and live records. The compactor checks every copied record
byte-for-byte, forces the output to disk, closes both handles, preserves POSIX ownership/permissions or ACLs where
supported, and atomically replaces the original. If atomic replacement is unavailable, it fails without deleting
the original. It needs extra disk space for one packed region, not another entire world.

This is atomic **per file**, not for the whole world. An interrupted run can leave some regions compacted and some
untouched; both layouts remain readable. A hard termination can leave `.cc-compact-*.tmp` files, which the game
ignores. With all writers stopped these can be deleted before the next load or manual run. Atomic replacement
does not guarantee recovery of filesystem directory metadata after power loss. There is no live/background
compaction while a dimension's storage is open. Keep regular backups.
