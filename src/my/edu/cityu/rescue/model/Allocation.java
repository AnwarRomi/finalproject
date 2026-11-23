package my.edu.cityu.rescue.model;

import java.time.LocalDateTime;
import java.util.Objects;

/** One committed or proposed transfer between a food batch and a recipient. */
public record Allocation(LocalDateTime time, int batchId, int recipientId, int portions) {
    public Allocation {
        Objects.requireNonNull(time);
        if (batchId < 1 || recipientId < 1 || portions < 1) {
            throw new IllegalArgumentException("An allocation needs positive IDs and portions.");
        }
    }
}
