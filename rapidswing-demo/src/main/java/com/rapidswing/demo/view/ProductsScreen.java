package com.rapidswing.demo.view;

import com.rapidswing.app.AppContext;
import com.rapidswing.crud.BeanScreens;
import com.rapidswing.crud.CrudScreen;
import com.rapidswing.crud.InMemoryRepository;
import com.rapidswing.demo.Roles;
import com.rapidswing.demo.model.Product;
import com.rapidswing.i18n.I18n;

import java.util.Arrays;

/** A screen generated from the {@link Product} bean at run time: no columns or form written by hand. */
public final class ProductsScreen {

    private static final InMemoryRepository<Product> PRODUCTS = new InMemoryRepository<>(Product::getId, Product::new)
            .addAll(Arrays.asList(
                    new Product(1, "Claw hammer", "TL-1001", Product.Category.TOOLS, "18.90", 42),
                    new Product(2, "Cordless drill", "TL-2040", Product.Category.TOOLS, "129.00", 7),
                    new Product(3, "Garden hose 25 m", "GD-0025", Product.Category.GARDEN, "34.50", 15),
                    new Product(4, "Pruning shears", "GD-0310", Product.Category.GARDEN, "22.00", 0),
                    new Product(5, "LED bulb E27", "EL-0927", Product.Category.ELECTRICAL, "4.99", 380),
                    new Product(6, "Extension cord 5 m", "EL-1505", Product.Category.ELECTRICAL, "12.75", 64),
                    new Product(7, "Wall paint, white 10 l", "PT-1010", Product.Category.PAINT, "54.00", 23)));

    private ProductsScreen() {
    }

    public static CrudScreen<Product> create(AppContext ctx) {
        I18n i18n = ctx.i18n();
        boolean canChange = ctx.session().hasAnyRole(Roles.ADMIN, Roles.SALES);
        return BeanScreens.of(Product.class)
                .exclude("id")
                .label("name", i18n.get("product.name"))
                .label("sku", i18n.get("product.sku"))
                .label("category", i18n.get("product.category"))
                .label("price", i18n.get("product.price"))
                .label("inStock", i18n.get("product.inStock"))
                .label("active", i18n.get("product.active"))
                .required("name", "sku")
                .searchable("name", "category", "price", "inStock")
                .toBuilder()
                .context(ctx)
                .names(i18n.get("product.singular"), i18n.get("product.plural"))
                .repository(PRODUCTS)
                .newItem(() -> {
                    Product product = new Product();
                    product.setId(PRODUCTS.size() + 100);
                    return product;
                })
                .allowCreate(canChange)
                .allowEdit(canChange)
                .allowDelete(ctx.session().hasRole(Roles.ADMIN))
                .build();
    }
}
