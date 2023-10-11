
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

/**
 * Simple XML Persistence Objects (POBJ).
 *
 * <p>
 * This package implements a library for <b>simple</b> persistence of Java objects via XML.
 * It's targeted at Java data structures that are small enough to fit in memory and
 * which are read much more often than they are written, and can be persisted as an XML file.
 * A good example would be configuration information for an application.
 *
 * <p>
 * Attributes and features:
 * <ul>
 * <li>Able to persist an arbitrary java object graph - you supply the XML (de)serialization strategy</li>
 * <li>Automatic support for deep copying the object graph</li>
 * <li>Read access uses natural Java</li>
 * <li>Changes are atomic, serialized, wholesale updates</li>
 * <li>All changes must fully validate</li>
 * <li>Versioning support for optimistic locking</li>
 * <li>Support for "out-of-band" persistent file updates</li>
 * <li>Support for automatic re-indexing on change</li>
 * <li>Support for listener notifications on update</li>
 * <li>Support for schema updates via XSLT transforms using the {@link org.dellroad.stuff.schema} classes</li>
 * <li>Support for JTA/XA transactions</li>
 * </ul>
 *
 * <p>
 * The primary class is {@link org.dellroad.stuff.pobj.PersistentObject}. Typically this class would be accessed through a
 * {@link org.dellroad.stuff.pobj.PersistentObjectSchemaUpdater} which allows for evolution of the XML schema over time;
 * see {@link org.dellroad.stuff.pobj.SpringPersistentObjectSchemaUpdater} for an example of Spring configuration.
 *
 * <p>
 * A {@link org.dellroad.stuff.pobj.PersistentObjectTransactionManager} for use with Spring's transaction support is also provided.
 *
 * @see <a href="https://github.com/archiecobbs/dellroad-stuff">The dellroad-stuff Project</a>
 */
package org.dellroad.stuff.pobj;
