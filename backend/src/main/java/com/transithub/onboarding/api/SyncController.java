package com.transithub.onboarding.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.transithub.onboarding.domain.DraftSnapshot;
import com.transithub.onboarding.repository.RegistrationSyncRepository;
import com.transithub.onboarding.service.RegistrationSyncService;
import com.transithub.onboarding.service.SyncConflictService;

import jakarta.validation.Valid;

/**
 * Offline synchronisation (UC-01.01-02, -18; AC-R1-SYN-01…08).
 *
 * <p>A device sends its drafts in a batch; each gets its own outcome. Send
 * an {@code Idempotency-Key} per batch: a retried batch then replays the
 * original answer. Evidence and signatures captured offline are uploaded
 * afterwards through the registration endpoints.
 */
@RestController
class SyncController {

    private final RegistrationResponseFactory responses;

    SyncController(RegistrationResponseFactory responses, RegistrationSyncService syncService,
                   SyncConflictService conflicts) {
        this.responses = responses;
        this.syncService = syncService;
        this.conflicts = conflicts;
    }

    private final RegistrationSyncService syncService;
    private final SyncConflictService conflicts;

    record SyncResponse(List<RegistrationSyncService.SyncResult> results) {
    }

    @PostMapping("/api/v1/sync/registrations")
    SyncResponse sync(@Valid @RequestBody RegistrationRequests.SyncBatchRequest request) {
        List<RegistrationSyncService.SyncItem> items = request.items().stream()
                .map(item -> new RegistrationSyncService.SyncItem(
                        item.id(), item.baseVersion(), item.snapshot().toDomain()))
                .toList();
        return new SyncResponse(syncService.sync(request.deviceId(), items));
    }

    /** Both versions of each conflict, for the supervisor to compare (AC-R1-SYN-05). */
    @GetMapping("/api/v1/registrations/{id}/sync-conflicts")
    List<ConflictResponse> conflicts(@PathVariable UUID id) {
        return conflicts.conflicts(id).stream().map(ConflictResponse::from).toList();
    }

    /** AC-R1-SYN-06: keep the server copy, take the device's, or supply a merged version. */
    @PostMapping("/api/v1/registrations/{id}/sync-conflicts/{conflictId}/resolution")
    RegistrationResponse resolve(@PathVariable UUID id, @PathVariable UUID conflictId,
                                 @Valid @RequestBody RegistrationRequests.ConflictResolutionRequest request) {
        return responses.from(conflicts.resolve(id, conflictId, request.resolution(),
                request.merged() == null ? null : request.merged().toDomain(), request.reason()));
    }

    /** Synced data that could not be applied, awaiting review (AC-R1-SYN-07). */
    @GetMapping("/api/v1/sync/quarantine")
    List<RegistrationSyncRepository.Quarantined> quarantine() {
        return conflicts.quarantine();
    }

    record ConflictResponse(
            UUID id, String deviceId, String deviceUser, String serverEditor, @Nullable Long baseVersion,
            long serverVersion, DraftSnapshot deviceSnapshot, DraftSnapshot serverSnapshot, Instant detectedAt,
            boolean open, @Nullable String resolution, @Nullable String resolutionReason,
            @Nullable String resolvedBy, @Nullable Instant resolvedAt) {

        static ConflictResponse from(RegistrationSyncRepository.Conflict conflict) {
            return new ConflictResponse(conflict.id(), conflict.deviceId(), conflict.deviceUser(),
                    conflict.serverEditor(), conflict.baseVersion(), conflict.serverVersion(),
                    conflict.deviceSnapshot(), conflict.serverSnapshot(), conflict.detectedAt(), conflict.isOpen(),
                    conflict.resolution(), conflict.resolutionReason(), conflict.resolvedBy(), conflict.resolvedAt());
        }
    }
}
