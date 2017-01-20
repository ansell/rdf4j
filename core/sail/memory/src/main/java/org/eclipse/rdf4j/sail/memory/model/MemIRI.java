/*******************************************************************************
 * Copyright (c) 2015 Eclipse RDF4J contributors, Aduna, and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *******************************************************************************/
package org.eclipse.rdf4j.sail.memory.model;

import org.eclipse.rdf4j.model.IRI;

/**
 * A MemoryStore-specific implementation of URI that stores separated namespace and local name information to
 * enable reuse of namespace String objects (reducing memory usage) and that gives it node properties.
 */
public class MemIRI implements IRI, MemResource {

	private static final long serialVersionUID = 9118488004995852467L;

	/*------------*
	 * Attributes *
	 *------------*/

	/**
	 * The URI's namespace.
	 */
	private final String namespace;

	/**
	 * The URI's local name.
	 */
	private final String localName;

	/**
	 * The object that created this MemURI.
	 */
	transient private final Object creator;

	/**
	 * The MemURI's hash code, 0 if not yet initialized.
	 */
	private volatile int hashCode = 0;

	/**
	 * The list of statements for which this MemURI is the subject.
	 */
	transient private volatile MemStatementList subjectStatements = null;

	/**
	 * The list of statements for which this MemURI is the predicate.
	 */
	transient private volatile MemStatementList predicateStatements = null;

	/**
	 * The list of statements for which this MemURI is the object.
	 */
	transient private volatile MemStatementList objectStatements = null;

	/**
	 * The list of statements for which this MemURI represents the context.
	 */
	transient private volatile MemStatementList contextStatements = null;

	/*--------------*
	 * Constructors *
	 *--------------*/

	/**
	 * Creates a new MemURI for a URI.
	 * 
	 * @param creator
	 *        The object that is creating this MemURI.
	 * @param namespace
	 *        namespace part of URI.
	 * @param localName
	 *        localname part of URI.
	 */
	public MemIRI(Object creator, String namespace, String localName) {
		this.creator = creator;
		this.namespace = namespace.intern();
		this.localName = localName;
	}

	/*---------*
	 * Methods *
	 *---------*/

	@Override
	public String toString() {
		return namespace + localName;
	}

	public String stringValue() {
		return toString();
	}

	public String getNamespace() {
		return namespace;
	}

	public String getLocalName() {
		return localName;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}

		if (other instanceof MemIRI) {
			MemIRI o = (MemIRI)other;
			return namespace.equals(o.getNamespace()) && localName.equals(o.getLocalName());
		}
		else if (other instanceof IRI) {
			String otherStr = other.toString();

			return namespace.length() + localName.length() == otherStr.length()
					&& otherStr.endsWith(localName) && otherStr.startsWith(namespace);
		}

		return false;
	}

	@Override
	public int hashCode() {
		int result = hashCode;
		if (result == 0) {
			// no need to synchronize as the result will be the same
			result = hashCode = toString().hashCode();
		}

		return result;
	}

	public Object getCreator() {
		return creator;
	}

	public boolean hasStatements() {
		MemStatementList toCheckSubjectStatements = subjectStatements;
		MemStatementList toCheckPredicateStatements = predicateStatements;
		MemStatementList toCheckObjectStatements = objectStatements;
		MemStatementList toCheckContextStatements = contextStatements;
		if (toCheckSubjectStatements != null && !toCheckSubjectStatements.isEmpty()) {
			return true;
		}
		else if (toCheckPredicateStatements != null && !toCheckPredicateStatements.isEmpty()) {
			return true;
		}
		else if (toCheckObjectStatements != null && !toCheckObjectStatements.isEmpty()) {
			return true;
		}
		else if (toCheckContextStatements != null && !toCheckContextStatements.isEmpty()) {
			return true;
		}
		return false;
	}

	public MemStatementList getSubjectStatementList() {
		MemStatementList toCheckSubjectStatements = subjectStatements;
		if (toCheckSubjectStatements == null) {
			return EMPTY_LIST;
		}
		else {
			return toCheckSubjectStatements;
		}
	}

	public int getSubjectStatementCount() {
		MemStatementList toCheckSubjectStatements = subjectStatements;
		if (toCheckSubjectStatements == null) {
			return 0;
		}
		else {
			return toCheckSubjectStatements.size();
		}
	}

	public void addSubjectStatement(MemStatement st) {
		MemStatementList toCheckSubjectStatements = subjectStatements;
		if (toCheckSubjectStatements == null) {
			synchronized(this) {
				toCheckSubjectStatements = subjectStatements;
				if(toCheckSubjectStatements == null) {
					toCheckSubjectStatements = subjectStatements = new MemStatementList(4);
				}
			}
		}

		toCheckSubjectStatements.add(st);
	}

	public void removeSubjectStatement(MemStatement st) {
		MemStatementList toCheckSubjectStatements = subjectStatements;
		if(toCheckSubjectStatements != null) {
			toCheckSubjectStatements.remove(st);
		}
	}

	public void cleanSnapshotsFromSubjectStatements(int currentSnapshot) {
		MemStatementList toCheckSubjectStatements = subjectStatements;
		if (toCheckSubjectStatements != null) {
			toCheckSubjectStatements.cleanSnapshots(currentSnapshot);
		}
	}

	/**
	 * Gets the list of statements for which this MemURI is the predicate.
	 * 
	 * @return a MemStatementList containing the statements.
	 */
	public MemStatementList getPredicateStatementList() {
		MemStatementList toReturnPredicateStatements = predicateStatements;
		if (toReturnPredicateStatements == null) {
			return EMPTY_LIST;
		}
		else {
			return toReturnPredicateStatements;
		}
	}

	/**
	 * Gets the number of Statements for which this MemURI is the predicate.
	 * 
	 * @return An integer larger than or equal to 0.
	 */
	public int getPredicateStatementCount() {
		MemStatementList toReturnPredicateStatements = predicateStatements;
		if (toReturnPredicateStatements == null) {
			return 0;
		}
		else {
			return toReturnPredicateStatements.size();
		}
	}

	/**
	 * Adds a statement to this MemURI's list of statements for which it is the predicate.
	 */
	public void addPredicateStatement(MemStatement st) {
		MemStatementList toAddPredicateStatements = predicateStatements;
		if (toAddPredicateStatements == null) {
			synchronized(this) {
				toAddPredicateStatements = predicateStatements;
				if (toAddPredicateStatements == null) {
					toAddPredicateStatements = predicateStatements = new MemStatementList(4);
				}
			}
		}

		toAddPredicateStatements.add(st);
	}

	/**
	 * Removes a statement from this MemURI's list of statements for which it is the predicate.
	 */
	public void removePredicateStatement(MemStatement st) {
		MemStatementList toCheckPredicateStatements = predicateStatements;
		if(toCheckPredicateStatements != null) {
			toCheckPredicateStatements.remove(st);
		}
	}

	/**
	 * Removes statements from old snapshots (those that have expired at or before the specified snapshot
	 * version) from this MemValue's list of statements for which it is the predicate.
	 * 
	 * @param currentSnapshot
	 *        The current snapshot version.
	 */
	public void cleanSnapshotsFromPredicateStatements(int currentSnapshot) {
		MemStatementList toCheckPredicateStatements = predicateStatements;
		if (toCheckPredicateStatements != null) {
			toCheckPredicateStatements.cleanSnapshots(currentSnapshot);
		}
	}

	public MemStatementList getObjectStatementList() {
		MemStatementList toCheckObjectStatements = objectStatements;
		if (toCheckObjectStatements == null) {
			return EMPTY_LIST;
		}
		else {
			return toCheckObjectStatements;
		}
	}

	public int getObjectStatementCount() {
		MemStatementList toCheckObjectStatements = objectStatements;
		if (toCheckObjectStatements == null) {
			return 0;
		}
		else {
			return toCheckObjectStatements.size();
		}
	}

	public void addObjectStatement(MemStatement st) {
		MemStatementList toCheckObjectStatements = objectStatements;
		if (toCheckObjectStatements == null) {
			synchronized(this) {
				toCheckObjectStatements = objectStatements;
				if(toCheckObjectStatements == null) {
					toCheckObjectStatements = objectStatements = new MemStatementList(4);
				}
			}
		}
		toCheckObjectStatements.add(st);
	}

	public void removeObjectStatement(MemStatement st) {
		MemStatementList toCheckObjectStatements = objectStatements;
		if(toCheckObjectStatements != null) {
			toCheckObjectStatements.remove(st);
		}
	}

	public void cleanSnapshotsFromObjectStatements(int currentSnapshot) {
		MemStatementList toCheckObjectStatements = objectStatements;
		if (toCheckObjectStatements != null) {
			toCheckObjectStatements.cleanSnapshots(currentSnapshot);
		}
	}

	public MemStatementList getContextStatementList() {
		MemStatementList toCheckContextStatements = contextStatements;
		if (toCheckContextStatements == null) {
			return EMPTY_LIST;
		}
		else {
			return toCheckContextStatements;
		}
	}

	public int getContextStatementCount() {
		MemStatementList toCheckContextStatements = contextStatements;
		if (toCheckContextStatements == null) {
			return 0;
		}
		else {
			return toCheckContextStatements.size();
		}
	}

	public void addContextStatement(MemStatement st) {
		MemStatementList toCheckContextStatements = contextStatements;
		if (toCheckContextStatements == null) {
			synchronized(this) {
				toCheckContextStatements = contextStatements;
				if(toCheckContextStatements == null) {
					toCheckContextStatements = contextStatements = new MemStatementList(4);
				}
			}
		}

		toCheckContextStatements.add(st);
	}

	public void removeContextStatement(MemStatement st) {
		MemStatementList toCheckContextStatements = contextStatements;
		if(toCheckContextStatements != null) {
			toCheckContextStatements.remove(st);
		}
	}

	public void cleanSnapshotsFromContextStatements(int currentSnapshot) {
		MemStatementList toCheckContextStatements = contextStatements;
		if (toCheckContextStatements != null) {
			toCheckContextStatements.cleanSnapshots(currentSnapshot);
		}
	}
}
