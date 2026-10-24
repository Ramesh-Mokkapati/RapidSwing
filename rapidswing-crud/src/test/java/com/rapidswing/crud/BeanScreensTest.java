package com.rapidswing.crud;

import com.rapidswing.testing.TestTasks;
import com.rapidswing.testing.Ui;
import org.junit.jupiter.api.Test;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeanScreensTest {

    public enum Category { TOOLS, GARDEN }

    public static class Product {
        private int id;
        private String name;
        private BigDecimal unitPrice;
        private Category category = Category.TOOLS;
        private boolean active = true;
        private Object ignored;

        public Product() {
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
        }

        public Category getCategory() {
            return category;
        }

        public void setCategory(Category category) {
            this.category = category;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }

        public Object getIgnored() {
            return ignored;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Product && ((Product) other).id == id;
        }

        @Override
        public int hashCode() {
            return id;
        }
    }

    private static Product product(int id, String name, String price) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setUnitPrice(new BigDecimal(price));
        return product;
    }

    @Test
    void columnsFormAndSearchComeFromTheBean() {
        BeanScreens<Product> beans = BeanScreens.of(Product.class).exclude("id").required("name").searchable("name");
        assertEquals(Arrays.asList("name", "unitPrice", "category", "active"),
                beans.properties().stream().map(java.beans.PropertyDescriptor::getName).collect(Collectors.toList()));
        assertEquals("Unit price", beans.labelOf("unitPrice"));

        InMemoryRepository<Product> repository = new InMemoryRepository<Product>(Product::getId, p -> {
            Product copy = product(p.getId(), p.getName(), p.getUnitPrice().toPlainString());
            copy.setCategory(p.getCategory());
            copy.setActive(p.isActive());
            return copy;
        }).addAll(Arrays.asList(product(1, "Hammer", "12.50"), product(2, "Rake", "20")));

        CrudScreen<Product> screen = Ui.onEdt(() -> {
            CrudScreen<Product> built = beans.toBuilder()
                    .repository(repository)
                    .tasks(TestTasks.immediate())
                    .confirmWith(message -> true)
                    .build();
            built.onShow();
            return built;
        });

        assertEquals(4, screen.getGrid().getModel().getColumnCount());
        assertEquals("Unit price", screen.getGrid().getModel().getColumn(1).getName());
        assertNotNull(screen.getSearchPanel());

        Ui.onEdt(() -> screen.getGrid().getTable().setRowSelectionInterval(0, 0));
        assertTrue(screen.getForm().component("Category") instanceof JComboBox);
        assertTrue(screen.getForm().component("Active") instanceof JCheckBox);
        Ui.onEdt(() -> {
            ((JTextField) screen.getForm().component("Name")).setText("Claw hammer");
            ((JCheckBox) screen.getForm().component("Active")).doClick();
            screen.save();
        });
        Product saved = repository.find(com.rapidswing.data.Query.all()).getItems().get(0);
        assertEquals("Claw hammer", saved.getName());
        assertFalse(saved.isActive());

        Ui.onEdt(screen::newItem);
        assertEquals(Category.TOOLS, ((JComboBox<?>) screen.getForm().component("Category")).getSelectedItem());
    }
}
