package com.tunetogether.ws;

import com.tunetogether.room.RoomReaper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic backstop sweep over every registered room (see {@link RoomReaper} for why
 * this is needed in addition to the eager reap chained after every leave/disconnect).
 */
@Component
public class RoomReaperScheduler {

    private final RoomReaper roomReaper;

    public RoomReaperScheduler(RoomReaper roomReaper) {
        this.roomReaper = roomReaper;
    }

    @Scheduled(fixedDelayString = "${app.room.reap-interval-ms}")
    public void sweep() {
        roomReaper.sweepOnce();
    }
}
