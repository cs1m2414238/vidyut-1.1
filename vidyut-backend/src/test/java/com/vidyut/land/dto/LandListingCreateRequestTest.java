package com.vidyut.land.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LandListingCreateRequestTest {

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void acceptsDraftWithoutOwnershipEvidence() {
        LandListingCreateRequest request = validRequest();
        request.setOwnershipDocumentUrl("");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsLocalOwnershipDocumentPath() {
        LandListingCreateRequest request = validRequest();
        request.setOwnershipDocumentUrl("C:\\Documents\\ownership-deed.pdf");

        assertThat(validator.validate(request))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("ownershipDocumentUrl")
                        && violation.getMessage().equals("Ownership document URL must use http or https"));
    }

    @Test
    void acceptsAccessibleOwnershipDocumentUrl() {
        assertThat(validator.validate(validRequest())).isEmpty();
    }

    private LandListingCreateRequest validRequest() {
        LandListingCreateRequest request = new LandListingCreateRequest();
        request.setTitle("Prince Kanpur Property");
        request.setAddress("NH-19, Kanpur, Uttar Pradesh");
        request.setPincode("208001");
        request.setOwnershipDocumentUrl("https://documents.vidyut.demo/prince-kanpur-ownership.pdf");
        return request;
    }
}
