package utilities.entitylocker.exception;

public class DeadlockException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 8369129442800851266L;

	public DeadlockException(String string) {
		super(string);
	}

}
