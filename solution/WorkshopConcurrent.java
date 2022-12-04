package cp2022.solution;

import cp2022.base.Workplace;
import cp2022.base.WorkplaceId;
import cp2022.base.Workshop;

import java.util.*;
import java.util.concurrent.*;

public class WorkshopConcurrent implements Workshop {
    private volatile int countLeavingWorkers;
    private final Map<WorkplaceId, WorkplaceWrapper> workplaces;
    private final ConcurrentMap<WorkplaceId, WorkplaceId> transferWishes; // key - where is the worker,
                                                                          // value - where he wants to go
    private final Map<Long, WorkplaceId> workers; // current workplace of all workers in the workshop
    private final Semaphore mutex;
    private final Semaphore populationLimit;
    private volatile boolean isCycle; // tells the awakened worker if he is part of a cycle

    public WorkshopConcurrent(Collection<Workplace> workplaces) {
        this.workplaces = new HashMap<>();
        for(var v: workplaces) {
            this.workplaces.put(v.getId(), new WorkplaceWrapper(v));
        }
        this.transferWishes = new ConcurrentHashMap<>();
        this.mutex = new Semaphore(1);
        this.populationLimit = new Semaphore(2 * this.workplaces.size(), true);
        this.workers = new HashMap<>();
        this.countLeavingWorkers = 2 * this.workplaces.size();
        this.isCycle = false;
    }

    private void acquire(Semaphore s) {
        try {
            s.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException("panic: unexpected thread interruption");
        }
    }

    public Workplace enter(WorkplaceId wid) {
        WorkplaceWrapper newWorkplace = workplaces.get(wid);
        acquire(populationLimit);
        acquire(mutex);
        if (newWorkplace.occupied) {
            mutex.release();
            newWorkplace.waitToEnterAcquire();
        }
        newWorkplace.occupied = true;
        workers.put(Thread.currentThread().getId(), wid);
        mutex.release();
        return workplaces.get(wid);
    }

    // returns the length of the cycle beginning from the start workplace, if there is no cycle returns 0
    private int cycleLength(WorkplaceId start) {
        int result = 1;
        WorkplaceId tmp = transferWishes.get(start);
        while (tmp != start && transferWishes.containsKey(tmp)) {
            tmp = transferWishes.get(tmp);
            result++;
        }
        if (tmp == start) return result;
        return 0;
    }

    private void letSomebodyIn(WorkplaceWrapper workplace) {
        if (!workplace.queue.isEmpty()) {
            WorkplaceId workerToPass = workplace.queue.remove(0);
            workplaces.get(workerToPass).waitForSwitchRelease();
        }
        else if (workplace.isSomebodyWaitingToEnter()) {
            workplace.waitToEnterRelease();
        }
        else {
            workplace.occupied = false;
            mutex.release();
        }
    }

    public Workplace switchTo(WorkplaceId wid) {
        acquire(mutex);
        int length;
        WorkplaceWrapper newWorkplace = workplaces.get(wid);
        WorkplaceId oldId = workers.get(Thread.currentThread().getId());
        WorkplaceWrapper oldWorkplace = workplaces.get(oldId);
        if (wid == oldId) {
            mutex.release();
            return newWorkplace;
        }
        if (newWorkplace.occupied) {
            transferWishes.put(oldId, wid);
            newWorkplace.queue.add(oldId);
            length = cycleLength(oldId);
            if (length > 0) {
                isCycle = true;
                CountDownLatch waitForCycle = new CountDownLatch(length);
                WorkplaceWrapper whereIAm = newWorkplace;
                WorkplaceWrapper whereIGo;
                newWorkplace.queue.remove(oldWorkplace.getId());
                newWorkplace.setLetPrevWorkplaceUse(null);
                newWorkplace.setWaitForPrevUser(waitForCycle);
                // the worker who found a cycle wakes up all workers from it
                while (whereIAm != oldWorkplace) {
                    whereIGo = workplaces.get(transferWishes.get(whereIAm.getId()));
                    whereIGo.queue.remove(whereIAm.getId());
                    whereIGo.setLetPrevWorkplaceUse(null);
                    whereIGo.setWaitForPrevUser(waitForCycle);
                    whereIAm.waitForSwitchRelease();
                    whereIAm = whereIGo;
                    acquire(mutex);
                    isCycle = true;
                }
            }
            else {
                mutex.release();
                oldWorkplace.waitForSwitchAcquire();
            }
            transferWishes.remove(oldId);
        }
        workers.remove(Thread.currentThread().getId());
        workers.put(Thread.currentThread().getId(), wid);
        if (!isCycle) {
            CountDownLatch latch = new CountDownLatch(2);
            oldWorkplace.setWaitForPrevUser(latch);
            newWorkplace.setLetPrevWorkplaceUse(latch);
            newWorkplace.occupied = true;
            letSomebodyIn(oldWorkplace);
        } else {
            isCycle = false;
            mutex.release();
        }
        return newWorkplace;
    }

    public void leave() {
        acquire(mutex);
        countLeavingWorkers--;
        if(countLeavingWorkers == 0) {
            for (int i = 0; i < 2 * workplaces.size(); i++) {
                populationLimit.release();
            }
            countLeavingWorkers = 2 * workplaces.size();
        }
        WorkplaceWrapper myWorkplace = workplaces.get(workers.get(Thread.currentThread().getId()));
        workers.remove(Thread.currentThread().getId());
        letSomebodyIn(myWorkplace);
    }
}