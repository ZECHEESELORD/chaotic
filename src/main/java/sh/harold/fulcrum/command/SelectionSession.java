package sh.harold.fulcrum.command;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record SelectionSession(UUID playerId, Instant startedAt) {

    private static final Duration TIMEOUT = Duration.ofMinutes(2);

    public boolean expired() {
        return Instant.now().isAfter(this.startedAt.plus(TIMEOUT));
    }
}
