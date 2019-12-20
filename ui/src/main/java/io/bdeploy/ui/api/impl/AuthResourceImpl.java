package io.bdeploy.ui.api.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ResourceContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;

import io.bdeploy.bhive.util.StorageHelper;
import io.bdeploy.common.security.ApiAccessToken;
import io.bdeploy.interfaces.UserInfo;
import io.bdeploy.jersey.JerseyServer;
import io.bdeploy.ui.api.AuthAdminResource;
import io.bdeploy.ui.api.AuthResource;
import io.bdeploy.ui.api.AuthService;
import io.bdeploy.ui.api.Minion;
import io.bdeploy.ui.dto.CredentialsDto;

public class AuthResourceImpl implements AuthResource {

    @Inject
    @Named(JerseyServer.TOKEN_SIGNER)
    private Function<ApiAccessToken, String> signer;

    @Inject
    private AuthService auth;

    @Inject
    private SecurityContext context;

    @Inject
    private Minion minion;

    @Context
    private ResourceContext rc;

    @Override
    public Response authenticate(CredentialsDto cred) {
        UserInfo info = auth.authenticate(cred.user, cred.password);
        if (info != null) {
            ApiAccessToken.Builder token = new ApiAccessToken.Builder().setIssuedTo(cred.user);

            // apply global capabilities. scoped ones are not in the token.
            info.capabilities.stream().filter(c -> c.scope == null).forEach(token::addCapability);

            String st = signer.apply(token.build());

            // cookie not set to 'secure' to allow sending during development.
            return Response.ok().cookie(new NewCookie("st", st, "/", null, null, 365, false)).entity(st).build();
        } else {
            throw new WebApplicationException("Invalid credentials", Status.UNAUTHORIZED);
        }
    }

    @Override
    public List<String> getRecentlyUsedInstanceGroups() {
        String user = context.getUserPrincipal().getName();
        List<String> reversed = new ArrayList<>(auth.getRecentlyUsedInstanceGroups(user));
        Collections.reverse(reversed);
        return reversed;
    }

    @Override
    public UserInfo getCurrentUser() {
        UserInfo info = auth.getUser(context.getUserPrincipal().getName());

        // dumb deep clone by JSON round-trip here - otherwise we update the cached in memory object.
        UserInfo clone = StorageHelper.fromRawBytes(StorageHelper.toRawBytes(info), UserInfo.class);
        clone.password = null;
        return clone;
    }

    @Override
    public void updateCurrentUser(UserInfo info) {
        if (info.password != null && !info.password.isBlank()) {
            auth.updateLocalPassword(info.name, info.password);
        }
        auth.updateUserInfo(info);
    }

    @Override
    public String getAuthPack() {
        return minion.createToken(context.getUserPrincipal().getName(), false);
    }

    @Override
    public AuthAdminResource getAdmin() {
        return rc.initResource(new AuthAdminResourceImpl());
    }

}
