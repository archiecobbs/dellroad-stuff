
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin8;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Provides the information necessary to auto-generate a {@link PropertyDefinition} based on the annotated getter method.
 *
 * <p>
 * {@link ProvidesProperty &#64;ProvidesProperty} method annotations can be used to automatically generate a
 * {@link PropertySet}'s using a {@link ProvidesPropertyScanner}. The resulting {@link PropertySet} can then
 * be provided to {@link com.vaadin.ui.Grid#withPropertySet Grid.withPropertySet()} for example.
 *
 * <p>
 * This annotation indicates that a read-only Vaadin {@link com.vaadin.data.PropertyDefinition} having the type
 * equal to the method's return type is accessible by reading that method. Annotated methods must have zero parameters.
 *
 * <p>
 * For example:
 * <blockquote><pre>
 * // Container backing object class
 * public class User {
 *
 *     public static final String USERNAME_PROPERTY = "username";
 *     public static final String REAL_NAME_PROPERTY = "realName";
 *
 *     private String username;
 *     private String realName;
 *
 *     public String getUsername() {
 *         return this.username;
 *     }
 *     public void setUsername(String username) {
 *         this.username = username;
 *     }
 *
 *     <b>&#64;ProvidesProperty</b>                     // property name "realName" is implied
 *     public String getRealName() {
 *         return this.realName;
 *     }
 *     public void setRealName(String realName) {
 *         this.realName = realName;
 *     }
 *
 *     <b>&#64;ProvidesProperty(USERNAME_PROPERTY)</b>  // display usernames in fixed-width font
 *     private Label usernameProperty() {
 *         return new Label("&lt;code&gt;" + StringUtil.escapeHtml(this.username) + "&lt;/code&gt;", ContentMode.HTML);
 *     }
 * }
 *
 * // Build Grid showing users with auto-generated properties
 * Grid<User> grid = Grid.withPropertySet(new ProvidesPropertyScanner(User.class).getPropertySet());
 * grid.setVisibleColumns(User.USERNAME_PROPERTY, User.REAL_NAME_PROPERTY);
 * ...
 * </pre></blockquote>
 *
 * <p>
 * Some details regarding {@link ProvidesProperty &#64;ProvidesProperty} annotations on methods:
 *  <ul>
 *  <li>Only non-void methods taking zero parameters are supported; {@link ProvidesProperty &#64;ProvidesProperty}
 *      annotations on other methods are ignored</li>
 *  <li>Protected, package private, and private methods are supported.</li>
 *  <li>{@link ProvidesProperty &#64;ProvidesProperty} annotations declared in super-types (including interfaces)
 *      are supported</li>
 *  <li>If a method and the superclass or superinterface method it overrides are both annotated with
 *      {@link ProvidesProperty &#64;ProvidesProperty}, then the overridding method's annotation takes precedence.
 *  <li>If two methods with different names are annotated with {@link ProvidesProperty &#64;ProvidesProperty} for the same
 *      {@linkplain #value property name}, then the declaration in the class which is a sub-type of the other
 *      wins (if the two classes are equal or not comparable, an exception is thrown). This allows subclasses
 *      to "override" which method supplies a given property.</li>
 *  </ul>
 *
 * @see ProvidesPropertyScanner
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.FIELD })
@Documented
public @interface ProvidesProperty {

    /**
     * Get the name of the Vaadin property.
     *
     * <p>
     * If this is left unset (empty string), then the bean property name of the annotated bean property "getter" method is used.
     *
     * @return property name
     */
    String value() default "";

    /**
     * Get the caption for the Vaadin property.
     *
     * <p>
     * If this is left unset (empty string), then the name of the property is used.
     *
     * @return property caption
     */
    String caption() default "";
}

