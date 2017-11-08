/*
 * Copyright 2015-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.rest.core.mapping;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.data.mapping.context.PersistentEntities;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * {@link ResourceMappings} for {@link PersistentEntities}.
 * 
 * @author Oliver Gierke
 * @author Mark Paluch
 */
public class PersistentEntitiesResourceMappings implements ResourceMappings {

	private final PersistentEntities entities;
	private final SearchResourceMappings searchResourceMappings = new SearchResourceMappings(
			Collections.<MethodResourceMapping> emptyList());

	private final Map<Class<?>, ResourceMetadata> cache = new HashMap<Class<?>, ResourceMetadata>();
	private final Map<Class<?>, MappingResourceMetadata> mappingCache = new HashMap<Class<?>, MappingResourceMetadata>();
	private final Map<PersistentProperty<?>, ResourceMapping> propertyCache = new HashMap<PersistentProperty<?>, ResourceMapping>();

	/**
	 * Creates a new {@link PersistentEntitiesResourceMappings} from the given {@link PersistentEntities}.
	 * 
	 * @param entities must not be {@literal null}.
	 */
	public PersistentEntitiesResourceMappings(PersistentEntities entities) {
		this.entities = entities;
	}

	/* 
	 * (non-Javadoc)
	 * @see org.springframework.data.rest.core.mapping.ResourceMappings#getMappingFor(java.lang.Class)
	 */
	@Override
	public ResourceMetadata getMetadataFor(Class<?> type) {

		Assert.notNull(type, "Type must not be null!");

		type = ClassUtils.getUserClass(type);

		if (cache.containsKey(type)) {
			return cache.get(type);
		}

		MappingResourceMetadata metadata = getMappingMetadataFor(type);
		cache.put(type, metadata);

		return metadata;
	}

	/**
	 * Returns the {@link MappingResourceMetadata} for the given type.
	 * 
	 * @param type must not be {@literal null}.
	 * @return the {@link MappingResourceMetadata} if the given type is a {@link PersistentEntity}, {@literal null}
	 *         otherwise.
	 */
	MappingResourceMetadata getMappingMetadataFor(Class<?> type) {

		Assert.notNull(type, "Type must not be null!");
		Class<?> userType = ClassUtils.getUserClass(type);

		MappingResourceMetadata mappingMetadata = mappingCache.get(userType);

		if (mappingMetadata != null) {
			return mappingMetadata;
		}

		Optional<PersistentEntity<?, ? extends PersistentProperty<?>>> entity = entities.getPersistentEntity(userType);

		return entity.map(it -> {

			MappingResourceMetadata metadata = new MappingResourceMetadata(it, this);
			mappingCache.put(userType, metadata);
			return metadata;

		}).orElse(null);
	}

	/* 
	 * (non-Javadoc)
	 * @see org.springframework.data.rest.core.mapping.ResourceMappings#getSearchResourceMappings(java.lang.Class)
	 */
	@Override
	public SearchResourceMappings getSearchResourceMappings(Class<?> domainType) {
		return searchResourceMappings;
	}

	/* 
	 * (non-Javadoc)
	 * @see org.springframework.data.rest.core.mapping.ResourceMappings#exportsMappingFor(java.lang.Class)
	 */
	@Override
	public boolean exportsMappingFor(Class<?> type) {

		if (!hasMappingFor(type)) {
			return false;
		}

		ResourceMetadata metadata = getMetadataFor(type);
		return metadata.isExported();
	}

	/* 
	 * (non-Javadoc)
	 * @see org.springframework.data.rest.core.mapping.ResourceMappings#exportsTopLevelResourceFor(java.lang.String)
	 */
	@Override
	public boolean exportsTopLevelResourceFor(String path) {

		Assert.hasText(path, "Path must not be null or empty!");

		for (ResourceMetadata metadata : this) {
			if (metadata.getPath().matches(path)) {
				return metadata.isExported();
			}
		}

		return false;
	}

	/* 
	 * (non-Javadoc)
	 * @see org.springframework.data.rest.core.mapping.ResourceMappings#hasMappingFor(java.lang.Class)
	 */
	@Override
	public boolean hasMappingFor(Class<?> type) {

		if (cache.containsKey(type)) {
			return true;
		}

		return false;
	}

	/* 
	 * (non-Javadoc)
	 * @see org.springframework.data.rest.core.mapping.ResourceMetadataProvider#getMappingFor(org.springframework.data.mapping.PersistentProperty)
	 */
	public ResourceMapping getMappingFor(PersistentProperty<?> property) {

		ResourceMapping propertyMapping = propertyCache.get(property);

		if (propertyMapping != null) {
			return propertyMapping;
		}

		propertyMapping = new PersistentPropertyResourceMapping(property, this);

		propertyCache.put(property, propertyMapping);

		return propertyMapping;
	}

	public boolean isMapped(PersistentProperty<?> property) {

		ResourceMapping metadata = getMappingFor(property);
		return metadata != null && metadata.isExported();
	}

	/* 
	 * (non-Javadoc)
	 * @see java.lang.Iterable#iterator()
	 */
	@Override
	public Iterator<ResourceMetadata> iterator() {

		Set<ResourceMetadata> metadata = new HashSet<ResourceMetadata>();

		for (ResourceMetadata candidate : cache.values()) {
			if (candidate != null) {
				metadata.add(candidate);
			}
		}

		return metadata.iterator();
	}

	/**
	 * Adds the given {@link ResourceMetadata} to the cache.
	 * 
	 * @param type must not be {@literal null}.
	 * @param metadata can be {@literal null}.
	 */
	protected final void addToCache(Class<?> type, ResourceMetadata metadata) {
		cache.put(type, metadata);
	}

	/**
	 * Returns whether we currently already have {@link ResourceMetadata} for the given type.
	 * 
	 * @param type must not be {@literal null}.
	 * @return
	 */
	protected final boolean hasMetadataFor(Class<?> type) {
		return cache.containsKey(type);
	}
}
