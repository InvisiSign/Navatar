package com.navatar.navigation;

import android.os.Bundle;
import android.os.RemoteException;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import com.navatar.R;
import com.navatar.util.ActivityUtils;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.util.Collection;
import java.util.Locale;

import javax.inject.Inject;

import dagger.Lazy;
import dagger.android.AndroidInjection;
import dagger.android.support.DaggerAppCompatActivity;

public class NavigationActivity extends DaggerAppCompatActivity implements BeaconConsumer {

    private BeaconManager beaconManager;
    private TextToSpeech tts;
    private boolean previousBeacon = false;


    @Inject
    Lazy<NavigationFragment> navFragmentProvider;

    public void onCreate(Bundle savedInstanceState) {
        AndroidInjection.inject(this);
        super.onCreate(savedInstanceState);

        beaconManager = BeaconManager.getInstanceForApplication(this);
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215i:4-19,i:20-21,i:22-23,p:24-24"));
        beaconManager.bind(this);

        tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR){
                    tts.setLanguage(Locale.ENGLISH);
                }
            }
        });


        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.navigation_activity);

        NavigationFragment mNavigationFragment =
                (NavigationFragment) getSupportFragmentManager().findFragmentById(R.id.contentFrame);

        if(mNavigationFragment == null) {
            mNavigationFragment = navFragmentProvider.get();
            ActivityUtils.addFragmentToActivity(
                    getSupportFragmentManager(), mNavigationFragment, R.id.contentFrame);
        }
    }


    @Override
    public void onBeaconServiceConnect() {
        RangeNotifier rangeNotifier = new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                if(beacons.size() > 0){
                    Log.d("TheBleTest", "Detected at least one beacon: " + beacons.size());
                    Beacon closestBeacon = beacons.iterator().next();
                    if(closestBeacon.getDistance() < .85 && !previousBeacon){
                        tts.speak(closestBeacon.getId1().toString(), TextToSpeech.QUEUE_FLUSH, null);
                        previousBeacon = !previousBeacon;
                    }
                    if(closestBeacon.getDistance() > 2 && previousBeacon){
                        previousBeacon = !previousBeacon;
                    }
                }
            }
        };
        try{
            beaconManager.startRangingBeaconsInRegion(new Region("iBeacon", null, null, null));
            beaconManager.addRangeNotifier(rangeNotifier);
            beaconManager.startRangingBeaconsInRegion(new Region("iBeacon", null, null, null));
            beaconManager.addRangeNotifier(rangeNotifier);
        }catch(RemoteException e){        }
    }
}
