package com.mdiwebma.base.view;

import androidx.fragment.app.Fragment;

public interface CommonFragmentModel {

    CharSequence getPageTitle();

    String getFragmentKey();

    Fragment newFragment();
}
