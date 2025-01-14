package io.bdeploy.minion.remote.jersey;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.ActivityReporter.Activity;
import io.bdeploy.common.Version;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.security.ScopedPermission;
import io.bdeploy.common.security.ScopedPermission.Permission;
import io.bdeploy.common.util.PathHelper;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.instance.SoftwareRepositoryConfiguration;
import io.bdeploy.interfaces.directory.EntryChunk;
import io.bdeploy.interfaces.directory.RemoteDirectory;
import io.bdeploy.interfaces.directory.RemoteDirectoryEntry;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.interfaces.manifest.SoftwareRepositoryManifest;
import io.bdeploy.interfaces.minion.MinionConfiguration;
import io.bdeploy.interfaces.remote.CommonDirectoryEntryResource;
import io.bdeploy.interfaces.remote.CommonInstanceResource;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.interfaces.remote.MinionStatusResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.JerseySecurityContext;
import io.bdeploy.minion.MinionRoot;
import io.bdeploy.ui.api.AuthService;
import io.bdeploy.ui.api.MinionMode;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.SecurityContext;

public class CommonRootResourceImpl implements CommonRootResource {

    private static final Logger log = LoggerFactory.getLogger(CommonRootResourceImpl.class);

    @Inject
    private BHiveRegistry registry;

    @Inject
    private ActivityReporter reporter;

    @Context
    private ResourceContext rc;

    @Inject
    private AuthService auth;

    @Context
    private ContainerRequestContext context;

    @Inject
    private MinionRoot minion;

    @Context
    private SecurityContext security;

    @Override
    public Version getVersion() {
        return VersionHelper.getVersion();
    }

    @Override
    public List<SoftwareRepositoryConfiguration> getSoftwareRepositories() {
        List<SoftwareRepositoryConfiguration> result = new ArrayList<>();
        for (Map.Entry<String, BHive> entry : registry.getAll().entrySet()) {
            SoftwareRepositoryConfiguration cfg = new SoftwareRepositoryManifest(entry.getValue()).read();
            if (cfg != null) {
                result.add(cfg);
            }
        }
        return result;
    }

    @Override
    public void addSoftwareRepository(SoftwareRepositoryConfiguration config, String storage) {
        if (storage == null) {
            storage = getStorageLocations().iterator().next();
        }

        if (!getStorageLocations().contains(storage)) {
            log.warn("Tried to use storage location: {}, valid are: {}", storage, getStorageLocations());
            throw new WebApplicationException("Invalid Storage Location", Status.NOT_FOUND);
        }

        Path hive = Paths.get(storage, config.name);
        if (Files.isDirectory(hive)) {
            throw new WebApplicationException("Hive path already exists", Status.NOT_ACCEPTABLE);
        }

        BHive h = new BHive(hive.toUri(), reporter);
        new SoftwareRepositoryManifest(h).update(config);
        registry.register(config.name, h);
    }

    @Override
    public Set<String> getStorageLocations() {
        return registry.getLocations().stream().map(Path::toString).collect(Collectors.toSet());
    }

    @Override
    public List<InstanceGroupConfiguration> getInstanceGroups() {
        // need to obtain from request to avoid SecurityContextInjectee wrapper.
        SecurityContext ctx = context.getSecurityContext();
        if (!(ctx instanceof JerseySecurityContext)) {
            throw new ForbiddenException(
                    "User '" + ctx.getUserPrincipal().getName() + "' is not authorized to access requested resource.");
        }
        JerseySecurityContext securityContext = (JerseySecurityContext) ctx;

        List<InstanceGroupConfiguration> result = new ArrayList<>();
        for (Map.Entry<String, BHive> entry : registry.getAll().entrySet()) {
            InstanceGroupConfiguration cfg = new InstanceGroupManifest(entry.getValue()).read();
            if (cfg == null) {
                continue;
            }
            // The current user must have at least scoped read permissions
            ScopedPermission requiredPermission = new ScopedPermission(cfg.name, Permission.READ);
            if (!securityContext.isAuthorized(requiredPermission)
                    && !auth.isAuthorized(securityContext.getUserPrincipal().getName(), requiredPermission)) {
                continue;
            }

            result.add(cfg);
        }
        return result;
    }

    @Override
    public void addInstanceGroup(InstanceGroupConfiguration meta, String storage) {
        if (storage == null) {
            storage = getStorageLocations().iterator().next();
        }

        if (!getStorageLocations().contains(storage)) {
            log.warn("Tried to use storage location: {}, valid are: {}", storage, getStorageLocations());
            throw new WebApplicationException("Invalid Storage Location", Status.NOT_FOUND);
        }

        Path hive = Paths.get(storage, meta.name);
        if (Files.isDirectory(hive)) {
            throw new WebApplicationException("Hive path already exists", Status.NOT_ACCEPTABLE);
        }

        meta.managed = (minion.getMode() != MinionMode.STANDALONE);

        BHive h = new BHive(hive.toUri(), reporter);
        new InstanceGroupManifest(h).update(meta);
        registry.register(meta.name, h);
    }

    @Override
    public void deleteInstanceGroup(String name) {
        BHive bHive = registry.get(name);
        if (bHive == null) {
            throw new WebApplicationException("Instance Group '" + name + "' does not exist", Status.NOT_FOUND);
        }
        registry.unregister(name);
        PathHelper.deleteRecursive(Paths.get(bHive.getUri()));
    }

    @Override
    public CommonInstanceResource getInstanceResource(String name) {
        if (name == null) {
            throw new WebApplicationException("No instance group parameter given", Status.BAD_REQUEST);
        }
        BHive bHive = registry.get(name);
        if (bHive == null) {
            throw new WebApplicationException("Instance Group '" + name + "' does not exist", Status.NOT_FOUND);
        }
        return rc.initResource(new CommonInstanceResourceImpl(name, bHive));
    }

    @Override
    public Path getLoggerConfig() {
        return minion.getLoggingConfigurationFile();
    }

    @Override
    public void setLoggerConfig(Path config) {
        MinionConfiguration minionConfig = minion.getMinions();
        try (Activity updating = reporter.start("Update Node Logging", minionConfig.size())) {
            for (var entry : minionConfig.entrySet()) {
                try {
                    ResourceProvider.getVersionedResource(entry.getValue().remote, MinionStatusResource.class, security)
                            .setLoggerConfig(config);
                } catch (Exception e) {
                    log.error("Cannot udpate logging configuration on {}", entry.getKey(), e);
                }

                updating.workAndCancelIfRequested(1);
            }
        } finally {
            PathHelper.deleteRecursive(config);
        }
    }

    @Override
    public List<RemoteDirectory> getLogDirectories() {
        List<RemoteDirectory> result = new ArrayList<>();

        MinionConfiguration minionConfig = minion.getMinions();
        try (Activity reading = reporter.start("Reading Node Logs", minionConfig.size())) {
            for (var entry : minionConfig.entrySet()) {
                RemoteDirectory dir = new RemoteDirectory();
                dir.minion = entry.getKey();

                try {
                    dir.entries.addAll(ResourceProvider
                            .getVersionedResource(entry.getValue().remote, MinionStatusResource.class, security).getLogEntries());
                } catch (Exception e) {
                    log.warn("Problem fetching log directory of {}", entry.getKey(), e);
                    dir.problem = e.toString();
                }

                result.add(dir);
                reading.workAndCancelIfRequested(1);
            }
        }

        return result;

    }

    @Override
    public EntryChunk getLogContent(String minionName, RemoteDirectoryEntry entry, long offset, long limit) {
        RemoteService svc = minion.getMinions().getRemote(minionName);
        if (svc == null) {
            throw new WebApplicationException("Cannot find minion " + minionName, Status.NOT_FOUND);
        }
        CommonDirectoryEntryResource sdr = ResourceProvider.getVersionedResource(svc, CommonDirectoryEntryResource.class,
                security);
        return sdr.getEntryContent(entry, offset, limit);
    }

    @Override
    public Response getLogStream(String minionName, RemoteDirectoryEntry entry) {
        RemoteService svc = minion.getMinions().getRemote(minionName);
        if (svc == null) {
            throw new WebApplicationException("Cannot find minion " + minionName, Status.NOT_FOUND);
        }
        CommonDirectoryEntryResource sdr = ResourceProvider.getVersionedResource(svc, CommonDirectoryEntryResource.class,
                security);
        return sdr.getEntryStream(entry);
    }

}
