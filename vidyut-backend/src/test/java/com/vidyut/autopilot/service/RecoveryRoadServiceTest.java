package com.vidyut.autopilot.service;
import com.vidyut.routing.client.OsrmClient;
import com.vidyut.routing.dto.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecoveryRoadServiceTest {
    @Test void rejectsEstimatedRoadRoutesEvenWhenTheyLookFeasible() {
        OsrmClient client=mock(OsrmClient.class);
        var points=List.of(new Coordinate(28,77),new Coordinate(27.9,77));
        when(client.getBestRoute(points)).thenReturn(new OsrmClient.RouteSelection(null, OsrmClient.RouteEngine.ESTIMATED));
        assertThatThrownBy(()->new RecoveryRoadService(client).route(points)).hasMessageContaining("ROAD_ROUTE_UNVERIFIED");
    }
    @Test void rejectsEstimatedMatrixCells() {
        OsrmClient client=mock(OsrmClient.class);
        var points=List.of(new Coordinate(28,77),new Coordinate(27.9,77));
        when(client.getVerifiedFullTable(points, OsrmClient.RouteEngine.PRIMARY)).thenReturn(new OsrmClient.MatrixSelection(null,OsrmClient.RouteEngine.PRIMARY,true));
        assertThatThrownBy(()->new RecoveryRoadService(client).matrix(points,OsrmClient.RouteEngine.PRIMARY)).hasMessageContaining("estimated matrix");
    }
}
