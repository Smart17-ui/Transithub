package com.transithub.onboarding.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.transithub.identity.Authorization;
import com.transithub.identity.Permission;
import com.transithub.onboarding.domain.CompanyDetails;
import com.transithub.onboarding.domain.DraftSnapshot;
import com.transithub.onboarding.repository.RegistrationSyncRepository.Outcome;
import com.transithub.onboarding.service.RegistrationSyncService.SyncItem;
import com.transithub.onboarding.service.RegistrationSyncService.SyncResult;
import com.transithub.shared.id.UuidV7;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Review finding 2: an item that fails unexpectedly never aborts the rest of the batch. */
class RegistrationSyncServiceTest {

    private final RegistrationSyncProcessor processor = mock(RegistrationSyncProcessor.class);
    private final Authorization authorization = mock(Authorization.class);
    private final RegistrationSyncService service = new RegistrationSyncService(processor, authorization);

    @Test
    void aFailingItemIsQuarantinedAndTheNextOneIsStillProcessed() {
        when(authorization.currentUserId()).thenReturn(Optional.of(UUID.randomUUID()));
        when(authorization.has(Permission.REGISTRATION_CAPTURE)).thenReturn(true);
        SyncItem broken = item();
        SyncItem fine = item();
        when(processor.process("tablet", broken)).thenThrow(new IllegalStateException("boom"));
        when(processor.quarantine(eq("tablet"), eq(broken), anyString()))
                .thenReturn(new SyncResult(broken.id(), Outcome.QUARANTINED, null, null, null, "kept"));
        when(processor.process("tablet", fine))
                .thenReturn(new SyncResult(fine.id(), Outcome.CREATED, "REG-2026-000001", 0L, null, null));

        List<SyncResult> results = service.sync("tablet", List.of(broken, fine));

        assertEquals(Outcome.QUARANTINED, results.get(0).outcome());
        assertEquals(Outcome.CREATED, results.get(1).outcome());
        verify(processor).process("tablet", fine);
    }

    @Test
    void ifEvenQuarantineFailsTheItemIsReportedAndTheBatchStillContinues() {
        when(authorization.currentUserId()).thenReturn(Optional.of(UUID.randomUUID()));
        when(authorization.has(Permission.REGISTRATION_CAPTURE)).thenReturn(true);
        SyncItem broken = item();
        SyncItem fine = item();
        when(processor.process("tablet", broken)).thenThrow(new IllegalStateException("boom"));
        when(processor.quarantine(eq("tablet"), eq(broken), anyString())).thenThrow(new IllegalStateException("db down"));
        when(processor.process("tablet", fine))
                .thenReturn(new SyncResult(fine.id(), Outcome.CREATED, "REG-2026-000002", 0L, null, null));

        List<SyncResult> results = service.sync("tablet", List.of(broken, fine));

        assertEquals("Not accepted; please retry later.", results.get(0).message());
        assertEquals(Outcome.CREATED, results.get(1).outcome());
    }

    private static SyncItem item() {
        return new SyncItem(UuidV7.generate(), null, new DraftSnapshot(CompanyDetails.empty(), List.of()));
    }
}
