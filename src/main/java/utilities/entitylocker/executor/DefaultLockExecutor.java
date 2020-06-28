package utilities.entitylocker.executor;

import utilities.entitylocker.EntityLocker.PacketReentrantLock;
import utilities.entitylocker.exception.EntityLockerException;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class DefaultLockExecutor implements ILockExecutor {

    public <R> R execute(final PacketReentrantLock lock, final Callable<R> task) {
        lock.lock();
        R result;
        try {
            result = task.call();
        } catch (Exception e) {
            throw new EntityLockerException("An error occurred during execution: " + e.getMessage());
        } finally {
            lock.unlock();
        }
        return result;
    }

    public void execute(final PacketReentrantLock lock, final Runnable task) {
        lock.lock();
        try {
            task.run();
        } finally {
            lock.unlock();
        }
    }


    @Override
    public <R> R tryExecute(final PacketReentrantLock lock, final Callable<R> task, long lockTimeout, TimeUnit lockTimeoutUnit) {
        try {
            lock.tryLock(lockTimeout, lockTimeoutUnit);
            return task.call();
        } catch (InterruptedException e) {
            throw new EntityLockerException(e.getMessage());
        } catch (Exception e) {
            throw new EntityLockerException("An error occurred during execution: " + e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void tryExecute(final PacketReentrantLock lock, final Runnable task, long lockTimeout, TimeUnit lockTimeoutUnit) {
        try {
            lock.tryLock(lockTimeout, lockTimeoutUnit);
            task.run();
        } catch (InterruptedException e) {
            throw new EntityLockerException(e.getMessage());
        } finally {
            lock.unlock();
        }
    }
}
