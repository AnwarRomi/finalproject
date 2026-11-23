package my.edu.cityu.rescue.model;

import java.time.LocalDateTime;

/** This batch can use either an ambient or refrigerated recipient. */
public final class ShelfStableBatch extends FoodBatch {
    public static final String STORAGE_TYPE = "Shelf-stable";

    public ShelfStableBatch(int id, String name, String donor, int portions, LocalDateTime useBy) {
        super(id, name, donor, portions, useBy);
    }

    @Override
    public String getStorageType() {
        return STORAGE_TYPE;
    }

    @Override
    public boolean canGoTo(Recipient recipient) {
        return true;
    }
}
