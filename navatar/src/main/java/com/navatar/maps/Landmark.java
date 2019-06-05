package com.navatar.maps;

import com.navatar.maps.particles.ParticleState;
import com.navatar.math.Constants;
import com.referencepoint.proto.LandmarkProto;

import java.lang.reflect.Field;

public class Landmark implements Comparable<Landmark> {
    /**
     * The {@link Map} that contains this {@link Landmark}
     */
    private Map mMap;
    /**
     * The {@link Building} that contains this {@link Landmark} or null if
     * outdoors
     */
    private Building mBuilding;
    /**
     * The original protobuf landmark.
     * */
    private LandmarkProto.Landmark landmark;
    /**
     * The landmarks weight which is based on its distance from the tile.
     * */
    private double weight;
    /**
     * The {@link ParticleState} of the {@link Landmark}
     */
    private ParticleState mParticleState;

    public Landmark(LandmarkProto.Landmark landmark) {
        this.landmark = landmark;

    }

    public Landmark(LandmarkProto.Landmark landmark, Map map) {
        this(landmark);
        mMap = map;
    }

    public Landmark(LandmarkProto.Landmark landmark, Map map, Building building) {
        this(landmark, map);
        mBuilding = building;
    }

    public Map getMap() { return mMap; }

    public void setMap(Map map) { mMap = map; }

    public Building getBuilding() {
        return mBuilding;
    }

    public void setBuilding(Building building) {
        this.mBuilding = building;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public double getWeight() {
        return weight;
    }

    public LandmarkProto.Landmark getLandmark() {
        return landmark;
    }

    public void setLandmark(LandmarkProto.Landmark landmark) {
        this.landmark = landmark;
    }

    @Override
    public int compareTo(Landmark o) {
        if (Math.abs(this.weight - o.weight) < Constants.DOUBLE_ACCURACY)
            return 0;
        if (this.weight < o.weight)
            return -1;
        return 1;
    }

    @Override
    public boolean equals(Object o) {
        Landmark ol = (Landmark)o;
        if(ol != null && ol.getName().equals(getName())) {
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        String t_string = "Room ";
        t_string=(landmark.getName().length()>10)?(""):t_string;
        return t_string+ landmark.getName().toString()+"\n";
    }

    public void setName(String newName){

        try{
            Field f = landmark.getClass().getDeclaredField("name_");
            f.setAccessible(true);
            f.set(landmark,newName);
        } catch(NoSuchFieldException e){

        } catch (IllegalAccessException e){ }
    }

    public String getName() {
        return getLandmark().getName();
    }

    public double getX() {
        return landmark.getLocation().getX();
    }

    public double getY() {
        return landmark.getLocation().getY();
    }

    public LandmarkProto.Landmark.LandmarkType getType() {
        return landmark.getType();
    }

    public ParticleState getParticleState() {
        return mParticleState;
    }

}
