package com.jobpilot.api.domain.matching.service;

import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Ensures profile saving returns immediately; the expensive refresh starts only after the DB commit. */
@Component
public class JobMatchRefreshScheduler {
    private final TaskExecutor taskExecutor;
    private final JobMatchGenerationService generationService;
    private final Set<Long> queuedMembers = ConcurrentHashMap.newKeySet();

    public JobMatchRefreshScheduler(TaskExecutor taskExecutor, JobMatchGenerationService generationService) {
        this.taskExecutor = taskExecutor;
        this.generationService = generationService;
    }

    public void enqueueForMember(Long memberId) {
        Runnable job = () -> schedule(memberId);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            taskExecutor.execute(job);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { taskExecutor.execute(job); }
        });
    }

    private void schedule(Long memberId) {
        if (!queuedMembers.add(memberId)) return;
        try {
            // Profile and skill are saved as two consecutive HTTP calls from the same form.
            Thread.sleep(1_000);
            generationService.regenerateForMember(memberId);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            queuedMembers.remove(memberId);
        }
    }
}
