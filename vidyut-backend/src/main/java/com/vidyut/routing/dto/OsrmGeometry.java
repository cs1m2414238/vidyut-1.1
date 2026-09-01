package com.vidyut.routing.dto;

import java.util.*;

//geometries=geojson

public record OsrmGeometry (String type ,List<List<Double>> coordinates){
}
