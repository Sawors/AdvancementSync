package io.github.sawors.advancementsync.progress;

import java.time.LocalDateTime;
import java.util.UUID;

public record CriterionUnlock(
        UUID playerId,
        LocalDateTime dateTime,
        String criterion) {
}
