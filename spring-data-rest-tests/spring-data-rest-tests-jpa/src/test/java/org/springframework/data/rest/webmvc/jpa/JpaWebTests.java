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
package org.springframework.data.rest.webmvc.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.junit.Assert.assertThat;
import static org.springframework.data.rest.webmvc.util.TestUtils.*;
import static org.springframework.http.HttpHeaders.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import net.minidev.json.JSONArray;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.core.mapping.ResourceMappings;
import org.springframework.data.rest.tests.CommonWebTests;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Links;
import org.springframework.hateoas.RelProvider;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

/**
 * Web integration tests specific to JPA.
 * 
 * @author Oliver Gierke
 * @author Greg Turnquist
 * @author Mark Paluch
 */
@Transactional
@ContextConfiguration(classes = JpaRepositoryConfig.class)
public class JpaWebTests extends CommonWebTests {

	private static final MediaType TEXT_URI_LIST = MediaType.valueOf("text/uri-list");
	static final String LINK_TO_SIBLINGS_OF = "$._embedded..[?(@.firstName == '%s')]._links.siblings.href[0]";

	@Autowired TestDataPopulator loader;
	@Autowired ResourceMappings mappings;
	@Autowired RelProvider relProvider;

	ObjectMapper mapper = new ObjectMapper();

	/* 
	 * (non-Javadoc)
	 * @see org.springframework.data.rest.webmvc.AbstractWebIntegrationTests#setUp()
	 */
	@Override
	@Before
	public void setUp() {
		loader.populateRepositories();
		super.setUp();
	}

	/* 
	 * (non-Javadoc)
	 * @see org.springframework.data.rest.webmvc.AbstractWebIntegrationTests#expectedRootLinkRels()
	 */
	@Override
	protected Iterable<String> expectedRootLinkRels() {
		return Arrays.asList("people", "authors", "books");
	}

	/* 
	 * (non-Javadoc)
	 * @see org.springframework.data.rest.webmvc.AbstractWebIntegrationTests#getPayloadToPost()
	 */
	@Override
	protected Map<String, String> getPayloadToPost() throws Exception {
		return Collections.singletonMap("people", readFileFromClasspath("person.json"));
	}

	/* 
	 * (non-Javadoc)
	 * @see org.springframework.data.rest.webmvc.AbstractWebIntegrationTests#getRootAndLinkedResources()
	 */
	@Override
	protected MultiValueMap<String, String> getRootAndLinkedResources() {

		MultiValueMap<String, String> map = new LinkedMultiValueMap<String, String>();
		map.add("authors", "books");
		map.add("books", "authors");

		return map;
	}

	@Test // DATAREST-99
	public void doesNotExposeCreditCardRepository() throws Exception {

		mvc.perform(get("/")). //
				andExpect(status().isOk()). //
				andExpect(doesNotHaveLinkWithRel(mappings.getMetadataFor(CreditCard.class).getRel()));
	}

	@Test
	public void accessPersons() throws Exception {

		MockHttpServletResponse response = client.request("/people?page=0&size=1");

		Link nextLink = client.assertHasLinkWithRel(Link.REL_NEXT, response);
		assertDoesNotHaveLinkWithRel(Link.REL_PREVIOUS, response);

		response = client.request(nextLink);
		client.assertHasLinkWithRel(Link.REL_PREVIOUS, response);
		nextLink = client.assertHasLinkWithRel(Link.REL_NEXT, response);

		response = client.request(nextLink);
		client.assertHasLinkWithRel(Link.REL_PREVIOUS, response);
		assertDoesNotHaveLinkWithRel(Link.REL_NEXT, response);
	}

	@Test // DATAREST-169
	public void exposesLinkForRelatedResource() throws Exception {

		MockHttpServletResponse response = client.request("/");
		Link ordersLink = client.assertHasLinkWithRel("orders", response);

		MockHttpServletResponse orders = client.request(ordersLink);
		Link creatorLink = assertHasContentLinkWithRel("creator", orders);

		assertThat(client.request(creatorLink)).isNotNull();
	}

	@Test // DATAREST-200
	public void exposesInlinedEntities() throws Exception {

		MockHttpServletResponse response = client.request("/");
		Link ordersLink = client.assertHasLinkWithRel("orders", response);

		MockHttpServletResponse orders = client.request(ordersLink);
		assertHasJsonPathValue("$..lineItems", orders);
	}

	@Test // DATAREST-199
	public void createsOrderUsingPut() throws Exception {

		mvc.perform(//
				put("/orders/{id}", 4711).//
						content(readFileFromClasspath("order.json")).contentType(MediaType.APPLICATION_JSON)//
		).andExpect(status().isCreated());
	}

	@Test // DATAREST-117
	public void createPersonThenVerifyIgnoredAttributesDontExist() throws Exception {

		Link peopleLink = client.discoverUnique("people");
		ObjectMapper mapper = new ObjectMapper();
		Person frodo = new Person("Frodo", "Baggins");
		frodo.setAge(77);
		frodo.setHeight(42);
		frodo.setWeight(75);
		String frodoString = mapper.writeValueAsString(frodo);

		MockHttpServletResponse response = postAndGet(peopleLink, frodoString, MediaType.APPLICATION_JSON);

		assertJsonPathEquals("$.firstName", "Frodo", response);
		assertJsonPathEquals("$.lastName", "Baggins", response);
		assertJsonPathDoesntExist("$.age", response);
		assertJsonPathDoesntExist("$.height", response);
		assertJsonPathDoesntExist("$.weight", response);
	}

	@Test // DATAREST-95
	public void createThenPatch() throws Exception {

		Link peopleLink = client.discoverUnique("people");

		MockHttpServletResponse bilbo = postAndGet(peopleLink, "{ \"firstName\" : \"Bilbo\", \"lastName\" : \"Baggins\" }",
				MediaType.APPLICATION_JSON);

		Link bilboLink = client.assertHasLinkWithRel("self", bilbo);

		assertThat((String) JsonPath.read(bilbo.getContentAsString(), "$.firstName")).isEqualTo("Bilbo");
		assertThat((String) JsonPath.read(bilbo.getContentAsString(), "$.lastName")).isEqualTo("Baggins");

		MockHttpServletResponse frodo = patchAndGet(bilboLink, "{ \"firstName\" : \"Frodo\" }", MediaType.APPLICATION_JSON);

		assertThat((String) JsonPath.read(frodo.getContentAsString(), "$.firstName")).isEqualTo("Frodo");
		assertThat((String) JsonPath.read(frodo.getContentAsString(), "$.lastName")).isEqualTo("Baggins");

		frodo = patchAndGet(bilboLink, "{ \"firstName\" : null }", MediaType.APPLICATION_JSON);

		assertThat((String) JsonPath.read(frodo.getContentAsString(), "$.firstName")).isNull();
		assertThat((String) JsonPath.read(frodo.getContentAsString(), "$.lastName")).isEqualTo("Baggins");
	}

	@Test // DATAREST-150
	public void createThenPut() throws Exception {

		Link peopleLink = client.discoverUnique("people");

		MockHttpServletResponse bilbo = postAndGet(peopleLink, //
				"{ \"firstName\" : \"Bilbo\", \"lastName\" : \"Baggins\" }", //
				MediaType.APPLICATION_JSON);

		Link bilboLink = client.assertHasLinkWithRel("self", bilbo);

		assertThat((String) JsonPath.read(bilbo.getContentAsString(), "$.firstName"), equalTo("Bilbo"));
		assertThat((String) JsonPath.read(bilbo.getContentAsString(), "$.lastName"), equalTo("Baggins"));

		MockHttpServletResponse frodo = putAndGet(bilboLink, //
				"{ \"firstName\" : \"Frodo\" }", //
				MediaType.APPLICATION_JSON);

		assertThat((String) JsonPath.read(frodo.getContentAsString(), "$.firstName"), equalTo("Frodo"));
		assertNull(JsonPath.read(frodo.getContentAsString(), "$.lastName"));
	}

	@Test
	public void listsSiblingsWithContentCorrectly() throws Exception {
		assertPersonWithNameAndSiblingLink("John");
	}

	@Test
	public void listsEmptySiblingsCorrectly() throws Exception {
		assertPersonWithNameAndSiblingLink("Billy Bob");
	}

	@Test // DATAREST-219
	public void manipulatePropertyCollectionRestfullyWithMultiplePosts() throws Exception {

		List<Link> links = preparePersonResources(new Person("Frodo", "Baggins"), //
				new Person("Bilbo", "Baggins"), //
				new Person("Merry", "Baggins"), //
				new Person("Pippin", "Baggins"));

		Link frodosSiblingLink = links.get(0);

		patchAndGet(frodosSiblingLink, links.get(1).getHref(), TEXT_URI_LIST);
		patchAndGet(frodosSiblingLink, links.get(2).getHref(), TEXT_URI_LIST);
		patchAndGet(frodosSiblingLink, links.get(3).getHref(), TEXT_URI_LIST);

		assertSiblingNames(frodosSiblingLink, "Bilbo", "Merry", "Pippin");
	}

	@Test // DATAREST-219
	public void manipulatePropertyCollectionRestfullyWithSinglePost() throws Exception {

		List<Link> links = preparePersonResources(new Person("Frodo", "Baggins"), //
				new Person("Bilbo", "Baggins"), //
				new Person("Merry", "Baggins"), //
				new Person("Pippin", "Baggins"));

		Link frodosSiblingLink = links.get(0);

		patchAndGet(frodosSiblingLink, toUriList(links.get(1), links.get(2), links.get(3)), TEXT_URI_LIST);

		assertSiblingNames(frodosSiblingLink, "Bilbo", "Merry", "Pippin");
	}

	@Test // DATAREST-219
	public void manipulatePropertyCollectionRestfullyWithMultiplePuts() throws Exception {

		List<Link> links = preparePersonResources(new Person("Frodo", "Baggins"), //
				new Person("Bilbo", "Baggins"), //
				new Person("Merry", "Baggins"), //
				new Person("Pippin", "Baggins"));

		Link frodosSiblingsLink = links.get(0);

		putAndGet(frodosSiblingsLink, links.get(1).expand().getHref(), TEXT_URI_LIST);
		putAndGet(frodosSiblingsLink, links.get(2).expand().getHref(), TEXT_URI_LIST);
		putAndGet(frodosSiblingsLink, links.get(3).expand().getHref(), TEXT_URI_LIST);
		assertSiblingNames(frodosSiblingsLink, "Pippin");

		patchAndGet(frodosSiblingsLink, links.get(2).getHref(), TEXT_URI_LIST);
		assertSiblingNames(frodosSiblingsLink, "Merry", "Pippin");
	}

	@Test // DATAREST-219
	public void manipulatePropertyCollectionRestfullyWithSinglePut() throws Exception {

		List<Link> links = preparePersonResources(new Person("Frodo", "Baggins"), //
				new Person("Bilbo", "Baggins"), //
				new Person("Merry", "Baggins"), //
				new Person("Pippin", "Baggins"));

		Link frodoSiblingLink = links.get(0);

		putAndGet(frodoSiblingLink, toUriList(links.get(1), links.get(2), links.get(3)), TEXT_URI_LIST);
		assertSiblingNames(frodoSiblingLink, "Bilbo", "Merry", "Pippin");

		putAndGet(frodoSiblingLink, toUriList(links.get(3)), TEXT_URI_LIST);
		assertSiblingNames(frodoSiblingLink, "Pippin");

		patchAndGet(frodoSiblingLink, toUriList(links.get(2)), TEXT_URI_LIST);
		assertSiblingNames(frodoSiblingLink, "Merry", "Pippin");
	}

	@Test // DATAREST-219
	public void manipulatePropertyCollectionRestfullyWithDelete() throws Exception {

		List<Link> links = preparePersonResources(new Person("Frodo", "Baggins"), //
				new Person("Bilbo", "Baggins"), //
				new Person("Merry", "Baggins"), //
				new Person("Pippin", "Baggins"));

		Link frodosSiblingsLink = links.get(0);

		patchAndGet(frodosSiblingsLink, links.get(1).getHref(), TEXT_URI_LIST);
		patchAndGet(frodosSiblingsLink, links.get(2).getHref(), TEXT_URI_LIST);
		patchAndGet(frodosSiblingsLink, links.get(3).getHref(), TEXT_URI_LIST);

		String pippinId = new UriTemplate("/people/{id}").match(links.get(3).getHref()).get("id");
		deleteAndVerify(new Link(frodosSiblingsLink.expand().getHref() + "/" + pippinId));

		assertSiblingNames(frodosSiblingsLink, "Bilbo", "Merry");
	}

	@Test // DATAREST-50
	public void propertiesCanHaveNulls() throws Exception {

		Link peopleLink = client.discoverUnique("people");

		Person frodo = new Person();
		frodo.setFirstName("Frodo");
		frodo.setLastName(null);

		MockHttpServletResponse response = postAndGet(peopleLink, mapper.writeValueAsString(frodo),
				MediaType.APPLICATION_JSON);
		String responseBody = response.getContentAsString();

		assertEquals(JsonPath.read(responseBody, "$.firstName"), "Frodo");
		assertNull(JsonPath.read(responseBody, "$.lastName"));
	}

	@Test // DATAREST-238
	public void putShouldWorkDespiteExistingLinks() throws Exception {

		Link peopleLink = client.discoverUnique("people");

		Person frodo = new Person("Frodo", "Baggins");
		String frodoString = mapper.writeValueAsString(frodo);

		MockHttpServletResponse createdPerson = postAndGet(peopleLink, frodoString, MediaType.APPLICATION_JSON);

		Link frodoLink = client.assertHasLinkWithRel("self", createdPerson);
		assertJsonPathEquals("$.firstName", "Frodo", createdPerson);

		String bilboWithFrodosLinks = createdPerson.getContentAsString().replace("Frodo", "Bilbo");

		MockHttpServletResponse overwrittenResponse = putAndGet(frodoLink, bilboWithFrodosLinks,
				MediaType.APPLICATION_JSON);

		client.assertHasLinkWithRel("self", overwrittenResponse);
		assertJsonPathEquals("$.firstName", "Bilbo", overwrittenResponse);
	}

	@Test // DATAREST-217
	public void doesNotAllowGetToCollectionResourceIfFindAllIsNotExported() throws Exception {

		Link link = client.discoverUnique("addresses");

		mvc.perform(get(link.getHref())).//
				andExpect(status().isMethodNotAllowed());
	}

	@Test // DATAREST-217
	public void doesNotAllowPostToCollectionResourceIfSaveIsNotExported() throws Exception {

		Link link = client.discoverUnique("addresses");

		mvc.perform(post(link.getHref()).content("{}").contentType(MediaType.APPLICATION_JSON)).//
				andExpect(status().isMethodNotAllowed());
	}

	/**
	 * Checks, that the server only returns the properties contained in the projection requested.
	 * 
	 * @see OrderSummary
	 */
	@Test // DATAREST-221
	public void returnsProjectionIfRequested() throws Exception {

		Link orders = client.discoverUnique("orders");

		MockHttpServletResponse response = client.request(orders);
		Link orderLink = assertContentLinkWithRel("self", response, true).expand();

		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(orderLink.getHref());
		String uri = builder.queryParam("projection", "summary").build().toUriString();

		response = mvc.perform(get(uri))//
				.andExpect(status().isOk())//
				.andExpect(jsonPath("$.price", is(2.5)))//
				.andReturn().getResponse();

		assertJsonPathDoesntExist("$.lineItems", response);
	}

	@Test // DATAREST-261
	public void relProviderDetectsCustomizedMapping() {
		assertThat(relProvider.getCollectionResourceRelFor(Person.class)).isEqualTo("people");
	}

	@Test // DATAREST-311
	public void onlyLinksShouldAppearWhenExecuteSearchCompact() throws Exception {

		Link peopleLink = client.discoverUnique("people");
		Person daenerys = new Person("Daenerys", "Targaryen");
		String daenerysString = mapper.writeValueAsString(daenerys);

		MockHttpServletResponse createdPerson = postAndGet(peopleLink, daenerysString, MediaType.APPLICATION_JSON);
		Link daenerysLink = client.assertHasLinkWithRel("self", createdPerson);
		assertJsonPathEquals("$.firstName", "Daenerys", createdPerson);

		Link searchLink = client.discoverUnique(peopleLink, "search");
		Link byFirstNameLink = client.discoverUnique(searchLink, "findFirstPersonByFirstName");

		MockHttpServletResponse response = client.request(byFirstNameLink.expand("Daenerys"),
				MediaType.parseMediaType("application/x-spring-data-compact+json"));

		String responseBody = response.getContentAsString();

		JSONArray personLinks = JsonPath.<JSONArray> read(responseBody, "$.links[?(@.rel=='person')].href");

		assertThat(personLinks).hasSize(1);
		assertThat(personLinks.get(0)).isEqualTo((Object) daenerysLink.getHref());
		assertThat(JsonPath.<JSONArray> read(responseBody, "$.content")).hasSize(0);
	}

	@Test // DATAREST-317
	public void rendersExcerptProjectionsCorrectly() throws Exception {

		Link authorsLink = client.discoverUnique("authors");

		MockHttpServletResponse response = client.request(authorsLink);
		String firstAuthorPath = "$._embedded.authors[0]";

		// Has main content
		assertHasJsonPathValue(firstAuthorPath.concat(".name"), response);

		// Embeddes content of related entity, self link and keeps relation link
		assertHasJsonPathValue(firstAuthorPath.concat("._embedded.books[0].title"), response);
		assertHasJsonPathValue(firstAuthorPath.concat("._embedded.books[0]._links.self"), response);
		assertHasJsonPathValue(firstAuthorPath.concat("._links.books"), response);

		// Access item resource and expect link to related resource present
		String content = response.getContentAsString();
		String href = JsonPath.read(content, firstAuthorPath.concat("._links.self.href"));

		client.follow(new Link(href)).andExpect(client.hasLinkWithRel("books"));
	}

	@Test // DATAREST-353
	public void returns404WhenTryingToDeleteANonExistingResource() throws Exception {

		Link receiptsLink = client.discoverUnique("receipts");

		mvc.perform(delete(receiptsLink.getHref().concat("/{id}"), 4711)).//
				andExpect(status().isNotFound());
	}

	@Test // DATAREST-384
	public void exectuesSearchThatTakesASort() throws Exception {

		Link booksLink = client.discoverUnique("books");
		Link searchLink = client.discoverUnique(booksLink, "search");
		Link findBySortedLink = client.discoverUnique(searchLink, "find-by-sorted");

		// Assert sort options advertised
		assertThat(findBySortedLink.isTemplated()).isTrue();
		assertThat(findBySortedLink.getVariableNames()).contains("sort", "projection");

		// Assert results returned as specified
		client.follow(findBySortedLink.expand("title,desc")).//
				andExpect(jsonPath("$._embedded.books[0].title").value("Spring Data (Second Edition)")).//
				andExpect(jsonPath("$._embedded.books[1].title").value("Spring Data")).//
				andExpect(client.hasLinkWithRel("self"));

		client.follow(findBySortedLink.expand("title,asc")).//
				andExpect(jsonPath("$._embedded.books[0].title").value("Spring Data")).//
				andExpect(jsonPath("$._embedded.books[1].title").value("Spring Data (Second Edition)")).//
				andExpect(client.hasLinkWithRel("self"));
	}

	@Test // DATAREST-160
	public void returnConflictWhenConcurrentlyEditingVersionedEntity() throws Exception {

		Link receiptLink = client.discoverUnique("receipts");

		Receipt receipt = new Receipt();
		receipt.setAmount(new BigDecimal(50));
		receipt.setSaleItem("Springy Tacos");

		String stringReceipt = mapper.writeValueAsString(receipt);

		MockHttpServletResponse createdReceipt = postAndGet(receiptLink, stringReceipt, MediaType.APPLICATION_JSON);
		Link tacosLink = client.assertHasLinkWithRel("self", createdReceipt);
		assertJsonPathEquals("$.saleItem", "Springy Tacos", createdReceipt);

		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(tacosLink.getHref());
		String concurrencyTag = createdReceipt.getHeader("ETag");

		mvc.perform(patch(builder.build().toUriString()).content("{ \"saleItem\" : \"SpringyBurritos\" }")
				.contentType(MediaType.APPLICATION_JSON).header(IF_MATCH, concurrencyTag)) //
				.andExpect(status().is2xxSuccessful());

		mvc.perform(patch(builder.build().toUriString()).content("{ \"saleItem\" : \"SpringyTequila\" }")
				.contentType(MediaType.APPLICATION_JSON).header(IF_MATCH, "\"falseETag\""))
				.andExpect(status().isPreconditionFailed());
	}

	@Test // DATAREST-423
	public void invokesCustomControllerAndBindsDomainObjectCorrectly() throws Exception {

		MockHttpServletResponse authorsResponse = client.request(client.discoverUnique("authors"));

		String authorUri = JsonPath.read(authorsResponse.getContentAsString(), "$._embedded.authors[0]._links.self.href");

		mvc.perform(delete(authorUri)).//
				andExpect(status().isIAmATeapot());
	}

	@Test // DATAREST-523
	public void augmentsCollectionAssociationUsingPost() throws Exception {

		List<Link> links = preparePersonResources(new Person("Frodo", "Baggins"), //
				new Person("Bilbo", "Baggins"));

		Link frodosSiblingsLink = links.get(0).expand();
		Link bilboLink = links.get(1);

		for (int i = 1; i <= 2; i++) {

			mvc.perform(post(frodosSiblingsLink.getHref()).//
					content(bilboLink.getHref()).//
					contentType(TEXT_URI_LIST)).//
					andExpect(status().isNoContent());

			mvc.perform(get(frodosSiblingsLink.getHref())).//
					andExpect(jsonPath("$._embedded.people", hasSize(i)));
		}
	}

	@Test // DATAREST-658
	public void returnsLinkHeadersForHeadRequestToItemResource() throws Exception {

		MockHttpServletResponse response = client.request(client.discoverUnique("people"));
		String personHref = JsonPath.read(response.getContentAsString(), "$._embedded.people[0]._links.self.href");

		response = mvc.perform(head(personHref))//
				.andExpect(status().isNoContent())//
				.andReturn().getResponse();

		Links links = Links.valueOf(response.getHeader("Link"));
		assertThat(links.hasLink("self")).isTrue();
		assertThat(links.hasLink("person")).isTrue();
	}

	@Test // DATAREST-883
	public void exectuesSearchThatTakesAMappedSortProperty() throws Exception {

		Link findBySortedLink = client.discoverUnique("books", "search", "find-by-sorted");

		// Assert sort options advertised
		assertThat(findBySortedLink.isTemplated()).isTrue();
		assertThat(findBySortedLink.getVariableNames()).contains("sort", "projection");

		// Assert results returned as specified
		client.follow(findBySortedLink.expand("sales,desc")).//
				andExpect(jsonPath("$._embedded.books[0].title").value("Spring Data (Second Edition)")).//
				andExpect(jsonPath("$._embedded.books[1].title").value("Spring Data")).//
				andExpect(client.hasLinkWithRel("self"));

		client.follow(findBySortedLink.expand("sales,asc")).//
				andExpect(jsonPath("$._embedded.books[0].title").value("Spring Data")).//
				andExpect(jsonPath("$._embedded.books[1].title").value("Spring Data (Second Edition)")).//
				andExpect(client.hasLinkWithRel("self"));
	}

	@Test // DATAREST-883
	public void exectuesCustomQuerySearchThatTakesAMappedSortProperty() throws Exception {

		Link findByLink = client.discoverUnique("books", "search", "find-spring-books-sorted");

		// Assert sort options advertised
		assertThat(findByLink.isTemplated()).isTrue();

		// Assert results returned as specified
		client.follow(findByLink.expand("0", "10", "sales,desc")).//
				andExpect(jsonPath("$._embedded.books[0].title").value("Spring Data (Second Edition)")).//
				andExpect(jsonPath("$._embedded.books[1].title").value("Spring Data")).//
				andExpect(client.hasLinkWithRel("self"));

		client.follow(findByLink.expand("0", "10", "unknown,asc,sales,asc")).//
				andExpect(jsonPath("$._embedded.books[0].title").value("Spring Data")).//
				andExpect(jsonPath("$._embedded.books[1].title").value("Spring Data (Second Edition)")).//
				andExpect(client.hasLinkWithRel("self"));
	}

	@Test // DATAREST-910
	public void callUnmappedCustomRepositoryController() throws Exception {

		mvc.perform(post("/orders/search/sort")).andExpect(status().isOk());
		mvc.perform(post("/orders/search/sort?sort=type&page=1&size=10")).andExpect(status().isOk());
	}

	@Test // DATAREST-976
	public void appliesSortByEmbeddedAssociation() throws Exception {

		Link booksLink = client.discoverUnique("books");
		Link searchLink = client.discoverUnique(booksLink, "search");
		Link findBySortedLink = client.discoverUnique(searchLink, "find-by-sorted");

		// Assert sort options advertised
		assertThat(findBySortedLink.isTemplated()).isTrue();
		assertThat(findBySortedLink.getVariableNames()).contains("sort", "projection");

		// Assert results returned as specified
		client.follow(findBySortedLink.expand("offer.price,desc")).//
				andExpect(jsonPath("$._embedded.books[0].title").value("Spring Data (Second Edition)")).//
				andExpect(jsonPath("$._embedded.books[1].title").value("Spring Data")).//
				andExpect(client.hasLinkWithRel("self"));

		client.follow(findBySortedLink.expand("offer.price,asc")).//
				andExpect(jsonPath("$._embedded.books[0].title").value("Spring Data")).//
				andExpect(jsonPath("$._embedded.books[1].title").value("Spring Data (Second Edition)")).//
				andExpect(client.hasLinkWithRel("self"));
	}

	private List<Link> preparePersonResources(Person primary, Person... persons) throws Exception {

		Link peopleLink = client.discoverUnique("people");
		List<Link> links = new ArrayList<Link>();

		MockHttpServletResponse primaryResponse = postAndGet(peopleLink, mapper.writeValueAsString(primary),
				MediaType.APPLICATION_JSON);
		links.add(client.assertHasLinkWithRel("siblings", primaryResponse));

		for (Person person : persons) {

			String payload = mapper.writeValueAsString(person);
			MockHttpServletResponse response = postAndGet(peopleLink, payload, MediaType.APPLICATION_JSON);

			links.add(client.assertHasLinkWithRel(Link.REL_SELF, response));
		}

		return links;
	}

	/**
	 * Asserts the {@link Person} resource the given link points to contains siblings with the given names.
	 * 
	 * @param link
	 * @param siblingNames
	 * @throws Exception
	 */
	private void assertSiblingNames(Link link, String... siblingNames) throws Exception {

		String responseBody = client.request(link).getContentAsString();
		List<String> persons = JsonPath.read(responseBody, "$._embedded.people[*].firstName");

		assertThat(persons).hasSize(siblingNames.length);
		assertThat(persons).contains(siblingNames);
	}

	private void assertPersonWithNameAndSiblingLink(String name) throws Exception {

		MockHttpServletResponse response = client.request(client.discoverUnique("people"));

		String jsonPath = String.format("$._embedded.people[?(@.firstName == '%s')]", name);

		// Assert content inlined
		Object john = JsonPath.<JSONArray> read(response.getContentAsString(), jsonPath).get(0);
		assertThat(john).isNotNull();
		assertThat(JsonPath.<String> read(john, "$.firstName")).isNotNull();

		// Assert sibling link exposed in resource pointed to
		Link selfLink = new Link(JsonPath.<String> read(john, "$._links.self.href"));
		client.follow(selfLink).//
				andExpect(status().isOk()).//
				andExpect(jsonPath("$._links.siblings", is(notNullValue())));
	}

	private static String toUriList(Link... links) {

		List<String> uris = new ArrayList<String>(links.length);

		for (Link link : links) {
			uris.add(link.expand().getHref());
		}

		return StringUtils.collectionToDelimitedString(uris, "\n");
	}
}
