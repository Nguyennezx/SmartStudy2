package com.example.smartstudy.main;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;

import com.example.smartstudy.R;
import com.example.smartstudy.device.DeviceFragment;
import com.example.smartstudy.home.HomeFragment;
import com.example.smartstudy.schedule.ScheduleFragment;
import com.example.smartstudy.statistics.StatisticsFragment;
import com.example.smartstudy.study.StudyFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private static final String STATE_SELECTED_TAB = "selected_navigation_tab";
    private int selectedTabId = R.id.nav_home;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (savedInstanceState != null) {
            selectedTabId = savedInstanceState.getInt(STATE_SELECTED_TAB, R.id.nav_home);
        }

        BottomNavigationView navigation = findViewById(R.id.bottom_navigation);
        // Root đã chừa chỗ cho system bars, không cộng lại inset vào thanh tab.
        ViewCompat.setOnApplyWindowInsetsListener(navigation, (view, insets) -> insets);
        navigation.setSelectedItemId(selectedTabId);
        showTab(selectedTabId);
        navigation.setOnItemSelectedListener(item -> showTab(item.getItemId()));
        navigation.setOnItemReselectedListener(item -> {
            // Keep the current screen when its tab is tapped again.
        });
    }

    private boolean showTab(int itemId) {
        Class<? extends Fragment> fragmentClass;
        if (itemId == R.id.nav_home) {
            fragmentClass = HomeFragment.class;
        } else if (itemId == R.id.nav_schedule) {
            fragmentClass = ScheduleFragment.class;
        } else if (itemId == R.id.nav_study) {
            fragmentClass = StudyFragment.class;
        } else if (itemId == R.id.nav_device) {
            fragmentClass = DeviceFragment.class;
        } else if (itemId == R.id.nav_statistics) {
            fragmentClass = StatisticsFragment.class;
        } else {
            return false;
        }

        FragmentManager manager = getSupportFragmentManager();
        if (manager.isStateSaved()) {
            return false;
        }

        String tag = fragmentClass.getName();
        Fragment target = manager.findFragmentByTag(tag);
        Fragment current = manager.getPrimaryNavigationFragment();
        if (target != null && target == current) {
            selectedTabId = itemId;
            return true;
        }

        FragmentTransaction transaction = manager.beginTransaction()
                .setReorderingAllowed(true);
        if (current != null) {
            transaction.hide(current)
                    .setMaxLifecycle(current, Lifecycle.State.STARTED);
        }
        if (target == null) {
            target = manager.getFragmentFactory().instantiate(getClassLoader(), tag);
            transaction.add(R.id.fragment_container, target, tag);
        } else {
            transaction.show(target);
        }
        transaction.setMaxLifecycle(target, Lifecycle.State.RESUMED)
                .setPrimaryNavigationFragment(target)
                .commitNow();
        selectedTabId = itemId;
        return true;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(STATE_SELECTED_TAB, selectedTabId);
        super.onSaveInstanceState(outState);
    }
}
