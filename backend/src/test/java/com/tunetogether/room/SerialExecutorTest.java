package com.tunetogether.room;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Foundational correctness check for the single-writer primitive every PartyRoom
 * mutation relies on: tasks submitted to one SerialExecutor run one at a time, even
 * when submitted concurrently from many threads and even though they execute on a
 * shared multi-threaded delegate pool.
 */
class SerialExecutorTest {

    @Test
    void tasksNeverOverlapEvenUnderConcurrentSubmission() throws Exception {
        ExecutorService delegate = Executors.newVirtualThreadPerTaskExecutor();
        SerialExecutor serial = new SerialExecutor(delegate);

        int n = 500;
        List<Integer> observedOrder = new CopyOnWriteArrayList<>();
        AtomicInteger concurrentlyActive = new AtomicInteger(0);
        AtomicInteger maxObservedConcurrency = new AtomicInteger(0);

        // One future per logical task, completed from INSIDE the task body -- this is
        // what we actually await, since serial.execute(...) itself returns as soon as
        // the task is enqueued, well before the task body has necessarily run.
        List<CompletableFuture<Void>> taskDone = new ArrayList<>();
        ExecutorService callerPool = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch startLatch = new CountDownLatch(1);
        for (int i = 0; i < n; i++) {
            int idx = i;
            CompletableFuture<Void> done = new CompletableFuture<>();
            taskDone.add(done);
            callerPool.execute(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                serial.execute(() -> {
                    int active = concurrentlyActive.incrementAndGet();
                    maxObservedConcurrency.updateAndGet(prev -> Math.max(prev, active));
                    observedOrder.add(idx);
                    concurrentlyActive.decrementAndGet();
                    done.complete(null);
                });
            });
        }
        startLatch.countDown();
        CompletableFuture.allOf(taskDone.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);

        assertEquals(1, maxObservedConcurrency.get(), "no two tasks should ever run concurrently");
        assertEquals(n, observedOrder.size(), "every submitted task must eventually run exactly once");

        callerPool.shutdown();
        delegate.shutdown();
    }
}
