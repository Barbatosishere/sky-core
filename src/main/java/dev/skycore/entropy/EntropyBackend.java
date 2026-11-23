package dev.skycore.entropy;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Core-internal runtime seam. Pack integrations use the read-only SkyCoreApi. */
interface EntropyBackend {
    default boolean available() { return true; }
    @Nullable String teamSnapshot(UUID team);
    @Nullable String fieldSnapshot(ServerLevel level, BlockPos pos);
    boolean hasEvidence(UUID team, String evidenceId);
    InitializationResult initialize(UUID team, ServerLevel level, BlockPos pos);

    enum InitializationResult {
        INITIALIZED, ALREADY_INITIALIZED, NOT_READY, CHUNK_UNLOADED, POSITION_OCCUPIED, INVALID_POSITION, CONFLICT;

        public boolean successful() { return this == INITIALIZED || this == ALREADY_INITIALIZED; }
    }
}
