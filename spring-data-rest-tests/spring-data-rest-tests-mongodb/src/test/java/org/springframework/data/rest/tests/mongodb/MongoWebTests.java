/*
 * Copyright 2013-2017 the original author or authors.
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
package org.springframework.data.rest.tests.mongodb;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.http.HttpHeaders.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import org.assertj.core.api.Condition;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.tests.CommonWebTests;
import org.springframework.data.rest.webmvc.RestMediaTypes;
import org.springframework.data.rest.webmvc.support.RepositoryEntityLinks;
import org.springframework.hateoas.Link;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

/**
 * Integration tests for MongoDB repositories.
 * 
 * @author Oliver Gierke
 * @author Greg Turnquist
 */
@ContextConfiguration(classes = MongoDbRepositoryConfig.class)
public class MongoWebTests extends CommonWebTests {

	@Autowired ProfileRepository repository;
	@Autowired UserRepository userRepository;
	@Autowired RepositoryEntityLinks entityLinks;

	ObjectMapper mapper = new ObjectMapper();

	@Before
	public void populateProfiles() {

		mapper.setSerializationInclusion(Include.NON_NULL);

		Profile twitter = new Profile();
		twitter.setPerson(1L);
		twitter.setType("Twitter");

		Profile linkedIn = new Profile();
		linkedIn.setPerson(1L);
		linkedIn.setType("LinkedIn");

		repository.saveAll(Arrays.asList(twitter, linkedIn));

		Address address = new Address();
		address.street = "ETagDoesntMatchExceptionUnitTests";
		address.zipCode = "Bar";

		User thomas = new User();
		thomas.firstname = "Thomas";
		thomas.lastname = "Darimont";
		thomas.address = address;

		userRepository.save(thomas);

		User oliver = new User();
		oliver.firstname = "Oliver";
		oliver.lastname = "Gierke";
		oliver.address = address;
		oliver.colleagues = Arrays.asList(thomas);
		userRepository.save(oliver);

		thomas.colleagues = Arrays.asList(oliver);
		userRepository.save(thomas);
	}

	@After
	public void cleanUp() {
		repository.deleteAll();
		userRepository.deleteAll();
	}

	/* 
	 * (non-Javadoc)
	 * @see org.springframework.data.rest.webmvc.AbstractWebIntegrationTests#expectedRootLinkRels()
	 */
	@Override
	protected Iterable<String> expectedRootLinkRels() {
		return Arrays.asList("profiles", "users");
	}

	@Test
	public void foo() throws Exception {

		Link profileLink = client.discoverUnique("profiles");
		client.follow(profileLink).//
				andExpect(jsonPath("$._embedded.profiles").value(hasSize(2)));
	}

	@Test
	public void rendersEmbeddedDocuments() throws Exception {

		Link usersLink = client.discoverUnique("users");
		Link userLink = assertHasContentLinkWithRel("self", client.request(usersLink));
		client.follow(userLink).//
				andExpect(jsonPath("$.address.zipCode").value(is(notNullValue())));
	}

	@Test // DATAREST-247
	public void executeQueryMethodWithPrimitiveReturnType() throws Exception {

		Link profiles = client.discoverUnique("profiles");
		Link profileSearches = client.discoverUnique(profiles, "search");
		Link countByTypeLink = client.discoverUnique(profileSearches, "countByType");

		assertThat(countByTypeLink.isTemplated()).isTrue();
		assertThat(countByTypeLink.getVariableNames()).contains("type");

		MockHttpServletResponse response = client.request(countByTypeLink.expand("Twitter"));
		assertThat(response.getContentAsString()).isEqualTo("1");
	}

	@Test
	public void testname() throws Exception {

		Link usersLink = client.discoverUnique("users");
		Link userLink = assertHasContentLinkWithRel("self", client.request(usersLink));

		MockHttpServletResponse response = patchAndGet(userLink,
				"{\"lastname\" : null, \"address\" : { \"zipCode\" : \"ZIP\"}}",
				org.springframework.http.MediaType.APPLICATION_JSON);

		assertThat(JsonPath.<String> read(response.getContentAsString(), "$.lastname")).isNull();
		assertThat(JsonPath.<String> read(response.getContentAsString(), "$.address.zipCode")).isEqualTo("ZIP");
	}

	@Test
	public void testname2() throws Exception {

		Link usersLink = client.discoverUnique("users");
		Link userLink = assertHasContentLinkWithRel("self", client.request(usersLink));

		MockHttpServletResponse response = patchAndGet(userLink,
				"[{ \"op\": \"replace\", \"path\": \"/address/zipCode\", \"value\": \"ZIP\" },"
						// + "{ \"op\": \"replace\", \"path\": \"/lastname\", \"value\": null }]", //
						+ "{ \"op\": \"remove\", \"path\": \"/lastname\" }]", //
				RestMediaTypes.JSON_PATCH_JSON);

		assertThat(JsonPath.<String> read(response.getContentAsString(), "$.lastname")).isNull();
		assertThat(JsonPath.<String> read(response.getContentAsString(), "$.address.zipCode")).isEqualTo("ZIP");
	}

	@Test // DATAREST-160
	public void returnConflictWhenConcurrentlyEditingVersionedEntity() throws Exception {

		Link receiptLink = client.discoverUnique("receipts");

		Receipt receipt = new Receipt();
		receipt.amount = new BigDecimal(50);
		receipt.saleItem = "Springy Tacos";

		String stringReceipt = mapper.writeValueAsString(receipt);

		MockHttpServletResponse createdReceipt = postAndGet(receiptLink, stringReceipt, MediaType.APPLICATION_JSON);
		Link tacosLink = client.assertHasLinkWithRel("self", createdReceipt);
		assertJsonPathEquals("$.saleItem", "Springy Tacos", createdReceipt);

		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(tacosLink.getHref());
		String concurrencyTag = createdReceipt.getHeader("ETag");

		mvc.perform(patch(builder.build().toUriString()).content("{ \"saleItem\" : \"SpringyBurritos\" }")
				.contentType(MediaType.APPLICATION_JSON).header(IF_MATCH, concurrencyTag))
				.andExpect(status().is2xxSuccessful());

		mvc.perform(patch(builder.build().toUriString()).content("{ \"saleItem\" : \"SpringyTequila\" }")
				.contentType(MediaType.APPLICATION_JSON).header(IF_MATCH, concurrencyTag))
				.andExpect(status().isPreconditionFailed());
	}

	@Test // DATAREST-471
	public void auditableResourceHasLastModifiedHeaderSet() throws Exception {

		Profile profile = repository.findAll().iterator().next();

		String header = mvc.perform(get("/profiles/{id}", profile.getId())).//
				andReturn().getResponse().getHeader("Last-Modified");

		assertThat(header).isNot(new Condition<String>(it -> it == null || it.isEmpty(), "Foo"));
	}

	@Test // DATAREST-482
	public void putDoesNotRemoveAssociations() throws Exception {

		Link usersLink = client.discoverUnique("users");
		Link userLink = assertHasContentLinkWithRel("self", client.request(usersLink));
		Link colleaguesLink = client.assertHasLinkWithRel("colleagues", client.request(userLink));

		// Expect a user returned as colleague
		client.follow(colleaguesLink).//
				andExpect(jsonPath("$._embedded.users").exists());

		User oliver = new User();
		oliver.firstname = "Oliver";
		oliver.lastname = "Gierke";

		putAndGet(userLink, mapper.writeValueAsString(oliver), MediaType.APPLICATION_JSON);

		// Expect colleague still present but address has been wiped
		client.follow(colleaguesLink).//
				andExpect(jsonPath("$._embedded.users").exists()).//
				andExpect(jsonPath("$.embedded.users[0].address").doesNotExist());
	}

	@Test // DATAREST-482
	public void emptiesAssociationForEmptyUriList() throws Exception {

		Link usersLink = client.discoverUnique("users");
		Link userLink = assertHasContentLinkWithRel("self", client.request(usersLink));
		Link colleaguesLink = client.assertHasLinkWithRel("colleagues", client.request(userLink));

		putAndGet(colleaguesLink, "", MediaType.parseMediaType("text/uri-list"));

		client.follow(colleaguesLink).//
				andExpect(status().isOk()).//
				andExpect(jsonPath("$").exists());
	}

	@Test // DATAREST-491
	public void updatesMapPropertyCorrectly() throws Exception {

		Link profilesLink = client.discoverUnique("profiles");
		Link profileLink = assertHasContentLinkWithRel("self", client.request(profilesLink));

		Profile profile = new Profile();
		profile.setMetadata(Collections.singletonMap("Key", "Value"));

		putAndGet(profileLink, mapper.writeValueAsString(profile), MediaType.APPLICATION_JSON);

		client.follow(profileLink).andExpect(jsonPath("$.metadata.Key").value("Value"));
	}

	@Test // DATAREST-506
	public void supportsConditionalGetsOnItemResource() throws Exception {

		Receipt receipt = new Receipt();
		receipt.amount = new BigDecimal(50);
		receipt.saleItem = "Springy Tacos";

		Link receiptsLink = client.discoverUnique("receipts");

		MockHttpServletResponse response = postAndGet(receiptsLink, mapper.writeValueAsString(receipt),
				MediaType.APPLICATION_JSON);

		Link receiptLink = client.getDiscoverer(response).findLinkWithRel("self", response.getContentAsString());

		mvc.perform(get(receiptLink.getHref()).header(IF_MODIFIED_SINCE, response.getHeader(LAST_MODIFIED))).//
				andExpect(status().isNotModified()).//
				andExpect(header().string(ETAG, is(notNullValue())));

		mvc.perform(get(receiptLink.getHref()).header(IF_NONE_MATCH, response.getHeader(ETAG))).//
				andExpect(status().isNotModified()).//
				andExpect(header().string(ETAG, is(notNullValue())));
	}

	@Test // DATAREST-511
	public void invokesQueryResourceReturningAnOptional() throws Exception {

		Profile profile = repository.findAll().iterator().next();

		Link link = client.discoverUnique("profiles", "search", "findProfileById");

		mvc.perform(get(link.expand(profile.getId()).getHref())).//
				andExpect(status().isOk());
	}

	@Test // DATAREST-517
	public void returnsNotFoundIfQueryExecutionDoesNotReturnResult() throws Exception {

		Link link = client.discoverUnique("profiles", "search", "findProfileById");

		mvc.perform(get(link.expand("").getHref())).//
				andExpect(status().isNotFound());
	}

	@Test // DATAREST-712
	public void invokesQueryMethodTakingAReferenceCorrectly() throws Exception {

		Link link = client.discoverUnique("users", "search", "findByColleaguesContains");

		User thomas = userRepository.findAll(QUser.user.firstname.eq("Thomas")).iterator().next();
		Link thomasUri = entityLinks.linkToSingleResource(User.class, thomas.id).expand();

		String href = link.expand(thomasUri.getHref()).getHref();

		mvc.perform(get(href)).andExpect(status().isOk());
	}

	@Test // DATAREST-835
	public void exposesETagHeaderForSearchResourceYieldingItemResource() throws Exception {

		Link link = client.discoverUnique("profiles", "search", "findProfileById");

		Profile profile = repository.findAll().iterator().next();

		mvc.perform(get(link.expand(profile.getId()).getHref()))//
				.andExpect(header().string("ETag", is("\"0\"")))//
				.andExpect(header().string("Last-Modified", is(notNullValue())));
	}

	@Test // DATAREST-835
	public void doesNotAddETagHeaderForCollectionQueryResource() throws Exception {

		Link link = client.discoverUnique("profiles", "search", "findByType");

		Profile profile = repository.findAll().iterator().next();

		mvc.perform(get(link.expand(profile.getType()).getHref()))//
				.andExpect(header().string("ETag", is(nullValue())))//
				.andExpect(header().string("Last-Modified", is(nullValue())));
	}
}
