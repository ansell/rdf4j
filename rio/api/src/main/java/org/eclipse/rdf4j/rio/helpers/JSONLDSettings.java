/*******************************************************************************
 * Copyright (c) 2015 Eclipse RDF4J contributors, Aduna, and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *******************************************************************************/
package org.eclipse.rdf4j.rio.helpers;

import java.util.Collections;
import java.util.Map;

import org.eclipse.rdf4j.rio.RioSetting;

/**
 * Settings that can be passed to JSONLD Parsers and Writers.
 * 
 * @author Peter Ansell
 * @see <a href="http://json-ld.org/spec/latest/json-ld-api/#data-structures">JSONLD Data Structures</a>
 */
public class JSONLDSettings {

	/**
	 * If set to true, the JSON-LD processor replaces arrays with just one element with that element during
	 * compaction. If set to false, all arrays will remain arrays even if they have just one element.
	 * <p>
	 * Defaults to true.
	 * 
	 * @see <a href="http://json-ld.org/spec/latest/json-ld-api/#data-structures">JSONLD Data Structures</a>
	 */
	public static final RioSetting<Boolean> COMPACT_ARRAYS = new RioSettingImpl<Boolean>(
			"org.eclipse.rdf4j.rio.jsonld.compactarrays", "Compact arrays", Boolean.TRUE);

	/**
	 * If set to true, the JSON-LD processor is allowed to optimize the output of the
	 * <a href= "http://json-ld.org/spec/latest/json-ld-api/#compaction-algorithm" >Compaction algorithm</a>
	 * to produce even compacter representations.
	 * <p>
	 * Defaults to false.
	 * 
	 * @see <a href="http://json-ld.org/spec/latest/json-ld-api/#data-structures">JSONLD Data Structures</a>
	 */
	public static final RioSetting<Boolean> OPTIMIZE = new RioSettingImpl<Boolean>(
			"org.eclipse.rdf4j.rio.jsonld.optimize", "Optimize output", Boolean.FALSE);

	/**
	 * If set to true, the JSON-LD processor will try to convert typed values to JSON native types instead of
	 * using the expanded object form when converting from RDF. xsd:boolean values will be converted to true
	 * or false. xsd:integer and xsd:double values will be converted to JSON numbers.
	 * <p>
	 * Defaults to false for RDF compatibility.
	 * 
	 * @see <a href="http://json-ld.org/spec/latest/json-ld-api/#data-structures">JSONLD Data Structures</a>
	 */
	public static final RioSetting<Boolean> USE_NATIVE_TYPES = new RioSettingImpl<Boolean>(
			"org.eclipse.rdf4j.rio.jsonld.usenativetypes", "Use Native JSON Types", Boolean.FALSE);

	/**
	 * If set to true, the JSON-LD processor will use the expanded rdf:type IRI as the property instead
	 * of @type when converting from RDF.
	 * <p>
	 * Defaults to false.
	 * 
	 * @see <a href="http://json-ld.org/spec/latest/json-ld-api/#data-structures">JSONLD Data Structures</a>
	 */
	public static final RioSetting<Boolean> USE_RDF_TYPE = new RioSettingImpl<Boolean>(
			"org.eclipse.rdf4j.rio.jsonld.userdftype", "Use RDF Type", Boolean.FALSE);

	/**
	 * The {@link JSONLDMode} that the writer will use to reorganise the JSONLD document after it is created.
	 * <p>
	 * Defaults to {@link JSONLDMode#EXPAND} to provide maximum RDF compatibility.
	 * 
	 * @see <a href="http://json-ld.org/spec/latest/json-ld-api/#features">JSONLD Features</a>
	 */
	public static final RioSetting<JSONLDMode> JSONLD_MODE = new RioSettingImpl<JSONLDMode>(
			"org.eclipse.rdf4j.rio.jsonld.mode", "JSONLD Mode", JSONLDMode.EXPAND);

	/**
	 * If set to true, the JSON-LD processor will try to represent the JSON-LD object in a hierarchical view.
	 * <p>
	 * Default to false
	 */
	public static final RioSetting<Boolean> HIERARCHICAL_VIEW = new RioSettingImpl<>(
			"org.eclipse.rdf4j.rio.jsonld.hierarchicalview", "Hierarchical representation of the JSON",
			Boolean.FALSE);

	/**
	 * If set to a non-null, non-empty {@link String}, {@link #LOCAL_CONTEXT} does not have a value set, and
	 * remote context retrieval is not disabled, the given context will be retrieved and used for JSON-LD
	 * processing.
	 * <p>
	 * To disable all remote context retrieval, set the system property
	 * <code>com.github.jsonldjava.disallowRemoteContextLoading</code> to <tt>true</tt>.
	 * <p>
	 * Alternatively, contexts can be
	 * <a href="https://github.com/jsonld-java/jsonld-java#loading-contexts-from-classpathjar">stored on the
	 * classpath and retrieved without network activity</a>.
	 * <p>
	 * Defaults to the empty string <code>""</code> but not used unless explicitly set.
	 * <p>
	 * Must not be set if {@link #LOCAL_CONTEXT} is also set.
	 *
	 * @see <a href="http://json-ld.org/spec/latest/json-ld-api/#context-processing-algorithm">JSONLD Context
	 *      Processing Algorithm</a>
	 */
	public static final RioSetting<String> REMOTE_CONTEXT = new RioSettingImpl<String>(
			"org.eclipse.rdf4j.rio.jsonld.remotecontext", "Remote Context", "");

	/**
	 * If set to a non-null {@link Map}, it will be used as the context for JSON-LD processing.
	 * <p>
	 * Defaults to the empty Map, {@link Collections#emptyMap()} but not used unless explicitly set.
	 * <p>
	 * Must not be set if {@link #REMOTE_CONTEXT} is also set.
	 *
	 * @see <a href="http://json-ld.org/spec/latest/json-ld-api/#context-processing-algorithm">JSONLD Context
	 *      Processing Algorithm</a>
	 */
	public static final RioSetting<Map<String, Object>> LOCAL_CONTEXT = new RioSettingImpl<Map<String, Object>>(
			"org.eclipse.rdf4j.rio.jsonld.localcontext", "Local Context", Collections.emptyMap());

	/**
	 * Private default constructor.
	 */
	private JSONLDSettings() {
	}

}
