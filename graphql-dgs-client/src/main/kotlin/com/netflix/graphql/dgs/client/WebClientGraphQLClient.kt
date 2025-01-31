/*
 * Copyright 2021 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.graphql.dgs.client

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.toEntity
import reactor.core.publisher.Mono
import java.util.function.Consumer

/**
 * A WebClient implementation of the DGS Client.
 * It does not implement [MonoGraphQLClient] because the user does not have to provide a request executor, but the API is the same otherwise.
 * A WebClient instance configured for the graphql endpoint (at least an url) must be provided.
 *
 * Example:
 * ```java
 *      WebClientGraphQLClient webClientGraphQLClient =
 *          new WebClientGraphQLClient(WebClient.create("http://localhost:8080/graphql"));
 *      GraphQLResponse message = webClientGraphQLClient.reactiveExecuteQuery("{hello}")
 *                                                      .map(r -> r.extractValue<String>("hello"));
 *      message.subscribe();
 * ```
 */
class WebClientGraphQLClient(
    private val webclient: WebClient,
    private val headersConsumer: Consumer<HttpHeaders>
) : MonoGraphQLClient {

    constructor(webclient: WebClient) : this(webclient, Consumer {})
    /**
     * @param query The query string. Note that you can use [code generation](https://netflix.github.io/dgs/generating-code-from-schema/#generating-query-apis-for-external-services) for a type safe query!
     * @return A [Mono] of [GraphQLResponse]. [GraphQLResponse] parses the response and gives easy access to data and errors.
     */
    override fun reactiveExecuteQuery(
        query: String,
    ): Mono<GraphQLResponse> {
        return reactiveExecuteQuery(query, emptyMap(), null)
    }

    /**
     * @param query The query string. Note that you can use [code generation](https://netflix.github.io/dgs/generating-code-from-schema/#generating-query-apis-for-external-services) for a type safe query!
     * @param variables A map of input variables
     * @return A [Mono] of [GraphQLResponse]. [GraphQLResponse] parses the response and gives easy access to data and errors.
     */
    override fun reactiveExecuteQuery(
        query: String,
        variables: Map<String, Any>,
    ): Mono<GraphQLResponse> {
        return reactiveExecuteQuery(query, variables, null)
    }

    /**
     * @param query The query string. Note that you can use [code generation](https://netflix.github.io/dgs/generating-code-from-schema/#generating-query-apis-for-external-services) for a type safe query!
     * @param variables A map of input variables
     * @param operationName Operation name
     * @return A [Mono] of [GraphQLResponse]. [GraphQLResponse] parses the response and gives easy access to data and errors.
     */
    override fun reactiveExecuteQuery(
        query: String,
        variables: Map<String, Any>,
        operationName: String?,
    ): Mono<GraphQLResponse> {

        @Suppress("BlockingMethodInNonBlockingContext") val serializedRequest = GraphQLClients.objectMapper.writeValueAsString(
            Request(
                query,
                variables,
                operationName
            )
        )

        return webclient.post()
            .bodyValue(serializedRequest)
            .headers { headers -> headers.addAll(GraphQLClients.defaultHeaders) }
            .headers(this.headersConsumer)
            .retrieve()
            .toEntity<String>()
            .map { response ->
                HttpResponse(
                    statusCode = response.statusCodeValue,
                    body = response.body,
                    headers = response.headers
                )
            }
            .map { httpResponse -> handleResponse(httpResponse, serializedRequest) }
    }

    private fun handleResponse(response: HttpResponse, requestBody: String): GraphQLResponse {
        val (statusCode, body) = response
        val headers = response.headers
        if (!HttpStatus.valueOf(statusCode).is2xxSuccessful) {
            throw GraphQLClientException(statusCode, webclient.toString(), body ?: "", requestBody)
        }

        return GraphQLResponse(body ?: "", headers)
    }
}
