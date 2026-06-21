package com.ichi.place.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ichi.place.dto.PlaceSearchResult;
import com.ichi.place.service.VWorldPlaceSearchService;

@RestController
@RequestMapping("/api/places")
public class PlaceController {

    private final VWorldPlaceSearchService places;

    public PlaceController(VWorldPlaceSearchService places) {
        this.places = places;
    }

    @GetMapping("/search")
    public List<PlaceSearchResult> search(@RequestParam("q") String query) {
        return places.search(query);
    }
}
