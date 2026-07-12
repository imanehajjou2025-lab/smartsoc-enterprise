package com.smartsoc.api.common.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Fait servir le build React (classpath:/static, embarqué dans le jar) par
 * Spring Boot lui-même : la plateforme n'expose qu'UN service sur :8080
 * (UI + API), sans Nginx. Cloudflare Tunnel pointe directement dessus
 * (voir ADR-007 et l'architecture SOC).
 *
 * Les fichiers statiques réels (index.html, /assets/**) sont servis tels
 * quels ; toute autre route côté client (/alerts, /admin/users…) retombe
 * sur index.html pour que le routage React fonctionne au rechargement.
 * Les préfixes techniques (API, actuator, swagger, websocket) sont exclus
 * du fallback : ils restent gérés par leurs contrôleurs/handlers.
 */
@Component
public class SpaResourceConfig implements WebMvcConfigurer {

    private static final String[] NON_SPA_PREFIXES = {
            "api/", "api-docs", "v3/", "actuator/", "swagger", "webjars/", "ws"
    };

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(@NonNull String resourcePath,
                                                   @NonNull Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        for (String prefix : NON_SPA_PREFIXES) {
                            if (resourcePath.startsWith(prefix)) {
                                return null;
                            }
                        }
                        Resource index = new ClassPathResource("static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
