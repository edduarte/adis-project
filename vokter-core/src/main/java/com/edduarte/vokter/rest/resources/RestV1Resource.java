/*
 * Copyright 2015 Ed Duarte
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.edduarte.vokter.rest.resources;

import com.edduarte.vokter.Context;
import com.edduarte.vokter.model.CancelRequest;
import com.edduarte.vokter.model.CommonResponse;
import com.edduarte.vokter.model.SubscribeRequest;
import com.google.common.collect.Lists;
import org.apache.commons.validator.routines.UrlValidator;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * REST Resource for calls on path "/vokter/v1/".
 *
 * @author Eduardo Duarte (<a href="mailto:hello@edduarte.com">hello@edduarte.com</a>)
 * @version 1.4.1
 * @since 1.0.0
 */
@Path("/v1/")
public class RestV1Resource {

    @GET
    @Path("exampleRequest")
    @Produces(MediaType.APPLICATION_JSON)
    public Response exampleRequest() {
        SubscribeRequest requestBody = new SubscribeRequest(
                "http://www.example.com",
                "http://your.site/client-rest-api",
                Lists.newArrayList("vokter", "vokter panoptes"),
                600,
                false,
                false
        );
        return Response.status(200)
                .type(MediaType.APPLICATION_JSON)
                .entity(requestBody.toString())
                .build();
    }


    @GET
    @Path("exampleResponse")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response exampleResponse() {
        CommonResponse responseBody = new CommonResponse(0, "");
        return Response.status(200)
                .type(MediaType.APPLICATION_JSON)
                .entity(responseBody.toString())
                .build();
    }


    @POST
    @Path("subscribe")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response watch(SubscribeRequest subscribeRequest) {
        String[] schemes = {"http", "https"};
        UrlValidator urlValidator = new UrlValidator(schemes, UrlValidator.ALLOW_LOCAL_URLS);

        String documentUrl = subscribeRequest.getDocumentUrl();
        if (documentUrl == null || documentUrl.isEmpty() ||
                !urlValidator.isValid(documentUrl)) {
            CommonResponse responseBody = new CommonResponse(1,
                    "The provided document URL is invalid.");
            return Response.status(400)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(responseBody.toString())
                    .build();
        }

        String clientUrl = subscribeRequest.getClientUrl();
        if (clientUrl == null || clientUrl.isEmpty() ||
                !urlValidator.isValid(clientUrl)) {
            CommonResponse responseBody = new CommonResponse(2,
                    "The provided client URL is invalid.");
            return Response.status(400)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(responseBody.toString())
                    .build();
        }

        List<String> keywords = subscribeRequest.getKeywords();
        if (keywords != null) {
            for (Iterator<String> it = keywords.iterator(); it.hasNext(); ) {
                String k = it.next();
                if (k == null || k.isEmpty()) {
                    it.remove();
                }
            }
        }

        if (keywords == null || keywords.isEmpty()) {
            CommonResponse responseBody = new CommonResponse(3,
                    "You need to provide at least one valid keyword.");
            return Response.status(400)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(responseBody.toString())
                    .build();
        }

        if (subscribeRequest.getIgnoreAdded() &&
                subscribeRequest.getIgnoreRemoved()) {
            CommonResponse responseBody = new CommonResponse(4,
                    "At least one difference action (added or " +
                            "removed) must not be ignored.");
            return Response.status(400)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(responseBody.toString())
                    .build();
        }

        Context context = Context.getInstance();
        boolean created = context.createJob(subscribeRequest);
        if (created) {
            CommonResponse responseBody = new CommonResponse(0,
                    ""
            );
            return Response.status(200)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(responseBody.toString())
                    .build();
        } else {
            CommonResponse responseBody = new CommonResponse(5,
                    "The request conflicts with a currently active watch " +
                            "job, since the provided document URL is " +
                            "already being watched and notified to the " +
                            "provided client URL.");
            return Response.status(409)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(responseBody.toString())
                    .build();
        }
    }


    @POST
    @Path("cancel")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response cancel(CancelRequest cancelRequest) throws ExecutionException {

        Context context = Context.getInstance();
        boolean wasDeleted = context.cancelJob(
                cancelRequest.getDocumentUrl(),
                cancelRequest.getClientUrl()
        );
        if (wasDeleted) {
            CommonResponse responseBody = new CommonResponse(0, "");
            return Response.status(200)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(responseBody.toString())
                    .build();
        } else {
            CommonResponse responseBody = new CommonResponse(7,
                    "The specified job to cancel does not exist.");
            return Response.status(404)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(responseBody.toString())
                    .build();
        }
    }
}