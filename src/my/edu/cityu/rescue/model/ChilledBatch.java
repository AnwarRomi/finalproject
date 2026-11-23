package my.edu.cityu.rescue.model;

import java.time.LocalDateTime;

/** This batch can only go to a recipient with refrigeration. */
public final class ChilledBatch extends FoodBatch {
    public static final String STORAGE_TYPE = "Chilled";

    public ChilledBatch(int id, String name, String donor, int portions, LocalDateTime useBy) {
        super(id, name, donor, portions, useBy);
    }

    @Override
    public String getStorageType() {
        return STORAGE_TYPE;
    }

    @Override
    public boolean canGoTo(Recipient recipient) {
        return recipient.hasRefrigeration();
    }
}
