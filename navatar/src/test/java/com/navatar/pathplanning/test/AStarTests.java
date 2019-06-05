package com.navatar.pathplanning.test;

import com.navatar.maps.Building;
import com.referencepoint.proto.BuildingMapProto;

import org.junit.Test;

public class AStarTests {

    private com.navatar.maps.Building mBuilding;

    public AStarTests() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        BuildingMapProto.Building map = BuildingMapProto.Building.parseFrom(classLoader.getResourceAsStream("test.nvm"));
        mBuilding = new Building(map);
    }


    @Test
    public void testFindPath() {

    }
}
