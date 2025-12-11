package com.pns.oauthserver.controller;

import com.pns.oauthserver.model.dto.Food;
import com.pns.oauthserver.service.OrderManageService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/foods")
public class FoodController {

    private final OrderManageService foodService;

    public FoodController(OrderManageService foodService) {
        this.foodService = foodService;
    }

    @GetMapping
    public List<Food> all() {
        return foodService.getAll();
    }

    @GetMapping("/{id}")
    public Food one(@PathVariable String id) {
        return foodService.get(id);
    }

    @PostMapping
    public Food create(@RequestBody Food food) {
        return foodService.create(food);
    }

    @PutMapping("/{id}")
    public Food update(@PathVariable String id, @RequestBody Food food) {
        return foodService.update(id, food);
    }

    @DeleteMapping("/{id}")
    public String delete(@PathVariable String id) {
        return foodService.delete(id) ? "Deleted" : "Not Found";
    }
}
