package utilities.entitylocker.executor;

import utilities.entitylocker.EntityLocker.PacketReentrantLock;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public interface ILockExecutor {
    <R> R execute(final PacketReentrantLock lock, final Callable<R> task);

    void execute(final PacketReentrantLock lock, final Runnable task);

    <R> R tryExecute(PacketReentrantLock lock, Callable<R> task, long lockTimeout, TimeUnit lockTimeoutUnit);

    void tryExecute(PacketReentrantLock lock, Runnable task, long lockTimeout, TimeUnit lockTimeoutUnit);
}
