package utilities.entitylocker.exception;

import java.util.concurrent.TimeUnit;

public class TimeoutLockException extends RuntimeException {

	/**
	 * Timeout while waiting for a lock.
	 */
	private static final long serialVersionUID = -3417050483263928274L;

	private final long timeout;

	private final TimeUnit timeUnit;

	public TimeoutLockException(final long timeout, final TimeUnit timeUnit) {
		super("Lock timeout after: " + timeUnit.toSeconds(timeout) + " seconds");
		this.timeout = timeout;
		this.timeUnit = timeUnit;
	}

	public long getTimeout() {
		return timeout;
	}

	public TimeUnit getTimeUnit() {
		return timeUnit;
	}
}
