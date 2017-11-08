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
package org.springframework.data.rest.webmvc.json.patch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AddOperationTests {

	@Test
	public void addBooleanPropertyValue() throws Exception {

		List<Todo> todos = new ArrayList<Todo>();
		todos.add(new Todo(1L, "A", false));
		todos.add(new Todo(2L, "B", false));
		todos.add(new Todo(3L, "C", false));

		AddOperation add = new AddOperation("/1/complete", true);
		add.perform(todos, Todo.class);

		assertTrue(todos.get(1).isComplete());
	}

	@Test
	public void addStringPropertyValue() throws Exception {

		List<Todo> todos = new ArrayList<Todo>();
		todos.add(new Todo(1L, "A", false));
		todos.add(new Todo(2L, "B", false));
		todos.add(new Todo(3L, "C", false));

		AddOperation add = new AddOperation("/1/description", "BBB");
		add.perform(todos, Todo.class);

		assertEquals("BBB", todos.get(1).getDescription());
	}

	@Test
	public void addItemToList() throws Exception {

		List<Todo> todos = new ArrayList<Todo>();
		todos.add(new Todo(1L, "A", false));
		todos.add(new Todo(2L, "B", false));
		todos.add(new Todo(3L, "C", false));

		AddOperation add = new AddOperation("/1", new Todo(null, "D", true));
		add.perform(todos, Todo.class);

		assertEquals(4, todos.size());
		assertEquals("A", todos.get(0).getDescription());
		assertFalse(todos.get(0).isComplete());
		assertEquals("D", todos.get(1).getDescription());
		assertTrue(todos.get(1).isComplete());
		assertEquals("B", todos.get(2).getDescription());
		assertFalse(todos.get(2).isComplete());
		assertEquals("C", todos.get(3).getDescription());
		assertFalse(todos.get(3).isComplete());
	}

	@Test // DATAREST-995
	public void addsItemsToNestedList() {

		Todo todo = new Todo(1L, "description", false);

		new AddOperation("/items/-", "Some text.").perform(todo, Todo.class);

		assertThat(todo.getItems().get(0)).isEqualTo("Some text.");
	}

	@Test // DATAREST-1039
	public void addsLazilyEvaluatedObjectToList() throws Exception {

		Todo todo = new Todo(1L, "description", false);

		ObjectMapper mapper = new ObjectMapper();
		JsonNode node = mapper.readTree("\"Some text.\"");
		JsonLateObjectEvaluator evaluator = new JsonLateObjectEvaluator(mapper, node);

		new AddOperation("/items/-", evaluator).perform(todo, Todo.class);

		assertThat(todo.getItems().get(0)).isEqualTo("Some text.");
	}

	@Test // DATAREST-1039
	public void initializesNullCollectionsOnAppend() {

		Todo todo = new Todo(1L, "description", false);

		new AddOperation("/uninitialized/-", "Text").perform(todo, Todo.class);

		assertThat(todo.getUninitialized()).containsExactly("Text");
	}
}
