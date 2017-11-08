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
package org.springframework.data.rest.webmvc.jpa.groovy;

import org.springframework.data.repository.CrudRepository;
import org.springframework.data.rest.webmvc.jpa.groovy.SimulatedGroovyDomainClass;

/**
 * Simulates a repository built on a Groovy domain object.
 *
 * @author Greg Turnquist
 */
public interface SimulatedGroovyDomainClassRepository extends CrudRepository<SimulatedGroovyDomainClass, Long> {}
