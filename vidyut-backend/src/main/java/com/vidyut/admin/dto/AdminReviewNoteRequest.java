package com.vidyut.admin.dto;

import jakarta.validation.constraints.Size;

public record AdminReviewNoteRequest(boolean approved, @Size(max = 800) String note) {}
