package io.bdeploy.ui.cli;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.TreeMap;

import javax.ws.rs.NotFoundException;

import io.bdeploy.common.cfg.Configuration.EnvironmentFallback;
import io.bdeploy.common.cfg.Configuration.Help;
import io.bdeploy.common.cli.ToolBase.CliTool.CliName;
import io.bdeploy.common.cli.ToolCategory;
import io.bdeploy.common.cli.data.DataResult;
import io.bdeploy.common.cli.data.DataTable;
import io.bdeploy.common.cli.data.DataTableColumn;
import io.bdeploy.common.cli.data.RenderableResult;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.DateHelper;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.pcu.ProcessDetailDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessHandleDto;
import io.bdeploy.interfaces.configuration.pcu.ProcessStatusDto;
import io.bdeploy.interfaces.manifest.state.InstanceStateRecord;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.cli.RemoteServiceTool;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.InstanceResource;
import io.bdeploy.ui.api.ProcessResource;
import io.bdeploy.ui.cli.RemoteProcessTool.RemoteProcessConfig;

@Help("Deploy to a remote master minion")
@ToolCategory(TextUIResources.UI_CATEGORY)
@CliName("remote-process")
public class RemoteProcessTool extends RemoteServiceTool<RemoteProcessConfig> {

    public @interface RemoteProcessConfig {

        @Help("UUID of the deployment to query/control")
        String uuid();

        @Help("The name of the application to control, controls all applications for the given UUID if missing")
        String application();

        @Help("The name of the instance group to work on")
        @EnvironmentFallback("REMOTE_BHIVE")
        String instanceGroup();

        @Help(value = "List processes on the remote", arg = false)
        boolean list() default false;

        @Help(value = "Start a named process.", arg = false)
        boolean start() default false;

        @Help(value = "Stop a named process.", arg = false)
        boolean stop() default false;
    }

    public RemoteProcessTool() {
        super(RemoteProcessConfig.class);
    }

    @Override
    protected RenderableResult run(RemoteProcessConfig config, RemoteService svc) {
        String instanceId = config.uuid();
        helpAndFailIfMissing(instanceId, "Missing --uuid");

        String groupName = config.instanceGroup();
        helpAndFailIfMissing(groupName, "Missing --instanceGroup");

        if (!config.start() && !config.list() && !config.stop()) {
            helpAndFailIfMissing(null, "Missing --start or --stop or --list");
        }

        InstanceResource ir = ResourceProvider.getResource(svc, InstanceGroupResource.class, getLocalContext())
                .getInstanceResource(groupName);
        String appId = config.application();
        if (config.start() || config.stop()) {
            if (config.start()) {
                if (appId == null || appId.isEmpty()) {
                    ir.getProcessResource(instanceId).startAll();
                } else {
                    ir.getProcessResource(instanceId).startProcess(appId);
                }
            } else if (config.stop()) {
                if (appId == null || appId.isEmpty()) {
                    ir.getProcessResource(instanceId).stopAll();
                } else {
                    ir.getProcessResource(instanceId).stopProcess(appId);
                }
            }
            return createSuccess();
        }

        Map<String, ProcessStatusDto> status = ir.getProcessResource(instanceId).getStatus();
        if (appId == null) {
            return createAllProcessesTable(config, svc, ir, status);
        } else {
            ProcessDetailDto appStatus = ir.getProcessResource(instanceId).getDetails(appId);

            DataResult result = createResultWithMessage(
                    "Details for " + appId + " of instance " + instanceId + " of instance group " + groupName)
                            .addField("Name", appStatus.status.appName).addField("Application ID", appStatus.status.appUid)
                            .addField("Exit Code", appStatus.status.exitCode)
                            .addField("Instance ID", appStatus.status.instanceUid)
                            .addField("Instance Version", appStatus.status.instanceTag).addField("Main PID", appStatus.status.pid)
                            .addField("State", appStatus.status.processState)
                            .addField("Retries", appStatus.retryCount + "/" + appStatus.maxRetryCount)
                            .addField("StdIn attached", (appStatus.hasStdin ? "Yes" : "No"));

            if (appStatus.handle != null) {
                addProcessDetails(result, appStatus.handle, "");
            }

            return result;
        }
    }

    private void addProcessDetails(DataResult result, ProcessHandleDto pdd, String indent) {
        result.addField("Process PID=" + pdd.pid,
                String.format("%1$s└[cpu=%3$ds] %4$s %5$s", indent, pdd.pid, pdd.totalCpuDuration, pdd.command,
                        (pdd.arguments != null && pdd.arguments.length > 0 ? String.join(" ", pdd.arguments) : "")));

        for (ProcessHandleDto child : pdd.children) {
            addProcessDetails(result, child, indent + " ");
        }
    }

    private DataTable createAllProcessesTable(RemoteProcessConfig config, RemoteService svc, InstanceResource ir,
            Map<String, ProcessStatusDto> status) {
        DataTable table = createDataTable();
        table.setCaption("Processes for Instance " + config.uuid() + " in Instance Group " + config.instanceGroup() + " on "
                + svc.getUri());
        table.column("Name", 35);
        table.column("ID", 14);
        table.column("Status", 20);
        table.column(new DataTableColumn("Version", "Ver.*", 5));
        table.column("Product Version", 20);
        table.column("Started At", 20);
        table.column("OS User", 20);
        table.column("PID", 6);
        table.column(new DataTableColumn("ExitCode", "Exit", 4));

        Map<String, Optional<InstanceConfiguration>> instanceInfos = new TreeMap<>();
        InstanceStateRecord deploymentStates = ir.getDeploymentStates(config.uuid());

        for (Entry<String, ProcessStatusDto> processEntry : status.entrySet()) {
            String tag = processEntry.getValue().instanceTag;
            Optional<InstanceConfiguration> instance = null;
            instance = instanceInfos.computeIfAbsent(tag, k -> {
                try {
                    return Optional.of(ir.readVersion(config.uuid(), tag));
                } catch (NotFoundException nf) {
                    // instance version not found - probably not synced to central.
                    out().println("WARNING: Cannot read instance version version " + tag
                            + ". This can happen for instance if the central server is not synchronized.");
                    return Optional.ofNullable(null);
                }
            });

            addProcessRows(table, ir.getProcessResource(config.uuid()), processEntry.getValue(), processEntry.getKey(), instance,
                    deploymentStates);
        }

        table.addFooter(" * Versions marked with '*' are out-of-sync (not running from the active version)");

        return table;
    }

    private void addProcessRows(DataTable table, ProcessResource pr, ProcessStatusDto process, String nodeName,
            Optional<InstanceConfiguration> instance, InstanceStateRecord deploymentStates) {
        ProcessDetailDto detail = pr.getDetails(process.appUid);
        ProcessHandleDto handle = detail.handle;

        table.row().cell(process.appName) //
                .cell(process.appUid) //
                .cell(process.processState.name()) //
                .cell(process.instanceTag + (process.instanceTag.equals(deploymentStates.activeTag) ? "" : "*")) //
                .cell(instance.isPresent() ? instance.get().product.getTag() : "?") //
                .cell(handle == null ? "-" : DateHelper.format(handle.startTime)) //
                .cell(handle == null ? "-" : handle.user) //
                .cell(handle == null ? "-" : Long.toString(handle.pid)) //
                .cell(Integer.toString(process.exitCode)).build(); //
    }

}