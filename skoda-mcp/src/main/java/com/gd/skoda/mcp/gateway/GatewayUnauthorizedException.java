package com.gd.skoda.mcp.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
class GatewayUnauthorizedException extends RuntimeException {}
