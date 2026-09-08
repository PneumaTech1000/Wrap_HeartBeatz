package com.giga.tech1000.heartbeatz.ui.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class  StateFragmentAdapter extends FragmentStateAdapter {

    private final LinkedHashMap<Class<?>, Fragment> fragments;
    private final FragmentManager manager;

    public StateFragmentAdapter(@NonNull FragmentManager fragmentManager, @NonNull Lifecycle lifecycle) {
        super(fragmentManager, lifecycle);

        this.fragments = new LinkedHashMap<>();
        this.manager = fragmentManager;
    }

    public void addFragment(Class<?> f, Object... fragment) {
        try {
            if (fragment != null && fragment.length > 0) {
                Class<?>[] classes = new Class[fragment.length];
                for (int i=0; i<fragment.length; i++) {
                    Object obj = fragment[i];
                    classes[i] = Class.forName(obj.getClass().getName());
                }

                Constructor<?> constructor = f.getDeclaredConstructor(classes);
                Fragment fragmentInstance = (Fragment) constructor.newInstance(fragment);
                this.fragments.put(f, fragmentInstance);

            } else {
                addFragment(f);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void addFragment(Class<?> f) {
        try {
            Constructor<?> constructor = f.getDeclaredConstructor();
            Fragment fragment = (Fragment) constructor.newInstance();
            this.fragments.put(f, fragment);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void addFragment(Object f) {
        if (!(f instanceof Fragment)) throw new RuntimeException("Not instance of Fragment");

        try {
            Class<?> clazz = Class.forName(f.getClass().getName());
            this.fragments.put(clazz, (Fragment) f);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

    }

    public Fragment getFragment(int position) {
        return (Fragment) this.fragments.values().toArray()[position];
    }

    public <T extends Fragment> T getFragment(Class<T> fragmentClass) {
        return (T) this.fragments.get(fragmentClass);
    }



    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return (Fragment) this.fragments.values().toArray()[position];
    }

    @Override
    public int getItemCount() {
        return this.fragments.size();
    }
}
