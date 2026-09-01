package com.vidyut.agent.tool;

import org.springframework.stereotype.Component;

@Component
public class RoutingTool {
    public String calculateOptimizedWaypoints(String origin, String destination) {
        return "Optimal waypoints calculated with 2 fast charging stops";
    }
}
