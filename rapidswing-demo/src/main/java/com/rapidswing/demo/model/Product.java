package com.rapidswing.demo.model;

import java.math.BigDecimal;

/**
 * A plain JavaBean: the products screen is generated from its properties at run time with
 * {@link com.rapidswing.crud.BeanScreens}, without any screen code of its own.
 */
public class Product {

    public enum Category { TOOLS, GARDEN, ELECTRICAL, PAINT }

    private int id;
    private String name = "";
    private String sku = "";
    private Category category = Category.TOOLS;
    private BigDecimal price = BigDecimal.ZERO;
    private int inStock;
    private boolean active = true;

    public Product() {
    }

    public Product(int id, String name, String sku, Category category, String price, int inStock) {
        this.id = id;
        this.name = name;
        this.sku = sku;
        this.category = category;
        this.price = new BigDecimal(price);
        this.inStock = inStock;
    }

    public Product(Product other) {
        this(other.id, other.name, other.sku, other.category, other.price.toPlainString(), other.inStock);
        this.active = other.active;
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

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getInStock() {
        return inStock;
    }

    public void setInStock(int inStock) {
        this.inStock = inStock;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Product && ((Product) other).id == id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}
