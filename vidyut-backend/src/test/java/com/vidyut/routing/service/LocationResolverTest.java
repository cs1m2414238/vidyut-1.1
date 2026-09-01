package com.vidyut.routing.service;

import com.vidyut.common.exception.BadRequestException;
import com.vidyut.routing.client.GeocodingClient;
import com.vidyut.routing.dto.Coordinate;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LocationResolverTest {

    @Test
    void resolvesArbitraryPlaceNamesThroughConfiguredGeocoderAndCachesThem() {
        GeocodingClient geocoder = mock(GeocodingClient.class);
        LocationResolver resolver = resolver(geocoder);
        when(geocoder.geocode("Satna Railway Station"))
                .thenReturn(Optional.of(new Coordinate(24.5805, 80.8322)));

        assertThat(resolver.resolve("Satna Railway Station"))
                .isEqualTo(new Coordinate(24.5805, 80.8322));
        assertThat(resolver.resolve("  SATNA   RAILWAY STATION "))
                .isEqualTo(new Coordinate(24.5805, 80.8322));

        verify(geocoder).geocode("Satna Railway Station");
    }

    @Test
    void acceptsExplicitCoordinatesWithoutCallingGeocoder() {
        GeocodingClient geocoder = mock(GeocodingClient.class);
        LocationResolver resolver = resolver(geocoder);

        assertThat(resolver.resolve("24.5805, 80.8322"))
                .isEqualTo(new Coordinate(24.5805, 80.8322));
        verifyNoInteractions(geocoder);
    }

    @Test
    void rejectsUnknownLocationsInsteadOfFallingBackToAnotherCity() {
        GeocodingClient geocoder = mock(GeocodingClient.class);
        LocationResolver resolver = resolver(geocoder);
        when(geocoder.geocode("not a real place")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve("not a real place"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Could not find");
    }

    private LocationResolver resolver(GeocodingClient geocoder) {
        LocationResolver resolver = new LocationResolver(geocoder);
        ReflectionTestUtils.setField(resolver, "cacheSize", 10);
        return resolver;
    }
}
