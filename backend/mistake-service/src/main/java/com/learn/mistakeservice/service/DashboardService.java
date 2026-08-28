package com.learn.mistakeservice.service;

import com.learn.mistakeservice.vo.DashboardStatsVO;
import org.springframework.stereotype.Service;

@Service
public interface DashboardService {
    DashboardStatsVO getDashboard();
}
