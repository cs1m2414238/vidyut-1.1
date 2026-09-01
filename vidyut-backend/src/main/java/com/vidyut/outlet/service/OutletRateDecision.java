package com.vidyut.outlet.service;

public record OutletRateDecision(boolean outlet, Long outletId, String tierName,
                                 double ratePerKwh, double visitorRatePerKwh) {}
