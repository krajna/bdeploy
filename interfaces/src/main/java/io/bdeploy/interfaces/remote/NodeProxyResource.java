package io.bdeploy.interfaces.remote;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/proxy")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface NodeProxyResource {

    @POST
    ProxiedResponseWrapper forward(ProxiedRequestWrapper wrapper);

}