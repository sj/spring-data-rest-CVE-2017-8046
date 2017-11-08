/*
 * Copyright 2016-2017 original author or authors.
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
package org.springframework.data.rest.webmvc.json;

import static org.assertj.core.api.Assertions.*;

import org.junit.Test;
import org.springframework.data.annotation.Transient;
import org.springframework.data.keyvalue.core.mapping.context.KeyValueMappingContext;
import org.springframework.data.mapping.PersistentEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link MappedProperties}.
 * 
 * @author Oliver Gierke
 */
public class MappedPropertiesUnitTests {

	ObjectMapper mapper = new ObjectMapper();
	KeyValueMappingContext<?, ?> context = new KeyValueMappingContext<>();
	PersistentEntity<?, ?> entity = context.getRequiredPersistentEntity(Sample.class);
	MappedProperties properties = MappedProperties.fromJacksonProperties(entity, mapper);

	@Test // DATAREST-575
	public void doesNotExposeMappedPropertyForNonSpringDataPersistentProperty() {

		assertThat(properties.hasPersistentPropertyForField("notExposedBySpringData")).isFalse();
		assertThat(properties.getPersistentProperty("notExposedBySpringData")).isNull();
	}

	@Test // DATAREST-575
	public void doesNotExposeMappedPropertyForNonJacksonProperty() {

		assertThat(properties.hasPersistentPropertyForField("notExposedByJackson")).isFalse();
		assertThat(properties.getPersistentProperty("notExposedByJackson")).isNull();
	}

	@Test // DATAREST-575
	public void exposesProperty() {

		assertThat(properties.hasPersistentPropertyForField("exposedProperty")).isTrue();
		assertThat(properties.getPersistentProperty("exposedProperty")).isNotNull();
	}

	@Test // DATAREST-575
	public void exposesRenamedPropertyByExternalName() {

		assertThat(properties.hasPersistentPropertyForField("email")).isTrue();
		assertThat(properties.getPersistentProperty("email")).isNotNull();
		assertThat(properties.getMappedName(entity.getRequiredPersistentProperty("emailAddress"))).isEqualTo("email");
	}

	static class Sample {

		public @Transient String notExposedBySpringData;
		public @JsonIgnore String notExposedByJackson;
		public String exposedProperty;
		public @JsonProperty("email") String emailAddress;
	}
}
