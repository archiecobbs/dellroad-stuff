
/*
 * Copyright (C) 2022 Archie L. Cobbs. All rights reserved.
 */

package org.dellroad.stuff.vaadin22.field;

import com.vaadin.flow.component.ItemLabelGenerator;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.textfield.Autocapitalize;
import com.vaadin.flow.component.textfield.Autocomplete;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextAreaVariant;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.data.renderer.TextRenderer;
import com.vaadin.flow.data.selection.MultiSelect;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.function.ValueProvider;

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

import org.dellroad.stuff.vaadin22.grid.GridColumn;
import org.dellroad.stuff.vaadin22.data.EnumDataProvider;

public class FieldBuilderTest extends TestSupport {

    @Test
    public void test1() throws Exception {

        final FieldBuilder<Foo> fieldBuilder = new FieldBuilder<>(Foo.class);
        final Binder<Foo> binder = new Binder<>(Foo.class);
        fieldBuilder.bindFields(binder);

    // Check field1

        final MyTextArea field1 = (MyTextArea)binder.getBinding("field1").get().getField();
        Assert.assertEquals(field1.getValueChangeTimeout(), 123);
        Assert.assertEquals(field1.getMinLength(), 456);
        Assert.assertTrue(field1.getSuffixComponent() instanceof Label);
        Assert.assertEquals(field1.getTabIndex(), 4);
        Assert.assertEquals(field1.getAutocapitalize(), Autocapitalize.WORDS);
        Assert.assertEquals(field1.getAutocomplete(), Autocomplete.EMAIL);
        Assert.assertEquals(field1.isEnabled(), false);
        Assert.assertEquals(field1.isRequired(), true);
        Assert.assertEquals(field1.getMinHeight(), "10px");
        Assert.assertEquals(field1.isAutocorrect(), true);
        Assert.assertEquals(field1.getMaxHeight(), "20px");
        Assert.assertEquals(field1.isRequiredIndicatorVisible(), true);
        Assert.assertEquals(field1.getId().get(), "id123");
        Assert.assertEquals(field1.getPlaceholder(), "Hi there");
        Assert.assertEquals(field1.isAutoselect(), false);
        Assert.assertEquals(field1.getValueChangeMode(), ValueChangeMode.ON_BLUR);
        Assert.assertEquals(field1.getMaxWidth(), "30px");
        Assert.assertEquals(field1.getHeight(), "40px");
        Assert.assertEquals(field1.isVisible(), false);
        Assert.assertEquals(field1.getErrorMessage(), "Boo boo");
        Assert.assertEquals(field1.isReadOnly(), true);
        Assert.assertEquals(field1.getMinWidth(), "50px");
        Assert.assertEquals(field1.getLabel(), "Blah blah");
        Assert.assertEquals(field1.isAutofocus(), true);
        Assert.assertEquals(field1.getHelperText(), "Help me");
        Assert.assertEquals(field1.isClearButtonVisible(), true);
        Assert.assertEquals(field1.isPreventInvalidInput(), true);
        Assert.assertEquals(field1.getWidth(), "60px");
        Assert.assertTrue(field1.getPrefixComponent() instanceof Label);
        Assert.assertTrue(field1.getHelperComponent() instanceof Label);
        Assert.assertEquals(field1.getMaxLength(), 55);
        Assert.assertTrue(field1.getClassNames().contains("abc"));
        Assert.assertTrue(field1.getClassNames().contains("def"));
        Assert.assertTrue(field1.getThemeNames().contains("ghi"));
        Assert.assertTrue(field1.getThemeNames().contains("jkl"));
        Assert.assertEquals(field1.getStyle().get("overflow-y"), "auto");

    // Check field2

        final ComboBox<?> field2 = (ComboBox<?>)binder.getBinding("field2").get().getField();
        Assert.assertTrue(field2.getDataProvider() instanceof EnumDataProvider);
        final EnumDataProvider<?> dp2 = (EnumDataProvider<?>)field2.getDataProvider();
        Assert.assertEquals(dp2.getEnumType(), MyEnum.class);
        Assert.assertEquals(field2.getPlaceholder(), "bleh");
        Assert.assertTrue(field2.getItemLabelGenerator() instanceof MyItemLabelGenerator);

    // Check field3

        final ComboBox<?> field3 = (ComboBox<?>)binder.getBinding("field3").get().getField();
        Assert.assertTrue(field3.getItemLabelGenerator() instanceof MyItemLabelGenerator);

    // Check field4

        final MultiSelect<?, ?> field4 = (MultiSelect<?, ?>)binder.getBinding("field4").get().getField();
        final Grid<?> grid4 = (Grid<?>)fieldBuilder.getFieldComponents().get("field4").getComponent();
        Assert.assertTrue(grid4.getDataProvider() instanceof EnumDataProvider);
        final EnumDataProvider<?> dp4 = (EnumDataProvider<?>)grid4.getDataProvider();
        Assert.assertEquals(dp4.getEnumType(), MyEnum.class);
        final Grid.Column<?> column4 = grid4.getColumnByKey("mykey");
        Assert.assertTrue(column4.getRenderer() instanceof MyEnumRenderer);
    }

// Classes

    public abstract class Foo {

        @FieldBuilder.TextArea(
          implementation = MyTextArea.class,
          valueChangeTimeout = 123,
          minLength = 456,
          suffixComponent = Label.class,
          tabIndex = 4,
          autocapitalize = Autocapitalize.WORDS,
          autocomplete = Autocomplete.EMAIL,
          enabled = false,
          required = true,
          minHeight = "10px",
          autocorrect = true,
          maxHeight = "20px",
          requiredIndicatorVisible = true,
          id = "id123",
          placeholder = "Hi there",
          autoselect = false,
          valueChangeMode = ValueChangeMode.ON_BLUR,
          maxWidth = "30px",
          height = "40px",
          visible = false,
          errorMessage = "Boo boo",
          readOnly = true,
          minWidth = "50px",
          label = "Blah blah",
          autofocus = true,
          helperText = "Help me",
          clearButtonVisible = true,
          preventInvalidInput = true,
          width = "60px",
          prefixComponent = Label.class,
          helperComponent = Label.class,
          styleProperties = { "overflow-y", "auto", "dummy-ignored" },
          maxLength = 55,
          addClassNames = { "abc", "def" },
          addThemeNames = { "ghi", "jkl" },
          addThemeVariants = { TextAreaVariant.LUMO_ALIGN_CENTER, TextAreaVariant.LUMO_SMALL })
        @FieldBuilder.Binding(required = "required field")
        public abstract String getField1();
        public abstract void setField1(String x);

        @FieldBuilder.ComboBox(
          placeholder = "bleh",
          items = EnumDataProvider.class,
          itemLabelGenerator = MyItemLabelGenerator.class)
        public abstract MyEnum getField2();
        public abstract void setField2(MyEnum x);

        @FieldBuilder.ComboBox
        public abstract Bar getField3();
        public abstract void setField3(Bar x);

        @FieldBuilder.GridMultiSelect(items = EnumDataProvider.class,
          column = @GridColumn(key = "mykey", renderer = MyEnumRenderer.class))
        public abstract Set<MyEnum> getField4();
        public abstract void setField4(Set<MyEnum> x);
    }

    @SuppressWarnings("serial")
    public static class Bar {

        @FieldBuilder.FieldDefault("itemLabelGenerator")
        private static MyItemLabelGenerator<Bar> buildItemLabelGenerator() {
            return new MyItemLabelGenerator<>();
        }
    }

    @SuppressWarnings("serial")
    public static class MyTextArea extends TextArea {
    }

    @SuppressWarnings("serial")
    public static class MyItemLabelGenerator<T> implements ItemLabelGenerator<T> {

        @Override
        public String apply(T obj) {
            return "foo";
        }
    }

    public enum MyEnum {
        FOO,
        BAR;

        @FieldBuilder.FieldDefault("renderer")
        private static MyEnumRenderer buildRenderer(FieldBuilderContext ctx) {
            return new MyEnumRenderer(null);
        }
    }

    @SuppressWarnings("serial")
    public static class MyEnumRenderer extends TextRenderer<MyEnum> {

        public MyEnumRenderer(ValueProvider<?, ?> valueProvider) {
            super(MyEnum::name);
        }
    }
}
