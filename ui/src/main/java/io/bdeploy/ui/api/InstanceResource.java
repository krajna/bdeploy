package io.bdeploy.ui.api;

import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration;
import io.bdeploy.interfaces.configuration.instance.InstanceConfiguration.InstancePurpose;
import io.bdeploy.interfaces.descriptor.client.ClientDescriptor;
import io.bdeploy.jersey.ActivityScope;
import io.bdeploy.jersey.JerseyAuthenticationProvider.Unsecured;
import io.bdeploy.ui.dto.DeploymentStateDto;
import io.bdeploy.ui.dto.InstanceConfigurationDto;
import io.bdeploy.ui.dto.InstanceNodeConfigurationListDto;
import io.bdeploy.ui.dto.InstanceVersionDto;

@Path("/")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface InstanceResource {

    @GET
    public List<InstanceConfiguration> list();

    @GET
    @Path("/{instance}/versions")
    public List<InstanceVersionDto> listVersions(@ActivityScope @PathParam("instance") String instanceId);

    @PUT
    public void create(InstanceConfiguration config);

    @GET
    @Path("/{instance}")
    public InstanceConfiguration read(@ActivityScope @PathParam("instance") String instanceId);

    @GET
    @Path("/{instance}/{versionTag}")
    public InstanceConfiguration readVersion(@ActivityScope @PathParam("instance") String instanceId,
            @PathParam("versionTag") String versionTag);

    @POST
    @Path("/{instance}")
    public void update(@ActivityScope @PathParam("instance") String instanceId, InstanceConfigurationDto config,
            @QueryParam("expect") String expectedTag);

    @DELETE
    @Path("/{instance}")
    public void delete(@ActivityScope @PathParam("instance") String instanceId);

    @GET
    @Path("/{instance}/{tag}/nodeConfiguration")
    public InstanceNodeConfigurationListDto getNodeConfigurations(@ActivityScope @PathParam("instance") String instanceId,
            @PathParam("tag") String versionTag);

    @GET
    @Path("/{instance}/{tag}/install")
    public void install(@ActivityScope @PathParam("instance") String instanceId, @ActivityScope @PathParam("tag") String tag);

    @GET
    @Path("/{instance}/{tag}/uninstall")
    public void uninstall(@ActivityScope @PathParam("instance") String instanceId, @ActivityScope @PathParam("tag") String tag);

    @GET
    @Path("/{instance}/{tag}/activate")
    public void activate(@ActivityScope @PathParam("instance") String instanceId, @ActivityScope @PathParam("tag") String tag);

    @GET
    @Path("/{instance}/state")
    public DeploymentStateDto getDeploymentStates(@ActivityScope @PathParam("instance") String instanceId);

    @GET
    @Path("/{instance}/launcher/{processId}")
    public ClientDescriptor getNewClientLauncherDescriptor(@PathParam("instance") String instance,
            @PathParam("processId") String processId);

    @GET
    @Path("/purposes")
    public List<InstancePurpose> getPurposes();

    @Path("/{instance}/processes")
    public ProcessResource getProcessResource(@ActivityScope @PathParam("instance") String instanceId);

    @Path("/{instance}/cfgFiles")
    public ConfigFileResource getConfigResource(@ActivityScope @PathParam("instance") String instanceId);

    @GET
    @Path("/{instance}/installerZip/{processId}")
    @Produces(MediaType.TEXT_PLAIN)
    public String createClientInstaller(@ActivityScope @PathParam("instance") String instanceId,
            @PathParam("processId") String processId);

    @GET
    @Unsecured
    @Path("/{instance}/installerDownload/{processId}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadClientInstaller(@ActivityScope @PathParam("instance") String instanceId,
            @PathParam("processId") String processId);

}
