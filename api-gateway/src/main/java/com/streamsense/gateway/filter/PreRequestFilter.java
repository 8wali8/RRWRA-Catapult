package com.streamsense.gateway.filter;

import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.UUID;

@Component
public class PreRequestFilter extends ZuulFilter {

    @Override
    public String filterType() {
        return "pre";
    }

    @Override
    public int filterOrder() {
        return 1;
    }

    @Override
    public boolean shouldFilter() {
        return true;
    }

    @Override
    public Object run() {
        RequestContext ctx = RequestContext.getCurrentContext();
        HttpServletRequest request = ctx.getRequest();
        
        // Add correlation ID for tracing
        String correlationId = UUID.randomUUID().toString();
        ctx.addZuulRequestHeader("X-Correlation-ID", correlationId);
        
        System.out.printf("🌐 Gateway: %s request to %s - Correlation ID: %s%n",
                request.getMethod(), request.getRequestURL(), correlationId);
        
        return null;
    }
}