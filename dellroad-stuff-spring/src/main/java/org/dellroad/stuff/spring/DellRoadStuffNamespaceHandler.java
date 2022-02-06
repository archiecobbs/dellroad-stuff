
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.spring;

import org.springframework.beans.factory.xml.NamespaceHandlerSupport;

/**
 * Spring {@link org.springframework.beans.factory.xml.NamespaceHandler NamespaceHandler}
 * for the <code>dellroad-stuff</code> XML namespace.
 *
 * <p>
 * This adds support for the following Spring custom XML tags:
 * <ul>
 *  <li><code>&lt;dellroad-stuff:sql/&gt;</code>, which defines a {@link org.dellroad.stuff.schema.SQLCommandList} bean</li>
 *  <li><code>&lt;dellroad-stuff:sql-update/&gt;</code>, which wraps a {@link org.dellroad.stuff.schema.SQLCommandList}
 *  in a {@link org.dellroad.stuff.spring.SpringSQLSchemaUpdate} bean</li>
 * </ul>
 */
public class DellRoadStuffNamespaceHandler extends NamespaceHandlerSupport {

    public static final String NAMESPACE_URI = "http://dellroad-stuff.googlecode.com/schema/dellroad-stuff";

    public static final String SQL_ELEMENT_NAME = "sql";
    public static final String SQL_UPDATE_ELEMENT_NAME = "sql-update";

    @Override
    public void init() {
        this.registerBeanDefinitionParser(SQL_ELEMENT_NAME, new SQLBeanDefinitionParser());
        this.registerBeanDefinitionParser(SQL_UPDATE_ELEMENT_NAME, new SQLUpdateBeanDefinitionParser());
    }
}

