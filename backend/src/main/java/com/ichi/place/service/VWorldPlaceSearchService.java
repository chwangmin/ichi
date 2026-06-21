package com.ichi.place.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.ichi.config.IchiProperties;
import com.ichi.place.dto.PlaceSearchResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class VWorldPlaceSearchService {

    private static final String ROAD = "ROAD";
    private static final String PARCEL = "PARCEL";

    private final IchiProperties props;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VWorldPlaceSearchService(IchiProperties props, RestClient.Builder builder) {
        this.props = props;
        this.restClient = builder != null ? builder.build() : null;
    }

    public List<PlaceSearchResult> search(String query) {
        String q = query == null ? "" : query.trim();
        if (q.length() < 2 || props == null || props.getVworld().getApiKey().isBlank()) {
            return List.of();
        }

        List<PlaceSearchResult> road = request(q, ROAD);
        if (!road.isEmpty()) {
            return road;
        }
        return request(q, PARCEL);
    }

    private List<PlaceSearchResult> request(String query, String type) {
        try {
            String body = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .scheme("https")
                    .host("api.vworld.kr")
                    .path("/req/address")
                    .queryParam("service", "address")
                    .queryParam("request", "getCoord")
                    .queryParam("version", "2.0")
                    .queryParam("crs", "EPSG:4326")
                    .queryParam("type", type)
                    .queryParam("address", query)
                    .queryParam("refine", "true")
                    .queryParam("simple", "false")
                    .queryParam("format", "json")
                    .queryParam("errorformat", "json")
                    .queryParam("key", props.getVworld().getApiKey())
                    .build())
                .retrieve()
                .body(String.class);
            return toResults(query, objectMapper.readTree(body));
        } catch (Exception e) {
            return List.of();
        }
    }

    List<PlaceSearchResult> toResults(String query, JsonNode root) {
        JsonNode response = root.path("response");
        if (!"OK".equalsIgnoreCase(response.path("status").asString())) {
            return List.of();
        }

        JsonNode point = response.path("result").path("point");
        double lng = point.path("x").asDouble(Double.NaN);
        double lat = point.path("y").asDouble(Double.NaN);
        if (!Double.isFinite(lat) || !Double.isFinite(lng)) {
            return List.of();
        }

        String label = response.path("refined").path("text").asString(query);
        return List.of(new PlaceSearchResult(label, lat, lng));
    }
}
