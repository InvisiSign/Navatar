package com.navatar.maps.test;

import com.navatar.maps.Building;
import com.navatar.maps.particles.ParticleState;
import com.referencepoint.proto.BuildingMapProto;

import junit.framework.TestCase;

import java.io.IOException;

public class BuildingMapWrapperTests extends TestCase {

    public void testNewBuildingMapWrapper () {
        ClassLoader classLoader = getClass().getClassLoader();

        try {
            BuildingMapProto.Building map = BuildingMapProto.Building.parseFrom(classLoader.getResourceAsStream("test.pb"));
            Building wrapper = new Building(map);

            assertNotNull(wrapper);

        } catch (IOException e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }

    public void testGetRoomLocation() {
        ClassLoader classLoader = getClass().getClassLoader();

        try {
            BuildingMapProto.Building map = BuildingMapProto.Building.parseFrom(classLoader.getResourceAsStream("test.pb"));
            Building wrapper = new Building(map);

            ParticleState state = wrapper.getRoomLocation("401");

            assertNotNull(state);

        } catch (IOException e) {
            e.printStackTrace();
            assertTrue(false);
        }
    }
}
