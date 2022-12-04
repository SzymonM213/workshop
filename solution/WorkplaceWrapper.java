package cp2022.solution;

import cp2022.base.Workplace;
import cp2022.base.WorkplaceId;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;

public class WorkplaceWrapper extends Workplace {
    private final Workplace workplace;
    public boolean occupied;
    public final List<WorkplaceId> queue; // queue of workers waiting to make switchTo() to this workplace
    private CountDownLatch waitForPrevUser; // latch to make sure that previous workplace's user finished switchTo()
    private CountDownLatch letPrevWorkplaceUse; // latch to let somebody use previous workplace
    private final Semaphore waitForSwitch; // workplace's worker who wants to switch to another
                                           // (occupied) workplace waits here
    private final Semaphore waitToEnter; // everyone who wants to enter this workplace (but it's occupied) waits here

    public WorkplaceWrapper(Workplace workplace) {
        super(workplace.getId());
        this.workplace = workplace;
        this.occupied = false;
        this.queue = new ArrayList<>();
        this.waitForSwitch = new Semaphore(0);
        this.waitToEnter = new Semaphore(0);
    }

    private void await(CountDownLatch l) {
        try {
            l.await();
        } catch (InterruptedException e) {
            throw new RuntimeException("panic: unexpected thread interruption");
        }
    }

    public void waitForSwitchAcquire() {
        try {
            waitForSwitch.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException("panic: unexpected thread interruption");
        }
    }

    public void waitForSwitchRelease() {
        waitForSwitch.release();
    }

    public void waitToEnterAcquire() {
        try {
            waitToEnter.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException("panic: unexpected thread interruption");
        }
    }
    public void waitToEnterRelease() {
        waitToEnter.release();
    }

    public void setWaitForPrevUser(CountDownLatch l) {
        waitForPrevUser = l;
    }
    public void setLetPrevWorkplaceUse(CountDownLatch l) {
        letPrevWorkplaceUse = l;
    }

    public boolean isSomebodyWaitingToEnter() {
        return waitToEnter.hasQueuedThreads();
    }

    public void use() {
        if (letPrevWorkplaceUse != null) {
            letPrevWorkplaceUse.countDown();
        }
        if (waitForPrevUser != null) {
            waitForPrevUser.countDown();
            await(waitForPrevUser); // waits for the next
        }
        workplace.use();
    }
}