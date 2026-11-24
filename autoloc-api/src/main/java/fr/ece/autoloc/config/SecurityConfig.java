package fr.ece.autoloc.config;

import fr.ece.autoloc.securite.FiltreLimiteDebit;
import fr.ece.autoloc.securite.FiltreProtectionConnexion;
import fr.ece.autoloc.securite.LimiteurDebit;
import fr.ece.autoloc.securite.ProtectionConnexion;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

@Configuration
public class SecurityConfig {

    /**
     * Content-Security-Policy : le navigateur n'exécute que les scripts servis par l'application elle-même
     * (aucun script en ligne, aucun eval) et n'envoie de requêtes qu'à elle. Même si un texte malveillant
     * arrivait dans une page, il ne pourrait ni s'exécuter ni exfiltrer les identifiants de l'espace agence.
     * Seules exceptions : la feuille de style et les polices Google Fonts du template.
     */
    static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' https://fonts.googleapis.com",
            "font-src 'self' https://fonts.gstatic.com",
            "img-src 'self' data:",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=(), payment=(), usb=()";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ProtectionConnexion protectionConnexion,
            LimiteurDebit limiteurRequetesPubliques) throws Exception {
        http
                // Les règles sont évaluées dans l'ordre : de la plus précise à la plus large
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/admin.html", "/reinitialisation.html", "/inscription.html", "/favicon.svg").permitAll()
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/fonts/**").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Espace client : rôle CLIENT seulement. Un client n'a accès à rien d'autre sous /api/
                        // (la règle suivante exige USER ou ADMIN), et le personnel n'a pas accès à l'espace client.
                        .requestMatchers("/api/client/**").hasRole("CLIENT")
                        .requestMatchers("/api/**").hasAnyRole("USER", "ADMIN")
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                // Avant toute vérification de mot de passe : limitation des formulaires publics, puis blocage
                // des identifiants et adresses qui ont accumulé les échecs de connexion.
                .addFilterBefore(new FiltreLimiteDebit(limiteurRequetesPubliques), BasicAuthenticationFilter.class)
                .addFilterBefore(new FiltreProtectionConnexion(protectionConnexion), BasicAuthenticationFilter.class)
                // En plus des en-têtes par défaut de Spring Security (X-Content-Type-Options: nosniff,
                // X-Frame-Options: DENY, Cache-Control: no-store, HSTS dès que le site est en HTTPS) :
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.SAME_ORIGIN))
                        .permissionsPolicyHeader(permissions -> permissions.policy(PERMISSIONS_POLICY)))
                // API REST sans état : ni session ni cookie, donc rien à protéger contre le CSRF
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }

    // Les comptes sont lus dans la base par service.compte.UtilisateurDetailsService (seul
    // UserDetailsService de l'application : Spring Security l'utilise automatiquement).

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Publie succès et échecs de connexion comme événements : ProtectionConnexion les compte. */
    @Bean
    public AuthenticationEventPublisher authenticationEventPublisher(ApplicationEventPublisher publisher) {
        return new DefaultAuthenticationEventPublisher(publisher);
    }
}
