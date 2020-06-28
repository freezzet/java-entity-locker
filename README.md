## Synopsis
Entity locker utility provides synchronization mechanism based on entity keys (e.g. name based locks).
This is supposed to be used by the components that are responsible for managing storage and caching of different type of entities in the application.

The utility itself does not deal with the entities and owners (threads), only with the keys (primary keys) of the entities.
It guarantees that at most one thread executes protected code on that entity based on given key.

If there’s a concurrent request to lock the same entity key, the other thread should wait until the entity key becomes available.
The utility allows concurrent execution of protected code on different entity keys.

The utility supports the deadlock prevention and allows to specify the waiting time for lock

## Code Example
	
	boolean deadlockPrevention = fasle; 
	
	/* Disable or enable the deadlock prevention
	 * When enabled the utility will perofrm a deadlock check based on all registered keys inside EntityLocker.
	*/ This only happens when an attempt is made to lock a key that is already in use.
	
	EntityLocker<Object> locker = new EntityLocker<>(deadlockPrevention); // Create the Entity Locker which only allows keys of type Object 

	// lock key execute protected code
	locker.lockAndExecute(null, () -> {//This code will be executed synchronized based on null key};
		}
	});

	Object key1 = new Object();

	// lock key and execute protected code
	locker.lockAndExecute(key1, () -> {// This code will be executed synchronized based on key1 object};
		}
	});

	// try to lock key in the specified time and execute protected code
	locker.tryLockAndExecute(key1, () -> {// This code will be executed synchronized based on key1 object};
		}
	}, 1000, TimeUnit.MILLISECONDS);


	Callable<String> task = () -> { // This code will be executed synchronized based on key1 object} ;
	// lock the key, execute protected code and return result
	String result = locker.lockAndExecute(key1, task);

## Installation
mvn clean package