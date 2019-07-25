package com.russ.locationalarm;

import android.content.Context;
import com.google.android.gms.maps.model.LatLng;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

class SerialLatLng implements Serializable {

    private double latitude;
    private double longitude;
    public SerialLatLng(double latitude, double longitude){
        this.latitude = latitude;
        this.longitude = longitude;
    }
    public double getLatitude(){return latitude;}
    public double getLongitude(){return longitude;}

    public LatLng toLatLng(){
        return new LatLng(latitude,longitude);
    }

    @Override
    public boolean equals(Object other){
        if(((SerialLatLng) other).latitude == latitude && ((SerialLatLng) other).longitude == longitude)
            return true;
        else return false;
    }
}


public class HeatmapLogic {

    public static HashSet<SerialLatLng> setLatLngs = new HashSet<>();

    public static ArrayList<SerialLatLng> listLatLngs = new ArrayList<SerialLatLng>();

    public final static double MIN_DIST = 0.00025;



    public static void addLocation(SerialLatLng newLatLng){
        addListLocation(newLatLng);
        addSetLocation(newLatLng);
    }

    private static void addSetLocation(SerialLatLng newLatLng){
        setLatLngs.add(newLatLng);
    }

    private static void addListLocation(SerialLatLng newLatLng){
        boolean tooClose = false;

        for(SerialLatLng latLng: listLatLngs){
            if(latLngDifference(newLatLng,latLng) < MIN_DIST) {
                tooClose = true;
                break;
            }
        }

        if(!tooClose)
            listLatLngs.add(newLatLng);

    }

    public static double latLngDifference(SerialLatLng latLng1, SerialLatLng latLng2){
        return Math.abs(latLng1.getLatitude()-latLng2.getLatitude())
                + Math.abs(latLng1.getLongitude()-latLng2.getLongitude());
    }

    public static HashSet<LatLng> serialLatLngToLatLng(HashSet<SerialLatLng> serialLatLngs){
        HashSet<LatLng> latLngs = new HashSet<>();
        for(SerialLatLng serialLatLng: serialLatLngs)
            latLngs.add(serialLatLng.toLatLng());

        return latLngs;
    }

    public static ArrayList<LatLng> serialLatLngToLatLng(ArrayList<SerialLatLng> serialLatLngs){
        ArrayList<LatLng> latLngs = new ArrayList<>();
        for(SerialLatLng serialLatLng: serialLatLngs)
            latLngs.add(serialLatLng.toLatLng());

        return latLngs;
    }

    public static void setStoredHashSet(HashSet<SerialLatLng> loadedHashSet){
        setLatLngs = loadedHashSet;
    }

    public static void setStoredList(ArrayList<SerialLatLng> loadedList){
        listLatLngs = loadedList;
    }

    public static ArrayList<SerialLatLng> getListLatLngs(){
        return  listLatLngs;
    }

    public static HashSet<SerialLatLng> getHashSetLatLngs(){
        return setLatLngs;
    }

    public static void resetStoredLatLngs(){

        listLatLngs = new ArrayList<SerialLatLng>();
        setLatLngs = new HashSet<SerialLatLng>();

    }







}
