/*
 * Copyright 2011 UnboundID Corp.
 * All Rights Reserved.
 */

package com.unboundid.scim.sdk;

import com.unboundid.scim.schema.ResourceDescriptor;

import java.net.URI;



/**
 * This class represents a SCIM Get Resource request to retrieve all or
 * selected attributes from a single resource.
 */
public final class GetResourceRequest extends SCIMRequest
{
  /**
   * The requested resource ID.
   */
  private final String resourceID;

  /**
   * The set of requested attributes.
   */
  private final SCIMQueryAttributes attributes;



  /**
   * Create a new SCIM Get Resource request from the provided information.
   *
   * @param baseURL              The base URL for the SCIM service.
   * @param authenticatedUserID  The authenticated user name or {@code null} if
   *                             the request is not authenticated.
   * @param resourceDescriptor   The ResourceDescriptor associated with this
   *                             request.
   * @param resourceID           The requested resource ID.
   * @param attributes           The set of requested attributes.
   */
  public GetResourceRequest(final URI baseURL,
                            final String authenticatedUserID,
                            final ResourceDescriptor resourceDescriptor,
                            final String resourceID,
                            final SCIMQueryAttributes attributes)
  {
    super(baseURL, authenticatedUserID, resourceDescriptor);
    this.resourceID          = resourceID;
    this.attributes          = attributes;
  }



  /**
   * Get the requested resource ID.
   *
   * @return  The requested resource ID.
   */
  public String getResourceID()
  {
    return resourceID;
  }



  /**
   * Get the set of requested attributes.
   *
   * @return  The set of requested attributes.
   */
  public SCIMQueryAttributes getAttributes()
  {
    return attributes;
  }
}