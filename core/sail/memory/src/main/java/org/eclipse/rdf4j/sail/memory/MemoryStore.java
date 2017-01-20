/*******************************************************************************
 * Copyright (c) 2015 Eclipse RDF4J contributors, Aduna, and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *******************************************************************************/
package org.eclipse.rdf4j.sail.memory;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import org.eclipse.rdf4j.IsolationLevels;
import org.eclipse.rdf4j.common.concurrent.locks.Lock;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.query.algebra.evaluation.EvaluationStrategyFactory;
import org.eclipse.rdf4j.query.algebra.evaluation.federation.FederatedServiceResolver;
import org.eclipse.rdf4j.query.algebra.evaluation.federation.FederatedServiceResolverClient;
import org.eclipse.rdf4j.query.algebra.evaluation.federation.FederatedServiceResolverImpl;
import org.eclipse.rdf4j.query.algebra.evaluation.impl.StrictEvaluationStrategyFactory;
import org.eclipse.rdf4j.sail.NotifyingSailConnection;
import org.eclipse.rdf4j.sail.SailChangedEvent;
import org.eclipse.rdf4j.sail.SailException;
import org.eclipse.rdf4j.sail.base.SailDataset;
import org.eclipse.rdf4j.sail.base.SailSink;
import org.eclipse.rdf4j.sail.base.SailStore;
import org.eclipse.rdf4j.sail.helpers.AbstractNotifyingSail;
import org.eclipse.rdf4j.sail.helpers.DirectoryLockManager;

/**
 * An implementation of the Sail interface that stores its data in main memory and that can use a file for
 * persistent storage. This Sail implementation supports single, isolated transactions. This means that
 * changes to the data are not visible until a transaction is committed and that concurrent transactions are
 * not possible. When another transaction is active, calls to <tt>startTransaction()</tt> will block until the
 * active transaction is committed or rolled back.
 * 
 * @author Arjohn Kampman
 * @author jeen
 */
public class MemoryStore extends AbstractNotifyingSail implements FederatedServiceResolverClient {

	/*-----------*
	 * Constants *
	 *-----------*/

	protected static final String DATA_FILE_NAME = "memorystore.data";

	protected static final String SYNC_FILE_NAME = "memorystore.sync";

	/*-----------*
	 * Variables *
	 *-----------*/

	/**
	 * Factory/cache for MemValue objects.
	 */
	private volatile SailStore store;

	private volatile boolean persist = false;

	/**
	 * The file used for data persistence, null if this is a volatile RDF store.
	 */
	private volatile File dataFile;

	/**
	 * The file used for serialising data, null if this is a volatile RDF store.
	 */
	private volatile File syncFile;

	/**
	 * The directory lock, null if this is read-only or a volatile RDF store.
	 */
	private volatile Lock dirLock;

	/**
	 * Flag indicating whether the contents of this repository have changed.
	 */
	private volatile boolean contentsChanged;

	/**
	 * The sync delay.
	 * 
	 * @see #setSyncDelay
	 */
	private volatile long syncDelay = 0L;

	/**
	 * Semaphore used to synchronize concurrent access to {@link #syncWithLock()} .
	 */
	private final Object syncSemaphore = new Object();

	/**
	 * The timer used to trigger file synchronization.
	 */
	private volatile Timer syncTimer;

	/**
	 * The currently scheduled timer task, if any.
	 */
	private volatile TimerTask syncTimerTask;

	/**
	 * Semaphore used to synchronize concurrent access to {@link #syncTimer} and {@link #syncTimerTask}.
	 */
	private final Object syncTimerSemaphore = new Object();

	private EvaluationStrategyFactory evalStratFactory;

	/** independent life cycle */
	private FederatedServiceResolver serviceResolver;

	/** dependent life cycle */
	private FederatedServiceResolverImpl dependentServiceResolver;

	/*--------------*
	 * Constructors *
	 *--------------*/

	/**
	 * Creates a new MemoryStore.
	 */
	public MemoryStore() {
		setSupportedIsolationLevels(IsolationLevels.NONE, IsolationLevels.READ_COMMITTED,
				IsolationLevels.SNAPSHOT_READ, IsolationLevels.SNAPSHOT, IsolationLevels.SERIALIZABLE);
		setDefaultIsolationLevel(IsolationLevels.SNAPSHOT_READ);
	}

	/**
	 * Creates a new persistent MemoryStore. If the specified data directory contains an existing store, its
	 * contents will be restored upon initialization.
	 * 
	 * @param dataDir
	 *        the data directory to be used for persistence.
	 */
	public MemoryStore(File dataDir) {
		this();
		setDataDir(dataDir);
		setPersist(true);
	}

	/*---------*
	 * Methods *
	 *---------*/

	public void setPersist(boolean persist) {
		if (isInitialized()) {
			throw new IllegalStateException("sail has already been initialized");
		}

		this.persist = persist;
	}

	public boolean getPersist() {
		return persist;
	}

	/**
	 * Sets the time (in milliseconds) to wait after a transaction was commited before writing the changed
	 * data to file. Setting this variable to 0 will force a file sync immediately after each commit. A
	 * negative value will deactivate file synchronization until the Sail is shut down. A positive value will
	 * postpone the synchronization for at least that amount of milliseconds. If in the meantime a new
	 * transaction is started, the file synchronization will be rescheduled to wait for another
	 * <tt>syncDelay</tt> ms. This way, bursts of transaction events can be combined in one file sync.
	 * <p>
	 * The default value for this parameter is <tt>0</tt> (immediate synchronization).
	 * 
	 * @param syncDelay
	 *        The sync delay in milliseconds.
	 */
	public void setSyncDelay(long syncDelay) {
		if (isInitialized()) {
			throw new IllegalStateException("sail has already been initialized");
		}

		this.syncDelay = syncDelay;
	}

	/**
	 * Gets the currently configured sync delay.
	 * 
	 * @return syncDelay The sync delay in milliseconds.
	 * @see #setSyncDelay
	 */
	public long getSyncDelay() {
		return syncDelay;
	}

	/**
	 * @return Returns the {@link EvaluationStrategy}.
	 */
	public EvaluationStrategyFactory getEvaluationStrategyFactory() {
		EvaluationStrategyFactory result = evalStratFactory;
		if (result == null) {
			synchronized (this) {
				result = evalStratFactory;
				if (result == null) {
					result = evalStratFactory = new StrictEvaluationStrategyFactory(
							getFederatedServiceResolver());
				}
			}
		}
		result.setQuerySolutionCacheThreshold(getIterationCacheSyncThreshold());
		return result;
	}

	/**
	 * Sets the {@link EvaluationStrategy} to use.
	 */
	public void setEvaluationStrategyFactory(EvaluationStrategyFactory factory) {
		evalStratFactory = Objects.requireNonNull(factory, "EvaluationStrategyFactory cannot be null");
	}

	/**
	 * @return Returns the SERVICE resolver.
	 */
	public FederatedServiceResolver getFederatedServiceResolver() {
		FederatedServiceResolver result = serviceResolver;
		if (result == null) {
			synchronized (this) {
				result = serviceResolver;
				if (result == null) {
					if (dependentServiceResolver == null) {
						dependentServiceResolver = new FederatedServiceResolverImpl();
					}
					result = serviceResolver = dependentServiceResolver;
				}
			}
		}
		return result;
	}

	/**
	 * Overrides the {@link FederatedServiceResolver} used by this instance, but the given resolver is not
	 * shutDown when this instance is.
	 * 
	 * @param resolver
	 *        The SERVICE resolver to set.
	 */
	public void setFederatedServiceResolver(FederatedServiceResolver resolver) {
		this.serviceResolver = resolver;
		// Shutdown any internal service resolver that was created before this point
		FederatedServiceResolverImpl toShutdownDependentServiceResolver = dependentServiceResolver;
		if (toShutdownDependentServiceResolver != null) {
			toShutdownDependentServiceResolver.shutDown();
		}
	}

	/**
	 * Initializes this repository. If a persistence file is defined for the store, the contents will be
	 * restored.
	 * 
	 * @throws SailException
	 *         when initialization of the store failed.
	 */
	protected void initializeInternal()
		throws SailException
	{
		logger.debug("Initializing MemoryStore...");

		SailStore toInitialiseStore = store = new MemorySailStore(debugEnabled());

		if (persist) {
			File dataDir = getDataDir();
			DirectoryLockManager locker = new DirectoryLockManager(dataDir);
			dataFile = new File(dataDir, DATA_FILE_NAME);
			syncFile = new File(dataDir, SYNC_FILE_NAME);

			if (dataFile.exists()) {
				logger.debug("Reading data from {}...", dataFile);

				// Initialize persistent store from file
				if (!dataFile.canRead()) {
					logger.error("Data file is not readable: {}", dataFile);
					throw new SailException("Can't read data file: " + dataFile);
				}
				// try to create a lock for later writing
				dirLock = locker.tryLock();
				if (dirLock == null) {
					logger.warn("Failed to lock directory: {}", dataDir);
				}
				// Don't try to read empty files: this will result in an
				// IOException, and the file doesn't contain any data anyway.
				if (dataFile.length() == 0L) {
					logger.warn("Ignoring empty data file: {}", dataFile);
				}
				else {
					SailSink explicit = toInitialiseStore.getExplicitSailSource().sink(IsolationLevels.NONE);
					SailSink inferred = toInitialiseStore.getInferredSailSource().sink(IsolationLevels.NONE);
					try {
						new FileIO(toInitialiseStore.getValueFactory()).read(dataFile, explicit, inferred);
						logger.debug("Data file read successfully");
					}
					catch (IOException e) {
						logger.error("Failed to read data file", e);
						throw new SailException(e);
					}
					finally {
						try {
							try {
								explicit.prepare();
							}
							finally {
								try {
									explicit.flush();
								}
								finally {
									explicit.close();
								}
							}
						}
						finally {
							try {
								inferred.prepare();
							}
							finally {
								try {
									inferred.flush();
								}
								finally {
									inferred.close();
								}
							}
						}
					}
				}
			}
			else {
				// file specified that does not exist yet, create it
				try {
					File dir = dataFile.getParentFile();
					if (dir != null && !dir.exists()) {
						logger.debug("Creating directory for data file...");
						if (!dir.mkdirs()) {
							logger.debug("Failed to create directory for data file: {}", dir);
							throw new SailException("Failed to create directory for data file: " + dir);
						}
					}
					// try to lock directory or fail
					dirLock = locker.lockOrFail();

					logger.debug("Initializing data file...");
					SailDataset explicit = toInitialiseStore.getExplicitSailSource().dataset(
							IsolationLevels.SNAPSHOT);
					SailDataset inferred = toInitialiseStore.getInferredSailSource().dataset(
							IsolationLevels.SNAPSHOT);
					try {
						new FileIO(toInitialiseStore.getValueFactory()).write(explicit, inferred, syncFile,
								dataFile);
					}
					finally {
						try {
							explicit.close();
						}
						finally {
							inferred.close();
						}
					}
					logger.debug("Data file initialized");
				}
				catch (IOException e) {
					logger.debug("Failed to initialize data file", e);
					throw new SailException("Failed to initialize data file " + dataFile, e);
				}
				catch (SailException e) {
					logger.debug("Failed to initialize data file", e);
					throw new SailException("Failed to initialize data file " + dataFile, e);
				}
			}
		}

		contentsChanged = false;

		logger.debug("MemoryStore initialized");
	}

	@Override
	protected void shutDownInternal()
		throws SailException
	{
		try {
			try {
				cancelSyncTimer();
			}
			finally {
				try {
					sync();
				}
				finally {
					SailStore toCloseStore = store;
					if (toCloseStore != null) {
						toCloseStore.close();
					}
					dataFile = null;
					syncFile = null;
				}
			}
		}
		finally {
			try {
				Lock toReleaseDirLock = dirLock;
				if (toReleaseDirLock != null) {
					toReleaseDirLock.release();
				}
			}
			finally {
				FederatedServiceResolverImpl toCloseDependentServiceResolver = dependentServiceResolver;
				if (toCloseDependentServiceResolver != null) {
					toCloseDependentServiceResolver.shutDown();
				}
			}
		}
	}

	/**
	 * Checks whether this Sail object is writable. A MemoryStore is not writable if a read-only data file is
	 * used.
	 */
	public boolean isWritable() {
		// Sail is not writable when it has a dataDir but no directory lock
		return !persist || dirLock != null;
	}

	@Override
	protected NotifyingSailConnection getConnectionInternal()
		throws SailException
	{
		return new MemoryStoreConnection(this);
	}

	public ValueFactory getValueFactory() {
		if (store == null) {
			throw new IllegalStateException("sail not initialized.");
		}

		return store.getValueFactory();
	}

	@Override
	public void notifySailChanged(SailChangedEvent event) {
		super.notifySailChanged(event);
		synchronized (syncSemaphore) {
			contentsChanged = true;
		}
	}

	protected void scheduleSyncTask()
		throws SailException
	{
		if (!persist) {
			return;
		}

		if (syncDelay == 0L) {
			// Sync immediately
			sync();
		}
		else if (syncDelay > 0L) {
			synchronized (syncTimerSemaphore) {
				// Sync in syncDelay milliseconds
				if (syncTimer == null) {
					// Create the syncTimer on a deamon thread
					syncTimer = new Timer("MemoryStore synchronization", true);
				}

				if (syncTimerTask != null) {
					// sync task from (concurrent) other transaction exists. 
					// cancel and replace with newly scheduled sync task.
					syncTimerTask.cancel();
				}

				syncTimerTask = new TimerTask() {

					@Override
					public void run() {
						try {
							sync();
						}
						catch (SailException e) {
							logger.warn("Unable to sync on timer", e);
						}
					}
				};

				syncTimer.schedule(syncTimerTask, syncDelay);
			}
		}
	}

	protected void cancelSyncTask() {
		synchronized (syncTimerSemaphore) {
			TimerTask toCancelSyncTask = syncTimerTask;
			syncTimerTask = null;
			if (toCancelSyncTask != null) {
				toCancelSyncTask.cancel();
			}
		}
	}

	protected void cancelSyncTimer() {
		synchronized (syncTimerSemaphore) {
			Timer toCancelSyncTimer = syncTimer;
			syncTimer = null;
			if (toCancelSyncTimer != null) {
				toCancelSyncTimer.cancel();
			}
		}
	}

	/**
	 * Synchronizes the contents of this repository with the data that is stored on disk. Data will only be
	 * written when the contents of the repository and data in the file are out of sync.
	 */
	public void sync()
		throws SailException
	{
		// syncSemaphore prevents concurrent file synchronizations
		synchronized (syncSemaphore) {
			if (persist && contentsChanged) {
				logger.debug("syncing data to file...");
				try {
					IsolationLevels level = IsolationLevels.SNAPSHOT;
					SailStore toSyncStore = store;
					SailDataset explicit = toSyncStore.getExplicitSailSource().dataset(level);
					SailDataset inferred = toSyncStore.getInferredSailSource().dataset(level);
					try {
						new FileIO(toSyncStore.getValueFactory()).write(explicit, inferred, syncFile,
								dataFile);
					}
					finally {
						try {
							explicit.close();
						}
						finally {
							inferred.close();
						}
					}
					contentsChanged = false;
					logger.debug("Data synced to file");
				}
				catch (IOException e) {
					logger.error("Failed to sync to file", e);
					throw new SailException(e);
				}
			}
		}
	}

	SailStore getSailStore() {
		return store;
	}
}
