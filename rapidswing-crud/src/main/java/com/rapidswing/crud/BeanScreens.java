package com.rapidswing.crud;

import com.rapidswing.data.BeanProperties;
import com.rapidswing.forms.FieldBuilder;
import com.rapidswing.forms.FormBuilder;
import com.rapidswing.property.Property;
import com.rapidswing.table.SearchPanel;
import com.rapidswing.table.TableBuilder;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Builds a {@link CrudScreen} from a JavaBean class at run time: one column
 * and one form field per property, a search panel over chosen properties, new items from the no-argument
 * constructor and copies from the copy constructor (or property by property).
 * <pre>
 * CrudScreen&lt;Product&gt; screen = BeanScreens.of(Product.class)
 *         .exclude("id")
 *         .label("sku", "SKU")
 *         .required("name", "sku")
 *         .searchable("name", "category", "price")
 *         .toBuilder()
 *         .context(ctx)
 *         .repository(products)
 *         .names("Product", "Products")
 *         .build();
 * </pre>
 * Supported property types: text, whole numbers, {@link BigDecimal}, {@link LocalDate}, booleans and enums;
 * other properties are left out. Properties without a setter are shown read-only. Labels are derived from
 * property names ("unitPrice" becomes "Unit price") unless set. The returned builder can still be changed, e.g.
 * to add detail tables or page size.
 */
public final class BeanScreens<T> {

    private final Class<T> type;
    private final Set<String> excluded = new HashSet<>();
    private final Set<String> hiddenInList = new HashSet<>();
    private final Set<String> required = new HashSet<>();
    private final Set<String> searchable = new LinkedHashSet<>();
    private final Map<String, String> labels = new HashMap<>();
    private String nameProperty;

    private BeanScreens(Class<T> type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    public static <T> BeanScreens<T> of(Class<T> type) {
        return new BeanScreens<>(type);
    }

    /** Leaves properties out of the list and the form. */
    public BeanScreens<T> exclude(String... properties) {
        excluded.addAll(Arrays.asList(properties));
        return this;
    }

    /** Shows properties in the form only, not as list columns. */
    public BeanScreens<T> formOnly(String... properties) {
        hiddenInList.addAll(Arrays.asList(properties));
        return this;
    }

    public BeanScreens<T> label(String property, String label) {
        labels.put(property, label);
        return this;
    }

    public BeanScreens<T> required(String... properties) {
        required.addAll(Arrays.asList(properties));
        return this;
    }

    /** Offers these properties in a search panel above the list. */
    public BeanScreens<T> searchable(String... properties) {
        searchable.addAll(Arrays.asList(properties));
        return this;
    }

    /** The property that names an item in titles and confirmations; defaults to the first text property. */
    public BeanScreens<T> itemName(String property) {
        this.nameProperty = property;
        return this;
    }

    /** The properties used, in order. */
    public List<PropertyDescriptor> properties() {
        List<PropertyDescriptor> used = new ArrayList<>();
        for (PropertyDescriptor descriptor : BeanProperties.of(type)) {
            if (!excluded.contains(descriptor.getName()) && supported(descriptor.getPropertyType())) {
                used.add(descriptor);
            }
        }
        return used;
    }

    /** The label of a property: set with {@link #label}, or derived from its name. */
    public String labelOf(String property) {
        String label = labels.get(property);
        if (label != null) {
            return label;
        }
        StringBuilder words = new StringBuilder();
        for (char c : property.toCharArray()) {
            if (Character.isUpperCase(c) && words.length() > 0) {
                words.append(' ').append(Character.toLowerCase(c));
            } else {
                words.append(words.length() == 0 ? Character.toUpperCase(c) : c);
            }
        }
        return words.toString();
    }

    /** A screen builder with columns, form, search, new item, copy and item name filled in. */
    public CrudScreen.Builder<T> toBuilder() {
        List<PropertyDescriptor> properties = properties();
        if (properties.isEmpty()) {
            throw new IllegalArgumentException(type.getName() + " has no supported properties");
        }
        CrudScreen.Builder<T> builder = CrudScreen.builder(type)
                .newItem(this::newInstance)
                .copy(this::copy)
                .itemName(itemNameFunction(properties))
                .columns(table -> columns(table, properties))
                .form((form, edit) -> form(form, edit, properties));
        if (!searchable.isEmpty()) {
            builder.search(panel -> search(panel, properties));
        }
        return builder;
    }

    // ---- Internals -----------------------------------------------------------------------------------

    private TableBuilder<T> columns(TableBuilder<T> table, List<PropertyDescriptor> properties) {
        TableBuilder<T> current = table;
        for (PropertyDescriptor descriptor : properties) {
            if (hiddenInList.contains(descriptor.getName())) {
                continue;
            }
            current = column(current, descriptor);
        }
        return current;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private TableBuilder<T> column(TableBuilder<T> table, PropertyDescriptor descriptor) {
        String name = descriptor.getName();
        Class columnType = boxed(descriptor.getPropertyType());
        Function<T, Object> getter = item -> BeanProperties.get(item, name);
        return table.column(labelOf(name), columnType, getter).field(name);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private FormBuilder form(FormBuilder form, EditContext<T> edit, List<PropertyDescriptor> properties) {
        FormBuilder current = form;
        for (PropertyDescriptor descriptor : properties) {
            String name = descriptor.getName();
            Class<?> propertyType = descriptor.getPropertyType();
            boolean primitive = propertyType.isPrimitive();
            Function<T, Object> getter = item -> BeanProperties.get(item, name);
            BiConsumer<T, Object> setter = (item, value) -> {
                if (descriptor.getWriteMethod() != null && !(primitive && value == null)) {
                    BeanProperties.set(item, name, value);
                }
            };
            Property property = edit.property(getter, setter);
            String label = labelOf(name);
            Class<?> boxed = boxed(propertyType);
            FieldBuilder<?> field;
            if (boxed == Boolean.class) {
                field = current.checkbox(label, property);
            } else if (boxed.isEnum()) {
                field = current.combo(label, property, (Object[]) boxed.getEnumConstants());
            } else if (boxed == Integer.class) {
                field = current.integer(label, property);
            } else if (boxed == BigDecimal.class) {
                field = current.decimal(label, property);
            } else if (boxed == LocalDate.class) {
                field = current.date(label, property);
            } else if (boxed == Long.class) {
                field = current.text(label, property, com.rapidswing.binding.Converters.longs());
            } else {
                field = current.text(label, property);
            }
            if (required.contains(name)) {
                field.required();
            }
            if (descriptor.getWriteMethod() == null) {
                field.enabledWhen(new Property<>(false));
            }
            current = field;
        }
        return current;
    }

    private SearchPanel search(SearchPanel panel, List<PropertyDescriptor> properties) {
        for (PropertyDescriptor descriptor : properties) {
            if (searchable.contains(descriptor.getName())) {
                panel.field(descriptor.getName(), labelOf(descriptor.getName()), descriptor.getPropertyType());
            }
        }
        return panel;
    }

    private Function<? super T, String> itemNameFunction(List<PropertyDescriptor> properties) {
        String property = nameProperty;
        if (property == null) {
            for (PropertyDescriptor descriptor : properties) {
                if (descriptor.getPropertyType() == String.class) {
                    property = descriptor.getName();
                    break;
                }
            }
        }
        if (property == null) {
            return String::valueOf;
        }
        String chosen = property;
        return item -> {
            Object value = BeanProperties.get(item, chosen);
            return value == null || value.toString().isEmpty() ? String.valueOf(item) : value.toString();
        };
    }

    private T newInstance() {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(type.getName() + " needs a no-argument constructor for New", e);
        }
    }

    /** The copy constructor if there is one, otherwise a new instance with every writable property copied. */
    private T copy(T item) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor(type);
            constructor.setAccessible(true);
            return constructor.newInstance(item);
        } catch (NoSuchMethodException e) {
            T copy = newInstance();
            for (PropertyDescriptor descriptor : BeanProperties.of(type)) {
                if (descriptor.getWriteMethod() != null) {
                    BeanProperties.set(copy, descriptor.getName(), BeanProperties.get(item, descriptor.getName()));
                }
            }
            return copy;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not copy " + type.getName(), e);
        }
    }

    private static boolean supported(Class<?> type) {
        Class<?> boxed = boxed(type);
        return boxed == String.class || boxed == Integer.class || boxed == Long.class || boxed == BigDecimal.class
                || boxed == LocalDate.class || boxed == Boolean.class || boxed.isEnum();
    }

    private static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        switch (type.getName().toLowerCase(Locale.ROOT)) {
            case "int":
                return Integer.class;
            case "long":
                return Long.class;
            case "boolean":
                return Boolean.class;
            default:
                return type;
        }
    }
}
