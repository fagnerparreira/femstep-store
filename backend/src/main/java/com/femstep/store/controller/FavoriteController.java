package com.femstep.store.controller;

import com.femstep.store.dto.catalog.ProductSummaryResponse;
import com.femstep.store.service.FavoriteService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/account/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping
    public List<ProductSummaryResponse> list(Authentication authentication) {
        return favoriteService.list(authentication.getName());
    }

    @GetMapping("/{productId}")
    public Map<String, Boolean> contains(
            Authentication authentication,
            @PathVariable Long productId
    ) {
        return Map.of("favorite", favoriteService.contains(authentication.getName(), productId));
    }

    @PostMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void add(Authentication authentication, @PathVariable Long productId) {
        favoriteService.add(authentication.getName(), productId);
    }

    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(Authentication authentication, @PathVariable Long productId) {
        favoriteService.remove(authentication.getName(), productId);
    }
}
