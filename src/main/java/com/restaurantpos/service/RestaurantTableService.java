package com.restaurantpos.service;

import com.restaurantpos.database.RestaurantTableRepository;
import com.restaurantpos.model.RestaurantTable;
import java.sql.SQLException;
import java.util.List;

/** Applies table operation rules and delegates persistence to the repository. */
public class RestaurantTableService {
    private final RestaurantTableRepository repository;

    public RestaurantTableService() {
        this(new RestaurantTableRepository());
    }

    public RestaurantTableService(RestaurantTableRepository repository) {
        this.repository = repository;
    }

    public List<RestaurantTable> getTables() throws SQLException {
        return repository.findAll();
    }

    public void reserve(int tableId) throws SQLException {
        repository.reserve(tableId);
    }

    public void cancelReservation(int tableId) throws SQLException {
        repository.cancelReservation(tableId);
    }
}
