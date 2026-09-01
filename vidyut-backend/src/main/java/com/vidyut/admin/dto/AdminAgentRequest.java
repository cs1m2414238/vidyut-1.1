package com.vidyut.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminAgentRequest(@NotBlank @Size(max = 1000) String question) {}
