package me.jfenn.alarmio.fragments;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import io.reactivex.disposables.Disposable;
import me.jfenn.alarmio.R;
import me.jfenn.alarmio.interfaces.ContextFragmentInstantiator;

public class RealStatusFragment extends BasePagerFragment {

    private RecyclerView recyclerView;

    private Disposable textColorPrimarySubscription;
    private Context context;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_recycler, container, false);
        recyclerView = v.findViewById(R.id.recycler);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 1));


        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        textColorPrimarySubscription.dispose();
    }


    @Override
    public String getTitle(Context context) {
        return context.getString(R.string.title_RealStatus);
    }

    public static class Instantiator extends ContextFragmentInstantiator {

        public Instantiator(Context context) {
            super(context);
        }

        @Override
        public String getTitle(Context context, int position) {
            return context.getString(R.string.title_RealStatus);
        }

        @Nullable
        @Override
        public BasePagerFragment newInstance(int position) {
            return new SettingsFragment();
        }
    }
}
