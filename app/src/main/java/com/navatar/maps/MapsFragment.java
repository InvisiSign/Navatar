package com.navatar.maps;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.ResultPoint;
import com.google.zxing.client.android.BeepManager;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;
import com.navatar.common.TextToSpeechProvider;
import com.navatar.location.details.QRCodeScanner;
import com.navatar.navigation.NavigationActivity;
import com.navatar.R;
import com.navatar.data.Building;
import com.navatar.data.Landmark;
import com.navatar.data.Map;
import com.navatar.data.Route;
import com.navatar.di.ActivityScoped;
import com.navatar.pathplanning.Step;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.BindViews;
import butterknife.ButterKnife;
import dagger.android.support.DaggerFragment;
import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.subjects.PublishSubject;

@ActivityScoped
public class MapsFragment extends DaggerFragment implements MapsContract.View {

    private final static String TAG = MapsFragment.class.getSimpleName();

    @BindView(R.id.mapSpinner)
    Spinner mapSpinner;

    @BindView(R.id.buildingSpinner)
    Spinner buildingSpinner;

    @BindView(R.id.fromSpinner)
    Spinner fromSpinner;

    @BindView(R.id.toSpinner)
    Spinner toSpinner;

    @BindView(R.id.startNavigationButton)
    Button startNavigationButton;

    @BindView(R.id.showNavigationButton)
    Button showNavigationButton;

    @BindView(R.id.barcode_scanner)
    DecoratedBarcodeView barcodeView;

    @BindView(R.id.showStepsListView)
    ListView showStepsListView;

    @BindViews({R.id.startNavigationButton, R.id.showNavigationButton})
    List<Button> showButtons;

    private static final ButterKnife.Action<View> VISIBLE =
            (v, index) -> v.setVisibility(View.VISIBLE);

    private static final ButterKnife.Action<View> GONE =
            (v, index) -> v.setVisibility(View.GONE);

    LinkedList<Spinner> spinners;
    Spinner currentSpinner;

    private final CompositeDisposable disposables = new CompositeDisposable();

    @Inject
    MapsContract.Presenter mPresenter;

    @Inject
    QRCodeScanner qrCodeScanner;

    @Inject
    TextToSpeechProvider mTextToSpeechProvider;

    @Inject
    Context mContext;

    @Inject
    public MapsFragment() { }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        mPresenter.takeView(this);
        barcodeView.resume();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disposables.clear();
        mPresenter.dropView();
    }

    @Override
    public void onPause() {
        super.onPause();
        mPresenter.dropView();
        barcodeView.pause();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.map_select_frag, container, false);
        ButterKnife.bind(this, root);
        spinners = new LinkedList<>();

        startNavigationButton.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), NavigationActivity.class);
            startActivity(intent);
        });

        showNavigationButton.setOnClickListener(v -> {
            mPresenter.onShowStepsSelected();
        });

        qrCodeScanner.setView(barcodeView, getActivity().getIntent());
        return root;
    }

    @Override
    public void showMaps(List<Map> maps) {
        MapListAdapter<Map> listAdapter = new MapListAdapter<>(mContext, mapSpinner, maps, R.string.mapSpinnerLabel);
        disposables.add(listAdapter.getSelected().subscribe(mPresenter::onMapSelected));
        currentSpinner = mapSpinner;

    }

    @Override
    public void showMap(Map map) {
        ButterKnife.apply(mapSpinner, GONE);
        ButterKnife.apply(buildingSpinner, VISIBLE);
        spinners.add(mapSpinner);
        currentSpinner = buildingSpinner;
        MapListAdapter<Building> listAdapter = new MapListAdapter<>(mContext, buildingSpinner, map.getBuildings(), R.string.buildingSpinnerLabel);
        disposables.add(listAdapter.getSelected().subscribe(mPresenter::onBuildingSelected));
    }

    @Override
    public void showFromLandmark(List<Landmark> landmark) {
        ButterKnife.apply(buildingSpinner, GONE);
        ButterKnife.apply(fromSpinner, VISIBLE);
        spinners.add(buildingSpinner);
        currentSpinner = fromSpinner;
        MapListAdapter<Landmark> listAdapter = new MapListAdapter<>(mContext, fromSpinner, landmark, R.string.fromSpinnerLabel);
        disposables.add(listAdapter.getSelected().subscribe(mPresenter::onFromLandmarkSelected));
    }

    @Override
    public void showToLandmark(List<Landmark> landmark) {
        ButterKnife.apply(fromSpinner, GONE);
        ButterKnife.apply(toSpinner, VISIBLE);
        spinners.add(fromSpinner);
        currentSpinner = toSpinner;
        MapListAdapter<Landmark> listAdapter = new MapListAdapter<>(mContext, toSpinner, landmark, R.string.toSpinnerLabel);
        disposables.add(listAdapter.getSelected().subscribe(mPresenter::onToLandmarkSelected));
    }

    @Override
    public void showNavigation(Route route) {
        spinners.add(toSpinner);
        ButterKnife.apply(spinners, GONE);
        ButterKnife.apply(showButtons, VISIBLE);
    }

    @Override
    public void showNoRouteFound() {
        mTextToSpeechProvider.speak(R.string.noRouteFound);
    }

    @Override
    public void showSteps(List<Step> steps) {
        ArrayAdapter<Step> list = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, steps);
        showStepsListView.setAdapter(list);
        showStepsListView.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean onBackPressed() {

        if (spinners.size() == 4) {
            ButterKnife.apply(showButtons, GONE);
            showStepsListView.setVisibility(View.GONE);
        }

        if (spinners.size() > 0) {
            // Remove current spinner, activate the last one and reset its selection
            currentSpinner.setVisibility(View.GONE);
            currentSpinner = spinners.removeLast();
            currentSpinner.setSelection(currentSpinner.getCount());
            currentSpinner.setVisibility(View.VISIBLE);
            /*  Since MapListAdapter sets its text to its hint in getView when the selection
                is greater than the number of options it has, setting the selection to that
                number effectively resets it. */
            return true;
        }
        return false;
    }

    private static class MapListAdapter<T> extends ArrayAdapter implements AdapterView.OnItemSelectedListener {

        private final String mHint;

        private PublishSubject<T> mSource = PublishSubject.create();

        public MapListAdapter(Context context, Spinner spinner, List<T> items, int hint) {
            this(context, items, hint);
            setSpinner(spinner);
        }

        public MapListAdapter(Context context, List<T> items, int hint) {
            this(context, items, context.getResources().getString(hint));
        }

        public MapListAdapter(Context context, List<T> items, String hint) {
            super(context, android.R.layout.simple_spinner_item, items);
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            mHint = hint;
        }

        private void setSpinner(Spinner spinner) {
            spinner.setAdapter(this);
            spinner.setOnItemSelectedListener(this);
            spinner.setSelection(getCount());
        }

        public Observable<T> getSelected() {
            return mSource;
        }

        @Override
        public int getCount() {
            return super.getCount();
        }

        @Override
        public boolean isEnabled(int position){
            if(position >= getCount()) {
                return false;
            }
            return true;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            View v = null;
            if (position >= getCount()) {
                v = super.getView(0, convertView, parent);
                TextView tv = (TextView) v;
                tv.setText("");
                tv.setHint(mHint);
            } else {
                v = super.getView(position, convertView, parent);
            }
            return v;
        }

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            if (position < getCount()) {
                mSource.onNext((T)getItem(position));
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {}

    }
}