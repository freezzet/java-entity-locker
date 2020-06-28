package utilities.entitylocker;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import utilities.entitylocker.exception.EntityLockerException;
import utilities.entitylocker.executor.DefaultLockExecutor;
import utilities.entitylocker.executor.DeadlockPreventionLockExecutor;
import utilities.entitylocker.executor.ILockExecutor;


/**
 * EntityLocker is an entity locker utility that provides synchronization mechanism based on entity keys (e.g. name based locks).
 * This is supposed to be used by the components that are responsible for managing storage and caching of different type of entities in the application.
 *
 * The utility itself does not deal with the entities, only with the keys (primary keys) of the entities.
 * It guarantees that at most one thread executes protected code on that entity based on given key.
 *
 * If there’s a concurrent request to lock the same entity key, the other thread should wait until the entity key becomes available.
 * EntityLocker allows concurrent execution of protected code on different entity keys.
 *
 * EntityLocker supports the deadlock prevention and allows to specify the waiting time for lock
 *
 * @param <T> The type of used entity keys
 * 
 * @author Kholodyakov Sergey
 */

public class EntityLocker<T> {

	private final static boolean DEFAULT_DEADLOCK_PREVENTION = false;
	
	private final Map<Object, PacketReentrantLock> packetLocks = new HashMap<>();
	
	private final ReentrantLock mapLock = new ReentrantLock();
	
	private final ILockExecutor lockExecutor;

	public EntityLocker() {
		this(DEFAULT_DEADLOCK_PREVENTION);
	}

	/**
	 * Constructor for using EntityLocker with default {@link DefaultLockExecutor} or {@link DeadlockPreventionLockExecutor}
	 * @param deadlockPrevention - use executor with deadlock prevention (checking possible locks inside EntityLocker)
	 */
	public EntityLocker(boolean deadlockPrevention) {
		lockExecutor = deadlockPrevention ? new DeadlockPreventionLockExecutor() : new DefaultLockExecutor();
	}

	public final <R> R lockAndExecute(final T key, final Callable<R> task) {
		if (task == null) {
			throw new EntityLockerException("Task is null");
		}
		return lockExecutor.execute(getOrCreatePacketLock(key), task);
	}

	public final void lockAndExecute(final T key, final Runnable task) {
		if (task == null) {
			throw new EntityLockerException("Task is null");
		}
		lockExecutor.execute(getOrCreatePacketLock(key), task);
	}

	public final <R> R tryLockAndExecute(final T key, final Callable<R> task, long lockTimeout, TimeUnit lockTimeoutUnit) {
		if (task == null) {
			throw new EntityLockerException("Task is null");
		}
		if (lockTimeoutUnit == null) {
			throw new EntityLockerException("TimeUnit is null");
		}
		return lockExecutor.tryExecute(getOrCreatePacketLock(key), task, lockTimeout, lockTimeoutUnit);
	}

	public final void tryLockAndExecute(final T key, final Runnable task, long lockTimeout, TimeUnit lockTimeoutUnit) {
		if (task == null) {
			throw new EntityLockerException("Task is null");
		}
		lockExecutor.tryExecute(getOrCreatePacketLock(key), task, lockTimeout, lockTimeoutUnit);
	}

	private PacketReentrantLock getOrCreatePacketLock(final T key) {
		PacketReentrantLock lock = packetLocks.get(key);
		if (lock != null) {
			return lock;
		}
		mapLock.lock();
		try {
			lock = packetLocks.get(key);
			if (lock == null) {
				lock = new PacketReentrantLock(key);
				packetLocks.put(key, lock);
			}
			lock.owners.incrementAndGet();
		} finally {
			mapLock.unlock();
		}

		return lock;
	}

	public class PacketReentrantLock extends ReentrantLock {
		final AtomicLong owners = new AtomicLong(0);
		private final T key;

		PacketReentrantLock(T key) {
			this.key = key;
		}

		@Override
		public void unlock() {
			super.unlock();
			if (owners.decrementAndGet() == 0) {
				mapLock.lock();
				try {
					if (owners.decrementAndGet() == 0) {
						packetLocks.remove(key);
					}
				} finally {
					mapLock.unlock();
				}
			}
		}

		@Override
		public Thread getOwner(){
			return super.getOwner();
		}

		public Object getKey() {
			return key;
		}
	}
}