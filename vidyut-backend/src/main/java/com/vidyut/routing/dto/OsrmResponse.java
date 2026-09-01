package com.vidyut.routing.dto;

import java.util.List;

public record OsrmResponse(
        String code,
        List<OsrmRoute> routes
) {
}

//   JSON response
//{
//  "code": "Ok",  STRING
//    LIST
//   "routes": [
//    {
//      "distance": 91234,
//      "duration": 5300,
//      "geometry": {
//         "type": "LineString",
//         "coordinates": []
//       }
//     }
//   ]
// }