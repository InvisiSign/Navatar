package com.navatar.data.source.local;


import android.arch.persistence.room.TypeConverter;

import com.navatar.maps.Building;
import com.navatar.maps.Landmark;
import com.navatar.maps.Map;

/**
 * @author Chris Daley
 */
public class RouteTypeConverter {

    @TypeConverter
    public static Landmark toLandmark(String id) {
        return new Landmark(null);
    }

    @TypeConverter
    public static String landmarkToString(Landmark landmark) {
        return landmark.getName();
    }

    @TypeConverter
    public static Building toBuilding(String id) {
        return null;
        //return new Building(null);
    }

    @TypeConverter
    public static String buildingToString(Building building) {
        return building.getName();
    }

    @TypeConverter
    public static Map toMap(String name) {
        return new Map(name, name.replace('_', ' '));
    }

    @TypeConverter
    public static String mapToString(Map map) {
        return map.getId();
    }

}