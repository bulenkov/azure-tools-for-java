/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.intellij.springcloud.properties;

import com.intellij.openapi.project.Project;
import com.microsoft.azure.toolkit.intellij.common.BaseEditor;
import com.microsoft.azure.toolkit.lib.springcloud.SpringCloudApp;
import com.microsoft.azure.toolkit.lib.springcloud.config.SpringCloudAppConfig;
import com.microsoft.azure.toolkit.lib.springcloud.task.DeploySpringCloudAppTask;
import com.microsoft.intellij.util.PluginUtil;
import com.microsoft.tooling.msservices.components.DefaultLoader;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;

public class SpringCloudAppPropertiesEditor extends BaseEditor {
    private static final LineBorder HIGH_LIGHT_BORDER = new LineBorder(Color.decode("0x8a2da5"), 1);
    private static final String DELETE_APP_PROMPT_MESSAGE = "This operation will delete the Spring Cloud App: '%s'.\n" +
        "Are you sure you want to continue?";
    private static final String DELETE_APP_DIRTY_PROMPT_MESSAGE = "This operation will discard your changes and delete the Spring Cloud App: '%s'.\n" +
        "Are you sure you want to continue?";
    private static final String OPERATE_APP_PROMPT_MESSAGE = "This operation will discard your changes.\nAre you sure you want to continue?";

    private static final String ENABLE_PUBLIC_URL_KEY = "enablePublicUrl";
    private static final String ENABLE_PERSISTENT_STORAGE_KEY = "enablePersistentStorage";
    private static final String ENV_TABLE_KEY = "envTable";
    private static final String CPU = "cpu";
    private static final String MEMORY_IN_GB_KEY = "memoryInGB";
    private static final String JVM_OPTIONS_KEY = "jvmOptions";
    private static final String JAVA_VERSION_KEY = "javaVersion";
    private static final String ENABLE_TEXT = "Enable";
    private static final String DISABLE_TEXT = "Disable";
    private static final String DISABLED_TEXT = "Disabled";
    private static final String EMPTY_TEXT = "Empty";
    private static final String DELETING_ACTION = "Deleting";
    private static final String SAVING_ACTION = "Saving";

    private JButton refreshButton;
    private JButton startButton;
    private JButton stopButton;
    private JButton restartButton;
    private JButton deleteButton;
    private JPanel contentPanel;
    private JButton saveButton;
    private SpringCloudAppPanel appPanel;

    @Nonnull
    private final Project project;
    @Nonnull
    private final SpringCloudApp app;

    public SpringCloudAppPropertiesEditor(@Nonnull Project project, @Nonnull SpringCloudApp app) {
        super();
        this.project = project;
        this.app = app;

        this.saveButton.addActionListener(e -> save(this.appPanel.getData()));
        this.refreshButton.addActionListener(e -> this.app.refresh());
        this.deleteButton.addActionListener(e -> this.app.remove());
        this.startButton.addActionListener(e -> this.app.start());
        this.stopButton.addActionListener(e -> this.app.stop());
        this.restartButton.addActionListener(e -> this.app.refresh());
        // TODO: add listener: update view on app updated and close editor on app deleted.
    }

    private void save(SpringCloudAppConfig config) {
        new DeploySpringCloudAppTask(config).execute();
    }

    private void freezeUI() {
        this.saveButton.setEnabled(false);
        this.startButton.setEnabled(false);
        this.stopButton.setEnabled(false);
        this.restartButton.setEnabled(false);
        this.deleteButton.setEnabled(false);
        this.refreshButton.setEnabled(false);
    }

    @NotNull
    @Override
    public JComponent getComponent() {
        return contentPanel;
    }

    @NotNull
    @Override
    public String getName() {
        return this.app.name();
    }

    @Override
    public void dispose() {
    }

    private void closeEditor() {
        DefaultLoader.getUIHelper().closeSpringCloudAppPropertyView(project, this.app.entity().getId());
        PluginUtil.showInfoNotificationProject(project,
            String.format("The editor for app %s is closed.", this.app.name()), "The app " + this.app.name() + " is deleted.");
    }

    private void createUIComponents() {
        this.appPanel = new SpringCloudAppPanel(this.app, this.project);
    }
}
