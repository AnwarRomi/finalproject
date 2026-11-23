package my.edu.cityu.rescue.service;

import my.edu.cityu.rescue.model.Allocation;
import my.edu.cityu.rescue.model.ChilledBatch;
import my.edu.cityu.rescue.model.FoodBatch;
import my.edu.cityu.rescue.model.Recipient;
import my.edu.cityu.rescue.model.ShelfStableBatch;
import my.edu.cityu.rescue.storage.RescueStore;
import my.edu.cityu.rescue.storage.RescueStore.Snapshot;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Validates records, plans redistribution and synchronizes changes with disk. */
public final class RescueService {
    public static final LocalDateTime DEMO_TIME = LocalDateTime.of(2026, 11, 24, 10, 0);
    private final RescueStore store;
    private Snapshot state;
    private long revision;

    public RescueService(RescueStore store) throws IOException {
        this.store = Objects.requireNonNull(store);
        state = store.load();
        validateLoadedState();
    }

    public record Plan(LocalDateTime time, long revision, List<Allocation> allocations) {
        public Plan {
            Objects.requireNonNull(time);
            allocations = List.copyOf(allocations);
        }
    }

    public record Totals(long registered, long allocated, long available, long expired) { }

    public List<FoodBatch> getBatches() { return List.copyOf(state.batches()); }
    public List<Allocation> getHistory() { return List.copyOf(state.history()); }

    public List<Recipient> getRecipients() {
        return state.recipients().stream()
                .map(recipient -> new Recipient(recipient.getId(), recipient.getName(),
                        recipient.hasRefrigeration(), recipient.getCapacity()))
                .toList();
    }

    public FoodBatch addBatch(String name, String donor, boolean chilled, int portions, LocalDateTime useBy)
            throws IOException {
        int id = Math.incrementExact(state.batches().stream().mapToInt(FoodBatch::getId).max().orElse(0));
        FoodBatch batch = chilled
                ? new ChilledBatch(id, name, donor, portions, useBy)
                : new ShelfStableBatch(id, name, donor, portions, useBy);
        List<FoodBatch> batches = new ArrayList<>(state.batches());
        batches.add(batch);
        save(new Snapshot(batches, state.recipients(), state.history()));
        return batch;
    }

    public int addRecipient(String name, boolean refrigerated, int capacity) throws IOException {
        int id = Math.incrementExact(state.recipients().stream().mapToInt(Recipient::getId).max().orElse(0));
        List<Recipient> recipients = new ArrayList<>(state.recipients());
        recipients.add(new Recipient(id, name, refrigerated, capacity));
        save(new Snapshot(state.batches(), recipients, state.history()));
        return id;
    }

    public void updateCapacity(int recipientId, int capacity) throws IOException {
        List<Recipient> recipients = new ArrayList<>(getRecipients());
        Recipient selected = recipients.stream().filter(recipient -> recipient.getId() == recipientId)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Select an existing recipient."));
        int used = usedPortions(false).getOrDefault(recipientId, 0);
        if (capacity < used) {
            throw new IllegalArgumentException("Capacity cannot be below the " + used + " portions already allocated.");
        }
        selected.setCapacity(capacity);
        save(new Snapshot(state.batches(), recipients, state.history()));
    }

    public Plan preview(LocalDateTime time) {
        Objects.requireNonNull(time);
        if (!state.history().isEmpty() && time.isBefore(state.history().get(state.history().size() - 1).time())) {
            throw new IllegalArgumentException("The simulation time cannot precede the last allocation.");
        }

        Map<Integer, Integer> available = remainingByBatch();
        Map<Integer, Integer> free = remainingByRecipient();
        List<FoodBatch> batches = new ArrayList<>(state.batches());
        batches.sort(Comparator.comparing(FoodBatch::getUseBy).thenComparingInt(FoodBatch::getId));
        List<Recipient> recipients = new ArrayList<>(state.recipients());
        recipients.sort(Comparator.comparing(Recipient::hasRefrigeration).thenComparingInt(Recipient::getId));

        List<Allocation> proposed = new ArrayList<>();
        for (FoodBatch batch : batches) {
            if (batch.hasExpiredAt(time)) {
                continue;
            }
            int left = available.get(batch.getId());
            for (Recipient recipient : recipients) {
                if (left == 0) {
                    break;
                }
                if (free.get(recipient.getId()) == 0 || !batch.canGoTo(recipient)) {
                    continue;
                }
                int portions = Math.min(left, free.get(recipient.getId()));
                proposed.add(new Allocation(time, batch.getId(), recipient.getId(), portions));
                left -= portions;
                free.put(recipient.getId(), free.get(recipient.getId()) - portions);
            }
        }
        return new Plan(time, revision, proposed);
    }

    public void commit(Plan plan) throws IOException {
        if (!preview(plan.time()).equals(plan)) {
            throw new IllegalArgumentException("The data changed. Preview again before confirming.");
        }
        if (plan.allocations().isEmpty()) {
            throw new IllegalArgumentException("The plan has no portions to allocate.");
        }
        List<Allocation> history = new ArrayList<>(state.history());
        history.addAll(plan.allocations());
        save(new Snapshot(state.batches(), state.recipients(), history));
    }

    public Map<Integer, Integer> remainingByBatch() {
        Map<Integer, Integer> used = usedPortions(true);
        Map<Integer, Integer> remaining = new HashMap<>();
        for (FoodBatch batch : state.batches()) {
            remaining.put(batch.getId(), batch.getPortions() - used.getOrDefault(batch.getId(), 0));
        }
        return remaining;
    }

    public Map<Integer, Integer> remainingByRecipient() {
        Map<Integer, Integer> used = usedPortions(false);
        Map<Integer, Integer> remaining = new HashMap<>();
        for (Recipient recipient : state.recipients()) {
            remaining.put(recipient.getId(), recipient.getCapacity() - used.getOrDefault(recipient.getId(), 0));
        }
        return remaining;
    }

    public Totals totalsAt(LocalDateTime time) {
        Map<Integer, Integer> remaining = remainingByBatch();
        long registered = 0;
        long available = 0;
        long expired = 0;
        for (FoodBatch batch : state.batches()) {
            registered += batch.getPortions();
            int left = remaining.get(batch.getId());
            if (batch.hasExpiredAt(time)) {
                expired += left;
            } else {
                available += left;
            }
        }
        return new Totals(registered, registered - available - expired, available, expired);
    }

    /** Creates one reproducible, fictional redistribution session in an empty demo file. */
    public void seedDemoIfEmpty() throws IOException {
        if (!state.batches().isEmpty() || !state.recipients().isEmpty()) {
            return;
        }
        save(new Snapshot(
                List.of(new ShelfStableBatch(1, "Bread rolls", "Campus Bakery", 18, DEMO_TIME.plusHours(2)),
                        new ChilledBatch(2, "Yoghurt cups", "Main Cafeteria", 24, DEMO_TIME.plusHours(1)),
                        new ChilledBatch(3, "Rice boxes", "Main Cafeteria", 8, DEMO_TIME.minusHours(1)),
                        new ShelfStableBatch(4, "Cracker packs", "Campus Mart", 10, DEMO_TIME.plusHours(6))),
                List.of(new Recipient(1, "Student Pantry", false, 15),
                        new Recipient(2, "Community Fridge", true, 12),
                        new Recipient(3, "Residence Hall", false, 6)),
                List.of()));
    }

    private Map<Integer, Integer> usedPortions(boolean byBatch) {
        Map<Integer, Integer> used = new HashMap<>();
        for (Allocation allocation : state.history()) {
            int id = byBatch ? allocation.batchId() : allocation.recipientId();
            used.merge(id, allocation.portions(), Math::addExact);
        }
        return used;
    }

    private void save(Snapshot candidate) throws IOException {
        store.save(candidate);
        state = candidate;
        revision++;
    }

    private void validateLoadedState() throws IOException {
        Map<Integer, FoodBatch> batches = new HashMap<>();
        for (FoodBatch batch : state.batches()) {
            if (batches.putIfAbsent(batch.getId(), batch) != null) {
                throw new IOException("Saved data contains duplicate food batch IDs.");
            }
        }
        Map<Integer, Recipient> recipients = new HashMap<>();
        for (Recipient recipient : state.recipients()) {
            if (recipients.putIfAbsent(recipient.getId(), recipient) != null) {
                throw new IOException("Saved data contains duplicate recipient IDs.");
            }
        }
        LocalDateTime previous = LocalDateTime.MIN;
        for (Allocation allocation : state.history()) {
            FoodBatch batch = batches.get(allocation.batchId());
            Recipient recipient = recipients.get(allocation.recipientId());
            if (allocation.time().isBefore(previous) || batch == null || recipient == null
                    || batch.hasExpiredAt(allocation.time()) || !batch.canGoTo(recipient)) {
                throw new IOException("Saved allocation history contains an invalid transfer.");
            }
            previous = allocation.time();
        }
        try {
            if (remainingByBatch().values().stream().anyMatch(left -> left < 0)
                    || remainingByRecipient().values().stream().anyMatch(left -> left < 0)) {
                throw new IOException("Saved allocations exceed inventory or recipient capacity.");
            }
        } catch (ArithmeticException error) {
            throw new IOException("Saved allocation totals exceed the supported range.", error);
        }
    }
}
