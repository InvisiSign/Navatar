package com.navatar.data.source.local;

import android.content.Context;
import android.content.res.AssetManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.common.base.Optional;
import com.navatar.data.source.MapsDataSource;
import com.navatar.location.model.Geofence;
import com.navatar.location.model.Point;
import com.navatar.location.model.Polygon;
import com.navatar.maps.Building;
import com.navatar.maps.Map;
import com.navatar.util.schedulers.BaseSchedulerProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Observable;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Chris Daley
 */
@Singleton
public class MapsLocalDataSource implements MapsDataSource {

    private String CAMPUS_GEOFENCES_JSON_FILENAME = "Campus_Geofences.json";

    private AssetManager assetManager;
    private BaseSchedulerProvider schedulerProvider;

    @Inject
    public MapsLocalDataSource(@NonNull Context context,
                               @NonNull BaseSchedulerProvider schedulerProvider) {
        checkNotNull(context, "context cannot be null");
        checkNotNull(schedulerProvider, "scheduleProvider cannot be null");
        this.assetManager = context.getAssets();
        this.schedulerProvider = schedulerProvider;
    }

    @Override
    public Flowable<List<Map>> getMaps() {
        // Get campus files
        try {
            String[] campusFiles = assetManager.list("maps");

            Observable<String> observable = Observable
                .fromIterable(Arrays.asList(campusFiles));

            return observable
                .observeOn(schedulerProvider.io())
                .filter(n -> !n.endsWith(".json"))
                .map(this::newMap)
                .toList()
                .toFlowable();

        } catch (IOException e) {
            return Flowable.empty();
        }
    }

    private Map newMap(String path) {

        Map map = new Map(path, path.replace('_', ' '));
        try {
            String[] buildingNames = assetManager.list("maps/" + path);

            for (String buildingName : buildingNames) {
                if (!buildingName.endsWith(".json")) {
                    Building bldg = new Building(assetManager.open("maps/" + path + "/" + buildingName));
                    map.addBuilding(bldg);
                }
            }
        } catch (IOException e) {

        }
        return map;
    }

    @Override
    public Flowable<Optional<Map>> getMap(@NonNull String mapId) {

        return Observable.just(Optional.of(newMap(mapId))).toFlowable(BackpressureStrategy.LATEST);
    }

    @Override
    public Flowable<List<Geofence>> getGeofences() {
        return Observable.fromIterable(loadCampusGeofencesJSONFromAsset())
                .observeOn(schedulerProvider.io())
                .toList()
                .toFlowable();
    }


    private List<Geofence> loadCampusGeofencesJSONFromAsset() {
        List<Geofence> gflist = new ArrayList<>();

        try {
            InputStream is = assetManager.open("maps/"+ CAMPUS_GEOFENCES_JSON_FILENAME);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String json = new String(buffer, "UTF-8");

            JSONObject obj = new JSONObject(json);
            JSONArray geofences = obj.getJSONArray("campuses");

            for (int i = 0; i < geofences.length(); i++) {
                JSONObject jo_inside = geofences.getJSONObject(i);
                JSONObject geofence = jo_inside.getJSONObject("geofence");

                ArrayList<Point> points = new ArrayList<>();
                Point minLatitude = Point.create(
                        geofence.getDouble("minLatitude"),
                        geofence.getDouble("minLongitude"), 0);
                points.add(minLatitude);
                Point maxLatitude = Point.create(
                        geofence.getDouble("maxLatitude"),
                        geofence.getDouble("maxLongitude"),0);
                points.add(maxLatitude);
                Polygon polygon = Polygon.create(points);
                gflist.add(new Geofence<Polygon>(polygon, jo_inside.getString("name")));
            }


        } catch (JSONException e) {
                //e.printStackTrace();

        } catch (IOException ex) {
            //ex.printStackTrace();
        }

        return gflist;

    }



    @Override
    public void saveMap(@NonNull Map map) {

    }

    @Override
    public void refreshMaps() {

    }


    @Override
    public void setSelectedMap(@NonNull Map map) {

    }

    @Nullable
    @Override
    public Map getSelectedMap() {
        return null;
    }
}
