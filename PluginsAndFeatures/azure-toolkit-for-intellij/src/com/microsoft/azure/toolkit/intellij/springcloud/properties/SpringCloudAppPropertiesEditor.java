/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.intellij.springcloud.properties;

import com.google.common.collect.Maps;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.HideableDecorator;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.PopupMenuListenerAdapter;
import com.intellij.ui.table.JBTable;
import com.microsoft.azure.management.appplatform.v2020_07_01.AppResourceProperties;
import com.microsoft.azure.management.appplatform.v2020_07_01.DeploymentResourceProperties;
import com.microsoft.azure.management.appplatform.v2020_07_01.DeploymentResourceStatus;
import com.microsoft.azure.management.appplatform.v2020_07_01.DeploymentSettings;
import com.microsoft.azure.management.appplatform.v2020_07_01.PersistentDisk;
import com.microsoft.azure.management.appplatform.v2020_07_01.RuntimeVersion;
import com.microsoft.azure.management.appplatform.v2020_07_01.implementation.AppResourceInner;
import com.microsoft.azure.management.appplatform.v2020_07_01.implementation.DeploymentResourceInner;
import com.microsoft.azure.management.resources.Subscription;
import com.microsoft.azure.toolkit.intellij.common.BaseEditor;
import com.microsoft.azure.toolkit.intellij.common.EnvironmentVariablesTextFieldWithBrowseButton;
import com.microsoft.azure.toolkit.intellij.springcloud.streaminglog.SpringCloudStreamingLogManager;
import com.microsoft.azure.toolkit.lib.common.cache.Cacheable;
import com.microsoft.azure.toolkit.lib.common.exception.AzureExecutionException;
import com.microsoft.azure.toolkit.lib.common.task.AzureTask;
import com.microsoft.azure.toolkit.lib.common.task.AzureTaskManager;
import com.microsoft.azure.toolkit.lib.common.utils.Utils;
import com.microsoft.azuretools.core.mvp.model.AzureMvpModel;
import com.microsoft.azuretools.core.mvp.model.springcloud.AzureSpringCloudMvpModel;
import com.microsoft.azuretools.core.mvp.model.springcloud.SpringCloudIdHelper;
import com.microsoft.azuretools.telemetry.TelemetryConstants;
import com.microsoft.azuretools.telemetrywrapper.EventUtil;
import com.microsoft.intellij.helpers.ConsoleViewStatus;
import com.microsoft.intellij.util.PluginUtil;
import com.microsoft.tooling.msservices.components.DefaultLoader;
import com.microsoft.tooling.msservices.serviceexplorer.azure.springcloud.SpringCloudMonitorUtil;
import com.microsoft.tooling.msservices.serviceexplorer.azure.springcloud.SpringCloudStateManager;
import io.reactivex.rxjava3.disposables.Disposable;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import rx.Observable;
import rx.schedulers.Schedulers;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.PopupMenuEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    private JButton triggerPublicButton;
    private JComboBox javaVersionCombo;
    private JComboBox cpuCombo;
    private JTextField jvmOpsTextField;
    private JButton refreshButton;
    private JButton startButton;
    private JButton stopButton;
    private JButton restartButton;
    private JButton deleteButton;
    private JLabel lblAppName;
    private JLabel lblPublic;
    private JPanel instanceDetailHolder;
    private JScrollPane pnlScroll;
    private JPanel statusPanel;
    private JButton triggerPersistentButton;
    private JComboBox memCombo;
    private JPanel mainPanel;
    private JPanel publicPanel;
    private JButton saveButton;
    private JBTable instanceTable;
    private HyperlinkLabel testUrlLink;
    private HyperlinkLabel publicUrlHyperLink;
    private JLabel subsLabel;
    private JLabel resourceGroupLabel;
    private JLabel clusterLabel;
    private JLabel appNameLabel;
    private JLabel persistentLabel;
    private EnvironmentVariablesTextFieldWithBrowseButton envTable;
    private JPanel pnlOptions;
    private JPanel pnlDetails;
    private JPanel pnlInstances;
    private JSeparator split;
    private JLabel lblInstances;
    private JLabel lblPersistentStorage;
    private JLabel lblEnv;
    private HideableDecorator instancePanelDecorator;

    private SpringCloudAppViewModel viewModel;
    private Project project;
    private AppResourceInner appResourceInner;
    private DeploymentResourceInner deploymentResourceInner;
    private String clusterId;
    private String appId;
    private String appName;
    private DefaultTableModel instancesTableModel;
    private final Map<JComponent, Border> borderMap = new HashMap<>();
    private final Disposable rxSubscription;

    public SpringCloudAppPropertiesEditor(Project project, String clusterId, String appId) {
        this.project = project;
        this.clusterId = clusterId;
        this.appId = appId;

        this.appName = SpringCloudIdHelper.getAppName(appId);
        instancesTableModel = new DefaultTableModel() {
            public boolean isCellEditable(int var1, int var2) {
                return false;
            }
        };
        instancesTableModel.addColumn("App Instances Name");
        instancesTableModel.addColumn("Status");
        instancesTableModel.addColumn("Discover Status");
        instanceTable.setModel(instancesTableModel);
        instanceTable.setRowSelectionAllowed(true);
        instanceTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        instanceTable.getEmptyText().setText("Loading instances status");
        this.saveButton.addActionListener(e -> {
            wrapperOperations(TelemetryConstants.SAVE_SPRING_CLOUD_APP, SAVING_ACTION, project, (changes) -> {
                if (changes.isEmpty()) {
                    PluginUtil.showInfoNotificationProject(project, "No actions performed", "You have no changes to apply.");
                    return;
                }
                save(changes);
            });

        });
        // Remove button icon as there will be IllegalArgumentException for disabled icons in IntelliJ 2020.2
        // this.saveButton.setIcon(UIHelperImpl.loadIcon("storagesaveas.png"));

        this.refreshButton.addActionListener(e -> {
            wrapperOperations(TelemetryConstants.REFRESH_SPRING_CLOUD_APP, "Refreshing", project, (changes) -> {
                // DO nothing
            });
        });
        // this.refreshButton.setIcon(UIHelperImpl.loadIcon("refresh.png"));

        this.deleteButton.addActionListener(e -> {
            wrapperOperations(TelemetryConstants.DELETE_SPRING_CLOUD_APP, DELETING_ACTION, project, (changes) -> {
                try {
                    AzureSpringCloudMvpModel.deleteApp(appId).await();
                    monitorStatus(appId, deploymentResourceInner);
                } catch (IOException | InterruptedException ex) {
                    PluginUtil.showErrorNotificationProject(project,
                                                            String.format("Cannot delete app '%s' due to error.", this.appName), ex.getMessage());
                }
            });
        });
        // this.deleteButton.setIcon(UIHelperImpl.loadIcon("Delete.png"));

        this.startButton.addActionListener(e -> {
            wrapperOperations(TelemetryConstants.START_SPRING_CLOUD_APP, "Starting", project, (changes) -> {
                try {
                    AzureSpringCloudMvpModel.startApp(appId, appResourceInner.properties().activeDeploymentName()).await();
                    monitorStatus(appId, deploymentResourceInner);
                } catch (IOException | InterruptedException ex) {
                    PluginUtil.showErrorNotificationProject(project, String.format("Cannot start app '%s' due to error.", this.appName), ex.getMessage());
                }
            });
        });
        // this.startButton.setIcon(UIHelperImpl.loadIcon("Start.png"));

        this.stopButton.addActionListener(e -> {
            wrapperOperations(TelemetryConstants.STOP_SPRING_CLOUD_APP, "Stopping", project, (changes) -> {
                try {
                    AzureSpringCloudMvpModel.stopApp(appId, appResourceInner.properties().activeDeploymentName()).await();
                    monitorStatus(appId, deploymentResourceInner);
                } catch (IOException | InterruptedException ex) {
                    PluginUtil.showErrorNotificationProject(project, String.format("Cannot stop app '%s' due to error.", this.appName), ex.getMessage());
                }
            });
        });
        // this.stopButton.setIcon(UIHelperImpl.loadIcon("Stop.png"));

        this.restartButton.addActionListener(e -> {
            wrapperOperations(TelemetryConstants.RESTART_SPRING_CLOUD_APP, "Restarting", project, (changes) -> {
                try {
                    AzureSpringCloudMvpModel.restartApp(appId, appResourceInner.properties().activeDeploymentName()).await();
                    monitorStatus(appId, deploymentResourceInner);
                } catch (IOException | InterruptedException ex) {
                    PluginUtil.showErrorNotificationProject(project, String.format("Cannot restart app '%s' due to error.", this.appName), ex.getMessage());
                }
            });
        });
        // this.restartButton.setIcon(UIHelperImpl.loadIcon("azure-springcloud-app-restart.png"));
        jvmOpsTextField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent documentEvent) {
                syncSaveStatus();
            }
        });
        cpuCombo.addActionListener(e -> {
            syncSaveStatus();
        });
        memCombo.addActionListener(e -> {
            syncSaveStatus();
        });
        javaVersionCombo.addActionListener(e -> {
            syncSaveStatus();
        });

        this.triggerPublicButton.addActionListener(e -> {
            this.triggerPublicUrl();
            syncSaveStatus();
        });
        // this.triggerPublicButton.setIcon(UIHelperImpl.loadIcon("azure-springcloud-app-assign.png"));
        this.triggerPersistentButton.addActionListener(e -> {
            this.triggerPersistentStorage();
            syncSaveStatus();
        });

        this.envTable.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent documentEvent) {
                syncSaveStatus();
            }
        });
        this.envTable.getTextField().setEditable(false);
        this.lblEnv.setLabelFor(envTable.getTextField());

        instancePanelDecorator = new HideableDecorator(instanceDetailHolder, "Instances", false);
        instancePanelDecorator.setContentComponent(pnlScroll);
        instancePanelDecorator.setOn(true);

        IntStream.range(1, 5).forEach(cpuCombo::addItem);
        IntStream.range(1, 9).forEach(memCombo::addItem);

        Arrays.asList("Java_8", "Java_11").forEach(javaVersionCombo::addItem);

        freezeUI();
        this.cpuCombo.setEditable(false);
        this.memCombo.setEditable(false);
        this.javaVersionCombo.setEditable(false);

        initUI();

        this.rxSubscription = SpringCloudStateManager.INSTANCE.subscribeSpringAppEvent(event -> {
            if (event.isUpdate()) {
                this.prepareViewModel(event.getAppInner(), event.getDeploymentInner());
            } else if (event.isDelete()) {
                closeEditor();
            }
        }, appId);
    }

    @NotNull
    @Override
    public JComponent getComponent() {
        return mainPanel;
    }

    @NotNull
    @Override
    public String getName() {
        return this.viewModel == null ? "Untitled" : this.viewModel.getAppName();
    }

    @Override
    public void dispose() {
        closeRxSubscription(rxSubscription);
    }

    private static void closeRxSubscription(final Disposable disposable) {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }

    private static void monitorStatus(String appId, DeploymentResourceInner deploymentResourceInner) throws IOException, InterruptedException {
        SpringCloudMonitorUtil.awaitAndMonitoringStatus(appId,
                                                             deploymentResourceInner == null ? null : deploymentResourceInner.properties().status());
    }

    private void wrapperOperations(String operation, String actionName, Project project,
                                   Consumer<Map<String, Object>> action) {
        if (this.viewModel == null) {
            return;
        }
        // TODO: record operation in telemetry
        Map<String, Object> changes;
        try {
            changes = getModifiedDataMap();
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException ex) {
            PluginUtil.showErrorNotificationProject(project, "Cannot get model state", ex.getMessage());
            return;
        }
        String promptMessage;
        if (StringUtils.equals(actionName, DELETING_ACTION)) {
            promptMessage = String.format(changes.isEmpty() ? DELETE_APP_PROMPT_MESSAGE : DELETE_APP_DIRTY_PROMPT_MESSAGE, this.appName);
        } else {
            promptMessage = changes.isEmpty() ? "" : String.format(OPERATE_APP_PROMPT_MESSAGE, actionName, this.appName);
        }
        if (promptMessage.isEmpty() || StringUtils.equals(actionName, SAVING_ACTION)
                || DefaultLoader.getUIHelper().showConfirmation(this.mainPanel,
                                                                promptMessage,
                                                                "Azure Explorer",
                                                                new String[]{"Yes", "No"},
                                                                null)) {
            freezeUI();
            final String message = String.format("%s app (%s)", actionName, this.appName);
            AzureTaskManager.getInstance().runInBackground(new AzureTask(null, message, false, () -> {
                EventUtil.executeWithLog(TelemetryConstants.SPRING_CLOUD, operation, logOperation -> {
                    logOperation.trackProperty(TelemetryConstants.SUBSCRIPTIONID, Utils.getSubscriptionId(clusterId));
                    action.accept(changes);
                });
                refreshData();
            }));
        }
    }

    private void initUI() {
        // Todo: find better way to align UI labels
        AzureTaskManager.getInstance().runLater(() -> {
            Dimension size = lblInstances.getPreferredSize();
            size.setSize(lblPersistentStorage.getWidth(), size.getHeight());
            lblInstances.setPreferredSize(size);
        });

        final JBPopupMenu instanceTablePopupMenu = new JBPopupMenu();
        final JBMenuItem startStreamingLogsItem = new JBMenuItem("Start Streaming Logs");
        startStreamingLogsItem.addActionListener(event -> {
            final int row = instanceTable.getSelectedRow();
            if (row >= 0) {
                final String instanceName = (String) instancesTableModel.getValueAt(row, 0);
                EventUtil.executeWithLog(
                    TelemetryConstants.SPRING_CLOUD,
                    TelemetryConstants.START_STREAMING_LOG_SPRING_CLOUD_APP, operation -> {
                        SpringCloudStreamingLogManager.getInstance().showStreamingLog(project, appId, instanceName);
                    });
            }
        });
        final JBMenuItem stopStreamingLogsItem = new JBMenuItem("Stop Streaming Logs");
        stopStreamingLogsItem.addActionListener(event -> {
            final int row = instanceTable.getSelectedRow();
            if (row >= 0) {
                final String instanceName = (String) instancesTableModel.getValueAt(row, 0);
                EventUtil.executeWithLog(
                    TelemetryConstants.SPRING_CLOUD,
                    TelemetryConstants.STOP_STREAMING_LOG_SPRING_CLOUD_APP, operation -> {
                        SpringCloudStreamingLogManager.getInstance().closeStreamingLog(instanceName);
                    });
            }
        });
        instanceTablePopupMenu.add(startStreamingLogsItem);
        instanceTablePopupMenu.add(stopStreamingLogsItem);
        instanceTablePopupMenu.addPopupMenuListener(new PopupMenuListenerAdapter() {
            @Override
            public void popupMenuWillBecomeVisible(final PopupMenuEvent e) {
                final int row = instanceTable.getSelectedRow();
                if (row >= 0) {
                    final String instanceName = (String) instancesTableModel.getValueAt(row, 0);
                    final ConsoleViewStatus status =
                            SpringCloudStreamingLogManager.getInstance().getConsoleViewStatus(instanceName);
                    stopStreamingLogsItem.setEnabled(status == ConsoleViewStatus.ACTIVE);
                    startStreamingLogsItem.setEnabled(status == ConsoleViewStatus.STOPPED);
                } else {
                    DefaultLoader.getIdeHelper().invokeLater(() -> instanceTablePopupMenu.setVisible(false));
                }
            }
        });

        // Select row with right click
        instanceTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(final MouseEvent mouseEvent) {
                if (SwingUtilities.isRightMouseButton(mouseEvent)) {
                    final int row = instanceTable.rowAtPoint(mouseEvent.getPoint());
                    if (row >= 0) {
                        instanceTable.clearSelection();
                        instanceTable.addRowSelectionInterval(row, row);
                        instanceTablePopupMenu.show(instanceTable, mouseEvent.getX(), mouseEvent.getY());
                    }
                }
            }
        });
    }

    private void freezeUI() {
        this.triggerPersistentButton.setEnabled(false);
        this.triggerPublicButton.setEnabled(false);
        this.javaVersionCombo.setEnabled(false);
        this.cpuCombo.setEnabled(false);
        this.memCombo.setEnabled(false);
        this.jvmOpsTextField.setEnabled(false);
        this.saveButton.setEnabled(false);
        this.startButton.setEnabled(false);
        this.stopButton.setEnabled(false);
        this.restartButton.setEnabled(false);
        this.deleteButton.setEnabled(false);
        this.refreshButton.setEnabled(false);
        this.envTable.setEditable(false);

        // clear highlight mark
        synchronized (borderMap) {
            for (Map.Entry<JComponent, Border> entry : borderMap.entrySet()) {
                entry.getKey().setBorder(entry.getValue());
            }
            borderMap.clear();
        }
        resetNormalText(this.persistentLabel);
        resetNormalText(this.publicUrlHyperLink);
    }

    private void restoreUI() {
        this.triggerPersistentButton.setEnabled(true);
        this.triggerPublicButton.setEnabled(true);
        this.cpuCombo.setEnabled(true);
        this.memCombo.setEnabled(true);
        this.javaVersionCombo.setEnabled(true);
        this.jvmOpsTextField.setEnabled(true);
        this.envTable.setEditable(true);
    }

    private void syncSaveStatus() {
        if (this.viewModel == null) {
            // we are pending to fill data
            return;
        }
        try {
            Map<String, Object> map = getModifiedDataMap();
            saveButton.setEnabled(MapUtils.isNotEmpty(map));
            updateBorder(this.cpuCombo, map.containsKey(CPU));
            updateBorder(this.memCombo, map.containsKey(MEMORY_IN_GB_KEY));
            updateBorder(this.jvmOpsTextField, map.containsKey(JVM_OPTIONS_KEY));
            updateBorder(this.javaVersionCombo, map.containsKey(JAVA_VERSION_KEY));
            updateBorder(this.envTable.getTextField(), map.containsKey(ENV_TABLE_KEY));
            if (map.containsKey(ENABLE_PERSISTENT_STORAGE_KEY)) {
                setItalicText(this.persistentLabel);
            } else {
                resetNormalText(this.persistentLabel);
            }
            if (map.containsKey(ENABLE_PUBLIC_URL_KEY)) {
                setItalicText(this.publicUrlHyperLink);
            } else {
                resetNormalText(this.publicUrlHyperLink);
            }
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            PluginUtil.showErrorNotificationProject(project, "Cannot get property through reflection", e.getMessage());
        }
    }

    private void updateBorder(final JComponent component, boolean highlight) {
        if (highlight) {
            addHighLight(component);
        } else {
            removeHighLight(component);
        }
    }

    private void removeHighLight(final JComponent component) {
        synchronized (borderMap) {
            if (borderMap.containsKey(component)) {
                Border border = borderMap.remove(component);
                component.setBorder(border);
            }
        }
    }

    private void addHighLight(final JComponent component) {
        synchronized (borderMap) {
            if (!borderMap.containsKey(component)) {
                borderMap.put(component, component.getBorder());
                component.setBorder(HIGH_LIGHT_BORDER);
            }
        }
    }

    private void triggerPersistentStorage() {
        final String text = this.triggerPersistentButton.getText();

        boolean enablePersist = StringUtils.equalsIgnoreCase(text, ENABLE_TEXT);
        if (enablePersist) {
            if (viewModel.isEnablePersistentStorage()) {
                renderPersistent(this.viewModel);
            } else {
                this.persistentLabel.setText("Persistent storage will be updated after you save the settings.");
            }
        } else {
            this.persistentLabel.setText(DISABLED_TEXT);
        }

        this.triggerPersistentButton.setText(enablePersist ? DISABLE_TEXT : ENABLE_TEXT);
    }

    private void triggerPublicUrl() {
        final String text = this.triggerPublicButton.getText();
        boolean updatePublicTrue = StringUtils.equalsIgnoreCase(text, ENABLE_TEXT);
        setPublicUrl(updatePublicTrue, this.viewModel.getPublicUrl(), "URL will be updated after you save the"
                + " settings.");
    }

    private void refreshData() {
        viewModel = null;
        Observable.fromCallable(() -> {
            AppResourceInner app = AzureSpringCloudMvpModel.getAppById(appId);
            if (app == null) {
                closeEditor();
                return Pair.of((AppResourceInner) null, (DeploymentResourceInner) null);
            }
            DeploymentResourceInner deploy = StringUtils.isNotEmpty(app.properties().activeDeploymentName())
                                             ? AzureSpringCloudMvpModel.getAppDeployment(appId, app.properties().activeDeploymentName()) : null;
            getTestKey(clusterId, true);
            return Pair.of(app, deploy);
        }).subscribeOn(Schedulers.io()).subscribe(pair -> AzureTaskManager.getInstance().runLater(
            () -> this.prepareViewModel(pair.getLeft(), pair.getRight())));
    }

    private Map<String, Object> getModifiedDataMap() throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        if (viewModel == null) {
            return null;
        }
        Map<String, Object> map = new HashMap<>();
        compareModel(viewModel, JVM_OPTIONS_KEY, this.jvmOpsTextField, map);
        compareModelComboBinding(viewModel, CPU, this.cpuCombo, map);
        compareModelComboBinding(viewModel, MEMORY_IN_GB_KEY, this.memCombo, map);
        compareModelTextComboBinding(viewModel, JAVA_VERSION_KEY, this.javaVersionCombo, map);

        final String text = this.triggerPublicButton.getText();
        boolean currentEnableFlag = !StringUtils.equalsIgnoreCase(text, ENABLE_TEXT);
        if (viewModel.isEnablePublicUrl() != currentEnableFlag) {
            map.put(ENABLE_PUBLIC_URL_KEY, currentEnableFlag);
        }

        boolean currentEnablePersist = !StringUtils.equalsIgnoreCase(this.triggerPersistentButton.getText(), ENABLE_TEXT);
        if (viewModel.isEnablePersistentStorage() != currentEnablePersist) {
            map.put(ENABLE_PERSISTENT_STORAGE_KEY, currentEnablePersist);
        }

        Map<String, String> oldEnvironment = viewModel.getEnvironment();
        Map<String, String> newEnvironment = this.envTable.getEnvironmentVariables();
        // Maps.difference cannot handling null
        if (!Maps.difference(oldEnvironment == null ? new HashMap<>() : oldEnvironment, newEnvironment).areEqual()) {
            map.put(ENV_TABLE_KEY, newEnvironment);
        }
        return map;
    }

    private void save(Map<String, Object> map) {
        try {
            DeploymentResourceProperties deploymentResourceProperties = deploymentResourceInner.properties();
            final DeploymentSettings deploymentSettings = deploymentResourceProperties.deploymentSettings();
            deploymentResourceProperties = deploymentResourceProperties.withDeploymentSettings(deploymentSettings);
            if (map.containsKey(CPU)) {
                deploymentSettings.withCpu((Integer) map.get(CPU));
            }
            if (map.containsKey(MEMORY_IN_GB_KEY)) {
                deploymentSettings.withMemoryInGB((Integer) map.get(MEMORY_IN_GB_KEY));
            }

            if (map.containsKey(JVM_OPTIONS_KEY)) {
                deploymentSettings.withJvmOptions((String) map.get(JVM_OPTIONS_KEY));
            }

            if (map.containsKey(JAVA_VERSION_KEY)) {
                deploymentSettings.withRuntimeVersion(RuntimeVersion.fromString((String) map.get(JAVA_VERSION_KEY)));
            }

            if (map.containsKey(ENV_TABLE_KEY)) {
                deploymentSettings.withEnvironmentVariables((Map<String, String>) map.get(ENV_TABLE_KEY));
            }

            AppResourceProperties appUpdate = null;
            if (map.containsKey(ENABLE_PUBLIC_URL_KEY)) {
                if (appUpdate == null) {
                    appUpdate = new AppResourceProperties();
                }
                appUpdate.withPublicProperty((Boolean) map.get(ENABLE_PUBLIC_URL_KEY));
            }

            if (map.containsKey(ENABLE_PERSISTENT_STORAGE_KEY)) {
                if (appUpdate == null) {
                    appUpdate = new AppResourceProperties();
                }
                boolean isEnablePersist = (Boolean) map.get(ENABLE_PERSISTENT_STORAGE_KEY);
                PersistentDisk pd = new PersistentDisk();
                pd.withMountPath("/persistent");
                pd.withSizeInGB(isEnablePersist ? 50 : 0);
                appUpdate.withPersistentDisk(pd);
            }
            if (appUpdate != null) {
                AzureSpringCloudMvpModel.updateAppProperties(appId, appUpdate);
            }
            deploymentResourceInner = AzureSpringCloudMvpModel
                    .updateProperties(appId, appResourceInner.properties().activeDeploymentName(), deploymentResourceProperties);

            AzureTaskManager.getInstance().runLater(() ->
                    PluginUtil.showInfoNotificationProject(project, "Update successfully", "Update app configuration "
                            + "successfully"));
            refreshData();

        } catch (Exception e) {
            AzureTaskManager.getInstance().runLater(() -> PluginUtil.displayErrorDialog("Failed to update app configuration", e.getMessage()));
        }
    }

    private void compareModelTextComboBinding(Object model, String propertyName, JComboBox comboBox, Map<String, Object> deltaMap)
            throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        String value = (String) PropertyUtils.getSimpleProperty(model, propertyName);
        String userInput = (String) comboBox.getModel().getSelectedItem();
        if (!Objects.equals(value, userInput)) {
            deltaMap.put(propertyName, userInput);
        }
    }

    private void compareModelComboBinding(Object model, String propertyName, JComboBox comboBox, Map<String, Object> deltaMap)
            throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        Integer value = (Integer) PropertyUtils.getSimpleProperty(model, propertyName);
        // userInput may be integer or string
        String userInput = Objects.toString(comboBox.getModel().getSelectedItem(), null);
        if (StringUtils.isNotEmpty(userInput) && !Objects.equals(value, Integer.parseInt(userInput))) {
            deltaMap.put(propertyName, Integer.parseInt(userInput));
        }
    }

    private void compareModel(Object model, String propertyName, JTextField textField, Map<String, Object> deltaMap)
            throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        String text = Objects.toString(PropertyUtils.getSimpleProperty(model, propertyName), "");
        if (StringUtils.equals(text, textField.getText())) {
            return;
        }
        deltaMap.put(propertyName, textField.getText());
    }

    private void setPublicUrl(boolean isPublic, String publicUrl, String hintMessage) {
        this.triggerPublicButton.setText(isPublic ? DISABLE_TEXT : ENABLE_TEXT);
        if (isPublic) {
            if (StringUtils.isNotEmpty(publicUrl)) {
                publicUrlHyperLink.setHyperlinkText(publicUrl);
                publicUrlHyperLink.setHyperlinkTarget(publicUrl);
            } else if (StringUtils.isNotEmpty(hintMessage)) {
                publicUrlHyperLink.setText(hintMessage);
                publicUrlHyperLink.setHyperlinkTarget("");
            }
        } else {
            publicUrlHyperLink.setText(DISABLED_TEXT);
            publicUrlHyperLink.setHyperlinkTarget("");
        }

    }

    private static void resetNormalText(Component comp) {
        if (comp != null && comp.getFont() != null) {
            Font font = comp.getFont();
            comp.setFont(new Font(font.getName(), Font.PLAIN, font.getSize()));
        }
    }

    private static void setItalicText(Component comp) {
        if (comp != null && comp.getFont() != null) {
            Font font = comp.getFont();
            comp.setFont(new Font(font.getName(), Font.ITALIC, font.getSize()));
        }
    }

    private void renderPersistent(SpringCloudAppViewModel model) {
        this.persistentLabel.setText(String.format("%s (%sG of %dG used)",
                                                   model.getPersistentMountPath(),
                                                   Objects.toString(model.getUsedStorageInGB(), "0"),
                                                   model.getTotalStorageInGB()));
    }

    private void handleTextComboBinding(Object model, String propertyName, JComboBox comboBox)
            throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        String text = Objects.toString(PropertyUtils.getSimpleProperty(model, propertyName), null);
        comboBox.getModel().setSelectedItem(text);
    }

    private void handleNumberComboBinding(Object model, String propertyName, JComboBox comboBox)
            throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        Integer value = (Integer) PropertyUtils.getSimpleProperty(model, propertyName);
        comboBox.getModel().setSelectedItem(value);
    }

    private void handleTextDataBinding(Object model, String propertyName, JTextField textField)
            throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        String text = Objects.toString(PropertyUtils.getSimpleProperty(model, propertyName), null);
        textField.setText(text);
    }

    private void prepareViewModel(AppResourceInner app, DeploymentResourceInner deploy) {
        try {
            if (app == null) {
                return;
            }
            this.appResourceInner = app;
            this.deploymentResourceInner = deploy;
            SpringCloudAppViewModel targetViewModel = new SpringCloudAppViewModel();
            String clusterKey = getTestKey(clusterId);
            targetViewModel.setTestUrl(AzureSpringCloudMvpModel.getTestEndpointForApp(clusterKey, app.name()));

            // persistent storage
            if (app.properties().persistentDisk() != null && app.properties().persistentDisk().sizeInGB().intValue() > 0) {
                targetViewModel.setEnablePersistentStorage(true);
                targetViewModel.setUsedStorageInGB(app.properties().persistentDisk().usedInGB());
                targetViewModel.setTotalStorageInGB(app.properties().persistentDisk().sizeInGB());
                targetViewModel.setPersistentMountPath(app.properties().persistentDisk().mountPath());
            } else {
                targetViewModel.setEnablePersistentStorage(false);
            }

            Subscription subs = AzureMvpModel.getInstance().getSubscriptionById(SpringCloudIdHelper.getSubscriptionId(this.appId));
            targetViewModel.setSubscriptionName(subs == null ? null : subs.displayName());
            targetViewModel.setResourceGroup(SpringCloudIdHelper.getResourceGroup(this.appId));
            if (deploy != null) {
                DeploymentSettings settings = deploy.properties().deploymentSettings();
                targetViewModel.setJavaVersion(Objects.toString(settings.runtimeVersion(), ""));
                targetViewModel.setJvmOptions(Objects.toString(settings.jvmOptions(), ""));
                targetViewModel.setCpu(settings.cpu());
                targetViewModel.setMemoryInGB(settings.memoryInGB());
                if (deploy.properties().instances() != null) {
                    targetViewModel.setDownInstanceCount((int) deploy.properties().instances().stream().filter(
                        t -> StringUtils.equalsIgnoreCase(t.discoveryStatus(), "DOWN")).count());
                    targetViewModel.setUpInstanceCount(deploy.properties().instances().size() - targetViewModel.getDownInstanceCount());
                    targetViewModel.setInstance(deploymentResourceInner.properties().instances().stream().map(t -> {
                        SpringCloudAppInstanceViewModel instanceViewModel = new SpringCloudAppInstanceViewModel();
                        instanceViewModel.setName(t.name());
                        instanceViewModel.setStatus(t.status());
                        instanceViewModel.setDiscoveryStatus(t.discoveryStatus());
                        return instanceViewModel;
                    }).collect(Collectors.toList()));
                } else {
                    targetViewModel.setUpInstanceCount(0);
                    targetViewModel.setDownInstanceCount(0);
                }
                // env variable
                targetViewModel.setEnvironment(settings.environmentVariables() == null ? new HashMap<>() :
                                               settings.environmentVariables());
            } else {
                targetViewModel.setUpInstanceCount(0);
                targetViewModel.setDownInstanceCount(0);
            }
            // public url
            targetViewModel.setEnablePublicUrl(app.properties().publicProperty());
            if (targetViewModel.isEnablePublicUrl()) {
                targetViewModel.setPublicUrl(app.properties().url());
            }

            targetViewModel.setClusterName(SpringCloudIdHelper.getClusterName(this.appId));
            targetViewModel.setAppName(app.name());

            // button enable
            DeploymentResourceStatus status = deploy == null ? DeploymentResourceStatus.UNKNOWN : deploy.properties().status();
            boolean stopped = DeploymentResourceStatus.STOPPED.equals(status);
            boolean unknown = DeploymentResourceStatus.UNKNOWN.equals(status);
            targetViewModel.setCanStop(!stopped && !unknown);
            targetViewModel.setCanStart(stopped);
            targetViewModel.setCanReStart(!stopped && !unknown);
            // status
            targetViewModel.setStatus(status.toString());
            this.updateModel(targetViewModel);
        } catch (AzureExecutionException e) {
            AzureTaskManager.getInstance().runLater(() -> {
                PluginUtil.showErrorNotificationProject(project, "Cannot binding data to Spring Cloud property view.", e.getMessage());
            });
        }
    }

    @Cacheable(value = "springcloud|cluster.testEndpoint", key = "$clusterId", condition = "!(force&&force[0])")
    private String getTestKey(String clusterId, boolean... force) {
        return AzureSpringCloudMvpModel.getPrimaryTestEndpoint(clusterId);
    }

    private void updateModel(SpringCloudAppViewModel newModel) throws AzureExecutionException {
        try {
            this.viewModel = null;
            this.subsLabel.setText(newModel.getSubscriptionName());
            this.resourceGroupLabel.setText(newModel.getResourceGroup());
            this.clusterLabel.setText(newModel.getClusterName());
            this.appNameLabel.setText(newModel.getAppName());
            setPublicUrl(newModel.isEnablePublicUrl(), newModel.getPublicUrl(), null);
            handleTextDataBinding(newModel, JVM_OPTIONS_KEY, this.jvmOpsTextField);
            handleNumberComboBinding(newModel, CPU, this.cpuCombo);
            handleNumberComboBinding(newModel, MEMORY_IN_GB_KEY, this.memCombo);
            handleTextComboBinding(newModel, JAVA_VERSION_KEY, this.javaVersionCombo);
            if (newModel.getTestUrl().startsWith("http")) {
                this.testUrlLink.setHyperlinkText(newModel.getTestUrl());
                this.testUrlLink.setHyperlinkTarget(newModel.getTestUrl());
            } else {
                this.testUrlLink.setHyperlinkTarget(null);
                this.testUrlLink.setText(newModel.getTestUrl());
            }
            this.triggerPersistentButton.setText(newModel.isEnablePersistentStorage() ? DISABLE_TEXT : ENABLE_TEXT);
            if (newModel.isEnablePersistentStorage()) {
                renderPersistent(newModel);
            } else {
                this.persistentLabel.setText(DISABLED_TEXT);
            }
            String statusLineText = newModel.getStatus();
            if (newModel.getUpInstanceCount().intValue() + newModel.getDownInstanceCount().intValue() > 0) {
                statusLineText = String.format("%s - Discovery Status(UP %d, DOWN %d)",
                                               newModel.getStatus(),
                                               newModel.getUpInstanceCount(), newModel.getDownInstanceCount());
            }
            Border statusLine = BorderFactory.createTitledBorder(statusLineText);
            this.statusPanel.setBorder(statusLine);
            this.startButton.setEnabled(newModel.isCanStart());
            this.stopButton.setEnabled(newModel.isCanStop());
            this.restartButton.setEnabled(newModel.isCanReStart());

            this.refreshButton.setEnabled(true);
            this.deleteButton.setEnabled(true);
            this.saveButton.setEnabled(false);
            instancesTableModel.getDataVector().removeAllElements();

            instanceTable.getEmptyText().setText(EMPTY_TEXT);

            if (newModel.getInstance() != null) {
                for (final SpringCloudAppInstanceViewModel deploymentInstance : newModel.getInstance()) {
                    instancesTableModel.addRow(new String[]{
                            deploymentInstance.getName(), deploymentInstance.getStatus(), deploymentInstance.getDiscoveryStatus()});
                }
            }
            instanceTable.setModel(instancesTableModel);
            instanceTable.updateUI();
            envTable.setEnvironmentVariables(
                    newModel.getEnvironment() == null ? new HashMap<>() : new HashMap<>(newModel.getEnvironment()));
            this.viewModel = newModel;
            restoreUI();
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new AzureExecutionException("Cannot get property through reflection", e);
        } catch (Exception ex) {
            throw new AzureExecutionException("Cannot render property view due to error", ex);
        }
    }

    private void closeEditor() {
        DefaultLoader.getUIHelper().closeSpringCloudAppPropertyView(project, appId);
        PluginUtil.showInfoNotificationProject(project,
                                               String.format("The editor for app %s is closed.", this.appName), "The app " + this.appName + " is deleted.");
    }

}
