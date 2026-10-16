package com.cardinalstar.cubicchunks.server.chunkio;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/** Cooperating storage users hold this lock until all cached region handles have been closed. */
public final class StorageMaintenanceLock implements AutoCloseable {

    private static final Map<Path, Owner> OWNERS = new HashMap<>();

    private final Path directory;
    private final Owner owner;
    private boolean closed;

    private StorageMaintenanceLock(Path directory, Owner owner) {
        this.directory = directory;
        this.owner = owner;
    }

    public static StorageMaintenanceLock openStorage(Path directory) throws IOException {
        return acquire(directory, false);
    }

    static StorageMaintenanceLock openMaintenance(Path directory) throws IOException {
        return acquire(directory, true);
    }

    private static synchronized StorageMaintenanceLock acquire(Path directory, boolean maintenance) throws IOException {
        Files.createDirectories(directory);
        Path key = directory.toRealPath();
        Owner owner = OWNERS.get(key);
        if (owner != null) {
            if (maintenance || owner.maintenance) throw busy(key);
            owner.references++;
            return new StorageMaintenanceLock(key, owner);
        }
        FileChannel channel = FileChannel
            .open(lockFile(key), StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        try {
            FileLock lock;
            try {
                // Beyond EOF, so ordinary readers cannot overlap the locked byte on Windows.
                lock = channel.tryLock(Long.MAX_VALUE - 1, 1, false);
            } catch (OverlappingFileLockException e) {
                throw busy(key);
            }
            if (lock == null) throw busy(key);
            owner = new Owner(channel, lock, maintenance);
            OWNERS.put(key, owner);
            return new StorageMaintenanceLock(key, owner);
        } catch (IOException | RuntimeException | Error e) {
            try {
                channel.close();
            } catch (IOException close) {
                e.addSuppressed(close);
            }
            throw e;
        }
    }

    private static IOException busy(Path directory) {
        return new IOException("CubicChunks storage is in use; stop the world/other maintenance first: " + directory);
    }

    static Path lockFile(Path directory) throws IOException {
        // POSIX releases a process's record locks when ANY descriptor for the inode closes. Keep the lock
        // outside the save/instance tree, where in-game world backups might open and close every file.
        Path locks;
        String configured = System.getProperty("cubicchunks.storageLockDirectory");
        try {
            if (configured != null) {
                locks = Paths.get(configured);
            } else {
                String home = System.getProperty("user.home");
                if (home == null)
                    throw new IOException("Java user.home is missing; set cubicchunks.storageLockDirectory");
                locks = Paths.get(home)
                    .resolve(".cubicchunks")
                    .resolve("storage-locks");
            }
        } catch (InvalidPathException e) {
            throw new IOException("Invalid storage lock directory; set cubicchunks.storageLockDirectory", e);
        }
        if (!locks.isAbsolute()) {
            throw new IOException("Storage lock directory must be absolute: " + locks);
        }
        Files.createDirectories(locks);
        locks = locks.toRealPath();
        Path storage = directory.toRealPath();
        if (locks.startsWith(storage)) {
            throw new IOException("Storage lock directory must be outside the save: " + locks);
        }
        byte[] hash;
        try {
            hash = MessageDigest.getInstance("SHA-256")
                .digest(
                    storage.toString()
                        .getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 is required by Java", e);
        }
        StringBuilder name = new StringBuilder(64);
        for (byte value : hash) {
            name.append(Character.forDigit((value >>> 4) & 15, 16));
            name.append(Character.forDigit(value & 15, 16));
        }
        return locks.resolve(name + ".lock");
    }

    @Override
    public void close() throws IOException {
        synchronized (StorageMaintenanceLock.class) {
            if (closed) return;
            closed = true;
            if (--owner.references == 0) {
                try {
                    owner.lock.release();
                } finally {
                    try {
                        owner.channel.close();
                    } finally {
                        OWNERS.remove(directory);
                    }
                }
            }
            // Never delete the lock file: another process could already have opened its inode.
        }
    }

    private static final class Owner {

        final FileChannel channel;
        final FileLock lock;
        final boolean maintenance;
        int references = 1;

        Owner(FileChannel channel, FileLock lock, boolean maintenance) {
            this.channel = channel;
            this.lock = lock;
            this.maintenance = maintenance;
        }
    }
}
