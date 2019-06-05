/**
 * Contains the A* algorithm for finding the shortest path in a 2D environment.
 **/
package com.navatar.pathplanning;

import com.navatar.maps.Building;
import com.navatar.maps.Landmark;
import com.navatar.maps.Route;
import com.navatar.maps.particles.ParticleState;
import com.navatar.math.Distance;
import com.referencepoint.proto.LandmarkProto.Landmark.LandmarkType;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Stack;

import io.reactivex.Single;

/**
 * Implements the A* algorithm for finding the optimum path in a 2D space.
 **/
public class AStar implements PathFinder {

    public AStar() { }
    /**
     * A single {@link Node} in the search graph.
     */
    public class Node implements Comparable<Node> {
        /**
         * The {@link Landmark} of the node
         */
        private Landmark landmark;
        /**
         * The {@link ParticleState} of the node
         */
        private ParticleState state;
        /**
         * The path cost for this node
         */
        private double g;
        /**
         * The heuristic cost of this node
         */
        private double h;
        /**
         * The node from which the algorithm reached this one
         */
        private Node cameFrom;

        public Node(ParticleState state, Landmark landmark) {
            this.state = state;
            this.landmark = landmark;
        }
        /**
         * Set the parent of this node
         *
         * @param cameFrom The parent node which lead us to this node.
         */
        public void setCameFrom(Node cameFrom) {
            this.cameFrom = cameFrom;
        }

        public Node getCameFrom() {
            return cameFrom;
        }
        /**
         * @see Comparable#compareTo(Object)
         */
        public int compareTo(Node other) {
            double f = h + g;
            double of = other.h + other.g;
            if (Double.compare(f, of) < 0)
                return -1;
            else if (f > of)
                return 1;
            else
                return 0;
        }
    }

    @Override
    public Single<Path> findPath(Route route) {
        return Single.fromCallable(() -> findPath(route.getFromLandmark(), route.getToLandmark()));
    }

    public Path findPath(Landmark start, Landmark goal) {
        Map<Landmark, Node> closed = new HashMap<>();
        Map<Landmark, Node> openMap = new HashMap<>();
        PriorityQueue<Node> openQueue = new PriorityQueue<>();

        Node startNode = new Node(start.getParticleState(), start);
        startNode.g = 0;
        startNode.cameFrom = null;
        openMap.put(startNode.landmark, startNode);
        openQueue.add(startNode);

        Node current;
        while (!openMap.isEmpty()) {
            current = openQueue.poll();
            if (current.landmark.equals(goal))
                return reconstructPath(current);
            openMap.remove(current.landmark);
            closed.put(current.landmark, current);
            for (Node neighbor : neighbors(current)) {
                if (closed.get(neighbor.landmark) == null) {
                    double nextStepCost = current.g + Distance.euclidean(current.state.getX(),
                            current.state.getY(), neighbor.state.getX(), neighbor.state.getY());
                    if (openMap.get(neighbor.landmark) == null || nextStepCost < neighbor.g) {
                        neighbor.setCameFrom(current);
                        neighbor.g = nextStepCost;
                        neighbor.h = neighbor.g + heuristicCost(neighbor.state, goal.getParticleState());
                        if (openMap.get(neighbor.landmark) == null) {
                            openMap.put(neighbor.landmark, neighbor);
                            openQueue.add(neighbor);
                        }
                    }
                }
            }
        }
        return null;
    }

    public Path findPath(ParticleState startState, Landmark start, ParticleState endState,
                         Landmark goal) {
        Map<Landmark, Node> closed = new HashMap<>();
        Map<Landmark, Node> openMap = new HashMap<>();
        PriorityQueue<Node> openQueue = new PriorityQueue<>();
        Node startNode = new Node(startState, start);
        startNode.g = 0;
        startNode.cameFrom = null;
        openMap.put(startNode.landmark, startNode);
        openQueue.add(startNode);

        Node current;
        while (!openMap.isEmpty()) {
            current = openQueue.poll();
            if (current.landmark.getName().equals(goal.getName()))
                return reconstructPath(current);
            openMap.remove(current.landmark);
            closed.put(current.landmark, current);
            for (Node neighbor : neighbors(current)) {
                if (closed.get(neighbor.landmark) == null) {
                    double nextStepCost = current.g + Distance.euclidean(current.state.getX(),
                            current.state.getY(), neighbor.state.getX(), neighbor.state.getY());
                    if (openMap.get(neighbor.landmark) == null || nextStepCost < neighbor.g) {
                        neighbor.setCameFrom(current);
                        neighbor.g = nextStepCost;
                        neighbor.h = neighbor.g + heuristicCost(neighbor.state, endState);
                        if (openMap.get(neighbor.landmark) == null) {
                            openMap.put(neighbor.landmark, neighbor);
                            openQueue.add(neighbor);
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Get the heuristic cost for the given location. This determines in which order the locations are
     * processed.
     *
     * @param currentState The current {@link ParticleState} to use for calculating the heuristic.
     * @param endState     The goal {@link ParticleState} we want to reach.
     * @return The heuristic cost assigned to the tile
     */
    private double heuristicCost(ParticleState currentState, ParticleState endState) {
        return Distance.euclidean(currentState.getX(), currentState.getY(), endState.getX(),
                endState.getY());
    }

    /**
     * Returns all the neighbors from the given node.
     *
     * @param node a {@link Landmark}
     * @return a {@link LinkedList} of {@link Node}s the neighbors to the node
     */
    private LinkedList<Node> neighbors(Node node) {
        LinkedList<Node> neighbors = new LinkedList<>();

        Building bldg = node.landmark.getBuilding();

        Map<LandmarkType, List<Landmark>> landmarks = bldg.getLandmarks(node.state);

        for (List<Landmark> landmarkGroup : landmarks.values()) {
            for (Landmark landmark : landmarkGroup) {
                Node neighbor = new Node(
                        new ParticleState(0, landmark.getX(),
                                landmark.getY(), node.state.getFloor()),
                        landmark);
                neighbors.add(neighbor);
            }
        }
        return neighbors;
    }

    /**
     * Reconstructs the path from the goal node. The algorithm creates first an inverted path by
     * backtracking from the goal until it reaches the starting node. It then inverts this path to
     * create the normal path which will be used for navigation.
     *
     * @param goal The goal {@link Node} to use in order to recalculate the path.
     * @return The reconstructed {@link Path} found by A*.
     */
    private Path reconstructPath(Node goal) {
        Stack<Node> reversePath = new Stack<>();
        Node current = goal;
        while (current != null) {
            reversePath.add(current);
            current = current.cameFrom;
        }
        Path path = new Path();
        while (!reversePath.empty()) {
            current = reversePath.pop();
            path.add(new Step(current.landmark, current.state));
        }
        return path;
    }
}
