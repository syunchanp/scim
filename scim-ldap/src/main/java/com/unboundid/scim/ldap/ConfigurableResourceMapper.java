/*
 * Copyright 2011 UnboundID Corp.
 * All Rights Reserved.
 */

package com.unboundid.scim.ldap;

import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Control;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPInterface;
import com.unboundid.ldap.sdk.Modification;
import com.unboundid.ldap.sdk.controls.ServerSideSortRequestControl;
import com.unboundid.ldap.sdk.controls.SortKey;
import com.unboundid.scim.schema.AttributeDescriptor;
import com.unboundid.scim.sdk.Debug;
import com.unboundid.scim.sdk.SCIMAttribute;
import com.unboundid.scim.sdk.SCIMAttributeType;
import com.unboundid.scim.sdk.SCIMException;
import com.unboundid.scim.sdk.SCIMObject;
import com.unboundid.scim.sdk.SCIMQueryAttributes;
import com.unboundid.scim.sdk.SCIMFilter;
import com.unboundid.scim.sdk.SCIMFilterType;
import com.unboundid.scim.sdk.ServerErrorException;
import com.unboundid.scim.sdk.SortParameters;
import com.unboundid.scim.sdk.AttributePath;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI;



/**
 * This class provides a resource mapper whose behavior can be specified in
 * configuration.
 */
public class ConfigurableResourceMapper extends ResourceMapper
{
  /**
   * The name of the SCIM resource handled by this resource mapper.
   */
  private String resourceName;

  /**
   * The query endpoint for resources handled by this resource mapper.
   */
  private String queryEndpoint;

  /**
   * The LDAP Search base DN to be used for querying.
   */
  private String searchBaseDN;

  /**
   * The LDAP filter to match all resources handled by this resource manager.
   */
  private Filter searchFilter;

  /**
   * The LDAP Add parameters.
   */
  private LDAPAddParameters addParameters;

  /**
   * A DN constructed value for the DN template.
   */
  private ConstructedValue dnConstructor;

  /**
   * The attribute mappers for this resource mapper.
   */
  private Map<AttributeDescriptor, AttributeMapper> attributeMappers;

  /**
   * The set of LDAP attributes used by this resource mapper.
   */
  private Set<String> ldapAttributeTypes;

  /**
   * The derived attributes for this resource mapper.
   */
  private Map<AttributeDescriptor,DerivedAttribute> derivedAttributes;



  /**
   * Create a new instance of the resource mapper.
   *
   * @param resourceName       The name of the SCIM resource handled by this
   *                           resource mapper.
   * @param queryEndpoint      The  query endpoint for resources handled by this
   *                           resource mapper.
   * @param searchBaseDN       The LDAP Search base DN.
   * @param searchFilter       The LDAP Search filter.
   * @param addParameters      The LDAP Add parameters.
   * @param mappers            The attribute mappers for this resource mapper.
   * @param derivedAttributes  The derived attributes for this resource mapper.
   */
  public ConfigurableResourceMapper(
      final String resourceName,
      final String queryEndpoint,
      final String searchBaseDN,
      final String searchFilter,
      final LDAPAddParameters addParameters,
      final Collection<AttributeMapper> mappers,
      final Collection<DerivedAttribute> derivedAttributes)
  {
    this.resourceName      = resourceName;
    this.queryEndpoint     = queryEndpoint;
    this.addParameters     = addParameters;
    this.searchBaseDN      = searchBaseDN;

    try
    {
      this.searchFilter = Filter.create(searchFilter);
    }
    catch (LDAPException e)
    {
      Debug.debugException(e);
      throw new IllegalArgumentException(e.getExceptionMessage());
    }

    if (addParameters != null)
    {
      this.dnConstructor =
          new ConstructedValue(addParameters.getDNTemplate().trim());
    }

    attributeMappers =
        new HashMap<AttributeDescriptor, AttributeMapper>(mappers.size());
    ldapAttributeTypes = new HashSet<String>();

    for (final AttributeMapper m : mappers)
    {
      attributeMappers.put(m.getAttributeDescriptor(), m);
      ldapAttributeTypes.addAll(m.getLDAPAttributeTypes());
    }

    this.derivedAttributes =
        new HashMap<AttributeDescriptor, DerivedAttribute>(
            derivedAttributes.size());
    for (final DerivedAttribute derivedAttribute : derivedAttributes)
    {
      this.derivedAttributes.put(derivedAttribute.getAttributeDescriptor(),
                                 derivedAttribute);
    }
  }



  /**
   * Parse an XML file defining a set of resource mappings.
   *
   * @param file  An XML file defining a set of resource mappings.
   *
   * @return  A list of resource mappers.
   *
   * @throws JAXBException  If an error occurs during the parsing.
   * @throws SAXException   If the XML schema cannot be instantiated.
   * @throws SCIMException  If some other error occurs.
   */
  public static List<ResourceMapper> parse(final File file)
      throws JAXBException, SAXException, SCIMException
  {
    final ObjectFactory factory = new ObjectFactory();
    final String packageName = factory.getClass().getPackage().getName();
    final JAXBContext context = JAXBContext.newInstance(packageName);

    final Unmarshaller unmarshaller = context.createUnmarshaller();
    final URL url = ResourcesDefinition.class.getResource("resources.xsd");
    if (url != null) {
      final SchemaFactory sf = SchemaFactory.newInstance(W3C_XML_SCHEMA_NS_URI);
      final Schema schema = sf.newSchema(url);
      unmarshaller.setSchema(schema);
    }

    final JAXBElement jaxbElement = (JAXBElement) unmarshaller.unmarshal(file);
    final ResourcesDefinition resources =
        (ResourcesDefinition)jaxbElement.getValue();

    final List<ResourceMapper> resourceMappers =
        new ArrayList<ResourceMapper>();
    for (final ResourceDefinition resource : resources.getResource())
    {
      if (resource.getLDAPSearch() == null)
      {
        continue;
      }

      final List<AttributeMapper> attributeMappers =
          new ArrayList<AttributeMapper>();
      final List<DerivedAttribute> derivedAttributes =
          new ArrayList<DerivedAttribute>();
      for (final AttributeDefinition attributeDefinition :
          resource.getAttribute())
      {
        final AttributeDescriptor attributeDescriptor =
            createAttributeDescriptor(attributeDefinition,
                                      resource.getSchema());

        final AttributeMapper m =
            AttributeMapper.create(attributeDefinition, attributeDescriptor);
        if (m != null)
        {
          attributeMappers.add(m);
        }

        if (attributeDefinition.getDerivation() != null)
        {
          final DerivedAttribute derivedAttribute =
              DerivedAttribute.create(
                  attributeDefinition.getDerivation().getJavaClass());
          derivedAttribute.initialize(attributeDescriptor);
          derivedAttributes.add(derivedAttribute);
        }
      }

      String searchBaseDN = null;
      String searchFilter = null;
      if (resource.getLDAPSearch() != null)
      {
        searchBaseDN = resource.getLDAPSearch().getBaseDN().trim();
        searchFilter = resource.getLDAPSearch().getFilter().trim();
      }

      resourceMappers.add(
          new ConfigurableResourceMapper(resource.getName(),
                                         resource.getQueryEndpoint(),
                                         searchBaseDN,
                                         searchFilter,
                                         resource.getLDAPAdd(),
                                         attributeMappers,
                                         derivedAttributes));
    }

    return resourceMappers;
  }



  @Override
  public void initializeMapper()
  {
    // No implementation required.
  }



  @Override
  public void finalizeMapper()
  {
    // No implementation required.
  }



  @Override
  public String getResourceName()
  {
    return resourceName;
  }



  @Override
  public String getQueryEndpoint()
  {
    return queryEndpoint;
  }



  @Override
  public boolean supportsQuery()
  {
    return searchBaseDN != null;
  }



  @Override
  public boolean supportsCreate()
  {
    return addParameters != null;
  }



  @Override
  public Set<String> toLDAPAttributeTypes(
      final SCIMQueryAttributes queryAttributes)
  {
    final Set<String> ldapAttributes = new HashSet<String>();
    for (final AttributeMapper m : attributeMappers.values())
    {
      if (queryAttributes.isAttributeRequested(m.getAttributeDescriptor()))
      {
        ldapAttributes.addAll(m.getLDAPAttributeTypes());
      }
    }

    for (final Map.Entry<AttributeDescriptor,DerivedAttribute> e :
        derivedAttributes.entrySet())
    {
      if (queryAttributes.isAttributeRequested(e.getKey()))
      {
        final DerivedAttribute derivedAttribute = e.getValue();
        ldapAttributes.addAll(derivedAttribute.getLDAPAttributeTypes());
      }
    }

    return ldapAttributes;
  }



  @Override
  public Entry toLDAPEntry(final SCIMObject scimObject)
      throws LDAPException
  {
    final Entry entry = new Entry("");

    if (addParameters == null)
    {
      throw new RuntimeException(
          "No LDAP Add Parameters were specified for the " + resourceName +
          " Resource Mapper");
    }

    for (final FixedAttribute fixedAttribute :
        addParameters.getFixedAttribute())
    {
      boolean preserveExisting = false;
      final String attributeName = fixedAttribute.getLdapAttribute();
      if (entry.hasAttribute(attributeName))
      {
        switch (fixedAttribute.getOnConflict())
        {
          case MERGE:
            break;
          case OVERWRITE:
            entry.removeAttribute(attributeName);
            break;
          case PRESERVE:
            preserveExisting = true;
            break;
        }
      }

      if (!preserveExisting)
      {
        entry.addAttribute(
            new Attribute(attributeName, fixedAttribute.getFixedValue()));
      }
    }

    for (final Attribute a : toLDAPAttributes(scimObject))
    {
      entry.addAttribute(a);
    }

    // TODO allow SCIM object values to be referenced
    entry.setDN(dnConstructor.constructValue(entry));

    return entry;
  }



  @Override
  public List<Attribute> toLDAPAttributes(final SCIMObject scimObject)
  {
    final List<Attribute> attributes = new ArrayList<Attribute>();

    for (final AttributeMapper attributeMapper : attributeMappers.values())
    {
      attributeMapper.toLDAPAttributes(scimObject, attributes);
    }

    return attributes;
  }



  @Override
  public List<Modification> toLDAPModifications(final Entry currentEntry,
                                                final SCIMObject scimObject)
  {
    final List<Attribute> attributes = toLDAPAttributes(scimObject);
    final Entry entry = new Entry(currentEntry.getDN(), attributes);

    final String[] ldapAttributesArray =
        ldapAttributeTypes.toArray(new String[ldapAttributeTypes.size()]);

    return Entry.diff(currentEntry, entry, true, false, ldapAttributesArray);
  }



  @Override
  public Filter toLDAPFilter(final SCIMFilter filter)
  {
    if (searchFilter == null)
    {
      return null;
    }

    if (filter == null)
    {
      return searchFilter;
    }

    final Filter filterComponent = toLDAPFilterComponent(filter);

    if (filterComponent == null)
    {
      return searchFilter;
    }
    else
    {
      return Filter.createANDFilter(filterComponent, searchFilter);
    }
  }



  @Override
  public String getSearchBaseDN()
  {
    return searchBaseDN;
  }



  /**
   * Map a SCIM filter component to an LDAP filter component.
   *
   * @param filter  The SCIM filter component to be mapped.
   *
   * @return  The LDAP filter component, or {@code null} if the filter
   *          component could not be mapped.
   */
  private Filter toLDAPFilterComponent(final SCIMFilter filter)
  {
    final SCIMFilterType filterType = filter.getFilterType();

    switch (filterType)
    {
      case AND:
        final List<Filter> andFilterComponents = new ArrayList<Filter>();
        for (SCIMFilter f : filter.getFilterComponents())
        {
          final Filter filterComponent = toLDAPFilterComponent(f);
          if (filterComponent != null)
          {
            andFilterComponents.add(filterComponent);
          }
        }
        return Filter.createANDFilter(andFilterComponents);

      case OR:
        final List<Filter> orFilterComponents = new ArrayList<Filter>();
        for (SCIMFilter f : filter.getFilterComponents())
        {
          final Filter filterComponent = toLDAPFilterComponent(f);
          if (filterComponent != null)
          {
            orFilterComponents.add(filterComponent);
          }
        }
        return Filter.createANDFilter(orFilterComponents);

      default:
        final AttributePath filterAttribute = filter.getFilterAttribute();
        // TODO: This should have a reference to the resource descriptor
        AttributeMapper attributeMapper = null;
        for(AttributeDescriptor attributeDescriptor : attributeMappers.keySet())
        {
          if(attributeDescriptor.getSchema().equals(
              filterAttribute.getAttributeSchema()) &&
              attributeDescriptor.getName().equalsIgnoreCase(
                  filterAttribute.getAttributeName()))
          {
            attributeMapper = attributeMappers.get(attributeDescriptor);
          }
        }
        if (attributeMapper != null)
        {
          return attributeMapper.toLDAPFilter(filter);
        }
        break;
    }

    return null;
  }



  @Override
  public Control toLDAPSortControl(final SortParameters sortParameters)
  {
    final SCIMAttributeType scimAttributeType = sortParameters.getSortBy();
    AttributeMapper attributeMapper = null;
    for(AttributeDescriptor attributeDescriptor : attributeMappers.keySet())
    {
      if(attributeDescriptor.getSchema().equals(
          scimAttributeType.getSchema()) &&
          attributeDescriptor.getName().equalsIgnoreCase(
              scimAttributeType.getName()))
      {
        attributeMapper = attributeMappers.get(attributeDescriptor);
      }
    }
    if (attributeMapper == null)
    {
      throw new RuntimeException("Cannot sort by attribute " +
                                 scimAttributeType);
    }

    final String ldapAttribute = attributeMapper.toLDAPSortAttributeType();

    final boolean reverseOrder = !sortParameters.isAscendingOrder();
    return new ServerSideSortRequestControl(
        new SortKey(ldapAttribute, reverseOrder));
  }



  @Override
  public List<SCIMAttribute> toSCIMAttributes(
      final String resourceName,
      final Entry entry,
      final SCIMQueryAttributes queryAttributes,
      final LDAPInterface ldapInterface)
  {
    final List<SCIMAttribute> attributes =
        new ArrayList<SCIMAttribute>();

    for (final AttributeMapper attributeMapper : attributeMappers.values())
    {
      if (queryAttributes.isAttributeRequested(
          attributeMapper.getAttributeDescriptor()))
      {
        final SCIMAttribute attribute = attributeMapper.toSCIMAttribute(entry);
        if (attribute != null)
        {
          attributes.add(attribute);
        }
      }
    }

    if (ldapInterface != null)
    {
      for (final Map.Entry<AttributeDescriptor,DerivedAttribute> e :
          derivedAttributes.entrySet())
      {
        if (queryAttributes.isAttributeRequested(e.getKey()))
        {
          final DerivedAttribute derivedAttribute = e.getValue();
          final SCIMAttribute attribute =
              derivedAttribute.toSCIMAttribute(entry, ldapInterface,
                                               searchBaseDN);
          if (attribute != null)
          {
            attributes.add(attribute);
          }
        }
      }
    }

    return attributes;
  }



  @Override
  public SCIMObject toSCIMObject(final Entry entry,
                                 final SCIMQueryAttributes queryAttributes,
                                 final LDAPInterface ldapInterface)
  {
    if (searchFilter != null)
    {
      try
      {
        if (!searchFilter.matchesEntry(entry))
        {
          return null;
        }
      }
      catch (LDAPException e)
      {
        Debug.debugException(e);
        throw new RuntimeException(e.getExceptionMessage());
      }
    }

    final List<SCIMAttribute> attributes =
        toSCIMAttributes(resourceName, entry,
                         queryAttributes, ldapInterface);

    final SCIMObject scimObject = new SCIMObject();
    for (final SCIMAttribute a : attributes)
    {
      scimObject.addAttribute(a);
    }

    return scimObject;
  }



  /**
   * Create an attribute descriptor from an attribute definition.
   *
   * @param attributeDefinition  The attribute definition.
   * @param resourceSchema       The resource schema URN.
   *
   * @return  A new attribute descriptor.
   *
   * @throws SCIMException  If an error occurs.
   */
  private static AttributeDescriptor createAttributeDescriptor(
      final AttributeDefinition attributeDefinition,
      final String resourceSchema)
      throws SCIMException
  {
    final String schema;
    if (attributeDefinition.getSchema() == null)
    {
      schema = resourceSchema;
    }
    else
    {
      schema = attributeDefinition.getSchema();
    }

    if (attributeDefinition.getSimple() != null)
    {
      final SimpleAttributeDefinition simpleDefinition =
          attributeDefinition.getSimple();

      return AttributeDescriptor.singularSimple(
          attributeDefinition.getName(),
          AttributeDescriptor.DataType.parse(
              simpleDefinition.getDataType().value()),
          attributeDefinition.getDescription(),
          schema,
          attributeDefinition.isReadOnly(),
          attributeDefinition.isRequired(),
          simpleDefinition.isCaseExact());
    }
    else if (attributeDefinition.getComplex() != null)
    {
      final ComplexAttributeDefinition complexDefinition =
          attributeDefinition.getComplex();

      final AttributeDescriptor[] subAttributes =
          new AttributeDescriptor[complexDefinition.getSubAttribute().size()];

      int i = 0;
      for (final SubAttributeDefinition subAttributeDefinition :
          complexDefinition.getSubAttribute())
      {
          subAttributes[i++] = AttributeDescriptor.singularSimple(
                  subAttributeDefinition.getName(),
                  AttributeDescriptor.DataType.parse(
                      subAttributeDefinition.getDataType().value()),
                  subAttributeDefinition.getDescription(),
                  schema,
                  subAttributeDefinition.isReadOnly(),
                  subAttributeDefinition.isRequired(),
                  subAttributeDefinition.isCaseExact());
      }

      return AttributeDescriptor.singularComplex(
          attributeDefinition.getName(),
          attributeDefinition.getDescription(),
          schema,
          attributeDefinition.isReadOnly(),
          attributeDefinition.isRequired(),
          subAttributes);
    }
    else if (attributeDefinition.getSimplePlural() != null)
    {
      final SimplePluralAttributeDefinition simplePluralDefinition =
          attributeDefinition.getSimplePlural();

      final String[] pluralTypes =
          new String[simplePluralDefinition.getPluralType().size()];

      int i = 0;
      for (final PluralType pluralType : simplePluralDefinition.getPluralType())
      {
        pluralTypes[i++] = pluralType.getName();
      }

      return AttributeDescriptor.pluralSimple(
          attributeDefinition.getName(),
          AttributeDescriptor.DataType.parse(
              simplePluralDefinition.getDataType().value()),
          attributeDefinition.getDescription(),
          schema,
          attributeDefinition.isReadOnly(),
          attributeDefinition.isRequired(),
          simplePluralDefinition.isCaseExact(),
          pluralTypes);
    }
    else if (attributeDefinition.getComplexPlural() != null)
    {
      final ComplexPluralAttributeDefinition complexPluralDefinition =
          attributeDefinition.getComplexPlural();

      final String[] pluralTypes =
          new String[complexPluralDefinition.getPluralType().size()];

      int i = 0;
      for (final PluralType pluralType :
          complexPluralDefinition.getPluralType())
      {
        pluralTypes[i++] = pluralType.getName();
      }

      final AttributeDescriptor[] subAttributes =
          new AttributeDescriptor[
              complexPluralDefinition.getSubAttribute().size()];

      i = 0;
      for (final SubAttributeDefinition subAttributeDefinition :
          complexPluralDefinition.getSubAttribute())
      {
          subAttributes[i++] = AttributeDescriptor.singularSimple(
                  subAttributeDefinition.getName(),
                  AttributeDescriptor.DataType.parse(
                      subAttributeDefinition.getDataType().value()),
                  subAttributeDefinition.getDescription(),
                  schema,
                  subAttributeDefinition.isReadOnly(),
                  subAttributeDefinition.isRequired(),
                  subAttributeDefinition.isCaseExact());
      }
      return AttributeDescriptor.pluralComplex(
          attributeDefinition.getName(),
          attributeDefinition.getDescription(),
          schema,
          attributeDefinition.isReadOnly(),
          attributeDefinition.isRequired(),
          pluralTypes, subAttributes);
    }
    else
    {
      final SCIMException e =
          new ServerErrorException(
              "Attribute definition '" + attributeDefinition.getName() +
              "' does not have a simple, complex, simplePlural or " +
              "complexPlural element");
      Debug.debugCodingError(e);
      throw e;
    }
  }
}