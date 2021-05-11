/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.intellij.springcloud.properties;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.JBTable;
import com.microsoft.azure.management.resources.Subscription;
import com.microsoft.azure.toolkit.intellij.appservice.subscription.SubscriptionComboBox;
import com.microsoft.azure.toolkit.intellij.common.AzureComboBox.ItemReference;
import com.microsoft.azure.toolkit.intellij.common.AzureFormPanel;
import com.microsoft.azure.toolkit.intellij.common.EnvironmentVariablesTextFieldWithBrowseButton;
import com.microsoft.azure.toolkit.intellij.springcloud.component.SpringCloudAppComboBox;
import com.microsoft.azure.toolkit.intellij.springcloud.component.SpringCloudClusterComboBox;
import com.microsoft.azure.toolkit.lib.common.form.AzureFormInput;
import com.microsoft.azure.toolkit.lib.springcloud.SpringCloudApp;
import com.microsoft.azure.toolkit.lib.springcloud.SpringCloudAppEntity;
import com.microsoft.azure.toolkit.lib.springcloud.SpringCloudCluster;
import com.microsoft.azure.toolkit.lib.springcloud.SpringCloudDeploymentEntity;
import com.microsoft.azure.toolkit.lib.springcloud.config.SpringCloudAppConfig;
import com.microsoft.azure.toolkit.lib.springcloud.config.SpringCloudDeploymentConfig;
import com.microsoft.azure.toolkit.lib.springcloud.model.SpringCloudJavaVersion;
import com.microsoft.azure.toolkit.lib.springcloud.model.SpringCloudPersistentDisk;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Contract;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class SpringCloudAppPanel extends JPanel implements AzureFormPanel<SpringCloudAppConfig> {
    private final Project project;
    private final SpringCloudApp app;

    @Getter
    private JPanel contentPanel;
    private SubscriptionComboBox selectorSubscription;
    private SpringCloudClusterComboBox selectorCluster;
    private SpringCloudAppComboBox selectorApp;
    private HyperlinkLabel txtEndpoint;
    private JButton toggleEndpoint;
    private HyperlinkLabel txtTestEndpoint;
    private JBLabel txtStorage;
    private JButton toggleStorage;
    private JRadioButton useJava8;
    private JRadioButton useJava11;
    private JTextField txtJvmOptions;
    private EnvironmentVariablesTextFieldWithBrowseButton envTable;
    private ComboBox<Integer> numCpu;
    private ComboBox<Integer> numMemory;
    private JSlider numInstance;
    private JBTable tableInstances;

    public SpringCloudAppPanel(@Nonnull SpringCloudApp app, @Nonnull final Project project) {
        super();
        this.app = app;
        this.project = project;
        this.init();
        this.setData(app);
    }

    private void init() {
        this.selectorSubscription.setLabel("Subscription");
        this.selectorCluster.setLabel("Spring Cloud");
        this.selectorApp.setLabel("App");

        this.toggleStorage.addActionListener(e -> toggleStorage("enable".equals(e.getActionCommand()), this.app.entity().getPersistentDisk()));
        this.toggleEndpoint.addActionListener(e -> toggleEndpoint("enable".equals(e.getActionCommand()), this.app.entity().getApplicationUrl()));

        this.numInstance.setValue(1);
        this.numInstance.setMinimum(1);
        this.numInstance.setMaximum(25);

        final DefaultTableModel model = new DefaultTableModel() {
            public boolean isCellEditable(int var1, int var2) {
                return false;
            }
        };
        model.addColumn("App Instances Name");
        model.addColumn("Status");
        model.addColumn("Discover Status");
        tableInstances.setModel(model);
        tableInstances.setRowSelectionAllowed(true);
        tableInstances.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tableInstances.getEmptyText().setText("Loading instances");
    }

    private void setData(@Nonnull SpringCloudApp app) {
        this.selectorCluster.setSubscription(this.app.entity().getSubscriptionId());
        this.selectorApp.setCluster(this.app.getCluster());
        this.selectorSubscription.setValue(new ItemReference<>(this.app.entity().getSubscriptionId(), Subscription::subscriptionId), true);
        this.selectorCluster.setValue(new ItemReference<>(this.app.getCluster().name(), SpringCloudCluster::name), true);
        this.selectorApp.setValue(new ItemReference<>(this.app.name(), SpringCloudApp::name), true);
        final SpringCloudAppEntity appEntity = app.entity();
        final SpringCloudPersistentDisk disk = appEntity.getPersistentDisk();
        this.toggleStorage(Objects.nonNull(disk), disk);
        final String publicUrl = appEntity.getApplicationUrl();
        this.toggleEndpoint(StringUtils.isNotBlank(publicUrl), publicUrl);
        final String testUrl = appEntity.getTestUrl();
        this.txtTestEndpoint.setHyperlinkText(testUrl);
        this.txtTestEndpoint.setHyperlinkTarget(testUrl);
        final SpringCloudDeploymentEntity deployment = app.activeDeployment().entity();
        final boolean useJava11 = StringUtils.equalsIgnoreCase(deployment.getRuntimeVersion(), SpringCloudJavaVersion.JAVA_11);
        this.useJava11.setSelected(useJava11);
        this.useJava8.setSelected(!useJava11);
        this.txtJvmOptions.setText(deployment.getJvmOptions());
        this.numCpu.setItem(deployment.getCpu());
        this.numCpu.setItem(deployment.getMemoryInGB());
        this.numInstance.setValue(deployment.getInstances().size());
        this.envTable.setEnvironmentVariables(deployment.getEnvironmentVariables());
    }

    @Contract("_->_")
    public SpringCloudAppConfig getData(@Nonnull SpringCloudAppConfig appConfig) {
        final SpringCloudDeploymentConfig deploymentConfig = appConfig.getDeployment();
        final String javaVersion = this.useJava11.isSelected() ? SpringCloudJavaVersion.JAVA_11 : SpringCloudJavaVersion.JAVA_8;
        appConfig.setSubscriptionId(this.selectorSubscription.getValue().subscriptionId());
        appConfig.setClusterName(this.selectorCluster.getValue().name());
        appConfig.setAppName(this.selectorApp.getValue().name());
        appConfig.setIsPublic("disable".equals(this.toggleEndpoint.getActionCommand()));
        appConfig.setRuntimeVersion(javaVersion);
        deploymentConfig.setEnablePersistentStorage("disable".equals(this.toggleStorage.getActionCommand()));
        deploymentConfig.setCpu(numCpu.getItem());
        deploymentConfig.setMemoryInGB(numMemory.getItem());
        deploymentConfig.setInstanceCount(numInstance.getValue());
        deploymentConfig.setJvmOptions(Optional.ofNullable(this.txtJvmOptions.getText()).map(String::trim).orElse(""));
        deploymentConfig.setEnvironment(envTable.getEnvironmentVariables());
        return appConfig;
    }

    @Override
    public void setData(SpringCloudAppConfig data) {
        throw new UnsupportedOperationException();
    }

    @Nonnull
    public SpringCloudAppConfig getData() {
        final SpringCloudAppConfig appConfig = SpringCloudAppConfig.builder()
            .deployment(SpringCloudDeploymentConfig.builder().build())
            .build();
        this.getData(appConfig);
        return appConfig;
    }

    private void toggleStorage(boolean enable, @Nullable SpringCloudPersistentDisk disk) {
        if (enable) {
            this.toggleStorage.setActionCommand("disable");
            this.toggleStorage.setText("Disable");
            this.txtStorage.setText(Optional.ofNullable(disk).map(Object::toString).orElse("<save to enable>"));
        } else {
            this.toggleStorage.setActionCommand("enable");
            this.toggleStorage.setText("Enable");
            this.txtStorage.setText("---");
        }
    }

    private void toggleEndpoint(boolean enable, @Nullable String url) {
        if (enable) {
            this.toggleStorage.setActionCommand("disable");
            this.toggleStorage.setText("Disable");
            this.txtStorage.setText(Optional.ofNullable(url).orElse("<save to enable>"));
        } else {
            this.toggleStorage.setActionCommand("enable");
            this.toggleStorage.setText("Enable");
            this.txtStorage.setText("---");
        }
    }

    @Override
    public List<AzureFormInput<?>> getInputs() {
        final AzureFormInput<?>[] inputs = {
            this.selectorSubscription,
            this.selectorCluster,
            this.selectorApp
        };
        return Arrays.asList(inputs);
    }

    private void createUIComponents() {
    }
}
