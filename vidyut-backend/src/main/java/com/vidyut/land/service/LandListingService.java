package com.vidyut.land.service;

import com.vidyut.land.dto.LandListingCreateRequest;
import com.vidyut.land.dto.LandListingResponse;

import java.util.List;

public interface LandListingService {
    LandListingResponse createListing(Long hostUserId, LandListingCreateRequest request);
    LandListingResponse updateListing(Long hostUserId, Long listingId, LandListingCreateRequest request);
    List<LandListingResponse> getAllListings();
    List<LandListingResponse> getListingsByHostUserId(Long hostUserId);
}
