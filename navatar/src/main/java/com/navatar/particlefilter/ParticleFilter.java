package com.navatar.particlefilter;

import com.navatar.maps.Building;
import com.navatar.maps.Map;
import com.navatar.maps.particles.Cluster;
import com.navatar.maps.particles.KMeans;
import com.navatar.maps.particles.Particle;
import com.navatar.maps.particles.ParticleState;
import com.navatar.math.Angles;
import com.navatar.math.Constants;
import com.navatar.math.Distance;
import com.referencepoint.proto.LandmarkProto.Landmark;
import com.referencepoint.proto.LandmarkProto.Landmark.LandmarkType;

import java.util.Collections;
import java.util.LinkedList;
import java.util.Random;
import java.util.Vector;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.subjects.PublishSubject;

public class ParticleFilter {
    private static final int NUM_OF_PARTICLE_FILTERS = 10;
    private static final int NUM_OF_PARTICLES = 1000;
    private static final int NUM_OF_PARTICLES_PER_PF = NUM_OF_PARTICLES / NUM_OF_PARTICLE_FILTERS;
    private static final int NUM_OF_CLUSTERS = 3;

    private static final double KMEANS_CONVERGENCE = 0.25;
    private static final double KMEANS_MAX_DIAMETER = 10.0;
    private static final double LANDMARK_RADIUS = 5.0;

    private float staticStepV = 0.1f;
    private float stepMean = 1.0f;
    private float stepMeanDiff = 0.0933f;

    private int run;

    private float[] stepM = new float[NUM_OF_PARTICLE_FILTERS];
    private Particle[][] particles = new Particle[NUM_OF_PARTICLE_FILTERS][NUM_OF_PARTICLES_PER_PF];
    private double[][] pfEllipses = new double[NUM_OF_PARTICLE_FILTERS][5];

    private LinkedList<Transition> transitions = new LinkedList<>();

    private static Random sRandom = new Random();
    private ParticleState locationEstimation;

    private Building map;

    private PublishSubject<FilterStep> mFilterSteps = PublishSubject.create();

    private PublishSubject<ParticleState> mCurrentState = PublishSubject.create();

    public ParticleFilter(Map map, ParticleState startingState) {

    }

    public ParticleFilter(Building map, ParticleState startingState) {
        this.map = map;
        locationEstimation = startingState;

        float currMean = stepMean;
        for (int i = 0; i < NUM_OF_PARTICLE_FILTERS; ++i, currMean += 3 * stepMeanDiff)
            stepM[i] = currMean;

        double newX, newY;
        for (int j = 0; j < NUM_OF_PARTICLE_FILTERS; ++j) {
            for (int i = 0; i < NUM_OF_PARTICLES_PER_PF; ++i) {
                do {
                    newX = startingState.getX() + 2 * sRandom.nextGaussian();
                    newY = startingState.getY() + 2 * sRandom.nextGaussian();
                } while (!map.isAccessible(newX, newY, startingState.getFloor()));

                particles[j][i] = Particle.newInstance(
                        new ParticleState(startingState.getDirection(), newX, newY, startingState.getFloor()),
                        2);
            }
        }

    }

    private Transition integrateTransitions(Vector<Transition> currTransitions) {

        int newDisp = 0;
        double angles[] = new double[currTransitions.size()];
        double weights[] = new double[currTransitions.size()];
        Transition newTransition = new Transition(), currTransition;

        for (int i = 0; i < currTransitions.size(); ++i) {
            currTransition = currTransitions.get(i);
            angles[i] = currTransition.getDirection();
            weights[i] = i + 1;
            newDisp += currTransition.getStep();

            if (currTransition.getLandmarkType() != null) {
                newTransition.setLandmarkType(currTransition.getLandmarkType());
                newTransition.setLeft(currTransition.isLeft());
            }
        }

        newTransition.setStep(newDisp);
        newTransition.setDirection(Angles.discretizeAngle(Angles.weightedAverage(angles, weights)));

        return newTransition;
    }

    public void execute() {
        Vector<Transition> currTransitions = new Vector<>();
        Transition currTransition, integratedTransition;
        ParticleState newState;
        long currTime;
        LandmarkType currentLandmark;

        synchronized (particles) {

            while (!transitions.isEmpty()) {
                currTransition = transitions.remove();
                currTransitions.add(currTransition);
                currTime = currTransition.getTime();
                currentLandmark = currTransition.getLandmarkType();

                // Gather all transitions within a second from the first one
                while (!transitions.isEmpty() && transitions.element().getTime() - currTime < 1000000000L) {
                    currTransition = transitions.remove();
                    currTransitions.add(currTransition);
                    if (currTransition.getLandmarkType() != null)
                        currentLandmark = currTransition.getLandmarkType();
                }

                integratedTransition = integrateTransitions(currTransitions);

                for (int i = 0; i < NUM_OF_PARTICLE_FILTERS; ++i) {
                    for (Particle particle : particles[i]) {
                        newState = transitionModel(particle, integratedTransition, stepM[i]);
                        if (map.pathBlocked(particle.getLastState(), newState))
                            particle.setWeight(0.0);
                        else
                            particle.setWeight(observationModel(particle, newState,
                                    integratedTransition.getLandmarkType(), integratedTransition.isLeft()));
                        particle.addState(newState);
                    }
                }
                currTransitions.clear();
                sampling(currentLandmark);

                locationEstimation = getLocationEstimate();

                FilterStep filterStep = new FilterStep(currTime, locationEstimation.getX(), locationEstimation.getY());

                double[][] pfs = getPFLocations();

                for (int i = 0; i < NUM_OF_PARTICLE_FILTERS; ++i) {
                    filterStep.addFilter(stepM[i], pfs[i]);
                }

                mFilterSteps.onNext(filterStep);
            }
        }
    }

    private double[][] getPFLocations() {
        int i, j;
        ParticleState currState;
        double varX, varY;
        double[][] values = new double[NUM_OF_PARTICLE_FILTERS][5];

        for (i = 0; i < NUM_OF_PARTICLE_FILTERS; ++i) {

            // Calculate mean
            for (j = 0; j < NUM_OF_PARTICLES_PER_PF; ++j) {
                currState = particles[i][j].getLastState();
                values[i][0] += currState.getX();
                values[i][1] += currState.getY();
            }

            values[i][0] /= NUM_OF_PARTICLES_PER_PF;
            values[i][1] /= NUM_OF_PARTICLES_PER_PF;

            // Calculate covariance matrix
            for (j = 0; j < NUM_OF_PARTICLES_PER_PF; ++j) {
                currState = particles[i][j].getLastState();
                varX = currState.getX() - values[i][0];
                varY = currState.getY() - values[i][1];

                values[i][2] += varX * varX;
                values[i][3] += varX * varY;
                values[i][4] += varY * varY;
            }

            values[i][2] /= NUM_OF_PARTICLES_PER_PF;
            values[i][3] /= NUM_OF_PARTICLES_PER_PF;
            values[i][4] /= NUM_OF_PARTICLES_PER_PF;
        }

        return values;
    }

    private ParticleState transitionModel(Particle particle, Transition integratedTransition,
                                          float stepM) {
        int steps = integratedTransition.getStep();
        ParticleState prevState = particle.getLastState();
        double choice = sRandom.nextDouble(), newDisp = 0, toRadians;
        int newDir;
        if (steps > 0) {
            for (int i = 0; i < steps; ++i) {
                if (choice > 0.01)
                    newDisp += sRandom.nextGaussian() * stepMeanDiff + integratedTransition.getStep() * stepM;
                else
                    newDisp += sRandom.nextGaussian() * staticStepV;
            }
        } else {
            if (choice > 0.2)
                newDisp = (float) sRandom.nextGaussian() * staticStepV;
            else
                newDisp =
                        (float) sRandom.nextGaussian() * stepMeanDiff + integratedTransition.getStep() * stepM;
        }

        if (newDisp < 0)
            newDisp = 0;
        // newDir = sRandom.nextGaussian()*dirV+integratedTransition.getDirection();
        newDir = pickBiasedOrientation(integratedTransition.getDirection());
        toRadians = Math.toRadians(newDir);
        return new ParticleState(newDir, prevState.getX() + newDisp * Math.cos(toRadians),
                prevState.getY() + newDisp * Math.sin(toRadians), prevState.getFloor());
    }

    private static int pickBiasedOrientation(int direction) {

        int i;
        double choice = sRandom.nextDouble(), distr = 0;
        final double[] dirProb = {0.0942998415, 0.811400318, 0.0942998414};

        for (i = 0; i < dirProb.length && choice > (distr += dirProb[i]); ++i)
            ;
        return direction + (i - 1) * 45;
    }

    private double observationModel(Particle particle, ParticleState newState, LandmarkType type,
                                    boolean isLeft) {
        double landmarkAngle, newDirection;
        boolean left;
        if (particle.getWeight() < Constants.DOUBLE_ACCURACY || !map.isAccessible(newState))
            return 0.0;
        if (type == null)
            return 1.0 * particle.getWeight();
        Landmark closestLandmark = map.getClosestLandmark(newState, type);

        if (Distance.euclidean(closestLandmark.getLocation().getX(),
                closestLandmark.getLocation().getY(), newState.getX(),
                newState.getY()) <= LANDMARK_RADIUS) {
            if (type == LandmarkType.HALLWAY_INTERSECTION || type == LandmarkType.STAIRS)
                return 1.0 * particle.getWeight();

            landmarkAngle =
                    Math.toDegrees(Math.atan2(closestLandmark.getLocation().getY() - newState.getY(),
                            closestLandmark.getLocation().getX() - newState.getX()));

            if (landmarkAngle < 0)
                landmarkAngle += 360.0;

            newDirection = newState.getDirection();

            if ((newDirection + 180 < 360) && (landmarkAngle > newDirection)
                    && (landmarkAngle < newDirection + 180))
                left = false;
            else left = !((newDirection + 180 >= 360)
                    && ((landmarkAngle > newDirection) && (landmarkAngle < 360))
                    || ((landmarkAngle >= 0) && (landmarkAngle < newDirection - 180)));

            if (left == isLeft)
                return 1.0 * particle.getWeight();
        }

        return 0.0;
    }

    private void sampling(LandmarkType type) {
        ParticleState currState, newState, oldState;
        com.referencepoint.proto.LandmarkProto.Landmark closestLandmark;
        double selection;
        double pdf[] = new double[NUM_OF_PARTICLES_PER_PF];
        double weights[] = new double[NUM_OF_PARTICLE_FILTERS];
        double bestPF = 0.0;
        int i, j, l, bestPFindex = -1;

        // Calculate sums of all particle filters
        for (l = 0; l < NUM_OF_PARTICLE_FILTERS; ++l) {
            for (i = 0; i < NUM_OF_PARTICLES_PER_PF; ++i)
                weights[l] += particles[l][i].getWeight();
            // TODO(ilapost): Make sure it works if we have distance based weights.
            if (weights[l] > bestPF && weights[l] > 0.0001) {
                bestPF = weights[l];
                bestPFindex = l;
            }
        }
        // Sample for each particle filter
        for (l = 0; l < NUM_OF_PARTICLE_FILTERS; ++l) {
            // If all particle filters are dead reset them
            if (bestPFindex == -1) {
                //logs.append("\nAll particles died!!!\nType of landmark: " + type);
                for (i = 0; i < NUM_OF_PARTICLES_PER_PF; ++i) {
                    currState = particles[l][i].getLastState();
                    if (!map.isAccessible(currState))
                        currState = getRandomAccessibleState(particles[l]);
                    // closestLandmark = map.closestVisibleLandmark(currState.getY(),
                    // currState.getX(), landmark);
                    if (type != null) {
                        closestLandmark = map.getCloseLandmark(currState, type);
                        Particle newParticle = Particle
                                .newInstance(new ParticleState(particles[l][i].getLastState().getDirection(),
                                        closestLandmark.getLocation().getX(), closestLandmark.getLocation().getY(),
                                        particles[l][i].getLastState().getFloor()), 2);
                        // currState.setX(newState.getX());
                        // currState.setY(newState.getY());

                        oldState = particles[l][i].getLastState();
                        newState = newParticle.getLastState();
                        oldState.setDirection(newState.getDirection());
                        oldState.setX(newState.getX());
                        oldState.setY(newState.getY());
                        particles[l][i].setWeight(newParticle.getWeight());
                    } else {
                        oldState = particles[l][i].getLastState();
                        oldState.setDirection(currState.getDirection());
                        oldState.setX(currState.getX());
                        oldState.setY(currState.getY());
                        particles[l][i].setWeight(1.0);
                    }
                }
            }
            // If the current particle filter is dead resample with the best one
            else if (weights[l] < 0.0001) {
                //logs.append("\nParticle filter with step " + stepM[l] + " died!!!\n");
                for (i = 0; i < NUM_OF_PARTICLES_PER_PF; ++i)
                    particles[bestPFindex][i].copyTo(particles[l][i]);
                stepM[l] = stepM[bestPFindex] + (float) sRandom.nextGaussian() * stepMeanDiff;
            }
            // Calculate new pdf
            pdf[0] = particles[l][0].getWeight();
            for (i = 1; i < NUM_OF_PARTICLES_PER_PF; ++i)
                pdf[i] = particles[l][i].getWeight() + pdf[i - 1];
            for (i = 0, j = 0; i < NUM_OF_PARTICLES_PER_PF; ++i) {
                if (particles[l][i].getWeight() < 0.0001) {
                    selection = sRandom.nextDouble() * pdf[NUM_OF_PARTICLES_PER_PF - 1];
                    for (j = 0; j < NUM_OF_PARTICLES_PER_PF && pdf[j] < selection; ++j)
                        ;
                    particles[l][j].copyTo(particles[l][i]);
                }
            }
        }
        if (bestPFindex == -1) {
            float currMean = stepMean;
            for (i = 0; i < NUM_OF_PARTICLE_FILTERS; ++i, currMean += 3 * stepMeanDiff)
                stepM[i] = currMean;
        }
    }

    private ParticleState getRandomAccessibleState(Particle[] particles) {
        ParticleState state;

        do {
            state = particles[sRandom.nextInt(NUM_OF_PARTICLES_PER_PF)].getNewState();
        } while (!map.isAccessible(state));

        return state;
    }

    public void addTransition(Transition transition) {
        transitions.add(transition);
    }

    public void finalize() {

    }


    public ParticleState getSynchronizedLocationEstimate() {
        synchronized (particles) {
            locationEstimation = getLocationEstimate();
        }
        return locationEstimation;
    }

    private int calculateAngle(Vector<ParticleState> states) {
        // TODO(ilapost): Change Angles.average parameter so it can accept any Java collections class.
        int statesSize = states.size();
        double[] angles = new double[statesSize];
        for (int i = 0; i < statesSize; ++i) {
            angles[i] = states.get(i).getDirection();
        }
        return Angles.discretizeAngle(Angles.average(angles));
    }

    public void changeFloor(int floor, ParticleState transitionState) {
        synchronized (particles) {
            double newX, newY;
            for (int j = 0; j < NUM_OF_PARTICLE_FILTERS; ++j) {
                for (int i = 0; i < NUM_OF_PARTICLES_PER_PF; ++i) {
                    do {
                        newX = transitionState.getX() + 2 * sRandom.nextGaussian();
                        newY = transitionState.getY() + 2 * sRandom.nextGaussian();
                    } while (!map.isAccessible((int) (newX + 0.5), (int) (newY + 0.5), floor));
                    particles[j][i]
                        .addState(new ParticleState(transitionState.getDirection(), newX, newY, floor));
                }
            }
        }
    }

    private ParticleState getLocationEstimate() {
        KMeans<ParticleState> kmeans =
                new KMeans<>(NUM_OF_CLUSTERS, KMEANS_CONVERGENCE, KMEANS_MAX_DIAMETER);
        Vector<ParticleState> states = new Vector<>();
        for (Particle[] particleFilter : particles) {
            for (Particle particle : particleFilter)
                states.add(particle.getLastState());
        }
        Vector<Cluster<ParticleState>> clusters = kmeans.calculateClusters(states);
        Cluster<ParticleState> largestCluster = Collections.max(clusters);
        ParticleState estimation = largestCluster.getMean();
        estimation.setDirection(calculateAngle(largestCluster.states()));

        mCurrentState.onNext(estimation);

        return estimation;
    }

    public ParticleState getLocation() {
        return getSynchronizedLocationEstimate();
    }

    public class ExecutePF extends Thread {

        public void run() {
            execute();
        }

        public void interrupt() { }
    }

    public Flowable<ParticleState> onStateChange() {
        return null;
    }

    public Flowable<FilterStep> filterSteps() {
        return mFilterSteps.toFlowable(BackpressureStrategy.LATEST);
    }
}