# Security

Security is a first-class, day-one concern, not a hardening pass before launch. This
covers Spring Security's current model, stateless token-based auth for microservices,
and method-level authorization.

## Table of contents
- [The microservices auth model](#the-microservices-auth-model)
- [Spring Security configuration (lambda DSL)](#spring-security-configuration-lambda-dsl)
- [OAuth2 resource server & JWT](#oauth2-resource-server--jwt)
- [Method security](#method-security)
- [Service-to-service auth](#service-to-service-auth)
- [Secure defaults & common mistakes](#secure-defaults--common-mistakes)

## The microservices auth model

In a microservices estate, don't build bespoke auth per service. The standard pattern:

- A central **OAuth2 / OIDC authorization server** (Keycloak, Auth0, Okta, Entra ID,
  or a Spring Authorization Server) issues tokens after authenticating users/clients.
- Each microservice is an **OAuth2 resource server**: it is stateless, holds no
  sessions, and authorizes each request by validating the bearer **JWT** access token
  (checking signature, issuer, audience, expiry) and reading scopes/roles from claims.
- The **API gateway** can do coarse-grained token checks at the edge, but each service
  still validates independently — never trust that "the gateway already checked".

Statelessness is what makes services horizontally scalable; sessions in memory break
that, so avoid server-side session state.

## Spring Security configuration (lambda DSL)

Spring Security 6/7 uses the lambda DSL and a `SecurityFilterChain` bean. There is no
`WebSecurityConfigurerAdapter` anymore (removed) — seeing it is a legacy signal.

```java
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/orders/**").hasAuthority("SCOPE_orders.read")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable());   // safe ONLY for stateless token APIs, not browser-session apps
        return http;
    }
}
```

Note the CSRF nuance: disabling CSRF is correct for a stateless, token-authenticated
API (there's no session cookie to forge), but it is **not** safe for a
browser-session/cookie app. Get this right per service rather than cargo-culting.

## OAuth2 resource server & JWT

Add `spring-boot-starter-oauth2-resource-server` and point it at the issuer; Spring
auto-configures JWT decoding and validation, fetching the signing keys from the
provider's JWKS endpoint:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://auth.example.com/realms/prod
```

This validates signature, issuer, and expiry for you. Also validate **audience** so a
token minted for another service isn't accepted here. Map custom role claims to
authorities with a `JwtAuthenticationConverter` when your provider puts roles in a
non-standard claim.

## Method security

Layer fine-grained authorization at the service/method level with
`@EnableMethodSecurity` and annotations, so authorization lives next to the operation
it protects rather than only in URL matchers:

```java
@PreAuthorize("hasAuthority('SCOPE_orders.write') and #order.customerId == authentication.name")
Order place(OrderDraft order) { ... }
```

`@PreAuthorize`/`@PostAuthorize` with SpEL cover role checks and ownership checks.
This defends against the common mistake of relying solely on URL-pattern rules, which
are easy to bypass as routing evolves.

## Service-to-service auth

When services call each other, they still need credentials — don't leave internal
calls unauthenticated on the assumption the network is private ("zero trust"). Use the
OAuth2 **client credentials** grant: the calling service obtains its own token and
sends it as a bearer token. Spring Security's OAuth2 client support (or a
service-mesh mТLS layer) handles this. Propagating the *user's* token downstream is
sometimes appropriate for on-behalf-of calls, but be deliberate about token audience
and scope when you do.

## Secure defaults & common mistakes

Things to build in, and to flag hard in reviews:

- **No secrets in code/config/images** — see `configuration-and-profiles.md`. Critical.
- **`permitAll()` everywhere** or `anyRequest().permitAll()` — accidental open service.
  Default-deny (`anyRequest().authenticated()`) and open up explicitly.
- **Weak JWT validation** — not checking audience/issuer, accepting `alg: none`,
  overlong token lifetimes. Critical.
- **Leaking internals in errors** — stack traces/SQL to clients (see
  `rest-api-design.md`).
- **Missing transport security** — TLS everywhere, including internal hops in
  sensitive environments.
- **Outdated dependencies** — run dependency vulnerability scanning (OWASP Dependency
  Check, Snyk, or GitHub Dependabot) in CI; a known-CVE library is a real exposure.
- **Overbroad CORS** — `*` origins on a credentialed API. Scope CORS to known clients.
- **Actuator over-exposure** — don't expose `env`, `heapdump`, `threaddump`, etc.
  publicly; restrict management endpoints (see `observability.md`).
