/*
 * Copyright 2014-2016 the original author or authors.
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
package org.springframework.data.rest.webmvc.solr;

import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.solr.repository.config.EnableSolrRepositories;
import org.springframework.test.context.ContextConfiguration;

/**
 * @author Christoph Strobl
 */
@ContextConfiguration
public class SolrTestBase {

	public static @ClassRule TemporaryFolder TEMP_FOLDER = new TemporaryFolder();

	@Configuration
	@EnableSolrRepositories
	@Import(SolrInfrastructureConfig.class)
	static class MyConf {

		@Bean
		String solrHomeDir() {
			return TEMP_FOLDER.getRoot().getAbsolutePath();
		}
	}
}
