package com.navatar.pathplanning;

import com.navatar.math.Distance;
import com.referencepoint.proto.BuildingMapProto.Building;
import com.referencepoint.proto.CoordinatesProto.Coordinates;
import com.referencepoint.proto.FloorProto.Floor;
import com.referencepoint.proto.LandmarkProto.Landmark;
import com.referencepoint.proto.MinimapProto.Minimap;

import java.util.List;
import java.util.Vector;

public class Direction {

    private Building map;
    private static final double MAX_LANDMARK_DISTANCE = 20.0;

    public Direction(Building in_map) {
        this.map = in_map;
    }

    public Path generateDirections(Path path) {

        Floor floor = this.map.getFloors(path.getFloor());
        Minimap miniMap = floor.getMinimap();
        List<Landmark> landmarks = floor.getLandmarksList();
        double a = miniMap.getSideSize();
        Step currentStep;
        if (path.getLength() == 0)
            return null;
        Step nextStep;
        Landmark closestLandmark;
        String side = "";
        int stepIndex = -1;
        Vector<Landmark> landmarkList = new Vector<>();
        double cx, cy, ncx, ncy;
        while ((currentStep = path.getStep(++stepIndex)) != null) {

            side = sideToFollow(path, stepIndex); // the side to follow based on the next step
            if (side.equalsIgnoreCase("left "))
                currentStep.setFollowLeft(true);
            else
                currentStep.setFollowLeft(false);

            if (stepIndex >= path.getLength() - 1) {
                currentStep.setDirectionString("You have reached your destination.");
                break;
            }
            closestLandmark = currentStep.getlandmark().getLandmark();
            nextStep = path.getStep(stepIndex + 1);

            /* Generate a list of landmarks between the current and the next step */

            cx = currentStep.getParticleState().getX();
            cy = currentStep.getParticleState().getY();
            ncx = nextStep.getParticleState().getX();
            ncy = nextStep.getParticleState().getY();

            vector vec_a = new vector(cx, cy, ncx, ncy);

            /*
             * Get the angle vec_a makes with the x axis, in order to determine what direction to go. The
             * angle will tell the heading.
             */
            double dir_a = Math.atan2(vec_a.getY(), vec_a.getX()) * 180 / Math.PI;

            double x_offset = 0f;
            double y_offset = 0f;

            /*
             * when dir_a is between 0-45 degree with the x_axis, to get to the next tile increase x
             * coordinate. Similarly if dir_a is between 45-135 degree heading is straight, so to go to
             * the next tile increase y coordinates of the current step. If dir_a >135 degree heading is
             * left so, decrease x coordinate to get to the next tile.
             */

            if (stepIndex == path.getLength() - 2) // When approaching destination
                if (dir_a <= 90 && dir_a >= 0) // we need to check the side
                    side = "right"; // of the destination landmark
                else // relative to current position.
                    side = "left";

            if (dir_a > 0 && dir_a <= 45)
                x_offset = a;
            else if (dir_a > 45 && dir_a < 135) {
                x_offset = 0f;
                y_offset = a;
            } else
                x_offset = -a;
            double dist = Distance.euclidean(cx, cy, ncx, ncy);
            double counter = 0;
            int landmarkIndex;
            /*
             * Starting at the current coordinates, until we have walked as much distance as dist or until
             * our closest landmark is the next step itself, find all the intermediate landmarks of the
             * same type as next step if they are on the same side as you go from next step.
             */
            double angle;
            while (counter < dist && !compareLandMark(closestLandmark, nextStep.getlandmark().getLandmark())) {
                cx += x_offset;
                cy += y_offset;
                landmarkIndex = findClosestLandmarks(landmarks, nextStep.getlandmark().getType(), cx, cy);
                if (landmarkIndex < 0)
                    break;
                closestLandmark = landmarks.get(landmarkIndex);
                vector vec_b = new vector(ncx, ncy, closestLandmark.getLocation().getX(),
                        closestLandmark.getLocation().getX());
                // find if the closest Land is desired side.
                angle = getAngle(vec_a, vec_b);
                if (angle >= -90 && angle <= 90) // consider only the landmarks that are ahead
                    if (side == sideToFollow(angle)) // consider only the landmarks that are on the correct
                        // side
                        if (!landmarkList.contains(closestLandmark)) // if the landmark hasn't already been
                            // considered
                            landmarkList.add(closestLandmark);

                counter += a;
            }
            StringBuilder directionStringBuilder = new StringBuilder();
            directionStringBuilder.append("Follow the wall ");
            directionStringBuilder.append("on your " + side);
            directionStringBuilder.append(" until you find the " + getOrdinalnumber(landmarkList.size()));
            directionStringBuilder.append(getLandMarkName(nextStep.getlandmark().getType()));
            currentStep.setDirectionString(directionStringBuilder.toString());
        }

        return path;
    }

    private String getLandMarkName(Landmark.LandmarkType landmarktype) {

        switch (landmarktype) {
            case DOOR:
                return " Door ";
            case HALLWAY_INTERSECTION:
                return " Hallway intersection ";
            case STAIRS:
                return " Stairs ";
            case ELEVATOR:
                return " Elevator ";
            default:
                return "";
        }

    }

    private String sideToFollow(double angle) {
        String side = "right";
        if (angle >= 45f)
            side = "right";
        else if (angle <= -45f)
            side = "left";
        else {

        }
//      side = "straight";
        return side;
    }

    private String sideToFollow(Path path, int pathIndex) {

        String side = "";

        if (pathIndex < path.getLength() - 2) {
            double x_pointAt = path.getStep(pathIndex).getParticleState().getX();
            double y_pointAt = path.getStep(pathIndex).getParticleState().getY();

            double x_nextpoint = path.getStep(pathIndex + 1).getParticleState().getX();
            double y_nextpoint = path.getStep(pathIndex + 1).getParticleState().getY();

            vector a = new vector(x_pointAt, y_pointAt, x_nextpoint, y_nextpoint);
            double x_nextnextpoint = path.getStep(pathIndex + 2).getParticleState().getX();
            double y_nextnextpoint = path.getStep(pathIndex + 2).getParticleState().getY();

            vector b = new vector(x_nextpoint, y_nextpoint, x_nextnextpoint, y_nextnextpoint);
            double angle = getAngle(a, b);
            if (angle >= 45f)
                side = "right";
            else if (angle <= -45f)
                side = "left";
            else
                side = "right";
        } else
            side = "right";

        return side;
    }

    private boolean compareLandMark(Landmark l1, Landmark l2) {
        /* compare two landmarks to see if they are the same */

        Coordinates c1 = l1.getLocation();
        Coordinates c2 = l2.getLocation();
        if (c1.getX() == c2.getX())
            if (c1.getY() == c2.getY())
                return true;
        return false;
    }

    private int findClosestLandmarks(List<Landmark> landmarks, Landmark.LandmarkType type, double x,
                                     double y) {
        int index = 0, k = 0;
        double distanceMax = Double.MAX_VALUE;
        for (Landmark landmark : landmarks) {
            if (landmark.getType() == type) {
                double distance =
                        Distance.euclidean(x, y, landmark.getLocation().getX(), landmark.getLocation().getY());
                if (distance <= MAX_LANDMARK_DISTANCE) {
                    if (distance < distanceMax)
                        distanceMax = distance;
                    k = index;
                }
            }
            index++;
        }
        return k;
    }

    private static class vector {
        private double x;
        private double y;

        public vector(double ax, double ay, double bx, double by) {
            this.x = bx - ax;
            this.y = by - ay;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }
    }

    private double getAngle(vector a, vector b) {
        double dot = a.getX() * b.getX() + b.getY() * a.getY();
        double cross = a.getX() * b.getY() - b.getX() * a.getY();
        return -Math.atan2((cross), dot) * 180 / Math.PI;
    }

    private String getOrdinalnumber(int num) {

        switch (num) {
            case 1:
                return "next";
            case 0:
                return "next";
            case 2:
                return "2nd";
            case 3:
                return "3rd";
            default:
                return num + "th";
        }
    }

}
