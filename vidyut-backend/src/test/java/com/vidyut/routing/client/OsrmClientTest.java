package com.vidyut.routing.client;

import com.vidyut.routing.dto.Coordinate;
import com.vidyut.routing.exception.OsrmException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OsrmClientTest {

    @Test
    void readsRoadDistanceAndDurationFromConfiguredOsrm() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OsrmClient client = new OsrmClient(builder, "http://localhost:5000", "", "driving", 20000, 100);

        server.expect(requestTo(
                        "http://localhost:5000/route/v1/driving/80.3217588%2C26.4609135%3B77.4126%2C23.2599"
                                + "?overview=full&geometries=geojson&radiuses=20000.0%3B20000.0"))
                .andRespond(withSuccess(
                        "{\"code\":\"Ok\",\"routes\":[{\"distance\":528000,\"duration\":23100}]}",
                        MediaType.APPLICATION_JSON));

        var response = client.getRoute(
                new Coordinate(26.4609135, 80.3217588),
                new Coordinate(23.2599, 77.4126)
        );

        assertThat(response.code()).isEqualTo("Ok");
        assertThat(response.routes()).singleElement().satisfies(route -> {
            assertThat(route.distance()).isEqualTo(528000);
            assertThat(route.duration()).isEqualTo(23100);
        });
        server.verify();
    }

    @Test
    void calculatesTheRouteThroughEverySelectedChargingStop() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OsrmClient client = new OsrmClient(builder, "http://localhost:5000", "", "driving", 20000, 100);

        server.expect(requestTo(
                        "http://localhost:5000/route/v1/driving/80.3%2C26.4%3B79.0%2C26.7%3B78.0%2C27.1%3B77.2%2C28.6"
                                + "?overview=full&geometries=geojson&radiuses=20000.0%3B20000.0%3B20000.0%3B20000.0"))
                .andRespond(withSuccess(
                        "{\"code\":\"Ok\",\"routes\":[{\"distance\":531000,\"duration\":25000,"
                                + "\"legs\":[{\"distance\":120000,\"duration\":6000},"
                                + "{\"distance\":160000,\"duration\":7000},"
                                + "{\"distance\":251000,\"duration\":12000}]}]}",
                        MediaType.APPLICATION_JSON));

        var response = client.getRoute(List.of(
                new Coordinate(26.4, 80.3),
                new Coordinate(26.7, 79.0),
                new Coordinate(27.1, 78.0),
                new Coordinate(28.6, 77.2)
        ));

        assertThat(response.routes()).singleElement().satisfies(route -> {
            assertThat(route.distance()).isEqualTo(531000);
            assertThat(route.legs()).extracting(leg -> leg.distance())
                    .containsExactly(120000.0, 160000.0, 251000.0);
        });
        server.verify();
    }

    @Test
    void buildsStationMatrixIndexesInOsrmOrder() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OsrmClient client = new OsrmClient(builder, "http://localhost:5000", "", "driving", 20000, 100);

        server.expect(requestTo(
                        "http://localhost:5000/table/v1/driving/80.3%2C26.4%3B79.0%2C26.7%3B78.0%2C27.1%3B77.4%2C23.2"
                                + "?sources=0%3B1%3B2&destinations=1%3B2%3B3&annotations=distance,duration"
                                + "&radiuses=20000.0%3B20000.0%3B20000.0%3B20000.0"))
                .andRespond(withSuccess(
                        "{\"code\":\"Ok\",\"distances\":[[1000,2000,3000],[0,1000,2000],[1000,0,1000]],"
                                + "\"durations\":[[10,20,30],[0,10,20],[10,0,10]]}",
                        MediaType.APPLICATION_JSON));

        var response = client.getMatrixTable(
                new Coordinate(26.4, 80.3),
                List.of(new Coordinate(26.7, 79.0), new Coordinate(27.1, 78.0)),
                new Coordinate(23.2, 77.4)
        );

        assertThat(response.distances()).hasSize(3);
        assertThat(response.distances().get(0)).containsExactly(1000.0, 2000.0, 3000.0);
        server.verify();
    }

    @Test
    void isolatesAndSkipsOnlyStationsOutsideTheLoadedMap() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OsrmClient client = new OsrmClient(builder, "http://localhost:5000", "", "driving", 20000, 100);

        server.expect(requestTo(
                        "http://localhost:5000/table/v1/driving/80.3%2C26.4%3B79.0%2C26.7%3B75.7%2C26.8%3B77.4%2C23.2"
                                + "?sources=0%3B1%3B2&destinations=1%3B2%3B3&annotations=distance,duration"
                                + "&radiuses=20000.0%3B20000.0%3B20000.0%3B20000.0"))
                .andRespond(withBadRequest()
                        .body("{\"message\":\"Could not find a matching segment\",\"code\":\"NoSegment\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        server.expect(requestTo(
                        "http://localhost:5000/table/v1/driving/80.3%2C26.4%3B79.0%2C26.7%3B77.4%2C23.2"
                                + "?sources=0%3B1&destinations=1%3B2&annotations=distance,duration"
                                + "&radiuses=20000.0%3B20000.0%3B20000.0"))
                .andRespond(withSuccess(
                        "{\"code\":\"Ok\",\"distances\":[[1000,3000],[0,2000]],"
                                + "\"durations\":[[10,30],[0,20]]}",
                        MediaType.APPLICATION_JSON));

        server.expect(requestTo(
                        "http://localhost:5000/table/v1/driving/80.3%2C26.4%3B75.7%2C26.8%3B77.4%2C23.2"
                                + "?sources=0%3B1&destinations=1%3B2&annotations=distance,duration"
                                + "&radiuses=20000.0%3B20000.0%3B20000.0"))
                .andRespond(withBadRequest()
                        .body("{\"message\":\"Could not find a matching segment\",\"code\":\"NoSegment\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        List<OsrmClient.MatrixBatch> batches = client.getMatrixTables(
                new Coordinate(26.4, 80.3),
                List.of(new Coordinate(26.7, 79.0), new Coordinate(26.8, 75.7)),
                new Coordinate(23.2, 77.4)
        );

        assertThat(batches).singleElement().satisfies(batch -> {
            assertThat(batch.stationIndexes()).containsExactly(0);
            assertThat(batch.stationCoordinates()).containsExactly(new Coordinate(26.7, 79.0));
        });
        server.verify();
    }

    @Test
    void marksAnEndpointOutsideTheLoadedMapAsACoverageError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OsrmClient client = new OsrmClient(builder, "http://localhost:5000", "", "driving", 20000, 100);

        server.expect(requestTo(
                        "http://localhost:5000/route/v1/driving/72.8777%2C19.076%3B73.8567%2C18.5204"
                                + "?overview=full&geometries=geojson&radiuses=20000.0%3B20000.0"))
                .andRespond(withBadRequest()
                        .body("{\"message\":\"Could not find a matching segment\",\"code\":\"NoSegment\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.getRoute(
                new Coordinate(19.076, 72.8777),
                new Coordinate(18.5204, 73.8567)
        )).isInstanceOfSatisfying(
                OsrmException.class,
                exception -> assertThat(exception.isLocationOutsideCoverage()).isTrue()
        );
        server.verify();
    }

    @Test
    void degradesToAConservativeRouteWhenRoadRoutingIsUnavailable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OsrmClient client = new OsrmClient(builder, "http://localhost:5000", "", "driving", 20000, 100);

        server.expect(requestTo(
                        "http://localhost:5000/route/v1/driving/80.3217588%2C26.4609135%3B77.4126%2C23.2599"
                                + "?overview=full&geometries=geojson&radiuses=20000.0%3B20000.0"))
                .andRespond(withServerError());

        var selection = client.getBestRoute(List.of(
                new Coordinate(26.4609135, 80.3217588),
                new Coordinate(23.2599, 77.4126)
        ));

        assertThat(selection.engine()).isEqualTo(OsrmClient.RouteEngine.ESTIMATED);
        assertThat(selection.response().code()).isEqualTo("Ok");
        assertThat(selection.response().routes()).singleElement().satisfies(route -> {
            assertThat(route.distance()).isGreaterThan(500_000);
            assertThat(route.duration()).isGreaterThan(0);
            assertThat(route.geometry().coordinates()).hasSize(2);
            assertThat(route.legs()).hasSize(1);
        });
        server.verify();
    }

    @Test
    void degradesToACompleteConservativeMatrixWhenTheTableEndpointFails() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OsrmClient client = new OsrmClient(builder, "http://localhost:5000", "", "driving", 20000, 100);
        List<Coordinate> coordinates = List.of(
                new Coordinate(26.4609135, 80.3217588),
                new Coordinate(25.4358, 78.5683),
                new Coordinate(23.2599, 77.4126)
        );

        server.expect(requestTo(
                        "http://localhost:5000/table/v1/driving/80.3217588%2C26.4609135%3B78.5683%2C25.4358%3B77.4126%2C23.2599"
                                + "?annotations=distance,duration&radiuses=20000.0%3B20000.0%3B20000.0"))
                .andRespond(withServerError());

        var selection = client.getBestFullTable(coordinates, OsrmClient.RouteEngine.PRIMARY);

        assertThat(selection.engine()).isEqualTo(OsrmClient.RouteEngine.ESTIMATED);
        assertThat(selection.estimatedCells()).isTrue();
        assertThat(selection.response().distances()).hasSize(3);
        assertThat(selection.response().distances()).allSatisfy(row -> assertThat(row).hasSize(3));
        assertThat(selection.response().distances().get(0).get(2)).isGreaterThan(500_000);
        assertThat(selection.response().durations().get(0).get(2)).isGreaterThan(0);
        server.verify();
    }

    @Test
    void degradesStationBatchesWithoutChangingTheirIndexLayout() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OsrmClient client = new OsrmClient(builder, "http://localhost:5000", "", "driving", 20000, 100);
        Coordinate origin = new Coordinate(26.4609135, 80.3217588);
        Coordinate station = new Coordinate(25.4358, 78.5683);
        Coordinate destination = new Coordinate(23.2599, 77.4126);

        server.expect(requestTo(
                        "http://localhost:5000/table/v1/driving/80.3217588%2C26.4609135%3B78.5683%2C25.4358%3B77.4126%2C23.2599"
                                + "?annotations=distance,duration&radiuses=20000.0%3B20000.0%3B20000.0"))
                .andRespond(withServerError());

        List<OsrmClient.MatrixBatch> batches = client.getBestMatrixTables(
                origin, List.of(station), destination, OsrmClient.RouteEngine.PRIMARY);

        assertThat(batches).singleElement().satisfies(batch -> {
            assertThat(batch.stationIndexes()).containsExactly(0);
            assertThat(batch.stationCoordinates()).containsExactly(station);
            assertThat(batch.response().distances()).hasSize(2);
            assertThat(batch.response().distances()).allSatisfy(row -> assertThat(row).hasSize(2));
            assertThat(batch.response().distances().get(0).get(0)).isGreaterThan(0);
            assertThat(batch.response().distances().get(0).get(1)).isGreaterThan(500_000);
            assertThat(batch.response().distances().get(1).get(0)).isZero();
            assertThat(batch.response().distances().get(1).get(1)).isGreaterThan(0);
        });
        server.verify();
    }

    @Test
    void fillsOnlyUnreachableMatrixCellsAndKeepsTheOsrmEngine() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OsrmClient client = new OsrmClient(builder, "http://localhost:5000", "", "driving", 20000, 100);
        List<Coordinate> coordinates = List.of(
                new Coordinate(26.4, 80.3),
                new Coordinate(26.7, 79.0),
                new Coordinate(23.2, 77.4)
        );

        server.expect(requestTo(
                        "http://localhost:5000/table/v1/driving/80.3%2C26.4%3B79.0%2C26.7%3B77.4%2C23.2"
                                + "?annotations=distance,duration&radiuses=20000.0%3B20000.0%3B20000.0"))
                .andRespond(withSuccess(
                        "{\"code\":\"Ok\",\"distances\":[[0,1000,null],[1000,0,2000],[null,2000,0]],"
                                + "\"durations\":[[0,10,null],[10,0,20],[null,20,0]]}",
                        MediaType.APPLICATION_JSON));

        var selection = client.getBestFullTable(coordinates, OsrmClient.RouteEngine.PRIMARY);

        assertThat(selection.engine()).isEqualTo(OsrmClient.RouteEngine.PRIMARY);
        assertThat(selection.estimatedCells()).isTrue();
        assertThat(selection.response().distances().get(0).get(1)).isEqualTo(1000);
        assertThat(selection.response().distances().get(0).get(2)).isNotNull().isGreaterThan(0);
        server.verify();
    }
}
