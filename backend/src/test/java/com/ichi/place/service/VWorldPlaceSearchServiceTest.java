package com.ichi.place.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.ichi.place.dto.PlaceSearchResult;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class VWorldPlaceSearchServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsSuccessfulVworldResponseToPlaceResult() {
        var service = new VWorldPlaceSearchService(null, null);
        var response = objectMapper.readTree("""
            {
              "response": {
                "status": "OK",
                "refined": { "text": "서울특별시 강남구 봉은사로 524" },
                "result": {
                  "point": { "x": "127.062831", "y": "37.514575" }
                }
              }
            }
            """);

        List<PlaceSearchResult> results = service.toResults("봉은사로 524", response);

        assertThat(results)
            .containsExactly(new PlaceSearchResult("서울특별시 강남구 봉은사로 524", 37.514575, 127.062831));
    }

    @Test
    void returnsEmptyListWhenVworldHasNoResult() {
        var service = new VWorldPlaceSearchService(null, null);
        var response = objectMapper.readTree("""
            { "response": { "status": "NOT_FOUND" } }
            """);

        assertThat(service.toResults("없는 주소", response)).isEmpty();
    }
}
