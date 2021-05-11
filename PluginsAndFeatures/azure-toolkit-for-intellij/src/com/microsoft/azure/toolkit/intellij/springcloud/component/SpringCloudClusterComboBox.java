/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.intellij.springcloud.component;

import com.microsoft.azure.toolkit.intellij.common.AzureComboBox;
import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperation;
import com.microsoft.azure.toolkit.lib.springcloud.AzureSpringCloud;
import com.microsoft.azure.toolkit.lib.springcloud.SpringCloudCluster;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SpringCloudClusterComboBox extends AzureComboBox<SpringCloudCluster> {

    private String subscriptionId;

    @Override
    protected String getItemText(final Object item) {
        if (Objects.isNull(item)) {
            return AzureComboBox.EMPTY_ITEM;
        }
        return ((SpringCloudCluster) item).name();
    }

    public void setSubscription(String subscriptionId) {
        if (Objects.equals(subscriptionId, this.subscriptionId)) {
            return;
        }
        this.subscriptionId = subscriptionId;
        if (subscriptionId == null) {
            this.clear();
            return;
        }
        this.refreshItems();
    }

    @NotNull
    @Override
    @AzureOperation(
        name = "springcloud|cluster.list.subscription",
        params = {"this.subscription.subscriptionId()"},
        type = AzureOperation.Type.SERVICE
    )
    protected List<? extends SpringCloudCluster> loadItems() throws Exception {
        if (Objects.nonNull(this.subscriptionId)) {
            final String sid = this.subscriptionId;
            final AzureSpringCloud az = Azure.az(AzureSpringCloud.class);
            return az.clusters();
        }
        return Collections.emptyList();
    }
}
