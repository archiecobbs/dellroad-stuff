
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

/**
 * Vaadin-related classes.
 *
 * <p>
 * Classes include:
 * <ul>
 *  <li>{@link org.dellroad.stuff.vaadin8.FieldBuilder} provides automatic creation of form fields based
 *      on introspection of Java bean properties with annotations.</li>
 *  <li>{@link org.dellroad.stuff.vaadin8.ProvidesPropertyScanner} provides automatic creation of a
 *      {@link com.vaadin.data.PropertySet} based on on introspection of Java bean properties with
 *      {@link org.dellroad.stuff.vaadin8.ProvidesProperty &#64;ProvidesProperty} annotations.</li>
 *  <li>{@link org.dellroad.stuff.vaadin8.VaadinExternalListener}, a support superclass for listeners scoped to a
 *      Vaadin application that avoids memory leaks when listening to more widely scoped event sources.</li>
 *  <li>{@link org.dellroad.stuff.vaadin8.VaadinUtil} provides some utility and convenience methods.</li>
 * </ul>
 *
 * @see org.dellroad.stuff.vaadin8.VaadinExternalListener
 * @see <a href="http://vaadin.com/">Vaadin</a>
 */
package org.dellroad.stuff.vaadin8;
