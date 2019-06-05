package com.navatar.maps.test;

import com.navatar.maps.particles.ParticleState;
import com.referencepoint.proto.BuildingMapProto.Building;

import junit.framework.TestCase;


public class MapsTest extends TestCase {

    public void testGetRoomLocation () {

        Building instance = Building.getDefaultInstance();
        com.navatar.maps.Building wrapper = new com.navatar.maps.Building(instance);

        ParticleState state = wrapper.getRoomLocation("Test");

        assertNull(state);
    }
}