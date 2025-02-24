/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.intellij.mysql.creation;

import com.microsoft.azure.management.mysql.v2020_01_01.ServerVersion;
import com.microsoft.azure.management.resources.ResourceGroup;
import com.microsoft.azure.management.resources.Subscription;
import com.microsoft.azure.toolkit.intellij.appservice.resourcegroup.ResourceGroupComboBox;
import com.microsoft.azure.toolkit.intellij.appservice.subscription.SubscriptionComboBox;
import com.microsoft.azure.toolkit.intellij.common.AzureFormPanel;
import com.microsoft.azure.toolkit.intellij.common.AzurePasswordFieldInput;
import com.microsoft.azure.toolkit.intellij.common.TextDocumentListenerAdapter;
import com.microsoft.azure.toolkit.intellij.mysql.AdminUsernameTextField;
import com.microsoft.azure.toolkit.intellij.mysql.ConnectionSecurityPanel;
import com.microsoft.azure.toolkit.intellij.mysql.MySQLRegionComboBox;
import com.microsoft.azure.toolkit.intellij.mysql.ServerNameTextField;
import com.microsoft.azure.toolkit.intellij.mysql.VersionComboBox;
import com.microsoft.azure.toolkit.lib.common.form.AzureFormInput;
import com.microsoft.azure.toolkit.lib.mysql.AzureMySQLConfig;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import java.awt.event.ItemEvent;
import java.util.Arrays;
import java.util.List;

public class MySQLCreationAdvancedPanel extends JPanel implements AzureFormPanel<AzureMySQLConfig> {

    private JPanel rootPanel;
    private ConnectionSecurityPanel security;
    @Getter
    private SubscriptionComboBox subscriptionComboBox;
    @Getter
    private ResourceGroupComboBox resourceGroupComboBox;
    @Getter
    private ServerNameTextField serverNameTextField;
    @Getter
    private MySQLRegionComboBox regionComboBox;
    @Getter
    private VersionComboBox versionComboBox;
    @Getter
    private AdminUsernameTextField adminUsernameTextField;
    @Getter
    private JPasswordField passwordField;
    @Getter
    private JPasswordField confirmPasswordField;

    private AzurePasswordFieldInput passwordFieldInput;
    private AzurePasswordFieldInput confirmPasswordFieldInput;

    private AzureMySQLConfig config;

    MySQLCreationAdvancedPanel(AzureMySQLConfig config) {
        super();
        this.config = config;
        $$$setupUI$$$(); // tell IntelliJ to call createUIComponents() here.
        init();
        initListeners();
        setData(config);
    }

    private void init() {
        passwordFieldInput = PasswordUtils.generatePasswordFieldInput(this.passwordField, this.adminUsernameTextField);
        confirmPasswordFieldInput = PasswordUtils.generateConfirmPasswordFieldInput(this.confirmPasswordField, this.passwordField);
    }

    private void initListeners() {
        this.subscriptionComboBox.addItemListener(this::onSubscriptionChanged);
        this.resourceGroupComboBox.addItemListener(this::onResourceGroupChanged);
        this.adminUsernameTextField.getDocument().addDocumentListener(generateAdminUsernameListener());
        this.security.getAllowAccessFromAzureServicesCheckBox().addItemListener(this::onSecurityAllowAccessFromAzureServicesCheckBoxChanged);
        this.security.getAllowAccessFromLocalMachineCheckBox().addItemListener(this::onSecurityAllowAccessFromLocalMachineCheckBoxChanged);
    }

    private void onSubscriptionChanged(final ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED && e.getItem() instanceof Subscription) {
            final Subscription subscription = (Subscription) e.getItem();
            this.resourceGroupComboBox.setSubscription(subscription);
            this.serverNameTextField.setSubscription(subscription);
            this.regionComboBox.setSubscription(subscription);
        }
    }

    private void onResourceGroupChanged(final ItemEvent e) {
        if (e.getStateChange() == ItemEvent.SELECTED && e.getItem() instanceof ResourceGroup) {
            final ResourceGroup resourceGroup = (ResourceGroup) e.getItem();
            this.serverNameTextField.setResourceGroup(resourceGroup);
        }
    }

    private DocumentListener generateAdminUsernameListener() {
        return new TextDocumentListenerAdapter() {
            @Override
            public void onDocumentChanged() {
                if (!adminUsernameTextField.isValueInitialized()) {
                    adminUsernameTextField.setValueInitialized(true);
                }
            }
        };
    }

    private void onSecurityAllowAccessFromAzureServicesCheckBoxChanged(final ItemEvent e) {
        config.setAllowAccessFromAzureServices(e.getStateChange() == ItemEvent.SELECTED);
    }

    private void onSecurityAllowAccessFromLocalMachineCheckBoxChanged(final ItemEvent e) {
        config.setAllowAccessFromLocalMachine(e.getStateChange() == ItemEvent.SELECTED);
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        rootPanel.setVisible(visible);
    }

    @Override
    public AzureMySQLConfig getData() {
        config.setServerName(serverNameTextField.getText());
        config.setAdminUsername(adminUsernameTextField.getText());
        config.setPassword(passwordField.getPassword());
        config.setConfirmPassword(confirmPasswordField.getPassword());
        config.setSubscription(subscriptionComboBox.getValue());
        config.setResourceGroup(resourceGroupComboBox.getValue());
        config.setRegion(regionComboBox.getValue());
        if (StringUtils.isNotBlank(versionComboBox.getValue())) {
            config.setVersion(ServerVersion.fromString(versionComboBox.getValue()));
        }
        config.setAllowAccessFromAzureServices(security.getAllowAccessFromAzureServicesCheckBox().isSelected());
        config.setAllowAccessFromLocalMachine(security.getAllowAccessFromLocalMachineCheckBox().isSelected());
        return config;
    }

    @Override
    public void setData(AzureMySQLConfig data) {
        if (StringUtils.isNotBlank(config.getServerName())) {
            serverNameTextField.setText(config.getServerName());
        }
        if (StringUtils.isNotBlank(config.getAdminUsername())) {
            adminUsernameTextField.setText(config.getAdminUsername());
        }
        if (config.getPassword() != null) {
            passwordField.setText(String.valueOf(config.getPassword()));
        }
        if (config.getConfirmPassword() != null) {
            confirmPasswordField.setText(String.valueOf(config.getConfirmPassword()));
        }
        if (config.getSubscription() != null) {
            subscriptionComboBox.setValue(config.getSubscription());
        }
        if (config.getResourceGroup() != null) {
            resourceGroupComboBox.setValue(config.getResourceGroup());
        }
        if (config.getRegion() != null) {
            regionComboBox.setValue(config.getRegion());
        }
        if (config.getVersion() != null) {
            versionComboBox.setValue(config.getVersion().toString());
        }
        security.getAllowAccessFromAzureServicesCheckBox().setSelected(config.isAllowAccessFromAzureServices());
        security.getAllowAccessFromLocalMachineCheckBox().setSelected(config.isAllowAccessFromLocalMachine());
    }

    @Override
    public List<AzureFormInput<?>> getInputs() {
        final AzureFormInput<?>[] inputs = {
            this.serverNameTextField,
            this.adminUsernameTextField,
            this.subscriptionComboBox,
            this.resourceGroupComboBox,
            this.regionComboBox,
            this.versionComboBox,
            this.passwordFieldInput,
            this.confirmPasswordFieldInput
        };
        return Arrays.asList(inputs);
    }

}
