package com.vidyut.outlet.dto;

public record OutletStatsResponse(Long outletId, String institutionName, long sessions,
                                  double totalSpend, double savedVsVisitor) {}
