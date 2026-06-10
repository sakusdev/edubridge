package dev.sakus.geyseredu.authservice;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JwksTokenValidator {
    private final AuthConfig config;
    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private String cachedJwks;
    private Instant cacheExpiresAt = Instant.EPOCH;

    public JwksTokenValidator(AuthConfig config) {
        this.config = config;
    }

    public String validateAndReadPayload(String idToken) throws IOException, InterruptedException, GeneralSecurityException {
        String header = JsonUtil.jwtHeader(idToken);
        String algorithm = JsonUtil.stringField(header, "alg").orElse("");
        String keyId = JsonUtil.stringField(header, "kid").orElse("");
        if (!"RS256".equals(algorithm)) {
            throw new GeneralSecurityException("Unsupported ID token algorithm: " + algorithm);
        }
        if (keyId.isBlank()) {
            throw new GeneralSecurityException("ID token does not contain kid.");
        }

        RSAPublicKey key = publicKey(keyId);
        verifySignature(idToken, key);
        String payload = JsonUtil.idTokenPayload(idToken);
        validateClaims(payload);
        return payload;
    }

    private void validateClaims(String payload) throws GeneralSecurityException {
        String audience = JsonUtil.stringField(payload, "aud").orElse("");
        String tenantId = JsonUtil.stringField(payload, "tid").orElse("");
        String issuer = JsonUtil.stringField(payload, "iss").orElse("");

        if (!config.clientId().equals(audience)) {
            throw new GeneralSecurityException("ID token audience is not allowed.");
        }
        long expiresAtEpoch = JsonUtil.longField(payload, "exp").orElse(0L);
        if (expiresAtEpoch <= Instant.now().getEpochSecond()) {
            throw new GeneralSecurityException("ID token is expired.");
        }
        if (!config.tenantAllowed(tenantId)) {
            throw new GeneralSecurityException("This tenant is not allowed.");
        }
        String expectedIssuer = "https://login.microsoftonline.com/" + tenantId + "/v2.0";
        if (!expectedIssuer.equals(issuer)) {
            throw new GeneralSecurityException("ID token issuer is not allowed.");
        }
    }

    private RSAPublicKey publicKey(String keyId) throws IOException, InterruptedException, GeneralSecurityException {
        String jwks = jwks();
        Optional<String> keyObject = findKeyObject(jwks, keyId);
        if (keyObject.isEmpty()) {
            cachedJwks = null;
            jwks = jwks();
            keyObject = findKeyObject(jwks, keyId);
        }
        String keyJson = keyObject.orElseThrow(() -> new GeneralSecurityException("JWKS key not found for kid " + keyId));
        String modulus = JsonUtil.stringField(keyJson, "n")
            .orElseThrow(() -> new GeneralSecurityException("JWKS key is missing modulus."));
        String exponent = JsonUtil.stringField(keyJson, "e")
            .orElseThrow(() -> new GeneralSecurityException("JWKS key is missing exponent."));

        BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(modulus));
        BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(exponent));
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e));
    }

    private String jwks() throws IOException, InterruptedException {
        if (cachedJwks != null && Instant.now().isBefore(cacheExpiresAt)) {
            return cachedJwks;
        }
        String metadata = get("https://login.microsoftonline.com/" + urlTenant() + "/v2.0/.well-known/openid-configuration");
        String jwksUri = JsonUtil.stringField(metadata, "jwks_uri")
            .orElseThrow(() -> new IOException("OpenID metadata did not include jwks_uri."));
        cachedJwks = get(jwksUri);
        cacheExpiresAt = Instant.now().plus(Duration.ofHours(6));
        return cachedJwks;
    }

    private String get(String uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(uri))
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(uri + " returned HTTP " + response.statusCode());
        }
        return response.body();
    }

    private Optional<String> findKeyObject(String jwks, String keyId) {
        Matcher matcher = Pattern.compile("\\{[^{}]*\"kid\"\\s*:\\s*\"" + Pattern.quote(keyId) + "\"[^{}]*}").matcher(jwks);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group());
    }

    private void verifySignature(String jwt, RSAPublicKey key) throws GeneralSecurityException {
        String[] parts = jwt.split("\\.");
        if (parts.length != 3) {
            throw new GeneralSecurityException("ID token is not a signed JWT.");
        }
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(key);
        signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        if (!signature.verify(Base64.getUrlDecoder().decode(parts[2]))) {
            throw new GeneralSecurityException("ID token signature is invalid.");
        }
    }

    private String urlTenant() {
        return config.tenant().replace("/", "%2F");
    }
}
