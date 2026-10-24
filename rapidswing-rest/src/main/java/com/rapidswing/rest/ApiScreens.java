package com.rapidswing.rest;

import com.rapidswing.app.AppContext;
import com.rapidswing.binding.Converters;
import com.rapidswing.crud.CrudScreen;
import com.rapidswing.crud.EditContext;
import com.rapidswing.forms.FieldBuilder;
import com.rapidswing.forms.FormBuilder;
import com.rapidswing.forms.Validator;
import com.rapidswing.forms.Validators;
import com.rapidswing.property.Property;
import com.rapidswing.table.TableBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.function.Function;

/**
 * Builds a complete {@link CrudScreen} for an API resource at runtime: a column per field, a form field per
 * editable field with validation rules from the schema, and New/Save/Delete only where the API allows them.
 * <pre>
 * OpenApiSpec spec = OpenApiSpec.fetch(client);
 * CrudScreen&lt;ApiRecord&gt; customers = ApiScreens.create(ctx, spec.resource("customers"), client);
 * </pre>
 */
public final class ApiScreens {

    private ApiScreens() {
    }

    public static CrudScreen<ApiRecord> create(AppContext ctx, ApiResource resource, RestClient client) {
        return create(ctx, resource, new RestRepository(client, resource));
    }

    public static CrudScreen<ApiRecord> create(AppContext ctx, ApiResource resource, RestRepository repository) {
        DateTimeFormatter dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(ctx.i18n().getLocale());
        return CrudScreen.builder(ApiRecord.class)
                .context(ctx)
                .names(resource.getSingularTitle(), resource.getTitle())
                .repository(repository)
                .newItem(() -> new ApiRecord(resource.getIdField()))
                .copy(ApiRecord::new)
                .itemName(record -> itemName(resource, record))
                .allowCreate(resource.canCreate())
                .allowEdit(resource.canUpdate())
                .allowDelete(resource.canDelete())
                .columns(table -> {
                    TableBuilder<ApiRecord> columns = table;
                    for (ApiField field : resource.getFields()) {
                        columns = column(columns, field, field.getJavaType(), dates);
                    }
                    return columns;
                })
                .form((form, edit) -> {
                    FormBuilder fields = form;
                    for (ApiField field : resource.getFields()) {
                        if (!field.isReadOnly()) {
                            fields = field(fields, edit, field);
                        }
                    }
                    return fields;
                })
                .build();
    }

    /** The first required text field's value, e.g. a name; otherwise "Customer 42". */
    static String itemName(ApiResource resource, ApiRecord record) {
        for (ApiField field : resource.getFields()) {
            Object value = record.get(field.getName());
            if (field.getType() == ApiField.Type.STRING && field.isRequired() && value != null) {
                return value.toString();
            }
        }
        Object id = record.getId();
        return resource.getSingularTitle() + (id != null ? " " + id : "");
    }

    private static <V> TableBuilder<ApiRecord> column(TableBuilder<ApiRecord> table, ApiField field, Class<V> type,
                                                      DateTimeFormatter dates) {
        String name = field.getName();
        if (type == LocalDate.class) {
            return table.column(field.getLabel(), LocalDate.class, record -> (LocalDate) record.get(name))
                    .format(dates::format);
        }
        return table.column(field.getLabel(), type, record -> type.cast(record.get(name)));
    }

    private static FormBuilder field(FormBuilder form, EditContext<ApiRecord> edit, ApiField field) {
        String label = field.getLabel();
        switch (field.getType()) {
            case INTEGER: {
                FieldBuilder<Integer> built = form.integer(label, property(edit, field, Integer.class));
                return range(required(built, field), field, BigDecimal::intValue);
            }
            case LONG: {
                FieldBuilder<Long> built = form.text(label, property(edit, field, Long.class), Converters.longs());
                return range(required(built, field), field, BigDecimal::longValue);
            }
            case DECIMAL: {
                FieldBuilder<BigDecimal> built = form.decimal(label, property(edit, field, BigDecimal.class));
                return range(required(built, field), field, value -> value);
            }
            case BOOLEAN:
                return form.checkbox(label, property(edit, field, Boolean.class));
            case DATE:
                return required(form.date(label, property(edit, field, LocalDate.class)), field);
            case ENUM:
                return required(form.combo(label, property(edit, field, String.class),
                        field.getEnumValues().toArray(new String[0])), field);
            default:
                return text(required(form.text(label, property(edit, field, String.class)), field), field);
        }
    }

    private static <V> Property<V> property(EditContext<ApiRecord> edit, ApiField field, Class<V> type) {
        String name = field.getName();
        return edit.property(record -> type.cast(record.get(name)), (record, value) -> record.set(name, value));
    }

    private static <V> FieldBuilder<V> required(FieldBuilder<V> built, ApiField field) {
        return field.isRequired() ? built.required() : built;
    }

    private static FieldBuilder<String> text(FieldBuilder<String> built, ApiField field) {
        if (field.getMinLength() != null && field.getMinLength() > 0) {
            built.validate(Validators.minLength(field.getMinLength()));
        }
        if (field.getMaxLength() != null) {
            built.validate(Validators.maxLength(field.getMaxLength()));
        }
        if (field.isEmail()) {
            built.validate(Validators.email());
        }
        if (field.getPattern() != null) {
            built.validate(Validators.pattern(field.getPattern(), "Must match " + field.getPattern()));
        }
        return built;
    }

    private static <V extends Comparable<? super V>> FieldBuilder<V> range(FieldBuilder<V> built, ApiField field,
                                                                         Function<BigDecimal, V> convert) {
        Validator<V> rule = null;
        if (field.getMinimum() != null && field.getMaximum() != null) {
            rule = Validators.range(convert.apply(field.getMinimum()), convert.apply(field.getMaximum()));
        } else if (field.getMinimum() != null) {
            rule = Validators.min(convert.apply(field.getMinimum()));
        } else if (field.getMaximum() != null) {
            rule = Validators.max(convert.apply(field.getMaximum()));
        }
        return rule != null ? built.validate(rule) : built;
    }
}
