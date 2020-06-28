package utilities.entitylocker;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import utilities.entitylocker.exception.DeadlockException;

import static org.junit.Assert.*;

/**
 * Unit test for entity locker utility. {@link EntityLocker}.
 */
public class EntityLockerTest {

    @Test
    public void testSameKeysAccess() {
        EntityLocker<Object> locker = new EntityLocker<>();
        Object key1 = new Object();
        CountDownLatch latch = new CountDownLatch(4);
        AtomicReference<Exception> exception = new AtomicReference<>();
        AtomicBoolean isRunning = new AtomicBoolean(false);

        // Test with slow task
        createAndRunSingleAccessTask(locker, key1, latch, isRunning, 100, exception);
        createAndRunSingleAccessTask(locker, key1, latch, isRunning, 100, exception);

        // Test with fast task
        createAndRunSingleAccessTask(locker, key1, latch, isRunning, 0, exception);
        createAndRunSingleAccessTask(locker, key1, latch, isRunning, 0, exception);

        try {
            // Waiting for the completion of all tasks
            latch.await();

            // Check that there is no MultipleAccessException
            assertNull(exception.get());
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }

        // Same test but with enabled deadlock protection
        locker = new EntityLocker<>(true);
        key1 = new Object();
        latch = new CountDownLatch(4);
        exception = new AtomicReference<>();
        isRunning = new AtomicBoolean(false);

        // Test with slow task
        createAndRunSingleAccessTask(locker, key1, latch, isRunning, 100, exception);
        createAndRunSingleAccessTask(locker, key1, latch, isRunning, 100, exception);

        // Test with fast task
        createAndRunSingleAccessTask(locker, key1, latch, isRunning, 0, exception);
        createAndRunSingleAccessTask(locker, key1, latch, isRunning, 0, exception);
        try {
            // Waiting for the completion of all tasks
            latch.await();
            // Check that there is no MultipleAccessException
            assertNull(exception.get());
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testDiffKeysAccess() {
        EntityLocker<Object> locker = new EntityLocker<>();
        Object key1 = new Object();
        Object key2 = new Object();
        CountDownLatch latch = new CountDownLatch(2);
        AtomicBoolean isRunning = new AtomicBoolean(false);
        AtomicReference<Exception> exception = new AtomicReference<>();

        createAndRunSingleAccessTask(locker, key1, latch, isRunning, 100, exception);
        createAndRunSingleAccessTask(locker, key2, latch, isRunning, 100, exception);

        try {
            latch.await();
            // Expecting MultipleAccessException because two threads simultaneously performed a task inside EntityLocker
            assertNotNull(exception.get());
            assertTrue(MultipleAccessException.class == exception.get().getClass());
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }

        // Same test but with enabled deadlock protection
        locker = new EntityLocker<>(true);
        key1 = new Object();
        key2 = new Object();
        latch = new CountDownLatch(2);
        isRunning = new AtomicBoolean(false);
        exception = new AtomicReference<>();

        createAndRunSingleAccessTask(locker, key1, latch, isRunning, 100, exception);
        createAndRunSingleAccessTask(locker, key2, latch, isRunning, 100, exception);

        try {
            latch.await();
            // Expecting MultipleAccessException because two threads simultaneously performed a task inside EntityLocker
            assertNotNull(exception.get());
            assertTrue(MultipleAccessException.class == exception.get().getClass());
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testLockTimeout() {
        EntityLocker<Object> locker = new EntityLocker<>();
        Object key1 = new Object();
        CountDownLatch latch = new CountDownLatch(2);
        AtomicReference<Exception> exception = new AtomicReference<>();
        AtomicBoolean isRunning = new AtomicBoolean(false);

        // Perform slow tasks which take longer than the set EntityLocker timeout
        createAndRunSingleAccessTask(locker, key1, latch, isRunning, 1000, exception, 500, TimeUnit.MILLISECONDS);
        createAndRunSingleAccessTask(locker, key1, latch, isRunning, 1000, exception, 500, TimeUnit.MILLISECONDS);

        try {
            latch.await();
            // Expecting IllegalMonitorStateException after timeout
            assertNotNull(exception.get());
            assertTrue(IllegalMonitorStateException.class == exception.get().getClass());
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }

        latch = new CountDownLatch(2);
        exception = new AtomicReference<>();
        isRunning = new AtomicBoolean(false);

        // Perform fast commands
        createAndRunSingleAccessTask(locker, key1, latch, isRunning, 100, exception, 500, TimeUnit.MILLISECONDS);
        createAndRunSingleAccessTask(locker, key1, latch, isRunning, 100, exception, 500, TimeUnit.MILLISECONDS);

        try {
            latch.await(3000, TimeUnit.MILLISECONDS);
            assertNull(exception.get());
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }

        latch = new CountDownLatch(2);
        exception = new AtomicReference<>();

        // Perform slow commands with difference keys
        createAndRunSingleAccessTask(locker, key1, latch, new AtomicBoolean(false), 1000, exception, 500, TimeUnit.MILLISECONDS);
        createAndRunSingleAccessTask(locker, new Object(), latch, new AtomicBoolean(false), 1000, exception, 500, TimeUnit.MILLISECONDS);

        try {
            latch.await(3000, TimeUnit.MILLISECONDS);
            assertNull(exception.get());
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }

    }

    @Test
    public void testNullKey() {
        EntityLocker<Object> locker = new EntityLocker<>();
        Exception e = null;
        try {
            locker.lockAndExecute(null, () -> {
                try {
                    new SingleAccessTask().doTask(null, new CountDownLatch(1), new AtomicBoolean(), 100);
                } catch (MultipleAccessException see) {
                    assert (false);
                }
            });
        } catch (Exception ex) {
            e = ex;
        }

        assertNull(e);
    }

    @Test
    public void testCallableResult() {
        EntityLocker<Object> locker = new EntityLocker<>();
        Callable<Object> task = () -> "result";
        Object result = locker.lockAndExecute(new Object(), task);
        assertTrue(Objects.equals(result, "result"));
    }

    @Test
    public void testDeadlockPrevention() {
        EntityLocker<Object> locker = new EntityLocker<>(true);

        // Test Deadlock with 2 threads
        Object key1 = new Object();
        Object key2 = new Object();
        CountDownLatch latch = new CountDownLatch(2);
        AtomicReference<Exception> exception = new AtomicReference<>();
        runReentrantThread(locker, key1, key2, exception, latch);
        runReentrantThread(locker, key2, key1, exception, latch);

        try {
            latch.await();
            System.out.println(exception.toString());
            assertTrue(exception.get().getClass() == DeadlockException.class);
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }

        // Test Deadlock with 3 threads
        Object key3 = new Object();
        latch = new CountDownLatch(3);
        exception = new AtomicReference<>();
        runReentrantThread(locker, key1, key2, exception, latch);
        runReentrantThread(locker, key2, key3, exception, latch);
        runReentrantThread(locker, key3, key1, exception, latch);

        try {
            latch.await();
            System.out.println(exception.toString());
            assertTrue(exception.get().getClass() == DeadlockException.class);
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }

        // Test Deadlock with 4 threads
        Object key4 = new Object();
        latch = new CountDownLatch(4);
        exception = new AtomicReference<>();
        runReentrantThread(locker, key1, key2, exception, latch);
        runReentrantThread(locker, key2, key3, exception, latch);
        runReentrantThread(locker, key3, key4, exception, latch);
        runReentrantThread(locker, key4, key1, exception, latch);
        try {
            latch.await();
            System.out.println(exception.toString());
            assertTrue(exception.get().getClass() == DeadlockException.class);
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }

        // Testing without deadlock (concurrent thread start)
        latch = new CountDownLatch(2);
        exception = new AtomicReference<>();
        runReentrantThread(locker, key1, key2, exception, latch);
        try {
            Thread.sleep(220);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        runReentrantThread(locker, key2, key1, exception, latch);

        try {
            latch.await();
            assertNull(exception.get());
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }

        // Testing without deadlock (1 reentrant thread, 1 key)
        exception.set(null);
        latch = new CountDownLatch(1);
        runReentrantThread(locker, key1, key1, exception, latch);

        try {
            latch.await();
            assertNull(exception.get());
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }

        // Testing without deadlock (1 reentrant thread, 2 keys)
        exception.set(null);
        latch = new CountDownLatch(1);
        runReentrantThread(locker, key1, key2, exception, latch);

        try {
            latch.await();
            assertNull(exception.get());
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }

        // Testing without deadlock (2 reentrant threads, all keys similar)
        exception.set(null);
        latch = new CountDownLatch(2);
        runReentrantThread(locker, key2, key1, exception, latch);
        runReentrantThread(locker, key2, key1, exception, latch);

        try {
            latch.await();
            assertNull(exception.get());
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }

        // Testing without deadlock (2 reentrant threads, only first keys
        // similar)
        exception.set(null);
        latch = new CountDownLatch(2);
        runReentrantThread(locker, key2, key1, exception, latch);
        runReentrantThread(locker, key2, key2, exception, latch);

        try {
            latch.await();
            assertNull(exception.get());
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }
    }
    
    private static void runReentrantThread(EntityLocker<Object> locker, Object key1, Object key2,
                                           AtomicReference<Exception> exception, CountDownLatch latch) {
        new Thread(() -> {
            try {
                locker.lockAndExecute(key2, () -> {
                    try {
                        System.out.println(System.currentTimeMillis() + " Task is started by: " + Thread.currentThread().getName()
                                + " Key: " + key2);
                        Thread.sleep(50);
                        locker.lockAndExecute(key1, () -> {
                            try {
                                System.out.println(System.currentTimeMillis() + " Task is started by: " + Thread.currentThread().getName()
                                        + " Key: " + key1);
                                Thread.sleep(50);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            System.out.println(System.currentTimeMillis() + " Task is finished by: "
                                    + Thread.currentThread().getName() + " Key: " + key1);
                        });

                    } catch (Exception e) {
                        exception.set(e);
                    } finally {
                        latch.countDown();
                    }
                    System.out.println(System.currentTimeMillis() + " Task is finished by: "
                            + Thread.currentThread().getName() + " Key: " + key2);
                });
            } catch (Exception ex) {
                System.out.println("deadlock: " + ex.getMessage());

                exception.set(ex);
                latch.countDown();
            }
        }).start();
    }

    private static SingleAccessTask createAndRunSingleAccessTask(EntityLocker<Object> locker, Object key, CountDownLatch latch,
                                                                 AtomicBoolean isRunning, int ms, AtomicReference<Exception> exception, long timeout, TimeUnit timeUnit) {
        SingleAccessTask task = new SingleAccessTask();
        Thread thread = new Thread(() -> {
            try {
                locker.tryLockAndExecute(key, () -> {
                    try {
                        task.doTask(new Object(), latch, isRunning, ms);
                    } catch (MultipleAccessException e) {
                        exception.set(e);
                    }
                }, timeout, timeUnit);
            } catch (IllegalMonitorStateException e) {
                exception.set(e);
            }
        });
        thread.start();
        return task;
    }

    private static SingleAccessTask createAndRunSingleAccessTask(EntityLocker<Object> locker, Object key, CountDownLatch latch,
                                                                 AtomicBoolean isRunning, int ms, AtomicReference<Exception> exception) {
        SingleAccessTask task = new SingleAccessTask();
        Thread thread = new Thread(() -> {
            try {
                locker.lockAndExecute(key, () -> {
                    try {
                        task.doTask(new Object(), latch, isRunning, ms);
                    } catch (MultipleAccessException e) {
                        exception.set(e);
                    }
                });
            } catch (IllegalMonitorStateException e) {
                exception.set(e);
            }
        });
        thread.start();
        return task;
    }

    static class SingleAccessTask {
        public void doTask(Object key, CountDownLatch latch, AtomicBoolean isRunning, int ms) throws MultipleAccessException {
            try {
                if (isRunning.getAndSet(true)) {
                    System.out.println(System.currentTimeMillis() + " Can't start task because it is already being executed by another thread. Current thread: " + Thread.currentThread().getName()
                            + " Key: " + key);
                    throw new MultipleAccessException();
                }
                System.out.println(System.currentTimeMillis() + " Task is started by: " + Thread.currentThread().getName()
                        + " Key: " + key);
                try {
                    Thread.sleep(ms); // task time simulation
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println(System.currentTimeMillis() + " Task is finished by: "
                        + Thread.currentThread().getName() + " Key: " + key);
            } finally {
                isRunning.set(false);
                latch.countDown();
            }
        }
    }

    static class MultipleAccessException extends Exception {
    }
}
