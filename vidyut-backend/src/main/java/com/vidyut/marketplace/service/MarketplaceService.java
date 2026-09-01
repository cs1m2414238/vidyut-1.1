package com.vidyut.marketplace.service;

import com.vidyut.common.exception.BadRequestException;
import com.vidyut.common.exception.DuplicateResourceException;
import com.vidyut.common.exception.ResourceNotFoundException;
import com.vidyut.admin.service.OperationalControlService;
import com.vidyut.company.entity.Company;
import com.vidyut.company.entity.VerificationStatus;
import com.vidyut.company.repository.CompanyRepository;
import com.vidyut.company.service.CompanyVerificationService;
import com.vidyut.account.repository.AccountRepository;
import com.vidyut.account.repository.HostProfileRepository;
import com.vidyut.account.entity.HostProfile;
import com.vidyut.host.entity.HostReview;
import com.vidyut.host.repository.HostReviewRepository;
import com.vidyut.land.entity.LandListing;
import com.vidyut.land.entity.LandListingStatus;
import com.vidyut.land.repository.LandListingRepository;
import com.vidyut.marketplace.dto.*;
import com.vidyut.marketplace.entity.*;
import com.vidyut.marketplace.repository.*;
import com.vidyut.notification.entity.NotificationType;
import com.vidyut.notification.service.NotificationService;
import com.vidyut.station.entity.*;
import com.vidyut.station.repository.ChargingConnectorRepository;
import com.vidyut.station.repository.ChargingStationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketplaceService {
    private final CompanyRepository companyRepository;
    private final LandListingRepository landRepository;
    private final CompanyServiceAreaRepository areaRepository;
    private final ChargerProductRepository productRepository;
    private final InstallationRequestRepository requestRepository;
    private final InstallationProposalRepository proposalRepository;
    private final InstallationStatusHistoryRepository historyRepository;
    private final CompanyPropertyInterestRepository interestRepository;
    private final ChargingStationRepository stationRepository;
    private final ChargingConnectorRepository connectorRepository;
    private final NotificationService notificationService;
    private final CompanyVerificationService verificationService;
    private final HostProfileRepository hostProfileRepository;
    private final HostReviewRepository hostReviewRepository;
    private final AccountRepository accountRepository;
    private final OperationalControlService operationalControlService;

    public List<ServiceAreaResponse> companyAreas(Long companyAccountId) {
        requireCompany(companyAccountId);
        return areaRepository.findByCompany_Account_IdOrderByCityAsc(companyAccountId).stream().map(this::areaResponse).toList();
    }

    @Transactional
    public ServiceAreaResponse saveArea(Long companyAccountId, Long id, ServiceAreaRequest input) {
        operationalControlService.assertCompanyMarketplaceAllowed(companyAccountId);
        Company company = requireVerifiedCompany(companyAccountId);
        CompanyServiceArea area = id == null ? CompanyServiceArea.builder().company(company).build()
                : areaRepository.findByIdAndCompany_Account_Id(id, companyAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Service area not found for this company"));
        area.setCity(input.city().trim());
        area.setState(input.state().trim());
        area.setPincode(clean(input.pincode()));
        area.setLatitude(input.latitude());
        area.setLongitude(input.longitude());
        area.setRadiusKm(input.radiusKm() == null ? 50 : input.radiusKm());
        area.setInstallationAvailable(input.installationAvailable() == null || input.installationAvailable());
        area.setMaintenanceAvailable(input.maintenanceAvailable() == null || input.maintenanceAvailable());
        area.setSurveyFee(input.surveyFee() == null ? 0 : input.surveyFee());
        area.setTypicalInstallationDays(input.typicalInstallationDays() == null ? 14 : input.typicalInstallationDays());
        area.setActive(input.active() == null || input.active());
        return areaResponse(areaRepository.save(area));
    }

    @Transactional
    public void deleteArea(Long companyAccountId, Long id) {
        CompanyServiceArea area = areaRepository.findByIdAndCompany_Account_Id(id, companyAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Service area not found for this company"));
        areaRepository.delete(area);
    }

    public List<ChargerProductResponse> companyProducts(Long companyAccountId) {
        requireCompany(companyAccountId);
        return productRepository.findByCompany_Account_IdOrderByCreatedAtDesc(companyAccountId).stream().map(this::productResponse).toList();
    }

    @Transactional
    public ChargerProductResponse saveProduct(Long companyAccountId, Long id, ChargerProductRequest input) {
        operationalControlService.assertCompanyPublishingAllowed(companyAccountId);
        Company company = requireVerifiedCompany(companyAccountId);
        ChargerProduct product = id == null ? ChargerProduct.builder().company(company).build()
                : productRepository.findByIdAndCompany_Account_Id(id, companyAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Charger product not found for this company"));
        product.setModelName(input.modelName().trim());
        product.setManufacturer(input.manufacturer().trim());
        product.setCurrentType(input.currentType());
        product.setConnectorType(input.connectorType());
        product.setPowerKw(input.powerKw());
        product.setEquipmentPrice(input.equipmentPrice());
        product.setInstallationPrice(input.installationPrice());
        product.setWarrantyMonths(input.warrantyMonths());
        product.setAmcAvailable(input.amcAvailable());
        product.setCertifications(clean(input.certifications()));
        product.setDescription(clean(input.description()));
        product.setImageUrl(clean(input.imageUrl()));
        product.setComplianceDocumentUrl(clean(input.complianceDocumentUrl()));
        product.setBusinessModels(new HashSet<>(input.businessModels()));
        product.setActive(input.active() == null || input.active());
        product.setApprovalStatus(ProductApprovalStatus.PENDING_REVIEW);
        product.setAdminReviewNote(null);
        return productResponse(productRepository.save(product));
    }

    @Transactional
    public void deleteProduct(Long companyAccountId, Long id) {
        ChargerProduct product = productRepository.findByIdAndCompany_Account_Id(id, companyAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Charger product not found for this company"));
        product.setActive(false);
        productRepository.save(product);
    }

    public List<MarketplaceCompanyResponse> matchingCompanies(Long hostUserId, Long propertyId) {
        operationalControlService.assertHostCanStartPartnership(hostUserId);
        LandListing owned = ownedProperty(hostUserId, propertyId);
        requireVerifiedHostProperty(hostUserId, owned);
        return companyRepository.findAll().stream()
                .filter(Company::isActive)
                .filter(verificationService::isMarketplaceVerified)
                .map(this::companyMatch)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(MarketplaceCompanyResponse::companyName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<PropertyOpportunityResponse> matchingOpportunities(Long companyAccountId) {
        operationalControlService.assertCompanyMarketplaceAllowed(companyAccountId);
        requireVerifiedCompany(companyAccountId);
        return landRepository.findByDiscoverableTrueAndStatusIn(List.of(LandListingStatus.APPROVED, LandListingStatus.ACTIVE)).stream()
                .filter(property -> isVerifiedHost(property.getHostUserId()))
                .map(this::propertyOpportunity)
                .sorted(Comparator.comparing(PropertyOpportunityResponse::city,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    @Transactional
    public InstallationRequestResponse createRequest(Long hostUserId, InstallationCreateRequest input) {
        operationalControlService.assertHostCanStartPartnership(hostUserId);
        LandListing property = ownedProperty(hostUserId, input.propertyId());
        requireVerifiedHostProperty(hostUserId, property);
        Company company = companyRepository.findById(input.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("Charger company not found"));
        if (!verificationService.isMarketplaceVerified(company)) {
            throw new BadRequestException("This company is not Vidyut verified");
        }
        ChargerProduct product = productRepository.findById(input.productId())
                .filter(item -> item.isActive() && item.getApprovalStatus() == ProductApprovalStatus.APPROVED
                        && item.getCompany().getId().equals(company.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Selected charger product is unavailable"));
        if (!product.getBusinessModels().contains(input.businessModel())) {
            throw new BadRequestException("The selected business model is not offered for this charger");
        }
        InstallationRequest request = requestRepository.save(InstallationRequest.builder()
                .hostUserId(hostUserId).property(property).company(company).product(product)
                .quantity(input.quantity()).businessModel(input.businessModel()).budget(input.budget())
                .targetInstallationDate(input.targetInstallationDate()).hostMessage(clean(input.message()))
                .status(InstallationStatus.REQUESTED).build());
        addHistory(request, InstallationStatus.REQUESTED, hostUserId, "Host requested a proposal");
        notificationService.sendNotification(company.getAccount().getId(), "New Host installation request",
                property.getTitle() + " requested " + product.getModelName() + " × " + input.quantity(), NotificationType.SYSTEM_ALERT);
        return requestResponse(request);
    }

    public List<InstallationRequestResponse> hostRequests(Long hostUserId) {
        return requestRepository.findByHostUserIdOrderByUpdatedAtDesc(hostUserId).stream().map(this::requestResponse).toList();
    }

    public List<InstallationRequestResponse> companyRequests(Long companyAccountId) {
        requireCompany(companyAccountId);
        return requestRepository.findByCompany_Account_IdOrderByUpdatedAtDesc(companyAccountId).stream().map(this::requestResponse).toList();
    }

    @Transactional
    public InstallationRequestResponse sendProposal(Long companyAccountId, Long requestId, ProposalRequest input) {
        operationalControlService.assertCompanyMarketplaceAllowed(companyAccountId);
        verificationService.requireMarketplaceVerified(companyAccountId);
        InstallationRequest request = companyRequest(companyAccountId, requestId);
        if (!EnumSet.of(InstallationStatus.UNDER_REVIEW, InstallationStatus.SITE_SURVEY_REQUESTED,
                InstallationStatus.SITE_SURVEY_SCHEDULED, InstallationStatus.SURVEY_COMPLETED).contains(request.getStatus())) {
            throw new BadRequestException("Complete request review or survey before sending a proposal");
        }
        double combinedShare = number(input.hostRevenueSharePercent()) + number(input.companyRevenueSharePercent());
        if (combinedShare > 100.001) throw new BadRequestException("Combined revenue shares cannot exceed 100%");
        InstallationProposal proposal = proposalRepository.findByRequest_Id(requestId)
                .orElseGet(() -> InstallationProposal.builder().request(request).build());
        proposal.setEquipmentTotal(input.equipmentTotal());
        proposal.setInstallationTotal(input.installationTotal());
        proposal.setMonthlyLease(input.monthlyLease());
        proposal.setHostRevenueSharePercent(input.hostRevenueSharePercent());
        proposal.setCompanyRevenueSharePercent(input.companyRevenueSharePercent());
        proposal.setValidUntil(input.validUntil());
        proposal.setEstimatedInstallationDays(input.estimatedInstallationDays());
        proposal.setTerms(clean(input.terms()));
        proposalRepository.save(proposal);
        move(request, InstallationStatus.PROPOSAL_SENT, companyAccountId, "Company sent a commercial proposal");
        notificationService.sendNotification(request.getHostUserId(), "Installation proposal received",
                request.getCompany().getCompanyName() + " sent a proposal for " + request.getProperty().getTitle(), NotificationType.SYSTEM_ALERT);
        return requestResponse(request);
    }

    @Transactional
    public InstallationRequestResponse updateStatus(Long companyAccountId, Long requestId, InstallationStatusUpdateRequest input) {
        if (input.status() == InstallationStatus.LIVE) {
            operationalControlService.assertCompanyPublishingAllowed(companyAccountId);
        }
        InstallationRequest request = companyRequest(companyAccountId, requestId);
        if (!allowedCompanyTransition(request.getStatus(), input.status())) {
            throw new BadRequestException("Cannot move installation from " + request.getStatus() + " to " + input.status());
        }
        if (input.status() == InstallationStatus.SITE_SURVEY_SCHEDULED) request.setScheduledSurveyAt(requiredDate(input.scheduledDate(), "Survey date"));
        if (input.status() == InstallationStatus.INSTALLATION_SCHEDULED) request.setScheduledInstallationAt(requiredDate(input.scheduledDate(), "Installation date"));
        if (input.status() == InstallationStatus.COMMISSIONED) commission(request);
        if (input.status() == InstallationStatus.LIVE) activateStation(request);
        request.setCompanyNote(clean(input.note()));
        move(request, input.status(), companyAccountId, input.note());
        notificationService.sendNotification(request.getHostUserId(), "Installation updated",
                request.getCompany().getCompanyName() + " moved " + request.getProperty().getTitle() + " to " + input.status(), NotificationType.SYSTEM_ALERT);
        return requestResponse(request);
    }

    @Transactional
    public InstallationRequestResponse acceptProposal(Long hostUserId, Long requestId) {
        operationalControlService.assertHostCanStartPartnership(hostUserId);
        InstallationRequest request = hostRequest(hostUserId, requestId);
        if (request.getStatus() != InstallationStatus.PROPOSAL_SENT) throw new BadRequestException("No proposal is awaiting acceptance");
        InstallationProposal proposal = proposalRepository.findByRequest_Id(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Proposal not found"));
        if (proposal.getValidUntil().isBefore(java.time.LocalDate.now())) throw new BadRequestException("This proposal has expired");
        move(request, InstallationStatus.ACCEPTED, hostUserId, "Host accepted the proposal");
        notificationService.sendNotification(request.getCompany().getAccount().getId(), "Proposal accepted",
                request.getProperty().getTitle() + " accepted your installation proposal", NotificationType.SYSTEM_ALERT);
        return requestResponse(request);
    }

    @Transactional
    public InstallationRequestResponse cancelRequest(Long hostUserId, Long requestId) {
        InstallationRequest request = hostRequest(hostUserId, requestId);
        if (EnumSet.of(InstallationStatus.INSTALLING, InstallationStatus.INSTALLED, InstallationStatus.COMMISSIONED,
                InstallationStatus.LIVE, InstallationStatus.CANCELLED, InstallationStatus.DECLINED,
                InstallationStatus.EXPIRED).contains(request.getStatus())) {
            throw new BadRequestException("This installation request can no longer be cancelled");
        }
        move(request, InstallationStatus.CANCELLED, hostUserId, "Host cancelled the request");
        notificationService.sendNotification(request.getCompany().getAccount().getId(), "Installation request cancelled",
                request.getProperty().getTitle() + " cancelled its request", NotificationType.SYSTEM_ALERT);
        return requestResponse(request);
    }

    @Transactional
    public PropertyInterestResponse saveOpportunity(Long companyAccountId, Long propertyId) {
        operationalControlService.assertCompanyMarketplaceAllowed(companyAccountId);
        Company company = requireVerifiedCompany(companyAccountId);
        LandListing property = publishedVerifiedProperty(propertyId);
        CompanyPropertyInterest interest = interestRepository
                .findByCompany_IdAndProperty_Id(company.getId(), propertyId)
                .orElseGet(() -> interestRepository.save(CompanyPropertyInterest.builder()
                        .company(company).property(property).status(InterestStatus.SAVED).build()));
        return interestResponse(interest);
    }

    @Transactional
    public PropertyInterestResponse expressInterest(Long companyAccountId, Long propertyId, PropertyInterestRequest input) {
        operationalControlService.assertCompanyMarketplaceAllowed(companyAccountId);
        Company company = requireVerifiedCompany(companyAccountId);
        LandListing property = publishedVerifiedProperty(propertyId);
        var existing = interestRepository.findByCompany_IdAndProperty_Id(company.getId(), propertyId);
        if (existing.isPresent() && existing.get().getStatus() != InterestStatus.SAVED) {
            throw new DuplicateResourceException("Your company has already contacted this property");
        }
        CompanyPropertyInterest interest = existing.orElseGet(() -> CompanyPropertyInterest.builder()
                .company(company).property(property).build());
        interest.setMessage(clean(input.message()));
        interest.setStatus(InterestStatus.PENDING);
        interest = interestRepository.save(interest);
        notificationService.sendNotification(property.getHostUserId(), "Company interested in your property",
                company.getCompanyName() + " wants to discuss charger installation at " + property.getTitle(), NotificationType.SYSTEM_ALERT);
        return interestResponse(interest);
    }

    public List<PropertyInterestResponse> hostInterests(Long hostUserId) {
        return interestRepository.findByProperty_HostUserIdOrderByCreatedAtDesc(hostUserId).stream()
                .filter(interest -> interest.getStatus() != InterestStatus.SAVED)
                .map(this::interestResponse).toList();
    }

    public List<PropertyInterestResponse> companyInterests(Long companyAccountId) {
        requireCompany(companyAccountId);
        return interestRepository.findByCompany_Account_IdOrderByCreatedAtDesc(companyAccountId).stream().map(this::interestResponse).toList();
    }

    @Transactional
    public PropertyInterestResponse respondToInterest(Long hostUserId, Long interestId, InterestStatus status) {
        if (status == InterestStatus.ACCEPTED) operationalControlService.assertHostCanStartPartnership(hostUserId);
        if (status != InterestStatus.ACCEPTED && status != InterestStatus.DECLINED) throw new BadRequestException("Host may accept or decline company interest");
        CompanyPropertyInterest interest = interestRepository.findByIdAndProperty_HostUserId(interestId, hostUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Company interest not found"));
        if (interest.getStatus() != InterestStatus.PENDING) throw new BadRequestException("This interest has already been answered");
        interest.setStatus(status);
        interestRepository.save(interest);
        notificationService.sendNotification(interest.getCompany().getAccount().getId(), "Host response received",
                interest.getProperty().getTitle() + " " + status.name().toLowerCase() + " your interest", NotificationType.SYSTEM_ALERT);
        return interestResponse(interest);
    }

    private LandListing publishedVerifiedProperty(Long propertyId) {
        return landRepository.findById(propertyId)
                .filter(item -> item.isDiscoverable() && (item.getStatus() == LandListingStatus.APPROVED || item.getStatus() == LandListingStatus.ACTIVE))
                .filter(item -> isVerifiedHost(item.getHostUserId()))
                .orElseThrow(() -> new ResourceNotFoundException("Published Host property not found"));
    }

    private MarketplaceCompanyResponse companyMatch(Company company) {
        List<ChargerProductResponse> products = productRepository.findByCompany_IdAndActiveTrueAndApprovalStatusOrderByPowerKwAsc(
                        company.getId(), ProductApprovalStatus.APPROVED).stream()
                .map(this::productResponse).toList();
        if (products.isEmpty()) return null;
        return new MarketplaceCompanyResponse(company.getId(), company.getCompanyName(), company.getWebsite(),
                null, null, "VIDYUT_VERIFIED", "Available nationwide",
                null, products);
    }

    private PropertyOpportunityResponse propertyOpportunity(LandListing property) {
        HostProfile host = hostProfileRepository.findById(property.getHostUserId()).orElse(null);
        List<HostReview> reviews = hostReviewRepository.findByHostAccountIdOrderByCreatedAtDesc(property.getHostUserId());
        double hostRating = reviews.isEmpty()
                ? (host == null ? 0 : host.getReputationScore())
                : reviews.stream().mapToInt(HostReview::getRating).average().orElse(0);
        int disputes = (int) reviews.stream().filter(HostReview::isReported).count();
        int verifiedProperties = (int) landRepository.findByHostUserId(property.getHostUserId()).stream()
                .filter(item -> item.getStatus() == LandListingStatus.APPROVED || item.getStatus() == LandListingStatus.ACTIVE)
                .count();
        int successfulPartnerships = (int) requestRepository.findByHostUserIdOrderByUpdatedAtDesc(property.getHostUserId()).stream()
                .filter(item -> item.getStatus() == InstallationStatus.COMMISSIONED || item.getStatus() == InstallationStatus.LIVE)
                .count();
        boolean identityVerified = host != null && host.isVerified();
        boolean ownershipVerified = present(property.getOwnershipDocumentUrl());
        boolean electricityVerified = present(property.getElectricityDocumentUrl());
        boolean videoVerified = present(property.getVideoVerificationUrl());
        int hostTrustScore = clamp((identityVerified ? 45 : 0)
                + (int) Math.round(Math.max(0, Math.min(5, hostRating)) * 7)
                + Math.min(15, successfulPartnerships * 5) - Math.min(25, disputes * 10));
        int propertyScore = clamp((identityVerified ? 15 : 0) + (ownershipVerified ? 20 : 0)
                + (electricityVerified ? 15 : 0) + (videoVerified ? 15 : 0)
                + Math.min(20, property.getAvailableParkingBays() * 4)
                + Math.min(15, (int) Math.round(property.getAvailableLoadKw() / 5.0)));
        int commercialScore = clamp(35 + Math.min(25, property.getAvailableParkingBays() * 5)
                + Math.min(30, (int) Math.round(property.getAvailableLoadKw() / 3.0))
                + (present(property.getOperatingHours()) ? 10 : 0));
        boolean highCapacitySite = property.getPreferredPowerKw() >= 120 || property.getAvailableParkingBays() >= 8;
        String verificationRisk = !identityVerified || !ownershipVerified || !electricityVerified ? "HIGH"
                : !videoVerified ? "MEDIUM" : "LOW";
        boolean physicalInspectionRecommended = "HIGH".equals(verificationRisk) || highCapacitySite;
        String verificationMethod = physicalInspectionRecommended ? "PHYSICAL_SITE_INSPECTION"
                : "MEDIUM".equals(verificationRisk) ? "LIVE_VIDEO_SURVEY" : "DOCUMENT_AND_VIDEO_REVIEW";
        List<HostReviewSummaryResponse> publicReviews = reviews.stream()
                .filter(review -> !review.isReported())
                .limit(3)
                .map(review -> {
                    ChargingStation reviewedStation = stationRepository.findById(review.getStationId()).orElse(null);
                    return new HostReviewSummaryResponse(review.getRating(), review.getStationId(),
                            reviewedStation == null ? "Previous charger" : reviewedStation.getName(),
                            reviewedStation == null ? null : reviewedStation.getCity(),
                            publicReviewerName(review.getCustomerName()), review.getComment(), review.getHostReply(),
                            review.getCreatedAt());
                })
                .toList();
        return new PropertyOpportunityResponse(property.getId(), property.getTitle(), property.getAddress(), property.getCity(),
                property.getState(), property.getPincode(), property.getLatitude(), property.getLongitude(),
                enumName(property.getPropertyType()), property.getAvailableParkingBays(), enumName(property.getPowerPhase()),
                property.getAvailableLoadKw(), property.getOperatingHours(), enumName(property.getOwnershipType()),
                property.getPreferredConnectorType(), property.getPreferredPowerKw(), property.getPhotoUrls(),
                property.getVideoVerificationUrl(), "Nationwide Host listing", null,
                host == null ? "Verified Host" : host.getDisplayName(), host == null ? null : host.getBio(),
                host == null || host.getAccount() == null ? null : host.getAccount().getCreatedAt(),
                round(hostRating), reviews.size(), hostTrustScore,
                verifiedProperties, successfulPartnerships, disputes, propertyScore, commercialScore,
                verificationRisk, verificationMethod, identityVerified, ownershipVerified, electricityVerified,
                videoVerified, physicalInspectionRecommended, publicReviews);
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private String publicReviewerName(String name) {
        if (!present(name)) return "Verified driver";
        String[] parts = name.trim().split("\\s+");
        return parts.length == 1 ? parts[0] : parts[0] + " " + parts[parts.length - 1].charAt(0) + ".";
    }

    private boolean allowedCompanyTransition(InstallationStatus from, InstallationStatus to) {
        return switch (from) {
            case REQUESTED -> to == InstallationStatus.UNDER_REVIEW || to == InstallationStatus.DECLINED;
            case UNDER_REVIEW -> to == InstallationStatus.SITE_SURVEY_REQUESTED || to == InstallationStatus.DECLINED;
            case SITE_SURVEY_REQUESTED -> to == InstallationStatus.SITE_SURVEY_SCHEDULED || to == InstallationStatus.DECLINED;
            case SITE_SURVEY_SCHEDULED -> to == InstallationStatus.SURVEY_COMPLETED;
            case ACCEPTED -> to == InstallationStatus.INSTALLATION_SCHEDULED;
            case INSTALLATION_SCHEDULED -> to == InstallationStatus.INSTALLING;
            case INSTALLING -> to == InstallationStatus.INSTALLED;
            case INSTALLED -> to == InstallationStatus.COMMISSIONED;
            case COMMISSIONED -> to == InstallationStatus.LIVE;
            default -> false;
        };
    }

    private void commission(InstallationRequest request) {
        if (request.getStationId() != null) return;
        LandListing property = request.getProperty();
        Company company = request.getCompany();
        String hostName = hostProfileRepository.findById(request.getHostUserId())
                .map(HostProfile::getDisplayName).orElse("Host partner #" + request.getHostUserId());
        ChargingStation station = ChargingStation.builder()
                .name(property.getTitle()).address(property.getAddress()).city(property.getCity())
                .latitude(property.getLatitude()).longitude(property.getLongitude())
                .pricePerKwh(property.getPricePerKwh() > 0 ? property.getPricePerKwh() : 16)
                .workingHours(present(property.getOperatingHours()) ? property.getOperatingHours() : "Open 24 hours")
                .amenities("Parking").hostUserId(request.getHostUserId())
                .propertyOwnerAccountId(request.getHostUserId()).propertyOwnerName(hostName)
                .operatorCompanyId(company.getId()).operatorCompanyName(company.getCompanyName())
                .supplierCompanyId(company.getId()).equipmentOwnerName(company.getCompanyName())
                .ownershipType(StationOwnershipType.HOST_PARTNERED).hostPartnershipId(request.getId())
                .operatingModel("HOST_PROPERTY_CPO_EQUIPMENT")
                .sourceInstallationRequestId(request.getId())
                .status(StationStatus.OFFLINE).availability(StationAvailability.UNAVAILABLE).build();
        for (int index = 1; index <= request.getQuantity(); index++) {
            String code = "VY-INST-" + request.getId() + "-" + index;
            if (connectorRepository.existsByChargerCodeIgnoreCase(code)) code += "-" + System.currentTimeMillis();
            ChargingConnector connector = ChargingConnector.builder().station(station)
                    .type(request.getProduct().getConnectorType()).powerKw(request.getProduct().getPowerKw())
                    .chargerCode(code).available(false).status(ChargerStatus.OFFLINE).build();
            station.getConnectors().add(connector);
        }
        request.setStationId(stationRepository.save(station).getId());
    }

    private void activateStation(InstallationRequest request) {
        if (request.getStationId() == null) throw new BadRequestException("Commission the installation before making it live");
        ChargingStation station = stationRepository.findById(request.getStationId())
                .orElseThrow(() -> new ResourceNotFoundException("Commissioned station not found"));
        station.setStatus(StationStatus.ACTIVE);
        station.setAvailability(StationAvailability.AVAILABLE);
        station.setEmergencyDisabled(false);
        station.getConnectors().forEach(connector -> {
            connector.setAvailable(true);
            connector.setStatus(ChargerStatus.ONLINE);
            connector.setLastHeartbeat(LocalDateTime.now());
        });
        stationRepository.save(station);
    }

    private void move(InstallationRequest request, InstallationStatus status, Long actor, String note) {
        request.setStatus(status);
        request.setUpdatedAt(LocalDateTime.now());
        requestRepository.save(request);
        addHistory(request, status, actor, clean(note));
    }

    private void addHistory(InstallationRequest request, InstallationStatus status, Long actor, String note) {
        historyRepository.save(InstallationStatusHistory.builder().request(request).status(status)
                .actorAccountId(actor).note(note).build());
    }

    private InstallationRequestResponse requestResponse(InstallationRequest request) {
        InstallationProposal proposal = proposalRepository.findByRequest_Id(request.getId()).orElse(null);
        List<StatusHistoryResponse> history = historyRepository.findByRequest_IdOrderByCreatedAtAsc(request.getId()).stream()
                .map(item -> new StatusHistoryResponse(item.getId(), item.getStatus(), item.getActorAccountId(), item.getNote(), item.getCreatedAt())).toList();
        return new InstallationRequestResponse(request.getId(), request.getHostUserId(), request.getProperty().getId(),
                request.getProperty().getTitle(), request.getProperty().getAddress(), request.getProperty().getCity(),
                request.getCompany().getId(), request.getCompany().getCompanyName(), request.getProduct().getId(),
                request.getProduct().getModelName(), request.getProduct().getConnectorType().name(), request.getProduct().getPowerKw(),
                request.getQuantity(), request.getBusinessModel(), request.getBudget(), request.getTargetInstallationDate(),
                request.getHostMessage(), request.getCompanyNote(), request.getScheduledSurveyAt(), request.getScheduledInstallationAt(),
                request.getStationId(), request.getStatus(), proposal == null ? null : proposalResponse(proposal), history,
                request.getCreatedAt(), request.getUpdatedAt());
    }

    private ProposalResponse proposalResponse(InstallationProposal proposal) {
        return new ProposalResponse(proposal.getId(), proposal.getEquipmentTotal(), proposal.getInstallationTotal(),
                proposal.getMonthlyLease(), proposal.getHostRevenueSharePercent(), proposal.getCompanyRevenueSharePercent(),
                proposal.getValidUntil(), proposal.getEstimatedInstallationDays(), proposal.getTerms());
    }

    private ServiceAreaResponse areaResponse(CompanyServiceArea area) {
        return new ServiceAreaResponse(area.getId(), area.getCity(), area.getState(), area.getPincode(), area.getLatitude(),
                area.getLongitude(), area.getRadiusKm(), area.isInstallationAvailable(), area.isMaintenanceAvailable(),
                area.getSurveyFee(), area.getTypicalInstallationDays(), area.isActive());
    }

    private ChargerProductResponse productResponse(ChargerProduct product) {
        return new ChargerProductResponse(product.getId(), product.getCompany().getId(), product.getCompany().getCompanyName(),
                product.getModelName(), product.getManufacturer(), product.getCurrentType(), product.getConnectorType(),
                product.getPowerKw(), product.getEquipmentPrice(), product.getInstallationPrice(), product.getWarrantyMonths(),
                product.isAmcAvailable(), product.getCertifications(), product.getDescription(), product.getImageUrl(),
                Set.copyOf(product.getBusinessModels()), product.isActive(), product.getComplianceDocumentUrl(),
                product.getApprovalStatus(), product.getAdminReviewNote());
    }

    private PropertyInterestResponse interestResponse(CompanyPropertyInterest interest) {
        boolean unlocked = interest.getStatus() == InterestStatus.ACCEPTED;
        var host = hostProfileRepository.findById(interest.getProperty().getHostUserId()).orElse(null);
        var hostAccount = accountRepository.findById(interest.getProperty().getHostUserId()).orElse(null);
        return new PropertyInterestResponse(interest.getId(), interest.getCompany().getId(), interest.getCompany().getCompanyName(),
                interest.getProperty().getId(), interest.getProperty().getTitle(), interest.getProperty().getCity(),
                interest.getMessage(), interest.getStatus(), interest.getCreatedAt(), unlocked,
                unlocked ? interest.getCompany().getSupportEmail() : null,
                unlocked ? interest.getCompany().getSupportPhone() : null,
                unlocked && hostAccount != null ? hostAccount.getEmail() : null,
                unlocked && host != null ? host.getPhone() : null);
    }

    private Company requireCompany(Long accountId) {
        return companyRepository.findByAccount_Id(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Company profile not found"));
    }

    private Company requireVerifiedCompany(Long accountId) {
        return verificationService.requireMarketplaceVerified(accountId);
    }

    private boolean isVerifiedHost(Long accountId) {
        return hostProfileRepository.findById(accountId)
                .map(profile -> profile.isVerified() && profile.getVerificationStatus()
                        == com.vidyut.account.entity.HostVerificationStatus.VERIFIED)
                .orElse(false);
    }

    private void requireVerifiedHostProperty(Long accountId, LandListing property) {
        if (!isVerifiedHost(accountId)) throw new BadRequestException("Host identity verification is required before commercial contact");
        if (!property.isDiscoverable() || (property.getStatus() != LandListingStatus.APPROVED
                && property.getStatus() != LandListingStatus.ACTIVE)) {
            throw new BadRequestException("This property must pass Vidyut review before contacting companies");
        }
    }

    private LandListing ownedProperty(Long hostUserId, Long propertyId) {
        return landRepository.findByIdAndHostUserId(propertyId, hostUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Property not found for this Host account"));
    }

    private InstallationRequest hostRequest(Long hostUserId, Long requestId) {
        return requestRepository.findByIdAndHostUserId(requestId, hostUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Installation request not found"));
    }

    private InstallationRequest companyRequest(Long companyAccountId, Long requestId) {
        return requestRepository.findByIdAndCompany_Account_Id(requestId, companyAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Installation request not found for this company"));
    }

    private java.time.LocalDate requiredDate(java.time.LocalDate value, String label) {
        if (value == null) throw new BadRequestException(label + " is required for this status");
        return value;
    }

    private double number(Double value) { return value == null ? 0 : value; }
    private boolean present(String value) { return value != null && !value.isBlank(); }
    private String clean(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }
    private String enumName(Enum<?> value) { return value == null ? null : value.name(); }

}
