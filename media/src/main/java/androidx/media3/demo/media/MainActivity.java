package androidx.media3.demo.media;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class MainActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private TabLayout tabLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewPager = findViewById(R.id.view_pager);
        tabLayout = findViewById(R.id.tab_layout);

        ViewPagerAdapter adapter = new ViewPagerAdapter(this);
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText(R.string.tab_home);
                    tab.setIcon(android.R.drawable.ic_menu_gallery);
                    break;
                case 1:
                    tab.setText(R.string.tab_pending_delete);
                    tab.setIcon(android.R.drawable.ic_menu_delete);
                    break;
                case 2:
                    tab.setText(R.string.tab_folders);
                    tab.setIcon(android.R.drawable.ic_menu_agenda);
                    break;
                case 3:
                    tab.setText(R.string.tab_more);
                    tab.setIcon(android.R.drawable.ic_menu_more);
                    break;
            }
        }).attach();

        // 请求权限
        if (!PermissionHelper.hasMediaPermissions(this)) {
            PermissionHelper.requestMediaPermissions(this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionHelper.PERMISSION_REQUEST_CODE) {
            // 权限授予后刷新 Fragment
            Fragment fragment = getSupportFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem());
            if (fragment instanceof HomeFragment) {
                ((HomeFragment) fragment).refreshFiles();
            } else if (fragment instanceof PendingDeleteFragment) {
                ((PendingDeleteFragment) fragment).loadPendingFiles();
            } else if (fragment instanceof FolderFragment) {
                ((FolderFragment) fragment).refreshFolders();
            } else if (fragment instanceof MoreFragment) {
                ((MoreFragment) fragment).refreshFiles();
            }
        }
    }

    private static class ViewPagerAdapter extends FragmentStateAdapter {
        public ViewPagerAdapter(FragmentActivity activity) {
            super(activity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new HomeFragment();
                case 1:
                    return new PendingDeleteFragment();
                case 2:
                    return new FolderFragment();
                case 3:
                    return new MoreFragment();
                default:
                    return new HomeFragment();
            }
        }

        @Override
        public int getItemCount() {
            return 4;
        }
    }
}

