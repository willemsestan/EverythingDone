package com.ywwynm.everythingdone.appwidgets.single;

import android.Manifest;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RemoteViews;

import com.bumptech.glide.Glide;
import com.ywwynm.everythingdone.Def;
import com.ywwynm.everythingdone.R;
import com.ywwynm.everythingdone.activities.EverythingDoneBaseActivity;
import com.ywwynm.everythingdone.adapters.BaseThingsAdapter;
import com.ywwynm.everythingdone.appwidgets.AppWidgetHelper;
import com.ywwynm.everythingdone.database.AppWidgetDAO;
import com.ywwynm.everythingdone.database.ThingDAO;
import com.ywwynm.everythingdone.managers.ModeManager;
import com.ywwynm.everythingdone.model.Thing;
import com.ywwynm.everythingdone.permission.PermissionUtil;
import com.ywwynm.everythingdone.permission.SimplePermissionCallback;
import com.ywwynm.everythingdone.utils.DeviceUtil;
import com.ywwynm.everythingdone.utils.DisplayUtil;
import com.ywwynm.everythingdone.utils.EdgeEffectUtil;

import java.util.List;

public class BaseThingWidgetConfiguration extends EverythingDoneBaseActivity {

    protected Class getSenderClass() {
        return BaseThingWidget.class;
    }

    private Toolbar      mActionBar;
    private RecyclerView mRecyclerView;

    private ThingsAdapter mAdapter;
    private List<Thing> mThings;
    private StaggeredGridLayoutManager mStaggeredGridLayoutManager;

    private int mSpanCount;

    private int mAppWidgetId;

    @Override
    protected int getLayoutResource() {
        return R.layout.activity_thing_widget_configure;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        mSpanCount = DisplayUtil.isTablet(this) ? 3 : 2;
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mSpanCount++;
        }
        mStaggeredGridLayoutManager.setSpanCount(mSpanCount);

        if (mThings.size() > 1) {
            mRecyclerView.scrollToPosition(0);
        }
        mAdapter.notifyDataSetChanged();
    }

    @Override
    protected void initMembers() {
        Intent intent = getIntent();
        mAppWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
        setResult(RESULT_CANCELED, intent);

        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        mSpanCount = DisplayUtil.isTablet(this) ? 3 : 2;
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mSpanCount++;
        }

        mThings = ThingDAO.getInstance(this)
                .getThingsForDisplay(Def.LimitForGettingThings.ALL_UNDERWAY);
        mThings.remove(0); // no header
        mAdapter = new ThingsAdapter();
    }

    @Override
    protected void findViews() {
        mActionBar    = f(R.id.actionbar);
        mRecyclerView = f(R.id.rv_things);
    }

    @Override
    protected void initUI() {
        DisplayUtil.expandLayoutAboveLollipop(this);
        DisplayUtil.expandStatusBarAboveKitkat(findViewById(R.id.view_status_bar));
        DisplayUtil.darkStatusBar(this);

        if (!PermissionUtil.hasStoragePermission(this)
                && PermissionUtil.shouldRequestPermissionWhenLoadingThings(mThings)) {
            doWithPermissionChecked(new SimplePermissionCallback(this) {
                @Override
                public void onGranted() {
                    initRecyclerView();
                }

                @Override
                public void onDenied() {
                    super.onDenied();
                    finish();
                }
            }, Def.Communication.REQUEST_PERMISSION_LOAD_THINGS_2,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE);
        } else {
            initRecyclerView();
        }
    }

    private void initRecyclerView() {
        mStaggeredGridLayoutManager = new StaggeredGridLayoutManager(
                mSpanCount, StaggeredGridLayoutManager.VERTICAL);
        mRecyclerView.setLayoutManager(mStaggeredGridLayoutManager);
        mRecyclerView.setAdapter(mAdapter);
    }

    @Override
    protected void setActionbar() {
        setSupportActionBar(mActionBar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mActionBar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    @Override
    protected void setEvents() {
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {

            final int edgeColor = EdgeEffectUtil.getEdgeColorDark();
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                EdgeEffectUtil.forRecyclerView(recyclerView, edgeColor);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    Glide.with(BaseThingWidgetConfiguration.this).resumeRequests();
                } else { // dragging or settling
                    Glide.with(BaseThingWidgetConfiguration.this).pauseRequests();
                }
            }
        });
    }

    private void endSelectThing(Thing thing) {
        Class clazz = getSenderClass();
        AppWidgetDAO.getInstance(this).insert(mAppWidgetId, thing.getId(),
                AppWidgetHelper.getSizeByProviderClass(clazz));

        RemoteViews views = AppWidgetHelper.createRemoteViewsForSingleThing(
                this, thing, -1, mAppWidgetId, clazz);
        AppWidgetManager.getInstance(this).updateAppWidget(mAppWidgetId, views);

        Intent intent = new Intent();
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
        setResult(RESULT_OK, intent);
        finish();
    }

    class ThingsAdapter extends BaseThingsAdapter {

        public ThingsAdapter() {
            super(BaseThingWidgetConfiguration.this);
        }

        @Override
        protected List<Thing> getThings() {
            return mThings;
        }

        @Override
        protected int getCurrentMode() {
            return ModeManager.NORMAL;
        }

        @Override
        public BaseThingViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new Holder(mInflater.inflate(R.layout.card_thing, parent, false));
        }

        @Override
        public void onBindViewHolder(BaseThingViewHolder holder, int position) {
            int m = (int) (mScreenDensity * 4);
            if (DeviceUtil.hasLollipopApi()) {
                m = (int) (mScreenDensity * 6);
            }

            StaggeredGridLayoutManager.LayoutParams lp =
                    (StaggeredGridLayoutManager.LayoutParams) holder.itemView.getLayoutParams();
            lp.setMargins(m, m, m, m);

            super.onBindViewHolder(holder, position);
        }

        class Holder extends BaseThingViewHolder {

            public Holder(View item) {
                super(item);

                cv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        endSelectThing(mThings.get(getAdapterPosition()));
                    }
                });
            }
        }

    }
}
