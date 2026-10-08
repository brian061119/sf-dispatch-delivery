package com.wedelivery.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wedelivery.dto.TrackingResponse;
import com.wedelivery.entity.User;
import com.wedelivery.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.*;

@Service
public class AiDeliveryService {
    private final GeminiDeliveryClient gemini;
    private final RecommendationService recommendations;
    private final TrackingService tracking;
    private final ObjectMapper mapper;
    private long lastGeocodeRequest;
    private final Map<String, Map<String,Object>> addressCache = new LinkedHashMap<>();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    public AiDeliveryService(GeminiDeliveryClient gemini, RecommendationService recommendations, TrackingService tracking, ObjectMapper mapper) {
        this.gemini = gemini; this.recommendations = recommendations; this.tracking = tracking; this.mapper = mapper;
    }
    public Map<String,Object> parse(String text) {
        checkText(text);
        JsonNode fields = gemini.extract(text, List.of());
        if (!fields.path("intent").asText().equals("DRAFT") && !fields.path("intent").asText().equals("QUOTE"))
            throw new IllegalArgumentException("Describe a delivery with pickup, destination and package details.");
        return draft(fields);
    }
    public Map<String,Object> chat(String message, List<Map<String,String>> history, User user) {
        checkText(message);
        if (history == null) history = List.of();
        if (history.size() > 20) history = history.subList(history.size()-20, history.size());
        for (Map<String,String> turn : history) {
            if (turn == null || !Set.of("user", "assistant").contains(turn.get("role"))) throw new IllegalArgumentException("Invalid conversation history.");
            checkText(turn.get("content"));
        }
        // A weight-only question cannot be priced yet. Ask for locations locally,
        // without spending model quota; preserve model handling when earlier user
        // turns contain additional route context.
        Pattern weightOnly = Pattern.compile("(?i)^\\s*how much for (?:a |an )?(\\d+(?:\\.\\d+)?)\\s*kg (?:package|parcel|item)\\s*[?.!]?\\s*$");
        Matcher weightMatch = weightOnly.matcher(message);
        boolean onlyWeightHistory = history.stream().filter(t -> "user".equals(t.get("role")))
            .allMatch(t -> weightOnly.matcher(t.get("content")).matches());
        if (weightMatch.matches() && onlyWeightHistory) {
            JsonNode fields = mapper.createObjectNode().put("intent", "QUOTE")
                .put("weightKg", new java.math.BigDecimal(weightMatch.group(1)));
            return quote(fields, user);
        }
        JsonNode fields = gemini.extract(message, history);
        switch (fields.path("intent").asText()) {
            case "TRACK": return track(fields);
            case "QUOTE": return quote(fields, user);
            case "DRAFT": {
                Map<String,Object> result = draft(fields);
                Map<String,Object> pkg = cast(cast(result.get("prefill")).get("pkg"));
                Map<String,Object> card = new LinkedHashMap<>(pkg);
                card.put("type", "prefill"); card.put("itemName", pkg.get("description"));
                result.put("cards", List.of(card));
                result.put("reply", "Review the extracted details in the order wizard. Confirm exact addresses, package details and the delivery option before payment.");
                return result;
            }
            case "HELP": return reply(helpReply(fields));
            case "WRITE": return reply("Please use your order detail page to cancel, modify or confirm receipt. The assistant cannot execute these actions or pay for an order.");
            default: throw new IllegalStateException("Gemini returned an unsupported intent. Please rephrase your question.");
        }
    }
    private String helpReply(JsonNode fields) {
        String answer = fields.path("reply").asText("").trim();
        if (answer.isEmpty()) throw new IllegalStateException("Gemini returned no answer. Please retry or rephrase your question.");
        if (answer.length() > 4000) answer = answer.substring(0, 4000);
        return answer;
    }
    private Map<String,Object> track(JsonNode fields) {
        String code = fields.path("trackingCode").asText("").trim();
        if (code.isEmpty()) return reply("Please provide the tracking code from your order confirmation.");
        if (!code.matches("[A-Za-z0-9-]{6,64}")) return reply("Please check your tracking code.");
        try {
            TrackingResponse data = tracking.trackByTrackingCode(code);
            Map<String,Object> card = new LinkedHashMap<>();
            card.put("type", "order"); card.put("orderId", data.getOrderNumber());
            card.put("status", data.getOrderStatus().name()); card.put("trackingCode", code);
            card.put("packageDescription", data.getCurrentStageDescription());
            Map<String,Object> result = reply("Order " + data.getOrderNumber() + ": " + data.getOrderStatus().name()
                + (data.getEtaMinutesRemaining() == null ? "" : ". Estimated remaining time: " + data.getEtaMinutesRemaining() + " min.") );
            result.put("cards", List.of(card)); return result;
        } catch (ResourceNotFoundException e) { return reply("No delivery found for that tracking code. Check the code from your confirmation."); }
    }
    private Map<String,Object> quote(JsonNode fields, User user) {
        Map<String,Object> draft = draft(fields), prefill = cast(draft.get("prefill"));
        Map<String,Object> pickup = cast(prefill.get("pickup")), dropoff = cast(prefill.get("dropoff")), pkg = cast(prefill.get("pkg"));
        if (!pkg.containsKey("weightKg") || pickup.isEmpty() || dropoff.isEmpty())
            return replyWithDraft("Please provide package weight and exact pickup and destination addresses, including street numbers. Weight alone is not enough for a real quote.", draft);
        Map<String,Object> p = locate(pickup), d = locate(dropoff);
        if (p.isEmpty() || d.isEmpty()) return replyWithDraft("I could not verify both exact addresses in San Francisco. Please give street numbers or review the locations on the map in the order wizard.", draft);
        if (p.get("lat").equals(d.get("lat")) && p.get("lng").equals(d.get("lng"))) return reply("Pickup and destination must differ.");
        List<?> candidates = recommendations.generateContractRecommendations(Map.of("pickup", p, "dropoff", d,
            "package", pkg, "priority", prefill.get("priority")), user).getCandidates();
        Map<String,Object> result = reply(candidates.isEmpty() ? "No delivery plan is currently available for this route and package." : "Current delivery quotes for the verified route. Confirm locations and package dimensions in the order wizard before paying.");
        result.put("cards", List.of(Map.of("type", "quote", "candidates", candidates)));
        prefill.put("pickup", p); prefill.put("dropoff", d); result.put("prefill", prefill);
        return result;
    }
    private Map<String,Object> replyWithDraft(String text, Map<String,Object> draft) {
        Map<String,Object> result = reply(text); result.put("prefill", draft.get("prefill"));
        Map<String,Object> pkg = cast(cast(draft.get("prefill")).get("pkg"));
        Map<String,Object> card = new LinkedHashMap<>(pkg); card.put("type", "prefill"); card.put("itemName", pkg.get("description"));
        result.put("cards", List.of(card)); return result;
    }
    synchronized Map<String,Object> locate(Map<String,Object> address) {
        String text = (String) address.get("line1");
        // A street without a house number is ambiguous: never quote to an arbitrary street centroid.
        if (text == null || !Pattern.compile("\\b\\d+[A-Za-z]?\\b").matcher(text).find()) return Map.of();
        if (addressCache.containsKey(text)) return new LinkedHashMap<>(addressCache.get(text));
        try {
            long wait = 1100 - (System.currentTimeMillis() - lastGeocodeRequest);
            if (wait > 0) Thread.sleep(wait);
            lastGeocodeRequest = System.currentTimeMillis();
            URI uri = URI.create("https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&limit=2&countrycodes=us&bounded=1&viewbox=-122.53,37.84,-122.35,37.708&q="
                + URLEncoder.encode(text + ", " + address.get("city") + ", CA", StandardCharsets.UTF_8));
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(4))
                .header("User-Agent", "SFDispatchDelivery/1.0 (local development address lookup)").GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode()!=200) return Map.of();
            JsonNode results = mapper.readTree(response.body());
            if (!results.isArray() || results.size()!=1) return Map.of();
            JsonNode found = results.get(0);
            if (found.path("address").path("house_number").asText("").isBlank()) return Map.of();
            String requested = Pattern.compile("\\b\\d+[A-Za-z]?\\b").matcher(text).results().findFirst().get().group();
            if (!requested.equalsIgnoreCase(found.path("address").path("house_number").asText())) return Map.of();
            double lat = found.path("lat").asDouble(), lng = found.path("lon").asDouble();
            if (lat<37.708 || lat>37.84 || lng< -122.53 || lng> -122.35) return Map.of();
            Map<String,Object> out = new LinkedHashMap<>(address); out.put("lat", lat); out.put("lng", lng);
            if (addressCache.size() >= 100) addressCache.remove(addressCache.keySet().iterator().next());
            addressCache.put(text, out); return new LinkedHashMap<>(out);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); return Map.of(); }
        catch (Exception e) { return Map.of(); }
    }
    Map<String,Object> draft(JsonNode fields) {
        Map<String,Object> prefill = new LinkedHashMap<>(), pkg = new LinkedHashMap<>();
        pkg.put("description", fields.path("itemName").asText(""));
        pkg.put("fragile", fields.path("fragile").asBoolean(false));
        for (String field : List.of("weightKg", "lengthCm", "widthCm", "heightCm")) {
            if (fields.hasNonNull(field)) {
                JsonNode value = fields.get(field);
                // Some providers emit 0 for omitted optional numeric fields. Treat that as
                // unknown; still reject negative/invalid values. Never quote with unknown weight.
                if (value.isNumber() && value.asDouble() == 0) continue;
                if (!value.isNumber() || !Double.isFinite(value.asDouble()) || value.asDouble()<=0)
                    throw new IllegalArgumentException("Package " + field + " must be positive.");
                pkg.put(field, value.decimalValue());
            }
        }
        prefill.put("pkg", pkg); prefill.put("priority", fields.path("priority").asText("STANDARD").equals("EXPRESS") ? "EXPRESS" : "STANDARD");
        List<String> missing = new ArrayList<>();
        if (!pkg.containsKey("weightKg")) missing.add("weightKg");
        for (String key : List.of("pickup", "dropoff")) {
            String text = fields.path(key + "Text").asText("").trim();
            if (!text.isEmpty()) prefill.put(key, Map.of("line1", text, "city", "San Francisco"));
            // Coordinates must come from a geocoder/user map confirmation, never a language model.
            missing.add(key + "ConfirmedLocation");
        }
        Map<String,Object> result = new LinkedHashMap<>(); result.put("prefill", prefill); result.put("missingFields", missing);
        result.put("mode", "gemini"); return result;
    }
    private Map<String,Object> reply(String text) { return new LinkedHashMap<>(Map.of("reply", text, "cards", List.of())); }
    @SuppressWarnings("unchecked") private Map<String,Object> cast(Object value) { return value instanceof Map ? (Map<String,Object>)value : Map.of(); }
    private void checkText(String text) {
        if (text == null || text.isBlank() || text.length()>4000) throw new IllegalArgumentException("Message must contain 1 to 4000 characters.");
    }
}
