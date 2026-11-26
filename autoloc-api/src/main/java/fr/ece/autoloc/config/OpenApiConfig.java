package fr.ece.autoloc.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Description OpenAPI de l'API, affichée par Swagger UI sur /swagger-ui.html.
 * Le schéma « basicAuth » ajoute le bouton « Authorize » : on y saisit student / student123 ou
 * admin / admin123, et Swagger envoie l'en-tête Authorization: Basic à chaque appel.
 */
@Configuration
public class OpenApiConfig {

    static final String SCHEMA_SECURITE = "basicAuth";

    @Bean
    public OpenAPI autolocOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AutoLoc API")
                        .version("1.0")
                        .description("API de location de véhicules (TP UP ASI, ECE). Routes publiques sous /api/public, "
                                + "routes USER ou ADMIN sous /api, routes ADMIN sous /api/admin, espace client sous /api/client. "
                                + "Comptes de test : student / student123 (USER) et admin / admin123 (ADMIN)."))
                .components(new Components().addSecuritySchemes(SCHEMA_SECURITE,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic")
                                .description("HTTP Basic : identifiant et mot de passe d'un compte AutoLoc.")))
                .addSecurityItem(new SecurityRequirement().addList(SCHEMA_SECURITE));
    }
}
