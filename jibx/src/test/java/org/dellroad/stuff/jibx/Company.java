
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.jibx;

import java.util.ArrayList;
import java.util.List;

public class Company {

    private List<Employee> employees = new ArrayList<>();
    private Employee employeeOfTheWeek;

    public List<Employee> getEmployees() {
        return this.employees;
    }
    public void setEmployees(List<Employee> employees) {
        this.employees = employees;
    }

    public Employee getEmployeeOfTheWeek() {
        return this.employeeOfTheWeek;
    }
    public void setEmployeeOfTheWeek(Employee employeeOfTheWeek) {
        this.employeeOfTheWeek = employeeOfTheWeek;
    }
}
