package com.navatar.maps;

import android.support.annotation.NonNull;

import com.navatar.BaseNavigator;
import com.navatar.BasePresenter;
import com.navatar.BaseView;
import com.navatar.data.Building;
import com.navatar.data.Landmark;
import com.navatar.data.Map;

import java.util.List;

public interface MapsContract {

    interface View extends BaseView<Presenter> {

        void addMaps(List<Map> maps);

        void showMap(Map map);

        void showFromLandmark(List<Landmark> landmark);

        void showToLandmark(List<Landmark> landmark);

    }

    interface Presenter extends BasePresenter<View> {

        void loadData();

        void onMapSelected(Map map);

        void onBuildingSelected(Building building);

        void onLandmarkSelected(Landmark landmark);

        void cleanup();
    }

    interface Navigator extends BaseNavigator<Presenter> {

        void navigate(Map map);

    }

}