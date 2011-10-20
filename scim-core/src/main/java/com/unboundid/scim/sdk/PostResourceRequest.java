/*
 * Copyright 2011 UnboundID Corp.
 * All Rights Reserved.
 */

package com.unboundid.scim.sdk;

import com.unboundid.scim.schema.ResourceDescriptor;

import java.net.URI;



/**
 * This class represents a SCIM Post Resource request to create a new resource.
 */
public final class PostResourceRequest extends SCIMRequest
{
  /**
   * The contents of the resource to be created.
   */
  private final SCIMObject resourceObject;

  /**
   * The set of requested attributes.
   */
  private final SCIMQueryAttributes attributes;



  /**
   * Create a new SCIM Post Resource request from the provided information.
   *
   * @param baseURL              The base URL for the SCIM service.
   * @param authenticatedUserID  The authenticated user name or {@code null} if
   *                             the request is not authenticated.
   * @param resourceDescriptor   The ResourceDescriptor associated with this
   *                             request.
   * @param resourceObject       The contents of the resource to be created.
   * @param attributes           The set of requested attributes.
   */
  public PostResourceRequest(final URI baseURL,
                             final String authenticatedUserID,
                             final ResourceDescriptor resourceDescriptor,
                             final SCIMObject resourceObject,
                             final SCIMQueryAttributes attributes)
  {
    super(baseURL, authenticatedUserID, resourceDescriptor);
    this.resourceObject      = resourceObject;
    this.attributes          = attributes;
  }



  /**
   * Get the contents of the resource to be created.
   *
   * @return  The contents of the resource to be created.
   */
  public SCIMObject getResourceObject()
  {
    return resourceObject;
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