package my.edu.cityu.rescue.model;

/** A campus pickup partner with capacity for a single redistribution session. */
public final class Recipient {
    private final int id;
    private final String name;
    private final boolean refrigeration;
    private int capacity;

    public Recipient(int id, String name, boolean refrigeration, int capacity) {
        if (id < 1) {
            throw new IllegalArgumentException("Recipient ID must be positive.");
        }
        this.id = id;
        this.name = FoodBatch.requireLabel(name, "Recipient");
        this.refrigeration = refrigeration;
        setCapacity(capacity);
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public boolean hasRefrigeration() { return refrigeration; }
    public int getCapacity() { return capacity; }

    public void setCapacity(int capacity) {
        if (capacity < 0 || capacity > 10_000) {
            throw new IllegalArgumentException("Capacity must be between 0 and 10,000 portions.");
        }
        this.capacity = capacity;
    }
}
