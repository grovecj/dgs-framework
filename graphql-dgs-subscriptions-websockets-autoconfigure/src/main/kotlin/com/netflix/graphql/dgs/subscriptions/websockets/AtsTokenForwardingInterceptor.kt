package com.netflix.graphql.dgs.subscriptions.websockets

import com.betfanatics.auth.AuthException
import com.betfanatics.auth.service.AuthService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor

@Component
open class AtsTokenForwardingInterceptor(private val authService: AuthService) : HttpSessionHandshakeInterceptor() {
    @Throws(Exception::class)
    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>
    ): Boolean {
        try {
            val loginContext = authService.getLoginContext((request as ServletServerHttpRequest).servletRequest)
                ?: throw AuthException("ATS token not found")
            attributes["loginContext"] = loginContext
        } catch (e: AuthException) {
            log.error("Error validating ATS token", e)
            response.setStatusCode(HttpStatus.UNAUTHORIZED)
            return false
        }
        return super.beforeHandshake(request, response, wsHandler, attributes)
    }

    companion object {
        private val log = LoggerFactory.getLogger(AtsTokenForwardingInterceptor::class.java)
    }
}
