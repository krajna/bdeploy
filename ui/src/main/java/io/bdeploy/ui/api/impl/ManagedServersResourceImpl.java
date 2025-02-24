package io.bdeploy.ui.api.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.stream.Collectors;

import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.file.StreamDataBodyPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.bdeploy.api.product.v1.impl.ScopedManifestKey;
import io.bdeploy.bhive.BHive;
import io.bdeploy.bhive.BHiveTransactions.Transaction;
import io.bdeploy.bhive.meta.MetaManifest;
import io.bdeploy.bhive.model.Manifest;
import io.bdeploy.bhive.model.Manifest.Key;
import io.bdeploy.bhive.model.ObjectId;
import io.bdeploy.bhive.op.ManifestDeleteOperation;
import io.bdeploy.bhive.op.ManifestListOperation;
import io.bdeploy.bhive.op.ManifestRefScanOperation;
import io.bdeploy.bhive.op.ObjectLoadOperation;
import io.bdeploy.bhive.op.remote.FetchOperation;
import io.bdeploy.bhive.op.remote.PushOperation;
import io.bdeploy.bhive.remote.RemoteBHive;
import io.bdeploy.bhive.remote.jersey.BHiveRegistry;
import io.bdeploy.bhive.remote.jersey.JerseyRemoteBHive;
import io.bdeploy.common.ActivityReporter;
import io.bdeploy.common.Version;
import io.bdeploy.common.security.RemoteService;
import io.bdeploy.common.util.StreamHelper;
import io.bdeploy.common.util.VersionHelper;
import io.bdeploy.interfaces.UpdateHelper;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfigurationDto;
import io.bdeploy.interfaces.configuration.instance.InstanceGroupConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceUpdateDto;
import io.bdeploy.interfaces.manifest.InstanceGroupManifest;
import io.bdeploy.interfaces.manifest.InstanceManifest;
import io.bdeploy.interfaces.manifest.managed.ControllingMaster;
import io.bdeploy.interfaces.manifest.managed.ManagedMasterDto;
import io.bdeploy.interfaces.manifest.managed.ManagedMasters;
import io.bdeploy.interfaces.manifest.managed.ManagedMastersConfiguration;
import io.bdeploy.interfaces.manifest.managed.MinionUpdateDto;
import io.bdeploy.interfaces.minion.MinionConfiguration;
import io.bdeploy.interfaces.minion.MinionDto;
import io.bdeploy.interfaces.minion.MinionStatusDto;
import io.bdeploy.interfaces.remote.CommonInstanceResource;
import io.bdeploy.interfaces.remote.CommonRootResource;
import io.bdeploy.interfaces.remote.MasterNamedResource;
import io.bdeploy.interfaces.remote.MasterRootResource;
import io.bdeploy.interfaces.remote.MasterSettingsResource;
import io.bdeploy.interfaces.remote.ResourceProvider;
import io.bdeploy.jersey.JerseyClientFactory;
import io.bdeploy.jersey.JerseyOnBehalfOfFilter;
import io.bdeploy.jersey.ws.change.msg.ObjectScope;
import io.bdeploy.ui.ProductTransferService;
import io.bdeploy.ui.api.BackendInfoResource;
import io.bdeploy.ui.api.InstanceGroupResource;
import io.bdeploy.ui.api.ManagedServersAttachEventResource;
import io.bdeploy.ui.api.ManagedServersResource;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.api.MinionMode;
import io.bdeploy.ui.api.SoftwareUpdateResource;
import io.bdeploy.ui.dto.CentralIdentDto;
import io.bdeploy.ui.dto.ObjectChangeDetails;
import io.bdeploy.ui.dto.ObjectChangeHint;
import io.bdeploy.ui.dto.ObjectChangeType;
import io.bdeploy.ui.dto.ProductDto;
import io.bdeploy.ui.dto.ProductTransferDto;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.Response.Status.Family;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriBuilder;

public class ManagedServersResourceImpl implements ManagedServersResource {

    private static final Logger log = LoggerFactory.getLogger(ManagedServersResourceImpl.class);

    @Context
    private SecurityContext context;

    @Inject
    private BHiveRegistry registry;

    @Inject
    private Minion minion;

    @Inject
    private ActivityReporter reporter;

    @Inject
    private ProductTransferService transfers;

    @Inject
    private ChangeEventManager changes;

    @Override
    public void tryAutoAttach(String groupName, ManagedMasterDto target) {
        RemoteService svc = new RemoteService(UriBuilder.fromUri(target.uri).build(), target.auth);
        CommonRootResource root = ResourceProvider.getVersionedResource(svc, CommonRootResource.class, context);

        boolean igExists = false;
        for (InstanceGroupConfiguration cfg : root.getInstanceGroups()) {
            if (cfg.name.equals(groupName)) {
                igExists = true; // don't try to create, instead sync
            }
        }

        BHive hive = getInstanceGroupHive(groupName);
        ManagedMasters mm = new ManagedMasters(hive);

        if (mm.read().getManagedMasters().containsKey(target.hostName)) {
            throw new WebApplicationException("Server with name " + target.hostName + " already exists!", Status.CONFLICT);
        }

        InstanceGroupManifest igm = new InstanceGroupManifest(hive);
        InstanceGroupConfiguration igc = igm.read();
        igc.logo = null;

        try {
            // store the attachment locally
            mm.attach(target, false);

            if (!igExists) {
                // initial create without logo - required to create instance group hive.
                root.addInstanceGroup(igc, null);

                // push the latest instance group manifest to the target
                hive.execute(new PushOperation().setHiveName(groupName).addManifest(igm.getKey()).setRemote(svc));
            } else {
                synchronize(groupName, target.hostName);
            }

            ResourceProvider.getVersionedResource(svc, ManagedServersAttachEventResource.class, context)
                    .setLocalAttached(groupName);

            changes.change(ObjectChangeType.INSTANCE_GROUP, igm.getKey(),
                    Map.of(ObjectChangeDetails.CHANGE_HINT, ObjectChangeHint.SERVERS));
        } catch (Exception e) {
            throw new WebApplicationException("Cannot automatically attach managed server " + target.hostName, e);
        }
    }

    @Override
    public void manualAttach(String groupName, ManagedMasterDto target) {
        BHive hive = getInstanceGroupHive(groupName);
        ManagedMasters mm = new ManagedMasters(hive);

        if (mm.read().getManagedMasters().containsKey(target.hostName)) {
            throw new WebApplicationException("Server with name " + target.hostName + " already exists!", Status.CONFLICT);
        }

        // store the attachment locally
        mm.attach(target, false);

        InstanceGroupManifest igm = new InstanceGroupManifest(hive);
        changes.change(ObjectChangeType.INSTANCE_GROUP, igm.getKey(),
                Map.of(ObjectChangeDetails.CHANGE_HINT, ObjectChangeHint.SERVERS));
    }

    @Override
    public String manualAttachCentral(String central) {
        CentralIdentDto dto = minion.getDecryptedPayload(central, CentralIdentDto.class);

        RemoteService selfSvc = minion.getSelf();
        RemoteService testSelf = new RemoteService(selfSvc.getUri(), dto.local.auth);

        // will throw if there is an authentication problem.
        InstanceGroupResource self = ResourceProvider.getVersionedResource(testSelf, InstanceGroupResource.class, context);

        dto.config.logo = null; // later.
        self.create(dto.config);
        if (dto.logo != null) {
            try (ByteArrayInputStream bis = new ByteArrayInputStream(dto.logo); MultiPart mp = new MultiPart();) {
                StreamDataBodyPart bp = new StreamDataBodyPart("image", bis);
                bp.setFilename("logo.png");
                bp.setMediaType(new MediaType("image", "png"));
                mp.bodyPart(bp);

                WebTarget target = ResourceProvider.of(testSelf).getBaseTarget(new JerseyOnBehalfOfFilter(context))
                        .path("/group/" + dto.config.name + "/image");
                Response response = target.request().post(Entity.entity(mp, MediaType.MULTIPART_FORM_DATA_TYPE));

                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    throw new IllegalStateException("Image update failed: " + response.getStatusInfo().getReasonPhrase());
                }
            } catch (IOException e) {
                log.error("Failed to update instance group logo", e);
            }
        }
        return dto.config.name;
    }

    @Override
    public String getCentralIdent(String groupName, ManagedMasterDto target) {
        BHive hive = getInstanceGroupHive(groupName);

        InstanceGroupManifest igm = new InstanceGroupManifest(hive);

        CentralIdentDto dto = new CentralIdentDto();
        dto.config = igm.read();
        dto.local = target;
        if (dto.config.logo != null) {
            try (InputStream is = hive.execute(new ObjectLoadOperation().setObject(dto.config.logo));
                    ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                StreamHelper.copy(is, baos);
                dto.logo = baos.toByteArray();
            } catch (IOException e) {
                log.error("Cannot read instance group logo, ignoring", e);
            }
        }

        return minion.getEncryptedPayload(dto);
    }

    @Override
    public List<ManagedMasterDto> getManagedServers(String instanceGroup) {
        BHive hive = getInstanceGroupHive(instanceGroup);

        ManagedMastersConfiguration masters = new ManagedMasters(hive).read();
        return masters.getManagedMasters().values().stream().map(e -> {
            e.auth = null;
            return e;
        }).collect(Collectors.toList());
    }

    @Override
    public ManagedMasterDto getServerForInstance(String instanceGroup, String instanceId, String instanceTag) {
        BHive hive = getInstanceGroupHive(instanceGroup);

        ManagedMastersConfiguration masters = new ManagedMasters(hive).read();
        InstanceManifest im = InstanceManifest.load(hive, instanceId, instanceTag);

        String selected = new ControllingMaster(hive, im.getManifest()).read().getName();
        if (selected == null) {
            return null;
        }

        ManagedMasterDto dto = masters.getManagedMaster(selected);

        if (dto == null) {
            throw new WebApplicationException(
                    "Recorded managed server for instance " + instanceId + " no longer available on instance group: " + selected,
                    Status.NOT_FOUND);
        }

        // clear token - don't transfer over the wire if not required.
        dto.auth = null;
        return dto;
    }

    private BHive getInstanceGroupHive(String instanceGroup) {
        BHive hive = registry.get(instanceGroup);
        if (hive == null) {
            throw new WebApplicationException("Cannot find Instance Group " + instanceGroup + " locally");
        }
        return hive;
    }

    @Override
    public List<InstanceConfiguration> getInstancesControlledBy(String groupName, String serverName) {
        List<InstanceConfiguration> instances = new ArrayList<>();
        BHive hive = getInstanceGroupHive(groupName);

        SortedSet<Manifest.Key> latestKeys = InstanceManifest.scan(hive, true);

        for (Manifest.Key key : latestKeys) {
            String associated = new ControllingMaster(hive, key).read().getName();
            if (serverName.equals(associated)) {
                instances.add(InstanceManifest.of(hive, key).getConfiguration());
            }
        }
        return instances;
    }

    @Override
    public void deleteManagedServer(String groupName, String serverName) {
        BHive hive = getInstanceGroupHive(groupName);
        List<InstanceConfiguration> controlled = getInstancesControlledBy(groupName, serverName);

        // delete all of the instances /LOCALLY/ on the central, but NOT using the remote master (we "just" detach).
        for (InstanceConfiguration cfg : controlled) {
            Set<Key> allInstanceObjects = hive.execute(new ManifestListOperation().setManifestName(cfg.uuid));
            allInstanceObjects.forEach(x -> hive.execute(new ManifestDeleteOperation().setToDelete(x)));
        }

        new ManagedMasters(hive).detach(serverName);

        InstanceGroupManifest igm = new InstanceGroupManifest(hive);
        changes.change(ObjectChangeType.INSTANCE_GROUP, igm.getKey(),
                Map.of(ObjectChangeDetails.CHANGE_HINT, ObjectChangeHint.SERVERS));
    }

    @Override
    public void updateManagedServer(String groupName, String serverName, ManagedMasterDto update) {
        if (!serverName.equals(update.hostName)) {
            throw new WebApplicationException("Server name does not match configuration: " + serverName, Status.BAD_REQUEST);
        }

        ManagedMasters mm = new ManagedMasters(getInstanceGroupHive(groupName));

        ManagedMasterDto old = mm.read().getManagedMaster(serverName);
        if (old == null) {
            throw new WebApplicationException("Server does not (yet) exist: " + serverName, Status.NOT_FOUND);
        }

        if (update.auth == null || update.auth.isBlank()) {
            // token might have been cleared out.
            update.auth = old.auth;
        }

        mm.attach(update, true);

        BHive hive = getInstanceGroupHive(groupName);
        InstanceGroupManifest igm = new InstanceGroupManifest(hive);
        changes.change(ObjectChangeType.INSTANCE_GROUP, igm.getKey(),
                Map.of(ObjectChangeDetails.CHANGE_HINT, ObjectChangeHint.SERVERS));
    }

    @Override
    public Map<String, MinionDto> getMinionsOfManagedServer(String groupName, String serverName) {
        BHive hive = getInstanceGroupHive(groupName);

        ManagedMasters mm = new ManagedMasters(hive);
        ManagedMasterDto attached = mm.read().getManagedMaster(serverName);
        return attached.minions.values();
    }

    @Override
    public Map<String, MinionStatusDto> getMinionStateOfManagedServer(String groupName, String serverName) {
        RemoteService svc = getConfiguredRemote(groupName, serverName);
        MasterRootResource root = ResourceProvider.getVersionedResource(svc, MasterRootResource.class, context);
        return root.getMinions();
    }

    private RemoteService getConfiguredRemote(String groupName, String serverName) {
        BHive hive = getInstanceGroupHive(groupName);

        ManagedMasterDto attached = new ManagedMasters(hive).read().getManagedMaster(serverName);
        if (attached == null) {
            throw new WebApplicationException("Managed server " + serverName + " not found for instance group " + groupName,
                    Status.EXPECTATION_FAILED);
        }

        return new RemoteService(UriBuilder.fromUri(attached.uri).build(), attached.auth);
    }

    @Override
    public ManagedMasterDto synchronize(String groupName, String serverName) {
        if (minion.getMode() != MinionMode.CENTRAL) {
            return null;
        }
        BHive hive = getInstanceGroupHive(groupName);
        try (Transaction t = hive.getTransactions().begin()) {
            return synchronizeTransacted(hive, groupName, serverName);
        }
    }

    private ManagedMasterDto synchronizeTransacted(BHive hive, String groupName, String serverName) {
        RemoteService svc = getConfiguredRemote(groupName, serverName);

        BackendInfoResource backendInfo = ResourceProvider.getVersionedResource(svc, BackendInfoResource.class, context);
        if (backendInfo.getVersion().mode != MinionMode.MANAGED) {
            throw new WebApplicationException("Server is no longer in managed mode: " + serverName, Status.EXPECTATION_FAILED);
        }

        ManagedMasters mm = new ManagedMasters(hive);
        ManagedMasterDto attached = mm.read().getManagedMaster(serverName);
        InstanceGroupManifest igm = new InstanceGroupManifest(hive);

        List<InstanceManifest> updatedInstances = new ArrayList<>();
        List<InstanceManifest> removedInstances = new ArrayList<>();

        // 1. Fetch information about updates, possibly required
        attached.update = getUpdates(svc);

        // don't continue actual data sync if update MUST be installed.
        if (!attached.update.forceUpdate) {
            // 2. Sync instance group data with managed server.
            CommonRootResource root = ResourceProvider.getVersionedResource(svc, CommonRootResource.class, context);
            if (root.getInstanceGroups().stream().map(g -> g.name).noneMatch(n -> n.equals(groupName))) {
                throw new WebApplicationException("Instance group (no longer?) found on the managed server", Status.NOT_FOUND);
            }

            Manifest.Key igKey = igm.getKey();
            String attributesMetaName = igm.getAttributes(hive).getMetaManifest().getMetaName();
            Set<Key> metaManifests = hive.execute(new ManifestListOperation().setManifestName(attributesMetaName));

            PushOperation push = new PushOperation().addManifest(igKey).setRemote(svc).setHiveName(groupName);
            metaManifests.forEach(push::addManifest);
            hive.execute(push);

            // 3. Fetch all instance and meta manifests, no products.
            CommonRootResource masterRoot = ResourceProvider.getVersionedResource(svc, CommonRootResource.class, context);
            CommonInstanceResource master = masterRoot.getInstanceResource(groupName);
            SortedMap<Key, InstanceConfiguration> instances = master.listInstanceConfigurations(true);
            List<String> instanceIds = instances.values().stream().map(ic -> ic.uuid).collect(Collectors.toList());

            FetchOperation fetchOp = new FetchOperation().setRemote(svc).setHiveName(groupName);
            try (RemoteBHive rbh = RemoteBHive.forService(svc, groupName, reporter)) {
                Set<Manifest.Key> keysToFetch = new LinkedHashSet<>();

                // maybe we can scope this down a little in the future.
                rbh.getManifestInventory(instanceIds.toArray(String[]::new)).forEach((k, v) -> keysToFetch.add(k));

                // we're also interested in all the related meta manifests.
                rbh.getManifestInventory(instanceIds.stream().map(s -> MetaManifest.META_PREFIX + s).toArray(String[]::new))
                        .forEach((k, v) -> keysToFetch.add(k));

                // set calculated keys to fetch operation.
                fetchOp.addManifest(keysToFetch);
            }

            hive.execute(fetchOp);

            // 4. Remove local instances no longer available on the remote
            SortedSet<Key> keysOnCentral = InstanceManifest.scan(hive, true);
            for (Key key : keysOnCentral) {
                InstanceManifest im = InstanceManifest.of(hive, key);
                if (instanceIds.contains(im.getConfiguration().uuid)) {
                    // MAYBE has been updated by the sync.
                    updatedInstances.add(im);
                    continue; // OK. instance exists
                }

                if (!serverName.equals(new ControllingMaster(hive, key).read().getName())) {
                    continue; // OK. other server or null (should not happen).
                }

                // Not OK: instance no longer on server.
                Set<Key> allInstanceObjects = hive
                        .execute(new ManifestListOperation().setManifestName(im.getConfiguration().uuid));
                allInstanceObjects.forEach(x -> hive.execute(new ManifestDeleteOperation().setToDelete(x)));

                removedInstances.add(im);
            }

            // 5. for all the fetched manifests, if they are instances, associate the server with it, and send out a change
            for (Manifest.Key instance : instances.keySet()) {
                new ControllingMaster(hive, instance).associate(serverName);
            }

            // 6. try to sync instance group properties
            try {
                MasterSettingsResource msr = ResourceProvider.getVersionedResource(svc, MasterSettingsResource.class, context);
                msr.mergeInstanceGroupAttributesDescriptors(minion.getSettings().instanceGroup.attributes);
            } catch (Exception e) {
                log.warn("Cannot sync InstanceGroup properties to managed server, ignoring", e);
            }

            attached.lastSync = Instant.now();
        }

        // 7. Fetch minion information and store in the managed masters
        Map<String, MinionStatusDto> status = backendInfo.getNodeStatus();
        Map<String, MinionDto> config = status.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().config));
        MinionConfiguration minions = attached.minions;
        minions.replaceWith(config);

        // 8. update current information in the hive.
        mm.attach(attached, true);

        // 9. send out notifications after *all* is done.
        for (var im : updatedInstances) {
            changes.change(ObjectChangeType.INSTANCE, im.getManifest(), new ObjectScope(groupName, im.getConfiguration().uuid));
        }

        for (var im : removedInstances) {
            changes.remove(ObjectChangeType.INSTANCE, im.getManifest(), new ObjectScope(groupName, im.getConfiguration().uuid));
        }

        changes.change(ObjectChangeType.INSTANCE_GROUP, igm.getKey(),
                Map.of(ObjectChangeDetails.CHANGE_HINT, ObjectChangeHint.SERVERS));

        return attached;
    }

    @Override
    public List<ProductDto> listProducts(String groupName, String serverName) {
        RemoteService svc = getConfiguredRemote(groupName, serverName);
        return ResourceProvider.getVersionedResource(svc, InstanceGroupResource.class, context).getProductResource(groupName)
                .list(null);
    }

    @Override
    public void transferProducts(String groupName, ProductTransferDto transfer) {
        transfers.initTransfer(getInstanceGroupHive(groupName), groupName, transfer);
    }

    @Override
    public SortedSet<ProductDto> getActiveTransfers(String groupName) {
        return transfers.getActiveTransfers(groupName);
    }

    private MinionUpdateDto getUpdates(RemoteService svc) {
        CommonRootResource root = ResourceProvider.getResource(svc, CommonRootResource.class, context);
        Version managedVersion = root.getVersion();
        Version runningVersion = VersionHelper.getVersion();

        // Determine whether or not an update must be installed
        MinionUpdateDto updateDto = new MinionUpdateDto();
        updateDto.updateVersion = runningVersion;
        updateDto.runningVersion = managedVersion;
        updateDto.updateAvailable = VersionHelper.compare(runningVersion, managedVersion) > 0;
        updateDto.forceUpdate = runningVersion.getMajor() > managedVersion.getMajor();

        // Contact the remote service to find out all installed versions
        Set<ScopedManifestKey> remoteVersions = new HashSet<>();
        try (RemoteBHive rbh = RemoteBHive.forService(svc, null, reporter)) {
            SortedMap<Key, ObjectId> inventory = rbh.getManifestInventory(SoftwareUpdateResource.BDEPLOY_MF_NAME,
                    SoftwareUpdateResource.LAUNCHER_MF_NAME);
            inventory.keySet().stream().forEach(key -> remoteVersions.add(ScopedManifestKey.parse(key)));
        }

        // Determine what is available in our hive
        Set<ScopedManifestKey> localVersion = new HashSet<>();
        localVersion.addAll(getLocalPackage(SoftwareUpdateResource.BDEPLOY_MF_NAME));
        localVersion.addAll(getLocalPackage(SoftwareUpdateResource.LAUNCHER_MF_NAME));

        // Compute what is missing and what needs to be installed
        updateDto.packagesToInstall = localVersion.stream().map(ScopedManifestKey::getKey).collect(Collectors.toList());
        localVersion.removeAll(remoteVersions);
        updateDto.packagesToTransfer = localVersion.stream().map(ScopedManifestKey::getKey).collect(Collectors.toList());

        return updateDto;
    }

    @Override
    public void transferUpdate(String groupName, String serverName, MinionUpdateDto updates) {
        // Avoid pushing all manifest if we do not specify what to transfer
        if (updates.packagesToTransfer.isEmpty()) {
            return;
        }

        RemoteService svc = getConfiguredRemote(groupName, serverName);

        // Push them to the default hive of the target server
        PushOperation push = new PushOperation();
        updates.packagesToTransfer.forEach(push::addManifest);

        // Updates are stored in the local default hive
        BHive defaultHive = registry.get(JerseyRemoteBHive.DEFAULT_NAME);
        defaultHive.execute(push.setRemote(svc));

        // update the information in the hive.
        BHive hive = getInstanceGroupHive(groupName);
        ManagedMasters mm = new ManagedMasters(hive);
        ManagedMasterDto attached = mm.read().getManagedMaster(serverName);
        attached.update = getUpdates(svc);
        mm.attach(attached, true);
    }

    @Override
    public void installUpdate(String groupName, String serverName, MinionUpdateDto updates) {
        // Only retain server packages in the list of packages to install
        Collection<Key> keys = updates.packagesToInstall;
        Collection<Key> server = keys.stream().filter(UpdateHelper::isBDeployServerKey).collect(Collectors.toList());

        // Determine OS of the master
        Optional<MinionDto> masterDto = getMinionsOfManagedServer(groupName, serverName).values().stream()
                .filter(dto -> dto.master).findFirst();
        if (!masterDto.isPresent()) {
            throw new WebApplicationException("Cannot determine master node");
        }

        // Trigger the update on the master node
        RemoteService svc = getConfiguredRemote(groupName, serverName);
        UpdateHelper.update(svc, server, true, context);

        // update the information in the hive.
        BHive hive = getInstanceGroupHive(groupName);
        ManagedMasters mm = new ManagedMasters(hive);
        ManagedMasterDto attached = mm.read().getManagedMaster(serverName);
        attached.update = getUpdates(svc);
        mm.attach(attached, true);

        // force a new RemoteService instance on next call
        JerseyClientFactory.invalidateCached(svc);
    }

    @Override
    public Version pingServer(String groupName, String serverName) {
        RemoteService svc = getConfiguredRemote(groupName, serverName);
        try {
            BackendInfoResource info = ResourceProvider.getVersionedResource(svc, BackendInfoResource.class, null);
            return info.getVersion().version;
        } catch (Exception e) {
            throw new WebApplicationException("Cannot contact " + serverName, e, Status.GATEWAY_TIMEOUT);
        }
    }

    private Collection<ScopedManifestKey> getLocalPackage(String manifestName) {
        String runningVersion = VersionHelper.getVersion().toString();

        BHive defaultHive = registry.get(JerseyRemoteBHive.DEFAULT_NAME);
        ManifestListOperation operation = new ManifestListOperation().setManifestName(manifestName);
        Set<Key> result = defaultHive.execute(operation);
        return result.stream().map(ScopedManifestKey::parse).filter(smk -> smk.getTag().equals(runningVersion))
                .collect(Collectors.toSet());
    }

    @Override
    public Boolean isDataMigrationRequired(String groupName) {
        BHive hive = getInstanceGroupHive(groupName);

        SortedSet<Key> instances = InstanceManifest.scan(hive, false);
        for (Key key : instances) {
            InstanceManifest im = InstanceManifest.of(hive, key);

            for (Key nodeKey : im.getInstanceNodeManifests().values()) {
                if (hive.execute(new ManifestRefScanOperation().setManifest(nodeKey)).size() > 0) {
                    // it has a manifest reference, which is not allowed... this is the hint that this
                    // node still uses the old scheme which referenced applications. this will destroy
                    // our sync as it would sync applications instead of config only.
                    return Boolean.TRUE;
                }
            }
        }
        return Boolean.FALSE;
    }

    @Override
    public void performDataMigration(String groupName) {
        BHive hive = getInstanceGroupHive(groupName);

        MasterNamedResource master = ResourceProvider.getVersionedResource(minion.getSelf(), MasterRootResource.class, context)
                .getNamedMaster(groupName);

        SortedSet<Key> scan = InstanceManifest.scan(hive, true);

        for (Key k : scan) {
            log.info("Migrating {}, using tag {}", k.getName(), k.getTag());
            InstanceManifest im = InstanceManifest.of(hive, k);

            // dummy update, re-use existing configuration. new manifests will be written without references
            InstanceUpdateDto update = new InstanceUpdateDto(new InstanceConfigurationDto(im.getConfiguration(), null), null);
            Key newKey = master.update(update, k.getTag());

            // now remove all previous versions of the instance (and it's nodes by matching segments of the manifest name.)
            Set<Key> keys = hive.execute(new ManifestListOperation().setManifestName(im.getConfiguration().uuid));

            for (Key any : keys) {
                if (any.getTag().equals(newKey.getTag())) {
                    continue;
                }
                hive.execute(new ManifestDeleteOperation().setToDelete(any));
            }
        }
    }

}
