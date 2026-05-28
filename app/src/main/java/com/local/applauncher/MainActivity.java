package com.local.applauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String PREFS_NAME = "launcher_preferences";
    private static final String PREF_CATEGORY_SCHEMA_VERSION = "category_schema_version";
    private static final int CATEGORY_SCHEMA_VERSION = 2;
    private static final String CATEGORY_ALL = "全部";
    private static final String CATEGORY_FAVORITES = "常用";
    private static final String CATEGORY_HIDDEN = "隐藏";
    private static final List<String> CATEGORIES = Arrays.asList(
            CATEGORY_ALL,
            CATEGORY_FAVORITES,
            "游戏",
            "娱乐",
            "AI",
            "学习",
            "社交",
            "支付",
            "购物",
            "出行",
            "其他",
            CATEGORY_HIDDEN
    );

    private final List<AppEntry> allApps = new ArrayList<>();
    private final List<AppEntry> filteredApps = new ArrayList<>();
    private final Map<String, TextView> categoryChips = new LinkedHashMap<>();
    private final Collator collator = Collator.getInstance(Locale.CHINA);

    private SharedPreferences preferences;
    private PackageManager packageManager;
    private LinearLayout chipContainer;
    private EditText searchInput;
    private TextView countText;
    private TextView emptyText;
    private GridView appGrid;
    private AppAdapter appAdapter;
    private String selectedCategory = CATEGORY_ALL;
    private String searchQuery = "";
    private boolean created;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        packageManager = getPackageManager();
        buildLayout();
        loadApps();
        renderCategoryChips();
        applyFilters();
        created = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (created) {
            loadApps();
            renderCategoryChips();
            applyFilters();
        }
    }

    private void buildLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F4F7FB"));
        root.setPadding(dp(18), dp(18), dp(18), dp(10));
        setContentView(root);

        TextView title = new TextView(this);
        title.setText("应用管理器");
        title.setTextColor(Color.parseColor("#172033"));
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView subtitle = new TextView(this);
        subtitle.setText("搜索、分类并快速打开手机里的 App");
        subtitle.setTextColor(Color.parseColor("#637083"));
        subtitle.setTextSize(14);
        root.addView(subtitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint("搜索 App 名称或包名");
        searchInput.setTextSize(16);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchInput.setBackgroundResource(R.drawable.search_box);
        searchInput.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
        );
        searchParams.setMargins(0, dp(18), 0, dp(12));
        root.addView(searchInput, searchParams);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().trim().toLowerCase(Locale.ROOT);
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        HorizontalScrollView chipScroller = new HorizontalScrollView(this);
        chipScroller.setHorizontalScrollBarEnabled(false);
        chipContainer = new LinearLayout(this);
        chipContainer.setOrientation(LinearLayout.HORIZONTAL);
        chipScroller.addView(chipContainer, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(chipScroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        countText = new TextView(this);
        countText.setTextColor(Color.parseColor("#637083"));
        countText.setTextSize(13);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        countParams.setMargins(0, dp(12), 0, dp(8));
        root.addView(countText, countParams);

        FrameLayout contentFrame = new FrameLayout(this);
        LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        );
        root.addView(contentFrame, frameParams);

        appAdapter = new AppAdapter(this, filteredApps);
        appGrid = new GridView(this);
        appGrid.setAdapter(appAdapter);
        appGrid.setNumColumns(GridView.AUTO_FIT);
        appGrid.setColumnWidth(dp(96));
        appGrid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        appGrid.setHorizontalSpacing(dp(10));
        appGrid.setVerticalSpacing(dp(12));
        appGrid.setClipToPadding(false);
        appGrid.setPadding(0, 0, 0, dp(18));
        appGrid.setSelector(android.R.color.transparent);
        appGrid.setOnItemClickListener((parent, view, position, id) -> launchApp(filteredApps.get(position)));
        appGrid.setOnItemLongClickListener((parent, view, position, id) -> {
            showAppActions(filteredApps.get(position));
            return true;
        });
        contentFrame.addView(appGrid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        emptyText = new TextView(this);
        emptyText.setText("没有符合条件的 App");
        emptyText.setTextColor(Color.parseColor("#637083"));
        emptyText.setTextSize(16);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setVisibility(View.GONE);
        contentFrame.addView(emptyText, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    private void loadApps() {
        List<AppEntry> nextApps = new ArrayList<>();
        boolean shouldMigrateCategories = preferences.getInt(PREF_CATEGORY_SCHEMA_VERSION, 1) < CATEGORY_SCHEMA_VERSION;
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> launchableActivities;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launchableActivities = packageManager.queryIntentActivities(
                    launcherIntent,
                    PackageManager.ResolveInfoFlags.of(0)
            );
        } else {
            launchableActivities = packageManager.queryIntentActivities(launcherIntent, 0);
        }

        for (ResolveInfo resolveInfo : launchableActivities) {
            if (resolveInfo.activityInfo == null) {
                continue;
            }
            String packageName = resolveInfo.activityInfo.packageName;
            String activityName = resolveInfo.activityInfo.name;
            if (getPackageName().equals(packageName)) {
                continue;
            }

            String label = resolveInfo.loadLabel(packageManager).toString();
            Drawable icon = resolveInfo.loadIcon(packageManager);
            AppEntry app = new AppEntry(label, packageName, activityName, icon);
            app.category = resolveCategory(app, shouldMigrateCategories);
            app.favorite = preferences.getBoolean(favoriteKey(app), false);
            app.hidden = preferences.getBoolean(hiddenKey(app), false);
            nextApps.add(app);
        }

        if (shouldMigrateCategories) {
            preferences.edit().putInt(PREF_CATEGORY_SCHEMA_VERSION, CATEGORY_SCHEMA_VERSION).apply();
        }

        Collections.sort(nextApps, (left, right) -> {
            if (left.favorite != right.favorite) {
                return left.favorite ? -1 : 1;
            }
            return collator.compare(left.label, right.label);
        });

        allApps.clear();
        allApps.addAll(nextApps);
    }

    private void renderCategoryChips() {
        categoryChips.clear();
        chipContainer.removeAllViews();

        for (String category : CATEGORIES) {
            TextView chip = new TextView(this);
            chip.setGravity(Gravity.CENTER);
            chip.setTextSize(14);
            chip.setSingleLine(true);
            chip.setPadding(dp(14), 0, dp(14), 0);
            chip.setText(categoryLabel(category));
            chip.setOnClickListener(v -> {
                selectedCategory = category;
                updateChipStyles();
                applyFilters();
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(38)
            );
            params.setMargins(0, 0, dp(8), 0);
            chipContainer.addView(chip, params);
            categoryChips.put(category, chip);
        }
        updateChipStyles();
    }

    private void updateChipStyles() {
        for (Map.Entry<String, TextView> entry : categoryChips.entrySet()) {
            boolean selected = entry.getKey().equals(selectedCategory);
            TextView chip = entry.getValue();
            chip.setText(categoryLabel(entry.getKey()));
            chip.setTextColor(selected ? Color.WHITE : Color.parseColor("#172033"));
            chip.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
            chip.setBackground(makeChipBackground(selected));
        }
    }

    private GradientDrawable makeChipBackground(boolean selected) {
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(19));
        background.setColor(selected ? Color.parseColor("#246BFE") : Color.WHITE);
        background.setStroke(dp(1), selected ? Color.parseColor("#246BFE") : Color.parseColor("#DCE3EE"));
        return background;
    }

    private String categoryLabel(String category) {
        return category + " " + countForCategory(category);
    }

    private int countForCategory(String category) {
        int count = 0;
        for (AppEntry app : allApps) {
            if (matchesCategory(app, category)) {
                count++;
            }
        }
        return count;
    }

    private void applyFilters() {
        filteredApps.clear();
        for (AppEntry app : allApps) {
            if (!matchesCategory(app, selectedCategory)) {
                continue;
            }
            if (!matchesSearch(app)) {
                continue;
            }
            filteredApps.add(app);
        }

        appAdapter.notifyDataSetChanged();
        countText.setText("当前显示 " + filteredApps.size() + " 个 App。点击打开，长按管理分类、常用和隐藏。");
        emptyText.setVisibility(filteredApps.isEmpty() ? View.VISIBLE : View.GONE);
        appGrid.setVisibility(filteredApps.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private boolean matchesCategory(AppEntry app, String category) {
        if (CATEGORY_ALL.equals(category)) {
            return !app.hidden;
        }
        if (CATEGORY_FAVORITES.equals(category)) {
            return app.favorite && !app.hidden;
        }
        if (CATEGORY_HIDDEN.equals(category)) {
            return app.hidden;
        }
        return !app.hidden && category.equals(app.category);
    }

    private boolean matchesSearch(AppEntry app) {
        if (searchQuery.isEmpty()) {
            return true;
        }
        return app.label.toLowerCase(Locale.ROOT).contains(searchQuery)
                || app.packageName.toLowerCase(Locale.ROOT).contains(searchQuery);
    }

    private void launchApp(AppEntry app) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(new ComponentName(app.packageName, app.activityName));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, "无法打开：" + app.label, Toast.LENGTH_SHORT).show();
            loadApps();
            renderCategoryChips();
            applyFilters();
        }
    }

    private void showAppActions(AppEntry app) {
        List<String> actions = new ArrayList<>();
        actions.add(app.favorite ? "取消常用" : "设为常用");
        actions.add("修改分类");
        actions.add(app.hidden ? "取消隐藏" : "隐藏");
        actions.add("应用信息");

        new AlertDialog.Builder(this)
                .setTitle(app.label)
                .setItems(actions.toArray(new String[0]), (dialog, which) -> {
                    String action = actions.get(which);
                    if (action.contains("常用")) {
                        toggleFavorite(app);
                    } else if ("修改分类".equals(action)) {
                        showCategoryDialog(app);
                    } else if (action.contains("隐藏")) {
                        toggleHidden(app);
                    } else {
                        openAppSettings(app);
                    }
                })
                .show();
    }

    private void toggleFavorite(AppEntry app) {
        app.favorite = !app.favorite;
        preferences.edit().putBoolean(favoriteKey(app), app.favorite).apply();
        reloadAfterChange();
    }

    private void toggleHidden(AppEntry app) {
        app.hidden = !app.hidden;
        preferences.edit().putBoolean(hiddenKey(app), app.hidden).apply();
        if (app.hidden && CATEGORY_FAVORITES.equals(selectedCategory)) {
            selectedCategory = CATEGORY_ALL;
        }
        reloadAfterChange();
    }

    private void showCategoryDialog(AppEntry app) {
        List<String> editableCategories = CATEGORIES.subList(2, CATEGORIES.size() - 1);
        int checkedIndex = Math.max(0, editableCategories.indexOf(app.category));
        new AlertDialog.Builder(this)
                .setTitle("修改分类：" + app.label)
                .setSingleChoiceItems(editableCategories.toArray(new String[0]), checkedIndex, (dialog, which) -> {
                    app.category = editableCategories.get(which);
                    preferences.edit()
                            .putString(categoryKey(app), app.category)
                            .putBoolean(manualCategoryKey(app), true)
                            .apply();
                    selectedCategory = app.category;
                    dialog.dismiss();
                    reloadAfterChange();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void openAppSettings(AppEntry app) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + app.packageName));
        startActivity(intent);
    }

    private String resolveCategory(AppEntry app, boolean shouldMigrateCategories) {
        boolean hasManualCategory = preferences.getBoolean(manualCategoryKey(app), false);
        String savedCategory = preferences.getString(categoryKey(app), null);

        if (savedCategory == null || shouldMigrateCategories && !hasManualCategory) {
            return inferCategory(app);
        }
        return normalizeCategory(savedCategory);
    }

    private void reloadAfterChange() {
        loadApps();
        renderCategoryChips();
        updateChipStyles();
        applyFilters();
    }

    private String inferCategory(AppEntry app) {
        String text = (app.label + " " + app.packageName).toLowerCase(Locale.ROOT);
        if (containsAny(text, "game", "games", "gaming", "mihoyo", "hoyoverse", "tencent.tmgp", "netease", "steam", "epic", "pubg", "honorofkings", "lolm", "minecraft", "genshin", "starrail", "brawl", "roblox", "pokemon", "gamecenter", "游戏", "手游", "王者荣耀", "和平精英", "原神", "崩坏", "星穹铁道", "我的世界", "蛋仔", "阴阳师", "第五人格", "明日方舟", "金铲铲", "英雄联盟", "元梦之星")) {
            return "游戏";
        }
        if (containsAny(text, "chatgpt", "openai", "claude", "gemini", "deepseek", "kimi", "豆包", "通义", "文心", "ai")) {
            return "AI";
        }
        if (containsAny(text, "bilibili", "douyin", "kuaishou", "xiaohongshu", "youtube", "iqiyi", "youku", "netflix", "music", "spotify", "podcast", "player", "哔哩", "抖音", "快手", "小红书", "视频", "音乐", "爱奇艺", "优酷", "腾讯视频", "网易云", "酷狗", "酷我", "喜马拉雅")) {
            return "娱乐";
        }
        if (containsAny(text, "wechat", "qq", "weibo", "telegram", "whatsapp", "instagram", "facebook", "twitter", "xhs", "discord", "line", "snapchat", "微信", "微博", "社交", "聊天", "知乎", "贴吧")) {
            return "社交";
        }
        if (containsAny(text, "alipay", "unionpay", "bank", "wallet", "pay", "finance", "支付", "银行", "云闪付", "钱包", "工商银行", "建设银行", "招商银行", "农业银行", "中国银行")) {
            return "支付";
        }
        if (containsAny(text, "taobao", "jd", "pdd", "amazon", "shop", "mall", "tmall", "meituan", "eleme", "淘宝", "京东", "拼多多", "闲鱼", "购物", "天猫", "美团", "饿了么", "得物")) {
            return "购物";
        }
        if (containsAny(text, "amap", "baidu.map", "didi", "ctrip", "railway", "travel", "map", "flight", "taxi", "高德", "地图", "滴滴", "携程", "铁路", "出行", "12306", "航旅", "公交", "地铁")) {
            return "出行";
        }
        if (containsAny(text, "study", "course", "classroom", "duolingo", "anki", "cet", "learn", "school", "mooc", "notion", "obsidian", "学习", "课程", "考试", "单词", "作业", "课堂", "大学", "慕课", "词典", "翻译")) {
            return "学习";
        }
        return "其他";
    }

    private String normalizeCategory(String category) {
        List<String> editableCategories = CATEGORIES.subList(2, CATEGORIES.size() - 1);
        if (editableCategories.contains(category)) {
            return category;
        }
        return "其他";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String categoryKey(AppEntry app) {
        return "category:" + app.key();
    }

    private String manualCategoryKey(AppEntry app) {
        return "manual-category:" + app.key();
    }

    private String favoriteKey(AppEntry app) {
        return "favorite:" + app.key();
    }

    private String hiddenKey(AppEntry app) {
        return "hidden:" + app.key();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class AppEntry {
        final String label;
        final String packageName;
        final String activityName;
        final Drawable icon;
        String category;
        boolean favorite;
        boolean hidden;

        AppEntry(String label, String packageName, String activityName, Drawable icon) {
            this.label = label;
            this.packageName = packageName;
            this.activityName = activityName;
            this.icon = icon;
        }

        String key() {
            return packageName + "/" + activityName;
        }
    }

    private final class AppAdapter extends BaseAdapter {
        private final Context context;
        private final List<AppEntry> apps;

        AppAdapter(Context context, List<AppEntry> apps) {
            this.context = context;
            this.apps = apps;
        }

        @Override
        public int getCount() {
            return apps.size();
        }

        @Override
        public Object getItem(int position) {
            return apps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                LinearLayout item = new LinearLayout(context);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setGravity(Gravity.CENTER);
                item.setBackgroundResource(R.drawable.app_card);
                item.setPadding(dp(8), dp(10), dp(8), dp(8));
                item.setMinimumHeight(dp(116));
                item.setLayoutParams(new AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(120)
                ));

                ImageView iconView = new ImageView(context);
                iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(48), dp(48));
                item.addView(iconView, iconParams);

                TextView nameView = new TextView(context);
                nameView.setTextColor(Color.parseColor("#172033"));
                nameView.setTextSize(12);
                nameView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                nameView.setGravity(Gravity.CENTER);
                nameView.setMaxLines(2);
                nameView.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                nameParams.setMargins(0, dp(8), 0, 0);
                item.addView(nameView, nameParams);

                TextView metaView = new TextView(context);
                metaView.setTextColor(Color.parseColor("#637083"));
                metaView.setTextSize(10);
                metaView.setGravity(Gravity.CENTER);
                metaView.setSingleLine(true);
                metaView.setEllipsize(TextUtils.TruncateAt.END);
                item.addView(metaView, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));

                holder = new ViewHolder(iconView, nameView, metaView);
                item.setTag(holder);
                convertView = item;
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            AppEntry app = apps.get(position);
            holder.iconView.setImageDrawable(app.icon);
            holder.nameView.setText(app.favorite ? "★ " + app.label : app.label);
            holder.metaView.setText(app.category);
            return convertView;
        }
    }

    private static final class ViewHolder {
        final ImageView iconView;
        final TextView nameView;
        final TextView metaView;

        ViewHolder(ImageView iconView, TextView nameView, TextView metaView) {
            this.iconView = iconView;
            this.nameView = nameView;
            this.metaView = metaView;
        }
    }

}
