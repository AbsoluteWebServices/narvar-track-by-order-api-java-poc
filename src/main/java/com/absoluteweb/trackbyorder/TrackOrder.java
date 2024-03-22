package com.absoluteweb.trackbyorder;

import com.fasterxml.jackson.databind.util.JSONPObject;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@RestController
public class TrackOrder {

    private final Logger log = LoggerFactory.getLogger(TrackOrder.class);
    private final RestTemplate restTemplate;
    private final Environment env;

    // Using constructor injection for better testability and Spring best practices
    @Autowired
    public TrackOrder(RestTemplate restTemplate, Environment env) {
        this.restTemplate = restTemplate;
        this.env = env;
    }

    @GetMapping(value = "/track/order/{orderId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> trackOrder(@PathVariable("orderId") String orderId) {
        String retailerMoniker = env.getProperty("RETAILER_MONIKER");
        int epoch = (int) (Instant.now().toEpochMilli() / 1000);
        String key = env.getProperty("HMAC_TOKEN");

        // If the key or retailerMoniker is not set in the environment, return a bad request
        if (key == null || retailerMoniker == null) {
            log.error("HMAC_TOKEN or RETAILER_MONIKER not set in the environment");
            return ResponseEntity.badRequest().body("HMAC_TOKEN or RETAILER_MONIKER not set in the environment");
        }

        try {
            String epochToken = hmacSha256(key, String.valueOf(epoch));
            String orderToken = hmacSha256(key, orderId + ":" + epoch);

            Map<String, String> uriVariables = Map.of(
                    "orderNum", orderId,
                    "retailerMoniker", retailerMoniker,
                    "epoch", String.valueOf(epoch),
                    "epochToken", epochToken,
                    "orderToken", orderToken
            );

            String urlTemplate = "https://ws.narvar.com/api/v2/orders/{orderNum}/tracking?retailer_moniker={retailerMoniker}&order_token={orderToken}&epoch={epoch}&epoch_token={epochToken}";
            ResponseEntity<String> response = restTemplate.exchange(urlTemplate, HttpMethod.GET, null, String.class, uriVariables);
            JSONObject jsonResponse = new JSONObject(response.getBody());

            if (jsonResponse.has("errors")) {
                log.error("Error in tracking order response for orderId {}: {}", orderId, jsonResponse.getJSONArray("errors"));
                return ResponseEntity.badRequest().body(jsonResponse.getJSONArray("errors").toString());
            }

            log.info("Tracking order successful for orderId: {}", orderId);
            return ResponseEntity.ok(response.getBody());
        } catch (Exception e) {
            log.error("Error occurred while tracking order for orderId: {}", orderId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error occurred while tracking order");
        }
    }

    private static String hmacSha256(String key, String data) throws Exception {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256_HMAC.init(secretKey);

        byte[] hash = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
