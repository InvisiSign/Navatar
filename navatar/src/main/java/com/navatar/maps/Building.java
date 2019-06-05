package com.navatar.maps;

import com.navatar.maps.particles.ParticleState;
import com.referencepoint.proto.BuildingMapProto;
import com.referencepoint.proto.CoordinatesProto;
import com.referencepoint.proto.FloorProto;
import com.referencepoint.proto.LandmarkProto;
import com.referencepoint.proto.MinimapProto;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Building {
    /**
     * Stores the protobuf map instance.
     */
    private BuildingMapProto.Building protoMap;
    /**
     * The minimap grid that stores the tiles.
     */
    private HashMap<Integer, Tile[][]> minimaps = new HashMap<>();
    /**
     * The accessible spaces of the map divided by floor.
     */
    //private HashMap<Integer, Path> accessibleSpaces = new HashMap<>();
    /**
     * Dispatches floor indices from floor numbers.
     */
    private HashMap<Integer, Integer> floorsToIndices = new HashMap<>();

    private List<Landmark> mLandmarks = new ArrayList<>();

    /**
     * The {@link com.navatar.maps.Map} the contains this {@link Building}
     */
    private Map mMap;


    public Building(InputStream strem) throws IOException {
        this(BuildingMapProto.Building.parseFrom(strem));
    }

    public Building(BuildingMapProto.Building map) {
        this.protoMap = map;
        int i = 0;
        for (FloorProto.Floor floor : map.getFloorsList()) {
            MinimapProto.Minimap minimap = floor.getMinimap();
            Tile[][] floorTiles = new Tile[minimap.getRows()][minimap.getColumns()];
            for (MinimapProto.Minimap.Tile tile : minimap.getTilesList()) {
                floorTiles[tile.getRow()][tile.getColumn()] = new Tile(tile,
                        floor.getLandmarksList(), minimap.getSideSize(), minimap.getMinCoordinates());
            }
            minimaps.put(floor.getNumber(), floorTiles);
            //accessibleSpaces.put(floor.getNumber(), createAccessibleArea(floor.getNavigableSpacesList()));
            floorsToIndices.put(floor.getNumber(), i++);
        }
    }

    public void setMap(Map map) {
        mMap = map;
    }

    public Map getMap() {
        return mMap;
    }

    /**
     * Checks if a square tile overlaps with any part of the accessible areas provided.
     *
     * @param navigableSpaces The navigable spaces to check if the tile overlaps with.
     * @return true if the tile overlaps with any accessible area.
     */
    /*
    private static Path createAccessibleArea(List<NavigableSpace> navigableSpaces) {
        Path navigableSpace = new Path();
        navigableSpace.setFillType(Path.FillType.EVEN_ODD);
        for (NavigableSpace space : navigableSpaces) {
            List<NavigableSpaceProto.Ring> rings = space.getRingsList();
            List<CoordinatesProto.Coordinates> outerBoundary = space.getOuterBoundaryList();
            boolean first = true;
            for (CoordinatesProto.Coordinates vertex : outerBoundary) {
                if (first) {
                    navigableSpace.moveTo((float) vertex.getX(), (float) vertex.getY());
                    first = false;
                } else {
                    navigableSpace.lineTo((float) vertex.getX(), (float) vertex.getY());
                }
            }
            if (!navigableSpace.isEmpty())
                navigableSpace.close();
            for (NavigableSpaceProto.Ring ring : rings) {
                first = true;
                for (CoordinatesProto.Coordinates vertex : ring.getPolygonList()) {
                    if (first) {
                        navigableSpace.moveTo((float) vertex.getX(), (float) vertex.getY());
                        first = false;
                    } else {
                        navigableSpace.lineTo((float) vertex.getX(), (float) vertex.getY());
                    }
                }
                navigableSpace.close();
            }
        }
        return navigableSpace;
    }*/

    /**
     * Finds and returns the {@link ParticleState} of the room with the specific name given as a parameter.
     *
     * @param room The room name to use in order to find its location.
     * @return The {@link ParticleState} of the room with the given name, or null if there is no
     * {@link Landmark} with the specific name.
     */
    public ParticleState getRoomLocation(String room) {
        for (FloorProto.Floor floor : protoMap.getFloorsList()) {
            for (LandmarkProto.Landmark landmark : floor.getLandmarksList()) {
                if (landmark.getName().equals(room)) {
                    CoordinatesProto.Coordinates particle = landmark.getParticles(0);
                    return new ParticleState(0, particle.getX(), particle.getY(), floor.getNumber());
                }
            }
        }
        return null;
    }


    /**
     * Returns all the rooms in the map.
     *
     * @return All the rooms contained in the map.
     */
    public List<Landmark> destinations() {
        ArrayList<Landmark> rooms = new ArrayList<>();
        for (FloorProto.Floor floor : protoMap.getFloorsList()) {
            for (LandmarkProto.Landmark landmark : floor.getLandmarksList()) {
                if (landmark.getType() == LandmarkProto.Landmark.LandmarkType.DOOR)
                    rooms.add(new Landmark(landmark));
            }
        }
        return rooms;
    }

    public Landmark getLandmark(String landmarkId) {
        for (Landmark landmark : destinations()) {
            if (landmark.getName().equals(landmarkId)) {
                return landmark;
            }
        }
        return null;
    }

    /**
     * Reads a protobuf map file and returns a BuildingMapWrapper that contains the protobuf.
     *
     * @param filename The filename of the protobuf map.
     * @return A new BuildingMapWrapper instance containg the protobuf map.
     * @throws IOException If the method cannot read the file.
     */
    public static Building readFrom(String filename) throws IOException {
        return new Building(BuildingMapProto.Building.parseFrom(new FileInputStream(filename)));
    }

    /**
     * Returns true if a point belongs to the accessible space in the map.
     *
     * @param x     The x coordinate of the point.
     * @param y     The y coordinate of the point.
     * @param floor The floor to check.
     * @return True it the point is accessible.
     */
    public boolean isAccessible(double x, double y, int floor) {
        return getTile(x, y, floor) != null;
    }

    /**
     * Same as {@link #isAccessible(double, double, int)}.
     *
     * @param state The state to check if it is accessible.
     * @return True if the state is accessible.
     */
    public boolean isAccessible(ParticleState state) {
        return isAccessible(state.getX(), state.getY(), state.getFloor());
    }

    /**
     * Returns the closest landmark to the given location (x,y) which is of a specific landmark type.
     *
     * @param x    The x coordinate of the point to search for the closest landmark.
     * @param y    The y coordinate of the point to search for the closest landmark.
     * @param type The type of the landmark to search for.
     * @return The closest landmark of a specific type.
     */
    public LandmarkProto.Landmark getClosestLandmark(ParticleState state, LandmarkProto.Landmark.LandmarkType type) {
        List<Landmark> landmarkGroup = getTile(state).getLandmarkGroup(type);
        if (landmarkGroup.isEmpty())
            return null;
        return landmarkGroup.get(0).getLandmark();
    }

    /**
     * Returns a landmark of specific type which is close to the given state. Keep in mind that the
     * landmark does not have to be the closest one.
     *
     * @param state The position to search around for a close landmark.
     * @param type  The type of landmark to search for.
     * @return A landmark close to the given position which is of the given type.
     */
    public LandmarkProto.Landmark getCloseLandmark(ParticleState state, LandmarkProto.Landmark.LandmarkType type) {
        List<Landmark> landmarkGroup = getTile(state).getLandmarkGroup(type);
        if (landmarkGroup.isEmpty())
            return null;
        Double choice = Math.random(), weightSum = 0.0;
        for (Landmark landmark : landmarkGroup) {
            weightSum += landmark.getWeight();
            if (weightSum > choice)
                return landmark.getLandmark();
        }
        return landmarkGroup.get(0).getLandmark();
    }

    /**
     * Returns all the landmarks close to a specific location.
     *
     * @param state The location to search for landmarks.
     */
    public java.util.Map<LandmarkProto.Landmark.LandmarkType, List<Landmark>> getLandmarks(ParticleState state) {
        Tile tile = getTile(state);
        if (tile != null)
            return tile.getLandmarks();
        return new HashMap<>();
    }

    Tile getTile(ParticleState state) {
        return getTile(state.getX(), state.getY(), state.getFloor());
    }

    /**
     * Returns the tile that contains the given point.
     */
    Tile getTile(double x, double y, int floor) {
        Tile[][] floorTiles = minimaps.get(floor);
        if (floorTiles == null)
            return null;
        double tileSize = protoMap.getFloors(floorsToIndices.get(floor)).getMinimap().getSideSize();
        CoordinatesProto.Coordinates origin =
                protoMap.getFloors(floorsToIndices.get(floor)).getMinimap().getMinCoordinates();
        int row = (int) ((y - origin.getY()) / tileSize);
        int column = (int) ((x - origin.getX()) / tileSize);
        if (row < 0 || row >= floorTiles.length || column < 0 || column >= floorTiles[0].length)
            return null;
        return floorTiles[row][column];
    }

    public BuildingMapProto.Building getProtobufMap() {
        return protoMap;
    }

    // TODO(ilapost): This function only checks the starting and ending points. It should also check
    // in between in case the path goes through an inaccessible area.

    /**
     * Checks if a given path goes through inaccessible areas.
     *
     * @param start The start of the path.
     * @param end   The end of the path.
     * @return True if the path goes through inaccessible areas.
     */
    public boolean pathBlocked(ParticleState start, ParticleState end) {
        return !(isAccessible(start) && isAccessible(end));
    }


    public String getName() {
        return protoMap.getName();
    }

    @Override
    public String toString() { return getName(); }

}
