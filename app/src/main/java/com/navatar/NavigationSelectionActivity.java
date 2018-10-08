package com.navatar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import com.navatar.maps.BuildingMapWrapper;
import com.navatar.maps.LandmarkWrapper;
import com.navatar.maps.MapService;

public class NavigationSelectionActivity extends Activity {
  private float stepLength = 0.0f;
  private Spinner typeSpinner, modeSpinner, maxDistanceSpinner, fromRoomSpinner, toRoomSpinner;
  private Button startNavigationButton;

 // private ArrayAdapter<?> typeArrayAdapter, modeArrayAdapter, maxDistanceArrayAdapter;
  private ArrayAdapter<LandmarkWrapper> roomArrayAdapter;
  private String userName = "";
//  private String typeItemSelected, modeItemSelected, maxDistanceItemSelected;
  private LandmarkWrapper fromRoomItemSelected, toRoomItemSelected;

  private MapService mapService;
  private BuildingMapWrapper map;

  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setTitle("Navigation");
    setContentView(R.layout.navigation_selection_layout);

    Intent mapIntent = new Intent(this, MapService.class);
    startService(mapIntent);
    bindService(mapIntent, mMapConnection, BIND_AUTO_CREATE);

 /*   typeSpinner = (Spinner) findViewById(R.id.typeSpinner);
    modeSpinner = (Spinner) findViewById(R.id.modeSpinner);
    maxDistanceSpinner = (Spinner) findViewById(R.id.maxDistanceSpinner);*/
    fromRoomSpinner = (Spinner) findViewById(R.id.fromSpinner);
    toRoomSpinner = (Spinner) findViewById(R.id.toSpinner);
    startNavigationButton = (Button) findViewById(R.id.startNavigationButton);

   /* typeArrayAdapter =
        ArrayAdapter.createFromResource(this, R.array.typeArray,
            android.R.layout.simple_spinner_item);
    typeArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    typeSpinner.setAdapter(typeArrayAdapter);

    modeArrayAdapter =
        ArrayAdapter.createFromResource(this, R.array.modeArray,
            android.R.layout.simple_spinner_item);
    modeArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    modeSpinner.setAdapter(modeArrayAdapter);

    maxDistanceArrayAdapter =
        ArrayAdapter.createFromResource(this, R.array.maxDistanceArray,
            android.R.layout.simple_spinner_item);
    maxDistanceArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    maxDistanceSpinner.setAdapter(maxDistanceArrayAdapter);*/

    // roomArrayAdapter = ArrayAdapter.createFromResource(
    // this, R.array.roomArray, android.R.layout.simple_spinner_item);

    roomArrayAdapter =
        new ArrayAdapter<LandmarkWrapper>(this, R.layout.room_layout,
            new ArrayList<LandmarkWrapper>());
    roomArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

    fromRoomSpinner.setAdapter(roomArrayAdapter);
    toRoomSpinner.setAdapter(roomArrayAdapter);

    fromRoomSpinner.setOnItemSelectedListener(fromRoomSpinnerItemSelected);
    toRoomSpinner.setOnItemSelectedListener(toRoomSpinnerItemSelected);

    // ****************
    // Listeners:
    // ****************

/*    typeSpinner.setOnItemSelectedListener(typeSpinnerItemSelected);
    modeSpinner.setOnItemSelectedListener(modeSpinnerItemSelected);
    maxDistanceSpinner.setOnItemSelectedListener(maxDistanceSpinnerItemSelected);*/
    startNavigationButton.setOnClickListener(startNavigationButtonClickListener);

    Bundle extras = getIntent().getExtras();
    if (extras != null) {
      stepLength = extras.getFloat("com.Navatar.stepLength");
      userName = extras.getString("com.Navatar.userName");
    }
  }

  

/*
  OnItemSelectedListener typeSpinnerItemSelected = new OnItemSelectedListener() {
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
      typeItemSelected = typeSpinner.getItemAtPosition(position).toString();
    }

    public void onNothingSelected(AdapterView<?> arg0) {}
  };

  OnItemSelectedListener modeSpinnerItemSelected = new OnItemSelectedListener() {
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
      modeItemSelected = modeSpinner.getItemAtPosition(position).toString();
    }

    public void onNothingSelected(AdapterView<?> arg0) {}
  };

  OnItemSelectedListener maxDistanceSpinnerItemSelected = new OnItemSelectedListener() {
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
      maxDistanceItemSelected = maxDistanceSpinner.getItemAtPosition(position).toString();
    }

    public void onNothingSelected(AdapterView<?> arg0) {}
  };
*/

  OnItemSelectedListener fromRoomSpinnerItemSelected = new OnItemSelectedListener() {
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
      fromRoomItemSelected = (LandmarkWrapper) fromRoomSpinner.getItemAtPosition(position);
    }

    public void onNothingSelected(AdapterView<?> arg0) {}
  };

  OnItemSelectedListener toRoomSpinnerItemSelected = new OnItemSelectedListener() {
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
      toRoomItemSelected = (LandmarkWrapper) toRoomSpinner.getItemAtPosition(position);
    }

    public void onNothingSelected(AdapterView<?> arg0) {}
  };

  OnClickListener startNavigationButtonClickListener = new OnClickListener() {
    public void onClick(View v) {
      startNavigation();
    }
  };

  @Override
  protected void onDestroy() {
    super.onDestroy();
    unbindService(mMapConnection);
  }

  private void startNavigation() {
    Intent myIntent = new Intent(this, NavigationActivity.class);
    myIntent.putExtra("com.Navatar.userName", userName);
    myIntent.putExtra("com.Navatar.stepLength", stepLength);
/*    myIntent.putExtra("com.Navatar.type", typeItemSelected);
    myIntent.putExtra("com.Navatar.mode", modeItemSelected);
    myIntent.putExtra("com.Navatar.maxDistance", maxDistanceItemSelected);*/
    //myIntent.putExtra("com.Navatar.fromRoom", fromRoomItemSelected.getLandmark());
    //myIntent.putExtra("com.Navatar.toRoom", toRoomItemSelected.getLandmark());
    startActivity(myIntent);
  }

  /** Defines callback for service binding, passed to bindService() */
  private ServiceConnection mMapConnection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
      MapService.MapBinder binder = (MapService.MapBinder) service;
      mapService = binder.getService();
      ArrayList<LandmarkWrapper> rooms = new ArrayList<LandmarkWrapper>();
      map = mapService.getActiveMap();
      rooms.addAll(map.destinations());
      Collections.sort(rooms, new Comparator<LandmarkWrapper>() {
        public int compare(LandmarkWrapper lhs, LandmarkWrapper rhs) {
          return lhs.toString().compareToIgnoreCase(rhs.toString());

        }
      });
      roomArrayAdapter.clear();
      roomArrayAdapter.addAll(rooms);
    }

    @Override
    public void onServiceDisconnected(ComponentName arg0) {
      mapService = null;
    }

  };
}