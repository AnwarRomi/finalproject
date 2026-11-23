package my.edu.cityu.rescue.model;

import java.time.LocalDateTime;
import java.util.Objects;

/** The identity, quantity and pickup deadline shared by all donated food. */
public abstract class FoodBatch {
    private final int id;
    private final String name;
    private final String donor;
    private final int portions;
    private final LocalDateTime useBy;

    protected FoodBatch(int id, String name, String donor, int portions, LocalDateTime useBy) {
        if (id < 1 || portions < 1 || portions > 10_000) {
            throw new IllegalArgumentException("A food batch needs 1 to 10,000 portions.");
        }
        this.id = id;
        this.name = requireLabel(name, "Food");
        this.donor = requireLabel(donor, "Donor");
        this.portions = portions;
        this.useBy = Objects.requireNonNull(useBy);
    }

    static String requireLabel(String value, String label) {
        String clean = Objects.requireNonNull(value).strip();
        if (clean.isEmpty() || clean.length() > 60 || clean.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(label + " must contain 1 to 60 printable characters.");
        }
        return clean;
    }

    public final int getId() { return id; }
    public final String getName() { return name; }
    public final String getDonor() { return donor; }
    public final int getPortions() { return portions; }
    public final LocalDateTime getUseBy() { return useBy; }

    public final boolean hasExpiredAt(LocalDateTime time) {
        return !useBy.isAfter(time);
    }

    public abstract String getStorageType();
    public abstract boolean canGoTo(Recipient recipient);
}
