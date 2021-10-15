package me.jfenn.alarmio.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import me.jfenn.alarmio.R;

public class RealStateFragment extends Fragment {

    private View view;

    public static RealStateFragment newInstance() {
        RealStateFragment RealStateFragment = new RealStateFragment();
        return RealStateFragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_realstatus, container,false);

        return view;
    }//onCreateView close
}
