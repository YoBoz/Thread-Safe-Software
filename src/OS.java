import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition; //Note that the 'notifyAll' method or similar polling mechanism MUST not be used
import java.util.HashMap;

// IMPORTANT:
//
//'Thread safe' and 'synchronized' classes (e.g. those in java.util.concurrent) other than the two imported above MUST not be used.
//
//You MUST not use the keyword 'synchronized', or any other `thread safe` classes or mechanisms  
//or any delays or 'busy waiting' (spin lock) methods. Furthermore, you must not use any delays such as Thread.sleep().

//However, you may import non-tread safe classes e.g.:

import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;

//Your OS class must handle exceptions locally i.e. it must not explicitly 'throw' exceptions 
//otherwise the compilation with the Test classes will fail!!!

public class OS implements OS_sim_interface {
    private final ReentrantLock lock = new ReentrantLock();
    private final TreeMap<Integer, Queue<Integer>> priorityQueues = new TreeMap<>();
    private final Map<Integer, Condition> conditions = new HashMap<>();
    private final Map<Integer, Integer> processPriorities = new HashMap<>();
    private int availableProcessors = 0;
    private int lastPid = -1;

    @Override
    public void set_number_of_processors(int nProcessors) {
        lock.lock();
        try {
            availableProcessors = nProcessors; // Set number of processor's to the entered value 
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int reg(int priority) {
        lock.lock();
        try {
            int pid = ++lastPid; // Assign a unique process ID
            priorityQueues.putIfAbsent(priority, new LinkedList<>()); // Create a queue for the priority if it doesn't exist
            conditions.put(pid, lock.newCondition()); // Create a condition object for the process
            processPriorities.put(pid, priority); // Store the process's priority
            return pid; // Return the assigned process ID
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void start(int ID) {
        lock.lock();
        try {
            int priority = processPriorities.get(ID); // Get the priority of the process
            Queue<Integer> queue = priorityQueues.get(priority); // Get the queue for the process's priority
            queue.add(ID); // Add the process to the queue
            // Wait if it's not the process's turn, there are no available processors, or it's not the highest priority process
            while ((queue.peek() == null || queue.peek() != ID) || availableProcessors <= 0 || !isHighestPriorityProcess(ID)) {
                conditions.get(ID).await();
            }
            queue.remove(ID); // Remove the process from the queue
            availableProcessors--; // Decrement the available processors count
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void schedule(int ID) {
        lock.lock();
        try {
            int priority = processPriorities.get(ID); // Get the priority of the process
            Queue<Integer> queue = priorityQueues.get(priority); // Get the queue for the process's priority
            queue.add(ID); // Add the process to the queue
            availableProcessors++; // Increment the available processors count
            notifyNextProcess(); // Notify the next proccess in line
            // Wait if it's not the process's turn or it's not the highest priority process
            while (queue.peek() == null || queue.peek() != ID || !isHighestPriorityProcess(ID)) {
                conditions.get(ID).await();
            }
            queue.remove(ID); // Remove the process from the queue
            availableProcessors--; // Decrement the available processors count
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void terminate(int ID) {
        lock.lock();
        try {
            availableProcessors++; // Increment the available processors count
            notifyNextProcess(); // Notify the next process in line
        } finally {
            lock.unlock();
        }
    }

    // Helper Function - Notify the next process in line to start execution
    private void notifyNextProcess() {
        for (Map.Entry<Integer, Queue<Integer>> entry : priorityQueues.entrySet()) {
            Queue<Integer> currentQueue = entry.getValue(); // Get the queue for the current priority level
            if (!currentQueue.isEmpty()) {
                Integer currentProcessId = currentQueue.peek(); // Get the ID of the next process in line
                Condition currentCondition = conditions.get(currentProcessId); // Get the condition object for the process
                if (currentCondition != null) {
                    currentCondition.signal(); // Signal the process to start execution
                    break;
                }
            }
        }
    }

    // Helper Function - Check if the process with the given ID is the highest priority process among all ready processes
    private boolean isHighestPriorityProcess(int ID) {
        int priority = processPriorities.get(ID); // Get the priority of the process
        // Check if all queues with higher priorities are empty
        return priorityQueues.headMap(priority, false).values().stream().allMatch(Queue::isEmpty);
    }
}