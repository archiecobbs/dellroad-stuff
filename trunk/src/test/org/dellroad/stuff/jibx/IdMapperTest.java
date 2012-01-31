
/*
 * Copyright (C) 2012 Archie L. Cobbs. All rights reserved.
 *
 * $Id$
 */

package org.dellroad.stuff.jibx;

import org.dellroad.stuff.TestSupport;
import org.testng.annotations.Test;

public class IdMapperTest extends TestSupport {

    @Test
    public void testIdMapper() throws Exception {
        Company company = JiBXUtil.readObject(Company.class, this.getClass().getResource("company.xml"));
        assert company.getEmployees().size() == 3;
        assert company.getEmployees().get(0).getName().equals("Aardvark, Annie");
        assert company.getEmployees().get(1).getName().equals("Appleby, Arnold");
        assert company.getEmployees().get(2).getName().equals("Aurgurkle, Doris");
        assert company.getEmployeeOfTheWeek() == company.getEmployees().get(1);
    }
}

