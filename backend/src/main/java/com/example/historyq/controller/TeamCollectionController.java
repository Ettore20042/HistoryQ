package com.example.historyq.controller;

import com.example.historyq.dto.TeamCollectionResponse;
import com.example.historyq.service.TeamCollectionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/teams/{teamId}/collections")
public class TeamCollectionController {
    private final TeamCollectionService service;

    public TeamCollectionController(TeamCollectionService service) {
        this.service = service;
    }

    @GetMapping
    public List<TeamCollectionResponse> shared(@PathVariable UUID teamId) {
        return service.shared(teamId);
    }

    @GetMapping("/candidates")
    public List<TeamCollectionResponse> candidates(
            @PathVariable UUID teamId,
            @RequestParam(defaultValue = "ALL") String source,
            @RequestParam(required = false) String query) {
        return service.candidates(teamId, source, query);
    }

    @PostMapping
    public List<TeamCollectionResponse> add(
            @PathVariable UUID teamId,
            @RequestBody AddCollectionsRequest request) {
        return service.add(teamId, request.collectionIds());
    }

    @DeleteMapping("/{collectionId}")
    public ResponseEntity<Void> remove(
            @PathVariable UUID teamId,
            @PathVariable UUID collectionId) {
        service.remove(teamId, collectionId);
        return ResponseEntity.noContent().build();
    }

    public record AddCollectionsRequest(List<UUID> collectionIds) {
    }
}
