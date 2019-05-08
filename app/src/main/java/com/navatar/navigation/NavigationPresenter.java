package com.navatar.navigation;

import android.support.annotation.Nullable;
import android.util.Log;

import com.navatar.common.SensorData;
import com.navatar.common.SensorDataProvider;
import com.navatar.data.Landmark;
import com.navatar.data.Route;
import com.navatar.data.source.LandmarkProvider;
import com.navatar.data.source.RoutesRepository;
import com.navatar.di.ActivityScoped;
import com.navatar.location.GeofencingProvider;
import com.navatar.location.LocationInteractor;
import com.navatar.math.Angles;
import com.navatar.pathplanning.Path;

import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;

import javax.inject.Inject;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.subjects.PublishSubject;

@ActivityScoped
public final class NavigationPresenter implements NavigationContract.Presenter {

    private final String TAG = NavigationPresenter.class.getSimpleName();

    private final CompositeDisposable disposables = new CompositeDisposable();


    @Inject
    RoutesRepository mRoutesRepository;

    @Inject
    GeofencingProvider mGeofencingProvider;

    @Inject
    LocationInteractor mLocationInteractor;

    @Inject
    SensorDataProvider mSensorDataProvider;

    @Inject
    LandmarkProvider mLandmarkProvider;

    @Nullable
    private NavigationContract.View mNavView;

    @Nullable
    private Route mRoute;

    private int mPathIndex;

    private Path mPath;

    private final PublishSubject<SensorData> sensorDataPublishSubject = PublishSubject.create();

    @Inject
    public NavigationPresenter() { }

    @Override
    public void takeView(NavigationContract.View view) {
        disposables.clear();
        mNavView = view;
        loadData();
    }

    @Override
    public void dropView() {
        mNavView = null;
        disposables.clear();
    }

    @Override
    public void onStartNavigation() {

        mPathIndex = 0;
        mPath = mRoute.getPath();

        if(mPath == null) {
            mNavView.showNoRouteFound();
            return;
        }

        mNavView.showStepCount(mPathIndex, mPath.getLength());
    }

    @Override
    public void onReverseRoute() {
        Landmark lmTo = mRoute.getToLandmark();
        mRoute.setToLandmark(mRoute.getFromLandmark());
        mRoute.setFromLandmark(lmTo);
        mNavView.showDirection("");
        onStartNavigation();
    }

    @Override
    public void nextStep() {

        String navigationCommand = mPath.getNextDirection(mPathIndex);

        if(!navigationCommand.matches("(?i:Turn.*)")){
            mPathIndex++;
        }

        if (mPathIndex >= mPath.getLength()) {
            mNavView.showStepCount(mPath.getLength(), mPath.getLength());
            mNavView.showReachedDestination();
        } else {
            mNavView.showStepCount(mPathIndex, mPath.getLength());
        }

        mNavView.showDirection(navigationCommand);

    }

    @Override
    public void addLandmark() {

    }

    @Override
    public void loadData() {

        disposables.clear();

        mRoute = mRoutesRepository.getSelectedRoute();

        if (mRoute == null) {
            mNavView.showNoRouteFound();
            return;
        }

        getLocation();
        getSensorData();
        setupLandmarkProvider();
        onStartNavigation();
    }

    private void getLocation() {
        disposables.add(mLocationInteractor.getLocationUpdates()
            .subscribe(
                location -> {
                    Log.e(TAG, "Lat: " + location.latitude() + " Long: " + location.longitude());
                },
                throwable -> {
                    Log.e(TAG, "Error while getting location", throwable);
                }
            )
        );
    }

    private void getSensorData() {
        disposables.add(mSensorDataProvider.onSensorChanged()
            .subscribe(sensorDataPublishSubject::onNext)
        );

        disposables.add(sensorDataPublishSubject
            .filter(s -> s.getSensorType() == SensorData.SensorType.COMPASS)
            .subscribe(this::compassCorrection)
        );

        disposables.add(sensorDataPublishSubject
            .filter(s -> s.getSensorType() == SensorData.SensorType.PEDOMETER)
            .subscribe(this::stepSensorCorrection)
        );
    }

    private void compassCorrection(SensorData data) {
        if(mPath != null) {
            mPath.setOrientation(Angles.discretizeAngle(Angles.compassToScreen(Math.toDegrees(data.getValues()[0]))));
        }
    }

    private void stepSensorCorrection(SensorData data) {
        Log.i(TAG, data.toString());
    }

    private void setupLandmarkProvider() {
        disposables.add(mLandmarkProvider
            .getLandmarks()
            .subscribe(l -> {
                Log.i(TAG,l.toString());
                if(mRoute.getToLandmark().equals(l)) {
                    mPathIndex = mPath.getLength()-1;
                    nextStep();
                }
            })
        );
    }
}
