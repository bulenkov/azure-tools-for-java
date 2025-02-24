/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.intellij.connector.mysql;

import com.intellij.openapi.project.Project;
import com.microsoft.azure.management.mysql.v2020_01_01.Server;
import com.microsoft.azure.toolkit.intellij.connector.ConnectorDialog;
import com.microsoft.azure.toolkit.intellij.connector.ModuleResource;
import com.microsoft.azuretools.ActionConstants;
import com.microsoft.tooling.msservices.helpers.Name;
import com.microsoft.tooling.msservices.serviceexplorer.AzureIconSymbol;
import com.microsoft.tooling.msservices.serviceexplorer.NodeActionEvent;
import com.microsoft.tooling.msservices.serviceexplorer.NodeActionListener;
import com.microsoft.tooling.msservices.serviceexplorer.azure.mysql.MySQLNode;

@Name(ConnectToMySQLAction.ACTION_NAME)
public class ConnectToMySQLAction extends NodeActionListener {

    public static final String ACTION_NAME = "Connect to Project(Preview)";

    private final MySQLNode node;
    private final Project project;

    public ConnectToMySQLAction(MySQLNode node) {
        super();
        this.node = node;
        this.project = (Project) node.getProject();
    }

    @Override
    public AzureIconSymbol getIconSymbol() {
        return AzureIconSymbol.MySQL.BIND_INTO;
    }

    @Override
    public void actionPerformed(NodeActionEvent e) {
        final ConnectorDialog<MySQLDatabaseResource, ModuleResource> dialog = new ConnectorDialog<>(project);
        final Server server = this.node.getServer();
        dialog.setResource(new MySQLDatabaseResource(server.id(), null));
        dialog.show();
    }

    @Override
    protected String getServiceName(NodeActionEvent event) {
        return ActionConstants.parse(ActionConstants.MySQL.LINK_TO_MODULE).getServiceName();
    }

    @Override
    protected String getOperationName(NodeActionEvent event) {
        return ActionConstants.parse(ActionConstants.MySQL.LINK_TO_MODULE).getOperationName();
    }
}
