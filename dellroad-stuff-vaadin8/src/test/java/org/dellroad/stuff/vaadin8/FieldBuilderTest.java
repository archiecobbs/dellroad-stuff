
/*
 * Copyright (C) 2011 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin8;

import com.vaadin.data.Binder;
import com.vaadin.data.provider.DataProvider;
import com.vaadin.data.provider.Query;
import com.vaadin.server.Resource;
import com.vaadin.server.Sizeable;
import com.vaadin.shared.ui.ValueChangeMode;
import com.vaadin.ui.ComboBox;
import com.vaadin.ui.IconGenerator;
import com.vaadin.ui.TextArea;
import com.vaadin.ui.TextField;

import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.dellroad.stuff.test.TestSupport;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class FieldBuilderTest extends TestSupport {

    @Test
    public void test1() throws Exception {
        final Binder<Foo> binder = new FieldBuilder<>(Foo.class).buildAndBind().getBinder();

        final TextArea field1 = (TextArea)binder.getBinding("field1").get().getField();
        Assert.assertEquals(field1.getPlaceholder(), "ph");
        Assert.assertEquals(field1.getValueChangeMode(), ValueChangeMode.EAGER);
        Assert.assertEquals(field1.getValueChangeTimeout(), 100);
        Assert.assertEquals(field1.getMaxLength(), 120);
        Assert.assertEquals(field1.getRows(), 33);
        Assert.assertEquals(field1.isWordWrap(), true);
        Assert.assertEquals(field1.isReadOnly(), true);
        Assert.assertEquals(field1.isRequiredIndicatorVisible(), true);
        Assert.assertEquals(field1.getTabIndex(), 17);
        Assert.assertTrue(field1.getStyleName().indexOf("aaa") != -1);
        Assert.assertTrue(field1.getStyleName().indexOf("bbb") != -1);
        Assert.assertEquals(field1.getPrimaryStyleName(), "prim");
        Assert.assertEquals(field1.getWidth(), 20.0f);
        Assert.assertEquals(field1.getWidthUnits(), Sizeable.Unit.PERCENTAGE);
        Assert.assertEquals(field1.getHeight(), 21.0f);
        Assert.assertEquals(field1.getHeightUnits(), Sizeable.Unit.PERCENTAGE);
        Assert.assertEquals(field1.getCaption(), "capt");
        Assert.assertEquals(field1.isCaptionAsHtml(), true);
        Assert.assertEquals(field1.getDescription(), "descr");
        Assert.assertEquals(field1.getId(), "idx");
        Assert.assertEquals(field1.isEnabled(), true);
        Assert.assertEquals(field1.isResponsive(), false);
        Assert.assertEquals(field1.isVisible(), false);

        final TextField field2 = (TextField)binder.getBinding("field2").get().getField();
        Assert.assertTrue(field2 instanceof MyTextField);

        final TextField field3 = (TextField)binder.getBinding("field3").get().getField();
        Assert.assertTrue(field3 instanceof MyTextField);

        final ComboBox<?> field4 = (ComboBox<?>)binder.getBinding("field4").get().getField();
        Assert.assertTrue(field4 instanceof EnumComboBox);
        Assert.assertEquals(((EnumComboBox<?>)field4).getEnumClass(), MyEnum.class);
        Assert.assertEquals(field4.getEmptySelectionCaption(), "bleh");
        Assert.assertTrue(field4.getItemIconGenerator() instanceof MyIconGenerator);
    }

// Classes

    public abstract class Foo {

        @FieldBuilder.AbstractComponent(
            styleNames = { "aaa", "bbb" },
            primaryStyleName = "prim",
            width = "20%",
            height = "21%",
            caption = "capt",
            captionAsHtml = true,
            description = "descr",
            id = "idx",
            enabled = true,
            responsive = false,
            visible = false)
        @FieldBuilder.AbstractField(
            readOnly = true,
            requiredIndicatorVisible = true,
            tabIndex = 17)
        @FieldBuilder.AbstractTextField(
            placeholder = "ph",
            valueChangeMode = ValueChangeMode.EAGER,
            valueChangeTimeout = 100,
            maxLength = 120)
        @FieldBuilder.TextArea(
            rows = 33,
            wordWrap = true)
        public abstract String getField1();
        public abstract void setField1(String x);

        @FieldBuilder.AbstractTextField(type = MyTextField.class)
        public abstract String getField2();
        public abstract void setField2(String x);

        @FieldBuilder.TextField(type = MyTextField.class)
        public abstract String getField3();
        public abstract void setField3(String x);

        @FieldBuilder.ComboBox(
            emptySelectionCaption = "bleh",
            itemIconGenerator = MyIconGenerator.class)
        @FieldBuilder.EnumComboBox
        public abstract MyEnum getField4();
        public abstract void setField4(MyEnum x);
    }

    @SuppressWarnings("serial")
    public static class MyTextField extends TextField {
    }

    @SuppressWarnings("serial")
    public static class MyIconGenerator implements IconGenerator<Object> {

        @Override
        public Resource apply(Object obj) {
            return null;
        }
    }

    public enum MyEnum {
        FOO,
        BAR;
    }
}

