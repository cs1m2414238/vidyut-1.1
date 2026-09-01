package com.vidyut.land.service;

import com.vidyut.land.dto.LandListingCreateRequest;
import com.vidyut.land.dto.LandListingResponse;
import com.vidyut.land.entity.LandListing;
import com.vidyut.land.entity.LandListingStatus;
import com.vidyut.land.entity.OwnershipType;
import com.vidyut.land.entity.PowerPhase;
import com.vidyut.land.entity.PropertyType;
import com.vidyut.land.repository.LandListingRepository;
import com.vidyut.account.repository.HostProfileRepository;
import com.vidyut.common.exception.ResourceNotFoundException;
import com.vidyut.admin.service.OperationalControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LandListingServiceImpl implements LandListingService {

    private final LandListingRepository landListingRepository;
    private final HostProfileRepository hostProfileRepository;
    private final OperationalControlService operationalControlService;

    @Override
    public LandListingResponse createListing(Long hostUserId, LandListingCreateRequest request) {
        operationalControlService.assertHostCanCreateListing(hostUserId);
        LandListing listing = LandListing.builder()
                .hostUserId(hostUserId)
                .status(LandListingStatus.PENDING_APPROVAL)
                .discoverable(false)
                .build();
        return mapToResponse(landListingRepository.save(apply(listing, request)));
    }

    @Override
    public LandListingResponse updateListing(Long hostUserId, Long listingId, LandListingCreateRequest request) {
        LandListing listing = landListingRepository.findByIdAndHostUserId(listingId, hostUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found for this Host account"));
        listing.setStatus(LandListingStatus.PENDING_APPROVAL);
        listing.setDiscoverable(false);
        return mapToResponse(landListingRepository.save(apply(listing, request)));
    }

    @Override
    public List<LandListingResponse> getAllListings() {
        return landListingRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<LandListingResponse> getListingsByHostUserId(Long hostUserId) {
        return landListingRepository.findByHostUserId(hostUserId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private LandListingResponse mapToResponse(LandListing l) {
        return LandListingResponse.builder()
                .id(l.getId())
                .hostUserId(l.getHostUserId())
                .title(l.getTitle())
                .address(l.getAddress())
                .city(l.getCity())
                .state(l.getState())
                .pincode(l.getPincode())
                .latitude(l.getLatitude())
                .longitude(l.getLongitude())
                .connectorType(l.getConnectorType())
                .powerKw(l.getPowerKw())
                .pricePerKwh(l.getPricePerKwh())
                .propertyType(l.getPropertyType())
                .availableParkingBays(l.getAvailableParkingBays())
                .powerPhase(l.getPowerPhase())
                .availableLoadKw(l.getAvailableLoadKw())
                .operatingHours(l.getOperatingHours())
                .ownershipType(l.getOwnershipType())
                .preferredConnectorType(l.getPreferredConnectorType())
                .preferredPowerKw(l.getPreferredPowerKw())
                .photoUrls(l.getPhotoUrls())
                .ownershipDocumentUrl(l.getOwnershipDocumentUrl())
                .electricityDocumentUrl(l.getElectricityDocumentUrl())
                .videoVerificationUrl(l.getVideoVerificationUrl())
                .adminReviewNote(l.getAdminReviewNote())
                .verificationStage(l.getVerificationStage())
                .discoverable(l.isDiscoverable())
                .status(l.getStatus())
                .createdAt(l.getCreatedAt())
                .build();
    }

    private LandListing apply(LandListing listing, LandListingCreateRequest request) {
        listing.setTitle(request.getTitle().trim());
        listing.setAddress(request.getAddress().trim());
        listing.setCity(clean(request.getCity()));
        listing.setState(clean(request.getState()));
        listing.setPincode(clean(request.getPincode()));
        listing.setLatitude(request.getLatitude() != 0 ? request.getLatitude() : 26.8467);
        listing.setLongitude(request.getLongitude() != 0 ? request.getLongitude() : 80.9462);
        listing.setConnectorType(clean(request.getConnectorType()) != null ? request.getConnectorType().trim() : "Type 2");
        listing.setPowerKw(request.getPowerKw() > 0 ? request.getPowerKw() : 7.4);
        listing.setPricePerKwh(request.getPricePerKwh() == null ? 0 : Math.max(0, request.getPricePerKwh()));
        listing.setPropertyType(request.getPropertyType() == null ? PropertyType.OTHER : request.getPropertyType());
        listing.setAvailableParkingBays(request.getAvailableParkingBays() == null ? 1 : Math.max(1, request.getAvailableParkingBays()));
        listing.setPowerPhase(request.getPowerPhase() == null ? PowerPhase.NOT_SURE : request.getPowerPhase());
        listing.setAvailableLoadKw(request.getAvailableLoadKw() == null ? 0 : Math.max(0, request.getAvailableLoadKw()));
        listing.setOperatingHours(clean(request.getOperatingHours()));
        listing.setOwnershipType(request.getOwnershipType() == null ? OwnershipType.OWNED : request.getOwnershipType());
        listing.setPreferredConnectorType(clean(request.getPreferredConnectorType()));
        listing.setPreferredPowerKw(request.getPreferredPowerKw() == null ? 0 : Math.max(0, request.getPreferredPowerKw()));
        listing.setPhotoUrls(clean(request.getPhotoUrls()));
        listing.setOwnershipDocumentUrl(clean(request.getOwnershipDocumentUrl()));
        listing.setElectricityDocumentUrl(clean(request.getElectricityDocumentUrl()));
        listing.setVideoVerificationUrl(clean(request.getVideoVerificationUrl()));
        listing.setAdminReviewNote(null);
        boolean hostVerified = hostProfileRepository.findById(listing.getHostUserId())
                .map(profile -> profile.isVerified() && profile.getVerificationStatus()
                        == com.vidyut.account.entity.HostVerificationStatus.VERIFIED)
                .orElse(false);
        boolean approved = listing.getStatus() == LandListingStatus.APPROVED || listing.getStatus() == LandListingStatus.ACTIVE;
        listing.setDiscoverable(hostVerified && approved && (request.getDiscoverable() == null || request.getDiscoverable()));
        return listing;
    }

    private String clean(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
