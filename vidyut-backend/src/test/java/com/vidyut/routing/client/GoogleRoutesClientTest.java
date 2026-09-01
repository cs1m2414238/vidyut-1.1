package com.vidyut.routing.client;

import com.vidyut.routing.dto.Coordinate;
import com.vidyut.routing.dto.OsrmResponse;
import com.vidyut.routing.dto.OsrmTableResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleRoutesClientTest {

    @Test
    void polylineDecodingTransformsEncodedStringToGeoJson() {
        // Sample polyline encoding
        String encoded = "_p~iF~ps|U_ulLnnqC_mqNvxq`@";
        List<List<Double>> coords = GoogleRoutesClient.decodePolyline(encoded);

        assertThat(coords).isNotEmpty();
        // GeoJSON coordinate order is [longitude, latitude]
        assertThat(coords.get(0)).hasSize(2);
    }

    @Test
    void computeRoutesCallsGoogleApiAndMapsToOsrmResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GoogleRoutesClient client = new GoogleRoutesClient(builder, "AIzaSyFakeKeyForTesting123", true);

        String jsonResponse = """
                {
                  "routes": [
                    {
                      "distanceMeters": 85000,
                      "duration": "5400s",
                      "polyline": {
                        "encodedPolyline": "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
                      },
                      "legs": [
                        {
                          "distanceMeters": 85000,
                          "duration": "5400s"
                        }
                      ]
                    }
                  ]
                }
                """;

        server.expect(requestTo("https://routes.googleapis.com/directions/v2:computeRoutes"))
                .andExpect(header("X-Goog-Api-Key", "AIzaSyFakeKeyForTesting123"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        OsrmResponse response = client.getRoute(List.of(
                new Coordinate(26.4499, 80.3319),
                new Coordinate(26.8467, 80.9462)
        ));

        assertThat(response.code()).isEqualTo("Ok");
        assertThat(response.routes()).singleElement().satisfies(route -> {
            assertThat(route.distance()).isEqualTo(85000.0);
            assertThat(route.duration()).isEqualTo(5400.0);
            assertThat(route.geometry().coordinates()).isNotEmpty();
        });
        server.verify();
    }

    @Test
    void computeRouteMatrixReturnsProperTableResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GoogleRoutesClient client = new GoogleRoutesClient(builder, "AIzaSyFakeKeyForTesting123", true);

        String jsonResponse = """
                [
                  {
                    "originIndex": 0,
                    "destinationIndex": 0,
                    "distanceMeters": 0,
                    "duration": "0s"
                  },
                  {
                    "originIndex": 0,
                    "destinationIndex": 1,
                    "distanceMeters": 85000,
                    "duration": "5400s"
                  },
                  {
                    "originIndex": 1,
                    "destinationIndex": 0,
                    "distanceMeters": 85000,
                    "duration": "5400s"
                  },
                  {
                    "originIndex": 1,
                    "destinationIndex": 1,
                    "distanceMeters": 0,
                    "duration": "0s"
                  }
                ]
                """;

        server.expect(requestTo("https://routes.googleapis.com/distanceMatrix/v2:computeRouteMatrix"))
                .andExpect(header("X-Goog-Api-Key", "AIzaSyFakeKeyForTesting123"))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        OsrmTableResponse table = client.getFullTable(List.of(
                new Coordinate(26.4499, 80.3319),
                new Coordinate(26.8467, 80.9462)
        ));

        assertThat(table.code()).isEqualTo("Ok");
        assertThat(table.distances()).hasSize(2);
        assertThat(table.durations()).hasSize(2);
        assertThat(table.distances().get(0).get(1)).isEqualTo(85000.0);
        assertThat(table.durations().get(0).get(1)).isEqualTo(5400.0);
        server.verify();
    }
}
