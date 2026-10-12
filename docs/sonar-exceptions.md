# Sonar analysis exceptions and CORS

PR #34 uses Maven scanner properties in the root POM. Automatic Analysis must remain off.

CSRF: java:S4502 is excluded only for the named Core and Chat SecurityConfig files. Their JWT filters read Authorization: Bearer, not authentication cookies. Re-review and remove the exception if cookie authentication, browser-managed credentials, or session login is introduced. No CORS rule is excluded.

Temporary duplication exclusions cover exactly the seven Chat copies listed in sonar.cpd.exclusions. These exclusions change duplication measurement only, not security scanning or coverage. Consolidate those copies into common after the presentation, removing each exclusion with its corresponding refactor. Do not extend this to a directory wildcard.

HTTP CORS and Chat SockJS/WebSocket origins use app.cors.allowed-origins (environment: APP_CORS_ALLOWED_ORIGINS), a comma-separated list of exact trusted origins. Defaults allow the local Vue server on port 8081 and local Docker frontend on port 80. Same-origin production requests remain supported; deployments with a separate frontend origin must explicitly configure its HTTPS origin in both services. Wildcard origins are not permitted with credentialed HTTP CORS. Keep credentials enabled for SockJS compatibility; allowing a trusted origin does not bypass JWT authentication.

Gitleaks scans HEAD history using the config inherited from main (#33 and #31). No additional secret exception is added by this PR.
