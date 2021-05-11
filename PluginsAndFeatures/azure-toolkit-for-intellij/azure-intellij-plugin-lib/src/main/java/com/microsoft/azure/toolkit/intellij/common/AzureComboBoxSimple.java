/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.intellij.common;

import lombok.Getter;
import lombok.Setter;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

@Setter
@Getter
public class AzureComboBoxSimple<T> extends AzureComboBox<T> {

    private Supplier<? extends List<? extends T>> supplier;
    private Validator validator;

    public AzureComboBoxSimple(@Nonnull final Supplier<? extends List<? extends T>> supplier) {
        super(false);
        this.supplier = supplier;
        this.refreshItems();
    }

    @Nonnull
    protected List<? extends T> loadItems() throws Exception {
        if (Objects.nonNull(this.supplier)) {
            return this.supplier.get();
        }
        return Collections.emptyList();
    }
}
