package com.tunetogether.room;

import java.util.ArrayDeque;
import java.util.concurrent.Executor;

/**
 * The classic {@link java.util.concurrent.Executor} javadoc "serial executor" pattern:
 * guarantees that at most one task submitted to a given instance is ever running at a
 * time, in FIFO submission order, while the actual execution happens on a shared
 * delegate pool (typically {@link java.util.concurrent.Executors#newVirtualThreadPerTaskExecutor()}).
 *
 * <p>This is the whole trick that lets a {@link PartyRoom}'s mutation logic be
 * completely lock-free internally: by construction, exactly one thread is ever
 * inside a room's own task body at any moment, so the room needs no locks of its
 * own -- only a single-writer discipline enforced by funneling every mutation
 * through its own {@code SerialExecutor}.
 *
 * <p>The {@code synchronized} blocks below only ever touch the {@link #tasks} deque
 * and the {@link #active} reference -- never I/O, never a room's business logic --
 * so they cannot pin a virtual-thread carrier. An idle room costs one empty deque
 * and no dedicated thread: nothing is scheduled on the delegate until work arrives.
 */
public final class SerialExecutor implements Executor {

    private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    private final Executor delegate;
    private Runnable active;

    public SerialExecutor(Executor delegate) {
        this.delegate = delegate;
    }

    @Override
    public synchronized void execute(Runnable task) {
        tasks.offer(() -> {
            try {
                task.run();
            } finally {
                scheduleNext();
            }
        });
        if (active == null) {
            scheduleNext();
        }
    }

    private synchronized void scheduleNext() {
        active = tasks.poll();
        if (active != null) {
            delegate.execute(active);
        }
    }
}
