package com.restaurantpos.service;

import com.restaurantpos.database.DashboardRepository;
import com.restaurantpos.model.SalesDashboardSummary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;

/** Applies dashboard calculations to totals loaded by the dashboard repository. */
public class DashboardService {
    private final DashboardRepository repository;

    public DashboardService() {
        this(new DashboardRepository());
    }

    public DashboardService(DashboardRepository repository) {
        this.repository = repository;
    }

    public SalesDashboardSummary getTodaySummary() throws SQLException {
        DashboardRepository.DashboardData data = repository.readToday();
        long averagePaise = data.averageOrderCount() == 0
            ? 0
            : BigDecimal.valueOf(data.orderValueTodayPaise())
                .divide(BigDecimal.valueOf(data.averageOrderCount()), 0, RoundingMode.HALF_UP)
                .longValueExact();
        return new SalesDashboardSummary(
            data.salesTodayPaise(),
            data.ordersCreatedToday(),
            data.paidOrdersCount(),
            data.unpaidOrdersCount(),
            averagePaise
        );
    }
}
