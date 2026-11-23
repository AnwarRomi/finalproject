package my.edu.cityu.rescue.storage;

import my.edu.cityu.rescue.model.Allocation;
import my.edu.cityu.rescue.model.ChilledBatch;
import my.edu.cityu.rescue.model.FoodBatch;
import my.edu.cityu.rescue.model.Recipient;
import my.edu.cityu.rescue.model.ShelfStableBatch;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Stores one versioned UTF-8 tab-separated snapshot and locks it for a single writer. */
public final class RescueStore implements AutoCloseable {
    private static final String HEADER = "CAMPUS_FOOD_RESCUE\t1";
    private final Path path;
    private final FileChannel lockChannel;
    private final FileLock lock;

    public RescueStore(Path path) throws IOException {
        this.path = path.toAbsolutePath().normalize();
        Files.createDirectories(this.path.getParent());
        Path lockPath = this.path.resolveSibling(this.path.getFileName() + ".lock");
        lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            lock = lockChannel.tryLock();
            if (lock == null) {
                throw new IOException("Another window is using " + this.path.getFileName() + ".");
            }
        } catch (IOException | OverlappingFileLockException error) {
            lockChannel.close();
            throw new IOException("Unable to lock " + this.path.getFileName() + ". Close any other open copy.", error);
        }
    }

    public Path getPath() { return path; }

    public record Snapshot(List<FoodBatch> batches, List<Recipient> recipients, List<Allocation> history) { }

    public Snapshot load() throws IOException {
        List<FoodBatch> batches = new ArrayList<>();
        List<Recipient> recipients = new ArrayList<>();
        List<Allocation> history = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            if (!HEADER.equals(reader.readLine())) {
                throw new IOException("Unsupported or missing data file header.");
            }
            int lineNumber = 1;
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                try {
                    String[] fields = line.split("\t", -1);
                    switch (fields[0]) {
                        case "B" -> batches.add(readBatch(fields));
                        case "R" -> recipients.add(readRecipient(fields));
                        case "A" -> history.add(readAllocation(fields));
                        default -> throw new IllegalArgumentException("Unknown record type.");
                    }
                } catch (RuntimeException error) {
                    throw new IOException("Invalid data at line " + lineNumber + " in " + path.getFileName() + ".", error);
                }
            }
        } catch (NoSuchFileException emptyStore) {
            return new Snapshot(List.of(), List.of(), List.of());
        }
        return new Snapshot(batches, recipients, history);
    }

    private static FoodBatch readBatch(String[] fields) {
        requireColumns(fields, 7);
        int id = Integer.parseInt(fields[1]);
        int portions = Integer.parseInt(fields[5]);
        LocalDateTime useBy = LocalDateTime.parse(fields[6]);
        return switch (fields[2]) {
            case ShelfStableBatch.STORAGE_TYPE -> new ShelfStableBatch(id, fields[3], fields[4], portions, useBy);
            case ChilledBatch.STORAGE_TYPE -> new ChilledBatch(id, fields[3], fields[4], portions, useBy);
            default -> throw new IllegalArgumentException("Unknown storage type.");
        };
    }

    private static Recipient readRecipient(String[] fields) {
        requireColumns(fields, 5);
        boolean refrigerated = switch (fields[3]) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException("Unknown refrigeration setting.");
        };
        return new Recipient(Integer.parseInt(fields[1]), fields[2], refrigerated, Integer.parseInt(fields[4]));
    }

    private static Allocation readAllocation(String[] fields) {
        requireColumns(fields, 5);
        return new Allocation(LocalDateTime.parse(fields[1]), Integer.parseInt(fields[2]),
                Integer.parseInt(fields[3]), Integer.parseInt(fields[4]));
    }

    private static void requireColumns(String[] fields, int expected) {
        if (fields.length != expected) {
            throw new IllegalArgumentException("Unexpected number of fields.");
        }
    }

    public void save(Snapshot snapshot) throws IOException {
        Path temporary = Files.createTempFile(path.getParent(), "rescue-", ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary)) {
                writeLine(writer, HEADER);
                for (FoodBatch batch : snapshot.batches()) {
                    writeLine(writer, "B", batch.getId(), batch.getStorageType(), batch.getName(),
                            batch.getDonor(), batch.getPortions(), batch.getUseBy());
                }
                for (Recipient recipient : snapshot.recipients()) {
                    writeLine(writer, "R", recipient.getId(), recipient.getName(),
                            recipient.hasRefrigeration(), recipient.getCapacity());
                }
                for (Allocation allocation : snapshot.history()) {
                    writeLine(writer, "A", allocation.time(), allocation.batchId(),
                            allocation.recipientId(), allocation.portions());
                }
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                throw new IOException("The data folder needs a filesystem that supports atomic replacement.", error);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void writeLine(BufferedWriter writer, Object... fields) throws IOException {
        for (int index = 0; index < fields.length; index++) {
            if (index > 0) {
                writer.write('\t');
            }
            writer.write(fields[index].toString());
        }
        writer.newLine();
    }

    @Override
    public void close() throws IOException {
        try {
            lock.release();
        } finally {
            lockChannel.close();
        }
    }
}
