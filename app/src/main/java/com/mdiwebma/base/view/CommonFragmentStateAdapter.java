package com.mdiwebma.base.view;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.mdiwebma.base.view.CommonFragmentModel;

import java.util.ArrayList;


// with ViewPage2
public class CommonFragmentStateAdapter extends FragmentStateAdapter {

    private final ArrayList<CommonFragmentModel> modelList = new ArrayList<>();

    public CommonFragmentStateAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return modelList.get(position).newFragment();
    }

    @Override
    public int getItemCount() {
        return modelList.size();
    }

    public void addFragmentModel(CommonFragmentModel model) {
        modelList.add(model);
    }

    public CharSequence getPageTitle(int position) {
        return modelList.get(position).getPageTitle();
    }

}