package com.vidyut.marketplace.dto;

import jakarta.validation.constraints.Size;

public record PropertyInterestRequest(@Size(max = 1200) String message) {}
