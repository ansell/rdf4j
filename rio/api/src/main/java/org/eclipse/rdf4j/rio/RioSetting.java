/*******************************************************************************
 * Copyright (c) 2015 Eclipse RDF4J contributors, Aduna, and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *******************************************************************************/
package org.eclipse.rdf4j.rio;

import java.io.Serializable;

/**
 * Identifies a parser setting along with its default value.
 * 
 * @author Peter Ansell
 */
public interface RioSetting<T extends Object> extends Serializable {

	/**
	 * A unique key for this parser setting.
	 * 
	 * @return A unique key identifying this parser setting.
	 */
	String getKey();

	/**
	 * The human readable name for this parser setting
	 * 
	 * @return The name for this parser setting.
	 */
	String getDescription();

	/**
	 * Returns the default value for this parser setting if it is not set by a user.
	 * 
	 * @return The default value for this parser setting.
	 */
	T getDefaultValue();
	
	/**
	 * Attempts to convert from a string to a type-safe representation based on the generic type of this setting.
	 * 
	 * @param stringRepresentation The string representation of this setting to convert
	 * @return A type-safe converted representation of this setting
	 * @throws RioConfigurationException if conversion from a string is not supported for this setting or the stringRepresentation was not valid
	 */
	default T convert(String stringRepresentation) {
		throw new RioConfigurationException("Conversion not implemented for setting: " + getKey());
	}
}
