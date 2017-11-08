/*
 * Copyright 2013-2015 the original author or authors.
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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.data.mapping.context.PersistentEntities;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.support.Repositories;
import org.springframework.data.rest.core.config.RepositoryRestConfiguration;
import org.springframework.hateoas.RelProvider;
import org.springframework.hateoas.core.EvoInflectorRelProvider;
import org.springframework.util.Assert;

/**
 * Central abstraction obtain {@link ResourceMetadata} and {@link ResourceMapping} instances for domain types and
 * repositories.
 * 
 * @author Oliver Gierke
 */
public class RepositoryResourceMappings extends PersistentEntitiesResourceMappings {

	private final Repositories repositories;
	private final Map<Class<?>, SearchResourceMappings> searchCache = new HashMap<Class<?>, SearchResourceMappings>();

	/**
	 * Creates a new {@link RepositoryResourceMappings} using the given {@link Repositories} and
	 * {@link PersistentEntities}.
	 * 
	 * @param repositories must not be {@literal null}.
	 * @param entities must not be {@literal null}.
	 * @param strategy must not be {@literal null}.
	 */
	public RepositoryResourceMappings(Repositories repositories, PersistentEntities entities,
			RepositoryDetectionStrategy strategy) {
		this(repositories, entities, strategy, new EvoInflectorRelProvider());
	}

	/**
	 * Creates a new {@link RepositoryResourceMappings} from the given {@link RepositoryRestConfiguration},
	 * {@link Repositories} and {@link RelProvider}.
	 * 
	 * @param repositories must not be {@literal null}.
	 * @param entities must not be {@literal null}.
	 * @param strategy must not be {@literal null}.
	 * @param relProvider must not be {@literal null}.
	 */
	public RepositoryResourceMappings(Repositories repositories, PersistentEntities entities, RepositoryDetectionStrategy strategy,
			RelProvider relProvider) {

		super(entities);

		Assert.notNull(repositories, "Repositories must not be null!");
		Assert.notNull(strategy, "RepositoryDetectionStrategy must not be null!");

		this.repositories = repositories;
		this.populateCache(repositories, relProvider, strategy);
	}

	private final void populateCache(Repositories repositories, RelProvider provider,
			RepositoryDetectionStrategy strategy) {

		for (Class<?> type : repositories) {

			RepositoryInformation repositoryInformation = repositories.getRequiredRepositoryInformation(type);
			Class<?> repositoryInterface = repositoryInformation.getRepositoryInterface();
			PersistentEntity<?, ?> entity = repositories.getPersistentEntity(type);

			CollectionResourceMapping mapping = new RepositoryCollectionResourceMapping(repositoryInformation, strategy,
					provider);
			RepositoryAwareResourceMetadata information = new RepositoryAwareResourceMetadata(entity, mapping, this,
					repositoryInformation);

			addToCache(repositoryInterface, information);

			if (!hasMetadataFor(type) || information.isPrimary()) {
				addToCache(type, information);
			}
		}
	}

	/* 
	 * (non-Javadoc)
	 * @see org.springframework.data.rest.core.mapping.ResourceMappings#getSearchResourceMappings(java.lang.Class)
	 */
	@Override
	public SearchResourceMappings getSearchResourceMappings(Class<?> domainType) {

		Assert.notNull(domainType, "Type must not be null!");

		if (searchCache.containsKey(domainType)) {
			return searchCache.get(domainType);
		}

		RepositoryInformation repositoryInformation = repositories.getRequiredRepositoryInformation(domainType);
		List<MethodResourceMapping> mappings = new ArrayList<MethodResourceMapping>();
		ResourceMetadata resourceMapping = getMetadataFor(domainType);

		if (resourceMapping.isExported()) {
			for (Method queryMethod : repositoryInformation.getQueryMethods()) {
				RepositoryMethodResourceMapping methodMapping = new RepositoryMethodResourceMapping(queryMethod,
						resourceMapping, repositoryInformation);
				if (methodMapping.isExported()) {
					mappings.add(methodMapping);
				}
			}
		}

		SearchResourceMappings searchResourceMappings = new SearchResourceMappings(mappings);
		searchCache.put(domainType, searchResourceMappings);
		return searchResourceMappings;
	}

	/* 
	 * (non-Javadoc)
	 * @see org.springframework.data.rest.core.mapping.ResourceMappings#hasMappingFor(java.lang.Class)
	 */
	@Override
	public boolean hasMappingFor(Class<?> type) {

		if (super.hasMappingFor(type)) {
			return true;
		}

		if (repositories.hasRepositoryFor(type)) {
			return true;
		}

		return false;
	}

	/* 
	 * (non-Javadoc)
	 * @see org.springframework.data.rest.core.mapping.PersistentEntitiesResourceMappings#isMapped(org.springframework.data.mapping.PersistentProperty)
	 */
	@Override
	public boolean isMapped(PersistentProperty<?> property) {
		return repositories.hasRepositoryFor(property.getActualType()) && super.isMapped(property);
	}
}
