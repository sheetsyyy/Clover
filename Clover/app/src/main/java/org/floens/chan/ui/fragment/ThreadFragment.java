/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.chan.ui.fragment;

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.volley.NetworkError;
import com.android.volley.NoConnectionError;
import com.android.volley.ServerError;
import com.android.volley.VolleyError;

import org.floens.chan.R;
import org.floens.chan.core.loader.EndOfLineException;
import org.floens.chan.core.loader.Loader;
import org.floens.chan.core.manager.ThreadManager;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Post;
import org.floens.chan.ui.activity.BaseActivity;
import org.floens.chan.ui.activity.ImageViewActivity;
import org.floens.chan.ui.adapter.PostAdapter;
import org.floens.chan.ui.view.LoadView;
import org.floens.chan.utils.Utils;

import java.util.List;

public class ThreadFragment extends Fragment implements ThreadManager.ThreadManagerListener, PostAdapter.PostAdapterListener {
    private BaseActivity baseActivity;
    private ThreadManager threadManager;
    private Loadable loadable;

    private PostAdapter postAdapter;
    private LoadView container;
    private AbsListView listView;
    private ImageView skip;
    private FilterView filterView;

    private SkipLogic skipLogic;
    private int highlightedPost = -1;
    private ThreadManager.ViewMode viewMode = ThreadManager.ViewMode.LIST;
    private String lastFilter = "";
    private boolean isFiltering = false;

    public static ThreadFragment newInstance(BaseActivity activity) {
        ThreadFragment fragment = new ThreadFragment();
        fragment.baseActivity = activity;
        fragment.threadManager = new ThreadManager(activity, fragment);

        return fragment;
    }

    public void bindLoadable(Loadable l) {
        if (loadable != null) {
            threadManager.unbindLoader();
        }

        setEmpty();

        loadable = l;
        threadManager.bindLoader(loadable);
    }

    public void requestData() {
        threadManager.requestData();
    }

    public void reload() {
        setEmpty();

        threadManager.requestData();
    }

    public void openReply() {
        if (threadManager.hasLoader()) {
            threadManager.openReply(true);
        }
    }

    public boolean hasLoader() {
        return threadManager.hasLoader();
    }

    public void setViewMode(ThreadManager.ViewMode viewMode) {
        this.viewMode = viewMode;
    }

    public Loader getLoader() {
        return threadManager.getLoader();
    }

    public void setFilter(String filter) {
        if (!filter.equals(lastFilter) && postAdapter != null) {
            lastFilter = filter;
            postAdapter.setFilter(filter);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (threadManager != null) {
            threadManager.onDestroy();
        }
        threadManager = null;
        loadable = null;
    }

    @Override
    public void onStart() {
        super.onStart();

        if (threadManager != null) {
            threadManager.onStart();
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        if (threadManager != null) {
            threadManager.onStop();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        container = new LoadView(inflater.getContext());
        return container;
    }

    @Override
    public void onPostClicked(Post post) {
        if (loadable.isBoardMode() || loadable.isCatalogMode()) {
            baseActivity.onOPClicked(post);
        } else if (loadable.isThreadMode() && !TextUtils.isEmpty(lastFilter)) {
            baseActivity.onSetFilter("");
            postAdapter.scrollToPost(post.no);
        }
    }

    @Override
    public void onThumbnailClicked(Post source) {
        if (postAdapter != null) {
            ImageViewActivity.setAdapter(postAdapter, source.no);

            Intent intent = new Intent(baseActivity, ImageViewActivity.class);
            baseActivity.startActivity(intent);
            baseActivity.overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        }
    }

    @Override
    public void onScrollTo(int post) {
        if (postAdapter != null) {
            postAdapter.scrollToPost(post);
        }
    }

    @Override
    public void onRefreshView() {
        if (postAdapter != null) {
            postAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onOpenThread(final Loadable thread, int highlightedPost) {
        baseActivity.onOpenThread(thread);
        this.highlightedPost = highlightedPost;
    }

    @Override
    public ThreadManager.ViewMode getViewMode() {
        return viewMode;
    }

    @Override
    public void onThreadLoaded(List posts, boolean append) {
        if (postAdapter == null) {
            RelativeLayout compound = new RelativeLayout(baseActivity);

            LinearLayout listViewContainer = new LinearLayout(baseActivity);
            listViewContainer.setOrientation(LinearLayout.VERTICAL);

            filterView = new FilterView(baseActivity);
            filterView.setVisibility(View.GONE);
            listViewContainer.addView(filterView, Utils.MATCH_WRAP_PARAMS);

            if (viewMode == ThreadManager.ViewMode.LIST) {
                ListView list = new ListView(baseActivity);
                listView = list;
                postAdapter = new PostAdapter(baseActivity, threadManager, listView, this);
                listView.setAdapter(postAdapter);
                list.setSelectionFromTop(loadable.listViewIndex, loadable.listViewTop);
            } else if (viewMode == ThreadManager.ViewMode.GRID) {
                GridView grid = new GridView(baseActivity);
                grid.setNumColumns(GridView.AUTO_FIT);
                TypedArray ta = baseActivity.obtainStyledAttributes(null, R.styleable.PostView, R.attr.post_style, 0);
                int postGridWidth = ta.getDimensionPixelSize(R.styleable.PostView_grid_width, 0);
                int postGridSpacing = ta.getDimensionPixelSize(R.styleable.PostView_grid_spacing, 0);
                ta.recycle();
                grid.setColumnWidth(postGridWidth);
                grid.setVerticalSpacing(postGridSpacing);
                grid.setHorizontalSpacing(postGridSpacing);
                listView = grid;
                postAdapter = new PostAdapter(baseActivity, threadManager, listView, this);
                listView.setAdapter(postAdapter);
                listView.setSelection(loadable.listViewIndex);
            }

            listView.setOnScrollListener(new OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {
                    if (!isFiltering) {
                        if (skipLogic != null) {
                            skipLogic.onScrollStateChanged(view, scrollState);
                        }
                    }
                }

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    if (!isFiltering) {
                        if (loadable != null) {
                            int index = view.getFirstVisiblePosition();
                            View v = view.getChildAt(0);
                            int top = v == null ? 0 : v.getTop();
                            if (index != 0 || top != 0) {
                                loadable.listViewIndex = index;
                                loadable.listViewTop = top;
                            }
                        }
                        if (skipLogic != null) {
                            skipLogic.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
                        }
                    }
                }
            });

            listViewContainer.addView(listView, Utils.MATCH_PARAMS);

            compound.addView(listViewContainer, Utils.MATCH_PARAMS);

            if (loadable.isThreadMode()) {
                skip = new ImageView(baseActivity);
                skip.setImageResource(R.drawable.skip_arrow_down);
                skip.setVisibility(View.GONE);
                compound.addView(skip, Utils.WRAP_PARAMS);

                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) skip.getLayoutParams();
                params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
                params.setMargins(0, 0, Utils.dp(8), Utils.dp(8));
                skip.setLayoutParams(params);

                skipLogic = new SkipLogic(skip, listView);
            }

            if (container != null) {
                container.setView(compound);
            }
        }

        postAdapter.setStatusMessage(null);

        if (append) {
            postAdapter.appendList(posts);
        } else {
            postAdapter.setList(posts);
        }

        if (highlightedPost >= 0) {
            threadManager.highlightPost(highlightedPost);
            postAdapter.scrollToPost(highlightedPost);
            highlightedPost = -1;
        }

        baseActivity.onThreadLoaded(loadable, posts);
    }

    @Override
    public void onThreadLoadError(VolleyError error) {
        if (error instanceof EndOfLineException) {
            postAdapter.setEndOfLine(true);
        } else {
            if (postAdapter == null) {
                if (container != null) {
                    container.setView(getLoadErrorTextView(error));
                }
            } else {
                postAdapter.setStatusMessage(getLoadErrorText(error));
            }
        }

        highlightedPost = -1;
    }

    public void onFilterResults(String filter, int count, boolean all) {
        isFiltering = !all;

        if (filterView != null) {
            if (all) {
                filterView.setVisibility(View.GONE);
            } else {
                filterView.setVisibility(View.VISIBLE);
                filterView.setText(filter, count);
            }
        }
    }

    private void setEmpty() {
        postAdapter = null;

        if (container != null) {
            container.setView(null);
        }

        if (listView != null) {
            listView.setOnScrollListener(null);
            listView = null;
        }

        skip = null;
        skipLogic = null;
    }

    /**
     * Returns an TextView containing the appropriate error message
     *
     * @param error
     * @return
     */
    private TextView getLoadErrorTextView(VolleyError error) {
        String errorMessage = getLoadErrorText(error);

        TextView view = new TextView(getActivity());
        view.setLayoutParams(Utils.MATCH_PARAMS);
        view.setText(errorMessage);
        view.setTextSize(24f);
        view.setGravity(Gravity.CENTER);

        return view;
    }

    private String getLoadErrorText(VolleyError error) {
        String errorMessage;

        if ((error instanceof NoConnectionError) || (error instanceof NetworkError)) {
            errorMessage = getActivity().getString(R.string.thread_load_failed_network);
        } else if (error instanceof ServerError) {
            errorMessage = getActivity().getString(R.string.thread_load_failed_server);
        } else {
            errorMessage = getActivity().getString(R.string.thread_load_failed_parsing);
        }

        return errorMessage;
    }

    private static class SkipLogic {
        private final ImageView skip;
        private int lastFirstVisibleItem;
        private int lastTop;
        private boolean up = false;
        private final AbsListView listView;

        public SkipLogic(ImageView skipView, AbsListView list) {
            skip = skipView;
            listView = list;
            skip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (up) {
                        listView.setSelection(0);
                    } else {
                        listView.setSelection(listView.getCount() - 1);
                    }
                    skip.setVisibility(View.GONE);
                }
            });
        }

        public void onScrollStateChanged(AbsListView view, int scrollState) {
            if (scrollState != AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                skip.setVisibility(View.VISIBLE);
            }
        }

        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            View v = view.getChildAt(0);
            int top = (v == null) ? 0 : v.getTop();

            if (firstVisibleItem == lastFirstVisibleItem) {
                if (top > lastTop) {
                    onUp();
                } else if (top < lastTop) {
                    onDown();
                }
            } else {
                if (firstVisibleItem > lastFirstVisibleItem) {
                    onDown();
                } else {
                    onUp();
                }
            }
            lastFirstVisibleItem = firstVisibleItem;
            lastTop = top;
        }

        private void onUp() {
            skip.setImageResource(R.drawable.skip_arrow_up);
            up = true;
        }

        private void onDown() {
            skip.setImageResource(R.drawable.skip_arrow_down);
            up = false;
        }
    }

    public class FilterView extends LinearLayout {
        private TextView textView;

        public FilterView(Context activity) {
            super(activity);
            init();
        }

        public FilterView(Context activity, AttributeSet attr) {
            super(activity, attr);
            init();
        }

        public FilterView(Context activity, AttributeSet attr, int style) {
            super(activity, attr, style);
            init();
        }

        private void init() {
            textView = new TextView(getContext());
            textView.setGravity(Gravity.CENTER);
            addView(textView, new LayoutParams(LayoutParams.MATCH_PARENT, Utils.dp(48)));
        }

        private void setText(String filter, int count) {
            String posts = getContext().getString(count == 1 ? R.string.one_post : R.string.multiple_posts);
            String text = getContext().getString(R.string.search_results, Integer.toString(count), posts, filter);
            textView.setText(text);
        }
    }
}