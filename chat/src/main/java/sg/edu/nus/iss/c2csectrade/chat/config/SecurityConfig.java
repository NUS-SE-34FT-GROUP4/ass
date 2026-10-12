package sg.edu.nus.iss.c2csectrade.chat.config;

import sg.edu.nus.iss.c2csectrade.chat.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Chat service security: stateless JWT, same token as Core. The WebSocket
 * handshake is permitted at the HTTP level; the STOMP CONNECT frame is
 * authenticated by WebSocketAuthInterceptor.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    @Value("${app.cors.allowed-origins:http://localhost:8081,http://127.0.0.1:8081,http://localhost,http://127.0.0.1}")
    private List<String> allowedOrigins;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
           .cors(cors -> cors.configurationSource(corsConfigurationSource()))
           .csrf(csrf -> csrf.disable())
           .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
           .authorizeHttpRequests(auth -> auth
               .requestMatchers("/ws/**").permitAll()
               .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
               .anyRequest().authenticated()
            )
           .exceptionHandling(ex -> ex
               .authenticationEntryPoint((request, response, authException) -> {
                   response.setContentType("application/json;charset=UTF-8");
                   response.setStatus(401);
                   response.getWriter().write("{\"error\":\"Unauthorized\"}");
               })
               .accessDeniedHandler((request, response, accessDeniedException) -> {
                   response.setContentType("application/json;charset=UTF-8");
                   response.setStatus(403);
                   response.getWriter().write("{\"error\":\"Access denied\"}");
               })
           );
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
