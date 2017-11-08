/*
 * Copyright 2014-2017 the original author or authors.
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
package org.springframework.data.rest.webmvc.alps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import net.minidev.json.JSONArray;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.rest.core.config.RepositoryRestConfiguration;
import org.springframework.data.rest.tests.AbstractControllerIntegrationTests;
import org.springframework.data.rest.tests.TestMvcClient;
import org.springframework.data.rest.webmvc.ProfileController;
import org.springframework.data.rest.webmvc.RestMediaTypes;
import org.springframework.data.rest.webmvc.config.RepositoryRestConfigurerAdapter;
import org.springframework.data.rest.webmvc.jpa.Item;
import org.springframework.data.rest.webmvc.jpa.JpaRepositoryConfig;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.LinkDiscoverer;
import org.springframework.hateoas.LinkDiscoverers;
import org.springframework.hateoas.core.JsonPathLinkDiscoverer;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

/**
 * Integration tests for {@link AlpsController}.
 * 
 * @author Oliver Gierke
 * @author Greg Turnquist
 */
@WebAppConfiguration
@ContextConfiguration(classes = { JpaRepositoryConfig.class, AlpsControllerIntegrationTests.Config.class })
public class AlpsControllerIntegrationTests extends AbstractControllerIntegrationTests {

	@Autowired WebApplicationContext context;
	@Autowired LinkDiscoverers discoverers;
	@Autowired RepositoryRestConfiguration configuration;

	@Configuration
	static class Config extends RepositoryRestConfigurerAdapter {

		@Bean
		public LinkDiscoverer alpsLinkDiscoverer() {
			return new JsonPathLinkDiscoverer("$.descriptors[?(@.name == '%s')].href",
					MediaType.valueOf("application/alps+json"));
		}

		@Override
		public void configureRepositoryRestConfiguration(RepositoryRestConfiguration config) {
			config.exposeIdsFor(Item.class);
		}
	}

	TestMvcClient client;

	@Before
	public void setUp() {

		MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
		this.client = new TestMvcClient(mvc, this.discoverers);
	}

	@After
	public void tearDown() {
		configuration.setEnableEnumTranslation(false);
	}

	@Test // DATAREST-230
	public void exposesAlpsCollectionResources() throws Exception {

		Link profileLink = client.discoverUnique("profile");
		Link peopleLink = client.discoverUnique(profileLink, "people", MediaType.ALL);

		client.follow(peopleLink, RestMediaTypes.ALPS_JSON)//
				.andExpect(jsonPath("$.alps.version").value("1.0"))//
				.andExpect(jsonPath("$.alps.descriptors[*].name", hasItems("people", "person")));
	}

	@Test // DATAREST-638
	public void verifyThatAlpsIsDefaultProfileFormat() throws Exception {

		Link profileLink = client.discoverUnique("profile");
		Link peopleLink = client.discoverUnique(profileLink, "people", MediaType.ALL);

		client.follow(peopleLink)//
				.andExpect(jsonPath("$.alps.version").value("1.0"))//
				.andExpect(jsonPath("$.alps.descriptors[*].name", hasItems("people", "person")));

	}

	@Test // DATAREST-463
	public void verifyThatAttributesIgnoredDontAppearInAlps() throws Exception {

		Link profileLink = client.discoverUnique("profile");
		Link itemsLink = client.discoverUnique(profileLink, "items", MediaType.ALL);

		client.follow(itemsLink, RestMediaTypes.ALPS_JSON)//
				// Exposes standard property
				.andExpect(jsonPath("$.alps.descriptors[*].descriptors[*].name", hasItems("name")))
				// Does not expose explicitly @JsonIgnored property
				.andExpect(jsonPath("$.alps.descriptors[*].descriptors[*].name", not(hasItems("owner"))))
				// Does not expose properties pointing to non exposed types
				.andExpect(jsonPath("$.alps.descriptors[*].descriptors[*].name", not(hasItems("manager", "curator"))));
	}

	@Test // DATAREST-494
	public void linksToJsonSchemaFromRepresentationDescriptor() throws Exception {

		Link profileLink = client.discoverUnique("profile");
		Link itemsLink = client.discoverUnique(profileLink, "items", MediaType.ALL);

		assertThat(itemsLink).isNotNull();

		String result = client.follow(itemsLink, RestMediaTypes.ALPS_JSON).andReturn().getResponse().getContentAsString();
		String href = JsonPath.<JSONArray> read(result, "$.alps.descriptors[?(@.id == 'item-representation')].href").get(0)
				.toString();

		assertThat(href, endsWith("/profile/items"));
	}

	@Test // DATAREST-516
	public void referenceToAssociatedEntityDesciptorPointsToRepresentationDescriptor() throws Exception {

		Link profileLink = client.discoverUnique("profile");
		Link usersLink = client.discoverUnique(profileLink, "people", MediaType.ALL);

		String jsonPath = "$.alps."; // Root
		jsonPath += "descriptors[?(@.id == 'person-representation')]."; // Representation descriptor
		jsonPath += "descriptors[?(@.name == 'father')]."; // First father descriptor
		jsonPath += "rt"; // Return type

		String result = client.follow(usersLink, RestMediaTypes.ALPS_JSON).andReturn().getResponse().getContentAsString();
		String rt = JsonPath.<JSONArray> read(result, jsonPath).get(0).toString();

		assertThat(rt).contains(ProfileController.PROFILE_ROOT_MAPPING).endsWith("-representation");
	}

	@Test // DATAREST-630
	public void onlyExposesIdAttributesWhenExposedInTheConfiguration() throws Exception {

		Link profileLink = client.discoverUnique("profile");
		Link itemsLink = client.discoverUnique(profileLink, "items", MediaType.ALL);

		client.follow(itemsLink, RestMediaTypes.ALPS_JSON)//
				// Exposes identifier if configured to
				.andExpect(jsonPath("$.alps.descriptors[*].descriptors[*].name", hasItems("id", "name")));
	}

	@Test // DATAREST-683
	public void enumValueListingsAreTranslatedIfEnabled() throws Exception {

		configuration.setEnableEnumTranslation(true);

		Link profileLink = client.discoverUnique("profile");
		Link peopleLink = client.discoverUnique(profileLink, "people", MediaType.ALL);

		String result = client.follow(peopleLink).andReturn().getResponse().getContentAsString();

		String value = JsonPath
				.<JSONArray> read(result,
						"$.alps.descriptors[?(@.id == 'person-representation')].descriptors[?(@.name == 'gender')].doc.value")
				.get(0).toString();

		assertThat(value).isEqualTo("Male, Female, Undefined");
	}

	@Test // DATAREST-753
	public void alpsCanHandleGroovyDomainObjects() throws Exception {

		Link profileLink = client.discoverUnique("profile");
		Link groovyDomainObjectLink = client.discoverUnique(profileLink, "simulatedGroovyDomainClasses");

		String result = client.follow(groovyDomainObjectLink).andReturn().getResponse().getContentAsString();

		String name = JsonPath
				.<JSONArray> read(result,
						"$.alps.descriptors[?(@.id == 'simulatedGroovyDomainClass-representation')].descriptors[0].name")
				.get(0).toString();

		assertThat(name).isEqualTo("name");
	}
}
