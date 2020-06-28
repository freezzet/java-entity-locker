package utilities.entitylocker.executor;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import utilities.entitylocker.EntityLocker;
import utilities.entitylocker.EntityLocker.PacketReentrantLock;
import utilities.entitylocker.exception.DeadlockException;
import utilities.entitylocker.exception.EntityLockerException;

public class DeadlockPreventionLockExecutor extends DefaultLockExecutor {
	private final Map<Thread, PacketReentrantLock> desiredLockByThread = new ConcurrentHashMap<>();

	public <R> R execute(final PacketReentrantLock lock, final Callable<R> task) {
		R result;

		try {
			if (!lock.tryLock(500, TimeUnit.MILLISECONDS)) {
				// Some thread already holds the lock so need to check a possible deadlock
				checkDeadlock(lock);

				lock.lock();
				desiredLockByThread.remove(Thread.currentThread());
			}
		} catch (InterruptedException e) {
			throw new EntityLockerException(e.getMessage());
		}

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
		try {
			if (!lock.tryLock(500, TimeUnit.MILLISECONDS)) {
				// Some thread already holds the lock so need to check a possible deadlock
				checkDeadlock(lock);

				lock.lock();
				desiredLockByThread.remove(Thread.currentThread());
			}
		} catch (InterruptedException e) {
			throw new EntityLockerException(e.getMessage());
		}

		try {
			task.run();
		} catch (Exception e) {
			throw new EntityLockerException("An error occurred during execution: " + e.getMessage());
		} finally {
			lock.unlock();
		}

	}

	private void checkDeadlock(final PacketReentrantLock lock) {
		Thread owner = lock.getOwner();
		if (owner != null) {

			/* The synchronization is required here.
			 * If the deadlock exists in the chain of locks, then it will remain unchanged throughout the whole check.
			 * However, if chain of locks changes during the deadlock check, it means there was no deadlock and
			 * this is necessary to prevent the addition of new desired locks to map until the current check is done.
			 */
			synchronized (desiredLockByThread) {
				desiredLockByThread.put(Thread.currentThread(), lock);

				PacketReentrantLock desiredLock = desiredLockByThread.get(owner);
				if (desiredLock != null) {
					StringBuilder chainOfLocksBuilder = new StringBuilder();
					chainOfLocksBuilder.append("\n\rCurrent thread '" + Thread.currentThread() + "' tries to lock '" + lock.getKey() + "'");

					while (desiredLock != null) {
						chainOfLocksBuilder.append("\n\rthat is locked by '" + owner + "' that tries to lock '" + desiredLock.getKey());

						Thread ownerOfDesiredLock = desiredLock.getOwner();
						if (ownerOfDesiredLock == null || ownerOfDesiredLock == owner) {
							break;
						}
						owner = ownerOfDesiredLock;

						if (owner == Thread.currentThread()) {
							chainOfLocksBuilder.append(" that is locked by the current thread");
							throw new DeadlockException("Deadlock is occurred. " + chainOfLocksBuilder.toString());
						}

						desiredLock = desiredLockByThread.get(owner);
					}
				}
			}
		}
	}
}
