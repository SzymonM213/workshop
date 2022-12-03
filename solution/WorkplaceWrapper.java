package cp2022.solution;

import cp2022.base.Workplace;
import cp2022.base.WorkplaceId;
import cp2022.solution.WorkshopConcurrent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;

public class WorkplaceWrapper extends Workplace {
    private final Workplace workplace;
    public final Semaphore waitForSwitch; // ja siedze na tym stanowisku i czekam az skoncze switchTo
    public CountDownLatch waitForNext;
    public CountDownLatch waitForPrev;
    public boolean occupied;
    public final Semaphore waitToEnter;
    public final List<WorkplaceId> queue;


    public WorkplaceWrapper(Workplace workplace) {
        super(workplace.getId());
        this.workplace = workplace;
        this.waitForSwitch = new Semaphore(0);
        this.occupied = false;
        this.waitToEnter = new Semaphore(0);
        this.queue = new ArrayList<>();
    }

    private void await(CountDownLatch l) {
        try {
            l.await();
        } catch (InterruptedException e) {
            throw new RuntimeException("panic: unexpected thread interruption");
        }
    }

    private void prapareUse() {
        if (waitForPrev != null) {
            waitForPrev.countDown();
        }
        if (waitForNext != null) {
            waitForNext.countDown();
            await(waitForNext);
        }
    }

    public void use() {
        prapareUse();
        workplace.use();
    }
}
