/*
 * Copyright 2011 UnboundID Corp.
 * All Rights Reserved.
 */

package com.unboundid.scim.sdk;

import com.unboundid.scim.schema.ResourceDescriptor;

import java.net.URI;



/**
 * This class represents a SCIM Get Resources request to retrieve selected
 * resources.
 */
public final class GetResourcesRequest extends SCIMRequest
{
  /**
   * The filter parameters of the request.
   */
  private final SCIMFilter filter;

  /**
   * The sorting parameters of the request.
   */
  private final SortParameters sortParameters;

  /**
   * The pagination parameters of the request.
   */
  private final PageParameters pageParameters;

  /**
   * The set of requested attributes.
   */
  private final SCIMQueryAttributes attributes;



  /**
   * Create a new SCIM Get Resource request from the provided information.
   *
   * @param baseURL               The base URL for the SCIM service.
   * @param authenticatedUserID   The authenticated user name or {@code null} if
   *                              the request is not authenticated.
   * @param resourceDescriptor    The ResourceDescriptor associated with this
   *                              request.
   * @param filter                The filter parameters of the request.
   * @param sortParameters        The sorting parameters of the request.
   * @param pageParameters        The pagination parameters of the request.
   * @param attributes            The set of requested attributes.
   */
  public GetResourcesRequest(final URI baseURL,
                             final String authenticatedUserID,
                             final ResourceDescriptor resourceDescriptor,
                             final SCIMFilter filter,
                             final SortParameters sortParameters,
                             final PageParameters pageParameters,
                             final SCIMQueryAttributes attributes)
  {
    super(baseURL, authenticatedUserID, resourceDescriptor);
    this.filter         = filter;
    this.sortParameters = sortParameters;
    this.pageParameters = pageParameters;
    this.attributes     = attributes;
  }



  /**
   * Retrieve the filter parameters of the request.
   *
   * @return  The filter parameters of the request.
   */
  public SCIMFilter getFilter()
  {
    return filter;
  }



  /**
   * Retrieve the sorting parameters of the request.
   *
   * @return  The sorting parameters of the request.
   */
  public SortParameters getSortParameters()
  {
    return sortParameters;
  }



  /**
   * Retrieve the pagination parameters of the request.
   *
   * @return  The pagination parameters of the request.
   */
  public PageParameters getPageParameters()
  {
    return pageParameters;
  }



  /**
   * Retrieve the set of requested attributes.
   *
   * @return  The set of requested attributes.
   */
  public SCIMQueryAttributes getAttributes()
  {
    return attributes;
  }
}