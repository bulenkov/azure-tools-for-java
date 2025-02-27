/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azuretools.core.mvp.model.function;

import com.microsoft.azure.PagedList;
import com.microsoft.azure.management.Azure;
import com.microsoft.azure.management.appservice.*;
import com.microsoft.azure.management.resources.fluentcore.arm.ResourceUtils;
import com.microsoft.azure.toolkit.lib.common.cache.CacheEvict;
import com.microsoft.azure.toolkit.lib.common.cache.Cacheable;
import com.microsoft.azure.toolkit.lib.common.cache.Preload;
import com.microsoft.azure.toolkit.lib.common.exception.AzureToolkitRuntimeException;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperation;
import com.microsoft.azuretools.authmanage.AuthMethodManager;
import com.microsoft.azuretools.azurecommons.helpers.NotNull;
import com.microsoft.azuretools.core.mvp.model.AzureMvpModel;
import com.microsoft.azuretools.core.mvp.model.ResourceEx;
import com.microsoft.azuretools.core.mvp.model.webapp.AppServiceUtils;
import com.microsoft.azuretools.utils.WebAppUtils;

import java.util.*;
import java.util.stream.Collectors;

public class AzureFunctionMvpModel {
    public static final PricingTier CONSUMPTION_PRICING_TIER = new PricingTier("Consumption", "");
    public static final String SUBSCRIPTION_FUNCTIONS = "subscription-functions";

    public static AzureFunctionMvpModel getInstance() {
        return SingletonHolder.INSTANCE;
    }

    @NotNull
    @AzureOperation(
        name = "function.get",
        params = {"nameFromResourceId(id)", "sid"},
        type = AzureOperation.Type.SERVICE
    )
    public FunctionApp getFunctionById(String sid, String id) throws AzureToolkitRuntimeException {
        final FunctionApp app = getFunctionAppsClient(sid).getById(id);
        if (Objects.isNull(app)) {
            final String error = String.format("cannot find FunctionApp[%s] in subscription[%s]", ResourceUtils.nameFromResourceId(id), sid);
            final String action = String.format("confirm if the FunctionApp[id=%s] still exists", ResourceUtils.nameFromResourceId(id));
            throw new AzureToolkitRuntimeException(error, action);
        }
        return app;
    }

    @AzureOperation(
        name = "function.get",
        params = {"name", "sid"},
        type = AzureOperation.Type.SERVICE
    )
    public FunctionApp getFunctionByName(String sid, String resourceGroup, String name) {
        return getFunctionAppsClient(sid).getByResourceGroup(resourceGroup, name);
    }

    @AzureOperation(
        name = "function.delete",
        params = {"nameFromResourceId(appId)", "sid"},
        type = AzureOperation.Type.SERVICE
    )
    @CacheEvict(cacheName = SUBSCRIPTION_FUNCTIONS, key = "$sid")
    public void deleteFunction(String sid, String appId) {
        getFunctionAppsClient(sid).deleteById(appId);
    }

    @AzureOperation(
        name = "function.restart",
        params = {"nameFromResourceId(appId)", "sid"},
        type = AzureOperation.Type.SERVICE
    )
    public void restartFunction(String sid, String appId) {
        getFunctionById(sid, appId).restart();
    }

    @AzureOperation(
        name = "function.start",
        params = {"nameFromResourceId(appId)", "sid"},
        type = AzureOperation.Type.SERVICE
    )
    public void startFunction(String sid, String appId) {
        getFunctionById(sid, appId).start();
    }

    @AzureOperation(
        name = "function.stop",
        params = {"nameFromResourceId(appId)", "sid"},
        type = AzureOperation.Type.SERVICE
    )
    public void stopFunction(String sid, String appId) {
        getFunctionById(sid, appId).stop();
    }

    /**
     * List app service plan by subscription id and resource group name.
     */
    @AzureOperation(
        name = "appservice|plan.list.subscription|rg",
        params = {"group", "sid"},
        type = AzureOperation.Type.SERVICE
    )
    public List<AppServicePlan> listAppServicePlanBySubscriptionIdAndResourceGroupName(String sid, String group) {
        List<AppServicePlan> appServicePlans = new ArrayList<>();

        Azure azure = AuthMethodManager.getInstance().getAzureClient(sid);
        appServicePlans.addAll(azure.appServices().appServicePlans().listByResourceGroup(group));

        return appServicePlans;
    }

    /**
     * List app service plan by subscription id.
     */
    @AzureOperation(
        name = "appservice|plan.list.subscription",
        params = {"sid"},
        type = AzureOperation.Type.SERVICE
    )
    public List<AppServicePlan> listAppServicePlanBySubscriptionId(String sid) {
        return AuthMethodManager.getInstance().getAzureClient(sid).appServices().appServicePlans().list(true);
    }

    /**
     * List all the Function Apps in selected subscriptions.
     *
     * @param force flag indicating whether force to fetch latest data from server
     * @return list of Function App
     */
    @AzureOperation(
        name = "function.list.subscription|selected",
        type = AzureOperation.Type.SERVICE
    )
    public List<ResourceEx<FunctionApp>> listAllFunctions(final boolean... force) {
        return AzureMvpModel.getInstance().getSelectedSubscriptions().parallelStream()
            .flatMap((sd) -> listFunctionsInSubscription(sd.subscriptionId(), force).stream())
            .collect(Collectors.toList());
    }

    @NotNull
    @AzureOperation(
        name = "function.list.java|subscription|selected",
        type = AzureOperation.Type.SERVICE
    )
    @Preload
    public List<ResourceEx<FunctionApp>> listJavaFunctionApps(final boolean... force) {
        return this.listAllFunctions(force).parallelStream()
            .filter(app -> WebAppUtils.isJavaWebApp(app.getResource()))
            .collect(Collectors.toList());
    }

    @AzureOperation(
        name = "function|envelops.list",
        params = {"nameFromResourceId(id)", "sid"},
        type = AzureOperation.Type.SERVICE
    )
    public List<FunctionEnvelope> listFunctionEnvelopeInFunctionApp(String sid, String id) {
        FunctionApp app = getFunctionById(sid, id);
        PagedList<FunctionEnvelope> functions = app.manager().functionApps().listFunctions(app.resourceGroupName(), app.name());
        functions.loadAll();
        return new ArrayList<>(functions);
    }

    @AzureOperation(
        name = "function.get_publishing_profile",
        params = {"nameFromResourceId(functionAppId)"},
        type = AzureOperation.Type.SERVICE
    )
    public boolean getPublishingProfileXmlWithSecrets(String sid, String functionAppId, String filePath) {
        final FunctionApp app = getFunctionById(sid, functionAppId);
        return AppServiceUtils.getPublishingProfileXmlWithSecrets(app, filePath);
    }

    @AzureOperation(
        name = "function.update_setting",
        params = {"nameFromResourceId(functionAppId)"},
        type = AzureOperation.Type.SERVICE
    )
    public void updateWebAppSettings(String sid, String functionAppId, Map<String, String> toUpdate, Set<String> toRemove) {
        final FunctionApp app = getFunctionById(sid, functionAppId);
        WebAppBase.Update<FunctionApp> update = app.update().withAppSettings(toUpdate);
        for (String key : toRemove) {
            update = update.withoutAppSetting(key);
        }
        update.apply();
    }

    @AzureOperation(
        name = "function.get_tiers",
        type = AzureOperation.Type.TASK
    )
    public List<PricingTier> listFunctionPricingTier() {
        final List<PricingTier> pricingTiers = AzureMvpModel.getInstance().listPricingTier();
        // Add Premium pricing tiers
        pricingTiers.add(new PricingTier(SkuName.ELASTIC_PREMIUM.toString(), "EP1"));
        pricingTiers.add(new PricingTier(SkuName.ELASTIC_PREMIUM.toString(), "EP2"));
        pricingTiers.add(new PricingTier(SkuName.ELASTIC_PREMIUM.toString(), "EP3"));
        pricingTiers.add(CONSUMPTION_PRICING_TIER);
        return pricingTiers;
    }

    public static boolean isApplicationLogEnabled(WebAppBase webAppBase) {
        final WebAppDiagnosticLogs config = webAppBase.diagnosticLogsConfig();
        if (config == null || config.inner() == null || config.inner().applicationLogs() == null) {
            return false;
        }
        final ApplicationLogsConfig applicationLogsConfig = config.inner().applicationLogs();
        return (applicationLogsConfig.fileSystem() != null && applicationLogsConfig.fileSystem().level() != LogLevel.OFF) ||
                (applicationLogsConfig.azureBlobStorage() != null && applicationLogsConfig.azureBlobStorage().level() != LogLevel.OFF) ||
                (applicationLogsConfig.azureTableStorage() != null && applicationLogsConfig.azureTableStorage().level() != LogLevel.OFF);
    }

    @AzureOperation(
        name = "function.turn_on_logs",
        params = {"functionApp.name()"},
        type = AzureOperation.Type.TASK
    )
    public static void enableApplicationLog(FunctionApp functionApp) {
        functionApp.update().updateDiagnosticLogsConfiguration()
                .withApplicationLogging()
                .withLogLevel(LogLevel.INFORMATION)
                .withApplicationLogsStoredOnFileSystem()
                .parent()
                .apply();
    }

    /**
     * List all Function Apps by subscription id.
     */
    @NotNull
    @AzureOperation(
        name = "function.list.subscription",
        params = {"subscriptionId"},
        type = AzureOperation.Type.SERVICE
    )
    @Cacheable(cacheName = "subscription-functions", key = "$subscriptionId", condition = "!(force&&force[0])")
    private List<ResourceEx<FunctionApp>> listFunctionsInSubscription(final String subscriptionId, final boolean... force) {
        final Azure azure = AuthMethodManager.getInstance().getAzureClient(subscriptionId);
        return azure.appServices().functionApps()
                .inner().list().stream().filter(inner -> inner.kind() != null && Arrays.asList(inner.kind().split(",")).contains("functionapp"))
                .map(inner -> new FunctionAppWrapper(subscriptionId, inner))
                .map(app -> new ResourceEx<FunctionApp>(app, subscriptionId))
                .collect(Collectors.toList());
    }

    private static FunctionApps getFunctionAppsClient(String sid) {
        return AuthMethodManager.getInstance().getAzureClient(sid).appServices().functionApps();
    }

    private static final class SingletonHolder {
        private static final AzureFunctionMvpModel INSTANCE = new AzureFunctionMvpModel();
    }
}
