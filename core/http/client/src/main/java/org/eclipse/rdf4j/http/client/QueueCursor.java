/*******************************************************************************
 * Copyright (c) 2015 Eclipse RDF4J contributors, Aduna, and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *******************************************************************************/
package org.eclipse.rdf4j.http.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.eclipse.rdf4j.RDF4JException;
import org.eclipse.rdf4j.common.iteration.LookAheadIteration;
import org.eclipse.rdf4j.query.QueryEvaluationException;

/**
 * Makes working with a queue easier by adding the methods {@link #done()} and {@link #toss(Exception)} and
 * automatically converting the exception into a QueryEvaluationException with an appropriate stack trace.
 * 
 * @author James Leigh
 */
public class QueueCursor<E> extends LookAheadIteration<E, QueryEvaluationException> {

	/**
	 * An indicator of whether this queue is open to having more items added to it.<br/>
	 * Set to true to lazily indicate that no more values should be added to this queue.<br/>
	 * Note that this value is never sent to false after being set to true so once this queue is closed it
	 * cannot be reopened.
	 */
	private volatile boolean done = false;

	/**
	 * The queue of items to process.
	 */
	private final BlockingQueue<E> queue;

	/**
	 * Sentinel/poison pill value used internally to identify the end of the queue.
	 */
	private final E afterLast = createAfterLast();

	private volatile Queue<Throwable> exceptions = new LinkedList<Throwable>();

	/**
	 * Creates an <tt>QueueCursor</tt> with the given (fixed) capacity and default access policy.
	 * 
	 * @param capacity
	 *        the capacity of this queue
	 */
	public QueueCursor(int capacity) {
		this(capacity, false);
	}

	/**
	 * Creates an <tt>QueueCursor</tt> with the given (fixed) capacity and the specified access policy.
	 * 
	 * @param capacity
	 *        the capacity of this queue
	 * @param fair
	 *        if <tt>true</tt> then queue accesses for threads blocked on insertion or removal, are processed
	 *        in FIFO order; if <tt>false</tt> the access order is unspecified.
	 */
	public QueueCursor(int capacity, boolean fair) {
		this.queue = new ArrayBlockingQueue<E>(capacity, fair);
	}

	/**
	 * The next time {@link #next()} is called this exception will be thrown. If it is not a
	 * QueryEvaluationException or RuntimeException it will be wrapped in a QueryEvaluationException.
	 */
	public void toss(Exception exception) {
		synchronized (exceptions) {
			exceptions.add(exception);
		}
	}

	/**
	 * Adds another item to the queue, blocking while the queue is full.
	 */
	public void put(E item)
		throws InterruptedException
	{
		if (!done) {
			queue.put(item);
		}
	}

	/**
	 * Indicates the method {@link #put(Object)} should not be called in the queue anymore and any calls to it
	 * will be noop's.
	 */
	public void done() {
		done = true;
		// Attempt to add the sentinel to the end of the queue, 
		// but do not fail if the queue is full or unavailable
		// Both the null and afterLast objects are defined as sentinels
		// In addition, the close method actively clears the queue so
		// we do not need to be as concerned about that here, 
		// as long as users call close properly
		queue.offer(afterLast);
	}

	/**
	 * Returns the next item in the queue or throws an exception.
	 */
	@Override
	public E getNextElement()
		throws QueryEvaluationException
	{
		try {
			checkException();
			E take;
			if (done) {
				take = queue.poll();
			}
			else {
				take = queue.take();
				if (done) {
					done(); 
				}
			}
			if (isAfterLast(take)) {
				checkException();
				// put afterLast back for others
				done(); 
				return null;
			}
			return take;
		}
		catch (InterruptedException e) {
			checkException();
			throw new QueryEvaluationException(e);
		}
	}

	@Override
	public void handleClose()
		throws QueryEvaluationException
	{
		done = true;
		do {
			// remove all unprocessed items and then add a sentinel
			// Note that we do not 
			queue.clear(); 
		}
		while (!queue.offer(afterLast));
		checkException();
	}

	public void checkException()
		throws QueryEvaluationException
	{
		synchronized (exceptions) {
			if (!exceptions.isEmpty()) {
				try {
					throw exceptions.remove();
				}
				catch (RDF4JException e) {
					if (e instanceof QueryEvaluationException) {
						List<StackTraceElement> stack = new ArrayList<StackTraceElement>();
						stack.addAll(Arrays.asList(e.getStackTrace()));
						StackTraceElement[] thisStack = new Throwable().getStackTrace();
						stack.addAll(Arrays.asList(thisStack).subList(1, thisStack.length));
						e.setStackTrace(stack.toArray(new StackTraceElement[stack.size()]));
						throw e;
					}
					else {
						throw new QueryEvaluationException(e);
					}
				}
				catch (RuntimeException e) {
					// any RuntimeException that is not an OpenRDFException should be
					// reported as-is
					List<StackTraceElement> stack = new ArrayList<StackTraceElement>();
					stack.addAll(Arrays.asList(e.getStackTrace()));
					StackTraceElement[] thisStack = new Throwable().getStackTrace();
					stack.addAll(Arrays.asList(thisStack));
					e.setStackTrace(stack.toArray(new StackTraceElement[stack.size()]));
					throw e;
				}
				catch (Throwable e) {
					throw new QueryEvaluationException(e);
				}
			}
		}
	}

	/**
	 * Identifies whether the given value is a sentinel identifying that the queue is complete.
	 * 
	 * @param take
	 *        The object to check for equality with the sentinel.
	 * @return True if the given object is null or is the same object reference as the sentinel.
	 */
	private boolean isAfterLast(E take) {
		return take == null || take == afterLast;
	}

	@SuppressWarnings("unchecked")
	private E createAfterLast() {
		return (E)new Object();
	}

}
